/* Included after the native source helpers. Each family owns an unnamed Job and
 * duplicates the actual returned process handle; PIDs never authorize disposal. */
#include <stdio.h>
typedef BOOL (WINAPI *HostCreateProcess)(LPCWSTR,LPWSTR,LPSECURITY_ATTRIBUTES,
        LPSECURITY_ATTRIBUTES,BOOL,DWORD,LPVOID,LPCWSTR,LPSTARTUPINFOW,LPPROCESS_INFORMATION);
typedef FARPROC (WINAPI *HostGetProcAddress)(HMODULE,LPCSTR);
typedef jlong (JNICALL *HostProcessCreate)(JNIEnv*,jclass,jstring,jstring,jstring,jlongArray,jboolean);
typedef struct HostProcess {
    uint64_t token;
    HANDLE original,monitor,job,failedProcess,failedThread;
    OwnerLink *owners;
    jweak identity;
    DWORD error;
    int creating,assigned,published,unknown,failed,terminating,retired,borrowed,pending,abandoned,nativeDirect;
    struct HostProcess *next,*pendingNext;
} HostProcess;
typedef struct HostCreation {OwnerLink *owners;HostProcess *created;struct HostCreation *previous;jweak handles;LONG64 token;int constructor;} HostCreation;
static SRWLOCK host_records=SRWLOCK_INIT;
static CONDITION_VARIABLE host_changed=CONDITION_VARIABLE_INIT;
static HostProcess *host_processes;
static uint64_t host_next_token;
static HostCreateProcess host_original_create;
static HostProcessCreate host_original_process;
static void *volatile *host_import_slot;
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
enum { HOST_CREATE=1,HOST_RESOLVE=2 };
typedef struct HostImport {
    struct HostImage *image;
    void *volatile *slot;
    void *original;
    void *entry;
    NativeLibrary *library;
    OwnerLink *sources;
    int kind,delayed,returned,slotOwned;
    RUNTIME_FUNCTION unwind;
    struct HostImport *next;
} HostImport;
typedef struct HostImage {
    HMODULE handle;
    DWORD size,functionsRva,functionCount;
    HostImport *imports;
    void *createTarget,*resolveTarget;
    int live,checked,failed,loadWindow,delayImports;
    struct HostImage *next;
} HostImage;
/* This directory contains actual registered image incarnations. Disabled entries
 * remain callable tombstones, as do retired Binding trampolines; a reused HMODULE
 * never reactivates an old import entry or carries its former sources forward. */
static SRWLOCK host_imports=SRWLOCK_INIT;
static HostImage *host_images;

static int host_private(HostProcess *process){
    if(!process->owners||process->unknown)return 0;
    for(OwnerLink *link=process->owners;link;link=link->next)
        if(!InterlockedCompareExchange(&link->owner->stopped,0,0))return 0;
    return 1;
}
static int host_close(HANDLE *handle,DWORD *error){
    if(!*handle)return 1;
    if(!CloseHandle(*handle)){*error=GetLastError();return 0;}
    *handle=NULL;return 1;
}
/* Queries and waits are nonblocking. Successful termination is not exit evidence. */
static void host_collect(HostProcess *process){
    if(process->creating||process->retired)return;
    if((process->failed||host_private(process))&&!process->terminating){
        BOOL done=process->assigned?TerminateJobObject(process->job,1)
                :!process->monitor||TerminateProcess(process->monitor,1);
        if(!done){process->error=GetLastError();return;}
        process->terminating=1;
    }
    if(process->job){
        JOBOBJECT_BASIC_ACCOUNTING_INFORMATION accounting;
        if(!QueryInformationJobObject(process->job,JobObjectBasicAccountingInformation,
                &accounting,sizeof(accounting),NULL)){process->error=GetLastError();return;}
        if(accounting.ActiveProcesses)return;
    }
    if(process->monitor){
        DWORD result=WaitForSingleObject(process->monitor,0);
        if(result!=WAIT_OBJECT_0){if(result==WAIT_FAILED)process->error=GetLastError();return;}
    }
    if(!host_close(&process->failedThread,&process->error)
            ||!host_close(&process->failedProcess,&process->error)
            ||!host_close(&process->monitor,&process->error)
            ||!host_close(&process->job,&process->error))return;
    process->retired=1;process->error=0;
}
static DWORD WINAPI host_worker(LPVOID argument){
    (void)argument;AcquireSRWLockExclusive(&host_records);
    for(;;){
        int pending=0;
        for(HostProcess *process=host_processes;process;process=process->next){host_collect(process);pending|=!process->retired;}
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
        int unused=record->failed||record->abandoned
                ||!record->nativeDirect&&record->published&&native_original.IsSameObject(env,record->identity,NULL);
        if(record->retired&&!record->creating&&!record->borrowed&&!record->pending&&unused){
            *link=record->next;if(record->identity)native_original.DeleteWeakGlobalRef(env,record->identity);
            native_owner_links_free(record->owners);SecureZeroMemory(record,sizeof(*record));free(record);
        }else link=&record->next;
    }
}
static BOOL host_create_owned(HostCreation *scope,HostCreateProcess original,int nativeDirect,
        LPCWSTR application,LPWSTR command,
        LPSECURITY_ATTRIBUTES processAttributes,LPSECURITY_ATTRIBUTES threadAttributes,
        BOOL inherit,DWORD flags,LPVOID environment,LPCWSTR directory,
        LPSTARTUPINFOW startup,LPPROCESS_INFORMATION information){
    if(!scope||!scope->owners)return original(application,command,processAttributes,
            threadAttributes,inherit,flags,environment,directory,startup,information);
    if(scope->created||native_owner_stopped(scope->owners)){SetLastError(ERROR_ACCESS_DENIED);return FALSE;}
    HostProcess *process=(HostProcess*)calloc(1,sizeof(*process));
    if(!process||!native_owner_merge(&process->owners,scope->owners)){
        if(process){native_owner_links_free(process->owners);free(process);}SetLastError(ERROR_NOT_ENOUGH_MEMORY);return FALSE;
    }
    process->job=CreateJobObjectW(NULL,NULL);
    JOBOBJECT_EXTENDED_LIMIT_INFORMATION limits;memset(&limits,0,sizeof(limits));
    limits.BasicLimitInformation.LimitFlags=JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
    if(!process->job||!SetInformationJobObject(process->job,JobObjectExtendedLimitInformation,&limits,sizeof(limits))){
        DWORD error=GetLastError();process->failed=1;process->error=error;
        host_publish(process);WakeAllConditionVariable(&host_changed);SetLastError(error);return FALSE;
    }
    process->nativeDirect=nativeDirect;process->unknown=nativeDirect;
    process->creating=1;process->borrowed=1;host_publish(process);scope->created=process;
    BOOL created=original(application,command,processAttributes,threadAttributes,
            inherit,flags|CREATE_SUSPENDED,environment,directory,startup,information);
    DWORD error=created?0:GetLastError();
    if(created){
        process->original=information->hProcess;
        if(!DuplicateHandle(GetCurrentProcess(),information->hProcess,GetCurrentProcess(),
                &process->monitor,0,FALSE,DUPLICATE_SAME_ACCESS)){
            error=GetLastError();process->monitor=information->hProcess;
        }else if(!AssignProcessToJobObject(process->job,information->hProcess))error=GetLastError();
        else{
            process->assigned=1;
            if(native_owner_stopped(process->owners))error=ERROR_ACCESS_DENIED;
            else if(!(flags&CREATE_SUSPENDED)&&ResumeThread(information->hThread)==(DWORD)-1)error=GetLastError();
        }
    }
    AcquireSRWLockExclusive(&host_records);
    if(error){
        process->failed=1;process->error=error;
        if(created){
            if(process->monitor!=information->hProcess)process->failedProcess=information->hProcess;
            process->failedThread=information->hThread;memset(information,0,sizeof(*information));
        }
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
    return host_create_owned(state?state->processScope:NULL,host_original_create,0,
            application,command,processAttributes,threadAttributes,inherit,flags,
            environment,directory,startup,information);
}
static void host_producers(OwnerLink **owners){
    for(OwnerLink **link=owners;*link;){
        if(!InterlockedCompareExchange(&(*link)->owner->producer,0,0)){
            OwnerLink *removed=*link;*link=removed->next;free(removed);
        }else link=&(*link)->next;
    }
}
static int host_capture_caller(NativeThread *state,void *caller,OwnerLink **owners){
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
    if(!selected)for(NativeLibrary *library=native_libraries;library;library=library->next)
        if(library->handle==image&&InterlockedCompareExchange(&library->alive,0,0)
                &&InterlockedCompareExchange(&library->complete,0,0)&&!InterlockedCompareExchange(&library->closing,0,0))
            ready=ready&&native_owner_merge(owners,library->owners);
    ReleaseSRWLockShared(&native_records);return ready;
}
static int host_capture_all(JNIEnv *env,NativeThread *state,void *caller,OwnerLink **owners){
    if(!native_capture_scope_owners(env,state,owners)
            ||!host_capture_caller(state,caller,owners))return 0;
    state->control++;state->sourceCapture++;
    jobjectArray sources=(jobjectArray)native_original.CallStaticObjectMethod(env,native_tasks,native_process_sources);
    state->sourceCapture--;int ready=sources&&!native_original.ExceptionCheck(env);
    if(ready){
        jsize count=native_original.GetArrayLength(env,sources);AcquireSRWLockExclusive(&native_records);
        for(jsize i=0;i<count&&ready;i++){
            jobject module=native_original.GetObjectArrayElement(env,sources,i);Owner *owner=native_owner(env,module);
            ready=owner&&native_owner_add(owners,owner);if(module)native_original.DeleteLocalRef(env,module);
        }
        ReleaseSRWLockExclusive(&native_records);
    }
    if(sources)native_original.DeleteLocalRef(env,sources);state->control--;
    return ready;
}
static int host_capture(JNIEnv *env,NativeThread *state,void *caller,OwnerLink **owners){
    int ready=host_capture_all(env,state,caller,owners);
    /* Runtime carriers do not become producers of every process they implement. */
    host_producers(owners);return ready;
}
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
        if(!associated&&!entry->library)for(NativeLibrary *library=native_libraries;library;library=library->next)
            if(library->hostImage==entry->image&&host_library_current(library,entry->image->handle)){
                associated=1;if(chosen&&chosen!=library)mixed=1;else chosen=library;
                if(owners)ready=ready&&native_owner_merge(owners,library->owners);
            }
    }
    if(selected)*selected=mixed?NULL:chosen;
    return !associated?0:ready?1:-1;
}
static int host_entry_sources(HostImport *entry,NativeThread *state,void *caller,OwnerLink **owners,NativeLibrary **selected,int retainSources){
    AcquireSRWLockShared(&host_imports);AcquireSRWLockShared(&native_records);
    int associated=host_entry_association(entry,state,owners,selected),ready=associated==1;
    ReleaseSRWLockShared(&native_records);
    ReleaseSRWLockShared(&host_imports);
    JNIEnv *env=NULL;
    if(ready&&native_vm&&(*native_vm)->GetEnv(native_vm,(void**)&env,JNI_VERSION_1_8)==JNI_OK){
        if(!state)state=native_thread();
        ready=state&&host_capture_all(env,state,caller,owners);
    }else if(ready){
        /* A detached OS thread has no JNIEnv. Its actual library and active C
         * scopes remain usable; never manufacture Java execution provenance. */
        ready=host_capture_caller(state,caller,owners);
        if(state){
            for(unsigned i=0;i<state->depth&&ready;i++)ready=native_owner_merge(owners,state->stack[i]->owners);
            for(NativeLibraryScope *scope=state->library;scope&&ready;scope=scope->previous)
                ready=native_owner_merge(owners,scope->library->owners);
        }
    }
    if(!retainSources)host_producers(owners);
    if(!ready){SetLastError(associated?ERROR_INVALID_DATA:ERROR_MOD_NOT_FOUND);return 0;}
    return 1;
}
static HostImport *host_consumed_entry(HostImport *entry,NativeThread *state,void *caller);
static BOOL WINAPI host_native_create(LPCWSTR application,LPWSTR command,
        LPSECURITY_ATTRIBUTES processAttributes,LPSECURITY_ATTRIBUTES threadAttributes,
        BOOL inherit,DWORD flags,LPVOID environment,LPCWSTR directory,
        LPSTARTUPINFOW startup,LPPROCESS_INFORMATION information,HostImport *entry,void *caller){
    DWORD incoming=GetLastError();OwnerLink *owners=NULL;
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    entry=host_consumed_entry(entry,state,caller);
    if(!host_entry_sources(entry,state,caller,&owners,NULL,0)){native_owner_links_free(owners);return FALSE;}
    if(native_owner_stopped(owners)){native_owner_links_free(owners);SetLastError(ERROR_ACCESS_DENIED);return FALSE;}
    if(!owners){SetLastError(incoming);return ((HostCreateProcess)entry->original)(application,command,processAttributes,
            threadAttributes,inherit,flags,environment,directory,startup,information);}
    HostCreation scope={0};scope.owners=owners;
    BOOL result=host_create_owned(&scope,(HostCreateProcess)entry->original,1,application,command,processAttributes,
            threadAttributes,inherit,flags,environment,directory,startup,information);
    DWORD error=GetLastError();
    if(scope.created){
        AcquireSRWLockExclusive(&host_records);
        /* Native PROCESS_INFORMATION is published here, without a Java Process.
         * Its successful original handles belong to the caller. Their later
         * transfer/close is unobserved, so this record is not a private family. */
        if(result&&!scope.created->failed)scope.created->published=1;
        scope.created->borrowed=0;host_collect(scope.created);
        WakeAllConditionVariable(&host_changed);ReleaseSRWLockExclusive(&host_records);
    }
    native_owner_links_free(owners);SetLastError(error);return result;
}
static void *host_import_entry(HostImport *entry);
static FARPROC WINAPI host_native_resolve(HMODULE module,LPCSTR name,HostImport *entry,void *caller){
    DWORD incoming=GetLastError();
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    entry=host_consumed_entry(entry,state,caller);
    AcquireSRWLockShared(&host_imports);AcquireSRWLockShared(&native_records);
    int associated=host_entry_association(entry,state,NULL,NULL);
    ReleaseSRWLockShared(&native_records);ReleaseSRWLockShared(&host_imports);
    if(associated!=1){SetLastError(associated?ERROR_INVALID_DATA:ERROR_MOD_NOT_FOUND);return NULL;}
    SetLastError(incoming);FARPROC actual=((HostGetProcAddress)entry->original)(module,name);
    DWORD error=GetLastError();
    int kind=(void*)actual==entry->image->createTarget?HOST_CREATE:
            (void*)actual==entry->image->resolveTarget?HOST_RESOLVE:0;
    if(!actual||!kind){SetLastError(error);return actual;}
    /* Names, ordinals and requested DLL handles do not authorize this result.
     * Only the actual returned Windows API address selects a controlled entry. */
    OwnerLink *owners=NULL;NativeLibrary *library=NULL;
    if(!host_entry_sources(entry,state,caller,&owners,&library,1)){native_owner_links_free(owners);return NULL;}
    if(native_owner_stopped(owners)){native_owner_links_free(owners);SetLastError(ERROR_ACCESS_DENIED);return NULL;}
    HostImport *resolved=(HostImport*)calloc(1,sizeof(*resolved));
    if(resolved){
        resolved->image=entry->image;resolved->original=(void*)actual;resolved->kind=kind;
        resolved->library=library;resolved->sources=owners;resolved->returned=1;
        if(kind!=HOST_CREATE||host_worker_ready())resolved->entry=host_import_entry(resolved);
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
        if(kind==HOST_CREATE)entry->image->loadWindow=1;
    }
    ReleaseSRWLockShared(&native_records);ReleaseSRWLockExclusive(&host_imports);
    if(associated!=1||stopped){
        RtlDeleteFunctionTable(&resolved->unwind);VirtualFree(resolved->entry,0,MEM_RELEASE);
        native_owner_links_free(owners);free(resolved);
        SetLastError(stopped?ERROR_ACCESS_DENIED:ERROR_MOD_NOT_FOUND);return NULL;
    }
    SetLastError(error);return (FARPROC)resolved->entry;
}
static void *host_import_entry(HostImport *entry){
    /* Keep the native caller as well as the exact import/result identity. A
     * cached dynamic pointer can be consumed from a different registered image. */
    unsigned frame=entry->kind==HOST_RESOLVE?40:104,capacity=4096;
    unsigned char *page=(unsigned char*)VirtualAlloc(NULL,capacity,MEM_COMMIT|MEM_RESERVE,PAGE_READWRITE);
    if(!page)return NULL;NativeCode code={page,page};
    nb(&code,0x48);nb(&code,0x81);nb(&code,0xec);nw(&code,frame);
    if(entry->kind==HOST_RESOLVE){
        ni(&code,8,(uintptr_t)entry);nl(&code,9,frame);nc(&code,(void*)host_native_resolve);
    }else{
        for(unsigned i=0;i<48;i+=8){nl(&code,0,frame+40+i);ns(&code,0,32+i);}
        ni(&code,0,(uintptr_t)entry);ns(&code,0,80);nl(&code,0,frame);ns(&code,0,88);
        nc(&code,(void*)host_native_create);
    }
    nb(&code,0x48);nb(&code,0x81);nb(&code,0xc4);nw(&code,frame);nb(&code,0xc3);
    entry->unwind.BeginAddress=0;entry->unwind.EndAddress=(DWORD)(code.at-page);entry->unwind.UnwindData=capacity-16;
    unsigned char *unwind=page+capacity-16;unwind[0]=1;unwind[1]=7;unwind[2]=2;unwind[3]=0;unwind[4]=7;unwind[5]=1;
    unsigned short scaled=(unsigned short)(frame/8);memcpy(unwind+6,&scaled,2);
    DWORD protection;
    if(!VirtualProtect(page,capacity,PAGE_EXECUTE_READ,&protection)||!RtlAddFunctionTable(&entry->unwind,1,(DWORD64)(uintptr_t)page)){
        VirtualFree(page,0,MEM_RELEASE);return NULL;
    }
    FlushInstructionCache(GetCurrentProcess(),page,capacity);entry->slotOwned=entry->slot!=NULL;return page;
}
static int host_exchange_import(HostImport *entry,void *expected,void *replacement){
    DWORD protection;if(!VirtualProtect((void*)entry->slot,sizeof(*entry->slot),PAGE_READWRITE,&protection))return 0;
    void *old=InterlockedCompareExchangePointer(entry->slot,replacement,expected);
    DWORD ignored;BOOL restored=VirtualProtect((void*)entry->slot,sizeof(*entry->slot),protection,&ignored);
    return old==expected&&restored;
}
static int host_target_kind(HostImage *image,void *actual){
    return actual&&actual==image->createTarget?HOST_CREATE:
            actual&&actual==image->resolveTarget?HOST_RESOLVE:0;
}
static HostImport *host_controlled_entry(HostImage *image,void *actual,int kind){
    if(!actual)return NULL;
    for(HostImport *entry=image->imports;entry;entry=entry->next)
        if(entry->entry==actual&&entry->kind==kind&&host_target_kind(image,entry->original)==kind)return entry;
    return NULL;
}
static int host_declared_kind(unsigned char *base,ULONGLONG name,DWORD size,int rvaBased){
    if(!rvaBased){if(name<(uintptr_t)base)return 0;name-=(uintptr_t)base;}
    if(name>=size||size-name<sizeof(WORD)+sizeof("CreateProcessW"))return 0;
    IMAGE_IMPORT_BY_NAME *symbol=(IMAGE_IMPORT_BY_NAME*)(base+name);
    if(!memcmp(symbol->Name,"CreateProcessW",sizeof("CreateProcessW")))return HOST_CREATE;
    if(size-name>=sizeof(WORD)+sizeof("GetProcAddress")
            &&!memcmp(symbol->Name,"GetProcAddress",sizeof("GetProcAddress")))return HOST_RESOLVE;
    return 0;
}
static void host_install_slot(HostImage *image,void *volatile *slot,int declared,int delayed){
    void *actual=__atomic_load_n(slot,__ATOMIC_ACQUIRE);int kind=host_target_kind(image,actual);
    HostImport *known=host_controlled_entry(image,actual,HOST_CREATE);
    if(!known)known=host_controlled_entry(image,actual,HOST_RESOLVE);
    if(known)kind=known->kind;
    /* The exact JDK slot already belongs to its ProcessImpl creation scope. It
     * is recognized by its actual installed entry, never by an image name. */
    if(slot==host_import_slot&&actual==(void*)host_create_process
            &&(void*)host_original_create==image->createTarget){image->loadWindow=1;return;}
    for(HostImage *old=host_images;old&&!kind;old=old->next)
        if(!old->live&&old->handle==image->handle)
            for(HostImport *prior=old->imports;prior;prior=prior->next)
                if(prior->entry==actual&&(prior->slot==slot||prior->returned)){
                    kind=host_target_kind(image,prior->original);if(kind)break;
                }
    if(!kind){
        if(!declared)return;
        if(declared==HOST_CREATE)image->loadWindow=1;
        if(delayed!=1){image->failed=1;return;}
    }
    HostImport *entry=(HostImport*)calloc(1,sizeof(*entry));
    if(!entry){image->failed=1;return;}
    entry->image=image;entry->slot=slot;entry->kind=kind?kind:declared;entry->delayed=delayed;
    entry->next=image->imports;image->imports=entry;
    if(entry->kind==HOST_CREATE)image->loadWindow=1;
    if(!kind)return; /* A declared unresolved delay entry is not an API result. */
    entry->original=kind==HOST_CREATE?image->createTarget:image->resolveTarget;
    if(known){entry->entry=known->entry;return;}
    if(kind==HOST_CREATE&&!host_worker_ready()){image->failed=1;return;}
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
        InterlockedExchange(&owner->producer,1);if(stopped)InterlockedExchange(&owner->stopped,1);
        ReleaseSRWLockExclusive(&native_records);
    }else InterlockedIncrement(&native_failures);
    ReleaseSRWLockExclusive(&host_imports);
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
static void host_observe_image(HMODULE handle){
    if(!handle)return;
    /* Query the OS loader before either ledger lock: an image initializer can
     * itself enter the JNI source observer while the loader lock is held. */
    HMODULE kernel=GetModuleHandleW(L"kernel32.dll");
    void *createTarget=kernel?(void*)GetProcAddress(kernel,"CreateProcessW"):NULL;
    void *resolveTarget=kernel?(void*)GetProcAddress(kernel,"GetProcAddress"):NULL;
    AcquireSRWLockExclusive(&host_imports);AcquireSRWLockShared(&native_records);
    int registered=0;
    for(NativeLibrary *library=native_libraries;library;library=library->next)
        if(host_library_current(library,handle)){registered=1;break;}
    if(!registered){ReleaseSRWLockShared(&native_records);ReleaseSRWLockExclusive(&host_imports);return;}
    HostImage *image=host_images;while(image&&(!image->live||image->handle!=handle))image=image->next;
    if(!image){image=(HostImage*)calloc(1,sizeof(*image));if(image){
        image->handle=handle;image->createTarget=createTarget;image->resolveTarget=resolveTarget;
        image->live=1;image->next=host_images;host_images=image;
    }}
    if(image)for(NativeLibrary *library=native_libraries;library;library=library->next)
        if(host_library_current(library,handle))library->hostImage=image;
    ReleaseSRWLockShared(&native_records);
    /* Loading/unloading callers invoke this outside native_records. The import
     * lock orders installation against the real library's pre-unload handoff. */
    if(image&&!image->checked)host_image_imports(image);
    if(!image)InterlockedIncrement(&native_failures);
    ReleaseSRWLockExclusive(&host_imports);
}
static void host_library_releasing(NativeLibrary *library){
    AcquireSRWLockExclusive(&host_imports);HostImage *image=library->hostImage;
    if(!image){ReleaseSRWLockExclusive(&host_imports);return;}
    AcquireSRWLockShared(&native_records);int shared=0;
    for(NativeLibrary *other=native_libraries;other;other=other->next)
        if(other!=library&&host_library_current(other,image->handle)){other->hostImage=image;shared=1;}
    ReleaseSRWLockShared(&native_records);
    host_promote_delays(image,NULL,NULL);
    /* JNI_OnUnload runs inside the real unload call. Restoring the bare API
     * before that call would reopen process creation for the stopped source.
     * Disable the last image entry first. Only its exact live unload scope may
     * still call it; other callers are refused without reading a retired IAT. */
    if(!shared&&image->live){image->delayImports=host_delay_gaps(image);image->live=0;}
    ReleaseSRWLockExclusive(&host_imports);
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
            &&__atomic_load_n(host_import_slot,__ATOMIC_ACQUIRE)==(void*)host_create_process)return 1;
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
            if(!VirtualProtect((void*)slot,sizeof(*slot),PAGE_READWRITE,&protection))return 0;
            host_original_create=(HostCreateProcess)expected;host_import_slot=slot;
            void *old=InterlockedCompareExchangePointer(slot,(void*)host_create_process,expected);
            DWORD ignored;BOOL restored=VirtualProtect((void*)slot,sizeof(*slot),protection,&ignored);
            return old==expected&&restored;
        }
    }
    return 0;
}
static int host_install(JNIEnv *env,jclass type,HMODULE image,void *target){
    if(host_ready)return host_process_type&&native_original.IsSameObject(env,type,host_process_type);
    host_process_handle=native_original.GetFieldID(env,type,"handle","J");
    host_process_gate=native_original.GetStaticMethodID(env,native_controller,"processCaller","(Ljava/lang/String;)Z");
    if(!host_process_handle||!host_process_gate)return 0;
    if(!host_process_type)host_process_type=(jclass)native_original.NewGlobalRef(env,type);
    host_original_process=(HostProcessCreate)target;
    if(!host_process_type||!host_import(image))return 0;
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
            if(!known||!native_owner_merge(&record->owners,owners))record->unknown=1;break;
        }
    ReleaseSRWLockExclusive(&host_records);native_owner_links_free(owners);
}
JNIEXPORT jstring JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_processRetire0(JNIEnv *env,jclass type,jobject process,jlong token){
    (void)type;if(!host_gate(env,"retire")||!host_identity(env,process))return NULL;
    const char *gap="HOST_PROCESS_CREATION_UNOBSERVED";char detail[128];
    AcquireSRWLockExclusive(&host_records);
    for(HostProcess *record=host_processes;record;record=record->next)
        if(record->token==(uint64_t)token&&record->identity&&native_original.IsSameObject(env,record->identity,process)){
            if(native_original.GetLongField(env,process,host_process_handle)!=(jlong)(uintptr_t)record->original)gap="HOST_PROCESS_ORIGINAL_HANDLE_CHANGED";
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
                    values[entry->kind==HOST_CREATE?11:12]++;
            }
        ReleaseSRWLockShared(&native_records);
        if(!loaded&&!current&&!resolved)continue;
        host_promote_delays(image,NULL,NULL);
        int failed=image->failed;
        for(HostImport *entry=current?image->imports:NULL;entry;entry=entry->next){
            if(!entry->slot)continue;
            if(host_controlled_entry(image,__atomic_load_n(entry->slot,__ATOMIC_ACQUIRE),entry->kind))
                values[entry->kind==HOST_CREATE?6:10]++;
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
JNIEXPORT jlongArray JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_hostState0(JNIEnv *env,jclass type,jobject module){
    (void)type;if(!native_controller||!native_original.CallStaticBooleanMethod(env,native_controller,native_control_gate)){
        native_refuse(env,"NATIVE_CONTROL_AGENT_REQUIRED");return NULL;
    }
    jlong values[13]={0};AcquireSRWLockExclusive(&host_records);host_prune(env);
    for(HostProcess *record=host_processes;record;record=record->next){
        if(!native_owner_matches(env,record->owners,module))continue;host_collect(record);
        /* Exit retires our independent handles, not the original handles
         * already returned to native code. No release/transfer observer exists. */
        if(record->nativeDirect&&record->published&&!record->failed)values[5]++;
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
    jlongArray result=native_original.NewLongArray(env,13);if(result)native_original.SetLongArrayRegion(env,result,0,13,values);return result;
}
