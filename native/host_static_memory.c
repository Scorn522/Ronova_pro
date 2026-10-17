/* Writable PE storage of one observed image incarnation. These are borrowed
 * native bytes, not decoded C/C++ objects or individual release rights. */
typedef struct HostStaticSpan {uint64_t address,bytes;struct HostStaticSpan *next;} HostStaticSpan;
typedef struct HostStaticMemory {HostStaticSpan *spans;int parsed,failed,closed;} HostStaticMemory;

static JNIEnv *host_static_environment(void){
    JNIEnv *env=NULL;DWORD error=GetLastError();
    if(native_vm&&native_vm_original.GetEnv)native_vm_original.GetEnv(native_vm,(void**)&env,JNI_VERSION_1_8);
    SetLastError(error);return env;
}
static int host_static_copy(HMODULE image,uint64_t offset,void *out,size_t bytes,uint64_t size){
    uintptr_t base=(uintptr_t)image;
    if(offset>size||bytes>size-offset||offset>UINTPTR_MAX-base||bytes>UINTPTR_MAX-(base+(uintptr_t)offset))return 0;
    SIZE_T copied=0;return host_real_read_process_memory(GetCurrentProcess(),(void*)(base+(uintptr_t)offset),out,bytes,&copied)&&copied==bytes;
}
static int host_static_range(HMODULE image,uint64_t address,uint64_t bytes){
    if(!bytes||bytes-1>UINTPTR_MAX-address)return 0;uint64_t end=address+bytes,cursor=address;
    while(cursor<end){MEMORY_BASIC_INFORMATION region={0};
        if(VirtualQuery((void*)(uintptr_t)cursor,&region,sizeof(region))!=sizeof(region)||region.AllocationBase!=image
                ||region.Type!=MEM_IMAGE||region.State!=MEM_COMMIT||!region.RegionSize)return 0;
        uint64_t base=(uint64_t)(uintptr_t)region.BaseAddress;
        if(base>cursor||region.RegionSize>UINT64_MAX-base||base+region.RegionSize<=cursor)return 0;
        cursor=base+region.RegionSize;
    }return 1;
}
#include "host_static_tls.c"
static int host_static_parse(HostImage *image,HostStaticMemory *memory){
    IMAGE_DOS_HEADER dos={0};IMAGE_NT_HEADERS64 nt={0};
    if(!host_static_copy(image->handle,0,&dos,sizeof(dos),sizeof(dos))||dos.e_magic!=IMAGE_DOS_SIGNATURE||dos.e_lfanew<=0)return 0;
    MEMORY_BASIC_INFORMATION region={0};
    if(VirtualQuery(image->handle,&region,sizeof(region))!=sizeof(region)||region.AllocationBase!=image->handle||region.Type!=MEM_IMAGE)return 0;
    if(!host_static_copy(image->handle,(uint64_t)dos.e_lfanew,&nt,sizeof(nt),UINT32_MAX)
            ||nt.Signature!=IMAGE_NT_SIGNATURE||nt.OptionalHeader.Magic!=IMAGE_NT_OPTIONAL_HDR64_MAGIC
            ||nt.FileHeader.SizeOfOptionalHeader<sizeof(IMAGE_OPTIONAL_HEADER64)||!nt.OptionalHeader.SizeOfImage)return 0;
    uint64_t size=nt.OptionalHeader.SizeOfImage;
    uint64_t first=(uint64_t)dos.e_lfanew+offsetof(IMAGE_NT_HEADERS64,OptionalHeader)+nt.FileHeader.SizeOfOptionalHeader;
    if(!nt.FileHeader.NumberOfSections||nt.FileHeader.NumberOfSections>4096||first>size
            ||(uint64_t)nt.FileHeader.NumberOfSections*sizeof(IMAGE_SECTION_HEADER)>size-first)return 0;
    for(unsigned index=0;index<nt.FileHeader.NumberOfSections;index++){
        IMAGE_SECTION_HEADER section={0};
        if(!host_static_copy(image->handle,first+(uint64_t)index*sizeof(section),&section,sizeof(section),size))return 0;
        if(!(section.Characteristics&IMAGE_SCN_MEM_WRITE)||(section.Characteristics&(IMAGE_SCN_MEM_EXECUTE|IMAGE_SCN_MEM_DISCARDABLE))
                ||!(section.Characteristics&(IMAGE_SCN_CNT_INITIALIZED_DATA|IMAGE_SCN_CNT_UNINITIALIZED_DATA)))continue;
        uint64_t bytes=section.Misc.VirtualSize?section.Misc.VirtualSize:section.SizeOfRawData,offset=section.VirtualAddress;
        if(!bytes)continue;if(offset>=size||bytes>size-offset||offset>UINTPTR_MAX-(uintptr_t)image->handle)return 0;
        if(!host_static_range(image->handle,(uint64_t)(uintptr_t)image->handle+offset,bytes))return 0;
        HostStaticSpan *span=(HostStaticSpan*)calloc(1,sizeof(*span));if(!span)return 0;
        span->address=(uint64_t)(uintptr_t)image->handle+offset;span->bytes=bytes;span->next=memory->spans;memory->spans=span;
    }return 1;
}
/* host_imports is held. Only actual load receipts and actual JNI image bindings
 * contribute owners; a numeric module/address or a file name contributes none. */
static void host_static_image_observed(HostImage *image){
    if(!image||!image->live||image->releasing)return;
    OwnerLink *owners=NULL;int unknown=0,ready=1;
    for(HostLibraryLoad *load=image->loads;load;load=load->imageNext)if(load->actual&&!load->retired){
        ready=ready&&native_owner_merge(&owners,load->owners);unknown|=load->unknown||!load->leaseComplete;
    }
    AcquireSRWLockShared(&native_records);
    for(NativeLibrary *library=native_libraries;library;library=library->next)
        if(library->hostImage==image&&library->handle==image->handle&&InterlockedCompareExchange(&library->alive,0,0)
                &&!InterlockedCompareExchange(&library->closing,0,0))ready=ready&&native_owner_merge(&owners,library->owners);
    ReleaseSRWLockShared(&native_records);
    if(!native_has_producer(owners)&&!native_has_core_state(owners)){native_owner_links_free(owners);return;}
    /* Publish once without making hot JNI contribution capture read mutable
     * parsing/lifetime fields. CPU accesses remain opaque even with no PE span. */
    InterlockedExchange(&image->privateOpaque,1);
    HostStaticMemory *memory=image->privateMemory;
    if(!memory){memory=(HostStaticMemory*)calloc(1,sizeof(*memory));if(memory)__atomic_store_n(&image->privateMemory,memory,__ATOMIC_RELEASE);}
    if(!memory||!ready){InterlockedExchange(&image->privateOpaque,1);native_owner_links_free(owners);return;}
    if(!memory->parsed){memory->parsed=1;memory->failed=!host_static_parse(image,memory);}
    if(memory->closed){InterlockedExchange(&image->privateOpaque,1);native_owner_links_free(owners);return;}
    if(memory->spans||memory->failed)InterlockedExchange(&image->privateOpaque,1);
    JNIEnv *env=host_static_environment();
    for(HostStaticSpan *span=memory->spans;span;span=span->next)
        native_memory_image_span(env,image,span->address,span->bytes,owners,unknown||memory->failed);
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    host_static_tls_observed_locked(state,image,owners,unknown||memory->failed);
    native_owner_links_free(owners);
}
static int host_static_image_prepare(HostImage *image,JNIEnv *env){
    if(image&&InterlockedCompareExchange(&image->active,0,0))return 0;
    if(!image)return 1;
    if(image->privateMemory&&!image->privateMemory->closed&&!native_memory_image_prepare(env,image))return 0;
    if(host_static_tls_image_prepare(image,env))return 1;
    if(image->privateMemory&&!image->privateMemory->closed)native_memory_image_cancel(env,image);
    return 0;
}
static void host_static_image_cancel(HostImage *image,JNIEnv *env){
    if(image&&image->privateMemory&&!image->privateMemory->closed)native_memory_image_cancel(env,image);
    if(image)host_static_tls_image_cancel(image,env);
}
static void host_static_image_closed(HostImage *image,JNIEnv *env){
    if(!image)return;
    if(image->privateMemory&&!image->privateMemory->closed){image->privateMemory->closed=1;native_memory_image_retire(env,image);}
    host_static_tls_image_closed(image,env);
}
/* host_imports is held. Returning our pin or a JVM loader reference does not
 * prove unmapping. An unchanged or same-base replacement image stays sealed;
 * only absence of the exact old MEM_IMAGE mapping retires its borrowed rows. */
static void host_static_image_released(HostImage *image,JNIEnv *env){
    if(!image||image->live)return;
    MEMORY_BASIC_INFORMATION region={0};
    if(VirtualQuery(image->handle,&region,sizeof(region))!=sizeof(region))return;
    if(region.State!=MEM_FREE&&region.Type==MEM_IMAGE&&region.AllocationBase==image->handle)return;
    host_static_image_closed(image,env);
}
static void host_static_library_returned(NativeLibrary *library,JNIEnv *env){
    DWORD error=GetLastError();AcquireSRWLockExclusive(&host_imports);
    host_static_image_released(library?library->hostImage:NULL,env);
    ReleaseSRWLockExclusive(&host_imports);SetLastError(error);
}
/* Data effects of this precise native scope can include private CPU loads.
 * Keep known modules and one existing unknown marker. This never participates
 * in allocation-creator ownership or grants a native memory operation. */
static int host_private_binding_unknown(Binding *binding){
    if(!binding||binding->unsafeOperation||binding->fileOperation||binding->threadOperation||binding->arraycopyOperation
            ||(!native_has_producer(binding->owners)&&!native_has_core_state(binding->owners)))return 0;
    HostImage *image=binding->hostImage;
    return !image||!__atomic_load_n(&image->privateMemory,__ATOMIC_ACQUIRE)
            ||InterlockedCompareExchange(&image->privateOpaque,0,0)!=0;
}
