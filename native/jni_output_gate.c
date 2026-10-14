/* Only a real, already attached current JNI environment can submit a result.
 * Heap/GC callbacks cannot enter JNI, attach a thread or wait on this path. */
static int native_jni_output_context(JNIEnv *env){
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    if(native_tooling_restricted())return 0;
    JNIEnv *actual=NULL;
    if(!native_vm_original.GetEnv||native_vm_original.GetEnv(native_vm,(void**)&actual,JNI_VERSION_1_8)!=JNI_OK||!actual)return 0;
    if(actual!=env){native_refuse(actual,"RONOVA_CURRENT_JNI_ENVIRONMENT_REQUIRED");return 0;}
    if(!native_thread_jni_observed(state,actual)){
        native_refuse(actual,"RONOVA_JNI_OUTPUT_CONTEXT_UNAVAILABLE");return 0;
    }
    return 1;
}

/* The VM wrote only private scratch. The gate contains no VM transition or
 * outside callback, and is also used by the exact allocation retirements. */
static int native_jni_output_commit(JNIEnv *env,void *target,const void *source,SIZE_T bytes){
    if(native_original.ExceptionCheck(env))return 0;
    if(!bytes)return 1;
    NativeToolingOutputUse use={0};
    use.restricted=native_tooling_restricted();
    NativeToolingOutputSpan span={target,source,bytes};
    jvmtiError result=native_tooling_output_commit(&use,&span,1);
    if(result==JVMTI_ERROR_NONE)return 1;
    native_refuse(env,result==JVMTI_ERROR_ACCESS_DENIED?"RONOVA_PROTECTED_NATIVE_OUTPUT":
            result==JVMTI_ERROR_NOT_AVAILABLE?"RONOVA_JNI_OUTPUT_UNAVAILABLE":"RONOVA_JNI_OUTPUT_WRITE_FAILED");
    return 0;
}
