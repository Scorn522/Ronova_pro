/* A raw address selects a live allocation only after its actual allocator has
 * returned it. Numeric addresses never manufacture producer provenance. */
typedef struct NativeMemoryImage {uint64_t start,length;size_t references;unsigned char *before,*after;} NativeMemoryImage;
typedef struct NativeMemoryInterest {Owner *owner;volatile LONG revisions;struct NativeMemoryInterest *next;} NativeMemoryInterest;
typedef struct NativeMemoryRevision {size_t references;OwnerLink *sources;NativeMemoryImage *image;struct NativeMemoryAllocation *allocation;int complete,counted;struct NativeMemoryRevision *previous;} NativeMemoryRevision;
typedef struct NativeMemoryRange {uint64_t start,length;NativeMemoryRevision *revision;struct NativeMemoryRange *next;} NativeMemoryRange;
typedef struct NativeMemoryAllocation {uint64_t address,bytes,generation,writeEpoch;jobject allocator;jweak bufferRoot,bufferAction;void *release;OwnerLink *sources,*readers,*preserved;NativeMemoryRange *ranges;NativeMemoryInterest *interests;CRITICAL_SECTION gate;volatile LONG alive,writers,reading,changing,users,sealed,unknownReaders,unknownUse,releaseFailed,managed,restoring;struct NativeMemoryAllocation *next;} NativeMemoryAllocation;
typedef struct NativeMemoryCall {NativeMemoryAllocation *allocation,*prepared;OwnerLink *sources;jobject allocator;jweak bufferRoot;void *release;uint64_t address,bytes,generation;int locked,admitted,known,unknown,managed,cleanup;} NativeMemoryCall;
typedef struct NativeMemoryWrite {NativeMemoryAllocation *allocation;OwnerLink *sources,*observed;NativeMemoryImage *image;uint64_t address,length,generation;int locked,written,reading,detached,unobserved,uncertain,unknown,admitted,initializing;struct NativeMemoryWrite *previous,*root;} NativeMemoryWrite;
typedef struct NativeBufferIoWrite {NativeMemoryWrite write;uint64_t epoch;jobject receipt,group;struct NativeBufferIoWrite *next;} NativeBufferIoWrite;
static SRWLOCK native_memory_records=SRWLOCK_INIT;
static NativeMemoryAllocation *native_memory_allocations;
static OwnerLink *native_memory_unknown;
static NativeBufferIoWrite *native_buffer_io_writes;

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
static int native_memory_sources(JNIEnv *env,NativeThread *state,jobjectArray supplied,OwnerLink **sources,int *unknown){
    if(!host_capture(env,state,NULL,sources))return 0;
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
    DWORD error=GetLastError();SIZE_T copied=0;int ready=ReadProcessMemory(GetCurrentProcess(),(const void*)(uintptr_t)address,bytes,length,&copied)&&copied==length;SetLastError(error);return ready;
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
    revision->references=1;revision->allocation=write->allocation;
    if((!write->initializing&&!native_owner_merge(&revision->sources,write->sources))||!native_memory_interests(revision)){native_memory_revision_release(revision);return NULL;}
    NativeMemoryImage *image=write->image;NativeMemoryRevision *previous=range->revision;
    revision->image=image;if(image)image->references++;
    revision->complete=!write->uncertain&&(!write->unknown||write->initializing)&&native_memory_image_covers(image,range->start,range->length);
    if(previous){
        revision->complete=revision->complete&&previous->complete&&native_memory_image_covers(previous->image,range->start,range->length)
                &&memcmp(image->before+(size_t)(range->start-image->start),previous->image->after+(size_t)(range->start-previous->image->start),(size_t)range->length)==0;
        if(revision->complete&&native_memory_same_sources(previous->sources,revision->sources)){
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
    state->control++;int ready=native_memory_sources(env,state,NULL,&call->sources,&call->unknown);state->control--;
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
    NativeMemoryRange *range=allocation->ranges;allocation->ranges=NULL;
    while(range){NativeMemoryRange *next=range->next;native_memory_revision_release(range->revision);free(range);range=next;}
    if(allocation->allocator){native_original.DeleteGlobalRef(env,allocation->allocator);allocation->allocator=NULL;}allocation->release=NULL;
    if(allocation->bufferRoot){native_original.DeleteWeakGlobalRef(env,allocation->bufferRoot);allocation->bufferRoot=NULL;}
    if(allocation->bufferAction){native_original.DeleteWeakGlobalRef(env,allocation->bufferAction);allocation->bufferAction=NULL;}
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
        allocation->address=returned;allocation->bytes=call->bytes;allocation->generation++;InterlockedExchange(&allocation->alive,1);native_memory_clip(allocation);
    }
    if(call->allocation&&(!completed||!call->known||binding->unsafeOperation==UNSAFE_REALLOCATE&&!returned&&!call->bytes)){
        InterlockedExchange(&call->allocation->unknownUse,1);InterlockedExchange(&call->allocation->releaseFailed,1);
    }
    ReleaseSRWLockExclusive(&native_memory_records);
    if(!frame)native_memory_unmapped(call->sources);
    native_memory_call_dispose(env,call);
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
            &&native_original.CallStaticBooleanMethod(env,native_tasks,native_memory_gate)&&!native_original.ExceptionCheck(env);
}
static int native_memory_read_sources(NativeMemoryWrite *read,OwnerLink **sources){
    NativeMemoryAllocation *allocation=read->allocation;
    if(!allocation||!InterlockedCompareExchange(&allocation->alive,0,0)||allocation->generation!=read->generation)return 1;
    int ready=native_memory_creator_sources(allocation,sources);uint64_t start=read->address-allocation->address;
    for(NativeMemoryRange *range=allocation->ranges;ready&&range;range=range->next)
        if(start<range->start+range->length&&range->start<start+read->length)ready=native_memory_revision_sources(sources,range->revision);
    return ready;
}
JNIEXPORT jlong JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_memoryWriteBegin0(JNIEnv *env,jclass type,jlong address,jlong length,jobjectArray contributors,jboolean reading,jboolean snapshot){
    if(!native_memory_authority(env,type)){native_refuse(env,"ACTUAL_NATIVE_MEMORY_WRITER_REQUIRED");return 0;}
    NativeThread *state=native_thread();if(!state||length<0){native_refuse(env,"NATIVE_MEMORY_SCOPE_UNAVAILABLE");return 0;}
    NativeMemoryWrite *write=(NativeMemoryWrite*)calloc(1,sizeof(*write));if(!write){native_refuse(env,"NATIVE_MEMORY_SCOPE_CAPACITY");return 0;}
    state->control++;int ready=native_memory_sources(env,state,contributors,&write->sources,&write->unknown);state->control--;
    if(!ready){native_owner_links_free(write->sources);free(write);native_refuse(env,"NATIVE_MEMORY_SOURCES_UNAVAILABLE");return 0;}
    write->address=(uint64_t)address;write->length=(uint64_t)length;write->reading=reading==JNI_TRUE;write->detached=write->reading&&snapshot==JNI_TRUE;
    AcquireSRWLockShared(&native_memory_records);write->allocation=native_memory_find(write->address,write->length,0);
    if(write->allocation&&!InterlockedCompareExchange(&write->allocation->sealed,0,0)){
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
    state->control++;int ready=native_memory_sources(env,state,contributors,&write->sources,&write->unknown);state->control--;
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
static int native_memory_private(NativeMemoryAllocation *allocation,OwnerLink *selected){
    if(!allocation->allocator||!allocation->release||InterlockedCompareExchange(&allocation->managed,0,0)||!native_memory_selected(allocation->sources,selected)
            ||InterlockedCompareExchange(&allocation->unknownReaders,0,0)||InterlockedCompareExchange(&allocation->unknownUse,0,0)
            ||InterlockedCompareExchange(&allocation->releaseFailed,0,0)||InterlockedCompareExchange(&allocation->sealed,0,0))return 0;
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
    int ready=InterlockedCompareExchange(&allocation->alive,0,0)&&native_memory_private(allocation,selected)
            &&!InterlockedCompareExchange(&allocation->users,0,0)&&!InterlockedCompareExchange(&allocation->writers,0,0)
            &&!InterlockedCompareExchange(&allocation->reading,0,0)&&!InterlockedCompareExchange(&allocation->changing,0,0);
    if(ready){InterlockedExchange(&allocation->sealed,1);InterlockedIncrement(&allocation->changing);}
    ReleaseSRWLockExclusive(&native_memory_records);if(!ready)return 0;
    void (JNICALL *release)(JNIEnv*,jobject,jlong)=(void (JNICALL *)(JNIEnv*,jobject,jlong))allocation->release;
    release(env,allocation->allocator,(jlong)allocation->address);
    ready=!native_original.ExceptionCheck(env);AcquireSRWLockExclusive(&native_memory_records);
    if(ready)native_memory_released(env,allocation);
    else {InterlockedExchange(&allocation->releaseFailed,1);InterlockedExchange(&allocation->unknownUse,1);}
    InterlockedDecrement(&allocation->changing);ReleaseSRWLockExclusive(&native_memory_records);return ready;
}
static int native_memory_restore_range(NativeMemoryAllocation *allocation,NativeMemoryRange *range,OwnerLink *selected){
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
        replacement->references=1;replacement->allocation=allocation;replacement->image=image;replacement->complete=1;
        if(!native_owner_merge(&replacement->sources,original->sources)||!native_memory_interests(replacement)){native_memory_revision_release(replacement);goto done;}
        // The outside writer's result remains intact. Only its saved baseline
        // changes, so undoing it later cannot resurrect a retired contribution.
        replacement->previous=rebuilt;rebuilt=replacement;memcpy(incoming,image->after,length);
    }
    const unsigned char *expected=head->image->after+(size_t)(range->start-head->image->start);
    uint64_t address=allocation->address+range->start;ready=native_memory_read(address,current,length);
    if(ready&&memcmp(current,incoming,length)!=0){
        ready=memcmp(current,expected,length)==0;
        if(ready){
            DWORD error=GetLastError();SIZE_T written=0;WriteProcessMemory(GetCurrentProcess(),(void*)(uintptr_t)address,incoming,length,&written);SetLastError(error);
            ready=native_memory_read(address,current,length)&&memcmp(current,incoming,length)==0;
            if(!ready)native_memory_unmapped(head->sources);
        }
    }
    if(ready){range->revision=rebuilt;rebuilt=NULL;native_memory_revision_release(head);}
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
        entry->receipt=native_original.NewGlobalRef(env,receipt);ready=entry->receipt&&entry->group&&native_memory_sources(env,state,sources,&entry->write.sources,&entry->write.unknown);
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
                &&!InterlockedCompareExchange(&allocation->sealed,0,0)&&!InterlockedCompareExchange(&allocation->changing,0,0)
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
    NativeThread *state=native_thread();if(!state||!action||!token||cursor<0||budget<=0){native_refuse(env,"BUFFER_SOURCE_RESTORE_SCOPE_UNAVAILABLE");return NULL;}
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
                    &&!InterlockedCompareExchange(&allocation->unknownUse,0,0)&&!InterlockedCompareExchange(&allocation->releaseFailed,0,0);
            if(ready)InterlockedExchange(&allocation->restoring,1);ReleaseSRWLockExclusive(&native_memory_records);
            if(ready){
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
        allocation=allocation->next;offset=0;
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
        LeaveCriticalSection(&allocation->gate);
    }
    jlongArray result=native_original.NewLongArray(env,5);if(result)native_original.SetLongArrayRegion(env,result,0,5,values);return result;
}
