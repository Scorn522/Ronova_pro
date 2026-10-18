/* Actual Host caller and whole-Mod source relationship. No object-layout,
 * allocator or byte-image authority is provided by this helper. */
static int native_state_core_sources(OwnerLink *actual,OwnerLink *complete){
    int core=0;
    for(OwnerLink *entry=actual;entry;entry=entry->next){
        OwnerLink *found=complete;while(found&&found->owner!=entry->owner)found=found->next;
        if(!found)return 0;
        if(InterlockedCompareExchange(&entry->owner->coreState,0,0))core=1;
    }
    return core;
}
/* Both directories are held. A Core frame in a different DLL is not a
 * relationship to this caller. Only its real binding/library/load can qualify
 * the observed image, with every contributor retained in the complete set. */
static int native_state_core_caller_locked(NativeThread *state,void *caller,OwnerLink *sources){
    uintptr_t address=(uintptr_t)caller;HostImage *image=NULL;
    for(HostImage *entry=host_images;entry;entry=entry->next){
        uintptr_t base=(uintptr_t)entry->handle;
        if(!caller||address<base||address-base>=entry->size||!host_callback_image_current_locked(state,entry))continue;
        if(image&&image!=entry)return 0;image=entry;
    }
    if(!image||!image->checked||image->failed)return 0;
    uintptr_t base=(uintptr_t)image->handle;
    for(unsigned i=state->depth;i>0;i--){Binding *binding=state->stack[i-1];
        uintptr_t original=binding?(uintptr_t)binding->original:0;
        if(!binding||binding->hostUnknown||original<base||original-base>=image->size)continue;
        if((binding->hostImage==image||host_callback_library_current_locked(state,binding->library,image)
                    ||host_module_load_current(binding->hostLoad,image))
                &&native_state_core_sources(binding->owners,sources))return 1;
    }
    for(NativeLibraryScope *scope=state->library;scope;scope=scope->previous)
        if(host_callback_library_current_locked(state,scope->library,image)
                &&native_state_core_sources(scope->library->owners,sources))return 1;
    NativeLibrary *worker=host_thread_worker_library(state,image->handle);
    if(worker&&host_callback_library_current_locked(state,worker,image)
            &&native_state_core_sources(worker->owners,sources))return 1;
    if(image->loads){
        OwnerLink *actual=NULL;HostLibraryLoad *selected=NULL;int unknown=0;
        int ready=host_module_select_locked(image,state,NULL,&actual,&selected,&unknown)==1
                &&!unknown&&selected&&selected->leaseComplete&&host_module_load_current(selected,image)
                &&native_state_core_sources(selected->owners,sources);
        native_owner_links_free(actual);if(ready)return 1;
    }
    NativeLibrary *selected=NULL;
    for(NativeLibrary *library=native_libraries;library;library=library->next)
        if(library->hostImage==image&&host_library_current(library,image->handle)){
            if(selected&&selected!=library)return 0;selected=library;
        }
    return selected&&native_state_core_sources(selected->owners,sources);
}
/* Shared with actual Host state consumers. This performs no Java query and
 * grants no operation/target/free authority. The caller still checks stopped
 * sources, actual kernel-object lifetime and its original operation. */
static int native_state_caller_eligible(NativeThread *state,void *caller,OwnerLink *sources,int unknown){
    if(!state||!caller||!sources||unknown)return 0;
    int restricted=state->heapCallback||state->toolingRestricted||state->vmDetaching||state->vmThreadEnding
            ||state->hostUnloadImage||state->hostRelease||state->hostLoad&&state->hostLoad->calling||host_loader_callout();
    if(restricted){if(!TryAcquireSRWLockShared(&host_imports))return 0;}
    else AcquireSRWLockShared(&host_imports);
    if(restricted){if(!TryAcquireSRWLockShared(&native_records)){ReleaseSRWLockShared(&host_imports);return 0;}}
    else AcquireSRWLockShared(&native_records);
    OwnerLink *actual=NULL;DWORD error=ERROR_INVALID_DATA;
    int ready=host_callback_caller_locked(state,caller,&actual,&unknown,&error)
            &&!unknown&&host_module_sources_cover(sources,actual)
            &&(native_has_producer(sources)||native_state_core_caller_locked(state,caller,sources));
    ReleaseSRWLockShared(&native_records);ReleaseSRWLockShared(&host_imports);
    native_owner_links_free(actual);return ready;
}
