/* Actual public jemalloc ABIs used by LWJGL, including its statically linked
 * Windows CRT build. Included after host_crt_allocations.c/host_cpp_allocations.c.
 * https://github.com/LWJGL/lwjgl3/blob/3.3.1/modules/lwjgl/jemalloc/src/generated/java/org/lwjgl/system/jemalloc/JEmalloc.java
 * https://github.com/jemalloc/jemalloc/blob/5.2.1/include/jemalloc/jemalloc_protos.h.in
 * https://jemalloc.net/jemalloc.3.html
 * No allocator is loaded, and no arena/page layout or private TLS is inferred.
 */
#include <limits.h>
typedef void *(__cdecl *HostArenaMalloc)(size_t);
typedef void *(__cdecl *HostArenaCalloc)(size_t,size_t);
typedef void *(__cdecl *HostArenaRealloc)(void*,size_t);
typedef void (__cdecl *HostArenaFree)(void*);
typedef void *(__cdecl *HostArenaMallocx)(size_t,int);
typedef void *(__cdecl *HostArenaRallocx)(void*,size_t,int);
typedef size_t (__cdecl *HostArenaXallocx)(void*,size_t,size_t,int);
typedef void (__cdecl *HostArenaDallocx)(void*,int);
typedef void (__cdecl *HostArenaSdallocx)(void*,size_t,int);
typedef size_t (__cdecl *HostArenaSallocx)(const void*,int);
typedef size_t (__cdecl *HostArenaUsable)(const void*);
typedef void *(__cdecl *HostArenaAligned)(size_t,size_t);
typedef int (__cdecl *HostArenaPosix)(void**,size_t,size_t);
typedef int (__cdecl *HostArenaMallctl)(const char*,void*,size_t*,void*,size_t);
typedef int (__cdecl *HostArenaBymib)(const size_t*,size_t,void*,size_t*,void*,size_t);
typedef int (__cdecl *HostArenaTomib)(const char*,size_t*,size_t*);
enum { HOST_ARENA_EXPORT_COUNT=16,HOST_ARENA_NAME_CAPACITY=256,HOST_ARENA_MIB_CAPACITY=8 };
typedef struct HostArenaProvider {
    HMODULE image,pin;void *exports[HOST_ARENA_EXPORT_COUNT];
    HostArenaFree release;HostArenaSallocx usable;HostArenaMallctl control;HostArenaTomib toMib;
    HostCrtErrno error;HostCrtDosErrno dosError;int errorReady;
    struct HostArenaProvider *next;
} HostArenaProvider;
static HostArenaProvider *volatile host_arena_providers;
static int host_is_arena_allocation(int kind){return kind>=HOST_ARENA_MALLOC&&kind<=HOST_ARENA_MALLCTLNAMETOMIB;}
static const char *host_arena_name(int kind){
    static const char *names[HOST_ARENA_EXPORT_COUNT]={"je_malloc","je_calloc","je_realloc","je_free","je_mallocx","je_rallocx",
        "je_xallocx","je_dallocx","je_sdallocx","je_sallocx","je_malloc_usable_size","je_aligned_alloc","je_posix_memalign",
        "je_mallctl","je_mallctlbymib","je_mallctlnametomib"};
    return host_is_arena_allocation(kind)?names[kind-HOST_ARENA_MALLOC]:NULL;
}
static HostArenaProvider *host_arena_target(void *actual,int kind){
    if(!actual||!host_is_arena_allocation(kind))return NULL;
    for(HostArenaProvider *provider=(HostArenaProvider*)InterlockedCompareExchangePointer((void *volatile*)&host_arena_providers,NULL,NULL);
            provider;provider=provider->next)if(provider->exports[kind-HOST_ARENA_MALLOC]==actual)return provider;
    return NULL;
}
static int host_arena_target_matches(void *actual,int kind){return host_arena_target(actual,kind)!=NULL;}
static int host_arena_named_target_kind(void *actual,const char *name){
    if(!actual||!name)return 0;
    for(int kind=HOST_ARENA_MALLOC;kind<=HOST_ARENA_MALLCTLNAMETOMIB;kind++)
        if(!strcmp(name,host_arena_name(kind))&&host_arena_target_matches(actual,kind))return kind;
    return 0;
}
static int host_arena_target_kind(void *actual){
    int selected=0;if(!actual)return 0;
    for(int kind=HOST_ARENA_MALLOC;kind<=HOST_ARENA_MALLCTLNAMETOMIB;kind++)if(host_arena_target_matches(actual,kind)){
        if(selected&&selected!=kind)return 0;selected=kind;
    }
    return selected;
}
/* An actual OS loader callout is also an original provider execution boundary.
 * Its TLS/CRT teardown may release a backing page without calling je_free.
 * Only the same real caller/consumer image can lease that paired provider;
 * backing_prepare still acquires every affected live allocation/alias gate. */
static NativeHostMemoryCall *host_arena_loader_scope(HostImport *entry,NativeThread *state,void *caller,
        OwnerLink *owners,int unknown,NativeHostArenaOriginalScope *scope){
    if(!state||state->hostArenaOriginal||!__atomic_load_n(&host_loader_callout_query,__ATOMIC_ACQUIRE)
            ||!host_loader_callout()||!entry||!entry->image
            ||native_image(caller)!=entry->image->handle)return NULL;
    HostArenaProvider *provider=NULL;
    for(HostArenaProvider *item=(HostArenaProvider*)InterlockedCompareExchangePointer((void *volatile*)&host_arena_providers,NULL,NULL);
            item;item=item->next)if(item->image==entry->image->handle){provider=item;break;}
    if(!provider)return NULL;int denied=0;
    NativeHostMemoryCall *call=native_memory_host_arena_prepare(NULL,(void*)provider->release,0,0,
            NATIVE_MEMORY_HOST_OBSERVE,owners,unknown,&denied);
    if(!call||denied)return NULL;
    *scope=(NativeHostArenaOriginalScope){0};scope->image=provider->image;scope->original=caller;
    scope->release=(void*)provider->release;scope->entry=entry;scope->call=call;
    scope->thread=GetCurrentThreadId();scope->previous=state->hostArenaOriginal;state->hostArenaOriginal=scope;
    return call;
}
static void host_arena_loader_scope_close(NativeThread *state,NativeHostArenaOriginalScope *scope,NativeHostMemoryCall *call){
    if(!call)return;state->hostArenaOriginal=scope->previous;native_memory_host_cancel(call);
}
static int host_arena_image_range(DWORD rva,size_t bytes,DWORD size){return rva<=size&&bytes<=(size_t)(size-rva);}
/* A shared CRT is selected by this implementation's real IAT targets, rather
 * than a DLL basename. The local LWJGL jemalloc imports only kernel32 and has
 * an isolated static CRT: there is no exported/imported errno TLS to access.
 * Our bookkeeping uses Ronova's CRT. sallocx is the public pure size observer;
 * arenas.lookup and nametomib return status directly and do not set errno.
 */
static int host_arena_provider_errors(HostArenaProvider *provider,HostGetProcAddress resolve){
    int ready=0;
    __try {
        unsigned char *base=(unsigned char*)provider->image;IMAGE_DOS_HEADER *dos=(IMAGE_DOS_HEADER*)base;
        if(dos->e_magic==IMAGE_DOS_SIGNATURE&&dos->e_lfanew>0){
            IMAGE_NT_HEADERS64 *nt=(IMAGE_NT_HEADERS64*)(base+dos->e_lfanew);
            if(nt->Signature==IMAGE_NT_SIGNATURE&&nt->FileHeader.Machine==IMAGE_FILE_MACHINE_AMD64
                    &&nt->OptionalHeader.Magic==IMAGE_NT_OPTIONAL_HDR64_MAGIC
                    &&nt->OptionalHeader.NumberOfRvaAndSizes>IMAGE_DIRECTORY_ENTRY_IMPORT){
                DWORD size=nt->OptionalHeader.SizeOfImage;
                provider->error=(HostCrtErrno)resolve(provider->image,"_errno");
                provider->dosError=(HostCrtDosErrno)resolve(provider->image,"__doserrno");
                IMAGE_DATA_DIRECTORY directory=nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_IMPORT];
                ready=1;
                if(directory.VirtualAddress){
                    if(!host_arena_image_range(directory.VirtualAddress,directory.Size,size))ready=0;
                    for(size_t at=0;ready&&at+sizeof(IMAGE_IMPORT_DESCRIPTOR)<=directory.Size;at+=sizeof(IMAGE_IMPORT_DESCRIPTOR)){
                        IMAGE_IMPORT_DESCRIPTOR *item=(IMAGE_IMPORT_DESCRIPTOR*)(base+directory.VirtualAddress+at);
                        if(!item->Name&&!item->FirstThunk)break;
                        if(!item->FirstThunk){ready=0;break;}
                        for(size_t index=0;;index++){
                            size_t offset=(size_t)item->FirstThunk+index*sizeof(IMAGE_THUNK_DATA64);
                            if(offset>UINT32_MAX||!host_arena_image_range((DWORD)offset,sizeof(IMAGE_THUNK_DATA64),size)){ready=0;break;}
                            IMAGE_THUNK_DATA64 *slot=(IMAGE_THUNK_DATA64*)(base+offset);if(!slot->u1.Function)break;
                            HMODULE implementation=native_image((void*)(uintptr_t)slot->u1.Function);if(!implementation)continue;
                            HostCrtErrno error=(HostCrtErrno)resolve(implementation,"_errno");
                            HostCrtDosErrno dosError=(HostCrtDosErrno)resolve(implementation,"__doserrno");
                            if(error){if(provider->error&&provider->error!=error)ready=0;else provider->error=error;}
                            if(dosError){if(provider->dosError&&provider->dosError!=dosError)ready=0;else provider->dosError=dosError;}
                        }
                    }
                }
                /* An unresolved delayed CRT can change the error provider at
                 * first use. Do not guess its future TLS or call into it. */
                if(nt->OptionalHeader.NumberOfRvaAndSizes>IMAGE_DIRECTORY_ENTRY_DELAY_IMPORT
                        &&nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_DELAY_IMPORT].VirtualAddress)ready=0;
            }
        }
    } __except(EXCEPTION_EXECUTE_HANDLER){ready=0;}
    return ready;
}
static void host_arena_publish(HMODULE image,HostGetProcAddress resolve){
    if(!image||!resolve)return;
    for(HostArenaProvider *old=(HostArenaProvider*)InterlockedCompareExchangePointer((void *volatile*)&host_arena_providers,NULL,NULL);
            old;old=old->next)if(old->image==image)return;
    HMODULE pin=NULL;
    if(!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,(LPCSTR)image,&pin)||pin!=image){if(pin)host_real_free_library(pin);return;}
    HostArenaProvider *provider=(HostArenaProvider*)calloc(1,sizeof(*provider));
    if(!provider){host_real_free_library(pin);return;}provider->image=image;provider->pin=pin;
    for(int kind=HOST_ARENA_MALLOC;kind<=HOST_ARENA_MALLCTLNAMETOMIB;kind++){
        void *actual=(void*)resolve(image,host_arena_name(kind));
        if(actual&&native_image(actual)==image)provider->exports[kind-HOST_ARENA_MALLOC]=actual;
    }
    provider->release=(HostArenaFree)provider->exports[HOST_ARENA_FREE-HOST_ARENA_MALLOC];
    provider->usable=(HostArenaSallocx)provider->exports[HOST_ARENA_SALLOCX-HOST_ARENA_MALLOC];
    provider->control=(HostArenaMallctl)provider->exports[HOST_ARENA_MALLCTL-HOST_ARENA_MALLOC];
    provider->toMib=(HostArenaTomib)provider->exports[HOST_ARENA_MALLCTLNAMETOMIB-HOST_ARENA_MALLOC];
    if(!provider->exports[0]||!provider->release||!provider->usable||!provider->control||!provider->toMib
            ||!provider->exports[HOST_ARENA_MALLCTLBYMIB-HOST_ARENA_MALLOC]){free(provider);host_real_free_library(pin);return;}
    provider->errorReady=host_arena_provider_errors(provider,resolve);
    for(;;){
        HostArenaProvider *head=(HostArenaProvider*)InterlockedCompareExchangePointer((void *volatile*)&host_arena_providers,NULL,NULL);
        for(HostArenaProvider *old=head;old;old=old->next)if(old->image==image){free(provider);host_real_free_library(pin);return;}
        provider->next=head;if(InterlockedCompareExchangePointer((void *volatile*)&host_arena_providers,provider,head)==head)return;
    }
}
static void host_arena_discover(HMODULE image){
    DWORD incoming=GetLastError();HostGetProcAddress resolve=__atomic_load_n(&host_windows_resolver,__ATOMIC_ACQUIRE);
    if(resolve)host_arena_publish(image,resolve);SetLastError(incoming);
}
static void host_arena_import_table(unsigned char *base,DWORD size,DWORD lookup,DWORD slots,int rvaBased){
    if(!lookup||!slots)return;
    for(size_t index=0;;index++){
        size_t at=(size_t)lookup+index*sizeof(IMAGE_THUNK_DATA64),iat=(size_t)slots+index*sizeof(IMAGE_THUNK_DATA64);
        if(at>UINT32_MAX||iat>UINT32_MAX||!host_arena_image_range((DWORD)at,sizeof(IMAGE_THUNK_DATA64),size)
                ||!host_arena_image_range((DWORD)iat,sizeof(IMAGE_THUNK_DATA64),size))return;
        IMAGE_THUNK_DATA64 *name=(IMAGE_THUNK_DATA64*)(base+at),*slot=(IMAGE_THUNK_DATA64*)(base+iat);
        uint64_t named=name->u1.AddressOfData;if(!named)return;
        if(IMAGE_SNAP_BY_ORDINAL64(named))continue;
        if(!rvaBased){if(named<(uintptr_t)base)continue;named-=(uintptr_t)base;}
        if(named>UINT32_MAX||!host_arena_image_range((DWORD)named,sizeof(WORD)+1,size))continue;
        const char *text=(const char*)(base+(size_t)named+sizeof(WORD));
        if(!memchr(text,0,(size_t)(size-named-sizeof(WORD))))return;
        int declared=0;for(int kind=HOST_ARENA_MALLOC;kind<=HOST_ARENA_MALLCTLNAMETOMIB;kind++)
            if(!strcmp(text,host_arena_name(kind))){declared=kind;break;}
        if(!declared||!slot->u1.Function)continue;
        void *actual=(void*)(uintptr_t)slot->u1.Function;HMODULE implementation=native_image(actual);
        /* A still unresolved delay entry points back into its consumer. No
         * dependency is loaded and no resolver is called to force that slot. */
        if(implementation&&implementation!=(HMODULE)base)host_arena_discover(implementation);
    }
}
static void host_arena_discover_imports(HMODULE image){
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
                            host_arena_import_table(base,size,item->OriginalFirstThunk,item->FirstThunk,1);
                        }
                }
                if(nt->OptionalHeader.NumberOfRvaAndSizes>IMAGE_DIRECTORY_ENTRY_DELAY_IMPORT){
                    typedef struct HostArenaDelayImport {DWORD attributes,name,module,iat,lookup,bound,unload,time;} HostArenaDelayImport;
                    IMAGE_DATA_DIRECTORY table=nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_DELAY_IMPORT];
                    if(table.VirtualAddress&&host_arena_image_range(table.VirtualAddress,table.Size,size))
                        for(size_t offset=0;offset+sizeof(HostArenaDelayImport)<=table.Size;offset+=sizeof(HostArenaDelayImport)){
                            HostArenaDelayImport *item=(HostArenaDelayImport*)(base+table.VirtualAddress+offset);
                            if(!item->name&&!item->iat)break;uintptr_t lookup=item->lookup,slots=item->iat;
                            if(!(item->attributes&1)){if(lookup<(uintptr_t)base||slots<(uintptr_t)base)continue;lookup-=(uintptr_t)base;slots-=(uintptr_t)base;}
                            if(lookup<=UINT32_MAX&&slots<=UINT32_MAX)host_arena_import_table(base,size,(DWORD)lookup,(DWORD)slots,(item->attributes&1)!=0);
                        }
                }
            }
        }
    } __except(EXCEPTION_EXECUTE_HANDLER){}
    host_real_free_library(pin);SetLastError(incoming);
}
static int host_arena_errors(HostImport *entry,HostCrtThreadErrors *saved){
    memset(saved,0,sizeof(*saved));saved->windowsBefore=GetLastError();
    HostArenaProvider *provider=host_arena_target(entry->original,entry->kind);
    if(provider){if(provider->error){saved->error=provider->error();if(saved->error)saved->before=*saved->error;}
        if(provider->dosError){saved->dosError=provider->dosError();if(saved->dosError)saved->dosBefore=*saved->dosError;}}
    SetLastError(saved->windowsBefore);
    return provider&&provider->errorReady&&(!provider->error||saved->error)&&(!provider->dosError||saved->dosError);
}
static void host_arena_restore(const HostCrtThreadErrors *saved){host_crt_allocation_restore(saved);}
static HostCrtThreadErrors host_arena_result(const HostCrtThreadErrors *saved){return host_crt_allocation_result(saved);}
static uint64_t host_arena_refuse(int kind,const HostCrtThreadErrors *incoming,DWORD error){
    int code=error==ERROR_BUSY?EBUSY:error==ERROR_NOT_ENOUGH_MEMORY||error==ERROR_OUTOFMEMORY?ENOMEM:
            error==ERROR_INVALID_DATA||error==ERROR_INVALID_PARAMETER||error==ERROR_INVALID_FUNCTION?EINVAL:EACCES;
    if(kind==HOST_ARENA_POSIX_MEMALIGN||kind>=HOST_ARENA_MALLCTL){
        host_arena_restore(incoming);SetLastError(error);return (uint64_t)(unsigned)code;
    }
    return host_crt_allocation_refuse(incoming,error);
}
static uint64_t host_arena_original(HostImport *entry,const uintptr_t *a){
    switch(entry->kind){
        case HOST_ARENA_MALLOC:return (uintptr_t)((HostArenaMalloc)entry->original)((size_t)a[0]);
        case HOST_ARENA_CALLOC:return (uintptr_t)((HostArenaCalloc)entry->original)((size_t)a[0],(size_t)a[1]);
        case HOST_ARENA_REALLOC:return (uintptr_t)((HostArenaRealloc)entry->original)((void*)a[0],(size_t)a[1]);
        case HOST_ARENA_FREE:((HostArenaFree)entry->original)((void*)a[0]);return 0;
        case HOST_ARENA_MALLOCX:return (uintptr_t)((HostArenaMallocx)entry->original)((size_t)a[0],(int)a[1]);
        case HOST_ARENA_RALLOCX:return (uintptr_t)((HostArenaRallocx)entry->original)((void*)a[0],(size_t)a[1],(int)a[2]);
        case HOST_ARENA_XALLOCX:return ((HostArenaXallocx)entry->original)((void*)a[0],(size_t)a[1],(size_t)a[2],(int)a[3]);
        case HOST_ARENA_DALLOCX:((HostArenaDallocx)entry->original)((void*)a[0],(int)a[1]);return 0;
        case HOST_ARENA_SDALLOCX:((HostArenaSdallocx)entry->original)((void*)a[0],(size_t)a[1],(int)a[2]);return 0;
        case HOST_ARENA_SALLOCX:return ((HostArenaSallocx)entry->original)((const void*)a[0],(int)a[1]);
        case HOST_ARENA_USABLE_SIZE:return ((HostArenaUsable)entry->original)((const void*)a[0]);
        case HOST_ARENA_ALIGNED_ALLOC:return (uintptr_t)((HostArenaAligned)entry->original)((size_t)a[0],(size_t)a[1]);
        case HOST_ARENA_POSIX_MEMALIGN:return (unsigned)((HostArenaPosix)entry->original)((void**)a[0],(size_t)a[1],(size_t)a[2]);
        case HOST_ARENA_MALLCTL:return (unsigned)((HostArenaMallctl)entry->original)((const char*)a[0],(void*)a[1],(size_t*)a[2],(void*)a[3],(size_t)a[4]);
        case HOST_ARENA_MALLCTLBYMIB:return (unsigned)((HostArenaBymib)entry->original)((const size_t*)a[0],(size_t)a[1],(void*)a[2],(size_t*)a[3],(void*)a[4],(size_t)a[5]);
        case HOST_ARENA_MALLCTLNAMETOMIB:return (unsigned)((HostArenaTomib)entry->original)((const char*)a[0],(size_t*)a[1],(size_t*)a[2]);
        default:SetLastError(ERROR_INVALID_FUNCTION);return 0;
    }
}
static uint64_t host_arena_scoped_original(HostImport *entry,HostArenaProvider *provider,NativeThread *state,
        const uintptr_t *arguments,NativeHostMemoryCall *call,int cleanup,const HostCrtThreadErrors *incoming){
    if(!state)state=native_thread();
    if(!state||!call){host_arena_restore(incoming);return host_arena_original(entry,arguments);}
    NativeHostArenaOriginalScope scope={0};scope.image=provider->image;scope.original=entry->original;
    scope.release=(void*)provider->release;scope.entry=entry;scope.call=call;scope.thread=GetCurrentThreadId();scope.cleanup=cleanup;
    scope.previous=state->hostArenaOriginal;state->hostArenaOriginal=&scope;uint64_t result=0;
    __try {host_arena_restore(incoming);result=host_arena_original(entry,arguments);}
    __finally {DWORD outgoing=GetLastError();state->hostArenaOriginal=scope.previous;
        native_thread_try_release(state);SetLastError(outgoing);}
    return result;
}
static void host_arena_release_scoped_original(void *release,uint64_t address,NativeHostMemoryCall *call){
    NativeThread *state=native_thread();
    if(!state){((HostArenaFree)release)((void*)(uintptr_t)address);return;}
    /* The memory backend supplies its already selected, sealed allocation
     * token. No HostImport or new producer permission is manufactured here. */
    NativeHostArenaOriginalScope scope={0};scope.image=native_image(release);scope.original=scope.release=release;
    scope.call=call;scope.thread=GetCurrentThreadId();scope.cleanup=1;scope.previous=state->hostArenaOriginal;
    state->hostArenaOriginal=&scope;
    __try {((HostArenaFree)release)((void*)(uintptr_t)address);}
    __finally {DWORD outgoing=GetLastError();state->hostArenaOriginal=scope.previous;
        native_thread_try_release(state);SetLastError(outgoing);}
}
static DWORD host_arena_range(const void *pointer,size_t bytes,int restricted){
    if(!pointer||!bytes)return 0;int hit=host_memory_range(pointer,bytes,0,restricted);
    if(hit)return hit>0?ERROR_ACCESS_DENIED:restricted?ERROR_BUSY:ERROR_INVALID_DATA;
    int locked=restricted?TryAcquireSRWLockExclusive(&native_private_gate):(AcquireSRWLockExclusive(&native_private_gate),1);
    if(!locked)return ERROR_BUSY;hit=host_memory_output_locked(pointer,bytes);ReleaseSRWLockExclusive(&native_private_gate);
    return hit?hit>0?ERROR_ACCESS_DENIED:ERROR_INVALID_DATA:0;
}
static int host_arena_read_size(const void *pointer,size_t *value){
    if(!pointer)return 0;int ready=0;__try {*value=*(const size_t*)pointer;ready=1;}__except(EXCEPTION_EXECUTE_HANDLER){ready=0;}return ready;
}
static int host_arena_read_name(const char *pointer,char *name,size_t capacity){
    if(!pointer)return 0;int ready=0;__try {for(size_t i=0;i<capacity;i++){name[i]=pointer[i];if(!name[i]){ready=1;break;}}}
    __except(EXCEPTION_EXECUTE_HANDLER){ready=0;}return ready;
}
static int host_arena_parse_name(const char *name,uintptr_t *context){
    if(strncmp(name,"arena.",6))return NATIVE_MEMORY_HOST_ARENA_UNKNOWN;
    const char *cursor=name+6;uint64_t index=0;if(*cursor<'0'||*cursor>'9')return NATIVE_MEMORY_HOST_ARENA_UNKNOWN;
    do {unsigned digit=(unsigned)(*cursor++-'0');if(index>(UINT_MAX-digit)/10)return NATIVE_MEMORY_HOST_ARENA_UNKNOWN;index=index*10+digit;}while(*cursor>='0'&&*cursor<='9');
    if(*cursor++!='.')return NATIVE_MEMORY_HOST_ARENA_UNKNOWN;*context=(uintptr_t)index+1;
    if(!strcmp(cursor,"reset"))return NATIVE_MEMORY_HOST_ARENA_RESET;
    if(!strcmp(cursor,"destroy"))return NATIVE_MEMORY_HOST_CONTEXT_FREE;
    static const char *safe[]={"initialized","purge","decay","dss","dirty_decay_ms","muzzy_decay_ms","retain_grow_limit"};
    for(unsigned i=0;i<sizeof(safe)/sizeof(safe[0]);i++)if(!strcmp(cursor,safe[i]))return NATIVE_MEMORY_HOST_OBSERVE;
    return NATIVE_MEMORY_HOST_ARENA_UNKNOWN;
}
static int host_arena_name_operation(const char *name,uintptr_t *context){
    if(!strcmp(name,"arenas.create"))return NATIVE_MEMORY_HOST_CONTEXT_CREATE;
    if(!strncmp(name,"arena.",6))return host_arena_parse_name(name,context);
    if(!strncmp(name,"stats.",6)||!strncmp(name,"config.",7)||!strncmp(name,"opt.",4))return NATIVE_MEMORY_HOST_OBSERVE;
    static const char *safe[]={"version","epoch","thread.arena","thread.allocated","thread.deallocated","thread.allocatedp","thread.deallocatedp",
        "thread.tcache.enabled","thread.tcache.flush","tcache.create","tcache.flush","tcache.destroy","arenas.lookup","arenas.narenas",
        "arenas.nbins","arenas.nhbins","arenas.nlextents","arenas.quantum","arenas.page","arenas.tcache_max","arenas.dirty_decay_ms","arenas.muzzy_decay_ms"};
    for(unsigned i=0;i<sizeof(safe)/sizeof(safe[0]);i++)if(!strcmp(name,safe[i]))return NATIVE_MEMORY_HOST_OBSERVE;
    return NATIVE_MEMORY_HOST_ARENA_UNKNOWN;
}
static int host_arena_mib_operation(HostArenaProvider *provider,const size_t *mib,size_t count,uintptr_t *context){
    size_t canonical[HOST_ARENA_MIB_CAPACITY],length;
    const char *commands[]={"arena.0.reset","arena.0.destroy","arenas.create"};
    for(unsigned c=0;c<3;c++){
        length=HOST_ARENA_MIB_CAPACITY;
        if(provider->toMib(commands[c],canonical,&length)||length!=count)continue;
        if(c<2){if(count==3&&canonical[0]==mib[0]&&canonical[2]==mib[2]&&mib[1]<=UINT_MAX){
            *context=(uintptr_t)mib[1]+1;return c?NATIVE_MEMORY_HOST_CONTEXT_FREE:NATIVE_MEMORY_HOST_ARENA_RESET;}}
        else if(!memcmp(canonical,mib,count*sizeof(*mib)))return NATIVE_MEMORY_HOST_CONTEXT_CREATE;
    }
    const char *prefixes[]={"stats.allocated","config.debug","opt.abort"};
    for(unsigned c=0;c<3;c++){length=HOST_ARENA_MIB_CAPACITY;
        if(!provider->toMib(prefixes[c],canonical,&length)&&length&&count&&canonical[0]==mib[0])return NATIVE_MEMORY_HOST_OBSERVE;}
    const char *safe[]={"version","epoch","thread.arena","thread.allocated","thread.deallocated","thread.allocatedp","thread.deallocatedp",
        "thread.tcache.enabled","thread.tcache.flush","tcache.create","tcache.flush","tcache.destroy","arenas.lookup","arenas.narenas",
        "arenas.nbins","arenas.nhbins","arenas.nlextents","arenas.quantum","arenas.page","arenas.tcache_max","arenas.dirty_decay_ms","arenas.muzzy_decay_ms",
        "arena.0.initialized","arena.0.purge","arena.0.decay","arena.0.dss","arena.0.dirty_decay_ms","arena.0.muzzy_decay_ms","arena.0.retain_grow_limit"};
    for(unsigned c=0;c<sizeof(safe)/sizeof(safe[0]);c++){length=HOST_ARENA_MIB_CAPACITY;
        if(provider->toMib(safe[c],canonical,&length)||length!=count)continue;
        if(!strncmp(safe[c],"arena.",6)){
            if(count==3&&canonical[0]==mib[0]&&canonical[2]==mib[2])return NATIVE_MEMORY_HOST_OBSERVE;
        }else if(!memcmp(canonical,mib,count*sizeof(*mib)))return NATIVE_MEMORY_HOST_OBSERVE;
    }
    return NATIVE_MEMORY_HOST_ARENA_UNKNOWN;
}
/* Only an actual successful allocation result is passed to these observers.
 * The xallocx return already is its actual resulting extent. */
static int host_arena_result_extent(HostArenaProvider *provider,uint64_t address,uint64_t *bytes,uintptr_t *context,int extentReturned){
    int ready=0;__try {
        if(!extentReturned)*bytes=(uint64_t)provider->usable((const void*)(uintptr_t)address,0);
        unsigned arena=0;size_t length=sizeof(arena);void *pointer=(void*)(uintptr_t)address;
        int status=provider->control("arenas.lookup",&arena,&length,&pointer,sizeof(pointer));
        if(!status&&length==sizeof(arena)&&*bytes){*context=(uintptr_t)arena+1;ready=1;}
    } __except(EXCEPTION_EXECUTE_HANDLER){ready=0;}
    return ready;
}
static int host_arena_zero_frees(HostArenaProvider *provider){
    int mode=-1;__try {
        const char *option=NULL;size_t length=sizeof(option);
        int status=provider->control("opt.zero_realloc",&option,&length,NULL,0);
        if(!status&&length==sizeof(option)&&option){if(!strcmp(option,"free"))mode=1;else if(!strcmp(option,"alloc"))mode=0;}
        else if(status==ENOENT){
            const char *version=NULL;length=sizeof(version);
            if(!provider->control("version",&version,&length,NULL,0)&&length==sizeof(version)&&version
                    &&!strncmp(version,"5.2.",4))mode=1; /* 5.2 public realloc(p,0) contract. */
        }
    } __except(EXCEPTION_EXECUTE_HANDLER){mode=-1;}
    return mode;
}
static uint64_t host_native_arena_allocation(HostImport *entry,void *caller,uintptr_t *arguments){
    HostCrtThreadErrors incoming;int errorsReady=host_arena_errors(entry,&incoming);
    HostArenaProvider *provider=host_arena_target(entry->original,entry->kind);
    if(!provider)return host_arena_refuse(entry->kind,&incoming,ERROR_INVALID_FUNCTION);
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    int restricted=state&&(state->heapCallback||state->toolingRestricted||state->vmDetaching||state->vmThreadEnding
            ||state->hostUnloadImage||state->hostRelease||state->hostLoad&&state->hostLoad->calling);
    HostImport *consumed=restricted?entry:host_consumed_entry(entry,state,caller);
    if(consumed->kind!=entry->kind||consumed->original!=entry->original)return host_arena_refuse(entry->kind,&incoming,ERROR_INVALID_DATA);
    entry=consumed;OwnerLink *owners=NULL;int unknown=0;
    if(!host_entry_sources_query(entry,state,caller,&owners,NULL,1,&unknown)){
        DWORD error=GetLastError();native_owner_links_free(owners);return host_arena_refuse(entry->kind,&incoming,error);
    }
    JNIEnv *env=host_memory_environment(restricted);uintptr_t actual[6]={0};
    unsigned argumentCount=entry->kind==HOST_ARENA_MALLCTLBYMIB?6:entry->kind==HOST_ARENA_MALLCTL?5:entry->kind==HOST_ARENA_XALLOCX?4:
            entry->kind==HOST_ARENA_RALLOCX||entry->kind==HOST_ARENA_SDALLOCX||entry->kind==HOST_ARENA_POSIX_MEMALIGN||entry->kind==HOST_ARENA_MALLCTLNAMETOMIB?3:
            entry->kind==HOST_ARENA_CALLOC||entry->kind==HOST_ARENA_REALLOC||entry->kind==HOST_ARENA_MALLOCX||entry->kind==HOST_ARENA_DALLOCX
            ||entry->kind==HOST_ARENA_SALLOCX||entry->kind==HOST_ARENA_ALIGNED_ALLOC?2:1;
    memcpy(actual,arguments,argumentCount*sizeof(*actual));
    int kind=entry->kind,control=kind>=HOST_ARENA_MALLCTL,query=kind==HOST_ARENA_SALLOCX||kind==HOST_ARENA_USABLE_SIZE;
    int replacing=kind==HOST_ARENA_REALLOC||kind==HOST_ARENA_RALLOCX||kind==HOST_ARENA_XALLOCX;
    int releasing=kind==HOST_ARENA_FREE||kind==HOST_ARENA_DALLOCX||kind==HOST_ARENA_SDALLOCX;
    uint64_t address=replacing||releasing||query?(uint64_t)arguments[0]:0;
    int operation=replacing?NATIVE_MEMORY_HOST_REALLOCATE:releasing?NATIVE_MEMORY_HOST_FREE:query||control?NATIVE_MEMORY_HOST_OBSERVE:NATIVE_MEMORY_HOST_ALLOCATE;
    uintptr_t context=0;int zeroMode=-1;DWORD refusal=0;
    char name[HOST_ARENA_NAME_CAPACITY];size_t mib[HOST_ARENA_MIB_CAPACITY];
    int possibleCleanup=address&&(releasing||kind==HOST_ARENA_REALLOC&&!actual[1])&&host_memory_cleanup_scope(entry,state);
    if(native_owner_stopped(owners)&&!possibleCleanup)refusal=ERROR_ACCESS_DENIED;
    else if(unknown&&(restricted||native_has_producer(owners))&&!possibleCleanup)refusal=ERROR_INVALID_DATA;
    if(!refusal&&address)refusal=host_arena_range((const void*)(uintptr_t)address,1,restricted);
    if(!refusal&&kind==HOST_ARENA_POSIX_MEMALIGN)refusal=host_arena_range((const void*)actual[0],sizeof(void*),restricted);
    if(!refusal&&control){
        if(kind!=HOST_ARENA_MALLCTLBYMIB){
            refusal=host_arena_range((const void*)actual[0],1,restricted);
            int captured=!refusal&&host_arena_read_name((const char*)actual[0],name,sizeof(name));
            if(captured){refusal=host_arena_range((const void*)actual[0],strlen(name)+1,restricted);actual[0]=(uintptr_t)name;}
            if(kind==HOST_ARENA_MALLCTL)operation=captured?host_arena_name_operation(name,&context):NATIVE_MEMORY_HOST_ARENA_UNKNOWN;
        }else {
            size_t count=(size_t)actual[1];
            refusal=host_arena_range((const void*)actual[0],count&&count<=SIZE_MAX/sizeof(size_t)?count*sizeof(size_t):1,restricted);
            int captured=0;
            if(!refusal&&count<=HOST_ARENA_MIB_CAPACITY&&actual[0]){__try {memcpy(mib,(const void*)actual[0],count*sizeof(size_t));captured=1;}__except(EXCEPTION_EXECUTE_HANDLER){captured=0;}}
            if(captured){actual[0]=(uintptr_t)mib;operation=host_arena_mib_operation(provider,mib,count,&context);}
            else operation=NATIVE_MEMORY_HOST_ARENA_UNKNOWN;
        }
        if(!refusal&&kind==HOST_ARENA_MALLCTLNAMETOMIB){
            refusal=host_arena_range((const void*)actual[2],sizeof(size_t),restricted);size_t capacity=0;
            if(!refusal&&host_arena_read_size((const void*)actual[2],&capacity))
                refusal=host_arena_range((const void*)actual[1],capacity<=SIZE_MAX/sizeof(size_t)?capacity*sizeof(size_t):SIZE_MAX,restricted);
        }else if(!refusal){unsigned first=kind==HOST_ARENA_MALLCTL?1:2;size_t capacity=0;
            refusal=host_arena_range((const void*)actual[first+1],sizeof(size_t),restricted);
            if(!refusal&&host_arena_read_size((const void*)actual[first+1],&capacity))refusal=host_arena_range((const void*)actual[first],capacity,restricted);
            if(!refusal)refusal=host_arena_range((const void*)actual[first+2],(size_t)actual[first+3],restricted);
        }
    }
    if(operation==NATIVE_MEMORY_HOST_ARENA_UNKNOWN)context=0;
    if(!refusal&&kind==HOST_ARENA_REALLOC&&address&&!actual[1])zeroMode=host_arena_zero_frees(provider);
    int cleanup=address&&(releasing||kind==HOST_ARENA_REALLOC&&!actual[1]&&zeroMode==1)&&host_memory_cleanup_scope(entry,state);
    NativeHostMemoryCall *call=NULL;uint64_t result=0;int originalReturned=0,rawPass=0;
    HostCrtThreadErrors returned=incoming;
    __try {
        if(!refusal&&native_owner_stopped(owners)&&!cleanup)refusal=ERROR_ACCESS_DENIED;
        else if(!refusal&&unknown&&(restricted||native_has_producer(owners))&&!cleanup)refusal=ERROR_INVALID_DATA;
        if(!refusal){int denied=0;
            call=native_memory_host_arena_prepare(env,(void*)provider->release,address,context,operation,owners,unknown,&denied);
            if(denied)refusal=ERROR_BUSY;else if(!native_memory_host_sources(call,&owners,&unknown))refusal=ERROR_NOT_ENOUGH_MEMORY;
        }
        int cleanupKnown=cleanup&&native_memory_host_cleanup(call)&&host_memory_cleanup_scope(entry,state);
        if(!refusal&&native_owner_stopped(owners)&&!cleanupKnown)refusal=ERROR_ACCESS_DENIED;
        else if(!refusal&&unknown&&(restricted||native_has_producer(owners))&&!cleanupKnown)refusal=ERROR_INVALID_DATA;
        if(refusal){if(call){native_memory_host_cancel(call);call=NULL;}
            result=host_arena_refuse(kind,&incoming,refusal);returned=host_arena_result(&incoming);}
        else if(!errorsReady){
            if(call){native_memory_host_abort(env,call);call=NULL;}
            int denied=0;NativeHostMemoryCall *opaque=native_memory_host_arena_prepare(env,(void*)provider->release,0,0,NATIVE_MEMORY_HOST_ARENA_UNKNOWN,owners,1,&denied);
            if(opaque)native_memory_host_arena_complete(env,opaque,0,0,0,0,0);
            native_memory_host_unobserved(owners);native_owner_links_free(owners);owners=NULL;
            InterlockedExchange(&entry->image->privateOpaque,1);rawPass=1;host_arena_restore(&incoming);result=host_arena_original(entry,actual);
        }else {
            result=host_arena_scoped_original(entry,provider,state,actual,call,cleanupKnown,&incoming);
            returned=host_arena_result(&incoming);originalReturned=1;
            uint64_t pointer=0,bytes=0;uintptr_t arena=0;int completed=0,released=0;
            if(control){
                completed=result==0;
                if(operation==NATIVE_MEMORY_HOST_CONTEXT_CREATE&&completed){
                    unsigned first=kind==HOST_ARENA_MALLCTL?1:2;size_t length=0;
                    int observed=0;__try {if(actual[first]&&host_arena_read_size((const void*)actual[first+1],&length)&&length==sizeof(unsigned)){
                        arena=(uintptr_t)*(const unsigned*)actual[first]+1;observed=1;}}
                    __except(EXCEPTION_EXECUTE_HANDLER){observed=0;}
                    if(!observed){native_memory_host_abort(env,call);call=NULL;native_memory_host_unobserved(owners);}
                }
                released=completed&&(operation==NATIVE_MEMORY_HOST_CONTEXT_FREE||operation==NATIVE_MEMORY_HOST_ARENA_RESET);
            }else if(releasing){completed=released=1;}
            else if(query){completed=1;}
            else if(kind==HOST_ARENA_XALLOCX){
                if(actual[1]&&actual[2]<=SIZE_MAX-actual[1]&&result>=actual[1]){pointer=address;bytes=result;completed=1;
                    if(!host_arena_result_extent(provider,pointer,&bytes,&arena,1)){bytes=UINT64_MAX;arena=0;}}
            }else {
                if(kind==HOST_ARENA_POSIX_MEMALIGN){
                    if(!result){__try {pointer=(uintptr_t)*(void**)actual[0];}__except(EXCEPTION_EXECUTE_HANDLER){pointer=0;native_memory_host_unobserved(owners);}}
                }else pointer=result;
                if(pointer){completed=1;if(!host_arena_result_extent(provider,pointer,&bytes,&arena,0)){bytes=UINT64_MAX;arena=0;}}
                else if(kind==HOST_ARENA_REALLOC&&address&&!actual[1]){
                    if(zeroMode==1)completed=released=1;
                    else if(zeroMode<0){native_memory_host_abort(env,call);call=NULL;}
                }
            }
            if(pointer)InterlockedExchange(&entry->image->privateOpaque,1);
            if(call){native_memory_host_arena_complete(env,call,pointer,bytes,arena,completed,released);call=NULL;}
        }
    } __finally {
        if(!rawPass){if(!originalReturned)returned=host_arena_result(&incoming);
            if(call)native_memory_host_abort(env,call);native_owner_links_free(owners);host_arena_restore(&returned);}
    }
    return result;
}
