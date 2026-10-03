/* JNI array pointers always refer to our copy. The real Critical pair ends
 * before control returns to native user code: nested pairs can consult policy
 * without calling Java under the JVM's GC locker. */
typedef struct NativeArrayCopy {
    jobject array;
    void *contents;
    size_t bytes;
    jsize length;
    char kind;
    int critical,releasing;
    OwnerLink *owners;
    jweak targetModule;
    struct NativeArrayCopy *next;
} NativeArrayCopy;
static NativeArrayCopy *native_array_copies;
typedef struct NativeStringCopy {jstring string;jchar *contents;size_t bytes;OwnerLink *owners;struct NativeStringCopy *next;} NativeStringCopy;
static NativeStringCopy *native_string_copies;
static const jchar *JNICALL native_get_string_critical(JNIEnv *env,jstring string,jboolean *copy){
    void *caller=__builtin_return_address(0);
    if(native_original.ExceptionCheck(env))return NULL;
    if(native_stopped(env)||native_caller_stopped(caller)){native_refuse(env,"RONOVA_STOPPED_NATIVE_STRING_POINTER");return NULL;}
    jsize length=native_original.GetStringLength(env,string);if(native_original.ExceptionCheck(env))return NULL;
    NativeStringCopy *lease=(NativeStringCopy*)calloc(1,sizeof(*lease));if(!lease){native_refuse(env,"NATIVE_STRING_COPY_ALLOCATION_FAILED");return NULL;}
    lease->bytes=(size_t)length*sizeof(jchar);lease->contents=(jchar*)malloc(lease->bytes?lease->bytes:1);
    lease->string=(jstring)native_original.NewGlobalRef(env,string);
    if(!lease->contents||!lease->string){if(lease->string)native_original.DeleteGlobalRef(env,lease->string);free(lease->contents);free(lease);native_refuse(env,"NATIVE_STRING_COPY_ALLOCATION_FAILED");return NULL;}
    const jchar *actual=native_original.GetStringCritical(env,string,NULL);
    if(actual){memcpy(lease->contents,actual,lease->bytes);native_original.ReleaseStringCritical(env,string,actual);}
    if(!actual){native_original.DeleteGlobalRef(env,lease->string);free(lease->contents);free(lease);return NULL;}
    NativeThread *state=native_thread();
    if(!native_capture_scope_owners(env,state,&lease->owners)||!native_capture_image_owners(caller,&lease->owners)){
        native_owner_links_free(lease->owners);native_original.DeleteGlobalRef(env,lease->string);SecureZeroMemory(lease->contents,lease->bytes);free(lease->contents);free(lease);native_refuse(env,"NATIVE_POINTER_SOURCE_CAPACITY");return NULL;
    }
    AcquireSRWLockExclusive(&native_records);lease->next=native_string_copies;native_string_copies=lease;ReleaseSRWLockExclusive(&native_records);
    if(copy)*copy=JNI_TRUE;return lease->contents;
}
static void JNICALL native_release_string_critical(JNIEnv *env,jstring string,const jchar *contents){
    AcquireSRWLockExclusive(&native_records);NativeStringCopy *lease=native_string_copies,*previous=NULL;
    while(lease&&(lease->contents!=contents||!native_original.IsSameObject(env,lease->string,string))){previous=lease;lease=lease->next;}
    if(!lease){ReleaseSRWLockExclusive(&native_records);native_refuse(env,"RONOVA_NATIVE_STRING_RELEASE_PAIR_MISSING");return;}
    if(previous)previous->next=lease->next;else native_string_copies=lease->next;ReleaseSRWLockExclusive(&native_records);
    native_original.DeleteGlobalRef(env,lease->string);native_owner_links_free(lease->owners);SecureZeroMemory(lease->contents,lease->bytes);free(lease->contents);free(lease);
}
static unsigned native_array_width(char kind){
    switch(kind){case 'Z':return sizeof(jboolean);case 'B':return sizeof(jbyte);case 'C':return sizeof(jchar);case 'S':return sizeof(jshort);
        case 'I':return sizeof(jint);case 'J':return sizeof(jlong);case 'F':return sizeof(jfloat);case 'D':return sizeof(jdouble);default:return 0;}
}
static void *native_array_copy(JNIEnv *env,jarray array,jboolean *copy,char expected,int critical,void *caller){
    if(native_caller_stopped(caller)){native_refuse(env,"RONOVA_STOPPED_NATIVE_ARRAY_POINTER");return NULL;}
    if(!native_pin_allowed(env,array))return NULL;
    jclass type=native_original.GetObjectClass(env,array);char *signature=NULL;
    if(!type||(*native_ti)->GetClassSignature(native_ti,type,&signature,NULL)!=JVMTI_ERROR_NONE)return NULL;
    char kind=signature[0]=='['&&signature[1]&&signature[2]==0?signature[1]:0;
    (*native_ti)->Deallocate(native_ti,(unsigned char*)signature);native_original.DeleteLocalRef(env,type);
    unsigned width=native_array_width(kind);
    if(!width||(expected&&kind!=expected)){native_refuse(env,"RONOVA_NATIVE_ARRAY_TYPE_MISMATCH");return NULL;}
    jsize length=native_original.GetArrayLength(env,array);if(native_original.ExceptionCheck(env))return NULL;
    NativeArrayCopy *lease=(NativeArrayCopy*)calloc(1,sizeof(*lease));if(!lease){native_refuse(env,"RONOVA_NATIVE_ARRAY_COPY_ALLOCATION_FAILED");return NULL;}
    lease->bytes=(size_t)length*width;lease->contents=malloc(lease->bytes?lease->bytes:1);
    lease->array=native_original.NewGlobalRef(env,array);lease->length=length;lease->kind=kind;lease->critical=critical;
    if(!lease->contents||!lease->array){if(lease->array)native_original.DeleteGlobalRef(env,lease->array);free(lease->contents);free(lease);native_refuse(env,"RONOVA_NATIVE_ARRAY_COPY_ALLOCATION_FAILED");return NULL;}
    // No JVM call, allocation, lock or policy callback occurs between this pair.
    void *actual=native_original.GetPrimitiveArrayCritical(env,array,NULL);
    if(actual){memcpy(lease->contents,actual,lease->bytes);native_original.ReleasePrimitiveArrayCritical(env,array,actual,JNI_ABORT);}
    if(!actual){native_original.DeleteGlobalRef(env,lease->array);free(lease->contents);free(lease);return NULL;}
    NativeThread *state=native_thread();
    if(!native_capture_scope_owners(env,state,&lease->owners)||!native_capture_image_owners(caller,&lease->owners)){
        native_owner_links_free(lease->owners);native_original.DeleteGlobalRef(env,lease->array);SecureZeroMemory(lease->contents,lease->bytes);free(lease->contents);free(lease);native_refuse(env,"NATIVE_POINTER_SOURCE_CAPACITY");return NULL;
    }
    jobject target=native_original.CallStaticObjectMethod(env,native_tasks,native_object_module,array);
    if(target){lease->targetModule=native_original.NewWeakGlobalRef(env,target);native_original.DeleteLocalRef(env,target);}
    if(native_original.ExceptionCheck(env)){
        if(lease->targetModule)native_original.DeleteWeakGlobalRef(env,lease->targetModule);
        native_original.DeleteGlobalRef(env,lease->array);native_owner_links_free(lease->owners);free(lease->contents);free(lease);return NULL;
    }
    AcquireSRWLockExclusive(&native_records);lease->next=native_array_copies;native_array_copies=lease;ReleaseSRWLockExclusive(&native_records);
    if(copy)*copy=JNI_TRUE;return lease->contents;
}
static void native_array_commit(JNIEnv *env,NativeArrayCopy *lease){
    // Each setter takes one private snapshot and checks the exact submitted
    // values. A held pointer cannot refill a newly stopped/protected target.
    switch(lease->kind){
        case 'Z':native_set_array_Boolean(env,(jbooleanArray)lease->array,0,lease->length,(const jboolean*)lease->contents);break;
        case 'B':native_set_array_Byte(env,(jbyteArray)lease->array,0,lease->length,(const jbyte*)lease->contents);break;
        case 'C':native_set_array_Char(env,(jcharArray)lease->array,0,lease->length,(const jchar*)lease->contents);break;
        case 'S':native_set_array_Short(env,(jshortArray)lease->array,0,lease->length,(const jshort*)lease->contents);break;
        case 'I':native_set_array_Int(env,(jintArray)lease->array,0,lease->length,(const jint*)lease->contents);break;
        case 'J':native_set_array_Long(env,(jlongArray)lease->array,0,lease->length,(const jlong*)lease->contents);break;
        case 'F':native_set_array_Float(env,(jfloatArray)lease->array,0,lease->length,(const jfloat*)lease->contents);break;
        case 'D':native_set_array_Double(env,(jdoubleArray)lease->array,0,lease->length,(const jdouble*)lease->contents);break;
    }
}
static void native_array_release(JNIEnv *env,jarray array,void *contents,jint mode,char expected,int critical,void *caller){
    if(mode!=0&&mode!=JNI_COMMIT&&mode!=JNI_ABORT){native_refuse(env,"RONOVA_NATIVE_ARRAY_RELEASE_MODE");return;}
    AcquireSRWLockExclusive(&native_records);NativeArrayCopy *lease=native_array_copies,*previous=NULL;
    while(lease&&(lease->contents!=contents||!native_original.IsSameObject(env,lease->array,array))){previous=lease;lease=lease->next;}
    if(!lease||lease->releasing||lease->critical!=critical||(expected&&lease->kind!=expected)){
        ReleaseSRWLockExclusive(&native_records);native_refuse(env,"RONOVA_NATIVE_ARRAY_RELEASE_PAIR_MISSING");return;
    }
    lease->releasing=1;
    if(mode!=JNI_COMMIT){if(previous)previous->next=lease->next;else native_array_copies=lease->next;}
    ReleaseSRWLockExclusive(&native_records);
    jthrowable pending=native_original.ExceptionOccurred(env);if(pending)native_original.ExceptionClear(env);
    if(mode!=JNI_ABORT&&!native_stopped(env)&&!native_caller_stopped(caller)&&!native_owner_stopped(lease->owners))native_array_commit(env,lease);
    // JNI_COMMIT retains the same pointer even when its attempted write was refused.
    if(mode==JNI_COMMIT){AcquireSRWLockExclusive(&native_records);lease->releasing=0;ReleaseSRWLockExclusive(&native_records);}
    else{native_original.DeleteGlobalRef(env,lease->array);if(lease->targetModule)native_original.DeleteWeakGlobalRef(env,lease->targetModule);native_owner_links_free(lease->owners);SecureZeroMemory(lease->contents,lease->bytes);free(lease->contents);free(lease);}
    if(pending){if(native_original.ExceptionCheck(env)){native_original.ExceptionClear(env);InterlockedIncrement(&native_failures);}native_original.Throw(env,pending);native_original.DeleteLocalRef(env,pending);}
}
