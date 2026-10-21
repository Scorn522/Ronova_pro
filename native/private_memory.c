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
    const void *address;SIZE_T length;struct NativePrivateRange *previous,*next;
    int linked;unsigned kind;
    NativePrivateNode metadata,span;
} NativePrivateRange;

enum {NATIVE_PRIVATE_EXTERNAL,NATIVE_PRIVATE_CRT,NATIVE_PRIVATE_VIRTUAL};
static SRWLOCK native_private_gate=SRWLOCK_INIT;
static NativePrivateRange *native_private_ranges;
static NativePrivateNode *native_private_index;

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
    range->previous=NULL;range->next=native_private_ranges;
    if(range->next)range->next->previous=range;native_private_ranges=range;range->linked=1;
    native_private_node(&range->metadata,range,range,sizeof(*range));
    if(range->address&&range->length)native_private_node(&range->span,range,range->address,range->length);
}
static void native_private_unlink(NativePrivateRange *range){
    native_private_index=native_private_remove(native_private_index,&range->metadata);
    if(range->address&&range->length)native_private_index=native_private_remove(native_private_index,&range->span);
    if(range->previous)range->previous->next=range->next;else native_private_ranges=range->next;
    if(range->next)range->next->previous=range->previous;
    range->previous=range->next=NULL;range->linked=0;
}
static int native_private_query(NativePrivateNode *root,uintptr_t first,uintptr_t last){
    if(!root||root->upper<first)return 0;
    if(root->left&&root->left->upper>=first&&native_private_query(root->left,first,last))return 1;
    if(root->first>last)return 0;
    return root->last>=first||native_private_query(root->right,first,last);
}
static NativePrivateRange *native_private_crt_at(NativePrivateNode *root,uintptr_t address){
    if(!root)return NULL;
    if(address<root->first)return native_private_crt_at(root->left,address);
    if(address>root->first)return native_private_crt_at(root->right,address);
    NativePrivateRange *range=root->owner;
    if(range->kind==NATIVE_PRIVATE_CRT&&(uintptr_t)range==address&&(uintptr_t)range->address==address)return range;
    range=native_private_crt_at(root->left,address);return range?range:native_private_crt_at(root->right,address);
}

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
    native_private_link(range);return 1;
}
static void native_private_unregister_locked(NativePrivateRange *range){
    if(!range||!range->linked)return;native_private_unlink(range);
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
    return native_private_query(native_private_index,first,last);
}
static NativePrivateRange *native_private_crt_locked(const void *pointer){
    uintptr_t address=(uintptr_t)pointer;
    if(address<sizeof(NativePrivateHeader))return NULL;
    return native_private_crt_at(native_private_index,address-sizeof(NativePrivateHeader));
}
static void *native_private_allocate(size_t bytes,int zero){
    size_t payload=bytes?bytes:1;
    if(payload>SIZE_MAX-sizeof(NativePrivateHeader)){errno=ENOMEM;return NULL;}
    size_t total=sizeof(NativePrivateHeader)+payload;
    AcquireSRWLockExclusive(&native_private_gate);
    NativePrivateHeader *header=zero?(NativePrivateHeader*)calloc(1,total):(NativePrivateHeader*)malloc(total);
    if(header){
        header->range.address=header;header->range.length=total;header->range.kind=NATIVE_PRIVATE_CRT;
        native_private_link(&header->range);
    }
    ReleaseSRWLockExclusive(&native_private_gate);return header?(void*)(header+1):NULL;
}
static void *native_private_malloc(size_t bytes){return native_private_allocate(bytes,0);}
static void *native_private_calloc(size_t count,size_t bytes){
    if(count&&bytes>SIZE_MAX/count){errno=ENOMEM;return NULL;}return native_private_allocate(count*bytes,1);
}
static void native_private_free(void *pointer){
    if(!pointer)return;AcquireSRWLockExclusive(&native_private_gate);
    NativePrivateRange *range=native_private_crt_locked(pointer);
    if(range){native_private_unlink(range);free(range);}
    else free(pointer); /* A deliberately unregistered CRT payload keeps its original free contract. */
    ReleaseSRWLockExclusive(&native_private_gate);
}
static void *native_private_realloc(void *pointer,size_t bytes){
    if(!pointer)return native_private_malloc(bytes);
    if(!bytes){native_private_free(pointer);return NULL;}
    if(bytes>SIZE_MAX-sizeof(NativePrivateHeader)){errno=ENOMEM;return NULL;}
    size_t total=sizeof(NativePrivateHeader)+bytes;
    AcquireSRWLockExclusive(&native_private_gate);NativePrivateRange *before=native_private_crt_locked(pointer);
    /* All three actual realloc families in this DLL grow their own private
     * arrays. No business pointer gains a private allocation by numeric reuse. */
    if(!before){ReleaseSRWLockExclusive(&native_private_gate);errno=EINVAL;return NULL;}
    native_private_unlink(before);
    NativePrivateHeader *header=(NativePrivateHeader*)realloc(before,total);
    if(header){
        header->range.address=header;header->range.length=total;header->range.kind=NATIVE_PRIVATE_CRT;
        native_private_link(&header->range);
    }else native_private_link(before);
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
        native_private_link(range);
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
            if(range->kind==NATIVE_PRIVATE_VIRTUAL&&range->address==address){native_private_unlink(range);free(range);break;}
            at=&range->next;
        }
    }
    /* Failed release and successful decommit both retain the reservation row. */
    ReleaseSRWLockExclusive(&native_private_gate);SetLastError(error);return released;
}
