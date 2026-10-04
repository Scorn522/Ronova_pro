/* Actual bootstrap Unsafe JNI bindings. Compiler intrinsics do not use these
 * entries and still require the Java call-site and VM writer paths. */
typedef struct NativeUnsafeEntry {jmethodID method;void *original;unsigned operation;struct NativeUnsafeEntry *next;} NativeUnsafeEntry;
static NativeUnsafeEntry *native_unsafe_entries;
typedef jboolean (JNICALL *NativeReferenceCAS)(JNIEnv*,jobject,jobject,jlong,jobject,jobject);
typedef struct NativeDefinitionDirectory {
    jclass owner; jobject directory,worker,unsafe; jlong offset;
    jmethodID caller; NativeReferenceCAS compare;
} NativeDefinitionDirectory;
static NativeDefinitionDirectory *native_definition_directory;

static void native_unsafe_classify(JNIEnv *env,jclass type,Binding *binding,const char *name,const char *descriptor){
    if(!native_unsafe_class||!native_original.IsSameObject(env,type,native_unsafe_class))return;
    if(strcmp(name,"allocateMemory0")==0&&strcmp(descriptor,"(J)J")==0){binding->unsafeOperation=UNSAFE_ALLOCATE;return;}
    if(strcmp(name,"reallocateMemory0")==0&&strcmp(descriptor,"(JJ)J")==0){binding->unsafeOperation=UNSAFE_REALLOCATE;return;}
    if(strcmp(name,"freeMemory0")==0&&strcmp(descriptor,"(J)V")==0){binding->unsafeOperation=UNSAFE_FREE;return;}
    static const struct {const char *kind,*suffix,*descriptor;} scalars[]={
        {"Object","Reference","Ljava/lang/Object;"},{"Boolean","Boolean","Z"},
        {"Byte","Byte","B"},{"Short","Short","S"},{"Char","Char","C"},
        {"Int","Int","I"},{"Long","Long","J"},{"Float","Float","F"},{"Double","Double","D"}
    };
    for(unsigned i=0;i<sizeof(scalars)/sizeof(scalars[0]);i++){
        char method[80],signature[160];
        snprintf(signature,sizeof(signature),"(Ljava/lang/Object;J)%s",scalars[i].descriptor);
        snprintf(method,sizeof(method),"get%s",scalars[i].suffix);int get=strcmp(name,method)==0;
        snprintf(method,sizeof(method),"get%sVolatile",scalars[i].suffix);get=get||strcmp(name,method)==0;
        if(get&&strcmp(descriptor,signature)==0){binding->unsafeOperation=UNSAFE_READ;binding->unsafeKind=scalars[i].kind;return;}
        snprintf(signature,sizeof(signature),"(Ljava/lang/Object;J%s)V",scalars[i].descriptor);
        snprintf(method,sizeof(method),"put%s",scalars[i].suffix);
        int put=strcmp(name,method)==0;
        snprintf(method,sizeof(method),"put%sVolatile",scalars[i].suffix);put=put||strcmp(name,method)==0;
        if(put&&strcmp(descriptor,signature)==0){binding->unsafeOperation=UNSAFE_PUT;binding->unsafeKind=scalars[i].kind;return;}
        if(i!=0&&i!=5&&i!=6)continue;
        snprintf(method,sizeof(method),"compareAndSet%s",scalars[i].suffix);
        snprintf(signature,sizeof(signature),"(Ljava/lang/Object;J%s%s)Z",scalars[i].descriptor,scalars[i].descriptor);
        if(strcmp(name,method)==0&&strcmp(descriptor,signature)==0){binding->unsafeOperation=UNSAFE_CAS;binding->unsafeKind=scalars[i].kind;return;}
        snprintf(method,sizeof(method),"compareAndExchange%s",scalars[i].suffix);
        snprintf(signature,sizeof(signature),"(Ljava/lang/Object;J%s%s)%s",scalars[i].descriptor,scalars[i].descriptor,scalars[i].descriptor);
        if(strcmp(name,method)==0&&strcmp(descriptor,signature)==0){binding->unsafeOperation=UNSAFE_EXCHANGE;binding->unsafeKind=scalars[i].kind;return;}
    }
    if(strcmp(name,"setMemory0")==0&&strcmp(descriptor,"(Ljava/lang/Object;JJB)V")==0)binding->unsafeOperation=UNSAFE_SET;
    else if(strcmp(name,"copyMemory0")==0&&strcmp(descriptor,"(Ljava/lang/Object;JLjava/lang/Object;JJ)V")==0)binding->unsafeOperation=UNSAFE_COPY;
    else if(strcmp(name,"copySwapMemory0")==0&&strcmp(descriptor,"(Ljava/lang/Object;JLjava/lang/Object;JJJ)V")==0)binding->unsafeOperation=UNSAFE_SWAP;
    if(binding->unsafeOperation)binding->unsafeKind="Byte";
}

static unsigned native_unsafe_save(Binding *binding){
    unsigned args=native_arguments(binding->signature);return 32+(args>4?(args-4)*8:0);
}
static uint64_t native_unsafe_argument(Binding *binding,unsigned char *frame,unsigned argument){
    uint64_t bits;unsigned at=argument<4?native_unsafe_save(binding)+argument*8:32+(argument-4)*8;
    memcpy(&bits,frame+at,sizeof(bits));return bits;
}
static jobject native_unsafe_box(JNIEnv *env,const char *kind,uint64_t bits){
    if(strcmp(kind,"Object")==0)return (jobject)(uintptr_t)bits;
    jvalue value;memset(&value,0,sizeof(value));memcpy(&value,&bits,sizeof(bits));
#define UNSAFE_BOX(KIND,CLASS,DESC) if(strcmp(kind,KIND)==0)return native_box(env,"java/lang/" CLASS,"(" DESC ")Ljava/lang/" CLASS ";",value);
    UNSAFE_BOX("Boolean","Boolean","Z") UNSAFE_BOX("Byte","Byte","B")
    UNSAFE_BOX("Char","Character","C") UNSAFE_BOX("Short","Short","S")
    UNSAFE_BOX("Int","Integer","I") UNSAFE_BOX("Long","Long","J")
    UNSAFE_BOX("Float","Float","F") UNSAFE_BOX("Double","Double","D")
#undef UNSAFE_BOX
    native_refuse(env,"NATIVE_UNSAFE_VALUE_LAYOUT_UNAVAILABLE");return NULL;
}
static void native_unsafe_destination(Binding *binding,unsigned char *frame,jobject *receiver,jlong *offset,jlong *length){
    unsigned destination=binding->unsafeOperation==UNSAFE_COPY||binding->unsafeOperation==UNSAFE_SWAP?4:2;
    *receiver=(jobject)(uintptr_t)native_unsafe_argument(binding,frame,destination);
    *offset=(jlong)native_unsafe_argument(binding,frame,destination+1);
    *length=binding->unsafeOperation>=UNSAFE_SET&&binding->unsafeOperation<=UNSAFE_SWAP?(jlong)native_unsafe_argument(binding,frame,destination+2):0;
}
static int native_unsafe_begin(JNIEnv *env,NativeThread *state,Binding *binding,unsigned char *frame){
    unsigned depth=state->depth-1;NativeUnsafeFrame *use=&state->unsafeFrames[depth];memset(use,0,sizeof(*use));
    if(binding->unsafeOperation>=UNSAFE_ALLOCATE&&binding->unsafeOperation<=UNSAFE_FREE)return native_memory_call_begin(env,state,binding,frame,use);
    NativeUnsafeMutation mutation;memset(&mutation,0,sizeof(mutation));
    native_unsafe_destination(binding,frame,&mutation.receiver,&mutation.offset,&mutation.length);
    // Standalone heap getters have no source-result consumer. Executed reads,
    // buffer reads and copies own their actual observation outside this leaf.
    // Raw reads still need the allocation lease below; no writer skips it.
    if(binding->unsafeOperation==UNSAFE_READ&&mutation.receiver)return 1;
    mutation.bulk=binding->unsafeOperation>=UNSAFE_SET&&binding->unsafeOperation<=UNSAFE_SWAP?JNI_TRUE:JNI_FALSE;
    mutation.reading=binding->unsafeOperation==UNSAFE_READ?JNI_TRUE:JNI_FALSE;
    mutation.copy=binding->unsafeOperation==UNSAFE_COPY||binding->unsafeOperation==UNSAFE_SWAP?JNI_TRUE:JNI_FALSE;
    if(mutation.copy){mutation.source=(jobject)(uintptr_t)native_unsafe_argument(binding,frame,2);mutation.sourceOffset=(jlong)native_unsafe_argument(binding,frame,3);}
    if(binding->unsafeOperation==UNSAFE_CAS||binding->unsafeOperation==UNSAFE_EXCHANGE)use->expected=native_unsafe_argument(binding,frame,4);
    state->control++;
    mutation.kind=native_original.NewStringUTF(env,binding->unsafeKind);
    jobject proposed=NULL;jobjectArray sources=NULL;int allowed=0;
    if(mutation.kind&&!native_original.ExceptionCheck(env)&&!mutation.bulk&&!mutation.reading){
        unsigned argument=binding->unsafeOperation==UNSAFE_PUT?4:5;
        proposed=native_unsafe_box(env,binding->unsafeKind,native_unsafe_argument(binding,frame,argument));
    }
    if(!native_original.ExceptionCheck(env)){
        // Exact controller gates have no heap receipt; their normal Java write
        // policy still runs below. Raw reads keep their caller contributors.
        jboolean noContributors=mutation.reading&&mutation.receiver?JNI_TRUE:JNI_FALSE;
        if(!mutation.bulk&&mutation.receiver&&!mutation.reading)
            noContributors=native_original.CallStaticBooleanMethod(env,native_controller,native_unsafe_metadata_query,mutation.receiver);
        if(!native_original.ExceptionCheck(env)){
            if(noContributors){
                jclass module=native_original.FindClass(env,"java/lang/Module");
                if(module){sources=native_original.NewObjectArray(env,0,module,NULL);native_original.DeleteLocalRef(env,module);}
            }else sources=native_mutation_sources(env,state);
        }
    }
    if(mutation.kind&&sources&&!native_original.ExceptionCheck(env)){
        mutation.previous=state->unsafeMutation;state->unsafeMutation=&mutation;
        jobject token=mutation.reading?native_original.CallStaticObjectMethod(env,native_tasks,native_unsafe_read_begin_query,mutation.receiver,mutation.offset,mutation.kind,sources)
                :mutation.copy?native_original.CallStaticObjectMethod(env,native_tasks,native_unsafe_copy_begin_query,mutation.source,mutation.sourceOffset,mutation.receiver,mutation.offset,mutation.length,sources)
                :native_original.CallStaticObjectMethod(env,native_tasks,native_unsafe_begin_query,mutation.receiver,mutation.offset,mutation.length,proposed,mutation.kind,mutation.bulk,sources);
        state->unsafeMutation=mutation.previous;
        if(!native_original.ExceptionCheck(env)&&!native_original.IsSameObject(env,token,native_unsafe_denied)){
            use->token=token;allowed=1;
        }else if(token)native_original.DeleteLocalRef(env,token);
    }
    if(proposed&&strcmp(binding->unsafeKind,"Object")!=0)native_original.DeleteLocalRef(env,proposed);
    if(sources)native_original.DeleteLocalRef(env,sources);
    if(mutation.kind)native_original.DeleteLocalRef(env,mutation.kind);
    state->control--;return allowed;
}
static void native_unsafe_end(JNIEnv *env,NativeThread *state,Binding *binding,unsigned char *frame){
    NativeUnsafeFrame *use=&state->unsafeFrames[state->depth-1];
    if(use->memoryCall){native_memory_call_end(env,state,binding,frame,use);return;}
    if(!use->token)return;
    uint64_t returned;memcpy(&returned,frame+native_unsafe_save(binding)+96,sizeof(returned));int applied=1;
    if(native_original.ExceptionCheck(env))applied=0;
    else if(binding->unsafeOperation==UNSAFE_CAS)applied=(jboolean)returned!=JNI_FALSE;
    else if(binding->unsafeOperation==UNSAFE_EXCHANGE){
        if(strcmp(binding->unsafeKind,"Object")==0)applied=native_original.IsSameObject(env,(jobject)(uintptr_t)returned,(jobject)(uintptr_t)use->expected);
        else if(strcmp(binding->unsafeKind,"Int")==0)applied=(jint)returned==(jint)use->expected;
        else applied=returned==use->expected;
    }
    state->control++;native_end_mutation(env,use->token,applied);state->control--;memset(use,0,sizeof(*use));
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_unsafeMutationBoundary0(JNIEnv *env,jclass type,jobject receiver,jlong offset,jlong length,jstring kind,jboolean bulk,jboolean reading){
    (void)type;NativeThread *state=native_thread();NativeUnsafeMutation *mutation=state?state->unsafeMutation:NULL;
    return mutation&&mutation->offset==offset&&mutation->length==length&&mutation->bulk==bulk&&mutation->reading==reading
            &&native_original.IsSameObject(env,mutation->receiver,receiver)&&native_original.IsSameObject(env,mutation->kind,kind)?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_unsafeControlScope0(JNIEnv *env,jclass type){
    (void)env;(void)type;NativeThread *state=native_thread();return state&&state->control?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_unsafeCopyBoundary0(JNIEnv *env,jclass type,jobject source,jlong sourceOffset,jobject receiver,jlong offset,jlong length){
    (void)type;NativeThread *state=native_thread();NativeUnsafeMutation *mutation=state?state->unsafeMutation:NULL;
    return mutation&&mutation->copy&&mutation->sourceOffset==sourceOffset&&mutation->offset==offset&&mutation->length==length
            &&native_original.IsSameObject(env,mutation->source,source)&&native_original.IsSameObject(env,mutation->receiver,receiver)?JNI_TRUE:JNI_FALSE;
}
static uint64_t native_unsafe_result(JNIEnv *env,Binding *binding,unsigned char *frame){
    if(binding->unsafeOperation!=UNSAFE_EXCHANGE)return 0;
    NativeThread *state=native_thread();if(!state)return 0;state->control++;
    jobject receiver;jlong offset,length;native_unsafe_destination(binding,frame,&receiver,&offset,&length);
    jstring kind=native_original.NewStringUTF(env,binding->unsafeKind);
    jobject value=kind?native_original.CallStaticObjectMethod(env,native_unsafe_backing,native_unsafe_read_query,receiver,offset,kind):NULL;
    uint64_t bits=0;
    if(!native_original.ExceptionCheck(env)){
        if(strcmp(binding->unsafeKind,"Object")==0)bits=(uint64_t)(uintptr_t)value;
        else if(value){
            jclass boxed=native_original.GetObjectClass(env,value);int wide=strcmp(binding->unsafeKind,"Long")==0;
            jmethodID accessor=boxed?native_original.GetMethodID(env,boxed,wide?"longValue":"intValue",wide?"()J":"()I"):NULL;
            if(accessor)bits=wide?(uint64_t)native_original.CallLongMethod(env,value,accessor):(uint64_t)(uint32_t)native_original.CallIntMethod(env,value,accessor);
            if(boxed)native_original.DeleteLocalRef(env,boxed);
        }
    }
    if(value&&strcmp(binding->unsafeKind,"Object")!=0)native_original.DeleteLocalRef(env,value);
    if(kind)native_original.DeleteLocalRef(env,kind);state->control--;return bits;
}
static int native_unsafe_prepare(JNIEnv *env){
    NativeThread *state=native_thread();if(!state)return 0;state->control++;
    jclass unsafe=native_original.FindClass(env,"jdk/internal/misc/Unsafe"),backing=NULL,booleanType=NULL;
    if(unsafe&&native_bootstrap_class(env,unsafe,"Ljdk/internal/misc/Unsafe;")){
        native_unsafe_class=(jclass)native_original.NewGlobalRef(env,unsafe);
        native_unsafe_register=native_original.GetStaticMethodID(env,unsafe,"registerNatives","()V");
    }
    if(!native_original.ExceptionCheck(env))backing=native_original.FindClass(env,"dev/ronova/pro/bootstrap/BackingBridge");
    if(backing&&native_bootstrap_class(env,backing,"Ldev/ronova/pro/bootstrap/BackingBridge;")){
        native_unsafe_backing=(jclass)native_original.NewGlobalRef(env,backing);
        native_unsafe_read_query=native_original.GetStaticMethodID(env,backing,"retained","(Ljava/lang/Object;JLjava/lang/String;)Ljava/lang/Object;");
    }
    if(!native_original.ExceptionCheck(env))native_unsafe_begin_query=native_original.GetStaticMethodID(env,native_tasks,"beginNativeUnsafeMutation","(Ljava/lang/Object;JJLjava/lang/Object;Ljava/lang/String;Z[Ljava/lang/Module;)Ljava/lang/Object;");
    if(!native_original.ExceptionCheck(env))native_unsafe_read_begin_query=native_original.GetStaticMethodID(env,native_tasks,"beginNativeUnsafeRead","(Ljava/lang/Object;JLjava/lang/String;[Ljava/lang/Module;)Ljava/lang/Object;");
    if(!native_original.ExceptionCheck(env))native_unsafe_copy_begin_query=native_original.GetStaticMethodID(env,native_tasks,"beginNativeUnsafeCopy","(Ljava/lang/Object;JLjava/lang/Object;JJ[Ljava/lang/Module;)Ljava/lang/Object;");
    if(!native_original.ExceptionCheck(env))native_unsafe_metadata_query=native_original.GetStaticMethodID(env,native_controller,"unsafeMetadata","(Ljava/lang/Object;)Z");
    if(!native_original.ExceptionCheck(env))native_memory_gate=native_original.GetStaticMethodID(env,native_tasks,"rawMemoryCaller","()Z");
    if(!native_original.ExceptionCheck(env))booleanType=native_original.FindClass(env,"java/lang/Boolean");
    jfieldID no=booleanType?native_original.GetStaticFieldID(env,booleanType,"FALSE","Ljava/lang/Boolean;"):NULL;
    jobject denied=no?native_original.GetStaticObjectField(env,booleanType,no):NULL;
    if(denied){native_unsafe_denied=native_original.NewGlobalRef(env,denied);native_original.DeleteLocalRef(env,denied);}
    if(unsafe)native_original.DeleteLocalRef(env,unsafe);if(backing)native_original.DeleteLocalRef(env,backing);if(booleanType)native_original.DeleteLocalRef(env,booleanType);
    int ready=native_unsafe_class&&native_unsafe_backing&&native_unsafe_register&&native_unsafe_read_query&&native_unsafe_begin_query&&native_unsafe_read_begin_query&&native_unsafe_copy_begin_query&&native_unsafe_metadata_query&&native_memory_gate&&native_unsafe_denied&&!native_original.ExceptionCheck(env);
    state->control--;return ready;
}
static int native_unsafe_install(JNIEnv *env){
    NativeThread *state=native_thread();if(!state||native_original.ExceptionCheck(env))return 0;
    // The actual boot class's own registrar submits its original native table;
    // MethodBind captures each original address before publishing its wrapper.
    state->control++;state->unsafeInstalling++;
    native_original.CallStaticVoidMethod(env,native_unsafe_class,native_unsafe_register);
    state->unsafeInstalling--;state->control--;
    return !native_original.ExceptionCheck(env);
}
static int native_unsafe_vm_target(jmethodID method,void *target){
    int matched=0;AcquireSRWLockShared(&native_records);
    for(NativeUnsafeEntry *entry=native_unsafe_entries;entry;entry=entry->next)
        if(entry->method==method&&entry->original==target){matched=1;break;}
    ReleaseSRWLockShared(&native_records);return matched;
}
static int native_unsafe_registered(JNIEnv *env,NativeThread *state,jclass type,const JNINativeMethod *methods,jmethodID *actual,jint count,void *caller){
    if(!native_unsafe_class||!native_original.IsSameObject(env,type,native_unsafe_class))return 1;
    // The real registrar's submitted table also captures methods that were
    // already bound before attachment, for which an unchanged address posts no
    // MethodBind event. Retain only entries actually submitted by this VM.
    void *current=NULL;if(count&&!code_vm_native_function(env,actual[0],&current))return 0;
    if(!code_vm_layout.image||native_image(caller)!=code_vm_layout.image)return 0;
    int ready=1;state->control++;
    for(jint i=0;i<count&&ready&&!native_original.ExceptionCheck(env);i++){
        Binding declaration;memset(&declaration,0,sizeof(declaration));declaration.method=actual[i];
        native_unsafe_classify(env,type,&declaration,methods[i].name,methods[i].signature);
        if(!declaration.unsafeOperation)continue;
        if(native_image(methods[i].fnPtr)!=code_vm_layout.image){ready=0;break;}
        AcquireSRWLockExclusive(&native_records);NativeUnsafeEntry *entry=native_unsafe_entries;
        while(entry&&entry->method!=actual[i])entry=entry->next;
        if(!entry){entry=(NativeUnsafeEntry*)calloc(1,sizeof(*entry));if(entry){entry->next=native_unsafe_entries;native_unsafe_entries=entry;}}
        if(entry){entry->method=actual[i];entry->original=methods[i].fnPtr;entry->operation=declaration.unsafeOperation;}else ready=0;
        ReleaseSRWLockExclusive(&native_records);
        if(ready)ready=native_binding_seed_one(env,state,type,actual[i],methods[i].name,methods[i].signature,3)==0;
    }
    state->control--;if(!ready)native_refuse(env,"NATIVE_UNSAFE_CURRENT_BINDINGS_PENDING");return ready&&!native_original.ExceptionCheck(env);
}

JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_initializeDefinitionDirectory0(JNIEnv *env,jclass type,jclass owner){
    if(!native_jni_ready||!native_original.IsSameObject(env,type,native_controller)
            ||!native_original.CallStaticBooleanMethod(env,native_controller,native_control_gate)){
        native_refuse(env,"ACTUAL_DEFINITION_DIRECTORY_AGENT_REQUIRED");return JNI_FALSE;
    }
    jfieldID declared=native_original.GetStaticFieldID(env,type,"definitionDirectoryOwner","Ljava/lang/Class;");
    jobject expected=declared?native_original.GetStaticObjectField(env,type,declared):NULL;
    int allowed=owner&&expected&&native_original.IsSameObject(env,owner,expected);
    if(expected)native_original.DeleteLocalRef(env,expected);
    if(!allowed){native_refuse(env,"ACTUAL_DEFINITION_DIRECTORY_CLASS_REQUIRED");return JNI_FALSE;}
    if(native_definition_directory)return native_original.IsSameObject(env,owner,native_definition_directory->owner);
    NativeDefinitionDirectory *record=(NativeDefinitionDirectory*)calloc(1,sizeof(*record));if(!record)return JNI_FALSE;
    jclass atomic=native_original.FindClass(env,"java/util/concurrent/atomic/AtomicReference");
    jobject directory=NULL,worker=NULL,unsafe=NULL,field=NULL;jclass actual=NULL;int ready=0;
    if(!atomic||!native_bootstrap_class(env,atomic,"Ljava/util/concurrent/atomic/AtomicReference;"))goto done;
    jfieldID directoryField=native_original.GetStaticFieldID(env,owner,"DIRECTORY","Ljava/util/concurrent/atomic/AtomicReference;");
    jfieldID workerField=native_original.GetStaticFieldID(env,owner,"WORKER","Ljava/util/concurrent/atomic/AtomicReference;");
    jfieldID value=native_original.GetFieldID(env,atomic,"value","Ljava/lang/Object;");
    jfieldID singleton=native_original.GetStaticFieldID(env,native_unsafe_class,"theUnsafe","Ljdk/internal/misc/Unsafe;");
    if(!directoryField||!workerField||!value||!singleton)goto done;
    directory=native_original.GetStaticObjectField(env,owner,directoryField);worker=native_original.GetStaticObjectField(env,owner,workerField);
    unsafe=native_original.GetStaticObjectField(env,native_unsafe_class,singleton);
    if(!directory||!worker||!unsafe||native_original.IsSameObject(env,directory,worker))goto done;
    actual=native_original.GetObjectClass(env,directory);int exact=actual&&native_original.IsSameObject(env,actual,atomic);
    if(actual)native_original.DeleteLocalRef(env,actual);actual=native_original.GetObjectClass(env,worker);
    exact=exact&&actual&&native_original.IsSameObject(env,actual,atomic);if(!exact)goto done;
    field=native_original.ToReflectedField(env,atomic,value,JNI_FALSE);
    jmethodID offset=native_original.GetMethodID(env,native_unsafe_class,"objectFieldOffset","(Ljava/lang/reflect/Field;)J");
    jmethodID compare=native_original.GetMethodID(env,native_unsafe_class,"compareAndSetReference","(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z");
    record->caller=native_original.GetStaticMethodID(env,type,"definitionDirectoryCaller","()Ljava/lang/Class;");
    if(!field||!offset||!compare||!record->caller)goto done;
    record->offset=native_original.CallLongMethod(env,unsafe,offset,field);
    AcquireSRWLockShared(&native_records);
    for(NativeUnsafeEntry *entry=native_unsafe_entries;entry;entry=entry->next)
        if(entry->method==compare&&entry->operation==UNSAFE_CAS&&native_image(entry->original)==code_vm_layout.image){record->compare=(NativeReferenceCAS)entry->original;break;}
    ReleaseSRWLockShared(&native_records);
    if(!record->compare||record->offset<0||native_original.ExceptionCheck(env))goto done;
    record->owner=(jclass)native_original.NewGlobalRef(env,owner);record->directory=native_original.NewGlobalRef(env,directory);
    record->worker=native_original.NewGlobalRef(env,worker);record->unsafe=native_original.NewGlobalRef(env,unsafe);
    if(!record->owner||!record->directory||!record->worker||!record->unsafe||native_original.ExceptionCheck(env))goto done;
    // Publish the immutable, fully bound pair before any transformer can use it.
    InterlockedExchangePointer((PVOID volatile*)&native_definition_directory,record);ready=1;
done:
    if(actual)native_original.DeleteLocalRef(env,actual);if(field)native_original.DeleteLocalRef(env,field);
    if(directory)native_original.DeleteLocalRef(env,directory);if(worker)native_original.DeleteLocalRef(env,worker);
    if(unsafe)native_original.DeleteLocalRef(env,unsafe);if(atomic)native_original.DeleteLocalRef(env,atomic);
    if(!ready){if(record->owner)native_original.DeleteGlobalRef(env,record->owner);if(record->directory)native_original.DeleteGlobalRef(env,record->directory);
        if(record->worker)native_original.DeleteGlobalRef(env,record->worker);if(record->unsafe)native_original.DeleteGlobalRef(env,record->unsafe);free(record);}
    return ready?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_compareDefinitionReference0(JNIEnv *env,jclass type,jobject slot,jobject expected,jobject next){
    NativeDefinitionDirectory *record=(NativeDefinitionDirectory*)InterlockedCompareExchangePointer((PVOID volatile*)&native_definition_directory,NULL,NULL);
    if(!record||!native_original.IsSameObject(env,type,native_controller)){
        native_refuse(env,"ACTUAL_DEFINITION_DIRECTORY_CAS_UNAVAILABLE");return JNI_FALSE;
    }
    jclass caller=(jclass)native_original.CallStaticObjectMethod(env,native_controller,record->caller);
    int allowed=!native_original.ExceptionCheck(env)&&caller&&native_original.IsSameObject(env,caller,record->owner)
            &&slot&&(native_original.IsSameObject(env,slot,record->directory)||native_original.IsSameObject(env,slot,record->worker));
    if(caller)native_original.DeleteLocalRef(env,caller);
    if(!allowed){native_refuse(env,"ACTUAL_DEFINITION_DIRECTORY_WRITER_REQUIRED");return JNI_FALSE;}
    // Invoke the VM's captured reference CAS, preserving its GC write barrier.
    // No Java field gate, heap-provenance lock, or thread-wide bypass is entered.
    return record->compare(env,record->unsafe,slot,record->offset,expected,next);
}
