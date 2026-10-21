/* Observed Win32 TLS/FLS imports and GetProcAddress receipts only. Compiler
 * static TLS, direct TEB accesses, saved outside exports and CPU pointer flows
 * are not intercepted. A slot stores an opaque value, never an owned object.
 *
 * https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-tlsgetvalue
 * https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-tlsgetvalue2
 * https://learn.microsoft.com/en-us/windows/win32/api/fibersapi/nf-fibersapi-flsfree
 * https://learn.microsoft.com/en-us/windows/win32/api/winnt/nc-winnt-pfls_callback_function
 */
typedef DWORD (WINAPI *HostTlsAlloc)(void);
typedef BOOL (WINAPI *HostTlsSet)(DWORD,LPVOID);
typedef LPVOID (WINAPI *HostTlsGet)(DWORD);
typedef BOOL (WINAPI *HostTlsFree)(DWORD);
typedef VOID (WINAPI *HostFlsCallback)(PVOID);
typedef DWORD (WINAPI *HostFlsAlloc)(HostFlsCallback);

typedef struct HostTlsLease {
    OwnerLink *owners;jobjectArray strong;HostLibraryLoad *load;
    struct HostThreadWorker *worker;void *tooling,*tls;
    LONG references;int unknown,complete,releasing;
    struct HostTlsLease *next;
} HostTlsLease;
typedef struct HostTlsLeaseLink {HostTlsLease *lease;struct HostTlsLeaseLink *next;} HostTlsLeaseLink;
typedef struct HostTlsSources {OwnerLink *owners;HostTlsLeaseLink *leases;int unknown;} HostTlsSources;
typedef struct HostTlsSlot HostTlsSlot;
typedef struct HostTlsActivation {HostTlsSlot *slot;struct HostTlsActivation *previous;} HostTlsActivation;
typedef struct HostTlsThread {
    uint64_t generation;HANDLE monitor;HostTlsSources observed;
    HostTlsActivation *activation;int ended,gap,releasing,reaped;
    struct HostTlsValue *values;
    struct HostTlsThread *next;
} HostTlsThread;
typedef struct HostTlsFiber {
    uint64_t generation;HostTlsThread *home;HostTlsSources observed;
    void *fiber;int actualFiber,ended,gap,reaped;
    struct HostTlsValue *values;
    struct HostTlsFiber *next;
} HostTlsFiber;
typedef struct HostTlsValue {
    HostTlsThread *thread;HostTlsFiber *fiber;HostTlsSources sources;
    HostTlsSlot *slot;struct HostTlsValue *contextNext;
    uintptr_t value;int initialized,uncertain,closed;
    struct HostTlsValue *next;
} HostTlsValue;
typedef struct HostTlsContribution {HostTlsSources sources;uintptr_t value;int valueReceipt;struct HostTlsContribution *next;} HostTlsContribution;
typedef struct HostTlsOperation {HostTlsSources *sources;uintptr_t value;int freeing,setting;struct HostTlsOperation *next;} HostTlsOperation;
struct HostTlsSlot {
    uint64_t generation;DWORD index;int fls,observed,live,uncertain,gap,cleanupGap,reaped;
    LONG busy,callbacks,borrowers;int closing;
    HostTlsSources allocation,aggregate;HostTlsValue *values;
    HostTlsContribution *contributions;HostTlsOperation *operations;
    HostFlsCallback callback;void *adapter;HMODULE provider,pin;void *providerAnchor;
    RUNTIME_FUNCTION unwind;struct HostTlsSlot *next;
};
typedef struct HostTlsBorrow {HostTlsSources sources;HostLibraryLoad *load;struct HostThreadWorker *worker;void *tooling;} HostTlsBorrow;
static SRWLOCK host_tls_records=SRWLOCK_INIT;
static HostTlsSlot *host_tls_slots;
static HostTlsThread *host_tls_threads;
static HostTlsFiber *host_tls_fibers;
static HostTlsLease *host_tls_leases;
static uint64_t host_tls_generation;
static DWORD host_tls_thread_key=TLS_OUT_OF_INDEXES,host_tls_fiber_key=FLS_OUT_OF_INDEXES;
static volatile LONG host_tls_ready;
static void WINAPI host_tls_fiber_end(PVOID);
static int host_tls_source_borrow(NativeThread*,OwnerLink*,void**);
static void host_tls_source_drop(void*);

static int host_is_tls(int kind){
    return kind>=HOST_TLS_ALLOC&&kind<=HOST_FLS_FREE||kind==HOST_TLS_GET2;
}
static uint64_t host_tls_failure(int kind,DWORD error,DWORD incoming){
    SetLastError(kind==HOST_TLS_GET2?incoming:error);
    return kind==HOST_TLS_ALLOC||kind==HOST_FLS_ALLOC?(uint64_t)(DWORD)TLS_OUT_OF_INDEXES:0;
}
static void host_tls_refused(int kind,DWORD index,DWORD error,const char *site,int restricted,int unknown,OwnerLink *owners,NativeThread *state,void *caller){
    static volatile LONG readFailures;
    if(kind==HOST_TLS_GET||kind==HOST_TLS_GET2||kind==HOST_FLS_GET){
        ULONG count=(ULONG)InterlockedIncrement(&readFailures);
        if(count>8&&(count&(count-1)))return;
    }
    unsigned scopes=state?(state->heapCallback?1:0)|(state->toolingRestricted?2:0)|(state->vmDetaching?4:0)
            |(state->vmThreadEnding?8:0)|(state->hostUnloadImage?16:0)|(state->hostRelease?32:0)
            |(state->hostLoad&&state->hostLoad->calling?64:0):0;
    fprintf(stderr,"NATIVE_TLS_REFUSED:kind=%d:index=%lu:error=%lu:site=%s:restricted=%d:unknown=%d:producer=%d:scopes=%u:depth=%u:thread=%lu:caller=%p\n",
            kind,(unsigned long)index,(unsigned long)error,site,restricted,unknown,native_has_producer(owners),scopes,state?state->depth:0,
            (unsigned long)GetCurrentThreadId(),caller);
}
static int host_tls_restricted(NativeThread *state){
    return state&&(state->heapCallback||state->toolingRestricted||state->vmDetaching||state->vmThreadEnding
            ||state->hostUnloadImage||state->hostRelease||state->hostLoad&&state->hostLoad->calling);
}
static int host_tls_lock(int restricted){
    return restricted?TryAcquireSRWLockExclusive(&host_tls_records):(AcquireSRWLockExclusive(&host_tls_records),1);
}
static int host_tls_cover(OwnerLink *retained,OwnerLink *actual){
    for(;actual;actual=actual->next){OwnerLink *p=retained;while(p&&p->owner!=actual->owner)p=p->next;if(!p)return 0;}return 1;
}
/* All source-container operations below hold host_tls_records. They only copy
 * already captured links/leases; none enter a loader, VM, or Host directory. */
static void host_tls_sources_clear(HostTlsSources *sources){
    native_owner_links_free(sources->owners);sources->owners=NULL;
    HostTlsLeaseLink *p=sources->leases;sources->leases=NULL;
    while(p){HostTlsLeaseLink *next=p->next;p->lease->references--;free(p);p=next;}sources->unknown=0;
}
static int host_tls_sources_lease(HostTlsSources *sources,HostTlsLease *lease){
    if(!lease)return 1;
    for(HostTlsLeaseLink *p=sources->leases;p;p=p->next)if(p->lease==lease)return 1;
    HostTlsLeaseLink *p=(HostTlsLeaseLink*)calloc(1,sizeof(*p));if(!p){sources->unknown=1;return 0;}
    p->lease=lease;p->next=sources->leases;sources->leases=p;lease->references++;return 1;
}
static int host_tls_sources_add(HostTlsSources *sources,OwnerLink *owners,HostTlsLease *lease,int unknown){
    int ready=native_owner_merge(&sources->owners,owners)&&host_tls_sources_lease(sources,lease);
    sources->unknown|=unknown||!ready||lease&&(!lease->complete||lease->unknown);return ready;
}
static int host_tls_sources_merge(HostTlsSources *to,const HostTlsSources *from){
    int ready=native_owner_merge(&to->owners,from->owners);to->unknown|=from->unknown||!ready;
    for(HostTlsLeaseLink *p=from->leases;p;p=p->next)if(!host_tls_sources_lease(to,p->lease))ready=0;
    return ready;
}
static int host_tls_slot_sources(HostTlsSources *to,const HostTlsSlot *slot,int operations){
    int ready=host_tls_sources_merge(to,&slot->aggregate);
    for(HostTlsContribution *p=slot->contributions;p;p=p->next)
        if(!host_tls_sources_merge(to,&p->sources))ready=0;
    if(operations)for(HostTlsOperation *p=slot->operations;p;p=p->next)
        if(p->sources&&!host_tls_sources_merge(to,p->sources))ready=0;
    return ready;
}
static int host_tls_prepare(void){
    LONG ready=InterlockedCompareExchange(&host_tls_ready,0,0);if(ready==1)return 1;
    if(ready||InterlockedCompareExchange(&host_tls_ready,2,0)){SetLastError(ERROR_BUSY);return 0;}
    DWORD incoming=GetLastError();DWORD threadKey=TlsAlloc(),fiberKey=FLS_OUT_OF_INDEXES;
    if(threadKey!=TLS_OUT_OF_INDEXES)fiberKey=FlsAlloc(host_tls_fiber_end);
    DWORD error=GetLastError();
    if(threadKey==TLS_OUT_OF_INDEXES||fiberKey==FLS_OUT_OF_INDEXES){
        /* These unexposed private indices have never contained a record. */
        if(threadKey!=TLS_OUT_OF_INDEXES)TlsFree(threadKey);
        InterlockedExchange(&host_tls_ready,0);SetLastError(error?error:ERROR_NOT_ENOUGH_MEMORY);return 0;
    }
    host_tls_thread_key=threadKey;host_tls_fiber_key=fiberKey;InterlockedExchange(&host_tls_ready,1);
    SetLastError(incoming);return 1;
}
static HostTlsThread *host_tls_thread_current(void){
    return InterlockedCompareExchange(&host_tls_ready,0,0)==1?(HostTlsThread*)TlsGetValue(host_tls_thread_key):NULL;
}
static HostTlsFiber *host_tls_fiber_current(void){
    return InterlockedCompareExchange(&host_tls_ready,0,0)==1?(HostTlsFiber*)FlsGetValue(host_tls_fiber_key):NULL;
}
static HostTlsThread *host_tls_thread_get(int restricted){
    HostTlsThread *thread=host_tls_thread_current();if(thread)return thread;
    thread=(HostTlsThread*)calloc(1,sizeof(*thread));if(!thread){SetLastError(ERROR_NOT_ENOUGH_MEMORY);return NULL;}
    /* The actual kernel-thread handle is the terminal witness. Thread IDs and
     * a JVM detach/ThreadEnd never retire a TLS value or its read receipt. */
    int locked=restricted?TryAcquireSRWLockExclusive(&host_records):(AcquireSRWLockExclusive(&host_records),1);
    if(!locked){free(thread);SetLastError(ERROR_BUSY);return NULL;}
    HANDLE monitor=NULL;BOOL copied=host_real_duplicate(GetCurrentProcess(),GetCurrentThread(),GetCurrentProcess(),
            &monitor,SYNCHRONIZE,FALSE,0);DWORD error=copied?0:GetLastError();
    if(copied&&!TlsSetValue(host_tls_thread_key,thread)){error=GetLastError();host_real_close(monitor);copied=FALSE;}
    if(copied){AcquireSRWLockExclusive(&host_tls_records);thread->generation=++host_tls_generation;
        thread->monitor=monitor;thread->next=host_tls_threads;host_tls_threads=thread;ReleaseSRWLockExclusive(&host_tls_records);}
    ReleaseSRWLockExclusive(&host_records);
    if(!copied){free(thread);SetLastError(error?error:ERROR_INVALID_STATE);return NULL;}
    native_thread_worker_wake();return thread;
}
static HostTlsFiber *host_tls_fiber_get(HostTlsThread *thread,int restricted){
    HostTlsFiber *fiber=host_tls_fiber_current();BOOL actual=IsThreadAFiber();void *identity=actual?GetCurrentFiber():NULL;
    if(fiber){
        if(!host_tls_lock(restricted)){SetLastError(ERROR_BUSY);return NULL;}
        if(actual){fiber->actualFiber=1;fiber->fiber=identity;}
        if(fiber->home!=thread)fiber->gap=1;
        ReleaseSRWLockExclusive(&host_tls_records);
        /* The sentinel can precede the user's still-running callbacks. The
         * same private FLS value still identifies that actual storage; ending
         * it does not invalidate an otherwise legal original FlsGet/Set. */
        return fiber;
    }
    fiber=(HostTlsFiber*)calloc(1,sizeof(*fiber));if(!fiber){SetLastError(ERROR_NOT_ENOUGH_MEMORY);return NULL;}
    fiber->home=thread;fiber->actualFiber=actual;fiber->fiber=identity;
    /* An internal FLS value travels with the actual FLS storage, including a
     * migrated fiber. The OS callback ends that incarnation; a recycled fiber
     * address alone can never select this record for a replacement fiber. */
    if(!FlsSetValue(host_tls_fiber_key,fiber)){DWORD error=GetLastError();free(fiber);SetLastError(error);return NULL;}
    AcquireSRWLockExclusive(&host_tls_records);fiber->generation=++host_tls_generation;
    fiber->next=host_tls_fibers;host_tls_fibers=fiber;ReleaseSRWLockExclusive(&host_tls_records);return fiber;
}
static void WINAPI host_tls_fiber_end(PVOID data){
    DWORD incoming=GetLastError();HostTlsFiber *fiber=(HostTlsFiber*)data;
    HostTlsThread *current=host_tls_thread_current();
    if(fiber){AcquireSRWLockExclusive(&host_tls_records);fiber->ended=1;
        /* The sentinel's position in the FLS callback order is unspecified.
         * It is not a barrier for the other application callbacks. A fiber
         * deleted from another thread, or a genuine fiber, remains a gap. */
        if(fiber->actualFiber||current!=fiber->home)fiber->gap=1;
        if(current&&!host_tls_sources_merge(&current->observed,&fiber->observed)){current->gap=1;current->observed.unknown=1;}
        ReleaseSRWLockExclusive(&host_tls_records);native_thread_worker_wake();}
    SetLastError(incoming);
}
/* Called with native_records already held on several source-capture paths.
 * No reverse acquisition of native_records/host_imports/host_records occurs. */
static int host_tls_capture(NativeThread *state,OwnerLink **owners,int *unknown){
    DWORD incoming=GetLastError();HostTlsThread *thread=host_tls_thread_current();HostTlsFiber *fiber=host_tls_fiber_current();
    int ready=1;
    if(host_tls_restricted(state)){if(!TryAcquireSRWLockShared(&host_tls_records)){if(unknown)*unknown=1;SetLastError(incoming);return 0;}}
    else AcquireSRWLockShared(&host_tls_records);
    /* A TLS value's opaque data history is not an unknown caller of every
     * later JNI/OS operation on this thread. Keep all observed contributors,
     * but obtain call origins from the live Java/native/binding scopes.
     * The value gap remains in observed.unknown, TLS reports, callback scopes
     * and the complete-lease/retirement checks; it grants no private lease. */
    if(thread){ready=native_owner_merge(owners,thread->observed.owners);if(unknown)*unknown|=thread->ended||thread->gap;}
    if(fiber){ready=ready&&native_owner_merge(owners,fiber->observed.owners);if(unknown)*unknown|=fiber->ended||fiber->gap;}
    ReleaseSRWLockShared(&host_tls_records);SetLastError(incoming);return ready;
}
static int host_tls_source_borrow(NativeThread *state,OwnerLink *owners,void **token){
    if(token)*token=NULL;if(!state||!token||!owners)return 0;
    DWORD incoming=GetLastError();HostTlsThread *thread=host_tls_thread_current();HostTlsFiber *fiber=host_tls_fiber_current();
    if(!thread){SetLastError(incoming);return 0;}HostTlsBorrow *borrow=(HostTlsBorrow*)calloc(1,sizeof(*borrow));
    if(!borrow){SetLastError(incoming);return 0;}
    if(!TryAcquireSRWLockExclusive(&host_tls_records)){free(borrow);SetLastError(incoming);return 0;}
    int ready=host_tls_sources_merge(&borrow->sources,&thread->observed);
    if(fiber)ready=ready&&host_tls_sources_merge(&borrow->sources,&fiber->observed);
    if(thread->activation)ready=ready&&host_tls_slot_sources(&borrow->sources,thread->activation->slot,1);
    ready=ready&&!thread->ended&&!borrow->sources.unknown&&borrow->sources.leases&&!native_owner_stopped(owners);
    OwnerLink *missing=NULL;
    if(ready)for(OwnerLink *p=owners;p;p=p->next){OwnerLink *actual=borrow->sources.owners;
        while(actual&&actual->owner!=p->owner)actual=actual->next;
        if(!actual&&!native_owner_add(&missing,p->owner)){ready=0;break;}}
    ReleaseSRWLockExclusive(&host_tls_records);
    /* A B worker/load can carry a TLS receipt from A. Retain A's actual read
     * lease and borrow the remaining B sources from a real executing parent;
     * requiring one parent to own both would silently lose the A contribution. */
    if(ready&&missing){HostLibraryLoad *load=state->hostLoad;
        if(!load)load=host_module_release_sources(state);
        if(!load)load=host_thread_worker_child(state,NULL);
        if(TryAcquireSRWLockExclusive(&host_imports)){
            int held=load&&!load->retired&&!load->releasing&&!load->unknown&&load->leaseComplete&&host_tls_cover(load->owners,missing);
            if(held){InterlockedIncrement(&load->borrowers);borrow->load=load;}
            ReleaseSRWLockExclusive(&host_imports);
        }
        if(!borrow->load&&!host_thread_worker_load_borrow(state,missing,&borrow->worker)
                &&!native_tooling_load_borrow(state,missing,&borrow->tooling))ready=0;
        if(ready&&!native_owner_merge(&borrow->sources.owners,missing))ready=0;
    }
    native_owner_links_free(missing);
    if(ready)*token=borrow;else {
        AcquireSRWLockExclusive(&host_tls_records);host_tls_sources_clear(&borrow->sources);ReleaseSRWLockExclusive(&host_tls_records);
        if(borrow->load){AcquireSRWLockExclusive(&host_imports);InterlockedDecrement(&borrow->load->borrowers);ReleaseSRWLockExclusive(&host_imports);}
        if(borrow->worker)host_thread_worker_load_drop(borrow->worker);
        if(borrow->tooling)native_thread_worker_heap_drop(borrow->tooling);free(borrow);
    }
    SetLastError(incoming);return ready;
}
static void host_tls_source_drop(void *token){
    if(!token)return;DWORD incoming=GetLastError();HostTlsBorrow *borrow=(HostTlsBorrow*)token;
    AcquireSRWLockExclusive(&host_tls_records);host_tls_sources_clear(&borrow->sources);ReleaseSRWLockExclusive(&host_tls_records);
    if(borrow->load){AcquireSRWLockExclusive(&host_imports);InterlockedDecrement(&borrow->load->borrowers);ReleaseSRWLockExclusive(&host_imports);}
    if(borrow->worker)host_thread_worker_load_drop(borrow->worker);
    if(borrow->tooling)native_thread_worker_heap_drop(borrow->tooling);
    free(borrow);native_thread_worker_wake();SetLastError(incoming);
}
static HostTlsLease *host_tls_lease_prepare(NativeThread *state,OwnerLink *owners,int unknown,int restricted){
    HostTlsLease *lease=(HostTlsLease*)calloc(1,sizeof(*lease));if(!lease){SetLastError(ERROR_NOT_ENOUGH_MEMORY);return NULL;}
    if(!native_owner_merge(&lease->owners,owners)){native_owner_links_free(lease->owners);free(lease);SetLastError(ERROR_NOT_ENOUGH_MEMORY);return NULL;}
    lease->unknown=unknown;lease->references=1;
    JNIEnv *env=host_memory_environment(restricted);
    if(env){jobjectArray sources=native_tooling_sources(env,owners,unknown);
        lease->complete=!unknown&&host_module_sources_complete(env,sources);
        if(sources&&!native_original.ExceptionCheck(env))lease->strong=(jobjectArray)native_original.NewGlobalRef(env,sources);
        if(sources)native_original.DeleteLocalRef(env,sources);
        if(!lease->strong||native_original.ExceptionCheck(env))lease->complete=0;
    }else if(!unknown){
        HostLibraryLoad *load=state?state->hostLoad:NULL;
        if(!load&&state)load=host_module_release_sources(state);
        if(!load&&state)load=host_thread_worker_child(state,NULL);
        int locked=restricted?TryAcquireSRWLockExclusive(&host_imports):(AcquireSRWLockExclusive(&host_imports),1);
        if(locked){int ready=load&&!load->retired&&!load->releasing&&!load->unknown&&load->leaseComplete
                    &&host_tls_cover(load->owners,owners);
            if(ready){InterlockedIncrement(&load->borrowers);lease->load=load;lease->complete=1;}
            ReleaseSRWLockExclusive(&host_imports);}
        if(!lease->complete&&host_thread_worker_load_borrow(state,owners,&lease->worker))lease->complete=1;
        if(!lease->complete&&native_tooling_load_borrow(state,owners,&lease->tooling))lease->complete=1;
        if(!lease->complete&&host_tls_source_borrow(state,owners,&lease->tls))lease->complete=1;
    }
    /* Retain even a partial lease as an explicit gap, rather than forgetting
     * an observed source because its weak Java identity later disappears. */
    AcquireSRWLockExclusive(&host_tls_records);lease->next=host_tls_leases;host_tls_leases=lease;
    ReleaseSRWLockExclusive(&host_tls_records);return lease;
}
static int host_tls_callback_sources(NativeThread *state,void *callback,OwnerLink **owners,int *unknown,int restricted){
    if(!restricted)return host_capture_caller(state,callback,owners);
    if(!state||!TryAcquireSRWLockShared(&host_imports)){SetLastError(ERROR_BUSY);return 0;}
    if(!TryAcquireSRWLockShared(&native_records)){ReleaseSRWLockShared(&host_imports);SetLastError(ERROR_BUSY);return 0;}
    DWORD error=ERROR_INVALID_DATA;int ready=host_callback_caller_locked(state,callback,owners,unknown,&error);
    ReleaseSRWLockShared(&native_records);ReleaseSRWLockShared(&host_imports);if(!ready)SetLastError(error);return ready;
}
static HostTlsSlot *host_tls_slot_find(int fls,DWORD index){
    for(HostTlsSlot *slot=host_tls_slots;slot;slot=slot->next)if(slot->live&&slot->fls==fls&&slot->index==index)return slot;return NULL;
}
static HostTlsValue *host_tls_value_find(HostTlsSlot *slot,HostTlsThread *thread,HostTlsFiber *fiber){
    for(HostTlsValue *value=slot->values;value;value=value->next)
        if(slot->fls?value->fiber==fiber:value->thread==thread)return value;return NULL;
}
static uint64_t host_tls_original(HostImport *entry,const uintptr_t *a,void *callback){
    switch(entry->kind){
        case HOST_TLS_ALLOC:return ((HostTlsAlloc)entry->original)();
        case HOST_FLS_ALLOC:return ((HostFlsAlloc)entry->original)((HostFlsCallback)callback);
        case HOST_TLS_SET:case HOST_FLS_SET:return ((HostTlsSet)entry->original)((DWORD)a[0],(LPVOID)a[1]);
        case HOST_TLS_GET:case HOST_TLS_GET2:case HOST_FLS_GET:return (uintptr_t)((HostTlsGet)entry->original)((DWORD)a[0]);
        case HOST_TLS_FREE:case HOST_FLS_FREE:return ((HostTlsFree)entry->original)((DWORD)a[0]);
    }SetLastError(ERROR_INVALID_FUNCTION);return 0;
}
static void WINAPI host_tls_callback(HostTlsSlot *slot,PVOID data){
    DWORD incoming=GetLastError(),outgoing=incoming;HostTlsSources capturedSources={0};OwnerLink *owners=NULL;int unknown=0,entered=0,returned=0,allowed=0;
    NativeThread *state=NULL;HostTlsThread *thread=NULL;HostTlsActivation activation={slot,NULL};NativeToolingScope scope={0};
    AcquireSRWLockExclusive(&host_tls_records);slot->callbacks++;
    int captured=host_tls_slot_sources(&capturedSources,slot,1);
    owners=capturedSources.owners;unknown=capturedSources.unknown||slot->uncertain||!captured;
    /* FlsFree can invoke this callback for a different fiber. Data-pointer
     * equality is not a fiber identity or a right to release that object. */
    /* The complete observed writer superset is intentional. No pointer-based
     * choice narrows those sources, and the callback proves no object free. */
    int receipt=0;
    for(HostTlsContribution *p=slot->contributions;p;p=p->next)
        if(p->valueReceipt&&p->value==(uintptr_t)data){receipt=1;break;}
    if(!receipt)for(HostTlsOperation *p=slot->operations;p;p=p->next)
        if(p->setting&&p->value==(uintptr_t)data&&p->sources){receipt=1;break;}
    if(!receipt){unknown=1;slot->gap=1;}
    allowed=captured&&slot->callback&&!native_owner_stopped(owners);
    if(!allowed)slot->cleanupGap=slot->gap=1;
    ReleaseSRWLockExclusive(&host_tls_records);
    __try {
        if(allowed){state=native_thread();thread=host_tls_thread_current();
            if(!thread)thread=host_tls_thread_get(1);
            if(!state||!thread){AcquireSRWLockExclusive(&host_tls_records);slot->cleanupGap=slot->gap=1;
                ReleaseSRWLockExclusive(&host_tls_records);}
            else {
                scope=(NativeToolingScope){owners,NULL,unknown,state->depth,state->tooling};
                native_thread_callback_enter(state);state->tooling=&scope;state->toolingRestricted++;entered=1;
                AcquireSRWLockExclusive(&host_tls_records);activation.previous=thread->activation;thread->activation=&activation;
                ReleaseSRWLockExclusive(&host_tls_records);
                if(native_owner_stopped(owners)){AcquireSRWLockExclusive(&host_tls_records);slot->cleanupGap=slot->gap=1;
                    ReleaseSRWLockExclusive(&host_tls_records);allowed=0;}
                else {SetLastError(incoming);slot->callback(data);outgoing=GetLastError();returned=1;}
            }
        }
    } __finally {
        DWORD interrupted=GetLastError();
        if(entered){AcquireSRWLockExclusive(&host_tls_records);thread->activation=activation.previous;
            ReleaseSRWLockExclusive(&host_tls_records);state->toolingRestricted--;state->tooling=scope.previous;
            native_thread_callback_leave(state);}
        AcquireSRWLockExclusive(&host_tls_records);slot->callbacks--;
        if(allowed&&!returned)slot->cleanupGap=slot->gap=slot->uncertain=1;
        host_tls_sources_clear(&capturedSources);ReleaseSRWLockExclusive(&host_tls_records);native_thread_worker_wake();
        SetLastError(returned?outgoing:allowed?interrupted:incoming);
    }
}
static void *host_tls_callback_entry(HostTlsSlot *slot){
    unsigned capacity=4096,frame=40;unsigned char *page=(unsigned char*)VirtualAlloc(NULL,capacity,MEM_COMMIT|MEM_RESERVE,PAGE_READWRITE);
    if(!page)return NULL;NativeCode code={page,page};
    nb(&code,0x48);nb(&code,0x81);nb(&code,0xec);nw(&code,frame);
    /* Original data arrives in RCX; adapter(slot,data) uses RCX,RDX. */
    nb(&code,0x48);nb(&code,0x89);nb(&code,0xca);ni(&code,1,(uintptr_t)slot);nc(&code,(void*)host_tls_callback);
    nb(&code,0x48);nb(&code,0x81);nb(&code,0xc4);nw(&code,frame);nb(&code,0xc3);
    slot->unwind.BeginAddress=0;slot->unwind.EndAddress=(DWORD)(code.at-page);slot->unwind.UnwindData=capacity-16;
    unsigned char *unwind=page+capacity-16;unwind[0]=1;unwind[1]=7;unwind[2]=2;unwind[3]=0;unwind[4]=7;unwind[5]=1;
    unsigned short scaled=(unsigned short)(frame/8);memcpy(unwind+6,&scaled,2);DWORD protection;
    if(!host_real_virtual_protect(page,capacity,PAGE_EXECUTE_READ,&protection)||!RtlAddFunctionTable(&slot->unwind,1,(DWORD64)(uintptr_t)page)){
        host_real_virtual_free(page,0,MEM_RELEASE);return NULL;}
    FlushInstructionCache(GetCurrentProcess(),page,capacity);return page;
}
static uint64_t host_native_tls(HostImport *entry,void *caller,uintptr_t *arguments){
    DWORD incoming=GetLastError(),error=0;int kind=entry->kind;
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    int restricted=host_tls_restricted(state),allocating=kind==HOST_TLS_ALLOC||kind==HOST_FLS_ALLOC;
    int fls=kind>=HOST_FLS_ALLOC&&kind<=HOST_FLS_FREE,setting=kind==HOST_TLS_SET||kind==HOST_FLS_SET;
    int getting=kind==HOST_TLS_GET||kind==HOST_TLS_GET2||kind==HOST_FLS_GET,freeing=kind==HOST_TLS_FREE||kind==HOST_FLS_FREE;
    DWORD index=allocating?TLS_OUT_OF_INDEXES:(DWORD)arguments[0];OwnerLink *owners=NULL;int unknown=0;
    if(!allocating&&(!fls&&(native_tls!=TLS_OUT_OF_INDEXES&&index==native_tls
            ||host_tls_thread_key!=TLS_OUT_OF_INDEXES&&index==host_tls_thread_key)
            ||fls&&host_tls_fiber_key!=FLS_OUT_OF_INDEXES&&index==host_tls_fiber_key))
        return host_tls_failure(kind,ERROR_ACCESS_DENIED,incoming);
    if(!restricted)entry=host_consumed_entry(entry,state,caller);
    if(!host_entry_sources_query(entry,state,caller,&owners,NULL,1,&unknown)){
        error=GetLastError();host_tls_refused(kind,index,error,"source_query",restricted,unknown,owners,state,caller);
        native_owner_links_free(owners);return host_tls_failure(kind,error,incoming);}
    HostTlsSlot *slot=NULL;HostTlsValue *value=NULL;HostTlsLease *lease=NULL;HostTlsThread *thread=NULL;HostTlsFiber *fiber=NULL;
    HostTlsSources prepared={0},aggregation={0},exposure={0};HostTlsContribution *contribution=NULL;
    HostTlsOperation operation={0};uint64_t result=allocating?(uint64_t)(DWORD)TLS_OUT_OF_INDEXES:0;
    int reserved=0,called=0,returned=0,success=0,published=0;const char *failure="preparation";
    __try {
        if(native_owner_stopped(owners)){error=ERROR_ACCESS_DENIED;failure="stopped_source";}
        else if(unknown&&(restricted||native_has_producer(owners))){error=ERROR_INVALID_DATA;failure="unknown_source";}
        if(!error&&InterlockedCompareExchange(&host_tls_ready,0,0)!=1){
            if(restricted)error=ERROR_BUSY;else if(!host_tls_prepare())error=GetLastError();}
        if(!error&&!allocating&&(!fls&&(native_tls!=TLS_OUT_OF_INDEXES&&index==native_tls
                ||host_tls_thread_key!=TLS_OUT_OF_INDEXES&&index==host_tls_thread_key)
                ||fls&&host_tls_fiber_key!=FLS_OUT_OF_INDEXES&&index==host_tls_fiber_key))error=ERROR_ACCESS_DENIED;
        if(!error&&(setting||getting)){
            thread=host_tls_thread_get(restricted);if(!thread)error=GetLastError();
            if(!error&&fls){fiber=host_tls_fiber_get(thread,restricted);if(!fiber)error=GetLastError();}
        }
        if(!error&&(allocating||setting||fls&&freeing)){
            if(kind==HOST_FLS_ALLOC&&arguments[0]){
                slot=(HostTlsSlot*)calloc(1,sizeof(*slot));if(!slot)error=ERROR_NOT_ENOUGH_MEMORY;
                else {slot->fls=1;slot->index=TLS_OUT_OF_INDEXES;slot->callback=(HostFlsCallback)arguments[0];
                    if(state&&state->heapCallback){
                        if(!native_thread_worker_heap_borrow(state,owners,(void*)slot->callback,&slot->providerAnchor,&slot->provider))error=GetLastError();
                    }else if(!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,(LPCSTR)slot->callback,&slot->pin))error=GetLastError();
                    else slot->provider=slot->pin;
                    if(!error&&!host_tls_callback_sources(state,(void*)slot->callback,&owners,&unknown,restricted))error=GetLastError();
                    if(!error){slot->adapter=host_tls_callback_entry(slot);if(!slot->adapter)error=GetLastError();}
                }
            }
            if(!error){lease=host_tls_lease_prepare(state,owners,unknown,restricted);if(!lease)error=GetLastError();
                // A known platform-only callback does not acquire a private
                // Mod lease by setting its own TLS value. Keep its partial
                // lease/report, but do not turn missing private retention into
                // a spurious OS failure. Real Mod sources and unknown callback
                // origins still require the original complete-source gate.
                else if(!lease->complete&&(native_has_producer(owners)||restricted&&unknown)){error=ERROR_INVALID_DATA;failure="source_lease";}}
        }
        if(!error){
            if(!host_tls_lock(restricted))error=ERROR_BUSY;
            else {
                if(allocating){
                    if(!slot)slot=(HostTlsSlot*)calloc(1,sizeof(*slot));
                    if(!slot)error=ERROR_NOT_ENOUGH_MEMORY;
                    else {slot->fls=fls;slot->observed=1;slot->index=TLS_OUT_OF_INDEXES;
                        slot->generation=++host_tls_generation;slot->busy=1;reserved=1;
                        if(!host_tls_sources_add(&slot->allocation,owners,lease,unknown)
                                ||!host_tls_sources_merge(&slot->aggregate,&slot->allocation))error=ERROR_NOT_ENOUGH_MEMORY;
                    }
                }else {
                    slot=host_tls_slot_find(fls,index);
                    HostTlsSlot *callbackSlot=thread&&thread->activation?thread->activation->slot:NULL;
                    if(!slot&&callbackSlot&&callbackSlot->fls==fls&&callbackSlot->index==index)slot=callbackSlot;
                    if(!slot){slot=(HostTlsSlot*)calloc(1,sizeof(*slot));
                        if(!slot)error=ERROR_NOT_ENOUGH_MEMORY;
                        else {slot->fls=fls;slot->index=index;slot->generation=++host_tls_generation;
                            slot->live=1;slot->uncertain=slot->gap=1;slot->next=host_tls_slots;host_tls_slots=slot;published=1;}}
                    else published=1;
                    int callbackUse=callbackSlot==slot&&(setting||getting);
                    /* An index is process-wide, its values are thread/fiber-local.
                     * Concurrent Get/Set on distinct contexts do not conflict.
                     * busy retains every in-flight operation for Free/retirement;
                     * closing still excludes new users, except the actual FLS
                     * callback already admitted by this slot's Free operation. */
                    if(!error&&(slot->closing&&!callbackUse||freeing&&(slot->busy||slot->callbacks))){
                        error=ERROR_BUSY;failure="slot_closing";}
                    if(!error){slot->busy++;if(freeing)slot->closing++;reserved=1;
                        operation.freeing=freeing;operation.setting=setting;operation.value=setting?arguments[1]:0;
                        operation.next=slot->operations;slot->operations=&operation;
                        if(setting||getting){value=host_tls_value_find(slot,thread,fiber);
                            if(!value){value=(HostTlsValue*)calloc(1,sizeof(*value));
                                if(value){
                                    value->thread=thread;value->fiber=fiber;value->slot=slot;
                                    value->next=slot->values;slot->values=value;
                                    if(fls){value->contextNext=fiber->values;fiber->values=value;}
                                    else {value->contextNext=thread->values;thread->values=value;}
                                }
                                else error=ERROR_NOT_ENOUGH_MEMORY;}
                        }
                        if(!error&&setting){
                            if(!host_tls_sources_merge(&prepared,&slot->allocation)||!host_tls_sources_merge(&prepared,&value->sources)
                                    ||!host_tls_sources_add(&prepared,owners,lease,unknown||!slot->observed))error=ERROR_NOT_ENOUGH_MEMORY;
                            else if(native_owner_stopped(prepared.owners))error=ERROR_ACCESS_DENIED;
                            /* Retain an in-flight writer before FlsSetValue:
                             * another OS thread may concurrently enter a real
                             * FLS callback. Failed stores remain only a gap. */
                            if(!error&&!host_tls_sources_merge(&aggregation,&prepared))error=ERROR_NOT_ENOUGH_MEMORY;
                            if(!error){contribution=(HostTlsContribution*)calloc(1,sizeof(*contribution));
                                if(!contribution)error=ERROR_NOT_ENOUGH_MEMORY;else operation.sources=&prepared;}
                        }
                        if(!error&&fls&&freeing){
                            if(!host_tls_sources_add(&prepared,owners,lease,unknown)
                                    ||!host_tls_sources_merge(&aggregation,&prepared))error=ERROR_NOT_ENOUGH_MEMORY;
                            if(!error){contribution=(HostTlsContribution*)calloc(1,sizeof(*contribution));
                                if(!contribution)error=ERROR_NOT_ENOUGH_MEMORY;else operation.sources=&prepared;}
                        }
                        if(!error&&getting){
                            HostTlsSources *observed=fls?&fiber->observed:&thread->observed;
                            if(!host_tls_sources_merge(&exposure,observed)||!host_tls_sources_merge(&exposure,&slot->allocation)
                                    ||!host_tls_sources_merge(&exposure,&value->sources))error=ERROR_NOT_ENOUGH_MEMORY;
                            if(!slot->observed||slot->uncertain)exposure.unknown=1;
                            if(!error&&native_owner_stopped(exposure.owners))error=ERROR_ACCESS_DENIED;
                        }
                    }
                }
                ReleaseSRWLockExclusive(&host_tls_records);
            }
        }
        if(!error&&(native_owner_stopped(owners)||setting&&native_owner_stopped(prepared.owners)
                ||getting&&native_owner_stopped(exposure.owners)))error=ERROR_ACCESS_DENIED;
        if(!error){SetLastError(incoming);called=1;result=host_tls_original(entry,arguments,slot?slot->adapter:NULL);
            error=GetLastError();returned=1;
            success=allocating?(DWORD)result!=TLS_OUT_OF_INDEXES:getting?
                    (kind==HOST_TLS_GET2?result!=0||slot->observed:
                     kind==HOST_FLS_GET?result!=0||error==ERROR_SUCCESS||slot->observed&&!slot->uncertain:
                     result!=0||error==ERROR_SUCCESS):result!=0;
        }
    } __finally {
        DWORD interrupted=GetLastError();AcquireSRWLockExclusive(&host_tls_records);
        if(slot&&reserved){
            if(called&&!returned){slot->gap=slot->uncertain=1;if(value)value->uncertain=1;
                if(setting&&value){HostTlsSources before=value->sources;value->sources=prepared;prepared=before;value->sources.unknown=1;}
                if(contribution)aggregation.unknown=1;}
            if(allocating){
                if(returned&&success){slot->index=(DWORD)result;slot->live=1;
                    HostTlsSlot *old=host_tls_slot_find(fls,slot->index);
                    if(old){old->live=0;if(!old->closing)old->uncertain=old->gap=1;}
                }else if(called&&!returned)slot->gap=slot->uncertain=1;
                if(!published){slot->next=host_tls_slots;host_tls_slots=slot;published=1;}
            }else if(returned&&success){
                if(setting){HostTlsSources before=value->sources;value->sources=prepared;prepared=before;
                    value->value=arguments[1];value->initialized=1;value->closed=0;
                }else if(getting){
                    if(!value->initialized&&result||value->initialized&&value->value!=(uintptr_t)result){
                        value->uncertain=slot->gap=1;
                        if(result){exposure.unknown=1;value->sources.unknown=1;}}
                    /* A read receipt survives Set(NULL), SlotFree, detach and
                     * subsequent index reuse. It is retired only with the real
                     * OS thread / actual FLS-context lifecycle. CPU value flow
                     * after returning the opaque pointer is still unobserved. */
                    HostTlsSources *observed=fls?&fiber->observed:&thread->observed;
                    HostTlsSources before=*observed;*observed=exposure;exposure=before;
                }else if(freeing){slot->live=0;
                    for(HostTlsValue *p=slot->values;p;p=p->next)p->closed=1;
                    if(slot->callback){
                        /* Public FlsFree has no documented barrier for a
                         * callback already fetched by another OS thread. Keep
                         * the callable tombstone's real provider/source lease. */
                        slot->gap=1;
                    }
                }
            }
            if(contribution&&called&&(!returned||success)){
                contribution->sources=aggregation;aggregation=(HostTlsSources){0};
                contribution->valueReceipt=setting&&returned&&success;contribution->value=setting?arguments[1]:0;
                contribution->next=slot->contributions;slot->contributions=contribution;contribution=NULL;
            }
            if(!allocating&&!slot->observed&&(!called||returned&&!success))slot->live=0;
            if(!allocating){HostTlsOperation **link=&slot->operations;
                while(*link&&*link!=&operation)link=&(*link)->next;if(*link==&operation)*link=operation.next;
                if(freeing)slot->closing--;}
            slot->busy--;
        }
        if(slot&&!published){
            /* Failed callback preparation may already own a provider pin or
             * a strong source. The daemon returns those in normal context. */
            if(!host_tls_sources_add(&slot->aggregate,owners,lease,unknown||!lease))slot->gap=1;
            slot->generation=++host_tls_generation;slot->index=TLS_OUT_OF_INDEXES;
            slot->next=host_tls_slots;host_tls_slots=slot;published=1;
        }
        host_tls_sources_clear(&prepared);host_tls_sources_clear(&aggregation);host_tls_sources_clear(&exposure);
        if(contribution)free(contribution);
        if(lease)lease->references--;ReleaseSRWLockExclusive(&host_tls_records);
        if(!called)host_tls_refused(kind,index,error?error:ERROR_INVALID_DATA,failure,restricted,unknown,owners,state,caller);
        native_owner_links_free(owners);
        // Reads retain the old observation and introduce no zero-reference
        // lease. New contexts already wake the daemon at publication, and its
        // existing monitor timeout observes actual kernel-thread termination.
        if(!getting)native_thread_worker_wake();
        SetLastError(kind==HOST_TLS_GET2?incoming:returned?error:error?error:interrupted);
    }
    return called?result:host_tls_failure(kind,error?error:ERROR_INVALID_DATA,incoming);
}

/* host_records is held by the handle consumer and by monitor close below. */
static int host_tls_internal_handle(HANDLE handle){
    int found=0;AcquireSRWLockShared(&host_tls_records);
    for(HostTlsThread *thread=host_tls_threads;thread;thread=thread->next)
        if(thread->monitor==handle){found=1;break;}
    ReleaseSRWLockShared(&host_tls_records);return found;
}
static int host_tls_pending(void){
    int pending=0;AcquireSRWLockShared(&host_tls_records);
    for(HostTlsThread *thread=host_tls_threads;thread&&!pending;thread=thread->next)pending=thread->monitor&&!thread->gap;
    for(HostTlsLease *lease=host_tls_leases;lease&&!pending;lease=lease->next)pending=!lease->references&&!lease->releasing;
    ReleaseSRWLockShared(&host_tls_records);return pending;
}
static void host_tls_reap(JNIEnv **environment){
    if(!environment||!*environment)return;JNIEnv *env=*environment;
    /* No new timer or enumerator: the existing retirement daemon observes only
     * our retained actual thread handles and queued zero-reference leases. */
    AcquireSRWLockExclusive(&host_records);AcquireSRWLockExclusive(&host_tls_records);
    __try {
    for(HostTlsThread *thread=host_tls_threads;thread;thread=thread->next){
        if(thread->releasing||thread->reaped)continue;
        if(!thread->ended){
            if(!thread->monitor)continue;DWORD status=WaitForSingleObject(thread->monitor,0);
            if(status!=WAIT_OBJECT_0){if(status==WAIT_FAILED)thread->gap=1;continue;}
            thread->ended=1;
        }
        host_tls_sources_clear(&thread->observed);
        int pending=0;
        for(HostTlsValue *value=thread->values;value;value=value->contextNext){
            if(value->closed&&!value->sources.owners&&!value->sources.leases&&!value->sources.unknown)continue;
            if(value->slot->busy){pending=1;continue;}
            value->closed=1;host_tls_sources_clear(&value->sources);
        }
        if(thread->monitor){if(host_real_close(thread->monitor))thread->monitor=NULL;else thread->gap=1;}
        if(!pending&&!thread->monitor)thread->reaped=1;
    }
    for(HostTlsFiber *fiber=host_tls_fibers;fiber;fiber=fiber->next){
        if(fiber->reaped||!fiber->home->ended)continue;
        // Migrated/opaque fibers retain the same explicit lifecycle gap and
        // source leases. An ended home thread grants no fiber disposal.
        if(!fiber->ended||fiber->actualFiber||fiber->gap){fiber->gap=1;continue;}
        host_tls_sources_clear(&fiber->observed);int pending=0;
        for(HostTlsValue *value=fiber->values;value;value=value->contextNext){
            if(value->closed&&!value->sources.owners&&!value->sources.leases&&!value->sources.unknown)continue;
            if(value->slot->busy||value->slot->callbacks){pending=1;continue;}
            value->closed=1;host_tls_sources_clear(&value->sources);
        }
        if(!pending)fiber->reaped=1;
    }
    for(HostTlsSlot *slot=host_tls_slots;slot;slot=slot->next){
        if(slot->reaped||slot->live||slot->busy||slot->callbacks||slot->borrowers||slot->uncertain||slot->cleanupGap)continue;
        if(slot->callback&&slot->index!=TLS_OUT_OF_INDEXES||slot->pin||slot->providerAnchor)continue;
        host_tls_sources_clear(&slot->allocation);host_tls_sources_clear(&slot->aggregate);
        HostTlsContribution *p=slot->contributions;slot->contributions=NULL;
        while(p){HostTlsContribution *next=p->next;host_tls_sources_clear(&p->sources);free(p);p=next;}
        slot->reaped=1;
    }
    } __finally {ReleaseSRWLockExclusive(&host_tls_records);ReleaseSRWLockExclusive(&host_records);}
    /* Callback adapter pages and their RUNTIME_FUNCTION remain callable
     * tombstones. A failed, unpublished allocation has no OS callback receipt. */
    for(;;){HostTlsSlot *release=NULL;HMODULE pin=NULL;void *anchor=NULL;
        AcquireSRWLockExclusive(&host_tls_records);
        for(HostTlsSlot *slot=host_tls_slots;slot;slot=slot->next)
            if(!slot->live&&!slot->busy&&!slot->callbacks&&!slot->uncertain&&slot->index==TLS_OUT_OF_INDEXES&&(slot->pin||slot->providerAnchor)){
                release=slot;pin=slot->pin;anchor=slot->providerAnchor;slot->pin=NULL;slot->providerAnchor=NULL;break;}
        ReleaseSRWLockExclusive(&host_tls_records);if(!release)break;
        NativeThread *daemon=native_thread();int freed=0,pinReturned=0;
        if(daemon){NativeToolingScope scope={release->aggregate.owners,NULL,release->aggregate.unknown,daemon->depth,daemon->tooling};
            native_thread_callback_enter(daemon);daemon->tooling=&scope;
            __try {freed=!pin||host_real_free_library(pin);pinReturned=1;}
            __finally {
                if(!pinReturned){AcquireSRWLockExclusive(&host_tls_records);release->pin=pin;release->providerAnchor=anchor;
                    release->gap=1;ReleaseSRWLockExclusive(&host_tls_records);}
                daemon->tooling=scope.previous;native_thread_callback_leave(daemon);
            }
        }
        if(!freed){AcquireSRWLockExclusive(&host_tls_records);release->pin=pin;release->providerAnchor=anchor;
            release->gap=1;ReleaseSRWLockExclusive(&host_tls_records);break;}
        if(anchor)native_thread_worker_heap_drop(anchor);
        AcquireSRWLockExclusive(&host_tls_records);host_tls_sources_clear(&release->allocation);host_tls_sources_clear(&release->aggregate);
        ReleaseSRWLockExclusive(&host_tls_records);
        if(!native_tooling_retirement_jni(environment))break;env=*environment;
    }
    for(;;){HostTlsLease *lease=NULL;
        AcquireSRWLockExclusive(&host_tls_records);
        HostTlsLease **link=&host_tls_leases;
        while(*link&&((*link)->references||(*link)->releasing))link=&(*link)->next;
        if(*link){lease=*link;*link=lease->next;lease->releasing=1;}
        ReleaseSRWLockExclusive(&host_tls_records);if(!lease)break;
        if(lease->strong)native_original.DeleteGlobalRef(env,lease->strong);
        if(lease->load){AcquireSRWLockExclusive(&host_imports);InterlockedDecrement(&lease->load->borrowers);ReleaseSRWLockExclusive(&host_imports);}
        if(lease->worker)host_thread_worker_load_drop(lease->worker);
        if(lease->tooling)native_thread_worker_heap_drop(lease->tooling);
        if(lease->tls)host_tls_source_drop(lease->tls);
        native_owner_links_free(lease->owners);free(lease);
    }
}
static void host_tls_state(JNIEnv *env,jobject module,jlong *values){
    AcquireSRWLockShared(&host_tls_records);
    for(HostTlsSlot *slot=host_tls_slots;slot;slot=slot->next){
        int matches=native_owner_matches(env,slot->aggregate.owners,module),retained=0;
        for(HostTlsContribution *p=slot->contributions;p;p=p->next){
            matches|=native_owner_matches(env,p->sources.owners,module);retained|=p->sources.leases!=NULL;}
        if(!matches)for(HostTlsValue *value=slot->values;value;value=value->next)
            if(native_owner_matches(env,value->sources.owners,module)){matches=1;break;}
        if(!matches)continue;
        if(slot->live)values[32]++;
        values[34]+=slot->busy+slot->callbacks;
        if(slot->gap||slot->uncertain||slot->cleanupGap)values[35]++;
        if(slot->pin||slot->providerAnchor||slot->allocation.leases||slot->aggregate.leases||retained)values[36]++;
        for(HostTlsValue *value=slot->values;value;value=value->next)
            if((value->sources.leases||value->sources.owners)&&native_owner_matches(env,value->sources.owners,module)){
                values[33]++;if(value->uncertain)values[35]++;}
    }
    for(HostTlsThread *thread=host_tls_threads;thread;thread=thread->next)
        if(native_owner_matches(env,thread->observed.owners,module)){
            if(thread->observed.leases)values[36]++;if(thread->gap||thread->observed.unknown)values[35]++;}
    for(HostTlsFiber *fiber=host_tls_fibers;fiber;fiber=fiber->next)
        if(native_owner_matches(env,fiber->observed.owners,module)){
            if(fiber->observed.leases)values[36]++;if(fiber->gap||fiber->observed.unknown)values[35]++;}
    ReleaseSRWLockShared(&host_tls_records);
}
