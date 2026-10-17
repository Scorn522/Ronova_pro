/* Actual KTM/TxF handles of an observed caller. GUIDs join transaction
 * observations; a numeric HANDLE alone never identifies a kernel object.
 * Witnesses and call-local duplicates retain the original access mask. They
 * authorize neither a commit nor a rollback, and no recovery replays either.
 * https://learn.microsoft.com/en-us/windows/win32/api/ktmw32/nf-ktmw32-createtransaction
 * https://learn.microsoft.com/en-us/windows/win32/api/ktmw32/nf-ktmw32-opentransaction
 * https://learn.microsoft.com/en-us/windows/win32/api/ktmw32/nf-ktmw32-gettransactioninformation
 * https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-createfiletransactedw
 * https://learn.microsoft.com/en-us/windows/win32/api/handleapi/nf-handleapi-duplicatehandle
 */
#include <ktmw32.h>

typedef struct HostTransactionErrors {DWORD windows;int crt;unsigned long dos;} HostTransactionErrors;
static HostTransactionErrors host_transaction_errors(void){
    HostTransactionErrors value;value.windows=GetLastError();value.crt=errno;value.dos=_doserrno;return value;
}
static void host_transaction_restore(HostTransactionErrors value){_doserrno=value.dos;errno=value.crt;SetLastError(value.windows);}
static int host_is_transaction(int kind){return kind>=HOST_TX_CREATE&&kind<=HOST_TX_FILE_A;}
static int host_transaction_restricted(void){
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    return host_loader_callout()||(state&&(state->heapCallback||state->toolingRestricted||state->vmDetaching||state->vmThreadEnding
            ||state->hostUnloadImage||state->hostRelease||state->hostLoad&&state->hostLoad->calling));
}
typedef struct HostTransactionGroup {
    GUID id;int identified,terminal,pending;volatile LONG uncertain;unsigned uses,pins;
    OwnerLink *owners,*contributors;
    struct HostTransactionGroup *parent,*next;
} HostTransactionGroup;
typedef struct HostTransactionObject HostTransactionObject;
typedef struct HostTransactionAlias {
    HANDLE value;int live,closing;DWORD flags;OwnerLink *owners;
    HostTransactionObject *object;struct HostTransactionAlias *next;
} HostTransactionAlias;
struct HostTransactionObject {
    uint64_t generation;HANDLE witness;int file,closing;volatile LONG uncertain;
    unsigned uses,pins;OwnerLink *owners,*contributors;HostTransactionGroup *group;
    struct HostTransactionObject *transaction;uint64_t transactionGeneration;
    HostTransactionAlias *aliases;struct HostTransactionObject *next;
};
typedef struct HostTransactionUse {
    HostTransactionObject *object;HostTransactionAlias *alias;HostTransactionGroup *group;
    HANDLE stable;int pin,active,closing,last,uncertain;
    struct HostTransactionUse *next;
} HostTransactionUse;
static SRWLOCK host_transaction_records=SRWLOCK_INIT;
static HostTransactionObject *host_transaction_objects;
static HostTransactionGroup *host_transaction_groups;
static HostTransactionUse *host_transaction_uses;
static uint64_t host_transaction_generation;
static int host_transaction_lock(int restricted){
    return restricted?TryAcquireSRWLockExclusive(&host_transaction_records):(AcquireSRWLockExclusive(&host_transaction_records),1);
}
static void host_transaction_gap(OwnerLink *owners,int restricted){native_memory_crypto_unobserved(owners,restricted);}
static HostTransactionGroup *host_transaction_group(HostTransactionGroup *group){
    while(group&&group->parent)group=group->parent;return group;
}
static HostTransactionGroup *host_transaction_guid(const GUID *id){
    for(HostTransactionGroup *group=host_transaction_groups;group;group=group->next)
        if(!group->parent&&group->identified&&!memcmp(&group->id,id,sizeof(*id)))return group;
    return NULL;
}
static int host_transaction_same_sources(OwnerLink *first,OwnerLink *second){
    return first&&second&&host_owners_contain(first,second)&&host_owners_contain(second,first);
}
static HostTransactionAlias *host_transaction_alias(HANDLE value){
    for(HostTransactionObject *row=host_transaction_objects;row;row=row->next)
        for(HostTransactionAlias *alias=row->aliases;alias;alias=alias->next)
            if(alias->live&&alias->value==value)return alias;
    return NULL;
}
static int host_transaction_private(HANDLE value){
    if(!value||value==INVALID_HANDLE_VALUE)return 0;
    for(HostTransactionObject *row=host_transaction_objects;row;row=row->next)
        if(__atomic_load_n(&row->witness,__ATOMIC_ACQUIRE)==value)return 1;
    for(HostTransactionUse *use=host_transaction_uses;use;use=use->next)
        if(__atomic_load_n(&use->stable,__ATOMIC_ACQUIRE)==value)return 1;
    return 0;
}
static int host_transaction_aliases(HostTransactionObject *row){
    for(HostTransactionAlias *alias=row->aliases;alias;alias=alias->next)if(alias->live)return 1;return 0;
}
static int host_transaction_read(const void *source,void *target,SIZE_T bytes){
    SIZE_T copied=0;return source&&host_real_read_process_memory(GetCurrentProcess(),source,target,bytes,&copied)&&copied==bytes;
}
static void host_transaction_merge_group(HostTransactionGroup *group,const GUID *id){
    group=host_transaction_group(group);if(!group)return;
    if(group->identified&&memcmp(&group->id,id,sizeof(*id))){InterlockedExchange(&group->uncertain,1);return;}
    HostTransactionGroup *other=host_transaction_guid(id);
    if(!other||other==group){group->id=*id;group->identified=1;return;}
    if(!native_owner_merge(&other->owners,group->owners)||!native_owner_merge(&other->contributors,group->contributors))
        InterlockedExchange(&other->uncertain,1);
    if(group->terminal&&other->terminal&&group->terminal!=other->terminal)InterlockedExchange(&other->uncertain,1);
    if(!other->terminal)other->terminal=group->terminal;
    if(other->uses||other->pins)InterlockedExchange(&other->uncertain,1);
    other->pending|=group->pending;other->uses+=group->uses;other->pins+=group->pins;
    if(group->uncertain)InterlockedExchange(&other->uncertain,1);
    group->uses=group->pins=0;group->parent=other;
}
static int host_transaction_use_release(HostTransactionUse *use,OwnerLink *contributors,int uncertain,int completed){
    if(!use)return 1;HostTransactionErrors saved=host_transaction_errors();int restricted=completed||host_transaction_restricted();
    if(completed&&use->uncertain){host_transaction_restore(saved);return 0;}
    if(uncertain){
        use->uncertain=1;
        if(use->object)InterlockedExchange(&use->object->uncertain,1);
        if(host_transaction_lock(restricted)){
            HostTransactionGroup *group=host_transaction_group(use->group);if(group)InterlockedExchange(&group->uncertain,1);
            if(group&&!native_owner_merge(&group->contributors,contributors))host_transaction_gap(NULL,1);
            ReleaseSRWLockExclusive(&host_transaction_records);
        }
        host_transaction_gap(contributors,restricted);host_transaction_restore(saved);return 0;
    }
    HANDLE stable=__atomic_load_n(&use->stable,__ATOMIC_ACQUIRE);
    if(stable&&!host_real_close(stable)){
        use->uncertain=1;if(use->object)InterlockedExchange(&use->object->uncertain,1);
        host_transaction_gap(contributors,restricted);host_transaction_restore(saved);return 0;
    }
    __atomic_store_n(&use->stable,NULL,__ATOMIC_RELEASE);
    if(!host_transaction_lock(restricted)){
        if(!completed){use->uncertain=1;if(use->object)InterlockedExchange(&use->object->uncertain,1);host_transaction_gap(contributors,restricted);}
        host_transaction_restore(saved);return 0;
    }
    HostTransactionGroup *group=host_transaction_group(use->group);
    if(use->object&&!native_owner_merge(&use->object->contributors,contributors))InterlockedExchange(&use->object->uncertain,1);
    if(group&&!native_owner_merge(&group->contributors,contributors))InterlockedExchange(&group->uncertain,1);
    if(use->pin){if(use->object&&use->object->pins)use->object->pins--;if(group&&group->pins)group->pins--;}
    if(use->active){if(use->object&&use->object->uses)use->object->uses--;if(group&&group->uses)group->uses--;}
    if(use->closing){if(use->alias)use->alias->closing=0;if(use->object)use->object->closing=0;}
    HostTransactionUse **link=&host_transaction_uses;while(*link&&*link!=use)link=&(*link)->next;if(*link)*link=use->next;
    ReleaseSRWLockExclusive(&host_transaction_records);free(use);host_transaction_restore(saved);return 1;
}
static void host_transaction_use_end(HostTransactionUse *use,OwnerLink *contributors,int uncertain){
    (void)host_transaction_use_release(use,contributors,uncertain,0);
}
static int host_transaction_sources(HostImport *entry,void *caller,OwnerLink **owners,int *unknown){
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    return host_entry_sources_query(entry,state,caller,owners,NULL,1,unknown);
}
/* Unknown handles remain uncovered. A registered alias is first compared with
 * its independent witness, before applying its old source policy to a reused
 * number. Close/reuse outside observed imports is a lifetime gap, not a right
 * to close the replacement object. */
static int host_transaction_use_begin(HostImport *entry,void *caller,HANDLE value,int type,
        OwnerLink **owners,int *unknown,HostTransactionUse **hold,DWORD *error){
    *hold=NULL;*error=0;int restricted=host_transaction_restricted();
    if(!host_transaction_lock(restricted)){*error=ERROR_BUSY;return 0;}
    if(host_transaction_private(value)){ReleaseSRWLockExclusive(&host_transaction_records);*error=ERROR_ACCESS_DENIED;return 0;}
    HostTransactionAlias *alias=host_transaction_alias(value);
    if(!alias){ReleaseSRWLockExclusive(&host_transaction_records);return 1;}
    HostTransactionObject *row=alias->object;HostTransactionGroup *group=host_transaction_group(row->group);
    if(alias->closing||row->closing||!row->witness){ReleaseSRWLockExclusive(&host_transaction_records);*error=ERROR_BUSY;return 0;}
    HostTransactionUse *use=(HostTransactionUse*)calloc(1,sizeof(*use));
    if(!use){ReleaseSRWLockExclusive(&host_transaction_records);*error=ERROR_NOT_ENOUGH_MEMORY;return 0;}
    use->object=row;use->alias=alias;use->group=group;use->pin=1;row->pins++;if(group)group->pins++;
    use->next=host_transaction_uses;host_transaction_uses=use;HANDLE witness=row->witness;
    ReleaseSRWLockExclusive(&host_transaction_records);
    HANDLE stable=NULL;
    if(!host_real_duplicate(GetCurrentProcess(),value,GetCurrentProcess(),&stable,0,FALSE,DUPLICATE_SAME_ACCESS)){
        *error=GetLastError();if(!*error)*error=ERROR_INVALID_HANDLE;host_transaction_use_end(use,NULL,0);return 0;
    }
    __atomic_store_n(&use->stable,stable,__ATOMIC_RELEASE);
    HostCompareHandles compare=__atomic_load_n(&host_compare_handles,__ATOMIC_ACQUIRE);
    SetLastError(ERROR_SUCCESS);int same=compare&&compare(stable,witness);DWORD compared=GetLastError();
    if(!same){
        if(!compare){*error=ERROR_NOT_SUPPORTED;host_transaction_use_end(use,NULL,0);return 0;}
        if(compared!=ERROR_NOT_SAME_OBJECT){*error=compared?compared:ERROR_INVALID_DATA;host_transaction_use_end(use,NULL,0);return 0;}
        HANDLE retired=NULL;
        if(host_transaction_lock(restricted)){
            alias->live=0;InterlockedExchange(&row->uncertain,1);
            if(!host_transaction_aliases(row)&&row->pins==1&&!row->uses){retired=row->witness;row->closing=1;use->closing=1;}
            ReleaseSRWLockExclusive(&host_transaction_records);
        }else InterlockedExchange(&row->uncertain,1);
        if(retired){if(host_real_close(retired))__atomic_store_n(&row->witness,NULL,__ATOMIC_RELEASE);else host_transaction_gap(row->owners,restricted);}
        host_transaction_gap(row->owners,restricted);host_transaction_use_end(use,NULL,0);return 1;
    }
    if(!host_transaction_sources(entry,caller,owners,unknown)){
        *error=GetLastError();if(!*error)*error=ERROR_INVALID_DATA;host_transaction_use_end(use,NULL,0);return 0;
    }
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    int eligible=native_state_caller_eligible(state,caller,*owners,*unknown);
    int completion=entry->kind>=HOST_TX_OVERLAPPED&&entry->kind<=HOST_TX_CANCEL_IO;
    int cleanup=(entry->kind==HOST_CLOSE||entry->kind==HOST_TX_ROLLBACK)&&eligible&&host_memory_cleanup_scope(entry,state);
    if(!host_transaction_lock(restricted)){*error=ERROR_BUSY;host_transaction_use_end(use,NULL,0);return 0;}
    group=host_transaction_group(use->group);
    if(*unknown||!eligible||!host_transaction_same_sources(row->owners,*owners)) *error=ERROR_ACCESS_DENIED;
    else if(type>=0&&row->file!=type)*error=ERROR_INVALID_HANDLE;
    else if(row->file&&(!row->transaction||row->transaction->generation!=row->transactionGeneration))*error=ERROR_INVALID_DATA;
    else if(row->uncertain||group&&group->uncertain)*error=ERROR_INVALID_DATA;
    else if((native_owner_stopped(*owners)||native_owner_stopped(row->owners)||native_owner_stopped(row->contributors)
            ||group&&(native_owner_stopped(group->owners)||native_owner_stopped(group->contributors)))&&!cleanup)*error=ERROR_ACCESS_DENIED;
    else if(!completion&&(row->uses||row->pins!=1||group&&(group->uses||group->pins!=1)))*error=ERROR_BUSY;
    else if(row->file&&entry->kind!=HOST_CLOSE&&entry->kind!=HOST_DUPLICATE&&entry->kind!=HOST_HANDLE_FLAGS
            &&!completion&&group&&(group->terminal||group->pending))*error=ERROR_TRANSACTION_NOT_ACTIVE;
    else if(!row->file&&group&&group->pending&&entry->kind!=HOST_TX_INFO&&entry->kind!=HOST_TX_ID
            &&entry->kind!=HOST_CLOSE&&entry->kind!=HOST_DUPLICATE&&entry->kind!=HOST_HANDLE_FLAGS)*error=ERROR_BUSY;
    else if((entry->kind==HOST_TX_FILE_W||entry->kind==HOST_TX_FILE_A)&&group&&group->terminal)*error=ERROR_TRANSACTION_NOT_ACTIVE;
    if(!*error){row->pins--;row->uses++;if(group){group->pins--;group->uses++;}use->pin=0;use->active=1;*hold=use;}
    ReleaseSRWLockExclusive(&host_transaction_records);
    if(*error){host_transaction_use_end(use,NULL,0);return 0;}return 1;
}
static int host_transaction_io_begin(HostImport *entry,void *caller,HANDLE value,OwnerLink **owners,int *unknown,
        HostTransactionUse **hold,DWORD *error){return host_transaction_use_begin(entry,caller,value,1,owners,unknown,hold,error);}
static HANDLE host_transaction_io_handle(HostTransactionUse *hold,HANDLE original){return hold?hold->stable:original;}
static void host_transaction_io_end(HostTransactionUse *hold,OwnerLink *contributors,int uncertain){host_transaction_use_end(hold,contributors,uncertain);}
static int host_transaction_io_release(HostTransactionUse *hold,OwnerLink *contributors){return host_transaction_use_release(hold,contributors,0,1);}
static void *host_transaction_file_identity(HostTransactionUse *hold,uint64_t *generation){
    if(generation)*generation=0;
    if(!hold||!hold->active||!hold->object||!hold->object->file)return NULL;
    if(generation)*generation=hold->object->generation;return hold->object;
}

/* A read-only real query uses the same-access witness, never an escalated
 * handle. An Open with insufficient query access still contributes its actual
 * successfully used GUID argument; a later real GetTransactionId checks it. */
static int host_transaction_actual_id(HANDLE value,GUID *id){
    void *actual=__atomic_load_n(&host_windows_api[HOST_TX_ID],__ATOMIC_ACQUIRE);
    return actual&&((BOOL (WINAPI*)(HANDLE,LPGUID))actual)(value,id);
}
static int host_transaction_register(HANDLE value,int file,HostTransactionObject *transaction,const GUID *opened,
        OwnerLink *owners,int restricted){
    HostTransactionObject *row=(HostTransactionObject*)calloc(1,sizeof(*row));
    HostTransactionAlias *alias=(HostTransactionAlias*)calloc(1,sizeof(*alias));
    HostTransactionGroup *prepared=(HostTransactionGroup*)calloc(1,sizeof(*prepared));
    int ready=row&&alias&&prepared;
    if(ready)ready=native_owner_merge(&row->owners,owners)&&native_owner_merge(&alias->owners,owners)
            &&native_owner_merge(&prepared->owners,owners);
    HANDLE witness=NULL;
    if(ready)ready=host_real_duplicate(GetCurrentProcess(),value,GetCurrentProcess(),&witness,0,FALSE,DUPLICATE_SAME_ACCESS);
    GUID id={0};int identified=0,mismatch=0;
    if(ready&&!file){
        identified=host_transaction_actual_id(witness,&id);
        if(identified&&opened&&memcmp(&id,opened,sizeof(id)))mismatch=1;
        if(!identified&&opened){id=*opened;identified=1;}
    }
    DWORD flags=0;if(ready&&!GetHandleInformation(value,&flags))mismatch=1;
    if(ready)ready=host_transaction_lock(restricted);
    if(ready){
        if(host_transaction_generation==UINT64_MAX)ready=0;
        else {
            HostTransactionGroup *group=transaction?host_transaction_group(transaction->group):NULL;
            if(!group&&identified)group=host_transaction_guid(&id);
            if(!group){group=prepared;prepared=NULL;group->identified=identified;group->id=id;
                group->next=host_transaction_groups;host_transaction_groups=group;}
            if(!native_owner_merge(&group->owners,owners))InterlockedExchange(&group->uncertain,1);
            row->generation=++host_transaction_generation;row->witness=witness;witness=NULL;
            row->file=file;row->group=group;row->aliases=alias;
            row->transaction=transaction;row->transactionGeneration=transaction?transaction->generation:0;
            alias->value=value;alias->live=1;alias->flags=flags;alias->object=row;
            HostTransactionAlias *previous=host_transaction_alias(value);
            if(previous){InterlockedExchange(&previous->object->uncertain,1);row->uncertain=1;}
            if(mismatch)row->uncertain=group->uncertain=1;
            row->next=host_transaction_objects;host_transaction_objects=row;row=NULL;alias=NULL;
        }
        ReleaseSRWLockExclusive(&host_transaction_records);
    }
    if(witness&&!host_real_close(witness)){InterlockedIncrement(&native_failures);host_transaction_gap(owners,restricted);}
    if(row){native_owner_links_free(row->owners);free(row);}
    if(alias){native_owner_links_free(alias->owners);free(alias);}
    if(prepared){native_owner_links_free(prepared->owners);free(prepared);}
    if(!ready||mismatch)host_transaction_gap(owners,restricted);return ready;
}
static int host_transaction_open_group(HostImport *entry,void *caller,const GUID *id,OwnerLink *owners,int unknown,
        HostTransactionUse **hold,DWORD *error){
    int restricted=host_transaction_restricted();
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    int eligible=native_state_caller_eligible(state,caller,owners,unknown);
    if(!host_transaction_lock(restricted)){*error=ERROR_BUSY;return 0;}
    HostTransactionGroup *group=host_transaction_guid(id);
    if(group){
        if(!eligible||unknown||!host_transaction_same_sources(group->owners,owners))*error=ERROR_ACCESS_DENIED;
        else if(group->uncertain)*error=ERROR_INVALID_DATA;
        else if(native_owner_stopped(owners)||native_owner_stopped(group->owners)||native_owner_stopped(group->contributors))*error=ERROR_ACCESS_DENIED;
        else if(group->uses||group->pins)*error=ERROR_BUSY;
        else {
            HostTransactionUse *use=(HostTransactionUse*)calloc(1,sizeof(*use));
            if(!use)*error=ERROR_NOT_ENOUGH_MEMORY;
            else {use->group=group;use->active=1;group->uses++;use->next=host_transaction_uses;host_transaction_uses=use;*hold=use;}
        }
    }
    ReleaseSRWLockExclusive(&host_transaction_records);return !*error;
}
static uint64_t host_transaction_original(HostImport *entry,uintptr_t *a){
    switch(entry->kind){
        case HOST_TX_CREATE:return (uintptr_t)((HANDLE (WINAPI*)(LPSECURITY_ATTRIBUTES,LPGUID,DWORD,DWORD,DWORD,DWORD,LPWSTR))entry->original)
                ((LPSECURITY_ATTRIBUTES)a[0],(LPGUID)a[1],(DWORD)a[2],(DWORD)a[3],(DWORD)a[4],(DWORD)a[5],(LPWSTR)a[6]);
        case HOST_TX_OPEN:return (uintptr_t)((HANDLE (WINAPI*)(DWORD,LPGUID))entry->original)((DWORD)a[0],(LPGUID)a[1]);
        case HOST_TX_COMMIT:case HOST_TX_ROLLBACK:case HOST_TX_COMMIT_ASYNC:case HOST_TX_ROLLBACK_ASYNC:
            return ((BOOL (WINAPI*)(HANDLE))entry->original)((HANDLE)a[0]);
        case HOST_TX_ID:return ((BOOL (WINAPI*)(HANDLE,LPGUID))entry->original)((HANDLE)a[0],(LPGUID)a[1]);
        case HOST_TX_INFO:return ((BOOL (WINAPI*)(HANDLE,PDWORD,PDWORD,PDWORD,PDWORD,DWORD,LPWSTR))entry->original)
                ((HANDLE)a[0],(PDWORD)a[1],(PDWORD)a[2],(PDWORD)a[3],(PDWORD)a[4],(DWORD)a[5],(LPWSTR)a[6]);
        case HOST_TX_SET_INFO:return ((BOOL (WINAPI*)(HANDLE,DWORD,DWORD,DWORD,LPWSTR))entry->original)
                ((HANDLE)a[0],(DWORD)a[1],(DWORD)a[2],(DWORD)a[3],(LPWSTR)a[4]);
        case HOST_TX_FILE_W:return (uintptr_t)((HANDLE (WINAPI*)(LPCWSTR,DWORD,DWORD,LPSECURITY_ATTRIBUTES,DWORD,DWORD,HANDLE,HANDLE,PUSHORT,PVOID))entry->original)
                ((LPCWSTR)a[0],(DWORD)a[1],(DWORD)a[2],(LPSECURITY_ATTRIBUTES)a[3],(DWORD)a[4],(DWORD)a[5],(HANDLE)a[6],(HANDLE)a[7],(PUSHORT)a[8],(PVOID)a[9]);
        case HOST_TX_FILE_A:return (uintptr_t)((HANDLE (WINAPI*)(LPCSTR,DWORD,DWORD,LPSECURITY_ATTRIBUTES,DWORD,DWORD,HANDLE,HANDLE,PUSHORT,PVOID))entry->original)
                ((LPCSTR)a[0],(DWORD)a[1],(DWORD)a[2],(LPSECURITY_ATTRIBUTES)a[3],(DWORD)a[4],(DWORD)a[5],(HANDLE)a[6],(HANDLE)a[7],(PUSHORT)a[8],(PVOID)a[9]);
    }
    SetLastError(ERROR_INVALID_FUNCTION);return 0;
}
static uint64_t host_native_transaction(HostImport *entry,void *caller,uintptr_t *arguments){
    HostTransactionErrors incoming=host_transaction_errors(),outgoing=incoming;
    int restricted=host_transaction_restricted();
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    if(!restricted)entry=host_consumed_entry(entry,state,caller);
    int kind=entry->kind,opening=kind==HOST_TX_OPEN,creating=kind==HOST_TX_CREATE;
    int file=kind==HOST_TX_FILE_W||kind==HOST_TX_FILE_A,returnsHandle=opening||creating||file;
    uint64_t result=returnsHandle?(uintptr_t)INVALID_HANDLE_VALUE:FALSE;
    OwnerLink *owners=NULL;HostTransactionUse *use=NULL;int unknown=0,eligible=0,called=0,returned=0;
    DWORD error=0;GUID opened={0};int haveOpened=0;
    uintptr_t a[10]={0};unsigned count=file?10:creating||kind==HOST_TX_INFO?7:kind==HOST_TX_SET_INFO?5:opening||kind==HOST_TX_ID?2:1;
    memcpy(a,arguments,count*sizeof(*a));
    if(entry->original!=__atomic_load_n(&host_windows_api[kind],__ATOMIC_ACQUIRE))error=ERROR_INVALID_DATA;
    if(!error&&!creating&&!opening)host_transaction_use_begin(entry,caller,(HANDLE)a[file?7:0],0,&owners,&unknown,&use,&error);
    if(!error&&!owners&&!host_transaction_sources(entry,caller,&owners,&unknown)){error=GetLastError();if(!error)error=ERROR_INVALID_DATA;}
    eligible=!error&&native_state_caller_eligible(state,caller,owners,unknown);
    if(!error&&opening){
        haveOpened=host_transaction_read((const void*)a[1],&opened,sizeof(opened));
        if(!haveOpened)error=ERROR_NOACCESS;
        else {
            host_transaction_open_group(entry,caller,&opened,owners,unknown,&use,&error);
            /* The original consumes exactly the GUID whose target admission
             * succeeded. A caller cannot redirect this Open by rewriting its
             * mutable argument between admission and the kernel operation. */
            a[1]=(uintptr_t)&opened;
        }
    }
    int cleanup=use&&kind==HOST_TX_ROLLBACK&&eligible&&host_memory_cleanup_scope(entry,state);
    if(!error&&native_owner_stopped(owners)&&!cleanup)error=ERROR_ACCESS_DENIED;
    if(!error&&unknown&&(native_has_producer(owners)||native_has_core_state(owners)))error=ERROR_INVALID_DATA;
    __try {
        if(!error&&kind==HOST_TX_ID&&a[1]&&host_memory_range((const void*)a[1],sizeof(GUID),0,restricted))error=ERROR_ACCESS_DENIED;
        if(!error&&kind==HOST_TX_INFO){
            for(int i=1;i<=4&&!error;i++)if(a[i]&&host_memory_range((const void*)a[i],sizeof(DWORD),0,restricted))error=ERROR_ACCESS_DENIED;
            if(!error&&a[6]&&(DWORD)a[5]&&host_memory_range((const void*)a[6],(DWORD)a[5],0,restricted))error=ERROR_ACCESS_DENIED;
        }
        if(!error){
            if(use&&use->object)a[file?7:0]=(uintptr_t)use->stable;
            host_transaction_restore(incoming);called=1;result=host_transaction_original(entry,a);
            outgoing=host_transaction_errors();returned=1;
            int success=returnsHandle?(HANDLE)(uintptr_t)result!=INVALID_HANDLE_VALUE&&(HANDLE)(uintptr_t)result!=NULL:result!=0;
            if(success&&returnsHandle){
                const GUID *observed=opening&&haveOpened?&opened:NULL;
                if(eligible&&(!file||use))host_transaction_register((HANDLE)(uintptr_t)result,file,file&&use?use->object:NULL,observed,owners,restricted);
                else host_transaction_gap(owners,restricted);
            }
            if(use){
                GUID id={0};DWORD outcome=0;
                int haveId=kind==HOST_TX_ID&&success&&host_transaction_read((const void*)arguments[1],&id,sizeof(id));
                int haveOutcome=kind==HOST_TX_INFO&&success&&arguments[1]&&host_transaction_read((const void*)arguments[1],&outcome,sizeof(outcome));
                if(host_transaction_lock(restricted)){
                    HostTransactionGroup *group=host_transaction_group(use->group);
                    if(group){
                        if(haveId)host_transaction_merge_group(group,&id);
                        group=host_transaction_group(group);
                        if(kind==HOST_TX_COMMIT||kind==HOST_TX_ROLLBACK){
                            if(success){group->terminal=kind==HOST_TX_COMMIT?TransactionOutcomeCommitted:TransactionOutcomeAborted;group->pending=0;}
                            else group->pending=1;
                        }else if(kind==HOST_TX_COMMIT_ASYNC||kind==HOST_TX_ROLLBACK_ASYNC)
                            group->pending|=success||outgoing.windows==ERROR_IO_PENDING?2:1;
                        else if(haveOutcome){
                            if(outcome==TransactionOutcomeCommitted||outcome==TransactionOutcomeAborted){group->terminal=(int)outcome;group->pending=0;}
                            // Undetermined can reconcile a returned synchronous
                            // failure, but cannot complete an asynchronous request.
                            else if(outcome==TransactionOutcomeUndetermined){group->terminal=0;group->pending&=2;}
                            else {group->pending=1;InterlockedExchange(&group->uncertain,1);}
                        }
                        if(kind==HOST_TX_ID&&success&&!haveId)InterlockedExchange(&group->uncertain,1);
                    }
                    ReleaseSRWLockExclusive(&host_transaction_records);
                }else {if(use->object)InterlockedExchange(&use->object->uncertain,1);use->uncertain=1;host_transaction_gap(owners,restricted);}
            }
        }else {outgoing=incoming;outgoing.windows=error;}
    } __finally {
        if(called&&!returned){outgoing=host_transaction_errors();host_transaction_gap(owners,restricted);}
        host_transaction_use_end(use,called?owners:NULL,called&&!returned||use&&use->uncertain);
        native_owner_links_free(owners);host_transaction_restore(outgoing);
    }
    return result;
}

/* The application operation always uses its original source slot. A private
 * same-access duplicate stabilizes identity, but is never substituted for a
 * CloseHandle/DUPLICATE_CLOSE_SOURCE operation (which must consume that slot).
 * The ledger lock never crosses any original Win32 call. */
static int host_transaction_handle(HostImport *entry,void *caller,uintptr_t *arguments,uint64_t *result){
    if(entry->kind!=HOST_CLOSE&&entry->kind!=HOST_DUPLICATE&&entry->kind!=HOST_HANDLE_FLAGS)return 0;
    HostTransactionErrors incoming=host_transaction_errors(),outgoing=incoming;
    int kind=entry->kind,duplicate=kind==HOST_DUPLICATE,restricted=host_transaction_restricted();
    if(duplicate&&(HANDLE)arguments[0]!=GetCurrentProcess()&&!host_local_process((HANDLE)arguments[0])){
        host_transaction_restore(incoming);return 0;
    }
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    if(!restricted)entry=host_consumed_entry(entry,state,caller);
    OwnerLink *owners=NULL;int unknown=0;HostTransactionUse *use=NULL;DWORD error=0;
    HANDLE value=(HANDLE)arguments[duplicate?1:0];
    int admitted=host_transaction_use_begin(entry,caller,value,-1,&owners,&unknown,&use,&error);
    if(admitted&&!use){native_owner_links_free(owners);host_transaction_restore(incoming);return 0;}
    *result=FALSE;
    if(entry->original!=__atomic_load_n(&host_windows_api[kind],__ATOMIC_ACQUIRE))error=ERROR_INVALID_DATA;
    int consuming=kind==HOST_CLOSE||duplicate&&((DWORD)arguments[6]&DUPLICATE_CLOSE_SOURCE);
    int targetLocal=duplicate&&arguments[2]&&((HANDLE)arguments[2]==GetCurrentProcess()||host_local_process((HANDLE)arguments[2]));
    int called=0,returned=0,sourceClosed=0;HANDLE retire=NULL;HostTransactionAlias *prepared=NULL;
    if(!error&&use&&consuming){
        if(host_transaction_lock(restricted)){
            use->closing=1;use->alias->closing=1;use->object->closing=1;use->last=1;
            for(HostTransactionAlias *alias=use->object->aliases;alias;alias=alias->next)
                if(alias->live&&alias!=use->alias){use->last=0;break;}
            ReleaseSRWLockExclusive(&host_transaction_records);
        }else error=ERROR_BUSY;
    }
    __try {
        if(!error&&duplicate&&arguments[3]&&host_memory_range((const void*)arguments[3],sizeof(HANDLE),0,restricted))error=ERROR_ACCESS_DENIED;
        if(!error&&duplicate&&targetLocal&&arguments[3]){
            prepared=(HostTransactionAlias*)calloc(1,sizeof(*prepared));
            if(prepared&&!native_owner_merge(&prepared->owners,owners)){native_owner_links_free(prepared->owners);free(prepared);prepared=NULL;}
        }
        if(!error){
            host_transaction_restore(incoming);called=1;*result=host_call_handle(entry,arguments);
            outgoing=host_transaction_errors();returned=1;
            /* The documented source close occurs even on a FALSE result.
             * An exception is deliberately left unresolved, without replay. */
            sourceClosed=kind==HOST_CLOSE?*result!=0:consuming;
            HANDLE output=NULL;DWORD outputFlags=0;int outputKnown=0;
            if(duplicate&&*result&&targetLocal&&arguments[3]){
                if(host_transaction_read((const void*)arguments[3],&output,sizeof(output))&&output&&output!=INVALID_HANDLE_VALUE){
                    HostCompareHandles compare=__atomic_load_n(&host_compare_handles,__ATOMIC_ACQUIRE);
                    outputKnown=compare&&compare(output,use->stable);
                    if(outputKnown&&!GetHandleInformation(output,&outputFlags))outputKnown=0;
                }
            }
            if(host_transaction_lock(restricted)){
                if(sourceClosed)use->alias->live=0;
                if(duplicate&&*result){
                    if(outputKnown&&prepared){
                        HostTransactionAlias *previous=host_transaction_alias(output);
                        if(previous){InterlockedExchange(&previous->object->uncertain,1);InterlockedExchange(&use->object->uncertain,1);}
                        prepared->value=output;prepared->live=1;prepared->flags=outputFlags;prepared->object=use->object;
                        prepared->next=use->object->aliases;use->object->aliases=prepared;prepared=NULL;
                    }else host_transaction_gap(owners,1);
                }
                if(kind==HOST_HANDLE_FLAGS&&*result){
                    DWORD mask=(DWORD)arguments[1];use->alias->flags=(use->alias->flags&~mask)|((DWORD)arguments[2]&mask);
                }
                if(sourceClosed&&!host_transaction_aliases(use->object))retire=use->object->witness;
                ReleaseSRWLockExclusive(&host_transaction_records);
            }else {
                InterlockedExchange(&use->object->uncertain,1);use->uncertain=1;
                if(sourceClosed){__atomic_store_n(&use->alias->live,0,__ATOMIC_RELEASE);if(use->last)retire=use->object->witness;}
                host_transaction_gap(owners,restricted);
            }
            /* No app alias of this object remains. Releasing both our own
             * witness and the call duplicate in this invocation preserves KTM
             * implicit rollback and TxF last-close/delete behavior. */
            if(retire){
                if(host_real_close(retire))__atomic_store_n(&use->object->witness,NULL,__ATOMIC_RELEASE);
                else {InterlockedExchange(&use->object->uncertain,1);use->uncertain=1;host_transaction_gap(owners,restricted);}
            }
            if(kind==HOST_HANDLE_FLAGS&&*result&&((DWORD)arguments[1]&HANDLE_FLAG_INHERIT)&&((DWORD)arguments[2]&HANDLE_FLAG_INHERIT))
                host_transaction_gap(owners,restricted);
            if(duplicate&&(*result&&!outputKnown||!targetLocal&&*result))host_transaction_gap(owners,restricted);
        }else {outgoing=incoming;outgoing.windows=error;}
    } __finally {
        if(called&&!returned){outgoing=host_transaction_errors();host_transaction_gap(owners,restricted);}
        if(prepared){native_owner_links_free(prepared->owners);free(prepared);}
        /* Once Close/Duplicate actually returned, our temporary duplicate is
         * no longer pending IO. Even failed metadata publication must release
         * this reference; it must not keep a last-close transaction alive. */
        host_transaction_use_end(use,called?owners:NULL,called&&!returned);
        native_owner_links_free(owners);host_transaction_restore(outgoing);
    }
    return 1;
}
