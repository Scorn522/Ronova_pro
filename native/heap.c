/* Actual field readers for control metadata; no field-write history. */
/* Positive field-location hints only; no holder, value or authorization is retained. */
typedef struct HeapField {
    jweak declaring;jfieldID field;char *name,*descriptor;struct HeapField *next;
} HeapField;
enum { HEAP_FIELD_BUCKETS=64 };
static SRWLOCK heap_records=SRWLOCK_INIT;
static HeapField *heap_fields[HEAP_FIELD_BUCKETS];
static unsigned heap_field_bucket(const char *name,const char *descriptor){
    uint32_t hash=2166136261u;
    for(const unsigned char *at=(const unsigned char*)name;*at;at++)hash=(hash^*at)*16777619u;
    hash=(hash^0xffu)*16777619u;
    for(const unsigned char *at=(const unsigned char*)descriptor;*at;at++)hash=(hash^*at)*16777619u;
    return hash&(HEAP_FIELD_BUCKETS-1);
}
static void heap_fields_free(JNIEnv *env,HeapField *fields){
    while(fields){HeapField *next=fields->next;
        if(fields->declaring)native_original.DeleteWeakGlobalRef(env,fields->declaring);
        free(fields->name);free(fields->descriptor);free(fields);fields=next;
    }
}
static jfieldID heap_field_candidate(JNIEnv *env,unsigned bucket,jclass declaring,const char *name,const char *descriptor){
    jfieldID field=NULL;AcquireSRWLockShared(&heap_records);
    for(HeapField *entry=heap_fields[bucket];entry;entry=entry->next)if(!strcmp(entry->name,name)&&!strcmp(entry->descriptor,descriptor)
            &&native_original.IsSameObject(env,entry->declaring,declaring)){field=entry->field;break;}
    ReleaseSRWLockShared(&heap_records);
    /* No record escapes the lock; the incoming declaring Class keeps its field ID alive. */
    return field;
}
static void heap_field_forget(JNIEnv *env,unsigned bucket,jclass declaring,jfieldID field,const char *name,const char *descriptor){
    HeapField *retired=NULL;AcquireSRWLockExclusive(&heap_records);HeapField **at=&heap_fields[bucket];
    while(*at){HeapField *entry=*at;
        if(native_original.IsSameObject(env,entry->declaring,NULL)||(entry->field==field&&!strcmp(entry->name,name)
                &&!strcmp(entry->descriptor,descriptor)&&native_original.IsSameObject(env,entry->declaring,declaring))){
            *at=entry->next;entry->next=retired;retired=entry;continue;
        }
        at=&entry->next;
    }
    ReleaseSRWLockExclusive(&heap_records);heap_fields_free(env,retired);
}
static void heap_field_remember(JNIEnv *env,unsigned bucket,jclass declaring,jfieldID field,const char *name,const char *descriptor){
    HeapField *entry=(HeapField*)calloc(1,sizeof(*entry));if(!entry)return;
    entry->field=field;entry->name=_strdup(name);entry->descriptor=_strdup(descriptor);
    if(entry->name&&entry->descriptor)entry->declaring=native_original.NewWeakGlobalRef(env,declaring);
    if(!entry->declaring||native_original.ExceptionCheck(env)){heap_fields_free(env,entry);return;}
    HeapField *retired=NULL;AcquireSRWLockExclusive(&heap_records);HeapField **at=&heap_fields[bucket];
    while(*at){HeapField *prior=*at;
        if(native_original.IsSameObject(env,prior->declaring,NULL)||(!strcmp(prior->name,name)&&!strcmp(prior->descriptor,descriptor)
                &&native_original.IsSameObject(env,prior->declaring,declaring))){
            *at=prior->next;prior->next=retired;retired=prior;continue;
        }
        at=&prior->next;
    }
    entry->next=heap_fields[bucket];heap_fields[bucket]=entry;
    ReleaseSRWLockExclusive(&heap_records);heap_fields_free(env,retired);
}
static int heap_field_named(jclass declaring,jfieldID field,const char *name,const char *descriptor){
    char *actual=NULL,*signature=NULL;
    int same=(*native_ti)->GetFieldName(native_ti,declaring,field,&actual,&signature,NULL)==JVMTI_ERROR_NONE
            &&actual&&signature&&!strcmp(actual,name)&&!strcmp(signature,descriptor);
    if(actual)(*native_ti)->Deallocate(native_ti,(unsigned char*)actual);
    if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);return same;
}
static jfieldID heap_read_field(JNIEnv *env,jclass declaring,const char *name,const char *descriptor,jint *access){
    unsigned bucket=heap_field_bucket(name,descriptor);jfieldID field=heap_field_candidate(env,bucket,declaring,name,descriptor);
    if(field){
        jclass actual=NULL;
        int same=(*native_ti)->GetFieldDeclaringClass(native_ti,declaring,field,&actual)==JVMTI_ERROR_NONE
                &&actual&&native_original.IsSameObject(env,actual,declaring);
        if(actual)native_original.DeleteLocalRef(env,actual);
        if(same&&heap_field_named(declaring,field,name,descriptor)
                &&(*native_ti)->GetFieldModifiers(native_ti,declaring,field,access)==JVMTI_ERROR_NONE
                &&!native_original.ExceptionCheck(env))return field;
        if(native_original.ExceptionCheck(env))return NULL;
        heap_field_forget(env,bucket,declaring,field,name,descriptor);field=NULL;
    }
    jfieldID *fields=NULL;jint count=0;
    if((*native_ti)->GetClassFields(native_ti,declaring,&count,&fields)==JVMTI_ERROR_NONE)for(jint i=0;i<count;i++){
        if(heap_field_named(declaring,fields[i],name,descriptor)){field=fields[i];break;}
    }
    if(fields)(*native_ti)->Deallocate(native_ti,(unsigned char*)fields);
    if(!field||(*native_ti)->GetFieldModifiers(native_ti,declaring,field,access)!=JVMTI_ERROR_NONE
            ||native_original.ExceptionCheck(env))return NULL;
    heap_field_remember(env,bucket,declaring,field,name,descriptor);
    return native_original.ExceptionCheck(env)?NULL:field;
}
JNIEXPORT jobjectArray JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_heapReadField0(JNIEnv *env,jclass controller,jobject holder,jclass declaring,jstring name,jstring descriptor){
    (void)controller;
    if(!code_caller(env)){
        native_refuse(env,"ACTUAL_CODE_FIELD_BRIDGE_REQUIRED");return NULL;
    }
    if(!holder||!declaring||!name||!descriptor)return NULL;
    const char *key=native_original.GetStringUTFChars(env,name,NULL),*desc=key?native_original.GetStringUTFChars(env,descriptor,NULL):NULL;
    jint access=0,status=0;jobject value=NULL;jobjectArray result=NULL;
    jfieldID field=key&&desc?heap_read_field(env,declaring,key,desc,&access):NULL;
    if(!field)goto read_done;
    int statik=(access&0x0008)!=0;
    if(statik){
        if(!native_original.IsSameObject(env,holder,declaring)||(*native_ti)->GetClassStatus(native_ti,declaring,&status)!=JVMTI_ERROR_NONE
                ||!(status&JVMTI_CLASS_STATUS_INITIALIZED))goto read_done;
    }else if(!native_original.IsInstanceOf(env,holder,declaring))goto read_done;
    if(desc[0]=='L'||desc[0]=='[')value=statik?native_original.GetStaticObjectField(env,declaring,field):native_original.GetObjectField(env,holder,field);
    else{
#define READ_HEAP_PRIMITIVE(KIND,NAME,SLOT,BOX,SIG) case KIND:{ \
        jvalue primitive;memset(&primitive,0,sizeof(primitive)); \
        primitive.SLOT=statik?native_original.GetStatic##NAME##Field(env,declaring,field):native_original.Get##NAME##Field(env,holder,field); \
        if(!native_original.ExceptionCheck(env))value=native_box(env,BOX,SIG,primitive);break;}
        switch(desc[0]){
            READ_HEAP_PRIMITIVE('Z',Boolean,z,"java/lang/Boolean","(Z)Ljava/lang/Boolean;")
            READ_HEAP_PRIMITIVE('B',Byte,b,"java/lang/Byte","(B)Ljava/lang/Byte;")
            READ_HEAP_PRIMITIVE('C',Char,c,"java/lang/Character","(C)Ljava/lang/Character;")
            READ_HEAP_PRIMITIVE('S',Short,s,"java/lang/Short","(S)Ljava/lang/Short;")
            READ_HEAP_PRIMITIVE('I',Int,i,"java/lang/Integer","(I)Ljava/lang/Integer;")
            READ_HEAP_PRIMITIVE('J',Long,j,"java/lang/Long","(J)Ljava/lang/Long;")
            READ_HEAP_PRIMITIVE('F',Float,f,"java/lang/Float","(F)Ljava/lang/Float;")
            READ_HEAP_PRIMITIVE('D',Double,d,"java/lang/Double","(D)Ljava/lang/Double;")
            default:goto read_done;
        }
#undef READ_HEAP_PRIMITIVE
        if(!value)goto read_done;
    }
    if(!native_original.ExceptionCheck(env)){
        jclass object=native_original.FindClass(env,"java/lang/Object");
        if(object){result=native_original.NewObjectArray(env,1,object,value);native_original.DeleteLocalRef(env,object);}
    }
read_done:
    if(value)native_original.DeleteLocalRef(env,value);
    if(key)native_original.ReleaseStringUTFChars(env,name,key);if(desc)native_original.ReleaseStringUTFChars(env,descriptor,desc);return result;
}
static int heap_array_span(JNIEnv *env,jobject array,jlong offset,jint count){
    if(!array||!native_ti||offset<0||count<0)return 0;
    jclass type=native_original.GetObjectClass(env,array);char *signature=NULL;size_t width=0;
    if(type&&(*native_ti)->GetClassSignature(native_ti,type,&signature,NULL)==JVMTI_ERROR_NONE&&signature&&signature[0]=='['){
        switch(signature[1]){
            case 'Z':case 'B':width=1;break;case 'C':case 'S':width=2;break;
            case 'I':case 'F':width=4;break;case 'J':case 'D':width=8;break;default:break;
        }
    }
    if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);if(type)native_original.DeleteLocalRef(env,type);
    if(!width)return 0;jsize length=native_original.GetArrayLength(env,(jarray)array);jlong bytes=(jlong)length*(jlong)width;
    return !native_original.ExceptionCheck(env)&&offset<=bytes&&(jlong)count<=bytes-offset;
}
static void heap_image_unavailable(JNIEnv *env){
    if(native_original.ExceptionCheck(env))return;
    jclass type=native_original.FindClass(env,"dev/ronova/pro/bootstrap/NativeControl$HeapImageUnavailable");
    if(type){native_original.ThrowNew(env,type,"HEAP_ARRAY_IMAGE_UNAVAILABLE");native_original.DeleteLocalRef(env,type);}
}
JNIEXPORT jlong JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_heapArrayBits0(JNIEnv *env,jclass controller,jobject array,jlong offset,jint count){
    (void)controller;
    if(!code_caller(env)){
        native_refuse(env,"ACTUAL_CODE_ARRAY_BRIDGE_REQUIRED");return 0;
    }
    if(count<=0||count>8||!heap_array_span(env,array,offset,count)){heap_image_unavailable(env);return 0;}
    NativeThread *state=native_thread();if(!state){heap_image_unavailable(env);return 0;}
    unsigned char bytes[8]={0};state->control++;
    void *actual=native_original.GetPrimitiveArrayCritical(env,(jarray)array,NULL);
    if(actual){memcpy(bytes,(unsigned char*)actual+(size_t)offset,(size_t)count);native_original.ReleasePrimitiveArrayCritical(env,(jarray)array,actual,JNI_ABORT);}
    state->control--;
    if(!actual||native_original.ExceptionCheck(env)){heap_image_unavailable(env);return 0;}
    // Pack memory-order bytes, not Java primitive values. This retains partial
    // elements, NaN payloads, signed zero and noncanonical boolean bytes exactly.
    uint64_t bits=0;for(jint i=0;i<count;i++)bits|=(uint64_t)bytes[i]<<(i*8);return (jlong)bits;
}
JNIEXPORT jbyteArray JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_heapArrayImage0(JNIEnv *env,jclass controller,jobject array,jlong offset,jint count){
    (void)controller;
    if(!code_caller(env)){
        native_refuse(env,"ACTUAL_CODE_ARRAY_BRIDGE_REQUIRED");return NULL;
    }
    if(!heap_array_span(env,array,offset,count))return NULL;
    NativeThread *state=native_thread();if(!state)return NULL;state->control++;
    jbyteArray result=native_original.NewByteArray(env,count);unsigned char *copy=result?(unsigned char*)malloc(count?count:1):NULL;
    if(!copy){if(result)native_original.DeleteLocalRef(env,result);state->control--;return NULL;}
    // The Critical pair contains only the bounded memory copy. Java callbacks,
    // allocation and JNI publication happen after the original array is released.
    void *actual=native_original.GetPrimitiveArrayCritical(env,(jarray)array,NULL);
    if(actual){memcpy(copy,(unsigned char*)actual+(size_t)offset,(size_t)count);native_original.ReleasePrimitiveArrayCritical(env,(jarray)array,actual,JNI_ABORT);}
    if(actual&&!native_original.ExceptionCheck(env))native_original.SetByteArrayRegion(env,result,0,count,(jbyte*)copy);
    free(copy);state->control--;
    if(!actual||native_original.ExceptionCheck(env)){native_original.DeleteLocalRef(env,result);return NULL;}return result;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_restoreHeapArrayBytes0(JNIEnv *env,jclass controller,jobject array,jlong offset,jbyteArray expected,jbyteArray incoming){
    (void)controller;jmethodID writer=native_original.GetStaticMethodID(env,native_controls,"recoveryWriter","()Z");
    if(!writer||!native_original.CallStaticBooleanMethod(env,native_controls,writer)){native_refuse(env,"HEAP_RECOVERY_CORE_REQUIRED");return JNI_FALSE;}
    if(!expected||!incoming)return JNI_FALSE;
    jsize count=native_original.GetArrayLength(env,expected);
    if(native_original.ExceptionCheck(env)||native_original.GetArrayLength(env,incoming)!=count||!heap_array_span(env,array,offset,count))return JNI_FALSE;
    NativeThread *state=native_thread();if(!state)return JNI_FALSE;state->control++;
    unsigned char *before=(unsigned char*)malloc(count?count:1),*after=(unsigned char*)malloc(count?count:1);jboolean restored=JNI_FALSE;
    if(!before||!after)goto bytes_done;
    native_original.GetByteArrayRegion(env,expected,0,count,(jbyte*)before);if(native_original.ExceptionCheck(env))goto bytes_done;
    native_original.GetByteArrayRegion(env,incoming,0,count,(jbyte*)after);if(native_original.ExceptionCheck(env))goto bytes_done;
    void *actual=native_original.GetPrimitiveArrayCritical(env,(jarray)array,NULL);int changed=0;
    if(actual){
        if(memcmp((unsigned char*)actual+(size_t)offset,before,(size_t)count)==0){memcpy((unsigned char*)actual+(size_t)offset,after,(size_t)count);changed=1;}
        native_original.ReleasePrimitiveArrayCritical(env,(jarray)array,actual,changed?0:JNI_ABORT);
    }
    if(changed&&!native_original.ExceptionCheck(env)){
        actual=native_original.GetPrimitiveArrayCritical(env,(jarray)array,NULL);
        if(actual){restored=memcmp((unsigned char*)actual+(size_t)offset,after,(size_t)count)==0;
            native_original.ReleasePrimitiveArrayCritical(env,(jarray)array,actual,JNI_ABORT);}
    }
bytes_done:
    free(before);free(after);state->control--;return native_original.ExceptionCheck(env)?JNI_FALSE:restored;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_restoreArray0(JNIEnv *env,jclass controller,jobject array,jint index,jobject expected,jobject incoming){
    (void)controller;jmethodID writer=native_original.GetStaticMethodID(env,native_controls,"recoveryWriter","()Z");
    if(!writer||!native_original.CallStaticBooleanMethod(env,native_controls,writer)){native_refuse(env,"HEAP_RECOVERY_CORE_REQUIRED");return JNI_FALSE;}
    if(!array||!native_ti)return JNI_FALSE;
    jclass type=native_original.GetObjectClass(env,array);char *signature=NULL;jboolean restored=JNI_FALSE;
    if(!type||(*native_ti)->GetClassSignature(native_ti,type,&signature,NULL)!=JVMTI_ERROR_NONE||!signature||signature[0]!='[')goto array_finished;
    jsize length=native_original.GetArrayLength(env,(jarray)array);if(native_original.ExceptionCheck(env)||index<0||index>=length)goto array_finished;
    if(signature[1]=='L'||signature[1]=='['){
        jclass klass=native_original.GetObjectClass(env,type);jmethodID component=klass?native_original.GetMethodID(env,klass,"getComponentType","()Ljava/lang/Class;"):NULL;
        jclass actual=component?(jclass)native_original.CallObjectMethod(env,type,component):NULL;
        if(klass)native_original.DeleteLocalRef(env,klass);
        if(!actual||incoming&&!native_original.IsInstanceOf(env,incoming,actual)){if(actual)native_original.DeleteLocalRef(env,actual);goto array_finished;}
        native_original.DeleteLocalRef(env,actual);
        jobject current=native_original.GetObjectArrayElement(env,(jobjectArray)array,index);
        if(!native_original.ExceptionCheck(env)&&native_original.IsSameObject(env,current,expected)){
            native_original.SetObjectArrayElement(env,(jobjectArray)array,index,incoming);
            if(!native_original.ExceptionCheck(env)){jobject after=native_original.GetObjectArrayElement(env,(jobjectArray)array,index);restored=native_original.IsSameObject(env,after,incoming);if(after)native_original.DeleteLocalRef(env,after);}
        }
        if(current)native_original.DeleteLocalRef(env,current);
    }else{
#define RESTORE_HEAP_ARRAY(KIND,NAME,TYPE,BOX,ACCESSOR,SIG) case KIND:{ \
        jclass boxed=native_original.FindClass(env,BOX);jmethodID access=boxed?native_original.GetMethodID(env,boxed,ACCESSOR,SIG):NULL; \
        if(expected&&incoming&&boxed&&access&&native_original.IsInstanceOf(env,expected,boxed)&&native_original.IsInstanceOf(env,incoming,boxed)){ \
            TYPE prior=native_original.Call##NAME##Method(env,expected,access),value=native_original.Call##NAME##Method(env,incoming,access),current=0,after=0; \
            if(!native_original.ExceptionCheck(env))native_original.Get##NAME##ArrayRegion(env,(TYPE##Array)array,index,1,&current); \
            if(!native_original.ExceptionCheck(env)&&memcmp(&prior,&current,sizeof(TYPE))==0){ \
                native_original.Set##NAME##ArrayRegion(env,(TYPE##Array)array,index,1,&value); \
                if(!native_original.ExceptionCheck(env))native_original.Get##NAME##ArrayRegion(env,(TYPE##Array)array,index,1,&after); \
                restored=!native_original.ExceptionCheck(env)&&memcmp(&after,&value,sizeof(TYPE))==0;}} \
        if(boxed)native_original.DeleteLocalRef(env,boxed);break;}
        switch(signature[1]){
            RESTORE_HEAP_ARRAY('Z',Boolean,jboolean,"java/lang/Boolean","booleanValue","()Z")
            RESTORE_HEAP_ARRAY('B',Byte,jbyte,"java/lang/Byte","byteValue","()B")
            RESTORE_HEAP_ARRAY('C',Char,jchar,"java/lang/Character","charValue","()C")
            RESTORE_HEAP_ARRAY('S',Short,jshort,"java/lang/Short","shortValue","()S")
            RESTORE_HEAP_ARRAY('I',Int,jint,"java/lang/Integer","intValue","()I")
            RESTORE_HEAP_ARRAY('J',Long,jlong,"java/lang/Long","longValue","()J")
            RESTORE_HEAP_ARRAY('F',Float,jfloat,"java/lang/Float","floatValue","()F")
            RESTORE_HEAP_ARRAY('D',Double,jdouble,"java/lang/Double","doubleValue","()D")
            default:break;
        }
#undef RESTORE_HEAP_ARRAY
    }
array_finished:
    if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);if(type)native_original.DeleteLocalRef(env,type);
    return native_original.ExceptionCheck(env)?JNI_FALSE:restored;
}
