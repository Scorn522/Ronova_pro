/* Included after memory.c. This is an explicit constructor/field/destructor
 * adapter, not an exception ABI hook or a C++ layout decoder. Raw CPU writes,
 * noncooperating constructors, vtables and unknown exception objects stay
 * opaque. Only observed allocations and actual adapter calls enter this path. */
#include "include/ronova_object_state.h"
enum {NATIVE_OBJECT_SCALAR=3,NATIVE_OBJECT_BORROWED_BUFFER=4,NATIVE_OBJECT_OWNED_BUFFER=5};

typedef struct NativeObjectStateSpan {
    uint64_t token,address,bytes,generation,epoch;NativeMemoryAllocation *allocation;
    unsigned kind,scalarKind,width,uses;int closed;struct NativeObjectStateSpan *next;
} NativeObjectStateSpan;
typedef struct NativeObjectState {
    uint64_t token,address,bytes,generation,destroyToken;NativeMemoryAllocation *allocation;
    HMODULE provider,providerPin;DWORD destroyThread;
    OwnerLink *sources,*providerSources;NativeObjectStateSpan *spans;unsigned references,uses;
    int destroyRequested,destroying,retired,uncertain;struct NativeObjectState *parent,*next;
} NativeObjectState;
typedef struct NativeObjectStateCall {
    uint64_t token,epoch,spanEpoch;DWORD thread;int borrowing;
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
static void native_object_state_release(NativeObjectState*);
static void native_object_state_dispose(NativeObjectState *object){
    NativeObjectStateSpan *span=object->spans;while(span){NativeObjectStateSpan *next=span->next;free(span);span=next;}
    HMODULE pin=object->providerPin;NativeObjectState *parent=object->parent;
    native_owner_links_free(object->sources);native_owner_links_free(object->providerSources);free(object);
    if(pin&&!host_real_free_library(pin))InterlockedIncrement(&native_failures);
    if(parent)native_object_state_release(parent);
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
/* object records are locked. Embedded metadata cannot reopen admissions after
 * a parent began the program's actual destructor. */
static int native_object_state_admitting(NativeObjectState *object){
    for(NativeObjectState *entry=object;entry;entry=entry->parent)
        if(entry->retired||entry->destroyRequested||entry->destroying)return 0;
    return 1;
}
static int native_object_state_descends(NativeObjectState *object,NativeObjectState *parent){
    for(NativeObjectState *entry=object;entry;entry=entry->parent)if(entry==parent)return 1;return 0;
}
static int native_object_state_provider(NativeObjectState *object,HMODULE provider,OwnerLink *sources,int unknown){
    return provider==object->provider&&!unknown&&sources&&native_memory_same_sources(object->providerSources,sources);
}
static int native_object_state_scalar_width(unsigned kind,uint64_t width){
    if(kind==RONOVA_FIELD_BOOL||kind==RONOVA_FIELD_BYTE)return width==1;
    if(kind==RONOVA_FIELD_FLOAT)return width==4||width==8;
    return (kind==RONOVA_FIELD_SIGNED||kind==RONOVA_FIELD_UNSIGNED||kind==RONOVA_FIELD_ENUM_SIGNED||kind==RONOVA_FIELD_ENUM_UNSIGNED)
            &&(width==1||width==2||width==4||width==8);
}
static int native_object_state_buffer_span(NativeObjectStateSpan *span){
    return span->kind==NATIVE_OBJECT_BORROWED_BUFFER||span->kind==NATIVE_OBJECT_OWNED_BUFFER;
}
static int native_object_state_span_current(NativeObjectState *object,NativeObjectStateSpan *span){
    NativeMemoryAllocation *target=span->allocation;
    return !span->closed&&native_object_state_current(object)&&InterlockedCompareExchange(&target->alive,0,0)
            &&target->generation==span->generation&&native_object_state_contains(target->address,target->bytes,span->address,span->bytes);
}
static void native_object_state_lock_pair(NativeMemoryAllocation *owner,NativeMemoryAllocation *target){
    if((uintptr_t)owner<=(uintptr_t)target){EnterCriticalSection(&owner->gate);if(target!=owner)EnterCriticalSection(&target->gate);}
    else {EnterCriticalSection(&target->gate);EnterCriticalSection(&owner->gate);}
}
static void native_object_state_unlock_pair(NativeMemoryAllocation *owner,NativeMemoryAllocation *target){
    if((uintptr_t)owner<=(uintptr_t)target){if(target!=owner)LeaveCriticalSection(&target->gate);LeaveCriticalSection(&owner->gate);}
    else {LeaveCriticalSection(&owner->gate);LeaveCriticalSection(&target->gate);}
}
/* records are locked; a typed field never overrides an explicitly borrowed
 * object/atomic/STL representation, including a span declared by its parent
 * or another exact owner of the same observed target allocation. */
static int native_object_state_excluded(NativeMemoryAllocation *allocation,uint64_t address,uint64_t bytes){
    for(NativeObjectState *object=native_object_states;object;object=object->next)if(!object->retired)
        for(NativeObjectStateSpan *span=object->spans;span;span=span->next)
            if(!span->closed&&span->allocation==allocation&&span->generation==allocation->generation
                    &&(span->kind==RONOVA_OBJECT_BORROWED_OBJECT||span->kind==NATIVE_OBJECT_BORROWED_BUFFER)
                    &&native_object_state_overlap(span->address,span->bytes,address,bytes))return 1;
    return 0;
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
                span->allocation=allocation;span->generation=generation;span->address=address;span->bytes=bytes;span->epoch=1;
                span->kind=kind==RONOVA_OBJECT_BYTES&&provider==object->provider
                        &&!unknown&&native_memory_same_sources(object->providerSources,sources)
                        ?RONOVA_OBJECT_BYTES:RONOVA_OBJECT_BORROWED_OBJECT;
                AcquireSRWLockExclusive(&native_object_state_records);
                if(native_object_state_admitting(object)&&(result=native_object_state_next_locked())){
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
static uint64_t native_object_state_add_typed(uint64_t token,uint64_t generation,uintptr_t address,uint64_t bytes,
        uint32_t scalarKind,uint32_t width,uint32_t relation,void *caller){
    NativeObjectStateErrors incoming=native_object_state_errors();OwnerLink *sources=NULL;int unknown=0;
    HMODULE provider=native_image(caller);native_object_state_sources(caller,&sources,&unknown,NULL);
    NativeObjectState *object=generation?native_object_state_acquire(token,generation):NULL;NativeMemoryAllocation *target=NULL;
    uint64_t result=0;int buffer=relation!=0,owned=relation==RONOVA_BUFFER_OWNED;
    int shape=native_object_state_scalar_width(scalarKind,width)&&native_object_state_range(address,bytes)
            &&(buffer?(relation==RONOVA_BUFFER_BORROWED||owned)&&bytes%width==0:bytes==width);
    if(object&&shape){
        NativeMemoryAllocation *owner=object->allocation;
        if(buffer){AcquireSRWLockShared(&native_memory_records);target=native_memory_find(address,bytes,0);ReleaseSRWLockShared(&native_memory_records);}
        else if(native_object_state_contains(object->address,object->bytes,address,bytes))target=owner;
        if(target){
            native_object_state_lock_pair(owner,target);native_object_state_contribute(owner,sources,unknown);
            if(target!=owner)native_object_state_contribute(target,sources,unknown);native_object_state_observed(object,sources,unknown);
            int ready=native_object_state_current(object)&&InterlockedCompareExchange(&target->alive,0,0)
                    &&native_object_state_contains(target->address,target->bytes,address,bytes)
                    &&!target->sealed&&!target->retiring&&!target->changing&&!target->restoring&&!target->writers
                    &&!owner->sealed&&!owner->retiring&&!owner->changing&&!owner->restoring
                    &&native_object_state_provider(object,provider,sources,unknown)
                    &&!native_owner_stopped(sources)&&!native_owner_stopped(object->sources);
            if(owned)ready=ready&&target!=owner&&!target->managed&&target->hostFamily!=NATIVE_MEMORY_HOST_IMAGE
                    &&!target->unknownUse&&!target->unknownReaders&&!target->releaseFailed
                    &&target->sources&&native_memory_same_sources(target->sources,object->providerSources);
            NativeObjectStateSpan *span=ready?(NativeObjectStateSpan*)calloc(1,sizeof(*span)):NULL;
            if(span){
                span->address=address;span->bytes=bytes;span->allocation=target;span->generation=target->generation;span->epoch=1;
                span->scalarKind=scalarKind;span->width=width;
                span->kind=!buffer?NATIVE_OBJECT_SCALAR:owned?NATIVE_OBJECT_OWNED_BUFFER:NATIVE_OBJECT_BORROWED_BUFFER;
                AcquireSRWLockExclusive(&native_object_state_records);
                ready=native_object_state_admitting(object);
                if(ready&&!buffer&&native_object_state_excluded(target,address,bytes))ready=0;
                if(ready&&owned)for(NativeObjectState *other=native_object_states;other;other=other->next)
                    if(!other->retired&&other->allocation==target&&other->generation==target->generation
                            &&native_object_state_overlap(other->address,other->bytes,address,bytes)){ready=0;break;}
                if(ready&&(result=native_object_state_next_locked())){
                    span->token=result;span->next=object->spans;object->spans=span;
                    if(buffer)InterlockedIncrement(&target->typedObjects);span=NULL;
                }
                ReleaseSRWLockExclusive(&native_object_state_records);free(span);
            }
            native_object_state_unlock_pair(owner,target);
        }
    }
    if(object){if(!result)native_memory_unmapped(sources);native_object_state_release(object);}
    native_owner_links_free(sources);NATIVE_OBJECT_RETURN(result);
}
static __declspec(noinline) uint64_t native_object_state_field(uint64_t token,uint64_t generation,uintptr_t address,uint64_t bytes,uint32_t kind){
    return native_object_state_add_typed(token,generation,address,bytes,kind,(uint32_t)bytes,0,__builtin_return_address(0));
}
static __declspec(noinline) uint64_t native_object_state_buffer(uint64_t token,uint64_t generation,uintptr_t address,uint64_t bytes,
        uint32_t kind,uint32_t width,uint32_t relation){
    return native_object_state_add_typed(token,generation,address,bytes,kind,width,relation,__builtin_return_address(0));
}
static __declspec(noinline) uint64_t native_object_state_nested(uint64_t token,uint64_t generation,uintptr_t address,uint64_t bytes){
    NativeObjectStateErrors incoming=native_object_state_errors();OwnerLink *sources=NULL;int unknown=0;
    void *caller=__builtin_return_address(0);HMODULE provider=native_image(caller),pin=NULL;
    native_object_state_sources(caller,&sources,&unknown,NULL);
    NativeObjectState *parent=generation?native_object_state_acquire(token,generation):NULL,*child=NULL;uint64_t result=0;
    if(parent&&native_object_state_range(address,bytes)&&native_object_state_contains(parent->address,parent->bytes,address,bytes)
            &&(address!=parent->address||bytes!=parent->bytes)&&native_object_state_provider(parent,provider,sources,unknown)
            &&GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,(LPCSTR)caller,&pin)&&pin==provider){
        NativeMemoryAllocation *allocation=parent->allocation;EnterCriticalSection(&allocation->gate);
        native_object_state_contribute(allocation,sources,unknown);native_object_state_observed(parent,sources,unknown);
        int ready=native_object_state_current(parent)&&!allocation->sealed&&!allocation->retiring&&!allocation->changing
                &&!allocation->restoring&&!allocation->writers&&!allocation->reading&&!native_owner_stopped(sources)
                &&!native_owner_stopped(parent->sources)&&!allocation->unknownUse&&!allocation->unknownReaders;
        if(ready)child=(NativeObjectState*)calloc(1,sizeof(*child));
        if(child){
            child->address=address;child->bytes=bytes;child->allocation=allocation;child->generation=generation;
            child->provider=provider;child->providerPin=pin;pin=NULL;
            ready=native_owner_merge(&child->providerSources,sources);child->sources=sources;sources=NULL;
            AcquireSRWLockExclusive(&native_object_state_records);ready=ready&&native_object_state_admitting(parent);
            for(NativeObjectState *old=native_object_states;ready&&old;old=old->next)
                if(!old->retired&&old->allocation==allocation&&old->generation==generation
                        &&native_object_state_overlap(old->address,old->bytes,address,bytes)
                        &&!native_object_state_descends(parent,old)){ready=0;break;}
            if(ready&&(result=native_object_state_next_locked())){
                child->token=result;child->parent=parent;parent->references++;child->next=native_object_states;native_object_states=child;
                InterlockedIncrement(&allocation->typedObjects);child=NULL;
            }
            ReleaseSRWLockExclusive(&native_object_state_records);
        }
        LeaveCriticalSection(&allocation->gate);
    }
    if(child)native_object_state_dispose(child);if(parent)native_object_state_release(parent);
    if(pin&&!host_real_free_library(pin))InterlockedIncrement(&native_failures);
    if(!result)native_memory_unmapped(sources);native_owner_links_free(sources);NATIVE_OBJECT_RETURN(result);
}
static __declspec(noinline) int native_object_state_buffer_end(uint64_t token,uint64_t generation,uint64_t spanToken){
    NativeObjectStateErrors incoming=native_object_state_errors();OwnerLink *sources=NULL;int unknown=0;JNIEnv *env=NULL;
    void *caller=__builtin_return_address(0);HMODULE provider=native_image(caller);native_object_state_sources(caller,&sources,&unknown,&env);
    NativeObjectState *object=generation?native_object_state_acquire(token,generation):NULL;NativeObjectStateSpan *span=NULL;int ready=0;
    if(object){
        AcquireSRWLockShared(&native_object_state_records);
        for(span=object->spans;span;span=span->next)if(span->token==spanToken&&!span->closed&&native_object_state_buffer_span(span))break;
        ReleaseSRWLockShared(&native_object_state_records);
        if(span){NativeMemoryAllocation *owner=object->allocation,*target=span->allocation;native_object_state_lock_pair(owner,target);
            native_object_state_contribute(owner,sources,unknown);if(target!=owner)native_object_state_contribute(target,sources,unknown);
            native_object_state_observed(object,sources,unknown);
            ready=native_object_state_span_current(object,span)&&native_object_state_provider(object,provider,sources,unknown)
                    &&!target->writers&&!target->reading&&!target->users&&!target->changing&&!target->restoring
                    &&!native_memory_buffer_aliases(env,target)&&target->typedObjects>0;
            AcquireSRWLockExclusive(&native_object_state_records);
            ready=ready&&!span->closed&&!span->uses&&!object->uses&&!object->retired;
            if(ready){span->closed=1;span->epoch++;InterlockedDecrement(&target->typedObjects);}
            ReleaseSRWLockExclusive(&native_object_state_records);native_object_state_unlock_pair(owner,target);
        }
        native_object_state_release(object);
    }
    native_owner_links_free(sources);NATIVE_OBJECT_RETURN(ready);
}
static __declspec(noinline) uint64_t native_object_state_epoch(uint64_t token,uint64_t generation,uint64_t spanToken){
    NativeObjectStateErrors incoming=native_object_state_errors();OwnerLink *sources=NULL;int unknown=0;
    void *caller=__builtin_return_address(0);HMODULE provider=native_image(caller);native_object_state_sources(caller,&sources,&unknown,NULL);
    NativeObjectState *object=generation?native_object_state_acquire(token,generation):NULL;uint64_t result=0;
    if(object){
        AcquireSRWLockShared(&native_object_state_records);
        for(NativeObjectStateSpan *span=object->spans;span;span=span->next)
            if(span->token==spanToken&&native_object_state_span_current(object,span)
                    &&native_object_state_provider(object,provider,sources,unknown)){result=span->epoch;break;}
        ReleaseSRWLockShared(&native_object_state_records);native_object_state_release(object);
    }
    native_owner_links_free(sources);NATIVE_OBJECT_RETURN(result);
}
static uint64_t native_object_state_begin(uint64_t token,uint64_t generation,uint64_t spanToken,int borrowing,void *caller){
    NativeObjectStateErrors incoming=native_object_state_errors();OwnerLink *sources=NULL;int unknown=0;JNIEnv *env=NULL;
    HMODULE provider=native_image(caller);
    native_object_state_sources(caller,&sources,&unknown,&env);
    NativeObjectState *object=generation?native_object_state_acquire(token,generation):NULL;NativeObjectStateCall *call=NULL;uint64_t result=0;
    if(object){
        NativeObjectStateSpan *span=NULL;AcquireSRWLockShared(&native_object_state_records);
        if(native_object_state_admitting(object))for(span=object->spans;span;span=span->next)if(span->token==spanToken&&!span->closed)break;
        ReleaseSRWLockShared(&native_object_state_records);
        NativeMemoryAllocation *owner=object->allocation,*allocation=span?span->allocation:owner;
        native_object_state_lock_pair(owner,allocation);native_object_state_contribute(owner,sources,unknown);
        if(owner!=allocation)native_object_state_contribute(allocation,sources,unknown);native_object_state_observed(object,sources,unknown);
        AcquireSRWLockShared(&native_object_state_records);
        int borrowed=span&&!borrowing&&native_object_state_excluded(allocation,span->address,span->bytes);
        int admitted=span&&!span->closed&&native_object_state_admitting(object);
        ReleaseSRWLockShared(&native_object_state_records);
        int ready=admitted&&native_object_state_span_current(object,span)&&!InterlockedCompareExchange(&allocation->sealed,0,0)
                &&!InterlockedCompareExchange(&allocation->retiring,0,0)&&!InterlockedCompareExchange(&allocation->changing,0,0)
                &&!InterlockedCompareExchange(&allocation->restoring,0,0)&&!native_owner_stopped(sources)
                &&!native_owner_stopped(object->sources)&&!unknown&&sources&&native_memory_same_sources(object->providerSources,sources)
                &&!native_owner_stopped(allocation->sources)&&!native_owner_stopped(allocation->readers)
                &&!allocation->unknownUse&&!allocation->unknownReaders
                &&!InterlockedCompareExchange(&allocation->writers,0,0)
                &&!owner->sealed&&!owner->retiring&&!owner->changing&&!owner->restoring
                &&(borrowing||span->kind==RONOVA_OBJECT_BYTES||span->kind==NATIVE_OBJECT_SCALAR||span->kind==NATIVE_OBJECT_OWNED_BUFFER)
                &&native_object_state_range(span->address,span->bytes)&&provider==object->provider
                &&span->allocation==allocation&&!borrowed;
        if(!borrowing)ready=ready&&!unknown&&!InterlockedCompareExchange(&allocation->writers,0,0)
                &&!InterlockedCompareExchange(&allocation->reading,0,0)&&!native_memory_buffer_aliases(env,allocation);
        if(ready)call=(NativeObjectStateCall*)calloc(1,sizeof(*call));
        if(call){
            call->object=object;call->span=span;call->borrowing=borrowing;call->thread=GetCurrentThreadId();
            call->write.allocation=allocation;call->write.address=span->address;call->write.length=span->bytes;
            call->write.generation=span->generation;call->write.sources=sources;sources=NULL;call->write.unknown=unknown;
            if(!borrowing){call->epoch=++allocation->writeEpoch;call->write.image=native_memory_image(&call->write);ready=call->write.image!=NULL;}
            if(ready){
                AcquireSRWLockExclusive(&native_object_state_records);
                if(!span->closed&&native_object_state_admitting(object)&&(result=native_object_state_next_locked())){
                    call->spanEpoch=++span->epoch;span->uses++;
                    call->token=result;call->next=native_object_state_calls;native_object_state_calls=call;object->uses++;
                    InterlockedIncrement(&allocation->users);InterlockedIncrement(borrowing?&allocation->reading:&allocation->writers);call=NULL;
                }
                ReleaseSRWLockExclusive(&native_object_state_records);
            }
            if(call){native_owner_links_free(call->write.sources);native_memory_image_release(call->write.image);free(call);}
        }
        if(!result&&!borrowing&&span&&span->kind==RONOVA_OBJECT_BYTES){
            InterlockedExchange(&allocation->unknownUse,1);native_memory_unmapped(sources);
            AcquireSRWLockExclusive(&native_object_state_records);object->uncertain=1;ReleaseSRWLockExclusive(&native_object_state_records);
        }
        native_object_state_unlock_pair(owner,allocation);
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
    NativeObjectState *object=call->object;NativeMemoryAllocation *owner=object->allocation,*allocation=call->write.allocation;int ready=0;
    native_object_state_lock_pair(owner,allocation);native_object_state_contribute(owner,sources,unknown);
    if(owner!=allocation)native_object_state_contribute(allocation,sources,unknown);native_object_state_observed(object,sources,unknown);
    NativeMemoryWrite *write=&call->write;if(!native_owner_merge(&write->sources,sources))write->unknown=1;write->unknown|=unknown;
    ready=native_object_state_span_current(object,call->span)&&allocation->generation==write->generation&&provider==object->provider;
    if(!borrowing){
        int captured=ready&&write->image&&native_memory_read(write->address,write->image->after,(size_t)write->length);
        write->uncertain=!completed||call->epoch!=allocation->writeEpoch||call->spanEpoch!=call->span->epoch
                ||InterlockedCompareExchange(&allocation->writers,0,0)!=1;
        if(captured){native_memory_range(write);ready=completed&&!write->uncertain&&!write->unknown&&!InterlockedCompareExchange(&allocation->unknownUse,0,0);}
        else ready=0;
        if(!ready){InterlockedExchange(&allocation->unknownUse,1);native_memory_unmapped(write->sources);}
        allocation->writeEpoch++;
    }
    InterlockedDecrement(borrowing?&allocation->reading:&allocation->writers);InterlockedDecrement(&allocation->users);
    AcquireSRWLockExclusive(&native_object_state_records);if(object->uses)object->uses--;if(call->span->uses)call->span->uses--;
    call->span->epoch++;if(!ready)object->uncertain=1;ReleaseSRWLockExclusive(&native_object_state_records);
    native_object_state_unlock_pair(owner,allocation);native_owner_links_free(sources);native_owner_links_free(write->sources);
    native_memory_image_release(write->image);free(call);native_object_state_release(object);NATIVE_OBJECT_RETURN(ready);
}
static __declspec(noinline) int native_object_state_write_end(uint64_t token,int completed){return native_object_state_end(token,completed,0,__builtin_return_address(0));}
static __declspec(noinline) int native_object_state_borrow_end(uint64_t token){return native_object_state_end(token,1,1,__builtin_return_address(0));}
/* owner gate and records are held. A target gate is only tried here: another
 * owner's oppositely ordered pair cannot deadlock the real destructor path. */
static int native_object_state_destroy_relations(JNIEnv *env,NativeObjectState *object,int completing){
    for(NativeObjectState *entry=native_object_states;entry;entry=entry->next){
        if(entry->retired||!native_object_state_descends(entry,object))continue;
        if(!native_object_state_current(entry)||entry->uses||(entry!=object&&entry->destroying))return 0;
        for(NativeObjectStateSpan *span=entry->spans;span;span=span->next){
            if(span->closed||!native_object_state_buffer_span(span))continue;
            if(completing)return 0;
            NativeMemoryAllocation *target=span->allocation;int locked=target==object->allocation;
            if(!locked&&!TryEnterCriticalSection(&target->gate))return 0;
            int ready=native_object_state_span_current(entry,span)&&!span->uses&&!target->writers&&!target->reading
                    &&!target->users&&!target->changing&&!target->restoring&&!native_memory_buffer_aliases(env,target);
            if(!locked)LeaveCriticalSection(&target->gate);if(!ready)return 0;
        }
    }
    return 1;
}
static __declspec(noinline) uint64_t native_object_state_destroy_begin(uint64_t token,uint64_t generation){
    NativeObjectStateErrors incoming=native_object_state_errors();OwnerLink *sources=NULL;int unknown=0;JNIEnv *env=NULL;
    void *caller=__builtin_return_address(0);HMODULE provider=native_image(caller);native_object_state_sources(caller,&sources,&unknown,&env);
    NativeObjectState *object=generation?native_object_state_acquire(token,generation):NULL;uint64_t result=0;
    if(object){
        NativeMemoryAllocation *allocation=object->allocation;EnterCriticalSection(&allocation->gate);
        native_object_state_contribute(allocation,sources,unknown);native_object_state_observed(object,sources,unknown);
        int ready=native_object_state_current(object)&&!native_memory_buffer_aliases(env,allocation)
                &&!InterlockedCompareExchange(&allocation->writers,0,0)&&!InterlockedCompareExchange(&allocation->reading,0,0)
                &&!InterlockedCompareExchange(&allocation->users,0,0)
                &&!InterlockedCompareExchange(&allocation->changing,0,0)&&!InterlockedCompareExchange(&allocation->restoring,0,0)
                &&provider==object->provider&&!unknown&&sources&&native_memory_same_sources(object->providerSources,sources);
        AcquireSRWLockExclusive(&native_object_state_records);
        ready=ready&&native_object_state_destroy_relations(env,object,0);
        if(native_object_state_provider(object,provider,sources,unknown))object->destroyRequested=1;
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
    NativeObjectState *retired=NULL;
    AcquireSRWLockExclusive(&native_object_state_records);
    ready=ready&&native_object_state_destroy_relations(NULL,object,1);
    if(ready){
        NativeObjectState **link=&native_object_states;
        while(*link){NativeObjectState *entry=*link;
            if(!entry->retired&&native_object_state_descends(entry,object)){
                *link=entry->next;entry->retired=1;entry->destroying=0;entry->references++;
                InterlockedDecrement(&allocation->typedObjects);entry->next=retired;retired=entry;
            }else link=&entry->next;
        }
    }else object->uncertain=1;
    ReleaseSRWLockExclusive(&native_object_state_records);LeaveCriticalSection(&allocation->gate);
    while(retired){NativeObjectState *entry=retired;retired=entry->next;native_object_state_release(entry);}
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
    int covered=0,blocked=0,relevant=0;uint64_t address=allocation->address+range->start;
    /* A provider declaration cannot turn an opaque live outside object into
     * writable state. Every actual creator and reader must already be stopped
     * and included in this exact cleanup selection. */
    AcquireSRWLockShared(&native_object_state_records);
    for(NativeObjectState *object=native_object_states;object;object=object->next){
        if(object->retired)continue;
        int touches=object->allocation==allocation&&native_object_state_overlap(object->address,object->bytes,address,range->length);
        for(NativeObjectStateSpan *span=object->spans;!touches&&span;span=span->next)
            touches=!span->closed&&span->allocation==allocation&&native_object_state_overlap(span->address,span->bytes,address,range->length);
        if(!touches)continue;relevant=1;
        NativeMemoryAllocation *owner=object->allocation;
        int ownerLocked=owner==allocation||TryEnterCriticalSection(&owner->gate);
        if(!ownerLocked){blocked=1;break;}
        int ownerReady=native_object_state_current(object)&&native_object_state_admitting(object)&&!object->uses&&!object->uncertain
                &&owner->sources&&!owner->unknownUse&&!owner->unknownReaders&&!owner->users&&!owner->writers&&!owner->reading&&!owner->changing
                &&(owner==allocation||!owner->restoring&&!owner->sealed&&!owner->retiring&&!native_memory_buffer_aliases(NULL,owner))
                &&selected&&native_object_state_selected_sources(object->sources,selected)
                &&native_object_state_selected_sources(owner->sources,selected)
                &&native_object_state_selected_sources(owner->readers,selected);
        if(owner!=allocation)LeaveCriticalSection(&owner->gate);
        if(!ownerReady){blocked=1;break;}
        for(NativeObjectStateSpan *span=object->spans;span;span=span->next){
            if(span->closed||span->allocation!=allocation)continue;
            if(!native_object_state_overlap(span->address,span->bytes,address,range->length))continue;
            if(!native_object_state_span_current(object,span)||span->uses||span->kind==RONOVA_OBJECT_BORROWED_OBJECT
                    ||span->kind==NATIVE_OBJECT_BORROWED_BUFFER){blocked=1;break;}
            if((span->kind==RONOVA_OBJECT_BYTES||span->kind==NATIVE_OBJECT_SCALAR||span->kind==NATIVE_OBJECT_OWNED_BUFFER)
                    &&native_object_state_contains(span->address,span->bytes,address,range->length))covered=1;
        }
        if(blocked)break;
    }
    ReleaseSRWLockShared(&native_object_state_records);
    if(!relevant)return !InterlockedCompareExchange(&allocation->opaque,0,0)||InterlockedCompareExchange(&allocation->managed,0,0);
    return covered&&!blocked&&selected&&allocation->sources&&!allocation->unknownUse&&!allocation->unknownReaders
            &&native_object_state_selected_sources(allocation->sources,selected)
            &&native_object_state_selected_sources(allocation->readers,selected);
}
static void native_object_state_restored(NativeMemoryAllocation *allocation,NativeMemoryRange *range){
    uint64_t address=allocation->address+range->start;AcquireSRWLockExclusive(&native_object_state_records);
    for(NativeObjectState *object=native_object_states;object;object=object->next)if(!object->retired&&native_object_state_current(object))
        for(NativeObjectStateSpan *span=object->spans;span;span=span->next)
            if(span->allocation==allocation&&native_object_state_span_current(object,span)
                    &&native_object_state_overlap(span->address,span->bytes,address,range->length))span->epoch++;
    ReleaseSRWLockExclusive(&native_object_state_records);
}
static void host_object_state_summary(JNIEnv *env,jobject module,jlong values[42]){
    AcquireSRWLockShared(&native_object_state_records);
    for(NativeObjectState *object=native_object_states;object;object=object->next){
        if(object->retired||module&&!native_owner_matches(env,object->sources,module))continue;
        int current=InterlockedCompareExchange(&object->allocation->alive,0,0)&&object->allocation->generation==object->generation;
        if(current)values[39]++;
        values[40]+=(jlong)object->uses+(object->destroying?1:0);
        int gap=!current||object->uncertain||object->destroyRequested||object->destroying;
        for(NativeObjectStateSpan *span=object->spans;!gap&&span;span=span->next)
            if(!span->closed&&!native_object_state_span_current(object,span))gap=1;
        if(gap)values[41]++;
    }
    ReleaseSRWLockShared(&native_object_state_records);
}

static const ronova_object_state_api_v1 native_object_state_api={
    sizeof(ronova_object_state_api_v1),RONOVA_OBJECT_STATE_ABI,
    native_object_state_constructed,native_object_state_allocation,native_object_state_generation,native_object_state_span,
    native_object_state_write_begin,native_object_state_write_end,native_object_state_borrow_begin,native_object_state_borrow_end,
    native_object_state_destroy_begin,native_object_state_destroy_end
};
static const ronova_object_state_api_v2 native_object_state_api_v2={
    {sizeof(ronova_object_state_api_v2),RONOVA_OBJECT_STATE_ABI_V2,
        native_object_state_constructed,native_object_state_allocation,native_object_state_generation,native_object_state_span,
        native_object_state_write_begin,native_object_state_write_end,native_object_state_borrow_begin,native_object_state_borrow_end,
        native_object_state_destroy_begin,native_object_state_destroy_end},
    native_object_state_field,native_object_state_nested,native_object_state_buffer,native_object_state_buffer_end,native_object_state_epoch
};
__declspec(dllexport) __declspec(noinline) const ronova_object_state_api_v1 *RonovaObjectStateGetApi(uint32_t abi){
    NativeObjectStateErrors incoming=native_object_state_errors();OwnerLink *sources=NULL;int unknown=0;
    int ready=(abi==RONOVA_OBJECT_STATE_ABI||abi==RONOVA_OBJECT_STATE_ABI_V2)
            &&native_object_state_sources(__builtin_return_address(0),&sources,&unknown,NULL)
            &&!native_owner_stopped(sources);
    native_owner_links_free(sources);
    NATIVE_OBJECT_RETURN(!ready?NULL:abi==RONOVA_OBJECT_STATE_ABI?&native_object_state_api:&native_object_state_api_v2.base);
}
#undef NATIVE_OBJECT_RETURN
