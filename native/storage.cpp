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
struct IdentityLifetime {
    // The real std::allocator adapter outlives both strings, their temporary
    // copies in identity(), and every original deallocation they perform.
    recovery_storage::RootAllocation<IdentityState> root;
    ronova::allocator_state allocations;
    IdentityState *value=nullptr;
    ronova::object_state observed;
    ronova::scalar_field<bool> valid,identified;
    ronova::sequence_field<IdentityPath> requested;
    ronova::sequence_field<IdentityText> identity;
    bool payloadConstructed=false,dtorDone=false,objectClosed=false;
    IdentityLifetime *next=nullptr;
    void initialize() {
        // This root is a real std::allocator allocation using its original
        // operator new/delete. Placement construction grants no storage rights.
        value=root.allocate();
        ::new(static_cast<void*>(value)) IdentityState(allocations);payloadConstructed=true;
        observed.constructed(value,sizeof(*value));
        valid=observed.scalar(value->decisions.valid);
        identified=observed.scalar(value->decisions.identified);
        requested=observed.sequence(value->requested);
        identity=observed.sequence(value->identity);
    }
    bool retire() noexcept {
        if(payloadConstructed&&!dtorDone) {
            // This private lifetime has left identity0's active path. Close
            // all old views before acquiring a same-call destruction receipt;
            // a refused relation leaves the actual object available to retry.
            if(!observed.release(requested)||!observed.release(identity))return false;
            auto destruction=observed.destroying();
            bool covered=observed.covered();
            if(covered&&!destruction.admitted())return false;
            value->~IdentityState();dtorDone=true;
            objectClosed=!covered||destruction.complete();
        }
        // The payload has no live members after its real destructor. A failed
        // completion stays unresolved; neither a second dtor nor guessed free
        // can finish it. Closing allocator state cannot replay refused frees.
        if(payloadConstructed&&!objectClosed)return false;
        if(!allocations.close()||!root.release())return false;
        value=nullptr;return true;
    }
};
static SRWLOCK identity_pending_lock=SRWLOCK_INIT;
static IdentityLifetime *identity_pending=nullptr;
static void park_identity(IdentityLifetime *lifetime) noexcept {
    // The node already exists. Refusal requires no allocation or callback.
    AcquireSRWLockExclusive(&identity_pending_lock);
    lifetime->next=identity_pending;identity_pending=lifetime;
    ReleaseSRWLockExclusive(&identity_pending_lock);
}
static void retry_identity() noexcept {
    ronova::object_state_detail::errors incoming;
    AcquireSRWLockExclusive(&identity_pending_lock);
    auto pending=std::exchange(identity_pending,nullptr);
    ReleaseSRWLockExclusive(&identity_pending_lock);
    // Retry only this detached snapshot, outside the registry lock. The SDK
    // still requires this actual caller's original provider and source set.
    while(pending) {
        auto lifetime=pending;pending=lifetime->next;lifetime->next=nullptr;
        if(lifetime->retire())delete lifetime;
        else park_identity(lifetime);
    }
}
struct IdentityOwner {
    IdentityLifetime *lifetime;
    IdentityOwner():lifetime(new IdentityLifetime) {
        try {lifetime->initialize();}
        catch(...) {
            // Keep both the original construction exception and the complete
            // metadata node if raw-storage retirement is refused or uncertain.
            ronova::object_state_detail::errors original;
            if(lifetime->retire())delete lifetime;
            else park_identity(lifetime);
            throw;
        }
    }
    IdentityOwner(const IdentityOwner&)=delete;
    IdentityOwner& operator=(const IdentityOwner&)=delete;
    ~IdentityOwner() noexcept {
        ronova::object_state_detail::errors incoming;
        if(lifetime->retire())delete lifetime;
        else park_identity(lifetime);
    }
};
}

// Borrow the live Java stream handle: no path-open and no ownership transfer.
extern "C" JNIEXPORT jstring JNICALL
Java_dev_ronova_pro_StorageNative_identity0(JNIEnv* env,jclass,jobject descriptor,jstring path) try {
    retry_identity();
    if(!descriptor||!path)return nullptr;
    if(!recovery_storage::exact(env,descriptor,"java/io/FileDescriptor"))return nullptr;
    auto type=env->GetObjectClass(descriptor);
    auto field=env->GetFieldID(type,"handle","J");env->DeleteLocalRef(type);
    if(!field)return nullptr;
    auto handle=reinterpret_cast<HANDLE>(env->GetLongField(descriptor,field));
    if(!handle||handle==INVALID_HANDLE_VALUE)return nullptr;
    jsize count=env->GetStringLength(path);
    if(count<3||count>4096)return nullptr;
    IdentityOwner owner;auto& state=*owner.lifetime;auto& value=*state.value;
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
