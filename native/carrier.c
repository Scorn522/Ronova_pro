/* Read only actual instance declarations/values. No caller-supplied address or
 * offset is accepted, and no Java accessor, constructor or class loader runs. */
typedef jlong (JNICALL *NativeCarrierOffset)(JNIEnv*,jobject,jobject);
typedef jint (JNICALL *NativeCarrierScale)(JNIEnv*,jobject,jclass);
typedef struct NativeCarrierMetadata {NativeCarrierOffset offset;NativeCarrierScale scale;int ready;} NativeCarrierMetadata;
static NativeCarrierMetadata native_carrier_metadata;
static jclass native_carrier_field;
static jfieldID native_carrier_field_owner,native_carrier_field_name,native_carrier_field_type;
static SRWLOCK native_carrier_field_lock=SRWLOCK_INIT;

static void native_carrier_metadata_registered(JNIEnv *env,jclass type,const JNINativeMethod *methods,jmethodID *actual,jint count,void *caller){
    if(!methods||!actual||count<0||!code_vm_layout.image||native_image(caller)!=code_vm_layout.image
            ||!native_original.IsSameObject(env,type,native_unsafe_class))return;
    NativeCarrierOffset offset=NULL;NativeCarrierScale scale=NULL;int valid=1;
    for(jint i=0;i<count&&valid;i++){
        int field=!strcmp(methods[i].name,"objectFieldOffset0")&&!strcmp(methods[i].signature,"(Ljava/lang/reflect/Field;)J");
        int array=!strcmp(methods[i].name,"arrayIndexScale0")&&!strcmp(methods[i].signature,"(Ljava/lang/Class;)I");
        if(!field&&!array)continue;
        jclass owner=NULL;char *name=NULL,*descriptor=NULL;jint modifiers=0;
        valid=actual[i]&&native_image(methods[i].fnPtr)==code_vm_layout.image
                &&(*native_ti)->GetMethodDeclaringClass(native_ti,actual[i],&owner)==JVMTI_ERROR_NONE
                &&owner&&native_original.IsSameObject(env,owner,type)
                &&(*native_ti)->GetMethodName(native_ti,actual[i],&name,&descriptor,NULL)==JVMTI_ERROR_NONE
                &&name&&descriptor&&!strcmp(name,methods[i].name)&&!strcmp(descriptor,methods[i].signature)
                &&(*native_ti)->GetMethodModifiers(native_ti,actual[i],&modifiers)==JVMTI_ERROR_NONE
                &&(modifiers&0x0108)==0x0100;
        if(valid&&field){if(offset)valid=0;else offset=(NativeCarrierOffset)methods[i].fnPtr;}
        if(valid&&array){if(scale)valid=0;else scale=(NativeCarrierScale)methods[i].fnPtr;}
        if(name)(*native_ti)->Deallocate(native_ti,(unsigned char*)name);if(descriptor)(*native_ti)->Deallocate(native_ti,(unsigned char*)descriptor);
        if(owner)native_original.DeleteLocalRef(env,owner);
    }
    if(!valid||!offset||!scale||native_original.ExceptionCheck(env))return;
    // registerNatives may still be inside Unsafe.<clinit>. Retain only the
    // actual VM entries here; obtaining its initialized singleton is a read
    // performed later, when a Core query actually needs the entries.
    AcquireSRWLockExclusive(&native_records);
    if(!native_carrier_metadata.ready){
        native_carrier_metadata.offset=offset;native_carrier_metadata.scale=scale;native_carrier_metadata.ready=1;
    }
    ReleaseSRWLockExclusive(&native_records);
}
static jobject native_carrier_unsafe(JNIEnv *env){
    jint status=0,modifiers=0;char *descriptor=NULL;
    if(!native_unsafe_class||(*native_ti)->GetClassStatus(native_ti,native_unsafe_class,&status)!=JVMTI_ERROR_NONE
            ||!(status&JVMTI_CLASS_STATUS_INITIALIZED))return NULL;
    jfieldID singleton=native_network_declared_field(env,native_unsafe_class,"theUnsafe",&descriptor,&modifiers);
    int valid=singleton&&descriptor&&!strcmp(descriptor,"Ljdk/internal/misc/Unsafe;")&&(modifiers&0x0008);
    if(descriptor)(*native_ti)->Deallocate(native_ti,(unsigned char*)descriptor);
    jobject unsafe=valid&&!native_original.ExceptionCheck(env)?native_original.GetStaticObjectField(env,native_unsafe_class,singleton):NULL;
    jclass unsafeType=unsafe?native_original.GetObjectClass(env,unsafe):NULL;
    valid=unsafeType&&native_original.IsSameObject(env,unsafeType,native_unsafe_class)&&!native_original.ExceptionCheck(env);
    if(unsafeType)native_original.DeleteLocalRef(env,unsafeType);
    if(!valid&&unsafe){native_original.DeleteLocalRef(env,unsafe);unsafe=NULL;}return unsafe;
}

static int native_carrier_reader(JNIEnv *env,jclass controller){
    if(!native_jni_ready||!native_ti||!native_controller||!native_original.IsSameObject(env,controller,native_controller)
            ||native_original.ExceptionCheck(env))return 0;
    // Authenticate the real Core stack even when JNI directly calls this private export.
    jmethodID reader=native_original.GetStaticMethodID(env,native_controller,"carrierReader","()Z");
    int allowed=reader&&native_original.CallStaticBooleanMethod(env,native_controller,reader)&&!native_original.ExceptionCheck(env);
    if(!allowed&&!native_original.ExceptionCheck(env))native_refuse(env,"ACTUAL_CARRIER_CORE_READER_REQUIRED");return allowed;
}
static jclass native_carrier_reflection(JNIEnv *env){
    jclass result=NULL;AcquireSRWLockShared(&native_carrier_field_lock);
    if(native_carrier_field)result=(jclass)native_original.NewLocalRef(env,native_carrier_field);
    ReleaseSRWLockShared(&native_carrier_field_lock);if(result||native_original.ExceptionCheck(env))return result;
    jint count=0;jclass *classes=NULL;
    if((*native_ti)->GetLoadedClasses(native_ti,&count,&classes)!=JVMTI_ERROR_NONE||count<0)return NULL;
    for(jint i=0;i<count&&!result&&!native_original.ExceptionCheck(env);i++){
        jint status=0;
        if(native_bootstrap_class(env,classes[i],"Ljava/lang/reflect/Field;")
                &&(*native_ti)->GetClassStatus(native_ti,classes[i],&status)==JVMTI_ERROR_NONE&&(status&JVMTI_CLASS_STATUS_INITIALIZED))
            result=(jclass)native_original.NewLocalRef(env,classes[i]);
    }
    if(classes){for(jint i=0;i<count;i++)if(classes[i])native_original.DeleteLocalRef(env,classes[i]);(*native_ti)->Deallocate(native_ti,(unsigned char*)classes);}
    if(!result)return NULL;
    const char *names[]={"clazz","name","type"},*signatures[]={"Ljava/lang/Class;","Ljava/lang/String;","Ljava/lang/Class;"};
    jfieldID fields[3]={0};int valid=1;
    for(unsigned i=0;i<3&&valid;i++){
        char *descriptor=NULL;jint modifiers=0;fields[i]=native_network_declared_field(env,result,names[i],&descriptor,&modifiers);
        valid=fields[i]&&descriptor&&!strcmp(descriptor,signatures[i])&&!(modifiers&0x0008);
        if(descriptor)(*native_ti)->Deallocate(native_ti,(unsigned char*)descriptor);
    }
    jclass retained=valid&&!native_original.ExceptionCheck(env)?(jclass)native_original.NewGlobalRef(env,result):NULL;
    if(!retained){native_original.DeleteLocalRef(env,result);return NULL;}
    AcquireSRWLockExclusive(&native_carrier_field_lock);
    if(!native_carrier_field){
        native_carrier_field_owner=fields[0];native_carrier_field_name=fields[1];native_carrier_field_type=fields[2];
        native_carrier_field=retained;retained=NULL;
    }
    ReleaseSRWLockExclusive(&native_carrier_field_lock);if(retained)native_original.DeleteGlobalRef(env,retained);return result;
}

typedef struct NativeCarrierTypes {jobject loader;jclass *types;jint count;char *owner;} NativeCarrierTypes;
static void native_carrier_types_clear(JNIEnv *env,NativeCarrierTypes *types){
    if(types->types){for(jint i=0;i<types->count;i++)if(types->types[i])native_original.DeleteLocalRef(env,types->types[i]);(*native_ti)->Deallocate(native_ti,(unsigned char*)types->types);}
    if(types->loader)native_original.DeleteLocalRef(env,types->loader);if(types->owner)(*native_ti)->Deallocate(native_ti,(unsigned char*)types->owner);
    memset(types,0,sizeof(*types));
}
static int native_carrier_types(JNIEnv *env,jclass owner,NativeCarrierTypes *types){
    return (*native_ti)->GetClassLoader(native_ti,owner,&types->loader)==JVMTI_ERROR_NONE
            &&(*native_ti)->GetClassSignature(native_ti,owner,&types->owner,NULL)==JVMTI_ERROR_NONE
            &&(*native_ti)->GetClassLoaderClasses(native_ti,types->loader,&types->count,&types->types)==JVMTI_ERROR_NONE
            &&types->count>=0&&!native_original.ExceptionCheck(env);
}
static int native_carrier_type_ready(JNIEnv *env,NativeCarrierTypes *types,const char *signature){
    const char *component=signature;while(*component=='[')component++;
    if(*component!='L')return component[0]&&component[1]==0&&strchr("ZBCSIJFD",component[0])!=NULL;
    // An array class can be created by the VM without Java code once its
    // component is already known. Hidden self types retain their actual holder.
    if(types->owner&&!strcmp(component,types->owner))return 1;
    for(jint i=0;i<types->count&&!native_original.ExceptionCheck(env);i++){
        char *actual=NULL;int valid=(*native_ti)->GetClassSignature(native_ti,types->types[i],&actual,NULL)==JVMTI_ERROR_NONE&&actual;
        int matched=valid&&!strcmp(component,actual);if(actual)(*native_ti)->Deallocate(native_ti,(unsigned char*)actual);
        if(!valid)return 0;if(matched)return 1;
    }
    return 0;
}
static jfieldID native_carrier_declared(JNIEnv *env,jclass declaring,const char *name,const char *descriptor,jint *modifiers){
    jfieldID *fields=NULL,selected=NULL;jint count=0;int valid=1;
    if((*native_ti)->GetClassFields(native_ti,declaring,&count,&fields)!=JVMTI_ERROR_NONE||count<0)valid=0;
    for(jint i=0;i<count&&valid;i++){
        char *actual=NULL,*signature=NULL;jclass owner=NULL;
        valid=(*native_ti)->GetFieldName(native_ti,declaring,fields[i],&actual,&signature,NULL)==JVMTI_ERROR_NONE&&actual&&signature;
        if(valid&&!strcmp(actual,name)&&!strcmp(signature,descriptor)){
            valid=!selected&&(*native_ti)->GetFieldDeclaringClass(native_ti,declaring,fields[i],&owner)==JVMTI_ERROR_NONE
                    &&owner&&native_original.IsSameObject(env,owner,declaring)
                    &&(*native_ti)->GetFieldModifiers(native_ti,declaring,fields[i],modifiers)==JVMTI_ERROR_NONE;
            if(valid)selected=fields[i];
        }
        if(actual)(*native_ti)->Deallocate(native_ti,(unsigned char*)actual);if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);
        if(owner)native_original.DeleteLocalRef(env,owner);
    }
    if(fields)(*native_ti)->Deallocate(native_ti,(unsigned char*)fields);return valid&&!native_original.ExceptionCheck(env)?selected:NULL;
}
static jobject native_carrier_value(JNIEnv *env,jobject receiver,jclass owner,jfieldID field,const char *descriptor,int statik,int *valid){
    if(descriptor[0]=='L'||descriptor[0]=='['){
        jobject value=statik?native_original.GetStaticObjectField(env,owner,field):native_original.GetObjectField(env,receiver,field);
        *valid=!native_original.ExceptionCheck(env);return value;
    }
    if(descriptor[1]){*valid=0;return NULL;}jvalue value={0};
#define CARRIER_PRIMITIVE(KIND,NAME,MEMBER) case KIND:value.MEMBER=statik?native_original.GetStatic##NAME##Field(env,owner,field):native_original.Get##NAME##Field(env,receiver,field);break;
    switch(descriptor[0]){
        CARRIER_PRIMITIVE('Z',Boolean,z) CARRIER_PRIMITIVE('B',Byte,b) CARRIER_PRIMITIVE('C',Char,c) CARRIER_PRIMITIVE('S',Short,s)
        CARRIER_PRIMITIVE('I',Int,i) CARRIER_PRIMITIVE('J',Long,j) CARRIER_PRIMITIVE('F',Float,f) CARRIER_PRIMITIVE('D',Double,d)
        default:*valid=0;return NULL;
    }
#undef CARRIER_PRIMITIVE
    jobject boxed=!native_original.ExceptionCheck(env)?native_network_box(env,descriptor[0],value):NULL;*valid=boxed&&!native_original.ExceptionCheck(env);return boxed;
}
static jobjectArray native_carrier_one(JNIEnv *env,jobject receiver,jclass declaring,jfieldID field,const char *descriptor,int statik){
    int valid=0;jobject value=native_carrier_value(env,receiver,declaring,field,descriptor,statik,&valid);
    jclass object=valid?native_original.FindClass(env,"java/lang/Object"):NULL;
    jobjectArray result=object?native_original.NewObjectArray(env,1,object,value):NULL;
    if(object)native_original.DeleteLocalRef(env,object);if(value)native_original.DeleteLocalRef(env,value);return result;
}

JNIEXPORT jobjectArray JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_carrierFields0(JNIEnv *env,jclass controller,jobject receiver){
    if(!native_carrier_reader(env,controller)||!receiver)return NULL;
    NativeThread *state=native_thread();if(!state)return NULL;state->control++;
    jobjectArray result=NULL;jclass reflection=NULL,current=NULL,object=NULL,rowType=NULL,array=NULL;jobject unsafe=NULL;
    NativeCarrierOffset offset=NULL;NativeCarrierScale scale=NULL;jint total=0,index=0,referenceWidth=0;int valid=1;
    AcquireSRWLockShared(&native_records);
    if(native_carrier_metadata.ready){offset=native_carrier_metadata.offset;scale=native_carrier_metadata.scale;}
    ReleaseSRWLockShared(&native_records);
    if(offset&&scale&&!native_original.ExceptionCheck(env))unsafe=native_carrier_unsafe(env);
    if(!offset||!scale||!unsafe||(reflection=native_carrier_reflection(env))==NULL)goto done;
    object=native_original.FindClass(env,"java/lang/Object");rowType=native_original.FindClass(env,"[Ljava/lang/Object;");array=native_original.FindClass(env,"[Ljava/lang/Object;");
    if(!object||!rowType||!array||native_original.ExceptionCheck(env))goto done;
    referenceWidth=scale(env,unsafe,array);if(native_original.ExceptionCheck(env)||(referenceWidth!=4&&referenceWidth!=8))goto done;
    current=native_original.GetObjectClass(env,receiver);
    while(current&&valid&&!native_original.ExceptionCheck(env)){
        jboolean isArray=JNI_FALSE;jfieldID *fields=NULL;jint count=0;
        valid=(*native_ti)->IsArrayClass(native_ti,current,&isArray)==JVMTI_ERROR_NONE;
        if(valid&&!isArray)valid=(*native_ti)->GetClassFields(native_ti,current,&count,&fields)==JVMTI_ERROR_NONE&&count>=0;
        for(jint i=0;i<count&&valid;i++){jint modifiers=0;valid=(*native_ti)->GetFieldModifiers(native_ti,current,fields[i],&modifiers)==JVMTI_ERROR_NONE;
            if(valid&&!(modifiers&0x0008)){if(total==INT32_MAX)valid=0;else total++;}}
        if(fields)(*native_ti)->Deallocate(native_ti,(unsigned char*)fields);
        jclass next=valid?native_original.GetSuperclass(env,current):NULL;native_original.DeleteLocalRef(env,current);current=next;
    }
    if(!valid||native_original.ExceptionCheck(env))goto done;
    result=native_original.NewObjectArray(env,total,rowType,NULL);current=result?native_original.GetObjectClass(env,receiver):NULL;
    while(current&&valid&&!native_original.ExceptionCheck(env)){
        jboolean isArray=JNI_FALSE;jfieldID *fields=NULL;jint count=0;NativeCarrierTypes types={0};
        valid=(*native_ti)->IsArrayClass(native_ti,current,&isArray)==JVMTI_ERROR_NONE;
        if(valid&&!isArray)valid=(*native_ti)->GetClassFields(native_ti,current,&count,&fields)==JVMTI_ERROR_NONE&&count>=0;
        int typesReady=0;
        for(jint i=0;i<count&&valid&&!native_original.ExceptionCheck(env);i++){
            jint modifiers=0;char *name=NULL,*descriptor=NULL;jclass declaring=NULL;
            valid=(*native_ti)->GetFieldModifiers(native_ti,current,fields[i],&modifiers)==JVMTI_ERROR_NONE;
            if(!valid||(modifiers&0x0008))continue;
            valid=(*native_ti)->GetFieldName(native_ti,current,fields[i],&name,&descriptor,NULL)==JVMTI_ERROR_NONE&&name&&descriptor
                    &&(*native_ti)->GetFieldDeclaringClass(native_ti,current,fields[i],&declaring)==JVMTI_ERROR_NONE
                    &&declaring&&native_original.IsSameObject(env,declaring,current);
            if(valid&&!typesReady){valid=native_carrier_types(env,current,&types);typesReady=valid;}
            if(valid)valid=native_carrier_type_ready(env,&types,descriptor);
            if(valid&&native_original.PushLocalFrame(env,16)==JNI_OK){
                jobject reflected=native_original.ToReflectedField(env,current,fields[i],JNI_FALSE);
                jclass type=reflected?(jclass)native_original.GetObjectField(env,reflected,native_carrier_field_type):NULL;
                jlong actualOffset=reflected&&!native_original.ExceptionCheck(env)?offset(env,unsafe,reflected):-1;
                jint width=descriptor[0]=='L'||descriptor[0]=='['?referenceWidth:descriptor[0]=='J'||descriptor[0]=='D'?8
                        :descriptor[0]=='I'||descriptor[0]=='F'?4:descriptor[0]=='S'||descriptor[0]=='C'?2:descriptor[0]=='Z'||descriptor[0]=='B'?1:0;
                int read=0;jobject value=type&&actualOffset>=0&&width>0&&!native_original.ExceptionCheck(env)?native_carrier_value(env,receiver,current,fields[i],descriptor,0,&read):NULL;
                jobjectArray row=read?native_original.NewObjectArray(env,8,object,NULL):NULL;
                jvalue number={0};number.j=actualOffset;jobject boxedOffset=row?native_network_box(env,'J',number):NULL;
                number.i=width;jobject boxedWidth=boxedOffset?native_network_box(env,'I',number):NULL;
                jstring fieldName=boxedWidth?native_original.NewStringUTF(env,name):NULL,signature=fieldName?native_original.NewStringUTF(env,descriptor):NULL;
                valid=row&&reflected&&type&&boxedOffset&&boxedWidth&&fieldName&&signature&&index<total&&!native_original.ExceptionCheck(env);
                if(valid){jobject values[8]={reflected,current,fieldName,signature,type,boxedOffset,boxedWidth,value};
                    for(jsize at=0;at<8&&!native_original.ExceptionCheck(env);at++)native_original.SetObjectArrayElement(env,row,at,values[at]);
                    native_original.SetObjectArrayElement(env,result,index++,row);valid=!native_original.ExceptionCheck(env);}
                native_original.PopLocalFrame(env,NULL);
            }else valid=0;
            if(name)(*native_ti)->Deallocate(native_ti,(unsigned char*)name);if(descriptor)(*native_ti)->Deallocate(native_ti,(unsigned char*)descriptor);
            if(declaring)native_original.DeleteLocalRef(env,declaring);
        }
        native_carrier_types_clear(env,&types);if(fields)(*native_ti)->Deallocate(native_ti,(unsigned char*)fields);
        jclass next=valid?native_original.GetSuperclass(env,current):NULL;native_original.DeleteLocalRef(env,current);current=next;
    }
    valid=valid&&result&&index==total&&!native_original.ExceptionCheck(env);
done:
    if(current)native_original.DeleteLocalRef(env,current);if(reflection)native_original.DeleteLocalRef(env,reflection);
    if(object)native_original.DeleteLocalRef(env,object);if(rowType)native_original.DeleteLocalRef(env,rowType);if(array)native_original.DeleteLocalRef(env,array);if(unsafe)native_original.DeleteLocalRef(env,unsafe);
    state->control--;
    if(!valid||!result||native_original.ExceptionCheck(env)){if(result)native_original.DeleteLocalRef(env,result);if(!native_original.ExceptionCheck(env))native_refuse(env,"CARRIER_INSTANCE_FIELD_METADATA_UNAVAILABLE");return NULL;}
    return result;
}

JNIEXPORT jobjectArray JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_carrierRead0(JNIEnv *env,jclass controller,jobject receiver,jobject reflected){
    if(!native_carrier_reader(env,controller)||!receiver||!reflected)return NULL;
    NativeThread *state=native_thread();if(!state)return NULL;state->control++;
    jobjectArray result=NULL;jclass reflection=native_carrier_reflection(env),actual=native_original.GetObjectClass(env,reflected),declaring=NULL,fieldType=NULL;
    jstring name=NULL;const char *key=NULL;char *descriptor=NULL;jint modifiers=0;
    if(!reflection||!actual||!native_original.IsSameObject(env,reflection,actual)||native_original.ExceptionCheck(env))goto done;
    declaring=(jclass)native_original.GetObjectField(env,reflected,native_carrier_field_owner);
    fieldType=(jclass)native_original.GetObjectField(env,reflected,native_carrier_field_type);name=(jstring)native_original.GetObjectField(env,reflected,native_carrier_field_name);
    if(!declaring||!fieldType||!name||!native_original.IsInstanceOf(env,receiver,declaring)
            ||(*native_ti)->GetClassSignature(native_ti,fieldType,&descriptor,NULL)!=JVMTI_ERROR_NONE||!descriptor)goto done;
    key=native_original.GetStringUTFChars(env,name,NULL);
    jfieldID field=key?native_carrier_declared(env,declaring,key,descriptor,&modifiers):NULL;
    if(field&&!(modifiers&0x0008)&&!native_original.ExceptionCheck(env))result=native_carrier_one(env,receiver,declaring,field,descriptor,0);
done:
    if(key)native_original.ReleaseStringUTFChars(env,name,key);if(descriptor)(*native_ti)->Deallocate(native_ti,(unsigned char*)descriptor);
    if(name)native_original.DeleteLocalRef(env,name);if(fieldType)native_original.DeleteLocalRef(env,fieldType);if(declaring)native_original.DeleteLocalRef(env,declaring);
    if(actual)native_original.DeleteLocalRef(env,actual);if(reflection)native_original.DeleteLocalRef(env,reflection);state->control--;
    if(!result||native_original.ExceptionCheck(env)){if(result)native_original.DeleteLocalRef(env,result);if(!native_original.ExceptionCheck(env))native_refuse(env,"CARRIER_FIELD_READ_UNAVAILABLE");return NULL;}return result;
}
JNIEXPORT jobjectArray JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_carrierStaticValue0(JNIEnv *env,jclass controller,jclass declaring,jstring name,jstring signature){
    if(!native_carrier_reader(env,controller)||!declaring||!name||!signature)return NULL;
    NativeThread *state=native_thread();if(!state)return NULL;state->control++;
    jobjectArray result=NULL;const char *key=native_original.GetStringUTFChars(env,name,NULL),*descriptor=key?native_original.GetStringUTFChars(env,signature,NULL):NULL;
    jint modifiers=0,status=0;jfieldID field=key&&descriptor?native_carrier_declared(env,declaring,key,descriptor,&modifiers):NULL;
    if(field&&(modifiers&0x0008)&&(*native_ti)->GetClassStatus(native_ti,declaring,&status)==JVMTI_ERROR_NONE
            &&(status&JVMTI_CLASS_STATUS_INITIALIZED)&&!native_original.ExceptionCheck(env))result=native_carrier_one(env,NULL,declaring,field,descriptor,1);
    if(descriptor)native_original.ReleaseStringUTFChars(env,signature,descriptor);if(key)native_original.ReleaseStringUTFChars(env,name,key);state->control--;
    if(!result||native_original.ExceptionCheck(env)){if(result)native_original.DeleteLocalRef(env,result);if(!native_original.ExceptionCheck(env))native_refuse(env,"CARRIER_INITIALIZED_STATIC_FIELD_REQUIRED");return NULL;}return result;
}

/* A callback identity is read only while its exact Core invocation is still
 * physically on this thread. No method pointer or retained verdict escapes. */
typedef struct NativeCarrierFrame {
    jvmtiFrameInfo actual;jclass declaring;char *name,*descriptor;unsigned char *bytes;jint length,modifiers;
    CodeVMMethod version;CodePool pool;
} NativeCarrierFrame;
static void native_carrier_frame_clear(JNIEnv *env,NativeCarrierFrame *frame){
    if(frame->declaring)native_original.DeleteLocalRef(env,frame->declaring);
    if(frame->name)(*native_ti)->Deallocate(native_ti,(unsigned char*)frame->name);
    if(frame->descriptor)(*native_ti)->Deallocate(native_ti,(unsigned char*)frame->descriptor);
    if(frame->bytes)(*native_ti)->Deallocate(native_ti,frame->bytes);
    free(frame->version.handlers);code_pool_free(&frame->pool);memset(frame,0,sizeof(*frame));
}
static int native_carrier_selector(JNIEnv *env,jmethodID method,jclass declaring,const char *name,const char *descriptor){
    jclass actual=NULL;char *actualName=NULL,*actualDescriptor=NULL;
    int valid=(*native_ti)->GetMethodDeclaringClass(native_ti,method,&actual)==JVMTI_ERROR_NONE&&actual,matched=0;
    if(valid&&native_original.IsSameObject(env,actual,declaring)){
        valid=(*native_ti)->GetMethodName(native_ti,method,&actualName,&actualDescriptor,NULL)==JVMTI_ERROR_NONE&&actualName&&actualDescriptor;
        matched=valid&&!strcmp(name,actualName)&&!strcmp(descriptor,actualDescriptor);
    }
    if(actual)native_original.DeleteLocalRef(env,actual);
    if(actualName)(*native_ti)->Deallocate(native_ti,(unsigned char*)actualName);
    if(actualDescriptor)(*native_ti)->Deallocate(native_ti,(unsigned char*)actualDescriptor);
    return valid&&!native_original.ExceptionCheck(env)?matched:-1;
}
static int native_carrier_frame_open(JNIEnv *env,jvmtiFrameInfo actual,jobjectArray policy,NativeCarrierFrame *frame){
    frame->actual=actual;jboolean obsolete=JNI_TRUE;
    int valid=actual.location>=0&&(*native_ti)->IsMethodObsolete(native_ti,actual.method,&obsolete)==JVMTI_ERROR_NONE&&!obsolete
            &&(*native_ti)->GetMethodDeclaringClass(native_ti,actual.method,&frame->declaring)==JVMTI_ERROR_NONE&&frame->declaring
            &&(*native_ti)->GetMethodName(native_ti,actual.method,&frame->name,&frame->descriptor,NULL)==JVMTI_ERROR_NONE&&frame->name&&frame->descriptor
            &&(*native_ti)->GetMethodModifiers(native_ti,actual.method,&frame->modifiers)==JVMTI_ERROR_NONE&&!(frame->modifiers&0x0500)
            &&code_vm_snapshot(env,actual.method,&frame->version,&frame->pool,frame->name,frame->descriptor)
            &&(*native_ti)->GetBytecodes(native_ti,actual.method,&frame->length,&frame->bytes)==JVMTI_ERROR_NONE
            &&frame->bytes&&frame->length>0&&frame->version.codeLength==(unsigned)frame->length;
    unsigned count=0,*offsets=valid?code_offsets(frame->bytes,(unsigned)frame->length,&count):NULL;
    if(valid)valid=offsets&&code_index(offsets,count,actual.location)>=0&&actual.location<frame->length;free(offsets);
    jobject module=valid?native_original.GetModule(env,frame->declaring):NULL;
    if(valid)valid=module&&!code_source_query_module_stopped(env,module,policy);if(module)native_original.DeleteLocalRef(env,module);
    if(valid){
        OwnerLink *owners=NULL;int known=0,unknown=0;
        valid=code_available&&code_frame_sources_query(env,actual.method,actual.location,&owners,&known,&unknown)
                &&!unknown&&!code_source_query_owners_stopped(env,owners,policy);code_links_free(owners);
    }
    return valid&&!native_original.ExceptionCheck(env)&&code_vm_unchanged(actual.method,&frame->version);
}
static int native_carrier_frame_current(JNIEnv *env,NativeCarrierFrame *frame,jint depth){
    jmethodID method=NULL;jlocation location=-1;jboolean obsolete=JNI_TRUE;
    return !native_original.ExceptionCheck(env)&&code_vm_unchanged(frame->actual.method,&frame->version)
            &&(*native_ti)->GetFrameLocation(native_ti,NULL,depth,&method,&location)==JVMTI_ERROR_NONE
            &&method==frame->actual.method&&location==frame->actual.location
            &&(*native_ti)->IsMethodObsolete(native_ti,method,&obsolete)==JVMTI_ERROR_NONE&&!obsolete;
}
/* Bind a mirror found through the actual initiating loader to the CP's real
 * resolved Klass, using that mirror's own declared VM method metadata. */
static int native_carrier_holder(JNIEnv *env,jclass type,uintptr_t holder){
    jmethodID *methods=NULL;jint count=0;int matched=0;
    if((*native_ti)->GetClassMethods(native_ti,type,&count,&methods)!=JVMTI_ERROR_NONE||count<0)return 0;
    for(jint i=0;i<count&&!matched&&!native_original.ExceptionCheck(env);i++){
        jclass declaring=NULL;CodeVMMethod version={0};CodePool pool={0};
        if((*native_ti)->GetMethodDeclaringClass(native_ti,methods[i],&declaring)==JVMTI_ERROR_NONE&&declaring
                &&native_original.IsSameObject(env,declaring,type)&&code_vm_snapshot(env,methods[i],&version,&pool,NULL,NULL))
            matched=pool.holder==holder&&code_vm_unchanged(methods[i],&version);
        if(declaring)native_original.DeleteLocalRef(env,declaring);free(version.handlers);code_pool_free(&pool);
    }
    if(methods)(*native_ti)->Deallocate(native_ti,(unsigned char*)methods);return matched;
}
static jclass native_carrier_call_owner(JNIEnv *env,NativeCarrierFrame *caller,NativeCarrierFrame *callee,unsigned index){
    CodePool *pool=&caller->pool;unsigned char tag=0;uint32_t slot=0;uintptr_t holder=0;
    if(!code_vm_tag(pool,index,&tag)||tag!=7||!code_vm_slot(pool,index,&slot,sizeof(slot))
            ||(slot&65535)>=pool->resolvedCount||!pool->resolved
            ||!code_vm_read(pool->resolved+(uintptr_t)(slot&65535)*sizeof(uintptr_t),&holder,sizeof(holder))||!holder)return NULL;
    if(holder==pool->holder)return (jclass)native_original.NewLocalRef(env,caller->declaring);
    if(holder==callee->pool.holder)return (jclass)native_original.NewLocalRef(env,callee->declaring);
    if(!code_vm_entry(pool,index)||pool->entries[index][0]!=7)return NULL;
    unsigned nameIndex=code_be(pool->entries[index]+1,2);if(!code_vm_entry(pool,nameIndex))return NULL;
    char *name=code_utf(pool,nameIndex);if(!name)return NULL;
    size_t length=strlen(name);char *signature=(char*)malloc(length+3);jclass result=NULL;NativeCarrierTypes types={0};
    if(signature){signature[0]='L';memcpy(signature+1,name,length);signature[length+1]=';';signature[length+2]=0;
        if(native_carrier_types(env,caller->declaring,&types))for(jint i=0;i<types.count&&!result&&!native_original.ExceptionCheck(env);i++){
            char *actual=NULL;
            int equal=(*native_ti)->GetClassSignature(native_ti,types.types[i],&actual,NULL)==JVMTI_ERROR_NONE&&actual&&!strcmp(signature,actual);
            if(actual)(*native_ti)->Deallocate(native_ti,(unsigned char*)actual);
            if(equal&&native_carrier_holder(env,types.types[i],holder))result=(jclass)native_original.NewLocalRef(env,types.types[i]);
        }}
    free(signature);free(name);native_carrier_types_clear(env,&types);return result;
}
static int native_carrier_invocation(JNIEnv *env,NativeCarrierFrame *caller,NativeCarrierFrame *callee,jobject consumer,int boundary,int locals,jint calleeDepth){
    unsigned at=(unsigned)caller->actual.location,opcode=caller->bytes[at];
    if(opcode<182||opcode>185||at+3>(unsigned)caller->length||(boundary&&opcode!=185))return 0;
    unsigned index=code_be(caller->bytes+at+1,2);CodePool *pool=&caller->pool;
    if(!code_vm_entry(pool,index))return 0;const unsigned char *entry=pool->entries[index];
    if((entry[0]!=10&&entry[0]!=11)||(opcode==185&&entry[0]!=11)||(opcode==182&&entry[0]!=10))return 0;
    unsigned ownerIndex=code_be(entry+1,2),selector=code_be(entry+3,2);
    if(!code_vm_entry(pool,selector)||pool->entries[selector][0]!=12)return 0;
    unsigned nameIndex=code_be(pool->entries[selector]+1,2),descriptorIndex=code_be(pool->entries[selector]+3,2);
    if(!code_vm_entry(pool,nameIndex)||!code_vm_entry(pool,descriptorIndex))return 0;
    char *name=code_utf(pool,nameIndex),*descriptor=code_utf(pool,descriptorIndex);
    int valid=name&&descriptor&&!strcmp(name,callee->name)&&!strcmp(descriptor,callee->descriptor)
            &&((opcode==184)==((callee->modifiers&0x0008)!=0));
    jclass owner=valid?native_carrier_call_owner(env,caller,callee,ownerIndex):NULL;
    if(valid)valid=owner!=NULL;
    if(valid&&boundary){
        char *signature=NULL;
        valid=!strcmp(name,"accept")&&!strcmp(descriptor,"(Ljava/lang/Object;)V")
                &&(*native_ti)->GetClassSignature(native_ti,owner,&signature,NULL)==JVMTI_ERROR_NONE&&signature
                &&!strcmp(signature,"Lnet/minecraftforge/common/util/NonNullConsumer;")
                &&native_original.IsInstanceOf(env,consumer,owner);
        if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);
    }else if(valid)valid=native_original.IsAssignableFrom(env,callee->declaring,owner)||native_original.IsAssignableFrom(env,owner,callee->declaring);
    // The supported live-attach route has no new locals capability. Its exact
    // Core callsite binds the captured consumer operand. When this environment
    // already has local access, also verify the actual physical receiver.
    if(valid&&locals&&!(callee->modifiers&0x0008)){
        jobject receiver=NULL;valid=(*native_ti)->GetLocalInstance(native_ti,NULL,calleeDepth,&receiver)==JVMTI_ERROR_NONE&&receiver;
        if(valid)valid=native_original.IsInstanceOf(env,receiver,owner)&&(!boundary||native_original.IsSameObject(env,receiver,consumer));
        if(receiver)native_original.DeleteLocalRef(env,receiver);
    }
    if(owner)native_original.DeleteLocalRef(env,owner);free(name);free(descriptor);return valid&&!native_original.ExceptionCheck(env);
}
static int native_carrier_adapter(JNIEnv *env,NativeCarrierFrame *frame){
    if(frame->modifiers&0x0040)return 1;
    jobject loader=NULL;char *signature=NULL;int matched=0;
    if((*native_ti)->GetClassLoader(native_ti,frame->declaring,&loader)!=JVMTI_ERROR_NONE
            ||(*native_ti)->GetClassSignature(native_ti,frame->declaring,&signature,NULL)!=JVMTI_ERROR_NONE||!signature)goto done;
    AcquireSRWLockShared(&code_records);CodeCandidates scan=code_candidates(signature);
    for(CodeImage *image=code_candidate_next(&scan);image&&!matched;image=code_candidate_next(&scan))
        if(image->hidden&&code_class_matches(env,image,frame->declaring,loader,signature)){
            CodeMethod *method=code_method(image,frame->name,frame->descriptor);unsigned ordinal=0;
            matched=method&&method->access==(unsigned)frame->modifiers
                    &&code_equivalent(image,method,frame->bytes,(unsigned)frame->length,&frame->pool,&frame->version,frame->actual.location,&ordinal)
                    &&code_vm_unchanged(frame->actual.method,&frame->version);
        }
    ReleaseSRWLockShared(&code_records);
done:
    if(loader)native_original.DeleteLocalRef(env,loader);if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);return matched&&!native_original.ExceptionCheck(env);
}
JNIEXPORT jint JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_carrierCallbackDepth0(JNIEnv *env,jclass controller,jclass declaring,jstring name,jstring descriptor){
    if(!native_carrier_reader(env,controller)||!declaring||!name||!descriptor)return 0;
    NativeThread *state=native_thread();if(!state)return 0;state->control++;
    const char *key=native_original.GetStringUTFChars(env,name,NULL),*signature=key?native_original.GetStringUTFChars(env,descriptor,NULL):NULL;
    jint total=0,result=0;int valid=key&&signature&&!native_original.ExceptionCheck(env)&&(*native_ti)->GetFrameCount(native_ti,NULL,&total)==JVMTI_ERROR_NONE;
    for(jint first=0;valid&&first<total&&!result;){
        jvmtiFrameInfo frames[16];jint count=0;
        valid=(*native_ti)->GetStackTrace(native_ti,NULL,first,16,frames,&count)==JVMTI_ERROR_NONE&&count>0&&count<=total-first;
        for(jint i=0;valid&&i<count&&!result;i++){
            int selected=native_carrier_selector(env,frames[i].method,declaring,key,signature);
            if(selected<0)valid=0;else if(selected)result=total-first-i;
        }
        first+=count;
    }
    if(signature)native_original.ReleaseStringUTFChars(env,descriptor,signature);if(key)native_original.ReleaseStringUTFChars(env,name,key);state->control--;
    return valid&&!native_original.ExceptionCheck(env)?result:0;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_carrierCallback0(JNIEnv *env,jclass controller,jobject consumer,jclass callee,jstring name,jstring descriptor,jint calleeBottomDepth,
                                                                                     jclass boundary,jstring boundaryName,jstring boundaryDescriptor,jint boundaryBottomDepth){
    if(!native_carrier_reader(env,controller)||!consumer||!callee||!name||!descriptor||!boundary||!boundaryName||!boundaryDescriptor)return JNI_FALSE;
    if(boundaryBottomDepth<=0||calleeBottomDepth<=boundaryBottomDepth||calleeBottomDepth-boundaryBottomDepth>2)return JNI_FALSE;
    jint delta=calleeBottomDepth-boundaryBottomDepth;
    NativeThread *state=native_thread();if(!state)return JNI_FALSE;state->control++;
    NativeCarrierFrame selected[3]={0};jobjectArray policy=NULL;const char *keys[4]={0};jstring texts[4]={name,descriptor,boundaryName,boundaryDescriptor};
    jint total=0,count=0,first=0;int valid=1;jvmtiFrameInfo frames[16];jvmtiCapabilities capabilities;memset(&capabilities,0,sizeof(capabilities));
    for(unsigned i=0;i<4&&valid;i++){keys[i]=native_original.GetStringUTFChars(env,texts[i],NULL);valid=keys[i]&&!native_original.ExceptionCheck(env);}
    if(valid)valid=(*native_ti)->GetFrameCount(native_ti,NULL,&total)==JVMTI_ERROR_NONE&&calleeBottomDepth<=total;
    if(valid){first=total-calleeBottomDepth;valid=(*native_ti)->GetStackTrace(native_ti,NULL,first,16,frames,&count)==JVMTI_ERROR_NONE&&count>delta;}
    if(valid)valid=native_carrier_selector(env,frames[0].method,callee,keys[0],keys[1])==1
            &&native_carrier_selector(env,frames[delta].method,boundary,keys[2],keys[3])==1;
    jfieldID stopped=valid?native_original.GetStaticFieldID(env,native_controls,"stoppedModules","[Ljava/lang/Module;"):NULL;
    if(stopped&&!native_original.ExceptionCheck(env))policy=(jobjectArray)native_original.GetStaticObjectField(env,native_controls,stopped);valid=valid&&policy&&!native_original.ExceptionCheck(env);
    for(jint i=0;i<=delta&&valid;i++)valid=native_carrier_frame_open(env,frames[i],policy,&selected[i]);
    NativeCarrierFrame *accept=&selected[delta-1];
    if(valid)valid=!strcmp(accept->name,"accept")&&!strcmp(accept->descriptor,"(Ljava/lang/Object;)V")
            &&(accept->modifiers&0x0009)==0x0001&&native_original.IsInstanceOf(env,consumer,accept->declaring);
    if(valid)valid=(*native_ti)->GetCapabilities(native_ti,&capabilities)==JVMTI_ERROR_NONE;
    if(valid)valid=native_carrier_invocation(env,&selected[delta],accept,consumer,1,capabilities.can_access_local_variables,first+delta-1);
    if(valid&&delta==2)valid=native_carrier_adapter(env,accept)
            &&native_carrier_invocation(env,accept,&selected[0],NULL,0,capabilities.can_access_local_variables,first);
    for(jint i=0;i<=delta&&valid;i++)valid=native_carrier_frame_current(env,&selected[i],first+i);
    for(unsigned i=0;i<3;i++)native_carrier_frame_clear(env,&selected[i]);if(policy)native_original.DeleteLocalRef(env,policy);
    for(unsigned i=0;i<4;i++)if(keys[i])native_original.ReleaseStringUTFChars(env,texts[i],keys[i]);state->control--;
    return valid&&!native_original.ExceptionCheck(env)?JNI_TRUE:JNI_FALSE;
}
