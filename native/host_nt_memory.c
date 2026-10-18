/* Actual ntdll Nt/Zw memory transfers, plus the already observed Win32 read/
 * write entries. These are source/control-range fences, without private
 * allocation observation. Only a SAME_ACCESS alias of
 * the supplied process object decides which endpoint belongs to this JVM.
 * https://learn.microsoft.com/en-us/windows/win32/api/handleapi/nf-handleapi-compareobjecthandles
 * https://learn.microsoft.com/en-us/windows/win32/api/memoryapi/nf-memoryapi-readprocessmemory
 * https://learn.microsoft.com/en-us/windows/win32/api/memoryapi/nf-memoryapi-writeprocessmemory
 * https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-erref/87fba13e-bf06-450e-83b1-9241dc81e781
 */
typedef struct HostNtCrtErrors {DWORD windows;int crt;unsigned long dos;} HostNtCrtErrors;
static HostNtCrtErrors host_nt_crt_errors(void){HostNtCrtErrors value={GetLastError(),errno,_doserrno};return value;}
static void host_nt_crt_restore(HostNtCrtErrors value){_doserrno=value.dos;errno=value.crt;SetLastError(value.windows);}
typedef LONG (NTAPI *HostNtMemoryTransfer)(HANDLE,PVOID,PVOID,SIZE_T,PSIZE_T);
typedef LONG (NTAPI *HostNtGetLastStatus)(void);
typedef void (NTAPI *HostNtSetLastStatus)(LONG);
typedef struct HostNtMemoryProvider {
    HMODULE image,pin;void *exports[4];
    HostNtGetLastStatus getStatus;HostNtSetLastStatus setStatus;
} HostNtMemoryProvider;
static HostNtMemoryProvider *volatile host_nt_memory_provider;

typedef struct HostNtThreadErrors {
    HostNtCrtErrors crt;LONG status;
    HostNtGetLastStatus getStatus;HostNtSetLastStatus setStatus;int ready;
} HostNtThreadErrors;
static int host_is_nt_memory(int kind){return kind>=HOST_NT_READ&&kind<=HOST_ZW_WRITE;}
static int host_is_win32_memory_transfer(int kind){return kind==HOST_READ_PROCESS_MEMORY||kind==HOST_WRITE_PROCESS_MEMORY;}
static int host_nt_memory_reading(int kind){return kind==HOST_NT_READ||kind==HOST_ZW_READ||kind==HOST_READ_PROCESS_MEMORY;}
static const char *host_nt_memory_name(int kind){
    static const char *names[]={"NtReadVirtualMemory","NtWriteVirtualMemory","ZwReadVirtualMemory","ZwWriteVirtualMemory"};
    return host_is_nt_memory(kind)?names[kind-HOST_NT_READ]:NULL;
}
static HostNtMemoryProvider *host_nt_memory_current(void){
    return (HostNtMemoryProvider*)InterlockedCompareExchangePointer((void *volatile*)&host_nt_memory_provider,NULL,NULL);
}
static int host_nt_errors_ready(void){
    HostNtMemoryProvider *provider=host_nt_memory_current();return provider&&provider->getStatus&&provider->setStatus;
}
static HostNtThreadErrors host_nt_errors(void){
    HostNtThreadErrors saved={0};saved.crt=host_nt_crt_errors();
    HostNtMemoryProvider *provider=host_nt_memory_current();
    if(provider){saved.getStatus=provider->getStatus;saved.setStatus=provider->setStatus;
        saved.ready=saved.getStatus&&saved.setStatus;if(saved.ready)saved.status=saved.getStatus();}
    SetLastError(saved.crt.windows);return saved;
}
static HostNtThreadErrors host_nt_result(const HostNtThreadErrors *incoming){
    /* Use the same retained actual ntdll helpers. Return NTSTATUS and the
     * thread's LastNtStatus are independent values, including on refusal. */
    HostNtThreadErrors saved=*incoming;saved.crt=host_nt_crt_errors();
    if(saved.ready)saved.status=saved.getStatus();SetLastError(saved.crt.windows);return saved;
}
static void host_nt_restore(const HostNtThreadErrors *saved){
    /* The real ntdll setter restores LastNtStatus and its associated Win32
     * mapping; restore the separately captured Win32 value after it. No TEB
     * offset, guessed TLS layout or return-status substitution is used. */
    if(saved->ready)saved->setStatus(saved->status);host_nt_crt_restore(saved->crt);
}
static LONG host_nt_refusal_status(DWORD error){
    /* STATUS_DEVICE_BUSY is 0x80000011, a warning with NT_SUCCESS == false.
     * 0xc000009e is STATUS_DEVICE_POWER_FAILURE and is not a busy result. */
    return (LONG)(error==ERROR_ACCESS_DENIED?0xc0000022u:error==ERROR_BUSY?0x80000011u:
            error==ERROR_NOT_ENOUGH_MEMORY||error==ERROR_OUTOFMEMORY?0xc0000017u:
            error==ERROR_INVALID_HANDLE?0xc0000008u:0xc000000du);
}
static uint64_t host_nt_refuse(const HostNtThreadErrors *incoming,DWORD error){
    host_nt_restore(incoming);return (uint64_t)(uint32_t)host_nt_refusal_status(error);
}
static uint64_t host_nt_transfer_refuse(int native,const HostNtThreadErrors *incoming,DWORD error){
    if(native)return host_nt_refuse(incoming,error);
    HostNtThreadErrors refused=*incoming;refused.crt.windows=error;host_nt_restore(&refused);return FALSE;
}

static HostNtMemoryProvider *host_nt_memory_publish(HMODULE provider,HostGetProcAddress resolve){
    HostNtMemoryProvider *known=host_nt_memory_current();
    if(known)return known->image==provider?known:NULL;
    if(!provider||!resolve)return NULL;
    HostNtCrtErrors incoming=host_nt_crt_errors();HMODULE image=GetModuleHandleW(L"ntdll.dll");
    if(provider!=image){host_nt_crt_restore(incoming);return NULL;}
    HostNtGetLastStatus getStatus=(HostNtGetLastStatus)resolve(image,"RtlGetLastNtStatus");
    HostNtSetLastStatus setStatus=(HostNtSetLastStatus)resolve(image,"RtlSetLastWin32ErrorAndNtStatusFromNtStatus");
    if(!getStatus||!setStatus||native_image((void*)getStatus)!=image||native_image((void*)setStatus)!=image){
        host_nt_crt_restore(incoming);return NULL;
    }
    /* Initial target discovery runs before these entries are published. Once
     * the actual helpers are available, retain their state across pinning and
     * metadata allocation too; later refreshes use the immutable record. */
    HostNtThreadErrors saved={0};saved.crt=incoming;saved.getStatus=getStatus;saved.setStatus=setStatus;
    saved.ready=1;saved.status=getStatus();HMODULE pin=NULL;
    if(!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,(LPCSTR)getStatus,&pin)||pin!=image){
        if(pin)host_real_free_library(pin);host_nt_restore(&saved);return NULL;
    }
    HostNtMemoryProvider *created=(HostNtMemoryProvider*)calloc(1,sizeof(*created));
    if(!created){host_real_free_library(pin);host_nt_restore(&saved);return NULL;}
    created->image=image;created->pin=pin;created->getStatus=getStatus;created->setStatus=setStatus;
    for(int kind=HOST_NT_READ;kind<=HOST_ZW_WRITE;kind++){
        void *actual=(void*)resolve(image,host_nt_memory_name(kind));
        if(actual&&native_image(actual)==image)created->exports[kind-HOST_NT_READ]=actual;
    }
    /* Nt/Zw aliases keep their exact names. An unexpected read/write alias
     * cannot cause an address-only classification to select either effect. */
    for(unsigned read=0;read<4;read+=2)for(unsigned write=1;write<4;write+=2)
        if(created->exports[read]&&created->exports[read]==created->exports[write]){
            free(created);host_real_free_library(pin);host_nt_restore(&saved);return NULL;
        }
    known=(HostNtMemoryProvider*)InterlockedCompareExchangePointer((void *volatile*)&host_nt_memory_provider,created,NULL);
    if(known){free(created);host_real_free_library(pin);}else known=created;
    host_nt_restore(&saved);return known;
}
static void *host_nt_memory_provider_target(HMODULE provider,int kind){
    if(!host_is_nt_memory(kind))return NULL;
    HostNtMemoryProvider *actual=host_nt_memory_publish(provider,__atomic_load_n(&host_windows_resolver,__ATOMIC_ACQUIRE));
    return actual?actual->exports[kind-HOST_NT_READ]:NULL;
}
static void host_nt_memory_targets(HostGetProcAddress resolve){
    if(!resolve)return;
    HostNtMemoryProvider *known=host_nt_memory_current();
    if(known)return;
    HostNtCrtErrors incoming=host_nt_crt_errors();HMODULE image=GetModuleHandleW(L"ntdll.dll");
    host_nt_memory_publish(image,resolve);host_nt_crt_restore(incoming);
}
static int host_nt_memory_target_matches(void *actual,int kind){
    HostNtMemoryProvider *provider=host_nt_memory_current();
    return actual&&provider&&host_is_nt_memory(kind)&&provider->exports[kind-HOST_NT_READ]==actual;
}
static int host_nt_memory_target_kind(void *actual){
    int selected=0;if(!actual)return 0;
    for(int kind=HOST_NT_READ;kind<=HOST_ZW_WRITE;kind++)if(host_nt_memory_target_matches(actual,kind)){
        if(selected&&selected!=kind)return 0;selected=kind;
    }
    return selected;
}
static int host_nt_memory_named_target_kind(void *actual,const char *name){
    if(!actual||!name)return 0;
    for(int kind=HOST_NT_READ;kind<=HOST_ZW_WRITE;kind++)
        if(!strcmp(name,host_nt_memory_name(kind))&&host_nt_memory_target_matches(actual,kind))return kind;
    return 0;
}
static int host_nt_memory_declared_name(const char *name,size_t maximum){
    if(!name)return 0;
    for(int kind=HOST_NT_READ;kind<=HOST_ZW_WRITE;kind++){
        const char *expected=host_nt_memory_name(kind);size_t bytes=strlen(expected)+1;
        if(bytes<=maximum&&!memcmp(name,expected,bytes))return kind;
    }
    return 0;
}

typedef struct HostNtTransferRanges {uint64_t dst,dstBytes,src,srcBytes,out,outBytes;} HostNtTransferRanges;
static HostNtTransferRanges host_nt_transfer_ranges(int reading,int local,const uintptr_t *a){
    HostNtTransferRanges ranges={0};uint64_t bytes=(uint64_t)(SIZE_T)a[3];
    if(reading){ranges.dst=(uint64_t)a[2];ranges.dstBytes=bytes;
        if(local){ranges.src=(uint64_t)a[1];ranges.srcBytes=bytes;}}
    else {ranges.src=(uint64_t)a[2];ranges.srcBytes=bytes;
        if(local){ranges.dst=(uint64_t)a[1];ranges.dstBytes=bytes;}}
    if(a[4]){ranges.out=(uint64_t)a[4];ranges.outBytes=sizeof(SIZE_T);}return ranges;
}
static int host_nt_transfer_control(const HostNtTransferRanges *ranges,int restricted){
    int hit=host_memory_range((const void*)(uintptr_t)ranges->dst,(SIZE_T)ranges->dstBytes,0,restricted);
    if(!hit)hit=host_memory_range((const void*)(uintptr_t)ranges->src,(SIZE_T)ranges->srcBytes,0,restricted);
    if(!hit)hit=host_memory_range((const void*)(uintptr_t)ranges->out,(SIZE_T)ranges->outBytes,0,restricted);
    return hit;
}
static int host_nt_transfer_private_locked(const HostNtTransferRanges *ranges){
    int hit=host_memory_output_locked((const void*)(uintptr_t)ranges->dst,(SIZE_T)ranges->dstBytes);
    if(!hit)hit=host_memory_output_locked((const void*)(uintptr_t)ranges->src,(SIZE_T)ranges->srcBytes);
    if(!hit)hit=host_memory_output_locked((const void*)(uintptr_t)ranges->out,(SIZE_T)ranges->outBytes);
    return hit;
}
static int host_nt_transfer_private_check(const HostNtTransferRanges *ranges,int restricted){
    if(!ranges->dstBytes&&!ranges->srcBytes&&!ranges->outBytes)return 0;
    int locked=restricted?TryAcquireSRWLockExclusive(&native_private_gate):(AcquireSRWLockExclusive(&native_private_gate),1);
    if(!locked)return -1;int hit=host_nt_transfer_private_locked(ranges);ReleaseSRWLockExclusive(&native_private_gate);return hit;
}
static uint64_t host_nt_transfer_original(HostImport *entry,HANDLE process,const uintptr_t *a,int native){
    if(native)return (uint64_t)(uint32_t)((HostNtMemoryTransfer)entry->original)(
            process,(PVOID)a[1],(PVOID)a[2],(SIZE_T)a[3],(PSIZE_T)a[4]);
    if(entry->kind==HOST_READ_PROCESS_MEMORY)return (uint64_t)(uint32_t)((HostReadProcessMemory)entry->original)(
            process,(LPCVOID)a[1],(LPVOID)a[2],(SIZE_T)a[3],(SIZE_T*)a[4]);
    return (uint64_t)(uint32_t)((HostWriteProcessMemory)entry->original)(
            process,(LPVOID)a[1],(LPCVOID)a[2],(SIZE_T)a[3],(SIZE_T*)a[4]);
}
static uint64_t host_native_memory_transfer(HostImport *entry,void *caller,uintptr_t *a,int callbackEntered,int native){
    HostNtThreadErrors incoming=host_nt_errors(),outgoing=incoming;int kind=entry->kind;
    if(!incoming.ready)return host_nt_transfer_refuse(native,&incoming,ERROR_NOT_SUPPORTED);
    if(native?!host_nt_memory_target_matches(entry->original,kind):!host_is_win32_memory_transfer(kind))
        return host_nt_transfer_refuse(native,&incoming,ERROR_INVALID_FUNCTION);
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    int restricted=host_loader_callout()||(state&&(state->heapCallback||state->toolingRestricted||state->vmDetaching||state->vmThreadEnding
            ||state->hostUnloadImage||state->hostRelease||state->hostLoad&&state->hostLoad->calling));
    HostImport *consumed=restricted?entry:host_consumed_entry(entry,state,caller);
    if(consumed->kind!=kind||consumed->original!=entry->original)return host_nt_transfer_refuse(native,&incoming,ERROR_INVALID_DATA);
    entry=consumed;OwnerLink *owners=NULL;int unknown=0,local=0,reading=host_nt_memory_reading(kind);
    DWORD error=0;HANDLE process=NULL;uint64_t result=0;int called=0,returned=0,finished=0,capturedState=0;
    HostNtTransferRanges ranges={0};(void)callbackEntered;
    __try {
        /* Restricted/loader calls use only the already captured C scopes and
         * Try locks. No new Java attachment or source inference is introduced. */
        if(restricted&&(!state||(!state->heapCallback&&!state->toolingRestricted&&!state->vmDetaching&&!state->vmThreadEnding
                &&!state->hostUnloadImage&&!state->hostRelease&&!(state->hostLoad&&state->hostLoad->calling))))error=ERROR_BUSY;
        if(!error&&!host_entry_sources_query(entry,state,caller,&owners,NULL,1,&unknown)){
            error=GetLastError();if(!error)error=ERROR_INVALID_DATA;
        }
        if(!error&&native_owner_stopped(owners))error=ERROR_ACCESS_DENIED;
        else if(!error&&unknown&&(restricted||native_has_producer(owners)))error=ERROR_INVALID_DATA;
        if(!error){
            /* These endpoints are local for every supplied process handle.
             * Protect them before even duplicating an invalid handle: handle
             * failure cannot hide an attempted control/count output. */
            ranges=host_nt_transfer_ranges(reading,0,a);
            int hit=host_nt_transfer_control(&ranges,restricted);
            if(!hit)hit=host_nt_transfer_private_check(&ranges,restricted);
            if(hit)error=hit>0?ERROR_ACCESS_DENIED:restricted?ERROR_BUSY:ERROR_INVALID_DATA;
        }
        if(!error){
            HostCompareHandles compare=__atomic_load_n(&host_compare_handles,__ATOMIC_ACQUIRE);
            if(!compare&&!restricted&&host_handle_identity_ready())compare=__atomic_load_n(&host_compare_handles,__ATOMIC_ACQUIRE);
            if(!compare)error=ERROR_NOT_SUPPORTED;
            else if(!host_real_duplicate(GetCurrentProcess(),(HANDLE)a[0],GetCurrentProcess(),&process,0,FALSE,DUPLICATE_SAME_ACCESS)){
                error=GetLastError();if(!error)error=ERROR_INVALID_HANDLE;
            }else local=compare(process,GetCurrentProcess())!=0;
        }
        int control=0;
        if(!error){
            ranges=host_nt_transfer_ranges(reading,local,a);
            control=host_nt_transfer_control(&ranges,restricted);
            if(control)error=control>0?ERROR_ACCESS_DENIED:restricted?ERROR_BUSY:ERROR_INVALID_DATA;
        }
        /* The count remains the actual caller pointer, including NULL and
         * aliases into source/destination. No output is cleared or reread. */
        if(!error){
            control=host_nt_transfer_control(&ranges,restricted);
            if(control)error=control>0?ERROR_ACCESS_DENIED:restricted?ERROR_BUSY:ERROR_INVALID_DATA;
            else if(native_owner_stopped(owners))error=ERROR_ACCESS_DENIED;
        }
        if(!error&&(ranges.dstBytes||ranges.srcBytes||ranges.outBytes)){
            int locked=restricted?TryAcquireSRWLockExclusive(&native_private_gate):(AcquireSRWLockExclusive(&native_private_gate),1);
            if(!locked)error=ERROR_BUSY;
            else __try {
                control=host_nt_transfer_private_locked(&ranges);
                if(control)error=control>0?ERROR_ACCESS_DENIED:restricted?ERROR_BUSY:ERROR_INVALID_DATA;
                else if(native_owner_stopped(owners))error=ERROR_ACCESS_DENIED;
                else {
                    /* Only this nonalertable original kernel copy runs under
                     * the existing private publication gate. No source/Host/
                     * row/JNI query, token publication or handle close does. */
                    host_nt_restore(&incoming);called=1;result=host_nt_transfer_original(entry,process,a,native);
                    outgoing=host_nt_result(&incoming);capturedState=1;returned=1;finished=1;
                }
            } __finally {
                if(called&&!capturedState){outgoing=host_nt_result(&incoming);capturedState=1;}
                ReleaseSRWLockExclusive(&native_private_gate);
            }
        }else if(!error){
            host_nt_restore(&incoming);called=1;result=host_nt_transfer_original(entry,process,a,native);
            outgoing=host_nt_result(&incoming);capturedState=1;returned=1;finished=1;
        }
        if(error){result=host_nt_transfer_refuse(native,&incoming,error);outgoing=host_nt_result(&incoming);finished=1;}
    } __finally {
        /* Retain original partial writes and errors. Native no longer records
         * byte images, allocator ownership or field completion receipts. */
        if(!finished&&called&&!capturedState)outgoing=host_nt_result(&incoming);
        if(process&&!host_real_close(process))InterlockedIncrement(&native_failures);
        native_owner_links_free(owners);host_nt_restore(&outgoing);
    }
    return result;
}
static uint64_t host_native_nt_memory(HostImport *entry,void *caller,uintptr_t *arguments,int callbackEntered){
    return host_native_memory_transfer(entry,caller,arguments,callbackEntered,1);
}
static uint64_t host_native_win32_memory_transfer(HostImport *entry,void *caller,uintptr_t *arguments,int callbackEntered){
    if(!host_nt_errors_ready()){
        /* The existing Win32 control fence does not invent LastNtStatus. */
        if(entry->kind==HOST_READ_PROCESS_MEMORY)return host_native_memory_read(entry,caller,arguments);
        if(entry->kind==HOST_WRITE_PROCESS_MEMORY)return host_native_memory(entry,caller,arguments);
    }
    return host_native_memory_transfer(entry,caller,arguments,callbackEntered,0);
}
