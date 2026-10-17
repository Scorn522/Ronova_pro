/* Companion to AuditFixCheck. Exercise production Host code against this
 * fixture DLL's real import table and real Windows children. The source/image
 * registration is fixture-owned; this does not certify game library discovery. */
#define JNI_OnLoad audit_backend_on_load
#define Agent_OnLoad audit_backend_agent_load
#define Agent_OnUnload audit_backend_agent_unload
#include "../../native/control.c"
#undef JNI_OnLoad
#undef Agent_OnLoad
#undef Agent_OnUnload

/* Physical FramePop capabilities belong to JVMTI's OnLoad environment on
 * this VM. Reserve only that fixture environment; do not install the real
 * controller, native_initialize, JNI table or JavaVM dispatcher. */
static jvmtiEnv *audit_physical_early;
JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *vm,char *options,void *reserved){
    (void)options;(void)reserved;jvmtiCapabilities requested,actual;
    jint status=(*vm)->GetEnv(vm,(void**)&audit_physical_early,JVMTI_VERSION_1_2);
    jvmtiError error=JVMTI_ERROR_NONE;memset(&requested,0,sizeof(requested));memset(&actual,0,sizeof(actual));
    requested.can_get_bytecodes=1;requested.can_generate_frame_pop_events=1;
    requested.can_redefine_classes=options&&!strcmp(options,"carrier-sites");
    if(status==JNI_OK&&audit_physical_early){
        error=(*audit_physical_early)->AddCapabilities(audit_physical_early,&requested);
        if(error==JVMTI_ERROR_NONE)error=(*audit_physical_early)->GetCapabilities(audit_physical_early,&actual);
    }
    if(status!=JNI_OK||!audit_physical_early||error!=JVMTI_ERROR_NONE||!actual.can_get_bytecodes||!actual.can_generate_frame_pop_events
            ||requested.can_redefine_classes&&!actual.can_redefine_classes){
        fprintf(stderr,"PHYSICAL_FIXTURE_ONLOAD_FAILED jni=%d jvmti=%d bytecodes=%u framePop=%u\n",
                status,(int)error,actual.can_get_bytecodes,actual.can_generate_frame_pop_events);
        if(audit_physical_early)(*audit_physical_early)->DisposeEnvironment(audit_physical_early);
        audit_physical_early=NULL;return JNI_ERR;
    }
    return JNI_OK;
}
JNIEXPORT void JNICALL Agent_OnUnload(JavaVM *vm){
    (void)vm;if(audit_physical_early)(*audit_physical_early)->DisposeEnvironment(audit_physical_early);audit_physical_early=NULL;
}

static Owner audit_owner;
static OwnerLink audit_sources;
static NativeLibrary audit_library;
static HostImage audit_image;
static char audit_program_a[MAX_PATH],audit_directory_a[MAX_PATH];
static wchar_t audit_program_w[MAX_PATH],audit_directory_w[MAX_PATH];

static __declspec(noinline) int audit_child(void *entry,int kind,int allowed,DWORD flags,DWORD refusal){
    char command_a[]="cmd.exe /d /c exit 37";
    wchar_t command_w[]=L"cmd.exe /d /c exit 37";
    STARTUPINFOA startup_a={0};startup_a.cb=sizeof(startup_a);
    STARTUPINFOW startup_w={0};startup_w.cb=sizeof(startup_w);
    startup_a.dwFlags=startup_w.dwFlags=STARTF_USESHOWWINDOW;
    startup_a.wShowWindow=startup_w.wShowWindow=SW_HIDE;
    PROCESS_INFORMATION process={0};BOOL created;
    SetLastError(0);
    uintptr_t arguments[10]={
            (uintptr_t)(kind==HOST_CREATE_A?(const void*)audit_program_a:(const void*)audit_program_w),
            (uintptr_t)(kind==HOST_CREATE_A?(void*)command_a:(void*)command_w),
            0,0,FALSE,flags|CREATE_NO_WINDOW,0,
            (uintptr_t)(kind==HOST_CREATE_A?(const void*)audit_directory_a:(const void*)audit_directory_w),
            (uintptr_t)(kind==HOST_CREATE_A?(void*)&startup_a:(void*)&startup_w),(uintptr_t)&process};
    if(entry)created=host_call_create(entry,kind,arguments,flags|CREATE_NO_WINDOW);
    else if(kind==HOST_CREATE_A)created=CreateProcessA(audit_program_a,command_a,NULL,NULL,FALSE,
            flags|CREATE_NO_WINDOW,NULL,audit_directory_a,&startup_a,&process);
    else created=CreateProcessW(audit_program_w,command_w,NULL,NULL,FALSE,
            flags|CREATE_NO_WINDOW,NULL,audit_directory_w,&startup_w,&process);
    DWORD error=GetLastError();int ok=allowed?created:!created&&error==refusal&&!process.hProcess&&!process.hThread;
    if(created){
        if(flags&CREATE_SUSPENDED){
            ok=ok&&WaitForSingleObject(process.hProcess,0)==WAIT_TIMEOUT;
            if(ResumeThread(process.hThread)==(DWORD)-1)ok=0;
        }
        DWORD waited=WaitForSingleObject(process.hProcess,5000),code=0;
        if(waited!=WAIT_OBJECT_0){TerminateProcess(process.hProcess,99);WaitForSingleObject(process.hProcess,2000);ok=0;}
        if(!GetExitCodeProcess(process.hProcess,&code)||code!=37)ok=0;
        if(!CloseHandle(process.hThread))ok=0;
        if(!CloseHandle(process.hProcess))ok=0;
    }
    if(!ok)fprintf(stderr,"HOST_CHILD_FAILED kind=%d allowed=%d created=%d error=%lu\n",kind,allowed,created,(unsigned long)error);
    return ok;
}
/* Enter after installation so an earlier loader call cannot leave the original
 * import in a register across host_image_imports. That first window is not covered. */
static __declspec(noinline) FARPROC audit_resolve(HMODULE image,LPCSTR name){return GetProcAddress(image,name);}
static void audit_restore_imports(void){
    AcquireSRWLockExclusive(&host_imports);
    for(HostImport *entry=audit_image.imports;entry;entry=entry->next)
        if(entry->slotOwned&&entry->slot&&__atomic_load_n(entry->slot,__ATOMIC_ACQUIRE)==entry->entry)
            host_exchange_import(entry,entry->entry,entry->original);
    audit_image.live=0;
    ReleaseSRWLockExclusive(&host_imports);
}
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm,void *reserved){(void)vm;(void)reserved;return JNI_VERSION_1_8;}

/* Isolated real-JVMTI stack fixture. Only the registration of the bridge and
 * watched tokens is fixture-owned. Site selection, live version checks and
 * normal/exceptional FramePop disposal below are production code. No original
 * JNI/JavaVM entry is replaced and no whole Agent installation is implied. */
static void audit_physical_failure(JNIEnv *env,const char *message){
    if((*env)->ExceptionCheck(env))return;
    jclass type=(*env)->FindClass(env,"java/lang/AssertionError");
    if(type){(*env)->ThrowNew(env,type,message);(*env)->DeleteLocalRef(env,type);}
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_physicalStop0(JNIEnv *env,jclass type){
    (void)type;
    NativeToolingEnv *tooling=native_ti?native_tooling_environment(native_ti):NULL;
    if(native_ti){const struct jvmtiInterface_1_ *original=tooling?tooling->original:*native_ti;
        original->SetEventNotificationMode(native_ti,JVMTI_DISABLE,JVMTI_EVENT_FRAME_POP,NULL);}
    InterlockedExchange(&code_pop_available,0);InterlockedExchange(&native_jni_ready,0);
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    if(state){
        while(state->execution){CodeExit *entry=state->execution;state->execution=entry->previous;
            if(entry->token)(*env)->DeleteGlobalRef(env,entry->token);free(entry);}
        TlsSetValue(native_tls,NULL);free(state);
    }
    if(native_tls!=TLS_OUT_OF_INDEXES){TlsFree(native_tls);native_tls=TLS_OUT_OF_INDEXES;}
    if(code_bridge){(*env)->DeleteGlobalRef(env,code_bridge);code_bridge=NULL;}
    if(native_controller){(*env)->DeleteGlobalRef(env,native_controller);native_controller=NULL;}
    code_pop_callback=NULL;
    int retired=1;
    if(native_ti){
        if(tooling){
            AcquireSRWLockExclusive(&tooling->mutation);
            retired=native_tooling_exchange((void *volatile*)native_ti,tooling->shadow,(void*)tooling->original);
            if(retired){
                native_private_unregister(&tooling->apiRange);
                retired=tooling->original->DisposeEnvironment(native_ti)==JVMTI_ERROR_NONE;
                if(retired){InterlockedExchange(&tooling->closing,1);InterlockedExchange(&tooling->disposed,1);}
            }
            ReleaseSRWLockExclusive(&tooling->mutation);
        }else retired=(*native_ti)->DisposeEnvironment(native_ti)==JVMTI_ERROR_NONE;
        native_ti=NULL;
    }
    native_private_unregister(&native_tooling_image_range);native_tooling_image_begin=native_tooling_image_end=0;
    native_vm=NULL;memset(&native_vm_original,0,sizeof(native_vm_original));memset(&native_original,0,sizeof(native_original));
    if(!retired)audit_physical_failure(env,"fixture own JVMTI environment did not restore/dispose");
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_physicalSetup0(JNIEnv *env,jclass type,jclass bridge){
    JavaVM *vm=NULL;jvmtiCapabilities capability,actual;jvmtiEventCallbacks callbacks;
    const char *failure="GetJavaVM";jint status=JNI_ERR;jvmtiError error=JVMTI_ERROR_NONE;int early=audit_physical_early!=NULL;
    memset(&actual,0,sizeof(actual));
    if(native_controller||native_jni_ready||native_ti||!bridge){audit_physical_failure(env,"physical fixture requires its own uninstalled JVM");return;}
    status=(*env)->GetJavaVM(env,&vm);if(status!=JNI_OK||!vm)goto failed;
    failure="GetEnv";
    if(audit_physical_early){native_ti=audit_physical_early;audit_physical_early=NULL;}
    else{status=(*vm)->GetEnv(vm,(void**)&native_ti,JVMTI_VERSION_1_2);if(status!=JNI_OK||!native_ti)goto failed;}
    native_original=**env;native_vm=vm;
    memset(&capability,0,sizeof(capability));capability.can_get_bytecodes=1;capability.can_generate_frame_pop_events=1;
    failure="AddCapabilities";error=(*native_ti)->AddCapabilities(native_ti,&capability);if(error!=JVMTI_ERROR_NONE)goto failed;
    failure="GetCapabilities";error=(*native_ti)->GetCapabilities(native_ti,&actual);
    if(error!=JVMTI_ERROR_NONE||!actual.can_get_bytecodes||!actual.can_generate_frame_pop_events)goto failed;
    /* The VM-layout reader requires an actually admitted own JVMTI table,
     * whose original GetBytecodes entry belongs to this same running VM.
     * Register only this fixture environment; the JavaVM/JNI tables stay real. */
    failure="own JVMTI environment admission";native_vm_original=**vm;
    HMODULE toolingImage=native_image((void*)Java_dev_ronova_pro_AuditFixCheck_physicalSetup0);
    if(!toolingImage)goto failed;
    native_tooling_image_begin=(uintptr_t)toolingImage;
    const IMAGE_DOS_HEADER *toolingDos=(const IMAGE_DOS_HEADER*)toolingImage;
    const IMAGE_NT_HEADERS64 *toolingNt=(const IMAGE_NT_HEADERS64*)(native_tooling_image_begin+toolingDos->e_lfanew);
    native_tooling_image_end=native_tooling_image_begin+toolingNt->OptionalHeader.SizeOfImage;
    if(!native_private_register(&native_tooling_image_range,(const void*)native_tooling_image_begin,
            (SIZE_T)(native_tooling_image_end-native_tooling_image_begin))
            ||!native_tooling_observe(native_ti,env,(void*)Java_dev_ronova_pro_AuditFixCheck_physicalSetup0,1))goto failed;
    failure="controller/frame-pop registration";
    native_controller=(jclass)(*env)->NewGlobalRef(env,type);code_bridge=(jclass)(*env)->NewGlobalRef(env,bridge);
    code_pop_callback=(*env)->GetStaticMethodID(env,type,"physicalPopped","(Ljava/lang/Object;Z)Z");native_tls=TlsAlloc();
    if(!native_controller||!code_bridge||!code_pop_callback||native_tls==TLS_OUT_OF_INDEXES||!native_thread())goto failed;
    memset(&callbacks,0,sizeof(callbacks));callbacks.FramePop=code_frame_popped;
    failure="SetEventCallbacks";error=(*native_ti)->SetEventCallbacks(native_ti,&callbacks,sizeof(callbacks));if(error!=JVMTI_ERROR_NONE)goto failed;
    failure="SetEventNotificationMode";error=(*native_ti)->SetEventNotificationMode(native_ti,JVMTI_ENABLE,JVMTI_EVENT_FRAME_POP,NULL);if(error!=JVMTI_ERROR_NONE)goto failed;
    jclass pool=(*env)->FindClass(env,"java/util/concurrent/ForkJoinPool"),queue=(*env)->FindClass(env,"java/util/concurrent/ForkJoinPool$WorkQueue");
    int quiescent=pool&&queue&&code_platform_queue_quiescent(env,pool,queue)
            &&!code_platform_queue_quiescent(env,type,queue)&&!code_platform_queue_quiescent(env,pool,type);
    if(pool)(*env)->DeleteLocalRef(env,pool);if(queue)(*env)->DeleteLocalRef(env,queue);
    if(!quiescent){failure="bounded queue snapshot/exact class refusal";goto failed;}
    InterlockedExchange(&code_pop_available,1);InterlockedExchange(&native_jni_ready,1);return;
failed:
    jvmtiPhase phase=JVMTI_PHASE_DEAD;if(native_ti)(*native_ti)->GetPhase(native_ti,&phase);char evidence[256];
    snprintf(evidence,sizeof(evidence),"physical setup %s jni=%d jvmti=%d phase=%d bytecodes=%u framePop=%u early=%d",
            failure,status,(int)error,(int)phase,actual.can_get_bytecodes,actual.can_generate_frame_pop_events,early);
    Java_dev_ronova_pro_AuditFixCheck_physicalStop0(env,type);audit_physical_failure(env,evidence);
}
/* The carrier check admits only this same fixture-owned environment and image.
 * All frame, CP, method-version and observer-plan comparisons are production
 * readers. This registration supplies no live Core or equipment authorization. */
JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_carrierSiteSetup0(JNIEnv *env,jclass type,jclass controls,jclass sources){
    if(native_controls||code_images){audit_physical_failure(env,"carrier fixture requires an uninstalled image registry");return;}
    Java_dev_ronova_pro_AuditFixCheck_physicalSetup0(env,type,sources);if((*env)->ExceptionCheck(env))return;
    jvmtiCapabilities capability;memset(&capability,0,sizeof(capability));
    if((*native_ti)->GetCapabilities(native_ti,&capability)!=JVMTI_ERROR_NONE||!capability.can_redefine_classes){
        Java_dev_ronova_pro_AuditFixCheck_physicalStop0(env,type);audit_physical_failure(env,"carrier fixture requires -agentpath DLL=carrier-sites");return;
    }
    native_controls=(jclass)native_original.NewGlobalRef(env,controls);
    native_control_gate=native_original.GetStaticMethodID(env,type,"carrierReader","()Z");
    if(!native_controls||!native_control_gate){
        if(native_controls)native_original.DeleteGlobalRef(env,native_controls);native_controls=NULL;native_control_gate=NULL;
        Java_dev_ronova_pro_AuditFixCheck_physicalStop0(env,type);audit_physical_failure(env,"carrier fixture bridge registration failed");
    }
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_carrierSiteStop0(JNIEnv *env,jclass type){
    InterlockedExchange(&code_available,0);native_control_gate=NULL;
    if(native_controls){native_original.DeleteGlobalRef(env,native_controls);native_controls=NULL;}
    Java_dev_ronova_pro_AuditFixCheck_physicalStop0(env,type);
}
JNIEXPORT jint JNICALL Java_dev_ronova_pro_AuditFixCheck_carrierSiteDepth0(JNIEnv *env,jclass type,jclass declaring,jstring name,jstring descriptor){
    return Java_dev_ronova_pro_bootstrap_NativeControl_carrierCallbackDepth0(env,type,declaring,name,descriptor);
}
JNIEXPORT jlongArray JNICALL Java_dev_ronova_pro_AuditFixCheck_carrierSiteFrame0(JNIEnv *env,jclass type,jclass declaring,jstring name,jstring descriptor,jint bottom){
    return Java_dev_ronova_pro_bootstrap_NativeControl_carrierFrame0(env,type,declaring,name,descriptor,bottom);
}
JNIEXPORT jlongArray JNICALL Java_dev_ronova_pro_AuditFixCheck_carrierSiteImage0(JNIEnv *env,jclass type,jclass declaring,jstring name,jstring descriptor){
    return Java_dev_ronova_pro_bootstrap_NativeControl_carrierMethodImage0(env,type,declaring,name,descriptor);
}
JNIEXPORT jclass JNICALL Java_dev_ronova_pro_AuditFixCheck_carrierSiteMethod0(JNIEnv *env,jclass type,jobject receiver,jclass symbolic,jstring name,jstring descriptor,jint opcode,jclass caller){
    return Java_dev_ronova_pro_bootstrap_NativeControl_carrierMethod0(env,type,receiver,symbolic,name,descriptor,opcode,caller);
}
JNIEXPORT jlongArray JNICALL Java_dev_ronova_pro_AuditFixCheck_carrierSiteCapture0(JNIEnv *env,jclass type,jobject receiver,jclass symbolic,jstring name,jstring descriptor,jint opcode,jclass caller,jstring callerName,jstring callerDescriptor,jint bottom){
    return Java_dev_ronova_pro_bootstrap_NativeControl_carrierCallSite0(env,type,receiver,symbolic,name,descriptor,opcode,caller,callerName,callerDescriptor,bottom);
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_carrierSiteMetadata0(JNIEnv *env,jclass type,jboolean available){
    (void)env;(void)type;InterlockedExchange(&code_available,available==JNI_TRUE);
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_AuditFixCheck_carrierSiteRegister0(JNIEnv *env,jclass type,jclass actual,jbyteArray bytes,jobject plan){
    jobject loader=NULL;char *signature=NULL;jstring name=NULL;jobjectArray keys=NULL,rows=NULL;int valid=actual&&bytes&&plan;
    if(valid)valid=(*native_ti)->GetClassLoader(native_ti,actual,&loader)==JVMTI_ERROR_NONE
            &&(*native_ti)->GetClassSignature(native_ti,actual,&signature,NULL)==JVMTI_ERROR_NONE&&signature;
    if(valid){size_t length=strlen(signature);valid=length>2&&signature[0]=='L'&&signature[length-1]==';';
        if(valid){signature[length-1]=0;name=native_original.NewStringUTF(env,signature+1);}}
    jclass objectType=valid?native_original.FindClass(env,"java/lang/Object"):NULL;
    if(objectType){keys=native_original.NewObjectArray(env,0,objectType,NULL);rows=native_original.NewObjectArray(env,0,objectType,NULL);native_original.DeleteLocalRef(env,objectType);}
    valid=valid&&name&&keys&&rows&&!native_original.ExceptionCheck(env);
    if(valid){InterlockedExchange(&code_available,1);valid=Java_dev_ronova_pro_bootstrap_NativeControl_codeLayout0(env,type,loader,name,bytes,keys,rows,JNI_FALSE,plan);}
    if(rows)native_original.DeleteLocalRef(env,rows);if(keys)native_original.DeleteLocalRef(env,keys);if(name)native_original.DeleteLocalRef(env,name);
    if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);if(loader)native_original.DeleteLocalRef(env,loader);return valid?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_carrierSiteRedefine0(JNIEnv *env,jclass type,jclass actual,jbyteArray bytes){
    (void)type;NativeToolingEnv *tooling=native_tooling_environment(native_ti);
    if(!tooling||!actual||!bytes){audit_physical_failure(env,"carrier fixture redefine arguments unavailable");return;}
    jsize length=native_original.GetArrayLength(env,bytes);jbyte *body=native_original.GetByteArrayElements(env,bytes,NULL);
    jvmtiError error=JVMTI_ERROR_INTERNAL;
    if(body){jvmtiClassDefinition definition={actual,length,(const unsigned char*)body};error=tooling->original->RedefineClasses(native_ti,1,&definition);
        native_original.ReleaseByteArrayElements(env,bytes,body,JNI_ABORT);}
    if(error!=JVMTI_ERROR_NONE){char message[96];snprintf(message,sizeof(message),"carrier fixture actual redefine failed jvmti=%d",(int)error);audit_physical_failure(env,message);}
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_physicalBind0(JNIEnv *env,jclass type,jobject token){
    (void)type;jmethodID method=NULL;jlocation location=-1;jint depth=0,bottom=0;jthread thread=NULL;
    CodeVMMethod version={0};CodePool pool={0};char *name=NULL,*descriptor=NULL;CodeExit *entry=NULL;
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);int ready=0;
    const char *stage="state/token";jvmtiError error=JVMTI_ERROR_NONE;CodeFailure failure;code_failure_init(&failure);
    if(!state||!token)goto done;
    stage="actual caller prefix";if(!code_execution_frame(env,&method,&location,&depth,&bottom))goto done;
    stage="GetMethodName";error=(*native_ti)->GetMethodName(native_ti,method,&name,&descriptor,NULL);
    if(error!=JVMTI_ERROR_NONE||!name||!descriptor)goto done;
    stage="method snapshot";if(!code_vm_snapshot_diagnostic(env,method,&version,&pool,name,descriptor,&failure))goto done;
    stage="snapshot unchanged";if(!code_vm_unchanged(method,&version))goto done;
    stage="GetCurrentThread";error=(*native_ti)->GetCurrentThread(native_ti,&thread);if(error!=JVMTI_ERROR_NONE)goto done;
    stage="duplicate watcher";
    for(CodeExit *seen=state->execution;seen;seen=seen->previous)
        if(seen->bottom==bottom||(*env)->IsSameObject(env,seen->token,token))goto done;
    stage="watcher allocation";entry=(CodeExit*)calloc(1,sizeof(*entry));if(!entry)goto done;
    entry->method=method;entry->bottom=bottom;entry->token=(*env)->NewGlobalRef(env,token);
    entry->physicalVersion[0]=version.method;entry->physicalVersion[1]=version.constantMethod;entry->physicalVersion[2]=version.constants;
    entry->physicalLength=version.codeLength;entry->physical=1;
    stage="global token";if(!entry->token)goto done;
    stage="captured version";if(!code_execution_version(entry))goto done;
    stage="NotifyFramePop";error=(*native_ti)->NotifyFramePop(native_ti,thread,depth);if(error!=JVMTI_ERROR_NONE)goto done;
    entry->previous=state->execution;state->execution=entry;entry=NULL;ready=1;
done:
    char evidence[512];
    if(!ready)snprintf(evidence,sizeof(evidence),"physical bind %s jvmti=%d snapshot=%s snapshotError=%d selector=%s%s bci=%lld depth=%d bottom=%d layout=%d ownBytecodes=%d",
            stage,(int)error,failure.stage?failure.stage:"none",(int)failure.error,name?name:"?",descriptor?descriptor:"?",
            (long long)location,depth,bottom,code_vm_layout.ready,native_tooling_getbytecodes_image(native_ti)!=NULL);
    if(entry){if(entry->token)(*env)->DeleteGlobalRef(env,entry->token);free(entry);}
    if(thread)(*env)->DeleteLocalRef(env,thread);if(name)(*native_ti)->Deallocate(native_ti,(unsigned char*)name);
    if(descriptor)(*native_ti)->Deallocate(native_ti,(unsigned char*)descriptor);free(version.handlers);code_pool_free(&pool);
    if(!ready)audit_physical_failure(env,evidence);
}
JNIEXPORT jlong JNICALL Java_dev_ronova_pro_AuditFixCheck_physicalSite0(JNIEnv *env,jclass type,jobject token){
    return Java_dev_ronova_pro_bootstrap_NativeControl_executionSite0(env,type,token);
}
JNIEXPORT jlong JNICALL Java_dev_ronova_pro_AuditFixCheck_physicalFrameSite0(JNIEnv *env,jclass type,jobject token){
    return Java_dev_ronova_pro_bootstrap_NativeControl_executionFrameSite0(env,type,token);
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_physicalInvalidate0(JNIEnv *env,jclass type,jobject token){
    (void)type;NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    for(CodeExit *entry=state?state->execution:NULL;entry;entry=entry->previous)
        if((*env)->IsSameObject(env,entry->token,token)){entry->physicalVersion[0]^=(uintptr_t)1;return;}
    audit_physical_failure(env,"fixture stale version had no actual watched token");
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_physicalRelease0(JNIEnv *env,jclass type,jobject token){
    (void)type;NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    CodeExit **slot=state?&state->execution:NULL;
    while(slot&&*slot&&!(*env)->IsSameObject(env,(*slot)->token,token))slot=&(*slot)->previous;
    if(slot&&*slot){CodeExit *entry=*slot;*slot=entry->previous;(*env)->DeleteGlobalRef(env,entry->token);free(entry);return;}
    audit_physical_failure(env,"fixture release had no actual watched token");
}

JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_hostProcess0(JNIEnv *env,jclass type){
    (void)type;const char *failure=NULL;void *dynamic_a=NULL,*dynamic_w=NULL;
    DWORD count_a=GetSystemDirectoryA(audit_directory_a,MAX_PATH),count_w=GetSystemDirectoryW(audit_directory_w,MAX_PATH);
    if(!count_a||count_a+9>=MAX_PATH||!count_w||count_w+9>=MAX_PATH){failure="system directory unavailable";goto done;}
    snprintf(audit_program_a,MAX_PATH,"%s\\cmd.exe",audit_directory_a);
    _snwprintf(audit_program_w,MAX_PATH,L"%ls\\cmd.exe",audit_directory_w);
    HMODULE kernel=GetModuleHandleW(L"kernel32.dll");
    audit_image.handle=native_image((void*)Java_dev_ronova_pro_AuditFixCheck_hostProcess0);
    audit_image.createTarget=(void*)GetProcAddress(kernel,"CreateProcessW");
    audit_image.createATarget=(void*)GetProcAddress(kernel,"CreateProcessA");
    audit_image.resolveTarget=(void*)GetProcAddress(kernel,"GetProcAddress");
    if(!audit_image.handle||!audit_image.createTarget||!audit_image.createATarget||!audit_image.resolveTarget){failure="actual Windows entry unavailable";goto done;}
    audit_owner.producer=1;audit_sources.owner=&audit_owner;
    audit_library.handle=audit_image.handle;audit_library.hostImage=&audit_image;
    audit_library.owners=&audit_sources;audit_library.jni=JNI_TRUE;audit_library.alive=audit_library.complete=1;
    native_libraries=&audit_library;host_images=&audit_image;audit_image.live=1;
    host_image_imports(&audit_image);
    if(audit_image.failed){failure="fixture imports were not installed";goto done;}
    if(!audit_child(NULL,HOST_CREATE_A,1,0,0)||!audit_child(NULL,HOST_CREATE_W,1,0,0)){
        failure="normal A/W imports changed a live call";goto done;
    }
    dynamic_a=(void*)audit_resolve(kernel,"CreateProcessA");
    dynamic_w=(void*)audit_resolve(kernel,"CreateProcessW");
    if(!dynamic_a||dynamic_a==audit_image.createATarget||!dynamic_w||dynamic_w==audit_image.createTarget){for(HostImport *entry=audit_image.imports;entry;entry=entry->next)fprintf(stderr,"HOST_IMPORT kind=%d installed=%d\n",entry->kind,entry->slot&&__atomic_load_n(entry->slot,__ATOMIC_ACQUIRE)==entry->entry);
        failure="dynamic A/W result was not wrapped";goto done;}
    if(!audit_child(dynamic_a,HOST_CREATE_A,1,CREATE_SUSPENDED,0)
            ||!audit_child(dynamic_w,HOST_CREATE_W,1,0,0)){
        failure="dynamic A/W result changed a live call or suspension";goto done;
    }
    InterlockedExchange(&audit_owner.stopped,1);
    if(!audit_child(NULL,HOST_CREATE_A,0,0,ERROR_ACCESS_DENIED)
            ||!audit_child(NULL,HOST_CREATE_W,0,0,ERROR_ACCESS_DENIED)
            ||!audit_child(dynamic_a,HOST_CREATE_A,0,0,ERROR_ACCESS_DENIED)
            ||!audit_child(dynamic_w,HOST_CREATE_W,0,0,ERROR_ACCESS_DENIED)){
        failure="stopped source created a child through an import or cached result";goto done;
    }
    SetLastError(0);
    if(audit_resolve(kernel,"CreateProcessA")||GetLastError()!=ERROR_ACCESS_DENIED){failure="stopped source resolved a new A entry";goto done;}
    if(!audit_child(audit_image.createATarget,HOST_CREATE_A,1,0,0)){
        failure="unselected original Windows entry was changed";goto done;
    }
    audit_image.live=0;
    if(!audit_child(dynamic_a,HOST_CREATE_A,0,0,ERROR_MOD_NOT_FOUND)){
        failure="retired image cached A entry remained callable";goto done;
    }
    for(int attempt=0;attempt<50;attempt++){
        int pending=0;AcquireSRWLockExclusive(&host_records);
        for(HostProcess *process=host_processes;process;process=process->next){host_collect(process);pending|=!process->retired;}
        ReleaseSRWLockExclusive(&host_records);
        if(!pending)break;
        if(attempt==49){failure="created child monitor/job remained live";break;}
        Sleep(10);
    }
done:
    audit_restore_imports();
    if(failure){jclass error=(*env)->FindClass(env,"java/lang/AssertionError");if(error)(*env)->ThrowNew(env,error,failure);}
}

/* Run in its own DLL/JVM without installing the controller. The real JavaVM
 * entries remain unchanged. An unavailable TLS index fails JNI observation;
 * this fixture-only VirtualProtect target fails the JVMTI table exchange after
 * its exact API range has been prepared. Neither is a production test hook. */
static BOOL (WINAPI *audit_vm_real_protect)(LPVOID,SIZE_T,DWORD,PDWORD);
static volatile LONG audit_vm_exchange_refused;
static BOOL WINAPI audit_vm_refuse_exchange(LPVOID address,SIZE_T length,DWORD protection,PDWORD previous){
    if(length==sizeof(void*)){InterlockedIncrement(&audit_vm_exchange_refused);SetLastError(ERROR_ACCESS_DENIED);return FALSE;}
    return audit_vm_real_protect(address,length,protection,previous);
}
typedef struct AuditVMWorker {JavaVM *vm;const char *failure;} AuditVMWorker;
static DWORD WINAPI audit_vm_worker(void *argument){
    AuditVMWorker *work=(AuditVMWorker*)argument;void *actual=(void*)(uintptr_t)1,*expected=(void*)(uintptr_t)1;
    SetLastError(0x5a71);jint original=native_vm_original.GetEnv(work->vm,&expected,JNI_VERSION_1_8);DWORD error=GetLastError();
    SetLastError(0x5a71);jint wrapped=native_tooling_getenv(work->vm,&actual,JNI_VERSION_1_8);DWORD wrappedError=GetLastError();
    if(original!=JNI_EDETACHED||wrapped!=original||actual!=expected||actual||wrappedError!=error){work->failure="detached GetEnv status/output changed";return 0;}
    for(int daemon=0;daemon<2;daemon++){
        JNIEnv *attached=NULL;wrapped=native_vm_attach_call(work->vm,(void**)&attached,NULL,daemon);
        JNIEnv *current=NULL;original=native_vm_original.GetEnv(work->vm,(void**)&current,JNI_VERSION_1_8);
        if(wrapped!=JNI_OK||!attached||original!=JNI_OK||current!=attached)work->failure="observer failure changed Attach result or detached its real attachment";
        if(!work->failure){actual=(void*)(uintptr_t)1;wrapped=native_tooling_getenv(work->vm,&actual,JNI_VERSION_1_8);
            if(wrapped!=JNI_OK||actual!=current)work->failure="attached worker GetEnv changed its original environment";}
        /* Only this fixture pairs its actual attachment. A failed observation
         * must not have performed a hidden Detach before this cleanup. */
        if(original==JNI_OK&&native_vm_original.DetachCurrentThread(work->vm)!=JNI_OK)work->failure="fixture could not detach its own worker";
        if(work->failure)return 0;
    }
    return 0;
}
static jthrowable audit_vm_pending(JNIEnv *env){
    jclass type=(*env)->FindClass(env,"java/lang/IllegalStateException");if(!type)return NULL;
    (*env)->ThrowNew(env,type,"audit original VM pending exception");(*env)->DeleteLocalRef(env,type);
    return (*env)->ExceptionOccurred(env);
}
static int audit_vm_pending_same(JNIEnv *env,jthrowable expected){
    jthrowable actual=(*env)->ExceptionOccurred(env);if(actual)(*env)->ExceptionClear(env);
    int same=actual&&(*env)->IsSameObject(env,actual,expected);
    if(actual)(*env)->DeleteLocalRef(env,actual);return same;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_AuditFixCheck_nativeVmResults0(JNIEnv *env,jclass type){
    (void)type;const char *failure=NULL;JavaVM *vm=NULL;jthrowable pending=NULL;jvmtiEnv *tooling=NULL;
    const struct jvmtiInterface_1_ *toolingOriginal=NULL;void *protectedValue=(void*)(uintptr_t)1;
    NativePrivateRange protectedRange={0};DWORD fixtureTls=TLS_OUT_OF_INDEXES;NativeThread thread={0};
    void *protectTarget=NULL;HANDLE worker=NULL;AuditVMWorker work={0};int initialized=0;
    if(native_vm||native_events||native_tls!=TLS_OUT_OF_INDEXES){failure="VM result fixture requires an uninstalled controller";goto done;}
    if((*env)->GetJavaVM(env,&vm)!=JNI_OK||!vm){failure="actual JavaVM unavailable";goto done;}
    native_vm=vm;native_vm_original=**vm;native_original=**env;initialized=1;
    if(!native_tooling_output_prepare()){failure="actual output writer unavailable";goto done;}
    LONG failures=native_failures;void *actual=NULL,*expected=NULL;
    SetLastError(0x5a72);jint original=native_vm_original.GetEnv(vm,&expected,JNI_VERSION_1_8);DWORD error=GetLastError();
    SetLastError(0x5a72);jint wrapped=native_tooling_getenv(vm,&actual,JNI_VERSION_1_8);DWORD wrappedError=GetLastError();
    if(original!=JNI_OK||wrapped!=original||actual!=expected||actual!=env||wrappedError!=error){failure="JNI GetEnv observer failure changed original status/output/error";goto done;}
    for(int daemon=0;daemon<2;daemon++){
        jint (JNICALL *attach)(JavaVM*,void**,void*)=daemon?native_vm_original.AttachCurrentThreadAsDaemon:native_vm_original.AttachCurrentThread;
        expected=actual=NULL;SetLastError(0x5a73);original=attach(vm,&expected,NULL);error=GetLastError();
        pending=audit_vm_pending(env);if(!pending){failure="pending exception unavailable";goto done;}
        SetLastError(0x5a73);wrapped=native_vm_attach_call(vm,&actual,NULL,daemon);wrappedError=GetLastError();
        int same=audit_vm_pending_same(env,pending);(*env)->DeleteLocalRef(env,pending);pending=NULL;
        if(original!=JNI_OK||wrapped!=original||actual!=expected||actual!=env||wrappedError!=error||!same){failure="attached no-op Attach changed its environment, pending Throwable, or original error";goto done;}
    }
    expected=actual=(void*)(uintptr_t)1;SetLastError(0x5a74);original=native_vm_original.GetEnv(vm,&expected,0x7fffffff);error=GetLastError();
    SetLastError(0x5a74);wrapped=native_tooling_getenv(vm,&actual,0x7fffffff);wrappedError=GetLastError();
    if(original!=JNI_EVERSION||wrapped!=original||actual!=expected||actual||wrappedError!=error){failure="unsupported GetEnv version status/output/error changed";goto done;}
    if(native_failures<=failures){failure="unavailable JNI observation was not recorded";goto done;}
    /* Admission retains exact control targets even with no TLS record. */
    const struct JNINativeInterface_ *jniTable=*env;
    if(native_tooling_getenv(vm,(void**)env,JNI_VERSION_1_8)!=JNI_ERR||*env!=jniTable
            ||native_vm_attach_call(vm,(void**)env,NULL,0)!=JNI_ERR||*env!=jniTable){failure="known current JNIEnv slot was writable after observation failure";goto done;}
    if(!native_private_register(&protectedRange,&protectedValue,sizeof(protectedValue))){failure="fixture exact control output unavailable";goto done;}
    if(native_tooling_getenv(vm,&protectedValue,JNI_VERSION_1_8)!=JNI_ERR||protectedValue!=(void*)(uintptr_t)1
            ||native_vm_attach_call(vm,&protectedValue,NULL,0)!=JNI_ERR||protectedValue!=(void*)(uintptr_t)1){failure="registered exact control output was modified";goto done;}
    native_private_unregister(&protectedRange);
    work.vm=vm;worker=CreateThread(NULL,0,audit_vm_worker,&work,0,NULL);
    if(!worker){failure="fixture worker unavailable";goto done;}
    if(WaitForSingleObject(worker,INFINITE)!=WAIT_OBJECT_0){failure="fixture worker wait failed";goto done;}
    CloseHandle(worker);worker=NULL;if(work.failure){failure=work.failure;goto done;}
    /* Use the VM's own table as the lifetime check, then explicitly dispose
     * only the environment created for this comparison. */
    original=native_vm_original.GetEnv(vm,(void**)&tooling,JVMTI_VERSION_1_2);
    if(original!=JNI_OK||!tooling){failure="actual JVMTI GetEnv unavailable";goto done;}
    toolingOriginal=*tooling;
    if(toolingOriginal->DisposeEnvironment(tooling)!=JVMTI_ERROR_NONE){failure="fixture baseline JVMTI disposal failed";goto done;}
    tooling=NULL;
    protectTarget=host_real_memory_target(HOST_VIRTUAL_PROTECT,"VirtualProtect");
    if(!protectTarget){failure="actual VirtualProtect unavailable";goto done;}
    audit_vm_real_protect=(BOOL (WINAPI*)(LPVOID,SIZE_T,DWORD,PDWORD))protectTarget;
    __atomic_store_n(&host_windows_api[HOST_VIRTUAL_PROTECT],(void*)audit_vm_refuse_exchange,__ATOMIC_RELEASE);
    NativeToolingEnv *before=native_tooling_environments;LONG toolingFailures=native_tooling_failures;
    pending=audit_vm_pending(env);if(!pending){failure="pending tooling exception unavailable";goto done;}
    wrapped=native_tooling_getenv(vm,(void**)&tooling,JVMTI_VERSION_1_2);
    int same=audit_vm_pending_same(env,pending);(*env)->DeleteLocalRef(env,pending);pending=NULL;
    __atomic_store_n(&host_windows_api[HOST_VIRTUAL_PROTECT],protectTarget,__ATOMIC_RELEASE);protectTarget=NULL;
    jint version=0;
    if(wrapped!=JNI_OK||!tooling||*tooling!=toolingOriginal||!same
            ||toolingOriginal->GetVersionNumber(tooling,&version)!=JVMTI_ERROR_NONE
            ||native_tooling_failures<=toolingFailures||!audit_vm_exchange_refused){failure="failed JVMTI observer changed/disposed original environment or lost pending Throwable";goto done;}
    AcquireSRWLockShared(&native_private_gate);int retained=native_private_range_locked((uintptr_t)tooling,(uintptr_t)tooling+sizeof(*tooling)-1);ReleaseSRWLockShared(&native_private_gate);
    if(native_tooling_environments!=before||retained){failure="failed table exchange retained a stale environment record/range";goto done;}
    if(toolingOriginal->DisposeEnvironment(tooling)!=JVMTI_ERROR_NONE){failure="fixture could not dispose its live original JVMTI result";goto done;}
    tooling=NULL;
    fixtureTls=TlsAlloc();if(fixtureTls==TLS_OUT_OF_INDEXES||!TlsSetValue(fixtureTls,&thread)){failure="fixture restricted scope unavailable";goto done;}
    native_tls=fixtureTls;thread.toolingRestricted=1;actual=(void*)(uintptr_t)1;SetLastError(0x5a75);
    wrapped=native_tooling_getenv(vm,&actual,JNI_VERSION_1_8);wrappedError=GetLastError();
    if(wrapped!=JNI_ERR||actual||wrappedError!=0x5a75){failure="restricted GetEnv refusal changed";goto done;}
    actual=(void*)(uintptr_t)1;SetLastError(0x5a75);wrapped=native_vm_attach_call(vm,&actual,NULL,0);wrappedError=GetLastError();
    if(wrapped!=JNI_ERR||actual||wrappedError!=0x5a75){failure="restricted Attach refusal changed";goto done;}
    thread.toolingRestricted=0;Owner stopped={0};stopped.stopped=1;OwnerLink sources={.owner=&stopped};NativeToolingScope scope={.owners=&sources};thread.tooling=&scope;
    actual=(void*)(uintptr_t)1;
    if(native_tooling_getenv(vm,&actual,JVMTI_VERSION_1_2)!=JNI_ERR||actual){failure="confirmed stopped tooling source obtained an environment";goto done;}
    actual=NULL;if(native_tooling_getenv(vm,&actual,JNI_VERSION_1_8)!=JNI_OK||actual!=env){failure="stopped tooling scope changed unrelated JNI GetEnv";goto done;}
done:
    if((*env)->ExceptionCheck(env))(*env)->ExceptionClear(env);
    if(pending)(*env)->DeleteLocalRef(env,pending);
    if(protectTarget)__atomic_store_n(&host_windows_api[HOST_VIRTUAL_PROTECT],protectTarget,__ATOMIC_RELEASE);
    if(tooling&&toolingOriginal)toolingOriginal->DisposeEnvironment(tooling);
    native_private_unregister(&protectedRange);native_private_unregister(&thread.jniRange);
    if(fixtureTls!=TLS_OUT_OF_INDEXES){TlsSetValue(fixtureTls,NULL);TlsFree(fixtureTls);}
    if(worker)CloseHandle(worker);
    if(initialized){native_tls=TLS_OUT_OF_INDEXES;native_vm=NULL;memset(&native_vm_original,0,sizeof(native_vm_original));memset(&native_original,0,sizeof(native_original));}
    if(failure){jclass error=(*env)->FindClass(env,"java/lang/AssertionError");if(error)(*env)->ThrowNew(env,error,failure);}
    return failure?JNI_FALSE:JNI_TRUE;
}

/* Keep the production Java stop-publication path, including its real JNI
 * crossing. Only these isolated native bodies call the blocking/reentry probes;
 * no controller, JVMTI dispatcher, original JNI table or native retirement is installed. */
static jclass audit_stop_fixture;
static jmethodID audit_stop_probe,audit_stop_reentry;
static void JNICALL audit_stop_module(JNIEnv *env,jclass control,jobject module,jboolean stopped){
    (void)control;(*env)->CallStaticVoidMethod(env,audit_stop_fixture,audit_stop_probe,module,stopped);
}
static void JNICALL audit_stop_retire(JNIEnv *env,jclass control,jobject module){(void)env;(void)control;(void)module;}
JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_stopPolicyStop0(JNIEnv *env,jclass type,jclass control){
    (void)type;if(control)(*env)->UnregisterNatives(env,control);
    if(audit_stop_fixture)(*env)->DeleteGlobalRef(env,audit_stop_fixture);
    audit_stop_fixture=NULL;audit_stop_probe=audit_stop_reentry=NULL;
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_stopPolicySetup0(JNIEnv *env,jclass type,jclass control){
    if(audit_stop_fixture||native_controller||native_jni_ready||native_vm){audit_physical_failure(env,"stop publication fixture requires an uninstalled controller");return;}
    audit_stop_fixture=(jclass)(*env)->NewGlobalRef(env,type);
    audit_stop_probe=(*env)->GetStaticMethodID(env,type,"stopPolicyNativeProbe","(Ljava/lang/Module;Z)V");
    audit_stop_reentry=(*env)->GetStaticMethodID(env,type,"stopPolicyNativeReenter","(Ljava/lang/Module;)V");
    if(!audit_stop_fixture||!audit_stop_probe||!audit_stop_reentry){
        if(audit_stop_fixture)(*env)->DeleteGlobalRef(env,audit_stop_fixture);
        audit_stop_fixture=NULL;audit_stop_probe=audit_stop_reentry=NULL;return;
    }
    JNINativeMethod methods[]={
        {"module0","(Ljava/lang/Module;Z)V",(void*)audit_stop_module},
        {"retireCode0","(Ljava/lang/Module;)V",(void*)audit_stop_retire},
        {"heapPolicyChanged0","(Ljava/lang/Object;)V",(void*)Java_dev_ronova_pro_bootstrap_NativeControl_heapPolicyChanged0}
    };
    if((*env)->RegisterNatives(env,control,methods,sizeof(methods)/sizeof(methods[0]))!=JNI_OK){
        if(audit_stop_fixture)(*env)->DeleteGlobalRef(env,audit_stop_fixture);
        audit_stop_fixture=NULL;audit_stop_probe=audit_stop_reentry=NULL;
    }
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_stopPolicyCallback0(JNIEnv *env,jclass type,jobject module){
    (void)type;if(!audit_stop_fixture||!audit_stop_reentry){audit_physical_failure(env,"stop publication native callback is not prepared");return;}
    (*env)->CallStaticVoidMethod(env,audit_stop_fixture,audit_stop_reentry,module);
}

/* Real OS allocations and real JNI direct views. Only the allocator/source
 * registration and Java observer bodies belong to this fixture; allocation
 * completion, weak aliases, lifetime admission and both undo entries are the
 * production implementations. No Agent or process-wide JNI table is installed. */
static BOOL (WINAPI *audit_opaque_real_free)(LPVOID,SIZE_T,DWORD);
static void *(JNICALL *audit_opaque_address_original)(JNIEnv*,jobject);
static jobject (JNICALL *audit_opaque_new_original)(JNIEnv*,void*,jlong);
static jthrowable audit_opaque_original_failure;
static int audit_opaque_release_count,audit_opaque_original_calls;
static void JNICALL audit_opaque_release(JNIEnv *env,jobject allocator,jlong address){
    (void)allocator;audit_opaque_release_count++;
    if(!audit_opaque_real_free((LPVOID)(uintptr_t)address,0,MEM_RELEASE))audit_physical_failure(env,"actual opaque allocation release failed");
}
static void *JNICALL audit_opaque_address_success(JNIEnv *env,jobject buffer){
    audit_opaque_original_calls++;void *address=audit_opaque_address_original(env,buffer);SetLastError(0x97b1);return address;
}
static void *JNICALL audit_opaque_address_failure(JNIEnv *env,jobject buffer){
    (void)buffer;audit_opaque_original_calls++;native_original.Throw(env,audit_opaque_original_failure);SetLastError(0x97b2);return NULL;
}
static jobject JNICALL audit_opaque_new_success(JNIEnv *env,void *address,jlong bytes){
    audit_opaque_original_calls++;jobject buffer=audit_opaque_new_original(env,address,bytes);SetLastError(0x97c1);return buffer;
}
static NativeMemoryAllocation *audit_opaque_record(JNIEnv *env,jclass type,NativeThread *state,void *address,Owner *owner){
    NativeMemoryCall *call=(NativeMemoryCall*)calloc(1,sizeof(*call));
    NativeMemoryAllocation *prepared=(NativeMemoryAllocation*)calloc(1,sizeof(*prepared));
    if(!call||!prepared){free(call);free(prepared);return NULL;}InitializeCriticalSection(&prepared->gate);
    call->prepared=prepared;call->bytes=65536;call->known=1;call->release=(void*)audit_opaque_release;
    call->allocator=native_original.NewGlobalRef(env,type);
    if(!call->allocator||!native_owner_add(&call->sources,owner)){native_memory_call_dispose(env,call);return NULL;}
    Binding binding={0};binding.signature="(J)J";binding.unsafeOperation=UNSAFE_ALLOCATE;
    unsigned char returned[256]={0};uint64_t value=(uint64_t)(uintptr_t)address;
    memcpy(returned+native_unsafe_save(&binding)+96,&value,sizeof(value));
    NativeUnsafeFrame frame={0};frame.memoryCall=call;native_memory_call_end(env,state,&binding,returned,&frame);
    return native_memory_allocations;
}
static int audit_opaque_original_release(JNIEnv *env,jclass type,NativeThread *state,NativeMemoryAllocation *allocation){
    NativeMemoryCall *call=(NativeMemoryCall*)calloc(1,sizeof(*call));if(!call)return 0;
    call->allocation=allocation;call->known=1;call->address=allocation->address;
    audit_opaque_release(env,type,(jlong)allocation->address);
    Binding binding={0};binding.signature="(J)V";binding.unsafeOperation=UNSAFE_FREE;
    unsigned char returned[256]={0};NativeUnsafeFrame frame={0};frame.memoryCall=call;
    native_memory_call_end(env,state,&binding,returned,&frame);return !native_original.ExceptionCheck(env)&&!allocation->alive;
}
static int audit_opaque_revision(NativeMemoryAllocation *allocation,Owner *owner,uint64_t before,uint64_t after){
    memcpy((void*)(uintptr_t)allocation->address,&before,sizeof(before));OwnerLink source={0};source.owner=owner;
    NativeMemoryWrite write={0};write.allocation=allocation;write.address=allocation->address;write.length=sizeof(before);write.generation=allocation->generation;write.sources=&source;
    write.image=native_memory_image(&write);if(!write.image)return 0;
    memcpy((void*)(uintptr_t)allocation->address,&after,sizeof(after));
    int ready=native_memory_read(write.address,write.image->after,sizeof(after));
    if(ready)native_memory_range(&write);native_memory_image_release(write.image);
    return ready&&allocation->ranges&&allocation->ranges->revision&&allocation->ranges->revision->complete;
}
static void audit_opaque_scope(JNIEnv *env,jclass type,NativeMemoryAllocation *allocation){
    native_original.SetStaticLongField(env,type,native_original.GetStaticFieldID(env,type,"opaqueNativeToken","J"),(jlong)(uintptr_t)allocation);
    native_original.SetStaticLongField(env,type,native_original.GetStaticFieldID(env,type,"opaqueNativeGeneration","J"),(jlong)allocation->generation);
}
static int audit_opaque_result(JNIEnv *env,jlongArray result,int managed,int pending){
    jlong values[5]={0};int count=managed?4:5,ready=result&&native_original.GetArrayLength(env,result)==count;
    if(ready){native_original.GetLongArrayRegion(env,result,0,count,values);ready=!native_original.ExceptionCheck(env)
            &&(pending?values[managed?1:2]>0:values[managed?1:2]==0)&&values[managed?2:3]==0&&values[managed?3:4]==(managed&&pending?0:1);}
    if(result)native_original.DeleteLocalRef(env,result);return ready;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_AuditFixCheck_opaqueNativeState0(JNIEnv *env,jclass type,jobject action,jobjectArray modules,jthrowable original){
    const char *failure=NULL;void *page=NULL,*unknownPage=NULL,*sameAddress=NULL;NativeThread thread={0};Owner *owner=NULL;
    NativeMemoryAllocation *allocation=NULL,*first=NULL;DWORD fixtureTls=TLS_OUT_OF_INDEXES;int initialized=0;
    LPVOID (WINAPI *allocate)(LPVOID,SIZE_T,DWORD,DWORD)=NULL;
    jobject view=NULL,latest=NULL,unknownView=NULL;uint64_t before=0x1020304050607080ULL,after=0x8070605040302010ULL;
    jlong oldBinding[2]={0},binding[2]={0};jfieldID observerMode=NULL,seen=NULL;
#define OPAQUE_REQUIRE(CONDITION,MESSAGE) do{if(!(CONDITION)){failure=(MESSAGE);goto done;}}while(0)
    OPAQUE_REQUIRE(!native_controller&&!native_vm&&native_tls==TLS_OUT_OF_INDEXES&&!native_memory_allocations&&!native_owners,"opaque fixture requires an uninstalled controller");
    native_original=**env;initialized=1;fixtureTls=TlsAlloc();native_tls=fixtureTls;
    OPAQUE_REQUIRE(fixtureTls!=TLS_OUT_OF_INDEXES&&TlsSetValue(fixtureTls,&thread),"opaque fixture TLS unavailable");
    native_controller=(jclass)native_original.NewGlobalRef(env,type);native_file_bridge=(jclass)native_original.NewGlobalRef(env,type);
    native_controls=(jclass)native_original.NewGlobalRef(env,type);native_memory_gate=native_original.GetStaticMethodID(env,type,"memoryRecoveryCaller","()Z");
    native_buffer_address_query=native_original.GetStaticMethodID(env,type,"opaqueNativeAddress","(Ljava/lang/Object;[Ljava/lang/Module;)Z");
    native_buffer_created_query=native_original.GetStaticMethodID(env,type,"opaqueNativeCreated","(Ljava/lang/Object;[Ljava/lang/Module;)V");
    observerMode=native_original.GetStaticFieldID(env,type,"opaqueNativeObserverMode","I");seen=native_original.GetStaticFieldID(env,type,"opaqueNativeSeen","Ljava/lang/Object;");
    HMODULE kernel=GetModuleHandleW(L"kernel32.dll");
    allocate=(LPVOID (WINAPI*)(LPVOID,SIZE_T,DWORD,DWORD))GetProcAddress(kernel,"VirtualAlloc");
    audit_opaque_real_free=(BOOL (WINAPI*)(LPVOID,SIZE_T,DWORD))GetProcAddress(kernel,"VirtualFree");
    OPAQUE_REQUIRE(native_controller&&native_file_bridge&&native_controls&&native_memory_gate&&native_buffer_address_query&&native_buffer_created_query&&observerMode&&seen&&allocate&&audit_opaque_real_free,"opaque fixture original entries unavailable");
    jobject module=native_original.GetObjectArrayElement(env,modules,0);owner=native_owner(env,module);if(module)native_original.DeleteLocalRef(env,module);
    OPAQUE_REQUIRE(owner,"opaque fixture actual module unavailable");owner->producer=owner->stopped=1;OwnerLink selected={0};selected.owner=owner;
    audit_opaque_address_original=native_original.GetDirectBufferAddress;audit_opaque_new_original=native_original.NewDirectByteBuffer;
    audit_opaque_original_failure=original;audit_opaque_release_count=audit_opaque_original_calls=0;
    page=allocate(NULL,65536,MEM_RESERVE|MEM_COMMIT,PAGE_READWRITE);OPAQUE_REQUIRE(page,"actual opaque allocation unavailable");sameAddress=page;
    first=allocation=audit_opaque_record(env,type,&thread,page,owner);OPAQUE_REQUIRE(allocation&&allocation->address==(uint64_t)(uintptr_t)page,"production allocation completion lost actual return");
    view=audit_opaque_new_original(env,page,32);OPAQUE_REQUIRE(view,"actual JNI direct view unavailable");
    OPAQUE_REQUIRE(native_memory_buffer_observe(env,view,NULL,(uint64_t)(uintptr_t)page,32,oldBinding)&&oldBinding[0]&&oldBinding[1],"actual JNI view did not bind its observed allocation");
    OPAQUE_REQUIRE(native_memory_buffer_use(env,view,oldBinding[0],(uint64_t)oldBinding[1],(uint64_t)(uintptr_t)page,32,1)&&allocation->users==1,"current native view did not retain its lifetime");
    OPAQUE_REQUIRE(native_memory_buffer_use(env,view,oldBinding[0],(uint64_t)oldBinding[1],(uint64_t)(uintptr_t)page,32,0)&&!allocation->users,"native view lifetime did not release");
    EnterCriticalSection(&allocation->gate);int retired=native_memory_retire_allocation(env,allocation,&selected);LeaveCriticalSection(&allocation->gate);
    OPAQUE_REQUIRE(!retired&&allocation->alive&&!audit_opaque_release_count,"live borrowed view permitted private native free");
    OPAQUE_REQUIRE(audit_opaque_revision(allocation,owner,before,after),"actual opaque byte revision unavailable");
    OPAQUE_REQUIRE(audit_opaque_result(env,Java_dev_ronova_pro_bootstrap_NativeControl_restoreMemory0(env,type,modules,0,0,8),0,1)
            &&allocation->alive&&!audit_opaque_release_count&&*(uint64_t*)page==after,"live borrowed view permitted raw native undo");
    allocation->managed=1;allocation->bufferAction=native_original.NewWeakGlobalRef(env,action);audit_opaque_scope(env,type,allocation);
    OPAQUE_REQUIRE(audit_opaque_result(env,Java_dev_ronova_pro_bootstrap_NativeControl_restoreBufferStorage0(env,type,action,oldBinding[0],oldBinding[1],modules,0,8),1,1)&&*(uint64_t*)page==after,"live borrowed view permitted managed native undo");
    while(allocation->ranges){NativeMemoryRange *range=allocation->ranges;allocation->ranges=range->next;native_memory_revision_release(range->revision);free(range);}
    OPAQUE_REQUIRE(audit_opaque_result(env,Java_dev_ronova_pro_bootstrap_NativeControl_restoreBufferStorage0(env,type,action,oldBinding[0],oldBinding[1],modules,0,0),1,1)&&!allocation->retiring,"empty revision list bypassed live borrowed view during managed free");
    OPAQUE_REQUIRE(audit_opaque_original_release(env,type,&thread,allocation),"actual original native release did not finish its lifecycle");page=NULL;
    OPAQUE_REQUIRE(!native_memory_buffer_use(env,view,oldBinding[0],(uint64_t)oldBinding[1],(uint64_t)(uintptr_t)sameAddress,32,1),"released native view retained its old lifetime");
    page=allocate(sameAddress,65536,MEM_RESERVE|MEM_COMMIT,PAGE_READWRITE);OPAQUE_REQUIRE(page==sameAddress,"actual same-address allocation reuse unavailable");
    allocation=audit_opaque_record(env,type,&thread,page,owner);OPAQUE_REQUIRE(allocation&&allocation!=first,"new actual allocation reused an old allocation token");
    latest=audit_opaque_new_original(env,page,32);OPAQUE_REQUIRE(latest,"replacement JNI direct view unavailable");
    allocation->managed=1;allocation->bufferAction=native_original.NewWeakGlobalRef(env,action);
    OPAQUE_REQUIRE(native_memory_buffer_observe(env,latest,action,(uint64_t)(uintptr_t)page,32,binding)&&binding[0]&&binding[1],"replacement view binding unavailable");
    OPAQUE_REQUIRE(!native_memory_buffer_use(env,view,oldBinding[0],(uint64_t)oldBinding[1],(uint64_t)(uintptr_t)page,32,1)
            &&!native_memory_buffer_use(env,view,binding[0],(uint64_t)binding[1],(uint64_t)(uintptr_t)page,32,1),"same-address allocation admitted an old view or mixed lifetime");
    audit_opaque_scope(env,type,allocation);
    OPAQUE_REQUIRE(native_memory_buffer_use(env,latest,binding[0],(uint64_t)binding[1],(uint64_t)(uintptr_t)page,32,1),"replacement lifetime did not admit its actual view");
    OPAQUE_REQUIRE(audit_opaque_result(env,Java_dev_ronova_pro_bootstrap_NativeControl_restoreBufferStorage0(env,type,action,binding[0],binding[1],modules,0,0),1,1)&&!allocation->retiring,"active actual view lease permitted managed free");
    OPAQUE_REQUIRE(native_memory_buffer_use(env,latest,binding[0],(uint64_t)binding[1],(uint64_t)(uintptr_t)page,32,0),"replacement lifetime lease did not finish");
    OPAQUE_REQUIRE(audit_opaque_result(env,Java_dev_ronova_pro_bootstrap_NativeControl_restoreBufferStorage0(env,type,action,binding[0],binding[1],modules,0,0),1,0)&&allocation->retiring,"actual managed retirement did not close lifetime admission");
    OPAQUE_REQUIRE(!native_memory_buffer_use(env,latest,binding[0],(uint64_t)binding[1],(uint64_t)(uintptr_t)page,32,1),"managed retirement reopened a Java native view lease");
    OPAQUE_REQUIRE(audit_opaque_original_release(env,type,&thread,allocation),"reserved managed original release did not finish");page=NULL;
    page=allocate(sameAddress,65536,MEM_RESERVE|MEM_COMMIT,PAGE_READWRITE);OPAQUE_REQUIRE(page==sameAddress,"second actual address reuse unavailable");
    allocation=audit_opaque_record(env,type,&thread,page,owner);OPAQUE_REQUIRE(allocation&&audit_opaque_revision(allocation,owner,before,after),"opaque exposure allocation unavailable");
    jobject exposed=audit_opaque_new_original(env,page,32);OPAQUE_REQUIRE(exposed,"actual exposure view unavailable");
    unknownPage=allocate(NULL,65536,MEM_RESERVE|MEM_COMMIT,PAGE_READWRITE);OPAQUE_REQUIRE(unknownPage,"unobserved original allocation unavailable");
    native_original.NewDirectByteBuffer=audit_opaque_new_success;native_original.GetDirectBufferAddress=audit_opaque_address_success;
    native_original.SetStaticIntField(env,type,observerMode,1);NativeMemoryAllocation *known=native_memory_allocations;
    unknownView=native_buffer_new(env,unknownPage,32);DWORD newError=GetLastError();
    OPAQUE_REQUIRE(unknownView&&!native_original.ExceptionCheck(env)&&newError==0x97c1
            &&audit_opaque_address_original(env,unknownView)==unknownPage&&native_original.GetDirectBufferCapacity(env,unknownView)==32
            &&native_memory_allocations==known&&!native_memory_unknown&&!allocation->opaque,"unknown JNI view observer failure changed original result or invented allocation provenance");
    native_original.GetDirectBufferAddress=audit_opaque_address_failure;
    void *address=native_buffer_address(env,exposed);DWORD error=GetLastError();
    OPAQUE_REQUIRE(!address&&error==0x97b2&&audit_vm_pending_same(env,original),"original JNI address failure lost Throwable identity or LastError");
    native_original.GetDirectBufferAddress=audit_opaque_address_success;
    address=native_buffer_address(env,exposed);error=GetLastError();
    OPAQUE_REQUIRE(address==page&&!native_original.ExceptionCheck(env)&&error==0x97b1&&allocation->opaque&&allocation->unknownUse&&!allocation->users&&!native_memory_unknown,"JNI address observer failure changed original result or failed to retain exact opaque allocation");
    jlong read=Java_dev_ronova_pro_bootstrap_NativeControl_memoryWriteBegin0(env,type,(jlong)(uintptr_t)page,8,modules,JNI_TRUE,JNI_FALSE);
    OPAQUE_REQUIRE(read&&!native_original.ExceptionCheck(env),"actual opaque native read receipt unavailable");
    jobjectArray contributors=Java_dev_ronova_pro_bootstrap_NativeControl_memoryReadSources0(env,type,read);int knownSource=0,unknownSource=0;
    jobject expectedModule=native_original.GetObjectArrayElement(env,modules,0);
    if(contributors)for(jsize index=0;index<native_original.GetArrayLength(env,contributors);index++){
        jobject source=native_original.GetObjectArrayElement(env,contributors,index);
        if(!source)unknownSource++;else{if(native_original.IsSameObject(env,source,expectedModule))knownSource++;native_original.DeleteLocalRef(env,source);}
    }
    if(contributors)native_original.DeleteLocalRef(env,contributors);if(expectedModule)native_original.DeleteLocalRef(env,expectedModule);
    Java_dev_ronova_pro_bootstrap_NativeControl_memoryWriteEnd0(env,type,read,JNI_TRUE);
    OPAQUE_REQUIRE(knownSource==1&&unknownSource==1&&!native_original.ExceptionCheck(env)&&!native_memory_unknown,"opaque read lost its real source or unknown marker, or globally polluted unrelated native memory");
    OPAQUE_REQUIRE(audit_opaque_result(env,Java_dev_ronova_pro_bootstrap_NativeControl_restoreMemory0(env,type,modules,0,0,8),0,1)&&allocation->alive&&*(uint64_t*)page==after,"opaque native address exposure permitted old byte-image undo");
    native_original.SetStaticIntField(env,type,observerMode,2);address=native_buffer_address(env,exposed);
    OPAQUE_REQUIRE(!address&&native_original.ExceptionCheck(env),"actual address lifetime refusal was softened");native_original.ExceptionClear(env);
    NativeToolingScope stopped={0};stopped.owners=&selected;thread.tooling=&stopped;int calls=audit_opaque_original_calls;
    address=native_buffer_address(env,exposed);OPAQUE_REQUIRE(!address&&native_original.ExceptionCheck(env)&&audit_opaque_original_calls==calls,"stopped native address source reached original API");native_original.ExceptionClear(env);
    jobject denied=native_buffer_new(env,page,32);OPAQUE_REQUIRE(!denied&&native_original.ExceptionCheck(env)&&audit_opaque_original_calls==calls,"stopped native view source reached original API");native_original.ExceptionClear(env);thread.tooling=NULL;
done:
    if(initialized){
        if(native_original.ExceptionCheck(env))native_original.ExceptionClear(env);
        while(thread.memoryWrite)native_memory_write_end(&thread,thread.memoryWrite,0);
        native_original.GetDirectBufferAddress=audit_opaque_address_original?audit_opaque_address_original:native_original.GetDirectBufferAddress;
        native_original.NewDirectByteBuffer=audit_opaque_new_original?audit_opaque_new_original:native_original.NewDirectByteBuffer;
        if(page&&audit_opaque_real_free)audit_opaque_real_free(page,0,MEM_RELEASE);
        if(unknownPage&&audit_opaque_real_free)audit_opaque_real_free(unknownPage,0,MEM_RELEASE);
        while(native_memory_allocations){NativeMemoryAllocation *next=native_memory_allocations->next;NativeMemoryAllocation *entry=native_memory_allocations;
            native_memory_released(env,entry);native_owner_links_free(entry->sources);native_owner_links_free(entry->readers);native_owner_links_free(entry->preserved);
            while(entry->interests){NativeMemoryInterest *interest=entry->interests;entry->interests=interest->next;free(interest);}DeleteCriticalSection(&entry->gate);free(entry);native_memory_allocations=next;}
        native_owner_links_free(native_memory_unknown);native_memory_unknown=NULL;
        while(native_owners){Owner *next=native_owners->next;if(native_owners->module)native_original.DeleteWeakGlobalRef(env,native_owners->module);free(native_owners);native_owners=next;}
        native_private_unregister(&thread.jniRange);
        if(native_controller)native_original.DeleteGlobalRef(env,native_controller);if(native_file_bridge)native_original.DeleteGlobalRef(env,native_file_bridge);if(native_controls)native_original.DeleteGlobalRef(env,native_controls);
        native_controller=native_file_bridge=native_controls=NULL;native_memory_gate=NULL;native_buffer_address_query=native_buffer_created_query=NULL;
        if(fixtureTls!=TLS_OUT_OF_INDEXES){TlsSetValue(fixtureTls,NULL);TlsFree(fixtureTls);}native_tls=TLS_OUT_OF_INDEXES;
        audit_opaque_original_failure=NULL;audit_opaque_address_original=NULL;audit_opaque_new_original=NULL;memset(&native_original,0,sizeof(native_original));
    }
    if(failure)audit_physical_failure(env,failure);
#undef OPAQUE_REQUIRE
    return failure?JNI_FALSE:JNI_TRUE;
}

/* Actual allocator returns are supplied to the production tracker without
 * installing this fixture DLL's imports or a global JNI/Host controller. */
static volatile uint64_t audit_private_native_static=0x98a1b2c3d4e5f607ULL;
static int audit_private_native_sources(JNIEnv *env,jobjectArray sources,jobjectArray modules){
    jobject expected=native_original.GetObjectArrayElement(env,modules,0);int known=0,unknown=0,other=0;
    if(sources)for(jsize index=0;index<native_original.GetArrayLength(env,sources);index++){
        jobject source=native_original.GetObjectArrayElement(env,sources,index);
        if(!source)unknown++;else{if(native_original.IsSameObject(env,source,expected))known++;else other++;native_original.DeleteLocalRef(env,source);}
    }
    if(expected)native_original.DeleteLocalRef(env,expected);if(sources)native_original.DeleteLocalRef(env,sources);
    return known==1&&unknown==1&&!other&&!native_original.ExceptionCheck(env);
}
static jlong audit_private_native_read(JNIEnv *env,jclass type,NativeMemoryAllocation *allocation,jobjectArray modules){
    jlong receipt=Java_dev_ronova_pro_bootstrap_NativeControl_memoryWriteBegin0(env,type,(jlong)allocation->address,8,modules,JNI_TRUE,JNI_FALSE);
    if(!receipt)return 0;
    jobjectArray sources=Java_dev_ronova_pro_bootstrap_NativeControl_memoryReadSources0(env,type,receipt);
    return audit_private_native_sources(env,sources,modules)?receipt:0;
}
static int audit_private_native_aliases_collected(JNIEnv *env,NativeMemoryAllocation *allocation){
    jclass system=native_original.FindClass(env,"java/lang/System");
    jmethodID collect=system?native_original.GetStaticMethodID(env,system,"gc","()V"):NULL;int ready=0;
    for(int attempt=0;collect&&attempt<8&&!native_original.ExceptionCheck(env);attempt++){
        native_original.CallStaticVoidMethod(env,system,collect);
        EnterCriticalSection(&allocation->gate);ready=!native_memory_buffer_aliases(env,allocation);LeaveCriticalSection(&allocation->gate);
        if(ready)break;
    }
    if(system)native_original.DeleteLocalRef(env,system);return ready&&!native_original.ExceptionCheck(env);
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_AuditFixCheck_privateNativeState0(JNIEnv *env,jclass type,jobjectArray modules){
    const char *failure=NULL;NativeThread thread={0};DWORD fixtureTls=TLS_OUT_OF_INDEXES;int initialized=0,denied=0;
    NativeHostMemoryCall *call=NULL,*probe=NULL,*pendingDestroy=NULL;NativeMemoryAllocation *first=NULL,*allocation=NULL,*imageAllocation=NULL;
    NativeHostMemoryContext *heapContext=NULL;OwnerLink *observedSources=NULL;
    HANDLE heap=NULL;void *block=NULL,*sharedBlock=NULL;jobject view=NULL,borrowerModule=NULL;Owner *owner=NULL,*borrower=NULL;OwnerLink selected={0},borrowed={0};
    HostImage image={0};HostStaticMemory parsed={0};jlong binding[2]={0};
    HANDLE (WINAPI *createHeap)(DWORD,SIZE_T,SIZE_T)=NULL;
    LPVOID (WINAPI *allocate)(HANDLE,DWORD,SIZE_T)=NULL;
    LPVOID (WINAPI *resize)(HANDLE,DWORD,LPVOID,SIZE_T)=NULL;
    BOOL (WINAPI *release)(HANDLE,DWORD,LPVOID)=NULL;
    BOOL (WINAPI *destroyHeap)(HANDLE)=NULL;
#define PRIVATE_REQUIRE(CONDITION,MESSAGE) do{if(!(CONDITION)){failure=(MESSAGE);goto done;}}while(0)
    PRIVATE_REQUIRE(!native_controller&&!native_vm&&native_tls==TLS_OUT_OF_INDEXES&&!native_memory_allocations
            &&!native_memory_host_contexts&&!native_memory_images&&!native_owners,"private tracker fixture requires an uninstalled controller");
    native_original=**env;initialized=1;fixtureTls=TlsAlloc();native_tls=fixtureTls;
    PRIVATE_REQUIRE(fixtureTls!=TLS_OUT_OF_INDEXES&&TlsSetValue(fixtureTls,&thread),"private tracker fixture TLS unavailable");
    native_controller=(jclass)native_original.NewGlobalRef(env,type);native_controls=(jclass)native_original.NewGlobalRef(env,type);
    native_memory_gate=native_original.GetStaticMethodID(env,type,"memoryRecoveryCaller","()Z");
    HMODULE kernel=GetModuleHandleW(L"kernel32.dll");
    createHeap=(HANDLE (WINAPI*)(DWORD,SIZE_T,SIZE_T))GetProcAddress(kernel,"HeapCreate");
    allocate=(LPVOID (WINAPI*)(HANDLE,DWORD,SIZE_T))GetProcAddress(kernel,"HeapAlloc");
    resize=(LPVOID (WINAPI*)(HANDLE,DWORD,LPVOID,SIZE_T))GetProcAddress(kernel,"HeapReAlloc");
    release=(BOOL (WINAPI*)(HANDLE,DWORD,LPVOID))GetProcAddress(kernel,"HeapFree");
    destroyHeap=(BOOL (WINAPI*)(HANDLE))GetProcAddress(kernel,"HeapDestroy");
    PRIVATE_REQUIRE(native_controller&&native_controls&&native_memory_gate&&createHeap&&allocate&&resize&&release&&destroyHeap,"private tracker fixture original entries unavailable");
    jobject module=native_original.GetObjectArrayElement(env,modules,0);owner=native_owner(env,module);if(module)native_original.DeleteLocalRef(env,module);
    PRIVATE_REQUIRE(owner,"private tracker fixture actual module unavailable");owner->producer=1;selected.owner=owner;
    jclass objectClass=native_original.FindClass(env,"java/lang/Object"),classClass=native_original.FindClass(env,"java/lang/Class");
    jmethodID getModule=classClass?native_original.GetMethodID(env,classClass,"getModule","()Ljava/lang/Module;"):NULL;
    if(objectClass&&getModule)borrowerModule=native_original.CallObjectMethod(env,objectClass,getModule);
    if(objectClass)native_original.DeleteLocalRef(env,objectClass);if(classClass)native_original.DeleteLocalRef(env,classClass);
    borrower=borrowerModule?native_owner(env,borrowerModule):NULL;
    PRIVATE_REQUIRE(borrower&&borrower!=owner,"private tracker fixture distinct actual borrower module unavailable");borrower->producer=1;borrowed.owner=borrower;
    NativeToolingSlot slot={0};slot.owners=&selected;NativeToolingInvocation invocation={0};invocation.slot=&slot;
    HostThreadWorker worker={0};worker.owners=&selected;
    PRIVATE_REQUIRE(!native_private_execution_unknown(&thread),"ordinary empty native scope acquired private CPU opacity");
    thread.toolingInvocation=&invocation;PRIVATE_REQUIRE(native_private_execution_unknown(&thread),"actual tooling invocation lost private CPU opacity");thread.toolingInvocation=NULL;
    thread.hostWorker=&worker;PRIVATE_REQUIRE(native_private_execution_unknown(&thread),"actual Host worker lost private CPU opacity");thread.hostWorker=NULL;
    NativeToolingScope restrictedScope={&selected,NULL,0,0,NULL};thread.tooling=&restrictedScope;thread.toolingRestricted=1;
    PRIVATE_REQUIRE(native_private_execution_unknown(&thread),"actual restricted FLS scope lost private CPU opacity");
    thread.tooling=NULL;thread.toolingRestricted=0;
    Binding business={0};business.owners=&selected;thread.stack[0]=&business;thread.depth=1;
    PRIVATE_REQUIRE(audit_private_native_sources(env,native_mutation_sources_from(env,&thread,NULL,0),modules),"actual business native contribution lost its known owner or private CPU marker");
    thread.depth=0;thread.stack[0]=NULL;
    call=native_memory_host_prepare(env,NATIVE_MEMORY_HOST_CRT,(void*)release,0,0x1234,8,NATIVE_MEMORY_HOST_FREE,&selected,0,&denied);
    PRIVATE_REQUIRE(!call&&!denied&&!native_memory_allocations,"numeric pointer manufactured a native allocation lifetime");
    call=native_memory_host_prepare(env,NATIVE_MEMORY_HOST_HEAP,(void*)release,0,0,0,NATIVE_MEMORY_HOST_CONTEXT_CREATE,&selected,0,&denied);
    PRIVATE_REQUIRE(call&&!denied&&!native_memory_host_contexts&&!native_memory_allocations,"HeapCreate preparation manufactured a heap or payload before its actual result");
    heap=createHeap(0,0,0);native_memory_host_complete(env,call,(uint64_t)(uintptr_t)heap,0,heap!=NULL,0);call=NULL;
    heapContext=native_memory_host_context((void*)release,(uintptr_t)heap,0);
    PRIVATE_REQUIRE(heap&&heapContext&&heapContext->created&&heapContext->observed&&heapContext->sources&&heapContext->sources->owner==owner
            &&!heapContext->sources->next&&!native_memory_allocations,"actual HeapCreate lost its independent creator incarnation or manufactured child geometry");
    call=native_memory_host_prepare(env,NATIVE_MEMORY_HOST_HEAP,(void*)release,(uintptr_t)heap,0,16,NATIVE_MEMORY_HOST_ALLOCATE,&borrowed,0,&denied);
    PRIVATE_REQUIRE(call&&!denied,"actual borrower heap allocation admission unavailable");
    int sourceUnknown=0,creatorSeen=0,borrowerSeen=0,sourceCount=0;
    PRIVATE_REQUIRE(native_memory_host_sources(call,&observedSources,&sourceUnknown),"heap creator source capture unavailable");
    for(OwnerLink *source=observedSources;source;source=source->next){creatorSeen+=source->owner==owner;borrowerSeen+=source->owner==borrower;sourceCount++;}
    PRIVATE_REQUIRE(!sourceUnknown&&creatorSeen==1&&borrowerSeen==1&&sourceCount==2,"borrower did not retain exact HeapCreate creator sources before original allocation");
    owner->stopped=1;PRIVATE_REQUIRE(native_owner_stopped(observedSources)==owner,"stopped HeapCreate creator escaped the actual allocation source gate");owner->stopped=0;
    native_owner_links_free(observedSources);observedSources=NULL;
    sharedBlock=allocate(heap,0,16);native_memory_host_complete(env,call,(uint64_t)(uintptr_t)sharedBlock,16,sharedBlock!=NULL,0);call=NULL;
    NativeMemoryAllocation *sharedAllocation=native_memory_find((uint64_t)(uintptr_t)sharedBlock,16,1);
    PRIVATE_REQUIRE(sharedBlock&&sharedAllocation&&sharedAllocation->hostContextEpoch==heapContext->epoch&&!sharedAllocation->unknownUse
            &&native_owner_matches(env,sharedAllocation->sources,borrowerModule),"actual shared heap block lost its borrower/context generation");
    call=native_memory_host_prepare(env,NATIVE_MEMORY_HOST_HEAP,(void*)release,(uintptr_t)heap,0,0,NATIVE_MEMORY_HOST_CONTEXT_FREE,&selected,0,&denied);
    PRIVATE_REQUIRE(call&&!denied,"actual shared heap destroy source admission unavailable");
    sourceUnknown=0;PRIVATE_REQUIRE(native_memory_host_sources(call,&observedSources,&sourceUnknown),"shared heap destroy sources unavailable");
    borrower->stopped=1;
    PRIVATE_REQUIRE(!sourceUnknown&&native_owner_stopped(observedSources)==borrower&&native_owner_matches(env,observedSources,borrowerModule),"HeapDestroy omitted a stopped shared block contributor");
    borrower->stopped=0;native_owner_links_free(observedSources);observedSources=NULL;
    native_memory_host_complete(env,call,0,0,0,0);call=NULL;
    PRIVATE_REQUIRE(!heapContext->changing&&sharedAllocation->alive,"declined shared HeapDestroy changed the actual old lifetime");
    call=native_memory_host_prepare(env,NATIVE_MEMORY_HOST_HEAP,(void*)release,(uintptr_t)heap,(uint64_t)(uintptr_t)sharedBlock,0,NATIVE_MEMORY_HOST_FREE,&borrowed,0,&denied);
    PRIVATE_REQUIRE(call&&!denied&&native_memory_host_known(call)&&heapContext->users==1,"actual shared HeapFree context lease unavailable");
    owner->stopped=1;PRIVATE_REQUIRE(!native_memory_host_cleanup(call),"stopped heap creator admitted cleanup of an active borrower's block");
    borrower->stopped=1;PRIVATE_REQUIRE(native_memory_host_cleanup(call),"fully observed stopped heap creators lost their actual release cleanup");owner->stopped=borrower->stopped=0;
    int destroyDenied=0;probe=native_memory_host_prepare(env,NATIVE_MEMORY_HOST_HEAP,(void*)release,(uintptr_t)heap,0,0,NATIVE_MEMORY_HOST_CONTEXT_FREE,&selected,0,&destroyDenied);
    PRIVATE_REQUIRE(!probe&&destroyDenied&&!heapContext->changing&&heapContext->users==1,"in-flight actual HeapFree permitted HeapDestroy");
    BOOL freed=release(heap,0,sharedBlock);native_memory_host_complete(env,call,0,0,freed,freed);call=NULL;if(freed)sharedBlock=NULL;
    PRIVATE_REQUIRE(freed&&!sharedAllocation->alive&&!heapContext->users&&!heapContext->changing,"actual shared HeapFree did not finish its exact context lease");
    call=native_memory_host_prepare(env,NATIVE_MEMORY_HOST_HEAP,(void*)release,(uintptr_t)heap,0,64,NATIVE_MEMORY_HOST_ALLOCATE,&selected,0,&denied);
    PRIVATE_REQUIRE(call&&!denied,"actual heap allocation context admission unavailable");
    block=allocate(heap,0,64);native_memory_host_complete(env,call,(uint64_t)(uintptr_t)block,64,block!=NULL,0);call=NULL;
    first=allocation=native_memory_find((uint64_t)(uintptr_t)block,64,1);
    PRIVATE_REQUIRE(block&&allocation&&allocation->hostFamily==NATIVE_MEMORY_HOST_HEAP&&allocation->hostContext==(uintptr_t)heap
            &&allocation->release==(void*)release&&allocation->opaque&&!allocation->unknownUse&&!native_memory_unknown,"actual allocator return lost exact opaque lifetime/provider ownership");
    uint64_t oldGeneration=allocation->generation;
    call=native_memory_host_prepare(env,NATIVE_MEMORY_HOST_HEAP,(void*)release,(uintptr_t)heap,(uint64_t)(uintptr_t)block,UINT64_MAX,NATIVE_MEMORY_HOST_REALLOCATE,&selected,0,&denied);
    PRIVATE_REQUIRE(call&&!denied,"actual failed realloc admission unavailable");
    destroyDenied=0;probe=native_memory_host_prepare(env,NATIVE_MEMORY_HOST_HEAP,(void*)release,(uintptr_t)heap,0,0,NATIVE_MEMORY_HOST_CONTEXT_FREE,&selected,0,&destroyDenied);
    PRIVATE_REQUIRE(!probe&&destroyDenied&&heapContext->users==1&&!heapContext->changing,"in-flight actual HeapReAlloc permitted HeapDestroy");
    void *resized=resize(heap,0,block,(SIZE_T)-1);
    native_memory_host_complete(env,call,(uint64_t)(uintptr_t)resized,UINT64_MAX,resized!=NULL,0);call=NULL;if(resized)block=resized;
    PRIVATE_REQUIRE(!resized&&allocation->alive&&allocation->generation==oldGeneration&&!allocation->changing&&!allocation->users,"failed original realloc retired or held its old lifetime");
    call=native_memory_host_prepare(env,NATIVE_MEMORY_HOST_HEAP,(void*)release,(uintptr_t)heap,(uint64_t)(uintptr_t)block,32,NATIVE_MEMORY_HOST_REALLOCATE,&selected,0,&denied);
    PRIVATE_REQUIRE(call&&!denied,"same-address realloc admission unavailable");
    void *oldAddress=block;resized=resize(heap,HEAP_REALLOC_IN_PLACE_ONLY,block,32);
    native_memory_host_complete(env,call,(uint64_t)(uintptr_t)resized,32,resized!=NULL,0);call=NULL;if(resized)block=resized;
    allocation=native_memory_find((uint64_t)(uintptr_t)block,32,1);
    PRIVATE_REQUIRE(resized==oldAddress&&allocation&&allocation!=first&&!first->alive&&first->generation!=oldGeneration
            &&allocation->bytes==32&&!allocation->unknownUse,"successful same-address realloc reused the old generation");
    memset(block,0x98,32);jlong receipt=audit_private_native_read(env,type,allocation,modules);
    PRIVATE_REQUIRE(receipt&&!native_memory_unknown,"private CPU bytes lost known source plus unknown read marker");
    call=native_memory_host_prepare(env,NATIVE_MEMORY_HOST_HEAP,(void*)release,(uintptr_t)heap,(uint64_t)(uintptr_t)block,0,NATIVE_MEMORY_HOST_FREE,&selected,0,&denied);
    PRIVATE_REQUIRE(!call&&denied&&allocation->alive,"active actual native read lease permitted original free");
    Java_dev_ronova_pro_bootstrap_NativeControl_memoryWriteEnd0(env,type,receipt,JNI_TRUE);
    view=native_original.NewDirectByteBuffer(env,block,32);
    PRIVATE_REQUIRE(view&&native_memory_buffer_observe(env,view,NULL,(uint64_t)(uintptr_t)block,32,binding)&&binding[0]
            &&!native_memory_buffer_use(env,view,(jlong)(uintptr_t)first,oldGeneration,(uint64_t)(uintptr_t)block,32,1),"new actual view accepted the old same-address lifetime");
    call=native_memory_host_prepare(env,NATIVE_MEMORY_HOST_HEAP,(void*)release,(uintptr_t)heap,(uint64_t)(uintptr_t)block,0,NATIVE_MEMORY_HOST_FREE,&selected,0,&denied);
    PRIVATE_REQUIRE(!call&&denied&&allocation->alive,"live JNI borrowed view permitted Host free");
    owner->stopped=1;EnterCriticalSection(&allocation->gate);int retired=native_memory_retire_allocation(env,allocation,&selected);LeaveCriticalSection(&allocation->gate);
    PRIVATE_REQUIRE(!retired&&allocation->alive,"live JNI borrowed view permitted private cleanup");
    native_original.DeleteLocalRef(env,view);view=NULL;
    PRIVATE_REQUIRE(audit_private_native_aliases_collected(env,allocation),"actual released JNI view remained reachable during private cleanup fixture");
    EnterCriticalSection(&allocation->gate);retired=native_memory_retire_allocation(env,allocation,&selected);LeaveCriticalSection(&allocation->gate);
    if(retired)block=NULL;native_memory_reap_references(env,allocation);
    PRIVATE_REQUIRE(retired&&!allocation->alive&&!allocation->hostReleasePin,"exact private stopped allocation did not call its actual HeapFree and retire");
    owner->stopped=0;
    call=native_memory_host_prepare(env,NATIVE_MEMORY_HOST_HEAP,(void*)release,(uintptr_t)heap,0,16,NATIVE_MEMORY_HOST_ALLOCATE,&selected,0,&denied);
    PRIVATE_REQUIRE(call&&!denied,"actual destroy target allocation admission unavailable");
    block=allocate(heap,0,16);native_memory_host_complete(env,call,(uint64_t)(uintptr_t)block,16,block!=NULL,0);call=NULL;
    NativeMemoryAllocation *destroyedAllocation=native_memory_find((uint64_t)(uintptr_t)block,16,1);NativeHostMemoryContext *destroyedContext=heapContext;
    PRIVATE_REQUIRE(block&&destroyedAllocation&&destroyedAllocation->hostContextEpoch==destroyedContext->epoch,"actual destroy target generation unavailable");
    pendingDestroy=native_memory_host_prepare(env,NATIVE_MEMORY_HOST_HEAP,(void*)release,(uintptr_t)heap,0,0,NATIVE_MEMORY_HOST_CONTEXT_FREE,&selected,0,&denied);
    PRIVATE_REQUIRE(pendingDestroy&&!denied&&destroyedContext->changing,"actual HeapDestroy context admission unavailable");
    BOOL destroyed=destroyHeap(heap);
    if(!destroyed){native_memory_host_complete(env,pendingDestroy,0,0,0,0);pendingDestroy=NULL;PRIVATE_REQUIRE(0,"actual HeapDestroy failed");}
    heap=NULL;block=NULL;
    call=native_memory_host_prepare(env,NATIVE_MEMORY_HOST_HEAP,(void*)release,0,0,0,NATIVE_MEMORY_HOST_CONTEXT_CREATE,&selected,0,&denied);
    PRIVATE_REQUIRE(call&&!denied,"replacement actual HeapCreate admission unavailable");
    heap=createHeap(0,0,0);native_memory_host_complete(env,call,(uint64_t)(uintptr_t)heap,0,heap!=NULL,0);call=NULL;
    heapContext=native_memory_host_context((void*)release,(uintptr_t)heap,0);
    PRIVATE_REQUIRE(heap&&heapContext&&heapContext!=destroyedContext&&heapContext->epoch!=destroyedContext->epoch&&heapContext->created,
            "replacement actual HeapCreate reused an older context incarnation");
    call=native_memory_host_prepare(env,NATIVE_MEMORY_HOST_HEAP,(void*)release,(uintptr_t)heap,0,16,NATIVE_MEMORY_HOST_ALLOCATE,&selected,0,&denied);
    PRIVATE_REQUIRE(call&&!denied,"replacement actual heap block admission unavailable");
    block=allocate(heap,0,16);native_memory_host_complete(env,call,(uint64_t)(uintptr_t)block,16,block!=NULL,0);call=NULL;
    allocation=native_memory_find((uint64_t)(uintptr_t)block,16,1);
    PRIVATE_REQUIRE(block&&allocation&&allocation->hostContextEpoch==heapContext->epoch&&!allocation->unknownUse,"replacement actual heap block lost its new incarnation or inherited an older retiring overlap");
    native_memory_host_complete(env,pendingDestroy,0,0,1,1);pendingDestroy=NULL;
    PRIVATE_REQUIRE(!destroyedAllocation->alive&&destroyedContext->retired&&!destroyedContext->changing
            &&allocation->alive&&!heapContext->retired&&!heapContext->changing&&!heapContext->users
            &&native_memory_host_context((void*)release,(uintptr_t)heap,0)==heapContext,"late actual HeapDestroy completion retired a newer heap/block incarnation");
    call=native_memory_host_prepare(env,NATIVE_MEMORY_HOST_HEAP,(void*)release,(uintptr_t)heap,(uint64_t)(uintptr_t)block,0,NATIVE_MEMORY_HOST_FREE,&selected,0,&denied);
    PRIVATE_REQUIRE(call&&!denied,"replacement actual HeapFree admission unavailable");
    freed=release(heap,0,block);native_memory_host_complete(env,call,0,0,freed,freed);call=NULL;if(freed)block=NULL;
    PRIVATE_REQUIRE(freed&&!allocation->alive,"replacement actual HeapFree failed");
    call=native_memory_host_prepare(env,NATIVE_MEMORY_HOST_HEAP,(void*)release,(uintptr_t)heap,0,0,NATIVE_MEMORY_HOST_CONTEXT_FREE,&selected,0,&denied);
    PRIVATE_REQUIRE(call&&!denied,"replacement actual HeapDestroy admission unavailable");
    destroyed=destroyHeap(heap);native_memory_host_complete(env,call,0,0,destroyed,destroyed);call=NULL;if(destroyed)heap=NULL;
    PRIVATE_REQUIRE(destroyed&&heapContext->retired&&!heapContext->changing,"replacement actual HeapDestroy did not retire its exact context");owner->stopped=1;
    image.handle=native_image((void*)&audit_private_native_static);image.live=1;
    PRIVATE_REQUIRE(image.handle&&host_static_parse(&image,&parsed),"actual fixture PE writable-storage parse failed");
    int containsStatic=0,containsCode=0,executable=0;
    for(HostStaticSpan *span=parsed.spans;span;span=span->next){
        uint64_t data=(uint64_t)(uintptr_t)&audit_private_native_static,code=(uint64_t)(uintptr_t)audit_private_native_read;
        if(data>=span->address&&sizeof(audit_private_native_static)<=span->bytes&&data-span->address<=span->bytes-sizeof(audit_private_native_static))containsStatic=1;
        if(code>=span->address&&code-span->address<span->bytes)containsCode=1;
        for(uint64_t cursor=span->address;cursor<span->address+span->bytes;){MEMORY_BASIC_INFORMATION region={0};
            PRIVATE_REQUIRE(VirtualQuery((void*)(uintptr_t)cursor,&region,sizeof(region))==sizeof(region)&&region.RegionSize,"actual fixture data page query unavailable");
            if(region.Protect&(PAGE_EXECUTE|PAGE_EXECUTE_READ|PAGE_EXECUTE_READWRITE|PAGE_EXECUTE_WRITECOPY))executable=1;
            cursor=(uint64_t)(uintptr_t)region.BaseAddress+region.RegionSize;
        }
    }
    PRIVATE_REQUIRE(containsStatic&&!containsCode&&!executable,"actual fixture PE parser omitted writable static storage or included executable code");
    native_memory_image_span(env,&image,(uint64_t)(uintptr_t)&audit_private_native_static,sizeof(audit_private_native_static),&selected,0);
    imageAllocation=native_memory_find((uint64_t)(uintptr_t)&audit_private_native_static,sizeof(audit_private_native_static),1);
    PRIVATE_REQUIRE(imageAllocation&&imageAllocation->hostFamily==NATIVE_MEMORY_HOST_IMAGE&&!imageAllocation->release&&!imageAllocation->hostReleasePin,"static IMAGE span gained independent free authority");
    receipt=audit_private_native_read(env,type,imageAllocation,modules);
    PRIVATE_REQUIRE(receipt&&native_memory_image_busy(env,&image)&&!native_memory_image_prepare(env,&image),"actual IMAGE read lease did not block unload preparation");
    Java_dev_ronova_pro_bootstrap_NativeControl_memoryWriteEnd0(env,type,receipt,JNI_TRUE);
    view=native_original.NewDirectByteBuffer(env,(void*)&audit_private_native_static,sizeof(audit_private_native_static));
    PRIVATE_REQUIRE(view&&native_memory_buffer_observe(env,view,NULL,(uint64_t)(uintptr_t)&audit_private_native_static,sizeof(audit_private_native_static),binding)
            &&binding[0]&&!native_memory_image_prepare(env,&image),"live IMAGE borrowed alias permitted unload preparation");
    native_original.DeleteLocalRef(env,view);view=NULL;
    PRIVATE_REQUIRE(audit_private_native_aliases_collected(env,imageAllocation)&&native_memory_image_prepare(env,&image)&&imageAllocation->retiring,"quiescent IMAGE storage did not seal before unload");
    PRIVATE_REQUIRE(!native_memory_buffer_enter(env,(uint64_t)(uintptr_t)&audit_private_native_static,sizeof(audit_private_native_static),&allocation)
            &&native_original.ExceptionCheck(env),"prepared IMAGE admitted a new native view");native_original.ExceptionClear(env);
    oldGeneration=imageAllocation->generation;image.privateMemory=&parsed;image.live=0;host_static_image_released(&image,env);
    PRIVATE_REQUIRE(imageAllocation->alive&&imageAllocation->generation==oldGeneration&&imageAllocation->retiring&&!parsed.closed,
            "returning an extra image pin retired still-mapped fixture IMAGE storage");image.live=1;
    native_memory_image_cancel(env,&image);PRIVATE_REQUIRE(!imageAllocation->retiring&&native_memory_image_prepare(env,&image),"failed original unload did not restore exact IMAGE admission");
    oldGeneration=imageAllocation->generation;native_memory_image_retire(env,&image);
    PRIVATE_REQUIRE(!imageAllocation->alive&&imageAllocation->generation!=oldGeneration&&!native_memory_image_prepare(env,&image)
            &&audit_private_native_static==0x98a1b2c3d4e5f607ULL&&!native_memory_unknown,"IMAGE lifetime retirement retained an old pair, freed static bytes or polluted unrelated sources");
done:
    if(initialized){
        if(native_original.ExceptionCheck(env))native_original.ExceptionClear(env);
        if(call)native_memory_host_abort(env,call);
        if(probe)native_memory_host_abort(env,probe);if(pendingDestroy)native_memory_host_abort(env,pendingDestroy);
        native_owner_links_free(observedSources);if(borrowerModule)native_original.DeleteLocalRef(env,borrowerModule);
        while(thread.memoryWrite)native_memory_write_end(&thread,thread.memoryWrite,0);
        if(view)native_original.DeleteLocalRef(env,view);
        if(sharedBlock&&heap&&release)release(heap,0,sharedBlock);if(block&&heap&&release)release(heap,0,block);if(heap&&destroyHeap)destroyHeap(heap);
        while(parsed.spans){HostStaticSpan *span=parsed.spans;parsed.spans=span->next;free(span);}
        while(native_memory_allocations){NativeMemoryAllocation *entry=native_memory_allocations;native_memory_allocations=entry->next;
            native_memory_released(env,entry);native_memory_reap_references(env,entry);
            native_owner_links_free(entry->sources);native_owner_links_free(entry->readers);native_owner_links_free(entry->preserved);
            while(entry->interests){NativeMemoryInterest *interest=entry->interests;entry->interests=interest->next;free(interest);}DeleteCriticalSection(&entry->gate);free(entry);}
        while(native_memory_host_contexts){NativeHostMemoryContext *entry=native_memory_host_contexts;native_memory_host_contexts=entry->next;native_owner_links_free(entry->sources);free(entry);}native_memory_host_context_epoch=0;
        while(native_memory_images){NativeMemoryImageState *entry=native_memory_images;native_memory_images=entry->next;free(entry);}
        native_owner_links_free(native_memory_unknown);native_memory_unknown=NULL;
        while(native_owners){Owner *entry=native_owners;native_owners=entry->next;if(entry->module)native_original.DeleteWeakGlobalRef(env,entry->module);free(entry);}
        native_private_unregister(&thread.jniRange);
        if(native_controller)native_original.DeleteGlobalRef(env,native_controller);if(native_controls)native_original.DeleteGlobalRef(env,native_controls);
        native_controller=native_controls=NULL;native_memory_gate=NULL;
        if(fixtureTls!=TLS_OUT_OF_INDEXES){TlsSetValue(fixtureTls,NULL);TlsFree(fixtureTls);}native_tls=TLS_OUT_OF_INDEXES;
        memset(&native_original,0,sizeof(native_original));
    }
    if(failure)audit_physical_failure(env,failure);
#undef PRIVATE_REQUIRE
    return failure?JNI_FALSE:JNI_TRUE;
}

/* Isolated explicit-object SDK seam. The fixture supplies its own source/image
 * registration; the allocation, bytes, SDK receipts, leases and original CRT
 * calls are real production paths. storage.cpp is the real C++ consumer. This
 * C adapter does not assert knowledge of any foreign C++ or exception ABI. */
typedef struct AuditObjectState {uint64_t plain,borrowed;LONG destroyed;} AuditObjectState;
static __declspec(noinline) void audit_object_original_destroy(AuditObjectState *object,volatile LONG *calls){
    object->destroyed=1;InterlockedIncrement(calls);
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_AuditFixCheck_hostObjectState0(JNIEnv *env,jclass type){
    const char *failure=NULL;int initialized=0,denied=0,destructorReturned=0;
    DWORD fixtureTls=TLS_OUT_OF_INDEXES;NativeThread thread={0};Owner owner={0};OwnerLink selected={.owner=&owner};
    HostImage image={0};NativeLibrary library={0};Binding binding={0};
    HMODULE fixturePin=NULL;
    jobject module=NULL;jclass classType=NULL;const ronova_object_state_api_v1 *api=NULL;
    AuditObjectState *block=NULL;NativeMemoryAllocation *allocation=NULL;NativeHostMemoryCall *call=NULL,*probe=NULL;
    uint64_t object=0,generation=0,plain=0,borrowed=0,write=0,alias=0,destroy=0;
    volatile LONG originalDestructors=0;unsigned originalFrees=0;
    void *(__cdecl *allocate)(size_t)=NULL;void (__cdecl *release)(void*)=NULL;
    const uint64_t before=0x1029384756abcdefULL,after=0xa1b2c3d4e5f6071ULL,untouched=0x8877665544332211ULL;
#define OBJECT_REQUIRE(CONDITION,MESSAGE) do{if(!(CONDITION)){failure=(MESSAGE);goto object_done;}}while(0)
    OBJECT_REQUIRE(!native_controller&&!native_vm&&native_tls==TLS_OUT_OF_INDEXES&&!native_memory_allocations
            &&!native_object_states&&!native_object_state_calls&&!native_owners&&!host_images&&!native_libraries&&!native_tooling_pin,
            "object seam requires an uninstalled isolated controller");
    native_original=**env;initialized=1;fixtureTls=TlsAlloc();native_tls=fixtureTls;
    OBJECT_REQUIRE(fixtureTls!=TLS_OUT_OF_INDEXES&&TlsSetValue(fixtureTls,&thread),"object seam actual TLS unavailable");
    classType=native_original.FindClass(env,"java/lang/Class");
    jmethodID getModule=classType?native_original.GetMethodID(env,classType,"getModule","()Ljava/lang/Module;"):NULL;
    module=getModule?native_original.CallObjectMethod(env,type,getModule):NULL;
    if(classType){native_original.DeleteLocalRef(env,classType);classType=NULL;}
    owner.module=module?native_original.NewWeakGlobalRef(env,module):NULL;owner.producer=1;
    OBJECT_REQUIRE(owner.module&&!native_original.ExceptionCheck(env),"object seam actual Java module unavailable");
    image.handle=native_image((void*)Java_dev_ronova_pro_AuditFixCheck_hostObjectState0);image.live=1;
    IMAGE_DOS_HEADER *dos=(IMAGE_DOS_HEADER*)image.handle;
    IMAGE_NT_HEADERS64 *nt=dos?(IMAGE_NT_HEADERS64*)((unsigned char*)dos+dos->e_lfanew):NULL;
    OBJECT_REQUIRE(dos&&dos->e_magic==IMAGE_DOS_SIGNATURE&&nt&&nt->Signature==IMAGE_NT_SIGNATURE
            &&nt->OptionalHeader.Magic==IMAGE_NT_OPTIONAL_HDR64_MAGIC,"object seam actual PE image unavailable");
    OBJECT_REQUIRE(GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,
            (LPCWSTR)Java_dev_ronova_pro_AuditFixCheck_hostObjectState0,&fixturePin)&&fixturePin==image.handle,
            "object seam actual control image pin unavailable");
    native_tooling_pin=fixturePin;
    image.size=nt->OptionalHeader.SizeOfImage;native_tooling_image_begin=(uintptr_t)image.handle;
    native_tooling_image_end=native_tooling_image_begin+image.size;
    library.handle=image.handle;library.hostImage=&image;library.owners=&selected;library.jni=JNI_TRUE;library.alive=library.complete=1;
    binding.library=&library;binding.owners=&selected;thread.stack[0]=&binding;thread.depth=1;
    host_images=&image;native_libraries=&library;
    api=RonovaObjectStateGetApi(RONOVA_OBJECT_STATE_ABI);
    OBJECT_REQUIRE(api&&api->abi==RONOVA_OBJECT_STATE_ABI&&api->size==sizeof(*api),"object SDK actual function table unavailable");
    OBJECT_REQUIRE(TlsSetValue(fixtureTls,NULL)&&!RonovaObjectStateGetApi(RONOVA_OBJECT_STATE_ABI)
            &&TlsSetValue(fixtureTls,&thread),"object SDK guessed a source or dereferenced absent native thread state");
    HMODULE crt=GetModuleHandleW(L"msvcrt.dll");
    allocate=crt?(void *(__cdecl*)(size_t))GetProcAddress(crt,"malloc"):NULL;
    release=crt?(void (__cdecl*)(void*))GetProcAddress(crt,"free"):NULL;
    OBJECT_REQUIRE(allocate&&release,"object seam actual CRT provider unavailable");
    block=(AuditObjectState*)allocate(sizeof(*block));OBJECT_REQUIRE(block,"object seam original CRT allocation failed");
    block->plain=before;block->borrowed=untouched;block->destroyed=0;
    native_memory_host_allocated(env,NATIVE_MEMORY_HOST_CRT,(void*)allocate,(void*)release,0,0,
            (uint64_t)(uintptr_t)block,sizeof(*block),&selected,0);
    allocation=native_memory_find((uint64_t)(uintptr_t)block,sizeof(*block),1);
    OBJECT_REQUIRE(allocation&&allocation->alive&&allocation->opaque&&!allocation->unknownUse,
            "object seam actual CRT result lost its exact allocation generation");
    errno=EAGAIN;SetLastError(0x524e0b01);object=api->constructed((uintptr_t)block,sizeof(*block));
    DWORD constructedWindows=GetLastError();int constructedErrno=errno;LONG constructedTyped=allocation->typedObjects;
    if(!object||constructedErrno!=EAGAIN||constructedWindows!=0x524e0b01||constructedTyped!=1){
        fprintf(stderr,"HOST_OBJECT_STATE_CONSTRUCT_ACTUAL token=%llu typed=%ld errno=%d lastError=%lu allocation=%p generation=%llu alive=%ld opaque=%ld unknownUse=%ld unknownReaders=%ld controlImage=%p fixtureImage=%p\n",
                (unsigned long long)object,(long)constructedTyped,constructedErrno,(unsigned long)constructedWindows,(void*)allocation,
                (unsigned long long)allocation->generation,(long)allocation->alive,(long)allocation->opaque,
                (long)allocation->unknownUse,(long)allocation->unknownReaders,(void*)native_control_image(),(void*)image.handle);fflush(stderr);
    }
    OBJECT_REQUIRE(object&&constructedErrno==EAGAIN&&constructedWindows==0x524e0b01&&constructedTyped==1,
            "object SDK constructed was uncovered, changed original errors, or lost typed lifetime");
    generation=api->generation(object);
    OBJECT_REQUIRE(generation==allocation->generation&&api->allocation(object)==(uint64_t)(uintptr_t)allocation,
            "object SDK receipt did not carry the actual allocation row and generation");
    plain=api->span(object,generation,(uintptr_t)&block->plain,sizeof(block->plain),RONOVA_OBJECT_BYTES);
    borrowed=api->span(object,generation,(uintptr_t)&block->borrowed,sizeof(block->borrowed),RONOVA_OBJECT_BORROWED_OBJECT);
    OBJECT_REQUIRE(plain&&borrowed&&!api->write_begin(object,generation+1,plain),
            "object SDK lost an explicit field or admitted another allocation generation");
    write=api->write_begin(object,generation,plain);
    OBJECT_REQUIRE(write&&allocation->users==1&&allocation->writers==1,"object SDK actual field writer lease unavailable");
    block->plain=after;int written=api->write_end(write,1);write=0;
    NativeMemoryRange *range=allocation->ranges;
    OBJECT_REQUIRE(written&&range&&range->revision&&range->revision->complete&&range->revision->image
            &&!memcmp(range->revision->image->before,&before,sizeof(before))
            &&!memcmp(range->revision->image->after,&after,sizeof(after))&&block->borrowed==untouched,
            "object SDK did not record the real before/after bytes or copied a borrowed object representation");
    OBJECT_REQUIRE(!native_object_state_restorable(allocation,range,&selected),"object SDK authorized undo while an actual creator/reader was live");
    owner.stopped=1;EnterCriticalSection(&allocation->gate);
    int qualified=native_object_state_restorable(allocation,range,&selected);
    int restored=qualified&&native_memory_restore_range(allocation,range,&selected);
    NativeMemoryRange borrowedRange={0};borrowedRange.start=(uint64_t)(uintptr_t)&block->borrowed-allocation->address;
    borrowedRange.length=sizeof(block->borrowed);
    int borrowedQualified=native_object_state_restorable(allocation,&borrowedRange,&selected);
    LeaveCriticalSection(&allocation->gate);owner.stopped=0;
    OBJECT_REQUIRE(restored&&!borrowedQualified&&block->plain==before&&block->borrowed==untouched&&allocation->opaque,
            "object SDK selected plain restore failed, widened borrowing, or cleared raw-layout opacity");
    probe=native_memory_host_prepare(env,NATIVE_MEMORY_HOST_CRT,(void*)release,0,(uint64_t)(uintptr_t)block,0,
            NATIVE_MEMORY_HOST_FREE,&selected,0,&denied);
    OBJECT_REQUIRE(!probe&&denied&&block->destroyed==0&&!native_memory_private(env,allocation,&selected),
            "raw free or automatic cleanup skipped the actual constructed object destructor");
    alias=api->borrow_begin(object,generation,borrowed);
    OBJECT_REQUIRE(alias&&allocation->users==1&&allocation->reading==1&&!api->destroy_begin(object,generation),
            "object SDK admitted destruction while an actual borrowed-object lease was live");
    probe=native_memory_host_prepare(env,NATIVE_MEMORY_HOST_CRT,(void*)release,0,(uint64_t)(uintptr_t)block,sizeof(*block)*2,
            NATIVE_MEMORY_HOST_REALLOCATE,&selected,0,&denied);
    OBJECT_REQUIRE(!probe&&denied&&block->borrowed==untouched&&originalDestructors==0,
            "an actual borrowed object permitted raw reallocation or guessed destruction");
    jlong active[42]={0};host_object_state_summary(env,module,active);
    OBJECT_REQUIRE(active[39]==1&&active[40]>=1&&active[41]>=1,"active object/borrow/refused-destroy state was not retained as pending");
    int unborrowed=api->borrow_end(alias);alias=0;
    OBJECT_REQUIRE(unborrowed&&!allocation->users&&!allocation->reading,"actual object alias lease did not close");
    destroy=api->destroy_begin(object,generation);
    OBJECT_REQUIRE(destroy&&!allocation->users&&!allocation->changing&&allocation->typedObjects==1,
            "object destructor boundary held an allocation lease across its own original free");
    audit_object_original_destroy(block,&originalDestructors);destructorReturned=1;
    OBJECT_REQUIRE(originalDestructors==1&&block->destroyed==1,"original explicit object destruction did not run exactly once");
    int closed=api->destroy_end(destroy,1);destroy=0;
    OBJECT_REQUIRE(closed&&!allocation->typedObjects,"actual destructor receipt did not release this allocation generation's typed lifetime");
    object=0;call=native_memory_host_prepare(env,NATIVE_MEMORY_HOST_CRT,(void*)release,0,(uint64_t)(uintptr_t)block,0,
            NATIVE_MEMORY_HOST_FREE,&selected,0,&denied);
    OBJECT_REQUIRE(call&&!denied,"original CRT free remained blocked after the real object destructor completed");
    release(block);block=NULL;originalFrees++;
    native_memory_host_complete(env,call,0,0,1,1);call=NULL;
    jlong closedState[42]={0};host_object_state_summary(env,module,closedState);
    OBJECT_REQUIRE(!allocation->alive&&allocation->generation!=generation&&originalDestructors==1&&originalFrees==1
            &&!closedState[39]&&!closedState[40]&&!closedState[41]&&!native_object_states&&!native_object_state_calls,
            "closed object was still live/pending or original CRT free failed to retire the exact allocation");
    fprintf(stdout,"HOST_OBJECT_STATE_SEAM_PASS isolated_source_image_registration actual_CRT_allocation nonzero_SDK_receipt exact_row_generation provider_errors real_before_after_and_selected_plain_restore borrowed_object_not_memcpy live_borrow_blocks_destroy raw_free_realloc_denied actual_C_destroy_then_original_free no_foreign_Cplusplus_ABI_claim\n");fflush(stdout);
object_done:
    if(initialized){
        if(native_original.ExceptionCheck(env))native_original.ExceptionClear(env);
        owner.stopped=0;
        if(call)native_memory_host_abort(env,call);if(probe)native_memory_host_abort(env,probe);
        if(api&&write)api->write_end(write,0);if(api&&alias)api->borrow_end(alias);if(api&&destroy)api->destroy_end(destroy,0);
        /* Only failure teardown drains fixture-owned registration metadata.
         * No successful assertion relies on this teardown to retire an object. */
        while(native_object_state_calls){NativeObjectStateCall *entry=native_object_state_calls;native_object_state_calls=entry->next;
            native_owner_links_free(entry->write.sources);native_memory_image_release(entry->write.image);free(entry);}
        while(native_object_states){NativeObjectState *entry=native_object_states;native_object_states=entry->next;native_object_state_dispose(entry);}
        if(block&&release){if(!destructorReturned)audit_object_original_destroy(block,&originalDestructors);release(block);}
        while(native_memory_allocations){NativeMemoryAllocation *entry=native_memory_allocations;native_memory_allocations=entry->next;
            native_memory_released(env,entry);native_memory_reap_references(env,entry);
            native_owner_links_free(entry->sources);native_owner_links_free(entry->readers);native_owner_links_free(entry->preserved);
            while(entry->interests){NativeMemoryInterest *interest=entry->interests;entry->interests=interest->next;free(interest);}DeleteCriticalSection(&entry->gate);free(entry);}
        native_owner_links_free(native_memory_unknown);native_memory_unknown=NULL;
        host_images=NULL;native_libraries=NULL;native_tooling_image_begin=native_tooling_image_end=0;native_tooling_pin=NULL;
        if(fixturePin)host_real_free_library(fixturePin);
        if(owner.module)native_original.DeleteWeakGlobalRef(env,owner.module);
        if(module)native_original.DeleteLocalRef(env,module);if(classType)native_original.DeleteLocalRef(env,classType);
        if(fixtureTls!=TLS_OUT_OF_INDEXES){TlsSetValue(fixtureTls,NULL);TlsFree(fixtureTls);}native_tls=TLS_OUT_OF_INDEXES;
        memset(&native_original,0,sizeof(native_original));
    }
    if(failure)audit_physical_failure(env,failure);
#undef OBJECT_REQUIRE
    return failure?JNI_FALSE:JNI_TRUE;
}

/* Real compiler TLS requires the fixture's -fno-emulated-tls build. Only this
 * DLL's image/source registration is fixture-owned. The production PE/TEB
 * reader, borrowed storage gates and actual OS-thread witness are unchanged. */
static _Thread_local volatile uint64_t audit_static_tls_word=UINT64_C(0x71932a4b5c6d8e0f);
typedef struct AuditStaticTlsPeer {
    HostImage *image;OwnerLink *owners;HANDLE ready,release;
    uint64_t address,initial;int observed;
} AuditStaticTlsPeer;
static DWORD WINAPI audit_static_tls_peer(void *argument){
    AuditStaticTlsPeer *peer=(AuditStaticTlsPeer*)argument;NativeThread state={0};
    peer->address=(uint64_t)(uintptr_t)&audit_static_tls_word;peer->initial=audit_static_tls_word;
    peer->observed=host_static_tls_observe(&state,peer->image,peer->owners,0);
    SetEvent(peer->ready);WaitForSingleObject(peer->release,INFINITE);return 0;
}
static HostStaticTlsSpan *audit_static_tls_span(HostStaticTlsImage *image,uint64_t address){
    for(HostStaticTlsSpan *span=image?image->spans:NULL;span;span=span->next)
        if(!span->closed&&address>=span->address&&sizeof(uint64_t)<=span->bytes&&address-span->address<=span->bytes-sizeof(uint64_t))return span;
    return NULL;
}
/* This provider-shaped release uses only fixture-owned HeapAlloc storage.
 * Supplying arena completions below exercises the production tracker seam;
 * it is not evidence that jemalloc discovery/control exports were executed. */
static void audit_static_arena_release(void *pointer){if(pointer)HeapFree(GetProcessHeap(),0,pointer);}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_AuditFixCheck_hostStaticTlsArena0(JNIEnv *env,jclass type){
    const char *failure=NULL;int initialized=0,denied=0,joined=0;DWORD fixtureTls=TLS_OUT_OF_INDEXES;
    NativeThread thread={0};Owner *owner=NULL;OwnerLink selected={0};HostImage image={0};
    AuditStaticTlsPeer peer={0};HANDLE peerHandle=NULL;HostStaticTlsImage *tls=NULL;
    HostStaticTlsSpan *mainSpan=NULL,*peerSpan=NULL;NativeMemoryAllocation *mainAllocation=NULL,*peerAllocation=NULL,*arenaAllocation=NULL;
    NativeHostMemoryCall *call=NULL;NativeHostArenaProvider *provider=NULL;NativeHostArenaContext *arena=NULL;
    jobject view=NULL,module=NULL;jlong binding[2]={0},oldToken=0;uint64_t oldGeneration=0,oldEpoch=0;
    void *block=NULL;uintptr_t context=18;uint64_t originalWord=audit_static_tls_word;
#define STATIC_ARENA_REQUIRE(CONDITION,MESSAGE) do{if(!(CONDITION)){failure=(MESSAGE);goto done;}}while(0)
    STATIC_ARENA_REQUIRE(!native_controller&&!native_vm&&native_tls==TLS_OUT_OF_INDEXES&&!native_memory_allocations
            &&!native_memory_images&&!native_memory_arena_contexts&&!native_memory_arena_providers&&!native_owners
            &&!host_static_tls_images&&!host_static_tls_threads,"static TLS/arena fixture requires an uninstalled controller");
    native_original=**env;initialized=1;fixtureTls=TlsAlloc();native_tls=fixtureTls;
    STATIC_ARENA_REQUIRE(fixtureTls!=TLS_OUT_OF_INDEXES&&TlsSetValue(fixtureTls,&thread),"static TLS/arena fixture thread context unavailable");
    native_controller=(jclass)native_original.NewGlobalRef(env,type);native_controls=(jclass)native_original.NewGlobalRef(env,type);
    native_memory_gate=native_original.GetStaticMethodID(env,type,"memoryRecoveryCaller","()Z");
    jclass classClass=native_original.FindClass(env,"java/lang/Class");
    jmethodID getModule=classClass?native_original.GetMethodID(env,classClass,"getModule","()Ljava/lang/Module;"):NULL;
    if(getModule)module=native_original.CallObjectMethod(env,type,getModule);
    if(classClass)native_original.DeleteLocalRef(env,classClass);
    owner=module?native_owner(env,module):NULL;if(owner){owner->producer=1;selected.owner=owner;}
    image.handle=native_image((void*)Java_dev_ronova_pro_AuditFixCheck_hostStaticTlsArena0);image.live=1;
    STATIC_ARENA_REQUIRE(native_controller&&native_controls&&native_memory_gate&&owner&&image.handle&&host_handle_identity_ready(),
            "static TLS/arena fixture source/image or real handle comparator unavailable");
    audit_static_tls_word=UINT64_C(0xa6b7c8d9e0f12345);
    STATIC_ARENA_REQUIRE(host_static_tls_observe(&thread,&image,&selected,0),"actual PE64/current TEB static TLS observation failed");
    tls=host_static_tls_image_locked(&image,0);mainSpan=audit_static_tls_span(tls,(uint64_t)(uintptr_t)&audit_static_tls_word);
    mainAllocation=native_memory_find((uint64_t)(uintptr_t)&audit_static_tls_word,sizeof(uint64_t),0);
    STATIC_ARENA_REQUIRE(tls&&tls->present&&tls->templateBytes&&mainSpan&&mainAllocation
            &&mainSpan->address!=tls->templateAddress&&mainAllocation->imageTag==mainSpan
            &&mainAllocation->hostFamily==NATIVE_MEMORY_HOST_IMAGE&&mainAllocation->opaque&&!mainAllocation->release,
            "static TLS template replaced the actual current borrowed instance or gained free authority");
    peer.image=&image;peer.owners=&selected;peer.ready=CreateEventW(NULL,TRUE,FALSE,NULL);peer.release=CreateEventW(NULL,TRUE,FALSE,NULL);
    if(peer.ready&&peer.release)peerHandle=CreateThread(NULL,0,audit_static_tls_peer,&peer,0,NULL);
    STATIC_ARENA_REQUIRE(peerHandle&&WaitForSingleObject(peer.ready,5000)==WAIT_OBJECT_0&&peer.observed
            &&peer.initial==originalWord&&audit_static_tls_word==UINT64_C(0xa6b7c8d9e0f12345)
            &&peer.address!=(uint64_t)(uintptr_t)&audit_static_tls_word,"actual OS threads shared a compiler TLS instance or initialization value");
    peerSpan=audit_static_tls_span(tls,peer.address);peerAllocation=native_memory_find(peer.address,sizeof(uint64_t),0);
    STATIC_ARENA_REQUIRE(peerSpan&&peerSpan!=mainSpan&&peerSpan->thread!=mainSpan->thread&&peerAllocation
            &&peerAllocation!=mainAllocation&&peerAllocation->imageTag==peerSpan,"independent current-thread TLS observation reused another thread tag");
    view=native_original.NewDirectByteBuffer(env,(void*)(uintptr_t)peer.address,sizeof(uint64_t));
    STATIC_ARENA_REQUIRE(view&&native_memory_buffer_observe(env,view,NULL,peer.address,sizeof(uint64_t),binding)&&binding[0]
            &&!host_static_image_prepare(&image,env)&&mainAllocation->alive&&peerAllocation->alive
            &&!mainAllocation->retiring&&!peerAllocation->retiring,"peer TLS alias failed to block/rollback aggregate image preparation");
    native_original.DeleteLocalRef(env,view);view=NULL;
    STATIC_ARENA_REQUIRE(audit_private_native_aliases_collected(env,peerAllocation)&&host_static_image_prepare(&image,env)
            &&mainAllocation->retiring&&peerAllocation->retiring,"quiescent observed TLS instances did not all join image preparation");
    host_static_image_cancel(&image,env);
    STATIC_ARENA_REQUIRE(!mainAllocation->retiring&&!peerAllocation->retiring,"cancelled image preparation left a TLS instance sealed");
    SetEvent(peer.release);joined=WaitForSingleObject(peerHandle,5000)==WAIT_OBJECT_0;
    STATIC_ARENA_REQUIRE(joined,"actual compiler TLS peer did not reach OS-thread termination");
    host_static_tls_reap(&env);
    STATIC_ARENA_REQUIRE(peerSpan->closed&&!peerAllocation->alive&&!mainSpan->closed&&mainAllocation->alive
            &&audit_static_tls_word==UINT64_C(0xa6b7c8d9e0f12345),"signaled peer OS thread retired another thread's TLS or changed its bytes");

    /* Arena observations below deliberately supply completion results. The
     * physical backing is real fixture storage; provider discovery and real
     * jemalloc reset are covered by the separate Host allocator fixture. */
    call=native_memory_host_arena_prepare(env,(void*)audit_static_arena_release,0,0,NATIVE_MEMORY_HOST_CONTEXT_CREATE,&selected,0,&denied);
    STATIC_ARENA_REQUIRE(call&&!denied,"arena create tracker seam refused its initial lease");
    native_memory_host_arena_complete(env,call,0,0,context,1,0);call=NULL;
    provider=native_memory_arena_provider((void*)audit_static_arena_release,0);arena=native_memory_arena_context(provider,context,0);
    call=native_memory_host_arena_prepare(env,(void*)audit_static_arena_release,0,0,NATIVE_MEMORY_HOST_ALLOCATE,&selected,0,&denied);
    STATIC_ARENA_REQUIRE(provider&&arena&&arena->created&&call&&!denied,"arena context/allocate completion seam unavailable");
    block=HeapAlloc(GetProcessHeap(),0,64);native_memory_host_arena_complete(env,call,(uint64_t)(uintptr_t)block,64,context,block!=NULL,0);call=NULL;
    arenaAllocation=native_memory_find((uint64_t)(uintptr_t)block,64,1);
    STATIC_ARENA_REQUIRE(block&&arenaAllocation&&arenaAllocation->hostFamily==NATIVE_MEMORY_HOST_ARENA
            &&arenaAllocation->hostContextEpoch==arena->epoch,"arena allocation seam lost its context incarnation");
    oldToken=(jlong)(uintptr_t)arenaAllocation;oldGeneration=arenaAllocation->generation;oldEpoch=arena->epoch;
    view=native_original.NewDirectByteBuffer(env,block,64);
    STATIC_ARENA_REQUIRE(view&&native_memory_buffer_observe(env,view,NULL,(uint64_t)(uintptr_t)block,64,binding)&&binding[0],"arena borrowed alias seam unavailable");
    call=native_memory_host_arena_prepare(env,(void*)audit_static_arena_release,0,context,NATIVE_MEMORY_HOST_ARENA_RESET,&selected,0,&denied);
    STATIC_ARENA_REQUIRE(!call&&denied&&arenaAllocation->alive&&arena->epoch==oldEpoch&&!arena->retired&&!provider->changing,
            "live borrowed arena alias permitted reset or changed its context generation");
    native_original.DeleteLocalRef(env,view);view=NULL;
    STATIC_ARENA_REQUIRE(audit_private_native_aliases_collected(env,arenaAllocation),"released arena alias remained reachable in completion seam");
    call=native_memory_host_arena_prepare(env,(void*)audit_static_arena_release,0,context,NATIVE_MEMORY_HOST_ARENA_RESET,&selected,0,&denied);
    STATIC_ARENA_REQUIRE(call&&!denied,"arena reset failure completion seam unavailable");
    native_memory_host_arena_complete(env,call,0,0,context,0,0);call=NULL;
    STATIC_ARENA_REQUIRE(arenaAllocation->alive&&arenaAllocation->generation==oldGeneration&&arena->epoch==oldEpoch&&!arena->retired&&!provider->changing,
            "failed arena reset completion retired or held its prior generation");
    call=native_memory_host_arena_prepare(env,(void*)audit_static_arena_release,0,context,NATIVE_MEMORY_HOST_ARENA_RESET,&selected,0,&denied);
    STATIC_ARENA_REQUIRE(call&&!denied,"arena reset success completion seam unavailable");
    native_memory_host_arena_complete(env,call,0,0,context,1,1);call=NULL;
    arena=native_memory_arena_context(provider,context,0);
    STATIC_ARENA_REQUIRE(!arenaAllocation->alive&&arena&&arena->epoch!=oldEpoch&&!arena->retired,
            "successful arena reset completion reused its former context generation");
    call=native_memory_host_arena_prepare(env,(void*)audit_static_arena_release,0,0,NATIVE_MEMORY_HOST_ALLOCATE,&selected,0,&denied);
    STATIC_ARENA_REQUIRE(call&&!denied,"replacement arena allocation completion seam unavailable");
    native_memory_host_arena_complete(env,call,(uint64_t)(uintptr_t)block,64,context,1,0);call=NULL;
    NativeMemoryAllocation *replacement=native_memory_find((uint64_t)(uintptr_t)block,64,1);
    view=native_original.NewDirectByteBuffer(env,block,64);
    STATIC_ARENA_REQUIRE(replacement&&replacement!=arenaAllocation&&replacement->hostContextEpoch==arena->epoch
            &&view&&native_memory_buffer_observe(env,view,NULL,(uint64_t)(uintptr_t)block,64,binding)&&binding[0]
            &&!native_memory_buffer_use(env,view,oldToken,oldGeneration,(uint64_t)(uintptr_t)block,64,1),
            "same-address arena replacement accepted a stale buffer generation");
done:
    if(peer.release)SetEvent(peer.release);
    if(peerHandle){if(!joined)WaitForSingleObject(peerHandle,INFINITE);CloseHandle(peerHandle);}
    if(peer.ready)CloseHandle(peer.ready);if(peer.release)CloseHandle(peer.release);
    if(initialized){
        if(native_original.ExceptionCheck(env))native_original.ExceptionClear(env);
        if(call)native_memory_host_cancel(call);
        if(view)native_original.DeleteLocalRef(env,view);if(module)native_original.DeleteLocalRef(env,module);
        while(host_static_tls_threads){HostStaticTlsThread *entry=host_static_tls_threads;host_static_tls_threads=entry->next;
            if(entry->monitor)host_real_close(entry->monitor);free(entry);}
        while(host_static_tls_images){HostStaticTlsImage *entry=host_static_tls_images;host_static_tls_images=entry->next;
            while(entry->spans){HostStaticTlsSpan *span=entry->spans;entry->spans=span->next;native_owner_links_free(span->owners);free(span);}
            native_owner_links_free(entry->owners);free(entry);}
        while(native_memory_allocations){NativeMemoryAllocation *entry=native_memory_allocations;native_memory_allocations=entry->next;
            native_memory_released(env,entry);native_memory_reap_references(env,entry);
            native_owner_links_free(entry->sources);native_owner_links_free(entry->readers);native_owner_links_free(entry->preserved);
            while(entry->interests){NativeMemoryInterest *interest=entry->interests;entry->interests=interest->next;free(interest);}DeleteCriticalSection(&entry->gate);free(entry);}
        if(block)audit_static_arena_release(block);
        while(native_memory_arena_contexts){NativeHostArenaContext *entry=native_memory_arena_contexts;native_memory_arena_contexts=entry->next;native_owner_links_free(entry->sources);free(entry);}
        while(native_memory_arena_providers){NativeHostArenaProvider *entry=native_memory_arena_providers;native_memory_arena_providers=entry->next;free(entry);}native_memory_host_context_epoch=0;
        while(native_memory_images){NativeMemoryImageState *entry=native_memory_images;native_memory_images=entry->next;free(entry);}
        native_owner_links_free(native_memory_unknown);native_memory_unknown=NULL;
        while(native_owners){Owner *entry=native_owners;native_owners=entry->next;if(entry->module)native_original.DeleteWeakGlobalRef(env,entry->module);free(entry);}
        native_private_unregister(&thread.jniRange);
        if(native_controller)native_original.DeleteGlobalRef(env,native_controller);if(native_controls)native_original.DeleteGlobalRef(env,native_controls);
        native_controller=native_controls=NULL;native_memory_gate=NULL;
        if(fixtureTls!=TLS_OUT_OF_INDEXES){TlsSetValue(fixtureTls,NULL);TlsFree(fixtureTls);}native_tls=TLS_OUT_OF_INDEXES;
        memset(&native_original,0,sizeof(native_original));
    }
    audit_static_tls_word=originalWord;
    if(failure)audit_physical_failure(env,failure);
#undef STATIC_ARENA_REQUIRE
    return failure?JNI_FALSE:JNI_TRUE;
}
