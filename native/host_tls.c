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
    LONG references;int unknown,complete,releasing,queued;
    struct HostTlsLease *next;
} HostTlsLease;
typedef struct HostTlsLeaseLink {HostTlsLease *lease;LONG references;struct HostTlsLeaseLink *next;} HostTlsLeaseLink;
typedef struct HostTlsSources {OwnerLink *owners;HostTlsLeaseLink *leases;int unknown,partial;} HostTlsSources;
typedef struct HostTlsSlot HostTlsSlot;
typedef struct HostTlsActivation {HostTlsSlot *slot;struct HostTlsActivation *previous;} HostTlsActivation;
typedef struct HostTlsThread {
    uint64_t generation,observedEpoch;HANDLE monitor;HostTlsSources observed;
    HostTlsActivation *activation;int ended,gap,releasing,reaped,queued,polling;
    struct HostTlsValue *values,*activeValues,*valuesTail;SIZE_T valueCount;
    struct HostTlsThread *next,*activeNext,*monitorNext,**monitorLink;
} HostTlsThread;
typedef struct HostTlsFiber {
    uint64_t generation,observedEpoch;HostTlsThread *home;HostTlsSources observed;
    void *fiber;int actualFiber,ended,gap,reaped,queued;
    struct HostTlsValue *values,*activeValues,*valuesTail;SIZE_T valueCount;
    struct HostTlsFiber *next,*activeNext;
} HostTlsFiber;
typedef struct HostTlsValue {
    HostTlsThread *thread;HostTlsFiber *fiber;HostTlsSources sources;
    HostTlsSlot *slot;struct HostTlsValue *contextNext,*workNext;
    uint64_t revision,retainedRevision,retainedEpoch;
    uintptr_t value;int initialized,uncertain,closed;
    struct HostTlsValue *next;
} HostTlsValue;
typedef struct HostTlsContribution {HostTlsSources sources;uintptr_t value;int valueReceipt;struct HostTlsContribution *next;} HostTlsContribution;
typedef struct HostTlsOperation {HostTlsSources *sources;uintptr_t value;int freeing,setting;struct HostTlsOperation *next;} HostTlsOperation;
struct HostTlsSlot {
    uint64_t generation;DWORD index;int fls,observed,live,uncertain,gap,cleanupGap,reaped,queued,releasing;
    LONG busy,callbacks,borrowers;int closing;
    HostTlsSources allocation,aggregate;HostTlsValue *values;
    HostTlsContribution *contributions;HostTlsOperation *operations;
    HostFlsCallback callback;void *adapter;HMODULE provider,pin;void *providerAnchor;
    RUNTIME_FUNCTION unwind;struct HostTlsSlot *next,*activeNext,*liveNext,**liveLink;
};
typedef struct HostTlsBorrow {HostTlsSources sources;HostLibraryLoad *load;struct HostThreadWorker *worker;void *tooling;} HostTlsBorrow;
static SRWLOCK host_tls_records=SRWLOCK_INIT;
static HostTlsSlot *host_tls_slots;
static HostTlsThread *host_tls_threads;
static HostTlsFiber *host_tls_fibers;
static HostTlsThread *host_tls_active_threads,*host_tls_threads_tail,*host_tls_monitors;
static HostTlsFiber *host_tls_active_fibers,*host_tls_fibers_tail;
static HostTlsSlot *host_tls_active_slots,*host_tls_slots_tail;
static HostTlsSlot *host_tls_live_slots;
static HostTlsLease *host_tls_retired_leases,*host_tls_retired_tail;
static SIZE_T host_tls_thread_count,host_tls_fiber_count,host_tls_slot_count,host_tls_polling;
static SIZE_T host_tls_thread_scan,host_tls_fiber_scan,host_tls_slot_scan;
static int host_tls_cleanup_retry;
static uint64_t host_tls_generation;
static DWORD host_tls_thread_key=TLS_OUT_OF_INDEXES,host_tls_fiber_key=FLS_OUT_OF_INDEXES;
static volatile LONG host_tls_ready;
static void WINAPI host_tls_fiber_end(PVOID);
static HostTlsThread *host_tls_thread_current(void);
static HostTlsFiber *host_tls_fiber_current(void);
static HostTlsSlot *host_tls_slot_find(int,DWORD);
static int host_tls_source_borrow(NativeThread*,OwnerLink*,HostLibraryLoad*,void**);
static void host_tls_source_drop(void*);

static int host_is_tls(int kind){
    return kind>=HOST_TLS_ALLOC&&kind<=HOST_FLS_FREE||kind==HOST_TLS_GET2;
}
static uint64_t host_tls_failure(int kind,DWORD error,DWORD incoming){
    SetLastError(kind==HOST_TLS_GET2?incoming:error);
    return kind==HOST_TLS_ALLOC||kind==HOST_FLS_ALLOC?(uint64_t)(DWORD)TLS_OUT_OF_INDEXES:0;
}
typedef struct HostTlsRefusal {
    const char *site;void *caller;HostImage *image;HostLibraryLoad *selected;
    HostLibraryLoad *callbackLoad;
    uint64_t generation;DWORD thread,index,error;int kind,restricted,unknown,producer;
    unsigned scopes,depth;int entryUnknown,returned,loadUnknown,selectedUnknown,leaseComplete;
    int entryQueryUnknown,entryQueryProducer,callbackSeen,callbackCaptured,callbackUnknown,callbackProducer;
    NativeSourceRoots roots;
} HostTlsRefusal;
static HostTlsRefusal host_tls_refusal(HostImport *entry,int kind,DWORD index,DWORD error,const char *site,
        int restricted,int unknown,OwnerLink *owners,NativeThread *state,void *caller,
        HostLibraryLoad *selected,const NativeSourceRoots *roots){
    HostTlsRefusal r={0};r.site=site;r.caller=caller;r.image=entry->image;r.selected=selected;
    r.thread=GetCurrentThreadId();r.index=index;r.error=error;r.kind=kind;r.restricted=restricted;
    r.unknown=r.entryQueryUnknown=unknown;r.producer=r.entryQueryProducer=native_has_producer(owners);r.leaseComplete=-1;
    if(roots)r.roots=*roots;
    r.entryUnknown=entry->unknown;r.returned=entry->returned;r.loadUnknown=entry->load?entry->load->unknown:0;
    r.selectedUnknown=selected?selected->unknown:0;
    if(state){r.depth=state->depth;r.scopes=(state->heapCallback?1:0)|(state->toolingRestricted?2:0)|(state->vmDetaching?4:0)
            |(state->vmThreadEnding?8:0)|(state->hostUnloadImage?16:0)|(state->hostRelease?32:0)
            |(state->hostLoad&&state->hostLoad->calling?64:0);
    }
    return r;
}
static void host_tls_refused(const HostTlsRefusal *r){
    /* Bounded failure-only deduplication. Reads and early lease failures cannot
     * consume a later TLS_SET/unknown_source diagnostic. No VM entry here. */
    static SRWLOCK gate=SRWLOCK_INIT;static HostTlsRefusal seen[128];static unsigned next;
    int duplicate=0;AcquireSRWLockExclusive(&gate);
    for(unsigned i=0;i<128;i++){HostTlsRefusal *p=&seen[i];
        if(p->site==r->site&&p->kind==r->kind&&p->index==r->index&&p->generation==r->generation
                &&p->caller==r->caller&&p->image==r->image&&p->selected==r->selected&&p->error==r->error
                &&p->unknown==r->unknown&&p->restricted==r->restricted&&p->entryUnknown==r->entryUnknown
                &&p->loadUnknown==r->loadUnknown&&p->selectedUnknown==r->selectedUnknown
                &&!memcmp(&p->roots,&r->roots,sizeof(r->roots))&&p->leaseComplete==r->leaseComplete
                &&p->entryQueryUnknown==r->entryQueryUnknown&&p->entryQueryProducer==r->entryQueryProducer
                &&p->callbackSeen==r->callbackSeen&&p->callbackCaptured==r->callbackCaptured
                &&p->callbackUnknown==r->callbackUnknown&&p->callbackProducer==r->callbackProducer
                &&p->callbackLoad==r->callbackLoad){duplicate=1;break;}}
    if(!duplicate)seen[next++%128]=*r;ReleaseSRWLockExclusive(&gate);if(duplicate)return;
    fprintf(stderr,"NATIVE_TLS_REFUSED:kind=%d:index=%lu:generation=%llu:error=%lu:site=%s:thread=%lu:caller=%p:image=%p:selected=%p:restricted=%d:unknown=%d:producer=%d:scopes=%u:depth=%u:entry_unknown=%d:entry_producer=%d:import_unknown=%d:returned=%d:load_unknown=%d:selected_unknown=%d:tls=%d:registration_unknown=%d:binding_unknown=%d:tooling_unknown=%d:code_unknown=%d:java_unknown=%d:callback_seen=%d:callback_captured=%d:callback_unknown=%d:callback_producer=%d:callback_load=%p:effective_unknown=%d:effective_producer=%d:lease_complete=%d\n",
            r->kind,(unsigned long)r->index,(unsigned long long)r->generation,(unsigned long)r->error,r->site,
            (unsigned long)r->thread,r->caller,r->image,r->selected,r->restricted,r->unknown,r->producer,r->scopes,r->depth,
            r->entryQueryUnknown,r->entryQueryProducer,r->entryUnknown,r->returned,r->loadUnknown,r->selectedUnknown,
            r->roots.tls,r->roots.registration,r->roots.binding,r->roots.tooling,r->roots.code,r->roots.process,
            r->callbackSeen,r->callbackCaptured,r->callbackUnknown,r->callbackProducer,r->callbackLoad,r->unknown,r->producer,r->leaseComplete);
}
static int host_tls_restricted(NativeThread *state){
    return state&&(state->heapCallback||state->toolingRestricted||state->vmDetaching||state->vmThreadEnding
            ||state->hostUnloadImage||state->hostRelease||state->hostLoad&&state->hostLoad->calling);
}
static int host_tls_lock(int restricted){
    /* This directory never enters the VM or loader while held. Contention is
     * not an OS TLS failure: returning BUSY during CRT/FLS teardown can leave
     * its destruction sentinel installed for the next OS callback. */
    (void)restricted;AcquireSRWLockExclusive(&host_tls_records);return 1;
}
static int host_tls_cover(OwnerLink *retained,OwnerLink *actual){
    for(;actual;actual=actual->next){OwnerLink *p=retained;while(p&&p->owner!=actual->owner)p=p->next;if(!p)return 0;}return 1;
}
/* All source-container operations below hold host_tls_records. They only copy
 * already captured links/leases; none enter a loader, VM, or Host directory. */
static void host_tls_lease_drop(HostTlsLease *lease){
    if(!--lease->references&&!lease->queued&&!lease->releasing){
        lease->queued=1;lease->next=NULL;
        if(host_tls_retired_tail)host_tls_retired_tail->next=lease;else host_tls_retired_leases=lease;
        host_tls_retired_tail=lease;
        native_thread_worker_wake();
    }
}
static void host_tls_links_drop(HostTlsLeaseLink *p){
    while(p&&!--p->references){HostTlsLeaseLink *next=p->next;host_tls_lease_drop(p->lease);free(p);p=next;}
}
static void host_tls_sources_clear(HostTlsSources *sources){
    native_owner_links_free(sources->owners);sources->owners=NULL;
    host_tls_links_drop(sources->leases);sources->leases=NULL;sources->unknown=sources->partial=0;
}
static int host_tls_sources_new_lease(HostTlsSources *sources,HostTlsLease *lease){
    if(!lease)return 1;
    HostTlsLeaseLink *p=(HostTlsLeaseLink*)calloc(1,sizeof(*p));if(!p){sources->unknown=1;return 0;}
    p->lease=lease;p->references=1;p->next=sources->leases;sources->leases=p;lease->references++;return 1;
}
static int host_tls_sources_add(HostTlsSources *sources,OwnerLink *owners,HostTlsLease *lease,int unknown){
    // Only the fresh lease of this admitted API invocation is added here.
    int ready=native_owner_merge(&sources->owners,owners)&&host_tls_sources_new_lease(sources,lease);
    /* Missing lifetime retention is reported through the actual partial lease,
     * not by inventing an unidentified actor. */
    sources->unknown|=unknown||!ready||lease&&lease->unknown;sources->partial|=lease&&!lease->complete;return ready;
}
static int host_tls_sources_merge(HostTlsSources *to,const HostTlsSources *from){
    int ready=native_owner_merge(&to->owners,from->owners);to->unknown|=from->unknown||!ready;to->partial|=from->partial;
    if(!ready)return 0;
    if(!from->leases)return ready;
    /* Immutable tails share their real lifetime references. Related successive
     * values/read observations no longer duplicate the complete old chain. */
    if(to->leases==from->leases)return ready;
    for(HostTlsLeaseLink *p=from->leases;p;p=p->next)if(!to->leases||p==to->leases){
        from->leases->references++;host_tls_links_drop(to->leases);to->leases=from->leases;return ready;}
    for(HostTlsLeaseLink *p=to->leases;p;p=p->next)if(p==from->leases)return ready;
    /* Tail sharing may miss when two slots already contributed equivalent
     * lease identities through different nodes. Build one transient identity
     * set; stage all new links before publishing any of them. */
    SIZE_T count=0,capacity=16;
    for(HostTlsLeaseLink *p=to->leases;p;p=p->next){if(count==SIZE_MAX/2)goto failed;count++;}
    for(HostTlsLeaseLink *p=from->leases;p;p=p->next){if(count==SIZE_MAX/2)goto failed;count++;}
    while(capacity<count*2){if(capacity>SIZE_MAX/2)goto failed;capacity*=2;}
    if(capacity>SIZE_MAX/sizeof(HostTlsLease*))goto failed;
    HostTlsLease **seen=(HostTlsLease**)calloc(capacity,sizeof(*seen));if(!seen)goto failed;
    for(HostTlsLeaseLink *p=to->leases;p;p=p->next){
        SIZE_T at=(((uintptr_t)p->lease>>4)*UINT64_C(11400714819323198485))&(capacity-1);
        while(seen[at]&&seen[at]!=p->lease)at=(at+1)&(capacity-1);seen[at]=p->lease;}
    HostTlsSources staged={0};HostTlsLeaseLink *tail=NULL;
    for(HostTlsLeaseLink *p=from->leases;p;p=p->next){
        SIZE_T at=(((uintptr_t)p->lease>>4)*UINT64_C(11400714819323198485))&(capacity-1);
        while(seen[at]&&seen[at]!=p->lease)at=(at+1)&(capacity-1);
        if(seen[at])continue;
        if(!host_tls_sources_new_lease(&staged,p->lease)){
            host_tls_sources_clear(&staged);free(seen);goto failed;}
        if(!tail)tail=staged.leases;seen[at]=p->lease;
    }
    free(seen);
    if(tail){tail->next=to->leases;to->leases=staged.leases;staged.leases=NULL;}
    return ready;
failed:
    to->unknown=1;return 0;
}
static int host_tls_slot_sources(HostTlsSources *to,const HostTlsSlot *slot,int operations){
    int ready=host_tls_sources_merge(to,&slot->aggregate);
    if(slot->gap)for(HostTlsContribution *p=slot->contributions;p;p=p->next)
        if(!host_tls_sources_merge(to,&p->sources))ready=0;
    if(operations)for(HostTlsOperation *p=slot->operations;p;p=p->next)
        if(p->sources&&!host_tls_sources_merge(to,p->sources))ready=0;
    return ready;
}
/* Historical identities remain published for reporting/callback tombstones.
 * These separate queues contain only contexts or slots not yet settled. */
static void host_tls_thread_schedule(HostTlsThread *thread){
    if(thread->queued||thread->reaped)return;thread->queued=1;thread->activeNext=NULL;
    if(host_tls_threads_tail)host_tls_threads_tail->activeNext=thread;else host_tls_active_threads=thread;
    host_tls_threads_tail=thread;host_tls_thread_count++;
}
static void host_tls_fiber_schedule(HostTlsFiber *fiber){
    if(fiber->queued||fiber->reaped)return;fiber->queued=1;fiber->activeNext=NULL;
    if(host_tls_fibers_tail)host_tls_fibers_tail->activeNext=fiber;else host_tls_active_fibers=fiber;
    host_tls_fibers_tail=fiber;host_tls_fiber_count++;
}
static void host_tls_slot_schedule(HostTlsSlot *slot){
    if(slot->queued||slot->reaped||slot->live)return;slot->queued=1;slot->activeNext=NULL;
    if(host_tls_slots_tail)host_tls_slots_tail->activeNext=slot;else host_tls_active_slots=slot;
    host_tls_slots_tail=slot;host_tls_slot_count++;
}
static void host_tls_slot_live(HostTlsSlot *slot,int live){
    slot->live=live;
    if(live&&!slot->liveLink){slot->liveNext=host_tls_live_slots;slot->liveLink=&host_tls_live_slots;
        if(host_tls_live_slots)host_tls_live_slots->liveLink=&slot->liveNext;host_tls_live_slots=slot;}
    else if(!live&&slot->liveLink){*slot->liveLink=slot->liveNext;
        if(slot->liveNext)slot->liveNext->liveLink=slot->liveLink;slot->liveNext=NULL;slot->liveLink=NULL;}
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
    /* This leaf gate also spans external handle checks and their actual OS
     * effects. A private monitor cannot be closed between allocation and
     * publication, without waiting for unrelated process/JNI records here. */
    (void)restricted;AcquireSRWLockExclusive(&host_tls_handles);
    HANDLE monitor=NULL;BOOL copied=host_real_duplicate(GetCurrentProcess(),GetCurrentThread(),GetCurrentProcess(),
            &monitor,SYNCHRONIZE,FALSE,0);DWORD error=copied?0:GetLastError();
    if(copied&&!TlsSetValue(host_tls_thread_key,thread)){error=GetLastError();host_real_close(monitor);copied=FALSE;}
    if(copied){AcquireSRWLockExclusive(&host_tls_records);thread->generation=++host_tls_generation;
        thread->monitor=monitor;thread->next=host_tls_threads;host_tls_threads=thread;
        thread->monitorNext=host_tls_monitors;thread->monitorLink=&host_tls_monitors;
        if(host_tls_monitors)host_tls_monitors->monitorLink=&thread->monitorNext;host_tls_monitors=thread;
        thread->polling=1;host_tls_polling++;host_tls_thread_schedule(thread);
        ReleaseSRWLockExclusive(&host_tls_records);}
    ReleaseSRWLockExclusive(&host_tls_handles);
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
    fiber->next=host_tls_fibers;host_tls_fibers=fiber;host_tls_fiber_schedule(fiber);
    ReleaseSRWLockExclusive(&host_tls_records);return fiber;
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
static int host_tls_capture_detail(NativeThread *state,OwnerLink **owners,int *unknown,int *roots){
    DWORD incoming=GetLastError();HostTlsThread *thread=host_tls_thread_current();HostTlsFiber *fiber=host_tls_fiber_current();
    int ready=1;
    AcquireSRWLockShared(&host_tls_records);
    if(roots)*roots=(thread?((thread->ended?1:0)|(thread->gap?2:0)|(thread->observed.unknown?8:0)):0)
            |(fiber?((fiber->gap?4:0)|(fiber->observed.unknown?16:0)):0);
    /* A TLS value's opaque data history is not an unknown caller of every
     * later JNI/OS operation on this thread. Keep all observed contributors,
     * but obtain call origins from the live Java/native/binding scopes.
     * The value gap remains in observed.unknown, TLS reports, callback scopes
     * and data-retirement checks; it grants no source identity or lifetime. */
    if(thread){ready=native_owner_merge(owners,thread->observed.owners);if(unknown)*unknown|=thread->ended||thread->gap;}
    /* The private sentinel can run before the application's FLS callbacks.
     * Its ended bit is not termination of the still-executing OS thread.
     * Migrated/deleted fibers retain their separate actual lifecycle gap. */
    if(fiber){ready=ready&&native_owner_merge(owners,fiber->observed.owners);if(unknown)*unknown|=fiber->gap;}
    ReleaseSRWLockShared(&host_tls_records);SetLastError(incoming);return ready;
}
static int host_tls_capture(NativeThread *state,OwnerLink **owners,int *unknown){return host_tls_capture_detail(state,owners,unknown,NULL);}
/* Retain identities, not the provenance verdict of an opaque TLS value.
 * A container can have an unknown/partial data history and still own complete
 * leases for some named contributors. Only those actual leases can discharge
 * a requested identity; bare OwnerLinks and partial leases cannot. Callers
 * hold host_tls_records, including publication of each new reference. */
static int host_tls_retain_known(HostTlsSources *retained,OwnerLink **missing,const HostTlsSources *from){
    for(HostTlsLeaseLink *p=from->leases;p&&*missing;p=p->next){HostTlsLease *lease=p->lease;
        if(lease->unknown||!lease->complete||lease->releasing||lease->queued||lease->references<=0
                ||native_owner_stopped(lease->owners))continue;
        int needed=0;
        for(OwnerLink *actual=*missing;actual&&!needed;actual=actual->next)
            for(OwnerLink *held=lease->owners;held;held=held->next)if(held->owner==actual->owner){needed=1;break;}
        if(!needed)continue;
        if(!host_tls_sources_add(retained,lease->owners,lease,0))return 0;
        for(OwnerLink **at=missing;*at;){OwnerLink *actual=*at,*held=lease->owners;
            while(held&&held->owner!=actual->owner)held=held->next;
            if(held){*at=actual->next;free(actual);}else at=&actual->next;
        }
    }
    return 1;
}
static int host_tls_retain_slot_known(HostTlsSources *retained,OwnerLink **missing,const HostTlsSlot *slot){
    if(!host_tls_retain_known(retained,missing,&slot->aggregate))return 0;
    if(slot->gap)for(HostTlsContribution *p=slot->contributions;p&&*missing;p=p->next)
        if(!host_tls_retain_known(retained,missing,&p->sources))return 0;
    for(HostTlsOperation *p=slot->operations;p&&*missing;p=p->next)
        if(p->sources&&!host_tls_retain_known(retained,missing,p->sources))return 0;
    return 1;
}
static int host_tls_source_borrow(NativeThread *state,OwnerLink *owners,HostLibraryLoad *selected,void **token){
    if(token)*token=NULL;if(!state||!token||!owners)return 0;
    DWORD incoming=GetLastError();HostTlsThread *thread=host_tls_thread_current();HostTlsFiber *fiber=host_tls_fiber_current();
    if(!thread){SetLastError(incoming);return 0;}HostTlsBorrow *borrow=(HostTlsBorrow*)calloc(1,sizeof(*borrow));
    if(!borrow){SetLastError(incoming);return 0;}
    OwnerLink *missing=NULL;int ready=native_owner_merge(&missing,owners);
    /* This leaf never enters the VM or loader while held. Unknown history is
     * left on the original observations/slots for callback and retirement
     * decisions. The new lifetime token covers only an already-known request. */
    AcquireSRWLockExclusive(&host_tls_records);
    ready=ready&&!thread->ended&&!thread->gap&&(!fiber||!fiber->gap)&&!native_owner_stopped(owners);
    if(ready)ready=host_tls_retain_known(&borrow->sources,&missing,&thread->observed);
    if(ready&&fiber)ready=host_tls_retain_known(&borrow->sources,&missing,&fiber->observed);
    if(ready&&thread->activation)ready=host_tls_retain_slot_known(&borrow->sources,&missing,thread->activation->slot);
    ready=ready&&borrow->sources.leases;
    ReleaseSRWLockExclusive(&host_tls_records);
    /* A B worker/load can carry a TLS receipt from A. Retain A's actual read
     * lease and borrow the remaining B sources from a real executing parent;
     * requiring one parent to own both would silently lose the A contribution. */
    if(ready&&missing){
        borrow->load=host_module_borrow_current(state,selected,missing,0);
        if(!borrow->load&&!host_thread_worker_load_borrow(state,missing,&borrow->worker)
                &&!native_tooling_load_borrow(state,missing,&borrow->tooling))ready=0;
        if(ready&&!native_owner_merge(&borrow->sources.owners,missing))ready=0;
    }
    native_owner_links_free(missing);
    if(ready)ready=host_tls_cover(borrow->sources.owners,owners)&&!native_owner_stopped(borrow->sources.owners);
    if(ready)*token=borrow;else {
        AcquireSRWLockExclusive(&host_tls_records);host_tls_sources_clear(&borrow->sources);ReleaseSRWLockExclusive(&host_tls_records);
        host_module_load_drop(borrow->load);
        if(borrow->worker)host_thread_worker_load_drop(borrow->worker);
        if(borrow->tooling)native_thread_worker_heap_drop(borrow->tooling);free(borrow);
    }
    SetLastError(incoming);return ready;
}
static void host_tls_source_drop(void *token){
    if(!token)return;DWORD incoming=GetLastError();HostTlsBorrow *borrow=(HostTlsBorrow*)token;
    AcquireSRWLockExclusive(&host_tls_records);host_tls_sources_clear(&borrow->sources);ReleaseSRWLockExclusive(&host_tls_records);
    host_module_load_drop(borrow->load);
    if(borrow->worker)host_thread_worker_load_drop(borrow->worker);
    if(borrow->tooling)native_thread_worker_heap_drop(borrow->tooling);
    free(borrow);native_thread_worker_wake();SetLastError(incoming);
}
static HostTlsLease *host_tls_lease_prepare(NativeThread *state,OwnerLink *owners,int unknown,int restricted,HostLibraryLoad *selected){
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
        lease->load=host_module_borrow_current(state,selected,owners,0);lease->complete=lease->load!=NULL;
        if(!lease->complete&&host_thread_worker_load_borrow(state,owners,&lease->worker))lease->complete=1;
        if(!lease->complete&&native_tooling_load_borrow(state,owners,&lease->tooling))lease->complete=1;
        if(!lease->complete&&host_tls_source_borrow(state,owners,selected,&lease->tls))lease->complete=1;
    }
    /* Retain even a partial lease as an explicit gap, rather than forgetting
     * an observed source because its weak Java identity later disappears. */
    return lease;
}
static int host_tls_callback_sources(NativeThread *state,void *callback,OwnerLink **owners,int *unknown,int restricted,HostLibraryLoad **selected){
    if(!restricted){int ready=host_capture_caller_detail(state,callback,owners,unknown,selected);
        if(!ready){if(selected)*selected=NULL;SetLastError(ERROR_NOT_ENOUGH_MEMORY);}return ready;}
    if(!state){SetLastError(ERROR_INVALID_STATE);return 0;}
    DWORD error=ERROR_INVALID_DATA;int ready=host_callback_caller_load(state,callback,owners,unknown,&error,selected);
    if(!ready)SetLastError(error);return ready;
}
static HostTlsSlot *host_tls_slot_find(int fls,DWORD index){
    for(HostTlsSlot *slot=host_tls_live_slots;slot;slot=slot->liveNext)if(slot->fls==fls&&slot->index==index)return slot;return NULL;
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
    DWORD index=allocating?TLS_OUT_OF_INDEXES:(DWORD)arguments[0];OwnerLink *owners=NULL;int unknown=0;HostLibraryLoad *selectedLoad=NULL;NativeSourceRoots roots;
    if(!allocating&&(!fls&&(native_tls!=TLS_OUT_OF_INDEXES&&index==native_tls
            ||host_tls_thread_key!=TLS_OUT_OF_INDEXES&&index==host_tls_thread_key)
            ||fls&&host_tls_fiber_key!=FLS_OUT_OF_INDEXES&&index==host_tls_fiber_key))
        return host_tls_failure(kind,ERROR_ACCESS_DENIED,incoming);
    if(!restricted)entry=host_consumed_entry(entry,state,caller);
    int captured=host_entry_sources_query_load(entry,state,caller,&owners,NULL,1,&unknown,&selectedLoad,&roots);
    DWORD queryError=GetLastError();
    // Capture the roots used by this source query before lease preparation or
    // callback/context bookkeeping can change them. Failure only adds its stage.
    HostTlsRefusal refusal=host_tls_refusal(entry,kind,index,queryError,"source_query",restricted,unknown,owners,state,caller,selectedLoad,&roots);
    if(!captured){
        error=queryError;
        AcquireSRWLockShared(&host_tls_records);HostTlsSlot *actual=host_tls_slot_find(fls,index);
        if(actual)refusal.generation=actual->generation;ReleaseSRWLockShared(&host_tls_records);
        host_tls_refused(&refusal);
        native_owner_links_free(owners);return host_tls_failure(kind,error,incoming);}
    HostTlsSlot *slot=NULL;HostTlsValue *value=NULL;HostTlsLease *lease=NULL;HostTlsThread *thread=NULL;HostTlsFiber *fiber=NULL;
    HostTlsSources prepared={0},aggregation={0};HostTlsContribution *contribution=NULL;
    OwnerLink *readOwners=NULL;uint64_t readRevision=0,readEpoch=0;
    HostTlsOperation operation={0};uint64_t result=allocating?(uint64_t)(DWORD)TLS_OUT_OF_INDEXES:0;
    int reserved=0,called=0,returned=0,success=0,published=0;const char *failure="preparation";
    __try {
        if(native_owner_stopped(owners)){error=ERROR_ACCESS_DENIED;failure="stopped_source";}
        else if(unknown&&native_has_producer(owners)){error=ERROR_INVALID_DATA;failure="unknown_source";}
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
                    if(!error){OwnerLink *callbackOwners=NULL;int callbackUnknown=0;
                        refusal.callbackSeen=1;
                        refusal.callbackCaptured=host_tls_callback_sources(state,(void*)slot->callback,&callbackOwners,&callbackUnknown,restricted,&refusal.callbackLoad);
                        DWORD callbackError=GetLastError();refusal.callbackUnknown=callbackUnknown;
                        refusal.callbackProducer=native_has_producer(callbackOwners);unknown|=callbackUnknown;
                        if(!refusal.callbackCaptured){error=callbackError?callbackError:ERROR_INVALID_DATA;failure="callback_sources";}
                        else if(!native_owner_merge(&owners,callbackOwners)){error=ERROR_NOT_ENOUGH_MEMORY;failure="callback_sources_merge";}
                        native_owner_links_free(callbackOwners);
                    }
                    if(!error){slot->adapter=host_tls_callback_entry(slot);if(!slot->adapter)error=GetLastError();}
                }
            }
            if(!error){lease=host_tls_lease_prepare(state,owners,unknown,restricted,selectedLoad);if(!lease)error=GetLastError();
                // A known platform-only callback does not acquire a private
                // Mod lease by setting its own TLS value. Public-carrier
                // attribution remains unknown and grants no private lifetime
                // or retirement authority. A restricted loader/callback scope
                // alone must not turn a CRT's own TLS into an invalid value.
                // Actual Mod callers and actual Mod-bearing slots stay gated.
                else if(!lease->complete&&native_has_producer(owners)){error=ERROR_INVALID_DATA;failure="source_lease";}}
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
                            host_tls_slot_live(slot,1);slot->uncertain=slot->gap=1;slot->next=host_tls_slots;host_tls_slots=slot;published=1;}}
                    else published=1;
                    /* Unknown public carriers may use ordinary per-context OS
                     * storage, never a slot bearing a captured Mod source.
                     * Check the complete observed history, not just the caller
                     * or the current opaque pointer. Keep every partial lease
                     * and unknown bit for reporting and retirement. */
                    if(!error&&(unknown||lease&&!lease->complete)){
                        int controlled=native_has_producer(slot->allocation.owners)||native_has_producer(slot->aggregate.owners);
                        for(HostTlsContribution *p=slot->contributions;p&&!controlled;p=p->next)
                            controlled=native_has_producer(p->sources.owners);
                        for(HostTlsValue *p=slot->values;p&&!controlled;p=p->next)
                            controlled=native_has_producer(p->sources.owners);
                        for(HostTlsOperation *p=slot->operations;p&&!controlled;p=p->next)
                            controlled=p->sources&&native_has_producer(p->sources->owners);
                        if(controlled){error=ERROR_INVALID_DATA;failure="controlled_slot_sources";}
                    }
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
                                if(value&&!host_tls_sources_merge(&value->sources,&slot->allocation)){
                                    host_tls_sources_clear(&value->sources);free(value);value=NULL;}
                                if(value){
                                    value->thread=thread;value->fiber=fiber;value->slot=slot;value->revision=1;
                                    value->next=slot->values;slot->values=value;
                                    if(fls){value->contextNext=fiber->values;fiber->values=value;
                                        if(fiber->valuesTail)fiber->valuesTail->workNext=value;else fiber->activeValues=value;
                                        fiber->valuesTail=value;fiber->valueCount++;}
                                    else {value->contextNext=thread->values;thread->values=value;
                                        if(thread->valuesTail)thread->valuesTail->workNext=value;else thread->activeValues=value;
                                        thread->valuesTail=value;thread->valueCount++;}
                                }
                                else error=ERROR_NOT_ENOUGH_MEMORY;}
                        }
                        if(!error&&setting){
                            if(!host_tls_sources_merge(&prepared,&value->sources)
                                    ||!host_tls_sources_add(&prepared,owners,lease,unknown||!slot->observed))error=ERROR_NOT_ENOUGH_MEMORY;
                            else if(native_owner_stopped(prepared.owners))error=ERROR_ACCESS_DENIED;
                            /* Retain an in-flight writer before FlsSetValue:
                             * another OS thread may concurrently enter a real
                             * FLS callback. Failed stores remain only a gap. */
                            if(!error&&!host_tls_sources_add(&aggregation,owners,lease,unknown||!slot->observed))error=ERROR_NOT_ENOUGH_MEMORY;
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
                            uint64_t epoch=fls?fiber->observedEpoch:thread->observedEpoch;
                            int current=!thread->ended&&!thread->gap&&(!fls||!fiber->ended&&!fiber->gap);
                            /* Only memoize collection membership in this actual
                             * context, never a caller permission. Observation is
                             * monotonic until real context cleanup advances its
                             * epoch. Pre-retaining a possible read also prevents
                             * a reentrant API hook from replacing newer history
                             * with the stale pre-call snapshot. Failed reads get
                             * no membership marker and may conservatively retain
                             * facts; the original API still runs only if allowed. */
                            if(!current||value->retainedRevision!=value->revision||value->retainedEpoch!=epoch)
                                if(!host_tls_sources_merge(observed,&value->sources))error=ERROR_NOT_ENOUGH_MEMORY;
                            if(!slot->observed||slot->uncertain)observed->unknown=1;
                            readOwners=observed->owners;readRevision=value->revision;readEpoch=epoch;
                            if(!error&&native_owner_stopped(readOwners))error=ERROR_ACCESS_DENIED;
                        }
                    }
                }
                ReleaseSRWLockExclusive(&host_tls_records);
            }
        }
        if(!error&&(native_owner_stopped(owners)||setting&&native_owner_stopped(prepared.owners)
                ||getting&&native_owner_stopped(readOwners)))error=ERROR_ACCESS_DENIED;
        if(!error){SetLastError(incoming);called=1;result=host_tls_original(entry,arguments,slot?slot->adapter:NULL);
            error=GetLastError();returned=1;
            success=allocating?(DWORD)result!=TLS_OUT_OF_INDEXES:getting?
                    (kind==HOST_TLS_GET2?result!=0||slot->observed:
                     kind==HOST_FLS_GET?result!=0||error==ERROR_SUCCESS||slot->observed&&!slot->uncertain:
                     result!=0||error==ERROR_SUCCESS):result!=0;
        }
    } __finally {
        DWORD interrupted=GetLastError();
        if(!called){refusal.site=failure;refusal.error=error?error:ERROR_INVALID_DATA;refusal.leaseComplete=lease?lease->complete:-1;
            refusal.unknown=unknown;refusal.producer=native_has_producer(owners)||refusal.callbackProducer;}
        AcquireSRWLockExclusive(&host_tls_records);
        if(!called){HostTlsSlot *actual=slot?slot:host_tls_slot_find(fls,index);if(actual)refusal.generation=actual->generation;}
        if(slot&&reserved){
            if(called&&!returned){slot->gap=slot->uncertain=1;if(value)value->uncertain=1;
                if(value){value->revision++;value->retainedRevision=0;}
                if(setting&&value){HostTlsSources before=value->sources;value->sources=prepared;prepared=before;value->sources.unknown=1;}
                if(contribution)aggregation.unknown=1;}
            if(allocating){
                if(returned&&success){slot->index=(DWORD)result;
                    HostTlsSlot *old=host_tls_slot_find(fls,slot->index);
                    if(old){host_tls_slot_live(old,0);if(!old->closing)old->uncertain=old->gap=1;host_tls_slot_schedule(old);}
                    host_tls_slot_live(slot,1);
                }else if(called&&!returned)slot->gap=slot->uncertain=1;
                if(!published){slot->next=host_tls_slots;host_tls_slots=slot;published=1;}
            }else if(returned&&success){
                if(setting){HostTlsSources before=value->sources;value->sources=prepared;prepared=before;
                    value->value=arguments[1];value->initialized=1;value->closed=0;value->revision++;value->retainedRevision=0;
                }else if(getting){
                    HostTlsSources *observed=fls?&fiber->observed:&thread->observed;
                    uint64_t actual=result;
                    /* A foreign implementation may reenter and store a new
                     * value during the real read. Retain that current source
                     * revision before exposing its returned pointer. On an
                     * allocation failure the read has no memory write to undo;
                     * refuse the result rather than lose its source lifetime. */
                    if(value->revision!=readRevision){
                        if(!host_tls_sources_merge(observed,&value->sources)){
                            error=ERROR_NOT_ENOUGH_MEMORY;result=0;success=0;
                        }else readRevision=value->revision;
                    }
                    if(!value->initialized&&actual||value->initialized&&value->value!=(uintptr_t)actual){
                        value->uncertain=slot->gap=1;value->revision++;value->retainedRevision=0;
                        if(actual){observed->unknown=1;value->sources.unknown=1;}}
                    /* A read receipt survives Set(NULL), SlotFree, detach and
                     * subsequent index reuse. It is retired only with the real
                     * OS thread / actual FLS-context lifecycle. CPU value flow
                     * after returning the opaque pointer is still unobserved. */
                    if(!slot->observed||slot->uncertain)observed->unknown=1;
                    if(success&&value->revision==readRevision&&readEpoch==(fls?fiber->observedEpoch:thread->observedEpoch)
                            &&!thread->ended&&!thread->gap&&(!fls||!fiber->ended&&!fiber->gap)){
                        value->retainedRevision=readRevision;value->retainedEpoch=readEpoch;}
                }else if(freeing){host_tls_slot_live(slot,0);
                    for(HostTlsValue *p=slot->values;p;p=p->next){p->closed=1;p->revision++;p->retainedRevision=0;}
                    if(slot->callback){
                        /* Public FlsFree has no documented barrier for a
                         * callback already fetched by another OS thread. Keep
                         * the callable tombstone's real provider/source lease. */
                        slot->gap=1;
                    }
                }
            }
            if(getting&&value&&(!returned||!success))value->retainedRevision=0;
            int needsHistory=(setting||fls&&freeing)&&called&&(!returned||success),aggregateSaved=1;
            if(needsHistory){
                aggregateSaved=host_tls_sources_add(&slot->aggregate,aggregation.owners,lease,aggregation.unknown);
                if(!aggregateSaved)slot->gap=1;
                slot->aggregate.partial|=aggregation.partial;
            }
            if(contribution&&needsHistory&&(fls||!aggregateSaved)){
                contribution->sources=aggregation;aggregation=(HostTlsSources){0};
                contribution->valueReceipt=fls&&setting&&returned&&success;contribution->value=fls&&setting?arguments[1]:0;
                contribution->next=slot->contributions;slot->contributions=contribution;contribution=NULL;
            }
            if(!allocating&&!slot->observed&&(!called||returned&&!success))host_tls_slot_live(slot,0);
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
        if(slot&&published)host_tls_slot_schedule(slot);
        host_tls_sources_clear(&prepared);host_tls_sources_clear(&aggregation);
        if(contribution)free(contribution);
        if(lease)host_tls_lease_drop(lease);ReleaseSRWLockExclusive(&host_tls_records);
        if(!called)host_tls_refused(&refusal);
        native_owner_links_free(owners);
        // Reads retain the old observation and introduce no zero-reference
        // lease. New contexts already wake the daemon at publication, and its
        // existing monitor timeout observes actual kernel-thread termination.
        if(!getting)native_thread_worker_wake();
        SetLastError(kind==HOST_TLS_GET2?incoming:returned?error:error?error:interrupted);
    }
    return called?result:host_tls_failure(kind,error?error:ERROR_INVALID_DATA,incoming);
}

/* host_tls_handles spans the consumer's check and actual handle effect. */
static int host_tls_internal_handle(HANDLE handle){
    int found=0;AcquireSRWLockShared(&host_tls_records);
    for(HostTlsThread *thread=host_tls_monitors;thread;thread=thread->monitorNext)
        if(thread->monitor==handle){found=1;break;}
    ReleaseSRWLockShared(&host_tls_records);return found;
}
static int host_tls_pending(void){
    AcquireSRWLockShared(&host_tls_records);
    int pending=host_tls_polling||host_tls_retired_leases||host_tls_thread_scan||host_tls_fiber_scan
            ||host_tls_slot_scan||host_tls_cleanup_retry;
    ReleaseSRWLockShared(&host_tls_records);return pending;
}
static void host_tls_values_reap(HostTlsValue **head,HostTlsValue **tail,SIZE_T *count,int fls){
    SIZE_T limit=*count<32?*count:32;
    while(limit--){HostTlsValue *value=*head;*head=value->workNext;if(!*head)*tail=NULL;
        value->workNext=NULL;(*count)--;
        if(value->slot->busy||fls&&value->slot->callbacks){
            if(*tail)(*tail)->workNext=value;else *head=value;*tail=value;(*count)++;continue;}
        value->closed=1;value->revision++;value->retainedRevision=0;host_tls_sources_clear(&value->sources);
    }
    if(*head)host_tls_cleanup_retry=1;
}
static void host_tls_reap(JNIEnv **environment){
    if(!environment||!*environment)return;JNIEnv *env=*environment;
    /* No new timer or enumerator: the existing retirement daemon observes only
     * our retained actual thread handles and queued zero-reference leases.
     * A pass visits at most 32 records of each kind. Unfinished sweep counters
     * continue fairly next time; a permanently unresolved gap cannot cause an
     * immediate self-waking loop. Historical tombstones are never the queue. */
    AcquireSRWLockExclusive(&host_tls_records);host_tls_cleanup_retry=0;
    if(!host_tls_thread_scan)host_tls_thread_scan=host_tls_thread_count;
    if(!host_tls_fiber_scan)host_tls_fiber_scan=host_tls_fiber_count;
    if(!host_tls_slot_scan)host_tls_slot_scan=host_tls_slot_count;
    ReleaseSRWLockExclusive(&host_tls_records);
    for(unsigned n=0;n<32;n++){
        /* Keep the real monitor protected through query/close and unlink; the
         * external handle effect takes this same gate shared. Never copy a
         * bare HANDLE out and then let another thread close/reuse it. The
         * locks cover one context only, not a complete history walk. */
        AcquireSRWLockExclusive(&host_tls_handles);AcquireSRWLockExclusive(&host_tls_records);
        if(!host_tls_thread_scan){ReleaseSRWLockExclusive(&host_tls_records);ReleaseSRWLockExclusive(&host_tls_handles);break;}
        HostTlsThread *thread=host_tls_active_threads;host_tls_thread_scan--;
        host_tls_active_threads=thread->activeNext;if(!host_tls_active_threads)host_tls_threads_tail=NULL;
        thread->queued=0;thread->activeNext=NULL;host_tls_thread_count--;
        if(!thread->ended&&thread->monitor){DWORD status=WaitForSingleObject(thread->monitor,0);
            if(status==WAIT_OBJECT_0)thread->ended=1;
            else if(status==WAIT_FAILED)thread->gap=1;
            if((thread->ended||status==WAIT_FAILED)&&thread->polling){thread->polling=0;host_tls_polling--;}}
        if(thread->ended){
            if(thread->observed.owners||thread->observed.leases||thread->observed.unknown||thread->observed.partial){
                thread->observedEpoch++;host_tls_sources_clear(&thread->observed);}
            host_tls_values_reap(&thread->activeValues,&thread->valuesTail,&thread->valueCount,0);
            if(thread->monitor){
                if(host_real_close(thread->monitor)){
                    *thread->monitorLink=thread->monitorNext;
                    if(thread->monitorNext)thread->monitorNext->monitorLink=thread->monitorLink;
                    thread->monitorLink=NULL;thread->monitorNext=NULL;thread->monitor=NULL;
                }else thread->gap=1;
            }
            if(!thread->activeValues&&!thread->monitor)thread->reaped=1;
        }
        host_tls_thread_schedule(thread);
        ReleaseSRWLockExclusive(&host_tls_records);ReleaseSRWLockExclusive(&host_tls_handles);
    }
    for(unsigned n=0;n<32;n++){
        AcquireSRWLockExclusive(&host_tls_records);
        if(!host_tls_fiber_scan){ReleaseSRWLockExclusive(&host_tls_records);break;}
        HostTlsFiber *fiber=host_tls_active_fibers;host_tls_fiber_scan--;
        host_tls_active_fibers=fiber->activeNext;if(!host_tls_active_fibers)host_tls_fibers_tail=NULL;
        fiber->queued=0;fiber->activeNext=NULL;host_tls_fiber_count--;
        if(fiber->home->ended){
            // An ended home thread grants no disposal of a migrated/opaque
            // fiber. Its actual unresolved lifetime and sources stay reported.
            if(!fiber->ended||fiber->actualFiber||fiber->gap)fiber->gap=1;
            else {
                if(fiber->observed.owners||fiber->observed.leases||fiber->observed.unknown||fiber->observed.partial){
                    fiber->observedEpoch++;host_tls_sources_clear(&fiber->observed);}
                host_tls_values_reap(&fiber->activeValues,&fiber->valuesTail,&fiber->valueCount,1);
                if(!fiber->activeValues)fiber->reaped=1;
            }
        }
        host_tls_fiber_schedule(fiber);ReleaseSRWLockExclusive(&host_tls_records);
    }
    /* Callback adapter pages and their RUNTIME_FUNCTION remain callable
     * tombstones. A failed, unpublished allocation has no OS callback receipt. */
    for(unsigned n=0;n<32;n++){HostTlsSlot *release=NULL;HMODULE pin=NULL;void *anchor=NULL;
        AcquireSRWLockExclusive(&host_tls_records);
        if(!host_tls_slot_scan){ReleaseSRWLockExclusive(&host_tls_records);break;}
        HostTlsSlot *slot=host_tls_active_slots;host_tls_slot_scan--;
        host_tls_active_slots=slot->activeNext;if(!host_tls_active_slots)host_tls_slots_tail=NULL;
        slot->queued=0;slot->activeNext=NULL;host_tls_slot_count--;
        if(!slot->live&&!slot->busy&&!slot->callbacks&&!slot->borrowers&&!slot->uncertain&&!slot->cleanupGap&&!slot->releasing){
            if(slot->index==TLS_OUT_OF_INDEXES&&(slot->pin||slot->providerAnchor)){
                release=slot;pin=slot->pin;anchor=slot->providerAnchor;
                slot->pin=NULL;slot->providerAnchor=NULL;slot->releasing=1;
            }else if(!(slot->callback&&slot->index!=TLS_OUT_OF_INDEXES)&&!slot->pin&&!slot->providerAnchor){
                host_tls_sources_clear(&slot->allocation);host_tls_sources_clear(&slot->aggregate);
                for(unsigned k=0;k<32&&slot->contributions;k++){
                    HostTlsContribution *p=slot->contributions;slot->contributions=p->next;
                    host_tls_sources_clear(&p->sources);free(p);
                }
                if(!slot->contributions)slot->reaped=1;else host_tls_cleanup_retry=1;
            }
        }
        if(!release)host_tls_slot_schedule(slot);
        ReleaseSRWLockExclusive(&host_tls_records);if(!release)continue;
        NativeThread *daemon=native_thread();int freed=0,pinReturned=0;
        if(daemon){NativeToolingScope scope={release->aggregate.owners,NULL,release->aggregate.unknown,daemon->depth,daemon->tooling};
            native_thread_callback_enter(daemon);daemon->tooling=&scope;
            __try {freed=!pin||host_real_free_library(pin);pinReturned=1;}
            __finally {
                if(!pinReturned){AcquireSRWLockExclusive(&host_tls_records);release->pin=pin;release->providerAnchor=anchor;
                    release->gap=1;release->releasing=0;host_tls_slot_schedule(release);
                    ReleaseSRWLockExclusive(&host_tls_records);}
                daemon->tooling=scope.previous;native_thread_callback_leave(daemon);
            }
        }
        if(!freed){AcquireSRWLockExclusive(&host_tls_records);release->pin=pin;release->providerAnchor=anchor;
            release->gap=1;release->releasing=0;host_tls_slot_schedule(release);
            ReleaseSRWLockExclusive(&host_tls_records);break;}
        if(anchor)native_thread_worker_heap_drop(anchor);
        AcquireSRWLockExclusive(&host_tls_records);release->releasing=0;host_tls_cleanup_retry=1;host_tls_slot_schedule(release);
        ReleaseSRWLockExclusive(&host_tls_records);
        if(!native_tooling_retirement_jni(environment))break;env=*environment;
    }
    if(!*environment)return;env=*environment;
    for(unsigned n=0;n<32;n++){HostTlsLease *lease=NULL;
        AcquireSRWLockExclusive(&host_tls_records);
        lease=host_tls_retired_leases;
        if(lease){host_tls_retired_leases=lease->next;if(!host_tls_retired_leases)host_tls_retired_tail=NULL;
            lease->next=NULL;lease->queued=0;
            if(lease->references||lease->releasing)lease=NULL;else lease->releasing=1;}
        ReleaseSRWLockExclusive(&host_tls_records);if(!lease)break;
        if(lease->strong)native_original.DeleteGlobalRef(env,lease->strong);
        host_module_load_drop(lease->load);
        if(lease->worker)host_thread_worker_load_drop(lease->worker);
        if(lease->tooling)native_thread_worker_heap_drop(lease->tooling);
        if(lease->tls)host_tls_source_drop(lease->tls);
        native_owner_links_free(lease->owners);free(lease);
    }
    AcquireSRWLockShared(&host_tls_records);
    int more=host_tls_thread_scan||host_tls_fiber_scan||host_tls_slot_scan||host_tls_retired_leases;
    ReleaseSRWLockShared(&host_tls_records);
    if(more)native_thread_worker_wake();
}
static int host_tls_owner_matches(OwnerLink *head,const Owner *selected,int all){
    if(all)return 1;for(;head;head=head->next)if(head->owner==selected)return 1;return 0;
}
static int host_tls_state(JNIEnv *env,jobject module,jlong *values){
    int all=module==NULL;Owner *selected=all?NULL:native_owner_published(env,module);
    if(native_original.ExceptionCheck(env))return 0;
    if(!all&&!selected)return 1;
    AcquireSRWLockShared(&host_tls_records);
    for(HostTlsSlot *slot=host_tls_slots;slot;slot=slot->next){
        int matches=host_tls_owner_matches(slot->aggregate.owners,selected,all),retained=0;
        for(HostTlsContribution *p=slot->contributions;p;p=p->next){
            matches|=host_tls_owner_matches(p->sources.owners,selected,all);retained|=p->sources.leases!=NULL;}
        if(!matches)for(HostTlsValue *value=slot->values;value;value=value->next)
            if(host_tls_owner_matches(value->sources.owners,selected,all)){matches=1;break;}
        if(!matches)continue;
        if(slot->live)values[32]++;
        values[34]+=slot->busy+slot->callbacks;
        if(slot->gap||slot->uncertain||slot->cleanupGap||slot->allocation.partial||slot->aggregate.partial)values[35]++;
        if(slot->pin||slot->providerAnchor||slot->allocation.leases||slot->aggregate.leases||retained)values[36]++;
        for(HostTlsValue *value=slot->values;value;value=value->next)
            if((value->sources.leases||value->sources.owners)&&host_tls_owner_matches(value->sources.owners,selected,all)){
                values[33]++;if(value->uncertain||value->sources.partial)values[35]++;}
    }
    for(HostTlsThread *thread=host_tls_threads;thread;thread=thread->next)
        if(host_tls_owner_matches(thread->observed.owners,selected,all)){
            if(thread->observed.leases)values[36]++;if(thread->gap||thread->observed.unknown||thread->observed.partial)values[35]++;}
    for(HostTlsFiber *fiber=host_tls_fibers;fiber;fiber=fiber->next)
        if(host_tls_owner_matches(fiber->observed.owners,selected,all)){
            if(fiber->observed.leases)values[36]++;if(fiber->gap||fiber->observed.unknown||fiber->observed.partial)values[35]++;}
    ReleaseSRWLockShared(&host_tls_records);return 1;
}
