/* The VM receives only private result storage. Publication below is a short
 * native-only operation, ordered with our actual control allocation lifetime;
 * no ordinary JVMTI query or outside callback runs under that gate. */
typedef struct NativeToolingOutputSpan {
    void *target;const void *source;SIZE_T length;
} NativeToolingOutputSpan;
typedef struct NativeToolingOutputUse {
    NativeToolingEnv *record;JNIEnv *env;int internal,restricted;
    NativeToolingUse retained;
} NativeToolingOutputUse;
typedef BOOL (WINAPI *NativeToolingOutputWrite)(HANDLE,LPVOID,LPCVOID,SIZE_T,SIZE_T*);
static NativeToolingOutputWrite native_tooling_output_writer;
static int native_tooling_output_prepare(void){
    NativeToolingOutputWrite writer=(NativeToolingOutputWrite)host_real_memory_target(HOST_WRITE_PROCESS_MEMORY,"WriteProcessMemory");
    if(!writer)return 0;__atomic_store_n(&native_tooling_output_writer,writer,__ATOMIC_RELEASE);return 1;
}

static jvmtiError native_tooling_output_begin(jvmtiEnv *api,void *caller,int callbackSafe,NativeToolingOutputUse *use){
    memset(use,0,sizeof(*use));
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    use->restricted=native_tooling_restricted();
    uintptr_t address=(uintptr_t)caller;
    int internal=address>=native_tooling_image_begin&&address<native_tooling_image_end;
    /* Internal source/definition bookkeeping already owns the controller's
     * environment. Its direct C caller, not a deeper callback frame, selects
     * this path; it neither enters JNI nor tries a directory lock recursively. */
    if(internal&&api==native_ti){
        for(NativeToolingEnv *record=__atomic_load_n(&native_tooling_environments,__ATOMIC_ACQUIRE);record;record=record->next)
            if(record->api==api&&record->own&&!InterlockedCompareExchange(&record->disposed,0,0)){
                use->record=record;use->internal=1;return JVMTI_ERROR_NONE;
            }
        return JVMTI_ERROR_INVALID_ENVIRONMENT;
    }
    if(use->restricted){
        if(!callbackSafe)return JVMTI_ERROR_NOT_AVAILABLE;
        /* Heap and GC/ObjectFree callbacks retain their real environment until
         * they return. Borrow that hold without JNI, attachment, allocation or
         * waiting for a thread which the VM may have stopped. */
        NativeToolingEnv *record=state?state->toolingCallbackEnvironment:NULL;
        if(!record||record->api!=api)return JVMTI_ERROR_INVALID_ENVIRONMENT;
        if(record->own)return JVMTI_ERROR_ACCESS_DENIED;
        if(InterlockedCompareExchange(&native_tooling_shutdown,0,0)
                ||InterlockedCompareExchange(&record->closing,0,0)
                ||InterlockedCompareExchange(&record->disposed,0,0))return JVMTI_ERROR_NOT_AVAILABLE;
        use->record=record;return JVMTI_ERROR_NONE;
    }
    NativeToolingEnv *record=native_tooling_environment(api);
    if(!record)return JVMTI_ERROR_INVALID_ENVIRONMENT;
    if(record->own)return JVMTI_ERROR_ACCESS_DENIED;
    if(!native_tooling_use_begin(record,&use->retained))return JVMTI_ERROR_INVALID_ENVIRONMENT;
    use->record=record;
    if(native_vm_original.GetEnv){
        JNIEnv *env=NULL;
        if(native_vm_original.GetEnv(native_vm,(void**)&env,JNI_VERSION_1_8)==JNI_OK){
            use->env=env;
            if(env&&!native_thread_jni_observed(state,env)){native_tooling_use_end(&use->retained);return JVMTI_ERROR_OUT_OF_MEMORY;}
        }
    }
    return JVMTI_ERROR_NONE;
}
static void native_tooling_output_end(NativeToolingOutputUse *use){
    native_tooling_use_end(&use->retained);
}
static int native_tooling_output_lock(int restricted){
    if(restricted)return TryAcquireSRWLockExclusive(&native_private_gate)!=0;
    AcquireSRWLockExclusive(&native_private_gate);return 1;
}
static jvmtiError native_tooling_output_check_locked(const NativeToolingOutputSpan *spans,size_t count){
    for(size_t i=0;i<count;i++){
        const NativeToolingOutputSpan *span=&spans[i];if(!span->length)continue;
        uintptr_t first=(uintptr_t)span->target;
        if(!span->target||span->length-1>UINTPTR_MAX-first)return JVMTI_ERROR_ILLEGAL_ARGUMENT;
        if(native_private_range_locked(first,first+span->length-1))return JVMTI_ERROR_ACCESS_DENIED;
    }
    return JVMTI_ERROR_NONE;
}
static jvmtiError native_tooling_output_commit(NativeToolingOutputUse *use,const NativeToolingOutputSpan *spans,size_t count){
    NativeToolingOutputWrite writer=__atomic_load_n(&native_tooling_output_writer,__ATOMIC_ACQUIRE);
    if(!writer)return JVMTI_ERROR_NOT_AVAILABLE;
    if(!native_tooling_output_lock(use->restricted))return JVMTI_ERROR_NOT_AVAILABLE;
    jvmtiError result=native_tooling_output_check_locked(spans,count);
    /* Check every destination before the first effect. An accurate control
     * refusal cannot publish just a count, pointer or one part of a result. */
    for(size_t i=0;result==JVMTI_ERROR_NONE&&i<count;i++)if(spans[i].length){
        SIZE_T written=0;
        if(!writer(GetCurrentProcess(),spans[i].target,spans[i].source,spans[i].length,&written)
                ||written!=spans[i].length)result=JVMTI_ERROR_ILLEGAL_ARGUMENT;
    }
    ReleaseSRWLockExclusive(&native_private_gate);return result;
}
static jvmtiError native_tooling_output_deallocate(NativeToolingOutputUse *use,unsigned char *memory){
    if(use->internal)return use->record->original->Deallocate(use->record->api,memory);
    if(!native_tooling_output_lock(use->restricted))return JVMTI_ERROR_NOT_AVAILABLE;
    jvmtiError result=memory&&native_private_range_locked((uintptr_t)memory,(uintptr_t)memory)?JVMTI_ERROR_ACCESS_DENIED:JVMTI_ERROR_NONE;
    ReleaseSRWLockExclusive(&native_private_gate);
    /* Even a callback-safe JVMTI entry may transition a JavaThread into the VM.
     * Never hold our allocation gate across that transition: a safepoint may
     * need a callback which itself retains native storage. A valid live VM
     * allocation cannot become a private CRT/page allocation before its free;
     * invalid/stale pointers and concurrent double-free/reuse are unsupported.
     * NULL retains the VM's no-op contract, without guessing AllocationBase. */
    return result==JVMTI_ERROR_NONE?use->record->original->Deallocate(use->record->api,memory):result;
}
