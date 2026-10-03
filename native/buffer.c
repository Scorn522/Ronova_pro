/* Native address exposure and the real DirectByteBuffer allocation caller. */
static jmethodID native_buffer_allocation_query,native_buffer_address_query,native_buffer_object_query,native_buffer_release_query,native_buffer_initializing_query;
static int native_buffer_prepare(JNIEnv *env){
    if(!native_file_bridge)return 0;
    native_buffer_allocation_query=native_original.GetStaticMethodID(env,native_file_bridge,"nativeBufferAllocation","()Z");
    native_buffer_address_query=native_original.GetStaticMethodID(env,native_file_bridge,"nativeBufferAddress","(Ljava/lang/Object;[Ljava/lang/Module;)V");
    native_buffer_object_query=native_original.GetStaticMethodID(env,native_file_bridge,"nativeBufferAllocationObject","()Ljava/lang/Object;");
    native_buffer_release_query=native_original.GetStaticMethodID(env,native_file_bridge,"nativeBufferRelease","(Ljava/lang/Object;)Z");
    native_buffer_initializing_query=native_original.GetStaticMethodID(env,native_file_bridge,"nativeBufferInitializing","(Ljava/lang/Object;)Z");
    return native_buffer_allocation_query&&native_buffer_address_query&&native_buffer_object_query&&native_buffer_release_query&&native_buffer_initializing_query&&!native_original.ExceptionCheck(env);
}
static int native_buffer_allocation(JNIEnv *env,NativeThread *state){
    state->control++;state->bufferAllocation++;
    jboolean managed=native_original.CallStaticBooleanMethod(env,native_file_bridge,native_buffer_allocation_query);
    state->bufferAllocation--;state->control--;return managed==JNI_TRUE;
}
static jobject native_buffer_constructing(JNIEnv *env,NativeThread *state){
    state->control++;state->bufferAllocation++;
    jobject buffer=native_original.CallStaticObjectMethod(env,native_file_bridge,native_buffer_object_query);
    state->bufferAllocation--;state->control--;return buffer;
}
static int native_buffer_releasing(JNIEnv *env,NativeThread *state,jweak action){
    state->control++;state->bufferAllocation++;
    jobject actual=action?native_original.NewLocalRef(env,action):NULL;
    jboolean allowed=actual?native_original.CallStaticBooleanMethod(env,native_file_bridge,native_buffer_release_query,actual):JNI_FALSE;
    if(actual)native_original.DeleteLocalRef(env,actual);state->bufferAllocation--;state->control--;return allowed==JNI_TRUE;
}
static int native_buffer_initializing(JNIEnv *env,NativeThread *state,jweak root){
    state->control++;state->bufferAllocation++;jobject buffer=root?native_original.NewLocalRef(env,root):NULL;
    jboolean initializing=buffer?native_original.CallStaticBooleanMethod(env,native_file_bridge,native_buffer_initializing_query,buffer):JNI_FALSE;
    if(buffer)native_original.DeleteLocalRef(env,buffer);state->bufferAllocation--;state->control--;return initializing==JNI_TRUE;
}
static void *JNICALL native_buffer_address(JNIEnv *env,jobject buffer){
    NativeThread *state=native_thread();if(!state||state->control)return native_original.GetDirectBufferAddress(env,buffer);
    if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_BUFFER_ADDRESS");return NULL;}
    state->control++;jobjectArray sources=native_mutation_sources(env,state);
    if(sources&&!native_original.ExceptionCheck(env)){
        jobject previous=state->bufferAddress;jobjectArray previousSources=state->bufferSources;
        state->bufferAddress=buffer;state->bufferSources=sources;
        native_original.CallStaticVoidMethod(env,native_file_bridge,native_buffer_address_query,buffer,sources);
        state->bufferAddress=previous;state->bufferSources=previousSources;
    }
    if(sources)native_original.DeleteLocalRef(env,sources);state->control--;
    return native_original.ExceptionCheck(env)?NULL:native_original.GetDirectBufferAddress(env,buffer);
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_bufferAllocationBoundary0(JNIEnv *env,jclass type){
    (void)env;(void)type;NativeThread *state=native_thread();return state&&state->bufferAllocation?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_bufferAddressBoundary0(JNIEnv *env,jclass type,jobject buffer,jobjectArray sources){
    (void)type;NativeThread *state=native_thread();return state&&state->bufferAddress&&state->bufferSources
            &&native_original.IsSameObject(env,state->bufferAddress,buffer)&&native_original.IsSameObject(env,state->bufferSources,sources)?JNI_TRUE:JNI_FALSE;
}
