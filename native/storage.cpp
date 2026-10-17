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
#include "include/ronova_object_state.hpp"
#include "storage_transaction.hpp"

extern "C" JNIEXPORT jint JNICALL Java_dev_ronova_pro_StorageNative_abi0(JNIEnv*,jclass) { return 3; }

namespace {
/* Only the actual decision members are restorable fields. The STL subobjects
 * retain their own constructors, growth and destructors; public data()/size()
 * can describe a stable external buffer without copying a string layout. */
struct IdentityState {
    std::wstring requested;
    std::string identity;
    struct Decisions {bool valid=true,identified=false;} decisions;
};
struct IdentityOwner {
    IdentityState *value=nullptr;
    ronova::object_state observed;
    ronova::scalar_field<bool> valid,identified;
    ronova::buffer_field<const wchar_t> requestedBuffer;
    ronova::buffer_field<const char> identityBuffer;
    uint64_t requestedObject=0,identityObject=0;
    template<class String> static bool inline_buffer(String& text) noexcept {
        const auto address=reinterpret_cast<uintptr_t>(text.data());
        const auto object=reinterpret_cast<uintptr_t>(&text);
        return address>=object&&address-object<sizeof(text);
    }
    template<class T> bool close_buffer(ronova::buffer_field<T>& buffer) noexcept {
        return !buffer.receipt()||observed.release(buffer);
    }
    bool close_requested() noexcept {return close_buffer(requestedBuffer);}
    bool close_identity() noexcept {return close_buffer(identityBuffer);}
    void bind_requested() noexcept {
        if(observed.typed()&&!value->requested.empty()&&!inline_buffer(value->requested))
            requestedBuffer=observed.borrowed_buffer(std::span<const wchar_t>(value->requested.data(),value->requested.size()+1));
    }
    void bind_identity() noexcept {
        if(observed.typed()&&!value->identity.empty()&&!inline_buffer(value->identity))
            identityBuffer=observed.borrowed_buffer(std::span<const char>(value->identity.data(),value->identity.size()+1));
    }
    IdentityOwner() {
        // An actual imported CRT allocation gives Host its real allocation
        // generation. Placement construction itself grants no storage rights.
        void *storage=std::malloc(sizeof(IdentityState));
        if(!storage)throw std::bad_alloc();
        try {value=::new(storage) IdentityState;}
        catch(...) {std::free(storage);throw;}
        observed.constructed(value,sizeof(*value));
        valid=observed.scalar(value->decisions.valid);
        identified=observed.scalar(value->decisions.identified);
        requestedObject=observed.borrowed_object(&value->requested,sizeof(value->requested));
        identityObject=observed.borrowed_object(&value->identity,sizeof(value->identity));
    }
    ~IdentityOwner() noexcept {
        auto destruction=observed.destroying();
        // A real outstanding lease retains the still-constructed object. It
        // cannot be silently freed under the SDK's active borrower/writer.
        if(observed.covered()&&!destruction.admitted())return;
        // Closing object admissions precedes ending the public data() spans;
        // the original STL destructors then release their own allocations.
        if(!close_requested()||!close_identity())return;
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
    IdentityOwner state;auto& value=*state.value;
    if(!state.close_requested())return nullptr;
    value.requested.reserve(count);
    const jchar* chars=env->GetStringChars(path,nullptr);if(!chars)return nullptr;
    bool admitted=true;
    {
        for(jsize i=0;i<count;i++) {
            wchar_t c=chars[i]==L'/'?L'\\':chars[i];
            if((!c||(c==L':'&&i!=1))&&!state.observed.set(state.valid,false)){admitted=false;break;}
            value.requested+=c;
        }
    }
    env->ReleaseStringChars(path,chars);
    state.bind_requested();
    if(!admitted)return nullptr;
    {
        auto valid=state.observed.borrowing(state.valid.receipt());
        auto requested=state.observed.borrowing(state.requestedObject);
        auto buffer=state.observed.borrowing(state.requestedBuffer.receipt());
        if(state.observed.covered()&&(!valid.observed()||!requested.observed()))return nullptr;
        if(state.requestedBuffer.receipt()&&!buffer.observed())return nullptr;
        if(!value.decisions.valid||value.requested[1]!=L':'||value.requested[2]!=L'\\')return nullptr;
    }
    if(!state.close_identity())return nullptr;
    bool identified;
    {
        auto requested=state.observed.borrowing(state.requestedObject);
        auto buffer=state.observed.borrowing(state.requestedBuffer.receipt());
        if(state.observed.covered()&&!requested.observed())return nullptr;
        if(state.requestedBuffer.receipt()&&!buffer.observed())return nullptr;
        identified=recovery_storage::identity(handle,value.requested,value.identity);
    }
    state.bind_identity();
    if(!state.observed.set(state.identified,identified))return nullptr;
    auto decision=state.observed.borrowing(state.identified.receipt());
    auto identity=state.observed.borrowing(state.identityObject);
    auto buffer=state.observed.borrowing(state.identityBuffer.receipt());
    if(state.observed.covered()&&(!decision.observed()||!identity.observed()))return nullptr;
    if(state.identityBuffer.receipt()&&!buffer.observed())return nullptr;
    if(!value.decisions.identified)return nullptr;
    return env->NewStringUTF(value.identity.c_str());
} catch(...) { return nullptr; }
