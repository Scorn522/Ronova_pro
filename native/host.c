/* Included after the native source helpers. Each family owns an unnamed Job and
 * duplicates the actual returned process handle; PIDs never authorize disposal. */
#include <stdio.h>
typedef BOOL (WINAPI *HostCreateProcessW)(LPCWSTR,LPWSTR,LPSECURITY_ATTRIBUTES,
        LPSECURITY_ATTRIBUTES,BOOL,DWORD,LPVOID,LPCWSTR,LPSTARTUPINFOW,LPPROCESS_INFORMATION);
typedef BOOL (WINAPI *HostCreateProcessA)(LPCSTR,LPSTR,LPSECURITY_ATTRIBUTES,
        LPSECURITY_ATTRIBUTES,BOOL,DWORD,LPVOID,LPCSTR,LPSTARTUPINFOA,LPPROCESS_INFORMATION);
typedef BOOL (WINAPI *HostCreateProcessAsUserW)(HANDLE,LPCWSTR,LPWSTR,LPSECURITY_ATTRIBUTES,
        LPSECURITY_ATTRIBUTES,BOOL,DWORD,LPVOID,LPCWSTR,LPSTARTUPINFOW,LPPROCESS_INFORMATION);
typedef BOOL (WINAPI *HostCreateProcessAsUserA)(HANDLE,LPCSTR,LPSTR,LPSECURITY_ATTRIBUTES,
        LPSECURITY_ATTRIBUTES,BOOL,DWORD,LPVOID,LPCSTR,LPSTARTUPINFOA,LPPROCESS_INFORMATION);
typedef BOOL (WINAPI *HostCreateProcessWithTokenW)(HANDLE,DWORD,LPCWSTR,LPWSTR,DWORD,
        LPVOID,LPCWSTR,LPSTARTUPINFOW,LPPROCESS_INFORMATION);
typedef BOOL (WINAPI *HostCreateProcessWithLogonW)(LPCWSTR,LPCWSTR,LPCWSTR,DWORD,LPCWSTR,
        LPWSTR,DWORD,LPVOID,LPCWSTR,LPSTARTUPINFOW,LPPROCESS_INFORMATION);
typedef BOOL (WINAPI *HostCompareHandles)(HANDLE,HANDLE);
typedef FARPROC (WINAPI *HostGetProcAddress)(HMODULE,LPCSTR);
typedef jlong (JNICALL *HostProcessCreate)(JNIEnv*,jclass,jstring,jstring,jstring,jlongArray,jboolean);
enum {
    HOST_CREATE_W=1,HOST_RESOLVE=2,HOST_CREATE_A=3,HOST_CREATE_USER_W=4,HOST_CREATE_USER_A=5,
    HOST_CREATE_TOKEN_W=6,HOST_CREATE_LOGON_W=7,HOST_CLOSE=8,HOST_DUPLICATE=9,HOST_HANDLE_FLAGS=10,
    HOST_OPEN_PROCESS=11,HOST_OPEN_THREAD=12,HOST_RESUME_THREAD=13,HOST_SUSPEND_THREAD=14,
    HOST_TERMINATE_PROCESS=15,HOST_TERMINATE_THREAD=16,
    HOST_CREATE_THREAD=17,HOST_CREATE_REMOTE_THREAD=18,HOST_CREATE_REMOTE_THREAD_EX=19,
    HOST_BEGIN_THREAD=20,HOST_BEGIN_THREAD_EX=21,
    HOST_LOAD_LIBRARY_A=22,HOST_LOAD_LIBRARY_W=23,HOST_LOAD_LIBRARY_EX_A=24,HOST_LOAD_LIBRARY_EX_W=25,
    HOST_FREE_LIBRARY=26,HOST_WRITE_PROCESS_MEMORY=27,HOST_VIRTUAL_PROTECT=28,HOST_VIRTUAL_PROTECT_EX=29,
    HOST_VIRTUAL_FREE=30,HOST_VIRTUAL_FREE_EX=31,HOST_UNMAP_VIEW=32,HOST_UNMAP_VIEW_EX=33,
    HOST_FREE_LIBRARY_EXIT_THREAD=34,HOST_READ_PROCESS_MEMORY=35,
    HOST_HEAP_ALLOC=36,HOST_HEAP_REALLOC=37,HOST_HEAP_FREE=38,HOST_VIRTUAL_ALLOC=39,HOST_VIRTUAL_ALLOC_EX=40,
    HOST_CRT_MALLOC=41,HOST_CRT_CALLOC=42,HOST_CRT_REALLOC=43,HOST_CRT_FREE=44,
    HOST_HEAP_DESTROY=45,HOST_KIND_COUNT=46
};
static int host_is_create(int kind){return kind==HOST_CREATE_W||kind==HOST_CREATE_A
        ||kind>=HOST_CREATE_USER_W&&kind<=HOST_CREATE_LOGON_W;}
static int host_is_handle(int kind){return kind>=HOST_CLOSE&&kind<=HOST_TERMINATE_THREAD;}
static int host_is_thread_admission(int kind){return kind>=HOST_CREATE_THREAD&&kind<=HOST_BEGIN_THREAD_EX;}
static HostCompareHandles host_compare_handles;
static HostGetProcAddress host_windows_resolver;
static void *host_windows_api[HOST_KIND_COUNT];
static void *host_exit_thread_original;
static void __attribute__((noreturn)) host_real_exit_thread(DWORD code){
    void *actual=__atomic_load_n(&host_exit_thread_original,__ATOMIC_ACQUIRE);
    if(actual)((void (WINAPI*)(DWORD))actual)(code);
    /* ExitThread itself is not interposed. Its control-DLL import is the
     * already resolved fallback, including a restricted callback's refusal. */
    ExitThread(code);__builtin_unreachable();
}
static BOOL host_real_free_library(HMODULE module){
    /* Backend pins own an independently acquired reference. Resolving the
     * original export avoids consuming a producer's newly controlled IAT. */
    DWORD incoming=GetLastError();
    void *actual=__atomic_load_n(&host_windows_api[HOST_FREE_LIBRARY],__ATOMIC_ACQUIRE);
    if(!actual){HMODULE kernel=GetModuleHandleW(L"kernel32.dll");
        HostGetProcAddress resolve=__atomic_load_n(&host_windows_resolver,__ATOMIC_ACQUIRE);
        actual=kernel?(void*)(resolve?resolve(kernel,"FreeLibrary"):GetProcAddress(kernel,"FreeLibrary")):NULL;
        if(actual)__atomic_store_n(&host_windows_api[HOST_FREE_LIBRARY],actual,__ATOMIC_RELEASE);
    }
    if(!actual){SetLastError(ERROR_PROC_NOT_FOUND);return FALSE;}
    SetLastError(incoming);return ((BOOL (WINAPI*)(HMODULE))actual)(module);
}
static void *host_real_memory_target(int kind,const char *name){
    void *actual=__atomic_load_n(&host_windows_api[kind],__ATOMIC_ACQUIRE);
    if(!actual){HMODULE kernel=GetModuleHandleW(L"kernel32.dll");
        HostGetProcAddress resolve=__atomic_load_n(&host_windows_resolver,__ATOMIC_ACQUIRE);
        actual=kernel?(void*)(resolve?resolve(kernel,name):GetProcAddress(kernel,name)):NULL;
        if(actual)__atomic_store_n(&host_windows_api[kind],actual,__ATOMIC_RELEASE);
    }return actual;
}
/* Only Ronova's own table/code installation calls these original exports.
 * An external callback never acquires a TLS flag or another bypass identity. */
static BOOL host_real_virtual_protect(LPVOID address,SIZE_T size,DWORD protection,PDWORD oldProtection){
    DWORD incoming=GetLastError();void *actual=host_real_memory_target(HOST_VIRTUAL_PROTECT,"VirtualProtect");
    if(!actual){SetLastError(ERROR_PROC_NOT_FOUND);return FALSE;}
    SetLastError(incoming);return ((BOOL (WINAPI*)(LPVOID,SIZE_T,DWORD,PDWORD))actual)(address,size,protection,oldProtection);
}
static BOOL host_real_virtual_free(LPVOID address,SIZE_T size,DWORD type){
    DWORD incoming=GetLastError();void *actual=host_real_memory_target(HOST_VIRTUAL_FREE,"VirtualFree");
    if(!actual){SetLastError(ERROR_PROC_NOT_FOUND);return FALSE;}
    SetLastError(incoming);return native_private_virtual_free(address,size,type,(NativePrivateVirtualFree)actual);
}
static BOOL host_real_write_process_memory(HANDLE process,LPVOID address,LPCVOID buffer,SIZE_T size,SIZE_T *written){
    DWORD incoming=GetLastError();void *actual=host_real_memory_target(HOST_WRITE_PROCESS_MEMORY,"WriteProcessMemory");
    if(!actual){SetLastError(ERROR_PROC_NOT_FOUND);return FALSE;}
    SetLastError(incoming);return ((BOOL (WINAPI*)(HANDLE,LPVOID,LPCVOID,SIZE_T,SIZE_T*))actual)(process,address,buffer,size,written);
}
static BOOL host_real_read_process_memory(HANDLE process,LPCVOID address,LPVOID buffer,SIZE_T size,SIZE_T *read){
    DWORD incoming=GetLastError();void *actual=host_real_memory_target(HOST_READ_PROCESS_MEMORY,"ReadProcessMemory");
    if(!actual){SetLastError(ERROR_PROC_NOT_FOUND);return FALSE;}
    SetLastError(incoming);return ((BOOL (WINAPI*)(HANDLE,LPCVOID,LPVOID,SIZE_T,SIZE_T*))actual)(process,address,buffer,size,read);
}
static BOOL host_real_close(HANDLE handle){
    void *actual=__atomic_load_n(&host_windows_api[HOST_CLOSE],__ATOMIC_ACQUIRE);
    return actual?((BOOL (WINAPI*)(HANDLE))actual)(handle):CloseHandle(handle);
}
static BOOL host_real_duplicate(HANDLE source,HANDLE handle,HANDLE target,LPHANDLE result,DWORD access,BOOL inherit,DWORD flags){
    void *actual=__atomic_load_n(&host_windows_api[HOST_DUPLICATE],__ATOMIC_ACQUIRE);
    return actual?((BOOL (WINAPI*)(HANDLE,HANDLE,HANDLE,LPHANDLE,DWORD,BOOL,DWORD))actual)(source,handle,target,result,access,inherit,flags)
            :DuplicateHandle(source,handle,target,result,access,inherit,flags);
}
static DWORD host_real_resume(HANDLE thread){
    void *actual=__atomic_load_n(&host_windows_api[HOST_RESUME_THREAD],__ATOMIC_ACQUIRE);
    return actual?((DWORD (WINAPI*)(HANDLE))actual)(thread):ResumeThread(thread);
}
static BOOL host_real_terminate(HANDLE process,UINT code){
    void *actual=__atomic_load_n(&host_windows_api[HOST_TERMINATE_PROCESS],__ATOMIC_ACQUIRE);
    return actual?((BOOL (WINAPI*)(HANDLE,UINT))actual)(process,code):TerminateProcess(process,code);
}
/* Every alias is in this JVM's handle table. Remote tables and inheritance are
 * explicit unresolved transfers; their numeric values never authorize a close. */
typedef struct HostHandle {
    HANDLE value;
    OwnerLink *owners;
    int role,live,inheritable;
    struct HostHandle *next;
} HostHandle;
typedef struct HostKernelFile {HostKernelTarget target;uint64_t token;DWORD error;LONG status;int pending;struct HostKernelFile *next;} HostKernelFile;
typedef struct HostProcess {
    uint64_t token;
    HANDLE original,monitor,threadMonitor,job,failedProcess,failedThread;
    OwnerLink *owners;
    HostHandle *handles;
    jweak identity;
    DWORD error,kernelError;LONG kernelStatus;int kernelPending,kernelProcessPending;
    HostKernelTarget kernelTarget;HostKernelFile *kernelFiles;
    uint64_t remoteTransfers,inheritedTransfers;
    int creating,assigned,published,unknown,failed,terminating,retired,borrowed,pending,abandoned,nativeDirect,exited,lifetimeGap;
    struct HostProcess *next,*pendingNext;
} HostProcess;
typedef struct HostCreation {OwnerLink *owners;HostProcess *created;struct HostCreation *previous;jweak handles;LONG64 token;int constructor;} HostCreation;
static SRWLOCK host_records=SRWLOCK_INIT;
static CONDITION_VARIABLE host_changed=CONDITION_VARIABLE_INIT;
static HostProcess *host_processes;
/* Whole JVM policy, independent of every child process's Java Mod sources.
 * Accessed under host_records; a read never selects or opens the backend. */
static int host_jvm_owner_desired,host_jvm_owner_pending;
static DWORD host_jvm_owner_error;
static LONG host_jvm_owner_status;
static RVH_TARGET_REPLY host_jvm_owner_observed;
static uint64_t host_next_token,host_next_kernel_file;
static HostCreateProcessW host_original_create;
static HostProcessCreate host_original_process;
static void *volatile *host_import_slot;
static NativePrivateRange host_process_import_range;
// The JDK import table is read-only outside host_import's VirtualProtect window.
// Even compare-exchange(NULL, NULL) requires write permission, so observations
// use an acquire load; only the actual replacement uses compare-exchange.
static jclass host_process_type;
static jfieldID host_process_handle;
static jmethodID host_process_gate;
static volatile LONG host_ready;
static volatile LONG host_process_current;
static volatile LONG host_java_boundary;
static volatile LONG64 host_next_creation;
static jmethodID host_process_method;
static HANDLE host_worker_handle;
typedef struct HostImport {
    struct HostImage *image;
    void *volatile *slot;
    void *original;
    void *entry;
    NativeLibrary *library;
    HostLibraryLoad *load;
    OwnerLink *sources;
    int kind,delayed,returned,slotOwned,unknown;
    RUNTIME_FUNCTION unwind;
    NativePrivateRange slotRange;
    struct HostImport *next;
} HostImport;
typedef struct HostImage {
    HMODULE handle;
    HMODULE pin;
    DWORD size,functionsRva,functionCount;
    HostImport *imports;
    void *createTarget,*createATarget,*resolveTarget;
    void *targets[HOST_KIND_COUNT];
    int live,checked,failed,loadWindow,delayImports;
    int releasing,pinReleased,opaque;
    struct HostStaticMemory *privateMemory;
    volatile LONG privateOpaque;
    volatile LONG retiring;
    volatile LONG active;
    HostLibraryLoad *loads;
    struct HostImage *next;
} HostImage;
/* This directory contains actual registered image incarnations. Disabled entries
 * remain callable tombstones, as do retired Binding trampolines; a reused HMODULE
 * never reactivates an old import entry or carries its former sources forward. */
static SRWLOCK host_imports=SRWLOCK_INIT;
static HostImage *host_images;
/* host_imports is held; only the current logical image incarnation may keep
 * an outside IAT address registered. Retained thunk tombstones keep no such
 * span after that incarnation closes, even if the address is later reused. */
static void host_image_private_slots_locked(HostImage *image){
    for(HostImport *entry=image->imports;entry;entry=entry->next){
        if(image->live&&!image->releasing&&entry->slot){
            if(!native_private_register(&entry->slotRange,(const void*)entry->slot,sizeof(*entry->slot)))image->failed=1;
        }else native_private_unregister(&entry->slotRange);
    }
}
static void host_image_imports(HostImage*);
static void host_static_image_observed(HostImage*);
static int host_static_image_prepare(HostImage*,JNIEnv*);
static void host_static_image_cancel(HostImage*,JNIEnv*);
static void host_static_image_closed(HostImage*,JNIEnv*);
static void host_static_image_released(HostImage*,JNIEnv*);
static int host_private_binding_unknown(Binding*);
static void host_windows_targets(void**);
static HostLibraryLoad *host_thread_worker_child(NativeThread*,HMODULE);
static int native_tooling_retirement_jni(JNIEnv**);
static void native_thread_worker_wake(void);
static int native_thread_worker_backend_ready(void);
static jobjectArray native_tooling_sources(JNIEnv*,OwnerLink*,int);
static HostLibraryLoad *host_module_release_sources(NativeThread*);
static void host_module_releases_reap(JNIEnv**);
static void host_module_releases_state(JNIEnv*,jobject,jlong*);
static int host_module_exits_pending(void);
static int host_module_exit_pending(const NativeThread*);
static int host_module_exit_internal_handle(HANDLE);
static int host_handle_identity_ready(void){
    if(__atomic_load_n(&host_compare_handles,__ATOMIC_ACQUIRE))return 1;
    HMODULE kernel=GetModuleHandleW(L"kernelbase.dll");
    HostGetProcAddress resolve=__atomic_load_n(&host_windows_resolver,__ATOMIC_ACQUIRE);
    HostCompareHandles actual=kernel?(HostCompareHandles)(resolve?resolve(kernel,"CompareObjectHandles"):
            GetProcAddress(kernel,"CompareObjectHandles")):NULL;
    if(!actual)return 0;
    __atomic_store_n(&host_compare_handles,actual,__ATOMIC_RELEASE);return 1;
}
static int host_same_handle(HANDLE left,HANDLE right){
    HostCompareHandles compare=__atomic_load_n(&host_compare_handles,__ATOMIC_ACQUIRE);
    return left&&right&&compare&&compare(left,right);
}
static int host_local_process(HANDLE process){return host_same_handle(process,GetCurrentProcess());}
static int host_private(HostProcess *process){
    if(!process->owners||process->unknown)return 0;
    for(OwnerLink *link=process->owners;link;link=link->next)
        if(!InterlockedCompareExchange(&link->owner->stopped,0,0))return 0;
    return 1;
}
static int host_handle_private(HostHandle *handle){
    if(!handle->owners)return 0;
    for(OwnerLink *link=handle->owners;link;link=link->next)
        if(!InterlockedCompareExchange(&link->owner->stopped,0,0))return 0;
    return 1;
}
static HANDLE host_handle_witness(HostProcess *process,int role){return role==1?process->monitor:process->threadMonitor;}
static int host_handle_live(HostProcess *process,HostHandle *handle){
    if(!handle->live)return 0;
    if(host_same_handle(handle->value,host_handle_witness(process,handle->role)))return 1;
    /* A reused number may now denote somebody else's object. Lose only this
     * alias, retain the gap, and never close the replacement by its old value. */
    handle->live=0;process->lifetimeGap=1;process->unknown=1;return 0;
}
static int host_handles_pending(HostProcess *process){
    for(HostHandle *handle=process->handles;handle;handle=handle->next)if(handle->live)return 1;
    return 0;
}
static int host_close(HANDLE *handle,DWORD *error){
    if(!*handle)return 1;
    if(!host_real_close(*handle)){*error=GetLastError();return 0;}
    *handle=NULL;return 1;
}
/* Driver authority starts with this same observed object and source record. */
static ULONG host_kernel_effects(HostProcess *process){
    if(process->unknown||!process->owners||!process->monitor||process->failed)return 0;
    int stopped=1,protected=1,live=1;
    for(OwnerLink *link=process->owners;link;link=link->next){
        if(!InterlockedCompareExchange(&link->owner->producer,0,0))return 0;
        if(!InterlockedCompareExchange(&link->owner->stopped,0,0))stopped=0;else live=0;
        if(!InterlockedCompareExchange(&link->owner->kernelProtected,0,0))protected=0;
    }
    return stopped?RVH_PROCESS_DENY_CHILD_CREATION:protected&&live?RVH_PROCESS_PROTECT_OUTSIDE_HANDLES:0;
}
static int host_kernel_drop_target(HostKernelTarget *target,HostKernelResult *result){
    if(!target->witness&&!target->targetId)return 1;
    if(host_kernel_remove(target->witness,target->observationToken,target,result))return 1;
    HostKernelResult removal=*result;if(host_kernel_forget(target,result))return 1;*result=removal;return 0;
}
static int host_kernel_drop_process(HostProcess *process){
    HostKernelResult result={0};
    while(process->kernelFiles){
        HostKernelFile *file=process->kernelFiles;
        if(!host_kernel_drop_target(&file->target,&result)){process->kernelError=result.error;process->kernelStatus=result.status;process->kernelPending=1;return 0;}
        process->kernelFiles=file->next;SecureZeroMemory(file,sizeof(*file));free(file);
    }
    if(!host_kernel_drop_target(&process->kernelTarget,&result)){process->kernelError=result.error;process->kernelStatus=result.status;process->kernelPending=1;return 0;}
    process->kernelError=0;process->kernelStatus=0;process->kernelPending=0;process->kernelProcessPending=0;return 1;
}
static void host_kernel_apply(HostProcess *process){
    ULONG effects=host_kernel_effects(process);
    if(!effects||process->exited){host_kernel_drop_process(process);return;}
    HostKernelResult result={0};
    if(!host_kernel_process(process->monitor,process->token,effects,&process->kernelTarget,&result)){
        process->kernelError=result.error;process->kernelStatus=result.status;process->kernelPending=1;
    }else{
        RVH_TARGET_REPLY reply={0};
        if(!host_kernel_query(process->monitor,process->token,&process->kernelTarget,&reply,&result)||!(reply.State&RVH_TARGET_ACTIVE)){
            process->kernelError=result.error;process->kernelStatus=result.status;process->kernelPending=1;
        }else{process->kernelError=0;process->kernelStatus=0;process->kernelPending=0;}
    }
    process->kernelProcessPending=process->kernelPending;
    for(HostKernelFile *file=process->kernelFiles;file;file=file->next){
        if(file->target.targetId&&!file->pending){
            RVH_TARGET_REPLY reply={0};HostKernelResult observed={0};
            if(!host_kernel_query(file->target.witness,file->token,&file->target,&reply,&observed)||!(reply.State&RVH_TARGET_ACTIVE)
                    ||(reply.State&(RVH_TARGET_VOLUME_DETACHED|RVH_TARGET_OWNER_EXITED|RVH_TARGET_PROCESS_EXITED))){
                file->pending=1;file->error=observed.error;file->status=observed.status;
            }
        }
        if(file->pending){process->kernelPending=1;process->kernelError=file->error;process->kernelStatus=file->status;}
    }
}
static void host_kernel_refresh(HostProcess *process){if(!process->creating&&!process->retired)host_kernel_apply(process);}
/* Queries and waits are nonblocking. Successful termination is not exit evidence. */
static void host_collect(HostProcess *process){
    if(process->creating||process->retired)return;
    host_kernel_refresh(process);
    if((process->failed||host_private(process))&&!process->terminating){
        BOOL done=process->assigned?TerminateJobObject(process->job,1)
                :!process->monitor||host_real_terminate(process->monitor,1);
        if(!done){process->error=GetLastError();return;}
        process->terminating=1;
    }
    if(!process->exited&&process->job){
        JOBOBJECT_BASIC_ACCOUNTING_INFORMATION accounting;
        if(!QueryInformationJobObject(process->job,JobObjectBasicAccountingInformation,
                &accounting,sizeof(accounting),NULL)){process->error=GetLastError();return;}
        if(accounting.ActiveProcesses)return;
    }
    if(!process->exited&&process->monitor){
        DWORD result=WaitForSingleObject(process->monitor,0);
        if(result!=WAIT_OBJECT_0){if(result==WAIT_FAILED)process->error=GetLastError();return;}
    }
    process->exited=1;
    if(process->handles){
        for(HostHandle *handle=process->handles;handle;handle=handle->next){
            if(!host_handle_live(process,handle))continue;
            if(!process->borrowed&&host_handle_private(handle)){
                if(!host_real_close(handle->value)){process->error=GetLastError();return;}
                handle->live=0;
            }
        }
        if(host_handles_pending(process))return;
    }
    if(!host_kernel_drop_process(process))return;
    if(!host_close(&process->failedThread,&process->error)
            ||!host_close(&process->failedProcess,&process->error)
            ||!host_close(&process->threadMonitor,&process->error)
            ||!host_close(&process->monitor,&process->error)
            ||!host_close(&process->job,&process->error))return;
    process->retired=1;process->error=0;
}
static void host_kernel_owner_refresh(int observation){
    ULONG effects=host_jvm_owner_desired?RVH_OWNER_PROTECT_OUTSIDE_HANDLES:0;
    if(observation){
        if(!host_kernel_owner_current(effects)){
            SecureZeroMemory(&host_jvm_owner_observed,sizeof(host_jvm_owner_observed));
            if(host_jvm_owner_desired)host_jvm_owner_pending=1;
            return;
        }
    }else{
        if(!host_jvm_owner_pending&&host_kernel_owner_current(effects))return;
        if(!host_jvm_owner_desired&&!host_jvm_owner_pending&&!host_jvm_owner_observed.Effects)return;
    }
    HostKernelResult result={0};RVH_TARGET_REPLY reply={0};
    int ready=observation?host_kernel_owner_observe(&reply,&result):host_kernel_owner_apply(effects,&reply,&result);
    host_jvm_owner_observed=reply;host_jvm_owner_pending=!ready;
    host_jvm_owner_error=result.error;host_jvm_owner_status=result.status;
}
/* Called with host_records held. Only a previously selected owner policy can
 * reopen the backend; merely reading status cannot select this process. */
static void host_kernel_close_idle(void){
    host_kernel_owner_refresh(0);
    if(host_jvm_owner_desired||host_jvm_owner_pending)return;
    for(HostProcess *process=host_processes;process;process=process->next){
        if(process->creating)return;
        if(!process->exited&&host_kernel_effects(process))return;
    }
    AcquireSRWLockShared(&native_records);
    int protected=0;for(Owner *owner=native_owners;owner;owner=owner->next)if(InterlockedCompareExchange(&owner->kernelProtected,0,0)){protected=1;break;}
    if(!protected){
        HostKernelResult result={0};
        if(host_kernel_close(&result))for(HostProcess *process=host_processes;process;process=process->next)host_kernel_drop_process(process);
    }
    ReleaseSRWLockShared(&native_records);
}
static DWORD WINAPI host_worker(LPVOID argument){
    (void)argument;AcquireSRWLockExclusive(&host_records);
    for(;;){
        int pending=0;
        for(HostProcess *process=host_processes;process;process=process->next){host_collect(process);pending|=!process->retired;}
        host_kernel_close_idle();
        pending|=host_jvm_owner_desired||host_jvm_owner_pending;
        SleepConditionVariableSRW(&host_changed,&host_records,pending?1000:INFINITE,0);
    }
}
static int host_worker_ready(void){
    AcquireSRWLockExclusive(&host_records);
    if(!host_worker_handle)host_worker_handle=CreateThread(NULL,0,host_worker,NULL,0,NULL);
    int ready=host_worker_handle!=NULL;ReleaseSRWLockExclusive(&host_records);return ready;
}
static void host_publish(HostProcess *process){
    AcquireSRWLockExclusive(&host_records);process->token=++host_next_token;
    process->next=host_processes;host_processes=process;ReleaseSRWLockExclusive(&host_records);
}
static void host_thread_end(JNIEnv *env,NativeThread *state){
    AcquireSRWLockExclusive(&host_records);
    while(state->processScope&&state->processScope->constructor){
        HostCreation *scope=state->processScope;state->processScope=scope->previous;
        if(scope->created&&!scope->created->failed){
            scope->created->failed=1;scope->created->failedProcess=scope->created->original;
            scope->created->borrowed=0;host_collect(scope->created);
        }
        if(scope->handles)native_original.DeleteWeakGlobalRef(env,scope->handles);
        native_owner_links_free(scope->owners);free(scope);
    }
    while(state->processCreated){
        HostProcess *record=state->processCreated;state->processCreated=record->pendingNext;
        record->pendingNext=NULL;record->pending=0;record->abandoned=1;
    }
    ReleaseSRWLockExclusive(&host_records);
}
static void host_prune(JNIEnv *env){
    for(HostProcess **link=&host_processes;*link;){
        HostProcess *record=*link;
        int unused=record->failed||record->abandoned||record->nativeDirect&&record->published
                ||!record->nativeDirect&&record->published&&native_original.IsSameObject(env,record->identity,NULL);
        if(record->retired&&!record->creating&&!record->borrowed&&!record->pending&&unused
                &&!record->lifetimeGap&&!record->remoteTransfers&&!record->inheritedTransfers){
            *link=record->next;if(record->identity)native_original.DeleteWeakGlobalRef(env,record->identity);
            while(record->handles){HostHandle *handle=record->handles;record->handles=handle->next;native_owner_links_free(handle->owners);free(handle);}
            native_owner_links_free(record->owners);SecureZeroMemory(record,sizeof(*record));free(record);
        }else link=&record->next;
    }
}
static unsigned host_create_flags_index(int kind){return kind==HOST_CREATE_W||kind==HOST_CREATE_A?5:
        kind==HOST_CREATE_TOKEN_W?4:6;}
static unsigned host_create_information_index(int kind){return kind==HOST_CREATE_W||kind==HOST_CREATE_A?9:
        kind==HOST_CREATE_TOKEN_W?8:10;}
static int host_create_inherits(int kind,uintptr_t *a){return kind==HOST_CREATE_W||kind==HOST_CREATE_A?(BOOL)a[4]:
        kind==HOST_CREATE_USER_W||kind==HOST_CREATE_USER_A?(BOOL)a[5]:0;}
/* Typed calls preserve the exact Windows signature, token, credentials, ANSI or
 * Unicode buffers and STARTUPINFO(EX). Credentials stay on the calling stack. */
static BOOL host_call_create(void *original,int kind,uintptr_t *a,DWORD flags){
    switch(kind){
        case HOST_CREATE_W:return ((HostCreateProcessW)original)((LPCWSTR)a[0],(LPWSTR)a[1],
                (LPSECURITY_ATTRIBUTES)a[2],(LPSECURITY_ATTRIBUTES)a[3],(BOOL)a[4],flags,
                (LPVOID)a[6],(LPCWSTR)a[7],(LPSTARTUPINFOW)a[8],(LPPROCESS_INFORMATION)a[9]);
        case HOST_CREATE_A:return ((HostCreateProcessA)original)((LPCSTR)a[0],(LPSTR)a[1],
                (LPSECURITY_ATTRIBUTES)a[2],(LPSECURITY_ATTRIBUTES)a[3],(BOOL)a[4],flags,
                (LPVOID)a[6],(LPCSTR)a[7],(LPSTARTUPINFOA)a[8],(LPPROCESS_INFORMATION)a[9]);
        case HOST_CREATE_USER_W:return ((HostCreateProcessAsUserW)original)((HANDLE)a[0],(LPCWSTR)a[1],(LPWSTR)a[2],
                (LPSECURITY_ATTRIBUTES)a[3],(LPSECURITY_ATTRIBUTES)a[4],(BOOL)a[5],flags,
                (LPVOID)a[7],(LPCWSTR)a[8],(LPSTARTUPINFOW)a[9],(LPPROCESS_INFORMATION)a[10]);
        case HOST_CREATE_USER_A:return ((HostCreateProcessAsUserA)original)((HANDLE)a[0],(LPCSTR)a[1],(LPSTR)a[2],
                (LPSECURITY_ATTRIBUTES)a[3],(LPSECURITY_ATTRIBUTES)a[4],(BOOL)a[5],flags,
                (LPVOID)a[7],(LPCSTR)a[8],(LPSTARTUPINFOA)a[9],(LPPROCESS_INFORMATION)a[10]);
        case HOST_CREATE_TOKEN_W:return ((HostCreateProcessWithTokenW)original)((HANDLE)a[0],(DWORD)a[1],
                (LPCWSTR)a[2],(LPWSTR)a[3],flags,(LPVOID)a[5],(LPCWSTR)a[6],
                (LPSTARTUPINFOW)a[7],(LPPROCESS_INFORMATION)a[8]);
        case HOST_CREATE_LOGON_W:return ((HostCreateProcessWithLogonW)original)((LPCWSTR)a[0],(LPCWSTR)a[1],
                (LPCWSTR)a[2],(DWORD)a[3],(LPCWSTR)a[4],(LPWSTR)a[5],flags,(LPVOID)a[7],
                (LPCWSTR)a[8],(LPSTARTUPINFOW)a[9],(LPPROCESS_INFORMATION)a[10]);
    }
    SetLastError(ERROR_INVALID_FUNCTION);return FALSE;
}
static void host_handle_list_free(HostHandle *head){while(head){HostHandle *next=head->next;native_owner_links_free(head->owners);free(head);head=next;}}
static void host_note_inheritance(int kind,uintptr_t *arguments){
    int inherit=host_create_inherits(kind,arguments);
    unsigned startupIndex=kind==HOST_CREATE_W||kind==HOST_CREATE_A?8:kind==HOST_CREATE_TOKEN_W?7:9;
    STARTUPINFOA *startup=(STARTUPINFOA*)arguments[startupIndex];
    int standard=startup&&startup->cb>=sizeof(*startup)&&(startup->dwFlags&STARTF_USESTDHANDLES);
    if(!inherit&&!standard)return;
    /* An opaque STARTUPINFOEX list may restrict inheritance, but public Win32
     * has no getter for that list. Retain the precise inheritable aliases as
     * a coverage gap instead of guessing which remote table received them. */
    AcquireSRWLockExclusive(&host_records);
    for(HostProcess *process=host_processes;process;process=process->next)
        for(HostHandle *handle=process->handles;handle;handle=handle->next)
            if(host_handle_live(process,handle)){
                DWORD flags;
                if(!GetHandleInformation(handle->value,&flags)){process->lifetimeGap=1;process->unknown=1;}
                else if(inherit&&(flags&HANDLE_FLAG_INHERIT)||standard
                        &&(handle->value==startup->hStdInput||handle->value==startup->hStdOutput||handle->value==startup->hStdError)){
                    process->inheritedTransfers++;process->unknown=1;
                }
            }
    ReleaseSRWLockExclusive(&host_records);
}
static BOOL host_create_owned(HostCreation *scope,void *original,int kind,int nativeDirect,uintptr_t *arguments){
    DWORD flags=(DWORD)arguments[host_create_flags_index(kind)];
    LPPROCESS_INFORMATION information=(LPPROCESS_INFORMATION)arguments[host_create_information_index(kind)];
    if(!scope||!scope->owners){
        BOOL created=host_call_create(original,kind,arguments,flags);DWORD error=GetLastError();
        if(created)host_note_inheritance(kind,arguments);SetLastError(error);return created;
    }
    if(scope->created||native_owner_stopped(scope->owners)){SetLastError(ERROR_ACCESS_DENIED);return FALSE;}
    HostProcess *process=(HostProcess*)calloc(1,sizeof(*process));
    if(!process||!native_owner_merge(&process->owners,scope->owners)){
        if(process){native_owner_links_free(process->owners);free(process);}SetLastError(ERROR_NOT_ENOUGH_MEMORY);return FALSE;
    }
    if(nativeDirect){
        HostHandle *primary=(HostHandle*)calloc(1,sizeof(*primary)),*thread=(HostHandle*)calloc(1,sizeof(*thread));
        int identity=host_handle_identity_ready();
        if(primary)primary->next=thread;
        if(!primary||!thread||!identity
                ||!native_owner_merge(&primary->owners,scope->owners)||!native_owner_merge(&thread->owners,scope->owners)){
            if(primary)host_handle_list_free(primary);else free(thread);
            native_owner_links_free(process->owners);free(process);
            SetLastError(identity?ERROR_NOT_ENOUGH_MEMORY:ERROR_NOT_SUPPORTED);return FALSE;
        }
        primary->role=1;thread->role=2;process->handles=primary;
    }
    process->job=CreateJobObjectW(NULL,NULL);
    JOBOBJECT_EXTENDED_LIMIT_INFORMATION limits;memset(&limits,0,sizeof(limits));
    limits.BasicLimitInformation.LimitFlags=JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
    if(!process->job||!SetInformationJobObject(process->job,JobObjectExtendedLimitInformation,&limits,sizeof(limits))){
        DWORD error=GetLastError();process->failed=1;process->error=error;
        host_publish(process);WakeAllConditionVariable(&host_changed);SetLastError(error);return FALSE;
    }
    process->nativeDirect=nativeDirect;
    process->creating=1;process->borrowed=1;host_publish(process);scope->created=process;
    BOOL created=host_call_create(original,kind,arguments,flags|CREATE_SUSPENDED);
    DWORD error=created?0:GetLastError();
    if(created){
        process->original=information->hProcess;
        if(!host_real_duplicate(GetCurrentProcess(),information->hProcess,GetCurrentProcess(),
                &process->monitor,0,FALSE,DUPLICATE_SAME_ACCESS)){
            error=GetLastError();process->monitor=information->hProcess;
        }else if(nativeDirect&&!host_real_duplicate(GetCurrentProcess(),information->hThread,GetCurrentProcess(),
                &process->threadMonitor,0,FALSE,DUPLICATE_SAME_ACCESS))error=GetLastError();
        else if(!AssignProcessToJobObject(process->job,information->hProcess))error=GetLastError();
        else{
            process->assigned=1;
            AcquireSRWLockExclusive(&host_records);
            AcquireSRWLockShared(&native_records);
            if(native_owner_stopped(process->owners))error=ERROR_ACCESS_DENIED;
            else if(nativeDirect&&(!host_same_handle(information->hProcess,process->monitor)
                    ||!host_same_handle(information->hThread,process->threadMonitor)))error=ERROR_INVALID_HANDLE;
            else{host_kernel_apply(process);if(!(flags&CREATE_SUSPENDED)&&host_real_resume(information->hThread)==(DWORD)-1)error=GetLastError();}
            ReleaseSRWLockShared(&native_records);
            ReleaseSRWLockExclusive(&host_records);
        }
    }
    if(created&&!error)host_note_inheritance(kind,arguments);
    AcquireSRWLockExclusive(&host_records);
    if(error){
        process->failed=1;process->error=error;
        if(created){
            if(process->monitor!=information->hProcess)process->failedProcess=information->hProcess;
            process->failedThread=information->hThread;memset(information,0,sizeof(*information));
        }
    }
    if(created&&!error&&nativeDirect){
        HostHandle *primary=process->handles,*thread=primary->next;
        primary->value=information->hProcess;thread->value=information->hThread;
        primary->live=thread->live=1;
        DWORD handleFlags;
        if(GetHandleInformation(primary->value,&handleFlags))primary->inheritable=(handleFlags&HANDLE_FLAG_INHERIT)!=0;
        else process->lifetimeGap=process->unknown=1;
        if(GetHandleInformation(thread->value,&handleFlags))thread->inheritable=(handleFlags&HANDLE_FLAG_INHERIT)!=0;
        else process->lifetimeGap=process->unknown=1;
    }
    process->creating=0;host_collect(process);WakeAllConditionVariable(&host_changed);
    ReleaseSRWLockExclusive(&host_records);
    if(error){SetLastError(error);return FALSE;}return created;
}
static BOOL WINAPI host_create_process(LPCWSTR application,LPWSTR command,
        LPSECURITY_ATTRIBUTES processAttributes,LPSECURITY_ATTRIBUTES threadAttributes,
        BOOL inherit,DWORD flags,LPVOID environment,LPCWSTR directory,
        LPSTARTUPINFOW startup,LPPROCESS_INFORMATION information){
    NativeThread *state=native_thread();
    uintptr_t arguments[]={(uintptr_t)application,(uintptr_t)command,(uintptr_t)processAttributes,
            (uintptr_t)threadAttributes,(uintptr_t)inherit,(uintptr_t)flags,(uintptr_t)environment,
            (uintptr_t)directory,(uintptr_t)startup,(uintptr_t)information};
    return host_create_owned(state?state->processScope:NULL,(void*)host_original_create,HOST_CREATE_W,0,arguments);
}
static void host_producers(OwnerLink **owners){
    for(OwnerLink **link=owners;*link;){
        if(!InterlockedCompareExchange(&(*link)->owner->producer,0,0)){
            OwnerLink *removed=*link;*link=removed->next;free(removed);
        }else link=&(*link)->next;
    }
}
static NativeLibrary *host_thread_worker_library(NativeThread *state,HMODULE image);
#include "host_module_sources.c"
#include "host_static_memory.c"
static int host_capture_caller(NativeThread *state,void *caller,OwnerLink **owners){
    HostImage *child=NULL;if(!host_module_capture(state,caller,owners,&child,NULL,NULL))return 0;
    if(child&&state&&(state->depth||state->library||state->tooling||state->hostLoad||state->hostWorker))return 1;
    HMODULE image=native_observe_image(caller);if(!image)return 1;
    int selected=0,ready=1;AcquireSRWLockShared(&native_records);
    /* A dynamic pointer may cross libraries. Prefer the actual executing
     * registration before falling back to all owners of a shared caller image. */
    if(state){
        for(unsigned i=0;i<state->depth;i++){
            NativeLibrary *library=state->stack[i]->library;
            if(library&&library->handle==image&&InterlockedCompareExchange(&library->alive,0,0)){
                selected=1;ready=ready&&native_owner_merge(owners,library->owners);
            }
        }
        for(NativeLibraryScope *scope=state->library;scope;scope=scope->previous)
            if(scope->library->handle==image&&InterlockedCompareExchange(&scope->library->alive,0,0)){
                selected=1;ready=ready&&native_owner_merge(owners,scope->library->owners);
            }
    }
    if(!selected){NativeLibrary *workerLibrary=host_thread_worker_library(state,image);
        if(workerLibrary){selected=1;ready=ready&&native_owner_merge(owners,workerLibrary->owners);}}
    if(!selected)for(NativeLibrary *library=native_libraries;library;library=library->next)
        if(library->handle==image&&InterlockedCompareExchange(&library->alive,0,0)
                &&InterlockedCompareExchange(&library->complete,0,0)&&!InterlockedCompareExchange(&library->closing,0,0))
            ready=ready&&native_owner_merge(owners,library->owners);
    ReleaseSRWLockShared(&native_records);return ready;
}
static int host_capture_all_query(JNIEnv *env,NativeThread *state,void *caller,OwnerLink **owners,int *unknown){
    if(!native_capture_scope_sources(env,state,owners,unknown)
            ||!host_capture_caller(state,caller,owners))return 0;
    state->control++;state->sourceCapture++;
    jobjectArray sources=(jobjectArray)native_original.CallStaticObjectMethod(env,native_controls,native_process_sources);
    state->sourceCapture--;int ready=sources&&!native_original.ExceptionCheck(env);
    if(ready){
        jsize count=native_original.GetArrayLength(env,sources);AcquireSRWLockExclusive(&native_records);
        for(jsize i=0;i<count&&ready&&!native_original.ExceptionCheck(env);i++){
            jobject module=native_original.GetObjectArrayElement(env,sources,i);
            if(!module){if(unknown)*unknown=1;else ready=0;}
            else {Owner *owner=native_owner(env,module);ready=owner&&native_owner_add(owners,owner);native_original.DeleteLocalRef(env,module);}
        }
        ReleaseSRWLockExclusive(&native_records);
    }
    if(sources)native_original.DeleteLocalRef(env,sources);state->control--;
    return ready&&!native_original.ExceptionCheck(env);
}
static int host_capture_all(JNIEnv *env,NativeThread *state,void *caller,OwnerLink **owners){return host_capture_all_query(env,state,caller,owners,NULL);}
static int host_capture_query(JNIEnv *env,NativeThread *state,void *caller,OwnerLink **owners,int *unknown){
    int ready=host_capture_all_query(env,state,caller,owners,unknown);
    /* Runtime carriers do not become producers of every process they implement. */
    host_producers(owners);return ready;
}
static int host_capture(JNIEnv *env,NativeThread *state,void *caller,OwnerLink **owners){return host_capture_query(env,state,caller,owners,NULL);}
static int host_library_current(NativeLibrary *library,HMODULE image){
    return library->handle==image&&library->jni&&!library->builtin
            &&InterlockedCompareExchange(&library->alive,0,0)
            &&InterlockedCompareExchange(&library->complete,0,0)
            &&!InterlockedCompareExchange(&library->closing,0,0);
}
static int host_entry_association(HostImport *entry,NativeThread *state,OwnerLink **owners,NativeLibrary **selected){
    /* Both source directories are locked by the caller. A returned API pointer
     * retains the resolving call's sources, never a cached permission. */
    int associated=0,ready=!owners||native_owner_merge(owners,entry->sources),mixed=0;
    NativeLibrary *chosen=NULL;
    /* The actual JNI_OnUnload call still owns its registered invocation while
     * the image is retiring. Preserve an unrelated source's legitimate call,
     * but check a stopped source again rather than restoring an unguarded IAT. */
    if(state)for(NativeLibraryScope *scope=state->library;scope;scope=scope->previous){
        NativeLibrary *library=scope->library;
        if(scope->unloading&&scope->phase==1&&library->hostImage==entry->image
                &&(!entry->library||entry->library==library)
                &&library->handle==entry->image->handle&&InterlockedCompareExchange(&library->alive,0,0)
                &&InterlockedCompareExchange(&library->closing,0,0)){
            associated=1;if(chosen&&chosen!=library)mixed=1;else chosen=library;
            if(owners)ready=ready&&native_owner_merge(owners,library->owners);
        }
    }
    /* Only this thread's genuine JDK load scope may use a not-yet-complete
     * image. osHandle comes from the actual JVM_LoadLibrary return, not a path
     * match or a cached export called later by the DLL itself. */
    if(entry->image->live&&state)for(NativeLibraryScope *scope=state->library;scope;scope=scope->previous){
        NativeLibrary *library=scope->library;
        if(!scope->unloading&&scope->phase==1&&scope->osLoadStarted&&scope->osHandle==entry->image->handle
                &&library->hostImage==entry->image&&(!entry->library||entry->library==library)
                &&library->handle==scope->osHandle&&library->jni&&!library->builtin
                &&InterlockedCompareExchange(&library->alive,0,0)&&!InterlockedCompareExchange(&library->closing,0,0)){
            associated=1;if(chosen&&chosen!=library)mixed=1;else chosen=library;
            if(owners)ready=ready&&native_owner_merge(owners,library->owners);
        }
    }
    if(entry->image->live){
        if(entry->slot&&__atomic_load_n(entry->slot,__ATOMIC_ACQUIRE)!=entry->entry)ready=0;
        /* An active real binding selects its actual library registration. A
         * shared image alone cannot distinguish consumers, so only the case
         * without that binding inherits all live registrations of the image. */
        if(entry->library){
            NativeLibrary *library=entry->library;
            if(library->hostImage==entry->image&&host_library_current(library,entry->image->handle)){
                associated=1;chosen=library;
                if(owners)ready=ready&&native_owner_merge(owners,library->owners);
            }
        }else if(state)for(unsigned i=0;i<state->depth;i++){
            NativeLibrary *library=state->stack[i]->library;
            if(library&&library->hostImage==entry->image&&host_library_current(library,entry->image->handle)){
                associated=1;if(chosen&&chosen!=library)mixed=1;else chosen=library;
                if(owners)ready=ready&&native_owner_merge(owners,library->owners);
            }
        }
        if(!associated&&!entry->library&&entry->image->loads){
            int child=host_module_select_locked(entry->image,state,entry->load,owners,NULL,NULL);
            if(child){associated=1;ready=ready&&child==1;}
        }
        if(!associated&&!entry->library){
            NativeLibrary *workerLibrary=host_thread_worker_library(state,entry->image->handle);
            if(workerLibrary&&workerLibrary->hostImage==entry->image){associated=1;chosen=workerLibrary;
                if(owners)ready=ready&&native_owner_merge(owners,workerLibrary->owners);}
        }
        if(!associated&&!entry->library)for(NativeLibrary *library=native_libraries;library;library=library->next)
            if(library->hostImage==entry->image&&host_library_current(library,entry->image->handle)){
                associated=1;if(chosen&&chosen!=library)mixed=1;else chosen=library;
                if(owners)ready=ready&&native_owner_merge(owners,library->owners);
            }
    }
    if(!associated&&!entry->library){
        int child=host_module_select_locked(entry->image,state,entry->load,owners,NULL,NULL);
        if(child){associated=1;ready=ready&&child==1;}
    }
    if(selected)*selected=mixed?NULL:chosen;
    return !associated?0:ready?1:-1;
}
#include "host_callback_sources.c"
static int host_entry_sources_query(HostImport *entry,NativeThread *state,void *caller,OwnerLink **owners,NativeLibrary **selected,int retainSources,int *unknown){
    int unobserved=entry->unknown;
    if(state&&(state->heapCallback||state->toolingRestricted||state->vmDetaching||state->vmThreadEnding||state->hostUnloadImage||state->hostRelease
            ||state->hostLoad&&state->hostLoad->calling))
        return host_callback_entry_sources(entry,state,caller,owners,selected,retainSources,host_is_thread_admission(entry->kind),unknown);
    AcquireSRWLockShared(&host_imports);AcquireSRWLockShared(&native_records);
    int associated=host_entry_association(entry,state,owners,selected),ready=associated==1;
    if(ready&&!entry->library&&entry->image->loads)
        ready=host_module_select_locked(entry->image,state,entry->load,NULL,NULL,&unobserved)==1;
    ReleaseSRWLockShared(&native_records);
    ReleaseSRWLockShared(&host_imports);
    JNIEnv *env=NULL;
    if(ready&&native_vm&&(*native_vm)->GetEnv(native_vm,(void**)&env,JNI_VERSION_1_8)==JNI_OK){
        if(!state)state=native_thread();
        ready=state&&host_capture_all_query(env,state,caller,owners,&unobserved);
    }else if(ready){
        /* A detached OS thread has no JNIEnv. Its actual library and active C
         * scopes remain usable; never manufacture Java execution provenance. */
        ready=host_capture_caller(state,caller,owners);
        if(state){
            for(unsigned i=0;i<state->depth&&ready;i++)ready=native_owner_merge(owners,state->stack[i]->owners);
            for(NativeLibraryScope *scope=state->library;scope&&ready;scope=scope->previous)
                ready=native_owner_merge(owners,scope->library->owners);
            for(NativeToolingScope *scope=state->tooling;scope&&ready;scope=scope->previous){
                ready=native_owner_merge(owners,scope->owners);unobserved|=scope->unknown;
            }
        }
    }
    if(unknown)*unknown|=unobserved;
    /* A captured unknown contributor is metadata, never a private worker or
     * resource permission. Keep ordinary profiler calls usable while refusing
     * a producer's default consumption until its complete scope is known. */
    if(!unknown&&unobserved&&native_has_producer(*owners))ready=0;
    if(!retainSources)host_producers(owners);
    if(!ready){SetLastError(associated?ERROR_INVALID_DATA:ERROR_MOD_NOT_FOUND);return 0;}
    return 1;
}
static int host_entry_sources(HostImport *entry,NativeThread *state,void *caller,OwnerLink **owners,NativeLibrary **selected,int retainSources){
    return host_entry_sources_query(entry,state,caller,owners,selected,retainSources,NULL);
}
static HostImport *host_consumed_entry(HostImport *entry,NativeThread *state,void *caller);
static BOOL host_native_create(HostImport *entry,void *caller,uintptr_t *arguments){
    DWORD incoming=GetLastError();OwnerLink *owners=NULL;
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    entry=host_consumed_entry(entry,state,caller);
    if(!host_entry_sources(entry,state,caller,&owners,NULL,0)){native_owner_links_free(owners);return FALSE;}
    if(native_owner_stopped(owners)){native_owner_links_free(owners);SetLastError(ERROR_ACCESS_DENIED);return FALSE;}
    if(!owners){
        SetLastError(incoming);return host_create_owned(NULL,entry->original,entry->kind,0,arguments);
    }
    HostCreation scope={0};scope.owners=owners;
    BOOL result=host_create_owned(&scope,entry->original,entry->kind,1,arguments);
    DWORD error=GetLastError();
    if(scope.created){
        AcquireSRWLockExclusive(&host_records);
        /* This exact PROCESS_INFORMATION publishes two observed aliases. A
         * later controlled duplicate or open adds its actual consumers; remote
         * transfer and inheritance keep the family unresolved. */
        if(result&&!scope.created->failed)scope.created->published=1;
        scope.created->borrowed=0;host_collect(scope.created);
        WakeAllConditionVariable(&host_changed);ReleaseSRWLockExclusive(&host_records);
    }
    native_owner_links_free(owners);SetLastError(error);return result;
}
static void *host_import_entry(HostImport *entry);
static int host_target_kind(HostImage *image,void *actual);
static void host_refresh_targets(HostImage *image);
static FARPROC WINAPI host_native_resolve(HMODULE module,LPCSTR name,HostImport *entry,void *caller){
    DWORD incoming=GetLastError();
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    if(state&&(state->heapCallback||state->toolingRestricted)){SetLastError(ERROR_BUSY);return NULL;}
    entry=host_consumed_entry(entry,state,caller);
    AcquireSRWLockShared(&host_imports);AcquireSRWLockShared(&native_records);
    int associated=host_entry_association(entry,state,NULL,NULL);
    ReleaseSRWLockShared(&native_records);ReleaseSRWLockShared(&host_imports);
    if(associated!=1){SetLastError(associated?ERROR_INVALID_DATA:ERROR_MOD_NOT_FOUND);return NULL;}
    SetLastError(incoming);FARPROC actual=((HostGetProcAddress)entry->original)(module,name);
    DWORD error=GetLastError();
    host_refresh_targets(entry->image);
    int kind=host_target_kind(entry->image,(void*)actual);
    if(!actual||!kind){host_module_mark_export(module,(void*)actual);SetLastError(error);return actual;}
    /* Names, ordinals and requested DLL handles do not authorize this result.
     * Only the actual returned Windows API address selects a controlled entry. */
    OwnerLink *owners=NULL;NativeLibrary *library=NULL;int unknown=0;
    if(!host_entry_sources_query(entry,state,caller,&owners,&library,1,&unknown)){native_owner_links_free(owners);return NULL;}
    if(native_owner_stopped(owners)){native_owner_links_free(owners);SetLastError(ERROR_ACCESS_DENIED);return NULL;}
    HostImport *resolved=(HostImport*)calloc(1,sizeof(*resolved));
    if(resolved){
        resolved->image=entry->image;resolved->original=(void*)actual;resolved->kind=kind;
        resolved->library=library;resolved->sources=owners;resolved->returned=1;resolved->unknown=unknown;
        AcquireSRWLockShared(&host_imports);
        (void)host_module_select_locked(entry->image,state,entry->load,&owners,&resolved->load,&unknown);
        ReleaseSRWLockShared(&host_imports);
        resolved->sources=owners;resolved->unknown=unknown;
        if(!host_is_create(kind)||host_worker_ready())resolved->entry=host_import_entry(resolved);
    }
    if(!resolved||!resolved->entry){
        AcquireSRWLockExclusive(&host_imports);entry->image->failed=1;ReleaseSRWLockExclusive(&host_imports);
        native_owner_links_free(owners);free(resolved);SetLastError(ERROR_NOT_ENOUGH_MEMORY);return NULL;
    }
    AcquireSRWLockExclusive(&host_imports);AcquireSRWLockShared(&native_records);
    associated=host_entry_association(resolved,state,NULL,NULL);
    int stopped=native_owner_stopped(resolved->sources)!=NULL;
    if(associated==1&&!stopped){
        resolved->next=entry->image->imports;entry->image->imports=resolved;
        entry->image->loadWindow=1;
    }
    ReleaseSRWLockShared(&native_records);ReleaseSRWLockExclusive(&host_imports);
    if(associated!=1||stopped){
        RtlDeleteFunctionTable(&resolved->unwind);host_real_virtual_free(resolved->entry,0,MEM_RELEASE);
        native_owner_links_free(owners);free(resolved);
        SetLastError(stopped?ERROR_ACCESS_DENIED:ERROR_MOD_NOT_FOUND);return NULL;
    }
    SetLastError(error);return (FARPROC)resolved->entry;
}
static int host_owners_contain(OwnerLink *head,OwnerLink *sources){
    if(!sources)return 0;
    for(;sources;sources=sources->next){
        OwnerLink *found=head;while(found&&found->owner!=sources->owner)found=found->next;
        if(!found)return 0;
    }
    return 1;
}
/* host_records is held. Numeric equality only locates a candidate; the retained
 * process or primary-thread handle must still name the same kernel object. */
static HostHandle *host_alias(HANDLE value,HostProcess **record){
    for(HostProcess *process=host_processes;process;process=process->next)
        for(HostHandle *handle=process->handles;handle;handle=handle->next)
            if(handle->value==value&&host_handle_live(process,handle)){
                if(record)*record=process;return handle;
            }
    return NULL;
}
static HostProcess *host_object_record(HANDLE value,int *role){
    for(HostProcess *process=host_processes;process;process=process->next){
        if(process->creating||process->retired)continue;
        if(host_same_handle(value,process->monitor)){if(role)*role=1;return process;}
        if(host_same_handle(value,process->threadMonitor)){if(role)*role=2;return process;}
    }
    return NULL;
}
static int host_thread_worker_internal_handle(HANDLE value);
static int host_internal_handle(HANDLE value){
    if(!value)return 0;if(value==host_worker_handle||host_thread_worker_internal_handle(value)||host_module_exit_internal_handle(value))return 1;
    for(HostProcess *process=host_processes;process;process=process->next)
        if(value==process->monitor||value==process->threadMonitor||value==process->job
                ||value==process->failedProcess||value==process->failedThread)return 1;
    return 0;
}
static HostHandle *host_add_alias(HostProcess *process,HANDLE value,int role,OwnerLink *owners){
    HostHandle *handle=host_alias(value,NULL);
    if(!handle){
        handle=(HostHandle*)calloc(1,sizeof(*handle));
        if(!handle){process->lifetimeGap=process->unknown=1;return NULL;}
        handle->value=value;handle->role=role;handle->live=1;
        handle->next=process->handles;process->handles=handle;
    }
    if(!owners||!native_owner_merge(&handle->owners,owners)||!native_owner_merge(&process->owners,owners))
        process->lifetimeGap=process->unknown=1;
    DWORD flags;
    if(GetHandleInformation(value,&flags))handle->inheritable=(flags&HANDLE_FLAG_INHERIT)!=0;
    else process->lifetimeGap=process->unknown=1;
    return handle;
}
static uint64_t host_call_handle(HostImport *entry,uintptr_t *a){
    switch(entry->kind){
        case HOST_CLOSE:return ((BOOL (WINAPI*)(HANDLE))entry->original)((HANDLE)a[0]);
        case HOST_DUPLICATE:return ((BOOL (WINAPI*)(HANDLE,HANDLE,HANDLE,LPHANDLE,DWORD,BOOL,DWORD))entry->original)
                ((HANDLE)a[0],(HANDLE)a[1],(HANDLE)a[2],(LPHANDLE)a[3],(DWORD)a[4],(BOOL)a[5],(DWORD)a[6]);
        case HOST_HANDLE_FLAGS:return ((BOOL (WINAPI*)(HANDLE,DWORD,DWORD))entry->original)((HANDLE)a[0],(DWORD)a[1],(DWORD)a[2]);
        case HOST_OPEN_PROCESS:case HOST_OPEN_THREAD:return (uintptr_t)((HANDLE (WINAPI*)(DWORD,BOOL,DWORD))entry->original)
                ((DWORD)a[0],(BOOL)a[1],(DWORD)a[2]);
        case HOST_RESUME_THREAD:case HOST_SUSPEND_THREAD:return ((DWORD (WINAPI*)(HANDLE))entry->original)((HANDLE)a[0]);
        case HOST_TERMINATE_PROCESS:case HOST_TERMINATE_THREAD:return ((BOOL (WINAPI*)(HANDLE,UINT))entry->original)((HANDLE)a[0],(UINT)a[1]);
    }
    SetLastError(ERROR_INVALID_FUNCTION);return 0;
}
static uint64_t host_handle_refused(int kind,DWORD error){SetLastError(error);
    return kind==HOST_RESUME_THREAD||kind==HOST_SUSPEND_THREAD?(DWORD)-1:0;}
static uint64_t host_native_handle(HostImport *entry,void *caller,uintptr_t *arguments){
    DWORD incoming=GetLastError();OwnerLink *owners=NULL;
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    int restricted=state&&(state->heapCallback||state->vmDetaching||state->vmThreadEnding||state->hostUnloadImage||state->hostRelease
            ||state->hostLoad&&state->hostLoad->calling);
    if(!restricted)entry=host_consumed_entry(entry,state,caller);
    if(!host_entry_sources(entry,state,caller,&owners,NULL,0)){
        DWORD error=GetLastError();native_owner_links_free(owners);return host_handle_refused(entry->kind,error);
    }
    int kind=entry->kind,opening=kind==HOST_OPEN_PROCESS||kind==HOST_OPEN_THREAD;
    int threadEffect=kind==HOST_RESUME_THREAD||kind==HOST_SUSPEND_THREAD||kind==HOST_TERMINATE_THREAD;
    HostCompareHandles compare=threadEffect?__atomic_load_n(&host_compare_handles,__ATOMIC_ACQUIRE):NULL;
    if(threadEffect&&!compare&&!restricted&&host_handle_identity_ready())
        compare=__atomic_load_n(&host_compare_handles,__ATOMIC_ACQUIRE);
    int closeOnly=kind==HOST_CLOSE||kind==HOST_DUPLICATE&&!arguments[2]&&((DWORD)arguments[6]&DUPLICATE_CLOSE_SOURCE);
    /* Pin the exit-witness directory before the handle records. A busy
     * directory is never evidence that an ordinary handle is private, and
     * the witness must not close/reuse between lookup and the actual call. */
    if(restricted){
        if(!TryAcquireSRWLockShared(&host_imports)){
            native_owner_links_free(owners);return host_handle_refused(kind,ERROR_BUSY);
        }
        if(!TryAcquireSRWLockExclusive(&host_records)){
            ReleaseSRWLockShared(&host_imports);
            native_owner_links_free(owners);return host_handle_refused(kind,ERROR_BUSY);
        }
        if(!TryAcquireSRWLockShared(&native_records)){
            ReleaseSRWLockExclusive(&host_records);ReleaseSRWLockShared(&host_imports);
            native_owner_links_free(owners);return host_handle_refused(kind,ERROR_BUSY);
        }
    }else {
        AcquireSRWLockShared(&host_imports);AcquireSRWLockExclusive(&host_records);AcquireSRWLockShared(&native_records);
    }
    HANDLE value=(HANDLE)arguments[kind==HOST_DUPLICATE?1:0];
    HANDLE thread=NULL;DWORD refusal=0;
    if(threadEffect){
        /* Fix this invocation's actual object and original access before the
         * comparison. Caller-side close/reuse cannot redirect the OS effect. */
        if(native_owner_stopped(owners))refusal=ERROR_ACCESS_DENIED;
        else if(!host_real_duplicate(GetCurrentProcess(),value,GetCurrentProcess(),&thread,0,FALSE,DUPLICATE_SAME_ACCESS)){
            DWORD error=GetLastError();refusal=error?error:ERROR_INVALID_HANDLE;
        }
        else if(host_worker_handle){
            if(!compare)refusal=ERROR_NOT_SUPPORTED;
            else{
                SetLastError(ERROR_SUCCESS);
                if(compare(thread,host_worker_handle))refusal=ERROR_ACCESS_DENIED;
                else{
                    DWORD error=GetLastError();
                    if(error!=ERROR_NOT_SAME_OBJECT)refusal=error?error:ERROR_INVALID_DATA;
                }
            }
        }
        if(!thread&&!refusal)refusal=ERROR_INVALID_HANDLE;
    }
    if(refusal){
        if(thread&&!host_real_close(thread))InterlockedIncrement(&native_failures);
        ReleaseSRWLockShared(&native_records);ReleaseSRWLockExclusive(&host_records);ReleaseSRWLockShared(&host_imports);
        native_owner_links_free(owners);return host_handle_refused(kind,refusal);
    }
    int local=kind!=HOST_DUPLICATE||host_local_process((HANDLE)arguments[0]);
    HostProcess *process=NULL;HostHandle *alias=!opening&&local?host_alias(value,&process):NULL;
    if(thread&&alias&&!host_same_handle(thread,host_handle_witness(process,alias->role))){alias=NULL;process=NULL;}
    if(!opening&&local&&!alias&&!host_internal_handle(value)){
        int role=0;process=host_object_record(thread?thread:value,&role);
        if(process){
            /* The temporary alias belongs only to this call. A changed caller
             * slot cannot be published as an alias for its former object. */
            if(!thread||host_same_handle(value,thread))alias=host_add_alias(process,value,role,owners);
            process->lifetimeGap=process->unknown=1;
        }
    }
    int stopped=native_owner_stopped(owners)!=NULL;
    if((!opening&&local&&host_internal_handle(value))
            ||stopped&&(!closeOnly||!alias||!host_handle_private(alias)||!host_owners_contain(alias->owners,owners))){
        if(thread&&!host_real_close(thread))InterlockedIncrement(&native_failures);
        ReleaseSRWLockShared(&native_records);ReleaseSRWLockExclusive(&host_records);ReleaseSRWLockShared(&host_imports);
        native_owner_links_free(owners);return host_handle_refused(kind,ERROR_ACCESS_DENIED);
    }
    if(alias&&!closeOnly&&(!owners||!native_owner_merge(&alias->owners,owners)||!native_owner_merge(&process->owners,owners)))
        process->lifetimeGap=process->unknown=1;
    uintptr_t threadArguments[2]={(uintptr_t)thread,kind==HOST_TERMINATE_THREAD?arguments[1]:0};
    SetLastError(incoming);uint64_t result=host_call_handle(entry,thread?threadArguments:arguments);DWORD error=GetLastError();
    if(kind==HOST_CLOSE){if(result&&alias)alias->live=0;}
    else if(kind==HOST_DUPLICATE){
        int targetLocal=arguments[2]&&host_local_process((HANDLE)arguments[2]);
        if(local&&alias&&((DWORD)arguments[6]&DUPLICATE_CLOSE_SOURCE))alias->live=0;
        if(result&&arguments[2]){
            if(targetLocal&&arguments[3]){
                HANDLE returned=*(LPHANDLE)arguments[3];int role=0;
                HostProcess *actual=process?process:host_object_record(returned,&role);
                if(actual){
                    if(alias)role=alias->role;
                    if(!role)host_object_record(returned,&role);
                    if(role&&host_same_handle(returned,host_handle_witness(actual,role)))host_add_alias(actual,returned,role,owners);
                    else actual->lifetimeGap=actual->unknown=1;
                    if(!local){actual->remoteTransfers++;actual->unknown=1;}
                }
            }else if(process){process->remoteTransfers++;process->unknown=1;}
        }
        /* Win32 closes the source even when duplication reports failure. This
         * observation applies only to the actual local object proven above. */
    }else if(opening&&result){
        int role=0;HostProcess *actual=host_object_record((HANDLE)(uintptr_t)result,&role);
        if(actual)host_add_alias(actual,(HANDLE)(uintptr_t)result,role,owners);
    }else if(kind==HOST_HANDLE_FLAGS&&result&&alias){
        DWORD flags;if(GetHandleInformation(value,&flags))alias->inheritable=(flags&HANDLE_FLAG_INHERIT)!=0;
        else process->lifetimeGap=process->unknown=1;
    }
    if(thread&&!host_real_close(thread))InterlockedIncrement(&native_failures);
    ReleaseSRWLockShared(&native_records);ReleaseSRWLockShared(&host_imports);
    if(process)host_collect(process);
    WakeAllConditionVariable(&host_changed);ReleaseSRWLockExclusive(&host_records);
    native_owner_links_free(owners);SetLastError(error);return result;
}
#include "host_thread_creation.c"
#include "host_crt_thread_creation.c"
#include "host_thread_workers.c"
#include "host_module_admission.c"
#include "host_memory.c"
#include "host_allocations.c"
#include "host_crt_allocations.c"
static uint64_t host_native_dispatch(HostImport *entry,void *caller,uintptr_t *arguments){
    if(entry->kind==HOST_FREE_LIBRARY_EXIT_THREAD)host_native_module_exit(entry,caller,arguments);
    if(entry->kind==HOST_RESOLVE)return (uintptr_t)host_native_resolve((HMODULE)arguments[0],(LPCSTR)arguments[1],entry,caller);
    if(host_is_create(entry->kind))return host_native_create(entry,caller,arguments);
    if(host_is_local_worker_create(entry->kind))return host_native_local_worker_create(entry,caller,arguments);
    if(host_is_thread_create(entry->kind))return host_native_thread_create(entry,caller,arguments);
    if(host_is_crt_thread_create(entry->kind))return host_native_crt_thread_create(entry,caller,arguments);
    if(host_is_handle(entry->kind))return host_native_handle(entry,caller,arguments);
    if(host_is_module_load(entry->kind))return host_native_module_load(entry,caller,arguments);
    if(entry->kind==HOST_FREE_LIBRARY)return host_native_module_release(entry,caller,arguments);
    if(host_is_memory_write(entry->kind))return host_native_memory(entry,caller,arguments);
    if(host_is_memory_release(entry->kind))return host_native_memory_release(entry,caller,arguments);
    if(entry->kind==HOST_READ_PROCESS_MEMORY)return host_native_memory_read(entry,caller,arguments);
    if(host_is_allocation(entry->kind))return host_native_allocation(entry,caller,arguments);
    if(host_is_crt_allocation(entry->kind))return host_native_crt_allocation(entry,caller,arguments);
    SetLastError(ERROR_INVALID_FUNCTION);return 0;
}
static uint64_t host_native_call(HostImport *entry,void *caller,uintptr_t *arguments){
    /* A terminal entry owns a heap-resident call hold until OS termination;
     * none of this returning dispatcher's epilogue can run for that call. */
    if(entry->kind==HOST_FREE_LIBRARY_EXIT_THREAD)host_native_module_exit(entry,caller,arguments);
    DWORD incoming=GetLastError();int crt=host_is_crt_allocation(entry->kind),crtReady=0;
    HostCrtThreadErrors crtBefore={0},crtAfter={0};
    if(crt){crtReady=host_crt_allocation_errors(entry,&crtBefore);crtAfter=crtBefore;}
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    int restricted=state&&(state->heapCallback||state->toolingRestricted||state->vmDetaching||state->vmThreadEnding||state->hostUnloadImage||state->hostRelease
            ||state->hostLoad&&state->hostLoad->calling);
    if(restricted){
        if(!TryAcquireSRWLockShared(&host_imports)){
            if(crt)return host_crt_allocation_refuse(&crtBefore,ERROR_BUSY);SetLastError(ERROR_BUSY);return 0;}
        if(!TryAcquireSRWLockShared(&native_records)){ReleaseSRWLockShared(&host_imports);
            if(crt)return host_crt_allocation_refuse(&crtBefore,ERROR_BUSY);SetLastError(ERROR_BUSY);return 0;}
    }else {AcquireSRWLockShared(&host_imports);AcquireSRWLockShared(&native_records);}
    int associated=host_entry_association(entry,state,NULL,NULL);
    if(associated==1)InterlockedIncrement(&entry->image->active);
    ReleaseSRWLockShared(&native_records);ReleaseSRWLockShared(&host_imports);
    if(associated!=1){DWORD error=associated?ERROR_INVALID_DATA:ERROR_MOD_NOT_FOUND;
        if(crt)return host_crt_allocation_refuse(&crtBefore,error);SetLastError(error);return 0;}
    uint64_t result=0;DWORD error=incoming;int returned=0;
    int callback=state&&(!crt||crtReady);if(callback)native_thread_callback_enter(state);
    __try {
        if(crt)host_crt_allocation_restore(&crtBefore);else SetLastError(incoming);
        result=host_native_dispatch(entry,caller,arguments);error=GetLastError();
        if(crtReady)crtAfter=host_crt_allocation_result(&crtBefore);returned=1;
    } __finally {
        DWORD interrupted=GetLastError();if(crtReady&&!returned)crtAfter=host_crt_allocation_result(&crtBefore);
        if(InterlockedDecrement(&entry->image->active)==0&&InterlockedCompareExchange(&entry->image->retiring,0,0))native_thread_worker_wake();
        if(callback)native_thread_callback_leave(state);
        if(crtReady)host_crt_allocation_restore(&crtAfter);else SetLastError(returned?error:interrupted);
    }
    return result;
}
static unsigned host_import_arguments(int kind){
    switch(kind){
        case HOST_CREATE_W:case HOST_CREATE_A:return 10;
        case HOST_CREATE_USER_W:case HOST_CREATE_USER_A:case HOST_CREATE_LOGON_W:return 11;
        case HOST_CREATE_TOKEN_W:return 9;
        case HOST_CREATE_THREAD:return 6;
        case HOST_CREATE_REMOTE_THREAD:return 7;
        case HOST_CREATE_REMOTE_THREAD_EX:return 8;
        case HOST_BEGIN_THREAD:return 3;
        case HOST_BEGIN_THREAD_EX:return 6;
        case HOST_LOAD_LIBRARY_A:case HOST_LOAD_LIBRARY_W:return 1;
        case HOST_LOAD_LIBRARY_EX_A:case HOST_LOAD_LIBRARY_EX_W:return 3;
        case HOST_WRITE_PROCESS_MEMORY:case HOST_VIRTUAL_PROTECT_EX:case HOST_READ_PROCESS_MEMORY:return 5;
        case HOST_VIRTUAL_PROTECT:return 4;
        case HOST_VIRTUAL_FREE:return 3;
        case HOST_VIRTUAL_FREE_EX:return 4;
        case HOST_UNMAP_VIEW:return 1;
        case HOST_UNMAP_VIEW_EX:return 2;
        case HOST_FREE_LIBRARY_EXIT_THREAD:return 2;
        case HOST_HEAP_ALLOC:case HOST_HEAP_FREE:return 3;
        case HOST_HEAP_REALLOC:case HOST_VIRTUAL_ALLOC:return 4;
        case HOST_VIRTUAL_ALLOC_EX:return 5;
        case HOST_CRT_MALLOC:case HOST_CRT_FREE:case HOST_HEAP_DESTROY:return 1;
        case HOST_CRT_CALLOC:case HOST_CRT_REALLOC:return 2;
        case HOST_DUPLICATE:return 7;
        case HOST_HANDLE_FLAGS:case HOST_OPEN_PROCESS:case HOST_OPEN_THREAD:return 3;
        case HOST_CLOSE:case HOST_RESUME_THREAD:case HOST_SUSPEND_THREAD:case HOST_FREE_LIBRARY:return 1;
        default:return 2;
    }
}
static void *host_import_entry(HostImport *entry){
    /* Keep the native caller as well as the exact import/result identity. A
     * cached dynamic pointer can be consumed from a different registered image. */
    unsigned arguments=host_import_arguments(entry->kind),frame=((32+arguments*8+15)&~15)+8,capacity=4096;
    unsigned char *page=(unsigned char*)VirtualAlloc(NULL,capacity,MEM_COMMIT|MEM_RESERVE,PAGE_READWRITE);
    if(!page)return NULL;NativeCode code={page,page};
    nb(&code,0x48);nb(&code,0x81);nb(&code,0xec);nw(&code,frame);
    unsigned registers[]={1,2,8,9};
    for(unsigned i=0;i<arguments&&i<4;i++)ns(&code,registers[i],32+i*8);
    for(unsigned i=4;i<arguments;i++){nl(&code,0,frame+40+(i-4)*8);ns(&code,0,32+i*8);}
    ni(&code,1,(uintptr_t)entry);nl(&code,2,frame);
    nb(&code,0x4c);nb(&code,0x8d);nb(&code,0x84);nb(&code,0x24);nw(&code,32);
    nc(&code,(void*)host_native_call);
    nb(&code,0x48);nb(&code,0x81);nb(&code,0xc4);nw(&code,frame);nb(&code,0xc3);
    entry->unwind.BeginAddress=0;entry->unwind.EndAddress=(DWORD)(code.at-page);entry->unwind.UnwindData=capacity-16;
    unsigned char *unwind=page+capacity-16;unwind[0]=1;unwind[1]=7;unwind[2]=2;unwind[3]=0;unwind[4]=7;unwind[5]=1;
    unsigned short scaled=(unsigned short)(frame/8);memcpy(unwind+6,&scaled,2);
    DWORD protection;
    if(!host_real_virtual_protect(page,capacity,PAGE_EXECUTE_READ,&protection)||!RtlAddFunctionTable(&entry->unwind,1,(DWORD64)(uintptr_t)page)){
        host_real_virtual_free(page,0,MEM_RELEASE);return NULL;
    }
    FlushInstructionCache(GetCurrentProcess(),page,capacity);entry->slotOwned=entry->slot!=NULL;return page;
}
static int host_exchange_import(HostImport *entry,void *expected,void *replacement){
    DWORD protection;if(!host_real_virtual_protect((void*)entry->slot,sizeof(*entry->slot),PAGE_READWRITE,&protection))return 0;
    void *old=InterlockedCompareExchangePointer(entry->slot,replacement,expected);
    DWORD ignored;BOOL restored=host_real_virtual_protect((void*)entry->slot,sizeof(*entry->slot),protection,&ignored);
    return old==expected&&restored;
}
static const char *host_api_names[HOST_KIND_COUNT]={NULL,"CreateProcessW","GetProcAddress","CreateProcessA",
        "CreateProcessAsUserW","CreateProcessAsUserA","CreateProcessWithTokenW","CreateProcessWithLogonW",
        "CloseHandle","DuplicateHandle","SetHandleInformation","OpenProcess","OpenThread",
        "ResumeThread","SuspendThread","TerminateProcess","TerminateThread",
        "CreateThread","CreateRemoteThread","CreateRemoteThreadEx","_beginthread","_beginthreadex",
        "LoadLibraryA","LoadLibraryW","LoadLibraryExA","LoadLibraryExW","FreeLibrary",
        "WriteProcessMemory","VirtualProtect","VirtualProtectEx",
        "VirtualFree","VirtualFreeEx","UnmapViewOfFile","UnmapViewOfFileEx","FreeLibraryAndExitThread","ReadProcessMemory",
        "HeapAlloc","HeapReAlloc","HeapFree","VirtualAlloc","VirtualAllocEx","malloc","calloc","realloc","free","HeapDestroy"};
static void host_windows_targets(void **targets){
    host_memory_prepare();
    HMODULE kernel=GetModuleHandleW(L"kernel32.dll"),advapi=GetModuleHandleW(L"advapi32.dll");
    HostGetProcAddress resolve=__atomic_load_n(&host_windows_resolver,__ATOMIC_ACQUIRE);
    if(!resolve&&kernel){
        resolve=(HostGetProcAddress)GetProcAddress(kernel,"GetProcAddress");
        if(resolve)__atomic_store_n(&host_windows_resolver,resolve,__ATOMIC_RELEASE);
    }
    if(resolve&&kernel){void *exit=(void*)resolve(kernel,"ExitThread");
        if(exit)__atomic_store_n(&host_exit_thread_original,exit,__ATOMIC_RELEASE);}
    for(int kind=1;kind<HOST_KIND_COUNT;kind++){
        if(host_is_crt_thread_create(kind)||host_is_crt_allocation(kind)){targets[kind]=NULL;continue;}
        HMODULE module=kind>=HOST_CREATE_USER_W&&kind<=HOST_CREATE_LOGON_W?advapi:kernel;
        targets[kind]=module&&resolve?(void*)resolve(module,host_api_names[kind]):NULL;
        if(targets[kind])__atomic_store_n(&host_windows_api[kind],targets[kind],__ATOMIC_RELEASE);
    }
    host_crt_thread_targets(resolve);
    static const wchar_t *providers[]={L"ucrtbase.dll",L"msvcrt.dll",L"api-ms-win-crt-heap-l1-1-0.dll"};
    for(unsigned i=0;i<sizeof(providers)/sizeof(providers[0]);i++){
        HMODULE provider=GetModuleHandleW(providers[i]);if(!provider)continue;
        for(int kind=HOST_CRT_MALLOC;kind<=HOST_CRT_FREE;kind++)host_crt_provider_target(provider,kind);
    }
}
static void host_refresh_targets(HostImage *image){
    /* A genuine later LoadLibrary may make an alternate-token API available.
     * Resolve its actual Windows export outside the ledgers; this classification
     * never bypasses the import incarnation and current-source checks. */
    void *targets[HOST_KIND_COUNT]={0};host_windows_targets(targets);
    for(int kind=1;kind<HOST_KIND_COUNT;kind++)if(targets[kind])
        __atomic_store_n(&image->targets[kind],targets[kind],__ATOMIC_RELEASE);
}
static int host_target_kind(HostImage *image,void *actual){
    int crtKind=host_crt_thread_target_kind(actual);if(crtKind)return crtKind;
    crtKind=host_crt_allocation_target_kind(actual);if(crtKind)return crtKind;
    if(actual)for(int kind=1;kind<HOST_KIND_COUNT;kind++)
        if(actual==__atomic_load_n(&image->targets[kind],__ATOMIC_ACQUIRE))return kind;
    return 0;
}
static HostImport *host_controlled_entry(HostImage *image,void *actual,int kind){
    if(!actual)return NULL;
    for(HostImport *entry=image->imports;entry;entry=entry->next)
        if(entry->entry==actual&&entry->kind==kind&&host_target_kind(image,entry->original)==kind)return entry;
    return NULL;
}
static int host_declared_kind(unsigned char *base,ULONGLONG name,DWORD size,int rvaBased){
    if(!rvaBased){if(name<(uintptr_t)base)return 0;name-=(uintptr_t)base;}
    if(name>=size||size-name<sizeof(WORD)+1)return 0;
    IMAGE_IMPORT_BY_NAME *symbol=(IMAGE_IMPORT_BY_NAME*)(base+name);
    for(int kind=1;kind<HOST_KIND_COUNT;kind++){
        size_t length=strlen(host_api_names[kind])+1;
        if(size-name>=sizeof(WORD)+length&&!memcmp(symbol->Name,host_api_names[kind],length))return kind;
    }
    return 0;
}
static void host_install_slot(HostImage *image,void *volatile *slot,int declared,int delayed){
    void *actual=__atomic_load_n(slot,__ATOMIC_ACQUIRE),*original=actual;int kind=host_target_kind(image,actual);
    HostImport *known=NULL;
    for(int candidate=1;candidate<HOST_KIND_COUNT&&!known;candidate++)known=host_controlled_entry(image,actual,candidate);
    if(known){kind=known->kind;original=known->original;}
    /* The exact JDK slot already belongs to its ProcessImpl creation scope. It
     * is recognized by its actual installed entry, never by an image name. */
    if(slot==host_import_slot&&actual==(void*)host_create_process
            &&(void*)host_original_create==image->createTarget){image->loadWindow=1;return;}
    for(HostImage *old=host_images;old&&!kind;old=old->next)
        if(!old->live&&old->handle==image->handle)
            for(HostImport *prior=old->imports;prior;prior=prior->next)
                if(prior->entry==actual&&(prior->slot==slot||prior->returned)){
                    kind=host_target_kind(image,prior->original);if(kind){original=prior->original;break;}
                }
    if(!kind){
        if(!declared)return;
        image->loadWindow=1;
        if(delayed!=1){image->failed=1;return;}
    }
    HostImport *entry=(HostImport*)calloc(1,sizeof(*entry));
    if(!entry){image->failed=1;return;}
    entry->image=image;entry->slot=slot;entry->kind=kind?kind:declared;entry->delayed=delayed;
    if(!native_private_register(&entry->slotRange,(const void*)slot,sizeof(*slot))){free(entry);image->failed=1;return;}
    entry->next=image->imports;image->imports=entry;
    image->loadWindow=1;
    if(!kind)return; /* A declared unresolved delay entry is not an API result. */
    entry->original=original;
    if(known){entry->entry=known->entry;return;}
    if(host_is_create(kind)&&!host_worker_ready()){image->failed=1;return;}
    entry->entry=host_import_entry(entry);
    if(!entry->entry){image->failed=1;return;}
    /* Retain code even after a failed replacement; a caller may have fetched it. */
    if(!host_exchange_import(entry,actual,entry->entry))image->failed=1;
}
static int host_delay_gaps(HostImage *image){
    for(HostImport *entry=image->imports;entry;entry=entry->next)
        if(entry->delayed==1&&!host_controlled_entry(image,
                __atomic_load_n(entry->slot,__ATOMIC_ACQUIRE),entry->kind))return 1;
    return 0;
}
static int host_result_has_owner(HostImport *entry,Owner *owner){
    if(!owner)return 1;
    for(OwnerLink *link=entry->sources;link;link=link->next)if(link->owner==owner)return 1;
    return 0;
}
static void host_promote_delays(HostImage *image,Owner *owner,HostImport *only){
    /* host_imports is exclusive; native_records is not held. Only a real delay
     * slot containing one of our actual resolution results can be rebound.
     * The original returned pointer and its provenance remain untouched. */
    if(!image->live)return;
    for(HostImport *slot=image->imports;slot;slot=slot->next){
        if(slot->delayed!=1||!slot->slot)continue;
        void *actual=__atomic_load_n(slot->slot,__ATOMIC_ACQUIRE);HostImport *result=NULL;
        for(HostImport *candidate=image->imports;candidate;candidate=candidate->next)
            if(candidate->returned&&candidate->entry==actual&&candidate->kind==slot->kind
                    &&(!only||candidate==only)&&host_result_has_owner(candidate,owner)
                    &&host_target_kind(image,candidate->original)==slot->kind){result=candidate;break;}
        if(!result)continue;
        if(!slot->slotOwned){
            slot->original=result->original;
            void *entry=host_import_entry(slot);
            if(!entry){image->failed=1;continue;}
            slot->entry=entry;
        }
        /* This entry has the real IAT slot, not the first resolver's library or
         * owners. Each later IAT consumption selects its current actual Binding.
         * Saved copies of result->entry still use the original result record. */
        if(!host_exchange_import(slot,actual,slot->entry))image->failed=1;
    }
}
typedef struct HostInstruction {unsigned length;int flow,relative,ripCall;} HostInstruction;
enum { HOST_NEXT,HOST_CONDITIONAL,HOST_JUMP,HOST_END };
/* Lengths are decoded forward from a function-table entry, never backwards
 * from an arbitrary byte. Unknown encodings and indirect jumps do not prove a
 * call boundary; in those cases the returned pointer keeps its own sources. */
static int host_instruction(const unsigned char *first,const unsigned char *end,HostInstruction *instruction){
    size_t available=(size_t)(end-first);if(!available)return 0;
    const unsigned char *at=first,*limit=first+(available<15?available:15);
    unsigned operand16=0,address32=0,rex=0,callOverride=0;
    memset(instruction,0,sizeof(*instruction));
    while(at<limit){
        unsigned prefix=*at;
        if(prefix>=0x40&&prefix<=0x4f){rex=prefix;at++;continue;}
        if(prefix==0x66){operand16=1;callOverride=1;}
        else if(prefix==0x67){address32=1;callOverride=1;}
        else if(prefix==0x64||prefix==0x65||prefix==0xf0)callOverride=1;
        else if(prefix!=0x2e&&prefix!=0x36&&prefix!=0x3e&&prefix!=0x26&&prefix!=0xf2&&prefix!=0xf3)break;
        rex=0;at++;
    }
    if(at==limit)return 0;
    unsigned op=*at++,two=0,modrm=0,immediate=0,relative=0;
    unsigned operand=operand16&&!(rex&8)?2:4;
    if(op==0x0f){
        if(at==limit)return 0;two=*at++;
        if(two>=0x80&&two<=0x8f){
            if(operand16)return 0;immediate=relative=4;instruction->flow=HOST_CONDITIONAL;
        }else if((two>=0x10&&two<=0x17)||two==0x1e||two==0x1f
                ||(two>=0x28&&two<=0x2f)||(two>=0x40&&two<=0x6f)
                ||(two>=0x74&&two<=0x76)||(two>=0x7c&&two<=0x7f)
                ||(two>=0x90&&two<=0x9f)||two==0xa3||two==0xa5||two==0xab
                ||two==0xad||two==0xae||two==0xaf||(two>=0xb0&&two<=0xbf)
                ||two==0xc0||two==0xc1||two==0xc3||two==0xc7||(two>=0xd0&&two<=0xfe)){
            modrm=1;if(two==0xba)immediate=1;if(two==0xb9)instruction->flow=HOST_END;
        }else if((two>=0x70&&two<=0x73)||two==0xa4||two==0xac
                ||two==0xc2||two==0xc4||two==0xc5||two==0xc6){modrm=1;immediate=1;}
        else if(two==0x0b)instruction->flow=HOST_END;
        else if(two!=0x31&&two!=0x77&&two!=0xa0&&two!=0xa1&&two!=0xa2
                &&two!=0xa8&&two!=0xa9&&!(two>=0xc8&&two<=0xcf))return 0;
    }else if(op<=0x3d&&(op&7)<=5){
        unsigned form=op&7;if(form<4)modrm=1;else immediate=form==4?1:operand;
    }else if(op>=0x50&&op<=0x5f){}
    else if(op==0x63)modrm=1;
    else if(op==0x68)immediate=operand16?2:4;
    else if(op==0x69){modrm=1;immediate=operand;}
    else if(op==0x6a)immediate=1;
    else if(op==0x6b){modrm=1;immediate=1;}
    else if((op>=0x70&&op<=0x7f)||(op>=0xe0&&op<=0xe3)){
        immediate=relative=1;instruction->flow=HOST_CONDITIONAL;
    }else if(op==0x80||op==0x81||op==0x83){modrm=1;immediate=op==0x81?operand:1;}
    else if(op>=0x84&&op<=0x8f)modrm=1;
    else if(op>=0x90&&op<=0x9f&&op!=0x9a){}
    else if(op>=0xa0&&op<=0xa3)immediate=address32?4:8;
    else if((op>=0xa4&&op<=0xa7)||(op>=0xaa&&op<=0xaf)){}
    else if(op==0xa8||op==0xa9)immediate=op==0xa8?1:operand;
    else if(op>=0xb0&&op<=0xb7)immediate=1;
    else if(op>=0xb8&&op<=0xbf)immediate=rex&8?8:operand;
    else if(op==0xc0||op==0xc1){modrm=1;immediate=1;}
    else if(op==0xc2){immediate=2;instruction->flow=HOST_END;}
    else if(op==0xc3||op==0xcc||op==0xf1||op==0xf4)instruction->flow=HOST_END;
    else if(op==0xc6||op==0xc7){modrm=1;immediate=op==0xc6?1:operand;}
    else if(op==0xc8)immediate=3;
    else if(op==0xc9||op==0xd7||(op>=0xec&&op<=0xef)||op==0xf5||(op>=0xf8&&op<=0xfd)){}
    else if((op>=0xd0&&op<=0xd3)||(op>=0xd8&&op<=0xdf)||op==0xf6||op==0xf7||op==0xfe||op==0xff)modrm=1;
    else if(op==0xe8||op==0xe9){
        if(operand16)return 0;immediate=4;if(op==0xe9){relative=4;instruction->flow=HOST_JUMP;}
    }else if(op==0xeb){immediate=relative=1;instruction->flow=HOST_JUMP;}
    else return 0;
    if(modrm){
        if(at==limit)return 0;unsigned value=*at++,mode=value>>6,reg=(value>>3)&7,rm=value&7,displacement=0;
        if((op==0x8f||op==0xc6||op==0xc7)&&reg!=0)return 0;
        if(op==0xfe&&reg>1)return 0;
        if(op==0xf6||op==0xf7){if(reg==1)return 0;if(reg==0)immediate=op==0xf6?1:operand;}
        if(op==0xff){
            if(reg==3||reg==4||reg==5||reg==7)return 0;
            instruction->ripCall=value==0x15&&!callOverride;
        }
        if(op==0x0f&&two==0x1f&&reg!=0)return 0;
        if(op==0x0f&&two==0x1e&&value!=0xfa&&value!=0xfb)return 0;
        if(mode!=3){
            if(rm==4){if(at==limit)return 0;unsigned sib=*at++;if(mode==0&&(sib&7)==5)displacement=4;}
            else if(mode==0&&rm==5)displacement=4;
            if(mode==1)displacement=1;else if(mode==2)displacement=4;
        }
        if(displacement>(size_t)(limit-at))return 0;at+=displacement;
    }
    if(immediate>(size_t)(limit-at))return 0;
    if(relative==1)instruction->relative=(int8_t)*at;
    else if(relative==4){int32_t value;memcpy(&value,at,sizeof(value));instruction->relative=value;}
    at+=immediate;instruction->length=(unsigned)(at-first);return 1;
}
static int host_call_queue(unsigned char *marks,DWORD *queue,size_t *count,DWORD offset){
    if(marks[offset]==0xff)return 0;
    if(!marks[offset]){marks[offset]=0x80;queue[(*count)++]=offset;}return 1;
}
static void *volatile *host_delay_call_slot(HostImage *image,void *caller){
    uintptr_t pc=(uintptr_t)caller,base=(uintptr_t)image->handle;
    if(pc<base||pc-base<6||pc-base>image->size||!image->functionCount)return NULL;
    const unsigned char *call=(const unsigned char*)(pc-6);
    if(call[0]!=0xff||call[1]!=0x15)return NULL;
    /* PE .pdata supplies an instruction entry point for this actual image.
     * Read it for each consumption; no previous call grants a later permission. */
    RUNTIME_FUNCTION *functions=(RUNTIME_FUNCTION*)(base+image->functionsRva);
    DWORD low=0,high=image->functionCount,rva=(DWORD)(pc-base-1);RUNTIME_FUNCTION *function=NULL;
    while(low<high){
        DWORD middle=low+(high-low)/2;RUNTIME_FUNCTION *candidate=&functions[middle];
        if(rva<candidate->BeginAddress)high=middle;
        else if(rva>=candidate->EndAddress)low=middle+1;
        else{function=candidate;break;}
    }
    if(!function||function->BeginAddress>=function->EndAddress||function->EndAddress>image->size)return NULL;
    DWORD span=function->EndAddress-function->BeginAddress;
    const unsigned char *code=(const unsigned char*)(base+function->BeginAddress);
    unsigned char *marks=(unsigned char*)calloc(span,1);DWORD *queue=(DWORD*)malloc((size_t)span*sizeof(*queue));
    if(!marks||!queue){free(marks);free(queue);return NULL;}
    size_t head=0,count=0;int proven=host_call_queue(marks,queue,&count,0);void *volatile *address=NULL;
    while(proven&&head<count){
        DWORD offset=queue[head++];HostInstruction instruction;
        if(!host_instruction(code+offset,code+span,&instruction)){proven=0;break;}
        for(unsigned i=1;i<instruction.length;i++)if(marks[offset+i]){proven=0;break;}
        if(!proven)break;
        marks[offset]=(unsigned char)instruction.length;memset(marks+offset+1,0xff,instruction.length-1);
        DWORD next=offset+instruction.length;
        if((uintptr_t)(code+next)==pc&&instruction.ripCall){
            int32_t displacement;memcpy(&displacement,code+next-4,sizeof(displacement));
            address=(void *volatile*)(pc+(intptr_t)displacement);
        }
        if(instruction.flow==HOST_CONDITIONAL||instruction.flow==HOST_JUMP){
            int64_t target=(int64_t)next+instruction.relative;
            if(target<0||target>=span){proven=0;break;}
            proven=host_call_queue(marks,queue,&count,(DWORD)target);
        }
        if(proven&&instruction.flow!=HOST_END&&instruction.flow!=HOST_JUMP&&next<span)
            proven=host_call_queue(marks,queue,&count,next);
    }
    /* Direct branch targets must agree with decoded boundaries. Do not turn
     * embedded data, overlapping instructions or an unknown path into a slot. */
    free(marks);free(queue);return proven?address:NULL;
}
static HostImport *host_consumed_entry(HostImport *entry,NativeThread *state,void *caller){
    if(state&&(state->heapCallback||state->toolingRestricted||state->vmDetaching||state->vmThreadEnding))return entry;
    if(!entry->returned)return entry;
    HostImport *consumed=entry;HostImage *image=entry->image;
    AcquireSRWLockExclusive(&host_imports);int mapped=image->live;
    if(!mapped&&state){
        AcquireSRWLockShared(&native_records);
        for(NativeLibraryScope *scope=state->library;scope&&!mapped;scope=scope->previous)
            mapped=scope->unloading&&scope->phase==1&&scope->library->hostImage==image
                    &&scope->library->handle==image->handle&&InterlockedCompareExchange(&scope->library->alive,0,0)
                    &&InterlockedCompareExchange(&scope->library->closing,0,0);
        ReleaseSRWLockShared(&native_records);
    }
    if(mapped){
        void *volatile *address=host_delay_call_slot(image,caller);
        host_promote_delays(image,NULL,NULL);
        if(address)for(HostImport *slot=image->imports;slot;slot=slot->next)
            if(slot->delayed==1&&slot->slot==address&&slot->kind==entry->kind&&slot->slotOwned
                    &&slot->original==entry->original
                    &&__atomic_load_n(slot->slot,__ATOMIC_ACQUIRE)==slot->entry){
                /* The current invocation fetched r through this exact slot,
                 * including before another thread promoted it. Use the slot's
                 * current Binding now; the immutable r record remains dynamic
                 * provenance for every call without this instruction relation. */
                consumed=slot;break;
            }
    }
    ReleaseSRWLockExclusive(&host_imports);return consumed;
}
static void host_module_update(JNIEnv *env,jobject module,jboolean stopped){
    AcquireSRWLockExclusive(&host_imports);AcquireSRWLockExclusive(&native_records);
    Owner *owner=native_owner(env,module);ReleaseSRWLockExclusive(&native_records);
    if(owner){
        if(stopped)for(HostImage *image=host_images;image;image=image->next)host_promote_delays(image,owner,NULL);
        AcquireSRWLockExclusive(&native_records);
        InterlockedExchange(&owner->producer,1);if(stopped){InterlockedExchange(&owner->kernelProtected,0);InterlockedExchange(&owner->stopped,1);}
        ReleaseSRWLockExclusive(&native_records);
        if(stopped)for(HostImage *image=host_images;image;image=image->next)
            InterlockedExchange(&image->retiring,host_module_image_sources_retired_locked(image));
    }else InterlockedIncrement(&native_failures);
    ReleaseSRWLockExclusive(&host_imports);
    native_thread_worker_wake();
    AcquireSRWLockExclusive(&host_records);for(HostProcess *process=host_processes;process;process=process->next)host_collect(process);host_kernel_close_idle();WakeConditionVariable(&host_changed);ReleaseSRWLockExclusive(&host_records);
}
static void host_delay_imports(HostImage *image,unsigned char *base,IMAGE_NT_HEADERS64 *nt,DWORD size){
    if(nt->OptionalHeader.NumberOfRvaAndSizes<=IMAGE_DIRECTORY_ENTRY_DELAY_IMPORT)return;
    IMAGE_DATA_DIRECTORY directory=nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_DELAY_IMPORT];
    if(!directory.VirtualAddress)return;
    if(directory.VirtualAddress>=size||directory.Size>size-directory.VirtualAddress){image->failed=1;return;}
    IMAGE_DELAYLOAD_DESCRIPTOR *descriptors=(IMAGE_DELAYLOAD_DESCRIPTOR*)(base+directory.VirtualAddress);
    for(DWORD i=0;(i+1)*sizeof(*descriptors)<=directory.Size&&descriptors[i].DllNameRVA;i++){
        int rvaBased=(descriptors[i].Attributes.AllAttributes&1)!=0;
        ULONGLONG names=descriptors[i].ImportNameTableRVA,addresses=descriptors[i].ImportAddressTableRVA;
        ULONGLONG bound=descriptors[i].BoundImportAddressTableRVA;
        if(!rvaBased){
            if(names<(uintptr_t)base||addresses<(uintptr_t)base)continue;
            names-=(uintptr_t)base;addresses-=(uintptr_t)base;
            bound=bound>=(uintptr_t)base?bound-(uintptr_t)base:0;
        }
        if(!names||names>=size||!addresses||addresses>=size)continue;
        IMAGE_THUNK_DATA64 *lookup=(IMAGE_THUNK_DATA64*)(base+names),*table=(IMAGE_THUNK_DATA64*)(base+addresses);
        for(DWORD j=0;(j+1)*sizeof(*lookup)<=size-names&&(j+1)*sizeof(*table)<=size-addresses&&lookup[j].u1.AddressOfData;j++){
            int declared=IMAGE_SNAP_BY_ORDINAL64(lookup[j].u1.Ordinal)?0:
                    host_declared_kind(base,lookup[j].u1.AddressOfData,size,rvaBased);
            host_install_slot(image,(void *volatile*)&table[j].u1.Function,declared,1);
            /* A bound delay result can bypass GetProcAddress. Control only its
             * actual API pointer; do not manufacture resolution or loader hooks. */
            if(bound&&bound<size&&(j+1)*sizeof(*table)<=size-bound){
                IMAGE_THUNK_DATA64 *boundTable=(IMAGE_THUNK_DATA64*)(base+bound);
                host_install_slot(image,(void *volatile*)&boundTable[j].u1.Function,0,2);
            }
        }
    }
    image->delayImports=host_delay_gaps(image);
}
static void host_image_imports(HostImage *image){
    image->checked=1;
    unsigned char *base=(unsigned char*)image->handle;IMAGE_DOS_HEADER *dos=(IMAGE_DOS_HEADER*)base;
    if(dos->e_magic!=IMAGE_DOS_SIGNATURE||dos->e_lfanew<=0){image->failed=1;return;}
    IMAGE_NT_HEADERS64 *nt=(IMAGE_NT_HEADERS64*)(base+dos->e_lfanew);
    if(nt->Signature!=IMAGE_NT_SIGNATURE||nt->OptionalHeader.Magic!=IMAGE_NT_OPTIONAL_HDR64_MAGIC){image->failed=1;return;}
    DWORD size=nt->OptionalHeader.SizeOfImage;image->size=size;
    if(nt->OptionalHeader.NumberOfRvaAndSizes>IMAGE_DIRECTORY_ENTRY_EXCEPTION){
        IMAGE_DATA_DIRECTORY functions=nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_EXCEPTION];
        if(functions.VirtualAddress&&functions.VirtualAddress<size&&functions.Size<=size-functions.VirtualAddress
                &&functions.Size%sizeof(RUNTIME_FUNCTION)==0){
            image->functionsRva=functions.VirtualAddress;image->functionCount=functions.Size/sizeof(RUNTIME_FUNCTION);
        }
    }
    IMAGE_DATA_DIRECTORY directory=nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_IMPORT];
    if(directory.VirtualAddress){
        if(directory.VirtualAddress>=size||directory.Size>size-directory.VirtualAddress)image->failed=1;
        else{
            IMAGE_IMPORT_DESCRIPTOR *descriptors=(IMAGE_IMPORT_DESCRIPTOR*)(base+directory.VirtualAddress);
            for(DWORD i=0;(i+1)*sizeof(*descriptors)<=directory.Size&&descriptors[i].Name;i++){
                DWORD names=descriptors[i].OriginalFirstThunk,addresses=descriptors[i].FirstThunk;
                if(!addresses||addresses>=size)continue;
                IMAGE_THUNK_DATA64 *lookup=names&&names<size?(IMAGE_THUNK_DATA64*)(base+names):NULL;
                IMAGE_THUNK_DATA64 *table=(IMAGE_THUNK_DATA64*)(base+addresses);
                for(DWORD j=0;(j+1)*sizeof(*table)<=size-addresses&&table[j].u1.Function;j++){
                    int declared=lookup&&(j+1)*sizeof(*lookup)<=size-names&&!IMAGE_SNAP_BY_ORDINAL64(lookup[j].u1.Ordinal)
                            ?host_declared_kind(base,lookup[j].u1.AddressOfData,size,1):0;
                    host_install_slot(image,(void *volatile*)&table[j].u1.Function,declared,0);
                }
            }
        }
    }
    host_delay_imports(image,base,nt,size);
}
static int host_observe_image_scope(HMODULE handle,NativeLibraryScope *loading){
    if(!handle)return 0;
    /* Resolve outside the ledgers. JVM_LoadLibrary has returned before an early
     * observation; no DLL loader notification or guessed file owner is used. */
    void *targets[HOST_KIND_COUNT]={0};host_windows_targets(targets);(void)host_handle_identity_ready();
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    AcquireSRWLockExclusive(&host_imports);AcquireSRWLockShared(&native_records);
    int early=loading&&state&&state->library==loading&&!loading->unloading&&loading->phase==1
            &&loading->osLoadStarted&&loading->osHandle==handle&&loading->library->handle==handle
            &&loading->library->jni&&!loading->library->builtin
            &&InterlockedCompareExchange(&loading->library->alive,0,0)
            &&!InterlockedCompareExchange(&loading->library->closing,0,0);
    int registered=early;
    for(NativeLibrary *library=native_libraries;library&&!registered;library=library->next)
        if(host_library_current(library,handle))registered=1;
    if(!registered){ReleaseSRWLockShared(&native_records);ReleaseSRWLockExclusive(&host_imports);return 0;}
    HostImage *image=host_images;while(image&&(!image->live||image->handle!=handle))image=image->next;
    if(!image){image=(HostImage*)calloc(1,sizeof(*image));if(image){
        image->handle=handle;memcpy(image->targets,targets,sizeof(targets));
        image->createTarget=targets[HOST_CREATE_W];image->createATarget=targets[HOST_CREATE_A];image->resolveTarget=targets[HOST_RESOLVE];
        image->live=1;image->next=host_images;host_images=image;
    }}
    if(image){
        if(early)loading->library->hostImage=image;
        for(NativeLibrary *library=native_libraries;library;library=library->next)
            if(host_library_current(library,handle))library->hostImage=image;
    }
    ReleaseSRWLockShared(&native_records);
    if(image&&!image->checked)host_image_imports(image);
    if(image)host_static_image_observed(image);
    if(!image)InterlockedIncrement(&native_failures);
    int ready=image&&image->checked&&!image->failed;
    ReleaseSRWLockExclusive(&host_imports);return ready;
}
static void host_observe_image(HMODULE handle){(void)host_observe_image_scope(handle,NULL);}
static int host_observe_loading(NativeLibraryScope *scope){return host_observe_image_scope(scope->osHandle,scope);}
static int host_image_other_live(HostImage *image,NativeLibrary *excluding){
    /* A pending registration counts only after this exact HostImage was bound
     * at its real OS return. Path-only NativeLibrary.handle observations do not. */
    for(NativeLibrary *other=native_libraries;other;other=other->next)
        if(other!=excluding&&other->hostImage==image&&other->handle==image->handle&&other->jni&&!other->builtin
                &&InterlockedCompareExchange(&other->alive,0,0)&&!InterlockedCompareExchange(&other->closing,0,0))return 1;
    if(image->pin&&!image->releasing&&image->loads)return 1;
    return 0;
}
static void host_library_load_failed(NativeLibrary *library){
    /* A replaced/cached JVM_UnloadLibrary entry may bypass the pre-unmap hook.
     * The failed genuine JDK load still invalidates this source. Never inspect
     * its potentially unmapped IAT while disabling its remaining entries. */
    AcquireSRWLockExclusive(&host_imports);AcquireSRWLockShared(&native_records);
    HostImage *image=library->hostImage;int closed=0;
    if(image&&image->live&&!host_image_other_live(image,library)){
        image->failed=1;image->delayImports=1;image->live=0;host_image_private_slots_locked(image);closed=1;
    }
    ReleaseSRWLockShared(&native_records);
    if(closed)host_static_image_released(image,host_static_environment());
    ReleaseSRWLockExclusive(&host_imports);
}
static int host_library_releasing(NativeLibrary *library,int prepare){
    AcquireSRWLockExclusive(&host_imports);HostImage *image=library->hostImage;
    if(!image){InterlockedExchange(&library->closing,1);ReleaseSRWLockExclusive(&host_imports);return 1;}
    AcquireSRWLockShared(&native_records);int shared=host_image_other_live(image,library);
    ReleaseSRWLockShared(&native_records);
    if(prepare&&!shared&&image->live&&!host_static_image_prepare(image,host_static_environment())){
        ReleaseSRWLockExclusive(&host_imports);return 0;
    }
    InterlockedExchange(&library->closing,1);
    host_promote_delays(image,NULL,NULL);
    /* JNI_OnUnload runs inside the real unload call. Restoring the bare API
     * before that call would reopen process creation for the stopped source.
     * Disable the last image entry first. Only its exact live unload scope may
     * still call it; other callers are refused without reading a retired IAT. */
    if(!shared&&image->live){
        if(!prepare)host_static_image_prepare(image,host_static_environment());
        image->delayImports=host_delay_gaps(image);image->live=0;host_image_private_slots_locked(image);
    }
    ReleaseSRWLockExclusive(&host_imports);
    return 1;
}
static void host_refuse(JNIEnv *env,const char *message){
    if(native_original.ExceptionCheck(env))return;
    jclass failure=native_original.FindClass(env,"java/io/IOException");
    if(failure){native_original.ThrowNew(env,failure,message);native_original.DeleteLocalRef(env,failure);}
}
static jlong JNICALL host_process_create(JNIEnv *env,jclass type,jstring command,jstring environment,
        jstring directory,jlongArray handles,jboolean redirect){
    NativeThread *state=native_thread();OwnerLink *owners=NULL;
    if(!state||!host_capture(env,state,__builtin_return_address(0),&owners)){
        native_owner_links_free(owners);host_refuse(env,"HOST_PROCESS_SOURCE_UNOBSERVED");return 0;
    }
    if(native_owner_stopped(owners)){
        native_owner_links_free(owners);host_refuse(env,"RONOVA_TERMINAL_PROCESS_START_REFUSED");return 0;
    }
    if(owners&&(!host_ready||__atomic_load_n(host_import_slot,__ATOMIC_ACQUIRE)!=(void*)host_create_process)){
        native_owner_links_free(owners);host_refuse(env,"HOST_PROCESS_IMPORT_CHANGED");return 0;
    }
    HostCreation scope={owners,NULL,state->processScope};state->processScope=&scope;
    jlong result=host_original_process(env,type,command,environment,directory,handles,redirect);
    state->processScope=scope.previous;
    if(scope.created){
        AcquireSRWLockExclusive(&host_records);
        if(result&&(HANDLE)(uintptr_t)result==scope.created->original&&!native_original.ExceptionCheck(env)){
            scope.created->pending=1;scope.created->pendingNext=state->processCreated;state->processCreated=scope.created;
        }else if(!scope.created->failed){
            scope.created->failed=1;scope.created->failedProcess=scope.created->original;
            host_collect(scope.created);WakeAllConditionVariable(&host_changed);
        }
        scope.created->borrowed=0;
        ReleaseSRWLockExclusive(&host_records);
    }
    native_owner_links_free(owners);return result;
}
/* Only the import in the code image of the actual JDK native create export is changed. */
static int host_import(HMODULE image){
    if(host_import_slot&&native_image((void*)host_import_slot)==image
            &&__atomic_load_n(host_import_slot,__ATOMIC_ACQUIRE)==(void*)host_create_process)
        return native_private_register(&host_process_import_range,(const void*)host_import_slot,sizeof(*host_import_slot));
    native_private_unregister(&host_process_import_range);
    unsigned char *base=(unsigned char*)image;IMAGE_DOS_HEADER *dos=(IMAGE_DOS_HEADER*)base;
    if(dos->e_magic!=IMAGE_DOS_SIGNATURE||dos->e_lfanew<=0)return 0;
    IMAGE_NT_HEADERS64 *nt=(IMAGE_NT_HEADERS64*)(base+dos->e_lfanew);
    if(nt->Signature!=IMAGE_NT_SIGNATURE||nt->OptionalHeader.Magic!=IMAGE_NT_OPTIONAL_HDR64_MAGIC)return 0;
    DWORD size=nt->OptionalHeader.SizeOfImage;
    IMAGE_DATA_DIRECTORY directory=nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_IMPORT];
    if(!directory.VirtualAddress||directory.VirtualAddress>=size||directory.Size>size-directory.VirtualAddress)return 0;
    HMODULE kernel=GetModuleHandleW(L"kernel32.dll");void *expected=kernel?(void*)GetProcAddress(kernel,"CreateProcessW"):NULL;
    if(!expected)return 0;
    IMAGE_IMPORT_DESCRIPTOR *descriptors=(IMAGE_IMPORT_DESCRIPTOR*)(base+directory.VirtualAddress);
    for(DWORD i=0;(i+1)*sizeof(*descriptors)<=directory.Size&&descriptors[i].Name;i++){
        DWORD names=descriptors[i].OriginalFirstThunk,addresses=descriptors[i].FirstThunk;
        if(!names||names>=size||!addresses||addresses>=size)continue;
        IMAGE_THUNK_DATA64 *lookup=(IMAGE_THUNK_DATA64*)(base+names),*table=(IMAGE_THUNK_DATA64*)(base+addresses);
        for(DWORD j=0;(j+1)*sizeof(*lookup)<=size-names&&(j+1)*sizeof(*table)<=size-addresses&&lookup[j].u1.AddressOfData;j++){
            if(IMAGE_SNAP_BY_ORDINAL64(lookup[j].u1.Ordinal))continue;
            ULONGLONG rva=lookup[j].u1.AddressOfData;
            if(rva>=size||size-rva<sizeof(WORD)+sizeof("CreateProcessW"))continue;
            IMAGE_IMPORT_BY_NAME *entry=(IMAGE_IMPORT_BY_NAME*)(base+rva);
            if(memcmp(entry->Name,"CreateProcessW",sizeof("CreateProcessW")))continue;
            void *volatile *slot=(void *volatile*)&table[j].u1.Function;
            if(__atomic_load_n(slot,__ATOMIC_ACQUIRE)!=expected)return 0;
            DWORD protection;
            if(!host_real_virtual_protect((void*)slot,sizeof(*slot),PAGE_READWRITE,&protection))return 0;
            AcquireSRWLockExclusive(&native_private_gate);
            host_original_create=(HostCreateProcessW)expected;host_import_slot=slot;
            void *old=InterlockedCompareExchangePointer(slot,(void*)host_create_process,expected);
            int registered=0;
            if(old==expected||old==(void*)host_create_process)
                registered=native_private_register_locked(&host_process_import_range,(const void*)slot,sizeof(*slot));
            ReleaseSRWLockExclusive(&native_private_gate);
            DWORD ignored;BOOL restored=host_real_virtual_protect((void*)slot,sizeof(*slot),protection,&ignored);
            /* Failed restoration does not undo an actual installed control
             * entry. Its stable JDK-image slot remains registered in that case. */
            return old==expected&&registered&&restored;
        }
    }
    return 0;
}
static int host_install(JNIEnv *env,jclass type,HMODULE image,void *target){
    if(host_ready)return host_process_type&&native_original.IsSameObject(env,type,host_process_type);
    void *targets[HOST_KIND_COUNT]={0};host_windows_targets(targets);
    host_process_handle=native_original.GetFieldID(env,type,"handle","J");
    host_process_gate=native_original.GetStaticMethodID(env,native_controller,"processCaller","(Ljava/lang/String;)Z");
    if(!host_process_handle||!host_process_gate)return 0;
    if(!host_process_type)host_process_type=(jclass)native_original.NewGlobalRef(env,type);
    host_original_process=(HostProcessCreate)target;
    if(!host_process_type||!host_handle_identity_ready()||!host_import(image))return 0;
    if(!host_worker_ready())return 0;
    /* The retained worker handle belongs to this backend, not a selected producer. */
    InterlockedExchange(&host_ready,1);return 1;
}
static int host_bind(JNIEnv *env,jclass type,jmethodID method,void *target,void **replacement){
    if(!native_jni_ready||!native_bootstrap_class(env,type,"Ljava/lang/ProcessImpl;"))return 0;
    char *name=NULL,*signature=NULL;
    int match=(*native_ti)->GetMethodName(native_ti,method,&name,&signature,NULL)==JVMTI_ERROR_NONE
            &&name&&signature&&!strcmp(name,"create")
            &&!strcmp(signature,"(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[JZ)J");
    if(name)(*native_ti)->Deallocate(native_ti,(unsigned char*)name);
    if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);
    if(!match)return 0;
    if(host_ready){
        if(target!=(void*)host_original_process&&target!=(void*)host_process_create){InterlockedExchange(&host_process_current,0);return 0;}
        host_process_method=method;
        InterlockedExchange(&host_process_current,1);
        *replacement=(void*)host_process_create;return 1;
    }
    HMODULE image=native_image(target);
    if(!image||target!=(void*)GetProcAddress(image,"Java_java_lang_ProcessImpl_create"))return 0;
    if(!host_install(env,type,image,target))return 1;
    host_process_method=method;InterlockedExchange(&host_process_current,1);*replacement=(void*)host_process_create;return 1;
}
static int host_gate(JNIEnv *env,const char *operation){
    if(!native_controller||!host_process_gate)return 0;
    jstring name=native_original.NewStringUTF(env,operation);if(!name)return 0;
    int allowed=native_original.CallStaticBooleanMethod(env,native_controller,host_process_gate,name);
    native_original.DeleteLocalRef(env,name);
    if(!allowed)native_refuse(env,"ACTUAL_PROCESS_RESOURCE_BRIDGE_REQUIRED");return allowed;
}
static int host_identity(JNIEnv *env,jobject process){
    if(!process||!host_process_type)return 0;
    jclass type=native_original.GetObjectClass(env,process);
    int valid=type&&native_original.IsSameObject(env,type,host_process_type);
    if(type)native_original.DeleteLocalRef(env,type);return valid;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_processPrepare0(JNIEnv *env,jclass type,jclass process){
    (void)type;
    if(!native_controller||!native_original.CallStaticBooleanMethod(env,native_controller,native_control_gate)){
        native_refuse(env,"NATIVE_CONTROL_AGENT_REQUIRED");return JNI_FALSE;
    }
    if(!native_bootstrap_class(env,process,"Ljava/lang/ProcessImpl;")||!native_java_image)return JNI_FALSE;
    void *target=(void*)GetProcAddress(native_java_image,"Java_java_lang_ProcessImpl_create");
    if(!target||!host_install(env,process,native_java_image,target))return JNI_FALSE;
    InterlockedExchange(&host_java_boundary,1);return JNI_TRUE;
}
JNIEXPORT jlong JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_processCreateBegin0(JNIEnv *env,jclass type,jlongArray handles){
    (void)type;if(!host_gate(env,"begin"))return 0;
    NativeThread *state=native_thread();OwnerLink *owners=NULL;
    if(!state||!host_capture(env,state,__builtin_return_address(0),&owners)){
        native_owner_links_free(owners);host_refuse(env,"HOST_PROCESS_SOURCE_UNOBSERVED");return 0;
    }
    if(native_owner_stopped(owners)){
        native_owner_links_free(owners);host_refuse(env,"RONOVA_TERMINAL_PROCESS_START_REFUSED");return 0;
    }
    if(!owners)return 0;
    if(!handles||native_original.GetArrayLength(env,handles)!=3
            ||!host_ready||__atomic_load_n(host_import_slot,__ATOMIC_ACQUIRE)!=(void*)host_create_process){
        native_owner_links_free(owners);host_refuse(env,"HOST_PROCESS_CREATE_ASSOCIATION_CHANGED");return 0;
    }
    HostCreation *scope=(HostCreation*)calloc(1,sizeof(*scope));
    if(scope)scope->handles=native_original.NewWeakGlobalRef(env,handles);
    if(!scope||!scope->handles){if(scope)free(scope);native_owner_links_free(owners);host_refuse(env,"HOST_PROCESS_SOURCE_CAPACITY");return 0;}
    scope->owners=owners;scope->constructor=1;scope->token=InterlockedIncrement64(&host_next_creation);
    scope->previous=state->processScope;state->processScope=scope;return (jlong)scope->token;
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_processCreateEnd0(JNIEnv *env,jclass type,jlong token,jlong result,jlongArray handles){
    (void)type;if(!host_gate(env,"end"))return;
    NativeThread *state=native_thread();HostCreation *scope=state?state->processScope:NULL;
    if(!scope||!scope->constructor||scope->token!=token||!native_original.IsSameObject(env,scope->handles,handles)){
        native_refuse(env,"HOST_PROCESS_CREATE_SCOPE_CHANGED");return;
    }
    state->processScope=scope->previous;
    if(scope->created){
        AcquireSRWLockExclusive(&host_records);
        if(result&&(HANDLE)(uintptr_t)result==scope->created->original&&!scope->created->failed){
            scope->created->pending=1;scope->created->pendingNext=state->processCreated;state->processCreated=scope->created;
        }else if(!scope->created->failed){
            scope->created->failed=1;scope->created->failedProcess=scope->created->original;host_collect(scope->created);
        }
        scope->created->borrowed=0;WakeAllConditionVariable(&host_changed);ReleaseSRWLockExclusive(&host_records);
    }
    native_original.DeleteWeakGlobalRef(env,scope->handles);native_owner_links_free(scope->owners);free(scope);
}
JNIEXPORT jlong JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_processConstructed0(JNIEnv *env,jclass type,jobject process,jlong handle){
    (void)type;if(!host_gate(env,"construct")||!host_identity(env,process)
            ||native_original.GetLongField(env,process,host_process_handle)!=handle)return 0;
    NativeThread *state=native_thread();if(!state)return 0;jlong token=0;
    AcquireSRWLockExclusive(&host_records);
    for(HostProcess **link=&state->processCreated;*link;link=&(*link)->pendingNext){
        HostProcess *record=*link;
        if(record->original!=(HANDLE)(uintptr_t)handle||record->published)continue;
        record->identity=native_original.NewWeakGlobalRef(env,process);
        if(record->identity){record->published=1;record->pending=0;*link=record->pendingNext;record->pendingNext=NULL;token=(jlong)record->token;}
        break;
    }
    ReleaseSRWLockExclusive(&host_records);return token;
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_processExposed0(JNIEnv *env,jclass type,jobject process,jlong token,jobjectArray sources){
    (void)type;if(!host_gate(env,"expose")||!host_identity(env,process))return;
    OwnerLink *owners=NULL;int known=sources&&!native_original.ExceptionCheck(env);
    jsize count=known?native_original.GetArrayLength(env,sources):0;known=known&&count>0;
    AcquireSRWLockExclusive(&native_records);
    for(jsize i=0;i<count&&known;i++){
        jobject module=native_original.GetObjectArrayElement(env,sources,i);Owner *owner=native_owner(env,module);
        known=owner&&native_owner_add(&owners,owner);if(module)native_original.DeleteLocalRef(env,module);
    }
    ReleaseSRWLockExclusive(&native_records);AcquireSRWLockExclusive(&host_records);
    for(HostProcess *record=host_processes;record;record=record->next)
        if(record->token==(uint64_t)token&&record->identity&&native_original.IsSameObject(env,record->identity,process)){
            if(!known||!native_owner_merge(&record->owners,owners))record->unknown=1;host_kernel_refresh(record);break;
        }
    ReleaseSRWLockExclusive(&host_records);native_owner_links_free(owners);
}
JNIEXPORT jstring JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_processRetire0(JNIEnv *env,jclass type,jobject process,jlong token){
    (void)type;if(!host_gate(env,"retire")||!host_identity(env,process))return NULL;
    const char *gap="HOST_PROCESS_CREATION_UNOBSERVED";char detail[128];
    AcquireSRWLockExclusive(&host_records);
    for(HostProcess *record=host_processes;record;record=record->next)
        if(record->token==(uint64_t)token&&record->identity&&native_original.IsSameObject(env,record->identity,process)){
            if(native_original.GetLongField(env,process,host_process_handle)!=(jlong)(uintptr_t)record->original
                    ||!record->retired&&!host_same_handle(record->original,record->monitor))gap="HOST_PROCESS_ORIGINAL_HANDLE_CHANGED";
            else if(record->lifetimeGap)gap="HOST_PROCESS_HANDLE_LIFETIME_UNOBSERVED";
            else if(record->remoteTransfers||record->inheritedTransfers)gap="HOST_PROCESS_HANDLE_TRANSFER_UNOBSERVED";
            else if(!record->retired&&!host_private(record))gap="HOST_PROCESS_SHARED_OR_UNKNOWN_SOURCE";
            else{
                host_collect(record);
                if(record->retired)gap="";
                else if(record->error){_snprintf(detail,sizeof(detail),"HOST_PROCESS_DISPOSITION_PENDING:win32=%lu",(unsigned long)record->error);gap=detail;}
                else gap="HOST_PROCESS_FAMILY_EXIT_PENDING";
            }
            break;
        }
    ReleaseSRWLockExclusive(&host_records);return native_original.NewStringUTF(env,gap);
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_hostReady0(JNIEnv *env,jclass type){
    (void)env;(void)type;return InterlockedCompareExchange(&host_ready,0,0)
            &&(InterlockedCompareExchange(&host_process_current,0,0)||InterlockedCompareExchange(&host_java_boundary,0,0))?JNI_TRUE:JNI_FALSE;
}
static void host_import_state(JNIEnv *env,jobject module,jlong *values){
    AcquireSRWLockExclusive(&host_imports);
    for(HostImage *image=host_images;image;image=image->next){
        AcquireSRWLockShared(&native_records);
        int loaded=0,current=0,resolved=0;
        for(NativeLibrary *library=native_libraries;library;library=library->next)
            if(library->hostImage==image&&native_owner_matches(env,library->owners,module)){
                loaded=1;
                if(image->live&&host_library_current(library,image->handle))current=1;
            }
        for(HostLibraryLoad *load=image->loads;load;load=load->imageNext)
            if(load->actual&&native_owner_matches(env,load->owners,module)){
                loaded=1;if(host_module_load_current(load,image))current=1;
            }
        for(Binding *binding=native_bindings;binding&&!current;binding=binding->next)
            current=image->live&&binding->library&&binding->library->hostImage==image
                    &&host_library_current(binding->library,image->handle)
                    &&InterlockedCompareExchange(&binding->current,0,0)
                    &&InterlockedCompareExchange(&binding->valid,0,0)
                    &&native_owner_matches(env,binding->owners,module);
        for(HostImport *entry=image->imports;entry;entry=entry->next)
            if(entry->returned&&native_owner_matches(env,entry->sources,module)){
                resolved=1;
                if(image->live&&(!entry->library||host_library_current(entry->library,image->handle)))
                    values[host_is_create(entry->kind)?11:entry->kind==HOST_RESOLVE?12:20]++;
            }
        ReleaseSRWLockShared(&native_records);
        if(!loaded&&!current&&!resolved)continue;
        host_promote_delays(image,NULL,NULL);
        int failed=image->failed;
        for(HostImport *entry=current?image->imports:NULL;entry;entry=entry->next){
            if(!entry->slot)continue;
            if(host_controlled_entry(image,__atomic_load_n(entry->slot,__ATOMIC_ACQUIRE),entry->kind))
                values[host_is_create(entry->kind)?6:entry->kind==HOST_RESOLVE?10:19]++;
            else failed=1;
        }
        /* Keep first-window and unconnected-entry coverage attached to the
         * actual loaded library's owners after unload. Never read a retired
         * IAT or infer a process merely because this coverage is incomplete. */
        if(loaded&&image->loadWindow)values[8]++;
        int delayGap=(loaded||current)&&(image->live?host_delay_gaps(image):image->delayImports);
        if(delayGap)values[9]++;
        if(failed||loaded&&image->loadWindow||delayGap)values[7]++;
    }
    AcquireSRWLockShared(&native_records);
    for(NativeLibrary *library=native_libraries;library;library=library->next)
        if(library->handle
                &&host_library_current(library,library->handle)&&!library->hostImage
                &&native_owner_matches(env,library->owners,module))values[7]++;
    ReleaseSRWLockShared(&native_records);ReleaseSRWLockExclusive(&host_imports);
}

static int host_kernel_sources_contain(JNIEnv *env,jobjectArray selected,OwnerLink *owners){
    if(!selected||!owners)return 0;jsize count=native_original.GetArrayLength(env,selected);if(count<=0)return 0;
    for(OwnerLink *link=owners;link;link=link->next){
        int found=0;for(jsize index=0;index<count;index++){
            jobject module=native_original.GetObjectArrayElement(env,selected,index);
            if(module&&native_original.IsSameObject(env,module,link->owner->module))found=1;
            if(module)native_original.DeleteLocalRef(env,module);if(found)break;
        }if(!found)return 0;
    }return !native_original.ExceptionCheck(env);
}
static void JNICALL host_kernel_owner_protect0(JNIEnv *env,jclass type,jboolean protect){
    if(!native_controller||!native_original.IsSameObject(env,type,native_controller))return;
    jmethodID gate=native_original.GetStaticMethodID(env,native_controller,"hostKernelCoreCaller","()Z");
    if(!gate||!native_original.CallStaticBooleanMethod(env,native_controller,gate)){
        native_refuse(env,"HOST_KERNEL_OWNER_ACTUAL_CORE_REQUIRED");return;
    }
    if(protect&&!host_worker_ready()){
        native_refuse(env,"HOST_KERNEL_OWNER_MAINTENANCE_UNAVAILABLE");return;
    }
    AcquireSRWLockExclusive(&host_records);
    host_jvm_owner_desired=protect?1:0;host_jvm_owner_pending=1;
    host_kernel_close_idle();WakeConditionVariable(&host_changed);ReleaseSRWLockExclusive(&host_records);
}
static void JNICALL host_kernel_protect0(JNIEnv *env,jclass type,jobject module,jboolean protect){
    (void)type;jmethodID gate=native_controller?native_original.GetStaticMethodID(env,native_controller,"hostKernelCoreCaller","()Z"):NULL;
    if(!gate||!native_original.CallStaticBooleanMethod(env,native_controller,gate)){
        native_refuse(env,"HOST_KERNEL_CORE_REQUIRED");return;
    }
    AcquireSRWLockExclusive(&native_records);Owner *owner=native_owner(env,module);
    int stopped=owner&&InterlockedCompareExchange(&owner->stopped,0,0);
    if(owner&&(!protect||!stopped))InterlockedExchange(&owner->kernelProtected,protect?1:0);ReleaseSRWLockExclusive(&native_records);
    if(protect&&stopped){native_refuse(env,"HOST_KERNEL_STOPPED_MODULE_PROTECTION_REFUSED");return;}
    if(!owner){native_refuse(env,"HOST_KERNEL_ACTUAL_MODULE_REQUIRED");return;}
    /* Opening the selected backend returns its actual access/signing/availability failure. */
    if(protect){HostKernelResult result={0};host_kernel_open(&result);}
    AcquireSRWLockExclusive(&host_records);
    for(HostProcess *process=host_processes;process;process=process->next)if(native_owner_matches(env,process->owners,module))host_kernel_refresh(process);
    host_kernel_close_idle();WakeConditionVariable(&host_changed);ReleaseSRWLockExclusive(&host_records);
}
static jstring JNICALL host_kernel_file0(JNIEnv *env,jclass type,jobject descriptor,jobjectArray selected){
    (void)type;
    jmethodID scope=native_controller?native_original.GetStaticMethodID(env,native_controller,"hostKernelFileCaller","(Ljava/io/FileDescriptor;[Ljava/lang/Module;)Z"):NULL;
    if(!scope||!native_original.CallStaticBooleanMethod(env,native_controller,scope,descriptor,selected)){
        native_refuse(env,"HOST_KERNEL_ACTUAL_FILE_SCOPE_REQUIRED");return NULL;
    }
    jclass fd=native_original.FindClass(env,"java/io/FileDescriptor");
    jfieldID field=fd?native_original.GetFieldID(env,fd,"handle","J"):NULL;
    int exact=fd&&descriptor&&native_original.IsInstanceOf(env,descriptor,fd);
    if(fd)native_original.DeleteLocalRef(env,fd);if(!exact||!field)return NULL;
    HANDLE handle=(HANDLE)(uintptr_t)native_original.GetLongField(env,descriptor,field);
    if(!handle||handle==INVALID_HANDLE_VALUE)return native_original.NewStringUTF(env,"HOST_KERNEL_FILE_ORIGINAL_HANDLE_UNAVAILABLE");
    int matched=0,failed=0;HostKernelResult result={0},firstFailure={0};
    AcquireSRWLockExclusive(&host_records);
    for(HostProcess *process=host_processes;process;process=process->next){
        if(process->creating||process->retired||process->exited||!host_private(process)||!host_kernel_sources_contain(env,selected,process->owners))continue;
        if(!process->monitor||WaitForSingleObject(process->monitor,0)!=WAIT_TIMEOUT)continue;
        matched++;host_kernel_refresh(process);
        if(!process->kernelTarget.targetId||process->kernelProcessPending){result.error=process->kernelError;result.status=process->kernelStatus;if(!failed++)firstFailure=result;continue;}
        HostKernelFile *file=process->kernelFiles;
        while(file&&!host_same_handle(file->target.witness,handle))file=file->next;
        if(!file){file=(HostKernelFile*)calloc(1,sizeof(*file));if(!file){process->kernelPending=1;process->kernelError=ERROR_NOT_ENOUGH_MEMORY;result.error=ERROR_NOT_ENOUGH_MEMORY;result.status=0;if(!failed++)firstFailure=result;continue;}
            file->token=++host_next_kernel_file;file->next=process->kernelFiles;process->kernelFiles=file;}
        if(!host_kernel_file(handle,handle,file->token,&process->kernelTarget,RVH_FILE_ALLOWED_FLAGS,&file->target,&result)){
            file->pending=1;file->error=result.error;file->status=result.status;
            process->kernelPending=1;process->kernelError=result.error;process->kernelStatus=result.status;if(!failed++)firstFailure=result;
        }else{file->pending=0;file->error=0;file->status=0;}
    }
    ReleaseSRWLockExclusive(&host_records);
    if(native_original.ExceptionCheck(env))return NULL;
    char text[192];
    if(!matched)return native_original.NewStringUTF(env,"HOST_KERNEL_NO_ACTIVE_EXCLUSIVE_ACTOR");
    if(failed)result=firstFailure;
    _snprintf(text,sizeof(text),"HOST_KERNEL_FILE_%s:actualActors=%d:pending=%d:win32=%lu:ntstatus=%ld",failed?"PENDING":"REGISTERED",matched,failed,(unsigned long)result.error,(long)result.status);
    return native_original.NewStringUTF(env,text);
}
static jlongArray JNICALL host_kernel_state0(JNIEnv *env,jclass type,jobject module){
    if(!native_controller||!native_original.IsSameObject(env,type,native_controller))return NULL;
    if(module&&!native_original.CallStaticBooleanMethod(env,native_controller,native_control_gate)){
        jmethodID core=native_original.GetStaticMethodID(env,native_controller,"hostKernelCoreCaller","()Z");
        if(!core||!native_original.CallStaticBooleanMethod(env,native_controller,core)){native_refuse(env,"HOST_KERNEL_CONTROL_STATE_REQUIRED");return NULL;}
    }
    int64_t raw[17]={0};jlong values[30]={0};
    AcquireSRWLockExclusive(&host_records);
    host_kernel_owner_refresh(1);host_kernel_state(raw);
    if(!raw[0]){
        SecureZeroMemory(&host_jvm_owner_observed,sizeof(host_jvm_owner_observed));
        if(host_jvm_owner_desired){host_jvm_owner_pending=1;host_jvm_owner_error=(DWORD)raw[4];host_jvm_owner_status=(LONG)raw[5];}
    }
    for(unsigned index=0;index<17;index++)values[index]=(jlong)raw[index];
    for(HostProcess *process=host_processes;process;process=process->next){
        if(module&&!native_owner_matches(env,process->owners,module))continue;
        if(process->kernelTarget.witness||process->kernelTarget.targetId)values[17]++;
        for(HostKernelFile *file=process->kernelFiles;file;file=file->next)if(file->target.witness||file->target.targetId)values[18]++;
        if(!process->retired&&process->kernelPending)values[19]++;
        if(!process->retired&&!host_kernel_effects(process))for(OwnerLink *link=process->owners;link;link=link->next)
            if(InterlockedCompareExchange(&link->owner->kernelProtected,0,0)){values[20]++;break;}
    }
    values[21]=host_jvm_owner_desired;
    values[22]=host_jvm_owner_observed.Effects==RVH_OWNER_PROTECT_OUTSIDE_HANDLES
            &&host_jvm_owner_observed.State==RVH_TARGET_ACTIVE&&!host_jvm_owner_pending;
    values[23]=(jlong)host_jvm_owner_observed.TargetId;values[24]=(jlong)host_jvm_owner_observed.HandleRightsReduced;
    values[25]=host_jvm_owner_error;values[26]=host_jvm_owner_status;values[27]=host_jvm_owner_observed.State;
    values[28]=(jlong)host_jvm_owner_observed.ObservedProcessId;values[29]=host_jvm_owner_pending;
    ReleaseSRWLockExclusive(&host_records);
    jlongArray result=native_original.NewLongArray(env,30);if(result)native_original.SetLongArrayRegion(env,result,0,30,values);return result;
}

JNIEXPORT jlongArray JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_hostState0(JNIEnv *env,jclass type,jobject module){
    (void)type;if(!native_controller||!native_original.CallStaticBooleanMethod(env,native_controller,native_control_gate)){
        native_refuse(env,"NATIVE_CONTROL_AGENT_REQUIRED");return NULL;
    }
    jlong values[32]={0};values[18]=__atomic_load_n(&host_compare_handles,__ATOMIC_ACQUIRE)!=NULL;
    AcquireSRWLockExclusive(&host_records);host_prune(env);
    for(HostProcess *record=host_processes;record;record=record->next){
        if(!native_owner_matches(env,record->owners,module))continue;host_collect(record);
        for(HostHandle *handle=record->handles;handle;handle=handle->next)
            if(handle->live)values[handle->role==1?13:14]++;
        values[15]+=record->remoteTransfers;values[16]+=record->inheritedTransfers;
        if(record->lifetimeGap)values[17]++;
        /* A completed local alias lifetime is distinct from remote tables,
         * inherited aliases and an unobserved close or replacement. */
        if((record->nativeDirect||record->handles)&&record->published&&!record->failed
                &&(host_handles_pending(record)||record->remoteTransfers||record->inheritedTransfers||record->lifetimeGap))values[5]++;
        if(record->retired)continue;values[0]++;
        if(record->unknown||!host_private(record))values[1]++;
        if(!record->published&&!record->failed)values[2]++;
        if(record->error)values[3]++;
        if(record->job){JOBOBJECT_BASIC_ACCOUNTING_INFORMATION accounting;
            if(QueryInformationJobObject(record->job,JobObjectBasicAccountingInformation,&accounting,sizeof(accounting),NULL))values[4]+=accounting.ActiveProcesses;
        }
    }
    ReleaseSRWLockExclusive(&host_records);
    host_import_state(env,module,values);
    host_thread_workers_state(env,module,values);
    host_module_loads_state(env,module,values);
    host_module_releases_state(env,module,values);
    jlongArray result=native_original.NewLongArray(env,32);if(result)native_original.SetLongArrayRegion(env,result,0,32,values);return result;
}
