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

/* A release frame retains execution sources through real detach callbacks.
 * Its embedded lease uses the existing source borrowers, but never enters the
 * load receipt directory and never represents an application loader reference. */
typedef struct HostModuleRelease {
    HostLibraryLoad retention;
    struct HostModuleRelease *previous,*next;
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
     * or proves final unmapping. No directory lock spans DLL detach callbacks.
     * FreeLibraryAndExitThread remains outside this returning entry contract. */
    SetLastError(incoming);BOOL actual=((HostFreeLibrary)entry->original)(module);DWORD error=GetLastError();
    state->tooling=retained->scope.previous;state->hostRelease=release->previous;
    InterlockedExchange(&retained->calling,0);native_thread_worker_wake();native_thread_callback_leave(state);
    SetLastError(error);return (uint64_t)actual;
}
static void host_module_releases_reap(JNIEnv **environment){
    if(!environment||!*environment)return;
    for(;;){HostModuleRelease *release=NULL;
        AcquireSRWLockExclusive(&host_imports);
        for(HostModuleRelease *candidate=host_module_releases;candidate;candidate=candidate->next){
            HostLibraryLoad *retained=&candidate->retention;
            if(!retained->calling&&!retained->borrowers&&!retained->releasing){
                retained->releasing=1;release=candidate;break;}
        }
        ReleaseSRWLockExclusive(&host_imports);if(!release)break;
        /* The original may have detached its calling thread. Dispose Java refs
         * only on the existing retirement worker's current valid JNIEnv. */
        HostLibraryLoad *retained=&release->retention;JNIEnv *env=*environment;
        if(retained->sourceLease)native_original.DeleteGlobalRef(env,retained->sourceLease);
        if(retained->origin)native_original.DeleteWeakGlobalRef(env,retained->origin);
        if(retained->sourceWorker)host_thread_worker_load_drop(retained->sourceWorker);
        if(retained->toolingAnchor)native_thread_worker_heap_drop(retained->toolingAnchor);
        AcquireSRWLockExclusive(&host_imports);
        if(retained->sourceParent)InterlockedDecrement(&retained->sourceParent->borrowers);
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
