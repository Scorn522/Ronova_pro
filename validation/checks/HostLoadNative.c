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
    target->address=address;target->bytes=bytes;target->protect=region.Protect;memcpy(target->before,address,bytes);return 1;
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
