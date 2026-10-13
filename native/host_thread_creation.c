/* Included in host.c after host_consumed_entry's declaration and before the
 * shared host_native_call dispatcher. These are the public Windows thread
 * creation entry points consumed through an observed image import or a newly
 * observed GetProcAddress result. They use the same actual image incarnation
 * and current producer sources as the existing process/handle entry points.
 *
 * https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-createthread
 * https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-createremotethread
 * https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-createremotethreadex
 */
typedef HANDLE (WINAPI *HostCreateThread)(LPSECURITY_ATTRIBUTES,SIZE_T,
        LPTHREAD_START_ROUTINE,LPVOID,DWORD,LPDWORD);
typedef HANDLE (WINAPI *HostCreateRemoteThread)(HANDLE,LPSECURITY_ATTRIBUTES,SIZE_T,
        LPTHREAD_START_ROUTINE,LPVOID,DWORD,LPDWORD);
typedef HANDLE (WINAPI *HostCreateRemoteThreadEx)(HANDLE,LPSECURITY_ATTRIBUTES,SIZE_T,
        LPTHREAD_START_ROUTINE,LPVOID,DWORD,LPPROC_THREAD_ATTRIBUTE_LIST,LPDWORD);

static int host_is_thread_create(int kind){
    return kind==HOST_CREATE_THREAD||kind==HOST_CREATE_REMOTE_THREAD||kind==HOST_CREATE_REMOTE_THREAD_EX;
}
static HANDLE host_call_thread_create(HostImport *entry,const uintptr_t *arguments){
    switch(entry->kind){
        case HOST_CREATE_THREAD:
            return ((HostCreateThread)entry->original)((LPSECURITY_ATTRIBUTES)arguments[0],
                    (SIZE_T)arguments[1],(LPTHREAD_START_ROUTINE)arguments[2],(LPVOID)arguments[3],
                    (DWORD)arguments[4],(LPDWORD)arguments[5]);
        case HOST_CREATE_REMOTE_THREAD:
            return ((HostCreateRemoteThread)entry->original)((HANDLE)arguments[0],
                    (LPSECURITY_ATTRIBUTES)arguments[1],(SIZE_T)arguments[2],
                    (LPTHREAD_START_ROUTINE)arguments[3],(LPVOID)arguments[4],
                    (DWORD)arguments[5],(LPDWORD)arguments[6]);
        case HOST_CREATE_REMOTE_THREAD_EX:
            return ((HostCreateRemoteThreadEx)entry->original)((HANDLE)arguments[0],
                    (LPSECURITY_ATTRIBUTES)arguments[1],(SIZE_T)arguments[2],
                    (LPTHREAD_START_ROUTINE)arguments[3],(LPVOID)arguments[4],
                    (DWORD)arguments[5],(LPPROC_THREAD_ATTRIBUTE_LIST)arguments[6],(LPDWORD)arguments[7]);
        default:SetLastError(ERROR_INVALID_FUNCTION);return NULL;
    }
}
static uint64_t host_native_thread_create(HostImport *entry,void *caller,uintptr_t *arguments){
    DWORD incoming=GetLastError();OwnerLink *owners=NULL;
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    entry=host_consumed_entry(entry,state,caller);
    if(!host_entry_sources(entry,state,caller,&owners,NULL,0)){
        DWORD error=GetLastError();native_owner_links_free(owners);SetLastError(error);return 0;
    }
    if(native_owner_stopped(owners)){
        native_owner_links_free(owners);SetLastError(ERROR_ACCESS_DENIED);return 0;
    }
    /* Keep the actual process handle, start routine, parameter, flags and
     * attribute list unchanged. Windows owns thread start/exit semantics and
     * its original failure results; no local callback is injected remotely. */
    SetLastError(incoming);HANDLE result=host_call_thread_create(entry,arguments);DWORD error=GetLastError();
    native_owner_links_free(owners);SetLastError(error);return (uint64_t)(uintptr_t)result;
}
