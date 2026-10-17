#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#define _WIN32_WINNT 0x0602
#include <windows.h>
#include <jni.h>
#include <atomic>
#include <string>
#include <vector>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <new>
#include <type_traits>
#include "include/ronova_object_state.hpp"
#include "storage_transaction.hpp"

extern "C" JNIEXPORT jint JNICALL Java_dev_ronova_pro_StorageNative_abi0(JNIEnv*,jclass) { return 3; }

namespace {
/* This real storage consumer exposes only the two ordinary decision fields.
 * The STL subobjects retain their actual constructors/destructors and remain
 * borrowed object representations; no string layout or ownership is copied. */
struct IdentityState {
    std::wstring requested;
    std::string identity;
    struct Decisions {bool valid=true,identified=false;} decisions;
};
static_assert(std::is_trivially_copyable_v<IdentityState::Decisions>);
struct IdentityOwner {
    IdentityState *value=nullptr;
    ronova::object_state observed;
    uint64_t decisions=0;
    IdentityOwner() {
        // An actual imported CRT allocation gives Host its real allocation
        // generation. Placement construction itself grants no storage rights.
        void *storage=std::malloc(sizeof(IdentityState));
        if(!storage)throw std::bad_alloc();
        try {value=::new(storage) IdentityState;}
        catch(...) {std::free(storage);throw;}
        if(observed.constructed(value,sizeof(*value))) {
            observed.borrowed_object(&value->requested,sizeof(value->requested));
            observed.borrowed_object(&value->identity,sizeof(value->identity));
            decisions=observed.bytes(&value->decisions,sizeof(value->decisions));
        }
    }
    ~IdentityOwner() noexcept {
        auto destruction=observed.destroying();
        // A real outstanding lease retains the still-constructed object. It
        // cannot be silently freed under the SDK's active borrower/writer.
        if(observed.covered()&&!destruction.admitted())return;
        value->~IdentityState();
        if(observed.covered()&&!destruction.complete())return;
        std::free(value);
    }
};
}

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
    IdentityOwner state;auto& value=*state.value;
    {
        auto write=state.observed.writing(state.decisions);
        value.requested.reserve(count);
        for(jsize i=0;i<count;i++) {
            wchar_t c=chars[i]==L'/'?L'\\':chars[i];
            if(!c||(c==L':'&&i!=1))value.decisions.valid=false;
            value.requested+=c;
        }
        write.complete();
    }
    env->ReleaseStringChars(path,chars);
    if(!value.decisions.valid||value.requested[1]!=L':'||value.requested[2]!=L'\\')return nullptr;
    {
        auto write=state.observed.writing(state.decisions);
        value.decisions.identified=recovery_storage::identity(handle,value.requested,value.identity);
        write.complete();
    }
    if(!value.decisions.identified)return nullptr;
    return env->NewStringUTF(value.identity.c_str());
} catch(...) { return nullptr; }
