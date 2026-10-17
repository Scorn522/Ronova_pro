/* Actual mimalloc 2.x public ABI. Only exact exports in an already observed,
 * pinned provider enter here. No private heap/page layout is read.
 * https://github.com/microsoft/mimalloc/blob/v2.1.7/include/mimalloc.h
 * https://github.com/microsoft/mimalloc/blob/v2.1.7/src/heap.c
 * https://github.com/microsoft/mimalloc/blob/v2.1.7/src/alloc.c
 * Included after host_arena_allocations.c for the actual PE/CRT observers. */
typedef void *(__cdecl *HostOtherMalloc)(size_t);
typedef void *(__cdecl *HostOtherCalloc)(size_t,size_t);
typedef void *(__cdecl *HostOtherRealloc)(void*,size_t);
typedef void (__cdecl *HostOtherFree)(void*);
typedef size_t (__cdecl *HostOtherUsable)(const void*);
typedef void *(__cdecl *HostOtherAligned)(size_t,size_t);
typedef void *(__cdecl *HostOtherReallocAligned)(void*,size_t,size_t);
typedef void *(__cdecl *HostOtherHeapGet)(void);
typedef void *(__cdecl *HostOtherHeapSet)(void*);
typedef void (__cdecl *HostOtherHeapEnd)(void*);
typedef void *(__cdecl *HostOtherHeapMalloc)(void*,size_t);
typedef void *(__cdecl *HostOtherHeapCalloc)(void*,size_t,size_t);
typedef void *(__cdecl *HostOtherHeapRealloc)(void*,void*,size_t);
typedef void *(__cdecl *HostOtherHeapAligned)(void*,size_t,size_t);
typedef void *(__cdecl *HostOtherHeapReallocAligned)(void*,void*,size_t,size_t);
typedef void (__cdecl *HostOtherCollect)(_Bool);
typedef void (__cdecl *HostOtherHeapCollect)(void*,_Bool);
typedef void *(__cdecl *HostOtherHeapArena)(int);
typedef void (__cdecl *HostOtherThreadDone)(void);
typedef int (__cdecl *HostOtherVersion)(void);
typedef _Bool (__cdecl *HostOtherContains)(void*,const void*);
enum {HOST_OTHER_EXPORT_COUNT=24};
typedef struct HostOtherProvider {
    HMODULE image,pin;void *exports[HOST_OTHER_EXPORT_COUNT];
    HostOtherFree release;HostOtherUsable usable;HostOtherHeapGet getDefault,getBacking;
    HostOtherContains contains;HostCrtErrno error;HostCrtDosErrno dosError;int errorReady,version;
    struct HostOtherProvider *next;
} HostOtherProvider;
static HostOtherProvider *volatile host_other_providers;
static int host_is_other_allocation(int kind){return kind>=HOST_MI_MALLOC&&kind<=HOST_MI_THREAD_DONE;}
static const char *host_other_name(int kind){
    static const char *names[HOST_OTHER_EXPORT_COUNT]={"mi_malloc","mi_calloc","mi_realloc","mi_free","mi_usable_size",
        "mi_malloc_aligned","mi_realloc_aligned","mi_heap_new","mi_heap_delete","mi_heap_destroy","mi_heap_malloc",
        "mi_heap_calloc","mi_heap_realloc","mi_heap_malloc_aligned","mi_heap_realloc_aligned","mi_heap_get_default",
        "mi_heap_get_backing","mi_heap_set_default","mi_collect","mi_heap_collect","mi_heap_new_in_arena",
        "mi_zalloc","mi_heap_zalloc","mi_thread_done"};
    return host_is_other_allocation(kind)?names[kind-HOST_MI_MALLOC]:NULL;
}
static HostOtherProvider *host_other_target(void *actual,int kind){
    if(!actual||!host_is_other_allocation(kind))return NULL;
    for(HostOtherProvider *provider=(HostOtherProvider*)InterlockedCompareExchangePointer((void *volatile*)&host_other_providers,NULL,NULL);
            provider;provider=provider->next)if(provider->exports[kind-HOST_MI_MALLOC]==actual)return provider;
    return NULL;
}
static int host_other_target_matches(void *actual,int kind){return host_other_target(actual,kind)!=NULL;}
static int host_other_named_target_kind(void *actual,const char *name){
    if(!actual||!name)return 0;
    for(int kind=HOST_MI_MALLOC;kind<=HOST_MI_THREAD_DONE;kind++)
        if(!strcmp(name,host_other_name(kind))&&host_other_target_matches(actual,kind))return kind;
    return 0;
}
static int host_other_target_kind(void *actual){
    int chosen=0;if(!actual)return 0;
    for(int kind=HOST_MI_MALLOC;kind<=HOST_MI_THREAD_DONE;kind++)if(host_other_target_matches(actual,kind)){
        if(chosen&&chosen!=kind)return 0;chosen=kind;
    }return chosen;
}
static void host_other_publish(HMODULE image,HostGetProcAddress resolve){
    if(!image||!resolve)return;
    for(HostOtherProvider *old=(HostOtherProvider*)InterlockedCompareExchangePointer((void *volatile*)&host_other_providers,NULL,NULL);
            old;old=old->next)if(old->image==image)return;
    HMODULE pin=NULL;
    if(!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,(LPCSTR)image,&pin)||pin!=image){if(pin)host_real_free_library(pin);return;}
    HostOtherProvider *provider=(HostOtherProvider*)calloc(1,sizeof(*provider));
    if(!provider){host_real_free_library(pin);return;}provider->image=image;provider->pin=pin;
    for(int kind=HOST_MI_MALLOC;kind<=HOST_MI_THREAD_DONE;kind++){
        void *actual=(void*)resolve(image,host_other_name(kind));
        if(actual&&native_image(actual)==image)provider->exports[kind-HOST_MI_MALLOC]=actual;
    }
    provider->release=(HostOtherFree)provider->exports[HOST_MI_FREE-HOST_MI_MALLOC];
    provider->usable=(HostOtherUsable)provider->exports[HOST_MI_USABLE_SIZE-HOST_MI_MALLOC];
    provider->getDefault=(HostOtherHeapGet)provider->exports[HOST_MI_HEAP_GET_DEFAULT-HOST_MI_MALLOC];
    provider->getBacking=(HostOtherHeapGet)provider->exports[HOST_MI_HEAP_GET_BACKING-HOST_MI_MALLOC];
    provider->contains=(HostOtherContains)resolve(image,"mi_heap_contains_block");
    HostOtherVersion version=(HostOtherVersion)resolve(image,"mi_version");
    if(!provider->exports[0]||!provider->release||!provider->usable||!provider->getDefault||!provider->getBacking
            ||!provider->exports[HOST_MI_HEAP_NEW-HOST_MI_MALLOC]||!provider->exports[HOST_MI_HEAP_DELETE-HOST_MI_MALLOC]
            ||!provider->exports[HOST_MI_HEAP_DESTROY-HOST_MI_MALLOC]||!version||!provider->contains
            ||native_image((void*)version)!=image||native_image((void*)provider->contains)!=image){
        free(provider);host_real_free_library(pin);return;}
    HostArenaProvider errors={0};errors.image=image;provider->errorReady=host_arena_provider_errors(&errors,resolve);
    provider->error=errors.error;provider->dosError=errors.dosError;
    HostCrtThreadErrors saved={0};saved.windowsBefore=GetLastError();
    if(provider->error){saved.error=provider->error();if(saved.error)saved.before=*saved.error;}
    if(provider->dosError){saved.dosError=provider->dosError();if(saved.dosError)saved.dosBefore=*saved.dosError;}
    __try {provider->version=version();}__except(EXCEPTION_EXECUTE_HANDLER){provider->version=0;}
    host_crt_allocation_restore(&saved);
    /* v3 changes heap semantics. Names alone do not authorize that different
     * contract; unsupported versions keep the existing opaque boundary. */
    if(provider->version<200||provider->version>=300){free(provider);host_real_free_library(pin);return;}
    for(;;){
        HostOtherProvider *head=(HostOtherProvider*)InterlockedCompareExchangePointer((void *volatile*)&host_other_providers,NULL,NULL);
        for(HostOtherProvider *old=head;old;old=old->next)if(old->image==image){free(provider);host_real_free_library(pin);return;}
        provider->next=head;if(InterlockedCompareExchangePointer((void *volatile*)&host_other_providers,provider,head)==head)return;
    }
}
static void host_other_discover(HMODULE image){
    DWORD incoming=GetLastError();HostGetProcAddress resolve=__atomic_load_n(&host_windows_resolver,__ATOMIC_ACQUIRE);
    if(resolve)host_other_publish(image,resolve);SetLastError(incoming);
}
static void host_other_import_table(unsigned char *base,DWORD size,DWORD lookup,DWORD slots,int rvaBased){
    if(!lookup||!slots)return;
    for(size_t index=0;;index++){
        size_t at=(size_t)lookup+index*sizeof(IMAGE_THUNK_DATA64),iat=(size_t)slots+index*sizeof(IMAGE_THUNK_DATA64);
        if(at>UINT32_MAX||iat>UINT32_MAX||!host_arena_image_range((DWORD)at,sizeof(IMAGE_THUNK_DATA64),size)
                ||!host_arena_image_range((DWORD)iat,sizeof(IMAGE_THUNK_DATA64),size))return;
        IMAGE_THUNK_DATA64 *name=(IMAGE_THUNK_DATA64*)(base+at),*slot=(IMAGE_THUNK_DATA64*)(base+iat);
        uint64_t named=name->u1.AddressOfData;if(!named)return;if(IMAGE_SNAP_BY_ORDINAL64(named))continue;
        if(!rvaBased){if(named<(uintptr_t)base)continue;named-=(uintptr_t)base;}
        if(named>UINT32_MAX||!host_arena_image_range((DWORD)named,sizeof(WORD)+1,size))continue;
        const char *text=(const char*)(base+(size_t)named+sizeof(WORD));
        if(!memchr(text,0,(size_t)(size-named-sizeof(WORD))))return;
        int declared=0;for(int kind=HOST_MI_MALLOC;kind<=HOST_MI_THREAD_DONE;kind++)
            if(!strcmp(text,host_other_name(kind))){declared=kind;break;}
        if(!declared||!slot->u1.Function)continue;
        HMODULE implementation=native_image((void*)(uintptr_t)slot->u1.Function);
        /* A delay slot still in its consumer has no observed provider yet. */
        if(implementation&&implementation!=(HMODULE)base)host_other_discover(implementation);
    }
}
static void host_other_discover_imports(HMODULE image){
    if(!image)return;DWORD incoming=GetLastError();HMODULE pin=NULL;
    if(!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,(LPCSTR)image,&pin)||pin!=image){
        if(pin)host_real_free_library(pin);SetLastError(incoming);return;}
    __try {
        unsigned char *base=(unsigned char*)image;IMAGE_DOS_HEADER *dos=(IMAGE_DOS_HEADER*)base;
        if(dos->e_magic==IMAGE_DOS_SIGNATURE&&dos->e_lfanew>0){
            IMAGE_NT_HEADERS64 *nt=(IMAGE_NT_HEADERS64*)(base+dos->e_lfanew);
            if(nt->Signature==IMAGE_NT_SIGNATURE&&nt->FileHeader.Machine==IMAGE_FILE_MACHINE_AMD64
                    &&nt->OptionalHeader.Magic==IMAGE_NT_OPTIONAL_HDR64_MAGIC){
                DWORD size=nt->OptionalHeader.SizeOfImage;
                if(nt->OptionalHeader.NumberOfRvaAndSizes>IMAGE_DIRECTORY_ENTRY_IMPORT){
                    IMAGE_DATA_DIRECTORY table=nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_IMPORT];
                    if(table.VirtualAddress&&host_arena_image_range(table.VirtualAddress,table.Size,size))
                        for(size_t offset=0;offset+sizeof(IMAGE_IMPORT_DESCRIPTOR)<=table.Size;offset+=sizeof(IMAGE_IMPORT_DESCRIPTOR)){
                            IMAGE_IMPORT_DESCRIPTOR *item=(IMAGE_IMPORT_DESCRIPTOR*)(base+table.VirtualAddress+offset);
                            if(!item->Name&&!item->FirstThunk)break;
                            host_other_import_table(base,size,item->OriginalFirstThunk,item->FirstThunk,1);
                        }
                }
                if(nt->OptionalHeader.NumberOfRvaAndSizes>IMAGE_DIRECTORY_ENTRY_DELAY_IMPORT){
                    typedef struct HostOtherDelayImport {DWORD attributes,name,module,iat,lookup,bound,unload,time;} HostOtherDelayImport;
                    IMAGE_DATA_DIRECTORY table=nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_DELAY_IMPORT];
                    if(table.VirtualAddress&&host_arena_image_range(table.VirtualAddress,table.Size,size))
                        for(size_t offset=0;offset+sizeof(HostOtherDelayImport)<=table.Size;offset+=sizeof(HostOtherDelayImport)){
                            HostOtherDelayImport *item=(HostOtherDelayImport*)(base+table.VirtualAddress+offset);
                            if(!item->name&&!item->iat)break;uintptr_t lookup=item->lookup,slots=item->iat;
                            if(!(item->attributes&1)){if(lookup<(uintptr_t)base||slots<(uintptr_t)base)continue;lookup-=(uintptr_t)base;slots-=(uintptr_t)base;}
                            if(lookup<=UINT32_MAX&&slots<=UINT32_MAX)host_other_import_table(base,size,(DWORD)lookup,(DWORD)slots,(item->attributes&1)!=0);
                        }
                }
            }
        }
    }__except(EXCEPTION_EXECUTE_HANDLER){}
    host_real_free_library(pin);SetLastError(incoming);
}
static NativeHostMemoryCall *host_other_loader_scope(HostImport *entry,NativeThread *state,void *caller,
        OwnerLink *owners,int unknown,NativeHostArenaOriginalScope *scope){
    if(!state||state->hostArenaOriginal||!__atomic_load_n(&host_loader_callout_query,__ATOMIC_ACQUIRE)
            ||!host_loader_callout()||!entry||!entry->image||native_image(caller)!=entry->image->handle)return NULL;
    HostOtherProvider *provider=NULL;
    for(HostOtherProvider *item=(HostOtherProvider*)InterlockedCompareExchangePointer((void *volatile*)&host_other_providers,NULL,NULL);
            item;item=item->next)if(item->image==entry->image->handle){provider=item;break;}
    if(!provider)return NULL;int denied=0;
    NativeHostMemoryCall *call=native_memory_host_other_prepare(NULL,(void*)provider->release,0,0,NATIVE_MEMORY_HOST_OBSERVE,owners,unknown,&denied);
    if(!call||denied)return NULL;
    *scope=(NativeHostArenaOriginalScope){0};scope->image=provider->image;scope->original=caller;
    scope->release=(void*)provider->release;scope->entry=entry;scope->call=call;scope->thread=GetCurrentThreadId();
    scope->previous=state->hostArenaOriginal;state->hostArenaOriginal=scope;return call;
}
static void host_other_loader_scope_close(NativeThread *state,NativeHostArenaOriginalScope *scope,NativeHostMemoryCall *call){
    if(!call)return;state->hostArenaOriginal=scope->previous;native_memory_host_cancel(call);
}
static int host_other_errors(HostImport *entry,HostCrtThreadErrors *saved){
    memset(saved,0,sizeof(*saved));saved->windowsBefore=GetLastError();
    HostOtherProvider *provider=host_other_target(entry->original,entry->kind);
    if(provider){if(provider->error){saved->error=provider->error();if(saved->error)saved->before=*saved->error;}
        if(provider->dosError){saved->dosError=provider->dosError();if(saved->dosError)saved->dosBefore=*saved->dosError;}}
    SetLastError(saved->windowsBefore);
    return provider&&provider->errorReady&&(!provider->error||saved->error)&&(!provider->dosError||saved->dosError);
}
static void host_other_restore(const HostCrtThreadErrors *saved){host_crt_allocation_restore(saved);}
static HostCrtThreadErrors host_other_result(const HostCrtThreadErrors *saved){return host_crt_allocation_result(saved);}
static uint64_t host_other_refuse(int kind,const HostCrtThreadErrors *incoming,DWORD error){
    (void)kind;return host_crt_allocation_refuse(incoming,error);
}
static uint64_t host_other_original(HostImport *entry,const uintptr_t *a){
    switch(entry->kind){
        case HOST_MI_MALLOC:case HOST_MI_ZALLOC:return (uintptr_t)((HostOtherMalloc)entry->original)((size_t)a[0]);
        case HOST_MI_CALLOC:return (uintptr_t)((HostOtherCalloc)entry->original)((size_t)a[0],(size_t)a[1]);
        case HOST_MI_REALLOC:return (uintptr_t)((HostOtherRealloc)entry->original)((void*)a[0],(size_t)a[1]);
        case HOST_MI_FREE:((HostOtherFree)entry->original)((void*)a[0]);return 0;
        case HOST_MI_USABLE_SIZE:return ((HostOtherUsable)entry->original)((const void*)a[0]);
        case HOST_MI_MALLOC_ALIGNED:return (uintptr_t)((HostOtherAligned)entry->original)((size_t)a[0],(size_t)a[1]);
        case HOST_MI_REALLOC_ALIGNED:return (uintptr_t)((HostOtherReallocAligned)entry->original)((void*)a[0],(size_t)a[1],(size_t)a[2]);
        case HOST_MI_HEAP_NEW:case HOST_MI_HEAP_GET_DEFAULT:case HOST_MI_HEAP_GET_BACKING:return (uintptr_t)((HostOtherHeapGet)entry->original)();
        case HOST_MI_HEAP_DELETE:case HOST_MI_HEAP_DESTROY:((HostOtherHeapEnd)entry->original)((void*)a[0]);return 0;
        case HOST_MI_HEAP_MALLOC:case HOST_MI_HEAP_ZALLOC:return (uintptr_t)((HostOtherHeapMalloc)entry->original)((void*)a[0],(size_t)a[1]);
        case HOST_MI_HEAP_CALLOC:return (uintptr_t)((HostOtherHeapCalloc)entry->original)((void*)a[0],(size_t)a[1],(size_t)a[2]);
        case HOST_MI_HEAP_REALLOC:return (uintptr_t)((HostOtherHeapRealloc)entry->original)((void*)a[0],(void*)a[1],(size_t)a[2]);
        case HOST_MI_HEAP_MALLOC_ALIGNED:return (uintptr_t)((HostOtherHeapAligned)entry->original)((void*)a[0],(size_t)a[1],(size_t)a[2]);
        case HOST_MI_HEAP_REALLOC_ALIGNED:return (uintptr_t)((HostOtherHeapReallocAligned)entry->original)((void*)a[0],(void*)a[1],(size_t)a[2],(size_t)a[3]);
        case HOST_MI_HEAP_SET_DEFAULT:return (uintptr_t)((HostOtherHeapSet)entry->original)((void*)a[0]);
        case HOST_MI_COLLECT:((HostOtherCollect)entry->original)((_Bool)(unsigned char)a[0]);return 0;
        case HOST_MI_HEAP_COLLECT:((HostOtherHeapCollect)entry->original)((void*)a[0],(_Bool)(unsigned char)a[1]);return 0;
        case HOST_MI_HEAP_NEW_IN_ARENA:return (uintptr_t)((HostOtherHeapArena)entry->original)((int)a[0]);
        case HOST_MI_THREAD_DONE:((HostOtherThreadDone)entry->original)();return 0;
        default:SetLastError(ERROR_INVALID_FUNCTION);return 0;
    }
}
static uint64_t host_other_scoped_original(HostImport *entry,HostOtherProvider *provider,NativeThread *state,
        const uintptr_t *arguments,NativeHostMemoryCall *call,int cleanup,const HostCrtThreadErrors *incoming){
    if(!state)state=native_thread();
    if(!state||!call){host_other_restore(incoming);return host_other_original(entry,arguments);}
    NativeHostArenaOriginalScope scope={0};scope.image=provider->image;scope.original=entry->original;
    scope.release=(void*)provider->release;scope.entry=entry;scope.call=call;scope.thread=GetCurrentThreadId();scope.cleanup=cleanup;
    scope.previous=state->hostArenaOriginal;state->hostArenaOriginal=&scope;uint64_t result=0;
    __try {host_other_restore(incoming);result=host_other_original(entry,arguments);}
    __finally {DWORD outgoing=GetLastError();state->hostArenaOriginal=scope.previous;native_thread_try_release(state);SetLastError(outgoing);}
    return result;
}
/* Only successful returned blocks are passed to the public usable/contains
 * observers. An incoming free/realloc pointer is never queried or dereferenced.
 * A same-address realloc may remain in its old heap despite an explicit target. */
static int host_other_extent(HostOtherProvider *provider,uint64_t pointer,uint64_t oldAddress,uintptr_t oldContext,
        uintptr_t requested,uint64_t *bytes,uintptr_t *context){
    int extent=0;*bytes=UINT64_MAX;*context=UINTPTR_MAX;
    __try {
        *bytes=provider->usable((const void*)(uintptr_t)pointer);extent=*bytes!=0;
        if(oldAddress==pointer&&oldContext){
            if(provider->contains((void*)oldContext,(const void*)(uintptr_t)pointer))*context=oldContext;
        }
        if(*context==UINTPTR_MAX&&requested&&provider->contains((void*)requested,(const void*)(uintptr_t)pointer))*context=requested;
        if(*context==UINTPTR_MAX){void *actual=provider->getDefault();
            if(actual&&provider->contains(actual,(const void*)(uintptr_t)pointer))*context=(uintptr_t)actual;}
        if(*context==UINTPTR_MAX){void *actual=provider->getBacking();
            if(actual&&provider->contains(actual,(const void*)(uintptr_t)pointer))*context=(uintptr_t)actual;}
        if(*context==UINTPTR_MAX&&oldAddress==pointer&&!oldContext)*context=0;
    }__except(EXCEPTION_EXECUTE_HANDLER){extent=0;*bytes=UINT64_MAX;*context=UINTPTR_MAX;}
    return extent;
}
static uint64_t host_native_other_allocation(HostImport *entry,void *caller,uintptr_t *arguments){
    HostCrtThreadErrors incoming;int errorsReady=host_other_errors(entry,&incoming);
    HostOtherProvider *provider=host_other_target(entry->original,entry->kind);
    if(!provider)return host_other_refuse(entry->kind,&incoming,ERROR_INVALID_FUNCTION);
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    int restricted=state&&(state->heapCallback||state->toolingRestricted||state->vmDetaching||state->vmThreadEnding
            ||state->hostUnloadImage||state->hostRelease||state->hostLoad&&state->hostLoad->calling);
    HostImport *consumed=restricted?entry:host_consumed_entry(entry,state,caller);
    if(consumed->kind!=entry->kind||consumed->original!=entry->original)return host_other_refuse(entry->kind,&incoming,ERROR_INVALID_DATA);
    entry=consumed;OwnerLink *owners=NULL;int unknown=0;
    if(!host_entry_sources_query(entry,state,caller,&owners,NULL,1,&unknown)){
        DWORD error=GetLastError();native_owner_links_free(owners);return host_other_refuse(entry->kind,&incoming,error);}
    JNIEnv *env=host_memory_environment(restricted);int kind=entry->kind;
    int heapAllocation=kind>=HOST_MI_HEAP_MALLOC&&kind<=HOST_MI_HEAP_REALLOC_ALIGNED||kind==HOST_MI_HEAP_ZALLOC;
    int replacing=kind==HOST_MI_REALLOC||kind==HOST_MI_REALLOC_ALIGNED||kind==HOST_MI_HEAP_REALLOC||kind==HOST_MI_HEAP_REALLOC_ALIGNED;
    int releasing=kind==HOST_MI_FREE,query=kind==HOST_MI_USABLE_SIZE;
    int creating=kind==HOST_MI_HEAP_NEW||kind==HOST_MI_HEAP_NEW_IN_ARENA;
    int deleting=kind==HOST_MI_HEAP_DELETE,destroying=kind==HOST_MI_HEAP_DESTROY;
    int accessor=kind==HOST_MI_HEAP_GET_DEFAULT||kind==HOST_MI_HEAP_GET_BACKING||kind==HOST_MI_HEAP_SET_DEFAULT;
    int collecting=kind==HOST_MI_COLLECT||kind==HOST_MI_HEAP_COLLECT,threadDone=kind==HOST_MI_THREAD_DONE;
    uint64_t address=replacing?(uint64_t)arguments[heapAllocation?1:0]:releasing||query?(uint64_t)arguments[0]:0;
    uintptr_t requested=heapAllocation?(uintptr_t)arguments[0]:0,context=deleting||destroying?(uintptr_t)arguments[0]:requested;
    int operation=creating?NATIVE_MEMORY_HOST_CONTEXT_CREATE:deleting?NATIVE_MEMORY_HOST_CONTEXT_DETACH:
            destroying?NATIVE_MEMORY_HOST_CONTEXT_FREE:threadDone?NATIVE_MEMORY_HOST_THREAD_DETACH:
            accessor?NATIVE_MEMORY_HOST_CONTEXT_OBSERVE:collecting||query?NATIVE_MEMORY_HOST_OBSERVE:
            replacing?NATIVE_MEMORY_HOST_REALLOCATE:releasing?NATIVE_MEMORY_HOST_FREE:NATIVE_MEMORY_HOST_ALLOCATE;
    uintptr_t backing=0;int backingObserved=0;
    int cleanup=address&&releasing&&host_memory_cleanup_scope(entry,state);
    NativeHostMemoryCall *call=NULL;uint64_t result=0;int originalReturned=0,rawPass=0;
    HostCrtThreadErrors returned=incoming;
    __try {
        DWORD refusal=0;
        if(native_owner_stopped(owners)&&!cleanup)refusal=ERROR_ACCESS_DENIED;
        else if(unknown&&(restricted||native_has_producer(owners))&&!cleanup)refusal=ERROR_INVALID_DATA;
        if(!refusal&&address)refusal=host_arena_range((const void*)(uintptr_t)address,1,restricted);
        if(!refusal&&(heapAllocation||deleting||destroying||kind==HOST_MI_HEAP_SET_DEFAULT||kind==HOST_MI_HEAP_COLLECT))
            refusal=host_arena_range((const void*)arguments[0],1,restricted);
        if(!refusal&&(deleting||destroying)){
            /* This accessor inspects the current thread, never the unknown
             * incoming heap. A backing heap's destroy is documented delete. */
            __try {backing=(uintptr_t)provider->getBacking();backingObserved=backing!=0;}
            __except(EXCEPTION_EXECUTE_HANDLER){backingObserved=0;}
            if(destroying&&backingObserved&&context==backing)operation=NATIVE_MEMORY_HOST_CONTEXT_DETACH;
        }
        if(!refusal){int denied=0;
            call=native_memory_host_other_prepare(env,(void*)provider->release,address,context,operation,owners,unknown,&denied);
            if(denied)refusal=ERROR_BUSY;else if(!native_memory_host_sources(call,&owners,&unknown))refusal=ERROR_NOT_ENOUGH_MEMORY;
        }
        int cleanupKnown=cleanup&&native_memory_host_cleanup(call)&&host_memory_cleanup_scope(entry,state);
        if(!refusal&&native_owner_stopped(owners)&&!cleanupKnown)refusal=ERROR_ACCESS_DENIED;
        else if(!refusal&&unknown&&(restricted||native_has_producer(owners))&&!cleanupKnown)refusal=ERROR_INVALID_DATA;
        if(refusal){if(call){native_memory_host_cancel(call);call=NULL;}
            result=host_other_refuse(kind,&incoming,refusal);returned=host_other_result(&incoming);}
        else if(!errorsReady){
            if(call){native_memory_host_abort(env,call);call=NULL;}
            int denied=0;NativeHostMemoryCall *opaque=native_memory_host_other_prepare(env,(void*)provider->release,0,0,NATIVE_MEMORY_HOST_ARENA_UNKNOWN,owners,1,&denied);
            if(opaque)native_memory_host_other_complete(env,opaque,0,0,0,0,0);
            native_memory_host_unobserved(owners);native_owner_links_free(owners);owners=NULL;
            InterlockedExchange(&entry->image->privateOpaque,1);rawPass=1;host_other_restore(&incoming);result=host_other_original(entry,arguments);
        }else {
            uintptr_t oldContext=native_memory_host_other_context(call);
            result=host_other_scoped_original(entry,provider,state,arguments,call,cleanupKnown,&incoming);
            returned=host_other_result(&incoming);originalReturned=1;
            uint64_t pointer=0,bytes=0;uintptr_t actualContext=0;int completed=1,released=0;
            if(creating){actualContext=(uintptr_t)result;completed=result!=0;}
            else if(deleting||destroying){
                released=destroying&&operation==NATIVE_MEMORY_HOST_CONTEXT_FREE;
                actualContext=backingObserved?(context==backing?0:backing):UINTPTR_MAX;
                if(!backingObserved&&call){native_memory_host_abort(env,call);call=NULL;}
            }else if(accessor)actualContext=(uintptr_t)result;
            else if(threadDone||collecting||query){}
            else if(releasing)released=1;
            else {
                pointer=result;completed=pointer!=0;
                if(pointer&&!host_other_extent(provider,pointer,address,oldContext,requested,&bytes,&actualContext))bytes=UINT64_MAX;
                /* mimalloc zero-size realloc returns a minimum allocation or
                 * failure. A NULL result never proves that its old block died. */
            }
            if(pointer)InterlockedExchange(&entry->image->privateOpaque,1);
            if(call){native_memory_host_other_complete(env,call,pointer,bytes,actualContext,completed,released);call=NULL;}
        }
    }__finally {
        if(!rawPass){if(!originalReturned)returned=host_other_result(&incoming);
            if(call)native_memory_host_abort(env,call);native_owner_links_free(owners);host_other_restore(&returned);}
    }
    return result;
}
