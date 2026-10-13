/* Included beside host_thread_creation.c, before host_native_call.
 * Only actual exports of the already loaded ucrtbase/msvcrt are classified.
 * A declared import name identifies an unresolved slot, never its authority.
 * Each target retains its actual CRT implementation and errno accessors;
 * neither CRT's original function is replaced by the other CRT's address.
 *
 * https://learn.microsoft.com/en-us/cpp/c-runtime-library/reference/beginthread-beginthreadex
 * https://learn.microsoft.com/en-us/cpp/c-runtime-library/errno-doserrno-sys-errlist-and-sys-nerr
 */
#include <errno.h>

typedef void (__cdecl *HostCrtStart)(void*);
typedef unsigned (__stdcall *HostCrtStartEx)(void*);
typedef uintptr_t (__cdecl *HostCrtBeginThread)(HostCrtStart,unsigned,void*);
typedef uintptr_t (__cdecl *HostCrtBeginThreadEx)(void*,unsigned,HostCrtStartEx,void*,unsigned,unsigned*);
typedef int *(__cdecl *HostCrtErrno)(void);
typedef unsigned long *(__cdecl *HostCrtDosErrno)(void);

typedef struct HostCrtThreadTarget {
    void *original;int kind;HMODULE image,pin;
    HostCrtErrno error;HostCrtDosErrno dosError;
    struct HostCrtThreadTarget *next;
} HostCrtThreadTarget;

typedef struct HostCrtThreadErrors {
    int *error;unsigned long *dosError;
    int before;unsigned long dosBefore;DWORD windowsBefore;
} HostCrtThreadErrors;

/* Published records are immutable. Installed/cached import thunks can outlive
 * a caller image, so the exact small CRT target set remains pinned while those
 * entries can still be consumed. This does not pin an application start DLL or
 * wrap, replace, or retain its start routine/userdata.
 */
static HostCrtThreadTarget *volatile host_crt_thread_exports;

static int host_is_crt_thread_create(int kind){return kind==HOST_BEGIN_THREAD||kind==HOST_BEGIN_THREAD_EX;}

static HostCrtThreadTarget *host_crt_thread_target(void *original,int kind){
    for(HostCrtThreadTarget *entry=(HostCrtThreadTarget*)InterlockedCompareExchangePointer((void *volatile*)&host_crt_thread_exports,NULL,NULL);
            entry;entry=entry->next)
        if(entry->original==original&&(!kind||entry->kind==kind))return entry;
    return NULL;
}

static void host_crt_thread_publish(void *actual,int kind,HostGetProcAddress resolve){
    if(!actual||host_crt_thread_target(actual,kind))return;
    HMODULE image=native_image(actual),pin=NULL;if(!image)return;
    /* Resolve errors in the actual implementation image. Forwarded exports
     * must not make us write an unrelated forwarding DLL's CRT error state.
     */
    HostCrtErrno error=(HostCrtErrno)resolve(image,"_errno");
    HostCrtDosErrno dosError=(HostCrtDosErrno)resolve(image,"__doserrno");
    if(!error||!dosError||native_image((void*)error)!=image||native_image((void*)dosError)!=image)return;
    if(!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,(LPCSTR)actual,&pin)||pin!=image){
        if(pin)FreeLibrary(pin);return;
    }
    HostCrtThreadTarget *entry=(HostCrtThreadTarget*)calloc(1,sizeof(*entry));
    if(!entry){FreeLibrary(pin);return;}
    entry->original=actual;entry->kind=kind;entry->image=image;entry->pin=pin;entry->error=error;entry->dosError=dosError;
    for(;;){
        HostCrtThreadTarget *head=(HostCrtThreadTarget*)InterlockedCompareExchangePointer((void *volatile*)&host_crt_thread_exports,NULL,NULL);
        int known=0;for(HostCrtThreadTarget *old=head;old;old=old->next)
            if(old->original==actual&&old->kind==kind){known=1;break;}
        if(known){FreeLibrary(pin);free(entry);return;}
        entry->next=head;
        if(InterlockedCompareExchangePointer((void *volatile*)&host_crt_thread_exports,entry,head)==head)return;
    }
}

static void host_crt_thread_targets(HostGetProcAddress resolve){
    if(!resolve)return;DWORD incoming=GetLastError();
    const wchar_t *modules[]={L"ucrtbase.dll",L"msvcrt.dll"};
    for(unsigned i=0;i<sizeof(modules)/sizeof(modules[0]);i++){
        HMODULE module=GetModuleHandleW(modules[i]);if(!module)continue;
        /* Both actual addresses are retained even when both CRTs are loaded.
         * Symbol spelling does not establish equality of their function code.
         */
        host_crt_thread_publish((void*)resolve(module,"_beginthread"),HOST_BEGIN_THREAD,resolve);
        host_crt_thread_publish((void*)resolve(module,"_beginthreadex"),HOST_BEGIN_THREAD_EX,resolve);
    }
    SetLastError(incoming);
}

static int host_crt_thread_target_kind(void *actual){
    HostCrtThreadTarget *entry=host_crt_thread_target(actual,0);return entry?entry->kind:0;
}

static uint64_t host_crt_thread_failure(int kind){return kind==HOST_BEGIN_THREAD?(uint64_t)(uintptr_t)-1:0;}

static int host_crt_thread_errors(HostCrtThreadTarget *target,HostCrtThreadErrors *state){
    memset(state,0,sizeof(*state));state->windowsBefore=GetLastError();
    state->error=target->error();if(state->error)state->before=*state->error;
    state->dosError=target->dosError();if(state->dosError)state->dosBefore=*state->dosError;
    if(!state->error||!state->dosError){SetLastError(state->windowsBefore);return 0;}
    SetLastError(state->windowsBefore);return 1;
}

static void host_crt_thread_restore(const HostCrtThreadErrors *state,int error,unsigned long dosError,DWORD windowsError){
    *state->error=error;*state->dosError=dosError;SetLastError(windowsError);
}

static uint64_t host_crt_thread_refuse(int kind,HostCrtThreadErrors *state,DWORD windowsError){
    int error=windowsError==ERROR_NOT_ENOUGH_MEMORY||windowsError==ERROR_OUTOFMEMORY?ENOMEM:
            windowsError==ERROR_MOD_NOT_FOUND||windowsError==ERROR_FILE_NOT_FOUND?ENOENT:
            windowsError==ERROR_INVALID_DATA||windowsError==ERROR_INVALID_PARAMETER?EINVAL:EACCES;
    host_crt_thread_restore(state,error,(unsigned long)windowsError,windowsError);
    return host_crt_thread_failure(kind);
}

static uint64_t host_call_crt_thread_create(HostImport *entry,const uintptr_t *arguments){
    if(entry->kind==HOST_BEGIN_THREAD)
        return (uint64_t)((HostCrtBeginThread)entry->original)((HostCrtStart)arguments[0],(unsigned)arguments[1],(void*)arguments[2]);
    return (uint64_t)((HostCrtBeginThreadEx)entry->original)((void*)arguments[0],(unsigned)arguments[1],
            (HostCrtStartEx)arguments[2],(void*)arguments[3],(unsigned)arguments[4],(unsigned*)arguments[5]);
}

static uint64_t host_native_crt_thread_create(HostImport *entry,void *caller,uintptr_t *arguments){
    DWORD incoming=GetLastError();int kind=entry->kind;
    HostCrtThreadTarget *target=host_crt_thread_target(entry->original,kind);
    if(!target){SetLastError(ERROR_INVALID_FUNCTION);return host_crt_thread_failure(kind);}
    HostCrtThreadErrors errors;SetLastError(incoming);
    if(!host_crt_thread_errors(target,&errors)){SetLastError(ERROR_NOT_ENOUGH_MEMORY);return host_crt_thread_failure(kind);}
    OwnerLink *owners=NULL;
    NativeThread *state=native_tls==TLS_OUT_OF_INDEXES?NULL:(NativeThread*)TlsGetValue(native_tls);
    HostImport *consumed=host_consumed_entry(entry,state,caller);
    /* Promotion of a real delay slot must preserve the exact original CRT
     * target, not select the other CRT's function by its shared semantic kind.
     */
    if(consumed->kind!=kind||consumed->original!=entry->original)
        return host_crt_thread_refuse(kind,&errors,ERROR_INVALID_DATA);
    entry=consumed;
    if(!host_entry_sources(entry,state,caller,&owners,NULL,0)){
        DWORD error=GetLastError();native_owner_links_free(owners);return host_crt_thread_refuse(kind,&errors,error);
    }
    if(native_owner_stopped(owners)){
        native_owner_links_free(owners);return host_crt_thread_refuse(kind,&errors,ERROR_ACCESS_DENIED);
    }
    /* All original arguments reach the actual CRT. It owns start calling
     * convention, thread-local CRT initialization, suspended creation, exit
     * cleanup, handle lifetime, and its actual failure return/error values.
     */
    host_crt_thread_restore(&errors,errors.before,errors.dosBefore,incoming);
    uint64_t result=host_call_crt_thread_create(entry,arguments);DWORD windowsError=GetLastError();
    int crtError=*errors.error;unsigned long dosError=*errors.dosError;
    native_owner_links_free(owners);
    host_crt_thread_restore(&errors,crtError,dosError,windowsError);return result;
}

/* Statically linked CRT implementations, different CRT exports, and pointers
 * cached before this image/result was observed are outside these exact loaded
 * exports. No module-wide claim is made for those unobserved creation paths.
 * Import declarations without a supported actual target retain the existing
 * failed/delay-import gap instead of acquiring a name-based permission.
 */
