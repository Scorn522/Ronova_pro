/* Included after host_crt_allocations.c. Only exact exports of already loaded
 * MSVC or GNU Win64 runtimes enter this path. These are allocation-function ABIs, not
 * new/delete expressions: no constructor, destructor, array cookie, virtual
 * table, or other C++ object layout is read or inferred here.
 * https://learn.microsoft.com/en-us/cpp/standard-library/new-operators
 * https://learn.microsoft.com/en-us/cpp/cpp/new-and-delete-operators
 * https://learn.microsoft.com/en-us/windows/win32/api/errhandlingapi/nf-errhandlingapi-raiseexception
 * https://gcc.gnu.org/onlinedocs/gcc-16.1.0/libstdc++/api/a00041_source.html
 * https://itanium-cxx-abi.github.io/cxx-abi/abi.html#mangling
 */
typedef void *(__cdecl *HostCppNew)(size_t);
typedef void *(__cdecl *HostCppNewNothrow)(size_t,const void*);
typedef void (__cdecl *HostCppDelete)(void*);
typedef void (__cdecl *HostCppDeleteNothrow)(void*,const void*);
typedef void (__cdecl *HostCppDeleteSized)(void*,size_t);
typedef void *(__cdecl *HostCppNewAligned)(size_t,size_t);
typedef void *(__cdecl *HostCppNewAlignedNothrow)(size_t,size_t,const void*);
typedef void (__cdecl *HostCppDeleteAligned)(void*,size_t);
typedef void (__cdecl *HostCppDeleteAlignedNothrow)(void*,size_t,const void*);
typedef void (__cdecl *HostCppDeleteSizedAligned)(void*,size_t,size_t);

/* On Win64 GNU size_t is unsigned long long (y), not the 32-bit unsigned
 * long (m). No class-specific/debug/placement spelling is admitted here. */
typedef struct HostCppSymbol {int kind;const char *msvc,*gnu;} HostCppSymbol;
static const HostCppSymbol host_cpp_symbols[]={
    {HOST_CPP_NEW,"??2@YAPEAX_K@Z","_Znwy"},
    {HOST_CPP_NEW_ARRAY,"??_U@YAPEAX_K@Z","_Znay"},
    {HOST_CPP_DELETE,"??3@YAXPEAX@Z","_ZdlPv"},
    {HOST_CPP_DELETE_ARRAY,"??_V@YAXPEAX@Z","_ZdaPv"},
    {HOST_CPP_NEW_NOTHROW,"??2@YAPEAX_KAEBUnothrow_t@std@@@Z","_ZnwyRKSt9nothrow_t"},
    {HOST_CPP_NEW_ARRAY_NOTHROW,"??_U@YAPEAX_KAEBUnothrow_t@std@@@Z","_ZnayRKSt9nothrow_t"},
    {HOST_CPP_DELETE_NOTHROW,"??3@YAXPEAXAEBUnothrow_t@std@@@Z","_ZdlPvRKSt9nothrow_t"},
    {HOST_CPP_DELETE_ARRAY_NOTHROW,"??_V@YAXPEAXAEBUnothrow_t@std@@@Z","_ZdaPvRKSt9nothrow_t"},
    {HOST_CPP_DELETE_SIZED,"??3@YAXPEAX_K@Z","_ZdlPvy"},
    {HOST_CPP_DELETE_ARRAY_SIZED,"??_V@YAXPEAX_K@Z","_ZdaPvy"},
    {HOST_CPP_NEW_ALIGNED,"??2@YAPEAX_KW4align_val_t@std@@@Z","_ZnwySt11align_val_t"},
    {HOST_CPP_NEW_ARRAY_ALIGNED,"??_U@YAPEAX_KW4align_val_t@std@@@Z","_ZnaySt11align_val_t"},
    {HOST_CPP_NEW_ALIGNED_NOTHROW,"??2@YAPEAX_KW4align_val_t@std@@AEBUnothrow_t@1@@Z","_ZnwySt11align_val_tRKSt9nothrow_t"},
    {HOST_CPP_NEW_ARRAY_ALIGNED_NOTHROW,"??_U@YAPEAX_KW4align_val_t@std@@AEBUnothrow_t@1@@Z","_ZnaySt11align_val_tRKSt9nothrow_t"},
    {HOST_CPP_DELETE_ALIGNED,"??3@YAXPEAXW4align_val_t@std@@@Z","_ZdlPvSt11align_val_t"},
    {HOST_CPP_DELETE_ARRAY_ALIGNED,"??_V@YAXPEAXW4align_val_t@std@@@Z","_ZdaPvSt11align_val_t"},
    {HOST_CPP_DELETE_ALIGNED_NOTHROW,"??3@YAXPEAXW4align_val_t@std@@AEBUnothrow_t@1@@Z","_ZdlPvSt11align_val_tRKSt9nothrow_t"},
    {HOST_CPP_DELETE_ARRAY_ALIGNED_NOTHROW,"??_V@YAXPEAXW4align_val_t@std@@AEBUnothrow_t@1@@Z","_ZdaPvSt11align_val_tRKSt9nothrow_t"},
    {HOST_CPP_DELETE_SIZED_ALIGNED,"??3@YAXPEAX_KW4align_val_t@std@@@Z","_ZdlPvySt11align_val_t"},
    {HOST_CPP_DELETE_ARRAY_SIZED_ALIGNED,"??_V@YAXPEAX_KW4align_val_t@std@@@Z","_ZdaPvySt11align_val_t"}
};

typedef struct HostCppAllocationTarget {
    void *original;int kind;volatile LONG unnamedSafe,names;HMODULE image,pin,errorPin;
    HostCrtErrno error;HostCrtDosErrno dosError;
    void *release;
    struct HostCppAllocationTarget *next;
} HostCppAllocationTarget;

static HostCppAllocationTarget *volatile host_cpp_allocation_exports;
static int host_is_cpp_allocation(int kind){
    return kind==HOST_CPP_NEW||kind==HOST_CPP_NEW_ARRAY||kind==HOST_CPP_DELETE||kind==HOST_CPP_DELETE_ARRAY
            ||kind>=HOST_CPP_NEW_NOTHROW&&kind<=HOST_CPP_DELETE_ARRAY_SIZED
            ||kind>=HOST_CPP_NEW_ALIGNED&&kind<=HOST_CPP_DELETE_ARRAY_SIZED_ALIGNED;
}
static int host_cpp_is_new(int kind){
    return kind==HOST_CPP_NEW||kind==HOST_CPP_NEW_ARRAY||kind==HOST_CPP_NEW_NOTHROW||kind==HOST_CPP_NEW_ARRAY_NOTHROW
            ||kind>=HOST_CPP_NEW_ALIGNED&&kind<=HOST_CPP_NEW_ARRAY_ALIGNED_NOTHROW;
}
static int host_cpp_is_array(int kind){
    return kind==HOST_CPP_NEW_ARRAY||kind==HOST_CPP_DELETE_ARRAY||kind==HOST_CPP_NEW_ARRAY_NOTHROW
            ||kind==HOST_CPP_DELETE_ARRAY_NOTHROW||kind==HOST_CPP_DELETE_ARRAY_SIZED
            ||kind==HOST_CPP_NEW_ARRAY_ALIGNED||kind==HOST_CPP_NEW_ARRAY_ALIGNED_NOTHROW
            ||kind==HOST_CPP_DELETE_ARRAY_ALIGNED||kind==HOST_CPP_DELETE_ARRAY_ALIGNED_NOTHROW
            ||kind==HOST_CPP_DELETE_ARRAY_SIZED_ALIGNED;
}
static int host_cpp_is_aligned(int kind){return kind>=HOST_CPP_NEW_ALIGNED&&kind<=HOST_CPP_DELETE_ARRAY_SIZED_ALIGNED;}
static int host_cpp_is_throwing(int kind){return kind==HOST_CPP_NEW||kind==HOST_CPP_NEW_ARRAY
        ||kind==HOST_CPP_NEW_ALIGNED||kind==HOST_CPP_NEW_ARRAY_ALIGNED;}
static const HostCppSymbol *host_cpp_symbol(int kind){
    for(unsigned i=0;i<sizeof(host_cpp_symbols)/sizeof(host_cpp_symbols[0]);i++)if(host_cpp_symbols[i].kind==kind)return &host_cpp_symbols[i];
    return NULL;
}
static const char *host_cpp_name(int kind){
    const HostCppSymbol *symbol=host_cpp_symbol(kind);return symbol?symbol->msvc:NULL;
}
static int host_cpp_declared_name(const char *name,size_t maximum){
    if(!name)return 0;
    for(unsigned i=0;i<sizeof(host_cpp_symbols)/sizeof(host_cpp_symbols[0]);i++){
        const HostCppSymbol *symbol=&host_cpp_symbols[i];size_t length=strlen(symbol->msvc)+1;
        if(length<=maximum&&!memcmp(name,symbol->msvc,length))return symbol->kind;
        length=strlen(symbol->gnu)+1;if(length<=maximum&&!memcmp(name,symbol->gnu,length))return symbol->kind;
    }
    return 0;
}
/* Preserve the established unaligned groups 1/2. Every valid Win64 alignment
 * has one set bit; its position plus array/scalar is an injective context.
 * This does not validate on behalf of the original operator: invalid operands
 * still reach that function unchanged, but cannot create an exact receipt. */
static uintptr_t host_cpp_context(int kind,const uintptr_t *arguments,int *ready){
    int array=host_cpp_is_array(kind);*ready=1;if(!host_cpp_is_aligned(kind))return array?2:1;
    size_t alignment=(size_t)arguments[kind==HOST_CPP_DELETE_SIZED_ALIGNED||kind==HOST_CPP_DELETE_ARRAY_SIZED_ALIGNED?2:1];
    if(!alignment||(alignment&(alignment-1))){*ready=0;return 0;}
    unsigned bit=0;while((alignment>>=1)!=0)bit++;return (uintptr_t)3+2*bit+(unsigned)array;
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
        if(entry->original==actual){const HostCppSymbol *symbol=host_cpp_symbol(entry->kind);
            LONG names=InterlockedCompareExchange(&entry->names,0,0);
            if(symbol&&((names&1)&&!strcmp(name,symbol->msvc)||(names&2)&&!strcmp(name,symbol->gnu)))return entry->kind;}
    return 0;
}
static int host_cpp_image_range(DWORD rva,size_t bytes,DWORD size){return rva<=size&&bytes<=(size_t)(size-rva);}
static int host_cpp_operator_name(const char *name){
    return !strncmp(name,"??2@",4)||!strncmp(name,"??3@",4)||!strncmp(name,"??_U@",5)||!strncmp(name,"??_V@",5)
            ||!strncmp(name,"_Zn",3)||!strncmp(name,"_Zd",3);
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
/* GNU and modern MSVC allocation functions may import their CRT rather than
 * re-export its errno accessors. Resolve only actually named, already loaded
 * CRT imports of this exact implementation. Multiple different TLS domains
 * are incomplete observation, never an excuse to substitute this DLL's CRT.
 * No imported function is executed during discovery. */
static int host_cpp_error_pair(HMODULE image,HostGetProcAddress resolve,HostCrtErrno *error,HostCrtDosErrno *dosError){
    HostCrtErrno candidate=(HostCrtErrno)resolve(image,"_errno");
    HostCrtDosErrno dos=(HostCrtDosErrno)resolve(image,"__doserrno");
    if(!candidate||!dos||native_image((void*)candidate)!=image||native_image((void*)dos)!=image)return 0;
    if(*error&&(*error!=candidate||*dosError!=dos))return -1;*error=candidate;*dosError=dos;return 1;
}
static int host_cpp_crt_import_kind(const char *name){
    for(int kind=HOST_CRT_MALLOC;kind<=HOST_CRT_FREE;kind++)if(!strcmp(name,host_crt_allocation_name(kind)))return kind;
    for(int kind=HOST_CRT_ALIGNED_MALLOC;kind<=HOST_CRT_ALIGNED_MSIZE;kind++)if(!strcmp(name,host_crt_aligned_name(kind)))return kind;
    return 0;
}
static void *host_cpp_import_original(HMODULE image,void *volatile *slot,int kind,const char *name,HostGetProcAddress resolve){
    void *current=__atomic_load_n(slot,__ATOMIC_ACQUIRE);HMODULE implementation=native_image(current);
    if(implementation&&(void*)resolve(implementation,name)==current)return current;
    /* Only this live consumer's exact current IAT record can unwrap a Host
     * thunk. A returned GPA entry, another slot/image, or a nominal descriptor
     * name cannot establish which CRT this implementation actually consumes. */
    if(!kind||!TryAcquireSRWLockShared(&host_imports))return NULL;
    void *original=NULL;
    for(HostImage *consumer=host_images;consumer&&!original;consumer=consumer->next){
        if(!consumer->live||consumer->releasing||consumer->handle!=image)continue;
        for(HostImport *entry=consumer->imports;entry;entry=entry->next)
            if(entry->image==consumer&&entry->slot==slot&&entry->entry==current&&entry->kind==kind&&entry->original
                    &&__atomic_load_n(slot,__ATOMIC_ACQUIRE)==current){original=entry->original;break;}
    }
    ReleaseSRWLockShared(&host_imports);
    if(!original||__atomic_load_n(slot,__ATOMIC_ACQUIRE)!=current)return NULL;
    implementation=native_image(original);
    return implementation&&(void*)resolve(implementation,name)==original?original:NULL;
}
static int host_cpp_imported_errors(HMODULE image,HostGetProcAddress resolve,HostCrtErrno *error,HostCrtDosErrno *dosError){
    int ready=0;
    __try {
        unsigned char *base=(unsigned char*)image;IMAGE_DOS_HEADER *dos=(IMAGE_DOS_HEADER*)base;
        if(dos->e_magic!=IMAGE_DOS_SIGNATURE||dos->e_lfanew<=0)__leave;
        IMAGE_NT_HEADERS64 *nt=(IMAGE_NT_HEADERS64*)(base+dos->e_lfanew);
        if(nt->Signature!=IMAGE_NT_SIGNATURE||nt->FileHeader.Machine!=IMAGE_FILE_MACHINE_AMD64
                ||nt->OptionalHeader.Magic!=IMAGE_NT_OPTIONAL_HDR64_MAGIC
                ||nt->OptionalHeader.NumberOfRvaAndSizes<=IMAGE_DIRECTORY_ENTRY_IMPORT)__leave;
        DWORD size=nt->OptionalHeader.SizeOfImage;IMAGE_DATA_DIRECTORY dir=nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_IMPORT];
        if(!dir.VirtualAddress||dir.Size<sizeof(IMAGE_IMPORT_DESCRIPTOR)||!host_cpp_image_range(dir.VirtualAddress,dir.Size,size))__leave;
        IMAGE_IMPORT_DESCRIPTOR *imports=(IMAGE_IMPORT_DESCRIPTOR*)(base+dir.VirtualAddress);int complete=1,terminated=0;
        for(DWORD i=0;i<dir.Size/sizeof(*imports);i++){
            IMAGE_IMPORT_DESCRIPTOR *descriptor=&imports[i];if(!descriptor->Name){terminated=1;break;}
            if(!host_cpp_image_range(descriptor->Name,1,size)||!memchr(base+descriptor->Name,0,size-descriptor->Name)){complete=0;break;}
            DWORD rva=descriptor->OriginalFirstThunk,iat=descriptor->FirstThunk;
            if(!rva||!iat||!host_cpp_image_range(rva,sizeof(IMAGE_THUNK_DATA64),size)
                    ||!host_cpp_image_range(iat,sizeof(IMAGE_THUNK_DATA64),size)){complete=0;break;}
            IMAGE_THUNK_DATA64 *names=(IMAGE_THUNK_DATA64*)(base+rva),*slots=(IMAGE_THUNK_DATA64*)(base+iat);
            size_t capacity=(size-rva)/sizeof(*names),slotCapacity=(size-iat)/sizeof(*slots);int ended=0;
            if(slotCapacity<capacity)capacity=slotCapacity;
            for(size_t n=0;n<capacity;n++){
                ULONGLONG address=names[n].u1.AddressOfData;if(!address){ended=1;break;}if(IMAGE_SNAP_BY_ORDINAL64(address))continue;
                if(address>=size||size-address<sizeof(WORD)+1){complete=0;break;}
                const char *name=(const char*)base+(size_t)address+sizeof(WORD);
                if(!memchr(name,0,size-(size_t)address-sizeof(WORD))){complete=0;break;}
                int kind=host_cpp_crt_import_kind(name);
                if(!kind&&strcmp(name,"_errno")&&strcmp(name,"__doserrno"))continue;
                void *actual=host_cpp_import_original(image,(void *volatile*)&slots[n].u1.Function,kind,name,resolve);
                HMODULE implementation=actual?native_image(actual):NULL;
                if(!implementation||host_cpp_error_pair(implementation,resolve,error,dosError)!=1){complete=0;break;}
            }
            if(!complete||!ended){complete=0;break;}
        }
        ready=complete&&terminated&&*error&&*dosError;
    } __except(EXCEPTION_EXECUTE_HANDLER){ready=0;}
    return ready;
}
static void host_cpp_publish(HMODULE provider,const HostCppSymbol *symbol,int gnu,HostGetProcAddress resolve){
    const char *name=gnu?symbol->gnu:symbol->msvc;int kind=symbol->kind;
    void *actual=(void*)resolve(provider,name);if(!actual)return;
    HMODULE image=native_image(actual),pin=NULL,errorPin=NULL;if(!image)return;
    int releaseKind=host_cpp_is_aligned(kind)?(host_cpp_is_array(kind)?HOST_CPP_DELETE_ARRAY_ALIGNED:HOST_CPP_DELETE_ALIGNED)
            :(host_cpp_is_array(kind)?HOST_CPP_DELETE_ARRAY:HOST_CPP_DELETE);
    const HostCppSymbol *releaseSymbol=host_cpp_symbol(releaseKind);
    const char *releaseName=gnu?releaseSymbol->gnu:releaseSymbol->msvc;
    void *release=(void*)resolve(image,releaseName);
    if(!release||(void*)resolve(provider,releaseName)!=release||native_image(release)!=image)return;
    HostCppAllocationTarget *known=host_cpp_target(actual,kind);
    if(known){
        if(known->image==image&&known->release==release){InterlockedOr(&known->names,gnu?2:1);
            if(!host_cpp_unnamed_safe(provider,actual,name,resolve))InterlockedExchange(&known->unnamedSafe,0);}
        return;
    }
    if(!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,(LPCSTR)actual,&pin)||pin!=image){
        if(pin)host_real_free_library(pin);return;}
    HostCrtErrno error=NULL;HostCrtDosErrno dosError=NULL;
    if(host_cpp_error_pair(image,resolve,&error,&dosError)!=1&&!host_cpp_imported_errors(image,resolve,&error,&dosError)){
        host_real_free_library(pin);return;}
    if(!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,(LPCSTR)error,&errorPin)
            ||errorPin!=native_image((void*)dosError)){
        if(errorPin)host_real_free_library(errorPin);host_real_free_library(pin);return;}
    HostCppAllocationTarget *entry=(HostCppAllocationTarget*)calloc(1,sizeof(*entry));
    if(!entry){host_real_free_library(errorPin);host_real_free_library(pin);return;}
    entry->original=actual;entry->kind=kind;entry->image=image;entry->pin=pin;entry->errorPin=errorPin;
    entry->error=error;entry->dosError=dosError;entry->release=release;entry->names=gnu?2:1;
    entry->unnamedSafe=host_cpp_unnamed_safe(image,actual,name,resolve)
            &&(provider==image||host_cpp_unnamed_safe(provider,actual,name,resolve));
    for(;;){
        HostCppAllocationTarget *head=(HostCppAllocationTarget*)InterlockedCompareExchangePointer(
                (void *volatile*)&host_cpp_allocation_exports,NULL,NULL);
        known=NULL;for(HostCppAllocationTarget *old=head;old;old=old->next)
            if(old->original==actual&&old->kind==kind){known=old;break;}
        if(known){if(known->image==image&&known->release==release){InterlockedOr(&known->names,entry->names);
                if(!entry->unnamedSafe)InterlockedExchange(&known->unnamedSafe,0);}
            host_real_free_library(errorPin);host_real_free_library(pin);free(entry);return;}
        entry->next=head;
        if(InterlockedCompareExchangePointer((void *volatile*)&host_cpp_allocation_exports,entry,head)==head)return;
    }
}
static void host_cpp_discover(HMODULE image){
    HostGetProcAddress resolve=__atomic_load_n(&host_windows_resolver,__ATOMIC_ACQUIRE);if(!image||!resolve)return;
    DWORD incoming=GetLastError();
    for(unsigned i=0;i<sizeof(host_cpp_symbols)/sizeof(host_cpp_symbols[0]);i++){
        host_cpp_publish(image,&host_cpp_symbols[i],0,resolve);host_cpp_publish(image,&host_cpp_symbols[i],1,resolve);}
    SetLastError(incoming);
}
static void host_cpp_discover_imports(HMODULE image){
    if(!image)return;DWORD incoming=GetLastError();
    __try {
        unsigned char *base=(unsigned char*)image;IMAGE_DOS_HEADER *dos=(IMAGE_DOS_HEADER*)base;
        if(dos->e_magic!=IMAGE_DOS_SIGNATURE||dos->e_lfanew<=0)__leave;
        IMAGE_NT_HEADERS64 *nt=(IMAGE_NT_HEADERS64*)(base+dos->e_lfanew);
        if(nt->Signature!=IMAGE_NT_SIGNATURE||nt->FileHeader.Machine!=IMAGE_FILE_MACHINE_AMD64
                ||nt->OptionalHeader.Magic!=IMAGE_NT_OPTIONAL_HDR64_MAGIC
                ||nt->OptionalHeader.NumberOfRvaAndSizes<=IMAGE_DIRECTORY_ENTRY_IMPORT)__leave;
        DWORD size=nt->OptionalHeader.SizeOfImage;IMAGE_DATA_DIRECTORY dir=nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_IMPORT];
        if(!dir.VirtualAddress||dir.Size<sizeof(IMAGE_IMPORT_DESCRIPTOR)||!host_cpp_image_range(dir.VirtualAddress,dir.Size,size))__leave;
        IMAGE_IMPORT_DESCRIPTOR *imports=(IMAGE_IMPORT_DESCRIPTOR*)(base+dir.VirtualAddress);
        for(DWORD i=0;i<dir.Size/sizeof(*imports);i++){
            DWORD name=imports[i].Name;if(!name)break;
            if(!host_cpp_image_range(name,1,size)||!memchr(base+name,0,size-name))break;
            HMODULE provider=GetModuleHandleA((const char*)base+name);if(provider)host_cpp_discover(provider);
        }
    } __except(EXCEPTION_EXECUTE_HANDLER){}
    SetLastError(incoming);
}
static void host_cpp_targets(HostGetProcAddress resolve){
    if(!resolve)return;DWORD incoming=GetLastError();
    static const wchar_t *providers[]={L"msvcrt.dll",L"msvcr70.dll",L"msvcr71.dll",L"msvcr80.dll",L"msvcr90.dll",
        L"msvcr100.dll",L"msvcr110.dll",L"msvcr120.dll",L"msvcr70d.dll",L"msvcr71d.dll",L"msvcr80d.dll",
        L"msvcr90d.dll",L"msvcr100d.dll",L"msvcr110d.dll",L"msvcr120d.dll",L"msvcr100_clr0400.dll",L"msvcr120_clr0400.dll",
        L"msvcp140.dll",L"msvcp140d.dll",L"vcruntime140.dll",L"vcruntime140d.dll",L"vcruntime140_1.dll",L"libstdc++-6.dll"};
    for(unsigned i=0;i<sizeof(providers)/sizeof(providers[0]);i++){
        HMODULE provider=GetModuleHandleW(providers[i]);if(provider)host_cpp_discover(provider);}
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
    if(host_cpp_is_throwing(kind))host_cpp_raise_refused(error);
    return 0;
}
static uint64_t host_cpp_original(HostImport *entry,const uintptr_t *arguments){
    switch(entry->kind){
        case HOST_CPP_NEW:case HOST_CPP_NEW_ARRAY:
            return (uint64_t)(uintptr_t)((HostCppNew)entry->original)((size_t)arguments[0]);
        case HOST_CPP_NEW_NOTHROW:case HOST_CPP_NEW_ARRAY_NOTHROW:
            return (uint64_t)(uintptr_t)((HostCppNewNothrow)entry->original)((size_t)arguments[0],(const void*)arguments[1]);
        case HOST_CPP_NEW_ALIGNED:case HOST_CPP_NEW_ARRAY_ALIGNED:
            return (uint64_t)(uintptr_t)((HostCppNewAligned)entry->original)((size_t)arguments[0],(size_t)arguments[1]);
        case HOST_CPP_NEW_ALIGNED_NOTHROW:case HOST_CPP_NEW_ARRAY_ALIGNED_NOTHROW:
            return (uint64_t)(uintptr_t)((HostCppNewAlignedNothrow)entry->original)((size_t)arguments[0],(size_t)arguments[1],(const void*)arguments[2]);
        case HOST_CPP_DELETE:case HOST_CPP_DELETE_ARRAY:
            ((HostCppDelete)entry->original)((void*)arguments[0]);return 0;
        case HOST_CPP_DELETE_NOTHROW:case HOST_CPP_DELETE_ARRAY_NOTHROW:
            ((HostCppDeleteNothrow)entry->original)((void*)arguments[0],(const void*)arguments[1]);return 0;
        case HOST_CPP_DELETE_SIZED:case HOST_CPP_DELETE_ARRAY_SIZED:
            ((HostCppDeleteSized)entry->original)((void*)arguments[0],(size_t)arguments[1]);return 0;
        case HOST_CPP_DELETE_ALIGNED:case HOST_CPP_DELETE_ARRAY_ALIGNED:
            ((HostCppDeleteAligned)entry->original)((void*)arguments[0],(size_t)arguments[1]);return 0;
        case HOST_CPP_DELETE_ALIGNED_NOTHROW:case HOST_CPP_DELETE_ARRAY_ALIGNED_NOTHROW:
            ((HostCppDeleteAlignedNothrow)entry->original)((void*)arguments[0],(size_t)arguments[1],(const void*)arguments[2]);return 0;
        case HOST_CPP_DELETE_SIZED_ALIGNED:case HOST_CPP_DELETE_ARRAY_SIZED_ALIGNED:
            ((HostCppDeleteSizedAligned)entry->original)((void*)arguments[0],(size_t)arguments[1],(size_t)arguments[2]);return 0;
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
    int contextReady=0;uintptr_t context=host_cpp_context(entry->kind,arguments,&contextReady);
    int cleanup=address&&!allocating&&host_memory_cleanup_scope(entry,state);
    NativeHostMemoryCall *call=NULL;uint64_t result=0;int originalStarted=0,originalReturned=0,rawPass=0,scoped=0;
    NativeHostCppOriginalScope scope={0};
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
                call=native_memory_host_prepare(env,NATIVE_MEMORY_HOST_CPP,target->release,context,address,0,
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
            /* Only this original call's own image can pair an intercepted CRT
             * allocation/free with its storage generation. It is neither a
             * provider-wide bypass nor a capability for an unknown pointer. */
            if(!state)state=native_thread();
            if(state){scope.image=target->image;scope.original=entry->original;scope.release=target->release;
                scope.entry=entry;scope.context=context;scope.call=call;scope.thread=GetCurrentThreadId();scope.cleanup=cleanupKnown;
                scope.operation=allocating?NATIVE_MEMORY_HOST_ALLOCATE:NATIVE_MEMORY_HOST_FREE;
                scope.previous=state->hostCppOriginal;state->hostCppOriginal=&scope;scoped=1;}
            host_cpp_restore(&incoming);originalStarted=1;result=host_cpp_original(entry,arguments);
            returned=host_cpp_result(&incoming);originalReturned=1;
            if(allocating&&result){
                InterlockedExchange(&entry->image->privateOpaque,1);
                native_memory_host_cpp_allocated(env,scoped?&scope:NULL,entry->original,target->release,context,
                        result,bytes,owners,unknown||!contextReady);
            }else if(call){scope.call=NULL;native_memory_host_complete(env,call,0,0,1,1);call=NULL;}
        }
    } __finally {
        if(!rawPass){
            if(!originalReturned)returned=host_cpp_result(&incoming);
            if(call){scope.call=NULL;if(originalStarted)native_memory_host_abort(env,call);else native_memory_host_cancel(call);}
            if(scoped){native_memory_host_cpp_scope_end(&scope);state->hostCppOriginal=scope.previous;native_thread_try_release(state);}
            native_owner_links_free(owners);host_cpp_restore(&returned);
        }
    }
    return result;
}
/* Unknown C++ layouts, inlined/statically linked allocation functions,
 * class-specific/custom placement/debug ABIs, and unobserved function
 * pointers remain gaps. These raw storage rows are never automatically
 * deleted by the memory backend: an unknown object's destructor obligations
 * cannot be discharged by calling operator delete on its bytes. An observed
 * public operator delete is the caller's own actual release after any real
 * destructor; it does not prove how arbitrary object fields are represented.
 */
