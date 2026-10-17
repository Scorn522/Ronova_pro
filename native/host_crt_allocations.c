/* Included after host_memory.c and the actual CRT thread target helpers.
 * Only imports/results resolving to these selected, already loaded CRT
 * exports enter this path. No C++ object layout or destructor is inferred.
 * https://learn.microsoft.com/en-us/cpp/c-runtime-library/reference/realloc
 * https://learn.microsoft.com/en-us/cpp/c-runtime-library/reference/calloc
 * https://learn.microsoft.com/en-us/cpp/c-runtime-library/reference/free
 */
typedef void *(__cdecl *HostCrtMalloc)(size_t);
typedef void *(__cdecl *HostCrtCalloc)(size_t,size_t);
typedef void *(__cdecl *HostCrtRealloc)(void*,size_t);
typedef void (__cdecl *HostCrtFree)(void*);

typedef struct HostCrtAllocationTarget {
    void *original;int kind;HMODULE image,pin;
    HostCrtErrno error;HostCrtDosErrno dosError;
    HostCrtRealloc reallocate;HostCrtFree release;
    struct HostCrtAllocationTarget *next;
} HostCrtAllocationTarget;

static HostCrtAllocationTarget *volatile host_crt_allocation_exports;
static int host_is_crt_allocation(int kind){return kind>=HOST_CRT_MALLOC&&kind<=HOST_CRT_FREE;}
static const char *host_crt_allocation_name(int kind){
    switch(kind){case HOST_CRT_MALLOC:return "malloc";case HOST_CRT_CALLOC:return "calloc";
        case HOST_CRT_REALLOC:return "realloc";case HOST_CRT_FREE:return "free";default:return NULL;}
}
static HostCrtAllocationTarget *host_crt_allocation_target(void *original,int kind){
    for(HostCrtAllocationTarget *entry=(HostCrtAllocationTarget*)InterlockedCompareExchangePointer(
                (void *volatile*)&host_crt_allocation_exports,NULL,NULL);entry;entry=entry->next)
        if(entry->original==original&&(!kind||entry->kind==kind))return entry;
    return NULL;
}
/* The resolver supplies only actual loaded CRT providers. A forwarding
 * api-set's HMODULE never substitutes for the implementation's heap/error
 * state. All retained functions are resolved in that exact implementation.
 */
static void *host_crt_provider_target(HMODULE provider,int kind){
    const char *name=host_crt_allocation_name(kind);if(!provider||!name)return NULL;
    DWORD incoming=GetLastError();HostGetProcAddress resolve=__atomic_load_n(&host_windows_resolver,__ATOMIC_ACQUIRE);
    void *actual=resolve?(void*)resolve(provider,name):NULL;
    HostCrtAllocationTarget *known=actual?host_crt_allocation_target(actual,kind):NULL;
    if(known){SetLastError(incoming);return known->original;}
    HMODULE image=actual?native_image(actual):NULL,pin=NULL;
    HostCrtErrno error=image?(HostCrtErrno)resolve(image,"_errno"):NULL;
    HostCrtDosErrno dosError=image?(HostCrtDosErrno)resolve(image,"__doserrno"):NULL;
    HostCrtRealloc reallocate=image?(HostCrtRealloc)resolve(image,"realloc"):NULL;
    HostCrtFree release=image?(HostCrtFree)resolve(image,"free"):NULL;
    if(!image||!error||!dosError||!reallocate||!release||native_image((void*)error)!=image
            ||native_image((void*)dosError)!=image||native_image((void*)reallocate)!=image||native_image((void*)release)!=image){
        SetLastError(incoming);return NULL;
    }
    if(!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,(LPCSTR)actual,&pin)||pin!=image){
        if(pin)host_real_free_library(pin);SetLastError(incoming);return NULL;
    }
    HostCrtAllocationTarget *entry=(HostCrtAllocationTarget*)calloc(1,sizeof(*entry));
    if(!entry){host_real_free_library(pin);SetLastError(incoming);return NULL;}
    entry->original=actual;entry->kind=kind;entry->image=image;entry->pin=pin;
    entry->error=error;entry->dosError=dosError;entry->reallocate=reallocate;entry->release=release;
    for(;;){
        HostCrtAllocationTarget *head=(HostCrtAllocationTarget*)InterlockedCompareExchangePointer(
                (void *volatile*)&host_crt_allocation_exports,NULL,NULL);
        known=NULL;for(HostCrtAllocationTarget *old=head;old;old=old->next)
            if(old->original==actual&&old->kind==kind){known=old;break;}
        if(known){host_real_free_library(pin);free(entry);SetLastError(incoming);return known->original;}
        entry->next=head;
        if(InterlockedCompareExchangePointer((void *volatile*)&host_crt_allocation_exports,entry,head)==head){SetLastError(incoming);return actual;}
    }
}
static int host_crt_allocation_target_kind(void *actual){
    HostCrtAllocationTarget *target=actual?host_crt_allocation_target(actual,0):NULL;return target?target->kind:0;
}

/* Also used by host_native_call so its association/epilogue bookkeeping cannot
 * change the CRT's caller-visible error state. Once captured, read these same
 * TLS pointers immediately after the original call; do not call an accessor
 * again while trying to capture its result.
 */
static int host_crt_allocation_errors(HostImport *entry,HostCrtThreadErrors *saved){
    memset(saved,0,sizeof(*saved));saved->windowsBefore=GetLastError();
    HostCrtAllocationTarget *target=host_crt_allocation_target(entry->original,entry->kind);
    if(target){saved->error=target->error();if(saved->error)saved->before=*saved->error;
        saved->dosError=target->dosError();if(saved->dosError)saved->dosBefore=*saved->dosError;}
    SetLastError(saved->windowsBefore);return target&&saved->error&&saved->dosError;
}
static void host_crt_allocation_restore(const HostCrtThreadErrors *saved){
    if(saved->error)*saved->error=saved->before;if(saved->dosError)*saved->dosError=saved->dosBefore;SetLastError(saved->windowsBefore);
}
static HostCrtThreadErrors host_crt_allocation_result(const HostCrtThreadErrors *incoming){
    HostCrtThreadErrors result=*incoming;result.windowsBefore=GetLastError();
    if(result.error)result.before=*result.error;if(result.dosError)result.dosBefore=*result.dosError;return result;
}
static uint64_t host_crt_allocation_refuse(const HostCrtThreadErrors *incoming,DWORD error){
    HostCrtThreadErrors refused=*incoming;refused.windowsBefore=error;
    refused.before=error==ERROR_NOT_ENOUGH_MEMORY||error==ERROR_OUTOFMEMORY?ENOMEM:
            error==ERROR_BUSY?EBUSY:error==ERROR_INVALID_DATA||error==ERROR_INVALID_PARAMETER?EINVAL:EACCES;
    refused.dosBefore=(unsigned long)error;host_crt_allocation_restore(&refused);return 0;
}
static uint64_t host_crt_allocation_original(HostImport *entry,const uintptr_t *arguments){
    switch(entry->kind){
        case HOST_CRT_MALLOC:return (uint64_t)(uintptr_t)((HostCrtMalloc)entry->original)((size_t)arguments[0]);
        case HOST_CRT_CALLOC:return (uint64_t)(uintptr_t)((HostCrtCalloc)entry->original)((size_t)arguments[0],(size_t)arguments[1]);
        case HOST_CRT_REALLOC:return (uint64_t)(uintptr_t)((HostCrtRealloc)entry->original)((void*)arguments[0],(size_t)arguments[1]);
        case HOST_CRT_FREE:((HostCrtFree)entry->original)((void*)arguments[0]);return 0;
        default:SetLastError(ERROR_INVALID_FUNCTION);return 0;
    }
}

static uint64_t host_native_crt_allocation(HostImport *entry,void *caller,uintptr_t *arguments){
    HostCrtThreadErrors incoming;int errorsReady=host_crt_allocation_errors(entry,&incoming);
    HostCrtAllocationTarget *target=host_crt_allocation_target(entry->original,entry->kind);
    if(!target)return host_crt_allocation_refuse(&incoming,ERROR_INVALID_FUNCTION);
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    int restricted=state&&(state->heapCallback||state->toolingRestricted||state->vmDetaching||state->vmThreadEnding
            ||state->hostUnloadImage||state->hostRelease||state->hostLoad&&state->hostLoad->calling);
    HostImport *consumed=restricted?entry:host_consumed_entry(entry,state,caller);
    if(consumed->kind!=entry->kind||consumed->original!=entry->original)
        return host_crt_allocation_refuse(&incoming,ERROR_INVALID_DATA);
    entry=consumed;OwnerLink *owners=NULL;int unknown=0;
    if(!host_entry_sources_query(entry,state,caller,&owners,NULL,1,&unknown)){
        DWORD error=GetLastError();native_owner_links_free(owners);return host_crt_allocation_refuse(&incoming,error);
    }
    JNIEnv *env=host_memory_environment(restricted);
    int replacing=entry->kind==HOST_CRT_REALLOC,releasing=entry->kind==HOST_CRT_FREE;
    uint64_t address=replacing||releasing?(uint64_t)arguments[0]:0,bytes=0;int extentUnknown=0;
    if(entry->kind==HOST_CRT_MALLOC)bytes=(uint64_t)(size_t)arguments[0];
    else if(replacing)bytes=(uint64_t)(size_t)arguments[1];
    else if(entry->kind==HOST_CRT_CALLOC){
        size_t count=(size_t)arguments[0],width=(size_t)arguments[1];
        /* Still call the original with both actual operands. A wrapped product
         * can never describe a nonnull result's writable extent. */
        if(count&&width>SIZE_MAX/count){bytes=UINT64_MAX;extentUnknown=1;}
        else bytes=(uint64_t)(count*width);
    }
    int cleanup=address&&(releasing||replacing&&!bytes)&&host_memory_cleanup_scope(entry,state);
    NativeHostMemoryCall *call=NULL;uint64_t result=0;int originalReturned=0,rawPass=0;
    HostCrtThreadErrors returned=incoming;
    __try {
        DWORD refusal=0;
        if(native_owner_stopped(owners)&&!cleanup)refusal=ERROR_ACCESS_DENIED;
        else if(unknown&&(restricted||native_has_producer(owners))&&!cleanup)refusal=ERROR_INVALID_DATA;
        if(!refusal&&address){
            /* Never query an unknown heap or infer an extent using _msize.
             * An exact private/control payload address cannot be consumed as
             * a business CRT allocation, including zero-sized realloc. */
            int hit=host_memory_range((const void*)(uintptr_t)address,1,0,restricted);
            if(hit)refusal=hit>0?ERROR_ACCESS_DENIED:restricted?ERROR_BUSY:ERROR_INVALID_DATA;
            if(!refusal){
                int locked=restricted?TryAcquireSRWLockExclusive(&native_private_gate):
                        (AcquireSRWLockExclusive(&native_private_gate),1);
                if(!locked)refusal=ERROR_BUSY;
                else {hit=host_memory_output_locked((const void*)(uintptr_t)address,1);
                    if(hit)refusal=hit>0?ERROR_ACCESS_DENIED:ERROR_INVALID_DATA;
                    ReleaseSRWLockExclusive(&native_private_gate);}
            }
            if(!refusal){int denied=0;
                call=native_memory_host_prepare(env,NATIVE_MEMORY_HOST_CRT,(void*)target->release,0,address,0,
                        replacing?NATIVE_MEMORY_HOST_REALLOCATE:NATIVE_MEMORY_HOST_FREE,owners,unknown,&denied);
                if(denied)refusal=ERROR_BUSY;
                else if(!native_memory_host_sources(call,&owners,&unknown))refusal=ERROR_NOT_ENOUGH_MEMORY;
            }
        }
        /* An original DLL unload may release its own already tracked block.
         * The real scope alone never authorizes an unknown pointer or a new
         * allocation: the exact provider/context/generation admission must
         * also have succeeded, including all current alias/user checks. */
        int cleanupKnown=cleanup&&native_memory_host_cleanup(call)&&host_memory_cleanup_scope(entry,state);
        if(!refusal&&native_owner_stopped(owners)&&!cleanupKnown)refusal=ERROR_ACCESS_DENIED;
        else if(!refusal&&unknown&&(restricted||native_has_producer(owners))&&!cleanupKnown)refusal=ERROR_INVALID_DATA;
        if(refusal){result=host_crt_allocation_refuse(&incoming,refusal);returned=host_crt_allocation_result(&incoming);}
        else if(!errorsReady){
            /* Missing error-state observation is not a new ordinary-call
             * refusal. Before the final direct call, abandon any retained old
             * lifetime as uncertain, record the missing observation, and clear
             * all our heap/JNI cleanup. The outer Host frame uses this same
             * raw mode and performs no target-CRT observer on the return path. */
            if(call){native_memory_host_abort(env,call);call=NULL;}
            native_memory_host_unobserved(owners);native_owner_links_free(owners);owners=NULL;
            InterlockedExchange(&entry->image->privateOpaque,1);
            rawPass=1;host_crt_allocation_restore(&incoming);
            result=host_crt_allocation_original(entry,arguments);
        }else {
            /* The actual CRT owns zero-size behavior, overflow/failure, new
             * handlers and all four original calling contracts. No allocation
             * or private-directory lock is held across these external calls. */
            host_crt_allocation_restore(&incoming);
            result=host_crt_allocation_original(entry,arguments);
            returned=host_crt_allocation_result(&incoming);originalReturned=1;
            if(result&&!releasing)InterlockedExchange(&entry->image->privateOpaque,1);
            if(call){
                int released=releasing||replacing&&address&&!bytes&&!result;
                int completed=releasing||result!=0||released;
                native_memory_host_complete(env,call,result,bytes,completed,released);call=NULL;
            }else if(result&&(entry->kind==HOST_CRT_MALLOC||entry->kind==HOST_CRT_CALLOC||replacing)){
                native_memory_host_allocated(env,NATIVE_MEMORY_HOST_CRT,entry->original,(void*)target->release,0,0,
                        result,bytes,owners,unknown||extentUnknown||(replacing&&address!=0));
            }
            /* A post-success observation failure records a gap in memory.c.
             * It never changes this result or frees the caller's allocation. */
        }
    } __finally {
        /* A real CRT/new-handler/invalid-parameter exception continues through
         * this frame unchanged. The old generation remains unresolved/alive
         * until an actual normal completion proves a resize/release. */
        if(!rawPass){
            if(!originalReturned)returned=host_crt_allocation_result(&incoming);
            if(call)native_memory_host_abort(env,call);
            native_owner_links_free(owners);host_crt_allocation_restore(&returned);
        }
    }
    return result;
}
