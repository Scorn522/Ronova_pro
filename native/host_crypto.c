/* Actual bcrypt.dll imports/results of an observed caller image. These are
 * opaque CNG handles, never addresses of owned memory or decoded hash state.
 * Only an original successful Open/Create publishes a new generation. No
 * callback or automatic cleanup invokes somebody else's CNG destructor.
 * https://learn.microsoft.com/en-us/windows/win32/api/bcrypt/nf-bcrypt-bcryptopenalgorithmprovider
 * https://learn.microsoft.com/en-us/windows/win32/api/bcrypt/nf-bcrypt-bcryptcreatehash
 * https://learn.microsoft.com/en-us/windows/win32/api/bcrypt/nf-bcrypt-bcrypthashdata
 * https://learn.microsoft.com/en-us/windows/win32/api/bcrypt/nf-bcrypt-bcryptfinishhash
 * https://learn.microsoft.com/en-us/windows/win32/api/bcrypt/nf-bcrypt-bcryptdestroyhash
 * https://learn.microsoft.com/en-us/windows/win32/api/bcrypt/nf-bcrypt-bcryptclosealgorithmprovider
 */
#include <bcrypt.h>

typedef struct HostCryptoErrors {DWORD windows;int crt;unsigned long dos;} HostCryptoErrors;
static HostCryptoErrors host_crypto_errors(void){
    HostCryptoErrors value;value.windows=GetLastError();value.crt=errno;value.dos=_doserrno;return value;
}
static void host_crypto_restore(HostCryptoErrors value){_doserrno=value.dos;errno=value.crt;SetLastError(value.windows);}
static int host_is_crypto(int kind){return kind>=HOST_CNG_OPEN&&kind<=HOST_CNG_CLOSE;}
static LONG host_crypto_refusal(DWORD error){
    return (LONG)(error==ERROR_ACCESS_DENIED?0xc0000022u:error==ERROR_BUSY?0x80000011u:
            error==ERROR_NOT_ENOUGH_MEMORY?0xc0000017u:error==ERROR_INVALID_HANDLE?0xc0000008u:0xc0000184u);
}

/* memory.c supplies these exact observed-range leases after its allocation
 * definition. A missing row stays uncovered; it is never inferred from a CNG
 * handle or from a stack/Java buffer address. Mode 1 retains users+reading
 * until actual DestroyHash, including against an embedded owner's destructor. */
typedef struct NativeHostCryptoSpan NativeHostCryptoSpan;
static NativeHostCryptoSpan *native_memory_crypto_span(uint64_t,uint64_t,OwnerLink*,int,OwnerLink**,int*,int*);
static void native_memory_crypto_span_end(NativeHostCryptoSpan*);
static void native_memory_crypto_unobserved(OwnerLink*,int);

typedef struct HostCryptoHandle {
    uintptr_t value;uint64_t generation,parentGeneration;
    void *provider;OwnerLink *owners,*contributors;
    struct HostCryptoHandle *parent;NativeHostCryptoSpan *storage;
    ULONG flags;unsigned uses;int hash,live,closing,uncertain,dataUnknown,finished;
    struct HostCryptoHandle *next;
} HostCryptoHandle;
static SRWLOCK host_crypto_records=SRWLOCK_INIT;
static HostCryptoHandle *host_crypto_handles;
static uint64_t host_crypto_generation;
static int host_crypto_lock(int restricted){
    return restricted?TryAcquireSRWLockExclusive(&host_crypto_records):(AcquireSRWLockExclusive(&host_crypto_records),1);
}

/* Records remain tombstones. A child keeps its own generation and sources
 * after an ordinary user-mode provider close; its parent's numeric value never
 * rebinds it to a later Open. Pseudo-handles gain no invented algorithm row. */
static HostCryptoHandle *host_crypto_find(uintptr_t value,int hash,void *provider,int *ambiguous){
    HostCryptoHandle *found=NULL;*ambiguous=0;
    for(HostCryptoHandle *row=host_crypto_handles;row;row=row->next)
        if(row->live&&row->value==value&&row->hash==hash&&row->provider==provider){
            if(found){*ambiguous=1;return found;}found=row;
        }
    return found;
}
static int host_crypto_same_sources(OwnerLink *first,OwnerLink *second){
    return first&&second&&host_owners_contain(first,second)&&host_owners_contain(second,first);
}
static LONG host_crypto_original(HostImport *entry,const uintptr_t *a){
    switch(entry->kind){
        case HOST_CNG_OPEN:return ((NTSTATUS (WINAPI*)(BCRYPT_ALG_HANDLE*,LPCWSTR,LPCWSTR,ULONG))entry->original)(
                (BCRYPT_ALG_HANDLE*)a[0],(LPCWSTR)a[1],(LPCWSTR)a[2],(ULONG)a[3]);
        case HOST_CNG_CREATE_HASH:return ((NTSTATUS (WINAPI*)(BCRYPT_ALG_HANDLE,BCRYPT_HASH_HANDLE*,PUCHAR,ULONG,PUCHAR,ULONG,ULONG))entry->original)(
                (BCRYPT_ALG_HANDLE)a[0],(BCRYPT_HASH_HANDLE*)a[1],(PUCHAR)a[2],(ULONG)a[3],(PUCHAR)a[4],(ULONG)a[5],(ULONG)a[6]);
        case HOST_CNG_HASH_DATA:case HOST_CNG_FINISH_HASH:
            return ((NTSTATUS (WINAPI*)(BCRYPT_HASH_HANDLE,PUCHAR,ULONG,ULONG))entry->original)(
                    (BCRYPT_HASH_HANDLE)a[0],(PUCHAR)a[1],(ULONG)a[2],(ULONG)a[3]);
        case HOST_CNG_DESTROY_HASH:return ((NTSTATUS (WINAPI*)(BCRYPT_HASH_HANDLE))entry->original)((BCRYPT_HASH_HANDLE)a[0]);
        case HOST_CNG_CLOSE:return ((NTSTATUS (WINAPI*)(BCRYPT_ALG_HANDLE,ULONG))entry->original)((BCRYPT_ALG_HANDLE)a[0],(ULONG)a[1]);
        default:return (LONG)0xc000000du;
    }
}
static uint64_t host_native_crypto(HostImport *entry,void *caller,uintptr_t *a){
    HostCryptoErrors incoming=host_crypto_errors(),outgoing=incoming;
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    int restricted=host_loader_callout()||(state&&(state->heapCallback||state->toolingRestricted||state->vmDetaching||state->vmThreadEnding
            ||state->hostUnloadImage||state->hostRelease||state->hostLoad&&state->hostLoad->calling));
    if(!restricted)entry=host_consumed_entry(entry,state,caller);
    OwnerLink *owners=NULL,*observed=NULL;int unknown=0,eligible=0,captured=0;
    DWORD error=0;LONG status=(LONG)0xc0000184u;
    int kind=entry->kind,opening=kind==HOST_CNG_OPEN,creating=kind==HOST_CNG_CREATE_HASH;
    int destroying=kind==HOST_CNG_DESTROY_HASH||kind==HOST_CNG_CLOSE;
    void *provider=__atomic_load_n(&host_windows_api[HOST_CNG_OPEN],__ATOMIC_ACQUIRE);
    HostCryptoHandle *row=NULL,*created=NULL;int held=0,called=0,returned=0,ambiguous=0,dataUnknown=0;
    NativeHostCryptoSpan *storage=NULL,*data=NULL;
    captured=host_entry_sources_query(entry,state,caller,&owners,NULL,1,&unknown);
    if(!captured){error=GetLastError();if(!error)error=ERROR_INVALID_DATA;}
    eligible=captured&&native_state_caller_eligible(state,caller,owners,unknown);
    if(entry->original!=__atomic_load_n(&host_windows_api[kind],__ATOMIC_ACQUIRE)||!provider)error=ERROR_INVALID_DATA;
    if(!opening){
        if(!host_crypto_lock(restricted))error=ERROR_BUSY;
        else {
        row=host_crypto_find(a[0],kind!=HOST_CNG_CREATE_HASH&&kind!=HOST_CNG_CLOSE,provider,&ambiguous);
        /* Target admission is independent of whether this caller could create
         * a new row. An ordinary/unknown caller cannot bypass a known target. */
        if(row&&!error){
            int cleanup=destroying&&eligible&&host_memory_cleanup_scope(entry,state);
            if(ambiguous||__atomic_load_n(&row->uncertain,__ATOMIC_ACQUIRE)||unknown||!captured)error=ERROR_INVALID_DATA;
            else if(!eligible||!host_crypto_same_sources(row->owners,owners))error=ERROR_ACCESS_DENIED;
            else if((native_owner_stopped(owners)||native_owner_stopped(row->owners)
                    ||native_owner_stopped(row->contributors))&&!cleanup)error=ERROR_ACCESS_DENIED;
            else if(row->closing||destroying&&row->uses||row->hash&&row->uses)error=ERROR_BUSY;
            if(!error){row->uses++;held=1;if(destroying)row->closing=1;}
        }
        ReleaseSRWLockExclusive(&host_crypto_records);
        }
    }
    if(!error&&native_owner_stopped(owners)&&!(row&&destroying&&eligible&&host_memory_cleanup_scope(entry,state)))error=ERROR_ACCESS_DENIED;
    if(!error&&unknown&&(native_has_producer(owners)||native_has_core_state(owners)))error=ERROR_INVALID_DATA;
    __try {
        /* Keep actual caller pointers and flags, including invalid/NULL input,
         * so the original provider owns parameter validation and failure writes.
         * Only writes to Ronova control ranges are refused before the operation. */
        if(!error&&(opening||creating)){
            const void *output=(const void*)a[creating?1:0];
            if(output&&host_memory_range(output,sizeof(void*),0,restricted))error=ERROR_ACCESS_DENIED;
        }
        if(!error&&creating&&a[2]&&(ULONG)a[3]
                &&host_memory_range((const void*)a[2],(ULONG)a[3],0,restricted))error=ERROR_ACCESS_DENIED;
        if(!error&&kind==HOST_CNG_FINISH_HASH&&a[1]&&(ULONG)a[2]
                &&host_memory_range((const void*)a[1],(ULONG)a[2],0,restricted))error=ERROR_ACCESS_DENIED;
        if(!error&&creating&&eligible&&a[2]&&(ULONG)a[3]){
            int denied=0;storage=native_memory_crypto_span(a[2],(ULONG)a[3],owners,1,&observed,&dataUnknown,&denied);
            if(denied)error=ERROR_BUSY;
        }
        if(!error&&((kind==HOST_CNG_HASH_DATA&&a[1]&&(ULONG)a[2])
                ||(creating&&a[4]&&(ULONG)a[5])||kind==HOST_CNG_FINISH_HASH&&a[1]&&(ULONG)a[2])){
            int denied=0;uint64_t address=creating?a[4]:a[1],bytes=(ULONG)a[creating?5:2];
            data=native_memory_crypto_span(address,bytes,owners,kind==HOST_CNG_FINISH_HASH?2:0,&observed,&dataUnknown,&denied);
            if(denied)error=ERROR_BUSY;
        }
        if(!error&&native_owner_stopped(observed))error=ERROR_ACCESS_DENIED;
        if(!error&&(opening||creating)&&eligible){
            created=(HostCryptoHandle*)calloc(1,sizeof(*created));
            if(created&&!native_owner_merge(&created->owners,owners)){native_owner_links_free(created->owners);free(created);created=NULL;}
        }
        if(!error){
            host_crypto_restore(incoming);called=1;status=host_crypto_original(entry,a);
            outgoing=host_crypto_errors();returned=1;
            if(status>=0&&(opening||creating)&&eligible){
                /* Read only the successful output slot. A failure grants no
                 * handle receipt, even if the caller left a previous value. */
                uintptr_t value=0;const void *output=(const void*)a[creating?1:0];SIZE_T copied=0;
                int published=0,read=output&&host_real_read_process_memory(GetCurrentProcess(),output,&value,sizeof(value),&copied)&&copied==sizeof(value)&&value;
                if(created&&read){
                    created->value=value;created->provider=provider;created->hash=creating;created->live=1;
                    created->parent=creating?row:NULL;created->parentGeneration=row?row->generation:0;
                    created->flags=opening?(ULONG)a[3]:(ULONG)a[6]|(row?row->flags&BCRYPT_HASH_REUSABLE_FLAG:0);
                    created->storage=storage;storage=NULL;created->dataUnknown=dataUnknown;
                    created->contributors=observed;observed=NULL;
                    if(host_crypto_lock(restricted)){
                    HostCryptoHandle *previous=host_crypto_find(value,creating,provider,&ambiguous);
                    if(host_crypto_generation!=UINT64_MAX){
                        created->generation=++host_crypto_generation;
                        if(previous){__atomic_store_n(&previous->uncertain,1,__ATOMIC_RELEASE);created->uncertain=1;}
                        created->next=host_crypto_handles;host_crypto_handles=created;created=NULL;published=1;
                    }
                    ReleaseSRWLockExclusive(&host_crypto_records);
                    }
                }
                if(!published)native_memory_crypto_unobserved(owners,restricted);
                if(creating&&storage){
                    /* The real hash exists even if its receipt cannot be
                     * published. Retain external storage as unresolved. */
                    native_memory_crypto_unobserved(owners,restricted);storage=NULL;
                }
            }
        }else status=host_crypto_refusal(error);
    } __finally {
        if(called&&!returned)outgoing=host_crypto_errors();
        NativeHostCryptoSpan *released=NULL;
        if(held){
            if(host_crypto_lock(restricted)){
            if(called&&!returned)__atomic_store_n(&row->uncertain,1,__ATOMIC_RELEASE);
            if(returned&&status>=0){
                if(destroying){row->live=0;released=row->storage;row->storage=NULL;}
                else if(kind==HOST_CNG_FINISH_HASH)row->finished=1;
                if(!destroying&&!native_owner_merge(&row->contributors,observed))row->dataUnknown=1;
                row->dataUnknown|=dataUnknown;
            }
            if(row->uses)row->uses--;if(destroying)row->closing=0;
            ReleaseSRWLockExclusive(&host_crypto_records);
            }else {
                /* Never wait for this directory under a loader callback. The
                 * exact row keeps its hold and storage as unresolved, even if
                 * the original destructive operation has already returned. */
                __atomic_store_n(&row->uncertain,1,__ATOMIC_RELEASE);
                native_memory_crypto_unobserved(owners,restricted);
            }
        }
        /* A provider exception may have retained pbHashObject. Keep its exact
         * live lease unresolved; do not free it or invent an unreturned hash. */
        if(storage&&called&&!returned){native_memory_crypto_unobserved(owners,restricted);storage=NULL;}
        native_memory_crypto_span_end(released);native_memory_crypto_span_end(storage);native_memory_crypto_span_end(data);
        if(created){
            if(created->storage){native_memory_crypto_unobserved(created->owners,restricted);created->storage=NULL;}
            native_owner_links_free(created->owners);native_owner_links_free(created->contributors);free(created);
        }
        if(dataUnknown&&eligible)native_memory_crypto_unobserved(owners,restricted);
        native_owner_links_free(observed);native_owner_links_free(owners);host_crypto_restore(outgoing);
    }
    return (uint32_t)status;
}
