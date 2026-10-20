#include <jvmti.h>
#include <windows.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <stdio.h>
#include <errno.h>
#include "private_memory.c"
#define malloc native_private_malloc
#define calloc native_private_calloc
#define realloc native_private_realloc
#define free native_private_free
#define _strdup native_private_strdup
#define VirtualAlloc native_private_virtual_alloc

typedef struct Owner { jweak module; volatile LONG stopped,producer,kernelProtected,coreState,hostGap; struct Owner *next; } Owner;
typedef struct OwnerLink {Owner *owner;struct OwnerLink *next;} OwnerLink;
typedef struct HostLibraryLoad HostLibraryLoad;
typedef struct NativeToolingScope {OwnerLink *owners;jweak origin;int unknown;unsigned depth;struct NativeToolingScope *previous;} NativeToolingScope;
typedef struct NativeToolingImage NativeToolingImage;
typedef struct NativeUnloader {jweak identity;struct NativeUnloader *next;} NativeUnloader;
typedef struct NativeLibrary {jweak identity,origin,name;wchar_t *path;jsize nameLength;Owner *carrier,*consumer;OwnerLink *owners;NativeUnloader *unloaders;HMODULE handle;struct HostImage *hostImage;jboolean builtin,jni;volatile LONG alive,complete,closing;struct NativeLibrary *next;} NativeLibrary;
typedef struct NativeLibraryScope {NativeLibrary *library;int unloading,phase,success,osLoadStarted;HMODULE osHandle;struct NativeLibraryScope *previous;} NativeLibraryScope;
typedef struct NativeRegistration {jmethodID *methods;jint count;OwnerLink *owners;jweak origin;NativeLibrary *loading;struct NativeRegistration *previous;int unknown;const JNINativeMethod *unsafeTable;HMODULE unsafeVM;} NativeRegistration;
/* One direct controller invocation of the bootstrap VM's own registrar. This
 * call-local scope cannot authorize a later or nested external registration. */
typedef struct NativeUnsafeInstall {
    HMODULE image;jmethodID installer;jint frames;int consumed;
    JNINativeMethod *methods;jmethodID *actual;jint count;OwnerLink *owners;void *caller;
} NativeUnsafeInstall;
typedef struct Binding {jmethodID method;jweak type,origin;Owner *owner,*declared;OwnerLink *owners;NativeLibrary *library,*loading;struct HostImage *hostImage;HostLibraryLoad *hostLoad;int hostUnknown;void *original,*entry;char *signature,*fileMethod;char result;unsigned unsafeOperation,fileOperation,fileArgument,threadOperation,arraycopyOperation;const char *unsafeKind;volatile LONG active,current,valid;RUNTIME_FUNCTION unwind;struct Binding *previous,*next;} Binding;
typedef struct NativeDefinition {jclass declaring;jobject loader;jbyteArray bytes;unsigned phase;struct NativeDefinition *previous;} NativeDefinition;
typedef struct CodeExit {
    jmethodID method;jint bottom;
    struct CodeRetirementOwner *retirementOwner;struct CodeExit *previous;
} CodeExit;
typedef struct NativeUnsafeFrame {jobject token;uint64_t expected;} NativeUnsafeFrame;
enum { UNSAFE_PUT=1,UNSAFE_CAS,UNSAFE_EXCHANGE,UNSAFE_SET,UNSAFE_COPY,UNSAFE_SWAP,UNSAFE_ALLOCATE,UNSAFE_REALLOCATE,UNSAFE_FREE,UNSAFE_READ };
typedef struct NativeFileFrame {jobject receiver,token;jclass declaring;jstring method,path;jobjectArray sources,references;jlongArray arguments;jint operation;jlong address,length;int finishing;} NativeFileFrame;
typedef struct NativeBindingSeed {jmethodID method;jclass declaring;uintptr_t version[3];int captured;struct NativeBindingSeed *previous;} NativeBindingSeed;
typedef struct NativeThreadFrame {jobject receiver,token;jobjectArray sources;jint operation;int finishing,completed,skipped,tokenGlobal;} NativeThreadFrame;
typedef struct NativeUnsafeMutation {jobject receiver,source;jstring kind;jlong offset,length,sourceOffset;jboolean bulk,reading,copy;struct NativeUnsafeMutation *previous;} NativeUnsafeMutation;
typedef struct NativeThread {
    Binding *stack[128],*entries[128];NativeUnsafeFrame unsafeFrames[128];
    NativeFileFrame fileFrames[128],*filePermit;NativeThreadFrame threadFrames[128],*threadPermit;
    NativeBindingSeed *bindingSeed;NativeUnsafeMutation *unsafeMutation;struct NativeMemoryWrite *memoryWrite;
    NativeUnsafeInstall *unsafeInstall;
    struct NativeArrayCopyContext *arraycopy;
    unsigned depth,control,sourceCapture,unsafeInstalling,bufferAllocation,heapCallback,toolingRestricted,arraycopyQuery;
    int poppedObserved;
    jclass defined,prepared,changedClass;jobject created,mutation,popped,changedHolder,bufferAddress,unobservedLibrary;
    jobjectArray bufferSources;CodeExit *execution;NativeDefinition *definition;NativeLibraryScope *library;
    NativeRegistration *registration;NativeToolingScope *tooling;struct NativeToolingEnv *toolingCallbackEnvironment;
    NativeToolingImage *toolingImage;struct HostCreation *processScope;struct HostProcess *processCreated;
    struct NativeToolingInvocation *toolingInvocation;struct NativeToolingDefinitionCall *definitionCall;
    struct NativeToolingSlot *heapWorkerAnchor;
    /* VM detach ends Java attachments, not a C callback or an OS worker. */
    struct HostThreadWorker *hostWorker;
    HostLibraryLoad *hostLoad;struct HostImage *hostUnloadImage;
    struct HostModuleRelease *hostRelease;
    unsigned callbackHolds,vmDetaching,vmThreadEnding;
    int storageOwned,vmAttached,javaEnded,javaCleaning;
    NativePrivateRange jniRange;
} NativeThread;
/* Actual dynamic TLS/FLS receipts are native state contributions, independent
 * of whether this OS thread currently has a Java attachment. */
static int host_tls_capture(NativeThread*,OwnerLink**,int*);
/* Complete actual sources qualify native state only through a producer or
 * this caller image's observed relationship to the exactly bound Core. The
 * implementation follows the Host directories; Host consumers may call this
 * declaration before host_state_sources.c is included in this translation unit. */
static int native_state_caller_eligible(NativeThread*,void*,OwnerLink*,int);
static jvmtiEnv *native_ti;
static JavaVM *native_vm;
static struct JNIInvokeInterface_ native_vm_original;
static SRWLOCK native_records=SRWLOCK_INIT;
static Owner *native_owners;
static Binding *native_bindings;
static NativeLibrary *native_libraries;
static DWORD native_tls=TLS_OUT_OF_INDEXES;
static volatile LONG native_events,native_jni_ready,native_boundary_ready,native_failures;
static volatile LONG native_initialization_attempted,native_handoff_attempted;
static volatile LONG native_describe_recorded;
static jclass native_controller;
static jmethodID native_control_gate;
static jmethodID native_library_gate;
static jmethodID native_load_gate;
static jclass native_library_class,native_impl_class,native_unloader_class;
static jfieldID native_library_context,native_impl_handle,native_impl_version,native_impl_name,native_impl_builtin,native_impl_jni;
static HMODULE native_java_image;
static volatile LONG native_library_present,native_library_ready;
static jclass native_controls,native_definitions;
static jmethodID native_writer_select,native_writer_raw_select;
static jmethodID native_owner_gate,native_field_gate,native_module_query,native_return_query;
static jmethodID native_image_query,native_defined_query,native_after_query,native_created_query;
static jmethodID native_array_query,native_array_policy,native_pin_query,native_registration_query,native_registration_target,native_object_module;
static jmethodID native_mutation_field,native_mutation_array,native_mutation_end;
static jmethodID native_network_sources;
static jmethodID native_process_sources;
static jclass native_unsafe_class,native_unsafe_backing;
static jobject native_unsafe_denied;
static jmethodID native_unsafe_begin_query,native_unsafe_read_begin_query,native_unsafe_copy_begin_query,native_unsafe_read_query,native_unsafe_register,native_unsafe_metadata_query;
static jmethodID native_network_retirement_write_query,native_network_retirement_owner_query,native_network_retirement_active_query;
static jmethodID native_memory_gate;
static struct JNINativeInterface_ native_original;
static HMODULE native_vm_getenv_image(JavaVM *vm);
static void native_tooling_retire(JNIEnv *env,jobject module);
static void native_tooling_definitions_thread_end(NativeThread *state);
static int native_tooling_definitions_pending_any(void);
static void native_tooling_definitions_reap(JNIEnv *env);
static int native_library_install(JNIEnv *env);
static int native_bootstrap_class(JNIEnv *env,jclass type,const char *expected);
static int native_bind_controller(JNIEnv *env,jclass controller);
static int code_initialize(void);
static int code_prepare(JNIEnv*);
static int code_current_sources(JNIEnv *env,OwnerLink **owners);
static int code_current_sources_query(JNIEnv *env,OwnerLink **owners,int *unknown);
static int code_frame_sources(JNIEnv *env,jmethodID method,jlocation location,OwnerLink **owners,int *known);
static int code_current_stopped(JNIEnv *env);
static void JNICALL code_class_prepared(jvmtiEnv *ti,JNIEnv *env,jthread thread,jclass type);
static void JNICALL code_frame_popped(jvmtiEnv *ti,JNIEnv *env,jthread thread,jmethodID method,jboolean exceptional);
static void code_thread_end(JNIEnv *env,NativeThread *state);
static void JNICALL code_retirement_step(jvmtiEnv*,JNIEnv*,jthread,jmethodID,jlocation);
static void code_retirement_thread_end(JNIEnv*,jthread);
static void native_unsafe_classify(JNIEnv*,jclass,Binding*,const char*,const char*);
static int native_unsafe_prepare(JNIEnv*);
static int native_unsafe_install(JNIEnv*);
static NativeUnsafeInstall *native_unsafe_registration(JNIEnv*,NativeThread*,jclass,const JNINativeMethod*,jint,void*);
static int native_unsafe_registered(JNIEnv*,NativeThread*,jclass,const JNINativeMethod*,jmethodID*,jint,void*);
static void native_carrier_metadata_registered(JNIEnv*,jclass,const JNINativeMethod*,jmethodID*,jint,void*);
static int native_unsafe_vm_target(jmethodID,void*);
static int native_unsafe_begin(JNIEnv*,NativeThread*,Binding*,unsigned char*);
static int native_unsafe_network_retirement(JNIEnv*,NativeThread*,Binding*,unsigned char*,jobject);
static void native_unsafe_end(JNIEnv*,NativeThread*,Binding*,unsigned char*);
static uint64_t native_unsafe_result(JNIEnv*,Binding*,unsigned char*);
static int native_file_classify(JNIEnv*,jclass,Binding*,const char*);
static int native_file_prepare(JNIEnv*);
static int native_file_begin(JNIEnv*,NativeThread*,Binding*,jobject,unsigned char*);
static void native_file_end(JNIEnv*,NativeThread*,unsigned char*);
static void native_file_thread_end(JNIEnv*,NativeThread*);
static void native_file_refuse(JNIEnv*,Binding*);
static int native_binding_seed_target(JNIEnv*,NativeThread*,jclass,jmethodID,void**,void**);
static int native_binding_seed_one(JNIEnv*,NativeThread*,jclass,jmethodID,const char*,const char*,unsigned);
static void native_thread_classify(JNIEnv*,jclass,Binding*);
static int native_thread_prepare(JNIEnv*);
static int native_thread_vm_target(jmethodID,void*);
static int native_arraycopy_vm_target(jmethodID,void*);
static void native_arraycopy_prepare_binding(JNIEnv*,jclass,jmethodID,void*);
static void native_arraycopy_classify(JNIEnv*,jclass,Binding*);
static int native_arraycopy_prepare(JNIEnv*);
static int native_arraycopy_install(JNIEnv*);
static int native_arraycopy_internal(JNIEnv*,NativeThread*,Binding*);
static void *native_arraycopy_internal_target(void);
static void JNICALL native_arraycopy_dispatch(JNIEnv*,jclass,jobject,jint,jobject,jint,jint);
static int native_thread_begin(JNIEnv*,NativeThread*,Binding*,jobject);
static void *native_thread_call(NativeThread*,Binding*);
static void native_thread_finish(JNIEnv*,NativeThread*,NativeThreadFrame*,int);
static void native_thread_refuse(JNIEnv*,Binding*);
static int native_buffer_prepare(JNIEnv*);
static void *JNICALL native_buffer_address(JNIEnv*,jobject);
static jobject JNICALL native_buffer_new(JNIEnv*,void*,jlong);
static unsigned native_arguments(const char*);
static int native_memory_call_begin(JNIEnv*,NativeThread*,Binding*,unsigned char*);
static void native_memory_thread_end(JNIEnv*,NativeThread*);
static void code_exception_frames(JNIEnv*,jthrowable,unsigned);
static void native_thread_try_release(NativeThread*);
static void native_thread_callback_leave(NativeThread*);
static int native_thread_worker_os_end(JNIEnv*,NativeThread*);
static int native_thread_module_exit_reaped(NativeThread*);
static int host_thread_worker_scope_idle(const NativeThread*);
static int host_module_capture(NativeThread*,void*,OwnerLink**,struct HostImage**,HostLibraryLoad**,int*);
static int host_module_binding_enter(Binding*);
static void host_module_binding_leave(Binding*);
static int host_capture_caller(NativeThread*,void*,OwnerLink**);
static HMODULE native_control_image(void);
static int native_control_range(uintptr_t,uintptr_t,int);
static int native_platform_range(uintptr_t,uintptr_t,int);
static size_t native_platform_env_span(jvmtiEnv*);
static BOOL host_real_virtual_protect(LPVOID,SIZE_T,DWORD,PDWORD);
static BOOL host_real_virtual_free(LPVOID,SIZE_T,DWORD);
static BOOL host_real_write_process_memory(HANDLE,LPVOID,LPCVOID,SIZE_T,SIZE_T*);
static BOOL host_real_read_process_memory(HANDLE,LPCVOID,LPVOID,SIZE_T,SIZE_T*);
static NativeThread *native_thread(void) {
    if(native_tls==TLS_OUT_OF_INDEXES)return NULL;NativeThread *state=(NativeThread*)TlsGetValue(native_tls);
    if(!state){state=(NativeThread*)calloc(1,sizeof(*state));if(state){state->storageOwned=1;if(!TlsSetValue(native_tls,state)){free(state);return NULL;}}}return state;
}
static void native_thread_java_observed(NativeThread *state){
    if(state&&!state->javaCleaning&&!state->vmDetaching&&!state->vmThreadEnding){state->vmAttached=1;state->javaEnded=0;}
}
/* A valid current JNIEnv slot is still a control target during ThreadEnd.
 * Recording it does not reopen the Java lifecycle or manufacture attachment. */
static int native_thread_jni_observed(NativeThread *state,JNIEnv *env){
    if(!env)return 0;if(!state)state=native_thread();if(!state){InterlockedIncrement(&native_failures);return 0;}
    /* The VM attachment is real even when its exact slot cannot be retained. */
    native_thread_java_observed(state);
    if(state->jniRange.linked&&state->jniRange.address!=(const void*)env)native_private_unregister(&state->jniRange);
    if(!native_private_register(&state->jniRange,env,sizeof(*env))){InterlockedIncrement(&native_failures);return 0;}
    return 1;
}
static void native_thread_callback_enter(NativeThread *state){state->callbackHolds++;}
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
static int native_has_core_state(OwnerLink *head){for(;head;head=head->next)if(InterlockedCompareExchange(&head->owner->coreState,0,0))return 1;return 0;}
static HMODULE native_image(void *address){HMODULE image=NULL;if(address)GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,(LPCSTR)address,&image);return image;}
static HMODULE native_observe_image(void *address){
    /* Registered JDK handles come only from their actual JVM_LoadLibrary
     * return. A code address observes an image, never a path-derived owner. */
    return native_image(address);
}
static int native_capture_image_owners(void *address,OwnerLink **owners){
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    return host_capture_caller(state,address,owners);
}
static int native_caller_stopped(void *address){
    OwnerLink *sources=NULL;int captured=native_capture_image_owners(address,&sources);
    int stopped=!captured||native_owner_stopped(sources)!=NULL;native_owner_links_free(sources);
    return stopped;
}
static int native_stopped(JNIEnv *env){
    NativeThread *state=native_thread();OwnerLink *retained=NULL;
    int captured=host_tls_capture(state,&retained,NULL),stopped=!captured||native_owner_stopped(retained)!=NULL;
    native_owner_links_free(retained);if(stopped)return 1;
    if(!state)return 0;if(env)native_thread_jni_observed(state,env);
    for(unsigned i=0;i<state->depth;i++)if(native_owner_stopped(state->stack[i]->owners))return 1;
    for(NativeLibraryScope *scope=state->library;scope;scope=scope->previous)if(native_owner_stopped(scope->library->owners))return 1;
    for(NativeToolingScope *scope=state->tooling;scope;scope=scope->previous)if(native_owner_stopped(scope->owners))return 1;
    return code_current_stopped(env);
}
static jweak native_origin(NativeThread *state){if(!state)return NULL;if(state->tooling&&state->depth<=state->tooling->depth&&state->tooling->origin)return state->tooling->origin;return state->depth?state->stack[state->depth-1]->origin:state->library?state->library->library->origin:NULL;}
static int native_capture_scope_sources(JNIEnv *env,NativeThread *state,OwnerLink **owners,int *unknown){
    if(!host_tls_capture(state,owners,unknown))return 0;
    if(!state)return 1;if(env)native_thread_jni_observed(state,env);for(unsigned i=0;i<state->depth;i++){if(!native_owner_merge(owners,state->stack[i]->owners))return 0;if(unknown&&state->stack[i]->hostUnknown)*unknown=1;}
    for(NativeRegistration *scope=state->registration;scope;scope=scope->previous){if(!native_owner_merge(owners,scope->owners))return 0;if(unknown&&scope->unknown)*unknown=1;}
    for(NativeLibraryScope *scope=state->library;scope;scope=scope->previous)if(!native_owner_merge(owners,scope->library->owners))return 0;for(NativeToolingScope *scope=state->tooling;scope;scope=scope->previous){if(!native_owner_merge(owners,scope->owners))return 0;if(scope->unknown&&unknown)*unknown=1;}return code_current_sources_query(env,owners,unknown);
}
static int native_capture_scope_owners(JNIEnv *env,NativeThread *state,OwnerLink **owners){return native_capture_scope_sources(env,state,owners,NULL);}
static int native_capture_network_sources(JNIEnv *env,NativeThread *state,OwnerLink **owners,int *unknown){
    if(!native_jni_ready||!state||state->control||!native_network_sources)return 1;
    state->control++;state->sourceCapture++;jobjectArray sources=(jobjectArray)native_original.CallStaticObjectMethod(env,native_controls,native_network_sources);state->sourceCapture--;int valid=sources&&!native_original.ExceptionCheck(env);
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
    native_thread_jni_observed(state,env);
    if(binding->fileOperation)memset(&state->fileFrames[state->depth],0,sizeof(NativeFileFrame));
    if(binding->threadOperation)memset(&state->threadFrames[state->depth],0,sizeof(NativeThreadFrame));
    // Controller JNI work already has its exact native control scope. Its own
    // JDK atomics must not recursively enter the Java memory bookkeeping.
    int internalArrayCopy=binding->arraycopyOperation&&native_arraycopy_internal(env,state,binding);
    if(binding->arraycopyOperation&&native_original.ExceptionCheck(env))return 0;
    if(((binding->unsafeOperation||(binding->fileOperation&&binding->fileOperation!=10)||binding->threadOperation)&&state->control)||internalArrayCopy){if(!host_module_binding_enter(binding))return 0;state->entries[state->depth]=binding;state->stack[state->depth++]=binding;InterlockedIncrement(&binding->active);return (uint64_t)(uintptr_t)(internalArrayCopy?native_arraycopy_internal_target():binding->original);}
    if(binding->declared&&InterlockedCompareExchange(&binding->declared->stopped,0,0))return 0;
    // A stopped caller may finish an already sealed resource's original close.
    // The exact descriptor scope is checked by the file bridge before dispatch.
    int stopped=code_current_stopped(env),networkRetirement=0;
    if(native_original.ExceptionCheck(env))return 0;
    if(stopped&&binding->fileOperation!=4&&binding->fileOperation!=7&&binding->fileOperation!=8&&binding->unsafeOperation!=UNSAFE_FREE){
        networkRetirement=native_unsafe_network_retirement(env,state,binding,frame,NULL);
        if(native_original.ExceptionCheck(env))return 0;
        if(!networkRetirement){
            // A real rejected-write cleanup must not silently lose an unsupported
            // PUT or retry its CAS forever. This observation only refuses it.
            if((binding->unsafeOperation==UNSAFE_CAS||binding->unsafeOperation==UNSAFE_PUT)&&native_network_retirement_active_query){
                state->control++;
                jboolean active=native_original.CallStaticBooleanMethod(env,native_controls,native_network_retirement_active_query);
                state->control--;
                if(active&&!native_original.ExceptionCheck(env))native_refuse(env,"NETWORK_RETIREMENT_WRITE_UNSUPPORTED");
            }
            return 0;
        }
    }
    Binding *effective=binding;
    while(effective&&(!InterlockedCompareExchange(&effective->valid,0,0)||native_owner_stopped(effective->owners)||effective->hostUnknown
            ||effective->library&&(!InterlockedCompareExchange(&effective->library->alive,0,0)||InterlockedCompareExchange(&effective->library->closing,0,0))))effective=effective->previous;
    if(!effective)return 0;
    state->control++;jobject type=native_original.NewLocalRef(env,effective->origin);
    jboolean allowed=type&&native_controls?native_original.CallStaticBooleanMethod(env,native_controls,native_owner_gate,type):JNI_FALSE;
    if(!allowed&&type&&!native_original.ExceptionCheck(env)){
        // Only this actual VM scalar write may substitute the exact retirement decision
        // for the owner's stopped-invocation check. The owner's module still
        // passes the Java owner predicate, and no permission escapes this call.
        if(!networkRetirement)networkRetirement=native_unsafe_network_retirement(env,state,binding,frame,NULL);
        if(networkRetirement&&!native_original.ExceptionCheck(env))
            allowed=native_unsafe_network_retirement(env,state,effective,frame,type)?JNI_TRUE:JNI_FALSE;
    }
    if(type)native_original.DeleteLocalRef(env,type);state->control--;if(native_original.ExceptionCheck(env)||!allowed)return 0;
    if(effective->hostUnknown||!host_module_binding_enter(effective))return 0;
    state->entries[state->depth]=binding;state->stack[state->depth++]=effective;InterlockedIncrement(&effective->active);
    if(binding->unsafeOperation&&!native_unsafe_begin(env,state,binding,frame)){
        state->depth--;InterlockedDecrement(&effective->active);host_module_binding_leave(effective);state->stack[state->depth]=state->entries[state->depth]=NULL;return 0;
    }
    if(binding->fileOperation&&!native_file_begin(env,state,binding,receiver,frame)){
        state->depth--;InterlockedDecrement(&effective->active);host_module_binding_leave(effective);state->stack[state->depth]=state->entries[state->depth]=NULL;return 0;
    }
    if(binding->threadOperation){
        if(!native_thread_begin(env,state,binding,receiver)){
            state->depth--;InterlockedDecrement(&effective->active);host_module_binding_leave(effective);state->stack[state->depth]=state->entries[state->depth]=NULL;return 0;
        }
        return (uint64_t)(uintptr_t)native_thread_call(state,effective);
    }
    return (uint64_t)(uintptr_t)(binding->arraycopyOperation?(void*)native_arraycopy_dispatch:effective->original);
}
static void native_leave(Binding *binding,unsigned char *frame){
    NativeThread *state=native_thread();if(!state||!state->depth||state->entries[state->depth-1]!=binding){InterlockedIncrement(&native_failures);return;}
    if(binding->unsafeOperation||binding->fileOperation||binding->threadOperation){
        unsigned args=native_arguments(binding->signature),save=32+(args>4?(args-4)*8:0);JNIEnv *env;memcpy(&env,frame+save,sizeof(env));
        if(binding->unsafeOperation)native_unsafe_end(env,state,binding,frame);
        if(binding->fileOperation)native_file_end(env,state,frame);
        if(binding->threadOperation)native_thread_finish(env,state,&state->threadFrames[state->depth-1],!native_original.ExceptionCheck(env));
    }
    state->depth--;Binding *active=state->stack[state->depth];InterlockedDecrement(&active->active);
    host_module_binding_leave(active);state->stack[state->depth]=state->entries[state->depth]=NULL;
    native_thread_try_release(state);
}
static uint64_t native_result(JNIEnv *env,Binding *binding,unsigned char *frame) {
    if(native_original.ExceptionCheck(env))return 0;
    if(binding->threadOperation){native_thread_refuse(env,binding);return 0;}
    if(binding->fileOperation){native_file_refuse(env,binding);return 0;}
    if(binding->unsafeOperation)return native_unsafe_result(env,binding,frame);
    if(binding->arraycopyOperation){native_refuse(env,"RONOVA_STOPPED_NATIVE_ARRAYCOPY");return 0;}
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
    DWORD previous;if(!host_real_virtual_protect(page,capacity,PAGE_EXECUTE_READ,&previous)||!RtlAddFunctionTable(&binding->unwind,1,(DWORD64)(uintptr_t)page)){host_real_virtual_free(page,0,MEM_RELEASE);return NULL;}
    FlushInstructionCache(GetCurrentProcess(),page,capacity);return page;
}
#include "host_kernel.c"
#include "host.c"
static jlong JNICALL native_rejected(JNIEnv *env,jobject receiver){(void)receiver;native_refuse(env,"RONOVA_NATIVE_BINDING_CONTROL_FAILED");return 0;}
static void JNICALL native_binding(jvmtiEnv *api,JNIEnv *env,jthread thread,jmethodID method,void *target,void **replacement) {
    (void)thread;jvmtiPhase phase;if(!native_definitions||(*api)->GetPhase(api,&phase)!=JVMTI_ERROR_NONE||phase!=JVMTI_PHASE_LIVE)return;
    NativeThread *state=native_thread();if(!state)return;state->control++;jclass type=NULL;
    if((*api)->GetMethodDeclaringClass(api,method,&type)!=JVMTI_ERROR_NONE){state->control--;return;}
    if(native_binding_seed_target(env,state,type,method,&target,replacement)){native_original.DeleteLocalRef(env,type);state->control--;return;}
    if(host_bind(env,type,method,target,replacement)){native_original.DeleteLocalRef(env,type);state->control--;return;}
    if(native_controller&&native_original.IsSameObject(env,type,native_controller)
            ||native_library_class&&native_original.IsSameObject(env,type,native_library_class)){native_original.DeleteLocalRef(env,type);state->control--;return;}
    jobject module=native_original.CallStaticObjectMethod(env,native_definitions,native_module_query,type);
    if(!module||native_original.ExceptionCheck(env)){native_original.DeleteLocalRef(env,type);state->control--;return;}
    NativeRegistration *registration=NULL;int vmUnsafe=0;
    for(NativeRegistration *scope=state->registration;scope&&!registration;scope=scope->previous)
        for(jint i=0;i<scope->count;i++)if(scope->methods[i]==method){
            registration=scope;
            vmUnsafe=scope->unsafeTable&&scope->unsafeVM&&scope->unsafeTable[i].fnPtr==target
                    &&native_original.IsSameObject(env,type,native_unsafe_class)
                    &&native_image(target)==scope->unsafeVM;break;
        }
    HMODULE image=vmUnsafe?NULL:native_observe_image(target);
    OwnerLink *imageSources=NULL;HostImage *childImage=NULL;HostLibraryLoad *childLoad=NULL;int childUnknown=0;
    int imageReady=vmUnsafe||(host_module_capture(state,target,&imageSources,&childImage,&childLoad,&childUnknown)
            &&native_capture_image_owners(target,&imageSources));
    native_arraycopy_prepare_binding(env,type,method,target);
    AcquireSRWLockExclusive(&native_records);
    Binding *previous=NULL,*aliased=NULL;
    for(Binding *old=native_bindings;old;old=old->next){
        if(!vmUnsafe&&old->entry&&old->entry==target)aliased=old;
        if(old->method==method&&InterlockedCompareExchange(&old->current,0,0)){if(!previous)previous=old;InterlockedExchange(&old->current,0);}
    }
    if(!target){ReleaseSRWLockExclusive(&native_records);native_owner_links_free(imageSources);native_original.DeleteLocalRef(env,module);native_original.DeleteLocalRef(env,type);state->control--;return;}
    Owner *declared=native_owner(env,module);char *name=NULL,*signature=NULL;Binding *binding=(Binding*)calloc(1,sizeof(*binding));
    int ready=imageReady&&binding&&declared&&(*api)->GetMethodName(api,method,&name,&signature,NULL)==JVMTI_ERROR_NONE;
    if(ready){
        binding->method=method;binding->declared=declared;binding->previous=previous;binding->original=aliased?aliased->original:target;
        binding->type=native_original.NewWeakGlobalRef(env,type);binding->signature=_strdup(signature);binding->result=native_return_type(signature);
        native_unsafe_classify(env,type,binding,name,signature);
        int fileReady=native_file_classify(env,type,binding,name);
        native_thread_classify(env,type,binding);
        native_arraycopy_classify(env,type,binding);
        ready=fileReady&&native_owner_add(&binding->owners,declared);
        ready=ready&&native_owner_merge(&binding->owners,imageSources);
        binding->hostImage=aliased?aliased->hostImage:childImage;binding->hostLoad=aliased?aliased->hostLoad:childLoad;
        binding->hostUnknown=childUnknown||(aliased&&aliased->hostUnknown);
        if(aliased)ready=ready&&native_owner_merge(&binding->owners,aliased->owners);
        if(registration){ready=ready&&native_owner_merge(&binding->owners,registration->owners);binding->loading=registration->loading;binding->hostUnknown|=registration->unknown;}
        // Query the OS loader before taking the ledger lock. JNI_OnLoad can
        // itself acquire this ledger while another thread is in the OS loader.
        if(aliased)image=aliased->library?aliased->library->handle:NULL;
        for(unsigned pass=0;pass<3&&!binding->library;pass++)for(NativeLibrary *library=native_libraries;library;library=library->next)
            if(image&&library->handle==image&&InterlockedCompareExchange(&library->alive,0,0)
                    &&(pass!=2||!childImage)
                    &&(pass==0?library->carrier==declared&&library->consumer==declared:pass==1?library->carrier==declared:1)){
                binding->library=library;break;
            }
        if(binding->library)ready=ready&&native_owner_merge(&binding->owners,binding->library->owners);
        // A first call only resolves a pre-existing implementation; its caller
        // has not installed code into this method. Only real RegisterNatives
        // submission carries installer provenance.
        if(!binding->library&&registration)binding->library=registration->loading;
        /* A first direct Core JNI call needs the same real entry/TLS scope as
         * a producer. Tie it to its already observed current library image;
         * an unrelated Core frame or a module name cannot create this edge. */
        if(!binding->hostImage&&!binding->hostUnknown&&native_has_core_state(binding->owners)
                &&binding->library&&binding->library->hostImage
                &&host_library_current(binding->library,image)
                &&binding->library->hostImage->handle==image)
            binding->hostImage=binding->library->hostImage;
        jweak origin=aliased?aliased->origin:binding->library?binding->library->origin:registration?registration->origin:childLoad?childLoad->origin:NULL;
        jclass actual=origin?(jclass)native_original.NewLocalRef(env,origin):(jclass)native_original.NewLocalRef(env,type);
        if(actual){binding->origin=native_original.NewWeakGlobalRef(env,actual);native_original.DeleteLocalRef(env,actual);}
        // Preserve pre-existing outside implementations for an actual later overwrite.
        // Ordinary bindings are recorded without replacing their VM entry point.
        if(ready&&binding->type&&binding->origin&&binding->signature){
            int wrapped=binding->unsafeOperation||binding->fileOperation||binding->threadOperation||binding->arraycopyOperation||binding->hostImage||binding->hostUnknown||native_has_producer(binding->owners);
            if(wrapped)binding->entry=native_entry(binding);
            ready=!wrapped||binding->entry!=NULL;
        }else ready=0;
        if(ready){binding->valid=binding->current=1;binding->owner=binding->owners?binding->owners->owner:NULL;binding->next=native_bindings;native_bindings=binding;if(binding->entry)*replacement=binding->entry;}
    }
    if(!ready){if(binding){if(binding->type)native_original.DeleteWeakGlobalRef(env,binding->type);if(binding->origin)native_original.DeleteWeakGlobalRef(env,binding->origin);native_owner_links_free(binding->owners);free(binding->signature);free(binding->fileMethod);free(binding);}*replacement=(void*)native_rejected;InterlockedIncrement(&native_failures);}
    if(name)(*api)->Deallocate(api,(unsigned char*)name);if(signature)(*api)->Deallocate(api,(unsigned char*)signature);ReleaseSRWLockExclusive(&native_records);native_owner_links_free(imageSources);native_original.DeleteLocalRef(env,module);native_original.DeleteLocalRef(env,type);state->control--;
}
static int native_field_allowed(JNIEnv *env,jobject object,jclass type,jfieldID field,jobject value,int statik) {
    if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_FIELD_WRITE");return 0;}NativeThread *state=native_thread();if(!native_controls||!state)return 1;
    state->control++;jclass declaring=NULL;jvmtiError result=(*native_ti)->GetFieldDeclaringClass(native_ti,type,field,&declaring);
    jobject reflected=result==JVMTI_ERROR_NONE?native_original.ToReflectedField(env,declaring,field,statik?JNI_TRUE:JNI_FALSE):NULL;
    jboolean allowed=reflected?native_original.CallStaticBooleanMethod(env,native_controls,native_field_gate,reflected,statik?NULL:object,value):JNI_FALSE;
    if(reflected)native_original.DeleteLocalRef(env,reflected);if(declaring)native_original.DeleteLocalRef(env,declaring);state->control--;return allowed&&!native_original.ExceptionCheck(env);
}
static jobject native_box(JNIEnv *env,const char *name,const char *signature,jvalue value){NativeThread *state=native_thread();if(!state)return NULL;state->control++;jclass type=native_original.FindClass(env,name);jmethodID method=type?native_original.GetStaticMethodID(env,type,"valueOf",signature):NULL;jobject result=method?native_original.CallStaticObjectMethodA(env,type,method,&value):NULL;if(type)native_original.DeleteLocalRef(env,type);state->control--;return result;}
static jobjectArray native_mutation_sources_from(JNIEnv *env,NativeThread *state,OwnerLink *retained,int retainedUnknown){
    OwnerLink *owners=NULL;int unknown=retainedUnknown!=0;
    if(state)for(unsigned at=0;at<state->depth;at++)if(host_private_binding_unknown(state->stack[at])){unknown=1;break;}
    if(!native_owner_merge(&owners,retained)||!native_capture_scope_sources(env,state,&owners,&unknown)){
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
static jobjectArray native_mutation_sources(JNIEnv *env,NativeThread *state){return native_mutation_sources_from(env,state,NULL,0);}
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
            token=native_original.CallStaticObjectMethod(env,native_controls,native_mutation_field,receiver,declaring,fieldName,fieldDescriptor,contributors);
            state->mutation=previous;
        }
    }
    if(contributors)native_original.DeleteLocalRef(env,contributors);
    if(fieldName)native_original.DeleteLocalRef(env,fieldName);if(fieldDescriptor)native_original.DeleteLocalRef(env,fieldDescriptor);
    native_original.DeleteLocalRef(env,declaring);return token;
}
static jobject native_begin_array_mutation_from(JNIEnv *env,jarray receiver,jsize start,jsize length,OwnerLink *retained,int retainedUnknown){
    if(native_original.ExceptionCheck(env))return NULL;
    NativeThread *state=native_thread();if(!state){native_refuse(env,"NATIVE_MUTATION_SCOPE_UNAVAILABLE");return NULL;}
    jobjectArray contributors=native_mutation_sources_from(env,state,retained,retainedUnknown);if(!contributors||native_original.ExceptionCheck(env)){if(contributors)native_original.DeleteLocalRef(env,contributors);return NULL;}
    jobject previous=state->mutation;state->mutation=receiver;
    jobject token=native_original.CallStaticObjectMethod(env,native_controls,native_mutation_array,receiver,start,length,contributors);
    state->mutation=previous;native_original.DeleteLocalRef(env,contributors);return token;
}
static jobject native_begin_array_mutation(JNIEnv *env,jarray receiver,jsize start,jsize length){return native_begin_array_mutation_from(env,receiver,start,length,NULL,0);}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_memoryMutationBoundary0(JNIEnv *env,jclass type,jobject receiver){
    (void)type;NativeThread *state=native_thread();
    return state&&state->mutation&&native_original.IsSameObject(env,state->mutation,receiver)?JNI_TRUE:JNI_FALSE;
}
static void native_end_mutation(JNIEnv *env,jobject token,int applied){
    if(!token)return;jthrowable failure=native_original.ExceptionOccurred(env);
    if(failure)native_original.ExceptionClear(env);
    native_original.CallStaticVoidMethod(env,native_controls,native_mutation_end,token,applied&&!failure?JNI_TRUE:JNI_FALSE);
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
    jboolean allowed=native_original.CallStaticBooleanMethod(env,native_controls,native_array_query,array,index,value);
    if(!allowed&&!native_original.ExceptionCheck(env))native_refuse(env,"RONOVA_PROTECTED_NATIVE_ARRAY_WRITE");return allowed&&!native_original.ExceptionCheck(env);
}
static int native_primitive_array_policy(JNIEnv *env,jarray array){
    if(native_original.ExceptionCheck(env))return 0;
    if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_ARRAY_WRITE");return 0;}
    jint policy=native_original.CallStaticIntMethod(env,native_controls,native_array_policy,array);
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
    jboolean allowed=native_original.CallStaticBooleanMethod(env,native_controls,native_pin_query,array);
    if(!allowed&&!native_original.ExceptionCheck(env))native_refuse(env,"RONOVA_PROTECTED_NATIVE_ARRAY_POINTER");return allowed&&!native_original.ExceptionCheck(env);
}
static void JNICALL native_set_array_Object(JNIEnv *env,jobjectArray array,jsize index,jobject value){jobject gate=native_begin_array_mutation(env,array,index,1);int applied=gate&&native_array_allowed(env,array,index,value);if(applied)native_original.SetObjectArrayElement(env,array,index,value);native_end_mutation(env,gate,applied);}
static void *native_array_copy(JNIEnv *env,jarray array,jboolean *copy,char expected,int critical,void *caller);
static void native_array_release(JNIEnv *env,jarray array,void *contents,jint mode,char expected,int critical,void *caller);
#define NATIVE_ARRAY(NAME,TYPE,SLOT,BOX,SIG) \
static TYPE *JNICALL native_get_array_##NAME(JNIEnv *env,TYPE##Array array,jboolean *copy){return (TYPE*)native_array_copy(env,array,copy,SIG[1],0,__builtin_return_address(0));} \
static void JNICALL native_release_array_##NAME(JNIEnv *env,TYPE##Array array,TYPE *contents,jint mode){native_array_release(env,array,contents,mode,SIG[1],0,__builtin_return_address(0));} \
static void native_set_array_from_##NAME(JNIEnv *env,TYPE##Array array,jsize start,jsize length,const TYPE *contents,OwnerLink *retained,int retainedUnknown){ \
    jsize size=native_original.GetArrayLength(env,array);if(native_original.ExceptionCheck(env))return; \
    if(start<0||length<0||start>size-length){native_original.Set##NAME##ArrayRegion(env,array,start,length,contents);return;} \
    if(length==0){native_original.Set##NAME##ArrayRegion(env,array,start,length,contents);return;} \
    size_t bytes=(size_t)length*sizeof(TYPE);TYPE *snapshot=(TYPE*)malloc(bytes);if(!snapshot){native_refuse(env,"RONOVA_NATIVE_ARRAY_SNAPSHOT_ALLOCATION_FAILED");return;}memcpy(snapshot,contents,bytes); \
    jobject gate=native_begin_array_mutation_from(env,array,start,length,retained,retainedUnknown);if(!gate){native_array_failure(env,"BEGIN",SIG[1],start,length);free(snapshot);return;} \
    int policy=native_primitive_array_policy(env,array);if(!policy){native_end_mutation(env,gate,0);native_array_failure(env,"POLICY",SIG[1],start,length);free(snapshot);return;} \
    if(policy==1)for(jsize i=0;i<length;i++){jvalue value;memset(&value,0,sizeof(value));value.SLOT=snapshot[i];jobject boxed=native_box(env,BOX,SIG,value); \
        int allowed=boxed&&native_array_allowed(env,array,start+i,boxed);if(boxed)native_original.DeleteLocalRef(env,boxed);if(!allowed){native_end_mutation(env,gate,0);native_array_failure(env,"VALUE",SIG[1],start+i,1);free(snapshot);return;}} \
    native_original.Set##NAME##ArrayRegion(env,array,start,length,snapshot);native_end_mutation(env,gate,1);native_array_failure(env,"COMMIT",SIG[1],start,length);free(snapshot);} \
static void JNICALL native_set_array_##NAME(JNIEnv *env,TYPE##Array array,jsize start,jsize length,const TYPE *contents){native_set_array_from_##NAME(env,array,start,length,contents,NULL,0);}
NATIVE_ARRAY(Boolean,jboolean,z,"java/lang/Boolean","(Z)Ljava/lang/Boolean;")
NATIVE_ARRAY(Byte,jbyte,b,"java/lang/Byte","(B)Ljava/lang/Byte;")
NATIVE_ARRAY(Char,jchar,c,"java/lang/Character","(C)Ljava/lang/Character;")
NATIVE_ARRAY(Short,jshort,s,"java/lang/Short","(S)Ljava/lang/Short;")
NATIVE_ARRAY(Int,jint,i,"java/lang/Integer","(I)Ljava/lang/Integer;")
NATIVE_ARRAY(Long,jlong,j,"java/lang/Long","(J)Ljava/lang/Long;")
NATIVE_ARRAY(Float,jfloat,f,"java/lang/Float","(F)Ljava/lang/Float;")
NATIVE_ARRAY(Double,jdouble,d,"java/lang/Double","(D)Ljava/lang/Double;")
static int native_jni_output_context(JNIEnv*);
static int native_jni_output_commit(JNIEnv*,void*,const void*,SIZE_T);
#include "arrays.c"
#include "arraycopy.c"
static void *JNICALL native_get_critical(JNIEnv *env,jarray array,jboolean *copy){return native_array_copy(env,array,copy,0,1,__builtin_return_address(0));}
static void JNICALL native_release_critical(JNIEnv *env,jarray array,void *contents,jint mode){native_array_release(env,array,contents,mode,0,1,__builtin_return_address(0));}
/* Failure-only installation diagnostics. JVMTI metadata reads do not clear or
 * replace the exception that the registrar will observe. */
static void native_registration_failure(JNIEnv *env,const char *stage,jclass type,void *implementation,void *caller,int unknown,jobjectArray sources){
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    if(!state||!state->unsafeInstalling)return;
    int pending=native_original.ExceptionCheck(env);char *signature=NULL,*frameClass=NULL,*name=NULL,*descriptor=NULL;
    jclass declaring=NULL;jvmtiFrameInfo frames[12];jint count=0,sourceCount=-1;int same=-1;
    (*native_ti)->GetClassSignature(native_ti,type,&signature,NULL);
    if(sources&&!pending)sourceCount=native_original.GetArrayLength(env,sources);
    if((*native_ti)->GetStackTrace(native_ti,NULL,0,12,frames,&count)==JVMTI_ERROR_NONE){
        for(jint i=0;i<count;i++){
            jint modifiers=0;
            if((*native_ti)->GetMethodModifiers(native_ti,frames[i].method,&modifiers)!=JVMTI_ERROR_NONE||!(modifiers&0x0100))continue;
            (*native_ti)->GetMethodName(native_ti,frames[i].method,&name,&descriptor,NULL);
            if((*native_ti)->GetMethodDeclaringClass(native_ti,frames[i].method,&declaring)==JVMTI_ERROR_NONE&&declaring){
                (*native_ti)->GetClassSignature(native_ti,declaring,&frameClass,NULL);
                if(!pending)same=native_original.IsSameObject(env,type,declaring);
            }break;
        }
    }
    fprintf(stderr,"RONOVA_UNSAFE_REGISTRATION_REFUSED:%s:target=%s:firstNative=%s#%s%s:same=%d:unknown=%d:sources=%d:pending=%d:caller=%p:implementation=%p\n",
            stage,signature?signature:"?",frameClass?frameClass:"?",name?name:"?",descriptor?descriptor:"?",same,unknown,(int)sourceCount,pending,caller,implementation);fflush(stderr);
    if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);
    if(frameClass)(*native_ti)->Deallocate(native_ti,(unsigned char*)frameClass);
    if(name)(*native_ti)->Deallocate(native_ti,(unsigned char*)name);
    if(descriptor)(*native_ti)->Deallocate(native_ti,(unsigned char*)descriptor);
    if(declaring)native_original.DeleteLocalRef(env,declaring);
}
static jobjectArray native_registration_sources(JNIEnv *env,void *implementation,void *caller,int *unobserved){
    NativeThread *state=native_thread();OwnerLink *sources=NULL;int unknown=0;
    int ready=native_capture_scope_sources(env,state,&sources,&unknown)
            &&host_module_capture(state,implementation,&sources,NULL,NULL,&unknown)
            &&native_capture_image_owners(implementation,&sources)
            &&host_module_capture(state,caller,&sources,NULL,NULL,&unknown)
            &&native_capture_image_owners(caller,&sources);
    AcquireSRWLockShared(&native_records);
    for(Binding *binding=native_bindings;binding;binding=binding->next)if(binding->entry&&binding->entry==implementation)ready=ready&&native_owner_merge(&sources,binding->owners);
    ReleaseSRWLockShared(&native_records);
    if(!ready){native_owner_links_free(sources);native_refuse(env,"NATIVE_REGISTRATION_SOURCE_CAPACITY");return NULL;}
    if(unobserved)*unobserved=unknown;
    jobjectArray result=native_tooling_sources(env,sources,unknown);native_owner_links_free(sources);return result;
}
static int native_registration_allowed(JNIEnv *env,jclass type,void *implementation,void *caller){
    if(native_original.ExceptionCheck(env))return 0;
    if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_REGISTRATION");return 0;}
    int unknown=0;jobjectArray sources=native_registration_sources(env,implementation,caller,&unknown);
    jboolean allowed=sources&&!native_original.ExceptionCheck(env)?native_original.CallStaticBooleanMethod(env,native_controls,native_registration_query,type,sources):JNI_FALSE;
    if(!allowed)native_registration_failure(env,"entry-query",type,implementation,caller,unknown,sources);
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
            ||!native_original.CallStaticBooleanMethod(env,native_controls,native_registration_target,type)){
        native_registration_failure(env,"target-query",type,NULL,caller,-1,NULL);
        native_refuse(env,"RONOVA_NATIVE_REGISTRATION_REFUSED");return JNI_ERR;
    }
    if(count<0||(count>0&&!methods)){native_refuse(env,"RONOVA_NATIVE_METHOD_TABLE_REQUIRED");return JNI_ERR;}
    if(count==0)return native_original.RegisterNatives(env,type,NULL,0);
    if((size_t)count>SIZE_MAX/sizeof(JNINativeMethod)){native_refuse(env,"RONOVA_NATIVE_METHOD_TABLE_CAPACITY");return JNI_ERR;}
    JNINativeMethod *snapshot=(JNINativeMethod*)calloc((size_t)count,sizeof(*snapshot));
    if(!snapshot){native_refuse(env,"RONOVA_NATIVE_METHOD_TABLE_CAPACITY");return JNI_ERR;}
    jmethodID *actual=(jmethodID*)calloc((size_t)count,sizeof(*actual));OwnerLink *owners=NULL;NativeThread *state=native_thread();
    NativeRegistration registration={actual,count,NULL,NULL,state&&state->library?state->library->library:NULL,state?state->registration:NULL};
    NativeUnsafeInstall *vmInstall=actual&&state?native_unsafe_registration(env,state,type,methods,count,caller):NULL;
    HostLibraryLoad *callerLoad=NULL;jobjectArray vmSources=NULL;int captured=actual&&state;
    if(vmInstall){
        /* This provenance comes from the exact private VM registrar and its
         * original table, independently of a public carrier's unknown callers. */
        jobject module=native_original.GetModule(env,native_unsafe_class);
        AcquireSRWLockExclusive(&native_records);Owner *owner=module?native_owner(env,module):NULL;
        captured=owner&&native_owner_add(&owners,owner)&&!native_owner_stopped(owners);ReleaseSRWLockExclusive(&native_records);
        if(module)native_original.DeleteLocalRef(env,module);
        registration.loading=NULL;registration.unsafeTable=snapshot;registration.unsafeVM=vmInstall->image;
    }else captured=captured&&native_capture_scope_sources(env,state,&owners,&registration.unknown)
            &&host_module_capture(state,caller,&owners,NULL,&callerLoad,&registration.unknown)
            &&native_capture_image_owners(caller,&owners);
    if(!captured){free(actual);native_owner_links_free(owners);native_registration_free(snapshot,count);native_refuse(env,"NATIVE_REGISTRATION_SOURCE_CAPACITY");return JNI_ERR;}
    jweak origin=vmInstall?native_unsafe_class:native_origin(state);if(!origin&&callerLoad)origin=callerLoad->origin;
    registration.owners=owners;registration.origin=origin;
    jint result=JNI_ERR;
    for(jint i=0;i<count;i++){
        // Copy both the table and its strings before authorizing any entry. The
        // original JNI function receives this same immutable call-local table.
        JNINativeMethod entry=methods[i];
        if(!entry.name||!entry.signature){native_refuse(env,"RONOVA_NATIVE_METHOD_TABLE_REQUIRED");goto finished;}
        if(vmInstall&&(native_image(entry.name)!=vmInstall->image||native_image(entry.signature)!=vmInstall->image)){
            native_refuse(env,"ACTUAL_UNSAFE_VM_TABLE_REQUIRED");goto finished;}
        snapshot[i].name=_strdup(entry.name);snapshot[i].signature=_strdup(entry.signature);snapshot[i].fnPtr=entry.fnPtr;
        if(!snapshot[i].name||!snapshot[i].signature){native_refuse(env,"RONOVA_NATIVE_METHOD_TABLE_CAPACITY");goto finished;}
        if(vmInstall&&native_image(snapshot[i].fnPtr)!=vmInstall->image){native_refuse(env,"ACTUAL_UNSAFE_VM_TABLE_REQUIRED");goto finished;}
        if(!vmInstall&&(!host_module_capture(state,snapshot[i].fnPtr,&owners,NULL,NULL,&registration.unknown)
                ||!native_capture_image_owners(snapshot[i].fnPtr,&owners))){
            native_refuse(env,"NATIVE_REGISTRATION_SOURCE_CAPACITY");goto finished;}
        registration.owners=owners;
        if(snapshot[i].fnPtr&&native_image(snapshot[i].fnPtr)==native_image((void*)native_register)){
            native_refuse(env,"RONOVA_NATIVE_CONTROLLER_ALIAS_REFUSED");goto finished;
        }
    }
    if(vmInstall){vmSources=native_tooling_sources(env,owners,0);if(!vmSources||native_original.ExceptionCheck(env))goto finished;}
    for(jint i=0;i<count;i++){
        jclass declaring=native_registration_declaring(env,type,&snapshot[i],&actual[i]);
        if(!declaring)goto finished;
        int allowed;
        if(vmInstall){
            allowed=native_original.IsSameObject(env,declaring,native_unsafe_class)&&!native_stopped(env)
                    &&native_original.CallStaticBooleanMethod(env,native_controls,native_registration_query,declaring,vmSources)
                    &&!native_original.ExceptionCheck(env);
            if(!allowed)native_refuse(env,"RONOVA_NATIVE_REGISTRATION_REFUSED");
        }else allowed=native_registration_allowed(env,declaring,snapshot[i].fnPtr,caller);
        native_original.DeleteLocalRef(env,declaring);if(!allowed)goto finished;
    }
    state->registration=&registration;result=native_original.RegisterNatives(env,type,snapshot,count);state->registration=registration.previous;
    if(result==JNI_OK&&vmInstall){
        /* The VM registrar guarantees the real JNI result is JNI_OK. Perform
         * our binding capture after it returns, never rewrite that result. */
        vmInstall->methods=snapshot;vmInstall->actual=actual;vmInstall->count=count;vmInstall->owners=owners;vmInstall->caller=caller;
        snapshot=NULL;actual=NULL;owners=NULL;
    }
finished:
    if(vmSources)native_original.DeleteLocalRef(env,vmSources);
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
    jmethodID writer=native_original.GetStaticMethodID(env,native_controls,"recoveryWriter","()Z");
    if(!writer||!native_original.CallStaticBooleanMethod(env,native_controls,writer)){native_refuse(env,"RECOVERY_FIELD_WRITER_REQUIRED");return JNI_FALSE;}
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
    jmethodID writer=native_original.GetStaticMethodID(env,native_controls,"recoveryWriter","()Z");
    if(!writer||!native_original.CallStaticBooleanMethod(env,native_controls,writer)){native_refuse(env,"RECOVERY_FIELD_WRITER_REQUIRED");return JNI_FALSE;}
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
    if(declaring)native_original.CallStaticVoidMethod(env,native_controls,native_created_query,declaring,actual);
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
static int native_thread_java_pending(const NativeThread *state){
    if(state->vmAttached||state->javaCleaning||state->vmDetaching||state->vmThreadEnding
            ||state->depth||state->execution||state->memoryWrite||state->filePermit||state->threadPermit
            ||state->defined||state->prepared||state->changedClass||state->created||state->mutation
            ||state->popped||state->changedHolder||state->bufferAddress||state->bufferSources||state->unobservedLibrary
            ||state->definition||state->definitionCall||state->registration||state->bindingSeed||state->unsafeMutation
            ||state->processScope||state->processCreated)return 1;
    for(unsigned i=0;i<128;i++){
        const NativeUnsafeFrame *unsafe=&state->unsafeFrames[i];
        const NativeFileFrame *file=&state->fileFrames[i];
        const NativeThreadFrame *thread=&state->threadFrames[i];
        if(state->stack[i]||state->entries[i]||unsafe->token
                ||file->receiver||file->token||file->declaring||file->method||file->sources||file->references||file->arguments
                ||thread->receiver||thread->token||thread->sources||thread->operation)return 1;
    }
    return 0;
}
/* The exact OS thread has terminated. Only its terminal Host call's own hold
 * and heap scope have been removed by the caller. Never operate on this
 * daemon's TLS, synthesize ThreadEnd, or close another thread's local refs. */
static int native_thread_module_exit_reaped(NativeThread *state){
    if(!state)return 1;
    if(state->hostWorker)return 1; /* the existing worker still owns this state */
    if(!state->storageOwned||state->callbackHolds||state->tooling||state->library||state->hostLoad||state->hostRelease
            ||state->heapCallback||state->heapWorkerAnchor||state->toolingCallbackEnvironment||state->toolingImage
            ||state->control||state->sourceCapture||state->unsafeInstalling||state->unsafeInstall||state->bufferAllocation
            ||native_thread_java_pending(state))return 0;
    native_private_unregister(&state->jniRange);SecureZeroMemory(state,sizeof(*state));free(state);return 1;
}
static void native_thread_java_end(JNIEnv *env,NativeThread *state){
    if(state->javaCleaning)return;state->javaCleaning=1;state->javaEnded=1;
    native_tooling_definitions_thread_end(state);
    host_thread_end(env,state);
    code_thread_end(env,state);
    native_file_thread_end(env,state);
    for(unsigned i=state->depth;i>0;i--){
        native_thread_finish(env,state,&state->threadFrames[i-1],0);
        if(state->unsafeFrames[i-1].token){jobject token=state->unsafeFrames[i-1].token;state->unsafeFrames[i-1].token=NULL;
            state->control++;native_end_mutation(env,token,0);state->control--;InterlockedIncrement(&native_failures);}
        Binding *active=state->stack[i-1];state->stack[i-1]=state->entries[i-1]=NULL;
        if(active){InterlockedDecrement(&active->active);host_module_binding_leave(active);}
        SecureZeroMemory(&state->unsafeFrames[i-1],sizeof(state->unsafeFrames[i-1]));
        state->depth=i-1;
    }
    /* A second environment's ThreadEnd must not decrement the same binding.
     * Later ThreadEnd callbacks can create new Java attachments; those are
     * drained on their own callback exit using the same cleared records. */
    state->depth=0;state->filePermit=NULL;state->threadPermit=NULL;
    native_memory_thread_end(env,state);
    state->defined=state->prepared=state->changedClass=NULL;
    state->created=state->mutation=state->popped=state->changedHolder=state->bufferAddress=state->unobservedLibrary=NULL;
    state->bufferSources=NULL;state->vmAttached=0;state->javaCleaning=0;native_private_unregister(&state->jniRange);
}
static void native_thread_native_end(NativeThread *state){
    while(state->library){NativeLibraryScope *scope=state->library;state->library=scope->previous;
        // An interrupted load has not demonstrated image retirement.
        InterlockedIncrement(&native_failures);SecureZeroMemory(scope,sizeof(*scope));free(scope);}
}
static void native_thread_try_release(NativeThread *state){
    if(!state||!state->storageOwned||!state->javaEnded||state->callbackHolds||state->hostWorker
            ||state->tooling||state->heapCallback||state->heapWorkerAnchor
            ||state->control||state->sourceCapture||state->unsafeInstalling||state->bufferAllocation
            ||native_thread_java_pending(state))return;
    if(native_tls!=TLS_OUT_OF_INDEXES&&(NativeThread*)TlsGetValue(native_tls)==state&&!TlsSetValue(native_tls,NULL)){
        InterlockedIncrement(&native_failures);return;
    }
    native_thread_native_end(state);native_private_unregister(&state->jniRange);SecureZeroMemory(state,sizeof(*state));free(state);
}
static void native_thread_callback_leave(NativeThread *state){
    if(!state||!state->callbackHolds){InterlockedIncrement(&native_failures);return;}
    state->callbackHolds--;native_thread_try_release(state);
}
/* Called only by the Host reaper after this exact OS thread has signaled and
 * its creator publication has drained. A daemon JNIEnv cannot close another
 * thread's local JNI references or manufacture a missing VM ThreadEnd.
 * Fresh pure-C states need no Java cleanup; detached states already ran it on
 * the actual event thread. An omitted detach or abandoned callback stays open. */
static int native_thread_worker_os_end(JNIEnv *env,NativeThread *state){
    (void)env;
    if(!state||!state->hostWorker||state->callbackHolds||state->heapCallback||state->heapWorkerAnchor||state->library
            ||state->toolingCallbackEnvironment||state->toolingImage||!host_thread_worker_scope_idle(state)
            ||state->control||state->sourceCapture||state->unsafeInstalling||state->bufferAllocation
            ||native_thread_java_pending(state))return 0;
    state->hostWorker=NULL;native_private_unregister(&state->jniRange);SecureZeroMemory(state,sizeof(*state));free(state);return 1;
}
static void JNICALL native_thread_start(jvmtiEnv *api,JNIEnv *env,jthread thread){
    (void)api;(void)env;(void)thread;
    /* Observe only an existing current-thread state. In particular, an actual
     * worker Attach made through a saved VM function still emits ThreadStart.
     * This does not enumerate environments or intercept that saved function. */
    if(native_tls!=TLS_OUT_OF_INDEXES){NativeThread *state=(NativeThread*)TlsGetValue(native_tls);if(state)native_thread_jni_observed(state,env);}
}
static void JNICALL native_thread_end(jvmtiEnv *api,JNIEnv *env,jthread thread){
    (void)api;(void)thread;
    code_retirement_thread_end(env,thread);
    if(native_tls==TLS_OUT_OF_INDEXES)return;
    NativeThread *state=(NativeThread*)TlsGetValue(native_tls);if(!state)return;
    state->vmThreadEnding++;native_thread_java_end(env,state);state->vmThreadEnding--;
    // Array copies may have been handed to another native worker. Their actual
    // matching release, rather than creator-thread exit, closes those leases.
    native_thread_try_release(state);
}
/* Only environments actually delivered by this VM are intercepted. The public
 * ABI supplies the first function-table pointer, not an environment enumerator
 * or a barrier for saved function pointers. These shadows do not claim either. */
#define NATIVE_TOOLING_CALLBACKS(X) \
    X(VMInit,3) X(VMDeath,2) X(ThreadStart,3) X(ThreadEnd,3) \
    X(ClassFileLoadHook,10) X(ClassLoad,4) X(ClassPrepare,4) X(VMStart,2) \
    X(Exception,8) X(ExceptionCatch,6) X(SingleStep,5) X(FramePop,5) \
    X(Breakpoint,5) X(FieldAccess,8) X(FieldModification,10) \
    X(MethodEntry,4) X(MethodExit,6) X(NativeMethodBind,6) \
    X(CompiledMethodLoad,7) X(CompiledMethodUnload,3) \
    X(DynamicCodeGenerated,4) X(DataDumpRequest,1) \
    X(MonitorWait,5) X(MonitorWaited,5) X(MonitorContendedEnter,4) \
    X(MonitorContendedEntered,4) X(ResourceExhausted,5) \
    X(GarbageCollectionStart,1) X(GarbageCollectionFinish,1) X(ObjectFree,2) \
    X(VMObjectAlloc,6) X(SampledObjectAlloc,6)
enum {
#define TOOLING_INDEX(NAME,COUNT) TOOLING_##NAME,
    NATIVE_TOOLING_CALLBACKS(TOOLING_INDEX)
#undef TOOLING_INDEX
    TOOLING_COUNT
};
typedef struct NativeToolingEnv NativeToolingEnv;
typedef struct NativeToolingSlot {
    NativeToolingEnv *environment;void *callback,*entry;OwnerLink *owners;
    jweak origin;jobjectArray sourceLease;HMODULE image,pin;volatile LONG admitted,active;int unknown,agent;
    int sourceLeaseComplete,heapSourceLeaseComplete;DWORD heapImageSize;
    const void *argument;RUNTIME_FUNCTION unwind;int queued,releasing;struct NativeToolingSlot *next,*retireNext;
} NativeToolingSlot;
typedef struct NativeToolingVersion {
    jvmtiEventCallbacks callbacks;NativeToolingSlot *slots[TOOLING_COUNT];
    struct NativeToolingVersion *previous;
} NativeToolingVersion;
typedef struct NativeToolingExtension {
    jint index;jvmtiExtensionEvent callback;OwnerLink *owners;HMODULE pin;
    NativeToolingSlot *slot;
    int unknown;struct NativeToolingExtension *next;
} NativeToolingExtension;
struct NativeToolingEnv {
    jvmtiEnv *api;const struct jvmtiInterface_1_ *original,*shadow;
    NativeToolingVersion *version;NativeToolingExtension *extensions;
    OwnerLink *owners;jobjectArray sourceLease;SRWLOCK mutation;volatile LONG closing,disposed,heapTracked;
    LONG uses,active;int own,unknown,retiring,queued;struct NativeToolingEnv *next,*retireNext;
    NativePrivateRange apiRange;
};
typedef struct NativeToolingUse {NativeToolingEnv *environment;} NativeToolingUse;
typedef struct NativeToolingInvocation {
    NativeToolingSlot *slot;NativeThread *thread;NativeToolingScope scope;
    struct NativeToolingInvocation *previous;
    unsigned savedControl;int controlSuspended;
} NativeToolingInvocation;
struct NativeToolingImage {
    jobject declaration,loader,domain;jstring name;jclass actual;jbyteArray original,bytes;
    jobjectArray writers;jboolean changed;unsigned phase;
    struct NativeToolingImage *previous;
};
static SRWLOCK native_tooling_records=SRWLOCK_INIT;
static NativeToolingEnv *native_tooling_environments;
static NativeToolingSlot *native_tooling_slots;
static const struct JNIInvokeInterface_ *native_vm_shadow;
static struct JNINativeInterface_ native_guarded;
static volatile LONG native_tooling_ready,native_tooling_failures,native_guarded_ready;
static HMODULE native_tooling_pin;
static HMODULE native_control_image(void){
    return (HMODULE)InterlockedCompareExchangePointer((void *volatile*)&native_tooling_pin,NULL,NULL);
}
static uintptr_t native_tooling_image_begin,native_tooling_image_end;
static NativePrivateRange native_tooling_image_range,native_tooling_vm_range;
static jmethodID native_tooling_image_query,native_tooling_candidate_query,native_tooling_thread_query,native_tooling_frame_query,native_tooling_protected_query;
static jmethodID native_tooling_heap_target,native_tooling_definitions_query;
static jobject native_tooling_deferred;
static HANDLE native_tooling_retirement_event;
static NativeToolingSlot *native_tooling_retired_slots;
static NativeToolingEnv *native_tooling_retired_environments;
static volatile LONG native_tooling_shutdown;
static void native_thread_worker_wake(void){
    if(native_tooling_retirement_event)SetEvent(native_tooling_retirement_event);
}
static int native_thread_worker_backend_ready(void){
    return native_tooling_retirement_event&&InterlockedCompareExchange(&native_tooling_ready,0,0)
            &&native_jni_ready&&!InterlockedCompareExchange(&native_tooling_shutdown,0,0);
}
static void native_tooling_version_retire(NativeToolingVersion *version);
static int native_tooling_extensions_unresolved_locked(const NativeToolingEnv *record);
static int native_tooling_heap_environment_pending(NativeToolingEnv *record);
static void native_tooling_heap_environment_release(JNIEnv *env,NativeToolingEnv *record);
static jvmtiError native_tooling_error(jvmtiError error){InterlockedIncrement(&native_tooling_failures);return error;}
static int native_tooling_restricted(void){
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    return state&&(state->heapCallback||state->toolingRestricted);
}
static JNIEnv *native_tooling_jni(void){
    if(native_tooling_restricted())return NULL;
    JNIEnv *env=NULL;
    if(native_vm&&native_vm_original.GetEnv&&native_vm_original.GetEnv(native_vm,(void**)&env,JNI_VERSION_1_8)==JNI_OK&&env)
        native_thread_jni_observed(native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls),env);
    return env;
}
static NativeToolingEnv *native_tooling_environment(jvmtiEnv *api){
    if(native_tooling_restricted())return NULL;
    NativeToolingEnv *found=NULL;AcquireSRWLockShared(&native_tooling_records);
    for(NativeToolingEnv *p=native_tooling_environments;p;p=p->next)
        if(p->api==api&&!InterlockedCompareExchange(&p->disposed,0,0)){found=p;break;}
    ReleaseSRWLockShared(&native_tooling_records);return found;
}
static int native_tooling_internal(void *caller){return native_image(caller)==native_image((void*)native_tooling_environment);}
/* Queue only the exact changed registration. Restricted GC/ObjectFree callbacks
 * never detach a DLL or enter JNI to release a source lease. */
static void native_tooling_slot_queue_locked(NativeToolingSlot *slot){
    if(slot->queued){if(native_tooling_retirement_event)SetEvent(native_tooling_retirement_event);return;}
    if(slot->admitted||slot->active||slot->releasing||(!slot->pin&&!slot->sourceLease&&!slot->origin))return;
    slot->queued=1;slot->retireNext=native_tooling_retired_slots;native_tooling_retired_slots=slot;
    if(native_tooling_retirement_event)SetEvent(native_tooling_retirement_event);
}
static void native_tooling_environment_queue_locked(NativeToolingEnv *record){
    if(record->queued||record->uses||record->active
            ||(record->disposed?(!record->sourceLease||native_tooling_extensions_unresolved_locked(record))&&!native_tooling_heap_environment_pending(record):!record->retiring||!record->closing))return;
    record->queued=1;record->retireNext=native_tooling_retired_environments;native_tooling_retired_environments=record;
    if(native_tooling_retirement_event)SetEvent(native_tooling_retirement_event);
}
static int native_tooling_use_begin(NativeToolingEnv *record,NativeToolingUse *use){
    int allowed=0;AcquireSRWLockExclusive(&native_tooling_records);
    if(!native_tooling_shutdown&&!record->closing&&!record->disposed){record->uses++;use->environment=record;allowed=1;}
    ReleaseSRWLockExclusive(&native_tooling_records);return allowed;
}
static void native_tooling_use_end(NativeToolingUse *use){
    if(!use->environment)return;AcquireSRWLockExclusive(&native_tooling_records);
    NativeToolingEnv *record=use->environment;record->uses--;native_tooling_environment_queue_locked(record);
    ReleaseSRWLockExclusive(&native_tooling_records);
}
/* A native-only LoadLibrary continuation may retain this actual executing
 * callback's prepared strong source lease, without entering JNI or inventing
 * an attachment. It returns the existing slot hold through the normal reaper. */
static int native_tooling_load_borrow(NativeThread *state,OwnerLink *owners,void **token){
    if(token)*token=NULL;if(!state||!state->tooling||!state->toolingCallbackEnvironment||!token)return 0;
    if(!TryAcquireSRWLockExclusive(&native_tooling_records))return 0;
    NativeToolingScope *scope=state->tooling;NativeToolingSlot *chosen=NULL;
    for(NativeToolingSlot *slot=native_tooling_slots;slot;slot=slot->next)
        if(slot->environment==state->toolingCallbackEnvironment&&scope->owners==slot->owners
                &&scope->origin==slot->origin&&scope->depth==state->depth){chosen=slot;break;}
    int ready=chosen&&!native_tooling_shutdown&&chosen->admitted&&chosen->active>0&&!chosen->releasing
            &&!chosen->unknown&&!scope->unknown&&chosen->sourceLease&&chosen->sourceLeaseComplete
            &&!chosen->environment->closing&&!chosen->environment->disposed
            &&host_module_sources_cover(chosen->owners,owners)&&!native_owner_stopped(owners);
    if(ready){InterlockedIncrement(&chosen->active);chosen->environment->active++;*token=chosen;}
    ReleaseSRWLockExclusive(&native_tooling_records);return ready;
}
#define TOOLING_USE(record) NativeToolingUse toolingUse __attribute__((cleanup(native_tooling_use_end)))={NULL}; \
    if(native_tooling_restricted())return JVMTI_ERROR_NOT_AVAILABLE; \
    if(!native_tooling_use_begin(record,&toolingUse))return JVMTI_ERROR_INVALID_ENVIRONMENT
static int native_tooling_capture(JNIEnv *env,void *caller,OwnerLink **owners,jweak *origin,int *unknown){
    NativeThread *state=native_thread();int valid=host_module_capture(state,caller,owners,NULL,NULL,unknown)
            &&native_capture_image_owners(caller,owners);
    if(!state||!native_jni_ready||!env)valid=valid&&host_tls_capture(state,owners,unknown);
    if(state){
        if(native_jni_ready&&env)valid=valid&&native_capture_scope_sources(env,state,owners,unknown);
        else {
            for(unsigned i=0;i<state->depth;i++)valid=valid&&native_owner_merge(owners,state->stack[i]->owners);
            for(NativeLibraryScope *p=state->library;p;p=p->previous)valid=valid&&native_owner_merge(owners,p->library->owners);
            for(NativeToolingScope *p=state->tooling;p;p=p->previous){valid=valid&&native_owner_merge(owners,p->owners);if(p->unknown)*unknown=1;}
            *unknown=1;
        }
        jweak actual=native_origin(state);
        if(origin&&actual&&env&&native_original.NewWeakGlobalRef)*origin=native_original.NewWeakGlobalRef(env,actual);
    }else *unknown=1;
    return valid&&(!env||!native_original.ExceptionCheck||!native_original.ExceptionCheck(env));
}
static int native_tooling_stopped(JNIEnv *env,void *caller){
    if(native_caller_stopped(caller))return 1;NativeThread *state=native_thread();
    for(NativeToolingScope *p=state?state->tooling:NULL;p;p=p->previous)if(native_owner_stopped(p->owners))return 1;
    return native_jni_ready&&env&&native_stopped(env);
}
static int native_tooling_effect_stopped(NativeToolingEnv *record,JNIEnv *env,void *caller){
    /* Passing an actual environment through another caller does not erase its
     * acquisition source. These owners live for the retained environment. */
    return native_owner_stopped(record->owners)||native_tooling_stopped(env,caller);
}
/* The context is an extra first Win64 argument. Each VM callback contains its
 * immutable slot directly, so a replaced callback never borrows the new table's
 * provenance. All public JDK 17 event arguments occupy integer ABI slots. */
static void *native_tooling_entry(NativeToolingSlot *slot,void *target,unsigned arguments){
    unsigned extra=arguments>3?(arguments-3)*8:0,frame=((32+extra+15)&~15)+8;
    unsigned char *page=(unsigned char*)VirtualAlloc(NULL,4096,MEM_COMMIT|MEM_RESERVE,PAGE_READWRITE);if(!page)return NULL;
    NativeCode code={page,page};nb(&code,0x48);nb(&code,0x81);nb(&code,0xec);nw(&code,frame);
    if(arguments>=4)ns(&code,9,32);
    for(unsigned i=4;i<arguments;i++){nl(&code,0,frame+40+(i-4)*8);ns(&code,0,40+(i-4)*8);}
    nb(&code,0x4d);nb(&code,0x89);nb(&code,0xc1); /* r9 = r8 */
    nb(&code,0x49);nb(&code,0x89);nb(&code,0xd0); /* r8 = rdx */
    nb(&code,0x48);nb(&code,0x89);nb(&code,0xca); /* rdx = rcx */
    ni(&code,1,(uintptr_t)slot);nc(&code,target);
    nb(&code,0x48);nb(&code,0x81);nb(&code,0xc4);nw(&code,frame);nb(&code,0xc3);
    slot->unwind.BeginAddress=0;slot->unwind.EndAddress=(DWORD)(code.at-page);slot->unwind.UnwindData=4080;
    unsigned char *unwind=page+4080;unwind[0]=1;unwind[1]=7;unwind[2]=2;unwind[3]=0;unwind[4]=7;unwind[5]=1;
    unsigned short scaled=(unsigned short)(frame/8);memcpy(unwind+6,&scaled,2);
    DWORD previous;
    if(!host_real_virtual_protect(page,4096,PAGE_EXECUTE_READ,&previous)||!RtlAddFunctionTable(&slot->unwind,1,(DWORD64)(uintptr_t)page)){
        host_real_virtual_free(page,0,MEM_RELEASE);return NULL;
    }
    FlushInstructionCache(GetCurrentProcess(),page,4096);return page;
}
static int native_tooling_callback_begin(NativeToolingSlot *slot,jvmtiEnv *api,NativeToolingInvocation *invocation){
    memset(invocation,0,sizeof(*invocation));
    AcquireSRWLockExclusive(&native_tooling_records);
    int allowed=slot&&slot->environment->api==api&&InterlockedCompareExchange(&slot->admitted,0,0)
            &&!InterlockedCompareExchange(&native_tooling_shutdown,0,0)
            &&!InterlockedCompareExchange(&slot->environment->closing,0,0)
            &&!InterlockedCompareExchange(&slot->environment->disposed,0,0)&&!native_owner_stopped(slot->owners);
    if(allowed){InterlockedIncrement(&slot->active);slot->environment->active++;invocation->slot=slot;}
    ReleaseSRWLockExclusive(&native_tooling_records);if(!allowed)return 0;
    NativeThread *state=native_thread();
    if(!state){AcquireSRWLockExclusive(&native_tooling_records);InterlockedDecrement(&slot->active);slot->environment->active--;
        native_tooling_slot_queue_locked(slot);native_tooling_environment_queue_locked(slot->environment);ReleaseSRWLockExclusive(&native_tooling_records);invocation->slot=NULL;return 0;}
    invocation->thread=state;
    invocation->previous=state->toolingInvocation;state->toolingInvocation=invocation;
    invocation->scope=(NativeToolingScope){slot->owners,slot->origin,slot->unknown,state->depth,state->tooling};
    native_thread_callback_enter(state);state->tooling=&invocation->scope;
    /* The VM may synchronously call an outside event from controller JNI.
     * Its source hold is real, but it must not inherit that JNI recursion bypass. */
    invocation->savedControl=state->control;
    invocation->controlSuspended=!slot->image||slot->image!=native_control_image();
    if(invocation->controlSuspended)state->control=0;
    return 1;
}
static void native_tooling_slot_retire(NativeToolingSlot *slot){
    if(!slot)return;
    AcquireSRWLockExclusive(&native_tooling_records);InterlockedExchange(&slot->admitted,0);
    native_tooling_slot_queue_locked(slot);ReleaseSRWLockExclusive(&native_tooling_records);
}
static void native_tooling_callback_end(NativeToolingInvocation *invocation){
    if(!invocation->slot)return;DWORD error=GetLastError();NativeThread *state=invocation->thread;state->tooling=invocation->scope.previous;
    state->toolingInvocation=invocation->previous;
    AcquireSRWLockExclusive(&native_tooling_records);NativeToolingSlot *slot=invocation->slot;
    InterlockedDecrement(&slot->active);slot->environment->active--;if(slot->agent)InterlockedExchange(&slot->admitted,0);
    native_tooling_slot_queue_locked(slot);native_tooling_environment_queue_locked(slot->environment);
    ReleaseSRWLockExclusive(&native_tooling_records);
    if(invocation->controlSuspended)state->control=invocation->savedControl;
    invocation->slot=NULL;invocation->thread=NULL;native_thread_callback_leave(state);SetLastError(error);
}
#define COMMA ,
#define TOOLING_CALLBACK(NAME,ARGS,CALL) \
static void JNICALL native_tooling_##NAME(NativeToolingSlot *slot,jvmtiEnv *api,ARGS){ \
    NativeToolingInvocation invocation;if(!native_tooling_callback_begin(slot,api,&invocation))return; \
    ((jvmtiEvent##NAME)slot->callback)(api,CALL);native_tooling_callback_end(&invocation); \
}
TOOLING_CALLBACK(VMInit,JNIEnv *env COMMA jthread thread,env COMMA thread)
TOOLING_CALLBACK(VMDeath,JNIEnv *env,env)
static void JNICALL native_tooling_ThreadStart(NativeToolingSlot *slot,jvmtiEnv *api,JNIEnv *env,jthread thread){
    NativeToolingInvocation invocation;if(!native_tooling_callback_begin(slot,api,&invocation))return;
    native_thread_jni_observed(invocation.thread,env);
    ((jvmtiEventThreadStart)slot->callback)(api,env,thread);native_tooling_callback_end(&invocation);
}
static void JNICALL native_tooling_ThreadEnd(NativeToolingSlot *slot,jvmtiEnv *api,JNIEnv *env,jthread thread){
    NativeToolingInvocation invocation;if(!native_tooling_callback_begin(slot,api,&invocation))return;
    NativeThread *state=invocation.thread;state->vmThreadEnding++;
    if(!native_thread_jni_observed(state,env)){state->vmThreadEnding--;native_tooling_callback_end(&invocation);return;}
    ((jvmtiEventThreadEnd)slot->callback)(api,env,thread);
    /* Our own environment may have already drained the earlier Java records.
     * Clear any actual records created by this callback before releasing its
     * C hold, while the real event thread's JNIEnv is still valid. */
    native_thread_java_end(env,state);state->vmThreadEnding--;native_tooling_callback_end(&invocation);
}
TOOLING_CALLBACK(ClassLoad,JNIEnv *env COMMA jthread thread COMMA jclass type,env COMMA thread COMMA type)
TOOLING_CALLBACK(ClassPrepare,JNIEnv *env COMMA jthread thread COMMA jclass type,env COMMA thread COMMA type)
TOOLING_CALLBACK(VMStart,JNIEnv *env,env)
TOOLING_CALLBACK(Exception,JNIEnv *env COMMA jthread thread COMMA jmethodID method COMMA jlocation location COMMA jobject exception COMMA jmethodID catcher COMMA jlocation caught,env COMMA thread COMMA method COMMA location COMMA exception COMMA catcher COMMA caught)
TOOLING_CALLBACK(ExceptionCatch,JNIEnv *env COMMA jthread thread COMMA jmethodID method COMMA jlocation location COMMA jobject exception,env COMMA thread COMMA method COMMA location COMMA exception)
TOOLING_CALLBACK(SingleStep,JNIEnv *env COMMA jthread thread COMMA jmethodID method COMMA jlocation location,env COMMA thread COMMA method COMMA location)
TOOLING_CALLBACK(FramePop,JNIEnv *env COMMA jthread thread COMMA jmethodID method COMMA jboolean exceptional,env COMMA thread COMMA method COMMA exceptional)
TOOLING_CALLBACK(Breakpoint,JNIEnv *env COMMA jthread thread COMMA jmethodID method COMMA jlocation location,env COMMA thread COMMA method COMMA location)
TOOLING_CALLBACK(FieldAccess,JNIEnv *env COMMA jthread thread COMMA jmethodID method COMMA jlocation location COMMA jclass type COMMA jobject object COMMA jfieldID field,env COMMA thread COMMA method COMMA location COMMA type COMMA object COMMA field)
TOOLING_CALLBACK(FieldModification,JNIEnv *env COMMA jthread thread COMMA jmethodID method COMMA jlocation location COMMA jclass type COMMA jobject object COMMA jfieldID field COMMA char kind COMMA jvalue value,env COMMA thread COMMA method COMMA location COMMA type COMMA object COMMA field COMMA kind COMMA value)
TOOLING_CALLBACK(MethodEntry,JNIEnv *env COMMA jthread thread COMMA jmethodID method,env COMMA thread COMMA method)
TOOLING_CALLBACK(MethodExit,JNIEnv *env COMMA jthread thread COMMA jmethodID method COMMA jboolean exceptional COMMA jvalue value,env COMMA thread COMMA method COMMA exceptional COMMA value)
TOOLING_CALLBACK(CompiledMethodLoad,jmethodID method COMMA jint size COMMA const void *address COMMA jint count COMMA const jvmtiAddrLocationMap *map COMMA const void *info,method COMMA size COMMA address COMMA count COMMA map COMMA info)
TOOLING_CALLBACK(CompiledMethodUnload,jmethodID method COMMA const void *address,method COMMA address)
TOOLING_CALLBACK(DynamicCodeGenerated,const char *name COMMA const void *address COMMA jint length,name COMMA address COMMA length)
static void JNICALL native_tooling_DataDumpRequest(NativeToolingSlot *slot,jvmtiEnv *api){NativeToolingInvocation invocation;if(!native_tooling_callback_begin(slot,api,&invocation))return;((jvmtiEventDataDumpRequest)slot->callback)(api);native_tooling_callback_end(&invocation);}
TOOLING_CALLBACK(MonitorWait,JNIEnv *env COMMA jthread thread COMMA jobject monitor COMMA jlong timeout,env COMMA thread COMMA monitor COMMA timeout)
TOOLING_CALLBACK(MonitorWaited,JNIEnv *env COMMA jthread thread COMMA jobject monitor COMMA jboolean timeout,env COMMA thread COMMA monitor COMMA timeout)
TOOLING_CALLBACK(MonitorContendedEnter,JNIEnv *env COMMA jthread thread COMMA jobject monitor,env COMMA thread COMMA monitor)
TOOLING_CALLBACK(MonitorContendedEntered,JNIEnv *env COMMA jthread thread COMMA jobject monitor,env COMMA thread COMMA monitor)
TOOLING_CALLBACK(ResourceExhausted,JNIEnv *env COMMA jint flags COMMA const void *reserved COMMA const char *description,env COMMA flags COMMA reserved COMMA description)
static void native_tooling_gc_callback(NativeToolingSlot *slot,jvmtiEnv *api){
    NativeToolingInvocation invocation;if(!native_tooling_callback_begin(slot,api,&invocation))return;
    NativeThread *state=invocation.thread;NativeToolingEnv *previous=state->toolingCallbackEnvironment;
    state->toolingCallbackEnvironment=slot->environment;state->toolingRestricted++;
    ((jvmtiEventGarbageCollectionStart)slot->callback)(api);
    state->toolingRestricted--;state->toolingCallbackEnvironment=previous;native_tooling_callback_end(&invocation);
}
static void JNICALL native_tooling_GarbageCollectionStart(NativeToolingSlot *slot,jvmtiEnv *api){native_tooling_gc_callback(slot,api);}
static void JNICALL native_tooling_GarbageCollectionFinish(NativeToolingSlot *slot,jvmtiEnv *api){native_tooling_gc_callback(slot,api);}
static void JNICALL native_tooling_ObjectFree(NativeToolingSlot *slot,jvmtiEnv *api,jlong tag){
    NativeToolingInvocation invocation;if(!native_tooling_callback_begin(slot,api,&invocation))return;
    NativeThread *state=invocation.thread;NativeToolingEnv *previous=state->toolingCallbackEnvironment;
    state->toolingCallbackEnvironment=slot->environment;state->toolingRestricted++;
    ((jvmtiEventObjectFree)slot->callback)(api,tag);
    state->toolingRestricted--;state->toolingCallbackEnvironment=previous;native_tooling_callback_end(&invocation);
}
TOOLING_CALLBACK(VMObjectAlloc,JNIEnv *env COMMA jthread thread COMMA jobject object COMMA jclass type COMMA jlong size,env COMMA thread COMMA object COMMA type COMMA size)
TOOLING_CALLBACK(SampledObjectAlloc,JNIEnv *env COMMA jthread thread COMMA jobject object COMMA jclass type COMMA jlong size,env COMMA thread COMMA object COMMA type COMMA size)
#undef TOOLING_CALLBACK
#undef COMMA
static jobjectArray native_tooling_writers(JNIEnv *env,OwnerLink *owners){
    jclass module=native_original.FindClass(env,"java/lang/Module");if(!module)return NULL;
    jsize count=0;for(OwnerLink *p=owners;p;p=p->next)count++;
    jobjectArray result=native_original.NewObjectArray(env,count,module,NULL);native_original.DeleteLocalRef(env,module);
    for(OwnerLink *p=owners;result&&p;p=p->next){jobject value=native_original.NewLocalRef(env,p->owner->module);native_original.SetObjectArrayElement(env,result,--count,value);if(value)native_original.DeleteLocalRef(env,value);}
    return result;
}
static jobjectArray native_tooling_sources(JNIEnv *env,OwnerLink *owners,int unknown){
    if(!unknown)return native_tooling_writers(env,owners);
    jclass module=native_original.FindClass(env,"java/lang/Module");if(!module)return NULL;
    jsize count=1;for(OwnerLink *p=owners;p;p=p->next)count++;
    jobjectArray result=native_original.NewObjectArray(env,count,module,NULL);native_original.DeleteLocalRef(env,module);
    for(OwnerLink *p=owners;result&&p;p=p->next){jobject value=native_original.NewLocalRef(env,p->owner->module);native_original.SetObjectArrayElement(env,result,--count,value);if(value)native_original.DeleteLocalRef(env,value);}
    /* The remaining null is the existing thread/resource unknown-source mark. */
    return result;
}
/* A hook's output is finalized immediately after that observed callback. VM
 * environment order and the non-retransformable/retransformable passes remain
 * unchanged. Unchanged outside callbacks retain their exact supplied output. */
static jobject native_tooling_declaration(JNIEnv *env,jclass actual,jobject loader,const char *name){
    if(actual)return native_original.GetModule(env,actual);
    jobject module=NULL;char *package=name?_strdup(name):NULL;
    if(package){char *last=strrchr(package,'/');if(last)*last=0;else *package=0;
        jvmtiError result=(*native_ti)->GetNamedModule(native_ti,loader,package,&module);free(package);
        if(result!=JVMTI_ERROR_NONE){native_refuse(env,"ACTUAL_JVMTI_DEFINITION_MODULE_UNAVAILABLE");return NULL;}}
    if(module)return module;
    if(loader){jclass type=native_original.GetObjectClass(env,loader);jmethodID getter=type?native_original.GetMethodID(env,type,"getUnnamedModule","()Ljava/lang/Module;"):NULL;
        module=getter?native_original.CallObjectMethod(env,loader,getter):NULL;if(type)native_original.DeleteLocalRef(env,type);return module;}
    jclass object=native_original.FindClass(env,"java/lang/Object");module=object?native_original.GetModule(env,object):NULL;if(object)native_original.DeleteLocalRef(env,object);return module;
}
static int native_tooling_image(JNIEnv *env,jvmtiEnv *api,jclass actual,jobject loader,const char *name,jobject domain,
        jint originalLength,const unsigned char *originalBytes,jint length,const unsigned char *bytes,OwnerLink *owners,int unknown,jboolean changed,jint *newLength,unsigned char **newBytes){
    /* Our own event supplies no changed output. Its Java callback only proved
     * this tuple and returned the same bytes; root/source capture belongs to
     * the separate definition/ClassPrepare paths. Do not allocate or cross JNI
     * for that no-op, including unknown, hidden and protected class names. */
    if(!changed)return 1;
    if(!native_jni_ready||!native_tooling_image_query||!env)return 1;
    NativeThread *state=native_thread();if(!state||length<0||originalLength<0||!bytes||!originalBytes)return 0;
    state->control++;jbyteArray original=NULL,input=NULL,result=NULL;int ready=0;
    jstring key=name?native_original.NewStringUTF(env,name):NULL;jobjectArray writers=native_tooling_sources(env,owners,unknown);
    jobject declaration=native_tooling_declaration(env,actual,loader,name);
    if(!declaration||!writers||native_original.ExceptionCheck(env))goto done;
    /* The exact Java registration directory may exclude a changed unrelated
     * image before copying either byte array. Missing names or unknown source
     * holds still take the complete path. A selector failure is not a verdict. */
    if(!unknown&&name&&native_tooling_candidate_query){
        jboolean selected=native_original.CallStaticBooleanMethod(env,native_controller,native_tooling_candidate_query,declaration,loader,key,actual,writers);
        if(native_original.ExceptionCheck(env)){native_original.ExceptionClear(env);InterlockedIncrement(&native_tooling_failures);}
        else if(!selected){ready=1;goto done;}
    }
    original=native_original.NewByteArray(env,originalLength);
    if(original)native_original.SetByteArrayRegion(env,original,0,originalLength,(const jbyte*)originalBytes);
    input=originalLength==length&&originalBytes==bytes?original:native_original.NewByteArray(env,length);
    if(input&&input!=original)native_original.SetByteArrayRegion(env,input,0,length,(const jbyte*)bytes);
    NativeToolingImage proof={declaration,loader,domain,key,actual,original,input,writers,changed,1,state->toolingImage};state->toolingImage=&proof;
    result=original&&input&&!native_original.ExceptionCheck(env)?(jbyteArray)native_original.CallStaticObjectMethod(env,native_controller,native_tooling_image_query,declaration,loader,key,actual,domain,original,input,writers,changed):NULL;
    state->toolingImage=proof.previous;ready=!native_original.ExceptionCheck(env)&&proof.phase==2&&result;
    if(ready){jsize count=native_original.GetArrayLength(env,result);jbyte *contents=native_original.GetByteArrayElements(env,result,NULL);
        ready=contents!=NULL;
        if(contents){if(count!=length||memcmp(contents,bytes,(size_t)length)){
                unsigned char *image=NULL;ready=(*api)->Allocate(api,count,&image)==JVMTI_ERROR_NONE&&image;
                if(ready){memcpy(image,contents,(size_t)count);if(*newBytes)(*api)->Deallocate(api,*newBytes);*newBytes=image;*newLength=count;}
            }native_original.ReleaseByteArrayElements(env,result,contents,JNI_ABORT);}
    }
done:
    if(result)native_original.DeleteLocalRef(env,result);if(writers)native_original.DeleteLocalRef(env,writers);
    if(declaration)native_original.DeleteLocalRef(env,declaration);if(input&&input!=original)native_original.DeleteLocalRef(env,input);
    if(key)native_original.DeleteLocalRef(env,key);if(original)native_original.DeleteLocalRef(env,original);state->control--;
    if(!ready)InterlockedIncrement(&native_tooling_failures);return ready;
}
static void native_tooling_invalid_image(jvmtiEnv *api,jint *length,unsigned char **bytes){
    if(*bytes&&*length>0){(*bytes)[0]=0;*length=1;InterlockedIncrement(&native_tooling_failures);return;}
    unsigned char *rejected=NULL;
    if((*api)->Allocate(api,1,&rejected)==JVMTI_ERROR_NONE&&rejected){*rejected=0;if(*bytes)(*api)->Deallocate(api,*bytes);*bytes=rejected;*length=1;}
    InterlockedIncrement(&native_tooling_failures);
}
static void JNICALL native_tooling_ClassFileLoadHook(NativeToolingSlot *slot,jvmtiEnv *api,JNIEnv *env,jclass actual,jobject loader,const char *name,jobject domain,jint length,const unsigned char *bytes,jint *newLength,unsigned char **newBytes){
    NativeToolingInvocation invocation;if(!native_tooling_callback_begin(slot,api,&invocation))return;
    unsigned char *baseline=length>0&&bytes?(unsigned char*)malloc((size_t)length):NULL;if(baseline)memcpy(baseline,bytes,(size_t)length);
    ((jvmtiEventClassFileLoadHook)slot->callback)(api,env,actual,loader,name,domain,length,bytes,newLength,newBytes);
    if(*newBytes){int changed=*newLength!=length||*newLength>=0&&(!baseline||memcmp(*newBytes,baseline,(size_t)length));
        if(*newLength<0||changed&&(!baseline||!native_tooling_image(env,api,actual,loader,name,domain,length,baseline,*newLength,*newBytes,slot->owners,slot->unknown,JNI_TRUE,newLength,newBytes)))native_tooling_invalid_image(api,newLength,newBytes);
    }
    free(baseline);native_tooling_callback_end(&invocation);
}
static void JNICALL native_tooling_own_image(jvmtiEnv *api,JNIEnv *env,jclass actual,jobject loader,const char *name,jobject domain,jint length,const unsigned char *bytes,jint *newLength,unsigned char **newBytes){
    if(api!=native_ti||!native_jni_ready||!native_tooling_image_query)return;
    if(!native_tooling_image(env,api,actual,loader,name,domain,length,bytes,length,bytes,NULL,0,JNI_FALSE,newLength,newBytes))native_tooling_invalid_image(api,newLength,newBytes);
}
static void JNICALL native_tooling_NativeMethodBind(NativeToolingSlot *slot,jvmtiEnv *api,JNIEnv *env,jthread thread,jmethodID method,void *target,void **replacement){
    NativeToolingInvocation invocation;if(!native_tooling_callback_begin(slot,api,&invocation))return;
    ((jvmtiEventNativeMethodBind)slot->callback)(api,env,thread,method,target,replacement);
    if(env&&*replacement!=target){
        jclass declaring=NULL;
        int controller=native_image(*replacement)==native_image((void*)native_binding)
                ||(*native_ti)->GetMethodDeclaringClass(native_ti,method,&declaring)==JVMTI_ERROR_NONE&&declaring
                &&(native_original.IsSameObject(env,declaring,native_controller)||native_library_class&&native_original.IsSameObject(env,declaring,native_library_class));
        if(declaring)native_original.DeleteLocalRef(env,declaring);
        if(controller){*replacement=target;InterlockedIncrement(&native_tooling_failures);native_tooling_callback_end(&invocation);return;}
        NativeThread *state=invocation.thread;OwnerLink *owners=NULL;
        int ready=native_owner_merge(&owners,slot->owners)&&native_capture_image_owners(*replacement,&owners);
        NativeRegistration registration={&method,1,owners,slot->origin,NULL,state->registration};state->registration=&registration;
        if(ready&&*replacement&&!native_original.ExceptionCheck(env))native_binding(native_ti,env,thread,method,*replacement,replacement);
        else {*replacement=(void*)native_rejected;InterlockedIncrement(&native_tooling_failures);}
        state->registration=registration.previous;native_owner_links_free(owners);
    }
    native_tooling_callback_end(&invocation);
}
static void JNICALL native_tooling_agent(jvmtiEnv *api,JNIEnv *env,void *argument){
    NativeToolingSlot *slot=(NativeToolingSlot*)argument;NativeToolingInvocation invocation;
    if(native_tooling_callback_begin(slot,api,&invocation)){
        ((jvmtiStartFunction)slot->callback)(api,env,(void*)slot->argument);native_tooling_callback_end(&invocation);}
    else native_tooling_slot_retire(slot);
}
static NativeToolingSlot *native_tooling_slot(JNIEnv *env,NativeToolingEnv *record,void *callback,void *wrapper,unsigned arguments,OwnerLink *writers,jweak origin,int unknown,int agent){
    NativeToolingSlot *slot=calloc(1,sizeof(*slot));if(!slot)return NULL;
    slot->environment=record;slot->callback=callback;slot->unknown=unknown||record->unknown;slot->agent=agent;
    int ready=native_owner_merge(&slot->owners,record->owners)&&native_owner_merge(&slot->owners,writers)&&native_capture_image_owners(callback,&slot->owners);
    if(origin&&env&&native_original.NewWeakGlobalRef)slot->origin=native_original.NewWeakGlobalRef(env,origin);
    if(slot->owners&&env&&native_original.NewGlobalRef){jobjectArray lease=native_tooling_writers(env,slot->owners);
        if(lease){slot->sourceLeaseComplete=!slot->unknown&&host_module_sources_complete(env,lease);
            slot->sourceLease=(jobjectArray)native_original.NewGlobalRef(env,lease);native_original.DeleteLocalRef(env,lease);}ready=ready&&slot->sourceLease;}
    slot->image=native_image(callback);
    if(slot->image&&!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,(LPCSTR)callback,&slot->pin))ready=0;
    if(!slot->image)slot->unknown=1;
    if(ready)slot->entry=agent?(void*)native_tooling_agent:native_tooling_entry(slot,wrapper,arguments);
    if(!slot->entry){if(slot->pin)host_real_free_library(slot->pin);if(slot->sourceLease)native_original.DeleteGlobalRef(env,slot->sourceLease);if(slot->origin)native_original.DeleteWeakGlobalRef(env,slot->origin);native_owner_links_free(slot->owners);free(slot);return NULL;}
    slot->admitted=1;AcquireSRWLockExclusive(&native_tooling_records);slot->next=native_tooling_slots;native_tooling_slots=slot;ReleaseSRWLockExclusive(&native_tooling_records);return slot;
}
static void native_tooling_version_retire(NativeToolingVersion *version){
    if(!version)return;
    for(unsigned i=0;i<TOOLING_COUNT;i++)native_tooling_slot_retire(version->slots[i]);
}
static void native_tooling_environment_slots_retire(NativeToolingEnv *record){
    AcquireSRWLockShared(&native_tooling_records);NativeToolingSlot *slots=native_tooling_slots;ReleaseSRWLockShared(&native_tooling_records);
    for(NativeToolingSlot *slot=slots;slot;slot=slot->next)if(slot->environment==record)native_tooling_slot_retire(slot);
}
/* closing and admission share one lock. A dispatcher that fetched our immutable
 * thunk can still enter it, but it cannot start the retired outside callback. */
static jvmtiError native_tooling_dispose_idle(NativeToolingEnv *record,int retirement){
    AcquireSRWLockExclusive(&native_tooling_records);
    if(record->disposed){ReleaseSRWLockExclusive(&native_tooling_records);return JVMTI_ERROR_INVALID_ENVIRONMENT;}
    int retiring=record->retiring||retirement;InterlockedExchange(&record->closing,1);if(retirement)record->retiring=1;
    if(record->uses||record->active){
        if(!retiring)InterlockedExchange(&record->closing,0);
        ReleaseSRWLockExclusive(&native_tooling_records);return JVMTI_ERROR_NOT_AVAILABLE;
    }
    ReleaseSRWLockExclusive(&native_tooling_records);
    jvmtiError result=record->original->DisposeEnvironment(record->api);
    /* Disposal can enter a VM safepoint. Keep the exact logical API range
     * registered through that call, then revoke it without holding a gate
     * across VM code. The VM owns its later physical reclamation. */
    if(result==JVMTI_ERROR_NONE)native_private_unregister(&record->apiRange);
    AcquireSRWLockExclusive(&native_tooling_records);
    if(result==JVMTI_ERROR_NONE){InterlockedExchange(&record->disposed,1);record->retiring=0;native_tooling_environment_queue_locked(record);}
    else if(!retiring)InterlockedExchange(&record->closing,0);
    ReleaseSRWLockExclusive(&native_tooling_records);
    if(result==JVMTI_ERROR_NONE){
        native_tooling_environment_slots_retire(record);
        native_tooling_heap_environment_release(native_tooling_jni(),record);
    }return result;
}
static int native_tooling_retirement_jni(JNIEnv **env){
    *env=NULL;if(InterlockedCompareExchange(&native_tooling_shutdown,0,0))return 0;
    if(!native_vm_original.GetEnv)return 0;
    jint status=native_vm_original.GetEnv(native_vm,(void**)env,JNI_VERSION_1_8);
    if(status==JNI_EDETACHED){JavaVMAttachArgs arguments={JNI_VERSION_1_8,"Ronova JVMTI retirement",NULL};
        if(!native_vm_original.AttachCurrentThreadAsDaemon)return 0;
        status=native_vm_original.AttachCurrentThreadAsDaemon(native_vm,(void**)env,&arguments);
    }
    if(status!=JNI_OK||!*env){*env=NULL;return 0;}
    native_thread_jni_observed(native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls),*env);return 1;
}
static DWORD WINAPI native_tooling_retirement_worker(void *argument){
    (void)argument;JNIEnv *env=NULL;
    for(;;){
        DWORD workerWait=WaitForSingleObject(native_tooling_retirement_event,
                host_thread_workers_pending()||host_tls_pending()||host_module_exits_pending()||native_tooling_definitions_pending_any()?1000:INFINITE);
        if(workerWait!=WAIT_OBJECT_0&&workerWait!=WAIT_TIMEOUT)break;
        if(InterlockedCompareExchange(&native_tooling_shutdown,0,0))break;
        int definitionsReaped=0;
        for(;;){
            if(InterlockedCompareExchange(&native_tooling_shutdown,0,0))break;
            /* A previous DLL's actual unload callback may have detached this
             * daemon. Never carry its old JNIEnv into another cleanup. */
            if(!native_tooling_retirement_jni(&env)){InterlockedIncrement(&native_tooling_failures);break;}
            host_module_releases_reap(&env);if(!env)break;
            host_thread_workers_reap(&env);if(!env)break;
            host_tls_reap(&env);if(!env)break;
            host_module_loads_reap(&env);if(!env)break;
            if(!definitionsReaped){native_tooling_definitions_reap(env);definitionsReaped=1;
                if(!native_tooling_retirement_jni(&env)){InterlockedIncrement(&native_tooling_failures);break;}}
            AcquireSRWLockExclusive(&native_tooling_records);
            NativeToolingSlot *slot=native_tooling_retired_slots;NativeToolingEnv *record=NULL;
            if(slot){native_tooling_retired_slots=slot->retireNext;slot->retireNext=NULL;slot->queued=0;}
            else if((record=native_tooling_retired_environments)!=NULL){native_tooling_retired_environments=record->retireNext;record->retireNext=NULL;record->queued=0;}
            ReleaseSRWLockExclusive(&native_tooling_records);if(!slot&&!record)break;
            if(slot){HMODULE pin=NULL;jobjectArray lease=NULL;jweak origin=NULL;NativeThread *state=native_thread();
                if(!state){InterlockedIncrement(&native_tooling_failures);continue;}
                AcquireSRWLockExclusive(&native_tooling_records);
                if(!slot->admitted&&!slot->active&&!slot->releasing){slot->releasing=1;pin=slot->pin;slot->pin=NULL;lease=slot->sourceLease;slot->sourceLease=NULL;origin=slot->origin;slot->origin=NULL;}
                ReleaseSRWLockExclusive(&native_tooling_records);
                NativeToolingScope scope={slot->owners,origin,slot->unknown,state->depth,state->tooling};
                native_thread_callback_enter(state);state->tooling=&scope;
                int released=!pin||host_real_free_library(pin);
                if(!released){state->tooling=scope.previous;
                    AcquireSRWLockExclusive(&native_tooling_records);slot->pin=pin;slot->sourceLease=lease;slot->origin=origin;slot->releasing=0;ReleaseSRWLockExclusive(&native_tooling_records);
                    native_thread_callback_leave(state);InterlockedIncrement(&native_tooling_failures);continue;}
                if(!native_tooling_retirement_jni(&env)){state->tooling=scope.previous;
                    AcquireSRWLockExclusive(&native_tooling_records);slot->sourceLease=lease;slot->origin=origin;slot->releasing=0;ReleaseSRWLockExclusive(&native_tooling_records);
                    native_thread_callback_leave(state);InterlockedIncrement(&native_tooling_failures);break;}
                state->tooling=scope.previous;
                if(lease)native_original.DeleteGlobalRef(env,lease);if(origin)native_original.DeleteWeakGlobalRef(env,origin);
                AcquireSRWLockExclusive(&native_tooling_records);slot->releasing=0;ReleaseSRWLockExclusive(&native_tooling_records);
                native_thread_callback_leave(state);
            }else {
                AcquireSRWLockExclusive(&record->mutation);
                if(!record->disposed){jvmtiError result=native_tooling_dispose_idle(record,1);
                    if(result!=JVMTI_ERROR_NONE&&result!=JVMTI_ERROR_NOT_AVAILABLE)InterlockedIncrement(&native_tooling_failures);}
                jobjectArray lease=NULL;AcquireSRWLockExclusive(&native_tooling_records);
                if(record->disposed&&!record->uses&&!record->active&&!native_tooling_extensions_unresolved_locked(record)){lease=record->sourceLease;record->sourceLease=NULL;}
                ReleaseSRWLockExclusive(&native_tooling_records);ReleaseSRWLockExclusive(&record->mutation);
                if(lease)native_original.DeleteGlobalRef(env,lease);
                native_tooling_heap_environment_release(env,record);
            }
        }
    }
    /* VM teardown supplies no public drain barrier for cached dispatchers. Keep
     * remaining entries/pins intact and do not attach or run detach code there. */
    if(env&&!InterlockedCompareExchange(&native_tooling_shutdown,0,0)&&native_vm_original.DetachCurrentThread)native_vm_original.DetachCurrentThread(native_vm);
    return 0;
}
static int native_tooling_retirement_start(void){
    if(native_tooling_retirement_event)return 1;
    native_tooling_retirement_event=CreateEventW(NULL,FALSE,FALSE,NULL);if(!native_tooling_retirement_event)return 0;
    HANDLE worker=CreateThread(NULL,0,native_tooling_retirement_worker,NULL,0,NULL);
    if(!worker){CloseHandle(native_tooling_retirement_event);native_tooling_retirement_event=NULL;return 0;}
    CloseHandle(worker);return 1;
}
static int native_tooling_owners_retired(OwnerLink *owners,int unknown){
    if(!owners||unknown)return 0;int selected=0;
    for(OwnerLink *p=owners;p;p=p->next){
        if(!InterlockedCompareExchange(&p->owner->stopped,0,0))return 0;
        if(InterlockedCompareExchange(&p->owner->producer,0,0))selected=1;
    }return selected;
}
static int native_tooling_cessation(NativeToolingEnv *record,int event){
    int allowed=1;AcquireSRWLockShared(&native_tooling_records);
    if(event<0){
        if(!native_tooling_owners_retired(record->owners,record->unknown))allowed=0;
        for(NativeToolingSlot *slot=native_tooling_slots;allowed&&slot;slot=slot->next)
            if(slot->environment==record&&slot->admitted&&!native_tooling_owners_retired(slot->owners,slot->unknown))allowed=0;
        if(native_tooling_extensions_unresolved_locked(record))allowed=0;
    }else {
        allowed=0;
        static const jvmtiEvent events[TOOLING_COUNT]={JVMTI_EVENT_VM_INIT,JVMTI_EVENT_VM_DEATH,JVMTI_EVENT_THREAD_START,JVMTI_EVENT_THREAD_END,
            JVMTI_EVENT_CLASS_FILE_LOAD_HOOK,JVMTI_EVENT_CLASS_LOAD,JVMTI_EVENT_CLASS_PREPARE,JVMTI_EVENT_VM_START,JVMTI_EVENT_EXCEPTION,JVMTI_EVENT_EXCEPTION_CATCH,
            JVMTI_EVENT_SINGLE_STEP,JVMTI_EVENT_FRAME_POP,JVMTI_EVENT_BREAKPOINT,JVMTI_EVENT_FIELD_ACCESS,JVMTI_EVENT_FIELD_MODIFICATION,
            JVMTI_EVENT_METHOD_ENTRY,JVMTI_EVENT_METHOD_EXIT,JVMTI_EVENT_NATIVE_METHOD_BIND,JVMTI_EVENT_COMPILED_METHOD_LOAD,JVMTI_EVENT_COMPILED_METHOD_UNLOAD,
            JVMTI_EVENT_DYNAMIC_CODE_GENERATED,JVMTI_EVENT_DATA_DUMP_REQUEST,JVMTI_EVENT_MONITOR_WAIT,JVMTI_EVENT_MONITOR_WAITED,JVMTI_EVENT_MONITOR_CONTENDED_ENTER,
            JVMTI_EVENT_MONITOR_CONTENDED_ENTERED,JVMTI_EVENT_RESOURCE_EXHAUSTED,JVMTI_EVENT_GARBAGE_COLLECTION_START,JVMTI_EVENT_GARBAGE_COLLECTION_FINISH,
            JVMTI_EVENT_OBJECT_FREE,JVMTI_EVENT_VM_OBJECT_ALLOC,JVMTI_EVENT_SAMPLED_OBJECT_ALLOC};
        if(record->version)for(unsigned i=0;i<TOOLING_COUNT;i++)if(event==events[i]){NativeToolingSlot *slot=record->version->slots[i];allowed=slot&&native_tooling_owners_retired(slot->owners,slot->unknown);break;}
    }
    ReleaseSRWLockShared(&native_tooling_records);return allowed;
}
static jvmtiError JNICALL native_tooling_set_callbacks(jvmtiEnv *api,const jvmtiEventCallbacks *callbacks,jint size){
    NativeToolingEnv *record=native_tooling_environment(api);if(!record)return native_tooling_restricted()?JVMTI_ERROR_NOT_AVAILABLE:JVMTI_ERROR_INVALID_ENVIRONMENT;
    TOOLING_USE(record);
    void *caller=__builtin_return_address(0);if(record->own){if(!native_tooling_internal(caller))return native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);return record->original->SetEventCallbacks(api,callbacks,size);}
    if(size<0)return JVMTI_ERROR_ILLEGAL_ARGUMENT;
    JNIEnv *env=native_tooling_jni();OwnerLink *owners=NULL;jweak origin=NULL;int unknown=0;
    if(!callbacks&&native_tooling_effect_stopped(record,env,caller)&&!native_tooling_cessation(record,-1))return native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);
    if(callbacks&&(native_tooling_effect_stopped(record,env,caller)||!native_tooling_capture(env,caller,&owners,&origin,&unknown))){native_owner_links_free(owners);return native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);}
    NativeToolingVersion *version=calloc(1,sizeof(*version));jvmtiEventCallbacks submitted;memset(&submitted,0,sizeof(submitted));
    SIZE_T copied=0;size_t length=size<(jint)sizeof(submitted)?(size_t)size:sizeof(submitted);
    int ready=version&&(!callbacks||!length||host_real_read_process_memory(GetCurrentProcess(),callbacks,&submitted,length,&copied)&&copied==length);
    int alias=0;
    if(ready){
#define TOOLING_ALIAS(NAME,COUNT) if(submitted.NAME&&native_tooling_internal((void*)submitted.NAME))alias=1;
        NATIVE_TOOLING_CALLBACKS(TOOLING_ALIAS)
#undef TOOLING_ALIAS
        if(alias)ready=0;
    }
    if(ready){
#define TOOLING_WRAP(NAME,COUNT) \
        if(submitted.NAME){NativeToolingSlot *slot=native_tooling_slot(env,record,(void*)submitted.NAME,(void*)native_tooling_##NAME,COUNT,owners,origin,unknown,0); \
            if(!slot)ready=0;else {version->slots[TOOLING_##NAME]=slot;version->callbacks.NAME=(jvmtiEvent##NAME)slot->entry;}}
        NATIVE_TOOLING_CALLBACKS(TOOLING_WRAP)
#undef TOOLING_WRAP
    }
    if(origin)native_original.DeleteWeakGlobalRef(env,origin);native_owner_links_free(owners);
    if(!ready){native_tooling_version_retire(version);free(version);return native_tooling_error(alias?JVMTI_ERROR_ACCESS_DENIED:JVMTI_ERROR_OUT_OF_MEMORY);}
    AcquireSRWLockExclusive(&record->mutation);
    jvmtiError result=InterlockedCompareExchange(&record->closing,0,0)||InterlockedCompareExchange(&record->disposed,0,0)?JVMTI_ERROR_INVALID_ENVIRONMENT:record->original->SetEventCallbacks(api,&version->callbacks,sizeof(version->callbacks));
    if(result==JVMTI_ERROR_NONE){AcquireSRWLockExclusive(&native_tooling_records);version->previous=record->version;record->version=version;ReleaseSRWLockExclusive(&native_tooling_records);native_tooling_version_retire(version->previous);}
    else {native_tooling_version_retire(version);free(version);}
    ReleaseSRWLockExclusive(&record->mutation);return result;
}
static jvmtiError JNICALL native_tooling_mode(jvmtiEnv *api,jvmtiEventMode mode,jvmtiEvent event,jthread thread,...){
    NativeToolingEnv *record=native_tooling_environment(api);if(!record)return native_tooling_restricted()?JVMTI_ERROR_NOT_AVAILABLE:JVMTI_ERROR_INVALID_ENVIRONMENT;
    TOOLING_USE(record);
    void *caller=__builtin_return_address(0);
    if(record->own&&!native_tooling_internal(caller)||native_tooling_effect_stopped(record,native_tooling_jni(),caller)
            &&(mode!=JVMTI_DISABLE||!native_tooling_cessation(record,event)))return native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);
    return record->original->SetEventNotificationMode(api,mode,event,thread);
}
static jvmtiError JNICALL native_tooling_dispose(jvmtiEnv *api){
    NativeToolingEnv *record=native_tooling_environment(api);if(!record)return native_tooling_restricted()?JVMTI_ERROR_NOT_AVAILABLE:JVMTI_ERROR_INVALID_ENVIRONMENT;
    if(record->own)return native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);
    if(native_tooling_effect_stopped(record,native_tooling_jni(),__builtin_return_address(0))&&!native_tooling_cessation(record,-1))return native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);
    AcquireSRWLockExclusive(&record->mutation);
    jvmtiError result=native_tooling_dispose_idle(record,0);
    ReleaseSRWLockExclusive(&record->mutation);return result;
}
static jvmtiError JNICALL native_tooling_add_capabilities(jvmtiEnv *api,const jvmtiCapabilities *capabilities){
    NativeToolingEnv *record=native_tooling_environment(api);if(!record)return native_tooling_restricted()?JVMTI_ERROR_NOT_AVAILABLE:JVMTI_ERROR_INVALID_ENVIRONMENT;
    TOOLING_USE(record);
    void *caller=__builtin_return_address(0);
    if(record->own&&!native_tooling_internal(caller)||native_tooling_effect_stopped(record,native_tooling_jni(),caller))return native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);
    return record->original->AddCapabilities(api,capabilities);
}
static jvmtiError JNICALL native_tooling_relinquish(jvmtiEnv *api,const jvmtiCapabilities *capabilities){
    NativeToolingEnv *record=native_tooling_environment(api);if(!record)return native_tooling_restricted()?JVMTI_ERROR_NOT_AVAILABLE:JVMTI_ERROR_INVALID_ENVIRONMENT;
    TOOLING_USE(record);
    if(record->own)return native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);
    if(native_tooling_effect_stopped(record,native_tooling_jni(),__builtin_return_address(0))&&!native_tooling_cessation(record,-1))return native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);
    return record->original->RelinquishCapabilities(api,capabilities);
}
static jvmtiError JNICALL native_tooling_set_jni(jvmtiEnv *api,const jniNativeInterface *table){
    NativeToolingEnv *record=native_tooling_environment(api);if(!record)return native_tooling_restricted()?JVMTI_ERROR_NOT_AVAILABLE:JVMTI_ERROR_INVALID_ENVIRONMENT;
    TOOLING_USE(record);
    void *caller=__builtin_return_address(0);
    if(record->own&&!native_tooling_internal(caller)||native_tooling_effect_stopped(record,native_tooling_jni(),caller))return native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);
    if(!table)return JVMTI_ERROR_NULL_POINTER;
    if(InterlockedCompareExchange(&native_guarded_ready,0,0)){
        jniNativeInterface incoming;SIZE_T copied=0;
        if(!host_real_read_process_memory(GetCurrentProcess(),table,&incoming,sizeof(incoming),&copied)||copied!=sizeof(incoming))return native_tooling_error(JVMTI_ERROR_ILLEGAL_ARGUMENT);
        const uintptr_t *original=(const uintptr_t*)&native_original,*guarded=(const uintptr_t*)&native_guarded,*proposed=(const uintptr_t*)&incoming;
        for(unsigned i=0;i<sizeof(incoming)/sizeof(uintptr_t);i++)if(guarded[i]!=original[i]&&proposed[i]!=guarded[i])return native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);
        /* The VM receives exactly the private table that passed the guard.
         * Rereading the caller's mutable table could replace a guarded slot
         * between the check above and the actual global JNI publication. */
        return record->original->SetJNIFunctionTable(api,&incoming);
    }
    return record->original->SetJNIFunctionTable(api,table);
}
#include "tooling_definitions.c"
static jvmtiError JNICALL native_tooling_generate(jvmtiEnv *api,jvmtiEvent event){
    NativeToolingEnv *record=native_tooling_environment(api);if(!record)return native_tooling_restricted()?JVMTI_ERROR_NOT_AVAILABLE:JVMTI_ERROR_INVALID_ENVIRONMENT;
    TOOLING_USE(record);
    if(record->own&&!native_tooling_internal(__builtin_return_address(0))||native_tooling_effect_stopped(record,native_tooling_jni(),__builtin_return_address(0)))return native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);
    return record->original->GenerateEvents(api,event);
}
#define COMMA ,
#define TOOLING_ENV_MUTATION(NAME,CLEARING,DECL,CALL) \
static jvmtiError JNICALL native_tooling_##NAME(jvmtiEnv *api,DECL){ \
    NativeToolingEnv *record=native_tooling_environment(api);if(!record)return native_tooling_restricted()?JVMTI_ERROR_NOT_AVAILABLE:JVMTI_ERROR_INVALID_ENVIRONMENT; \
    TOOLING_USE(record); \
    void *caller=__builtin_return_address(0); \
    if(record->own&&!native_tooling_internal(caller)||native_tooling_effect_stopped(record,native_tooling_jni(),caller) \
            &&(!(CLEARING)||!native_tooling_cessation(record,-1)))return native_tooling_error(JVMTI_ERROR_ACCESS_DENIED); \
    return record->original->NAME(api,CALL); \
}
TOOLING_ENV_MUTATION(SetNativeMethodPrefix,0,const char *prefix,prefix)
TOOLING_ENV_MUTATION(SetNativeMethodPrefixes,0,jint count COMMA char **prefixes,count COMMA prefixes)
/* These change the VM's future class search itself. They use the same actual
 * environment/caller gate as module mutations, including before Java control
 * installation; an ordinary JPLIS premain call keeps the VM's own contract. */
TOOLING_ENV_MUTATION(AddToBootstrapClassLoaderSearch,0,const char *segment,segment)
TOOLING_ENV_MUTATION(AddToSystemClassLoaderSearch,0,const char *segment,segment)
TOOLING_ENV_MUTATION(SetFieldModificationWatch,0,jclass type COMMA jfieldID field,type COMMA field)
TOOLING_ENV_MUTATION(ClearFieldModificationWatch,1,jclass type COMMA jfieldID field,type COMMA field)
TOOLING_ENV_MUTATION(SetFieldAccessWatch,0,jclass type COMMA jfieldID field,type COMMA field)
TOOLING_ENV_MUTATION(ClearFieldAccessWatch,1,jclass type COMMA jfieldID field,type COMMA field)
TOOLING_ENV_MUTATION(SetBreakpoint,0,jmethodID method COMMA jlocation location,method COMMA location)
TOOLING_ENV_MUTATION(ClearBreakpoint,1,jmethodID method COMMA jlocation location,method COMMA location)
TOOLING_ENV_MUTATION(NotifyFramePop,0,jthread thread COMMA jint depth,thread COMMA depth)
TOOLING_ENV_MUTATION(SetHeapSamplingInterval,0,jint interval,interval)
TOOLING_ENV_MUTATION(AddModuleReads,0,jobject module COMMA jobject target,module COMMA target)
TOOLING_ENV_MUTATION(AddModuleExports,0,jobject module COMMA const char *package COMMA jobject target,module COMMA package COMMA target)
TOOLING_ENV_MUTATION(AddModuleOpens,0,jobject module COMMA const char *package COMMA jobject target,module COMMA package COMMA target)
TOOLING_ENV_MUTATION(AddModuleUses,0,jobject module COMMA jclass service,module COMMA service)
TOOLING_ENV_MUTATION(AddModuleProvides,0,jobject module COMMA jclass service COMMA jclass implementation,module COMMA service COMMA implementation)
#undef TOOLING_ENV_MUTATION
#undef COMMA
static jvmtiError JNICALL native_tooling_SetEnvironmentLocalStorage(jvmtiEnv *api,const void *data){
    void *caller=__builtin_return_address(0);
    if(native_tooling_restricted()){
        NativeThread *state=(NativeThread*)TlsGetValue(native_tls);
        NativeToolingEnv *record=state->toolingCallbackEnvironment;
        if(!record||record->api!=api)return JVMTI_ERROR_INVALID_ENVIRONMENT;
        if(record->closing||record->disposed||native_tooling_shutdown)return JVMTI_ERROR_NOT_AVAILABLE;
        if(native_owner_stopped(record->owners))return JVMTI_ERROR_ACCESS_DENIED;
        uintptr_t address=(uintptr_t)caller;
        if(record->own&&(address<native_tooling_image_begin||address>=native_tooling_image_end))return JVMTI_ERROR_ACCESS_DENIED;
        for(NativeToolingScope *scope=state->tooling;scope;scope=scope->previous)
            if(native_owner_stopped(scope->owners))return JVMTI_ERROR_ACCESS_DENIED;
        /* This API is explicitly heap-callback-safe. The enclosing synchronous
         * call retains this exact environment; no JNI or lock is needed here. */
        return record->original->SetEnvironmentLocalStorage(api,data);
    }
    NativeToolingEnv *record=native_tooling_environment(api);if(!record)return JVMTI_ERROR_INVALID_ENVIRONMENT;
    TOOLING_USE(record);
    if(record->own&&!native_tooling_internal(caller)||native_tooling_effect_stopped(record,native_tooling_jni(),caller))return native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);
    return record->original->SetEnvironmentLocalStorage(api,data);
}
#include "tooling_output_gate.c"
#include "jni_output_gate.c"
#include "jni_outputs.c"
/* Reuse the actual Thread/Runnable/scheduler policy. The same frame identity
 * that proves a JNI thread call proves this JVMTI call, without Java publication
 * of a fabricated producer or a borrowed deeper controller frame. */
static jvmtiError native_tooling_thread_begin(NativeToolingEnv *record,JNIEnv *env,jthread thread,jint operation,void *caller,NativeThreadFrame *frame){
    memset(frame,0,sizeof(*frame));
    if(native_tooling_effect_stopped(record,env,caller))return native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);
    if(!native_jni_ready)return JVMTI_ERROR_NONE;
    NativeThread *state=native_thread();if(!env||!state||!native_tooling_thread_query)return native_tooling_error(JVMTI_ERROR_NOT_AVAILABLE);
    if(native_original.ExceptionCheck(env))return native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);
    jthread current=NULL;
    if(!thread){jvmtiError result=(*native_ti)->GetCurrentThread(native_ti,&current);if(result!=JVMTI_ERROR_NONE)return result;thread=current;}
    if(!thread)return JVMTI_ERROR_INVALID_THREAD;
    OwnerLink *owners=NULL;jweak origin=NULL;int unknown=0;
    int ready=native_tooling_capture(env,caller,&owners,&origin,&unknown)&&native_owner_merge(&owners,record->owners);unknown|=record->unknown;
    if(origin)native_original.DeleteWeakGlobalRef(env,origin);
    frame->operation=operation;frame->receiver=native_original.NewGlobalRef(env,thread);
    if(current)native_original.DeleteLocalRef(env,current);
    jobjectArray sources=ready?native_tooling_sources(env,owners,unknown):NULL;native_owner_links_free(owners);
    if(sources){frame->sources=(jobjectArray)native_original.NewGlobalRef(env,sources);native_original.DeleteLocalRef(env,sources);}
    ready=ready&&frame->receiver&&frame->sources&&!native_original.ExceptionCheck(env);
    if(ready){state->control++;NativeThreadFrame *previous=state->threadPermit;state->threadPermit=frame;
        frame->token=native_original.CallStaticObjectMethod(env,native_controls,native_tooling_thread_query,frame->receiver,frame->sources,operation);
        state->threadPermit=previous;state->control--;ready=!native_original.ExceptionCheck(env);
        if(ready&&frame->token){frame->skipped=native_original.IsSameObject(env,frame->token,native_tooling_deferred);
            jobject global=native_original.NewGlobalRef(env,frame->token);if(global){native_original.DeleteLocalRef(env,frame->token);frame->token=global;frame->tokenGlobal=1;}else ready=0;}
    }
    if(!ready||frame->skipped){
        /* JVMTI reports a policy refusal through its error result. A Java guard
         * raised during this query must not poison later unrelated list targets. */
        if(native_original.ExceptionCheck(env))native_original.ExceptionClear(env);
        native_thread_finish(env,state,frame,0);if(native_original.ExceptionCheck(env))native_original.ExceptionClear(env);
        return native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);
    }return JVMTI_ERROR_NONE;
}
static void native_tooling_thread_end_call(JNIEnv *env,NativeThreadFrame *frame,jvmtiError result){
    if(frame->operation)native_thread_finish(env,native_thread(),frame,result==JVMTI_ERROR_NONE);
}
typedef struct NativeToolingFrame {jmethodID method;jlocation location;int observed;} NativeToolingFrame;
static jvmtiError native_tooling_frame(JNIEnv *env,jthread thread,jint depth,void *caller,NativeToolingFrame *frame){
    memset(frame,0,sizeof(*frame));
    if(!native_jni_ready||native_tooling_internal(caller))return JVMTI_ERROR_NONE;
    if(!env||!native_tooling_frame_query||!native_tooling_protected_query)return native_tooling_error(JVMTI_ERROR_NOT_AVAILABLE);
    if(native_original.ExceptionCheck(env))return native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);
    jmethodID method=NULL;jlocation location=0;jclass declaring=NULL;
    jvmtiError result=(*native_ti)->GetFrameLocation(native_ti,thread,depth,&method,&location);
    if(result==JVMTI_ERROR_NONE)result=(*native_ti)->GetMethodDeclaringClass(native_ti,method,&declaring);
    if(result!=JVMTI_ERROR_NONE)return result;
    NativeThread *state=native_thread();if(!state){native_original.DeleteLocalRef(env,declaring);return JVMTI_ERROR_OUT_OF_MEMORY;}
    state->control++;jboolean owned=native_original.CallStaticBooleanMethod(env,native_controls,native_tooling_frame_query,declaring);
    if(!native_original.ExceptionCheck(env))owned|=native_original.CallStaticBooleanMethod(env,native_controls,native_tooling_protected_query,declaring);
    jobject module=native_original.GetModule(env,declaring);state->control--;int stopped=0;
    if(module){AcquireSRWLockShared(&native_records);for(Owner *owner=native_owners;owner;owner=owner->next)
            if(native_original.IsSameObject(env,owner->module,module)&&InterlockedCompareExchange(&owner->stopped,0,0)){stopped=1;break;}
        ReleaseSRWLockShared(&native_records);native_original.DeleteLocalRef(env,module);}
    OwnerLink *contributors=NULL;int known=0;
    int sourced=location<0||code_frame_sources(env,method,location,&contributors,&known);
    stopped|=native_owner_stopped(contributors)!=NULL;native_owner_links_free(contributors);
    native_original.DeleteLocalRef(env,declaring);
    int failed=native_original.ExceptionCheck(env);if(failed)native_original.ExceptionClear(env);
    if(owned||stopped||failed||!sourced)return native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);
    frame->method=method;frame->location=location;frame->observed=1;return JVMTI_ERROR_NONE;
}
static jvmtiError native_tooling_frame_current(jthread thread,jint depth,NativeToolingFrame *frame){
    if(!frame->observed)return JVMTI_ERROR_NONE;jmethodID method=NULL;jlocation location=0;
    jvmtiError result=(*native_ti)->GetFrameLocation(native_ti,thread,depth,&method,&location);
    if(result!=JVMTI_ERROR_NONE)return result;
    return method==frame->method&&location==frame->location?JVMTI_ERROR_NONE:native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);
}
#define COMMA ,
#define TOOLING_THREAD_EFFECT(NAME,OPERATION,FRAME_DEPTH,DECL,CALL) \
static jvmtiError JNICALL native_tooling_##NAME(jvmtiEnv *api,jthread thread DECL){ \
    NativeToolingEnv *record=native_tooling_environment(api);if(!record)return native_tooling_restricted()?JVMTI_ERROR_NOT_AVAILABLE:JVMTI_ERROR_INVALID_ENVIRONMENT; \
    TOOLING_USE(record); \
    void *caller=__builtin_return_address(0);if(record->own&&!native_tooling_internal(caller))return native_tooling_error(JVMTI_ERROR_ACCESS_DENIED); \
    JNIEnv *env=native_tooling_jni();NativeToolingFrame observed={0};jvmtiError result=(FRAME_DEPTH)>=0?native_tooling_frame(env,thread,(FRAME_DEPTH),caller,&observed):JVMTI_ERROR_NONE; \
    if(result!=JVMTI_ERROR_NONE)return result;NativeThreadFrame frame;result=native_tooling_thread_begin(record,env,thread,OPERATION,caller,&frame); \
    if(result==JVMTI_ERROR_NONE&&(FRAME_DEPTH)>=0)result=native_tooling_frame_current(thread,(FRAME_DEPTH),&observed); \
    if(result==JVMTI_ERROR_NONE)result=record->original->NAME(api,thread CALL);native_tooling_thread_end_call(env,&frame,result);return result; \
}
TOOLING_THREAD_EFFECT(SuspendThread,4,-1,,)
TOOLING_THREAD_EFFECT(ResumeThread,5,-1,,)
TOOLING_THREAD_EFFECT(StopThread,3,-1,COMMA jobject exception,COMMA exception)
TOOLING_THREAD_EFFECT(InterruptThread,2,-1,,)
TOOLING_THREAD_EFFECT(PopFrame,3,0,,)
TOOLING_THREAD_EFFECT(ForceEarlyReturnVoid,3,0,,)
TOOLING_THREAD_EFFECT(ForceEarlyReturnObject,3,0,COMMA jobject value,COMMA value)
TOOLING_THREAD_EFFECT(ForceEarlyReturnInt,3,0,COMMA jint value,COMMA value)
TOOLING_THREAD_EFFECT(ForceEarlyReturnLong,3,0,COMMA jlong value,COMMA value)
TOOLING_THREAD_EFFECT(ForceEarlyReturnFloat,3,0,COMMA jfloat value,COMMA value)
TOOLING_THREAD_EFFECT(ForceEarlyReturnDouble,3,0,COMMA jdouble value,COMMA value)
TOOLING_THREAD_EFFECT(SetLocalObject,3,depth,COMMA jint depth COMMA jint slot COMMA jobject value,COMMA depth COMMA slot COMMA value)
TOOLING_THREAD_EFFECT(SetLocalInt,3,depth,COMMA jint depth COMMA jint slot COMMA jint value,COMMA depth COMMA slot COMMA value)
TOOLING_THREAD_EFFECT(SetLocalLong,3,depth,COMMA jint depth COMMA jint slot COMMA jlong value,COMMA depth COMMA slot COMMA value)
TOOLING_THREAD_EFFECT(SetLocalFloat,3,depth,COMMA jint depth COMMA jint slot COMMA jfloat value,COMMA depth COMMA slot COMMA value)
TOOLING_THREAD_EFFECT(SetLocalDouble,3,depth,COMMA jint depth COMMA jint slot COMMA jdouble value,COMMA depth COMMA slot COMMA value)
#undef TOOLING_THREAD_EFFECT
#undef COMMA
static jvmtiError native_tooling_thread_list(jvmtiEnv *api,jint count,const jthread *threads,jvmtiError *results,int resume,void *caller){
    NativeToolingEnv *record=native_tooling_environment(api);if(!record)return native_tooling_restricted()?JVMTI_ERROR_NOT_AVAILABLE:JVMTI_ERROR_INVALID_ENVIRONMENT;
    TOOLING_USE(record);
    if(record->own&&!native_tooling_internal(caller)||native_tooling_effect_stopped(record,native_tooling_jni(),caller))return native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);
    if(count<0)return JVMTI_ERROR_ILLEGAL_ARGUMENT;if(!threads||!results)return JVMTI_ERROR_NULL_POINTER;
    if(!count)return resume?record->original->ResumeThreadList(api,count,threads,results):record->original->SuspendThreadList(api,count,threads,results);
    /* This destination is supplied by the caller. Check it before applying any
     * thread effect; our private original API is not an external write bypass. */
    NativeToolingOutputSpan destination={results,NULL,(size_t)count*sizeof(*results)};
    if(!native_tooling_output_lock(0))return native_tooling_error(JVMTI_ERROR_NOT_AVAILABLE);
    jvmtiError admission=native_tooling_output_check_locked(&destination,1);
    ReleaseSRWLockExclusive(&native_private_gate);
    if(admission!=JVMTI_ERROR_NONE)return native_tooling_error(admission);
    jthread *snapshot=(jthread*)calloc((size_t)count,sizeof(*snapshot)),*allowed=(jthread*)calloc((size_t)count,sizeof(*allowed));
    NativeThreadFrame *frames=(NativeThreadFrame*)calloc((size_t)count,sizeof(*frames));jvmtiError *effects=(jvmtiError*)calloc((size_t)count,sizeof(*effects));
    if(!snapshot||!allowed||!frames||!effects){free(snapshot);free(allowed);free(frames);free(effects);return JVMTI_ERROR_OUT_OF_MEMORY;}
    SIZE_T copied=0;size_t length=(size_t)count*sizeof(*snapshot);JNIEnv *env=native_tooling_jni();jint selected=0;
    jvmtiError result=JVMTI_ERROR_NONE;
    if(!host_real_read_process_memory(GetCurrentProcess(),threads,snapshot,length,&copied)||copied!=length)result=JVMTI_ERROR_ILLEGAL_ARGUMENT;
    if(result==JVMTI_ERROR_NONE){for(jint i=0;i<count;i++){
            effects[i]=native_tooling_thread_begin(record,env,snapshot[i],resume?5:4,caller,&frames[i]);
            if(effects[i]==JVMTI_ERROR_NONE)allowed[selected++]=snapshot[i];
        }
        jvmtiError *partial=(jvmtiError*)calloc((size_t)(selected?selected:1),sizeof(*partial));
        if(!partial){result=JVMTI_ERROR_OUT_OF_MEMORY;
            /* No VM effect ran. Do not complete an admitted frame using the
             * zero-initialized per-thread status as a fabricated success. */
            for(jint i=0;i<count;i++)if(effects[i]==JVMTI_ERROR_NONE)effects[i]=result;
        }
        else {result=selected?(resume?record->original->ResumeThreadList(api,selected,allowed,partial):record->original->SuspendThreadList(api,selected,allowed,partial)):JVMTI_ERROR_ACCESS_DENIED;
            jint at=0;for(jint i=0;i<count;i++)if(effects[i]==JVMTI_ERROR_NONE)effects[i]=result==JVMTI_ERROR_NONE?partial[at++]:result;
            NativeToolingOutputUse output={.record=record,.env=env};
            destination.source=effects;jvmtiError published=native_tooling_output_commit(&output,&destination,1);
            /* A publication refusal cannot undo an already applied thread
             * operation. Complete each frame from its actual VM status. */
            if(published!=JVMTI_ERROR_NONE)result=published;
            free(partial);}
    }
    for(jint i=0;i<count;i++)native_tooling_thread_end_call(env,&frames[i],effects[i]);
    free(snapshot);free(allowed);free(frames);free(effects);return result;
}
static jvmtiError JNICALL native_tooling_SuspendThreadList(jvmtiEnv *api,jint count,const jthread *threads,jvmtiError *results){return native_tooling_thread_list(api,count,threads,results,0,__builtin_return_address(0));}
static jvmtiError JNICALL native_tooling_ResumeThreadList(jvmtiEnv *api,jint count,const jthread *threads,jvmtiError *results){return native_tooling_thread_list(api,count,threads,results,1,__builtin_return_address(0));}
#include "tooling_heap.c"
#include "tooling_worker_lease.c"
static jvmtiError JNICALL native_tooling_run_agent(jvmtiEnv *api,jthread thread,jvmtiStartFunction callback,const void *argument,jint priority){
    NativeToolingEnv *record=native_tooling_environment(api);if(!record)return native_tooling_restricted()?JVMTI_ERROR_NOT_AVAILABLE:JVMTI_ERROR_INVALID_ENVIRONMENT;
    TOOLING_USE(record);
    JNIEnv *env=native_tooling_jni();void *caller=__builtin_return_address(0);OwnerLink *owners=NULL;jweak origin=NULL;int unknown=0;
    if(!callback)return JVMTI_ERROR_NULL_POINTER;
    if(record->own&&!native_tooling_internal(caller)||native_tooling_effect_stopped(record,env,caller)||!native_tooling_capture(env,caller,&owners,&origin,&unknown)){native_owner_links_free(owners);return native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);}
    NativeToolingSlot *slot=native_tooling_slot(env,record,(void*)callback,NULL,3,owners,origin,unknown,1);
    if(origin)native_original.DeleteWeakGlobalRef(env,origin);native_owner_links_free(owners);if(!slot)return native_tooling_error(JVMTI_ERROR_OUT_OF_MEMORY);
    NativeThreadFrame frame;jvmtiError result=native_tooling_thread_begin(record,env,thread,1,caller,&frame);
    slot->argument=argument;if(result==JVMTI_ERROR_NONE)result=record->original->RunAgentThread(api,thread,native_tooling_agent,slot,priority);
    native_tooling_thread_end_call(env,&frame,result);
    if(result!=JVMTI_ERROR_NONE)native_tooling_slot_retire(slot);return result;
}
#include "tooling_extensions.c"
#include "control_ranges.c"
#include "tooling_outputs.c"
static int native_tooling_exchange(void *volatile *slot,void *expected,void *replacement){
    MEMORY_BASIC_INFORMATION region;if(!VirtualQuery((void*)slot,&region,sizeof(region))||region.State!=MEM_COMMIT||(region.Protect&(PAGE_GUARD|PAGE_NOACCESS)))return 0;
    DWORD previous=0;if(!host_real_virtual_protect((void*)slot,sizeof(void*),PAGE_READWRITE,&previous))return 0;
    void *actual=InterlockedCompareExchangePointer(slot,replacement,expected);DWORD ignored;int restored=host_real_virtual_protect((void*)slot,sizeof(void*),previous,&ignored);
    if(!restored)InterlockedIncrement(&native_tooling_failures);return actual==expected;
}
static int native_tooling_observe(jvmtiEnv *api,JNIEnv *env,void *caller,int own){
    if(!api||!*api)return 0;
    if(!native_tooling_output_prepare())return 0;
    const struct jvmtiInterface_1_ *original=*api;HMODULE image=native_image((void*)native_vm_original.GetEnv);
    if(!image||native_image((void*)original)!=image||native_image((void*)original->GetVersionNumber)!=image)return 0;
    NativeToolingEnv *record=calloc(1,sizeof(*record));struct jvmtiInterface_1_ *shadow=(struct jvmtiInterface_1_*)VirtualAlloc(NULL,sizeof(*shadow),MEM_COMMIT|MEM_RESERVE,PAGE_READWRITE);
    if(!record||!shadow){free(record);if(shadow)host_real_virtual_free(shadow,0,MEM_RELEASE);return 0;}
    record->api=api;record->original=original;record->shadow=shadow;record->own=own;record->mutation=(SRWLOCK)SRWLOCK_INIT;
    if(!own&&!native_tooling_capture(env,caller,&record->owners,NULL,&record->unknown)){native_owner_links_free(record->owners);free(record);host_real_virtual_free(shadow,0,MEM_RELEASE);return 0;}
    if(record->owners&&env&&native_original.NewGlobalRef){jobjectArray lease=native_tooling_writers(env,record->owners);
        if(lease){record->sourceLease=(jobjectArray)native_original.NewGlobalRef(env,lease);native_original.DeleteLocalRef(env,lease);}
        if(!record->sourceLease){native_owner_links_free(record->owners);free(record);host_real_virtual_free(shadow,0,MEM_RELEASE);return 0;}}
    *shadow=*original;shadow->SetEventCallbacks=native_tooling_set_callbacks;shadow->SetEventNotificationMode=native_tooling_mode;
    shadow->DisposeEnvironment=native_tooling_dispose;shadow->AddCapabilities=native_tooling_add_capabilities;shadow->RelinquishCapabilities=native_tooling_relinquish;
    shadow->SetJNIFunctionTable=native_tooling_set_jni;shadow->RedefineClasses=native_tooling_redefine;shadow->RetransformClasses=native_tooling_retransform;
    shadow->GenerateEvents=native_tooling_generate;shadow->RunAgentThread=native_tooling_run_agent;shadow->SetExtensionEventCallback=native_tooling_extension;
    shadow->SetEnvironmentLocalStorage=native_tooling_SetEnvironmentLocalStorage;shadow->SetNativeMethodPrefix=native_tooling_SetNativeMethodPrefix;shadow->SetNativeMethodPrefixes=native_tooling_SetNativeMethodPrefixes;
    shadow->AddToBootstrapClassLoaderSearch=native_tooling_AddToBootstrapClassLoaderSearch;shadow->AddToSystemClassLoaderSearch=native_tooling_AddToSystemClassLoaderSearch;
    shadow->SetFieldModificationWatch=native_tooling_SetFieldModificationWatch;shadow->ClearFieldModificationWatch=native_tooling_ClearFieldModificationWatch;
    shadow->SetFieldAccessWatch=native_tooling_SetFieldAccessWatch;shadow->ClearFieldAccessWatch=native_tooling_ClearFieldAccessWatch;
    shadow->SetBreakpoint=native_tooling_SetBreakpoint;shadow->ClearBreakpoint=native_tooling_ClearBreakpoint;shadow->NotifyFramePop=native_tooling_NotifyFramePop;shadow->SetTag=native_tooling_SetTag;
    shadow->SetHeapSamplingInterval=native_tooling_SetHeapSamplingInterval;
    shadow->AddModuleReads=native_tooling_AddModuleReads;shadow->AddModuleExports=native_tooling_AddModuleExports;shadow->AddModuleOpens=native_tooling_AddModuleOpens;
    shadow->AddModuleUses=native_tooling_AddModuleUses;shadow->AddModuleProvides=native_tooling_AddModuleProvides;
#define TOOLING_EFFECT(NAME) shadow->NAME=native_tooling_##NAME;
    TOOLING_EFFECT(SuspendThread) TOOLING_EFFECT(ResumeThread) TOOLING_EFFECT(StopThread) TOOLING_EFFECT(InterruptThread)
    TOOLING_EFFECT(SuspendThreadList) TOOLING_EFFECT(ResumeThreadList) TOOLING_EFFECT(PopFrame)
    TOOLING_EFFECT(ForceEarlyReturnVoid) TOOLING_EFFECT(ForceEarlyReturnObject) TOOLING_EFFECT(ForceEarlyReturnInt) TOOLING_EFFECT(ForceEarlyReturnLong) TOOLING_EFFECT(ForceEarlyReturnFloat) TOOLING_EFFECT(ForceEarlyReturnDouble)
    TOOLING_EFFECT(SetLocalObject) TOOLING_EFFECT(SetLocalInt) TOOLING_EFFECT(SetLocalLong) TOOLING_EFFECT(SetLocalFloat) TOOLING_EFFECT(SetLocalDouble)
    TOOLING_EFFECT(FollowReferences) TOOLING_EFFECT(IterateThroughHeap) TOOLING_EFFECT(IterateOverObjectsReachableFromObject) TOOLING_EFFECT(IterateOverReachableObjects) TOOLING_EFFECT(IterateOverHeap) TOOLING_EFFECT(IterateOverInstancesOfClass)
#undef TOOLING_EFFECT
    native_tooling_outputs_install(shadow);
    DWORD previous;if(!host_real_virtual_protect(shadow,sizeof(*shadow),PAGE_READONLY,&previous)){if(record->sourceLease)native_original.DeleteGlobalRef(env,record->sourceLease);native_owner_links_free(record->owners);free(record);host_real_virtual_free(shadow,0,MEM_RELEASE);return 0;}
    if(!native_private_register(&record->apiRange,api,sizeof(*api))){if(record->sourceLease)native_original.DeleteGlobalRef(env,record->sourceLease);native_owner_links_free(record->owners);free(record);host_real_virtual_free(shadow,0,MEM_RELEASE);return 0;}
    /* A dispatcher which sees the installed table waits for this directory
     * lock. Publish only after the native-only exchange succeeds: snapshots
     * retain published records forever, and an unwrapped environment may be
     * disposed through its original table without notifying this directory. */
    AcquireSRWLockExclusive(&native_tooling_records);
    int installed=native_tooling_exchange((void *volatile*)api,(void*)original,shadow);
    if(installed){record->next=native_tooling_environments;__atomic_store_n(&native_tooling_environments,record,__ATOMIC_RELEASE);}
    ReleaseSRWLockExclusive(&native_tooling_records);
    if(!installed){native_private_unregister(&record->apiRange);if(record->sourceLease)native_original.DeleteGlobalRef(env,record->sourceLease);
        native_owner_links_free(record->owners);free(record);host_real_virtual_free(shadow,0,MEM_RELEASE);return 0;}
    return 1;
}
/* JNI 17 Attach is a no-op for an attached thread; Detach may send ThreadEnd
 * while the originating native routine and its OS worker are still running.
 * Preserve the original result/output and only reopen the Java cleanup cycle
 * on a successful Attach outside the real end/detach dispatch.
 * https://docs.oracle.com/en/java/javase/17/docs/specs/jni/invocation.html
 */
static JNIEnv *native_vm_current_env_observed(void){
    JNIEnv *env=NULL;jint status=native_vm_original.GetEnv(native_vm,(void**)&env,JNI_VERSION_1_8);
    if(status!=JNI_OK)return NULL; /* No attachment is created by this observation. */
    if(env)native_thread_jni_observed(native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls),env);
    else InterlockedIncrement(&native_failures);
    return env;
}
/* These observations can enter Java to capture sources. Their exception is
 * bookkeeping failure, while a Throwable already pending belongs to the
 * original invocation and must be restored with the same identity. */
static jthrowable native_vm_observation_begin(JNIEnv *env){
    if(!env)return NULL;const struct JNINativeInterface_ *api=native_original.ExceptionOccurred?&native_original:*env;
    jthrowable pending=api->ExceptionOccurred(env);if(pending)api->ExceptionClear(env);return pending;
}
static int native_vm_observation_end(JNIEnv *env,jthrowable pending){
    if(!env)return 0;const struct JNINativeInterface_ *api=native_original.ExceptionCheck?&native_original:*env;
    int failed=api->ExceptionCheck(env);if(failed)api->ExceptionClear(env);
    if(pending){api->Throw(env,pending);api->DeleteLocalRef(env,pending);}return failed;
}
static int native_vm_output_alias(void **result,const void *api){
    if(!result||!api)return 0;uintptr_t first=(uintptr_t)result;
    return sizeof(*result)-1>UINTPTR_MAX-first||native_private_overlap(first,first+sizeof(*result)-1,api,sizeof(void*));
}
static jint native_vm_output_admission(void **result,int restricted,JNIEnv *env){
    if(!result)return JNI_OK;
    /* A known current VM slot stays protected if recording it failed. */
    if(native_vm_output_alias(result,env))return JNI_ERR;
    if(!native_tooling_output_lock(restricted))return JNI_ERR;
    NativeToolingOutputSpan span={result,NULL,sizeof(*result)};
    jvmtiError status=native_tooling_output_check_locked(&span,1);
    ReleaseSRWLockExclusive(&native_private_gate);return status==JVMTI_ERROR_NONE?JNI_OK:JNI_ERR;
}
static jint native_vm_output_publish(void **result,void *value,int restricted,jint status){
    if(!result)return status;
    if(status==JNI_OK&&native_vm_output_alias(result,value))return JNI_ERR;
    NativeToolingOutputUse use={.restricted=restricted};NativeToolingOutputSpan span={result,&value,sizeof(value)};
    return native_tooling_output_commit(&use,&span,1)==JVMTI_ERROR_NONE?status:JNI_ERR;
}
static jint native_vm_attach_call(JavaVM *vm,void **result,void *arguments,int daemon){
    jint (JNICALL *original)(JavaVM*,void**,void*)=daemon?native_vm_original.AttachCurrentThreadAsDaemon:native_vm_original.AttachCurrentThread;
    if(vm!=native_vm||!original)return JNI_ERR;
    DWORD incoming=GetLastError();int restricted=native_tooling_restricted();
    JNIEnv *env=restricted?NULL:native_vm_current_env_observed();
    if(native_vm_output_admission(result,restricted,env)!=JNI_OK){SetLastError(incoming);return JNI_ERR;}
    if(restricted){jint status=native_vm_output_publish(result,NULL,1,JNI_ERR);SetLastError(incoming);return status;}
    void *value=NULL;SetLastError(incoming);
    jint status=original(vm,result?&value:NULL,arguments);DWORD error=GetLastError();
    /* Attachment is a real effect even if the caller concurrently retires its
     * output address. Never undo it with an invented Detach rollback. */
    if(status==JNI_OK&&value)native_thread_jni_observed(native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls),(JNIEnv*)value);
    status=native_vm_output_publish(result,value,0,status);SetLastError(error);return status;
}
static jint JNICALL native_vm_attach(JavaVM *vm,void **result,void *arguments){return native_vm_attach_call(vm,result,arguments,0);}
static jint JNICALL native_vm_attach_daemon(JavaVM *vm,void **result,void *arguments){return native_vm_attach_call(vm,result,arguments,1);}
static jint JNICALL native_vm_detach(JavaVM *vm){
    if(vm!=native_vm||!native_vm_original.DetachCurrentThread)return JNI_ERR;
    DWORD incoming=GetLastError();
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    if(native_tooling_restricted()){SetLastError(incoming);return JNI_ERR;}
    if(state)state->vmDetaching++;SetLastError(incoming);
    jint status=native_vm_original.DetachCurrentThread(vm);DWORD error=GetLastError();
    if(state){state->vmDetaching--;native_thread_try_release(state);}
    SetLastError(error);return status;
}
static jint JNICALL native_tooling_getenv(JavaVM *vm,void **result,jint version){
    if(vm!=native_vm||!native_vm_original.GetEnv)return JNI_ERR;
    DWORD incoming=GetLastError();int restricted=native_tooling_restricted();
    JNIEnv *env=restricted?NULL:native_vm_current_env_observed();
    if(native_vm_output_admission(result,restricted,env)!=JNI_OK){SetLastError(incoming);return JNI_ERR;}
    if(restricted){InterlockedIncrement(&native_tooling_failures);jint status=native_vm_output_publish(result,NULL,1,JNI_ERR);SetLastError(incoming);return status;}
    void *caller=__builtin_return_address(0);int tooling=(version&JVMTI_VERSION_MASK_INTERFACE_TYPE)==JVMTI_VERSION_INTERFACE_JVMTI;
    if(tooling){jthrowable pending=native_vm_observation_begin(env);int stopped=native_tooling_stopped(env,caller);
        int failed=native_vm_observation_end(env,pending);if(failed)InterlockedIncrement(&native_tooling_failures);
        if(stopped){InterlockedIncrement(&native_tooling_failures);jint status=native_vm_output_publish(result,NULL,0,JNI_ERR);SetLastError(incoming);return status;}}
    void *value=NULL;SetLastError(incoming);
    jint status=native_vm_original.GetEnv(vm,result?&value:NULL,version);DWORD error=GetLastError();
    if(status==JNI_OK&&!tooling&&value)native_thread_jni_observed(native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls),(JNIEnv*)value);
    const struct jvmtiInterface_1_ *unpublished=NULL;
    if(status==JNI_OK&&tooling&&value){jvmtiEnv *api=(jvmtiEnv*)value;
        unpublished=*api;jthrowable pending=native_vm_observation_begin(env);
        int observed=native_tooling_observe(api,env,caller,0),failed=native_vm_observation_end(env,pending);
        /* A live original environment is still the VM's successful result.
         * A failed observer records degraded coverage, never a Dispose rollback. */
        if(!observed||failed)InterlockedIncrement(&native_tooling_failures);
    }
    jint published=native_vm_output_publish(result,value,0,status);
    if(published!=JNI_OK&&status==JNI_OK&&tooling&&value){
        jthrowable pending=native_vm_observation_begin(env);
        NativeToolingEnv *record=native_tooling_environment((jvmtiEnv*)value);
        if(record){AcquireSRWLockExclusive(&record->mutation);native_tooling_dispose_idle(record,0);ReleaseSRWLockExclusive(&record->mutation);}
        else if(unpublished&&unpublished->DisposeEnvironment((jvmtiEnv*)value)!=JVMTI_ERROR_NONE)InterlockedIncrement(&native_tooling_failures);
        if(native_vm_observation_end(env,pending))InterlockedIncrement(&native_tooling_failures);
    }
    SetLastError(error);return published;
}
static HMODULE native_vm_getenv_image(JavaVM *vm){
    if(vm&&vm==native_vm&&*vm==native_vm_shadow&&native_vm_original.GetEnv)return native_image((void*)native_vm_original.GetEnv);
    return vm&&*vm?native_image((void*)(*vm)->GetEnv):NULL;
}
static HMODULE native_tooling_getbytecodes_image(jvmtiEnv *api){
    if(!api||api!=native_ti)return NULL;
    for(NativeToolingEnv *record=__atomic_load_n(&native_tooling_environments,__ATOMIC_ACQUIRE);record;record=record->next)
        if(record->api==api&&record->own&&!InterlockedCompareExchange(&record->disposed,0,0)
                &&*api==record->shadow&&record->shadow->GetBytecodes==native_tooling_output_GetBytecodes)
            return native_image((void*)record->original->GetBytecodes);
    return NULL;
}
static int native_tooling_install(JavaVM *vm,jvmtiEnv *api){
    if(native_tooling_ready)return 1;const struct JNIInvokeInterface_ *original=*vm;native_vm_original=*original;
    struct JNIInvokeInterface_ *shadow=(struct JNIInvokeInterface_*)VirtualAlloc(NULL,sizeof(*shadow),MEM_COMMIT|MEM_RESERVE,PAGE_READWRITE);if(!shadow)return 0;
    *shadow=*original;shadow->GetEnv=native_tooling_getenv;
    shadow->AttachCurrentThread=native_vm_attach;shadow->AttachCurrentThreadAsDaemon=native_vm_attach_daemon;
    shadow->DetachCurrentThread=native_vm_detach;DWORD previous;
    if(!host_real_virtual_protect(shadow,sizeof(*shadow),PAGE_READONLY,&previous)){host_real_virtual_free(shadow,0,MEM_RELEASE);return 0;}
    if(!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,(LPCSTR)native_tooling_getenv,&native_tooling_pin)){host_real_virtual_free(shadow,0,MEM_RELEASE);return 0;}
    native_tooling_image_begin=(uintptr_t)native_tooling_pin;
    const IMAGE_DOS_HEADER *toolingDos=(const IMAGE_DOS_HEADER*)native_tooling_pin;
    const IMAGE_NT_HEADERS64 *toolingNt=(const IMAGE_NT_HEADERS64*)(native_tooling_image_begin+toolingDos->e_lfanew);
    native_tooling_image_end=native_tooling_image_begin+toolingNt->OptionalHeader.SizeOfImage;
    if(!native_private_register(&native_tooling_image_range,(const void*)native_tooling_image_begin,(SIZE_T)(native_tooling_image_end-native_tooling_image_begin))
            ||!native_private_register(&native_tooling_vm_range,vm,sizeof(*vm))){host_real_free_library(native_tooling_pin);native_tooling_pin=NULL;host_real_virtual_free(shadow,0,MEM_RELEASE);return 0;}
    if(!native_tooling_observe(api,NULL,(void*)native_tooling_install,1)){host_real_free_library(native_tooling_pin);native_tooling_pin=NULL;host_real_virtual_free(shadow,0,MEM_RELEASE);return 0;}
    /* The installed environment now references this DLL even if starting the
     * retirement worker fails; retain its image rather than unmapping entries. */
    if(!native_tooling_retirement_start()){InterlockedIncrement(&native_tooling_failures);return 0;}
    native_vm_shadow=shadow;
    if(!native_tooling_exchange((void *volatile*)vm,(void*)original,shadow)){InterlockedIncrement(&native_tooling_failures);return 0;}
    InterlockedExchange(&native_tooling_ready,1);return 1;
}
static void native_tooling_retire(JNIEnv *env,jobject module){
    (void)module;
    AcquireSRWLockShared(&native_tooling_records);NativeToolingEnv *records=native_tooling_environments;ReleaseSRWLockShared(&native_tooling_records);
    for(NativeToolingEnv *record=records;record;record=record->next){
        if(record->own||InterlockedCompareExchange(&record->disposed,0,0))continue;
        AcquireSRWLockExclusive(&record->mutation);
        if(record->disposed){ReleaseSRWLockExclusive(&record->mutation);continue;}
        if(record->closing){AcquireSRWLockExclusive(&native_tooling_records);native_tooling_environment_queue_locked(record);ReleaseSRWLockExclusive(&native_tooling_records);ReleaseSRWLockExclusive(&record->mutation);continue;}
        NativeToolingVersion *current=record->version;int changed=0;
        jvmtiEventCallbacks callbacks=current?current->callbacks:(jvmtiEventCallbacks){0};
        if(current){
#define TOOLING_REMOVE(NAME,COUNT) if(current->slots[TOOLING_##NAME]&&native_owner_stopped(current->slots[TOOLING_##NAME]->owners)){ \
                native_tooling_slot_retire(current->slots[TOOLING_##NAME]);callbacks.NAME=NULL;changed=1;}
            NATIVE_TOOLING_CALLBACKS(TOOLING_REMOVE)
#undef TOOLING_REMOVE
        }
        if(changed){jvmtiError result=record->original->SetEventCallbacks(record->api,&callbacks,sizeof(callbacks));
            if(result!=JVMTI_ERROR_NONE)InterlockedIncrement(&native_tooling_failures);
            else {AcquireSRWLockExclusive(&native_tooling_records);current->callbacks=callbacks;ReleaseSRWLockExclusive(&native_tooling_records);}}
        native_tooling_extensions_retire_stopped(record);
        if(native_tooling_cessation(record,-1)){
            jvmtiError result=native_tooling_dispose_idle(record,1);
            if(result!=JVMTI_ERROR_NONE&&result!=JVMTI_ERROR_NOT_AVAILABLE)InterlockedIncrement(&native_tooling_failures);
        }
        ReleaseSRWLockExclusive(&record->mutation);
    }
    AcquireSRWLockShared(&native_tooling_records);NativeToolingSlot *slots=native_tooling_slots;ReleaseSRWLockShared(&native_tooling_records);
    for(NativeToolingSlot *slot=slots;slot;slot=slot->next)if(native_owner_stopped(slot->owners))native_tooling_slot_retire(slot);(void)env;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_jvmtiBoundary0(JNIEnv *env,jclass type,jobject declaration,jobject loader,jstring name,jclass actual,jobject domain,jbyteArray original,jbyteArray bytes,jobjectArray writers,jboolean changed){
    NativeThread *state=native_thread();NativeToolingImage *proof=state?state->toolingImage:NULL;
    if(!proof||proof->phase!=1||!native_original.IsSameObject(env,type,native_controller)||proof->changed!=changed
            ||!native_original.IsSameObject(env,proof->loader,loader)||!native_original.IsSameObject(env,proof->name,name)
            ||!native_original.IsSameObject(env,proof->actual,actual)||!native_original.IsSameObject(env,proof->domain,domain)
            ||!native_original.IsSameObject(env,proof->declaration,declaration)||!native_original.IsSameObject(env,proof->original,original)
            ||!native_original.IsSameObject(env,proof->bytes,bytes)||!native_original.IsSameObject(env,proof->writers,writers))return JNI_FALSE;
    proof->phase=2;return JNI_TRUE;
}
JNIEXPORT jlongArray JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_jvmtiState0(JNIEnv *env,jclass type,jobject module){
    if(!native_controller||!native_original.IsSameObject(env,type,native_controller)||!native_original.CallStaticBooleanMethod(env,native_controller,native_control_gate)){native_refuse(env,"NATIVE_CONTROL_AGENT_REQUIRED");return NULL;}
    AcquireSRWLockShared(&native_tooling_records);NativeToolingSlot *slots=native_tooling_slots;ReleaseSRWLockShared(&native_tooling_records);
    for(NativeToolingSlot *slot=slots;slot;slot=slot->next)if(!slot->admitted&&!slot->active)native_tooling_slot_retire(slot);
    jlong values[8]={0};values[0]=native_tooling_ready&&native_vm&&*native_vm==native_vm_shadow;values[5]=native_tooling_failures;
    AcquireSRWLockShared(&native_tooling_records);
    for(NativeToolingEnv *record=native_tooling_environments;record;record=record->next){
        if(record->own){values[6]=!record->disposed&&*record->api==record->shadow;continue;}
        int match=native_owner_matches(env,record->owners,module);if(match){if(record->disposed)values[4]++;if(!record->disposed||record->sourceLease)values[1]++;}
        for(NativeToolingExtension *p=record->extensions;p;p=p->next)if(!p->slot&&p->unknown&&native_owner_matches(env,p->owners,module))values[7]++;
    }
    for(NativeToolingSlot *slot=native_tooling_slots;slot;slot=slot->next){
        if(native_owner_matches(env,slot->owners,module)){int retained=slot->pin||slot->sourceLease||slot->origin||slot->releasing;
            values[2]+=InterlockedCompareExchange(&slot->admitted,0,0)!=0||retained;values[3]+=InterlockedCompareExchange(&slot->active,0,0);
            if(slot->unknown&&(slot->admitted||slot->active||retained))values[7]++;}
    }ReleaseSRWLockShared(&native_tooling_records);
    jlongArray result=native_original.NewLongArray(env,8);if(result)native_original.SetLongArrayRegion(env,result,0,8,values);return result;
}
JNIEXPORT void JNICALL Agent_OnUnload(JavaVM *vm){
    /* The DLL and immutable thunks remain pinned: a VM dispatcher may already
     * have fetched one. No public JVMTI call proves those cached entries drained. */
    InterlockedExchange(&native_tooling_shutdown,1);InterlockedExchange(&native_tooling_ready,0);
    if(native_tooling_retirement_event)SetEvent(native_tooling_retirement_event);
    if(vm==native_vm&&native_vm_shadow)native_tooling_exchange((void *volatile*)vm,(void*)native_vm_shadow,(void*)&native_vm_original);
    AcquireSRWLockShared(&native_tooling_records);NativeToolingSlot *slots=native_tooling_slots;ReleaseSRWLockShared(&native_tooling_records);
    for(NativeToolingSlot *slot=slots;slot;slot=slot->next)native_tooling_slot_retire(slot);
}

static jint native_initialize(JavaVM *vm){
    if(native_events)return JNI_OK;
    /* A failed OnLoad may already have published the own JVMTI table or VM
     * dispatcher. Never reacquire/rewrap it on a later JNI_OnLoad handoff. */
    if(InterlockedCompareExchange(&native_initialization_attempted,1,0))return JNI_ERR;
    if(native_vm&&native_vm!=vm)return JNI_ERR;
    if(!native_ti&&(*vm)->GetEnv(vm,(void**)&native_ti,JVMTI_VERSION_1_2)!=JNI_OK){InterlockedExchange(&native_initialization_attempted,0);return JNI_ERR;}native_vm=vm;
    if(native_tls==TLS_OUT_OF_INDEXES)native_tls=TlsAlloc();
    if(native_tls==TLS_OUT_OF_INDEXES){InterlockedExchange(&native_initialization_attempted,0);return JNI_ERR;}
    jvmtiCapabilities caps;memset(&caps,0,sizeof(caps));caps.can_generate_native_method_bind_events=1;caps.can_generate_all_class_hook_events=1;caps.can_retransform_classes=1;
    if((*native_ti)->AddCapabilities(native_ti,&caps)!=JVMTI_ERROR_NONE){InterlockedExchange(&native_initialization_attempted,0);return JNI_ERR;}
    if(!native_tooling_install(vm,native_ti))return JNI_ERR;
    jvmtiEventCallbacks callbacks;memset(&callbacks,0,sizeof(callbacks));callbacks.NativeMethodBind=native_binding;
    callbacks.ThreadStart=native_thread_start;callbacks.ThreadEnd=native_thread_end;
    callbacks.ClassFileLoadHook=native_tooling_own_image;callbacks.ClassPrepare=code_class_prepared;callbacks.FramePop=code_frame_popped;callbacks.SingleStep=code_retirement_step;
    if((*native_ti)->SetEventCallbacks(native_ti,&callbacks,sizeof(callbacks))!=JVMTI_ERROR_NONE
            ||(*native_ti)->SetEventNotificationMode(native_ti,JVMTI_ENABLE,JVMTI_EVENT_NATIVE_METHOD_BIND,NULL)!=JVMTI_ERROR_NONE
            ||(*native_ti)->SetEventNotificationMode(native_ti,JVMTI_ENABLE,JVMTI_EVENT_THREAD_START,NULL)!=JVMTI_ERROR_NONE
            ||(*native_ti)->SetEventNotificationMode(native_ti,JVMTI_ENABLE,JVMTI_EVENT_THREAD_END,NULL)!=JVMTI_ERROR_NONE
            ||(*native_ti)->SetEventNotificationMode(native_ti,JVMTI_ENABLE,JVMTI_EVENT_CLASS_PREPARE,NULL)!=JVMTI_ERROR_NONE)return JNI_ERR;
    if(native_tls==TLS_OUT_OF_INDEXES)native_tls=TlsAlloc();if(native_tls==TLS_OUT_OF_INDEXES)return JNI_ERR;
    code_initialize();InterlockedExchange(&native_events,1);return JNI_OK;
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
            ||!native_bootstrap_class(env,tasks,"Ldev/ronova/pro/bootstrap/ControlBridge;")
            ||!native_bootstrap_class(env,definitions,"Ldev/ronova/pro/bootstrap/DefinitionBridge;")
            ||native_controller&&!(*env)->IsSameObject(env,native_controller,type)){native_refuse(env,"ACTUAL_NATIVE_CONTROLLER_REQUIRED");return JNI_FALSE;}
    jmethodID control=(*env)->GetStaticMethodID(env,type,"controlCaller","()Z");if(!control||!(*env)->CallStaticBooleanMethod(env,type,control)){native_refuse(env,"NATIVE_CONTROL_AGENT_REQUIRED");return JNI_FALSE;}
    /* A published JNI table is only one stage. Unsafe/arraycopy installation
     * may have failed after that publication; a repeated handoff must retain
     * that failure rather than report a complete boundary. */
    if(native_jni_ready)return native_boundary_ready?JNI_TRUE:JNI_FALSE;
    if(!native_events||InterlockedCompareExchange(&native_handoff_attempted,0,0))return JNI_FALSE;
    struct JNINativeInterface_ *table=NULL;jvmtiError acquired=(*native_ti)->GetJNIFunctionTable(native_ti,&table);
    if(acquired!=JVMTI_ERROR_NONE||!table){
        // Only this allocation failure precedes all controller preparation,
        // native registration and JNI table publication in this handoff.
        if(acquired==JVMTI_ERROR_OUT_OF_MEMORY){jfieldID retry=(*env)->GetStaticFieldID(env,type,"nativeHandoffRetryable","Z");
            if(retry)(*env)->SetStaticBooleanField(env,type,retry,JNI_TRUE);}
        return JNI_FALSE;
    }
    if(InterlockedCompareExchange(&native_handoff_attempted,1,0)){(*native_ti)->Deallocate(native_ti,(unsigned char*)table);return JNI_FALSE;}
    native_original=*table;
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
    if(!task||!definition||!native_library_gate||!native_owner_gate||!native_field_gate||!native_module_query||!native_return_query||!native_image_query||!native_defined_query||!native_after_query||!native_created_query||!native_array_query||!native_array_policy||!native_pin_query||!native_registration_query||!native_registration_target||!native_object_module||!native_network_sources||!native_process_sources||!native_mutation_field||!native_mutation_array||!native_mutation_end){(*native_ti)->Deallocate(native_ti,(unsigned char*)table);return JNI_FALSE;}native_controls=task;native_definitions=definition;
    native_writer_select=native_original.GetStaticMethodID(env,task,"controlWriterFrame","(Ljava/lang/Class;Ljava/lang/String;)Z");
    native_writer_raw_select=native_original.GetStaticMethodID(env,task,"rawMemoryWriterFrame","(Ljava/lang/Class;Ljava/lang/String;)Z");
    if(!native_writer_select||!native_writer_raw_select){(*native_ti)->Deallocate(native_ti,(unsigned char*)table);return JNI_FALSE;}
    if(!code_prepare(env)||!native_unsafe_prepare(env)||!native_file_prepare(env)||!native_thread_prepare(env)||!native_buffer_prepare(env)||!native_arraycopy_prepare(env)){(*native_ti)->Deallocate(native_ti,(unsigned char*)table);return JNI_FALSE;}
    native_tooling_thread_query=native_original.GetStaticMethodID(env,tasks,"nativeThreadOperation","(Ljava/lang/Thread;[Ljava/lang/Module;I)Ljava/lang/Object;");
    native_tooling_frame_query=native_original.GetStaticMethodID(env,tasks,"ownState","(Ljava/lang/Class;)Z");
    native_tooling_protected_query=native_original.GetStaticMethodID(env,tasks,"protectedModClass","(Ljava/lang/Class;)Z");
    native_tooling_heap_target=native_original.GetStaticMethodID(env,tasks,"nativeHeapTargetAllowed","(Ljava/lang/Object;[Ljava/lang/Module;)Z");
    jclass toolingBoolean=native_original.FindClass(env,"java/lang/Boolean");jfieldID toolingFalse=toolingBoolean?native_original.GetStaticFieldID(env,toolingBoolean,"FALSE","Ljava/lang/Boolean;"):NULL;
    jobject toolingDeferred=toolingFalse?native_original.GetStaticObjectField(env,toolingBoolean,toolingFalse):NULL;
    native_tooling_deferred=toolingDeferred?native_original.NewGlobalRef(env,toolingDeferred):NULL;
    if(toolingDeferred)native_original.DeleteLocalRef(env,toolingDeferred);if(toolingBoolean)native_original.DeleteLocalRef(env,toolingBoolean);
    native_tooling_image_query=native_original.GetStaticMethodID(env,type,"jvmtiImage","(Ljava/lang/Module;Ljava/lang/ClassLoader;Ljava/lang/String;Ljava/lang/Class;Ljava/security/ProtectionDomain;[B[B[Ljava/lang/Module;Z)[B");
    native_tooling_candidate_query=native_original.GetStaticMethodID(env,type,"jvmtiImageCandidate","(Ljava/lang/Module;Ljava/lang/ClassLoader;Ljava/lang/String;Ljava/lang/Class;[Ljava/lang/Module;)Z");
    native_tooling_definitions_query=native_original.GetStaticMethodID(env,type,"jvmtiDefinitionsChanged","([Ljava/lang/Class;)Z");
    if(!native_tooling_thread_query||!native_tooling_frame_query||!native_tooling_protected_query||!native_tooling_heap_target||!native_tooling_deferred||!native_tooling_image_query||!native_tooling_candidate_query||!native_tooling_definitions_query||!native_bind_controller(env,type)
            ||(*native_ti)->SetEventNotificationMode(native_ti,JVMTI_ENABLE,JVMTI_EVENT_CLASS_FILE_LOAD_HOOK,NULL)!=JVMTI_ERROR_NONE){(*native_ti)->Deallocate(native_ti,(unsigned char*)table);return JNI_FALSE;}
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
    table->GetDirectBufferAddress=native_buffer_address;table->NewDirectByteBuffer=native_buffer_new;
    table->ExceptionDescribe=native_describe;
    table->RegisterNatives=native_register;table->UnregisterNatives=native_unregister;
    table->FindClass=native_find;table->GetMethodID=native_lookup_GetMethodID;table->GetStaticMethodID=native_lookup_GetStaticMethodID;table->GetFieldID=native_lookup_GetFieldID;table->GetStaticFieldID=native_lookup_GetStaticFieldID;
    table->DefineClass=native_define;native_jni_outputs_install(table);native_guarded=*table;InterlockedExchange(&native_guarded_ready,1);jvmtiError result=(*native_ti)->SetJNIFunctionTable(native_ti,table);(*native_ti)->Deallocate(native_ti,(unsigned char*)table);InterlockedExchange(&native_jni_ready,result==JVMTI_ERROR_NONE);
    if(result==JVMTI_ERROR_NONE)InterlockedExchange(&native_library_present,native_library_install(env));
    if(result==JVMTI_ERROR_NONE&&!native_unsafe_install(env))return JNI_FALSE;
    if(result==JVMTI_ERROR_NONE&&!native_arraycopy_install(env))return JNI_FALSE;
    if(result!=JVMTI_ERROR_NONE)return JNI_FALSE;
    InterlockedExchange(&native_boundary_ready,1);return JNI_TRUE;
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_module0(JNIEnv *env,jclass type,jobject module,jboolean stopped){(void)type;if(!native_controller||!native_original.CallStaticBooleanMethod(env,native_controller,native_control_gate)){native_refuse(env,"NATIVE_CONTROL_AGENT_REQUIRED");return;}AcquireSRWLockExclusive(&native_records);Owner *owner=native_owner(env,module);if(owner){InterlockedExchange(&owner->producer,1);if(stopped)InterlockedExchange(&owner->stopped,1);}if(!owner)InterlockedIncrement(&native_failures);ReleaseSRWLockExclusive(&native_records);if(stopped)native_tooling_retire(env,module);}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_coreStateOwner0(JNIEnv *env,jclass type,jclass actual){
    DWORD incoming=GetLastError();int crt=errno;unsigned long dos=_doserrno;jboolean result=JNI_FALSE;
    jobject module=NULL;
    if(native_jni_ready&&native_controller&&actual&&!native_original.ExceptionCheck(env)
            &&native_original.IsSameObject(env,type,native_controller)){
        jmethodID gate=native_original.GetStaticMethodID(env,native_controller,"coreStateOwnerCaller","(Ljava/lang/Class;)Z");
        if(gate&&native_original.CallStaticBooleanMethod(env,native_controller,gate,actual)&&!native_original.ExceptionCheck(env)){
            /* The VM supplies this authenticated class's actual module. This
             * does not invoke module0 or assign the Core a producer identity. */
            module=native_original.GetModule(env,actual);
            if(module&&!native_original.ExceptionCheck(env)){
                AcquireSRWLockExclusive(&native_records);Owner *owner=native_owner(env,module);
                if(owner&&!InterlockedCompareExchange(&owner->stopped,0,0)){
                    InterlockedExchange(&owner->coreState,1);result=JNI_TRUE;
                }
                ReleaseSRWLockExclusive(&native_records);
            }
        }
    }
    if(module)native_original.DeleteLocalRef(env,module);
    _doserrno=dos;errno=crt;SetLastError(incoming);return result;
}
#include "sources.c"
#include "code.c"
#include "heap.c"
#include "unsafe.c"
#include "methods.c"
#include "file.c"
#include "thread.c"
#include "buffer.c"
#include "memory.c"
#include "host_state_sources.c"
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

/* Cache immutable frame-selection facts, not controller authorization. The
 * declaring Class identity is compared even if a jmethodID is later reused.
 * Each operation still reads its actual live stack and authorizes its writer
 * against the current Java policy. Weak classes permit ordinary unloading. */
typedef struct ControlWriterRule {
    jmethodID method;jweak declaring;jboolean selected[2];struct ControlWriterRule *next;
} ControlWriterRule;
static SRWLOCK native_writer_rules_lock=SRWLOCK_INIT;
static ControlWriterRule *native_writer_rules[257];
static int native_writer_frame(JNIEnv *env,jmethodID actual,jclass declaring,int raw,jboolean *selected){
    unsigned bucket=(unsigned)(((uintptr_t)actual>>3)%257);
    AcquireSRWLockShared(&native_writer_rules_lock);
    for(ControlWriterRule *rule=native_writer_rules[bucket];rule;rule=rule->next)
        if(rule->method==actual&&native_original.IsSameObject(env,rule->declaring,declaring)){
            *selected=rule->selected[raw];ReleaseSRWLockShared(&native_writer_rules_lock);return 1;
        }
    ReleaseSRWLockShared(&native_writer_rules_lock);
    char *signature=NULL,*name=NULL;
    jvmtiError error=(*native_ti)->GetClassSignature(native_ti,declaring,&signature,NULL);
    if(error!=JVMTI_ERROR_NONE||!signature){if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);native_refuse(env,"ACTUAL_CONTROL_WRITER_FRAME_UNAVAILABLE");return 0;}
    int platform=!strncmp(signature,"Ljava/",6)||!strncmp(signature,"Ljdk/",5)||!strncmp(signature,"Lsun/",5);
    (*native_ti)->Deallocate(native_ti,(unsigned char*)signature);
    jboolean controlSelected=JNI_FALSE;
    if(!platform){
        error=(*native_ti)->GetMethodName(native_ti,actual,&name,NULL,NULL);
        if(error!=JVMTI_ERROR_NONE||!name){if(name)(*native_ti)->Deallocate(native_ti,(unsigned char*)name);native_refuse(env,"ACTUAL_CONTROL_WRITER_FRAME_UNAVAILABLE");return 0;}
        jstring method=native_original.NewStringUTF(env,name);(*native_ti)->Deallocate(native_ti,(unsigned char*)name);
        if(!method)return 0;
        controlSelected=native_original.CallStaticBooleanMethod(env,native_controls,native_writer_select,declaring,method);
        native_original.DeleteLocalRef(env,method);if(native_original.ExceptionCheck(env))return 0;
    }
    jboolean rawSelected=native_original.CallStaticBooleanMethod(env,native_controls,native_writer_raw_select,declaring,NULL);
    if(native_original.ExceptionCheck(env))return 0;
    *selected=raw?rawSelected:controlSelected;
    ControlWriterRule *added=calloc(1,sizeof(ControlWriterRule));
    if(!added)return 1; // An unavailable cache does not change the live decision.
    added->method=actual;added->selected[0]=controlSelected;added->selected[1]=rawSelected;added->declaring=native_original.NewWeakGlobalRef(env,declaring);
    if(!added->declaring){free(added);return !native_original.ExceptionCheck(env);}
    AcquireSRWLockExclusive(&native_writer_rules_lock);
    ControlWriterRule **cursor=&native_writer_rules[bucket];
    while(*cursor){
        ControlWriterRule *rule=*cursor;
        if(native_original.IsSameObject(env,rule->declaring,NULL)){
            *cursor=rule->next;native_original.DeleteWeakGlobalRef(env,rule->declaring);free(rule);continue;
        }
        if(rule->method==actual&&native_original.IsSameObject(env,rule->declaring,declaring)){
            ReleaseSRWLockExclusive(&native_writer_rules_lock);native_original.DeleteWeakGlobalRef(env,added->declaring);free(added);return 1;
        }
        cursor=&rule->next;
    }
    added->next=native_writer_rules[bucket];native_writer_rules[bucket]=added;
    ReleaseSRWLockExclusive(&native_writer_rules_lock);return 1;
}
/* Chunks preserve arbitrary depth and never skip a nearer foreign writer. */
static jclass native_live_writer(JNIEnv *env,jclass type,int raw){
    (void)type;
    if(!native_ti||!native_controls||!native_writer_select||!native_writer_raw_select){native_refuse(env,"ACTUAL_CONTROL_WRITER_STACK_UNAVAILABLE");return NULL;}
    jint depth=0;
    for(;;){
        jvmtiFrameInfo frames[16];jint count=0;
        jvmtiError error=(*native_ti)->GetStackTrace(native_ti,NULL,depth,16,frames,&count);
        if(error==JVMTI_ERROR_ILLEGAL_ARGUMENT&&depth>0)return NULL;
        if(error!=JVMTI_ERROR_NONE){native_refuse(env,"ACTUAL_CONTROL_WRITER_STACK_UNAVAILABLE");return NULL;}
        for(jint at=0;at<count;at++){
            jclass declaring=NULL;jboolean selected=JNI_FALSE;
            if((*native_ti)->GetMethodDeclaringClass(native_ti,frames[at].method,&declaring)!=JVMTI_ERROR_NONE||!declaring){native_refuse(env,"ACTUAL_CONTROL_WRITER_FRAME_UNAVAILABLE");return NULL;}
            if(!native_writer_frame(env,frames[at].method,declaring,raw,&selected)){native_original.DeleteLocalRef(env,declaring);return NULL;}
            if(selected)return declaring;
            native_original.DeleteLocalRef(env,declaring);
        }
        if(count<16)return NULL;
        depth+=count;
    }
}
JNIEXPORT jclass JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_controlWriter0(JNIEnv *env,jclass type){return native_live_writer(env,type,0);}
JNIEXPORT jclass JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_rawMemoryWriter0(JNIEnv *env,jclass type){return native_live_writer(env,type,1);}

JNIEXPORT jint JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_networkBoundaryDepth0(JNIEnv *env,jclass type,jclass boundary,jstring boundaryMethod){
    NativeThread *state=native_thread();
    if(!state||!native_jni_ready||!native_ti||!native_controller||!boundary||!boundaryMethod
            ||!native_original.IsSameObject(env,type,native_controller)||native_original.ExceptionCheck(env))return 0;
    state->control++;
    const char *wanted=native_original.GetStringUTFChars(env,boundaryMethod,NULL);jint total=0,result=0;
    int valid=wanted&&!native_original.ExceptionCheck(env)&&(*native_ti)->GetFrameCount(native_ti,NULL,&total)==JVMTI_ERROR_NONE;
    for(jint depth=0;valid&&!result;){
        jvmtiFrameInfo frames[16];jint count=0;
        jvmtiError error=(*native_ti)->GetStackTrace(native_ti,NULL,depth,16,frames,&count);
        if(error!=JVMTI_ERROR_NONE){valid=0;break;}
        for(jint at=0;at<count&&valid&&!result;at++){
            jclass declaring=NULL;char *name=NULL;
            error=(*native_ti)->GetMethodDeclaringClass(native_ti,frames[at].method,&declaring);
            if(error!=JVMTI_ERROR_NONE||!declaring)valid=0;
            if(valid&&native_original.IsSameObject(env,declaring,boundary)){
                error=(*native_ti)->GetMethodName(native_ti,frames[at].method,&name,NULL,NULL);
                if(error!=JVMTI_ERROR_NONE||!name)valid=0;
                else if(!strcmp(name,wanted)){result=total-depth-at;if(result<=0)valid=0;}
            }
            if(name)(*native_ti)->Deallocate(native_ti,(unsigned char*)name);
            if(declaring)native_original.DeleteLocalRef(env,declaring);
            if(native_original.ExceptionCheck(env))valid=0;
        }
        if(count<16)break;if(depth>INT32_MAX-count){valid=0;break;}depth+=count;
    }
    if(wanted)native_original.ReleaseStringUTFChars(env,boundaryMethod,wanted);
    state->control--;return valid&&!native_original.ExceptionCheck(env)?result:0;
}

/* This reader establishes only a real call path. It never authorizes a field,
 * target or value, and its internal JNI bookkeeping does not supply a verdict. */
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_networkCleanupBoundary0(JNIEnv *env,jclass type,jclass boundary,jstring boundaryMethod,jint phase,jint bottomDepth){
    NativeThread *state=native_thread();
    if(!state||!native_jni_ready||!native_ti||!native_controller||!boundary||!boundaryMethod||bottomDepth<=0
            ||!native_original.IsSameObject(env,type,native_controller)||native_original.ExceptionCheck(env))return JNI_FALSE;
    state->control++;
    jfieldID stoppedField=native_original.GetStaticFieldID(env,native_controls,"stoppedModules","[Ljava/lang/Module;");
    jobjectArray policy=stoppedField&&!native_original.ExceptionCheck(env)
            ?(jobjectArray)native_original.GetStaticObjectField(env,native_controls,stoppedField):NULL;
    // The Java caller is already NetworkCleanup. Resolve its trusted callback
    // here rather than initializing a new helper during controller installation.
    jclass helper=policy&&!native_original.ExceptionCheck(env)?native_original.FindClass(env,"dev/ronova/pro/bootstrap/NetworkCleanup"):NULL;
    jmethodID query=helper&&native_bootstrap_class(env,helper,"Ldev/ronova/pro/bootstrap/NetworkCleanup;")
            ?native_original.GetStaticMethodID(env,helper,"frame","(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/String;I)I"):NULL;
    jint total=0;
    int valid=policy&&helper&&query&&!native_original.ExceptionCheck(env)
            &&(*native_ti)->GetFrameCount(native_ti,NULL,&total)==JVMTI_ERROR_NONE,matched=0;
    for(jint depth=0;valid&&!matched;){
        jvmtiFrameInfo frames[16];jint count=0;
        jvmtiError error=(*native_ti)->GetStackTrace(native_ti,NULL,depth,16,frames,&count);
        if(error!=JVMTI_ERROR_NONE){valid=0;break;}
        for(jint at=0;at<count&&valid&&!matched;at++){
            jclass declaring=NULL;jstring method=NULL,descriptor=NULL;char *name=NULL,*signature=NULL;
            error=(*native_ti)->GetMethodDeclaringClass(native_ti,frames[at].method,&declaring);
            if(error!=JVMTI_ERROR_NONE||!declaring)valid=0;
            if(valid){
                error=(*native_ti)->GetMethodName(native_ti,frames[at].method,&name,&signature,NULL);
                if(error!=JVMTI_ERROR_NONE||!name||!signature)valid=0;
            }
            if(valid){method=native_original.NewStringUTF(env,name);descriptor=native_original.NewStringUTF(env,signature);if(!method||!descriptor)valid=0;}
            if(valid&&!native_original.ExceptionCheck(env)){
                jint decision=native_original.CallStaticIntMethod(env,helper,query,declaring,method,descriptor,boundary,boundaryMethod,phase);
                if(native_original.ExceptionCheck(env)||decision!=1&&decision!=2)valid=0;
                else {
                    // A framework name does not make an overwritten instruction
                    // a framework writer. Read this actual method/BCI/version's
                    // contributors only; the outer rejected-write source stays
                    // in its original scope and is not merged into this decision.
                    if(frames[at].location>=0&&code_method_relevant(env,frames[at].method,NULL)){
                        OwnerLink *owners=NULL;int known=0,unknown=0;
                        valid=code_available&&code_frame_sources_query(env,frames[at].method,frames[at].location,&owners,&known,&unknown);
                        if(valid&&(unknown||code_source_query_owners_stopped(env,owners,policy)))valid=0;
                        code_links_free(owners);
                    }
                    if(valid&&!native_original.ExceptionCheck(env)&&decision==2){if(total-depth-at==bottomDepth)matched=1;else valid=0;}
                }
            }else valid=0;
            if(name)(*native_ti)->Deallocate(native_ti,(unsigned char*)name);
            if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);
            if(method)native_original.DeleteLocalRef(env,method);if(descriptor)native_original.DeleteLocalRef(env,descriptor);
            if(declaring)native_original.DeleteLocalRef(env,declaring);
        }
        if(count<16)break;if(depth>INT32_MAX-count){valid=0;break;}depth+=count;
    }
    if(helper)native_original.DeleteLocalRef(env,helper);
    if(policy)native_original.DeleteLocalRef(env,policy);
    state->control--;return valid&&matched&&!native_original.ExceptionCheck(env)?JNI_TRUE:JNI_FALSE;
}

/* These metadata readers never resolve a Java reflection cache or call a Java
 * accessor. A name that has more than one VM declaration is not an exact key. */
static jfieldID native_network_declared_field(JNIEnv *env,jclass declaring,const char *wanted,char **signature,jint *modifiers){
    jint count=0;jfieldID *fields=NULL,selected=NULL;int valid=1;
    *signature=NULL;*modifiers=0;
    if((*native_ti)->GetClassFields(native_ti,declaring,&count,&fields)!=JVMTI_ERROR_NONE||count<0)valid=0;
    for(jint at=0;valid&&at<count;at++){
        char *name=NULL,*descriptor=NULL;jclass owner=NULL;
        if((*native_ti)->GetFieldName(native_ti,declaring,fields[at],&name,&descriptor,NULL)!=JVMTI_ERROR_NONE||!name||!descriptor)valid=0;
        if(valid&&!strcmp(name,wanted)){
            jint actualModifiers=0;
            if(selected||(*native_ti)->GetFieldDeclaringClass(native_ti,declaring,fields[at],&owner)!=JVMTI_ERROR_NONE
                    ||!owner||!native_original.IsSameObject(env,owner,declaring)
                    ||(*native_ti)->GetFieldModifiers(native_ti,declaring,fields[at],&actualModifiers)!=JVMTI_ERROR_NONE)valid=0;
            else {selected=fields[at];*signature=descriptor;descriptor=NULL;*modifiers=actualModifiers;}
        }
        if(name)(*native_ti)->Deallocate(native_ti,(unsigned char*)name);
        if(descriptor)(*native_ti)->Deallocate(native_ti,(unsigned char*)descriptor);
        if(owner)native_original.DeleteLocalRef(env,owner);
        if(native_original.ExceptionCheck(env))valid=0;
    }
    if(fields)(*native_ti)->Deallocate(native_ti,(unsigned char*)fields);
    if(!valid){if(*signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)*signature);*signature=NULL;return NULL;}
    return selected;
}

typedef struct {const char *signature;jclass type;jfieldID value;} NativeNetworkBox;
static NativeNetworkBox native_network_boxes[]={
    {"Ljava/lang/Boolean;",NULL,NULL},{"Ljava/lang/Byte;",NULL,NULL},
    {"Ljava/lang/Character;",NULL,NULL},{"Ljava/lang/Short;",NULL,NULL},
    {"Ljava/lang/Integer;",NULL,NULL},{"Ljava/lang/Long;",NULL,NULL},
    {"Ljava/lang/Float;",NULL,NULL},{"Ljava/lang/Double;",NULL,NULL}
};
static SRWLOCK native_network_boxes_lock=SRWLOCK_INIT;

/* Find only an already initialized bootstrap wrapper. FindClass/GetFieldID can
 * initialize a class, so even these lookups use the VM's existing declarations. */
static jclass native_network_box_type(JNIEnv *env,unsigned index,char kind,jfieldID *value){
    NativeNetworkBox *box=&native_network_boxes[index];jclass result=NULL;
    AcquireSRWLockShared(&native_network_boxes_lock);
    if(box->type){result=(jclass)native_original.NewLocalRef(env,box->type);*value=box->value;}
    ReleaseSRWLockShared(&native_network_boxes_lock);
    if(result||native_original.ExceptionCheck(env))return result;
    jint count=0;jclass *classes=NULL;
    if((*native_ti)->GetLoadedClasses(native_ti,&count,&classes)!=JVMTI_ERROR_NONE||count<0)goto finished;
    for(jint at=0;at<count&&!result&&!native_original.ExceptionCheck(env);at++){
        jint status=0;
        if(native_bootstrap_class(env,classes[at],box->signature)
                &&(*native_ti)->GetClassStatus(native_ti,classes[at],&status)==JVMTI_ERROR_NONE
                &&(status&JVMTI_CLASS_STATUS_INITIALIZED))result=(jclass)native_original.NewLocalRef(env,classes[at]);
    }
finished:
    if(classes){for(jint at=0;at<count;at++)if(classes[at])native_original.DeleteLocalRef(env,classes[at]);(*native_ti)->Deallocate(native_ti,(unsigned char*)classes);}
    if(!result||native_original.ExceptionCheck(env)){if(result)native_original.DeleteLocalRef(env,result);return NULL;}
    char *signature=NULL;jint modifiers=0;
    jfieldID field=native_network_declared_field(env,result,"value",&signature,&modifiers);
    int exact=field&&signature&&signature[0]==kind&&!signature[1]&&!(modifiers&0x0008)&&!native_original.ExceptionCheck(env);
    if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);
    if(!exact){native_original.DeleteLocalRef(env,result);return NULL;}
    jclass retained=(jclass)native_original.NewGlobalRef(env,result);
    if(!retained||native_original.ExceptionCheck(env)){native_original.DeleteLocalRef(env,result);return NULL;}
    AcquireSRWLockExclusive(&native_network_boxes_lock);
    if(!box->type){box->type=retained;box->value=field;retained=NULL;}
    ReleaseSRWLockExclusive(&native_network_boxes_lock);
    if(retained)native_original.DeleteGlobalRef(env,retained);
    *value=field;return result;
}

static jobject native_network_box(JNIEnv *env,char kind,jvalue value){
    unsigned index;
    switch(kind){case 'Z':index=0;break;case 'B':index=1;break;case 'C':index=2;break;case 'S':index=3;break;
        case 'I':index=4;break;case 'J':index=5;break;case 'F':index=6;break;case 'D':index=7;break;default:return NULL;}
    jfieldID field=NULL;jclass type=native_network_box_type(env,index,kind,&field);
    jobject result=type&&field&&!native_original.ExceptionCheck(env)?native_original.AllocObject(env,type):NULL;
    if(result&&!native_original.ExceptionCheck(env))switch(kind){
        case 'Z':native_original.SetBooleanField(env,result,field,value.z);break;
        case 'B':native_original.SetByteField(env,result,field,value.b);break;
        case 'C':native_original.SetCharField(env,result,field,value.c);break;
        case 'S':native_original.SetShortField(env,result,field,value.s);break;
        case 'I':native_original.SetIntField(env,result,field,value.i);break;
        case 'J':native_original.SetLongField(env,result,field,value.j);break;
        case 'F':native_original.SetFloatField(env,result,field,value.f);break;
        case 'D':native_original.SetDoubleField(env,result,field,value.d);break;
    }
    if(type)native_original.DeleteLocalRef(env,type);
    if(native_original.ExceptionCheck(env)){if(result)native_original.DeleteLocalRef(env,result);return NULL;}
    return result;
}

JNIEXPORT jobject JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_networkReadField0(JNIEnv *env,jclass type,jobject receiver,jclass declaring,jstring fieldName,jboolean statik){
    NativeThread *state=native_thread();
    if(!state||!native_jni_ready||!native_ti||!native_controller||!declaring||!fieldName
            ||!native_original.IsSameObject(env,type,native_controller)||native_original.ExceptionCheck(env))return NULL;
    state->control++;
    const char *wanted=native_original.GetStringUTFChars(env,fieldName,NULL);char *signature=NULL;jint modifiers=0;
    jfieldID field=wanted&&!native_original.ExceptionCheck(env)?native_network_declared_field(env,declaring,wanted,&signature,&modifiers):NULL;
    jobject result=NULL;
    if(!field||!signature||!!(modifiers&0x0008)!=!!statik||native_original.ExceptionCheck(env))goto finished;
    if(statik){jint status=0;if((*native_ti)->GetClassStatus(native_ti,declaring,&status)!=JVMTI_ERROR_NONE||!(status&JVMTI_CLASS_STATUS_INITIALIZED))goto finished;}
    else if(!receiver||!native_original.IsInstanceOf(env,receiver,declaring))goto finished;
    if(signature[0]=='L'||signature[0]=='[')result=statik?native_original.GetStaticObjectField(env,declaring,field):native_original.GetObjectField(env,receiver,field);
    else if(!signature[1]){
        jvalue value={0};
#define NETWORK_READ_PRIMITIVE(KIND,NAME,MEMBER) case KIND:value.MEMBER=statik?native_original.GetStatic##NAME##Field(env,declaring,field):native_original.Get##NAME##Field(env,receiver,field);break;
        switch(signature[0]){
            NETWORK_READ_PRIMITIVE('Z',Boolean,z)
            NETWORK_READ_PRIMITIVE('B',Byte,b)
            NETWORK_READ_PRIMITIVE('C',Char,c)
            NETWORK_READ_PRIMITIVE('S',Short,s)
            NETWORK_READ_PRIMITIVE('I',Int,i)
            NETWORK_READ_PRIMITIVE('J',Long,j)
            NETWORK_READ_PRIMITIVE('F',Float,f)
            NETWORK_READ_PRIMITIVE('D',Double,d)
            default:goto finished;
        }
#undef NETWORK_READ_PRIMITIVE
        if(!native_original.ExceptionCheck(env))result=native_network_box(env,signature[0],value);
    }
finished:
    if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);
    if(wanted)native_original.ReleaseStringUTFChars(env,fieldName,wanted);
    state->control--;
    if(native_original.ExceptionCheck(env)){if(result)native_original.DeleteLocalRef(env,result);return NULL;}
    return result;
}

JNIEXPORT jobject JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_networkField0(JNIEnv *env,jclass type,jclass declaring,jstring fieldName){
    NativeThread *state=native_thread();
    if(!state||!native_jni_ready||!native_ti||!native_controller||!declaring||!fieldName
            ||!native_original.IsSameObject(env,type,native_controller)||native_original.ExceptionCheck(env))return NULL;
    state->control++;
    const char *wanted=native_original.GetStringUTFChars(env,fieldName,NULL);char *signature=NULL;jint modifiers=0;
    jfieldID field=wanted&&!native_original.ExceptionCheck(env)?native_network_declared_field(env,declaring,wanted,&signature,&modifiers):NULL;
    jobject result=field&&!native_original.ExceptionCheck(env)?native_original.ToReflectedField(env,declaring,field,(modifiers&0x0008)?JNI_TRUE:JNI_FALSE):NULL;
    if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);
    if(wanted)native_original.ReleaseStringUTFChars(env,fieldName,wanted);
    state->control--;
    if(native_original.ExceptionCheck(env)){if(result)native_original.DeleteLocalRef(env,result);return NULL;}
    return result;
}
#include "carrier.c"
#include "platform_structures.c"
