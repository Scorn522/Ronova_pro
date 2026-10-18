/* Included by bindings.c after the standard callback/source helpers.
 * JVMTI 17 GetExtensionEvents describes only the variadic parameters; the
 * fixed jvmtiEnv* is supplied separately by the VM. HotSpot 17's ClassUnload
 * metadata describes JNIEnv* and const char*, and its actual dispatch passes
 * (jvmtiEnv*, JNIEnv*, const char*).
 * https://docs.oracle.com/en/java/javase/17/docs/specs/jvmti.html#GetExtensionEvents
 * https://github.com/openjdk/jdk17u/blob/master/src/hotspot/share/prims/jvmtiExtensions.cpp
 * https://github.com/openjdk/jdk17u/blob/master/src/hotspot/share/prims/jvmtiExport.cpp
 */
#define NATIVE_TOOLING_EXTENSION_PARAMS 64
typedef struct NativeToolingExtensionSignature {
    unsigned arguments,floating;int supported;
} NativeToolingExtensionSignature;

/* Each accepted Win64 parameter occupies one eight-byte argument position.
 * Pointer kinds do not inherit their base type's scalar calling convention.
 * In a variadic event, scalar float is promoted to double and small integer
 * types to int; the thunk preserves the VM's original promoted argument bits.
 * https://learn.microsoft.com/en-us/cpp/build/x64-calling-convention
 */
static int native_tooling_extension_parameter(const jvmtiParamInfo *parameter,int *floating){
    *floating=0;
    switch(parameter->base_type){
        case JVMTI_TYPE_JBYTE:case JVMTI_TYPE_JCHAR:case JVMTI_TYPE_JSHORT:
        case JVMTI_TYPE_JINT:case JVMTI_TYPE_JLONG:case JVMTI_TYPE_JFLOAT:
        case JVMTI_TYPE_JDOUBLE:case JVMTI_TYPE_JBOOLEAN:case JVMTI_TYPE_JOBJECT:
        case JVMTI_TYPE_JTHREAD:case JVMTI_TYPE_JCLASS:case JVMTI_TYPE_JVALUE:
        case JVMTI_TYPE_JFIELDID:case JVMTI_TYPE_JMETHODID:case JVMTI_TYPE_CCHAR:
        case JVMTI_TYPE_CVOID:case JVMTI_TYPE_JNIENV:break;
        default:return 0;
    }
    switch(parameter->kind){
        case JVMTI_KIND_IN_PTR:case JVMTI_KIND_IN_BUF:case JVMTI_KIND_ALLOC_BUF:
        case JVMTI_KIND_ALLOC_ALLOC_BUF:case JVMTI_KIND_OUT:case JVMTI_KIND_OUT_BUF:return 1;
        case JVMTI_KIND_IN:
            if(parameter->base_type==JVMTI_TYPE_CVOID||parameter->base_type==JVMTI_TYPE_JNIENV)return 0;
            if(parameter->base_type==JVMTI_TYPE_JVALUE&&sizeof(jvalue)!=8)return 0;
            *floating=parameter->base_type==JVMTI_TYPE_JFLOAT||parameter->base_type==JVMTI_TYPE_JDOUBLE;
            return 1;
        default:return 0;
    }
}
static void native_tooling_extension_metadata_free(NativeToolingEnv *record,jint count,jvmtiExtensionEventInfo *events){
    if(!events)return;
    for(jint i=0;i<count;i++){
        jvmtiExtensionEventInfo *event=&events[i];
        if(event->params){
            for(jint p=0;p<event->param_count;p++)if(event->params[p].name)
                record->original->Deallocate(record->api,(unsigned char*)event->params[p].name);
            record->original->Deallocate(record->api,(unsigned char*)event->params);
        }
        if(event->id)record->original->Deallocate(record->api,(unsigned char*)event->id);
        if(event->short_description)record->original->Deallocate(record->api,(unsigned char*)event->short_description);
    }
    record->original->Deallocate(record->api,(unsigned char*)events);
}
static jvmtiError native_tooling_extension_signature(NativeToolingEnv *record,jint index,NativeToolingExtensionSignature *signature){
    memset(signature,0,sizeof(*signature));jint count=0;jvmtiExtensionEventInfo *events=NULL;
    jvmtiError result=record->original->GetExtensionEvents(record->api,&count,&events);
    if(result!=JVMTI_ERROR_NONE)return result;
    int found=0;
    for(jint i=0;i<count;i++)if(events[i].extension_event_index==index){
        found=1;jvmtiExtensionEventInfo *event=&events[i];
        if(event->param_count>=0&&event->param_count<=NATIVE_TOOLING_EXTENSION_PARAMS
                &&(!event->param_count||event->params)&&sizeof(void*)==8){
            signature->arguments=1+(unsigned)event->param_count;signature->supported=1;
            for(jint p=0;p<event->param_count;p++){
                int floating=0;
                if(!native_tooling_extension_parameter(&event->params[p],&floating)){signature->supported=0;break;}
                if(floating&&p<3)signature->floating|=1u<<(p+1);
            }
        }
        break;
    }
    native_tooling_extension_metadata_free(record,count,events);
    return found?JVMTI_ERROR_NONE:JVMTI_ERROR_ILLEGAL_ARGUMENT;
}
static void native_tooling_extension_address(NativeCode *code,unsigned reg,unsigned offset){
    nb(code,reg<8?0x48:0x4c);nb(code,0x8d);nb(code,0x84|((reg&7)<<3));nb(code,0x24);nw(code,offset);
}
/* Admission calls have their own arguments. Keep the VM's GPR/XMM values and
 * stack parameters separately, then restore the original variadic call exactly.
 * The generated entry contains its immutable slot, so replacing an event never
 * borrows the new registration's owners or caller origin.
 */
static void *native_tooling_extension_entry(NativeToolingSlot *slot,const NativeToolingExtensionSignature *signature){
    unsigned arguments=signature->arguments,extra=arguments>4?(arguments-4)*8:0;
    unsigned save=32+extra,invocation=(save+96+7)&~7;
    unsigned returned=(invocation+(unsigned)sizeof(NativeToolingInvocation)+15)&~15;
    unsigned frame=((returned+32+15)&~15)+8;
    unsigned char *page=(unsigned char*)VirtualAlloc(NULL,4096,MEM_COMMIT|MEM_RESERVE,PAGE_READWRITE);if(!page)return NULL;
    NativeCode code={page,page};nb(&code,0x48);nb(&code,0x81);nb(&code,0xec);nw(&code,frame);
    const unsigned regs[]={1,2,8,9};
    for(unsigned i=0;i<4;i++)ns(&code,regs[i],save+i*8);
    for(unsigned i=0;i<4;i++)if(signature->floating&(1u<<i))nf(&code,1,i,save+32+i*16);
    for(unsigned i=0;i<extra;i+=8){nl(&code,0,frame+40+i);ns(&code,0,32+i);}
    ni(&code,1,(uintptr_t)slot);nl(&code,2,save);native_tooling_extension_address(&code,8,invocation);
    nc(&code,(void*)native_tooling_callback_begin);
    nb(&code,0x85);nb(&code,0xc0);nb(&code,0x0f);nb(&code,0x84);
    unsigned char *skip=code.at;nw(&code,0);
    for(unsigned i=0;i<4;i++)nl(&code,regs[i],save+i*8);
    for(unsigned i=0;i<4;i++)if(signature->floating&(1u<<i))nf(&code,0,i,save+32+i*16);
    nc(&code,slot->callback);
    /* Extension events return void. Preserve the callback's return registers
     * across the internal end call as well; output pointers remain untouched. */
    ns(&code,0,returned);nf(&code,1,0,returned+16);
    native_tooling_extension_address(&code,1,invocation);nc(&code,(void*)native_tooling_callback_end);
    nl(&code,0,returned);nf(&code,0,0,returned+16);
    uint32_t distance=(uint32_t)(code.at-(skip+4));memcpy(skip,&distance,sizeof(distance));
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
static NativeToolingSlot *native_tooling_extension_slot(JNIEnv *env,NativeToolingEnv *record,jvmtiExtensionEvent callback,
        const NativeToolingExtensionSignature *signature,OwnerLink *writers,jweak origin,int unknown){
    NativeToolingSlot *slot=calloc(1,sizeof(*slot));if(!slot)return NULL;
    slot->environment=record;slot->callback=(void*)callback;slot->unknown=unknown||record->unknown;
    int ready=native_owner_merge(&slot->owners,record->owners)&&native_owner_merge(&slot->owners,writers)&&native_capture_image_owners((void*)callback,&slot->owners);
    if(origin&&env&&native_original.NewWeakGlobalRef){slot->origin=native_original.NewWeakGlobalRef(env,origin);if(!slot->origin)ready=0;}
    if(slot->owners&&env&&native_original.NewGlobalRef){jobjectArray lease=native_tooling_writers(env,slot->owners);
        if(lease){slot->sourceLease=(jobjectArray)native_original.NewGlobalRef(env,lease);native_original.DeleteLocalRef(env,lease);}ready=ready&&slot->sourceLease;}
    slot->image=native_image((void*)callback);
    if(slot->image&&!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,(LPCSTR)callback,&slot->pin))ready=0;
    if(!slot->image)slot->unknown=1;
    if(ready)slot->entry=native_tooling_extension_entry(slot,signature);
    if(!slot->entry){
        if(slot->pin)host_real_free_library(slot->pin);if(slot->sourceLease)native_original.DeleteGlobalRef(env,slot->sourceLease);
        if(slot->origin)native_original.DeleteWeakGlobalRef(env,slot->origin);native_owner_links_free(slot->owners);free(slot);return NULL;
    }
    slot->admitted=1;AcquireSRWLockExclusive(&native_tooling_records);
    slot->next=native_tooling_slots;native_tooling_slots=slot;ReleaseSRWLockExclusive(&native_tooling_records);return slot;
}
/* Caller holds native_tooling_records. Unsupported outside callbacks retain
 * their original lifetime limitation even after their registration is cleared:
 * no intercepted exit exists that could release their DLL or environment lease.
 * Supported entries use the existing per-slot in-flight retirement instead. */
static int native_tooling_extensions_unresolved_locked(const NativeToolingEnv *record){
    for(NativeToolingExtension *extension=record->extensions;extension;extension=extension->next)if(!extension->slot)return 1;
    return 0;
}
static void native_tooling_extension_abandon(NativeToolingExtension *extension){
    if(!extension)return;
    if(extension->slot)native_tooling_slot_retire(extension->slot);
    else {if(extension->pin)host_real_free_library(extension->pin);native_owner_links_free(extension->owners);}
    free(extension);
}
static jvmtiError JNICALL native_tooling_extension(jvmtiEnv *api,jint index,jvmtiExtensionEvent callback){
    NativeToolingEnv *record=native_tooling_environment(api);if(!record)return JVMTI_ERROR_INVALID_ENVIRONMENT;
    TOOLING_USE(record);
    JNIEnv *env=native_tooling_jni();void *caller=__builtin_return_address(0);
    if(record->own){if(!native_tooling_internal(caller))return native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);return record->original->SetExtensionEventCallback(api,index,callback);}
    int stopped=native_tooling_effect_stopped(record,env,caller);
    if(callback&&(stopped||native_tooling_internal((void*)callback)))return native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);
    NativeToolingExtension *extension=NULL;NativeToolingExtensionSignature signature={0};
    OwnerLink *owners=NULL;jweak origin=NULL;int unknown=0;
    if(callback){
        jvmtiError result=native_tooling_extension_signature(record,index,&signature);if(result!=JVMTI_ERROR_NONE)return result;
        int ready=native_tooling_capture(env,caller,&owners,&origin,&unknown)&&native_owner_merge(&owners,record->owners)&&native_capture_image_owners((void*)callback,&owners);unknown|=record->unknown;
        if(!ready){if(origin)native_original.DeleteWeakGlobalRef(env,origin);native_owner_links_free(owners);return native_tooling_error(JVMTI_ERROR_OUT_OF_MEMORY);}
        if(!signature.supported&&native_has_producer(owners)){
            if(origin)native_original.DeleteWeakGlobalRef(env,origin);native_owner_links_free(owners);return native_tooling_error(JVMTI_ERROR_NOT_AVAILABLE);
        }
        extension=calloc(1,sizeof(*extension));ready=extension!=NULL;
        if(extension){extension->index=index;extension->callback=callback;
            if(signature.supported){
                extension->slot=native_tooling_extension_slot(env,record,callback,&signature,owners,origin,unknown);
                if(extension->slot){extension->owners=extension->slot->owners;extension->unknown=extension->slot->unknown;}
                else ready=0;
            }else {
                extension->owners=owners;owners=NULL;extension->unknown=1;
                HMODULE image=native_image((void*)callback);
                if(image&&!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,(LPCSTR)callback,&extension->pin))ready=0;
            }
        }
        if(origin)native_original.DeleteWeakGlobalRef(env,origin);native_owner_links_free(owners);
        if(!ready){native_tooling_extension_abandon(extension);return native_tooling_error(JVMTI_ERROR_OUT_OF_MEMORY);}
    }
    AcquireSRWLockExclusive(&record->mutation);
    jvmtiError result=JVMTI_ERROR_NONE;
    if(record->closing||record->disposed||native_tooling_shutdown)result=JVMTI_ERROR_INVALID_ENVIRONMENT;
    else if(callback&&native_owner_stopped(extension->owners))result=native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);
    else if(!callback&&stopped){
        int owned=0;AcquireSRWLockShared(&native_tooling_records);
        for(NativeToolingExtension *p=record->extensions;p;p=p->next)if(p->index==index&&p->callback&&native_owner_stopped(p->owners)){owned=1;break;}
        ReleaseSRWLockShared(&native_tooling_records);if(!owned)result=native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);
    }
    if(result==JVMTI_ERROR_NONE)result=record->original->SetExtensionEventCallback(api,index,
            extension&&extension->slot?(jvmtiExtensionEvent)extension->slot->entry:callback);
    if(result==JVMTI_ERROR_NONE){
        AcquireSRWLockExclusive(&native_tooling_records);
        for(NativeToolingExtension *p=record->extensions;p;p=p->next)if(p->index==index&&p->callback){
            p->callback=NULL;if(p->slot){InterlockedExchange(&p->slot->admitted,0);native_tooling_slot_queue_locked(p->slot);}
        }
        if(extension){extension->next=record->extensions;record->extensions=extension;extension=NULL;}
        native_tooling_environment_queue_locked(record);ReleaseSRWLockExclusive(&native_tooling_records);
    }
    ReleaseSRWLockExclusive(&record->mutation);native_tooling_extension_abandon(extension);return result;
}
/* Caller holds record->mutation. Close admission even when the VM rejects the
 * unregister call; an already fetched entry still checks that same admission.
 * The last admitted invocation queues its exact pin and source lease for the
 * existing worker, which performs release outside restricted VM callbacks. */
static void native_tooling_extensions_retire_stopped(NativeToolingEnv *record){
    for(NativeToolingExtension *extension=record->extensions;extension;extension=extension->next)
        if(extension->callback&&native_owner_stopped(extension->owners)){
            if(extension->slot)native_tooling_slot_retire(extension->slot);
            jvmtiError result=record->original->SetExtensionEventCallback(record->api,extension->index,NULL);
            if(result!=JVMTI_ERROR_NONE)InterlockedIncrement(&native_tooling_failures);
            else {AcquireSRWLockExclusive(&native_tooling_records);extension->callback=NULL;ReleaseSRWLockExclusive(&native_tooling_records);}
        }
}
#undef NATIVE_TOOLING_EXTENSION_PARAMS
