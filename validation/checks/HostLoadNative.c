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
