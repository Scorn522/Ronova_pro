/* The public Windows CRT aligned-allocation ABI. These blocks must use the
 * exact implementation's _aligned_free, never its ordinary free export.
 * https://learn.microsoft.com/en-us/cpp/c-runtime-library/reference/aligned-realloc
 * https://learn.microsoft.com/en-us/cpp/c-runtime-library/reference/aligned-offset-recalloc
 * https://learn.microsoft.com/en-us/cpp/c-runtime-library/reference/aligned-msize
 */
typedef struct HostCrtAlignedTarget {
    void *original;int kind;HMODULE image,pin;HostCrtErrno error;HostCrtDosErrno dosError;
    HostCrtFree release;struct HostCrtAlignedTarget *next;
} HostCrtAlignedTarget;
static HostCrtAlignedTarget *volatile host_crt_aligned_exports;
static int host_is_crt_aligned(int kind){return kind>=HOST_CRT_ALIGNED_MALLOC&&kind<=HOST_CRT_ALIGNED_MSIZE;}
static const char *host_crt_aligned_name(int kind){
    static const char *names[]={"_aligned_malloc","_aligned_offset_malloc","_aligned_realloc","_aligned_offset_realloc",
        "_aligned_recalloc","_aligned_offset_recalloc","_aligned_free","_aligned_msize"};
    return host_is_crt_aligned(kind)?names[kind-HOST_CRT_ALIGNED_MALLOC]:NULL;
}
static HostCrtAlignedTarget *host_crt_aligned_target(void *actual,int kind){
    for(HostCrtAlignedTarget *target=(HostCrtAlignedTarget*)InterlockedCompareExchangePointer(
            (void *volatile*)&host_crt_aligned_exports,NULL,NULL);target;target=target->next)
        if(target->original==actual&&(!kind||kind==target->kind))return target;
    return NULL;
}
static void *host_crt_aligned_provider(HMODULE provider,int kind){
    const char *name=host_crt_aligned_name(kind);if(!provider||!name)return NULL;
    DWORD incoming=GetLastError();HostGetProcAddress resolve=__atomic_load_n(&host_windows_resolver,__ATOMIC_ACQUIRE);
    void *actual=resolve?(void*)resolve(provider,name):NULL;
    HostCrtAlignedTarget *known=actual?host_crt_aligned_target(actual,kind):NULL;
    if(known){SetLastError(incoming);return actual;}
    HMODULE image=actual?native_image(actual):NULL,pin=NULL;
    HostCrtErrno error=image?(HostCrtErrno)resolve(image,"_errno"):NULL;
    HostCrtDosErrno dosError=image?(HostCrtDosErrno)resolve(image,"__doserrno"):NULL;
    HostCrtFree release=image?(HostCrtFree)resolve(image,"_aligned_free"):NULL;
    if(!image||!error||!dosError||!release||native_image((void*)error)!=image
            ||native_image((void*)dosError)!=image||native_image((void*)release)!=image){SetLastError(incoming);return NULL;}
    if(!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,(LPCSTR)actual,&pin)||pin!=image){
        if(pin)host_real_free_library(pin);SetLastError(incoming);return NULL;}
    HostCrtAlignedTarget *target=(HostCrtAlignedTarget*)calloc(1,sizeof(*target));
    if(!target){host_real_free_library(pin);SetLastError(incoming);return NULL;}
    *target=(HostCrtAlignedTarget){actual,kind,image,pin,error,dosError,release,NULL};
    for(;;){
        HostCrtAlignedTarget *head=(HostCrtAlignedTarget*)InterlockedCompareExchangePointer(
                (void *volatile*)&host_crt_aligned_exports,NULL,NULL);
        for(HostCrtAlignedTarget *old=head;old;old=old->next)if(old->original==actual&&old->kind==kind){
            free(target);host_real_free_library(pin);SetLastError(incoming);return actual;}
        target->next=head;
        if(InterlockedCompareExchangePointer((void *volatile*)&host_crt_aligned_exports,target,head)==head){SetLastError(incoming);return actual;}
    }
}
static int host_crt_aligned_target_kind(void *actual){
    HostCrtAlignedTarget *target=actual?host_crt_aligned_target(actual,0):NULL;return target?target->kind:0;
}
static int host_crt_aligned_errors(HostImport *entry,HostCrtThreadErrors *saved){
    memset(saved,0,sizeof(*saved));saved->windowsBefore=GetLastError();
    HostCrtAlignedTarget *target=host_crt_aligned_target(entry->original,entry->kind);
    if(target){saved->error=target->error();if(saved->error)saved->before=*saved->error;
        saved->dosError=target->dosError();if(saved->dosError)saved->dosBefore=*saved->dosError;}
    SetLastError(saved->windowsBefore);return target&&saved->error&&saved->dosError;
}
static uint64_t host_crt_aligned_refuse(int kind,const HostCrtThreadErrors *incoming,DWORD error){
    host_crt_allocation_refuse(incoming,error);return kind==HOST_CRT_ALIGNED_MSIZE?SIZE_MAX:0;
}
static uint64_t host_crt_aligned_original(HostImport *entry,const uintptr_t *a){
    switch(entry->kind){
        case HOST_CRT_ALIGNED_MALLOC:return (uintptr_t)((void *(__cdecl*)(size_t,size_t))entry->original)(a[0],a[1]);
        case HOST_CRT_ALIGNED_OFFSET_MALLOC:return (uintptr_t)((void *(__cdecl*)(size_t,size_t,size_t))entry->original)(a[0],a[1],a[2]);
        case HOST_CRT_ALIGNED_REALLOC:return (uintptr_t)((void *(__cdecl*)(void*,size_t,size_t))entry->original)((void*)a[0],a[1],a[2]);
        case HOST_CRT_ALIGNED_OFFSET_REALLOC:return (uintptr_t)((void *(__cdecl*)(void*,size_t,size_t,size_t))entry->original)((void*)a[0],a[1],a[2],a[3]);
        case HOST_CRT_ALIGNED_RECALLOC:return (uintptr_t)((void *(__cdecl*)(void*,size_t,size_t,size_t))entry->original)((void*)a[0],a[1],a[2],a[3]);
        case HOST_CRT_ALIGNED_OFFSET_RECALLOC:return (uintptr_t)((void *(__cdecl*)(void*,size_t,size_t,size_t,size_t))entry->original)((void*)a[0],a[1],a[2],a[3],a[4]);
        case HOST_CRT_ALIGNED_FREE:((HostCrtFree)entry->original)((void*)a[0]);return 0;
        case HOST_CRT_ALIGNED_MSIZE:return ((size_t (__cdecl*)(void*,size_t,size_t))entry->original)((void*)a[0],a[1],a[2]);
        default:SetLastError(ERROR_INVALID_FUNCTION);return 0;
    }
}
static uint64_t host_native_crt_aligned(HostImport *entry,void *caller,uintptr_t *a){
    HostCrtThreadErrors incoming;int errorsReady=host_crt_aligned_errors(entry,&incoming);
    HostCrtAlignedTarget *target=host_crt_aligned_target(entry->original,entry->kind);
    if(!target)return host_crt_aligned_refuse(entry->kind,&incoming,ERROR_INVALID_FUNCTION);
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    int restricted=state&&(state->heapCallback||state->toolingRestricted||state->vmDetaching||state->vmThreadEnding
            ||state->hostUnloadImage||state->hostRelease||state->hostLoad&&state->hostLoad->calling);
    HostImport *consumed=restricted?entry:host_consumed_entry(entry,state,caller);
    if(consumed->kind!=entry->kind||consumed->original!=entry->original)
        return host_crt_aligned_refuse(entry->kind,&incoming,ERROR_INVALID_DATA);
    entry=consumed;OwnerLink *owners=NULL;int unknown=0;
    NativeHostCppOriginalScope *cppScope=host_crt_cpp_scope(entry,state,caller);
    NativeHostArenaOriginalScope *customScope=host_memory_arena_scope(entry,state,caller);
    if(!host_entry_sources_query(entry,state,caller,&owners,NULL,1,&unknown)){
        DWORD error=GetLastError();native_owner_links_free(owners);return host_crt_aligned_refuse(entry->kind,&incoming,error);}
    JNIEnv *env=host_memory_environment(restricted);int kind=entry->kind;
    int observing=kind==HOST_CRT_ALIGNED_MSIZE,releasing=kind==HOST_CRT_ALIGNED_FREE;
    int replacing=kind>=HOST_CRT_ALIGNED_REALLOC&&kind<=HOST_CRT_ALIGNED_OFFSET_RECALLOC;
    int counted=kind==HOST_CRT_ALIGNED_RECALLOC||kind==HOST_CRT_ALIGNED_OFFSET_RECALLOC;
    int offsetApi=kind==HOST_CRT_ALIGNED_OFFSET_MALLOC||kind==HOST_CRT_ALIGNED_OFFSET_REALLOC||kind==HOST_CRT_ALIGNED_OFFSET_RECALLOC;
    uint64_t address=observing||releasing||replacing?a[0]:0,bytes=0;int overflow=0;
    size_t alignment=0,offset=0;
    if(!observing&&!releasing){
        if(counted){size_t count=a[1],width=a[2];overflow=count&&width>SIZE_MAX/count;bytes=overflow?UINT64_MAX:count*width;alignment=a[3];if(offsetApi)offset=a[4];}
        else {bytes=a[replacing?1:0];alignment=a[replacing?2:1];if(offsetApi)offset=a[replacing?3:2];}
    }
    int cleanup=address&&(releasing||replacing&&!bytes)&&(host_memory_cleanup_scope(entry,state)||host_crt_cpp_cleanup_scope(cppScope,state));
    NativeHostMemoryCall *call=NULL;uint64_t result=0;int originalStarted=0,returnedNormally=0,rawPass=0,customBacking=0;
    HostCrtThreadErrors returned=incoming;
    __try {
        DWORD refusal=0;
        if(native_owner_stopped(owners)&&!cleanup)refusal=ERROR_ACCESS_DENIED;
        else if(unknown&&(restricted||native_has_producer(owners))&&!cleanup)refusal=ERROR_INVALID_DATA;
        if(!refusal&&address){
            int hit=host_memory_range((const void*)(uintptr_t)address,1,0,restricted);
            if(hit)refusal=hit>0?ERROR_ACCESS_DENIED:restricted?ERROR_BUSY:ERROR_INVALID_DATA;
            if(!refusal){int locked=native_private_read_lock(restricted);
                if(!locked)refusal=ERROR_BUSY;
                else {hit=host_memory_output_locked((const void*)(uintptr_t)address,1);
                    if(hit)refusal=hit>0?ERROR_ACCESS_DENIED:ERROR_INVALID_DATA;ReleaseSRWLockShared(&native_private_gate);}}
            if(!refusal){int denied=0;
                if(customScope&&!observing){call=native_memory_host_custom_backing_prepare(env,customScope,NATIVE_MEMORY_HOST_CRT,
                        (void*)target->release,0,address,replacing?NATIVE_MEMORY_HOST_REALLOCATE:NATIVE_MEMORY_HOST_FREE,owners,unknown,&denied);
                    customBacking=call!=NULL;}
                if(!call&&!denied)call=native_memory_host_cpp_backing_prepare(env,cppScope,(void*)target->release,0,address,
                    observing?NATIVE_MEMORY_HOST_OBSERVE:replacing?NATIVE_MEMORY_HOST_REALLOCATE:NATIVE_MEMORY_HOST_FREE,owners,unknown,&denied);
                if(denied)refusal=ERROR_BUSY;else if(!native_memory_host_sources(call,&owners,&unknown))refusal=ERROR_NOT_ENOUGH_MEMORY;}
        }
        int cleanupKnown=cleanup&&native_memory_host_cleanup(call)
                &&(host_memory_cleanup_scope(entry,state)||host_crt_cpp_cleanup_scope(cppScope,state));
        if(!refusal&&native_owner_stopped(owners)&&!cleanupKnown)refusal=ERROR_ACCESS_DENIED;
        else if(!refusal&&unknown&&(restricted||native_has_producer(owners))&&!cleanupKnown)refusal=ERROR_INVALID_DATA;
        if(refusal){result=host_crt_aligned_refuse(kind,&incoming,refusal);returned=host_crt_allocation_result(&incoming);}
        else if(!errorsReady){
            if(call){if(observing)native_memory_host_cancel(call);else native_memory_host_abort(env,call);call=NULL;}
            native_memory_host_unobserved(owners);native_owner_links_free(owners);owners=NULL;
            InterlockedExchange(&entry->image->privateOpaque,1);rawPass=1;host_crt_allocation_restore(&incoming);
            result=host_crt_aligned_original(entry,a);
        }else {
            host_crt_allocation_restore(&incoming);originalStarted=1;result=host_crt_aligned_original(entry,a);
            returned=host_crt_allocation_result(&incoming);returnedNormally=1;
            if(result&&!observing&&!releasing)InterlockedExchange(&entry->image->privateOpaque,1);
            if(call){
                if(observing){native_memory_host_cancel(call);call=NULL;}
                else {
                    /* Invalid alignment/offset must not turn a null result into
                     * a fabricated free. Leave ambiguous zero-size failures
                     * unresolved; the exact provider still owns validation. */
                    int validAlignment=alignment&&!(alignment&(alignment-1));
                    int zeroRelease=replacing&&address&&!bytes&&!result&&!overflow&&validAlignment&&!offset&&returned.before!=EINVAL;
                    int released=releasing||zeroRelease;
                    int ambiguous=replacing&&address&&!bytes&&!result&&!zeroRelease;
                    if(ambiguous)native_memory_host_abort(env,call);
                    else if(customBacking)native_memory_host_backing_complete(env,call,bytes,releasing||result!=0||released,released);
                    else native_memory_host_complete(env,call,result,bytes,releasing||result!=0||released,released);
                    call=NULL;
                }
            }else if(result&&!observing&&!releasing)
                native_memory_host_allocated(env,NATIVE_MEMORY_HOST_CRT,entry->original,(void*)target->release,0,0,
                    result,bytes,owners,unknown||overflow||(replacing&&address!=0));
            if(result&&!observing&&!releasing&&cppScope)native_memory_host_cpp_backing_allocated(env,cppScope,entry->original,
                    (void*)target->release,0,result,bytes,owners,unknown||overflow);
            if(result&&!observing&&!releasing&&customScope)native_memory_host_custom_backing_allocated(customScope,NATIVE_MEMORY_HOST_CRT,
                    (void*)target->release,0,result);
        }
    } __finally {
        if(!rawPass){
            if(!returnedNormally)returned=host_crt_allocation_result(&incoming);
            if(call){if(!originalStarted||observing)native_memory_host_cancel(call);else native_memory_host_abort(env,call);}
            native_owner_links_free(owners);host_crt_allocation_restore(&returned);
        }
    }
    return result;
}
