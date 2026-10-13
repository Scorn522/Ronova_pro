/* Synchronous JVMTI heap dispatch. Included by bindings.c after the tooling
 * operation policy. Heap callbacks cannot call JNI, Java, GetTag/SetTag, or
 * acquire a lock whose owner can be stalled by the VM's heap walk.
 * JDK 17: https://docs.oracle.com/en/java/javase/17/docs/specs/jvmti.html#heapCallbacks
 */

enum {
    TOOLING_HEAP_ITERATION,TOOLING_HEAP_REFERENCE,TOOLING_HEAP_PRIMITIVE,
    TOOLING_HEAP_ARRAY,TOOLING_HEAP_STRING,TOOLING_HEAP_ROOT,
    TOOLING_HEAP_STACK,TOOLING_HEAP_OBJECT,TOOLING_HEAP_LEGACY_REFERENCE,
    TOOLING_HEAP_CALLBACK_COUNT
};

typedef struct NativeToolingHeapSlot {
    void *callback;OwnerLink *owners;jobjectArray sourceLease;jweak origin;
    HMODULE pin;NativeToolingSlot *retirement;int unknown,restricted,published;volatile LONG active;
} NativeToolingHeapSlot;

typedef struct NativeToolingHeapInvocation {
    NativeThread *thread,*previousThread;NativeToolingScope scope;
    NativeToolingEnv *previousEnvironment;
    NativeToolingHeapSlot *slot;DWORD lastError;int installed;
} NativeToolingHeapInvocation;

typedef struct NativeToolingHeapCall {
    NativeToolingEnv *environment;NativeToolingHeapSlot slots[TOOLING_HEAP_CALLBACK_COUNT];
    jvmtiHeapCallbacks callbacks;const void *data;NativeThread callbackThread;
    struct NativeToolingHeapEnvironment *tagState;
    struct NativeToolingHeapObjectSnapshot *objects;size_t objectCount;
    size_t *tags;size_t tagCapacity;
    LONG64 tagEpoch,policyEpoch;int claimed;
    volatile LONG refusal,policyChanged;int restricted,objectsPublished;
} NativeToolingHeapCall;

typedef struct NativeToolingHeapObject {
    jweak object;struct NativeToolingHeapObject *next;
} NativeToolingHeapObject;

typedef struct NativeToolingHeapEnvironment {
    NativeToolingEnv *environment;NativeToolingHeapObject *objects;
    volatile LONG64 epoch;volatile LONG mutations;
    NativeToolingHeapCall *volatile walking;struct NativeToolingHeapEnvironment *next;
} NativeToolingHeapEnvironment;

typedef struct NativeToolingHeapObjectSnapshot {
    jobject object;jlong tag;unsigned eligible;int unique,valid,reported,matched;
} NativeToolingHeapObjectSnapshot;

static SRWLOCK native_tooling_heap_records=SRWLOCK_INIT;
static NativeToolingHeapEnvironment *native_tooling_heap_environments;
static volatile LONG64 native_tooling_heap_policy_epoch;
static volatile LONG native_tooling_heap_policy_active;

/* The caller is an actual source/control publication, not a heap callback.
 * A changed source or control role revokes a prepared admission before the
 * next pointer is handed to the outside callback.
 */
static void native_tooling_heap_policy_changed(void){InterlockedIncrement64(&native_tooling_heap_policy_epoch);}

JNIEXPORT void JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_heapPolicyChanged0(JNIEnv *env,jclass type,jobject object){
    if(!InterlockedCompareExchange(&native_tooling_heap_policy_active,0,0))return;
    /* The Java notification wrapper authenticates the actual ledger/control
     * publication. Its private native notification can only revoke admission.
     */
    if(!native_controller||!native_original.IsSameObject(env,type,native_controller))return;
    if(!object){native_tooling_heap_policy_changed();return;}
    AcquireSRWLockShared(&native_tooling_heap_records);
    for(NativeToolingHeapEnvironment *entry=native_tooling_heap_environments;entry;entry=entry->next){
        NativeToolingHeapCall *call=(NativeToolingHeapCall*)InterlockedCompareExchangePointer((void *volatile*)&entry->walking,NULL,NULL);
        if(!call||!call->restricted)continue;
        if(!call->objectsPublished){InterlockedExchange(&call->policyChanged,1);continue;}
        for(size_t i=0;i<call->objectCount;i++)if(native_original.IsSameObject(env,object,call->objects[i].object)){
            InterlockedExchange(&call->policyChanged,1);break;
        }
    }
    ReleaseSRWLockShared(&native_tooling_heap_records);
}

static NativeToolingHeapEnvironment *native_tooling_heap_environment(NativeToolingEnv *record,int create){
    NativeToolingHeapEnvironment *found=NULL;AcquireSRWLockExclusive(&native_tooling_heap_records);
    for(NativeToolingHeapEnvironment *entry=native_tooling_heap_environments;entry;entry=entry->next)
        if(entry->environment==record){found=entry;break;}
    if(!found&&create){found=(NativeToolingHeapEnvironment*)calloc(1,sizeof(*found));
        if(found){found->environment=record;found->next=native_tooling_heap_environments;native_tooling_heap_environments=found;InterlockedExchange(&record->heapTracked,1);}}
    ReleaseSRWLockExclusive(&native_tooling_heap_records);return found;
}

static int native_tooling_heap_environment_pending(NativeToolingEnv *record){return InterlockedCompareExchange(&record->heapTracked,0,0)!=0;}

/* Only a successful, observed SetTag carrying the exact jobject creates a
 * candidate. The numeric label itself never creates an object or its owners.
 */
static int native_tooling_heap_remember(JNIEnv *env,NativeToolingHeapEnvironment *state,jobject object){
    if(!env||!object)return 0;NativeToolingHeapObject *added=(NativeToolingHeapObject*)calloc(1,sizeof(*added));
    if(!added)return 0;added->object=native_original.NewWeakGlobalRef(env,object);
    if(!added->object){free(added);return 0;}
    int found=0;AcquireSRWLockExclusive(&native_tooling_heap_records);
    for(NativeToolingHeapObject *entry=state->objects;entry;entry=entry->next)
        if(native_original.IsSameObject(env,entry->object,object)){found=1;break;}
    if(!found){added->next=state->objects;state->objects=added;added=NULL;}
    ReleaseSRWLockExclusive(&native_tooling_heap_records);
    if(added){native_original.DeleteWeakGlobalRef(env,added->object);free(added);}return 1;
}

/* Disposal is performed only after the environment's ordinary uses/active
 * callbacks have drained. The retirement worker supplies an attached JNIEnv.
 */
static void native_tooling_heap_environment_release(JNIEnv *env,NativeToolingEnv *record){
    if(!env||!InterlockedCompareExchange(&record->disposed,0,0))return;
    NativeToolingHeapEnvironment *retired=NULL;AcquireSRWLockExclusive(&native_tooling_heap_records);
    NativeToolingHeapEnvironment **link=&native_tooling_heap_environments;
    while(*link){NativeToolingHeapEnvironment *entry=*link;
        if(entry->environment==record&&!entry->walking&&!entry->mutations){*link=entry->next;retired=entry;InterlockedExchange(&record->heapTracked,0);break;}
        link=&entry->next;
    }
    ReleaseSRWLockExclusive(&native_tooling_heap_records);
    if(retired){NativeToolingHeapObject *entry=retired->objects;
        while(entry){NativeToolingHeapObject *next=entry->next;native_original.DeleteWeakGlobalRef(env,entry->object);free(entry);entry=next;}
        free(retired);
    }
}

static int native_tooling_heap_current_callback(void){
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    return state&&state->heapCallback;
}

static jvmtiError native_tooling_heap_target_query(JNIEnv *env,jobject object,jobjectArray sources){
    if(!env||!native_jni_ready||!native_tooling_heap_target)return JVMTI_ERROR_NOT_AVAILABLE;
    if(native_original.ExceptionCheck(env))return JVMTI_ERROR_ACCESS_DENIED;
    NativeThread *state=native_thread();if(!state)return JVMTI_ERROR_OUT_OF_MEMORY;
    if(!sources)return JVMTI_ERROR_OUT_OF_MEMORY;
    state->control++;jboolean allowed=native_original.CallStaticBooleanMethod(env,native_controls,native_tooling_heap_target,object,sources);state->control--;
    if(native_original.ExceptionCheck(env)){native_original.ExceptionClear(env);return JVMTI_ERROR_ACCESS_DENIED;}
    return allowed?JVMTI_ERROR_NONE:JVMTI_ERROR_ACCESS_DENIED;
}

static jvmtiError native_tooling_heap_target_gate(JNIEnv *env,jobject object,OwnerLink *owners,int unknown){
    if(!env)return JVMTI_ERROR_NOT_AVAILABLE;
    if(unknown||native_owner_stopped(owners))return JVMTI_ERROR_ACCESS_DENIED;
    jobjectArray sources=native_tooling_sources(env,owners,unknown);
    jvmtiError result=native_tooling_heap_target_query(env,object,sources);
    if(sources)native_original.DeleteLocalRef(env,sources);return result;
}

static jvmtiError JNICALL native_tooling_SetTag(jvmtiEnv *api,jobject object,jlong tag){
    if(native_tooling_heap_current_callback())return native_tooling_error(JVMTI_ERROR_NOT_AVAILABLE);
    NativeToolingEnv *record=native_tooling_environment(api);if(!record)return JVMTI_ERROR_INVALID_ENVIRONMENT;
    TOOLING_USE(record);
    void *caller=__builtin_return_address(0);JNIEnv *env=native_tooling_jni();
    if(record->own&&!native_tooling_internal(caller)||native_tooling_stopped(env,caller))return native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);
    OwnerLink *owners=NULL;int unknown=0;
    if(!native_tooling_capture(env,caller,&owners,NULL,&unknown)){native_owner_links_free(owners);return native_tooling_error(JVMTI_ERROR_ACCESS_DENIED);}
    jvmtiError result=JVMTI_ERROR_NONE;
    if(native_has_producer(owners))result=native_tooling_heap_target_gate(env,object,owners,unknown);
    native_owner_links_free(owners);if(result!=JVMTI_ERROR_NONE)return native_tooling_error(result);
    NativeToolingHeapEnvironment *state=native_tooling_heap_environment(record,1);
    if(!state)return JVMTI_ERROR_OUT_OF_MEMORY;
    InterlockedIncrement(&state->mutations);InterlockedIncrement64(&state->epoch);
    result=record->original->SetTag(api,object,tag);
    if(result==JVMTI_ERROR_NONE&&tag&&env&&native_original.NewWeakGlobalRef&&!native_tooling_heap_remember(env,state,object)){
        /* The VM write already occurred. Failure to retain an exact candidate
         * cannot turn it into a failed/rolled-back SetTag or grant permission.
         */
        InterlockedIncrement(&native_tooling_failures);
        if(native_original.ExceptionCheck(env))native_original.ExceptionClear(env);
    }
    InterlockedIncrement64(&state->epoch);InterlockedDecrement(&state->mutations);return result;
}

static void native_tooling_heap_refuse(NativeToolingHeapCall *call,jvmtiError error){
    InterlockedCompareExchange(&call->refusal,(LONG)error,JVMTI_ERROR_NONE);
}

/* The invocation owns its fallback storage until the outside callback returns.
 * TlsSetValue does not attach the VM's internal heap thread or allocate a Java
 * Thread. All captured weak/strong references remain owned by the outer call.
 */
static int native_tooling_heap_callback_begin(NativeToolingHeapCall *call,unsigned index,NativeToolingHeapInvocation *invocation){
    memset(invocation,0,sizeof(*invocation));invocation->lastError=GetLastError();
    NativeToolingHeapSlot *slot=&call->slots[index];NativeToolingEnv *record=call->environment;
    if(!slot->callback||InterlockedCompareExchange(&call->refusal,0,0))return 0;
    if(InterlockedCompareExchange(&native_tooling_shutdown,0,0)
            ||slot->published&&!InterlockedCompareExchange(&slot->retirement->admitted,0,0)
            ||InterlockedCompareExchange(&record->closing,0,0)
            ||InterlockedCompareExchange(&record->disposed,0,0)
            ||native_owner_stopped(slot->owners)){
        native_tooling_heap_refuse(call,JVMTI_ERROR_ACCESS_DENIED);return 0;
    }
    if(native_tls==TLS_OUT_OF_INDEXES){native_tooling_heap_refuse(call,JVMTI_ERROR_NOT_AVAILABLE);return 0;}
    invocation->previousThread=(NativeThread*)TlsGetValue(native_tls);
    invocation->thread=invocation->previousThread;
    if(!invocation->thread){
        invocation->thread=&call->callbackThread;
        if(!TlsSetValue(native_tls,invocation->thread)){
            invocation->thread=NULL;native_tooling_heap_refuse(call,JVMTI_ERROR_OUT_OF_MEMORY);return 0;
        }
        invocation->installed=1;
    }
    NativeThread *state=invocation->thread;
    invocation->scope=(NativeToolingScope){slot->owners,slot->origin,slot->unknown,state->depth,state->tooling};
    invocation->previousEnvironment=state->toolingCallbackEnvironment;state->toolingCallbackEnvironment=record;
    state->tooling=&invocation->scope;state->heapCallback++;invocation->slot=slot;InterlockedIncrement(&slot->active);
    SetLastError(invocation->lastError);return 1;
}

static void native_tooling_heap_callback_end(NativeToolingHeapInvocation *invocation){
    if(!invocation->slot)return;DWORD lastError=GetLastError();
    invocation->thread->toolingCallbackEnvironment=invocation->previousEnvironment;
    invocation->thread->heapCallback--;invocation->thread->tooling=invocation->scope.previous;
    InterlockedDecrement(&invocation->slot->active);
    if(invocation->installed)TlsSetValue(native_tls,invocation->previousThread);
    invocation->slot=NULL;SetLastError(lastError);
}

static void native_tooling_heap_release(JNIEnv *env,NativeToolingHeapCall *call){
    /* This runs only after the synchronous VM function has returned. No VM
     * dispatcher can retain a heap callback supplied to a completed call.
     */
    if(call->claimed){
        AcquireSRWLockExclusive(&native_tooling_heap_records);
        InterlockedIncrement64(&call->tagState->epoch);
        InterlockedCompareExchangePointer((void *volatile*)&call->tagState->walking,NULL,call);
        if(call->restricted)InterlockedDecrement(&native_tooling_heap_policy_active);
        call->claimed=0;ReleaseSRWLockExclusive(&native_tooling_heap_records);
    }
    for(size_t i=0;i<call->objectCount;i++)if(call->objects[i].object)native_original.DeleteGlobalRef(env,call->objects[i].object);
    free(call->objects);free(call->tags);call->objects=NULL;call->tags=NULL;call->objectCount=call->tagCapacity=0;
    for(unsigned i=0;i<TOOLING_HEAP_CALLBACK_COUNT;i++){
        NativeToolingHeapSlot *slot=&call->slots[i];
        NativeToolingSlot *anchor=slot->published?slot->retirement:NULL;
        if(anchor){
            /* Closing admission cannot release a synchronous call's image or
             * provenance while active=1 still covers its exit code below.
             */
            AcquireSRWLockExclusive(&native_tooling_records);InterlockedExchange(&anchor->admitted,0);ReleaseSRWLockExclusive(&native_tooling_records);
        }
        int released=1;
        if(slot->pin){
            NativeThread *state=native_thread();NativeToolingScope scope;
            if(state){scope=(NativeToolingScope){slot->owners,slot->origin,slot->unknown,state->depth,state->tooling};state->tooling=&scope;}
            released=FreeLibrary(slot->pin);
            if(state)state->tooling=scope.previous;
            if(!released){
                /* Reuse the existing delayed-release worker on an actual
                 * unload failure. Retain its exact sources and image lease.
                 */
                NativeToolingSlot *retirement=slot->retirement;
                if(retirement&&!anchor){
                    retirement->environment=call->environment;retirement->callback=slot->callback;retirement->owners=slot->owners;
                    retirement->origin=slot->origin;retirement->sourceLease=slot->sourceLease;retirement->pin=slot->pin;retirement->unknown=slot->unknown;
                    AcquireSRWLockExclusive(&native_tooling_records);retirement->next=native_tooling_slots;native_tooling_slots=retirement;
                    native_tooling_slot_queue_locked(retirement);ReleaseSRWLockExclusive(&native_tooling_records);
                    slot->published=1;anchor=retirement;
                }
                InterlockedIncrement(&native_tooling_failures);
            }
        }
        if(released){
            if(slot->sourceLease)native_original.DeleteGlobalRef(env,slot->sourceLease);
            if(slot->origin)native_original.DeleteWeakGlobalRef(env,slot->origin);
        }
        if(anchor){
            AcquireSRWLockExclusive(&native_tooling_records);
            if(released){anchor->pin=NULL;anchor->sourceLease=NULL;anchor->origin=NULL;}
            InterlockedExchange(&anchor->admitted,0);
            if(InterlockedCompareExchange(&anchor->active,0,0))InterlockedDecrement(&anchor->active);
            native_tooling_slot_queue_locked(anchor);native_tooling_environment_queue_locked(call->environment);
            ReleaseSRWLockExclusive(&native_tooling_records);
        }else {native_owner_links_free(slot->owners);free(slot->retirement);}
        memset(slot,0,sizeof(*slot));
    }
}

static jvmtiError native_tooling_heap_slot(JNIEnv *env,NativeToolingHeapCall *call,unsigned index,void *callback,
        OwnerLink *sources,jweak origin,int unknown){
    NativeToolingHeapSlot *slot=&call->slots[index];slot->callback=callback;slot->unknown=unknown;
    if(!callback)return JVMTI_ERROR_NONE;
    slot->retirement=(NativeToolingSlot*)calloc(1,sizeof(*slot->retirement));
    if(!slot->retirement)return JVMTI_ERROR_OUT_OF_MEMORY;
    int ready=native_owner_merge(&slot->owners,sources)&&native_capture_image_owners(callback,&slot->owners);
    if(native_owner_stopped(slot->owners))return JVMTI_ERROR_ACCESS_DENIED;
    HMODULE image=native_image(callback);if(!image)slot->unknown=1;
    if(image&&!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,(LPCSTR)callback,&slot->pin))ready=0;
    if(origin&&env&&native_original.NewWeakGlobalRef)slot->origin=native_original.NewWeakGlobalRef(env,origin);
    if(origin&&!slot->origin)ready=0;
    if(slot->owners&&env&&native_original.NewGlobalRef){
        jobjectArray lease=native_tooling_writers(env,slot->owners);
        if(lease){slot->sourceLease=(jobjectArray)native_original.NewGlobalRef(env,lease);native_original.DeleteLocalRef(env,lease);}
        if(!slot->sourceLease)ready=0;
    }
    slot->restricted=native_has_producer(slot->owners);if(slot->restricted)call->restricted=1;
    if(!ready||env&&native_original.ExceptionCheck&&native_original.ExceptionCheck(env))return JVMTI_ERROR_OUT_OF_MEMORY;
    NativeToolingSlot *anchor=slot->retirement;
    anchor->environment=call->environment;anchor->callback=callback;anchor->owners=slot->owners;
    anchor->origin=slot->origin;anchor->sourceLease=slot->sourceLease;anchor->pin=slot->pin;anchor->image=image;anchor->unknown=slot->unknown;
    int allowed=0;AcquireSRWLockExclusive(&native_tooling_records);
    if(!native_tooling_shutdown&&!call->environment->closing&&!call->environment->disposed&&!native_owner_stopped(slot->owners)){
        /* No thunk is installed for a synchronous heap callback. This exact
         * anchor keeps its operation sources visible to module retirement.
         */
        InterlockedExchange(&anchor->active,1);InterlockedExchange(&anchor->admitted,1);
        anchor->next=native_tooling_slots;native_tooling_slots=anchor;slot->published=1;allowed=1;
    }
    ReleaseSRWLockExclusive(&native_tooling_records);
    return allowed?JVMTI_ERROR_NONE:JVMTI_ERROR_ACCESS_DENIED;
}

/* Writable-pointer admission is supplied by the exact object/tag snapshot
 * below. The original VM pointer is passed only after that admission succeeds.
 */
static size_t native_tooling_heap_tag_hash(jlong tag){
    uint64_t value=(uint64_t)tag;value^=value>>33;value*=UINT64_C(0xff51afd7ed558ccd);
    value^=value>>33;value*=UINT64_C(0xc4ceb9fe1a85ec53);value^=value>>33;return (size_t)value;
}

static NativeToolingHeapObjectSnapshot *native_tooling_heap_tag_find(NativeToolingHeapCall *call,jlong tag){
    if(!tag||!call->tagCapacity)return NULL;size_t at=native_tooling_heap_tag_hash(tag)&(call->tagCapacity-1);
    while(call->tags[at]){
        NativeToolingHeapObjectSnapshot *entry=&call->objects[call->tags[at]-1];
        if(entry->tag==tag)return entry;at=(at+1)&(call->tagCapacity-1);
    }return NULL;
}

static int native_tooling_heap_snapshot_current(NativeToolingHeapCall *call){
    return !InterlockedCompareExchange(&call->policyChanged,0,0)
            &&call->policyEpoch==InterlockedCompareExchange64(&native_tooling_heap_policy_epoch,0,0)
            &&!InterlockedCompareExchange(&call->tagState->mutations,0,0)
            &&call->tagEpoch==InterlockedCompareExchange64(&call->tagState->epoch,0,0);
}

static int native_tooling_heap_tag_allowed(NativeToolingHeapCall *call,unsigned index,jlong *tag){
    if(!tag){native_tooling_heap_refuse(call,JVMTI_ERROR_NOT_AVAILABLE);return 0;}
    NativeToolingHeapObjectSnapshot *entry=native_tooling_heap_tag_find(call,*tag);
    if(!entry||!entry->unique||!entry->valid){native_tooling_heap_refuse(call,JVMTI_ERROR_NOT_AVAILABLE);return 0;}
    if(!(entry->eligible&(1u<<index))){native_tooling_heap_refuse(call,JVMTI_ERROR_ACCESS_DENIED);return 0;}
    return 1;
}

static int native_tooling_heap_tags(NativeToolingHeapCall *call,unsigned index,jlong *tag,jlong *referrer){
    NativeToolingHeapSlot *slot=&call->slots[index];
    /* A newly marked producer cannot use an earlier unrestricted preparation.
     * Its next ordinary call can build the exact object snapshot afresh.
     */
    if(!slot->restricted){
        if(native_has_producer(slot->owners)){native_tooling_heap_refuse(call,JVMTI_ERROR_NOT_AVAILABLE);return 0;}
        return !InterlockedCompareExchange(&call->refusal,0,0);
    }
    if(!call->tagState||!native_tooling_heap_snapshot_current(call)){
        native_tooling_heap_refuse(call,JVMTI_ERROR_NOT_AVAILABLE);return 0;
    }
    if(!native_tooling_heap_tag_allowed(call,index,tag))return 0;
    return !referrer||referrer==tag||native_tooling_heap_tag_allowed(call,index,referrer);
}

static void native_tooling_heap_tag_changed(NativeToolingHeapCall *call,jlong before,jlong after){
    if(before==after)return;
    NativeToolingHeapObjectSnapshot *previous=native_tooling_heap_tag_find(call,before);
    NativeToolingHeapObjectSnapshot *collision=native_tooling_heap_tag_find(call,after);
    if(previous)previous->valid=0;if(collision)collision->valid=0;
    /* The real tag write stays in the VM. A new numeric value cannot inherit
     * another object's rights, and zero cannot identify an untagged object.
     * A later ordinary walk rechecks the actual jobject and its current tag.
     */
}

static void native_tooling_heap_tags_written(NativeToolingHeapCall *call,jlong *tag,jlong *referrer,jlong before,jlong referrerBefore){
    if(!call->restricted)return;
    if(tag)native_tooling_heap_tag_changed(call,before,*tag);
    if(referrer&&referrer!=tag)native_tooling_heap_tag_changed(call,referrerBefore,*referrer);
}

static jlong native_tooling_heap_tag_value(jlong *tag){return tag?*tag:0;}

#define TOOLING_HEAP_BEGIN(INDEX,TAG,REFERRER,ABORT) \
    NativeToolingHeapCall *call=(NativeToolingHeapCall*)data; \
    NativeToolingHeapInvocation invocation; \
    if(!native_tooling_heap_tags(call,INDEX,TAG,REFERRER) \
            ||!native_tooling_heap_callback_begin(call,INDEX,&invocation))return ABORT; \
    jlong before=native_tooling_heap_tag_value(TAG),referrerBefore=native_tooling_heap_tag_value(REFERRER)
#define TOOLING_HEAP_END(TAG,REFERRER) \
    native_tooling_heap_tags_written(call,TAG,REFERRER,before,referrerBefore); \
    native_tooling_heap_callback_end(&invocation)

static jint JNICALL native_tooling_heap_iteration(jlong classTag,jlong size,jlong *tag,jint length,void *data){
    TOOLING_HEAP_BEGIN(TOOLING_HEAP_ITERATION,tag,NULL,JVMTI_VISIT_ABORT);
    jint result=((jvmtiHeapIterationCallback)call->slots[TOOLING_HEAP_ITERATION].callback)(classTag,size,tag,length,(void*)call->data);
    TOOLING_HEAP_END(tag,NULL);return result;
}

static jint JNICALL native_tooling_heap_reference(jvmtiHeapReferenceKind kind,const jvmtiHeapReferenceInfo *info,
        jlong classTag,jlong referrerClassTag,jlong size,jlong *tag,jlong *referrer,jint length,void *data){
    TOOLING_HEAP_BEGIN(TOOLING_HEAP_REFERENCE,tag,referrer,JVMTI_VISIT_ABORT);
    jint result=((jvmtiHeapReferenceCallback)call->slots[TOOLING_HEAP_REFERENCE].callback)
            (kind,info,classTag,referrerClassTag,size,tag,referrer,length,(void*)call->data);
    TOOLING_HEAP_END(tag,referrer);return result;
}

static jint JNICALL native_tooling_heap_primitive(jvmtiHeapReferenceKind kind,const jvmtiHeapReferenceInfo *info,
        jlong classTag,jlong *tag,jvalue value,jvmtiPrimitiveType type,void *data){
    TOOLING_HEAP_BEGIN(TOOLING_HEAP_PRIMITIVE,tag,NULL,JVMTI_VISIT_ABORT);
    jint result=((jvmtiPrimitiveFieldCallback)call->slots[TOOLING_HEAP_PRIMITIVE].callback)
            (kind,info,classTag,tag,value,type,(void*)call->data);
    TOOLING_HEAP_END(tag,NULL);return result;
}

static jint JNICALL native_tooling_heap_array(jlong classTag,jlong size,jlong *tag,jint count,
        jvmtiPrimitiveType type,const void *elements,void *data){
    TOOLING_HEAP_BEGIN(TOOLING_HEAP_ARRAY,tag,NULL,JVMTI_VISIT_ABORT);
    jint result=((jvmtiArrayPrimitiveValueCallback)call->slots[TOOLING_HEAP_ARRAY].callback)
            (classTag,size,tag,count,type,elements,(void*)call->data);
    TOOLING_HEAP_END(tag,NULL);return result;
}

static jint JNICALL native_tooling_heap_string(jlong classTag,jlong size,jlong *tag,const jchar *value,jint length,void *data){
    TOOLING_HEAP_BEGIN(TOOLING_HEAP_STRING,tag,NULL,JVMTI_VISIT_ABORT);
    jint result=((jvmtiStringPrimitiveValueCallback)call->slots[TOOLING_HEAP_STRING].callback)
            (classTag,size,tag,value,length,(void*)call->data);
    TOOLING_HEAP_END(tag,NULL);return result;
}

static jvmtiIterationControl JNICALL native_tooling_heap_root(jvmtiHeapRootKind kind,jlong classTag,jlong size,jlong *tag,void *data){
    TOOLING_HEAP_BEGIN(TOOLING_HEAP_ROOT,tag,NULL,JVMTI_ITERATION_ABORT);
    jvmtiIterationControl result=((jvmtiHeapRootCallback)call->slots[TOOLING_HEAP_ROOT].callback)
            (kind,classTag,size,tag,(void*)call->data);
    TOOLING_HEAP_END(tag,NULL);return result;
}

static jvmtiIterationControl JNICALL native_tooling_heap_stack(jvmtiHeapRootKind kind,jlong classTag,jlong size,jlong *tag,
        jlong threadTag,jint depth,jmethodID method,jint slot,void *data){
    TOOLING_HEAP_BEGIN(TOOLING_HEAP_STACK,tag,NULL,JVMTI_ITERATION_ABORT);
    jvmtiIterationControl result=((jvmtiStackReferenceCallback)call->slots[TOOLING_HEAP_STACK].callback)
            (kind,classTag,size,tag,threadTag,depth,method,slot,(void*)call->data);
    TOOLING_HEAP_END(tag,NULL);return result;
}

static jvmtiIterationControl JNICALL native_tooling_heap_object(jlong classTag,jlong size,jlong *tag,void *data){
    TOOLING_HEAP_BEGIN(TOOLING_HEAP_OBJECT,tag,NULL,JVMTI_ITERATION_ABORT);
    jvmtiIterationControl result=((jvmtiHeapObjectCallback)call->slots[TOOLING_HEAP_OBJECT].callback)
            (classTag,size,tag,(void*)call->data);
    TOOLING_HEAP_END(tag,NULL);return result;
}

static jvmtiIterationControl JNICALL native_tooling_heap_legacy_reference(jvmtiObjectReferenceKind kind,jlong classTag,
        jlong size,jlong *tag,jlong referrerTag,jint index,void *data){
    TOOLING_HEAP_BEGIN(TOOLING_HEAP_LEGACY_REFERENCE,tag,NULL,JVMTI_ITERATION_ABORT);
    jvmtiIterationControl result=((jvmtiObjectReferenceCallback)call->slots[TOOLING_HEAP_LEGACY_REFERENCE].callback)
            (kind,classTag,size,tag,referrerTag,index,(void*)call->data);
    TOOLING_HEAP_END(tag,NULL);return result;
}

#undef TOOLING_HEAP_END
#undef TOOLING_HEAP_BEGIN

static jvmtiError native_tooling_heap_snapshot(JNIEnv *env,NativeToolingHeapCall *call){
    if(!call->restricted)return JVMTI_ERROR_NONE;
    if(!env||!native_jni_ready||!native_tooling_heap_target)return JVMTI_ERROR_NOT_AVAILABLE;
    NativeToolingHeapEnvironment *state=call->tagState;
    call->policyEpoch=InterlockedCompareExchange64(&native_tooling_heap_policy_epoch,0,0);
    call->tagEpoch=InterlockedCompareExchange64(&state->epoch,0,0);
    if(InterlockedCompareExchange(&state->mutations,0,0))return JVMTI_ERROR_NOT_AVAILABLE;
    /* Registry nodes and weak identities are immutable during a live use of
     * this environment. Capture just its current head; later SetTag additions
     * revoke this preparation through epoch/mutations.
     */
    AcquireSRWLockShared(&native_tooling_heap_records);NativeToolingHeapObject *head=state->objects;ReleaseSRWLockShared(&native_tooling_heap_records);
    size_t count=0;for(NativeToolingHeapObject *entry=head;entry;entry=entry->next)count++;
    if(count>INT32_MAX||count>SIZE_MAX/sizeof(NativeToolingHeapObjectSnapshot))return JVMTI_ERROR_OUT_OF_MEMORY;
    NativeToolingHeapObjectSnapshot *objects=count?(NativeToolingHeapObjectSnapshot*)calloc(count,sizeof(*objects)):NULL;
    if(count&&!objects)return JVMTI_ERROR_OUT_OF_MEMORY;
    size_t copied=0;jvmtiError result=JVMTI_ERROR_NONE;
    for(NativeToolingHeapObject *entry=head;entry;entry=entry->next){
        jobject object=native_original.NewGlobalRef(env,entry->object);
        if(object)objects[copied++].object=object;
        else if(native_original.ExceptionCheck(env)){result=JVMTI_ERROR_OUT_OF_MEMORY;break;}
    }
    AcquireSRWLockExclusive(&native_tooling_heap_records);
    call->objects=objects;call->objectCount=copied;call->objectsPublished=1;
    ReleaseSRWLockExclusive(&native_tooling_heap_records);
    if(result!=JVMTI_ERROR_NONE)return result;
    if(!native_tooling_heap_snapshot_current(call))return JVMTI_ERROR_NOT_AVAILABLE;
    if(!copied)return JVMTI_ERROR_NONE;
    size_t capacity=2;while(capacity<copied*2){if(capacity>SIZE_MAX/2)return JVMTI_ERROR_OUT_OF_MEMORY;capacity*=2;}
    if(capacity>SIZE_MAX/sizeof(size_t))return JVMTI_ERROR_OUT_OF_MEMORY;
    call->tags=(size_t*)calloc(capacity,sizeof(*call->tags));call->tagCapacity=capacity;
    jlong *keys=(jlong*)malloc(copied*sizeof(*keys));if(!call->tags||!keys){free(keys);return JVMTI_ERROR_OUT_OF_MEMORY;}
    jint keyCount=0;
    for(size_t i=0;i<copied&&result==JVMTI_ERROR_NONE;i++){
        NativeToolingHeapObjectSnapshot *entry=&objects[i];result=call->environment->original->GetTag(call->environment->api,entry->object,&entry->tag);
        if(result!=JVMTI_ERROR_NONE||!entry->tag)continue;
        entry->unique=1;size_t at=native_tooling_heap_tag_hash(entry->tag)&(capacity-1);
        while(call->tags[at]&&objects[call->tags[at]-1].tag!=entry->tag)at=(at+1)&(capacity-1);
        if(call->tags[at]){objects[call->tags[at]-1].unique=0;entry->unique=0;}
        else {call->tags[at]=i+1;keys[keyCount++]=entry->tag;}
    }
    if(result==JVMTI_ERROR_NONE&&keyCount){
        jint matchedCount=0;jobject *matchedObjects=NULL;jlong *matchedTags=NULL;
        result=call->environment->original->GetObjectsWithTags(call->environment->api,keyCount,keys,&matchedCount,&matchedObjects,&matchedTags);
        if(result==JVMTI_ERROR_NONE){
            for(jint i=0;i<matchedCount;i++){
                NativeToolingHeapObjectSnapshot *entry=native_tooling_heap_tag_find(call,matchedTags[i]);
                if(entry){entry->reported++;if(native_original.IsSameObject(env,entry->object,matchedObjects[i]))entry->matched++;}
                if(matchedObjects[i])native_original.DeleteLocalRef(env,matchedObjects[i]);
            }
            if(matchedObjects)call->environment->original->Deallocate(call->environment->api,(unsigned char*)matchedObjects);
            if(matchedTags)call->environment->original->Deallocate(call->environment->api,(unsigned char*)matchedTags);
        }
    }
    free(keys);if(result!=JVMTI_ERROR_NONE)return result;
    if(!native_tooling_heap_snapshot_current(call))return JVMTI_ERROR_NOT_AVAILABLE;
    for(size_t i=0;i<copied;i++){
        NativeToolingHeapObjectSnapshot *entry=&objects[i];entry->valid=entry->unique&&entry->reported==1&&entry->matched==1;
        if(!entry->valid)continue;
        for(unsigned index=0;index<TOOLING_HEAP_CALLBACK_COUNT;index++){
            NativeToolingHeapSlot *slot=&call->slots[index];if(!slot->restricted||slot->unknown||native_owner_stopped(slot->owners))continue;
            result=native_tooling_heap_target_query(env,entry->object,slot->sourceLease);
            if(result==JVMTI_ERROR_NONE)entry->eligible|=1u<<index;
            else if(result!=JVMTI_ERROR_ACCESS_DENIED)return result;
        }
    }
    return native_tooling_heap_snapshot_current(call)?JVMTI_ERROR_NONE:JVMTI_ERROR_NOT_AVAILABLE;
}

static jvmtiError native_tooling_heap_prepare(NativeToolingEnv *record,JNIEnv *env,void *caller,const void *data,int follow,
        const jvmtiHeapCallbacks *callbacks,const void *const *legacy,NativeToolingHeapCall *call){
    memset(call,0,sizeof(*call));call->environment=record;call->data=data;
    if(record->own&&!native_tooling_internal(caller)||native_tooling_stopped(env,caller))return JVMTI_ERROR_ACCESS_DENIED;
    jvmtiHeapCallbacks supplied={0};
    if(callbacks){SIZE_T copied=0;
        if(!ReadProcessMemory(GetCurrentProcess(),callbacks,&supplied,sizeof(supplied),&copied)||copied!=sizeof(supplied))return JVMTI_ERROR_ILLEGAL_ARGUMENT;}
    OwnerLink *sources=NULL;jweak origin=NULL;int unknown=0;
    if(!native_tooling_capture(env,caller,&sources,&origin,&unknown)){
        if(origin)native_original.DeleteWeakGlobalRef(env,origin);native_owner_links_free(sources);return JVMTI_ERROR_ACCESS_DENIED;}
    jvmtiError result=JVMTI_ERROR_NONE;
#define TOOLING_HEAP_SLOT(INDEX,POINTER) \
    if(result==JVMTI_ERROR_NONE)result=native_tooling_heap_slot(env,call,INDEX,(void*)(POINTER),sources,origin,unknown)
    if(callbacks){
        if(!follow){TOOLING_HEAP_SLOT(TOOLING_HEAP_ITERATION,supplied.heap_iteration_callback);}
        else {TOOLING_HEAP_SLOT(TOOLING_HEAP_REFERENCE,supplied.heap_reference_callback);}
        TOOLING_HEAP_SLOT(TOOLING_HEAP_PRIMITIVE,supplied.primitive_field_callback);
        TOOLING_HEAP_SLOT(TOOLING_HEAP_ARRAY,supplied.array_primitive_value_callback);
        TOOLING_HEAP_SLOT(TOOLING_HEAP_STRING,supplied.string_primitive_value_callback);
        call->callbacks=supplied;
        if(!follow)call->callbacks.heap_iteration_callback=supplied.heap_iteration_callback?native_tooling_heap_iteration:NULL;
        else call->callbacks.heap_reference_callback=supplied.heap_reference_callback?native_tooling_heap_reference:NULL;
        call->callbacks.primitive_field_callback=supplied.primitive_field_callback?native_tooling_heap_primitive:NULL;
        call->callbacks.array_primitive_value_callback=supplied.array_primitive_value_callback?native_tooling_heap_array:NULL;
        call->callbacks.string_primitive_value_callback=supplied.string_primitive_value_callback?native_tooling_heap_string:NULL;
    }
    if(legacy){
        TOOLING_HEAP_SLOT(TOOLING_HEAP_ROOT,legacy[0]);TOOLING_HEAP_SLOT(TOOLING_HEAP_STACK,legacy[1]);
        TOOLING_HEAP_SLOT(TOOLING_HEAP_OBJECT,legacy[2]);TOOLING_HEAP_SLOT(TOOLING_HEAP_LEGACY_REFERENCE,legacy[3]);
    }
#undef TOOLING_HEAP_SLOT
    if(origin)native_original.DeleteWeakGlobalRef(env,origin);native_owner_links_free(sources);
    if(result!=JVMTI_ERROR_NONE)return result;
    call->tagState=native_tooling_heap_environment(record,1);if(!call->tagState)return JVMTI_ERROR_OUT_OF_MEMORY;
    if(InterlockedCompareExchangePointer((void *volatile*)&call->tagState->walking,call,NULL))return JVMTI_ERROR_NOT_AVAILABLE;
    call->claimed=1;
    if(call->restricted)InterlockedIncrement(&native_tooling_heap_policy_active);
    return native_tooling_heap_snapshot(env,call);
}

static jvmtiError native_tooling_heap_finish(JNIEnv *env,NativeToolingHeapCall *call,jvmtiError result){
    LONG refusal=InterlockedCompareExchange(&call->refusal,0,0);
    native_tooling_heap_release(env,call);
    if(result==JVMTI_ERROR_NONE&&refusal!=JVMTI_ERROR_NONE)return native_tooling_error((jvmtiError)refusal);
    return result;
}

static jvmtiError JNICALL native_tooling_FollowReferences(jvmtiEnv *api,jint filter,jclass type,jobject initial,
        const jvmtiHeapCallbacks *callbacks,const void *data){
    if(native_tooling_heap_current_callback())return native_tooling_error(JVMTI_ERROR_NOT_AVAILABLE);
    NativeToolingEnv *record=native_tooling_environment(api);if(!record)return JVMTI_ERROR_INVALID_ENVIRONMENT;
    TOOLING_USE(record);JNIEnv *env=native_tooling_jni();NativeToolingHeapCall call;
    jvmtiError result=native_tooling_heap_prepare(record,env,__builtin_return_address(0),data,1,callbacks,NULL,&call);
    if(result==JVMTI_ERROR_NONE)result=record->original->FollowReferences(api,filter,type,initial,callbacks?&call.callbacks:NULL,callbacks?&call:data);
    return native_tooling_heap_finish(env,&call,result);
}

static jvmtiError JNICALL native_tooling_IterateThroughHeap(jvmtiEnv *api,jint filter,jclass type,
        const jvmtiHeapCallbacks *callbacks,const void *data){
    if(native_tooling_heap_current_callback())return native_tooling_error(JVMTI_ERROR_NOT_AVAILABLE);
    NativeToolingEnv *record=native_tooling_environment(api);if(!record)return JVMTI_ERROR_INVALID_ENVIRONMENT;
    TOOLING_USE(record);JNIEnv *env=native_tooling_jni();NativeToolingHeapCall call;
    jvmtiError result=native_tooling_heap_prepare(record,env,__builtin_return_address(0),data,0,callbacks,NULL,&call);
    if(result==JVMTI_ERROR_NONE)result=record->original->IterateThroughHeap(api,filter,type,callbacks?&call.callbacks:NULL,callbacks?&call:data);
    return native_tooling_heap_finish(env,&call,result);
}

static jvmtiError JNICALL native_tooling_IterateOverObjectsReachableFromObject(jvmtiEnv *api,jobject object,
        jvmtiObjectReferenceCallback callback,const void *data){
    if(native_tooling_heap_current_callback())return native_tooling_error(JVMTI_ERROR_NOT_AVAILABLE);
    NativeToolingEnv *record=native_tooling_environment(api);if(!record)return JVMTI_ERROR_INVALID_ENVIRONMENT;
    TOOLING_USE(record);JNIEnv *env=native_tooling_jni();NativeToolingHeapCall call;
    const void *legacy[4]={NULL,NULL,NULL,(const void*)callback};
    jvmtiError result=native_tooling_heap_prepare(record,env,__builtin_return_address(0),data,0,NULL,legacy,&call);
    if(result==JVMTI_ERROR_NONE)result=record->original->IterateOverObjectsReachableFromObject(api,object,callback?native_tooling_heap_legacy_reference:NULL,callback?&call:data);
    return native_tooling_heap_finish(env,&call,result);
}

static jvmtiError JNICALL native_tooling_IterateOverReachableObjects(jvmtiEnv *api,jvmtiHeapRootCallback root,
        jvmtiStackReferenceCallback stack,jvmtiObjectReferenceCallback object,const void *data){
    if(native_tooling_heap_current_callback())return native_tooling_error(JVMTI_ERROR_NOT_AVAILABLE);
    NativeToolingEnv *record=native_tooling_environment(api);if(!record)return JVMTI_ERROR_INVALID_ENVIRONMENT;
    TOOLING_USE(record);JNIEnv *env=native_tooling_jni();NativeToolingHeapCall call;
    const void *legacy[4]={(const void*)root,(const void*)stack,NULL,(const void*)object};
    jvmtiError result=native_tooling_heap_prepare(record,env,__builtin_return_address(0),data,0,NULL,legacy,&call);
    if(result==JVMTI_ERROR_NONE)result=record->original->IterateOverReachableObjects(api,root?native_tooling_heap_root:NULL,
            stack?native_tooling_heap_stack:NULL,object?native_tooling_heap_legacy_reference:NULL,root||stack||object?&call:data);
    return native_tooling_heap_finish(env,&call,result);
}

static jvmtiError JNICALL native_tooling_IterateOverHeap(jvmtiEnv *api,jvmtiHeapObjectFilter filter,jvmtiHeapObjectCallback callback,const void *data){
    if(native_tooling_heap_current_callback())return native_tooling_error(JVMTI_ERROR_NOT_AVAILABLE);
    NativeToolingEnv *record=native_tooling_environment(api);if(!record)return JVMTI_ERROR_INVALID_ENVIRONMENT;
    TOOLING_USE(record);JNIEnv *env=native_tooling_jni();NativeToolingHeapCall call;
    const void *legacy[4]={NULL,NULL,(const void*)callback,NULL};
    jvmtiError result=native_tooling_heap_prepare(record,env,__builtin_return_address(0),data,0,NULL,legacy,&call);
    if(result==JVMTI_ERROR_NONE)result=record->original->IterateOverHeap(api,filter,callback?native_tooling_heap_object:NULL,callback?&call:data);
    return native_tooling_heap_finish(env,&call,result);
}

static jvmtiError JNICALL native_tooling_IterateOverInstancesOfClass(jvmtiEnv *api,jclass type,jvmtiHeapObjectFilter filter,
        jvmtiHeapObjectCallback callback,const void *data){
    if(native_tooling_heap_current_callback())return native_tooling_error(JVMTI_ERROR_NOT_AVAILABLE);
    NativeToolingEnv *record=native_tooling_environment(api);if(!record)return JVMTI_ERROR_INVALID_ENVIRONMENT;
    TOOLING_USE(record);JNIEnv *env=native_tooling_jni();NativeToolingHeapCall call;
    const void *legacy[4]={NULL,NULL,(const void*)callback,NULL};
    jvmtiError result=native_tooling_heap_prepare(record,env,__builtin_return_address(0),data,0,NULL,legacy,&call);
    if(result==JVMTI_ERROR_NONE)result=record->original->IterateOverInstancesOfClass(api,type,filter,callback?native_tooling_heap_object:NULL,callback?&call:data);
    return native_tooling_heap_finish(env,&call,result);
}
