/* Install boundaries around the implementation currently published by this VM.
 * A seed carries the actual Method identity, never a guessed original entry. */
static jlong JNICALL native_binding_rebinding(JNIEnv *env,jobject receiver){
    (void)receiver;native_refuse(env,"NATIVE_REBIND_NOT_PUBLISHED");return 0;
}
static int native_binding_current(jmethodID method,void *target,unsigned kind){
    int found=0;AcquireSRWLockShared(&native_records);
    for(Binding *binding=native_bindings;binding;binding=binding->next)
        if(binding->method==method&&binding->entry==target&&InterlockedCompareExchange(&binding->current,0,0)
                &&(!kind||kind==1&&binding->fileOperation||kind==2&&binding->threadOperation||kind==3&&binding->unsafeOperation)){found=1;break;}
    ReleaseSRWLockShared(&native_records);return found;
}
static int native_binding_resolved(jmethodID method,void *target){
    if(!target)return 0;if(native_binding_current(method,target,0))return 1;
    HMODULE image=native_image(target);
    // Thread's registered native methods live in the VM image itself. Accept
    // only a matching actual VM export, not its unresolved native-call stub.
    return image&&(image!=code_vm_layout.image||native_thread_vm_target(method,target)||native_unsafe_vm_target(method,target));
}
static int native_binding_seed_target(JNIEnv *env,NativeThread *state,jclass declaring,jmethodID method,void **target,void **replacement){
    NativeBindingSeed *seed=state->bindingSeed;
    if(!seed||seed->method!=method||*target!=(void*)native_binding_rebinding)return 0;
    void *current=NULL;uintptr_t version[3]={0};
    if(!native_original.IsSameObject(env,declaring,seed->declaring)||!code_vm_native_binding(env,method,&current,version)||!current){
        /* The callback supplies no readable current implementation. Replaying
         * the seed's earlier address would publish an unobserved old target. */
        *replacement=(void*)native_rejected;return 1;
    }
    if(memcmp(version,seed->version,sizeof(version))){*replacement=current;return 1;}
    if(!native_binding_resolved(method,current)){*replacement=current;return 1;}
    seed->captured=1;*target=current;*replacement=current;return 0;
}
static int native_binding_pending(jclass type,const char *name,const char *signature,unsigned kind,const char *reason){
    if(kind==1){
        char *declaring=NULL;
        (*native_ti)->GetClassSignature(native_ti,type,&declaring,NULL);
        fprintf(stderr,"RONOVA_NATIVE_FILE_BINDING_PENDING:%s#%s%s:%s\n",declaring?declaring:"?",name,signature,reason);
        if(declaring)(*native_ti)->Deallocate(native_ti,(unsigned char*)declaring);
    }
    return 1;
}
static int native_binding_seed_one(JNIEnv *env,NativeThread *state,jclass type,jmethodID method,const char *name,const char *signature,unsigned kind){
    void *current=NULL;uintptr_t version[3]={0};if(!code_vm_native_binding(env,method,&current,version))return native_binding_pending(type,name,signature,kind,"METHOD_LAYOUT_UNAVAILABLE");
    if(native_binding_current(method,current,kind))return 0;
    if(!native_binding_resolved(method,current))return native_binding_pending(type,name,signature,kind,current?"UNRESOLVED_ENTRY":"UNBOUND_ENTRY");
    NativeBindingSeed seed={.method=method,.declaring=type,.previous=state->bindingSeed};memcpy(seed.version,version,sizeof(version));state->bindingSeed=&seed;
    JNINativeMethod registration={(char*)name,(char*)signature,(void*)native_binding_rebinding};
    jint installed=native_original.RegisterNatives(env,type,&registration,1);state->bindingSeed=seed.previous;
    if(installed!=JNI_OK)return native_binding_pending(type,name,signature,kind,"REGISTER_FAILED");
    if(!seed.captured)return native_binding_pending(type,name,signature,kind,"BIND_CALLBACK_UNOBSERVED");
    if(native_original.ExceptionCheck(env))return native_binding_pending(type,name,signature,kind,"BIND_CALLBACK_EXCEPTION");
    void *published=NULL;uintptr_t publishedVersion[3]={0};
    if(!code_vm_native_binding(env,method,&published,publishedVersion))return native_binding_pending(type,name,signature,kind,"PUBLISHED_LAYOUT_UNAVAILABLE");
    if(memcmp(version,publishedVersion,sizeof(version)))return native_binding_pending(type,name,signature,kind,"DECLARATION_VERSION_CHANGED");
    return native_binding_current(method,published,kind)?0:native_binding_pending(type,name,signature,kind,"BOUND_ENTRY_NOT_PUBLISHED");
}
