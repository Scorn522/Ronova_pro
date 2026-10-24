/* Actual retained control allocations and separately anchored platform
 * structures. JNI table copies do not establish an environment identity.
 * Results are hit / clear / unknown. */
static int native_control_overlaps(uintptr_t first,uintptr_t last,const void *allocation,SIZE_T size){
    if(!allocation||!size)return 0;
    uintptr_t base=(uintptr_t)allocation,span=(uintptr_t)(size-1);
    uintptr_t end=span>UINTPTR_MAX-base?UINTPTR_MAX:base+span;
    return first<=end&&base<=last;
}
static int native_control_shared(SRWLOCK *lock,int restricted){
    if(restricted)return TryAcquireSRWLockShared(lock)!=0;
    AcquireSRWLockShared(lock);return 1;
}
static int native_control_tooling_range(uintptr_t first,uintptr_t last){
    int hit=0,unknown=0;
    /* Publication initializes api/next before its release store; published
     * records and links are retained even after disposal. Their private
     * allocations, versions, source nodes, shadows and generated entries are
     * already in native_private_index. Only the VM-owned environment's full
     * platform span needs this separate live query (apiRange covers its slot).
     * Disposal takes mutation before records and frees api before publishing
     * disposed. Only an overlapping candidate needs mutation; never read
     * through that outside pointer or hold a directory lock here. */
    NativeToolingEnv *environments=__atomic_load_n(&native_tooling_environments,__ATOMIC_ACQUIRE);
    for(NativeToolingEnv *record=environments;record;record=record->next){
        if(!native_control_overlaps(first,last,record->api,native_platform_env_span(record->api)))continue;
        if(InterlockedCompareExchange(&native_tooling_shutdown,0,0)
                ||InterlockedCompareExchange(&record->disposed,0,0))continue;
        if(!TryAcquireSRWLockShared(&record->mutation)){
            if(!InterlockedCompareExchange(&native_tooling_shutdown,0,0)
                    &&!InterlockedCompareExchange(&record->disposed,0,0)){unknown=1;native_range_unknown("TOOLING_ENV_BUSY");}
            continue;
        }
        if(!InterlockedCompareExchange(&native_tooling_shutdown,0,0)
                &&!InterlockedCompareExchange(&record->disposed,0,0)){
            if(InterlockedCompareExchange(&record->closing,0,0)){unknown=1;native_range_unknown("TOOLING_ENV_CLOSING");}
            else hit=1;
        }
        ReleaseSRWLockShared(&record->mutation);
        if(hit)return 1;
    }
    return unknown?-1:0;
}
/* Deferral is only for an effect that independently checks this exact private
 * directory while holding its gate through the original API. Domain/VM checks
 * still run here, outside that leaf gate. Standalone queries never defer. */
static int native_control_range_query(uintptr_t first,uintptr_t last,int restricted,int deferPrivate){
    if(first>last)return native_range_unknown("ADDRESS_OVERFLOW");
    /* Re-read only retained live JNIEnv slots. This also covers a table
     * pointer changed since that thread's last JNI entry, without a VM scan. */
    int jni=native_jni_table_range(first,last,restricted);
    if(jni>0)return 1;
    int platform=native_platform_range(first,last,restricted);
    if(platform>0)return 1;
    /* The redirected CRT/VirtualAlloc paths register before returning and
     * unregister only on actual release. This covers owners, bindings, Host
     * records, tooling records and generated entries, including retained
     * history. Heap calls register their stack span before publishing walking;
     * Host IAT slots register before publication and track image retirement.
     * Query these exact ranges once, not again through each domain's lists.
     * Release the leaf gate before querying the live VM environment below. */
    if(!deferPrivate){
        if(!native_private_read_lock(restricted))return native_range_unknown("PRIVATE_RANGES_BUSY");
        int privateHit=native_private_range_locked(first,last);
        ReleaseSRWLockShared(&native_private_gate);
        if(privateHit)return privateHit;
    }
    int unknown=jni<0||platform<0;HMODULE image=native_control_image();
    uintptr_t begin=native_tooling_image_begin,end=native_tooling_image_end;
    if(image&&begin==(uintptr_t)image&&end>begin){
        if(first<end&&begin<=last)return 1;
    }else{unknown=1;native_range_unknown("CONTROL_IMAGE_UNAVAILABLE");}
    const struct JNIInvokeInterface_ *shadow=native_vm_shadow;
    if(native_control_overlaps(first,last,shadow,sizeof(*shadow)))return 1;
    if(shadow&&InterlockedCompareExchange(&native_tooling_ready,0,0)
            &&!InterlockedCompareExchange(&native_tooling_shutdown,0,0)
            &&native_control_overlaps(first,last,native_vm,sizeof(*native_vm)))return 1;
    int hit=native_control_tooling_range(first,last);
    if(hit>0)return 1;if(hit<0)unknown=1;
    return unknown?-1:0;
}
static int native_control_range(uintptr_t first,uintptr_t last,int restricted){
    return native_control_range_query(first,last,restricted,0);
}
static void native_range_refuse(JNIEnv *env,int restricted){
    NativeThread *state=native_range_state();char message[192];
    snprintf(message,sizeof(message),"NATIVE_CONTROL_RANGE_UNAVAILABLE:%s:restricted=%d:loaderQuery=%s",
            state&&state->controlRangeFailure?state->controlRangeFailure:"UNSPECIFIED",restricted,
            __atomic_load_n(&host_loader_callout_query,__ATOMIC_ACQUIRE)?"ready":"uninitialized");
    native_refuse(env,message);
}
