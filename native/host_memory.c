/* Actual memory-writing Windows imports/results from an observed image.
 * Admission protects the current control ranges; it does not intercept CPU
 * stores, raw syscalls, or memory APIs consumed outside the observed images.
 * https://learn.microsoft.com/en-us/windows/win32/api/handleapi/nf-handleapi-compareobjecthandles
 * https://learn.microsoft.com/en-us/windows/win32/api/memoryapi/nf-memoryapi-readprocessmemory
 * https://learn.microsoft.com/en-us/windows/win32/api/memoryapi/nf-memoryapi-writeprocessmemory
 * https://learn.microsoft.com/en-us/windows/win32/api/memoryapi/nf-memoryapi-virtualprotect
 */
typedef BOOL (WINAPI *HostWriteProcessMemory)(HANDLE,LPVOID,LPCVOID,SIZE_T,SIZE_T*);
typedef BOOL (WINAPI *HostReadProcessMemory)(HANDLE,LPCVOID,LPVOID,SIZE_T,SIZE_T*);
typedef BOOL (WINAPI *HostVirtualProtect)(LPVOID,SIZE_T,DWORD,PDWORD);
typedef BOOL (WINAPI *HostVirtualProtectEx)(HANDLE,LPVOID,SIZE_T,DWORD,PDWORD);
typedef BOOL (WINAPI *HostVirtualFree)(LPVOID,SIZE_T,DWORD);
typedef BOOL (WINAPI *HostVirtualFreeEx)(HANDLE,LPVOID,SIZE_T,DWORD);
typedef BOOL (WINAPI *HostUnmapView)(LPCVOID);
typedef BOOL (WINAPI *HostUnmapViewEx)(PVOID,ULONG);
static DWORD host_memory_page_size;
static uintptr_t host_memory_maximum;
static void host_memory_prepare(void){
    if(__atomic_load_n(&host_memory_page_size,__ATOMIC_ACQUIRE))return;
    SYSTEM_INFO system;GetSystemInfo(&system);
    if(system.dwPageSize){
        __atomic_store_n(&host_memory_maximum,(uintptr_t)system.lpMaximumApplicationAddress,__ATOMIC_RELEASE);
        __atomic_store_n(&host_memory_page_size,system.dwPageSize,__ATOMIC_RELEASE);
    }
}
static int host_is_memory_write(int kind){
    return kind>=HOST_WRITE_PROCESS_MEMORY&&kind<=HOST_VIRTUAL_PROTECT_EX;
}
static int host_is_memory_release(int kind){
    return kind>=HOST_VIRTUAL_FREE&&kind<=HOST_UNMAP_VIEW_EX;
}
/* A Host worker need not be attached to Java. Source capture never attaches
 * a thread while the loader or VM callback restricts Java transitions. */
static JNIEnv *host_memory_environment(int restricted){
    JNIEnv *env=NULL;
    if(!restricted&&!host_loader_callout()&&native_vm&&native_vm_original.GetEnv
            &&native_vm_original.GetEnv(native_vm,(void**)&env,JNI_VERSION_1_8)==JNI_OK)return env;
    return NULL;
}
static int host_memory_cleanup_scope(HostImport *entry,NativeThread *state){
    if(!entry||!entry->image||!state)return 0;
    if(state->hostUnloadImage==entry->image)return 1;
    for(NativeLibraryScope *scope=state->library;scope;scope=scope->previous)
        if(scope->unloading&&scope->phase==1&&scope->library&&scope->library->hostImage==entry->image
                &&scope->library->handle==entry->image->handle
                &&InterlockedCompareExchange(&scope->library->alive,0,0)
                &&InterlockedCompareExchange(&scope->library->closing,0,0))return 1;
    return 0;
}
/* Query one actual local write interval. Protection changes affect whole pages,
 * including a protected allocation sharing the requested page. Native's exact
 * private index includes Host allocations, thunks and currently registered IAT
 * slots; no second Host history scan or imports lock is needed here. */
static int host_memory_range_query(const void *address,SIZE_T size,int pages,int restricted,int deferPrivate){
    NativeThread *state=native_range_state();if(state)state->controlRangeFailure=NULL;
    /* A zero-sized protection request never authorizes a control page; leave
     * its actual success/failure on an ordinary page to the original API. */
    if(!size){if(!pages)return 0;size=1;}
    uintptr_t first=(uintptr_t)address,span=(uintptr_t)(size-1);
    if(span>UINTPTR_MAX-first)return native_range_unknown("ADDRESS_OVERFLOW");uintptr_t last=first+span;
    if(pages){DWORD page=__atomic_load_n(&host_memory_page_size,__ATOMIC_ACQUIRE);
        if(!page)return native_range_unknown("PAGE_SIZE_UNAVAILABLE");first-=first%page;
        uintptr_t remaining=page-1-last%page;
        last=remaining>UINTPTR_MAX-last?UINTPTR_MAX:last+remaining;
    }
    return native_control_range_query(first,last,restricted,deferPrivate);
}
static int host_memory_range(const void *address,SIZE_T size,int pages,int restricted){
    return host_memory_range_query(address,size,pages,restricted,0);
}
/* The caller holds native_private_gate. This is only the final local output
 * check: source/Host/VM directory queries must have finished before the gate. */
static int host_memory_output_locked(const void *address,SIZE_T size){
    if(!size)return 0;uintptr_t first=(uintptr_t)address,span=(uintptr_t)(size-1);
    return span>UINTPTR_MAX-first?-1:native_private_range_locked(first,first+span);
}
/* The gate also serializes private allocation/registration with actual local
 * writes and page changes. No source, Host, VM or JNI query belongs here. */
static int host_memory_range_locked(const void *address,SIZE_T size,int pages){
    if(!pages)return host_memory_output_locked(address,size);
    if(!size)size=1;
    uintptr_t first=(uintptr_t)address,span=(uintptr_t)(size-1);
    if(span>UINTPTR_MAX-first)return -1;uintptr_t last=first+span;
    DWORD page=__atomic_load_n(&host_memory_page_size,__ATOMIC_ACQUIRE);
    if(!page)return -1;first-=first%page;
    uintptr_t remaining=page-1-last%page;
    last=remaining>UINTPTR_MAX-last?UINTPTR_MAX:last+remaining;
    return native_private_range_locked(first,last);
}
static uint64_t host_native_memory_read(HostImport *entry,void *caller,uintptr_t *arguments){
    DWORD incoming=GetLastError();OwnerLink *owners=NULL;int unknown=0;
    HANDLE source=(HANDLE)arguments[0];LPCVOID address=(LPCVOID)arguments[1];
    LPVOID buffer=(LPVOID)arguments[2];SIZE_T size=(SIZE_T)arguments[3];SIZE_T *read=(SIZE_T*)arguments[4];
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    int restricted=state&&(state->heapCallback||state->toolingRestricted||state->vmDetaching||state->vmThreadEnding
            ||state->hostUnloadImage||state->hostRelease||state->hostLoad&&state->hostLoad->calling);
    if(!restricted)entry=host_consumed_entry(entry,state,caller);
    if(!host_entry_sources_query(entry,state,caller,&owners,NULL,1,&unknown)){
        DWORD error=GetLastError();native_owner_links_free(owners);SetLastError(error);return FALSE;
    }
    DWORD error=0;HANDLE process=NULL;BOOL result=FALSE;
    if(native_owner_stopped(owners))error=ERROR_ACCESS_DENIED;
    else if(unknown&&(restricted||native_has_producer(owners)))error=ERROR_INVALID_DATA;
    /* Both destinations are local even when the source process is remote.
     * A SAME_ACCESS alias fixes exactly the supplied kernel object without
     * acquiring query rights or reclassifying it through a PID. */
    if(!error&&!host_real_duplicate(GetCurrentProcess(),source,GetCurrentProcess(),&process,0,FALSE,DUPLICATE_SAME_ACCESS))
        error=GetLastError();
    int hit=0;
    if(!error)hit=host_memory_range(buffer,size,0,restricted);
    if(!error&&!hit&&read)hit=host_memory_range(read,sizeof(*read),0,restricted);
    if(!error&&hit)error=hit>0?ERROR_ACCESS_DENIED:restricted?ERROR_BUSY:ERROR_INVALID_DATA;
    if(!error){
        int locked=native_private_read_lock(restricted);
        if(!locked)error=ERROR_BUSY;
        else __try {
            hit=host_memory_output_locked(buffer,size);
            if(!hit&&read)hit=host_memory_output_locked(read,sizeof(*read));
            if(hit)error=hit>0?ERROR_ACCESS_DENIED:restricted?ERROR_BUSY:ERROR_INVALID_DATA;
            else if(native_owner_stopped(owners))error=ERROR_ACCESS_DENIED;
            else {
                /* Unlike a VM query, the real Windows copy can execute inside
                 * this allocation/publication gate. Do not stage either output:
                 * a failed read can have real partial effects, and NULL, zero
                 * length and overlapping outputs keep their original contract. */
                SetLastError(incoming);
                result=((HostReadProcessMemory)entry->original)(process,address,buffer,size,read);
                error=GetLastError();
            }
        } __finally {ReleaseSRWLockShared(&native_private_gate);}
    }
    if(process&&!host_real_close(process))InterlockedIncrement(&native_failures);
    native_owner_links_free(owners);SetLastError(error);return (uint64_t)result;
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
    int restricted=state&&(state->heapCallback||state->toolingRestricted||state->vmDetaching||state->vmThreadEnding||state->hostUnloadImage||state->hostRelease
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
    const void *address=(const void*)arguments[entry->kind==HOST_VIRTUAL_PROTECT?0:1];
    SIZE_T size=(SIZE_T)arguments[entry->kind==HOST_WRITE_PROCESS_MEMORY?3:entry->kind==HOST_VIRTUAL_PROTECT?1:2];
    const void *output=(const void*)arguments[entry->kind==HOST_VIRTUAL_PROTECT?3:4];
    SIZE_T outputSize=entry->kind==HOST_WRITE_PROCESS_MEMORY?sizeof(SIZE_T):sizeof(DWORD);
    int hit=0;
    if(!error&&local){
        hit=host_memory_range(address,size,entry->kind!=HOST_WRITE_PROCESS_MEMORY,restricted);
    }
    /* Outputs always belong to this process, even when the target is remote.
     * Refusing a write leaves both output bytes and requested page state alone. */
    if(!error&&!hit){
        if(output)hit=host_memory_range(output,outputSize,0,restricted);
    }
    if(!error&&hit)error=hit>0?ERROR_ACCESS_DENIED:restricted?ERROR_BUSY:ERROR_INVALID_DATA;
    if(!error&&(local||output)){
        int locked=native_private_read_lock(restricted);
        if(!locked)error=ERROR_BUSY;
        else __try {
            hit=local?host_memory_range_locked(address,size,entry->kind!=HOST_WRITE_PROCESS_MEMORY):0;
            if(!hit&&output)hit=host_memory_output_locked(output,outputSize);
            if(hit)error=hit>0?ERROR_ACCESS_DENIED:restricted?ERROR_BUSY:ERROR_INVALID_DATA;
            else if(native_owner_stopped(owners))error=ERROR_ACCESS_DENIED;
            else {
                /* Consume the genuine Windows entry while private allocation
                 * and publication are excluded. Preserve its one-call partial
                 * effects and optional output contract; no staging or preclear. */
                SetLastError(incoming);result=host_memory_original(entry,process,arguments);error=GetLastError();
            }
        } __finally {ReleaseSRWLockShared(&native_private_gate);}
    }else if(!error){
        /* A remote target with no local output has no private local effect. */
        if(native_owner_stopped(owners))error=ERROR_ACCESS_DENIED;
        else {SetLastError(incoming);result=host_memory_original(entry,process,arguments);error=GetLastError();}
    }
    /* Capture the original API's error before releasing only our alias. No
     * caller-owned handle is closed, and no output is synthesized on refusal. */
    if(process&&!host_real_close(process))InterlockedIncrement(&native_failures);
    native_owner_links_free(owners);SetLastError(error);return (uint64_t)result;
}

/* RegionSize covers one attribute segment, not the whole reservation/view.
 * Starting from the actual AllocationBase, walk only that current allocation.
 * No path, module name, allocation directory, or caller-supplied length defines
 * its boundary. A free page has no defined AllocationBase and is not traversed.
 * https://learn.microsoft.com/en-us/windows/win32/api/memoryapi/nf-memoryapi-virtualquery
 * https://learn.microsoft.com/en-us/windows/win32/api/winnt/ns-winnt-memory_basic_information
 */
/* Resolve only Win32 allocation geometry. Both the domain preflight and the
 * final gate-held check use this routine; it never enters another directory. */
static int host_memory_allocation_span(const void *address,const void **allocation,SIZE_T *size,int *pages){
    MEMORY_BASIC_INFORMATION initial;
    if(VirtualQuery(address,&initial,sizeof(initial))!=sizeof(initial))return -1;
    if(initial.State==MEM_FREE){*allocation=address;*size=1;*pages=1;return 0;}
    uintptr_t requested=(uintptr_t)address,segment=(uintptr_t)initial.BaseAddress,base=(uintptr_t)initial.AllocationBase;
    if(!base||base>segment||segment>requested||!initial.RegionSize||requested-segment>=initial.RegionSize)return -1;
    uintptr_t maximum=__atomic_load_n(&host_memory_maximum,__ATOMIC_ACQUIRE),cursor=base;
    if(!maximum||requested>maximum)return -1;
    for(;;){
        if(cursor>maximum)break;
        MEMORY_BASIC_INFORMATION current;
        if(VirtualQuery((const void*)cursor,&current,sizeof(current))!=sizeof(current))return -1;
        if(current.State==MEM_FREE||(uintptr_t)current.AllocationBase!=base)break;
        if((current.State!=MEM_COMMIT&&current.State!=MEM_RESERVE)||(uintptr_t)current.BaseAddress!=cursor
                ||!current.RegionSize||current.RegionSize>UINTPTR_MAX-cursor)return -1;
        cursor+=(uintptr_t)current.RegionSize;
    }
    if(cursor<=requested||cursor<=base)return -1;
    *allocation=(const void*)base;*size=(SIZE_T)(cursor-base);*pages=0;return 0;
}
static int host_memory_allocation_preflight(const void *address,int restricted){
    const void *allocation;SIZE_T size;int pages;
    if(host_memory_allocation_span(address,&allocation,&size,&pages))return -1;
    return host_memory_range_query(allocation,size,pages,restricted,1);
}
static int host_memory_allocation_locked(const void *address){
    const void *allocation;SIZE_T size;int pages;
    if(host_memory_allocation_span(address,&allocation,&size,&pages))return -1;
    return host_memory_range_locked(allocation,size,pages);
}
/* Only the release path uses this deferred preflight. Before the real local
 * release it must resolve the affected current span again, check every private
 * byte, and keep the shared gate through the original API. No private verdict
 * from this preflight is retained or used to authorize that final effect. */
static int host_memory_release_preflight(HostImport *entry,const uintptr_t *arguments,int restricted){
    const void *address=(const void*)arguments[entry->kind==HOST_VIRTUAL_FREE_EX?1:0];
    if(entry->kind==HOST_UNMAP_VIEW||entry->kind==HOST_UNMAP_VIEW_EX)
        return host_memory_allocation_preflight(address,restricted);
    SIZE_T size=(SIZE_T)arguments[entry->kind==HOST_VIRTUAL_FREE_EX?2:1];
    DWORD type=(DWORD)arguments[entry->kind==HOST_VIRTUAL_FREE_EX?3:2];
    if(type==MEM_DECOMMIT&&size)return host_memory_range_query(address,size,1,restricted,1);
    /* RELEASE(base,0), DECOMMIT(base,0), and unmapping can remove an entire
     * reservation/view containing differently protected segments. Never infer
     * that a non-base or zero-sized request must fail and so needs no guard.
     * Placeholder coalesce/split flags have real nonzero-size contracts: check
     * that complete explicit interval as well, retaining every original flag.
     * https://learn.microsoft.com/en-us/windows/win32/api/memoryapi/nf-memoryapi-virtualfree
     * https://learn.microsoft.com/en-us/windows/win32/api/memoryapi/nf-memoryapi-unmapviewoffileex
     */
    int hit=host_memory_allocation_preflight(address,restricted);
    return hit||!size?hit:host_memory_range_query(address,size,1,restricted,1);
}
/* Repeat the affected current reservation/view and explicit interval under
 * the private gate. Reusing the preflight here would take domain locks. */
static int host_memory_release_locked(HostImport *entry,const uintptr_t *arguments){
    const void *address=(const void*)arguments[entry->kind==HOST_VIRTUAL_FREE_EX?1:0];
    if(entry->kind==HOST_UNMAP_VIEW||entry->kind==HOST_UNMAP_VIEW_EX)
        return host_memory_allocation_locked(address);
    SIZE_T size=(SIZE_T)arguments[entry->kind==HOST_VIRTUAL_FREE_EX?2:1];
    DWORD type=(DWORD)arguments[entry->kind==HOST_VIRTUAL_FREE_EX?3:2];
    if(type==MEM_DECOMMIT&&size)return host_memory_range_locked(address,size,1);
    int hit=host_memory_allocation_locked(address);
    return hit||!size?hit:host_memory_range_locked(address,size,1);
}
static BOOL host_memory_release_original(HostImport *entry,HANDLE process,const uintptr_t *a){
    switch(entry->kind){
        case HOST_VIRTUAL_FREE:return ((HostVirtualFree)entry->original)((LPVOID)a[0],(SIZE_T)a[1],(DWORD)a[2]);
        case HOST_VIRTUAL_FREE_EX:return ((HostVirtualFreeEx)entry->original)(process,(LPVOID)a[1],(SIZE_T)a[2],(DWORD)a[3]);
        case HOST_UNMAP_VIEW:return ((HostUnmapView)entry->original)((LPCVOID)a[0]);
        case HOST_UNMAP_VIEW_EX:return ((HostUnmapViewEx)entry->original)((PVOID)a[0],(ULONG)a[1]);
        default:SetLastError(ERROR_INVALID_FUNCTION);return FALSE;
    }
}
/* A late dump can outlive the failing loader/FLS callback. Record only the
 * actual BUSY failure, after releasing our gates, with the original result
 * and arguments. This does not retry, infer ownership or change admission. */
static void host_memory_release_busy(HostImport *entry,void *caller,const uintptr_t *arguments,
        NativeThread *state,int restricted,const char *site,const char *range,int returned,uint64_t writer){
    static volatile LONG reports;ULONG count=(ULONG)InterlockedIncrement(&reports);
    if(count>32&&(count&(count-1)))return;
    unsigned address=entry->kind==HOST_VIRTUAL_FREE_EX?1:0;
    int unmap=entry->kind==HOST_UNMAP_VIEW||entry->kind==HOST_UNMAP_VIEW_EX;
    SIZE_T bytes=unmap?0:(SIZE_T)arguments[address+1];
    DWORD flags=entry->kind==HOST_UNMAP_VIEW?0:entry->kind==HOST_UNMAP_VIEW_EX?(DWORD)arguments[1]:(DWORD)arguments[address+2];
    DWORD incoming=GetLastError();int crt=errno;unsigned long dos=_doserrno;
    /* Sampled immediately after the failed shared acquisition, not now after
     * source cleanup. A zero sample or later writer cannot identify the thread
     * that caused contention; the log deliberately calls this a sample. */
    unsigned long writerBytes=(unsigned long)((writer>>32)&0xffffff);
    fprintf(stderr,"NATIVE_MEMORY_RELEASE_BUSY:kind=%d:site=%s:range=%s:thread=%lu:caller=%p:image=%p:address=%p:bytes=%llu:flags=%lu:restricted=%d:original_called=%d:detaching=%u:thread_ending=%u:tooling=%u:heap=%u:loader=%d:writer_sample_thread=%lu:writer_sample_site=%s:writer_sample_bytes=%lu:writer_sample_bytes_capped=%d\n",
            entry->kind,site,range?range:"NONE",(unsigned long)GetCurrentThreadId(),caller,entry->image->handle,
            (void*)arguments[address],(unsigned long long)bytes,(unsigned long)flags,restricted,returned,
            state?state->vmDetaching:0,state?state->vmThreadEnding:0,state?state->toolingRestricted:0,state?state->heapCallback:0,host_loader_callout(),
            (unsigned long)(uint32_t)writer,native_private_writer_site(writer),writerBytes,writerBytes==0xffffff);
    _doserrno=dos;errno=crt;SetLastError(incoming);
}
static uint64_t host_native_memory_release(HostImport *entry,void *caller,uintptr_t *arguments){
    DWORD incoming=GetLastError();OwnerLink *owners=NULL;int unknown=0;
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    int restricted=host_loader_callout()||(state&&(state->heapCallback||state->toolingRestricted||state->vmDetaching
            ||state->vmThreadEnding||state->hostUnloadImage||state->hostRelease||state->hostLoad&&state->hostLoad->calling));
    if(!restricted)entry=host_consumed_entry(entry,state,caller);
    if(!host_entry_sources_query(entry,state,caller,&owners,NULL,1,&unknown)){
        DWORD error=GetLastError();native_owner_links_free(owners);
        if(error==ERROR_BUSY)host_memory_release_busy(entry,caller,arguments,state,restricted,"sources",NULL,0,0);
        SetLastError(error);return FALSE;
    }
    DWORD error=0;HANDLE process=NULL;BOOL result=FALSE;int returned=0;
    const char *failure="policy",*rangeFailure=NULL;uint64_t privateWriter=0;
    int local=entry->kind!=HOST_VIRTUAL_FREE_EX;
    /* Only the genuine current unload body may release ordinary resources of
     * its stopped image. This relation grants no control-memory permission. */
    int cleanup=host_memory_cleanup_scope(entry,state)&&entry->image&&native_image(caller)==entry->image->handle;
    __try {
        if(native_owner_stopped(owners)&&!cleanup)error=ERROR_ACCESS_DENIED;
        else if(unknown&&(restricted||native_has_producer(owners)))error=ERROR_INVALID_DATA;
        if(!error&&!local){
            failure="process_handle";
            HostCompareHandles compare=__atomic_load_n(&host_compare_handles,__ATOMIC_ACQUIRE);
            if(!compare&&!restricted&&host_handle_identity_ready())compare=__atomic_load_n(&host_compare_handles,__ATOMIC_ACQUIRE);
            if(!compare)error=ERROR_NOT_SUPPORTED;
            else if(!host_real_duplicate(GetCurrentProcess(),(HANDLE)arguments[0],GetCurrentProcess(),&process,0,FALSE,DUPLICATE_SAME_ACCESS))error=GetLastError();
            else local=compare(process,GetCurrentProcess())!=0;
        }
        if(!error)failure="control_range";
        int hit=!error&&local?host_memory_release_preflight(entry,arguments,restricted):0;
        if(hit<0&&state)rangeFailure=state->controlRangeFailure;
        if(!error&&hit)error=hit>0?ERROR_ACCESS_DENIED:restricted?ERROR_BUSY:ERROR_INVALID_DATA;
        if(!error&&local){
            failure="private_gate";
            int locked=native_private_read_lock(restricted);
            if(!locked){error=ERROR_BUSY;privateWriter=state?state->privateRangeWriter:0;}
            else __try {
                failure="final_range";
                hit=host_memory_release_locked(entry,arguments);
                if(hit)error=hit>0?ERROR_ACCESS_DENIED:restricted?ERROR_BUSY:ERROR_INVALID_DATA;
                else if(native_owner_stopped(owners)&&!(cleanup&&host_memory_cleanup_scope(entry,state)))error=ERROR_ACCESS_DENIED;
                else {failure="original_api";SetLastError(incoming);result=host_memory_release_original(entry,process,arguments);error=GetLastError();returned=1;}
            } __finally {ReleaseSRWLockShared(&native_private_gate);}
        }else if(!error){
            if(native_owner_stopped(owners))error=ERROR_ACCESS_DENIED;
            else {failure="original_api";SetLastError(incoming);result=host_memory_release_original(entry,process,arguments);error=GetLastError();returned=1;}
        }
    } __finally {
        DWORD interrupted=GetLastError();
        if(process&&!host_real_close(process))InterlockedIncrement(&native_failures);
        native_owner_links_free(owners);
        if(!result&&error==ERROR_BUSY)host_memory_release_busy(entry,caller,arguments,state,restricted,failure,rangeFailure,returned,privateWriter);
        SetLastError(returned||error?error:interrupted);
    }
    return (uint64_t)result;
}
