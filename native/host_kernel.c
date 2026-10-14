/* Included before host.c. Only the existing Host owner controller calls these
 * helpers. Child/file rules use exact observed objects. A separate optional
 * owner rule binds the device session's JVM process, never a Java module,
 * executable name, file path, caller-supplied PID or arbitrary process handle. */
#include <stdint.h>
#include <string.h>
#include "driver/ronova_host_protocol.h"

typedef BOOL (WINAPI *HostKernelCompareHandles)(HANDLE,HANDLE);
typedef ULONG (WINAPI *HostKernelStatusError)(LONG);
typedef struct HostKernelResult {
    DWORD error;
    LONG status;
    int response,live;
} HostKernelResult;
typedef struct HostKernelTarget {
    HANDLE witness; /* private, noninheritable duplicate owned by this client */
    uint64_t observationToken,generation,sessionId,targetId;
    RVH_SCOPE scope;
    ULONG kind,effects;
    ULONGLONG actorTargetId,volumeSerial;
    UCHAR fileId[16];
    int removed;
} HostKernelTarget;
typedef struct HostKernelSession {
    HANDLE device;
    uint64_t generation,closedGeneration,nextGeneration,nextRequest;
    int live;
    DWORD error;
    LONG status;
    int response;
    RVH_SESSION_REPLY observed;
    RVH_TARGET_REPLY ownerProtection;
} HostKernelSession;
enum {HOST_KERNEL_STATE_COUNT=17};
static SRWLOCK host_kernel_records=SRWLOCK_INIT;
static HostKernelSession host_kernel_session;
static HostKernelCompareHandles host_kernel_compare;
static HostKernelStatusError host_kernel_status_error;

static int host_kernel_report(HostKernelResult *result,DWORD error,LONG status,int response,int ready){
    host_kernel_session.error=error;host_kernel_session.status=status;host_kernel_session.response=response;
    if(result){result->error=error;result->status=status;result->response=response;result->live=host_kernel_session.live;}
    SetLastError(error);return ready;
}
static DWORD host_kernel_nt_error(LONG status){
    if(status>=0)return ERROR_SUCCESS;
    if(!host_kernel_status_error){
        HMODULE image=GetModuleHandleW(L"ntdll.dll");
        host_kernel_status_error=image?(HostKernelStatusError)GetProcAddress(image,"RtlNtStatusToDosError"):NULL;
    }
    return host_kernel_status_error?host_kernel_status_error(status):ERROR_MR_MID_NOT_FOUND;
}
static int host_kernel_identity_ready(HostKernelResult *result){
    if(!host_kernel_compare){
        HMODULE image=GetModuleHandleW(L"kernelbase.dll");
        host_kernel_compare=image?(HostKernelCompareHandles)GetProcAddress(image,"CompareObjectHandles"):NULL;
    }
    return host_kernel_compare?1:host_kernel_report(result,ERROR_PROC_NOT_FOUND,0,0,0);
}
static int host_kernel_same(HANDLE actual,HANDLE witness,HostKernelResult *result){
    if(!actual||actual==INVALID_HANDLE_VALUE||!witness||witness==INVALID_HANDLE_VALUE)
        return host_kernel_report(result,ERROR_INVALID_HANDLE,0,0,0);
    if(!host_kernel_identity_ready(result))return 0;
    SetLastError(ERROR_SUCCESS);
    if(host_kernel_compare(actual,witness))return 1;
    DWORD error=GetLastError();return host_kernel_report(result,error?error:ERROR_NOT_SAME_OBJECT,0,0,0);
}
static int host_kernel_copy(HANDLE source,HANDLE *copy,HostKernelResult *result){
    if(!source||source==INVALID_HANDLE_VALUE)return host_kernel_report(result,ERROR_INVALID_HANDLE,0,0,0);
    if(!DuplicateHandle(GetCurrentProcess(),source,GetCurrentProcess(),copy,0,FALSE,DUPLICATE_SAME_ACCESS)){
        DWORD error=GetLastError();return host_kernel_report(result,error,0,0,0);
    }
    return 1;
}
static int host_kernel_drop(void){
    host_kernel_session.live=0;
    if(!host_kernel_session.device)return 1;
    if(!CloseHandle(host_kernel_session.device))return 0;
    host_kernel_session.device=NULL;
    if(host_kernel_session.generation>host_kernel_session.closedGeneration)
        host_kernel_session.closedGeneration=host_kernel_session.generation;
    memset(&host_kernel_session.observed,0,sizeof(host_kernel_session.observed));
    memset(&host_kernel_session.ownerProtection,0,sizeof(host_kernel_session.ownerProtection));return 1;
}
static int host_kernel_invalid(HostKernelResult *result,DWORD error){
    /* A broken transport has no usable receipt. Closing our sole device handle
     * requests actual driver session cleanup; failed Close remains pending. */
    host_kernel_drop();return host_kernel_report(result,error,0,0,0);
}
static void host_kernel_request(RVH_REQUEST *request,ULONG size,int hello){
    memset(request,0,sizeof(*request));request->Magic=RVH_MAGIC;request->Version=RVH_VERSION;request->Size=size;
    request->SessionId=hello?0:host_kernel_session.observed.Header.SessionId;
    if(!++host_kernel_session.nextRequest)++host_kernel_session.nextRequest;
    request->RequestId=host_kernel_session.nextRequest;
}
static int host_kernel_io(ULONG operation,void *input,ULONG inputSize,void *output,ULONG outputSize,HostKernelResult *result){
    RVH_REQUEST *request=(RVH_REQUEST*)input;DWORD returned=0;
    memset(output,0,outputSize);
    if(!host_kernel_session.device)return host_kernel_report(result,ERROR_INVALID_HANDLE,0,0,0);
    if(!DeviceIoControl(host_kernel_session.device,operation,input,inputSize,output,outputSize,&returned,NULL)){
        DWORD error=GetLastError();return host_kernel_invalid(result,error);
    }
    RVH_RESPONSE *reply=(RVH_RESPONSE*)output;
    if(returned!=outputSize||reply->Size!=outputSize)return host_kernel_invalid(result,ERROR_BAD_LENGTH);
    if(reply->Magic!=RVH_MAGIC)return host_kernel_invalid(result,ERROR_INVALID_DATA);
    if(reply->Version!=RVH_VERSION)return host_kernel_invalid(result,ERROR_REVISION_MISMATCH);
    if(reply->RequestId!=request->RequestId||!reply->SessionId
            ||request->SessionId&&reply->SessionId!=request->SessionId)
        return host_kernel_invalid(result,ERROR_INVALID_DATA);
    return host_kernel_report(result,host_kernel_nt_error(reply->Status),reply->Status,1,reply->Status>=0);
}
static int host_kernel_session_reply(const RVH_SESSION_REPLY *reply,HostKernelResult *result){
    if(reply->Reserved||reply->MaxProcesses!=RVH_MAX_PROCESSES||reply->MaxFiles!=RVH_MAX_FILES
            ||reply->ProcessCount>reply->MaxProcesses||reply->FileCount>reply->MaxFiles
            ||reply->DriverState!=RVH_DRIVER_LOADED||!(reply->Capabilities&RVH_CAP_DEVICE_SESSION))
        return host_kernel_invalid(result,ERROR_INVALID_DATA);
    return 1;
}
static int host_kernel_open_locked(HostKernelResult *result){
    if(host_kernel_session.live)return host_kernel_report(result,ERROR_SUCCESS,0,0,1);
    if(host_kernel_session.device&&!host_kernel_drop()){
        DWORD error=GetLastError();return host_kernel_report(result,error,0,0,0);
    }
    if(host_kernel_session.nextGeneration==UINT64_MAX)return host_kernel_report(result,ERROR_ARITHMETIC_OVERFLOW,0,0,0);
    HANDLE device=CreateFileW(RVH_WIN32_DEVICE,GENERIC_READ|GENERIC_WRITE,FILE_SHARE_READ|FILE_SHARE_WRITE,
            NULL,OPEN_EXISTING,FILE_ATTRIBUTE_NORMAL,NULL);
    if(device==INVALID_HANDLE_VALUE){DWORD error=GetLastError();return host_kernel_report(result,error,0,0,0);}
    host_kernel_session.device=device;host_kernel_session.generation=++host_kernel_session.nextGeneration;
    RVH_REQUEST request;RVH_SESSION_REPLY reply;host_kernel_request(&request,sizeof(request),1);
    if(!host_kernel_io(IOCTL_RVH_HELLO,&request,sizeof(request),&reply,sizeof(reply),result)){
        DWORD error=host_kernel_session.error;LONG status=host_kernel_session.status;int response=host_kernel_session.response;
        host_kernel_drop();return host_kernel_report(result,error,status,response,0);
    }
    if(!host_kernel_session_reply(&reply,result))return 0;
    host_kernel_session.observed=reply;host_kernel_session.live=1;
    return host_kernel_report(result,ERROR_SUCCESS,reply.Header.Status,1,1);
}
static int host_kernel_open(HostKernelResult *result){
    AcquireSRWLockExclusive(&host_kernel_records);int ready=host_kernel_open_locked(result);
    ReleaseSRWLockExclusive(&host_kernel_records);return ready;
}
static int host_kernel_target_pair(HANDLE actual,uint64_t token,const HostKernelTarget *target,HostKernelResult *result){
    if(!target||!token||target->observationToken!=token||!target->witness)
        return host_kernel_report(result,ERROR_INVALID_PARAMETER,0,0,0);
    return host_kernel_same(actual,target->witness,result);
}
static int host_kernel_target_live(const HostKernelTarget *target,HostKernelResult *result){
    if(!target||!target->targetId||target->removed||!host_kernel_session.live
            ||target->generation!=host_kernel_session.generation||target->sessionId!=host_kernel_session.observed.Header.SessionId)
        return host_kernel_report(result,ERROR_INVALID_HANDLE,0,0,0);
    return 1;
}
static int host_kernel_forget_locked(HostKernelTarget *target,HostKernelResult *result){
    if(!target)return host_kernel_report(result,ERROR_INVALID_PARAMETER,0,0,0);
    if(!target->witness){
        if(target->targetId)return host_kernel_report(result,ERROR_INVALID_DATA,0,0,0);
        memset(target,0,sizeof(*target));return host_kernel_report(result,ERROR_SUCCESS,0,0,1);
    }
    if(!target->removed&&(!target->generation||target->generation>host_kernel_session.closedGeneration))
        return host_kernel_report(result,ERROR_BUSY,0,0,0);
    if(!CloseHandle(target->witness)){DWORD error=GetLastError();return host_kernel_report(result,error,0,0,0);}
    memset(target,0,sizeof(*target));return host_kernel_report(result,ERROR_SUCCESS,0,0,1);
}
static int host_kernel_forget(HostKernelTarget *target,HostKernelResult *result){
    AcquireSRWLockExclusive(&host_kernel_records);int ready=host_kernel_forget_locked(target,result);
    ReleaseSRWLockExclusive(&host_kernel_records);return ready;
}
static int host_kernel_prepare_target(HANDLE actual,HANDLE witness,uint64_t token,ULONG kind,HostKernelTarget *target,HostKernelResult *result){
    if(!target||!token)return host_kernel_report(result,ERROR_INVALID_PARAMETER,0,0,0);
    if(target->witness){
        if(target->kind!=kind)return host_kernel_report(result,ERROR_INVALID_PARAMETER,0,0,0);
        if(!host_kernel_target_pair(actual,token,target,result))return 0;
        if(!host_kernel_same(witness,target->witness,result))return 0;
    }else if(target->targetId)return host_kernel_report(result,ERROR_INVALID_DATA,0,0,0);
    if(!host_kernel_open_locked(result))return 0;
    if(target->witness&&(target->generation!=host_kernel_session.generation
            ||target->sessionId!=host_kernel_session.observed.Header.SessionId)){
        if(!host_kernel_forget_locked(target,result))return 0;
    }
    if(!target->witness){
        HANDLE copy=NULL;if(!host_kernel_copy(actual,&copy,result))return 0;
        /* Keep a failed local-copy close reviewable by its actual owner record. */
        target->witness=copy;target->observationToken=token;target->kind=kind;target->removed=1;
        target->generation=host_kernel_session.generation;target->sessionId=host_kernel_session.observed.Header.SessionId;
        target->scope.Low=token;target->scope.High=target->generation;
        if(!host_kernel_same(witness,copy,result))return 0;
    }
    return 1;
}
static int host_kernel_target_reply(const RVH_TARGET_REPLY *reply,const HostKernelTarget *target,ULONG effects,int admission,HostKernelResult *result){
    if(reply->Reserved||reply->Kind!=target->kind||!reply->TargetId
            ||target->targetId&&reply->TargetId!=target->targetId||reply->Effects!=effects
            ||reply->Scope.Low!=target->scope.Low||reply->Scope.High!=target->scope.High)
        return host_kernel_invalid(result,ERROR_INVALID_DATA);
    if(target->kind==RVH_TARGET_PROCESS){
        DWORD process=GetProcessId(target->witness);
        if(!process){DWORD error=GetLastError();return host_kernel_report(result,error,0,0,0);}
        if(reply->ObservedProcessId!=process||reply->ActorTargetId)return host_kernel_invalid(result,ERROR_INVALID_DATA);
    }else if(target->kind==RVH_TARGET_FILE){
        if(reply->ActorTargetId!=target->actorTargetId||reply->VolumeSerialNumber!=target->volumeSerial
                ||memcmp(reply->FileId,target->fileId,sizeof(target->fileId)))return host_kernel_invalid(result,ERROR_INVALID_DATA);
    }else return host_kernel_invalid(result,ERROR_INVALID_DATA);
    if(admission&&!(reply->State&RVH_TARGET_ACTIVE))
        return host_kernel_report(result,ERROR_INVALID_STATE,reply->Header.Status,1,0);
    return 1;
}
static int host_kernel_process(HANDLE monitor,uint64_t token,ULONG effects,HostKernelTarget *target,HostKernelResult *result){
    AcquireSRWLockExclusive(&host_kernel_records);int ready=0;
    if(!effects||effects&~RVH_PROCESS_ALLOWED_FLAGS){host_kernel_report(result,ERROR_INVALID_PARAMETER,0,0,0);goto finished;}
    if(!host_kernel_prepare_target(monitor,monitor,token,RVH_TARGET_PROCESS,target,result))goto finished;
    DWORD process=GetProcessId(target->witness);
    if(!process){DWORD error=GetLastError();host_kernel_report(result,error,0,0,0);goto finished;}
    if(host_kernel_compare(target->witness,GetCurrentProcess())){host_kernel_report(result,ERROR_ACCESS_DENIED,0,0,0);goto finished;}
    RVH_PROCESS_ADD_REQUEST request;memset(&request,0,sizeof(request));host_kernel_request(&request.Header,sizeof(request),0);
    request.ProcessHandle=(ULONGLONG)(uintptr_t)target->witness;request.Scope=target->scope;request.Effects=effects;
    RVH_TARGET_REPLY reply;int existing=target->targetId!=0;target->removed=0;
    if(!host_kernel_io(IOCTL_RVH_PROCESS_ADD,&request,sizeof(request),&reply,sizeof(reply),result)){
        if(!existing&&host_kernel_session.response)target->removed=1;goto finished;
    }
    if(!host_kernel_target_reply(&reply,target,effects,1,result))goto finished;
    target->targetId=reply.TargetId;target->effects=reply.Effects;
    ready=host_kernel_report(result,ERROR_SUCCESS,reply.Header.Status,1,1);
finished:
    ReleaseSRWLockExclusive(&host_kernel_records);return ready;
}
static int host_kernel_query_locked(HANDLE actual,uint64_t token,const HostKernelTarget *target,RVH_TARGET_REPLY *reply,HostKernelResult *result){
    if(!reply)return host_kernel_report(result,ERROR_INVALID_PARAMETER,0,0,0);
    memset(reply,0,sizeof(*reply));
    if(!host_kernel_target_pair(actual,token,target,result)||!host_kernel_target_live(target,result))return 0;
    RVH_TARGET_REQUEST request;memset(&request,0,sizeof(request));host_kernel_request(&request.Header,sizeof(request),0);
    request.Kind=target->kind;request.TargetId=target->targetId;
    if(!host_kernel_io(IOCTL_RVH_QUERY_TARGET,&request,sizeof(request),reply,sizeof(*reply),result))return 0;
    if(!host_kernel_target_reply(reply,target,target->effects,0,result))return 0;
    return host_kernel_report(result,ERROR_SUCCESS,reply->Header.Status,1,1);
}
static int host_kernel_query(HANDLE actual,uint64_t token,HostKernelTarget *target,RVH_TARGET_REPLY *reply,HostKernelResult *result){
    AcquireSRWLockExclusive(&host_kernel_records);int ready=host_kernel_query_locked(actual,token,target,reply,result);
    ReleaseSRWLockExclusive(&host_kernel_records);return ready;
}
static int host_kernel_file(HANDLE file,HANDLE witness,uint64_t token,const HostKernelTarget *actor,ULONG effects,HostKernelTarget *target,HostKernelResult *result){
    AcquireSRWLockExclusive(&host_kernel_records);int ready=0;
    if(!actor||actor==target||actor->kind!=RVH_TARGET_PROCESS||!effects||effects&~RVH_FILE_ALLOWED_FLAGS){
        host_kernel_report(result,ERROR_INVALID_PARAMETER,0,0,0);goto finished;
    }
    if(!host_kernel_target_live(actor,result))goto finished;
    RVH_TARGET_REPLY actorReply;
    if(!host_kernel_query_locked(actor->witness,actor->observationToken,actor,&actorReply,result))goto finished;
    if(!host_kernel_prepare_target(file,witness,token,RVH_TARGET_FILE,target,result))goto finished;
    FILE_ID_INFO identity;
    if(!GetFileInformationByHandleEx(target->witness,FileIdInfo,&identity,sizeof(identity))){
        DWORD error=GetLastError();host_kernel_report(result,error,0,0,0);goto finished;
    }
    UCHAR present=0;for(unsigned i=0;i<sizeof(identity.FileId.Identifier);i++)present|=identity.FileId.Identifier[i];
    if(!present){host_kernel_report(result,ERROR_NOT_SUPPORTED,0,0,0);goto finished;}
    if(target->targetId&&(target->actorTargetId!=actor->targetId
            ||target->scope.Low!=actor->scope.Low||target->scope.High!=actor->scope.High
            ||target->volumeSerial!=identity.VolumeSerialNumber||memcmp(target->fileId,identity.FileId.Identifier,sizeof(target->fileId)))){
        host_kernel_report(result,ERROR_NOT_SAME_OBJECT,0,0,0);goto finished;
    }
    target->scope=actor->scope;target->actorTargetId=actor->targetId;target->volumeSerial=identity.VolumeSerialNumber;
    memcpy(target->fileId,identity.FileId.Identifier,sizeof(target->fileId));
    RVH_FILE_ADD_REQUEST request;memset(&request,0,sizeof(request));host_kernel_request(&request.Header,sizeof(request),0);
    request.FileHandle=(ULONGLONG)(uintptr_t)target->witness;request.ActorTargetId=actor->targetId;request.Scope=actor->scope;request.Effects=effects;
    RVH_TARGET_REPLY reply;int existing=target->targetId!=0;target->removed=0;
    if(!host_kernel_io(IOCTL_RVH_FILE_ADD,&request,sizeof(request),&reply,sizeof(reply),result)){
        if(!existing&&host_kernel_session.response)target->removed=1;goto finished;
    }
    if(!host_kernel_target_reply(&reply,target,effects,1,result))goto finished;
    target->targetId=reply.TargetId;target->effects=reply.Effects;
    ready=host_kernel_report(result,ERROR_SUCCESS,reply.Header.Status,1,1);
finished:
    ReleaseSRWLockExclusive(&host_kernel_records);return ready;
}
static int host_kernel_remove(HANDLE actual,uint64_t token,HostKernelTarget *target,HostKernelResult *result){
    AcquireSRWLockExclusive(&host_kernel_records);int ready=0;
    if(!host_kernel_target_pair(actual,token,target,result))goto finished;
    if(target->removed){ready=host_kernel_forget_locked(target,result);goto finished;}
    if(!host_kernel_target_live(target,result))goto finished;
    RVH_TARGET_REQUEST request;memset(&request,0,sizeof(request));host_kernel_request(&request.Header,sizeof(request),0);
    request.Kind=target->kind;request.TargetId=target->targetId;RVH_RESPONSE reply;
    if(!host_kernel_io(IOCTL_RVH_REMOVE,&request,sizeof(request),&reply,sizeof(reply),result))goto finished;
    target->removed=1;
    if(!host_kernel_forget_locked(target,result))goto finished;
    ready=host_kernel_report(result,ERROR_SUCCESS,reply.Status,1,1);
finished:
    ReleaseSRWLockExclusive(&host_kernel_records);return ready;
}
/* The owner receipt has no duplicated target handle or opaque Mod scope. The
 * same original FILE_OBJECT/process binding authorizes both set and withdrawal. */
static int host_kernel_owner_reply(const RVH_TARGET_REPLY *reply,ULONG effects,HostKernelResult *result){
    UCHAR fileId=0;for(unsigned index=0;index<sizeof(reply->FileId);index++)fileId|=reply->FileId[index];
    if(reply->Reserved||reply->Kind!=RVH_TARGET_OWNER_PROCESS||reply->Effects!=effects
            ||(effects&&!reply->TargetId)||(host_kernel_session.ownerProtection.TargetId
                &&reply->TargetId!=host_kernel_session.ownerProtection.TargetId)
            ||reply->Scope.Low||reply->Scope.High||reply->ActorTargetId||reply->VolumeSerialNumber||fileId
            ||reply->ObservedProcessId!=GetCurrentProcessId()||reply->ChildCreationsDenied
            ||reply->FileOperationsDenied||reply->SectionAttemptsObserved||reply->UnservedObservations)
        return host_kernel_invalid(result,ERROR_INVALID_DATA);
    if(reply->State!=(effects?RVH_TARGET_ACTIVE:RVH_TARGET_DISABLED))
        return host_kernel_report(result,ERROR_INVALID_STATE,reply->Header.Status,1,0);
    return 1;
}
static int host_kernel_owner_apply(ULONG effects,RVH_TARGET_REPLY *observed,HostKernelResult *result){
    AcquireSRWLockExclusive(&host_kernel_records);int ready=0;
    if(observed)memset(observed,0,sizeof(*observed));
    if(effects&~RVH_OWNER_ALLOWED_FLAGS){host_kernel_report(result,ERROR_INVALID_PARAMETER,0,0,0);goto finished;}
    /* Turning off a never-opened or already-closed session must not open the
     * backend. A prior failed close still owns its real cleanup obligation. */
    if(!effects&&!host_kernel_session.live){
        if(host_kernel_session.device&&!host_kernel_drop()){
            DWORD error=GetLastError();host_kernel_report(result,error,0,0,0);goto finished;
        }
        ready=host_kernel_report(result,ERROR_SUCCESS,0,0,1);goto finished;
    }
    if(!host_kernel_open_locked(result))goto finished;
    if(effects&&!(host_kernel_session.observed.Capabilities&RVH_CAP_OWNER_PROCESS_HANDLES)){
        host_kernel_report(result,ERROR_NOT_SUPPORTED,0,1,0);goto finished;
    }
    RVH_OWNER_PROTECT_REQUEST request;memset(&request,0,sizeof(request));host_kernel_request(&request.Header,sizeof(request),0);
    request.Effects=effects;RVH_TARGET_REPLY reply;
    if(!host_kernel_io(IOCTL_RVH_OWNER_PROTECT,&request,sizeof(request),&reply,sizeof(reply),result)
            ||!host_kernel_owner_reply(&reply,effects,result))goto finished;
    host_kernel_session.ownerProtection=reply;if(observed)*observed=reply;
    ready=host_kernel_report(result,ERROR_SUCCESS,reply.Header.Status,1,1);
finished:
    ReleaseSRWLockExclusive(&host_kernel_records);return ready;
}
static int host_kernel_owner_current(ULONG effects){
    AcquireSRWLockShared(&host_kernel_records);
    int current=host_kernel_session.live&&host_kernel_session.device&&host_kernel_session.ownerProtection.TargetId
            &&host_kernel_session.ownerProtection.Effects==effects;
    ReleaseSRWLockShared(&host_kernel_records);return current;
}
/* Observation never opens/reopens a device or reinstalls a policy. Only an
 * explicit desired owner policy, maintained by the existing Host worker, does. */
static int host_kernel_owner_observe(RVH_TARGET_REPLY *observed,HostKernelResult *result){
    AcquireSRWLockExclusive(&host_kernel_records);int ready=0;
    if(observed)memset(observed,0,sizeof(*observed));
    if(!host_kernel_session.live||!host_kernel_session.device||!host_kernel_session.ownerProtection.TargetId){
        host_kernel_report(result,ERROR_INVALID_HANDLE,0,0,0);goto finished;
    }
    RVH_TARGET_REQUEST request;memset(&request,0,sizeof(request));host_kernel_request(&request.Header,sizeof(request),0);
    request.Kind=RVH_TARGET_OWNER_PROCESS;request.TargetId=host_kernel_session.ownerProtection.TargetId;
    RVH_TARGET_REPLY reply;ULONG effects=host_kernel_session.ownerProtection.Effects;
    if(!host_kernel_io(IOCTL_RVH_QUERY_TARGET,&request,sizeof(request),&reply,sizeof(reply),result)
            ||!host_kernel_owner_reply(&reply,effects,result))goto finished;
    host_kernel_session.ownerProtection=reply;if(observed)*observed=reply;
    ready=host_kernel_report(result,ERROR_SUCCESS,reply.Header.Status,1,1);
finished:
    ReleaseSRWLockExclusive(&host_kernel_records);return ready;
}
static int host_kernel_close(HostKernelResult *result){
    AcquireSRWLockExclusive(&host_kernel_records);int ready=host_kernel_drop();DWORD error=ready?ERROR_SUCCESS:GetLastError();
    host_kernel_report(result,error,0,0,ready);ReleaseSRWLockExclusive(&host_kernel_records);return ready;
}
/* The snapshot is an actual HELLO/QUERY_SESSION result. Callback installation,
 * fixed bounds and unserved paths remain independent of a successful open. */
static void host_kernel_state(int64_t values[HOST_KERNEL_STATE_COUNT]){
    AcquireSRWLockExclusive(&host_kernel_records);HostKernelResult result;memset(values,0,sizeof(int64_t)*HOST_KERNEL_STATE_COUNT);
    int ready=host_kernel_session.live&&host_kernel_session.device!=NULL;
    if(ready){
        RVH_REQUEST request;RVH_SESSION_REPLY reply;host_kernel_request(&request,sizeof(request),0);
        ready=host_kernel_io(IOCTL_RVH_QUERY_SESSION,&request,sizeof(request),&reply,sizeof(reply),&result);
        if(ready)ready=host_kernel_session_reply(&reply,&result);
        if(ready)host_kernel_session.observed=reply;
    }
    values[0]=ready&&host_kernel_session.live;values[2]=(int64_t)host_kernel_session.generation;
    values[4]=host_kernel_session.error;values[5]=host_kernel_session.status;
    if(ready){const RVH_SESSION_REPLY *reply=&host_kernel_session.observed;
        values[1]=(int64_t)reply->Header.SessionId;values[3]=reply->Header.Version;
        values[6]=(int64_t)reply->Capabilities;values[7]=(int64_t)reply->Unserved;
        values[8]=reply->ObRegisterStatus;values[9]=reply->ProcessNotifyStatus;
        values[10]=reply->FilterRegisterStatus;values[11]=reply->FilterStartStatus;values[12]=reply->DriverState;
        values[13]=reply->ProcessCount;values[14]=reply->FileCount;values[15]=reply->MaxProcesses;values[16]=reply->MaxFiles;
    }
    ReleaseSRWLockExclusive(&host_kernel_records);
}
