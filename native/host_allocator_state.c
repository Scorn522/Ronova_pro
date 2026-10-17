/* Cooperative custom allocator lifetimes. Included after memory/object state.
 * This observes actual original calls; it neither invokes client cleanup code
 * nor grants a scalar address authority over an unobserved allocation. */
#include "include/ronova_allocator_state.h"
typedef struct NativeAllocatorContext {
    uint64_t token,epoch;uintptr_t actual;HMODULE provider,pin;OwnerLink *sources;
    NativeHostCustomAnchor *anchors;int retired,uncertain;struct NativeAllocatorContext *next;
} NativeAllocatorContext;
typedef struct NativeAllocatorReceipt {
    uint64_t token,generation;NativeAllocatorContext *context;NativeMemoryAllocation *allocation;
    struct NativeAllocatorReceipt *next;
} NativeAllocatorReceipt;
typedef struct NativeAllocatorCall {
    uint64_t token;DWORD thread;int operation;NativeAllocatorContext *context;NativeAllocatorReceipt *receipt;
    NativeMemoryAllocation *parent;uint64_t parentGeneration;NativeHostMemoryCall *memory;
    NativeHostArenaOriginalScope scope;struct NativeAllocatorCall *next;
} NativeAllocatorCall;
static SRWLOCK native_allocator_state_records=SRWLOCK_INIT;
static NativeAllocatorContext *native_allocator_contexts;
static NativeAllocatorReceipt *native_allocator_receipts;
static NativeAllocatorCall *native_allocator_calls;
static uint64_t native_allocator_sequence;
#define NATIVE_ALLOCATOR_RETURN(value) do{native_object_state_errors_restore(incoming);return (value);}while(0)
static uint64_t native_allocator_next(void){
    uint64_t value;AcquireSRWLockExclusive(&native_allocator_state_records);
    value=native_allocator_sequence==UINT64_MAX?0:++native_allocator_sequence;
    ReleaseSRWLockExclusive(&native_allocator_state_records);return value;
}
static int native_allocator_caller(void *caller,HMODULE *provider,OwnerLink **sources,JNIEnv **env,int entering){
    int unknown=0;*provider=native_image(caller);
    /* The common SDK query keeps every real contributor and qualifies this
     * exact observed caller as a producer or an actually bound Core image. */
    int ready=*provider&&native_object_state_sources(caller,sources,&unknown,env)&&!unknown&&*sources;
    return ready&&(!entering||!native_owner_stopped(*sources));
}
static NativeAllocatorContext *native_allocator_context_find(uint64_t token){
    NativeAllocatorContext *found=NULL;AcquireSRWLockShared(&native_allocator_state_records);
    for(NativeAllocatorContext *entry=native_allocator_contexts;entry;entry=entry->next)
        if(entry->token==token&&!entry->retired){found=entry;break;}
    ReleaseSRWLockShared(&native_allocator_state_records);return found;
}
static NativeAllocatorReceipt *native_allocator_receipt_find(uint64_t token){
    NativeAllocatorReceipt *found=NULL;AcquireSRWLockShared(&native_allocator_state_records);
    for(NativeAllocatorReceipt *entry=native_allocator_receipts;entry;entry=entry->next)if(entry->token==token){found=entry;break;}
    ReleaseSRWLockShared(&native_allocator_state_records);return found;
}
static int native_allocator_current(NativeAllocatorReceipt *receipt){
    return receipt&&!receipt->context->retired&&!receipt->context->uncertain&&receipt->allocation->alive
            &&receipt->allocation->generation==receipt->generation&&receipt->allocation->hostFamily==NATIVE_MEMORY_HOST_CUSTOM
            &&receipt->allocation->release==(void*)receipt->context->provider;
}
static int native_allocator_identity(NativeAllocatorContext *context,HMODULE provider,OwnerLink *sources){
    return context&&!context->retired&&!context->uncertain&&context->provider==provider
            &&native_memory_same_sources(context->sources,sources);
}
static __declspec(noinline) uint64_t native_allocator_context(uintptr_t actual){
    NativeObjectStateErrors incoming=native_object_state_errors();void *caller=__builtin_return_address(0);
    OwnerLink *sources=NULL;HMODULE provider=NULL,pin=NULL;JNIEnv *env=NULL;uint64_t result=0;
    int ready=native_allocator_caller(caller,&provider,&sources,&env,1);
    if(ready){AcquireSRWLockShared(&native_allocator_state_records);
        for(NativeAllocatorContext *entry=native_allocator_contexts;entry;entry=entry->next)
            if(!entry->retired&&entry->provider==provider&&entry->actual==actual){
                if(native_allocator_identity(entry,provider,sources))result=entry->token;ready=0;break;}
        ReleaseSRWLockShared(&native_allocator_state_records);}
    if(ready&&GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,(LPCSTR)caller,&pin)&&pin==provider){
        NativeAllocatorContext *context=(NativeAllocatorContext*)calloc(1,sizeof(*context));int denied=0;
        uint64_t token=context?native_allocator_next():0;
        NativeHostMemoryCall *call=token?native_memory_host_allocator_prepare(env,NATIVE_MEMORY_HOST_CUSTOM,(void*)provider,0,0,
                NATIVE_MEMORY_HOST_CONTEXT_CREATE,sources,0,&denied,NULL):NULL;
        if(call){native_memory_host_arena_complete(env,call,0,0,(uintptr_t)token,1,0);
            AcquireSRWLockShared(&native_memory_records);
            NativeHostArenaContext *heap=native_memory_arena_context(native_memory_arena_provider((void*)provider,0),(uintptr_t)token,0);
            uint64_t epoch=heap?heap->epoch:0;ReleaseSRWLockShared(&native_memory_records);
            if(epoch){context->token=token;context->epoch=epoch;context->actual=actual;context->provider=provider;
                AcquireSRWLockExclusive(&native_allocator_state_records);NativeAllocatorContext *winner=NULL;
                for(NativeAllocatorContext *old=native_allocator_contexts;old;old=old->next)
                    if(!old->retired&&old->provider==provider&&old->actual==actual){winner=old;break;}
                if(winner){if(native_allocator_identity(winner,provider,sources))result=winner->token;}
                else {context->pin=pin;pin=NULL;context->sources=sources;sources=NULL;
                    context->next=native_allocator_contexts;native_allocator_contexts=context;result=token;context=NULL;}
                ReleaseSRWLockExclusive(&native_allocator_state_records);
                if(winner){AcquireSRWLockExclusive(&native_memory_records);
                    NativeHostArenaContext *loser=native_memory_arena_context(native_memory_arena_provider((void*)provider,0),(uintptr_t)token,0);
                    if(loser&&loser->epoch==epoch)loser->retired=1;ReleaseSRWLockExclusive(&native_memory_records);}
            }}
        free(context);
    }
    if(pin)host_real_free_library(pin);native_owner_links_free(sources);NATIVE_ALLOCATOR_RETURN(result);
}
static uint64_t native_allocator_query(uint64_t token,int kind,void *caller){
    NativeObjectStateErrors incoming=native_object_state_errors();OwnerLink *sources=NULL;HMODULE provider=NULL;
    uint64_t result=0;if(native_allocator_caller(caller,&provider,&sources,NULL,0)){
        if(!kind){NativeAllocatorContext *context=native_allocator_context_find(token);
            if(native_allocator_identity(context,provider,sources))result=context->epoch;}
        else {NativeAllocatorReceipt *receipt=native_allocator_receipt_find(token);
            if(native_allocator_current(receipt)&&native_allocator_identity(receipt->context,provider,sources))
                result=kind==1?(uint64_t)(uintptr_t)receipt->allocation:receipt->generation;}
    }
    native_owner_links_free(sources);NATIVE_ALLOCATOR_RETURN(result);
}
static __declspec(noinline) uint64_t native_allocator_context_generation(uint64_t token){return native_allocator_query(token,0,__builtin_return_address(0));}
static __declspec(noinline) uint64_t native_allocator_allocation(uint64_t token){return native_allocator_query(token,1,__builtin_return_address(0));}
static __declspec(noinline) uint64_t native_allocator_generation(uint64_t token){return native_allocator_query(token,2,__builtin_return_address(0));}
static uint64_t native_allocator_begin(uint64_t contextToken,uint64_t epoch,uint64_t receiptToken,uint64_t generation,
        uint64_t parentToken,uint64_t parentGeneration,int operation,void *caller){
    NativeObjectStateErrors incoming=native_object_state_errors();OwnerLink *sources=NULL;HMODULE provider=NULL;JNIEnv *env=NULL;
    NativeAllocatorContext *context=native_allocator_context_find(contextToken);NativeAllocatorReceipt *receipt=NULL,*parent=NULL;
    if(receiptToken){receipt=native_allocator_receipt_find(receiptToken);context=receipt?receipt->context:NULL;}
    if(parentToken)parent=native_allocator_receipt_find(parentToken);
    int ready=native_allocator_caller(caller,&provider,&sources,&env,1)&&native_allocator_identity(context,provider,sources);
    if(ready&&receipt)ready=native_allocator_current(receipt)&&receipt->generation==generation;
    if(ready&&!receipt)ready=epoch&&context->epoch==epoch;
    if(ready&&parentToken)ready=native_allocator_current(parent)&&parent->generation==parentGeneration
            &&native_allocator_identity(parent->context,provider,sources);
    NativeAllocatorCall *call=ready?(NativeAllocatorCall*)calloc(1,sizeof(*call)):NULL;
    if(call){int denied=0;
        call->context=context;call->receipt=receipt;call->operation=operation;call->thread=GetCurrentThreadId();
        call->parent=parent?parent->allocation:NULL;call->parentGeneration=parent?parent->generation:0;
        call->memory=native_memory_host_custom_prepare(env,(void*)provider,(uintptr_t)context->token,context->epoch,
                receipt?receipt->allocation:NULL,receipt?receipt->generation:0,call->parent,call->parentGeneration,operation,sources,&denied);
        if(call->memory){AcquireSRWLockShared(&native_allocator_state_records);
            for(NativeHostCustomAnchor *anchor=context->anchors;anchor;anchor=anchor->next)
                if(!native_memory_custom_anchor_add(call->memory,anchor->allocation,anchor->generation))ready=0;
            ReleaseSRWLockShared(&native_allocator_state_records);
            NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
            uint64_t token=ready&&state?native_allocator_next():0;
            if(token){call->token=token;call->scope.image=provider;call->scope.original=caller;call->scope.release=(void*)provider;
                call->scope.call=call->memory;call->scope.thread=call->thread;call->scope.previous=state->hostArenaOriginal;
                AcquireSRWLockExclusive(&native_allocator_state_records);call->next=native_allocator_calls;native_allocator_calls=call;
                ReleaseSRWLockExclusive(&native_allocator_state_records);state->hostArenaOriginal=&call->scope;
                native_owner_links_free(sources);NATIVE_ALLOCATOR_RETURN(token);}
            native_memory_host_cancel(call->memory);
        }free(call);
    }
    native_owner_links_free(sources);NATIVE_ALLOCATOR_RETURN(0);
}
static __declspec(noinline) uint64_t native_allocator_allocate_begin(uint64_t context,uint64_t epoch,uint64_t parent,uint64_t generation){
    return native_allocator_begin(context,epoch,0,0,parent,generation,NATIVE_MEMORY_HOST_ALLOCATE,__builtin_return_address(0));
}
static __declspec(noinline) uint64_t native_allocator_free_begin(uint64_t receipt,uint64_t generation){
    return native_allocator_begin(0,0,receipt,generation,0,0,NATIVE_MEMORY_HOST_FREE,__builtin_return_address(0));
}
static __declspec(noinline) uint64_t native_allocator_realloc_begin(uint64_t receipt,uint64_t generation){
    return native_allocator_begin(0,0,receipt,generation,0,0,NATIVE_MEMORY_HOST_REALLOCATE,__builtin_return_address(0));
}
static __declspec(noinline) uint64_t native_allocator_bulk_begin(uint64_t context,uint64_t epoch,uint32_t operation){
    if(operation!=RONOVA_ALLOCATOR_RESET&&operation!=RONOVA_ALLOCATOR_DESTROY)return 0;
    return native_allocator_begin(context,epoch,0,0,0,0,
            operation==RONOVA_ALLOCATOR_RESET?NATIVE_MEMORY_HOST_ARENA_RESET:NATIVE_MEMORY_HOST_CONTEXT_FREE,__builtin_return_address(0));
}
static NativeAllocatorCall *native_allocator_end_call(uint64_t token,int operation,void *caller,JNIEnv **env){
    OwnerLink *sources=NULL;HMODULE provider=NULL;NativeAllocatorCall *call=NULL;
    if(!native_allocator_caller(caller,&provider,&sources,env,0)){native_owner_links_free(sources);return NULL;}
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    AcquireSRWLockExclusive(&native_allocator_state_records);
    for(NativeAllocatorCall **link=&native_allocator_calls;*link;link=&(*link)->next){NativeAllocatorCall *entry=*link;
        if(entry->token!=token)continue;
        if(entry->thread==GetCurrentThreadId()&&state&&state->hostArenaOriginal==&entry->scope
                &&entry->context->provider==provider&&native_memory_same_sources(entry->context->sources,sources)
                &&(operation<0?entry->operation==NATIVE_MEMORY_HOST_ARENA_RESET||entry->operation==NATIVE_MEMORY_HOST_CONTEXT_FREE:entry->operation==operation)){
            *link=entry->next;call=entry;
        }break;
    }
    ReleaseSRWLockExclusive(&native_allocator_state_records);native_owner_links_free(sources);return call;
}
static NativeMemoryAllocation *native_allocator_parent(NativeAllocatorCall *call,uint64_t address,uint64_t bytes,uint64_t *generation){
    NativeMemoryAllocation *found=NULL;if(!address||bytes&&!native_object_state_range(address,bytes))return NULL;
    AcquireSRWLockShared(&native_memory_records);
    if(call->parent&&call->parent->alive&&call->parent->generation==call->parentGeneration)found=call->parent;
    if(!found){
        for(NativeHostCustomAnchor *anchor=call->memory->customAnchors;anchor;anchor=anchor->next){NativeMemoryAllocation *row=anchor->allocation;
            if(row->alive&&row->generation==anchor->generation&&address>=row->address&&bytes<=row->bytes&&address-row->address<=row->bytes-bytes
                    &&(!found||row->bytes<found->bytes))found=row;}
        for(NativeMemoryAllocation *row=native_memory_allocations;row;row=row->next)
            if(row->alive&&row->hostCustomAllocation==call->memory&&address>=row->address&&bytes<=row->bytes
                    &&address-row->address<=row->bytes-bytes&&(!found||row->bytes<found->bytes))found=row;
    }
    if(found)*generation=found->generation;ReleaseSRWLockShared(&native_memory_records);return found;
}
static void native_allocator_retire(JNIEnv *env,NativeHostMemoryCall *call){
    for(NativeHostMemoryHold *held=call->holds;held;held=held->next){
        if(held->observing)continue;NativeMemoryAllocation *row=held->allocation;EnterCriticalSection(&row->gate);
        AcquireSRWLockExclusive(&native_memory_records);
        if(row->alive&&row->generation==held->generation)native_memory_released(env,row);
        ReleaseSRWLockExclusive(&native_memory_records);LeaveCriticalSection(&row->gate);
    }
}
static int native_allocator_collect_anchors(NativeAllocatorCall *call){
    NativeHostCustomAnchor *added=NULL;int ready=1;AcquireSRWLockExclusive(&native_memory_records);
    for(NativeMemoryAllocation *row=native_memory_allocations;row;row=row->next)
        if(row->alive&&row->hostCustomAllocation==call->memory){
            NativeHostCustomAnchor *anchor=(NativeHostCustomAnchor*)calloc(1,sizeof(*anchor));
            if(!anchor){ready=0;break;}anchor->allocation=row;anchor->generation=row->generation;anchor->next=added;added=anchor;
            /* These are real underlying Host returns, including pool metadata
             * retained after a failed suballocation. They are not guessed
             * children. The pool's actual release owns their later lifetime. */
            InterlockedOr(&row->unknownUse,2);InterlockedExchange(&row->retiring,1);
        }
    ReleaseSRWLockExclusive(&native_memory_records);
    AcquireSRWLockExclusive(&native_allocator_state_records);
    while(added){NativeHostCustomAnchor *anchor=added;added=anchor->next;anchor->next=call->context->anchors;call->context->anchors=anchor;}
    ReleaseSRWLockExclusive(&native_allocator_state_records);return ready;
}
static int native_allocator_call_close(NativeAllocatorCall *call,JNIEnv *env,int uncertain){
    if(!uncertain&&!native_allocator_collect_anchors(call))uncertain=1;
    NativeThread *state=(NativeThread*)TlsGetValue(native_tls);state->hostArenaOriginal=call->scope.previous;
    if(uncertain){AcquireSRWLockExclusive(&native_allocator_state_records);call->context->uncertain=1;ReleaseSRWLockExclusive(&native_allocator_state_records);
        AcquireSRWLockExclusive(&native_memory_records);native_memory_arena_uncertain_locked(call->memory->arenaProvider,(uintptr_t)call->context->token);
        for(NativeMemoryAllocation *row=native_memory_allocations;row;row=row->next)if(row->alive&&row->hostCustomAllocation==call->memory){
            InterlockedOr(&row->unknownUse,1);InterlockedExchange(&row->releaseFailed,1);InterlockedExchange(&row->retiring,1);}
        ReleaseSRWLockExclusive(&native_memory_records);
        native_memory_host_abort(env,call->memory);}
    else native_memory_host_cancel(call->memory);
    HMODULE pin=NULL;AcquireSRWLockExclusive(&native_allocator_state_records);
    if(call->context->retired){pin=call->context->pin;call->context->pin=NULL;}
    ReleaseSRWLockExclusive(&native_allocator_state_records);
    free(call);if(pin)host_real_free_library(pin);native_thread_try_release(state);return !uncertain;
}
static uint64_t native_allocator_allocation_end(uint64_t token,uintptr_t address,uint64_t bytes,uint32_t outcome,int operation,void *caller){
    NativeObjectStateErrors incoming=native_object_state_errors();JNIEnv *env=NULL;
    NativeAllocatorCall *call=native_allocator_end_call(token,operation,caller,&env);if(!call)NATIVE_ALLOCATOR_RETURN(0);
    uint64_t result=0;int uncertain=outcome>RONOVA_ALLOCATOR_RELEASED||outcome==RONOVA_ALLOCATOR_UNRESOLVED||call->memory->customFailure;
    if(outcome==RONOVA_ALLOCATOR_FAILED&&!uncertain)result=call->receipt?call->receipt->token:0;
    else if(operation==NATIVE_MEMORY_HOST_REALLOCATE&&outcome==RONOVA_ALLOCATOR_RELEASED&&!address&&!uncertain)
        native_allocator_retire(env,call->memory);
    else if(outcome==RONOVA_ALLOCATOR_SUCCESS&&!uncertain&&!address&&!bytes&&operation==NATIVE_MEMORY_HOST_ALLOCATE){
        /* A real zero-count standard allocator may return NULL. It exposes no
         * storage extent and creates no receipt, but is not an unknown free. */
    }else if(outcome==RONOVA_ALLOCATOR_SUCCESS&&!uncertain){
        uint64_t parentGeneration=0;NativeMemoryAllocation *parent=native_allocator_parent(call,address,bytes,&parentGeneration);
        NativeAllocatorReceipt *receipt=parent?(NativeAllocatorReceipt*)calloc(1,sizeof(*receipt)):NULL;
        uint64_t next=receipt?native_allocator_next():0;
        if(!next){free(receipt);receipt=NULL;uncertain=1;}
        if(receipt){NativeHostCustomAnchor *anchor=(NativeHostCustomAnchor*)calloc(1,sizeof(*anchor));
            if(!anchor){free(receipt);receipt=NULL;uncertain=1;}
            if(receipt){
            if(operation==NATIVE_MEMORY_HOST_REALLOCATE)native_allocator_retire(env,call->memory);
            NativeMemoryAllocation *row=native_memory_host_custom_publish(env,call->memory,parent,parentGeneration,address,bytes,next);
            if(row){receipt->token=next;receipt->generation=row->generation;receipt->context=call->context;receipt->allocation=row;
                anchor->allocation=parent;anchor->generation=parentGeneration;
                    AcquireSRWLockExclusive(&native_allocator_state_records);anchor->next=call->context->anchors;call->context->anchors=anchor;
                    receipt->next=native_allocator_receipts;native_allocator_receipts=receipt;ReleaseSRWLockExclusive(&native_allocator_state_records);
                    result=next;receipt=NULL;anchor=NULL;
            }else uncertain=1;free(anchor);
            free(receipt);
            }
        }
    }else uncertain=1;
    if(!native_allocator_call_close(call,env,uncertain))result=0;NATIVE_ALLOCATOR_RETURN(result);
}
static __declspec(noinline) uint64_t native_allocator_allocate_end(uint64_t token,uintptr_t address,uint64_t bytes,uint32_t outcome){
    return native_allocator_allocation_end(token,address,bytes,outcome,NATIVE_MEMORY_HOST_ALLOCATE,__builtin_return_address(0));
}
static __declspec(noinline) uint64_t native_allocator_realloc_end(uint64_t token,uintptr_t address,uint64_t bytes,uint32_t outcome){
    return native_allocator_allocation_end(token,address,bytes,outcome,NATIVE_MEMORY_HOST_REALLOCATE,__builtin_return_address(0));
}
static __declspec(noinline) uint64_t native_allocator_allocated(uint64_t context,uint64_t epoch,uint64_t parent,uint64_t generation,
        uintptr_t address,uint64_t bytes){
    NativeObjectStateErrors incoming=native_object_state_errors();uint64_t result=0;void *caller=__builtin_return_address(0);
    if(parent){uint64_t call=native_allocator_begin(context,epoch,0,0,parent,generation,NATIVE_MEMORY_HOST_ALLOCATE,caller);
        if(call)result=native_allocator_allocation_end(call,address,bytes,RONOVA_ALLOCATOR_SUCCESS,NATIVE_MEMORY_HOST_ALLOCATE,caller);}
    NATIVE_ALLOCATOR_RETURN(result);
}
static uint64_t native_allocator_release_end(uint64_t token,uint32_t outcome,int operation,void *caller){
    NativeObjectStateErrors incoming=native_object_state_errors();JNIEnv *env=NULL;
    NativeAllocatorCall *call=native_allocator_end_call(token,operation,caller,&env);if(!call)NATIVE_ALLOCATOR_RETURN(0);
    int uncertain=outcome>RONOVA_ALLOCATOR_SUCCESS||call->memory->customFailure;uint64_t result=!uncertain;
    if(!uncertain&&outcome==RONOVA_ALLOCATOR_SUCCESS){
        native_allocator_retire(env,call->memory);
        if(operation<0){AcquireSRWLockExclusive(&native_memory_records);
            NativeHostArenaContext *old=call->memory->arena;if(old)old->retired=1;
            NativeHostArenaContext *next=NULL;
            if(call->operation==NATIVE_MEMORY_HOST_ARENA_RESET){next=native_memory_arena_context(call->memory->arenaProvider,(uintptr_t)call->context->token,1);
                if(next){next->created=1;next->unknown=0;if(!native_owner_merge(&next->sources,call->context->sources))next->unknown=1;}}
            ReleaseSRWLockExclusive(&native_memory_records);AcquireSRWLockExclusive(&native_allocator_state_records);
            if(call->operation==NATIVE_MEMORY_HOST_CONTEXT_FREE)call->context->retired=1;
            else if(next&&!next->unknown)call->context->epoch=next->epoch;else uncertain=1;
            ReleaseSRWLockExclusive(&native_allocator_state_records);
        }
    }
    if(!native_allocator_call_close(call,env,uncertain))result=0;NATIVE_ALLOCATOR_RETURN(uncertain?0:result);
}
static __declspec(noinline) int native_allocator_free_end(uint64_t token,uint32_t outcome){return (int)native_allocator_release_end(token,outcome,NATIVE_MEMORY_HOST_FREE,__builtin_return_address(0));}
static __declspec(noinline) int native_allocator_bulk_end(uint64_t token,uint32_t outcome){return (int)native_allocator_release_end(token,outcome,-1,__builtin_return_address(0));}
static const ronova_allocator_state_api_v1 native_allocator_state_api={
    sizeof(ronova_allocator_state_api_v1),RONOVA_ALLOCATOR_STATE_ABI,native_allocator_context,native_allocator_context_generation,
    native_allocator_allocate_begin,native_allocator_allocate_end,native_allocator_allocated,native_allocator_allocation,native_allocator_generation,
    native_allocator_free_begin,native_allocator_free_end,native_allocator_realloc_begin,native_allocator_realloc_end,
    native_allocator_bulk_begin,native_allocator_bulk_end
};
__declspec(dllexport) __declspec(noinline) const ronova_allocator_state_api_v1 *RonovaAllocatorStateGetApi(uint32_t abi){
    NativeObjectStateErrors incoming=native_object_state_errors();OwnerLink *sources=NULL;HMODULE provider=NULL;
    int ready=abi==RONOVA_ALLOCATOR_STATE_ABI&&native_allocator_caller(__builtin_return_address(0),&provider,&sources,NULL,1);
    native_owner_links_free(sources);NATIVE_ALLOCATOR_RETURN(ready?&native_allocator_state_api:NULL);
}
#undef NATIVE_ALLOCATOR_RETURN
