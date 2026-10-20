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
/* Only published owners and links are supplied here. Their enclosing retained
 * Binding/environment/slot keeps these exact nodes alive after retirement. */
static int native_control_owner_links(uintptr_t first,uintptr_t last,OwnerLink *links){
    for(OwnerLink *link=links;link;link=link->next)
        if(native_control_overlaps(first,last,link,sizeof(*link))
                ||native_control_overlaps(first,last,link->owner,sizeof(*link->owner)))return 1;
    return 0;
}
static int native_control_bindings_range(uintptr_t first,uintptr_t last,int restricted){
    if(!native_control_shared(&native_records,restricted))return -1;
    int hit=0;
    for(Owner *owner=native_owners;owner&&!hit;owner=owner->next)
        hit=native_control_overlaps(first,last,owner,sizeof(*owner));
    for(Binding *binding=native_bindings;binding&&!hit;binding=binding->next){
        hit=native_control_overlaps(first,last,binding,sizeof(*binding))
                ||native_control_owner_links(first,last,binding->owners);
        /* native_entry places its unwind tail at capacity-16. Published
         * bindings and their RtlAddFunctionTable records are never reclaimed. */
        if(!hit&&binding->entry)
            hit=native_control_overlaps(first,last,binding->entry,(SIZE_T)binding->unwind.UnwindData+16);
    }
    ReleaseSRWLockShared(&native_records);return hit;
}
static int native_control_tooling_range(uintptr_t first,uintptr_t last,int restricted){
    if(!native_control_shared(&native_tooling_records,restricted))return -1;
    int hit=0,unknown=0;
    NativeToolingEnv *environments=native_tooling_environments;
    for(NativeToolingEnv *record=environments;record&&!hit;record=record->next){
        hit=native_control_overlaps(first,last,record,sizeof(*record))
                ||native_control_overlaps(first,last,record->shadow,sizeof(*record->shadow))
                ||native_control_owner_links(first,last,record->owners);
        for(NativeToolingVersion *version=record->version;version&&!hit;version=version->previous)
            hit=native_control_overlaps(first,last,version,sizeof(*version));
        for(NativeToolingExtension *extension=record->extensions;extension&&!hit;extension=extension->next)
            hit=native_control_overlaps(first,last,extension,sizeof(*extension))
                    ||native_control_owner_links(first,last,extension->owners);
    }
    for(NativeToolingSlot *slot=native_tooling_slots;slot&&!hit;slot=slot->next){
        hit=native_control_overlaps(first,last,slot,sizeof(*slot))
                ||native_control_owner_links(first,last,slot->owners);
        /* Agent entries are in the retained DLL; synchronous heap anchors have
         * no generated entry. Ordinary and extension thunks allocate 4096. */
        if(!hit&&slot->entry&&!slot->agent)
            hit=native_control_overlaps(first,last,slot->entry,4096);
    }
    ReleaseSRWLockShared(&native_tooling_records);
    if(hit)return 1;
    /* Published environments/next links remain allocated. Release the records
     * lock before mutation: disposal takes mutation before records, and frees
     * the VM-owned api before it can publish disposed. Only an overlapping api
     * candidate needs this hold; never read through that outside pointer. */
    for(NativeToolingEnv *record=environments;record;record=record->next){
        if(!native_control_overlaps(first,last,record->api,native_platform_env_span(record->api)))continue;
        if(InterlockedCompareExchange(&native_tooling_shutdown,0,0)
                ||InterlockedCompareExchange(&record->disposed,0,0))continue;
        if(!TryAcquireSRWLockShared(&record->mutation)){
            if(!InterlockedCompareExchange(&native_tooling_shutdown,0,0)
                    &&!InterlockedCompareExchange(&record->disposed,0,0))unknown=1;
            continue;
        }
        if(!InterlockedCompareExchange(&native_tooling_shutdown,0,0)
                &&!InterlockedCompareExchange(&record->disposed,0,0)){
            if(InterlockedCompareExchange(&record->closing,0,0))unknown=1;
            else hit=1;
        }
        ReleaseSRWLockShared(&record->mutation);
        if(hit)return 1;
    }
    return unknown?-1:0;
}
static int native_control_heap_range(uintptr_t first,uintptr_t last,int restricted){
    if(!native_control_shared(&native_tooling_heap_records,restricted))return -1;
    int hit=0;
    for(NativeToolingHeapEnvironment *record=native_tooling_heap_environments;record&&!hit;record=record->next){
        hit=native_control_overlaps(first,last,record,sizeof(*record));
        NativeToolingHeapCall *walking=(NativeToolingHeapCall*)InterlockedCompareExchangePointer(
                (void *volatile*)&record->walking,NULL,NULL);
        if(!hit&&walking)hit=native_control_overlaps(first,last,walking,sizeof(*walking));
    }
    /* The synchronous exit clears walking under the exclusive lock before its
     * stack or buffers can be released. Do not retain it outside this query. */
    ReleaseSRWLockShared(&native_tooling_heap_records);return hit;
}
static int native_control_range(uintptr_t first,uintptr_t last,int restricted){
    if(first>last)return -1;
    /* Re-read only retained live JNIEnv slots. This also covers a table
     * pointer changed since that thread's last JNI entry, without a VM scan. */
    int jni=native_jni_table_range(first,last,restricted);
    if(jni>0)return 1;
    int platform=native_platform_range(first,last,restricted);
    if(platform>0)return 1;
    /* Consult the same exact allocation directory used by JNI/JVMTI result
     * publication. Release its leaf lock before taking any domain lock below. */
    if(!native_control_shared(&native_private_gate,restricted))return -1;
    int privateHit=native_private_range_locked(first,last);
    ReleaseSRWLockShared(&native_private_gate);
    if(privateHit)return privateHit;
    int unknown=jni<0||platform<0;HMODULE image=native_control_image();
    uintptr_t begin=native_tooling_image_begin,end=native_tooling_image_end;
    if(image&&begin==(uintptr_t)image&&end>begin){
        if(first<end&&begin<=last)return 1;
    }else unknown=1;
    const struct JNIInvokeInterface_ *shadow=native_vm_shadow;
    if(native_control_overlaps(first,last,shadow,sizeof(*shadow)))return 1;
    if(shadow&&InterlockedCompareExchange(&native_tooling_ready,0,0)
            &&!InterlockedCompareExchange(&native_tooling_shutdown,0,0)
            &&native_control_overlaps(first,last,native_vm,sizeof(*native_vm)))return 1;
    int hit=native_control_bindings_range(first,last,restricted);
    if(hit>0)return 1;if(hit<0)unknown=1;
    hit=native_control_tooling_range(first,last,restricted);
    if(hit>0)return 1;if(hit<0)unknown=1;
    hit=native_control_heap_range(first,last,restricted);
    if(hit>0)return 1;if(hit<0)unknown=1;
    return unknown?-1:0;
}
