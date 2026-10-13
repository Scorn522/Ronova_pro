/* Real JNI_OnLoad companion for AuditFixCheck. Build separate normal, nested,
 * failing and support DLLs from this source; no backend registry is fabricated. */
#include <jni.h>
#include <windows.h>
#include <stdint.h>
#include <stdio.h>
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

/* Include beside the real HostLoadNative.c payload, never an embedded second
 * production controller. Run only in the normal DLL with AUDIT_HOST_JVMTI_CHECK. */
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
typedef struct LoadClassBytes {unsigned char bytes[256];unsigned length;} LoadClassBytes;
static void load_ti_u1(LoadClassBytes *image,unsigned value){image->bytes[image->length++]=(unsigned char)value;}
static void load_ti_u2(LoadClassBytes *image,unsigned value){load_ti_u1(image,value>>8);load_ti_u1(image,value);}
static void load_ti_u4(LoadClassBytes *image,unsigned value){load_ti_u2(image,value>>16);load_ti_u2(image,value);}
static void load_ti_utf8(LoadClassBytes *image,const char *value){unsigned length=(unsigned)strlen(value);load_ti_u1(image,1);load_ti_u2(image,length);memcpy(image->bytes+image->length,value,length);image->length+=length;}
static int load_ti_define(JNIEnv *env,jobject loader,char suffix){
    char name[]="dev/ronova/pro/JvmtiPayloadA";name[sizeof(name)-2]=suffix;LoadClassBytes image={0};
    load_ti_u4(&image,0xcafebabe);load_ti_u2(&image,0);load_ti_u2(&image,61);load_ti_u2(&image,8);
    load_ti_utf8(&image,name);load_ti_u1(&image,7);load_ti_u2(&image,1);
    load_ti_utf8(&image,"java/lang/Object");load_ti_u1(&image,7);load_ti_u2(&image,3);
    load_ti_utf8(&image,"value");load_ti_utf8(&image,"()I");load_ti_utf8(&image,"Code");
    load_ti_u2(&image,0x21);load_ti_u2(&image,2);load_ti_u2(&image,4);load_ti_u2(&image,0);load_ti_u2(&image,0);load_ti_u2(&image,1);
    load_ti_u2(&image,0x9);load_ti_u2(&image,5);load_ti_u2(&image,6);load_ti_u2(&image,1);load_ti_u2(&image,7);load_ti_u4(&image,15);
    load_ti_u2(&image,1);load_ti_u2(&image,0);load_ti_u4(&image,3);load_ti_u1(&image,0x10);load_ti_u1(&image,7);load_ti_u1(&image,0xac);
    load_ti_u2(&image,0);load_ti_u2(&image,0);load_ti_u2(&image,0);
    jclass actual=(*env)->DefineClass(env,name,loader,(const jbyte*)image.bytes,(jsize)image.length);
    jmethodID method=actual?(*env)->GetStaticMethodID(env,actual,"value","()I"):NULL;
    int result=method?(*env)->CallStaticIntMethod(env,actual,method):-1;if(actual)(*env)->DeleteLocalRef(env,actual);return result;
}
static jclass JNICALL load_ti_uncontrolled_define(JNIEnv *env,const char *name,jobject loader,const jbyte *bytes,jsize length){
    (void)env;(void)name;(void)loader;(void)bytes;(void)length;return NULL;
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

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm,void *reserved){
    (void)reserved;
#ifdef AUDIT_HOST_LOAD_SUPPORT
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
