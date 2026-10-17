#include <jni.h>
#include "bindings.c"
#include <bcrypt.h>
// Preserve Object.clone itself, including Cloneable checks; never substitute a Java ancestor body.
JNIEXPORT jint JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_abi(JNIEnv *env, jclass type) {
    (void)env; (void)type; return 91;
}
JNIEXPORT jobject JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_returnIdentifier0(JNIEnv *env,jclass type) {
    (void)type;
    if(native_original.ExceptionCheck(env))return NULL;
    unsigned char bytes[16];
    if(BCryptGenRandom(NULL,bytes,sizeof(bytes),BCRYPT_USE_SYSTEM_PREFERRED_RNG)<0){
        native_refuse(env,"NATIVE_RETURN_IDENTIFIER_RANDOM_UNAVAILABLE");return NULL;
    }
    bytes[6]=(bytes[6]&0x0f)|0x40;bytes[8]=(bytes[8]&0x3f)|0x80;
    uint64_t most=0,least=0;
    for(unsigned i=0;i<8;i++){most=(most<<8)|bytes[i];least=(least<<8)|bytes[i+8];}
    NativeThread *state=native_thread();
    if(!state){native_refuse(env,"NATIVE_RETURN_IDENTIFIER_SCOPE_UNAVAILABLE");return NULL;}
    state->control++;
    jclass uuid=native_original.FindClass(env,"java/util/UUID");
    jmethodID constructor=uuid?native_original.GetMethodID(env,uuid,"<init>","(JJ)V"):NULL;
    jobject result=constructor?native_original.NewObject(env,uuid,constructor,(jlong)most,(jlong)least):NULL;
    if(uuid)native_original.DeleteLocalRef(env,uuid);
    state->control--;
    return result;
}
JNIEXPORT jobject JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_returnFuture0(JNIEnv *env,jclass type) {
    (void)type;
    if(native_original.ExceptionCheck(env))return NULL;
    NativeThread *state=native_thread();
    if(!state){native_refuse(env,"NATIVE_RETURN_FUTURE_SCOPE_UNAVAILABLE");return NULL;}
    state->control++;
    jclass future=native_original.FindClass(env,"java/util/concurrent/CompletableFuture");
    /* Match completedFuture(null)'s exact JDK construction without entering a
     * woven inline factory that could notify a foreign synchronous observer. */
    jfieldID emptyField=future?native_original.GetStaticFieldID(env,future,"NIL",
            "Ljava/util/concurrent/CompletableFuture$AltResult;"):NULL;
    jobject empty=emptyField?native_original.GetStaticObjectField(env,future,emptyField):NULL;
    if(!empty)native_refuse(env,"NATIVE_RETURN_FUTURE_EMPTY_RESULT_UNAVAILABLE");
    jmethodID constructor=empty?native_original.GetMethodID(env,future,"<init>","(Ljava/lang/Object;)V"):NULL;
    jobject result=constructor?native_original.NewObject(env,future,constructor,empty):NULL;
    if(empty)native_original.DeleteLocalRef(env,empty);
    if(future)native_original.DeleteLocalRef(env,future);
    state->control--;
    return result;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_hostLibraryLoadReady0(JNIEnv *env,jclass type) {
    (void)env; (void)type; return host_library_load_ready()?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT jobject JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_clone0(JNIEnv *env, jclass type, jobject source) {
    (void)type;
    jclass bridge=(*env)->FindClass(env,"dev/ronova/pro/bootstrap/ControlBridge");
    if(bridge==NULL)return NULL;
    jmethodID gate=(*env)->GetStaticMethodID(env,bridge,"taskEffectAllowed","(Ljava/lang/Object;Ljava/lang/String;)Z");
    if(gate==NULL)return NULL;
    jstring operation=(*env)->NewStringUTF(env,"native-clone");
    if(operation==NULL)return NULL;
    jboolean allowed=(*env)->CallStaticBooleanMethod(env,bridge,gate,source,operation);
    if((*env)->ExceptionCheck(env))return NULL;
    if(!allowed) {
        jclass failure=(*env)->FindClass(env,"java/lang/CloneNotSupportedException");
        if(failure!=NULL)(*env)->ThrowNew(env,failure,"RONOVA_TERMINAL_CLONE_REFUSED");
        return NULL;
    }
    if(source==NULL) {
        jclass failure=(*env)->FindClass(env,"java/lang/NullPointerException");
        if(failure!=NULL)(*env)->ThrowNew(env,failure,"clone source");
        return NULL;
    }
    jclass object=(*env)->FindClass(env,"java/lang/Object");
    if(object==NULL)return NULL;
    jmethodID clone=(*env)->GetMethodID(env,object,"clone","()Ljava/lang/Object;");
    if(clone==NULL)return NULL;
    return (*env)->CallNonvirtualObjectMethod(env,source,object,clone);
}

// Bind the actual private controller entries before publishing JNI/Unsafe guards.
// Resolving any of these entries later would read the guarded native-library map.
static void JNICALL host_module0(JNIEnv *env,jclass type,jobject module,jboolean stopped){
    (void)type;
    if(!native_controller||!native_original.CallStaticBooleanMethod(env,native_controller,native_control_gate)){
        native_refuse(env,"NATIVE_CONTROL_AGENT_REQUIRED");return;
    }
    /* Rebind observed slots before stopping their resolver. A call that already
     * fetched the old result is classified at its exact original IAT call site. */
    host_module_update(env,module,stopped);
    if(stopped&&!native_original.ExceptionCheck(env))native_tooling_retire(env,module);
}
static int native_bind_controller(JNIEnv *env,jclass controller){
    static const JNINativeMethod methods[]={
        {"abi","()I",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_abi},
        {"returnIdentifier0","()Ljava/util/UUID;",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_returnIdentifier0},
        {"returnFuture0","()Ljava/util/concurrent/CompletableFuture;",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_returnFuture0},
        {"controlWriter0","()Ljava/lang/Class;",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_controlWriter0},
        {"rawMemoryWriter0","()Ljava/lang/Class;",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_rawMemoryWriter0},
        {"initialize0","(Ljava/lang/Class;Ljava/lang/Class;)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_initialize0},
        {"initializeDefinitionDirectory0","(Ljava/lang/Class;)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_initializeDefinitionDirectory0},
        {"compareDefinitionReference0","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_compareDefinitionReference0},
        {"codeLayout0","(Ljava/lang/ClassLoader;Ljava/lang/String;[B[Ljava/lang/String;[[[Ljava/lang/Module;ZLjava/lang/Object;)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_codeLayout0},
        {"codeDefinition0","(Ljava/lang/Class;[B)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_codeDefinition0},
        {"codeVersion0","(Ljava/lang/Class;[B)[Ljava/lang/String;",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_codeVersion0},
        {"codeDigest0","([B)[B",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_codeDigest0},
        {"codeMethodVersion0","(Ljava/lang/Class;[BLjava/lang/String;)[Ljava/lang/String;",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_codeMethodVersion0},
        {"codeDeclarations0","(Ljava/lang/Class;)[Ljava/lang/String;",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_codeDeclarations0},
        {"loadedClasses0","(Z)[Ljava/lang/Class;",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_loadedClasses0},
        {"bootstrapLookupClasses0","()[Ljava/lang/Class;",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_bootstrapLookupClasses0},
        {"controlTable0","()Ljava/lang/Object;",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_controlTable0},
        {"codeFrame0","(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;I)[Ljava/lang/Module;",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_codeFrame0},
        {"executionRootAllowed0","()Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_executionRootAllowed0},
        {"executionPlan0","(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;I)Ljava/lang/Object;",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_executionPlan0},
        {"executionWatch0","(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;ILjava/lang/Object;Ljava/lang/Object;)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_executionWatch0},
        {"executionSite0","(Ljava/lang/Object;)J",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_executionSite0},
        {"executionFrameSite0","(Ljava/lang/Object;)J",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_executionFrameSite0},
        {"platformPrepareTargets0","([Ljava/lang/String;)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_platformPrepareTargets0},
        {"platformQueueQuiescent0","(Ljava/lang/Class;Ljava/lang/Class;)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_platformQueueQuiescent0},
        {"executionPopPermit0","(Ljava/lang/Object;)I",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_executionPopPermit0},
        {"codeFieldOwner0","(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Class;",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_codeFieldOwner0},
        {"heapWatch0","(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_heapWatch0},
        {"heapReadField0","(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;)[Ljava/lang/Object;",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_heapReadField0},
        {"heapArrayImage0","(Ljava/lang/Object;JI)[B",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_heapArrayImage0},
        {"heapArrayBits0","(Ljava/lang/Object;JI)J",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_heapArrayBits0},
        {"restoreHeapArrayBytes0","(Ljava/lang/Object;J[B[B)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_restoreHeapArrayBytes0},
        {"heapState0","()[J",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_heapState0},
        {"heapPolicyChanged0","(Ljava/lang/Object;)V",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_heapPolicyChanged0},
        {"jvmtiDefinitionCompletion0","([Ljava/lang/Class;)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_jvmtiDefinitionCompletion0},
        {"jvmtiDefinitionsPending0","(Ljava/lang/Module;)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_jvmtiDefinitionsPending0},
        {"heapEventPermit0","(Ljava/lang/Object;Ljava/lang/Class;)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_heapEventPermit0},
        {"codeState0","(Ljava/lang/Module;)[J",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_codeState0},
        {"retireCode0","(Ljava/lang/Module;)V",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_retireCode0},
        {"codeRetirementFramePermit0","(Ljava/lang/Class;ILjava/lang/Object;Ljava/lang/Module;)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_codeRetirementFramePermit0},
        {"preparedClass0","(Ljava/lang/Class;)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_preparedClass0},
        {"sourceCapture0","()Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_sourceCapture0},
        {"sourceQueryStopped0","()Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_sourceQueryStopped0},
        {"networkBoundaryDepth0","(Ljava/lang/Class;Ljava/lang/String;)I",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_networkBoundaryDepth0},
        {"networkCleanupBoundary0","(Ljava/lang/Class;Ljava/lang/String;II)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_networkCleanupBoundary0},
        {"networkReadField0","(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Z)Ljava/lang/Object;",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_networkReadField0},
        {"networkField0","(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/reflect/Field;",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_networkField0},
        {"carrierFields0","(Ljava/lang/Object;)[[Ljava/lang/Object;",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_carrierFields0},
        {"carrierRead0","(Ljava/lang/Object;Ljava/lang/reflect/Field;)[Ljava/lang/Object;",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_carrierRead0},
        {"carrierMethod0","(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;ILjava/lang/Class;)Ljava/lang/Class;",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_carrierMethod0},
        {"carrierMethodImage0","(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;)[J",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_carrierMethodImage0},
        {"carrierFrame0","(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;I)[J",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_carrierFrame0},
        {"carrierCallSite0","(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;ILjava/lang/Class;Ljava/lang/String;Ljava/lang/String;I)[J",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_carrierCallSite0},
        {"carrierStaticValue0","(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;)[Ljava/lang/Object;",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_carrierStaticValue0},
        {"carrierCallbackDepth0","(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;)I",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_carrierCallbackDepth0},
        {"carrierCallback0","(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;ILjava/lang/Class;Ljava/lang/String;Ljava/lang/String;I)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_carrierCallback0},
        {"arrayCopyInternalBoundary0","()Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_arrayCopyInternalBoundary0},
        {"arrayCopyReadBoundary0","(Ljava/lang/Object;II)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_arrayCopyReadBoundary0},
        {"arrayCopyBoundary0","(Ljava/lang/Object;IILjava/lang/Object;IILjava/lang/Object;)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_arrayCopyBoundary0},
        {"fileOperationBoundary0","(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Module;IJJ)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_fileOperationBoundary0},
        {"mappingDuplicateFrame0","([JI)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_mappingDuplicateFrame0},
        {"fileOperationFinishing0","(Ljava/lang/Object;)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_fileOperationFinishing0},
        {"ioOperationBoundary0","(Ljava/lang/Class;Ljava/lang/String;[J[Ljava/lang/Object;[Ljava/lang/Module;)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_ioOperationBoundary0},
        {"fileBindings0","()I",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_fileBindings0},
        {"fileBindingState0","()[J",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_fileBindingState0},
        {"bufferAllocationBoundary0","()Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_bufferAllocationBoundary0},
        {"bufferAddressBoundary0","(Ljava/lang/Object;[Ljava/lang/Module;)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_bufferAddressBoundary0},
        {"bindBufferStorage0","(Ljava/nio/Buffer;Ljava/lang/Object;JJ)[J",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_bindBufferStorage0},
        {"observeBufferStorage0","(Ljava/nio/Buffer;Ljava/lang/Object;JJ)[J",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_observeBufferStorage0},
        {"bufferStorageUse0","(Ljava/nio/Buffer;JJJJZ)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_bufferStorageUse0},
        {"exposeBufferStorage0","(Ljava/nio/Buffer;JJJJ[Ljava/lang/Module;)V",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_exposeBufferStorage0},
        {"restoreBufferStorage0","(Ljava/lang/Object;JJ[Ljava/lang/Module;JI)[J",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_restoreBufferStorage0},
        {"ioBufferWrite0","(Ljava/lang/Object;Ljava/lang/Object;JJJJ[Ljava/lang/Module;)J",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_ioBufferWrite0},
        {"ioBufferWritten0","(Ljava/lang/Object;JJZ)V",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_ioBufferWritten0},
        {"ioReadVector0","(Ljava/lang/Object;JIZ)[J",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_ioReadVector0},
        {"threadOperationBoundary0","(Ljava/lang/Thread;[Ljava/lang/Module;I)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_threadOperationBoundary0},
        {"threadOperationFinishing0","(Ljava/lang/Object;Z)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_threadOperationFinishing0},
        {"threadBindings0","()I",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_threadBindings0},
        {"libraryBegin0","(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/Class;)J",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_libraryBegin0},
        {"libraryUnloadBegin0","(Ljava/lang/Object;JLjava/lang/Class;ZZ)J",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_libraryUnloadBegin0},
        {"libraryUnloader0","(Ljava/lang/Object;Ljava/lang/Object;J)V",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_libraryUnloader0},
        {"libraryActivate0","()Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_libraryActivate0},
        {"libraryPresent0","()Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_libraryPresent0},
        {"libraryEnd0","(JJZ)V",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_libraryEnd0},
        {"module0","(Ljava/lang/Module;Z)V",(void*)host_module0},
        {"processPrepare0","(Ljava/lang/Class;)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_processPrepare0},
        {"processCreateBegin0","([J)J",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_processCreateBegin0},
        {"processCreateEnd0","(JJ[J)V",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_processCreateEnd0},
        {"processConstructed0","(Ljava/lang/Process;J)J",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_processConstructed0},
        {"processExposed0","(Ljava/lang/Process;J[Ljava/lang/Module;)V",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_processExposed0},
        {"processRetire0","(Ljava/lang/Process;J)Ljava/lang/String;",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_processRetire0},
        {"hostReady0","()Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_hostReady0},
        {"hostLibraryLoadReady0","()Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_hostLibraryLoadReady0},
        {"hostKernelProtect0","(Ljava/lang/Module;Z)V",(void*)host_kernel_protect0},
        {"hostKernelOwnerProtect0","(Z)V",(void*)host_kernel_owner_protect0},
        {"hostKernelFile0","(Ljava/io/FileDescriptor;[Ljava/lang/Module;)Ljava/lang/String;",(void*)host_kernel_file0},
        {"hostKernelState0","(Ljava/lang/Module;)[J",(void*)host_kernel_state0},
        {"hostState0","(Ljava/lang/Module;)[J",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_hostState0},
        {"status0","(Ljava/lang/Module;)[J",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_status0},
        {"jvmtiBoundary0","(Ljava/lang/Module;Ljava/lang/ClassLoader;Ljava/lang/String;Ljava/lang/Class;Ljava/security/ProtectionDomain;[B[B[Ljava/lang/Module;Z)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_jvmtiBoundary0},
        {"jvmtiState0","(Ljava/lang/Module;)[J",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_jvmtiState0},
        {"bindingControlled0","(Ljava/lang/reflect/Method;)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_bindingControlled0},
        {"clearField0","(Ljava/lang/reflect/Field;Ljava/lang/Object;Ljava/lang/Object;)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_clearField0},
        {"restoreField0","(Ljava/lang/reflect/Field;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_restoreField0},
        {"restoreArray0","(Ljava/lang/Object;ILjava/lang/Object;Ljava/lang/Object;)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_restoreArray0},
        {"activeClass0","()Ljava/lang/Class;",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_activeClass0},
        {"definedClass0","(Ljava/lang/Class;Ljava/lang/Class;)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_definedClass0},
        {"definitionBegin0","(Ljava/lang/Class;Ljava/lang/ClassLoader;[B)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_definitionBegin0},
        {"definitionEnd0","(Ljava/lang/Class;)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_definitionEnd0},
        {"createdObject0","(Ljava/lang/Class;Ljava/lang/Object;)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_createdObject0},
        {"memoryMutationBoundary0","(Ljava/lang/Object;)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_memoryMutationBoundary0},
        {"unsafeMutationBoundary0","(Ljava/lang/Object;JJLjava/lang/String;ZZ)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_unsafeMutationBoundary0},
        {"unsafeControlScope0","()Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_unsafeControlScope0},
        {"memoryWriteBegin0","(JJ[Ljava/lang/Module;ZZ)J",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_memoryWriteBegin0},
        {"memoryWriteEnd0","(JZ)V",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_memoryWriteEnd0},
        {"memoryReadSources0","(J)[Ljava/lang/Module;",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_memoryReadSources0},
        {"memoryWriteContributors0","(J[Ljava/lang/Module;)V",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_memoryWriteContributors0},
        {"restoreMemory0","([Ljava/lang/Module;JJI)[J",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_restoreMemory0},
        {"unsafeCopyBoundary0","(Ljava/lang/Object;JLjava/lang/Object;JJ)Z",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_unsafeCopyBoundary0},
        {"memoryState0","(Ljava/lang/Module;)[J",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_memoryState0},
        {"clone0","(Ljava/lang/Object;)Ljava/lang/Object;",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_clone0},
    };
    return native_original.RegisterNatives(env,controller,methods,(jint)(sizeof(methods)/sizeof(methods[0])))==JNI_OK
            &&!native_original.ExceptionCheck(env);
}
