/* Included before this translation unit redirects its own CRT allocations.
 * This gate is a leaf: its holders never enter a source/Host/VM directory,
 * Java, a JVMTI operation, or an outside callback. */
#include <stddef.h>
#include <errno.h>
#include <malloc.h>

/* This translation unit's real CRT heap, before its allocation names are
 * redirected. Heap handles from another provider never identify this heap. */
static HANDLE native_private_crt_heap(void){
    DWORD error=GetLastError();int saved=errno;intptr_t actual=_get_heap_handle();
    errno=saved;SetLastError(error);return actual>0?(HANDLE)(uintptr_t)actual:NULL;
}

typedef struct NativePrivateRange {
    const void *address;SIZE_T length;struct NativePrivateRange *next;
    int linked;unsigned kind;
} NativePrivateRange;

enum {NATIVE_PRIVATE_EXTERNAL,NATIVE_PRIVATE_CRT,NATIVE_PRIVATE_VIRTUAL};
static SRWLOCK native_private_gate=SRWLOCK_INIT;
static NativePrivateRange *native_private_ranges;

/* Every returned CRT payload has max_align_t alignment. The same registered
 * allocation contains its directory node and the complete requested payload. */
typedef union NativePrivateHeader {
    max_align_t alignment;
    NativePrivateRange range;
} NativePrivateHeader;

typedef BOOL (WINAPI *NativePrivateVirtualFree)(LPVOID,SIZE_T,DWORD);

static int native_private_overlap(uintptr_t first,uintptr_t last,const void *address,SIZE_T length){
    if(!address||!length)return 0;uintptr_t base=(uintptr_t)address,span=(uintptr_t)(length-1);
    uintptr_t end=span>UINTPTR_MAX-base?UINTPTR_MAX:base+span;
    return first<=end&&base<=last;
}
static int native_private_register_locked(NativePrivateRange *range,const void *address,SIZE_T length){
    if(!range||!address||!length||length-1>UINTPTR_MAX-(uintptr_t)address)return 0;
    if(range->linked)return range->address==address&&range->length==length;
    range->address=address;range->length=length;range->kind=NATIVE_PRIVATE_EXTERNAL;
    range->next=native_private_ranges;range->linked=1;native_private_ranges=range;return 1;
}
static void native_private_unregister_locked(NativePrivateRange *range){
    if(!range||!range->linked)return;NativePrivateRange **at=&native_private_ranges;
    while(*at&&*at!=range)at=&(*at)->next;
    if(*at==range)*at=range->next;
    range->address=NULL;range->length=0;range->next=NULL;range->linked=0;range->kind=NATIVE_PRIVATE_EXTERNAL;
}
static int native_private_register(NativePrivateRange *range,const void *address,SIZE_T length){
    AcquireSRWLockExclusive(&native_private_gate);int ready=native_private_register_locked(range,address,length);
    ReleaseSRWLockExclusive(&native_private_gate);return ready;
}
static void native_private_unregister(NativePrivateRange *range){
    AcquireSRWLockExclusive(&native_private_gate);native_private_unregister_locked(range);ReleaseSRWLockExclusive(&native_private_gate);
}
/* The caller already holds native_private_gate through its actual output copy.
 * External intrusive nodes are control metadata even when their covered span
 * belongs to a real VM object, stack frame, or currently observed IAT slot. */
static int native_private_range_locked(uintptr_t first,uintptr_t last){
    if(first>last)return -1;
    for(NativePrivateRange *range=native_private_ranges;range;range=range->next)
        if(native_private_overlap(first,last,range,sizeof(*range))
                ||native_private_overlap(first,last,range->address,range->length))return 1;
    return 0;
}
static NativePrivateRange **native_private_crt_locked(const void *pointer){
    uintptr_t address=(uintptr_t)pointer;
    if(address<sizeof(NativePrivateHeader))return NULL;
    uintptr_t header=address-sizeof(NativePrivateHeader);NativePrivateRange **at=&native_private_ranges;
    while(*at){NativePrivateRange *range=*at;
        if(range->kind==NATIVE_PRIVATE_CRT&&(uintptr_t)range==header&&range->address==(const void*)header)return at;
        at=&range->next;
    }return NULL;
}
static void *native_private_allocate(size_t bytes,int zero){
    size_t payload=bytes?bytes:1;
    if(payload>SIZE_MAX-sizeof(NativePrivateHeader)){errno=ENOMEM;return NULL;}
    size_t total=sizeof(NativePrivateHeader)+payload;
    AcquireSRWLockExclusive(&native_private_gate);
    NativePrivateHeader *header=zero?(NativePrivateHeader*)calloc(1,total):(NativePrivateHeader*)malloc(total);
    if(header){
        header->range.address=header;header->range.length=total;header->range.kind=NATIVE_PRIVATE_CRT;
        header->range.next=native_private_ranges;header->range.linked=1;native_private_ranges=&header->range;
    }
    ReleaseSRWLockExclusive(&native_private_gate);return header?(void*)(header+1):NULL;
}
static void *native_private_malloc(size_t bytes){return native_private_allocate(bytes,0);}
static void *native_private_calloc(size_t count,size_t bytes){
    if(count&&bytes>SIZE_MAX/count){errno=ENOMEM;return NULL;}return native_private_allocate(count*bytes,1);
}
static void native_private_free(void *pointer){
    if(!pointer)return;AcquireSRWLockExclusive(&native_private_gate);
    NativePrivateRange **at=native_private_crt_locked(pointer);
    if(at){NativePrivateRange *range=*at;*at=range->next;free(range);}
    else free(pointer); /* A deliberately unregistered CRT payload keeps its original free contract. */
    ReleaseSRWLockExclusive(&native_private_gate);
}
static void *native_private_realloc(void *pointer,size_t bytes){
    if(!pointer)return native_private_malloc(bytes);
    if(!bytes){native_private_free(pointer);return NULL;}
    if(bytes>SIZE_MAX-sizeof(NativePrivateHeader)){errno=ENOMEM;return NULL;}
    size_t total=sizeof(NativePrivateHeader)+bytes;
    AcquireSRWLockExclusive(&native_private_gate);NativePrivateRange **at=native_private_crt_locked(pointer);
    /* All three actual realloc families in this DLL grow their own private
     * arrays. No business pointer gains a private allocation by numeric reuse. */
    if(!at){ReleaseSRWLockExclusive(&native_private_gate);errno=EINVAL;return NULL;}
    NativePrivateRange *before=*at,*next=before->next;
    NativePrivateHeader *header=(NativePrivateHeader*)realloc(before,total);
    if(header){
        header->range.address=header;header->range.length=total;header->range.kind=NATIVE_PRIVATE_CRT;
        header->range.next=next;header->range.linked=1;*at=&header->range;
    }
    /* A failed nonzero realloc leaves the original allocation and row alive. */
    ReleaseSRWLockExclusive(&native_private_gate);return header?(void*)(header+1):NULL;
}
static char *native_private_strdup(const char *text){
    if(!text){errno=EINVAL;return NULL;}size_t length=strlen(text);
    if(length==SIZE_MAX){errno=ENOMEM;return NULL;}
    char *copy=(char*)native_private_malloc(length+1);if(copy)memcpy(copy,text,length+1);return copy;
}
/* JNI array/string copies are intentionally writable outside values. Their
 * lease and owners use private allocations; their bytes never enter this list. */
static void *native_payload_malloc(size_t bytes){return malloc(bytes);}
static void native_payload_free(void *pointer){free(pointer);}

static LPVOID native_private_virtual_alloc(LPVOID address,SIZE_T bytes,DWORD type,DWORD protection){
    DWORD incoming=GetLastError();SYSTEM_INFO system;GetSystemInfo(&system);SIZE_T page=system.dwPageSize;
    /* Every current call reserves from NULL and commits the full reservation.
     * Record its real system-page rounding, including the shadow-table tail. */
    if(!page||bytes>SIZE_MAX-(page-1)){SetLastError(ERROR_NOT_ENOUGH_MEMORY);return NULL;}
    SIZE_T rounded=bytes?((bytes+page-1)/page)*page:0;
    AcquireSRWLockExclusive(&native_private_gate);
    NativePrivateRange *range=(NativePrivateRange*)calloc(1,sizeof(*range));
    if(!range){ReleaseSRWLockExclusive(&native_private_gate);SetLastError(ERROR_NOT_ENOUGH_MEMORY);return NULL;}
    SetLastError(incoming);LPVOID allocated=VirtualAlloc(address,bytes,type,protection);DWORD error=GetLastError();
    if(allocated){
        range->address=allocated;range->length=rounded;range->kind=NATIVE_PRIVATE_VIRTUAL;
        range->next=native_private_ranges;range->linked=1;native_private_ranges=range;
    }else free(range);
    ReleaseSRWLockExclusive(&native_private_gate);SetLastError(error);return allocated;
}
static BOOL native_private_virtual_free(LPVOID address,SIZE_T bytes,DWORD type,NativePrivateVirtualFree original){
    if(!original){SetLastError(ERROR_PROC_NOT_FOUND);return FALSE;}
    DWORD incoming=GetLastError();AcquireSRWLockExclusive(&native_private_gate);
    SetLastError(incoming);BOOL released=original(address,bytes,type);DWORD error=GetLastError();
    if(released&&(type&MEM_RELEASE)){
        NativePrivateRange **at=&native_private_ranges;
        while(*at){NativePrivateRange *range=*at;
            if(range->kind==NATIVE_PRIVATE_VIRTUAL&&range->address==address){*at=range->next;free(range);break;}
            at=&range->next;
        }
    }
    /* Failed release and successful decommit both retain the reservation row. */
    ReleaseSRWLockExclusive(&native_private_gate);SetLastError(error);return released;
}
