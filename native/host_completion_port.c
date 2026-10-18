/* Observed completion ports and actual transacted-file associations. An
 * existing outside port remains borrowed; its numeric value is not a Core
 * owner. Records are tombstones, while their SAME_ACCESS witnesses live only
 * as long as the observed application aliases.
 * https://learn.microsoft.com/en-us/windows/win32/api/ioapiset/nf-ioapiset-createiocompletionport
 * https://learn.microsoft.com/en-us/windows/win32/api/ioapiset/nf-ioapiset-getqueuedcompletionstatus
 * https://learn.microsoft.com/en-us/windows/win32/api/ioapiset/nf-ioapiset-getqueuedcompletionstatusex
 * https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-setfilecompletionnotificationmodes
 */
typedef struct HostTransactionUse HostTransactionUse;
static int host_transaction_io_begin(HostImport*,void*,HANDLE,OwnerLink**,int*,HostTransactionUse**,DWORD*);
static HANDLE host_transaction_io_handle(HostTransactionUse*,HANDLE);
static void host_transaction_io_end(HostTransactionUse*,OwnerLink*,int);
static void *host_transaction_file_identity(HostTransactionUse*,uint64_t*);
static int host_transaction_io_packet(void*,ULONG_PTR,LPOVERLAPPED*);

typedef struct HostCompletionErrors {DWORD windows;int crt;unsigned long dos;} HostCompletionErrors;
static HostCompletionErrors host_completion_errors(void){
    HostCompletionErrors value;value.windows=GetLastError();value.crt=errno;value.dos=_doserrno;return value;
}
static void host_completion_restore(HostCompletionErrors value){_doserrno=value.dos;errno=value.crt;SetLastError(value.windows);}
static int host_is_completion_port(int kind){return kind>=HOST_TX_IOCP_CREATE&&kind<=HOST_TX_IOCP_NOTIFY;}
static int host_completion_restricted(void){
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    return host_loader_callout()||(state&&(state->heapCallback||state->toolingRestricted||state->vmDetaching||state->vmThreadEnding
            ||state->hostUnloadImage||state->hostRelease||state->hostLoad&&state->hostLoad->calling));
}
typedef struct HostCompletionPort HostCompletionPort;
typedef struct HostCompletionAlias {
    HANDLE value;DWORD flags;int live,closing;
    HostCompletionPort *port;struct HostCompletionAlias *next;
} HostCompletionAlias;
struct HostCompletionPort {
    uint64_t generation;HANDLE witness;int owned,closing;volatile LONG uncertain,retiring;
    unsigned pins,calls,waiters;OwnerLink *owners,*contributors;
    HostCompletionAlias *aliases;struct HostCompletionPort *next;
};
typedef struct HostCompletionUse {
    HostCompletionPort *port;HostCompletionAlias *alias;HANDLE stable;
    int pin,active,waiting,consuming,last;struct HostCompletionUse *next;
} HostCompletionUse;
typedef struct HostCompletionFile {
    void *file;uint64_t generation;HostCompletionPort *port;ULONG_PTR key;
    volatile LONG associated,modes;struct HostCompletionFile *next;
} HostCompletionFile;
static SRWLOCK host_completion_records=SRWLOCK_INIT;
static HostCompletionPort *host_completion_ports;
static HostCompletionUse *host_completion_uses;
static HostCompletionFile *volatile host_completion_files;
static uint64_t host_completion_generation;
static int host_completion_lock(int restricted){
    return restricted?TryAcquireSRWLockExclusive(&host_completion_records):(AcquireSRWLockExclusive(&host_completion_records),1);
}
static void host_completion_gap(OwnerLink *owners,int restricted){host_source_gap(owners,restricted);}
static HostCompletionAlias *host_completion_alias(HANDLE value){
    for(HostCompletionPort *port=host_completion_ports;port;port=port->next)
        for(HostCompletionAlias *alias=port->aliases;alias;alias=alias->next)
            if(alias->live&&alias->value==value)return alias;
    return NULL;
}
static int host_completion_aliases(HostCompletionPort *port){
    for(HostCompletionAlias *alias=port->aliases;alias;alias=alias->next)if(alias->live)return 1;return 0;
}
static int host_completion_private(HANDLE value){
    if(!value||value==INVALID_HANDLE_VALUE)return 0;
    for(HostCompletionPort *port=host_completion_ports;port;port=port->next)
        if(__atomic_load_n(&port->witness,__ATOMIC_ACQUIRE)==value)return 1;
    for(HostCompletionUse *use=host_completion_uses;use;use=use->next)
        if(__atomic_load_n(&use->stable,__ATOMIC_ACQUIRE)==value)return 1;
    return 0;
}
static int host_completion_same_sources(OwnerLink *first,OwnerLink *second){
    return first&&second&&host_owners_contain(first,second)&&host_owners_contain(second,first);
}
static HANDLE host_completion_retire(HostCompletionPort *port){
    HANDLE witness=__atomic_load_n(&port->witness,__ATOMIC_ACQUIRE);
    return witness&&!InterlockedCompareExchange(&port->retiring,1,0)?witness:NULL;
}
/* Keep the witness in the private-handle directory through its actual Close;
 * the retiring bit elects one closer without exposing a still-live witness. */
static void host_completion_retire_close(HostCompletionPort *port,HANDLE witness,OwnerLink *owners,int restricted){
    if(!witness)return;
    if(host_real_close(witness)){
        __atomic_store_n(&port->witness,NULL,__ATOMIC_RELEASE);InterlockedExchange(&port->retiring,0);
    }else {InterlockedExchange(&port->uncertain,1);host_completion_gap(owners,restricted);}
}
static int host_completion_sources(HostImport *entry,void *caller,OwnerLink **owners,int *unknown,int restricted){
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    if(restricted)return host_callback_entry_sources(entry,state,caller,owners,NULL,1,0,unknown);
    return host_entry_sources_query(entry,state,caller,owners,NULL,1,unknown);
}
static int host_completion_eligible(void *caller,OwnerLink *owners,int unknown){
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    return native_state_caller_eligible(state,caller,owners,unknown);
}
static void host_completion_use_end(HostCompletionUse *use,OwnerLink *contributors,int uncertain){
    if(!use)return;HostCompletionErrors saved=host_completion_errors();int restricted=host_completion_restricted();
    HANDLE stable=__atomic_load_n(&use->stable,__ATOMIC_ACQUIRE);
    if(stable&&!host_real_close(stable)){
        InterlockedExchange(&use->port->uncertain,1);host_completion_gap(contributors,restricted);host_completion_restore(saved);return;
    }
    __atomic_store_n(&use->stable,NULL,__ATOMIC_RELEASE);
    if(!host_completion_lock(restricted)){
        InterlockedExchange(&use->port->uncertain,1);host_completion_gap(contributors,restricted);host_completion_restore(saved);return;
    }
    HostCompletionPort *port=use->port;
    if(uncertain)InterlockedExchange(&port->uncertain,1);
    if(!native_owner_merge(&port->contributors,contributors))InterlockedExchange(&port->uncertain,1);
    if(use->pin&&port->pins)port->pins--;
    if(use->active&&port->calls)port->calls--;
    if(use->waiting&&port->waiters)port->waiters--;
    if(use->consuming){use->alias->closing=0;port->closing=0;}
    HostCompletionUse **link=&host_completion_uses;while(*link&&*link!=use)link=&(*link)->next;if(*link)*link=use->next;
    HANDLE retired=NULL;
    if(!host_completion_aliases(port)&&!port->pins&&port->calls==port->waiters)
        retired=host_completion_retire(port);
    ReleaseSRWLockExclusive(&host_completion_records);
    host_completion_retire_close(port,retired,contributors,restricted);
    free(use);host_completion_restore(saved);
}
/* Waits use the actual public handle, after releasing the identity duplicate.
 * Waiters retain only tombstone metadata and do not prevent a real last Close
 * from releasing our witness and producing ERROR_ABANDONED_WAIT_0. */
static int host_completion_use_begin(HostImport *entry,void *caller,HANDLE value,int consuming,
        OwnerLink **owners,int *unknown,HostCompletionUse **hold,DWORD *error){
    *hold=NULL;*error=0;int restricted=host_completion_restricted();
    if(!host_completion_lock(restricted)){*error=ERROR_BUSY;return 0;}
    if(host_completion_private(value)){ReleaseSRWLockExclusive(&host_completion_records);*error=ERROR_ACCESS_DENIED;return 0;}
    HostCompletionAlias *alias=host_completion_alias(value);
    if(!alias){ReleaseSRWLockExclusive(&host_completion_records);return 1;}
    HostCompletionPort *port=alias->port;
    if(alias->closing||port->closing||port->retiring||!port->witness){ReleaseSRWLockExclusive(&host_completion_records);*error=ERROR_BUSY;return 0;}
    HostCompletionUse *use=(HostCompletionUse*)calloc(1,sizeof(*use));
    if(!use){ReleaseSRWLockExclusive(&host_completion_records);*error=ERROR_NOT_ENOUGH_MEMORY;return 0;}
    use->port=port;use->alias=alias;use->pin=1;port->pins++;
    use->next=host_completion_uses;host_completion_uses=use;HANDLE witness=port->witness;
    ReleaseSRWLockExclusive(&host_completion_records);
    HANDLE stable=NULL;
    if(!host_real_duplicate(GetCurrentProcess(),value,GetCurrentProcess(),&stable,0,FALSE,DUPLICATE_SAME_ACCESS)){
        *error=GetLastError();if(!*error)*error=ERROR_INVALID_HANDLE;host_completion_use_end(use,NULL,0);return 0;
    }
    __atomic_store_n(&use->stable,stable,__ATOMIC_RELEASE);
    HostCompareHandles compare=__atomic_load_n(&host_compare_handles,__ATOMIC_ACQUIRE);
    SetLastError(ERROR_SUCCESS);int same=compare&&compare(stable,witness);DWORD compared=GetLastError();
    if(!same){
        if(!compare||compared!=ERROR_NOT_SAME_OBJECT){
            *error=!compare?ERROR_NOT_SUPPORTED:compared?compared:ERROR_INVALID_DATA;host_completion_use_end(use,NULL,0);return 0;
        }
        HANDLE retired=NULL;
        if(host_completion_lock(restricted)){
            alias->live=0;InterlockedExchange(&port->uncertain,1);
            if(!host_completion_aliases(port)&&port->pins==1&&!port->calls)
                retired=host_completion_retire(port);
            ReleaseSRWLockExclusive(&host_completion_records);
        }else InterlockedExchange(&port->uncertain,1);
        host_completion_retire_close(port,retired,port->owners,restricted);
        host_completion_gap(port->owners,restricted);host_completion_use_end(use,NULL,0);return 1;
    }
    if(port->owned&&!host_completion_sources(entry,caller,owners,unknown,restricted)){
        *error=GetLastError();if(!*error)*error=ERROR_INVALID_DATA;host_completion_use_end(use,NULL,0);return 0;
    }
    int eligible=!port->owned||host_completion_eligible(caller,*owners,*unknown);
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    int cleanup=entry->kind==HOST_CLOSE&&eligible&&host_memory_cleanup_scope(entry,state);
    if(!host_completion_lock(restricted)){*error=ERROR_BUSY;host_completion_use_end(use,NULL,0);return 0;}
    if(port->owned&&(*unknown||!eligible||!host_completion_same_sources(port->owners,*owners)))*error=ERROR_ACCESS_DENIED;
    else if(port->owned&&port->uncertain)*error=ERROR_INVALID_DATA;
    else if(port->owned&&(native_owner_stopped(*owners)||native_owner_stopped(port->owners)
            ||native_owner_stopped(port->contributors))&&!cleanup)*error=ERROR_ACCESS_DENIED;
    else if(alias->closing||port->closing||port->witness!=witness)*error=ERROR_BUSY;
    else if(consuming&&(port->pins!=1||port->calls!=port->waiters))*error=ERROR_BUSY;
    if(!*error){
        port->pins--;use->pin=0;port->calls++;use->active=1;
        if(consuming){use->consuming=1;alias->closing=1;port->closing=1;use->last=1;
            for(HostCompletionAlias *other=port->aliases;other;other=other->next)if(other->live&&other!=alias){use->last=0;break;}}
    }
    ReleaseSRWLockExclusive(&host_completion_records);
    if(*error){host_completion_use_end(use,NULL,0);return 0;}
    *hold=use;return 1;
}
/* Output validation/allocation occurs while this is still a short ordinary
 * use. Only the final call boundary changes it to a close-compatible wait. */
static DWORD host_completion_wait_enter(HostCompletionUse *use,int restricted){
    if(!use)return 0;if(!host_completion_lock(restricted))return ERROR_BUSY;
    if(use->port->closing||use->alias->closing){ReleaseSRWLockExclusive(&host_completion_records);return ERROR_BUSY;}
    use->waiting=1;use->port->waiters++;HANDLE stable=__atomic_load_n(&use->stable,__ATOMIC_ACQUIRE);
    ReleaseSRWLockExclusive(&host_completion_records);
    if(stable&&!host_real_close(stable))return GetLastError();
    __atomic_store_n(&use->stable,NULL,__ATOMIC_RELEASE);return 0;
}
/* A real successful observation is required even for a borrowed port. Existing
 * observations are never promoted when somebody supplies the same handle. */
static HostCompletionPort *host_completion_observe(HANDLE value,OwnerLink *owners,int owned,int restricted){
    HostCompletionPort *prepared=(HostCompletionPort*)calloc(1,sizeof(*prepared));
    HostCompletionAlias *alias=(HostCompletionAlias*)calloc(1,sizeof(*alias));
    HANDLE witness=NULL;DWORD flags=0;int ready=prepared&&alias;
    if(ready&&owned)ready=native_owner_merge(&prepared->owners,owners);
    if(ready)ready=host_real_duplicate(GetCurrentProcess(),value,GetCurrentProcess(),&witness,0,FALSE,DUPLICATE_SAME_ACCESS);
    if(ready)ready=GetHandleInformation(value,&flags)!=FALSE;
    HostCompletionPort *found=NULL,*candidate=NULL;HostCompletionAlias *expected=NULL;HANDLE previousWitness=NULL;
    HANDLE retired=NULL;int snapshot=0,same=0,mismatch=0;
    if(ready&&host_completion_lock(restricted)){
        HostCompletionAlias *previous=host_completion_alias(value);
        snapshot=1;
        if(previous){
            if(!previous->closing&&!previous->port->closing&&!previous->port->retiring&&previous->port->witness){
                expected=previous;candidate=previous->port;candidate->pins++;previousWitness=candidate->witness;
            }else ready=0;
        }
        ReleaseSRWLockExclusive(&host_completion_records);
    }else ready=0;
    if(candidate){
        HostCompareHandles compare=__atomic_load_n(&host_compare_handles,__ATOMIC_ACQUIRE);
        SetLastError(ERROR_SUCCESS);same=compare&&compare(witness,previousWitness);DWORD compared=GetLastError();
        mismatch=!same&&compare&&compared==ERROR_NOT_SAME_OBJECT;
        if(!same&&!mismatch)ready=0;
    }
    if(snapshot&&host_completion_lock(restricted)){
        if(candidate&&candidate->pins)candidate->pins--;
        HostCompletionAlias *previous=host_completion_alias(value);
        if(ready&&same&&previous==expected&&!previous->closing&&!candidate->closing)found=candidate;
        else if(ready&&((!expected&&!previous)||(mismatch&&previous==expected&&!previous->closing&&!candidate->closing))){
            if(host_completion_generation==UINT64_MAX)ready=0;
            else {
                /* Only an actual NOT_SAME_OBJECT result retires an observed
                 * older alias. A failed comparison retains the old record. */
                if(mismatch){
                previous->live=0;InterlockedExchange(&previous->port->uncertain,1);
                if(!host_completion_aliases(previous->port)&&!previous->port->pins
                        &&previous->port->calls==previous->port->waiters)
                    retired=host_completion_retire(previous->port);
                }
                prepared->generation=++host_completion_generation;prepared->witness=witness;witness=NULL;prepared->owned=owned;
                alias->value=value;alias->flags=flags;alias->live=1;alias->port=prepared;prepared->aliases=alias;
                prepared->next=host_completion_ports;host_completion_ports=prepared;found=prepared;prepared=NULL;alias=NULL;
            }
        }else ready=0;
        ReleaseSRWLockExclusive(&host_completion_records);
    }else {if(candidate)InterlockedExchange(&candidate->uncertain,1);ready=0;}
    host_completion_retire_close(candidate,retired,owners,restricted);
    if(witness&&!host_real_close(witness))host_completion_gap(owners,restricted);
    if(prepared){native_owner_links_free(prepared->owners);free(prepared);}free(alias);
    if(!ready||!found)host_completion_gap(owners,restricted);return found;
}
static HostCompletionFile *host_completion_file_find(void *file,uint64_t generation){
    for(HostCompletionFile *row=__atomic_load_n(&host_completion_files,__ATOMIC_ACQUIRE);row;row=row->next)
        if(row->file==file&&row->generation==generation)return row;
    return NULL;
}
/* Exact transaction object incarnations, rather than numeric file handles,
 * identify these immutable associations. Modes only accumulate after success.
 * This append-only acquire snapshot makes no OS/JNI call or blocking lock. */
static void *host_completion_file_port(HostTransactionUse *use,ULONG_PTR *key,UCHAR *modes){
    if(key)*key=0;if(modes)*modes=0;uint64_t generation=0;
    void *file=host_transaction_file_identity(use,&generation);if(!file||!generation)return NULL;
    HostCompletionFile *row=host_completion_file_find(file,generation);if(!row)return NULL;
    if(modes)*modes=(UCHAR)InterlockedCompareExchange(&row->modes,0,0);
    if(!InterlockedCompareExchange(&row->associated,0,0))return NULL;
    if(key)*key=row->key;return row->port;
}
static int host_completion_file_publish(HostCompletionFile **prepared,void *file,uint64_t generation,
        HostCompletionPort *port,ULONG_PTR key,UCHAR modes,int associate,int restricted){
    if(!host_completion_lock(restricted))return 0;
    HostCompletionFile *row=host_completion_file_find(file,generation);
    if(!row){
        row=*prepared;
        if(row){row->file=file;row->generation=generation;row->next=__atomic_load_n(&host_completion_files,__ATOMIC_ACQUIRE);
            __atomic_store_n(&host_completion_files,row,__ATOMIC_RELEASE);*prepared=NULL;}
    }
    int ready=row!=NULL;
    if(row){
        if(associate&&!InterlockedCompareExchange(&row->associated,0,0)){
            row->key=key;row->port=port;InterlockedExchange(&row->associated,1);
        }else if(associate&&row->port!=port)ready=0;
        if(!associate)InterlockedOr(&row->modes,modes);
    }
    ReleaseSRWLockExclusive(&host_completion_records);return ready;
}
typedef struct HostCompletionOutput {void *address;SIZE_T bytes;} HostCompletionOutput;
static DWORD host_completion_outputs(HostCompletionOutput *outputs,unsigned count,int restricted,int locked){
    for(unsigned i=0;i<count;i++)if(outputs[i].address&&outputs[i].bytes){
        int hit=locked?host_memory_output_locked(outputs[i].address,outputs[i].bytes)
                :host_memory_range(outputs[i].address,outputs[i].bytes,0,restricted);
        if(hit)return hit>0?ERROR_ACCESS_DENIED:restricted?ERROR_BUSY:ERROR_INVALID_DATA;
    }return 0;
}
static int host_completion_read(const void *address,void *value,SIZE_T bytes){
    SIZE_T copied=0;return !address||host_real_read_process_memory(GetCurrentProcess(),address,value,bytes,&copied)&&copied==bytes;
}
static int host_completion_copy(void *address,const void *value,SIZE_T bytes){
    SIZE_T copied=0;return !address||!bytes||host_real_write_process_memory(GetCurrentProcess(),address,value,bytes,&copied)&&copied==bytes;
}
static uint64_t host_completion_associate(HostImport *entry,void *caller,uintptr_t *a){
    HostCompletionErrors incoming=host_completion_errors(),outgoing=incoming;int restricted=host_completion_restricted();
    int notification=entry->kind==HOST_TX_IOCP_NOTIFY,unknown=0,called=0,returned=0,eligible=0;
    OwnerLink *owners=NULL;HostTransactionUse *fileUse=NULL;HostCompletionUse *portUse=NULL;
    HostCompletionFile *prepared=NULL;void *file=NULL;uint64_t generation=0;DWORD error=0;uint64_t result=0;
    __try {
        if((notification||(HANDLE)a[0]!=INVALID_HANDLE_VALUE)
                &&!host_transaction_io_begin(entry,caller,(HANDLE)a[0],&owners,&unknown,&fileUse,&error)){
            if(!error)error=ERROR_INVALID_DATA;
        }
        if(!error&&fileUse){
            file=host_transaction_file_identity(fileUse,&generation);
            if(!file||!generation)error=ERROR_INVALID_DATA;
            else if(!host_completion_file_find(file,generation)){
                prepared=(HostCompletionFile*)calloc(1,sizeof(*prepared));if(!prepared)error=ERROR_NOT_ENOUGH_MEMORY;
            }
        }
        if(!error&&!notification&&a[1]
                &&!host_completion_use_begin(entry,caller,(HANDLE)a[1],0,&owners,&unknown,&portUse,&error)){
            if(!error)error=ERROR_INVALID_DATA;
        }
        if(!error&&!notification&&!a[1]){
            int captured=fileUse!=NULL||host_completion_sources(entry,caller,&owners,&unknown,restricted);
            eligible=captured&&!unknown&&host_completion_eligible(caller,owners,unknown);
            if(native_owner_stopped(owners))error=ERROR_ACCESS_DENIED;
        }
        HANDLE fileHandle=host_transaction_io_handle(fileUse,(HANDLE)a[0]);
        if(!error&&(fileUse||portUse||eligible)&&native_owner_stopped(owners))error=ERROR_ACCESS_DENIED;
        if(!error){
            host_completion_restore(incoming);called=1;
            if(notification)result=((BOOL (WINAPI*)(HANDLE,UCHAR))entry->original)(fileHandle,(UCHAR)a[1]);
            else result=(uintptr_t)((HANDLE (WINAPI*)(HANDLE,HANDLE,ULONG_PTR,DWORD))entry->original)(
                    fileHandle,(HANDLE)a[1],(ULONG_PTR)a[2],(DWORD)a[3]);
            outgoing=host_completion_errors();returned=1;
            if(result){
                if(notification){
                    if(file&&!host_completion_file_publish(&prepared,file,generation,NULL,0,(UCHAR)a[1],0,restricted))
                        host_completion_gap(owners,restricted);
                }else {
                    HostCompletionPort *port=portUse?portUse->port:host_completion_observe((HANDLE)(uintptr_t)result,owners,!a[1]&&eligible,restricted);
                    if(file&&(!port||!host_completion_file_publish(&prepared,file,generation,port,(ULONG_PTR)a[2],0,1,restricted)))
                        host_completion_gap(owners,restricted);
                    if(!file&&(HANDLE)a[0]!=INVALID_HANDLE_VALUE)host_completion_gap(owners,restricted);
                }
            }
        }else outgoing.windows=error;
    } __finally {
        if(called&&!returned){outgoing=host_completion_errors();host_completion_gap(owners,restricted);}
        host_completion_use_end(portUse,called?owners:NULL,called&&!returned);
        host_transaction_io_end(fileUse,called?owners:NULL,called&&!returned);
        free(prepared);native_owner_links_free(owners);host_completion_restore(outgoing);
    }
    return result;
}
typedef struct HostCompletionResult {DWORD bytes;ULONG_PTR key;LPOVERLAPPED op;ULONG removed;} HostCompletionResult;
static uint64_t host_completion_dequeue(HostImport *entry,void *caller,uintptr_t *a){
    HostCompletionErrors incoming=host_completion_errors(),outgoing=incoming;int restricted=host_completion_restricted();
    int multiple=entry->kind==HOST_TX_IOCP_GET_EX,unknown=0,called=0,returned=0;BOOL result=FALSE;
    OwnerLink *owners=NULL;HostCompletionUse *use=NULL;DWORD error=0;
    HostCompletionResult *stage=NULL;LPOVERLAPPED_ENTRY entries=NULL;SIZE_T arrayBytes=0;
    HostCompletionOutput outputs[3];unsigned outputCount=0;
    if(multiple){
        if((ULONG)a[2]>SIZE_MAX/sizeof(*entries))error=ERROR_INVALID_PARAMETER;
        else arrayBytes=(SIZE_T)(ULONG)a[2]*sizeof(*entries);
        outputs[outputCount++]=(HostCompletionOutput){(void*)a[1],arrayBytes};
        outputs[outputCount++]=(HostCompletionOutput){(void*)a[3],sizeof(ULONG)};
    }else {
        outputs[outputCount++]=(HostCompletionOutput){(void*)a[1],sizeof(DWORD)};
        outputs[outputCount++]=(HostCompletionOutput){(void*)a[2],sizeof(ULONG_PTR)};
        outputs[outputCount++]=(HostCompletionOutput){(void*)a[3],sizeof(LPOVERLAPPED)};
    }
    __try {
        if(!error&&!host_completion_use_begin(entry,caller,(HANDLE)a[0],0,&owners,&unknown,&use,&error)){
            if(!error)error=ERROR_INVALID_DATA;
        }
        if(!error)error=host_completion_outputs(outputs,outputCount,restricted,0);
        if(!error){stage=(HostCompletionResult*)calloc(1,sizeof(*stage));if(!stage)error=ERROR_NOT_ENOUGH_MEMORY;}
        if(!error&&multiple&&arrayBytes&&a[1]){entries=(LPOVERLAPPED_ENTRY)calloc(1,arrayBytes);if(!entries)error=ERROR_NOT_ENOUGH_MEMORY;}
        if(!error){
            int seeded=multiple?host_completion_read((void*)a[3],&stage->removed,sizeof(stage->removed))
                    :host_completion_read((void*)a[1],&stage->bytes,sizeof(stage->bytes))
                    &&host_completion_read((void*)a[2],&stage->key,sizeof(stage->key))
                    &&host_completion_read((void*)a[3],&stage->op,sizeof(stage->op));
            if(!seeded)error=ERROR_NOACCESS;
        }
        if(!error)error=host_completion_wait_enter(use,restricted);
        if(!error){
            host_completion_restore(incoming);called=1;
            if(multiple)result=((BOOL (WINAPI*)(HANDLE,LPOVERLAPPED_ENTRY,ULONG,PULONG,DWORD,BOOL))entry->original)(
                    (HANDLE)a[0],a[1]?entries:NULL,(ULONG)a[2],a[3]?&stage->removed:NULL,(DWORD)a[4],(BOOL)a[5]);
            else result=((BOOL (WINAPI*)(HANDLE,LPDWORD,PULONG_PTR,LPOVERLAPPED*,DWORD))entry->original)(
                    (HANDLE)a[0],a[1]?&stage->bytes:NULL,a[2]?&stage->key:NULL,a[3]?&stage->op:NULL,(DWORD)a[4]);
            outgoing=host_completion_errors();returned=1;
            void *tag=use?use->port:NULL;
            if(multiple){
                if(result&&entries&&stage->removed<=(ULONG)a[2])for(ULONG i=0;i<stage->removed;i++)
                    host_transaction_io_packet(tag,entries[i].lpCompletionKey,&entries[i].lpOverlapped);
                else if(result){result=FALSE;error=ERROR_INVALID_DATA;host_completion_gap(owners,restricted);}
            }else if(stage->op)host_transaction_io_packet(tag,stage->key,&stage->op);
            if(!error)error=host_completion_outputs(outputs,outputCount,restricted,0);
            if(!error&&(native_owner_stopped(owners)))error=ERROR_ACCESS_DENIED;
            if(!error){
                int locked=restricted?TryAcquireSRWLockExclusive(&native_private_gate):(AcquireSRWLockExclusive(&native_private_gate),1);
                if(!locked)error=ERROR_BUSY;
                else {
                    error=host_completion_outputs(outputs,outputCount,restricted,1);
                    if(!error){
                        int copied=multiple?(!result||!stage->removed||host_completion_copy((void*)a[1],entries,(SIZE_T)stage->removed*sizeof(*entries)))
                                &&host_completion_copy((void*)a[3],&stage->removed,sizeof(stage->removed))
                                :host_completion_copy((void*)a[1],&stage->bytes,sizeof(stage->bytes))
                                &&host_completion_copy((void*)a[2],&stage->key,sizeof(stage->key))
                                &&host_completion_copy((void*)a[3],&stage->op,sizeof(stage->op));
                        if(!copied)error=ERROR_NOACCESS;
                    }
                    ReleaseSRWLockExclusive(&native_private_gate);
                }
            }
            if(error){result=FALSE;outgoing.windows=error;host_completion_gap(owners,restricted);}
        }else outgoing.windows=error;
    } __finally {
        if(called&&!returned){outgoing=host_completion_errors();host_completion_gap(owners,restricted);}
        host_completion_use_end(use,called?owners:NULL,called&&!returned);
        free(entries);free(stage);native_owner_links_free(owners);host_completion_restore(outgoing);
    }
    return result;
}
static uint64_t host_completion_post(HostImport *entry,void *caller,uintptr_t *a){
    HostCompletionErrors incoming=host_completion_errors(),outgoing=incoming;int restricted=host_completion_restricted();
    OwnerLink *owners=NULL;HostCompletionUse *use=NULL;int unknown=0,called=0,returned=0;DWORD error=0;BOOL result=FALSE;
    __try {
        if(!host_completion_use_begin(entry,caller,(HANDLE)a[0],0,&owners,&unknown,&use,&error)){
            if(!error)error=ERROR_INVALID_DATA;
        }
        /* This is a pointer VALUE supplied by the caller, not an OVERLAPPED to
         * read. Arbitrary public values and posted bytes/key stay unchanged. */
        if(!error&&a[3]){
            int hit=host_memory_range((void*)a[3],1,0,restricted);
            if(hit)error=hit>0?ERROR_ACCESS_DENIED:restricted?ERROR_BUSY:ERROR_INVALID_DATA;
        }
        if(!error&&native_owner_stopped(owners))error=ERROR_ACCESS_DENIED;
        if(!error){
            HANDLE port=use?use->stable:(HANDLE)a[0];host_completion_restore(incoming);called=1;
            result=((BOOL (WINAPI*)(HANDLE,DWORD,ULONG_PTR,LPOVERLAPPED))entry->original)(port,(DWORD)a[1],(ULONG_PTR)a[2],(LPOVERLAPPED)a[3]);
            outgoing=host_completion_errors();returned=1;
        }else outgoing.windows=error;
    } __finally {
        if(called&&!returned){outgoing=host_completion_errors();host_completion_gap(owners,restricted);}
        host_completion_use_end(use,called?owners:NULL,called&&!returned);native_owner_links_free(owners);host_completion_restore(outgoing);
    }
    return result;
}
static uint64_t host_native_completion_port(HostImport *entry,void *caller,uintptr_t *a){
    HostCompletionErrors incoming=host_completion_errors();int restricted=host_completion_restricted();
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    if(!restricted)entry=host_consumed_entry(entry,state,caller);
    if(entry->original!=__atomic_load_n(&host_windows_api[entry->kind],__ATOMIC_ACQUIRE)){
        incoming.windows=ERROR_INVALID_DATA;host_completion_restore(incoming);return 0;
    }
    host_completion_restore(incoming);
    if(entry->kind==HOST_TX_IOCP_CREATE||entry->kind==HOST_TX_IOCP_NOTIFY)return host_completion_associate(entry,caller,a);
    if(entry->kind==HOST_TX_IOCP_GET||entry->kind==HOST_TX_IOCP_GET_EX)return host_completion_dequeue(entry,caller,a);
    if(entry->kind==HOST_TX_IOCP_POST)return host_completion_post(entry,caller,a);
    SetLastError(ERROR_INVALID_FUNCTION);return 0;
}
static int host_completion_port_handle(HostImport *entry,void *caller,uintptr_t *a,uint64_t *result){
    int kind=entry->kind;if(kind!=HOST_CLOSE&&kind!=HOST_DUPLICATE&&kind!=HOST_HANDLE_FLAGS)return 0;
    HostCompletionErrors incoming=host_completion_errors(),outgoing=incoming;int restricted=host_completion_restricted();
    int duplicate=kind==HOST_DUPLICATE;
    if(duplicate&&(HANDLE)a[0]!=GetCurrentProcess()&&!host_local_process((HANDLE)a[0])){host_completion_restore(incoming);return 0;}
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    if(!restricted)entry=host_consumed_entry(entry,state,caller);
    int consuming=kind==HOST_CLOSE||duplicate&&((DWORD)a[6]&DUPLICATE_CLOSE_SOURCE);
    OwnerLink *owners=NULL;HostCompletionUse *use=NULL;int unknown=0,called=0,returned=0;DWORD error=0;
    int admitted=host_completion_use_begin(entry,caller,(HANDLE)a[duplicate?1:0],consuming,&owners,&unknown,&use,&error);
    if(admitted&&!use){native_owner_links_free(owners);host_completion_restore(incoming);return 0;}
    *result=FALSE;
    if(entry->original!=__atomic_load_n(&host_windows_api[kind],__ATOMIC_ACQUIRE))error=ERROR_INVALID_DATA;
    int targetLocal=duplicate&&a[2]&&((HANDLE)a[2]==GetCurrentProcess()||host_local_process((HANDLE)a[2]));
    HostCompletionAlias *prepared=NULL;HANDLE retire=NULL;
    __try {
        if(!error&&duplicate&&a[3]){
            int hit=host_memory_range((void*)a[3],sizeof(HANDLE),0,restricted);
            if(hit)error=hit>0?ERROR_ACCESS_DENIED:restricted?ERROR_BUSY:ERROR_INVALID_DATA;
        }
        if(!error&&duplicate&&targetLocal&&a[3]){
            prepared=(HostCompletionAlias*)calloc(1,sizeof(*prepared));if(!prepared)error=ERROR_NOT_ENOUGH_MEMORY;
        }
        if(!error){
            host_completion_restore(incoming);called=1;*result=host_call_handle(entry,a);outgoing=host_completion_errors();returned=1;
            int sourceClosed=kind==HOST_CLOSE?*result!=0:consuming;
            HANDLE output=NULL;DWORD flags=0;int known=0;
            if(duplicate&&*result&&targetLocal&&a[3]&&host_completion_read((void*)a[3],&output,sizeof(output))
                    &&output&&output!=INVALID_HANDLE_VALUE){known=host_same_handle(output,use->stable)&&GetHandleInformation(output,&flags);}
            if(host_completion_lock(restricted)){
                if(sourceClosed)use->alias->live=0;
                if(duplicate&&*result&&known&&prepared){
                    HostCompletionAlias *previous=host_completion_alias(output);
                    if(previous){previous->live=0;InterlockedExchange(&previous->port->uncertain,1);}
                    prepared->value=output;prepared->flags=flags;prepared->live=1;prepared->port=use->port;
                    prepared->next=use->port->aliases;use->port->aliases=prepared;prepared=NULL;
                }
                if(kind==HOST_HANDLE_FLAGS&&*result){DWORD mask=(DWORD)a[1];use->alias->flags=(use->alias->flags&~mask)|((DWORD)a[2]&mask);}
                if(sourceClosed&&!host_completion_aliases(use->port))retire=host_completion_retire(use->port);
                ReleaseSRWLockExclusive(&host_completion_records);
            }else {
                InterlockedExchange(&use->port->uncertain,1);
                if(sourceClosed){__atomic_store_n(&use->alias->live,0,__ATOMIC_RELEASE);
                    if(use->last)retire=host_completion_retire(use->port);}
                host_completion_gap(owners,restricted);
            }
            /* Neither the directory witness nor this call's identity duplicate
             * may outlive the last real app alias and strand a blocked dequeue. */
            host_completion_retire_close(use->port,retire,owners,restricted);
            if(duplicate&&*result&&(!targetLocal||!known))host_completion_gap(owners,restricted);
            if(kind==HOST_HANDLE_FLAGS&&*result&&((DWORD)a[1]&HANDLE_FLAG_INHERIT)&&((DWORD)a[2]&HANDLE_FLAG_INHERIT))
                host_completion_gap(owners,restricted);
        }else outgoing.windows=error;
    } __finally {
        if(called&&!returned){outgoing=host_completion_errors();host_completion_gap(owners,restricted);}
        free(prepared);host_completion_use_end(use,called?owners:NULL,called&&!returned);
        native_owner_links_free(owners);host_completion_restore(outgoing);
    }
    return 1;
}
