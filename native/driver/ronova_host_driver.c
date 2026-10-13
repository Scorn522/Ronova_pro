/* Windows 10 2004+ x64 WDK kernel driver. No installation or signing code.
 * Source ownership comes from the existing Host client; the kernel can bind a
 * private process object, not distinguish individual Java modules in one JVM.
 * All callback tables and references live in nonpaged storage. The short spin
 * lock publishes/removes effects; filesystem calls and dereferences stay out.
 */
#include <fltKernel.h>
#include <wdmsec.h>
#include "ronova_host_protocol.h"

#define RVH_TAG 'HvoR'
/* Windows SDK 10.0.26100.0 winnt.h process/thread access-mask values. The WDK
 * kernel headers do not expose the complete user-mode set; retain the exact
 * existing masks without importing windows.h into the kernel translation unit. */
#define RVH_ACCESS_PROCESS_TERMINATE                 0x0001UL
#define RVH_ACCESS_PROCESS_CREATE_THREAD             0x0002UL
#define RVH_ACCESS_PROCESS_VM_OPERATION              0x0008UL
#define RVH_ACCESS_PROCESS_VM_WRITE                  0x0020UL
#define RVH_ACCESS_PROCESS_DUP_HANDLE                0x0040UL
#define RVH_ACCESS_PROCESS_CREATE_PROCESS            0x0080UL
#define RVH_ACCESS_PROCESS_SET_QUOTA                 0x0100UL
#define RVH_ACCESS_PROCESS_SET_INFORMATION           0x0200UL
#define RVH_ACCESS_PROCESS_SUSPEND_RESUME             0x0800UL
#define RVH_ACCESS_PROCESS_QUERY_LIMITED_INFORMATION  0x1000UL
#define RVH_ACCESS_THREAD_TERMINATE                  0x0001UL
#define RVH_ACCESS_THREAD_SUSPEND_RESUME              0x0002UL
#define RVH_ACCESS_THREAD_SET_CONTEXT                 0x0010UL
#define RVH_ACCESS_THREAD_SET_INFORMATION             0x0020UL
#define RVH_ACCESS_THREAD_SET_THREAD_TOKEN            0x0080UL
#define RVH_ACCESS_THREAD_IMPERSONATE                 0x0100UL
#define RVH_ACCESS_THREAD_DIRECT_IMPERSONATION        0x0200UL
#define RVH_ACCESS_THREAD_SET_LIMITED_INFORMATION     0x0400UL
#define RVH_PROCESS_RIGHTS (RVH_ACCESS_PROCESS_CREATE_PROCESS|RVH_ACCESS_PROCESS_CREATE_THREAD|RVH_ACCESS_PROCESS_DUP_HANDLE|RVH_ACCESS_PROCESS_SET_QUOTA|RVH_ACCESS_PROCESS_SET_INFORMATION|RVH_ACCESS_PROCESS_SUSPEND_RESUME|RVH_ACCESS_PROCESS_TERMINATE|RVH_ACCESS_PROCESS_VM_OPERATION|RVH_ACCESS_PROCESS_VM_WRITE)
#define RVH_THREAD_RIGHTS (RVH_ACCESS_THREAD_DIRECT_IMPERSONATION|RVH_ACCESS_THREAD_IMPERSONATE|RVH_ACCESS_THREAD_SET_CONTEXT|RVH_ACCESS_THREAD_SET_INFORMATION|RVH_ACCESS_THREAD_SET_LIMITED_INFORMATION|RVH_ACCESS_THREAD_SET_THREAD_TOKEN|RVH_ACCESS_THREAD_SUSPEND_RESUME|RVH_ACCESS_THREAD_TERMINATE)

typedef struct RVH_PROCESS_RECORD {
    ULONGLONG Id;
    RVH_SCOPE Scope;
    PEPROCESS Process;
    ULONG Effects;
    BOOLEAN Exited;
    ULONGLONG RightsReduced,ChildrenDenied;
} RVH_PROCESS_RECORD;

typedef struct RVH_FILE_RECORD {
    ULONGLONG Id,ActorId;
    RVH_SCOPE Scope;
    PEPROCESS Actor;
    PFILE_OBJECT FileObject;
    HANDLE KeepOpen;
    PFLT_VOLUME Volume;
    PFLT_INSTANCE Instance;
    FILE_ID_INFORMATION Identity;
    ULONG Effects;
    BOOLEAN ActorExited,Detached;
    ULONGLONG Denied,Sections,Unserved;
} RVH_FILE_RECORD;

typedef struct RVH_SESSION {
    LIST_ENTRY Link;
    EX_RUNDOWN_REF Requests;
    KEVENT Retired;
    PEPROCESS Owner;
    PFILE_OBJECT DeviceFile;
    ULONGLONG Id,Unserved;
    BOOLEAN Closed,OwnerExited;
    RVH_PROCESS_RECORD Processes[RVH_MAX_PROCESSES];
    RVH_FILE_RECORD Files[RVH_MAX_FILES];
} RVH_SESSION;

typedef struct RVH_STREAM_ID {
    FILE_ID_INFORMATION Identity;
} RVH_STREAM_ID;
typedef struct RVH_INSTANCE_STATE {
    BOOLEAN Detached; /* published under RvhLock, including FILE_ADD */
} RVH_INSTANCE_STATE;

static KSPIN_LOCK RvhLock;
static LIST_ENTRY RvhSessions;
static EX_RUNDOWN_REF RvhCalls;
static ULONG RvhSessionCount;
static volatile LONG RvhStopping;
static volatile LONG RvhShutdownStarted;
static volatile LONG64 RvhSequence;
static PDEVICE_OBJECT RvhDevice;
static PDRIVER_OBJECT RvhDriver;
static PFLT_FILTER RvhFilter;
static PVOID RvhObHandle;
static BOOLEAN RvhNotifyRegistered,RvhLinkCreated;
static UNICODE_STRING RvhObAltitude;
static NTSTATUS RvhObStatus=STATUS_NOT_SUPPORTED;
static NTSTATUS RvhNotifyStatus=STATUS_NOT_SUPPORTED;
static NTSTATUS RvhFilterStatus=STATUS_NOT_SUPPORTED;
static NTSTATUS RvhFilterStartStatus=STATUS_NOT_SUPPORTED;

static const GUID RvhDeviceClass={0x4b4674d1,0x816f,0x4e1d,{0x93,0xd8,0xab,0x58,0x8e,0xf9,0x15,0x8f}};

DRIVER_INITIALIZE DriverEntry;
static DRIVER_UNLOAD RvhDriverUnload;
static DRIVER_DISPATCH RvhDispatch;
static NTSTATUS RvhFilterUnload(FLT_FILTER_UNLOAD_FLAGS Flags);
static FLT_PREOP_CALLBACK_STATUS RvhPreCreate(PFLT_CALLBACK_DATA Data,PCFLT_RELATED_OBJECTS Objects,PVOID *Completion);
static FLT_POSTOP_CALLBACK_STATUS RvhPostCreate(PFLT_CALLBACK_DATA Data,PCFLT_RELATED_OBJECTS Objects,PVOID Completion,FLT_POST_OPERATION_FLAGS Flags);
static FLT_PREOP_CALLBACK_STATUS RvhPreFileOperation(PFLT_CALLBACK_DATA Data,PCFLT_RELATED_OBJECTS Objects,PVOID *Completion);
static NTSTATUS RvhInstanceSetup(PCFLT_RELATED_OBJECTS Objects,FLT_INSTANCE_SETUP_FLAGS Flags,DEVICE_TYPE Type,FLT_FILESYSTEM_TYPE FsType);
static VOID RvhInstanceTeardown(PCFLT_RELATED_OBJECTS Objects,FLT_INSTANCE_TEARDOWN_FLAGS Flags);

static const FLT_CONTEXT_REGISTRATION RvhContexts[]={
    {FLT_STREAM_CONTEXT,0,NULL,sizeof(RVH_STREAM_ID),RVH_TAG,NULL,NULL,NULL},
    {FLT_INSTANCE_CONTEXT,0,NULL,sizeof(RVH_INSTANCE_STATE),RVH_TAG,NULL,NULL,NULL},
    {FLT_CONTEXT_END}
};
static const FLT_OPERATION_REGISTRATION RvhOperations[]={
    {IRP_MJ_CREATE,0,RvhPreCreate,RvhPostCreate},
    {IRP_MJ_READ,0,RvhPreFileOperation,NULL},
    {IRP_MJ_WRITE,0,RvhPreFileOperation,NULL},
    {IRP_MJ_SET_INFORMATION,0,RvhPreFileOperation,NULL},
    {IRP_MJ_SET_SECURITY,0,RvhPreFileOperation,NULL},
    {IRP_MJ_ACQUIRE_FOR_SECTION_SYNCHRONIZATION,0,RvhPreFileOperation,NULL},
    {IRP_MJ_OPERATION_END}
};
static const FLT_REGISTRATION RvhRegistration={
    sizeof(FLT_REGISTRATION),FLT_REGISTRATION_VERSION,
    /* Close real sessions, then use the documented nonmandatory filter unload.
     * Do not allow service stop to force-unload a live control-device session. */
    FLTFL_REGISTRATION_DO_NOT_SUPPORT_SERVICE_STOP,
    RvhContexts,RvhOperations,RvhFilterUnload,RvhInstanceSetup,NULL,
    RvhInstanceTeardown,NULL,NULL,NULL,NULL,NULL,NULL,NULL
};

static BOOLEAN RvhScopeEqual(RVH_SCOPE Left,RVH_SCOPE Right){return Left.Low==Right.Low&&Left.High==Right.High;}
static BOOLEAN RvhScopePresent(RVH_SCOPE Scope){return Scope.Low!=0||Scope.High!=0;}
static ULONGLONG RvhNextId(VOID){return (ULONGLONG)InterlockedIncrement64(&RvhSequence);}
static BOOLEAN RvhFileIdValid(const FILE_ID_INFORMATION *Identity){
    UCHAR any=0;ULONG i;for(i=0;i<sizeof(Identity->FileId.Identifier);i++)any|=Identity->FileId.Identifier[i];return any!=0;
}
static BOOLEAN RvhFileIdEqual(const FILE_ID_INFORMATION *Left,const FILE_ID_INFORMATION *Right){
    return Left->VolumeSerialNumber==Right->VolumeSerialNumber
        &&RtlCompareMemory(Left->FileId.Identifier,Right->FileId.Identifier,sizeof(Left->FileId.Identifier))==sizeof(Left->FileId.Identifier);
}
static RVH_PROCESS_RECORD *RvhProcessById(RVH_SESSION *Session,ULONGLONG Id){
    ULONG i;for(i=0;i<RVH_MAX_PROCESSES;i++)if(Session->Processes[i].Id==Id&&Id!=0)return &Session->Processes[i];return NULL;
}
static RVH_FILE_RECORD *RvhFileById(RVH_SESSION *Session,ULONGLONG Id){
    ULONG i;for(i=0;i<RVH_MAX_FILES;i++)if(Session->Files[i].Id==Id&&Id!=0)return &Session->Files[i];return NULL;
}
static VOID RvhDropFile(RVH_FILE_RECORD *File){
    if(File->KeepOpen!=NULL)ZwClose(File->KeepOpen);
    if(File->FileObject!=NULL)ObDereferenceObject(File->FileObject);
    if(File->Actor!=NULL)ObDereferenceObject(File->Actor);
    if(File->Instance!=NULL)FltObjectDereference(File->Instance);
    if(File->Volume!=NULL)FltObjectDereference(File->Volume);
    RtlZeroMemory(File,sizeof(*File));
}
static VOID RvhHeader(RVH_RESPONSE *Out,const RVH_REQUEST *In,RVH_SESSION *Session,ULONG Size,NTSTATUS Status){
    Out->Magic=RVH_MAGIC;Out->Version=RVH_VERSION;Out->Size=Size;Out->Status=Status;
    Out->SessionId=Session->Id;Out->RequestId=In->RequestId;
}
static ULONGLONG RvhCapabilities(VOID){
    ULONGLONG result=RVH_CAP_DEVICE_SESSION;
    if(NT_SUCCESS(RvhNotifyStatus))result|=RVH_CAP_PROCESS_OBJECT_BIND;
    if(NT_SUCCESS(RvhObStatus)&&NT_SUCCESS(RvhNotifyStatus))result|=RVH_CAP_OUTSIDE_HANDLE_RIGHTS;
    if(NT_SUCCESS(RvhNotifyStatus))result|=RVH_CAP_CHILD_CREATION_DENIAL;
    if(NT_SUCCESS(RvhNotifyStatus)&&NT_SUCCESS(RvhFilterStatus)&&NT_SUCCESS(RvhFilterStartStatus))result|=RVH_CAP_EXACT_FILE_BIND|RVH_CAP_FILE_READ_WRITE|RVH_CAP_NEW_EXECUTE_OPEN_DENIAL;
    return result;
}
static VOID RvhSessionReply(RVH_SESSION *Session,RVH_SESSION_REPLY *Out){
    ULONG i;KIRQL irql;Out->Capabilities=RvhCapabilities();Out->MaxProcesses=RVH_MAX_PROCESSES;Out->MaxFiles=RVH_MAX_FILES;
    Out->ObRegisterStatus=RvhObStatus;Out->ProcessNotifyStatus=RvhNotifyStatus;
    Out->FilterRegisterStatus=RvhFilterStatus;Out->FilterStartStatus=RvhFilterStartStatus;
    Out->DriverState=InterlockedCompareExchange(&RvhStopping,0,0)?RVH_DRIVER_UNLOADING:RVH_DRIVER_LOADED;
    KeAcquireSpinLock(&RvhLock,&irql);Out->Unserved=RVH_BASE_GAPS|Session->Unserved;
    for(i=0;i<RVH_MAX_PROCESSES;i++)if(Session->Processes[i].Id!=0)Out->ProcessCount++;
    for(i=0;i<RVH_MAX_FILES;i++)if(Session->Files[i].Id!=0)Out->FileCount++;
    KeReleaseSpinLock(&RvhLock,irql);
}
/* Called only under RvhLock; it exports values, never kernel pointers. */
static VOID RvhProcessReply(RVH_SESSION *Session,RVH_PROCESS_RECORD *Record,RVH_TARGET_REPLY *Out){
    Out->Kind=RVH_TARGET_PROCESS;Out->Effects=Record->Effects;Out->TargetId=Record->Id;Out->Scope=Record->Scope;
    Out->ObservedProcessId=(ULONGLONG)(ULONG_PTR)PsGetProcessId(Record->Process);
    Out->State=Session->OwnerExited?RVH_TARGET_OWNER_EXITED:Record->Exited?RVH_TARGET_PROCESS_EXITED:RVH_TARGET_ACTIVE;
    Out->HandleRightsReduced=Record->RightsReduced;Out->ChildCreationsDenied=Record->ChildrenDenied;
}
static VOID RvhFileReply(RVH_SESSION *Session,RVH_FILE_RECORD *Record,RVH_TARGET_REPLY *Out){
    Out->Kind=RVH_TARGET_FILE;Out->Effects=Record->Effects;Out->TargetId=Record->Id;Out->Scope=Record->Scope;
    Out->ActorTargetId=Record->ActorId;Out->ObservedProcessId=(ULONGLONG)(ULONG_PTR)PsGetProcessId(Record->Actor);
    Out->VolumeSerialNumber=Record->Identity.VolumeSerialNumber;
    RtlCopyMemory(Out->FileId,Record->Identity.FileId.Identifier,sizeof(Out->FileId));
    Out->State=Session->OwnerExited?RVH_TARGET_OWNER_EXITED:Record->ActorExited?RVH_TARGET_PROCESS_EXITED:Record->Detached?RVH_TARGET_VOLUME_DETACHED:RVH_TARGET_ACTIVE;
    Out->FileOperationsDenied=Record->Denied;Out->SectionAttemptsObserved=Record->Sections;Out->UnservedObservations=Record->Unserved;
}

static NTSTATUS RvhAddProcess(RVH_SESSION *Session,const RVH_PROCESS_ADD_REQUEST *In,RVH_TARGET_REPLY *Out){
    NTSTATUS status;PEPROCESS process=NULL;RVH_PROCESS_RECORD *freeRecord=NULL;ULONG i;KIRQL irql;
    if(In->Reserved!=0||!RvhScopePresent(In->Scope)||In->Effects==0||(In->Effects&~RVH_PROCESS_ALLOWED_FLAGS)!=0)return STATUS_INVALID_PARAMETER;
    /* The notification also retires exact owner/actor objects on real exit.
     * An installed Ob callback alone does not provide this session lifetime. */
    if(!NT_SUCCESS(RvhNotifyStatus))return RvhNotifyStatus;
    if((In->Effects&RVH_PROCESS_PROTECT_OUTSIDE_HANDLES)!=0&&!NT_SUCCESS(RvhObStatus))return RvhObStatus;
    if((In->Effects&RVH_PROCESS_DENY_CHILD_CREATION)!=0&&!NT_SUCCESS(RvhNotifyStatus))return RvhNotifyStatus;
    /* Resolves in the actual original client process, with user access checks. */
    status=ObReferenceObjectByHandle((HANDLE)(ULONG_PTR)In->ProcessHandle,RVH_ACCESS_PROCESS_QUERY_LIMITED_INFORMATION|RVH_ACCESS_PROCESS_SET_INFORMATION,*PsProcessType,UserMode,(PVOID *)&process,NULL);
    if(!NT_SUCCESS(status))return status;
    if(process==Session->Owner||process==PsInitialSystemProcess){ObDereferenceObject(process);return STATUS_ACCESS_DENIED;}
    if(PsGetProcessExitStatus(process)!=STATUS_PENDING){ObDereferenceObject(process);return STATUS_PROCESS_IS_TERMINATING;}
    KeAcquireSpinLock(&RvhLock,&irql);
    status=Session->Closed||Session->OwnerExited?STATUS_FILE_CLOSED:STATUS_INSUFFICIENT_RESOURCES;
    if(!Session->Closed&&!Session->OwnerExited)for(i=0;i<RVH_MAX_PROCESSES;i++){
        RVH_PROCESS_RECORD *record=&Session->Processes[i];
        if(record->Id==0){if(freeRecord==NULL)freeRecord=record;continue;}
        if(record->Process!=process)continue;
        if(!RvhScopeEqual(record->Scope,In->Scope)){status=STATUS_OBJECT_NAME_COLLISION;freeRecord=NULL;break;}
        /* One exact object has one policy in this session. Replace atomically. */
        record->Effects=In->Effects;RvhProcessReply(Session,record,Out);status=STATUS_SUCCESS;freeRecord=NULL;break;
    }
    if(status==STATUS_INSUFFICIENT_RESOURCES&&freeRecord!=NULL){
        freeRecord->Id=RvhNextId();freeRecord->Process=process;ObReferenceObject(process);freeRecord->Scope=In->Scope;freeRecord->Effects=In->Effects;
        RvhProcessReply(Session,freeRecord,Out);status=STATUS_SUCCESS;
    }
    KeReleaseSpinLock(&RvhLock,irql);
    /* Exit may have preceded publication, including an older exited HANDLE.
     * Query the same retained object outside the DISPATCH_LEVEL spin lock. */
    if(NT_SUCCESS(status)&&PsGetProcessExitStatus(process)!=STATUS_PENDING){
        KeAcquireSpinLock(&RvhLock,&irql);freeRecord=RvhProcessById(Session,Out->TargetId);
        if(freeRecord!=NULL&&freeRecord->Process==process){freeRecord->Exited=TRUE;RvhProcessReply(Session,freeRecord,Out);}
        KeReleaseSpinLock(&RvhLock,irql);
    }
    if(process!=NULL)ObDereferenceObject(process);return status;
}

static NTSTATUS RvhAddFile(RVH_SESSION *Session,const RVH_FILE_ADD_REQUEST *In,RVH_TARGET_REPLY *Out){
    RVH_FILE_RECORD prepared={0};RVH_INSTANCE_STATE *instanceState=NULL;FILE_STANDARD_INFORMATION standard;
    RVH_FILE_RECORD *freeRecord=NULL;RVH_PROCESS_RECORD *actor;NTSTATUS status;ULONG i,returned=0;KIRQL irql;
    if(In->Reserved!=0||!RvhScopePresent(In->Scope)||In->Effects==0||(In->Effects&~RVH_FILE_ALLOWED_FLAGS)!=0)return STATUS_INVALID_PARAMETER;
    if(!NT_SUCCESS(RvhFilterStatus))return RvhFilterStatus;if(!NT_SUCCESS(RvhFilterStartStatus))return RvhFilterStartStatus;
    if(IoGetTopLevelIrp()!=NULL||KeAreAllApcsDisabled())return STATUS_INVALID_DEVICE_STATE;
    KeAcquireSpinLock(&RvhLock,&irql);actor=RvhProcessById(Session,In->ActorTargetId);
    if(actor==NULL||actor->Exited||!RvhScopeEqual(actor->Scope,In->Scope)||Session->Closed||Session->OwnerExited)status=STATUS_INVALID_HANDLE;
    else{prepared.Actor=actor->Process;ObReferenceObject(prepared.Actor);status=STATUS_SUCCESS;}
    KeReleaseSpinLock(&RvhLock,irql);if(!NT_SUCCESS(status))return status;
    prepared.ActorId=In->ActorTargetId;prepared.Scope=In->Scope;prepared.Effects=In->Effects;
    if(PsGetProcessExitStatus(prepared.Actor)!=STATUS_PENDING){status=STATUS_PROCESS_IS_TERMINATING;goto Done;}
    status=ObReferenceObjectByHandle((HANDLE)(ULONG_PTR)In->FileHandle,FILE_READ_ATTRIBUTES,*IoFileObjectType,UserMode,(PVOID *)&prepared.FileObject,NULL);
    if(!NT_SUCCESS(status))goto Done;
    if((prepared.FileObject->Flags&FO_HANDLE_CREATED)==0||(prepared.FileObject->Flags&(FO_CLEANUP_COMPLETE|FO_FILE_OPEN_CANCELLED))!=0){status=STATUS_FILE_CLOSED;goto Done;}
    /* Keep this already-authorized exact file open when the client closes its
     * temporary duplicate. No new access is requested or exported to user mode. */
    status=ObOpenObjectByPointer(prepared.FileObject,OBJ_KERNEL_HANDLE,NULL,FILE_READ_ATTRIBUTES,*IoFileObjectType,KernelMode,&prepared.KeepOpen);
    if(!NT_SUCCESS(status))goto Done;
    status=FltGetVolumeFromFileObject(RvhFilter,prepared.FileObject,&prepared.Volume);if(!NT_SUCCESS(status))goto Done;
    status=FltGetVolumeInstanceFromName(RvhFilter,prepared.Volume,NULL,&prepared.Instance);if(!NT_SUCCESS(status))goto Done;
    status=FltGetInstanceContext(prepared.Instance,(PFLT_CONTEXT *)&instanceState);if(!NT_SUCCESS(status))goto Done;
    status=FltQueryInformationFile(prepared.Instance,prepared.FileObject,&standard,sizeof(standard),FileStandardInformation,&returned);
    if(!NT_SUCCESS(status))goto Done;if(returned<sizeof(standard)||standard.Directory||standard.DeletePending){status=STATUS_NOT_SUPPORTED;goto Done;}
    status=FltQueryInformationFile(prepared.Instance,prepared.FileObject,&prepared.Identity,sizeof(prepared.Identity),FileIdInformation,&returned);
    if(!NT_SUCCESS(status))goto Done;if(returned<sizeof(prepared.Identity)||!RvhFileIdValid(&prepared.Identity)){status=STATUS_NOT_SUPPORTED;goto Done;}
    KeAcquireSpinLock(&RvhLock,&irql);actor=RvhProcessById(Session,In->ActorTargetId);
    if(Session->Closed||Session->OwnerExited||actor==NULL||actor->Exited||actor->Process!=prepared.Actor||!RvhScopeEqual(actor->Scope,In->Scope))status=STATUS_FILE_CLOSED;
    else if(instanceState->Detached)status=STATUS_VOLUME_DISMOUNTED;
    else{
        status=STATUS_INSUFFICIENT_RESOURCES;
        for(i=0;i<RVH_MAX_FILES;i++){
            RVH_FILE_RECORD *record=&Session->Files[i];
            if(record->Id==0){if(freeRecord==NULL)freeRecord=record;continue;}
            if(record->Actor!=prepared.Actor||record->Volume!=prepared.Volume||record->Instance!=prepared.Instance||!RvhFileIdEqual(&record->Identity,&prepared.Identity))continue;
            if(!RvhScopeEqual(record->Scope,prepared.Scope)){status=STATUS_OBJECT_NAME_COLLISION;freeRecord=NULL;break;}
            if(record->Detached){status=STATUS_VOLUME_DISMOUNTED;freeRecord=NULL;break;}
            record->Effects=prepared.Effects;RvhFileReply(Session,record,Out);status=STATUS_SUCCESS;freeRecord=NULL;break;
        }
        if(status==STATUS_INSUFFICIENT_RESOURCES&&freeRecord!=NULL){
            prepared.Id=RvhNextId();*freeRecord=prepared;RtlZeroMemory(&prepared,sizeof(prepared));
            RvhFileReply(Session,freeRecord,Out);status=STATUS_SUCCESS;
        }
    }
    KeReleaseSpinLock(&RvhLock,irql);
Done:
    if(instanceState!=NULL)FltReleaseContext(instanceState);RvhDropFile(&prepared);return status;
}

static NTSTATUS RvhTarget(RVH_SESSION *Session,const RVH_TARGET_REQUEST *In,RVH_TARGET_REPLY *Out,BOOLEAN Remove){
    RVH_PROCESS_RECORD *process;RVH_FILE_RECORD *file;RVH_FILE_RECORD released={0};PEPROCESS releasedProcess=NULL,observedProcess=NULL;
    NTSTATUS status=STATUS_INVALID_HANDLE;KIRQL irql;ULONG i;
    if(In->Reserved!=0||In->TargetId==0||(In->Kind!=RVH_TARGET_PROCESS&&In->Kind!=RVH_TARGET_FILE))return STATUS_INVALID_PARAMETER;
    KeAcquireSpinLock(&RvhLock,&irql);
    if(Session->Closed)status=STATUS_FILE_CLOSED;
    else if(In->Kind==RVH_TARGET_PROCESS&&(process=RvhProcessById(Session,In->TargetId))!=NULL){
        status=STATUS_SUCCESS;
        if(Remove){
            for(i=0;i<RVH_MAX_FILES;i++)if(Session->Files[i].Id!=0&&Session->Files[i].ActorId==process->Id){status=STATUS_DEVICE_BUSY;break;}
            if(NT_SUCCESS(status)){releasedProcess=process->Process;RtlZeroMemory(process,sizeof(*process));}
        }else{RvhProcessReply(Session,process,Out);observedProcess=process->Process;ObReferenceObject(observedProcess);}
    }else if(In->Kind==RVH_TARGET_FILE&&(file=RvhFileById(Session,In->TargetId))!=NULL){
        status=STATUS_SUCCESS;if(Remove){released=*file;RtlZeroMemory(file,sizeof(*file));}else{RvhFileReply(Session,file,Out);observedProcess=file->Actor;ObReferenceObject(observedProcess);}
    }
    KeReleaseSpinLock(&RvhLock,irql);
    if(observedProcess!=NULL){
        if(PsGetProcessExitStatus(observedProcess)!=STATUS_PENDING){
            KeAcquireSpinLock(&RvhLock,&irql);
            if(In->Kind==RVH_TARGET_PROCESS&&(process=RvhProcessById(Session,In->TargetId))!=NULL&&process->Process==observedProcess){process->Exited=TRUE;RvhProcessReply(Session,process,Out);}
            else if(In->Kind==RVH_TARGET_FILE&&(file=RvhFileById(Session,In->TargetId))!=NULL&&file->Actor==observedProcess){file->ActorExited=TRUE;RvhFileReply(Session,file,Out);}
            KeReleaseSpinLock(&RvhLock,irql);
        }
        ObDereferenceObject(observedProcess);
    }
    if(releasedProcess!=NULL)ObDereferenceObject(releasedProcess);RvhDropFile(&released);return status;
}

static OB_PREOP_CALLBACK_STATUS RvhObjectPre(PVOID Context,POB_PRE_OPERATION_INFORMATION Information){
    PEPROCESS target,destination;ACCESS_MASK *access,mask;LIST_ENTRY *link;ULONG i;KIRQL irql;
    UNREFERENCED_PARAMETER(Context);
    if(Information->KernelHandle||!ExAcquireRundownProtection(&RvhCalls))return OB_PREOP_SUCCESS;
    if(Information->ObjectType==*PsProcessType){target=(PEPROCESS)Information->Object;mask=RVH_PROCESS_RIGHTS;}
    else if(Information->ObjectType==*PsThreadType){target=PsGetThreadProcess((PETHREAD)Information->Object);mask=RVH_THREAD_RIGHTS;}
    else{ExReleaseRundownProtection(&RvhCalls);return OB_PREOP_SUCCESS;}
    if(Information->Operation==OB_OPERATION_HANDLE_CREATE){access=&Information->Parameters->CreateHandleInformation.DesiredAccess;destination=PsGetCurrentProcess();}
    else if(Information->Operation==OB_OPERATION_HANDLE_DUPLICATE){access=&Information->Parameters->DuplicateHandleInformation.DesiredAccess;destination=(PEPROCESS)Information->Parameters->DuplicateHandleInformation.TargetProcess;}
    else{ExReleaseRundownProtection(&RvhCalls);return OB_PREOP_SUCCESS;}
    KeAcquireSpinLock(&RvhLock,&irql);
    for(link=RvhSessions.Flink;link!=&RvhSessions;link=link->Flink){
        RVH_SESSION *session=CONTAINING_RECORD(link,RVH_SESSION,Link);if(session->Closed||session->OwnerExited)continue;
        for(i=0;i<RVH_MAX_PROCESSES;i++){
            RVH_PROCESS_RECORD *record=&session->Processes[i];
            if(record->Id==0||record->Exited||record->Process!=target||(record->Effects&RVH_PROCESS_PROTECT_OUTSIDE_HANDLES)==0)continue;
            /* Destination controls duplication: an owner transferring to an
             * outside process does not give that process the owner's exemption. */
            if(destination==session->Owner||destination==target)continue;
            if((*access&mask)!=0){*access&=~mask;record->RightsReduced++;}
        }
    }
    KeReleaseSpinLock(&RvhLock,irql);ExReleaseRundownProtection(&RvhCalls);return OB_PREOP_SUCCESS;
}

static VOID RvhProcessNotify(PEPROCESS Process,HANDLE ProcessId,PPS_CREATE_NOTIFY_INFO Create){
    LIST_ENTRY *link;KIRQL irql;ULONG i;PEPROCESS creator=PsGetCurrentProcess();UNREFERENCED_PARAMETER(ProcessId);
    if(!ExAcquireRundownProtection(&RvhCalls))return;
    KeAcquireSpinLock(&RvhLock,&irql);
    for(link=RvhSessions.Flink;link!=&RvhSessions;link=link->Flink){
        RVH_SESSION *session=CONTAINING_RECORD(link,RVH_SESSION,Link);if(session->Closed)continue;
        if(Create==NULL){
            if(session->Owner==Process)session->OwnerExited=TRUE;
            for(i=0;i<RVH_MAX_PROCESSES;i++)if(session->Processes[i].Id!=0&&session->Processes[i].Process==Process)session->Processes[i].Exited=TRUE;
            for(i=0;i<RVH_MAX_FILES;i++)if(session->Files[i].Id!=0&&session->Files[i].Actor==Process)session->Files[i].ActorExited=TRUE;
        }else if(!session->OwnerExited&&NT_SUCCESS(Create->CreationStatus)){
            for(i=0;i<RVH_MAX_PROCESSES;i++){
                RVH_PROCESS_RECORD *record=&session->Processes[i];
                /* The documented callback runs on the creating thread. A
                 * user-selected parent PID is not the actual creator object. */
                if(record->Id!=0&&!record->Exited&&record->Process==creator&&(record->Effects&RVH_PROCESS_DENY_CHILD_CREATION)!=0){
                    Create->CreationStatus=STATUS_ACCESS_DENIED;record->ChildrenDenied++;break;
                }
            }
        }
    }
    KeReleaseSpinLock(&RvhLock,irql);ExReleaseRundownProtection(&RvhCalls);
}

static BOOLEAN RvhRelevantFiles(PEPROCESS Actor,PFLT_VOLUME Volume,PFLT_INSTANCE Instance){
    LIST_ENTRY *link;KIRQL irql;ULONG i;BOOLEAN found=FALSE;
    KeAcquireSpinLock(&RvhLock,&irql);
    for(link=RvhSessions.Flink;link!=&RvhSessions&&!found;link=link->Flink){
        RVH_SESSION *session=CONTAINING_RECORD(link,RVH_SESSION,Link);if(session->Closed||session->OwnerExited)continue;
        for(i=0;i<RVH_MAX_FILES;i++){RVH_FILE_RECORD *record=&session->Files[i];
            if(record->Id!=0&&!record->Detached&&!record->ActorExited&&record->Actor==Actor&&record->Volume==Volume&&record->Instance==Instance){found=TRUE;break;}}
    }
    KeReleaseSpinLock(&RvhLock,irql);return found;
}
static VOID RvhFileGap(PEPROCESS Actor,PFLT_VOLUME Volume,PFLT_INSTANCE Instance,ULONGLONG Gap){
    LIST_ENTRY *link;KIRQL irql;ULONG i;
    KeAcquireSpinLock(&RvhLock,&irql);
    for(link=RvhSessions.Flink;link!=&RvhSessions;link=link->Flink){
        RVH_SESSION *session=CONTAINING_RECORD(link,RVH_SESSION,Link);if(session->Closed||session->OwnerExited)continue;
        for(i=0;i<RVH_MAX_FILES;i++){RVH_FILE_RECORD *record=&session->Files[i];
            if(record->Id!=0&&record->Actor==Actor&&record->Volume==Volume&&record->Instance==Instance){session->Unserved|=Gap;record->Unserved++;}}
    }
    KeReleaseSpinLock(&RvhLock,irql);
}
static BOOLEAN RvhFileDecision(PEPROCESS Actor,PFILE_OBJECT File,PFLT_VOLUME Volume,PFLT_INSTANCE Instance,const FILE_ID_INFORMATION *Identity,ULONG Effect,BOOLEAN Section,ULONGLONG Gap){
    LIST_ENTRY *link;KIRQL irql;ULONG i;BOOLEAN denied=FALSE;
    KeAcquireSpinLock(&RvhLock,&irql);
    for(link=RvhSessions.Flink;link!=&RvhSessions;link=link->Flink){
        RVH_SESSION *session=CONTAINING_RECORD(link,RVH_SESSION,Link);if(session->Closed||session->OwnerExited)continue;
        for(i=0;i<RVH_MAX_FILES;i++){
            RVH_FILE_RECORD *record=&session->Files[i];
            if(record->Id==0||record->Detached||record->ActorExited||record->Actor!=Actor||record->Volume!=Volume||record->Instance!=Instance)continue;
            if(record->FileObject!=File&&(Identity==NULL||!RvhFileIdEqual(&record->Identity,Identity)))continue;
            if(Gap!=0){record->Unserved++;session->Unserved|=Gap;}
            if(Section){record->Sections++;record->Unserved++;session->Unserved|=RVH_GAP_EXISTING_SECTIONS|RVH_GAP_MAPPED_IO;}
            else if((record->Effects&Effect)!=0){denied=TRUE;record->Denied++;}
        }
    }
    KeReleaseSpinLock(&RvhLock,irql);return denied;
}
static NTSTATUS RvhQueryFileId(PFLT_INSTANCE Instance,PFILE_OBJECT File,FILE_ID_INFORMATION *Identity){
    ULONG returned=0;NTSTATUS status;
    if(KeGetCurrentIrql()!=PASSIVE_LEVEL||KeAreAllApcsDisabled()||IoGetTopLevelIrp()!=NULL)return STATUS_INVALID_DEVICE_STATE;
    RtlZeroMemory(Identity,sizeof(*Identity));
    status=FltQueryInformationFile(Instance,File,Identity,sizeof(*Identity),FileIdInformation,&returned);
    if(NT_SUCCESS(status)&&(returned<sizeof(*Identity)||!RvhFileIdValid(Identity)))status=STATUS_NOT_SUPPORTED;
    return status;
}
static VOID RvhRememberFileId(PFLT_INSTANCE Instance,PFILE_OBJECT File,const FILE_ID_INFORMATION *Identity){
    RVH_STREAM_ID *context=NULL;PFLT_CONTEXT previous=NULL;NTSTATUS status;
    status=FltAllocateContext(RvhFilter,FLT_STREAM_CONTEXT,sizeof(*context),NonPagedPoolNx,(PFLT_CONTEXT *)&context);
    if(!NT_SUCCESS(status))return;context->Identity=*Identity;
    (VOID)FltSetStreamContext(Instance,File,FLT_SET_CONTEXT_KEEP_IF_EXISTS,context,&previous);
    if(previous!=NULL)FltReleaseContext(previous);FltReleaseContext(context);
}
static BOOLEAN RvhCompletedOpenAccess(PIO_SECURITY_CONTEXT Security,ACCESS_MASK *Access){
    ACCESS_MASK requested;PACCESS_STATE state;
    if(Security==NULL)return FALSE;
    requested=Security->DesiredAccess;state=Security->AccessState;
    if(((requested|(state!=NULL?state->OriginalDesiredAccess:0))&MAXIMUM_ALLOWED)!=0){
        /* MAXIMUM_ALLOWED is a request, never FILE_ALL_ACCESS. Consume only a
         * completed public access state with a concrete actual grant. Do not
         * modify that state or infer rights from file names/ReadAccess booleans. */
        if(state==NULL||state->RemainingDesiredAccess!=0||state->PreviouslyGrantedAccess==0
                ||(state->PreviouslyGrantedAccess&MAXIMUM_ALLOWED)!=0)return FALSE;
        *Access=state->PreviouslyGrantedAccess;
    }else *Access=requested;
    /* Post-create runs at PASSIVE_LEVEL. Map generic file rights through the
     * I/O manager's public mapping instead of assuming GENERIC_* bit values. */
    RtlMapGenericMask(Access,IoGetFileObjectGenericMapping());
    return (*Access&(GENERIC_READ|GENERIC_WRITE|GENERIC_EXECUTE|GENERIC_ALL|MAXIMUM_ALLOWED))==0;
}
static ULONG RvhOpenEffects(ACCESS_MASK Access){
    ULONG effects=0;
    if((Access&FILE_READ_DATA)!=0)effects|=RVH_FILE_DENY_READ;
    if((Access&(FILE_WRITE_DATA|FILE_APPEND_DATA))!=0)effects|=RVH_FILE_DENY_WRITE;
    if((Access&FILE_EXECUTE)!=0)effects|=RVH_FILE_DENY_NEW_EXECUTE_OPEN;
    return effects;
}

static FLT_PREOP_CALLBACK_STATUS RvhPreCreate(PFLT_CALLBACK_DATA Data,PCFLT_RELATED_OBJECTS Objects,PVOID *Completion){
    PEPROCESS actor;UNREFERENCED_PARAMETER(Completion);
    if(Data->RequestorMode!=UserMode||!ExAcquireRundownProtection(&RvhCalls))return FLT_PREOP_SUCCESS_NO_CALLBACK;
    actor=FltGetRequestorProcess(Data);
    if(actor==NULL||!RvhRelevantFiles(actor,Objects->Volume,Objects->Instance)){ExReleaseRundownProtection(&RvhCalls);return FLT_PREOP_SUCCESS_NO_CALLBACK;}
    /* A pre-create FileObject is unopened. Query the actual completed file,
     * then cancel before its user handle is created; never guess by a name. */
    ExReleaseRundownProtection(&RvhCalls);return FLT_PREOP_SUCCESS_WITH_CALLBACK;
}
static FLT_POSTOP_CALLBACK_STATUS RvhPostCreate(PFLT_CALLBACK_DATA Data,PCFLT_RELATED_OBJECTS Objects,PVOID Completion,FLT_POST_OPERATION_FLAGS Flags){
    PEPROCESS actor;FILE_ID_INFORMATION identity;NTSTATUS status;ACCESS_MASK access;ULONG effects;UNREFERENCED_PARAMETER(Completion);
    if((Flags&FLTFL_POST_OPERATION_DRAINING)!=0||!NT_SUCCESS(Data->IoStatus.Status)||Data->IoStatus.Status==STATUS_REPARSE||Data->RequestorMode!=UserMode)return FLT_POSTOP_FINISHED_PROCESSING;
    if(!ExAcquireRundownProtection(&RvhCalls))return FLT_POSTOP_FINISHED_PROCESSING;
    actor=FltGetRequestorProcess(Data);
    if(actor!=NULL&&RvhRelevantFiles(actor,Objects->Volume,Objects->Instance)){
        status=RvhQueryFileId(Objects->Instance,Objects->FileObject,&identity);
        if(!NT_SUCCESS(status))RvhFileGap(actor,Objects->Volume,Objects->Instance,RVH_GAP_FILE_ID_UNAVAILABLE);
        else{
            effects=0;
            if(!RvhCompletedOpenAccess(Data->Iopb->Parameters.Create.SecurityContext,&access)
                    ||(Objects->FileObject->Flags&FO_HANDLE_CREATED)!=0){
                /* A grant we cannot resolve, or a handle created by another
                 * filter, is not permission to cancel/deny every open. Keep
                 * this exact object's gap and retain its later I/O identity. */
                (VOID)RvhFileDecision(actor,Objects->FileObject,Objects->Volume,Objects->Instance,&identity,0,FALSE,RVH_GAP_OPEN_ADMISSION_UNAVAILABLE);
                RvhRememberFileId(Objects->Instance,Objects->FileObject,&identity);
            }else{
                effects=RvhOpenEffects(access);
                if(effects!=0&&RvhFileDecision(actor,Objects->FileObject,Objects->Volume,Objects->Instance,&identity,effects,FALSE,0)){
                    /* Refuse the new backing-file handle before it can create
                     * a read/write/execute section. Already-held file/section
                     * handles and existing views are not revoked by this call. */
                    FltCancelFileOpen(Objects->Instance,Objects->FileObject);Data->IoStatus.Status=STATUS_ACCESS_DENIED;Data->IoStatus.Information=0;
                }else RvhRememberFileId(Objects->Instance,Objects->FileObject,&identity);
            }
        }
    }
    ExReleaseRundownProtection(&RvhCalls);return FLT_POSTOP_FINISHED_PROCESSING;
}
static BOOLEAN RvhMutatesFile(FILE_INFORMATION_CLASS Class){
    switch(Class){
        case FileBasicInformation:case FileDispositionInformation:case FileDispositionInformationEx:
        case FileEndOfFileInformation:case FileAllocationInformation:case FileValidDataLengthInformation:
        case FileRenameInformation:case FileRenameInformationEx:case FileLinkInformation:case FileLinkInformationEx:
        case FileShortNameInformation:return TRUE;
        default:return FALSE;
    }
}
static FLT_PREOP_CALLBACK_STATUS RvhPreFileOperation(PFLT_CALLBACK_DATA Data,PCFLT_RELATED_OBJECTS Objects,PVOID *Completion){
    PEPROCESS actor;RVH_STREAM_ID *context=NULL;FILE_ID_INFORMATION identity;const FILE_ID_INFORMATION *known=NULL;
    NTSTATUS status;ULONG effect=0;BOOLEAN section,denied;UNREFERENCED_PARAMETER(Completion);
    if(!ExAcquireRundownProtection(&RvhCalls))return FLT_PREOP_SUCCESS_NO_CALLBACK;
    actor=FltGetRequestorProcess(Data);
    if(actor==NULL||!RvhRelevantFiles(actor,Objects->Volume,Objects->Instance)){ExReleaseRundownProtection(&RvhCalls);return FLT_PREOP_SUCCESS_NO_CALLBACK;}
    section=Data->Iopb->MajorFunction==IRP_MJ_ACQUIRE_FOR_SECTION_SYNCHRONIZATION;
    if(Data->RequestorMode!=UserMode){RvhFileGap(actor,Objects->Volume,Objects->Instance,RVH_GAP_KERNEL_MODE_IO);ExReleaseRundownProtection(&RvhCalls);return FLT_PREOP_SUCCESS_NO_CALLBACK;}
    if(FlagOn(Data->Iopb->IrpFlags,IRP_PAGING_IO)){RvhFileGap(actor,Objects->Volume,Objects->Instance,RVH_GAP_PAGING_IO);ExReleaseRundownProtection(&RvhCalls);return FLT_PREOP_SUCCESS_NO_CALLBACK;}
    if(Data->Iopb->MajorFunction==IRP_MJ_READ)effect=RVH_FILE_DENY_READ;
    else if(Data->Iopb->MajorFunction==IRP_MJ_WRITE||Data->Iopb->MajorFunction==IRP_MJ_SET_SECURITY)effect=RVH_FILE_DENY_WRITE;
    else if(Data->Iopb->MajorFunction==IRP_MJ_SET_INFORMATION&&RvhMutatesFile(Data->Iopb->Parameters.SetFileInformation.FileInformationClass))effect=RVH_FILE_DENY_WRITE;
    if(!section&&effect==0){ExReleaseRundownProtection(&RvhCalls);return FLT_PREOP_SUCCESS_NO_CALLBACK;}
    if(KeGetCurrentIrql()<=APC_LEVEL&&NT_SUCCESS(FltGetStreamContext(Objects->Instance,Objects->FileObject,(PFLT_CONTEXT *)&context)))known=&context->Identity;
    else if(!section&&KeGetCurrentIrql()==PASSIVE_LEVEL&&!KeAreAllApcsDisabled()&&IoGetTopLevelIrp()==NULL){
        status=RvhQueryFileId(Objects->Instance,Objects->FileObject,&identity);
        if(NT_SUCCESS(status)){known=&identity;RvhRememberFileId(Objects->Instance,Objects->FileObject,&identity);}
    }
    denied=RvhFileDecision(actor,Objects->FileObject,Objects->Volume,Objects->Instance,known,effect,section,0);
    if(known==NULL)RvhFileGap(actor,Objects->Volume,Objects->Instance,RVH_GAP_OLD_FILE_OBJECT_ALIASES);
    if(context!=NULL)FltReleaseContext(context);
    /* Microsoft permits no policy ACCESS_DENIED in section synchronization.
     * This branch records the actual attempt and deliberately leaves it open:
     * https://learn.microsoft.com/en-us/windows-hardware/drivers/ifs/flt-parameters-for-irp-mj-acquire-for-section-synchronization */
    if(denied&&!section){
        if(FLT_IS_FASTIO_OPERATION(Data)){ExReleaseRundownProtection(&RvhCalls);return FLT_PREOP_DISALLOW_FASTIO;}
        Data->IoStatus.Status=STATUS_ACCESS_DENIED;Data->IoStatus.Information=0;
        ExReleaseRundownProtection(&RvhCalls);return FLT_PREOP_COMPLETE;
    }
    ExReleaseRundownProtection(&RvhCalls);return FLT_PREOP_SUCCESS_NO_CALLBACK;
}
static NTSTATUS RvhInstanceSetup(PCFLT_RELATED_OBJECTS Objects,FLT_INSTANCE_SETUP_FLAGS Flags,DEVICE_TYPE Type,FLT_FILESYSTEM_TYPE FsType){
    RVH_INSTANCE_STATE *state=NULL;NTSTATUS status;UNREFERENCED_PARAMETER(Flags);UNREFERENCED_PARAMETER(Type);
    if(FsType!=FLT_FSTYPE_NTFS&&FsType!=FLT_FSTYPE_REFS)return STATUS_FLT_DO_NOT_ATTACH;
    status=FltAllocateContext(Objects->Filter,FLT_INSTANCE_CONTEXT,sizeof(*state),NonPagedPoolNx,(PFLT_CONTEXT *)&state);
    if(!NT_SUCCESS(status))return status;state->Detached=FALSE;
    status=FltSetInstanceContext(Objects->Instance,FLT_SET_CONTEXT_KEEP_IF_EXISTS,state,NULL);FltReleaseContext(state);return status;
}
static VOID RvhInstanceTeardown(PCFLT_RELATED_OBJECTS Objects,FLT_INSTANCE_TEARDOWN_FLAGS Flags){
    LIST_ENTRY *link;KIRQL irql;ULONG i;RVH_INSTANCE_STATE *state=NULL;UNREFERENCED_PARAMETER(Flags);
    if(!ExAcquireRundownProtection(&RvhCalls))return;
    (VOID)FltGetInstanceContext(Objects->Instance,(PFLT_CONTEXT *)&state);
    KeAcquireSpinLock(&RvhLock,&irql);
    if(state!=NULL)state->Detached=TRUE;
    for(link=RvhSessions.Flink;link!=&RvhSessions;link=link->Flink){RVH_SESSION *session=CONTAINING_RECORD(link,RVH_SESSION,Link);
        for(i=0;i<RVH_MAX_FILES;i++)if(session->Files[i].Id!=0&&session->Files[i].Instance==Objects->Instance)session->Files[i].Detached=TRUE;}
    KeReleaseSpinLock(&RvhLock,irql);if(state!=NULL)FltReleaseContext(state);ExReleaseRundownProtection(&RvhCalls);
}

static NTSTATUS RvhReadAltitude(PUNICODE_STRING RegistryPath,PCWSTR Suffix,PCWSTR Name,PUNICODE_STRING Value){
    WCHAR buffer[1024];UCHAR data[sizeof(KEY_VALUE_PARTIAL_INFORMATION)+512];UNICODE_STRING path,valueName;
    OBJECT_ATTRIBUTES attributes;HANDLE key=NULL;ULONG returned=0,i;NTSTATUS status;PKEY_VALUE_PARTIAL_INFORMATION information=(PKEY_VALUE_PARTIAL_INFORMATION)data;BOOLEAN point=FALSE;
    RtlZeroMemory(Value,sizeof(*Value));path.Buffer=buffer;path.Length=0;path.MaximumLength=sizeof(buffer);
    status=RtlAppendUnicodeStringToString(&path,RegistryPath);if(!NT_SUCCESS(status))return status;
    status=RtlAppendUnicodeToString(&path,Suffix);if(!NT_SUCCESS(status))return status;
    InitializeObjectAttributes(&attributes,&path,OBJ_CASE_INSENSITIVE|OBJ_KERNEL_HANDLE,NULL,NULL);
    status=ZwOpenKey(&key,KEY_QUERY_VALUE,&attributes);if(!NT_SUCCESS(status))return status;
    RtlInitUnicodeString(&valueName,Name);status=ZwQueryValueKey(key,&valueName,KeyValuePartialInformation,data,sizeof(data),&returned);ZwClose(key);
    if(!NT_SUCCESS(status))return status;
    if(information->Type!=REG_SZ||information->DataLength<2*sizeof(WCHAR)||information->DataLength>512||(information->DataLength%sizeof(WCHAR))!=0)return STATUS_INVALID_PARAMETER;
    Value->Length=(USHORT)(information->DataLength-sizeof(WCHAR));Value->MaximumLength=(USHORT)information->DataLength;
    Value->Buffer=ExAllocatePool2(POOL_FLAG_NON_PAGED,Value->MaximumLength,RVH_TAG);if(Value->Buffer==NULL)return STATUS_INSUFFICIENT_RESOURCES;
    RtlCopyMemory(Value->Buffer,information->Data,information->DataLength);
    if(Value->Buffer[Value->Length/sizeof(WCHAR)]!=L'\0')status=STATUS_INVALID_PARAMETER;
    for(i=0;NT_SUCCESS(status)&&i<Value->Length/sizeof(WCHAR);i++){
        WCHAR c=Value->Buffer[i];if(c>=L'0'&&c<=L'9')continue;
        if(c==L'.'&&!point&&i!=0&&i+1<Value->Length/sizeof(WCHAR)){point=TRUE;continue;}status=STATUS_INVALID_PARAMETER;
    }
    if(!NT_SUCCESS(status)){ExFreePoolWithTag(Value->Buffer,RVH_TAG);RtlZeroMemory(Value,sizeof(*Value));}return status;
}

static VOID RvhRetireSession(RVH_SESSION *Session){
    ULONG i;KIRQL irql;RVH_FILE_RECORD file;PEPROCESS process;BOOLEAN already;
    KeAcquireSpinLock(&RvhLock,&irql);already=Session->Closed;Session->Closed=TRUE;KeReleaseSpinLock(&RvhLock,irql);
    if(already){(VOID)KeWaitForSingleObject(&Session->Retired,Executive,KernelMode,FALSE,NULL);return;}
    /* Close publication before waiting; in-flight ADD rechecks Closed. */
    ExWaitForRundownProtectionRelease(&Session->Requests);
    for(i=0;i<RVH_MAX_FILES;i++){
        KeAcquireSpinLock(&RvhLock,&irql);file=Session->Files[i];RtlZeroMemory(&Session->Files[i],sizeof(Session->Files[i]));KeReleaseSpinLock(&RvhLock,irql);RvhDropFile(&file);
    }
    for(i=0;i<RVH_MAX_PROCESSES;i++){
        KeAcquireSpinLock(&RvhLock,&irql);process=Session->Processes[i].Process;RtlZeroMemory(&Session->Processes[i],sizeof(Session->Processes[i]));KeReleaseSpinLock(&RvhLock,irql);
        if(process!=NULL)ObDereferenceObject(process);
    }
    KeSetEvent(&Session->Retired,IO_NO_INCREMENT,FALSE);
}
static NTSTATUS RvhOpen(PIRP Irp,PIO_STACK_LOCATION Stack){
    RVH_SESSION *session;PEPROCESS owner;KIRQL irql;NTSTATUS status;
    if(Irp->RequestorMode!=UserMode||Stack->FileObject==NULL||Stack->FileObject->FileName.Length!=0)return STATUS_ACCESS_DENIED;
#if defined(_WIN64)
    if(IoIs32bitProcess(Irp))return STATUS_NOT_SUPPORTED;
#endif
    owner=IoGetRequestorProcess(Irp);if(owner==NULL||owner!=PsGetCurrentProcess())return STATUS_ACCESS_DENIED;
    session=ExAllocatePool2(POOL_FLAG_NON_PAGED,sizeof(*session),RVH_TAG);if(session==NULL)return STATUS_INSUFFICIENT_RESOURCES;
    ExInitializeRundownProtection(&session->Requests);KeInitializeEvent(&session->Retired,NotificationEvent,FALSE);session->Owner=owner;ObReferenceObject(owner);
    session->DeviceFile=Stack->FileObject;session->Id=RvhNextId();session->Unserved=RVH_BASE_GAPS;
    KeAcquireSpinLock(&RvhLock,&irql);
    if(RvhStopping||RvhSessionCount>=RVH_MAX_SESSIONS||Stack->FileObject->FsContext!=NULL)status=STATUS_DEVICE_BUSY;
    else{Stack->FileObject->FsContext=session;InsertTailList(&RvhSessions,&session->Link);RvhSessionCount++;status=STATUS_SUCCESS;}
    KeReleaseSpinLock(&RvhLock,irql);
    if(!NT_SUCCESS(status)){ObDereferenceObject(owner);ExFreePoolWithTag(session,RVH_TAG);}return status;
}
static NTSTATUS RvhControl(PIRP Irp,PIO_STACK_LOCATION Stack,PULONG_PTR Information){
    RVH_SESSION *session=NULL;RVH_REQUEST request;RVH_TARGET_REPLY target;RVH_SESSION_REPLY reply;RVH_RESPONSE response;
    ULONG code=Stack->Parameters.DeviceIoControl.IoControlCode,inSize=0,outSize=0;KIRQL irql;NTSTATUS status;
    if(Irp->RequestorMode!=UserMode||Stack->FileObject==NULL||KeGetCurrentIrql()!=PASSIVE_LEVEL)return STATUS_ACCESS_DENIED;
#if defined(_WIN64)
    if(IoIs32bitProcess(Irp))return STATUS_NOT_SUPPORTED;
#endif
    switch(code){
        case IOCTL_RVH_HELLO:case IOCTL_RVH_QUERY_SESSION:inSize=sizeof(RVH_REQUEST);outSize=sizeof(RVH_SESSION_REPLY);break;
        case IOCTL_RVH_PROCESS_ADD:inSize=sizeof(RVH_PROCESS_ADD_REQUEST);outSize=sizeof(RVH_TARGET_REPLY);break;
        case IOCTL_RVH_FILE_ADD:inSize=sizeof(RVH_FILE_ADD_REQUEST);outSize=sizeof(RVH_TARGET_REPLY);break;
        case IOCTL_RVH_QUERY_TARGET:inSize=sizeof(RVH_TARGET_REQUEST);outSize=sizeof(RVH_TARGET_REPLY);break;
        case IOCTL_RVH_REMOVE:inSize=sizeof(RVH_TARGET_REQUEST);outSize=sizeof(RVH_RESPONSE);break;
        default:return STATUS_INVALID_DEVICE_REQUEST;
    }
    if(Stack->Parameters.DeviceIoControl.InputBufferLength!=inSize||Stack->Parameters.DeviceIoControl.OutputBufferLength!=outSize||Irp->AssociatedIrp.SystemBuffer==NULL)return STATUS_INFO_LENGTH_MISMATCH;
    RtlCopyMemory(&request,Irp->AssociatedIrp.SystemBuffer,sizeof(request));
    if(request.Magic!=RVH_MAGIC||request.Version!=RVH_VERSION||request.Size!=inSize||request.Reserved!=0)return STATUS_REVISION_MISMATCH;
    KeAcquireSpinLock(&RvhLock,&irql);session=(RVH_SESSION *)Stack->FileObject->FsContext;
    if(session==NULL||session->Closed||session->OwnerExited||RvhStopping||session->DeviceFile!=Stack->FileObject||!ExAcquireRundownProtection(&session->Requests))session=NULL;
    KeReleaseSpinLock(&RvhLock,irql);if(session==NULL)return STATUS_FILE_CLOSED;
    if(IoGetRequestorProcess(Irp)!=session->Owner||PsGetCurrentProcess()!=session->Owner){status=STATUS_ACCESS_DENIED;goto Finished;}
    if((code==IOCTL_RVH_HELLO&&request.SessionId!=0)||(code!=IOCTL_RVH_HELLO&&request.SessionId!=session->Id)){status=STATUS_INVALID_HANDLE;goto Finished;}
    RtlZeroMemory(&target,sizeof(target));RtlZeroMemory(&reply,sizeof(reply));RtlZeroMemory(&response,sizeof(response));
    if(code==IOCTL_RVH_HELLO||code==IOCTL_RVH_QUERY_SESSION){RvhSessionReply(session,&reply);status=STATUS_SUCCESS;}
    else if(code==IOCTL_RVH_PROCESS_ADD)status=RvhAddProcess(session,(const RVH_PROCESS_ADD_REQUEST *)Irp->AssociatedIrp.SystemBuffer,&target);
    else if(code==IOCTL_RVH_FILE_ADD)status=RvhAddFile(session,(const RVH_FILE_ADD_REQUEST *)Irp->AssociatedIrp.SystemBuffer,&target);
    else status=RvhTarget(session,(const RVH_TARGET_REQUEST *)Irp->AssociatedIrp.SystemBuffer,&target,code==IOCTL_RVH_REMOVE);
    if(outSize==sizeof(reply)){RvhHeader(&reply.Header,&request,session,outSize,status);RtlCopyMemory(Irp->AssociatedIrp.SystemBuffer,&reply,outSize);}
    else if(outSize==sizeof(target)){RvhHeader(&target.Header,&request,session,outSize,status);RtlCopyMemory(Irp->AssociatedIrp.SystemBuffer,&target,outSize);}
    else{RvhHeader(&response,&request,session,outSize,status);RtlCopyMemory(Irp->AssociatedIrp.SystemBuffer,&response,outSize);}
    *Information=outSize;status=STATUS_SUCCESS;
Finished:
    ExReleaseRundownProtection(&session->Requests);return status;
}
static NTSTATUS RvhClose(PIO_STACK_LOCATION Stack,BOOLEAN Final){
    RVH_SESSION *session;KIRQL irql;
    if(Stack->FileObject==NULL)return STATUS_SUCCESS;
    KeAcquireSpinLock(&RvhLock,&irql);session=(RVH_SESSION *)Stack->FileObject->FsContext;KeReleaseSpinLock(&RvhLock,irql);
    if(session==NULL)return STATUS_SUCCESS;
    RvhRetireSession(session);
    if(Final){
        KeAcquireSpinLock(&RvhLock,&irql);Stack->FileObject->FsContext=NULL;RemoveEntryList(&session->Link);RvhSessionCount--;KeReleaseSpinLock(&RvhLock,irql);
        ObDereferenceObject(session->Owner);ExFreePoolWithTag(session,RVH_TAG);
    }
    return STATUS_SUCCESS;
}
static NTSTATUS RvhDispatch(PDEVICE_OBJECT Device,PIRP Irp){
    PIO_STACK_LOCATION stack=IoGetCurrentIrpStackLocation(Irp);ULONG_PTR information=0;NTSTATUS status;
    UNREFERENCED_PARAMETER(Device);
    if(!ExAcquireRundownProtection(&RvhCalls)){status=STATUS_DELETE_PENDING;goto Complete;}
    switch(stack->MajorFunction){
        case IRP_MJ_CREATE:status=RvhOpen(Irp,stack);break;
        case IRP_MJ_DEVICE_CONTROL:status=RvhControl(Irp,stack,&information);break;
        case IRP_MJ_CLEANUP:status=RvhClose(stack,FALSE);break;
        case IRP_MJ_CLOSE:status=RvhClose(stack,TRUE);break;
        default:status=STATUS_INVALID_DEVICE_REQUEST;break;
    }
    ExReleaseRundownProtection(&RvhCalls);
Complete:
    Irp->IoStatus.Status=status;Irp->IoStatus.Information=information;IoCompleteRequest(Irp,IO_NO_INCREMENT);return status;
}

static VOID RvhShutdown(VOID){
    UNICODE_STRING link;PFLT_FILTER filter;
    if(InterlockedExchange(&RvhShutdownStarted,1)!=0)return;
    InterlockedExchange(&RvhStopping,1);
    if(RvhLinkCreated){RtlInitUnicodeString(&link,RVH_DOS_DEVICE);IoDeleteSymbolicLink(&link);RvhLinkCreated=FALSE;}
    /* Blocks new callbacks and IOCTLs; waits for every actual in-flight user.
     * In particular, FILE_ADD cannot race FltUnregisterFilter. */
    ExWaitForRundownProtectionRelease(&RvhCalls);
    if(RvhObHandle!=NULL){ObUnRegisterCallbacks(RvhObHandle);RvhObHandle=NULL;}
    if(RvhNotifyRegistered){(VOID)PsSetCreateProcessNotifyRoutineEx(RvhProcessNotify,TRUE);RvhNotifyRegistered=FALSE;}
    filter=RvhFilter;RvhFilter=NULL;if(filter!=NULL)FltUnregisterFilter(filter);
    if(RvhObAltitude.Buffer!=NULL){ExFreePoolWithTag(RvhObAltitude.Buffer,RVH_TAG);RtlZeroMemory(&RvhObAltitude,sizeof(RvhObAltitude));}
    if(RvhDevice!=NULL){IoDeleteDevice(RvhDevice);RvhDevice=NULL;}
}
static VOID RvhDriverUnload(PDRIVER_OBJECT Driver){UNREFERENCED_PARAMETER(Driver);RvhShutdown();}
static NTSTATUS RvhFilterUnload(FLT_FILTER_UNLOAD_FLAGS Flags){
    KIRQL irql;BOOLEAN busy;UNREFERENCED_PARAMETER(Flags);
    KeAcquireSpinLock(&RvhLock,&irql);busy=RvhSessionCount!=0;
    if(!busy)InterlockedExchange(&RvhStopping,1);
    KeReleaseSpinLock(&RvhLock,irql);
    /* Service stop is disabled in FLT_REGISTRATION. The supported unload is
     * the nonmandatory Filter Manager path, after the actual device files close. */
    if(busy)return STATUS_FLT_DO_NOT_DETACH;
    RvhDriverUnload(RvhDriver);return STATUS_SUCCESS;
}

NTSTATUS DriverEntry(PDRIVER_OBJECT Driver,PUNICODE_STRING RegistryPath){
    OB_OPERATION_REGISTRATION operations[2]={0};OB_CALLBACK_REGISTRATION registration={0};
    UNICODE_STRING filterAltitude={0},deviceName,sddl,link;LARGE_INTEGER time;ULONG i;NTSTATUS status;
    KeInitializeSpinLock(&RvhLock);InitializeListHead(&RvhSessions);ExInitializeRundownProtection(&RvhCalls);
    KeQuerySystemTimePrecise(&time);RvhSequence=time.QuadPart;RvhDriver=Driver;
    for(i=0;i<=IRP_MJ_MAXIMUM_FUNCTION;i++)Driver->MajorFunction[i]=RvhDispatch;
    Driver->DriverUnload=RvhDriverUnload;
    RvhObStatus=RvhReadAltitude(RegistryPath,L"\\Parameters",L"ObAltitude",&RvhObAltitude);
    if(NT_SUCCESS(RvhObStatus)){
        operations[0].ObjectType=PsProcessType;operations[0].Operations=OB_OPERATION_HANDLE_CREATE|OB_OPERATION_HANDLE_DUPLICATE;operations[0].PreOperation=RvhObjectPre;
        operations[1].ObjectType=PsThreadType;operations[1].Operations=OB_OPERATION_HANDLE_CREATE|OB_OPERATION_HANDLE_DUPLICATE;operations[1].PreOperation=RvhObjectPre;
        registration.Version=OB_FLT_REGISTRATION_VERSION;registration.OperationRegistrationCount=RTL_NUMBER_OF(operations);
        registration.Altitude=RvhObAltitude;registration.OperationRegistration=operations;
        RvhObStatus=ObRegisterCallbacks(&registration,&RvhObHandle);
    }
    RvhNotifyStatus=PsSetCreateProcessNotifyRoutineEx(RvhProcessNotify,FALSE);RvhNotifyRegistered=NT_SUCCESS(RvhNotifyStatus);
    RvhFilterStatus=RvhReadAltitude(RegistryPath,L"\\Parameters\\Instances\\RonovaHost Instance",L"Altitude",&filterAltitude);
    /* A registered minifilter is also the supported, nonmandatory unload
     * endpoint. Do not publish a control device that can only be stopped
     * through the forced SCM path after an altitude/register/start failure. */
    if(!NT_SUCCESS(RvhFilterStatus)){RvhShutdown();return RvhFilterStatus;}
    ExFreePoolWithTag(filterAltitude.Buffer,RVH_TAG);
    RvhFilterStatus=FltRegisterFilter(Driver,&RvhRegistration,&RvhFilter);
    if(!NT_SUCCESS(RvhFilterStatus)){RvhShutdown();return RvhFilterStatus;}
    RvhFilterStartStatus=FltStartFiltering(RvhFilter);
    if(!NT_SUCCESS(RvhFilterStartStatus)){RvhShutdown();return RvhFilterStartStatus;}
    RtlInitUnicodeString(&deviceName,RVH_NT_DEVICE);RtlInitUnicodeString(&sddl,L"D:P(A;;GA;;;SY)(A;;GA;;;BA)");
    status=IoCreateDeviceSecure(Driver,0,&deviceName,RVH_DEVICE_TYPE,FILE_DEVICE_SECURE_OPEN,FALSE,&sddl,&RvhDeviceClass,&RvhDevice);
    if(!NT_SUCCESS(status)){RvhShutdown();return status;}
    RvhDevice->Flags|=DO_BUFFERED_IO;
    RtlInitUnicodeString(&link,RVH_DOS_DEVICE);status=IoCreateSymbolicLink(&link,&deviceName);
    if(!NT_SUCCESS(status)){RvhShutdown();return status;}
    RvhLinkCreated=TRUE;RvhDevice->Flags&=~DO_DEVICE_INITIALIZING;
    return STATUS_SUCCESS;
}
