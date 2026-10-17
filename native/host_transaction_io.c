/* Actual TxF I/O. Only the kernel-written private OVERLAPPED is completion
 * evidence for an observed public OVERLAPPED. Public Internal, manual signals
 * and posted public pointers never release a request. Ordinary unobserved
 * handles/OVERLAPPED storage retain their original uncovered operation.
 * https://learn.microsoft.com/en-us/windows/win32/api/ioapiset/nf-ioapiset-getoverlappedresult
 * https://learn.microsoft.com/en-us/windows/win32/api/ioapiset/nf-ioapiset-getoverlappedresultex
 * https://learn.microsoft.com/en-us/windows/win32/api/ioapiset/nf-ioapiset-cancelioex
 */
typedef struct HostTransactionUse HostTransactionUse;
static int host_transaction_io_begin(HostImport*,void*,HANDLE,OwnerLink**,int*,HostTransactionUse**,DWORD*);
static HANDLE host_transaction_io_handle(HostTransactionUse*,HANDLE);
static void host_transaction_io_end(HostTransactionUse*,OwnerLink*,int);
static int host_transaction_io_release(HostTransactionUse*,OwnerLink*);
static void *host_transaction_file_identity(HostTransactionUse*,uint64_t*);
static void *host_completion_file_port(HostTransactionUse*,ULONG_PTR*,UCHAR*);
typedef struct NativeHostCryptoSpan NativeHostCryptoSpan;
static NativeHostCryptoSpan *native_memory_crypto_span(uint64_t,uint64_t,OwnerLink*,int,OwnerLink**,int*,int*);
static void native_memory_crypto_span_end(NativeHostCryptoSpan*);
static int native_memory_crypto_span_complete(NativeHostCryptoSpan*,int);
static int native_memory_crypto_span_covers(NativeHostCryptoSpan*,uint64_t,uint64_t);
static void *native_memory_crypto_span_identity(NativeHostCryptoSpan*,uint64_t*);
static int native_memory_crypto_span_current(void*,uint64_t,uint64_t,uint64_t,int);
static int native_memory_crypto_span_overlapped(NativeHostCryptoSpan*,LPOVERLAPPED,ULONG_PTR,ULONG_PTR);
static void native_memory_crypto_span_pending(NativeHostCryptoSpan*);
static void native_memory_crypto_unobserved(OwnerLink*,int);

typedef struct HostTransactionIoErrors {DWORD windows;int crt;unsigned long dos;} HostTransactionIoErrors;
static HostTransactionIoErrors host_transaction_io_errors(void){
    HostTransactionIoErrors value;value.windows=GetLastError();value.crt=errno;value.dos=_doserrno;return value;
}
static void host_transaction_io_restore(HostTransactionIoErrors value){_doserrno=value.dos;errno=value.crt;SetLastError(value.windows);}
static int host_is_transaction_io(int kind){return kind>=HOST_TX_READ&&kind<=HOST_TX_FLUSH;}
static int host_is_transaction_completion(int kind){return kind>=HOST_TX_OVERLAPPED&&kind<=HOST_TX_CANCEL_IO;}

enum {HOST_IO_SUBMITTING,HOST_IO_ACTIVE,HOST_IO_FINISHING,HOST_IO_DONE,HOST_IO_UNCOVERED,HOST_IO_UNCERTAIN};
typedef struct HostTransactionPendingIo {
    /* This TU's calloc is native_private_calloc. The entire permanent node,
     * including shadow, is protected by the existing NativePrivateRange. */
    OVERLAPPED shadow;LPOVERLAPPED publicOverlapped;
    HostTransactionUse *use;NativeHostCryptoSpan *spans[4];
    OwnerLink *owners,*contributors;void *fileTag,*portTag,*publicRow;
    uint64_t fileGeneration,publicGeneration;
    ULONG_PTR key;UCHAR modes;int shadowed,discard,copied,published,kind;
    volatile LONG phase,references;PTP_TIMER timer;
    struct HostTransactionPendingIo *next;
} HostTransactionPendingIo;
static SRWLOCK host_transaction_io_records=SRWLOCK_INIT;
static HostTransactionPendingIo *host_transaction_pending_io;
static int host_transaction_io_lock(int restricted){
    return restricted?TryAcquireSRWLockExclusive(&host_transaction_io_records):(AcquireSRWLockExclusive(&host_transaction_io_records),1);
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
static DWORD host_transaction_io_output_gate(HostTransactionIoOutput *outputs,unsigned count,int restricted){
    DWORD error=host_transaction_io_outputs(outputs,count,restricted,0);if(error||!count)return error;
    int locked=restricted?TryAcquireSRWLockExclusive(&native_private_gate):(AcquireSRWLockExclusive(&native_private_gate),1);
    if(!locked)return ERROR_BUSY;
    error=host_transaction_io_outputs(outputs,count,restricted,1);ReleaseSRWLockExclusive(&native_private_gate);return error;
}
static void host_transaction_io_timer_close(HostTransactionPendingIo *request){
    PTP_TIMER timer=__atomic_exchange_n(&request->timer,NULL,__ATOMIC_ACQ_REL);
    if(timer){SetThreadpoolTimer(timer,NULL,0,0);CloseThreadpoolTimer(timer);}
    /* Already queued callbacks may run. Contexts remain permanent tombstones;
     * DONE prevents another write/release. No wait crosses a ledger lock. */
}
static int host_transaction_io_finish(HostTransactionPendingIo *request,int ownedReferences,int restricted){
    if(!request)return 0;
    if(!host_transaction_io_lock(restricted))return 0;
    LONG phase=InterlockedCompareExchange(&request->phase,0,0);
    if(phase==HOST_IO_DONE){ReleaseSRWLockExclusive(&host_transaction_io_records);return 1;}
    ULONG_PTR status=__atomic_load_n(&request->shadow.Internal,__ATOMIC_ACQUIRE);
    if(phase!=HOST_IO_ACTIVE||InterlockedCompareExchange(&request->references,0,0)>ownedReferences
            ||(!request->discard&&(!request->shadowed||status==0x103u))){
        ReleaseSRWLockExclusive(&host_transaction_io_records);return 0;
    }
    int claimed=InterlockedCompareExchange(&request->phase,HOST_IO_FINISHING,HOST_IO_ACTIVE)==HOST_IO_ACTIVE;
    ReleaseSRWLockExclusive(&host_transaction_io_records);if(!claimed)return 0;
    int ready=1;
    if(!request->discard&&!request->copied){
        ULONG_PTR high=__atomic_load_n(&request->shadow.InternalHigh,__ATOMIC_ACQUIRE);
        ready=native_memory_crypto_span_overlapped(request->spans[2],request->publicOverlapped,high,status);
        if(ready)request->copied=1;
    }
    if(ready)for(unsigned i=0;i<4;i++)if(request->spans[i]){
        if(native_memory_crypto_span_complete(request->spans[i],1))request->spans[i]=NULL;
        else ready=0;
    }
    if(ready&&request->use){
        if(host_transaction_io_release(request->use,request->contributors))request->use=NULL;
        else ready=0;
    }
    if(ready){InterlockedExchange(&request->phase,HOST_IO_DONE);host_transaction_io_timer_close(request);}
    else InterlockedExchange(&request->phase,HOST_IO_ACTIVE);
    return ready;
}
static VOID CALLBACK host_transaction_io_worker(PTP_CALLBACK_INSTANCE instance,PVOID context,PTP_TIMER timer){
    (void)instance;(void)timer;HostTransactionIoErrors saved=host_transaction_io_errors();
    /* One already submitted private status only. No Java, graph scanning,
     * public status/signal inference, cancellation or completion dequeue. */
    host_transaction_io_finish((HostTransactionPendingIo*)context,0,1);host_transaction_io_restore(saved);
}
static int host_transaction_io_publish(HostTransactionPendingIo *request,int restricted){
    if(!host_transaction_io_lock(restricted))return 0;
    int ready=1;
    for(HostTransactionPendingIo *old=host_transaction_pending_io;old;old=old->next)
        if(old->publicOverlapped==request->publicOverlapped&&old->phase!=HOST_IO_DONE){ready=0;break;}
    if(ready){
        request->published=1;request->next=__atomic_load_n(&host_transaction_pending_io,__ATOMIC_ACQUIRE);
        __atomic_store_n(&host_transaction_pending_io,request,__ATOMIC_RELEASE);
    }
    ReleaseSRWLockExclusive(&host_transaction_io_records);return ready;
}
/* Called after real dequeue, outside the port ledger. A public posted pointer
 * is not a shadow. Wrong port/key hides the private address; a late packet
 * maps only its own immutable old tombstone, never the new request at that
 * public numeric address. No queue draining is used to update the status. */
static int host_transaction_io_packet(void *portTag,ULONG_PTR key,LPOVERLAPPED *op){
    if(!op||!*op)return 0;HostTransactionIoErrors saved=host_transaction_io_errors();
    HostTransactionPendingIo *found=NULL;
    for(HostTransactionPendingIo *request=__atomic_load_n(&host_transaction_pending_io,__ATOMIC_ACQUIRE);request;request=request->next)
        if(*op==&request->shadow){found=request;break;}
    int answer=0;
    if(found){
        *op=NULL;answer=-1;
        ULONG_PTR status=__atomic_load_n(&found->shadow.Internal,__ATOMIC_ACQUIRE);
        if(found->shadowed&&found->portTag==portTag&&portTag&&found->key==key&&status!=0x103u){
            *op=found->publicOverlapped;answer=1;host_transaction_io_finish(found,0,1);
        }
    }
    host_transaction_io_restore(saved);return answer;
}
static BOOL host_transaction_io_original(HostImport *entry,HANDLE file,const uintptr_t *a){
    switch(entry->kind){
        case HOST_TX_READ:return ((BOOL (WINAPI*)(HANDLE,LPVOID,DWORD,LPDWORD,LPOVERLAPPED))entry->original)(
                file,(LPVOID)a[1],(DWORD)a[2],(LPDWORD)a[3],(LPOVERLAPPED)a[4]);
        case HOST_TX_WRITE:return ((BOOL (WINAPI*)(HANDLE,LPCVOID,DWORD,LPDWORD,LPOVERLAPPED))entry->original)(
                file,(LPCVOID)a[1],(DWORD)a[2],(LPDWORD)a[3],(LPOVERLAPPED)a[4]);
        case HOST_TX_SEEK:{LARGE_INTEGER distance;distance.QuadPart=(LONGLONG)a[1];
            return ((BOOL (WINAPI*)(HANDLE,LARGE_INTEGER,PLARGE_INTEGER,DWORD))entry->original)(file,distance,(PLARGE_INTEGER)a[2],(DWORD)a[3]);}
        case HOST_TX_TRUNCATE:case HOST_TX_FLUSH:return ((BOOL (WINAPI*)(HANDLE))entry->original)(file);
        default:SetLastError(ERROR_INVALID_FUNCTION);return FALSE;
    }
}
static uint64_t host_native_transaction_io(HostImport *entry,void *caller,uintptr_t *arguments){
    HostTransactionIoErrors incoming=host_transaction_io_errors(),outgoing=incoming;
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    int restricted=host_transaction_restricted();if(!restricted)entry=host_consumed_entry(entry,state,caller);
    OwnerLink *owners=NULL,*observed=NULL;int unknown=0,dataUnknown=0,called=0,returned=0;
    HostTransactionUse *use=NULL;NativeHostCryptoSpan *spans[4]={0};HostTransactionPendingIo *request=NULL;
    HostTransactionIoOutput outputs[3];unsigned outputCount=0;DWORD error=0;BOOL result=FALSE;
    int transfer=entry->kind==HOST_TX_READ||entry->kind==HOST_TX_WRITE;
    uintptr_t a[5]={0};unsigned count=transfer?5:entry->kind==HOST_TX_SEEK?4:1;memcpy(a,arguments,count*sizeof(*a));
    if(entry->kind==HOST_TX_READ)outputs[outputCount++]=(HostTransactionIoOutput){(void*)a[1],(DWORD)a[2]};
    if(transfer){outputs[outputCount++]=(HostTransactionIoOutput){(void*)a[3],sizeof(DWORD)};outputs[outputCount++]=(HostTransactionIoOutput){(void*)a[4],sizeof(OVERLAPPED)};}
    else if(entry->kind==HOST_TX_SEEK)outputs[outputCount++]=(HostTransactionIoOutput){(void*)a[2],sizeof(LARGE_INTEGER)};
    __try {
        if(!host_transaction_io_begin(entry,caller,(HANDLE)a[0],&owners,&unknown,&use,&error)&&!error)error=ERROR_INVALID_DATA;
        if(!error)error=host_transaction_io_outputs(outputs,outputCount,restricted,0);
        /* Unknown ordinary handles retain IoBridge and never capture Java
         * sources or acquire business memory leases through this directory. */
        if(!error&&use&&transfer&&a[1]&&(DWORD)a[2]){
            int denied=0;spans[0]=native_memory_crypto_span(a[1],(DWORD)a[2],owners,entry->kind==HOST_TX_READ?2:0,&observed,&dataUnknown,&denied);
            if(denied)error=ERROR_BUSY;
        }
        if(!error&&use&&transfer&&a[3]){
            int denied=0;spans[1]=native_memory_crypto_span(a[3],sizeof(DWORD),owners,2,&observed,&dataUnknown,&denied);if(denied)error=ERROR_BUSY;
        }
        if(!error&&use&&transfer&&a[4]){
            int denied=0;spans[2]=native_memory_crypto_span(a[4],sizeof(OVERLAPPED),owners,2,&observed,&dataUnknown,&denied);if(denied)error=ERROR_BUSY;
        }
        if(!error&&use&&entry->kind==HOST_TX_SEEK&&a[2]){
            int denied=0;spans[3]=native_memory_crypto_span(a[2],sizeof(LARGE_INTEGER),owners,2,&observed,&dataUnknown,&denied);if(denied)error=ERROR_BUSY;
        }
        if(!error&&use&&transfer&&a[4]){
            request=(HostTransactionPendingIo*)calloc(1,sizeof(*request));
            if(!request)error=ERROR_NOT_ENOUGH_MEMORY;
            else {
                request->publicOverlapped=(LPOVERLAPPED)a[4];request->kind=entry->kind;
                request->fileTag=host_transaction_file_identity(use,&request->fileGeneration);
                request->portTag=host_completion_file_port(use,&request->key,&request->modes);
                request->publicRow=native_memory_crypto_span_identity(spans[2],&request->publicGeneration);
                if(!native_owner_merge(&request->owners,owners)||!native_owner_merge(&request->contributors,observed))error=ERROR_NOT_ENOUGH_MEMORY;
                if(!error&&spans[2]){
                    OVERLAPPED initial={0};
                    if(!host_transaction_read((const void*)a[4],&initial,sizeof(initial)))error=ERROR_NOACCESS;
                    else {
                        request->shadow.Offset=initial.Offset;request->shadow.OffsetHigh=initial.OffsetHigh;request->shadow.hEvent=initial.hEvent;
                        request->shadow.Internal=0x103u;request->shadowed=1;
                        request->timer=CreateThreadpoolTimer(host_transaction_io_worker,request,NULL);if(!request->timer)error=ERROR_NOT_ENOUGH_MEMORY;
                    }
                }
            }
        }
        HANDLE file=host_transaction_io_handle(use,(HANDLE)a[0]);
        if(!error&&use&&(!file||file==INVALID_HANDLE_VALUE))error=ERROR_INVALID_HANDLE;
        if(!error)error=host_transaction_io_output_gate(outputs,outputCount,restricted);
        if(!error&&(native_owner_stopped(owners)||native_owner_stopped(observed)))error=ERROR_ACCESS_DENIED;
        if(!error&&request){
            request->use=use;for(unsigned i=0;i<4;i++)request->spans[i]=spans[i];
            if(!host_transaction_io_publish(request,restricted))error=ERROR_BUSY;
            else {
                use=NULL;for(unsigned i=0;i<4;i++)spans[i]=NULL;
                if(request->shadowed){
                    /* Publish pending before the actual I/O can start. The
                     * guest's old zero/previous result cannot advertise an
                     * unfinished new request as completed. */
                    if(!native_memory_crypto_span_overlapped(request->spans[2],request->publicOverlapped,0,0x103u))error=ERROR_BUSY;
                    else a[4]=(uintptr_t)&request->shadow;
                }
            }
        }
        if(!error){host_transaction_io_restore(incoming);called=1;result=host_transaction_io_original(entry,file,a);outgoing=host_transaction_io_errors();returned=1;}
        else {outgoing=incoming;outgoing.windows=error;}
    } __finally {
        if(called&&!returned)outgoing=host_transaction_io_errors();
        int known=use!=NULL||request&&request->published;
        if(request&&request->published){
            if(called&&!returned){
                InterlockedExchange(&request->phase,HOST_IO_UNCERTAIN);host_transaction_io_end(request->use,request->contributors,1);
                for(unsigned i=0;i<4;i++)native_memory_crypto_span_pending(request->spans[i]);
                native_memory_crypto_unobserved(request->owners,restricted);host_transaction_io_timer_close(request);
            }else if(called&&!request->shadowed&&!result&&outgoing.windows==ERROR_IO_PENDING){
                /* No fixed generation for stack/Java OVERLAPPED. Its original
                 * pending operation remains uncovered; public bits or packets
                 * cannot release this old use or erase its existing gap. */
                InterlockedExchange(&request->phase,HOST_IO_UNCOVERED);
                for(unsigned i=0;i<4;i++)native_memory_crypto_span_pending(request->spans[i]);
                native_memory_crypto_unobserved(request->owners,restricted);
            }else {
                request->discard=!called||!request->shadowed||(!result&&outgoing.windows!=ERROR_IO_PENDING
                        &&__atomic_load_n(&request->shadow.Internal,__ATOMIC_ACQUIRE)==0x103u);
                /* Arm under SUBMITTING: even an early callback/packet cannot
                 * retire resources before the initiating API has returned. */
                if(request->timer){LARGE_INTEGER due;due.QuadPart=-10000;FILETIME when={(DWORD)due.LowPart,(DWORD)due.HighPart};SetThreadpoolTimer(request->timer,&when,1,0);}
                InterlockedExchange(&request->phase,HOST_IO_ACTIVE);
                if(!host_transaction_io_finish(request,0,restricted)&&!request->timer)native_memory_crypto_unobserved(request->owners,restricted);
            }
            request=NULL;
        }else if(called&&!returned){
            if(use)host_transaction_io_end(use,observed,1);
            for(unsigned i=0;i<4;i++)native_memory_crypto_span_pending(spans[i]);
            native_memory_crypto_unobserved(owners,restricted);use=NULL;for(unsigned i=0;i<4;i++)spans[i]=NULL;
        }
        for(unsigned i=0;i<4;i++)native_memory_crypto_span_end(spans[i]);
        if(use)host_transaction_io_end(use,called?observed:NULL,0);
        if(dataUnknown&&known)native_memory_crypto_unobserved(owners,restricted);
        if(request){host_transaction_io_timer_close(request);native_owner_links_free(request->owners);native_owner_links_free(request->contributors);free(request);}
        native_owner_links_free(observed);native_owner_links_free(owners);host_transaction_io_restore(outgoing);
    }
    return (uint64_t)result;
}
static BOOL host_transaction_completion_original(HostImport *entry,HANDLE file,LPOVERLAPPED op,const uintptr_t *a){
    switch(entry->kind){
        case HOST_TX_OVERLAPPED:return ((BOOL (WINAPI*)(HANDLE,LPOVERLAPPED,LPDWORD,BOOL))entry->original)(file,op,(LPDWORD)a[2],(BOOL)a[3]);
        case HOST_TX_OVERLAPPED_EX:return ((BOOL (WINAPI*)(HANDLE,LPOVERLAPPED,LPDWORD,DWORD,BOOL))entry->original)(file,op,(LPDWORD)a[2],(DWORD)a[3],(BOOL)a[4]);
        case HOST_TX_CANCEL_IO:return ((BOOL (WINAPI*)(HANDLE,LPOVERLAPPED))entry->original)(file,op);
        default:SetLastError(ERROR_INVALID_FUNCTION);return FALSE;
    }
}
static uint64_t host_native_transaction_completion(HostImport *entry,void *caller,uintptr_t *a){
    HostTransactionIoErrors incoming=host_transaction_io_errors(),outgoing=incoming;
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    int restricted=host_transaction_restricted();if(!restricted)entry=host_consumed_entry(entry,state,caller);
    OwnerLink *owners=NULL,*observed=NULL;int unknown=0,dataUnknown=0,called=0,returned=0;
    HostTransactionUse *use=NULL;HostTransactionPendingIo *request=NULL;NativeHostCryptoSpan *countSpan=NULL;
    DWORD error=0;BOOL result=FALSE;LPOVERLAPPED op=(LPOVERLAPPED)a[1];int cancel=entry->kind==HOST_TX_CANCEL_IO;
    HostTransactionIoOutput output={(void*)(cancel?0:a[2]),sizeof(DWORD)};
    __try {
        if(!host_transaction_io_begin(entry,caller,(HANDLE)a[0],&owners,&unknown,&use,&error)&&!error)error=ERROR_INVALID_DATA;
        if(!error&&op){
            /* Check the actual user input before translating it. Neither an
             * exact private shadow nor an interior control address is a
             * public OVERLAPPED from which status/event bytes may be read. */
            int hit=host_memory_range(op,sizeof(*op),0,restricted);
            if(hit)error=hit>0?ERROR_ACCESS_DENIED:restricted?ERROR_BUSY:ERROR_INVALID_DATA;
        }
        if(!error)error=host_transaction_io_outputs(&output,cancel?0:1,restricted,0);
        if(!error&&use&&op){
            uint64_t generation=0;void *tag=host_transaction_file_identity(use,&generation);
            if(!host_transaction_io_lock(restricted))error=ERROR_BUSY;
            else {
                for(HostTransactionPendingIo *node=host_transaction_pending_io;node;node=node->next)
                    if(node->publicOverlapped==op&&node->fileTag==tag&&node->fileGeneration==generation){request=node;break;}
                if(request){
                    LONG phase=InterlockedCompareExchange(&request->phase,0,0);
                    if(phase==HOST_IO_FINISHING||phase==HOST_IO_UNCERTAIN){request=NULL;error=ERROR_BUSY;}
                    else InterlockedIncrement(&request->references);
                }
                ReleaseSRWLockExclusive(&host_transaction_io_records);
            }
            if(request&&request->shadowed){
                int current=native_memory_crypto_span_current(request->publicRow,request->publicGeneration,(uint64_t)(uintptr_t)op,sizeof(*op),restricted);
                if(current!=1){InterlockedDecrement(&request->references);request=NULL;if(current<0)error=ERROR_BUSY;}
                else op=&request->shadow;
            }
        }
        if(!error&&use&&!cancel&&a[2]){
            int retained=0;
            if(request)for(unsigned i=0;i<4&&!retained;i++)retained=native_memory_crypto_span_covers(request->spans[i],a[2],sizeof(DWORD));
            if(!retained){int denied=0;countSpan=native_memory_crypto_span(a[2],sizeof(DWORD),owners,2,&observed,&dataUnknown,&denied);if(denied)error=ERROR_BUSY;}
        }
        if(!error)error=host_transaction_io_output_gate(&output,cancel?0:1,restricted);
        if(!error&&(native_owner_stopped(owners)||native_owner_stopped(observed)))error=ERROR_ACCESS_DENIED;
        if(!error){
            HANDLE file=host_transaction_io_handle(use,(HANDLE)a[0]);
            host_transaction_io_restore(incoming);called=1;result=host_transaction_completion_original(entry,file,op,a);
            outgoing=host_transaction_io_errors();returned=1;
        }else {outgoing=incoming;outgoing.windows=error;}
    } __finally {
        if(called&&!returned)outgoing=host_transaction_io_errors();
        native_memory_crypto_span_end(countSpan);
        int terminalQuery=called&&returned&&!cancel&&(result||(outgoing.windows!=ERROR_IO_INCOMPLETE
                &&outgoing.windows!=WAIT_TIMEOUT&&outgoing.windows!=WAIT_IO_COMPLETION));
        if(request){
            /* This call's output write has finished. Its one owned reference
             * permits terminal cleanup before returning a real query result. */
            if(terminalQuery)host_transaction_io_finish(request,1,restricted);
            InterlockedDecrement(&request->references);
        }
        if(use)host_transaction_io_end(use,called?observed:NULL,called&&!returned);
        if(dataUnknown&&use)native_memory_crypto_unobserved(owners,restricted);
        native_owner_links_free(observed);native_owner_links_free(owners);host_transaction_io_restore(outgoing);
    }
    return (uint64_t)result;
}
