/* Historical private-allocation/SDK reference. Not included by production
 * control.c/bindings.c in V1.5; no API in this file is installed or exported. */
/* Shared only by the observed Host allocator wrappers and memory.c. */
#ifndef RONOVA_HOST_MEMORY_TRACKING_H
#define RONOVA_HOST_MEMORY_TRACKING_H
typedef struct NativeHostMemoryCall NativeHostMemoryCall;
typedef struct NativeHostBytesCall NativeHostBytesCall;
typedef struct NativeHostTransferCall NativeHostTransferCall;
typedef struct NativeHostArenaOriginalScope {
    HMODULE image;void *original,*release,*entry;NativeHostMemoryCall *call;
    DWORD thread;int cleanup;struct NativeHostArenaOriginalScope *previous;
} NativeHostArenaOriginalScope;
typedef struct NativeHostCppOriginalScope {
    HMODULE image;void *original,*release,*entry;uintptr_t context;
    NativeHostMemoryCall *call;DWORD thread;int cleanup,operation;
    struct NativeHostCppOriginalScope *previous;
} NativeHostCppOriginalScope;
enum { NATIVE_MEMORY_HOST_HEAP=1,NATIVE_MEMORY_HOST_VIRTUAL=2,NATIVE_MEMORY_HOST_CRT=3,NATIVE_MEMORY_HOST_IMAGE=4,NATIVE_MEMORY_HOST_CPP=5,NATIVE_MEMORY_HOST_ARENA=6,NATIVE_MEMORY_HOST_OTHER=7,NATIVE_MEMORY_HOST_CUSTOM=8 };
enum { NATIVE_MEMORY_HOST_ALLOCATE=0,NATIVE_MEMORY_HOST_REALLOCATE=1,NATIVE_MEMORY_HOST_FREE=2,NATIVE_MEMORY_HOST_DECOMMIT=3,NATIVE_MEMORY_HOST_CONTEXT_FREE=4,NATIVE_MEMORY_HOST_CONTEXT_CREATE=5 };
enum { NATIVE_MEMORY_HOST_ARENA_RESET=6,NATIVE_MEMORY_HOST_ARENA_UNKNOWN=7,NATIVE_MEMORY_HOST_OBSERVE=8 };
enum { NATIVE_MEMORY_HOST_CONTEXT_DETACH=9,NATIVE_MEMORY_HOST_THREAD_DETACH=10,NATIVE_MEMORY_HOST_CONTEXT_OBSERVE=11 };

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
/* OBSERVE takes a read-only user lease only for the exact family, release and
 * context. An observed foreign pointer is left unchanged; completion never
 * retires or changes the generation. */
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
/* Admission was refused before any original call ran; keep the old lifetime. */
static void native_memory_host_cancel(NativeHostMemoryCall*);
static void native_memory_host_unobserved(OwnerLink *owners);

/* Only actual observed CRT byte entries use these private spans. A zero byte
 * count has no numeric-pointer ownership meaning. Probe returns 0 only when
 * neither nonempty range touches observed storage, 1 on any intersection,
 * and -1 when its directory cannot be queried. Begin captures both exact
 * generations and all source-byte contributors in one short admission; an
 * observed partial/out-of-bounds span cannot fall through as unknown memory.
 * The original runs with counter leases alone, including secure CRT callbacks.
 * Unknown byte provenance remains unknown and never supplies an object/field,
 * constructor/destructor, allocator, or byte-rollback capability. */
static int native_memory_host_bytes_probe(uint64_t dst,uint64_t dstBytes,uint64_t src,uint64_t srcBytes,int restricted);
static NativeHostBytesCall *native_memory_host_bytes_begin(uint64_t dst,uint64_t dstBytes,uint64_t src,uint64_t srcBytes,
        OwnerLink *owners,int unknown,int restricted,int *denied);
/* caller is the actual return address, callbackEntered the actual enclosing
 * Host dispatch hold. Only this form can delegate into an already active,
 * exactly covered SDK mutator or original VM JNI writer. */
static NativeHostBytesCall *native_memory_host_bytes_begin_caller(uint64_t dst,uint64_t dstBytes,uint64_t src,uint64_t srcBytes,
        OwnerLink *owners,int unknown,int restricted,int *denied,int callbackEntered,void *caller);
static int native_memory_host_bytes_output_unobserved(NativeHostBytesCall*);
/* returned means the original really returned, including a secure CRT errno.
 * called with no return records possible partial effects. An unavailable Try
 * completion retains an explicitly uncertain lease; it cannot change the real
 * original result/errno or claim rollback. */
static void native_memory_host_bytes_end(NativeHostBytesCall*,int called,int returned);

/* One actual Nt/Zw/Win32 process-memory transfer. All supplied ranges are
 * local: the wrapper passes a remote endpoint as 0/0, never its numeric remote
 * address. The optional local SIZE_T output is a separate provider write, not
 * evidence that the requested data interval was fully copied. Probe/begin use
 * the same observed-partial-range rules as the private CRT byte boundary.
 * Every distinct row is admitted once, even when data and count overlap. */
static int native_memory_host_transfer_probe(uint64_t dst,uint64_t dstBytes,uint64_t src,uint64_t srcBytes,
        uint64_t output,uint64_t outputBytes,int restricted);
static NativeHostTransferCall *native_memory_host_transfer_begin_caller(uint64_t dst,uint64_t dstBytes,uint64_t src,uint64_t srcBytes,
        uint64_t output,uint64_t outputBytes,OwnerLink *owners,int unknown,int restricted,int *denied,int callbackEntered,void *caller);
/* Query only after a real call and before end. Missing local outputs or an
 * unobserved data source remain a wrapper image gap, with no invented row. */
static int native_memory_host_transfer_output_unobserved(NativeHostTransferCall*);
/* The real process identity establishes an opaque remote input. It is not a
 * local numeric range or an unknown caller/consumer of the count output. */
static void native_memory_host_transfer_input_unknown(NativeHostTransferCall*);
/* returned means the actual function returned, including failure NTSTATUS or
 * FALSE. Neither status nor a byte-count value is guessed or read here. */
static void native_memory_host_transfer_end(NativeHostTransferCall*,int called,int returned);

/* The exact loaded allocator's free export identifies one provider. Every
 * observed call leases it until the actual call and result observation finish;
 * a bulk command additionally leases the affected actual arena generation.
 * context is arena index+1 (zero means no observed arena). Pointer operations
 * obtain their context only from an existing exact row, never by asking an
 * allocator to inspect an unknown incoming pointer. Unknown controls close the
 * provider's old views without pretending that their allocations were freed. */
static NativeHostMemoryCall *native_memory_host_arena_prepare(JNIEnv*,void *release,uint64_t address,uintptr_t context,
        int operation,OwnerLink *owners,int unknown,int *denied);
static void native_memory_host_arena_complete(JNIEnv*,NativeHostMemoryCall*,uint64_t address,uint64_t bytes,
        uintptr_t context,int completed,int released);
/* mimalloc heap handles are actual opaque provider returns. Safe heap deletion
 * migrates their surviving blocks without replacing a block generation.
 * A result context UINTPTR_MAX means ownership could not be observed; zero
 * means a known block was actually abandoned by safe delete/thread_done. */
static NativeHostMemoryCall *native_memory_host_other_prepare(JNIEnv*,void *release,uint64_t address,uintptr_t context,
        int operation,OwnerLink*,int unknown,int *denied);
static void native_memory_host_other_complete(JNIEnv*,NativeHostMemoryCall*,uint64_t address,uint64_t bytes,
        uintptr_t context,int completed,int released);
static uintptr_t native_memory_host_other_context(NativeHostMemoryCall*);
/* These calls are made only after the Host wrapper matched the actual CRT
 * caller and consumer image to this thread's current real C++ original scope.
 * The inner returned CRT row can be promoted once to the exact outer new
 * result. A nested free borrows only its retained outer delete generation. */
static void native_memory_host_cpp_backing_allocated(JNIEnv*,NativeHostCppOriginalScope*,void *allocator,void *release,
        uintptr_t context,uint64_t address,uint64_t bytes,OwnerLink*,int unknown);
static void native_memory_host_cpp_allocated(JNIEnv*,NativeHostCppOriginalScope*,void *allocator,void *release,
        uintptr_t context,uint64_t address,uint64_t bytes,OwnerLink*,int unknown);
static NativeHostMemoryCall *native_memory_host_cpp_backing_prepare(JNIEnv*,NativeHostCppOriginalScope*,void *release,
        uintptr_t context,uint64_t address,int operation,OwnerLink*,int unknown,int *denied);
static void native_memory_host_cpp_scope_end(NativeHostCppOriginalScope*);
/* Only a Host wrapper whose actual caller/consumer matched the current custom
 * original scope tags a freshly returned Heap/Virtual/CRT/CPP/ARENA/OTHER
 * backing generation with its exact physical release, context and epoch.
 * The free bridge consumes that exact retained backing and child generations.
 * Real bulk operations use their ordinary family completion and include every
 * exact cooperative descendant. A FREE bridge uses backing_complete. Known
 * parents cannot fall through a refused realloc/decommit to a foreign row.
 * Other families and unrelated pointers return NULL/denied==0 unchanged. */
static void native_memory_host_custom_backing_allocated(NativeHostArenaOriginalScope*,int family,void *release,
        uintptr_t context,uint64_t address);
static NativeHostMemoryCall *native_memory_host_custom_backing_prepare(JNIEnv*,NativeHostArenaOriginalScope*,int family,
        void *release,uintptr_t context,uint64_t address,int operation,OwnerLink*,int unknown,int *denied);
/* Only the same thread's currently executing real provider function may pair
 * its own observed Windows backing call with this allocator lease. */
static void native_memory_host_backing_allocated(JNIEnv*,NativeHostArenaOriginalScope*,void *allocator,void *release,
        uint64_t address,uint64_t bytes,OwnerLink*,int unknown);
static NativeHostMemoryCall *native_memory_host_backing_prepare(JNIEnv*,NativeHostArenaOriginalScope*,void *release,
        uint64_t address,uint64_t bytes,int operation,OwnerLink*,int unknown,int *denied);
static void native_memory_host_backing_complete(JNIEnv*,NativeHostMemoryCall*,uint64_t bytes,int completed,int released);
static void host_arena_release_scoped_original(void *release,uint64_t address,NativeHostMemoryCall*);

/* Exact observed image incarnation; these opaque borrowed spans are never freed
 * independently. Retire follows the real image lifetime, not a numeric HMODULE. */
static void native_memory_image_span(JNIEnv*,void *imageTag,uint64_t address,uint64_t bytes,OwnerLink *owners,int unknown);
static int native_memory_image_busy(JNIEnv*,void *imageTag);
static int native_memory_image_prepare(JNIEnv*,void *imageTag);
static void native_memory_image_cancel(JNIEnv*,void *imageTag);
static void native_memory_image_retire(JNIEnv*,void *imageTag);
#endif
