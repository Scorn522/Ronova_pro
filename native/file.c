/* File JNI calls observed at their actual VM binding. Existing bindings are not
 * guessed from DLL export names or replaced with assumed original addresses. */
enum { NATIVE_FILE_TYPES=13, NATIVE_FILE_DISPATCHER=3, NATIVE_FILE_DESCRIPTOR=4, NATIVE_FILE_CHANNEL=5,
       NATIVE_SOCKET_DISPATCHER=6, NATIVE_DATAGRAM_DISPATCHER=7, NATIVE_SOCKET_NET=8, NATIVE_DATAGRAM_CHANNEL=9,
       NATIVE_ASYNC_SOCKET=10, NATIVE_ASYNC_SERVER=11, NATIVE_ASYNC_FILE=12 };
enum { NATIVE_FILE_CREATE_SOCKET=9,NATIVE_FILE_MAPPING_DUPLICATE=10 };
static jclass native_file_types[NATIVE_FILE_TYPES],native_file_bridge,native_file_duplicate_type;
static jmethodID native_file_begin_query,native_file_end_query,native_io_begin_query,native_io_end_query,native_file_duplicate_end_query;
static jfieldID native_file_duplicate_returned,native_file_duplicate_succeeded;
/* An armed declaration owns no original function address. The only body it
 * can acquire is the actual NativeMethodBind event's target, before HotSpot
 * publishes that target or enters the first invocation. Keep old immutable
 * versions so a later redefinition cannot lend its selector to an old one. */
typedef struct NativeFileDeclaration {
    jmethodID method;uintptr_t version[3];unsigned type,operation,argument;
    char *name,*signature;struct NativeFileDeclaration *next;
} NativeFileDeclaration;
static SRWLOCK native_file_declarations_lock=SRWLOCK_INIT;
static NativeFileDeclaration *native_file_declarations;
enum { NATIVE_FILE_BOUND=0,NATIVE_FILE_ARMED_VM_ENTRY=1,NATIVE_FILE_PENDING=2,NATIVE_FILE_TOTAL=3 };
static unsigned native_file_descriptor_argument(const char *signature){
    if(strncmp(signature,"(Ljava/io/FileDescriptor;",sizeof("(Ljava/io/FileDescriptor;")-1)==0)return 2;
    if(strncmp(signature,"(ZLjava/io/FileDescriptor;",sizeof("(ZLjava/io/FileDescriptor;")-1)==0)return 3;
    return 0;
}
static int native_file_socket_creation(unsigned type,jint modifiers,const char *name,const char *signature){
    return type==NATIVE_SOCKET_NET&&(modifiers&0x0008)&&strcmp(name,"socket0")==0&&strcmp(signature,"(ZZZZ)I")==0;
}
static int native_file_mapping_duplicate(unsigned type,jint modifiers,const char *name,const char *signature){
    return type==NATIVE_FILE_DISPATCHER&&(modifiers&0x0008)&&strcmp(name,"duplicateHandle")==0&&strcmp(signature,"(J)J")==0;
}
static int native_file_accepts(unsigned type,jint modifiers,const char *name,const char *signature){
    if(!(modifiers&0x0100))return 0;
    // This Windows JDK entry allocates the descriptor number before any
    // FileDescriptor exists. It must still pass the actual native source gate.
    if(native_file_socket_creation(type,modifiers,name,signature))return 1;
    if(native_file_mapping_duplicate(type,modifiers,name,signature))return 1;
    if(type==NATIVE_FILE_DISPATCHER)return (modifiers&0x0008)&&strncmp(signature,"(Ljava/io/FileDescriptor;",sizeof("(Ljava/io/FileDescriptor;")-1)==0;
    if(type==NATIVE_FILE_CHANNEL)return !(modifiers&0x0008)||strcmp(signature,"(JJ)I")==0;
    if(type==NATIVE_SOCKET_DISPATCHER&&(modifiers&0x0008)&&strcmp(signature,"(I)V")==0)return 1;
    if(type>=NATIVE_ASYNC_SOCKET)return (modifiers&0x0008)&&strncmp(signature,"(J",2)==0;
    if(type>=NATIVE_SOCKET_DISPATCHER)return native_file_descriptor_argument(signature)!=0;
    return !(modifiers&0x0008);
}
static unsigned native_file_operation(unsigned type,jint modifiers,const char *name,const char *signature){
    return native_file_socket_creation(type,modifiers,name,signature)?NATIVE_FILE_CREATE_SOCKET
            :native_file_mapping_duplicate(type,modifiers,name,signature)?NATIVE_FILE_MAPPING_DUPLICATE
            :type==NATIVE_FILE_DISPATCHER?(strcmp(name,"close0")==0?4:3)
            :type==NATIVE_SOCKET_DISPATCHER&&strcmp(name,"close0")==0?7
            :type>=NATIVE_ASYNC_SOCKET?8
            :type==NATIVE_SOCKET_NET&&strcmp(name,"accept")==0
                    &&strcmp(signature,"(Ljava/io/FileDescriptor;Ljava/io/FileDescriptor;[Ljava/net/InetSocketAddress;)I")==0?8
            :type>=NATIVE_SOCKET_DISPATCHER?3
            :type==NATIVE_FILE_DESCRIPTOR?(strcmp(name,"close0")==0?4:strcmp(name,"sync")==0?5:3)
            :type==NATIVE_FILE_CHANNEL&&strcmp(name,"unmap0")==0?6
            :type==NATIVE_FILE_CHANNEL&&strcmp(name,"transferTo0")==0
                    &&strcmp(signature,"(Ljava/io/FileDescriptor;JJLjava/io/FileDescriptor;)J")==0?8
            :strcmp(name,"open0")==0?2:1;
}
static unsigned native_file_argument(unsigned type,jint modifiers,const char *name,const char *signature){
    unsigned argument=native_file_mapping_duplicate(type,modifiers,name,signature)?1
            :type>=NATIVE_SOCKET_DISPATCHER?native_file_descriptor_argument(signature):type==NATIVE_FILE_DISPATCHER?2:1;
    return argument?argument:1;
}
static int native_file_snapshot(JNIEnv *env,unsigned type,jmethodID method,void **target,uintptr_t version[3]){
    jclass declaring=NULL;jboolean obsolete=JNI_TRUE;
    int ready=type<NATIVE_FILE_TYPES&&native_file_types[type]
            &&(*native_ti)->GetMethodDeclaringClass(native_ti,method,&declaring)==JVMTI_ERROR_NONE
            &&native_original.IsSameObject(env,declaring,native_file_types[type])
            &&(*native_ti)->IsMethodObsolete(native_ti,method,&obsolete)==JVMTI_ERROR_NONE&&!obsolete
            &&code_vm_native_binding(env,method,target,version);
    if(declaring)native_original.DeleteLocalRef(env,declaring);return ready&&!native_original.ExceptionCheck(env);
}
static NativeFileDeclaration *native_file_arm(unsigned type,jmethodID method,jint modifiers,const char *name,const char *signature,const uintptr_t version[3]){
    NativeFileDeclaration *found=NULL;
    AcquireSRWLockShared(&native_file_declarations_lock);
    for(NativeFileDeclaration *entry=native_file_declarations;entry;entry=entry->next)
        if(entry->method==method&&entry->type==type&&!memcmp(entry->version,version,sizeof(entry->version))
                &&!strcmp(entry->name,name)&&!strcmp(entry->signature,signature)){found=entry;break;}
    ReleaseSRWLockShared(&native_file_declarations_lock);if(found)return found;
    NativeFileDeclaration *entry=(NativeFileDeclaration*)calloc(1,sizeof(*entry));if(!entry)return NULL;
    entry->method=method;entry->type=type;memcpy(entry->version,version,sizeof(entry->version));
    entry->operation=native_file_operation(type,modifiers,name,signature);entry->argument=native_file_argument(type,modifiers,name,signature);
    entry->name=_strdup(name);entry->signature=_strdup(signature);
    if(!entry->name||!entry->signature){free(entry->name);free(entry->signature);free(entry);return NULL;}
    AcquireSRWLockExclusive(&native_file_declarations_lock);
    for(NativeFileDeclaration *actual=native_file_declarations;actual;actual=actual->next)
        if(actual->method==method&&actual->type==type&&!memcmp(actual->version,version,sizeof(actual->version))
                &&!strcmp(actual->name,name)&&!strcmp(actual->signature,signature)){found=actual;break;}
    if(!found){entry->next=native_file_declarations;native_file_declarations=entry;found=entry;entry=NULL;}
    ReleaseSRWLockExclusive(&native_file_declarations_lock);
    if(entry){free(entry->name);free(entry->signature);free(entry);}return found;
}
static int native_file_bind_events_ready(void){
    /* native_events is published only after SetEventCallbacks and enabling
     * NativeMethodBind on our protected JVMTI environment both succeeded. */
    return InterlockedCompareExchange(&native_events,0,0)&&native_ti&&native_definitions
            &&InterlockedCompareExchange(&native_jni_ready,0,0)&&InterlockedCompareExchange(&native_boundary_ready,0,0)
            &&InterlockedCompareExchange(&native_tooling_ready,0,0)&&!InterlockedCompareExchange(&native_tooling_shutdown,0,0);
}
static void native_file_seed_one(JNIEnv *env,NativeThread *state,unsigned type,jmethodID method,int install,jlong values[4]){
    jint modifiers=0;char *name=NULL,*signature=NULL;void *current=NULL;uintptr_t version[3]={0};unsigned status=NATIVE_FILE_PENDING;int accepted=1;
    if((*native_ti)->GetMethodModifiers(native_ti,method,&modifiers)!=JVMTI_ERROR_NONE)goto done;
    if(!(modifiers&0x0100)){accepted=0;goto done;}
    if((*native_ti)->GetMethodName(native_ti,method,&name,&signature,NULL)!=JVMTI_ERROR_NONE)goto done;
    if(!native_file_accepts(type,modifiers,name,signature)){accepted=0;goto done;}
    if(!native_file_snapshot(env,type,method,&current,version)||!native_file_arm(type,method,modifiers,name,signature,version))goto done;
    if(!install)goto done;
    if(native_binding_current(method,current,1)){status=NATIVE_FILE_BOUND;goto done;}
    if(native_binding_resolved(method,current)){
        if(!native_binding_seed_one(env,state,native_file_types[type],method,name,signature,1))status=NATIVE_FILE_BOUND;
        goto done;
    }
    /* No sentinel address is guessed, registered or called. This state only
     * promises an exact armed declaration plus the enabled pre-publication
     * callback. Its current VM body is deliberately still unconfirmed. */
    if(current&&native_image(current)==code_vm_layout.image&&native_file_bind_events_ready())status=NATIVE_FILE_ARMED_VM_ENTRY;
done:
    if(accepted){values[status]++;values[NATIVE_FILE_TOTAL]++;}
    if(name)(*native_ti)->Deallocate(native_ti,(unsigned char*)name);if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);
}
static void native_file_bindings(JNIEnv *env,NativeThread *state,int install,jlong values[4]){
    for(unsigned i=0;i<NATIVE_FILE_TYPES&&!native_original.ExceptionCheck(env);i++){
        jmethodID *methods=NULL;jint count=0;
        if(!native_file_types[i]||(*native_ti)->GetClassMethods(native_ti,native_file_types[i],&count,&methods)!=JVMTI_ERROR_NONE){
            values[NATIVE_FILE_PENDING]++;values[NATIVE_FILE_TOTAL]++;continue;
        }
        for(jint j=0;j<count&&!native_original.ExceptionCheck(env);j++)native_file_seed_one(env,state,i,methods[j],install,values);
        if(methods)(*native_ti)->Deallocate(native_ti,(unsigned char*)methods);
    }
}
static int native_file_install_allowed(JNIEnv *env,jclass controller){
    if(!native_jni_ready||!native_controller||!native_original.IsSameObject(env,controller,native_controller)
            ||!native_control_gate||!native_original.CallStaticBooleanMethod(env,native_controller,native_control_gate)){
        native_refuse(env,"NATIVE_FILE_INSTALL_AGENT_REQUIRED");return 0;
    }
    return !native_original.ExceptionCheck(env);
}
JNIEXPORT jint JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_fileBindings0(JNIEnv *env,jclass controller){
    if(!native_file_install_allowed(env,controller))return -1;
    NativeThread *state=native_thread();if(!state)return -1;state->control++;jlong values[4]={0};native_file_bindings(env,state,1,values);state->control--;
    /* Preserve the old entry's meaning: an armed VM entry is not a published
     * original-function wrapper and therefore still counts as unresolved. */
    return (jint)(values[NATIVE_FILE_ARMED_VM_ENTRY]+values[NATIVE_FILE_PENDING]);
}
JNIEXPORT jlongArray JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_fileBindingState0(JNIEnv *env,jclass controller){
    if(!native_file_install_allowed(env,controller))return NULL;
    NativeThread *state=native_thread();if(!state)return NULL;state->control++;jlong values[4]={0};native_file_bindings(env,state,1,values);
    jlongArray result=!native_original.ExceptionCheck(env)?native_original.NewLongArray(env,4):NULL;
    if(result)native_original.SetLongArrayRegion(env,result,0,4,values);state->control--;return result;
}

static int native_file_classify(JNIEnv *env,jclass type,Binding *binding,const char *name){
    unsigned matched=NATIVE_FILE_TYPES;for(unsigned i=0;i<NATIVE_FILE_TYPES;i++)if(native_file_types[i]&&native_original.IsSameObject(env,type,native_file_types[i])){matched=i;break;}
    if(matched==NATIVE_FILE_TYPES)return 1;
    if(!name||!binding->signature){native_refuse(env,"NATIVE_FILE_METHOD_CAPTURE_FAILED");return 0;}
    jint modifiers=0;void *current=NULL;uintptr_t version[3]={0};
    if((*native_ti)->GetMethodModifiers(native_ti,binding->method,&modifiers)!=JVMTI_ERROR_NONE){native_refuse(env,"NATIVE_FILE_DECLARATION_UNAVAILABLE");return 0;}
    if(!native_file_accepts(matched,modifiers,name,binding->signature))return 1;
    NativeFileDeclaration *entry=native_file_snapshot(env,matched,binding->method,&current,version)
            ?native_file_arm(matched,binding->method,modifiers,name,binding->signature,version):NULL;
    if(!entry){native_refuse(env,"NATIVE_FILE_DECLARATION_UNAVAILABLE");return 0;}
    binding->fileMethod=_strdup(entry->name);
    if(!binding->fileMethod){native_refuse(env,"NATIVE_FILE_METHOD_CAPTURE_FAILED");return 0;}
    binding->fileArgument=entry->argument;binding->fileOperation=entry->operation;
    return !native_original.ExceptionCheck(env);
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
        native_file_duplicate_end_query=native_original.GetStaticMethodID(env,bridge,"nativeMappingDuplicateFinished","(Ljava/lang/Object;JZ)V");
    }
    if(bridge)native_original.DeleteLocalRef(env,bridge);
    jclass duplicate=!native_original.ExceptionCheck(env)?native_original.FindClass(env,"dev/ronova/pro/bootstrap/ResourceBridge$MappingDuplicateNative"):NULL;
    if(duplicate&&native_bootstrap_class(env,duplicate,"Ldev/ronova/pro/bootstrap/ResourceBridge$MappingDuplicateNative;")){
        native_file_duplicate_type=(jclass)native_original.NewGlobalRef(env,duplicate);
        native_file_duplicate_returned=native_original.GetFieldID(env,duplicate,"returned","J");
        native_file_duplicate_succeeded=native_original.GetFieldID(env,duplicate,"nativeSucceeded","Z");
    }
    if(duplicate)native_original.DeleteLocalRef(env,duplicate);
    ready=ready&&native_file_bridge&&native_file_begin_query&&native_file_end_query&&native_io_begin_query&&native_io_end_query&&native_file_duplicate_end_query
            &&native_file_duplicate_type&&native_file_duplicate_returned&&native_file_duplicate_succeeded&&!native_original.ExceptionCheck(env);
    /* Register the actual declarations without resolving or invoking their
     * I/O. A later bind refreshes a changed version from its own event. */
    if(ready){jlong declarations[4]={0};native_file_bindings(env,state,0,declarations);ready=!native_original.ExceptionCheck(env);}
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
    const char *name=binding->fileOperation==2?"java/io/FileNotFoundException":binding->fileOperation==5?"java/io/SyncFailedException"
            :binding->fileOperation==NATIVE_FILE_CREATE_SOCKET?"java/net/SocketException":"java/io/IOException";
    jclass type=native_original.FindClass(env,name);
    if(type){native_original.ThrowNew(env,type,"RONOVA_NATIVE_FILE_OPERATION_REFUSED");native_original.DeleteLocalRef(env,type);}
}
static int native_file_begin(JNIEnv *env,NativeThread *state,Binding *binding,jobject receiver,unsigned char *frame){
    NativeFileFrame *use=&state->fileFrames[state->depth-1];
    if(binding->fileOperation!=4&&binding->fileOperation!=7&&binding->fileOperation!=8&&native_stopped(env)){native_file_refuse(env,binding);return 0;}
    if(binding->fileOperation==NATIVE_FILE_MAPPING_DUPLICATE){
        Binding *actual=state->stack[state->depth-1];
        if(!actual||native_has_producer(actual->owners)){native_file_refuse(env,binding);return 0;}
    }
    if(binding->fileArgument>=2)receiver=(jobject)(uintptr_t)native_unsafe_argument(binding,frame,binding->fileArgument);
    state->control++;
    use->receiver=native_original.NewGlobalRef(env,receiver);use->operation=(jint)binding->fileOperation;
    if(binding->fileOperation==6){use->address=(jlong)native_unsafe_argument(binding,frame,2);use->length=(jlong)native_unsafe_argument(binding,frame,3);}
    if(binding->fileOperation==7)use->address=(jlong)(jint)native_unsafe_argument(binding,frame,2);
    if(binding->fileOperation==NATIVE_FILE_MAPPING_DUPLICATE)use->address=(jlong)native_unsafe_argument(binding,frame,2);
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
        if(ready&&use->operation==NATIVE_FILE_MAPPING_DUPLICATE)
            ready=use->token&&native_original.IsInstanceOf(env,use->token,native_file_duplicate_type);
    }
    if(!ready)native_file_clear(env,use);
    state->control--;return ready;
}
static void native_file_finish(JNIEnv *env,NativeThread *state,NativeFileFrame *use,int completed,jlong returned){
    if(!use->declaring)return; // Controller-internal I/O bypasses observation before acquiring a scope.
    state->control++;
    if(use->token){
        jthrowable failure=native_original.ExceptionOccurred(env);if(failure)native_original.ExceptionClear(env);
        NativeFileFrame *previous=state->filePermit;state->filePermit=use;use->finishing=1;
        if(use->operation==NATIVE_FILE_MAPPING_DUPLICATE){
            // Only our preallocated receipt metadata is written here. Preserve
            // the actual return even if the following Java finish itself fails.
            native_original.SetLongField(env,use->token,native_file_duplicate_returned,returned);
            native_original.SetBooleanField(env,use->token,native_file_duplicate_succeeded,completed&&!failure?JNI_TRUE:JNI_FALSE);
            native_original.CallStaticVoidMethod(env,native_file_bridge,native_file_duplicate_end_query,use->token,returned,completed&&!failure?JNI_TRUE:JNI_FALSE);
        }else if(use->operation==8)native_original.CallStaticVoidMethod(env,native_file_bridge,native_io_end_query,use->token,completed&&!failure?JNI_TRUE:JNI_FALSE);
        else native_original.CallStaticVoidMethod(env,native_file_bridge,native_file_end_query,use->token);
        state->filePermit=previous;
        if(failure){
            if(native_original.ExceptionCheck(env)){native_original.ExceptionClear(env);InterlockedIncrement(&native_failures);}
            native_original.Throw(env,failure);native_original.DeleteLocalRef(env,failure);
        }
    }
    native_file_clear(env,use);state->control--;
}
static void native_file_end(JNIEnv *env,NativeThread *state,unsigned char *frame){
    NativeFileFrame *use=&state->fileFrames[state->depth-1];jlong returned=0;
    if(use->operation==NATIVE_FILE_MAPPING_DUPLICATE&&frame){
        Binding *binding=state->entries[state->depth-1];unsigned args=native_arguments(binding->signature);
        unsigned save=32+(args>4?(args-4)*8:0);
        memcpy(&returned,frame+save+96,sizeof(returned));
    }
    native_file_finish(env,state,use,1,returned);
}
static void native_file_thread_end(JNIEnv *env,NativeThread *state){
    for(unsigned i=state->depth;i>0;i--)if(state->fileFrames[i-1].declaring)native_file_finish(env,state,&state->fileFrames[i-1],0,0);
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
/* This query grants no write/close permission. It pins one actual original
 * duplicateForMapping frame while the Java scope retains both descriptors. */
static int native_file_mapping_original(JNIEnv *env,jmethodID method,jlocation location,CodeVMMethod *version,CodePool *pool){
    if(!code_method_relevant(env,method,NULL))return !native_original.ExceptionCheck(env);
    unsigned char *bytes=NULL;jint length=0;int valid=0;
    if((*native_ti)->GetBytecodes(native_ti,method,&length,&bytes)!=JVMTI_ERROR_NONE)return 0;
    AcquireSRWLockShared(&code_records);
    CodeCandidates scan=code_candidates("Lsun/nio/ch/FileDispatcherImpl;");
    for(CodeImage *image=code_candidate_next(&scan);image&&!valid;image=code_candidate_next(&scan)){
        if(!code_class_matches(env,image,native_file_types[NATIVE_FILE_DISPATCHER],NULL,"Lsun/nio/ch/FileDispatcherImpl;"))continue;
        CodeMethod *entry=code_method(image,"duplicateForMapping","(Ljava/io/FileDescriptor;)Ljava/io/FileDescriptor;");
        unsigned ordinal=0;
        if(!entry||!code_equivalent(image,entry,bytes,(unsigned)length,pool,version,location,&ordinal)||!code_vm_unchanged(method,version))continue;
        int original=1;
        for(unsigned i=0;i<entry->instructions&&original;i++){
            if(entry->unknown&&entry->unknown[i])original=0;
            for(OwnerLink *source=entry->owners?entry->owners[i]:NULL;source&&original;source=source->next)
                if(InterlockedCompareExchange(&source->owner->producer,0,0))original=0;
        }
        if(original)valid=1;
    }
    ReleaseSRWLockShared(&code_records);if(bytes)(*native_ti)->Deallocate(native_ti,bytes);
    return valid&&!native_original.ExceptionCheck(env);
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_mappingDuplicateFrame0(JNIEnv *env,jclass type,jlongArray identity,jint phase){
    if(!native_jni_ready||!native_controller||!native_original.IsSameObject(env,type,native_controller)
            ||!native_file_bridge||!native_file_types[NATIVE_FILE_DISPATCHER]||!identity||phase<0||phase>2
            ||native_original.GetArrayLength(env,identity)!=5)return JNI_FALSE;
    NativeThread *state=native_thread();if(!state||native_original.ExceptionCheck(env))return JNI_FALSE;
    state->control++;jthread thread=NULL;jint total=0,count=0;jvmtiFrameInfo frames[16];
    int valid=0,child=0;CodeVMMethod version={0};CodePool pool={0};jlong expected[5]={0};
    if(phase!=0){native_original.GetLongArrayRegion(env,identity,0,5,expected);if(native_original.ExceptionCheck(env))goto done;}
    if((*native_ti)->GetCurrentThread(native_ti,&thread)!=JVMTI_ERROR_NONE
            ||(*native_ti)->GetFrameCount(native_ti,thread,&total)!=JVMTI_ERROR_NONE
            ||(*native_ti)->GetStackTrace(native_ti,thread,0,16,frames,&count)!=JVMTI_ERROR_NONE)goto done;
    for(jint i=0;i<count&&!native_original.ExceptionCheck(env);i++){
        jclass declaring=NULL;char *name=NULL,*descriptor=NULL;jint modifiers=0;
        int ready=(*native_ti)->GetMethodDeclaringClass(native_ti,frames[i].method,&declaring)==JVMTI_ERROR_NONE
                &&(*native_ti)->GetMethodName(native_ti,frames[i].method,&name,&descriptor,NULL)==JVMTI_ERROR_NONE;
        int helper=ready&&(native_original.IsSameObject(env,declaring,native_controller)||native_original.IsSameObject(env,declaring,native_file_bridge));
        int dispatcher=ready&&native_original.IsSameObject(env,declaring,native_file_types[NATIVE_FILE_DISPATCHER]);
        int nativeCall=dispatcher&&phase==1&&!child&&strcmp(name,"duplicateHandle")==0&&strcmp(descriptor,"(J)J")==0
                &&(*native_ti)->GetMethodModifiers(native_ti,frames[i].method,&modifiers)==JVMTI_ERROR_NONE&&(modifiers&0x0108)==0x0108;
        int parent=dispatcher&&strcmp(name,"duplicateForMapping")==0
                &&strcmp(descriptor,"(Ljava/io/FileDescriptor;)Ljava/io/FileDescriptor;")==0&&frames[i].location>=0
                &&(phase!=1||child);
        if(parent){
            jboolean obsolete=JNI_TRUE;
            ready=(*native_ti)->IsMethodObsolete(native_ti,frames[i].method,&obsolete)==JVMTI_ERROR_NONE&&!obsolete
                    &&code_vm_snapshot(env,frames[i].method,&version,&pool,name,descriptor)
                    &&native_file_mapping_original(env,frames[i].method,frames[i].location,&version,&pool)
                    &&code_vm_unchanged(frames[i].method,&version);
            jlong actual[5]={(jlong)(uintptr_t)frames[i].method,(jlong)version.method,(jlong)version.constantMethod,(jlong)version.constants,(jlong)(total-i)};
            if(ready&&phase!=0)for(unsigned at=0;at<5;at++)if(actual[at]!=expected[at]){ready=0;break;}
            if(ready){if(phase==0)native_original.SetLongArrayRegion(env,identity,0,5,actual);valid=!native_original.ExceptionCheck(env);}
        }
        if(name)(*native_ti)->Deallocate(native_ti,(unsigned char*)name);if(descriptor)(*native_ti)->Deallocate(native_ti,(unsigned char*)descriptor);
        if(declaring)native_original.DeleteLocalRef(env,declaring);
        if(parent||(!helper&&!nativeCall))break;
        if(nativeCall)child=1;
    }
done:
    free(version.handlers);code_pool_free(&pool);if(thread)native_original.DeleteLocalRef(env,thread);
    state->control--;return valid&&!native_original.ExceptionCheck(env)?JNI_TRUE:JNI_FALSE;
}
