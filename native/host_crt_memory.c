/* Actual loaded CRT byte/wchar_t operations. Included after host_crt_allocations.c.
 * Each import/result retains its declared symbol and exact provider address:
 * memcpy/memmove may share code, but neither an address alias nor a spelling
 * establishes the ABI of a different narrow/wide/secure operation.
 * https://learn.microsoft.com/en-us/cpp/c-runtime-library/reference/memcpy-s-wmemcpy-s
 * https://learn.microsoft.com/en-us/cpp/c-runtime-library/reference/memmove-s-wmemmove-s
 * Only the original CRT validates parameters and invokes its actual handler.
 */
#include <wchar.h>

typedef void *(__cdecl *HostCrtMemoryCopy)(void*,const void*,size_t);
typedef void *(__cdecl *HostCrtMemorySet)(void*,int,size_t);
typedef int (__cdecl *HostCrtMemoryCopyS)(void*,size_t,const void*,size_t);
typedef wchar_t *(__cdecl *HostCrtWideMemoryCopy)(wchar_t*,const wchar_t*,size_t);
typedef wchar_t *(__cdecl *HostCrtWideMemorySet)(wchar_t*,wchar_t,size_t);
typedef int (__cdecl *HostCrtWideMemoryCopyS)(wchar_t*,size_t,const wchar_t*,size_t);

typedef struct HostCrtMemoryTarget {
    void *original;int kind;HMODULE image,pin;
    HostCrtErrno error;HostCrtDosErrno dosError;
    struct HostCrtMemoryTarget *next;
} HostCrtMemoryTarget;
static HostCrtMemoryTarget *volatile host_crt_memory_exports;

static int host_is_crt_memory(int kind){return kind>=HOST_CRT_MEMCPY&&kind<=HOST_CRT_WMEMMOVE_S;}
static int host_is_crt_memory_secure(int kind){
    return kind==HOST_CRT_MEMCPY_S||kind==HOST_CRT_MEMMOVE_S
            ||kind==HOST_CRT_WMEMCPY_S||kind==HOST_CRT_WMEMMOVE_S;
}
static const char *host_crt_memory_name(int kind){
    static const char *names[]={"memcpy","memmove","memset","memcpy_s","memmove_s",
        "wmemcpy","wmemmove","wmemset","wmemcpy_s","wmemmove_s"};
    return host_is_crt_memory(kind)?names[kind-HOST_CRT_MEMCPY]:NULL;
}
static int host_crt_memory_contract(int kind){
    switch(kind){
        case HOST_CRT_MEMCPY:case HOST_CRT_MEMMOVE:return 1;
        case HOST_CRT_MEMSET:return 2;
        case HOST_CRT_MEMCPY_S:case HOST_CRT_MEMMOVE_S:return 3;
        case HOST_CRT_WMEMCPY:case HOST_CRT_WMEMMOVE:return 4;
        case HOST_CRT_WMEMSET:return 5;
        case HOST_CRT_WMEMCPY_S:case HOST_CRT_WMEMMOVE_S:return 6;
        default:return 0;
    }
}
static HostCrtMemoryTarget *host_crt_memory_target(void *actual,int kind){
    if(!actual||!host_is_crt_memory(kind))return NULL;
    for(HostCrtMemoryTarget *target=(HostCrtMemoryTarget*)InterlockedCompareExchangePointer(
            (void *volatile*)&host_crt_memory_exports,NULL,NULL);target;target=target->next)
        if(target->original==actual&&target->kind==kind)return target;
    return NULL;
}
static int host_crt_memory_target_matches(void *actual,int kind){return host_crt_memory_target(actual,kind)!=NULL;}
static int host_crt_memory_target_kind(void *actual){
    int selected=0;if(!actual)return 0;
    for(int kind=HOST_CRT_MEMCPY;kind<=HOST_CRT_WMEMMOVE_S;kind++)if(host_crt_memory_target_matches(actual,kind)){
        /* A same-address memcpy/memmove still needs the actual import or
         * resolver symbol. Never give their shared address the first name. */
        if(selected&&selected!=kind)return 0;selected=kind;
    }
    return selected;
}
static int host_crt_memory_named_target_kind(void *actual,const char *name){
    if(!actual||!name)return 0;
    for(int kind=HOST_CRT_MEMCPY;kind<=HOST_CRT_WMEMMOVE_S;kind++)
        if(!strcmp(name,host_crt_memory_name(kind))&&host_crt_memory_target_matches(actual,kind))return kind;
    return 0;
}
static int host_crt_memory_declared_name(const char *name,size_t maximum){
    if(!name)return 0;
    for(int kind=HOST_CRT_MEMCPY;kind<=HOST_CRT_WMEMMOVE_S;kind++){
        const char *expected=host_crt_memory_name(kind);size_t bytes=strlen(expected)+1;
        if(bytes<=maximum&&!memcmp(name,expected,bytes))return kind;
    }
    return 0;
}
static void *host_crt_memory_observe(HMODULE provider,int kind,HostGetProcAddress resolve){
    const char *name=host_crt_memory_name(kind);if(!provider||!name||!resolve)return NULL;
    DWORD incoming=GetLastError();void *actual=(void*)resolve(provider,name);
    HostCrtMemoryTarget *known=host_crt_memory_target(actual,kind);
    if(known){SetLastError(incoming);return known->original;}
    HMODULE image=actual?native_image(actual):NULL,pin=NULL;
    /* Forwarders are accepted only when the actual implementation exports
     * this exact symbol at this address. No api-set's CRT state substitutes
     * for that implementation's TLS. An incompatible same-address symbol
     * cannot acquire a narrow/wide/secure calling contract by discovery order. */
    if(!image||(void*)resolve(image,name)!=actual){SetLastError(incoming);return NULL;}
    for(int other=HOST_CRT_MEMCPY;other<=HOST_CRT_WMEMMOVE_S;other++)
        if(host_crt_memory_contract(other)!=host_crt_memory_contract(kind)
                &&(void*)resolve(image,host_crt_memory_name(other))==actual){SetLastError(incoming);return NULL;}
    HostCrtErrno error=(HostCrtErrno)resolve(image,"_errno");
    HostCrtDosErrno dosError=(HostCrtDosErrno)resolve(image,"__doserrno");
    if(error&&native_image((void*)error)!=image)error=NULL;
    if(dosError&&native_image((void*)dosError)!=image)dosError=NULL;
    /* Plain vcruntime operations can exist without an exported errno TLS.
     * Keep their exact addresses with an explicit observation gap. Secure
     * functions have a real errno contract and require their actual TLS. */
    if(host_is_crt_memory_secure(kind)&&(!error||!dosError)){SetLastError(incoming);return NULL;}
    if(!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,(LPCSTR)actual,&pin)||pin!=image){
        if(pin)host_real_free_library(pin);SetLastError(incoming);return NULL;
    }
    HostCrtMemoryTarget *target=(HostCrtMemoryTarget*)calloc(1,sizeof(*target));
    if(!target){host_real_free_library(pin);SetLastError(incoming);return NULL;}
    target->original=actual;target->kind=kind;target->image=image;target->pin=pin;target->error=error;target->dosError=dosError;
    for(;;){
        HostCrtMemoryTarget *head=(HostCrtMemoryTarget*)InterlockedCompareExchangePointer(
                (void *volatile*)&host_crt_memory_exports,NULL,NULL);
        known=NULL;for(HostCrtMemoryTarget *old=head;old;old=old->next)
            if(old->original==actual&&old->kind==kind){known=old;break;}
        if(known){host_real_free_library(pin);free(target);SetLastError(incoming);return known->original;}
        target->next=head;
        if(InterlockedCompareExchangePointer((void *volatile*)&host_crt_memory_exports,target,head)==head){
            SetLastError(incoming);return actual;
        }
    }
}
static void *host_crt_memory_provider_target(HMODULE provider,int kind){
    return host_crt_memory_observe(provider,kind,__atomic_load_n(&host_windows_resolver,__ATOMIC_ACQUIRE));
}
static void host_crt_memory_targets(HostGetProcAddress resolve){
    if(!resolve)return;DWORD incoming=GetLastError();
    static const wchar_t *providers[]={L"ucrtbase.dll",L"ucrtbased.dll",L"msvcrt.dll",
        L"api-ms-win-crt-string-l1-1-0.dll",L"api-ms-win-crt-runtime-l1-1-0.dll",
        L"vcruntime140.dll",L"vcruntime140d.dll",L"vcruntime140_1.dll",L"vcruntime140_1d.dll"};
    for(unsigned i=0;i<sizeof(providers)/sizeof(providers[0]);i++){
        HMODULE provider=GetModuleHandleW(providers[i]);if(!provider)continue;
        for(int kind=HOST_CRT_MEMCPY;kind<=HOST_CRT_WMEMMOVE_S;kind++)host_crt_memory_observe(provider,kind,resolve);
    }
    SetLastError(incoming);
}
static int host_crt_memory_errors(HostImport *entry,HostCrtThreadErrors *saved){
    memset(saved,0,sizeof(*saved));saved->windowsBefore=GetLastError();
    HostCrtMemoryTarget *target=host_crt_memory_target(entry->original,entry->kind);
    if(target){
        if(target->error){saved->error=target->error();if(saved->error)saved->before=*saved->error;}
        if(target->dosError){saved->dosError=target->dosError();if(saved->dosError)saved->dosBefore=*saved->dosError;}
    }
    SetLastError(saved->windowsBefore);return target&&saved->error&&saved->dosError;
}
static uint64_t host_crt_memory_refuse(int kind,const HostCrtThreadErrors *incoming,DWORD error){
    HostCrtThreadErrors refused=*incoming;refused.windowsBefore=error;
    refused.before=error==ERROR_NOT_ENOUGH_MEMORY||error==ERROR_OUTOFMEMORY?ENOMEM:error==ERROR_BUSY?EBUSY:
            error==ERROR_INVALID_DATA||error==ERROR_INVALID_PARAMETER||error==ERROR_INVALID_FUNCTION
            ||error==ERROR_ARITHMETIC_OVERFLOW?EINVAL:EACCES;
    refused.dosBefore=(unsigned long)error;host_crt_allocation_restore(&refused);
    return host_is_crt_memory_secure(kind)?(uint64_t)(uint32_t)refused.before:0;
}
static uint64_t host_crt_memory_original(HostImport *entry,const uintptr_t *a){
    switch(entry->kind){
        case HOST_CRT_MEMCPY:case HOST_CRT_MEMMOVE:
            return (uint64_t)(uintptr_t)((HostCrtMemoryCopy)entry->original)((void*)a[0],(const void*)a[1],(size_t)a[2]);
        case HOST_CRT_MEMSET:
            return (uint64_t)(uintptr_t)((HostCrtMemorySet)entry->original)((void*)a[0],(int)a[1],(size_t)a[2]);
        case HOST_CRT_MEMCPY_S:case HOST_CRT_MEMMOVE_S:
            return (uint64_t)(uint32_t)((HostCrtMemoryCopyS)entry->original)((void*)a[0],(size_t)a[1],(const void*)a[2],(size_t)a[3]);
        case HOST_CRT_WMEMCPY:case HOST_CRT_WMEMMOVE:
            return (uint64_t)(uintptr_t)((HostCrtWideMemoryCopy)entry->original)((wchar_t*)a[0],(const wchar_t*)a[1],(size_t)a[2]);
        case HOST_CRT_WMEMSET:
            return (uint64_t)(uintptr_t)((HostCrtWideMemorySet)entry->original)((wchar_t*)a[0],(wchar_t)a[1],(size_t)a[2]);
        case HOST_CRT_WMEMCPY_S:case HOST_CRT_WMEMMOVE_S:
            return (uint64_t)(uint32_t)((HostCrtWideMemoryCopyS)entry->original)((wchar_t*)a[0],(size_t)a[1],(const wchar_t*)a[2],(size_t)a[3]);
        default:SetLastError(ERROR_INVALID_FUNCTION);return 0;
    }
}
typedef struct HostCrtMemoryRanges {void *destination;const void *source;uint64_t writing,reading;int overflow;} HostCrtMemoryRanges;
static HostCrtMemoryRanges host_crt_memory_ranges(int kind,const uintptr_t *a){
    HostCrtMemoryRanges range={0};int secure=host_is_crt_memory_secure(kind);
    int setting=kind==HOST_CRT_MEMSET||kind==HOST_CRT_WMEMSET;
    size_t count=(size_t)a[secure?3:2],writeCount=count,readCount=setting?0:count;
    range.destination=(void*)a[0];range.source=setting?NULL:(const void*)a[secure?2:1];
    /* A zero-count CRT call does not touch either buffer, even when the
     * pointer is controlled or a supplied destination capacity is enormous. */
    if(!count)return range;
    if(secure){
        size_t capacity=(size_t)a[1];
        if(!range.destination)writeCount=readCount=0;
        else if(!range.source||capacity<count){
            /* memcpy_s clears its whole declared destination before its real
             * invalid-parameter handler; memmove_s leaves it untouched.
             * These branches select possible memory effects only. They do
             * not synthesize the CRT's validation, errno or handler result. */
            writeCount=kind==HOST_CRT_MEMCPY_S||kind==HOST_CRT_WMEMCPY_S?capacity:0;readCount=0;
        }
    }
    size_t width=kind>=HOST_CRT_WMEMCPY?sizeof(wchar_t):1;
    if(writeCount>SIZE_MAX/width||readCount>SIZE_MAX/width){range.overflow=1;return range;}
    range.writing=(uint64_t)(writeCount*width);range.reading=(uint64_t)(readCount*width);return range;
}
/* Source/Host and private directories are queried separately with short locks.
 * No directory/private/row gate survives into the original CRT or its handler. */
static int host_crt_memory_control(const HostCrtMemoryRanges *range,int restricted){
    int hit=host_memory_range(range->destination,(SIZE_T)range->writing,0,restricted);
    if(!hit)hit=host_memory_range(range->source,(SIZE_T)range->reading,0,restricted);
    if(hit)return hit;
    int locked=restricted?TryAcquireSRWLockExclusive(&native_private_gate):(AcquireSRWLockExclusive(&native_private_gate),1);
    if(!locked)return -1;
    hit=host_memory_output_locked(range->destination,(SIZE_T)range->writing);
    if(!hit)hit=host_memory_output_locked(range->source,(SIZE_T)range->reading);
    ReleaseSRWLockExclusive(&native_private_gate);return hit;
}
static uint64_t host_native_crt_memory(HostImport *entry,void *caller,uintptr_t *a,int callbackEntered){
    HostCrtThreadErrors incoming;int errorsReady=host_crt_memory_errors(entry,&incoming),kind=entry->kind;
    if(!host_crt_memory_target(entry->original,kind))return host_crt_memory_refuse(kind,&incoming,ERROR_INVALID_FUNCTION);
    if(!errorsReady&&entry->image)InterlockedExchange(&entry->image->privateOpaque,1);
    HostCrtMemoryRanges range=host_crt_memory_ranges(kind,a);
    /* An unrepresentable nonzero wchar_t interval cannot be narrowed to a
     * first-byte probe: that would hide known/control storage in its middle.
     * This boundary refuses overflow before the CRT; its original overflow
     * fault/handler behavior is deliberately not claimed as preserved. */
    if(range.overflow){if(entry->image)InterlockedExchange(&entry->image->privateOpaque,1);
        return host_crt_memory_refuse(kind,&incoming,ERROR_ARITHMETIC_OVERFLOW);}
    if(!range.writing&&!range.reading){host_crt_allocation_restore(&incoming);return host_crt_memory_original(entry,a);}
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    int restricted=host_loader_callout()||(state&&(state->heapCallback||state->toolingRestricted||state->vmDetaching||state->vmThreadEnding
            ||state->hostUnloadImage||state->hostRelease||state->hostLoad&&state->hostLoad->calling));
    int control=host_crt_memory_control(&range,restricted);
    int observed=control>0?1:native_memory_host_bytes_probe((uint64_t)(uintptr_t)range.destination,range.writing,
            (uint64_t)(uintptr_t)range.source,range.reading,restricted);
    if(!control&&!observed){
        /* Ordinary unobserved stack/provider storage stays an ordinary call.
         * No Java query, memory row or ownership is manufactured for it. */
        if(entry->image)InterlockedExchange(&entry->image->privateOpaque,1);
        host_crt_allocation_restore(&incoming);return host_crt_memory_original(entry,a);
    }
    HostImport *consumed=restricted?entry:host_consumed_entry(entry,state,caller);
    if(consumed->kind!=kind||consumed->original!=entry->original)return host_crt_memory_refuse(kind,&incoming,ERROR_INVALID_DATA);
    entry=consumed;OwnerLink *owners=NULL;int unknown=0;
    if(restricted&&(!state||(!state->heapCallback&&!state->toolingRestricted&&!state->vmDetaching&&!state->vmThreadEnding
            &&!state->hostUnloadImage&&!state->hostRelease&&!(state->hostLoad&&state->hostLoad->calling))))
        return host_crt_memory_refuse(kind,&incoming,ERROR_BUSY);
    if(!host_entry_sources_query(entry,state,caller,&owners,NULL,1,&unknown)){
        DWORD error=GetLastError();native_owner_links_free(owners);return host_crt_memory_refuse(kind,&incoming,error);
    }
    NativeHostBytesCall *call=NULL;uint64_t result=0;int called=0,returnedNormally=0;
    HostCrtThreadErrors returned=incoming;
    __try {
        DWORD refusal=0;
        if(native_owner_stopped(owners))refusal=ERROR_ACCESS_DENIED;
        else if(unknown)refusal=ERROR_INVALID_DATA;
        else if(control)refusal=control>0?ERROR_ACCESS_DENIED:restricted?ERROR_BUSY:ERROR_INVALID_DATA;
        else if(observed<0)refusal=restricted?ERROR_BUSY:ERROR_INVALID_DATA;
        if(!refusal){int denied=0;
            call=native_memory_host_bytes_begin_caller((uint64_t)(uintptr_t)range.destination,range.writing,
                    (uint64_t)(uintptr_t)range.source,range.reading,owners,unknown,restricted,&denied,callbackEntered,caller);
            /* Once a published range was observed, a missing admission cannot
             * downgrade this invocation to an unobserved raw-pointer call. */
            if(denied||!call)refusal=ERROR_BUSY;
        }
        if(!refusal){
            control=host_crt_memory_control(&range,restricted);
            if(control)refusal=control>0?ERROR_ACCESS_DENIED:restricted?ERROR_BUSY:ERROR_INVALID_DATA;
            else if(native_owner_stopped(owners))refusal=ERROR_ACCESS_DENIED;
        }
        if(refusal)result=host_crt_memory_refuse(kind,&incoming,refusal);
        else {
            /* The one paired lease covers both source reads and destination
             * writes, including overlap within one actual allocation. The
             * real CRT alone performs the copy/fill and any user callback. */
            host_crt_allocation_restore(&incoming);called=1;result=host_crt_memory_original(entry,a);
            returned=host_crt_allocation_result(&incoming);returnedNormally=1;
        }
    } __finally {
        /* Read the same actual TLS pointers before bookkeeping. An original
         * exception continues unchanged; partial writes stay opaque/unknown
         * and no private object fields or bytes are guessed or rolled back. */
        if(!returnedNormally)returned=host_crt_allocation_result(&incoming);
        if(call){
            if(called&&native_memory_host_bytes_output_unobserved(call)&&entry->image)
                InterlockedExchange(&entry->image->privateOpaque,1);
            native_memory_host_bytes_end(call,called,returnedNormally);
        }
        native_owner_links_free(owners);host_crt_allocation_restore(&returned);
    }
    return result;
}
