/* Shared only by the observed Host allocator wrappers and memory.c. */
#ifndef RONOVA_HOST_MEMORY_TRACKING_H
#define RONOVA_HOST_MEMORY_TRACKING_H
typedef struct NativeHostMemoryCall NativeHostMemoryCall;
enum { NATIVE_MEMORY_HOST_HEAP=1,NATIVE_MEMORY_HOST_VIRTUAL=2,NATIVE_MEMORY_HOST_CRT=3,NATIVE_MEMORY_HOST_IMAGE=4,NATIVE_MEMORY_HOST_CPP=5 };
enum { NATIVE_MEMORY_HOST_ALLOCATE=0,NATIVE_MEMORY_HOST_REALLOCATE=1,NATIVE_MEMORY_HOST_FREE=2,NATIVE_MEMORY_HOST_DECOMMIT=3,NATIVE_MEMORY_HOST_CONTEXT_FREE=4,NATIVE_MEMORY_HOST_CONTEXT_CREATE=5 };

/* Post-success only. Owners are borrowed for this call and copied by the tracker.
 * bytes==UINT64_MAX means an actual nonnull result whose extent is unknown.
 * Heap context is the real HANDLE, freeFlags only the original HeapFree flags;
 * CRT/Virtual context and freeFlags are zero. release is the exact provider's
 * HeapFree/VirtualFree/free function, never a controlled wrapper entry. Heap
 * blocks use prepare/complete so their context incarnation is captured before
 * the original call; a late post-success HANDLE alone cannot supply it. */
static void native_memory_host_allocated(JNIEnv*,int family,void *allocator,void *release,uintptr_t context,uintptr_t freeFlags,
        uint64_t address,uint64_t bytes,OwnerLink *owners,int unknown);

/* Short locks only: the returned token holds this generation's users/changing
 * admission across the one original call. NULL with denied==0 keeps an unknown
 * original pointer/API unchanged. denied==1 names a known busy/retired/control
 * allocation. The wrapper must complete or abort on real exceptional unwind. */
static NativeHostMemoryCall *native_memory_host_prepare(JNIEnv*,int family,void *release,uintptr_t context,
        uint64_t address,uint64_t bytes,int operation,OwnerLink *owners,int unknown,int *denied);
/* A heap lease can protect an actual call with an unknown block pointer. It is
 * not a cleanup capability: only a retained allocation generation is one. */
static int native_memory_host_known(NativeHostMemoryCall*);
/* Only an actual original release inside the caller's real unload scope may
 * use this exception. Every held creator/reader must already be stopped and
 * fully observed; a known pointer alone cannot release shared live storage. */
static int native_memory_host_cleanup(NativeHostMemoryCall*);
/* Merge the captured heap creator and affected allocation/readers before the
 * original destructive call's final source gate. No live lookup by HANDLE is
 * performed here; the token already holds the exact context incarnation. */
static int native_memory_host_sources(NativeHostMemoryCall*,OwnerLink **owners,int *unknown);
/* completed==0 preserves the old lifetime. A nonnull successful realloc retires
 * the old generation and records a new one, even at the same address. released
 * is true only after real free success or the provider's documented realloc(p,0)
 * release result. A decommit keeps the reservation, invalidating its old views.
 * CONTEXT_CREATE takes the real HeapCreate result as returned; no pool extent
 * or child allocation is inferred from that opaque handle. */
static void native_memory_host_complete(JNIEnv*,NativeHostMemoryCall*,uint64_t returned,uint64_t bytes,int completed,int released);
/* Missing/exceptional completion keeps the old row uncertain and closes new
 * admissions; it does not claim that the original call failed or released it. */
static void native_memory_host_abort(JNIEnv*,NativeHostMemoryCall*);
static void native_memory_host_unobserved(OwnerLink *owners);

/* Exact observed image incarnation; these opaque borrowed spans are never freed
 * independently. Retire follows the real image lifetime, not a numeric HMODULE. */
static void native_memory_image_span(JNIEnv*,void *imageTag,uint64_t address,uint64_t bytes,OwnerLink *owners,int unknown);
static int native_memory_image_busy(JNIEnv*,void *imageTag);
static int native_memory_image_prepare(JNIEnv*,void *imageTag);
static void native_memory_image_cancel(JNIEnv*,void *imageTag);
static void native_memory_image_retire(JNIEnv*,void *imageTag);
#endif
