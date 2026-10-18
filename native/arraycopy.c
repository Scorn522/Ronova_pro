/* System.arraycopy has its own VM entry and array contract. It is not Unsafe. */
typedef void (JNICALL *NativeSystemArrayCopy)(JNIEnv*,jclass,jobject,jint,jobject,jint,jint);
static jclass native_arraycopy_class;
static jmethodID native_arraycopy_method,native_arraycopy_component,native_arraycopy_internal_query,native_arraycopy_required_query,native_arraycopy_read_query,native_arraycopy_begin_query;
static NativeSystemArrayCopy native_arraycopy_vm;
static void *native_arraycopy_import_target;
static uintptr_t native_arraycopy_import_slot;
static unsigned char native_arraycopy_import_code[6];
static const char native_arraycopy_signature[]="(Ljava/lang/Object;ILjava/lang/Object;II)V";
typedef struct NativeArrayCopyContext {
    jobject source,target,read;jint from,to,length,prefix;unsigned phase;struct NativeArrayCopyContext *previous;
} NativeArrayCopyContext;
static int native_arraycopy_image_read(HMODULE image,uintptr_t address,void *value,SIZE_T length){
    if(!image||!length||length-1>UINTPTR_MAX-address||native_image((void*)address)!=image
            ||native_image((void*)(address+length-1))!=image)return 0;
    SIZE_T copied=0;
    return host_real_read_process_memory(GetCurrentProcess(),(void*)address,value,length,&copied)&&copied==length;
}
static int native_arraycopy_rva_read(HMODULE image,DWORD imageSize,ULONGLONG rva,void *value,SIZE_T length){
    uintptr_t base=(uintptr_t)image;
    return rva<=imageSize&&length<=imageSize-rva&&rva<=UINTPTR_MAX-base
            &&native_arraycopy_image_read(image,base+(uintptr_t)rva,value,length);
}
static int native_arraycopy_vm_import_prepare(void *target){
    HMODULE image=native_java_image;uintptr_t base=(uintptr_t)image,address=(uintptr_t)target;
    unsigned char code[6];IMAGE_DOS_HEADER dos;IMAGE_NT_HEADERS64 nt;
    if(!image||!native_arraycopy_vm||!native_arraycopy_image_read(image,address,code,sizeof(code))
            ||code[0]!=0xff||code[1]!=0x25||!native_arraycopy_image_read(image,base,&dos,sizeof(dos))
            ||dos.e_magic!=IMAGE_DOS_SIGNATURE||dos.e_lfanew<=0||(uintptr_t)dos.e_lfanew>UINTPTR_MAX-base
            ||!native_arraycopy_image_read(image,base+(uintptr_t)dos.e_lfanew,&nt,sizeof(nt))
            ||nt.Signature!=IMAGE_NT_SIGNATURE||nt.OptionalHeader.Magic!=IMAGE_NT_OPTIONAL_HDR64_MAGIC
            ||nt.OptionalHeader.NumberOfRvaAndSizes<=IMAGE_DIRECTORY_ENTRY_IMPORT)return 0;
    DWORD imageSize=nt.OptionalHeader.SizeOfImage;
    if(address<base||address-base>imageSize||sizeof(code)>imageSize-(address-base)||sizeof(code)>UINTPTR_MAX-address)return 0;
    int32_t displacement;memcpy(&displacement,code+2,sizeof(displacement));uintptr_t slot=address+sizeof(code);
    if(displacement<0){uintptr_t distance=(uintptr_t)-(int64_t)displacement;if(distance>slot)return 0;slot-=distance;}
    else{if((uintptr_t)displacement>UINTPTR_MAX-slot)return 0;slot+=(uintptr_t)displacement;}
    if(slot<base||slot-base>imageSize||sizeof(void*)>imageSize-(slot-base))return 0;
    ULONGLONG slotRva=slot-base;IMAGE_DATA_DIRECTORY directory=nt.OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_IMPORT];
    if(!directory.VirtualAddress||directory.VirtualAddress>imageSize||directory.Size>imageSize-directory.VirtualAddress)return 0;
    for(DWORD index=0;index<directory.Size/sizeof(IMAGE_IMPORT_DESCRIPTOR);index++){
        IMAGE_IMPORT_DESCRIPTOR descriptor;
        if(!native_arraycopy_rva_read(image,imageSize,(ULONGLONG)directory.VirtualAddress+(ULONGLONG)index*sizeof(descriptor),&descriptor,sizeof(descriptor)))return 0;
        if(!descriptor.Name)break;
        if(!descriptor.OriginalFirstThunk||!descriptor.FirstThunk||slotRva<descriptor.FirstThunk
                ||(slotRva-descriptor.FirstThunk)%sizeof(IMAGE_THUNK_DATA64))continue;
        char module[sizeof("jvm.dll")];
        if(!native_arraycopy_rva_read(image,imageSize,descriptor.Name,module,sizeof(module))
                ||module[sizeof(module)-1]!='\0'||_stricmp(module,"jvm.dll"))continue;
        IMAGE_THUNK_DATA64 lookup;ULONGLONG distance=slotRva-descriptor.FirstThunk;int present=0;
        for(ULONGLONG offset=0;offset<=distance;offset+=sizeof(lookup)){
            if(!native_arraycopy_rva_read(image,imageSize,(ULONGLONG)descriptor.OriginalFirstThunk+offset,&lookup,sizeof(lookup)))return 0;
            if(!lookup.u1.AddressOfData)break;
            if(offset==distance){present=1;break;}
        }
        if(!present)continue;
        if(IMAGE_SNAP_BY_ORDINAL64(lookup.u1.Ordinal))return 0;
        char name[sizeof("JVM_ArrayCopy")];
        if(lookup.u1.AddressOfData>imageSize||sizeof(WORD)>imageSize-lookup.u1.AddressOfData
                ||!native_arraycopy_rva_read(image,imageSize,lookup.u1.AddressOfData+sizeof(WORD),name,sizeof(name))
                ||memcmp(name,"JVM_ArrayCopy",sizeof(name)))return 0;
        void *current=NULL;
        if(!native_arraycopy_rva_read(image,imageSize,slotRva,&current,sizeof(current))||current!=(void*)native_arraycopy_vm)return 0;
        native_arraycopy_import_slot=slot;memcpy(native_arraycopy_import_code,code,sizeof(code));
        __atomic_store_n(&native_arraycopy_import_target,target,__ATOMIC_RELEASE);return 1;
    }
    return 0;
}
static int native_arraycopy_vm_import(void *target){
    /* The PE directory establishes this exact thunk and slot once. Every call
     * still checks its live instruction bytes and destination before selecting
     * the VM export; a replaced binding or import cannot reuse that verdict. */
    void *prepared=__atomic_load_n(&native_arraycopy_import_target,__ATOMIC_ACQUIRE);
    if(!prepared||target!=prepared)return 0;
    unsigned char code[sizeof(native_arraycopy_import_code)];void *current=NULL;
    return native_arraycopy_image_read(native_java_image,(uintptr_t)target,code,sizeof(code))
            &&!memcmp(code,native_arraycopy_import_code,sizeof(code))
            &&native_arraycopy_image_read(native_java_image,native_arraycopy_import_slot,&current,sizeof(current))
            &&current==(void*)native_arraycopy_vm;
}
static int native_arraycopy_vm_target(jmethodID method,void *target){
    return native_arraycopy_method&&method==native_arraycopy_method&&native_arraycopy_vm
            &&(target==(void*)native_arraycopy_vm||native_arraycopy_vm_import(target));
}
static void *native_arraycopy_internal_target(void){return (void*)native_arraycopy_vm;}
static void native_arraycopy_prepare_binding(JNIEnv *env,jclass type,jmethodID method,void *target){
    // registerNatives supplies a linker thunk directly. Inspect that exact
    // callback target before native_records is held or its wrapper published.
    if(native_arraycopy_class&&method==native_arraycopy_method&&target
            &&target!=(void*)native_arraycopy_vm
            &&!__atomic_load_n(&native_arraycopy_import_target,__ATOMIC_ACQUIRE)
            &&native_original.IsSameObject(env,type,native_arraycopy_class))
        native_arraycopy_vm_import_prepare(target);
}
static void native_arraycopy_classify(JNIEnv *env,jclass type,Binding *binding){
    if(native_arraycopy_class&&binding->method==native_arraycopy_method
            &&native_original.IsSameObject(env,type,native_arraycopy_class))binding->arraycopyOperation=1;
}
static int native_arraycopy_prepare(JNIEnv *env){
    NativeThread *state=native_thread();if(!state)return 0;state->control++;
    // Resolve the VM target before publishing the class/method used by the
    // already active NativeMethodBind callback, including initializer work.
    HMODULE image=native_vm_getenv_image(native_vm);
    if(image)native_arraycopy_vm=(NativeSystemArrayCopy)GetProcAddress(image,"JVM_ArrayCopy");
    native_java_image=GetModuleHandleW(L"java.dll");
    jclass type=native_original.FindClass(env,"java/lang/System"),classType=NULL;jint modifiers=0;
    if(type&&native_bootstrap_class(env,type,"Ljava/lang/System;")){
        native_arraycopy_class=(jclass)native_original.NewGlobalRef(env,type);
        native_arraycopy_method=native_original.GetStaticMethodID(env,type,"arraycopy",native_arraycopy_signature);
    }
    if(type)native_original.DeleteLocalRef(env,type);
    if(!native_original.ExceptionCheck(env))classType=native_original.FindClass(env,"java/lang/Class");
    if(classType)native_arraycopy_component=native_original.GetMethodID(env,classType,"getComponentType","()Ljava/lang/Class;");
    if(classType)native_original.DeleteLocalRef(env,classType);
    if(!native_original.ExceptionCheck(env))native_arraycopy_internal_query=native_original.GetStaticMethodID(env,native_controls,"nativeArrayCopyInternal","()Z");
    if(!native_original.ExceptionCheck(env))native_arraycopy_required_query=native_original.GetStaticMethodID(env,native_controls,"nativeArrayCopyRequired","(Ljava/lang/Object;Ljava/lang/Object;)Z");
    if(!native_original.ExceptionCheck(env))native_arraycopy_read_query=native_original.GetStaticMethodID(env,native_controls,"beginNativeArrayCopyRead","(Ljava/lang/Object;II)Ljava/lang/Object;");
    if(!native_original.ExceptionCheck(env))native_arraycopy_begin_query=native_original.GetStaticMethodID(env,native_controls,"beginNativeArrayCopy","(Ljava/lang/Object;IILjava/lang/Object;IILjava/lang/Object;[Ljava/lang/Module;)Ljava/lang/Object;");
    int ready=native_arraycopy_class&&native_arraycopy_method&&native_arraycopy_component&&native_arraycopy_internal_query&&native_arraycopy_required_query&&native_arraycopy_read_query&&native_arraycopy_begin_query&&native_arraycopy_vm
            &&(*native_ti)->GetMethodModifiers(native_ti,native_arraycopy_method,&modifiers)==JVMTI_ERROR_NONE
            &&(modifiers&0x0100)&&(modifiers&0x0008)&&!native_original.ExceptionCheck(env);
    state->control--;return ready;
}
static int native_arraycopy_install(JNIEnv *env){
    NativeThread *state=native_thread();if(!state||!native_arraycopy_class||!native_arraycopy_method)return 0;
    state->control++;
    int ready=native_binding_seed_one(env,state,native_arraycopy_class,native_arraycopy_method,
            "arraycopy",native_arraycopy_signature,4)==0;
    state->control--;return ready&&!native_original.ExceptionCheck(env);
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_arrayCopyInternalBoundary0(JNIEnv *env,jclass type){
    (void)env;(void)type;NativeThread *state=native_thread();
    return state&&state->control&&state->arraycopyQuery?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_arrayCopyReadBoundary0(JNIEnv *env,jclass type,jobject source,jint from,jint length){
    (void)type;NativeThread *state=native_thread();NativeArrayCopyContext *copy=state?state->arraycopy:NULL;
    return copy&&copy->phase==1&&copy->from==from&&copy->length==length
            &&native_original.IsSameObject(env,copy->source,source)?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT jboolean JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_arrayCopyBoundary0(JNIEnv *env,jclass type,jobject source,jint from,jint length,jobject target,jint to,jint prefix,jobject read){
    (void)type;NativeThread *state=native_thread();NativeArrayCopyContext *copy=state?state->arraycopy:NULL;
    return copy&&copy->phase==2&&copy->from==from&&copy->length==length&&copy->to==to&&copy->prefix==prefix
            &&native_original.IsSameObject(env,copy->source,source)&&native_original.IsSameObject(env,copy->target,target)
            &&native_original.IsSameObject(env,copy->read,read)?JNI_TRUE:JNI_FALSE;
}
static int native_arraycopy_internal(JNIEnv *env,NativeThread *state,Binding *binding){
    /* The counter alone grants nothing. Only the current VM implementation and
     * the existing nearest-writer query may perform controller bookkeeping. */
    if(!state->control||!native_arraycopy_vm_target(binding->method,binding->original)
            ||!InterlockedCompareExchange(&binding->valid,0,0)||!InterlockedCompareExchange(&binding->current,0,0)
            ||native_owner_stopped(binding->owners))return 0;
    state->arraycopyQuery++;
    jboolean internal=native_original.CallStaticBooleanMethod(env,native_controls,native_arraycopy_internal_query);
    state->arraycopyQuery--;return internal&&!native_original.ExceptionCheck(env);
}
static int native_arraycopy_kind(JNIEnv *env,jobject array,jclass *type,char *kind){
    char *signature=NULL;*type=native_original.GetObjectClass(env,array);*kind=0;
    if(!*type)return 0;
    jvmtiError result=(*native_ti)->GetClassSignature(native_ti,*type,&signature,NULL);
    if(result!=JVMTI_ERROR_NONE||!signature){if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);native_refuse(env,"NATIVE_ARRAYCOPY_TYPE_UNAVAILABLE");return 0;}
    if(signature[0]=='[')*kind=signature[1]&&strchr("ZBCSIJFD",signature[1])&&signature[2]=='\0'?signature[1]:'L';
    (*native_ti)->Deallocate(native_ti,(unsigned char*)signature);return 1;
}
static jobject native_arraycopy_snapshot(JNIEnv *env,jclass type,char kind,jint length){
    switch(kind){
        case 'Z':return native_original.NewBooleanArray(env,length);
        case 'B':return native_original.NewByteArray(env,length);
        case 'C':return native_original.NewCharArray(env,length);
        case 'S':return native_original.NewShortArray(env,length);
        case 'I':return native_original.NewIntArray(env,length);
        case 'J':return native_original.NewLongArray(env,length);
        case 'F':return native_original.NewFloatArray(env,length);
        case 'D':return native_original.NewDoubleArray(env,length);
        default:{
            jclass component=(jclass)native_original.CallObjectMethod(env,type,native_arraycopy_component);
            jobject result=component?native_original.NewObjectArray(env,length,component,NULL):NULL;
            if(component)native_original.DeleteLocalRef(env,component);return result;
        }
    }
}
static jobject native_arraycopy_value(JNIEnv *env,jobject snapshot,char kind,jint index){
    jvalue value;memset(&value,0,sizeof(value));
#define ARRAYCOPY_VALUE(KIND,NAME,TYPE,SLOT,BOX,SIGNATURE) \
    case KIND:native_original.Get##NAME##ArrayRegion(env,snapshot,index,1,&value.SLOT); \
        return native_original.ExceptionCheck(env)?NULL:native_box(env,BOX,SIGNATURE,value);
    switch(kind){
        ARRAYCOPY_VALUE('Z',Boolean,jboolean,z,"java/lang/Boolean","(Z)Ljava/lang/Boolean;")
        ARRAYCOPY_VALUE('B',Byte,jbyte,b,"java/lang/Byte","(B)Ljava/lang/Byte;")
        ARRAYCOPY_VALUE('C',Char,jchar,c,"java/lang/Character","(C)Ljava/lang/Character;")
        ARRAYCOPY_VALUE('S',Short,jshort,s,"java/lang/Short","(S)Ljava/lang/Short;")
        ARRAYCOPY_VALUE('I',Int,jint,i,"java/lang/Integer","(I)Ljava/lang/Integer;")
        ARRAYCOPY_VALUE('J',Long,jlong,j,"java/lang/Long","(J)Ljava/lang/Long;")
        ARRAYCOPY_VALUE('F',Float,jfloat,f,"java/lang/Float","(F)Ljava/lang/Float;")
        ARRAYCOPY_VALUE('D',Double,jdouble,d,"java/lang/Double","(D)Ljava/lang/Double;")
        default:return native_original.GetObjectArrayElement(env,snapshot,index);
    }
#undef ARRAYCOPY_VALUE
}
static void native_arraycopy_finish(JNIEnv *env,jobject token,int written){
    if(!token)return;
    /* A reference copy may have published its compatible prefix before the VM
     * throws ArrayStoreException. Keep both that write history and its cause. */
    jthrowable failure=native_original.ExceptionOccurred(env);
    if(!failure||!written){if(failure)native_original.DeleteLocalRef(env,failure);native_end_mutation(env,token,written);return;}
    native_original.ExceptionClear(env);
    native_original.CallStaticVoidMethod(env,native_controls,native_mutation_end,token,JNI_TRUE);
    if(native_original.ExceptionCheck(env)){native_original.ExceptionClear(env);InterlockedIncrement(&native_failures);}
    native_original.Throw(env,failure);native_original.DeleteLocalRef(env,failure);native_original.DeleteLocalRef(env,token);
}
static void JNICALL native_arraycopy_dispatch(JNIEnv *env,jclass type,jobject source,jint from,jobject target,jint to,jint length){
    NativeThread *state=native_thread();
    Binding *binding=state&&state->depth?state->stack[state->depth-1]:NULL;
    if(!binding||binding->method!=native_arraycopy_method||!binding->original){native_refuse(env,"NATIVE_ARRAYCOPY_SCOPE_UNAVAILABLE");return;}
    int vmImplementation=native_arraycopy_vm_target(binding->method,binding->original);
    /* An exact java.dll import thunk and the VM export implement this same
     * method. Dispatch the verified export directly so a later IAT write cannot
     * substitute code after the check or borrow controller bookkeeping. */
    NativeSystemArrayCopy actual=vmImplementation?native_arraycopy_vm:(NativeSystemArrayCopy)binding->original;
    jclass left=NULL,right=NULL,component=NULL;jobject snapshot=NULL,gate=NULL,read=NULL;jobjectArray contributors=NULL;
    NativeArrayCopyContext copy={.source=source,.target=target,.from=from,.to=to,.length=length,.previous=state->arraycopy};
    char sourceKind=0,targetKind=0;int bookkeeping=0,written=0,allowed=0,scoped=0,readTransferred=0;jint prefix=length;
    /* Invalid arguments never mutate an array. The actual published method
     * supplies its normal NPE/type/range exception, including length zero. */
    if(!source||!target){actual(env,type,source,from,target,to,length);return;}
    if(!native_arraycopy_kind(env,source,&left,&sourceKind)||!native_arraycopy_kind(env,target,&right,&targetKind))goto finish;
    if(!sourceKind||!targetKind||sourceKind!=targetKind){actual(env,type,source,from,target,to,length);goto finish;}
    jsize sourceLength=native_original.GetArrayLength(env,source),targetLength=native_original.GetArrayLength(env,target);
    if(native_original.ExceptionCheck(env))goto finish;
    if(from<0||to<0||length<0||from>sourceLength-length||to>targetLength-length||length==0){actual(env,type,source,from,target,to,length);goto finish;}
    if(vmImplementation){
        state->control++;state->arraycopyQuery++;
        jboolean required=native_original.CallStaticBooleanMethod(env,native_controls,native_arraycopy_required_query,source,target);
        state->arraycopyQuery--;state->control--;
        if(native_original.ExceptionCheck(env))goto finish;
        /* Ordinary arrays have no backing policy or history to capture. The
         * current invocation and both real objects still select this path. */
        if(!required){actual(env,type,source,from,target,to,length);goto finish;}
    }
    state->arraycopy=&copy;scoped=1;
    contributors=native_mutation_sources(env,state);
    if(!contributors||native_original.ExceptionCheck(env))goto finish;
    state->control++;bookkeeping=1;copy.phase=1;
    read=native_original.CallStaticObjectMethod(env,native_controls,native_arraycopy_read_query,source,from,length);
    copy.phase=0;copy.read=read;state->control--;bookkeeping=0;
    if(!read||native_original.ExceptionCheck(env)){if(!native_original.ExceptionCheck(env))native_refuse(env,"NATIVE_ARRAYCOPY_READ_UNAVAILABLE");goto finish;}
    snapshot=native_arraycopy_snapshot(env,left,sourceKind,length);
    if(!snapshot||native_original.ExceptionCheck(env)){if(!native_original.ExceptionCheck(env))native_refuse(env,"NATIVE_ARRAYCOPY_SNAPSHOT_UNAVAILABLE");goto finish;}
    /* This unpublished array only freezes the source values. It is never a
     * blanket permission to write the caller's destination or another array. */
    native_arraycopy_vm(env,native_arraycopy_class,source,from,snapshot,0,length);
    if(native_original.ExceptionCheck(env))goto finish;
    if(sourceKind=='L'){
        component=(jclass)native_original.CallObjectMethod(env,right,native_arraycopy_component);
        if(!component||native_original.ExceptionCheck(env)){if(!native_original.ExceptionCheck(env))native_refuse(env,"NATIVE_ARRAYCOPY_COMPONENT_UNAVAILABLE");goto finish;}
        for(jint i=0;i<length;i++){
            jobject value=native_original.GetObjectArrayElement(env,snapshot,i);
            if(native_original.ExceptionCheck(env)){if(value)native_original.DeleteLocalRef(env,value);goto finish;}
            int compatible=!value||native_original.IsInstanceOf(env,value,component);
            if(value)native_original.DeleteLocalRef(env,value);
            if(!compatible){prefix=i;break;}
        }
        if(prefix==0){actual(env,type,snapshot,0,target,to,length);goto finish;}
    }
    state->control++;bookkeeping=1;copy.prefix=prefix;copy.phase=2;
    gate=native_original.CallStaticObjectMethod(env,native_controls,native_arraycopy_begin_query,source,from,length,target,to,prefix,read,contributors);
    copy.phase=0;
    if(!gate||native_original.ExceptionCheck(env)){if(!native_original.ExceptionCheck(env))native_refuse(env,"NATIVE_ARRAYCOPY_MUTATION_UNAVAILABLE");goto finish;}
    readTransferred=1;
    int policy=sourceKind=='L'?1:native_primitive_array_policy(env,target);
    if(!policy||native_original.ExceptionCheck(env))goto finish;
    allowed=1;
    if(policy==1)for(jint i=0;i<prefix;i++){
        jobject value=native_arraycopy_value(env,snapshot,sourceKind,i);
        if(native_original.ExceptionCheck(env)){if(value)native_original.DeleteLocalRef(env,value);allowed=0;break;}
        int accepted=(sourceKind=='L'||value)&&native_array_allowed(env,target,to+i,value);
        if(value)native_original.DeleteLocalRef(env,value);
        if(!accepted){allowed=0;break;}
    }
    state->control--;bookkeeping=0;
    if(allowed&&!native_original.ExceptionCheck(env)){
        actual(env,type,snapshot,0,target,to,length);
        written=!native_original.ExceptionCheck(env)||(vmImplementation&&prefix>0&&prefix<length);
    }
finish:
    if(bookkeeping)state->control--;
    if(gate){state->control++;native_arraycopy_finish(env,gate,written);state->control--;}
    if(read&&!readTransferred){state->control++;native_end_mutation(env,read,0);state->control--;read=NULL;}
    if(read)native_original.DeleteLocalRef(env,read);
    if(scoped)state->arraycopy=copy.previous;
    if(contributors)native_original.DeleteLocalRef(env,contributors);
    if(snapshot)native_original.DeleteLocalRef(env,snapshot);
    if(component)native_original.DeleteLocalRef(env,component);
    if(left)native_original.DeleteLocalRef(env,left);if(right)native_original.DeleteLocalRef(env,right);
}
