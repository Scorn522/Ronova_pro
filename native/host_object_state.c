/* Included after memory.c. This is an explicit constructor/field/destructor
 * adapter, not an exception ABI hook or a C++ layout decoder. Raw CPU writes,
 * noncooperating constructors, vtables and unknown exception objects stay
 * opaque. Only observed allocations and actual adapter calls enter this path. */
#include "include/ronova_object_state.h"

typedef struct NativeObjectStateSpan {
    uint64_t token,address,bytes,generation;NativeMemoryAllocation *allocation;
    unsigned kind;struct NativeObjectStateSpan *next;
} NativeObjectStateSpan;
typedef struct NativeObjectState {
    uint64_t token,address,bytes,generation,destroyToken;NativeMemoryAllocation *allocation;
    HMODULE provider,providerPin;DWORD destroyThread;
    OwnerLink *sources,*providerSources;NativeObjectStateSpan *spans;unsigned references,uses;
    int destroyRequested,destroying,retired,uncertain;struct NativeObjectState *next;
} NativeObjectState;
typedef struct NativeObjectStateCall {
    uint64_t token,epoch;DWORD thread;int borrowing;
    NativeObjectState *object;NativeObjectStateSpan *span;NativeMemoryWrite write;
    struct NativeObjectStateCall *next;
} NativeObjectStateCall;
static SRWLOCK native_object_state_records=SRWLOCK_INIT;
static NativeObjectState *native_object_states;
static NativeObjectStateCall *native_object_state_calls;
static uint64_t native_object_state_sequence;

typedef struct NativeObjectStateErrors {DWORD windows;int crt;} NativeObjectStateErrors;
static NativeObjectStateErrors native_object_state_errors(void){
    NativeObjectStateErrors result;result.windows=GetLastError();result.crt=errno;return result;
}
static void native_object_state_errors_restore(NativeObjectStateErrors incoming){errno=incoming.crt;SetLastError(incoming.windows);}
#define NATIVE_OBJECT_RETURN(value) do {native_object_state_errors_restore(incoming);return (value);}while(0)

/* Source queries must not replace a real pending Java exception. The SDK's
 * caller, its runtime scopes and TLS/FLS retain the provenance; no provider
 * supplied source list, cleanup callback or ambient bypass is accepted. */
static int native_object_state_sources(void *caller,OwnerLink **sources,int *unknown,JNIEnv **environment){
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    int restricted=state&&(state->heapCallback||state->toolingRestricted||state->vmDetaching||state->vmThreadEnding
            ||state->hostUnloadImage||state->hostRelease||state->hostLoad&&state->hostLoad->calling);
    JNIEnv *env=host_memory_environment(restricted);if(environment)*environment=env;
    if(!state){*unknown=1;return 0;}
    if(restricted||!env){
        if(environment)*environment=NULL;
        if(!TryAcquireSRWLockShared(&host_imports)){*unknown=1;return 0;}
        if(!TryAcquireSRWLockShared(&native_records)){ReleaseSRWLockShared(&host_imports);*unknown=1;return 0;}
        DWORD error=ERROR_INVALID_DATA;
        int ready=host_callback_caller_locked(state,caller,sources,unknown,&error);
        if(ready)ready=host_callback_scopes_locked(state,sources,unknown,&error);
        for(unsigned i=0;i<state->depth;i++)if(state->stack[i]&&state->stack[i]->hostUnknown)*unknown=1;
        for(NativeRegistration *scope=state->registration;scope&&ready;scope=scope->previous){
            ready=native_owner_merge(sources,scope->owners);if(scope->unknown)*unknown=1;
        }
        host_producers(sources);
        ReleaseSRWLockShared(&native_records);ReleaseSRWLockShared(&host_imports);
        if(!ready||!*sources)*unknown=1;
        return ready;
    }
    jthrowable pending=env?native_original.ExceptionOccurred(env):NULL;
    if(pending)native_original.ExceptionClear(env);
    int ready=host_capture_query(env,state,caller,sources,unknown);
    if(env&&native_original.ExceptionCheck(env)){native_original.ExceptionClear(env);ready=0;}
    if(pending){native_original.Throw(env,pending);native_original.DeleteLocalRef(env,pending);}
    if(!ready||!*sources)*unknown=1;return ready;
}
static int native_object_state_range(uint64_t address,uint64_t bytes){
    if(!address||!bytes||bytes>SIZE_MAX||bytes-1>UINTPTR_MAX-(uintptr_t)address)return 0;
    return native_control_range((uintptr_t)address,(uintptr_t)(address+bytes-1),1)==0;
}
static int native_object_state_contains(uint64_t base,uint64_t bytes,uint64_t address,uint64_t length){
    return address>=base&&length<=bytes&&address-base<=bytes-length;
}
static int native_object_state_overlap(uint64_t first,uint64_t bytes,uint64_t other,uint64_t length){
    return first<other+length&&other<first+bytes;
}
/* allocation->gate is held; this only adds readers/uncertainty. In particular
 * it never changes the allocation's creators, opaque flag, or unknownUse to 0. */
static void native_object_state_contribute(NativeMemoryAllocation *allocation,OwnerLink *sources,int unknown){
    AcquireSRWLockExclusive(&native_memory_records);
    if(!native_owner_merge(&allocation->readers,sources)||unknown||!sources)InterlockedExchange(&allocation->unknownReaders,1);
    if(unknown)InterlockedExchange(&allocation->unknownUse,1);
    ReleaseSRWLockExclusive(&native_memory_records);
}
static uint64_t native_object_state_next_locked(void){return native_object_state_sequence==UINT64_MAX?0:++native_object_state_sequence;}
static void native_object_state_dispose(NativeObjectState *object){
    NativeObjectStateSpan *span=object->spans;while(span){NativeObjectStateSpan *next=span->next;free(span);span=next;}
    HMODULE pin=object->providerPin;native_owner_links_free(object->sources);native_owner_links_free(object->providerSources);free(object);
    if(pin&&!host_real_free_library(pin))InterlockedIncrement(&native_failures);
}
static NativeObjectState *native_object_state_acquire(uint64_t token,uint64_t generation){
    NativeObjectState *found=NULL;AcquireSRWLockExclusive(&native_object_state_records);
    for(NativeObjectState *object=native_object_states;object;object=object->next)
        if(!object->retired&&object->token==token&&(!generation||object->generation==generation)){object->references++;found=object;break;}
    ReleaseSRWLockExclusive(&native_object_state_records);return found;
}
static void native_object_state_release(NativeObjectState *object){
    int dispose=0;AcquireSRWLockExclusive(&native_object_state_records);
    if(object->references)object->references--;dispose=object->retired&&!object->references;
    ReleaseSRWLockExclusive(&native_object_state_records);if(dispose)native_object_state_dispose(object);
}
static int native_object_state_current(NativeObjectState *object){
    NativeMemoryAllocation *allocation=object->allocation;
    return InterlockedCompareExchange(&allocation->alive,0,0)&&allocation->generation==object->generation
            &&native_object_state_contains(allocation->address,allocation->bytes,object->address,object->bytes);
}
static void native_object_state_observed(NativeObjectState *object,OwnerLink *sources,int unknown){
    AcquireSRWLockExclusive(&native_object_state_records);
    if(!native_owner_merge(&object->sources,sources)||unknown)object->uncertain=1;
    ReleaseSRWLockExclusive(&native_object_state_records);
}
static __declspec(noinline) uint64_t native_object_state_constructed(uintptr_t address,uint64_t bytes){
    NativeObjectStateErrors incoming=native_object_state_errors();OwnerLink *sources=NULL;int unknown=0;
    void *caller=__builtin_return_address(0);HMODULE provider=native_image(caller),pin=NULL;
    native_object_state_sources(caller,&sources,&unknown,NULL);
    int pinned=provider&&GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,(LPCWSTR)caller,&pin)&&pin==provider;
    NativeObjectState *object=NULL;NativeMemoryAllocation *allocation=NULL;uint64_t token=0;
    if(native_object_state_range(address,bytes)){
        AcquireSRWLockShared(&native_memory_records);allocation=native_memory_find(address,bytes,0);ReleaseSRWLockShared(&native_memory_records);
    }
    if(allocation){
        EnterCriticalSection(&allocation->gate);native_object_state_contribute(allocation,sources,unknown);
        /* A different or incompletely observed caller may tighten borrowing,
         * but cannot declare another producer's storage to be its own object. */
        int ready=!unknown&&sources&&native_memory_same_sources(allocation->sources,sources)
                &&InterlockedCompareExchange(&allocation->alive,0,0)&&!InterlockedCompareExchange(&allocation->sealed,0,0)
                &&!InterlockedCompareExchange(&allocation->retiring,0,0)&&!InterlockedCompareExchange(&allocation->changing,0,0)
                &&!InterlockedCompareExchange(&allocation->restoring,0,0)
                &&!InterlockedCompareExchange(&allocation->unknownUse,0,0)&&!InterlockedCompareExchange(&allocation->unknownReaders,0,0)
                &&native_object_state_contains(allocation->address,allocation->bytes,address,bytes);
        ready=ready&&pinned;
        if(ready)object=(NativeObjectState*)calloc(1,sizeof(*object));
        if(object){
            object->allocation=allocation;object->generation=allocation->generation;object->address=address;object->bytes=bytes;
            object->provider=provider;object->providerPin=pin;pin=NULL;
            object->uncertain=InterlockedCompareExchange(&allocation->unknownUse,0,0)||InterlockedCompareExchange(&allocation->unknownReaders,0,0);
            ready=ready&&native_owner_merge(&object->providerSources,sources);
            object->sources=sources;sources=NULL;AcquireSRWLockExclusive(&native_object_state_records);
            for(NativeObjectState *old=native_object_states;old;old=old->next)
                if(!old->retired&&old->allocation==allocation&&old->generation==allocation->generation
                        &&native_object_state_overlap(old->address,old->bytes,address,bytes)){ready=0;break;}
            if(ready&&(token=native_object_state_next_locked())){
                object->token=token;object->next=native_object_states;native_object_states=object;
                InterlockedIncrement(&allocation->typedObjects);object=NULL;
            }
            ReleaseSRWLockExclusive(&native_object_state_records);
        }
        LeaveCriticalSection(&allocation->gate);
    }
    if(object){native_memory_unmapped(object->sources);native_object_state_dispose(object);}
    if(pin&&!host_real_free_library(pin))InterlockedIncrement(&native_failures);
    if(!token)native_memory_unmapped(sources);native_owner_links_free(sources);NATIVE_OBJECT_RETURN(token);
}
static uint64_t native_object_state_allocation(uint64_t token){
    NativeObjectStateErrors incoming=native_object_state_errors();NativeObjectState *object=native_object_state_acquire(token,0);
    uint64_t result=object?(uint64_t)(uintptr_t)object->allocation:0;if(object)native_object_state_release(object);NATIVE_OBJECT_RETURN(result);
}
static uint64_t native_object_state_generation(uint64_t token){
    NativeObjectStateErrors incoming=native_object_state_errors();NativeObjectState *object=native_object_state_acquire(token,0);
    uint64_t result=object?object->generation:0;if(object)native_object_state_release(object);NATIVE_OBJECT_RETURN(result);
}
static __declspec(noinline) uint64_t native_object_state_span(uint64_t token,uint64_t generation,uintptr_t address,uint64_t bytes,uint32_t kind){
    NativeObjectStateErrors incoming=native_object_state_errors();OwnerLink *sources=NULL;int unknown=0;
    void *caller=__builtin_return_address(0);HMODULE provider=native_image(caller);native_object_state_sources(caller,&sources,&unknown,NULL);
    NativeObjectState *object=generation?native_object_state_acquire(token,generation):NULL;uint64_t result=0;
    if(object&&native_object_state_range(address,bytes)&&(kind==RONOVA_OBJECT_BYTES||kind==RONOVA_OBJECT_BORROWED_OBJECT)){
        NativeMemoryAllocation *allocation=object->allocation;EnterCriticalSection(&allocation->gate);
        native_object_state_contribute(allocation,sources,unknown);native_object_state_observed(object,sources,unknown);
        /* Subobjects outside this exact construction extent are never inferred.
         * A foreign registration is retained only as a borrowed exclusion. */
        if(native_object_state_current(object)&&native_object_state_contains(object->address,object->bytes,address,bytes)){
            NativeObjectStateSpan *span=(NativeObjectStateSpan*)calloc(1,sizeof(*span));
            if(span){
                span->allocation=allocation;span->generation=generation;span->address=address;span->bytes=bytes;
                span->kind=kind==RONOVA_OBJECT_BYTES&&provider==object->provider
                        &&!unknown&&native_memory_same_sources(object->providerSources,sources)
                        ?RONOVA_OBJECT_BYTES:RONOVA_OBJECT_BORROWED_OBJECT;
                AcquireSRWLockExclusive(&native_object_state_records);
                if(!object->destroyRequested&&!object->destroying&&!object->retired&&(result=native_object_state_next_locked())){
                    span->token=result;span->next=object->spans;object->spans=span;span=NULL;
                }
                ReleaseSRWLockExclusive(&native_object_state_records);if(span)free(span);
            }
        }
        LeaveCriticalSection(&allocation->gate);
    }
    if(object){
        if(!result){AcquireSRWLockExclusive(&native_object_state_records);object->uncertain=1;ReleaseSRWLockExclusive(&native_object_state_records);native_memory_unmapped(sources);}
        native_object_state_release(object);
    }
    native_owner_links_free(sources);NATIVE_OBJECT_RETURN(result);
}
static uint64_t native_object_state_begin(uint64_t token,uint64_t generation,uint64_t spanToken,int borrowing,void *caller){
    NativeObjectStateErrors incoming=native_object_state_errors();OwnerLink *sources=NULL;int unknown=0;JNIEnv *env=NULL;
    HMODULE provider=native_image(caller);
    native_object_state_sources(caller,&sources,&unknown,&env);
    NativeObjectState *object=generation?native_object_state_acquire(token,generation):NULL;NativeObjectStateCall *call=NULL;uint64_t result=0;
    if(object){
        NativeMemoryAllocation *allocation=object->allocation;EnterCriticalSection(&allocation->gate);
        native_object_state_contribute(allocation,sources,unknown);native_object_state_observed(object,sources,unknown);
        NativeObjectStateSpan *span=NULL;AcquireSRWLockShared(&native_object_state_records);
        if(!object->destroyRequested&&!object->destroying&&!object->retired)for(span=object->spans;span;span=span->next)if(span->token==spanToken)break;
        int borrowed=0;
        if(span&&!borrowing)for(NativeObjectStateSpan *other=object->spans;other;other=other->next)
            if(other->kind==RONOVA_OBJECT_BORROWED_OBJECT&&native_object_state_overlap(other->address,other->bytes,span->address,span->bytes)){borrowed=1;break;}
        ReleaseSRWLockShared(&native_object_state_records);
        int ready=span&&native_object_state_current(object)&&!InterlockedCompareExchange(&allocation->sealed,0,0)
                &&!InterlockedCompareExchange(&allocation->retiring,0,0)&&!InterlockedCompareExchange(&allocation->changing,0,0)
                &&!InterlockedCompareExchange(&allocation->restoring,0,0)&&!native_owner_stopped(sources)
                &&!native_owner_stopped(object->sources)&&!unknown&&sources&&native_memory_same_sources(object->providerSources,sources)
                &&(borrowing||span->kind==RONOVA_OBJECT_BYTES)
                &&native_object_state_range(span->address,span->bytes)&&provider==object->provider
                &&span->allocation==allocation&&span->generation==generation&&!borrowed;
        if(!borrowing)ready=ready&&!unknown&&!InterlockedCompareExchange(&allocation->writers,0,0)
                &&!InterlockedCompareExchange(&allocation->reading,0,0)&&!native_memory_buffer_aliases(env,allocation);
        if(ready)call=(NativeObjectStateCall*)calloc(1,sizeof(*call));
        if(call){
            call->object=object;call->span=span;call->borrowing=borrowing;call->thread=GetCurrentThreadId();
            call->write.allocation=allocation;call->write.address=span->address;call->write.length=span->bytes;
            call->write.generation=generation;call->write.sources=sources;sources=NULL;call->write.unknown=unknown;
            if(!borrowing){call->epoch=++allocation->writeEpoch;call->write.image=native_memory_image(&call->write);ready=call->write.image!=NULL;}
            if(ready){
                AcquireSRWLockExclusive(&native_object_state_records);
                if(!object->destroyRequested&&!object->destroying&&!object->retired&&(result=native_object_state_next_locked())){
                    call->token=result;call->next=native_object_state_calls;native_object_state_calls=call;object->uses++;
                    InterlockedIncrement(&allocation->users);InterlockedIncrement(borrowing?&allocation->reading:&allocation->writers);call=NULL;
                }
                ReleaseSRWLockExclusive(&native_object_state_records);
            }
            if(call){native_owner_links_free(call->write.sources);native_memory_image_release(call->write.image);free(call);}
        }
        if(!result&&!borrowing){
            InterlockedExchange(&allocation->unknownUse,1);native_memory_unmapped(sources);
            AcquireSRWLockExclusive(&native_object_state_records);object->uncertain=1;ReleaseSRWLockExclusive(&native_object_state_records);
        }
        LeaveCriticalSection(&allocation->gate);
        if(!result)native_object_state_release(object);
    }
    native_owner_links_free(sources);NATIVE_OBJECT_RETURN(result);
}
static __declspec(noinline) uint64_t native_object_state_write_begin(uint64_t token,uint64_t generation,uint64_t span){
    return native_object_state_begin(token,generation,span,0,__builtin_return_address(0));
}
static __declspec(noinline) uint64_t native_object_state_borrow_begin(uint64_t token,uint64_t generation,uint64_t span){
    return native_object_state_begin(token,generation,span,1,__builtin_return_address(0));
}
static NativeObjectStateCall *native_object_state_take(uint64_t token,int borrowing,HMODULE provider,OwnerLink *sources,int unknown){
    NativeObjectStateCall *result=NULL;AcquireSRWLockExclusive(&native_object_state_records);
    NativeObjectStateCall **link=&native_object_state_calls;
    while(*link){NativeObjectStateCall *call=*link;if(call->token==token&&call->borrowing==borrowing
                &&call->thread==GetCurrentThreadId()&&call->object->provider==provider&&!unknown&&sources
                &&native_memory_same_sources(call->object->providerSources,sources)){
            *link=call->next;result=call;break;}link=&call->next;}
    ReleaseSRWLockExclusive(&native_object_state_records);return result;
}
static int native_object_state_end(uint64_t token,int completed,int borrowing,void *caller){
    NativeObjectStateErrors incoming=native_object_state_errors();HMODULE provider=native_image(caller);
    OwnerLink *sources=NULL;int unknown=0;native_object_state_sources(caller,&sources,&unknown,NULL);
    NativeObjectStateCall *call=native_object_state_take(token,borrowing,provider,sources,unknown);
    if(!call){native_owner_links_free(sources);NATIVE_OBJECT_RETURN(0);}
    NativeObjectState *object=call->object;NativeMemoryAllocation *allocation=object->allocation;int ready=0;
    EnterCriticalSection(&allocation->gate);native_object_state_contribute(allocation,sources,unknown);native_object_state_observed(object,sources,unknown);
    NativeMemoryWrite *write=&call->write;if(!native_owner_merge(&write->sources,sources))write->unknown=1;write->unknown|=unknown;
    ready=native_object_state_current(object)&&allocation->generation==write->generation&&provider==object->provider;
    if(!borrowing){
        int captured=ready&&write->image&&native_memory_read(write->address,write->image->after,(size_t)write->length);
        write->uncertain=!completed||call->epoch!=allocation->writeEpoch||InterlockedCompareExchange(&allocation->writers,0,0)!=1;
        if(captured){native_memory_range(write);ready=completed&&!write->uncertain&&!write->unknown&&!InterlockedCompareExchange(&allocation->unknownUse,0,0);}
        else ready=0;
        if(!ready){InterlockedExchange(&allocation->unknownUse,1);native_memory_unmapped(write->sources);}
        allocation->writeEpoch++;
    }
    InterlockedDecrement(borrowing?&allocation->reading:&allocation->writers);InterlockedDecrement(&allocation->users);
    AcquireSRWLockExclusive(&native_object_state_records);if(object->uses)object->uses--;if(!ready)object->uncertain=1;ReleaseSRWLockExclusive(&native_object_state_records);
    LeaveCriticalSection(&allocation->gate);native_owner_links_free(sources);native_owner_links_free(write->sources);
    native_memory_image_release(write->image);free(call);native_object_state_release(object);NATIVE_OBJECT_RETURN(ready);
}
static __declspec(noinline) int native_object_state_write_end(uint64_t token,int completed){return native_object_state_end(token,completed,0,__builtin_return_address(0));}
static __declspec(noinline) int native_object_state_borrow_end(uint64_t token){return native_object_state_end(token,1,1,__builtin_return_address(0));}
static __declspec(noinline) uint64_t native_object_state_destroy_begin(uint64_t token,uint64_t generation){
    NativeObjectStateErrors incoming=native_object_state_errors();OwnerLink *sources=NULL;int unknown=0;JNIEnv *env=NULL;
    void *caller=__builtin_return_address(0);HMODULE provider=native_image(caller);native_object_state_sources(caller,&sources,&unknown,&env);
    NativeObjectState *object=generation?native_object_state_acquire(token,generation):NULL;uint64_t result=0;
    if(object){
        NativeMemoryAllocation *allocation=object->allocation;EnterCriticalSection(&allocation->gate);
        native_object_state_contribute(allocation,sources,unknown);native_object_state_observed(object,sources,unknown);
        int ready=native_object_state_current(object)&&!native_memory_buffer_aliases(env,allocation)
                &&!InterlockedCompareExchange(&allocation->writers,0,0)&&!InterlockedCompareExchange(&allocation->reading,0,0)
                &&!InterlockedCompareExchange(&allocation->changing,0,0)&&!InterlockedCompareExchange(&allocation->restoring,0,0)
                &&provider==object->provider&&!unknown&&sources&&native_memory_same_sources(object->providerSources,sources);
        AcquireSRWLockExclusive(&native_object_state_records);
        if(provider==object->provider&&!unknown&&native_memory_same_sources(object->providerSources,sources))object->destroyRequested=1;
        if(ready&&!object->uses&&!object->destroying&&!object->retired&&(result=native_object_state_next_locked())){
            object->destroying=1;object->destroyToken=result;object->destroyThread=GetCurrentThreadId();
        }else object->uncertain=1;
        ReleaseSRWLockExclusive(&native_object_state_records);LeaveCriticalSection(&allocation->gate);
        if(!result)native_object_state_release(object);
        /* Success retains metadata only. No allocation users/changing lease
         * crosses the real destructor or its original provider free. */
    }
    native_owner_links_free(sources);NATIVE_OBJECT_RETURN(result);
}
static __declspec(noinline) int native_object_state_destroy_end(uint64_t token,int completed){
    NativeObjectStateErrors incoming=native_object_state_errors();NativeObjectState *object=NULL;
    void *caller=__builtin_return_address(0);HMODULE provider=native_image(caller);
    OwnerLink *sources=NULL;int unknown=0;native_object_state_sources(caller,&sources,&unknown,NULL);
    AcquireSRWLockExclusive(&native_object_state_records);
    for(NativeObjectState *entry=native_object_states;entry;entry=entry->next)
        if(entry->destroying&&entry->destroyToken==token&&entry->destroyThread==GetCurrentThreadId()&&entry->provider==provider
                &&!unknown&&sources&&native_memory_same_sources(entry->providerSources,sources)){
            object=entry;entry->destroyToken=0;break;}
    ReleaseSRWLockExclusive(&native_object_state_records);if(!object){native_owner_links_free(sources);NATIVE_OBJECT_RETURN(0);}
    NativeMemoryAllocation *allocation=object->allocation;EnterCriticalSection(&allocation->gate);
    native_object_state_contribute(allocation,sources,unknown);native_object_state_observed(object,sources,unknown);
    int ready=completed&&native_object_state_current(object)&&InterlockedCompareExchange(&allocation->typedObjects,0,0)>0;
    AcquireSRWLockExclusive(&native_object_state_records);
    if(ready){
        InterlockedDecrement(&allocation->typedObjects);object->retired=1;
        NativeObjectState **link=&native_object_states;while(*link&&*link!=object)link=&(*link)->next;if(*link)*link=object->next;
    }else object->uncertain=1;
    ReleaseSRWLockExclusive(&native_object_state_records);LeaveCriticalSection(&allocation->gate);
    native_owner_links_free(sources);native_object_state_release(object);NATIVE_OBJECT_RETURN(ready);
}

/* Called with allocation->gate held by the existing restore consumer. This
 * qualifies only a declared byte subrange, never the object's whole layout or
 * a raw-free right. Existing unknown-use/alias/generation gates still apply. */
static int native_object_state_selected_sources(OwnerLink *sources,OwnerLink *selected){
    for(OwnerLink *source=sources;source;source=source->next){
        if(!InterlockedCompareExchange(&source->owner->stopped,0,0))return 0;
        OwnerLink *target=selected;while(target&&target->owner!=source->owner)target=target->next;if(!target)return 0;
    }return 1;
}
static int native_object_state_restorable(NativeMemoryAllocation *allocation,NativeMemoryRange *range,OwnerLink *selected){
    int covered=0,blocked=0;uint64_t address=allocation->address+range->start;
    /* A provider declaration cannot turn an opaque live outside object into
     * writable state. Every actual creator and reader must already be stopped
     * and included in this exact cleanup selection. */
    if(!selected||!allocation->sources||InterlockedCompareExchange(&allocation->unknownUse,0,0)
            ||InterlockedCompareExchange(&allocation->unknownReaders,0,0)
            ||!native_object_state_selected_sources(allocation->sources,selected)
            ||!native_object_state_selected_sources(allocation->readers,selected))return 0;
    AcquireSRWLockShared(&native_object_state_records);
    for(NativeObjectState *object=native_object_states;object;object=object->next){
        if(object->retired||object->allocation!=allocation||object->generation!=allocation->generation)continue;
        if(!native_object_state_overlap(object->address,object->bytes,address,range->length))continue;
        if(object->destroyRequested||object->destroying||object->uses||object->uncertain
                ||!native_object_state_selected_sources(object->sources,selected)){blocked=1;break;}
        for(NativeObjectStateSpan *span=object->spans;span;span=span->next){
            if(span->allocation!=allocation||span->generation!=allocation->generation){blocked=1;break;}
            if(!native_object_state_overlap(span->address,span->bytes,address,range->length))continue;
            if(span->kind==RONOVA_OBJECT_BORROWED_OBJECT){blocked=1;break;}
            if(span->kind==RONOVA_OBJECT_BYTES&&native_object_state_contains(span->address,span->bytes,address,range->length))covered=1;
        }
        if(blocked)break;
    }
    ReleaseSRWLockShared(&native_object_state_records);return covered&&!blocked;
}
static void host_object_state_summary(JNIEnv *env,jobject module,jlong values[42]){
    AcquireSRWLockShared(&native_object_state_records);
    for(NativeObjectState *object=native_object_states;object;object=object->next){
        if(object->retired||module&&!native_owner_matches(env,object->sources,module))continue;
        int current=InterlockedCompareExchange(&object->allocation->alive,0,0)&&object->allocation->generation==object->generation;
        if(current)values[39]++;
        values[40]+=(jlong)object->uses+(object->destroying?1:0);
        if(!current||object->uncertain||object->destroyRequested||object->destroying)values[41]++;
    }
    ReleaseSRWLockShared(&native_object_state_records);
}

static const ronova_object_state_api_v1 native_object_state_api={
    sizeof(ronova_object_state_api_v1),RONOVA_OBJECT_STATE_ABI,
    native_object_state_constructed,native_object_state_allocation,native_object_state_generation,native_object_state_span,
    native_object_state_write_begin,native_object_state_write_end,native_object_state_borrow_begin,native_object_state_borrow_end,
    native_object_state_destroy_begin,native_object_state_destroy_end
};
__declspec(dllexport) __declspec(noinline) const ronova_object_state_api_v1 *RonovaObjectStateGetApi(uint32_t abi){
    NativeObjectStateErrors incoming=native_object_state_errors();OwnerLink *sources=NULL;int unknown=0;
    int ready=abi==RONOVA_OBJECT_STATE_ABI&&native_object_state_sources(__builtin_return_address(0),&sources,&unknown,NULL)
            &&!native_owner_stopped(sources);
    native_owner_links_free(sources);NATIVE_OBJECT_RETURN(ready?&native_object_state_api:NULL);
}
#undef NATIVE_OBJECT_RETURN
