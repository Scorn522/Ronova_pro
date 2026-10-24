/* Only current JNIEnv values actually delivered by this VM enter this list.
 * The JNI slot and its complete live table are separate allocations. A JVMTI
 * GetJNIFunctionTable copy is neither allocation's identity or lifetime. */
static SRWLOCK native_jni_tables_gate=SRWLOCK_INIT;
static NativeThread *native_jni_threads;
static const struct jvmtiInterface_1_ *native_jni_vm_original;
static const struct JNINativeInterface_ *native_jni_vm_table;
static NativePrivateRange native_jni_vm_range;
static volatile LONG native_jni_mutating,native_jni_pending;
enum { NATIVE_JNI_SLOT_COUNT=sizeof(struct JNINativeInterface_)/sizeof(uintptr_t) };
typedef struct NativeJniApproval {
    struct JNINativeInterface_ table;OwnerLink *sources[NATIVE_JNI_SLOT_COUNT];
    unsigned char revoked[NATIVE_JNI_SLOT_COUNT];
} NativeJniApproval;
static NativeJniApproval native_jni_approved;
/* Validated call-local input remains alive across the actual VM publication.
 * It is not the approved baseline until that publication succeeds. */
static NativeJniApproval *native_jni_publishing;

static int native_jni_read(const void *address,void *copy,SIZE_T length){
    return native_control_snapshot(address,copy,length);
}
static int native_jni_current(JNIEnv *env){
    JNIEnv *actual=NULL;
    return env&&native_vm&&native_vm_original.GetEnv
            &&native_vm_original.GetEnv(native_vm,(void**)&actual,JNI_VERSION_1_8)==JNI_OK&&actual==env;
}
static int native_jni_snapshot(JNIEnv *env,const struct JNINativeInterface_ **table,struct JNINativeInterface_ *copy){
    const struct JNINativeInterface_ *actual=NULL;
    if(!native_jni_read(env,&actual,sizeof(actual))||!actual
            ||((uintptr_t)actual&(sizeof(void*)-1))
            ||sizeof(*actual)-1>UINTPTR_MAX-(uintptr_t)actual
            ||!native_jni_read(actual,copy,sizeof(*copy)))return 0;
    *table=actual;return 1;
}
static int native_jni_install_table(JNIEnv *env){
    struct JNINativeInterface_ copy;const struct JNINativeInterface_ *table=NULL;
    HMODULE vm=native_image((void*)native_vm_original.GetEnv);
    if(!native_jni_current(env)||!native_jni_snapshot(env,&table,&copy))return 0;
    /* HotSpot 17 publishes into the VM's static table, rather than into the
     * JVMTI allocation. A current heap/shadow table remains an equally real
     * active table, but supplies no static VM publication address. */
    if(vm&&native_image((void*)table)==vm){
        if(!native_private_register(&native_jni_vm_range,table,sizeof(*table)))return 0;
        native_jni_vm_table=table;
    }
    return 1;
}
static int native_jni_controlled(unsigned index){
    const uintptr_t *original=(const uintptr_t*)&native_original,*guarded=(const uintptr_t*)&native_guarded;
    return guarded[index]!=original[index];
}
static void native_jni_approve_initial(const struct JNINativeInterface_ *table){
    AcquireSRWLockExclusive(&native_jni_tables_gate);native_jni_approved.table=*table;
    ReleaseSRWLockExclusive(&native_jni_tables_gate);
}
static void native_jni_approval_release(NativeJniApproval *approval){
    for(unsigned i=4;i<NATIVE_JNI_SLOT_COUNT;i++)native_owner_links_free(approval->sources[i]);
}
static int native_jni_approval_prepare(NativeThread *state,const struct JNINativeInterface_ *table,NativeJniApproval *approval){
    memset(approval,0,sizeof(*approval));approval->table=*table;
    uintptr_t *proposed=(uintptr_t*)&approval->table;const uintptr_t *original=(const uintptr_t*)&native_original;
    int ready=1;
    /* This may enter Host/native source directories. No JNI directory lock is
     * held, and a nested current-env observation cannot recurse into capture. */
    state->jniObserving++;
    for(unsigned i=4;i<NATIVE_JNI_SLOT_COUNT&&ready;i++){
        if(native_jni_controlled(i)||!proposed[i]||proposed[i]==original[i])continue;
        ready=host_capture_caller(state,(void*)proposed[i],&approval->sources[i]);
        if(ready&&native_owner_stopped(approval->sources[i])){
            proposed[i]=original[i];native_owner_links_free(approval->sources[i]);approval->sources[i]=NULL;
        }
    }
    state->jniObserving--;return ready;
}
/* Read under either JNI gate mode. The return value means the approved
 * baseline must be retired under the exclusive gate; it is not permission
 * to keep a rejected pointer in a caller's table. */
static int native_jni_expected_read_locked(unsigned index,uintptr_t current,uintptr_t *expected){
    const uintptr_t *original=(const uintptr_t*)&native_original,*guarded=(const uintptr_t*)&native_guarded;
    if(native_jni_controlled(index)){*expected=guarded[index];return 0;}
    if(index<4){*expected=current;return 0;} /* Reserved words are not function addresses. */
    const uintptr_t *approved=(const uintptr_t*)&native_jni_approved.table;
    const NativeJniApproval *publishing=native_jni_publishing;
    int rejectedPublication=0;
    if(publishing&&current==((const uintptr_t*)&publishing->table)[index]){
        if(!publishing->revoked[index]&&!native_owner_stopped(publishing->sources[index])){*expected=current;return 0;}
        rejectedPublication=1;
    }
    if(rejectedPublication||current!=approved[index]||native_owner_stopped(native_jni_approved.sources[index])){
        *expected=original[index];return 1;
    }
    *expected=approved[index];return 0;
}
/* Only actual baseline retirement writes require the exclusive gate. */
static uintptr_t native_jni_expected_locked(unsigned index,uintptr_t current){
    uintptr_t expected;
    if(native_jni_expected_read_locked(index,current,&expected)){
        ((uintptr_t*)&native_jni_approved.table)[index]=expected;
        native_owner_links_free(native_jni_approved.sources[index]);native_jni_approved.sources[index]=NULL;
        InterlockedExchange(&native_jni_pending,1);
    }
    return expected;
}
static void native_jni_live_retired(NativeThread *state,const struct JNINativeInterface_ *table,unsigned char *retired){
    const uintptr_t *targets=(const uintptr_t*)table,*original=(const uintptr_t*)&native_original;
    memset(retired,0,NATIVE_JNI_SLOT_COUNT);state->jniObserving++;
    for(unsigned i=4;i<NATIVE_JNI_SLOT_COUNT;i++)
        if(!native_jni_controlled(i)&&targets[i]&&targets[i]!=original[i])retired[i]=native_caller_stopped((void*)targets[i])!=0;
    state->jniObserving--;
}
/* A function can acquire a real source relationship after publication. Refresh
 * outside the JNI directory; only an unchanged actual approved address can be
 * revoked by this result. Captured roots remain useful during safepoints. */
static void native_jni_revalidate(NativeThread *state){
    if(!state||state->jniObserving||native_tooling_restricted()||!native_jni_ready)return;
    struct JNINativeInterface_ snapshot;unsigned char retired[NATIVE_JNI_SLOT_COUNT];
    AcquireSRWLockShared(&native_jni_tables_gate);snapshot=native_jni_approved.table;ReleaseSRWLockShared(&native_jni_tables_gate);
    native_jni_live_retired(state,&snapshot,retired);
    int changed=0;for(unsigned i=4;i<NATIVE_JNI_SLOT_COUNT;i++)if(retired[i]){changed=1;break;}
    if(!changed)return;
    const uintptr_t *observed=(const uintptr_t*)&snapshot,*original=(const uintptr_t*)&native_original;
    AcquireSRWLockExclusive(&native_jni_tables_gate);uintptr_t *approved=(uintptr_t*)&native_jni_approved.table;
    for(unsigned i=4;i<NATIVE_JNI_SLOT_COUNT;i++)if(retired[i]){
        if(native_jni_publishing&&((const uintptr_t*)&native_jni_publishing->table)[i]==observed[i])native_jni_publishing->revoked[i]=1;
        if(approved[i]!=observed[i])continue;
        approved[i]=original[i];native_owner_links_free(native_jni_approved.sources[i]);native_jni_approved.sources[i]=NULL;
        InterlockedExchange(&native_jni_pending,1);
    }
    ReleaseSRWLockExclusive(&native_jni_tables_gate);
}
static void native_jni_overlay(struct JNINativeInterface_ *table){
    uintptr_t *incoming=(uintptr_t*)table;int retire=0;
    AcquireSRWLockShared(&native_jni_tables_gate);
    for(unsigned i=4;i<NATIVE_JNI_SLOT_COUNT;i++){
        uintptr_t expected;
        if(native_jni_expected_read_locked(i,incoming[i],&expected)){retire=1;break;}
        incoming[i]=expected;
    }
    ReleaseSRWLockShared(&native_jni_tables_gate);
    if(!retire)return;
    // Re-read every actual approval after changing gate mode. Only the local
    // copy has changed so far; no baseline or source lifetime crossed locks.
    AcquireSRWLockExclusive(&native_jni_tables_gate);
    for(unsigned i=4;i<NATIVE_JNI_SLOT_COUNT;i++)incoming[i]=native_jni_expected_locked(i,incoming[i]);
    ReleaseSRWLockExclusive(&native_jni_tables_gate);
}
/* The range lock pins the actual attachment's table lifetime through these
 * native-only writes. Each pointer changes atomically, as in HotSpot's own
 * copy_jni_function_table. A legal active unguarded hook remains approved; an
 * unapproved pointer or a retired hook returns to the saved VM original. */
static int native_jni_repair_locked(const struct JNINativeInterface_ *table,const struct JNINativeInterface_ *copy){
    if(!InterlockedCompareExchange(&native_guarded_ready,0,0)||!InterlockedCompareExchange(&native_jni_ready,0,0))return 1;
    const uintptr_t *incoming=(const uintptr_t*)copy;int valid=1;
    for(unsigned i=4;i<NATIVE_JNI_SLOT_COUNT;i++){
        uintptr_t expected=native_jni_expected_locked(i,incoming[i]);if(incoming[i]==expected)continue;
        InterlockedExchange(&native_jni_pending,1);
        void *volatile *slot=(void *volatile*)((uintptr_t)table+i*sizeof(void*));
        MEMORY_BASIC_INFORMATION region;DWORD previous=0,ignored=0;
        if(!VirtualQuery((const void*)slot,&region,sizeof(region))||region.State!=MEM_COMMIT
                ||(region.Protect&(PAGE_GUARD|PAGE_NOACCESS))){valid=0;continue;}
        int writable=(region.Protect&0xff)==PAGE_READWRITE||(region.Protect&0xff)==PAGE_WRITECOPY
                ||(region.Protect&0xff)==PAGE_EXECUTE_READWRITE||(region.Protect&0xff)==PAGE_EXECUTE_WRITECOPY;
        if(!writable&&!host_real_virtual_protect((void*)slot,sizeof(*slot),PAGE_READWRITE,&previous)){valid=0;continue;}
        void *actual=InterlockedCompareExchangePointer(slot,(void*)expected,(void*)incoming[i]);
        if(!writable&&!host_real_virtual_protect((void*)slot,sizeof(*slot),previous,&ignored))valid=0;
        /* Another guarded publisher is harmless. An unvalidated competing
         * writer does not become this repair's expected value. */
        if(actual!=(void*)incoming[i]&&actual!=(void*)expected
                &&native_jni_expected_locked(i,(uintptr_t)actual)!=(uintptr_t)actual)valid=0;
    }
    return valid;
}
/* Both registered ranges are owned by this state and only changed under the
 * JNI directory gate, including detach. A matching live snapshot needs no
 * private-directory publication, even during an all-thread table repair. */
static int native_jni_ranges_match(NativeThread *state,JNIEnv *env,const struct JNINativeInterface_ *table){
    return state->jniRange.linked&&state->jniRange.address==(const void*)env
            &&state->jniRange.length==sizeof(*env)&&state->jniTableRange.linked
            &&state->jniTableRange.address==(const void*)table&&state->jniTableRange.length==sizeof(*table);
}
static int native_jni_observe_locked(NativeThread *state,JNIEnv *env,int restricted){
    struct JNINativeInterface_ copy;const struct JNINativeInterface_ *table=NULL;
    if(!native_jni_snapshot(env,&table,&copy))return 0;
    int valid=native_jni_ranges_match(state,env,table);
    if(!valid){
        if(!native_private_write_lock(restricted,NATIVE_PRIVATE_JNI_REGISTER))return 0;
        if(state->jniRange.linked&&state->jniRange.address!=(const void*)env)native_private_unregister_locked(&state->jniRange);
        if(state->jniTableRange.linked&&state->jniTableRange.address!=(const void*)table)native_private_unregister_locked(&state->jniTableRange);
        valid=native_private_register_locked(&state->jniRange,env,sizeof(*env))
                &&native_private_register_locked(&state->jniTableRange,table,sizeof(*table));
        native_private_write_unlock();
    }
    if(!state->jniLinked){state->jniNext=native_jni_threads;native_jni_threads=state;state->jniLinked=1;}
    return native_jni_repair_locked(table,&copy)&&valid;
}
/* Every entry still reads the current slot and the complete live table. A
 * previously registered, unchanged attachment needs no directory mutation.
 * Hold the JNI reader gate while consulting approvals and retained ranges;
 * publishing, detach and repair retain their exclusive writer gate. */
static int native_jni_unchanged_locked(NativeThread *state,JNIEnv *env){
    struct JNINativeInterface_ copy;const struct JNINativeInterface_ *table=NULL;
    int valid=state->jniLinked&&native_jni_snapshot(env,&table,&copy)&&native_jni_ranges_match(state,env,table);
    if(valid&&InterlockedCompareExchange(&native_guarded_ready,0,0)&&InterlockedCompareExchange(&native_jni_ready,0,0)){
        const uintptr_t *incoming=(const uintptr_t*)&copy;
        for(unsigned i=4;i<NATIVE_JNI_SLOT_COUNT&&valid;i++){
            uintptr_t expected;valid=!native_jni_expected_read_locked(i,incoming[i],&expected)&&incoming[i]==expected;
        }
    }
    return valid;
}
static int native_jni_unchanged(NativeThread *state,JNIEnv *env,int restricted,int all){
    if(!native_bounded_read_lock(&native_jni_tables_gate,restricted))return 0;
    int valid=native_jni_unchanged_locked(state,env);
    if(all)for(NativeThread *other=native_jni_threads;valid&&other;other=other->jniNext){
        if(other==state||!other->jniRange.linked)continue;
        valid=native_jni_unchanged_locked(other,(JNIEnv*)other->jniRange.address);
    }
    ReleaseSRWLockShared(&native_jni_tables_gate);return valid;
}
static int native_jni_refresh(NativeThread *state,JNIEnv *env,int all){
    int restricted=native_tooling_restricted();
    if(native_jni_unchanged(state,env,restricted,all))return 1;
    if(restricted){
        if(!TryAcquireSRWLockExclusive(&native_jni_tables_gate))return 0;
    }else AcquireSRWLockExclusive(&native_jni_tables_gate);
    int valid=native_jni_observe_locked(state,env,restricted);
    if(all)for(NativeThread *other=native_jni_threads;other;other=other->jniNext){
        if(other==state||!other->jniRange.linked)continue;
        if(!native_jni_observe_locked(other,(JNIEnv*)other->jniRange.address,restricted))valid=0;
    }
    ReleaseSRWLockExclusive(&native_jni_tables_gate);return valid;
}
/* Observation is used inside source/output bookkeeping as well as on public
 * entries. It never makes a safepoint or calls an outside callback while a
 * caller may hold a domain lock. Explicit outer entries synchronize below. */
static int native_jni_checkpoint(NativeThread *state,JNIEnv *env,int all){
    DWORD error=GetLastError();int saved=errno;unsigned long dos=_doserrno;int valid=0;
    if(state&&env){
        if(state->jniObserving)valid=state->jniRange.linked&&state->jniRange.address==(const void*)env;
        else if(native_jni_current(env)){
            state->jniObserving++;valid=native_jni_refresh(state,env,all);state->jniObserving--;
        }
    }
    errno=saved;_doserrno=dos;SetLastError(error);return valid;
}
static void native_jni_thread_end(NativeThread *state){
    if(!state)return;
    /* An unlinked state is absent from every foreign all-thread walk. Only
     * its owning thread can first register it; empty pure-C/repeated teardown
     * therefore has nothing to pin or remove from either directory. */
    if(!state->jniLinked&&!state->jniTableRange.linked&&!state->jniRange.linked)return;
    DWORD error=GetLastError();int saved=errno;unsigned long dos=_doserrno;
    AcquireSRWLockExclusive(&native_jni_tables_gate);
    if(state->jniLinked){NativeThread **at=&native_jni_threads;while(*at&&*at!=state)at=&(*at)->jniNext;
        if(*at==state)*at=state->jniNext;state->jniNext=NULL;state->jniLinked=0;}
    ReleaseSRWLockExclusive(&native_jni_tables_gate);
    /* No foreign all-thread reader can retain this state after the unlink's
     * exclusive hold. Only its owner can start observation. Keep its private
     * ranges alive until actual removal, without pinning the JNI directory
     * behind another private writer; state storage is freed only afterward. */
    if(state->jniTableRange.linked||state->jniRange.linked){
        native_private_write_lock(0,NATIVE_PRIVATE_JNI_RETIRE);
        native_private_unregister_locked(&state->jniTableRange);native_private_unregister_locked(&state->jniRange);
        native_private_write_unlock();
    }
    errno=saved;_doserrno=dos;SetLastError(error);
}
typedef struct NativeJniProtection {void *address;SIZE_T length;DWORD previous;int changed;} NativeJniProtection;
static int native_jni_permissions_close(NativeJniProtection *regions,unsigned count){
    int valid=1;for(unsigned i=count;i>0;i--){NativeJniProtection *region=&regions[i-1];DWORD ignored=0;
        if(region->changed&&!host_real_virtual_protect(region->address,region->length,region->previous,&ignored))valid=0;}
    return valid;
}
static int native_jni_permissions_open(NativeJniProtection *regions,unsigned *count){
    uintptr_t at=(uintptr_t)native_jni_vm_table,last=at+sizeof(*native_jni_vm_table);*count=0;
    if(!at||last<at)return 0;
    while(at<last){MEMORY_BASIC_INFORMATION memory;
        if(*count==4||!VirtualQuery((void*)at,&memory,sizeof(memory))||memory.State!=MEM_COMMIT
                ||(memory.Protect&(PAGE_GUARD|PAGE_NOACCESS))||memory.RegionSize>UINTPTR_MAX-(uintptr_t)memory.BaseAddress)return 0;
        uintptr_t end=(uintptr_t)memory.BaseAddress+memory.RegionSize;if(end>last)end=last;if(end<=at)return 0;
        NativeJniProtection *region=&regions[(*count)++];region->address=(void*)at;region->length=end-at;region->changed=0;
        DWORD protection=memory.Protect&0xff;
        if(protection!=PAGE_READWRITE&&protection!=PAGE_WRITECOPY&&protection!=PAGE_EXECUTE_READWRITE&&protection!=PAGE_EXECUTE_WRITECOPY){
            if(!host_real_virtual_protect(region->address,region->length,PAGE_READWRITE,&region->previous))return 0;
            region->changed=1;
        }
        at=end;
    }
    return 1;
}
static jvmtiError native_jni_vm_set(jvmtiEnv *api,const struct jvmtiInterface_1_ *original,const jniNativeInterface *table,int *published){
    *published=0;
    if(!native_jni_vm_table){
        /* Keep the original legal publication path when no actual static
         * target was observed. Do not guess a VM address from a shadow/copy. */
        jvmtiError result=original->SetJNIFunctionTable(api,table);*published=result==JVMTI_ERROR_NONE;return result;
    }
    NativeJniProtection regions[4];unsigned count=0;
    /* An earlier raw writer may have restored the actual VM table to RO.
     * Serialize permission changes with in-place repairs, release the directory
     * before the safepoint, then restore every actual region on every exit. */
    AcquireSRWLockExclusive(&native_jni_tables_gate);
    int opened=native_jni_permissions_open(regions,&count);
    if(!opened){native_jni_permissions_close(regions,count);ReleaseSRWLockExclusive(&native_jni_tables_gate);return JVMTI_ERROR_NOT_AVAILABLE;}
    ReleaseSRWLockExclusive(&native_jni_tables_gate);
    jvmtiError result=original->SetJNIFunctionTable(api,table);
    *published=result==JVMTI_ERROR_NONE;
    AcquireSRWLockExclusive(&native_jni_tables_gate);
    int closed=native_jni_permissions_close(regions,count);ReleaseSRWLockExclusive(&native_jni_tables_gate);
    return closed?result:JVMTI_ERROR_INTERNAL;
}
/* This is a nonwaiting publisher hold, not a directory lock. JNI table copying
 * runs at a VM safepoint: a synchronous callback must be able to observe or
 * detach without waiting for the Java thread which initiated that safepoint. */
static int native_jni_sync_owned(NativeThread *state,JNIEnv *env){
    int valid=1;
    for(unsigned pass=0;pass<2&&InterlockedExchange(&native_jni_pending,0);pass++){
        struct JNINativeInterface_ copy;const struct JNINativeInterface_ *table=NULL;
        if(!native_jni_current(env)||!native_jni_snapshot(env,&table,&copy)||!native_jni_vm_original){valid=0;break;}
        native_jni_revalidate(state);native_jni_overlay(&copy);int published=0;
        if(native_jni_vm_set(native_ti,native_jni_vm_original,&copy,&published)!=JVMTI_ERROR_NONE){valid=0;break;}
        /* A VM may change the current table pointer during publication. Only
         * the actual returned attachment may establish its replacement range. */
        if(!native_jni_checkpoint(state,env,0)){valid=0;break;}
    }
    if(!valid)InterlockedExchange(&native_jni_pending,1);
    return valid;
}
static int native_jni_synchronize(NativeThread *state,JNIEnv *env){
    if(!state||state->jniSynchronizing||native_tooling_restricted()||!native_jni_ready
            ||InterlockedCompareExchange(&native_tooling_shutdown,0,0)
            ||!InterlockedCompareExchange(&native_jni_pending,0,0))return 1;
    if(InterlockedCompareExchange(&native_jni_mutating,1,0))return 1;
    DWORD error=GetLastError();int saved=errno;unsigned long dos=_doserrno;
    state->jniSynchronizing++;int valid=native_jni_sync_owned(state,env);state->jniSynchronizing--;
    InterlockedExchange(&native_jni_mutating,0);
    if(!valid)InterlockedIncrement(&native_tooling_failures);
    errno=saved;_doserrno=dos;SetLastError(error);return valid;
}
static jvmtiError native_jni_publish(NativeToolingEnv *record,JNIEnv *env,const jniNativeInterface *table){
    NativeThread *state=native_thread();if(!state)return JVMTI_ERROR_OUT_OF_MEMORY;
    if(InterlockedCompareExchange(&native_jni_mutating,1,0))return JVMTI_ERROR_NOT_AVAILABLE;
    state->jniSynchronizing++;
    NativeJniApproval candidate,previous;int committed=0,published=0;
    if(!native_jni_approval_prepare(state,table,&candidate)){
        native_jni_approval_release(&candidate);state->jniSynchronizing--;InterlockedExchange(&native_jni_mutating,0);return JVMTI_ERROR_NOT_AVAILABLE;
    }
    AcquireSRWLockExclusive(&native_jni_tables_gate);native_jni_publishing=&candidate;ReleaseSRWLockExclusive(&native_jni_tables_gate);
    /* Actual successful VM publication, including success followed by a page
     * restoration error, owns the new baseline. A rejected API call never does. */
    LONG pending=InterlockedExchange(&native_jni_pending,0);
    jvmtiError result=native_jni_vm_set(record->api,record->original,&candidate.table,&published);
    unsigned char retired[NATIVE_JNI_SLOT_COUNT];memset(retired,0,sizeof(retired));
    if(published)native_jni_live_retired(state,&candidate.table,retired);
    AcquireSRWLockExclusive(&native_jni_tables_gate);
    if(published){uintptr_t *proposed=(uintptr_t*)&candidate.table;const uintptr_t *original=(const uintptr_t*)&native_original;
        for(unsigned i=4;i<NATIVE_JNI_SLOT_COUNT;i++)if(!native_jni_controlled(i)
                &&(candidate.revoked[i]||retired[i]||native_owner_stopped(candidate.sources[i]))){
            proposed[i]=original[i];native_owner_links_free(candidate.sources[i]);candidate.sources[i]=NULL;candidate.revoked[i]=0;
            InterlockedExchange(&native_jni_pending,1);
        }
        previous=native_jni_approved;native_jni_approved=candidate;memset(candidate.sources,0,sizeof(candidate.sources));committed=1;
    }
    native_jni_publishing=NULL;ReleaseSRWLockExclusive(&native_jni_tables_gate);
    native_jni_approval_release(&candidate);if(committed)native_jni_approval_release(&previous);
    if(!published){if(pending||native_jni_ready)InterlockedExchange(&native_jni_pending,1);}
    if(native_jni_ready&&env){
        if(!native_jni_checkpoint(state,env,0)||!native_jni_sync_owned(state,env)){
            InterlockedExchange(&native_jni_pending,1);result=JVMTI_ERROR_INTERNAL;
        }
    }
    state->jniSynchronizing--;InterlockedExchange(&native_jni_mutating,0);return result;
}
static int native_jni_table_range(uintptr_t first,uintptr_t last,int restricted){
    if(!native_bounded_read_lock(&native_jni_tables_gate,restricted))return native_range_unknown("JNI_TABLES_BUSY");
    uintptr_t vmTable=(uintptr_t)native_jni_vm_table;
    int hit=vmTable&&sizeof(*native_jni_vm_table)-1<=UINTPTR_MAX-vmTable
            &&first<=vmTable+sizeof(*native_jni_vm_table)-1&&vmTable<=last,unknown=0;
    for(NativeThread *state=native_jni_threads;state&&!hit;state=state->jniNext){
        const struct JNINativeInterface_ *table=NULL;
        if(!state->jniRange.linked||!native_jni_read(state->jniRange.address,&table,sizeof(table))||!table){
            unknown=1;native_range_unknown("JNI_ENV_UNREADABLE");continue;}
        uintptr_t address=(uintptr_t)table;
        if(sizeof(*table)-1>UINTPTR_MAX-address){unknown=1;native_range_unknown("JNI_TABLE_OVERFLOW");continue;}
        hit=first<=address+sizeof(*table)-1&&address<=last;
    }
    ReleaseSRWLockShared(&native_jni_tables_gate);return hit?1:unknown?-1:0;
}
