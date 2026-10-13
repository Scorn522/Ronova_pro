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
    if(!host_entry_sources(entry,state,caller,&owners,NULL,0)){
        DWORD error=GetLastError();native_owner_links_free(owners);SetLastError(error);return 0;
    }
    if(native_owner_stopped(owners)){
        native_owner_links_free(owners);SetLastError(ERROR_ACCESS_DENIED);return 0;
    }
    /* No source-directory lock may span DllMain or its dependency callbacks.
     * Already admitted in-flight initialization stays a real existing native
     * invocation; this entry does not claim to undo it or register an unknown
     * child image as a JDK/JNI library. */
    SetLastError(incoming);HMODULE actual=host_call_module_load(entry,arguments);DWORD error=GetLastError();
    native_owner_links_free(owners);SetLastError(error);return (uint64_t)(uintptr_t)actual;
}
