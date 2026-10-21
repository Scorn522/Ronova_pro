/* Included after the existing Windows/CRT creation helpers. Only the three
 * observed local creation APIs get a source-carrying start adapter. Remote
 * creation, ordinary profilers, and incomplete source/lease paths retain their
 * original creation admission and acquire no worker disposal permission.
 *
 * https://learn.microsoft.com/en-us/cpp/c-runtime-library/reference/beginthread-beginthreadex
 * https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-exitthread
 * https://docs.oracle.com/en/java/javase/17/docs/specs/jni/invocation.html#detachcurrentthread
 */
typedef struct HostThreadWorker {
    void *start,*argument;int kind,unknown,wrapped;
    OwnerLink *owners;jobjectArray sourceLease;HMODULE image,pin;
    HostCrtThreadTarget *crt;NativeLibrary *library;void *sourceAnchor;struct HostThreadWorker *sourceParent;
    HostLibraryLoad *sourceLoad;void *sourceTlsAnchor;
    NativeThread *thread;NativeToolingScope scope;HANDLE monitor,monitorExtra;
    volatile LONG calling,created,started,returned,exited,closed,releasing;
    volatile LONG lifecycleGap,cleanupGap;LONG borrowers;DWORD error;
    struct HostThreadWorker *next;
} HostThreadWorker;

/* Fully prepared records enter a lock-free incoming queue. Directory consumers
 * drain it while holding host_records; only the daemon removes/reclaims a
 * record after the creator and actual OS worker have both finished. Publication
 * never waits for that lock, including a heap callback's unsupported-path
 * record. No JNI or loader service is used by publication.
 */
static HostThreadWorker *host_thread_workers;
static HostThreadWorker *volatile host_thread_worker_incoming;
static void native_thread_worker_wake(void);
static int native_thread_worker_backend_ready(void);
static int native_thread_worker_os_end(JNIEnv*,NativeThread*);
static int native_tooling_retirement_jni(JNIEnv**);
static jobjectArray native_tooling_sources(JNIEnv*,OwnerLink*,int);
static int native_thread_worker_heap_borrow(NativeThread*,OwnerLink*,void*,void**,HMODULE*);
static void native_thread_worker_heap_drop(void*);
static int host_tls_source_borrow(NativeThread*,OwnerLink*,void**);
static void host_tls_source_drop(void*);

static int host_is_local_worker_create(int kind){
    return kind==HOST_CREATE_THREAD||kind==HOST_BEGIN_THREAD||kind==HOST_BEGIN_THREAD_EX;
}
static HostThreadWorker *host_thread_worker_head(void){
    /* host_records is exclusive. Publishers only touch the incoming queue. */
    HostThreadWorker *incoming=(HostThreadWorker*)InterlockedExchangePointer((void *volatile*)&host_thread_worker_incoming,NULL);
    while(incoming){HostThreadWorker *next=incoming->next;incoming->next=host_thread_workers;
        host_thread_workers=incoming;incoming=next;}
    return host_thread_workers;
}
static void host_thread_worker_publish(HostThreadWorker *worker){
    HostThreadWorker *head;
    do {head=(HostThreadWorker*)InterlockedCompareExchangePointer((void *volatile*)&host_thread_worker_incoming,NULL,NULL);
        worker->next=head;}
    while(InterlockedCompareExchangePointer((void *volatile*)&host_thread_worker_incoming,worker,head)!=head);
    native_thread_worker_wake();
}
static int host_thread_worker_sources_cover(OwnerLink *retained,OwnerLink *actual){
    if(!retained||!actual)return 0;
    for(;actual;actual=actual->next){OwnerLink *found=retained;
        while(found&&found->owner!=actual->owner)found=found->next;if(!found)return 0;}
    return 1;
}
static int host_thread_worker_scope_idle(const NativeThread *state){
    /* Pointer comparison only: an abnormal OS exit can leave a scope pointer
     * into its destroyed stack. That scope is not evidence of completed work.
     */
    return state&&state->hostWorker&&(!state->tooling||state->tooling==&state->hostWorker->scope);
}
static NativeLibrary *host_thread_worker_library(NativeThread *state,HMODULE image){
    /* The current worker retains this immutable registration. This is the
     * actual creation invocation, not a synthetic JDK load scope.
     */
    HostThreadWorker *worker=state?state->hostWorker:NULL;
    NativeLibrary *library=worker?worker->library:NULL;
    return library&&worker->wrapped&&worker->image==image&&host_library_current(library,image)?library:NULL;
}
static HostLibraryLoad *host_thread_worker_child(NativeThread *state,HMODULE image){
    HostThreadWorker *worker=state?state->hostWorker:NULL;
    HostLibraryLoad *load=worker&&worker->wrapped?worker->sourceLoad:NULL;
    return load&&(!image||worker->image==image)?load:NULL;
}
static int host_thread_worker_load_borrow(NativeThread *state,OwnerLink *owners,HostThreadWorker **anchor){
    if(anchor)*anchor=NULL;if(!state||!state->hostWorker||!anchor)return 0;
    AcquireSRWLockExclusive(&host_records);HostThreadWorker *parent=state->hostWorker;
    while(parent&&parent->sourceParent)parent=parent->sourceParent;
    int ready=parent&&!parent->closed&&!parent->releasing&&!parent->unknown
            &&(parent->sourceLease||parent->sourceAnchor||parent->sourceLoad)
            &&host_module_sources_cover(parent->owners,owners);
    if(ready){parent->borrowers++;*anchor=parent;}ReleaseSRWLockExclusive(&host_records);return ready;
}
static void host_thread_worker_load_drop(HostThreadWorker *anchor){
    if(!anchor)return;AcquireSRWLockExclusive(&host_records);anchor->borrowers--;
    ReleaseSRWLockExclusive(&host_records);native_thread_worker_wake();
}
static int host_thread_worker_borrow_child(NativeThread *state,HostThreadWorker *worker,void *start){
    HostLibraryLoad *load=NULL;int unknown=0;
    if(!host_module_capture(state,start,&worker->owners,NULL,&load,&unknown)||unknown||!load)return 0;
    int ready=host_module_load_hold(load,worker->owners);
    if(ready)worker->sourceLoad=load;return ready;
}
static int host_thread_worker_start_sources(NativeLibrary *selected,NativeThread *state,
        HMODULE image,void *start,OwnerLink **owners){
    /* A real selected registration takes precedence for a shared image. The
     * existing caller helper otherwise selects current Binding/load scopes,
     * or conservatively retains the actual current registrations of the image.
     * An address identifies the mapped DLL; it does not narrow Java ownership.
     */
    AcquireSRWLockShared(&native_records);
    int specific=selected&&selected->handle==image&&InterlockedCompareExchange(&selected->alive,0,0);
    int ready=!specific||native_owner_merge(owners,selected->owners);
    ReleaseSRWLockShared(&native_records);
    return specific?ready:host_capture_caller(state,start,owners);
}
static int host_thread_worker_keep_sources(JNIEnv *env,HostThreadWorker *worker){
    jobjectArray sources=native_tooling_sources(env,worker->owners,worker->unknown);
    int ready=sources&&!native_original.ExceptionCheck(env);
    if(ready){jsize count=native_original.GetArrayLength(env,sources);
        for(jsize i=0;i<count&&ready;i++){
            jobject actual=native_original.GetObjectArrayElement(env,sources,i);
            if(!actual)ready=0;else native_original.DeleteLocalRef(env,actual);
        }
    }
    if(ready)worker->sourceLease=(jobjectArray)native_original.NewGlobalRef(env,sources);
    if(sources)native_original.DeleteLocalRef(env,sources);
    return ready&&worker->sourceLease&&!native_original.ExceptionCheck(env);
}
static int host_thread_worker_borrow_sources(NativeThread *state,HostThreadWorker *worker){
    if(!state||!state->hostWorker)return 0;
    AcquireSRWLockExclusive(&host_records);
    HostThreadWorker *parent=state->hostWorker;
    while(parent&&parent->sourceParent)parent=parent->sourceParent;
    int ready=parent&&!parent->closed&&!parent->releasing&&(parent->sourceLease||parent->sourceAnchor)
            &&!parent->unknown&&host_thread_worker_sources_cover(parent->owners,worker->owners);
    if(ready){parent->borrowers++;worker->sourceParent=parent;}
    ReleaseSRWLockExclusive(&host_records);return ready;
}

/* Returns a complete pre-call record, an explicit unwrapped lifecycle gap, or
 * NULL. No producer is narrowed out of the full source lease; carrier sources
 * do not make the already confirmed producer's entire creation untrackable.
 */
static HostThreadWorker *host_thread_worker_prepare(HostImport *entry,NativeThread *state,void *caller,
        void *start,void *argument,HostCrtThreadTarget *crt,int *refused){
    OwnerLink *owners=NULL;NativeLibrary *selected=NULL;int unknown=0;
    int captured=host_entry_sources_query(entry,state,caller,&owners,&selected,1,&unknown);
    if(!native_has_producer(owners)){native_owner_links_free(owners);return NULL;}
    HostThreadWorker *worker=(HostThreadWorker*)calloc(1,sizeof(*worker));
    if(!worker){native_owner_links_free(owners);*refused=1;SetLastError(ERROR_NOT_ENOUGH_MEMORY);return NULL;}
    worker->owners=owners;worker->kind=entry->kind;worker->start=start;worker->argument=argument;worker->crt=crt;
    worker->unknown=unknown||!captured;worker->calling=1;
    if(!captured||unknown||!start||!native_thread_worker_backend_ready()){
        worker->lifecycleGap=1;return worker;
    }
    if(state&&state->heapCallback){
        /* Borrow only the real already prepared callback pin/strong lease.
         * This path performs no JNI, global-ref creation, GetEnv or Winloader.
         */
        if(!native_thread_worker_heap_borrow(state,worker->owners,start,
                &worker->sourceAnchor,&worker->image)){
            worker->lifecycleGap=1;return worker;
        }
    }else {
        DWORD pinError=0;
        if(!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,(LPCSTR)start,&worker->pin))pinError=GetLastError();
        worker->image=worker->pin;
        if(!worker->pin||!host_thread_worker_start_sources(selected,state,worker->image,start,&worker->owners)){
            worker->error=pinError?pinError:ERROR_INVALID_DATA;worker->lifecycleGap=1;return worker;
        }
        JNIEnv *env=NULL;
        /* Detach/ThreadEnd still has the same actual native parent. It may pin
         * the exact start DLL, but cannot use JNI or Java-frame discovery to
         * manufacture a new strong source lease in this window.
         */
        int nativeOnly=state&&(state->vmDetaching||state->vmThreadEnding||state->hostRelease);
        int attached=!nativeOnly&&native_vm&&(*native_vm)->GetEnv(native_vm,(void**)&env,JNI_VERSION_1_8)==JNI_OK;
        /* An actual child receipt can carry the full already retained lease
         * into a detached worker. The source token is distinct from heap slots. */
        int child=host_thread_worker_borrow_child(state,worker,start);
        if(attached){
            if(!host_thread_worker_keep_sources(env,worker)){
                worker->error=ERROR_NOT_ENOUGH_MEMORY;*refused=1;return worker;
            }
        }else if(!child&&!host_thread_worker_borrow_sources(state,worker)
                &&!host_tls_source_borrow(state,worker->owners,&worker->sourceTlsAnchor)){
            worker->lifecycleGap=1;return worker;
        }
    }
    if(selected&&selected->handle==worker->image)worker->library=selected;
    worker->thread=(NativeThread*)calloc(1,sizeof(*worker->thread));
    if(!worker->thread){worker->error=ERROR_NOT_ENOUGH_MEMORY;*refused=1;return worker;}
    worker->thread->storageOwned=1;worker->thread->hostWorker=worker;
    worker->scope=(NativeToolingScope){worker->owners,NULL,worker->unknown,0,NULL};
    worker->thread->tooling=&worker->scope;worker->wrapped=1;return worker;
}

static int host_thread_worker_enter(HostThreadWorker *worker){
    DWORD incoming=GetLastError();HostCrtThreadErrors errors;int crt=worker->crt&&host_crt_thread_errors(worker->crt,&errors);
    InterlockedExchange(&worker->started,1);
    HANDLE monitor=NULL;
    BOOL copied=host_real_duplicate(GetCurrentProcess(),GetCurrentThread(),GetCurrentProcess(),
            &monitor,SYNCHRONIZE,FALSE,0);DWORD error=copied?0:GetLastError();
    AcquireSRWLockExclusive(&host_records);
    /* The startup copy names this running kernel thread directly. Any creator
     * backup is a second private alias, never the application's returned handle.
     */
    if(copied){worker->monitorExtra=worker->monitor;worker->monitor=monitor;}
    else if(!worker->monitor){worker->error=error;worker->lifecycleGap=1;}
    ReleaseSRWLockExclusive(&host_records);
    NativeThread *prepared=worker->thread,*state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    int ready=native_tls!=TLS_OUT_OF_INDEXES;
    if(state&&state!=prepared){
        /* A genuine pre-start JVM attachment can already own this TLS state.
         * Adopt only its actual idle state, preserving its attachment markers.
         */
        ready=ready&&!state->hostWorker&&!state->depth&&!state->tooling&&!state->library&&!state->callbackHolds;
        if(ready){AcquireSRWLockExclusive(&host_records);worker->thread=state;ReleaseSRWLockExclusive(&host_records);
            state->hostWorker=worker;state->tooling=&worker->scope;free(prepared);}
    }else state=prepared;
    if(ready)ready=TlsSetValue(native_tls,state)!=0;
    if(!ready){AcquireSRWLockExclusive(&host_records);worker->error=ERROR_INVALID_STATE;
        worker->lifecycleGap=1;if(state&&state!=prepared)worker->cleanupGap=1;
        ReleaseSRWLockExclusive(&host_records);}
    native_thread_worker_wake();
    if(crt)host_crt_thread_restore(&errors,errors.before,errors.dosBefore,incoming);else SetLastError(incoming);
    return ready&&!native_owner_stopped(worker->owners);
}
static void host_thread_worker_returned(HostThreadWorker *worker){
    DWORD incoming=GetLastError();HostCrtThreadErrors errors;int crt=worker->crt&&host_crt_thread_errors(worker->crt,&errors);
    InterlockedExchange(&worker->returned,1);native_thread_worker_wake();
    /* The persistent scope remains installed through CRT/TLS/DLL exit cleanup.
     * A C return does not close a pin, source lease, or the actual OS lifetime.
     */
    if(crt)host_crt_thread_restore(&errors,errors.before,errors.dosBefore,incoming);else SetLastError(incoming);
}
static DWORD WINAPI host_thread_worker_win_start(void *argument){
    HostThreadWorker *worker=(HostThreadWorker*)argument;DWORD result=0;
    if(host_thread_worker_enter(worker))result=((LPTHREAD_START_ROUTINE)worker->start)(worker->argument);
    host_thread_worker_returned(worker);return result;
}
static void __cdecl host_thread_worker_crt_start(void *argument){
    HostThreadWorker *worker=(HostThreadWorker*)argument;
    if(host_thread_worker_enter(worker))((HostCrtStart)worker->start)(worker->argument);
    host_thread_worker_returned(worker);
}
static unsigned __stdcall host_thread_worker_crt_ex_start(void *argument){
    HostThreadWorker *worker=(HostThreadWorker*)argument;unsigned result=0;
    if(host_thread_worker_enter(worker))result=((HostCrtStartEx)worker->start)(worker->argument);
    host_thread_worker_returned(worker);return result;
}
static uint64_t host_thread_worker_original(HostImport *entry,void *caller,uintptr_t *arguments){
    return entry->kind==HOST_CREATE_THREAD?host_native_thread_create(entry,caller,arguments):
            host_native_crt_thread_create(entry,caller,arguments);
}
static void host_thread_worker_complete_call(HostThreadWorker *worker,uint64_t result){
    int created=worker->kind==HOST_BEGIN_THREAD?result!=(uint64_t)(uintptr_t)-1:result!=0;
    if(created&&worker->wrapped&&worker->kind!=HOST_BEGIN_THREAD){
        /* Suspended Win32/ex workers need a real private witness even before
         * their start adapter runs. beginthread's auto-close return is unused.
         */
        AcquireSRWLockExclusive(&host_records);
        if(!worker->monitor){HANDLE monitor=NULL;
            if(host_real_duplicate(GetCurrentProcess(),(HANDLE)(uintptr_t)result,GetCurrentProcess(),
                    &monitor,SYNCHRONIZE,FALSE,0))worker->monitor=monitor;
            else {worker->error=GetLastError();worker->lifecycleGap=1;}
        }
        ReleaseSRWLockExclusive(&host_records);
    }
    InterlockedExchange(&worker->created,created||InterlockedCompareExchange(&worker->started,0,0));
    InterlockedExchange(&worker->calling,0);native_thread_worker_wake();
}
static uint64_t host_native_local_worker_create(HostImport *entry,void *caller,uintptr_t *arguments){
    DWORD incoming=GetLastError();NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    entry=host_consumed_entry(entry,state,caller);
    HostCrtThreadTarget *target=entry->kind==HOST_CREATE_THREAD?NULL:host_crt_thread_target(entry->original,entry->kind);
    HostCrtThreadErrors errors;int crt=target&&host_crt_thread_errors(target,&errors);
    unsigned startIndex=entry->kind==HOST_BEGIN_THREAD?0:2,argumentIndex=entry->kind==HOST_BEGIN_THREAD?2:3;
    int refused=0;
    HostThreadWorker *worker=host_thread_worker_prepare(entry,state,caller,(void*)arguments[startIndex],
            (void*)arguments[argumentIndex],target,&refused);
    if(worker)host_thread_worker_publish(worker);
    if(refused){DWORD error=worker?worker->error:GetLastError();
        uint64_t result=entry->kind==HOST_CREATE_THREAD?0:host_crt_thread_failure(entry->kind);
        if(worker)host_thread_worker_complete_call(worker,result);
        if(crt)return host_crt_thread_refuse(entry->kind,&errors,error);SetLastError(error);return result;
    }
    uintptr_t adapted[6];uintptr_t *actual=arguments;
    if(worker&&worker->wrapped){memcpy(adapted,arguments,(entry->kind==HOST_BEGIN_THREAD?3:6)*sizeof(*adapted));
        adapted[startIndex]=(uintptr_t)(entry->kind==HOST_CREATE_THREAD?(void*)host_thread_worker_win_start:
                entry->kind==HOST_BEGIN_THREAD?(void*)host_thread_worker_crt_start:(void*)host_thread_worker_crt_ex_start);
        adapted[argumentIndex]=(uintptr_t)worker;actual=adapted;
    }
    if(crt)host_crt_thread_restore(&errors,errors.before,errors.dosBefore,incoming);else SetLastError(incoming);
    uint64_t result=host_thread_worker_original(entry,caller,actual);DWORD windowsError=GetLastError();
    int crtError=crt?*errors.error:0;unsigned long dosError=crt?*errors.dosError:0;
    if(worker)host_thread_worker_complete_call(worker,result);
    if(crt)host_crt_thread_restore(&errors,crtError,dosError,windowsError);else SetLastError(windowsError);return result;
}

static int host_thread_workers_pending(void){
    int pending=0;AcquireSRWLockExclusive(&host_records);
    for(HostThreadWorker *worker=host_thread_worker_head();worker;worker=worker->next)
        if(!worker->closed&&!worker->cleanupGap
                &&(worker->calling||worker->wrapped&&(worker->monitor||worker->exited||!worker->created)
                ||!worker->created&&(worker->pin||worker->sourceLease||worker->sourceParent||worker->sourceAnchor||worker->sourceLoad||worker->sourceTlsAnchor))){pending=1;break;}
    ReleaseSRWLockExclusive(&host_records);return pending;
}
/* Only the existing attached daemon calls this. It never waits for a worker,
 * closes its application handle, detaches it from the VM, or forces it to exit.
 */
static void host_thread_workers_reap(JNIEnv **environment){
    if(!environment||!*environment)return;JNIEnv *env=*environment;
    NativeThread *daemon=native_thread();if(!daemon)return;
    native_thread_callback_enter(daemon);
    for(;;){HostThreadWorker *worker=NULL;
        AcquireSRWLockExclusive(&host_records);
        for(HostThreadWorker *candidate=host_thread_worker_head();candidate;candidate=candidate->next){
            if(candidate->closed||candidate->calling||candidate->releasing)continue;
            if(candidate->created){
                if(!candidate->wrapped)continue;
                if(!candidate->exited){
                    if(!candidate->monitor)continue;DWORD status=WaitForSingleObject(candidate->monitor,0);
                    if(status!=WAIT_OBJECT_0){if(status==WAIT_FAILED){candidate->error=GetLastError();candidate->lifecycleGap=1;}continue;}
                    candidate->exited=1;
                }
            }
            if(candidate->cleanupGap)continue;
            if(candidate->borrowers&&!candidate->thread)continue;
            candidate->releasing=1;worker=candidate;break;
        }
        ReleaseSRWLockExclusive(&host_records);if(!worker)break;
        if(worker->thread){
            /* A signaled terminal loader call still owns this exact thread's
             * heap scope until its release reaper returns that call's holds.
             * It can become signaled after the preceding release-reap pass. */
            if(host_module_exit_pending(worker->thread)){
                AcquireSRWLockExclusive(&host_records);worker->releasing=0;
                ReleaseSRWLockExclusive(&host_records);break;
            }
            int released=1;
            if(worker->created){
                /* OS exit invalidates any abandoned stack scopes. The worker
                 * scope itself resides in this retained heap record.
                 */
                released=host_thread_worker_scope_idle(worker->thread);
                if(released){worker->thread->tooling=&worker->scope;
                    released=native_thread_worker_os_end(env,worker->thread);}
            }else free(worker->thread); /* failed creation never installed TLS */
            AcquireSRWLockExclusive(&host_records);
            if(released)worker->thread=NULL;
            else {worker->cleanupGap=worker->lifecycleGap=1;worker->error=ERROR_INVALID_STATE;}
            if(!released||worker->borrowers){worker->releasing=0;ReleaseSRWLockExclusive(&host_records);continue;}
            ReleaseSRWLockExclusive(&host_records);
        }
        NativeToolingScope scope={worker->owners,NULL,worker->unknown,daemon->depth,daemon->tooling};daemon->tooling=&scope;
        int released=!worker->pin||host_real_free_library(worker->pin);DWORD error=released?0:GetLastError();daemon->tooling=scope.previous;
        if(released){AcquireSRWLockExclusive(&host_records);worker->pin=NULL;ReleaseSRWLockExclusive(&host_records);}
        if(released){AcquireSRWLockExclusive(&host_records);
            /* Close and erase the exact internal aliases atomically with the
             * directory's numeric-handle lookup. OS reuse cannot turn a stale
             * private witness into an unrelated caller's handle permission.
             */
            if(worker->monitor){released=host_real_close(worker->monitor)!=0;
                if(released)worker->monitor=NULL;else error=GetLastError();}
            if(released&&worker->monitorExtra){released=host_real_close(worker->monitorExtra)!=0;
                if(released)worker->monitorExtra=NULL;else error=GetLastError();}
            ReleaseSRWLockExclusive(&host_records);}
        if(!released){AcquireSRWLockExclusive(&host_records);worker->error=error;worker->lifecycleGap=1;
            worker->releasing=0;ReleaseSRWLockExclusive(&host_records);break;}
        /* A DLL exit callback can genuinely detach this daemon. A cached
         * JNIEnv is then unusable even though the C source scope stayed alive.
         * The existing normal-context helper obtains its real current JNI
         * environment or performs the daemon's actual attachment again.
         */
        if(!native_tooling_retirement_jni(environment)){
            AcquireSRWLockExclusive(&host_records);worker->error=ERROR_INVALID_STATE;
            worker->lifecycleGap=1;worker->releasing=0;ReleaseSRWLockExclusive(&host_records);break;
        }
        env=*environment;
        if(worker->sourceLease)native_original.DeleteGlobalRef(env,worker->sourceLease);
        if(worker->sourceAnchor)native_thread_worker_heap_drop(worker->sourceAnchor);
        if(worker->sourceTlsAnchor){host_tls_source_drop(worker->sourceTlsAnchor);worker->sourceTlsAnchor=NULL;}
        if(worker->sourceLoad){host_module_load_drop(worker->sourceLoad);worker->sourceLoad=NULL;native_thread_worker_wake();}
        AcquireSRWLockExclusive(&host_records);
        worker->pin=NULL;worker->monitor=NULL;worker->sourceLease=NULL;worker->sourceAnchor=NULL;
        if(worker->sourceParent){worker->sourceParent->borrowers--;worker->sourceParent=NULL;}
        native_owner_links_free(worker->owners);worker->owners=NULL;worker->scope.owners=NULL;
        HostThreadWorker **link=&host_thread_workers;while(*link&&*link!=worker)link=&(*link)->next;
        if(*link==worker)*link=worker->next;
        worker->closed=1;worker->releasing=0;ReleaseSRWLockExclusive(&host_records);free(worker);
    }
    native_thread_callback_leave(daemon);
}
static int host_thread_worker_internal_handle(HANDLE value){
    /* host_records is held by the existing handle consumer. */
    for(HostThreadWorker *worker=host_thread_worker_head();worker;worker=worker->next)
        if(!worker->closed&&(worker->monitor==value||worker->monitorExtra==value))return 1;
    return 0;
}
static void host_thread_workers_state(JNIEnv *env,jobject module,jlong *values){
    AcquireSRWLockExclusive(&host_records);
    for(HostThreadWorker *worker=host_thread_worker_head();worker;worker=worker->next){
        if(worker->closed||!native_owner_matches(env,worker->owners,module))continue;
        values[21]++;
        if(worker->calling||worker->created&&!worker->exited)values[22]++;
        if(worker->lifecycleGap||worker->cleanupGap)values[23]++;
    }
    ReleaseSRWLockExclusive(&host_records);
}
