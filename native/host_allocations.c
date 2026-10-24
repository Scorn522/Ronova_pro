/* Only exact Windows exports consumed by an observed image reach this path.
 * Allocation geometry comes from the real return, never a caller's guessed
 * heap extent. Every original API is invoked once with its original flags.
 */
typedef LPVOID (WINAPI *HostHeapAlloc)(HANDLE,DWORD,SIZE_T);
typedef LPVOID (WINAPI *HostHeapReAlloc)(HANDLE,DWORD,LPVOID,SIZE_T);
typedef BOOL (WINAPI *HostHeapFree)(HANDLE,DWORD,LPVOID);
typedef BOOL (WINAPI *HostHeapDestroy)(HANDLE);
typedef HANDLE (WINAPI *HostHeapCreate)(DWORD,SIZE_T,SIZE_T);
typedef LPVOID (WINAPI *HostVirtualAlloc)(LPVOID,SIZE_T,DWORD,DWORD);
typedef LPVOID (WINAPI *HostVirtualAllocEx)(HANDLE,LPVOID,SIZE_T,DWORD,DWORD);

static int host_is_allocation(int kind){
    return kind>=HOST_HEAP_ALLOC&&kind<=HOST_VIRTUAL_ALLOC_EX||kind==HOST_HEAP_DESTROY||kind==HOST_HEAP_CREATE;
}
static uint64_t host_allocation_original(HostImport *entry,HANDLE process,const uintptr_t *a){
    switch(entry->kind){
        case HOST_HEAP_ALLOC:return (uintptr_t)((HostHeapAlloc)entry->original)((HANDLE)a[0],(DWORD)a[1],(SIZE_T)a[2]);
        case HOST_HEAP_REALLOC:return (uintptr_t)((HostHeapReAlloc)entry->original)((HANDLE)a[0],(DWORD)a[1],(LPVOID)a[2],(SIZE_T)a[3]);
        case HOST_HEAP_FREE:return ((HostHeapFree)entry->original)((HANDLE)a[0],(DWORD)a[1],(LPVOID)a[2]);
        case HOST_HEAP_DESTROY:return ((HostHeapDestroy)entry->original)((HANDLE)a[0]);
        case HOST_HEAP_CREATE:return (uintptr_t)((HostHeapCreate)entry->original)((DWORD)a[0],(SIZE_T)a[1],(SIZE_T)a[2]);
        case HOST_VIRTUAL_ALLOC:return (uintptr_t)((HostVirtualAlloc)entry->original)((LPVOID)a[0],(SIZE_T)a[1],(DWORD)a[2],(DWORD)a[3]);
        case HOST_VIRTUAL_ALLOC_EX:return (uintptr_t)((HostVirtualAllocEx)entry->original)(process,(LPVOID)a[1],(SIZE_T)a[2],(DWORD)a[3],(DWORD)a[4]);
        default:SetLastError(ERROR_INVALID_FUNCTION);return 0;
    }
}
static uint64_t host_native_allocation(HostImport *entry,void *caller,uintptr_t *arguments){
    DWORD incoming=GetLastError();OwnerLink *owners=NULL;int unknown=0;
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    int restricted=state&&(state->heapCallback||state->toolingRestricted||state->vmDetaching||state->vmThreadEnding
            ||state->hostUnloadImage||state->hostRelease||state->hostLoad&&state->hostLoad->calling);
    if(!restricted)entry=host_consumed_entry(entry,state,caller);
    if(!host_entry_sources_query(entry,state,caller,&owners,NULL,1,&unknown)){
        DWORD error=GetLastError();native_owner_links_free(owners);SetLastError(error);return 0;
    }
    JNIEnv *env=host_memory_environment(restricted);NativeHostMemoryCall *tracking=NULL;
    NativeHostArenaOriginalScope *arena=host_memory_arena_scope(entry,state,caller);
    HANDLE process=NULL;DWORD error=0;uint64_t result=0;int returned=0,denied=0,customBacking=0;
    int heap=entry->kind==HOST_HEAP_ALLOC||entry->kind==HOST_HEAP_REALLOC
            ||entry->kind==HOST_HEAP_FREE||entry->kind==HOST_HEAP_DESTROY||entry->kind==HOST_HEAP_CREATE;
    int cleanup=entry->kind==HOST_HEAP_FREE&&host_memory_cleanup_scope(entry,state);
    int local=entry->kind!=HOST_VIRTUAL_ALLOC_EX,guard=0;
    unsigned first=entry->kind==HOST_VIRTUAL_ALLOC_EX?1:0;
    void *release=heap?host_real_memory_target(HOST_HEAP_FREE,"HeapFree"):
            host_real_memory_target(HOST_VIRTUAL_FREE,"VirtualFree");
    const void *address=NULL;SIZE_T bytes=0;
    if(entry->kind==HOST_HEAP_ALLOC)bytes=(SIZE_T)arguments[2];
    else if(entry->kind==HOST_HEAP_REALLOC||entry->kind==HOST_HEAP_FREE){
        address=(const void*)arguments[2];if(entry->kind==HOST_HEAP_REALLOC)bytes=(SIZE_T)arguments[3];
    }else if(!heap){address=(const void*)arguments[first];bytes=(SIZE_T)arguments[first+1];}
    __try {
        if(native_owner_stopped(owners)&&!cleanup)error=ERROR_ACCESS_DENIED;
        else if(unknown&&(restricted||native_has_producer(owners))&&!cleanup)error=ERROR_INVALID_DATA;
        if(!error&&!local){
            HostCompareHandles compare=__atomic_load_n(&host_compare_handles,__ATOMIC_ACQUIRE);
            if(!compare&&!restricted&&host_handle_identity_ready())compare=__atomic_load_n(&host_compare_handles,__ATOMIC_ACQUIRE);
            if(!compare)error=ERROR_NOT_SUPPORTED;
            else if(!host_real_duplicate(GetCurrentProcess(),(HANDLE)arguments[0],GetCurrentProcess(),&process,0,FALSE,DUPLICATE_SAME_ACCESS))error=GetLastError();
            else local=compare(process,GetCurrentProcess())!=0;
        }
        if(!error&&entry->kind==HOST_HEAP_DESTROY){
            HANDLE privateHeap=native_private_crt_heap();
            if(privateHeap&&(HANDLE)arguments[0]==privateHeap)error=ERROR_ACCESS_DENIED;
            else if(!privateHeap&&native_has_producer(owners))error=ERROR_INVALID_DATA;
        }
        /* A normal heap allocation has no input payload. Realloc/free can
         * consume a control address; a nonnull VirtualAlloc request may change
         * existing committed pages instead of creating a reservation. */
        if(!error&&local&&address&&(entry->kind==HOST_HEAP_REALLOC||entry->kind==HOST_HEAP_FREE||!heap)){
            guard=1;int hit=host_memory_range(address,heap?1:bytes,!heap,restricted);
            if(hit)error=hit>0?ERROR_ACCESS_DENIED:restricted?ERROR_BUSY:ERROR_INVALID_DATA;
        }
        if(!error&&heap){
            int operation=entry->kind==HOST_HEAP_ALLOC?NATIVE_MEMORY_HOST_ALLOCATE:
                    entry->kind==HOST_HEAP_REALLOC?NATIVE_MEMORY_HOST_REALLOCATE:
                    entry->kind==HOST_HEAP_DESTROY?NATIVE_MEMORY_HOST_CONTEXT_FREE:
                    entry->kind==HOST_HEAP_CREATE?NATIVE_MEMORY_HOST_CONTEXT_CREATE:NATIVE_MEMORY_HOST_FREE;
            if(arena&&(operation==NATIVE_MEMORY_HOST_FREE||operation==NATIVE_MEMORY_HOST_REALLOCATE||operation==NATIVE_MEMORY_HOST_CONTEXT_FREE)){
                tracking=native_memory_host_custom_backing_prepare(env,arena,NATIVE_MEMORY_HOST_HEAP,release,arguments[0],
                        (uint64_t)(uintptr_t)address,operation,owners,unknown,&denied);customBacking=tracking!=NULL&&operation==NATIVE_MEMORY_HOST_FREE;
            }
            if(!tracking&&!denied)tracking=native_memory_host_prepare(env,NATIVE_MEMORY_HOST_HEAP,release,entry->kind==HOST_HEAP_CREATE?0:arguments[0],
                    entry->kind==HOST_HEAP_ALLOC||entry->kind==HOST_HEAP_DESTROY||entry->kind==HOST_HEAP_CREATE?0:(uint64_t)(uintptr_t)address,
                    bytes,operation,owners,unknown,&denied);
            if(denied)error=ERROR_BUSY;
            else if(!native_memory_host_sources(tracking,&owners,&unknown))error=ERROR_NOT_ENOUGH_MEMORY;
            else if(cleanup&&!native_memory_host_cleanup(tracking)&&(native_owner_stopped(owners)||unknown&&(restricted||native_has_producer(owners))))
                error=native_owner_stopped(owners)?ERROR_ACCESS_DENIED:ERROR_INVALID_DATA;
        }
        if(!error){
            int locked=0;
            if(guard){
                locked=native_private_read_lock(restricted);
                if(!locked)error=ERROR_BUSY;
                else {int hit=host_memory_range_locked(address,heap?1:bytes,!heap);
                    if(hit)error=hit>0?ERROR_ACCESS_DENIED:restricted?ERROR_BUSY:ERROR_INVALID_DATA;
                    /* Heap exceptions run outside exception filters before
                     * finally unwinds this frame. Those filters may allocate;
                     * keep only the exact generation lease across that call. */
                    if(heap){ReleaseSRWLockShared(&native_private_gate);locked=0;}}
            }
            __try {
                int retiring=cleanup&&native_memory_host_cleanup(tracking)&&host_memory_cleanup_scope(entry,state);
                if(!error&&native_owner_stopped(owners)&&!retiring)error=ERROR_ACCESS_DENIED;
                else if(!error&&unknown&&(restricted||native_has_producer(owners))&&!retiring)error=ERROR_INVALID_DATA;
                if(!error){SetLastError(incoming);result=host_allocation_original(entry,process,arguments);error=GetLastError();returned=1;}
            } __finally {if(locked)ReleaseSRWLockShared(&native_private_gate);}
        }
        if(tracking){
            NativeHostMemoryCall *finished=tracking;tracking=NULL;
            if(customBacking)native_memory_host_backing_complete(env,finished,bytes,returned&&result!=0,
                    returned&&result!=0&&entry->kind==HOST_HEAP_FREE);
            else native_memory_host_complete(env,finished,entry->kind==HOST_HEAP_FREE||entry->kind==HOST_HEAP_DESTROY?0:result,bytes,returned&&result!=0,
                    returned&&result!=0&&(entry->kind==HOST_HEAP_FREE||entry->kind==HOST_HEAP_DESTROY));
        }else if(returned&&result&&heap){
            /* No allocation/context token means no captured heap epoch. A late
             * return must not be attributed to a newer reused heap HANDLE. */
            native_memory_host_unobserved(owners);
        }
        if(returned&&result&&entry->kind!=HOST_HEAP_FREE&&entry->kind!=HOST_HEAP_DESTROY)
            InterlockedExchange(&entry->image->privateOpaque,1);
        if(returned&&result&&arena&&(entry->kind==HOST_HEAP_ALLOC||entry->kind==HOST_HEAP_REALLOC))
            native_memory_host_custom_backing_allocated(arena,NATIVE_MEMORY_HOST_HEAP,release,arguments[0],result);
        if(returned&&result&&local&&!heap){
            DWORD type=(DWORD)arguments[first+2];
            if((type&MEM_RESERVE)||(!(type&(MEM_RESET|MEM_RESET_UNDO))&&!address)){
                const void *base=NULL;SIZE_T extent=0;int pages=0;
                int geometry=host_memory_allocation_span((const void*)(uintptr_t)result,&base,&extent,&pages);
                if(!geometry&&!pages&&base==(const void*)(uintptr_t)result){
                    if(arena)native_memory_host_backing_allocated(env,arena,entry->original,release,result,extent,owners,unknown);
                    else native_memory_host_allocated(env,NATIVE_MEMORY_HOST_VIRTUAL,entry->original,release,0,0,result,extent,owners,unknown);
                    if(arena)native_memory_host_custom_backing_allocated(arena,NATIVE_MEMORY_HOST_VIRTUAL,release,0,result);
                }
                else native_memory_host_unobserved(owners);
            }
        }
    } __finally {
        DWORD interrupted=GetLastError();
        if(tracking)native_memory_host_abort(env,tracking);
        if(process&&!host_real_close(process))InterlockedIncrement(&native_failures);
        native_owner_links_free(owners);SetLastError(returned?error:error?error:interrupted);
    }
    return result;
}
