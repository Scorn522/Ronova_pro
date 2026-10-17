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
#include "include/ronova_allocator_state.hpp"
#include "include/ronova_object_state.hpp"
#include "storage_transaction.hpp"

extern "C" JNIEXPORT jint JNICALL Java_dev_ronova_pro_StorageNative_abi0(JNIEnv*,jclass) { return 3; }

namespace {
using IdentityPath=std::basic_string<wchar_t,std::char_traits<wchar_t>,ronova::allocator_adapter<wchar_t>>;
using IdentityText=std::basic_string<char,std::char_traits<char>,ronova::allocator_adapter<char>>;
/* The private consumer exposes actual decision members and public string
 * operations. The SDK never copies an STL representation to restore it. */
struct IdentityState {
    IdentityPath requested;
    IdentityText identity;
    struct Decisions {bool valid=true,identified=false;} decisions;
    explicit IdentityState(ronova::allocator_state& allocations)
            :requested(ronova::allocator_adapter<wchar_t>(allocations)),identity(ronova::allocator_adapter<char>(allocations)){}
};
struct IdentityOwner {
    // The real std::allocator adapter outlives both strings, their temporary
    // copies in identity(), and every original deallocation they perform.
    ronova::allocator_state allocations;
    IdentityState *value=nullptr;
    ronova::object_state observed;
    ronova::scalar_field<bool> valid,identified;
    ronova::sequence_field<IdentityPath> requested;
    ronova::sequence_field<IdentityText> identity;
    IdentityOwner() {
        // An actual imported CRT allocation gives Host its real allocation
        // generation. Placement construction itself grants no storage rights.
        void *storage=std::malloc(sizeof(IdentityState));
        if(!storage)throw std::bad_alloc();
        try {value=::new(storage) IdentityState(allocations);}
        catch(...) {std::free(storage);throw;}
        observed.constructed(value,sizeof(*value));
        valid=observed.scalar(value->decisions.valid);
        identified=observed.scalar(value->decisions.identified);
        requested=observed.sequence(value->requested);
        identity=observed.sequence(value->identity);
    }
    ~IdentityOwner() noexcept {
        auto destruction=observed.destroying();
        // A real outstanding lease retains the still-constructed object. It
        // cannot be silently freed under the SDK's active borrower/writer.
        if(observed.covered()&&!destruction.admitted())return;
        // Close public sequence relationships before the original STL
        // destructors release their own allocations.
        if(!observed.release(requested)||!observed.release(identity))return;
        value->~IdentityState();
        if(!allocations.close())return;
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
    if(!state.observed.mutate(state.requested,[&]{value.requested.reserve(count);}))return nullptr;
    bool admitted=false,valid=true;
    {
        const jchar* chars=env->GetStringChars(path,nullptr);if(!chars)return nullptr;
        struct ReleaseChars {JNIEnv *env;jstring text;const jchar *chars;
            ~ReleaseChars(){env->ReleaseStringChars(text,chars);}} release{env,path,chars};
        admitted=state.observed.mutate(state.requested,[&]{
            for(jsize i=0;i<count;i++) {
                wchar_t c=chars[i]==L'/'?L'\\':chars[i];
                if(!c||(c==L':'&&i!=1))valid=false;
                value.requested+=c;
            }
        });
    }
    if(!admitted||(!valid&&!state.observed.set(state.valid,false)))return nullptr;
    {
        auto valid=state.observed.borrowing(state.valid.receipt());
        auto requested=state.observed.borrowing(state.requested.receipt());
        auto view=state.observed.view(state.requested);
        auto buffer=state.observed.borrowing(view.receipt());
        if(state.observed.covered()&&(!valid.observed()||!requested.observed()))return nullptr;
        if(view.receipt()&&!buffer.observed())return nullptr;
        if(!value.decisions.valid||value.requested[1]!=L':'||value.requested[2]!=L'\\')return nullptr;
    }
    bool identified=false,requestedRead=false;
    if(!state.observed.mutate(state.identity,[&]{
        auto requested=state.observed.borrowing(state.requested.receipt());
        auto view=state.observed.view(state.requested);
        auto buffer=state.observed.borrowing(view.receipt());
        requestedRead=!state.observed.covered()||(requested.observed()&&view.receipt()&&buffer.observed());
        if(!requestedRead)return;
        identified=recovery_storage::identity(handle,value.requested,value.identity);
    })||!requestedRead)return nullptr;
    if(!state.observed.set(state.identified,identified))return nullptr;
    auto decision=state.observed.borrowing(state.identified.receipt());
    auto identity=state.observed.borrowing(state.identity.receipt());
    auto view=state.observed.view(state.identity);
    auto buffer=state.observed.borrowing(view.receipt());
    if(state.observed.covered()&&(!decision.observed()||!identity.observed()))return nullptr;
    if(view.receipt()&&!buffer.observed())return nullptr;
    if(!value.decisions.identified)return nullptr;
    return env->NewStringUTF(value.identity.c_str());
} catch(...) { return nullptr; }
