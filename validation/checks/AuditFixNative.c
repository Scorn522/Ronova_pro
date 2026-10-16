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
