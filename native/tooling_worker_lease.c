/* Included by bindings.c immediately after tooling_heap.c. A heap callback
 * may borrow only its already published, strongly retained callback sources
 * and actual pinned image. The token transfers one lifetime hold, never JNI,
 * producer registration or private disposal permission.
 */
static int native_thread_worker_heap_sources_cover(OwnerLink *retained,OwnerLink *actual){
    if(!retained||!actual)return 0;
    for(;actual;actual=actual->next){
        if(!actual->owner)return 0;
        OwnerLink *found=retained;
        while(found&&found->owner!=actual->owner)found=found->next;
        if(!found)return 0;
    }
    return 1;
}

static int native_thread_worker_heap_start_in_image(const NativeToolingSlot *anchor,void *start){
    /* Normal preparation captured this actual pinned PE's SizeOfImage before
     * publication. Heap callbacks do not resolve loaders or reread mutable PE
     * headers to widen the retained image's original range.
     */
    uintptr_t base=(uintptr_t)anchor->image,address=(uintptr_t)start;
    return base&&start&&anchor->heapImageSize&&address>=base&&address-base<anchor->heapImageSize;
}

static int native_thread_worker_heap_borrow(NativeThread *state,OwnerLink *fullSources,
        void *start,void **token,HMODULE *image){
    DWORD incoming=GetLastError();
    if(token)*token=NULL;if(image)*image=NULL;
    if(!token||!image||!fullSources||!start){SetLastError(ERROR_INVALID_PARAMETER);return 0;}
    if(!state||!state->heapCallback||!state->heapWorkerAnchor||!state->tooling
            ||!state->toolingCallbackEnvironment){SetLastError(ERROR_INVALID_STATE);return 0;}
    /* No lock held by a Java thread paused in the heap walk may be waited on. */
    if(!TryAcquireSRWLockExclusive(&native_tooling_records)){SetLastError(ERROR_BUSY);return 0;}
    NativeToolingSlot *anchor=state->heapWorkerAnchor,*published=native_tooling_slots;
    while(published&&published!=anchor)published=published->next;
    DWORD failure=ERROR_INVALID_DATA;int ready=0;
    if(published){
        NativeToolingEnv *environment=anchor->environment;
        NativeToolingScope *scope=state->tooling;
        int associated=environment&&environment==state->toolingCallbackEnvironment
                &&scope->owners==anchor->owners&&scope->origin==anchor->origin&&scope->depth==state->depth;
        /* A shared environment's original creator is separate from this
         * callback's current complete sources and retained image lease. */
        int stopped=native_owner_stopped(fullSources)||native_owner_stopped(anchor->owners);
        int known=!anchor->unknown&&environment;
        for(NativeToolingScope *current=scope;current;current=current->previous){
            if(current->unknown)known=0;
            if(native_owner_stopped(current->owners))stopped=1;
        }
        if(native_tooling_shutdown||!anchor->admitted||stopped
                ||(environment&&(environment->closing||environment->disposed)))failure=ERROR_ACCESS_DENIED;
        else if(associated&&known&&anchor->active>0&&!anchor->queued&&!anchor->releasing
                &&anchor->sourceLease&&anchor->heapSourceLeaseComplete&&anchor->pin&&anchor->image==anchor->pin
                &&native_thread_worker_heap_sources_cover(anchor->owners,fullSources)
                &&native_thread_worker_heap_start_in_image(anchor,start)){
            InterlockedIncrement(&anchor->active);environment->active++;
            *token=anchor;*image=anchor->image;ready=1;
        }
    }
    ReleaseSRWLockExclusive(&native_tooling_records);
    SetLastError(ready?incoming:failure);return ready;
}

static void native_thread_worker_heap_drop(void *token){
    if(!token)return;DWORD incoming=GetLastError();NativeToolingSlot *anchor=(NativeToolingSlot*)token;
    /* Only the existing normal-context daemon returns this owned token, after
     * actual OS exit (or a failed creation). Callback/Detach/C return is not
     * an exit witness. The heap caller's own active=1 is returned separately.
     */
    AcquireSRWLockExclusive(&native_tooling_records);
    InterlockedDecrement(&anchor->active);anchor->environment->active--;
    native_tooling_slot_queue_locked(anchor);
    native_tooling_environment_queue_locked(anchor->environment);
    ReleaseSRWLockExclusive(&native_tooling_records);SetLastError(incoming);
}
