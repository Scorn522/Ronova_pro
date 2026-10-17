/* Included after host_crt_allocations.c. Only exact exports of already loaded
 * MSVC runtimes enter this path. These are allocation-function ABIs, not
 * new/delete expressions: no constructor, destructor, array cookie, virtual
 * table, or other C++ object layout is read or inferred here.
 * https://learn.microsoft.com/en-us/cpp/standard-library/new-operators
 * https://learn.microsoft.com/en-us/cpp/cpp/new-and-delete-operators
 * https://learn.microsoft.com/en-us/windows/win32/api/errhandlingapi/nf-errhandlingapi-raiseexception
 */
typedef void *(__cdecl *HostCppNew)(size_t);
typedef void *(__cdecl *HostCppNewNothrow)(size_t,const void*);
typedef void (__cdecl *HostCppDelete)(void*);
typedef void (__cdecl *HostCppDeleteNothrow)(void*,const void*);
typedef void (__cdecl *HostCppDeleteSized)(void*,size_t);

typedef struct HostCppAllocationTarget {
    void *original;int kind;volatile LONG unnamedSafe;HMODULE image,pin;
    HostCrtErrno error;HostCrtDosErrno dosError;
    HostCppDelete release;uintptr_t context;
    struct HostCppAllocationTarget *next;
} HostCppAllocationTarget;

static HostCppAllocationTarget *volatile host_cpp_allocation_exports;
static int host_is_cpp_allocation(int kind){
    return kind==HOST_CPP_NEW||kind==HOST_CPP_NEW_ARRAY||kind==HOST_CPP_DELETE||kind==HOST_CPP_DELETE_ARRAY
            ||kind>=HOST_CPP_NEW_NOTHROW&&kind<=HOST_CPP_DELETE_ARRAY_SIZED;
}
static int host_cpp_is_new(int kind){
    return kind==HOST_CPP_NEW||kind==HOST_CPP_NEW_ARRAY||kind==HOST_CPP_NEW_NOTHROW||kind==HOST_CPP_NEW_ARRAY_NOTHROW;
}
static int host_cpp_is_array(int kind){
    return kind==HOST_CPP_NEW_ARRAY||kind==HOST_CPP_DELETE_ARRAY||kind==HOST_CPP_NEW_ARRAY_NOTHROW
            ||kind==HOST_CPP_DELETE_ARRAY_NOTHROW||kind==HOST_CPP_DELETE_ARRAY_SIZED;
}
static const char *host_cpp_name(int kind){
    switch(kind){
        case HOST_CPP_NEW:return "??2@YAPEAX_K@Z";
        case HOST_CPP_NEW_ARRAY:return "??_U@YAPEAX_K@Z";
        case HOST_CPP_DELETE:return "??3@YAXPEAX@Z";
        case HOST_CPP_DELETE_ARRAY:return "??_V@YAXPEAX@Z";
        case HOST_CPP_NEW_NOTHROW:return "??2@YAPEAX_KAEBUnothrow_t@std@@@Z";
        case HOST_CPP_NEW_ARRAY_NOTHROW:return "??_U@YAPEAX_KAEBUnothrow_t@std@@@Z";
        case HOST_CPP_DELETE_NOTHROW:return "??3@YAXPEAXAEBUnothrow_t@std@@@Z";
        case HOST_CPP_DELETE_ARRAY_NOTHROW:return "??_V@YAXPEAXAEBUnothrow_t@std@@@Z";
        case HOST_CPP_DELETE_SIZED:return "??3@YAXPEAX_K@Z";
        case HOST_CPP_DELETE_ARRAY_SIZED:return "??_V@YAXPEAX_K@Z";
        default:return NULL;
    }
}
static HostCppAllocationTarget *host_cpp_target(void *actual,int kind){
    for(HostCppAllocationTarget *entry=(HostCppAllocationTarget*)InterlockedCompareExchangePointer(
                (void *volatile*)&host_cpp_allocation_exports,NULL,NULL);entry;entry=entry->next)
        if(entry->original==actual&&(!kind||entry->kind==kind))return entry;
    return NULL;
}
static int host_cpp_target_matches(void *actual,int kind){return actual&&host_is_cpp_allocation(kind)&&host_cpp_target(actual,kind)!=NULL;}
static int host_cpp_named_target_kind(void *actual,const char *name){
    if(!actual||!name)return 0;
    for(HostCppAllocationTarget *entry=(HostCppAllocationTarget*)InterlockedCompareExchangePointer(
                (void *volatile*)&host_cpp_allocation_exports,NULL,NULL);entry;entry=entry->next)
        if(entry->original==actual&&!strcmp(name,host_cpp_name(entry->kind)))return entry->kind;
    return 0;
}
static int host_cpp_image_range(DWORD rva,size_t bytes,DWORD size){return rva<=size&&bytes<=(size_t)(size-rva);}
static int host_cpp_operator_name(const char *name){
    return !strncmp(name,"??2@",4)||!strncmp(name,"??3@",4)||!strncmp(name,"??_U@",5)||!strncmp(name,"??_V@",5);
}
/* Some real MSVC DLLs give one code address to both a one-argument new[] and
 * a four-argument debug operator new. A raw code pointer cannot determine the
 * caller's ABI. Retain exact name+address routes, but never classify that raw
 * pointer by the first symbol found. Inspect only the pinned PE export table;
 * an unreadable/incomplete table also leaves the unnamed path unclassified.
 */
static int host_cpp_unnamed_safe(HMODULE image,void *actual,const char *name,HostGetProcAddress resolve){
    int safe=0;
    __try {
        unsigned char *base=(unsigned char*)image;IMAGE_DOS_HEADER *dos=(IMAGE_DOS_HEADER*)base;
        if(dos->e_magic==IMAGE_DOS_SIGNATURE&&dos->e_lfanew>0){
            IMAGE_NT_HEADERS64 *nt=(IMAGE_NT_HEADERS64*)(base+dos->e_lfanew);
            if(nt->Signature==IMAGE_NT_SIGNATURE&&nt->FileHeader.Machine==IMAGE_FILE_MACHINE_AMD64
                    &&nt->OptionalHeader.Magic==IMAGE_NT_OPTIONAL_HDR64_MAGIC
                    &&nt->OptionalHeader.NumberOfRvaAndSizes>IMAGE_DIRECTORY_ENTRY_EXPORT){
                DWORD size=nt->OptionalHeader.SizeOfImage;
                IMAGE_DATA_DIRECTORY directory=nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_EXPORT];
                if(directory.VirtualAddress&&directory.Size>=sizeof(IMAGE_EXPORT_DIRECTORY)
                        &&host_cpp_image_range(directory.VirtualAddress,directory.Size,size)){
                    IMAGE_EXPORT_DIRECTORY *exports=(IMAGE_EXPORT_DIRECTORY*)(base+directory.VirtualAddress);
                    if(exports->NumberOfNames&&host_cpp_image_range(exports->AddressOfNames,
                                (size_t)exports->NumberOfNames*sizeof(DWORD),size)){
                        DWORD *names=(DWORD*)(base+exports->AddressOfNames);safe=1;
                        for(DWORD i=0;i<exports->NumberOfNames;i++){
                            DWORD rva=names[i];if(!rva||!host_cpp_image_range(rva,1,size)
                                    ||!memchr(base+rva,0,(size_t)(size-rva))){safe=0;break;}
                            const char *alias=(const char*)(base+rva);
                            if(strcmp(alias,name)&&host_cpp_operator_name(alias)&&(void*)resolve(image,alias)==actual){safe=0;break;}
                        }
                    }
                }
            }
        }
    } __except(EXCEPTION_EXECUTE_HANDLER){safe=0;}
    return safe;
}
static void host_cpp_publish(HMODULE provider,int kind,HostGetProcAddress resolve){
    const char *name=host_cpp_name(kind);if(!provider||!name||!resolve)return;
    void *actual=(void*)resolve(provider,name);if(!actual)return;
    HostCppAllocationTarget *known=host_cpp_target(actual,kind);
    if(known){
        if(provider!=known->image&&!host_cpp_unnamed_safe(provider,actual,name,resolve))InterlockedExchange(&known->unnamedSafe,0);
        return;
    }
    HMODULE image=native_image(actual),pin=NULL;
    if(!image||!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,(LPCSTR)actual,&pin)||pin!=image){
        if(pin)host_real_free_library(pin);return;
    }
    /* A forwarder's spelling never chooses a different provider's heap/TLS.
     * The exact implementation must export both its matching scalar/array
     * deallocator and its own errno accessors. Otherwise keep the existing gap.
     */
    const char *releaseName=host_cpp_name(host_cpp_is_array(kind)?HOST_CPP_DELETE_ARRAY:HOST_CPP_DELETE);
    HostCppDelete release=(HostCppDelete)resolve(image,releaseName);
    void *providerRelease=(void*)resolve(provider,releaseName);
    HostCrtErrno error=(HostCrtErrno)resolve(image,"_errno");
    HostCrtDosErrno dosError=(HostCrtDosErrno)resolve(image,"__doserrno");
    if(!release||providerRelease!=(void*)release||!error||!dosError||native_image((void*)release)!=image||native_image((void*)error)!=image
            ||native_image((void*)dosError)!=image){host_real_free_library(pin);return;}
    HostCppAllocationTarget *entry=(HostCppAllocationTarget*)calloc(1,sizeof(*entry));
    if(!entry){host_real_free_library(pin);return;}
    entry->original=actual;entry->kind=kind;entry->image=image;entry->pin=pin;
    entry->error=error;entry->dosError=dosError;entry->release=release;entry->context=host_cpp_is_array(kind)?2:1;
    entry->unnamedSafe=host_cpp_unnamed_safe(image,actual,name,resolve)
            &&(provider==image||host_cpp_unnamed_safe(provider,actual,name,resolve));
    for(;;){
        HostCppAllocationTarget *head=(HostCppAllocationTarget*)InterlockedCompareExchangePointer(
                (void *volatile*)&host_cpp_allocation_exports,NULL,NULL);
        known=NULL;for(HostCppAllocationTarget *old=head;old;old=old->next)
            if(old->original==actual&&old->kind==kind){known=old;break;}
        if(known){if(!entry->unnamedSafe)InterlockedExchange(&known->unnamedSafe,0);host_real_free_library(pin);free(entry);return;}
        entry->next=head;
        if(InterlockedCompareExchangePointer((void *volatile*)&host_cpp_allocation_exports,entry,head)==head)return;
    }
}
static void host_cpp_targets(HostGetProcAddress resolve){
    if(!resolve)return;DWORD incoming=GetLastError();
    static const wchar_t *providers[]={L"msvcrt.dll",L"msvcr70.dll",L"msvcr71.dll",L"msvcr80.dll",L"msvcr90.dll",
        L"msvcr100.dll",L"msvcr110.dll",L"msvcr120.dll",L"msvcr70d.dll",L"msvcr71d.dll",L"msvcr80d.dll",
        L"msvcr90d.dll",L"msvcr100d.dll",L"msvcr110d.dll",L"msvcr120d.dll",
        L"msvcr100_clr0400.dll",L"msvcr120_clr0400.dll",L"msvcp140.dll",L"msvcp140d.dll",
        L"vcruntime140.dll",L"vcruntime140d.dll"};
    static const int kinds[]={HOST_CPP_NEW,HOST_CPP_NEW_ARRAY,HOST_CPP_DELETE,HOST_CPP_DELETE_ARRAY,
        HOST_CPP_NEW_NOTHROW,HOST_CPP_NEW_ARRAY_NOTHROW,HOST_CPP_DELETE_NOTHROW,HOST_CPP_DELETE_ARRAY_NOTHROW,
        HOST_CPP_DELETE_SIZED,HOST_CPP_DELETE_ARRAY_SIZED};
    for(unsigned i=0;i<sizeof(providers)/sizeof(providers[0]);i++){
        HMODULE provider=GetModuleHandleW(providers[i]);if(!provider)continue;
        for(unsigned k=0;k<sizeof(kinds)/sizeof(kinds[0]);k++)host_cpp_publish(provider,kinds[k],resolve);
    }
    SetLastError(incoming);
}
static int host_cpp_target_kind(void *actual){
    int kind=0;if(!actual)return 0;
    for(HostCppAllocationTarget *entry=(HostCppAllocationTarget*)InterlockedCompareExchangePointer(
                (void *volatile*)&host_cpp_allocation_exports,NULL,NULL);entry;entry=entry->next){
        if(entry->original!=actual)continue;
        if(!InterlockedCompareExchange(&entry->unnamedSafe,0,0)||kind&&kind!=entry->kind)return 0;kind=entry->kind;
    }
    return kind;
}
static int host_cpp_errors(HostImport *entry,HostCrtThreadErrors *saved){
    memset(saved,0,sizeof(*saved));saved->windowsBefore=GetLastError();
    HostCppAllocationTarget *target=host_cpp_target(entry->original,entry->kind);
    if(target){saved->error=target->error();if(saved->error)saved->before=*saved->error;
        saved->dosError=target->dosError();if(saved->dosError)saved->dosBefore=*saved->dosError;}
    SetLastError(saved->windowsBefore);return target&&saved->error&&saved->dosError;
}
static void host_cpp_restore(const HostCrtThreadErrors *saved){host_crt_allocation_restore(saved);}
static HostCrtThreadErrors host_cpp_result(const HostCrtThreadErrors *incoming){return host_crt_allocation_result(incoming);}
static void __attribute__((noreturn)) host_cpp_raise_refused(DWORD error){
    ULONG_PTR detail=(ULONG_PTR)error;
    /* This is a Ronova policy refusal, not std::bad_alloc. A throwing-new ABI
     * cannot report refusal by returning NULL or by invoking a new_handler in
     * place of the original allocator. The exception is never continued here.
     */
    RaiseException(0xE0520001u,EXCEPTION_NONCONTINUABLE,1,&detail);__builtin_unreachable();
}
static uint64_t host_cpp_refuse(int kind,const HostCrtThreadErrors *incoming,DWORD error){
    host_crt_allocation_refuse(incoming,error);
    if(kind==HOST_CPP_NEW||kind==HOST_CPP_NEW_ARRAY)host_cpp_raise_refused(error);
    return 0;
}
static uint64_t host_cpp_original(HostImport *entry,const uintptr_t *arguments){
    switch(entry->kind){
        case HOST_CPP_NEW:case HOST_CPP_NEW_ARRAY:
            return (uint64_t)(uintptr_t)((HostCppNew)entry->original)((size_t)arguments[0]);
        case HOST_CPP_NEW_NOTHROW:case HOST_CPP_NEW_ARRAY_NOTHROW:
            return (uint64_t)(uintptr_t)((HostCppNewNothrow)entry->original)((size_t)arguments[0],(const void*)arguments[1]);
        case HOST_CPP_DELETE:case HOST_CPP_DELETE_ARRAY:
            ((HostCppDelete)entry->original)((void*)arguments[0]);return 0;
        case HOST_CPP_DELETE_NOTHROW:case HOST_CPP_DELETE_ARRAY_NOTHROW:
            ((HostCppDeleteNothrow)entry->original)((void*)arguments[0],(const void*)arguments[1]);return 0;
        case HOST_CPP_DELETE_SIZED:case HOST_CPP_DELETE_ARRAY_SIZED:
            ((HostCppDeleteSized)entry->original)((void*)arguments[0],(size_t)arguments[1]);return 0;
        default:SetLastError(ERROR_INVALID_FUNCTION);return 0;
    }
}
static uint64_t host_native_cpp_allocation(HostImport *entry,void *caller,uintptr_t *arguments){
    HostCrtThreadErrors incoming;int errorsReady=host_cpp_errors(entry,&incoming);
    HostCppAllocationTarget *target=host_cpp_target(entry->original,entry->kind);
    if(!target)return host_cpp_refuse(entry->kind,&incoming,ERROR_INVALID_FUNCTION);
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    int restricted=state&&(state->heapCallback||state->toolingRestricted||state->vmDetaching||state->vmThreadEnding
            ||state->hostUnloadImage||state->hostRelease||state->hostLoad&&state->hostLoad->calling);
    HostImport *consumed=restricted?entry:host_consumed_entry(entry,state,caller);
    if(consumed->kind!=entry->kind||consumed->original!=entry->original)
        return host_cpp_refuse(entry->kind,&incoming,ERROR_INVALID_DATA);
    entry=consumed;OwnerLink *owners=NULL;int unknown=0;
    if(!host_entry_sources_query(entry,state,caller,&owners,NULL,1,&unknown)){
        DWORD error=GetLastError();native_owner_links_free(owners);return host_cpp_refuse(entry->kind,&incoming,error);
    }
    JNIEnv *env=host_memory_environment(restricted);int allocating=host_cpp_is_new(entry->kind);
    uint64_t address=allocating?0:(uint64_t)arguments[0],bytes=allocating?(uint64_t)(size_t)arguments[0]:0;
    int cleanup=address&&!allocating&&host_memory_cleanup_scope(entry,state);
    NativeHostMemoryCall *call=NULL;uint64_t result=0;int originalReturned=0,rawPass=0;
    HostCrtThreadErrors returned=incoming;
    __try {
        DWORD refusal=0;
        if(native_owner_stopped(owners)&&!cleanup)refusal=ERROR_ACCESS_DENIED;
        else if(unknown&&(restricted||native_has_producer(owners))&&!cleanup)refusal=ERROR_INVALID_DATA;
        if(!refusal&&address){
            /* Only the exact allocation-function pointer is a storage base.
             * Neither a sized-delete operand nor an array cookie authorizes
             * reading another byte or widening this control-memory check.
             */
            int hit=host_memory_range((const void*)(uintptr_t)address,1,0,restricted);
            if(hit)refusal=hit>0?ERROR_ACCESS_DENIED:restricted?ERROR_BUSY:ERROR_INVALID_DATA;
            if(!refusal){
                int locked=restricted?TryAcquireSRWLockExclusive(&native_private_gate):
                        (AcquireSRWLockExclusive(&native_private_gate),1);
                if(!locked)refusal=ERROR_BUSY;
                else {hit=host_memory_output_locked((const void*)(uintptr_t)address,1);
                    if(hit)refusal=hit>0?ERROR_ACCESS_DENIED:ERROR_INVALID_DATA;
                    ReleaseSRWLockExclusive(&native_private_gate);}
            }
            if(!refusal){int denied=0;
                call=native_memory_host_prepare(env,NATIVE_MEMORY_HOST_CPP,(void*)target->release,target->context,address,0,
                        NATIVE_MEMORY_HOST_FREE,owners,unknown,&denied);
                if(denied)refusal=ERROR_BUSY;
                else if(!native_memory_host_sources(call,&owners,&unknown))refusal=ERROR_NOT_ENOUGH_MEMORY;
            }
        }
        /* An unload cleanup scope is insufficient on its own: the exact live
         * allocation generation, scalar/array group, provider, and all current
         * users/aliases must also have acquired the existing release lease.
         */
        int cleanupKnown=cleanup&&native_memory_host_cleanup(call)&&host_memory_cleanup_scope(entry,state);
        if(!refusal&&native_owner_stopped(owners)&&!cleanupKnown)refusal=ERROR_ACCESS_DENIED;
        else if(!refusal&&unknown&&(restricted||native_has_producer(owners))&&!cleanupKnown)refusal=ERROR_INVALID_DATA;
        if(refusal){result=host_cpp_refuse(entry->kind,&incoming,refusal);returned=host_cpp_result(&incoming);}
        else if(!errorsReady){
            if(call){native_memory_host_abort(env,call);call=NULL;}
            native_memory_host_unobserved(owners);native_owner_links_free(owners);owners=NULL;
            InterlockedExchange(&entry->image->privateOpaque,1);rawPass=1;host_cpp_restore(&incoming);
            result=host_cpp_original(entry,arguments);
        }else {
            /* The original function owns zero-size/failure behavior and every
             * new_handler/exception. A real delete expression reaches this
             * storage function after destruction; Ronova invokes no destructor
             * and never substitutes malloc/free or an inferred delete target.
             * No ledger/private-directory lock crosses the original call.
             */
            host_cpp_restore(&incoming);result=host_cpp_original(entry,arguments);
            returned=host_cpp_result(&incoming);originalReturned=1;
            if(allocating&&result){
                InterlockedExchange(&entry->image->privateOpaque,1);
                native_memory_host_allocated(env,NATIVE_MEMORY_HOST_CPP,entry->original,(void*)target->release,target->context,0,
                        result,bytes,owners,unknown);
            }else if(call){native_memory_host_complete(env,call,0,0,1,1);call=NULL;}
        }
    } __finally {
        if(!rawPass){
            if(!originalReturned)returned=host_cpp_result(&incoming);
            if(call)native_memory_host_abort(env,call);
            native_owner_links_free(owners);host_cpp_restore(&returned);
        }
    }
    return result;
}
/* Unknown C++ layouts, inlined/statically linked allocation functions,
 * class-specific/custom placement/debug/aligned ABIs, and unobserved function
 * pointers remain gaps. These raw storage rows are never automatically
 * deleted by the memory backend: an unknown object's destructor obligations
 * cannot be discharged by calling operator delete on its bytes.
 */
