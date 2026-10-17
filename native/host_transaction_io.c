/* Actual synchronous file operations on observed transacted files. Unknown
 * handles keep their original Win32 operation and never acquire a transaction
 * identity. Pending OVERLAPPED work has no fabricated completion receipt. */
typedef struct HostTransactionUse HostTransactionUse;
static int host_transaction_io_begin(HostImport*,void*,HANDLE,OwnerLink**,int*,HostTransactionUse**,DWORD*);
static HANDLE host_transaction_io_handle(HostTransactionUse*,HANDLE);
static void host_transaction_io_end(HostTransactionUse*,OwnerLink*,int);
typedef struct NativeHostCryptoSpan NativeHostCryptoSpan;
static NativeHostCryptoSpan *native_memory_crypto_span(uint64_t,uint64_t,OwnerLink*,int,OwnerLink**,int*,int*);
static void native_memory_crypto_span_end(NativeHostCryptoSpan*);
static void native_memory_crypto_span_pending(NativeHostCryptoSpan*);
static void native_memory_crypto_unobserved(OwnerLink*,int);

typedef struct HostTransactionIoErrors {DWORD windows;int crt;unsigned long dos;} HostTransactionIoErrors;
static HostTransactionIoErrors host_transaction_io_errors(void){
    HostTransactionIoErrors value;value.windows=GetLastError();value.crt=errno;value.dos=_doserrno;return value;
}
static void host_transaction_io_restore(HostTransactionIoErrors value){_doserrno=value.dos;errno=value.crt;SetLastError(value.windows);}
static int host_is_transaction_io(int kind){return kind>=HOST_TX_READ&&kind<=HOST_TX_FLUSH;}

/* Only unresolved operations enter this append-only directory. Each node
 * retains the exact handle use and allocation generations that the kernel may
 * still access. No reaper, guessed byte count, CancelIo or automatic close can
 * turn an unobserved completion into a completed operation. */
typedef struct HostTransactionPendingIo {
    HostTransactionUse *use;NativeHostCryptoSpan *spans[4];
    OwnerLink *contributors;int kind;struct HostTransactionPendingIo *next;
} HostTransactionPendingIo;
static HostTransactionPendingIo *volatile host_transaction_pending_io;
static void host_transaction_io_pending(HostTransactionPendingIo *pending){
    PVOID volatile *head=(PVOID volatile*)&host_transaction_pending_io;PVOID previous;
    do {previous=InterlockedCompareExchangePointer(head,NULL,NULL);pending->next=(HostTransactionPendingIo*)previous;}
    while(InterlockedCompareExchangePointer(head,pending,previous)!=previous);
}
typedef struct HostTransactionIoOutput {void *address;SIZE_T bytes;} HostTransactionIoOutput;
static DWORD host_transaction_io_outputs(HostTransactionIoOutput *outputs,unsigned count,int restricted,int locked){
    for(unsigned i=0;i<count;i++)if(outputs[i].address&&outputs[i].bytes){
        int hit=locked?host_memory_output_locked(outputs[i].address,outputs[i].bytes)
                :host_memory_range(outputs[i].address,outputs[i].bytes,0,restricted);
        if(hit)return hit>0?ERROR_ACCESS_DENIED:restricted?ERROR_BUSY:ERROR_INVALID_DATA;
    }
    return 0;
}
static BOOL host_transaction_io_original(HostImport *entry,HANDLE file,const uintptr_t *a){
    switch(entry->kind){
        case HOST_TX_READ:return ((BOOL (WINAPI*)(HANDLE,LPVOID,DWORD,LPDWORD,LPOVERLAPPED))entry->original)(
                file,(LPVOID)a[1],(DWORD)a[2],(LPDWORD)a[3],(LPOVERLAPPED)a[4]);
        case HOST_TX_WRITE:return ((BOOL (WINAPI*)(HANDLE,LPCVOID,DWORD,LPDWORD,LPOVERLAPPED))entry->original)(
                file,(LPCVOID)a[1],(DWORD)a[2],(LPDWORD)a[3],(LPOVERLAPPED)a[4]);
        case HOST_TX_SEEK:{LARGE_INTEGER distance;distance.QuadPart=(LONGLONG)a[1];
            return ((BOOL (WINAPI*)(HANDLE,LARGE_INTEGER,PLARGE_INTEGER,DWORD))entry->original)(
                    file,distance,(PLARGE_INTEGER)a[2],(DWORD)a[3]);}
        case HOST_TX_TRUNCATE:case HOST_TX_FLUSH:
            return ((BOOL (WINAPI*)(HANDLE))entry->original)(file);
        default:SetLastError(ERROR_INVALID_FUNCTION);return FALSE;
    }
}
static uint64_t host_native_transaction_io(HostImport *entry,void *caller,uintptr_t *a){
    HostTransactionIoErrors incoming=host_transaction_io_errors(),outgoing=incoming;
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    int restricted=host_loader_callout()||(state&&(state->heapCallback||state->toolingRestricted||state->vmDetaching||state->vmThreadEnding
            ||state->hostUnloadImage||state->hostRelease||state->hostLoad&&state->hostLoad->calling));
    if(!restricted)entry=host_consumed_entry(entry,state,caller);
    OwnerLink *owners=NULL,*observed=NULL;int unknown=0,dataUnknown=0,called=0,returned=0;
    HostTransactionUse *use=NULL;NativeHostCryptoSpan *spans[4]={0};HostTransactionPendingIo *pending=NULL;
    HostTransactionIoOutput outputs[3];unsigned outputCount=0;DWORD error=0;BOOL result=FALSE;
    int transfer=entry->kind==HOST_TX_READ||entry->kind==HOST_TX_WRITE;
    if(entry->kind==HOST_TX_READ)outputs[outputCount++]=(HostTransactionIoOutput){(void*)a[1],(DWORD)a[2]};
    if(transfer){
        outputs[outputCount++]=(HostTransactionIoOutput){(void*)a[3],sizeof(DWORD)};
        outputs[outputCount++]=(HostTransactionIoOutput){(void*)a[4],sizeof(OVERLAPPED)};
    }else if(entry->kind==HOST_TX_SEEK)outputs[outputCount++]=(HostTransactionIoOutput){(void*)a[2],sizeof(LARGE_INTEGER)};
    __try {
        if(!host_transaction_io_begin(entry,caller,(HANDLE)a[0],&owners,&unknown,&use,&error)){
            if(!error)error=ERROR_INVALID_DATA;
        }
        if(!error)error=host_transaction_io_outputs(outputs,outputCount,restricted,0);
        // Ordinary JDK handles already have their own IoBridge write lifetime.
        // A numeric buffer match cannot borrow that independent operation's
        // lease. Only this actual transaction use owns new business leases;
        // control-output admission above still applies to every handle.
        if(!error&&use&&transfer&&a[1]&&(DWORD)a[2]){
            int denied=0;spans[0]=native_memory_crypto_span(a[1],(DWORD)a[2],owners,
                    entry->kind==HOST_TX_READ?2:0,&observed,&dataUnknown,&denied);
            if(denied)error=ERROR_BUSY;
        }
        for(unsigned i=0;i<outputCount&&!error&&use;i++)if(outputs[i].address&&outputs[i].bytes){
            // The read data destination was already retained as spans[0].
            if(entry->kind==HOST_TX_READ&&i==0)continue;
            int denied=0;spans[i+1]=native_memory_crypto_span((uint64_t)(uintptr_t)outputs[i].address,
                    outputs[i].bytes,owners,2,&observed,&dataUnknown,&denied);
            if(denied)error=ERROR_BUSY;
        }
        if(!error&&native_owner_stopped(observed))error=ERROR_ACCESS_DENIED;
        if(!error&&(use||spans[0]||spans[1]||spans[2]||spans[3])){
            pending=(HostTransactionPendingIo*)calloc(1,sizeof(*pending));
            if(!pending)error=ERROR_NOT_ENOUGH_MEMORY;
        }
        HANDLE file=host_transaction_io_handle(use,(HANDLE)a[0]);
        if(!error&&use&&(!file||file==INVALID_HANDLE_VALUE))error=ERROR_INVALID_HANDLE;
        if(!error&&outputCount){
            // Validate against current control publication, then release the
            // short gate. No metadata/private-directory lock crosses file I/O.
            int locked=restricted?TryAcquireSRWLockExclusive(&native_private_gate)
                    :(AcquireSRWLockExclusive(&native_private_gate),1);
            if(!locked)error=ERROR_BUSY;
            else {error=host_transaction_io_outputs(outputs,outputCount,restricted,1);ReleaseSRWLockExclusive(&native_private_gate);}
        }
        if(!error&&(native_owner_stopped(owners)||native_owner_stopped(observed)))error=ERROR_ACCESS_DENIED;
        if(!error){
            host_transaction_io_restore(incoming);called=1;result=host_transaction_io_original(entry,file,a);
            outgoing=host_transaction_io_errors();returned=1;
        }else outgoing.windows=error;
    } __finally {
        if(called&&!returned)outgoing=host_transaction_io_errors();
        int known=use!=NULL;
        int unresolved=called&&(!returned||(transfer&&a[4]&&!result&&outgoing.windows==ERROR_IO_PENDING));
        if(unresolved){
            if(use)host_transaction_io_end(use,observed,1);
            if(pending){
                pending->use=use;pending->kind=entry->kind;pending->contributors=observed;observed=NULL;
                for(unsigned i=0;i<4;i++){pending->spans[i]=spans[i];native_memory_crypto_span_pending(spans[i]);spans[i]=NULL;}
                host_transaction_io_pending(pending);pending=NULL;
            }
            native_memory_crypto_unobserved(owners,restricted);
        }else {
            for(unsigned i=0;i<4;i++)native_memory_crypto_span_end(spans[i]);
            if(use)host_transaction_io_end(use,called?observed:NULL,0);
        }
        if(dataUnknown&&known)native_memory_crypto_unobserved(owners,restricted);
        free(pending);native_owner_links_free(observed);native_owner_links_free(owners);host_transaction_io_restore(outgoing);
    }
    return (uint64_t)result;
}
