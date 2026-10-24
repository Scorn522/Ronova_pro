/* Actual TxF I/O. Only the kernel-written private OVERLAPPED completes an
 * observed request. Caller buffers retain the original OS lifetime contract;
 * there is no allocator generation, SDK field lease or private byte image.
 * Public Internal, manual signals and posted pointers do not release a request.
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
    HostTransactionUse *use;
    OwnerLink *owners;void *fileTag,*portTag;
    uint64_t fileGeneration;
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
    int locked=native_private_read_lock(restricted);
    if(!locked)return ERROR_BUSY;
    error=host_transaction_io_outputs(outputs,count,restricted,1);ReleaseSRWLockShared(&native_private_gate);return error;
}
/* The original OVERLAPPED contract keeps caller storage valid until its real
 * operation completes. Copy only the actual kernel status/count, under the
 * control-range publication fence; no buffer bytes or old values are replayed. */
static int host_transaction_io_status(HostTransactionPendingIo *request,ULONG_PTR high,ULONG_PTR status){
    LPOVERLAPPED target=request?request->publicOverlapped:NULL;if(!target)return 0;
    if(host_memory_range(target,sizeof(*target),0,1))return 0;
    if(!native_private_read_lock(1))return 0;int ready=0;
    __try {
        if(!host_memory_output_locked(target,sizeof(*target))){
            __try {target->InternalHigh=high;MemoryBarrier();target->Internal=status;ready=1;}
            __except(EXCEPTION_EXECUTE_HANDLER){ready=0;}
        }
    } __finally {ReleaseSRWLockShared(&native_private_gate);}
    return ready;
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
        ready=host_transaction_io_status(request,high,status);
        if(ready)request->copied=1;
    }
    if(ready&&request->use){
        if(host_transaction_io_release(request->use,request->owners))request->use=NULL;
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
    OwnerLink *owners=NULL;int unknown=0,called=0,returned=0;
    HostTransactionUse *use=NULL;HostTransactionPendingIo *request=NULL;
    HostTransactionIoOutput outputs[3];unsigned outputCount=0;DWORD error=0;BOOL result=FALSE;
    int transfer=entry->kind==HOST_TX_READ||entry->kind==HOST_TX_WRITE;
    uintptr_t a[5]={0};unsigned count=transfer?5:entry->kind==HOST_TX_SEEK?4:1;memcpy(a,arguments,count*sizeof(*a));
    if(entry->kind==HOST_TX_READ)outputs[outputCount++]=(HostTransactionIoOutput){(void*)a[1],(DWORD)a[2]};
    if(transfer){outputs[outputCount++]=(HostTransactionIoOutput){(void*)a[3],sizeof(DWORD)};outputs[outputCount++]=(HostTransactionIoOutput){(void*)a[4],sizeof(OVERLAPPED)};}
    else if(entry->kind==HOST_TX_SEEK)outputs[outputCount++]=(HostTransactionIoOutput){(void*)a[2],sizeof(LARGE_INTEGER)};
    __try {
        if(!host_transaction_io_begin(entry,caller,(HANDLE)a[0],&owners,&unknown,&use,&error)&&!error)error=ERROR_INVALID_DATA;
        if(!error)error=host_transaction_io_outputs(outputs,outputCount,restricted,0);
        /* Read/write buffer arguments are ordinary OS spans. Check only the
         * actual control fence, never private allocation ownership. */
        if(!error&&transfer&&a[1]&&(DWORD)a[2]){
            int hit=host_memory_range((void*)a[1],(DWORD)a[2],0,restricted);
            if(hit)error=hit>0?ERROR_ACCESS_DENIED:restricted?ERROR_BUSY:ERROR_INVALID_DATA;
        }
        if(!error&&use&&transfer&&a[4]){
            request=(HostTransactionPendingIo*)calloc(1,sizeof(*request));
            if(!request)error=ERROR_NOT_ENOUGH_MEMORY;
            else {
                request->publicOverlapped=(LPOVERLAPPED)a[4];request->kind=entry->kind;
                request->fileTag=host_transaction_file_identity(use,&request->fileGeneration);
                request->portTag=host_completion_file_port(use,&request->key,&request->modes);
                if(!native_owner_merge(&request->owners,owners))error=ERROR_NOT_ENOUGH_MEMORY;
                if(!error){
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
        if(!error&&(native_owner_stopped(owners)))error=ERROR_ACCESS_DENIED;
        if(!error&&request){
            request->use=use;
            if(!host_transaction_io_publish(request,restricted))error=ERROR_BUSY;
            else {
                use=NULL;
                if(request->shadowed){
                    /* Publish pending before the actual I/O can start. The
                     * guest's old zero/previous result cannot advertise an
                     * unfinished new request as completed. */
                    if(!host_transaction_io_status(request,0,0x103u))error=ERROR_BUSY;
                    else a[4]=(uintptr_t)&request->shadow;
                }
            }
        }
        if(!error){host_transaction_io_restore(incoming);called=1;result=host_transaction_io_original(entry,file,a);outgoing=host_transaction_io_errors();returned=1;}
        else {outgoing=incoming;outgoing.windows=error;}
    } __finally {
        if(called&&!returned)outgoing=host_transaction_io_errors();
        if(request&&request->published){
            if(called&&!returned){
                InterlockedExchange(&request->phase,HOST_IO_UNCERTAIN);host_transaction_io_end(request->use,request->owners,1);
                host_source_gap(request->owners,restricted);host_transaction_io_timer_close(request);
            }else if(called&&!request->shadowed&&!result&&outgoing.windows==ERROR_IO_PENDING){
                /* An unshadowed request has no internal kernel completion
                 * witness. Keep its actual handle use pending. */
                InterlockedExchange(&request->phase,HOST_IO_UNCOVERED);
                host_source_gap(request->owners,restricted);
            }else {
                request->discard=!called||!request->shadowed||(!result&&outgoing.windows!=ERROR_IO_PENDING
                        &&__atomic_load_n(&request->shadow.Internal,__ATOMIC_ACQUIRE)==0x103u);
                /* Arm under SUBMITTING: even an early callback/packet cannot
                 * retire resources before the initiating API has returned. */
                if(request->timer){LARGE_INTEGER due;due.QuadPart=-10000;FILETIME when={(DWORD)due.LowPart,(DWORD)due.HighPart};SetThreadpoolTimer(request->timer,&when,1,0);}
                InterlockedExchange(&request->phase,HOST_IO_ACTIVE);
                if(!host_transaction_io_finish(request,0,restricted)&&!request->timer)host_source_gap(request->owners,restricted);
            }
            request=NULL;
        }else if(called&&!returned){
            if(use)host_transaction_io_end(use,owners,1);
            host_source_gap(owners,restricted);use=NULL;
        }
        if(use)host_transaction_io_end(use,called?owners:NULL,0);
        if(request){host_transaction_io_timer_close(request);native_owner_links_free(request->owners);free(request);}
        native_owner_links_free(owners);host_transaction_io_restore(outgoing);
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
    OwnerLink *owners=NULL;int unknown=0,called=0,returned=0;
    HostTransactionUse *use=NULL;HostTransactionPendingIo *request=NULL;
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
            if(request&&request->shadowed)op=&request->shadow;
        }
        if(!error)error=host_transaction_io_output_gate(&output,cancel?0:1,restricted);
        if(!error&&(native_owner_stopped(owners)))error=ERROR_ACCESS_DENIED;
        if(!error){
            HANDLE file=host_transaction_io_handle(use,(HANDLE)a[0]);
            host_transaction_io_restore(incoming);called=1;result=host_transaction_completion_original(entry,file,op,a);
            outgoing=host_transaction_io_errors();returned=1;
        }else {outgoing=incoming;outgoing.windows=error;}
    } __finally {
        if(called&&!returned)outgoing=host_transaction_io_errors();
        int terminalQuery=called&&returned&&!cancel&&(result||(outgoing.windows!=ERROR_IO_INCOMPLETE
                &&outgoing.windows!=WAIT_TIMEOUT&&outgoing.windows!=WAIT_IO_COMPLETION));
        if(request){
            /* This call's output write has finished. Its one owned reference
             * permits terminal cleanup before returning a real query result. */
            if(terminalQuery)host_transaction_io_finish(request,1,restricted);
            InterlockedDecrement(&request->references);
        }
        if(use)host_transaction_io_end(use,called?owners:NULL,called&&!returned);
        native_owner_links_free(owners);host_transaction_io_restore(outgoing);
    }
    return (uint64_t)result;
}
