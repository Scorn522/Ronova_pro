#define WIN32_LEAN_AND_MEAN
#define _WIN32_WINNT 0x0602
#include <windows.h>
#include <jni.h>
#include <atomic>
#include <string>
#include <vector>
#include <cstring>
#include <cstdio>
#include "storage_transaction.hpp"

extern "C" JNIEXPORT jint JNICALL Java_dev_ronova_pro_StorageNative_abi0(JNIEnv*,jclass) { return 2; }

// Borrow the live Java stream handle: no path-open and no ownership transfer.
extern "C" JNIEXPORT jstring JNICALL
Java_dev_ronova_pro_StorageNative_identity0(JNIEnv* env,jclass,jobject descriptor,jstring path) try {
    if(!descriptor||!path)return nullptr;
    if(!recovery_storage::exact(env,descriptor,"java/io/FileDescriptor"))return nullptr;
    auto type=env->GetObjectClass(descriptor);
    auto field=env->GetFieldID(type,"handle","J");env->DeleteLocalRef(type);
    if(!field)return nullptr;
    auto handle=reinterpret_cast<HANDLE>(env->GetLongField(descriptor,field));
    if(!handle||handle==INVALID_HANDLE_VALUE)return nullptr;
    jsize count=env->GetStringLength(path);
    if(count<3||count>4096)return nullptr;
    const jchar* chars=env->GetStringChars(path,nullptr);if(!chars)return nullptr;
    std::wstring requested;requested.reserve(count);bool valid=true;
    for(jsize i=0;i<count;i++) {
        wchar_t c=chars[i]==L'/'?L'\\':chars[i];
        if(!c||(c==L':'&&i!=1))valid=false;
        requested+=c;
    }
    env->ReleaseStringChars(path,chars);
    if(!valid||requested[1]!=L':'||requested[2]!=L'\\')return nullptr;
    std::string identity;
    if(!recovery_storage::identity(handle,requested,identity))return nullptr;
    return env->NewStringUTF(identity.c_str());
} catch(...) { return nullptr; }
