#include <jni.h>
// Preserve Object.clone itself, including Cloneable checks; never substitute a Java ancestor body.
JNIEXPORT jint JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_abi(JNIEnv *env, jclass type) {
    (void)env; (void)type; return 1;
}
JNIEXPORT jobject JNICALL Java_dev_ronova_pro_bootstrap_NativeControl_clone0(JNIEnv *env, jclass type, jobject source) {
    (void)type;
    jclass bridge=(*env)->FindClass(env,"dev/ronova/pro/bootstrap/TaskBridge");
    if(bridge==NULL)return NULL;
    jmethodID gate=(*env)->GetStaticMethodID(env,bridge,"taskEffectAllowed","(Ljava/lang/Object;Ljava/lang/String;)Z");
    if(gate==NULL)return NULL;
    jstring operation=(*env)->NewStringUTF(env,"native-clone");
    if(operation==NULL)return NULL;
    jboolean allowed=(*env)->CallStaticBooleanMethod(env,bridge,gate,source,operation);
    if((*env)->ExceptionCheck(env))return NULL;
    if(!allowed) {
        jclass failure=(*env)->FindClass(env,"java/lang/CloneNotSupportedException");
        if(failure!=NULL)(*env)->ThrowNew(env,failure,"RONOVA_TERMINAL_CLONE_REFUSED");
        return NULL;
    }
    if(source==NULL) {
        jclass failure=(*env)->FindClass(env,"java/lang/NullPointerException");
        if(failure!=NULL)(*env)->ThrowNew(env,failure,"clone source");
        return NULL;
    }
    jclass object=(*env)->FindClass(env,"java/lang/Object");
    if(object==NULL)return NULL;
    jmethodID clone=(*env)->GetMethodID(env,object,"clone","()Ljava/lang/Object;");
    if(clone==NULL)return NULL;
    return (*env)->CallNonvirtualObjectMethod(env,source,object,clone);
}
