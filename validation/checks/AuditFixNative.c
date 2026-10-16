/* Companion to AuditFixCheck. Exercise production Host code against this
 * fixture DLL's real import table and real Windows children. The source/image
 * registration is fixture-owned; this does not certify game library discovery. */
#define JNI_OnLoad audit_backend_on_load
#include "../../native/control.c"
#undef JNI_OnLoad

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
