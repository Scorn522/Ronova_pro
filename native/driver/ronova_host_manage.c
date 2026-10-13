/* Explicit, x64 developer deployment entry for the fixed RonovaHost package.
 * Nothing invokes this tool from the game. No target process/file commands,
 * service-stop fallback, certificate creation or TestSigning. Only a selected
 * modifying command may request normal Windows UAC consent for this same EXE.
 * Primitive package changes use DiInstallDriver/DiUninstallDriver; unload uses
 * FilterUnload, the user-mode equivalent of nonmandatory FltUnloadFilter.
 */
#include <windows.h>
#include <shellapi.h>
#include <objbase.h>
#include <setupapi.h>
#include <newdev.h>
#include <fltuser.h>
#include <strsafe.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wchar.h>
#include "ronova_host_protocol.h"

#if defined(__MINGW32__)
/* Older MinGW headers omit these supported Windows entry points. Signatures
 * match Windows SDK 10.0.26100.0 setupapi.h/newdev.h; link the SDK import libs.
 * The MSVC/WDK build uses its headers without these compatibility declarations. */
__declspec(dllimport) BOOL WINAPI SetupGetInfDriverStoreLocationW(
    PCWSTR FileName,PSP_ALTPLATFORM_INFO AlternatePlatformInfo,PCWSTR LocaleName,
    PWSTR ReturnBuffer,DWORD ReturnBufferSize,PDWORD RequiredSize);
__declspec(dllimport) BOOL WINAPI SetupGetInfPublishedNameW(
    PCWSTR DriverStoreLocation,PWSTR ReturnBuffer,DWORD ReturnBufferSize,PDWORD RequiredSize);
__declspec(dllimport) BOOL WINAPI DiUninstallDriverW(
    HWND hwndParent,LPCWSTR InfPath,DWORD Flags,PBOOL NeedReboot);
#endif

#if !defined(_WIN64)
#error RonovaHostManage must be built for x64.
#endif

#define RVH_SERVICE_NAME L"RonovaHost"
#define RVH_INF_NAME L"RonovaHost.inf"
#define RVH_SYS_NAME L"RonovaHost.sys"
#define RVH_CAT_NAME L"RonovaHost.cat"
#define RVH_INSTALL_SECTION L"DefaultInstall.NTamd64.10.0...19041"
#define RVH_SERVICES_SECTION L"DefaultInstall.NTamd64.10.0...19041.Services"
#define RVH_SERVICE_SECTION L"RonovaHost.Service"
#define RVH_ALTITUDE_CHARS 256
#define RVH_COMMAND_LINE_CHARS 32767
#define RVH_ELEVATED_CHILD L"--elevated-child"

typedef struct RVH_PACKAGE_INFO {
    WCHAR Inf[MAX_PATH];
    WCHAR ObAltitude[RVH_ALTITUDE_CHARS],FilterAltitude[RVH_ALTITUDE_CHARS];
    WCHAR DriverVersion[64],DriverDate[32];
    ULONGLONG Version,Date;
} RVH_PACKAGE_INFO;

typedef struct RVH_SERVICE_INFO {
    SC_HANDLE Manager,Service;
    QUERY_SERVICE_CONFIGW *Config;
    SERVICE_STATUS_PROCESS Status;
    BOOL Present;
    WCHAR Image[MAX_PATH],StoreInf[MAX_PATH],PublishedInf[MAX_PATH];
    RVH_PACKAGE_INFO Package;
} RVH_SERVICE_INFO;

typedef struct RVH_PRIVILEGE {
    HANDLE Token;
    TOKEN_PRIVILEGES Previous;
    BOOL Changed;
} RVH_PRIVILEGE;

static int RvhWin32Error(PCWSTR Stage,DWORD Error){
    WCHAR message[1024]={0};DWORD length;
    length=FormatMessageW(FORMAT_MESSAGE_FROM_SYSTEM|FORMAT_MESSAGE_IGNORE_INSERTS,NULL,Error,0,message,ARRAYSIZE(message),NULL);
    while(length!=0&&(message[length-1]==L'\r'||message[length-1]==L'\n'))message[--length]=L'\0';
    fwprintf(stderr,L"Stage=%ls Win32=%lu (0x%08lX) Message=%ls\n",Stage,Error,Error,length?message:L"No system message available");
    SetLastError(Error);return 0;
}
static int RvhHresultError(PCWSTR Stage,HRESULT Result){
    DWORD error=HRESULT_FACILITY(Result)==FACILITY_WIN32?HRESULT_CODE(Result):ERROR_GEN_FAILURE;
    fwprintf(stderr,L"Stage=%ls HRESULT=0x%08lX\n",Stage,(ULONG)Result);
    return RvhWin32Error(Stage,error);
}
static int RvhPathCopy(PWSTR Out,size_t Count,PCWSTR Value){
    if(FAILED(StringCchCopyW(Out,Count,Value)))return RvhWin32Error(L"path-length",ERROR_FILENAME_EXCED_RANGE);
    return 1;
}
static int RvhAbsoluteFile(PCWSTR Input,PWSTR Out){
    DWORD length,attributes;
    if(Input==NULL||!((Input[0]>=L'A'&&Input[0]<=L'Z')||(Input[0]>=L'a'&&Input[0]<=L'z'))
            ||Input[1]!=L':'||Input[2]!=L'\\')return RvhWin32Error(L"absolute-local-path-required",ERROR_BAD_PATHNAME);
    length=GetFullPathNameW(Input,MAX_PATH,Out,NULL);
    if(length==0)return RvhWin32Error(L"full-path",GetLastError());
    if(length>=MAX_PATH)return RvhWin32Error(L"full-path",ERROR_FILENAME_EXCED_RANGE);
    attributes=GetFileAttributesW(Out);
    if(attributes==INVALID_FILE_ATTRIBUTES)return RvhWin32Error(L"package-file",GetLastError());
    if((attributes&FILE_ATTRIBUTE_DIRECTORY)!=0)return RvhWin32Error(L"regular-file-required",ERROR_DIRECTORY);
    return 1;
}
static int RvhSibling(PCWSTR Path,PCWSTR Name,PWSTR Out){
    WCHAR *slash;
    if(!RvhPathCopy(Out,MAX_PATH,Path))return 0;
    slash=wcsrchr(Out,L'\\');if(slash==NULL)return RvhWin32Error(L"package-directory",ERROR_BAD_PATHNAME);
    slash[1]=L'\0';
    if(FAILED(StringCchCatW(Out,MAX_PATH,Name)))return RvhWin32Error(L"package-directory",ERROR_FILENAME_EXCED_RANGE);
    return 1;
}
static int RvhSameFile(PCWSTR Left,PCWSTR Right){
    FILE_ID_INFO leftId,rightId;HANDLE left,right;DWORD error=ERROR_SUCCESS;
    left=CreateFileW(Left,FILE_READ_ATTRIBUTES,FILE_SHARE_READ|FILE_SHARE_WRITE|FILE_SHARE_DELETE,NULL,OPEN_EXISTING,FILE_ATTRIBUTE_NORMAL,NULL);
    if(left==INVALID_HANDLE_VALUE)return RvhWin32Error(L"installed-file-identity",GetLastError());
    right=CreateFileW(Right,FILE_READ_ATTRIBUTES,FILE_SHARE_READ|FILE_SHARE_WRITE|FILE_SHARE_DELETE,NULL,OPEN_EXISTING,FILE_ATTRIBUTE_NORMAL,NULL);
    if(right==INVALID_HANDLE_VALUE){error=GetLastError();CloseHandle(left);return RvhWin32Error(L"installed-file-identity",error);}
    if(!GetFileInformationByHandleEx(left,FileIdInfo,&leftId,sizeof(leftId))
            ||!GetFileInformationByHandleEx(right,FileIdInfo,&rightId,sizeof(rightId)))error=GetLastError();
    else if(leftId.VolumeSerialNumber!=rightId.VolumeSerialNumber
            ||memcmp(leftId.FileId.Identifier,rightId.FileId.Identifier,sizeof(leftId.FileId.Identifier))!=0)error=ERROR_NOT_SAME_OBJECT;
    CloseHandle(right);CloseHandle(left);
    return error==ERROR_SUCCESS?1:RvhWin32Error(L"installed-file-identity",error);
}
static int RvhAltitude(PCWSTR Value){
    size_t i,length=wcslen(Value);BOOL point=FALSE;
    if(length==0||length>254)return 0;
    for(i=0;i<length;i++){
        if(Value[i]>=L'0'&&Value[i]<=L'9')continue;
        if(Value[i]==L'.'&&!point&&i!=0&&i+1<length){point=TRUE;continue;}
        return 0;
    }
    return 1;
}
static int RvhInfString(HINF Inf,PCWSTR Section,PCWSTR Key,DWORD Index,PWSTR Out,DWORD Count){
    INFCONTEXT context;
    if(!SetupFindFirstLineW(Inf,Section,Key,&context)||!SetupGetStringFieldW(&context,Index,Out,Count,NULL))
        return RvhWin32Error(L"package-inf-field",GetLastError());
    return 1;
}
static int RvhInfExpect(HINF Inf,PCWSTR Section,PCWSTR Key,DWORD Index,PCWSTR Expected){
    WCHAR value[MAX_PATH];
    if(!RvhInfString(Inf,Section,Key,Index,value,ARRAYSIZE(value)))return 0;
    if(_wcsicmp(value,Expected)!=0){
        fwprintf(stderr,L"Section=%ls Key=%ls Expected=%ls Actual=%ls\n",Section,Key,Expected,value);
        return RvhWin32Error(L"RonovaHost-package-identity",ERROR_INVALID_DATA);
    }
    return 1;
}
static int RvhInfInteger(HINF Inf,PCWSTR Section,PCWSTR Key,int Expected){
    INFCONTEXT context;int value;
    if(!SetupFindFirstLineW(Inf,Section,Key,&context)||!SetupGetIntField(&context,1,&value))
        return RvhWin32Error(L"package-inf-number",GetLastError());
    return value==Expected?1:RvhWin32Error(L"RonovaHost-package-identity",ERROR_INVALID_DATA);
}
static int RvhVersion(RVH_PACKAGE_INFO *Package){
    WCHAR *next,*cursor=Package->DriverVersion;ULONG i;ULONGLONG version=0;unsigned month,day,year;WCHAR extra;
    SYSTEMTIME date={0};FILETIME fileTime;
    for(i=0;i<4;i++){
        unsigned long part;
        if(*cursor<L'0'||*cursor>L'9')return RvhWin32Error(L"DriverVer-version",ERROR_INVALID_DATA);
        part=wcstoul(cursor,&next,10);
        if(part>65535||(i==3?*next!=L'\0':*next!=L'.'))return RvhWin32Error(L"DriverVer-version",ERROR_INVALID_DATA);
        version=(version<<16)|part;cursor=next+1;
    }
    if(swscanf_s(Package->DriverDate,L"%u/%u/%u%lc",&month,&day,&year,&extra,1)!=3
            ||month>12||month==0||day>31||day==0||year<1601||year>9999)
        return RvhWin32Error(L"DriverVer-date",ERROR_INVALID_DATA);
    date.wYear=(WORD)year;date.wMonth=(WORD)month;date.wDay=(WORD)day;
    if(!SystemTimeToFileTime(&date,&fileTime))return RvhWin32Error(L"DriverVer-date",GetLastError());
    Package->Version=version;Package->Date=((ULONGLONG)fileTime.dwHighDateTime<<32)|fileTime.dwLowDateTime;return 1;
}
static int RvhReadPackage(PCWSTR Path,RVH_PACKAGE_INFO *Out){
    HINF inf;UINT line;WCHAR raw[MAX_PATH];int ready=0;
    ZeroMemory(Out,sizeof(*Out));if(!RvhAbsoluteFile(Path,Out->Inf))return 0;
    inf=SetupOpenInfFileW(Out->Inf,NULL,INF_STYLE_WIN4,&line);
    if(inf==INVALID_HANDLE_VALUE)return RvhWin32Error(L"open-package-inf",GetLastError());
    if(SetupGetLineCountW(inf,L"Manufacturer")>=0||SetupGetLineCountW(inf,L"DefaultInstall")>=0
            ||SetupGetLineCountW(inf,L"DefaultUninstall")>=0){RvhWin32Error(L"primitive-x64-package-required",ERROR_INVALID_DATA);goto Finished;}
    if(!RvhInfExpect(inf,L"Version",L"Signature",1,L"$WINDOWS NT$")
            ||!RvhInfExpect(inf,L"Version",L"Provider",1,L"Ronova Pro")
            ||!RvhInfExpect(inf,L"Version",L"Class",1,L"SecurityEnhancer")
            ||!RvhInfExpect(inf,L"Version",L"ClassGuid",1,L"{d02bc3da-0c8e-4945-9bd5-f1883c226c8c}")
            ||!RvhInfExpect(inf,L"Version",L"CatalogFile",1,RVH_CAT_NAME)
            ||!RvhInfInteger(inf,L"Version",L"PnpLockdown",1)
            ||!RvhInfExpect(inf,RVH_INSTALL_SECTION,L"CopyFiles",1,L"RonovaHost.DriverFiles")
            ||!RvhInfExpect(inf,RVH_SERVICES_SECTION,L"AddService",1,RVH_SERVICE_NAME)
            ||!RvhInfExpect(inf,RVH_SERVICES_SECTION,L"AddService",2,L"")
            ||!RvhInfExpect(inf,RVH_SERVICES_SECTION,L"AddService",3,RVH_SERVICE_SECTION)
            ||!RvhInfInteger(inf,RVH_SERVICE_SECTION,L"ServiceType",SERVICE_FILE_SYSTEM_DRIVER)
            ||!RvhInfInteger(inf,RVH_SERVICE_SECTION,L"StartType",SERVICE_DEMAND_START)
            ||!RvhInfExpect(inf,RVH_SERVICE_SECTION,L"Dependencies",1,L"FltMgr")
            ||!RvhInfExpect(inf,RVH_SERVICE_SECTION,L"LoadOrderGroup",1,L"FSFilter Security Enhancer")
            ||!RvhInfExpect(inf,RVH_SERVICE_SECTION,L"AddReg",1,L"RonovaHost.Registry")
            ||!RvhInfInteger(inf,L"DestinationDirs",L"RonovaHost.DriverFiles",13))goto Finished;
    /* Read the literal dirid before SetupAPI expands it to an install path. */
    if(GetPrivateProfileStringW(RVH_SERVICE_SECTION,L"ServiceBinary",L"",raw,ARRAYSIZE(raw),Out->Inf)==0
            ||_wcsicmp(raw,L"%13%\\RonovaHost.sys")!=0){RvhWin32Error(L"DriverStore-service-binary-required",ERROR_INVALID_DATA);goto Finished;}
    if(!RvhInfString(inf,L"Strings",L"ObAltitude",1,Out->ObAltitude,ARRAYSIZE(Out->ObAltitude))
            ||!RvhInfString(inf,L"Strings",L"FilterAltitude",1,Out->FilterAltitude,ARRAYSIZE(Out->FilterAltitude)))goto Finished;
    if(!RvhAltitude(Out->ObAltitude)||!RvhAltitude(Out->FilterAltitude)){RvhWin32Error(L"actual-assigned-altitudes-required",ERROR_INVALID_DATA);goto Finished;}
    if(!RvhInfString(inf,L"Version",L"DriverVer",1,Out->DriverDate,ARRAYSIZE(Out->DriverDate))
            ||!RvhInfString(inf,L"Version",L"DriverVer",2,Out->DriverVersion,ARRAYSIZE(Out->DriverVersion))||!RvhVersion(Out))goto Finished;
    ready=1;
Finished:
    SetupCloseInfFile(inf);return ready;
}
static void RvhReleaseService(RVH_SERVICE_INFO *Service){
    if(Service->Config!=NULL)HeapFree(GetProcessHeap(),0,Service->Config);
    if(Service->Service!=NULL)CloseServiceHandle(Service->Service);
    if(Service->Manager!=NULL)CloseServiceHandle(Service->Manager);
    ZeroMemory(Service,sizeof(*Service));
}
static int RvhServiceImage(PCWSTR Configured,PWSTR Out){
    WCHAR input[MAX_PATH],expanded[MAX_PATH],windows[MAX_PATH];size_t length;DWORD count;
    if(!RvhPathCopy(input,ARRAYSIZE(input),Configured))return 0;
    length=wcslen(input);
    if(length>1&&input[0]==L'"'&&input[length-1]==L'"'){input[length-1]=L'\0';MoveMemory(input,input+1,(length-1)*sizeof(WCHAR));}
    count=ExpandEnvironmentStringsW(input,expanded,ARRAYSIZE(expanded));
    if(count==0)return RvhWin32Error(L"service-image-path",GetLastError());
    if(count>ARRAYSIZE(expanded))return RvhWin32Error(L"service-image-path",ERROR_FILENAME_EXCED_RANGE);
    if(_wcsnicmp(expanded,L"\\SystemRoot\\",12)==0||_wcsnicmp(expanded,L"System32\\",9)==0){
        PCWSTR relative=expanded[0]==L'\\'?expanded+12:expanded;
        count=GetWindowsDirectoryW(windows,ARRAYSIZE(windows));
        if(count==0||count>=ARRAYSIZE(windows))return RvhWin32Error(L"Windows-directory",count?ERROR_FILENAME_EXCED_RANGE:GetLastError());
        if(FAILED(StringCchPrintfW(input,ARRAYSIZE(input),L"%ls\\%ls",windows,relative)))return RvhWin32Error(L"service-image-path",ERROR_FILENAME_EXCED_RANGE);
    }else if(!RvhPathCopy(input,ARRAYSIZE(input),_wcsnicmp(expanded,L"\\??\\",4)==0?expanded+4:expanded))return 0;
    return RvhAbsoluteFile(input,Out);
}
static int RvhRegistryAltitude(PCWSTR Subkey,PCWSTR Name,PCWSTR Expected){
    WCHAR value[RVH_ALTITUDE_CHARS];DWORD size=sizeof(value);LSTATUS error;
    error=RegGetValueW(HKEY_LOCAL_MACHINE,Subkey,Name,RRF_RT_REG_SZ,NULL,value,&size);
    if(error!=ERROR_SUCCESS)return RvhWin32Error(L"installed-altitude",error);
    return RvhAltitude(value)&&wcscmp(value,Expected)==0?1:RvhWin32Error(L"installed-altitude-mismatch",ERROR_INVALID_DATA);
}
static int RvhReadService(RVH_SERVICE_INFO *Out){
    DWORD needed=0,error;WCHAR storeBinary[MAX_PATH],candidateInf[MAX_PATH];PCWSTR name;
    ZeroMemory(Out,sizeof(*Out));
    Out->Manager=OpenSCManagerW(NULL,NULL,SC_MANAGER_CONNECT);
    if(Out->Manager==NULL)return RvhWin32Error(L"SCM-query-open",GetLastError());
    Out->Service=OpenServiceW(Out->Manager,RVH_SERVICE_NAME,SERVICE_QUERY_CONFIG|SERVICE_QUERY_STATUS);
    if(Out->Service==NULL){error=GetLastError();if(error==ERROR_SERVICE_DOES_NOT_EXIST)return 1;return RvhWin32Error(L"RonovaHost-service-query",error);}
    Out->Present=TRUE;
    if(!QueryServiceStatusEx(Out->Service,SC_STATUS_PROCESS_INFO,(LPBYTE)&Out->Status,sizeof(Out->Status),&needed))
        return RvhWin32Error(L"service-state",GetLastError());
    if(QueryServiceConfigW(Out->Service,NULL,0,&needed)||GetLastError()!=ERROR_INSUFFICIENT_BUFFER)
        return RvhWin32Error(L"service-configuration",GetLastError());
    Out->Config=HeapAlloc(GetProcessHeap(),HEAP_ZERO_MEMORY,needed);
    if(Out->Config==NULL)return RvhWin32Error(L"service-configuration",ERROR_NOT_ENOUGH_MEMORY);
    if(!QueryServiceConfigW(Out->Service,Out->Config,needed,&needed))return RvhWin32Error(L"service-configuration",GetLastError());
    if(Out->Config->dwServiceType!=SERVICE_FILE_SYSTEM_DRIVER||Out->Config->dwStartType!=SERVICE_DEMAND_START
            ||_wcsicmp(Out->Config->lpLoadOrderGroup,L"FSFilter Security Enhancer")!=0
            ||_wcsicmp(Out->Config->lpDependencies,L"FltMgr")!=0||Out->Config->lpDependencies[wcslen(L"FltMgr")+1]!=L'\0')
        return RvhWin32Error(L"fixed-demand-start-service-required",ERROR_INVALID_DATA);
    if(!RvhServiceImage(Out->Config->lpBinaryPathName,Out->Image))return 0;
    name=wcsrchr(Out->Image,L'\\');
    if(name==NULL||_wcsicmp(name+1,RVH_SYS_NAME)!=0)return RvhWin32Error(L"RonovaHost-service-image",ERROR_INVALID_DATA);
    if(!RvhSibling(Out->Image,RVH_INF_NAME,candidateInf))return 0;
    if(!SetupGetInfDriverStoreLocationW(candidateInf,NULL,NULL,Out->StoreInf,ARRAYSIZE(Out->StoreInf),NULL))
        return RvhWin32Error(L"installed-DriverStore-package",GetLastError());
    if(!SetupGetInfPublishedNameW(Out->StoreInf,Out->PublishedInf,ARRAYSIZE(Out->PublishedInf),NULL))
        return RvhWin32Error(L"installed-published-inf",GetLastError());
    if(!RvhSibling(Out->StoreInf,RVH_SYS_NAME,storeBinary)||!RvhSameFile(Out->Image,storeBinary)||!RvhReadPackage(Out->StoreInf,&Out->Package))return 0;
    if(!RvhRegistryAltitude(L"SYSTEM\\CurrentControlSet\\Services\\RonovaHost\\Parameters",L"ObAltitude",Out->Package.ObAltitude)
            ||!RvhRegistryAltitude(L"SYSTEM\\CurrentControlSet\\Services\\RonovaHost\\Parameters\\Instances\\RonovaHost Instance",L"Altitude",Out->Package.FilterAltitude)
            ||!RvhRegistryAltitude(L"SYSTEM\\CurrentControlSet\\Services\\RonovaHost\\Instances\\RonovaHost Instance",L"Altitude",Out->Package.FilterAltitude))return 0;
    return 1;
}
static void RvhPrintService(const RVH_SERVICE_INFO *Service){
    wprintf(L"ServicePresent=%lu\n",(ULONG)Service->Present);
    if(!Service->Present)return;
    wprintf(L"ServiceState=%lu ServiceWin32Exit=%lu ServiceSpecificExit=%lu StartType=%lu\n",Service->Status.dwCurrentState,
        Service->Status.dwWin32ExitCode,Service->Status.dwServiceSpecificExitCode,Service->Config->dwStartType);
    wprintf(L"ServiceImage=%ls\nInstalledInf=%ls\nDriverStoreInf=%ls\nDriverVer=%ls,%ls\nObAltitude=%ls FilterAltitude=%ls\n",
        Service->Image,Service->PublishedInf,Service->StoreInf,Service->Package.DriverDate,Service->Package.DriverVersion,Service->Package.ObAltitude,Service->Package.FilterAltitude);
}
static int RvhInstalledArgument(PCWSTR Argument,const RVH_SERVICE_INFO *Service){
    WCHAR absolute[MAX_PATH],store[MAX_PATH],published[MAX_PATH];
    if(!Service->Present)return RvhWin32Error(L"installed-service-required",ERROR_SERVICE_DOES_NOT_EXIST);
    if(!RvhAbsoluteFile(Argument,absolute))return 0;
    if(!SetupGetInfDriverStoreLocationW(absolute,NULL,NULL,store,ARRAYSIZE(store),NULL)
            ||!SetupGetInfPublishedNameW(store,published,ARRAYSIZE(published),NULL))
        return RvhWin32Error(L"installed-inf-argument",GetLastError());
    if(!RvhSameFile(store,Service->StoreInf)||!RvhSameFile(published,Service->PublishedInf))return 0;
    return 1;
}
static int RvhPrintPrivilege(BOOL *IsElevated){
    HANDLE token;TOKEN_ELEVATION elevation;DWORD needed;LUID id;BOOL present=FALSE,enabled=FALSE;PTOKEN_PRIVILEGES privileges=NULL;ULONG i;
    if(!OpenProcessToken(GetCurrentProcess(),TOKEN_QUERY,&token))return RvhWin32Error(L"current-token",GetLastError());
    if(!GetTokenInformation(token,TokenElevation,&elevation,sizeof(elevation),&needed)){DWORD error=GetLastError();CloseHandle(token);return RvhWin32Error(L"current-elevation",error);}
    if(!LookupPrivilegeValueW(NULL,SE_LOAD_DRIVER_NAME,&id)){DWORD error=GetLastError();CloseHandle(token);return RvhWin32Error(L"load-privilege",error);}
    GetTokenInformation(token,TokenPrivileges,NULL,0,&needed);
    if(GetLastError()!=ERROR_INSUFFICIENT_BUFFER){DWORD error=GetLastError();CloseHandle(token);return RvhWin32Error(L"token-privileges",error);}
    privileges=HeapAlloc(GetProcessHeap(),0,needed);
    if(privileges==NULL){CloseHandle(token);return RvhWin32Error(L"token-privileges",ERROR_NOT_ENOUGH_MEMORY);}
    if(!GetTokenInformation(token,TokenPrivileges,privileges,needed,&needed)){DWORD error=GetLastError();HeapFree(GetProcessHeap(),0,privileges);CloseHandle(token);return RvhWin32Error(L"token-privileges",error);}
    for(i=0;i<privileges->PrivilegeCount;i++)if(privileges->Privileges[i].Luid.LowPart==id.LowPart&&privileges->Privileges[i].Luid.HighPart==id.HighPart){
        present=TRUE;enabled=(privileges->Privileges[i].Attributes&SE_PRIVILEGE_ENABLED)!=0;break;
    }
    wprintf(L"Elevated=%lu LoadDriverPrivilegePresent=%lu LoadDriverPrivilegeEnabled=%lu\n",(ULONG)elevation.TokenIsElevated,(ULONG)present,(ULONG)enabled);
    *IsElevated=elevation.TokenIsElevated!=0;
    HeapFree(GetProcessHeap(),0,privileges);CloseHandle(token);return 1;
}
static int RvhCommandCharacter(PWSTR Out,size_t Count,size_t *Used,WCHAR Value){
    if(*Used+1>=Count)return RvhWin32Error(L"UAC-command-line-length",ERROR_FILENAME_EXCED_RANGE);
    Out[(*Used)++]=Value;Out[*Used]=L'\0';return 1;
}
static int RvhCommandArgument(PWSTR Out,size_t Count,size_t *Used,PCWSTR Argument){
    size_t slashes=0;
    if(*Used!=0&&!RvhCommandCharacter(Out,Count,Used,L' '))return 0;
    if(!RvhCommandCharacter(Out,Count,Used,L'"'))return 0;
    for(;;){
        if(*Argument==L'\\'){slashes++;Argument++;continue;}
        /* Windows CRT argv parsing consumes pairs of backslashes immediately
         * before a quote. Double trailing backslashes before our closing quote
         * too, and escape every literal quote; spaces/unicode stay in one argv. */
        if(*Argument==L'"'||*Argument==L'\0')slashes*=2;
        while(slashes!=0){if(!RvhCommandCharacter(Out,Count,Used,L'\\'))return 0;slashes--;}
        if(*Argument==L'\0')break;
        if(*Argument==L'"'&&!RvhCommandCharacter(Out,Count,Used,L'\\'))return 0;
        if(!RvhCommandCharacter(Out,Count,Used,*Argument++))return 0;
    }
    return RvhCommandCharacter(Out,Count,Used,L'"');
}
static int RvhRequestElevation(int Argc,wchar_t **Argv){
    SHELLEXECUTEINFOW execute={0};PWSTR executable=NULL,directory=NULL,parameters=NULL,slash;
    DWORD length,wait,error,exitCode=1;size_t used=0,capacity;HRESULT result;BOOL comReady=FALSE;int i;
    executable=HeapAlloc(GetProcessHeap(),0,RVH_COMMAND_LINE_CHARS*sizeof(WCHAR));
    parameters=HeapAlloc(GetProcessHeap(),HEAP_ZERO_MEMORY,RVH_COMMAND_LINE_CHARS*sizeof(WCHAR));
    if(executable==NULL||parameters==NULL){RvhWin32Error(L"UAC-command-allocation",ERROR_NOT_ENOUGH_MEMORY);goto Finished;}
    /* argv[0], PATH, a shell command and any caller-selected executable are
     * deliberately excluded. Only the image of this already-running tool is
     * launched, with the previously parsed fixed command and its exact argv. */
    length=GetModuleFileNameW(NULL,executable,RVH_COMMAND_LINE_CHARS);
    if(length==0){RvhWin32Error(L"UAC-current-executable",GetLastError());goto Finished;}
    if(length>=RVH_COMMAND_LINE_CHARS-3){RvhWin32Error(L"UAC-current-executable-length",ERROR_FILENAME_EXCED_RANGE);goto Finished;}
    directory=HeapAlloc(GetProcessHeap(),0,(length+1)*sizeof(WCHAR));
    if(directory==NULL){RvhWin32Error(L"UAC-directory-allocation",ERROR_NOT_ENOUGH_MEMORY);goto Finished;}
    CopyMemory(directory,executable,(length+1)*sizeof(WCHAR));slash=wcsrchr(directory,L'\\');
    if(slash==NULL){RvhWin32Error(L"UAC-current-executable-path",ERROR_BAD_PATHNAME);goto Finished;}
    slash[1]=L'\0';
    /* Reserve the quoted executable, separating space and final terminator in
     * the real Windows command line, although lpParameters excludes argv[0]. */
    capacity=RVH_COMMAND_LINE_CHARS-length-3;
    for(i=1;i<Argc;i++)if(!RvhCommandArgument(parameters,capacity,&used,Argv[i]))goto Finished;
    if(!RvhCommandArgument(parameters,capacity,&used,RVH_ELEVATED_CHILD))goto Finished;
    result=CoInitializeEx(NULL,COINIT_APARTMENTTHREADED|COINIT_DISABLE_OLE1DDE);
    if(FAILED(result)){RvhHresultError(L"UAC-shell-COM",result);goto Finished;}
    comReady=TRUE;
    execute.cbSize=sizeof(execute);
    execute.fMask=SEE_MASK_NOCLOSEPROCESS|SEE_MASK_NOASYNC|SEE_MASK_NO_CONSOLE|SEE_MASK_FLAG_NO_UI;
    execute.lpVerb=L"runas";execute.lpFile=executable;execute.lpParameters=parameters;
    execute.lpDirectory=directory;execute.nShow=SW_SHOWNORMAL;
    wprintf(L"Stage=UAC-request Command=%ls Executable=%ls\n",Argv[1],executable);fflush(stdout);fflush(stderr);
    /* FLAG_NO_UI suppresses shell error dialogs, never the Windows UAC consent
     * prompt. NO_CONSOLE requests the existing console for the child's output. */
    if(!ShellExecuteExW(&execute)){
        error=GetLastError();RvhWin32Error(error==ERROR_CANCELLED?L"UAC-cancelled":L"UAC-request",error);
        exitCode=error!=ERROR_SUCCESS?error:ERROR_GEN_FAILURE;goto Finished;
    }
    /* The synchronous shell launch is finished. Release its STA before the
     * kernel wait; the child process handle has no COM/message-pump dependency. */
    CoUninitialize();comReady=FALSE;
    if(execute.hProcess==NULL){RvhWin32Error(L"UAC-child-process-required",ERROR_INVALID_HANDLE);goto Finished;}
    wait=WaitForSingleObject(execute.hProcess,INFINITE);
    if(wait!=WAIT_OBJECT_0){RvhWin32Error(L"UAC-child-wait",wait==WAIT_FAILED?GetLastError():ERROR_GEN_FAILURE);goto Finished;}
    if(!GetExitCodeProcess(execute.hProcess,&exitCode)){RvhWin32Error(L"UAC-child-exit-code",GetLastError());exitCode=1;goto Finished;}
    wprintf(L"Stage=UAC-child-complete ChildExitCode=%lu\n",exitCode);
Finished:
    if(execute.hProcess!=NULL)CloseHandle(execute.hProcess);
    if(comReady)CoUninitialize();
    if(directory!=NULL)HeapFree(GetProcessHeap(),0,directory);
    if(parameters!=NULL)HeapFree(GetProcessHeap(),0,parameters);
    if(executable!=NULL)HeapFree(GetProcessHeap(),0,executable);
    return (int)exitCode;
}
static int RvhEnablePrivilege(RVH_PRIVILEGE *Privilege){
    TOKEN_PRIVILEGES desired;DWORD length=sizeof(Privilege->Previous),error;
    ZeroMemory(Privilege,sizeof(*Privilege));ZeroMemory(&desired,sizeof(desired));
    if(!OpenProcessToken(GetCurrentProcess(),TOKEN_QUERY|TOKEN_ADJUST_PRIVILEGES,&Privilege->Token))return RvhWin32Error(L"load-token",GetLastError());
    if(!LookupPrivilegeValueW(NULL,SE_LOAD_DRIVER_NAME,&desired.Privileges[0].Luid))return RvhWin32Error(L"load-privilege",GetLastError());
    desired.PrivilegeCount=1;desired.Privileges[0].Attributes=SE_PRIVILEGE_ENABLED;SetLastError(ERROR_SUCCESS);
    if(!AdjustTokenPrivileges(Privilege->Token,FALSE,&desired,sizeof(Privilege->Previous),&Privilege->Previous,&length))return RvhWin32Error(L"enable-load-privilege",GetLastError());
    error=GetLastError();
    if(error!=ERROR_SUCCESS)return RvhWin32Error(L"enable-load-privilege",error);
    Privilege->Changed=TRUE;wprintf(L"LoadDriverPrivilegeEnabledForAction=1\n");return 1;
}
static void RvhRestorePrivilege(RVH_PRIVILEGE *Privilege){
    if(Privilege->Token!=NULL){
        if(Privilege->Changed&&!AdjustTokenPrivileges(Privilege->Token,FALSE,&Privilege->Previous,0,NULL,NULL))RvhWin32Error(L"restore-load-privilege",GetLastError());
        CloseHandle(Privilege->Token);
    }
    ZeroMemory(Privilege,sizeof(*Privilege));
}
static int RvhDeviceQuery(void){
    HANDLE device;RVH_REQUEST request={0};RVH_SESSION_REPLY reply={0};DWORD returned=0,error=ERROR_SUCCESS;
    device=CreateFileW(RVH_WIN32_DEVICE,GENERIC_READ|GENERIC_WRITE,FILE_SHARE_READ|FILE_SHARE_WRITE,NULL,OPEN_EXISTING,FILE_ATTRIBUTE_NORMAL,NULL);
    if(device==INVALID_HANDLE_VALUE)return RvhWin32Error(L"driver-device-query-open",GetLastError());
    request.Magic=RVH_MAGIC;request.Version=RVH_VERSION;request.Size=sizeof(request);request.RequestId=1;
    if(!DeviceIoControl(device,IOCTL_RVH_HELLO,&request,sizeof(request),&reply,sizeof(reply),&returned,NULL))error=GetLastError();
    else if(returned!=sizeof(reply)||reply.Header.Size!=sizeof(reply))error=ERROR_BAD_LENGTH;
    else if(reply.Header.Magic!=RVH_MAGIC||reply.Header.Version!=RVH_VERSION||reply.Header.RequestId!=request.RequestId
            ||reply.Header.SessionId==0||reply.Reserved!=0||reply.DriverState!=RVH_DRIVER_LOADED
            ||reply.MaxProcesses!=RVH_MAX_PROCESSES||reply.MaxFiles!=RVH_MAX_FILES
            ||reply.ProcessCount>reply.MaxProcesses||reply.FileCount>reply.MaxFiles)error=ERROR_INVALID_DATA;
    if(!CloseHandle(device)&&error==ERROR_SUCCESS)error=GetLastError();
    if(error!=ERROR_SUCCESS)return RvhWin32Error(L"driver-device-query",error);
    wprintf(L"Protocol=%lu Session=%llu DriverState=%lu NTSTATUS=0x%08lX\n",reply.Header.Version,reply.Header.SessionId,reply.DriverState,(ULONG)reply.Header.Status);
    wprintf(L"Capabilities=0x%016llX Unserved=0x%016llX\n",reply.Capabilities,reply.Unserved);
    wprintf(L"ObRegister=0x%08lX ProcessNotify=0x%08lX FilterRegister=0x%08lX FilterStart=0x%08lX\n",(ULONG)reply.ObRegisterStatus,(ULONG)reply.ProcessNotifyStatus,(ULONG)reply.FilterRegisterStatus,(ULONG)reply.FilterStartStatus);
    /* These counts belong only to the tool's temporary, already-closed session. */
    wprintf(L"QuerySessionProcesses=%lu QuerySessionFiles=%lu GlobalSessionCount=not-exposed\n",reply.ProcessCount,reply.FileCount);
    if(reply.Header.Status<0||!(reply.Capabilities&RVH_CAP_DEVICE_SESSION))return RvhWin32Error(L"driver-query-rejected",ERROR_INVALID_STATE);
    return 1;
}
static int RvhWaitStopped(RVH_SERVICE_INFO *Service,DWORD Timeout){
    ULONGLONG deadline=GetTickCount64()+Timeout;DWORD needed;
    for(;;){
        if(!QueryServiceStatusEx(Service->Service,SC_STATUS_PROCESS_INFO,(LPBYTE)&Service->Status,sizeof(Service->Status),&needed))return RvhWin32Error(L"unload-service-state",GetLastError());
        if(Service->Status.dwCurrentState==SERVICE_STOPPED){wprintf(L"ServiceState=%lu\n",Service->Status.dwCurrentState);return 1;}
        if(GetTickCount64()>=deadline){wprintf(L"ServiceState=%lu\n",Service->Status.dwCurrentState);return RvhWin32Error(L"unload-state-still-pending",ERROR_TIMEOUT);}
        Sleep(100);
    }
}
static int RvhUnload(RVH_SERVICE_INFO *Service,DWORD Timeout){
    HRESULT result;
    if(!Service->Present)return RvhWin32Error(L"unload-service",ERROR_SERVICE_DOES_NOT_EXIST);
    if(Service->Status.dwCurrentState==SERVICE_STOPPED){wprintf(L"Stage=already-unloaded ServiceState=%lu\n",Service->Status.dwCurrentState);return 1;}
    if(Service->Status.dwCurrentState!=SERVICE_RUNNING)return RvhWin32Error(L"unload-service-transition",ERROR_SERVICE_CANNOT_ACCEPT_CTRL);
    result=FilterUnload(RVH_SERVICE_NAME);
    if(FAILED(result))return RvhHresultError(L"nonmandatory-FilterUnload",result);
    wprintf(L"Stage=FilterUnload HRESULT=0x%08lX\n",(ULONG)result);
    return RvhWaitStopped(Service,Timeout);
}
static int RvhLoad(void){
    HRESULT result=FilterLoad(RVH_SERVICE_NAME);
    if(FAILED(result))return RvhHresultError(L"FilterLoad",result);
    wprintf(L"Stage=FilterLoad HRESULT=0x%08lX\n",(ULONG)result);return RvhDeviceQuery();
}
static int RvhPinPackage(const RVH_PACKAGE_INFO *Package,HANDLE Files[3]){
    PCWSTR names[]={RVH_INF_NAME,RVH_SYS_NAME,RVH_CAT_NAME};ULONG i;WCHAR path[MAX_PATH];
    for(i=0;i<ARRAYSIZE(names);i++){
        if(!RvhSibling(Package->Inf,names[i],path))return 0;
        Files[i]=CreateFileW(path,GENERIC_READ,FILE_SHARE_READ,NULL,OPEN_EXISTING,FILE_ATTRIBUTE_NORMAL,NULL);
        if(Files[i]==INVALID_HANDLE_VALUE){Files[i]=NULL;return RvhWin32Error(L"package-INF-SYS-CAT-required",GetLastError());}
    }
    return 1;
}
static void RvhUnpinPackage(HANDLE Files[3]){ULONG i;for(i=0;i<3;i++)if(Files[i]!=NULL){CloseHandle(Files[i]);Files[i]=NULL;}}
static int RvhExpectedPackage(const RVH_SERVICE_INFO *Service,const RVH_PACKAGE_INFO *Expected){
    if(!Service->Present)return RvhWin32Error(L"installed-service-not-published",ERROR_SERVICE_DOES_NOT_EXIST);
    if(Service->Package.Version!=Expected->Version||Service->Package.Date!=Expected->Date
            ||wcscmp(Service->Package.ObAltitude,Expected->ObAltitude)!=0||wcscmp(Service->Package.FilterAltitude,Expected->FilterAltitude)!=0)
        return RvhWin32Error(L"requested-package-not-active",ERROR_REVISION_MISMATCH);
    if(Service->Status.dwCurrentState!=SERVICE_STOPPED)return RvhWin32Error(L"install-unexpected-loaded-state",ERROR_INVALID_STATE);
    return 1;
}
static int RvhSupportedOS(void){
    OSVERSIONINFOEXW version={0};DWORDLONG mask=0;
    version.dwOSVersionInfoSize=sizeof(version);version.dwMajorVersion=10;version.dwMinorVersion=0;version.dwBuildNumber=19041;
    mask=VerSetConditionMask(mask,VER_MAJORVERSION,VER_GREATER_EQUAL);mask=VerSetConditionMask(mask,VER_MINORVERSION,VER_GREATER_EQUAL);mask=VerSetConditionMask(mask,VER_BUILDNUMBER,VER_GREATER_EQUAL);
    if(!VerifyVersionInfoW(&version,VER_MAJORVERSION|VER_MINORVERSION|VER_BUILDNUMBER,mask))return RvhWin32Error(L"Windows-10-2004-or-later-required",GetLastError());
    return 1;
}
static void RvhUsage(void){
    fwprintf(stderr,L"RonovaHostManage query\n"
        L"RonovaHostManage install --package C:\\path\\RonovaHost.inf\n"
        L"RonovaHostManage load\n"
        L"RonovaHostManage unload [--timeout-ms 10000]\n"
        L"RonovaHostManage upgrade --package C:\\path\\RonovaHost.inf --installed-inf C:\\Windows\\INF\\oemN.inf [--reload] [--timeout-ms 10000]\n"
        L"RonovaHostManage remove --installed-inf C:\\Windows\\INF\\oemN.inf [--timeout-ms 10000]\n"
        L"Install never loads. Upgrade reloads only with --reload. Unload/remove/upgrade never call service stop.\n"
        L"Selected modifying actions reuse an elevated token or request Windows UAC consent for this same tool. Query never requests elevation.\n"
        L"Exit 0: completed; 1: action failure; 1223: UAC cancelled; other UAC launch errors retain Win32 codes; 3010: reboot required. No reboot is requested.\n");
}

int wmain(int Argc,wchar_t **Argv){
    PCWSTR command,packagePath=NULL,installedPath=NULL;BOOL reload=FALSE,reboot=FALSE,upgrade,install,remove,query,elevated=FALSE,elevatedChild=FALSE;
    DWORD timeout=10000;int i,exitCode=1;RVH_PACKAGE_INFO package;RVH_SERVICE_INFO service={0},after={0};
    RVH_PRIVILEGE privilege={0};HANDLE pinned[3]={0};BOOL haveTimeout=FALSE;
    if(Argc<2){RvhUsage();return 1;}
    command=Argv[1];
    if(wcscmp(command,L"--help")==0){RvhUsage();return 0;}
    upgrade=wcscmp(command,L"upgrade")==0;install=wcscmp(command,L"install")==0;remove=wcscmp(command,L"remove")==0;
    query=wcscmp(command,L"query")==0;
    if(!upgrade&&!install&&!remove&&!query&&wcscmp(command,L"load")!=0&&wcscmp(command,L"unload")!=0){RvhUsage();return 1;}
    for(i=2;i<Argc;i++){
        if(wcscmp(Argv[i],RVH_ELEVATED_CHILD)==0&&!elevatedChild){elevatedChild=TRUE;continue;}
        if(wcscmp(Argv[i],L"--reload")==0&&!reload){reload=TRUE;continue;}
        if(wcscmp(Argv[i],L"--package")==0&&packagePath==NULL&&i+1<Argc){packagePath=Argv[++i];continue;}
        if(wcscmp(Argv[i],L"--installed-inf")==0&&installedPath==NULL&&i+1<Argc){installedPath=Argv[++i];continue;}
        if(wcscmp(Argv[i],L"--timeout-ms")==0&&!haveTimeout&&i+1<Argc){
            WCHAR *end;unsigned long value=wcstoul(Argv[++i],&end,10);
            if(*end!=L'\0'||value==0||value>60000){RvhUsage();return 1;}timeout=(DWORD)value;haveTimeout=TRUE;continue;
        }
        RvhUsage();return 1;
    }
    if((install||upgrade)!=(packagePath!=NULL)||(remove||upgrade)!=(installedPath!=NULL)||(reload&&!upgrade)
            ||(haveTimeout&&!remove&&!upgrade&&wcscmp(command,L"unload")!=0)||(query&&elevatedChild)){RvhUsage();return 1;}
    if(!RvhSupportedOS()||!RvhPrintPrivilege(&elevated))goto Finished;
    if(!query){
        if(!elevated){
            /* The marker only prevents a request loop; actual TokenElevation,
             * never an argv flag, grants access to the existing action code. */
            if(elevatedChild){RvhWin32Error(L"UAC-child-not-elevated",ERROR_ELEVATION_REQUIRED);goto Finished;}
            exitCode=RvhRequestElevation(Argc,Argv);goto Finished;
        }
        wprintf(L"ElevationReused=1 ElevatedChild=%lu\n",(ULONG)elevatedChild);
    }
    /* Pin all source package files before reading metadata or draining the old
     * driver. A staged/signed package cannot be rewritten during this action. */
    if(install||upgrade){
        WCHAR absolute[MAX_PATH];PCWSTR name;
        if(!RvhAbsoluteFile(packagePath,absolute))goto Finished;
        name=wcsrchr(absolute,L'\\');
        if(name==NULL||_wcsicmp(name+1,RVH_INF_NAME)!=0){RvhWin32Error(L"original-RonovaHost-inf-name-required",ERROR_INVALID_NAME);goto Finished;}
        ZeroMemory(&package,sizeof(package));if(!RvhPathCopy(package.Inf,ARRAYSIZE(package.Inf),absolute))goto Finished;
        if(!RvhPinPackage(&package,pinned)||!RvhReadPackage(absolute,&package))goto Finished;
    }
    if(!RvhReadService(&service))goto Finished;
    RvhPrintService(&service);
    if(query){exitCode=!service.Present||service.Status.dwCurrentState==SERVICE_STOPPED?0:RvhDeviceQuery()?0:1;goto Finished;}
    if(install){
        if(service.Present){RvhWin32Error(L"install-requires-absent-service-use-upgrade",ERROR_SERVICE_EXISTS);goto Finished;}
    }else if(!service.Present){RvhWin32Error(L"installed-service-required",ERROR_SERVICE_DOES_NOT_EXIST);goto Finished;}
    if((remove||upgrade)&&!RvhInstalledArgument(installedPath,&service))goto Finished;
    if(upgrade&&(package.Version<=service.Package.Version||package.Date<service.Package.Date)){
        RvhWin32Error(L"upgrade-requires-newer-DriverVer",ERROR_REVISION_MISMATCH);goto Finished;
    }
    if(wcscmp(command,L"load")==0||wcscmp(command,L"unload")==0||remove||upgrade){if(!RvhEnablePrivilege(&privilege))goto Finished;}
    if(wcscmp(command,L"load")==0){exitCode=RvhLoad()?0:1;goto Finished;}
    if(wcscmp(command,L"unload")==0){exitCode=RvhUnload(&service,timeout)?0:1;goto Finished;}
    if((remove||upgrade)&&!RvhUnload(&service,timeout))goto Finished;
    /* Re-read the exact configured package after unload. Package APIs must not
     * act on a replacement registered by an unrelated concurrent operation. */
    if(remove||upgrade){
        if(!RvhReadService(&after)||!RvhInstalledArgument(installedPath,&after))goto Finished;
        if(after.Status.dwCurrentState!=SERVICE_STOPPED){RvhWin32Error(L"package-change-requires-same-stopped-driver",ERROR_INVALID_STATE);goto Finished;}
        RvhReleaseService(&after);
    }
    if(remove){
        if(!DiUninstallDriverW(NULL,service.PublishedInf,0,&reboot)){RvhWin32Error(L"DiUninstallDriver",GetLastError());goto Finished;}
        wprintf(L"Stage=DiUninstallDriver NeedReboot=%lu RemovedInf=%ls\n",(ULONG)reboot,service.PublishedInf);
        RvhReleaseService(&service);
        if(reboot){wprintf(L"RestartRequired=1 RemovalPending=1\n");exitCode=ERROR_SUCCESS_REBOOT_REQUIRED;goto Finished;}
        if(!RvhReadService(&after))goto Finished;RvhPrintService(&after);
        if(after.Present){RvhWin32Error(L"remove-service-still-present",ERROR_SERVICE_EXISTS);goto Finished;}
        exitCode=0;goto Finished;
    }
    if(!DiInstallDriverW(NULL,package.Inf,0,&reboot)){
        RvhWin32Error(L"DiInstallDriver",GetLastError());
        if(upgrade)wprintf(L"UpgradeInstallFailed=1 PreviousPackage=%ls PreviousDriverUnloaded=1\n",service.PublishedInf);
        goto Finished;
    }
    wprintf(L"Stage=DiInstallDriver NeedReboot=%lu RequestedInf=%ls\n",(ULONG)reboot,package.Inf);
    RvhReleaseService(&service);
    if(reboot){wprintf(L"RestartRequired=1 LoadDeferred=1\n");exitCode=ERROR_SUCCESS_REBOOT_REQUIRED;goto Finished;}
    if(!RvhReadService(&after))goto Finished;RvhPrintService(&after);
    if(!RvhExpectedPackage(&after,&package))goto Finished;
    if(reload&&!RvhLoad())goto Finished;
    wprintf(L"Stage=%ls ReloadRequested=%lu\n",upgrade?L"upgrade-complete":L"install-complete",(ULONG)reload);exitCode=0;
Finished:
    RvhUnpinPackage(pinned);RvhReleaseService(&after);RvhReleaseService(&service);RvhRestorePrivilege(&privilege);return exitCode;
}
