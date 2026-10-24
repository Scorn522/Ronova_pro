/* OpenJDK 17 Win64 platform structures, anchored in actual VM/JPLIS objects.
 * No address supplied by a Mod creates a protected allocation or a restore capability. */
typedef struct NativePlatformEnv17 {
    jvmtiEnv external; jint magic,version; void *next; unsigned char retransformable;
    const void *local; jvmtiEventCallbacks callbacks; void *extension,*tags;
    jlong enabled[3]; jvmtiCapabilities capabilities,prohibited; unsigned char hooked;
    char **prefixes; jint prefixCount;
} NativePlatformEnv17;
typedef struct NativePlatformJplisEnv {jvmtiEnv *api;void *agent;jboolean retransformer;} NativePlatformJplisEnv;
typedef struct NativePlatformJplis {
    JavaVM *vm; NativePlatformJplisEnv normal,retransform;
    jobject instrumentation; jmethodID premain,agentmain,transform;
    jboolean redefineAvailable,redefineAdded,prefixAvailable,prefixAdded;
    const char *className,*options,*jar;
} NativePlatformJplis;
typedef struct NativePlatformAgent {
    uintptr_t address; jobject instrumentation; struct NativePlatformAgent *next;
} NativePlatformAgent;
static NativePlatformAgent *native_platform_agents;
static SRWLOCK native_platform_gate=SRWLOCK_INIT;
static volatile LONG native_platform_ready;

/* Anchor libinstrument in the validated JPLIS environment's real callback,
 * including a callback already wrapped by this controller. Hold its slot
 * lifetime while acquiring a loader reference; no loader call holds the
 * tooling directory. A filename or a Mod-supplied address cannot seed it. */
static void native_platform_backend(const NativePlatformEnv17 *normal,jvmtiEnv *api){
    if(InterlockedCompareExchangePointer((void *volatile*)&native_instrument_backend,NULL,NULL)
            ||!normal->callbacks.ClassFileLoadHook)return;
    void *callback=(void*)normal->callbacks.ClassFileLoadHook;NativeToolingSlot *held=NULL;
    AcquireSRWLockShared(&native_tooling_records);
    for(NativeToolingSlot *slot=native_tooling_slots;slot;slot=slot->next)
        if(slot->environment->api==api&&slot->entry==callback&&!slot->releasing){
            held=slot;InterlockedIncrement(&slot->active);callback=slot->callback;break;
        }
    ReleaseSRWLockShared(&native_tooling_records);
    HMODULE pin=NULL;
    if(callback&&GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,(LPCSTR)callback,&pin)){
        if(pin==native_control_image()||InterlockedCompareExchangePointer((void *volatile*)&native_instrument_backend,pin,NULL))
            host_real_free_library(pin);
    }
    if(held){
        AcquireSRWLockExclusive(&native_tooling_records);InterlockedDecrement(&held->active);
        native_tooling_slot_queue_locked(held);ReleaseSRWLockExclusive(&native_tooling_records);
    }
}
/* Resolve only the absence of Java attribution observed before Java startup.
 * The caller has validated the VM, JPLIS local-storage back pointers and the
 * actual InstrumentationImpl global receiver. Match the currently installed
 * callback slots as well: neither a DLL name nor an arbitrary GetEnv result
 * grants a platform identity. Native/Java owners and other unknowns survive. */
static void native_platform_startup_sources(const NativePlatformEnv17 *snapshot,jvmtiEnv *api){
    NativeToolingEnv *record=native_tooling_environment(api);
    HMODULE backend=(HMODULE)InterlockedCompareExchangePointer((void *volatile*)&native_instrument_backend,NULL,NULL);
    if(!record||!backend||record->own||record->owners||!record->startupUnknown)return;
    AcquireSRWLockExclusive(&record->mutation);
    AcquireSRWLockExclusive(&native_tooling_records);
    if(!record->closing&&!record->disposed&&snapshot->external==record->shadow&&*api==record->shadow){
        InterlockedExchange(&record->unknown,0);
        if(record->version){
#define PLATFORM_STARTUP_SOURCE(NAME,COUNT) \
            {NativeToolingSlot *slot=record->version->slots[TOOLING_##NAME]; \
             if(slot&&slot->admitted&&slot->startupUnknown&&!slot->owners&&slot->image==backend \
                     &&slot->entry==(void*)snapshot->callbacks.NAME)InterlockedExchange(&slot->unknown,0);}
            NATIVE_TOOLING_CALLBACKS(PLATFORM_STARTUP_SOURCE)
#undef PLATFORM_STARTUP_SOURCE
        }
    }
    ReleaseSRWLockExclusive(&native_tooling_records);
    ReleaseSRWLockExclusive(&record->mutation);
}
static int native_platform_profile(JNIEnv *env){
    if(InterlockedCompareExchange(&native_platform_ready,0,0))return 1;
    if(!InitOnceExecuteOnce(&code_vm_once,code_vm_initialize,env,NULL)||!code_vm_layout.ready)return 0;
    NativePlatformEnv17 snapshot; jvmtiCapabilities capabilities;void *local=NULL;jint version=0;
    memset(&capabilities,0,sizeof(capabilities));
    if(!code_vm_read((uintptr_t)native_ti,&snapshot,sizeof(snapshot))||snapshot.magic!=0x71ee
            ||(*native_ti)->GetVersionNumber(native_ti,&version)!=JVMTI_ERROR_NONE
            ||(*native_ti)->GetCapabilities(native_ti,&capabilities)!=JVMTI_ERROR_NONE
            ||(*native_ti)->GetEnvironmentLocalStorage(native_ti,&local)!=JVMTI_ERROR_NONE
            ||snapshot.local!=local||memcmp(&snapshot.capabilities,&capabilities,sizeof(capabilities))
            ||(snapshot.version&JVMTI_VERSION_MASK_INTERFACE_TYPE)!=JVMTI_VERSION_INTERFACE_JVMTI
            ||sizeof(void*)!=8)return 0;
    InterlockedExchange(&native_platform_ready,1);return 1;
}
static size_t native_platform_env_span(jvmtiEnv *api){
    /* The public environment is the first member of the supported HotSpot 17
     * layout. Include _next and callbacks, not just its first function pointer. */
    return api&&InterlockedCompareExchange(&native_platform_ready,0,0)?sizeof(NativePlatformEnv17):sizeof(*api);
}
static int native_platform_range(uintptr_t first,uintptr_t last,int restricted){
    if(!native_control_shared(&native_platform_gate,restricted))return native_range_unknown("PLATFORM_AGENTS_BUSY");
    int hit=0,unknown=0;
    for(NativePlatformAgent *record=native_platform_agents;record&&!hit;record=record->next){
        hit=native_control_overlaps(first,last,(void*)record->address,sizeof(NativePlatformJplis));
        if(!hit){
            NativePlatformJplis image;
            /* This retained controller anchor is a live local snapshot, just
             * like the JNI slots checked by the same range admission. Keep
             * fault handling and the VM identity check without a kernel read
             * on every scalar Unsafe/NativeImage access. Never cache api. */
            if(!native_control_snapshot((const void*)record->address,&image,sizeof(image))||image.vm!=native_vm){
                unknown=1;native_range_unknown("JPLIS_AGENT_UNREADABLE");continue;}
            hit=native_control_overlaps(first,last,image.normal.api,native_platform_env_span(image.normal.api))
                    ||native_control_overlaps(first,last,image.retransform.api,native_platform_env_span(image.retransform.api));
        }
    }
    ReleaseSRWLockShared(&native_platform_gate);
    if(hit)return 1;
    if(InterlockedCompareExchange(&native_platform_ready,0,0)&&native_ti
            &&native_control_overlaps(first,last,native_ti,sizeof(NativePlatformEnv17)))return 1;
    return unknown?-1:0;
}
static int native_platform_gate_call(JNIEnv *env,jclass controller,const char *name){
    if(!native_jni_ready||!native_original.IsSameObject(env,controller,native_controller))return 0;
    jmethodID gate=native_original.GetStaticMethodID(env,native_controller,name,"()Z");
    int allowed=gate&&native_original.CallStaticBooleanMethod(env,native_controller,gate)&&!native_original.ExceptionCheck(env);
    if(!allowed&&!native_original.ExceptionCheck(env))native_refuse(env,"ACTUAL_PLATFORM_CONTROL_CALLER_REQUIRED");
    return allowed;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_protectPlatformStructures0(JNIEnv *env,jclass controller,jobject instrumentation){
    if(!native_platform_gate_call(env,controller,"platformInstaller")||!instrumentation)return JNI_FALSE;
    NativeThread *state=native_thread();if(!state)return JNI_FALSE;state->control++;
    jclass actual=NULL;NativePlatformAgent *prepared=NULL;int valid=native_platform_profile(env);
    if(!valid)goto done;
    actual=native_original.GetObjectClass(env,instrumentation);
    valid=actual&&native_bootstrap_class(env,actual,"Lsun/instrument/InstrumentationImpl;");
    jfieldID field=valid?native_original.GetFieldID(env,actual,"mNativeAgent","J"):NULL;
    uintptr_t address=field?(uintptr_t)native_original.GetLongField(env,instrumentation,field):0;
    NativePlatformJplis image;NativePlatformEnv17 normal,retransform;
    valid=address&&!native_original.ExceptionCheck(env)&&code_vm_read(address,&image,sizeof(image))
            &&image.vm==native_vm&&image.normal.agent==(void*)address&&image.normal.api
            &&(!image.retransform.api||image.retransform.agent==(void*)address);
    if(valid){
        valid=code_vm_read((uintptr_t)image.normal.api,&normal,sizeof(normal))
                &&normal.magic==0x71ee&&normal.local==(void*)(address+offsetof(NativePlatformJplis,normal));
    }
    if(valid&&image.retransform.api){
        valid=code_vm_read((uintptr_t)image.retransform.api,&retransform,sizeof(retransform))
                &&retransform.magic==0x71ee&&retransform.local==(void*)(address+offsetof(NativePlatformJplis,retransform));
    }
    // The constructor returns before JPLIS installs this global reference.
    // Once present, require a real global handle for this exact receiver.
    if(valid&&image.instrumentation)valid=native_original.GetObjectRefType(env,image.instrumentation)==JNIGlobalRefType
            &&native_original.IsSameObject(env,image.instrumentation,instrumentation)&&!native_original.ExceptionCheck(env);
    if(!valid)goto done;
    prepared=(NativePlatformAgent*)calloc(1,sizeof(*prepared));
    if(!prepared){valid=0;goto done;}
    prepared->address=address;prepared->instrumentation=native_original.NewGlobalRef(env,instrumentation);
    if(!prepared->instrumentation){valid=0;goto done;}
    AcquireSRWLockExclusive(&native_platform_gate);
    NativePlatformAgent *found=native_platform_agents;while(found&&found->address!=address)found=found->next;
    if(!found){prepared->next=native_platform_agents;native_platform_agents=prepared;prepared=NULL;}
    else valid=native_original.IsSameObject(env,found->instrumentation,instrumentation);
    ReleaseSRWLockExclusive(&native_platform_gate);
    if(valid){
        native_platform_backend(&normal,image.normal.api);
        if(image.instrumentation){
            native_platform_startup_sources(&normal,image.normal.api);
            if(image.retransform.api)native_platform_startup_sources(&retransform,image.retransform.api);
        }
    }
done:
    if(prepared){if(prepared->instrumentation)native_original.DeleteGlobalRef(env,prepared->instrumentation);free(prepared);}
    if(actual)native_original.DeleteLocalRef(env,actual);state->control--;
    return valid&&!native_original.ExceptionCheck(env)?JNI_TRUE:JNI_FALSE;
}

/* A class anchor belongs to an actual already-existing entity. Holding the
 * original Class keeps its Klass alive; the weak object does not keep bodies alive. */
typedef struct NativeEntitySlot {jlong offset;unsigned char kind;} NativeEntitySlot;
typedef struct NativeEntityClass {
    jweak object; jclass original; jlong bytes,offset; uint64_t bits; int width;
    unsigned char referenceType;
    NativeEntitySlot *slots;size_t count;struct NativeEntityClass *next;
} NativeEntityClass;
static NativeEntityClass *native_entity_classes[256];
static SRWLOCK native_entity_gate=SRWLOCK_INIT;
typedef jint (JNICALL *NativePlatformGetInt)(JNIEnv*,jobject,jobject,jlong);
typedef jlong (JNICALL *NativePlatformGetLong)(JNIEnv*,jobject,jobject,jlong);
typedef jboolean (JNICALL *NativePlatformCasInt)(JNIEnv*,jobject,jobject,jlong,jint,jint);
typedef jboolean (JNICALL *NativePlatformCasLong)(JNIEnv*,jobject,jobject,jlong,jlong,jlong);
static void *native_platform_unsafe(JNIEnv *env,const char *name,const char *descriptor){
    jmethodID method=native_original.GetMethodID(env,native_unsafe_class,name,descriptor);void *result=NULL;
    if(!method)return NULL;
    AcquireSRWLockShared(&native_records);
    for(NativeUnsafeEntry *entry=native_unsafe_entries;entry;entry=entry->next)
        if(entry->method==method){result=entry->original;break;}
    ReleaseSRWLockShared(&native_records);
    return result&&native_image(result)==code_vm_layout.image?result:NULL;
}
static int native_platform_boolean_flag(const char *wanted,int *value){
    uint64_t flagsAddress=0,countAddress=0,stride=0,nameOffset=0,valueOffset=0;
    uintptr_t flags=0;size_t count=0;
    if(!code_vm_field("JVMFlag","flags",1,&flagsAddress)||!code_vm_field("JVMFlag","numFlags",1,&countAddress)
            ||!code_vm_type("JVMFlag",&stride)||!code_vm_field("JVMFlag","_name",0,&nameOffset)
            ||!code_vm_field("JVMFlag","_addr",0,&valueOffset)||nameOffset+sizeof(uintptr_t)>stride||valueOffset+sizeof(uintptr_t)>stride
            ||!code_vm_read((uintptr_t)flagsAddress,&flags,sizeof(flags))||!code_vm_read((uintptr_t)countAddress,&count,sizeof(count))
            ||!stride||count>65536)return 0;
    for(size_t i=0;i<count;i++){
        uintptr_t name=0,at=flags+i*(uintptr_t)stride,flag=0;unsigned char boolean=0;
        if(at<flags||!code_vm_read(at+nameOffset,&name,sizeof(name)))return 0;
        if(name&&code_vm_name(name,wanted)){
            if(!code_vm_read(at+valueOffset,&flag,sizeof(flag))||!code_vm_read(flag,&boolean,1)||boolean>1)return 0;
            *value=boolean;return 1;
        }
    }
    return 0;
}
typedef struct NativeEntityHeader {
    jlong offset,mirrorOffset;uintptr_t base;uint64_t layoutOffset,referenceOffset;
    unsigned slowBit;int width,shift,ready;
} NativeEntityHeader;
static NativeEntityHeader native_entity_header;
static INIT_ONCE native_entity_header_once=INIT_ONCE_STATIC_INIT;
static BOOL CALLBACK native_entity_header_initialize(PINIT_ONCE once,PVOID parameter,PVOID *context){
    (void)once;(void)parameter;(void)context;NativeEntityHeader *header=&native_entity_header;
    uint64_t offset=0,mirrorAddress=0,baseAddress=0,shiftAddress=0,klassSize=0,instanceSize=0;int compressed=0;int32_t mirrorOffset=0;
    if(!native_platform_boolean_flag("UseCompressedClassPointers",&compressed)
            ||!code_vm_field("oopDesc",compressed?"_metadata._compressed_klass":"_metadata._klass",0,&offset)
            ||!code_vm_field("java_lang_Class","_klass_offset",1,&mirrorAddress)
            ||!code_vm_read((uintptr_t)mirrorAddress,&mirrorOffset,sizeof(mirrorOffset))
            ||!code_vm_field("Klass","_layout_helper",0,&header->layoutOffset)
            ||!code_vm_field("InstanceKlass","_reference_type",0,&header->referenceOffset)
            ||!code_vm_integer("Klass::_lh_instance_slow_path_bit",&header->slowBit)
            ||!code_vm_type("Klass",&klassSize)||!code_vm_type("InstanceKlass",&instanceSize)
            ||header->layoutOffset>klassSize||sizeof(int32_t)>klassSize-header->layoutOffset
            ||header->referenceOffset>=instanceSize||!header->slowBit||offset>64||offset>INT64_MAX||mirrorOffset<=0)return TRUE;
    header->offset=(jlong)offset;header->mirrorOffset=mirrorOffset;header->width=compressed?4:8;
    if(compressed){
        int32_t shift=0;
        if(!code_vm_field("CompressedKlassPointers","_narrow_klass._base",1,&baseAddress)
                ||!code_vm_field("CompressedKlassPointers","_narrow_klass._shift",1,&shiftAddress)
                ||!code_vm_read((uintptr_t)baseAddress,&header->base,sizeof(header->base))
                ||!code_vm_read((uintptr_t)shiftAddress,&shift,sizeof(shift))||shift<0||shift>=64)return TRUE;
        header->shift=shift;
    }
    header->ready=1;return TRUE;
}
static int native_entity_header_profile(void){
    return InitOnceExecuteOnce(&native_entity_header_once,native_entity_header_initialize,NULL,NULL)&&native_entity_header.ready;
}
/* java_lang_Class::_klass_offset stores a full metadata pointer even when
 * object headers use narrowKlass. Encode that actual mirror's Klass with this
 * VM's exported base/shift; sampled bytes from a changed object are no anchor. */
static int native_entity_klass(JNIEnv *env,jobject unsafe,jclass type,uint64_t *bits,jlong *bytes,unsigned char *referenceType){
    NativeEntityHeader *header=&native_entity_header;
    if(!unsafe||!type||!native_entity_header_profile())return 0;
    jclass mirrorType=native_original.GetObjectClass(env,type);jlong mirrorBytes=0;
    int valid=mirrorType&&native_bootstrap_class(env,mirrorType,"Ljava/lang/Class;")
            &&(*native_ti)->GetObjectSize(native_ti,type,&mirrorBytes)==JVMTI_ERROR_NONE
            &&header->mirrorOffset<=mirrorBytes&&(jlong)sizeof(jlong)<=mirrorBytes-header->mirrorOffset;
    if(mirrorType)native_original.DeleteLocalRef(env,mirrorType);if(!valid)return 0;
    NativePlatformGetLong read=(NativePlatformGetLong)native_platform_unsafe(env,"getLongVolatile","(Ljava/lang/Object;J)J");
    if(!read)return 0;
    uintptr_t klass=(uintptr_t)(uint64_t)read(env,unsafe,type,header->mirrorOffset);int32_t layout=0;
    valid=klass&&header->layoutOffset<=UINTPTR_MAX-klass&&header->referenceOffset<=UINTPTR_MAX-klass
            &&!native_original.ExceptionCheck(env)&&code_vm_read(klass+(uintptr_t)header->layoutOffset,&layout,sizeof(layout))
            &&layout>0&&code_vm_read(klass+(uintptr_t)header->referenceOffset,referenceType,sizeof(*referenceType));
    uint64_t encoded=(uint64_t)klass;
    if(valid&&header->width==4){
        valid=klass>=header->base;
        if(valid){uint64_t delta=(uint64_t)(klass-header->base),mask=(UINT64_C(1)<<header->shift)-1;
            encoded=delta>>header->shift;
            valid=!(delta&mask)&&encoded&&encoded<=UINT32_MAX&&(encoded<<header->shift)==delta;
        }
    }
    jlong size=(jlong)((uint32_t)layout&~header->slowBit);
    valid=valid&&encoded&&size>=header->offset+header->width&&(size%(jlong)sizeof(uintptr_t))==0
            &&(uintptr_t)(uint64_t)read(env,unsafe,type,header->mirrorOffset)==klass&&!native_original.ExceptionCheck(env);
    if(!valid)return 0;*bits=encoded;*bytes=size;return 1;
}
static int native_entity_header_equals(JNIEnv *env,jobject unsafe,jobject object,uint64_t bits){
    NativeEntityHeader *header=&native_entity_header;void *read=native_platform_unsafe(env,
            header->width==4?"getIntVolatile":"getLongVolatile",header->width==4?"(Ljava/lang/Object;J)I":"(Ljava/lang/Object;J)J");
    if(!read)return 0;
    uint64_t actual=header->width==4?(uint32_t)((NativePlatformGetInt)read)(env,unsafe,object,header->offset)
            :(uint64_t)((NativePlatformGetLong)read)(env,unsafe,object,header->offset);
    return actual==bits&&!native_original.ExceptionCheck(env);
}
static int native_entity_layout(JNIEnv *env,jobject unsafe,jclass type,NativeEntitySlot **output,size_t *length){
    NativeEntitySlot *rows=NULL;size_t count=0;int valid=native_carrier_metadata.ready;
    jclass current=(jclass)native_original.NewLocalRef(env,type);
    while(current&&valid&&!native_original.ExceptionCheck(env)){
        jint number=0;jfieldID *fields=NULL;
        valid=(*native_ti)->GetClassFields(native_ti,current,&number,&fields)==JVMTI_ERROR_NONE&&number>=0;
        for(jint i=0;valid&&i<number;i++){
            jint modifiers=0;char *signature=NULL;
            valid=(*native_ti)->GetFieldModifiers(native_ti,current,fields[i],&modifiers)==JVMTI_ERROR_NONE;
            if(!valid||modifiers&0x0008)continue;
            valid=(*native_ti)->GetFieldName(native_ti,current,fields[i],NULL,&signature,NULL)==JVMTI_ERROR_NONE&&signature;
            jobject field=valid?native_original.ToReflectedField(env,current,fields[i],JNI_FALSE):NULL;
            jlong offset=field?native_carrier_metadata.offset(env,unsafe,field):-1;
            unsigned char kind=signature?(signature[0]=='L'||signature[0]=='['?'L':(unsigned char)signature[0]):0;
            if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);
            if(field)native_original.DeleteLocalRef(env,field);
            valid=offset>=0&&kind&&!native_original.ExceptionCheck(env)&&count<SIZE_MAX/sizeof(*rows)-1;
            if(valid){NativeEntitySlot *next=(NativeEntitySlot*)realloc(rows,(count+1)*sizeof(*rows));
                if(!next)valid=0;else {rows=next;rows[count++]=(NativeEntitySlot){offset,kind};}}
        }
        if(fields)(*native_ti)->Deallocate(native_ti,(unsigned char*)fields);
        jclass parent=valid?native_original.GetSuperclass(env,current):NULL;native_original.DeleteLocalRef(env,current);current=parent;
    }
    if(current)native_original.DeleteLocalRef(env,current);
    if(!valid||native_original.ExceptionCheck(env)){free(rows);return 0;}
    // Small declaration arrays, ordered independently of reflection/cache order.
    for(size_t i=1;i<count;i++){NativeEntitySlot row=rows[i];size_t at=i;while(at&&rows[at-1].offset>row.offset){rows[at]=rows[at-1];at--;}rows[at]=row;}
    *output=rows;*length=count;return 1;
}
static NativeEntityClass *native_entity_find(JNIEnv *env,jobject object,unsigned bucket){
    for(NativeEntityClass *record=native_entity_classes[bucket];record;record=record->next)
        if(native_original.IsSameObject(env,record->object,object))return record;
    return NULL;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_captureEntityClass0(JNIEnv *env,jclass controller,jobject object,jclass expected){
    if(!native_platform_gate_call(env,controller,"carrierReader")||!object||!expected)return JNI_FALSE;
    NativeThread *state=native_thread();if(!state)return JNI_FALSE;state->control++;
    jclass actual=NULL;jobject unsafe=NULL;NativeEntityClass *record=NULL;
    uint64_t bits=0;jlong bytes=0;unsigned char referenceType=0;jint hash=0;
    int valid=native_platform_profile(env)&&native_entity_header_profile()&&(unsafe=native_carrier_unsafe(env))!=NULL
            &&native_entity_klass(env,unsafe,expected,&bits,&bytes,&referenceType);
    if(valid){actual=native_original.GetObjectClass(env,object);valid=actual&&native_original.IsSameObject(env,actual,expected)
            &&native_entity_header_equals(env,unsafe,object,bits);}
    if(valid)valid=(*native_ti)->GetObjectHashCode(native_ti,object,&hash)==JVMTI_ERROR_NONE;
    unsigned bucket=(unsigned)hash&255;
    if(valid){
        AcquireSRWLockShared(&native_entity_gate);NativeEntityClass *existing=native_entity_find(env,object,bucket);
        if(existing){valid=native_original.IsSameObject(env,existing->original,expected)&&existing->bits==bits&&existing->bytes==bytes
                &&existing->referenceType==referenceType&&existing->offset==native_entity_header.offset&&existing->width==native_entity_header.width;
            ReleaseSRWLockShared(&native_entity_gate);goto done;}
        ReleaseSRWLockShared(&native_entity_gate);
    }
    if(valid){record=(NativeEntityClass*)calloc(1,sizeof(*record));valid=record!=NULL;}
    if(valid){
        record->offset=native_entity_header.offset;record->width=native_entity_header.width;
        record->bits=bits;record->bytes=bytes;record->referenceType=referenceType;
        valid=native_entity_layout(env,unsafe,expected,&record->slots,&record->count);
    }
    if(valid){
        jclass now=native_original.GetObjectClass(env,object);
        valid=now&&native_original.IsSameObject(env,now,expected)&&native_entity_header_equals(env,unsafe,object,record->bits)
                &&!native_original.ExceptionCheck(env);
        if(now)native_original.DeleteLocalRef(env,now);
    }
    if(valid){record->object=native_original.NewWeakGlobalRef(env,object);record->original=(jclass)native_original.NewGlobalRef(env,expected);valid=record->object&&record->original;}
    if(valid){
        AcquireSRWLockExclusive(&native_entity_gate);
        // Reclaim dead body anchors in this bucket; original Klass lives only as long as needed.
        NativeEntityClass **at=&native_entity_classes[bucket];
        while(*at){NativeEntityClass *old=*at;if(native_original.IsSameObject(env,old->object,NULL)){
            *at=old->next;native_original.DeleteWeakGlobalRef(env,old->object);native_original.DeleteGlobalRef(env,old->original);free(old->slots);free(old);
        }else at=&old->next;}
        NativeEntityClass *existing=native_entity_find(env,object,bucket);
        if(existing)valid=native_original.IsSameObject(env,existing->original,expected)&&existing->bits==record->bits&&existing->bytes==record->bytes
                &&existing->referenceType==record->referenceType&&existing->offset==record->offset&&existing->width==record->width;
        else {record->next=native_entity_classes[bucket];native_entity_classes[bucket]=record;record=NULL;}
        ReleaseSRWLockExclusive(&native_entity_gate);
    }
done:
    if(record){if(record->object)native_original.DeleteWeakGlobalRef(env,record->object);if(record->original)native_original.DeleteGlobalRef(env,record->original);free(record->slots);free(record);}
    if(unsafe)native_original.DeleteLocalRef(env,unsafe);if(actual)native_original.DeleteLocalRef(env,actual);state->control--;
    return valid&&!native_original.ExceptionCheck(env)?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_restoreEntityClass0(JNIEnv *env,jclass controller,jobject object,jclass expected){
    if(!native_platform_gate_call(env,controller,"carrierReader")||!object||!expected)return JNI_FALSE;
    NativeThread *state=native_thread();if(!state)return JNI_FALSE;state->control++;
    jint hash=0;int valid=native_platform_profile(env)&&native_entity_header_profile()
            &&(*native_ti)->GetObjectHashCode(native_ti,object,&hash)==JVMTI_ERROR_NONE;
    jclass actual=NULL;jobject unsafe=NULL;NativeEntitySlot *rows=NULL;size_t count=0;
    AcquireSRWLockShared(&native_entity_gate);
    NativeEntityClass *record=valid?native_entity_find(env,object,(unsigned)hash&255):NULL;
    valid=record&&native_original.IsSameObject(env,record->original,expected)
            &&record->offset==native_entity_header.offset&&record->width==native_entity_header.width;
    ReleaseSRWLockShared(&native_entity_gate);
    // This JNI argument keeps the body alive, so its weak-keyed immutable
    // anchor cannot be reclaimed. Never hold the directory lock across VM
    // field enumeration, reflected-field allocation or the actual CAS.
    uint64_t originalBits=0;jlong originalBytes=0;unsigned char originalReference=0;
    if(valid)valid=(unsafe=native_carrier_unsafe(env))!=NULL
            &&native_entity_klass(env,unsafe,expected,&originalBits,&originalBytes,&originalReference)
            &&originalBits==record->bits&&originalBytes==record->bytes&&originalReference==record->referenceType;
    if(valid){actual=native_original.GetObjectClass(env,object);valid=actual!=NULL;}
    if(valid&&!native_original.IsSameObject(env,actual,expected)){
        uint64_t before=0;jlong bytes=0;unsigned char referenceType=0;
        valid=native_entity_klass(env,unsafe,actual,&before,&bytes,&referenceType)&&bytes==record->bytes
                &&referenceType==record->referenceType&&native_entity_layout(env,unsafe,actual,&rows,&count)&&count==record->count;
        for(size_t i=0;valid&&i<count;i++)valid=rows[i].offset==record->slots[i].offset&&rows[i].kind==record->slots[i].kind;
        void *compare=valid?native_platform_unsafe(env,record->width==4?"compareAndSetInt":"compareAndSetLong",record->width==4?"(Ljava/lang/Object;JII)Z":"(Ljava/lang/Object;JJJ)Z"):NULL;
        valid=valid&&compare&&!native_original.ExceptionCheck(env);
        // Expected is the canonical header for the Class whose complete layout
        // was inspected above. A later competing class change makes CAS fail;
        // a fresh unvalidated header can never become the expected value.
        if(valid&&record->width==4)valid=((NativePlatformCasInt)compare)(env,unsafe,object,record->offset,(jint)before,(jint)record->bits);
        else if(valid)valid=((NativePlatformCasLong)compare)(env,unsafe,object,record->offset,(jlong)before,(jlong)record->bits);
    }
    if(valid)valid=native_entity_header_equals(env,unsafe,object,record->bits);
    if(valid){jclass restored=native_original.GetObjectClass(env,object);valid=restored&&native_original.IsSameObject(env,restored,expected);if(restored)native_original.DeleteLocalRef(env,restored);}
    free(rows);if(unsafe)native_original.DeleteLocalRef(env,unsafe);if(actual)native_original.DeleteLocalRef(env,actual);state->control--;
    return valid&&!native_original.ExceptionCheck(env)?JNI_TRUE:JNI_FALSE;
}
