#ifndef RONOVA_HOST_PROTOCOL_H
#define RONOVA_HOST_PROTOCOL_H

/* Shared x64 user/kernel ABI. Handles are real, local user handles, never PIDs.
 * Scope is opaque metadata supplied by the existing exact Host owner record;
 * it grants no authority. Every operation is bound to the opened FILE_OBJECT's
 * original process and session. Semantic errors are Response.Status (NTSTATUS).
 * IOCTL transport failure produces no usable response or target receipt. */
#if defined(_KERNEL_MODE)
#include <ntdef.h>
#include <devioctl.h>
#else
#include <windows.h>
#include <winioctl.h>
#endif

#define RVH_MAGIC 0x31485652UL
#define RVH_VERSION 1UL
#define RVH_DEVICE_TYPE 0x8337UL
#define RVH_WIN32_DEVICE L"\\\\.\\RonovaHost"
#define RVH_NT_DEVICE L"\\Device\\RonovaHost"
#define RVH_DOS_DEVICE L"\\DosDevices\\RonovaHost"
#define RVH_MAX_PROCESSES 64UL
#define RVH_MAX_FILES 64UL
#define RVH_MAX_SESSIONS 16UL
#define RVH_IOCTL(function) CTL_CODE(RVH_DEVICE_TYPE,function,METHOD_BUFFERED,FILE_READ_DATA|FILE_WRITE_DATA)
#define IOCTL_RVH_HELLO RVH_IOCTL(0x800)
#define IOCTL_RVH_PROCESS_ADD RVH_IOCTL(0x801)
#define IOCTL_RVH_FILE_ADD RVH_IOCTL(0x802)
#define IOCTL_RVH_REMOVE RVH_IOCTL(0x803)
#define IOCTL_RVH_QUERY_TARGET RVH_IOCTL(0x804)
#define IOCTL_RVH_QUERY_SESSION RVH_IOCTL(0x805)
#define IOCTL_RVH_OWNER_PROTECT RVH_IOCTL(0x806)

#define RVH_TARGET_PROCESS 1UL
#define RVH_TARGET_FILE 2UL
#define RVH_TARGET_OWNER_PROCESS 3UL
#define RVH_OWNER_PROTECT_OUTSIDE_HANDLES 0x00000001UL
#define RVH_OWNER_ALLOWED_FLAGS 0x00000001UL
#define RVH_PROCESS_PROTECT_OUTSIDE_HANDLES 0x00000001UL
#define RVH_PROCESS_DENY_CHILD_CREATION 0x00000002UL
#define RVH_PROCESS_ALLOWED_FLAGS 0x00000003UL
#define RVH_FILE_DENY_READ 0x00000001UL
#define RVH_FILE_DENY_WRITE 0x00000002UL
#define RVH_FILE_DENY_NEW_EXECUTE_OPEN 0x00000004UL
#define RVH_FILE_ALLOWED_FLAGS 0x00000007UL

#define RVH_CAP_DEVICE_SESSION 0x0000000000000001ULL
#define RVH_CAP_PROCESS_OBJECT_BIND 0x0000000000000002ULL
#define RVH_CAP_OUTSIDE_HANDLE_RIGHTS 0x0000000000000004ULL
#define RVH_CAP_CHILD_CREATION_DENIAL 0x0000000000000008ULL
#define RVH_CAP_EXACT_FILE_BIND 0x0000000000000010ULL
#define RVH_CAP_FILE_READ_WRITE 0x0000000000000020ULL
#define RVH_CAP_NEW_EXECUTE_OPEN_DENIAL 0x0000000000000040ULL
#define RVH_CAP_OWNER_PROCESS_HANDLES 0x0000000000000080ULL

/* These paths are explicitly not controlled by this driver version. */
#define RVH_GAP_SHARED_JVM_MOD_IDENTITY 0x0000000000000001ULL
#define RVH_GAP_PREEXISTING_PROCESS_THREAD_HANDLES 0x0000000000000002ULL
#define RVH_GAP_HANDLE_INHERITANCE 0x0000000000000004ULL
#define RVH_GAP_EXISTING_SECTIONS 0x0000000000000008ULL
#define RVH_GAP_MAPPED_IO 0x0000000000000010ULL
#define RVH_GAP_KERNEL_MODE_IO 0x0000000000000020ULL
#define RVH_GAP_PAGING_IO 0x0000000000000040ULL
#define RVH_GAP_OLD_FILE_OBJECT_ALIASES 0x0000000000000080ULL
#define RVH_GAP_CREATE_SIDE_EFFECTS 0x0000000000000100ULL
#define RVH_GAP_IMAGE_NOTIFY_CANNOT_DENY 0x0000000000000200ULL
#define RVH_GAP_FILE_ID_UNAVAILABLE 0x0000000000000400ULL
#define RVH_GAP_OPEN_ADMISSION_UNAVAILABLE 0x0000000000000800ULL
#define RVH_GAP_FILE_CONTROL_SOURCE_UNAVAILABLE 0x0000000000001000ULL
#define RVH_GAP_PREEXISTING_OFFLOAD_TOKENS 0x0000000000002000ULL
#define RVH_GAP_KERNEL_HANDLES 0x0000000000004000ULL
#define RVH_BASE_GAPS 0x00000000000063FFULL

#define RVH_DRIVER_LOADED 1UL
#define RVH_DRIVER_UNLOADING 2UL
#define RVH_TARGET_ACTIVE 0x00000001UL
#define RVH_TARGET_PROCESS_EXITED 0x00000002UL
#define RVH_TARGET_VOLUME_DETACHED 0x00000004UL
#define RVH_TARGET_OWNER_EXITED 0x00000008UL
#define RVH_TARGET_DISABLED 0x00000010UL

#pragma pack(push,8)
typedef struct RVH_SCOPE {
    ULONGLONG Low;
    ULONGLONG High;
} RVH_SCOPE;

typedef struct RVH_REQUEST {
    ULONG Magic;
    ULONG Version;
    ULONG Size;
    ULONG Reserved;
    ULONGLONG SessionId; /* zero only for HELLO */
    ULONGLONG RequestId; /* echoed; not an authorization token */
} RVH_REQUEST;

typedef struct RVH_RESPONSE {
    ULONG Magic;
    ULONG Version;
    ULONG Size;
    LONG Status;
    ULONGLONG SessionId;
    ULONGLONG RequestId;
} RVH_RESPONSE;

typedef struct RVH_SESSION_REPLY {
    RVH_RESPONSE Header;
    ULONGLONG Capabilities;
    ULONGLONG Unserved;
    ULONG ProcessCount;
    ULONG FileCount;
    ULONG MaxProcesses;
    ULONG MaxFiles;
    LONG ObRegisterStatus;
    LONG ProcessNotifyStatus;
    LONG FilterRegisterStatus;
    LONG FilterStartStatus;
    ULONG DriverState;
    ULONG Reserved;
} RVH_SESSION_REPLY;

typedef struct RVH_PROCESS_ADD_REQUEST {
    RVH_REQUEST Header;
    ULONGLONG ProcessHandle; /* QUERY_LIMITED_INFORMATION | SET_INFORMATION */
    RVH_SCOPE Scope;
    ULONG Effects;
    ULONG Reserved;
} RVH_PROCESS_ADD_REQUEST;

/* This separate rule always targets the FILE_OBJECT's referenced owner process.
 * No supplied process HANDLE, PID, Scope or Java module can select its object.
 * Zero Effects withdraws this session's rule; it never upgrades old handles. */
typedef struct RVH_OWNER_PROTECT_REQUEST {
    RVH_REQUEST Header;
    ULONG Effects;
    ULONG Reserved;
} RVH_OWNER_PROTECT_REQUEST;

typedef struct RVH_FILE_ADD_REQUEST {
    RVH_REQUEST Header;
    ULONGLONG FileHandle; /* FILE_READ_ATTRIBUTES; an open regular file */
    ULONGLONG ActorTargetId; /* exact process target in this session and Scope */
    RVH_SCOPE Scope;
    ULONG Effects;
    ULONG Reserved;
} RVH_FILE_ADD_REQUEST;

typedef struct RVH_TARGET_REQUEST {
    RVH_REQUEST Header;
    ULONG Kind;
    ULONG Reserved;
    ULONGLONG TargetId;
} RVH_TARGET_REQUEST;

/* ADD and QUERY_TARGET have this fixed response. ProcessId is observation only;
 * the kernel holds referenced objects and never looks a target up by this ID. */
typedef struct RVH_TARGET_REPLY {
    RVH_RESPONSE Header;
    ULONG Kind;
    ULONG Effects;
    ULONGLONG TargetId;
    RVH_SCOPE Scope;
    ULONGLONG ActorTargetId;
    ULONGLONG VolumeSerialNumber;
    UCHAR FileId[16];
    ULONGLONG ObservedProcessId;
    ULONG State;
    ULONG Reserved;
    ULONGLONG HandleRightsReduced;
    ULONGLONG ChildCreationsDenied;
    ULONGLONG FileOperationsDenied;
    ULONGLONG SectionAttemptsObserved;
    ULONGLONG UnservedObservations;
} RVH_TARGET_REPLY;
#pragma pack(pop)

typedef char rvh_size_scope[(sizeof(RVH_SCOPE)==16)?1:-1];
typedef char rvh_size_request[(sizeof(RVH_REQUEST)==32)?1:-1];
typedef char rvh_size_response[(sizeof(RVH_RESPONSE)==32)?1:-1];
typedef char rvh_size_session_reply[(sizeof(RVH_SESSION_REPLY)==88)?1:-1];
typedef char rvh_size_process_add[(sizeof(RVH_PROCESS_ADD_REQUEST)==64)?1:-1];
typedef char rvh_size_owner_protect[(sizeof(RVH_OWNER_PROTECT_REQUEST)==40)?1:-1];
typedef char rvh_size_file_add[(sizeof(RVH_FILE_ADD_REQUEST)==72)?1:-1];
typedef char rvh_size_target_request[(sizeof(RVH_TARGET_REQUEST)==48)?1:-1];
typedef char rvh_size_target_reply[(sizeof(RVH_TARGET_REPLY)==152)?1:-1];

#endif
