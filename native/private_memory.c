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

struct NativePrivateRange;
typedef struct NativePrivateNode {
    uintptr_t first,last,upper;struct NativePrivateNode *left,*right;
    struct NativePrivateRange *owner;int height;
} NativePrivateNode;
typedef struct NativePrivateRange {
    const void *address;SIZE_T length;
    int linked;unsigned kind;
    NativePrivateNode metadata,span;
} NativePrivateRange;

enum {NATIVE_PRIVATE_EXTERNAL,NATIVE_PRIVATE_CRT,NATIVE_PRIVATE_VIRTUAL};
static SRWLOCK native_private_gate=SRWLOCK_INIT;
static NativePrivateNode *native_private_index;
enum {NATIVE_PRIVATE_REGISTER=1,NATIVE_PRIVATE_UNREGISTER,NATIVE_PRIVATE_ALLOCATE,
    NATIVE_PRIVATE_FREE,NATIVE_PRIVATE_REALLOCATE,NATIVE_PRIVATE_RESERVE,
    NATIVE_PRIVATE_RELEASE,NATIVE_PRIVATE_JNI_REGISTER,NATIVE_PRIVATE_JNI_RETIRE,
    NATIVE_PRIVATE_IMPORT_PUBLISH};
/* Failure-only readers sample one coherent operation/size/thread tuple. The
 * 24-bit requested size saturates explicitly; zero denotes an unsized site.
 * This is
 * diagnostic state, not a lock owner oracle or an admission decision. Zero
 * includes acquisition/release windows and a queued writer, not just idle. */
static uint64_t native_private_writer;
static void native_private_read_busy(uint64_t);
static void native_private_writer_publish(unsigned operation,SIZE_T bytes){
    uint64_t size=bytes>0xffffff?0xffffff:(uint64_t)bytes;
    __atomic_store_n(&native_private_writer,((uint64_t)operation<<56)|(size<<32)|GetCurrentThreadId(),__ATOMIC_RELEASE);
}
static int native_private_write_lock_sized(int restricted,unsigned operation,SIZE_T bytes){
    if(restricted){if(!TryAcquireSRWLockExclusive(&native_private_gate))return 0;}
    else AcquireSRWLockExclusive(&native_private_gate);
    native_private_writer_publish(operation,bytes);
    return 1;
}
static int native_private_write_lock(int restricted,unsigned operation){return native_private_write_lock_sized(restricted,operation,0);}
static void native_private_write_unlock(void){
    __atomic_store_n(&native_private_writer,0,__ATOMIC_RELEASE);
    ReleaseSRWLockExclusive(&native_private_gate);
}
static const char *native_private_writer_site(uint64_t sample){
    switch((unsigned)(sample>>56)){
        case NATIVE_PRIVATE_REGISTER:return "register";
        case NATIVE_PRIVATE_UNREGISTER:return "unregister";
        case NATIVE_PRIVATE_ALLOCATE:return "crt_allocate";
        case NATIVE_PRIVATE_FREE:return "crt_free";
        case NATIVE_PRIVATE_REALLOCATE:return "crt_reallocate";
        case NATIVE_PRIVATE_RESERVE:return "virtual_allocate";
        case NATIVE_PRIVATE_RELEASE:return "virtual_free";
        case NATIVE_PRIVATE_JNI_REGISTER:return "jni_register";
        case NATIVE_PRIVATE_JNI_RETIRE:return "jni_retire";
        case NATIVE_PRIVATE_IMPORT_PUBLISH:return "import_publish";
        default:return "unobserved";
    }
}

/* Queries and ordinary-memory effects only read the directory. A shared hold
 * still excludes every allocation, publication, relocation and retirement
 * until the actual effect finishes, without serializing unrelated outside
 * buffers. Restricted callbacks never enter a blocking wait for a writer.
 * No VM transition, callback or private allocation is permitted while this
 * leaf hold is live. */
static int native_bounded_read_lock(SRWLOCK *gate,int restricted){
    if(restricted){
        if(TryAcquireSRWLockShared(gate))return 1;
        /* A transient leaf writer must not become an immediate API failure.
         * Retry admission only, with a bounded processor hint: no sleep,
         * scheduler/VM call or unbounded wait under a loader/VM callback. A
         * suspended, reentrant or blocked writer still reaches the same BUSY
         * result. The original memory operation is never retried here. */
        for(unsigned retry=0;retry<64;retry++){
            YieldProcessor();
            if(TryAcquireSRWLockShared(gate))return 1;
        }
        return 0;
    }
    AcquireSRWLockShared(gate);return 1;
}
static int native_private_read_lock(int restricted){
    if(native_bounded_read_lock(&native_private_gate,restricted))return 1;
    native_private_read_busy(__atomic_load_n(&native_private_writer,__ATOMIC_ACQUIRE));return 0;
}

/* The same live ranges, indexed under the same leaf gate. Embedded nodes do
 * not allocate, publish permissions, or outlive their actual registration.
 * Include both the directory record and its complete covered allocation. */
static int native_private_height(NativePrivateNode *node){return node?node->height:0;}
static void native_private_update(NativePrivateNode *node){
    int left=native_private_height(node->left),right=native_private_height(node->right);
    node->height=1+(left>right?left:right);node->upper=node->last;
    if(node->left&&node->left->upper>node->upper)node->upper=node->left->upper;
    if(node->right&&node->right->upper>node->upper)node->upper=node->right->upper;
}
static NativePrivateNode *native_private_rotate(NativePrivateNode *node,int left){
    NativePrivateNode *next=left?node->right:node->left;
    if(left){node->right=next->left;next->left=node;}else{node->left=next->right;next->right=node;}
    native_private_update(node);native_private_update(next);return next;
}
static NativePrivateNode *native_private_balance(NativePrivateNode *node){
    if(!node)return NULL;native_private_update(node);
    int balance=native_private_height(node->left)-native_private_height(node->right);
    if(balance>1){
        if(native_private_height(node->left->left)<native_private_height(node->left->right))
            node->left=native_private_rotate(node->left,1);
        return native_private_rotate(node,0);
    }
    if(balance< -1){
        if(native_private_height(node->right->right)<native_private_height(node->right->left))
            node->right=native_private_rotate(node->right,0);
        return native_private_rotate(node,1);
    }
    return node;
}
static int native_private_before(NativePrivateNode *left,NativePrivateNode *right){
    return left->first<right->first||(left->first==right->first&&(uintptr_t)left<(uintptr_t)right);
}
static NativePrivateNode *native_private_insert(NativePrivateNode *root,NativePrivateNode *node){
    if(!root)return node;
    if(native_private_before(node,root))root->left=native_private_insert(root->left,node);
    else root->right=native_private_insert(root->right,node);
    return native_private_balance(root);
}
static NativePrivateNode *native_private_take_min(NativePrivateNode *root,NativePrivateNode **taken){
    if(!root->left){*taken=root;return root->right;}
    root->left=native_private_take_min(root->left,taken);return native_private_balance(root);
}
static NativePrivateNode *native_private_remove(NativePrivateNode *root,NativePrivateNode *node){
    if(!root)return NULL;
    if(root==node){
        if(!root->left)return root->right;if(!root->right)return root->left;
        NativePrivateNode *next=NULL,*right=native_private_take_min(root->right,&next);
        next->left=root->left;next->right=right;return native_private_balance(next);
    }
    if(native_private_before(node,root))root->left=native_private_remove(root->left,node);
    else root->right=native_private_remove(root->right,node);
    return native_private_balance(root);
}
static void native_private_node(NativePrivateNode *node,NativePrivateRange *owner,const void *address,SIZE_T length){
    uintptr_t first=(uintptr_t)address,delta=(uintptr_t)(length-1);
    node->first=first;node->last=delta>UINTPTR_MAX-first?UINTPTR_MAX:first+delta;
    node->upper=node->last;node->left=node->right=NULL;node->height=1;node->owner=owner;
    native_private_index=native_private_insert(native_private_index,node);
}
static void native_private_link(NativePrivateRange *range){
    range->linked=1;
    /* A CRT span starts at its header and covers the entire range record.
     * Indexing that record again doubles tree mutations on every allocation
     * without protecting any additional byte. External/virtual metadata is
     * separately allocated and must retain its own node. */
    if(range->kind!=NATIVE_PRIVATE_CRT)
        native_private_node(&range->metadata,range,range,sizeof(*range));
    if(range->address&&range->length)native_private_node(&range->span,range,range->address,range->length);
}
static void native_private_unlink(NativePrivateRange *range){
    if(range->kind!=NATIVE_PRIVATE_CRT)
        native_private_index=native_private_remove(native_private_index,&range->metadata);
    if(range->address&&range->length)native_private_index=native_private_remove(native_private_index,&range->span);
    range->linked=0;
}
static int native_private_query(NativePrivateNode *root,uintptr_t first,uintptr_t last){
    if(!root||root->upper<first)return 0;
    if(root->left&&root->left->upper>=first&&native_private_query(root->left,first,last))return 1;
    if(root->first>last)return 0;
    return root->last>=first||native_private_query(root->right,first,last);
}
static NativePrivateRange *native_private_allocation_at(NativePrivateNode *root,uintptr_t address,unsigned kind){
    if(!root)return NULL;
    if(address<root->first)return native_private_allocation_at(root->left,address,kind);
    if(address>root->first)return native_private_allocation_at(root->right,address,kind);
    NativePrivateRange *range=root->owner;
    /* Metadata and external spans can share this key. Only the exact owned
     * allocation span may select a CRT header or virtual reservation. */
    if(root==&range->span&&range->kind==kind&&(uintptr_t)range->address==address)return range;
    range=native_private_allocation_at(root->left,address,kind);
    return range?range:native_private_allocation_at(root->right,address,kind);
}

/* Every returned CRT payload has max_align_t alignment. The same registered
 * allocation contains its directory node and the complete requested payload. */
typedef union NativePrivateHeader {
    max_align_t alignment;
    struct {NativePrivateRange range;SLIST_ENTRY cached;} data;
} NativePrivateHeader;
_Static_assert(_Alignof(NativePrivateHeader)>=MEMORY_ALLOCATION_ALIGNMENT,"private SList header alignment");
_Static_assert(offsetof(NativePrivateHeader,data.cached)%MEMORY_ALLOCATION_ALIGNMENT==0,"private SList entry alignment");
/* Reserve a bounded, wholly private address range before publishing control
 * payload. Committing and reusing blocks inside that reservation cannot expose
 * a new unprotected address, so neither operation mutates the global range
 * tree. Only used pages are committed; retained storage is bounded at 64 MiB.
 * Capacity/preparation/commit failures keep the original registered CRT path. */
static size_t native_private_thread_bytes(void);
enum {NATIVE_PRIVATE_SMALL_BYTES=128,NATIVE_PRIVATE_SMALL_POWER_CLASSES=7,
    NATIVE_PRIVATE_SMALL_CLASSES=NATIVE_PRIVATE_SMALL_POWER_CLASSES+1,
    // The captured 8 KiB control allocation otherwise holds the global CRT
    // writer while a driver unload callback must admit an unrelated release.
    NATIVE_PRIVATE_SMALL_MAX=NATIVE_PRIVATE_SMALL_BYTES<<(NATIVE_PRIVATE_SMALL_POWER_CLASSES-1),
    NATIVE_PRIVATE_SMALL_SLAB=256*1024,NATIVE_PRIVATE_SMALL_RESERVE=64*1024*1024,
    NATIVE_PRIVATE_SMALL_SLABS=NATIVE_PRIVATE_SMALL_RESERVE/NATIVE_PRIVATE_SMALL_SLAB};
typedef struct NativePrivateSmallClass {SLIST_HEADER free;unsigned current;} NativePrivateSmallClass;
typedef struct NativePrivateSmallSlab {unsigned classIndex;SIZE_T used;} NativePrivateSmallSlab;
static NativePrivateSmallClass native_private_small[NATIVE_PRIVATE_SMALL_CLASSES];
static NativePrivateSmallSlab native_private_small_slabs[NATIVE_PRIVATE_SMALL_SLABS];
static volatile LONG native_private_small_initialized;
static NativePrivateRange native_private_small_range;
static SRWLOCK native_private_small_gate=SRWLOCK_INIT;
static unsigned native_private_small_next;
static SIZE_T native_private_small_capacity(unsigned index){
    return index==NATIVE_PRIVATE_SMALL_CLASSES-1?native_private_thread_bytes():(SIZE_T)NATIVE_PRIVATE_SMALL_BYTES<<index;
}
static SIZE_T native_private_small_stride(unsigned index){
    SIZE_T alignment=_Alignof(NativePrivateHeader),bytes=sizeof(NativePrivateHeader)+native_private_small_capacity(index);
    return ((bytes+alignment-1)/alignment)*alignment;
}
static int native_private_small_ready(void){
    if(__atomic_load_n(&native_private_small_initialized,__ATOMIC_ACQUIRE)==2)return 1;
    if(InterlockedCompareExchange(&native_private_small_initialized,1,0)!=0)return 0;
    DWORD error=GetLastError();int saved=errno;SYSTEM_INFO system;GetSystemInfo(&system);void *reserved=NULL;
    if(system.dwPageSize&&NATIVE_PRIVATE_SMALL_SLAB%system.dwPageSize==0){
        native_private_write_lock_sized(0,NATIVE_PRIVATE_RESERVE,NATIVE_PRIVATE_SMALL_RESERVE);
        reserved=VirtualAlloc(NULL,NATIVE_PRIVATE_SMALL_RESERVE,MEM_RESERVE,PAGE_READWRITE);
        if(reserved){
            native_private_small_range.address=reserved;native_private_small_range.length=NATIVE_PRIVATE_SMALL_RESERVE;
            native_private_small_range.kind=NATIVE_PRIVATE_VIRTUAL;native_private_link(&native_private_small_range);
        }
        native_private_write_unlock();
    }
    if(reserved)for(unsigned i=0;i<NATIVE_PRIVATE_SMALL_CLASSES;i++){
        InitializeSListHead(&native_private_small[i].free);native_private_small[i].current=NATIVE_PRIVATE_SMALL_SLABS;
    }
    InterlockedExchange(&native_private_small_initialized,reserved?2:3);
    errno=saved;SetLastError(error);return reserved!=NULL;
}
static NativePrivateHeader *native_private_small_take(SIZE_T payload){
    if(!native_private_small_ready())return NULL;
    unsigned index=0;SIZE_T capacity=NATIVE_PRIVATE_SMALL_BYTES;
    // Thread-state destruction is the observed large CRT-free writer. Keep
    // an exact compile-time size class, not a second allocator or a guessed
    // numeric layout. Ordinary larger requests retain their CRT contract.
    if(payload==native_private_thread_bytes()){index=NATIVE_PRIVATE_SMALL_CLASSES-1;capacity=payload;}
    else while(payload>capacity){capacity<<=1;index++;}
    NativePrivateSmallClass *bucket=&native_private_small[index];
    SLIST_ENTRY *entry=InterlockedPopEntrySList(&bucket->free);
    if(entry)return (NativePrivateHeader*)((unsigned char*)entry - offsetof(NativePrivateHeader,data.cached));
    DWORD error=GetLastError();int saved=errno;NativePrivateHeader *header=NULL;
    // This independent leaf gate serializes only virgin slots/page commits.
    // Its holders never acquire native_private_gate, enter the CRT or invoke
    // Java/JVMTI/callbacks. Outside release readers need not acquire this gate.
    AcquireSRWLockExclusive(&native_private_small_gate);
    entry=InterlockedPopEntrySList(&bucket->free);
    if(entry)header=(NativePrivateHeader*)((unsigned char*)entry - offsetof(NativePrivateHeader,data.cached));
    else{
        SIZE_T stride=native_private_small_stride(index);unsigned current=bucket->current;
        NativePrivateSmallSlab *slab=current<NATIVE_PRIVATE_SMALL_SLABS?&native_private_small_slabs[current]:NULL;
        if(!slab||__atomic_load_n(&slab->used,__ATOMIC_RELAXED)>=NATIVE_PRIVATE_SMALL_SLAB/stride){
            slab=NULL;
            if(native_private_small_next<NATIVE_PRIVATE_SMALL_SLABS){
                current=native_private_small_next;
                if(VirtualAlloc((unsigned char*)native_private_small_range.address+(SIZE_T)current*NATIVE_PRIVATE_SMALL_SLAB,
                        NATIVE_PRIVATE_SMALL_SLAB,MEM_COMMIT,PAGE_READWRITE)){
                    slab=&native_private_small_slabs[current];bucket->current=current;native_private_small_next++;
                    __atomic_store_n(&slab->classIndex,index+1,__ATOMIC_RELEASE);
                }
            }
        }
        if(slab){
            SIZE_T used=__atomic_load_n(&slab->used,__ATOMIC_RELAXED);
            header=(NativePrivateHeader*)((unsigned char*)native_private_small_range.address+(SIZE_T)current*NATIVE_PRIVATE_SMALL_SLAB+used*stride);
            header->data.range.address=header;header->data.range.length=stride;header->data.range.kind=NATIVE_PRIVATE_CRT;
            // Per-slab identities bound real slots without trusting payload
            // bytes or creating one global range-tree node per allocation.
            __atomic_store_n(&slab->used,used+1,__ATOMIC_RELEASE);
        }
    }
    ReleaseSRWLockExclusive(&native_private_small_gate);errno=saved;SetLastError(error);return header;
}
static NativePrivateHeader *native_private_small_header(const void *pointer,unsigned *classIndex){
    if(__atomic_load_n(&native_private_small_initialized,__ATOMIC_ACQUIRE)!=2)return NULL;
    uintptr_t address=(uintptr_t)pointer,base=(uintptr_t)native_private_small_range.address;
    if(address<base||address-base>=NATIVE_PRIVATE_SMALL_RESERVE)return NULL;
    SIZE_T offset=address-base,within=offset%NATIVE_PRIVATE_SMALL_SLAB;
    NativePrivateSmallSlab *slab=&native_private_small_slabs[offset/NATIVE_PRIVATE_SMALL_SLAB];
    unsigned kind=__atomic_load_n(&slab->classIndex,__ATOMIC_ACQUIRE);
    if(!kind||kind>NATIVE_PRIVATE_SMALL_CLASSES||within<sizeof(NativePrivateHeader))return NULL;
    SIZE_T slot=within-sizeof(NativePrivateHeader),stride=native_private_small_stride(kind-1);
    if(slot%stride||slot/stride>=__atomic_load_n(&slab->used,__ATOMIC_ACQUIRE))return NULL;
    *classIndex=kind-1;return (NativePrivateHeader*)(address-sizeof(NativePrivateHeader));
}
/* The entire reservation stays protected while a slot is free or live. The
 * original CRT fallback is never mistaken for a suballocation by address. */
static int native_private_small_put(void *pointer){
    unsigned index=0;NativePrivateHeader *header=native_private_small_header(pointer,&index);if(!header)return 0;
    InterlockedPushEntrySList(&native_private_small[index].free,&header->data.cached);return 1;
}

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
    native_private_link(range);return 1;
}
static void native_private_unregister_locked(NativePrivateRange *range){
    if(!range||!range->linked)return;native_private_unlink(range);
    range->address=NULL;range->length=0;range->linked=0;range->kind=NATIVE_PRIVATE_EXTERNAL;
}
static int native_private_register(NativePrivateRange *range,const void *address,SIZE_T length){
    native_private_write_lock(0,NATIVE_PRIVATE_REGISTER);int ready=native_private_register_locked(range,address,length);
    native_private_write_unlock();return ready;
}
static void native_private_unregister(NativePrivateRange *range){
    native_private_write_lock(0,NATIVE_PRIVATE_UNREGISTER);native_private_unregister_locked(range);native_private_write_unlock();
}
/* The caller already holds native_private_gate through its actual output copy.
 * External intrusive nodes are control metadata even when their covered span
 * belongs to a real VM object, stack frame, or currently observed IAT slot. */
static int native_private_range_locked(uintptr_t first,uintptr_t last){
    if(first>last)return -1;
    return native_private_query(native_private_index,first,last);
}
static NativePrivateRange *native_private_crt_locked(const void *pointer){
    uintptr_t address=(uintptr_t)pointer;
    if(address<sizeof(NativePrivateHeader))return NULL;
    return native_private_allocation_at(native_private_index,address-sizeof(NativePrivateHeader),NATIVE_PRIVATE_CRT);
}
static void *native_private_allocate(size_t bytes,int zero){
    size_t payload=bytes?bytes:1;
    if(payload>SIZE_MAX-sizeof(NativePrivateHeader)){errno=ENOMEM;return NULL;}
    if(payload<=NATIVE_PRIVATE_SMALL_MAX||payload==native_private_thread_bytes()){
        NativePrivateHeader *reused=native_private_small_take(payload);
        if(reused){if(zero)memset(reused+1,0,payload);return reused+1;}
    }
    size_t total=sizeof(NativePrivateHeader)+payload;
    native_private_write_lock_sized(0,NATIVE_PRIVATE_ALLOCATE,total);
    NativePrivateHeader *header=(NativePrivateHeader*)malloc(total);
    if(header){
        header->data.range.address=header;header->data.range.length=total;header->data.range.kind=NATIVE_PRIVATE_CRT;
        native_private_link(&header->data.range);
    }
    native_private_write_unlock();
    /* Publish protection before initializing the payload. It is not returned
     * to its caller yet, and zeroing cannot mutate the range directory. Keep
     * this potentially large write out of the global allocation writer hold. */
    if(header&&zero)memset(header+1,0,payload);
    return header?(void*)(header+1):NULL;
}
static void *native_private_malloc(size_t bytes){return native_private_allocate(bytes,0);}
static void *native_private_calloc(size_t count,size_t bytes){
    if(count&&bytes>SIZE_MAX/count){errno=ENOMEM;return NULL;}return native_private_allocate(count*bytes,1);
}
static void native_private_free(void *pointer){
    if(!pointer)return;
    if(native_private_small_put(pointer))return;
    native_private_write_lock(0,NATIVE_PRIVATE_FREE);
    NativePrivateRange *range=native_private_crt_locked(pointer);
    if(range){native_private_writer_publish(NATIVE_PRIVATE_FREE,range->length);native_private_unlink(range);free(range);}
    else free(pointer); /* A deliberately unregistered CRT payload keeps its original free contract. */
    native_private_write_unlock();
}
static void *native_private_realloc(void *pointer,size_t bytes){
    if(!pointer)return native_private_malloc(bytes);
    if(!bytes){native_private_free(pointer);return NULL;}
    if(bytes>SIZE_MAX-sizeof(NativePrivateHeader)){errno=ENOMEM;return NULL;}
    unsigned smallClass=0;
    if(native_private_small_header(pointer,&smallClass)){
        SIZE_T capacity=native_private_small_capacity(smallClass);
        if(bytes<=capacity)return pointer;
        void *next=native_private_malloc(bytes);if(!next)return NULL;
        memcpy(next,pointer,capacity);native_private_small_put(pointer);return next;
    }
    size_t total=sizeof(NativePrivateHeader)+bytes;
    native_private_write_lock_sized(0,NATIVE_PRIVATE_REALLOCATE,total);NativePrivateRange *before=native_private_crt_locked(pointer);
    /* All three actual realloc families in this DLL grow their own private
     * arrays. No business pointer gains a private allocation by numeric reuse. */
    if(!before){native_private_write_unlock();errno=EINVAL;return NULL;}
    native_private_unlink(before);
    NativePrivateHeader *header=(NativePrivateHeader*)realloc(before,total);
    if(header){
        header->data.range.address=header;header->data.range.length=total;header->data.range.kind=NATIVE_PRIVATE_CRT;
        native_private_link(&header->data.range);
    }else native_private_link(before);
    /* A failed nonzero realloc leaves the original allocation and row alive. */
    native_private_write_unlock();return header?(void*)(header+1):NULL;
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
    native_private_write_lock_sized(0,NATIVE_PRIVATE_RESERVE,rounded);
    NativePrivateRange *range=(NativePrivateRange*)calloc(1,sizeof(*range));
    if(!range){native_private_write_unlock();SetLastError(ERROR_NOT_ENOUGH_MEMORY);return NULL;}
    SetLastError(incoming);LPVOID allocated=VirtualAlloc(address,bytes,type,protection);DWORD error=GetLastError();
    if(allocated){
        range->address=allocated;range->length=rounded;range->kind=NATIVE_PRIVATE_VIRTUAL;
        native_private_link(range);
    }else free(range);
    native_private_write_unlock();SetLastError(error);return allocated;
}
static BOOL native_private_virtual_free(LPVOID address,SIZE_T bytes,DWORD type,NativePrivateVirtualFree original){
    if(!original){SetLastError(ERROR_PROC_NOT_FOUND);return FALSE;}
    DWORD incoming=GetLastError();native_private_write_lock(0,NATIVE_PRIVATE_RELEASE);
    SetLastError(incoming);BOOL released=original(address,bytes,type);DWORD error=GetLastError();
    if(released&&(type&MEM_RELEASE)){
        NativePrivateRange *range=native_private_allocation_at(native_private_index,(uintptr_t)address,NATIVE_PRIVATE_VIRTUAL);
        if(range){native_private_unlink(range);free(range);}
    }
    /* Failed release and successful decommit both retain the reservation row. */
    native_private_write_unlock();SetLastError(error);return released;
}
