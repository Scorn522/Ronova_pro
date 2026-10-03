/* Actual JDK load scopes and OS image identity; no file-name ownership inference. */
typedef jboolean (JNICALL *NativeLoad)(JNIEnv*,jclass,jobject,jstring,jboolean,jboolean,jboolean);
typedef void (JNICALL *NativeUnload)(JNIEnv*,jclass,jstring,jboolean,jboolean,jlong);
static NativeLoad native_load_original;
static NativeUnload native_unload_original;
static int native_library_parameters(JNIEnv *env,NativeLibrary *library,jstring name,jboolean builtin,jboolean jni){
    return library->builtin==builtin&&library->jni==jni&&native_original.IsSameObject(env,library->name,name);
}
static jboolean JNICALL native_library_load(JNIEnv *env,jclass type,jobject identity,jstring name,jboolean builtin,jboolean jni,jboolean throwing){
    NativeThread *state=native_thread();NativeLibraryScope *scope=state?state->library:NULL;
    if(!InterlockedCompareExchange(&native_library_ready,0,0)&&!scope){
        if(!state){native_refuse(env,"NATIVE_LIBRARY_SCOPE_CAPACITY");return JNI_FALSE;}
        jobject previous=state->unobservedLibrary;state->unobservedLibrary=identity;
        jboolean result=native_load_original(env,type,identity,name,builtin,jni,throwing);
        state->unobservedLibrary=previous;return result;
    }
    if(!scope||scope->unloading||scope->phase!=0||!native_original.IsSameObject(env,scope->library->identity,identity)
            ||!native_library_parameters(env,scope->library,name,builtin,jni)){native_refuse(env,"ACTUAL_NATIVE_LOAD_SCOPE_REQUIRED");return JNI_FALSE;}
    if(!native_original.CallStaticBooleanMethod(env,native_controller,native_load_gate)){native_refuse(env,"ACTUAL_JDK_NATIVE_LOAD_CALL_REQUIRED");return JNI_FALSE;}
    jobject context=native_original.GetStaticObjectField(env,native_library_class,native_library_context);
    jclass deque=context?native_original.GetObjectClass(env,context):NULL;
    jmethodID peek=deque?native_original.GetMethodID(env,deque,"peek","()Ljava/lang/Object;"):NULL;
    jobject top=peek?native_original.CallObjectMethod(env,context,peek):NULL;
    int registered=top&&native_original.IsSameObject(env,top,identity);
    if(top)native_original.DeleteLocalRef(env,top);if(deque)native_original.DeleteLocalRef(env,deque);if(context)native_original.DeleteLocalRef(env,context);
    if(!registered||native_original.ExceptionCheck(env)){native_refuse(env,"ACTUAL_JDK_NATIVE_LOAD_CONTEXT_REQUIRED");return JNI_FALSE;}
    jstring snapshot=native_original.NewString(env,(const jchar*)scope->library->path,scope->library->nameLength);if(!snapshot)return JNI_FALSE;
    scope->phase=1;jboolean result=native_load_original(env,type,identity,snapshot,builtin,jni,throwing);native_original.DeleteLocalRef(env,snapshot);
    jthrowable pending=native_original.ExceptionOccurred(env);if(pending)native_original.ExceptionClear(env);
    jlong handle=native_original.GetLongField(env,identity,native_impl_handle);
    AcquireSRWLockExclusive(&native_records);scope->library->handle=(HMODULE)(uintptr_t)handle;scope->success=result&&handle!=0;scope->phase=2;ReleaseSRWLockExclusive(&native_records);
    if(pending){if(native_original.ExceptionCheck(env))native_original.ExceptionClear(env);native_original.Throw(env,pending);native_original.DeleteLocalRef(env,pending);}return result;
}
static void JNICALL native_library_unload(JNIEnv *env,jclass type,jstring name,jboolean builtin,jboolean jni,jlong handle){
    NativeThread *state=native_thread();NativeLibraryScope *scope=state?state->library:NULL;
    if(!InterlockedCompareExchange(&native_library_ready,0,0)&&!scope){native_unload_original(env,type,name,builtin,jni,handle);return;}
    if(!scope||!scope->unloading||scope->phase!=0||scope->library->handle!=(HMODULE)(uintptr_t)handle
            ||!native_library_parameters(env,scope->library,name,builtin,jni)){native_refuse(env,"ACTUAL_NATIVE_UNLOAD_SCOPE_REQUIRED");return;}
    AcquireSRWLockShared(&native_records);int shared=0;
    if(jni)for(NativeLibrary *other=native_libraries;other;other=other->next)
        if(other!=scope->library&&other->handle==scope->library->handle&&InterlockedCompareExchange(&other->alive,0,0)
                &&other->consumer!=scope->library->consumer&&!native_owner_stopped(other->owners)){shared=1;break;}
    ReleaseSRWLockShared(&native_records);if(shared){native_refuse(env,"NATIVE_SHARED_IMAGE_UNLOAD_PENDING");return;}
    jstring snapshot=native_original.NewString(env,(const jchar*)scope->library->path,scope->library->nameLength);if(!snapshot)return;
    InterlockedExchange(&scope->library->closing,1);
    host_library_releasing(scope->library);
    scope->phase=1;native_unload_original(env,type,snapshot,builtin,jni,handle);native_original.DeleteLocalRef(env,snapshot);scope->success=!native_original.ExceptionCheck(env);scope->phase=2;
}
static int native_library_install(JNIEnv *env){
    jclass libraries=native_original.FindClass(env,"jdk/internal/loader/NativeLibraries");
    jclass implementation=libraries?native_original.FindClass(env,"jdk/internal/loader/NativeLibraries$NativeLibraryImpl"):NULL;
    if(!libraries||!implementation)return 0;
    native_library_class=(jclass)native_original.NewGlobalRef(env,libraries);native_impl_class=(jclass)native_original.NewGlobalRef(env,implementation);
    jclass unloader=native_original.FindClass(env,"jdk/internal/loader/NativeLibraries$Unloader");if(unloader){native_unloader_class=(jclass)native_original.NewGlobalRef(env,unloader);native_original.DeleteLocalRef(env,unloader);}
    native_library_context=native_original.GetStaticFieldID(env,libraries,"nativeLibraryContext","Ljava/util/Deque;");
    native_impl_handle=native_original.GetFieldID(env,implementation,"handle","J");native_impl_version=native_original.GetFieldID(env,implementation,"jniVersion","I");native_impl_name=native_original.GetFieldID(env,implementation,"name","Ljava/lang/String;");
    native_impl_builtin=native_original.GetFieldID(env,implementation,"isBuiltin","Z");native_impl_jni=native_original.GetFieldID(env,implementation,"isJNI","Z");
    native_load_gate=native_original.GetStaticMethodID(env,native_controller,"libraryLoadCaller","()Z");
    HMODULE java=GetModuleHandleW(L"java.dll");
    native_java_image=java;
    native_load_original=java?(NativeLoad)GetProcAddress(java,"Java_jdk_internal_loader_NativeLibraries_load"):NULL;
    native_unload_original=java?(NativeUnload)GetProcAddress(java,"Java_jdk_internal_loader_NativeLibraries_unload"):NULL;
    int ready=native_library_class&&native_impl_class&&native_unloader_class&&native_library_context&&native_impl_handle&&native_impl_version&&native_impl_name&&native_impl_builtin&&native_impl_jni&&native_load_gate&&native_load_original&&native_unload_original&&!native_original.ExceptionCheck(env);
    if(ready){JNINativeMethod entries[]={
        {"load","(Ljdk/internal/loader/NativeLibraries$NativeLibraryImpl;Ljava/lang/String;ZZZ)Z",(void*)native_library_load},
        {"unload","(Ljava/lang/String;ZZJ)V",(void*)native_library_unload}};
        ready=native_original.RegisterNatives(env,libraries,entries,2)==JNI_OK;
    }
    native_original.DeleteLocalRef(env,implementation);native_original.DeleteLocalRef(env,libraries);
    if(native_original.ExceptionCheck(env)){native_original.ExceptionClear(env);ready=0;}return ready;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_libraryActivate0(JNIEnv *env,jclass type){
    (void)type;if(!native_controller||!native_original.CallStaticBooleanMethod(env,native_controller,native_control_gate)){native_refuse(env,"NATIVE_CONTROL_AGENT_REQUIRED");return JNI_FALSE;}
    if(!InterlockedCompareExchange(&native_library_present,0,0))return JNI_FALSE;InterlockedExchange(&native_library_ready,1);return JNI_TRUE;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_libraryPresent0(JNIEnv *env,jclass type){
    (void)env;(void)type;return InterlockedCompareExchange(&native_library_present,0,0)?JNI_TRUE:JNI_FALSE;
}
static int native_library_permission(JNIEnv *env,const char *operation){
    if(!native_controller||!native_library_gate)return 0;
    jstring name=native_original.NewStringUTF(env,operation);
    jboolean allowed=name?native_original.CallStaticBooleanMethod(env,native_controller,native_library_gate,name):JNI_FALSE;
    if(name)native_original.DeleteLocalRef(env,name);
    if(!allowed&&!native_original.ExceptionCheck(env))native_refuse(env,"ACTUAL_NATIVE_LIBRARY_OPERATION_REQUIRED");
    return allowed&&!native_original.ExceptionCheck(env);
}
JNIEXPORT jlong JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_libraryBegin0(JNIEnv *env,jclass type,jobject identity,jclass from,jclass actor){
    (void)type;if(!native_library_permission(env,"libraryBegin"))return 0;
    if(native_stopped(env)){native_refuse(env,"RONOVA_STOPPED_NATIVE_LIBRARY_LOAD");return 0;}
    NativeThread *state=native_thread();if(!state){native_refuse(env,"NATIVE_LIBRARY_SCOPE_CAPACITY");return 0;}
    state->control++;
    jobject carrier=from?native_original.CallStaticObjectMethod(env,native_definitions,native_module_query,from):NULL;
    jobject consumer=actor&&!native_original.ExceptionCheck(env)?native_original.CallStaticObjectMethod(env,native_definitions,native_module_query,actor):NULL;
    NativeLibrary *library=(NativeLibrary*)calloc(1,sizeof(*library));NativeLibraryScope *scope=(NativeLibraryScope*)calloc(1,sizeof(*scope));
    int ready=library&&scope&&!native_original.ExceptionCheck(env);
    AcquireSRWLockExclusive(&native_records);
    if(ready){
        library->carrier=native_owner(env,carrier);library->consumer=native_owner(env,consumer);
        ready=native_owner_add(&library->owners,library->carrier)&&native_owner_add(&library->owners,library->consumer);
        if(carrier&&!library->carrier||consumer&&!library->consumer)ready=0;
        library->identity=native_original.NewWeakGlobalRef(env,identity);
        library->origin=native_original.NewWeakGlobalRef(env,actor?actor:from?from:native_controller);
        jstring name=(jstring)native_original.GetObjectField(env,identity,native_impl_name);
        library->builtin=native_original.GetBooleanField(env,identity,native_impl_builtin);library->jni=native_original.GetBooleanField(env,identity,native_impl_jni);
        if(name){library->name=native_original.NewWeakGlobalRef(env,name);jsize length=native_original.GetStringLength(env,name);library->nameLength=length;library->path=(wchar_t*)calloc((size_t)length+1,sizeof(wchar_t));
            if(library->path)native_original.GetStringRegion(env,name,0,length,(jchar*)library->path);native_original.DeleteLocalRef(env,name);}
        ready=ready&&library->identity&&library->origin&&library->name&&library->path&&!native_original.ExceptionCheck(env)&&!native_owner_stopped(library->owners);
        if(ready){library->alive=1;library->next=native_libraries;native_libraries=library;scope->library=library;scope->previous=state->library;state->library=scope;}
    }
    ReleaseSRWLockExclusive(&native_records);
    if(carrier)native_original.DeleteLocalRef(env,carrier);if(consumer)native_original.DeleteLocalRef(env,consumer);state->control--;
    if(!ready){if(library){if(library->identity)native_original.DeleteWeakGlobalRef(env,library->identity);if(library->origin)native_original.DeleteWeakGlobalRef(env,library->origin);if(library->name)native_original.DeleteWeakGlobalRef(env,library->name);free(library->path);native_owner_links_free(library->owners);free(library);}free(scope);native_refuse(env,"NATIVE_LIBRARY_SCOPE_UNOBSERVED");InterlockedIncrement(&native_failures);return 0;}
    return (jlong)(uintptr_t)scope;
}
JNIEXPORT jlong JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_libraryUnloadBegin0(JNIEnv *env,jclass type,jobject unloader,jlong handle,jclass actor,jboolean automatic,jboolean recovery){
    (void)type;if(!native_library_permission(env,"libraryUnloadBegin"))return 0;
    NativeThread *state=native_thread();if(!state){native_refuse(env,"NATIVE_LIBRARY_SCOPE_CAPACITY");return 0;}
    AcquireSRWLockShared(&native_records);NativeLibrary *library=NULL;int attached=0;
    for(NativeLibrary *candidate=native_libraries;candidate&&!attached;candidate=candidate->next)if(candidate->handle==(HMODULE)(uintptr_t)handle&&InterlockedCompareExchange(&candidate->alive,0,0))
        for(NativeUnloader *actual=candidate->unloaders;actual;actual=actual->next)if(native_original.IsSameObject(env,actual->identity,unloader)){library=candidate;attached=1;break;}
    ReleaseSRWLockShared(&native_records);if(!library)return 0;
    if(!attached){native_refuse(env,"ACTUAL_NATIVE_LIBRARY_UNLOADER_REQUIRED");return 0;}
    if(!automatic&&!recovery){
        jobject consumer=actor?native_original.CallStaticObjectMethod(env,native_definitions,native_module_query,actor):NULL;
        int owns=consumer&&library->consumer&&native_original.IsSameObject(env,library->consumer->module,consumer);
        if(consumer)native_original.DeleteLocalRef(env,consumer);
        if(!owns||native_original.ExceptionCheck(env)){native_refuse(env,"NATIVE_LIBRARY_FOREIGN_RELEASE_REFUSED");return 0;}
    }
    NativeLibraryScope *scope=(NativeLibraryScope*)calloc(1,sizeof(*scope));if(!scope){native_refuse(env,"NATIVE_LIBRARY_SCOPE_CAPACITY");return 0;}
    scope->library=library;scope->unloading=1;scope->previous=state->library;state->library=scope;return (jlong)(uintptr_t)scope;
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_libraryUnloader0(JNIEnv *env,jclass type,jobject identity,jobject unloader,jlong handle){
    (void)type;if(!native_library_permission(env,"libraryUnloader"))return;
    AcquireSRWLockExclusive(&native_records);NativeLibrary *library=native_libraries;
    while(library&&!native_original.IsSameObject(env,library->identity,identity))library=library->next;
    int ready=!library;
    if(library&&library->handle==(HMODULE)(uintptr_t)handle&&InterlockedCompareExchange(&library->alive,0,0)){
        NativeUnloader *record=(NativeUnloader*)calloc(1,sizeof(*record));
        if(record){record->identity=native_original.NewWeakGlobalRef(env,unloader);if(record->identity){record->next=library->unloaders;library->unloaders=record;ready=1;}else free(record);}
    }
    ReleaseSRWLockExclusive(&native_records);if(!ready){native_refuse(env,"NATIVE_LIBRARY_UNLOADER_ASSOCIATION_UNOBSERVED");InterlockedIncrement(&native_failures);}
}
JNIEXPORT void JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_libraryEnd0(JNIEnv *env,jclass type,jlong token,jlong handle,jboolean success){
    (void)type;if(!native_library_permission(env,"libraryEnd"))return;
    NativeThread *state=native_thread();NativeLibraryScope *scope=state?state->library:NULL;
    if(!scope||(uintptr_t)scope!=(uintptr_t)token){native_refuse(env,"NATIVE_LIBRARY_SCOPE_CHANGED");return;}
    AcquireSRWLockExclusive(&native_records);
    if(scope->unloading){if(scope->phase==2&&scope->success&&scope->library->handle==(HMODULE)(uintptr_t)handle)InterlockedExchange(&scope->library->alive,0);}
    else{
        InterlockedExchange(&scope->library->complete,1);
        if(scope->phase!=2||!scope->success){InterlockedExchange(&scope->library->alive,0);for(Binding *binding=native_bindings;binding;binding=binding->next)if(binding->library==scope->library||binding->loading==scope->library)InterlockedExchange(&binding->valid,0);}
        else if(scope->library->handle!=(HMODULE)(uintptr_t)handle||!success)InterlockedIncrement(&native_failures);
    }
    ReleaseSRWLockExclusive(&native_records);
    /* The real load has returned and the exact JDK library record is complete.
     * This does not claim to intercept JNI_OnLoad's first native side effects. */
    if(!scope->unloading&&scope->phase==2&&scope->success)host_observe_image(scope->library->handle);
    state->library=scope->previous;SecureZeroMemory(scope,sizeof(*scope));free(scope);
}
