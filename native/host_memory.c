/* Actual memory-writing Windows imports/results from an observed image.
 * Admission protects the current control ranges; it does not intercept CPU
 * stores, raw syscalls, or memory APIs consumed outside the observed images.
 * https://learn.microsoft.com/en-us/windows/win32/api/handleapi/nf-handleapi-compareobjecthandles
 * https://learn.microsoft.com/en-us/windows/win32/api/memoryapi/nf-memoryapi-writeprocessmemory
 * https://learn.microsoft.com/en-us/windows/win32/api/memoryapi/nf-memoryapi-virtualprotect
 */
typedef BOOL (WINAPI *HostWriteProcessMemory)(HANDLE,LPVOID,LPCVOID,SIZE_T,SIZE_T*);
typedef BOOL (WINAPI *HostVirtualProtect)(LPVOID,SIZE_T,DWORD,PDWORD);
typedef BOOL (WINAPI *HostVirtualProtectEx)(HANDLE,LPVOID,SIZE_T,DWORD,PDWORD);
static DWORD host_memory_page_size;
static void host_memory_prepare(void){
    if(__atomic_load_n(&host_memory_page_size,__ATOMIC_ACQUIRE))return;
    SYSTEM_INFO system;GetSystemInfo(&system);
    if(system.dwPageSize)__atomic_store_n(&host_memory_page_size,system.dwPageSize,__ATOMIC_RELEASE);
}
static int host_is_memory_write(int kind){
    return kind>=HOST_WRITE_PROCESS_MEMORY&&kind<=HOST_VIRTUAL_PROTECT_EX;
}
static int host_memory_overlaps(uintptr_t first,uintptr_t last,const void *allocation,SIZE_T size){
    if(!allocation||!size)return 0;uintptr_t base=(uintptr_t)allocation;
    uintptr_t span=(uintptr_t)(size-1),end=span>UINTPTR_MAX-base?UINTPTR_MAX:base+span;
    return first<=end&&base<=last;
}
/* Only traverse source chains owned by a currently published Host record.
 * The directory lock also covers removal before a release frame frees its
 * owners; temporary admission/query chains are not part of this directory. */
static int host_memory_source_range(uintptr_t first,uintptr_t last,OwnerLink *sources){
    for(;sources;sources=sources->next)
        if(host_memory_overlaps(first,last,sources,sizeof(*sources)))return 1;
    return 0;
}
static int host_memory_import_range(uintptr_t first,uintptr_t last,int restricted){
    if(restricted){if(!TryAcquireSRWLockShared(&host_imports))return -1;}
    else AcquireSRWLockShared(&host_imports);
    int hit=0;
    for(HostImage *image=host_images;image&&!hit;image=image->next){
        hit=host_memory_overlaps(first,last,image,sizeof(*image));
        for(HostImport *entry=image->imports;entry&&!hit;entry=entry->next){
            hit=host_memory_overlaps(first,last,entry,sizeof(*entry))
                    ||host_memory_overlaps(first,last,entry->entry,4096)
                    ||host_memory_source_range(first,last,entry->sources)
                    ||image->live&&!image->releasing&&host_memory_overlaps(first,last,(void*)entry->slot,sizeof(*entry->slot));
        }
    }
    for(HostLibraryLoad *load=host_library_loads;load&&!hit;load=load->next)
        hit=host_memory_overlaps(first,last,load,sizeof(*load))||host_memory_source_range(first,last,load->owners);
    for(HostModuleRelease *release=host_module_releases;release&&!hit;release=release->next)
        hit=host_memory_overlaps(first,last,release,sizeof(*release))
                ||host_memory_source_range(first,last,release->retention.owners);
    ReleaseSRWLockShared(&host_imports);return hit;
}
/* Query one actual local write interval. Protection changes affect whole pages,
 * including a protected allocation sharing the requested page. Query each
 * directory separately; no Host lock spans the Native control-range query. */
static int host_memory_range(const void *address,SIZE_T size,int pages,int restricted){
    /* A zero-sized protection request never authorizes a control page; leave
     * its actual success/failure on an ordinary page to the original API. */
    if(!size){if(!pages)return 0;size=1;}
    uintptr_t first=(uintptr_t)address,span=(uintptr_t)(size-1);
    if(span>UINTPTR_MAX-first)return -1;uintptr_t last=first+span;
    if(pages){DWORD page=__atomic_load_n(&host_memory_page_size,__ATOMIC_ACQUIRE);
        if(!page)return -1;first-=first%page;
        uintptr_t remaining=page-1-last%page;
        last=remaining>UINTPTR_MAX-last?UINTPTR_MAX:last+remaining;
    }
    int hit=native_control_range(first,last,restricted);
    return hit?hit:host_memory_import_range(first,last,restricted);
}
static BOOL host_memory_original(HostImport *entry,HANDLE process,const uintptr_t *a){
    switch(entry->kind){
        case HOST_WRITE_PROCESS_MEMORY:return ((HostWriteProcessMemory)entry->original)(process,(LPVOID)a[1],(LPCVOID)a[2],(SIZE_T)a[3],(SIZE_T*)a[4]);
        case HOST_VIRTUAL_PROTECT:return ((HostVirtualProtect)entry->original)((LPVOID)a[0],(SIZE_T)a[1],(DWORD)a[2],(PDWORD)a[3]);
        case HOST_VIRTUAL_PROTECT_EX:return ((HostVirtualProtectEx)entry->original)(process,(LPVOID)a[1],(SIZE_T)a[2],(DWORD)a[3],(PDWORD)a[4]);
        default:SetLastError(ERROR_INVALID_FUNCTION);return FALSE;
    }
}
static uint64_t host_native_memory(HostImport *entry,void *caller,uintptr_t *arguments){
    DWORD incoming=GetLastError();OwnerLink *owners=NULL;int unknown=0;
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    int restricted=state&&(state->heapCallback||state->vmDetaching||state->vmThreadEnding||state->hostUnloadImage||state->hostRelease
            ||state->hostLoad&&state->hostLoad->calling);
    /* Delay-slot promotion takes a blocking directory lock and inspects PE
     * code. A restricted callback consumes its genuine saved entry directly;
     * the callback source query below still validates the current invocation. */
    if(!restricted)entry=host_consumed_entry(entry,state,caller);
    if(!host_entry_sources_query(entry,state,caller,&owners,NULL,1,&unknown)){
        DWORD error=GetLastError();native_owner_links_free(owners);SetLastError(error);return FALSE;
    }
    DWORD error=0;HANDLE process=NULL;BOOL result=FALSE;
    if(native_owner_stopped(owners))error=ERROR_ACCESS_DENIED;
    else if(unknown&&(restricted||native_has_producer(owners)))error=ERROR_INVALID_DATA;
    int local=entry->kind==HOST_VIRTUAL_PROTECT;
    if(!error&&!local){
        /* Pin the kernel object before comparison and consumption. The caller's
         * handle can close/reuse concurrently without redirecting this call.
         * The private alias retains exactly the original handle's access. */
        HostCompareHandles compare=__atomic_load_n(&host_compare_handles,__ATOMIC_ACQUIRE);
        if(!compare&&!restricted&&host_handle_identity_ready())
            compare=__atomic_load_n(&host_compare_handles,__ATOMIC_ACQUIRE);
        if(!compare)error=ERROR_NOT_SUPPORTED;
        else if(!host_real_duplicate(GetCurrentProcess(),(HANDLE)arguments[0],GetCurrentProcess(),&process,0,FALSE,DUPLICATE_SAME_ACCESS))error=GetLastError();
        else local=compare(process,GetCurrentProcess())!=0;
    }
    int hit=0;
    if(!error&&local){
        const void *address=(const void*)arguments[entry->kind==HOST_VIRTUAL_PROTECT?0:1];
        SIZE_T size=(SIZE_T)arguments[entry->kind==HOST_WRITE_PROCESS_MEMORY?3:entry->kind==HOST_VIRTUAL_PROTECT?1:2];
        hit=host_memory_range(address,size,entry->kind!=HOST_WRITE_PROCESS_MEMORY,restricted);
    }
    /* Outputs always belong to this process, even when the target is remote.
     * Refusing a write leaves both output bytes and requested page state alone. */
    if(!error&&!hit){
        const void *output=(const void*)arguments[entry->kind==HOST_VIRTUAL_PROTECT?3:4];
        if(output)hit=host_memory_range(output,entry->kind==HOST_WRITE_PROCESS_MEMORY?sizeof(SIZE_T):sizeof(DWORD),0,restricted);
    }
    if(!error&&hit)error=hit>0?ERROR_ACCESS_DENIED:restricted?ERROR_BUSY:ERROR_INVALID_DATA;
    if(!error&&native_owner_stopped(owners))error=ERROR_ACCESS_DENIED;
    if(!error){SetLastError(incoming);result=host_memory_original(entry,process,arguments);error=GetLastError();}
    /* Capture the original API's error before releasing only our alias. No
     * caller-owned handle is closed, and no output is synthesized on refusal. */
    if(process&&!host_real_close(process))InterlockedIncrement(&native_failures);
    native_owner_links_free(owners);SetLastError(error);return (uint64_t)result;
}
