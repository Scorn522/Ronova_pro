/* A raw address selects a live allocation only after its actual allocator has
 * returned it. Numeric addresses never manufacture producer provenance. */
typedef struct NativeMemoryImage {uint64_t start,length;size_t references;unsigned char *before,*after;} NativeMemoryImage;
typedef struct NativeMemoryInterest {Owner *owner;volatile LONG revisions;struct NativeMemoryInterest *next;} NativeMemoryInterest;
typedef struct NativeMemoryRevision {size_t references;OwnerLink *sources;NativeMemoryImage *image;struct NativeMemoryAllocation *allocation;uint64_t objectView;int complete,counted;struct NativeMemoryRevision *previous;} NativeMemoryRevision;
typedef struct NativeMemoryRange {uint64_t start,length;NativeMemoryRevision *revision;struct NativeMemoryRange *next;} NativeMemoryRange;
typedef struct NativeBufferAlias {jweak buffer;uint64_t address,bytes,generation;LONG users;int borrowed;struct NativeBufferAlias *next;} NativeBufferAlias;
typedef struct NativeHostMemoryContext {void *release;uintptr_t context;uint64_t epoch;LONG users,changing;int observed,created,retired,uncertain,unknown;OwnerLink *sources;struct NativeHostMemoryContext *next;} NativeHostMemoryContext;
typedef struct NativeMemoryImageState {void *tag;int retiring,retired;struct NativeMemoryImageState *next;} NativeMemoryImageState;
typedef struct NativeMemoryAllocation {uint64_t address,bytes,generation,writeEpoch;jobject allocator;jweak bufferRoot,bufferAction;void *release;OwnerLink *sources,*readers,*preserved;NativeMemoryRange *ranges;NativeMemoryInterest *interests;NativeBufferAlias *aliases;int hostFamily;void *hostAllocator,*imageTag,*hostBackingProvider,*hostStorageRelease;uintptr_t hostContext,hostFreeFlags,hostStorageContext;uint64_t hostContextEpoch;HMODULE hostReleasePin,hostStoragePin;NativeHostCppOriginalScope *hostCppScope;struct NativeMemoryAllocation *hostCustomParent;uint64_t hostCustomParentGeneration;NativeHostMemoryCall *hostCustomAllocation;CRITICAL_SECTION gate;volatile LONG alive,writers,reading,changing,users,sealed,retiring,unknownReaders,unknownUse,opaque,releaseFailed,managed,restoring,typedObjects;struct NativeMemoryAllocation *next;} NativeMemoryAllocation;
typedef struct NativeMemoryCall {NativeMemoryAllocation *allocation,*prepared;OwnerLink *sources;jobject allocator;jweak bufferRoot;void *release;uint64_t address,bytes,generation;int locked,admitted,known,unknown,managed,cleanup;} NativeMemoryCall;
typedef struct NativeMemoryWrite {NativeMemoryAllocation *allocation;OwnerLink *sources,*observed;NativeMemoryImage *image;uint64_t address,length,generation,objectView;int locked,written,reading,detached,unobserved,opaque,uncertain,unknown,admitted,initializing;struct NativeMemoryWrite *previous,*root;} NativeMemoryWrite;
typedef struct NativeBufferIoWrite {NativeMemoryWrite write;uint64_t epoch;jobject receipt,group;struct NativeBufferIoWrite *next;} NativeBufferIoWrite;
typedef struct NativeHostMemoryHold {NativeMemoryAllocation *allocation;uint64_t generation;int observing,borrowed,backing,dependent;struct NativeHostMemoryHold *next;} NativeHostMemoryHold;
typedef struct NativeHostCustomAnchor {NativeMemoryAllocation *allocation;uint64_t generation;struct NativeHostCustomAnchor *next;} NativeHostCustomAnchor;
typedef struct NativeHostArenaProvider {void *release;LONG users,changing;int uncertain;struct NativeHostArenaProvider *next;} NativeHostArenaProvider;
typedef struct NativeHostArenaContext {NativeHostArenaProvider *provider;uintptr_t context;uint64_t epoch;DWORD thread;int retired,uncertain,created,unknown;OwnerLink *sources;struct NativeHostArenaContext *next;} NativeHostArenaContext;
struct NativeHostMemoryCall {int family,operation,unknown,admissionUnknown;void *release;uintptr_t context;uint64_t address,bytes,contextEpoch;OwnerLink *sources,*admissions;NativeHostMemoryContext *heap,*preparedHeap;NativeHostMemoryHold *holds;NativeHostArenaProvider *arenaProvider;NativeHostArenaContext *arena;int arenaExclusive,backingLease,retirement,customFailure;void *backingProvider;NativeMemoryAllocation *backingAllocation;NativeHostMemoryCall *arenaOuter;NativeHostCustomAnchor *customAnchors;};
static int native_object_state_restorable(NativeMemoryAllocation*,NativeMemoryRange*,OwnerLink*);
static void native_object_state_restored(NativeMemoryAllocation*,NativeMemoryRange*);
static SRWLOCK native_memory_records=SRWLOCK_INIT;
static NativeMemoryAllocation *native_memory_allocations;
static OwnerLink *native_memory_unknown;
static NativeBufferIoWrite *native_buffer_io_writes;
static NativeHostMemoryContext *native_memory_host_contexts;
static uint64_t native_memory_host_context_epoch;
static NativeMemoryImageState *native_memory_images;
static NativeHostArenaProvider *native_memory_arena_providers;
static NativeHostArenaContext *native_memory_arena_contexts;
static void native_memory_released(JNIEnv*,NativeMemoryAllocation*);
static void native_memory_release_provider(NativeMemoryAllocation*);
static void native_memory_reap_references(JNIEnv*,NativeMemoryAllocation*);
static void native_memory_revision_release(NativeMemoryRevision*);
static int native_memory_arena_observe_hold(NativeHostMemoryCall*,NativeMemoryAllocation*);
static int native_memory_same_sources(OwnerLink*,OwnerLink*);
static int native_memory_custom_ancestor(NativeMemoryAllocation*,NativeMemoryAllocation*,uint64_t);
static int native_memory_host_backing_hold(JNIEnv*,NativeHostMemoryCall*,NativeMemoryAllocation*,int);
static int native_memory_host_custom_bulk_hold(JNIEnv*,NativeHostMemoryCall*,NativeMemoryAllocation*);

static int native_memory_preserved(NativeMemoryAllocation *allocation,Owner *owner){
    for(OwnerLink *source=allocation->preserved;source;source=source->next)if(source->owner==owner)return 1;return 0;
}
static int native_memory_creator_sources(NativeMemoryAllocation *allocation,OwnerLink **sources){
    for(OwnerLink *source=allocation->sources;source;source=source->next)
        if(!native_memory_preserved(allocation,source->owner)&&!native_owner_add(sources,source->owner))return 0;return 1;
}
static int native_memory_creator_matches(JNIEnv *env,NativeMemoryAllocation *allocation,jobject module){
    for(OwnerLink *source=allocation->sources;source;source=source->next)
        if(!native_memory_preserved(allocation,source->owner)&&(!module||native_original.IsSameObject(env,source->owner->module,module)))return 1;return 0;
}

static NativeMemoryAllocation *native_memory_find(uint64_t address,uint64_t length,int base){
    for(NativeMemoryAllocation *entry=native_memory_allocations;entry;entry=entry->next){
        if(!InterlockedCompareExchange(&entry->alive,0,0))continue;
        if(base?entry->address==address:address>=entry->address&&length<=entry->bytes&&address-entry->address<=entry->bytes-length)return entry;
    }return NULL;
}
static int native_memory_observed(uint64_t address,uint64_t length){
    AcquireSRWLockShared(&native_memory_records);
    int observed=native_memory_find(address,length,0)!=NULL;
    ReleaseSRWLockShared(&native_memory_records);return observed;
}
static int native_memory_buffer_aliases(JNIEnv *env,NativeMemoryAllocation *allocation){
    if(!env){for(NativeBufferAlias *alias=allocation->aliases;alias;alias=alias->next)if(alias->generation==allocation->generation)return 1;return 0;}
    int borrowed=0;NativeBufferAlias **link=&allocation->aliases;
    while(*link){NativeBufferAlias *alias=*link;
        if(!alias->users&&native_original.IsSameObject(env,alias->buffer,NULL)){
            *link=alias->next;native_original.DeleteWeakGlobalRef(env,alias->buffer);free(alias);continue;
        }
        if(alias->borrowed&&alias->generation==allocation->generation)borrowed=1;link=&alias->next;
    }
    return borrowed;
}
static void native_memory_buffer_aliases_clear(JNIEnv *env,NativeMemoryAllocation *allocation){
    NativeBufferAlias *alias=allocation->aliases;allocation->aliases=NULL;
    while(alias){NativeBufferAlias *next=alias->next;native_original.DeleteWeakGlobalRef(env,alias->buffer);free(alias);alias=next;}
}
/* Called only by an actual JNI direct-buffer entry. Unknown numeric spans stay unknown. */
static int native_memory_buffer_enter(JNIEnv *env,uint64_t address,uint64_t bytes,NativeMemoryAllocation **held){
    *held=NULL;if(!address||!bytes)return 1;AcquireSRWLockShared(&native_memory_records);NativeMemoryAllocation *allocation=native_memory_find(address,bytes,0);
    ReleaseSRWLockShared(&native_memory_records);if(!allocation)return 1;
    EnterCriticalSection(&allocation->gate);AcquireSRWLockExclusive(&native_memory_records);
    int ready=InterlockedCompareExchange(&allocation->alive,0,0)&&!InterlockedCompareExchange(&allocation->sealed,0,0)&&!InterlockedCompareExchange(&allocation->retiring,0,0)
            &&!InterlockedCompareExchange(&allocation->changing,0,0)&&address>=allocation->address&&bytes<=allocation->bytes&&address-allocation->address<=allocation->bytes-bytes;
    if(ready){InterlockedIncrement(&allocation->users);*held=allocation;}
    ReleaseSRWLockExclusive(&native_memory_records);LeaveCriticalSection(&allocation->gate);
    if(!ready)native_refuse(env,"BUFFER_NATIVE_STORAGE_LIFETIME_CHANGED");return ready;
}
static void native_memory_buffer_leave(NativeMemoryAllocation *allocation){
    if(!allocation)return;AcquireSRWLockExclusive(&native_memory_records);InterlockedDecrement(&allocation->users);ReleaseSRWLockExclusive(&native_memory_records);
}
static void native_memory_buffer_exposed(JNIEnv *env,NativeMemoryAllocation *allocation,jobjectArray sources){
    if(!allocation)return;EnterCriticalSection(&allocation->gate);OwnerLink *readers=NULL;int unknown=0;
    jsize count=sources?native_original.GetArrayLength(env,sources):0;
    AcquireSRWLockExclusive(&native_records);
    for(jsize i=0;i<count&&!native_original.ExceptionCheck(env);i++){
        jobject module=native_original.GetObjectArrayElement(env,sources,i);Owner *owner=module?native_owner(env,module):NULL;
        if(!owner||!native_owner_add(&readers,owner))unknown=1;if(module)native_original.DeleteLocalRef(env,module);
    }
    ReleaseSRWLockExclusive(&native_records);AcquireSRWLockExclusive(&native_memory_records);
    // An exposed pointer can be retained and changed by native instructions
    // with no observed write boundary. Old byte images cannot authorize undo.
    InterlockedExchange(&allocation->opaque,1);InterlockedExchange(&allocation->unknownUse,1);
    if(unknown||!readers||!native_owner_merge(&allocation->readers,readers))InterlockedExchange(&allocation->unknownReaders,1);
    ReleaseSRWLockExclusive(&native_memory_records);native_owner_links_free(readers);LeaveCriticalSection(&allocation->gate);
}
static int native_memory_buffer_observe(JNIEnv *env,jobject buffer,jobject action,uint64_t address,uint64_t bytes,jlong values[2]){
    if(!address||!bytes)return 1;
    AcquireSRWLockShared(&native_memory_records);NativeMemoryAllocation *allocation=native_memory_find(address,bytes,0);
    ReleaseSRWLockShared(&native_memory_records);if(!allocation)return 1;
    EnterCriticalSection(&allocation->gate);
    int ready=InterlockedCompareExchange(&allocation->alive,0,0)&&!InterlockedCompareExchange(&allocation->sealed,0,0)&&!InterlockedCompareExchange(&allocation->retiring,0,0)
            &&!InterlockedCompareExchange(&allocation->changing,0,0)&&address>=allocation->address&&bytes<=allocation->bytes&&address-allocation->address<=allocation->bytes-bytes;
    NativeBufferAlias *alias=NULL;int capacityFailed=0;
    if(ready){
        native_memory_buffer_aliases(env,allocation);
        for(alias=allocation->aliases;alias;alias=alias->next)if(native_original.IsSameObject(env,alias->buffer,buffer))break;
        if(alias)ready=alias->generation==allocation->generation&&alias->address==address&&alias->bytes==bytes;
        else{
            alias=(NativeBufferAlias*)calloc(1,sizeof(*alias));
            if(alias)alias->buffer=native_original.NewWeakGlobalRef(env,buffer);
            ready=alias&&alias->buffer&&!native_original.ExceptionCheck(env);
            if(ready){
                alias->address=address;alias->bytes=bytes;alias->generation=allocation->generation;
                alias->borrowed=!action||!allocation->bufferAction||!native_original.IsSameObject(env,allocation->bufferAction,action);
                alias->next=allocation->aliases;allocation->aliases=alias;
            }else{capacityFailed=1;if(alias){if(alias->buffer)native_original.DeleteWeakGlobalRef(env,alias->buffer);free(alias);alias=NULL;}}
        }
        if(ready){values[0]=(jlong)(uintptr_t)allocation;values[1]=(jlong)allocation->generation;}
    }
    if(capacityFailed){InterlockedExchange(&allocation->opaque,1);InterlockedExchange(&allocation->unknownUse,1);}
    LeaveCriticalSection(&allocation->gate);return ready||capacityFailed;
}
static int native_memory_buffer_use(JNIEnv *env,jobject buffer,jlong token,uint64_t generation,uint64_t address,uint64_t bytes,int retain){
    AcquireSRWLockShared(&native_memory_records);NativeMemoryAllocation *allocation=native_memory_allocations;
    while(allocation&&(jlong)(uintptr_t)allocation!=token)allocation=allocation->next;
    ReleaseSRWLockShared(&native_memory_records);if(!allocation)return 0;
    EnterCriticalSection(&allocation->gate);NativeBufferAlias *alias=allocation->aliases;
    while(alias&&!native_original.IsSameObject(env,alias->buffer,buffer))alias=alias->next;
    int ready=alias&&alias->generation==generation&&alias->address==address&&alias->bytes==bytes;
    AcquireSRWLockExclusive(&native_memory_records);
    if(retain){
        ready=ready&&InterlockedCompareExchange(&allocation->alive,0,0)&&allocation->generation==generation
                &&!InterlockedCompareExchange(&allocation->sealed,0,0)&&!InterlockedCompareExchange(&allocation->retiring,0,0)&&!InterlockedCompareExchange(&allocation->changing,0,0);
        if(ready){alias->users++;InterlockedIncrement(&allocation->users);}
    }else{
        ready=ready&&alias->users>0;
        if(ready){alias->users--;InterlockedDecrement(&allocation->users);}
    }
    ReleaseSRWLockExclusive(&native_memory_records);LeaveCriticalSection(&allocation->gate);
    if(!retain&&!allocation->alive)native_memory_reap_references(env,allocation);return ready;
}
JNIEXPORT jlongArray JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_observeBufferStorage0(JNIEnv *env,jclass type,jobject buffer,jobject action,jlong address,jlong bytes){
    jmethodID gate=native_controller?native_original.GetStaticMethodID(env,native_controller,"bufferStorageCaller","(Ljava/nio/Buffer;Ljava/lang/Object;JJ)Z"):NULL;
    if(!gate||!native_original.IsSameObject(env,type,native_controller)||!native_original.CallStaticBooleanMethod(env,native_controller,gate,buffer,action,address,bytes)||native_original.ExceptionCheck(env)){
        native_refuse(env,"ACTUAL_BUFFER_STORAGE_OBSERVATION_REQUIRED");return NULL;
    }
    if(!buffer||bytes<0){native_refuse(env,"ACTUAL_BUFFER_STORAGE_SPAN_REQUIRED");return NULL;}jlong values[2]={0,0};
    if(!native_memory_buffer_observe(env,buffer,action,(uint64_t)address,(uint64_t)bytes,values)){
        if(!native_original.ExceptionCheck(env))native_refuse(env,"BUFFER_NATIVE_STORAGE_OBSERVATION_CHANGED");return NULL;
    }
    jlongArray result=native_original.NewLongArray(env,2);if(result)native_original.SetLongArrayRegion(env,result,0,2,values);return result;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_bufferStorageUse0(JNIEnv *env,jclass type,jobject buffer,jlong token,jlong generation,jlong address,jlong bytes,jboolean retain){
    jmethodID gate=native_controller?native_original.GetStaticMethodID(env,native_controller,"bufferStorageUseCaller","(Ljava/nio/Buffer;JJJJZ)Z"):NULL;
    if(!gate||!native_original.IsSameObject(env,type,native_controller)||!native_original.CallStaticBooleanMethod(env,native_controller,gate,buffer,token,generation,address,bytes,retain)||native_original.ExceptionCheck(env)){
        native_refuse(env,"ACTUAL_BUFFER_STORAGE_USE_REQUIRED");return JNI_FALSE;
    }
    if(!buffer||!token||bytes<0)return JNI_FALSE;
    return native_memory_buffer_use(env,buffer,token,(uint64_t)generation,(uint64_t)address,(uint64_t)bytes,retain==JNI_TRUE)?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_exposeBufferStorage0(JNIEnv *env,jclass type,jobject buffer,jlong token,jlong generation,jlong address,jlong bytes,jobjectArray sources){
    jmethodID gate=native_controller?native_original.GetStaticMethodID(env,native_controller,"bufferStorageExposureCaller","(Ljava/nio/Buffer;JJJJ[Ljava/lang/Module;)Z"):NULL;
    if(!gate||!native_original.IsSameObject(env,type,native_controller)||!native_original.CallStaticBooleanMethod(env,native_controller,gate,buffer,token,generation,address,bytes,sources)||native_original.ExceptionCheck(env)){
        native_refuse(env,"ACTUAL_BUFFER_STORAGE_EXPOSURE_REQUIRED");return;
    }
    AcquireSRWLockShared(&native_memory_records);NativeMemoryAllocation *allocation=native_memory_allocations;
    while(allocation&&(jlong)(uintptr_t)allocation!=token)allocation=allocation->next;
    ReleaseSRWLockShared(&native_memory_records);int ready=0;
    if(allocation){
        EnterCriticalSection(&allocation->gate);
        for(NativeBufferAlias *alias=allocation->aliases;alias;alias=alias->next)
            if(alias->users>0&&alias->generation==(uint64_t)generation&&alias->address==(uint64_t)address&&alias->bytes==(uint64_t)bytes
                    &&native_original.IsSameObject(env,alias->buffer,buffer)&&InterlockedCompareExchange(&allocation->alive,0,0)&&allocation->generation==(uint64_t)generation){ready=1;break;}
        if(ready){
            native_memory_buffer_exposed(env,allocation,sources);
            if(native_original.ExceptionCheck(env)){native_original.ExceptionClear(env);InterlockedIncrement(&native_failures);}
        }
        LeaveCriticalSection(&allocation->gate);
    }
    if(!ready)native_refuse(env,"BUFFER_NATIVE_STORAGE_GENERATION_CHANGED");
}
static int native_memory_sources(JNIEnv *env,NativeThread *state,jobjectArray supplied,OwnerLink **sources,int *unknown,int capturedJava){
    if(capturedJava){
        // The authenticated Java memory boundary has already captured its
        // current Java contributors. Reobserve VM/native scopes, but do not
        // walk the same Java invocation again while establishing its lease.
        if(!native_capture_scope_sources(env,state,sources,unknown))return 0;
        host_producers(sources);
    }else if(!host_capture_query(env,state,NULL,sources,unknown))return 0;
    if(!supplied){if(!*sources)*unknown=1;return 1;}jsize count=native_original.GetArrayLength(env,supplied);int ready=1;
    AcquireSRWLockExclusive(&native_records);
    for(jsize i=0;i<count&&ready&&!native_original.ExceptionCheck(env);i++){
        jobject actual=native_original.GetObjectArrayElement(env,supplied,i);Owner *owner=actual?native_owner(env,actual):NULL;
        if(owner&&InterlockedCompareExchange(&owner->producer,0,0))ready=native_owner_add(sources,owner);
        if(!actual)*unknown=1;
        if(actual)native_original.DeleteLocalRef(env,actual);
    }
    ReleaseSRWLockExclusive(&native_records);if(!*sources)*unknown=1;return ready&&!native_original.ExceptionCheck(env);
}
static void native_memory_unmapped(OwnerLink *sources){
    AcquireSRWLockExclusive(&native_memory_records);if(!native_owner_merge(&native_memory_unknown,sources))InterlockedIncrement(&native_failures);ReleaseSRWLockExclusive(&native_memory_records);
}
static void native_memory_host_unobserved(OwnerLink *owners){native_memory_unmapped(owners);}
/* memory_records is held. Each row is one incarnation, not a mutable HANDLE
 * slot. HeapCreate can return a reused handle before an older HeapDestroy's
 * wrapper completes; that older token must still name its own counters/owners.
 * A borrowed row observes only an actual HeapAlloc/HeapDestroy call, never pool
 * geometry; a failed first call leaves no observed lifetime. */
static NativeHostMemoryContext *native_memory_host_context(void *release,uintptr_t context,int create){
    if(!release||!context)return NULL;
    for(NativeHostMemoryContext *entry=native_memory_host_contexts;entry;entry=entry->next)
        if(!entry->retired&&entry->release==release&&entry->context==context)return entry;
    if(!create||native_memory_host_context_epoch==UINT64_MAX)return NULL;
    NativeHostMemoryContext *entry=(NativeHostMemoryContext*)calloc(1,sizeof(*entry));if(!entry)return NULL;
    entry->release=release;entry->context=context;entry->epoch=++native_memory_host_context_epoch;
    entry->next=native_memory_host_contexts;native_memory_host_contexts=entry;return entry;
}
static void native_memory_host_context_uncertain_locked(NativeHostMemoryContext *heap){
    heap->uncertain=1;
    for(NativeMemoryAllocation *allocation=native_memory_allocations;allocation;allocation=allocation->next)
        if(allocation->alive&&allocation->hostFamily==NATIVE_MEMORY_HOST_HEAP&&allocation->release==heap->release
                &&allocation->hostContext==heap->context&&allocation->hostContextEpoch==heap->epoch){
            InterlockedExchange(&allocation->sealed,1);InterlockedExchange(&allocation->retiring,1);
            InterlockedExchange(&allocation->unknownUse,1);InterlockedExchange(&allocation->releaseFailed,1);
        }
}
/* A fresh real HeapCreate supersedes only this exact provider/handle. A handle
 * can already be enumerated/destroyed while this wrapper is returning. Newer
 * observations make that ordering ambiguous: keep it unobserved instead of
 * attaching an older creator to a possibly newer heap. */
static void native_memory_host_created_locked(NativeHostMemoryCall *call,uint64_t returned){
    NativeHostMemoryContext *entry=call->preparedHeap;
    if(!entry||!call->release||!returned||!call->contextEpoch)return;
    for(NativeHostMemoryContext *later=native_memory_host_contexts;later;later=later->next)
        if(later->release==call->release&&later->context==(uintptr_t)returned&&later->epoch>call->contextEpoch
                &&(later->observed||later->users||later->changing)){
            for(NativeHostMemoryContext *heap=native_memory_host_contexts;heap;heap=heap->next)
                if(!heap->retired&&heap->release==call->release&&heap->context==(uintptr_t)returned)native_memory_host_context_uncertain_locked(heap);
            return;
        }
    NativeHostMemoryContext *old=native_memory_host_context(call->release,(uintptr_t)returned,0);
    if(old){
        if(!old->changing)native_memory_host_context_uncertain_locked(old);
        old->retired=1;
    }
    call->preparedHeap=NULL;entry->release=call->release;entry->context=(uintptr_t)returned;
    entry->epoch=call->contextEpoch;entry->observed=entry->created=1;
    entry->unknown=call->unknown;entry->sources=call->sources;call->sources=NULL;
    entry->next=native_memory_host_contexts;native_memory_host_contexts=entry;
}
static NativeMemoryImageState *native_memory_image_state(void *tag,int create){
    for(NativeMemoryImageState *entry=native_memory_images;entry;entry=entry->next)if(entry->tag==tag)return entry;
    if(!create)return NULL;NativeMemoryImageState *entry=(NativeMemoryImageState*)calloc(1,sizeof(*entry));if(!entry)return NULL;
    entry->tag=tag;entry->next=native_memory_images;native_memory_images=entry;return entry;
}
/* These contexts describe public jemalloc arena indices actually returned by
 * arenas.create/arenas.lookup. They never describe a backing page subdivision.
 * The provider gate covers the gap between an allocator return and lookup:
 * no observed bulk command can discard that pointer before it is published. */
static NativeHostArenaProvider *native_memory_arena_provider(void *release,int create){
    for(NativeHostArenaProvider *entry=native_memory_arena_providers;entry;entry=entry->next)
        if(entry->release==release)return entry;
    if(!create||!release)return NULL;
    NativeHostArenaProvider *entry=(NativeHostArenaProvider*)calloc(1,sizeof(*entry));if(!entry)return NULL;
    entry->release=release;entry->next=native_memory_arena_providers;native_memory_arena_providers=entry;return entry;
}
static NativeHostArenaContext *native_memory_arena_context(NativeHostArenaProvider *provider,uintptr_t context,int create){
    if(!provider||!context)return NULL;
    for(NativeHostArenaContext *entry=native_memory_arena_contexts;entry;entry=entry->next)
        if(!entry->retired&&entry->provider==provider&&entry->context==context)return entry;
    if(!create||native_memory_host_context_epoch==UINT64_MAX)return NULL;
    NativeHostArenaContext *entry=(NativeHostArenaContext*)calloc(1,sizeof(*entry));if(!entry)return NULL;
    entry->provider=provider;entry->context=context;entry->epoch=++native_memory_host_context_epoch;entry->thread=GetCurrentThreadId();
    entry->next=native_memory_arena_contexts;native_memory_arena_contexts=entry;return entry;
}
static void native_memory_arena_uncertain_locked(NativeHostArenaProvider *provider,uintptr_t context){
    if(!provider)return;if(!context)provider->uncertain=1;
    for(NativeHostArenaContext *arena=native_memory_arena_contexts;arena;arena=arena->next)
        if(arena->provider==provider&&(!context||arena->context==context)&&!arena->retired)arena->uncertain=1;
    for(NativeMemoryAllocation *allocation=native_memory_allocations;allocation;allocation=allocation->next)
        if(allocation->alive&&(allocation->hostFamily==NATIVE_MEMORY_HOST_ARENA||allocation->hostFamily==NATIVE_MEMORY_HOST_OTHER
                    ||allocation->hostFamily==NATIVE_MEMORY_HOST_CUSTOM)&&allocation->release==provider->release
                &&(!context||allocation->hostContext==context)){
            InterlockedExchange(&allocation->unknownUse,1);InterlockedExchange(&allocation->releaseFailed,1);
            InterlockedExchange(&allocation->sealed,1);InterlockedExchange(&allocation->retiring,1);
            for(NativeMemoryAllocation *child=native_memory_allocations;child;child=child->next)
                if(child->alive&&child->hostFamily==NATIVE_MEMORY_HOST_CUSTOM
                        &&native_memory_custom_ancestor(child,allocation,allocation->generation)){
                    InterlockedOr(&child->unknownUse,1);InterlockedExchange(&child->releaseFailed,1);
                    InterlockedExchange(&child->sealed,1);InterlockedExchange(&child->retiring,1);
                }
        }
}
static void native_memory_release_provider(NativeMemoryAllocation *allocation){
    HMODULE pin=NULL,storagePin=NULL;
    if(TryEnterCriticalSection(&allocation->gate)){
        if(!InterlockedCompareExchange(&allocation->alive,0,0)&&!InterlockedCompareExchange(&allocation->changing,0,0)){
            pin=allocation->hostReleasePin;allocation->hostReleasePin=NULL;
            storagePin=allocation->hostStoragePin;allocation->hostStoragePin=NULL;
        }
        LeaveCriticalSection(&allocation->gate);
    }
    if(pin&&!host_real_free_library(pin))InterlockedIncrement(&native_failures);
    if(storagePin&&!host_real_free_library(storagePin))InterlockedIncrement(&native_failures);
}
static void native_memory_host_record(JNIEnv *env,int family,void *allocator,void *release,uintptr_t context,uintptr_t flags,
        uint64_t address,uint64_t bytes,OwnerLink *owners,int unknown,uint64_t contextEpoch,void *imageTag,void *backingProvider){
    if(!address)return;NativeMemoryAllocation *entry=(NativeMemoryAllocation*)calloc(1,sizeof(*entry));
    if(!entry){native_memory_unmapped(owners);return;}InitializeCriticalSection(&entry->gate);
    entry->hostFamily=family;entry->hostAllocator=allocator;entry->release=release;entry->hostContext=context;entry->hostFreeFlags=flags;entry->imageTag=imageTag;
    entry->hostBackingProvider=backingProvider;
    entry->address=address;entry->bytes=bytes==UINT64_MAX||bytes>UINT64_MAX-address?0:bytes;entry->generation=1;entry->opaque=entry->alive=1;
    if(unknown||!owners||bytes==UINT64_MAX||bytes>UINT64_MAX-address||!native_owner_merge(&entry->sources,owners))entry->unknownUse=1;
    if(family!=NATIVE_MEMORY_HOST_IMAGE){
        if(!release||!GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,(LPCWSTR)release,&entry->hostReleasePin))entry->unknownUse=1;
    }
    int accepted=1,arenaResult=0;NativeHostMemoryContext *heap=NULL;AcquireSRWLockExclusive(&native_memory_records);
    if(family==NATIVE_MEMORY_HOST_HEAP){
        heap=native_memory_host_context(release,context,0);
        if(!heap||!heap->observed||heap->changing||heap->uncertain||!contextEpoch||contextEpoch!=heap->epoch)accepted=0;
        else entry->hostContextEpoch=contextEpoch;
    }else if(family==NATIVE_MEMORY_HOST_ARENA||family==NATIVE_MEMORY_HOST_OTHER){
        NativeHostArenaProvider *provider=native_memory_arena_provider(release,0);
        NativeHostArenaContext *arena=native_memory_arena_context(provider,context,0);
        if(family==NATIVE_MEMORY_HOST_OTHER&&!context&&provider){
            /* A documented heap delete/thread_done abandons live storage.
             * mi_free still owns the block; no new heap is inferred for it. */
            if(provider->uncertain)entry->unknownUse=1;else arenaResult=provider->users>0&&entry->hostReleasePin!=NULL;
        }else if(!provider||!arena||!contextEpoch||arena->epoch!=contextEpoch)entry->unknownUse=1;
        else {entry->hostContextEpoch=contextEpoch;if(provider->uncertain||arena->uncertain)entry->unknownUse=1;
            else arenaResult=provider->users>0&&entry->hostReleasePin!=NULL;}
    }else if(family==NATIVE_MEMORY_HOST_IMAGE){
        NativeMemoryImageState *image=native_memory_image_state(imageTag,1);
        if(!image||image->retiring||image->retired)accepted=0;
        for(NativeMemoryAllocation *old=native_memory_allocations;accepted&&old;old=old->next)
            if(old->alive&&old->imageTag==imageTag&&old->address==address&&old->bytes==entry->bytes){
                if(!native_owner_merge(&old->sources,owners)||unknown)InterlockedExchange(&old->unknownUse,1);accepted=2;break;
            }
    }
    if(accepted==1){
        // A nested allocator may expose a sub-block of another observed
        // allocator. Keep both actual origins, but do not free either overlap
        // on the strength of a guessed physical allocator relationship.
        for(NativeMemoryAllocation *old=native_memory_allocations;old;old=old->next)if(old->alive&&entry->bytes&&old->bytes
                &&address<old->address+old->bytes&&old->address<address+entry->bytes){
            /* This child is an actual successful je_* result with its exact
             * live provider, actual sallocx extent and arenas.lookup epoch.
             * It may be wholly inside a separately observed backing storage
             * call. Keep that child's own free capability; the parent loses
             * independent reclamation authority. Neither a partial overlap
             * nor another arena child receives this exception. */
            if(arenaResult&&(old->hostFamily==NATIVE_MEMORY_HOST_HEAP||old->hostFamily==NATIVE_MEMORY_HOST_VIRTUAL
                        ||old->hostFamily==NATIVE_MEMORY_HOST_CRT)&&address>=old->address&&entry->bytes<=old->bytes
                    &&address-old->address<=old->bytes-entry->bytes){
                /* Only an actual backing return made inside this provider's
                 * original scope supplies a parent edge. Mere containment
                 * still freezes the older row but cannot anchor CUSTOM bytes. */
                if(old->hostBackingProvider==release&&!(old->unknownUse&~2)&&!old->unknownReaders&&!old->releaseFailed
                        &&native_memory_same_sources(old->sources,entry->sources)
                        &&(!entry->hostCustomParent||old->bytes<entry->hostCustomParent->bytes)){
                    entry->hostCustomParent=old;entry->hostCustomParentGeneration=old->generation;
                }
                /* Bit 2 is only the known backing relationship; OR retains
                 * every pre-existing ordinary unknown-use bit. */
                InterlockedOr(&old->unknownUse,2);InterlockedExchange(&old->retiring,1);continue;
            }
            /* An actual independent Windows heap may reuse payload pages
             * after the older original Destroy returned but before its token
             * finishes retiring rows. Those distinct incarnations are not a
             * nested allocator overlap and confer no authority over each other. */
            if(heap&&heap->created&&old->hostFamily==NATIVE_MEMORY_HOST_HEAP&&old->release==release
                    &&(old->hostContext!=context||old->hostContextEpoch!=contextEpoch)){
                NativeHostMemoryContext *prior=native_memory_host_contexts;
                while(prior&&(prior->release!=release||prior->context!=old->hostContext||prior->epoch!=old->hostContextEpoch))prior=prior->next;
                if(prior&&(prior->changing||prior->retired))continue;
            }
            InterlockedExchange(&old->unknownUse,1);entry->unknownUse=1;
        }
        entry->next=native_memory_allocations;native_memory_allocations=entry;
    }
    ReleaseSRWLockExclusive(&native_memory_records);
    if(accepted!=1){
        if(!accepted)native_memory_unmapped(owners);
        if(entry->hostReleasePin)host_real_free_library(entry->hostReleasePin);native_owner_links_free(entry->sources);DeleteCriticalSection(&entry->gate);free(entry);
    }
    (void)env;
}
static void native_memory_host_allocated(JNIEnv *env,int family,void *allocator,void *release,uintptr_t context,uintptr_t flags,
        uint64_t address,uint64_t bytes,OwnerLink *owners,int unknown){
    native_memory_host_record(env,family,allocator,release,context,flags,address,bytes,owners,unknown,0,NULL,NULL);
}
/* records is held and no original API is running under this short gate. */
static int native_memory_host_hold(JNIEnv *env,NativeHostMemoryCall *call,NativeMemoryAllocation *allocation){
    if(!TryEnterCriticalSection(&allocation->gate))return 0;
    int ready=allocation->alive&&!allocation->sealed&&!allocation->retiring&&!allocation->changing&&!allocation->users&&!allocation->typedObjects
            &&!allocation->writers&&!allocation->reading&&!native_memory_buffer_aliases(env,allocation);
    NativeHostMemoryHold *hold=ready?(NativeHostMemoryHold*)calloc(1,sizeof(*hold)):NULL;
    if(hold){hold->allocation=allocation;hold->generation=allocation->generation;hold->next=call->holds;call->holds=hold;
        InterlockedIncrement(&allocation->users);InterlockedIncrement(&allocation->changing);}
    LeaveCriticalSection(&allocation->gate);return hold!=NULL;
}
static void native_memory_host_drop_locked(NativeHostMemoryCall *call,int uncertain){
    if(uncertain)for(NativeHostMemoryCall *outer=call->arenaOuter;outer;outer=outer->arenaOuter)
        if(outer->family==NATIVE_MEMORY_HOST_CUSTOM)outer->customFailure=1;
    for(NativeHostMemoryHold *hold=call->holds;hold;hold=hold->next){NativeMemoryAllocation *allocation=hold->allocation;
        if(uncertain){InterlockedExchange(&allocation->unknownUse,1);InterlockedExchange(&allocation->releaseFailed,1);InterlockedExchange(&allocation->retiring,1);}
        if(!hold->borrowed){if(!hold->observing)InterlockedDecrement(&allocation->changing);InterlockedDecrement(&allocation->users);}
    }
    if(call->heap){
        NativeHostMemoryContext *heap=call->heap;call->heap=NULL;
        if(call->operation==NATIVE_MEMORY_HOST_CONTEXT_FREE){heap->changing=0;if(uncertain)heap->uncertain=1;}
        else heap->users--;
        /* Failed first observation has no heap lifetime to carry into a later
         * reused numeric handle. Concurrent successful calls set observed. */
        if(!heap->observed&&!heap->users&&!heap->changing)heap->retired=1;
    }
    if(call->arenaProvider){
        if(uncertain&&call->arenaExclusive)native_memory_arena_uncertain_locked(call->arenaProvider,call->context);
        if(call->arenaExclusive)call->arenaProvider->changing=0;else call->arenaProvider->users--;
        call->arenaProvider=NULL;
    }
}
static void native_memory_host_dispose(NativeHostMemoryCall *call){
    if(call->family==NATIVE_MEMORY_HOST_CUSTOM){AcquireSRWLockExclusive(&native_memory_records);
        for(NativeMemoryAllocation *row=native_memory_allocations;row;row=row->next)if(row->hostCustomAllocation==call)row->hostCustomAllocation=NULL;
        ReleaseSRWLockExclusive(&native_memory_records);}
    while(call->customAnchors){NativeHostCustomAnchor *anchor=call->customAnchors;call->customAnchors=anchor->next;free(anchor);}
    while(call->holds){NativeHostMemoryHold *hold=call->holds;call->holds=hold->next;native_memory_release_provider(hold->allocation);free(hold);}
    if(call->preparedHeap){native_owner_links_free(call->preparedHeap->sources);free(call->preparedHeap);}
    native_owner_links_free(call->sources);native_owner_links_free(call->admissions);free(call);
}
static int native_memory_host_known(NativeHostMemoryCall *call){return call&&call->holds!=NULL;}
static int native_memory_host_cleanup(NativeHostMemoryCall *call){
    if(!native_memory_host_known(call)||call->unknown||call->admissionUnknown||call->operation==NATIVE_MEMORY_HOST_CONTEXT_FREE)return 0;
    int ready=1;AcquireSRWLockShared(&native_memory_records);
    if(call->heap&&(call->heap->retired||call->heap->uncertain||call->contextEpoch!=call->heap->epoch))ready=0;
    for(NativeHostMemoryHold *hold=call->holds;ready&&hold;hold=hold->next){
        NativeMemoryAllocation *allocation=hold->allocation;
        LONG unknownUse=allocation->unknownUse;
        if(hold->backing||call->backingLease&&allocation->hostBackingProvider==call->backingProvider)unknownUse&=~2;
        if(!allocation->alive||allocation->generation!=hold->generation||!allocation->sources||unknownUse
                ||allocation->unknownReaders||allocation->releaseFailed||allocation->managed||allocation->hostFamily==NATIVE_MEMORY_HOST_IMAGE){ready=0;break;}
        for(OwnerLink *source=allocation->sources;source;source=source->next)
            if(!InterlockedCompareExchange(&source->owner->stopped,0,0)){ready=0;break;}
        for(OwnerLink *reader=allocation->readers;ready&&reader;reader=reader->next)
            if(!InterlockedCompareExchange(&reader->owner->stopped,0,0)){ready=0;break;}
    }
    ReleaseSRWLockShared(&native_memory_records);return ready;
}
static int native_memory_host_sources(NativeHostMemoryCall *call,OwnerLink **owners,int *unknown){
    if(!call)return 1;*unknown|=call->admissionUnknown;
    return native_owner_merge(owners,call->sources)&&native_owner_merge(owners,call->admissions);
}
static NativeHostMemoryCall *native_memory_host_prepare_outer(JNIEnv *env,int family,void *release,uintptr_t context,uint64_t address,uint64_t bytes,
        int operation,OwnerLink *owners,int unknown,int *denied,NativeHostMemoryCall *outer){
    *denied=0;NativeHostMemoryCall *call=(NativeHostMemoryCall*)calloc(1,sizeof(*call));
    if(!call){
        AcquireSRWLockShared(&native_memory_records);
        if(family==NATIVE_MEMORY_HOST_HEAP&&operation!=NATIVE_MEMORY_HOST_CONTEXT_CREATE
                &&native_memory_host_context(release,context,0))*denied=1;
        for(NativeMemoryAllocation *allocation=native_memory_allocations;allocation;allocation=allocation->next)if(allocation->alive
                &&(operation==NATIVE_MEMORY_HOST_CONTEXT_FREE?allocation->hostFamily==family&&allocation->hostContext==context&&allocation->release==release
                    :operation==NATIVE_MEMORY_HOST_DECOMMIT?allocation->hostFamily==family&&address>=allocation->address&&address-allocation->address<=allocation->bytes
                    :operation!=NATIVE_MEMORY_HOST_ALLOCATE&&allocation->address==address)){*denied=1;break;}
        ReleaseSRWLockShared(&native_memory_records);native_memory_unmapped(owners);return NULL;
    }
    call->family=family;call->operation=operation;call->release=release;call->context=context;call->address=address;call->bytes=bytes;call->unknown=call->admissionUnknown=unknown||!owners;
    call->arenaOuter=outer;
    if(!native_owner_merge(&call->sources,owners)){call->unknown=call->admissionUnknown=1;native_memory_unmapped(owners);}
    if(family==NATIVE_MEMORY_HOST_HEAP&&operation==NATIVE_MEMORY_HOST_CONTEXT_CREATE){
        /* Prepare all bookkeeping before the original, whose successful result
         * is an opaque heap handle, not a byte allocation. */
        call->preparedHeap=(NativeHostMemoryContext*)calloc(1,sizeof(*call->preparedHeap));
        if(call->preparedHeap){
            AcquireSRWLockExclusive(&native_memory_records);
            if(native_memory_host_context_epoch!=UINT64_MAX)call->contextEpoch=++native_memory_host_context_epoch;
            ReleaseSRWLockExclusive(&native_memory_records);
        }
        if(!call->preparedHeap||!call->contextEpoch){native_memory_unmapped(owners);native_memory_host_dispose(call);return NULL;}return call;
    }
    int ready=1,found=operation==NATIVE_MEMORY_HOST_ALLOCATE||operation==NATIVE_MEMORY_HOST_CONTEXT_FREE;
    AcquireSRWLockExclusive(&native_memory_records);
    if(family==NATIVE_MEMORY_HOST_HEAP){
        NativeHostMemoryContext *heap=native_memory_host_context(release,context,operation==NATIVE_MEMORY_HOST_ALLOCATE||operation==NATIVE_MEMORY_HOST_CONTEXT_FREE);
        found=heap!=NULL;
        if(heap){
            if(heap->changing||heap->uncertain||operation==NATIVE_MEMORY_HOST_CONTEXT_FREE&&heap->users)ready=0;
            else{
                call->heap=heap;call->contextEpoch=heap->epoch;
                if(operation==NATIVE_MEMORY_HOST_CONTEXT_FREE)heap->changing=1;else heap->users++;
                if(!native_owner_merge(&call->sources,heap->sources))call->unknown=call->admissionUnknown=1;
                call->unknown|=heap->unknown;call->admissionUnknown|=heap->unknown;
            }
        }
    }
    if(ready&&operation!=NATIVE_MEMORY_HOST_ALLOCATE){
        for(NativeMemoryAllocation *allocation=native_memory_allocations;allocation;allocation=allocation->next){
            if(!allocation->alive)continue;
            int match=operation==NATIVE_MEMORY_HOST_CONTEXT_FREE
                    ?call->heap&&allocation->hostFamily==family&&allocation->hostContext==context&&allocation->release==release
                        &&allocation->hostContextEpoch==call->contextEpoch
                    :operation==NATIVE_MEMORY_HOST_DECOMMIT
                    ?allocation->hostFamily==family&&address>=allocation->address&&address-allocation->address<=allocation->bytes
                    :allocation->address==address;
            if(!match)continue;
            if(operation!=NATIVE_MEMORY_HOST_OBSERVE&&allocation->hostFamily==NATIVE_MEMORY_HOST_CUSTOM
                    &&family!=NATIVE_MEMORY_HOST_CUSTOM){ready=0;break;}
            /* An extent observer has no release authority over a foreign
             * provider. Preserve that row exactly and call the original. */
            if(operation==NATIVE_MEMORY_HOST_OBSERVE&&(allocation->hostFamily!=family
                    ||allocation->hostContext!=context||allocation->release!=release
                    ||family==NATIVE_MEMORY_HOST_HEAP&&(!call->heap||allocation->hostContextEpoch!=call->contextEpoch)))continue;
            if(allocation->managed||allocation->hostFamily==NATIVE_MEMORY_HOST_IMAGE){ready=0;break;}
            if(allocation->hostFamily!=family||allocation->hostContext!=context||allocation->release!=release
                    ||family==NATIVE_MEMORY_HOST_HEAP&&(!call->heap||allocation->hostContextEpoch!=call->contextEpoch)){
                // A different provider can receive this scalar pointer during
                // an ordinary call, but it has no exact cleanup capability.
                // Its unobserved outcome must not leave the old row freeable.
                if(!native_memory_host_hold(env,call,allocation)){ready=0;break;}
                InterlockedExchange(&allocation->unknownUse,1);InterlockedExchange(&allocation->releaseFailed,1);InterlockedExchange(&allocation->retiring,1);
                /* That provider has no exact completion for this row. Close
                 * its views now and detach it from the new provider's token. */
                NativeHostMemoryHold *foreign=call->holds;call->holds=foreign->next;
                InterlockedDecrement(&allocation->changing);InterlockedDecrement(&allocation->users);free(foreign);
                call->unknown=1;break;
            }
            found=1;if(operation==NATIVE_MEMORY_HOST_CONTEXT_FREE){
                if(!native_memory_host_custom_bulk_hold(env,call,allocation)){ready=0;break;}continue;
            }
            if(operation==NATIVE_MEMORY_HOST_OBSERVE?!native_memory_arena_observe_hold(call,allocation)
                    :!native_memory_host_hold(env,call,allocation)){ready=0;break;}
            if(!native_memory_creator_sources(allocation,&call->admissions)||!native_owner_merge(&call->admissions,allocation->readers))call->admissionUnknown=1;
            call->admissionUnknown|=allocation->unknownUse||allocation->unknownReaders||allocation->releaseFailed;
            if(operation!=NATIVE_MEMORY_HOST_CONTEXT_FREE)break;
        }
    }
    if(family==NATIVE_MEMORY_HOST_HEAP&&operation!=NATIVE_MEMORY_HOST_ALLOCATE&&operation!=NATIVE_MEMORY_HOST_CONTEXT_FREE&&!call->holds)call->unknown=1;
    if(!ready)*denied=1;
    if(!ready||!found)native_memory_host_drop_locked(call,0);
    ReleaseSRWLockExclusive(&native_memory_records);
    if(!ready||!found){native_memory_host_dispose(call);return NULL;}return call;
}
static NativeHostMemoryCall *native_memory_host_prepare(JNIEnv *env,int family,void *release,uintptr_t context,uint64_t address,uint64_t bytes,
        int operation,OwnerLink *owners,int unknown,int *denied){
    return native_memory_host_prepare_outer(env,family,release,context,address,bytes,operation,owners,unknown,denied,NULL);
}
static void native_memory_host_complete(JNIEnv *env,NativeHostMemoryCall *call,uint64_t returned,uint64_t bytes,int completed,int released){
    if(!call)return;
    if(call->operation==NATIVE_MEMORY_HOST_CONTEXT_CREATE){
        if(completed&&returned){
            AcquireSRWLockExclusive(&native_memory_records);native_memory_host_created_locked(call,returned);ReleaseSRWLockExclusive(&native_memory_records);
            if(call->preparedHeap)native_memory_unmapped(call->sources);
        }
        native_memory_host_dispose(call);return;
    }
    OwnerLink *sources=NULL;int unknown=call->unknown;
    if(!native_owner_merge(&sources,call->sources))unknown=1;
    for(NativeHostMemoryHold *hold=call->holds;hold;hold=hold->next){
        NativeMemoryAllocation *allocation=hold->allocation;EnterCriticalSection(&allocation->gate);
        if(completed&&allocation->alive&&allocation->generation==hold->generation){
            if(call->operation==NATIVE_MEMORY_HOST_REALLOCATE&&returned){if(!native_owner_merge(&sources,allocation->sources))unknown=1;}
            AcquireSRWLockExclusive(&native_memory_records);
            if(released||call->operation==NATIVE_MEMORY_HOST_REALLOCATE&&returned)native_memory_released(env,allocation);
            else if(call->operation==NATIVE_MEMORY_HOST_DECOMMIT){
                allocation->generation++;allocation->writeEpoch++;InterlockedExchange(&allocation->opaque,1);
                NativeMemoryRange *range=allocation->ranges;allocation->ranges=NULL;
                while(range){NativeMemoryRange *next=range->next;native_memory_revision_release(range->revision);free(range);range=next;}
                if(env)native_memory_buffer_aliases_clear(env,allocation);
            }
            ReleaseSRWLockExclusive(&native_memory_records);
        }
        LeaveCriticalSection(&allocation->gate);
    }
    if(completed&&returned&&(call->operation==NATIVE_MEMORY_HOST_ALLOCATE||call->operation==NATIVE_MEMORY_HOST_REALLOCATE)){
        if(call->heap){AcquireSRWLockExclusive(&native_memory_records);call->heap->observed=1;ReleaseSRWLockExclusive(&native_memory_records);}
        /* Keep the context user until registration finishes. Otherwise a
         * successful destroy could fall between return and block publication. */
        native_memory_host_record(env,call->family,NULL,call->release,call->context,0,returned,bytes,sources,unknown,call->contextEpoch,NULL,NULL);
    }
    else if(completed&&call->family==NATIVE_MEMORY_HOST_HEAP&&call->operation==NATIVE_MEMORY_HOST_FREE&&!call->holds)native_memory_unmapped(sources);
    AcquireSRWLockExclusive(&native_memory_records);
    if(completed&&released&&call->operation==NATIVE_MEMORY_HOST_CONTEXT_FREE&&call->heap){call->heap->observed=1;call->heap->retired=1;}
    native_memory_host_drop_locked(call,0);ReleaseSRWLockExclusive(&native_memory_records);
    for(NativeHostMemoryHold *hold=call->holds;hold;hold=hold->next)native_memory_reap_references(env,hold->allocation);
    native_owner_links_free(sources);native_memory_host_dispose(call);
}
static void native_memory_host_abort(JNIEnv *env,NativeHostMemoryCall *call){
    (void)env;if(!call)return;AcquireSRWLockExclusive(&native_memory_records);native_memory_host_drop_locked(call,1);ReleaseSRWLockExclusive(&native_memory_records);
    native_memory_unmapped(call->sources);native_memory_host_dispose(call);
}
static void native_memory_host_cancel(NativeHostMemoryCall *call){
    if(!call)return;AcquireSRWLockExclusive(&native_memory_records);native_memory_host_drop_locked(call,0);
    ReleaseSRWLockExclusive(&native_memory_records);native_memory_host_dispose(call);
}
static int native_memory_host_cpp_scope(NativeHostCppOriginalScope *scope){
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    return state&&scope&&state->hostCppOriginal==scope&&scope->thread==GetCurrentThreadId()
            &&scope->image&&native_image(scope->original)==scope->image&&native_image(scope->release)==scope->image;
}
static void native_memory_host_cpp_backing_allocated(JNIEnv *env,NativeHostCppOriginalScope *scope,void *allocator,void *release,
        uintptr_t context,uint64_t address,uint64_t bytes,OwnerLink *owners,int unknown){
    (void)env;(void)allocator;(void)owners;(void)unknown;
    if(!address||!native_memory_host_cpp_scope(scope)||scope->operation!=NATIVE_MEMORY_HOST_ALLOCATE)return;
    AcquireSRWLockExclusive(&native_memory_records);
    for(NativeMemoryAllocation *row=native_memory_allocations;row;row=row->next)
        if(row->alive&&row->address==address&&row->hostFamily==NATIVE_MEMORY_HOST_CRT
                &&row->release==release&&row->hostContext==context){
            /* The CRT wrapper calls after its normal actual-result record.
             * No new row, extent lookup or generation is created here. */
            if(bytes!=UINT64_MAX&&bytes<=row->bytes&&!row->changing&&!row->sealed&&!row->retiring&&!row->managed)
                row->hostCppScope=scope;
            break;
        }
    ReleaseSRWLockExclusive(&native_memory_records);
}
static void native_memory_host_cpp_allocated(JNIEnv *env,NativeHostCppOriginalScope *scope,void *allocator,void *release,
        uintptr_t context,uint64_t address,uint64_t bytes,OwnerLink *owners,int unknown){
    if(!address)return;int promoted=0;HMODULE pin=NULL;
    int valid=native_memory_host_cpp_scope(scope)&&scope->operation==NATIVE_MEMORY_HOST_ALLOCATE
            &&scope->release==release&&scope->context==context&&scope->original==allocator;
    if(valid&&GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,(LPCSTR)release,&pin)){
        AcquireSRWLockExclusive(&native_memory_records);
        for(NativeMemoryAllocation *row=native_memory_allocations;row;row=row->next)
            if(row->alive&&row->address==address&&row->hostCppScope==scope&&row->hostFamily==NATIVE_MEMORY_HOST_CRT){
                if(TryEnterCriticalSection(&row->gate)){
                    int ready=bytes!=UINT64_MAX&&bytes<=row->bytes&&!row->users&&!row->changing&&!row->writers&&!row->reading
                            &&!row->typedObjects&&!row->sealed&&!row->retiring&&!row->managed&&!row->hostStorageRelease
                            &&row->hostReleasePin&&!native_memory_buffer_aliases(env,row);
                    if(ready){
                        row->hostStorageRelease=row->release;row->hostStorageContext=row->hostContext;
                        row->hostStoragePin=row->hostReleasePin;row->hostReleasePin=pin;pin=NULL;
                        row->hostFamily=NATIVE_MEMORY_HOST_CPP;row->hostAllocator=allocator;
                        row->release=release;row->hostContext=context;row->hostContextEpoch=0;row->hostCppScope=NULL;
                        if(unknown||!owners||!native_owner_merge(&row->sources,owners))InterlockedOr(&row->unknownUse,1);
                        promoted=1;
                    }
                    LeaveCriticalSection(&row->gate);
                }
                break;
            }
        ReleaseSRWLockExclusive(&native_memory_records);
    }
    if(pin)host_real_free_library(pin);
    if(!promoted)native_memory_host_allocated(env,NATIVE_MEMORY_HOST_CPP,allocator,release,context,0,address,bytes,owners,unknown);
}
static NativeHostMemoryCall *native_memory_host_cpp_backing_prepare(JNIEnv *env,NativeHostCppOriginalScope *scope,void *release,
        uintptr_t context,uint64_t address,int operation,OwnerLink *owners,int unknown,int *denied){
    *denied=0;NativeHostMemoryCall *call=NULL,*discard=NULL;int cpp=0;
    AcquireSRWLockExclusive(&native_memory_records);
    NativeMemoryAllocation *row=native_memory_allocations;
    while(row&&(!row->alive||row->address!=address||row->hostFamily!=NATIVE_MEMORY_HOST_CPP))row=row->next;
    if(row){
        cpp=1;NativeHostMemoryCall *outer=scope?scope->call:NULL;
        int valid=native_memory_host_cpp_scope(scope)&&scope->operation==NATIVE_MEMORY_HOST_FREE
                &&operation==NATIVE_MEMORY_HOST_FREE&&outer&&outer->family==NATIVE_MEMORY_HOST_CPP
                &&outer->operation==NATIVE_MEMORY_HOST_FREE&&outer->release==scope->release&&outer->context==scope->context
                &&row->release==scope->release&&row->hostContext==scope->context
                &&row->hostStorageRelease==release&&row->hostStorageContext==context;
        int retained=0;if(valid)for(NativeHostMemoryHold *held=outer->holds;held;held=held->next)
            if(held->allocation==row&&held->generation==row->generation&&!held->observing){retained=1;break;}
        if(retained){
            call=(NativeHostMemoryCall*)calloc(1,sizeof(*call));
            if(call){call->family=NATIVE_MEMORY_HOST_CRT;call->operation=operation;call->release=release;call->bytes=row->bytes;
                call->backingLease=1;call->backingAllocation=row;call->backingProvider=outer->backingProvider;call->arenaOuter=outer;
                call->context=context;call->address=address;call->unknown=outer->unknown||unknown||!owners;
                call->admissionUnknown=outer->admissionUnknown||unknown||!owners;
                int ready=native_owner_merge(&call->sources,owners)&&native_owner_merge(&call->sources,outer->sources)
                        &&native_memory_host_custom_bulk_hold(env,call,row);
                if(!ready){native_memory_host_drop_locked(call,0);discard=call;call=NULL;}
            }
        }
        if(!call)*denied=1;
    }
    ReleaseSRWLockExclusive(&native_memory_records);
    if(discard)native_memory_host_dispose(discard);
    if(cpp)return call;
    return native_memory_host_prepare(env,NATIVE_MEMORY_HOST_CRT,release,context,address,0,operation,owners,unknown,denied);
}
static void native_memory_host_cpp_scope_end(NativeHostCppOriginalScope *scope){
    if(!scope)return;AcquireSRWLockExclusive(&native_memory_records);
    for(NativeMemoryAllocation *row=native_memory_allocations;row;row=row->next)if(row->hostCppScope==scope)row->hostCppScope=NULL;
    ReleaseSRWLockExclusive(&native_memory_records);
}
static int native_memory_arena_observe_hold(NativeHostMemoryCall *call,NativeMemoryAllocation *allocation){
    if(!TryEnterCriticalSection(&allocation->gate))return 0;
    int ready=allocation->alive&&!allocation->sealed&&!allocation->retiring&&!allocation->changing;
    NativeHostMemoryHold *hold=ready?(NativeHostMemoryHold*)calloc(1,sizeof(*hold)):NULL;
    if(hold){hold->allocation=allocation;hold->generation=allocation->generation;hold->observing=1;
        hold->next=call->holds;call->holds=hold;InterlockedIncrement(&allocation->users);}
    LeaveCriticalSection(&allocation->gate);return hold!=NULL;
}
static NativeHostMemoryCall *native_memory_host_allocator_prepare(JNIEnv *env,int family,void *release,uint64_t address,uintptr_t context,
        int operation,OwnerLink *owners,int unknown,int *denied,NativeHostMemoryCall *outer){
    *denied=0;NativeHostMemoryCall *call=(NativeHostMemoryCall*)calloc(1,sizeof(*call));
    if(!call){*denied=1;native_memory_unmapped(owners);return NULL;}
    call->family=family;call->operation=operation;call->release=release;call->address=address;
    call->arenaOuter=outer;
    call->context=context;call->unknown=call->admissionUnknown=unknown||!owners;
    if(!native_owner_merge(&call->sources,owners))call->unknown=call->admissionUnknown=1;
    int exclusive=operation==NATIVE_MEMORY_HOST_CONTEXT_FREE||operation==NATIVE_MEMORY_HOST_CONTEXT_CREATE
            ||operation==NATIVE_MEMORY_HOST_ARENA_RESET||operation==NATIVE_MEMORY_HOST_ARENA_UNKNOWN
            ||operation==NATIVE_MEMORY_HOST_CONTEXT_DETACH||operation==NATIVE_MEMORY_HOST_THREAD_DETACH;
    int bulk=operation==NATIVE_MEMORY_HOST_CONTEXT_FREE||operation==NATIVE_MEMORY_HOST_ARENA_RESET
            ||operation==NATIVE_MEMORY_HOST_ARENA_UNKNOWN;
    int ready=1;AcquireSRWLockExclusive(&native_memory_records);
    NativeHostArenaProvider *provider=native_memory_arena_provider(release,1);
    if(!provider||provider->changing||exclusive&&provider->users)ready=0;
    else {call->arenaProvider=provider;call->arenaExclusive=exclusive;
        if(exclusive)provider->changing=1;else provider->users++;
        if(provider->uncertain)call->unknown=1;
    }
    if(ready&&context){
        call->arena=native_memory_arena_context(provider,context,0);
        if(call->arena){call->contextEpoch=call->arena->epoch;
            if(!native_owner_merge(&call->sources,call->arena->sources))call->unknown=call->admissionUnknown=1;
            call->unknown|=call->arena->uncertain||call->arena->unknown;
            call->admissionUnknown|=call->arena->unknown;
        }
        if(family==NATIVE_MEMORY_HOST_OTHER&&operation==NATIVE_MEMORY_HOST_CONTEXT_FREE
                &&(!call->arena||!call->arena->created)){
            /* The public destroy API falls back to safe delete for a backing
             * heap. An unobserved heap origin cannot prove destructive mode. */
            operation=call->operation=NATIVE_MEMORY_HOST_ARENA_UNKNOWN;context=call->context=0;
        }
    }
    for(NativeMemoryAllocation *allocation=native_memory_allocations;ready&&allocation;allocation=allocation->next){
        if(!allocation->alive)continue;
        int own=allocation->hostFamily==family&&allocation->release==release;
        int match=bulk?own&&(!context||allocation->hostContext==context
                    ||family==NATIVE_MEMORY_HOST_ARENA&&!allocation->hostContext)
                :address&&allocation->address==address;
        if(!match)continue;
        int observing=operation==NATIVE_MEMORY_HOST_OBSERVE;
        if(!observing&&allocation->hostFamily==NATIVE_MEMORY_HOST_CUSTOM&&family!=NATIVE_MEMORY_HOST_CUSTOM){ready=0;break;}
        if(observing&&!own)continue;
        if(!observing&&(allocation->managed||allocation->hostFamily==NATIVE_MEMORY_HOST_IMAGE)){ready=0;break;}
        if(bulk&&own){if(!native_memory_host_custom_bulk_hold(env,call,allocation)){ready=0;break;}continue;}
        if(observing?!native_memory_arena_observe_hold(call,allocation):!native_memory_host_hold(env,call,allocation)){ready=0;break;}
        if(!own){
            /* Do not attach an unknown provider to this row. Close its old
             * views only after its real current users have denied admission. */
            InterlockedExchange(&allocation->unknownUse,1);InterlockedExchange(&allocation->releaseFailed,1);
            InterlockedExchange(&allocation->retiring,1);call->unknown=1;
            NativeHostMemoryHold *foreign=call->holds;call->holds=foreign->next;
            if(!foreign->observing)InterlockedDecrement(&allocation->changing);InterlockedDecrement(&allocation->users);free(foreign);
            break;
        }
        if(!bulk){call->context=allocation->hostContext;call->contextEpoch=allocation->hostContextEpoch;
            call->arena=native_memory_arena_context(provider,call->context,0);
            if(!(family==NATIVE_MEMORY_HOST_OTHER&&!call->context)
                    &&(!call->arena||call->arena->epoch!=call->contextEpoch||call->arena->uncertain))call->unknown=1;
        }
        if(!native_memory_creator_sources(allocation,&call->admissions)||!native_owner_merge(&call->admissions,allocation->readers))call->admissionUnknown=1;
        call->admissionUnknown|=allocation->unknownUse||allocation->unknownReaders||allocation->releaseFailed;
        if(!bulk)break;
    }
    if(ready&&address&&!call->holds)call->unknown=1;
    if(!ready){*denied=1;native_memory_host_drop_locked(call,0);}
    ReleaseSRWLockExclusive(&native_memory_records);
    if(!ready){native_memory_host_dispose(call);return NULL;}return call;
}
static NativeHostMemoryCall *native_memory_host_arena_prepare(JNIEnv *env,void *release,uint64_t address,uintptr_t context,
        int operation,OwnerLink *owners,int unknown,int *denied){
    return native_memory_host_allocator_prepare(env,NATIVE_MEMORY_HOST_ARENA,release,address,context,operation,owners,unknown,denied,NULL);
}
static NativeHostMemoryCall *native_memory_host_other_prepare(JNIEnv *env,void *release,uint64_t address,uintptr_t context,
        int operation,OwnerLink *owners,int unknown,int *denied){
    return native_memory_host_allocator_prepare(env,NATIVE_MEMORY_HOST_OTHER,release,address,context,operation,owners,unknown,denied,NULL);
}
static uintptr_t native_memory_host_other_context(NativeHostMemoryCall *call){return call?call->context:0;}
static void native_memory_host_arena_complete(JNIEnv *env,NativeHostMemoryCall *call,uint64_t address,uint64_t bytes,
        uintptr_t context,int completed,int released){
    if(!call)return;NativeHostArenaProvider *provider=call->arenaProvider;
    int unknown=call->unknown;OwnerLink *sources=NULL;
    if(!native_owner_merge(&sources,call->sources))unknown=1;
    if(call->operation==NATIVE_MEMORY_HOST_ARENA_UNKNOWN){
        /* No successful/failed opaque command is evidence that a particular
         * child was freed. Close the provider views and retain that gap. */
        AcquireSRWLockExclusive(&native_memory_records);native_memory_arena_uncertain_locked(provider,0);
        ReleaseSRWLockExclusive(&native_memory_records);
    }else if(completed){
        for(NativeHostMemoryHold *hold=call->holds;hold;hold=hold->next){
            NativeMemoryAllocation *allocation=hold->allocation;EnterCriticalSection(&allocation->gate);
            /* A nested real backing release may already have retired the old
             * row. Its retained outer lease still carries realloc provenance. */
            if(call->operation==NATIVE_MEMORY_HOST_REALLOCATE&&address&&!native_owner_merge(&sources,allocation->sources))unknown=1;
            if(!hold->observing&&allocation->alive&&allocation->generation==hold->generation){
                int bulk=call->operation==NATIVE_MEMORY_HOST_CONTEXT_FREE||call->operation==NATIVE_MEMORY_HOST_ARENA_RESET;
                int exact=!bulk||hold->dependent||allocation->hostContext==call->context&&allocation->hostContextEpoch==call->contextEpoch;
                AcquireSRWLockExclusive(&native_memory_records);
                if(exact&&(released||call->operation==NATIVE_MEMORY_HOST_REALLOCATE&&address))native_memory_released(env,allocation);
                else if(bulk){InterlockedExchange(&allocation->unknownUse,1);InterlockedExchange(&allocation->releaseFailed,1);
                    InterlockedExchange(&allocation->sealed,1);InterlockedExchange(&allocation->retiring,1);}
                ReleaseSRWLockExclusive(&native_memory_records);
            }
            LeaveCriticalSection(&allocation->gate);
        }
        if(call->operation==NATIVE_MEMORY_HOST_CONTEXT_FREE||call->operation==NATIVE_MEMORY_HOST_ARENA_RESET){
            AcquireSRWLockExclusive(&native_memory_records);
            if(call->arena&&call->arena->epoch==call->contextEpoch)call->arena->retired=1;
            if(call->operation==NATIVE_MEMORY_HOST_ARENA_RESET&&call->context){
                NativeHostArenaContext *next=native_memory_arena_context(provider,call->context,1);
                if(next&&call->arena){next->created=call->arena->created;next->unknown=call->arena->unknown;
                    if(!native_owner_merge(&next->sources,call->arena->sources))next->unknown=1;}
                if(!next)native_memory_arena_uncertain_locked(provider,call->context);
            }
            ReleaseSRWLockExclusive(&native_memory_records);
        }else if(call->operation==NATIVE_MEMORY_HOST_CONTEXT_CREATE){
            AcquireSRWLockExclusive(&native_memory_records);
            NativeHostArenaContext *old=native_memory_arena_context(provider,context,0);
            if(old){native_memory_arena_uncertain_locked(provider,context);old->retired=1;}
            NativeHostArenaContext *next=native_memory_arena_context(provider,context,1);
            if(next){next->created=1;next->unknown=unknown;if(!native_owner_merge(&next->sources,sources))next->unknown=1;}
            else native_memory_arena_uncertain_locked(provider,0);
            ReleaseSRWLockExclusive(&native_memory_records);
        }else if(address&&(call->operation==NATIVE_MEMORY_HOST_ALLOCATE||call->operation==NATIVE_MEMORY_HOST_REALLOCATE)){
            uint64_t epoch=0;AcquireSRWLockExclusive(&native_memory_records);
            NativeHostArenaContext *arena=native_memory_arena_context(provider,context,1);
            if(arena){epoch=arena->epoch;if(!native_owner_merge(&sources,arena->sources))unknown=1;unknown|=arena->unknown||arena->uncertain;}
            else if(call->family!=NATIVE_MEMORY_HOST_OTHER||context)unknown=1;
            ReleaseSRWLockExclusive(&native_memory_records);
            native_memory_host_record(env,call->family,NULL,call->release,context,0,address,bytes,sources,unknown,epoch,NULL,NULL);
        }
    }
    AcquireSRWLockExclusive(&native_memory_records);native_memory_host_drop_locked(call,0);ReleaseSRWLockExclusive(&native_memory_records);
    for(NativeHostMemoryHold *hold=call->holds;hold;hold=hold->next)if(!hold->allocation->alive)native_memory_reap_references(env,hold->allocation);
    native_owner_links_free(sources);native_memory_host_dispose(call);
}
static void native_memory_host_other_complete(JNIEnv *env,NativeHostMemoryCall *call,uint64_t address,uint64_t bytes,
        uintptr_t context,int completed,int released){
    if(!call)return;
    if(context==UINTPTR_MAX){context=0;call->unknown=1;}
    if(call->operation!=NATIVE_MEMORY_HOST_CONTEXT_DETACH&&call->operation!=NATIVE_MEMORY_HOST_THREAD_DETACH
            &&call->operation!=NATIVE_MEMORY_HOST_CONTEXT_OBSERVE){
        native_memory_host_arena_complete(env,call,address,bytes,context,completed,released);return;
    }
    AcquireSRWLockExclusive(&native_memory_records);
    NativeHostArenaProvider *provider=call->arenaProvider;
    if(completed&&call->operation==NATIVE_MEMORY_HOST_CONTEXT_OBSERVE&&context){
        NativeHostArenaContext *actual=native_memory_arena_context(provider,context,1);
        if(!actual)native_memory_arena_uncertain_locked(provider,0);
        /* A public accessor proves a handle, not private-heap creation or a
         * destructive lifetime. Keep any existing creation epoch unchanged. */
    }else if(completed&&call->operation==NATIVE_MEMORY_HOST_CONTEXT_DETACH){
        NativeHostArenaContext *destination=context?native_memory_arena_context(provider,context,1):NULL;
        uint64_t epoch=destination?destination->epoch:0;
        if(context&&!destination)native_memory_arena_uncertain_locked(provider,call->context);
        if(call->arena&&call->arena->epoch==call->contextEpoch){
            for(NativeMemoryAllocation *row=native_memory_allocations;row;row=row->next)
                if(row->alive&&row->hostFamily==NATIVE_MEMORY_HOST_OTHER&&row->release==call->release
                        &&row->hostContext==call->context&&row->hostContextEpoch==call->contextEpoch){
                    row->hostContext=context;row->hostContextEpoch=epoch;
                    if(call->unknown||context&&!destination)InterlockedOr(&row->unknownUse,1);
                }
            call->arena->retired=1;
        }
    }else if(completed&&call->operation==NATIVE_MEMORY_HOST_THREAD_DETACH){
        DWORD thread=GetCurrentThreadId();
        for(NativeHostArenaContext *heap=native_memory_arena_contexts;heap;heap=heap->next)
            if(heap->provider==provider&&!heap->retired&&heap->thread==thread){
                for(NativeMemoryAllocation *row=native_memory_allocations;row;row=row->next)
                    if(row->alive&&row->hostFamily==NATIVE_MEMORY_HOST_OTHER&&row->release==call->release
                            &&row->hostContext==heap->context&&row->hostContextEpoch==heap->epoch){
                        row->hostContext=0;row->hostContextEpoch=0;
                    }
                heap->retired=1;
            }
    }
    native_memory_host_drop_locked(call,0);ReleaseSRWLockExclusive(&native_memory_records);
    native_memory_host_dispose(call);
}
/* A custom child is linked to an already observed parent incarnation. Numeric
 * containment alone cannot attach it to somebody else's allocation. */
static int native_memory_custom_ancestor(NativeMemoryAllocation *child,NativeMemoryAllocation *ancestor,uint64_t generation){
    for(NativeMemoryAllocation *row=child;row;){
        if(row==ancestor&&row->generation==generation)return row->alive!=0;
        NativeMemoryAllocation *parent=row->hostCustomParent;
        if(!parent||!parent->alive||parent->generation!=row->hostCustomParentGeneration)break;
        row=parent;
    }return 0;
}
static int native_memory_custom_member(NativeMemoryAllocation *row,void *provider,uintptr_t context){
    for(NativeMemoryAllocation *entry=row;entry;){
        if(entry->hostFamily==NATIVE_MEMORY_HOST_CUSTOM&&entry->release==provider&&entry->hostContext==context)return 1;
        NativeMemoryAllocation *parent=entry->hostCustomParent;
        if(!parent||!parent->alive||parent->generation!=entry->hostCustomParentGeneration)break;entry=parent;
    }return 0;
}
static int native_memory_custom_anchor_add(NativeHostMemoryCall *call,NativeMemoryAllocation *allocation,uint64_t generation){
    if(!allocation||!generation)return 0;
    for(NativeHostCustomAnchor *old=call->customAnchors;old;old=old->next)
        if(old->allocation==allocation&&old->generation==generation)return 1;
    NativeHostCustomAnchor *anchor=(NativeHostCustomAnchor*)calloc(1,sizeof(*anchor));if(!anchor)return 0;
    anchor->allocation=allocation;anchor->generation=generation;anchor->next=call->customAnchors;call->customAnchors=anchor;return 1;
}
static NativeHostMemoryCall *native_memory_host_custom_prepare(JNIEnv *env,void *provider,uintptr_t context,uint64_t contextEpoch,
        NativeMemoryAllocation *target,uint64_t generation,NativeMemoryAllocation *parent,uint64_t parentGeneration,
        int operation,OwnerLink *sources,int *denied){
    NativeHostMemoryCall *call=native_memory_host_allocator_prepare(env,NATIVE_MEMORY_HOST_CUSTOM,provider,0,context,
            NATIVE_MEMORY_HOST_ALLOCATE,sources,0,denied,NULL);
    if(!call)return NULL;int ready=1;AcquireSRWLockExclusive(&native_memory_records);
    ready=call->arena&&call->arena->epoch==contextEpoch&&!call->arena->uncertain&&!call->arena->unknown
            &&!call->arenaProvider->uncertain&&!call->unknown&&!call->admissionUnknown;
    int bulk=operation==NATIVE_MEMORY_HOST_ARENA_RESET||operation==NATIVE_MEMORY_HOST_CONTEXT_FREE;
    if(ready&&bulk){
        if(call->arenaProvider->users!=1)ready=0;
        else {call->arenaProvider->users--;call->arenaProvider->changing=1;call->arenaExclusive=1;}
    }
    call->operation=operation;
    if(ready&&target)ready=target->alive&&target->generation==generation&&target->hostFamily==NATIVE_MEMORY_HOST_CUSTOM
            &&target->release==provider&&target->hostContext==context&&target->hostContextEpoch==contextEpoch;
    if(ready&&parent){
        ready=parent->alive&&parent->generation==parentGeneration&&parent->hostFamily==NATIVE_MEMORY_HOST_CUSTOM
                &&parent->release==provider&&!parent->unknownUse&&!parent->unknownReaders&&!parent->releaseFailed
                &&native_memory_same_sources(parent->sources,sources)&&native_memory_arena_observe_hold(call,parent)
                &&native_memory_custom_anchor_add(call,parent,parentGeneration);
    }
    for(NativeMemoryAllocation *row=native_memory_allocations;ready&&row;row=row->next){
        if(!row->alive||row->hostFamily!=NATIVE_MEMORY_HOST_CUSTOM||row->release!=provider)continue;
        int affected=target?native_memory_custom_ancestor(row,target,generation):bulk&&native_memory_custom_member(row,provider,context);
        if(!affected)continue;
        if(row->unknownUse||row->unknownReaders||row->releaseFailed||!native_memory_host_hold(env,call,row)){ready=0;break;}
        if(!native_memory_creator_sources(row,&call->admissions)||!native_owner_merge(&call->admissions,row->readers))ready=0;
        if(row->hostCustomParent&&!native_memory_custom_anchor_add(call,row->hostCustomParent,row->hostCustomParentGeneration))ready=0;
    }
    if(ready&&(!native_owner_merge(&call->admissions,call->sources)||native_owner_stopped(call->admissions)))ready=0;
    if(!ready){*denied=1;native_memory_host_drop_locked(call,0);}
    ReleaseSRWLockExclusive(&native_memory_records);
    if(!ready){native_memory_host_dispose(call);return NULL;}return call;
}
static NativeMemoryAllocation *native_memory_host_custom_publish(JNIEnv *env,NativeHostMemoryCall *call,
        NativeMemoryAllocation *parent,uint64_t parentGeneration,uint64_t address,uint64_t bytes,uint64_t generation){
    (void)env;if(!call||!parent||!address||bytes>SIZE_MAX||bytes>UINT64_MAX-address||!generation)return NULL;
    NativeMemoryAllocation *entry=(NativeMemoryAllocation*)calloc(1,sizeof(*entry));if(!entry)return NULL;
    InitializeCriticalSection(&entry->gate);entry->hostFamily=NATIVE_MEMORY_HOST_CUSTOM;entry->release=call->release;
    entry->hostContext=call->context;entry->hostContextEpoch=call->contextEpoch;entry->address=address;entry->bytes=bytes;
    entry->generation=generation;entry->alive=entry->opaque=1;entry->hostCustomParent=parent;entry->hostCustomParentGeneration=parentGeneration;
    int ready=native_owner_merge(&entry->sources,call->sources)&&entry->sources
            &&GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,(LPCSTR)call->release,&entry->hostReleasePin);
    AcquireSRWLockExclusive(&native_memory_records);
    ready=ready&&parent->alive&&parent->generation==parentGeneration&&!parent->unknownReaders&&!parent->releaseFailed
            &&!(parent->unknownUse&~2)&&native_memory_same_sources(parent->sources,call->sources)
            &&address>=parent->address&&bytes<=parent->bytes&&address-parent->address<=parent->bytes-bytes;
    for(NativeMemoryAllocation *old=native_memory_allocations;ready&&old;old=old->next)
        if(old->alive&&old->bytes&&address<old->address+old->bytes&&old->address<address+bytes
                &&!native_memory_custom_ancestor(parent,old,old->generation))ready=0;
    if(ready){
        if(parent->hostFamily!=NATIVE_MEMORY_HOST_CUSTOM){InterlockedOr(&parent->unknownUse,2);InterlockedExchange(&parent->retiring,1);}
        entry->next=native_memory_allocations;native_memory_allocations=entry;
    }
    ReleaseSRWLockExclusive(&native_memory_records);
    if(!ready){if(entry->hostReleasePin)host_real_free_library(entry->hostReleasePin);native_owner_links_free(entry->sources);
        DeleteCriticalSection(&entry->gate);free(entry);return NULL;}return entry;
}
static int native_memory_host_backing_scope(NativeHostArenaOriginalScope *scope){
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    NativeHostMemoryCall *outer=scope?scope->call:NULL;
    return state&&state->hostArenaOriginal==scope&&scope->thread==GetCurrentThreadId()&&outer
            &&(outer->family==NATIVE_MEMORY_HOST_ARENA||outer->family==NATIVE_MEMORY_HOST_OTHER
                    ||outer->family==NATIVE_MEMORY_HOST_CUSTOM)&&outer->arenaProvider
            &&outer->arenaProvider->release==scope->release
            &&(outer->arenaExclusive?outer->arenaProvider->changing:outer->arenaProvider->users>0);
}
static void native_memory_host_backing_allocated(JNIEnv *env,NativeHostArenaOriginalScope *scope,void *allocator,void *release,
        uint64_t address,uint64_t bytes,OwnerLink *owners,int unknown){
    AcquireSRWLockShared(&native_memory_records);int paired=native_memory_host_backing_scope(scope);
    ReleaseSRWLockShared(&native_memory_records);
    native_memory_host_record(env,NATIVE_MEMORY_HOST_VIRTUAL,allocator,release,0,0,address,bytes,owners,unknown,0,NULL,paired?scope->release:NULL);
}
/* Only a real provider call can reuse the destructive generation that it
 * already holds. Every other affected allocation obtains the ordinary hold.
 * A backing-only retiring flag is not a general unknown-state exception. */
static int native_memory_host_backing_hold(JNIEnv *env,NativeHostMemoryCall *call,NativeMemoryAllocation *allocation,int parent){
    for(NativeHostMemoryHold *held=call->holds;held;held=held->next)
        if(held->allocation==allocation&&held->generation==allocation->generation)return !held->observing;
    NativeHostMemoryHold *outer=NULL;NativeHostMemoryCall *owner=NULL;
    for(NativeHostMemoryCall *entry=call->arenaOuter;entry;entry=entry->arenaOuter)
        for(NativeHostMemoryHold *held=entry->holds;held;held=held->next)
            if(held->allocation==allocation&&held->generation==allocation->generation&&!held->observing&&!held->borrowed){outer=held;owner=entry;break;}
    if(!outer&&!parent)return native_memory_host_hold(env,call,allocation);
    if(!TryEnterCriticalSection(&allocation->gate))return 0;
    int ownRetirement=outer&&owner->retirement;
    int backing=(parent||outer&&outer->backing)&&(allocation->unknownUse&2)&&allocation->hostBackingProvider;
    int ready=allocation->alive&&!allocation->writers&&!allocation->reading&&!allocation->typedObjects&&!allocation->managed
            &&!native_memory_buffer_aliases(env,allocation);
    if(outer)ready=ready&&allocation->changing==1&&allocation->users==(ownRetirement?0:1)
            &&(!allocation->sealed||ownRetirement)&&(!allocation->retiring||ownRetirement||backing);
    else ready=ready&&!allocation->sealed&&!allocation->changing&&!allocation->users
            &&(!allocation->retiring||backing&&(!call->backingProvider||allocation->hostBackingProvider==call->backingProvider));
    NativeHostMemoryHold *hold=ready?(NativeHostMemoryHold*)calloc(1,sizeof(*hold)):NULL;
    if(hold){hold->allocation=allocation;hold->generation=allocation->generation;hold->borrowed=outer!=NULL;
        hold->backing=backing;
        hold->next=call->holds;call->holds=hold;
        if(!hold->borrowed){InterlockedIncrement(&allocation->users);InterlockedIncrement(&allocation->changing);}}
    LeaveCriticalSection(&allocation->gate);return hold!=NULL;
}
/* The actual owning provider/context is leased before this helper. Only the
 * recorded parent generation and its exact cooperative descendants join the
 * bulk operation; containment of arbitrary storage grants no authority. */
static int native_memory_host_custom_bulk_hold(JNIEnv *env,NativeHostMemoryCall *call,NativeMemoryAllocation *parent){
    if(!native_memory_host_backing_hold(env,call,parent,1))return 0;
    for(NativeMemoryAllocation *row=native_memory_allocations;row;row=row->next){
        if(!row->alive||row!=parent&&(row->hostFamily!=NATIVE_MEMORY_HOST_CUSTOM
                ||!native_memory_custom_ancestor(row,parent,parent->generation)))continue;
        if(row!=parent&&!native_memory_host_backing_hold(env,call,row,0))return 0;
        NativeHostMemoryHold *hold=call->holds;
        while(hold&&(hold->allocation!=row||hold->generation!=row->generation))hold=hold->next;
        if(!hold)return 0;if(row!=parent)hold->dependent=1;
        if(!native_memory_creator_sources(row,&call->admissions)||!native_owner_merge(&call->admissions,row->readers))call->admissionUnknown=1;
        LONG unknownUse=row->unknownUse;if(hold->backing)unknownUse&=~2;
        call->admissionUnknown|=unknownUse!=0||row->unknownReaders||row->releaseFailed;
    }return 1;
}
static int native_memory_custom_backing_family(int family){
    return family==NATIVE_MEMORY_HOST_HEAP||family==NATIVE_MEMORY_HOST_VIRTUAL||family==NATIVE_MEMORY_HOST_CRT
            ||family==NATIVE_MEMORY_HOST_CPP||family==NATIVE_MEMORY_HOST_ARENA||family==NATIVE_MEMORY_HOST_OTHER;
}
static void native_memory_host_custom_backing_allocated(NativeHostArenaOriginalScope *scope,int family,void *release,
        uintptr_t context,uint64_t address){
    if(!address||!native_memory_custom_backing_family(family))return;
    AcquireSRWLockExclusive(&native_memory_records);
    if(native_memory_host_backing_scope(scope)&&scope->call->family==NATIVE_MEMORY_HOST_CUSTOM){
        for(NativeMemoryAllocation *row=native_memory_allocations;row;row=row->next)
            if(row->alive&&row->hostFamily==family&&row->release==release&&row->hostContext==context&&row->address==address){
                if(!row->unknownUse&&!row->unknownReaders&&!row->releaseFailed&&!row->managed
                        &&native_memory_same_sources(row->sources,scope->call->sources)
                        &&(!row->hostBackingProvider||row->hostBackingProvider==scope->release)){
                    row->hostBackingProvider=scope->release;row->hostCustomAllocation=scope->call;
                }break;
            }
    }
    ReleaseSRWLockExclusive(&native_memory_records);
}
static NativeHostMemoryCall *native_memory_host_custom_backing_prepare(JNIEnv *env,NativeHostArenaOriginalScope *scope,int family,
        void *release,uintptr_t context,uint64_t address,int operation,OwnerLink *owners,int unknown,int *denied){
    *denied=0;if(!scope||!native_memory_custom_backing_family(family))return NULL;
    NativeHostMemoryCall *call=NULL;AcquireSRWLockExclusive(&native_memory_records);
    if(!native_memory_host_backing_scope(scope)||scope->call->family!=NATIVE_MEMORY_HOST_CUSTOM){ReleaseSRWLockExclusive(&native_memory_records);return NULL;}
    NativeHostMemoryCall *outer=scope->call;NativeMemoryAllocation *parent=NULL;
    int bulk=operation==NATIVE_MEMORY_HOST_CONTEXT_FREE||operation==NATIVE_MEMORY_HOST_ARENA_RESET||operation==NATIVE_MEMORY_HOST_ARENA_UNKNOWN;
    if(bulk&&!address){
        int paired=native_memory_same_sources(outer->sources,owners)&&!unknown;
        ReleaseSRWLockExclusive(&native_memory_records);
        if(!paired){*denied=1;return NULL;}
        if(family==NATIVE_MEMORY_HOST_HEAP&&operation==NATIVE_MEMORY_HOST_CONTEXT_FREE)
            return native_memory_host_prepare_outer(env,family,release,context,0,0,operation,owners,unknown,denied,outer);
        if(family==NATIVE_MEMORY_HOST_ARENA||family==NATIVE_MEMORY_HOST_OTHER)
            return native_memory_host_allocator_prepare(env,family,release,0,context,operation,owners,unknown,denied,outer);
        return NULL;
    }
    for(NativeMemoryAllocation *row=native_memory_allocations;row;row=row->next)
        if(row->alive&&row->hostFamily==family&&row->release==release
                &&(family==NATIVE_MEMORY_HOST_ARENA||family==NATIVE_MEMORY_HOST_OTHER||row->hostContext==context)&&row->hostBackingProvider==scope->release
                &&address>=row->address&&(row->bytes?address-row->address<row->bytes:address==row->address)){parent=row;break;}
    if(!parent){ReleaseSRWLockExclusive(&native_memory_records);return NULL;}
    int paired=parent->hostCustomAllocation==outer;
    for(NativeHostCustomAnchor *anchor=outer->customAnchors;!paired&&anchor;anchor=anchor->next)
        if(anchor->allocation->alive&&anchor->allocation->generation==anchor->generation
                &&native_memory_custom_ancestor(anchor->allocation,parent,parent->generation))paired=1;
    for(NativeHostMemoryHold *held=outer->holds;!paired&&held;held=held->next)
        if(held->allocation->generation==held->generation&&native_memory_custom_ancestor(held->allocation,parent,parent->generation))paired=1;
    int ready=paired&&operation==NATIVE_MEMORY_HOST_FREE&&address==parent->address;
    if(ready)call=(NativeHostMemoryCall*)calloc(1,sizeof(*call));
    if(!call)ready=0;
    if(ready){
        call->family=family;call->operation=operation;call->release=release;call->context=parent->hostContext;call->address=address;call->bytes=parent->bytes;
        call->backingLease=1;call->backingProvider=scope->release;call->arenaOuter=outer;call->backingAllocation=parent;
        call->unknown=call->admissionUnknown=unknown||!owners;
        ready=native_owner_merge(&call->sources,owners)&&native_memory_same_sources(outer->sources,owners);
        if(ready&&family==NATIVE_MEMORY_HOST_HEAP){
            NativeHostMemoryContext *heap=native_memory_host_context(release,call->context,0);
            ready=heap&&heap->observed&&!heap->retired&&!heap->changing&&!heap->uncertain&&heap->epoch==parent->hostContextEpoch;
            if(ready){call->heap=heap;call->contextEpoch=heap->epoch;heap->users++;}
        }
        if(ready&&(family==NATIVE_MEMORY_HOST_ARENA||family==NATIVE_MEMORY_HOST_OTHER)){
            NativeHostArenaProvider *provider=native_memory_arena_provider(release,0);
            NativeHostArenaContext *arena=native_memory_arena_context(provider,call->context,0);
            ready=provider&&!provider->changing&&!provider->uncertain
                    &&(family==NATIVE_MEMORY_HOST_OTHER&&!call->context||arena&&arena->epoch==parent->hostContextEpoch&&!arena->uncertain&&!arena->unknown);
            if(ready){call->arenaProvider=provider;provider->users++;call->arena=arena;call->contextEpoch=parent->hostContextEpoch;}
        }
        for(NativeMemoryAllocation *row=native_memory_allocations;ready&&row;row=row->next){
            if(!row->alive||row!=parent&&(row->hostFamily!=NATIVE_MEMORY_HOST_CUSTOM
                    ||!native_memory_custom_ancestor(row,parent,parent->generation)))continue;
            if(row->hostFamily==NATIVE_MEMORY_HOST_IMAGE||!native_memory_host_backing_hold(env,call,row,row==parent)){ready=0;break;}
            if(!native_memory_creator_sources(row,&call->admissions)||!native_owner_merge(&call->admissions,row->readers))ready=0;
            LONG unknownUse=row->unknownUse;if(row==parent)unknownUse&=~2;
            call->admissionUnknown|=unknownUse!=0||row->unknownReaders||row->releaseFailed;
            if(row!=parent&&row->release!=scope->release)ready=0;
        }
    }
    if(!ready){*denied=1;
        /* A refused underlying realloc did not run and cannot invalidate the
         * old receipt when its caller reports the actual FAILED result. */
        if(operation!=NATIVE_MEMORY_HOST_REALLOCATE)outer->customFailure=1;
        if(call)native_memory_host_drop_locked(call,0);}
    ReleaseSRWLockExclusive(&native_memory_records);
    if(!ready){if(call)native_memory_host_dispose(call);return NULL;}return call;
}
static NativeHostMemoryCall *native_memory_host_backing_prepare(JNIEnv *env,NativeHostArenaOriginalScope *scope,void *release,
        uint64_t address,uint64_t bytes,int operation,OwnerLink *owners,int unknown,int *denied){
    if(scope&&scope->call&&scope->call->family==NATIVE_MEMORY_HOST_CUSTOM)
        return native_memory_host_custom_backing_prepare(env,scope,NATIVE_MEMORY_HOST_VIRTUAL,release,0,address,operation,owners,unknown,denied);
    *denied=0;NativeHostMemoryCall *call=(NativeHostMemoryCall*)calloc(1,sizeof(*call));
    if(!call){*denied=1;return NULL;}
    call->family=NATIVE_MEMORY_HOST_VIRTUAL;call->operation=operation;call->release=release;
    call->unknown=call->admissionUnknown=unknown||!owners;
    if(!native_owner_merge(&call->sources,owners))call->unknown=call->admissionUnknown=1;
    int ready=1;AcquireSRWLockExclusive(&native_memory_records);
    if(!native_memory_host_backing_scope(scope))ready=0;
    NativeMemoryAllocation *parent=NULL;
    for(NativeMemoryAllocation *row=native_memory_allocations;ready&&row;row=row->next)
        if(row->alive&&row->hostFamily==NATIVE_MEMORY_HOST_VIRTUAL&&row->release==release&&row->hostBackingProvider==scope->release
                &&row->bytes&&(operation==NATIVE_MEMORY_HOST_FREE?row->address==address:
                    address>=row->address&&bytes&&bytes<=row->bytes&&address-row->address<=row->bytes-bytes)){parent=row;break;}
    if(!parent){ReleaseSRWLockExclusive(&native_memory_records);native_memory_host_dispose(call);return NULL;}
    call->backingLease=1;call->backingProvider=scope->release;call->arenaOuter=scope->call;call->backingAllocation=parent;
    call->address=operation==NATIVE_MEMORY_HOST_FREE?parent->address:address;call->bytes=operation==NATIVE_MEMORY_HOST_FREE?parent->bytes:bytes;
    uint64_t end=call->address+call->bytes;
    for(NativeMemoryAllocation *row=native_memory_allocations;ready&&row;row=row->next){
        if(!row->alive)continue;
        int affected=row==parent||(row->bytes?row->address<end&&call->address<row->address+row->bytes:
                row->address>=call->address&&row->address<end);
        if(!affected)continue;
        if(row->hostFamily==NATIVE_MEMORY_HOST_IMAGE||!native_memory_host_backing_hold(env,call,row,row==parent)){ready=0;break;}
        if(!native_memory_creator_sources(row,&call->admissions)||!native_owner_merge(&call->admissions,row->readers))call->admissionUnknown=1;
        LONG unknownUse=row->unknownUse;
        if(call->holds->backing)unknownUse&=~2;
        call->admissionUnknown|=unknownUse!=0||row->unknownReaders||row->releaseFailed;
        if(row!=parent&&(row->hostFamily==NATIVE_MEMORY_HOST_ARENA||row->hostFamily==NATIVE_MEMORY_HOST_OTHER)
                &&row->release!=scope->release)call->admissionUnknown=1;
    }
    if(!ready){*denied=1;native_memory_host_drop_locked(call,0);}
    ReleaseSRWLockExclusive(&native_memory_records);
    if(!ready){native_memory_host_dispose(call);return NULL;}return call;
}
static void native_memory_host_backing_complete(JNIEnv *env,NativeHostMemoryCall *call,uint64_t bytes,int completed,int released){
    if(!call)return;if(!call->backingLease){native_memory_host_complete(env,call,0,bytes,completed,released);return;}
    if(!completed)for(NativeHostMemoryCall *outer=call->arenaOuter;outer;outer=outer->arenaOuter)
        if(outer->family==NATIVE_MEMORY_HOST_CUSTOM)outer->customFailure=1;
    if(completed)for(NativeHostMemoryHold *hold=call->holds;hold;hold=hold->next){
        NativeMemoryAllocation *allocation=hold->allocation;EnterCriticalSection(&allocation->gate);
        if(allocation->alive&&allocation->generation==hold->generation){
            AcquireSRWLockExclusive(&native_memory_records);
            if(released&&(allocation==call->backingAllocation||allocation->address>=call->address&&allocation->bytes<=call->bytes
                    &&allocation->address-call->address<=call->bytes-allocation->bytes))native_memory_released(env,allocation);
            else if(allocation==call->backingAllocation){
                allocation->generation++;allocation->writeEpoch++;InterlockedExchange(&allocation->opaque,1);
                NativeMemoryRange *range=allocation->ranges;allocation->ranges=NULL;
                while(range){NativeMemoryRange *next=range->next;native_memory_revision_release(range->revision);free(range);range=next;}
                if(env)native_memory_buffer_aliases_clear(env,allocation);
            }else if(!hold->borrowed){
                /* A partial decommit proves invalid views, not a logical
                 * allocator free. The outer held generation gets its own
                 * actual success/failure completion and is left untouched. */
                InterlockedExchange(&allocation->retiring,1);InterlockedExchange(&allocation->unknownUse,1);
                InterlockedExchange(&allocation->releaseFailed,1);
            }
            ReleaseSRWLockExclusive(&native_memory_records);
        }
        LeaveCriticalSection(&allocation->gate);
    }
    AcquireSRWLockExclusive(&native_memory_records);native_memory_host_drop_locked(call,0);ReleaseSRWLockExclusive(&native_memory_records);
    for(NativeHostMemoryHold *hold=call->holds;hold;hold=hold->next)if(!hold->allocation->alive)native_memory_reap_references(env,hold->allocation);
    native_memory_host_dispose(call);
}
static void native_memory_image_span(JNIEnv *env,void *imageTag,uint64_t address,uint64_t bytes,OwnerLink *owners,int unknown){
    if(!imageTag||!address||!bytes)return;
    native_memory_host_record(env,NATIVE_MEMORY_HOST_IMAGE,NULL,NULL,0,0,address,bytes,owners,unknown,0,imageTag,NULL);
}
static int native_memory_image_busy(JNIEnv *env,void *imageTag){
    if(!imageTag)return 1;int busy=0;AcquireSRWLockShared(&native_memory_records);
    for(NativeMemoryAllocation *allocation=native_memory_allocations;allocation;allocation=allocation->next)if(allocation->alive&&allocation->imageTag==imageTag){
        if(!TryEnterCriticalSection(&allocation->gate)){busy=1;break;}
        busy=allocation->users||allocation->writers||allocation->reading||allocation->changing||native_memory_buffer_aliases(env,allocation);
        LeaveCriticalSection(&allocation->gate);if(busy)break;
    }
    ReleaseSRWLockShared(&native_memory_records);return busy;
}
static int native_memory_image_prepare(JNIEnv *env,void *imageTag){
    int ready=imageTag!=NULL;NativeHostMemoryHold *sealed=NULL;AcquireSRWLockExclusive(&native_memory_records);
    NativeMemoryImageState *image=ready?native_memory_image_state(imageTag,1):NULL;
    if(!image||image->retired)ready=0;
    for(NativeMemoryAllocation *allocation=native_memory_allocations;ready&&allocation;allocation=allocation->next)if(allocation->alive&&allocation->imageTag==imageTag){
        if(!TryEnterCriticalSection(&allocation->gate)){ready=0;break;}
        ready=!allocation->users&&!allocation->writers&&!allocation->reading&&!allocation->changing&&!native_memory_buffer_aliases(env,allocation);
        if(ready&&!allocation->retiring){
            NativeHostMemoryHold *hold=(NativeHostMemoryHold*)calloc(1,sizeof(*hold));
            if(!hold)ready=0;else{hold->allocation=allocation;hold->next=sealed;sealed=hold;InterlockedExchange(&allocation->retiring,1);}
        }
        LeaveCriticalSection(&allocation->gate);
    }
    if(ready)image->retiring=1;
    while(sealed){NativeHostMemoryHold *hold=sealed;sealed=hold->next;if(!ready)InterlockedExchange(&hold->allocation->retiring,0);free(hold);}
    ReleaseSRWLockExclusive(&native_memory_records);return ready;
}
static void native_memory_image_cancel(JNIEnv *env,void *imageTag){
    (void)env;if(!imageTag)return;AcquireSRWLockExclusive(&native_memory_records);NativeMemoryImageState *image=native_memory_image_state(imageTag,0);
    if(image&&!image->retired){image->retiring=0;
        for(NativeMemoryAllocation *allocation=native_memory_allocations;allocation;allocation=allocation->next)
            if(allocation->alive&&allocation->imageTag==imageTag&&!allocation->sealed)InterlockedExchange(&allocation->retiring,0);
    }
    ReleaseSRWLockExclusive(&native_memory_records);
}
static void native_memory_image_retire(JNIEnv *env,void *imageTag){
    if(!imageTag)return;AcquireSRWLockExclusive(&native_memory_records);NativeMemoryImageState *image=native_memory_image_state(imageTag,1);
    if(image)image->retired=image->retiring=1;NativeMemoryAllocation *head=native_memory_allocations;ReleaseSRWLockExclusive(&native_memory_records);
    for(NativeMemoryAllocation *allocation=head;allocation;allocation=allocation->next)if(allocation->imageTag==imageTag){
        if(TryEnterCriticalSection(&allocation->gate)){
            AcquireSRWLockExclusive(&native_memory_records);if(allocation->alive)native_memory_released(env,allocation);ReleaseSRWLockExclusive(&native_memory_records);
            LeaveCriticalSection(&allocation->gate);
        }else{
            // The real image is already gone. Invalidate its pair immediately;
            // a remaining old receipt can release its counts and weak refs later.
            AcquireSRWLockExclusive(&native_memory_records);
            if(allocation->alive){InterlockedExchange(&allocation->alive,0);InterlockedExchange(&allocation->sealed,1);allocation->generation++;}
            ReleaseSRWLockExclusive(&native_memory_records);
        }
    }
}
static int native_memory_same_sources(OwnerLink *left,OwnerLink *right){
    for(OwnerLink *entry=left;entry;entry=entry->next){int found=0;for(OwnerLink *other=right;other;other=other->next)if(entry->owner==other->owner){found=1;break;}if(!found)return 0;}
    for(OwnerLink *entry=right;entry;entry=entry->next){int found=0;for(OwnerLink *other=left;other;other=other->next)if(entry->owner==other->owner){found=1;break;}if(!found)return 0;}return 1;
}
static void native_memory_image_release(NativeMemoryImage *image){
    if(image&&!--image->references){free(image->before);free(image->after);free(image);}
}
static int native_memory_interests(NativeMemoryRevision *revision){
    NativeMemoryAllocation *allocation=revision->allocation;int ready=1;AcquireSRWLockExclusive(&native_memory_records);
    for(OwnerLink *source=revision->sources;source&&ready;source=source->next){
        NativeMemoryInterest *interest=allocation->interests;while(interest&&interest->owner!=source->owner)interest=interest->next;
        if(!interest){interest=(NativeMemoryInterest*)calloc(1,sizeof(*interest));if(!interest){ready=0;break;}interest->owner=source->owner;interest->next=allocation->interests;allocation->interests=interest;}
    }
    if(ready){
        for(OwnerLink *source=revision->sources;source;source=source->next)
            for(NativeMemoryInterest *interest=allocation->interests;interest;interest=interest->next)if(interest->owner==source->owner){InterlockedIncrement(&interest->revisions);break;}
        revision->counted=1;
    }
    ReleaseSRWLockExclusive(&native_memory_records);return ready;
}
static void native_memory_revision_release(NativeMemoryRevision *revision){
    while(revision&&!--revision->references){
        NativeMemoryRevision *previous=revision->previous;
        if(revision->counted)for(OwnerLink *source=revision->sources;source;source=source->next)
            for(NativeMemoryInterest *interest=revision->allocation->interests;interest;interest=interest->next)if(interest->owner==source->owner){InterlockedDecrement(&interest->revisions);break;}
        native_owner_links_free(revision->sources);native_memory_image_release(revision->image);free(revision);revision=previous;
    }
}
static int native_memory_read(uint64_t address,void *bytes,size_t length){
    DWORD error=GetLastError();SIZE_T copied=0;int ready=host_real_read_process_memory(GetCurrentProcess(),(const void*)(uintptr_t)address,bytes,length,&copied)&&copied==length;SetLastError(error);return ready;
}
static NativeMemoryImage *native_memory_image(NativeMemoryWrite *write){
    if(!write->length||write->length>SIZE_MAX)return NULL;
    NativeMemoryImage *image=(NativeMemoryImage*)calloc(1,sizeof(*image));if(!image)return NULL;
    image->references=1;image->start=write->address-write->allocation->address;image->length=write->length;
    image->before=(unsigned char*)malloc((size_t)image->length);image->after=(unsigned char*)malloc((size_t)image->length);
    if(!image->before||!image->after||!native_memory_read(write->address,image->before,(size_t)image->length)){native_memory_image_release(image);return NULL;}
    return image;
}
static int native_memory_image_covers(NativeMemoryImage *image,uint64_t start,uint64_t length){
    return image&&start>=image->start&&length<=image->length&&start-image->start<=image->length-length;
}
static int native_memory_revision_matches(JNIEnv *env,NativeMemoryRevision *revision,jobject module){
    for(;revision;revision=revision->previous)if(revision->sources&&native_owner_matches(env,revision->sources,module))return 1;return 0;
}
static int native_memory_revision_sources(OwnerLink **sources,NativeMemoryRevision *revision){
    for(;revision;revision=revision->complete?NULL:revision->previous)if(!native_owner_merge(sources,revision->sources))return 0;return 1;
}
static NativeMemoryRange *native_memory_split(NativeMemoryRange *range,uint64_t at){
    if(at<=range->start||at>=range->start+range->length)return range;
    NativeMemoryRange *tail=(NativeMemoryRange*)calloc(1,sizeof(*tail));if(!tail)return NULL;
    tail->start=at;tail->length=range->start+range->length-at;tail->revision=range->revision;if(tail->revision)tail->revision->references++;
    tail->next=range->next;range->next=tail;range->length=at-range->start;return tail;
}
static NativeMemoryRevision *native_memory_revision(NativeMemoryWrite *write,NativeMemoryRange *range){
    NativeMemoryRevision *revision=(NativeMemoryRevision*)calloc(1,sizeof(*revision));if(!revision)return NULL;
    revision->references=1;revision->allocation=write->allocation;revision->objectView=write->objectView;
    if((!write->initializing&&!native_owner_merge(&revision->sources,write->sources))||!native_memory_interests(revision)){native_memory_revision_release(revision);return NULL;}
    NativeMemoryImage *image=write->image;NativeMemoryRevision *previous=range->revision;
    revision->image=image;if(image)image->references++;
    revision->complete=!write->uncertain&&(!write->unknown||write->initializing)&&native_memory_image_covers(image,range->start,range->length);
    if(previous){
        if(previous->objectView==revision->objectView)
            revision->complete=revision->complete&&previous->complete&&native_memory_image_covers(previous->image,range->start,range->length)
                    &&memcmp(image->before+(size_t)(range->start-image->start),previous->image->after+(size_t)(range->start-previous->image->start),(size_t)range->length)==0;
        if(revision->complete&&previous->objectView==revision->objectView&&native_memory_same_sources(previous->sources,revision->sources)){
            // Consecutive writes by the same actual contributors retain the
            // first baseline instead of accumulating a revision per store.
            memcpy(image->before+(size_t)(range->start-image->start),previous->image->before+(size_t)(range->start-previous->image->start),(size_t)range->length);
            previous=previous->previous;
        }
        revision->previous=previous;if(previous)previous->references++;
    }
    if(!revision->complete)InterlockedExchange(&write->allocation->unknownUse,1);
    return revision;
}
static void native_memory_range(NativeMemoryWrite *write){
    if(!write->length)return;NativeMemoryAllocation *allocation=write->allocation;
    uint64_t start=write->address-allocation->address,end=start+write->length,position=start;NativeMemoryRange **link=&allocation->ranges;
    while(*link&&(*link)->start+(*link)->length<=start)link=&(*link)->next;
    if(*link&&(*link)->start<start){if(!native_memory_split(*link,start)){InterlockedExchange(&allocation->unknownUse,1);native_memory_unmapped(write->sources);return;}link=&(*link)->next;}
    while(position<end){
        NativeMemoryRange *range=*link;uint64_t right=end-position>65536?position+65536:end;
        if(!range||range->start>position){
            if(range&&range->start<right)right=range->start;
            NativeMemoryRange *added=(NativeMemoryRange*)calloc(1,sizeof(*added));if(!added){InterlockedExchange(&allocation->unknownUse,1);native_memory_unmapped(write->sources);return;}
            added->start=position;added->length=right-position;added->next=range;*link=range=added;
        }else{
            if(range->start+range->length<right)right=range->start+range->length;
            if(right<range->start+range->length&&!native_memory_split(range,right)){InterlockedExchange(&allocation->unknownUse,1);native_memory_unmapped(write->sources);return;}
        }
        NativeMemoryRevision *revision=native_memory_revision(write,range);
        if(!revision){InterlockedExchange(&allocation->unknownUse,1);native_memory_unmapped(write->sources);return;}
        native_memory_revision_release(range->revision);range->revision=revision;position=right;link=&range->next;
    }
}
static void native_memory_call_dispose(JNIEnv *env,NativeMemoryCall *call){
    if(call->locked)InterlockedDecrement(&call->allocation->changing);
    if(call->admitted)InterlockedDecrement(&call->allocation->users);
    if(call->locked)LeaveCriticalSection(&call->allocation->gate);
    if(call->allocator)native_original.DeleteGlobalRef(env,call->allocator);
    if(call->bufferRoot)native_original.DeleteWeakGlobalRef(env,call->bufferRoot);
    if(call->prepared){DeleteCriticalSection(&call->prepared->gate);free(call->prepared);}
    native_owner_links_free(call->sources);free(call);
}
static int native_memory_allocator(JNIEnv *env,NativeThread *state,Binding *binding,unsigned char *frame,NativeMemoryCall *call){
    Binding *effective=state->stack[state->depth-1];void *release=NULL;int known=0;
    AcquireSRWLockShared(&native_records);
    for(NativeUnsafeEntry *entry=native_unsafe_entries;entry;entry=entry->next){
        if(entry->method==effective->method&&entry->original==effective->original&&entry->operation==binding->unsafeOperation)known=1;
        if(entry->operation==UNSAFE_FREE)release=entry->original;
    }
    ReleaseSRWLockShared(&native_records);call->known=known;
    if(known&&release&&binding->unsafeOperation!=UNSAFE_FREE){
        jobject receiver=(jobject)(uintptr_t)native_unsafe_argument(binding,frame,1);
        call->allocator=native_original.NewGlobalRef(env,receiver);call->release=release;
        if(!call->allocator)return 0;
    }
    return !native_original.ExceptionCheck(env);
}
static int native_memory_call_begin(JNIEnv *env,NativeThread *state,Binding *binding,unsigned char *frame,NativeUnsafeFrame *use){
    NativeMemoryCall *call=(NativeMemoryCall*)calloc(1,sizeof(*call));if(!call){native_refuse(env,"NATIVE_MEMORY_SCOPE_CAPACITY");return 0;}
    state->control++;int ready=native_memory_sources(env,state,NULL,&call->sources,&call->unknown,0);state->control--;
    if(!ready){native_owner_links_free(call->sources);free(call);native_refuse(env,"NATIVE_MEMORY_SOURCES_UNAVAILABLE");return 0;}
    if(!native_memory_allocator(env,state,binding,frame,call))goto refused;
    if(binding->unsafeOperation!=UNSAFE_FREE){
        call->managed=native_buffer_allocation(env,state);if(native_original.ExceptionCheck(env))goto refused;
        if(call->managed){jobject buffer=native_buffer_constructing(env,state);
            if(buffer){call->bufferRoot=native_original.NewWeakGlobalRef(env,buffer);native_original.DeleteLocalRef(env,buffer);if(!call->bufferRoot)goto refused;}
            if(native_original.ExceptionCheck(env))goto refused;
        }
    }
    if(binding->unsafeOperation==UNSAFE_ALLOCATE)call->bytes=native_unsafe_argument(binding,frame,2);
    else{call->address=native_unsafe_argument(binding,frame,2);if(binding->unsafeOperation==UNSAFE_REALLOCATE)call->bytes=native_unsafe_argument(binding,frame,3);}
    if(call->address&&(binding->unsafeOperation==UNSAFE_FREE||binding->unsafeOperation==UNSAFE_REALLOCATE)){
        /* The old pointer can name private control state even when it has no
         * business allocation row. Check it before acquiring business holds;
         * realloc with a zero new size can release it just like free. */
        uintptr_t address=(uintptr_t)call->address;
        int restricted=state->heapCallback||state->toolingRestricted||state->vmDetaching||state->vmThreadEnding
                ||state->hostUnloadImage||state->hostRelease||state->hostLoad&&state->hostLoad->calling;
        int hit=native_control_range(address,address,restricted);
        if(hit){
            native_refuse(env,hit<0?"NATIVE_MEMORY_CONTROL_RANGE_UNAVAILABLE":binding->unsafeOperation==UNSAFE_FREE
                    ?"NATIVE_MEMORY_CONTROL_FREE_DENIED":"NATIVE_MEMORY_CONTROL_REALLOCATE_DENIED");
            goto refused;
        }
    }
    if(binding->unsafeOperation!=UNSAFE_FREE){
        call->prepared=(NativeMemoryAllocation*)calloc(1,sizeof(*call->prepared));
        if(!call->prepared){native_refuse(env,"NATIVE_MEMORY_RECORD_CAPACITY");goto refused;}
        InitializeCriticalSection(&call->prepared->gate);
    }
    if(call->address){
        AcquireSRWLockShared(&native_memory_records);call->allocation=native_memory_find(call->address,0,1);
        if(call->allocation&&!InterlockedCompareExchange(&call->allocation->sealed,0,0)){
            call->generation=call->allocation->generation;InterlockedIncrement(&call->allocation->users);call->admitted=1;
        }
        ReleaseSRWLockShared(&native_memory_records);
        if(call->allocation){
            if(!call->admitted){native_refuse(env,"NATIVE_MEMORY_ALLOCATION_RETIRED");goto refused;}
            EnterCriticalSection(&call->allocation->gate);call->locked=1;InterlockedIncrement(&call->allocation->changing);
            if(!InterlockedCompareExchange(&call->allocation->alive,0,0)||call->allocation->address!=call->address||call->allocation->generation!=call->generation){
                native_refuse(env,"NATIVE_MEMORY_ALLOCATION_CHANGED");goto refused;
            }
            if(InterlockedCompareExchange(&call->allocation->users,0,0)!=1||InterlockedCompareExchange(&call->allocation->writers,0,0)
                    ||InterlockedCompareExchange(&call->allocation->reading,0,0)||InterlockedCompareExchange(&call->allocation->changing,0,0)!=1){
                native_refuse(env,"NATIVE_MEMORY_ALLOCATION_IN_USE");goto refused;
            }
            if(call->allocation->hostFamily==NATIVE_MEMORY_HOST_IMAGE
                    ||call->allocation->hostFamily&&native_memory_buffer_aliases(env,call->allocation)){
                native_refuse(env,"NATIVE_HOST_MEMORY_ORIGINAL_LIFETIME_REQUIRED");goto refused;
            }
            if(InterlockedCompareExchange(&call->allocation->managed,0,0)){
                int allowed=binding->unsafeOperation==UNSAFE_FREE;
                if(allowed&&call->allocation->bufferAction)allowed=native_buffer_releasing(env,state,call->allocation->bufferAction);
                else if(allowed&&call->allocation->bufferRoot){
                    jobject constructing=native_buffer_constructing(env,state);
                    allowed=constructing&&native_original.IsSameObject(env,constructing,call->allocation->bufferRoot);
                    if(constructing)native_original.DeleteLocalRef(env,constructing);
                }
                if(!allowed||native_original.ExceptionCheck(env)){native_refuse(env,"MANAGED_BUFFER_ORIGINAL_RELEASE_REQUIRED");goto refused;}
                call->cleanup=call->known&&(call->allocation->bufferAction||call->allocation->bufferRoot);
            }
        }else native_memory_unmapped(call->sources);
    }
    if(native_stopped(env)&&!call->cleanup){native_refuse(env,"RONOVA_STOPPED_NATIVE_MEMORY_OPERATION");goto refused;}
    use->memoryCall=call;return 1;
refused:
    native_memory_call_dispose(env,call);return 0;
}
static void native_memory_clip(NativeMemoryAllocation *allocation){
    NativeMemoryRange **link=&allocation->ranges;
    while(*link){NativeMemoryRange *range=*link;
        if(range->start>=allocation->bytes){*link=range->next;native_memory_revision_release(range->revision);free(range);}
        else{if(range->length>allocation->bytes-range->start)range->length=allocation->bytes-range->start;link=&range->next;}
    }
}
static void native_memory_released(JNIEnv *env,NativeMemoryAllocation *allocation){
    InterlockedExchange(&allocation->alive,0);InterlockedExchange(&allocation->sealed,1);allocation->generation++;
    int aliasesIdle=1;for(NativeBufferAlias *alias=allocation->aliases;alias;alias=alias->next)if(alias->users){aliasesIdle=0;break;}
    if(env&&aliasesIdle)native_memory_buffer_aliases_clear(env,allocation);
    NativeMemoryRange *range=allocation->ranges;allocation->ranges=NULL;
    while(range){NativeMemoryRange *next=range->next;native_memory_revision_release(range->revision);free(range);range=next;}
    if(env&&allocation->allocator){native_original.DeleteGlobalRef(env,allocation->allocator);allocation->allocator=NULL;}allocation->release=NULL;
    if(env&&allocation->bufferRoot){native_original.DeleteWeakGlobalRef(env,allocation->bufferRoot);allocation->bufferRoot=NULL;}
    if(env&&allocation->bufferAction){native_original.DeleteWeakGlobalRef(env,allocation->bufferAction);allocation->bufferAction=NULL;}
}
static void native_memory_reap_references(JNIEnv *env,NativeMemoryAllocation *allocation){
    if(env&&TryEnterCriticalSection(&allocation->gate)){
        if(!allocation->alive&&!allocation->users&&!allocation->changing){
            native_memory_buffer_aliases_clear(env,allocation);
            if(allocation->allocator){native_original.DeleteGlobalRef(env,allocation->allocator);allocation->allocator=NULL;}
            if(allocation->bufferRoot){native_original.DeleteWeakGlobalRef(env,allocation->bufferRoot);allocation->bufferRoot=NULL;}
            if(allocation->bufferAction){native_original.DeleteWeakGlobalRef(env,allocation->bufferAction);allocation->bufferAction=NULL;}
            NativeMemoryRange *range=allocation->ranges;allocation->ranges=NULL;
            while(range){NativeMemoryRange *next=range->next;native_memory_revision_release(range->revision);free(range);range=next;}
        }
        LeaveCriticalSection(&allocation->gate);
    }
    native_memory_release_provider(allocation);
}
static void native_memory_call_end(JNIEnv *env,NativeThread *state,Binding *binding,unsigned char *frame,NativeUnsafeFrame *use){
    (void)state;NativeMemoryCall *call=use->memoryCall;if(!call)return;use->memoryCall=NULL;
    uint64_t returned=0;if(frame)memcpy(&returned,frame+native_unsafe_save(binding)+96,sizeof(returned));
    int completed=frame&&!native_original.ExceptionCheck(env);
    AcquireSRWLockExclusive(&native_memory_records);
    if(completed&&binding->unsafeOperation==UNSAFE_FREE){if(call->allocation){
        if(call->known)native_memory_released(env,call->allocation);
        else {InterlockedExchange(&call->allocation->unknownUse,1);InterlockedExchange(&call->allocation->releaseFailed,1);}
    }}
    else if(completed&&returned){
        NativeMemoryAllocation *allocation=call->allocation;
        if(!allocation){allocation=call->prepared;call->prepared=NULL;allocation->sources=call->sources;call->sources=NULL;allocation->next=native_memory_allocations;native_memory_allocations=allocation;}
        else if(!native_owner_merge(&allocation->sources,call->sources)){InterlockedExchange(&allocation->unknownUse,1);InterlockedIncrement(&native_failures);}
        if(allocation->allocator)native_original.DeleteGlobalRef(env,allocation->allocator);
        allocation->allocator=call->allocator;call->allocator=NULL;allocation->release=call->release;
        if(call->managed){InterlockedExchange(&allocation->managed,1);allocation->bufferRoot=call->bufferRoot;call->bufferRoot=NULL;}
        if(call->unknown||!call->known||binding->unsafeOperation==UNSAFE_REALLOCATE&&call->address&&!call->allocation)InterlockedExchange(&allocation->unknownUse,1);
        native_memory_buffer_aliases_clear(env,allocation);
        allocation->hostFamily=0;allocation->hostContext=allocation->hostFreeFlags=0;allocation->hostContextEpoch=0;allocation->hostAllocator=allocation->imageTag=NULL;
        allocation->address=returned;allocation->bytes=call->bytes;allocation->generation++;InterlockedExchange(&allocation->alive,1);native_memory_clip(allocation);
    }
    if(call->allocation&&(!completed||!call->known||binding->unsafeOperation==UNSAFE_REALLOCATE&&!returned&&!call->bytes)){
        InterlockedExchange(&call->allocation->unknownUse,1);InterlockedExchange(&call->allocation->releaseFailed,1);
    }
    ReleaseSRWLockExclusive(&native_memory_records);
    if(!frame)native_memory_unmapped(call->sources);
    NativeMemoryAllocation *finished=call->allocation;native_memory_call_dispose(env,call);if(finished)native_memory_reap_references(env,finished);
}
JNIEXPORT jlongArray JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_bindBufferStorage0(JNIEnv *env,jclass type,jobject buffer,jobject action,jlong address,jlong bytes){
    jmethodID gate=native_controller?native_original.GetStaticMethodID(env,native_controller,"bufferBindingCaller","(Ljava/nio/Buffer;Ljava/lang/Object;JJ)Z"):NULL;
    if(!gate||!native_original.IsSameObject(env,type,native_controller)||!native_original.CallStaticBooleanMethod(env,native_controller,gate,buffer,action,address,bytes)){
        native_refuse(env,"ACTUAL_BUFFER_STORAGE_BINDING_REQUIRED");return NULL;
    }
    NativeThread *state=native_thread();if(!state)return NULL;state->control++;jlong values[2]={0,0};NativeMemoryAllocation *found=NULL;
    AcquireSRWLockShared(&native_memory_records);
    for(NativeMemoryAllocation *allocation=native_memory_allocations;allocation;allocation=allocation->next)
        if(InterlockedCompareExchange(&allocation->alive,0,0)&&InterlockedCompareExchange(&allocation->managed,0,0)&&allocation->bufferRoot
                &&native_original.IsSameObject(env,allocation->bufferRoot,buffer)&&allocation->address==(uint64_t)address&&allocation->bytes==(uint64_t)bytes){found=allocation;break;}
    ReleaseSRWLockShared(&native_memory_records);
    if(found&&TryEnterCriticalSection(&found->gate)){
        if(InterlockedCompareExchange(&found->alive,0,0)&&!InterlockedCompareExchange(&found->changing,0,0)&&!InterlockedCompareExchange(&found->sealed,0,0)
                &&found->address==(uint64_t)address&&found->bytes==(uint64_t)bytes&&native_original.IsSameObject(env,found->bufferRoot,buffer)){
            if(!found->bufferAction)found->bufferAction=native_original.NewWeakGlobalRef(env,action);
            if(found->bufferAction&&native_original.IsSameObject(env,found->bufferAction,action)){values[0]=(jlong)(uintptr_t)found;values[1]=(jlong)found->generation;}
        }
        LeaveCriticalSection(&found->gate);
    }
    jlongArray result=native_original.ExceptionCheck(env)?NULL:native_original.NewLongArray(env,2);if(result)native_original.SetLongArrayRegion(env,result,0,2,values);state->control--;return result;
}
static int native_memory_authority(JNIEnv *env,jclass type){
    return native_controller&&native_original.IsSameObject(env,type,native_controller)&&native_memory_gate
            &&native_original.CallStaticBooleanMethod(env,native_controls,native_memory_gate)&&!native_original.ExceptionCheck(env);
}
static int native_memory_read_sources(NativeMemoryWrite *read,OwnerLink **sources){
    NativeMemoryAllocation *allocation=read->allocation;
    if(!allocation||!InterlockedCompareExchange(&allocation->alive,0,0)||allocation->generation!=read->generation)return 1;
    if(InterlockedCompareExchange(&allocation->opaque,0,0))read->opaque=1;
    int ready=native_memory_creator_sources(allocation,sources);uint64_t start=read->address-allocation->address;
    for(NativeMemoryRange *range=allocation->ranges;ready&&range;range=range->next)
        if(start<range->start+range->length&&range->start<start+read->length)ready=native_memory_revision_sources(sources,range->revision);
    return ready;
}
/* Internal CNG range lifetime only. This is not a public address-to-ownership
 * adapter. Missing stack/JNI/provider storage stays uncovered, with no row.
 * Hash objects remain opaque; original CNG operations alone mutate them. */
struct NativeHostCryptoSpan {NativeMemoryAllocation *allocation;uint64_t generation;int writing;};
static int native_memory_crypto_restricted(void){
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    return host_loader_callout()||(state&&(state->heapCallback||state->toolingRestricted||state->vmDetaching||state->vmThreadEnding
            ||state->hostUnloadImage||state->hostRelease||state->hostLoad&&state->hostLoad->calling));
}
static void native_memory_crypto_unobserved(OwnerLink *owners,int restricted){
    if(restricted){if(!TryAcquireSRWLockExclusive(&native_memory_records)){InterlockedIncrement(&native_failures);return;}}
    else AcquireSRWLockExclusive(&native_memory_records);
    if(!native_owner_merge(&native_memory_unknown,owners))InterlockedIncrement(&native_failures);
    ReleaseSRWLockExclusive(&native_memory_records);
}
static NativeHostCryptoSpan *native_memory_crypto_span(uint64_t address,uint64_t bytes,OwnerLink *owners,int mode,
        OwnerLink **observed,int *unknown,int *denied){
    if(!address||!bytes)return NULL;
    NativeMemoryAllocation *allocation=NULL;*denied=0;int restricted=native_memory_crypto_restricted();
    if(restricted){if(!TryAcquireSRWLockShared(&native_memory_records)){*denied=1;return NULL;}}
    else AcquireSRWLockShared(&native_memory_records);
    allocation=native_memory_find(address,bytes,0);
    if(!allocation&&native_memory_find(address,1,0))*denied=1;
    ReleaseSRWLockShared(&native_memory_records);
    if(*denied)return NULL;
    if(!allocation){*unknown=1;return NULL;}
    if(restricted&&!TryEnterCriticalSection(&allocation->gate)){*denied=1;return NULL;}
    if(!restricted)EnterCriticalSection(&allocation->gate);
    int ready=allocation->alive&&!allocation->sealed&&!allocation->retiring&&!allocation->changing&&!allocation->restoring
            &&!allocation->writers&&address>=allocation->address&&bytes<=allocation->bytes&&address-allocation->address<=allocation->bytes-bytes;
    if(mode)ready=ready&&!allocation->users&&!allocation->reading&&!native_memory_buffer_aliases(NULL,allocation);
    NativeMemoryWrite read={0};read.allocation=allocation;read.address=address;read.length=bytes;read.generation=allocation->generation;
    if(ready&&!native_memory_read_sources(&read,observed)){*unknown=1;ready=0;}
    if(ready&&native_owner_stopped(*observed))ready=0;
    NativeHostCryptoSpan *span=ready?(NativeHostCryptoSpan*)calloc(1,sizeof(*span)):NULL;
    if(span){
        span->allocation=allocation;span->generation=allocation->generation;span->writing=mode==2;
        int locked=restricted?TryAcquireSRWLockExclusive(&native_memory_records):(AcquireSRWLockExclusive(&native_memory_records),1);
        if(locked){
            InterlockedIncrement(&allocation->users);InterlockedIncrement(span->writing?&allocation->writers:&allocation->reading);
            if(!native_owner_merge(&allocation->readers,owners)||!owners)InterlockedExchange(&allocation->unknownReaders,1);
            if(mode)InterlockedExchange(&allocation->opaque,1);
            ReleaseSRWLockExclusive(&native_memory_records);
            *unknown|=read.opaque||allocation->unknownUse||allocation->unknownReaders;
        }else {free(span);span=NULL;*denied=1;}
    }else *denied=1;
    LeaveCriticalSection(&allocation->gate);return span;
}
static void native_memory_crypto_span_end(NativeHostCryptoSpan *span){
    if(!span)return;NativeMemoryAllocation *allocation=span->allocation;int restricted=native_memory_crypto_restricted();
    /* Row identities are immutable tombstones. Counter-only release needs no
     * allocation gate, and the held users count prevents actual retirement. */
    if(restricted){if(!TryAcquireSRWLockShared(&native_memory_records)){InterlockedIncrement(&native_failures);return;}}
    else AcquireSRWLockShared(&native_memory_records);
    if(allocation->generation==span->generation){
        InterlockedDecrement(span->writing?&allocation->writers:&allocation->reading);InterlockedDecrement(&allocation->users);
    }else {InterlockedExchange(&allocation->unknownUse,1);InterlockedIncrement(&native_failures);}
    ReleaseSRWLockShared(&native_memory_records);free(span);
}
JNIEXPORT jlong JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_memoryWriteBegin0(JNIEnv *env,jclass type,jlong address,jlong length,jobjectArray contributors,jboolean reading,jboolean snapshot){
    if(!native_memory_authority(env,type)){native_refuse(env,"ACTUAL_NATIVE_MEMORY_WRITER_REQUIRED");return 0;}
    NativeThread *state=native_thread();if(!state||length<0){native_refuse(env,"NATIVE_MEMORY_SCOPE_UNAVAILABLE");return 0;}
    NativeMemoryWrite *write=(NativeMemoryWrite*)calloc(1,sizeof(*write));if(!write){native_refuse(env,"NATIVE_MEMORY_SCOPE_CAPACITY");return 0;}
    state->control++;int ready=native_memory_sources(env,state,contributors,&write->sources,&write->unknown,reading==JNI_TRUE&&contributors!=NULL);state->control--;
    if(!ready){native_owner_links_free(write->sources);free(write);native_refuse(env,"NATIVE_MEMORY_SOURCES_UNAVAILABLE");return 0;}
    write->address=(uint64_t)address;write->length=(uint64_t)length;write->reading=reading==JNI_TRUE;write->detached=write->reading&&snapshot==JNI_TRUE;
    if(!write->reading&&write->length){
        /* A raw writer must not mistake an already registered control span for
         * an untracked business allocation. This is admission, not a lock over
         * the later JVM Unsafe body: no private/domain gate crosses that body
         * or any Java/JNI transition. Concurrent later publication stays open. */
        uintptr_t first=(uintptr_t)write->address;uint64_t span=write->length-1;
        int restricted=state->heapCallback||state->toolingRestricted||state->vmDetaching||state->vmThreadEnding
                ||state->hostUnloadImage||state->hostRelease||state->hostLoad&&state->hostLoad->calling;
        int hit=span>UINTPTR_MAX-first?-1:native_control_range(first,first+(uintptr_t)span,restricted);
        if(hit){
            /* A zero receipt alone would let rawReceipt authorize the write.
             * Keep the pending exception through normal scope/source cleanup. */
            native_refuse(env,hit>0?"NATIVE_MEMORY_CONTROL_WRITE_DENIED":"NATIVE_MEMORY_CONTROL_RANGE_UNAVAILABLE");
            goto refused;
        }
    }
    AcquireSRWLockShared(&native_memory_records);write->allocation=native_memory_find(write->address,write->length,0);
    if(write->allocation&&!InterlockedCompareExchange(&write->allocation->sealed,0,0)&&!InterlockedCompareExchange(&write->allocation->retiring,0,0)
            &&!InterlockedCompareExchange(&write->allocation->changing,0,0)){
        write->generation=write->allocation->generation;InterlockedIncrement(&write->allocation->users);write->admitted=1;
        if(write->detached&&!native_memory_creator_sources(write->allocation,&write->observed))write->unobserved=1;
    }
    ReleaseSRWLockShared(&native_memory_records);
    if(write->allocation&&!write->admitted){native_refuse(env,"NATIVE_MEMORY_ALLOCATION_RETIRED");goto refused;}
    NativeMemoryWrite *previous=state->memoryWrite;
    if(write->reading)for(NativeMemoryWrite *outer=previous;outer;outer=outer->previous){
        if(!outer->reading||!outer->detached||outer->allocation!=write->allocation||write->address<outer->address
                ||write->length>outer->length||write->address-outer->address>outer->length-write->length)continue;
        write->detached=1;write->generation=outer->generation;write->root=outer->root;write->previous=previous;
        if(!native_owner_merge(&write->observed,outer->root->observed))write->unobserved=1;
        state->memoryWrite=write;return (jlong)(uintptr_t)write;
    }
    if(!write->detached)for(NativeMemoryWrite *outer=previous;outer;outer=outer->previous){
        if(outer->detached||outer->reading!=write->reading||outer->allocation!=write->allocation||write->address<outer->address
                ||write->length>outer->length||write->address-outer->address>outer->length-write->length)continue;
        if(write->reading&&(write->address!=outer->address||write->length!=outer->length))continue;
        write->generation=outer->generation;write->root=outer->root;write->previous=previous;state->memoryWrite=write;return (jlong)(uintptr_t)write;
    }
    write->root=write;
    if(write->allocation){
        NativeMemoryAllocation *allocation=write->allocation;
        if(write->detached){write->locked=TryEnterCriticalSection(&allocation->gate)!=0;
            if(!write->locked&&InterlockedCompareExchange(&allocation->restoring,0,0)){EnterCriticalSection(&allocation->gate);write->locked=1;}}
        else{EnterCriticalSection(&allocation->gate);write->locked=1;}
        if(!write->locked){InterlockedIncrement(&allocation->reading);write->unobserved=1;}
        else if(!InterlockedCompareExchange(&allocation->alive,0,0)||allocation->generation!=write->generation||write->address<allocation->address||write->length>allocation->bytes||write->address-allocation->address>allocation->bytes-write->length){
            LeaveCriticalSection(&allocation->gate);write->locked=0;native_refuse(env,"NATIVE_MEMORY_ALLOCATION_CHANGED");goto refused;
        }else{
            write->generation=allocation->generation;InterlockedIncrement(write->reading?&allocation->reading:&allocation->writers);
            if(!write->reading&&allocation->bufferRoot&&!allocation->bufferAction)write->initializing=native_buffer_initializing(env,state,allocation->bufferRoot);
            if(native_original.ExceptionCheck(env)){
                InterlockedDecrement(write->reading?&allocation->reading:&allocation->writers);LeaveCriticalSection(&allocation->gate);write->locked=0;goto refused;
            }
            if(!write->reading){allocation->writeEpoch++;write->image=native_memory_image(write);}
            if(write->detached){
                if(!native_memory_read_sources(write,&write->observed))write->unobserved=1;
                LeaveCriticalSection(&allocation->gate);write->locked=0;
            }
        }
    }else if(length&&!write->reading)native_memory_unmapped(write->sources);
    write->previous=state->memoryWrite;state->memoryWrite=write;return (jlong)(uintptr_t)write;
refused:
    if(write->admitted)InterlockedDecrement(&write->allocation->users);
    native_owner_links_free(write->sources);native_owner_links_free(write->observed);free(write);return 0;
}
static void native_memory_write_end(NativeThread *state,NativeMemoryWrite *write,int applied){
    NativeMemoryAllocation *allocation=write->allocation;state->memoryWrite=write->previous;
    if(write->root!=write){
        if(applied||write->written){write->root->written=1;if(!native_owner_merge(&write->root->sources,write->sources)){write->root->unknown=1;native_memory_unmapped(write->sources);}}
        write->root->unknown|=write->unknown;
        if(write->reading&&(applied||write->written)){
            write->root->unobserved|=write->unobserved;
            write->root->opaque|=write->opaque;
            if(!native_owner_merge(&write->root->observed,write->observed))write->root->unobserved=1;
        }
        if(write->admitted)InterlockedDecrement(&allocation->users);
        native_owner_links_free(write->sources);native_owner_links_free(write->observed);native_memory_image_release(write->image);free(write);return;
    }
    if(allocation){
        if(write->detached)write->locked=TryEnterCriticalSection(&allocation->gate)!=0;
        int current=(!write->detached||write->locked)&&InterlockedCompareExchange(&allocation->alive,0,0)&&allocation->generation==write->generation;
        if(!write->reading&&current){
            if(write->image&&!native_memory_read(write->address,write->image->after,(size_t)write->length)){native_memory_image_release(write->image);write->image=NULL;}
            if(!applied&&!write->written){
                if(write->image&&memcmp(write->image->before,write->image->after,(size_t)write->length)!=0){write->written=1;write->uncertain=1;}
                else if(!write->image){InterlockedExchange(&allocation->unknownUse,1);native_memory_unmapped(write->sources);}
            }
        }
        if(applied||write->written){
            if(write->unknown&&!write->initializing)InterlockedExchange(&allocation->unknownUse,1);
            if(write->detached&&!write->locked){InterlockedExchange(&allocation->unknownReaders,1);native_memory_unmapped(write->sources);}
            else if(current){
                if(write->reading){AcquireSRWLockExclusive(&native_memory_records);int ready=native_owner_merge(&allocation->readers,write->sources);if(!write->sources||!ready)InterlockedExchange(&allocation->unknownReaders,1);ReleaseSRWLockExclusive(&native_memory_records);if(!ready)native_memory_unmapped(write->sources);}
                else native_memory_range(write);
            }
            else native_memory_unmapped(write->sources);
            if(write->unobserved){InterlockedExchange(&allocation->unknownReaders,1);native_memory_unmapped(write->sources);}
        }
        InterlockedDecrement(write->reading?&allocation->reading:&allocation->writers);
        if(write->admitted)InterlockedDecrement(&allocation->users);
        if(write->locked)LeaveCriticalSection(&allocation->gate);
    }
    native_owner_links_free(write->sources);native_owner_links_free(write->observed);native_memory_image_release(write->image);free(write);
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_memoryWriteEnd0(JNIEnv *env,jclass type,jlong token,jboolean applied){
    if(!native_memory_authority(env,type)){native_refuse(env,"ACTUAL_NATIVE_MEMORY_WRITER_REQUIRED");return;}
    NativeThread *state=native_thread();NativeMemoryWrite *write=state?state->memoryWrite:NULL;
    if(!write||(jlong)(uintptr_t)write!=token){native_refuse(env,"ACTUAL_NATIVE_MEMORY_SCOPE_ORDER_REQUIRED");return;}
    native_memory_write_end(state,write,applied==JNI_TRUE);
}
JNIEXPORT jobjectArray JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_memoryReadSources0(JNIEnv *env,jclass type,jlong token){
    if(!native_memory_authority(env,type)){native_refuse(env,"ACTUAL_NATIVE_MEMORY_READER_REQUIRED");return NULL;}
    NativeThread *state=native_thread();NativeMemoryWrite *read=state?state->memoryWrite:NULL;
    while(read&&(jlong)(uintptr_t)read!=token)read=read->previous;
    if(!read||!read->reading){native_refuse(env,"ACTUAL_NATIVE_MEMORY_READ_SCOPE_REQUIRED");return NULL;}
    state->control++;OwnerLink *sources=NULL;int ready=native_owner_merge(&sources,read->observed);NativeMemoryAllocation *allocation=read->allocation;
    if(ready&&allocation){
        // A copy holds its destination gate. Never wait for its source gate
        // here: two opposite copies must not lock one another indefinitely.
        int locked=!read->detached||TryEnterCriticalSection(&allocation->gate);
        if(locked){ready=native_memory_read_sources(read,&sources);if(read->detached)LeaveCriticalSection(&allocation->gate);}
        else read->unobserved=1;
    }
    if(ready)ready=native_owner_merge(&read->observed,sources);
    jclass module=NULL;jobjectArray result=NULL,retained=NULL;jsize count=0,at=0;
    if(ready){
        // A real exposed allocation retains its known contributors, but native
        // instructions may have added an unobserved writer. Use the existing
        // null-source protocol without turning this local opacity into a
        // global unmapped allocation/source failure.
        if(read->opaque||read->unobserved)count=at=1;
        for(OwnerLink *source=sources;source;source=source->next)count++;
        module=native_original.FindClass(env,"java/lang/Module");retained=module?native_original.NewObjectArray(env,count,module,NULL):NULL;
        for(OwnerLink *source=sources;retained&&source&&!native_original.ExceptionCheck(env);source=source->next){
            jobject actual=native_original.NewLocalRef(env,source->owner->module);if(!actual)continue;
            native_original.SetObjectArrayElement(env,retained,at++,actual);native_original.DeleteLocalRef(env,actual);
        }
        if(retained&&!native_original.ExceptionCheck(env))result=native_original.NewObjectArray(env,at,module,NULL);
        for(jsize i=0;result&&i<at&&!native_original.ExceptionCheck(env);i++){
            jobject actual=native_original.GetObjectArrayElement(env,retained,i);native_original.SetObjectArrayElement(env,result,i,actual);native_original.DeleteLocalRef(env,actual);
        }
    }
    if(retained)native_original.DeleteLocalRef(env,retained);if(module)native_original.DeleteLocalRef(env,module);native_owner_links_free(sources);state->control--;
    if(!ready)native_refuse(env,"NATIVE_MEMORY_READ_SOURCE_CAPACITY");return result;
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_memoryWriteContributors0(JNIEnv *env,jclass type,jlong token,jobjectArray contributors){
    if(!native_memory_authority(env,type)){native_refuse(env,"ACTUAL_NATIVE_MEMORY_WRITER_REQUIRED");return;}
    NativeThread *state=native_thread();NativeMemoryWrite *write=state?state->memoryWrite:NULL;
    if(!write||write->reading||(jlong)(uintptr_t)write!=token){native_refuse(env,"ACTUAL_NATIVE_MEMORY_WRITE_SCOPE_REQUIRED");return;}
    state->control++;int ready=native_memory_sources(env,state,contributors,&write->sources,&write->unknown,0);state->control--;
    if(!ready)native_refuse(env,"NATIVE_MEMORY_WRITE_SOURCE_CAPACITY");
}
static void native_memory_thread_end(JNIEnv *env,NativeThread *state){
    (void)env;while(state->memoryWrite){NativeMemoryWrite *write=state->memoryWrite;native_memory_unmapped(write->sources);native_memory_write_end(state,write,0);}
}
static int native_memory_interest_matches(JNIEnv *env,NativeMemoryAllocation *allocation,jobject module){
    for(NativeMemoryInterest *interest=allocation->interests;interest;interest=interest->next)
        if(InterlockedCompareExchange(&interest->revisions,0,0)>0&&(!module||native_original.IsSameObject(env,interest->owner->module,module)))return 1;return 0;
}
static int native_memory_selected(OwnerLink *sources,OwnerLink *selected){
    if(!sources)return 0;
    for(OwnerLink *source=sources;source;source=source->next){
        if(!InterlockedCompareExchange(&source->owner->stopped,0,0))return 0;
        int found=0;for(OwnerLink *target=selected;target;target=target->next)if(target->owner==source->owner){found=1;break;}if(!found)return 0;
    }return 1;
}
static int native_memory_targets(NativeMemoryRevision *revision,OwnerLink *selected){
    for(;revision;revision=revision->previous)for(OwnerLink *source=revision->sources;source;source=source->next)
        for(OwnerLink *target=selected;target;target=target->next)if(target->owner==source->owner)return 1;return 0;
}
static int native_memory_busy_targets(NativeMemoryAllocation *allocation,OwnerLink *selected){
    for(OwnerLink *source=allocation->sources;source;source=source->next)
        if(!native_memory_preserved(allocation,source->owner))for(OwnerLink *target=selected;target;target=target->next)if(source->owner==target->owner)return 1;
    for(NativeMemoryInterest *interest=allocation->interests;interest;interest=interest->next)
        if(InterlockedCompareExchange(&interest->revisions,0,0)>0)
            for(OwnerLink *target=selected;target;target=target->next)if(target->owner==interest->owner)return 1;return 0;
}
static int native_memory_created_targets(NativeMemoryAllocation *allocation,OwnerLink *selected){
    for(OwnerLink *source=allocation->sources;source;source=source->next)
        if(!native_memory_preserved(allocation,source->owner))for(OwnerLink *target=selected;target;target=target->next)if(source->owner==target->owner)return 1;
    return 0;
}
static int native_memory_private(JNIEnv *env,NativeMemoryAllocation *allocation,OwnerLink *selected){
    /* A raw operator delete cannot replace the program's destructor/delete
     * expression. Only an actual observed delete retires those allocations. */
    if((!allocation->allocator&&!allocation->hostFamily)||allocation->hostFamily==NATIVE_MEMORY_HOST_IMAGE
            ||allocation->hostFamily==NATIVE_MEMORY_HOST_CPP||allocation->hostFamily==NATIVE_MEMORY_HOST_CUSTOM||!allocation->release||InterlockedCompareExchange(&allocation->managed,0,0)||!native_memory_selected(allocation->sources,selected)
            ||InterlockedCompareExchange(&allocation->unknownReaders,0,0)||InterlockedCompareExchange(&allocation->unknownUse,0,0)
            ||InterlockedCompareExchange(&allocation->releaseFailed,0,0)||InterlockedCompareExchange(&allocation->sealed,0,0)
            ||InterlockedCompareExchange(&allocation->typedObjects,0,0))return 0;
    if(native_memory_buffer_aliases(env,allocation))return 0;
    if(allocation->hostFamily==NATIVE_MEMORY_HOST_HEAP){
        NativeHostMemoryContext *heap=native_memory_host_context(allocation->release,allocation->hostContext,0);
        if(!heap||heap->epoch!=allocation->hostContextEpoch||!heap->observed||heap->changing||heap->uncertain)return 0;
    }else if(allocation->hostFamily==NATIVE_MEMORY_HOST_ARENA||allocation->hostFamily==NATIVE_MEMORY_HOST_OTHER){
        NativeHostArenaProvider *provider=native_memory_arena_provider(allocation->release,0);
        NativeHostArenaContext *arena=native_memory_arena_context(provider,allocation->hostContext,0);
        if(!provider||provider->changing||provider->uncertain)return 0;
        if(!(allocation->hostFamily==NATIVE_MEMORY_HOST_OTHER&&!allocation->hostContext)
                &&(!arena||arena->uncertain||arena->epoch!=allocation->hostContextEpoch))return 0;
    }
    if(allocation->readers&&!native_memory_selected(allocation->readers,selected))return 0;
    for(NativeMemoryInterest *interest=allocation->interests;interest;interest=interest->next){
        if(InterlockedCompareExchange(&interest->revisions,0,0)<=0)continue;
        int found=0;for(OwnerLink *target=selected;target;target=target->next)if(target->owner==interest->owner){found=1;break;}
        if(!found||!InterlockedCompareExchange(&interest->owner->stopped,0,0))return 0;
    }
    return 1;
}
static int native_memory_retire_allocation(JNIEnv *env,NativeMemoryAllocation *allocation,OwnerLink *selected){
    // The caller holds this allocation's actual operation gate. Admission and
    // sealing also share the lookup lock, so a read waiting for the gate cannot
    // disappear from the decision to call the saved allocator's deallocator.
    AcquireSRWLockExclusive(&native_memory_records);
    int ready=InterlockedCompareExchange(&allocation->alive,0,0)&&native_memory_private(env,allocation,selected)
            &&!InterlockedCompareExchange(&allocation->users,0,0)&&!InterlockedCompareExchange(&allocation->writers,0,0)
            &&!InterlockedCompareExchange(&allocation->reading,0,0)&&!InterlockedCompareExchange(&allocation->changing,0,0);
    NativeHostMemoryContext *heap=NULL;NativeHostArenaProvider *arenaProvider=NULL;
    if(ready){
        if(allocation->hostFamily==NATIVE_MEMORY_HOST_HEAP){heap=native_memory_host_context(allocation->release,allocation->hostContext,0);heap->users++;}
        else if(allocation->hostFamily==NATIVE_MEMORY_HOST_ARENA||allocation->hostFamily==NATIVE_MEMORY_HOST_OTHER){arenaProvider=native_memory_arena_provider(allocation->release,0);arenaProvider->users++;}
        InterlockedExchange(&allocation->sealed,1);InterlockedIncrement(&allocation->changing);
    }
    ReleaseSRWLockExclusive(&native_memory_records);if(!ready)return 0;
    NativeHostMemoryHold arenaHold={0};NativeHostMemoryCall arenaCall={0};
    if(arenaProvider){
        arenaHold.allocation=allocation;arenaHold.generation=allocation->generation;
        arenaCall.family=allocation->hostFamily;arenaCall.operation=NATIVE_MEMORY_HOST_FREE;
        arenaCall.release=allocation->release;arenaCall.context=allocation->hostContext;arenaCall.contextEpoch=allocation->hostContextEpoch;
        arenaCall.address=allocation->address;arenaCall.bytes=allocation->bytes;arenaCall.sources=allocation->sources;
        arenaCall.holds=&arenaHold;arenaCall.arenaProvider=arenaProvider;arenaCall.retirement=1;
    }
    // No allocator runs under a tracker mutex. Sealing shares admission's
    // lookup lock, and this exact row remains stable while its original free runs.
    LeaveCriticalSection(&allocation->gate);
    int returned=0;
    __try {
        if(allocation->hostFamily==NATIVE_MEMORY_HOST_HEAP){
            ready=((BOOL (WINAPI*)(HANDLE,DWORD,LPVOID))allocation->release)((HANDLE)allocation->hostContext,(DWORD)allocation->hostFreeFlags,(LPVOID)(uintptr_t)allocation->address)!=FALSE;
        }else if(allocation->hostFamily==NATIVE_MEMORY_HOST_VIRTUAL){
            ready=((BOOL (WINAPI*)(LPVOID,SIZE_T,DWORD))allocation->release)((LPVOID)(uintptr_t)allocation->address,0,MEM_RELEASE)!=FALSE;
        }else if(allocation->hostFamily==NATIVE_MEMORY_HOST_ARENA||allocation->hostFamily==NATIVE_MEMORY_HOST_OTHER){
            host_arena_release_scoped_original(allocation->release,allocation->address,&arenaCall);ready=1;
        }else if(allocation->hostFamily==NATIVE_MEMORY_HOST_CRT){
            ((void (__cdecl*)(void*))allocation->release)((void*)(uintptr_t)allocation->address);ready=1;
        }else{
            void (JNICALL *release)(JNIEnv*,jobject,jlong)=(void (JNICALL *)(JNIEnv*,jobject,jlong))allocation->release;
            release(env,allocation->allocator,(jlong)allocation->address);ready=!native_original.ExceptionCheck(env);
        }
        returned=1;
    } __finally {
        EnterCriticalSection(&allocation->gate);AcquireSRWLockExclusive(&native_memory_records);
        if(returned&&ready){if(allocation->alive)native_memory_released(env,allocation);}
        else {InterlockedExchange(&allocation->releaseFailed,1);InterlockedExchange(&allocation->unknownUse,1);}
        if(heap)heap->users--;
        if(arenaProvider)arenaProvider->users--;
        InterlockedDecrement(&allocation->changing);ReleaseSRWLockExclusive(&native_memory_records);
        /* A native exception keeps propagating. Its caller will not execute
         * the normal gate release, so do not strand that gate on unwind. */
        if(!returned)LeaveCriticalSection(&allocation->gate);
    }
    return ready;
}
static int native_memory_restore_range(NativeMemoryAllocation *allocation,NativeMemoryRange *range,OwnerLink *selected){
    if(!native_object_state_restorable(allocation,range,selected))return 0;
    NativeMemoryRevision *head=range->revision,*rebuilt=NULL;NativeMemoryRevision **history=NULL;
    size_t count=0,capacity=0,oldest=0,length=(size_t)range->length;int found=0,ready=0;
    unsigned char *current=NULL,*incoming=NULL;
    for(NativeMemoryRevision *revision=head;revision;revision=revision->previous){
        if(!revision->complete||!native_memory_image_covers(revision->image,range->start,range->length))goto done;
        if(count==capacity){
            size_t next=capacity?capacity*2:16;if(next<capacity||next>SIZE_MAX/sizeof(*history))goto done;
            NativeMemoryRevision **grown=(NativeMemoryRevision**)realloc(history,next*sizeof(*history));if(!grown)goto done;history=grown;capacity=next;
        }
        history[count]=revision;if(native_memory_selected(revision->sources,selected)){oldest=count;found=1;}count++;
    }
    if(!found)goto done;
    current=(unsigned char*)malloc(length);incoming=(unsigned char*)malloc(length);if(!current||!incoming)goto done;
    NativeMemoryRevision *first=history[oldest];
    memcpy(incoming,first->image->before+(size_t)(range->start-first->image->start),length);
    rebuilt=first->previous;if(rebuilt)rebuilt->references++;
    for(size_t at=oldest+1;at>0;at--){
        NativeMemoryRevision *original=history[at-1];if(native_memory_selected(original->sources,selected))continue;
        NativeMemoryImage *image=(NativeMemoryImage*)calloc(1,sizeof(*image));if(!image)goto done;
        image->references=1;image->start=range->start;image->length=range->length;image->before=(unsigned char*)malloc(length);image->after=(unsigned char*)malloc(length);
        if(!image->before||!image->after){native_memory_image_release(image);goto done;}
        memcpy(image->before,incoming,length);memcpy(image->after,original->image->after+(size_t)(range->start-original->image->start),length);
        NativeMemoryRevision *replacement=(NativeMemoryRevision*)calloc(1,sizeof(*replacement));
        if(!replacement){native_memory_image_release(image);goto done;}
        replacement->references=1;replacement->allocation=allocation;replacement->image=image;replacement->complete=1;replacement->objectView=original->objectView;
        if(!native_owner_merge(&replacement->sources,original->sources)||!native_memory_interests(replacement)){native_memory_revision_release(replacement);goto done;}
        // The outside writer's result remains intact. Only its saved baseline
        // changes, so undoing it later cannot resurrect a retired contribution.
        replacement->previous=rebuilt;rebuilt=replacement;memcpy(incoming,image->after,length);
    }
    const unsigned char *expected=head->image->after+(size_t)(range->start-head->image->start);
    uint64_t address=allocation->address+range->start;ready=native_memory_read(address,current,length);
    if(ready&&memcmp(current,incoming,length)!=0){
        ready=memcmp(current,expected,length)==0&&host_memory_range((void*)(uintptr_t)address,length,0,1)==0;
        if(ready){
            DWORD error=GetLastError();SIZE_T written=0;host_real_write_process_memory(GetCurrentProcess(),(void*)(uintptr_t)address,incoming,length,&written);SetLastError(error);
            ready=native_memory_read(address,current,length)&&memcmp(current,incoming,length)==0;
            if(!ready)native_memory_unmapped(head->sources);
        }
    }
    if(ready){range->revision=rebuilt;rebuilt=NULL;native_memory_revision_release(head);native_object_state_restored(allocation,range);}
done:
    native_memory_revision_release(rebuilt);free(history);free(current);free(incoming);return ready;
}
static int native_buffer_io_disjoint(NativeBufferIoWrite *left,NativeBufferIoWrite *right){
    return left->write.address>=right->write.address+right->write.length||right->write.address>=left->write.address+left->write.length;
}
static int native_buffer_io_companions(JNIEnv *env,NativeBufferIoWrite *entry){
    LONG companions=0;int ready=1;AcquireSRWLockShared(&native_memory_records);
    for(NativeBufferIoWrite *other=native_buffer_io_writes;other;other=other->next)if(other->write.allocation==entry->write.allocation){
        companions++;
        if(!native_original.IsSameObject(env,entry->group,other->group)||!native_buffer_io_disjoint(entry,other)){ready=0;break;}
    }
    ReleaseSRWLockShared(&native_memory_records);return ready?(int)companions:-1;
}
JNIEXPORT jlong JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_ioBufferWrite0(JNIEnv *env,jclass type,jobject receipt,jobject action,jlong token,jlong generation,jlong address,jlong bytes,jobjectArray sources){
    jmethodID gate=native_controller?native_original.GetStaticMethodID(env,native_controller,"ioBufferWriteCaller","(Ljava/lang/Object;Ljava/lang/Object;JJJJ[Ljava/lang/Module;)Z"):NULL;
    if(!gate||!native_original.IsSameObject(env,type,native_controller)||!native_original.CallStaticBooleanMethod(env,native_controller,gate,receipt,action,token,generation,address,bytes,sources)
            ||native_original.ExceptionCheck(env)){native_refuse(env,"ACTUAL_IO_BUFFER_WRITE_REQUIRED");return 0;}
    NativeThread *state=native_thread();if(!state||bytes<=0){native_refuse(env,"IO_BUFFER_WRITE_SCOPE_UNAVAILABLE");return 0;}
    state->control++;NativeBufferIoWrite *entry=(NativeBufferIoWrite*)calloc(1,sizeof(*entry));int ready=entry!=NULL;
    if(ready){
        jmethodID groupQuery=native_original.GetStaticMethodID(env,native_controller,"ioBufferWriteGroup","(Ljava/lang/Object;)Ljava/lang/Object;");
        jobject group=groupQuery?native_original.CallStaticObjectMethod(env,native_controller,groupQuery,receipt):NULL;
        if(group){entry->group=native_original.NewGlobalRef(env,group);native_original.DeleteLocalRef(env,group);}
        entry->receipt=native_original.NewGlobalRef(env,receipt);ready=entry->receipt&&entry->group&&native_memory_sources(env,state,sources,&entry->write.sources,&entry->write.unknown,0);
    }
    NativeMemoryAllocation *allocation=NULL;
    if(ready){AcquireSRWLockShared(&native_memory_records);
        for(NativeMemoryAllocation *actual=native_memory_allocations;actual;actual=actual->next)if((jlong)(uintptr_t)actual==token){allocation=actual;break;}
        ReleaseSRWLockShared(&native_memory_records);ready=allocation!=NULL;
    }
    if(ready){
        EnterCriticalSection(&allocation->gate);
        ready=InterlockedCompareExchange(&allocation->alive,0,0)&&allocation->generation==(uint64_t)generation
                &&InterlockedCompareExchange(&allocation->managed,0,0)&&allocation->bufferAction&&native_original.IsSameObject(env,allocation->bufferAction,action)
                &&!InterlockedCompareExchange(&allocation->sealed,0,0)&&!InterlockedCompareExchange(&allocation->retiring,0,0)&&!InterlockedCompareExchange(&allocation->changing,0,0)
                &&(uint64_t)address>=allocation->address&&(uint64_t)bytes<=allocation->bytes&&(uint64_t)address-allocation->address<=allocation->bytes-(uint64_t)bytes;
        if(ready){
            entry->write.allocation=allocation;entry->write.address=(uint64_t)address;entry->write.length=(uint64_t)bytes;entry->write.generation=(uint64_t)generation;
            LONG writers=InterlockedCompareExchange(&allocation->writers,0,0);int companions=native_buffer_io_companions(env,entry);
            entry->write.uncertain=writers!=0&&(companions<0||companions!=writers);
            entry->epoch=writers&&!entry->write.uncertain?allocation->writeEpoch:++allocation->writeEpoch;
            entry->write.image=native_memory_image(&entry->write);ready=entry->write.image!=NULL;
            if(ready){
                InterlockedIncrement(&allocation->users);InterlockedIncrement(&allocation->writers);
                AcquireSRWLockExclusive(&native_memory_records);entry->next=native_buffer_io_writes;native_buffer_io_writes=entry;ReleaseSRWLockExclusive(&native_memory_records);
            }
        }
        LeaveCriticalSection(&allocation->gate);
    }
    if(!ready){
        if(entry){if(entry->receipt)native_original.DeleteGlobalRef(env,entry->receipt);if(entry->group)native_original.DeleteGlobalRef(env,entry->group);native_owner_links_free(entry->write.sources);native_memory_image_release(entry->write.image);free(entry);}
        state->control--;native_refuse(env,"IO_BUFFER_WRITE_ASSOCIATION_UNAVAILABLE");return 0;
    }
    state->control--;return (jlong)(uintptr_t)entry;
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_ioBufferWritten0(JNIEnv *env,jclass type,jobject receipt,jlong token,jlong bytes,jboolean completed){
    jmethodID gate=native_controller?native_original.GetStaticMethodID(env,native_controller,"ioBufferWrittenCaller","(Ljava/lang/Object;JJZ)Z"):NULL;
    if(!gate||!native_original.IsSameObject(env,type,native_controller)||!native_original.CallStaticBooleanMethod(env,native_controller,gate,receipt,token,bytes,completed)
            ||native_original.ExceptionCheck(env)){native_refuse(env,"ACTUAL_IO_BUFFER_WRITE_COMPLETION_REQUIRED");return;}
    NativeThread *state=native_thread();if(!state){native_refuse(env,"IO_BUFFER_COMPLETION_SCOPE_UNAVAILABLE");return;}state->control++;
    NativeBufferIoWrite *entry=NULL;AcquireSRWLockExclusive(&native_memory_records);
    for(NativeBufferIoWrite **link=&native_buffer_io_writes;*link;link=&(*link)->next)
        if((jlong)(uintptr_t)*link==token&&native_original.IsSameObject(env,(*link)->receipt,receipt)){entry=*link;*link=entry->next;break;}
    ReleaseSRWLockExclusive(&native_memory_records);
    if(!entry){state->control--;native_refuse(env,"ACTUAL_PENDING_IO_BUFFER_WRITE_REQUIRED");return;}
    NativeMemoryWrite *write=&entry->write;NativeMemoryAllocation *allocation=write->allocation;EnterCriticalSection(&allocation->gate);
    int current=InterlockedCompareExchange(&allocation->alive,0,0)&&allocation->generation==write->generation;
    int captured=current&&native_memory_read(write->address,write->image->after,(size_t)write->length);
    if(captured){
        int valid=completed==JNI_TRUE&&bytes>=0&&(uint64_t)bytes<=write->length;
        int companions=native_buffer_io_companions(env,entry);
        write->uncertain|=allocation->writeEpoch!=entry->epoch||companions<0||InterlockedCompareExchange(&allocation->writers,0,0)!=companions+1;
        int changed=memcmp(write->image->before,write->image->after,(size_t)write->length)!=0;
        if(valid){
            size_t tail=(size_t)(write->length-(uint64_t)bytes);
            if(tail&&memcmp(write->image->before+(size_t)bytes,write->image->after+(size_t)bytes,tail)!=0){write->uncertain=1;valid=0;}
        }
        if(valid)write->length=(uint64_t)bytes;
        else write->uncertain=1;
        if(write->length&&(valid||changed))native_memory_range(write);
        if(write->unknown&&(valid&&bytes>0||changed))InterlockedExchange(&allocation->unknownUse,1);
    }else{InterlockedExchange(&allocation->unknownUse,1);native_memory_unmapped(write->sources);}
    uint64_t previousEpoch=allocation->writeEpoch++;AcquireSRWLockShared(&native_memory_records);
    for(NativeBufferIoWrite *other=native_buffer_io_writes;other;other=other->next)
        if(other->write.allocation==allocation&&other->epoch==previousEpoch&&native_original.IsSameObject(env,other->group,entry->group)&&native_buffer_io_disjoint(entry,other))other->epoch=allocation->writeEpoch;
    ReleaseSRWLockShared(&native_memory_records);
    InterlockedDecrement(&allocation->writers);InterlockedDecrement(&allocation->users);LeaveCriticalSection(&allocation->gate);
    native_original.DeleteGlobalRef(env,entry->receipt);native_original.DeleteGlobalRef(env,entry->group);native_owner_links_free(write->sources);native_memory_image_release(write->image);free(entry);state->control--;
}
JNIEXPORT jlongArray JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_ioReadVector0(JNIEnv *env,jclass type,jobject operation,jlong address,jint count,jboolean windows){
    jmethodID gate=native_controller?native_original.GetStaticMethodID(env,native_controller,"ioReadVectorCaller","(Ljava/lang/Object;JIZ)Z"):NULL;
    if(!gate||!native_original.IsSameObject(env,type,native_controller)||!native_original.CallStaticBooleanMethod(env,native_controller,gate,operation,address,count,windows)
            ||native_original.ExceptionCheck(env)){native_refuse(env,"ACTUAL_IO_VECTOR_REQUIRED");return NULL;}
    if(count<0||count>INT32_MAX/2){native_refuse(env,"IO_VECTOR_LENGTH_INVALID");return NULL;}
    // WSABUF is ULONG length, aligned pointer; the synchronous JDK iovec is
    // pointer, size_t. Both layouts are captured from the original call site.
    struct NativeWsaBuffer {uint32_t length;uintptr_t address;};
    struct NativeIovec {uintptr_t address,length;};
    jlongArray result=native_original.NewLongArray(env,count*2);if(!result)return NULL;
    uint64_t stride=windows?sizeof(struct NativeWsaBuffer):sizeof(struct NativeIovec);
    if((uint64_t)count>0&&((uint64_t)address>UINT64_MAX-(uint64_t)count*stride)){native_refuse(env,"IO_VECTOR_RANGE_INVALID");return NULL;}
    for(jint i=0;i<count;i++){
        jlong values[2];int ready;
        if(windows){struct NativeWsaBuffer span={0};ready=native_memory_read((uint64_t)address+(uint64_t)i*stride,&span,sizeof(span));values[0]=(jlong)span.address;values[1]=(jlong)span.length;}
        else {struct NativeIovec span={0};ready=native_memory_read((uint64_t)address+(uint64_t)i*stride,&span,sizeof(span));values[0]=(jlong)span.address;values[1]=(jlong)span.length;}
        if(!ready||values[1]<0){native_refuse(env,"IO_VECTOR_LAYOUT_UNAVAILABLE");return NULL;}
        native_original.SetLongArrayRegion(env,result,i*2,2,values);if(native_original.ExceptionCheck(env))return NULL;
    }
    return result;
}
static int native_memory_selection(JNIEnv *env,jobjectArray modules,OwnerLink **selected){
    int ready=modules!=NULL;jsize count=modules?native_original.GetArrayLength(env,modules):0;
    AcquireSRWLockExclusive(&native_records);
    for(jsize i=0;i<count&&ready&&!native_original.ExceptionCheck(env);i++){
        jobject module=native_original.GetObjectArrayElement(env,modules,i);Owner *owner=module?native_owner(env,module):NULL;
        ready=owner&&InterlockedCompareExchange(&owner->stopped,0,0)&&native_owner_add(selected,owner);if(module)native_original.DeleteLocalRef(env,module);
    }
    ReleaseSRWLockExclusive(&native_records);return ready&&*selected&&!native_original.ExceptionCheck(env);
}
JNIEXPORT jlongArray JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_restoreBufferStorage0(JNIEnv *env,jclass type,jobject action,jlong token,jlong generation,jobjectArray modules,jlong cursor,jint budget){
    jmethodID gate=native_controller?native_original.GetStaticMethodID(env,native_controller,"bufferRestoreCaller","(Ljava/lang/Object;JJ[Ljava/lang/Module;JI)Z"):NULL;
    if(!gate||!native_original.IsSameObject(env,type,native_controller)||!native_original.CallStaticBooleanMethod(env,native_controller,gate,action,token,generation,modules,cursor,budget)
            ||native_original.ExceptionCheck(env)){native_refuse(env,"ACTUAL_BUFFER_SOURCE_RESTORE_REQUIRED");return NULL;}
    NativeThread *state=native_thread();if(!state||!action||!token||cursor<0||budget<0){native_refuse(env,"BUFFER_SOURCE_RESTORE_SCOPE_UNAVAILABLE");return NULL;}
    state->control++;OwnerLink *selected=NULL;jlong values[4]={cursor,1,0,0};
    if(!native_memory_selection(env,modules,&selected)){native_owner_links_free(selected);state->control--;native_refuse(env,"ACTUAL_STOPPED_MEMORY_SOURCES_REQUIRED");return NULL;}
    NativeMemoryAllocation *allocation=NULL;
    AcquireSRWLockShared(&native_memory_records);
    for(NativeMemoryAllocation *entry=native_memory_allocations;entry;entry=entry->next)if((jlong)(uintptr_t)entry==token){allocation=entry;break;}
    ReleaseSRWLockShared(&native_memory_records);
    if(allocation&&TryEnterCriticalSection(&allocation->gate)){
        int associated=InterlockedCompareExchange(&allocation->alive,0,0)&&allocation->generation==(uint64_t)generation
                &&InterlockedCompareExchange(&allocation->managed,0,0)&&allocation->bufferAction&&native_original.IsSameObject(env,allocation->bufferAction,action);
        if(associated){
            AcquireSRWLockExclusive(&native_memory_records);
            int ready=!InterlockedCompareExchange(&allocation->sealed,0,0)&&!InterlockedCompareExchange(&allocation->users,0,0)
                    &&!InterlockedCompareExchange(&allocation->writers,0,0)&&!InterlockedCompareExchange(&allocation->reading,0,0)
                    &&!InterlockedCompareExchange(&allocation->changing,0,0)&&!InterlockedCompareExchange(&allocation->restoring,0,0)
                    &&(!budget||!InterlockedCompareExchange(&allocation->retiring,0,0))
                    &&!InterlockedCompareExchange(&allocation->unknownUse,0,0)&&!InterlockedCompareExchange(&allocation->releaseFailed,0,0)
                    &&!native_memory_buffer_aliases(env,allocation);
            if(ready){InterlockedExchange(&allocation->restoring,1);if(!budget)InterlockedExchange(&allocation->retiring,1);}ReleaseSRWLockExclusive(&native_memory_records);
            if(ready){
                if(!budget){
                    // The actual Java retirement will run this original cleaner.
                    // Keep new byte/view admissions out until that exact free.
                    values[0]=0;values[1]=0;values[3]=1;
                }else{
                int remaining=budget>256?256:budget;NativeMemoryRange **link=&allocation->ranges;
                while(*link&&(*link)->start+(*link)->length<=(uint64_t)cursor)link=&(*link)->next;
                while(*link&&remaining--){
                    NativeMemoryRange *range=*link;values[0]=(jlong)(range->start+range->length);
                    if(native_memory_targets(range->revision,selected)&&native_memory_restore_range(allocation,range,selected))values[2]+=(jlong)range->length;
                    if(!range->revision){*link=range->next;free(range);}else link=&range->next;
                }
                if(!*link){values[0]=0;values[3]=1;values[1]=0;
                    // A previous chunk can retain an unresolved mixed revision.
                    // Completion is the whole allocation's remaining interests.
                    for(NativeMemoryInterest *interest=allocation->interests;interest;interest=interest->next)
                        if(InterlockedCompareExchange(&interest->revisions,0,0)>0)
                            for(OwnerLink *target=selected;target;target=target->next)if(target->owner==interest->owner){values[1]=1;break;}
                    if(!values[1]){
                        AcquireSRWLockExclusive(&native_memory_records);
                        // Retain creation provenance, but record that the real
                        // Java owner preserved this allocation after restoring
                        // these stopped contributors. It is not leaked private storage.
                        if(!native_owner_merge(&allocation->preserved,selected))values[1]=1;
                        ReleaseSRWLockExclusive(&native_memory_records);
                    }
                }
                }
                InterlockedExchange(&allocation->restoring,0);
            }
        }else{values[0]=0;values[3]=1;}
        LeaveCriticalSection(&allocation->gate);
    }else if(!allocation){values[0]=0;values[3]=1;}
    native_owner_links_free(selected);jlongArray result=native_original.ExceptionCheck(env)?NULL:native_original.NewLongArray(env,4);
    if(result)native_original.SetLongArrayRegion(env,result,0,4,values);state->control--;return result;
}
JNIEXPORT jlongArray JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_restoreMemory0(JNIEnv *env,jclass type,jobjectArray modules,jlong cursor,jlong offset,jint budget){
    jmethodID gate=native_controller?native_original.GetStaticMethodID(env,native_controller,"memoryRecoveryCaller","()Z"):NULL;
    if(!native_controller||!native_original.IsSameObject(env,type,native_controller)||!gate
            ||!native_original.CallStaticBooleanMethod(env,native_controller,gate)||native_original.ExceptionCheck(env)){
        native_refuse(env,"ACTUAL_NATIVE_MEMORY_RECOVERY_REQUIRED");return NULL;
    }
    NativeThread *state=native_thread();if(!state||!modules||offset<0||budget<=0){native_refuse(env,"NATIVE_MEMORY_RECOVERY_SCOPE_UNAVAILABLE");return NULL;}
    state->control++;OwnerLink *selected=NULL;int ready=native_memory_selection(env,modules,&selected);
    if(!ready||!selected||native_original.ExceptionCheck(env)){native_owner_links_free(selected);state->control--;native_refuse(env,"ACTUAL_STOPPED_MEMORY_SOURCES_REQUIRED");return NULL;}
    jlong values[5]={0,0,0,0,0};int remaining=budget>256?256:budget;
    AcquireSRWLockShared(&native_memory_records);NativeMemoryAllocation *allocation=native_memory_allocations;
    if(cursor){while(allocation&&(jlong)(uintptr_t)allocation!=cursor)allocation=allocation->next;if(!allocation){allocation=native_memory_allocations;offset=0;}}
    ReleaseSRWLockShared(&native_memory_records);
    while(allocation){
        if(!remaining){values[0]=(jlong)(uintptr_t)allocation;values[1]=offset;break;}
        if(!TryEnterCriticalSection(&allocation->gate)){
            AcquireSRWLockShared(&native_memory_records);if(native_memory_busy_targets(allocation,selected))values[2]++;ReleaseSRWLockShared(&native_memory_records);remaining--;
        }else{
            if(!offset&&InterlockedCompareExchange(&allocation->alive,0,0)&&native_memory_retire_allocation(env,allocation,selected))values[3]+=(jlong)allocation->bytes;
            if(native_original.ExceptionCheck(env)){LeaveCriticalSection(&allocation->gate);break;}
            if(InterlockedCompareExchange(&allocation->alive,0,0)){
                if(native_memory_created_targets(allocation,selected))values[2]++;
                NativeMemoryRange **link=&allocation->ranges;
                while(*link&&(*link)->start+(*link)->length<=(uint64_t)offset)link=&(*link)->next;
                if(!*link)remaining--;
                while(*link&&remaining){
                    NativeMemoryRange *range=*link;uint64_t next=range->start+range->length;remaining--;
                    if(native_memory_targets(range->revision,selected)){
                        if(!InterlockedCompareExchange(&allocation->managed,0,0)&&!InterlockedCompareExchange(&allocation->releaseFailed,0,0)
                                &&!native_memory_buffer_aliases(env,allocation)
                                &&!InterlockedCompareExchange(&allocation->users,0,0)&&!InterlockedCompareExchange(&allocation->writers,0,0)&&!InterlockedCompareExchange(&allocation->reading,0,0)
                                &&!InterlockedCompareExchange(&allocation->changing,0,0)&&native_memory_restore_range(allocation,range,selected))values[3]+=(jlong)range->length;
                        if(native_memory_targets(range->revision,selected))values[2]++;
                    }
                    if(!range->revision){*link=range->next;free(range);}else link=&range->next;
                    offset=(jlong)next;
                }
                if(*link){values[0]=(jlong)(uintptr_t)allocation;values[1]=offset;LeaveCriticalSection(&allocation->gate);break;}
            }else remaining--;
            LeaveCriticalSection(&allocation->gate);
        }
        native_memory_reap_references(env,allocation);allocation=allocation->next;offset=0;
    }
    if(!allocation)values[4]=1;
    native_owner_links_free(selected);jlongArray result=native_original.ExceptionCheck(env)?NULL:native_original.NewLongArray(env,5);if(result)native_original.SetLongArrayRegion(env,result,0,5,values);state->control--;return result;
}
JNIEXPORT jlongArray JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_memoryState0(JNIEnv *env,jclass type,jobject module){
    if(!native_controller||!native_original.IsSameObject(env,type,native_controller)||!native_original.CallStaticBooleanMethod(env,native_controller,native_control_gate)){native_refuse(env,"NATIVE_CONTROL_AGENT_REQUIRED");return NULL;}
    jlong values[5]={0,0,0,0,0};AcquireSRWLockShared(&native_memory_records);
    for(NativeMemoryAllocation *allocation=native_memory_allocations;allocation;allocation=allocation->next){
        if(!InterlockedCompareExchange(&allocation->alive,0,0))continue;
        int created=native_memory_creator_matches(env,allocation,module);if(created)values[0]++;
        if(created||native_memory_interest_matches(env,allocation,module))values[2]+=InterlockedCompareExchange(&allocation->users,0,0)+InterlockedCompareExchange(&allocation->changing,0,0);
    }
    values[3]=native_memory_unknown&&native_owner_matches(env,native_memory_unknown,module);
    NativeMemoryAllocation *head=native_memory_allocations;ReleaseSRWLockShared(&native_memory_records);
    // Range lists are held by the allocation's actual mutation gate. Do not
    // take that gate while holding the global allocation lookup lock.
    for(NativeMemoryAllocation *allocation=head;allocation;allocation=allocation->next){
        if(!TryEnterCriticalSection(&allocation->gate)){
            AcquireSRWLockShared(&native_memory_records);if(InterlockedCompareExchange(&allocation->alive,0,0)&&native_memory_interest_matches(env,allocation,module))values[1]++;ReleaseSRWLockShared(&native_memory_records);continue;
        }
        if(InterlockedCompareExchange(&allocation->alive,0,0)){
            int related=native_memory_creator_matches(env,allocation,module);
            for(NativeMemoryRange *range=allocation->ranges;range;range=range->next)if(native_memory_revision_matches(env,range->revision,module)){values[1]++;related=1;}
            int shared=InterlockedCompareExchange(&allocation->unknownReaders,0,0)!=0||InterlockedCompareExchange(&allocation->unknownUse,0,0)!=0;
            for(OwnerLink *reader=allocation->readers;!shared&&reader;reader=reader->next)if(!module||!native_original.IsSameObject(env,reader->owner->module,module))shared=1;
            if(related&&shared)values[4]++;
        }
        LeaveCriticalSection(&allocation->gate);native_memory_reap_references(env,allocation);
    }
    jlongArray result=native_original.NewLongArray(env,5);if(result)native_original.SetLongArrayRegion(env,result,0,5,values);return result;
}
