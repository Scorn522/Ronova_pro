/* Only current JNIEnv values actually delivered by this VM enter this list.
 * The JNI slot and its complete live table are separate allocations. A JVMTI
 * GetJNIFunctionTable copy is neither allocation's identity or lifetime. */
static SRWLOCK native_jni_tables_gate=SRWLOCK_INIT;
static NativeThread *native_jni_threads;
static const struct jvmtiInterface_1_ *native_jni_vm_original;
static const struct JNINativeInterface_ *native_jni_vm_table;
static volatile LONG native_jni_mutating,native_jni_pending;

static int native_jni_read(const void *address,void *copy,SIZE_T length){
    SIZE_T read=0;return host_real_read_process_memory(GetCurrentProcess(),address,copy,length,&read)&&read==length;
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
    if(vm&&native_image((void*)table)==vm)native_jni_vm_table=table;
    return 1;
}
static int native_jni_controlled(unsigned index){
    const uintptr_t *original=(const uintptr_t*)&native_original,*guarded=(const uintptr_t*)&native_guarded;
    return guarded[index]!=original[index];
}
static void native_jni_overlay(struct JNINativeInterface_ *table){
    uintptr_t *incoming=(uintptr_t*)table;const uintptr_t *guarded=(const uintptr_t*)&native_guarded;
    for(unsigned i=0;i<sizeof(*table)/sizeof(uintptr_t);i++)if(native_jni_controlled(i))incoming[i]=guarded[i];
}
/* The range lock pins the actual attachment's table lifetime through these
 * native-only writes. Each pointer changes atomically, as in HotSpot's own
 * copy_jni_function_table; an unrelated slot is never written here. */
static int native_jni_repair_locked(const struct JNINativeInterface_ *table,const struct JNINativeInterface_ *copy){
    if(!InterlockedCompareExchange(&native_guarded_ready,0,0)||!InterlockedCompareExchange(&native_jni_ready,0,0))return 1;
    const uintptr_t *incoming=(const uintptr_t*)copy,*guarded=(const uintptr_t*)&native_guarded;int valid=1;
    for(unsigned i=0;i<sizeof(*copy)/sizeof(uintptr_t);i++){
        if(!native_jni_controlled(i)||incoming[i]==guarded[i])continue;
        InterlockedExchange(&native_jni_pending,1);
        void *volatile *slot=(void *volatile*)((uintptr_t)table+i*sizeof(void*));
        MEMORY_BASIC_INFORMATION region;DWORD previous=0,ignored=0;
        if(!VirtualQuery((const void*)slot,&region,sizeof(region))||region.State!=MEM_COMMIT
                ||(region.Protect&(PAGE_GUARD|PAGE_NOACCESS))){valid=0;continue;}
        int writable=(region.Protect&0xff)==PAGE_READWRITE||(region.Protect&0xff)==PAGE_WRITECOPY
                ||(region.Protect&0xff)==PAGE_EXECUTE_READWRITE||(region.Protect&0xff)==PAGE_EXECUTE_WRITECOPY;
        if(!writable&&!host_real_virtual_protect((void*)slot,sizeof(*slot),PAGE_READWRITE,&previous)){valid=0;continue;}
        void *actual=InterlockedCompareExchangePointer(slot,(void*)guarded[i],(void*)incoming[i]);
        if(!writable&&!host_real_virtual_protect((void*)slot,sizeof(*slot),previous,&ignored))valid=0;
        /* Another guarded publisher is harmless. An unvalidated competing
         * writer does not become this repair's expected value. */
        if(actual!=(void*)incoming[i]&&actual!=(void*)guarded[i])valid=0;
    }
    return valid;
}
static int native_jni_observe_locked(NativeThread *state,JNIEnv *env,int restricted){
    struct JNINativeInterface_ copy;const struct JNINativeInterface_ *table=NULL;
    if(!native_jni_snapshot(env,&table,&copy))return 0;
    if(restricted){if(!TryAcquireSRWLockExclusive(&native_private_gate))return 0;}
    else AcquireSRWLockExclusive(&native_private_gate);
    if(state->jniRange.linked&&state->jniRange.address!=(const void*)env)native_private_unregister_locked(&state->jniRange);
    if(state->jniTableRange.linked&&state->jniTableRange.address!=(const void*)table)native_private_unregister_locked(&state->jniTableRange);
    int valid=native_private_register_locked(&state->jniRange,env,sizeof(*env))
            &&native_private_register_locked(&state->jniTableRange,table,sizeof(*table));
    ReleaseSRWLockExclusive(&native_private_gate);
    if(!state->jniLinked){state->jniNext=native_jni_threads;native_jni_threads=state;state->jniLinked=1;}
    return native_jni_repair_locked(table,&copy)&&valid;
}
static int native_jni_refresh(NativeThread *state,JNIEnv *env,int all){
    int restricted=native_tooling_restricted();
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
    if(!state)return;DWORD error=GetLastError();int saved=errno;unsigned long dos=_doserrno;
    AcquireSRWLockExclusive(&native_jni_tables_gate);
    if(state->jniLinked){NativeThread **at=&native_jni_threads;while(*at&&*at!=state)at=&(*at)->jniNext;
        if(*at==state)*at=state->jniNext;state->jniNext=NULL;state->jniLinked=0;}
    AcquireSRWLockExclusive(&native_private_gate);
    native_private_unregister_locked(&state->jniTableRange);native_private_unregister_locked(&state->jniRange);
    ReleaseSRWLockExclusive(&native_private_gate);ReleaseSRWLockExclusive(&native_jni_tables_gate);
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
static jvmtiError native_jni_vm_set(jvmtiEnv *api,const struct jvmtiInterface_1_ *original,const jniNativeInterface *table){
    if(!native_jni_vm_table){
        /* Keep the original legal publication path when no actual static
         * target was observed. Do not guess a VM address from a shadow/copy. */
        return original->SetJNIFunctionTable(api,table);
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
        native_jni_overlay(&copy);
        if(native_jni_vm_set(native_ti,native_jni_vm_original,&copy)!=JVMTI_ERROR_NONE){valid=0;break;}
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
    /* This exact immutable table already passed the guarded-slot check. Its
     * publication satisfies older repairs while retaining the caller's legal
     * changes to every unguarded slot. Later repairs set pending again. */
    LONG pending=InterlockedExchange(&native_jni_pending,0);
    jvmtiError result=native_jni_vm_set(record->api,record->original,table);
    if(result!=JVMTI_ERROR_NONE){if(pending)InterlockedExchange(&native_jni_pending,1);}
    else if(native_jni_ready&&env){
        if(!native_jni_checkpoint(state,env,0)||!native_jni_sync_owned(state,env)){
            InterlockedExchange(&native_jni_pending,1);result=JVMTI_ERROR_INTERNAL;
        }
    }
    state->jniSynchronizing--;InterlockedExchange(&native_jni_mutating,0);return result;
}
static int native_jni_table_range(uintptr_t first,uintptr_t last,int restricted){
    if(restricted){if(!TryAcquireSRWLockShared(&native_jni_tables_gate))return native_range_unknown("JNI_TABLES_BUSY");}
    else AcquireSRWLockShared(&native_jni_tables_gate);
    int hit=0,unknown=0;
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
