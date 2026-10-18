/* Native address exposure and the real DirectByteBuffer allocation caller. */
static jmethodID native_buffer_address_query,native_buffer_created_query;
static int native_buffer_control(JNIEnv *env,const void *address,SIZE_T bytes){
    NativeThread *state=native_thread();int restricted=host_loader_callout()||(state&&(state->heapCallback||state->toolingRestricted));
    int hit=host_memory_range(address,bytes,0,restricted);
    if(hit)native_refuse(env,hit>0?"NATIVE_CONTROL_BUFFER_DENIED":"NATIVE_CONTROL_RANGE_UNAVAILABLE");return !hit;
}
static int native_buffer_prepare(JNIEnv *env){
    if(!native_file_bridge)return 0;
    native_buffer_address_query=native_original.GetStaticMethodID(env,native_file_bridge,"nativeBufferAddress","(Ljava/lang/Object;[Ljava/lang/Module;)Z");
    native_buffer_created_query=native_original.GetStaticMethodID(env,native_file_bridge,"nativeBufferCreated","(Ljava/lang/Object;[Ljava/lang/Module;)V");
    return native_buffer_address_query&&native_buffer_created_query&&!native_original.ExceptionCheck(env);
}
static void *JNICALL native_buffer_address(JNIEnv *env,jobject buffer){
    NativeThread *state=native_thread();if(!state||state->control)return native_original.GetDirectBufferAddress(env,buffer);
    DWORD previousError=GetLastError();
    if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_BUFFER_ADDRESS");SetLastError(previousError);return NULL;}
    SetLastError(previousError);void *address=native_original.GetDirectBufferAddress(env,buffer);DWORD error=GetLastError();
    // Never replace the original failure or its pending Throwable with an observer failure.
    if(!address||native_original.ExceptionCheck(env)){SetLastError(error);return address;}
    if(!native_buffer_control(env,address,1)){SetLastError(error);return NULL;}
    state->control++;jobjectArray sources=native_mutation_sources(env,state);
    if(native_original.ExceptionCheck(env)){native_original.ExceptionClear(env);InterlockedIncrement(&native_failures);}
    int allowed=1;if(sources&&!native_original.ExceptionCheck(env)){
        jobject previous=state->bufferAddress;jobjectArray previousSources=state->bufferSources;
        state->bufferAddress=buffer;state->bufferSources=sources;
        allowed=native_original.CallStaticBooleanMethod(env,native_file_bridge,native_buffer_address_query,buffer,sources)==JNI_TRUE;
        state->bufferAddress=previous;state->bufferSources=previousSources;
        if(native_original.ExceptionCheck(env)){native_original.ExceptionClear(env);InterlockedIncrement(&native_failures);allowed=1;}
    }
    if(allowed){
        if(native_original.ExceptionCheck(env)){native_original.ExceptionClear(env);InterlockedIncrement(&native_failures);}
    }
    if(sources)native_original.DeleteLocalRef(env,sources);state->control--;
    if(!allowed)native_refuse(env,"BUFFER_NATIVE_ADDRESS_LIFETIME_DENIED");
    SetLastError(error);return allowed?address:NULL;
}
static jobject JNICALL native_buffer_new(JNIEnv *env,void *address,jlong bytes){
    NativeThread *state=native_thread();if(!state||state->control)return native_original.NewDirectByteBuffer(env,address,bytes);
    DWORD previousError=GetLastError();
    if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_BUFFER");SetLastError(previousError);return NULL;}
    if(bytes>=0&&!native_buffer_control(env,address,(SIZE_T)bytes)){SetLastError(previousError);return NULL;}
    SetLastError(previousError);jobject buffer=native_original.NewDirectByteBuffer(env,address,bytes);DWORD error=GetLastError();
    if(buffer&&!native_original.ExceptionCheck(env)){
        state->control++;jobjectArray sources=native_mutation_sources(env,state);
        if(sources&&!native_original.ExceptionCheck(env)){
            jobject previous=state->bufferAddress;jobjectArray previousSources=state->bufferSources;
            state->bufferAddress=buffer;state->bufferSources=sources;
            native_original.CallStaticVoidMethod(env,native_file_bridge,native_buffer_created_query,buffer,sources);
            state->bufferAddress=previous;state->bufferSources=previousSources;
        }
        // Registration is an observer of this already successful JNI operation.
        // A missing Java receipt leaves this actual allocation opaque, not the result null.
        if(native_original.ExceptionCheck(env)){native_original.ExceptionClear(env);InterlockedIncrement(&native_failures);}
        if(native_original.ExceptionCheck(env)){native_original.ExceptionClear(env);InterlockedIncrement(&native_failures);}
        if(sources)native_original.DeleteLocalRef(env,sources);state->control--;
    }
    SetLastError(error);return buffer;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_bufferAllocationBoundary0(JNIEnv *env,jclass type){
    (void)env;(void)type;NativeThread *state=native_thread();return state&&state->bufferAllocation?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_bufferAddressBoundary0(JNIEnv *env,jclass type,jobject buffer,jobjectArray sources){
    (void)type;NativeThread *state=native_thread();return state&&state->bufferAddress&&state->bufferSources
            &&native_original.IsSameObject(env,state->bufferAddress,buffer)&&native_original.IsSameObject(env,state->bufferSources,sources)?JNI_TRUE:JNI_FALSE;
}
