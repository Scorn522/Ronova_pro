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
    if(!DeleteFileW(invalidW)&&!failure)failure="HOST_MODULE_INVALID_IMAGE_REMOVE";
    if(failure){load_thread_fail(env,failure);return;}
    fprintf(stderr,"HOST_MODULE_PRODUCER_ADMISSION_NOTRUN no_actual_Mod_producer no_fabricated_sources; child_DLL_sources_and_preobservation_loader_window_remain_uncontrolled\n");
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
