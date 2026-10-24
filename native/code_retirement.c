/* Included by code.c. A request is only an observation of a stopped owner's
 * actual thread/stack. The current event frame is independently selected again
 * before a VM return. No thread name, class-name prefix or task token grants it. */
typedef struct CodeRetirementOwner {
    Owner *owner;volatile LONG64 requested,popped,failures;struct CodeRetirementOwner *next;
} CodeRetirementOwner;
typedef struct CodeRetirement {
    jweak thread;OwnerLink *owners;volatile LONG busy,ended,active,armed,waiting,ownedSuspend,pendingForce,reason;
    jmethodID waitMethod;jint waitBottom;struct CodeRetirement *next;
} CodeRetirement;
enum {CODE_RETIRE_READY,CODE_RETIRE_CLEANUP,CODE_RETIRE_FRAMEWORK,CODE_RETIRE_NATIVE_SCOPE,CODE_RETIRE_SOURCE_SCOPE,CODE_RETIRE_VM};
static SRWLOCK code_retirement_records=SRWLOCK_INIT;
static CodeRetirement *code_retirements;
static CodeRetirementOwner *code_retirement_owners;
static volatile LONG code_suspend_available,code_force_available,code_step_available,code_retirement_onload;

static void code_retirement_initialize(void){
    jvmtiPhase phase=JVMTI_PHASE_DEAD;jvmtiCapabilities possible,capability,actual;
    memset(&possible,0,sizeof(possible));memset(&actual,0,sizeof(actual));
    if((*native_ti)->GetPhase(native_ti,&phase)==JVMTI_ERROR_NONE)
        InterlockedExchange(&code_retirement_onload,phase==JVMTI_PHASE_ONLOAD);
    if((*native_ti)->GetPotentialCapabilities(native_ti,&possible)==JVMTI_ERROR_NONE){
        // Ask independently. A VM which withholds one live-phase capability
        // must not make already available observations disappear with it.
        if(possible.can_suspend){memset(&capability,0,sizeof(capability));capability.can_suspend=1;(*native_ti)->AddCapabilities(native_ti,&capability);}
        if(possible.can_force_early_return){memset(&capability,0,sizeof(capability));capability.can_force_early_return=1;(*native_ti)->AddCapabilities(native_ti,&capability);}
        if(possible.can_generate_single_step_events){memset(&capability,0,sizeof(capability));capability.can_generate_single_step_events=1;(*native_ti)->AddCapabilities(native_ti,&capability);}
    }
    if((*native_ti)->GetCapabilities(native_ti,&actual)==JVMTI_ERROR_NONE){
        InterlockedExchange(&code_suspend_available,actual.can_suspend);
        InterlockedExchange(&code_force_available,actual.can_force_early_return);
        InterlockedExchange(&code_step_available,actual.can_generate_single_step_events);
    }
    // PopFrame is intentionally not requested: it re-executes the caller's
    // invoke and emits no FramePop event for our real source cleanup.
}
static Owner *code_retirement_declared_identity(JNIEnv *env,CodeFrameIdentity *frame){
    Owner *owner=frame->module?native_owner_published(env,frame->module):NULL;
    return owner&&InterlockedCompareExchange(&owner->producer,0,0)?owner:NULL;
}
static Owner *code_retirement_declared(JNIEnv *env,jmethodID method){
    CodeFrameIdentity frame;code_frame_identity_open(env,method,&frame);
    Owner *owner=code_retirement_declared_identity(env,&frame);
    code_frame_identity_close(env,&frame);return owner;
}
static int code_retirement_candidate(JNIEnv *env,jmethodID method,jobject module){
    CodeFrameIdentity frame;code_frame_identity_open(env,method,&frame);
    Owner *declared=code_retirement_declared_identity(env,&frame);
    int selected=declared&&(!module||native_original.IsSameObject(env,declared->module,module))||code_frame_selected(env,&frame,module,0);
    code_frame_identity_close(env,&frame);return selected;
}
static int code_retirement_observed(JNIEnv *env,jmethodID method,jlocation location,jobject module,int *unknown){
    CodeFrameIdentity frame;code_frame_identity_open(env,method,&frame);
    Owner *declared=code_retirement_declared_identity(env,&frame);
    int selected=declared&&(!module||native_original.IsSameObject(env,declared->module,module));
    if(location>=0&&code_frame_selected(env,&frame,module,0)){
        OwnerLink *sources=NULL;int known=0,missing=0;
        if(!code_frame_sources_identity(env,&frame,location,&sources,&known,&missing,NULL))*unknown=1;
        else {if(sources&&native_owner_matches(env,sources,module))selected=1;if(missing)*unknown=1;}
        code_links_free(sources);
    }
    code_frame_identity_close(env,&frame);return selected;
}
static CodeRetirementOwner *code_retirement_owner(Owner *owner){
    CodeRetirementOwner *result=NULL;AcquireSRWLockExclusive(&code_retirement_records);
    for(result=code_retirement_owners;result&&result->owner!=owner;result=result->next);
    if(!result){result=(CodeRetirementOwner*)calloc(1,sizeof(*result));
        if(result){result->owner=owner;result->next=code_retirement_owners;
            InterlockedExchangePointer((void *volatile*)&code_retirement_owners,result);}}
    ReleaseSRWLockExclusive(&code_retirement_records);return result;
}
static void code_retirement_failure(CodeRetirement *request){
    InterlockedExchange(&request->reason,CODE_RETIRE_VM);
    // Published statistics are immutable except for their atomic counters and
    // never removed. The failure path may still own a suspension: it cannot
    // wait for an owner-table lock held by that selected thread.
    for(OwnerLink *entry=request->owners;entry;entry=entry->next){
        CodeRetirementOwner *owner=(CodeRetirementOwner*)InterlockedCompareExchangePointer((void *volatile*)&code_retirement_owners,NULL,NULL);
        for(;owner&&owner->owner!=entry->owner;owner=owner->next);
        if(owner)InterlockedIncrement64(&owner->failures);else InterlockedIncrement(&native_failures);
    }
}
static CodeRetirement *code_retirement_hold(JNIEnv *env,jthread thread){
    CodeRetirement *result=NULL;AcquireSRWLockExclusive(&code_retirement_records);
    for(CodeRetirement *entry=code_retirements;entry;entry=entry->next)
        if(!entry->ended&&native_original.IsSameObject(env,entry->thread,thread)){
            if(!entry->busy){InterlockedExchange(&entry->busy,1);result=entry;}break;
        }
    ReleaseSRWLockExclusive(&code_retirement_records);return result;
}
static void code_retirement_release(JNIEnv *env,CodeRetirement *request){
    int ended=0;AcquireSRWLockExclusive(&code_retirement_records);
    if(request->ended){CodeRetirement **slot=&code_retirements;while(*slot&&*slot!=request)slot=&(*slot)->next;
        if(*slot){*slot=request->next;ended=1;}}
    InterlockedExchange(&request->busy,0);ReleaseSRWLockExclusive(&code_retirement_records);
    if(ended){native_original.DeleteWeakGlobalRef(env,request->thread);code_links_free(request->owners);SecureZeroMemory(request,sizeof(*request));free(request);}
}
static int code_retirement_event(CodeRetirement *request,jthread thread,int enable){
    jvmtiError error=(*native_ti)->SetEventNotificationMode(native_ti,enable?JVMTI_ENABLE:JVMTI_DISABLE,JVMTI_EVENT_SINGLE_STEP,thread);
    if(error==JVMTI_ERROR_THREAD_NOT_ALIVE){InterlockedExchange(&request->ended,1);InterlockedExchange(&request->active,0);return 0;}
    if(error!=JVMTI_ERROR_NONE){code_retirement_failure(request);return 0;}
    InterlockedExchange(&request->armed,enable);return 1;
}
static int code_retirement_resume(CodeRetirement *request,jthread thread){
    if(!InterlockedCompareExchange(&request->ownedSuspend,0,0))return 1;
    jvmtiError error=(*native_ti)->ResumeThread(native_ti,thread);
    if(error==JVMTI_ERROR_NONE||error==JVMTI_ERROR_THREAD_NOT_ALIVE){InterlockedExchange(&request->ownedSuspend,0);
        if(error==JVMTI_ERROR_THREAD_NOT_ALIVE){InterlockedExchange(&request->ended,1);InterlockedExchange(&request->active,0);}return 1;}
    if(error==JVMTI_ERROR_THREAD_NOT_SUSPENDED){InterlockedExchange(&request->ownedSuspend,0);code_retirement_failure(request);return 0;}
    // No request ever resumes a suspension it did not create. A failed resume
    // keeps this exact receipt for the next production retirement attempt.
    code_retirement_failure(request);return 0;
}
static int code_retirement_native_scope(NativeThread *state){
    return state->depth||state->control||state->sourceCapture||state->unsafeInstalling||state->bufferAllocation
        ||state->heapCallback||state->toolingRestricted||state->arraycopyQuery||state->bindingSeed||state->unsafeMutation
        ||state->memoryWrite||state->unsafeInstall||state->arraycopy||state->defined||state->prepared||state->changedClass
        ||state->created||state->mutation||state->popped||state->changedHolder||state->bufferAddress||state->bufferSources
        ||state->unobservedLibrary||state->definition||state->library||state->registration||state->tooling
        ||state->toolingCallbackEnvironment||state->toolingImage||state->processScope||state->processCreated
        ||state->toolingInvocation||state->definitionCall||state->heapWorkerAnchor||state->hostWorker||state->hostLoad
        ||state->hostUnloadImage||state->hostRelease||state->vmDetaching||state->vmThreadEnding||state->javaCleaning;
}
static int code_retirement_wait(CodeRetirement *request,jthread thread,jmethodID method,jint bottom,int reason){
    jvmtiError error=(*native_ti)->NotifyFramePop(native_ti,thread,0);
    if(error!=JVMTI_ERROR_NONE){code_retirement_failure(request);code_retirement_event(request,thread,0);return 0;}
    request->waitMethod=method;request->waitBottom=bottom;InterlockedExchange(&request->waiting,1);
    InterlockedExchange(&request->reason,reason);return code_retirement_event(request,thread,0);
}
static int code_retirement_unbox(JNIEnv *env,jobject value,char result,jvalue *bits){
    memset(bits,0,sizeof(*bits));if(result=='V')return 1;
    if(result=='L'||result=='['){bits->l=value;return 1;}if(!value)return 0;
    const char *name=NULL,*signature=NULL;
    switch(result){case 'Z':name="booleanValue";signature="()Z";break;case 'B':name="byteValue";signature="()B";break;
        case 'C':name="charValue";signature="()C";break;case 'S':name="shortValue";signature="()S";break;case 'I':name="intValue";signature="()I";break;
        case 'J':name="longValue";signature="()J";break;case 'F':name="floatValue";signature="()F";break;case 'D':name="doubleValue";signature="()D";break;default:return 0;}
    jclass type=native_original.GetObjectClass(env,value);jmethodID method=type?native_original.GetMethodID(env,type,name,signature):NULL;
    if(method)switch(result){case 'Z':bits->i=native_original.CallBooleanMethod(env,value,method);break;case 'B':bits->i=native_original.CallByteMethod(env,value,method);break;
        case 'C':bits->i=native_original.CallCharMethod(env,value,method);break;case 'S':bits->i=native_original.CallShortMethod(env,value,method);break;case 'I':bits->i=native_original.CallIntMethod(env,value,method);break;
        case 'J':bits->j=native_original.CallLongMethod(env,value,method);break;case 'F':bits->f=native_original.CallFloatMethod(env,value,method);break;case 'D':bits->d=native_original.CallDoubleMethod(env,value,method);break;}
    if(type)native_original.DeleteLocalRef(env,type);return method&&!native_original.ExceptionCheck(env);
}
static jvmtiError code_retirement_return(jthread thread,char result,jvalue bits){
    switch(result){case 'V':return (*native_ti)->ForceEarlyReturnVoid(native_ti,thread);
        case 'Z':case 'B':case 'C':case 'S':case 'I':return (*native_ti)->ForceEarlyReturnInt(native_ti,thread,bits.i);
        case 'J':return (*native_ti)->ForceEarlyReturnLong(native_ti,thread,bits.j);
        case 'F':return (*native_ti)->ForceEarlyReturnFloat(native_ti,thread,bits.f);
        case 'D':return (*native_ti)->ForceEarlyReturnDouble(native_ti,thread,bits.d);
        case 'L':case '[':return (*native_ti)->ForceEarlyReturnObject(native_ti,thread,bits.l);
        default:return JVMTI_ERROR_TYPE_MISMATCH;}
}
static void code_retirement_force(JNIEnv *env,jthread thread,NativeThread *state,CodeRetirement *request,
        CodeRetirementOwner *owner,jmethodID method,jlocation location,jint bottom){
    jclass declaring=NULL;jobject module=NULL,value=NULL;jstring descriptor=NULL;
    char *name=NULL,*signature=NULL;CodePool pool={0};CodeVMMethod version={0};CodeExit *exit=NULL;
    int permanent=1,reason=CODE_RETIRE_SOURCE_SCOPE,ready=0;
    if((*native_ti)->GetMethodDeclaringClass(native_ti,method,&declaring)!=JVMTI_ERROR_NONE
            ||(*native_ti)->GetMethodName(native_ti,method,&name,&signature,NULL)!=JVMTI_ERROR_NONE
            ||!code_vm_snapshot(env,method,&version,&pool,name,signature))goto done;
    /* The actual declaring Mod is selected as a whole. A Plan, instruction
     * witness, leaf-body shape or individual field receipt grants no return. */
    module=native_original.GetModule(env,declaring);
    if(!module||!native_original.IsSameObject(env,module,owner->owner->module)||!InterlockedCompareExchange(&owner->owner->stopped,0,0)){
        reason=CODE_RETIRE_FRAMEWORK;goto done;
    }
    permanent=0;
    const char *end=strrchr(signature,')');if(!end||!end[1])goto done;char result=end[1];jvalue returned;
    if(result!='V'){
        descriptor=native_original.NewStringUTF(env,signature);if(!descriptor)goto done;
        state->control++;value=native_original.CallStaticObjectMethod(env,native_definitions,native_return_query,module,declaring,descriptor);state->control--;
    }
    if(native_original.ExceptionCheck(env)||!code_retirement_unbox(env,value,result,&returned))goto done;
    jmethodID actual=NULL;jlocation at=-1;jint frames=0;
    if(code_retirement_native_scope(state)||!code_vm_unchanged(method,&version)
            ||!InterlockedCompareExchange(&owner->owner->stopped,0,0)
            ||(*native_ti)->GetFrameCount(native_ti,thread,&frames)!=JVMTI_ERROR_NONE||frames!=bottom
            ||(*native_ti)->GetFrameLocation(native_ti,thread,0,&actual,&at)!=JVMTI_ERROR_NONE||actual!=method||at!=location)goto done;
    exit=(CodeExit*)calloc(1,sizeof(*exit));if(!exit)goto done;
    exit->method=method;exit->bottom=bottom;exit->retirementOwner=owner;
    if((*native_ti)->NotifyFramePop(native_ti,thread,0)!=JVMTI_ERROR_NONE)goto done;
    if(!code_retirement_event(request,thread,0))goto done;
    jvmtiError error=code_retirement_return(thread,result,returned);
    if(error!=JVMTI_ERROR_NONE){permanent=1;reason=CODE_RETIRE_VM;code_retirement_failure(request);goto done;}
    exit->previous=state->execution;state->execution=exit;exit=NULL;
    request->waitMethod=method;request->waitBottom=bottom;InterlockedExchange(&request->pendingForce,1);
    InterlockedExchange(&request->reason,CODE_RETIRE_READY);InterlockedIncrement64(&owner->requested);ready=1;
done:
    if(exit)free(exit);
    if(native_original.ExceptionCheck(env)){native_original.ExceptionClear(env);permanent=1;reason=CODE_RETIRE_VM;code_retirement_failure(request);}
    if(value)native_original.DeleteLocalRef(env,value);if(descriptor)native_original.DeleteLocalRef(env,descriptor);
    if(module)native_original.DeleteLocalRef(env,module);if(declaring)native_original.DeleteLocalRef(env,declaring);
    if(name)(*native_ti)->Deallocate(native_ti,(unsigned char*)name);if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);
    free(version.handlers);code_pool_free(&pool);
    if(!ready&&!request->ended){InterlockedExchange(&request->reason,reason);
        if(permanent)code_retirement_wait(request,thread,method,bottom,reason);
        else if(!request->armed)code_retirement_event(request,thread,1);
    }
}
static void JNICALL code_retirement_step(jvmtiEnv *ti,JNIEnv *env,jthread thread,jmethodID method,jlocation location){
    (void)ti;if(!native_jni_ready||location<0||native_original.ExceptionCheck(env))return;
    CodeRetirement *request=code_retirement_hold(env,thread);if(!request)return;
    NativeThread *state=native_thread();if(!state){code_retirement_failure(request);code_retirement_release(env,request);return;}
    native_thread_callback_enter(state);
    if(!request->active||request->waiting||request->pendingForce)goto done;
    jint bottom=0;jmethodID actual=NULL;jlocation at=-1;
    if((*native_ti)->GetFrameCount(native_ti,thread,&bottom)!=JVMTI_ERROR_NONE
        ||(*native_ti)->GetFrameLocation(native_ti,thread,0,&actual,&at)!=JVMTI_ERROR_NONE||actual!=method||at!=location){code_retirement_failure(request);goto done;}
    if(code_retirement_native_scope(state)){
        code_retirement_wait(request,thread,method,bottom,CODE_RETIRE_NATIVE_SCOPE);goto done;
    }
    CodeRetirementOwner *selected=NULL;int relevant=0,unmapped=0;Owner *declared=code_retirement_declared(env,method);
    for(OwnerLink *entry=request->owners;entry;entry=entry->next){
        jobject module=native_original.NewLocalRef(env,entry->owner->module);if(!module)continue;
        if(code_retirement_candidate(env,method,module))relevant=1;
        int missing=0;if(!selected&&code_retirement_observed(env,method,location,module,&missing))selected=code_retirement_owner(entry->owner);
        if(missing)unmapped=1;native_original.DeleteLocalRef(env,module);
    }
    if(!relevant){
        CodeTrace trace;code_trace_init(&trace);int remains=0;
        if(!code_trace(thread,&trace)){code_retirement_failure(request);code_trace_close(&trace);goto done;}
        for(jint i=1;i<trace.count&&!remains;i++)for(OwnerLink *entry=request->owners;entry&&!remains;entry=entry->next){
            jobject module=native_original.NewLocalRef(env,entry->owner->module);
            if(module){remains=code_retirement_candidate(env,trace.frames[i].method,module);native_original.DeleteLocalRef(env,module);}
        }
        code_trace_close(&trace);
        if(remains)code_retirement_wait(request,thread,method,bottom,CODE_RETIRE_FRAMEWORK);
        else{code_retirement_event(request,thread,0);InterlockedExchange(&request->active,0);}goto done;
    }
    if(!selected||unmapped){code_retirement_wait(request,thread,method,bottom,CODE_RETIRE_SOURCE_SCOPE);goto done;}
    OwnerLink *sources=NULL;int known=0,unknown=0,shared=0;
    if(!code_frame_sources_query(env,method,location,&sources,&known,&unknown))unknown=1;
    for(OwnerLink *entry=sources;entry;entry=entry->next)if(!InterlockedCompareExchange(&entry->owner->stopped,0,0))shared=1;
    code_links_free(sources);
    if(unknown||shared){code_retirement_wait(request,thread,method,bottom,CODE_RETIRE_SOURCE_SCOPE);goto done;}
    // A different live declaring Mod is outside this whole-Mod return. The
    // framework above a selected Mod frame keeps its original execution.
    if(declared&&declared!=selected->owner&&!InterlockedCompareExchange(&declared->stopped,0,0)){
        code_retirement_wait(request,thread,method,bottom,CODE_RETIRE_FRAMEWORK);goto done;
    }
    code_retirement_force(env,thread,state,request,selected,method,location,bottom);
done:
    // Entry rejected an already pending business exception. Any exception here
    // therefore came from this event's own metadata/source/policy work; do not
    // inject that control failure into the interrupted application frame.
    if(native_original.ExceptionCheck(env)){
        native_original.ExceptionClear(env);code_retirement_failure(request);
        if(!request->pendingForce)code_retirement_event(request,thread,0);
    }
    code_retirement_release(env,request);native_thread_callback_leave(state);
}
static void code_retirement_exit_observed(CodeRetirementOwner *owner,int closed){
    if(closed)InterlockedIncrement64(&owner->popped);else InterlockedIncrement64(&owner->failures);
}
static void code_retirement_popped(JNIEnv *env,jthread thread,jmethodID method,jint bottom,int closed){
    CodeRetirement *request=code_retirement_hold(env,thread);if(!request)return;
    if((request->waiting||request->pendingForce)&&request->waitMethod==method&&request->waitBottom==bottom){
        InterlockedExchange(&request->waiting,0);InterlockedExchange(&request->pendingForce,0);
        if(!closed)code_retirement_failure(request);
        request->waitMethod=NULL;request->waitBottom=0;
        if(request->active&&!request->ended)code_retirement_event(request,thread,1);
    }
    code_retirement_release(env,request);
}
static void code_retirement_thread_end(JNIEnv *env,jthread thread){
    if(!native_jni_ready||!thread)return;
    AcquireSRWLockExclusive(&code_retirement_records);CodeRetirement *request=NULL;
    for(CodeRetirement *entry=code_retirements;entry;entry=entry->next)if(!entry->ended&&native_original.IsSameObject(env,entry->thread,thread)){
        InterlockedExchange(&entry->ended,1);InterlockedExchange(&entry->active,0);InterlockedExchange(&entry->armed,0);InterlockedExchange(&entry->ownedSuspend,0);
        if(!entry->busy){InterlockedExchange(&entry->busy,1);request=entry;}break;
    }
    ReleaseSRWLockExclusive(&code_retirement_records);if(request)code_retirement_release(env,request);
}
static void code_retirement_arm(JNIEnv *env,jthread thread,Owner *owner,jmethodID observed,jint depth,jthread current){
    CodeRetirement *request=NULL;AcquireSRWLockExclusive(&code_retirement_records);
    for(CodeRetirement *entry=code_retirements;entry;entry=entry->next)if(!entry->ended&&native_original.IsSameObject(env,entry->thread,thread)){request=entry;break;}
    if(request&&request->busy){ReleaseSRWLockExclusive(&code_retirement_records);return;}
    if(!request){request=(CodeRetirement*)calloc(1,sizeof(*request));
        if(request){request->thread=native_original.NewWeakGlobalRef(env,thread);if(!request->thread){free(request);request=NULL;}
            else {request->next=code_retirements;code_retirements=request;}}}
    if(request&&!native_owner_add(&request->owners,owner)){ReleaseSRWLockExclusive(&code_retirement_records);return;}
    if(!request){ReleaseSRWLockExclusive(&code_retirement_records);return;}
    // Existing armed/waiting work belongs to its event thread. Do not race its
    // real pop by borrowing the callback's busy slot for a repeated query.
    if(request->active&&(request->armed||request->waiting||request->pendingForce)&&!request->ownedSuspend){ReleaseSRWLockExclusive(&code_retirement_records);return;}
    InterlockedExchange(&request->busy,1);InterlockedExchange(&request->active,1);ReleaseSRWLockExclusive(&code_retirement_records);
    if(!code_retirement_resume(request,thread))goto done;
    if(current&&native_original.IsSameObject(env,current,thread)){
        code_retirement_event(request,thread,1);goto done;
    }
    jvmtiError error=(*native_ti)->SuspendThread(native_ti,thread);
    if(error==JVMTI_ERROR_THREAD_NOT_ALIVE){InterlockedExchange(&request->ended,1);InterlockedExchange(&request->active,0);goto done;}
    if(error==JVMTI_ERROR_THREAD_SUSPENDED){
        // Another tool owns the suspension; only its owner may resume it.
        code_retirement_event(request,thread,1);goto done;
    }
    if(error!=JVMTI_ERROR_NONE){code_retirement_failure(request);goto done;}
    InterlockedExchange(&request->ownedSuspend,1);
    // No Java callback, owner-table lock or VM-layout walk while another thread
    // is suspended: it might itself hold one of those native/source locks.
    jmethodID actual=NULL;jlocation location=-1;
    if((*native_ti)->GetFrameLocation(native_ti,thread,depth,&actual,&location)==JVMTI_ERROR_NONE&&actual==observed)
        code_retirement_event(request,thread,1);
    else InterlockedExchange(&request->reason,CODE_RETIRE_SOURCE_SCOPE);
    code_retirement_resume(request,thread);
done:
    code_retirement_release(env,request);
}
static void code_retirement_refresh_idle(JNIEnv *env,jthread thread,jvmtiFrameInfo *frames,jint count){
    // A transient first observation may have vanished before the selected
    // suspension. Such an unarmed request needs a real subsequent stack read,
    // rather than remaining pending forever or borrowing a fabricated pop.
    CodeRetirement *request=NULL;AcquireSRWLockExclusive(&code_retirement_records);
    for(CodeRetirement *entry=code_retirements;entry;entry=entry->next)if(!entry->ended&&native_original.IsSameObject(env,entry->thread,thread)){
        if(!entry->busy&&!entry->armed&&!entry->waiting&&!entry->pendingForce){InterlockedExchange(&entry->busy,1);request=entry;}break;
    }
    ReleaseSRWLockExclusive(&code_retirement_records);if(!request)return;
    int remains=0;
    if(!code_retirement_resume(request,thread))goto done;
    for(jint i=0;i<count&&!remains;i++)for(OwnerLink *entry=request->owners;entry&&!remains;entry=entry->next){
        jobject module=native_original.NewLocalRef(env,entry->owner->module);
        if(module){remains=code_retirement_candidate(env,frames[i].method,module);native_original.DeleteLocalRef(env,module);}
    }
    if(!remains)InterlockedExchange(&request->active,0);
done:
    code_retirement_release(env,request);
}
static void code_retirement_scan(JNIEnv *env,jobject module,jlong *values){
    if(!code_available)return;
    Owner *selected=NULL;AcquireSRWLockShared(&native_records);
    for(Owner *owner=native_owners;owner;owner=owner->next)if(InterlockedCompareExchange(&owner->producer,0,0)
        &&InterlockedCompareExchange(&owner->stopped,0,0)&&module&&native_original.IsSameObject(env,owner->module,module)){selected=owner;break;}
    ReleaseSRWLockShared(&native_records);
    int retiring=selected&&code_pop_available&&code_suspend_available&&code_force_available&&code_step_available;
    if(!values&&!retiring)return;
    CodeRetirementOwner *statistics=retiring?code_retirement_owner(selected):NULL;
    if(retiring&&!statistics){InterlockedIncrement(&native_failures);if(values)values[2]++;retiring=0;}
    jthread current=NULL,*threads=NULL;jint count=0;(*native_ti)->GetCurrentThread(native_ti,&current);
    if((*native_ti)->GetAllThreads(native_ti,&count,&threads)!=JVMTI_ERROR_NONE){
        if(statistics)InterlockedIncrement64(&statistics->failures);if(values)values[2]++;goto done;}
    for(jint i=0;i<count;i++){
        CodeTrace trace;code_trace_init(&trace);
        if(code_trace(threads[i],&trace)){
            jvmtiFrameInfo *frames=trace.frames;jint depth=trace.count;
            int found=-1;
            for(jint j=0;j<depth;j++)if(code_retirement_candidate(env,frames[j].method,module)){
                if(found<0)found=j;
                if(values){int unknown=0;
                    if(code_retirement_observed(env,frames[j].method,frames[j].location,module,&unknown))values[1]++;
                    if(unknown)values[2]++;
                }else break;
            }
            if(retiring){
                if(found>=0)code_retirement_arm(env,threads[i],selected,frames[found].method,found,current);
                else code_retirement_refresh_idle(env,threads[i],frames,depth);
            }
        }else if(values){
            jint state=0;
            if((*native_ti)->GetThreadState(native_ti,threads[i],&state)!=JVMTI_ERROR_NONE||(state&JVMTI_THREAD_STATE_ALIVE))values[2]++;
        }
        code_trace_close(&trace);native_original.DeleteLocalRef(env,threads[i]);
    }
done:
    if(threads)(*native_ti)->Deallocate(native_ti,(unsigned char*)threads);if(current)native_original.DeleteLocalRef(env,current);
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_retireCode0(JNIEnv *env,jclass type,jobject module){
    if(!native_jni_ready||!native_original.IsSameObject(env,type,native_controller)
        ||!native_original.CallStaticBooleanMethod(env,native_controller,native_control_gate)){native_refuse(env,"ACTUAL_CODE_RETIREMENT_AGENT_REQUIRED");return;}
    code_retirement_scan(env,module,NULL);
}
static void code_retirement_state(JNIEnv *env,jobject module,jlong *values){
    values[5]=code_suspend_available;values[6]=code_force_available;values[7]=code_step_available;values[8]=code_retirement_onload;
    AcquireSRWLockShared(&code_retirement_records);
    for(CodeRetirementOwner *entry=code_retirement_owners;entry;entry=entry->next)if(!module||native_original.IsSameObject(env,entry->owner->module,module)){
        values[10]+=InterlockedCompareExchange64(&entry->requested,0,0);values[11]+=InterlockedCompareExchange64(&entry->popped,0,0);values[15]+=InterlockedCompareExchange64(&entry->failures,0,0);
    }
    for(CodeRetirement *entry=code_retirements;entry;entry=entry->next)if(!entry->ended&&native_owner_matches(env,entry->owners,module)){
        values[9]+=InterlockedCompareExchange(&entry->active,0,0)!=0;values[16]+=InterlockedCompareExchange(&entry->ownedSuspend,0,0)!=0;
        values[18]+=InterlockedCompareExchange(&entry->pendingForce,0,0)!=0;
        if(entry->active)switch(InterlockedCompareExchange(&entry->reason,0,0)){
            case CODE_RETIRE_CLEANUP:values[12]++;break;case CODE_RETIRE_FRAMEWORK:values[13]++;break;
            case CODE_RETIRE_NATIVE_SCOPE:values[14]++;break;case CODE_RETIRE_SOURCE_SCOPE:values[17]++;break;default:break;
        }
    }
    ReleaseSRWLockShared(&code_retirement_records);
}
