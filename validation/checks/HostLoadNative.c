/* Real JNI_OnLoad companion for AuditFixCheck. Build separate normal, nested,
 * failing and support DLLs from this source; no backend registry is fabricated. */
#include <jni.h>
#include <windows.h>
#include <bcrypt.h>
#include <ktmw32.h>
#include <stdint.h>
#include <limits.h>
#include <stdio.h>
#include <string.h>
#include <wchar.h>
#include <errno.h>
typedef BOOL (WINAPI *LoadCreateA)(LPCSTR,LPSTR,LPSECURITY_ATTRIBUTES,LPSECURITY_ATTRIBUTES,BOOL,DWORD,LPVOID,LPCSTR,LPSTARTUPINFOA,LPPROCESS_INFORMATION);
typedef BOOL (WINAPI *LoadCreateW)(LPCWSTR,LPWSTR,LPSECURITY_ATTRIBUTES,LPSECURITY_ATTRIBUTES,BOOL,DWORD,LPVOID,LPCWSTR,LPSTARTUPINFOW,LPPROCESS_INFORMATION);
extern LoadCreateA __imp_CreateProcessA;
extern LoadCreateW __imp_CreateProcessW;
static int load_child(void *entry,int ansi,DWORD *error){
    char directoryA[MAX_PATH],programA[MAX_PATH],commandA[]="cmd.exe /d /c exit 37";
    wchar_t directoryW[MAX_PATH],programW[MAX_PATH],commandW[]=L"cmd.exe /d /c exit 37";
    DWORD countA=GetSystemDirectoryA(directoryA,MAX_PATH),countW=GetSystemDirectoryW(directoryW,MAX_PATH);
    if(!countA||countA+9>=MAX_PATH||!countW||countW+9>=MAX_PATH){*error=ERROR_BAD_PATHNAME;return 0;}
    snprintf(programA,MAX_PATH,"%s\\cmd.exe",directoryA);_snwprintf(programW,MAX_PATH,L"%ls\\cmd.exe",directoryW);
    STARTUPINFOA startupA={0};startupA.cb=sizeof(startupA);startupA.dwFlags=STARTF_USESHOWWINDOW;startupA.wShowWindow=SW_HIDE;
    STARTUPINFOW startupW={0};startupW.cb=sizeof(startupW);startupW.dwFlags=STARTF_USESHOWWINDOW;startupW.wShowWindow=SW_HIDE;
    PROCESS_INFORMATION process={0};BOOL created;SetLastError(0);
    if(ansi)created=entry?((LoadCreateA)entry)(programA,commandA,NULL,NULL,FALSE,CREATE_NO_WINDOW,NULL,directoryA,&startupA,&process)
            :CreateProcessA(programA,commandA,NULL,NULL,FALSE,CREATE_NO_WINDOW,NULL,directoryA,&startupA,&process);
    else created=entry?((LoadCreateW)entry)(programW,commandW,NULL,NULL,FALSE,CREATE_NO_WINDOW,NULL,directoryW,&startupW,&process)
            :CreateProcessW(programW,commandW,NULL,NULL,FALSE,CREATE_NO_WINDOW,NULL,directoryW,&startupW,&process);
    *error=GetLastError();if(!created)return 0;
    DWORD waited=WaitForSingleObject(process.hProcess,5000),code=0;
    int ok=waited==WAIT_OBJECT_0&&GetExitCodeProcess(process.hProcess,&code)&&code==37;
    if(waited!=WAIT_OBJECT_0){TerminateProcess(process.hProcess,99);WaitForSingleObject(process.hProcess,2000);}
    if(!CloseHandle(process.hThread))ok=0;
    if(!CloseHandle(process.hProcess))ok=0;
    if(!ok)*error=ERROR_INVALID_DATA;return ok;
}
static int load_private_entry(void *entry){
    HMODULE image=NULL;
    return entry&&!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,(LPCSTR)entry,&image);
}
/* Merge after load_private_entry in HostLoadNative.c. Invoke load_os_check(env)
 * once from the normal payload's JNI_OnLoad under AUDIT_HOST_OS_CHECK. These
 * public calls use the already installed primary backend; no controller is
 * compiled into this payload and no producer authority is manufactured. */
typedef BOOL (WINAPI *LoadDuplicate)(HANDLE,HANDLE,HANDLE,LPHANDLE,DWORD,BOOL,DWORD);
typedef BOOL (WINAPI *LoadClose)(HANDLE);
typedef BOOL (WINAPI *LoadSame)(HANDLE,HANDLE);
typedef BOOL (WINAPI *LoadOpenToken)(HANDLE,DWORD,PHANDLE);
typedef BOOL (WINAPI *LoadUserW)(HANDLE,LPCWSTR,LPWSTR,LPSECURITY_ATTRIBUTES,LPSECURITY_ATTRIBUTES,
        BOOL,DWORD,LPVOID,LPCWSTR,LPSTARTUPINFOW,LPPROCESS_INFORMATION);
typedef BOOL (WINAPI *LoadUserA)(HANDLE,LPCSTR,LPSTR,LPSECURITY_ATTRIBUTES,LPSECURITY_ATTRIBUTES,
        BOOL,DWORD,LPVOID,LPCSTR,LPSTARTUPINFOA,LPPROCESS_INFORMATION);
typedef BOOL (WINAPI *LoadTokenW)(HANDLE,DWORD,LPCWSTR,LPWSTR,DWORD,LPVOID,LPCWSTR,LPSTARTUPINFOW,LPPROCESS_INFORMATION);
typedef BOOL (WINAPI *LoadLogonW)(LPCWSTR,LPCWSTR,LPCWSTR,DWORD,LPCWSTR,LPWSTR,DWORD,LPVOID,LPCWSTR,LPSTARTUPINFOW,LPPROCESS_INFORMATION);
extern LoadClose __imp_CloseHandle;
extern LoadDuplicate __imp_DuplicateHandle;
extern HANDLE (WINAPI *__imp_OpenProcess)(DWORD,BOOL,DWORD);
extern HANDLE (WINAPI *__imp_OpenThread)(DWORD,BOOL,DWORD);
extern DWORD (WINAPI *__imp_ResumeThread)(HANDLE);
extern DWORD (WINAPI *__imp_SuspendThread)(HANDLE);
extern BOOL (WINAPI *__imp_TerminateProcess)(HANDLE,UINT);
extern BOOL (WINAPI *__imp_TerminateThread)(HANDLE,DWORD);
extern BOOL (WINAPI *__imp_SetHandleInformation)(HANDLE,DWORD,DWORD);

static int load_os_fail(JNIEnv *env,const char *message){
    if(!(*env)->ExceptionCheck(env)){
        jclass failure=(*env)->FindClass(env,"java/lang/AssertionError");
        if(failure)(*env)->ThrowNew(env,failure,message);
    }
    return 0;
}
static int load_os_program(char *directoryA,char *programA,wchar_t *directoryW,wchar_t *programW){
    DWORD countA=GetSystemDirectoryA(directoryA,MAX_PATH),countW=GetSystemDirectoryW(directoryW,MAX_PATH);
    if(!countA||countA+9>=MAX_PATH||!countW||countW+9>=MAX_PATH)return 0;
    snprintf(programA,MAX_PATH,"%s\\cmd.exe",directoryA);_snwprintf(programW,MAX_PATH,L"%ls\\cmd.exe",directoryW);
    return 1;
}
static __declspec(noinline) const char *load_os_aliases(LoadSame same){
    char directoryA[MAX_PATH],programA[MAX_PATH];wchar_t directoryW[MAX_PATH],programW[MAX_PATH];
    if(!load_os_program(directoryA,programA,directoryW,programW))return "HOST_OS_SYSTEM_DIRECTORY";
    wchar_t command[]=L"cmd.exe /d /c exit 37";
    STARTUPINFOW startup={0};startup.cb=sizeof(startup);startup.dwFlags=STARTF_USESHOWWINDOW;startup.wShowWindow=SW_HIDE;
    PROCESS_INFORMATION created={0};HANDLE processCopy=NULL,threadCopy=NULL,openedProcess=NULL,openedThread=NULL;
    HMODULE kernel=GetModuleHandleW(L"kernel32.dll");
    LoadDuplicate duplicate=(LoadDuplicate)GetProcAddress(kernel,"DuplicateHandle");
    LoadClose close=(LoadClose)GetProcAddress(kernel,"CloseHandle");
    const char *failure=NULL;
    if(!load_private_entry((void*)duplicate)||!load_private_entry((void*)close))return "HOST_OS_DYNAMIC_HANDLES_UNCONTROLLED";
    if(!CreateProcessW(programW,command,NULL,NULL,FALSE,CREATE_NO_WINDOW|CREATE_SUSPENDED,NULL,directoryW,&startup,&created))
        return "HOST_OS_ALIAS_CREATE";
    if(!DuplicateHandle(GetCurrentProcess(),created.hProcess,GetCurrentProcess(),&processCopy,0,FALSE,DUPLICATE_SAME_ACCESS)
            ||!same(created.hProcess,processCopy)){failure="HOST_OS_PROCESS_DUPLICATE_CHANGED_OBJECT";goto done;}
    if(!duplicate(GetCurrentProcess(),created.hThread,GetCurrentProcess(),&threadCopy,0,FALSE,
            DUPLICATE_SAME_ACCESS|DUPLICATE_CLOSE_SOURCE)){
        created.hThread=NULL;failure="HOST_OS_THREAD_CLOSE_SOURCE_TRANSFER";goto done;
    }
    created.hThread=NULL;
    openedProcess=OpenProcess(SYNCHRONIZE|PROCESS_QUERY_LIMITED_INFORMATION,FALSE,created.dwProcessId);
    openedThread=OpenThread(SYNCHRONIZE|THREAD_QUERY_LIMITED_INFORMATION|THREAD_SUSPEND_RESUME,FALSE,created.dwThreadId);
    if(!openedProcess||!same(openedProcess,processCopy)||!openedThread||!same(openedThread,threadCopy)){
        failure="HOST_OS_OPEN_CHANGED_ACTUAL_OBJECT";goto done;
    }
    DWORD flags=0;
    if(!SetHandleInformation(processCopy,HANDLE_FLAG_INHERIT,HANDLE_FLAG_INHERIT)
            ||!GetHandleInformation(processCopy,&flags)||!(flags&HANDLE_FLAG_INHERIT)
            ||!SetHandleInformation(processCopy,HANDLE_FLAG_INHERIT,0)){
        failure="HOST_OS_HANDLE_FLAGS_CHANGED";goto done;
    }
    if(!close(created.hProcess)){failure="HOST_OS_DYNAMIC_ORIGINAL_CLOSE";goto done;}created.hProcess=NULL;
    if(SuspendThread(openedThread)!=1||ResumeThread(threadCopy)!=2||ResumeThread(openedThread)!=1){
        failure="HOST_OS_REAL_THREAD_SUSPEND_RESUME_COUNT";goto done;
    }
    DWORD exitCode=0;
    if(WaitForSingleObject(processCopy,5000)!=WAIT_OBJECT_0||!GetExitCodeProcess(processCopy,&exitCode)||exitCode!=37)
        failure="HOST_OS_ALIASED_CHILD_EXIT";
done:
    if(failure){HANDLE actual=processCopy?processCopy:created.hProcess;
        if(actual){TerminateProcess(actual,99);WaitForSingleObject(actual,2000);}}
    if(openedThread&&!CloseHandle(openedThread)&&!failure)failure="HOST_OS_OPENED_THREAD_CLOSE";
    if(threadCopy&&!close(threadCopy)&&!failure)failure="HOST_OS_DYNAMIC_THREAD_CLOSE";
    if(openedProcess&&!CloseHandle(openedProcess)&&!failure)failure="HOST_OS_OPENED_PROCESS_CLOSE";
    if(processCopy&&!close(processCopy)&&!failure)failure="HOST_OS_DYNAMIC_PROCESS_CLOSE";
    if(created.hThread)CloseHandle(created.hThread);if(created.hProcess)CloseHandle(created.hProcess);
    return failure;
}
static __declspec(noinline) const char *load_os_termination(void){
    char directoryA[MAX_PATH],programA[MAX_PATH];wchar_t directoryW[MAX_PATH],programW[MAX_PATH];
    if(!load_os_program(directoryA,programA,directoryW,programW))return "HOST_OS_SYSTEM_DIRECTORY";
    STARTUPINFOW startup={0};startup.cb=sizeof(startup);startup.dwFlags=STARTF_USESHOWWINDOW;startup.wShowWindow=SW_HIDE;
    for(int thread=0;thread<2;thread++){
        wchar_t command[]=L"cmd.exe /d /c exit 37";PROCESS_INFORMATION created={0};
        if(!CreateProcessW(programW,command,NULL,NULL,FALSE,CREATE_NO_WINDOW|CREATE_SUSPENDED,NULL,directoryW,&startup,&created))
            return "HOST_OS_TERMINATION_CREATE";
        BOOL stopped=thread?TerminateThread(created.hThread,43):TerminateProcess(created.hProcess,41);
        DWORD waited=WaitForSingleObject(thread?created.hThread:created.hProcess,5000),code=0;
        BOOL queried=thread?GetExitCodeThread(created.hThread,&code):GetExitCodeProcess(created.hProcess,&code);
        int ok=stopped&&waited==WAIT_OBJECT_0&&queried&&code==(thread?43:41);
        if(WaitForSingleObject(created.hProcess,0)!=WAIT_OBJECT_0){TerminateProcess(created.hProcess,99);WaitForSingleObject(created.hProcess,2000);}
        if(!CloseHandle(created.hThread)||!CloseHandle(created.hProcess))ok=0;
        if(!ok)return thread?"HOST_OS_ACTUAL_THREAD_TERMINATION":"HOST_OS_ACTUAL_PROCESS_TERMINATION";
    }
    return NULL;
}
static __declspec(noinline) int load_os_token_attempt(void *entry,int kind,HANDLE token,DWORD *error){
    char directoryA[MAX_PATH],programA[MAX_PATH],commandA[]="cmd.exe /d /c exit 37";
    wchar_t directoryW[MAX_PATH],programW[MAX_PATH],commandW[]=L"cmd.exe /d /c exit 37";
    if(!load_os_program(directoryA,programA,directoryW,programW))return -1;
    STARTUPINFOA startupA={0};startupA.cb=sizeof(startupA);startupA.dwFlags=STARTF_USESHOWWINDOW;startupA.wShowWindow=SW_HIDE;
    STARTUPINFOW startupW={0};startupW.cb=sizeof(startupW);startupW.dwFlags=STARTF_USESHOWWINDOW;startupW.wShowWindow=SW_HIDE;
    PROCESS_INFORMATION created={0};BOOL result=FALSE;DWORD flags=CREATE_NO_WINDOW|CREATE_SUSPENDED;
    SetLastError(0);
    if(kind==0)result=((LoadUserW)entry)(token,programW,commandW,NULL,NULL,FALSE,flags,NULL,directoryW,&startupW,&created);
    else if(kind==1)result=((LoadUserA)entry)(token,programA,commandA,NULL,NULL,FALSE,flags,NULL,directoryA,&startupA,&created);
    else if(kind==2)result=((LoadTokenW)entry)(token,0,programW,commandW,flags,NULL,directoryW,&startupW,&created);
    else result=((LoadLogonW)entry)(L"",L"",L"",(DWORD)-1,programW,commandW,flags,NULL,directoryW,&startupW,&created);
    *error=GetLastError();if(!result)return 0;
    int ok=ResumeThread(created.hThread)==1&&WaitForSingleObject(created.hProcess,5000)==WAIT_OBJECT_0;
    DWORD exitCode=0;ok=ok&&GetExitCodeProcess(created.hProcess,&exitCode)&&exitCode==37;
    if(!ok){TerminateProcess(created.hProcess,99);WaitForSingleObject(created.hProcess,2000);}
    if(!CloseHandle(created.hThread)||!CloseHandle(created.hProcess))ok=0;
    return ok?1:-1;
}
static __declspec(noinline) const char *load_os_tokens(void){
    wchar_t directory[MAX_PATH],path[MAX_PATH];DWORD length=GetSystemDirectoryW(directory,MAX_PATH);
    if(!length||length+14>=MAX_PATH)return "HOST_OS_SYSTEM_DIRECTORY";
    _snwprintf(path,MAX_PATH,L"%ls\\advapi32.dll",directory);HMODULE advapi=LoadLibraryW(path);
    if(!advapi)return "HOST_OS_TOKEN_LIBRARY";
    LoadOpenToken openToken=(LoadOpenToken)GetProcAddress(advapi,"OpenProcessToken");HANDLE token=NULL;
    const char *failure=NULL;
    if(!openToken||!openToken(GetCurrentProcess(),TOKEN_QUERY|TOKEN_DUPLICATE|TOKEN_ASSIGN_PRIMARY,&token)){
        failure="HOST_OS_CURRENT_AUTHORIZED_TOKEN";goto done;
    }
    const char *names[]={"CreateProcessAsUserW","CreateProcessAsUserA","CreateProcessWithTokenW","CreateProcessWithLogonW"};
    for(int kind=0;kind<4;kind++){
        void *entry=(void*)GetProcAddress(advapi,names[kind]);
        if(!load_private_entry(entry)){failure="HOST_OS_TOKEN_ENTRY_UNCONTROLLED";break;}
        DWORD error=0;int result=load_os_token_attempt(entry,kind,token,&error);
        /* A standard current token may lack the OS privilege required by these
         * APIs. A denied credential/privilege result is not privileged success. */
        if(result<0||kind<3&&!result&&error!=ERROR_PRIVILEGE_NOT_HELD&&error!=ERROR_ACCESS_DENIED
                ||kind==3&&(result||error!=ERROR_INVALID_PARAMETER)){
            fprintf(stderr,"HOST_OS_TOKEN_FAILED kind=%d result=%d win32=%lu\n",kind,result,(unsigned long)error);
            failure="HOST_OS_TOKEN_SIGNATURE_OR_WINDOWS_AUTHORIZATION";break;
        }
        fprintf(stdout,"HOST_OS_TOKEN_RESULT api=%s created=%d win32=%lu\n",names[kind],result,(unsigned long)error);
    }
done:
    if(token)CloseHandle(token);FreeLibrary(advapi);return failure;
}
static int load_os_check(JNIEnv *env){
    void *imports[]={(void*)__imp_CloseHandle,(void*)__imp_DuplicateHandle,(void*)__imp_SetHandleInformation,
            (void*)__imp_OpenProcess,(void*)__imp_OpenThread,(void*)__imp_ResumeThread,(void*)__imp_SuspendThread,
            (void*)__imp_TerminateProcess,(void*)__imp_TerminateThread};
    for(unsigned i=0;i<sizeof(imports)/sizeof(imports[0]);i++)
        if(!load_private_entry(imports[i]))return load_os_fail(env,"HOST_OS_REAL_IMPORT_NOT_CONTROLLED");
    HMODULE kernelbase=GetModuleHandleW(L"kernelbase.dll");
    LoadSame same=kernelbase?(LoadSame)GetProcAddress(kernelbase,"CompareObjectHandles"):NULL;
    if(!same)return load_os_fail(env,"HOST_OS_OBJECT_IDENTITY_API");
    const char *failure=load_os_aliases(same);if(!failure)failure=load_os_termination();if(!failure)failure=load_os_tokens();
    if(failure)return load_os_fail(env,failure);
    fprintf(stdout,"HOST_OS_API_PASS primary_backend actual_process_thread_handles local_dup_close_source open_same_objects handle_flags suspend_resume terminate token_signatures\n");
    return 1;
}

/* Reuse the real class-image payload for the old AUDIT_HOST_JVMTI_CHECK and the
 * independent AUDIT_JVMTI_DISPATCH_CHECK; no production controller is embedded. */
#include <jvmti.h>
#include <string.h>
#ifdef AUDIT_HOST_JVMTI_CHECK
static volatile LONG load_ti_first,load_ti_second;
static jvmtiEnv *load_ti_identity;
static const char load_ti_prefix[]="dev/ronova/pro/JvmtiPayload";
static void load_ti_fail(JNIEnv *env,const char *message){
    if((*env)->ExceptionCheck(env))return;jclass type=(*env)->FindClass(env,"java/lang/AssertionError");
    if(type){(*env)->ThrowNew(env,type,message);(*env)->DeleteLocalRef(env,type);}
}
static void JNICALL load_ti_callback_first(jvmtiEnv *api,JNIEnv *env,jclass actual,jobject loader,const char *name,jobject domain,jint length,const unsigned char *bytes,jint *newLength,unsigned char **newBytes){
    (void)actual;(void)loader;(void)domain;
    if(!name||strncmp(name,load_ti_prefix,sizeof(load_ti_prefix)-1))return;
    if(api!=load_ti_identity){load_ti_fail(env,"JVMTI_CALLBACK_WRONG_ACTUAL_ENVIRONMENT");return;}
    InterlockedIncrement(&load_ti_first);unsigned char *replacement=NULL;
    if((*api)->Allocate(api,length,&replacement)!=JVMTI_ERROR_NONE||!replacement){load_ti_fail(env,"JVMTI_REAL_CALLBACK_ALLOCATION_FAILED");return;}
    memcpy(replacement,bytes,(size_t)length);int changed=0;
    for(jint i=0;i+2<length;i++)if(replacement[i]==0x10&&replacement[i+1]==7&&replacement[i+2]==0xac){replacement[i+1]=9;changed++;}
    if(changed!=1){(*api)->Deallocate(api,replacement);load_ti_fail(env,"JVMTI_ACTUAL_INPUT_METHOD_BODY_NOT_FOUND");return;}
    *newLength=length;*newBytes=replacement;
}
static void JNICALL load_ti_callback_second(jvmtiEnv *api,JNIEnv *env,jclass actual,jobject loader,const char *name,jobject domain,jint length,const unsigned char *bytes,jint *newLength,unsigned char **newBytes){
    (void)actual;(void)loader;(void)domain;(void)length;(void)bytes;(void)newLength;(void)newBytes;
    if(!name||strncmp(name,load_ti_prefix,sizeof(load_ti_prefix)-1))return;
    if(api!=load_ti_identity){load_ti_fail(env,"JVMTI_CALLBACK_REPLACEMENT_WRONG_ENVIRONMENT");return;}
    InterlockedIncrement(&load_ti_second);
}
#endif
#if defined(AUDIT_HOST_JVMTI_CHECK) || defined(AUDIT_JVMTI_DISPATCH_CHECK)
typedef struct LoadClassBytes {unsigned char bytes[256];unsigned length;} LoadClassBytes;
static void load_ti_u1(LoadClassBytes *image,unsigned value){image->bytes[image->length++]=(unsigned char)value;}
static void load_ti_u2(LoadClassBytes *image,unsigned value){load_ti_u1(image,value>>8);load_ti_u1(image,value);}
static void load_ti_u4(LoadClassBytes *image,unsigned value){load_ti_u2(image,value>>16);load_ti_u2(image,value);}
static void load_ti_utf8(LoadClassBytes *image,const char *value){unsigned length=(unsigned)strlen(value);load_ti_u1(image,1);load_ti_u2(image,length);memcpy(image->bytes+image->length,value,length);image->length+=length;}
static jclass load_ti_define_image(JNIEnv *env,jobject loader,char suffix,LoadClassBytes *saved){
    char name[]="dev/ronova/pro/JvmtiPayloadA";name[sizeof(name)-2]=suffix;LoadClassBytes image={0};
    load_ti_u4(&image,0xcafebabe);load_ti_u2(&image,0);load_ti_u2(&image,61);load_ti_u2(&image,8);
    load_ti_utf8(&image,name);load_ti_u1(&image,7);load_ti_u2(&image,1);
    load_ti_utf8(&image,"java/lang/Object");load_ti_u1(&image,7);load_ti_u2(&image,3);
    load_ti_utf8(&image,"value");load_ti_utf8(&image,"()I");load_ti_utf8(&image,"Code");
    load_ti_u2(&image,0x21);load_ti_u2(&image,2);load_ti_u2(&image,4);load_ti_u2(&image,0);load_ti_u2(&image,0);load_ti_u2(&image,1);
    load_ti_u2(&image,0x9);load_ti_u2(&image,5);load_ti_u2(&image,6);load_ti_u2(&image,1);load_ti_u2(&image,7);load_ti_u4(&image,15);
    load_ti_u2(&image,1);load_ti_u2(&image,0);load_ti_u4(&image,3);load_ti_u1(&image,0x10);load_ti_u1(&image,7);load_ti_u1(&image,0xac);
    load_ti_u2(&image,0);load_ti_u2(&image,0);load_ti_u2(&image,0);
    if(saved)*saved=image;
    return (*env)->DefineClass(env,name,loader,(const jbyte*)image.bytes,(jsize)image.length);
}
static jclass load_ti_define_class(JNIEnv *env,jobject loader,char suffix){return load_ti_define_image(env,loader,suffix,NULL);}
#endif
#ifdef AUDIT_HOST_JVMTI_CHECK
static int load_ti_define(JNIEnv *env,jobject loader,char suffix){
    jclass actual=load_ti_define_class(env,loader,suffix);
    jmethodID method=actual?(*env)->GetStaticMethodID(env,actual,"value","()I"):NULL;
    int result=method?(*env)->CallStaticIntMethod(env,actual,method):-1;if(actual)(*env)->DeleteLocalRef(env,actual);return result;
}
static jclass JNICALL load_ti_uncontrolled_define(JNIEnv *env,const char *name,jobject loader,const jbyte *bytes,jsize length){
    (void)env;(void)name;(void)loader;(void)bytes;(void)length;return NULL;
}
/* Only the global JNI publication path changed here. Use a real fresh JVMTI
 * environment; leave the old class/heap/worker matrices outside this phase. */
JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_hostJniTable0(JNIEnv *env,jclass check){
    (void)check;JavaVM *vm=NULL;jvmtiEnv *api=NULL;
    jniNativeInterface *table=NULL,*published=NULL;const char *failure=NULL;
    if((*env)->GetJavaVM(env,&vm)!=JNI_OK||!vm
            ||(*vm)->GetEnv(vm,(void**)&api,JVMTI_VERSION_1_2)!=JNI_OK||!api){
        load_ti_fail(env,"JNI_TABLE_REAL_ENVIRONMENT_REQUIRED");return;
    }
    if((*api)->GetJNIFunctionTable(api,&table)!=JVMTI_ERROR_NONE||!table)failure="JNI_TABLE_ACTUAL_CURRENT_TABLE";
    if(!failure){
        jclass (JNICALL *define)(JNIEnv*,const char*,jobject,const jbyte*,jsize)=table->DefineClass;
        if(!define||(*api)->SetJNIFunctionTable(api,table)!=JVMTI_ERROR_NONE)failure="JNI_TABLE_ACCEPTED_SNAPSHOT_PUBLICATION";
        if(!failure){
            table->DefineClass=load_ti_uncontrolled_define;
            if((*api)->GetJNIFunctionTable(api,&published)!=JVMTI_ERROR_NONE||!published
                    ||published->DefineClass!=define)failure="JNI_TABLE_CALLER_CHANGE_REACHED_VM";
            if(!failure&&(*api)->SetJNIFunctionTable(api,table)!=JVMTI_ERROR_ACCESS_DENIED)failure="JNI_TABLE_GUARDED_REPLACEMENT_ACCEPTED";
            if(published){(*api)->Deallocate(api,(unsigned char*)published);published=NULL;}
            if(!failure&&((*api)->GetJNIFunctionTable(api,&published)!=JVMTI_ERROR_NONE||!published
                    ||published->DefineClass!=define))failure="JNI_TABLE_REJECTED_PUBLICATION_CHANGED_VM";
        }
    }
    if(published)(*api)->Deallocate(api,(unsigned char*)published);
    if(table)(*api)->Deallocate(api,(unsigned char*)table);
    if((*api)->DisposeEnvironment(api)!=JVMTI_ERROR_NONE&&!failure)failure="JNI_TABLE_ACTUAL_DISPOSE";
    if(failure)load_ti_fail(env,failure);
}
/* Only the public VM, JVMTI and current JNI environment table slots are attack targets. */
typedef struct LoadJvmtiOutputSlot {
    void *address;MEMORY_BASIC_INFORMATION region;unsigned char bytes[sizeof(void*)];
} LoadJvmtiOutputSlot;
static int load_ti_output_slot(LoadJvmtiOutputSlot *slot,void *address){
    MEMORY_BASIC_INFORMATION region={0};
    if(!address||VirtualQuery(address,&region,sizeof(region))!=sizeof(region)||region.State!=MEM_COMMIT
            ||(region.Protect&(PAGE_GUARD|PAGE_NOACCESS)))return 0;
    uintptr_t offset=(uintptr_t)address-(uintptr_t)region.BaseAddress;
    if(offset>=region.RegionSize||sizeof(slot->bytes)>region.RegionSize-offset)return 0;
    slot->address=address;slot->region=region;memcpy(slot->bytes,address,sizeof(slot->bytes));return 1;
}
static int load_ti_output_unchanged(const LoadJvmtiOutputSlot *slot){
    MEMORY_BASIC_INFORMATION region={0};
    return VirtualQuery(slot->address,&region,sizeof(region))==sizeof(region)&&region.State==MEM_COMMIT
            &&region.AllocationBase==slot->region.AllocationBase&&region.Type==slot->region.Type&&region.Protect==slot->region.Protect
            &&!memcmp(slot->bytes,slot->address,sizeof(slot->bytes));
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_hostJvmtiOutput0(JNIEnv *env,jclass check){
    JavaVM *vm=NULL;jvmtiEnv *api=NULL;const char *failure=NULL;jvmtiError error=JVMTI_ERROR_NONE;
    unsigned char *allocated=NULL;char *name=NULL,*signature=NULL;jthread current=NULL;void *attackSlot=NULL;int unexpectedDeallocation=0;
    jintArray copiedArray=NULL;jint *copiedElements=NULL;
    JNIEnv *observedEnv=NULL;jint ordinaryGetEnv=JNI_ERR,ordinaryAttach=JNI_ERR,ordinaryDaemon=JNI_ERR,jniResult=JNI_OK;
    if((*env)->GetJavaVM(env,&vm)!=JNI_OK||!vm
            ||(*vm)->GetEnv(vm,(void**)&api,JVMTI_VERSION_1_2)!=JNI_OK||!api){
        load_ti_fail(env,"JVMTI_OUTPUT_REAL_ENVIRONMENT_REQUIRED");return;
    }
    LoadJvmtiOutputSlot slots[3]={0};
    if(!load_ti_output_slot(&slots[0],vm)||!load_ti_output_slot(&slots[1],api)||!load_ti_output_slot(&slots[2],env)){
        (*api)->DisposeEnvironment(api);load_ti_fail(env,"JVMTI_OUTPUT_ACTUAL_VM_ENVIRONMENT_SLOTS");return;
    }
    jint version=0,copyVersion=0,normalFrames=0;
    jniResult=ordinaryGetEnv=(*vm)->GetEnv(vm,(void**)&observedEnv,JNI_VERSION_1_8);
    if(jniResult!=JNI_OK||observedEnv!=env){failure="VM_OUTPUT_ORDINARY_CURRENT_GETENV";goto done;}
    observedEnv=NULL;jniResult=ordinaryAttach=(*vm)->AttachCurrentThread(vm,(void**)&observedEnv,NULL);
    if(jniResult!=JNI_OK||observedEnv!=env){failure="VM_OUTPUT_ORDINARY_ALREADY_ATTACHED";goto done;}
    observedEnv=NULL;jniResult=ordinaryDaemon=(*vm)->AttachCurrentThreadAsDaemon(vm,(void**)&observedEnv,NULL);
    if(jniResult!=JNI_OK||observedEnv!=env){failure="VM_OUTPUT_ORDINARY_ALREADY_ATTACHED_DAEMON";goto done;}
    error=(*api)->GetVersionNumber(api,&version);
    if(error!=JVMTI_ERROR_NONE||!version){failure="JVMTI_OUTPUT_ORDINARY_VERSION";goto done;}
    error=(*api)->GetVersionNumber(api,NULL);
    if(error!=JVMTI_ERROR_NULL_POINTER){failure="JVMTI_OUTPUT_REQUIRED_NULL_ERROR";goto done;}
    error=(*api)->Allocate(api,32,&allocated);
    if(error!=JVMTI_ERROR_NONE||!allocated){failure="JVMTI_OUTPUT_REAL_ALLOCATION";goto done;}
    memset(allocated,0x5a,32);
    if(allocated[0]!=0x5a||allocated[31]!=0x5a){failure="JVMTI_OUTPUT_REAL_ALLOCATION_CONTENT";goto done;}
    error=(*api)->Deallocate(api,NULL);
    if(error!=JVMTI_ERROR_NONE){failure="JVMTI_OUTPUT_OPTIONAL_NULL_DEALLOCATION";goto done;}
    copiedArray=(*env)->NewIntArray(env,1);
    if(!copiedArray||(*env)->ExceptionCheck(env)){failure="JVMTI_OUTPUT_REAL_JNI_ARRAY";goto done;}
    jboolean isCopy=JNI_FALSE;copiedElements=(*env)->GetIntArrayElements(env,copiedArray,&isCopy);
    if(!copiedElements||isCopy!=JNI_TRUE||(*env)->ExceptionCheck(env)){failure="JVMTI_OUTPUT_REAL_JNI_ARRAY_COPY";goto done;}
    error=(*api)->GetVersionNumber(api,copiedElements);copyVersion=copiedElements[0];
    if(error!=JVMTI_ERROR_NONE||copyVersion!=version){failure="JVMTI_OUTPUT_JNI_COPY_VERSION";goto done;}
    (*env)->ReleaseIntArrayElements(env,copiedArray,copiedElements,JNI_ABORT);copiedElements=NULL;
    if((*env)->ExceptionCheck(env)){failure="JVMTI_OUTPUT_JNI_COPY_ABORT_RELEASE";goto done;}
    (*env)->DeleteLocalRef(env,copiedArray);copiedArray=NULL;
    jmethodID method=(*env)->GetStaticMethodID(env,check,"main","([Ljava/lang/String;)V");
    if(!method||(*env)->ExceptionCheck(env)){failure="JVMTI_OUTPUT_ACTUAL_METHOD_ID";goto done;}
    error=(*api)->GetMethodName(api,method,&name,&signature,NULL);
    if(error!=JVMTI_ERROR_NONE||!name||!signature||strcmp(name,"main")||strcmp(signature,"([Ljava/lang/String;)V")){
        failure="JVMTI_OUTPUT_ORDINARY_METHOD_OPTIONAL_NULL";goto done;
    }
    char invalidCanary=0;char *invalidName=&invalidCanary;
    error=(*api)->GetMethodName(api,NULL,&invalidName,NULL,NULL);
    if(error!=JVMTI_ERROR_INVALID_METHODID){failure="JVMTI_OUTPUT_ORIGINAL_INVALID_METHOD_ID";goto done;}
    error=(*api)->GetCurrentThread(api,&current);
    if(error!=JVMTI_ERROR_NONE||!current){failure="JVMTI_OUTPUT_ACTUAL_CURRENT_THREAD";goto done;}
    jvmtiFrameInfo frames[2]={{0}},before[2];jint count=0;
    error=(*api)->GetStackTrace(api,current,0,2,frames,&count);
    if(error!=JVMTI_ERROR_NONE||count<1||count>2||!frames[0].method){failure="JVMTI_OUTPUT_ORDINARY_STACK_TRACE";goto done;}
    normalFrames=count;
    for(unsigned target=0;target<3;target++){
        void *slot=slots[target].address;attackSlot=slot;
        jniResult=(*vm)->GetEnv(vm,(void**)slot,JVMTI_VERSION_1_2);
        if(jniResult!=JNI_ERR){failure="VM_OUTPUT_CONTROL_GETENV_ACCEPTED";goto done;}
        if(!load_ti_output_unchanged(&slots[0])||!load_ti_output_unchanged(&slots[1])||!load_ti_output_unchanged(&slots[2])){failure="VM_OUTPUT_CONTROL_GETENV_CHANGED_SLOT";goto done;}
        jniResult=(*vm)->AttachCurrentThread(vm,(void**)slot,NULL);
        if(jniResult!=JNI_ERR){failure="VM_OUTPUT_CONTROL_ATTACH_ACCEPTED";goto done;}
        if(!load_ti_output_unchanged(&slots[0])||!load_ti_output_unchanged(&slots[1])||!load_ti_output_unchanged(&slots[2])){failure="VM_OUTPUT_CONTROL_ATTACH_CHANGED_SLOT";goto done;}
        jniResult=(*vm)->AttachCurrentThreadAsDaemon(vm,(void**)slot,NULL);
        if(jniResult!=JNI_ERR){failure="VM_OUTPUT_CONTROL_ATTACH_DAEMON_ACCEPTED";goto done;}
        if(!load_ti_output_unchanged(&slots[0])||!load_ti_output_unchanged(&slots[1])||!load_ti_output_unchanged(&slots[2])){failure="VM_OUTPUT_CONTROL_ATTACH_DAEMON_CHANGED_SLOT";goto done;}
        error=(*api)->GetVersionNumber(api,(jint*)slot);
        if(error!=JVMTI_ERROR_ACCESS_DENIED){failure="JVMTI_OUTPUT_CONTROL_VERSION_ACCEPTED";goto done;}
        if(!load_ti_output_unchanged(&slots[0])||!load_ti_output_unchanged(&slots[1])||!load_ti_output_unchanged(&slots[2])){failure="JVMTI_OUTPUT_CONTROL_VERSION_CHANGED_SLOT";goto done;}
        error=(*api)->Allocate(api,16,(unsigned char**)slot);
        if(error!=JVMTI_ERROR_ACCESS_DENIED){failure="JVMTI_OUTPUT_CONTROL_ALLOCATION_ACCEPTED";goto done;}
        if(!load_ti_output_unchanged(&slots[0])||!load_ti_output_unchanged(&slots[1])||!load_ti_output_unchanged(&slots[2])){failure="JVMTI_OUTPUT_CONTROL_ALLOCATION_CHANGED_SLOT";goto done;}
        char canary=0;char *ordinary=&canary;
        error=(*api)->GetMethodName(api,method,&ordinary,(char**)slot,NULL);
        if(error!=JVMTI_ERROR_ACCESS_DENIED||ordinary!=&canary){failure="JVMTI_OUTPUT_METHOD_PARTIAL_COMMIT";goto done;}
        if(!load_ti_output_unchanged(&slots[0])||!load_ti_output_unchanged(&slots[1])||!load_ti_output_unchanged(&slots[2])){failure="JVMTI_OUTPUT_METHOD_CHANGED_SLOT";goto done;}
        memset(frames,0x5a,sizeof(frames));memcpy(before,frames,sizeof(frames));
        error=(*api)->GetStackTrace(api,current,0,2,frames,(jint*)slot);
        if(error!=JVMTI_ERROR_ACCESS_DENIED||memcmp(frames,before,sizeof(frames))){failure="JVMTI_OUTPUT_STACK_FRAMES_PARTIAL_COMMIT";goto done;}
        if(!load_ti_output_unchanged(&slots[0])||!load_ti_output_unchanged(&slots[1])||!load_ti_output_unchanged(&slots[2])){failure="JVMTI_OUTPUT_STACK_COUNT_CHANGED_SLOT";goto done;}
        count=-37;
        error=(*api)->GetStackTrace(api,current,0,1,(jvmtiFrameInfo*)slot,&count);
        if(error!=JVMTI_ERROR_ACCESS_DENIED||count!=-37){failure="JVMTI_OUTPUT_STACK_COUNT_PARTIAL_COMMIT";goto done;}
        if(!load_ti_output_unchanged(&slots[0])||!load_ti_output_unchanged(&slots[1])||!load_ti_output_unchanged(&slots[2])){failure="JVMTI_OUTPUT_STACK_FRAMES_CHANGED_SLOT";goto done;}
        error=(*api)->Deallocate(api,(unsigned char*)slot);
        if(error!=JVMTI_ERROR_ACCESS_DENIED){unexpectedDeallocation=1;failure="JVMTI_OUTPUT_CONTROL_DEALLOCATION_ACCEPTED";goto done;}
        if(!load_ti_output_unchanged(&slots[0])||!load_ti_output_unchanged(&slots[1])||!load_ti_output_unchanged(&slots[2])){failure="JVMTI_OUTPUT_CONTROL_DEALLOCATION_CHANGED_SLOT";goto done;}
    }
done:
    if(copiedElements)(*env)->ReleaseIntArrayElements(env,copiedArray,copiedElements,JNI_ABORT);
    if(copiedArray)(*env)->DeleteLocalRef(env,copiedArray);
    if(current&&!unexpectedDeallocation)(*env)->DeleteLocalRef(env,current);
    /* Never repair a changed control slot or read one after an unexpected free. */
    if(!unexpectedDeallocation&&load_ti_output_unchanged(&slots[0])&&load_ti_output_unchanged(&slots[1])&&load_ti_output_unchanged(&slots[2])){
        if(allocated){jvmtiError cleanup=(*api)->Deallocate(api,allocated);if(cleanup!=JVMTI_ERROR_NONE&&!failure){failure="JVMTI_OUTPUT_REAL_ALLOCATION_RELEASE";error=cleanup;}}
        if(name){jvmtiError cleanup=(*api)->Deallocate(api,(unsigned char*)name);if(cleanup!=JVMTI_ERROR_NONE&&!failure){failure="JVMTI_OUTPUT_REAL_NAME_RELEASE";error=cleanup;}}
        if(signature){jvmtiError cleanup=(*api)->Deallocate(api,(unsigned char*)signature);if(cleanup!=JVMTI_ERROR_NONE&&!failure){failure="JVMTI_OUTPUT_REAL_SIGNATURE_RELEASE";error=cleanup;}}
        jvmtiError cleanup=(*api)->DisposeEnvironment(api);if(cleanup!=JVMTI_ERROR_NONE&&!failure){failure="JVMTI_OUTPUT_REAL_DISPOSE";error=cleanup;}
    }else if(!failure)failure="JVMTI_OUTPUT_CONTROL_SLOT_CHANGED";
    if(failure){fprintf(stderr,"JVMTI_OUTPUT_FAILED stage=%s target=%p jni=%d jvmti=%d\n",failure,attackSlot,(int)jniResult,(int)error);fflush(stderr);load_ti_fail(env,failure);}
    else {fprintf(stdout,"JVMTI_OUTPUT_ACTUAL vmSlot=%p environmentSlot=%p jniEnvironmentSlot=%p version=0x%X jniCopyVersion=0x%X normalFrames=%d ordinaryGetEnvJNI18=%d attachedCurrent=%d daemonCurrent=%d vmJvmtiJniSlots_GetEnvJVMTI12_Attach_AttachDaemon=JNI_ERR\n",(void*)vm,(void*)api,(void*)env,(unsigned)version,(unsigned)copyVersion,(int)normalFrames,(int)ordinaryGetEnv,(int)ordinaryAttach,(int)ordinaryDaemon);fflush(stdout);}
}
static int load_jni_output_slots_unchanged(const LoadJvmtiOutputSlot *slots){
    return load_ti_output_unchanged(&slots[0])&&load_ti_output_unchanged(&slots[1])&&load_ti_output_unchanged(&slots[2]);
}
/* Consume only the exception expected from this actual call. An unexpected
 * exception (including a failed inspection) remains pending for the caller. */
static int load_jni_output_exception(JNIEnv *env,jclass expected,jmethodID message,const char *text){
    jthrowable failure=(*env)->ExceptionOccurred(env);if(!failure)return 0;
    (*env)->ExceptionClear(env);int matched=(*env)->IsInstanceOf(env,failure,expected);
    if(matched&&text){
        jstring detail=(jstring)(*env)->CallObjectMethod(env,failure,message);
        const char *characters=detail&&!(*env)->ExceptionCheck(env)?(*env)->GetStringUTFChars(env,detail,NULL):NULL;
        matched=characters&&!strcmp(characters,text);
        if(characters)(*env)->ReleaseStringUTFChars(env,detail,characters);
        if(detail)(*env)->DeleteLocalRef(env,detail);
    }
    if((*env)->ExceptionCheck(env))matched=0;
    else if(!matched)(*env)->Throw(env,failure);
    (*env)->DeleteLocalRef(env,failure);return matched;
}
static int load_jni_output_refused(JNIEnv *env,const LoadJvmtiOutputSlot *slots,jclass protectedType,jmethodID message){
    return load_jni_output_slots_unchanged(slots)
            &&load_jni_output_exception(env,protectedType,message,"RONOVA_PROTECTED_NATIVE_OUTPUT");
}
/* Each destination is one real public table slot; no private address or
 * fabricated controller identity is supplied by this fixture. */
static int load_jni_output_control(JNIEnv *env,jintArray array,jstring string,const LoadJvmtiOutputSlot *slots,
        jclass protectedType,jmethodID message,const char **stage,unsigned *target){
    for(*target=0;*target<3;(*target)++){
        void *slot=slots[*target].address;
        *stage="JNI_OUTPUT_CONTROL_GET_JAVA_VM";jint result=(*env)->GetJavaVM(env,(JavaVM**)slot);
        if(!load_jni_output_refused(env,slots,protectedType,message)||result!=JNI_ERR)return 0;
        *stage="JNI_OUTPUT_CONTROL_INT_REGION";(*env)->GetIntArrayRegion(env,array,0,1,(jint*)slot);
        if(!load_jni_output_refused(env,slots,protectedType,message))return 0;
        *stage="JNI_OUTPUT_CONTROL_STRING_REGION";(*env)->GetStringRegion(env,string,0,1,(jchar*)slot);
        if(!load_jni_output_refused(env,slots,protectedType,message))return 0;
        *stage="JNI_OUTPUT_CONTROL_STRING_UTF_REGION";(*env)->GetStringUTFRegion(env,string,0,1,(char*)slot);
        if(!load_jni_output_refused(env,slots,protectedType,message))return 0;
        *stage="JNI_OUTPUT_CONTROL_STRING_CHARS_COPY";const jchar *characters=(*env)->GetStringChars(env,string,(jboolean*)slot);
        if(!load_jni_output_slots_unchanged(slots))return 0;
        if(characters)(*env)->ReleaseStringChars(env,string,characters);
        if(!load_jni_output_refused(env,slots,protectedType,message)||characters)return 0;
        *stage="JNI_OUTPUT_CONTROL_STRING_UTF_CHARS_COPY";const char *utf=(*env)->GetStringUTFChars(env,string,(jboolean*)slot);
        if(!load_jni_output_slots_unchanged(slots))return 0;
        if(utf)(*env)->ReleaseStringUTFChars(env,string,utf);
        if(!load_jni_output_refused(env,slots,protectedType,message)||utf)return 0;
        *stage="JNI_OUTPUT_CONTROL_ARRAY_ELEMENTS_COPY";jint *elements=(*env)->GetIntArrayElements(env,array,(jboolean*)slot);
        if(!load_jni_output_slots_unchanged(slots))return 0;
        if(elements)(*env)->ReleaseIntArrayElements(env,array,elements,JNI_ABORT);
        if(!load_jni_output_refused(env,slots,protectedType,message)||elements)return 0;
        *stage="JNI_OUTPUT_CONTROL_ARRAY_CRITICAL_COPY";void *critical=(*env)->GetPrimitiveArrayCritical(env,array,(jboolean*)slot);
        if(critical)(*env)->ReleasePrimitiveArrayCritical(env,array,critical,JNI_ABORT);
        if(!load_jni_output_refused(env,slots,protectedType,message)||critical)return 0;
        *stage="JNI_OUTPUT_CONTROL_STRING_CRITICAL_COPY";characters=(*env)->GetStringCritical(env,string,(jboolean*)slot);
        if(characters)(*env)->ReleaseStringCritical(env,string,characters);
        if(!load_jni_output_refused(env,slots,protectedType,message)||characters)return 0;
    }
    return 1;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_AuditFixCheck_hostJniOutput0(JNIEnv *env,jclass check){
    (void)check;JavaVM *vm=NULL,*ordinaryVm=NULL;jvmtiEnv *api=NULL;LoadJvmtiOutputSlot slots[3]={0};int observed=0;
    const char *failure=NULL,*stage="JNI_OUTPUT_INITIALIZATION";unsigned target=0;jint result=JNI_OK;jvmtiError disposed=JVMTI_ERROR_NONE;
    jintArray array=NULL;jstring string=NULL;jclass protectedType=NULL,arrayBounds=NULL,stringBounds=NULL,throwable=NULL;
    jmethodID message=NULL;const jchar *characters=NULL;const char *utf=NULL;jint *elements=NULL;
    const jint values[3]={0x13579,-37,0x2468};const jchar text[3]={'R',0,0x4e2d};
    const unsigned char encoded[6]={'R',0xc0,0x80,0xe4,0xb8,0xad};
    if((*env)->ExceptionCheck(env))return JNI_FALSE;
    result=(*env)->GetJavaVM(env,&vm);
    if(result!=JNI_OK||!vm||(*env)->ExceptionCheck(env)){failure="JNI_OUTPUT_REAL_VM_REQUIRED";goto done;}
    result=(*vm)->GetEnv(vm,(void**)&api,JVMTI_VERSION_1_2);
    if(result!=JNI_OK||!api||(*env)->ExceptionCheck(env)){failure="JNI_OUTPUT_REAL_JVMTI_ENV_REQUIRED";goto done;}
    if(!load_ti_output_slot(&slots[0],vm)||!load_ti_output_slot(&slots[1],api)||!load_ti_output_slot(&slots[2],env)){
        failure="JNI_OUTPUT_REAL_PUBLIC_SLOTS_REQUIRED";goto done;
    }
    observed=1;
    unsigned char source=0x31,destination=0x5a;SIZE_T written=0;
    BOOL wrote=WriteProcessMemory(GetCurrentProcess(),&destination,&source,sizeof(source),&written);
    if(!wrote||written!=sizeof(source)||destination!=source||(*env)->ExceptionCheck(env)){failure="JNI_OUTPUT_HOST_ORDINARY_WRITE";goto done;}
    written=(SIZE_T)0x7e;SetLastError(0);
    wrote=WriteProcessMemory(GetCurrentProcess(),slots[2].address,slots[2].bytes,sizeof(slots[2].bytes),&written);
    DWORD writeError=GetLastError();
    if(wrote||writeError!=ERROR_ACCESS_DENIED||written!=(SIZE_T)0x7e||!load_jni_output_slots_unchanged(slots)||(*env)->ExceptionCheck(env)){
        failure="JNI_OUTPUT_HOST_CONTROL_ENV_SAME_VALUE_WRITE";goto done;
    }
    protectedType=(*env)->FindClass(env,"java/lang/IllegalStateException");
    if(!protectedType||(*env)->ExceptionCheck(env)){failure="JNI_OUTPUT_INITIAL_PROTECTED_TYPE";goto done;}
    arrayBounds=(*env)->FindClass(env,"java/lang/ArrayIndexOutOfBoundsException");
    if(!arrayBounds||(*env)->ExceptionCheck(env)){failure="JNI_OUTPUT_INITIAL_ARRAY_BOUNDS_TYPE";goto done;}
    stringBounds=(*env)->FindClass(env,"java/lang/StringIndexOutOfBoundsException");
    if(!stringBounds||(*env)->ExceptionCheck(env)){failure="JNI_OUTPUT_INITIAL_STRING_BOUNDS_TYPE";goto done;}
    throwable=(*env)->FindClass(env,"java/lang/Throwable");
    if(!throwable||(*env)->ExceptionCheck(env)){failure="JNI_OUTPUT_INITIAL_THROWABLE_TYPE";goto done;}
    message=(*env)->GetMethodID(env,throwable,"getMessage","()Ljava/lang/String;");
    if(!message||(*env)->ExceptionCheck(env)){failure="JNI_OUTPUT_INITIAL_EXCEPTION_MESSAGE";goto done;}
    array=(*env)->NewIntArray(env,3);
    if(!array||(*env)->ExceptionCheck(env)){failure="JNI_OUTPUT_INITIAL_ARRAY";goto done;}
    (*env)->SetIntArrayRegion(env,array,0,3,values);
    if((*env)->ExceptionCheck(env)){failure="JNI_OUTPUT_INITIAL_ARRAY_VALUES";goto done;}
    string=(*env)->NewString(env,text,3);
    if(!string||(*env)->ExceptionCheck(env)){failure="JNI_OUTPUT_INITIAL_STRING";goto done;}
    result=(*env)->GetJavaVM(env,&ordinaryVm);
    if(result!=JNI_OK||ordinaryVm!=vm||(*env)->ExceptionCheck(env)){failure="JNI_OUTPUT_ORDINARY_GET_JAVA_VM";goto done;}
    jint integers[3]={0};jchar wide[3]={0};unsigned char bytes[10];memset(bytes,0x5a,sizeof(bytes));
    (*env)->GetIntArrayRegion(env,array,0,3,integers);
    if((*env)->ExceptionCheck(env)||memcmp(integers,values,sizeof(values))){failure="JNI_OUTPUT_ORDINARY_INT_REGION";goto done;}
    (*env)->GetStringRegion(env,string,0,3,wide);
    if((*env)->ExceptionCheck(env)||memcmp(wide,text,sizeof(text))){failure="JNI_OUTPUT_ORDINARY_STRING_REGION";goto done;}
    (*env)->GetStringUTFRegion(env,string,0,3,(char*)bytes);
    if((*env)->ExceptionCheck(env)||memcmp(bytes,encoded,sizeof(encoded))||bytes[6]!=0||bytes[7]!=0x5a||bytes[8]!=0x5a||bytes[9]!=0x5a){failure="JNI_OUTPUT_ORDINARY_UTF_REGION";goto done;}
    memset(bytes,0x5a,sizeof(bytes));(*env)->GetStringUTFRegion(env,string,3,0,(char*)bytes);
    if((*env)->ExceptionCheck(env)||bytes[0]!=0){failure="JNI_OUTPUT_ORDINARY_EMPTY_UTF_REGION";goto done;}
    for(unsigned i=1;i<sizeof(bytes);i++)if(bytes[i]!=0x5a){failure="JNI_OUTPUT_EMPTY_UTF_REGION_CHANGED_TAIL";goto done;}
    jboolean copied=0x7e;characters=(*env)->GetStringChars(env,string,&copied);
    if(!characters||(*env)->ExceptionCheck(env)||(copied!=JNI_TRUE&&copied!=JNI_FALSE)||memcmp(characters,text,sizeof(text))){failure="JNI_OUTPUT_ORDINARY_STRING_CHARS";goto done;}
    (*env)->ReleaseStringChars(env,string,characters);characters=NULL;
    if((*env)->ExceptionCheck(env)){failure="JNI_OUTPUT_ORDINARY_STRING_CHARS_RELEASE";goto done;}
    copied=0x7e;utf=(*env)->GetStringUTFChars(env,string,&copied);
    if(!utf||(*env)->ExceptionCheck(env)||(copied!=JNI_TRUE&&copied!=JNI_FALSE)||memcmp(utf,encoded,sizeof(encoded))||utf[sizeof(encoded)]!=0){failure="JNI_OUTPUT_ORDINARY_UTF_CHARS";goto done;}
    (*env)->ReleaseStringUTFChars(env,string,utf);utf=NULL;
    if((*env)->ExceptionCheck(env)){failure="JNI_OUTPUT_ORDINARY_UTF_CHARS_RELEASE";goto done;}
    copied=0x7e;elements=(*env)->GetIntArrayElements(env,array,&copied);
    if(!elements||(*env)->ExceptionCheck(env)||copied!=JNI_TRUE||memcmp(elements,values,sizeof(values))){failure="JNI_OUTPUT_ORDINARY_ARRAY_ELEMENTS";goto done;}
    memset(elements,0x5a,sizeof(values));(*env)->GetIntArrayRegion(env,array,0,3,elements);
    if((*env)->ExceptionCheck(env)||memcmp(elements,values,sizeof(values))){failure="JNI_OUTPUT_ORDINARY_ARRAY_COPY_PAYLOAD_REGION";goto done;}
    (*env)->ReleaseIntArrayElements(env,array,elements,JNI_ABORT);elements=NULL;
    if((*env)->ExceptionCheck(env)){failure="JNI_OUTPUT_ORDINARY_ARRAY_ELEMENTS_RELEASE";goto done;}
    copied=0x7e;void *critical=(*env)->GetPrimitiveArrayCritical(env,array,&copied);
    int valid=critical&&copied==JNI_TRUE&&!memcmp(critical,values,sizeof(values));
    if(critical)(*env)->ReleasePrimitiveArrayCritical(env,array,critical,JNI_ABORT);
    if(!valid||(*env)->ExceptionCheck(env)){failure="JNI_OUTPUT_ORDINARY_ARRAY_CRITICAL_RELEASE";goto done;}
    copied=0x7e;const jchar *criticalText=(*env)->GetStringCritical(env,string,&copied);
    valid=criticalText&&copied==JNI_TRUE&&!memcmp(criticalText,text,sizeof(text));
    if(criticalText)(*env)->ReleaseStringCritical(env,string,criticalText);
    if(!valid||(*env)->ExceptionCheck(env)){failure="JNI_OUTPUT_ORDINARY_STRING_CRITICAL_RELEASE";goto done;}
    if(!load_jni_output_control(env,array,string,slots,protectedType,message,&stage,&target)){failure=stage;goto done;}
    target=2;stage="JNI_OUTPUT_ARRAY_ORIGINAL_BOUNDS";
    (*env)->GetIntArrayRegion(env,array,2,2,(jint*)slots[target].address);
    if(!load_jni_output_slots_unchanged(slots)||!load_jni_output_exception(env,arrayBounds,message,NULL)){failure=stage;goto done;}
    stage="JNI_OUTPUT_STRING_ORIGINAL_BOUNDS";
    (*env)->GetStringRegion(env,string,2,2,(jchar*)slots[target].address);
    if(!load_jni_output_slots_unchanged(slots)||!load_jni_output_exception(env,stringBounds,message,NULL)){failure=stage;goto done;}
    stage="JNI_OUTPUT_UTF_ORIGINAL_BOUNDS";
    (*env)->GetStringUTFRegion(env,string,2,2,(char*)slots[target].address);
    if(!load_jni_output_slots_unchanged(slots)||!load_jni_output_exception(env,stringBounds,message,NULL)){failure=stage;goto done;}
done:
    /* A broken control slot is evidence, never an address to repair. Avoid
     * further API dereferences in that failure branch. */
    if(observed&&!load_jni_output_slots_unchanged(slots)){
        fprintf(stderr,"JNI_OUTPUT_FAILED stage=%s target=%u control_slot_changed\n",failure?failure:stage,target);fflush(stderr);return JNI_FALSE;
    }
    if(elements)(*env)->ReleaseIntArrayElements(env,array,elements,JNI_ABORT);
    if(utf)(*env)->ReleaseStringUTFChars(env,string,utf);
    if(characters)(*env)->ReleaseStringChars(env,string,characters);
    if(array)(*env)->DeleteLocalRef(env,array);if(string)(*env)->DeleteLocalRef(env,string);
    if(throwable)(*env)->DeleteLocalRef(env,throwable);if(stringBounds)(*env)->DeleteLocalRef(env,stringBounds);
    if(arrayBounds)(*env)->DeleteLocalRef(env,arrayBounds);if(protectedType)(*env)->DeleteLocalRef(env,protectedType);
    if(api){disposed=(*api)->DisposeEnvironment(api);if(disposed!=JVMTI_ERROR_NONE&&!failure)failure="JNI_OUTPUT_REAL_DISPOSE";}
    if((*env)->ExceptionCheck(env)&&!failure)failure="JNI_OUTPUT_UNEXPECTED_EXCEPTION";
    if(failure){fprintf(stderr,"JNI_OUTPUT_FAILED stage=%s target=%u jni=%d dispose=%d\n",failure,target,(int)result,(int)disposed);fflush(stderr);load_ti_fail(env,failure);return JNI_FALSE;}
    fprintf(stdout,"JNI_OUTPUT_PASS\n");fflush(stdout);return JNI_TRUE;
}
typedef BOOL (WINAPI *LoadMemoryRead)(HANDLE,LPCVOID,LPVOID,SIZE_T,SIZE_T*);
extern LoadMemoryRead __imp_ReadProcessMemory;
/* Exercise only local output admission through the real import and GPA. The
 * source handle is either the current pseudo handle or its VM_READ-only alias. */
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_AuditFixCheck_hostMemoryRead0(JNIEnv *env,jclass check){
    (void)check;JavaVM *vm=NULL;jvmtiEnv *api=NULL;HANDLE actual=NULL;
    LoadJvmtiOutputSlot slots[3]={0};int observed=0;unsigned mode=0,target=3;
    const char *failure=NULL;jint result=JNI_OK;jvmtiError disposed=JVMTI_ERROR_NONE;DWORD error=0;
    const unsigned char source[sizeof(void*)]={0x31,0x53,0x75,0x19};
    unsigned char output[sizeof(void*)+2],before[sizeof(output)];memset(before,0x5a,sizeof(before));
    if((*env)->ExceptionCheck(env))return JNI_FALSE;
    result=(*env)->GetJavaVM(env,&vm);
    if(result!=JNI_OK||!vm||(*env)->ExceptionCheck(env)){failure="HOST_MEMORY_READ_REAL_VM_REQUIRED";goto done;}
    result=(*vm)->GetEnv(vm,(void**)&api,JVMTI_VERSION_1_2);
    if(result!=JNI_OK||!api||(*env)->ExceptionCheck(env)){failure="HOST_MEMORY_READ_REAL_JVMTI_ENV_REQUIRED";goto done;}
    if(!load_ti_output_slot(&slots[0],vm)||!load_ti_output_slot(&slots[1],api)||!load_ti_output_slot(&slots[2],env)){
        failure="HOST_MEMORY_READ_REAL_PUBLIC_SLOTS_REQUIRED";goto done;
    }
    observed=1;
    HMODULE kernel=GetModuleHandleW(L"kernel32.dll");
    LoadMemoryRead entries[]={__imp_ReadProcessMemory,kernel?(LoadMemoryRead)GetProcAddress(kernel,"ReadProcessMemory"):NULL};
    if(!load_private_entry((void*)entries[0])||!load_private_entry((void*)entries[1])){
        failure="HOST_MEMORY_READ_ACTUAL_IMPORT_OR_GPA_NOT_CONTROLLED";goto done;
    }
    if(!DuplicateHandle(GetCurrentProcess(),GetCurrentProcess(),GetCurrentProcess(),&actual,PROCESS_VM_READ,FALSE,0)){
        error=GetLastError();failure="HOST_MEMORY_READ_VM_READ_ONLY_CURRENT_ALIAS";goto done;
    }
    for(mode=0;mode<2;mode++){
        HANDLE process=mode?actual:GetCurrentProcess();SIZE_T copied=(SIZE_T)0x7e;BOOL read;
        target=3;memcpy(output,before,sizeof(output));
        read=entries[mode](process,source,output+1,sizeof(source),&copied);error=GetLastError();
        if(!read||copied!=sizeof(source)||memcmp(output+1,source,sizeof(source))||output[0]!=0x5a||output[sizeof(output)-1]!=0x5a){
            failure="HOST_MEMORY_READ_ORDINARY_BYTES_AND_COUNT";goto done;
        }
        memcpy(output,before,sizeof(output));
        read=entries[mode](process,source,output+1,sizeof(source),NULL);error=GetLastError();
        if(!read||memcmp(output+1,source,sizeof(source))||output[0]!=0x5a||output[sizeof(output)-1]!=0x5a){
            failure="HOST_MEMORY_READ_ORDINARY_OPTIONAL_NULL_COUNT";goto done;
        }
        copied=(SIZE_T)0x7e;
        read=entries[mode](process,source,slots[0].address,0,&copied);error=GetLastError();
        if(!read||copied!=0||!load_jni_output_slots_unchanged(slots)){
            failure="HOST_MEMORY_READ_ZERO_LENGTH_NO_BUFFER_WRITE";goto done;
        }
        for(target=0;target<3;target++){
            /* The payload attempt copies the slot's own value; it never
             * deliberately replaces a live VM function-table pointer. */
            copied=(SIZE_T)0x7e;SetLastError(0);
            read=entries[mode](process,slots[target].bytes,slots[target].address,sizeof(slots[target].bytes),&copied);error=GetLastError();
            if(read||error!=ERROR_ACCESS_DENIED||copied!=(SIZE_T)0x7e||!load_jni_output_slots_unchanged(slots)){
                failure="HOST_MEMORY_READ_PROTECTED_PAYLOAD_OUTPUT";goto done;
            }
            memcpy(output,before,sizeof(output));SetLastError(0);
            read=entries[mode](process,source,output+1,sizeof(source),(SIZE_T*)slots[target].address);error=GetLastError();
            if(read||error!=ERROR_ACCESS_DENIED||memcmp(output,before,sizeof(output))||!load_jni_output_slots_unchanged(slots)){
                failure="HOST_MEMORY_READ_PROTECTED_COUNT_NO_PARTIAL_PAYLOAD";goto done;
            }
            SetLastError(0);
            read=entries[mode](process,source,(void*)(uintptr_t)1,0,(SIZE_T*)slots[target].address);error=GetLastError();
            if(read||error!=ERROR_ACCESS_DENIED||!load_jni_output_slots_unchanged(slots)){
                failure="HOST_MEMORY_READ_ZERO_LENGTH_PROTECTED_COUNT";goto done;
            }
        }
    }
done:
    /* Never repair or dereference a table after an actual output changed it. */
    if(observed&&!load_jni_output_slots_unchanged(slots)){
        fprintf(stderr,"HOST_MEMORY_READ_FAILED stage=%s route=%u target=%u control_slot_changed\n",failure?failure:"cleanup",mode,target);fflush(stderr);return JNI_FALSE;
    }
    if(actual&&!CloseHandle(actual)&&!failure){error=GetLastError();failure="HOST_MEMORY_READ_CURRENT_ALIAS_CLOSE";}
    if(api){disposed=(*api)->DisposeEnvironment(api);if(disposed!=JVMTI_ERROR_NONE&&!failure)failure="HOST_MEMORY_READ_REAL_DISPOSE";}
    if((*env)->ExceptionCheck(env)&&!failure)failure="HOST_MEMORY_READ_UNEXPECTED_EXCEPTION";
    if(failure){
        fprintf(stderr,"HOST_MEMORY_READ_FAILED stage=%s route=%u target=%u win32=%lu jni=%d dispose=%d\n",failure,mode,target,(unsigned long)error,(int)result,(int)disposed);
        fflush(stderr);load_ti_fail(env,failure);return JNI_FALSE;
    }
    fprintf(stdout,"HOST_MEMORY_READ_PASS\n");fflush(stdout);return JNI_TRUE;
}
static int load_jvmti_environment(JavaVM *vm,JNIEnv *env,jclass check){
    jvmtiEnv *api=NULL;
    if((*vm)->GetEnv(vm,(void**)&api,JVMTI_VERSION_1_2)!=JNI_OK||!api){load_ti_fail(env,"REAL_JVMTI_GETENV_FAILED");return 0;}
    load_ti_identity=api;InterlockedExchange(&load_ti_first,0);InterlockedExchange(&load_ti_second,0);
    jvmtiCapabilities capabilities={0};capabilities.can_generate_all_class_hook_events=1;
    int ready=(*api)->AddCapabilities(api,&capabilities)==JVMTI_ERROR_NONE;
    jniNativeInterface *table=NULL;
    if(ready){ready=(*api)->GetJNIFunctionTable(api,&table)==JVMTI_ERROR_NONE&&table;
        if(table){table->DefineClass=load_ti_uncontrolled_define;
            ready=ready&&(*api)->SetJNIFunctionTable(api,table)==JVMTI_ERROR_ACCESS_DENIED;(*api)->Deallocate(api,(unsigned char*)table);}}
    jvmtiEventCallbacks callbacks={0};callbacks.ClassFileLoadHook=load_ti_callback_first;
    ready=ready&&(*api)->SetEventCallbacks(api,&callbacks,sizeof(callbacks))==JVMTI_ERROR_NONE
            &&(*api)->SetEventNotificationMode(api,JVMTI_ENABLE,JVMTI_EVENT_CLASS_FILE_LOAD_HOOK,NULL)==JVMTI_ERROR_NONE;
    jclass classType=(*env)->FindClass(env,"java/lang/Class");jmethodID getLoader=classType?(*env)->GetMethodID(env,classType,"getClassLoader","()Ljava/lang/ClassLoader;"):NULL;
    jobject loader=getLoader?(*env)->CallObjectMethod(env,check,getLoader):NULL;
    if(classType)(*env)->DeleteLocalRef(env,classType);
    if(ready&&!(*env)->ExceptionCheck(env))ready=load_ti_define(env,loader,'A')==9&&load_ti_first==1&&load_ti_second==0;
    callbacks.ClassFileLoadHook=load_ti_callback_second;
    if(ready&&!(*env)->ExceptionCheck(env))ready=(*api)->SetEventCallbacks(api,&callbacks,sizeof(callbacks))==JVMTI_ERROR_NONE
            &&load_ti_define(env,loader,'B')==7&&load_ti_first==1&&load_ti_second==1;
    if(ready&&!(*env)->ExceptionCheck(env))ready=(*api)->SetEventNotificationMode(api,JVMTI_DISABLE,JVMTI_EVENT_CLASS_FILE_LOAD_HOOK,NULL)==JVMTI_ERROR_NONE
            &&load_ti_define(env,loader,'C')==7&&load_ti_first==1&&load_ti_second==1;
    if(ready&&!(*env)->ExceptionCheck(env))ready=(*api)->SetEventNotificationMode(api,JVMTI_ENABLE,JVMTI_EVENT_CLASS_FILE_LOAD_HOOK,NULL)==JVMTI_ERROR_NONE;
    /* Disposal is the real lifecycle action; never call an invalid saved API
     * after it. The following actual definition must no longer reach it. */
    jvmtiError disposed=(*api)->DisposeEnvironment(api);load_ti_identity=NULL;
    if(ready&&!(*env)->ExceptionCheck(env))ready=disposed==JVMTI_ERROR_NONE&&load_ti_define(env,loader,'D')==7&&load_ti_first==1&&load_ti_second==1;
    if(loader)(*env)->DeleteLocalRef(env,loader);
    if(!ready||(*env)->ExceptionCheck(env)){load_ti_fail(env,"JVMTI_REAL_CALLBACK_EVENT_OR_DISPOSAL_CONTROL_FAILED");return 0;}
    fprintf(stdout,"JVMTI_ENVIRONMENT_PASS actual_GetEnv_identity protected_JNI_DefineClass_overwrite_refused class_callback_changed_real_method replaced_callback_preserved_unmodified_method disabled_event_stopped_dispatch actual_Dispose_stopped_dispatch\n");fflush(stdout);return 1;
}
#endif

#ifdef AUDIT_JVMTI_DISPATCH_CHECK
/* This entry exercises only current heap/extension dispatch. It creates no
 * Host/Mod registration. Callbacks use no JNI/Java or locks; the modern heap
 * callback also exercises only callback-safe SetEnvironmentLocalStorage. */
typedef struct LoadDispatchReferenceTrace {
    jint kind,length,fieldIndex,result,branch;
    int infoPresent,tagPresent,referrerPresent,sameTagPointer,userdataMatches;
    jlong classTag,referrerClassTag,size,tagBefore,referrerBefore,tagAfter,referrerAfter;
    uintptr_t dataPointer,tagPointer,referrerPointer;
} LoadDispatchReferenceTrace;
typedef struct LoadDispatchHeap {
    jvmtiEnv *api;
    jlong classTag,firstBefore,secondBefore,firstAfter,secondAfter,decoyTag;
    unsigned firstSeen,secondSeen;int error,storageCalled;jvmtiError storageResult;
    unsigned referenceCalls,referenceCount,referenceDropped;
    int childEdge,decoyEdge;
    LoadDispatchReferenceTrace references[16];
} LoadDispatchHeap;
static LoadDispatchHeap *load_dispatch_heap_active;
static int load_dispatch_heap_wrong_data;
static jvmtiEnv *load_dispatch_api;
static JNIEnv *load_dispatch_caller_jni,*load_dispatch_event_jni;
static DWORD load_dispatch_caller_thread,load_dispatch_event_thread;
static jint load_dispatch_extension_index;
static volatile LONG load_dispatch_first,load_dispatch_second,load_dispatch_event_error;
static const char load_dispatch_unload_first[]="dev/ronova/pro/JvmtiPayloadU";
static const char load_dispatch_unload_second[]="dev/ronova/pro/JvmtiPayloadV";
static const char load_dispatch_unload_cleared[]="dev/ronova/pro/JvmtiPayloadW";
static void load_dispatch_fail(JNIEnv *env,const char *stage,jvmtiError error){
    fprintf(stderr,"JVMTI_DISPATCH_FAILED stage=%s jvmti=%d\n",stage,(int)error);fflush(stderr);
    if((*env)->ExceptionCheck(env))return;
    jclass failure=(*env)->FindClass(env,"java/lang/AssertionError");
    if(failure){(*env)->ThrowNew(env,failure,stage);(*env)->DeleteLocalRef(env,failure);}
}
static LoadDispatchHeap *load_dispatch_heap_data(void *data){
    if(data!=load_dispatch_heap_active||data==NULL){load_dispatch_heap_wrong_data=1;return NULL;}
    return (LoadDispatchHeap*)data;
}
static int load_dispatch_heap_object(LoadDispatchHeap *state,jlong classTag,jlong size,jlong *tag){
    if(!tag||size<=0||classTag!=state->classTag){state->error=1;return 0;}
    if(*tag==state->firstBefore){state->firstSeen++;*tag=state->firstAfter;}
    else if(*tag==state->secondBefore){state->secondSeen++;*tag=state->secondAfter;}
    else{state->error=1;return 0;}
    return 1;
}
static jint JNICALL load_dispatch_heap_iteration(jlong classTag,jlong size,jlong *tag,jint length,void *data){
    LoadDispatchHeap *state=load_dispatch_heap_data(data);(void)length;
    if(state&&!state->storageCalled){
        state->storageCalled=1;state->storageResult=(*state->api)->SetEnvironmentLocalStorage(state->api,data);
        if(state->storageResult!=JVMTI_ERROR_NONE){state->error=1;return JVMTI_VISIT_ABORT;}
    }
    return state&&load_dispatch_heap_object(state,classTag,size,tag)?JVMTI_VISIT_OBJECTS:JVMTI_VISIT_ABORT;
}
static jvmtiIterationControl JNICALL load_dispatch_legacy_object(jlong classTag,jlong size,jlong *tag,void *data){
    LoadDispatchHeap *state=load_dispatch_heap_data(data);
    return state&&load_dispatch_heap_object(state,classTag,size,tag)?JVMTI_ITERATION_CONTINUE:JVMTI_ITERATION_ABORT;
}
static jint JNICALL load_dispatch_heap_reference(jvmtiHeapReferenceKind kind,const jvmtiHeapReferenceInfo *info,
        jlong classTag,jlong referrerClassTag,jlong size,jlong *tag,jlong *referrerTag,jint length,void *data){
    LoadDispatchHeap *expected=load_dispatch_heap_active;LoadDispatchReferenceTrace *trace=NULL;jint result=JVMTI_VISIT_ABORT,branch=1;
    /* Only write this fixed caller-owned buffer while the synchronous heap
     * call runs. Formatting, JNI readback and output happen after it returns. */
    if(expected){
        expected->referenceCalls++;
        if(expected->referenceCount<sizeof(expected->references)/sizeof(expected->references[0])){
            trace=&expected->references[expected->referenceCount++];
            trace->kind=kind;trace->length=length;trace->fieldIndex=kind==JVMTI_HEAP_REFERENCE_FIELD&&info?info->field.index:-1;
            trace->infoPresent=info!=NULL;trace->tagPresent=tag!=NULL;trace->referrerPresent=referrerTag!=NULL;
            trace->sameTagPointer=tag!=NULL&&tag==referrerTag;trace->userdataMatches=data==expected;
            trace->classTag=classTag;trace->referrerClassTag=referrerClassTag;trace->size=size;
            trace->tagBefore=tag?*tag:0;trace->referrerBefore=referrerTag?*referrerTag:0;
            trace->dataPointer=(uintptr_t)data;trace->tagPointer=(uintptr_t)tag;trace->referrerPointer=(uintptr_t)referrerTag;
        }else expected->referenceDropped++;
    }
    LoadDispatchHeap *state=load_dispatch_heap_data(data);
    if(!state)goto observed;
    if(!tag||classTag!=state->classTag||size<=0){state->error=1;branch=2;goto observed;}
    /* Class/tag filters select reported referrees, not the traversed graph.
     * After the exact child edge wrote both tags, another (possibly untagged)
     * carrier may reference either object. Preserve that completed write and
     * stop following this additional edge; do not write or count it again. */
    if(state->secondSeen==1&&(*tag==state->firstAfter||*tag==state->secondAfter)){
        result=0;branch=7;goto observed;
    }
    if(*tag==state->firstBefore){result=JVMTI_VISIT_OBJECTS;branch=3;goto observed;}
    if(*tag==state->secondBefore){
        if(kind!=JVMTI_HEAP_REFERENCE_FIELD||!info||!referrerTag||*referrerTag!=state->firstBefore
                ||referrerClassTag!=state->classTag){state->error=1;branch=4;goto observed;}
        state->secondSeen++;*tag=state->secondAfter;*referrerTag=state->firstAfter;result=0;branch=5;goto observed;
    }
    state->error=1;branch=6;
observed:
    if(trace){trace->result=result;trace->branch=branch;trace->tagAfter=tag?*tag:0;trace->referrerAfter=referrerTag?*referrerTag:0;}
    return result;
}
static jvmtiIterationControl JNICALL load_dispatch_legacy_reference(jvmtiObjectReferenceKind kind,jlong classTag,
        jlong size,jlong *tag,jlong referrerTag,jint index,void *data){
    LoadDispatchHeap *state=load_dispatch_heap_data(data);(void)index;
    if(!state)return JVMTI_ITERATION_ABORT;
    if(tag&&*tag==state->secondBefore){
        if(kind!=JVMTI_REFERENCE_FIELD||classTag!=state->classTag||size<=0||referrerTag!=state->firstBefore){state->error=1;return JVMTI_ITERATION_ABORT;}
        state->secondSeen++;*tag=state->secondAfter;
    }
    /* Do not traverse unrelated class/loader graphs from this exact root. */
    return JVMTI_ITERATION_IGNORE;
}
static int load_dispatch_tags(jvmtiEnv *api,jobject first,jobject second,jobject decoy,const LoadDispatchHeap *state){
    jlong a=0,b=0,c=0;
    return !load_dispatch_heap_wrong_data&&!state->error
            &&(*api)->GetTag(api,first,&a)==JVMTI_ERROR_NONE&&a==state->firstAfter
            &&(*api)->GetTag(api,second,&b)==JVMTI_ERROR_NONE&&b==state->secondAfter
            &&(*api)->GetTag(api,decoy,&c)==JVMTI_ERROR_NONE&&c==state->decoyTag;
}
static int load_dispatch_reference_graph(JNIEnv *env,jclass type,jobject first,jobject second,jobject decoy,LoadDispatchHeap *state){
    jfieldID child=(*env)->GetFieldID(env,type,"child","Ljava/lang/Object;");
    jfieldID other=child?(*env)->GetFieldID(env,type,"decoy","Ljava/lang/Object;"):NULL;
    if(!child||!other||(*env)->ExceptionCheck(env))return 0;
    jobject actualChild=(*env)->GetObjectField(env,first,child),actualDecoy=(*env)->GetObjectField(env,first,other);
    state->childEdge=(*env)->IsSameObject(env,actualChild,second);state->decoyEdge=(*env)->IsSameObject(env,actualDecoy,decoy);
    if(actualChild)(*env)->DeleteLocalRef(env,actualChild);if(actualDecoy)(*env)->DeleteLocalRef(env,actualDecoy);
    return !(*env)->ExceptionCheck(env);
}
static void load_dispatch_reference_report(jvmtiEnv *api,jclass type,jobject first,jobject second,jobject decoy,
        const LoadDispatchHeap *state,jvmtiError result){
    jobject objects[]={type,first,second,decoy};jlong values[4]={0};jvmtiError errors[4];
    for(unsigned i=0;i<4;i++)errors[i]=(*api)->GetTag(api,objects[i],&values[i]);
    fprintf(stderr,"JVMTI_FOLLOW_ACTUAL result=%d calls=%u stored=%u dropped=%u firstSeen=%u secondSeen=%u error=%d wrongUserdata=%d childEdge=%d decoyEdge=%d expectedData=%p\n",
        result,state->referenceCalls,state->referenceCount,state->referenceDropped,state->firstSeen,state->secondSeen,state->error,load_dispatch_heap_wrong_data,state->childEdge,state->decoyEdge,(const void*)state);
    fprintf(stderr,"JVMTI_FOLLOW_TAGS expectedClass=%lld beforeFirst=%lld beforeSecond=%lld expectedFirst=%lld expectedSecond=%lld expectedDecoy=%lld classError=%d classActual=%lld firstError=%d firstActual=%lld secondError=%d secondActual=%lld decoyError=%d decoyActual=%lld\n",
        (long long)state->classTag,(long long)state->firstBefore,(long long)state->secondBefore,(long long)state->firstAfter,(long long)state->secondAfter,(long long)state->decoyTag,
        errors[0],(long long)values[0],errors[1],(long long)values[1],errors[2],(long long)values[2],errors[3],(long long)values[3]);
    for(unsigned i=0;i<state->referenceCount;i++){
        const LoadDispatchReferenceTrace *trace=&state->references[i];
        fprintf(stderr,"JVMTI_FOLLOW_TUPLE sample=%u kind=%d info=%d fieldIndex=%d classTag=%lld referrerClassTag=%lld size=%lld length=%d tagPresent=%d referrerPresent=%d alias=%d tagBefore=%lld referrerBefore=%lld tagAfter=%lld referrerAfter=%lld userdataMatch=%d userdata=%p tagPtr=%p referrerPtr=%p branch=%d returnedFlags=0x%X\n",
            i,trace->kind,trace->infoPresent,trace->fieldIndex,(long long)trace->classTag,(long long)trace->referrerClassTag,(long long)trace->size,trace->length,
            trace->tagPresent,trace->referrerPresent,trace->sameTagPointer,(long long)trace->tagBefore,(long long)trace->referrerBefore,(long long)trace->tagAfter,(long long)trace->referrerAfter,
            trace->userdataMatches,(void*)trace->dataPointer,(void*)trace->tagPointer,(void*)trace->referrerPointer,trace->branch,(unsigned)trace->result);
    }
    fflush(stderr);
}
static int load_dispatch_heap(JNIEnv *env,jvmtiEnv *api,jclass type,jobject first,jobject second,jobject decoy){
    jobject objects[]={type,first,second,decoy};jlong original[4]={0};unsigned saved=0;jvmtiError error=JVMTI_ERROR_NONE;
    void *previousStorage=NULL,*actualStorage=NULL;int storageSaved=0;
    jclass a=(*env)->GetObjectClass(env,first),b=(*env)->GetObjectClass(env,second),c=(*env)->GetObjectClass(env,decoy);
    int actual=a&&b&&c&&(*env)->IsSameObject(env,a,type)&&(*env)->IsSameObject(env,b,type)&&!(*env)->IsSameObject(env,c,type)
            &&!(*env)->IsSameObject(env,first,second)&&!(*env)->IsSameObject(env,first,decoy)&&!(*env)->IsSameObject(env,second,decoy);
    if(a)(*env)->DeleteLocalRef(env,a);if(b)(*env)->DeleteLocalRef(env,b);if(c)(*env)->DeleteLocalRef(env,c);
    const char *stage="actual_object_type_identity";int ready=0;
    if(!actual||(*env)->ExceptionCheck(env))goto done;
    stage="actual_GetEnvironmentLocalStorage_before_heap";
    error=(*api)->GetEnvironmentLocalStorage(api,&previousStorage);if(error!=JVMTI_ERROR_NONE)goto done;storageSaved=1;
    stage="actual_GetTag_before_heap";
    for(;saved<4;saved++){error=(*api)->GetTag(api,objects[saved],&original[saved]);if(error!=JVMTI_ERROR_NONE)goto done;}
    LoadDispatchHeap state={0};state.api=api;state.classTag=0x52444801;state.firstBefore=0x52444802;state.secondBefore=0x52444803;state.decoyTag=0x52444804;
    jlong initial[]={state.classTag,state.firstBefore,state.secondBefore,state.decoyTag};
    for(unsigned i=0;i<4;i++){error=(*api)->SetTag(api,objects[i],initial[i]);if(error!=JVMTI_ERROR_NONE){stage="actual_SetTag";goto done;}}
    load_dispatch_heap_active=&state;load_dispatch_heap_wrong_data=0;
    state.firstAfter=0x52444812;state.secondAfter=0x52444813;
    jvmtiHeapCallbacks callbacks={0};callbacks.heap_iteration_callback=load_dispatch_heap_iteration;
    stage="IterateThroughHeap_type_userdata_tag_ptr";
    error=(*api)->IterateThroughHeap(api,JVMTI_HEAP_FILTER_UNTAGGED,type,&callbacks,&state);
    if(state.storageCalled&&state.storageResult!=JVMTI_ERROR_NONE){stage="callback_safe_SetEnvironmentLocalStorage";error=state.storageResult;goto done;}
    if(error!=JVMTI_ERROR_NONE||state.firstSeen!=1||state.secondSeen!=1||!load_dispatch_tags(api,first,second,decoy,&state))goto done;
    stage="callback_safe_SetEnvironmentLocalStorage_actual_readback";
    error=(*api)->GetEnvironmentLocalStorage(api,&actualStorage);
    if(error!=JVMTI_ERROR_NONE||!state.storageCalled||state.storageResult!=JVMTI_ERROR_NONE||actualStorage!=&state)goto done;
    state.firstBefore=state.firstAfter;state.secondBefore=state.secondAfter;state.firstAfter=0x52444822;state.secondAfter=0x52444823;
    state.firstSeen=state.secondSeen=0;callbacks.heap_iteration_callback=NULL;callbacks.heap_reference_callback=load_dispatch_heap_reference;
    stage="actual_FollowReferences_root_fields";
    if(!load_dispatch_reference_graph(env,type,first,second,decoy,&state))goto done;
    stage="FollowReferences_actual_root_type_referrer_tag_ptr";
    error=(*api)->FollowReferences(api,JVMTI_HEAP_FILTER_UNTAGGED,type,first,&callbacks,&state);
    if(error!=JVMTI_ERROR_NONE||state.secondSeen!=1||!load_dispatch_tags(api,first,second,decoy,&state)){
        load_dispatch_reference_report(api,type,first,second,decoy,&state,error);goto done;
    }
    state.firstBefore=state.firstAfter;state.secondBefore=state.secondAfter;state.firstAfter=0x52444832;state.secondAfter=0x52444833;
    state.firstSeen=state.secondSeen=0;
    stage="IterateOverInstancesOfClass_type_userdata_tag_ptr";
    error=(*api)->IterateOverInstancesOfClass(api,type,JVMTI_HEAP_OBJECT_TAGGED,load_dispatch_legacy_object,&state);
    if(error!=JVMTI_ERROR_NONE||state.firstSeen!=1||state.secondSeen!=1||!load_dispatch_tags(api,first,second,decoy,&state))goto done;
    state.firstBefore=state.firstAfter;state.secondBefore=state.secondAfter;state.secondAfter=0x52444843;state.firstSeen=state.secondSeen=0;
    stage="IterateOverObjectsReachableFromObject_actual_root_userdata_tag_ptr";
    error=(*api)->IterateOverObjectsReachableFromObject(api,first,load_dispatch_legacy_reference,&state);
    if(error!=JVMTI_ERROR_NONE||state.secondSeen!=1||!load_dispatch_tags(api,first,second,decoy,&state))goto done;
    ready=1;
done:
    load_dispatch_heap_active=NULL;
    for(unsigned i=0;i<saved;i++){jvmtiError restored=(*api)->SetTag(api,objects[i],original[i]);if(restored!=JVMTI_ERROR_NONE){ready=0;stage="restore_actual_object_tags";error=restored;}}
    if(storageSaved){jvmtiError restored=(*api)->SetEnvironmentLocalStorage(api,previousStorage);if(restored!=JVMTI_ERROR_NONE){ready=0;stage="restore_actual_environment_storage";error=restored;}}
    if(!ready){load_dispatch_fail(env,stage,error);return 0;}
    fprintf(stdout,"JVMTI_HEAP_DISPATCH_PASS real_GetEnv real_tag_capability actual_class_filter actual_jobject_root modern_and_legacy_userdata tag_ptr_and_referrer_writeback unrelated_object_preserved original_tags_restored callback_safe_environment_storage_actual_readback original_storage_restored callbacks_without_JNI_Java_or_locks\n");fflush(stdout);return 1;
}
static void load_dispatch_extension_free(jvmtiEnv *api,jint count,jvmtiExtensionEventInfo *events){
    if(!events)return;
    for(jint i=0;i<count;i++){
        jvmtiExtensionEventInfo *event=&events[i];
        if(event->params){for(jint p=0;p<event->param_count;p++)if(event->params[p].name)(*api)->Deallocate(api,(unsigned char*)event->params[p].name);(*api)->Deallocate(api,(unsigned char*)event->params);}
        if(event->id)(*api)->Deallocate(api,(unsigned char*)event->id);
        if(event->short_description)(*api)->Deallocate(api,(unsigned char*)event->short_description);
    }
    (*api)->Deallocate(api,(unsigned char*)events);
}
static int load_dispatch_unload_name(const char *name){
    return name&&(strcmp(name,load_dispatch_unload_first)==0||strcmp(name,load_dispatch_unload_second)==0||strcmp(name,load_dispatch_unload_cleared)==0);
}
static void load_dispatch_unload_observe(jvmtiEnv *api,JNIEnv *env,const char *name,int replacement){
    DWORD thread=GetCurrentThreadId();
    if(api!=load_dispatch_api||env==NULL||env==load_dispatch_caller_jni||thread==load_dispatch_caller_thread
            ||(load_dispatch_event_jni&&load_dispatch_event_jni!=env)||(load_dispatch_event_thread&&load_dispatch_event_thread!=thread)){
        InterlockedOr(&load_dispatch_event_error,1);return;
    }
    if(name==NULL){InterlockedOr(&load_dispatch_event_error,2);return;}
    if(!load_dispatch_unload_name(name))return;
    if(strcmp(name,replacement?load_dispatch_unload_second:load_dispatch_unload_first)!=0)InterlockedOr(&load_dispatch_event_error,2);
    load_dispatch_event_jni=env;load_dispatch_event_thread=thread;
    InterlockedIncrement(replacement?&load_dispatch_second:&load_dispatch_first);
}
static void JNICALL load_dispatch_unload_callback_first(jvmtiEnv *api,JNIEnv *env,const char *name){load_dispatch_unload_observe(api,env,name,0);}
static void JNICALL load_dispatch_unload_callback_second(jvmtiEnv *api,JNIEnv *env,const char *name){load_dispatch_unload_observe(api,env,name,1);}
JNIEXPORT jint JNICALL Java_dev_ronova_pro_AuditFixCheck_jvmtiDispatch0(JNIEnv *env,jclass check,jclass type,jobject first,jobject second,jobject decoy,jboolean heapOnly){
    (void)check;JavaVM *vm=NULL;jvmtiEnv *api=NULL;jvmtiCapabilities potential={0},requested={0},actual={0};
    if((*env)->GetJavaVM(env,&vm)!=JNI_OK||!vm||(*vm)->GetEnv(vm,(void**)&api,JVMTI_VERSION_1_2)!=JNI_OK||!api){load_dispatch_fail(env,"real_JVMTI_GetEnv",JVMTI_ERROR_INVALID_ENVIRONMENT);return 0;}
    jvmtiError error=(*api)->GetPotentialCapabilities(api,&potential);requested.can_tag_objects=1;
    if(error==JVMTI_ERROR_NONE&&potential.can_tag_objects)error=(*api)->AddCapabilities(api,&requested);
    else if(error==JVMTI_ERROR_NONE)error=JVMTI_ERROR_NOT_AVAILABLE;
    if(error==JVMTI_ERROR_NONE)error=(*api)->GetCapabilities(api,&actual);
    if(error!=JVMTI_ERROR_NONE||!actual.can_tag_objects){load_dispatch_fail(env,"real_tag_capability",error);(*api)->DisposeEnvironment(api);return 0;}
    if(!load_dispatch_heap(env,api,type,first,second,decoy)){(*api)->DisposeEnvironment(api);return 0;}
    if(heapOnly){
        error=(*api)->DisposeEnvironment(api);
        if(error!=JVMTI_ERROR_NONE)load_dispatch_fail(env,"heap_only_actual_DisposeEnvironment",error);
        return 0;
    }
    jint count=0,index=-1;jvmtiExtensionEventInfo *events=NULL;
    error=(*api)->GetExtensionEvents(api,&count,&events);
    if(error!=JVMTI_ERROR_NONE){load_dispatch_fail(env,"real_GetExtensionEvents",error);(*api)->DisposeEnvironment(api);return 0;}
    if(count<0||(count!=0&&events==NULL)){load_dispatch_fail(env,"real_extension_metadata_result",JVMTI_ERROR_INTERNAL);(*api)->DisposeEnvironment(api);return 0;}
    for(jint i=0;i<count;i++){
        jvmtiExtensionEventInfo *event=&events[i];
        if(event->id&&strcmp(event->id,"com.sun.hotspot.events.ClassUnload")==0){
            fprintf(stdout,"JVMTI_CLASS_UNLOAD_METADATA index=%d params=%d\n",event->extension_event_index,event->param_count);
            if(event->param_count==2&&event->params)fprintf(stdout,"JVMTI_CLASS_UNLOAD_PARAMETER_TYPES firstKind=%d firstType=%d secondKind=%d secondType=%d\n",event->params[0].kind,event->params[0].base_type,event->params[1].kind,event->params[1].base_type);
            if(event->param_count==2&&event->params&&event->params[0].base_type==JVMTI_TYPE_JNIENV
                    &&event->params[0].kind==JVMTI_KIND_IN_PTR&&event->params[1].base_type==JVMTI_TYPE_CCHAR
                    &&(event->params[1].kind==JVMTI_KIND_IN_PTR||event->params[1].kind==JVMTI_KIND_IN_BUF))index=event->extension_event_index;
            break;
        }
    }
    load_dispatch_extension_free(api,count,events);
    if(index<0){fprintf(stdout,"JVMTI_CLASS_UNLOAD_NOTRUN actual_extension_signature_unavailable\n");fflush(stdout);(*api)->DisposeEnvironment(api);return 0;}
    load_dispatch_api=api;load_dispatch_extension_index=index;load_dispatch_caller_jni=env;load_dispatch_caller_thread=GetCurrentThreadId();
    load_dispatch_event_jni=NULL;load_dispatch_event_thread=0;InterlockedExchange(&load_dispatch_first,0);InterlockedExchange(&load_dispatch_second,0);InterlockedExchange(&load_dispatch_event_error,0);
    error=(*api)->SetExtensionEventCallback(api,index,(jvmtiExtensionEvent)load_dispatch_unload_callback_first);
    if(error!=JVMTI_ERROR_NONE){load_dispatch_fail(env,"real_ClassUnload_registration",error);load_dispatch_api=NULL;(*api)->DisposeEnvironment(api);return 0;}
    return 1;
}
JNIEXPORT jclass JNICALL Java_dev_ronova_pro_AuditFixCheck_jvmtiDispatchDefine0(JNIEnv *env,jclass check,jobject loader,jchar suffix){
    (void)check;
    if(!load_dispatch_api||!loader||(suffix!='U'&&suffix!='V'&&suffix!='W')){load_dispatch_fail(env,"actual_disposable_loader_request",JVMTI_ERROR_ILLEGAL_ARGUMENT);return NULL;}
    return load_ti_define_class(env,loader,(char)suffix);
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_jvmtiDispatchPhase0(JNIEnv *env,jclass check,jint phase){
    (void)check;
    if(!load_dispatch_api||(phase!=1&&phase!=2)){load_dispatch_fail(env,"actual_ClassUnload_phase",JVMTI_ERROR_ILLEGAL_ARGUMENT);return;}
    jvmtiError error=(*load_dispatch_api)->SetExtensionEventCallback(load_dispatch_api,load_dispatch_extension_index,
            phase==1?(jvmtiExtensionEvent)load_dispatch_unload_callback_second:NULL);
    if(error!=JVMTI_ERROR_NONE)load_dispatch_fail(env,phase==1?"real_ClassUnload_replacement":"real_ClassUnload_clear",error);
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_jvmtiDispatchCollect0(JNIEnv *env,jclass check){
    (void)check;
    if(!load_dispatch_api){load_dispatch_fail(env,"actual_GC_environment",JVMTI_ERROR_INVALID_ENVIRONMENT);return;}
    jvmtiError error=(*load_dispatch_api)->ForceGarbageCollection(load_dispatch_api);
    if(error!=JVMTI_ERROR_NONE)load_dispatch_fail(env,"real_ForceGarbageCollection",error);
}
JNIEXPORT jlongArray JNICALL Java_dev_ronova_pro_AuditFixCheck_jvmtiDispatchState0(JNIEnv *env,jclass check){
    (void)check;jlong values[5]={InterlockedCompareExchange(&load_dispatch_first,0,0),InterlockedCompareExchange(&load_dispatch_second,0,0),
        InterlockedCompareExchange(&load_dispatch_event_error,0,0),(jlong)load_dispatch_event_thread,(jlong)(uintptr_t)load_dispatch_event_jni};
    jlongArray result=(*env)->NewLongArray(env,5);if(result)(*env)->SetLongArrayRegion(env,result,0,5,values);return result;
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_jvmtiDispatchFinish0(JNIEnv *env,jclass check){
    (void)check;if(!load_dispatch_api)return;jvmtiEnv *api=load_dispatch_api;
    jvmtiError cleared=(*api)->SetExtensionEventCallback(api,load_dispatch_extension_index,NULL),disposed=(*api)->DisposeEnvironment(api);
    load_dispatch_api=NULL;
    if(cleared!=JVMTI_ERROR_NONE||disposed!=JVMTI_ERROR_NONE)load_dispatch_fail(env,"actual_ClassUnload_clear_and_DisposeEnvironment",cleared!=JVMTI_ERROR_NONE?cleared:disposed);
}
#endif

#ifdef AUDIT_HOST_THREAD_CREATE_CHECK
typedef HANDLE (WINAPI *LoadThreadCreate)(LPSECURITY_ATTRIBUTES,SIZE_T,LPTHREAD_START_ROUTINE,LPVOID,DWORD,LPDWORD);
typedef HANDLE (WINAPI *LoadRemoteCreate)(HANDLE,LPSECURITY_ATTRIBUTES,SIZE_T,LPTHREAD_START_ROUTINE,LPVOID,DWORD,LPDWORD);
typedef HANDLE (WINAPI *LoadRemoteCreateEx)(HANDLE,LPSECURITY_ATTRIBUTES,SIZE_T,LPTHREAD_START_ROUTINE,LPVOID,DWORD,LPPROC_THREAD_ATTRIBUTE_LIST,LPDWORD);
extern LoadThreadCreate __imp_CreateThread;
extern LoadRemoteCreate __imp_CreateRemoteThread;
extern LoadRemoteCreateEx __imp_CreateRemoteThreadEx;
#define LOAD_THREAD_COOKIE UINT64_C(0x524f4e4f56415454)
#define LOAD_THREAD_EXIT ((DWORD)61)
typedef struct LoadThreadCase {
    uint64_t cookie,observedCookie;
    uintptr_t observedData;
    volatile LONG calls,error;
    HANDLE handle;DWORD id,observedId,createError;
    HANDLE ready,release;DWORD readyError,releaseWait,releaseError;
    int completed,mustComplete;
} LoadThreadCase;
typedef struct LoadThreadHeap {
    LoadThreadCase *thread;
    jlong classTag,objectTag;
    unsigned seen;int error;
} LoadThreadHeap;
static LoadThreadCase *load_thread_active;
static LoadThreadHeap *load_thread_heap_active;
static DWORD WINAPI load_thread_start(LPVOID data){
    LoadThreadCase *state=load_thread_active;
    if(!state)return LOAD_THREAD_EXIT;
    if(data!=state){InterlockedOr(&state->error,1);return LOAD_THREAD_EXIT;}
    state->observedData=(uintptr_t)data;state->observedCookie=state->cookie;state->observedId=GetCurrentThreadId();
    if(state->observedCookie!=LOAD_THREAD_COOKIE)InterlockedOr(&state->error,2);
    InterlockedIncrement(&state->calls);return LOAD_THREAD_EXIT;
}
static LoadThreadCase *load_thread_case_new(void){
    LoadThreadCase *state=(LoadThreadCase*)HeapAlloc(GetProcessHeap(),HEAP_ZERO_MEMORY,sizeof(*state));
    if(state){state->cookie=LOAD_THREAD_COOKIE;load_thread_active=state;}return state;
}
static void load_thread_case_free(LoadThreadCase *state){
    if(!state)return;
    /* A failed wait does not prove the native start routine stopped reading
     * its userdata. Keep that allocation alive for the failing process. */
    if((!state->handle&&!state->mustComplete)||state->completed){load_thread_active=NULL;HeapFree(GetProcessHeap(),0,state);}
}
static int load_thread_fail(JNIEnv *env,const char *stage){
    fprintf(stderr,"HOST_THREAD_CREATE_FAILED stage=%s\n",stage);fflush(stderr);
    return load_os_fail(env,stage);
}
static int load_thread_finish(LoadThreadCase *state,const char *name){
    if(!state->handle){
        fprintf(stderr,"HOST_THREAD_CREATE_RESULT api=%s created=0 lastError=%lu threadId=%lu\n",name,(unsigned long)state->createError,(unsigned long)state->id);
        return 0;
    }
    DWORD actualId=GetThreadId(state->handle),idError=GetLastError(),beforeCode=0;
    BOOL beforeQueried=GetExitCodeThread(state->handle,&beforeCode);DWORD beforeError=GetLastError();
    DWORD beforeWait=WaitForSingleObject(state->handle,0),beforeWaitError=GetLastError();
    LONG beforeCalls=InterlockedCompareExchange(&state->calls,0,0);
    DWORD resumed=ResumeThread(state->handle),resumeError=GetLastError();
    DWORD waited=WaitForSingleObject(state->handle,5000),waitError=GetLastError(),exitCode=0;
    BOOL queried=GetExitCodeThread(state->handle,&exitCode);DWORD exitError=GetLastError();
    state->completed=waited==WAIT_OBJECT_0;
    int observed=state->completed&&InterlockedCompareExchange(&state->calls,0,0)==1
            &&!InterlockedCompareExchange(&state->error,0,0)&&state->observedData==(uintptr_t)state
            &&state->observedCookie==LOAD_THREAD_COOKIE&&state->observedId==state->id;
    BOOL closed=CloseHandle(state->handle);DWORD closeError=GetLastError();
    int ok=state->id!=0&&actualId==state->id&&state->id!=GetCurrentThreadId()
            &&beforeQueried&&beforeCode==STILL_ACTIVE&&beforeWait==WAIT_TIMEOUT&&beforeCalls==0
            &&resumed==1&&state->completed&&queried&&exitCode==LOAD_THREAD_EXIT&&observed&&closed;
    /* Successful creation does not define GetLastError. Record the actual
     * immediate result instead of assuming Windows leaves our sentinel intact. */
    fprintf(ok?stdout:stderr,"HOST_THREAD_CREATE_RESULT api=%s created=1 lastError=%lu threadId=%lu actualThreadId=%lu idError=%lu beforeExit=%lu beforeError=%lu beforeWait=%lu beforeWaitError=%lu beforeCalls=%ld resumeCount=%lu resumeError=%lu wait=%lu waitError=%lu exit=%lu exitError=%lu userdataCookie=%d closed=%d closeError=%lu\n",
            name,(unsigned long)state->createError,(unsigned long)state->id,(unsigned long)actualId,(unsigned long)idError,
            (unsigned long)beforeCode,(unsigned long)beforeError,(unsigned long)beforeWait,(unsigned long)beforeWaitError,(long)beforeCalls,
            (unsigned long)resumed,(unsigned long)resumeError,(unsigned long)waited,(unsigned long)waitError,
            (unsigned long)exitCode,(unsigned long)exitError,observed,(int)closed,(unsigned long)closeError);
    fflush(ok?stdout:stderr);return ok;
}
typedef void (__cdecl *LoadCrtStart)(void*);
typedef unsigned (__stdcall *LoadCrtStartEx)(void*);
typedef uintptr_t (__cdecl *LoadCrtBegin)(LoadCrtStart,unsigned,void*);
typedef uintptr_t (__cdecl *LoadCrtBeginEx)(void*,unsigned,LoadCrtStartEx,void*,unsigned,unsigned*);
typedef int *(__cdecl *LoadCrtErrno)(void);
typedef unsigned long *(__cdecl *LoadCrtDosErrno)(void);
static void __cdecl load_crt_start(void *data){
    LoadThreadCase *state=load_thread_active;if(!state)return;
    load_thread_start(data);state->observedId=GetCurrentThreadId();
    if(!SetEvent(state->ready))InterlockedOr(&state->error,4);state->readyError=GetLastError();
    state->releaseWait=WaitForSingleObject(state->release,5000);state->releaseError=GetLastError();
    if(state->releaseWait!=WAIT_OBJECT_0)InterlockedOr(&state->error,8);
    /* Returning lets this exact CRT run _endthread and close its own handle. */
}
static unsigned __stdcall load_crt_start_ex(void *data){return (unsigned)load_thread_start(data);}
static int load_crt_begin_finish(LoadThreadCase *state,uintptr_t returned){
    /* Never synchronize on, inspect, or close returned: _beginthread owns
     * that handle and can close it as soon as its start routine returns. */
    DWORD readyWait=WaitForSingleObject(state->ready,5000),readyWaitError=GetLastError();
    state->id=readyWait==WAIT_OBJECT_0?state->observedId:0;
    if(state->id)state->handle=OpenThread(SYNCHRONIZE|THREAD_QUERY_LIMITED_INFORMATION,FALSE,state->id);
    DWORD openError=GetLastError(),actualId=state->handle?GetThreadId(state->handle):0;
    BOOL released=SetEvent(state->release);DWORD releaseError=GetLastError();
    DWORD waited=state->handle?WaitForSingleObject(state->handle,5000):WAIT_FAILED,waitError=GetLastError(),exitCode=0;
    BOOL queried=state->handle?GetExitCodeThread(state->handle,&exitCode):FALSE;DWORD exitError=GetLastError();
    state->completed=state->handle&&waited==WAIT_OBJECT_0;
    int observed=state->completed&&InterlockedCompareExchange(&state->calls,0,0)==1
            &&!InterlockedCompareExchange(&state->error,0,0)&&state->observedData==(uintptr_t)state
            &&state->observedCookie==LOAD_THREAD_COOKIE&&state->releaseWait==WAIT_OBJECT_0;
    BOOL closed=state->handle?CloseHandle(state->handle):FALSE;DWORD closeError=GetLastError();
    DWORD callbackReleaseWait=state->completed?state->releaseWait:0;
    int ok=readyWait==WAIT_OBJECT_0&&state->id&&state->id!=GetCurrentThreadId()&&actualId==state->id
            &&released&&state->completed&&queried&&observed&&closed;
    fprintf(ok?stdout:stderr,"HOST_CRT_BEGIN_RESULT returned=%llx original_handle_owned_by_CRT readyWait=%lu readyWaitError=%lu threadId=%lu actualThreadId=%lu openError=%lu released=%d releaseError=%lu callbackReleaseObserved=%d callbackReleaseWait=%lu wait=%lu waitError=%lu exit=%lu exitError=%lu userdataCookie=%d ownedHandleClosed=%d closeError=%lu\n",
            (unsigned long long)returned,(unsigned long)readyWait,(unsigned long)readyWaitError,(unsigned long)state->id,(unsigned long)actualId,
            (unsigned long)openError,(int)released,(unsigned long)releaseError,state->completed,(unsigned long)callbackReleaseWait,
            (unsigned long)waited,(unsigned long)waitError,(unsigned long)exitCode,(unsigned long)exitError,observed,(int)closed,(unsigned long)closeError);
    fflush(ok?stdout:stderr);return ok;
}
static int load_crt_thread_check(JNIEnv *env){
    const wchar_t *modules[]={L"ucrtbase.dll",L"msvcrt.dll"};const char *names[]={"ucrtbase.dll","msvcrt.dll"};
    HMODULE image=NULL;unsigned selected=0;
    for(;selected<sizeof(modules)/sizeof(modules[0]);selected++)if((image=GetModuleHandleW(modules[selected]))!=NULL)break;
    if(!image)return load_thread_fail(env,"HOST_CRT_ACTUAL_LOADED_RUNTIME_ABSENT");
    LoadCrtBegin begin=(LoadCrtBegin)GetProcAddress(image,"_beginthread");
    LoadCrtBeginEx beginEx=(LoadCrtBeginEx)GetProcAddress(image,"_beginthreadex");
    LoadCrtErrno error=(LoadCrtErrno)GetProcAddress(image,"_errno");
    LoadCrtDosErrno dosError=(LoadCrtDosErrno)GetProcAddress(image,"__doserrno");
    HMODULE errorImage=NULL,dosImage=NULL;
    if(!begin||!beginEx||!load_private_entry((void*)begin)||!load_private_entry((void*)beginEx))
        return load_thread_fail(env,"HOST_CRT_ACTUAL_DYNAMIC_ENTRIES_NOT_CONTROLLED");
    if(!error||!dosError
            ||!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,(LPCSTR)error,&errorImage)
            ||!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,(LPCSTR)dosError,&dosImage)
            ||errorImage!=image||dosImage!=image)return load_thread_fail(env,"HOST_CRT_ACTUAL_RUNTIME_ERROR_ACCESSORS");
    int *callerError=error();unsigned long *callerDosError=dosError();
    if(!callerError||!callerDosError)return load_thread_fail(env,"HOST_CRT_ACTUAL_CALLER_ERROR_STORAGE");
    int savedError=*callerError;unsigned long savedDosError=*callerDosError;DWORD savedWindowsError=GetLastError();int ready=1;
    for(unsigned kind=0;kind<2;kind++){
        LoadThreadCase *state=load_thread_case_new();
        if(!state){load_thread_fail(env,"HOST_CRT_NATIVE_USERDATA_ALLOCATION");ready=0;break;}
        if(kind==0){
            state->ready=CreateEventW(NULL,TRUE,FALSE,NULL);state->release=CreateEventW(NULL,TRUE,FALSE,NULL);
            if(!state->ready||!state->release){
                if(state->ready)CloseHandle(state->ready);if(state->release)CloseHandle(state->release);load_thread_case_free(state);
                load_thread_fail(env,"HOST_CRT_NATIVE_START_EVENTS");ready=0;break;
            }
        }
        *callerError=0x5454;*callerDosError=0x524e0055;SetLastError(0x524e0054);unsigned id=0;
        uintptr_t returned=kind?beginEx(NULL,0,load_crt_start_ex,state,CREATE_SUSPENDED,&id):begin(load_crt_start,0,state);
        state->createError=GetLastError();int *actualError=error();unsigned long *actualDosError=dosError();
        int observedError=actualError?*actualError:0;unsigned long observedDosError=actualDosError?*actualDosError:0;
        int sameStorage=actualError==callerError&&actualDosError==callerDosError;
        int created=kind?returned!=0:returned!=(uintptr_t)-1;
        fprintf(created?stdout:stderr,"HOST_CRT_THREAD_RESULT runtime=%s api=%s returned=%llx lastError=%lu errno=%d doserrno=%lu callerErrorStorage=%d\n",
                names[selected],kind?"_beginthreadex":"_beginthread",(unsigned long long)returned,(unsigned long)state->createError,observedError,observedDosError,sameStorage);
        int settled=0;
        if(created){
            if(kind){state->handle=(HANDLE)returned;state->id=(DWORD)id;settled=load_thread_finish(state,"actual_CRT_beginthreadex");}
            else {state->mustComplete=1;settled=load_crt_begin_finish(state,returned);}
        }
        if(kind==0&&(!created||state->completed)){
            BOOL closedReady=CloseHandle(state->ready),closedRelease=CloseHandle(state->release);
            if(!closedReady||!closedRelease)settled=0;
        }
        load_thread_case_free(state);
        if(!created||!sameStorage||!settled){load_thread_fail(env,kind?"HOST_CRT_BEGINTHREADEX_ACTUAL_RESULT":"HOST_CRT_BEGINTHREAD_ACTUAL_RESULT");ready=0;break;}
    }
    *callerError=savedError;*callerDosError=savedDosError;SetLastError(savedWindowsError);return ready;
}
/* The allocation phase reuses this independently loaded CRT fixture. Each
 * selected runtime owns the actual function and TLS error storage it returns. */
#include <errno.h>
typedef void *(__cdecl *LoadCrtMalloc)(size_t);
typedef void *(__cdecl *LoadCrtCalloc)(size_t,size_t);
typedef void *(__cdecl *LoadCrtRealloc)(void*,size_t);
typedef void (__cdecl *LoadCrtFree)(void*);
extern LoadCrtMalloc __imp_malloc;
extern LoadCrtCalloc __imp_calloc;
extern LoadCrtRealloc __imp_realloc;
extern LoadCrtFree __imp_free;
static int load_crt_allocation_check(JNIEnv *env){
    const wchar_t *modules[]={L"ucrtbase.dll",L"msvcrt.dll"};const char *names[]={"ucrtbase.dll","msvcrt.dll"};
    int selected=0;
    for(unsigned at=0;at<sizeof(modules)/sizeof(modules[0]);at++){
        HMODULE image=GetModuleHandleW(modules[at]);if(!image)continue;selected++;
        LoadCrtMalloc allocate=(LoadCrtMalloc)GetProcAddress(image,"malloc");
        LoadCrtCalloc zero=(LoadCrtCalloc)GetProcAddress(image,"calloc");
        LoadCrtRealloc resize=(LoadCrtRealloc)GetProcAddress(image,"realloc");
        LoadCrtFree release=(LoadCrtFree)GetProcAddress(image,"free");
        LoadCrtErrno error=(LoadCrtErrno)GetProcAddress(image,"_errno");LoadCrtDosErrno dos=(LoadCrtDosErrno)GetProcAddress(image,"__doserrno");
        HMODULE errorImage=NULL,dosImage=NULL;
        if(!allocate||!zero||!resize||!release||!load_private_entry((void*)allocate)||!load_private_entry((void*)zero)
                ||!load_private_entry((void*)resize)||!load_private_entry((void*)release))return load_thread_fail(env,"HOST_CRT_ALLOCATION_ACTUAL_GPA_ENTRIES");
        if(!error||!dos||!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,(LPCSTR)error,&errorImage)
                ||!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,(LPCSTR)dos,&dosImage)
                ||errorImage!=image||dosImage!=image)return load_thread_fail(env,"HOST_CRT_ALLOCATION_PROVIDER_ERROR_STORAGE");
        int *actualError=error();unsigned long *actualDos=dos();if(!actualError||!actualDos)return load_thread_fail(env,"HOST_CRT_ALLOCATION_CALLER_TLS");
        int savedError=*actualError;unsigned long savedDos=*actualDos;DWORD savedWindows=GetLastError();
        unsigned char *block=NULL,*cleared=NULL;void *empty=NULL;const char *failure=NULL;DWORD failureWindows=0;int failureError=0;
        *actualError=EDOM;*actualDos=0x524e0088;SetLastError(0x524e0089);release(NULL);
        if(*actualError!=EDOM||*actualDos!=0x524e0088||GetLastError()!=0x524e0089){failure="HOST_CRT_FREE_NULL_ERRORS_CHANGED";goto allocated_done;}
        block=(unsigned char*)allocate(32);cleared=(unsigned char*)zero(8,4);
        if(!block||!cleared){failure="HOST_CRT_ALLOCATION_ACTUAL_SMALL_RESULTS";goto allocated_done;}
        for(unsigned i=0;i<32;i++){if(cleared[i]){failure="HOST_CRT_CALLOC_ACTUAL_ZEROED_BYTES";goto allocated_done;}block[i]=(unsigned char)(i^0xa7);}
        {
            /* This request exceeds the selected Microsoft CRT's maximum. A
             * NULL failure must keep the exact old caller allocation usable. */
            *actualError=EDOM;SetLastError(0x524e0089);void *failed=resize(block,SIZE_MAX);
            failureWindows=GetLastError();failureError=*actualError;
            if(failed){block=(unsigned char*)failed;failure="HOST_CRT_REALLOC_MAXIMUM_WAS_NOT_REFUSED";goto allocated_done;}
            if(failureError!=ENOMEM){failure="HOST_CRT_REALLOC_ORIGINAL_ERRNO";goto allocated_done;}
            for(unsigned i=0;i<32;i++)if(block[i]!=(unsigned char)(i^0xa7)){failure="HOST_CRT_REALLOC_FAILURE_LOST_OLD_BYTES";goto allocated_done;}
            unsigned char *grown=(unsigned char*)resize(block,64);if(!grown){failure="HOST_CRT_REALLOC_ACTUAL_GROW";goto allocated_done;}block=grown;
            for(unsigned i=0;i<32;i++)if(block[i]!=(unsigned char)(i^0xa7)){failure="HOST_CRT_REALLOC_GROW_LOST_ORIGINAL_BYTES";goto allocated_done;}
            void *released=resize(block,0);block=(unsigned char*)released;
            if(released){failure="HOST_CRT_REALLOC_ZERO_ORIGINAL_RELEASE_RESULT";goto allocated_done;}
        }
        empty=allocate(0);if(!empty){failure="HOST_CRT_MALLOC_ZERO_ORIGINAL_RESULT";goto allocated_done;}release(empty);empty=NULL;
        empty=zero(0,17);if(!empty){failure="HOST_CRT_CALLOC_ZERO_ORIGINAL_RESULT";goto allocated_done;}release(empty);empty=NULL;
        empty=resize(NULL,0);if(!empty){failure="HOST_CRT_REALLOC_NULL_ZERO_ORIGINAL_RESULT";goto allocated_done;}release(empty);empty=NULL;
        {
            *actualError=EDOM;size_t count=SIZE_MAX/2+1;empty=zero(count,2);
            if(empty||*actualError!=ENOMEM){failure="HOST_CRT_CALLOC_OVERFLOW_ORIGINAL_RESULT_ERRNO";goto allocated_done;}
        }
        if(error()!=actualError||dos()!=actualDos){failure="HOST_CRT_ALLOCATION_ERROR_STORAGE_CHANGED";goto allocated_done;}
allocated_done:
        if(empty)release(empty);if(cleared)release(cleared);if(block)release(block);
        *actualError=savedError;*actualDos=savedDos;SetLastError(savedWindows);
        if(failure){fprintf(stderr,"HOST_CRT_ALLOCATION_FAILED runtime=%s stage=%s reallocFailureErrno=%d reallocFailureWin32=%lu\n",names[at],failure,failureError,(unsigned long)failureWindows);return load_thread_fail(env,failure);}
        fprintf(stdout,"HOST_CRT_ALLOCATION_RESULT runtime=%s actual_GPA_original_small_zero_overflow_and_failed_realloc_old_bytes retained_TLS reallocFailureErrno=%d reallocFailureWin32=%lu\n",names[at],failureError,(unsigned long)failureWindows);
    }
    if(!selected)return load_thread_fail(env,"HOST_CRT_ALLOCATION_LOADED_PROVIDER_ABSENT");
    if(!load_private_entry((void*)__imp_malloc)||!load_private_entry((void*)__imp_calloc)
            ||!load_private_entry((void*)__imp_realloc)||!load_private_entry((void*)__imp_free))return load_thread_fail(env,"HOST_CRT_ALLOCATION_ACTUAL_IAT_ENTRIES");
    unsigned char *direct=(unsigned char*)__imp_malloc(16),*cleared=(unsigned char*)__imp_calloc(4,4);
    if(!direct||!cleared){if(direct)__imp_free(direct);if(cleared)__imp_free(cleared);return load_thread_fail(env,"HOST_CRT_ALLOCATION_IAT_RESULTS");}
    int ready=1;for(unsigned i=0;i<16;i++){if(cleared[i])ready=0;direct[i]=(unsigned char)(i+1);}
    unsigned char *grown=(unsigned char*)__imp_realloc(direct,32);
    if(grown){direct=grown;for(unsigned i=0;i<16;i++)if(direct[i]!=(unsigned char)(i+1))ready=0;}else ready=0;
    __imp_free(direct);__imp_free(cleared);if(!ready)return load_thread_fail(env,"HOST_CRT_ALLOCATION_IAT_DATA_RETAINED");
    fprintf(stdout,"HOST_CRT_ALLOCATION_PASS actual_loaded_providers=%d actual_IAT_and_GPA returned_blocks zero_request_lifetimes overflow_original_failure realloc_failure_old_bytes realloc_zero_release provider_errno_doserrno_free_null_LastError no_Cplusplus_object_disposal_claim\n",selected);
    return 1;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_AuditFixCheck_hostCrtAllocations0(JNIEnv *env,jclass check){
    (void)check;return load_crt_allocation_check(env)?JNI_TRUE:JNI_FALSE;
}
typedef void *(__cdecl *LoadCppNew)(size_t);
typedef void (__cdecl *LoadCppDelete)(void*);
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_AuditFixCheck_hostCppPrivate0(JNIEnv *env,jclass check){
    (void)check;HMODULE image=GetModuleHandleW(L"msvcrt.dll");
    if(!image)return load_thread_fail(env,"HOST_CPP_LOADED_MSVCRT_ABSENT")?JNI_TRUE:JNI_FALSE;
    const char *names[2][2]={{"??2@YAPEAX_K@Z","??3@YAXPEAX@Z"},{"??_U@YAPEAX_K@Z","??_V@YAXPEAX@Z"}};
    LoadCrtErrno error=(LoadCrtErrno)GetProcAddress(image,"_errno");LoadCrtDosErrno dos=(LoadCrtDosErrno)GetProcAddress(image,"__doserrno");
    HMODULE errorImage=NULL,dosImage=NULL;
    if(!error||!dos||!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,(LPCSTR)error,&errorImage)
            ||!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,(LPCSTR)dos,&dosImage)
            ||errorImage!=image||dosImage!=image)return load_thread_fail(env,"HOST_CPP_PROVIDER_ERROR_ACCESSORS")?JNI_TRUE:JNI_FALSE;
    int *actualError=error();unsigned long *actualDos=dos();
    if(!actualError||!actualDos)return load_thread_fail(env,"HOST_CPP_PROVIDER_ERROR_STORAGE")?JNI_TRUE:JNI_FALSE;
    int savedError=*actualError;unsigned long savedDos=*actualDos;DWORD savedWindows=GetLastError();
    unsigned completed=0,skipped=0,covered=0;const char *failure=NULL;DWORD exception=0;
    for(unsigned kind=0;kind<2&&!failure;kind++){
        LoadCppNew allocate=(LoadCppNew)GetProcAddress(image,names[kind][0]);
        LoadCppDelete release=(LoadCppDelete)GetProcAddress(image,names[kind][1]);
        if(!allocate||!release){
            fprintf(stdout,"HOST_CPP_PRIVATE_SKIP provider=msvcrt.dll kind=%s missing_new=%d missing_delete=%d\n",kind?"array":"scalar",!allocate,!release);skipped++;continue;
        }
        if(!load_private_entry((void*)allocate)||!load_private_entry((void*)release)){failure="HOST_CPP_ACTUAL_NAMED_GPA_NOT_CONTROLLED";break;}
        __try {
            *actualError=EDOM;*actualDos=0x524e0090;SetLastError(0x524e0091);release(NULL);
            DWORD nullWindows=GetLastError();
            if(*actualError!=EDOM||*actualDos!=0x524e0090||nullWindows!=0x524e0091)failure="HOST_CPP_DELETE_NULL_PROVIDER_ERRORS_CHANGED";
        } __except(EXCEPTION_EXECUTE_HANDLER){exception=GetExceptionCode();failure="HOST_CPP_DELETE_NULL_NATIVE_EXCEPTION";}
        for(unsigned zero=0;zero<2&&!failure;zero++){
            void *block=NULL;uintptr_t returned=0;int newError=0,deleteError=0;unsigned long newDos=0,deleteDos=0;DWORD newWindows=0,deleteWindows=0;
            __try {
                *actualError=EDOM;*actualDos=0x524e0090;SetLastError(0x524e0091);
                block=allocate(zero?0:32);newWindows=GetLastError();newError=*actualError;newDos=*actualDos;returned=(uintptr_t)block;
                if(!block)failure="HOST_CPP_ORIGINAL_SMALL_OR_ZERO_RETURNED_NULL";
                else if(!zero){
                    volatile unsigned char *bytes=(volatile unsigned char*)block;
                    for(unsigned i=0;i<32;i++)bytes[i]=(unsigned char)(i^0xa6);
                    for(unsigned i=0;i<32;i++)if(bytes[i]!=(unsigned char)(i^0xa6)){failure="HOST_CPP_RETURNED_SMALL_BYTES_CHANGED";break;}
                }
                /* A zero request has no promised writable byte. Only its real
                 * nonnull storage result and corresponding delete are used. */
                if(block){void *retiring=block;block=NULL;
                    *actualError=EDOM;*actualDos=0x524e0090;SetLastError(0x524e0091);
                    release(retiring);deleteWindows=GetLastError();deleteError=*actualError;deleteDos=*actualDos;
                    if(deleteWindows&&deleteWindows==deleteDos&&deleteError!=EDOM)failure="HOST_CPP_PAIRED_DELETE_REPORTED_FAILURE";
                }
            } __except(EXCEPTION_EXECUTE_HANDLER){exception=GetExceptionCode();failure="HOST_CPP_ALLOCATION_OR_DELETE_NATIVE_EXCEPTION";}
            if(block){void *retiring=block;block=NULL;
                __try {release(retiring);} __except(EXCEPTION_EXECUTE_HANDLER){exception=GetExceptionCode();failure="HOST_CPP_PAIRED_DELETE_CLEANUP_NATIVE_EXCEPTION";}
            }
            /* Successful new/delete need not define LastError. Report their
             * actual immediate states; only the no-op delete(NULL) is checked
             * against fixed sentinels. No impossible allocation is requested. */
            fprintf(failure?stderr:stdout,"HOST_CPP_PRIVATE_RESULT provider=msvcrt.dll kind=%s bytes=%u returned=%llx new_errno=%d new_doserrno=%lu new_LastError=%lu delete_errno=%d delete_doserrno=%lu delete_LastError=%lu storage_only=1\n",
                    kind?"array":"scalar",zero?0:32,(unsigned long long)returned,newError,newDos,(unsigned long)newWindows,deleteError,deleteDos,(unsigned long)deleteWindows);
        }
        if(!failure){completed++;covered|=1u<<kind;}
    }
    if(!failure&&!completed)failure="HOST_CPP_NO_REAL_NEW_DELETE_PAIR_EXECUTED";
    if(!failure&&(error()!=actualError||dos()!=actualDos))failure="HOST_CPP_PROVIDER_ERROR_STORAGE_CHANGED";
    if(failure)fprintf(stderr,"HOST_CPP_PRIVATE_FAILED stage=%s nativeException=%08lx completed_pairs=%u skipped_pairs=%u\n",failure,(unsigned long)exception,completed,skipped);
    else fprintf(stdout,"HOST_CPP_PRIVATE_PASS provider=msvcrt.dll actual_named_GPA completed_pairs=%u covered_kind_mask=%u skipped_pairs=%u small_storage_write_read zero_request_paired_delete provider_errno_doserrno_delete_null_LastError no_object_destruction_claim\n",completed,covered,skipped);
    fflush(failure?stderr:stdout);*actualError=savedError;*actualDos=savedDos;SetLastError(savedWindows);
    if(failure){load_thread_fail(env,failure);return JNI_FALSE;}return JNI_TRUE;
}
/* Native55 selects only this phase. No producer registration is manufactured:
 * an ordinary observed profiler tests ThreadEnd dispatch and Host sources, not
 * the producer-only start wrapper, source lease or image retirement. */
typedef struct LoadThreadEndCase {
    LoadThreadCase parent,during,after;
    JavaVM *vm;jvmtiEnv *api;JNIEnv *attached,*afterEnv,*eventEnv;
    uintptr_t continuedData;uint64_t continuedCookie;
    DWORD continuedId,eventId;
    jint attachResult,detachResult,afterEnvResult;
    volatile LONG callbacks,callbackError,continued;
    int tlsSet,tlsCleared,eventThreadPresent;
} LoadThreadEndCase;
static volatile LONG load_thread_end_tls=-1;
static LoadThreadEndCase *volatile load_thread_end_active;
static void JNICALL load_thread_end_callback(jvmtiEnv *api,JNIEnv *env,jthread thread){
    DWORD index=(DWORD)InterlockedCompareExchange(&load_thread_end_tls,0,0);
    if(index==TLS_OUT_OF_INDEXES)return;
    LoadThreadEndCase *state=(LoadThreadEndCase*)TlsGetValue(index);
    if(!state||state!=InterlockedCompareExchangePointer((void *volatile*)&load_thread_end_active,NULL,NULL))return;
    LONG count=InterlockedIncrement(&state->callbacks);
    state->eventEnv=env;state->eventId=GetCurrentThreadId();state->eventThreadPresent=thread!=NULL;
    if(count!=1||api!=state->api||env!=state->attached||!thread||state->eventId!=state->parent.id){
        InterlockedOr(&state->callbackError,1);return;
    }
    /* Correlation uses this actual native thread's fixture TLS, not a Mod or
     * producer identity. No JNI/GetEnv/JVMTI call, output, wait or lock here. */
    SetLastError(0x524e0055);
    state->during.handle=CreateThread(NULL,0,load_thread_start,&state->during,CREATE_SUSPENDED,&state->during.id);
    state->during.createError=GetLastError();
}
static unsigned __stdcall load_thread_end_start(void *data){
    LoadThreadEndCase *state=(LoadThreadEndCase*)InterlockedCompareExchangePointer((void *volatile*)&load_thread_end_active,NULL,NULL);
    if(!state||data!=&state->parent)return (unsigned)LOAD_THREAD_EXIT;
    load_thread_start(data);
    DWORD index=(DWORD)InterlockedCompareExchange(&load_thread_end_tls,0,0);
    state->tlsSet=index!=TLS_OUT_OF_INDEXES&&TlsSetValue(index,state);
    if(!state->tlsSet){InterlockedOr(&state->parent.error,4);return (unsigned)LOAD_THREAD_EXIT;}
    JavaVMAttachArgs arguments={JNI_VERSION_1_8,"RonovaAuditNativeDetach",NULL};
    state->attachResult=(*state->vm)->AttachCurrentThread(state->vm,(void**)&state->attached,&arguments);
    if(state->attachResult==JNI_OK&&state->attached){
        state->detachResult=(*state->vm)->DetachCurrentThread(state->vm);
        state->afterEnvResult=(*state->vm)->GetEnv(state->vm,(void**)&state->afterEnv,JNI_VERSION_1_8);
        /* This is the same OS start routine after its Java ThreadEnd. It uses
         * only its original C userdata; the detached JNIEnv is never called. */
        state->continuedData=(uintptr_t)data;state->continuedCookie=state->parent.cookie;state->continuedId=GetCurrentThreadId();
        InterlockedExchange(&state->continued,1);SetLastError(0x524e0055);
        state->after.handle=CreateThread(NULL,0,load_thread_start,&state->after,CREATE_SUSPENDED,&state->after.id);
        state->after.createError=GetLastError();
    }
    state->tlsCleared=TlsSetValue(index,NULL);return (unsigned)LOAD_THREAD_EXIT;
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_hostThreadEnd0(JNIEnv *env,jclass check){
    (void)check;JavaVM *vm=NULL;jvmtiEnv *api=NULL;jvmtiError error=JVMTI_ERROR_NONE;
    const char *stage="HOST_THREAD_END_REAL_VM";int ready=0,registered=0;
    LoadThreadEndCase *state=NULL;DWORD index=TLS_OUT_OF_INDEXES;
    LoadCrtErrno crtError=NULL;LoadCrtDosErrno crtDosError=NULL;int *callerError=NULL;unsigned long *callerDosError=NULL;
    int savedError=0;unsigned long savedDosError=0;DWORD savedWindowsError=0;int saved=0;
    if((*env)->GetJavaVM(env,&vm)!=JNI_OK||!vm){load_thread_fail(env,stage);return;}
    stage="HOST_THREAD_END_REAL_JVMTI_GETENV";
    jint status=(*vm)->GetEnv(vm,(void**)&api,JVMTI_VERSION_1_2);
    if(status!=JNI_OK||!api){fprintf(stderr,"HOST_THREAD_END_GETENV_RESULT jni=%d actualEnv=%p\n",status,(void*)api);load_thread_fail(env,stage);return;}
    stage="HOST_THREAD_END_ACTUAL_CONTROLLED_ENTRIES";
    if(!load_private_entry((void*)__imp_CreateThread))goto done;
    const wchar_t *modules[]={L"ucrtbase.dll",L"msvcrt.dll"};const char *names[]={"ucrtbase.dll","msvcrt.dll"};
    HMODULE image=NULL;unsigned selected=0;
    for(;selected<sizeof(modules)/sizeof(modules[0]);selected++)if((image=GetModuleHandleW(modules[selected]))!=NULL)break;
    if(!image)goto done;
    LoadCrtBeginEx beginEx=(LoadCrtBeginEx)GetProcAddress(image,"_beginthreadex");
    crtError=(LoadCrtErrno)GetProcAddress(image,"_errno");crtDosError=(LoadCrtDosErrno)GetProcAddress(image,"__doserrno");
    HMODULE errorImage=NULL,dosImage=NULL;
    if(!beginEx||!load_private_entry((void*)beginEx)||!crtError||!crtDosError
            ||!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,(LPCSTR)crtError,&errorImage)
            ||!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,(LPCSTR)crtDosError,&dosImage)
            ||errorImage!=image||dosImage!=image)goto done;
    callerError=crtError();callerDosError=crtDosError();if(!callerError||!callerDosError)goto done;
    savedError=*callerError;savedDosError=*callerDosError;savedWindowsError=GetLastError();saved=1;
    stage="HOST_THREAD_END_NATIVE_USERDATA";
    state=(LoadThreadEndCase*)HeapAlloc(GetProcessHeap(),HEAP_ZERO_MEMORY,sizeof(*state));if(!state)goto done;
    state->vm=vm;state->api=api;state->parent.cookie=state->during.cookie=state->after.cookie=LOAD_THREAD_COOKIE;
    state->attachResult=state->detachResult=state->afterEnvResult=JNI_ERR;
    index=TlsAlloc();if(index==TLS_OUT_OF_INDEXES)goto done;
    InterlockedExchange(&load_thread_end_tls,(LONG)index);
    InterlockedExchangePointer((void *volatile*)&load_thread_end_active,state);load_thread_active=&state->parent;
    jvmtiEventCallbacks callbacks={0};callbacks.ThreadEnd=load_thread_end_callback;
    stage="HOST_THREAD_END_REAL_EVENT_CALLBACK";
    error=(*api)->SetEventCallbacks(api,&callbacks,(jint)sizeof(callbacks));if(error!=JVMTI_ERROR_NONE)goto done;registered=1;
    error=(*api)->SetEventNotificationMode(api,JVMTI_ENABLE,JVMTI_EVENT_THREAD_END,NULL);if(error!=JVMTI_ERROR_NONE)goto done;
    stage="HOST_THREAD_END_ACTUAL_CRT_START";unsigned id=0;
    *callerError=0x5555;*callerDosError=0x524e0056;SetLastError(0x524e0055);
    uintptr_t returned=beginEx(NULL,0,load_thread_end_start,&state->parent,CREATE_SUSPENDED,&id);
    state->parent.createError=GetLastError();state->parent.handle=(HANDLE)returned;state->parent.id=(DWORD)id;
    int *actualError=crtError();unsigned long *actualDosError=crtDosError();
    int sameStorage=actualError==callerError&&actualDosError==callerDosError;
    fprintf(returned?stdout:stderr,"HOST_THREAD_END_CRT_RESULT runtime=%s returned=%llx lastError=%lu errno=%d doserrno=%lu callerErrorStorage=%d\n",
            names[selected],(unsigned long long)returned,(unsigned long)state->parent.createError,actualError?*actualError:0,actualDosError?*actualDosError:0,sameStorage);
    stage="HOST_THREAD_END_ATTACH_DETACH_NATIVE_CONTINUATION";
    int parentReady=load_thread_finish(&state->parent,"CRT_attach_detach_original_start");
    if(!state->parent.completed)goto done;
    fprintf(parentReady?stdout:stderr,"HOST_THREAD_END_RESULT attach=%d attachedEnv=%p detach=%d afterGetEnv=%d afterEnv=%p callbacks=%ld callbackError=%ld eventEnv=%p eventThreadId=%lu eventThreadPresent=%d nativeContinued=%ld continuedThreadId=%lu continuedUserdata=%d tlsSet=%d tlsCleared=%d\n",
            state->attachResult,(void*)state->attached,state->detachResult,state->afterEnvResult,(void*)state->afterEnv,
            (long)InterlockedCompareExchange(&state->callbacks,0,0),(long)InterlockedCompareExchange(&state->callbackError,0,0),
            (void*)state->eventEnv,(unsigned long)state->eventId,state->eventThreadPresent,(long)InterlockedCompareExchange(&state->continued,0,0),
            (unsigned long)state->continuedId,state->continuedData==(uintptr_t)&state->parent&&state->continuedCookie==LOAD_THREAD_COOKIE,state->tlsSet,state->tlsCleared);
    /* Both children were suspended throughout the actual callback/detach.
     * Resume/wait/close only here, after the original OS thread has exited. */
    load_thread_active=&state->during;int duringReady=load_thread_finish(&state->during,"actual_ThreadEnd_callback_CreateThread");
    load_thread_active=&state->after;int afterReady=load_thread_finish(&state->after,"after_actual_detach_CreateThread");
    ready=parentReady&&sameStorage&&duringReady&&afterReady&&state->attachResult==JNI_OK&&state->attached
            &&state->detachResult==JNI_OK&&state->afterEnvResult==JNI_EDETACHED&&state->afterEnv==NULL
            &&InterlockedCompareExchange(&state->callbacks,0,0)==1&&!InterlockedCompareExchange(&state->callbackError,0,0)
            &&state->eventEnv==state->attached&&state->eventId==state->parent.id&&state->eventThreadPresent
            &&InterlockedCompareExchange(&state->continued,0,0)==1&&state->continuedId==state->parent.id
            &&state->continuedData==(uintptr_t)&state->parent&&state->continuedCookie==LOAD_THREAD_COOKIE&&state->tlsSet&&state->tlsCleared;
done:
    if(registered){
        jvmtiError disabled=(*api)->SetEventNotificationMode(api,JVMTI_DISABLE,JVMTI_EVENT_THREAD_END,NULL);
        jvmtiEventCallbacks empty={0};jvmtiError cleared=(*api)->SetEventCallbacks(api,&empty,(jint)sizeof(empty));
        if(disabled!=JVMTI_ERROR_NONE||cleared!=JVMTI_ERROR_NONE){ready=0;stage="HOST_THREAD_END_REAL_DISABLE_CLEAR";error=disabled!=JVMTI_ERROR_NONE?disabled:cleared;}
    }
    jvmtiError disposed=(*api)->DisposeEnvironment(api);if(disposed!=JVMTI_ERROR_NONE){ready=0;stage="HOST_THREAD_END_REAL_DISPOSE";error=disposed;}
    if(state&&((!state->parent.handle||state->parent.completed)&&(!state->during.handle||state->during.completed)&&(!state->after.handle||state->after.completed))){
        InterlockedExchangePointer((void *volatile*)&load_thread_end_active,NULL);InterlockedExchange(&load_thread_end_tls,-1);
        if(index!=TLS_OUT_OF_INDEXES&&!TlsFree(index)){ready=0;stage="HOST_THREAD_END_NATIVE_TLS_CLOSE";}
        load_thread_active=NULL;HeapFree(GetProcessHeap(),0,state);
    }
    /* If any real OS thread has not exited, its original userdata remains
     * allocated for the failing process; detach alone cannot free it. */
    if(saved){*callerError=savedError;*callerDosError=savedDosError;SetLastError(savedWindowsError);}
    if(!ready){fprintf(stderr,"HOST_THREAD_END_FAILED stage=%s jvmti=%d\n",stage,error);load_thread_fail(env,stage);return;}
    fprintf(stdout,"HOST_PRODUCER_WORKER_NOTRUN sourceLease start_DLL_pin real_OS_exit_lease creator_return_publication_race no_actual_producer_registration_for_this_profiler\n");
    fprintf(stdout,"HOST_CALLBACK_DETACH_INFLIGHT_NOTRUN no_public_legal_outer_callback_detach_trigger no_VM_internal_thread_detach\n");fflush(stdout);
}
static jint JNICALL load_thread_heap_iteration(jlong classTag,jlong size,jlong *tag,jint length,void *data){
    (void)length;LoadThreadHeap *state=load_thread_heap_active;
    if(!state)return JVMTI_VISIT_ABORT;
    if(data!=state||classTag!=state->classTag||size<=0||!tag||*tag!=state->objectTag||state->seen!=0){state->error=1;return JVMTI_VISIT_ABORT;}
    state->seen++;
    /* No JNI/GetEnv, JVMTI call, wait, output or lock in this callback. The
     * public Host-consumed CreateThread is the path under test. */
    SetLastError(0x524e0054);
    state->thread->handle=CreateThread(NULL,0,load_thread_start,state->thread,CREATE_SUSPENDED,&state->thread->id);
    state->thread->createError=GetLastError();return 0;
}
static int load_thread_heap_check(JNIEnv *env,JavaVM *vm,jclass type,jobject payload){
    jvmtiEnv *api=NULL;jvmtiError error=JVMTI_ERROR_NONE;const char *stage="HOST_THREAD_HEAP_REAL_GETENV";
    jlong savedClass=0,savedObject=0;int haveClass=0,haveObject=0,ready=0;
    LoadThreadCase *thread=NULL;LoadThreadHeap state={0};
    jint status=(*vm)->GetEnv(vm,(void**)&api,JVMTI_VERSION_1_2);
    if(status!=JNI_OK||!api){fprintf(stderr,"HOST_THREAD_HEAP_GETENV_RESULT jni=%d actualEnv=%p\n",status,(void*)api);return load_thread_fail(env,stage);}
    jvmtiCapabilities potential={0},request={0},actual={0};
    stage="HOST_THREAD_HEAP_REAL_TAG_CAPABILITY";error=(*api)->GetPotentialCapabilities(api,&potential);
    if(error!=JVMTI_ERROR_NONE||!potential.can_tag_objects)goto done;
    request.can_tag_objects=1;error=(*api)->AddCapabilities(api,&request);if(error!=JVMTI_ERROR_NONE)goto done;
    error=(*api)->GetCapabilities(api,&actual);if(error!=JVMTI_ERROR_NONE||!actual.can_tag_objects)goto done;
    stage="HOST_THREAD_HEAP_SAVE_REAL_TAGS";error=(*api)->GetTag(api,type,&savedClass);if(error!=JVMTI_ERROR_NONE)goto done;haveClass=1;
    error=(*api)->GetTag(api,payload,&savedObject);if(error!=JVMTI_ERROR_NONE)goto done;haveObject=1;
    state.classTag=0x524e5401;state.objectTag=0x524e5402;
    stage="HOST_THREAD_HEAP_SET_ACTUAL_TAGS";error=(*api)->SetTag(api,type,state.classTag);if(error!=JVMTI_ERROR_NONE)goto done;
    error=(*api)->SetTag(api,payload,state.objectTag);if(error!=JVMTI_ERROR_NONE)goto done;
    stage="HOST_THREAD_HEAP_NATIVE_USERDATA_ALLOCATION";thread=load_thread_case_new();if(!thread)goto done;
    state.thread=thread;load_thread_heap_active=&state;
    jvmtiHeapCallbacks callbacks={0};callbacks.heap_iteration_callback=load_thread_heap_iteration;
    stage="HOST_THREAD_HEAP_ACTUAL_CALLBACK_CREATE";
    error=(*api)->IterateThroughHeap(api,JVMTI_HEAP_FILTER_UNTAGGED,type,&callbacks,&state);
    load_thread_heap_active=NULL;
    /* Always settle a created thread after the synchronous walk returned,
     * including when a later callback/dispatch result reported failure. */
    int settled=load_thread_finish(thread,"tagged_heap_CreateThread");
    if(error!=JVMTI_ERROR_NONE||state.error||state.seen!=1||!settled)goto done;
    ready=1;
done:
    load_thread_heap_active=NULL;
    if(haveObject){jvmtiError restored=(*api)->SetTag(api,payload,savedObject);if(restored!=JVMTI_ERROR_NONE){ready=0;stage="HOST_THREAD_HEAP_RESTORE_OBJECT_TAG";error=restored;}}
    if(haveClass){jvmtiError restored=(*api)->SetTag(api,type,savedClass);if(restored!=JVMTI_ERROR_NONE){ready=0;stage="HOST_THREAD_HEAP_RESTORE_CLASS_TAG";error=restored;}}
    jvmtiError disposed=(*api)->DisposeEnvironment(api);if(disposed!=JVMTI_ERROR_NONE){ready=0;stage="HOST_THREAD_HEAP_DISPOSE_REAL_ENV";error=disposed;}
    load_thread_case_free(thread);
    if(!ready){fprintf(stderr,"HOST_THREAD_HEAP_RESULT stage=%s jvmti=%d seen=%u callbackError=%d\n",stage,error,state.seen,state.error);return load_thread_fail(env,stage);}
    return 1;
}
typedef HMODULE (WINAPI *LoadModuleA)(LPCSTR);
typedef HMODULE (WINAPI *LoadModuleW)(LPCWSTR);
typedef HMODULE (WINAPI *LoadModuleExA)(LPCSTR,HANDLE,DWORD);
typedef HMODULE (WINAPI *LoadModuleExW)(LPCWSTR,HANDLE,DWORD);
typedef BOOL (WINAPI *LoadModuleFree)(HMODULE);
/* Keep the call through an ordinary function pointer: a noreturn declaration
 * would let the compiler remove the returned marker we need to observe. */
typedef VOID (WINAPI *LoadModuleFreeExit)(HMODULE,DWORD);
typedef DWORD (WINAPI *LoadMappedNameW)(HANDLE,LPVOID,LPWSTR,DWORD);
extern LoadModuleFree __imp_FreeLibrary;
extern LoadModuleFreeExit __imp_FreeLibraryAndExitThread;
extern LoadModuleA __imp_LoadLibraryA;
extern LoadModuleW __imp_LoadLibraryW;
extern LoadModuleExA __imp_LoadLibraryExA;
extern LoadModuleExW __imp_LoadLibraryExW;
#ifdef AUDIT_HOST_JVMTI_CHECK
JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_hostDefinitionsChanged0(JNIEnv *env,jclass check,jobject loader){
    (void)check;JavaVM *vm=NULL;jvmtiEnv *api=NULL;jclass actual=NULL;const char *failure=NULL;
    if((*env)->GetJavaVM(env,&vm)!=JNI_OK||!vm||(*vm)->GetEnv(vm,(void**)&api,JVMTI_VERSION_1_2)!=JNI_OK||!api){load_thread_fail(env,"DEFINITIONS_REAL_ENVIRONMENT");return;}
    load_ti_identity=api;InterlockedExchange(&load_ti_first,0);InterlockedExchange(&load_ti_second,0);
    jvmtiCapabilities caps={0};caps.can_generate_all_class_hook_events=1;caps.can_retransform_classes=1;caps.can_redefine_classes=1;
    jvmtiError error=(*api)->AddCapabilities(api,&caps);LoadClassBytes image={0};jmethodID method=NULL;
    jvmtiEventCallbacks callbacks={0};callbacks.ClassFileLoadHook=load_ti_callback_first;
    if(error!=JVMTI_ERROR_NONE){failure="DEFINITIONS_REAL_CAPABILITIES";goto done;}
    error=(*api)->SetEventCallbacks(api,&callbacks,sizeof(callbacks));if(error!=JVMTI_ERROR_NONE){failure="DEFINITIONS_REAL_FIRST_CALLBACK";goto done;}
    error=(*api)->SetEventNotificationMode(api,JVMTI_ENABLE,JVMTI_EVENT_CLASS_FILE_LOAD_HOOK,NULL);if(error!=JVMTI_ERROR_NONE){failure="DEFINITIONS_REAL_EVENT_ENABLE";goto done;}
    actual=load_ti_define_image(env,loader,'A',&image);method=actual?(*env)->GetStaticMethodID(env,actual,"value","()I"):NULL;
    if(!method||(*env)->ExceptionCheck(env)||(*env)->CallStaticIntMethod(env,actual,method)!=9||load_ti_first!=1){failure="DEFINITIONS_REAL_INITIAL_METHOD";goto done;}
    callbacks.ClassFileLoadHook=load_ti_callback_second;error=(*api)->SetEventCallbacks(api,&callbacks,sizeof(callbacks));
    if(error!=JVMTI_ERROR_NONE){failure="DEFINITIONS_REAL_REPLACEMENT_CALLBACK";goto done;}
    error=(*api)->RetransformClasses(api,1,&actual);
    if(error!=JVMTI_ERROR_NONE||(*env)->ExceptionCheck(env)||(*env)->CallStaticIntMethod(env,actual,method)!=7||load_ti_second!=1){failure="DEFINITIONS_ACTUAL_RETRANSFORM";goto done;}
    int changed=0;for(unsigned i=0;i+2<image.length;i++)if(image.bytes[i]==0x10&&image.bytes[i+1]==7&&image.bytes[i+2]==0xac){image.bytes[i+1]=11;changed++;}
    if(changed!=1){failure="DEFINITIONS_ACTUAL_REDEFINE_IMAGE";goto done;}
    jvmtiClassDefinition definition={actual,(jint)image.length,image.bytes};error=(*api)->RedefineClasses(api,1,&definition);
    if(error!=JVMTI_ERROR_NONE||(*env)->ExceptionCheck(env)||(*env)->CallStaticIntMethod(env,actual,method)!=11||load_ti_second!=2){failure="DEFINITIONS_ACTUAL_REDEFINE";goto done;}
    error=(*api)->RetransformClasses(api,-1,&actual);
    if(error!=JVMTI_ERROR_ILLEGAL_ARGUMENT||(*env)->ExceptionCheck(env)||(*env)->CallStaticIntMethod(env,actual,method)!=11){failure="DEFINITIONS_ORIGINAL_INVALID_ARGUMENT";goto done;}
    fprintf(stderr,"DEFINITIONS_REAL_APIS_PASS initial=9 retransform=7 redefine=11 invalid_count=%d unchanged_after_failure=11\n",error);
done:
    {jvmtiError disposed=(*api)->DisposeEnvironment(api);if(disposed!=JVMTI_ERROR_NONE&&!failure){failure="DEFINITIONS_ACTUAL_DISPOSE";error=disposed;}}
    load_ti_identity=NULL;if(actual)(*env)->DeleteLocalRef(env,actual);
    if(failure){fprintf(stderr,"DEFINITIONS_REAL_FAILURE stage=%s jvmti=%d\n",failure,error);load_thread_fail(env,failure);return;}
    fprintf(stderr,"DEFINITIONS_MOD_PROPAGATION_AND_FAILED_NOTIFY_REAPER_NOTRUN no_actual_Mod_producer no_private_queue_reflection no_manufactured_authority\n");
}
#endif
static HMODULE load_module_call(void *entry,unsigned kind,const char *a,const wchar_t *w,DWORD flags){
    switch(kind){
        case 0:return ((LoadModuleA)entry)(a);
        case 1:return ((LoadModuleW)entry)(w);
        case 2:return ((LoadModuleExA)entry)(a,NULL,flags);
        default:return ((LoadModuleExW)entry)(w,NULL,flags);
    }
}
static const char *load_module_resource(void *entry,unsigned kind,const char *a,const wchar_t *w,DWORD flags,uintptr_t expectedTag){
    SetLastError(0x524e0056);
    HMODULE actual=load_module_call(entry,kind,a,w,flags);DWORD error=GetLastError();
    uintptr_t tag=(uintptr_t)actual&3;
    fprintf(stderr,"HOST_MODULE_RESOURCE_ACTUAL api=LoadLibraryEx%s mode=%s module=%p tag=%lu flags=%lu actualLastError=%lu\n",
            kind==2?"A":"W",kind==2?"import":"dynamic",(void*)actual,(unsigned long)tag,(unsigned long)flags,(unsigned long)error);
    if(!actual)return "HOST_MODULE_RESOURCE_LOAD_REFUSED";
    /* Inspect the returned handle, not an assumed flag-to-code-image relation.
     * Resource handles go only to their original FreeLibrary; no export runs. */
    int tagged=(tag&expectedTag)!=0;
    if(!FreeLibrary(actual))return "HOST_MODULE_RESOURCE_REFERENCE_RELEASE";
    return tagged?NULL:"HOST_MODULE_RESOURCE_RETURN_TAG_CHANGED";
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_hostModuleRelease0(JNIEnv *env,jclass check,jstring controlPath){
    (void)check;HMODULE kernel=GetModuleHandleW(L"kernel32.dll");
    void *entries[]={(void*)__imp_FreeLibrary,kernel?(void*)GetProcAddress(kernel,"FreeLibrary"):NULL};
    if(!controlPath||!load_private_entry(entries[0])||!load_private_entry(entries[1])){
        load_thread_fail(env,"HOST_RELEASE_ACTUAL_ENTRIES_OR_CONTROL_PATH");return;
    }
    jsize length=(*env)->GetStringLength(env,controlPath);
    wchar_t *path=(wchar_t*)calloc((size_t)length+1,sizeof(wchar_t));
    if(!path){load_thread_fail(env,"HOST_RELEASE_CONTROL_PATH_MEMORY");return;}
    (*env)->GetStringRegion(env,controlPath,0,length,(jchar*)path);
    HMODULE control=(*env)->ExceptionCheck(env)?NULL:GetModuleHandleW(path);free(path);
    if(!control){load_thread_fail(env,"HOST_RELEASE_ACTUAL_CONTROL_IMAGE");return;}
    for(unsigned mode=0;mode<2;mode++){
        LoadModuleFree release=(LoadModuleFree)entries[mode];
        HMODULE actual=LoadLibraryW(L"kernel32.dll");
        if(!actual||actual!=kernel){if(actual)FreeLibrary(actual);load_thread_fail(env,"HOST_RELEASE_ACTUAL_APPLICATION_REFERENCE");return;}
        SetLastError(0x524e0058);BOOL result=release(actual);DWORD error=GetLastError();
        fprintf(stderr,"HOST_RELEASE_ACTUAL mode=%s result=%d actualLastError=%lu\n",mode?"dynamic":"import",result!=FALSE,(unsigned long)error);
        if(!result){load_thread_fail(env,"HOST_RELEASE_ORIGINAL_SUCCESS_CHANGED");return;}
        SetLastError(0x524e0058);result=release(NULL);error=GetLastError();
        fprintf(stderr,"HOST_RELEASE_INVALID_ACTUAL mode=%s result=%d actualLastError=%lu\n",mode?"dynamic":"import",result!=FALSE,(unsigned long)error);
        if(result||!error||error==ERROR_ACCESS_DENIED){load_thread_fail(env,"HOST_RELEASE_ORIGINAL_FAILURE_CHANGED");return;}
        SetLastError(0x524e0058);result=release(control);error=GetLastError();
        if(result||error!=ERROR_ACCESS_DENIED){load_thread_fail(env,"HOST_RELEASE_CONTROL_IMAGE_NOT_PROTECTED");return;}
        fprintf(stderr,"HOST_RELEASE_CONTROL_REFUSED mode=%s module=%p actualLastError=%lu\n",mode?"dynamic":"import",(void*)control,(unsigned long)error);
    }
    fprintf(stderr,"HOST_RELEASE_STOPPED_CACHE_AND_DETACH_CHILD_SOURCES_NOTRUN no_actual_Mod_producer no_manufactured_sources; FreeLibraryAndExitThread_application_ref_ownership_and_final_unmap_not_covered\n");
}

typedef struct LoadModuleReleaseThread {
    LoadModuleFreeExit entry;HMODULE module;
    uint64_t cookie,observedCookie;uintptr_t observedData;
    DWORD requestedExit,observedId;volatile LONG entered,returned;
    HANDLE handle;
} LoadModuleReleaseThread;
static DWORD WINAPI load_module_release_thread_start(LPVOID data){
    LoadModuleReleaseThread *state=(LoadModuleReleaseThread*)data;
    state->observedData=(uintptr_t)data;state->observedCookie=state->cookie;
    state->observedId=GetCurrentThreadId();InterlockedIncrement(&state->entered);
    /* This CreateThread worker never attaches to the JVM or calls JNI/CRT. */
    state->entry(state->module,state->requestedExit);
    InterlockedIncrement(&state->returned);return LOAD_THREAD_EXIT+20;
}
static const char *load_module_release_thread_run(LoadModuleFreeExit entry,HMODULE module,DWORD requestedExit,
        unsigned mode,int control,int *started,int *completed){
    *started=0;*completed=0;
    LoadModuleReleaseThread *state=HeapAlloc(GetProcessHeap(),HEAP_ZERO_MEMORY,sizeof(*state));
    if(!state)return "HOST_FREE_EXIT_WORKER_STATE";
    state->entry=entry;state->module=module;state->requestedExit=requestedExit;state->cookie=LOAD_THREAD_COOKIE;
    DWORD id=0;state->handle=CreateThread(NULL,0,load_module_release_thread_start,state,CREATE_SUSPENDED,&id);
    DWORD createError=GetLastError();
    if(!state->handle){
        HeapFree(GetProcessHeap(),0,state);
        fprintf(stderr,"HOST_FREE_EXIT_WORKER_CREATE_FAILED mode=%s control=%d lastError=%lu\n",mode?"dynamic":"import",control,(unsigned long)createError);
        return "HOST_FREE_EXIT_WORKER_CREATE";
    }
    *started=1;
    DWORD actualId=GetThreadId(state->handle),beforeCode=0,exitCode=0;
    BOOL beforeQueried=GetExitCodeThread(state->handle,&beforeCode);
    DWORD beforeWait=WaitForSingleObject(state->handle,0);
    LONG beforeEntered=InterlockedCompareExchange(&state->entered,0,0);
    DWORD resumed=ResumeThread(state->handle),resumeError=GetLastError();
    DWORD waited=WaitForSingleObject(state->handle,5000);
    *completed=waited==WAIT_OBJECT_0;
    if(!*completed){
        /* Neither a timeout nor a failed resume proves the start routine has
         * left its userdata/module. Retain its actual handle and allocation. */
        fprintf(stderr,"HOST_FREE_EXIT_WORKER_UNSETTLED mode=%s control=%d threadId=%lu resume=%lu resumeError=%lu wait=%lu state=%p handle=%p retained=1\n",
                mode?"dynamic":"import",control,(unsigned long)id,(unsigned long)resumed,(unsigned long)resumeError,(unsigned long)waited,(void*)state,(void*)state->handle);
        return "HOST_FREE_EXIT_WORKER_NOT_EXITED";
    }
    BOOL queried=GetExitCodeThread(state->handle,&exitCode);
    LONG entered=InterlockedCompareExchange(&state->entered,0,0),returned=InterlockedCompareExchange(&state->returned,0,0);
    int observed=state->observedData==(uintptr_t)state&&state->observedCookie==LOAD_THREAD_COOKIE&&state->observedId==id;
    BOOL closed=CloseHandle(state->handle);
    int ok=id&&actualId==id&&id!=GetCurrentThreadId()&&beforeQueried&&beforeCode==STILL_ACTIVE
            &&beforeWait==WAIT_TIMEOUT&&beforeEntered==0&&resumed==1&&queried&&exitCode==requestedExit
            &&entered==1&&returned==0&&observed&&closed;
    fprintf(ok?stdout:stderr,"HOST_FREE_EXIT_WORKER mode=%s control=%d threadId=%lu actualThreadId=%lu requestedExit=%lu actualExit=%lu signaled=%d entered=%ld returned=%ld userdata=%d closed=%d\n",
            mode?"dynamic":"import",control,(unsigned long)id,(unsigned long)actualId,(unsigned long)requestedExit,
            (unsigned long)exitCode,*completed,(long)entered,(long)returned,observed,(int)closed);
    HeapFree(GetProcessHeap(),0,state);
    return ok?NULL:"HOST_FREE_EXIT_ORIGINAL_THREAD_CONTRACT";
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_hostModuleReleaseThread0(JNIEnv *env,jclass check,jstring controlPath){
    (void)check;const char *failure=NULL;wchar_t *controlW=NULL;
    HMODULE kernel=GetModuleHandleW(L"kernel32.dll"),control=NULL,observed=NULL,fixture=NULL;
    LoadModuleFreeExit entries[]={__imp_FreeLibraryAndExitThread,
            kernel?(LoadModuleFreeExit)GetProcAddress(kernel,"FreeLibraryAndExitThread"):NULL};
    LoadMappedNameW mappedName=kernel?(LoadMappedNameW)GetProcAddress(kernel,"K32GetMappedFileNameW"):NULL;
    if(!controlPath||!mappedName||!load_private_entry((void*)entries[0])||!load_private_entry((void*)entries[1])){
        load_thread_fail(env,"HOST_FREE_EXIT_ACTUAL_ENTRIES_OR_CONTROL_PATH");return;
    }
    jsize length=(*env)->GetStringLength(env,controlPath);controlW=calloc((size_t)length+1,sizeof(*controlW));
    if(!controlW){load_thread_fail(env,"HOST_FREE_EXIT_CONTROL_PATH_MEMORY");return;}
    (*env)->GetStringRegion(env,controlPath,0,length,(jchar*)controlW);
    if((*env)->ExceptionCheck(env)){free(controlW);return;}
    control=GetModuleHandleW(controlW);
    void *code=control?(void*)GetProcAddress(control,"Java_dev_ronova_pro_bootstrap_NativeControl_abi"):NULL;
    MEMORY_BASIC_INFORMATION before={0};unsigned char originalByte=0;
    if(!code||!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,(LPCSTR)code,&observed)
            ||observed!=control||VirtualQuery(code,&before,sizeof(before))!=sizeof(before)
            ||before.State!=MEM_COMMIT||before.Type!=MEM_IMAGE||before.AllocationBase!=control){
        failure="HOST_FREE_EXIT_EXACT_CONTROL_IMAGE";goto done;
    }
    originalByte=*(unsigned char*)code;
    wchar_t fixturePath[MAX_PATH],directory[MAX_PATH];
    if(!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
            (LPCSTR)(uintptr_t)load_module_release_thread_start,&fixture)){
        failure="HOST_FREE_EXIT_ACTUAL_FIXTURE_IMAGE";goto done;
    }
    DWORD count=GetModuleFileNameW(fixture,fixturePath,MAX_PATH);
    if(!count||count>=MAX_PATH||(count=GetTempPathW(MAX_PATH,directory))==0||count>=MAX_PATH){
        failure="HOST_FREE_EXIT_ACTUAL_FIXTURE_OR_TEMP_PATH";goto done;
    }
    for(unsigned mode=0;mode<2&&!failure;mode++){
        wchar_t path[MAX_PATH]={0},mappedBefore[MAX_PATH]={0};HMODULE resource=NULL;int started=0,completed=0;
        MEMORY_BASIC_INFORMATION resourceBefore={0};DWORD mappedLength=0;
        if(!GetTempFileNameW(directory,L"rve",0,path)){failure="HOST_FREE_EXIT_TEMP_DLL_CREATE";break;}
        /* DATAFILE executes no code and acquires no observed executable-image
         * pin. Keep its actual tagged handle for the API, and query only the
         * real mapped file; ordinary DLL application-ref/final-unmap is separate. */
        if(GetModuleHandleW(path)||!CopyFileW(fixturePath,path,FALSE))failure="HOST_FREE_EXIT_TEMP_DLL_COPY";
        if(!failure&&(resource=LoadLibraryExW(path,NULL,LOAD_LIBRARY_AS_DATAFILE))==NULL)failure="HOST_FREE_EXIT_RESOURCE_LOAD";
        if(!failure&&(VirtualQuery(resource,&resourceBefore,sizeof(resourceBefore))!=sizeof(resourceBefore)
                ||resourceBefore.State!=MEM_COMMIT||resourceBefore.Type!=MEM_MAPPED
                ||(mappedLength=mappedName(GetCurrentProcess(),resourceBefore.AllocationBase,mappedBefore,MAX_PATH))==0
                ||mappedLength>=MAX_PATH))failure="HOST_FREE_EXIT_RESOURCE_MAPPING";
        if(!failure)failure=load_module_release_thread_run(entries[mode],resource,LOAD_THREAD_EXIT+mode*2,mode,0,&started,&completed);
        if(started&&!completed){
            fprintf(stderr,"HOST_FREE_EXIT_ACTIVE_DLL_RETAINED mode=%s path=%ls module=%p\n",mode?"dynamic":"import",path,(void*)resource);
            break;
        }
        if(!started){
            if(resource&&!FreeLibrary(resource)&&!failure)failure="HOST_FREE_EXIT_RESOURCE_CLEANUP";
        }else{
            /* The former address can be reused. Query the backing identity,
             * without reading freed bytes or requiring MEM_FREE. */
            wchar_t mappedAfter[MAX_PATH]={0};
            DWORD afterLength=mappedName(GetCurrentProcess(),resourceBefore.AllocationBase,mappedAfter,MAX_PATH);
            MEMORY_BASIC_INFORMATION remaining={0};
            if(!afterLength&&VirtualQuery(resourceBefore.AllocationBase,&remaining,sizeof(remaining))==sizeof(remaining)
                    &&remaining.State==MEM_COMMIT&&remaining.Type==MEM_MAPPED&&remaining.AllocationBase==resourceBefore.AllocationBase
                    &&!failure)failure="HOST_FREE_EXIT_RESOURCE_IDENTITY_QUERY";
            int same=afterLength==mappedLength&&afterLength<MAX_PATH
                    &&memcmp(mappedBefore,mappedAfter,(size_t)mappedLength*sizeof(wchar_t))==0;
            fprintf(same?stderr:stdout,"HOST_FREE_EXIT_RESOURCE mode=%s module=%p original_view_gone=%d\n",
                    mode?"dynamic":"import",(void*)resource,!same);
            if(same){
                if(!failure)failure="HOST_FREE_EXIT_RESOURCE_VIEW_RETAINED";
                fprintf(stderr,"HOST_FREE_EXIT_RESOURCE_RETAINED path=%ls\n",path);break;
            }
        }
        if(!DeleteFileW(path)&&!failure)failure="HOST_FREE_EXIT_TEMP_DLL_REMOVE";
        if(failure)break;
        /* The existing controller handle is only an exact attack target. It is
         * deliberately not presented as a caller-owned normal library ref. */
        failure=load_module_release_thread_run(entries[mode],control,LOAD_THREAD_EXIT+mode*2+1,mode,1,&started,&completed);
        if(!completed)break;
        MEMORY_BASIC_INFORMATION after={0};
        int preserved=GetModuleHandleW(controlW)==control&&VirtualQuery(code,&after,sizeof(after))==sizeof(after)
                &&after.State==MEM_COMMIT&&after.Type==MEM_IMAGE&&after.AllocationBase==control
                &&after.Protect==before.Protect&&*(unsigned char*)code==originalByte;
        if(!preserved&&!failure)failure="HOST_FREE_EXIT_CONTROL_MAPPING_CHANGED";
        fprintf(preserved?stdout:stderr,"HOST_FREE_EXIT_CONTROL mode=%s borrowed_attack_target=1 mapping_preserved=%d\n",mode?"dynamic":"import",preserved);
    }
    if(!failure)fprintf(stderr,"HOST_FREE_EXIT_PRODUCER_STOPPED_SHARED_RESTRICTED_CALLBACKS_AND_EXECUTABLE_APP_REF_FINAL_UNMAP_NOTRUN control_mapping_is_not_internal_refcount_proof\n");
done:
    free(controlW);if(failure)load_thread_fail(env,failure);
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_hostModuleLoad0(JNIEnv *env,jclass check){
    (void)check;HMODULE kernel=GetModuleHandleW(L"kernel32.dll");
    const char *names[]={"LoadLibraryA","LoadLibraryW","LoadLibraryExA","LoadLibraryExW"};
    void *entries[]={(void*)__imp_LoadLibraryA,(void*)__imp_LoadLibraryW,(void*)__imp_LoadLibraryExA,(void*)__imp_LoadLibraryExW};
    if(!kernel){load_thread_fail(env,"HOST_MODULE_ACTUAL_KERNEL_IMAGE");return;}
    wchar_t directory[MAX_PATH],invalidW[MAX_PATH];char invalidA[MAX_PATH*3];
    DWORD count=GetTempPathW(MAX_PATH,directory);
    if(!count||count>=MAX_PATH||!GetTempFileNameW(directory,L"rvh",0,invalidW)){
        load_thread_fail(env,"HOST_MODULE_INVALID_IMAGE_CREATE");return;
    }
    int encoded=WideCharToMultiByte(CP_ACP,WC_NO_BEST_FIT_CHARS,invalidW,-1,invalidA,sizeof(invalidA),NULL,NULL);
    if(!encoded){DeleteFileW(invalidW);load_thread_fail(env,"HOST_MODULE_INVALID_IMAGE_ANSI_PATH");return;}
    const char *failure=NULL;
    for(unsigned kind=0;kind<4&&!failure;kind++){
        void *dynamic=(void*)GetProcAddress(kernel,names[kind]);
        if(!load_private_entry(entries[kind])||!load_private_entry(dynamic)){
            failure="HOST_MODULE_ACTUAL_IMPORT_OR_RESULT_UNCONTROLLED";break;
        }
        for(unsigned mode=0;mode<2&&!failure;mode++){
            void *entry=mode?dynamic:entries[kind];DWORD flags=kind>=2?LOAD_LIBRARY_SEARCH_SYSTEM32:0;
            SetLastError(0x524e0056);
            HMODULE actual=load_module_call(entry,kind,"kernel32.dll",L"kernel32.dll",flags);DWORD error=GetLastError();
            if(!actual){failure="HOST_MODULE_VALID_LOAD_REFUSED";break;}
            if(actual!=kernel)failure="HOST_MODULE_ORIGINAL_RETURN_CHANGED";
            if(!FreeLibrary(actual)&&!failure)failure="HOST_MODULE_ACTUAL_REFERENCE_RELEASE";
            if(failure)break;
            SetLastError(0x524e0056);actual=load_module_call(entry,kind,invalidA,invalidW,0);DWORD invalid=GetLastError();
            if(actual){FreeLibrary(actual);failure="HOST_MODULE_INVALID_IMAGE_ACCEPTED";break;}
            if(invalid!=ERROR_BAD_EXE_FORMAT){failure="HOST_MODULE_REAL_INVALID_IMAGE_ERROR_CHANGED";break;}
            fprintf(stderr,"HOST_MODULE_ACTUAL api=%s mode=%s module=%p validLastError=%lu invalidImageError=%lu flags=%lu\n",
                    names[kind],mode?"dynamic":"import",(void*)kernel,(unsigned long)error,(unsigned long)invalid,(unsigned long)flags);
        }
    }
    if(!failure){
        HMODULE fixture=NULL;wchar_t fixturePath[MAX_PATH];
        if(!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
                (LPCSTR)(uintptr_t)load_module_call,&fixture))failure="HOST_MODULE_RESOURCE_ACTUAL_FIXTURE_IMAGE";
        DWORD length=failure?0:GetModuleFileNameW(fixture,fixturePath,MAX_PATH);
        if(!failure&&(!length||length>=MAX_PATH))failure="HOST_MODULE_RESOURCE_ACTUAL_FIXTURE_PATH";
        // Reuse the temporary input as a copy of this real PE, never as a new
        // executable child or a manufactured JNI/source registration.
        if(!failure&&!CopyFileW(fixturePath,invalidW,FALSE))failure="HOST_MODULE_RESOURCE_PE_COPY";
        if(!failure)failure=load_module_resource(entries[2],2,invalidA,invalidW,LOAD_LIBRARY_AS_DATAFILE,1);
        if(!failure){
            void *dynamic=(void*)GetProcAddress(kernel,"LoadLibraryExW");
            if(!load_private_entry(dynamic))failure="HOST_MODULE_RESOURCE_DYNAMIC_ENTRY_UNCONTROLLED";
            else failure=load_module_resource(dynamic,3,invalidA,invalidW,LOAD_LIBRARY_AS_IMAGE_RESOURCE,2);
        }
    }
    if(!DeleteFileW(invalidW)&&!failure)failure="HOST_MODULE_INVALID_IMAGE_REMOVE";
    if(failure){load_thread_fail(env,failure);return;}
    fprintf(stderr,"HOST_MODULE_PRODUCER_AND_NEW_CHILD_SOURCES_NOTRUN no_actual_Mod_producer no_new_executable_child_DLL no_fabricated_sources; preobservation_loader_window_not_covered\n");
}

/* One independent memory phase. Every writable allocation and process handle
 * belongs to this process; no controller, producer or raw backend is embedded. */
typedef BOOL (WINAPI *LoadMemoryWrite)(HANDLE,LPVOID,LPCVOID,SIZE_T,SIZE_T*);
typedef BOOL (WINAPI *LoadMemoryProtect)(LPVOID,SIZE_T,DWORD,PDWORD);
typedef BOOL (WINAPI *LoadMemoryProtectEx)(HANDLE,LPVOID,SIZE_T,DWORD,PDWORD);
extern LoadMemoryWrite __imp_WriteProcessMemory;
extern LoadMemoryProtect __imp_VirtualProtect;
extern LoadMemoryProtectEx __imp_VirtualProtectEx;
typedef struct LoadMemoryEntries {
    LoadMemoryWrite write[2];LoadMemoryProtect protect[2];LoadMemoryProtectEx protectEx[2];
} LoadMemoryEntries;
typedef struct LoadMemoryTarget {
    void *address;SIZE_T bytes;DWORD protect;BYTE before[sizeof(uintptr_t)];
} LoadMemoryTarget;
static int load_memory_readonly(DWORD protect){
    DWORD basic=protect&0xff;
    return !(protect&PAGE_GUARD)&&(basic==PAGE_READONLY||basic==PAGE_EXECUTE_READ);
}
static int load_memory_target(LoadMemoryTarget *target,void *address,SIZE_T bytes,DWORD type,void *allocation){
    MEMORY_BASIC_INFORMATION region={0};
    if(!address||!bytes||bytes>sizeof(target->before)||VirtualQuery(address,&region,sizeof(region))!=sizeof(region)
            ||region.State!=MEM_COMMIT||region.Type!=type||!load_memory_readonly(region.Protect)
            ||allocation&&region.AllocationBase!=allocation)return 0;
    uintptr_t offset=(uintptr_t)address-(uintptr_t)region.BaseAddress;
    if(offset>=region.RegionSize||bytes>region.RegionSize-offset)return 0;
    target->address=address;target->bytes=bytes;target->protect=region.Protect;
    /* The observed API must not also produce its own reference snapshot.
     * With -fno-builtin, memcpy is a real controlled import and can refuse a
     * control-table read. Inspect the actual stable bytes directly instead. */
    for(SIZE_T i=0;i<bytes;i++)target->before[i]=((volatile const BYTE*)address)[i];return 1;
}
static int load_memory_unchanged(const LoadMemoryTarget *target){
    MEMORY_BASIC_INFORMATION region={0};
    return VirtualQuery(target->address,&region,sizeof(region))==sizeof(region)&&region.State==MEM_COMMIT
            &&region.Protect==target->protect&&!memcmp(target->address,target->before,target->bytes);
}
static int load_memory_scratch_protect(BYTE *scratch,DWORD expected){
    MEMORY_BASIC_INFORMATION region={0};return VirtualQuery(scratch,&region,sizeof(region))==sizeof(region)
            &&region.State==MEM_COMMIT&&region.Type==MEM_PRIVATE&&region.AllocationBase==scratch&&region.Protect==expected;
}
static const char *load_memory_normal(const LoadMemoryEntries *entries,HANDLE actual,BYTE *scratch){
    static const BYTE patterns[2][8]={{0x31,0x72,0x19,0xa4,0x55,0x96,0x27,0xe8},{0x82,0x13,0x64,0xb5,0x26,0xd7,0x48,0x99}};
    for(unsigned mode=0;mode<2;mode++){
        HANDLE process=mode?actual:GetCurrentProcess();SIZE_T written=0,read=0;BYTE observed[8]={0};DWORD old=0;
        /* The count is optional for WPM. No success LastError value is assumed. */
        if(!entries->write[mode](process,scratch+16,patterns[mode],sizeof(patterns[mode]),mode?NULL:&written)
                ||!mode&&written!=sizeof(patterns[mode])||memcmp(scratch+16,patterns[mode],sizeof(patterns[mode])))return "HOST_MEMORY_ORIGINAL_SCRATCH_WRITE";
        if(!ReadProcessMemory(process,scratch+16,observed,sizeof(observed),&read)||read!=sizeof(observed)
                ||memcmp(observed,patterns[mode],sizeof(observed)))return "HOST_MEMORY_ORIGINAL_SCRATCH_READ";
        if(!entries->protect[mode](scratch+1,1,PAGE_READONLY,&old)||old!=PAGE_READWRITE||!load_memory_scratch_protect(scratch,PAGE_READONLY))return "HOST_MEMORY_ORIGINAL_PRIVATE_PROTECT";
        if(!entries->protect[mode](scratch+1,1,PAGE_READWRITE,&old)||old!=PAGE_READONLY||!load_memory_scratch_protect(scratch,PAGE_READWRITE))return "HOST_MEMORY_ORIGINAL_PRIVATE_PROTECT_RESTORE";
        if(!entries->protectEx[mode](process,scratch+2,1,PAGE_READONLY,&old)||old!=PAGE_READWRITE||!load_memory_scratch_protect(scratch,PAGE_READONLY))return "HOST_MEMORY_ORIGINAL_PRIVATE_PROTECT_EX";
        if(!entries->protectEx[mode](process,scratch+2,1,PAGE_READWRITE,&old)||old!=PAGE_READONLY||!load_memory_scratch_protect(scratch,PAGE_READWRITE))return "HOST_MEMORY_ORIGINAL_PRIVATE_PROTECT_EX_RESTORE";
        if(memcmp(scratch+16,patterns[mode],sizeof(patterns[mode])))return "HOST_MEMORY_PRIVATE_PROTECTION_CHANGED_DATA";
    }
    return NULL;
}
static const char *load_memory_write_refused(LoadMemoryWrite entry,HANDLE process,const LoadMemoryTarget *target,const char *stage){
    SIZE_T written=(SIZE_T)-1;SetLastError(0x524e004d);
    BOOL result=entry(process,target->address,target->before,target->bytes,&written);DWORD error=GetLastError();
    fprintf(stderr,"HOST_MEMORY_WRITE_ACTUAL target=%s result=%d win32=%lu bytes=%llu\n",stage,result!=FALSE,(unsigned long)error,(unsigned long long)written);
    /* Read-only OS failure alone is not a successful policy rejection. */
    return !result&&error==ERROR_ACCESS_DENIED&&written==(SIZE_T)-1&&load_memory_unchanged(target)?NULL:stage;
}
static const char *load_memory_protect_refused(const LoadMemoryEntries *entries,HANDLE process,const LoadMemoryTarget *target,int extended,void *address,const char *stage){
    DWORD old=0x524e004d;SetLastError(0x524e004d);
    /* Even an unexpectedly accepted call cannot remove execute permission or
     * enable a write: the requested protection is exactly the live original. */
    BOOL result=extended?entries->protectEx[0](process,address,1,target->protect,&old):entries->protect[1](address,1,target->protect,&old);
    DWORD error=GetLastError();fprintf(stderr,"HOST_MEMORY_PROTECT_ACTUAL target=%s extended=%d result=%d win32=%lu\n",stage,extended,result!=FALSE,(unsigned long)error);
    return !result&&error==ERROR_ACCESS_DENIED&&load_memory_unchanged(target)?NULL:stage;
}
static const char *load_memory_outputs(const LoadMemoryEntries *entries,HANDLE actual,BYTE *scratch,const LoadMemoryTarget *output){
    BYTE before=scratch[24],next=(BYTE)(before^0x5a);SetLastError(0x524e004d);
    BOOL result=entries->write[0](GetCurrentProcess(),scratch+24,&next,1,(SIZE_T*)output->address);DWORD error=GetLastError();
    if(result||error!=ERROR_ACCESS_DENIED||scratch[24]!=before||!load_memory_unchanged(output))return "HOST_MEMORY_PROTECTED_SIZE_T_OUTPUT";
    /* The output alias is in the exact DLL's reserved DOS header, never in
     * an instruction. No failure path attempts to write a replacement output. */
    SetLastError(0x524e004d);result=entries->protect[0](scratch+24,1,PAGE_READONLY,(DWORD*)output->address);error=GetLastError();
    if(result||error!=ERROR_ACCESS_DENIED||!load_memory_scratch_protect(scratch,PAGE_READWRITE)||!load_memory_unchanged(output))return "HOST_MEMORY_PROTECTED_DWORD_OUTPUT";
    SetLastError(0x524e004d);result=entries->protectEx[1](actual,scratch+24,1,PAGE_READONLY,(DWORD*)output->address);error=GetLastError();
    if(result||error!=ERROR_ACCESS_DENIED||!load_memory_scratch_protect(scratch,PAGE_READWRITE)||!load_memory_unchanged(output))return "HOST_MEMORY_PROTECTED_DWORD_OUTPUT_EX";
    return NULL;
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_hostMemory0(JNIEnv *env,jclass check,jstring controlPath){
    (void)check;const char *failure=NULL;HANDLE actual=NULL;BYTE *scratch=NULL;wchar_t *path=NULL;JavaVM *vm=NULL;
    HMODULE kernel=GetModuleHandleW(L"kernel32.dll"),base=GetModuleHandleW(L"kernelbase.dll"),control=NULL,observed=NULL;
    SYSTEM_INFO system={0};GetSystemInfo(&system);
    LoadMemoryEntries entries={{__imp_WriteProcessMemory,kernel?(LoadMemoryWrite)GetProcAddress(kernel,"WriteProcessMemory"):NULL},
        {__imp_VirtualProtect,kernel?(LoadMemoryProtect)GetProcAddress(kernel,"VirtualProtect"):NULL},
        {__imp_VirtualProtectEx,kernel?(LoadMemoryProtectEx)GetProcAddress(kernel,"VirtualProtectEx"):NULL}};
    LoadSame same=base?(LoadSame)GetProcAddress(base,"CompareObjectHandles"):NULL;
    for(unsigned mode=0;mode<2;mode++)if(!load_private_entry((void*)entries.write[mode])||!load_private_entry((void*)entries.protect[mode])||!load_private_entry((void*)entries.protectEx[mode])){
        failure="HOST_MEMORY_ACTUAL_IMPORT_OR_GPA_NOT_CONTROLLED";goto done;
    }
    if(!same||!DuplicateHandle(GetCurrentProcess(),GetCurrentProcess(),GetCurrentProcess(),&actual,
            PROCESS_VM_OPERATION|PROCESS_VM_WRITE|PROCESS_VM_READ,FALSE,0)||!same(GetCurrentProcess(),actual)){
        failure="HOST_MEMORY_ACTUAL_VM_RIGHTS_ONLY_CURRENT_ALIAS";goto done;
    }
    if(system.dwPageSize<64||(scratch=VirtualAlloc(NULL,system.dwPageSize,MEM_COMMIT|MEM_RESERVE,PAGE_READWRITE))==NULL){failure="HOST_MEMORY_PRIVATE_SCRATCH_ALLOCATION";goto done;}
    failure=load_memory_normal(&entries,actual,scratch);if(failure)goto done;
    if(!controlPath){failure="HOST_MEMORY_ACTUAL_CONTROL_PATH";goto done;}
    jsize length=(*env)->GetStringLength(env,controlPath);path=calloc((size_t)length+1,sizeof(*path));
    if(!path){failure="HOST_MEMORY_CONTROL_PATH_ALLOCATION";goto done;}
    (*env)->GetStringRegion(env,controlPath,0,length,(jchar*)path);if((*env)->ExceptionCheck(env))goto done;
    control=GetModuleHandleW(path);free(path);path=NULL;
    void *code=control?(void*)GetProcAddress(control,"Java_dev_ronova_pro_bootstrap_NativeControl_abi"):NULL;
    LoadMemoryTarget codeTarget={0},vmTarget={0},thunkTarget={0},outputTarget={0};
    if(!code||!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,(LPCSTR)code,&observed)
            ||observed!=control||!load_memory_target(&codeTarget,code,1,MEM_IMAGE,control)){
        failure="HOST_MEMORY_EXACT_CONTROL_DLL_CODE";goto done;
    }
    if((*env)->GetJavaVM(env,&vm)!=JNI_OK||!vm||!*vm||!(*vm)->GetEnv
            ||!load_memory_target(&vmTarget,(void*)&(*vm)->GetEnv,sizeof((*vm)->GetEnv),MEM_PRIVATE,(void*)*vm)){
        failure="HOST_MEMORY_ACTUAL_VM_SHADOW_GETENV_SLOT";goto done;
    }
    if(!load_memory_target(&thunkTarget,(void*)entries.write[1],1,MEM_PRIVATE,NULL)){failure="HOST_MEMORY_ACTUAL_GPA_HOOK_THUNK";goto done;}
    IMAGE_DOS_HEADER *dos=(IMAGE_DOS_HEADER*)control;
    if(dos->e_magic!=IMAGE_DOS_SIGNATURE||(uintptr_t)&dos->e_res2[0]%sizeof(SIZE_T)
            ||!load_memory_target(&outputTarget,&dos->e_res2[0],sizeof(SIZE_T),MEM_IMAGE,control)){
        failure="HOST_MEMORY_EXACT_READONLY_CONTROL_OUTPUT_AREA";goto done;
    }
    failure=load_memory_write_refused(entries.write[0],GetCurrentProcess(),&codeTarget,"HOST_MEMORY_CONTROL_CODE_SAME_VALUE_WRITE");if(failure)goto done;
    failure=load_memory_write_refused(entries.write[1],actual,&vmTarget,"HOST_MEMORY_VM_SHADOW_SAME_VALUE_WRITE");if(failure)goto done;
    failure=load_memory_write_refused(entries.write[0],actual,&thunkTarget,"HOST_MEMORY_HOOK_THUNK_SAME_VALUE_WRITE");if(failure)goto done;
    failure=load_memory_protect_refused(&entries,GetCurrentProcess(),&codeTarget,0,codeTarget.address,"HOST_MEMORY_CONTROL_CODE_PROTECT");if(failure)goto done;
    /* The address is outside the public VM table structure but in its actual
     * committed page. A byte-only overlap check would miss VirtualProtect's effect. */
    uintptr_t vmPage=(uintptr_t)*vm-(uintptr_t)*vm%system.dwPageSize;
    void *tail=(void*)(vmPage+system.dwPageSize-1);MEMORY_BASIC_INFORMATION region={0};
    if((uintptr_t)tail<(uintptr_t)*vm+sizeof(**vm)||VirtualQuery(tail,&region,sizeof(region))!=sizeof(region)
            ||region.State!=MEM_COMMIT||region.AllocationBase!=(void*)*vm||region.Protect!=vmTarget.protect){
        failure="HOST_MEMORY_ACTUAL_SHADOW_PAGE_TAIL";goto done;
    }
    failure=load_memory_protect_refused(&entries,actual,&vmTarget,1,tail,"HOST_MEMORY_SHADOW_SAME_PAGE_TAIL_PROTECT_EX");if(failure)goto done;
    failure=load_memory_protect_refused(&entries,actual,&thunkTarget,0,thunkTarget.address,"HOST_MEMORY_HOOK_THUNK_PROTECT");if(failure)goto done;
    failure=load_memory_outputs(&entries,actual,scratch,&outputTarget);
done:
    {
        DWORD failureError=GetLastError();free(path);
        if(scratch&&!VirtualFree(scratch,0,MEM_RELEASE)&&!failure){failure="HOST_MEMORY_PRIVATE_SCRATCH_RELEASE";failureError=GetLastError();}
        if(actual&&!CloseHandle(actual)&&!failure){failure="HOST_MEMORY_CURRENT_ALIAS_CLOSE";failureError=GetLastError();}
        if(failure){fprintf(stderr,"HOST_MEMORY_FAILED stage=%s win32=%lu\n",failure,(unsigned long)failureError);load_thread_fail(env,failure);}
    }
}
/* Independent release-memory phase. A released/decommitted address is only
 * queried; data is read again only after a successful commit at the same base. */
typedef BOOL (WINAPI *LoadMemoryFree)(LPVOID,SIZE_T,DWORD);
typedef BOOL (WINAPI *LoadMemoryFreeEx)(HANDLE,LPVOID,SIZE_T,DWORD);
typedef BOOL (WINAPI *LoadMemoryUnmap)(LPCVOID);
typedef BOOL (WINAPI *LoadMemoryUnmapEx)(PVOID,ULONG);
extern LoadMemoryFree __imp_VirtualFree;
extern LoadMemoryFreeEx __imp_VirtualFreeEx;
extern LoadMemoryUnmap __imp_UnmapViewOfFile;
extern LoadMemoryUnmapEx __imp_UnmapViewOfFileEx;
typedef struct LoadMemoryReleaseEntries {
    LoadMemoryFree release[2];LoadMemoryFreeEx releaseEx[2];
    LoadMemoryUnmap unmap[2];LoadMemoryUnmapEx unmapEx[2];
} LoadMemoryReleaseEntries;
typedef struct LoadMemoryReleaseTarget {
    LoadMemoryTarget data;MEMORY_BASIC_INFORMATION region;
} LoadMemoryReleaseTarget;
/* The documented UnmapViewOfFileEx transient boost flag; no placeholder is
 * reserved or replaced by this fixture. */
enum {LOAD_MEMORY_UNMAP_TRANSIENT_BOOST=0x00000001UL};
static int load_memory_release_target(LoadMemoryReleaseTarget *target,void *address,SIZE_T bytes,DWORD type,void *allocation){
    return load_memory_target(&target->data,address,bytes,type,allocation)
            &&VirtualQuery(address,&target->region,sizeof(target->region))==sizeof(target->region)
            &&target->region.AllocationBase;
}
static int load_memory_release_kept(const LoadMemoryReleaseTarget *target){
    MEMORY_BASIC_INFORMATION region={0};
    return VirtualQuery(target->data.address,&region,sizeof(region))==sizeof(region)&&region.State==MEM_COMMIT
            &&region.BaseAddress==target->region.BaseAddress&&region.RegionSize==target->region.RegionSize
            &&region.AllocationBase==target->region.AllocationBase&&region.Type==target->region.Type
            &&region.Protect==target->data.protect&&!memcmp(target->data.address,target->data.before,target->data.bytes);
}
static int load_memory_release_state(void *address,DWORD state,void *allocation){
    MEMORY_BASIC_INFORMATION region={0};
    if(VirtualQuery(address,&region,sizeof(region))!=sizeof(region)||region.State!=state)return 0;
    /* AllocationBase, Protect and Type are undefined for MEM_FREE. */
    return state==MEM_FREE||region.AllocationBase==allocation;
}
static const char *load_memory_release_normal(const LoadMemoryReleaseEntries *entries,HANDLE actual,DWORD pageSize){
    const char *failure=NULL;DWORD error=0;BYTE *scratch=NULL;
    for(unsigned extended=0;extended<2;extended++)for(unsigned mode=0;mode<2;mode++){
        HANDLE process=mode?actual:GetCurrentProcess();
        scratch=VirtualAlloc(NULL,pageSize,MEM_COMMIT|MEM_RESERVE,PAGE_READWRITE);
        if(!scratch){failure="HOST_MEMORY_RELEASE_PRIVATE_ALLOCATION";error=GetLastError();goto done;}
        if(!load_memory_scratch_protect(scratch,PAGE_READWRITE)){failure="HOST_MEMORY_RELEASE_PRIVATE_REGION";error=ERROR_INVALID_DATA;goto done;}
        ((volatile BYTE*)scratch)[1]=0x5a;
        BOOL result=extended?entries->releaseEx[mode](process,scratch+1,1,MEM_DECOMMIT):entries->release[mode](scratch+1,1,MEM_DECOMMIT);
        if(!result){failure="HOST_MEMORY_RELEASE_ORIGINAL_DECOMMIT";error=GetLastError();goto done;}
        if(!load_memory_release_state(scratch,MEM_RESERVE,scratch)){failure="HOST_MEMORY_RELEASE_DECOMMIT_NOT_RESERVED";error=ERROR_INVALID_DATA;goto done;}
        void *committed=VirtualAlloc(scratch,pageSize,MEM_COMMIT,PAGE_READWRITE);
        if(!committed){failure="HOST_MEMORY_RELEASE_ORIGINAL_RECOMMIT";error=GetLastError();goto done;}
        if(committed!=scratch||!load_memory_scratch_protect(scratch,PAGE_READWRITE)||((volatile BYTE*)scratch)[1]!=0){
            failure="HOST_MEMORY_RELEASE_RECOMMIT_DATA_OR_REGION";error=ERROR_INVALID_DATA;goto done;
        }
        ((volatile BYTE*)scratch)[1]=0xa5;if(((volatile BYTE*)scratch)[1]!=0xa5){failure="HOST_MEMORY_RELEASE_RECOMMIT_WRITE";error=ERROR_INVALID_DATA;goto done;}
        void *released=scratch;
        result=extended?entries->releaseEx[mode](process,scratch,0,MEM_RELEASE):entries->release[mode](scratch,0,MEM_RELEASE);
        if(!result){failure="HOST_MEMORY_RELEASE_ORIGINAL_RELEASE";error=GetLastError();goto done;}
        scratch=NULL;
        if(!load_memory_release_state(released,MEM_FREE,NULL)){failure="HOST_MEMORY_RELEASE_RELEASE_NOT_FREE";error=ERROR_INVALID_DATA;goto done;}
        fprintf(stderr,"HOST_MEMORY_RELEASE_SCRATCH_ACTUAL route=%s extended=%u decommit_recommit_release=1\n",mode?"GPA":"IAT",extended);
    }
done:
    if(scratch&&!VirtualFree(scratch,0,MEM_RELEASE)&&!failure){failure="HOST_MEMORY_RELEASE_PRIVATE_CLEANUP";error=GetLastError();}
    if(failure)SetLastError(error);return failure;
}
static const char *load_memory_release_mapped(const LoadMemoryReleaseEntries *entries,DWORD pageSize){
    wchar_t directory[MAX_PATH]={0},path[MAX_PATH]={0};HANDLE file=INVALID_HANDLE_VALUE,mapping=NULL;BYTE *view=NULL;
    const char *failure=NULL;int temporary=0;DWORD error=0,count=GetTempPathW(MAX_PATH,directory);
    if(!count||count>=MAX_PATH){failure="HOST_MEMORY_RELEASE_TEMP_DIRECTORY";error=GetLastError();goto done;}
    if(!GetTempFileNameW(directory,L"rvm",0,path)){failure="HOST_MEMORY_RELEASE_TEMP_FILE";error=GetLastError();goto done;}
    temporary=1;
    file=CreateFileW(path,GENERIC_READ|GENERIC_WRITE,FILE_SHARE_READ|FILE_SHARE_WRITE|FILE_SHARE_DELETE,NULL,OPEN_EXISTING,FILE_ATTRIBUTE_TEMPORARY,NULL);
    if(file==INVALID_HANDLE_VALUE){failure="HOST_MEMORY_RELEASE_TEMP_FILE_OPEN";error=GetLastError();goto done;}
    mapping=CreateFileMappingW(file,NULL,PAGE_READWRITE,0,pageSize,NULL);
    if(!mapping){failure="HOST_MEMORY_RELEASE_REAL_FILE_MAPPING";error=GetLastError();goto done;}
    for(unsigned extended=0;extended<2;extended++)for(unsigned mode=0;mode<2;mode++){
        view=MapViewOfFile(mapping,FILE_MAP_READ|FILE_MAP_WRITE,0,0,pageSize);
        if(!view){failure="HOST_MEMORY_RELEASE_REAL_FILE_VIEW";error=GetLastError();goto done;}
        MEMORY_BASIC_INFORMATION region={0};
        if(VirtualQuery(view,&region,sizeof(region))!=sizeof(region)||region.State!=MEM_COMMIT
                ||region.Type!=MEM_MAPPED||region.AllocationBase!=view||region.Protect!=PAGE_READWRITE){
            failure="HOST_MEMORY_RELEASE_REAL_MAPPED_REGION";error=ERROR_INVALID_DATA;goto done;
        }
        ((volatile BYTE*)view)[0]=(BYTE)(0x31+extended*2+mode);
        if(((volatile BYTE*)view)[0]!=(BYTE)(0x31+extended*2+mode)){failure="HOST_MEMORY_RELEASE_REAL_MAPPED_WRITE";error=ERROR_INVALID_DATA;goto done;}
        void *unmapped=view;ULONG flags=mode?LOAD_MEMORY_UNMAP_TRANSIENT_BOOST:0;
        BOOL result=extended?entries->unmapEx[mode](view,flags):entries->unmap[mode](view);
        if(!result){failure="HOST_MEMORY_RELEASE_ORIGINAL_UNMAP";error=GetLastError();goto done;}
        view=NULL;
        if(!load_memory_release_state(unmapped,MEM_FREE,NULL)){failure="HOST_MEMORY_RELEASE_UNMAP_NOT_FREE";error=ERROR_INVALID_DATA;goto done;}
        fprintf(stderr,"HOST_MEMORY_RELEASE_MAPPING_ACTUAL route=%s extended=%u flags=%lu unmap=1\n",mode?"GPA":"IAT",extended,(unsigned long)(extended?flags:0));
    }
done:
    if(view&&!UnmapViewOfFile(view)&&!failure){failure="HOST_MEMORY_RELEASE_VIEW_CLEANUP";error=GetLastError();}
    if(mapping&&!CloseHandle(mapping)&&!failure){failure="HOST_MEMORY_RELEASE_MAPPING_CLOSE";error=GetLastError();}
    if(file!=INVALID_HANDLE_VALUE&&!CloseHandle(file)&&!failure){failure="HOST_MEMORY_RELEASE_TEMP_FILE_CLOSE";error=GetLastError();}
    if(temporary&&!DeleteFileW(path)&&!failure){failure="HOST_MEMORY_RELEASE_TEMP_FILE_REMOVE";error=GetLastError();}
    if(failure)SetLastError(error);return failure;
}
static const char *load_memory_release_refused(const LoadMemoryReleaseEntries *entries,unsigned mode,HANDLE process,int extended,
        void *address,SIZE_T size,DWORD kind,const LoadMemoryReleaseTarget *target,const char *stage){
    SetLastError(0x524e0052);
    BOOL result=extended?entries->releaseEx[mode](process,address,size,kind):entries->release[mode](address,size,kind);
    DWORD error=GetLastError();fprintf(stderr,"HOST_MEMORY_RELEASE_REFUSED_ACTUAL target=%s route=%s extended=%d kind=%lu size=%llu result=%d win32=%lu\n",
            stage,mode?"GPA":"IAT",extended,(unsigned long)kind,(unsigned long long)size,result!=FALSE,(unsigned long)error);
    /* Short circuit on acceptance: never read memory the OS may have freed. */
    int denied=!result&&error==ERROR_ACCESS_DENIED&&load_memory_release_kept(target);SetLastError(error);
    return denied?NULL:stage;
}
static const char *load_memory_unmap_refused(const LoadMemoryReleaseEntries *entries,unsigned mode,int extended,ULONG flags,
        HMODULE control,const LoadMemoryReleaseTarget *target,const char *stage){
    SetLastError(0x524e0052);
    BOOL result=extended?entries->unmapEx[mode]((void*)control,flags):entries->unmap[mode]((void*)control);
    DWORD error=GetLastError();fprintf(stderr,"HOST_MEMORY_UNMAP_REFUSED_ACTUAL target=%s route=%s extended=%d flags=%lu result=%d win32=%lu\n",
            stage,mode?"GPA":"IAT",extended,(unsigned long)flags,result!=FALSE,(unsigned long)error);
    int denied=!result&&error==ERROR_ACCESS_DENIED&&load_memory_release_kept(target);SetLastError(error);
    return denied?NULL:stage;
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_hostMemoryRelease0(JNIEnv *env,jclass check,jstring controlPath){
    (void)check;const char *failure=NULL;HANDLE actual=NULL;wchar_t *path=NULL;JavaVM *vm=NULL;
    HMODULE kernel=GetModuleHandleW(L"kernel32.dll"),base=GetModuleHandleW(L"kernelbase.dll"),control=NULL,observed=NULL;
    SYSTEM_INFO system={0};GetSystemInfo(&system);
    LoadMemoryReleaseEntries entries={{__imp_VirtualFree,kernel?(LoadMemoryFree)GetProcAddress(kernel,"VirtualFree"):NULL},
        {__imp_VirtualFreeEx,kernel?(LoadMemoryFreeEx)GetProcAddress(kernel,"VirtualFreeEx"):NULL},
        {__imp_UnmapViewOfFile,kernel?(LoadMemoryUnmap)GetProcAddress(kernel,"UnmapViewOfFile"):NULL},
        {__imp_UnmapViewOfFileEx,kernel?(LoadMemoryUnmapEx)GetProcAddress(kernel,"UnmapViewOfFileEx"):NULL}};
    LoadSame same=base?(LoadSame)GetProcAddress(base,"CompareObjectHandles"):NULL;
    for(unsigned mode=0;mode<2;mode++)if(!load_private_entry((void*)entries.release[mode])||!load_private_entry((void*)entries.releaseEx[mode])
            ||!load_private_entry((void*)entries.unmap[mode])||!load_private_entry((void*)entries.unmapEx[mode])){
        failure="HOST_MEMORY_RELEASE_ACTUAL_IMPORT_OR_GPA_NOT_CONTROLLED";goto done;
    }
    if(!same||!DuplicateHandle(GetCurrentProcess(),GetCurrentProcess(),GetCurrentProcess(),&actual,PROCESS_VM_OPERATION,FALSE,0)
            ||!same(GetCurrentProcess(),actual)){failure="HOST_MEMORY_RELEASE_VM_OPERATION_ONLY_CURRENT_ALIAS";goto done;}
    if(system.dwPageSize<64){failure="HOST_MEMORY_RELEASE_ACTUAL_PAGE_SIZE";goto done;}
    failure=load_memory_release_normal(&entries,actual,system.dwPageSize);if(failure)goto done;
    failure=load_memory_release_mapped(&entries,system.dwPageSize);if(failure)goto done;
    if(!controlPath){failure="HOST_MEMORY_RELEASE_ACTUAL_CONTROL_PATH";goto done;}
    jsize length=(*env)->GetStringLength(env,controlPath);path=calloc((size_t)length+1,sizeof(*path));
    if(!path){failure="HOST_MEMORY_RELEASE_CONTROL_PATH_ALLOCATION";goto done;}
    (*env)->GetStringRegion(env,controlPath,0,length,(jchar*)path);if((*env)->ExceptionCheck(env))goto done;
    control=GetModuleHandleW(path);free(path);path=NULL;
    void *code=control?(void*)GetProcAddress(control,"Java_dev_ronova_pro_bootstrap_NativeControl_abi"):NULL;
    LoadMemoryReleaseTarget codeTarget={0},vmTarget={0},thunkTarget={0};
    if(!code||!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,(LPCSTR)code,&observed)
            ||observed!=control||!load_memory_release_target(&codeTarget,code,1,MEM_IMAGE,control)){
        failure="HOST_MEMORY_RELEASE_EXACT_CONTROL_DLL_IMAGE";goto done;
    }
    if((*env)->GetJavaVM(env,&vm)!=JNI_OK||!vm||!*vm||!(*vm)->GetEnv
            ||!load_memory_release_target(&vmTarget,(void*)&(*vm)->GetEnv,sizeof((*vm)->GetEnv),MEM_PRIVATE,(void*)*vm)){
        failure="HOST_MEMORY_RELEASE_ACTUAL_VM_SHADOW_GETENV";goto done;
    }
    /* This is an actual, private GPA thunk for a different API; the requested
     * MEM_RELEASE base is its real allocation, not the export's interior byte. */
    if(!load_memory_release_target(&thunkTarget,(void*)entries.unmapEx[1],1,MEM_PRIVATE,NULL)){
        failure="HOST_MEMORY_RELEASE_ACTUAL_GPA_HOOK_THUNK";goto done;
    }
    failure=load_memory_release_refused(&entries,0,GetCurrentProcess(),0,vmTarget.region.AllocationBase,0,MEM_DECOMMIT,&vmTarget,"HOST_MEMORY_RELEASE_VM_SHADOW_ZERO_DECOMMIT");if(failure)goto done;
    failure=load_memory_release_refused(&entries,1,actual,1,vmTarget.region.AllocationBase,0,MEM_RELEASE,&vmTarget,"HOST_MEMORY_RELEASE_VM_SHADOW_WHOLE_RELEASE");if(failure)goto done;
    failure=load_memory_release_refused(&entries,1,GetCurrentProcess(),0,thunkTarget.region.AllocationBase,0,MEM_RELEASE,&thunkTarget,"HOST_MEMORY_RELEASE_THUNK_WHOLE_RELEASE");if(failure)goto done;
    failure=load_memory_release_refused(&entries,0,GetCurrentProcess(),1,thunkTarget.data.address,1,MEM_DECOMMIT,&thunkTarget,"HOST_MEMORY_RELEASE_THUNK_PAGE_DECOMMIT");if(failure)goto done;
    /* Only Unmap is requested for the exact loaded controller image. */
    failure=load_memory_unmap_refused(&entries,0,0,0,control,&codeTarget,"HOST_MEMORY_RELEASE_CONTROL_IMAGE_UNMAP");if(failure)goto done;
    failure=load_memory_unmap_refused(&entries,1,1,LOAD_MEMORY_UNMAP_TRANSIENT_BOOST,control,&codeTarget,"HOST_MEMORY_RELEASE_CONTROL_IMAGE_UNMAP_EX");
done:
    {
        DWORD error=GetLastError();free(path);
        if(actual&&!CloseHandle(actual)&&!failure){failure="HOST_MEMORY_RELEASE_CURRENT_ALIAS_CLOSE";error=GetLastError();}
        if(failure){fprintf(stderr,"HOST_MEMORY_RELEASE_FAILED stage=%s win32=%lu\n",failure,(unsigned long)error);load_thread_fail(env,failure);}
    }
}
typedef LPVOID (WINAPI *LoadHeapAllocate)(HANDLE,DWORD,SIZE_T);
typedef LPVOID (WINAPI *LoadHeapReallocate)(HANDLE,DWORD,LPVOID,SIZE_T);
typedef BOOL (WINAPI *LoadHeapRelease)(HANDLE,DWORD,LPVOID);
typedef BOOL (WINAPI *LoadHeapDestroy)(HANDLE);
typedef LPVOID (WINAPI *LoadVirtualAllocate)(LPVOID,SIZE_T,DWORD,DWORD);
typedef LPVOID (WINAPI *LoadVirtualAllocateEx)(HANDLE,LPVOID,SIZE_T,DWORD,DWORD);
extern LoadHeapAllocate __imp_HeapAlloc;
extern LoadHeapReallocate __imp_HeapReAlloc;
extern LoadHeapRelease __imp_HeapFree;
extern LoadVirtualAllocate __imp_VirtualAlloc;
static int load_win32_allocation_check(JNIEnv *env){
    HMODULE kernel=GetModuleHandleW(L"kernel32.dll");
    LoadHeapAllocate allocate=kernel?(LoadHeapAllocate)GetProcAddress(kernel,"HeapAlloc"):NULL;
    LoadHeapDestroy destroy=kernel?(LoadHeapDestroy)GetProcAddress(kernel,"HeapDestroy"):NULL;
    LoadVirtualAllocateEx allocateEx=kernel?(LoadVirtualAllocateEx)GetProcAddress(kernel,"VirtualAllocEx"):NULL;
    LoadMemoryFreeEx releaseEx=kernel?(LoadMemoryFreeEx)GetProcAddress(kernel,"VirtualFreeEx"):NULL;
    void *actuals[]={(void*)__imp_HeapAlloc,(void*)__imp_HeapReAlloc,(void*)__imp_HeapFree,
            (void*)__imp_VirtualAlloc,(void*)allocate,(void*)destroy,(void*)allocateEx,(void*)releaseEx};
    for(unsigned i=0;i<sizeof(actuals)/sizeof(actuals[0]);i++)
        if(!load_private_entry(actuals[i]))return load_thread_fail(env,"HOST_ALLOCATION_ACTUAL_IMPORT_OR_RESULT_UNCONTROLLED");
    HANDLE heap=NULL,process=NULL;BYTE *block=NULL,*zero=NULL,*reservation=NULL,*extended=NULL;
    const char *failure=NULL;DWORD saved=0;SYSTEM_INFO system;GetSystemInfo(&system);
    heap=HeapCreate(0,0,0);if(!heap)return load_thread_fail(env,"HOST_ALLOCATION_REAL_HEAP_CREATE");
    __try {
        SetLastError(0x524e0061);block=HeapAlloc(heap,HEAP_ZERO_MEMORY,48);saved=GetLastError();
        if(!block||saved!=0x524e0061){failure="HOST_ALLOCATION_HEAP_ORIGINAL_RESULT_ERROR";__leave;}
        for(unsigned i=0;i<48;i++)if(block[i]){failure="HOST_ALLOCATION_ZERO_MEMORY_FLAG_LOST";break;}
        if(failure)__leave;block[0]=0xa5;block[23]=0x5a;block[47]=0x6c;
        SetLastError(0x524e0062);void *failed=HeapReAlloc(heap,0,block,SIZE_MAX);saved=GetLastError();
        if(failed){block=failed;failure="HOST_ALLOCATION_HUGE_REALLOC_UNEXPECTED_SUCCESS";__leave;}
        /* HeapReAlloc does not promise a GetLastError value on failure. Its
         * actual NULL and unchanged old allocation are the usable contract. */
        if(block[0]!=0xa5||block[23]!=0x5a||block[47]!=0x6c){
            failure="HOST_ALLOCATION_FAILED_REALLOC_CHANGED_OLD_BLOCK";__leave;
        }
        SetLastError(0x524e0063);void *shrunk=HeapReAlloc(heap,HEAP_REALLOC_IN_PLACE_ONLY,block,24);saved=GetLastError();
        if(shrunk!=block){if(shrunk)block=shrunk;failure="HOST_ALLOCATION_IN_PLACE_ONLY_ORIGINAL_RESULT";__leave;}
        if(saved!=0x524e0063||block[0]!=0xa5||block[23]!=0x5a){
            failure="HOST_ALLOCATION_SAME_ADDRESS_SHRINK_OR_FLAGS_CHANGED";__leave;
        }
        if(!HeapFree(heap,0,block)){failure="HOST_ALLOCATION_HEAP_REAL_FREE";__leave;}block=NULL;
        zero=allocate(heap,0,0);if(!zero){failure="HOST_ALLOCATION_ZERO_BYTE_REAL_RESULT";__leave;}
        if(!HeapFree(heap,0,zero)){failure="HOST_ALLOCATION_ZERO_BYTE_REAL_FREE";__leave;}zero=NULL;
        DWORD exception=0;void *unexpected=NULL;
        __try {unexpected=HeapAlloc(heap,HEAP_GENERATE_EXCEPTIONS,SIZE_MAX);}
        __except(EXCEPTION_EXECUTE_HANDLER){exception=GetExceptionCode();}
        if(unexpected){HeapFree(heap,0,unexpected);failure="HOST_ALLOCATION_EXCEPTION_REQUEST_RETURNED_POINTER";__leave;}
        if(exception!=0xc0000017UL){failure="HOST_ALLOCATION_ORIGINAL_NO_MEMORY_EXCEPTION_CHANGED";__leave;}
        block=allocate(heap,0,8);if(!block){failure="HOST_ALLOCATION_EXCEPTION_LEFT_CONTEXT_BUSY";__leave;}
        /* Destroy the actual heap with one live observed block. No attempt is
         * made to discover other heaps or dereference the retired pointer. */
        if(!destroy(heap)){failure="HOST_ALLOCATION_REAL_HEAP_DESTROY";__leave;}heap=NULL;block=NULL;
        if(!system.dwPageSize){failure="HOST_ALLOCATION_SYSTEM_PAGE_SIZE";__leave;}
        reservation=VirtualAlloc(NULL,(SIZE_T)system.dwPageSize*2,MEM_RESERVE|MEM_COMMIT,PAGE_READWRITE);
        if(!reservation||!load_memory_release_state(reservation,MEM_COMMIT,reservation)){
            failure="HOST_ALLOCATION_REAL_VIRTUAL_RETURN_GEOMETRY";__leave;
        }
        reservation[1]=0x7d;
        if(!VirtualFree(reservation,system.dwPageSize,MEM_DECOMMIT)
                ||!load_memory_release_state(reservation,MEM_RESERVE,reservation)){
            failure="HOST_ALLOCATION_REAL_DECOMMIT_RESERVATION";__leave;
        }
        if(VirtualAlloc(reservation,system.dwPageSize,MEM_COMMIT,PAGE_READWRITE)!=reservation||reservation[1]!=0){
            failure="HOST_ALLOCATION_ORIGINAL_RECOMMIT_RETURN_ZERO";__leave;
        }
        if(!DuplicateHandle(GetCurrentProcess(),GetCurrentProcess(),GetCurrentProcess(),&process,PROCESS_VM_OPERATION,FALSE,0)){
            failure="HOST_ALLOCATION_CURRENT_VM_OPERATION_ALIAS";__leave;
        }
        extended=allocateEx(process,NULL,system.dwPageSize,MEM_RESERVE|MEM_COMMIT,PAGE_READWRITE);
        if(!extended||!load_memory_release_state(extended,MEM_COMMIT,extended)){
            failure="HOST_ALLOCATION_CURRENT_EX_ORIGINAL_RESULT";__leave;
        }
        extended[0]=0x39;
        if(!releaseEx(process,extended,0,MEM_RELEASE)){failure="HOST_ALLOCATION_CURRENT_EX_REAL_FREE";__leave;}
        extended=NULL;
        if(!VirtualFree(reservation,0,MEM_RELEASE)){failure="HOST_ALLOCATION_REAL_RESERVATION_RELEASE";__leave;}reservation=NULL;
    } __finally {
        if(extended)releaseEx(process,extended,0,MEM_RELEASE);
        if(reservation)VirtualFree(reservation,0,MEM_RELEASE);
        if(process)CloseHandle(process);
        if(zero)HeapFree(heap,0,zero);
        if(block)HeapFree(heap,0,block);
        if(heap)destroy(heap);
    }
    if(failure){fprintf(stderr,"HOST_ALLOCATION_FAILED stage=%s win32=%lu\n",failure,(unsigned long)saved);return load_thread_fail(env,failure);}
    fprintf(stderr,"HOST_ALLOCATION_ACTUAL heap=IAT/dynamic realloc=failure/same-address exception=original virtual=current-process/commit/release\n");
    return 1;
}
extern DWORD (WINAPI *__imp_TlsAlloc)(void);
extern BOOL (WINAPI *__imp_TlsSetValue)(DWORD,LPVOID);
extern LPVOID (WINAPI *__imp_TlsGetValue)(DWORD);
extern BOOL (WINAPI *__imp_TlsFree)(DWORD);
extern DWORD (WINAPI *__imp_FlsAlloc)(PFLS_CALLBACK_FUNCTION);
extern BOOL (WINAPI *__imp_FlsSetValue)(DWORD,PVOID);
extern PVOID (WINAPI *__imp_FlsGetValue)(DWORD);
extern BOOL (WINAPI *__imp_FlsFree)(DWORD);
extern HANDLE (WINAPI *__imp_HeapCreate)(DWORD,SIZE_T,SIZE_T);
typedef struct LoadTlsPrivateCase {
    DWORD tls,fls;void *parentFiber;volatile LONG callbacks,error;int threadValue,fiberValue;
} LoadTlsPrivateCase;
typedef struct LoadTlsPrivateValue {LoadTlsPrivateCase *test;int marker;} LoadTlsPrivateValue;
static VOID WINAPI load_tls_private_callback(PVOID data){
    LoadTlsPrivateValue *value=(LoadTlsPrivateValue*)data;
    if(!value||!value->test)return;
    if(value->marker!=71&&value->marker!=93)InterlockedOr(&value->test->error,1);
    InterlockedIncrement(&value->test->callbacks);
}
static VOID WINAPI load_tls_private_fiber(PVOID data){
    LoadTlsPrivateValue *value=(LoadTlsPrivateValue*)data;LoadTlsPrivateCase *test=value->test;
    if(FlsGetValue(test->fls)!=NULL||!FlsSetValue(test->fls,value)||FlsGetValue(test->fls)!=value)
        InterlockedOr(&test->error,2);
    test->fiberValue=1;SwitchToFiber(test->parentFiber);
    InterlockedOr(&test->error,4);SwitchToFiber(test->parentFiber);
}
static DWORD WINAPI load_tls_private_thread(PVOID data){
    LoadTlsPrivateCase *test=(LoadTlsPrivateCase*)data;
    LoadTlsPrivateValue local={test,71},fiberLocal={test,93};void *other=NULL;
    SetLastError(0x524e0100);
    if(TlsGetValue(test->tls)!=NULL||GetLastError()!=ERROR_SUCCESS
            ||!TlsSetValue(test->tls,&local)||TlsGetValue(test->tls)!=&local)InterlockedOr(&test->error,8);
    test->threadValue=1;test->parentFiber=ConvertThreadToFiber(NULL);
    if(!test->parentFiber){InterlockedOr(&test->error,16);return 1;}
    if(FlsGetValue(test->fls)!=NULL||!FlsSetValue(test->fls,&local))InterlockedOr(&test->error,32);
    other=CreateFiber(0,load_tls_private_fiber,&fiberLocal);
    if(other){
        SwitchToFiber(other);
        if(!test->fiberValue||FlsGetValue(test->fls)!=&local)InterlockedOr(&test->error,64);
        DeleteFiber(other);
        if(test->callbacks!=1)InterlockedOr(&test->error,128);
    }else InterlockedOr(&test->error,256);
    if(!FlsFree(test->fls)||test->callbacks!=2)InterlockedOr(&test->error,512);
    test->fls=FLS_OUT_OF_INDEXES;
    if(!TlsSetValue(test->tls,NULL)||!ConvertFiberToThread())InterlockedOr(&test->error,1024);
    return test->error?1:0;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_AuditFixCheck_hostTlsPrivate0(JNIEnv *env,jclass check){
    (void)check;const char *failure=NULL;DWORD incoming=GetLastError();
    LoadTlsPrivateCase *test=(LoadTlsPrivateCase*)HeapAlloc(GetProcessHeap(),HEAP_ZERO_MEMORY,sizeof(*test));
    if(!test)return load_thread_fail(env,"HOST_TLS_FIXTURE_STORAGE");
    test->tls=TLS_OUT_OF_INDEXES;test->fls=FLS_OUT_OF_INDEXES;HANDLE thread=NULL;int running=0;
    void *imports[]={(void*)__imp_HeapCreate,(void*)__imp_TlsAlloc,(void*)__imp_TlsSetValue,(void*)__imp_TlsGetValue,
        (void*)__imp_TlsFree,(void*)__imp_FlsAlloc,(void*)__imp_FlsSetValue,(void*)__imp_FlsGetValue,(void*)__imp_FlsFree};
    for(unsigned i=0;i<sizeof(imports)/sizeof(imports[0]);i++)if(!load_private_entry(imports[i])){
        failure="HOST_POOL_TLS_FLS_ACTUAL_IAT_ENTRY";goto tls_done;}
    HMODULE kernel=GetModuleHandleW(L"kernel32.dll");
    void *(WINAPI *get2)(DWORD)=kernel?(void *(WINAPI*)(DWORD))GetProcAddress(kernel,"TlsGetValue2"):NULL;
    if(get2&&!load_private_entry((void*)get2)){failure="HOST_TLS_GET2_ACTUAL_GPA_ENTRY";goto tls_done;}
    test->tls=TlsAlloc();test->fls=FlsAlloc(load_tls_private_callback);
    if(test->tls==TLS_OUT_OF_INDEXES||test->fls==FLS_OUT_OF_INDEXES){failure="HOST_TLS_FLS_ACTUAL_SLOT_ALLOCATION";goto tls_done;}
    if(!TlsSetValue(test->tls,test)){failure="HOST_TLS_ACTUAL_SET";goto tls_done;}
    SetLastError(0x524e0100);
    if(TlsGetValue(test->tls)!=test||GetLastError()!=ERROR_SUCCESS){failure="HOST_TLS_NON_NULL_ORIGINAL_RESULT_ERROR";goto tls_done;}
    if(get2){SetLastError(0x524e0100);if(get2(test->tls)!=test||GetLastError()!=0x524e0100){failure="HOST_TLS_GET2_LAST_ERROR";goto tls_done;}}
    thread=CreateThread(NULL,0,load_tls_private_thread,test,0,NULL);
    if(!thread||WaitForSingleObject(thread,10000)!=WAIT_OBJECT_0){
        /* An uncompleted worker keeps its heap payload/slots. Report this
         * failure without freeing its storage or forcing thread termination. */
        failure="HOST_TLS_ACTUAL_THREAD_COMPLETION";
        running=thread!=NULL;goto tls_done;
    }
    if(test->error||!test->threadValue||!test->fiberValue||test->callbacks!=2){
        fprintf(stderr,"HOST_TLS_FLS_FAILED flags=%ld callbacks=%ld\n",test->error,test->callbacks);
        failure="HOST_TLS_THREAD_FIBER_ORIGINAL_VALUE_CALLBACK";goto tls_done;}
    if(TlsGetValue(test->tls)!=test||!TlsSetValue(test->tls,NULL)){failure="HOST_TLS_THREAD_VALUE_ISOLATION";goto tls_done;}
    SetLastError(0x524e0100);
    if(TlsGetValue(test->tls)!=NULL||GetLastError()!=ERROR_SUCCESS){failure="HOST_TLS_NULL_IS_SUCCESS";goto tls_done;}
    if(get2){SetLastError(0x524e0100);if(get2(test->tls)!=NULL||GetLastError()!=0x524e0100){failure="HOST_TLS_GET2_NULL_ERROR";goto tls_done;}}
tls_done:
    if(thread)CloseHandle(thread);
    if(!running){
        if(test->fls!=FLS_OUT_OF_INDEXES)FlsFree(test->fls);
        if(test->tls!=TLS_OUT_OF_INDEXES)TlsFree(test->tls);
        HeapFree(GetProcessHeap(),0,test);
    }
    SetLastError(incoming);
    if(failure){load_thread_fail(env,failure);return JNI_FALSE;}
    fprintf(stdout,"HOST_TLS_FLS_PASS actual_IAT separate_kernel_threads actual_fiber_switch_DeleteFiber callback_once_per_nonnull_FLS FlsFree NULL_success TlsGetValue2=%s no_Core_producer_authority\n",get2?"actual_GPA_error_preserved":"not_exported");
    return JNI_TRUE;
}
/* Ordinary-source original ABI check. Producer-only tracker gates are covered
 * separately by AuditFixNative; this does not fabricate Core permission. */
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_AuditFixCheck_hostArenaAllocations0(JNIEnv *env,jclass check){
    (void)check;HMODULE module=GetModuleHandleW(L"jemalloc.dll");
    if(!module)return load_thread_fail(env,"HOST_ARENA_ACTUAL_LWJGL_MODULE");
    const char *names[]={"je_malloc","je_calloc","je_realloc","je_free","je_mallocx","je_rallocx","je_xallocx",
        "je_dallocx","je_sdallocx","je_sallocx","je_malloc_usable_size","je_aligned_alloc","je_posix_memalign",
        "je_mallctl","je_mallctlbymib","je_mallctlnametomib"};
    void *functions[16];for(unsigned i=0;i<16;i++){
        functions[i]=(void*)GetProcAddress(module,names[i]);
        if(!load_private_entry(functions[i]))return load_thread_fail(env,"HOST_ARENA_EXACT_NAMED_GPA");
    }
    void *(__cdecl *allocate)(size_t)=(void*)functions[0];
    void *(__cdecl *allocateZero)(size_t,size_t)=(void*)functions[1];
    void *(__cdecl *resize)(void*,size_t)=(void*)functions[2];
    void (__cdecl *release)(void*)=(void*)functions[3];
    void *(__cdecl *allocateX)(size_t,int)=(void*)functions[4];
    void *(__cdecl *resizeX)(void*,size_t,int)=(void*)functions[5];
    size_t (__cdecl *expandX)(void*,size_t,size_t,int)=(void*)functions[6];
    void (__cdecl *releaseX)(void*,int)=(void*)functions[7];
    void (__cdecl *sizedRelease)(void*,size_t,int)=(void*)functions[8];
    size_t (__cdecl *sizeX)(const void*,int)=(void*)functions[9];
    size_t (__cdecl *usable)(const void*)=(void*)functions[10];
    void *(__cdecl *aligned)(size_t,size_t)=(void*)functions[11];
    int (__cdecl *posix)(void**,size_t,size_t)=(void*)functions[12];
    int (__cdecl *control)(const char*,void*,size_t*,void*,size_t)=(void*)functions[13];
    int (__cdecl *byMib)(const size_t*,size_t,void*,size_t*,void*,size_t)=(void*)functions[14];
    int (__cdecl *toMib)(const char*,size_t*,size_t*)=(void*)functions[15];
    unsigned arena=0;size_t length=sizeof(arena);char command[80];
    fprintf(stderr,"HOST_ARENA_STAGE real_arenas_create\n");fflush(stderr);
    if(control("arenas.create",&arena,&length,NULL,0)||length!=sizeof(arena)||arena>=2047)
        return load_thread_fail(env,"HOST_ARENA_REAL_CREATE");
    int flags=(int)((arena+1u)<<20)|0x100; /* MALLOCX_ARENA / TCACHE_NONE */
    fprintf(stderr,"HOST_ARENA_STAGE real_suballocations arena=%u\n",arena);fflush(stderr);
    unsigned char *block=allocateX(128,flags);
    if(!block||sizeX(block,0)<128||usable(block)<128)return load_thread_fail(env,"HOST_ARENA_REAL_EXTENT");
    block[7]=0x5c;
    if(resize(block,SIZE_MAX)!=NULL||block[7]!=0x5c)return load_thread_fail(env,"HOST_ARENA_FAILED_REALLOC_OLD_BYTES");
    unsigned char *grown=resizeX(block,512,flags);
    if(!grown||grown[7]!=0x5c)return load_thread_fail(env,"HOST_ARENA_REAL_RALLOCX");
    block=grown;if(expandX(block,512,0,flags)<512)return load_thread_fail(env,"HOST_ARENA_REAL_XALLOCX");
    unsigned found=UINT_MAX;length=sizeof(found);void *address=block;
    if(control("arenas.lookup",&found,&length,&address,sizeof(address))||found!=arena)
        return load_thread_fail(env,"HOST_ARENA_ACTUAL_RETURNED_ARENA");
    releaseX(block,flags);block=allocateX(64,flags);
    if(!block)return load_thread_fail(env,"HOST_ARENA_REAL_SDALLOCX_STORAGE");
    sizedRelease(block,64,flags);
    block=allocateX(64,flags);if(!block)return load_thread_fail(env,"HOST_ARENA_REAL_RESET_STORAGE");
    snprintf(command,sizeof(command),"arena.%u.reset",arena);
    fprintf(stderr,"HOST_ARENA_STAGE real_reset\n");fflush(stderr);
    if(control(command,NULL,NULL,NULL,0))return load_thread_fail(env,"HOST_ARENA_REAL_RESET");
    block=allocateX(64,flags);if(!block)return load_thread_fail(env,"HOST_ARENA_REAL_POST_RESET");releaseX(block,flags);
    snprintf(command,sizeof(command),"arena.%u.destroy",arena);size_t mib[8],mibLength=8;
    fprintf(stderr,"HOST_ARENA_STAGE real_byMib_destroy\n");fflush(stderr);
    if(toMib(command,mib,&mibLength)||byMib(mib,mibLength,NULL,NULL,NULL,0))
        return load_thread_fail(env,"HOST_ARENA_SIX_ARGUMENT_BYMIB_DESTROY");
    block=allocateZero(4,8);if(!block)return load_thread_fail(env,"HOST_ARENA_CALLOC");
    for(unsigned i=0;i<32;i++)if(block[i])return load_thread_fail(env,"HOST_ARENA_CALLOC_ORIGINAL_ZERO");release(block);
    block=allocate(16);if(!block)return load_thread_fail(env,"HOST_ARENA_MALLOC");release(block);release(NULL);
    block=aligned(64,128);if(!block||(uintptr_t)block%64)return load_thread_fail(env,"HOST_ARENA_ALIGNED_ALLOC");release(block);
    void *result=NULL;if(posix(&result,64,128)||!result||(uintptr_t)result%64)return load_thread_fail(env,"HOST_ARENA_POSIX_MEMALIGN");release(result);
    fprintf(stdout,"HOST_ARENA_PASS actual_LWJGL_jemalloc_16_named_GPA malloc_calloc_realloc_failure rallocx_xallocx_extent actual_arena_lookup reset_six_argument_byMib_destroy paired_free no_Core_producer_authority\n");fflush(stdout);
    return JNI_TRUE;
}
/* Local provider files are explicit inputs. Altered search path resolves only
 * their existing sibling runtime dependencies; no process PATH is changed. */
static HMODULE load_allocator_provider(JNIEnv *env,jstring providerPath){
    if(!providerPath)return NULL;jsize length=(*env)->GetStringLength(env,providerPath);
    const jchar *text=(*env)->GetStringChars(env,providerPath,NULL);if(!text)return NULL;
    wchar_t *path=(wchar_t*)calloc((size_t)length+1,sizeof(wchar_t));
    if(path)for(jsize index=0;index<length;index++)path[index]=(wchar_t)text[index];
    (*env)->ReleaseStringChars(env,providerPath,text);if(!path)return NULL;
    HMODULE module=LoadLibraryExW(path,NULL,LOAD_WITH_ALTERED_SEARCH_PATH);free(path);return module;
}
/* Actual ordinary calls against fixed upstream mimalloc v2.1.7. This fixture
 * owns every returned allocation/heap. Producer-only alias/typed gates remain
 * the separate production-tracker seam; no Core identity is manufactured. */
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_AuditFixCheck_hostMimalloc0(JNIEnv *env,jclass check,jstring providerPath){
    (void)check;HMODULE module=load_allocator_provider(env,providerPath);
    if(!module)return load_thread_fail(env,"HOST_MIMALLOC_EXPLICIT_PROVIDER_LOAD");
    const char *names[]={"mi_malloc","mi_calloc","mi_realloc","mi_free","mi_usable_size","mi_malloc_aligned","mi_realloc_aligned",
        "mi_heap_new","mi_heap_delete","mi_heap_destroy","mi_heap_malloc","mi_heap_calloc","mi_heap_realloc","mi_heap_malloc_aligned",
        "mi_heap_realloc_aligned","mi_heap_get_default","mi_heap_get_backing","mi_heap_set_default","mi_collect","mi_heap_collect",
        "mi_heap_new_in_arena","mi_zalloc","mi_heap_zalloc","mi_thread_done"};
    void *functions[24]={0};const char *failure=NULL;void *heap=NULL,*savedDefault=NULL;unsigned char *block=NULL;int defaultChanged=0;
    for(unsigned index=0;index<24;index++){
        functions[index]=(void*)GetProcAddress(module,names[index]);
        if(!load_private_entry(functions[index])){failure="HOST_MIMALLOC_24_EXACT_NAMED_GPA";goto done;}
    }
    void *(__cdecl *allocate)(size_t)=(void*)functions[0];
    void *(__cdecl *allocateZero)(size_t,size_t)=(void*)functions[1];
    void *(__cdecl *resize)(void*,size_t)=(void*)functions[2];
    void (__cdecl *release)(void*)=(void*)functions[3];
    size_t (__cdecl *usable)(const void*)=(void*)functions[4];
    void *(__cdecl *aligned)(size_t,size_t)=(void*)functions[5];
    void *(__cdecl *resizeAligned)(void*,size_t,size_t)=(void*)functions[6];
    void *(__cdecl *newHeap)(void)=(void*)functions[7];
    void (__cdecl *deleteHeap)(void*)=(void*)functions[8];
    void (__cdecl *destroyHeap)(void*)=(void*)functions[9];
    void *(__cdecl *heapAllocate)(void*,size_t)=(void*)functions[10];
    void *(__cdecl *heapZero)(void*,size_t,size_t)=(void*)functions[11];
    void *(__cdecl *heapResize)(void*,void*,size_t)=(void*)functions[12];
    void *(__cdecl *heapAligned)(void*,size_t,size_t)=(void*)functions[13];
    void *(__cdecl *heapResizeAligned)(void*,void*,size_t,size_t)=(void*)functions[14];
    void *(__cdecl *getDefault)(void)=(void*)functions[15];
    void *(__cdecl *getBacking)(void)=(void*)functions[16];
    void *(__cdecl *setDefault)(void*)=(void*)functions[17];
    void (__cdecl *collect)(_Bool)=(void*)functions[18];
    void (__cdecl *collectHeap)(void*,_Bool)=(void*)functions[19];
    void *(__cdecl *newInArena)(int)=(void*)functions[20];
    void *(__cdecl *zalloc)(size_t)=(void*)functions[21];
    void *(__cdecl *heapZalloc)(void*,size_t)=(void*)functions[22];
    void (__cdecl *threadDone)(void)=(void*)functions[23];
    fprintf(stderr,"HOST_MIMALLOC_STAGE real_global_allocations\n");fflush(stderr);
    block=allocate(64);if(!block||usable(block)<64){failure="HOST_MIMALLOC_REAL_EXTENT";goto done;}block[7]=0x5c;
    void *changed=resize(block,SIZE_MAX);
    if(changed){block=changed;failure="HOST_MIMALLOC_IMPOSSIBLE_REALLOC_RETURN";goto done;}
    if(block[7]!=0x5c){failure="HOST_MIMALLOC_FAILED_REALLOC_OLD_BYTES";goto done;}
    changed=resize(block,128);if(!changed){failure="HOST_MIMALLOC_REAL_REALLOC";goto done;}block=changed;
    if(block[7]!=0x5c){failure="HOST_MIMALLOC_REAL_REALLOC_BYTES";goto done;}release(block);block=NULL;
    block=allocateZero(4,16);if(!block){failure="HOST_MIMALLOC_REAL_CALLOC";goto done;}
    for(unsigned index=0;index<64;index++)if(block[index]){failure="HOST_MIMALLOC_REAL_CALLOC_ZERO";goto done;}release(block);block=NULL;
    block=zalloc(32);if(!block){failure="HOST_MIMALLOC_REAL_ZALLOC";goto done;}
    for(unsigned index=0;index<32;index++)if(block[index]){failure="HOST_MIMALLOC_REAL_ZALLOC_ZERO";goto done;}release(block);block=NULL;
    block=aligned(64,64);if(!block||(uintptr_t)block%64){failure="HOST_MIMALLOC_REAL_ALIGNED";goto done;}block[7]=0x3d;
    changed=resizeAligned(block,128,64);if(!changed){failure="HOST_MIMALLOC_REAL_ALIGNED_REALLOC";goto done;}block=changed;
    if((uintptr_t)block%64||block[7]!=0x3d){failure="HOST_MIMALLOC_ALIGNED_REALLOC_BYTES";goto done;}release(block);block=NULL;
    savedDefault=getDefault();if(!savedDefault||!getBacking()){failure="HOST_MIMALLOC_ACTUAL_DEFAULT_BACKING";goto done;}
    heap=newHeap();if(!heap){failure="HOST_MIMALLOC_REAL_HEAP_NEW";goto done;}
    if(setDefault(heap)!=savedDefault){defaultChanged=1;failure="HOST_MIMALLOC_SET_DEFAULT_RETURN";goto done;}defaultChanged=1;
    block=allocate(64);if(!block||getDefault()!=heap){failure="HOST_MIMALLOC_DEFAULT_HEAP_ALLOCATION";goto done;}block[7]=0x71;
    if(setDefault(savedDefault)!=heap){failure="HOST_MIMALLOC_RESTORE_DEFAULT_RETURN";goto done;}defaultChanged=0;
    fprintf(stderr,"HOST_MIMALLOC_STAGE real_heap_delete_migration\n");fflush(stderr);
    deleteHeap(heap);heap=NULL;
    if(block[7]!=0x71||usable(block)<64){failure="HOST_MIMALLOC_HEAP_DELETE_DID_NOT_PRESERVE_BLOCK";goto done;}release(block);block=NULL;
    heap=newInArena(0);if(!heap){failure="HOST_MIMALLOC_REAL_HEAP_NONE_ARENA";goto done;}
    block=heapZero(heap,4,16);if(!block){failure="HOST_MIMALLOC_REAL_HEAP_CALLOC";goto done;}
    for(unsigned index=0;index<64;index++)if(block[index]){failure="HOST_MIMALLOC_REAL_HEAP_CALLOC_ZERO";goto done;}
    changed=heapResize(heap,block,128);if(!changed){failure="HOST_MIMALLOC_REAL_HEAP_REALLOC";goto done;}block=changed;release(block);block=NULL;
    block=heapZalloc(heap,32);if(!block){failure="HOST_MIMALLOC_REAL_HEAP_ZALLOC";goto done;}
    for(unsigned index=0;index<32;index++)if(block[index]){failure="HOST_MIMALLOC_REAL_HEAP_ZALLOC_ZERO";goto done;}release(block);block=NULL;
    block=heapAligned(heap,64,64);if(!block||(uintptr_t)block%64){failure="HOST_MIMALLOC_REAL_HEAP_ALIGNED";goto done;}block[7]=0x43;
    changed=heapResizeAligned(heap,block,128,64);if(!changed){failure="HOST_MIMALLOC_REAL_HEAP_ALIGNED_REALLOC";goto done;}block=changed;
    if((uintptr_t)block%64||block[7]!=0x43){failure="HOST_MIMALLOC_HEAP_ALIGNED_REALLOC_BYTES";goto done;}release(block);block=NULL;
    block=heapAllocate(heap,64);if(!block){failure="HOST_MIMALLOC_REAL_HEAP_DESTROY_STORAGE";goto done;}
    collectHeap(heap,1);fprintf(stderr,"HOST_MIMALLOC_STAGE real_heap_destroy\n");fflush(stderr);
    destroyHeap(heap);heap=NULL;block=NULL;collect(1);release(NULL);threadDone();
done:
    if(defaultChanged&&savedDefault&&functions[17])((void *(__cdecl*)(void*))functions[17])(savedDefault);
    if(block&&functions[3])((void (__cdecl*)(void*))functions[3])(block);
    if(heap&&functions[9])((void (__cdecl*)(void*))functions[9])(heap);
    if(!FreeLibrary(module)&&!failure)failure="HOST_MIMALLOC_APPLICATION_REFERENCE_RETURN";
    if(failure)return load_thread_fail(env,failure);
    fprintf(stdout,"HOST_MIMALLOC_PASS actual_fixed_upstream_v2_1_7_24_named_GPA real_malloc_calloc_zalloc_realloc_failure aligned_heap_returns heap_delete_preserves_live_blocks heap_destroy collect_thread_done paired_free no_Core_producer_authority\n");fflush(stdout);return JNI_TRUE;
}
static void *load_gnu_allocate(void **functions,unsigned kind,const void *nothrow){
    if(kind<2)return ((void *(__cdecl*)(size_t))functions[kind])(128);
    if(kind<4)return ((void *(__cdecl*)(size_t,const void*))functions[kind])(128,nothrow);
    if(kind<6)return ((void *(__cdecl*)(size_t,size_t))functions[kind])(128,64);
    return ((void *(__cdecl*)(size_t,size_t,const void*))functions[kind])(128,64,nothrow);
}
static void load_gnu_delete(void **functions,unsigned kind,void *pointer,const void *nothrow){
    if(kind<10)((void (__cdecl*)(void*))functions[kind])(pointer);
    else if(kind<12)((void (__cdecl*)(void*,const void*))functions[kind])(pointer,nothrow);
    else if(kind<14)((void (__cdecl*)(void*,size_t))functions[kind])(pointer,128);
    else if(kind<16)((void (__cdecl*)(void*,size_t))functions[kind])(pointer,64);
    else if(kind<18)((void (__cdecl*)(void*,size_t,const void*))functions[kind])(pointer,64,nothrow);
    else ((void (__cdecl*)(void*,size_t,size_t))functions[kind])(pointer,128,64);
}
/* Twenty exact Windows GNU Itanium ABI exports from an installed shared
 * libstdc++, followed by owned classes compiled against that same DLL. */
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_AuditFixCheck_hostGnuCpp0(JNIEnv *env,jclass check,jstring providerPath,jstring classPath){
    (void)check;HMODULE provider=load_allocator_provider(env,providerPath),classes=NULL;const char *failure=NULL;
    if(!provider)return load_thread_fail(env,"HOST_GNU_CPP_EXPLICIT_PROVIDER_LOAD");
    const char *names[]={"_Znwy","_Znay","_ZnwyRKSt9nothrow_t","_ZnayRKSt9nothrow_t",
        "_ZnwySt11align_val_t","_ZnaySt11align_val_t","_ZnwySt11align_val_tRKSt9nothrow_t","_ZnaySt11align_val_tRKSt9nothrow_t",
        "_ZdlPv","_ZdaPv","_ZdlPvRKSt9nothrow_t","_ZdaPvRKSt9nothrow_t","_ZdlPvy","_ZdaPvy",
        "_ZdlPvSt11align_val_t","_ZdaPvSt11align_val_t","_ZdlPvSt11align_val_tRKSt9nothrow_t","_ZdaPvSt11align_val_tRKSt9nothrow_t",
        "_ZdlPvySt11align_val_t","_ZdaPvySt11align_val_t"};
    void *functions[20]={0};const void *nothrow=NULL;unsigned allocationKinds[]={0,1,2,3,0,1,4,5,6,7,4,5};
    for(unsigned index=0;index<20;index++){
        functions[index]=(void*)GetProcAddress(provider,names[index]);
        if(!load_private_entry(functions[index])){failure="HOST_GNU_CPP_20_EXACT_NAMED_GPA";goto done;}
    }
    nothrow=(const void*)GetProcAddress(provider,"_ZSt7nothrow");
    if(!nothrow){failure="HOST_GNU_CPP_ACTUAL_NOTHROW_OBJECT";goto done;}
    fprintf(stderr,"HOST_GNU_CPP_STAGE real_twenty_named_ABIs\n");fflush(stderr);
    for(unsigned pair=0;pair<12;pair++){
        unsigned char *block=NULL;DWORD exception=0;
        __try {
            block=(unsigned char*)load_gnu_allocate(functions,allocationKinds[pair],nothrow);
            if(!block)failure="HOST_GNU_CPP_ORIGINAL_SMALL_NEW_NULL";
            else if(pair>=6&&(uintptr_t)block%64)failure="HOST_GNU_CPP_ORIGINAL_ALIGNED_NEW";
            if(!failure)for(unsigned index=0;index<128;index++)block[index]=(unsigned char)(index^0xa6);
            if(!failure)for(unsigned index=0;index<128;index++)if(block[index]!=(unsigned char)(index^0xa6)){failure="HOST_GNU_CPP_ORIGINAL_NEW_BYTES";break;}
            if(block){void *retiring=block;block=NULL;load_gnu_delete(functions,pair+8,retiring,nothrow);}
            if(!failure)load_gnu_delete(functions,pair+8,NULL,nothrow);
        } __except(EXCEPTION_EXECUTE_HANDLER){exception=GetExceptionCode();failure="HOST_GNU_CPP_REAL_ABI_NATIVE_EXCEPTION";}
        if(block){void *retiring=block;block=NULL;load_gnu_delete(functions,pair+8,retiring,nothrow);}
        if(failure){fprintf(stderr,"HOST_GNU_CPP_FAILED pair=%u exception=%08lx\n",pair,(unsigned long)exception);goto done;}
    }
    classes=load_allocator_provider(env,classPath);if(!classes){failure="HOST_GNU_CPP_CLASS_DLL_LOAD";goto done;}
    void *(__cdecl *create)(unsigned)=(void*)GetProcAddress(classes,"audit_gnu_class_create");
    uint64_t (__cdecl *value)(void*,unsigned)=(void*)GetProcAddress(classes,"audit_gnu_class_value");
    void (__cdecl *destroy)(void*,unsigned)=(void*)GetProcAddress(classes,"audit_gnu_class_destroy");
    void (__cdecl *counts)(uint64_t*,uint64_t*)=(void*)GetProcAddress(classes,"audit_gnu_class_counts");
    if(!create||!value||!destroy||!counts){failure="HOST_GNU_CPP_REAL_CLASS_EXPORTS";goto done;}
    fprintf(stderr,"HOST_GNU_CPP_STAGE real_Cpp23_class_expressions\n");fflush(stderr);
    unsigned expectedCounts[]={1,3,1,2};uint64_t expectedValues[]={37,111,53,106};
    for(unsigned kind=0;kind<4;kind++){
        uint64_t beforeCreated=0,beforeDestroyed=0,created=0,destroyed=0;counts(&beforeCreated,&beforeDestroyed);
        void *object=create(kind);counts(&created,&destroyed);
        int correct=object&&created==beforeCreated+expectedCounts[kind]&&destroyed==beforeDestroyed
                &&value(object,kind)==expectedValues[kind]&&(kind<2||(uintptr_t)object%64==0);
        if(object)destroy(object,kind);counts(&created,&destroyed);
        if(!correct||created!=beforeCreated+expectedCounts[kind]||destroyed!=beforeDestroyed+expectedCounts[kind]){
            failure="HOST_GNU_CPP_REAL_CTOR_VIRTUAL_ARRAY_COOKIE_DTOR";goto done;
        }
    }
done:
    if(classes&&!FreeLibrary(classes)&&!failure)failure="HOST_GNU_CPP_CLASS_APPLICATION_REFERENCE_RETURN";
    if(!FreeLibrary(provider)&&!failure)failure="HOST_GNU_CPP_PROVIDER_APPLICATION_REFERENCE_RETURN";
    if(failure)return load_thread_fail(env,failure);
    fprintf(stdout,"HOST_GNU_CPP_PASS actual_shared_libstdcxx_20_exact_named_ABIs scalar_array_nothrow_sized_aligned sized_aligned real_Cpp23_class_ctor_dtor_array_cookie_virtual_dispatch own_objects_only no_Core_producer_authority no_opaque_class_destructor_inference\n");fflush(stdout);return JNI_TRUE;
}
/* This phase consumes the system UCRT's exact byte exports and the payload's
 * real import slots. Indirect volatile calls keep these actions out of the
 * compiler's memcpy/memset expansion path. No backend table is supplied. */
typedef void *(__cdecl *LoadBytesCopy)(void*,const void*,size_t);
typedef void *(__cdecl *LoadBytesFill)(void*,int,size_t);
typedef int (__cdecl *LoadBytesSecure)(void*,size_t,const void*,size_t);
typedef int (__cdecl *LoadWideSecure)(wchar_t*,size_t,const wchar_t*,size_t);
typedef void (__cdecl *LoadBytesInvalid)(const wchar_t*,const wchar_t*,const wchar_t*,unsigned,uintptr_t);
typedef LoadBytesInvalid (__cdecl *LoadBytesSetInvalid)(LoadBytesInvalid);
extern LoadBytesCopy __imp_memcpy,__imp_memmove;
extern LoadBytesFill __imp_memset;
extern LoadBytesSecure __imp_memcpy_s,__imp_memmove_s;
extern LoadWideSecure __imp_wmemcpy_s,__imp_wmemmove_s;
typedef struct LoadBytesEntries {void *entries[2][10];int *error;LoadBytesSetInvalid setInvalid;} LoadBytesEntries;
static const char *load_bytes_names[]={"memcpy","memmove","memset","memcpy_s","memmove_s",
    "wmemcpy","wmemmove","wmemset","wmemcpy_s","wmemmove_s"};
static volatile LONG load_bytes_invalid_calls;
static void __cdecl load_bytes_invalid_handler(const wchar_t *expression,const wchar_t *function,const wchar_t *file,unsigned line,uintptr_t reserved){
    (void)expression;(void)function;(void)file;(void)line;(void)reserved;
    InterlockedIncrement(&load_bytes_invalid_calls);
}
static int load_bytes_secure(unsigned kind){return kind==3||kind==4||kind==8||kind==9;}
static int load_bytes_wide(unsigned kind){return kind>=5;}
static int load_bytes_available(unsigned kind){return kind<5||kind>7;}
static __declspec(noinline) uintptr_t load_bytes_call(const LoadBytesEntries *entries,unsigned mode,unsigned kind,
        void *destination,size_t destinationSize,const void *source,unsigned value,size_t count){
    void *actual=entries->entries[mode][kind];
    switch(kind){
    case 0:case 1:{LoadBytesCopy volatile call=(LoadBytesCopy)actual;return (uintptr_t)call(destination,source,count);}
    case 2:{LoadBytesFill volatile call=(LoadBytesFill)actual;return (uintptr_t)call(destination,(int)value,count);}
    case 3:case 4:{LoadBytesSecure volatile call=(LoadBytesSecure)actual;return (uintptr_t)call(destination,destinationSize,source,count);}
    case 8:case 9:{LoadWideSecure volatile call=(LoadWideSecure)actual;return (uintptr_t)call(destination,destinationSize,source,count);}
    default:return UINTPTR_MAX;
    }
}
/* The slot must really be an import of this exact CRT contract. The current
 * slot value is a Host thunk, so its current address alone cannot identify the
 * original provider. Inspect only this already loaded payload's PE imports. */
static int load_bytes_ucrt_import(const char *name,void **slot){
    HMODULE image=NULL;
    if(!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
            (LPCSTR)load_bytes_ucrt_import,&image))return 0;
    BYTE *base=(BYTE*)image;IMAGE_DOS_HEADER *dos=(IMAGE_DOS_HEADER*)base;
    if(dos->e_magic!=IMAGE_DOS_SIGNATURE)return 0;
    IMAGE_NT_HEADERS *nt=(IMAGE_NT_HEADERS*)(base+dos->e_lfanew);
    if(nt->Signature!=IMAGE_NT_SIGNATURE)return 0;
    DWORD rva=nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_IMPORT].VirtualAddress;if(!rva)return 0;
    for(IMAGE_IMPORT_DESCRIPTOR *provider=(IMAGE_IMPORT_DESCRIPTOR*)(base+rva);provider->Name;provider++){
        const char *dll=(const char*)(base+provider->Name);
        if(_stricmp(dll,"ucrtbase.dll")&&_stricmp(dll,"api-ms-win-crt-string-l1-1-0.dll"))continue;
        if(!provider->OriginalFirstThunk||!provider->FirstThunk)continue;
        IMAGE_THUNK_DATA *names=(IMAGE_THUNK_DATA*)(base+provider->OriginalFirstThunk);
        IMAGE_THUNK_DATA *values=(IMAGE_THUNK_DATA*)(base+provider->FirstThunk);
        for(;names->u1.AddressOfData;names++,values++){
            if(IMAGE_SNAP_BY_ORDINAL(names->u1.Ordinal))continue;
            IMAGE_IMPORT_BY_NAME *import=(IMAGE_IMPORT_BY_NAME*)(base+names->u1.AddressOfData);
            if(!strcmp((const char*)import->Name,name))return (void**)&values->u1.Function==slot;
        }
    }
    return 0;
}
static const char *load_bytes_entries(LoadBytesEntries *entries){
    HMODULE provider=GetModuleHandleW(L"ucrtbase.dll");if(!provider)return "CRT_BYTES_ACTUAL_UCRTBASE_ABSENT";
    void **slots[]={(void**)&__imp_memcpy,(void**)&__imp_memmove,(void**)&__imp_memset,(void**)&__imp_memcpy_s,(void**)&__imp_memmove_s,
        NULL,NULL,NULL,(void**)&__imp_wmemcpy_s,(void**)&__imp_wmemmove_s};
    for(unsigned kind=0;kind<10;kind++){
        entries->entries[1][kind]=(void*)GetProcAddress(provider,load_bytes_names[kind]);
        if(!load_bytes_available(kind)){
            if(entries->entries[1][kind])return "CRT_BYTES_NONSECURE_WIDE_EXPORT_PRESENT_WITHOUT_ACTUAL_IAT_COVERAGE";
            continue;
        }
        entries->entries[0][kind]=*slots[kind];
        if(!load_bytes_ucrt_import(load_bytes_names[kind],slots[kind]))return "CRT_BYTES_ACTUAL_UCRT_IAT_PROVIDER";
        if(!load_private_entry(entries->entries[0][kind])||!load_private_entry(entries->entries[1][kind]))return "CRT_BYTES_SEVEN_EXACT_IAT_AND_GPA_NOT_CONTROLLED";
    }
    LoadCrtErrno error=(LoadCrtErrno)GetProcAddress(provider,"_errno");
    entries->error=error?error():NULL;
    entries->setInvalid=(LoadBytesSetInvalid)GetProcAddress(provider,"_set_thread_local_invalid_parameter_handler");
    return entries->error&&entries->setInvalid?NULL:"CRT_BYTES_REAL_ERRNO_AND_THREAD_INVALID_HANDLER";
}
static const char *load_bytes_normal(const LoadBytesEntries *entries,BYTE *scratch){
    for(unsigned mode=0;mode<2;mode++)for(unsigned kind=0;kind<10;kind++){
        if(!load_bytes_available(kind))continue;
        union {uint64_t alignment;BYTE bytes[512];} expected;
        for(unsigned i=0;i<512;i++)scratch[i]=expected.bytes[i]=(BYTE)(0x19u+29u*i);
        BYTE *bytes=scratch+32,*input=scratch+160,*want=expected.bytes+32;
        wchar_t *wide=(wchar_t*)(scratch+256),*wideInput=(wchar_t*)(scratch+384),*wideWant=(wchar_t*)(expected.bytes+256);
        for(unsigned i=0;i<32;i++){wide[i]=wideWant[i]=(wchar_t)(0x2301u+17u*i);wideInput[i]=(wchar_t)(0x1201u+23u*i);}
        for(unsigned i=0;i<64;i++)expected.bytes[384+i]=scratch[384+i];
        void *destination=NULL;const void *source=NULL;size_t count=0,size=0;unsigned fill=0;
        switch(kind){
        case 0:case 3:destination=bytes+8;source=input+3;count=13;size=32;for(unsigned i=0;i<count;i++)want[8+i]=input[3+i];break;
        case 1:case 4:destination=bytes+9;source=bytes+3;count=19;size=48;for(unsigned i=0;i<count;i++)want[9+i]=bytes[3+i];break;
        case 2:destination=bytes+5;count=21;fill=0x1d3;for(unsigned i=0;i<count;i++)want[5+i]=(BYTE)fill;break;
        case 8:destination=wide+4;source=wideInput+2;count=7;size=24;for(unsigned i=0;i<count;i++)wideWant[4+i]=wideInput[2+i];break;
        case 9:destination=wide+5;source=wide+1;count=13;size=27;for(unsigned i=0;i<count;i++)wideWant[5+i]=wide[1+i];break;
        }
        *entries->error=0x298;SetLastError(0x524e0098);
        uintptr_t result=load_bytes_call(entries,mode,kind,destination,size,source,fill,count);DWORD windows=GetLastError();
        if(result!=(load_bytes_secure(kind)?0:(uintptr_t)destination)||*entries->error!=0x298||windows!=0x524e0098)
            return "CRT_BYTES_ORIGINAL_RETURN_ERRNO_OR_LASTERROR";
        for(unsigned i=0;i<512;i++)if(scratch[i]!=expected.bytes[i])return "CRT_BYTES_ORIGINAL_COPY_OVERLAP_FILL_WIDE_COUNT_OR_CANARY";
    }
    return NULL;
}
static const char *load_bytes_invalid(const LoadBytesEntries *entries,BYTE *scratch){
    unsigned kinds[]={3,4,8,9};
    for(unsigned mode=0;mode<2;mode++)for(unsigned selected=0;selected<4;selected++)for(unsigned nullSource=0;nullSource<2;nullSource++){
        unsigned kind=kinds[selected];size_t unit=load_bytes_wide(kind)?sizeof(wchar_t):1;
        BYTE expected[128];for(unsigned i=0;i<128;i++)scratch[i]=expected[i]=(BYTE)(0xa3u+7u*i);
        void *destination=scratch+16;size_t size=nullSource?9:7,count=nullSource?3:8;
        const void *source=nullSource?NULL:scratch+80;
        if(kind==3||kind==8)for(size_t i=0;i<size*unit;i++)expected[16+i]=0;
        InterlockedExchange(&load_bytes_invalid_calls,0);*entries->error=0x298;SetLastError(0x524e0098);
        uintptr_t result=load_bytes_call(entries,mode,kind,destination,size,source,0,count);DWORD windows=GetLastError();
        int expectedError=nullSource?EINVAL:ERANGE;
        if(result!=(uintptr_t)expectedError||*entries->error!=expectedError||windows!=0x524e0098||load_bytes_invalid_calls!=1)
            return "CRT_BYTES_SECURE_ORIGINAL_ERROR_ERRNO_HANDLER_ONCE";
        for(unsigned i=0;i<128;i++)if(scratch[i]!=expected[i])return "CRT_BYTES_SECURE_MEMCPY_DESTSIZE_CLEAR_OR_MEMMOVE_UNCHANGED";
    }
    return NULL;
}
static const char *load_bytes_refused(const LoadBytesEntries *entries,void *address,size_t bytes,DWORD expectedError){
    if(!address||!bytes||bytes>64||bytes%sizeof(wchar_t))return "CRT_BYTES_ACTUAL_TARGET_GEOMETRY";
    int expectedErrno=expectedError==ERROR_BUSY?EBUSY:EACCES;
    union {uint64_t alignment;BYTE bytes[96];} input;
    BYTE before[64],saved[96];for(unsigned i=0;i<96;i++)input.bytes[i]=(BYTE)(0x45u+13u*i);
    for(size_t i=0;i<bytes;i++){before[i]=((BYTE*)address)[i];input.bytes[16+i]=before[i];}
    for(unsigned i=0;i<96;i++)saved[i]=input.bytes[i];
    for(unsigned mode=0;mode<2;mode++)for(unsigned kind=0;kind<10;kind++){
        if(!load_bytes_available(kind))continue;
        size_t unit=load_bytes_wide(kind)?sizeof(wchar_t):1,count=bytes/unit;unsigned fill=before[0];
        if(kind==2)count=1;
        InterlockedExchange(&load_bytes_invalid_calls,0);*entries->error=0x298;SetLastError(0x524e0098);
        uintptr_t result=UINTPTR_MAX;DWORD exception=0;
        __try {result=load_bytes_call(entries,mode,kind,address,bytes/unit,input.bytes+16,fill,count);}
        __except(EXCEPTION_EXECUTE_HANDLER){exception=GetExceptionCode();}
        DWORD windows=GetLastError();int crt=*entries->error;
        if(exception||result!=(load_bytes_secure(kind)?expectedErrno:0)||windows!=expectedError
                ||crt!=expectedErrno||load_bytes_invalid_calls){
            fprintf(stderr,"CRT_BYTES_REFUSAL_FAILED api=%s path=%s result=%llu win32=%lu errno=%d handler=%ld exception=%08lx\n",
                    load_bytes_names[kind],mode?"GPA":"IAT",(unsigned long long)result,(unsigned long)windows,crt,load_bytes_invalid_calls,(unsigned long)exception);
            return "CRT_BYTES_CONTROL_TYPED_OR_LIVE_BORROW_REFUSAL_CONTRACT";
        }
        for(size_t i=0;i<bytes;i++)if(((BYTE*)address)[i]!=before[i])return "CRT_BYTES_REFUSED_TARGET_CHANGED";
        for(unsigned i=0;i<96;i++)if(input.bytes[i]!=saved[i])return "CRT_BYTES_REFUSED_INPUT_OR_CANARY_CHANGED";
    }
    /* Unlike move_s's invalid-argument path, copy_s would clear destSize even
     * when count is only one element. That real write must be rejected before
     * the original handler or clear can affect this protected/borrowed target. */
    for(unsigned mode=0;mode<2;mode++)for(unsigned kind=3;kind<=8;kind+=5){
        size_t unit=load_bytes_wide(kind)?sizeof(wchar_t):1;
        InterlockedExchange(&load_bytes_invalid_calls,0);*entries->error=0x298;SetLastError(0x524e0098);
        uintptr_t result=load_bytes_call(entries,mode,kind,address,bytes/unit,NULL,0,1);DWORD windows=GetLastError();
        if(result!=(uintptr_t)expectedErrno||*entries->error!=expectedErrno||windows!=expectedError||load_bytes_invalid_calls)
            return "CRT_BYTES_PROTECTED_SECURE_ERROR_DESTSIZE_CLEAR_NOT_REFUSED";
        for(size_t i=0;i<bytes;i++)if(((BYTE*)address)[i]!=before[i])return "CRT_BYTES_PROTECTED_SECURE_ERROR_CLEARED_TARGET";
    }
    return NULL;
}
static const char *load_bytes_zero(const LoadBytesEntries *entries,const LoadMemoryTarget *target){
    for(unsigned mode=0;mode<2;mode++)for(unsigned kind=0;kind<10;kind++)for(unsigned numeric=0;numeric<2;numeric++){
        if(!load_bytes_available(kind))continue;
        void *destination=numeric?(void*)(uintptr_t)1:target->address;
        InterlockedExchange(&load_bytes_invalid_calls,0);*entries->error=0x298;SetLastError(0x524e0098);
        uintptr_t result=load_bytes_call(entries,mode,kind,destination,SIZE_MAX,(void*)(uintptr_t)3,0x5432,0);
        DWORD windows=GetLastError();int crt=*entries->error;LONG handler=load_bytes_invalid_calls;
        uintptr_t expected=load_bytes_secure(kind)?0:(uintptr_t)destination;int unchanged=load_memory_unchanged(target);
        if(result!=expected||crt!=0x298||windows!=0x524e0098||handler||!unchanged){
            fprintf(stderr,"CRT_BYTES_ZERO_FAILED api=%s path=%s target=%s result=%llu expected=%llu errno=%d windows=%lu handler=%ld unchanged=%d\n",
                    load_bytes_names[kind],mode?"GPA":"IAT",numeric?"numeric":"VM_shadow",(unsigned long long)result,
                    (unsigned long long)expected,crt,(unsigned long)windows,handler,unchanged);
            return "CRT_BYTES_ORIGINAL_ZERO_COUNT_NUMERIC_POINTER_CONTRACT";
        }
    }
    return NULL;
}
/* This DLL copy is loaded by the system loader before the real Core binding.
 * Its own imports/GPA results are consumed while a deeper Core frame is live. */
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_AuditFixCheck_hostCrtBytesForeign0(JNIEnv *env,jclass check,jlong address,jlong bytes,jint mode){
    (void)check;LoadBytesEntries entries={0};const char *failure=load_bytes_entries(&entries);
    if(!failure&&(bytes<=0||bytes>64||mode<0||mode>1))failure="CRT_BYTES_FOREIGN_ACTUAL_ARGUMENTS";
    LoadBytesInvalid previous=NULL;int handler=0;
    if(!failure){previous=entries.setInvalid(load_bytes_invalid_handler);handler=1;
        if(!mode)failure=load_bytes_refused(&entries,(void*)(uintptr_t)address,(size_t)bytes,ERROR_BUSY);
        else {
            BYTE before[64];for(size_t i=0;i<(size_t)bytes;i++)before[i]=((BYTE*)(uintptr_t)address)[i];
            *entries.error=0x298;SetLastError(0x524e0098);
            uintptr_t result=load_bytes_call(&entries,0,2,(void*)(uintptr_t)address,0,NULL,0x6b,(size_t)bytes);
            DWORD windows=GetLastError();
            if(result!=(uintptr_t)address||*entries.error!=0x298||windows!=0x524e0098)failure="CRT_BYTES_FOREIGN_ALIVE_ORIGINAL_FILL";
            for(size_t i=0;i<(size_t)bytes&&!failure;i++)if(((BYTE*)(uintptr_t)address)[i]!=0x6b)failure="CRT_BYTES_FOREIGN_ALIVE_FILL_BYTES";
            result=load_bytes_call(&entries,1,0,(void*)(uintptr_t)address,0,before,0,(size_t)bytes);
            if(result!=(uintptr_t)address&&!failure)failure="CRT_BYTES_FOREIGN_ALIVE_ORIGINAL_RESTORE";
            for(size_t i=0;i<(size_t)bytes&&!failure;i++)if(((BYTE*)(uintptr_t)address)[i]!=before[i])failure="CRT_BYTES_FOREIGN_ALIVE_RESTORED_BYTES";
        }
    }
    if(handler&&entries.setInvalid(previous)!=load_bytes_invalid_handler&&!failure)failure="CRT_BYTES_FOREIGN_INVALID_HANDLER_RESTORE";
    if(failure)return load_thread_fail(env,failure)?JNI_TRUE:JNI_FALSE;
    fprintf(stdout,"HOST_CRT_BYTES_FOREIGN_PASS actual_distinct_system_loader_image mode=%s deeper_Core_frame_no_raw_override\n",mode?"plain_Heap_alive_original_write_and_restore":"typed_or_live_borrow_refused");
    fflush(stdout);return JNI_TRUE;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_AuditFixCheck_hostCrtBytes0(JNIEnv *env,jclass check,jstring fixturePath){
    LoadBytesEntries entries={0};const char *failure=load_bytes_entries(&entries);HANDLE heap=NULL;BYTE *scratch=NULL;
    HMODULE fixture=NULL;JavaVM *vm=NULL;LoadMemoryTarget target={0};int targetObserved=0,handler=0,live=0,held=0,privateLive=0;
    LoadBytesInvalid previous=NULL;DWORD exception=0;
    int (__cdecl *bind)(HMODULE)=NULL;
    int (__cdecl *run)(void)=NULL;
    int (__cdecl *containersRun)(void)=NULL;
    int (__cdecl *hold)(int)=NULL;int (__cdecl *finish)(void)=NULL;
    uint64_t (__cdecl *current)(unsigned)=NULL;
    int (__cdecl *privateCreate)(void)=NULL;int (__cdecl *privateApply)(unsigned)=NULL;int (__cdecl *privateFinish)(void)=NULL;
    uint64_t (__cdecl *privateCurrent)(unsigned)=NULL;
    if(failure)goto done;
    fprintf(stdout,"HOST_CRT_BYTES_NOT_AVAILABLE provider=ucrtbase.dll APIs=wmemcpy,wmemmove,wmemset no_actual_exports no_imports_no_fallback_calls\n");
    if((*env)->GetJavaVM(env,&vm)!=JNI_OK||!vm||!*vm
            ||!load_memory_target(&target,(void*)&(*vm)->GetEnv,sizeof((*vm)->GetEnv),MEM_PRIVATE,(void*)*vm)){
        failure="CRT_BYTES_ACTUAL_VM_SHADOW_GETENV_SLOT";goto done;
    }
    targetObserved=1;heap=HeapCreate(0,0,0);if(heap)scratch=(BYTE*)HeapAlloc(heap,0,512);
    if(!scratch){failure="CRT_BYTES_ACTUAL_HEAP_STORAGE";goto done;}
    previous=entries.setInvalid(load_bytes_invalid_handler);handler=1;
    __try {
        failure=load_bytes_normal(&entries,scratch);if(!failure)failure=load_bytes_invalid(&entries,scratch);
        if(!failure)failure=load_bytes_refused(&entries,target.address,target.bytes,ERROR_ACCESS_DENIED);
        if(!failure)failure=load_bytes_zero(&entries,&target);
        if(failure)__leave;
        fixture=load_allocator_provider(env,fixturePath);if(!fixture){failure="CRT_BYTES_ACTUAL_CPP_FIXTURE_LOAD";__leave;}
        bind=(void*)GetProcAddress(fixture,"audit_allocator_fields_bind");run=(void*)GetProcAddress(fixture,"audit_allocator_fields_run");
        containersRun=(void*)GetProcAddress(fixture,"audit_allocator_fields_containers_run");
        hold=(void*)GetProcAddress(fixture,"audit_allocator_fields_hold");finish=(void*)GetProcAddress(fixture,"audit_allocator_fields_finish");
        current=(void*)GetProcAddress(fixture,"audit_allocator_fields_current");
        privateCreate=(void*)GetProcAddress(fixture,"audit_crt_private_create");privateApply=(void*)GetProcAddress(fixture,"audit_crt_private_apply");
        privateFinish=(void*)GetProcAddress(fixture,"audit_crt_private_finish");privateCurrent=(void*)GetProcAddress(fixture,"audit_crt_private_current");
        if(!bind||!run||!containersRun||!hold||!finish||!current||!privateCreate||!privateApply||!privateFinish||!privateCurrent){failure="CRT_BYTES_ACTUAL_CPP_EXPORTS";__leave;}
        if(!privateCreate()){failure="CRT_BYTES_NONSDK_PRIVATE_CLASS_REAL_OPERATOR_NEW";__leave;}privateLive=1;
        for(unsigned kind=0;kind<10&&!failure;kind++){
            BYTE expectedBytes[24];wchar_t expectedWide[12];uint64_t expectedScalar=UINT64_C(0x1122334455667788),byteSum=0,wideSum=0;
            for(unsigned i=0;i<24;i++)expectedBytes[i]=(BYTE)(0x10+i);
            for(unsigned i=0;i<12;i++)expectedWide[i]=(wchar_t)(0x140+i);
            switch(kind){
            case 0:case 3:expectedScalar=UINT64_C(0x1020304050607080);break;
            case 1:for(unsigned i=0;i<9;i++)expectedBytes[3+i]=(BYTE)(0x10+i);break;
            case 2:for(unsigned i=0;i<7;i++)expectedBytes[4+i]=0x3c;break;
            case 4:for(unsigned i=0;i<10;i++)expectedBytes[2+i]=(BYTE)(0x10+i);break;
            case 5:for(unsigned i=0;i<4;i++)expectedWide[2+i]=(wchar_t)(0x101u*(i+1)+0x11u*i);break;
            case 6:for(unsigned i=0;i<6;i++)expectedWide[3+i]=(wchar_t)(0x140+i);break;
            case 7:for(unsigned i=0;i<5;i++)expectedWide[2+i]=0x3535;break;
            case 8:for(unsigned i=0;i<4;i++)expectedWide[1+i]=(wchar_t)(0x101u*(i+1)+0x11u*i);break;
            case 9:for(unsigned i=0;i<7;i++)expectedWide[2+i]=(wchar_t)(0x140+i);break;
            }
            for(unsigned i=0;i<24;i++)byteSum+=(uint64_t)(i+1)*expectedBytes[i];
            for(unsigned i=0;i<12;i++)wideSum+=(uint64_t)(i+1)*expectedWide[i];
            if(!privateApply(kind)||privateCurrent(0)!=expectedScalar||privateCurrent(1)!=byteSum||privateCurrent(2)!=wideSum
                    ||privateCurrent(3)!=UINT64_C(0x193a5c7e9fb1d2f4)||privateCurrent(4)!=UINT64_C(0x4f2d1b9e7c5a3816))failure="CRT_BYTES_NONSDK_PRIVATE_SCALAR_ARRAY_OR_CANARY";
        }
        if(failure)__leave;
        if(!privateFinish()){failure="CRT_BYTES_NONSDK_PRIVATE_CLASS_REAL_DELETE";__leave;}privateLive=0;
        if(!bind(NULL)){failure="CRT_BYTES_REAL_CORE_ALLOCATOR_FIELDS_BIND";__leave;}live=1;
        if(!run()){failure="CRT_BYTES_REAL_CORE_ALLOCATOR_FIELDS_START";__leave;}
        if(current(18)!=3||!current(0)||!current(2)||!current(3)){failure="CRT_BYTES_REAL_CORE_TYPED_RECEIPTS_REQUIRED";__leave;}
        jmethodID foreign=(*env)->GetStaticMethodID(env,check,"hostCrtBytesForeign","(JJI)Z");
        if(!foreign){failure="CRT_BYTES_ACTUAL_SYSTEM_LOADER_FORWARDER";__leave;}
        // Ordinary observed Heap storage has no typed-object declaration or
        // live alias. A foreign alive source may contribute a real raw write.
        if(!(*env)->CallStaticBooleanMethod(env,check,foreign,(jlong)(uintptr_t)scratch,(jlong)32,(jint)1)||(*env)->ExceptionCheck(env)){
            failure="CRT_BYTES_FOREIGN_PLAIN_HEAP_ALIVE_COMMON_WRITE";__leave;
        }
        {BYTE before[32];for(unsigned i=0;i<32;i++)before[i]=scratch[i];
            if(load_bytes_call(&entries,0,3,scratch,sizeof(before),before,0,sizeof(before))){failure="CRT_BYTES_CORE_PLAIN_HEAP_COMMON_WRITE";__leave;}
            for(unsigned i=0;i<32;i++)if(scratch[i]!=before[i]){failure="CRT_BYTES_CORE_PLAIN_HEAP_ORIGINAL_BYTES";break;}}
        for(unsigned selection=0;selection<2&&!failure;selection++){
            void *address=(void*)(uintptr_t)current(selection?4:6);size_t bytes=selection?sizeof(uint64_t):(size_t)current(7);
            if(!address||!hold(selection?2:1)){failure="CRT_BYTES_REAL_TYPED_BORROW_BEGIN";break;}held=1;
            failure=load_bytes_refused(&entries,address,bytes,ERROR_BUSY);
            if(!failure&&(!(*env)->CallStaticBooleanMethod(env,check,foreign,(jlong)(uintptr_t)address,(jlong)bytes,(jint)0)||(*env)->ExceptionCheck(env)))failure="CRT_BYTES_FOREIGN_REAL_TYPED_BORROW";
            if(!hold(0)){failure="CRT_BYTES_REAL_TYPED_BORROW_RETURN";break;}held=0;
            // Returning an alias preserves the real typed-object declaration.
            // Only the SDK's accurate original-operation scope may write it.
            if(!failure)failure=load_bytes_refused(&entries,address,bytes,ERROR_BUSY);
            if(!failure&&(!(*env)->CallStaticBooleanMethod(env,check,foreign,(jlong)(uintptr_t)address,(jlong)bytes,(jint)0)||(*env)->ExceptionCheck(env)))failure="CRT_BYTES_FOREIGN_TYPED_WRITE_AFTER_BORROW_RETURN";
        }
        if(!failure&&(!containersRun()||current(28)!=1||current(29)!=1||current(32)!=16||current(33)!=64))failure="CRT_BYTES_REAL_SDK_ORIGINAL_MUTATION_AFTER_BORROW_RETURN";
        if(!failure&&(!finish()))failure="CRT_BYTES_REAL_TYPED_ORIGINAL_DESTRUCTOR_RELEASE";else if(!failure)live=0;
    } __except(EXCEPTION_EXECUTE_HANDLER){exception=GetExceptionCode();failure="CRT_BYTES_ACTUAL_NATIVE_EXCEPTION";}
done:
    if(held&&hold)hold(0);
    if(live&&finish&&!(*env)->ExceptionCheck(env)&&finish())live=0;
    if(privateLive&&privateFinish&&privateFinish())privateLive=0;
    if(handler&&entries.setInvalid(previous)!=load_bytes_invalid_handler&&!failure)failure="CRT_BYTES_ORIGINAL_THREAD_INVALID_HANDLER_RESTORE";
    if(targetObserved&&!load_memory_unchanged(&target)&&!failure)failure="CRT_BYTES_VM_SHADOW_CHANGED";
    if(scratch&&!HeapFree(heap,0,scratch)&&!failure)failure="CRT_BYTES_ORIGINAL_HEAP_FREE";
    if(heap&&!HeapDestroy(heap)&&!failure)failure="CRT_BYTES_ORIGINAL_HEAP_DESTROY";
    if(fixture&&!live&&!privateLive&&!FreeLibrary(fixture)&&!failure)failure="CRT_BYTES_CPP_APPLICATION_REFERENCE_RETURN";
    if(failure){fprintf(stderr,"HOST_CRT_BYTES_FAILED stage=%s exception=%08lx\n",failure,(unsigned long)exception);return load_thread_fail(env,failure)?JNI_TRUE:JNI_FALSE;}
    fprintf(stdout,"HOST_CRT_BYTES_PASS actual_ucrtbase_seven_exact_IAT_GPA ordinary_Heap_storage copy_same_allocation_overlap_move_byte_fill_secure_wchar_counts secure_original_ERRNO_single_returning_thread_invalid_handler memcpy_s_destSize_clear_memmove_s_unchanged input_canaries_preserved real_VM_GetEnv_shadow_write_refused zero_count_numeric_pointer_original_contract nonsdk_trivial_private_class_actual_operator_new_private_scalar_byte_wchar_getters_real_secure_wide_copy_move_memset_0x35_byte_pattern_original_delete plain_Heap_foreign_Core_alive_common_write real_Core_SDK_payload_and_scalar_Core_foreign_raw_refused_during_and_after_borrow SDK_original_container_mutation_and_destructor_release NOT_AVAILABLE_wmemcpy_wmemmove_wmemset_no_actual_exports\n");
    fflush(stdout);return JNI_TRUE;
}

/* The ordinary JNI payload calls an independent real C++23 PMR consumer.
 * Any SDK coverage is reported from real receipts; original-call success alone
 * does not certify the separately supplied-source isolated SDK seam. */
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_AuditFixCheck_hostCustomAllocatorFields0(JNIEnv *env,jclass check,jstring fixturePath){
    jobject owner=(*env)->GetModule(env,check);jclass moduleType=owner?(*env)->GetObjectClass(env,owner):NULL;
    jmethodID moduleName=moduleType?(*env)->GetMethodID(env,moduleType,"getName","()Ljava/lang/String;"):NULL;
    jstring name=moduleName?(*env)->CallObjectMethod(env,owner,moduleName):NULL;
    const char *text=name?(*env)->GetStringUTFChars(env,name,NULL):NULL;
    int requireCore=text&&!strcmp(text,"ronova_pro");
    if(text)(*env)->ReleaseStringUTFChars(env,name,text);
    if(name)(*env)->DeleteLocalRef(env,name);if(moduleType)(*env)->DeleteLocalRef(env,moduleType);if(owner)(*env)->DeleteLocalRef(env,owner);
    if((*env)->ExceptionCheck(env))return JNI_FALSE;
    HMODULE module=load_allocator_provider(env,fixturePath);const char *failure=NULL;
    if(!module)return load_thread_fail(env,"HOST_CUSTOM_FIELDS_EXPLICIT_FIXTURE_LOAD");
    int (__cdecl *bind)(HMODULE)=(void*)GetProcAddress(module,"audit_allocator_fields_bind");
    int (__cdecl *run)(void)=(void*)GetProcAddress(module,"audit_allocator_fields_run");
    int (__cdecl *containersRun)(void)=(void*)GetProcAddress(module,"audit_allocator_fields_containers_run");
    int (__cdecl *containersMutate)(void)=(void*)GetProcAddress(module,"audit_allocator_fields_containers_mutate");
    uint64_t (__cdecl *current)(unsigned)=(void*)GetProcAddress(module,"audit_allocator_fields_current");
    int (__cdecl *finish)(void)=(void*)GetProcAddress(module,"audit_allocator_fields_finish");
    int completed=1;uint64_t coverage=0,context=0,object=0,field=0,buffer=0,standard=0,vector=0,string=0,stale=0;DWORD exception=0;
    if(!bind||!run||!containersRun||!containersMutate||!current||!finish){failure="HOST_CUSTOM_FIELDS_ACTUAL_EXPORTS";goto done;}
    fprintf(stderr,"HOST_CUSTOM_FIELDS_STAGE real_HeapCreate_backed_standard_PMR_pool_Cpp23_consumer\n");fflush(stderr);
    __try {
        completed=0;
        if(!bind(NULL)||!run())failure="HOST_CUSTOM_FIELDS_ORIGINAL_CONSTRUCT_WRITE_VIRTUAL_CALL";
        if(!failure){coverage=current(18);context=current(12);object=current(0);field=current(2);buffer=current(3);}
        if(!failure&&(!containersRun()||!containersMutate()))failure="HOST_CUSTOM_FIELDS_ORIGINAL_STD_ALLOCATOR_VECTOR_STRING_MUTATIONS";
        if(!failure){standard=current(22);vector=current(24);string=current(25);stale=current(30);}
        if(!failure&&requireCore&&(coverage!=3||!context||!object||!field||!buffer||!standard||!vector||!string||stale!=4))
            failure="HOST_BOUND_CORE_REAL_STATE_RECEIPTS_MISSING";
        completed=finish();
        if(!completed&&!failure)failure="HOST_CUSTOM_FIELDS_ORIGINAL_LIFETIME_RETAINED";
    } __except(EXCEPTION_EXECUTE_HANDLER){exception=GetExceptionCode();failure="HOST_CUSTOM_FIELDS_REAL_NATIVE_EXCEPTION";}
done:
    if(completed&&!FreeLibrary(module)&&!failure)failure="HOST_CUSTOM_FIELDS_APPLICATION_REFERENCE_RETURN";
    if(failure){fprintf(stderr,"HOST_CUSTOM_FIELDS_FAILED exception=%08lx\n",(unsigned long)exception);return load_thread_fail(env,failure);}
    fprintf(stdout,"HOST_CUSTOM_FIELDS_PASS actual_HeapCreate_HeapAlloc_HeapFree_standard_unsynchronized_pool_original_allocate_deallocate Cpp23_constructor_scalar_write_owned_payload_virtual_read_destructor ctx=%llu object=%llu field=%llu buffer=%llu coverage=%s real_std_allocator_original_allocate_deallocate vector_basic_string_resize_reserve_reallocation_shrink std_context=%llu vector_view=%llu string_view=%llu rejected_old_views=%llu std_allocator_coverage=%s no_named_CPP_backing_inference no_fabricated_Core_source\n",
            (unsigned long long)context,(unsigned long long)object,(unsigned long long)field,(unsigned long long)buffer,
            coverage==3&&context&&object&&field&&buffer?"observed":"uncovered",
            (unsigned long long)standard,(unsigned long long)vector,(unsigned long long)string,(unsigned long long)stale,
            standard&&vector&&string&&stale==4?"observed":"uncovered");
    fflush(stdout);return JNI_TRUE;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_AuditFixCheck_hostCngHash0(JNIEnv *env,jclass check){
    (void)check;HMODULE provider=LoadLibraryExW(L"bcrypt.dll",NULL,LOAD_LIBRARY_SEARCH_SYSTEM32);
    if(!provider)return load_thread_fail(env,"CNG_ACTUAL_PROVIDER_LOAD")?JNI_TRUE:JNI_FALSE;
    typedef NTSTATUS (WINAPI *Open)(BCRYPT_ALG_HANDLE*,LPCWSTR,LPCWSTR,ULONG);
    typedef NTSTATUS (WINAPI *Create)(BCRYPT_ALG_HANDLE,BCRYPT_HASH_HANDLE*,PUCHAR,ULONG,PUCHAR,ULONG,ULONG);
    typedef NTSTATUS (WINAPI *Data)(BCRYPT_HASH_HANDLE,PUCHAR,ULONG,ULONG);
    typedef NTSTATUS (WINAPI *Finish)(BCRYPT_HASH_HANDLE,PUCHAR,ULONG,ULONG);
    typedef NTSTATUS (WINAPI *Destroy)(BCRYPT_HASH_HANDLE);
    typedef NTSTATUS (WINAPI *Close)(BCRYPT_ALG_HANDLE,ULONG);
    const char *names[]={"BCryptOpenAlgorithmProvider","BCryptCreateHash","BCryptHashData","BCryptFinishHash","BCryptDestroyHash","BCryptCloseAlgorithmProvider"};
    void *entries[6]={0};const char *failure=NULL;
    for(unsigned i=0;i<6;i++){entries[i]=(void*)GetProcAddress(provider,names[i]);if(!load_private_entry(entries[i])){failure="CNG_ACTUAL_GPA_NOT_CONTROLLED";break;}}
    Open open=(Open)entries[0];Create create=(Create)entries[1];Data data=(Data)entries[2];
    Finish finish=(Finish)entries[3];Destroy destroy=(Destroy)entries[4];Close close=(Close)entries[5];
    static const BYTE expected[32]={0xba,0x78,0x16,0xbf,0x8f,0x01,0xcf,0xea,0x41,0x41,0x40,0xde,0x5d,0xae,0x22,0x23,
        0xb0,0x03,0x61,0xa3,0x96,0x17,0x7a,0x9c,0xb4,0x10,0xff,0x61,0xf2,0x00,0x15,0xad};
    BYTE input[]={'a','b','c'},digest[32];BCRYPT_ALG_HANDLE algorithm=NULL;BCRYPT_HASH_HANDLE hash=NULL;
    HANDLE heap=NULL;PUCHAR external=NULL;int externalHeld=0;
    for(unsigned kind=0;kind<2&&!failure;kind++){
        NTSTATUS status=kind?open(&algorithm,BCRYPT_SHA256_ALGORITHM,NULL,BCRYPT_HASH_REUSABLE_FLAG)
                :BCryptOpenAlgorithmProvider(&algorithm,BCRYPT_SHA256_ALGORITHM,NULL,0);
        if(status<0||!algorithm){failure="CNG_ORIGINAL_OPEN";break;}
        status=kind?create(algorithm,&hash,NULL,0,NULL,0,0):BCryptCreateHash(algorithm,&hash,NULL,0,NULL,0,0);
        if(status<0||!hash){failure="CNG_ORIGINAL_CREATE";break;}
        for(unsigned round=0;round<(kind?2u:1u);round++){
            status=kind?data(hash,input,sizeof(input),0):BCryptHashData(hash,input,sizeof(input),0);
            if(status<0){failure="CNG_ORIGINAL_HASH_DATA";break;}
            memset(digest,0xa5,sizeof(digest));status=kind?finish(hash,digest,sizeof(digest),0):BCryptFinishHash(hash,digest,sizeof(digest),0);
            if(status<0||memcmp(digest,expected,sizeof(digest))){failure="CNG_ORIGINAL_DIGEST_OR_REUSABLE_STATE";break;}
        }
        if(failure)break;
        status=kind?destroy(hash):BCryptDestroyHash(hash);if(status<0){failure="CNG_ORIGINAL_DESTROY";break;}hash=NULL;
        status=kind?close(algorithm,0):BCryptCloseAlgorithmProvider(algorithm,0);if(status<0){failure="CNG_ORIGINAL_CLOSE";break;}algorithm=NULL;
    }
    if(!failure){
        ULONG bytes=0,copied=0;NTSTATUS status=open(&algorithm,BCRYPT_SHA256_ALGORITHM,NULL,0);
        if(status<0||!algorithm)failure="CNG_EXTERNAL_ORIGINAL_OPEN";
        if(!failure&&(BCryptGetProperty(algorithm,BCRYPT_OBJECT_LENGTH,(PUCHAR)&bytes,sizeof(bytes),&copied,0)<0
                ||copied!=sizeof(bytes)||!bytes))failure="CNG_EXTERNAL_ORIGINAL_OBJECT_LENGTH";
        if(!failure){heap=HeapCreate(0,0,0);if(heap)external=(PUCHAR)HeapAlloc(heap,0,bytes);
            if(!external)failure="CNG_EXTERNAL_ACTUAL_HEAP_ALLOCATION";}
        if(!failure){status=create(algorithm,&hash,external,bytes,NULL,0,0);
            if(status<0||!hash)failure="CNG_EXTERNAL_ORIGINAL_CREATE";else externalHeld=1;}
        if(!failure&&HeapFree(heap,0,external)){
            // The provider may still refer to a wrongly released object. Stop
            // without dereferencing it again or invoking its destructor.
            external=NULL;hash=NULL;algorithm=NULL;externalHeld=0;failure="CNG_LIVE_EXTERNAL_OBJECT_FREE_ADMITTED";
        }
        if(!failure){status=data(hash,input,sizeof(input),0);
            if(status<0||finish(hash,digest,sizeof(digest),0)<0||memcmp(digest,expected,sizeof(digest)))failure="CNG_EXTERNAL_ORIGINAL_HASH";}
        if(!failure){status=destroy(hash);if(status<0)failure="CNG_EXTERNAL_ORIGINAL_DESTROY";
            else {hash=NULL;externalHeld=0;}}
        if(!failure){if(!HeapFree(heap,0,external))failure="CNG_DESTROYED_EXTERNAL_OBJECT_FREE_REFUSED";else external=NULL;}
        if(!failure){if(close(algorithm,0)<0)failure="CNG_EXTERNAL_ORIGINAL_CLOSE";else algorithm=NULL;}
    }
    if(hash&&BCryptDestroyHash(hash)>=0){hash=NULL;externalHeld=0;}
    if(algorithm)BCryptCloseAlgorithmProvider(algorithm,0);
    if(external&&!externalHeld&&HeapFree(heap,0,external))external=NULL;
    if(heap&&!external&&!externalHeld)HeapDestroy(heap);
    if(!FreeLibrary(provider)&&!failure)failure="CNG_APPLICATION_REFERENCE_RETURN";
    if(failure)return load_thread_fail(env,failure)?JNI_TRUE:JNI_FALSE;
    fprintf(stdout,"HOST_CNG_HASH_PASS actual_bcrypt_provider IAT_and_exact_six_GPA original_SHA256_abc inherited_reusable_flag two_finish_rounds actual_external_Heap_buffer live_hash_free_refused Destroy_success_then_original_free actual_destroy_close no_private_hash_byte_undo\n");
    fflush(stdout);return JNI_TRUE;
}
typedef HANDLE (WINAPI *LoadTxCreate)(LPSECURITY_ATTRIBUTES,LPGUID,DWORD,DWORD,DWORD,DWORD,LPWSTR);
typedef HANDLE (WINAPI *LoadTxOpen)(DWORD,LPGUID);
typedef BOOL (WINAPI *LoadTxId)(HANDLE,LPGUID);
typedef BOOL (WINAPI *LoadTxInformation)(HANDLE,PDWORD,PDWORD,PDWORD,PDWORD,DWORD,LPWSTR);
typedef BOOL (WINAPI *LoadTxRollback)(HANDLE);
typedef HANDLE (WINAPI *LoadTxFile)(LPCWSTR,DWORD,DWORD,LPSECURITY_ATTRIBUTES,DWORD,DWORD,HANDLE,HANDLE,PUSHORT,PVOID);
typedef BOOL (WINAPI *LoadTxRead)(HANDLE,LPVOID,DWORD,LPDWORD,LPOVERLAPPED);
typedef BOOL (WINAPI *LoadTxWrite)(HANDLE,LPCVOID,DWORD,LPDWORD,LPOVERLAPPED);
typedef BOOL (WINAPI *LoadTxSeek)(HANDLE,LARGE_INTEGER,PLARGE_INTEGER,DWORD);
typedef BOOL (WINAPI *LoadTxFileOnly)(HANDLE);
extern LoadTxCreate __imp_CreateTransaction;
extern LoadTxId __imp_GetTransactionId;
extern LoadTxInformation __imp_GetTransactionInformation;
extern LoadTxRollback __imp_RollbackTransaction;
extern LoadTxRollback __imp_CommitTransactionAsync;
extern LoadTxRead __imp_ReadFile;
extern LoadTxWrite __imp_WriteFile;
extern LoadTxSeek __imp_SetFilePointerEx;
extern LoadTxFileOnly __imp_SetEndOfFile;
extern LoadTxFileOnly __imp_FlushFileBuffers;
/* This entry is loaded by the real system loader from a distinct copy of this
 * DLL before Core binding. A deeper Core frame cannot authorize its image. */
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_AuditFixCheck_hostTransactionForeign0(JNIEnv *env,jclass check,jlong actual){
    (void)check;
    if(!actual||!load_private_entry((void*)__imp_RollbackTransaction))
        return load_thread_fail(env,"TX_FOREIGN_ACTUAL_IAT_REQUIRED")?JNI_TRUE:JNI_FALSE;
    SetLastError(0);BOOL result=RollbackTransaction((HANDLE)(uintptr_t)actual);DWORD error=GetLastError();
    if(result||error!=ERROR_ACCESS_DENIED){
        fprintf(stderr,"HOST_TRANSACTION_FOREIGN_FAILED result=%d win32=%lu\n",result!=FALSE,(unsigned long)error);
        return load_thread_fail(env,"TX_FOREIGN_KNOWN_TRANSACTION_MUTATION_ADMITTED")?JNI_TRUE:JNI_FALSE;
    }
    fprintf(stdout,"HOST_TRANSACTION_FOREIGN_PASS actual_distinct_system_loader_image known_transaction_rollback_refused deep_Core_frame_grants_no_image_role\n");
    fflush(stdout);return JNI_TRUE;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_AuditFixCheck_hostTransactionState0(JNIEnv *env,jclass check,jstring tempFile){
    HMODULE provider=NULL,kernel=GetModuleHandleW(L"kernel32.dll");wchar_t *path=NULL;
    HANDLE transaction=INVALID_HANDLE_VALUE,query=INVALID_HANDLE_VALUE,file=INVALID_HANDLE_VALUE,alias=NULL,moved=NULL,heap=NULL;
    BYTE *input=NULL,*output=NULL;const char *failure=NULL;DWORD failureError=0;int rolledBack=0;
    LoadTxRollback rollback=NULL;LoadClose close=NULL;LoadMemoryTarget protectedOutput={0};int protectedObserved=0;
    if((*env)->ExceptionCheck(env))return JNI_FALSE;
    if(!tempFile||!kernel){failure="TX_ACTUAL_TEMP_FILE_PATH_REQUIRED";goto done;}
    jsize length=(*env)->GetStringLength(env,tempFile);
    if(length<=0||length>=32767){failure="TX_ACTUAL_TEMP_FILE_PATH_LENGTH";goto done;}
    path=(wchar_t*)calloc((size_t)length+1,sizeof(*path));
    if(!path){failure="TX_ACTUAL_TEMP_FILE_PATH_STORAGE";goto done;}
    (*env)->GetStringRegion(env,tempFile,0,length,(jchar*)path);if((*env)->ExceptionCheck(env))goto done;
    provider=LoadLibraryExW(L"KtmW32.dll",NULL,LOAD_LIBRARY_SEARCH_SYSTEM32);
    if(!provider){failure="TX_ACTUAL_KTM_PROVIDER_LOAD";goto done;}
    const char *transactionNames[]={"CreateTransaction","OpenTransaction","GetTransactionId","GetTransactionInformation","RollbackTransaction"};
    const char *fileNames[]={"CreateFileTransactedW","ReadFile","WriteFile","SetFilePointerEx","SetEndOfFile","FlushFileBuffers","DuplicateHandle","CloseHandle"};
    void *transactionEntries[5]={0},*fileEntries[8]={0};
    for(unsigned i=0;i<5;i++){transactionEntries[i]=(void*)GetProcAddress(provider,transactionNames[i]);
        if(!load_private_entry(transactionEntries[i])){failure="TX_ACTUAL_KTM_EXACT_GPA_NOT_CONTROLLED";goto done;}}
    for(unsigned i=0;i<8;i++){fileEntries[i]=(void*)GetProcAddress(kernel,fileNames[i]);
        if(!load_private_entry(fileEntries[i])){failure="TX_ACTUAL_FILE_EXACT_GPA_NOT_CONTROLLED";goto done;}}
    void *imports[]={(void*)__imp_CreateTransaction,(void*)__imp_GetTransactionId,(void*)__imp_GetTransactionInformation,
        (void*)__imp_RollbackTransaction,(void*)__imp_ReadFile,(void*)__imp_WriteFile,(void*)__imp_SetFilePointerEx,
        (void*)__imp_SetEndOfFile,(void*)__imp_FlushFileBuffers,(void*)__imp_DuplicateHandle,(void*)__imp_CloseHandle};
    for(unsigned i=0;i<sizeof(imports)/sizeof(imports[0]);i++)if(!load_private_entry(imports[i])){
        failure="TX_ACTUAL_KTM_OR_FILE_IAT_NOT_CONTROLLED";goto done;}
    LoadTxOpen open=(LoadTxOpen)transactionEntries[1];LoadTxId id=(LoadTxId)transactionEntries[2];
    LoadTxInformation information=(LoadTxInformation)transactionEntries[3];rollback=(LoadTxRollback)transactionEntries[4];
    LoadTxFile createFile=(LoadTxFile)fileEntries[0];LoadTxRead read=(LoadTxRead)fileEntries[1];
    LoadTxSeek seek=(LoadTxSeek)fileEntries[3];LoadTxFileOnly flush=(LoadTxFileOnly)fileEntries[5];
    LoadDuplicate duplicate=(LoadDuplicate)fileEntries[6];close=(LoadClose)fileEntries[7];
    transaction=CreateTransaction(NULL,NULL,0,0,0,30000,L"Ronova actual Core rollback fixture");
    if(transaction==INVALID_HANDLE_VALUE){failure="TX_ORIGINAL_CREATE";goto done;}
    GUID original={0},reopened={0};DWORD outcome=0,timeout=0;
    if(!id(transaction,&original)){failure="TX_ORIGINAL_GUID";goto done;}
    query=open(TRANSACTION_QUERY_INFORMATION,&original);
    if(query==INVALID_HANDLE_VALUE||!GetTransactionId(query,&reopened)||memcmp(&original,&reopened,sizeof(original))){
        failure="TX_ORIGINAL_OPEN_SAME_GUID";goto done;}
    if(!information(query,&outcome,NULL,NULL,&timeout,0,NULL)||outcome!=TransactionOutcomeUndetermined){
        failure="TX_ORIGINAL_QUERY_ACTIVE";goto done;}
    if(!load_private_entry((void*)__imp_CommitTransactionAsync)){failure="TX_ASYNC_ACTUAL_IAT_REQUIRED";goto done;}
    SetLastError(0);
    if(CommitTransactionAsync(query)||GetLastError()!=ERROR_ACCESS_DENIED){failure="TX_ASYNC_QUERY_ONLY_ACCESS_ORIGINAL_ERROR";goto done;}
    outcome=0;
    if(!information(query,&outcome,NULL,NULL,NULL,0,NULL)||outcome!=TransactionOutcomeUndetermined){
        failure="TX_FAILED_ASYNC_QUERY_RECONCILIATION";goto done;}
    jmethodID foreign=(*env)->GetStaticMethodID(env,check,"hostTransactionForeign","(J)Z");
    if(!foreign||!(*env)->CallStaticBooleanMethod(env,check,foreign,(jlong)(uintptr_t)transaction)||(*env)->ExceptionCheck(env)){
        failure="TX_ACTUAL_FOREIGN_CALLER_CALLBACK";goto done;}
    outcome=0;
    if(!GetTransactionInformation(transaction,&outcome,NULL,NULL,NULL,0,NULL)||outcome!=TransactionOutcomeUndetermined){
        failure="TX_FOREIGN_REFUSAL_CHANGED_ORIGINAL_TRANSACTION";goto done;}
    heap=HeapCreate(0,0,0);if(heap){input=(BYTE*)HeapAlloc(heap,0,64);output=(BYTE*)HeapAlloc(heap,0,64);}
    if(!input||!output){failure="TX_ACTUAL_HEAP_IO_BUFFERS";goto done;}
    for(unsigned i=0;i<32;i++)input[i]=(BYTE)(0x31+i);memset(output,0xa5,64);
    file=createFile(path,GENERIC_READ|GENERIC_WRITE,0,NULL,OPEN_EXISTING,FILE_ATTRIBUTE_NORMAL,NULL,transaction,NULL,NULL);
    if(file==INVALID_HANDLE_VALUE){failure="TX_ORIGINAL_TRANSACTED_FILE_OPEN";goto done;}
    DWORD count=0;LARGE_INTEGER zero={0},position={0},end={0};end.QuadPart=32;
    if(!WriteFile(file,input,32,&count,NULL)||count!=32){failure="TX_ORIGINAL_SYNCHRONOUS_WRITE";goto done;}
    if(!seek(file,zero,&position,FILE_BEGIN)||position.QuadPart){failure="TX_ORIGINAL_SEEK_BEGIN";goto done;}
    if(!DuplicateHandle(GetCurrentProcess(),file,GetCurrentProcess(),&alias,0,FALSE,DUPLICATE_SAME_ACCESS)){
        failure="TX_ORIGINAL_FILE_ALIAS";goto done;}
    count=0;
    if(!ReadFile(alias,output,12,&count,NULL)||count!=12||memcmp(input,output,12)
            ||!SetFilePointerEx(file,zero,&position,FILE_CURRENT)||position.QuadPart!=12){
        failure="TX_FILE_ALIAS_SHARED_POSITION";goto done;}
    if(!seek(alias,zero,NULL,FILE_BEGIN)){failure="TX_ORIGINAL_ALIAS_SEEK_OPTIONAL_OUTPUT";goto done;}
    count=0;
    if(!read(file,output,32,&count,NULL)||count!=32||memcmp(input,output,32)){
        failure="TX_ORIGINAL_SYNCHRONOUS_READBACK";goto done;}
    if(!SetFilePointerEx(alias,end,NULL,FILE_BEGIN)||!SetEndOfFile(file)||!flush(alias)){
        failure="TX_ORIGINAL_TRUNCATE_FLUSH";goto done;}
    JavaVM *vm=NULL;
    if((*env)->GetJavaVM(env,&vm)!=JNI_OK||!vm||!*vm
            ||!load_memory_target(&protectedOutput,(void*)&(*vm)->GetEnv,sizeof((*vm)->GetEnv),MEM_PRIVATE,(void*)*vm)){
        failure="TX_ACTUAL_PROTECTED_VM_SHADOW_OUTPUT";goto done;}
    protectedObserved=1;
    /* Copy the slot's current bytes into our own file first. A broken admission
     * cannot replace the live function pointer with a different value. */
    memcpy(input,protectedOutput.before,protectedOutput.bytes);count=0;
    if(!seek(file,zero,NULL,FILE_BEGIN)||!WriteFile(file,input,(DWORD)protectedOutput.bytes,&count,NULL)
            ||count!=protectedOutput.bytes||!seek(file,zero,NULL,FILE_BEGIN)){
        failure="TX_PROTECTED_OUTPUT_SAME_VALUE_INPUT";goto done;}
    count=0x524e5458;SetLastError(0);
    BOOL accepted=read(alias,protectedOutput.address,(DWORD)protectedOutput.bytes,&count,NULL);DWORD refusedError=GetLastError();
    if(accepted||refusedError!=ERROR_ACCESS_DENIED||count!=0x524e5458||!load_memory_unchanged(&protectedOutput)){
        failure="TX_PROTECTED_READ_OUTPUT_NOT_REFUSED_BEFORE_ORIGINAL";goto done;}
    if(!SetFilePointerEx(file,zero,&position,FILE_CURRENT)||position.QuadPart){
        failure="TX_PROTECTED_READ_REFUSAL_ADVANCED_SHARED_POSITION";goto done;}
    if(!CloseHandle(alias)){failure="TX_ORIGINAL_ALIAS_CLOSE";goto done;}alias=NULL;
    BOOL transferred=duplicate(GetCurrentProcess(),file,GetCurrentProcess(),&moved,0,FALSE,DUPLICATE_SAME_ACCESS|DUPLICATE_CLOSE_SOURCE);
    /* DUPLICATE_CLOSE_SOURCE closes the original even when duplication fails. */
    file=INVALID_HANDLE_VALUE;
    if(!transferred||!moved){failure="TX_ORIGINAL_CLOSE_SOURCE_DUPLICATE";goto done;}
    if(!FlushFileBuffers(moved)||!close(moved)){failure="TX_ORIGINAL_TRANSFERRED_FILE_FLUSH_CLOSE";goto done;}moved=NULL;
    if(!rollback(transaction)){failure="TX_ORIGINAL_SYNCHRONOUS_ROLLBACK";goto done;}rolledBack=1;
    outcome=0;
    if(!GetTransactionInformation(query,&outcome,NULL,NULL,NULL,0,NULL)||outcome!=TransactionOutcomeAborted){
        failure="TX_REOPENED_QUERY_DID_NOT_SHARE_ROLLBACK_OUTCOME";goto done;}
done:
    failureError=GetLastError();
    if(alias&&!CloseHandle(alias)&&!failure){failure="TX_ALIAS_CLEANUP_CLOSE";failureError=GetLastError();}
    if(file!=INVALID_HANDLE_VALUE&&!CloseHandle(file)&&!failure){failure="TX_FILE_CLEANUP_CLOSE";failureError=GetLastError();}
    if(moved&&!CloseHandle(moved)&&!failure){failure="TX_TRANSFER_CLEANUP_CLOSE";failureError=GetLastError();}
    if(transaction!=INVALID_HANDLE_VALUE&&!rolledBack&&rollback&&!rollback(transaction)&&!failure){failure="TX_CLEANUP_ROLLBACK";failureError=GetLastError();}
    if(query!=INVALID_HANDLE_VALUE&&!CloseHandle(query)&&!failure){failure="TX_QUERY_HANDLE_CLOSE";failureError=GetLastError();}
    if(transaction!=INVALID_HANDLE_VALUE&&!CloseHandle(transaction)&&!failure){failure="TX_ORIGINAL_TRANSACTION_CLOSE";failureError=GetLastError();}
    if(input){if(HeapFree(heap,0,input))input=NULL;else if(!failure){failure="TX_INPUT_BUFFER_RETURN";failureError=GetLastError();}}
    if(output){if(HeapFree(heap,0,output))output=NULL;else if(!failure){failure="TX_OUTPUT_BUFFER_RETURN";failureError=GetLastError();}}
    if(heap&&!input&&!output&&!HeapDestroy(heap)&&!failure){failure="TX_ACTUAL_HEAP_DESTROY";failureError=GetLastError();}
    if(provider&&!FreeLibrary(provider)&&!failure){failure="TX_KTM_APPLICATION_REFERENCE_RETURN";failureError=GetLastError();}
    free(path);
    if(protectedObserved&&!load_memory_unchanged(&protectedOutput)){
        fprintf(stderr,"HOST_TRANSACTION_FAILED protected_VM_shadow_changed\n");fflush(stderr);return JNI_FALSE;
    }
    if((*env)->ExceptionCheck(env)&&!failure)failure="TX_UNEXPECTED_JNI_EXCEPTION";
    if(failure){fprintf(stderr,"HOST_TRANSACTION_FAILED stage=%s win32=%lu\n",failure,(unsigned long)failureError);
        return load_thread_fail(env,failure)?JNI_TRUE:JNI_FALSE;}
    fprintf(stdout,"HOST_TRANSACTION_PASS actual_KtmW32_kernel32 IAT_and_exact_GPA actual_GUID_Open_query_alias failed_async_original_access_error query_reconciles_active foreign_image_known_transaction_mutation_refused observed_Heap_IO_buffers synchronous_write_seek_readback_truncate_flush file_alias_shared_position protected_ReadFile_output_refused_without_position_change original_CloseSource_duplicate original_alias_close synchronous_Rollback_shared_outcome no_commit_permit_bypass\n");
    fflush(stdout);return JNI_TRUE;
}
typedef BOOL (WINAPI *LoadTxResult)(HANDLE,LPOVERLAPPED,LPDWORD,BOOL);
typedef BOOL (WINAPI *LoadTxResultEx)(HANDLE,LPOVERLAPPED,LPDWORD,DWORD,BOOL);
typedef BOOL (WINAPI *LoadTxCancel)(HANDLE,LPOVERLAPPED);
typedef HANDLE (WINAPI *LoadTxPort)(HANDLE,HANDLE,ULONG_PTR,DWORD);
typedef BOOL (WINAPI *LoadTxQueue)(HANDLE,LPDWORD,PULONG_PTR,LPOVERLAPPED*,DWORD);
typedef BOOL (WINAPI *LoadTxQueueEx)(HANDLE,LPOVERLAPPED_ENTRY,ULONG,PULONG,DWORD,BOOL);
typedef BOOL (WINAPI *LoadTxPost)(HANDLE,DWORD,ULONG_PTR,LPOVERLAPPED);
typedef BOOL (WINAPI *LoadTxNotify)(HANDLE,UCHAR);
extern LoadTxResult __imp_GetOverlappedResult;
extern LoadTxCancel __imp_CancelIoEx;
extern LoadTxPort __imp_CreateIoCompletionPort;
extern LoadTxQueue __imp_GetQueuedCompletionStatus;
extern LoadTxPost __imp_PostQueuedCompletionStatus;
extern LoadTxNotify __imp_SetFileCompletionNotificationModes;
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_AuditFixCheck_hostTransactionAsyncForeign0(JNIEnv *env,jclass check,jlong file,jlong overlapped){
    (void)check;
    if(!file||!overlapped||!load_private_entry((void*)__imp_GetOverlappedResult)||!load_private_entry((void*)__imp_CancelIoEx))
        return load_thread_fail(env,"TX_ASYNC_FOREIGN_ACTUAL_IAT_REQUIRED")?JNI_TRUE:JNI_FALSE;
    DWORD count=0x524e4153;SetLastError(0);
    BOOL result=GetOverlappedResult((HANDLE)(uintptr_t)file,(LPOVERLAPPED)(uintptr_t)overlapped,&count,FALSE);DWORD error=GetLastError();
    if(result||error!=ERROR_ACCESS_DENIED||count!=0x524e4153)
        return load_thread_fail(env,"TX_ASYNC_FOREIGN_KNOWN_COMPLETION_ADMITTED")?JNI_TRUE:JNI_FALSE;
    SetLastError(0);result=CancelIoEx((HANDLE)(uintptr_t)file,(LPOVERLAPPED)(uintptr_t)overlapped);error=GetLastError();
    if(result||error!=ERROR_ACCESS_DENIED)
        return load_thread_fail(env,"TX_ASYNC_FOREIGN_KNOWN_CANCEL_ADMITTED")?JNI_TRUE:JNI_FALSE;
    fprintf(stdout,"HOST_TRANSACTION_ASYNC_FOREIGN_PASS actual_distinct_image known_completion_and_cancel_refused deep_Core_frame_grants_no_role\n");
    fflush(stdout);return JNI_TRUE;
}
/* A pending return can race actual completion. Successful free is accepted only
 * when a real nowait result already proves that this request is terminal. */
static int load_tx_async_buffer_probe(HANDLE heap,BYTE **buffer,HANDLE file,LPOVERLAPPED op,
        LoadTxResult result,DWORD expected,int allowCancelled,DWORD *error){
    if(!HeapFree(heap,0,*buffer))return 1;
    *buffer=NULL;DWORD count=0;BOOL completed=result(file,op,&count,FALSE);*error=GetLastError();
    if(completed&&count==expected)return 2;
    if(!completed&&allowCancelled&&*error==ERROR_OPERATION_ABORTED)return 3;
    return 0;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_AuditFixCheck_hostTransactionAsync0(JNIEnv *env,jclass check,jstring tempFile){
    const DWORD bytes=8u*1024u*1024u,readBytes=8u*1024u*1024u-16u,fakeBytes=0x524e4153;
    const ULONG_PTR key=(ULONG_PTR)UINT64_C(0x524f4e4f5641494f),sentinelKey=(ULONG_PTR)UINT64_C(0x53454e54494e454c);
    HANDLE transaction=INVALID_HANDLE_VALUE,file=INVALID_HANDLE_VALUE,port=NULL,portAlias=NULL,heap=NULL,eventA=NULL,eventB=NULL;
    BYTE *input=NULL,*output=NULL;OVERLAPPED *opA=NULL,*opB=NULL;wchar_t *path=NULL;const char *failure=NULL;DWORD error=0;
    unsigned pendingStarts=0,heldProbes=0,completedBeforeProbe=0,forgedInternal=0,incomplete=0,oldAcrossGeneration=0;
    int activeA=0,activeB=0,seedPacketQueued=0,cancelled=0,rolledBack=0;unsigned generationA=0;
    LoadTxResult result=NULL;LoadTxResultEx resultEx=NULL;LoadTxCancel cancel=NULL;LoadTxQueue queue=NULL;
    LoadTxQueueEx queueEx=NULL;LoadTxPost post=NULL;LoadTxRollback rollback=NULL;LoadMemoryTarget protectedOutput={0};int protectedObserved=0;
    if((*env)->ExceptionCheck(env))return JNI_FALSE;
    HMODULE kernel=GetModuleHandleW(L"kernel32.dll"),ktm=GetModuleHandleW(L"KtmW32.dll");
    if(!tempFile||!kernel||!ktm){failure="TX_ASYNC_ACTUAL_PATH_AND_PROVIDERS";goto done;}
    jsize length=(*env)->GetStringLength(env,tempFile);
    if(length<=0||length>=32767||(path=(wchar_t*)calloc((size_t)length+1,sizeof(*path)))==NULL){failure="TX_ASYNC_ACTUAL_PATH_STORAGE";goto done;}
    (*env)->GetStringRegion(env,tempFile,0,length,(jchar*)path);if((*env)->ExceptionCheck(env))goto done;
    const char *names[]={"GetOverlappedResult","GetOverlappedResultEx","CancelIoEx","CreateIoCompletionPort",
        "GetQueuedCompletionStatus","GetQueuedCompletionStatusEx","PostQueuedCompletionStatus","SetFileCompletionNotificationModes"};
    void *entries[8]={0};
    for(unsigned i=0;i<8;i++){entries[i]=(void*)GetProcAddress(kernel,names[i]);
        if(!load_private_entry(entries[i])){failure="TX_ASYNC_EIGHT_EXACT_GPA_NOT_CONTROLLED";goto done;}}
    void *imports[]={(void*)__imp_GetOverlappedResult,(void*)__imp_CancelIoEx,(void*)__imp_CreateIoCompletionPort,
        (void*)__imp_GetQueuedCompletionStatus,(void*)__imp_PostQueuedCompletionStatus,(void*)__imp_SetFileCompletionNotificationModes,
        (void*)__imp_ReadFile,(void*)__imp_WriteFile,(void*)__imp_CreateTransaction,(void*)__imp_CloseHandle};
    for(unsigned i=0;i<sizeof(imports)/sizeof(imports[0]);i++)if(!load_private_entry(imports[i])){
        failure="TX_ASYNC_ACTUAL_IAT_NOT_CONTROLLED";goto done;}
    result=(LoadTxResult)entries[0];resultEx=(LoadTxResultEx)entries[1];cancel=(LoadTxCancel)entries[2];
    LoadTxPort createPort=(LoadTxPort)entries[3];queue=(LoadTxQueue)entries[4];queueEx=(LoadTxQueueEx)entries[5];post=(LoadTxPost)entries[6];
    LoadTxFile createFile=(LoadTxFile)GetProcAddress(kernel,"CreateFileTransactedW");rollback=(LoadTxRollback)GetProcAddress(ktm,"RollbackTransaction");
    if(!load_private_entry((void*)createFile)||!load_private_entry((void*)rollback)){failure="TX_ASYNC_ACTUAL_FILE_AND_ROLLBACK_GPA";goto done;}
    JavaVM *vm=NULL;
    if((*env)->GetJavaVM(env,&vm)!=JNI_OK||!vm||!*vm
            ||!load_memory_target(&protectedOutput,(void*)&(*vm)->GetEnv,sizeof((*vm)->GetEnv),MEM_PRIVATE,(void*)*vm)){
        failure="TX_ASYNC_ACTUAL_CONTROL_OUTPUT";goto done;}
    protectedObserved=1;
    heap=HeapCreate(0,0,0);
    if(heap){input=(BYTE*)HeapAlloc(heap,0,bytes);output=(BYTE*)HeapAlloc(heap,0,bytes);
        opA=(OVERLAPPED*)HeapAlloc(heap,HEAP_ZERO_MEMORY,sizeof(*opA));opB=(OVERLAPPED*)HeapAlloc(heap,HEAP_ZERO_MEMORY,sizeof(*opB));}
    eventA=CreateEventW(NULL,TRUE,FALSE,NULL);eventB=CreateEventW(NULL,TRUE,FALSE,NULL);
    if(!input||!output||!opA||!opB||!eventA||!eventB){failure="TX_ASYNC_ACTUAL_HEAP_BUFFERS_OV_AND_MANUAL_EVENTS";goto done;}
    for(DWORD i=0;i<bytes;i++)input[i]=(BYTE)(17u+37u*i);memcpy(input,protectedOutput.before,protectedOutput.bytes);
    memset(output,0xa5,bytes);opA->hEvent=eventA;opB->hEvent=eventB;
    transaction=CreateTransaction(NULL,NULL,0,0,0,30000,L"Ronova actual Core asynchronous rollback fixture");
    if(transaction==INVALID_HANDLE_VALUE){failure="TX_ASYNC_ORIGINAL_CREATE";goto done;}
    file=createFile(path,GENERIC_READ|GENERIC_WRITE,0,NULL,OPEN_EXISTING,FILE_ATTRIBUTE_NORMAL|FILE_FLAG_OVERLAPPED,NULL,transaction,NULL,NULL);
    if(file==INVALID_HANDLE_VALUE){failure="TX_ASYNC_ORIGINAL_OVERLAPPED_FILE";goto done;}
    port=CreateIoCompletionPort(INVALID_HANDLE_VALUE,NULL,0,1);
    if(!port||!DuplicateHandle(GetCurrentProcess(),port,GetCurrentProcess(),&portAlias,0,FALSE,DUPLICATE_SAME_ACCESS)
            ||createPort(file,port,key,0)!=port||!SetFileCompletionNotificationModes(file,FILE_SKIP_SET_EVENT_ON_HANDLE)){
        failure="TX_ASYNC_REAL_IOCP_ALIAS_ASSOCIATION_AND_NOTIFICATION";goto done;}
    if(!PostQueuedCompletionStatus(portAlias,fakeBytes,sentinelKey,opA)){failure="TX_ASYNC_ORIGINAL_SENTINEL_POST";goto done;}
    OVERLAPPED_ENTRY packet={0};ULONG removed=0;
    if(!queueEx(port,&packet,1,&removed,5000,FALSE)||removed!=1||packet.lpOverlapped!=opA
            ||packet.dwNumberOfBytesTransferred!=fakeBytes||packet.lpCompletionKey!=sentinelKey){
        failure="TX_ASYNC_SENTINEL_ORIGINAL_PUBLIC_VALUES";goto done;}
    DWORD count=0;SetLastError(0);BOOL started=WriteFile(file,input,bytes,&count,opA);error=GetLastError();
    if(!started&&error!=ERROR_IO_PENDING){failure="TX_ASYNC_ORIGINAL_LARGE_WRITE";goto done;}
    activeA=1;generationA=1;seedPacketQueued=1;if(!started)pendingStarts++;
    jmethodID foreign=(*env)->GetStaticMethodID(env,check,"hostTransactionAsyncForeign","(JJ)Z");
    if(!foreign||!(*env)->CallStaticBooleanMethod(env,check,foreign,(jlong)(uintptr_t)file,(jlong)(uintptr_t)opA)||(*env)->ExceptionCheck(env)){
        failure="TX_ASYNC_ACTUAL_FOREIGN_COMPLETION_CANCEL";goto done;}
    /* IOCP can defer the status-block update until dequeue. Never wait here
     * for a result that requires this same thread to dequeue its packet. */
    count=0;BOOL completed=resultEx(file,opA,&count,0,FALSE);error=GetLastError();
    if(completed&&count!=bytes){failure="TX_ASYNC_ORIGINAL_WRITE_RESULT_COUNT";goto done;}
    if(completed&&started)activeA=0;
    else {
        if(!completed){if(error!=ERROR_IO_INCOMPLETE&&error!=WAIT_TIMEOUT){failure="TX_ASYNC_ORIGINAL_WRITE_NOWAIT_RESULT";goto done;}incomplete++;}
        DWORD got=0;ULONG_PTR gotKey=0;LPOVERLAPPED gotOp=NULL;
        if(!GetQueuedCompletionStatus(portAlias,&got,&gotKey,&gotOp,10000)||gotOp!=opA||gotKey!=key||got!=bytes){
            failure="TX_ASYNC_ORIGINAL_WRITE_REAL_PACKET";goto done;}
        seedPacketQueued=0;activeA=0;
        if(!resultEx(file,opA,&count,0,FALSE)||count!=bytes){failure="TX_ASYNC_WRITE_RESULT_AFTER_PACKET";goto done;}
    }
    OVERLAPPED before=*opB;count=0x524e4153;SetLastError(0);
    BOOL admitted=ReadFile(file,protectedOutput.address,(DWORD)protectedOutput.bytes,&count,opB);error=GetLastError();
    if(admitted||error!=ERROR_ACCESS_DENIED||count!=0x524e4153||memcmp(&before,opB,sizeof(before))||!load_memory_unchanged(&protectedOutput)){
        failure="TX_ASYNC_PROTECTED_OUTPUT_CHANGED_OR_ORIGINAL_ISSUED";goto done;}
    memset(opA,0,sizeof(*opA));opA->hEvent=eventA;opA->Offset=16;ResetEvent(eventA);
    count=0;SetLastError(0);started=ReadFile(file,output,readBytes,&count,opA);error=GetLastError();
    if(!started&&error!=ERROR_IO_PENDING){failure="TX_ASYNC_ORIGINAL_REUSED_OV_READ";goto done;}
    activeA=1;generationA=2;int readPending=!started;if(readPending)pendingStarts++;
    if(seedPacketQueued){
        DWORD got=0;ULONG_PTR gotKey=0;LPOVERLAPPED gotOp=NULL;
        if(!queue(portAlias,&got,&gotKey,&gotOp,10000)||gotOp!=opA||gotKey!=key||got!=bytes
                ||opA->Offset!=16||opA->OffsetHigh||opA->hEvent!=eventA){
            failure="TX_ASYNC_OLD_PACKET_RETIRED_OR_OVERWROTE_NEW_GENERATION";goto done;}
        seedPacketQueued=0;oldAcrossGeneration=1;
    }
    if(readPending){
        // Do not overwrite a status that the real completion already published.
        if(InterlockedCompareExchange64((volatile LONG64*)&opA->Internal,0,STATUS_PENDING)==STATUS_PENDING)forgedInternal++;
        int probe=load_tx_async_buffer_probe(heap,&output,file,opA,result,readBytes,0,&error);
        if(!probe){failure="TX_ASYNC_PUBLIC_STATUS_OR_OLD_PACKET_RELEASED_STILL_PENDING_BUFFER";goto done;}
        if(probe==1)heldProbes++;else {completedBeforeProbe++;activeA=0;}
        count=0;completed=result(file,opA,&count,FALSE);error=GetLastError();
        if(completed){if(count!=readBytes){failure="TX_ASYNC_FORGED_PUBLIC_INTERNAL_BECAME_RESULT";goto done;}activeA=0;}
        else if(error==ERROR_IO_INCOMPLETE||error==WAIT_TIMEOUT)incomplete++;
        else {failure="TX_ASYNC_REUSED_OV_NOWAIT_RESULT";goto done;}
        if(!post(portAlias,fakeBytes,key,opA)){failure="TX_ASYNC_PUBLIC_OV_FAKE_POST";goto done;}
    }
    int sawRead=0,sawFake=!readPending;
    for(unsigned i=0;i<(readPending?2u:1u);i++){
        memset(&packet,0,sizeof(packet));removed=0;
        if(!queueEx(port,&packet,1,&removed,10000,FALSE)||removed!=1||packet.lpOverlapped!=opA||packet.lpCompletionKey!=key){
            failure="TX_ASYNC_REUSED_OV_REAL_OR_PUBLIC_POST_PACKET";goto done;}
        if(packet.dwNumberOfBytesTransferred==fakeBytes&&!sawFake){
            sawFake=1;
            if(activeA&&output){int probe=load_tx_async_buffer_probe(heap,&output,file,opA,result,readBytes,0,&error);
                if(!probe){failure="TX_ASYNC_PUBLIC_POST_RELEASED_STILL_PENDING_BUFFER";goto done;}
                if(probe==1)heldProbes++;else {completedBeforeProbe++;activeA=0;}}
        }else if(packet.dwNumberOfBytesTransferred==readBytes&&!sawRead){sawRead=1;activeA=0;}
        else {failure="TX_ASYNC_PACKET_COUNT_OR_GENERATION_CHANGED";goto done;}
    }
    count=0;
    if(!sawRead||!sawFake||!resultEx(file,opA,&count,0,FALSE)||count!=readBytes||output&&memcmp(output,input+16,readBytes)){
        failure="TX_ASYNC_REAL_READ_COMPLETION_OR_BYTES";goto done;}
    if(!output)output=(BYTE*)HeapAlloc(heap,0,bytes);
    if(!output){failure="TX_ASYNC_CANCEL_ACTUAL_OUTPUT_STORAGE";goto done;}
    memset(opB,0,sizeof(*opB));opB->hEvent=eventB;ResetEvent(eventB);count=0;SetLastError(0);
    started=ReadFile(file,output,bytes,&count,opB);error=GetLastError();
    if(!started&&error!=ERROR_IO_PENDING){failure="TX_ASYNC_ORIGINAL_CANCEL_CANDIDATE_READ";goto done;}
    activeB=1;int cancelPending=!started;if(cancelPending)pendingStarts++;
    SetLastError(0);BOOL requested=cancel(file,opB);DWORD cancelError=GetLastError();
    if(!requested&&cancelError!=ERROR_NOT_FOUND){failure="TX_ASYNC_ORIGINAL_CANCEL_REQUEST";goto done;}
    if(cancelPending){int probe=load_tx_async_buffer_probe(heap,&output,file,opB,result,bytes,1,&error);
        if(!probe){failure="TX_ASYNC_CANCEL_REQUEST_RELEASED_STILL_PENDING_BUFFER";goto done;}
        if(probe==1)heldProbes++;else {completedBeforeProbe++;activeB=0;if(probe==3)cancelled=1;}}
    DWORD got=0;ULONG_PTR gotKey=0;LPOVERLAPPED gotOp=NULL;SetLastError(0);
    BOOL dequeued=queue(portAlias,&got,&gotKey,&gotOp,10000);error=GetLastError();
    if(gotOp!=opB||gotKey!=key||!dequeued&&error!=ERROR_OPERATION_ABORTED||dequeued&&got!=bytes){
        failure="TX_ASYNC_CANCEL_REAL_PACKET_PUBLIC_RESULT";goto done;}
    activeB=0;cancelled=!dequeued;count=0;SetLastError(0);completed=resultEx(file,opB,&count,0,FALSE);error=GetLastError();
    if(cancelled?completed||error!=ERROR_OPERATION_ABORTED:!completed||count!=bytes||output&&memcmp(output,input,bytes)){
        failure="TX_ASYNC_CANCEL_RESULT_DID_NOT_MATCH_REAL_PACKET";goto done;}
    gotOp=(LPOVERLAPPED)(uintptr_t)1;SetLastError(0);
    if(queue(port,&got,&gotKey,&gotOp,0)||GetLastError()!=WAIT_TIMEOUT||gotOp!=NULL){failure="TX_ASYNC_EMPTY_QUEUE_TIMEOUT_BECAME_COMPLETION";goto done;}
done:
    error=GetLastError();
    if((activeA||activeB)&&file!=INVALID_HANDLE_VALUE&&cancel){
        if(activeA&&opA)cancel(file,opA);if(activeB&&opB)cancel(file,opB);
        // Bounded real dequeues only. Unresolved work retains its actual storage.
        for(unsigned i=0;i<6&&(activeA||activeB)&&queue&&port;i++){
            DWORD ignored=0;ULONG_PTR ignoredKey=0;LPOVERLAPPED ignoredOp=NULL;queue(port,&ignored,&ignoredKey,&ignoredOp,1000);
            if(activeA&&opA){DWORD actual=0;BOOL ready=resultEx(file,opA,&actual,0,FALSE);DWORD why=GetLastError();
                if(ready||why==ERROR_OPERATION_ABORTED)activeA=0;}
            if(activeB&&opB){DWORD actual=0;BOOL ready=resultEx(file,opB,&actual,0,FALSE);DWORD why=GetLastError();
                if(ready||why==ERROR_OPERATION_ABORTED)activeB=0;}
        }
    }
    if(!activeA&&!activeB){
        if(file!=INVALID_HANDLE_VALUE){if(CloseHandle(file))file=INVALID_HANDLE_VALUE;else if(!failure){failure="TX_ASYNC_COMPLETED_FILE_CLOSE_BUSY";error=GetLastError();}}
        if(portAlias&&!CloseHandle(portAlias)&&!failure){failure="TX_ASYNC_COMPLETED_PORT_ALIAS_CLOSE";error=GetLastError();}
        if(port&&!CloseHandle(port)&&!failure){failure="TX_ASYNC_COMPLETED_PORT_CLOSE";error=GetLastError();}
        if(file==INVALID_HANDLE_VALUE&&transaction!=INVALID_HANDLE_VALUE&&rollback){
            if(rollback(transaction))rolledBack=1;else if(!failure){failure="TX_ASYNC_ORIGINAL_ROLLBACK";error=GetLastError();}}
        if(transaction!=INVALID_HANDLE_VALUE&&!CloseHandle(transaction)&&!failure){failure="TX_ASYNC_ORIGINAL_TRANSACTION_CLOSE";error=GetLastError();}
        if(input){if(HeapFree(heap,0,input))input=NULL;else if(!failure){failure="TX_ASYNC_COMPLETED_INPUT_FREE_BUSY";error=GetLastError();}}
        if(output){if(HeapFree(heap,0,output))output=NULL;else if(!failure){failure="TX_ASYNC_COMPLETED_OUTPUT_FREE_BUSY";error=GetLastError();}}
        if(opA){if(HeapFree(heap,0,opA))opA=NULL;else if(!failure){failure="TX_ASYNC_COMPLETED_OV_A_FREE_BUSY";error=GetLastError();}}
        if(opB){if(HeapFree(heap,0,opB))opB=NULL;else if(!failure){failure="TX_ASYNC_COMPLETED_OV_B_FREE_BUSY";error=GetLastError();}}
        if(eventA&&!CloseHandle(eventA)&&!failure){failure="TX_ASYNC_EVENT_A_CLOSE";error=GetLastError();}
        if(eventB&&!CloseHandle(eventB)&&!failure){failure="TX_ASYNC_EVENT_B_CLOSE";error=GetLastError();}
        if(heap&&!input&&!output&&!opA&&!opB&&!HeapDestroy(heap)&&!failure){failure="TX_ASYNC_ACTUAL_HEAP_DESTROY";error=GetLastError();}
    }else if(!failure)failure="TX_ASYNC_REAL_COMPLETION_STILL_UNRESOLVED";
    free(path);
    if(protectedObserved&&!load_memory_unchanged(&protectedOutput)){fprintf(stderr,"HOST_TRANSACTION_ASYNC_FAILED control_output_changed\n");fflush(stderr);return JNI_FALSE;}
    if((*env)->ExceptionCheck(env)&&!failure)failure="TX_ASYNC_UNEXPECTED_JNI_EXCEPTION";
    if(!failure&&!rolledBack)failure="TX_ASYNC_ORIGINAL_ROLLBACK_NOT_COMPLETED";
    if(failure){fprintf(stderr,"HOST_TRANSACTION_ASYNC_FAILED stage=%s win32=%lu activeA=%d activeB=%d generationA=%u seedPacket=%d\n",
            failure,(unsigned long)error,activeA,activeB,generationA,seedPacketQueued);return load_thread_fail(env,failure)?JNI_TRUE:JNI_FALSE;}
    fprintf(stdout,"HOST_TRANSACTION_ASYNC_PASS actual_Core_TxF_Heap_public_OV_private_system_completion IAT_exact_eight_GPA manual_events_IOCP_alias original_public_packet_values protected_output_foreign_completion_cancel_refused actual_8MiB_write_read_cancel rollback original_free_close pending_starts=%u held_probes=%u completed_before_probe=%u public_Internal_CAS=%u incomplete_or_timeout=%u old_packet_cross_generation=%s actual_cancel=%s pending_evidence=%s\n",
            pendingStarts,heldProbes,completedBeforeProbe,forgedInternal,incomplete,oldAcrossGeneration?"observed":"not_observed_first_generation_required_dequeue",
            cancelled?"operation_aborted":"completed_normally",pendingStarts?"actual_IO_PENDING_return":"pending_not_observed");
    fflush(stdout);return JNI_TRUE;
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_hostWin32Allocations0(JNIEnv *env,jclass check){
    (void)check;load_win32_allocation_check(env);
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_hostRawUnsafe0(JNIEnv *env,jclass check){
    JavaVM *vm=NULL;jvmtiEnv *api=NULL;
    if((*env)->GetJavaVM(env,&vm)!=JNI_OK||!vm
            ||(*vm)->GetEnv(vm,(void**)&api,JVMTI_VERSION_1_2)!=JNI_OK||!api){
        load_thread_fail(env,"RAW_UNSAFE_ACTUAL_VM_JVMTI_ENVIRONMENT_REQUIRED");return;
    }
    /* Only actual public first slots leave this synchronous fixture call. No
     * controller registration, source identity or replacement table is made. */
    jclass lifetimeFailure=(*env)->FindClass(env,"dev/ronova/pro/AuditFixCheck$RawUnsafeLifetimeFailure");
    jmethodID action=lifetimeFailure?(*env)->GetStaticMethodID(env,check,"rawUnsafeSlots","(JJJ)V"):NULL;
    if(action)(*env)->CallStaticVoidMethod(env,check,action,(jlong)(uintptr_t)vm,(jlong)(uintptr_t)api,(jlong)(uintptr_t)env);
    jthrowable failure=(*env)->ExceptionOccurred(env);
    if(failure)(*env)->ExceptionClear(env);
    if(failure&&lifetimeFailure&&(*env)->IsInstanceOf(env,failure,lifetimeFailure)){
        /* An unexpectedly accepted free/reallocation may have invalidated api.
         * Preserve that failure without dereferencing it during cleanup. */
        (*env)->DeleteLocalRef(env,lifetimeFailure);(*env)->Throw(env,failure);(*env)->DeleteLocalRef(env,failure);return;
    }
    jvmtiError disposed=(*api)->DisposeEnvironment(api);
    if(lifetimeFailure)(*env)->DeleteLocalRef(env,lifetimeFailure);
    if(failure){(*env)->Throw(env,failure);(*env)->DeleteLocalRef(env,failure);return;}
    if(!action||disposed!=JVMTI_ERROR_NONE)load_thread_fail(env,"RAW_UNSAFE_ACTUAL_ENVIRONMENT_DISPOSE_FAILED");
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_AuditFixCheck_hostThreadCreate0(JNIEnv *env,jclass check,jclass type,jobject payload){
    (void)check;JavaVM *vm=NULL;jclass actual=payload?(*env)->GetObjectClass(env,payload):NULL;
    int correct=actual&&type&&(*env)->IsSameObject(env,actual,type);if(actual)(*env)->DeleteLocalRef(env,actual);
    if(!correct||(*env)->ExceptionCheck(env)){load_thread_fail(env,"HOST_THREAD_ACTUAL_PAYLOAD_TYPE");return;}
    if((*env)->GetJavaVM(env,&vm)!=JNI_OK||!vm){load_thread_fail(env,"HOST_THREAD_ACTUAL_JAVA_VM");return;}
    void *imports[]={(void*)__imp_CreateThread,(void*)__imp_CreateRemoteThread,(void*)__imp_CreateRemoteThreadEx};
    for(unsigned i=0;i<sizeof(imports)/sizeof(imports[0]);i++)if(!load_private_entry(imports[i])){load_thread_fail(env,"HOST_THREAD_REAL_IMPORT_NOT_CONTROLLED");return;}
    const char *names[]={"CreateThread","current_process_CreateRemoteThread","current_process_CreateRemoteThreadEx"};
    HANDLE currentProcess=GetCurrentProcess();
    for(unsigned kind=0;kind<3;kind++){
        LoadThreadCase *state=load_thread_case_new();if(!state){load_thread_fail(env,"HOST_THREAD_NATIVE_USERDATA_ALLOCATION");return;}
        SetLastError(0x524e0054);
        if(kind==0)state->handle=CreateThread(NULL,0,load_thread_start,state,CREATE_SUSPENDED,&state->id);
        else if(kind==1)state->handle=CreateRemoteThread(currentProcess,NULL,0,load_thread_start,state,CREATE_SUSPENDED,&state->id);
        else state->handle=CreateRemoteThreadEx(currentProcess,NULL,0,load_thread_start,state,CREATE_SUSPENDED,NULL,&state->id);
        state->createError=GetLastError();int ready=load_thread_finish(state,names[kind]);
        load_thread_case_free(state);if(!ready){load_thread_fail(env,names[kind]);return;}
    }
    if(!load_crt_thread_check(env))return;
    load_thread_heap_check(env,vm,type,payload);
}
#endif

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm,void *reserved){
    (void)reserved;
#if defined(AUDIT_JVMTI_DISPATCH_CHECK) || defined(AUDIT_HOST_THREAD_CREATE_CHECK)
    /* Each independent command starts from its native method. */
    (void)vm;return JNI_VERSION_1_8;
#elif defined(AUDIT_HOST_LOAD_SUPPORT)
    (void)vm;return JNI_VERSION_1_8;
#else
    JNIEnv *env=NULL;if((*vm)->GetEnv(vm,(void**)&env,JNI_VERSION_1_8)!=JNI_OK)return JNI_ERR;
    HMODULE kernel=GetModuleHandleW(L"kernel32.dll");
    void *createA=kernel?(void*)GetProcAddress(kernel,"CreateProcessA"):NULL;
    void *createW=kernel?(void*)GetProcAddress(kernel,"CreateProcessW"):NULL;
    DWORD error=0;int flags=0;
    if(load_child(NULL,1,&error))flags|=1;
    if(load_child(NULL,0,&error))flags|=2;
    if(createA&&load_child(createA,1,&error))flags|=4;
    if(createW&&load_child(createW,0,&error))flags|=8;
    if(load_private_entry(createA))flags|=16;
    if(load_private_entry(createW))flags|=32;
    if(!load_private_entry((void*)__imp_CreateProcessA)||!load_private_entry((void*)__imp_CreateProcessW)){
        jclass failure=(*env)->FindClass(env,"java/lang/AssertionError");
        if(failure)(*env)->ThrowNew(env,failure,"REAL_JNI_ONLOAD_NORMAL_IMPORTS_NOT_CONTROLLED");return JNI_ERR;
    }
    jclass check=(*env)->FindClass(env,"dev/ronova/pro/AuditFixCheck");
    jmethodID observed=check?(*env)->GetStaticMethodID(env,check,"hostLoadObserved","(JJI)V"):NULL;
#ifdef AUDIT_HOST_OS_CHECK
    if(!load_os_check(env))return JNI_ERR;
#endif
#ifdef AUDIT_HOST_JVMTI_CHECK
    if(!load_jvmti_environment(vm,env,check))return JNI_ERR;
#endif
    if(observed)(*env)->CallStaticVoidMethod(env,check,observed,(jlong)(uintptr_t)createA,(jlong)(uintptr_t)createW,(jint)flags);
    if((*env)->ExceptionCheck(env))return JNI_ERR;
    if(flags!=63){
        jclass failure=(*env)->FindClass(env,"java/lang/AssertionError");
        if(failure)(*env)->ThrowNew(env,failure,"REAL_JNI_ONLOAD_HOST_ENTRIES_NOT_CONTROLLED");return JNI_ERR;
    }
#ifdef AUDIT_HOST_LOAD_NESTED
    jmethodID nested=(*env)->GetStaticMethodID(env,check,"hostLoadNested","()V");
    if(nested)(*env)->CallStaticVoidMethod(env,check,nested);
    if((*env)->ExceptionCheck(env))return JNI_ERR;
    if(!load_child(createA,1,&error)||!load_child(createW,0,&error)){
        jclass failure=(*env)->FindClass(env,"java/lang/AssertionError");
        if(failure)(*env)->ThrowNew(env,failure,"OUTER_JNI_ONLOAD_SCOPE_CHANGED_AFTER_NESTED_LOAD");return JNI_ERR;
    }
#endif
#ifdef AUDIT_HOST_LOAD_THROW
    jclass failure=(*env)->FindClass(env,"java/lang/IllegalStateException");
    if(failure)(*env)->ThrowNew(env,failure,"AUDIT_HOST_LOAD_THROW");return JNI_VERSION_1_8;
#elif defined(AUDIT_HOST_LOAD_FAIL)
    return JNI_ERR;
#else
    return JNI_VERSION_1_8;
#endif
#endif
}
#ifdef AUDIT_HOST_LOAD_SUPPORT
JNIEXPORT jlong JNICALL Java_dev_ronova_pro_AuditFixCheck_hostLoadCachedCreate0(JNIEnv *env,jclass type,jlong pointer,jboolean ansi){
    (void)env;(void)type;DWORD error=0;
    int created=load_child((void*)(uintptr_t)pointer,ansi?1:0,&error);
    return ((jlong)created<<32)|(jlong)(uint32_t)error;
}
#endif

#ifdef AUDIT_HOST_THREAD_CREATE_CHECK
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_AuditFixCheck_hostCrtAligned0(JNIEnv *env,jclass type){
    (void)type;HMODULE provider=GetModuleHandleW(L"ucrtbase.dll");
    if(!provider)return load_thread_fail(env,"ALIGNED_CRT_ACTUAL_PROVIDER_ABSENT")?JNI_TRUE:JNI_FALSE;
    const char *names[]={"_aligned_malloc","_aligned_offset_malloc","_aligned_realloc","_aligned_offset_realloc",
        "_aligned_recalloc","_aligned_offset_recalloc","_aligned_free","_aligned_msize"};
    void *entries[8]={0};
    for(unsigned i=0;i<8;i++){
        entries[i]=(void*)GetProcAddress(provider,names[i]);
        if(!load_private_entry(entries[i]))return load_thread_fail(env,names[i])?JNI_TRUE:JNI_FALSE;
    }
    void *(__cdecl *allocate)(size_t,size_t)=entries[0];
    void *(__cdecl *offsetAllocate)(size_t,size_t,size_t)=entries[1];
    void *(__cdecl *resize)(void*,size_t,size_t)=entries[2];
    void *(__cdecl *offsetResize)(void*,size_t,size_t,size_t)=entries[3];
    void *(__cdecl *rezero)(void*,size_t,size_t,size_t)=entries[4];
    void *(__cdecl *offsetRezero)(void*,size_t,size_t,size_t,size_t)=entries[5];
    void (__cdecl *release)(void*)=entries[6];
    size_t (__cdecl *extent)(void*,size_t,size_t)=entries[7];
    BYTE *plain=allocate(32,64),*offset=offsetAllocate(32,64,8);const char *failure=NULL;
    if(!plain||!offset){failure="ALIGNED_CRT_ACTUAL_ALLOCATION";goto done;}
    if((uintptr_t)plain%64||((uintptr_t)offset+8)%64||extent(plain,64,0)<32||extent(offset,64,8)<32){failure="ALIGNED_CRT_SIZE_ALIGNMENT";goto done;}
    memset(plain,0x5a,32);memset(offset,0x7b,32);
    BYTE *failed=resize(plain,SIZE_MAX,64);
    if(failed){plain=failed;failure="ALIGNED_CRT_FAILED_RESIZE_RESULT";goto done;}
    for(unsigned i=0;i<32;i++)if(plain[i]!=0x5a){failure="ALIGNED_CRT_FAILED_RESIZE_OLD_BYTES";goto done;}
    BYTE *next=resize(plain,64,64);if(!next){failure="ALIGNED_CRT_RESIZE";goto done;}plain=next;
    next=offsetResize(offset,64,64,8);if(!next){failure="ALIGNED_CRT_OFFSET_RESIZE";goto done;}offset=next;
    size_t plainBefore=extent(plain,64,0),offsetBefore=extent(offset,64,8);
    if(plainBefore<64||offsetBefore<64||plainBefore==SIZE_MAX||offsetBefore==SIZE_MAX){failure="ALIGNED_CRT_PRE_RECALLOC_ACTUAL_EXTENT";goto done;}
    next=rezero(plain,2,64,64);if(!next){failure="ALIGNED_CRT_RECALLOC";goto done;}plain=next;
    next=offsetRezero(offset,2,64,64,8);if(!next){failure="ALIGNED_CRT_OFFSET_RECALLOC";goto done;}offset=next;
    for(unsigned i=0;i<32;i++)if(plain[i]!=0x5a||offset[i]!=0x7b){failure="ALIGNED_CRT_RESIZE_ORIGINAL_BYTES";goto done;}
    /* Recalloc zeros the newly allocated extent. The allocator can retain
     * more than the preceding request's 64 bytes; those old usable bytes do
     * not become a zero-initialized extension merely because of that request. */
    for(size_t i=plainBefore;i<128;i++)if(plain[i]){failure="ALIGNED_CRT_RECALLOC_ZEROED_EXTENSION";goto done;}
    for(size_t i=offsetBefore;i<128;i++)if(offset[i]){failure="ALIGNED_CRT_OFFSET_RECALLOC_ZEROED_EXTENSION";goto done;}
done:
    if(plain)release(plain);if(offset)release(offset);release(NULL);
    if(failure)return load_thread_fail(env,failure)?JNI_TRUE:JNI_FALSE;
    fprintf(stdout,"HOST_CRT_ALIGNED_PASS actual_ucrtbase_8_named_GPA alignment_offset_read_extent failed_resize_old_bytes resize_recalloc paired_aligned_free no_object_layout_claim\n");
    return JNI_TRUE;
}
#endif
