/* Real LoadLibrary receipts. These are execution/source relationships, not
 * independently identifiable Windows loader references. Only our extra pin
 * is released here; application HMODULE references are never consumed.
 * Included before the ordinary Host source queries. */
struct HostLibraryLoad {
    HostImage *parentImage,*image,*preparedImage;
    NativeLibrary *parentLibrary;
    HostLibraryLoad *parentLoad,*sourceParent,*previousScope;
    struct HostThreadWorker *sourceWorker;
    void *toolingAnchor,*tlsAnchor;
    OwnerLink *owners;jobjectArray sourceLease;jweak origin;
    HMODULE actual,sparePin;DWORD flags,error;
    NativeToolingScope scope;
    volatile LONG calling,borrowers;
    int unknown,leaseComplete,resource,nonExecutable,publicCarrier,retired,releasing,refused;
    struct HostLibraryLoad *next,*imageNext;
};
static HostLibraryLoad *host_library_loads;
static int host_library_current(NativeLibrary*,HMODULE);
static int host_thread_worker_load_borrow(NativeThread*,OwnerLink*,struct HostThreadWorker**);
static void host_thread_worker_load_drop(struct HostThreadWorker*);
static int native_tooling_load_borrow(NativeThread*,OwnerLink*,void**);
static void native_thread_worker_heap_drop(void*);
static int host_tls_source_borrow(NativeThread*,OwnerLink*,void**);
static void host_tls_source_drop(void*);

static int host_module_sources_cover(OwnerLink *retained,OwnerLink *actual){
    for(;actual;actual=actual->next){OwnerLink *found=retained;
        while(found&&found->owner!=actual->owner)found=found->next;if(!found)return 0;}
    return 1;
}
static int host_module_sources_complete(JNIEnv *env,jobjectArray sources){
    if(!sources||native_original.ExceptionCheck(env))return 0;
    jsize count=native_original.GetArrayLength(env,sources);
    for(jsize i=0;i<count;i++){jobject actual=native_original.GetObjectArrayElement(env,sources,i);
        if(!actual||native_original.ExceptionCheck(env)){if(actual)native_original.DeleteLocalRef(env,actual);return 0;}
        native_original.DeleteLocalRef(env,actual);}
    return !native_original.ExceptionCheck(env);
}
static int host_module_producers_retired(OwnerLink *owners,int unknown){
    if(unknown)return 0;int producer=0;
    for(OwnerLink *source=owners;source;source=source->next){
        if(!InterlockedCompareExchange(&source->owner->producer,0,0))continue;
        producer=1;if(!InterlockedCompareExchange(&source->owner->stopped,0,0))return 0;
    }return producer;
}
static int host_module_image_sources_retired_locked(HostImage *image){
    if(!image->pin||image->opaque)return 0;int found=0;
    for(HostLibraryLoad *load=image->loads;load;load=load->imageNext){
        if(!load->actual)continue;found=1;
        if(load->unknown||!load->leaseComplete||!host_module_producers_retired(load->owners,load->unknown))return 0;
    }return found;
}
static int host_module_load_current(const HostLibraryLoad *load,HostImage *image){
    return load&&load->actual&&load->image==image&&!load->resource&&!load->nonExecutable
            &&!load->retired&&image->live&&image->pin&&!image->releasing;
}
/* host_imports is held. Prefer actual execution provenance to every historical
 * loader of a shared image. No path/name or borrowed JDK identity is used. */
static int host_module_select_locked(HostImage *image,NativeThread *state,HostLibraryLoad *preferred,
        OwnerLink **owners,HostLibraryLoad **selected,int *unknown){
    if(selected)*selected=NULL;if(!image||!image->loads)return 0;
    if(state&&state->hostUnloadImage==image){
        if(owners&&state->tooling&&!native_owner_merge(owners,state->tooling->owners))return -1;
        if(unknown&&state->tooling)*unknown|=state->tooling->unknown;return 1;
    }
    if(!image->live||!image->pin||image->releasing)return 0;
    HostLibraryLoad *specific=host_module_load_current(preferred,image)?preferred:NULL;
    if(!specific&&state){
        for(unsigned i=state->depth;i>0&&!specific;i--){Binding *binding=state->stack[i-1];
            if(binding&&host_module_load_current(binding->hostLoad,image))specific=binding->hostLoad;}
        for(HostLibraryLoad *scope=state->hostLoad;scope&&!specific;scope=scope->parentLoad)
            if(host_module_load_current(scope,image))specific=scope;
        if(!specific){HostLibraryLoad *worker=host_thread_worker_child(state,image->handle);
            if(host_module_load_current(worker,image))specific=worker;}
    }
    if(specific){
        if(owners&&!native_owner_merge(owners,specific->owners))return -1;
        if(unknown)*unknown|=specific->unknown;if(selected)*selected=specific;return 1;
    }
    /* A B binding/callback/worker executing in a shared child remains B's
     * actual invocation. A's old load alone cannot poison that invocation. */
    int scoped=0,ready=1;
    if(state){
        if(state->registration){scoped=1;if(owners)ready=ready&&native_owner_merge(owners,state->registration->owners);
            if(unknown)*unknown|=state->registration->unknown;}
        for(unsigned i=0;i<state->depth;i++){Binding *binding=state->stack[i];
            if(!binding)continue;scoped=1;if(owners)ready=ready&&native_owner_merge(owners,binding->owners);
            if(unknown)*unknown|=binding->hostUnknown;}
        for(NativeLibraryScope *scope=state->library;scope;scope=scope->previous){
            scoped=1;if(owners)ready=ready&&native_owner_merge(owners,scope->library->owners);}
        for(NativeToolingScope *scope=state->tooling;scope;scope=scope->previous){
            scoped=1;if(owners)ready=ready&&native_owner_merge(owners,scope->owners);
            if(unknown)*unknown|=scope->unknown;}
        /* Select a parent relationship only when this actual registration and
         * the complete current native scope cover that receipt's sources. */
        if(scoped&&ready){HostLibraryLoad *chosen=NULL;int mixed=0;
            for(HostLibraryLoad *load=image->loads;load;load=load->imageNext){
                if(!host_module_load_current(load,image)||!load->parentLibrary||load->unknown)continue;
                int parent=0;for(unsigned i=0;i<state->depth&&!parent;i++)
                    parent=state->stack[i]&&state->stack[i]->library==load->parentLibrary;
                for(NativeLibraryScope *scope=state->library;scope&&!parent;scope=scope->previous)
                    parent=scope->library==load->parentLibrary;
                if(parent&&owners&&host_module_sources_cover(*owners,load->owners)){
                    if(chosen&&chosen!=load)mixed=1;else chosen=load;}
            }
            if(selected&&!mixed)*selected=chosen;
        }
    }
    if(scoped)return ready?1:-1;
    int outside=0;
    for(HostLibraryLoad *load=image->loads;load&&!outside;load=load->imageNext)
        outside=host_module_load_current(load,image)&&load->leaseComplete&&!load->unknown&&!native_has_producer(load->owners);
    if(outside){
        /* The actual Windows API provider or a confirmed unrelated consumer
         * remains a public carrier. A stopped receipt cannot own every bare
         * call in that shared DLL; this unknown path receives no private lease. */
        for(HostLibraryLoad *load=image->loads;load;load=load->imageNext)
            if(host_module_load_current(load,image)&&load->leaseComplete&&!load->unknown&&!native_has_producer(load->owners))
                if(owners)ready=ready&&native_owner_merge(owners,load->owners);
        if(unknown)*unknown=1;return ready?1:-1;
    }
    int found=0;HostLibraryLoad *chosen=NULL;int mixed=0;
    for(HostLibraryLoad *load=image->loads;load;load=load->imageNext){
        if(!host_module_load_current(load,image))continue;found=1;
        if(owners)ready=ready&&native_owner_merge(owners,load->owners);
        if(unknown)*unknown|=load->unknown;
        if(chosen&&chosen!=load)mixed=1;else chosen=load;
    }
    /* Shared/unobserved execution cannot acquire a private lifetime lease. */
    if(unknown&&mixed)*unknown=1;if(selected&&!mixed)*selected=chosen;
    return !found?0:ready?1:-1;
}
static HostImage *host_module_address_locked(void *address,HMODULE actual,int restricted){
    uintptr_t at=(uintptr_t)address;HostImage *found=NULL;
    for(HostImage *image=host_images;image;image=image->next){
        if(!image->live||!image->loads||!image->pin||image->releasing)continue;
        if(restricted){uintptr_t base=(uintptr_t)image->handle;
            if(!address||at<base||at-base>=image->size)continue;
        }else if(image->handle!=actual)continue;
        if(found&&found!=image)return NULL;found=image;
    }return found;
}
static int host_module_capture(NativeThread *state,void *address,OwnerLink **owners,
        HostImage **actualImage,HostLibraryLoad **actualLoad,int *unknown){
    if(actualImage)*actualImage=NULL;if(actualLoad)*actualLoad=NULL;
    int restricted=state&&(state->heapCallback||state->vmDetaching||state->vmThreadEnding||state->hostUnloadImage||state->hostRelease
            ||state->hostLoad&&state->hostLoad->calling);
    HMODULE handle=restricted?NULL:native_image(address);
    if(restricted){if(!TryAcquireSRWLockShared(&host_imports))return 0;}
    else AcquireSRWLockShared(&host_imports);
    HostImage *image=host_module_address_locked(address,handle,restricted);
    int associated=image?host_module_select_locked(image,state,NULL,owners,actualLoad,unknown):0;
    if(actualImage)*actualImage=image;ReleaseSRWLockShared(&host_imports);
    return associated>=0;
}
static void host_module_parent(NativeThread *state,void *caller,HostLibraryLoad *load){
    uintptr_t address=(uintptr_t)caller;
    int restricted=state&&(state->vmDetaching||state->vmThreadEnding||state->hostUnloadImage||state->hostRelease
            ||state->hostLoad&&state->hostLoad->calling);
    if(restricted){if(!TryAcquireSRWLockShared(&host_imports)){load->unknown=1;return;}
        if(!TryAcquireSRWLockShared(&native_records)){ReleaseSRWLockShared(&host_imports);load->unknown=1;return;}
    }else {AcquireSRWLockShared(&host_imports);AcquireSRWLockShared(&native_records);}
    HostImage *parent=load->parentImage;
    if(!parent)for(HostImage *image=host_images;image;image=image->next){uintptr_t base=(uintptr_t)image->handle;
        if(!image->live||!caller||address<base||address-base>=image->size)continue;
        if(parent&&parent!=image){parent=NULL;load->unknown=1;break;}parent=image;}
    load->parentImage=parent;
    NativeLibrary *selected=NULL;int mixed=0;
    if(parent&&state){
        for(unsigned i=0;i<state->depth;i++){NativeLibrary *library=state->stack[i]->library;
            if(library&&library->hostImage==parent&&library->handle==parent->handle&&InterlockedCompareExchange(&library->alive,0,0)){
                if(selected&&selected!=library)mixed=1;else selected=library;}}
        for(NativeLibraryScope *scope=state->library;scope;scope=scope->previous){NativeLibrary *library=scope->library;
            if(library->hostImage==parent&&library->handle==parent->handle&&InterlockedCompareExchange(&library->alive,0,0)){
                if(selected&&selected!=library)mixed=1;else selected=library;}}
    }
    if(!selected&&!mixed&&parent&&load->parentLibrary&&load->parentLibrary->hostImage==parent
            &&load->parentLibrary->handle==parent->handle)selected=load->parentLibrary;
    load->parentLibrary=mixed?NULL:selected;
    ReleaseSRWLockShared(&native_records);ReleaseSRWLockShared(&host_imports);
}
static int host_module_binding_enter(Binding *binding){
    HostImage *image=binding->hostImage;if(!image)return 1;
    AcquireSRWLockShared(&host_imports);
    int ready=image->live&&!image->releasing&&(image->pin||binding->library&&host_library_current(binding->library,image->handle));
    if(ready)InterlockedIncrement(&image->active);ReleaseSRWLockShared(&host_imports);
    return ready;
}
static void host_module_binding_leave(Binding *binding){
    if(binding->hostImage&&InterlockedDecrement(&binding->hostImage->active)==0
            &&InterlockedCompareExchange(&binding->hostImage->retiring,0,0))native_thread_worker_wake();
}
static int host_module_borrow_load(NativeThread *state,HostLibraryLoad *load){
    HostLibraryLoad *parent=state?state->hostLoad:NULL;
    if(!parent&&state)parent=host_module_release_sources(state);
    if(!parent&&state)parent=host_thread_worker_child(state,NULL);
    AcquireSRWLockExclusive(&host_imports);
    int ready=parent&&!parent->retired&&!parent->releasing&&!parent->unknown&&parent->leaseComplete
            &&host_module_sources_cover(parent->owners,load->owners);
    if(ready){InterlockedIncrement(&parent->borrowers);load->sourceParent=parent;load->leaseComplete=1;}
    ReleaseSRWLockExclusive(&host_imports);
    if(!ready&&host_thread_worker_load_borrow(state,load->owners,&load->sourceWorker))ready=load->leaseComplete=1;
    if(!ready&&native_tooling_load_borrow(state,load->owners,&load->toolingAnchor))ready=load->leaseComplete=1;
    if(!ready&&host_tls_source_borrow(state,load->owners,&load->tlsAnchor))ready=load->leaseComplete=1;
    return ready;
}
static int host_module_keep_sources(NativeThread *state,HostLibraryLoad *load){
    JNIEnv *env=NULL;int nativeOnly=state&&(state->vmDetaching||state->vmThreadEnding||state->heapCallback||state->hostUnloadImage||state->hostRelease
            ||state->hostLoad&&state->hostLoad->calling);
    int attached=!nativeOnly&&native_vm&&(*native_vm)->GetEnv(native_vm,(void**)&env,JNI_VERSION_1_8)==JNI_OK;
    if(!attached)return !load->unknown&&host_module_borrow_load(state,load);
    jobjectArray sources=native_tooling_sources(env,load->owners,load->unknown);
    load->leaseComplete=!load->unknown&&host_module_sources_complete(env,sources);
    if(sources)load->sourceLease=(jobjectArray)native_original.NewGlobalRef(env,sources);
    if(sources)native_original.DeleteLocalRef(env,sources);
    jweak origin=native_origin(state);
    if(origin&&!native_original.ExceptionCheck(env)){jobject actual=native_original.NewLocalRef(env,origin);
        if(actual){load->origin=native_original.NewWeakGlobalRef(env,actual);native_original.DeleteLocalRef(env,actual);}}
    return load->sourceLease&&!native_original.ExceptionCheck(env)&&load->leaseComplete;
}
static void host_module_publish(HostLibraryLoad *load){
    AcquireSRWLockExclusive(&host_imports);load->next=host_library_loads;host_library_loads=load;
    ReleaseSRWLockExclusive(&host_imports);native_thread_worker_wake();
}
static void host_module_return(HostLibraryLoad *load,HMODULE actual,DWORD error){
    load->actual=actual;load->error=error;
    if(!actual)return;
    /* The documented returned-handle macros, rather than requested flags,
     * distinguish a new resource mapping from an already loaded code DLL. */
    load->resource=((uintptr_t)actual&3)!=0;
    if(load->resource)return;
    HMODULE pin=NULL;
    if(!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,(LPCSTR)actual,&pin)||pin!=actual){
        load->unknown=1;if(pin&&!host_real_free_library(pin))load->sparePin=pin;return;}
    IMAGE_DOS_HEADER *dos=(IMAGE_DOS_HEADER*)actual;
    int dll=dos->e_magic==IMAGE_DOS_SIGNATURE&&dos->e_lfanew>0;
    IMAGE_NT_HEADERS64 *nt=dll?(IMAGE_NT_HEADERS64*)((unsigned char*)actual+dos->e_lfanew):NULL;
    dll=dll&&nt->Signature==IMAGE_NT_SIGNATURE&&(nt->FileHeader.Characteristics&IMAGE_FILE_DLL)!=0;
    load->nonExecutable=(load->flags&DONT_RESOLVE_DLL_REFERENCES)!=0||!dll;
    if(load->nonExecutable){if(!host_real_free_library(pin))load->sparePin=pin;return;}
    void *targets[HOST_KIND_COUNT]={0};host_windows_targets(targets);
    int publicCarrier=0;for(int kind=1;kind<HOST_KIND_COUNT&&!publicCarrier;kind++)
        if(targets[kind])publicCarrier=native_image(targets[kind])==actual;
    if(publicCarrier){
        /* A genuine Windows API provider is a public backend, not code owned
         * by whichever consumer just acquired another aggregate reference.
         * Its caller's imports/resolver already carry that actual source.
         * Return our observation pin now, leaving the original loader ref. */
        load->publicCarrier=1;if(!host_real_free_library(pin))load->sparePin=pin;return;
    }
    AcquireSRWLockExclusive(&host_imports);
    HostImage *image=host_images;while(image&&(!image->live||image->releasing||image->handle!=actual))image=image->next;
    if(!image){image=load->preparedImage;load->preparedImage=NULL;
        image->handle=actual;image->live=1;memcpy(image->targets,targets,sizeof(targets));
        image->createTarget=targets[HOST_CREATE_W];image->createATarget=targets[HOST_CREATE_A];image->resolveTarget=targets[HOST_RESOLVE];
        image->next=host_images;host_images=image;}
    HMODULE spare=NULL;if(!image->pin)image->pin=pin;else spare=pin;
    load->image=image;load->imageNext=image->loads;image->loads=load;
    if(!image->checked)host_image_imports(image);
    InterlockedExchange(&image->retiring,host_module_image_sources_retired_locked(image));
    ReleaseSRWLockExclusive(&host_imports);
    /* This spare belongs only to Ronova. The surviving directory pin retains
     * the same image while the extra reference is returned. */
    if(spare&&!host_real_free_library(spare)){AcquireSRWLockExclusive(&host_imports);load->sparePin=spare;
        ReleaseSRWLockExclusive(&host_imports);InterlockedIncrement(&native_failures);}
}
static void host_module_mark_export(HMODULE requested,void *actual){
    if(!actual||!requested)return;
    AcquireSRWLockExclusive(&host_imports);
    for(HostImage *image=host_images;image;image=image->next)
        if(image->live&&image->pin&&image->loads&&image->handle==requested){image->opaque=1;InterlockedExchange(&image->retiring,0);}
    ReleaseSRWLockExclusive(&host_imports);
}
static int host_module_image_release_ready_locked(HostImage *image){
    if(!image->pin||image->releasing||image->opaque||InterlockedCompareExchange(&image->active,0,0))return 0;
    int found=0;
    for(HostLibraryLoad *load=image->loads;load;load=load->imageNext){
        if(!load->actual)continue;found=1;
        if(load->calling||load->borrowers||load->sparePin||load->unknown||!load->leaseComplete
                ||!host_module_producers_retired(load->owners,load->unknown))return 0;
    }return found;
}
static void host_module_loads_reap(JNIEnv **environment){
    if(!environment||!*environment)return;NativeThread *state=native_thread();if(!state)return;
    native_thread_callback_enter(state);
    for(;;){HostLibraryLoad *load=NULL;HMODULE pin=NULL;
        AcquireSRWLockExclusive(&host_imports);
        for(HostLibraryLoad *candidate=host_library_loads;candidate;candidate=candidate->next)
            if(candidate->sparePin&&!candidate->calling&&!candidate->releasing){load=candidate;load->releasing=1;pin=load->sparePin;break;}
        ReleaseSRWLockExclusive(&host_imports);if(!load)break;
        NativeToolingScope scope={load->owners,load->origin,load->unknown,state->depth,state->tooling};state->tooling=&scope;
        int released=host_real_free_library(pin)!=0;DWORD error=released?0:GetLastError();state->tooling=scope.previous;
        AcquireSRWLockExclusive(&host_imports);if(released)load->sparePin=NULL;else load->error=error;
        load->releasing=0;ReleaseSRWLockExclusive(&host_imports);
        if(!native_tooling_retirement_jni(environment)){native_thread_callback_leave(state);return;}
        if(!released)break;
    }
    for(;;){HostImage *image=NULL;HMODULE pin=NULL;OwnerLink *sources=NULL;int oldLive=0;
        AcquireSRWLockExclusive(&host_imports);
        for(HostImage *candidate=host_images;candidate;candidate=candidate->next)
            if(host_module_image_release_ready_locked(candidate)){image=candidate;break;}
        if(image){
            int ready=1;for(HostLibraryLoad *load=image->loads;load&&ready;load=load->imageNext)
                ready=native_owner_merge(&sources,load->owners);
            if(ready){image->releasing=1;host_image_private_slots_locked(image);pin=image->pin;oldLive=image->live;
                AcquireSRWLockShared(&native_records);int jni=0;
                for(NativeLibrary *library=native_libraries;library&&!jni;library=library->next)
                    jni=library->hostImage==image&&host_library_current(library,image->handle);
                ReleaseSRWLockShared(&native_records);
                if(!jni)image->live=0; /* logical tombstone before our own release */
            }else {image->opaque=1;image=NULL;InterlockedIncrement(&native_failures);}
        }
        ReleaseSRWLockExclusive(&host_imports);
        if(!image){native_owner_links_free(sources);break;}
        HostImage *previousImage=state->hostUnloadImage;state->hostUnloadImage=image;
        NativeToolingScope scope={sources,NULL,0,state->depth,state->tooling};state->tooling=&scope;
        int released=host_real_free_library(pin)!=0;DWORD error=released?0:GetLastError();state->tooling=scope.previous;
        state->hostUnloadImage=previousImage;
        AcquireSRWLockExclusive(&host_imports);
        if(released){image->pin=NULL;image->pinReleased=1;}else {image->live=oldLive;image->opaque=1;
            for(HostLibraryLoad *load=image->loads;load;load=load->imageNext)load->error=error;}
        image->releasing=0;host_image_private_slots_locked(image);InterlockedExchange(&image->retiring,0);ReleaseSRWLockExclusive(&host_imports);native_owner_links_free(sources);
        if(!native_tooling_retirement_jni(environment)){native_thread_callback_leave(state);return;}
        if(!released)break;
    }
    for(;;){HostLibraryLoad *load=NULL;
        AcquireSRWLockExclusive(&host_imports);
        for(HostLibraryLoad *candidate=host_library_loads;candidate;candidate=candidate->next){
            if(candidate->retired||candidate->releasing||candidate->calling||candidate->borrowers||candidate->sparePin)continue;
            int failed=candidate->refused||!candidate->actual;
            int done=host_module_producers_retired(candidate->owners,candidate->unknown)
                    &&(!candidate->image||!candidate->image->pin&&!candidate->image->releasing);
            if(failed||done){candidate->releasing=1;load=candidate;break;}
        }
        ReleaseSRWLockExclusive(&host_imports);if(!load)break;
        JNIEnv *env=*environment;
        if(load->sourceLease)native_original.DeleteGlobalRef(env,load->sourceLease);
        if(load->origin)native_original.DeleteWeakGlobalRef(env,load->origin);
        if(load->sourceWorker)host_thread_worker_load_drop(load->sourceWorker);
        if(load->toolingAnchor)native_thread_worker_heap_drop(load->toolingAnchor);
        if(load->tlsAnchor)host_tls_source_drop(load->tlsAnchor);
        free(load->preparedImage);
        AcquireSRWLockExclusive(&host_imports);
        load->preparedImage=NULL;load->sourceLease=NULL;load->origin=NULL;load->sourceWorker=NULL;load->toolingAnchor=NULL;load->tlsAnchor=NULL;
        if(load->sourceParent){InterlockedDecrement(&load->sourceParent->borrowers);load->sourceParent=NULL;}
        load->retired=1;load->releasing=0;ReleaseSRWLockExclusive(&host_imports);
        native_thread_worker_wake();
    }
    native_thread_callback_leave(state);
}
static void host_module_loads_state(JNIEnv *env,jobject module,jlong *values){
    AcquireSRWLockShared(&host_imports);
    for(HostLibraryLoad *load=host_library_loads;load;load=load->next){
        if(!native_owner_matches(env,load->owners,module))continue;
        if(load->actual){values[24]++;if(!load->publicCarrier)values[31]++; /* app ref lifetime not observed */}
        if(load->sparePin)values[25]++;
        if(load->resource||load->nonExecutable)values[29]++;
        if(load->calling||load->borrowers||load->sourceLease||load->sourceParent||load->sourceWorker||load->toolingAnchor||load->tlsAnchor)values[26]++;
        if(!load->retired&&(load->unknown||!load->leaseComplete||load->image&&(load->image->opaque
                ||!host_module_producers_retired(load->owners,load->unknown))))values[27]++;
        if(load->actual&&!load->resource&&!load->nonExecutable&&!load->publicCarrier&&(!load->image||load->image->failed||!load->image->checked))values[28]++;
    }
    for(HostImage *image=host_images;image;image=image->next){int match=0;
        for(HostLibraryLoad *load=image->loads;load&&!match;load=load->imageNext)match=native_owner_matches(env,load->owners,module);
        if(!match)continue;if(image->pin)values[25]++;if(image->pinReleased)values[30]++;
    }ReleaseSRWLockShared(&host_imports);
}
