/* Consumed LoadLibrary imports and genuine resolver results from an observed
 * image. Admission precedes the real loader call; paths and requested names
 * neither assign an owner nor identify a loaded module. The original loader
 * keeps dependency, initialization, reference-count and flag semantics.
 * https://learn.microsoft.com/en-us/windows/win32/api/libloaderapi/nf-libloaderapi-loadlibraryw
 * https://learn.microsoft.com/en-us/windows/win32/api/libloaderapi/nf-libloaderapi-loadlibraryexw
 */
typedef HMODULE (WINAPI *HostLoadLibraryA)(LPCSTR);
typedef HMODULE (WINAPI *HostLoadLibraryW)(LPCWSTR);
typedef HMODULE (WINAPI *HostLoadLibraryExA)(LPCSTR,HANDLE,DWORD);
typedef HMODULE (WINAPI *HostLoadLibraryExW)(LPCWSTR,HANDLE,DWORD);
typedef BOOL (WINAPI *HostFreeLibrary)(HMODULE);
typedef void (WINAPI *HostFreeLibraryAndExitThread)(HMODULE,DWORD);

/* A release frame retains execution sources through real detach callbacks.
 * Its embedded lease uses the existing source borrowers, but never enters the
 * load receipt directory and never represents an application loader reference. */
typedef struct HostModuleRelease {
    HostLibraryLoad retention;
    struct HostModuleRelease *previous,*next;
    NativeThread *thread;HostImage *callImage;HANDLE exitThread;
    int exiting,exitObserved,scopeInstalled,callbackHeld,cleanupGap;
    volatile LONG exitReady;
    DWORD exitError;
} HostModuleRelease;
static HostModuleRelease *host_module_releases;
static HostLibraryLoad *host_module_release_sources(NativeThread *state){
    return state&&state->hostRelease?&state->hostRelease->retention:NULL;
}
static void host_module_release_publish(HostModuleRelease *release){
    AcquireSRWLockExclusive(&host_imports);release->next=host_module_releases;host_module_releases=release;
    ReleaseSRWLockExclusive(&host_imports);native_thread_worker_wake();
}
static uint64_t host_native_module_release(HostImport *entry,void *caller,uintptr_t *arguments){
    DWORD incoming=GetLastError();OwnerLink *owners=NULL;int unknown=0;
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    if(state&&state->heapCallback){SetLastError(ERROR_BUSY);return FALSE;}
    /* This exact captured image contains the live control tables/trampolines.
     * An ordinary aggregate loader reference cannot authorize dismantling it.
     * Compare the original handle, without interpreting resource mapping tags. */
    HMODULE module=(HMODULE)arguments[0],control=native_control_image();
    if(control&&module==control){SetLastError(ERROR_ACCESS_DENIED);return FALSE;}
    entry=host_consumed_entry(entry,state,caller);
    if(!host_entry_sources_query(entry,state,caller,&owners,NULL,1,&unknown)){
        DWORD error=GetLastError();native_owner_links_free(owners);SetLastError(error);return FALSE;
    }
    if(native_owner_stopped(owners)){
        native_owner_links_free(owners);SetLastError(ERROR_ACCESS_DENIED);return FALSE;
    }
    HostModuleRelease *release=(HostModuleRelease*)calloc(1,sizeof(*release));
    if(!release){native_owner_links_free(owners);SetLastError(ERROR_NOT_ENOUGH_MEMORY);return FALSE;}
    HostLibraryLoad *retained=&release->retention;retained->owners=owners;retained->unknown=unknown;
    if(!host_module_keep_sources(state,retained))retained->unknown=1;
    if(native_has_producer(owners)&&(retained->unknown||!retained->leaseComplete||!native_thread_worker_backend_ready())){
        host_module_release_publish(release);SetLastError(ERROR_INVALID_DATA);return FALSE;
    }
    if(!state)state=native_thread();
    if(!state){host_module_release_publish(release);SetLastError(ERROR_NOT_ENOUGH_MEMORY);return FALSE;}
    retained->calling=1;release->previous=state->hostRelease;
    retained->scope=(NativeToolingScope){owners,retained->origin,retained->unknown,state->depth,state->tooling};
    native_thread_callback_enter(state);state->hostRelease=release;state->tooling=&retained->scope;
    host_module_release_publish(release);
    /* The original call alone consumes the application's aggregate reference.
     * Neither its BOOL nor our independent pin cleanup identifies a load receipt
     * or proves final unmapping. No directory lock spans DLL detach callbacks. */
    SetLastError(incoming);BOOL actual=((HostFreeLibrary)entry->original)(module);DWORD error=GetLastError();
    state->tooling=retained->scope.previous;state->hostRelease=release->previous;
    InterlockedExchange(&retained->calling,0);native_thread_worker_wake();native_thread_callback_leave(state);
    SetLastError(error);return (uint64_t)actual;
}
/* Restricted callbacks may borrow an existing complete lease only. None of
 * these paths enters JNI, attaches a thread, or waits for a ledger lock. */
static int host_module_exit_keep_sources(NativeThread *state,HostLibraryLoad *retained,int restricted){
    if(retained->unknown)return 0;
    if(!retained->owners){retained->leaseComplete=1;return 1;}
    if(!restricted)return host_module_keep_sources(state,retained);
    if(!state)return 0;
    HostLibraryLoad *parent=state->hostLoad;
    if(!parent)parent=host_module_release_sources(state);
    if(!parent)parent=host_thread_worker_child(state,NULL);
    if(parent&&TryAcquireSRWLockExclusive(&host_imports)){
        int ready=!parent->retired&&!parent->releasing&&!parent->unknown&&parent->leaseComplete
                &&host_module_sources_cover(parent->owners,retained->owners);
        if(ready){InterlockedIncrement(&parent->borrowers);retained->sourceParent=parent;retained->leaseComplete=1;}
        ReleaseSRWLockExclusive(&host_imports);if(ready)return 1;
    }
    if(state->hostWorker&&TryAcquireSRWLockExclusive(&host_records)){
        HostThreadWorker *worker=state->hostWorker;while(worker->sourceParent)worker=worker->sourceParent;
        int ready=!worker->closed&&!worker->releasing&&!worker->unknown
                &&(worker->sourceLease||worker->sourceAnchor||worker->sourceLoad)
                &&host_module_sources_cover(worker->owners,retained->owners);
        if(ready){worker->borrowers++;retained->sourceWorker=worker;retained->leaseComplete=1;}
        ReleaseSRWLockExclusive(&host_records);if(ready)return 1;
    }
    if(native_tooling_load_borrow(state,retained->owners,&retained->toolingAnchor))return retained->leaseComplete=1;
    return 0;
}
static void __attribute__((noreturn)) host_native_module_exit(HostImport *entry,void *caller,uintptr_t *arguments){
    DWORD incoming=GetLastError(),exitCode=(DWORD)arguments[1],error=ERROR_ACCESS_DENIED;
    HMODULE module=(HMODULE)arguments[0],control=native_control_image();
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    int restricted=state&&(state->heapCallback||state->vmDetaching||state->vmThreadEnding||state->hostUnloadImage||state->hostRelease
            ||state->hostLoad&&state->hostLoad->calling);
    if(!restricted)entry=host_consumed_entry(entry,state,caller);
    HostModuleRelease *release=(HostModuleRelease*)calloc(1,sizeof(*release));
    if(!release){SetLastError(ERROR_NOT_ENOUGH_MEMORY);host_real_exit_thread(exitCode);}
    /* Publish before acquiring any lease or OS witness. Allocation/lock failure
     * can refuse unloading, but this API must still terminate its own caller. */
    if(restricted){if(!TryAcquireSRWLockExclusive(&host_imports)){
        free(release);SetLastError(ERROR_BUSY);host_real_exit_thread(exitCode);}}
    else AcquireSRWLockExclusive(&host_imports);
    if(!state)state=native_thread();
    release->exiting=1;release->thread=state;release->retention.calling=1;release->retention.unknown=1;
    if(state){native_thread_callback_enter(state);release->callbackHeld=1;}
    int locked=1;
    if(restricted)locked=TryAcquireSRWLockShared(&native_records);
    else AcquireSRWLockShared(&native_records);
    int associated=locked?host_entry_association(entry,state,NULL,NULL):0;
    if(locked)ReleaseSRWLockShared(&native_records);
    if(associated==1){InterlockedIncrement(&entry->image->active);release->callImage=entry->image;}
    HANDLE monitor=NULL;
    if(host_real_duplicate(GetCurrentProcess(),GetCurrentThread(),GetCurrentProcess(),&monitor,SYNCHRONIZE,FALSE,0))release->exitThread=monitor;
    else release->exitError=GetLastError();
    release->next=host_module_releases;host_module_releases=release;
    ReleaseSRWLockExclusive(&host_imports);
    OwnerLink *owners=NULL;int unknown=0;
    int captured=associated==1&&state&&host_entry_sources_query(entry,state,caller,&owners,NULL,1,&unknown);
    if(!captured)error=associated==1?GetLastError():associated?ERROR_INVALID_DATA:ERROR_MOD_NOT_FOUND;
    int sourceLock=1;
    if(restricted)sourceLock=TryAcquireSRWLockExclusive(&host_imports);
    else AcquireSRWLockExclusive(&host_imports);
    if(sourceLock){release->retention.owners=owners;release->retention.unknown=!captured||unknown;owners=NULL;ReleaseSRWLockExclusive(&host_imports);}
    else{captured=0;error=ERROR_BUSY;}
    native_owner_links_free(owners);
    HostLibraryLoad *retained=&release->retention;
    int stopped=native_owner_stopped(retained->owners)!=NULL;
    int producer=native_has_producer(retained->owners);
    int kept=sourceLock&&host_module_exit_keep_sources(state,retained,restricted);
    if(!kept)retained->unknown=1;
    /* As with ordinary FreeLibrary, an accurately observed public profiler
     * need not acquire a producer's private Java lease. Its incomplete retained
     * scope stays unknown, and cannot authorize a producer continuation. */
    int allowed=captured&&!unknown&&(!producer||kept&&!retained->unknown&&retained->leaseComplete)&&release->exitThread&&state
            &&!state->heapCallback&&!stopped&&native_thread_worker_backend_ready()&&!(control&&module==control);
    if(!release->exitThread){error=release->exitError;release->cleanupGap=1;}
    else if(!state)error=ERROR_NOT_ENOUGH_MEMORY;
    else if((control&&module==control)||stopped)error=ERROR_ACCESS_DENIED;
    else if(!kept||retained->unknown)error=ERROR_INVALID_DATA;
    else if(state->heapCallback)error=ERROR_BUSY;
    else if(!native_thread_worker_backend_ready())error=ERROR_INVALID_STATE;
    if(state){
        release->previous=state->hostRelease;
        retained->scope=(NativeToolingScope){retained->owners,retained->origin,retained->unknown,state->depth,state->tooling};
        state->hostRelease=release;state->tooling=&retained->scope;release->scopeInstalled=1;
    }
    InterlockedExchange(&release->exitReady,1);native_thread_worker_wake();
    /* This call alone consumes the application's aggregate loader reference.
     * Refusal suppresses only unloading: the caller already requested its own
     * exit, with this exact code. Neither branch returns or fabricates a BOOL.
     * https://learn.microsoft.com/en-us/windows/win32/api/libloaderapi/nf-libloaderapi-freelibraryandexitthread */
    if(allowed){SetLastError(incoming);((HostFreeLibraryAndExitThread)entry->original)(module,exitCode);__builtin_unreachable();}
    SetLastError(error);host_real_exit_thread(exitCode);
}
static int host_module_exits_pending(void){
    int pending=0;AcquireSRWLockShared(&host_imports);
    for(HostModuleRelease *release=host_module_releases;release&&!pending;release=release->next)
        pending=release->exiting&&release->exitReady&&!release->cleanupGap&&!release->retention.retired;
    ReleaseSRWLockShared(&host_imports);return pending;
}
static int host_module_exit_pending(const NativeThread *state){
    int pending=0;AcquireSRWLockShared(&host_imports);
    for(HostModuleRelease *release=host_module_releases;release&&!pending;release=release->next)
        pending=release->exiting&&release->thread==state&&release->scopeInstalled&&!release->cleanupGap;
    ReleaseSRWLockShared(&host_imports);return pending;
}
static int host_module_exit_internal_handle(HANDLE value){
    /* The handle consumer holds host_imports shared before its record locks
     * and through the actual OS operation, keeping this exact witness stable. */
    int found=0;for(HostModuleRelease *release=host_module_releases;release&&!found;release=release->next)
        found=release->exitThread&&release->exitThread==value;
    return found;
}
static void host_module_releases_reap(JNIEnv **environment){
    if(!environment||!*environment)return;
    for(;;){HostModuleRelease *release=NULL;
        AcquireSRWLockExclusive(&host_imports);
        for(HostModuleRelease *candidate=host_module_releases;candidate;candidate=candidate->next){
            HostLibraryLoad *retained=&candidate->retention;
            if(retained->retired||candidate->cleanupGap)continue;
            if(candidate->exiting&&candidate->exitReady&&!candidate->exitObserved&&candidate->exitThread){
                DWORD status=WaitForSingleObject(candidate->exitThread,0);
                if(status==WAIT_OBJECT_0){candidate->exitObserved=1;InterlockedExchange(&retained->calling,0);}
                else if(status==WAIT_FAILED){candidate->exitError=GetLastError();candidate->cleanupGap=1;}
            }
            if(!retained->calling&&!retained->borrowers&&!retained->releasing){
                retained->releasing=1;release=candidate;break;}
        }
        ReleaseSRWLockExclusive(&host_imports);if(!release)break;
        /* The original may have detached its calling thread. Dispose Java refs
         * only on the existing retirement worker's current valid JNIEnv. */
        HostLibraryLoad *retained=&release->retention;JNIEnv *env=*environment;
        int unresolved=0;
        if(release->exiting&&release->scopeInstalled){
            NativeThread *state=release->thread;
            if(!state||state->hostRelease!=release||state->tooling!=&retained->scope||!release->callbackHeld||!state->callbackHolds){
                /* An abandoned intervening callback is not this call's scope.
                 * Do not erase it, dereference its stack, or pretend it ended. */
                AcquireSRWLockExclusive(&host_imports);release->cleanupGap=1;retained->releasing=0;
                ReleaseSRWLockExclusive(&host_imports);continue;
            }
            state->tooling=retained->scope.previous;state->hostRelease=release->previous;
            state->callbackHolds--;release->callbackHeld=0;release->scopeInstalled=0;
            unresolved=!native_thread_module_exit_reaped(state);if(!unresolved)release->thread=NULL;
        }else if(release->thread)unresolved=1;
        if(retained->sourceLease){native_original.DeleteGlobalRef(env,retained->sourceLease);retained->sourceLease=NULL;}
        if(retained->origin){native_original.DeleteWeakGlobalRef(env,retained->origin);retained->origin=NULL;}
        if(retained->sourceWorker){host_thread_worker_load_drop(retained->sourceWorker);retained->sourceWorker=NULL;}
        if(retained->toolingAnchor){native_thread_worker_heap_drop(retained->toolingAnchor);retained->toolingAnchor=NULL;}
        AcquireSRWLockExclusive(&host_imports);
        if(retained->sourceParent){InterlockedDecrement(&retained->sourceParent->borrowers);retained->sourceParent=NULL;}
        if(release->callImage){
            if(InterlockedDecrement(&release->callImage->active)==0&&InterlockedCompareExchange(&release->callImage->retiring,0,0))native_thread_worker_wake();
            release->callImage=NULL;
        }
        if(release->exitThread){
            if(!host_real_close(release->exitThread)){release->exitError=GetLastError();retained->releasing=0;ReleaseSRWLockExclusive(&host_imports);break;}
            release->exitThread=NULL;
        }
        if(unresolved){release->cleanupGap=1;retained->retired=1;retained->releasing=0;ReleaseSRWLockExclusive(&host_imports);continue;}
        HostModuleRelease **link=&host_module_releases;while(*link&&*link!=release)link=&(*link)->next;
        if(*link)*link=release->next;ReleaseSRWLockExclusive(&host_imports);
        native_owner_links_free(retained->owners);free(release);native_thread_worker_wake();
    }
}
static void host_module_releases_state(JNIEnv *env,jobject module,jlong *values){
    AcquireSRWLockShared(&host_imports);
    for(HostModuleRelease *release=host_module_releases;release;release=release->next)
        if(native_owner_matches(env,release->retention.owners,module))values[26]++;
    ReleaseSRWLockShared(&host_imports);
}

static int host_is_module_load(int kind){
    return kind>=HOST_LOAD_LIBRARY_A&&kind<=HOST_LOAD_LIBRARY_EX_W;
}
static HMODULE host_call_module_load(HostImport *entry,const uintptr_t *a){
    switch(entry->kind){
        case HOST_LOAD_LIBRARY_A:return ((HostLoadLibraryA)entry->original)((LPCSTR)a[0]);
        case HOST_LOAD_LIBRARY_W:return ((HostLoadLibraryW)entry->original)((LPCWSTR)a[0]);
        case HOST_LOAD_LIBRARY_EX_A:return ((HostLoadLibraryExA)entry->original)((LPCSTR)a[0],(HANDLE)a[1],(DWORD)a[2]);
        case HOST_LOAD_LIBRARY_EX_W:return ((HostLoadLibraryExW)entry->original)((LPCWSTR)a[0],(HANDLE)a[1],(DWORD)a[2]);
        default:SetLastError(ERROR_INVALID_FUNCTION);return NULL;
    }
}
static uint64_t host_native_module_load(HostImport *entry,void *caller,uintptr_t *arguments){
    DWORD incoming=GetLastError();OwnerLink *owners=NULL;
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    /* Heap callbacks cannot invoke the loader, even through a cached result. */
    if(state&&state->heapCallback){SetLastError(ERROR_BUSY);return 0;}
    entry=host_consumed_entry(entry,state,caller);
    NativeLibrary *parentLibrary=NULL;int unknown=0;
    if(!host_entry_sources_query(entry,state,caller,&owners,&parentLibrary,1,&unknown)){
        DWORD error=GetLastError();native_owner_links_free(owners);SetLastError(error);return 0;
    }
    if(native_owner_stopped(owners)){
        native_owner_links_free(owners);SetLastError(ERROR_ACCESS_DENIED);return 0;
    }
    HostLibraryLoad *load=(HostLibraryLoad*)calloc(1,sizeof(*load));
    HostImage *prepared=(HostImage*)calloc(1,sizeof(*prepared));
    if(!load||!prepared){free(load);free(prepared);native_owner_links_free(owners);SetLastError(ERROR_NOT_ENOUGH_MEMORY);return 0;}
    load->owners=owners;load->unknown=unknown;load->preparedImage=prepared;load->calling=1;
    load->flags=entry->kind==HOST_LOAD_LIBRARY_EX_A||entry->kind==HOST_LOAD_LIBRARY_EX_W?(DWORD)arguments[2]:0;
    load->parentLibrary=parentLibrary;
    OwnerLink *parentSources=NULL;
    if(!host_module_capture(state,caller,&parentSources,&load->parentImage,&load->parentLoad,&unknown))load->unknown=1;
    if(!native_owner_merge(&load->owners,parentSources))load->unknown=1;native_owner_links_free(parentSources);
    load->unknown|=unknown;
    host_module_parent(state,caller,load);
    /* Save the whole strong source set while JNI is legal, before any loader
     * callback can detach this thread. A native-only continuation borrows only
     * an actual existing complete lease; it never attaches to invent one. */
    int retained=host_module_keep_sources(state,load);
    if(!retained)load->unknown=1;
    if(native_has_producer(load->owners)&&(load->unknown||!load->leaseComplete||!native_thread_worker_backend_ready())){
        load->refused=1;host_module_publish(load);InterlockedExchange(&load->calling,0);
        native_thread_worker_wake();SetLastError(ERROR_INVALID_DATA);return 0;
    }
    if(!state)state=native_thread();
    if(!state){load->refused=1;host_module_publish(load);InterlockedExchange(&load->calling,0);
        native_thread_worker_wake();SetLastError(ERROR_NOT_ENOUGH_MEMORY);return 0;}
    load->previousScope=state->hostLoad;
    load->scope=(NativeToolingScope){load->owners,load->origin,load->unknown,state->depth,state->tooling};
    native_thread_callback_enter(state);state->hostLoad=load;state->tooling=&load->scope;host_module_publish(load);
    /* No directory lock spans DllMain/TLS. This keeps the real loader's flags,
     * return, last error and application reference. Post-return observation
     * neither intercepts the first callbacks nor creates a NativeLibrary. */
    SetLastError(incoming);HMODULE actual=host_call_module_load(entry,arguments);DWORD error=GetLastError();
    host_module_return(load,actual,error);
    state->tooling=load->scope.previous;state->hostLoad=load->previousScope;
    InterlockedExchange(&load->calling,0);native_thread_worker_wake();native_thread_callback_leave(state);
    SetLastError(error);return (uint64_t)(uintptr_t)actual;
}
