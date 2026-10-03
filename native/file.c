/* File JNI calls observed at their actual VM binding. Existing bindings are not
 * guessed from DLL export names or replaced with assumed original addresses. */
enum { NATIVE_FILE_TYPES=13, NATIVE_FILE_DISPATCHER=3, NATIVE_FILE_DESCRIPTOR=4, NATIVE_FILE_CHANNEL=5,
       NATIVE_SOCKET_DISPATCHER=6, NATIVE_DATAGRAM_DISPATCHER=7, NATIVE_SOCKET_NET=8, NATIVE_DATAGRAM_CHANNEL=9,
       NATIVE_ASYNC_SOCKET=10, NATIVE_ASYNC_SERVER=11, NATIVE_ASYNC_FILE=12 };
static jclass native_file_types[NATIVE_FILE_TYPES],native_file_bridge;
static jmethodID native_file_begin_query,native_file_end_query,native_io_begin_query,native_io_end_query;
static unsigned native_file_descriptor_argument(const char *signature){
    if(strncmp(signature,"(Ljava/io/FileDescriptor;",sizeof("(Ljava/io/FileDescriptor;")-1)==0)return 2;
    if(strncmp(signature,"(ZLjava/io/FileDescriptor;",sizeof("(ZLjava/io/FileDescriptor;")-1)==0)return 3;
    return 0;
}
static int native_file_accepts(unsigned type,jint modifiers,const char *signature){
    if(!(modifiers&0x0100))return 0;
    if(type==NATIVE_FILE_DISPATCHER)return (modifiers&0x0008)&&strncmp(signature,"(Ljava/io/FileDescriptor;",sizeof("(Ljava/io/FileDescriptor;")-1)==0;
    if(type==NATIVE_FILE_CHANNEL)return !(modifiers&0x0008)||strcmp(signature,"(JJ)I")==0;
    if(type==NATIVE_SOCKET_DISPATCHER&&(modifiers&0x0008)&&strcmp(signature,"(I)V")==0)return 1;
    if(type>=NATIVE_ASYNC_SOCKET)return (modifiers&0x0008)&&strncmp(signature,"(J",2)==0;
    if(type>=NATIVE_SOCKET_DISPATCHER)return native_file_descriptor_argument(signature)!=0;
    return !(modifiers&0x0008);
}

static int native_file_seed_one(JNIEnv *env,NativeThread *state,unsigned type,jmethodID method){
    jint modifiers=0;char *name=NULL,*signature=NULL;int remaining=1;
    if((*native_ti)->GetMethodModifiers(native_ti,method,&modifiers)!=JVMTI_ERROR_NONE)return 1;
    if(!(modifiers&0x0100))return 0;
    if((*native_ti)->GetMethodName(native_ti,method,&name,&signature,NULL)!=JVMTI_ERROR_NONE)goto done;
    if(!native_file_accepts(type,modifiers,signature)){remaining=0;goto done;}
    remaining=native_binding_seed_one(env,state,native_file_types[type],method,name,signature,1);
done:
    if(name)(*native_ti)->Deallocate(native_ti,(unsigned char*)name);if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);return remaining;
}
JNIEXPORT jint JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_fileBindings0(JNIEnv *env,jclass controller){
    (void)controller;
    if(!native_jni_ready||!native_control_gate||!native_original.CallStaticBooleanMethod(env,native_controller,native_control_gate)){
        native_refuse(env,"NATIVE_FILE_INSTALL_AGENT_REQUIRED");return -1;
    }
    NativeThread *state=native_thread();if(!state)return -1;state->control++;jint remaining=0;
    for(unsigned i=0;i<NATIVE_FILE_TYPES&&!native_original.ExceptionCheck(env);i++){
        jmethodID *methods=NULL;jint count=0;
        if(!native_file_types[i]||(*native_ti)->GetClassMethods(native_ti,native_file_types[i],&count,&methods)!=JVMTI_ERROR_NONE){remaining++;continue;}
        for(jint j=0;j<count&&!native_original.ExceptionCheck(env);j++)remaining+=native_file_seed_one(env,state,i,methods[j]);
        if(methods)(*native_ti)->Deallocate(native_ti,(unsigned char*)methods);
    }
    state->control--;return remaining;
}

static void native_file_classify(JNIEnv *env,jclass type,Binding *binding,const char *name){
    unsigned matched=NATIVE_FILE_TYPES;for(unsigned i=0;i<NATIVE_FILE_TYPES;i++)if(native_file_types[i]&&native_original.IsSameObject(env,type,native_file_types[i])){matched=i;break;}
    if(matched==NATIVE_FILE_TYPES)return;
    jint modifiers=0;if((*native_ti)->GetMethodModifiers(native_ti,binding->method,&modifiers)!=JVMTI_ERROR_NONE||!native_file_accepts(matched,modifiers,binding->signature))return;
    binding->fileMethod=_strdup(name);
    if(!binding->fileMethod){native_refuse(env,"NATIVE_FILE_METHOD_CAPTURE_FAILED");return;}
    binding->fileArgument=matched>=NATIVE_SOCKET_DISPATCHER?native_file_descriptor_argument(binding->signature):matched==NATIVE_FILE_DISPATCHER?2:1;
    if(!binding->fileArgument)binding->fileArgument=1;
    binding->fileOperation=matched==NATIVE_FILE_DISPATCHER?(strcmp(name,"close0")==0?4:3)
            :matched==NATIVE_SOCKET_DISPATCHER&&strcmp(name,"close0")==0?7
            :matched>=NATIVE_ASYNC_SOCKET?8
            :matched>=NATIVE_SOCKET_DISPATCHER?3
            :matched==NATIVE_FILE_DESCRIPTOR?(strcmp(name,"close0")==0?4:strcmp(name,"sync")==0?5:3)
            :matched==NATIVE_FILE_CHANNEL&&strcmp(name,"unmap0")==0?6
            :strcmp(name,"open0")==0?2:1;
}
static int native_file_prepare(JNIEnv *env){
    NativeThread *state=native_thread();if(!state)return 0;state->control++;
    static const char *names[]={"java/io/FileInputStream","java/io/FileOutputStream","java/io/RandomAccessFile","sun/nio/ch/FileDispatcherImpl","java/io/FileDescriptor","sun/nio/ch/FileChannelImpl",
        "sun/nio/ch/SocketDispatcher","sun/nio/ch/DatagramDispatcher","sun/nio/ch/Net","sun/nio/ch/DatagramChannelImpl",
        "sun/nio/ch/WindowsAsynchronousSocketChannelImpl","sun/nio/ch/WindowsAsynchronousServerSocketChannelImpl","sun/nio/ch/WindowsAsynchronousFileChannelImpl"};
    int ready=1;
    for(unsigned i=0;i<NATIVE_FILE_TYPES&&!native_original.ExceptionCheck(env);i++){
        jclass type=native_original.FindClass(env,names[i]);char descriptor[96];snprintf(descriptor,sizeof(descriptor),"L%s;",names[i]);
        if(type&&native_bootstrap_class(env,type,descriptor))native_file_types[i]=(jclass)native_original.NewGlobalRef(env,type);
        if(type)native_original.DeleteLocalRef(env,type);if(!native_file_types[i])ready=0;
    }
    jclass bridge=!native_original.ExceptionCheck(env)?native_original.FindClass(env,"dev/ronova/pro/bootstrap/ResourceBridge"):NULL;
    if(bridge&&native_bootstrap_class(env,bridge,"Ldev/ronova/pro/bootstrap/ResourceBridge;")){
        native_file_bridge=(jclass)native_original.NewGlobalRef(env,bridge);
        native_file_begin_query=native_original.GetStaticMethodID(env,bridge,"nativeFileOperation","(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Module;IJJ)Ljava/lang/Object;");
        native_file_end_query=native_original.GetStaticMethodID(env,bridge,"nativeFileOperationFinished","(Ljava/lang/Object;)V");
        native_io_begin_query=native_original.GetStaticMethodID(env,bridge,"nativeIoOperation","(Ljava/lang/Class;Ljava/lang/String;[J[Ljava/lang/Object;[Ljava/lang/Module;)Ljava/lang/Object;");
        native_io_end_query=native_original.GetStaticMethodID(env,bridge,"nativeIoFinished","(Ljava/lang/Object;Z)V");
    }
    if(bridge)native_original.DeleteLocalRef(env,bridge);
    ready=ready&&native_file_bridge&&native_file_begin_query&&native_file_end_query&&native_io_begin_query&&native_io_end_query&&!native_original.ExceptionCheck(env);
    state->control--;return ready;
}
static void native_file_clear(JNIEnv *env,NativeFileFrame *use){
    if(use->receiver)native_original.DeleteGlobalRef(env,use->receiver);
    if(use->token)native_original.DeleteGlobalRef(env,use->token);
    if(use->declaring)native_original.DeleteGlobalRef(env,use->declaring);
    if(use->method)native_original.DeleteGlobalRef(env,use->method);
    if(use->sources)native_original.DeleteGlobalRef(env,use->sources);
    if(use->references)native_original.DeleteGlobalRef(env,use->references);
    if(use->arguments)native_original.DeleteGlobalRef(env,use->arguments);
    memset(use,0,sizeof(*use));
}
static jobject native_file_keep(JNIEnv *env,jobject local){
    if(!local)return NULL;
    jobject kept=native_original.ExceptionCheck(env)?NULL:native_original.NewGlobalRef(env,local);native_original.DeleteLocalRef(env,local);return kept;
}
static int native_io_arguments(JNIEnv *env,Binding *binding,unsigned char *frame,NativeFileFrame *use){
    unsigned count=native_arguments(binding->signature)-2;
    jlong *numbers=(jlong*)calloc(count?count:1,sizeof(*numbers));if(!numbers)return 0;
    jclass object=native_original.FindClass(env,"java/lang/Object");
    jobjectArray references=object?native_original.NewObjectArray(env,(jsize)count,object,NULL):NULL;
    if(object)native_original.DeleteLocalRef(env,object);
    const char *descriptor=binding->signature+1;
    for(unsigned i=0;references&&i<count&&!native_original.ExceptionCheck(env);i++){
        uint64_t bits=native_unsafe_argument(binding,frame,i+2);char kind=*descriptor;
        if(kind=='L'||kind=='['){
            native_original.SetObjectArrayElement(env,references,(jsize)i,(jobject)(uintptr_t)bits);
            while(*descriptor=='[')descriptor++;
            if(*descriptor=='L'){while(*descriptor&&*descriptor!=';')descriptor++;}if(*descriptor)descriptor++;
        }else{
            numbers[i]=kind=='J'?(jlong)bits:kind=='Z'?(jlong)(bits!=0):kind=='B'?(jlong)(jbyte)bits:kind=='S'?(jlong)(jshort)bits:kind=='C'?(jlong)(jchar)bits:(jlong)(jint)bits;
            descriptor++;
        }
    }
    jlongArray arguments=!native_original.ExceptionCheck(env)?native_original.NewLongArray(env,(jsize)count):NULL;
    if(arguments)native_original.SetLongArrayRegion(env,arguments,0,(jsize)count,numbers);free(numbers);
    use->references=(jobjectArray)native_file_keep(env,references);use->arguments=(jlongArray)native_file_keep(env,arguments);
    return use->references&&use->arguments&&!native_original.ExceptionCheck(env);
}
static void native_file_refuse(JNIEnv *env,Binding *binding){
    if(native_original.ExceptionCheck(env))return;
    const char *name=binding->fileOperation==2?"java/io/FileNotFoundException":binding->fileOperation==5?"java/io/SyncFailedException":"java/io/IOException";
    jclass type=native_original.FindClass(env,name);
    if(type){native_original.ThrowNew(env,type,"RONOVA_NATIVE_FILE_OPERATION_REFUSED");native_original.DeleteLocalRef(env,type);}
}
static int native_file_begin(JNIEnv *env,NativeThread *state,Binding *binding,jobject receiver,unsigned char *frame){
    NativeFileFrame *use=&state->fileFrames[state->depth-1];
    if(binding->fileOperation!=4&&binding->fileOperation!=7&&binding->fileOperation!=8&&native_stopped(env)){native_file_refuse(env,binding);return 0;}
    if(binding->fileArgument>=2)receiver=(jobject)(uintptr_t)native_unsafe_argument(binding,frame,binding->fileArgument);
    state->control++;
    use->receiver=native_original.NewGlobalRef(env,receiver);use->operation=(jint)binding->fileOperation;
    if(binding->fileOperation==6){use->address=(jlong)native_unsafe_argument(binding,frame,2);use->length=(jlong)native_unsafe_argument(binding,frame,3);}
    if(binding->fileOperation==7)use->address=(jlong)(jint)native_unsafe_argument(binding,frame,2);
    use->declaring=(jclass)native_original.NewGlobalRef(env,binding->type);
    if(!native_original.ExceptionCheck(env))use->method=(jstring)native_file_keep(env,native_original.NewStringUTF(env,binding->fileMethod));
    if(!native_original.ExceptionCheck(env))use->sources=(jobjectArray)native_file_keep(env,native_mutation_sources(env,state));
    int ready=use->receiver&&use->declaring&&use->method&&use->sources&&!native_original.ExceptionCheck(env);
    if(ready&&use->operation==8)ready=native_io_arguments(env,binding,frame,use);
    if(ready){
        NativeFileFrame *previous=state->filePermit;state->filePermit=use;
        jobject token=use->operation==8?native_original.CallStaticObjectMethod(env,native_file_bridge,native_io_begin_query,use->declaring,use->method,use->arguments,use->references,use->sources)
                :native_original.CallStaticObjectMethod(env,native_file_bridge,native_file_begin_query,receiver,use->declaring,use->method,use->sources,use->operation,use->address,use->length);
        use->token=native_file_keep(env,token);
        state->filePermit=previous;ready=!native_original.ExceptionCheck(env);
    }
    if(!ready)native_file_clear(env,use);
    state->control--;return ready;
}
static void native_file_finish(JNIEnv *env,NativeThread *state,NativeFileFrame *use,int completed){
    if(!use->declaring)return; // Controller-internal I/O bypasses observation before acquiring a scope.
    state->control++;
    if(use->token){
        jthrowable failure=native_original.ExceptionOccurred(env);if(failure)native_original.ExceptionClear(env);
        NativeFileFrame *previous=state->filePermit;state->filePermit=use;use->finishing=1;
        if(use->operation==8)native_original.CallStaticVoidMethod(env,native_file_bridge,native_io_end_query,use->token,completed&&!failure?JNI_TRUE:JNI_FALSE);
        else native_original.CallStaticVoidMethod(env,native_file_bridge,native_file_end_query,use->token);
        state->filePermit=previous;
        if(failure){
            if(native_original.ExceptionCheck(env)){native_original.ExceptionClear(env);InterlockedIncrement(&native_failures);}
            native_original.Throw(env,failure);native_original.DeleteLocalRef(env,failure);
        }
    }
    native_file_clear(env,use);state->control--;
}
static void native_file_end(JNIEnv *env,NativeThread *state){native_file_finish(env,state,&state->fileFrames[state->depth-1],1);}
static void native_file_thread_end(JNIEnv *env,NativeThread *state){
    for(unsigned i=state->depth;i>0;i--)if(state->fileFrames[i-1].declaring)native_file_finish(env,state,&state->fileFrames[i-1],0);
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_ioOperationBoundary0(JNIEnv *env,jclass type,jclass declaring,jstring method,jlongArray arguments,jobjectArray references,jobjectArray sources){
    (void)type;NativeThread *state=native_thread();NativeFileFrame *use=state?state->filePermit:NULL;
    return use&&!use->finishing&&use->operation==8&&native_original.IsSameObject(env,use->declaring,declaring)&&native_original.IsSameObject(env,use->method,method)
            &&native_original.IsSameObject(env,use->arguments,arguments)&&native_original.IsSameObject(env,use->references,references)&&native_original.IsSameObject(env,use->sources,sources)?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_fileOperationBoundary0(JNIEnv *env,jclass type,jobject receiver,jclass declaring,jstring method,jobjectArray sources,jint operation,jlong address,jlong length){
    (void)type;NativeThread *state=native_thread();NativeFileFrame *use=state?state->filePermit:NULL;
    return use&&!use->finishing&&use->operation==operation&&use->address==address&&use->length==length&&native_original.IsSameObject(env,use->receiver,receiver)
            &&native_original.IsSameObject(env,use->declaring,declaring)&&native_original.IsSameObject(env,use->method,method)
            &&native_original.IsSameObject(env,use->sources,sources)?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_fileOperationFinishing0(JNIEnv *env,jclass type,jobject token){
    (void)type;NativeThread *state=native_thread();NativeFileFrame *use=state?state->filePermit:NULL;
    return use&&use->finishing&&token&&native_original.IsSameObject(env,use->token,token)?JNI_TRUE:JNI_FALSE;
}
