/* Included by host.c after host_entry_association. Heap callbacks and actual
 * ThreadEnd/Detach windows already have native scopes. Java stack discovery
 * can enter the VM during a heap walk or after the Java thread stopped being
 * queryable. Capture only existing image/library records and executing C scopes.
 */
static int host_callback_library_current_locked(NativeThread *state,NativeLibrary *library,HostImage *image){
    if(!library||library->hostImage!=image||library->handle!=image->handle)return 0;
    if(image->live&&host_library_current(library,image->handle))return 1;
    for(NativeLibraryScope *scope=state->library;scope;scope=scope->previous){
        if(scope->library!=library||scope->phase!=1||!library->jni||library->builtin
                ||!InterlockedCompareExchange(&library->alive,0,0))continue;
        if(scope->unloading&&InterlockedCompareExchange(&library->closing,0,0))return 1;
        if(image->live&&!scope->unloading&&scope->osLoadStarted&&scope->osHandle==image->handle
                &&!InterlockedCompareExchange(&library->closing,0,0))return 1;
    }
    return 0;
}
static int host_callback_image_current_locked(NativeThread *state,HostImage *image){
    if(state->hostUnloadImage==image)return 1;
    if(image->live)return 1;
    for(NativeLibraryScope *scope=state->library;scope;scope=scope->previous)
        if(scope->unloading&&host_callback_library_current_locked(state,scope->library,image))return 1;
    return 0;
}
/* Published directories retain their records as tombstones. Library owners
 * are immutable at publication; current alive/complete/stopped flags remain
 * live reads. No VM query or loader operation is used by these readers.
 * The range was taken from this
 * observed PE image incarnation; it never resolves a new DLL, path or module.
 * Prefer the active registration for a shared caller image, as in the ordinary
 * host_capture_caller path. Otherwise retain all actual current registrations.
 */
static int host_callback_caller_load(NativeThread *state,void *caller,OwnerLink **owners,int *unknown,DWORD *error,HostLibraryLoad **selectedLoad){
    if(selectedLoad)*selectedLoad=NULL;
    uintptr_t address=(uintptr_t)caller;HostImage *actual=NULL;
    for(HostImage *image=__atomic_load_n(&host_images,__ATOMIC_ACQUIRE);image;image=image->next){
        uintptr_t base=(uintptr_t)image->handle;
        if(!caller||address<base||address-base>=image->size||!host_callback_image_current_locked(state,image))continue;
        if(actual&&actual!=image){*error=ERROR_INVALID_DATA;return 0;}actual=image;
    }
    if(!actual){*error=ERROR_MOD_NOT_FOUND;return 0;}
    if(actual->loads){int child=host_module_select_locked(actual,state,NULL,owners,selectedLoad,unknown);
        if(child==1)return 1;if(child<0){*error=ERROR_NOT_ENOUGH_MEMORY;return 0;}}
    int selected=0,ready=1;
    for(unsigned i=0;i<state->depth;i++){
        Binding *binding=state->stack[i];NativeLibrary *library=binding?binding->library:NULL;
        if(host_callback_library_current_locked(state,library,actual)){
            selected=1;ready=ready&&native_owner_merge(owners,library->owners);
        }
    }
    for(NativeLibraryScope *scope=state->library;scope;scope=scope->previous)
        if(host_callback_library_current_locked(state,scope->library,actual)){
            selected=1;ready=ready&&native_owner_merge(owners,scope->library->owners);
        }
    if(!selected){NativeLibrary *workerLibrary=host_thread_worker_library(state,actual->handle);
        if(workerLibrary&&workerLibrary->hostImage==actual){
            selected=1;ready=ready&&native_owner_merge(owners,workerLibrary->owners);
        }
    }
    if(!selected){
      if(unknown)*unknown|=__atomic_load_n(&actual->relationsGap,__ATOMIC_ACQUIRE);
      for(HostImageLibrary *p=__atomic_load_n(&actual->libraries,__ATOMIC_ACQUIRE);p;p=p->next){
        NativeLibrary *library=p->library;
        if(actual->live&&library->hostImage==actual&&host_library_current(library,actual->handle)){
            selected=1;ready=ready&&native_owner_merge(owners,library->owners);
        }
      }
    }
    if(!selected){*error=ERROR_MOD_NOT_FOUND;return 0;}
    if(!ready){*error=ERROR_NOT_ENOUGH_MEMORY;return 0;}return 1;
}
static int host_callback_caller_locked(NativeThread *state,void *caller,OwnerLink **owners,int *unknown,DWORD *error){
    return host_callback_caller_load(state,caller,owners,unknown,error,NULL);
}
static int host_callback_scopes_locked(NativeThread *state,OwnerLink **owners,int *unknown,DWORD *error,NativeSourceRoots *roots){
    if(!host_tls_capture_detail(state,owners,unknown,roots?&roots->tls:NULL)){*error=ERROR_NOT_ENOUGH_MEMORY;return 0;}
    return native_capture_active_sources(state,owners,unknown,roots,error);
}
static int host_callback_entry_sources(HostImport *entry,NativeThread *state,void *caller,
        OwnerLink **owners,NativeLibrary **selected,int retainSources,int threadAdmissionOnly,int *unobserved,HostLibraryLoad **selectedLoad,NativeSourceRoots *roots){
    if(selected)*selected=NULL;
    if(!state||(!state->heapCallback&&!state->toolingRestricted&&!state->vmDetaching&&!state->vmThreadEnding&&!state->hostUnloadImage&&!state->hostRelease
            &&!(state->hostLoad&&state->hostLoad->calling))){
        SetLastError(ERROR_INVALID_STATE);return 0;
    }
    int unknown=entry->unknown;
    HostLoadSelection selection={0};
    int associated=host_entry_association_query(entry,state,owners,selected,&unknown,&selection),ready=associated==1;
    DWORD error=associated<0?ERROR_NOT_ENOUGH_MEMORY:associated?ERROR_INVALID_DATA:ERROR_MOD_NOT_FOUND;
    if(ready)ready=host_callback_caller_load(state,caller,owners,&unknown,&error,selectedLoad);
    if(ready)ready=host_callback_scopes_locked(state,owners,&unknown,&error,roots);
    if(unobserved)*unobserved|=unknown;
    /* A normal profiler may lack Java attribution while its actual image and
     * current library are known. New thread creation only checks stopped
     * sources; it records no private worker or disposal permission. Retaining
     * sources or admitting a producer still requires the full known scope. */
    if(ready&&unknown&&!unobserved&&(retainSources||native_has_producer(*owners)
            ||state->heapCallback&&!threadAdmissionOnly)){
        error=ERROR_INVALID_DATA;ready=0;
    }
    if(ready&&!retainSources)host_producers(owners);
    if(!ready){if(selected)*selected=NULL;if(selectedLoad)*selectedLoad=NULL;SetLastError(error);return 0;}return 1;
}
