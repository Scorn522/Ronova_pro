/* Whole-Mod raw-memory entry control. Ordinary private allocations, C++
 * objects and byte contents have no Ronova SDK ledger or recovery image.
 * A scope holds only the actual caller/source set for a real Unsafe/JNI body. */
typedef struct NativeMemoryWrite {
    OwnerLink *sources;uint64_t address,length;jlong token;int reading,unknown,nativeScalar;
    struct NativeMemoryWrite *previous;
} NativeMemoryWrite;
static volatile LONG64 native_memory_generation;
/* Tokens identify operations, not reusable storage. Saturation must not make
 * a stale token valid again, including on another thread. */
static jlong native_memory_token(void){
    LONG64 current=InterlockedCompareExchange64(&native_memory_generation,0,0);
    for(;;){
        if(current==INT64_MAX)return 0;
        LONG64 actual=InterlockedCompareExchange64(&native_memory_generation,current+1,current);
        if(actual==current)return (jlong)(current+1);current=actual;
    }
}
static NativeMemoryWrite *native_memory_scope_take(NativeThread *state){
    NativeMemoryWrite *scope=state->memorySpare;
    if(scope)state->memorySpare=NULL;
    else scope=(NativeMemoryWrite*)calloc(1,sizeof(*scope));
    return scope;
}
static void native_memory_scope_recycle(NativeThread *state,NativeMemoryWrite *scope){
    native_owner_links_free(scope->sources);memset(scope,0,sizeof(*scope));
    /* One empty container per thread bounds retention. Nested live scopes
     * still allocate normally; no sources, permission or range result survives. */
    if(!state->memorySpare)state->memorySpare=scope;else free(scope);
}
static int native_memory_sources(JNIEnv *env,NativeThread *state,jobjectArray supplied,OwnerLink **sources,int *unknown,int javaCaptured){
    if(javaCaptured){
        /* memoryWriteBegin's authenticated controller boundary has just read
         * the live Java sources (including the actual buffer/copy operation).
         * Retain all additional live C roots here, without calling back into
         * Java to repeat that same walk. This is per operation, not a cache. */
        if(!native_capture_scope_sources(env,state,sources,unknown)||!host_capture_caller(state,NULL,sources))return 0;
        host_producers(sources);
    }else if(!host_capture_query(env,state,NULL,sources,unknown))return 0;
    return native_owner_modules(env,supplied,sources,unknown,NULL);
}
static int native_memory_authority(JNIEnv *env,jclass type){
    if(!native_controller||!native_original.IsSameObject(env,type,native_controller)||!native_memory_gate)return 0;
    /* Generic callers, reads, copies and completions retain the original
     * Java selector. The actual Native Unsafe scalar begin has its own exact
     * live prefix check; no source/range or policy result is cached. */
    return !native_original.ExceptionCheck(env)
            &&native_original.CallStaticBooleanMethod(env,native_controls,native_memory_gate)&&!native_original.ExceptionCheck(env);
}
static int native_memory_control(JNIEnv *env,NativeThread *state,uint64_t address,uint64_t bytes){
    if(!bytes)return 1;
    int restricted=host_loader_callout()||(state&&(state->heapCallback||state->toolingRestricted||state->vmDetaching
            ||state->vmThreadEnding||state->hostUnloadImage||state->hostRelease||state->hostLoad&&state->hostLoad->calling));
    int hit=bytes>SIZE_MAX?-1:host_memory_range((void*)(uintptr_t)address,(SIZE_T)bytes,0,restricted);
    if(hit>0)native_refuse(env,"NATIVE_CONTROL_MEMORY_DENIED");else if(hit<0)native_range_refuse(env,restricted);
    return !hit;
}
static int native_memory_call_begin(JNIEnv *env,NativeThread *state,Binding *binding,unsigned char *frame){
    OwnerLink *sources=NULL;int unknown=0;
    state->control++;int ready=native_memory_sources(env,state,NULL,&sources,&unknown,0);state->control--;
    if(ready&&(native_owner_stopped(sources)||native_stopped(env))){native_refuse(env,"RONOVA_STOPPED_NATIVE_MEMORY_OPERATION");ready=0;}
    if(ready&&unknown&&native_has_producer(sources)){
        native_source_diagnostic(state,binding->unsafeOperation==UNSAFE_ALLOCATE?"unsafe_allocate":"unsafe_memory");
        native_refuse(env,"NATIVE_MEMORY_SOURCES_UNAVAILABLE");ready=0;
    }
    if(ready&&binding->unsafeOperation!=UNSAFE_ALLOCATE){
        uint64_t address=native_unsafe_argument(binding,frame,2);
        if(address)ready=native_memory_control(env,state,address,1);
    }
    native_owner_links_free(sources);
    if(!ready&&!native_original.ExceptionCheck(env))native_refuse(env,"NATIVE_MEMORY_SOURCES_UNAVAILABLE");
    return ready;
}
static jlong native_memory_begin(JNIEnv *env,jlong address,jlong length,jobjectArray contributors,jboolean reading){
    NativeThread *state=native_thread();if(!state||length<0){native_refuse(env,"NATIVE_MEMORY_SCOPE_UNAVAILABLE");return 0;}
    NativeMemoryWrite *scope=native_memory_scope_take(state);if(!scope){native_refuse(env,"NATIVE_MEMORY_SCOPE_CAPACITY");return 0;}
    scope->address=(uint64_t)address;scope->length=(uint64_t)length;scope->reading=reading==JNI_TRUE;
    state->control++;int ready=native_memory_sources(env,state,contributors,&scope->sources,&scope->unknown,contributors!=NULL);state->control--;
    if(ready&&native_owner_stopped(scope->sources)){native_refuse(env,"RONOVA_STOPPED_NATIVE_MEMORY_SOURCE");ready=0;}
    if(ready)ready=native_memory_control(env,state,scope->address,scope->length);
    if(!ready){native_memory_scope_recycle(state,scope);if(!native_original.ExceptionCheck(env))native_refuse(env,"NATIVE_MEMORY_SOURCES_UNAVAILABLE");return 0;}
    scope->token=native_memory_token();
    if(!scope->token){native_memory_scope_recycle(state,scope);native_refuse(env,"NATIVE_MEMORY_SCOPE_CAPACITY");return 0;}
    scope->previous=state->memoryWrite;state->memoryWrite=scope;return scope->token;
}
JNIEXPORT jlong JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_memoryWriteBegin0(JNIEnv *env,jclass type,jlong address,jlong length,jobjectArray contributors,jboolean reading,jboolean snapshot){
    int scalar=native_unsafe_memory_authority(env,type,address,length,reading,snapshot);
    if(!scalar&&!native_memory_authority(env,type)){native_refuse(env,"ACTUAL_NATIVE_MEMORY_BOUNDARY_REQUIRED");return 0;}
    NativeThread *state=native_thread();NativeUnsafeMutation *mutation=scalar?state->unsafeMutation:NULL;
    jlong token=native_memory_begin(env,address,length,contributors,reading);
    if(token&&mutation){state->memoryWrite->nativeScalar=1;mutation->rawMemoryToken=token;}
    return token;
}
static int native_memory_scalar_pending(NativeThread *state,jlong token){
    NativeMemoryWrite *scope=state?state->memoryWrite:NULL;
    return scope&&scope->token==token&&scope->nativeScalar&&!scope->reading;
}
static int native_memory_scalar_receipt(JNIEnv *env,NativeThread *state,jlong token,jobject receipt){
    NativeMemoryWrite *scope=state?state->memoryWrite:NULL;
    if(!scope||scope->token!=token||!scope->nativeScalar||scope->reading||!receipt)return 0;
    jclass actual=native_original.GetObjectClass(env,receipt);
    int valid=actual&&native_original.IsSameObject(env,actual,native_unsafe_raw_type);
    if(actual)native_original.DeleteLocalRef(env,actual);
    if(valid)valid=native_original.GetLongField(env,receipt,native_unsafe_raw_token)==token
            &&!native_original.GetBooleanField(env,receipt,native_unsafe_raw_closed)
            &&!native_original.GetBooleanField(env,receipt,native_unsafe_raw_reading);
    if(valid){
        jobject owner=native_original.GetObjectField(env,receipt,native_unsafe_raw_thread);jthread current=NULL;
        valid=owner&&(*native_ti)->GetCurrentThread(native_ti,&current)==JVMTI_ERROR_NONE&&current
                &&native_original.IsSameObject(env,owner,current);
        if(current)native_original.DeleteLocalRef(env,current);if(owner)native_original.DeleteLocalRef(env,owner);
    }
    // JNI field access can dispatch a watch callback. A recycled node at the
    // same address is not the same scope: recheck its never-reused token too.
    return valid&&!native_original.ExceptionCheck(env)&&state->memoryWrite==scope&&native_memory_scalar_pending(state,token);
}
/* Only the actual VM scalar's native_leave reaches this helper. Heap and copy
 * receipts still use their original Java completion. A Java allocation failure
 * closes its own token before the begin returns, so it never enters this path. */
static void native_memory_scalar_end(JNIEnv *env,NativeThread *state,jlong token,jobject receipt){
    jthrowable failure=native_original.ExceptionOccurred(env);if(failure)native_original.ExceptionClear(env);
    NativeMemoryWrite *scope=state?state->memoryWrite:NULL;
    int valid=scope&&scope->token==token&&scope->nativeScalar&&!scope->reading
            &&(!receipt||native_memory_scalar_receipt(env,state,token,receipt));
    if(valid){
        // Finish the exact Native scope before a field-watch callback can
        // observe closed. Such a callback may reenter, but cannot consume this
        // retired token or make us follow a recycled scope node afterwards.
        state->memoryWrite=scope->previous;native_memory_scope_recycle(state,scope);
        if(receipt)native_original.SetBooleanField(env,receipt,native_unsafe_raw_closed,JNI_TRUE);
    }else native_refuse(env,"ACTUAL_NATIVE_RAW_RECEIPT_REQUIRED");
    if(failure){if(native_original.ExceptionCheck(env)){native_original.ExceptionClear(env);InterlockedIncrement(&native_failures);}
        native_original.Throw(env,failure);native_original.DeleteLocalRef(env,failure);}
    if(receipt)native_original.DeleteLocalRef(env,receipt);
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_memoryWriteEnd0(JNIEnv *env,jclass type,jlong token,jboolean written){
    (void)written;if(!native_memory_authority(env,type)){native_refuse(env,"ACTUAL_NATIVE_MEMORY_BOUNDARY_REQUIRED");return;}
    NativeThread *state=native_thread();NativeMemoryWrite *scope=state?state->memoryWrite:NULL;
    if(!scope||scope->token!=token){native_refuse(env,"ACTUAL_NATIVE_MEMORY_SCOPE_REQUIRED");return;}
    state->memoryWrite=scope->previous;native_memory_scope_recycle(state,scope);
}
JNIEXPORT jobjectArray JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_memoryReadSources0(JNIEnv *env,jclass type,jlong token){
    if(!native_memory_authority(env,type)){native_refuse(env,"ACTUAL_NATIVE_MEMORY_BOUNDARY_REQUIRED");return NULL;}
    NativeThread *state=native_thread();NativeMemoryWrite *scope=state?state->memoryWrite:NULL;
    while(scope&&scope->token!=token)scope=scope->previous;
    if(!scope||!scope->reading){native_refuse(env,"ACTUAL_NATIVE_MEMORY_READ_SCOPE_REQUIRED");return NULL;}
    /* Known actual callers remain known. Raw bytes have no field/allocation
     * provenance in V1.5, so retain the existing explicit unknown marker. */
    return native_tooling_sources(env,scope->sources,1);
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_memoryWriteContributors0(JNIEnv *env,jclass type,jlong token,jobjectArray contributors){
    if(!native_memory_authority(env,type)){native_refuse(env,"ACTUAL_NATIVE_MEMORY_BOUNDARY_REQUIRED");return;}
    NativeThread *state=native_thread();NativeMemoryWrite *scope=state?state->memoryWrite:NULL;
    if(!scope||scope->reading||scope->token!=token){native_refuse(env,"ACTUAL_NATIVE_MEMORY_SCOPE_REQUIRED");return;}
    state->control++;int ready=native_memory_sources(env,state,contributors,&scope->sources,&scope->unknown,0);state->control--;
    if(!ready)native_refuse(env,"NATIVE_MEMORY_SOURCES_UNAVAILABLE");
    else if(native_owner_stopped(scope->sources))native_refuse(env,"RONOVA_STOPPED_NATIVE_MEMORY_SOURCE");
}
static void native_memory_thread_end(JNIEnv *env,NativeThread *state){
    (void)env;while(state->memoryWrite){NativeMemoryWrite *scope=state->memoryWrite;state->memoryWrite=scope->previous;native_owner_links_free(scope->sources);free(scope);}
    NativeMemoryWrite *spare=state->memorySpare;state->memorySpare=NULL;free(spare);
}
/* Reading a real OS I/O-vector descriptor is an entry argument query, not a
 * private-storage registration or byte restoration capability. */
static int native_memory_read(uint64_t address,void *target,size_t bytes){
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    int restricted=host_loader_callout()||(state&&(state->heapCallback||state->toolingRestricted));
    if(host_memory_range((void*)(uintptr_t)address,bytes,0,restricted))return 0;
    SIZE_T copied=0;return host_real_read_process_memory(GetCurrentProcess(),(void*)(uintptr_t)address,target,bytes,&copied)&&copied==bytes;
}
JNIEXPORT jlongArray JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_ioReadVector0(JNIEnv *env,jclass type,jobject operation,jlong address,jint count,jboolean windows){
    jmethodID gate=native_controller?native_original.GetStaticMethodID(env,native_controller,"ioReadVectorCaller","(Ljava/lang/Object;JIZ)Z"):NULL;
    if(!gate||!native_original.IsSameObject(env,type,native_controller)||!native_original.CallStaticBooleanMethod(env,native_controller,gate,operation,address,count,windows)
            ||native_original.ExceptionCheck(env)){native_refuse(env,"ACTUAL_IO_VECTOR_REQUIRED");return NULL;}
    if(count<0||count>INT32_MAX/2){native_refuse(env,"IO_VECTOR_LENGTH_INVALID");return NULL;}
    // WSABUF is ULONG length, aligned pointer; the synchronous JDK iovec is
    // pointer, size_t. Both layouts are captured from the original call site.
    struct NativeWsaBuffer {uint32_t length;uintptr_t address;};
    struct NativeIovec {uintptr_t address,length;};
    jlongArray result=native_original.NewLongArray(env,count*2);if(!result)return NULL;
    uint64_t stride=windows?sizeof(struct NativeWsaBuffer):sizeof(struct NativeIovec);
    if((uint64_t)count>0&&((uint64_t)address>UINT64_MAX-(uint64_t)count*stride)){native_refuse(env,"IO_VECTOR_RANGE_INVALID");return NULL;}
    for(jint i=0;i<count;i++){
        jlong values[2];int ready;
        if(windows){struct NativeWsaBuffer span={0};ready=native_memory_read((uint64_t)address+(uint64_t)i*stride,&span,sizeof(span));values[0]=(jlong)span.address;values[1]=(jlong)span.length;}
        else {struct NativeIovec span={0};ready=native_memory_read((uint64_t)address+(uint64_t)i*stride,&span,sizeof(span));values[0]=(jlong)span.address;values[1]=(jlong)span.length;}
        if(!ready||values[1]<0){native_refuse(env,"IO_VECTOR_LAYOUT_UNAVAILABLE");return NULL;}
        native_original.SetLongArrayRegion(env,result,i*2,2,values);if(native_original.ExceptionCheck(env))return NULL;
    }
    return result;
}
