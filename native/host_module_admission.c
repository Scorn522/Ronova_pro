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
