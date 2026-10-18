/* Included in bindings.c in place of the two definition-operation wrappers.
 * ClassFileLoadHook supplies a candidate image, not VM acceptance. Only a
 * successful original RedefineClasses/RetransformClasses queues these exact
 * classes for the existing external-definition continuation.
 * https://docs.oracle.com/en/java/javase/17/docs/specs/jvmti.html#RetransformClasses
 */
typedef struct NativeToolingDefinitionCall {
    NativeToolingEnv *environment;NativeThread *thread;
    jclass *classes;unsigned char **images;jvmtiClassDefinition *definitions;
    jobjectArray targets;OwnerLink *owners;jint count;
    int ready,success,interrupted,unknown,phase;
    struct NativeToolingDefinitionCall *previous,*next;
} NativeToolingDefinitionCall;

static NativeToolingDefinitionCall *native_tooling_definitions_pending;

static int native_tooling_definitions_context(NativeThread *state){
    if(!state||state->heapCallback||state->vmThreadEnding||state->vmDetaching||state->javaCleaning)return 0;
    /* These APIs are not Callback Safe. In particular, a GC/ObjectFree event
     * must not gain a JNI query merely because this wrapper was called. The
     * agent-thread start function is ordinary agent execution, not an event. */
    for(NativeToolingInvocation *p=state->toolingInvocation;p;p=p->previous)
        if(p->slot&&!p->slot->agent)return 0;
    return 1;
}
static JNIEnv *native_tooling_definitions_env(NativeThread *state){
    if(!native_tooling_definitions_context(state)||!native_vm_original.GetEnv||!native_original.NewGlobalRef)return NULL;
    JNIEnv *env=NULL;
    return native_vm_original.GetEnv(native_vm,(void**)&env,JNI_VERSION_1_8)==JNI_OK?env:NULL;
}
static void native_tooling_definitions_thread_end(NativeThread *state){
    for(NativeToolingDefinitionCall *p=state->definitionCall;p;p=p->previous)p->interrupted=1;
}
static int native_tooling_definitions_has_globals(const NativeToolingDefinitionCall *call){
    if(call->targets)return 1;
    if(call->classes)for(jint i=0;i<call->count;i++)if(call->classes[i])return 1;
    return 0;
}
static void native_tooling_definitions_free(JNIEnv *env,NativeToolingDefinitionCall *call){
    if(call->targets)native_original.DeleteGlobalRef(env,call->targets);
    if(call->classes)for(jint i=0;i<call->count;i++)if(call->classes[i])native_original.DeleteGlobalRef(env,call->classes[i]);
    if(call->images)for(jint i=0;i<call->count;i++)free(call->images[i]);
    free(call->classes);free(call->images);free(call->definitions);native_owner_links_free(call->owners);free(call);
}
static int native_tooling_definitions_read(const void *source,void *target,size_t length){
    SIZE_T copied=0;return !length||source&&host_real_read_process_memory(GetCurrentProcess(),source,target,length,&copied)&&copied==length;
}
static int native_tooling_definitions_snapshot(JNIEnv *env,NativeToolingDefinitionCall *call,
        const jvmtiClassDefinition *definitions,const jclass *types,int redefine,void *caller){
    size_t count=(size_t)call->count;
    if(!count||count>SIZE_MAX/sizeof(jvmtiClassDefinition)||count>SIZE_MAX/sizeof(jclass))return 0;
    call->classes=calloc(count,sizeof(*call->classes));
    jclass *input=calloc(count,sizeof(*input));
    if(!call->classes||!input){free(input);return 0;}
    if(redefine){
        call->definitions=calloc(count,sizeof(*call->definitions));call->images=calloc(count,sizeof(*call->images));
        if(!call->definitions||!call->images||!native_tooling_definitions_read(definitions,call->definitions,count*sizeof(*definitions))){free(input);return 0;}
        for(jint i=0;i<call->count;i++)input[i]=call->definitions[i].klass;
    }else if(!native_tooling_definitions_read(types,input,count*sizeof(*input))){free(input);return 0;}
    int ready=1;
    for(jint i=0;i<call->count&&ready;i++){
        jint status=0;
        /* Invalid original input still goes to the original API unchanged. Do
         * not pass an invalid JNI handle to a reference-retention operation. */
        ready=input[i]&&call->environment->original->GetClassStatus(call->environment->api,input[i],&status)==JVMTI_ERROR_NONE;
        if(ready){call->classes[i]=(jclass)native_original.NewGlobalRef(env,input[i]);ready=call->classes[i]!=NULL&&!native_original.ExceptionCheck(env);}
        if(ready&&redefine){
            jvmtiClassDefinition *item=&call->definitions[i];item->klass=call->classes[i];
            if(item->class_byte_count>0&&item->class_bytes){
                size_t length=(size_t)item->class_byte_count;call->images[i]=malloc(length);
                ready=call->images[i]&&native_tooling_definitions_read(item->class_bytes,call->images[i],length);
                if(ready)item->class_bytes=call->images[i];
            }
        }
    }
    free(input);if(!ready)return 0;
    jclass type=native_original.FindClass(env,"java/lang/Class");
    jobjectArray targets=type?native_original.NewObjectArray(env,call->count,type,NULL):NULL;
    if(type)native_original.DeleteLocalRef(env,type);
    for(jint i=0;targets&&i<call->count&&!native_original.ExceptionCheck(env);i++)native_original.SetObjectArrayElement(env,targets,i,call->classes[i]);
    if(targets&&!native_original.ExceptionCheck(env))call->targets=(jobjectArray)native_original.NewGlobalRef(env,targets);
    if(targets)native_original.DeleteLocalRef(env,targets);
    if(!call->targets||native_original.ExceptionCheck(env))return 0;
    if(!native_tooling_capture(env,caller,&call->owners,NULL,&call->unknown)||!native_owner_merge(&call->owners,call->environment->owners))call->unknown=1;
    call->unknown|=call->environment->unknown;
    call->ready=1;return 1;
}
static void native_tooling_definitions_exception(JNIEnv *env,jthrowable failure){
    if(native_original.ExceptionCheck(env))native_original.ExceptionClear(env);
    if(failure){native_original.Throw(env,failure);native_original.DeleteLocalRef(env,failure);}
}
static int native_tooling_definitions_notify(JNIEnv *env,NativeThread *state,NativeToolingDefinitionCall *call){
    if(!call->success||!call->ready||!native_tooling_definitions_context(state)||!native_tooling_definitions_query)return 0;
    jthrowable failure=native_original.ExceptionOccurred(env);if(failure)native_original.ExceptionClear(env);
    NativeToolingDefinitionCall *previous=state->definitionCall;call->previous=previous;state->definitionCall=call;
    call->thread=state;call->phase=1;call->interrupted=0;native_thread_callback_enter(state);state->control++;
    /* Refill the private array from the immutable native target references.
     * The JNI boundary also compares every element, not merely this array id. */
    for(jint i=0;i<call->count&&!native_original.ExceptionCheck(env);i++)native_original.SetObjectArrayElement(env,call->targets,i,call->classes[i]);
    jboolean accepted=!native_original.ExceptionCheck(env)?native_original.CallStaticBooleanMethod(env,native_controller,native_tooling_definitions_query,call->targets):JNI_FALSE;
    int ready=accepted&&call->phase==2&&!call->interrupted&&!native_original.ExceptionCheck(env);
    native_tooling_definitions_exception(env,failure);state->control--;state->definitionCall=previous;call->previous=NULL;call->thread=NULL;
    native_thread_callback_leave(state);return ready;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_jvmtiDefinitionCompletion0(JNIEnv *env,jclass type,jobjectArray targets){
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    NativeToolingDefinitionCall *call=state?state->definitionCall:NULL;
    if(!call||call->thread!=state||!call->success||!call->ready||call->phase!=1||call->interrupted
            ||!native_tooling_definitions_context(state)||!native_original.IsSameObject(env,type,native_controller)
            ||!native_original.IsSameObject(env,targets,call->targets)||native_original.GetArrayLength(env,targets)!=call->count)return JNI_FALSE;
    for(jint i=0;i<call->count;i++){
        jobject actual=native_original.GetObjectArrayElement(env,targets,i);
        int same=actual&&native_original.IsSameObject(env,actual,call->classes[i]);
        if(actual)native_original.DeleteLocalRef(env,actual);if(!same||native_original.ExceptionCheck(env))return JNI_FALSE;
    }
    call->phase=2;return JNI_TRUE;
}
static void native_tooling_definitions_queue(NativeToolingDefinitionCall *call){
    call->thread=NULL;call->previous=NULL;call->phase=0;
    AcquireSRWLockExclusive(&native_tooling_records);call->next=native_tooling_definitions_pending;native_tooling_definitions_pending=call;
    ReleaseSRWLockExclusive(&native_tooling_records);
    if(native_tooling_retirement_event)SetEvent(native_tooling_retirement_event);
}
static int native_tooling_definitions_pending_any(void){
    AcquireSRWLockShared(&native_tooling_records);int pending=native_tooling_definitions_pending!=NULL;ReleaseSRWLockShared(&native_tooling_records);return pending;
}
static void native_tooling_definitions_reap(JNIEnv *env){
    if(!env||native_tooling_shutdown)return;NativeThread *state=native_thread();if(!state)return;
    native_thread_callback_enter(state);
    /* Keep actual successful work visible to retirement queries until its
     * Java continuation has accepted it. Only this daemon removes nodes;
     * producers may prepend new work, which is handled on a later wake. */
    AcquireSRWLockShared(&native_tooling_records);NativeToolingDefinitionCall *calls=native_tooling_definitions_pending;ReleaseSRWLockShared(&native_tooling_records);
    while(calls&&!native_tooling_shutdown){
        if(native_tooling_definitions_env(state)!=env)break;
        NativeToolingDefinitionCall *call=calls;
        AcquireSRWLockShared(&native_tooling_records);calls=call->next;ReleaseSRWLockShared(&native_tooling_records);
        int released=!call->success||!call->ready||native_tooling_definitions_notify(env,state,call);
        if(released){
            AcquireSRWLockExclusive(&native_tooling_records);
            NativeToolingDefinitionCall **link=&native_tooling_definitions_pending;
            while(*link&&*link!=call)link=&(*link)->next;if(*link==call)*link=call->next;
            ReleaseSRWLockExclusive(&native_tooling_records);native_tooling_definitions_free(env,call);
        }else InterlockedIncrement(&native_tooling_failures);
    }
    native_thread_callback_leave(state);
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_jvmtiDefinitionsPending0(JNIEnv *env,jclass type,jobject module){
    if(!native_controller||!native_original.IsSameObject(env,type,native_controller)
            ||!native_original.CallStaticBooleanMethod(env,native_controller,native_control_gate))return JNI_TRUE;
    int pending=0;AcquireSRWLockShared(&native_tooling_records);
    for(NativeToolingDefinitionCall *call=native_tooling_definitions_pending;call&&!pending;call=call->next)
        if(call->success&&call->ready)pending=call->unknown||native_owner_matches(env,call->owners,module)||native_owner_matches(env,call->environment->owners,module);
    ReleaseSRWLockShared(&native_tooling_records);return pending?JNI_TRUE:JNI_FALSE;
}
static int native_tooling_definitions_stopped(NativeThread *state){
    if(!state)return 0;
    for(unsigned i=0;i<state->depth;i++)if(native_owner_stopped(state->stack[i]->owners))return 1;
    for(NativeLibraryScope *p=state->library;p;p=p->previous)if(native_owner_stopped(p->library->owners))return 1;
    for(NativeToolingScope *p=state->tooling;p;p=p->previous)if(native_owner_stopped(p->owners))return 1;
    return 0;
}
static jvmtiError native_tooling_definition_operation(NativeToolingEnv *record,jint count,
        const jvmtiClassDefinition *definitions,const jclass *types,int redefine,void *caller){
    DWORD incoming=GetLastError();
    NativeThread *state=native_thread();JNIEnv *env=native_tooling_definitions_env(state);
    jthrowable failure=env?native_original.ExceptionOccurred(env):NULL;if(failure)native_original.ExceptionClear(env);
    int stopped=native_owner_stopped(record->owners)||(env?native_tooling_stopped(env,caller):native_tooling_definitions_stopped(state));
    NativeToolingDefinitionCall *call=!stopped&&env&&count>0?calloc(1,sizeof(*call)):NULL;
    if(call){call->environment=record;call->count=count;call->thread=state;call->previous=state->definitionCall;state->definitionCall=call;native_thread_callback_enter(state);state->control++;
        native_tooling_definitions_snapshot(env,call,definitions,types,redefine,caller);
        native_tooling_definitions_exception(env,failure);failure=NULL;state->control--;
    }else if(env){native_tooling_definitions_exception(env,failure);failure=NULL;}
    if(stopped)return native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);
    int savedReady=call&&call->ready;
    SetLastError(incoming);
    jvmtiError result=redefine?record->original->RedefineClasses(record->api,count,call&&call->ready?call->definitions:definitions)
            :record->original->RetransformClasses(record->api,count,call&&call->ready?call->classes:types);
    DWORD error=GetLastError();
    if(call){NativeToolingDefinitionCall *previous=call->previous;state->definitionCall=previous;call->previous=NULL;
        JNIEnv *current=native_tooling_definitions_env(state);int same=current&&current==env&&!call->interrupted;
        call->success=result==JVMTI_ERROR_NONE;
        if(call->success&&call->ready){
            if(same&&native_tooling_definitions_notify(current,state,call))native_tooling_definitions_free(current,call);
            else {InterlockedIncrement(&native_tooling_failures);native_tooling_definitions_queue(call);}
        }else if(current)native_tooling_definitions_free(current,call);
        else if(native_tooling_definitions_has_globals(call)){
            /* Strong refs obtained in a legitimate pre-call JNI context can be
             * released by the daemon; failed calls never become notifications. */
            native_tooling_definitions_queue(call);
        }else native_tooling_definitions_free(NULL,call);
        native_thread_callback_leave(state);
    }
    if(result==JVMTI_ERROR_NONE&&count>0&&!savedReady){
        /* A real commit without a complete, legally retained target snapshot
         * remains an attribution/coverage gap. Borrowed handles cannot be
         * handed to a different JNI thread as a fabricated continuation. */
        InterlockedIncrement(&native_tooling_failures);AcquireSRWLockExclusive(&native_tooling_records);record->unknown=1;ReleaseSRWLockExclusive(&native_tooling_records);
    }
    SetLastError(error);return result;
}
static jvmtiError JNICALL native_tooling_redefine(jvmtiEnv *api,jint count,const jvmtiClassDefinition *definitions){
    DWORD incoming=GetLastError();
    NativeToolingEnv *record=native_tooling_environment(api);if(!record)return native_tooling_restricted()?JVMTI_ERROR_NOT_AVAILABLE:JVMTI_ERROR_INVALID_ENVIRONMENT;
    jvmtiError result;DWORD error;
    {TOOLING_USE(record);SetLastError(incoming);result=native_tooling_definition_operation(record,count,definitions,NULL,1,__builtin_return_address(0));error=GetLastError();}
    SetLastError(error);return result;
}
static jvmtiError JNICALL native_tooling_retransform(jvmtiEnv *api,jint count,const jclass *types){
    DWORD incoming=GetLastError();
    NativeToolingEnv *record=native_tooling_environment(api);if(!record)return native_tooling_restricted()?JVMTI_ERROR_NOT_AVAILABLE:JVMTI_ERROR_INVALID_ENVIRONMENT;
    jvmtiError result;DWORD error;
    {TOOLING_USE(record);SetLastError(incoming);result=native_tooling_definition_operation(record,count,NULL,types,0,__builtin_return_address(0));error=GetLastError();}
    SetLastError(error);return result;
}
