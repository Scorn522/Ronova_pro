#ifndef RONOVA_ALLOCATOR_STATE_H
#define RONOVA_ALLOCATOR_STATE_H
#include <stddef.h>
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif

#define RONOVA_ALLOCATOR_STATE_ABI 1u
enum ronova_allocator_outcome {
    RONOVA_ALLOCATOR_FAILED=0,
    RONOVA_ALLOCATOR_SUCCESS=1,
    RONOVA_ALLOCATOR_UNRESOLVED=2,
    /* Only realloc whose actual documented result released its old block. */
    RONOVA_ALLOCATOR_RELEASED=3
};
enum ronova_allocator_bulk_operation {
    RONOVA_ALLOCATOR_RESET=1,
    RONOVA_ALLOCATOR_DESTROY=2
};

/* Cooperative observation of ORIGINAL allocator operations. No function here
 * allocates/frees the supplied storage or calls a client cleanup callback.
 * The actual SDK caller image and current Host/JNI sources identify a provider;
 * client-supplied source lists and numeric addresses provide no ownership.
 *
 * context describes a real existing allocator/pool handle; zero denotes the
 * adapter's default context. A receipt is not a raw memory permission.
 *
 * allocate_begin must precede the original allocation. With parent==0 its
 * successful result needs a real Host-observed Heap/Virtual/CRT backing return
 * from this original scope, or an exact still-live backing already retained
 * by this context. An explicit parent is a current same-provider receipt.
 * Every child must fit its exact parent generation without a live sibling
 * overlap. Unknown raw addresses produce no receipt. allocated is the
 * post-success shorthand ONLY for an explicit parent receipt.
 *
 * free/realloc/bulk begin refuse live aliases, typed objects and current users.
 * Run the original destructive operation only after a nonzero begin receipt.
 * Report its actual outcome once, on this same thread/provider. FAILED keeps
 * the old lifetime. Successful nonnull realloc replaces its generation even
 * at the same address. Exceptional/ambiguous completion uses UNRESOLVED and
 * retains an uncertain lifetime. A missing end remains pending, never freed.
 *
 * RESET reports a real pool reset, retires that context's member generations
 * and changes its context generation. DESTROY reports real destruction and
 * closes the context. Neither operation frees an unrelated backing context.
 * A separate backing receipt must surround the actual backing release.
 * CUSTOM storage is never automatically reclaimed by Ronova.
 */
typedef struct ronova_allocator_state_api_v1 {
    uint32_t size,abi;
    uint64_t (*context)(uintptr_t actual_context);
    uint64_t (*context_generation)(uint64_t context);
    uint64_t (*allocate_begin)(uint64_t context,uint64_t generation,uint64_t parent,uint64_t parent_generation);
    uint64_t (*allocate_end)(uint64_t call,uintptr_t returned,uint64_t bytes,uint32_t outcome);
    uint64_t (*allocated)(uint64_t context,uint64_t generation,uint64_t parent,uint64_t parent_generation,uintptr_t returned,uint64_t bytes);
    uint64_t (*allocation)(uint64_t receipt);
    uint64_t (*generation)(uint64_t receipt);
    uint64_t (*free_begin)(uint64_t receipt,uint64_t generation);
    int (*free_end)(uint64_t call,uint32_t outcome);
    uint64_t (*realloc_begin)(uint64_t receipt,uint64_t generation);
    uint64_t (*realloc_end)(uint64_t call,uintptr_t returned,uint64_t bytes,uint32_t outcome);
    uint64_t (*bulk_begin)(uint64_t context,uint64_t generation,uint32_t operation);
    int (*bulk_end)(uint64_t call,uint32_t outcome);
} ronova_allocator_state_api_v1;
typedef const ronova_allocator_state_api_v1 *(*ronova_allocator_state_get_api)(uint32_t abi);

#ifdef __cplusplus
}
#endif
#endif
