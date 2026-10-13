/* Included by host.c after host_entry_association. Restricted heap callbacks
 * already have an actual native scope; discovering Java/loader provenance here
 * would enter JNI or wait on a VM/loader thread during the heap walk. Capture
 * only the existing exact image/library records and the executing C scopes.
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
    if(image->live)return 1;
    for(NativeLibraryScope *scope=state->library;scope;scope=scope->previous)
        if(scope->unloading&&host_callback_library_current_locked(state,scope->library,image))return 1;
    return 0;
}
/* Both directories are already held shared. The range was taken from this
 * observed PE image incarnation; it never resolves a new DLL, path or module.
 * Prefer the active registration for a shared caller image, as in the ordinary
 * host_capture_caller path. Otherwise retain all actual current registrations.
 */
static int host_callback_caller_locked(NativeThread *state,void *caller,OwnerLink **owners,DWORD *error){
    uintptr_t address=(uintptr_t)caller;HostImage *actual=NULL;
    for(HostImage *image=host_images;image;image=image->next){
        uintptr_t base=(uintptr_t)image->handle;
        if(!caller||address<base||address-base>=image->size||!host_callback_image_current_locked(state,image))continue;
        if(actual&&actual!=image){*error=ERROR_INVALID_DATA;return 0;}actual=image;
    }
    if(!actual){*error=ERROR_MOD_NOT_FOUND;return 0;}
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
    if(!selected)for(NativeLibrary *library=native_libraries;library;library=library->next)
        if(actual->live&&library->hostImage==actual&&host_library_current(library,actual->handle)){
            selected=1;ready=ready&&native_owner_merge(owners,library->owners);
        }
    if(!selected){*error=ERROR_MOD_NOT_FOUND;return 0;}
    if(!ready){*error=ERROR_NOT_ENOUGH_MEMORY;return 0;}return 1;
}
static int host_callback_scopes_locked(NativeThread *state,OwnerLink **owners,int *unknown,DWORD *error){
    for(unsigned i=0;i<state->depth;i++){
        Binding *binding=state->stack[i];
        if(!binding){*error=ERROR_INVALID_DATA;return 0;}
        if(!native_owner_merge(owners,binding->owners)){*error=ERROR_NOT_ENOUGH_MEMORY;return 0;}
    }
    for(NativeLibraryScope *scope=state->library;scope;scope=scope->previous){
        if(!scope->library){*error=ERROR_INVALID_DATA;return 0;}
        if(!native_owner_merge(owners,scope->library->owners)){*error=ERROR_NOT_ENOUGH_MEMORY;return 0;}
    }
    for(NativeToolingScope *scope=state->tooling;scope;scope=scope->previous){
        if(!native_owner_merge(owners,scope->owners)){*error=ERROR_NOT_ENOUGH_MEMORY;return 0;}
        if(scope->unknown)*unknown=1;
    }
    return 1;
}
static int host_callback_entry_sources(HostImport *entry,NativeThread *state,void *caller,
        OwnerLink **owners,NativeLibrary **selected,int retainSources,int threadAdmissionOnly){
    if(selected)*selected=NULL;
    if(!state||!state->heapCallback){SetLastError(ERROR_INVALID_STATE);return 0;}
    if(!TryAcquireSRWLockShared(&host_imports)){SetLastError(ERROR_BUSY);return 0;}
    if(!TryAcquireSRWLockShared(&native_records)){
        ReleaseSRWLockShared(&host_imports);SetLastError(ERROR_BUSY);return 0;
    }
    int associated=host_entry_association(entry,state,owners,selected),ready=associated==1,unknown=0;
    DWORD error=associated?ERROR_INVALID_DATA:ERROR_MOD_NOT_FOUND;
    if(ready)ready=host_callback_caller_locked(state,caller,owners,&error);
    if(ready)ready=host_callback_scopes_locked(state,owners,&unknown,&error);
    /* A normal profiler may lack Java attribution while its actual image and
     * current library are known. New thread creation only checks stopped
     * sources; it records no private worker or disposal permission. Retaining
     * sources or admitting a producer still requires the full known scope. */
    if(ready&&unknown&&(!threadAdmissionOnly||retainSources||native_has_producer(*owners))){
        error=ERROR_INVALID_DATA;ready=0;
    }
    if(ready&&!retainSources)host_producers(owners);
    ReleaseSRWLockShared(&native_records);ReleaseSRWLockShared(&host_imports);
    if(!ready){if(selected)*selected=NULL;SetLastError(error);return 0;}return 1;
}
