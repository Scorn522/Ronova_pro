#ifdef RONOVA_RANGE_CONTENTION_AGENT
/* Real VM attachment traffic across premain, using only exported JNI/JVMTI.
 * No production source is included and no controller directories are seeded. */
#include <jni.h>
#include <jvmti.h>
#include <windows.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
static JavaVM *audit_range_vm;
static HANDLE audit_range_workers[4];
static volatile LONG audit_range_stop,audit_range_failed;
static LONG64 audit_range_calls[4];
static DWORD WINAPI audit_range_worker(void *argument){
    unsigned index=(unsigned)(uintptr_t)argument;JNIEnv *env=NULL;
    if((*audit_range_vm)->AttachCurrentThreadAsDaemon(audit_range_vm,(void**)&env,NULL)!=JNI_OK||!env){
        InterlockedExchange(&audit_range_failed,1);return 1;}
    ULONGLONG until=GetTickCount64()+30000;
    while(!InterlockedCompareExchange(&audit_range_stop,0,0)&&GetTickCount64()<until){
        JNIEnv *current=NULL;
        if((*audit_range_vm)->GetEnv(audit_range_vm,(void**)&current,JNI_VERSION_1_8)!=JNI_OK||current!=env){
            InterlockedExchange(&audit_range_failed,1);break;}
        audit_range_calls[index]++;
        if(!(audit_range_calls[index]&15))Sleep(1);
    }
    if(!InterlockedCompareExchange(&audit_range_stop,0,0))InterlockedExchange(&audit_range_failed,1);
    if((*audit_range_vm)->DetachCurrentThread(audit_range_vm)!=JNI_OK)InterlockedExchange(&audit_range_failed,1);
    return 0;
}
static jlong JNICALL audit_range_finish(JNIEnv *env,jclass type){
    (void)env;(void)type;InterlockedExchange(&audit_range_stop,1);
    if(WaitForMultipleObjects(4,audit_range_workers,TRUE,5000)!=WAIT_OBJECT_0)return -1;
    LONG64 total=0;
    for(unsigned i=0;i<4;i++){
        if(!audit_range_calls[i])InterlockedExchange(&audit_range_failed,1);
        total+=audit_range_calls[i];CloseHandle(audit_range_workers[i]);audit_range_workers[i]=NULL;
    }
    return InterlockedCompareExchange(&audit_range_failed,0,0)?-1:total;
}
static void JNICALL audit_range_vm_init(jvmtiEnv *api,JNIEnv *env,jthread thread){
    (void)api;(void)thread;
    jclass check=(*env)->FindClass(env,"dev/ronova/pro/AuditFixCheck");
    JNINativeMethod method={"rangeContentionFinish0","()J",(void*)audit_range_finish};
    if(!check||(*env)->RegisterNatives(env,check,&method,1)!=JNI_OK){
        fprintf(stderr,"RANGE_CONTENTION_REGISTRATION_FAILED\n");return;}
    (*env)->DeleteLocalRef(env,check);
    for(unsigned i=0;i<4;i++){
        audit_range_workers[i]=CreateThread(NULL,0,audit_range_worker,(void*)(uintptr_t)i,0,NULL);
        if(!audit_range_workers[i])InterlockedExchange(&audit_range_failed,1);
    }
    fprintf(stderr,"RANGE_CONTENTION_REAL_VM_WORKERS_STARTED\n");
}
JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *vm,char *options,void *reserved){
    (void)options;(void)reserved;jvmtiEnv *api=NULL;audit_range_vm=vm;
    if((*vm)->GetEnv(vm,(void**)&api,JVMTI_VERSION_1_2)!=JNI_OK||!api)return JNI_ERR;
    jvmtiEventCallbacks callbacks;memset(&callbacks,0,sizeof(callbacks));callbacks.VMInit=audit_range_vm_init;
    if((*api)->SetEventCallbacks(api,&callbacks,sizeof(callbacks))!=JVMTI_ERROR_NONE
            ||(*api)->SetEventNotificationMode(api,JVMTI_ENABLE,JVMTI_EVENT_VM_INIT,NULL)!=JVMTI_ERROR_NONE)return JNI_ERR;
    return JNI_OK;
}
#else
/* Companion to AuditFixCheck. Exercise production Host code against this
 * fixture DLL's real import table and real Windows children. The source/image
 * registration is fixture-owned; this does not certify game library discovery. */
#ifndef RONOVA_LIVE_JNI_ONLY
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
#else
#include <jni.h>
#include <jvmti.h>
#include <windows.h>
#include <stdint.h>
#include <string.h>
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm,void *reserved){(void)vm;(void)reserved;return JNI_VERSION_1_8;}
static void audit_physical_failure(JNIEnv *env,const char *message){
    if((*env)->ExceptionCheck(env))return;
    jclass type=(*env)->FindClass(env,"java/lang/AssertionError");
    if(type){(*env)->ThrowNew(env,type,message);(*env)->DeleteLocalRef(env,type);}
}
#endif

/* This entry runs only under the already installed production premain backend,
 * in a separate JVM. It supplies no Owner, table identity or controller state.
 * The bare system export deliberately simulates a saved native write path so
 * the subsequent real guarded JNI lookup must repair actual table damage. */
typedef BOOL (WINAPI *AuditJniProtect)(LPVOID,SIZE_T,DWORD,PDWORD);
static AuditJniProtect audit_jni_real_protect(void){
    HMODULE image=GetModuleHandleW(L"kernelbase.dll");if(!image)return NULL;
    uintptr_t base=(uintptr_t)image;const IMAGE_DOS_HEADER *dos=(const IMAGE_DOS_HEADER*)image;
    if(dos->e_magic!=IMAGE_DOS_SIGNATURE||dos->e_lfanew<=0||dos->e_lfanew>0x100000)return NULL;
    const IMAGE_NT_HEADERS64 *nt=(const IMAGE_NT_HEADERS64*)(base+(uintptr_t)dos->e_lfanew);
    if(nt->Signature!=IMAGE_NT_SIGNATURE||nt->OptionalHeader.Magic!=IMAGE_NT_OPTIONAL_HDR64_MAGIC)return NULL;
    DWORD size=nt->OptionalHeader.SizeOfImage;IMAGE_DATA_DIRECTORY directory=nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_EXPORT];
    if(directory.Size<sizeof(IMAGE_EXPORT_DIRECTORY)||directory.VirtualAddress>size||directory.Size>size-directory.VirtualAddress)return NULL;
    const IMAGE_EXPORT_DIRECTORY *exports=(const IMAGE_EXPORT_DIRECTORY*)(base+directory.VirtualAddress);
    if(exports->AddressOfNames>size||exports->NumberOfNames>(size-exports->AddressOfNames)/sizeof(DWORD)
            ||exports->AddressOfNameOrdinals>size||exports->NumberOfNames>(size-exports->AddressOfNameOrdinals)/sizeof(WORD)
            ||exports->AddressOfFunctions>size||exports->NumberOfFunctions>(size-exports->AddressOfFunctions)/sizeof(DWORD))return NULL;
    const DWORD *names=(const DWORD*)(base+exports->AddressOfNames),*functions=(const DWORD*)(base+exports->AddressOfFunctions);
    const WORD *ordinals=(const WORD*)(base+exports->AddressOfNameOrdinals);
    for(DWORD i=0;i<exports->NumberOfNames;i++){
        if(names[i]>=size||sizeof("VirtualProtect")>size-names[i]||memcmp((void*)(base+names[i]),"VirtualProtect",sizeof("VirtualProtect")))continue;
        if(ordinals[i]>=exports->NumberOfFunctions)return NULL;DWORD target=functions[ordinals[i]];
        if(!target||target>=size||(target>=directory.VirtualAddress&&target-directory.VirtualAddress<directory.Size))return NULL;
        return (AuditJniProtect)(base+target);
    }
    return NULL;
}
typedef struct AuditJniPermission {void *address;SIZE_T length;DWORD previous;int changed;} AuditJniPermission;
static int audit_jni_open(AuditJniProtect protect,const void *table,DWORD protection,AuditJniPermission *regions,unsigned *count){
    uintptr_t at=(uintptr_t)table,last=at+sizeof(struct JNINativeInterface_);*count=0;
    if(!protect||!at||last<at)return 0;
    while(at<last){MEMORY_BASIC_INFORMATION memory;
        if(*count==4||!VirtualQuery((void*)at,&memory,sizeof(memory))||memory.State!=MEM_COMMIT
                ||(memory.Protect&(PAGE_GUARD|PAGE_NOACCESS))||memory.RegionSize>UINTPTR_MAX-(uintptr_t)memory.BaseAddress)return 0;
        uintptr_t end=(uintptr_t)memory.BaseAddress+memory.RegionSize;if(end>last)end=last;if(end<=at)return 0;
        AuditJniPermission *region=&regions[(*count)++];region->address=(void*)at;region->length=end-at;region->changed=0;
        if(!protect(region->address,region->length,protection,&region->previous))return 0;region->changed=1;at=end;
    }
    return 1;
}
static int audit_jni_close(AuditJniProtect protect,AuditJniPermission *regions,unsigned count){
    int valid=1;for(unsigned i=count;i>0;i--){AuditJniPermission *region=&regions[i-1];DWORD ignored=0;
        if(region->changed&&!protect(region->address,region->length,region->previous,&ignored))valid=0;}
    return valid;
}
static jint (JNICALL *audit_jni_version_original)(JNIEnv*);
static jint JNICALL audit_jni_version(JNIEnv *env){return audit_jni_version_original(env);}
static jint JNICALL audit_jni_unapproved_version(JNIEnv *env){return audit_jni_version_original(env);}
static jmethodID JNICALL audit_jni_bad_method(JNIEnv *env,jclass type,const char *name,const char *descriptor){
    (void)env;(void)type;(void)name;(void)descriptor;return NULL;
}
static jfieldID JNICALL audit_jni_bad_field(JNIEnv *env,jclass type,const char *name,const char *descriptor){
    (void)env;(void)type;(void)name;(void)descriptor;return NULL;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_AuditFixCheck_jniLiveTable0(JNIEnv *env,jclass check){
    JavaVM *vm=NULL;jvmtiEnv *api=NULL;jniNativeInterface *copy=NULL,*published=NULL;
    struct JNINativeInterface_ before=**env;struct JNINativeInterface_ *table=(struct JNINativeInterface_*)*env;
    AuditJniProtect protect=audit_jni_real_protect();AuditJniPermission regions[4];unsigned count=0;
    AuditJniPermission readonlyRegions[4];unsigned readonlyCount=0;
    const char *failure=NULL;int changed=0,versionChanged=0;
#define JNI_LIVE_REQUIRE(test,message) do{if(!(test)){failure=(message);goto jni_live_done;}}while(0)
    JNI_LIVE_REQUIRE(protect&&before.GetVersion&&before.GetMethodID&&before.GetFieldID&&before.GetStaticMethodID,"JNI_LIVE_REAL_ENTRIES_REQUIRED");
    JNI_LIVE_REQUIRE((*env)->GetJavaVM(env,&vm)==JNI_OK&&vm
            &&(*vm)->GetEnv(vm,(void**)&api,JVMTI_VERSION_1_2)==JNI_OK&&api,"JNI_LIVE_REAL_JVMTI_REQUIRED");
    JNIEnv *observed=NULL;
    JNI_LIVE_REQUIRE((*vm)->GetEnv(vm,(void**)&observed,JNI_VERSION_1_8)==JNI_OK&&observed==env,"JNI_LIVE_CURRENT_ENV_IDENTITY");
    JNI_LIVE_REQUIRE((*api)->GetJNIFunctionTable(api,&copy)==JVMTI_ERROR_NONE&&copy&&(const void*)copy!=(const void*)table,"JNI_LIVE_SNAPSHOT_IS_NOT_ACTIVE_TABLE");
    audit_jni_version_original=before.GetVersion;copy->GetVersion=audit_jni_version;
    versionChanged=1;JNI_LIVE_REQUIRE((*api)->SetJNIFunctionTable(api,copy)==JVMTI_ERROR_NONE,"JNI_LIVE_LEGAL_UNGUARDED_PUBLICATION");
    JNI_LIVE_REQUIRE((*env)->GetVersion==audit_jni_version&&(*env)->GetVersion(env)==before.GetVersion(env),"JNI_LIVE_UNGUARDED_ORIGINAL_RESULT");
    DWORD old=0;SetLastError(0);
    BOOL protectedEnv=VirtualProtect(env,sizeof(*env),PAGE_READWRITE,&old);DWORD envError=GetLastError();
    if(protectedEnv){DWORD ignored=0;protect(env,sizeof(*env),old,&ignored);}
    JNI_LIVE_REQUIRE(!protectedEnv&&envError==ERROR_ACCESS_DENIED,"JNI_LIVE_ENV_SLOT_VIRTUALPROTECT_NOT_REFUSED");
    SetLastError(0);BOOL protectedTable=VirtualProtect(table,sizeof(*table),PAGE_READWRITE,&old);DWORD tableError=GetLastError();
    if(protectedTable){DWORD ignored=0;protect(table,sizeof(*table),old,&ignored);}
    JNI_LIVE_REQUIRE(!protectedTable&&tableError==ERROR_ACCESS_DENIED,"JNI_LIVE_FULL_TABLE_VIRTUALPROTECT_NOT_REFUSED");
    void *tail=(unsigned char*)table+sizeof(*table)-1;SetLastError(0);
    BOOL protectedTail=VirtualProtect(tail,1,PAGE_READWRITE,&old);DWORD tailError=GetLastError();
    if(protectedTail){DWORD ignored=0;protect(tail,1,old,&ignored);}
    JNI_LIVE_REQUIRE(!protectedTail&&tailError==ERROR_ACCESS_DENIED,"JNI_LIVE_TABLE_TAIL_VIRTUALPROTECT_NOT_REFUSED");
    SIZE_T written=0;uintptr_t word=0;memcpy(&word,(unsigned char*)table+sizeof(*table)-sizeof(word),sizeof(word));SetLastError(0);
    BOOL write=WriteProcessMemory(GetCurrentProcess(),(unsigned char*)table+sizeof(*table)-sizeof(word),&word,sizeof(word),&written);
    JNI_LIVE_REQUIRE(!write&&!written&&GetLastError()==ERROR_ACCESS_DENIED,"JNI_LIVE_TABLE_TAIL_WRITE_NOT_REFUSED");
    int opened=audit_jni_open(protect,table,PAGE_READWRITE,regions,&count);
    if(!opened){audit_jni_close(protect,regions,count);count=0;failure="JNI_LIVE_UNGUARDED_PAGE_OPEN";goto jni_live_done;}
    InterlockedExchangePointer((void *volatile*)&table->GetVersion,(void*)audit_jni_unapproved_version);changed=1;
    JNI_LIVE_REQUIRE(audit_jni_close(protect,regions,count),"JNI_LIVE_UNGUARDED_PAGE_RESTORE");count=0;
    JNI_LIVE_REQUIRE(table->GetVersion==audit_jni_unapproved_version,"JNI_LIVE_UNGUARDED_FORCE_WRITE_NOT_OBSERVED");
    jmethodID rawBoundary=(*env)->GetStaticMethodID(env,check,"jniLiveBoundary","()I");
    JNI_LIVE_REQUIRE(rawBoundary&&!(*env)->ExceptionCheck(env)&&(*env)->GetVersion==before.GetVersion,"JNI_LIVE_UNAPPROVED_UNGUARDED_SLOT_NOT_RESTORED");
    JNI_LIVE_REQUIRE((*api)->GetJNIFunctionTable(api,&published)==JVMTI_ERROR_NONE&&published,"JNI_LIVE_UNGUARDED_REPUBLISH_SNAPSHOT");
    published->GetVersion=audit_jni_version;
    JNI_LIVE_REQUIRE((*api)->SetJNIFunctionTable(api,published)==JVMTI_ERROR_NONE&&(*env)->GetVersion==audit_jni_version,"JNI_LIVE_UNGUARDED_LEGAL_REPUBLISH");
    (*api)->Deallocate(api,(unsigned char*)published);published=NULL;
    opened=audit_jni_open(protect,table,PAGE_READWRITE,regions,&count);
    if(!opened){audit_jni_close(protect,regions,count);count=0;failure="JNI_LIVE_FORCE_WRITE_PAGE_OPEN";goto jni_live_done;}
    InterlockedExchangePointer((void *volatile*)&table->GetMethodID,(void*)audit_jni_bad_method);
    InterlockedExchangePointer((void *volatile*)&table->GetFieldID,(void*)audit_jni_bad_field);changed=1;
    JNI_LIVE_REQUIRE(audit_jni_close(protect,regions,count),"JNI_LIVE_FORCE_WRITE_PAGE_RESTORE");count=0;
    JNI_LIVE_REQUIRE(table->GetMethodID==audit_jni_bad_method&&table->GetFieldID==audit_jni_bad_field,"JNI_LIVE_FORCE_WRITE_NOT_OBSERVED");
    /* A real old-protect restore may leave the global table RO. This is the
     * exact restore/publication handoff which previously could crash HotSpot. */
    JNI_LIVE_REQUIRE(audit_jni_open(protect,table,PAGE_READONLY,readonlyRegions,&readonlyCount),"JNI_LIVE_READONLY_TABLE_SETUP");
    jmethodID boundary=(*env)->GetStaticMethodID(env,check,"jniLiveBoundary","()I");
    JNI_LIVE_REQUIRE(boundary&&!(*env)->ExceptionCheck(env),"JNI_LIVE_GUARDED_LOOKUP_FAILED");
    JNI_LIVE_REQUIRE((*env)->CallStaticIntMethod(env,check,boundary)==73&&!(*env)->ExceptionCheck(env),"JNI_LIVE_GUARDED_RESULT_CHANGED");
    JNI_LIVE_REQUIRE((*env)->GetMethodID==before.GetMethodID&&(*env)->GetFieldID==before.GetFieldID
            &&(*env)->GetVersion==audit_jni_version,"JNI_LIVE_CONTROLLED_REPAIR_OR_UNGUARDED_PRESERVATION");
    MEMORY_BASIC_INFORMATION after;
    JNI_LIVE_REQUIRE(VirtualQuery(table,&after,sizeof(after))&&after.Protect==PAGE_READONLY,"JNI_LIVE_PUBLICATION_DID_NOT_RESTORE_RO");
    JNI_LIVE_REQUIRE((*api)->GetJNIFunctionTable(api,&published)==JVMTI_ERROR_NONE&&published
            &&published->GetMethodID==before.GetMethodID&&published->GetFieldID==before.GetFieldID
            &&published->GetVersion==audit_jni_version,"JNI_LIVE_REPAIRED_TABLE_NOT_PUBLISHED");
jni_live_done:
    /* Restore permissions and selected actual pointers even after a failed
     * assertion. No old full-table snapshot overwrites unrelated agent slots. */
    jthrowable pending=before.ExceptionOccurred(env);if(pending)before.ExceptionClear(env);
    if(count&&protect){if(!audit_jni_close(protect,regions,count)&&!failure)failure="JNI_LIVE_FINAL_PAGE_RESTORE";count=0;}
    if(readonlyCount&&protect&&!audit_jni_close(protect,readonlyRegions,readonlyCount)&&!failure)failure="JNI_LIVE_FINAL_RO_RESTORE";
    if(changed&&protect){
        int opened=audit_jni_open(protect,table,PAGE_READWRITE,regions,&count);
        if(opened){InterlockedExchangePointer((void *volatile*)&table->GetMethodID,(void*)before.GetMethodID);
            InterlockedExchangePointer((void *volatile*)&table->GetFieldID,(void*)before.GetFieldID);
            InterlockedExchangePointer((void *volatile*)&table->GetVersion,(void*)before.GetVersion);}
        if((!audit_jni_close(protect,regions,count)||!opened)&&!failure)failure="JNI_LIVE_FINAL_POINTER_RESTORE";
    }
    if(published&&api)(*api)->Deallocate(api,(unsigned char*)published);
    if(copy&&api)(*api)->Deallocate(api,(unsigned char*)copy);copy=NULL;
    if(versionChanged&&api){
        if((*api)->GetJNIFunctionTable(api,&copy)!=JVMTI_ERROR_NONE||!copy){if(!failure)failure="JNI_LIVE_FINAL_SNAPSHOT";}
        else {copy->GetVersion=before.GetVersion;if((*api)->SetJNIFunctionTable(api,copy)!=JVMTI_ERROR_NONE&&!failure)failure="JNI_LIVE_FINAL_UNGUARDED_RESTORE";
            (*api)->Deallocate(api,(unsigned char*)copy);}
    }
    if(api&&(*api)->DisposeEnvironment(api)!=JVMTI_ERROR_NONE&&!failure)failure="JNI_LIVE_FINAL_ENV_DISPOSE";
    if(pending){before.Throw(env,pending);before.DeleteLocalRef(env,pending);}
    if(failure)audit_physical_failure(env,failure);
#undef JNI_LIVE_REQUIRE
    return failure?JNI_FALSE:JNI_TRUE;
}
/* Called from the real automatic module's HookTarget after its System.load.
 * Its JNI library/source registration and subsequent stop are production paths. */
JNIEXPORT jlongArray JNICALL Java_audit_jni_retirement_HookTarget_install0(JNIEnv *env,jclass target){
    (void)target;JavaVM *vm=NULL;jvmtiEnv *api=NULL;jniNativeInterface *copy=NULL,*cleanup=NULL;
    const char *failure=NULL;int attempted=0;struct JNINativeInterface_ before=**env;jlongArray result=NULL;
    if((*env)->GetJavaVM(env,&vm)!=JNI_OK||!vm||(*vm)->GetEnv(vm,(void**)&api,JVMTI_VERSION_1_2)!=JNI_OK||!api){failure="JNI_STOP_HOOK_REAL_ENVIRONMENT";goto hook_done;}
    if((*api)->GetJNIFunctionTable(api,&copy)!=JVMTI_ERROR_NONE||!copy){failure="JNI_STOP_HOOK_REAL_SNAPSHOT";goto hook_done;}
    audit_jni_version_original=before.GetVersion;copy->GetVersion=audit_jni_version;attempted=1;
    if((*api)->SetJNIFunctionTable(api,copy)!=JVMTI_ERROR_NONE||(*env)->GetVersion!=audit_jni_version
            ||(*env)->GetVersion(env)!=before.GetVersion(env)){failure="JNI_STOP_HOOK_LEGAL_PUBLICATION";goto hook_done;}
    jlong pointers[]={(jlong)(uintptr_t)audit_jni_version,(jlong)(uintptr_t)before.GetVersion};
    result=(*env)->NewLongArray(env,2);if(result)(*env)->SetLongArrayRegion(env,result,0,2,pointers);
    if(!result||(*env)->ExceptionCheck(env))failure="JNI_STOP_HOOK_REAL_RESULT";
hook_done:
    if(copy&&api){(*api)->Deallocate(api,(unsigned char*)copy);copy=NULL;}
    if(!failure&&api){
        if((*api)->DisposeEnvironment(api)==JVMTI_ERROR_NONE)api=NULL;
        else failure="JNI_STOP_HOOK_REAL_DISPOSE";
    }
    if(failure&&attempted&&api){
        jthrowable pending=before.ExceptionOccurred(env);if(pending)before.ExceptionClear(env);
        if((*api)->GetJNIFunctionTable(api,&cleanup)==JVMTI_ERROR_NONE&&cleanup){cleanup->GetVersion=before.GetVersion;
            (*api)->SetJNIFunctionTable(api,cleanup);(*api)->Deallocate(api,(unsigned char*)cleanup);}
        AuditJniProtect protect=audit_jni_real_protect();AuditJniPermission regions[4];unsigned count=0;
        if(protect&&audit_jni_open(protect,*env,PAGE_READWRITE,regions,&count))
            InterlockedExchangePointer((void *volatile*)&((struct JNINativeInterface_*)*env)->GetVersion,(void*)before.GetVersion);
        if(protect)audit_jni_close(protect,regions,count);
        if(pending){before.Throw(env,pending);before.DeleteLocalRef(env,pending);}
    }
    if(api&&(*api)->DisposeEnvironment(api)!=JVMTI_ERROR_NONE&&!failure)failure="JNI_STOP_HOOK_REAL_DISPOSE";
    if(failure){audit_physical_failure(env,failure);return NULL;}return result;
}
/* The owner module is stopped before this live main-module method is entered.
 * No call back into that module, native Owner injection or ready flag is used. */
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_AuditFixCheck_jniStoppedHookObserve0(JNIEnv *env,jclass check,jlong hook,jlong original){
    if(!hook||!original||hook==original||(*env)->GetVersion!=(void*)(uintptr_t)original
            ||(*env)->GetVersion==(void*)(uintptr_t)hook){audit_physical_failure(env,"JNI_STOP_HOOK_STILL_CURRENT");return JNI_FALSE;}
    jmethodID boundary=(*env)->GetStaticMethodID(env,check,"jniLiveBoundary","()I");
    if(!boundary||(*env)->ExceptionCheck(env)||(*env)->CallStaticIntMethod(env,check,boundary)!=73
            ||(*env)->GetVersion!=(void*)(uintptr_t)original){audit_physical_failure(env,"JNI_STOP_HOOK_ORIGINAL_BOUNDARY_UNAVAILABLE");return JNI_FALSE;}
    return JNI_TRUE;
}
#ifndef RONOVA_LIVE_JNI_ONLY
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

/* Supplied heap contexts exercise the production completion/alias seam, not
 * mimalloc provider discovery. Physical bytes come from these real routines. */
static void *audit_other_cpp_allocate(size_t bytes){return HeapAlloc(GetProcessHeap(),0,bytes);}
static void audit_other_cpp_release(void *pointer){audit_static_arena_release(pointer);}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_AuditFixCheck_privateOtherState0(JNIEnv *env,jclass type){
    const char *failure=NULL;int initialized=0,denied=0;DWORD fixtureTls=TLS_OUT_OF_INDEXES;
    NativeThread thread={0};Owner owner={0};OwnerLink selected={.owner=&owner};
    NativeHostMemoryCall *call=NULL,*outer=NULL,*nested=NULL;NativeHostArenaProvider *provider=NULL;
    NativeHostArenaContext *heap=NULL;NativeMemoryAllocation *row=NULL,*cppRow=NULL;
    NativeHostCppOriginalScope cpp={0};void *block=NULL,*cppBlock=NULL;jobject view=NULL,module=NULL;jlong binding[2]={0};
    uintptr_t context=0x41,backing=0x52;uint64_t generation=0,epoch=0;
#define OTHER_REQUIRE(CONDITION,MESSAGE) do{if(!(CONDITION)){failure=(MESSAGE);goto other_done;}}while(0)
    OTHER_REQUIRE(!native_controller&&!native_vm&&native_tls==TLS_OUT_OF_INDEXES&&!native_memory_allocations
            &&!native_memory_arena_contexts&&!native_memory_arena_providers,"other allocator seam requires an uninstalled isolated controller");
    native_original=**env;initialized=1;fixtureTls=TlsAlloc();native_tls=fixtureTls;owner.producer=1;
    OTHER_REQUIRE(fixtureTls!=TLS_OUT_OF_INDEXES&&TlsSetValue(fixtureTls,&thread),"other allocator seam actual TLS unavailable");
    jclass classType=native_original.FindClass(env,"java/lang/Class");
    jmethodID getModule=classType?native_original.GetMethodID(env,classType,"getModule","()Ljava/lang/Module;"):NULL;
    if(getModule)module=native_original.CallObjectMethod(env,type,getModule);
    if(classType)native_original.DeleteLocalRef(env,classType);
    owner.module=module?native_original.NewWeakGlobalRef(env,module):NULL;
    OTHER_REQUIRE(owner.module&&!native_original.ExceptionCheck(env),"other allocator seam actual source module unavailable");
    call=native_memory_host_other_prepare(env,(void*)audit_static_arena_release,0,0,NATIVE_MEMORY_HOST_CONTEXT_CREATE,&selected,0,&denied);
    OTHER_REQUIRE(call&&!denied,"other allocator heap create lease unavailable");
    native_memory_host_other_complete(env,call,0,0,context,1,0);call=NULL;
    provider=native_memory_arena_provider((void*)audit_static_arena_release,0);heap=native_memory_arena_context(provider,context,0);
    call=native_memory_host_other_prepare(env,(void*)audit_static_arena_release,0,context,NATIVE_MEMORY_HOST_ALLOCATE,&selected,0,&denied);
    OTHER_REQUIRE(provider&&heap&&heap->created&&call&&!denied,"other allocator actual-context completion unavailable");
    block=HeapAlloc(GetProcessHeap(),0,64);
    native_memory_host_other_complete(env,call,(uint64_t)(uintptr_t)block,64,context,block!=NULL,0);call=NULL;
    row=native_memory_find((uint64_t)(uintptr_t)block,64,1);
    OTHER_REQUIRE(block&&row&&row->hostFamily==NATIVE_MEMORY_HOST_OTHER&&!row->unknownUse,"other allocator lost exact live context storage");
    generation=row->generation;epoch=row->hostContextEpoch;
    view=native_original.NewDirectByteBuffer(env,block,64);
    OTHER_REQUIRE(view&&native_memory_buffer_observe(env,view,NULL,(uint64_t)(uintptr_t)block,64,binding)&&binding[0],"other allocator real JNI alias unavailable");
    call=native_memory_host_other_prepare(env,(void*)audit_static_arena_release,0,context,NATIVE_MEMORY_HOST_CONTEXT_FREE,&selected,0,&denied);
    OTHER_REQUIRE(!call&&denied&&row->alive&&row->generation==generation&&!heap->retired&&!provider->changing,
            "live borrowed other-allocator alias permitted destructive heap release");
    InterlockedIncrement(&row->typedObjects);
    call=native_memory_host_other_prepare(env,(void*)audit_static_arena_release,0,context,NATIVE_MEMORY_HOST_CONTEXT_DETACH,&selected,0,&denied);
    OTHER_REQUIRE(call&&!denied,"safe heap delete incorrectly rejected surviving alias/object storage");
    native_memory_host_other_complete(env,call,0,0,backing,1,0);call=NULL;
    OTHER_REQUIRE(row->alive&&row->generation==generation&&row->hostContext==backing&&row->hostContextEpoch!=epoch
            &&row->typedObjects==1&&heap->retired&&!row->unknownUse
            &&native_memory_buffer_use(env,view,binding[0],binding[1],(uint64_t)(uintptr_t)block,64,1),
            "safe heap delete retired storage, changed block generation, or invalidated its actual borrowed view");
    OTHER_REQUIRE(native_memory_buffer_use(env,view,binding[0],binding[1],(uint64_t)(uintptr_t)block,64,0),"migrated view could not release its exact user lease");
    call=native_memory_host_other_prepare(env,(void*)audit_static_arena_release,0,0,NATIVE_MEMORY_HOST_CONTEXT_CREATE,&selected,0,&denied);
    OTHER_REQUIRE(call&&!denied,"reused heap handle create lease unavailable");
    native_memory_host_other_complete(env,call,0,0,context,1,0);call=NULL;
    heap=native_memory_arena_context(provider,context,0);
    OTHER_REQUIRE(heap&&heap->epoch!=epoch&&row->hostContext==backing,"reused heap handle retained an earlier heap incarnation");
    call=native_memory_host_other_prepare(env,(void*)audit_static_arena_release,0,context,NATIVE_MEMORY_HOST_CONTEXT_FREE,&selected,0,&denied);
    OTHER_REQUIRE(call&&!denied,"a migrated alias was incorrectly attributed to a reused private heap");
    native_memory_host_other_complete(env,call,0,0,context,1,1);call=NULL;
    OTHER_REQUIRE(row->alive&&row->generation==generation,"new private heap destruction released previously migrated storage");
    call=native_memory_host_other_prepare(env,(void*)audit_static_arena_release,0,0,NATIVE_MEMORY_HOST_THREAD_DETACH,&selected,0,&denied);
    OTHER_REQUIRE(call&&!denied,"non-destructive thread teardown rejected surviving storage");
    native_memory_host_other_complete(env,call,0,0,0,1,0);call=NULL;
    OTHER_REQUIRE(row->alive&&row->generation==generation&&!row->hostContext&&!row->hostContextEpoch&&row->typedObjects==1,
            "thread teardown destroyed abandoned storage or retained a dead heap epoch");
    native_original.DeleteLocalRef(env,view);view=NULL;
    OTHER_REQUIRE(audit_private_native_aliases_collected(env,row),"released other allocator alias remained reachable");
    call=native_memory_host_other_prepare(env,(void*)audit_static_arena_release,(uint64_t)(uintptr_t)block,0,NATIVE_MEMORY_HOST_FREE,&selected,0,&denied);
    OTHER_REQUIRE(!call&&denied&&row->alive,"typed storage lost its raw-free guard after heap/thread migration");
    InterlockedDecrement(&row->typedObjects);
    call=native_memory_host_other_prepare(env,(void*)audit_static_arena_release,(uint64_t)(uintptr_t)block,0,NATIVE_MEMORY_HOST_REALLOCATE,&selected,0,&denied);
    OTHER_REQUIRE(call&&!denied,"abandoned block could not acquire its exact realloc lease");
    native_memory_host_other_complete(env,call,0,0,0,0,0);call=NULL;
    OTHER_REQUIRE(row->alive&&row->generation==generation,"failed other-allocator realloc retired its old generation");
    call=native_memory_host_other_prepare(env,(void*)audit_static_arena_release,(uint64_t)(uintptr_t)block,0,NATIVE_MEMORY_HOST_FREE,&selected,0,&denied);
    OTHER_REQUIRE(call&&!denied,"canonical free could not release an actually abandoned block");
    audit_static_arena_release(block);block=NULL;native_memory_host_other_complete(env,call,0,0,0,1,1);call=NULL;

    cpp.image=native_image((void*)audit_other_cpp_allocate);cpp.original=(void*)audit_other_cpp_allocate;
    cpp.release=(void*)audit_other_cpp_release;cpp.context=1;cpp.thread=GetCurrentThreadId();cpp.operation=NATIVE_MEMORY_HOST_ALLOCATE;
    thread.hostCppOriginal=&cpp;cppBlock=audit_other_cpp_allocate(64);
    native_memory_host_allocated(env,NATIVE_MEMORY_HOST_CRT,(void*)audit_other_cpp_allocate,(void*)audit_static_arena_release,0,0,
            (uint64_t)(uintptr_t)cppBlock,64,&selected,0);
    cppRow=native_memory_find((uint64_t)(uintptr_t)cppBlock,64,1);
    OTHER_REQUIRE(cppBlock&&cppRow&&!cppRow->unknownUse,"C++ nested storage seam actual CRT return unavailable");
    generation=cppRow->generation;
    native_memory_host_cpp_backing_allocated(env,&cpp,(void*)audit_other_cpp_allocate,(void*)audit_static_arena_release,0,
            (uint64_t)(uintptr_t)cppBlock,64,&selected,0);
    native_memory_host_cpp_allocated(env,&cpp,(void*)audit_other_cpp_allocate,(void*)audit_other_cpp_release,1,
            (uint64_t)(uintptr_t)cppBlock,64,&selected,0);
    OTHER_REQUIRE(cppRow->hostFamily==NATIVE_MEMORY_HOST_CPP&&cppRow->generation==generation&&!cppRow->unknownUse
            &&cppRow->hostStorageRelease==(void*)audit_static_arena_release&&cppRow->release==(void*)audit_other_cpp_release
            &&!native_memory_private(env,cppRow,&selected),"C++ nested storage was duplicated, lost its provider pair, or gained automatic raw-delete permission");
    native_memory_host_cpp_scope_end(&cpp);thread.hostCppOriginal=NULL;
    outer=native_memory_host_prepare(env,NATIVE_MEMORY_HOST_CPP,(void*)audit_other_cpp_release,1,(uint64_t)(uintptr_t)cppBlock,0,
            NATIVE_MEMORY_HOST_FREE,&selected,0,&denied);
    OTHER_REQUIRE(outer&&!denied,"C++ exact outer delete lease unavailable");
    cpp.original=(void*)audit_other_cpp_release;cpp.operation=NATIVE_MEMORY_HOST_FREE;cpp.call=outer;thread.hostCppOriginal=&cpp;
    nested=native_memory_host_cpp_backing_prepare(env,&cpp,(void*)audit_other_cpp_allocate,0,(uint64_t)(uintptr_t)cppBlock,
            NATIVE_MEMORY_HOST_FREE,&selected,0,&denied);
    OTHER_REQUIRE(!nested&&denied&&cppRow->alive&&cppRow->users==1&&cppRow->changing==1,"C++ outer scope authorized a mismatched actual backing provider");
    nested=native_memory_host_cpp_backing_prepare(env,&cpp,(void*)audit_static_arena_release,0,(uint64_t)(uintptr_t)cppBlock,
            NATIVE_MEMORY_HOST_FREE,&selected,0,&denied);
    OTHER_REQUIRE(nested&&!denied&&nested->holds&&nested->holds->borrowed&&cppRow->users==1&&cppRow->changing==1,
            "C++ actual nested CRT free could not borrow its exact outer generation");
    audit_static_arena_release(cppBlock);cppBlock=NULL;native_memory_host_complete(env,nested,0,0,1,1);nested=NULL;
    native_memory_host_complete(env,outer,0,0,1,1);outer=NULL;cpp.call=NULL;
    OTHER_REQUIRE(!cppRow->alive&&cppRow->generation!=generation&&!cppRow->users&&!cppRow->changing,
            "C++ nested actual free did not retire exactly once or leaked an outer lease");
    fprintf(stdout,"PRIVATE_OTHER_STATE_SEAM_PASS supplied_mimalloc_heap_completions real_storage_and_JNI_alias alias_blocks_destroy safe_delete_migrates_same_generation reused_heap_isolated thread_done_abandons typed_rawfree_denied failed_realloc_keeps_old exact_CPP_promote_and_nested_CRT_borrow no_provider_discovery_claim\n");fflush(stdout);
other_done:
    if(initialized){
        if(native_original.ExceptionCheck(env))native_original.ExceptionClear(env);
        native_memory_host_cpp_scope_end(&cpp);thread.hostCppOriginal=NULL;
        if(nested)native_memory_host_cancel(nested);if(outer)native_memory_host_cancel(outer);if(call)native_memory_host_cancel(call);
        if(view)native_original.DeleteLocalRef(env,view);
        if(block)audit_static_arena_release(block);if(cppBlock)audit_static_arena_release(cppBlock);
        while(native_memory_allocations){NativeMemoryAllocation *entry=native_memory_allocations;native_memory_allocations=entry->next;
            native_memory_released(env,entry);native_memory_reap_references(env,entry);
            native_owner_links_free(entry->sources);native_owner_links_free(entry->readers);native_owner_links_free(entry->preserved);
            DeleteCriticalSection(&entry->gate);free(entry);}
        while(native_memory_arena_contexts){NativeHostArenaContext *entry=native_memory_arena_contexts;native_memory_arena_contexts=entry->next;native_owner_links_free(entry->sources);free(entry);}
        while(native_memory_arena_providers){NativeHostArenaProvider *entry=native_memory_arena_providers;native_memory_arena_providers=entry->next;free(entry);}
        native_memory_host_context_epoch=0;native_owner_links_free(native_memory_unknown);native_memory_unknown=NULL;
        if(owner.module)native_original.DeleteWeakGlobalRef(env,owner.module);if(module)native_original.DeleteLocalRef(env,module);
        if(fixtureTls!=TLS_OUT_OF_INDEXES){TlsSetValue(fixtureTls,NULL);TlsFree(fixtureTls);}native_tls=TLS_OUT_OF_INDEXES;
        memset(&native_original,0,sizeof(native_original));
    }
    if(failure)audit_physical_failure(env,failure);
#undef OTHER_REQUIRE
    return failure?JNI_FALSE:JNI_TRUE;
}

/* The source/image registration below is supplied by this isolated seam.
 * The external DLL owns the actual std::pmr pool, Windows backing, polymorphic
 * object, fields and destructors. SDK receipts and all original calls are real;
 * this is not evidence that live Core discovery admitted an ordinary DLL. */
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_AuditFixCheck_privateCustomAllocatorState0(JNIEnv *env,jclass type,jstring path){
    const char *failure=NULL;int initialized=0,ran=0,finished=0,denied=0;DWORD fixtureTls=TLS_OUT_OF_INDEXES;
    HMODULE fixture=NULL,backendPin=NULL,backend=native_image((void*)Java_dev_ronova_pro_AuditFixCheck_privateCustomAllocatorState0);
    const jchar *characters=NULL;jobject module=NULL;Owner owner={0};OwnerLink selected={.owner=&owner};
    NativeThread thread={0};NativeLibrary library={0};Binding binding={0};HostImage *image=NULL;
    NativeHostMemoryCall *probe=NULL;NativeAllocatorContext *context=NULL,*standardContext=NULL;
    NativeAllocatorReceipt *objectReceipt=NULL,*payloadReceipt=NULL;NativeMemoryAllocation *objectRow=NULL,*payloadRow=NULL;
    NativeAllocatorReceipt *oldVector=NULL,*oldString=NULL,*newVector=NULL,*newString=NULL;
    int standardBacking=0;
    uint64_t contextToken=0,contextEpoch=0,objectToken=0,objectGeneration=0,payloadToken=0,payloadGeneration=0,fieldEpoch=0;
    int (__cdecl *bind)(HMODULE)=NULL;int (__cdecl *run)(void)=NULL;int (__cdecl *hold)(int)=NULL;
    int (__cdecl *attempt)(void)=NULL;int (__cdecl *finish)(void)=NULL;uint64_t (__cdecl *current)(unsigned)=NULL;
    int (__cdecl *containersRun)(void)=NULL;int (__cdecl *containersHold)(int)=NULL;int (__cdecl *containersMutate)(void)=NULL;
    int (__cdecl *attemptHeapDestroy)(void)=NULL;
    int (__cdecl *containersShapeMismatch)(void)=NULL;
    unsigned char payloadBefore[32],payloadAfter[32];
#define CUSTOM_REQUIRE(CONDITION,MESSAGE) do{if(!(CONDITION)){failure=(MESSAGE);goto custom_done;}}while(0)
    CUSTOM_REQUIRE(path&&backend&&!native_controller&&!native_vm&&native_tls==TLS_OUT_OF_INDEXES&&!native_memory_allocations
            &&!native_allocator_contexts&&!native_allocator_receipts&&!native_allocator_calls&&!native_object_states
            &&!host_images&&!native_libraries&&!native_tooling_pin,"custom allocator seam requires an uninstalled isolated backend");
    characters=(*env)->GetStringChars(env,path,NULL);
    CUSTOM_REQUIRE(characters,"custom allocator fixture path unavailable");fixture=LoadLibraryW((LPCWSTR)characters);
    (*env)->ReleaseStringChars(env,path,characters);characters=NULL;
    CUSTOM_REQUIRE(fixture,"actual external custom allocator/typed-field fixture did not load");
    bind=(int (__cdecl*)(HMODULE))GetProcAddress(fixture,"audit_allocator_fields_bind");
    run=(int (__cdecl*)(void))GetProcAddress(fixture,"audit_allocator_fields_run");
    current=(uint64_t (__cdecl*)(unsigned))GetProcAddress(fixture,"audit_allocator_fields_current");
    hold=(int (__cdecl*)(int))GetProcAddress(fixture,"audit_allocator_fields_hold");
    attempt=(int (__cdecl*)(void))GetProcAddress(fixture,"audit_allocator_fields_attempt_free");
    finish=(int (__cdecl*)(void))GetProcAddress(fixture,"audit_allocator_fields_finish");
    containersRun=(int (__cdecl*)(void))GetProcAddress(fixture,"audit_allocator_fields_containers_run");
    containersHold=(int (__cdecl*)(int))GetProcAddress(fixture,"audit_allocator_fields_containers_hold");
    containersMutate=(int (__cdecl*)(void))GetProcAddress(fixture,"audit_allocator_fields_containers_mutate");
    attemptHeapDestroy=(int (__cdecl*)(void))GetProcAddress(fixture,"audit_allocator_fields_attempt_heap_destroy");
    containersShapeMismatch=(int (__cdecl*)(void))GetProcAddress(fixture,"audit_allocator_fields_containers_shape_mismatch");
    CUSTOM_REQUIRE(bind&&run&&current&&hold&&attempt&&finish&&containersRun&&containersHold&&containersMutate&&attemptHeapDestroy,
            "actual fixture exports unavailable");
    CUSTOM_REQUIRE(containersShapeMismatch,"actual public-shape mismatch fixture export unavailable");
    native_original=**env;initialized=1;fixtureTls=TlsAlloc();native_tls=fixtureTls;
    CUSTOM_REQUIRE(fixtureTls!=TLS_OUT_OF_INDEXES&&TlsSetValue(fixtureTls,&thread),"custom allocator fixture TLS unavailable");
    CUSTOM_REQUIRE(GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,
            (LPCWSTR)Java_dev_ronova_pro_AuditFixCheck_privateCustomAllocatorState0,&backendPin)&&backendPin==backend,
            "custom allocator seam actual control image pin unavailable");
    IMAGE_DOS_HEADER *backendDos=(IMAGE_DOS_HEADER*)backend;
    IMAGE_NT_HEADERS64 *backendNt=(IMAGE_NT_HEADERS64*)((unsigned char*)backend+backendDos->e_lfanew);
    native_tooling_pin=backendPin;native_tooling_image_begin=(uintptr_t)backend;
    native_tooling_image_end=native_tooling_image_begin+backendNt->OptionalHeader.SizeOfImage;
    jclass classType=native_original.FindClass(env,"java/lang/Class");
    jmethodID getModule=classType?native_original.GetMethodID(env,classType,"getModule","()Ljava/lang/Module;"):NULL;
    if(getModule)module=native_original.CallObjectMethod(env,type,getModule);
    if(classType)native_original.DeleteLocalRef(env,classType);
    owner.module=module?native_original.NewWeakGlobalRef(env,module):NULL;owner.producer=1;
    CUSTOM_REQUIRE(owner.module&&!native_original.ExceptionCheck(env),"custom allocator fixture actual Java module unavailable");
    library.handle=fixture;library.owners=&selected;library.jni=JNI_TRUE;library.alive=library.complete=1;
    binding.library=&library;binding.owners=&selected;thread.stack[0]=&binding;thread.depth=1;thread.toolingRestricted=1;
    native_libraries=&library;
    /* Resolve the two actual SDK getters before this isolated restricted
     * callback installs GPA admission. GetApi itself still runs afterwards in
     * the registered fixture image and must capture its real source scope. */
    CUSTOM_REQUIRE(bind(backend),"actual fixture SDK getter addresses unavailable");
    CUSTOM_REQUIRE(host_observe_image_scope(fixture,NULL)&&library.hostImage,"actual mapped fixture imports were not observed");
    image=library.hostImage;
    int ranActual=run();ran=1;
    if(!ranActual){
        fprintf(stderr,"PRIVATE_CUSTOM_ACTUAL_FAILED stage=%llu ctx=%llu epoch=%llu objectReceipt=%llu objectGen=%llu payloadReceipt=%llu payloadGen=%llu typedRow=%llu scalar=%llu owned=%llu coverage=%llu pending=%llu constructors=%llu destructors=%llu originalDeallocations=%llu lastError=%lu\n",
                (unsigned long long)current(21),(unsigned long long)current(12),(unsigned long long)current(13),
                (unsigned long long)current(8),(unsigned long long)current(9),(unsigned long long)current(10),(unsigned long long)current(11),
                (unsigned long long)current(0),(unsigned long long)current(2),(unsigned long long)current(3),(unsigned long long)current(18),
                (unsigned long long)current(17),(unsigned long long)current(15),(unsigned long long)current(16),(unsigned long long)current(20),
                (unsigned long)GetLastError());fflush(stderr);
    }
    CUSTOM_REQUIRE(ranActual,"actual custom allocator/typed field SDK did not produce covered original operations");
    contextToken=current(12);contextEpoch=current(13);objectToken=current(8);objectGeneration=current(9);
    payloadToken=current(10);payloadGeneration=current(11);fieldEpoch=current(14);
    context=native_allocator_context_find(contextToken);objectReceipt=native_allocator_receipt_find(objectToken);
    payloadReceipt=native_allocator_receipt_find(payloadToken);
    objectRow=objectReceipt?objectReceipt->allocation:NULL;payloadRow=payloadReceipt?payloadReceipt->allocation:NULL;
    CUSTOM_REQUIRE(contextToken&&contextEpoch&&objectToken&&objectGeneration&&payloadToken&&payloadGeneration
            &&current(18)==3&&current(2)&&current(3)&&current(5)==42&&current(15)==1&&!current(16)
            &&context&&context->provider==fixture&&context->epoch==contextEpoch&&native_allocator_current(objectReceipt)
            &&native_allocator_current(payloadReceipt)&&objectRow&&payloadRow&&objectRow!=payloadRow
            &&objectRow->hostFamily==NATIVE_MEMORY_HOST_CUSTOM&&payloadRow->hostFamily==NATIVE_MEMORY_HOST_CUSTOM
            &&objectRow->generation==objectGeneration&&payloadRow->generation==payloadGeneration
            &&objectRow->typedObjects&&payloadRow->typedObjects&&!objectRow->unknownUse&&!payloadRow->unknownUse
            &&objectRow->hostCustomParent&&payloadRow->hostCustomParent,"nonzero real custom/typed receipts lost their exact provider or backing generation");
    CUSTOM_REQUIRE(!native_allocator_state_api.free_begin(payloadToken,payloadGeneration)&&native_allocator_current(payloadReceipt),
            "a different actual provider consumed a custom allocator receipt");
    CUSTOM_REQUIRE(containersRun(),"actual non-PMR std allocator/vector/string mutation failed");
    standardContext=native_allocator_context_find(current(22));oldVector=native_allocator_receipt_find(current(26));
    oldString=native_allocator_receipt_find(current(27));
    CUSTOM_REQUIRE(standardContext&&standardContext->provider==fixture&&standardContext->epoch==current(23)
            &&native_allocator_current(oldVector)&&native_allocator_current(oldString)&&oldVector!=oldString
            &&oldVector->context==standardContext&&oldString->context==standardContext&&current(24)&&current(25)
            &&current(32)==16&&current(33)==64&&!current(34)
            &&oldVector->allocation->hostCustomParent&&oldString->allocation->hostCustomParent
            &&oldVector->allocation->typedObjects&&oldString->allocation->typedObjects,
            "real standard allocator receipts/public container views lost exact shared state or typed storage");
    NativeMemoryAllocation *vectorBacking=oldVector->allocation->hostCustomParent,*stringBacking=oldString->allocation->hostCustomParent;
    CUSTOM_REQUIRE((vectorBacking->hostFamily==NATIVE_MEMORY_HOST_CRT||vectorBacking->hostFamily==NATIVE_MEMORY_HOST_CPP)
            &&(stringBacking->hostFamily==NATIVE_MEMORY_HOST_CRT||stringBacking->hostFamily==NATIVE_MEMORY_HOST_CPP)
            &&vectorBacking->alive&&stringBacking->alive&&vectorBacking->release&&stringBacking->release
            &&vectorBacking->generation==oldVector->allocation->hostCustomParentGeneration
            &&stringBacking->generation==oldString->allocation->hostCustomParentGeneration,
            "actual std allocator backing was not an exact observed CRT/C++ generation");
    standardBacking=(vectorBacking->hostFamily==NATIVE_MEMORY_HOST_CPP?1:2)|(stringBacking->hostFamily==NATIVE_MEMORY_HOST_CPP?1:2);
    uint64_t vectorCalls=current(28),stringCalls=current(29);
    CUSTOM_REQUIRE(containersHold(1)&&!containersMutate()&&current(28)==vectorCalls&&current(29)==stringCalls
            &&native_allocator_current(oldVector)&&native_allocator_current(oldString)&&!current(34),
            "active public container views reached original reserve/resize or changed allocator receipts");
    CUSTOM_REQUIRE(containersHold(0)&&containersMutate()&&current(28)==vectorCalls+2&&current(29)==stringCalls+2
            &&current(30)==4&&current(31)==3&&current(32)==4&&current(33)==3&&!current(34)
            &&!native_allocator_current(oldVector)&&!native_allocator_current(oldString)
            &&!oldVector->allocation->alive&&!oldString->allocation->alive&&!vectorBacking->alive&&!stringBacking->alive,
            "real grow/shrink failed to retire old std storage, reject stale views, or preserve exact original calls");
    newVector=native_allocator_receipt_find(current(26));newString=native_allocator_receipt_find(current(27));
    CUSTOM_REQUIRE(native_allocator_current(newVector)&&native_allocator_current(newString)&&newVector!=oldVector&&newString!=oldString
            &&newVector->context==standardContext&&newString->context==standardContext&&!standardContext->retired
            &&!newVector->allocation->unknownUse&&!newString->allocation->unknownUse,
            "current public container views failed to rebind to live std allocator generations");
    NativeMemoryAllocation *elementRow=newVector->allocation;NativeMemoryRange *elementRange=NULL;
    EnterCriticalSection(&elementRow->gate);
    for(NativeMemoryRange *entry=elementRow->ranges;entry;entry=entry->next)
        if(entry->start==0&&entry->length==4*sizeof(uint32_t)){elementRange=entry;break;}
    uint32_t elementsBefore[4],elementsAfter[4];
    int elementHistory=elementRange&&elementRange->revision&&elementRange->revision->objectView
            &&elementRange->revision->previous&&elementRange->revision->previous->objectView
            &&elementRange->revision->objectView!=elementRange->revision->previous->objectView;
    int elementRead=native_memory_read(elementRow->address,elementsBefore,sizeof(elementsBefore));owner.stopped=1;
    int elementRestored=elementRange&&native_memory_restore_range(elementRow,elementRange,&selected);owner.stopped=0;
    int elementUnchanged=native_memory_read(elementRow->address,elementsAfter,sizeof(elementsAfter))
            &&!memcmp(elementsBefore,elementsAfter,sizeof(elementsBefore));LeaveCriticalSection(&elementRow->gate);
    CUSTOM_REQUIRE(elementHistory&&elementRead&&!elementRestored&&elementUnchanged&&!elementRow->unknownUse,
            "a new public element view claimed old lifetime history or poisoned unrelated current storage");
    CUSTOM_REQUIRE(hold(1)&&attempt()&&current(20)==0&&native_allocator_current(payloadReceipt)&&payloadRow->users>0,
            "live typed-buffer alias reached an original PMR deallocate or changed its receipt");
    CUSTOM_REQUIRE(attemptHeapDestroy()&&native_allocator_current(payloadReceipt)&&objectRow->alive&&payloadRow->alive,
            "actual HeapDestroy bypassed cooperative descendants and their live typed alias");
    probe=native_memory_host_custom_prepare(env,(void*)fixture,(uintptr_t)contextToken,contextEpoch,NULL,0,NULL,0,
            NATIVE_MEMORY_HOST_ARENA_RESET,&selected,&denied);
    CUSTOM_REQUIRE(!probe&&denied&&objectRow->alive&&payloadRow->alive&&context->epoch==contextEpoch,
            "custom pool reset admission ignored an actual live alias/typed object");
    CUSTOM_REQUIRE(hold(0),"actual typed-buffer alias could not close");
    probe=native_memory_host_custom_prepare(env,(void*)fixture,(uintptr_t)contextToken,contextEpoch,objectRow,objectGeneration,NULL,0,
            NATIVE_MEMORY_HOST_REALLOCATE,&selected,&denied);
    CUSTOM_REQUIRE(!probe&&denied&&objectRow->alive&&objectRow->generation==objectGeneration,
            "raw custom realloc bypassed an actual constructed-object lifetime");
    CUSTOM_REQUIRE(native_memory_read(current(6),payloadBefore,sizeof(payloadBefore)),"actual owned payload bytes unavailable");
    uint64_t scalar=current(4);NativeMemoryRange *range=NULL;
    EnterCriticalSection(&objectRow->gate);
    for(NativeMemoryRange *entry=objectRow->ranges;entry;entry=entry->next)
        if(objectRow->address+entry->start==scalar&&entry->length==sizeof(uint64_t)){range=entry;break;}
    owner.stopped=1;
    int restored=range&&native_object_state_restorable(objectRow,range,&selected)&&native_memory_restore_range(objectRow,range,&selected);
    owner.stopped=0;LeaveCriticalSection(&objectRow->gate);
    CUSTOM_REQUIRE(restored&&current(5)==7&&current(14)>fieldEpoch&&native_memory_read(current(6),payloadAfter,sizeof(payloadAfter))
            &&!memcmp(payloadBefore,payloadAfter,sizeof(payloadBefore))&&current(19)==7+32*64+496,
            "actual typed scalar restore failed, lost its epoch, or copied an owned payload/object representation");
    CUSTOM_REQUIRE(containersShapeMismatch()&&current(32)==2&&current(33)==3,
            "an unwrapped public resize let a stale wider element view write outside the current lifetime");
    CUSTOM_REQUIRE(finish(),"actual destructor/custom deallocate/pool release/HeapDestroy failed to close");finished=1;
    CUSTOM_REQUIRE(context->retired&&!native_allocator_current(objectReceipt)&&!native_allocator_current(payloadReceipt)
            &&standardContext->retired&&!native_allocator_current(newVector)&&!native_allocator_current(newString)
            &&!newVector->allocation->alive&&!newString->allocation->alive
            &&!objectRow->alive&&!payloadRow->alive&&!objectRow->typedObjects&&!payloadRow->typedObjects
            &&!native_allocator_calls&&!native_object_states&&!native_object_state_calls,
            "actual custom allocator/object closure left a current receipt, typed lifetime or original lease");
    fprintf(stdout,"PRIVATE_CUSTOM_ALLOCATOR_STATE_PASS supplied_exact_image_source actual_external_PMR_pool observed_Heap_backing real_nonPMR_std_allocator shared_rebind_state std_backing=%s nonzero_custom_and_typed_receipts live_views_block_original_reserve_resize stale_views_refused original_reallocation_delete_pair live_descendants_block_actual_HeapDestroy cross_provider_refused live_borrow_and_typed_block_original_free_reset_realloc exact_scalar_restore_epoch payload_unchanged actual_destructor_pool_release_HeapDestroy no_Core_discovery_claim\n",
            standardBacking==1?"CPP":standardBacking==2?"CRT":"CPP_and_CRT");fflush(stdout);
custom_done:
    if(failure&&ran&&!finished&&current){fprintf(stderr,"PRIVATE_CUSTOM_CONTAINER_FAILED stage=%llu ctx=%llu epoch=%llu vectorReceipt=%llu stringReceipt=%llu vectorView=%llu stringView=%llu vectorCalls=%llu stringCalls=%llu stale=%llu growth=%llu pending=%llu error=%lu reason=%s\n",
            (unsigned long long)current(21),(unsigned long long)current(22),(unsigned long long)current(23),
            (unsigned long long)current(26),(unsigned long long)current(27),(unsigned long long)current(24),(unsigned long long)current(25),
            (unsigned long long)current(28),(unsigned long long)current(29),(unsigned long long)current(30),
            (unsigned long long)current(31),(unsigned long long)current(34),(unsigned long)GetLastError(),failure);fflush(stderr);}
    if(characters)(*env)->ReleaseStringChars(env,path,characters);
    if(initialized){
        if(native_original.ExceptionCheck(env))native_original.ExceptionClear(env);owner.stopped=0;
        if(probe)native_memory_host_cancel(probe);
        if(ran&&!finished&&hold&&finish){hold(0);finished=finish();}
        while(native_allocator_calls){NativeAllocatorCall *entry=native_allocator_calls;native_allocator_calls=entry->next;
            if(thread.hostArenaOriginal==&entry->scope)thread.hostArenaOriginal=entry->scope.previous;
            native_memory_host_abort(env,entry->memory);free(entry);}
        for(HostImage *item=host_images;item;item=item->next){
            for(HostImport *entry=item->imports;entry;entry=entry->next)
                if(entry->slotOwned&&entry->slot&&__atomic_load_n(entry->slot,__ATOMIC_ACQUIRE)==entry->entry)
                    host_exchange_import(entry,entry->entry,entry->original);
            item->live=0;
        }
        /* Failure disposal clears only this fixture's metadata. Successful
         * assertions above require the real original operations to close it. */
        while(native_object_state_calls){NativeObjectStateCall *entry=native_object_state_calls;native_object_state_calls=entry->next;
            native_owner_links_free(entry->write.sources);native_memory_image_release(entry->write.image);free(entry);}
        while(native_object_states){NativeObjectState *entry=native_object_states;native_object_states=entry->next;native_object_state_dispose(entry);}
        while(native_memory_allocations){NativeMemoryAllocation *entry=native_memory_allocations;native_memory_allocations=entry->next;
            native_memory_released(env,entry);native_memory_reap_references(env,entry);native_owner_links_free(entry->sources);
            native_owner_links_free(entry->readers);native_owner_links_free(entry->preserved);
            while(entry->interests){NativeMemoryInterest *interest=entry->interests;entry->interests=interest->next;free(interest);}
            DeleteCriticalSection(&entry->gate);free(entry);}
        while(native_allocator_receipts){NativeAllocatorReceipt *entry=native_allocator_receipts;native_allocator_receipts=entry->next;free(entry);}
        while(native_allocator_contexts){NativeAllocatorContext *entry=native_allocator_contexts;native_allocator_contexts=entry->next;
            while(entry->anchors){NativeHostCustomAnchor *anchor=entry->anchors;entry->anchors=anchor->next;free(anchor);}
            native_owner_links_free(entry->sources);if(entry->pin)host_real_free_library(entry->pin);free(entry);}
        while(native_memory_arena_contexts){NativeHostArenaContext *entry=native_memory_arena_contexts;native_memory_arena_contexts=entry->next;native_owner_links_free(entry->sources);free(entry);}
        while(native_memory_arena_providers){NativeHostArenaProvider *entry=native_memory_arena_providers;native_memory_arena_providers=entry->next;free(entry);}
        while(native_memory_host_contexts){NativeHostMemoryContext *entry=native_memory_host_contexts;native_memory_host_contexts=entry->next;native_owner_links_free(entry->sources);free(entry);}
        native_memory_host_context_epoch=0;native_allocator_sequence=0;native_owner_links_free(native_memory_unknown);native_memory_unknown=NULL;
        host_images=NULL;native_libraries=NULL;
        native_tooling_pin=NULL;native_tooling_image_begin=native_tooling_image_end=0;
        if(backendPin)host_real_free_library(backendPin);
        if(owner.module)native_original.DeleteWeakGlobalRef(env,owner.module);if(module)native_original.DeleteLocalRef(env,module);
        native_private_unregister(&thread.jniRange);
        if(fixtureTls!=TLS_OUT_OF_INDEXES){TlsSetValue(fixtureTls,NULL);TlsFree(fixtureTls);}native_tls=TLS_OUT_OF_INDEXES;
        memset(&native_original,0,sizeof(native_original));
    }
    if(fixture)host_real_free_library(fixture);
    if(failure)audit_physical_failure(env,failure);
#undef CUSTOM_REQUIRE
    return failure?JNI_FALSE:JNI_TRUE;
}
#endif /* !RONOVA_LIVE_JNI_ONLY */
#endif /* !RONOVA_RANGE_CONTENTION_AGENT */
