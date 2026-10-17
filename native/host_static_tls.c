/* Compiler static TLS for an actually observed PE64 image and the current
 * Windows x64 thread only. The image template is never a thread instance.
 * TlsSlots/TlsExpansionSlots and TlsGetValue are the separate Win32 TLS API.
 *
 * The supported x64 TEB prefix is Reserved1[11] (0x58) in the local Windows
 * SDK/mingw winternl.h. NtCurrentTeb is supplied by winnt.h; LLVM's Windows
 * ASan and Go's Windows amd64 runtime use the same static TLS vector field.
 * This OS-internal layout is not a portable Windows ABI. Other architectures,
 * inaccessible/changing metadata and restricted loader callbacks stay opaque.
 *
 * https://learn.microsoft.com/en-us/windows/win32/debug/pe-format#the-tls-section
 * https://learn.microsoft.com/en-us/windows/win32/api/winternl/ns-winternl-teb
 * https://github.com/llvm/llvm-project/blob/main/compiler-rt/lib/asan/asan_win.cpp
 * https://github.com/golang/go/blob/master/src/runtime/go_tls.h
 */
#include <winternl.h>

typedef struct HostStaticTlsThread {
    HANDLE monitor;int ended,gap;
    struct HostStaticTlsThread *next;
} HostStaticTlsThread;

typedef struct HostStaticTlsSpan {
    HostStaticTlsThread *thread;
    uint64_t address,bytes;OwnerLink *owners;int unknown,gap,closed;
    struct HostStaticTlsSpan *next;
} HostStaticTlsSpan;

typedef struct HostStaticTlsImage {
    HostImage *image;HostStaticTlsSpan *spans;OwnerLink *owners;
    uint64_t templateAddress,templateBytes,indexAddress,bytes;
    DWORD index,alignment;int parsed,present,failed,gap,preparing,closed;
    struct HostStaticTlsImage *next;
} HostStaticTlsImage;

/* host_imports owns these directories. Monitor installation/return also takes
 * host_records in the existing host_imports -> host_records order, so the
 * observed handle consumers cannot close/reuse an internal witness. Records
 * remain tombstones: no later OS thread or same-base image reuses a tag. */
static HostStaticTlsThread *host_static_tls_threads;
static HostStaticTlsImage *host_static_tls_images;

static int host_static_tls_restricted(const NativeThread *state){
    return state&&(state->heapCallback||state->toolingRestricted||state->vmDetaching||state->vmThreadEnding
            ||state->hostUnloadImage||state->hostRelease||state->hostLoad&&state->hostLoad->calling);
}
static HostStaticTlsImage *host_static_tls_image_locked(HostImage *image,int create){
    for(HostStaticTlsImage *entry=host_static_tls_images;entry;entry=entry->next)if(entry->image==image)return entry;
    if(!create)return NULL;HostStaticTlsImage *entry=(HostStaticTlsImage*)calloc(1,sizeof(*entry));if(!entry)return NULL;
    entry->image=image;entry->next=host_static_tls_images;host_static_tls_images=entry;return entry;
}
/* Validate only the exact supplied chain/range. There is no heap enumeration,
 * object decoding, allocation ownership inference or read of the TLS payload. */
static int host_static_tls_range(uint64_t address,uint64_t bytes,int privateOnly){
    if(!address||!bytes||bytes>UINTPTR_MAX-address)return 0;
    uint64_t cursor=address,end=address+bytes;
    while(cursor<end){MEMORY_BASIC_INFORMATION region={0};
        if(VirtualQuery((void*)(uintptr_t)cursor,&region,sizeof(region))!=sizeof(region)
                ||region.State!=MEM_COMMIT||!region.RegionSize||(region.Protect&(PAGE_GUARD|PAGE_NOACCESS))
                ||privateOnly&&region.Type!=MEM_PRIVATE)return 0;
        DWORD protection=region.Protect&0xff;
        if(protection!=PAGE_READONLY&&protection!=PAGE_READWRITE&&protection!=PAGE_WRITECOPY
                &&protection!=PAGE_EXECUTE_READ&&protection!=PAGE_EXECUTE_READWRITE&&protection!=PAGE_EXECUTE_WRITECOPY)return 0;
        uint64_t base=(uint64_t)(uintptr_t)region.BaseAddress;
        if(base>cursor||region.RegionSize>UINT64_MAX-base||base+region.RegionSize<=cursor)return 0;
        cursor=base+region.RegionSize;
    }return 1;
}
static int host_static_tls_copy(uint64_t address,void *out,size_t bytes){
    if(!host_static_tls_range(address,bytes,0))return 0;SIZE_T copied=0;
    return host_real_read_process_memory(GetCurrentProcess(),(void*)(uintptr_t)address,out,bytes,&copied)&&copied==bytes;
}
static int host_static_tls_image_range(HostImage *image,uint64_t size,uint64_t address,uint64_t bytes){
    uint64_t base=(uint64_t)(uintptr_t)image->handle;
    return address>=base&&address-base<=size&&bytes<=size-(address-base)&&host_static_range(image->handle,address,bytes);
}
static int host_static_tls_parse(HostStaticTlsImage *entry){
    HostImage *image=entry->image;IMAGE_DOS_HEADER dos={0};IMAGE_NT_HEADERS64 nt={0};
    uint64_t base=(uint64_t)(uintptr_t)image->handle;
    if(!host_static_tls_image_range(image,UINT32_MAX,base,sizeof(dos))
            ||!host_static_tls_copy(base,&dos,sizeof(dos))||dos.e_magic!=IMAGE_DOS_SIGNATURE||dos.e_lfanew<=0
            ||(uint64_t)dos.e_lfanew>UINTPTR_MAX-base)return 0;
    uint64_t ntAddress=base+(uint64_t)dos.e_lfanew;
    if(!host_static_tls_image_range(image,UINT32_MAX,ntAddress,sizeof(nt))
            ||!host_static_tls_copy(ntAddress,&nt,sizeof(nt))||nt.Signature!=IMAGE_NT_SIGNATURE
            ||nt.FileHeader.Machine!=IMAGE_FILE_MACHINE_AMD64||nt.OptionalHeader.Magic!=IMAGE_NT_OPTIONAL_HDR64_MAGIC
            ||nt.FileHeader.SizeOfOptionalHeader<sizeof(IMAGE_OPTIONAL_HEADER64)||!nt.OptionalHeader.SizeOfImage)return 0;
    uint64_t size=nt.OptionalHeader.SizeOfImage;
    if(!host_static_tls_image_range(image,size,ntAddress,sizeof(nt)))return 0;
    if(nt.OptionalHeader.NumberOfRvaAndSizes<=IMAGE_DIRECTORY_ENTRY_TLS)return 1;
    IMAGE_DATA_DIRECTORY directory=nt.OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_TLS];
    if(!directory.VirtualAddress&&!directory.Size)return 1;
    if(!directory.VirtualAddress||directory.Size<sizeof(IMAGE_TLS_DIRECTORY64)
            ||directory.VirtualAddress>=size||directory.Size>size-directory.VirtualAddress
            ||directory.VirtualAddress>UINTPTR_MAX-base)return 0;
    IMAGE_TLS_DIRECTORY64 tls={0};uint64_t address=base+directory.VirtualAddress;
    if(!host_static_tls_image_range(image,size,address,sizeof(tls))||!host_static_tls_copy(address,&tls,sizeof(tls))
            ||tls.EndAddressOfRawData<tls.StartAddressOfRawData)return 0;
    uint64_t initialized=tls.EndAddressOfRawData-tls.StartAddressOfRawData;
    if(initialized&&!host_static_tls_image_range(image,size,tls.StartAddressOfRawData,initialized))return 0;
    if(!host_static_tls_image_range(image,size,tls.AddressOfIndex,sizeof(DWORD))
            ||!host_static_tls_copy(tls.AddressOfIndex,&entry->index,sizeof(entry->index))
            ||initialized>UINT64_MAX-tls.SizeOfZeroFill)return 0;
    /* The initialized bytes plus SizeOfZeroFill are the exact module payload.
     * Alignment padding and neighboring loader allocations are not this span. */
    entry->templateAddress=tls.StartAddressOfRawData;entry->templateBytes=initialized;
    entry->indexAddress=tls.AddressOfIndex;entry->bytes=initialized+tls.SizeOfZeroFill;
    entry->alignment=(tls.Characteristics>>20)&15;
    if(entry->alignment==15)return 0;
    entry->present=1;return 1;
}
static HostStaticTlsThread *host_static_tls_thread_locked(void){
    if(!__atomic_load_n(&host_compare_handles,__ATOMIC_ACQUIRE))return NULL;
    for(HostStaticTlsThread *thread=host_static_tls_threads;thread;thread=thread->next)
        if(!thread->ended&&thread->monitor&&host_same_handle(thread->monitor,GetCurrentThread()))return thread;
    HostStaticTlsThread *thread=(HostStaticTlsThread*)calloc(1,sizeof(*thread));if(!thread)return NULL;
    AcquireSRWLockExclusive(&host_records);HANDLE monitor=NULL;
    BOOL copied=host_real_duplicate(GetCurrentProcess(),GetCurrentThread(),GetCurrentProcess(),&monitor,SYNCHRONIZE,FALSE,0);
    if(copied){thread->monitor=monitor;
        thread->next=host_static_tls_threads;host_static_tls_threads=thread;}
    ReleaseSRWLockExclusive(&host_records);
    if(!copied){free(thread);return NULL;}native_thread_worker_wake();return thread;
}
/* host_imports is exclusive and the owners were captured from an actual
 * registered Binding/load/callback/worker. No address or module name supplies
 * owners here. The return value describes observation, never call admission. */
static int host_static_tls_observed_locked(NativeThread *state,HostImage *image,OwnerLink *owners,int unknown){
    if(!image||(!native_has_producer(owners)&&!native_has_core_state(owners)))return 1;
    InterlockedExchange(&image->privateOpaque,1);
    if(host_static_tls_restricted(state))return 0;
    HostStaticTlsImage *entry=host_static_tls_image_locked(image,1);if(!entry)return 0;
    if(!native_owner_merge(&entry->owners,owners))entry->gap=1;
    if(!image->live||image->releasing||entry->preparing||entry->closed){entry->gap=1;return 0;}
    if(!entry->parsed){entry->parsed=1;entry->failed=!host_static_tls_parse(entry);}
    if(entry->failed){entry->gap=1;return 0;}if(!entry->present||!entry->bytes)return 1;
#if defined(_WIN64) && (defined(__x86_64__) || defined(_M_X64)) && !defined(_M_ARM64EC)
    /* The local headers publish this prefix as Reserved1[12]. Its last cell
     * is the x64 compiler-static TLS vector, not the dynamic API's TlsSlots.
     * Compile-time bounds describe the supported layout; safe-copy below can
     * still decline an inaccessible or concurrently replaced vector. */
    _Static_assert(sizeof(void*)==8&&offsetof(TEB,Reserved1)+11*sizeof(void*)==0x58,"Windows x64 TEB prefix required");
    uint64_t teb=(uint64_t)(uintptr_t)NtCurrentTeb(),self=0,vector=0,block=0,confirmedVector=0,confirmedBlock=0;
    DWORD index=0,confirmedIndex=0;
    if(teb>UINTPTR_MAX-(offsetof(TEB,Reserved1)+11*sizeof(void*))
            ||!host_static_tls_copy(teb+offsetof(NT_TIB,Self),&self,sizeof(self))||self!=teb
            ||!host_static_tls_copy(entry->indexAddress,&index,sizeof(index))||index!=entry->index
            ||!host_static_tls_copy(teb+offsetof(TEB,Reserved1)+11*sizeof(void*),&vector,sizeof(vector))||!vector
            ||(uint64_t)index>(UINTPTR_MAX-vector)/sizeof(void*)
            ||!host_static_tls_copy(vector+(uint64_t)index*sizeof(void*),&block,sizeof(block))
            ||!host_static_tls_range(block,entry->bytes,1)
            ||entry->alignment&&(block&((UINT64_C(1)<<(entry->alignment-1))-1))
            ||!host_static_tls_copy(entry->indexAddress,&confirmedIndex,sizeof(confirmedIndex))||confirmedIndex!=index
            ||!host_static_tls_copy(teb+offsetof(TEB,Reserved1)+11*sizeof(void*),&confirmedVector,sizeof(confirmedVector))||confirmedVector!=vector
            ||!host_static_tls_copy(vector+(uint64_t)index*sizeof(void*),&confirmedBlock,sizeof(confirmedBlock))||confirmedBlock!=block){entry->gap=1;return 0;}
    HostStaticTlsThread *thread=host_static_tls_thread_locked();if(!thread){entry->gap=1;return 0;}
    HostStaticTlsSpan *span=NULL;int changed=0;
    for(HostStaticTlsSpan *old=entry->spans;old;old=old->next)if(old->thread==thread&&!old->closed){
        if(old->address==block&&old->bytes==entry->bytes)span=old;else {old->gap=1;changed=1;}
    }
    /* A different pointer in the same live image/thread has no terminal
     * witness for the former bytes. Retain both distinct tags as opaque; do
     * not claim that a pointer change freed or replaced an earlier instance. */
    if(!span){span=(HostStaticTlsSpan*)calloc(1,sizeof(*span));if(!span){entry->gap=1;return 0;}
        span->thread=thread;span->address=block;span->bytes=entry->bytes;
        span->next=entry->spans;entry->spans=span;}
    if(!native_owner_merge(&span->owners,owners))span->gap=1;
    span->unknown|=unknown;span->gap|=changed;
    /* native_memory_image_span always creates borrowed opaque storage, with
     * no independent release function. Direct CPU reads/writes stay unknown. */
    native_memory_image_span(NULL,span,span->address,span->bytes,span->owners,span->unknown||span->gap);
    return !span->gap;
#else
    entry->gap=1;return 0;
#endif
}
static int host_static_tls_observe(NativeThread *state,HostImage *image,OwnerLink *owners,int unknown){
    DWORD incoming=GetLastError();
    if(!image||(!native_has_producer(owners)&&!native_has_core_state(owners))){SetLastError(incoming);return 1;}
    InterlockedExchange(&image->privateOpaque,1);
    /* DllMain/TLS and restricted JVMTI callbacks must not enter the loader,
     * call JNI, or wait on directory locks. A later real normal-context scope
     * may observe its own thread, never impersonate this callback's thread. */
    if(host_static_tls_restricted(state)){InterlockedExchange(&image->staticTlsUnobserved,1);SetLastError(incoming);return 0;}
    AcquireSRWLockExclusive(&host_imports);
    int observed=host_static_tls_observed_locked(state,image,owners,unknown);
    ReleaseSRWLockExclusive(&host_imports);SetLastError(incoming);return observed;
}
/* host_imports is exclusive. Every observed thread span for this exact image
 * participates, including spans whose OS thread has not ended. Alias/read
 * receipts in memory.c reject preparation and cancel the complete attempt. */
static int host_static_tls_image_prepare(HostImage *image,JNIEnv *env){
    if(!image)return 1;HostStaticTlsImage *entry=host_static_tls_image_locked(image,1);
    if(!entry)return 0;if(entry->closed)return 1;
    for(HostStaticTlsSpan *span=entry->spans;span;span=span->next)if(!span->closed&&!native_memory_image_prepare(env,span)){
        for(HostStaticTlsSpan *undo=entry->spans;undo;undo=undo->next)if(!undo->closed)native_memory_image_cancel(env,undo);
        entry->preparing=0;return 0;
    }
    entry->preparing=1;return 1;
}
static void host_static_tls_image_cancel(HostImage *image,JNIEnv *env){
    HostStaticTlsImage *entry=host_static_tls_image_locked(image,0);if(!entry||entry->closed)return;
    entry->preparing=0;
    for(HostStaticTlsSpan *span=entry->spans;span;span=span->next)if(!span->closed)native_memory_image_cancel(env,span);
}
static void host_static_tls_image_closed(HostImage *image,JNIEnv *env){
    HostStaticTlsImage *entry=host_static_tls_image_locked(image,0);if(!entry||entry->closed)return;
    entry->closed=entry->preparing=1;
    for(HostStaticTlsSpan *span=entry->spans;span;span=span->next)if(!span->closed){
        span->closed=1;native_memory_image_retire(env,span);
    }
}
/* The normal handle consumer holds host_imports and host_records throughout
 * lookup and the real OS call, matching monitor install/close above/below. */
static int host_static_tls_internal_handle(HANDLE handle){
    for(HostStaticTlsThread *thread=host_static_tls_threads;thread;thread=thread->next)if(thread->monitor==handle)return 1;
    return 0;
}
static int host_static_tls_pending(void){
    int pending=0;AcquireSRWLockShared(&host_imports);
    for(HostStaticTlsThread *thread=host_static_tls_threads;thread&&!pending;thread=thread->next)
        pending=thread->monitor&&!thread->ended&&!thread->gap;
    ReleaseSRWLockShared(&host_imports);return pending;
}
static void host_static_tls_reap(JNIEnv **environment){
    if(!environment||!*environment)return;DWORD incoming=GetLastError();
    AcquireSRWLockExclusive(&host_imports);AcquireSRWLockExclusive(&host_records);
    __try {
    for(HostStaticTlsThread *thread=host_static_tls_threads;thread;thread=thread->next){
        if(!thread->monitor||thread->ended)continue;DWORD status=WaitForSingleObject(thread->monitor,0);
        if(status!=WAIT_OBJECT_0){if(status==WAIT_FAILED)thread->gap=1;continue;}
        thread->ended=1;
        /* JVM detach, ThreadEnd and TLS callbacks are not terminal witnesses.
         * This exact signaled OS thread only retires its own independent tags.
         * It does not prepare/retire the image or another thread's payload. */
        for(HostStaticTlsImage *entry=host_static_tls_images;entry;entry=entry->next)
            for(HostStaticTlsSpan *span=entry->spans;span;span=span->next)if(span->thread==thread&&!span->closed){
                span->closed=1;native_memory_image_retire(*environment,span);
            }
        if(host_real_close(thread->monitor))thread->monitor=NULL;else thread->gap=1;
    }
    } __finally {ReleaseSRWLockExclusive(&host_records);ReleaseSRWLockExclusive(&host_imports);SetLastError(incoming);}
}
static void host_static_tls_state(JNIEnv *env,jobject module,jlong *values){
    AcquireSRWLockShared(&host_imports);
    for(HostStaticTlsImage *entry=host_static_tls_images;entry;entry=entry->next){
        if(!entry->closed&&(entry->failed||entry->gap||InterlockedCompareExchange(&entry->image->staticTlsUnobserved,0,0))
                &&native_owner_matches(env,entry->owners,module))values[38]++;
        for(HostStaticTlsSpan *span=entry->spans;span;span=span->next)if(!span->closed&&native_owner_matches(env,span->owners,module)){
            values[37]++;if(span->unknown||span->gap||span->thread->gap)values[38]++;
        }
    }
    ReleaseSRWLockShared(&host_imports);
}
