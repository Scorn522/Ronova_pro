#include <jvmti.h>
#include <windows.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <stdio.h>

typedef struct Owner { jweak module; volatile LONG stopped,producer; struct Owner *next; } Owner;
typedef struct OwnerLink {Owner *owner;struct OwnerLink *next;} OwnerLink;
typedef struct NativeUnloader {jweak identity;struct NativeUnloader *next;} NativeUnloader;
typedef struct NativeLibrary {jweak identity,origin,name;wchar_t *path;jsize nameLength;Owner *carrier,*consumer;OwnerLink *owners;NativeUnloader *unloaders;HMODULE handle;struct HostImage *hostImage;jboolean builtin,jni;volatile LONG alive,complete,closing;struct NativeLibrary *next;} NativeLibrary;
typedef struct NativeLibraryScope {NativeLibrary *library;int unloading,phase,success;struct NativeLibraryScope *previous;} NativeLibraryScope;
typedef struct NativeRegistration {jmethodID *methods;jint count;OwnerLink *owners;jweak origin;NativeLibrary *loading;struct NativeRegistration *previous;} NativeRegistration;
typedef struct Binding {jmethodID method;jweak type,origin;Owner *owner,*declared;OwnerLink *owners;NativeLibrary *library,*loading;void *original,*entry;char *signature,*fileMethod;char result;unsigned unsafeOperation,fileOperation,fileArgument,threadOperation;const char *unsafeKind;volatile LONG active,current,valid;RUNTIME_FUNCTION unwind;struct Binding *previous,*next;} Binding;
typedef struct NativeDefinition {jclass declaring;jobject loader;jbyteArray bytes;unsigned phase;struct NativeDefinition *previous;} NativeDefinition;
typedef struct CodeExit {jmethodID method;jobject token;struct CodeExit *previous;} CodeExit;
typedef struct NativeUnsafeFrame {jobject token;uint64_t expected;struct NativeMemoryCall *memoryCall;} NativeUnsafeFrame;
enum { UNSAFE_PUT=1,UNSAFE_CAS,UNSAFE_EXCHANGE,UNSAFE_SET,UNSAFE_COPY,UNSAFE_SWAP,UNSAFE_ALLOCATE,UNSAFE_REALLOCATE,UNSAFE_FREE,UNSAFE_READ };
typedef struct NativeFileFrame {jobject receiver,token;jclass declaring;jstring method;jobjectArray sources,references;jlongArray arguments;jint operation;jlong address,length;int finishing;} NativeFileFrame;
typedef struct NativeBindingSeed {jmethodID method;void *original;int captured;struct NativeBindingSeed *previous;} NativeBindingSeed;
typedef struct NativeThreadFrame {jobject receiver,token;jobjectArray sources;jint operation;int finishing,completed,skipped,tokenGlobal;} NativeThreadFrame;
typedef struct NativeUnsafeMutation {jobject receiver,source;jstring kind;jlong offset,length,sourceOffset;jboolean bulk,reading,copy;struct NativeUnsafeMutation *previous;} NativeUnsafeMutation;
typedef struct NativeThread {Binding *stack[128],*entries[128];NativeUnsafeFrame unsafeFrames[128];NativeFileFrame fileFrames[128],*filePermit;NativeThreadFrame threadFrames[128],*threadPermit;NativeBindingSeed *bindingSeed;NativeUnsafeMutation *unsafeMutation;struct NativeMemoryWrite *memoryWrite;unsigned depth,control,sourceCapture,unsafeInstalling,bufferAllocation;jclass defined,prepared,changedClass;jobject created,mutation,popped,changedHolder,bufferAddress,unobservedLibrary;jobjectArray bufferSources;CodeExit *execution;NativeDefinition *definition;NativeLibraryScope *library;NativeRegistration *registration;struct HostCreation *processScope;struct HostProcess *processCreated;} NativeThread;
static jvmtiEnv *native_ti;
static JavaVM *native_vm;
static SRWLOCK native_records=SRWLOCK_INIT;
static Owner *native_owners;
static Binding *native_bindings;
static NativeLibrary *native_libraries;
static DWORD native_tls=TLS_OUT_OF_INDEXES;
static volatile LONG native_events,native_jni_ready,native_failures;
static volatile LONG native_describe_recorded;
static jclass native_controller;
static jmethodID native_control_gate;
static jmethodID native_library_gate;
static jmethodID native_load_gate;
static jclass native_library_class,native_impl_class,native_unloader_class;
static jfieldID native_library_context,native_impl_handle,native_impl_version,native_impl_name,native_impl_builtin,native_impl_jni;
static HMODULE native_java_image;
static volatile LONG native_library_present,native_library_ready;
static jclass native_tasks,native_definitions;
static jmethodID native_owner_gate,native_field_gate,native_module_query,native_return_query;
static jmethodID native_image_query,native_defined_query,native_after_query,native_created_query;
static jmethodID native_array_query,native_array_policy,native_pin_query,native_registration_query,native_registration_target,native_object_module;
static jmethodID native_mutation_field,native_mutation_array,native_mutation_end;
static jmethodID native_network_sources;
static jmethodID native_process_sources;
static jclass native_unsafe_class,native_unsafe_backing;
static jobject native_unsafe_denied;
static jmethodID native_unsafe_begin_query,native_unsafe_read_begin_query,native_unsafe_copy_begin_query,native_unsafe_read_query,native_unsafe_register;
static jmethodID native_memory_gate;
static struct JNINativeInterface_ native_original;
static int native_library_install(JNIEnv *env);
static int native_bootstrap_class(JNIEnv *env,jclass type,const char *expected);
static int native_bind_controller(JNIEnv *env,jclass controller);
static int code_initialize(void);
static int code_prepare(JNIEnv*);
static int code_current_sources(JNIEnv *env,OwnerLink **owners);
static int code_current_sources_query(JNIEnv *env,OwnerLink **owners,int *unknown);
static int code_current_stopped(JNIEnv *env);
static void JNICALL code_class_prepared(jvmtiEnv *ti,JNIEnv *env,jthread thread,jclass type);
static void JNICALL code_frame_popped(jvmtiEnv *ti,JNIEnv *env,jthread thread,jmethodID method,jboolean exceptional);
static void code_thread_end(JNIEnv *env,NativeThread *state);
static int heap_initialize(void);
static void JNICALL heap_field_modified(jvmtiEnv*,JNIEnv*,jthread,jmethodID,jlocation,jclass,jobject,jfieldID,char,jvalue);
static void native_unsafe_classify(JNIEnv*,jclass,Binding*,const char*,const char*);
static int native_unsafe_prepare(JNIEnv*);
static int native_unsafe_install(JNIEnv*);
static int native_unsafe_registered(JNIEnv*,NativeThread*,jclass,const JNINativeMethod*,jmethodID*,jint,void*);
static int native_unsafe_vm_target(jmethodID,void*);
static int native_unsafe_begin(JNIEnv*,NativeThread*,Binding*,unsigned char*);
static void native_unsafe_end(JNIEnv*,NativeThread*,Binding*,unsigned char*);
static uint64_t native_unsafe_result(JNIEnv*,Binding*,unsigned char*);
static void native_file_classify(JNIEnv*,jclass,Binding*,const char*);
static int native_file_prepare(JNIEnv*);
static int native_file_begin(JNIEnv*,NativeThread*,Binding*,jobject,unsigned char*);
static void native_file_end(JNIEnv*,NativeThread*);
static void native_file_thread_end(JNIEnv*,NativeThread*);
static void native_file_refuse(JNIEnv*,Binding*);
static int native_binding_seed_target(JNIEnv*,NativeThread*,jmethodID,void**,void**);
static int native_binding_seed_one(JNIEnv*,NativeThread*,jclass,jmethodID,const char*,const char*,unsigned);
static void native_thread_classify(JNIEnv*,jclass,Binding*);
static int native_thread_prepare(JNIEnv*);
static int native_thread_vm_target(jmethodID,void*);
static int native_thread_begin(JNIEnv*,NativeThread*,Binding*,jobject);
static void *native_thread_call(NativeThread*,Binding*);
static void native_thread_finish(JNIEnv*,NativeThread*,NativeThreadFrame*,int);
static void native_thread_refuse(JNIEnv*,Binding*);
static int native_buffer_prepare(JNIEnv*);
static int native_buffer_allocation(JNIEnv*,NativeThread*);
static void *JNICALL native_buffer_address(JNIEnv*,jobject);
static unsigned native_arguments(const char*);
static int native_memory_call_begin(JNIEnv*,NativeThread*,Binding*,unsigned char*,NativeUnsafeFrame*);
static void native_memory_call_end(JNIEnv*,NativeThread*,Binding*,unsigned char*,NativeUnsafeFrame*);
static void native_memory_thread_end(JNIEnv*,NativeThread*);
static void code_exception_frames(JNIEnv*,jthrowable,unsigned);
static NativeThread *native_thread(void) {
    if(native_tls==TLS_OUT_OF_INDEXES)return NULL;NativeThread *state=(NativeThread*)TlsGetValue(native_tls);
    if(!state){state=(NativeThread*)calloc(1,sizeof(*state));if(state&&!TlsSetValue(native_tls,state)){free(state);return NULL;}}return state;
}
static void native_refuse(JNIEnv *env,const char *message){
    const struct JNINativeInterface_ *api=native_original.FindClass?&native_original:*env;
    if(api->ExceptionCheck(env))return;
    jclass type=api->FindClass(env,"java/lang/IllegalStateException");
    if(type){api->ThrowNew(env,type,message);api->DeleteLocalRef(env,type);}
}
static Owner *native_owner(JNIEnv *env,jobject module){
    if(!module)return NULL;Owner *owner=native_owners;
    while(owner&&!native_original.IsSameObject(env,owner->module,module))owner=owner->next;
    if(!owner){owner=(Owner*)calloc(1,sizeof(*owner));if(owner){owner->module=native_original.NewWeakGlobalRef(env,module);if(!owner->module){free(owner);return NULL;}owner->next=native_owners;native_owners=owner;}}
    return owner;
}
static void native_owner_links_free(OwnerLink *head){while(head){OwnerLink *next=head->next;free(head);head=next;}}
static int native_owner_add(OwnerLink **head,Owner *owner){
    if(!owner)return 1;for(OwnerLink *p=*head;p;p=p->next)if(p->owner==owner)return 1;
    OwnerLink *link=(OwnerLink*)malloc(sizeof(*link));if(!link)return 0;link->owner=owner;link->next=*head;*head=link;return 1;
}
static int native_owner_merge(OwnerLink **head,OwnerLink *source){for(;source;source=source->next)if(!native_owner_add(head,source->owner))return 0;return 1;}
static Owner *native_owner_stopped(OwnerLink *head){for(;head;head=head->next)if(InterlockedCompareExchange(&head->owner->stopped,0,0))return head->owner;return NULL;}
static int native_owner_matches(JNIEnv *env,OwnerLink *head,jobject module){if(!module)return 1;for(;head;head=head->next)if(native_original.IsSameObject(env,head->owner->module,module))return 1;return 0;}
static int native_has_producer(OwnerLink *head){for(;head;head=head->next)if(InterlockedCompareExchange(&head->owner->producer,0,0))return 1;return 0;}
static HMODULE native_image(void *address){HMODULE image=NULL;if(address)GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,(LPCSTR)address,&image);return image;}
static HMODULE native_observe_image(void *address){
    HMODULE image=native_image(address);if(!image)return NULL;
    DWORD capacity=512;wchar_t *path=NULL;
    while(capacity<=32768){path=(wchar_t*)calloc(capacity,sizeof(wchar_t));if(!path)return image;
        DWORD size=GetModuleFileNameW(image,path,capacity);if(size&&size<capacity)break;free(path);path=NULL;if(!size)break;capacity*=2;}
    if(path){AcquireSRWLockExclusive(&native_records);
        for(NativeLibrary *library=native_libraries;library;library=library->next)if(!library->handle&&!library->builtin
                &&library->path&&InterlockedCompareExchange(&library->alive,0,0)&&_wcsicmp(path,library->path)==0)library->handle=image;
        ReleaseSRWLockExclusive(&native_records);free(path);}
    return image;
}
static int native_capture_image_owners(void *address,OwnerLink **owners){
    HMODULE image=native_observe_image(address);int ready=1;AcquireSRWLockShared(&native_records);
    for(NativeLibrary *library=native_libraries;library;library=library->next)if(image&&library->handle==image&&InterlockedCompareExchange(&library->alive,0,0))ready=ready&&native_owner_merge(owners,library->owners);
    ReleaseSRWLockShared(&native_records);return ready;
}
static int native_caller_stopped(void *address){
    HMODULE image=native_observe_image(address);int stopped=0;AcquireSRWLockShared(&native_records);
    for(NativeLibrary *library=native_libraries;library;library=library->next)if(image&&library->handle==image&&InterlockedCompareExchange(&library->alive,0,0)&&native_owner_stopped(library->owners)){stopped=1;break;}
    ReleaseSRWLockShared(&native_records);return stopped;
}
static int native_stopped(JNIEnv *env){NativeThread *state=native_thread();if(!state)return 0;for(unsigned i=0;i<state->depth;i++)if(native_owner_stopped(state->stack[i]->owners))return 1;for(NativeLibraryScope *scope=state->library;scope;scope=scope->previous)if(native_owner_stopped(scope->library->owners))return 1;return code_current_stopped(env);}
static jweak native_origin(NativeThread *state){return state&&state->depth?state->stack[state->depth-1]->origin:state&&state->library?state->library->library->origin:NULL;}
static int native_capture_scope_sources(JNIEnv *env,NativeThread *state,OwnerLink **owners,int *unknown){
    if(!state)return 1;for(unsigned i=0;i<state->depth;i++)if(!native_owner_merge(owners,state->stack[i]->owners))return 0;
    for(NativeLibraryScope *scope=state->library;scope;scope=scope->previous)if(!native_owner_merge(owners,scope->library->owners))return 0;return code_current_sources_query(env,owners,unknown);
}
static int native_capture_scope_owners(JNIEnv *env,NativeThread *state,OwnerLink **owners){return native_capture_scope_sources(env,state,owners,NULL);}
static int native_capture_network_sources(JNIEnv *env,NativeThread *state,OwnerLink **owners,int *unknown){
    if(!native_jni_ready||!state||state->control||!native_network_sources)return 1;
    state->control++;state->sourceCapture++;jobjectArray sources=(jobjectArray)native_original.CallStaticObjectMethod(env,native_tasks,native_network_sources);state->sourceCapture--;int valid=sources&&!native_original.ExceptionCheck(env);
    if(valid){jsize count=native_original.GetArrayLength(env,sources);AcquireSRWLockExclusive(&native_records);
        for(jsize i=0;i<count&&valid&&!native_original.ExceptionCheck(env);i++){
            jobject module=native_original.GetObjectArrayElement(env,sources,i);
            if(!module){if(unknown)*unknown=1;else valid=0;}
            else {Owner *owner=native_owner(env,module);valid=owner&&native_owner_add(owners,owner);native_original.DeleteLocalRef(env,module);}
        }ReleaseSRWLockExclusive(&native_records);
    }if(sources)native_original.DeleteLocalRef(env,sources);state->control--;return valid&&!native_original.ExceptionCheck(env);
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_sourceCapture0(JNIEnv *env,jclass type){
    (void)env;(void)type;NativeThread *state=native_thread();return state&&state->sourceCapture!=0;
}
static uint64_t native_enter(JNIEnv *env,jobject receiver,Binding *binding,unsigned char *frame) {
    NativeThread *state=native_thread();if(!state||state->depth==128){native_refuse(env,"RONOVA_NATIVE_SCOPE_CAPACITY");return 0;}
    if(binding->fileOperation)memset(&state->fileFrames[state->depth],0,sizeof(NativeFileFrame));
    if(binding->threadOperation)memset(&state->threadFrames[state->depth],0,sizeof(NativeThreadFrame));
    // Controller JNI work already has its exact native control scope. Its own
    // JDK atomics must not recursively enter the Java memory bookkeeping.
    if((binding->unsafeOperation||binding->fileOperation||binding->threadOperation)&&state->control){state->entries[state->depth]=binding;state->stack[state->depth++]=binding;InterlockedIncrement(&binding->active);return (uint64_t)(uintptr_t)binding->original;}
    if(binding->declared&&InterlockedCompareExchange(&binding->declared->stopped,0,0))return 0;
    // A stopped caller may finish an already sealed resource's original close.
    // The exact descriptor scope is checked by the file bridge before dispatch.
    if(code_current_stopped(env)&&binding->fileOperation!=4&&binding->fileOperation!=7&&binding->fileOperation!=8&&binding->unsafeOperation!=UNSAFE_FREE)return 0;
    Binding *effective=binding;
    while(effective&&(!InterlockedCompareExchange(&effective->valid,0,0)||native_owner_stopped(effective->owners)
            ||effective->library&&(!InterlockedCompareExchange(&effective->library->alive,0,0)||InterlockedCompareExchange(&effective->library->closing,0,0))))effective=effective->previous;
    if(!effective)return 0;
    state->control++;jobject type=native_original.NewLocalRef(env,effective->origin);
    jboolean allowed=type&&native_tasks?native_original.CallStaticBooleanMethod(env,native_tasks,native_owner_gate,type):JNI_FALSE;
    if(type)native_original.DeleteLocalRef(env,type);state->control--;if(native_original.ExceptionCheck(env)||!allowed)return 0;
    state->entries[state->depth]=binding;state->stack[state->depth++]=effective;InterlockedIncrement(&effective->active);
    if(binding->unsafeOperation&&!native_unsafe_begin(env,state,binding,frame)){
        state->depth--;InterlockedDecrement(&effective->active);state->stack[state->depth]=state->entries[state->depth]=NULL;return 0;
    }
    if(binding->fileOperation&&!native_file_begin(env,state,binding,receiver,frame)){
        state->depth--;InterlockedDecrement(&effective->active);state->stack[state->depth]=state->entries[state->depth]=NULL;return 0;
    }
    if(binding->threadOperation){
        if(!native_thread_begin(env,state,binding,receiver)){
            state->depth--;InterlockedDecrement(&effective->active);state->stack[state->depth]=state->entries[state->depth]=NULL;return 0;
        }
        return (uint64_t)(uintptr_t)native_thread_call(state,effective);
    }
    return (uint64_t)(uintptr_t)effective->original;
}
static void native_leave(Binding *binding,unsigned char *frame){
    NativeThread *state=native_thread();if(!state||!state->depth||state->entries[state->depth-1]!=binding){InterlockedIncrement(&native_failures);return;}
    if(binding->unsafeOperation||binding->fileOperation||binding->threadOperation){
        unsigned args=native_arguments(binding->signature),save=32+(args>4?(args-4)*8:0);JNIEnv *env;memcpy(&env,frame+save,sizeof(env));
        if(binding->unsafeOperation)native_unsafe_end(env,state,binding,frame);
        if(binding->fileOperation)native_file_end(env,state);
        if(binding->threadOperation)native_thread_finish(env,state,&state->threadFrames[state->depth-1],!native_original.ExceptionCheck(env));
    }
    state->depth--;InterlockedDecrement(&state->stack[state->depth]->active);state->stack[state->depth]=state->entries[state->depth]=NULL;
}
static uint64_t native_result(JNIEnv *env,Binding *binding,unsigned char *frame) {
    if(native_original.ExceptionCheck(env))return 0;
    if(binding->threadOperation){native_thread_refuse(env,binding);return 0;}
    if(binding->fileOperation){native_file_refuse(env,binding);return 0;}
    if(binding->unsafeOperation)return native_unsafe_result(env,binding,frame);
    NativeThread *state=native_thread();if(!state||!native_definitions)return 0;state->control++;
    jobject type=native_original.NewLocalRef(env,binding->type);jstring descriptor=native_original.NewStringUTF(env,binding->signature);
    Owner *stopped=native_owner_stopped(binding->owners);jobject owner=stopped?native_original.NewLocalRef(env,stopped->module):NULL;
    jobject value=type&&descriptor?native_original.CallStaticObjectMethod(env,native_definitions,native_return_query,owner,type,descriptor):NULL;uint64_t result=0;
    if(value&&!native_original.ExceptionCheck(env)) {
        const char *method=NULL,*signature=NULL;
        switch(binding->result){case 'Z':method="booleanValue";signature="()Z";break;case 'B':method="byteValue";signature="()B";break;case 'C':method="charValue";signature="()C";break;case 'S':method="shortValue";signature="()S";break;case 'I':method="intValue";signature="()I";break;case 'J':method="longValue";signature="()J";break;case 'F':method="floatValue";signature="()F";break;case 'D':method="doubleValue";signature="()D";break;default:result=(uint64_t)(uintptr_t)value;break;}
        if(method){jclass boxed=native_original.GetObjectClass(env,value);jmethodID accessor=native_original.GetMethodID(env,boxed,method,signature);jvalue bits;memset(&bits,0,sizeof(bits));
            if(accessor)switch(binding->result){case 'Z':bits.z=native_original.CallBooleanMethod(env,value,accessor);break;case 'B':bits.b=native_original.CallByteMethod(env,value,accessor);break;case 'C':bits.c=native_original.CallCharMethod(env,value,accessor);break;case 'S':bits.s=native_original.CallShortMethod(env,value,accessor);break;case 'I':bits.i=native_original.CallIntMethod(env,value,accessor);break;case 'J':bits.j=native_original.CallLongMethod(env,value,accessor);break;case 'F':bits.f=native_original.CallFloatMethod(env,value,accessor);break;case 'D':bits.d=native_original.CallDoubleMethod(env,value,accessor);break;}
            memcpy(&result,&bits,sizeof(result));native_original.DeleteLocalRef(env,boxed);native_original.DeleteLocalRef(env,value);
        }
    }
    if(owner)native_original.DeleteLocalRef(env,owner);if(type)native_original.DeleteLocalRef(env,type);if(descriptor)native_original.DeleteLocalRef(env,descriptor);state->control--;return result;
}
typedef struct NativeCode {unsigned char *start,*at;} NativeCode;
static void nb(NativeCode *c,unsigned n){*c->at++=(unsigned char)n;}
static void nw(NativeCode *c,uint32_t n){memcpy(c->at,&n,4);c->at+=4;}
static void na(NativeCode *c,uintptr_t n){memcpy(c->at,&n,8);c->at+=8;}
static void ns(NativeCode *c,unsigned reg,unsigned offset){nb(c,reg<8?0x48:0x4c);nb(c,0x89);nb(c,0x84|((reg&7)<<3));nb(c,0x24);nw(c,offset);}
static void nl(NativeCode *c,unsigned reg,unsigned offset){nb(c,reg<8?0x48:0x4c);nb(c,0x8b);nb(c,0x84|((reg&7)<<3));nb(c,0x24);nw(c,offset);}
static void nf(NativeCode *c,int store,unsigned reg,unsigned offset){nb(c,0xf3);nb(c,0x0f);nb(c,store?0x7f:0x6f);nb(c,0x84|(reg<<3));nb(c,0x24);nw(c,offset);}
static void ni(NativeCode *c,unsigned reg,uintptr_t n){nb(c,reg<8?0x48:0x49);nb(c,0xb8|(reg&7));na(c,n);}
static void nc(NativeCode *c,void *target){ni(c,0,(uintptr_t)target);nb(c,0xff);nb(c,0xd0);}
static void nr(NativeCode *c,unsigned save){unsigned regs[]={1,2,8,9};for(unsigned i=0;i<4;i++)nl(c,regs[i],save+i*8);for(unsigned i=0;i<4;i++)nf(c,0,i,save+32+i*16);}
static unsigned native_arguments(const char *descriptor){unsigned count=2;const char *p=descriptor+1;while(*p&&*p!=')'){while(*p=='[')p++;if(*p=='L')while(*p&&*p!=';')p++;if(*p)p++;count++;}return count;}
static char native_return_type(const char *descriptor){const char *p=descriptor+1;while(*p&&*p!=')'){while(*p=='[')p++;if(*p=='L')while(*p&&*p!=';')p++;if(*p)p++;}return *p==')'?p[1]:'V';}
static void *native_entry(Binding *binding) {
    unsigned args=native_arguments(binding->signature),extra=args>4?(args-4)*8:0,save=32+extra,frame=((save+128+15)&~15)+8;
    unsigned capacity=((extra*2+1024+4095)&~4095);unsigned char *page=(unsigned char*)VirtualAlloc(NULL,capacity,MEM_COMMIT|MEM_RESERVE,PAGE_READWRITE);if(!page)return NULL;
    NativeCode c={page,page};nb(&c,0x48);nb(&c,0x81);nb(&c,0xec);nw(&c,frame);
    unsigned regs[]={1,2,8,9};for(unsigned i=0;i<4;i++)ns(&c,regs[i],save+i*8);for(unsigned i=0;i<4;i++)nf(&c,1,i,save+32+i*16);
    for(unsigned i=0;i<extra;i+=8){nl(&c,0,frame+40+i);ns(&c,0,32+i);}
    ni(&c,8,(uintptr_t)binding);nb(&c,0x4c);nb(&c,0x8d);nb(&c,0x0c);nb(&c,0x24);nc(&c,(void*)native_enter);nb(&c,0x48);nb(&c,0x85);nb(&c,0xc0);nb(&c,0x0f);nb(&c,0x84);unsigned char *blocked=c.at;nw(&c,0);
    nr(&c,save);nb(&c,0xff);nb(&c,0xd0);ns(&c,0,save+96);nf(&c,1,0,save+104);ni(&c,1,(uintptr_t)binding);nb(&c,0x48);nb(&c,0x8d);nb(&c,0x14);nb(&c,0x24);nc(&c,(void*)native_leave);nl(&c,0,save+96);nf(&c,0,0,save+104);
    nb(&c,0xe9);unsigned char *finish=c.at;nw(&c,0);uint32_t jump=(uint32_t)(c.at-(blocked+4));memcpy(blocked,&jump,4);
    nl(&c,1,save);ni(&c,2,(uintptr_t)binding);nb(&c,0x4c);nb(&c,0x8d);nb(&c,0x04);nb(&c,0x24);nc(&c,(void*)native_result);nb(&c,0x66);nb(&c,0x48);nb(&c,0x0f);nb(&c,0x6e);nb(&c,0xc0);
    jump=(uint32_t)(c.at-(finish+4));memcpy(finish,&jump,4);nb(&c,0x48);nb(&c,0x81);nb(&c,0xc4);nw(&c,frame);nb(&c,0xc3);
    binding->unwind.BeginAddress=0;binding->unwind.EndAddress=(DWORD)(c.at-page);binding->unwind.UnwindData=capacity-16;
    unsigned char *unwind=page+capacity-16;unwind[0]=1;unwind[1]=7;unwind[2]=2;unwind[3]=0;unwind[4]=7;unwind[5]=1;unsigned short scaled=(unsigned short)(frame/8);memcpy(unwind+6,&scaled,2);
    DWORD previous;if(!VirtualProtect(page,capacity,PAGE_EXECUTE_READ,&previous)||!RtlAddFunctionTable(&binding->unwind,1,(DWORD64)(uintptr_t)page)){VirtualFree(page,0,MEM_RELEASE);return NULL;}
    FlushInstructionCache(GetCurrentProcess(),page,capacity);return page;
}
#include "host.c"
static jlong JNICALL native_rejected(JNIEnv *env,jobject receiver){(void)receiver;native_refuse(env,"RONOVA_NATIVE_BINDING_CONTROL_FAILED");return 0;}
static void JNICALL native_binding(jvmtiEnv *api,JNIEnv *env,jthread thread,jmethodID method,void *target,void **replacement) {
    (void)thread;jvmtiPhase phase;if(!native_definitions||(*api)->GetPhase(api,&phase)!=JVMTI_ERROR_NONE||phase!=JVMTI_PHASE_LIVE)return;
    NativeThread *state=native_thread();if(!state)return;state->control++;jclass type=NULL;
    if((*api)->GetMethodDeclaringClass(api,method,&type)!=JVMTI_ERROR_NONE){state->control--;return;}
    if(native_binding_seed_target(env,state,method,&target,replacement)){native_original.DeleteLocalRef(env,type);state->control--;return;}
    if(host_bind(env,type,method,target,replacement)){native_original.DeleteLocalRef(env,type);state->control--;return;}
    if(native_controller&&native_original.IsSameObject(env,type,native_controller)
            ||native_library_class&&native_original.IsSameObject(env,type,native_library_class)){native_original.DeleteLocalRef(env,type);state->control--;return;}
    jobject module=native_original.CallStaticObjectMethod(env,native_definitions,native_module_query,type);
    if(!module||native_original.ExceptionCheck(env)){native_original.DeleteLocalRef(env,type);state->control--;return;}
    HMODULE image=native_observe_image(target);
    AcquireSRWLockExclusive(&native_records);
    Binding *previous=NULL,*aliased=NULL;
    for(Binding *old=native_bindings;old;old=old->next){
        if(old->entry&&old->entry==target)aliased=old;
        if(old->method==method&&InterlockedCompareExchange(&old->current,0,0)){if(!previous)previous=old;InterlockedExchange(&old->current,0);}
    }
    if(!target){ReleaseSRWLockExclusive(&native_records);native_original.DeleteLocalRef(env,module);native_original.DeleteLocalRef(env,type);state->control--;return;}
    Owner *declared=native_owner(env,module);char *name=NULL,*signature=NULL;Binding *binding=(Binding*)calloc(1,sizeof(*binding));
    int ready=binding&&declared&&(*api)->GetMethodName(api,method,&name,&signature,NULL)==JVMTI_ERROR_NONE;
    if(ready){
        binding->method=method;binding->declared=declared;binding->previous=previous;binding->original=aliased?aliased->original:target;
        binding->type=native_original.NewWeakGlobalRef(env,type);binding->signature=_strdup(signature);binding->result=native_return_type(signature);
        native_unsafe_classify(env,type,binding,name,signature);
        native_file_classify(env,type,binding,name);
        native_thread_classify(env,type,binding);
        ready=native_owner_add(&binding->owners,declared);
        if(aliased)ready=ready&&native_owner_merge(&binding->owners,aliased->owners);
        NativeRegistration *registration=NULL;
        for(NativeRegistration *scope=state->registration;scope&&!registration;scope=scope->previous)
            for(jint i=0;i<scope->count;i++)if(scope->methods[i]==method){registration=scope;break;}
        if(registration){ready=ready&&native_owner_merge(&binding->owners,registration->owners);binding->loading=registration->loading;}
        // Query the OS loader before taking the ledger lock. JNI_OnLoad can
        // itself acquire this ledger while another thread is in the OS loader.
        if(aliased)image=aliased->library?aliased->library->handle:NULL;
        for(unsigned pass=0;pass<3&&!binding->library;pass++)for(NativeLibrary *library=native_libraries;library;library=library->next)
            if(image&&library->handle==image&&InterlockedCompareExchange(&library->alive,0,0)
                    &&(pass==0?library->carrier==declared&&library->consumer==declared:pass==1?library->carrier==declared:1)){
                binding->library=library;break;
            }
        if(binding->library)ready=ready&&native_owner_merge(&binding->owners,binding->library->owners);
        // A first call only resolves a pre-existing implementation; its caller
        // has not installed code into this method. Only real RegisterNatives
        // submission carries installer provenance.
        if(!binding->library&&registration)binding->library=registration->loading;
        jweak origin=aliased?aliased->origin:binding->library?binding->library->origin:registration?registration->origin:NULL;
        jclass actual=origin?(jclass)native_original.NewLocalRef(env,origin):(jclass)native_original.NewLocalRef(env,type);
        if(actual){binding->origin=native_original.NewWeakGlobalRef(env,actual);native_original.DeleteLocalRef(env,actual);}
        // Preserve pre-existing outside implementations for an actual later overwrite.
        // Ordinary bindings are recorded without replacing their VM entry point.
        if(ready&&binding->type&&binding->origin&&binding->signature){
            int wrapped=binding->unsafeOperation||binding->fileOperation||binding->threadOperation||native_has_producer(binding->owners);
            if(wrapped)binding->entry=native_entry(binding);
            ready=!wrapped||binding->entry!=NULL;
        }else ready=0;
        if(ready){binding->valid=binding->current=1;binding->owner=binding->owners?binding->owners->owner:NULL;binding->next=native_bindings;native_bindings=binding;if(binding->entry)*replacement=binding->entry;}
    }
    if(!ready){if(binding){if(binding->type)native_original.DeleteWeakGlobalRef(env,binding->type);if(binding->origin)native_original.DeleteWeakGlobalRef(env,binding->origin);native_owner_links_free(binding->owners);free(binding->signature);free(binding->fileMethod);free(binding);}*replacement=(void*)native_rejected;InterlockedIncrement(&native_failures);}
    if(name)(*api)->Deallocate(api,(unsigned char*)name);if(signature)(*api)->Deallocate(api,(unsigned char*)signature);ReleaseSRWLockExclusive(&native_records);native_original.DeleteLocalRef(env,module);native_original.DeleteLocalRef(env,type);state->control--;
}
static int native_field_allowed(JNIEnv *env,jobject object,jclass type,jfieldID field,jobject value,int statik) {
    if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_FIELD_WRITE");return 0;}NativeThread *state=native_thread();if(!native_tasks||!state)return 1;
    state->control++;jclass declaring=NULL;jvmtiError result=(*native_ti)->GetFieldDeclaringClass(native_ti,type,field,&declaring);
    jobject reflected=result==JVMTI_ERROR_NONE?native_original.ToReflectedField(env,declaring,field,statik?JNI_TRUE:JNI_FALSE):NULL;
    jboolean allowed=reflected?native_original.CallStaticBooleanMethod(env,native_tasks,native_field_gate,reflected,statik?NULL:object,value):JNI_FALSE;
    if(reflected)native_original.DeleteLocalRef(env,reflected);if(declaring)native_original.DeleteLocalRef(env,declaring);state->control--;return allowed&&!native_original.ExceptionCheck(env);
}
static jobject native_box(JNIEnv *env,const char *name,const char *signature,jvalue value){NativeThread *state=native_thread();if(!state)return NULL;state->control++;jclass type=native_original.FindClass(env,name);jmethodID method=type?native_original.GetStaticMethodID(env,type,"valueOf",signature):NULL;jobject result=method?native_original.CallStaticObjectMethodA(env,type,method,&value):NULL;if(type)native_original.DeleteLocalRef(env,type);state->control--;return result;}
static jobjectArray native_mutation_sources(JNIEnv *env,NativeThread *state){
    OwnerLink *owners=NULL;int unknown=0;
    if(!native_capture_scope_sources(env,state,&owners,&unknown)){
        native_owner_links_free(owners);if(!native_original.ExceptionCheck(env))native_refuse(env,"NATIVE_MUTATION_SOURCES_UNAVAILABLE");return NULL;
    }
    state->control++;jclass module=native_original.FindClass(env,"java/lang/Module");jsize count=unknown?1:0;
    for(OwnerLink *entry=owners;entry;entry=entry->next)if(InterlockedCompareExchange(&entry->owner->producer,0,0)&&!native_original.IsSameObject(env,entry->owner->module,NULL))count++;
    // A null slot is the Java resource/memory protocol's unknown-source marker.
    // Keep it separate from actual owners, whose stop checks still all apply.
    jobjectArray sources=module?native_original.NewObjectArray(env,count,module,NULL):NULL;jsize at=unknown?1:0;
    for(OwnerLink *entry=owners;sources&&entry&&!native_original.ExceptionCheck(env);entry=entry->next){
        if(!InterlockedCompareExchange(&entry->owner->producer,0,0))continue;
        jobject actual=native_original.NewLocalRef(env,entry->owner->module);if(!actual)continue;
        native_original.SetObjectArrayElement(env,sources,at++,actual);native_original.DeleteLocalRef(env,actual);
    }
    if(module)native_original.DeleteLocalRef(env,module);state->control--;native_owner_links_free(owners);return sources;
}
static jobject native_begin_field_mutation(JNIEnv *env,jobject receiver,jclass type,jfieldID field){
    if(native_original.ExceptionCheck(env))return NULL;
    NativeThread *state=native_thread();if(!state){native_refuse(env,"NATIVE_MUTATION_SCOPE_UNAVAILABLE");return NULL;}
    jclass declaring=NULL;char *name=NULL,*descriptor=NULL;
    jvmtiError result=(*native_ti)->GetFieldDeclaringClass(native_ti,type,field,&declaring);
    if(result==JVMTI_ERROR_NONE)result=(*native_ti)->GetFieldName(native_ti,declaring,field,&name,&descriptor,NULL);
    if(result!=JVMTI_ERROR_NONE){
        if(name)(*native_ti)->Deallocate(native_ti,(unsigned char*)name);if(descriptor)(*native_ti)->Deallocate(native_ti,(unsigned char*)descriptor);
        if(declaring)native_original.DeleteLocalRef(env,declaring);native_refuse(env,"NATIVE_MUTATION_FIELD_UNAVAILABLE");return NULL;
    }
    jstring fieldName=native_original.NewStringUTF(env,name),fieldDescriptor=NULL;
    if(!native_original.ExceptionCheck(env))fieldDescriptor=native_original.NewStringUTF(env,descriptor);
    (*native_ti)->Deallocate(native_ti,(unsigned char*)name);(*native_ti)->Deallocate(native_ti,(unsigned char*)descriptor);
    jobject token=NULL;jobjectArray contributors=NULL;
    if(fieldName&&fieldDescriptor&&!native_original.ExceptionCheck(env)){
        contributors=native_mutation_sources(env,state);
        if(contributors&&!native_original.ExceptionCheck(env)){
            jobject previous=state->mutation;state->mutation=receiver;
            token=native_original.CallStaticObjectMethod(env,native_tasks,native_mutation_field,receiver,declaring,fieldName,fieldDescriptor,contributors);
            state->mutation=previous;
        }
    }
    if(contributors)native_original.DeleteLocalRef(env,contributors);
    if(fieldName)native_original.DeleteLocalRef(env,fieldName);if(fieldDescriptor)native_original.DeleteLocalRef(env,fieldDescriptor);
    native_original.DeleteLocalRef(env,declaring);return token;
}
static jobject native_begin_array_mutation(JNIEnv *env,jarray receiver,jsize start,jsize length){
    if(native_original.ExceptionCheck(env))return NULL;
    NativeThread *state=native_thread();if(!state){native_refuse(env,"NATIVE_MUTATION_SCOPE_UNAVAILABLE");return NULL;}
    jobjectArray contributors=native_mutation_sources(env,state);if(!contributors||native_original.ExceptionCheck(env)){if(contributors)native_original.DeleteLocalRef(env,contributors);return NULL;}
    jobject previous=state->mutation;state->mutation=receiver;
    jobject token=native_original.CallStaticObjectMethod(env,native_tasks,native_mutation_array,receiver,start,length,contributors);
    state->mutation=previous;native_original.DeleteLocalRef(env,contributors);return token;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_memoryMutationBoundary0(JNIEnv *env,jclass type,jobject receiver){
    (void)type;NativeThread *state=native_thread();
    return state&&state->mutation&&native_original.IsSameObject(env,state->mutation,receiver)?JNI_TRUE:JNI_FALSE;
}
static void native_end_mutation(JNIEnv *env,jobject token,int applied){
    if(!token)return;jthrowable failure=native_original.ExceptionOccurred(env);
    if(failure)native_original.ExceptionClear(env);
    native_original.CallStaticVoidMethod(env,native_tasks,native_mutation_end,token,applied&&!failure?JNI_TRUE:JNI_FALSE);
    if(failure){if(native_original.ExceptionCheck(env)){native_original.ExceptionClear(env);InterlockedIncrement(&native_failures);}native_original.Throw(env,failure);native_original.DeleteLocalRef(env,failure);}
    native_original.DeleteLocalRef(env,token);
}
static int native_loader_field(JNIEnv *env,jobject receiver,jclass type,jfieldID field,void *caller){
    if(!InterlockedCompareExchange(&native_library_present,0,0)||!native_impl_class)return 0;jclass declaring=NULL;
    if((*native_ti)->GetFieldDeclaringClass(native_ti,type,field,&declaring)!=JVMTI_ERROR_NONE)return 0;
    int metadata=native_original.IsSameObject(env,declaring,native_impl_class)
            ||native_original.IsSameObject(env,declaring,native_library_class)
            ||native_unloader_class&&native_original.IsSameObject(env,declaring,native_unloader_class);
    native_original.DeleteLocalRef(env,declaring);if(!metadata)return 0;
    NativeThread *state=native_thread();NativeLibraryScope *scope=state?state->library:NULL;
    // The genuine JDK loader must publish its handle even if the group stopped
    // during OnLoad. The same Java native frame does not authorize DLL user code.
    int active=scope&&!scope->unloading&&scope->phase==1&&native_original.IsSameObject(env,scope->library->identity,receiver);
    // Loads begun before Java scope weaving still have an exact native call.
    // This permits only that call's original java.dll metadata publication;
    // it does not invent a tracked library owner for the installation gap.
    int installing=state&&state->unobservedLibrary&&native_original.IsSameObject(env,state->unobservedLibrary,receiver);
    if(receiver&&(active||installing)&&(field==native_impl_handle||field==native_impl_version)&&native_image(caller)==native_java_image)return 1;
    native_refuse(env,"RONOVA_NATIVE_LIBRARY_METADATA_WRITE_REFUSED");return -1;
}
#define NATIVE_FIELD(NAME,TYPE,SLOT,BOX,SIG) \
static void JNICALL native_set_##NAME(JNIEnv *env,jobject object,jfieldID field,TYPE value){jclass type=native_original.GetObjectClass(env,object);int loader=type?native_loader_field(env,object,type,field,__builtin_return_address(0)):0;if(loader){if(loader>0)native_original.Set##NAME##Field(env,object,field,value);if(type)native_original.DeleteLocalRef(env,type);return;}jvalue v;memset(&v,0,sizeof(v));v.SLOT=value;jobject boxed=native_box(env,BOX,SIG,v);if(!boxed||native_original.ExceptionCheck(env)){if(boxed)native_original.DeleteLocalRef(env,boxed);if(type)native_original.DeleteLocalRef(env,type);return;}jobject gate=type?native_begin_field_mutation(env,object,type,field):NULL;int applied=gate&&native_field_allowed(env,object,type,field,boxed,0);if(applied)native_original.Set##NAME##Field(env,object,field,value);native_end_mutation(env,gate,applied);if(type)native_original.DeleteLocalRef(env,type);native_original.DeleteLocalRef(env,boxed);} \
static void JNICALL native_static_##NAME(JNIEnv *env,jclass type,jfieldID field,TYPE value){if(native_loader_field(env,NULL,type,field,__builtin_return_address(0)))return;jvalue v;memset(&v,0,sizeof(v));v.SLOT=value;jobject boxed=native_box(env,BOX,SIG,v);if(!boxed||native_original.ExceptionCheck(env)){if(boxed)native_original.DeleteLocalRef(env,boxed);return;}jclass declaring=NULL;if((*native_ti)->GetFieldDeclaringClass(native_ti,type,field,&declaring)!=JVMTI_ERROR_NONE){native_original.DeleteLocalRef(env,boxed);native_refuse(env,"NATIVE_FIELD_DECLARING_UNAVAILABLE");return;}jobject gate=native_begin_field_mutation(env,declaring,declaring,field);int applied=gate&&native_field_allowed(env,NULL,type,field,boxed,1);if(applied)native_original.SetStatic##NAME##Field(env,type,field,value);native_end_mutation(env,gate,applied);native_original.DeleteLocalRef(env,declaring);native_original.DeleteLocalRef(env,boxed);}
NATIVE_FIELD(Boolean,jboolean,z,"java/lang/Boolean","(Z)Ljava/lang/Boolean;")
NATIVE_FIELD(Byte,jbyte,b,"java/lang/Byte","(B)Ljava/lang/Byte;")
NATIVE_FIELD(Char,jchar,c,"java/lang/Character","(C)Ljava/lang/Character;")
NATIVE_FIELD(Short,jshort,s,"java/lang/Short","(S)Ljava/lang/Short;")
NATIVE_FIELD(Int,jint,i,"java/lang/Integer","(I)Ljava/lang/Integer;")
NATIVE_FIELD(Long,jlong,j,"java/lang/Long","(J)Ljava/lang/Long;")
NATIVE_FIELD(Float,jfloat,f,"java/lang/Float","(F)Ljava/lang/Float;")
NATIVE_FIELD(Double,jdouble,d,"java/lang/Double","(D)Ljava/lang/Double;")
static void JNICALL native_set_Object(JNIEnv *env,jobject object,jfieldID field,jobject value){jclass type=native_original.GetObjectClass(env,object);if(type&&native_loader_field(env,object,type,field,__builtin_return_address(0))){native_original.DeleteLocalRef(env,type);return;}jobject gate=type?native_begin_field_mutation(env,object,type,field):NULL;int applied=gate&&native_field_allowed(env,object,type,field,value,0);if(applied)native_original.SetObjectField(env,object,field,value);native_end_mutation(env,gate,applied);if(type)native_original.DeleteLocalRef(env,type);}
static void JNICALL native_static_Object(JNIEnv *env,jclass type,jfieldID field,jobject value){if(native_loader_field(env,NULL,type,field,__builtin_return_address(0)))return;jclass declaring=NULL;if((*native_ti)->GetFieldDeclaringClass(native_ti,type,field,&declaring)!=JVMTI_ERROR_NONE){native_refuse(env,"NATIVE_FIELD_DECLARING_UNAVAILABLE");return;}jobject gate=native_begin_field_mutation(env,declaring,declaring,field);int applied=gate&&native_field_allowed(env,NULL,type,field,value,1);if(applied)native_original.SetStaticObjectField(env,type,field,value);native_end_mutation(env,gate,applied);native_original.DeleteLocalRef(env,declaring);}
static int native_array_allowed(JNIEnv *env,jarray array,jsize index,jobject value){
    if(native_original.ExceptionCheck(env))return 0;
    if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_ARRAY_WRITE");return 0;}
    jboolean allowed=native_original.CallStaticBooleanMethod(env,native_tasks,native_array_query,array,index,value);
    if(!allowed&&!native_original.ExceptionCheck(env))native_refuse(env,"RONOVA_PROTECTED_NATIVE_ARRAY_WRITE");return allowed&&!native_original.ExceptionCheck(env);
}
static int native_primitive_array_policy(JNIEnv *env,jarray array){
    if(native_original.ExceptionCheck(env))return 0;
    if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_ARRAY_WRITE");return 0;}
    jint policy=native_original.CallStaticIntMethod(env,native_tasks,native_array_policy,array);
    if(native_original.ExceptionCheck(env))return 0;
    if(policy==1||policy==2)return policy;
    native_refuse(env,"RONOVA_PROTECTED_NATIVE_ARRAY_WRITE");return 0;
}
/* Failure formatting through Throwable invokes guarded Java again. Record the
 * still-active JNI boundary directly from JVMTI before restoring the exception. */
static void native_array_failure_frames(JNIEnv *env){
    jthread thread=NULL;jvmtiFrameInfo *frames=NULL;jint depth=0,count=0;
    jvmtiError result=(*native_ti)->GetCurrentThread(native_ti,&thread);
    if(result==JVMTI_ERROR_NONE)result=(*native_ti)->GetFrameCount(native_ti,thread,&depth);
    if(result==JVMTI_ERROR_NONE&&depth>0){
        frames=(jvmtiFrameInfo*)calloc((size_t)depth,sizeof(*frames));
        if(!frames)result=JVMTI_ERROR_OUT_OF_MEMORY;
        else result=(*native_ti)->GetStackTrace(native_ti,thread,0,depth,frames,&count);
    }
    fprintf(stderr,"RONOVA_NATIVE_ARRAY_FAILURE_ACTIVE_FRAMES:%d:%d\n",(int)result,(int)count);
    if(result==JVMTI_ERROR_NONE)for(jint i=0;i<count;i++){
        jclass declaring=NULL;char *owner=NULL,*name=NULL,*descriptor=NULL;
        jvmtiError method=(*native_ti)->GetMethodDeclaringClass(native_ti,frames[i].method,&declaring);
        if(method==JVMTI_ERROR_NONE)method=(*native_ti)->GetClassSignature(native_ti,declaring,&owner,NULL);
        if(method==JVMTI_ERROR_NONE)method=(*native_ti)->GetMethodName(native_ti,frames[i].method,&name,&descriptor,NULL);
        fprintf(stderr,"RONOVA_NATIVE_ARRAY_FAILURE_FRAME:%d:%s#%s%s:%lld:%d\n",(int)i,
                owner?owner:"?",name?name:"?",descriptor?descriptor:"?",(long long)frames[i].location,(int)method);
        if(owner)(*native_ti)->Deallocate(native_ti,(unsigned char*)owner);
        if(name)(*native_ti)->Deallocate(native_ti,(unsigned char*)name);
        if(descriptor)(*native_ti)->Deallocate(native_ti,(unsigned char*)descriptor);
        if(declaring)native_original.DeleteLocalRef(env,declaring);
    }
    free(frames);if(thread)native_original.DeleteLocalRef(env,thread);fflush(stderr);
}
/* Capture the original pending exception before Java's diagnostic printing
 * can enter guarded class loading again. Preserve ExceptionDescribe semantics. */
static void JNICALL native_describe(JNIEnv *env){
    jthrowable failure=native_original.ExceptionOccurred(env);
    if(failure&&!InterlockedCompareExchange(&native_describe_recorded,1,0)){
        native_original.ExceptionClear(env);
        jclass throwable=native_original.FindClass(env,"java/lang/Throwable");
        jfieldID messageField=throwable?native_original.GetFieldID(env,throwable,"detailMessage","Ljava/lang/String;"):NULL;
        jfieldID causeField=throwable?native_original.GetFieldID(env,throwable,"cause","Ljava/lang/Throwable;"):NULL;
        if(native_original.ExceptionCheck(env))native_original.ExceptionClear(env);
        jthrowable chain[16]={failure};unsigned count=1;
        for(unsigned at=0;at<count;at++){
            jclass actual=native_original.GetObjectClass(env,chain[at]);char *signature=NULL;
            if(actual)(*native_ti)->GetClassSignature(native_ti,actual,&signature,NULL);
            jstring message=messageField?(jstring)native_original.GetObjectField(env,chain[at],messageField):NULL;
            const char *detail=message&&!native_original.ExceptionCheck(env)?native_original.GetStringUTFChars(env,message,NULL):NULL;
            fprintf(stderr,"RONOVA_NATIVE_DESCRIBE_FAILURE_CHAIN:%u:%s:%s\n",at,signature?signature:"?",detail?detail:"");
            if(detail)native_original.ReleaseStringUTFChars(env,message,detail);
            if(message)native_original.DeleteLocalRef(env,message);
            if(native_original.ExceptionCheck(env))native_original.ExceptionClear(env);
            code_exception_frames(env,chain[at],at);
            if(native_original.ExceptionCheck(env))native_original.ExceptionClear(env);
            // InvocationTargetException stores its overridden cause in target.
            jfieldID nextField=signature&&!strcmp(signature,"Ljava/lang/reflect/InvocationTargetException;")
                    ?native_original.GetFieldID(env,actual,"target","Ljava/lang/Throwable;"):causeField;
            jthrowable next=nextField?(jthrowable)native_original.GetObjectField(env,chain[at],nextField):NULL;
            if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);
            if(actual)native_original.DeleteLocalRef(env,actual);
            if(native_original.ExceptionCheck(env)){native_original.ExceptionClear(env);if(next)native_original.DeleteLocalRef(env,next);break;}
            if(!next)break;int repeated=0;
            for(unsigned i=0;i<count;i++)if(native_original.IsSameObject(env,next,chain[i])){repeated=1;break;}
            if(repeated||count==16){native_original.DeleteLocalRef(env,next);break;}
            chain[count++]=next;
        }
        for(unsigned i=1;i<count;i++)native_original.DeleteLocalRef(env,chain[i]);
        if(throwable)native_original.DeleteLocalRef(env,throwable);
        if(native_original.ExceptionCheck(env))native_original.ExceptionClear(env);
        fflush(stderr);native_original.Throw(env,failure);
    }
    if(failure)native_original.DeleteLocalRef(env,failure);
    native_original.ExceptionDescribe(env);
}
/* libinstrument clears a failed SetByteArrayRegion exception before reporting
 * its assertion. Preserve that exact exception, not a diagnostic's failure. */
static void native_array_failure(JNIEnv *env,const char *phase,char kind,jsize start,jsize length){
    jthrowable failure=native_original.ExceptionOccurred(env);if(!failure)return;
    native_original.ExceptionClear(env);
    jclass actual=native_original.GetObjectClass(env,failure),throwable=NULL;char *signature=NULL;
    jstring message=NULL;const char *details=NULL;
    if(actual)(*native_ti)->GetClassSignature(native_ti,actual,&signature,NULL);
    throwable=native_original.FindClass(env,"java/lang/Throwable");
    if(throwable){jfieldID field=native_original.GetFieldID(env,throwable,"detailMessage","Ljava/lang/String;");
        if(field)message=(jstring)native_original.GetObjectField(env,failure,field);}
    if(message&&!native_original.ExceptionCheck(env))details=native_original.GetStringUTFChars(env,message,NULL);
    fprintf(stderr,"RONOVA_NATIVE_ARRAY_FAILURE:%s:%c:%d:%d:%s:%s\n",phase,kind,(int)start,(int)length,
            signature?signature:"UNAVAILABLE_EXCEPTION_TYPE",details?details:"");fflush(stderr);
    if(details)native_original.ReleaseStringUTFChars(env,message,details);
    if(message)native_original.DeleteLocalRef(env,message);if(throwable)native_original.DeleteLocalRef(env,throwable);
    if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);if(actual)native_original.DeleteLocalRef(env,actual);
    if(native_original.ExceptionCheck(env))native_original.ExceptionClear(env);
    native_array_failure_frames(env);
    if(native_original.ExceptionCheck(env))native_original.ExceptionClear(env);
    native_original.Throw(env,failure);native_original.DeleteLocalRef(env,failure);
}
static int native_pin_allowed(JNIEnv *env,jarray array){
    if(native_original.ExceptionCheck(env))return 0;
    if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_ARRAY_POINTER");return 0;}
    jboolean allowed=native_original.CallStaticBooleanMethod(env,native_tasks,native_pin_query,array);
    if(!allowed&&!native_original.ExceptionCheck(env))native_refuse(env,"RONOVA_PROTECTED_NATIVE_ARRAY_POINTER");return allowed&&!native_original.ExceptionCheck(env);
}
static void JNICALL native_set_array_Object(JNIEnv *env,jobjectArray array,jsize index,jobject value){jobject gate=native_begin_array_mutation(env,array,index,1);int applied=gate&&native_array_allowed(env,array,index,value);if(applied)native_original.SetObjectArrayElement(env,array,index,value);native_end_mutation(env,gate,applied);}
static void *native_array_copy(JNIEnv *env,jarray array,jboolean *copy,char expected,int critical,void *caller);
static void native_array_release(JNIEnv *env,jarray array,void *contents,jint mode,char expected,int critical,void *caller);
#define NATIVE_ARRAY(NAME,TYPE,SLOT,BOX,SIG) \
static TYPE *JNICALL native_get_array_##NAME(JNIEnv *env,TYPE##Array array,jboolean *copy){return (TYPE*)native_array_copy(env,array,copy,SIG[1],0,__builtin_return_address(0));} \
static void JNICALL native_release_array_##NAME(JNIEnv *env,TYPE##Array array,TYPE *contents,jint mode){native_array_release(env,array,contents,mode,SIG[1],0,__builtin_return_address(0));} \
static void JNICALL native_set_array_##NAME(JNIEnv *env,TYPE##Array array,jsize start,jsize length,const TYPE *contents){ \
    jsize size=native_original.GetArrayLength(env,array);if(native_original.ExceptionCheck(env))return; \
    if(start<0||length<0||start>size-length){native_original.Set##NAME##ArrayRegion(env,array,start,length,contents);return;} \
    if(length==0){native_original.Set##NAME##ArrayRegion(env,array,start,length,contents);return;} \
    size_t bytes=(size_t)length*sizeof(TYPE);TYPE *snapshot=(TYPE*)malloc(bytes);if(!snapshot){native_refuse(env,"RONOVA_NATIVE_ARRAY_SNAPSHOT_ALLOCATION_FAILED");return;}memcpy(snapshot,contents,bytes); \
    jobject gate=native_begin_array_mutation(env,array,start,length);if(!gate){native_array_failure(env,"BEGIN",SIG[1],start,length);free(snapshot);return;} \
    int policy=native_primitive_array_policy(env,array);if(!policy){native_end_mutation(env,gate,0);native_array_failure(env,"POLICY",SIG[1],start,length);free(snapshot);return;} \
    if(policy==1)for(jsize i=0;i<length;i++){jvalue value;memset(&value,0,sizeof(value));value.SLOT=snapshot[i];jobject boxed=native_box(env,BOX,SIG,value); \
        int allowed=boxed&&native_array_allowed(env,array,start+i,boxed);if(boxed)native_original.DeleteLocalRef(env,boxed);if(!allowed){native_end_mutation(env,gate,0);native_array_failure(env,"VALUE",SIG[1],start+i,1);free(snapshot);return;}} \
    native_original.Set##NAME##ArrayRegion(env,array,start,length,snapshot);native_end_mutation(env,gate,1);native_array_failure(env,"COMMIT",SIG[1],start,length);free(snapshot);}
NATIVE_ARRAY(Boolean,jboolean,z,"java/lang/Boolean","(Z)Ljava/lang/Boolean;")
NATIVE_ARRAY(Byte,jbyte,b,"java/lang/Byte","(B)Ljava/lang/Byte;")
NATIVE_ARRAY(Char,jchar,c,"java/lang/Character","(C)Ljava/lang/Character;")
NATIVE_ARRAY(Short,jshort,s,"java/lang/Short","(S)Ljava/lang/Short;")
NATIVE_ARRAY(Int,jint,i,"java/lang/Integer","(I)Ljava/lang/Integer;")
NATIVE_ARRAY(Long,jlong,j,"java/lang/Long","(J)Ljava/lang/Long;")
NATIVE_ARRAY(Float,jfloat,f,"java/lang/Float","(F)Ljava/lang/Float;")
NATIVE_ARRAY(Double,jdouble,d,"java/lang/Double","(D)Ljava/lang/Double;")
#include "arrays.c"
static void *JNICALL native_get_critical(JNIEnv *env,jarray array,jboolean *copy){return native_array_copy(env,array,copy,0,1,__builtin_return_address(0));}
static void JNICALL native_release_critical(JNIEnv *env,jarray array,void *contents,jint mode){native_array_release(env,array,contents,mode,0,1,__builtin_return_address(0));}
static jobjectArray native_registration_sources(JNIEnv *env,void *implementation,void *caller){
    NativeThread *state=native_thread();OwnerLink *sources=NULL;int ready=1;
    HMODULE image=native_observe_image(implementation);
    HMODULE callerImage=native_observe_image(caller);
    AcquireSRWLockShared(&native_records);
    if(state){for(unsigned i=0;i<state->depth;i++)ready=ready&&native_owner_merge(&sources,state->stack[i]->owners);
        for(NativeLibraryScope *scope=state->library;scope;scope=scope->previous)ready=ready&&native_owner_merge(&sources,scope->library->owners);}
    for(NativeLibrary *library=native_libraries;library;library=library->next)if((image&&library->handle==image||callerImage&&library->handle==callerImage)&&InterlockedCompareExchange(&library->alive,0,0))ready=ready&&native_owner_merge(&sources,library->owners);
    for(Binding *binding=native_bindings;binding;binding=binding->next)if(binding->entry&&binding->entry==implementation)ready=ready&&native_owner_merge(&sources,binding->owners);
    ReleaseSRWLockShared(&native_records);
    if(!ready){native_owner_links_free(sources);native_refuse(env,"NATIVE_REGISTRATION_SOURCE_CAPACITY");return NULL;}
    jint count=0;for(OwnerLink *p=sources;p;p=p->next)count++;
    jclass module=native_original.FindClass(env,"java/lang/Module");jobjectArray result=module?native_original.NewObjectArray(env,count,module,NULL):NULL;
    jint index=0;for(OwnerLink *p=sources;result&&p;p=p->next){jobject owner=native_original.NewLocalRef(env,p->owner->module);if(!owner){native_refuse(env,"NATIVE_REGISTRATION_SOURCE_UNOBSERVED");break;}native_original.SetObjectArrayElement(env,result,index++,owner);native_original.DeleteLocalRef(env,owner);}
    if(module)native_original.DeleteLocalRef(env,module);native_owner_links_free(sources);return result;
}
static int native_registration_allowed(JNIEnv *env,jclass type,void *implementation,void *caller){
    if(native_original.ExceptionCheck(env))return 0;
    if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_REGISTRATION");return 0;}
    jobjectArray sources=native_registration_sources(env,implementation,caller);
    jboolean allowed=sources&&!native_original.ExceptionCheck(env)?native_original.CallStaticBooleanMethod(env,native_tasks,native_registration_query,type,sources):JNI_FALSE;
    if(sources)native_original.DeleteLocalRef(env,sources);
    if(!allowed&&!native_original.ExceptionCheck(env))native_refuse(env,"RONOVA_NATIVE_REGISTRATION_REFUSED");return allowed&&!native_original.ExceptionCheck(env);
}
static void native_registration_free(JNINativeMethod *methods,jint count){
    if(!methods)return;
    for(jint i=0;i<count;i++){free(methods[i].name);free(methods[i].signature);}
    free(methods);
}
static jclass native_registration_declaring(JNIEnv *env,jclass type,const JNINativeMethod *entry,jmethodID *actual){
    jclass current=(jclass)native_original.NewLocalRef(env,type);
    while(current&&!native_original.ExceptionCheck(env)){
        jint count=0;jmethodID *methods=NULL;
        if((*native_ti)->GetClassMethods(native_ti,current,&count,&methods)!=JVMTI_ERROR_NONE){
            native_original.DeleteLocalRef(env,current);native_refuse(env,"RONOVA_NATIVE_DECLARATION_UNOBSERVED");return NULL;
        }
        int found=0,failed=0;
        for(jint i=0;i<count&&!found&&!failed;i++){
            char *name=NULL,*signature=NULL;
            jvmtiError result=(*native_ti)->GetMethodName(native_ti,methods[i],&name,&signature,NULL);
            failed=result!=JVMTI_ERROR_NONE;
            if(!failed&&strcmp(name,entry->name)==0&&strcmp(signature,entry->signature)==0){
                jint modifiers=0;
                result=(*native_ti)->GetMethodModifiers(native_ti,methods[i],&modifiers);
                // The VM resolves the first matching declaration, including a
                // non-native declaration that must not fall through to its parent.
                found=result==JVMTI_ERROR_NONE&&(modifiers&0x0100)!=0;
                if(found)*actual=methods[i];
                failed=!found;
            }
            if(name)(*native_ti)->Deallocate(native_ti,(unsigned char*)name);
            if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);
        }
        if(methods)(*native_ti)->Deallocate(native_ti,(unsigned char*)methods);
        if(found)return current;
        if(failed){native_original.DeleteLocalRef(env,current);native_refuse(env,"RONOVA_NATIVE_DECLARATION_UNOBSERVED");return NULL;}
        jclass parent=native_original.GetSuperclass(env,current);
        native_original.DeleteLocalRef(env,current);current=parent;
    }
    if(current)native_original.DeleteLocalRef(env,current);
    native_refuse(env,"RONOVA_NATIVE_DECLARATION_UNOBSERVED");return NULL;
}
static jint JNICALL native_register(JNIEnv *env,jclass type,const JNINativeMethod *methods,jint count){
    void *caller=__builtin_return_address(0);
    if(native_original.ExceptionCheck(env)||native_stopped(env)
            ||!native_original.CallStaticBooleanMethod(env,native_tasks,native_registration_target,type)){
        native_refuse(env,"RONOVA_NATIVE_REGISTRATION_REFUSED");return JNI_ERR;
    }
    if(count<0||(count>0&&!methods)){native_refuse(env,"RONOVA_NATIVE_METHOD_TABLE_REQUIRED");return JNI_ERR;}
    if(count==0)return native_original.RegisterNatives(env,type,NULL,0);
    if((size_t)count>SIZE_MAX/sizeof(JNINativeMethod)){native_refuse(env,"RONOVA_NATIVE_METHOD_TABLE_CAPACITY");return JNI_ERR;}
    JNINativeMethod *snapshot=(JNINativeMethod*)calloc((size_t)count,sizeof(*snapshot));
    if(!snapshot){native_refuse(env,"RONOVA_NATIVE_METHOD_TABLE_CAPACITY");return JNI_ERR;}
    jmethodID *actual=(jmethodID*)calloc((size_t)count,sizeof(*actual));OwnerLink *owners=NULL;NativeThread *state=native_thread();
    NativeRegistration registration={actual,count,NULL,NULL,state&&state->library?state->library->library:NULL,state?state->registration:NULL};
    if(!actual||!state||!native_capture_scope_owners(env,state,&owners)){free(actual);native_owner_links_free(owners);native_registration_free(snapshot,count);native_refuse(env,"NATIVE_REGISTRATION_SOURCE_CAPACITY");return JNI_ERR;}
    HMODULE callerImage=native_observe_image(caller);jweak origin=native_origin(state);
    AcquireSRWLockShared(&native_records);
    for(NativeLibrary *library=native_libraries;library;library=library->next)if(callerImage&&library->handle==callerImage&&InterlockedCompareExchange(&library->alive,0,0)){
        if(!native_owner_merge(&owners,library->owners)){ReleaseSRWLockShared(&native_records);free(actual);native_owner_links_free(owners);native_registration_free(snapshot,count);native_refuse(env,"NATIVE_REGISTRATION_SOURCE_CAPACITY");return JNI_ERR;}
        if(!origin)origin=library->origin;
        if(!registration.loading&&!InterlockedCompareExchange(&library->complete,0,0))registration.loading=library;
    }
    registration.owners=owners;registration.origin=origin;ReleaseSRWLockShared(&native_records);
    jint result=JNI_ERR;
    for(jint i=0;i<count;i++){
        // Copy both the table and its strings before authorizing any entry. The
        // original JNI function receives this same immutable call-local table.
        JNINativeMethod entry=methods[i];
        if(!entry.name||!entry.signature){native_refuse(env,"RONOVA_NATIVE_METHOD_TABLE_REQUIRED");goto finished;}
        snapshot[i].name=_strdup(entry.name);snapshot[i].signature=_strdup(entry.signature);snapshot[i].fnPtr=entry.fnPtr;
        if(!snapshot[i].name||!snapshot[i].signature){native_refuse(env,"RONOVA_NATIVE_METHOD_TABLE_CAPACITY");goto finished;}
        if(snapshot[i].fnPtr&&native_image(snapshot[i].fnPtr)==native_image((void*)native_register)){
            native_refuse(env,"RONOVA_NATIVE_CONTROLLER_ALIAS_REFUSED");goto finished;
        }
    }
    for(jint i=0;i<count;i++){
        jclass declaring=native_registration_declaring(env,type,&snapshot[i],&actual[i]);
        if(!declaring)goto finished;
        int allowed=native_registration_allowed(env,declaring,snapshot[i].fnPtr,caller);
        native_original.DeleteLocalRef(env,declaring);if(!allowed)goto finished;
    }
    state->registration=&registration;result=native_original.RegisterNatives(env,type,snapshot,count);state->registration=registration.previous;
    if(result==JNI_OK&&state->unsafeInstalling&&!native_unsafe_registered(env,state,type,snapshot,actual,count,caller))result=JNI_ERR;
finished:
    free(actual);native_owner_links_free(owners);native_registration_free(snapshot,count);return result;
}
static jint JNICALL native_unregister(JNIEnv *env,jclass type){
    if(!native_registration_allowed(env,type,NULL,__builtin_return_address(0)))return JNI_ERR;
    jint result=native_original.UnregisterNatives(env,type);
    if(result==JNI_OK){AcquireSRWLockExclusive(&native_records);for(Binding *binding=native_bindings;binding;binding=binding->next)
        if(native_original.IsSameObject(env,binding->type,type))InterlockedExchange(&binding->current,0);ReleaseSRWLockExclusive(&native_records);}
    return result;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_clearField0(JNIEnv *env,jclass controller,jobject field,jobject receiver,jobject expected){
    (void)controller;
    jmethodID writer=native_original.GetStaticMethodID(env,native_tasks,"recoveryWriter","()Z");
    if(!writer||!native_original.CallStaticBooleanMethod(env,native_tasks,writer)){native_refuse(env,"RECOVERY_FIELD_WRITER_REQUIRED");return JNI_FALSE;}
    jclass reflection=native_original.GetObjectClass(env,field);
    jmethodID declaration=reflection?native_original.GetMethodID(env,reflection,"getDeclaringClass","()Ljava/lang/Class;"):NULL;
    jclass type=declaration?(jclass)native_original.CallObjectMethod(env,field,declaration):NULL;
    jfieldID id=native_original.FromReflectedField(env,field);char *name=NULL,*signature=NULL;jint modifiers=0;jboolean cleared=JNI_FALSE;
    if(type&&id&&(*native_ti)->GetFieldName(native_ti,type,id,&name,&signature,NULL)==JVMTI_ERROR_NONE
            &&(*native_ti)->GetFieldModifiers(native_ti,type,id,&modifiers)==JVMTI_ERROR_NONE){
        int statik=(modifiers&0x0008)!=0;
        if(!statik&&!receiver){native_refuse(env,"RECOVERY_FIELD_RECEIVER_REQUIRED");goto finished;}
        if(signature[0]=='L'||signature[0]=='['){
            jobject actual=statik?native_original.GetStaticObjectField(env,type,id):native_original.GetObjectField(env,receiver,id);
            if(native_original.IsSameObject(env,actual,expected)&&!native_original.ExceptionCheck(env)){
                if(statik)native_original.SetStaticObjectField(env,type,id,NULL);else native_original.SetObjectField(env,receiver,id,NULL);cleared=JNI_TRUE;
            }
            if(actual)native_original.DeleteLocalRef(env,actual);
        }else{
#define CLEAR_NATIVE_PRIMITIVE(KIND,NAME,TYPE,BOX,ACCESSOR,SIG) case KIND:{ \
            jclass boxed=native_original.FindClass(env,BOX);jmethodID access=boxed?native_original.GetMethodID(env,boxed,ACCESSOR,SIG):NULL; \
            if(expected&&boxed&&access&&native_original.IsInstanceOf(env,expected,boxed)){ \
                TYPE prior=native_original.Call##NAME##Method(env,expected,access); \
                TYPE actual=statik?native_original.GetStatic##NAME##Field(env,type,id):native_original.Get##NAME##Field(env,receiver,id); \
                if(!native_original.ExceptionCheck(env)&&memcmp(&prior,&actual,sizeof(TYPE))==0){ \
                    if(statik)native_original.SetStatic##NAME##Field(env,type,id,(TYPE)0);else native_original.Set##NAME##Field(env,receiver,id,(TYPE)0);cleared=JNI_TRUE;}} \
            if(boxed)native_original.DeleteLocalRef(env,boxed);break;}
            switch(signature[0]){
                CLEAR_NATIVE_PRIMITIVE('Z',Boolean,jboolean,"java/lang/Boolean","booleanValue","()Z")
                CLEAR_NATIVE_PRIMITIVE('B',Byte,jbyte,"java/lang/Byte","byteValue","()B")
                CLEAR_NATIVE_PRIMITIVE('C',Char,jchar,"java/lang/Character","charValue","()C")
                CLEAR_NATIVE_PRIMITIVE('S',Short,jshort,"java/lang/Short","shortValue","()S")
                CLEAR_NATIVE_PRIMITIVE('I',Int,jint,"java/lang/Integer","intValue","()I")
                CLEAR_NATIVE_PRIMITIVE('J',Long,jlong,"java/lang/Long","longValue","()J")
                CLEAR_NATIVE_PRIMITIVE('F',Float,jfloat,"java/lang/Float","floatValue","()F")
                CLEAR_NATIVE_PRIMITIVE('D',Double,jdouble,"java/lang/Double","doubleValue","()D")
            }
#undef CLEAR_NATIVE_PRIMITIVE
        }
    }
finished:
    if(name)(*native_ti)->Deallocate(native_ti,(unsigned char*)name);if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);
    if(type)native_original.DeleteLocalRef(env,type);if(reflection)native_original.DeleteLocalRef(env,reflection);
    return native_original.ExceptionCheck(env)?JNI_FALSE:cleared;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_restoreField0(JNIEnv *env,jclass controller,jobject field,jobject receiver,jobject expected,jobject incoming){
    (void)controller;
    jmethodID writer=native_original.GetStaticMethodID(env,native_tasks,"recoveryWriter","()Z");
    if(!writer||!native_original.CallStaticBooleanMethod(env,native_tasks,writer)){native_refuse(env,"RECOVERY_FIELD_WRITER_REQUIRED");return JNI_FALSE;}
    if(!field||!native_ti)return JNI_FALSE;
    jclass reflection=native_original.GetObjectClass(env,field);
    jmethodID declaration=reflection?native_original.GetMethodID(env,reflection,"getDeclaringClass","()Ljava/lang/Class;"):NULL;
    jclass type=declaration?(jclass)native_original.CallObjectMethod(env,field,declaration):NULL;
    jfieldID id=native_original.FromReflectedField(env,field);char *name=NULL,*signature=NULL;jint modifiers=0,status=0;jboolean restored=JNI_FALSE;
    if(!type||!id||native_original.ExceptionCheck(env)||(*native_ti)->GetFieldName(native_ti,type,id,&name,&signature,NULL)!=JVMTI_ERROR_NONE
            ||(*native_ti)->GetFieldModifiers(native_ti,type,id,&modifiers)!=JVMTI_ERROR_NONE)goto restore_finished;
    int statik=(modifiers&0x0008)!=0;
    if(statik){if((*native_ti)->GetClassStatus(native_ti,type,&status)!=JVMTI_ERROR_NONE||!(status&JVMTI_CLASS_STATUS_INITIALIZED))goto restore_finished;}
    else if(!receiver||!native_original.IsInstanceOf(env,receiver,type))goto restore_finished;
    if(signature[0]=='L'||signature[0]=='['){
        jmethodID valueType=native_original.GetMethodID(env,reflection,"getType","()Ljava/lang/Class;");
        jclass expectedType=valueType?(jclass)native_original.CallObjectMethod(env,field,valueType):NULL;
        if(!expectedType||incoming&&!native_original.IsInstanceOf(env,incoming,expectedType)){if(expectedType)native_original.DeleteLocalRef(env,expectedType);goto restore_finished;}
        jobject actual=statik?native_original.GetStaticObjectField(env,type,id):native_original.GetObjectField(env,receiver,id);
        if(!native_original.ExceptionCheck(env)&&native_original.IsSameObject(env,actual,expected)){
            if(statik)native_original.SetStaticObjectField(env,type,id,incoming);else native_original.SetObjectField(env,receiver,id,incoming);
            if(!native_original.ExceptionCheck(env)){jobject after=statik?native_original.GetStaticObjectField(env,type,id):native_original.GetObjectField(env,receiver,id);restored=native_original.IsSameObject(env,after,incoming);if(after)native_original.DeleteLocalRef(env,after);}
        }if(actual)native_original.DeleteLocalRef(env,actual);native_original.DeleteLocalRef(env,expectedType);
    }else{
#define RESTORE_NATIVE_PRIMITIVE(KIND,NAME,TYPE,BOX,ACCESSOR,SIG) case KIND:{ \
        jclass boxed=native_original.FindClass(env,BOX);jmethodID access=boxed?native_original.GetMethodID(env,boxed,ACCESSOR,SIG):NULL; \
        if(expected&&incoming&&boxed&&access&&native_original.IsInstanceOf(env,expected,boxed)&&native_original.IsInstanceOf(env,incoming,boxed)){ \
            TYPE prior=native_original.Call##NAME##Method(env,expected,access),value=native_original.Call##NAME##Method(env,incoming,access); \
            TYPE actual=statik?native_original.GetStatic##NAME##Field(env,type,id):native_original.Get##NAME##Field(env,receiver,id); \
            if(!native_original.ExceptionCheck(env)&&memcmp(&prior,&actual,sizeof(TYPE))==0){ \
                if(statik)native_original.SetStatic##NAME##Field(env,type,id,value);else native_original.Set##NAME##Field(env,receiver,id,value); \
                TYPE after=statik?native_original.GetStatic##NAME##Field(env,type,id):native_original.Get##NAME##Field(env,receiver,id); \
                restored=!native_original.ExceptionCheck(env)&&memcmp(&after,&value,sizeof(TYPE))==0;}} \
        if(boxed)native_original.DeleteLocalRef(env,boxed);break;}
        switch(signature[0]){
            RESTORE_NATIVE_PRIMITIVE('Z',Boolean,jboolean,"java/lang/Boolean","booleanValue","()Z")
            RESTORE_NATIVE_PRIMITIVE('B',Byte,jbyte,"java/lang/Byte","byteValue","()B")
            RESTORE_NATIVE_PRIMITIVE('C',Char,jchar,"java/lang/Character","charValue","()C")
            RESTORE_NATIVE_PRIMITIVE('S',Short,jshort,"java/lang/Short","shortValue","()S")
            RESTORE_NATIVE_PRIMITIVE('I',Int,jint,"java/lang/Integer","intValue","()I")
            RESTORE_NATIVE_PRIMITIVE('J',Long,jlong,"java/lang/Long","longValue","()J")
            RESTORE_NATIVE_PRIMITIVE('F',Float,jfloat,"java/lang/Float","floatValue","()F")
            RESTORE_NATIVE_PRIMITIVE('D',Double,jdouble,"java/lang/Double","doubleValue","()D")
            default:break;
        }
#undef RESTORE_NATIVE_PRIMITIVE
    }
restore_finished:
    if(name)(*native_ti)->Deallocate(native_ti,(unsigned char *)name);if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char *)signature);
    if(type)native_original.DeleteLocalRef(env,type);if(reflection)native_original.DeleteLocalRef(env,reflection);return restored;
}
static jclass JNICALL native_define(JNIEnv *env,const char *name,jobject loader,const jbyte *bytes,jsize length){
    if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_DEFINE_CLASS");return NULL;}
    NativeThread *state=native_thread();jweak origin=native_origin(state);if(!origin)return native_original.DefineClass(env,name,loader,bytes,length);
    state->control++;jclass declaring=(jclass)native_original.NewLocalRef(env,origin);
    jbyteArray original=native_original.NewByteArray(env,length);if(original)native_original.SetByteArrayRegion(env,original,0,length,bytes);
    NativeDefinition definition={declaring,loader,original,1,state->definition};state->definition=&definition;
    jbyteArray image=declaring&&original&&!native_original.ExceptionCheck(env)?(jbyteArray)native_original.CallStaticObjectMethod(env,native_definitions,native_image_query,declaring,loader,original):NULL;
    jclass actual=NULL;jbyte *contents=image&&!native_original.ExceptionCheck(env)?native_original.GetByteArrayElements(env,image,NULL):NULL;
    if(contents){actual=native_original.DefineClass(env,name,loader,contents,native_original.GetArrayLength(env,image));native_original.ReleaseByteArrayElements(env,image,contents,JNI_ABORT);}
    jclass previous=state->defined;state->defined=actual;
    if(actual&&!native_original.ExceptionCheck(env))native_original.CallStaticVoidMethod(env,native_definitions,native_defined_query,declaring,actual);
    state->defined=previous;
    if(image){
        jthrowable pending=native_original.ExceptionOccurred(env);if(pending)native_original.ExceptionClear(env);
        definition.phase=3;
        native_original.CallStaticVoidMethod(env,native_definitions,native_after_query,declaring);
        if(pending){if(native_original.ExceptionCheck(env)){native_original.ExceptionClear(env);InterlockedIncrement(&native_failures);}native_original.Throw(env,pending);native_original.DeleteLocalRef(env,pending);}
    }
    state->definition=definition.previous;
    if(image)native_original.DeleteLocalRef(env,image);if(original)native_original.DeleteLocalRef(env,original);if(declaring)native_original.DeleteLocalRef(env,declaring);state->control--;return actual;
}
static jobject native_created(JNIEnv *env,jobject actual){
    NativeThread *state=native_thread();jweak origin=native_origin(state);if(!actual||!origin||native_original.ExceptionCheck(env))return actual;
    jclass declaring=(jclass)native_original.NewLocalRef(env,origin);jobject previous=state->created;state->created=actual;
    if(declaring)native_original.CallStaticVoidMethod(env,native_tasks,native_created_query,declaring,actual);
    state->created=previous;if(declaring)native_original.DeleteLocalRef(env,declaring);return actual;
}
static jobject JNICALL native_new(JNIEnv *env,jclass type,jmethodID method,...){if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_ALLOCATION");return NULL;}va_list args;va_start(args,method);jobject result=native_original.NewObjectV(env,type,method,args);va_end(args);return native_created(env,result);}
static jobject JNICALL native_newV(JNIEnv *env,jclass type,jmethodID method,va_list args){if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_ALLOCATION");return NULL;}return native_created(env,native_original.NewObjectV(env,type,method,args));}
static jobject JNICALL native_newA(JNIEnv *env,jclass type,jmethodID method,const jvalue *args){if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_ALLOCATION");return NULL;}return native_created(env,native_original.NewObjectA(env,type,method,args));}
static jobject JNICALL native_allocate(JNIEnv *env,jclass type){if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_ALLOCATION");return NULL;}return native_created(env,native_original.AllocObject(env,type));}
static jclass JNICALL native_find(JNIEnv *env,const char *name){if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_CLASS_INITIALIZATION");return NULL;}return native_original.FindClass(env,name);}
#define NATIVE_LOOKUP(NAME,TYPE) \
static TYPE JNICALL native_lookup_##NAME(JNIEnv *env,jclass type,const char *name,const char *signature){if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_CLASS_INITIALIZATION");return NULL;}return native_original.NAME(env,type,name,signature);}
NATIVE_LOOKUP(GetMethodID,jmethodID) NATIVE_LOOKUP(GetStaticMethodID,jmethodID) NATIVE_LOOKUP(GetFieldID,jfieldID) NATIVE_LOOKUP(GetStaticFieldID,jfieldID)
#define NATIVE_CALL(NAME,TYPE,ZERO) \
static TYPE JNICALL native_call_##NAME(JNIEnv *env,jobject object,jmethodID method,...){if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_CALLBACK");return ZERO;}va_list args;va_start(args,method);TYPE result=native_original.Call##NAME##MethodV(env,object,method,args);va_end(args);return result;} \
static TYPE JNICALL native_call_##NAME##V(JNIEnv *env,jobject object,jmethodID method,va_list args){if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_CALLBACK");return ZERO;}return native_original.Call##NAME##MethodV(env,object,method,args);} \
static TYPE JNICALL native_call_##NAME##A(JNIEnv *env,jobject object,jmethodID method,const jvalue *args){if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_CALLBACK");return ZERO;}return native_original.Call##NAME##MethodA(env,object,method,args);} \
static TYPE JNICALL native_scall_##NAME(JNIEnv *env,jclass type,jmethodID method,...){if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_CALLBACK");return ZERO;}va_list args;va_start(args,method);TYPE result=native_original.CallStatic##NAME##MethodV(env,type,method,args);va_end(args);return result;} \
static TYPE JNICALL native_scall_##NAME##V(JNIEnv *env,jclass type,jmethodID method,va_list args){if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_CALLBACK");return ZERO;}return native_original.CallStatic##NAME##MethodV(env,type,method,args);} \
static TYPE JNICALL native_scall_##NAME##A(JNIEnv *env,jclass type,jmethodID method,const jvalue *args){if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_CALLBACK");return ZERO;}return native_original.CallStatic##NAME##MethodA(env,type,method,args);} \
static TYPE JNICALL native_ncall_##NAME(JNIEnv *env,jobject object,jclass type,jmethodID method,...){if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_CALLBACK");return ZERO;}va_list args;va_start(args,method);TYPE result=native_original.CallNonvirtual##NAME##MethodV(env,object,type,method,args);va_end(args);return result;} \
static TYPE JNICALL native_ncall_##NAME##V(JNIEnv *env,jobject object,jclass type,jmethodID method,va_list args){if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_CALLBACK");return ZERO;}return native_original.CallNonvirtual##NAME##MethodV(env,object,type,method,args);} \
static TYPE JNICALL native_ncall_##NAME##A(JNIEnv *env,jobject object,jclass type,jmethodID method,const jvalue *args){if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_CALLBACK");return ZERO;}return native_original.CallNonvirtual##NAME##MethodA(env,object,type,method,args);}
NATIVE_CALL(Object,jobject,NULL) NATIVE_CALL(Boolean,jboolean,0) NATIVE_CALL(Byte,jbyte,0) NATIVE_CALL(Char,jchar,0) NATIVE_CALL(Short,jshort,0) NATIVE_CALL(Int,jint,0) NATIVE_CALL(Long,jlong,0) NATIVE_CALL(Float,jfloat,0) NATIVE_CALL(Double,jdouble,0)
#define NATIVE_VOID(PREFIX,DECL,ARGS,CALLARGS) \
static void JNICALL native_##PREFIX##Void(JNIEnv *env,DECL,jmethodID method,...){if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_CALLBACK");return;}va_list args;va_start(args,method);native_original.Call##PREFIX##VoidMethodV(env,ARGS,method,args);va_end(args);} \
static void JNICALL native_##PREFIX##VoidV(JNIEnv *env,DECL,jmethodID method,va_list args){if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_CALLBACK");return;}native_original.Call##PREFIX##VoidMethodV(env,ARGS,method,args);} \
static void JNICALL native_##PREFIX##VoidA(JNIEnv *env,DECL,jmethodID method,const jvalue *args){if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_CALLBACK");return;}native_original.Call##PREFIX##VoidMethodA(env,ARGS,method,args);}
NATIVE_VOID(,jobject object,object,0)
NATIVE_VOID(Static,jclass type,type,0)
/* The nonvirtual form has two fixed receiver arguments. */
static void JNICALL native_nonvirtualVoid(JNIEnv *env,jobject object,jclass type,jmethodID method,...){if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_CALLBACK");return;}va_list args;va_start(args,method);native_original.CallNonvirtualVoidMethodV(env,object,type,method,args);va_end(args);}
static void JNICALL native_nonvirtualVoidV(JNIEnv *env,jobject object,jclass type,jmethodID method,va_list args){if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_CALLBACK");return;}native_original.CallNonvirtualVoidMethodV(env,object,type,method,args);}
static void JNICALL native_nonvirtualVoidA(JNIEnv *env,jobject object,jclass type,jmethodID method,const jvalue *args){if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_CALLBACK");return;}native_original.CallNonvirtualVoidMethodA(env,object,type,method,args);}
static void JNICALL native_thread_end(jvmtiEnv *api,JNIEnv *env,jthread thread){
    (void)api;(void)env;(void)thread;
    if(native_tls==TLS_OUT_OF_INDEXES)return;
    NativeThread *state=(NativeThread*)TlsGetValue(native_tls);if(!state)return;
    host_thread_end(env,state);
    code_thread_end(env,state);
    native_file_thread_end(env,state);
    for(unsigned i=state->depth;i>0;i--){
        native_thread_finish(env,state,&state->threadFrames[i-1],0);
        if(state->unsafeFrames[i-1].memoryCall)native_memory_call_end(env,state,state->entries[i-1],NULL,&state->unsafeFrames[i-1]);
        if(state->unsafeFrames[i-1].token){state->control++;native_end_mutation(env,state->unsafeFrames[i-1].token,0);state->control--;InterlockedIncrement(&native_failures);}
        InterlockedDecrement(&state->stack[i-1]->active);
    }
    native_memory_thread_end(env,state);
    while(state->library){NativeLibraryScope *scope=state->library;state->library=scope->previous;
        // An interrupted load has not demonstrated image retirement.
        InterlockedIncrement(&native_failures);SecureZeroMemory(scope,sizeof(*scope));free(scope);}
    // Array copies may have been handed to another native worker. Their actual
    // matching release, rather than creator-thread exit, closes those leases.
    TlsSetValue(native_tls,NULL);SecureZeroMemory(state,sizeof(*state));free(state);
}
static jint native_initialize(JavaVM *vm){
    if(native_events)return JNI_OK;if((*vm)->GetEnv(vm,(void**)&native_ti,JVMTI_VERSION_1_2)!=JNI_OK)return JNI_ERR;native_vm=vm;
    jvmtiCapabilities caps;memset(&caps,0,sizeof(caps));caps.can_generate_native_method_bind_events=1;
    if((*native_ti)->AddCapabilities(native_ti,&caps)!=JVMTI_ERROR_NONE)return JNI_ERR;
    jvmtiEventCallbacks callbacks;memset(&callbacks,0,sizeof(callbacks));callbacks.NativeMethodBind=native_binding;
    callbacks.ThreadEnd=native_thread_end;callbacks.ClassPrepare=code_class_prepared;callbacks.FramePop=code_frame_popped;callbacks.FieldModification=heap_field_modified;
    if((*native_ti)->SetEventCallbacks(native_ti,&callbacks,sizeof(callbacks))!=JVMTI_ERROR_NONE
            ||(*native_ti)->SetEventNotificationMode(native_ti,JVMTI_ENABLE,JVMTI_EVENT_NATIVE_METHOD_BIND,NULL)!=JVMTI_ERROR_NONE
            ||(*native_ti)->SetEventNotificationMode(native_ti,JVMTI_ENABLE,JVMTI_EVENT_THREAD_END,NULL)!=JVMTI_ERROR_NONE
            ||(*native_ti)->SetEventNotificationMode(native_ti,JVMTI_ENABLE,JVMTI_EVENT_CLASS_PREPARE,NULL)!=JVMTI_ERROR_NONE)return JNI_ERR;
    if(native_tls==TLS_OUT_OF_INDEXES)native_tls=TlsAlloc();if(native_tls==TLS_OUT_OF_INDEXES)return JNI_ERR;
    code_initialize();heap_initialize();InterlockedExchange(&native_events,1);return JNI_OK;
}
static int native_bootstrap_class(JNIEnv *env,jclass type,const char *expected){
    if(!native_ti||!type)return 0;char *signature=NULL;jobject loader=NULL;
    int allowed=(*native_ti)->GetClassSignature(native_ti,type,&signature,NULL)==JVMTI_ERROR_NONE
            &&signature&&strcmp(signature,expected)==0&&(*native_ti)->GetClassLoader(native_ti,type,&loader)==JVMTI_ERROR_NONE&&!loader;
    if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);if(loader)(*env)->DeleteLocalRef(env,loader);return allowed;
}
JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *vm,char *options,void *reserved){(void)options;(void)reserved;return native_initialize(vm);}
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm,void *reserved){(void)reserved;return native_initialize(vm)==JNI_OK?JNI_VERSION_1_8:JNI_ERR;}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_initialize0(JNIEnv *env,jclass type,jclass tasks,jclass definitions){
    if(!native_bootstrap_class(env,type,"Ldev/ronova/pro/bootstrap/NativeControl;")
            ||!native_bootstrap_class(env,tasks,"Ldev/ronova/pro/bootstrap/TaskBridge;")
            ||!native_bootstrap_class(env,definitions,"Ldev/ronova/pro/bootstrap/DefinitionBridge;")
            ||native_controller&&!(*env)->IsSameObject(env,native_controller,type)){native_refuse(env,"ACTUAL_NATIVE_CONTROLLER_REQUIRED");return JNI_FALSE;}
    jmethodID control=(*env)->GetStaticMethodID(env,type,"controlCaller","()Z");if(!control||!(*env)->CallStaticBooleanMethod(env,type,control)){native_refuse(env,"NATIVE_CONTROL_AGENT_REQUIRED");return JNI_FALSE;}
    if(native_jni_ready)return JNI_TRUE;
    if(!native_events)return JNI_FALSE;struct JNINativeInterface_ *table=NULL;if((*native_ti)->GetJNIFunctionTable(native_ti,&table)!=JVMTI_ERROR_NONE)return JNI_FALSE;native_original=*table;
    native_controller=(jclass)native_original.NewGlobalRef(env,type);native_control_gate=control;
    native_library_gate=(*env)->GetStaticMethodID(env,type,"libraryBoundaryCaller","(Ljava/lang/String;)Z");
    jclass task=(jclass)(*env)->NewGlobalRef(env,tasks),definition=(jclass)(*env)->NewGlobalRef(env,definitions);
    native_owner_gate=(*env)->GetStaticMethodID(env,tasks,"modOwnerAllowed","(Ljava/lang/Object;)Z");native_field_gate=(*env)->GetStaticMethodID(env,tasks,"reflectFieldWrite","(Ljava/lang/reflect/Field;Ljava/lang/Object;Ljava/lang/Object;)Z");
    native_module_query=(*env)->GetStaticMethodID(env,definitions,"module","(Ljava/lang/Class;)Ljava/lang/Module;");native_return_query=(*env)->GetStaticMethodID(env,definitions,"nativeReturn","(Ljava/lang/Module;Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Object;");
    native_image_query=(*env)->GetStaticMethodID(env,definitions,"nativeImage","(Ljava/lang/Class;Ljava/lang/ClassLoader;[B)[B");native_defined_query=(*env)->GetStaticMethodID(env,definitions,"nativeDefined","(Ljava/lang/Class;Ljava/lang/Class;)V");
    native_after_query=(*env)->GetStaticMethodID(env,definitions,"nativeAfter","(Ljava/lang/Class;)V");
    native_created_query=(*env)->GetStaticMethodID(env,tasks,"nativeCreated","(Ljava/lang/Class;Ljava/lang/Object;)V");
    native_array_query=(*env)->GetStaticMethodID(env,tasks,"nativeArrayElementAllowed","(Ljava/lang/Object;ILjava/lang/Object;)Z");native_pin_query=(*env)->GetStaticMethodID(env,tasks,"nativeArrayPointerAllowed","(Ljava/lang/Object;)Z");
    native_array_policy=(*env)->GetStaticMethodID(env,tasks,"nativePrimitiveArrayPolicy","(Ljava/lang/Object;)I");
    native_registration_query=(*env)->GetStaticMethodID(env,tasks,"nativeRegistrationAllowed","(Ljava/lang/Class;[Ljava/lang/Module;)Z");
    native_registration_target=(*env)->GetStaticMethodID(env,tasks,"nativeRegistrationTargetAllowed","(Ljava/lang/Class;)Z");
    native_object_module=(*env)->GetStaticMethodID(env,tasks,"objectModule","(Ljava/lang/Object;)Ljava/lang/Module;");
    native_network_sources=(*env)->GetStaticMethodID(env,tasks,"nativeNetworkSources","()[Ljava/lang/Module;");
    native_process_sources=(*env)->GetStaticMethodID(env,tasks,"nativeProcessSources","()[Ljava/lang/Module;");
    native_mutation_field=(*env)->GetStaticMethodID(env,tasks,"beginNativeFieldMutation","(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Module;)Ljava/lang/Object;");
    native_mutation_array=(*env)->GetStaticMethodID(env,tasks,"beginNativeArrayMutation","(Ljava/lang/Object;II[Ljava/lang/Module;)Ljava/lang/Object;");
    native_mutation_end=(*env)->GetStaticMethodID(env,tasks,"finishFieldMutation","(Ljava/lang/Object;Z)V");
    if(!task||!definition||!native_library_gate||!native_owner_gate||!native_field_gate||!native_module_query||!native_return_query||!native_image_query||!native_defined_query||!native_after_query||!native_created_query||!native_array_query||!native_array_policy||!native_pin_query||!native_registration_query||!native_registration_target||!native_object_module||!native_network_sources||!native_process_sources||!native_mutation_field||!native_mutation_array||!native_mutation_end){(*native_ti)->Deallocate(native_ti,(unsigned char*)table);return JNI_FALSE;}native_tasks=task;native_definitions=definition;
    if(!code_prepare(env)||!native_unsafe_prepare(env)||!native_file_prepare(env)||!native_thread_prepare(env)||!native_buffer_prepare(env)){(*native_ti)->Deallocate(native_ti,(unsigned char*)table);return JNI_FALSE;}
    if(!native_bind_controller(env,type)){(*native_ti)->Deallocate(native_ti,(unsigned char*)table);return JNI_FALSE;}
#define INSTALL_NATIVE_FIELD(NAME) table->Set##NAME##Field=native_set_##NAME;table->SetStatic##NAME##Field=native_static_##NAME;
    INSTALL_NATIVE_FIELD(Object) INSTALL_NATIVE_FIELD(Boolean) INSTALL_NATIVE_FIELD(Byte) INSTALL_NATIVE_FIELD(Char) INSTALL_NATIVE_FIELD(Short) INSTALL_NATIVE_FIELD(Int) INSTALL_NATIVE_FIELD(Long) INSTALL_NATIVE_FIELD(Float) INSTALL_NATIVE_FIELD(Double)
#define INSTALL_NATIVE_CALL(NAME) table->Call##NAME##Method=native_call_##NAME;table->Call##NAME##MethodV=native_call_##NAME##V;table->Call##NAME##MethodA=native_call_##NAME##A;table->CallStatic##NAME##Method=native_scall_##NAME;table->CallStatic##NAME##MethodV=native_scall_##NAME##V;table->CallStatic##NAME##MethodA=native_scall_##NAME##A;table->CallNonvirtual##NAME##Method=native_ncall_##NAME;table->CallNonvirtual##NAME##MethodV=native_ncall_##NAME##V;table->CallNonvirtual##NAME##MethodA=native_ncall_##NAME##A;
    INSTALL_NATIVE_CALL(Object) INSTALL_NATIVE_CALL(Boolean) INSTALL_NATIVE_CALL(Byte) INSTALL_NATIVE_CALL(Char) INSTALL_NATIVE_CALL(Short) INSTALL_NATIVE_CALL(Int) INSTALL_NATIVE_CALL(Long) INSTALL_NATIVE_CALL(Float) INSTALL_NATIVE_CALL(Double)
    table->CallVoidMethod=native_Void;table->CallVoidMethodV=native_VoidV;table->CallVoidMethodA=native_VoidA;
    table->CallStaticVoidMethod=native_StaticVoid;table->CallStaticVoidMethodV=native_StaticVoidV;table->CallStaticVoidMethodA=native_StaticVoidA;
    table->CallNonvirtualVoidMethod=native_nonvirtualVoid;table->CallNonvirtualVoidMethodV=native_nonvirtualVoidV;table->CallNonvirtualVoidMethodA=native_nonvirtualVoidA;
    table->NewObject=native_new;table->NewObjectV=native_newV;table->NewObjectA=native_newA;table->AllocObject=native_allocate;
    table->SetObjectArrayElement=native_set_array_Object;
#define INSTALL_NATIVE_ARRAY(NAME) table->Get##NAME##ArrayElements=native_get_array_##NAME;table->Release##NAME##ArrayElements=native_release_array_##NAME;table->Set##NAME##ArrayRegion=native_set_array_##NAME;
    INSTALL_NATIVE_ARRAY(Boolean) INSTALL_NATIVE_ARRAY(Byte) INSTALL_NATIVE_ARRAY(Char) INSTALL_NATIVE_ARRAY(Short) INSTALL_NATIVE_ARRAY(Int) INSTALL_NATIVE_ARRAY(Long) INSTALL_NATIVE_ARRAY(Float) INSTALL_NATIVE_ARRAY(Double)
    table->GetPrimitiveArrayCritical=native_get_critical;table->ReleasePrimitiveArrayCritical=native_release_critical;
    table->GetStringCritical=native_get_string_critical;table->ReleaseStringCritical=native_release_string_critical;
    table->GetDirectBufferAddress=native_buffer_address;
    table->ExceptionDescribe=native_describe;
    table->RegisterNatives=native_register;table->UnregisterNatives=native_unregister;
    table->FindClass=native_find;table->GetMethodID=native_lookup_GetMethodID;table->GetStaticMethodID=native_lookup_GetStaticMethodID;table->GetFieldID=native_lookup_GetFieldID;table->GetStaticFieldID=native_lookup_GetStaticFieldID;
    table->DefineClass=native_define;jvmtiError result=(*native_ti)->SetJNIFunctionTable(native_ti,table);(*native_ti)->Deallocate(native_ti,(unsigned char*)table);InterlockedExchange(&native_jni_ready,result==JVMTI_ERROR_NONE);
    if(result==JVMTI_ERROR_NONE)InterlockedExchange(&native_library_present,native_library_install(env));
    if(result==JVMTI_ERROR_NONE&&!native_unsafe_install(env))return JNI_FALSE;
    return result==JVMTI_ERROR_NONE?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_module0(JNIEnv *env,jclass type,jobject module,jboolean stopped){(void)type;if(!native_controller||!native_original.CallStaticBooleanMethod(env,native_controller,native_control_gate)){native_refuse(env,"NATIVE_CONTROL_AGENT_REQUIRED");return;}AcquireSRWLockExclusive(&native_records);Owner *owner=native_owner(env,module);if(owner){InterlockedExchange(&owner->producer,1);if(stopped)InterlockedExchange(&owner->stopped,1);}if(!owner)InterlockedIncrement(&native_failures);ReleaseSRWLockExclusive(&native_records);}
#include "sources.c"
#include "code.c"
#include "heap.c"
#include "unsafe.c"
#include "methods.c"
#include "file.c"
#include "thread.c"
#include "buffer.c"
#include "memory.c"
JNIEXPORT jlongArray JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_status0(JNIEnv *env,jclass type,jobject module){
    (void)type;jlong values[7]={native_events,native_jni_ready,0,0,native_failures,0,0};
    AcquireSRWLockShared(&native_records);
    for(Binding *binding=native_bindings;binding;binding=binding->next)if(native_owner_matches(env,binding->owners,module)){
        values[2]+=binding->entry&&InterlockedCompareExchange(&binding->current,0,0)!=0;values[3]+=InterlockedCompareExchange(&binding->active,0,0);
    }
    for(NativeLibrary *library=native_libraries;library;library=library->next)if(InterlockedCompareExchange(&library->alive,0,0)&&native_owner_matches(env,library->owners,module))values[6]++;
    for(NativeArrayCopy *lease=native_array_copies;lease;lease=lease->next)if(!module
            ||module&&native_owner_matches(env,lease->owners,module)
            ||lease->targetModule&&native_original.IsSameObject(env,lease->targetModule,module))values[5]++;
    for(NativeStringCopy *lease=native_string_copies;lease;lease=lease->next)if(!module
            ||module&&native_owner_matches(env,lease->owners,module))values[5]++;
    ReleaseSRWLockShared(&native_records);
    jlongArray result=native_original.NewLongArray(env,7);if(result)native_original.SetLongArrayRegion(env,result,0,7,values);return result;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_bindingControlled0(JNIEnv *env,jclass type,jobject method){
    (void)type;if(!native_controller||!native_original.CallStaticBooleanMethod(env,native_controller,native_control_gate)){native_refuse(env,"NATIVE_CONTROL_AGENT_REQUIRED");return JNI_FALSE;}
    jmethodID actual=native_original.FromReflectedMethod(env,method);jboolean controlled=JNI_FALSE;
    if(actual==host_process_method&&InterlockedCompareExchange(&host_ready,0,0)&&InterlockedCompareExchange(&host_process_current,0,0))return JNI_TRUE;
    AcquireSRWLockShared(&native_records);for(Binding *binding=native_bindings;binding;binding=binding->next)if(binding->method==actual&&binding->entry&&InterlockedCompareExchange(&binding->current,0,0)){controlled=JNI_TRUE;break;}ReleaseSRWLockShared(&native_records);return controlled;
}
JNIEXPORT jclass JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_activeClass0(JNIEnv *env,jclass type){(void)type;jweak origin=native_origin(native_thread());return origin?(jclass)native_original.NewLocalRef(env,origin):NULL;}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_definedClass0(JNIEnv *env,jclass type,jclass declaring,jclass actual){(void)type;NativeThread *state=native_thread();jweak origin=native_origin(state);return origin&&state->defined&&native_original.IsSameObject(env,state->defined,actual)&&native_original.IsSameObject(env,origin,declaring)?JNI_TRUE:JNI_FALSE;}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_definitionBegin0(JNIEnv *env,jclass type,jclass declaring,jobject loader,jbyteArray bytes){(void)type;NativeThread *state=native_thread();NativeDefinition *scope=state?state->definition:NULL;if(!scope||scope->phase!=1||!native_original.IsSameObject(env,scope->declaring,declaring)||!native_original.IsSameObject(env,scope->loader,loader)||!native_original.IsSameObject(env,scope->bytes,bytes))return JNI_FALSE;scope->phase=2;return JNI_TRUE;}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_definitionEnd0(JNIEnv *env,jclass type,jclass declaring){(void)type;NativeThread *state=native_thread();NativeDefinition *scope=state?state->definition:NULL;if(!scope||scope->phase!=3||!native_original.IsSameObject(env,scope->declaring,declaring))return JNI_FALSE;scope->phase=4;return JNI_TRUE;}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_createdObject0(JNIEnv *env,jclass type,jclass declaring,jobject actual){(void)type;NativeThread *state=native_thread();jweak origin=native_origin(state);return origin&&state->created&&native_original.IsSameObject(env,state->created,actual)&&native_original.IsSameObject(env,origin,declaring)?JNI_TRUE:JNI_FALSE;}
