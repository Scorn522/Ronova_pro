/* Actual java.lang.Thread JNI entries, including direct reflective/JNI calls. */
static jclass native_thread_class;
static jobject native_thread_deferred;
static jmethodID native_thread_begin_query,native_thread_end_query;
static struct {const char *name,*signature,*vmEntry;jmethodID method;} native_thread_methods[]={
    {"start0","()V","JVM_StartThread",NULL},
    {"interrupt0","()V","JVM_Interrupt",NULL},
    {"stop0","(Ljava/lang/Object;)V","JVM_StopThread",NULL},
    {"suspend0","()V","JVM_SuspendThread",NULL},
    {"resume0","()V","JVM_ResumeThread",NULL},
    {"setPriority0","(I)V","JVM_SetThreadPriority",NULL},
    {"setNativeName","(Ljava/lang/String;)V","JVM_SetNativeThreadName",NULL}
};
#define NATIVE_THREAD_METHODS (sizeof(native_thread_methods)/sizeof(native_thread_methods[0]))
static int native_thread_vm_target(jmethodID method,void *target){
    if(!code_vm_layout.image)return 0;
    for(unsigned i=0;i<NATIVE_THREAD_METHODS;i++)if(native_thread_methods[i].method==method)
        return target==(void*)GetProcAddress(code_vm_layout.image,native_thread_methods[i].vmEntry);
    return 0;
}
static void native_thread_classify(JNIEnv *env,jclass type,Binding *binding){
    if(!native_thread_class||!native_original.IsSameObject(env,type,native_thread_class))return;
    for(unsigned i=0;i<NATIVE_THREAD_METHODS;i++)if(native_thread_methods[i].method==binding->method){binding->threadOperation=i+1;return;}
}
static int native_thread_prepare(JNIEnv *env){
    NativeThread *state=native_thread();if(!state)return 0;state->control++;
    jclass type=native_original.FindClass(env,"java/lang/Thread");
    if(type&&native_bootstrap_class(env,type,"Ljava/lang/Thread;"))native_thread_class=(jclass)native_original.NewGlobalRef(env,type);
    if(type)native_original.DeleteLocalRef(env,type);int ready=native_thread_class!=NULL;
    for(unsigned i=0;i<NATIVE_THREAD_METHODS&&ready;i++){
        jmethodID method=native_original.GetMethodID(env,native_thread_class,native_thread_methods[i].name,native_thread_methods[i].signature);jint modifiers=0;
        ready=method&&(*native_ti)->GetMethodModifiers(native_ti,method,&modifiers)==JVMTI_ERROR_NONE&&(modifiers&0x0100)&&!(modifiers&0x0008);
        if(ready)native_thread_methods[i].method=method;
    }
    if(ready){
        native_thread_begin_query=native_original.GetStaticMethodID(env,native_tasks,"nativeThreadOperation","(Ljava/lang/Thread;[Ljava/lang/Module;I)Ljava/lang/Object;");
        native_thread_end_query=native_original.GetStaticMethodID(env,native_tasks,"nativeThreadOperationFinished","(Ljava/lang/Object;Z)V");
        jclass booleanType=native_original.FindClass(env,"java/lang/Boolean");
        jfieldID field=booleanType?native_original.GetStaticFieldID(env,booleanType,"FALSE","Ljava/lang/Boolean;"):NULL;
        if(field)native_thread_deferred=native_file_keep(env,native_original.GetStaticObjectField(env,booleanType,field));
        if(booleanType)native_original.DeleteLocalRef(env,booleanType);
        ready=native_thread_begin_query&&native_thread_end_query&&native_thread_deferred&&!native_original.ExceptionCheck(env);
    }
    state->control--;return ready;
}
JNIEXPORT jint JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_threadBindings0(JNIEnv *env,jclass controller){
    (void)controller;
    if(!native_jni_ready||!native_control_gate||!native_original.CallStaticBooleanMethod(env,native_controller,native_control_gate)){
        native_refuse(env,"NATIVE_THREAD_INSTALL_AGENT_REQUIRED");return -1;
    }
    NativeThread *state=native_thread();if(!state)return -1;state->control++;jint remaining=0;
    for(unsigned i=0;i<NATIVE_THREAD_METHODS&&!native_original.ExceptionCheck(env);i++){
        if(!native_thread_methods[i].method){remaining++;continue;}
        remaining+=native_binding_seed_one(env,state,native_thread_class,native_thread_methods[i].method,
                native_thread_methods[i].name,native_thread_methods[i].signature,2);
    }
    state->control--;return remaining;
}
static void native_thread_clear(JNIEnv *env,NativeThreadFrame *use){
    if(use->receiver)native_original.DeleteGlobalRef(env,use->receiver);
    if(use->sources)native_original.DeleteGlobalRef(env,use->sources);
    if(use->token){if(use->tokenGlobal)native_original.DeleteGlobalRef(env,use->token);else native_original.DeleteLocalRef(env,use->token);}
    memset(use,0,sizeof(*use));
}
static void native_thread_refuse(JNIEnv *env,Binding *binding){
    if(native_original.ExceptionCheck(env))return;
    jclass type=native_original.FindClass(env,binding->threadOperation==1?"java/lang/IllegalThreadStateException":"java/lang/SecurityException");
    if(type){native_original.ThrowNew(env,type,"RONOVA_NATIVE_THREAD_OPERATION_REFUSED");native_original.DeleteLocalRef(env,type);}
}
static int native_thread_begin(JNIEnv *env,NativeThread *state,Binding *binding,jobject receiver){
    NativeThreadFrame *use=&state->threadFrames[state->depth-1];
    if(native_stopped(env)){native_thread_refuse(env,binding);return 0;}
    state->control++;use->operation=(jint)binding->threadOperation;use->receiver=native_original.NewGlobalRef(env,receiver);
    if(!native_original.ExceptionCheck(env))use->sources=(jobjectArray)native_file_keep(env,native_mutation_sources(env,state));
    int ready=use->receiver&&use->sources&&!native_original.ExceptionCheck(env);
    if(ready){
        NativeThreadFrame *previous=state->threadPermit;state->threadPermit=use;
        use->token=native_original.CallStaticObjectMethod(env,native_tasks,native_thread_begin_query,receiver,use->sources,use->operation);
        state->threadPermit=previous;ready=!native_original.ExceptionCheck(env);
        if(ready&&use->token){
            use->skipped=native_original.IsSameObject(env,use->token,native_thread_deferred);
            jobject global=native_original.NewGlobalRef(env,use->token);
            if(global){native_original.DeleteLocalRef(env,use->token);use->token=global;use->tokenGlobal=1;}else ready=0;
        }
    }
    // A failed global retention still owns its local token and must release the
    // Java start/interrupt window before returning the original exception.
    if(!ready)native_thread_finish(env,state,use,0);
    state->control--;return ready;
}
static void JNICALL native_thread_skip(JNIEnv *env,jobject receiver){(void)env;(void)receiver;}
static void *native_thread_call(NativeThread *state,Binding *effective){
    return state->threadFrames[state->depth-1].skipped?(void*)native_thread_skip:effective->original;
}
static void native_thread_finish(JNIEnv *env,NativeThread *state,NativeThreadFrame *use,int completed){
    if(!use->operation)return;state->control++;
    if(use->token){
        jthrowable failure=native_original.ExceptionOccurred(env);if(failure)native_original.ExceptionClear(env);
        NativeThreadFrame *previous=state->threadPermit;state->threadPermit=use;use->finishing=1;use->completed=completed;
        native_original.CallStaticVoidMethod(env,native_tasks,native_thread_end_query,use->token,completed?JNI_TRUE:JNI_FALSE);state->threadPermit=previous;
        if(failure){
            if(native_original.ExceptionCheck(env)){native_original.ExceptionClear(env);InterlockedIncrement(&native_failures);}
            native_original.Throw(env,failure);native_original.DeleteLocalRef(env,failure);
        }
    }
    native_thread_clear(env,use);state->control--;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_threadOperationBoundary0(JNIEnv *env,jclass type,jobject receiver,jobjectArray sources,jint operation){
    (void)type;NativeThread *state=native_thread();NativeThreadFrame *use=state?state->threadPermit:NULL;
    return use&&!use->finishing&&use->operation==operation&&native_original.IsSameObject(env,use->receiver,receiver)
            &&native_original.IsSameObject(env,use->sources,sources)?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_threadOperationFinishing0(JNIEnv *env,jclass type,jobject token,jboolean completed){
    (void)type;NativeThread *state=native_thread();NativeThreadFrame *use=state?state->threadPermit:NULL;
    return use&&use->finishing&&use->completed==!!completed&&token&&native_original.IsSameObject(env,use->token,token)?JNI_TRUE:JNI_FALSE;
}
