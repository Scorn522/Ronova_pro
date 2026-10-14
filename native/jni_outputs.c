/* Included after the shared JNI output context/commit declarations.  Every
 * caller-owned output is staged before the short native-only publication.
 * Original JNI calls and their matching releases never run under that gate. */

static void native_jni_output_oom(JNIEnv *env){
    if(native_original.ExceptionCheck(env))return;
    jclass type=native_original.FindClass(env,"java/lang/OutOfMemoryError");
    if(type){
        native_original.ThrowNew(env,type,"RONOVA_JNI_OUTPUT_SCRATCH_ALLOCATION_FAILED");
        native_original.DeleteLocalRef(env,type);
    }
}

static jint JNICALL native_jni_output_GetJavaVM(JNIEnv *env,JavaVM **vm){
    if(!native_jni_output_context(env))return JNI_ERR;
    if(native_original.ExceptionCheck(env))return JNI_ERR;
    JavaVM *value=NULL;
    jint result=native_original.GetJavaVM(env,vm?&value:NULL);
    if(result!=JNI_OK||native_original.ExceptionCheck(env))return result;
    if(vm&&!native_jni_output_commit(env,vm,&value,sizeof(value)))return JNI_ERR;
    return result;
}

/* Array lengths cannot change.  For an invalid interval no full-sized buffer
 * is needed: the real Region call retains its own bounds exception and cannot
 * write the dummy.  Required NULL arguments are still passed as NULL. */
#define NATIVE_JNI_OUTPUT_ARRAY(Name,Type) \
static void JNICALL native_jni_output_Get##Name##ArrayRegion(JNIEnv *env,Type##Array array,jsize start,jsize length,Type *buffer){ \
    if(!native_jni_output_context(env))return; \
    if(native_original.ExceptionCheck(env))return; \
    Type dummy=0,*scratch=NULL;SIZE_T bytes=0; \
    if(buffer&&array&&start>=0&&length>0){ \
        jsize total=native_original.GetArrayLength(env,array); \
        if(native_original.ExceptionCheck(env))return; \
        if(start<=total&&length<=total-start){ \
            if((SIZE_T)length>SIZE_MAX/sizeof(*scratch)){native_jni_output_oom(env);return;} \
            bytes=(SIZE_T)length*sizeof(*scratch);scratch=(Type*)malloc(bytes); \
            if(!scratch){native_jni_output_oom(env);return;} \
        } \
    } \
    native_original.Get##Name##ArrayRegion(env,array,start,length,buffer?(scratch?scratch:&dummy):NULL); \
    if(bytes&&!native_original.ExceptionCheck(env))native_jni_output_commit(env,buffer,scratch,bytes); \
    free(scratch); \
}

NATIVE_JNI_OUTPUT_ARRAY(Boolean,jboolean)
NATIVE_JNI_OUTPUT_ARRAY(Byte,jbyte)
NATIVE_JNI_OUTPUT_ARRAY(Char,jchar)
NATIVE_JNI_OUTPUT_ARRAY(Short,jshort)
NATIVE_JNI_OUTPUT_ARRAY(Int,jint)
NATIVE_JNI_OUTPUT_ARRAY(Long,jlong)
NATIVE_JNI_OUTPUT_ARRAY(Float,jfloat)
NATIVE_JNI_OUTPUT_ARRAY(Double,jdouble)

static void JNICALL native_jni_output_GetStringRegion(JNIEnv *env,jstring string,jsize start,jsize length,jchar *buffer){
    if(!native_jni_output_context(env))return;
    if(native_original.ExceptionCheck(env))return;
    jchar dummy=0,*scratch=NULL;SIZE_T bytes=0;
    if(buffer&&string&&start>=0&&length>0){
        jsize total=native_original.GetStringLength(env,string);
        if(native_original.ExceptionCheck(env))return;
        if(start<=total&&length<=total-start){
            if((SIZE_T)length>SIZE_MAX/sizeof(*scratch)){native_jni_output_oom(env);return;}
            bytes=(SIZE_T)length*sizeof(*scratch);scratch=(jchar*)malloc(bytes);
            if(!scratch){native_jni_output_oom(env);return;}
        }
    }
    native_original.GetStringRegion(env,string,start,length,buffer?(scratch?scratch:&dummy):NULL);
    if(bytes&&!native_original.ExceptionCheck(env))native_jni_output_commit(env,buffer,scratch,bytes);
    free(scratch);
}

static void JNICALL native_jni_output_GetStringUTFRegion(JNIEnv *env,jstring string,jsize start,jsize length,char *buffer){
    if(!native_jni_output_context(env))return;
    if(native_original.ExceptionCheck(env))return;
    unsigned char dummy=0xff,*scratch=NULL;SIZE_T capacity=1;
    if(buffer&&string&&start>=0&&length>0){
        jsize total=native_original.GetStringLength(env,string);
        if(native_original.ExceptionCheck(env))return;
        if(start<=total&&length<=total-start){
            if((SIZE_T)length>(SIZE_MAX-1)/3){native_jni_output_oom(env);return;}
            capacity=(SIZE_T)length*3+1;scratch=(unsigned char*)malloc(capacity);
            if(!scratch){native_jni_output_oom(env);return;}
            memset(scratch,0xff,capacity);
        }
    }
    unsigned char *actual=scratch?scratch:&dummy;
    native_original.GetStringUTFRegion(env,string,start,length,buffer?(char*)actual:NULL);
    if(buffer&&!native_original.ExceptionCheck(env)){
        /* Modified UTF-8 contains neither a literal zero in its payload nor
         * 0xff.  Copy only bytes the VM wrote, including its terminator when
         * present, without publishing unused worst-case capacity.  JDK 17 also
         * writes one zero for a successful non-NULL, zero-length destination. */
        SIZE_T written=0;
        while(written<capacity&&actual[written]!=0xff){
            if(actual[written++]==0)break;
        }
        if(written)native_jni_output_commit(env,buffer,actual,written);
    }
    free(scratch);
}

static const jchar *JNICALL native_jni_output_GetStringChars(JNIEnv *env,jstring string,jboolean *isCopy){
    if(!native_jni_output_context(env))return NULL;
    if(native_original.ExceptionCheck(env))return NULL;
    jboolean copied=JNI_FALSE;
    const jchar *characters=native_original.GetStringChars(env,string,isCopy?&copied:NULL);
    if(!characters)return NULL;
    if(native_original.ExceptionCheck(env)||(isCopy&&!native_jni_output_commit(env,isCopy,&copied,sizeof(copied)))){
        native_original.ReleaseStringChars(env,string,characters);return NULL;
    }
    return characters;
}

static const char *JNICALL native_jni_output_GetStringUTFChars(JNIEnv *env,jstring string,jboolean *isCopy){
    if(!native_jni_output_context(env))return NULL;
    if(native_original.ExceptionCheck(env))return NULL;
    jboolean copied=JNI_FALSE;
    const char *characters=native_original.GetStringUTFChars(env,string,isCopy?&copied:NULL);
    if(!characters)return NULL;
    if(native_original.ExceptionCheck(env)||(isCopy&&!native_jni_output_commit(env,isCopy,&copied,sizeof(copied)))){
        native_original.ReleaseStringUTFChars(env,string,characters);return NULL;
    }
    return characters;
}

static void native_jni_outputs_install(struct JNINativeInterface_ *table){
    table->GetJavaVM=native_jni_output_GetJavaVM;
#define NATIVE_JNI_OUTPUT_INSTALL_ARRAY(Name) table->Get##Name##ArrayRegion=native_jni_output_Get##Name##ArrayRegion
    NATIVE_JNI_OUTPUT_INSTALL_ARRAY(Boolean);
    NATIVE_JNI_OUTPUT_INSTALL_ARRAY(Byte);
    NATIVE_JNI_OUTPUT_INSTALL_ARRAY(Char);
    NATIVE_JNI_OUTPUT_INSTALL_ARRAY(Short);
    NATIVE_JNI_OUTPUT_INSTALL_ARRAY(Int);
    NATIVE_JNI_OUTPUT_INSTALL_ARRAY(Long);
    NATIVE_JNI_OUTPUT_INSTALL_ARRAY(Float);
    NATIVE_JNI_OUTPUT_INSTALL_ARRAY(Double);
#undef NATIVE_JNI_OUTPUT_INSTALL_ARRAY
    table->GetStringRegion=native_jni_output_GetStringRegion;
    table->GetStringUTFRegion=native_jni_output_GetStringUTFRegion;
    table->GetStringChars=native_jni_output_GetStringChars;
    table->GetStringUTFChars=native_jni_output_GetStringUTFChars;
}

#undef NATIVE_JNI_OUTPUT_ARRAY
