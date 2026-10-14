/* Included by bindings.c after the output-use and commit helpers.  The VM sees
 * only private output storage.  Successful results are published together by
 * native_tooling_output_commit; a refused publication owns its cleanup here. */

static void native_tooling_output_span(NativeToolingOutputSpan *spans,size_t *count,
        void *target,const void *source,SIZE_T length){
    if(target&&length)spans[(*count)++]=(NativeToolingOutputSpan){target,source,length};
}

static void native_tooling_output_free(NativeToolingOutputUse *use,void *memory){
    if(memory&&use->record->original->Deallocate(use->record->api,(unsigned char*)memory)!=JVMTI_ERROR_NONE)
        InterlockedIncrement(&native_tooling_failures);
}

static void native_tooling_output_ref(NativeToolingOutputUse *use,jobject object){
    if(object&&use->env&&native_original.DeleteLocalRef)native_original.DeleteLocalRef(use->env,object);
}

static void native_tooling_output_refs(NativeToolingOutputUse *use,jint count,void *memory){
    jobject *objects=(jobject*)memory;
    if(objects)for(jint i=0;i<count;i++)native_tooling_output_ref(use,objects[i]);
    native_tooling_output_free(use,memory);
}

static void native_tooling_output_strings(NativeToolingOutputUse *use,jint count,char **strings){
    if(strings)for(jint i=0;i<count;i++)native_tooling_output_free(use,strings[i]);
    native_tooling_output_free(use,strings);
}

static void native_tooling_output_thread_info(NativeToolingOutputUse *use,jvmtiThreadInfo *info){
    native_tooling_output_free(use,info->name);
    native_tooling_output_ref(use,info->thread_group);
    native_tooling_output_ref(use,info->context_class_loader);
}

static void native_tooling_output_group_info(NativeToolingOutputUse *use,jvmtiThreadGroupInfo *info){
    native_tooling_output_free(use,info->name);
    native_tooling_output_ref(use,info->parent);
}

static void native_tooling_output_monitor_usage(NativeToolingOutputUse *use,jvmtiMonitorUsage *info){
    native_tooling_output_ref(use,info->owner);
    native_tooling_output_refs(use,info->waiter_count,info->waiters);
    native_tooling_output_refs(use,info->notify_waiter_count,info->notify_waiters);
}

static void native_tooling_output_monitor_depths(NativeToolingOutputUse *use,jint count,
        jvmtiMonitorStackDepthInfo *monitors){
    if(monitors)for(jint i=0;i<count;i++)native_tooling_output_ref(use,monitors[i].monitor);
    native_tooling_output_free(use,monitors);
}

static void native_tooling_output_variables(NativeToolingOutputUse *use,jint count,
        jvmtiLocalVariableEntry *variables){
    if(variables)for(jint i=0;i<count;i++){
        native_tooling_output_free(use,variables[i].name);
        native_tooling_output_free(use,variables[i].signature);
        native_tooling_output_free(use,variables[i].generic_signature);
    }
    native_tooling_output_free(use,variables);
}

static void native_tooling_output_stacks(NativeToolingOutputUse *use,jint count,jvmtiStackInfo *stacks){
    if(stacks)for(jint i=0;i<count;i++)native_tooling_output_ref(use,stacks[i].thread);
    /* The frame buffers are part of the one returned allocation. */
    native_tooling_output_free(use,stacks);
}

static void native_tooling_output_parameters(NativeToolingOutputUse *use,jint count,jvmtiParamInfo *parameters){
    if(parameters)for(jint i=0;i<count;i++)native_tooling_output_free(use,parameters[i].name);
    native_tooling_output_free(use,parameters);
}

static void native_tooling_output_extensions(NativeToolingOutputUse *use,jint count,
        jvmtiExtensionFunctionInfo *extensions){
    if(extensions)for(jint i=0;i<count;i++){
        native_tooling_output_free(use,extensions[i].id);
        native_tooling_output_free(use,extensions[i].short_description);
        native_tooling_output_parameters(use,extensions[i].param_count,extensions[i].params);
        native_tooling_output_free(use,extensions[i].errors);
    }
    native_tooling_output_free(use,extensions);
}

static void native_tooling_output_extension_events(NativeToolingOutputUse *use,jint count,
        jvmtiExtensionEventInfo *extensions){
    if(extensions)for(jint i=0;i<count;i++){
        native_tooling_output_free(use,extensions[i].id);
        native_tooling_output_free(use,extensions[i].short_description);
        native_tooling_output_parameters(use,extensions[i].param_count,extensions[i].params);
    }
    native_tooling_output_free(use,extensions);
}

#define TOOLING_OUTPUT_BEGIN(callbackSafe) \
    NativeToolingOutputUse use={0}; \
    jvmtiError error=native_tooling_output_begin(api,__builtin_return_address(0),callbackSafe,&use); \
    if(error!=JVMTI_ERROR_NONE)return error
#define TOOLING_OUTPUT_RETURN(result) do { \
    jvmtiError outputResult=(result);native_tooling_output_end(&use);return outputResult; \
} while(0)

/* Output pointers stay NULL when the caller supplied NULL, including optional
 * name/signature outputs.  Required-NULL and invalid-input errors remain the
 * original VM's decision. */
#define TOOLING_OUTPUT_VALUE(name,callbackSafe,type,parameters,direct,scratch,target,discard) \
static jvmtiError JNICALL native_tooling_output_##name parameters { \
    TOOLING_OUTPUT_BEGIN(callbackSafe); \
    if(use.internal)TOOLING_OUTPUT_RETURN(use.record->original->name direct); \
    type value={0}; \
    error=use.record->original->name scratch; \
    if(error==JVMTI_ERROR_NONE){ \
        NativeToolingOutputSpan spans[1];size_t count=0; \
        native_tooling_output_span(spans,&count,target,&value,sizeof(value)); \
        error=native_tooling_output_commit(&use,spans,count); \
        if(error!=JVMTI_ERROR_NONE){discard;} \
    } \
    TOOLING_OUTPUT_RETURN(error); \
}

TOOLING_OUTPUT_VALUE(GetThreadState,0,jint,
    (jvmtiEnv *api,jthread thread,jint *state_ptr),
    (api,thread,state_ptr),(api,thread,state_ptr?&value:NULL),state_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetCurrentThread,0,jthread,
    (jvmtiEnv *api,jthread *thread_ptr),
    (api,thread_ptr),(api,thread_ptr?&value:NULL),thread_ptr,native_tooling_output_ref(&use,value))
TOOLING_OUTPUT_VALUE(GetThreadInfo,0,jvmtiThreadInfo,
    (jvmtiEnv *api,jthread thread,jvmtiThreadInfo *info_ptr),
    (api,thread,info_ptr),(api,thread,info_ptr?&value:NULL),info_ptr,native_tooling_output_thread_info(&use,&value))
TOOLING_OUTPUT_VALUE(GetCurrentContendedMonitor,0,jobject,
    (jvmtiEnv *api,jthread thread,jobject *monitor_ptr),
    (api,thread,monitor_ptr),(api,thread,monitor_ptr?&value:NULL),monitor_ptr,native_tooling_output_ref(&use,value))
TOOLING_OUTPUT_VALUE(GetThreadGroupInfo,0,jvmtiThreadGroupInfo,
    (jvmtiEnv *api,jthreadGroup group,jvmtiThreadGroupInfo *info_ptr),
    (api,group,info_ptr),(api,group,info_ptr?&value:NULL),info_ptr,native_tooling_output_group_info(&use,&value))
TOOLING_OUTPUT_VALUE(GetFrameCount,0,jint,
    (jvmtiEnv *api,jthread thread,jint *count_ptr),
    (api,thread,count_ptr),(api,thread,count_ptr?&value:NULL),count_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetLocalObject,0,jobject,
    (jvmtiEnv *api,jthread thread,jint depth,jint slot,jobject *value_ptr),
    (api,thread,depth,slot,value_ptr),(api,thread,depth,slot,value_ptr?&value:NULL),value_ptr,native_tooling_output_ref(&use,value))
TOOLING_OUTPUT_VALUE(GetLocalInt,0,jint,
    (jvmtiEnv *api,jthread thread,jint depth,jint slot,jint *value_ptr),
    (api,thread,depth,slot,value_ptr),(api,thread,depth,slot,value_ptr?&value:NULL),value_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetLocalLong,0,jlong,
    (jvmtiEnv *api,jthread thread,jint depth,jint slot,jlong *value_ptr),
    (api,thread,depth,slot,value_ptr),(api,thread,depth,slot,value_ptr?&value:NULL),value_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetLocalFloat,0,jfloat,
    (jvmtiEnv *api,jthread thread,jint depth,jint slot,jfloat *value_ptr),
    (api,thread,depth,slot,value_ptr),(api,thread,depth,slot,value_ptr?&value:NULL),value_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetLocalDouble,0,jdouble,
    (jvmtiEnv *api,jthread thread,jint depth,jint slot,jdouble *value_ptr),
    (api,thread,depth,slot,value_ptr),(api,thread,depth,slot,value_ptr?&value:NULL),value_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetNamedModule,0,jobject,
    (jvmtiEnv *api,jobject loader,const char *package_name,jobject *module_ptr),
    (api,loader,package_name,module_ptr),(api,loader,package_name,module_ptr?&value:NULL),module_ptr,native_tooling_output_ref(&use,value))
TOOLING_OUTPUT_VALUE(IsModifiableClass,0,jboolean,
    (jvmtiEnv *api,jclass klass,jboolean *modifiable_ptr),
    (api,klass,modifiable_ptr),(api,klass,modifiable_ptr?&value:NULL),modifiable_ptr,(void)0)
TOOLING_OUTPUT_VALUE(Allocate,1,unsigned char*,
    (jvmtiEnv *api,jlong size,unsigned char **memory_ptr),
    (api,size,memory_ptr),(api,size,memory_ptr?&value:NULL),memory_ptr,native_tooling_output_free(&use,value))
TOOLING_OUTPUT_VALUE(GetClassStatus,0,jint,
    (jvmtiEnv *api,jclass klass,jint *status_ptr),
    (api,klass,status_ptr),(api,klass,status_ptr?&value:NULL),status_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetSourceFileName,0,char*,
    (jvmtiEnv *api,jclass klass,char **source_ptr),
    (api,klass,source_ptr),(api,klass,source_ptr?&value:NULL),source_ptr,native_tooling_output_free(&use,value))
TOOLING_OUTPUT_VALUE(GetClassModifiers,0,jint,
    (jvmtiEnv *api,jclass klass,jint *modifiers_ptr),
    (api,klass,modifiers_ptr),(api,klass,modifiers_ptr?&value:NULL),modifiers_ptr,(void)0)
TOOLING_OUTPUT_VALUE(IsInterface,0,jboolean,
    (jvmtiEnv *api,jclass klass,jboolean *interface_ptr),
    (api,klass,interface_ptr),(api,klass,interface_ptr?&value:NULL),interface_ptr,(void)0)
TOOLING_OUTPUT_VALUE(IsArrayClass,0,jboolean,
    (jvmtiEnv *api,jclass klass,jboolean *array_ptr),
    (api,klass,array_ptr),(api,klass,array_ptr?&value:NULL),array_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetClassLoader,0,jobject,
    (jvmtiEnv *api,jclass klass,jobject *loader_ptr),
    (api,klass,loader_ptr),(api,klass,loader_ptr?&value:NULL),loader_ptr,native_tooling_output_ref(&use,value))
TOOLING_OUTPUT_VALUE(GetObjectHashCode,0,jint,
    (jvmtiEnv *api,jobject object,jint *hash_ptr),
    (api,object,hash_ptr),(api,object,hash_ptr?&value:NULL),hash_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetObjectMonitorUsage,0,jvmtiMonitorUsage,
    (jvmtiEnv *api,jobject object,jvmtiMonitorUsage *info_ptr),
    (api,object,info_ptr),(api,object,info_ptr?&value:NULL),info_ptr,native_tooling_output_monitor_usage(&use,&value))
TOOLING_OUTPUT_VALUE(GetFieldDeclaringClass,0,jclass,
    (jvmtiEnv *api,jclass klass,jfieldID field,jclass *declaring_ptr),
    (api,klass,field,declaring_ptr),(api,klass,field,declaring_ptr?&value:NULL),declaring_ptr,native_tooling_output_ref(&use,value))
TOOLING_OUTPUT_VALUE(GetFieldModifiers,0,jint,
    (jvmtiEnv *api,jclass klass,jfieldID field,jint *modifiers_ptr),
    (api,klass,field,modifiers_ptr),(api,klass,field,modifiers_ptr?&value:NULL),modifiers_ptr,(void)0)
TOOLING_OUTPUT_VALUE(IsFieldSynthetic,0,jboolean,
    (jvmtiEnv *api,jclass klass,jfieldID field,jboolean *synthetic_ptr),
    (api,klass,field,synthetic_ptr),(api,klass,field,synthetic_ptr?&value:NULL),synthetic_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetMethodDeclaringClass,0,jclass,
    (jvmtiEnv *api,jmethodID method,jclass *declaring_ptr),
    (api,method,declaring_ptr),(api,method,declaring_ptr?&value:NULL),declaring_ptr,native_tooling_output_ref(&use,value))
TOOLING_OUTPUT_VALUE(GetMethodModifiers,0,jint,
    (jvmtiEnv *api,jmethodID method,jint *modifiers_ptr),
    (api,method,modifiers_ptr),(api,method,modifiers_ptr?&value:NULL),modifiers_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetMaxLocals,0,jint,
    (jvmtiEnv *api,jmethodID method,jint *max_ptr),
    (api,method,max_ptr),(api,method,max_ptr?&value:NULL),max_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetArgumentsSize,0,jint,
    (jvmtiEnv *api,jmethodID method,jint *size_ptr),
    (api,method,size_ptr),(api,method,size_ptr?&value:NULL),size_ptr,(void)0)
TOOLING_OUTPUT_VALUE(IsMethodNative,0,jboolean,
    (jvmtiEnv *api,jmethodID method,jboolean *native_ptr),
    (api,method,native_ptr),(api,method,native_ptr?&value:NULL),native_ptr,(void)0)
TOOLING_OUTPUT_VALUE(IsMethodSynthetic,0,jboolean,
    (jvmtiEnv *api,jmethodID method,jboolean *synthetic_ptr),
    (api,method,synthetic_ptr),(api,method,synthetic_ptr?&value:NULL),synthetic_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetVersionNumber,0,jint,
    (jvmtiEnv *api,jint *version_ptr),
    (api,version_ptr),(api,version_ptr?&value:NULL),version_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetCapabilities,0,jvmtiCapabilities,
    (jvmtiEnv *api,jvmtiCapabilities *capabilities_ptr),
    (api,capabilities_ptr),(api,capabilities_ptr?&value:NULL),capabilities_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetSourceDebugExtension,0,char*,
    (jvmtiEnv *api,jclass klass,char **extension_ptr),
    (api,klass,extension_ptr),(api,klass,extension_ptr?&value:NULL),extension_ptr,native_tooling_output_free(&use,value))
TOOLING_OUTPUT_VALUE(IsMethodObsolete,0,jboolean,
    (jvmtiEnv *api,jmethodID method,jboolean *obsolete_ptr),
    (api,method,obsolete_ptr),(api,method,obsolete_ptr?&value:NULL),obsolete_ptr,(void)0)
TOOLING_OUTPUT_VALUE(IsModifiableModule,0,jboolean,
    (jvmtiEnv *api,jobject module,jboolean *modifiable_ptr),
    (api,module,modifiable_ptr),(api,module,modifiable_ptr?&value:NULL),modifiable_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetThreadLocalStorage,0,void*,
    (jvmtiEnv *api,jthread thread,void **data_ptr),
    (api,thread,data_ptr),(api,thread,data_ptr?&value:NULL),data_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetTag,0,jlong,
    (jvmtiEnv *api,jobject object,jlong *tag_ptr),
    (api,object,tag_ptr),(api,object,tag_ptr?&value:NULL),tag_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetJNIFunctionTable,0,jniNativeInterface*,
    (jvmtiEnv *api,jniNativeInterface **table_ptr),
    (api,table_ptr),(api,table_ptr?&value:NULL),table_ptr,native_tooling_output_free(&use,value))
TOOLING_OUTPUT_VALUE(GetErrorName,0,char*,
    (jvmtiEnv *api,jvmtiError requested,char **name_ptr),
    (api,requested,name_ptr),(api,requested,name_ptr?&value:NULL),name_ptr,native_tooling_output_free(&use,value))
TOOLING_OUTPUT_VALUE(GetJLocationFormat,0,jvmtiJlocationFormat,
    (jvmtiEnv *api,jvmtiJlocationFormat *format_ptr),
    (api,format_ptr),(api,format_ptr?&value:NULL),format_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetSystemProperty,0,char*,
    (jvmtiEnv *api,const char *property,char **value_ptr),
    (api,property,value_ptr),(api,property,value_ptr?&value:NULL),value_ptr,native_tooling_output_free(&use,value))
TOOLING_OUTPUT_VALUE(GetPhase,0,jvmtiPhase,
    (jvmtiEnv *api,jvmtiPhase *phase_ptr),
    (api,phase_ptr),(api,phase_ptr?&value:NULL),phase_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetCurrentThreadCpuTimerInfo,1,jvmtiTimerInfo,
    (jvmtiEnv *api,jvmtiTimerInfo *info_ptr),
    (api,info_ptr),(api,info_ptr?&value:NULL),info_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetCurrentThreadCpuTime,1,jlong,
    (jvmtiEnv *api,jlong *nanos_ptr),
    (api,nanos_ptr),(api,nanos_ptr?&value:NULL),nanos_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetThreadCpuTimerInfo,0,jvmtiTimerInfo,
    (jvmtiEnv *api,jvmtiTimerInfo *info_ptr),
    (api,info_ptr),(api,info_ptr?&value:NULL),info_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetThreadCpuTime,0,jlong,
    (jvmtiEnv *api,jthread thread,jlong *nanos_ptr),
    (api,thread,nanos_ptr),(api,thread,nanos_ptr?&value:NULL),nanos_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetTimerInfo,1,jvmtiTimerInfo,
    (jvmtiEnv *api,jvmtiTimerInfo *info_ptr),
    (api,info_ptr),(api,info_ptr?&value:NULL),info_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetTime,1,jlong,
    (jvmtiEnv *api,jlong *nanos_ptr),
    (api,nanos_ptr),(api,nanos_ptr?&value:NULL),nanos_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetPotentialCapabilities,0,jvmtiCapabilities,
    (jvmtiEnv *api,jvmtiCapabilities *capabilities_ptr),
    (api,capabilities_ptr),(api,capabilities_ptr?&value:NULL),capabilities_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetAvailableProcessors,0,jint,
    (jvmtiEnv *api,jint *count_ptr),
    (api,count_ptr),(api,count_ptr?&value:NULL),count_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetEnvironmentLocalStorage,1,void*,
    (jvmtiEnv *api,void **data_ptr),
    (api,data_ptr),(api,data_ptr?&value:NULL),data_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetObjectSize,0,jlong,
    (jvmtiEnv *api,jobject object,jlong *size_ptr),
    (api,object,size_ptr),(api,object,size_ptr?&value:NULL),size_ptr,(void)0)
TOOLING_OUTPUT_VALUE(GetLocalInstance,0,jobject,
    (jvmtiEnv *api,jthread thread,jint depth,jobject *value_ptr),
    (api,thread,depth,value_ptr),(api,thread,depth,value_ptr?&value:NULL),value_ptr,native_tooling_output_ref(&use,value))

#define TOOLING_OUTPUT_ARRAY(name,type,parameters,direct,scratch,discard) \
static jvmtiError JNICALL native_tooling_output_##name parameters { \
    TOOLING_OUTPUT_BEGIN(0); \
    if(use.internal)TOOLING_OUTPUT_RETURN(use.record->original->name direct); \
    jint result_count=0;type *values=NULL; \
    error=use.record->original->name scratch; \
    if(error==JVMTI_ERROR_NONE){ \
        NativeToolingOutputSpan spans[2];size_t count=0; \
        native_tooling_output_span(spans,&count,count_ptr,&result_count,sizeof(result_count)); \
        native_tooling_output_span(spans,&count,values_ptr,&values,sizeof(values)); \
        error=native_tooling_output_commit(&use,spans,count); \
        if(error!=JVMTI_ERROR_NONE){discard;} \
    } \
    TOOLING_OUTPUT_RETURN(error); \
}

TOOLING_OUTPUT_ARRAY(GetAllModules,jobject,
    (jvmtiEnv *api,jint *count_ptr,jobject **values_ptr),
    (api,count_ptr,values_ptr),(api,count_ptr?&result_count:NULL,values_ptr?&values:NULL),
    native_tooling_output_refs(&use,result_count,values))
TOOLING_OUTPUT_ARRAY(GetAllThreads,jthread,
    (jvmtiEnv *api,jint *count_ptr,jthread **values_ptr),
    (api,count_ptr,values_ptr),(api,count_ptr?&result_count:NULL,values_ptr?&values:NULL),
    native_tooling_output_refs(&use,result_count,values))
TOOLING_OUTPUT_ARRAY(GetOwnedMonitorInfo,jobject,
    (jvmtiEnv *api,jthread thread,jint *count_ptr,jobject **values_ptr),
    (api,thread,count_ptr,values_ptr),(api,thread,count_ptr?&result_count:NULL,values_ptr?&values:NULL),
    native_tooling_output_refs(&use,result_count,values))
TOOLING_OUTPUT_ARRAY(GetTopThreadGroups,jthreadGroup,
    (jvmtiEnv *api,jint *count_ptr,jthreadGroup **values_ptr),
    (api,count_ptr,values_ptr),(api,count_ptr?&result_count:NULL,values_ptr?&values:NULL),
    native_tooling_output_refs(&use,result_count,values))
TOOLING_OUTPUT_ARRAY(GetClassMethods,jmethodID,
    (jvmtiEnv *api,jclass klass,jint *count_ptr,jmethodID **values_ptr),
    (api,klass,count_ptr,values_ptr),(api,klass,count_ptr?&result_count:NULL,values_ptr?&values:NULL),
    native_tooling_output_free(&use,values))
TOOLING_OUTPUT_ARRAY(GetClassFields,jfieldID,
    (jvmtiEnv *api,jclass klass,jint *count_ptr,jfieldID **values_ptr),
    (api,klass,count_ptr,values_ptr),(api,klass,count_ptr?&result_count:NULL,values_ptr?&values:NULL),
    native_tooling_output_free(&use,values))
TOOLING_OUTPUT_ARRAY(GetImplementedInterfaces,jclass,
    (jvmtiEnv *api,jclass klass,jint *count_ptr,jclass **values_ptr),
    (api,klass,count_ptr,values_ptr),(api,klass,count_ptr?&result_count:NULL,values_ptr?&values:NULL),
    native_tooling_output_refs(&use,result_count,values))
TOOLING_OUTPUT_ARRAY(GetLineNumberTable,jvmtiLineNumberEntry,
    (jvmtiEnv *api,jmethodID method,jint *count_ptr,jvmtiLineNumberEntry **values_ptr),
    (api,method,count_ptr,values_ptr),(api,method,count_ptr?&result_count:NULL,values_ptr?&values:NULL),
    native_tooling_output_free(&use,values))
TOOLING_OUTPUT_ARRAY(GetLocalVariableTable,jvmtiLocalVariableEntry,
    (jvmtiEnv *api,jmethodID method,jint *count_ptr,jvmtiLocalVariableEntry **values_ptr),
    (api,method,count_ptr,values_ptr),(api,method,count_ptr?&result_count:NULL,values_ptr?&values:NULL),
    native_tooling_output_variables(&use,result_count,values))
TOOLING_OUTPUT_ARRAY(GetBytecodes,unsigned char,
    (jvmtiEnv *api,jmethodID method,jint *count_ptr,unsigned char **values_ptr),
    (api,method,count_ptr,values_ptr),(api,method,count_ptr?&result_count:NULL,values_ptr?&values:NULL),
    native_tooling_output_free(&use,values))
TOOLING_OUTPUT_ARRAY(GetLoadedClasses,jclass,
    (jvmtiEnv *api,jint *count_ptr,jclass **values_ptr),
    (api,count_ptr,values_ptr),(api,count_ptr?&result_count:NULL,values_ptr?&values:NULL),
    native_tooling_output_refs(&use,result_count,values))
TOOLING_OUTPUT_ARRAY(GetClassLoaderClasses,jclass,
    (jvmtiEnv *api,jobject loader,jint *count_ptr,jclass **values_ptr),
    (api,loader,count_ptr,values_ptr),(api,loader,count_ptr?&result_count:NULL,values_ptr?&values:NULL),
    native_tooling_output_refs(&use,result_count,values))
TOOLING_OUTPUT_ARRAY(GetExtensionFunctions,jvmtiExtensionFunctionInfo,
    (jvmtiEnv *api,jint *count_ptr,jvmtiExtensionFunctionInfo **values_ptr),
    (api,count_ptr,values_ptr),(api,count_ptr?&result_count:NULL,values_ptr?&values:NULL),
    native_tooling_output_extensions(&use,result_count,values))
TOOLING_OUTPUT_ARRAY(GetExtensionEvents,jvmtiExtensionEventInfo,
    (jvmtiEnv *api,jint *count_ptr,jvmtiExtensionEventInfo **values_ptr),
    (api,count_ptr,values_ptr),(api,count_ptr?&result_count:NULL,values_ptr?&values:NULL),
    native_tooling_output_extension_events(&use,result_count,values))
TOOLING_OUTPUT_ARRAY(GetSystemProperties,char*,
    (jvmtiEnv *api,jint *count_ptr,char ***values_ptr),
    (api,count_ptr,values_ptr),(api,count_ptr?&result_count:NULL,values_ptr?&values:NULL),
    native_tooling_output_strings(&use,result_count,values))
TOOLING_OUTPUT_ARRAY(GetOwnedMonitorStackDepthInfo,jvmtiMonitorStackDepthInfo,
    (jvmtiEnv *api,jthread thread,jint *count_ptr,jvmtiMonitorStackDepthInfo **values_ptr),
    (api,thread,count_ptr,values_ptr),(api,thread,count_ptr?&result_count:NULL,values_ptr?&values:NULL),
    native_tooling_output_monitor_depths(&use,result_count,values))

static jvmtiError JNICALL native_tooling_output_Deallocate(jvmtiEnv *api,unsigned char *memory){
    TOOLING_OUTPUT_BEGIN(1);
    if(use.internal)TOOLING_OUTPUT_RETURN(use.record->original->Deallocate(api,memory));
    error=native_tooling_output_deallocate(&use,memory);
    TOOLING_OUTPUT_RETURN(error);
}

static jvmtiError JNICALL native_tooling_output_CreateRawMonitor(jvmtiEnv *api,const char *name,jrawMonitorID *monitor_ptr){
    TOOLING_OUTPUT_BEGIN(1);
    if(use.internal)TOOLING_OUTPUT_RETURN(use.record->original->CreateRawMonitor(api,name,monitor_ptr));
    jrawMonitorID monitor=NULL;
    error=use.record->original->CreateRawMonitor(api,name,monitor_ptr?&monitor:NULL);
    if(error==JVMTI_ERROR_NONE){
        NativeToolingOutputSpan spans[1];size_t count=0;
        native_tooling_output_span(spans,&count,monitor_ptr,&monitor,sizeof(monitor));
        error=native_tooling_output_commit(&use,spans,count);
        if(error!=JVMTI_ERROR_NONE&&monitor&&use.record->original->DestroyRawMonitor(api,monitor)!=JVMTI_ERROR_NONE)
            InterlockedIncrement(&native_tooling_failures);
    }
    TOOLING_OUTPUT_RETURN(error);
}

static jvmtiError JNICALL native_tooling_output_GetFrameLocation(jvmtiEnv *api,jthread thread,jint depth,
        jmethodID *method_ptr,jlocation *location_ptr){
    TOOLING_OUTPUT_BEGIN(0);
    if(use.internal)TOOLING_OUTPUT_RETURN(use.record->original->GetFrameLocation(api,thread,depth,method_ptr,location_ptr));
    jmethodID method=NULL;jlocation location=0;
    error=use.record->original->GetFrameLocation(api,thread,depth,method_ptr?&method:NULL,location_ptr?&location:NULL);
    if(error==JVMTI_ERROR_NONE){
        NativeToolingOutputSpan spans[2];size_t count=0;
        native_tooling_output_span(spans,&count,method_ptr,&method,sizeof(method));
        native_tooling_output_span(spans,&count,location_ptr,&location,sizeof(location));
        error=native_tooling_output_commit(&use,spans,count);
    }
    TOOLING_OUTPUT_RETURN(error);
}

static jvmtiError JNICALL native_tooling_output_GetMethodLocation(jvmtiEnv *api,jmethodID method,
        jlocation *start_ptr,jlocation *end_ptr){
    TOOLING_OUTPUT_BEGIN(0);
    if(use.internal)TOOLING_OUTPUT_RETURN(use.record->original->GetMethodLocation(api,method,start_ptr,end_ptr));
    jlocation start=0,end=0;
    error=use.record->original->GetMethodLocation(api,method,start_ptr?&start:NULL,end_ptr?&end:NULL);
    if(error==JVMTI_ERROR_NONE){
        NativeToolingOutputSpan spans[2];size_t count=0;
        native_tooling_output_span(spans,&count,start_ptr,&start,sizeof(start));
        native_tooling_output_span(spans,&count,end_ptr,&end,sizeof(end));
        error=native_tooling_output_commit(&use,spans,count);
    }
    TOOLING_OUTPUT_RETURN(error);
}

static jvmtiError JNICALL native_tooling_output_GetClassSignature(jvmtiEnv *api,jclass klass,
        char **signature_ptr,char **generic_ptr){
    TOOLING_OUTPUT_BEGIN(0);
    if(use.internal)TOOLING_OUTPUT_RETURN(use.record->original->GetClassSignature(api,klass,signature_ptr,generic_ptr));
    char *signature=NULL,*generic=NULL;
    error=use.record->original->GetClassSignature(api,klass,signature_ptr?&signature:NULL,generic_ptr?&generic:NULL);
    if(error==JVMTI_ERROR_NONE){
        NativeToolingOutputSpan spans[2];size_t count=0;
        native_tooling_output_span(spans,&count,signature_ptr,&signature,sizeof(signature));
        native_tooling_output_span(spans,&count,generic_ptr,&generic,sizeof(generic));
        error=native_tooling_output_commit(&use,spans,count);
        if(error!=JVMTI_ERROR_NONE){native_tooling_output_free(&use,signature);native_tooling_output_free(&use,generic);}
    }
    TOOLING_OUTPUT_RETURN(error);
}

#define TOOLING_OUTPUT_NAMES(name,parameters,direct,scratch) \
static jvmtiError JNICALL native_tooling_output_##name parameters { \
    TOOLING_OUTPUT_BEGIN(0); \
    if(use.internal)TOOLING_OUTPUT_RETURN(use.record->original->name direct); \
    char *result_name=NULL,*signature=NULL,*generic=NULL; \
    error=use.record->original->name scratch; \
    if(error==JVMTI_ERROR_NONE){ \
        NativeToolingOutputSpan spans[3];size_t count=0; \
        native_tooling_output_span(spans,&count,name_ptr,&result_name,sizeof(result_name)); \
        native_tooling_output_span(spans,&count,signature_ptr,&signature,sizeof(signature)); \
        native_tooling_output_span(spans,&count,generic_ptr,&generic,sizeof(generic)); \
        error=native_tooling_output_commit(&use,spans,count); \
        if(error!=JVMTI_ERROR_NONE){ \
            native_tooling_output_free(&use,result_name);native_tooling_output_free(&use,signature);native_tooling_output_free(&use,generic); \
        } \
    } \
    TOOLING_OUTPUT_RETURN(error); \
}
TOOLING_OUTPUT_NAMES(GetFieldName,
    (jvmtiEnv *api,jclass klass,jfieldID field,char **name_ptr,char **signature_ptr,char **generic_ptr),
    (api,klass,field,name_ptr,signature_ptr,generic_ptr),
    (api,klass,field,name_ptr?&result_name:NULL,signature_ptr?&signature:NULL,generic_ptr?&generic:NULL))
TOOLING_OUTPUT_NAMES(GetMethodName,
    (jvmtiEnv *api,jmethodID method,char **name_ptr,char **signature_ptr,char **generic_ptr),
    (api,method,name_ptr,signature_ptr,generic_ptr),
    (api,method,name_ptr?&result_name:NULL,signature_ptr?&signature:NULL,generic_ptr?&generic:NULL))

static jvmtiError JNICALL native_tooling_output_GetThreadGroupChildren(jvmtiEnv *api,jthreadGroup group,
        jint *thread_count_ptr,jthread **threads_ptr,jint *group_count_ptr,jthreadGroup **groups_ptr){
    TOOLING_OUTPUT_BEGIN(0);
    if(use.internal)TOOLING_OUTPUT_RETURN(use.record->original->GetThreadGroupChildren(api,group,thread_count_ptr,threads_ptr,group_count_ptr,groups_ptr));
    jint thread_count=0,group_count=0;jthread *threads=NULL;jthreadGroup *groups=NULL;
    error=use.record->original->GetThreadGroupChildren(api,group,thread_count_ptr?&thread_count:NULL,
        threads_ptr?&threads:NULL,group_count_ptr?&group_count:NULL,groups_ptr?&groups:NULL);
    if(error==JVMTI_ERROR_NONE){
        NativeToolingOutputSpan spans[4];size_t count=0;
        native_tooling_output_span(spans,&count,thread_count_ptr,&thread_count,sizeof(thread_count));
        native_tooling_output_span(spans,&count,threads_ptr,&threads,sizeof(threads));
        native_tooling_output_span(spans,&count,group_count_ptr,&group_count,sizeof(group_count));
        native_tooling_output_span(spans,&count,groups_ptr,&groups,sizeof(groups));
        error=native_tooling_output_commit(&use,spans,count);
        if(error!=JVMTI_ERROR_NONE){
            native_tooling_output_refs(&use,thread_count,threads);native_tooling_output_refs(&use,group_count,groups);
        }
    }
    TOOLING_OUTPUT_RETURN(error);
}

static jvmtiError JNICALL native_tooling_output_GetAllStackTraces(jvmtiEnv *api,jint maximum,
        jvmtiStackInfo **stacks_ptr,jint *count_ptr){
    TOOLING_OUTPUT_BEGIN(0);
    if(use.internal)TOOLING_OUTPUT_RETURN(use.record->original->GetAllStackTraces(api,maximum,stacks_ptr,count_ptr));
    jvmtiStackInfo *stacks=NULL;jint result_count=0;
    error=use.record->original->GetAllStackTraces(api,maximum,stacks_ptr?&stacks:NULL,count_ptr?&result_count:NULL);
    if(error==JVMTI_ERROR_NONE){
        NativeToolingOutputSpan spans[2];size_t count=0;
        native_tooling_output_span(spans,&count,stacks_ptr,&stacks,sizeof(stacks));
        native_tooling_output_span(spans,&count,count_ptr,&result_count,sizeof(result_count));
        error=native_tooling_output_commit(&use,spans,count);
        if(error!=JVMTI_ERROR_NONE)native_tooling_output_stacks(&use,result_count,stacks);
    }
    TOOLING_OUTPUT_RETURN(error);
}

static jvmtiError JNICALL native_tooling_output_GetThreadListStackTraces(jvmtiEnv *api,jint thread_count,
        const jthread *threads,jint maximum,jvmtiStackInfo **stacks_ptr){
    TOOLING_OUTPUT_BEGIN(0);
    if(use.internal)TOOLING_OUTPUT_RETURN(use.record->original->GetThreadListStackTraces(api,thread_count,threads,maximum,stacks_ptr));
    jvmtiStackInfo *stacks=NULL;
    error=use.record->original->GetThreadListStackTraces(api,thread_count,threads,maximum,stacks_ptr?&stacks:NULL);
    if(error==JVMTI_ERROR_NONE){
        NativeToolingOutputSpan spans[1];size_t count=0;
        native_tooling_output_span(spans,&count,stacks_ptr,&stacks,sizeof(stacks));
        error=native_tooling_output_commit(&use,spans,count);
        if(error!=JVMTI_ERROR_NONE)native_tooling_output_stacks(&use,thread_count,stacks);
    }
    TOOLING_OUTPUT_RETURN(error);
}

static jvmtiError JNICALL native_tooling_output_GetStackTrace(jvmtiEnv *api,jthread thread,jint start_depth,
        jint maximum,jvmtiFrameInfo *frame_buffer,jint *count_ptr){
    TOOLING_OUTPUT_BEGIN(0);
    if(use.internal)TOOLING_OUTPUT_RETURN(use.record->original->GetStackTrace(api,thread,start_depth,maximum,frame_buffer,count_ptr));
    jvmtiFrameInfo dummy={0},*frames=NULL;jint result_count=0;
    if(frame_buffer&&count_ptr&&maximum>0){
        if((SIZE_T)maximum>SIZE_MAX/sizeof(*frames))TOOLING_OUTPUT_RETURN(JVMTI_ERROR_OUT_OF_MEMORY);
        frames=(jvmtiFrameInfo*)malloc((SIZE_T)maximum*sizeof(*frames));
        if(!frames)TOOLING_OUTPUT_RETURN(JVMTI_ERROR_OUT_OF_MEMORY);
    }
    /* A non-NULL zero/invalid-size buffer remains non-NULL for the real VM's
     * argument checks.  No caller output is ever used as scratch. */
    error=use.record->original->GetStackTrace(api,thread,start_depth,maximum,
        frame_buffer?(frames?frames:&dummy):NULL,count_ptr?&result_count:NULL);
    if(error==JVMTI_ERROR_NONE){
        if(result_count<0||maximum<0||result_count>maximum)error=JVMTI_ERROR_INTERNAL;
        else{
            NativeToolingOutputSpan spans[2];size_t count=0;
            native_tooling_output_span(spans,&count,frame_buffer,frames,(SIZE_T)result_count*sizeof(*frames));
            native_tooling_output_span(spans,&count,count_ptr,&result_count,sizeof(result_count));
            error=native_tooling_output_commit(&use,spans,count);
        }
    }
    free(frames);
    TOOLING_OUTPUT_RETURN(error);
}

static jvmtiError JNICALL native_tooling_output_GetObjectsWithTags(jvmtiEnv *api,jint tag_count,const jlong *tags,
        jint *count_ptr,jobject **objects_ptr,jlong **tags_ptr){
    TOOLING_OUTPUT_BEGIN(0);
    if(use.internal)TOOLING_OUTPUT_RETURN(use.record->original->GetObjectsWithTags(api,tag_count,tags,count_ptr,objects_ptr,tags_ptr));
    jint result_count=0;jobject *objects=NULL;jlong *result_tags=NULL;
    error=use.record->original->GetObjectsWithTags(api,tag_count,tags,count_ptr?&result_count:NULL,
        objects_ptr?&objects:NULL,tags_ptr?&result_tags:NULL);
    if(error==JVMTI_ERROR_NONE){
        NativeToolingOutputSpan spans[3];size_t count=0;
        native_tooling_output_span(spans,&count,count_ptr,&result_count,sizeof(result_count));
        native_tooling_output_span(spans,&count,objects_ptr,&objects,sizeof(objects));
        native_tooling_output_span(spans,&count,tags_ptr,&result_tags,sizeof(result_tags));
        error=native_tooling_output_commit(&use,spans,count);
        if(error!=JVMTI_ERROR_NONE){
            native_tooling_output_refs(&use,result_count,objects);native_tooling_output_free(&use,result_tags);
        }
    }
    TOOLING_OUTPUT_RETURN(error);
}

static jvmtiError JNICALL native_tooling_output_GetClassVersionNumbers(jvmtiEnv *api,jclass klass,
        jint *minor_ptr,jint *major_ptr){
    TOOLING_OUTPUT_BEGIN(0);
    if(use.internal)TOOLING_OUTPUT_RETURN(use.record->original->GetClassVersionNumbers(api,klass,minor_ptr,major_ptr));
    jint minor=0,major=0;
    error=use.record->original->GetClassVersionNumbers(api,klass,minor_ptr?&minor:NULL,major_ptr?&major:NULL);
    if(error==JVMTI_ERROR_NONE){
        NativeToolingOutputSpan spans[2];size_t count=0;
        native_tooling_output_span(spans,&count,minor_ptr,&minor,sizeof(minor));
        native_tooling_output_span(spans,&count,major_ptr,&major,sizeof(major));
        error=native_tooling_output_commit(&use,spans,count);
    }
    TOOLING_OUTPUT_RETURN(error);
}

static jvmtiError JNICALL native_tooling_output_GetConstantPool(jvmtiEnv *api,jclass klass,
        jint *pool_count_ptr,jint *byte_count_ptr,unsigned char **bytes_ptr){
    TOOLING_OUTPUT_BEGIN(0);
    if(use.internal)TOOLING_OUTPUT_RETURN(use.record->original->GetConstantPool(api,klass,pool_count_ptr,byte_count_ptr,bytes_ptr));
    jint pool_count=0,byte_count=0;unsigned char *bytes=NULL;
    error=use.record->original->GetConstantPool(api,klass,pool_count_ptr?&pool_count:NULL,
        byte_count_ptr?&byte_count:NULL,bytes_ptr?&bytes:NULL);
    if(error==JVMTI_ERROR_NONE){
        NativeToolingOutputSpan spans[3];size_t count=0;
        native_tooling_output_span(spans,&count,pool_count_ptr,&pool_count,sizeof(pool_count));
        native_tooling_output_span(spans,&count,byte_count_ptr,&byte_count,sizeof(byte_count));
        native_tooling_output_span(spans,&count,bytes_ptr,&bytes,sizeof(bytes));
        error=native_tooling_output_commit(&use,spans,count);
        if(error!=JVMTI_ERROR_NONE)native_tooling_output_free(&use,bytes);
    }
    TOOLING_OUTPUT_RETURN(error);
}

static void native_tooling_outputs_install(struct jvmtiInterface_1_ *shadow){
#define TOOLING_OUTPUT_INSTALL(name) shadow->name=native_tooling_output_##name
    TOOLING_OUTPUT_INSTALL(GetAllModules);
    TOOLING_OUTPUT_INSTALL(GetAllThreads);
    TOOLING_OUTPUT_INSTALL(GetThreadInfo);
    TOOLING_OUTPUT_INSTALL(GetOwnedMonitorInfo);
    TOOLING_OUTPUT_INSTALL(GetCurrentContendedMonitor);
    TOOLING_OUTPUT_INSTALL(GetTopThreadGroups);
    TOOLING_OUTPUT_INSTALL(GetThreadGroupInfo);
    TOOLING_OUTPUT_INSTALL(GetThreadGroupChildren);
    TOOLING_OUTPUT_INSTALL(GetFrameCount);
    TOOLING_OUTPUT_INSTALL(GetThreadState);
    TOOLING_OUTPUT_INSTALL(GetCurrentThread);
    TOOLING_OUTPUT_INSTALL(GetFrameLocation);
    TOOLING_OUTPUT_INSTALL(GetLocalObject);
    TOOLING_OUTPUT_INSTALL(GetLocalInt);
    TOOLING_OUTPUT_INSTALL(GetLocalLong);
    TOOLING_OUTPUT_INSTALL(GetLocalFloat);
    TOOLING_OUTPUT_INSTALL(GetLocalDouble);
    TOOLING_OUTPUT_INSTALL(CreateRawMonitor);
    TOOLING_OUTPUT_INSTALL(GetNamedModule);
    TOOLING_OUTPUT_INSTALL(IsModifiableClass);
    TOOLING_OUTPUT_INSTALL(Allocate);
    TOOLING_OUTPUT_INSTALL(Deallocate);
    TOOLING_OUTPUT_INSTALL(GetClassSignature);
    TOOLING_OUTPUT_INSTALL(GetClassStatus);
    TOOLING_OUTPUT_INSTALL(GetSourceFileName);
    TOOLING_OUTPUT_INSTALL(GetClassModifiers);
    TOOLING_OUTPUT_INSTALL(GetClassMethods);
    TOOLING_OUTPUT_INSTALL(GetClassFields);
    TOOLING_OUTPUT_INSTALL(GetImplementedInterfaces);
    TOOLING_OUTPUT_INSTALL(IsInterface);
    TOOLING_OUTPUT_INSTALL(IsArrayClass);
    TOOLING_OUTPUT_INSTALL(GetClassLoader);
    TOOLING_OUTPUT_INSTALL(GetObjectHashCode);
    TOOLING_OUTPUT_INSTALL(GetObjectMonitorUsage);
    TOOLING_OUTPUT_INSTALL(GetFieldName);
    TOOLING_OUTPUT_INSTALL(GetFieldDeclaringClass);
    TOOLING_OUTPUT_INSTALL(GetFieldModifiers);
    TOOLING_OUTPUT_INSTALL(IsFieldSynthetic);
    TOOLING_OUTPUT_INSTALL(GetMethodName);
    TOOLING_OUTPUT_INSTALL(GetMethodDeclaringClass);
    TOOLING_OUTPUT_INSTALL(GetMethodModifiers);
    TOOLING_OUTPUT_INSTALL(GetMaxLocals);
    TOOLING_OUTPUT_INSTALL(GetArgumentsSize);
    TOOLING_OUTPUT_INSTALL(GetLineNumberTable);
    TOOLING_OUTPUT_INSTALL(GetMethodLocation);
    TOOLING_OUTPUT_INSTALL(GetLocalVariableTable);
    TOOLING_OUTPUT_INSTALL(GetBytecodes);
    TOOLING_OUTPUT_INSTALL(IsMethodNative);
    TOOLING_OUTPUT_INSTALL(IsMethodSynthetic);
    TOOLING_OUTPUT_INSTALL(GetLoadedClasses);
    TOOLING_OUTPUT_INSTALL(GetClassLoaderClasses);
    TOOLING_OUTPUT_INSTALL(GetVersionNumber);
    TOOLING_OUTPUT_INSTALL(GetCapabilities);
    TOOLING_OUTPUT_INSTALL(GetSourceDebugExtension);
    TOOLING_OUTPUT_INSTALL(IsMethodObsolete);
    TOOLING_OUTPUT_INSTALL(IsModifiableModule);
    TOOLING_OUTPUT_INSTALL(GetAllStackTraces);
    TOOLING_OUTPUT_INSTALL(GetThreadListStackTraces);
    TOOLING_OUTPUT_INSTALL(GetThreadLocalStorage);
    TOOLING_OUTPUT_INSTALL(GetStackTrace);
    TOOLING_OUTPUT_INSTALL(GetTag);
    TOOLING_OUTPUT_INSTALL(GetObjectsWithTags);
    TOOLING_OUTPUT_INSTALL(GetJNIFunctionTable);
    TOOLING_OUTPUT_INSTALL(GetExtensionFunctions);
    TOOLING_OUTPUT_INSTALL(GetExtensionEvents);
    TOOLING_OUTPUT_INSTALL(GetErrorName);
    TOOLING_OUTPUT_INSTALL(GetJLocationFormat);
    TOOLING_OUTPUT_INSTALL(GetSystemProperties);
    TOOLING_OUTPUT_INSTALL(GetSystemProperty);
    TOOLING_OUTPUT_INSTALL(GetPhase);
    TOOLING_OUTPUT_INSTALL(GetCurrentThreadCpuTimerInfo);
    TOOLING_OUTPUT_INSTALL(GetCurrentThreadCpuTime);
    TOOLING_OUTPUT_INSTALL(GetThreadCpuTimerInfo);
    TOOLING_OUTPUT_INSTALL(GetThreadCpuTime);
    TOOLING_OUTPUT_INSTALL(GetTimerInfo);
    TOOLING_OUTPUT_INSTALL(GetTime);
    TOOLING_OUTPUT_INSTALL(GetPotentialCapabilities);
    TOOLING_OUTPUT_INSTALL(GetAvailableProcessors);
    TOOLING_OUTPUT_INSTALL(GetClassVersionNumbers);
    TOOLING_OUTPUT_INSTALL(GetConstantPool);
    TOOLING_OUTPUT_INSTALL(GetEnvironmentLocalStorage);
    TOOLING_OUTPUT_INSTALL(GetOwnedMonitorStackDepthInfo);
    TOOLING_OUTPUT_INSTALL(GetObjectSize);
    TOOLING_OUTPUT_INSTALL(GetLocalInstance);
#undef TOOLING_OUTPUT_INSTALL
}

#undef TOOLING_OUTPUT_NAMES
#undef TOOLING_OUTPUT_ARRAY
#undef TOOLING_OUTPUT_VALUE
#undef TOOLING_OUTPUT_RETURN
#undef TOOLING_OUTPUT_BEGIN
