#ifndef RONOVA_OBJECT_STATE_H
#define RONOVA_OBJECT_STATE_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define RONOVA_OBJECT_STATE_ABI 1u
enum ronova_object_span_kind {
    /* Only explicitly exposed, independently byte-restorable fields. Never a
     * vptr, STL object, ownership handle, or an inferred class representation. */
    RONOVA_OBJECT_BYTES=1,
    RONOVA_OBJECT_BORROWED_OBJECT=2
};

/* The producer calls constructed only AFTER the actual constructor returns.
 * The tracker must already have observed the containing allocation. These
 * receipts retain that exact allocation row AND generation; an address cannot
 * create a row or provide ownership of another source's object.
 *
 * span only adds provenance or narrows borrowing. A borrowed-object declaration
 * excludes memcpy undo; borrow_begin/end additionally represent a live alias.
 * write_begin/end surround the actual field write and record its real bytes.
 * None of these functions writes the supplied object or calls a destructor.
 *
 * destroy_begin closes new object admissions and refuses active field/alias
 * leases. Call the ORIGINAL destructor only after it succeeds. Then call
 * destroy_end(true), and only afterwards the original allocation's free. A
 * failed/exceptional destructor leaves the object retained and unresolved.
 * Missing SDK / zero constructed receipt means uncovered original behavior;
 * it is never a successfully observed object.
 *
 * Scalar receipts avoid client-supplied output buffers in the control DLL.
 * A receipt is not an authorization token for raw memory or foreign objects.
 */
typedef struct ronova_object_state_api_v1 {
    uint32_t size,abi;
    uint64_t (*constructed)(uintptr_t object,uint64_t bytes);
    uint64_t (*allocation)(uint64_t object);
    uint64_t (*generation)(uint64_t object);
    uint64_t (*span)(uint64_t object,uint64_t generation,uintptr_t address,uint64_t bytes,uint32_t kind);
    uint64_t (*write_begin)(uint64_t object,uint64_t generation,uint64_t span);
    int (*write_end)(uint64_t write,int completed);
    uint64_t (*borrow_begin)(uint64_t object,uint64_t generation,uint64_t span);
    int (*borrow_end)(uint64_t borrow);
    uint64_t (*destroy_begin)(uint64_t object,uint64_t generation);
    int (*destroy_end)(uint64_t destroy,int completed);
} ronova_object_state_api_v1;

typedef const ronova_object_state_api_v1 *(*ronova_object_state_get_api)(uint32_t abi);

#ifdef __cplusplus
}
#endif
#endif
