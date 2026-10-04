/* OpenJDK 17 method metadata, located through the actual running VM's exported layout. */
typedef struct CodeVMTable {uintptr_t entries;uint64_t stride,name,field,isStatic,offset,address,size,value;} CodeVMTable;
typedef struct CodeVMLayout {
    HMODULE image;CodeVMTable fields,types,integers;int ready;
    uint64_t methodSize,methodConstant,constantSize,constantFlags,constantCode,constantPool,constantName,constantSignature;
    uint64_t poolSize,poolLength,poolTags,poolOperands,poolHolder,poolResolved,klassName,arrayLength,byteData,shortData,klassData,symbolLength,symbolData;
    uint64_t headerSize,exceptionSize,exceptionStart,exceptionEnd,exceptionTarget,exceptionType,parameterSize,checkedSize;
    unsigned line,locals,exceptions,checked,generic,parameters,annotations[4];
    unsigned unresolvedClass,classIndex,stringIndex,classError,handleError,typeError,dynamicError;
} CodeVMLayout;
/* Scratch bytes have no authority or retained VM identity. One version query
 * reuses them while each actual method still supplies a fresh complete image. */
typedef struct CodeVMBuffer {unsigned char *header,*table;size_t headerSize,tableSize;} CodeVMBuffer;
static int code_vm_buffer(unsigned char **bytes,size_t *capacity,size_t size){
    if(size<=*capacity)return 1;
    unsigned char *next=(unsigned char*)realloc(*bytes,size);if(!next)return 0;*bytes=next;*capacity=size;return 1;
}
static void code_vm_buffer_free(CodeVMBuffer *buffer){free(buffer->header);free(buffer->table);memset(buffer,0,sizeof(*buffer));}
static CodeVMLayout code_vm_layout;
static INIT_ONCE code_vm_once=INIT_ONCE_STATIC_INIT;
static int code_vm_read(uintptr_t address,void *destination,size_t size){
    SIZE_T copied=0;
    return address&&size<=UINTPTR_MAX-address&&ReadProcessMemory(GetCurrentProcess(),(LPCVOID)address,destination,size,&copied)&&copied==size;
}
static int code_vm_export(HMODULE image,const char *name,void *value,size_t size){
    uintptr_t address=(uintptr_t)GetProcAddress(image,name);
    return address&&native_image((void*)address)==image&&code_vm_read(address,value,size);
}
static int code_vm_name(uintptr_t address,const char *expected){
    size_t length=strlen(expected);char *name=(char*)malloc(length+1);if(!name)return 0;
    int equal=code_vm_read(address,name,length+1)&&!memcmp(name,expected,length+1);free(name);return equal;
}
static int code_vm_table(CodeVMTable *table,HMODULE image,const char *entries,const char *stride,const char *name){
    return code_vm_export(image,entries,&table->entries,sizeof(table->entries))
            &&native_image((void*)table->entries)==image
            &&code_vm_export(image,stride,&table->stride,sizeof(table->stride))
            &&code_vm_export(image,name,&table->name,sizeof(table->name))
            &&table->stride>=sizeof(uintptr_t)&&table->name<=table->stride-sizeof(uintptr_t);
}
static int code_vm_field(const char *type,const char *field,int expectedStatic,uint64_t *result){
    CodeVMTable *table=&code_vm_layout.fields;
    for(uintptr_t at=table->entries;native_image((void*)at)==code_vm_layout.image;){
        uintptr_t name=0,member=0;int32_t isStatic=0;
        if(!code_vm_read(at+table->name,&name,sizeof(name))||!name)return 0;
        if(code_vm_name(name,type)&&code_vm_read(at+table->field,&member,sizeof(member))&&member&&code_vm_name(member,field)){
            if(!code_vm_read(at+table->isStatic,&isStatic,sizeof(isStatic))||isStatic!=expectedStatic)return 0;
            if(expectedStatic){uintptr_t address=0;if(!code_vm_read(at+table->address,&address,sizeof(address)))return 0;*result=address;return native_image((void*)address)==code_vm_layout.image;}
            return code_vm_read(at+table->offset,result,sizeof(*result));
        }
        if(table->stride>UINTPTR_MAX-at)return 0;at+=(uintptr_t)table->stride;
    }
    return 0;
}
static int code_vm_type(const char *type,uint64_t *result){
    CodeVMTable *table=&code_vm_layout.types;
    for(uintptr_t at=table->entries;native_image((void*)at)==code_vm_layout.image;){
        uintptr_t name=0;if(!code_vm_read(at+table->name,&name,sizeof(name))||!name)return 0;
        if(code_vm_name(name,type))return code_vm_read(at+table->size,result,sizeof(*result))&&*result!=0;
        if(table->stride>UINTPTR_MAX-at)return 0;at+=(uintptr_t)table->stride;
    }
    return 0;
}
static int code_vm_integer(const char *constant,unsigned *result){
    CodeVMTable *table=&code_vm_layout.integers;
    for(uintptr_t at=table->entries;native_image((void*)at)==code_vm_layout.image;){
        uintptr_t name=0;int32_t value=0;if(!code_vm_read(at+table->name,&name,sizeof(name))||!name)return 0;
        if(code_vm_name(name,constant)){if(!code_vm_read(at+table->value,&value,sizeof(value))||value<0)return 0;*result=(unsigned)value;return 1;}
        if(table->stride>UINTPTR_MAX-at)return 0;at+=(uintptr_t)table->stride;
    }
    return 0;
}
static BOOL CALLBACK code_vm_initialize(PINIT_ONCE once,PVOID parameter,PVOID *context){
    (void)once;(void)context;JNIEnv *env=(JNIEnv*)parameter;JavaVM *vm=NULL;CodeVMLayout *layout=&code_vm_layout;
    if(!native_original.GetJavaVM||native_original.GetJavaVM(env,&vm)!=JNI_OK||!vm)return TRUE;
    HMODULE image=native_image((void*)native_original.GetJavaVM);
    if(!image||native_image((void*)(*vm)->GetEnv)!=image||native_image((void*)(*native_ti)->GetBytecodes)!=image)return TRUE;
    layout->image=image;
    if(!code_vm_table(&layout->fields,image,"gHotSpotVMStructs","gHotSpotVMStructEntryArrayStride","gHotSpotVMStructEntryTypeNameOffset")
            ||!code_vm_table(&layout->types,image,"gHotSpotVMTypes","gHotSpotVMTypeEntryArrayStride","gHotSpotVMTypeEntryTypeNameOffset")
            ||!code_vm_table(&layout->integers,image,"gHotSpotVMIntConstants","gHotSpotVMIntConstantEntryArrayStride","gHotSpotVMIntConstantEntryNameOffset"))return TRUE;
#define CODE_VM_EXPORT(table,member,name,width) if(!code_vm_export(image,name,&layout->table.member,sizeof(layout->table.member))||layout->table.member>layout->table.stride-(width))return TRUE
    CODE_VM_EXPORT(fields,field,"gHotSpotVMStructEntryFieldNameOffset",sizeof(uintptr_t));
    CODE_VM_EXPORT(fields,isStatic,"gHotSpotVMStructEntryIsStaticOffset",sizeof(int32_t));
    CODE_VM_EXPORT(fields,offset,"gHotSpotVMStructEntryOffsetOffset",sizeof(uint64_t));
    CODE_VM_EXPORT(fields,address,"gHotSpotVMStructEntryAddressOffset",sizeof(uintptr_t));
    CODE_VM_EXPORT(types,size,"gHotSpotVMTypeEntrySizeOffset",sizeof(uint64_t));
    CODE_VM_EXPORT(integers,value,"gHotSpotVMIntConstantEntryValueOffset",sizeof(int32_t));
#undef CODE_VM_EXPORT
    uint64_t version=0;int32_t major=0;
    if(!code_vm_field("Abstract_VM_Version","_vm_major_version",1,&version)||!code_vm_read((uintptr_t)version,&major,sizeof(major))||major!=17)return TRUE;
#define CODE_VM_FIELD(member,type,field) if(!code_vm_field(type,field,0,&layout->member))return TRUE
    CODE_VM_FIELD(methodConstant,"Method","_constMethod");
    CODE_VM_FIELD(constantSize,"ConstMethod","_constMethod_size");
    CODE_VM_FIELD(constantFlags,"ConstMethod","_flags");
    CODE_VM_FIELD(constantCode,"ConstMethod","_code_size");
    CODE_VM_FIELD(constantPool,"ConstMethod","_constants");
    CODE_VM_FIELD(constantName,"ConstMethod","_name_index");
    CODE_VM_FIELD(constantSignature,"ConstMethod","_signature_index");
    CODE_VM_FIELD(poolLength,"ConstantPool","_length");
    CODE_VM_FIELD(poolTags,"ConstantPool","_tags");
    CODE_VM_FIELD(poolOperands,"ConstantPool","_operands");
    CODE_VM_FIELD(poolHolder,"ConstantPool","_pool_holder");
    CODE_VM_FIELD(poolResolved,"ConstantPool","_resolved_klasses");
    CODE_VM_FIELD(klassName,"Klass","_name");
    CODE_VM_FIELD(arrayLength,"Array<int>","_length");
    CODE_VM_FIELD(byteData,"Array<u1>","_data");
    CODE_VM_FIELD(shortData,"Array<u2>","_data");
    CODE_VM_FIELD(klassData,"Array<Klass*>","_data[0]");
    CODE_VM_FIELD(symbolLength,"Symbol","_length");
    CODE_VM_FIELD(symbolData,"Symbol","_body[0]");
    CODE_VM_FIELD(exceptionStart,"ExceptionTableElement","start_pc");
    CODE_VM_FIELD(exceptionEnd,"ExceptionTableElement","end_pc");
    CODE_VM_FIELD(exceptionTarget,"ExceptionTableElement","handler_pc");
    CODE_VM_FIELD(exceptionType,"ExceptionTableElement","catch_type_index");
#undef CODE_VM_FIELD
#define CODE_VM_TYPE(member,type) if(!code_vm_type(type,&layout->member))return TRUE
    CODE_VM_TYPE(methodSize,"Method");CODE_VM_TYPE(headerSize,"ConstMethod");CODE_VM_TYPE(poolSize,"ConstantPool");
    CODE_VM_TYPE(exceptionSize,"ExceptionTableElement");CODE_VM_TYPE(parameterSize,"MethodParametersElement");CODE_VM_TYPE(checkedSize,"CheckedExceptionElement");
#undef CODE_VM_TYPE
#define CODE_VM_INTEGER(member,name) if(!code_vm_integer(name,&layout->member))return TRUE
    CODE_VM_INTEGER(line,"ConstMethod::_has_linenumber_table");CODE_VM_INTEGER(locals,"ConstMethod::_has_localvariable_table");
    CODE_VM_INTEGER(exceptions,"ConstMethod::_has_exception_table");CODE_VM_INTEGER(checked,"ConstMethod::_has_checked_exceptions");
    CODE_VM_INTEGER(generic,"ConstMethod::_has_generic_signature");CODE_VM_INTEGER(parameters,"ConstMethod::_has_method_parameters");
    CODE_VM_INTEGER(annotations[0],"ConstMethod::_has_method_annotations");CODE_VM_INTEGER(annotations[1],"ConstMethod::_has_parameter_annotations");
    CODE_VM_INTEGER(annotations[2],"ConstMethod::_has_type_annotations");CODE_VM_INTEGER(annotations[3],"ConstMethod::_has_default_annotations");
    CODE_VM_INTEGER(unresolvedClass,"JVM_CONSTANT_UnresolvedClass");CODE_VM_INTEGER(classIndex,"JVM_CONSTANT_ClassIndex");CODE_VM_INTEGER(stringIndex,"JVM_CONSTANT_StringIndex");
    CODE_VM_INTEGER(classError,"JVM_CONSTANT_UnresolvedClassInError");CODE_VM_INTEGER(handleError,"JVM_CONSTANT_MethodHandleInError");
    CODE_VM_INTEGER(typeError,"JVM_CONSTANT_MethodTypeInError");CODE_VM_INTEGER(dynamicError,"JVM_CONSTANT_DynamicInError");
#undef CODE_VM_INTEGER
    if(layout->headerSize>SIZE_MAX||layout->poolSize>SIZE_MAX||layout->exceptionSize>SIZE_MAX||layout->parameterSize>SIZE_MAX||layout->checkedSize>SIZE_MAX)return TRUE;
    if(layout->constantSize+4>layout->headerSize||layout->constantFlags+2>layout->headerSize||layout->constantCode+2>layout->headerSize
            ||layout->constantName+2>layout->headerSize||layout->constantSignature+2>layout->headerSize||layout->constantPool+sizeof(uintptr_t)>layout->headerSize
            ||layout->poolLength+4>layout->poolSize||layout->poolTags+sizeof(uintptr_t)>layout->poolSize||layout->poolOperands+sizeof(uintptr_t)>layout->poolSize
            ||layout->poolHolder+sizeof(uintptr_t)>layout->poolSize||layout->poolResolved+sizeof(uintptr_t)>layout->poolSize
            ||layout->exceptionStart+2>layout->exceptionSize||layout->exceptionEnd+2>layout->exceptionSize||layout->exceptionTarget+2>layout->exceptionSize||layout->exceptionType+2>layout->exceptionSize)return TRUE;
    layout->ready=1;return TRUE;
}
static void code_vm_put(unsigned char *bytes,uint64_t value,unsigned width){for(unsigned i=0;i<width;i++)bytes[width-i-1]=(unsigned char)(value>>(i*8));}
static int code_trace_type(JNIEnv *env,jobject value,const char *expected){
    if(!value)return 0;jclass type=native_original.GetObjectClass(env,value);char *signature=NULL;
    int matched=type&&(*native_ti)->GetClassSignature(native_ti,type,&signature,NULL)==JVMTI_ERROR_NONE
            &&signature&&!strcmp(signature,expected);
    if(signature)(*native_ti)->Deallocate(native_ti,(unsigned char*)signature);
    if(type)native_original.DeleteLocalRef(env,type);return matched;
}
/* Read the saved Throwable trace, not the already-unwound current thread.
 * JDK 17 layout: openjdk/jdk17u, javaClasses.hpp/javaClasses.cpp BacktraceIterator.
 * Symbols use this actual loaded VM's already-validated exported field layout. */
static void code_exception_frames(JNIEnv *env,jthrowable failure,unsigned cause){
    if(!InitOnceExecuteOnce(&code_vm_once,code_vm_initialize,env,NULL)||!code_vm_layout.ready){
        fprintf(stderr,"RONOVA_NATIVE_THROWABLE_FRAMES:%u:VM_LAYOUT_UNAVAILABLE\n",cause);return;
    }
    jclass throwable=native_original.FindClass(env,"java/lang/Throwable");
    jfieldID traceField=throwable?native_original.GetFieldID(env,throwable,"backtrace","Ljava/lang/Object;"):NULL;
    jfieldID depthField=throwable?native_original.GetFieldID(env,throwable,"depth","I"):NULL;
    jobject chunk=traceField?native_original.GetObjectField(env,failure,traceField):NULL;
    jint depth=depthField?native_original.GetIntField(env,failure,depthField):0;
    if(throwable)native_original.DeleteLocalRef(env,throwable);
    unsigned emitted=0,limit=depth>0?(unsigned)depth:0;int complete=1;if(limit>4096)limit=4096;
    while(chunk&&emitted<limit&&!native_original.ExceptionCheck(env)){
        if(!code_trace_type(env,chunk,"[Ljava/lang/Object;")||native_original.GetArrayLength(env,(jarray)chunk)!=6){complete=0;break;}
        jobject parts[4]={0};
        for(unsigned i=0;i<4;i++)parts[i]=native_original.GetObjectArrayElement(env,(jobjectArray)chunk,i);
        int valid=code_trace_type(env,parts[0],"[S")&&code_trace_type(env,parts[1],"[I")
                &&code_trace_type(env,parts[2],"[Ljava/lang/Object;")&&code_trace_type(env,parts[3],"[J");
        for(unsigned i=0;i<4&&valid;i++)valid=native_original.GetArrayLength(env,(jarray)parts[i])==32;
        jshort methods[32];jint positions[32];jlong names[32];
        if(valid){
            native_original.GetShortArrayRegion(env,(jshortArray)parts[0],0,32,methods);
            native_original.GetIntArrayRegion(env,(jintArray)parts[1],0,32,positions);
            native_original.GetLongArrayRegion(env,(jlongArray)parts[3],0,32,names);
            valid=!native_original.ExceptionCheck(env);
        }
        for(unsigned i=0;valid&&i<32&&emitted<limit;i++){
            jclass mirror=(jclass)native_original.GetObjectArrayElement(env,(jobjectArray)parts[2],i);char *owner=NULL,*name=NULL;uint16_t length=0;
            if(!code_trace_type(env,mirror,"Ljava/lang/Class;")){if(mirror)native_original.DeleteLocalRef(env,mirror);valid=0;break;}
            jvmtiError result=(*native_ti)->GetClassSignature(native_ti,mirror,&owner,NULL);uintptr_t symbol=(uintptr_t)names[i];
            if(symbol&&code_vm_read(symbol+(uintptr_t)code_vm_layout.symbolLength,&length,sizeof(length))){
                name=(char*)malloc((size_t)length+1);
                if(name){if(code_vm_read(symbol+(uintptr_t)code_vm_layout.symbolData,name,length))name[length]=0;else{free(name);name=NULL;}}
            }
            fprintf(stderr,"RONOVA_NATIVE_THROWABLE_FRAME:%u:%u:%s#%s:METHOD_ID=%u:BCI_VERSION=%u:CLASS_STATUS=%d\n",
                    cause,emitted++,owner?owner:"?",name?name:"?",(unsigned)(uint16_t)methods[i],(unsigned)positions[i],(int)result);
            free(name);if(owner)(*native_ti)->Deallocate(native_ti,(unsigned char*)owner);native_original.DeleteLocalRef(env,mirror);
        }
        for(unsigned i=0;i<4;i++)if(parts[i])native_original.DeleteLocalRef(env,parts[i]);
        if(!valid){complete=0;break;}
        jobject next=native_original.GetObjectArrayElement(env,(jobjectArray)chunk,4);
        native_original.DeleteLocalRef(env,chunk);chunk=next;
    }
    if(chunk)native_original.DeleteLocalRef(env,chunk);
    if(native_original.ExceptionCheck(env)){native_original.ExceptionClear(env);complete=0;}
    fprintf(stderr,"RONOVA_NATIVE_THROWABLE_FRAMES:%u:%u:%d:%s\n",cause,emitted,(int)depth,
            complete&&emitted==(unsigned)depth?"COMPLETE":"INCOMPLETE");
}
static int code_vm_tag(CodePool *pool,unsigned index,unsigned char *tag){return index<pool->count&&code_vm_read(pool->tags+index,tag,1);}
static int code_vm_slot(CodePool *pool,unsigned index,void *value,size_t size){return index<pool->count&&code_vm_read(pool->slots+(uintptr_t)index*sizeof(uintptr_t),value,size);}
static int code_vm_self(CodePool *pool,unsigned index){
    unsigned char tag=0;uint32_t slot=0;uintptr_t klass=0,symbol=0;
    if(!pool->vm||!pool->holder||!pool->holderName||!pool->resolved||!code_vm_tag(pool,index,&tag)||tag!=7||!code_vm_slot(pool,index,&slot,4))return 0;
    unsigned resolved=slot&65535,name=slot>>16;
    return resolved<pool->resolvedCount&&code_vm_read(pool->resolved+(uintptr_t)resolved*sizeof(uintptr_t),&klass,sizeof(klass))&&klass==pool->holder
            &&code_vm_tag(pool,name,&tag)&&tag==1&&code_vm_slot(pool,name,&symbol,sizeof(symbol))&&symbol==pool->holderName;
}
static size_t code_vm_utf_hash(uintptr_t symbol,size_t count){return code_pair_hash((unsigned)((uint64_t)symbol>>32),(unsigned)symbol)&(count-1);}
static int code_vm_utf_index(CodePool *pool){
    pool->utfIndexed=0;
    if(!pool->scanTags)pool->scanTags=(unsigned char*)malloc(pool->count);
    if(!pool->scanSlots)pool->scanSlots=(uintptr_t*)malloc((size_t)pool->count*sizeof(*pool->scanSlots));
    if(!pool->scanTags||!pool->scanSlots)return 0;
    if(!pool->utfBuckets){
        size_t count=32;while(count<(size_t)pool->count*2)count*=2;
        pool->utfBuckets=(unsigned*)calloc(count,sizeof(*pool->utfBuckets));if(!pool->utfBuckets)return 0;pool->utfBucketCount=count;
    }else memset(pool->utfBuckets,0,pool->utfBucketCount*sizeof(*pool->utfBuckets));
    if(!code_vm_read(pool->tags,pool->scanTags,pool->count)
            ||!code_vm_read(pool->slots,pool->scanSlots,(size_t)pool->count*sizeof(*pool->scanSlots)))return 0;
    for(unsigned i=1;i<pool->count;i++)if(pool->scanTags[i]==1){
        uintptr_t symbol=pool->scanSlots[i];size_t at=code_vm_utf_hash(symbol,pool->utfBucketCount);
        while(pool->utfBuckets[at]&&pool->scanSlots[pool->utfBuckets[at]]!=symbol)at=(at+1)&(pool->utfBucketCount-1);
        if(!pool->utfBuckets[at])pool->utfBuckets[at]=i;
    }
    pool->utfIndexed=1;return 1;
}
static unsigned code_vm_string_utf(CodePool *pool,uintptr_t symbol){
    int refreshed=0;
    if(!pool->utfIndexed){if(!code_vm_utf_index(pool))return 0;refreshed=1;}
    for(;;){
        size_t at=code_vm_utf_hash(symbol,pool->utfBucketCount);
        while(pool->utfBuckets[at]){
            unsigned index=pool->utfBuckets[at];
            if(pool->scanSlots[index]==symbol){
                unsigned char tag=0;uintptr_t actual=0;
                // The index only proposes a slot in this query's actual pool.
                // Every use still checks its current tag and Symbol identity.
                if(code_vm_tag(pool,index,&tag)&&tag==1&&code_vm_slot(pool,index,&actual,sizeof(actual))&&actual==symbol)return index;
                break;
            }
            at=(at+1)&(pool->utfBucketCount-1);
        }
        // A miss or changed slot is never a cached negative result. Refresh
        // the live pool once, then apply the same selected-slot check again.
        if(refreshed||!code_vm_utf_index(pool))return 0;refreshed=1;
    }
}
static int code_vm_entry(CodePool *pool,unsigned index){
    if(index>=pool->count||!pool->entries||!pool->sizes)return 0;
    if(pool->entries[index])return 1;if(!pool->vm)return 0;
    CodeVMLayout *layout=&code_vm_layout;unsigned char tag=0;uint32_t value=0;uint64_t wide=0;uintptr_t symbol=0;
    if(!code_vm_tag(pool,index,&tag))return 0;
    if(tag==layout->unresolvedClass||tag==layout->classError)tag=7;
    else if(tag==layout->handleError)tag=15;else if(tag==layout->typeError)tag=16;else if(tag==layout->dynamicError)tag=17;
    unsigned size=0;unsigned char small[9]={tag};
    if(tag==1){
        uint16_t length=0;if(!code_vm_slot(pool,index,&symbol,sizeof(symbol))||!symbol||!code_vm_read(symbol+(uintptr_t)layout->symbolLength,&length,sizeof(length)))return 0;
        size=(unsigned)length+3;unsigned char *entry=(unsigned char*)malloc(size);if(!entry)return 0;entry[0]=1;code_vm_put(entry+1,length,2);
        if(!code_vm_read(symbol+(uintptr_t)layout->symbolData,entry+3,length)){free(entry);return 0;}
        pool->entries[index]=entry;pool->sizes[index]=size;return 1;
    }
    if(tag==8){
        if(!code_vm_slot(pool,index,&symbol,sizeof(symbol))||!symbol||(symbol&1))return 0;
        unsigned utf=code_vm_string_utf(pool,symbol);
        if(!utf)return 0;size=3;code_vm_put(small+1,utf,2);
    }else if(tag==5||tag==6){if(!code_vm_slot(pool,index,&wide,sizeof(wide)))return 0;size=9;code_vm_put(small+1,wide,8);}
    else {
        if(!code_vm_slot(pool,index,&value,sizeof(value)))return 0;
        switch(tag){
            case 3:case 4:size=5;code_vm_put(small+1,value,4);break;
            case 7:size=3;code_vm_put(small+1,value>>16,2);break;
            case 9:case 10:case 11:case 12:case 17:case 18:size=5;code_vm_put(small+1,value&65535,2);code_vm_put(small+3,value>>16,2);break;
            case 15:if((value&65535)>9||(value&65535)==0)return 0;size=4;small[1]=(unsigned char)value;code_vm_put(small+2,value>>16,2);break;
            case 16:case 19:case 20:size=3;code_vm_put(small+1,value,2);break;
            default:
                if(tag==layout->classIndex||tag==layout->stringIndex){size=3;small[0]=tag==layout->classIndex?7:8;code_vm_put(small+1,value,2);break;}
                return 0;
        }
    }
    unsigned char *entry=(unsigned char*)malloc(size);if(!entry)return 0;memcpy(entry,small,size);pool->entries[index]=entry;pool->sizes[index]=size;return 1;
}
static int code_vm_operand(CodePool *pool,unsigned index,uint16_t *value){return index<pool->operandLength&&code_vm_read(pool->operands+(uintptr_t)index*2,value,2);}
static int code_vm_operand_offset(CodePool *pool,unsigned index,unsigned *offset){
    uint16_t low=0,high=0;if(index>UINT32_MAX/2||!code_vm_operand(pool,index*2,&low)||!code_vm_operand(pool,index*2+1,&high))return 0;
    *offset=(unsigned)low|((unsigned)high<<16);return *offset<=pool->operandLength;
}
static int code_vm_bootstrap(CodePool *pool,unsigned index){
    if(index>=pool->bootstrapCount||!pool->bootstraps)return 0;CodeBootstrap *bootstrap=&pool->bootstraps[index];if(bootstrap->loaded)return 1;if(!pool->vm)return 0;
    unsigned offset=0,limit=pool->operandLength;uint16_t handle=0,count=0;
    if(!code_vm_operand_offset(pool,index,&offset)||offset<pool->bootstrapCount*2||index+1<pool->bootstrapCount&&!code_vm_operand_offset(pool,index+1,&limit)
            ||limit<offset||limit-offset<2||!code_vm_operand(pool,offset,&handle)||!code_vm_operand(pool,offset+1,&count)||(unsigned)count!=limit-offset-2)return 0;
    unsigned *arguments=count?(unsigned*)calloc(count,sizeof(unsigned)):NULL;if(count&&!arguments)return 0;
    for(unsigned i=0;i<count;i++){uint16_t argument=0;if(!code_vm_operand(pool,offset+2+i,&argument)){free(arguments);return 0;}arguments[i]=argument;}
    bootstrap->handle=handle;bootstrap->count=count;bootstrap->arguments=arguments;bootstrap->loaded=1;return 1;
}
static int code_vm_pool(CodePool *pool,uintptr_t actual){
    if(pool->vm==actual&&pool->vmReady)return 1;
    code_pool_free(pool);
    CodeVMLayout *layout=&code_vm_layout;uintptr_t tags=0,operands=0,holder=0,resolved=0,name=0;int32_t length=0,tagLength=0,operandLength=0,resolvedLength=0;
    if(!actual)return 0;
    unsigned char *header=(unsigned char*)malloc((size_t)layout->poolSize);if(!header)return 0;
    int copied=code_vm_read(actual,header,(size_t)layout->poolSize);
    if(copied){
        memcpy(&length,header+(size_t)layout->poolLength,sizeof(length));
        memcpy(&tags,header+(size_t)layout->poolTags,sizeof(tags));
        memcpy(&operands,header+(size_t)layout->poolOperands,sizeof(operands));
        memcpy(&holder,header+(size_t)layout->poolHolder,sizeof(holder));
        memcpy(&resolved,header+(size_t)layout->poolResolved,sizeof(resolved));
    }
    free(header);
    if(!copied||length<1||length>65536||!tags||!code_vm_read(tags+(uintptr_t)layout->arrayLength,&tagLength,4)||tagLength!=length
            ||!holder||!code_vm_read(holder+(uintptr_t)layout->klassName,&name,sizeof(name))||!name)goto failed;
    pool->vm=actual;pool->count=(unsigned)length;pool->slots=actual+(uintptr_t)layout->poolSize;pool->tags=tags+(uintptr_t)layout->byteData;pool->allocated=1;
    pool->holder=holder;pool->holderName=name;
    if(resolved){if(!code_vm_read(resolved+(uintptr_t)layout->arrayLength,&resolvedLength,4)||resolvedLength<0||resolvedLength>65536)goto failed;
        pool->resolved=resolved+(uintptr_t)layout->klassData;pool->resolvedCount=(unsigned)resolvedLength;
        if(pool->resolved<resolved||pool->resolvedCount>(UINTPTR_MAX-pool->resolved)/sizeof(uintptr_t))goto failed;}
    if(pool->slots<actual||pool->count>(UINTPTR_MAX-pool->slots)/sizeof(uintptr_t))goto failed;
    pool->entries=(const unsigned char**)calloc(pool->count,sizeof(*pool->entries));pool->sizes=(uint32_t*)calloc(pool->count,sizeof(*pool->sizes));if(!pool->entries||!pool->sizes)goto failed;
    if(operands){
        if(!code_vm_read(operands+(uintptr_t)layout->arrayLength,&operandLength,4)||operandLength<0)goto failed;
        pool->operandLength=(unsigned)operandLength;pool->operands=operands+(uintptr_t)layout->shortData;
        if(operandLength){unsigned first=0;if(!code_vm_operand_offset(pool,0,&first)||first%2||first/2>65535)goto failed;pool->bootstrapCount=first/2;
            pool->bootstraps=(CodeBootstrap*)calloc((size_t)pool->bootstrapCount+1,sizeof(*pool->bootstraps));if(!pool->bootstraps)goto failed;}
    }
    pool->vmReady=1;return 1;
failed:
    // A later method in this query must not accept a half-built pool merely
    // because its entries and sizes arrays were allocated before the failure.
    code_pool_free(pool);return 0;
}
static int code_vm_back(uintptr_t *cursor,uintptr_t lower,size_t size){if(*cursor<lower||size>*cursor-lower)return 0;*cursor-=size;return 1;}
static int code_vm_skip_table(uintptr_t *cursor,uintptr_t lower,uint64_t element){
    uint16_t count=0;return code_vm_back(cursor,lower,2)&&code_vm_read(*cursor,&count,2)&&element<=SIZE_MAX&&count<=SIZE_MAX/(size_t)element&&code_vm_back(cursor,lower,(size_t)count*(size_t)element);
}
static int code_vm_unchanged(jmethodID method,CodeVMMethod *version){
    uintptr_t actual=0,constant=0,pool=0;CodeVMLayout *layout=&code_vm_layout;
    return code_vm_read((uintptr_t)method,&actual,sizeof(actual))&&actual==version->method
            &&code_vm_read(actual+(uintptr_t)layout->methodConstant,&constant,sizeof(constant))&&constant==version->constantMethod
            &&code_vm_read(constant+(uintptr_t)layout->constantPool,&pool,sizeof(pool))&&pool==version->constants;
}
static int code_vm_snapshot_checked(JNIEnv *env,jmethodID method,CodeVMMethod *version,CodePool *pool,const char *name,const char *signature,CodeVMBuffer *scratch,CodeFailure *failure){
    if(!InitOnceExecuteOnce(&code_vm_once,code_vm_initialize,env,NULL)||!code_vm_layout.ready)return code_failed(failure,"snapshot_layout",JVMTI_ERROR_NONE);
    CodeVMLayout *layout=&code_vm_layout;int32_t words=0;uint16_t flags=0,length=0,nameIndex=0,signatureIndex=0;
    if(!code_vm_read((uintptr_t)method,&version->method,sizeof(version->method))||!version->method
            ||!code_vm_read(version->method+(uintptr_t)layout->methodConstant,&version->constantMethod,sizeof(version->constantMethod))||!version->constantMethod)return code_failed(failure,"snapshot_method",JVMTI_ERROR_NONE);
    uintptr_t constant=version->constantMethod;
    // Copy this actual ConstMethod header once. Field offsets and bounds come
    // from the loaded VM; every call still obtains a fresh live snapshot.
    if(!code_vm_buffer(&scratch->header,&scratch->headerSize,(size_t)layout->headerSize))return code_failed(failure,"snapshot_header_allocation",JVMTI_ERROR_NONE);
    unsigned char *header=scratch->header;
    int copied=code_vm_read(constant,header,(size_t)layout->headerSize);
    if(copied){
        memcpy(&version->constants,header+(size_t)layout->constantPool,sizeof(version->constants));
        memcpy(&words,header+(size_t)layout->constantSize,4);
        memcpy(&flags,header+(size_t)layout->constantFlags,2);memcpy(&length,header+(size_t)layout->constantCode,2);
        memcpy(&nameIndex,header+(size_t)layout->constantName,2);memcpy(&signatureIndex,header+(size_t)layout->constantSignature,2);
    }
    if(!copied||words<=0||(uintptr_t)words>(UINTPTR_MAX-constant)/sizeof(uintptr_t))return code_failed(failure,"snapshot_header",JVMTI_ERROR_NONE);
    if(!code_vm_pool(pool,version->constants))return code_failed(failure,"snapshot_pool",JVMTI_ERROR_NONE);
    unsigned recognized=layout->line|layout->locals|layout->exceptions|layout->checked|layout->generic|layout->parameters|0x0040;
    for(unsigned i=0;i<4;i++)recognized|=layout->annotations[i];if(flags&~recognized)return code_failed(failure,"snapshot_flags",JVMTI_ERROR_NONE);
    if(!code_vm_entry(pool,nameIndex)||!code_vm_entry(pool,signatureIndex)||pool->entries[nameIndex][0]!=1||pool->entries[signatureIndex][0]!=1)return code_failed(failure,"snapshot_selector_constants",JVMTI_ERROR_NONE);
    // Version and frame queries already hold this actual method's JVMTI
    // selector. Borrow it only for this snapshot; compare both UTF entries as
    // before, and retain the final live Method/ConstMethod/pool identity check.
    char *queriedName=NULL,*queriedSignature=NULL;int matching=1;
    if(!name||!signature){
        jvmtiError error=(*native_ti)->GetMethodName(native_ti,method,&queriedName,&queriedSignature,NULL);matching=error==JVMTI_ERROR_NONE;
        if(!matching)code_failed(failure,"snapshot_method_name",error);
        name=queriedName;signature=queriedSignature;
    }
    if(matching)matching=strlen(name)==pool->sizes[nameIndex]-3&&!memcmp(name,pool->entries[nameIndex]+3,pool->sizes[nameIndex]-3)
            &&strlen(signature)==pool->sizes[signatureIndex]-3&&!memcmp(signature,pool->entries[signatureIndex]+3,pool->sizes[signatureIndex]-3);
    if(queriedName)(*native_ti)->Deallocate(native_ti,(unsigned char*)queriedName);if(queriedSignature)(*native_ti)->Deallocate(native_ti,(unsigned char*)queriedSignature);if(!matching)return code_failed(failure,"snapshot_selector",JVMTI_ERROR_NONE);
    uintptr_t cursor=constant+(uintptr_t)words*sizeof(uintptr_t),lower=constant+(uintptr_t)layout->headerSize+length;
    if(lower<constant||lower>cursor)return code_failed(failure,"snapshot_bounds",JVMTI_ERROR_NONE);
    for(unsigned i=0;i<4;i++)if(flags&layout->annotations[i])if(!code_vm_back(&cursor,lower,sizeof(uintptr_t)))return code_failed(failure,"snapshot_annotations",JVMTI_ERROR_NONE);
    if(flags&layout->generic)if(!code_vm_back(&cursor,lower,2))return code_failed(failure,"snapshot_generic",JVMTI_ERROR_NONE);
    if(flags&layout->parameters)if(!code_vm_skip_table(&cursor,lower,layout->parameterSize))return code_failed(failure,"snapshot_parameters",JVMTI_ERROR_NONE);
    if(flags&layout->checked)if(!code_vm_skip_table(&cursor,lower,layout->checkedSize))return code_failed(failure,"snapshot_checked_exceptions",JVMTI_ERROR_NONE);
    version->codeLength=length;
    if(flags&layout->exceptions){
        uint16_t count=0;if(!code_vm_back(&cursor,lower,2)||!code_vm_read(cursor,&count,2)||!count||count>SIZE_MAX/(size_t)layout->exceptionSize
                ||!code_vm_back(&cursor,lower,(size_t)count*(size_t)layout->exceptionSize))return code_failed(failure,"snapshot_handler_table",JVMTI_ERROR_NONE);
        version->handlerCount=count;version->handlers=(CodeHandler*)calloc(count,sizeof(*version->handlers));if(!version->handlers)return code_failed(failure,"snapshot_handler_allocation",JVMTI_ERROR_NONE);
        size_t size=(size_t)count*(size_t)layout->exceptionSize;
        if(!code_vm_buffer(&scratch->table,&scratch->tableSize,size))return code_failed(failure,"snapshot_handler_allocation",JVMTI_ERROR_NONE);
        unsigned char *table=scratch->table;
        int valid=code_vm_read(cursor,table,size);
        for(unsigned i=0;valid&&i<count;i++){
            const unsigned char *entry=table+(size_t)i*(size_t)layout->exceptionSize;uint16_t start=0,end=0,target=0,type=0;
            memcpy(&start,entry+(size_t)layout->exceptionStart,2);memcpy(&end,entry+(size_t)layout->exceptionEnd,2);
            memcpy(&target,entry+(size_t)layout->exceptionTarget,2);memcpy(&type,entry+(size_t)layout->exceptionType,2);
            if(start>=end||end>length||target>=length||type>=pool->count){valid=0;break;}
            version->handlers[i]=(CodeHandler){start,end,target,type};
        }
        if(!valid)return code_failed(failure,"snapshot_handler_contents",JVMTI_ERROR_NONE);
    }
    return code_vm_unchanged(method,version)?1:code_failed(failure,"snapshot_version_changed",JVMTI_ERROR_NONE);
}
static int code_vm_snapshot_query(JNIEnv *env,jmethodID method,CodeVMMethod *version,CodePool *pool,const char *name,const char *signature,CodeVMBuffer *scratch){
    return code_vm_snapshot_checked(env,method,version,pool,name,signature,scratch,NULL);
}
static int code_vm_snapshot_diagnostic(JNIEnv *env,jmethodID method,CodeVMMethod *version,CodePool *pool,const char *name,const char *signature,CodeFailure *failure){
    CodeVMBuffer scratch={0};int ready=code_vm_snapshot_checked(env,method,version,pool,name,signature,&scratch,failure);
    code_vm_buffer_free(&scratch);return ready;
}
static int code_vm_snapshot(JNIEnv *env,jmethodID method,CodeVMMethod *version,CodePool *pool,const char *name,const char *signature){
    CodeVMBuffer scratch={0};int ready=code_vm_snapshot_query(env,method,version,pool,name,signature,&scratch);
    code_vm_buffer_free(&scratch);return ready;
}
/* OpenJDK 17 Method::native_function_offset is sizeof(Method). The size and
 * ConstMethod layout below come from this loaded VM's exported metadata. */
static int code_vm_native_function(JNIEnv *env,jmethodID method,void **function){
    jint modifiers=0;CodeVMMethod version;CodePool pool;memset(&version,0,sizeof(version));memset(&pool,0,sizeof(pool));
    uintptr_t first=0,last=0;int ready=(*native_ti)->GetMethodModifiers(native_ti,method,&modifiers)==JVMTI_ERROR_NONE&&(modifiers&0x0100)!=0;
    if(ready)ready=code_vm_snapshot(env,method,&version,&pool,NULL,NULL)&&version.codeLength==0
            &&code_vm_layout.methodSize>=code_vm_layout.methodConstant+sizeof(uintptr_t)
            &&version.method<=UINTPTR_MAX-sizeof(uintptr_t)
            &&code_vm_layout.methodSize<=UINTPTR_MAX-version.method-sizeof(uintptr_t);
    if(ready){
        uintptr_t at=version.method+(uintptr_t)code_vm_layout.methodSize;
        ready=code_vm_read(at,&first,sizeof(first))&&code_vm_unchanged(method,&version)
                &&code_vm_read(at,&last,sizeof(last))&&first==last;
    }
    free(version.handlers);code_pool_free(&pool);
    if(ready)*function=(void*)first;return ready;
}
