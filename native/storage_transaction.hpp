// Optional Windows NTFS transaction backend. Staging never commits public data.
#include <bcrypt.h>
#include <ktmw32.h>
#include <memory>
#include <limits>
#include <map>
#include <algorithm>
#include <array>
#include <expected>
#include <span>
#include <string_view>
#include "include/ronova_allocator_state.hpp"
#include "include/ronova_object_state.hpp"

namespace recovery_storage {

// Storage's two real consumers keep this node outside the payload lifetime.
// It wraps one original std::allocator allocation, not an allocator provider or
// an object representation. Only the caller knows when the actual dtor ran.
template<class T> class RootAllocation {
    ronova::allocator_state state_;
    T* value_=nullptr;
    bool allocated_=false,entered_=false,returned_=false,called_=false,closed_=false;
    struct Upstream {
        using value_type=T;RootAllocation* actual=nullptr;
        T* allocate(std::size_t count){return std::allocator<T>{}.allocate(count);}
        void deallocate(T* pointer,std::size_t count) noexcept {
            actual->entered_=true;std::allocator<T>{}.deallocate(pointer,count);actual->returned_=true;
        }
        bool operator==(const Upstream& other)const noexcept{return actual==other.actual;}
    };
    using Allocator=ronova::allocator_adapter<T,Upstream>;
public:
    RootAllocation()=default;
    RootAllocation(const RootAllocation&)=delete;
    RootAllocation& operator=(const RootAllocation&)=delete;
    T* allocate() {
        if(allocated_||closed_)throw std::bad_alloc();
        Allocator allocator(state_,Upstream{this});value_=allocator.allocate(1);allocated_=true;return value_;
    }
    bool release() noexcept {
        if(closed_)return true;
        if(allocated_&&!called_) {
            entered_=returned_=false;Allocator allocator(state_,Upstream{this});allocator.deallocate(value_,1);
            if(!entered_)return false; // No original deallocate: a later admitted retry is possible.
            called_=true;
            if(!returned_)return false;
        }
        // Once the actual deallocate ran, only retry metadata/context closure.
        // This proves that the sole adapter record closed, including uncovered
        // records whose receipt was always zero; zero is not a free receipt.
        if(called_&&!returned_)return false;
        if(!state_.close())return false;
        closed_=true;value_=nullptr;return true;
    }
};

struct Api {
    HMODULE module=nullptr;
    decltype(&CreateTransaction) create=nullptr;
    decltype(&CommitTransaction) commit=nullptr;
    decltype(&RollbackTransaction) rollback=nullptr;
    decltype(&CreateFileTransactedW) open=nullptr;
    decltype(&GetTransactionId) id=nullptr;
    decltype(&OpenTransaction) reopen=nullptr;
    decltype(&GetTransactionInformation) information=nullptr;
    Api() {
        module=LoadLibraryExW(L"KtmW32.dll",nullptr,LOAD_LIBRARY_SEARCH_SYSTEM32);
        if(module) {
            create=reinterpret_cast<decltype(create)>(GetProcAddress(module,"CreateTransaction"));
            commit=reinterpret_cast<decltype(commit)>(GetProcAddress(module,"CommitTransaction"));
            rollback=reinterpret_cast<decltype(rollback)>(GetProcAddress(module,"RollbackTransaction"));
            id=reinterpret_cast<decltype(id)>(GetProcAddress(module,"GetTransactionId"));
            reopen=reinterpret_cast<decltype(reopen)>(GetProcAddress(module,"OpenTransaction"));
            information=reinterpret_cast<decltype(information)>(GetProcAddress(module,"GetTransactionInformation"));
        }
        open=reinterpret_cast<decltype(open)>(GetProcAddress(GetModuleHandleW(L"kernel32.dll"),"CreateFileTransactedW"));
    }
    bool supported()const{return create&&commit&&rollback&&open&&id&&reopen&&information;}
};
static Api& api(){static Api value;return value;}
struct Hash {
    BCRYPT_ALG_HANDLE algorithm=nullptr;BCRYPT_HASH_HANDLE handle=nullptr;
    [[nodiscard]] std::expected<void,jlong> close() {
        if(handle) {
            auto status=BCryptDestroyHash(handle);
            if(status<0)return std::unexpected(static_cast<jlong>(status));
            handle=nullptr;
        }
        if(algorithm) {
            auto status=BCryptCloseAlgorithmProvider(algorithm,0);
            if(status<0)return std::unexpected(static_cast<jlong>(status));
            algorithm=nullptr;
        }
        return {};
    }
    ~Hash(){(void)close();} // Exceptional unwind only; normal retirement preserves failed handles.
    [[nodiscard]] std::expected<void,jlong> open() {
        if(auto closed=close();!closed)return closed;
        auto status=BCryptOpenAlgorithmProvider(&algorithm,BCRYPT_SHA256_ALGORITHM,nullptr,0);
        if(status<0)return std::unexpected(static_cast<jlong>(status));
        status=BCryptCreateHash(algorithm,&handle,nullptr,0,nullptr,0,0);
        if(status<0)return std::unexpected(static_cast<jlong>(status));
        return {};
    }
    [[nodiscard]] std::expected<void,jlong> add(std::span<const unsigned char> bytes) {
        auto status=BCryptHashData(handle,const_cast<PUCHAR>(bytes.data()),static_cast<ULONG>(bytes.size()),0);
        if(status<0)return std::unexpected(static_cast<jlong>(status));
        return {};
    }
    [[nodiscard]] std::expected<std::string,jlong> finish() {
        std::array<unsigned char,32> digest;
        auto status=BCryptFinishHash(handle,digest.data(),static_cast<ULONG>(digest.size()),0);
        if(status<0)return std::unexpected(static_cast<jlong>(status));
        static constexpr char hex[]="0123456789abcdef";
        std::string out;
        for(auto b:digest){out+=hex[b>>4];out+=hex[b&15];}
        return out;
    }
};
// These fields describe live OS/JNI resources and their coupled progress. A
// previous phase/offset is not a previous file position or BCrypt hash state.
// Borrow this actual subobject; never snapshot or replay its representation.
struct TransactionControl {
    enum Phase { CandidateHash, Open, BaselineHash, Write, Flush, ExpectedHash, ClosePrepared, Ready } phase=CandidateHash;
    Hash hashing;
    HANDLE handle=INVALID_HANDLE_VALUE,file=INVALID_HANDLE_VALUE;
    jobject request=nullptr;jlong token=0;
    ULONGLONG expires=0;bool finalized=false;jlong outcome=0,error=0;
    jlong image_size=0;
    ULONGLONG offset=0;
};
using TransactionPath=std::basic_string<wchar_t,std::char_traits<wchar_t>,ronova::allocator_adapter<wchar_t>>;
using TransactionText=std::basic_string<char,std::char_traits<char>,ronova::allocator_adapter<char>>;
struct TransactionObservation;
struct Transaction:TransactionControl {
    TransactionPath requested;
    TransactionText expected_id,baseline,expected;
    TransactionObservation* tracking;
    Transaction(const ronova::allocator_state& allocations,TransactionObservation* actual)
        :requested(ronova::allocator_adapter<wchar_t>(allocations)),
        expected_id(ronova::allocator_adapter<char>(allocations)),baseline(ronova::allocator_adapter<char>(allocations)),
        expected(ronova::allocator_adapter<char>(allocations)),tracking(actual){}
    // Exceptional process/JNI unwind only. Normal paths use explicit close receipts below.
    ~Transaction(){if(file!=INVALID_HANDLE_VALUE)CloseHandle(file);
        if(handle!=INVALID_HANDLE_VALUE){if(!finalized)api().rollback(handle);CloseHandle(handle);}}
};
struct TransactionObservation {
    RootAllocation<Transaction> root;
    ronova::allocator_state allocations;
    ronova::object_state object;
    ronova::opaque_field<TransactionControl> control;
    ronova::sequence_field<TransactionPath> requested;
    ronova::sequence_field<TransactionText> expected_id,baseline,expected;
    // Slot identity and actual completion witnesses outlive the payload. In
    // particular, a failed final receipt must not resurrect a destroyed object.
    jobject owner=nullptr;jlong token=0,outcome=0,error=0;
    ULONGLONG expires=0;
    bool counted=false,bound=false,retiring=false,handles_closed=false;
    bool payload_destroyed=false,destroy_completed=false,raw_freed=false,context_closed=false;
    std::shared_ptr<TransactionObservation> unresolved;
    void bind(Transaction& tx) noexcept {
        object.constructed(&tx,sizeof(tx));control=object.opaque(static_cast<TransactionControl&>(tx));
        requested=object.sequence(tx.requested);expected_id=object.sequence(tx.expected_id);
        baseline=object.sequence(tx.baseline);expected=object.sequence(tx.expected);bound=true;
    }
    void observed(const Transaction& tx) noexcept {
        owner=tx.request;token=tx.token;outcome=tx.outcome;error=tx.error;expires=tx.expires;
    }
};
struct TransactionDelete {
    std::shared_ptr<TransactionObservation> tracking;
    void operator()(Transaction* tx)const noexcept;
};
using TransactionPtr=std::unique_ptr<Transaction,TransactionDelete>;
struct TransactionSlot {TransactionPtr value;std::uint64_t revision=0;};
static SRWLOCK slots_lock=SRWLOCK_INIT;
static std::array<TransactionSlot,2> slots;
static std::atomic<int> admitted{0};
static std::atomic<jlong> next_token{1};
static TransactionPtr allocate_transaction() {
    auto tracking=std::make_shared<TransactionObservation>();
    auto pointer=tracking->root.allocate();
    try{std::construct_at(pointer,tracking->allocations,tracking.get());}
    catch(...){if(!tracking->root.release())tracking->unresolved=tracking;throw;}
    return TransactionPtr(pointer,TransactionDelete{std::move(tracking)});
}
template<class F> static bool use_control(Transaction& tx,F&& original) {
    auto& tracking=*tx.tracking;
    if(tracking.payload_destroyed||tracking.raw_freed)return false;
    bool used;
    if(tracking.bound)used=tracking.object.use(tracking.control,std::forward<F>(original));
    else {std::forward<F>(original)();used=true;}
    tracking.observed(tx);return used;
}
static jlongArray result(JNIEnv* env,jlong status,jlong token=0,jlong error=0) {
    if(env->ExceptionCheck())return nullptr;
    jlong values[3]={status,token,error};auto out=env->NewLongArray(3);
    if(out)env->SetLongArrayRegion(out,0,3,values);return out;
}
static TransactionPtr take(JNIEnv* env,jobject owner,jlong token) {
    AcquireSRWLockExclusive(&slots_lock);TransactionPtr found;
    for(auto& slot:slots)if(slot.value) {
        const auto& actual=*slot.value.get_deleter().tracking;
        if(actual.token==token&&env->IsSameObject(actual.owner,owner)){found=std::move(slot.value);++slot.revision;break;}
    }
    ReleaseSRWLockExclusive(&slots_lock);return found;
}
// Closing a handle and committing a transaction are independent observations. Neither JNI
// return success nor a missing registry slot is substituted for a resource-close receipt.
static bool remember_retirement(JNIEnv* env,const TransactionObservation& tx) {
    if(!tx.owner)return false;
    // The original Java failure must not suppress the actual close receipt.
    jthrowable pending=env->ExceptionOccurred();if(pending)env->ExceptionClear();
    auto type=env->GetObjectClass(tx.owner);
    auto method=type?env->GetMethodID(type,"nativeRetired","(JJZJ)V"):nullptr;
    if(type)env->DeleteLocalRef(type);
    if(method)env->CallVoidMethod(tx.owner,method,tx.token,tx.outcome,JNI_TRUE,tx.error);
    bool recorded=method&&!env->ExceptionCheck();
    if(pending){if(env->ExceptionCheck())env->ExceptionClear();env->Throw(pending);env->DeleteLocalRef(pending);}
    return recorded;
}
static bool retain_for_retirement(TransactionPtr& tx,bool active=false) {
    auto& actual=*tx.get_deleter().tracking;
    actual.retiring|=!active;
    ULONGLONG expires=GetTickCount64()+1000;bool updated=true;
    if(active)updated=use_control(*tx,[&]{tx->expires=expires;});
    actual.retiring|=!updated;actual.expires=expires;
    AcquireSRWLockExclusive(&slots_lock);
    // An admitted transaction owns one of two positions, including while outside the lock.
    // Thus its position cannot have been consumed by a third admission.
    for(auto& slot:slots)if(!slot.value){slot.value=std::move(tx);++slot.revision;break;}
    ReleaseSRWLockExclusive(&slots_lock);
    return updated;
}
static bool destroy_storage(Transaction* tx,TransactionObservation& actual) noexcept {
    if(!actual.payload_destroyed) {
        // Retirement owns this payload exclusively and never resumes drive.
        // Close relations first so a refused later relation leaves no destroy
        // scope spanning a JNI return or a different OS thread.
        if(actual.bound&&(!actual.object.release(actual.requested)||!actual.object.release(actual.expected_id)
                ||!actual.object.release(actual.baseline)||!actual.object.release(actual.expected)))return false;
        bool covered=actual.object.covered();auto destruction=actual.object.destroying();
        if(covered&&!destruction.admitted())return false;
        tx->~Transaction();actual.payload_destroyed=true;
        actual.destroy_completed=!covered||destruction.complete();
    }
    if(!actual.destroy_completed)return false;
    if(!actual.context_closed)actual.context_closed=actual.allocations.close();
    if(!actual.context_closed)return false;
    if(!actual.raw_freed) {
        actual.raw_freed=actual.root.release();
    }
    return actual.raw_freed;
}
static bool release(JNIEnv* env,TransactionPtr& tx) {
    if(!tx)return true;auto actual=tx.get_deleter().tracking;actual->retiring=true;
    if(!destroy_storage(tx.get(),*actual)||(actual->owner&&!remember_retirement(env,*actual))) {
        retain_for_retirement(tx);return false;
    }
    if(actual->owner)env->DeleteGlobalRef(actual->owner);actual->owner=nullptr;
    (void)tx.release();if(actual->counted){actual->counted=false;admitted.fetch_sub(1);}return true;
}
inline void TransactionDelete::operator()(Transaction* tx)const noexcept {
    if(!tx)return;
    // StageCleanup normally transfers a JNI-owned transaction back to its
    // slot. Early allocation failures have no Java reference or OS handles.
    if(!tracking->owner&&destroy_storage(tx,*tracking)) {
        if(tracking->counted){tracking->counted=false;admitted.fetch_sub(1);}return;
    }
    TransactionPtr retained(tx,*this);retain_for_retirement(retained);
    if(retained) {
        // The two-admission invariant supplies a slot during normal operation.
        // At static teardown retain the same metadata/storage identity instead
        // of recursively invoking a refused deleter or reusing its address.
        tracking->unresolved=tracking;(void)retained.release();
    }
}
static bool close_file(Transaction& tx) {
    if(tx.file==INVALID_HANDLE_VALUE)return true;
    if(!CloseHandle(tx.file)){tx.error=GetLastError();return false;}
    tx.file=INVALID_HANDLE_VALUE;return true;
}
static bool finish_handles(JNIEnv* env,TransactionPtr& tx) {
    auto& actual=*tx.get_deleter().tracking;actual.retiring=true;
    if(!actual.handles_closed) {
        bool closed=false;
        bool observed=use_control(*tx,[&]{
            if(auto hash=tx->hashing.close();!hash){tx->error=hash.error();return;}
            if(tx->file==INVALID_HANDLE_VALUE&&(tx->handle==INVALID_HANDLE_VALUE||CloseHandle(tx->handle))) {
                tx->handle=INVALID_HANDLE_VALUE;closed=true;return;
            }
            tx->error=GetLastError();
        });
        actual.handles_closed=closed;
        if(!observed||!closed){retain_for_retirement(tx);return false;}
    }
    return release(env,tx);
}
static bool rollback_and_retire(JNIEnv* env,TransactionPtr& tx) {
    auto& actual=*tx.get_deleter().tracking;actual.retiring=true;
    if(actual.handles_closed)return release(env,tx);
    // Keep even failed-to-close file handles. An attempted close is not a close receipt.
    bool closed=false;
    bool observed=use_control(*tx,[&]{
        if(!close_file(*tx))return;
        closed=true;
        if(!tx->finalized) {
            tx->finalized=true;
            if(tx->handle==INVALID_HANDLE_VALUE)tx->outcome=0; // never allocated, not a fictitious rollback
            else if(api().rollback(tx->handle))tx->outcome=2;
            else {tx->outcome=3;tx->error=GetLastError();}
        }
    });
    if(!observed||!closed){retain_for_retirement(tx);return false;}
    return finish_handles(env,tx);
}
static bool live_owner(JNIEnv* env,jobject request) {
    auto type=env->GetObjectClass(request);
    auto method=type?env->GetMethodID(type,"nativeKeepAlive","()Z"):nullptr;
    if(type)env->DeleteLocalRef(type);
    return method&&env->CallBooleanMethod(request,method)==JNI_TRUE&&!env->ExceptionCheck();
}
static jlong reap_expired(JNIEnv* env) {
    std::array<TransactionPtr,2> expired;ULONGLONG now=GetTickCount64();jlong reaped=0;
    for(std::size_t i=0;i<slots.size();i++) {
        jobject owner=nullptr;jlong token=0;std::uint64_t revision=0;bool callback=false;
        AcquireSRWLockExclusive(&slots_lock);
        auto& slot=slots[i];
        if(slot.value) {
            auto& actual=*slot.value.get_deleter().tracking;
            if(now>=actual.expires) {
                revision=slot.revision;token=actual.token;
                callback=!actual.retiring&&!actual.payload_destroyed;
                if(callback)owner=env->NewLocalRef(actual.owner);
                else {expired[i]=std::move(slot.value);++slot.revision;}
            }
        }
        ReleaseSRWLockExclusive(&slots_lock);
        if(callback&&owner) {
            // Keep the original token visible while Java executes. A take or
            // subsequent reinsert changes revision, so this callback cannot
            // expire or renew the next state of the same transaction.
            bool live=live_owner(env,owner);
            AcquireSRWLockExclusive(&slots_lock);
            auto& current=slots[i];
            if(current.value&&current.revision==revision) {
                auto& actual=*current.value.get_deleter().tracking;
                if(actual.token==token&&env->IsSameObject(actual.owner,owner)) {
                    if(live&&!env->ExceptionCheck()){actual.expires=now+1000;++current.revision;}
                    else {expired[i]=std::move(current.value);++current.revision;}
                }
            }
            ReleaseSRWLockExclusive(&slots_lock);env->DeleteLocalRef(owner);
        }
        if(env->ExceptionCheck())break;
    }
    // No Root permit is involved in rollback; no registry lock spans OS I/O.
    for(auto& tx:expired)if(tx&&rollback_and_retire(env,tx))reaped++;
    return reaped;
}
static bool exact(JNIEnv* env,jobject object,const char* name) {
    if(!object)return false;auto expected=env->FindClass(name);auto actual=env->GetObjectClass(object);
    bool ok=expected&&actual&&env->IsSameObject(expected,actual);
    if(expected)env->DeleteLocalRef(expected);if(actual)env->DeleteLocalRef(actual);return ok;
}
static bool string(JNIEnv* env,jobject object,const char* method,std::string& value) {
    auto type=env->GetObjectClass(object);auto id=env->GetMethodID(type,method,"()Ljava/lang/String;");env->DeleteLocalRef(type);
    if(!id)return false;auto text=static_cast<jstring>(env->CallObjectMethod(object,id));
    if(env->ExceptionCheck()||!text)return false;
    const char* bytes=env->GetStringUTFChars(text,nullptr);if(!bytes){env->DeleteLocalRef(text);return false;}
    value=bytes;env->ReleaseStringUTFChars(text,bytes);env->DeleteLocalRef(text);return true;
}
static bool path(JNIEnv* env,jobject request,std::wstring& value) {
    auto type=env->GetObjectClass(request);auto method=env->GetMethodID(type,"path","()Ljava/lang/String;");env->DeleteLocalRef(type);
    if(!method)return false;auto text=static_cast<jstring>(env->CallObjectMethod(request,method));
    if(env->ExceptionCheck()||!text)return false;jsize count=env->GetStringLength(text);
    if(count<3||count>4096){env->DeleteLocalRef(text);return false;}
    const jchar* chars=env->GetStringChars(text,nullptr);if(!chars){env->DeleteLocalRef(text);return false;}
    bool ok=true;value.resize(count);
    for(jsize i=0;i<count;i++){value[i]=chars[i]==L'/'?L'\\':chars[i];if(chars[i]==0||(chars[i]==':'&&i!=1))ok=false;}
    env->ReleaseStringChars(text,chars);env->DeleteLocalRef(text);
    return ok&&((value[0]>=L'A'&&value[0]<=L'Z')||(value[0]>=L'a'&&value[0]<=L'z'))&&value[1]==L':'&&value[2]==L'\\';
}
template<class PathAllocator,class TextAllocator>
static bool identity(HANDLE file,const std::basic_string<wchar_t,std::char_traits<wchar_t>,PathAllocator>& requested,
        std::basic_string<char,std::char_traits<char>,TextAllocator>& out) {
    BY_HANDLE_FILE_INFORMATION info{};FILE_ID_INFO id{};wchar_t fs[32],final_path[4104];
    if(GetFileType(file)!=FILE_TYPE_DISK||!GetFileInformationByHandle(file,&info)||info.nNumberOfLinks!=1
        ||(info.dwFileAttributes&(FILE_ATTRIBUTE_DIRECTORY|FILE_ATTRIBUTE_REPARSE_POINT|FILE_ATTRIBUTE_ENCRYPTED|FILE_ATTRIBUTE_SPARSE_FILE|FILE_ATTRIBUTE_COMPRESSED))
        ||!GetFileInformationByHandleEx(file,FileIdInfo,&id,sizeof(id))
        ||!GetVolumeInformationByHandleW(file,nullptr,0,nullptr,nullptr,nullptr,fs,32)||_wcsicmp(fs,L"NTFS")!=0)return false;
    DWORD length=GetFinalPathNameByHandleW(file,final_path,4104,FILE_NAME_NORMALIZED|VOLUME_NAME_DOS);
    if(!length||length>=4104||_wcsicmp(final_path,(L"\\\\?\\"+requested).c_str())!=0)return false;
    unsigned long long low=0,high=0;std::memcpy(&low,id.FileId.Identifier,8);std::memcpy(&high,id.FileId.Identifier+8,8);
    char text[80];std::snprintf(text,sizeof(text),"win32-file-id/1:%llx:%llx:%llx",static_cast<unsigned long long>(id.VolumeSerialNumber),low,high);
    out=text;return true;
}
}

namespace recovery_storage {
struct StageCleanup {
    JNIEnv* env;TransactionPtr& tx;bool& retained;
    ~StageCleanup(){if(!retained&&tx){retained=true;rollback_and_retire(env,tx);}}
};
[[nodiscard]] static std::expected<void,jlong> start_file_hash(Transaction& tx) {
    LARGE_INTEGER zero{};
    tx.offset=0;
    if(!SetFilePointerEx(tx.file,zero,nullptr,FILE_BEGIN))return std::unexpected(static_cast<jlong>(GetLastError()));
    return tx.hashing.open();
}
// The time/byte allowance belongs to one call. Phase, file position and digest belong
// to the original token and are never reset merely because that allowance is spent.
static jlong drive_actual(JNIEnv* env,Transaction& tx) {
    if(tx.phase==Transaction::Ready)return 1;
    constexpr DWORD chunk_size=16384;
    std::array<unsigned char,chunk_size> buffer;
    auto type=env->GetObjectClass(tx.request);
    auto read=type?env->GetMethodID(type,"readImage","(J[B)I"):nullptr;
    if(type)env->DeleteLocalRef(type);
    if(!read)return 0;
    auto chunk=env->NewByteArray(chunk_size);if(!chunk)return 0;
    struct Local {JNIEnv* env;jobject value;~Local(){env->DeleteLocalRef(value);}} local{env,chunk};
    ULONGLONG deadline=GetTickCount64()+20;DWORD consumed=0;bool first=true;
    auto failure=[&](jlong code)->jlong{tx.error=code?code:GetLastError();return 6;};
    auto candidate=[&](DWORD want)->bool {
        jint count=env->CallIntMethod(tx.request,read,static_cast<jlong>(tx.offset),chunk);
        if(env->ExceptionCheck()||count!=static_cast<jint>(want))return false;
        env->GetByteArrayRegion(chunk,0,count,reinterpret_cast<jbyte*>(buffer.data()));
        return !env->ExceptionCheck();
    };
    while(first||(GetTickCount64()<deadline&&consumed<1024*1024)) {
        first=false;
        switch(tx.phase) {
            case Transaction::CandidateHash: {
                DWORD want=static_cast<DWORD>(std::min<ULONGLONG>(chunk_size,static_cast<ULONGLONG>(tx.image_size)-tx.offset));
                if(want) {
                    if(!candidate(want))return 0;
                    if(auto hashed=tx.hashing.add(std::span<const unsigned char>(buffer).first(want));!hashed)return failure(hashed.error());
                    tx.offset+=want;consumed+=want;break;
                }
                auto hash=tx.hashing.finish();
                if(!hash)return failure(hash.error());
                if(std::string_view(hash->data(),hash->size())!=std::string_view(tx.expected.data(),tx.expected.size()))return 0;
                tx.phase=Transaction::Open;break;
            }
            case Transaction::Open: {
                // Capacity is not an elapsed-time permission. The exact Java owner
                // retains this private transaction; stop/cancel still rolls it back.
                tx.handle=api().create(nullptr,nullptr,0,0,0,0,const_cast<wchar_t*>(L"Ronova registered recovery storage"));
                if(tx.handle==INVALID_HANDLE_VALUE){tx.error=GetLastError();return 4;}
                GUID guid{};if(!api().id(tx.handle,&guid))return failure(0);
                char transaction_id[33];static const char hex[]="0123456789abcdef";
                const auto raw=reinterpret_cast<const unsigned char*>(&guid);
                for(int i=0;i<16;i++){transaction_id[2*i]=hex[raw[i]>>4];transaction_id[2*i+1]=hex[raw[i]&15];}transaction_id[32]=0;
                auto request_type=env->GetObjectClass(tx.request);
                auto identify=request_type?env->GetMethodID(request_type,"nativeTransaction","(Ljava/lang/String;)V"):nullptr;
                if(request_type)env->DeleteLocalRef(request_type);
                if(!identify)return 0;
                auto text=env->NewStringUTF(transaction_id);if(!text)return 0;
                env->CallVoidMethod(tx.request,identify,text);env->DeleteLocalRef(text);
                if(env->ExceptionCheck())return 0;
                tx.file=api().open(tx.requested.c_str(),GENERIC_READ|GENERIC_WRITE,0,nullptr,OPEN_EXISTING,FILE_FLAG_OPEN_REPARSE_POINT,nullptr,tx.handle,nullptr,nullptr);
                if(tx.file==INVALID_HANDLE_VALUE)return failure(0);
                std::string file_id;if(!identity(tx.file,tx.requested,file_id)
                    ||std::string_view(file_id.data(),file_id.size())!=std::string_view(tx.expected_id.data(),tx.expected_id.size()))return 5;
                if(auto started=start_file_hash(tx);!started)return failure(started.error());
                tx.phase=Transaction::BaselineHash;break;
            }
            case Transaction::BaselineHash:
            case Transaction::ExpectedHash: {
                DWORD count=0;if(!ReadFile(tx.file,buffer.data(),static_cast<DWORD>(buffer.size()),&count,nullptr))return failure(0);
                if(count) {
                    if(tx.offset>static_cast<ULONGLONG>(std::numeric_limits<jlong>::max())-count)return failure(ERROR_ARITHMETIC_OVERFLOW);
                    if(auto hashed=tx.hashing.add(std::span<const unsigned char>(buffer).first(count));!hashed)return failure(hashed.error());
                    tx.offset+=count;consumed+=count;break;
                }
                auto hash=tx.hashing.finish();if(!hash)return failure(hash.error());
                const auto& expected=tx.phase==Transaction::BaselineHash?tx.baseline:tx.expected;
                if(std::string_view(hash->data(),hash->size())!=std::string_view(expected.data(),expected.size()))return 5;
                if(tx.phase==Transaction::ExpectedHash) { tx.phase=Transaction::ClosePrepared;break; }
                LARGE_INTEGER zero{};if(!SetFilePointerEx(tx.file,zero,nullptr,FILE_BEGIN))return failure(0);
                tx.offset=0;tx.phase=Transaction::Write;break;
            }
            case Transaction::Write: {
                DWORD want=static_cast<DWORD>(std::min<ULONGLONG>(chunk_size,static_cast<ULONGLONG>(tx.image_size)-tx.offset));
                if(!want){tx.phase=Transaction::Flush;break;}
                if(!candidate(want))return 0;
                DWORD count=0;
                if(!WriteFile(tx.file,buffer.data(),want,&count,nullptr))return failure(0);
                if(!count||count>want)return failure(ERROR_WRITE_FAULT);
                tx.offset+=count;consumed+=count;break;
            }
            case Transaction::Flush:
                if(!SetEndOfFile(tx.file)||!FlushFileBuffers(tx.file))return failure(0);
                if(auto started=start_file_hash(tx);!started)return failure(started.error());
                tx.phase=Transaction::ExpectedHash;break;
            case Transaction::ClosePrepared: {
                std::string file_id;if(!identity(tx.file,tx.requested,file_id)
                    ||std::string_view(file_id.data(),file_id.size())!=std::string_view(tx.expected_id.data(),tx.expected_id.size()))return 5;
                if(!close_file(tx))return failure(tx.error);
                tx.phase=Transaction::Ready;return 1;
            }
            case Transaction::Ready:return 1;
        }
    }
    return 7;
}
template<class C> struct ConfigurationRead {
    ronova::object_state::borrow_scope representation;
    ronova::buffer_field<typename C::value_type> current;
    ronova::object_state::borrow_scope elements;
    ConfigurationRead(ronova::object_state& object,const ronova::sequence_field<C>& sequence)
        :representation(object.borrowing(sequence.receipt())),current(object.view(sequence)),elements(object.borrowing(current.receipt())){}
    bool admitted(bool covered)const noexcept{return !covered||(representation.observed()&&current.covered()&&elements.observed());}
};
static jlong drive(JNIEnv* env,Transaction& tx) {
    auto& actual=*tx.tracking;
    if(actual.retiring||actual.payload_destroyed)return 6;
    ConfigurationRead path(actual.object,actual.requested);
    ConfigurationRead id(actual.object,actual.expected_id);
    ConfigurationRead baseline(actual.object,actual.baseline);
    ConfigurationRead expected(actual.object,actual.expected);
    bool covered=actual.object.covered();
    if(!path.admitted(covered)||!id.admitted(covered)||!baseline.admitted(covered)||!expected.admitted(covered)) {
        actual.retiring=true;actual.error=ERROR_BUSY;return 6;
    }
    // Configuration is assigned exactly once. A recovered empty configuration
    // cannot reconstruct a running transaction or replay its earlier phase.
    if(tx.requested.empty()||tx.expected_id.empty()||tx.baseline.empty()||tx.expected.empty()) {
        actual.retiring=true;return 0;
    }
    jlong status=6;
    if(!use_control(tx,[&]{status=drive_actual(env,tx);})) {
        actual.retiring=true;if(!actual.error)actual.error=ERROR_BUSY;return 6;
    }
    return status;
}
static jlongArray advance_owned(JNIEnv* env,TransactionPtr& tx,bool& retained) {
    auto actual=tx.get_deleter().tracking;
    jlong status=drive(env,*tx),token=actual->token,error=actual->error;
    auto response=result(env,status,token,error);if(!response)return nullptr;
    if(status==1||status==7) {
        if(!retain_for_retirement(tx,true)) {
            jlong refused[3]={6,token,ERROR_BUSY};env->SetLongArrayRegion(response,0,3,refused);
        }
        retained=true;
    }
    return response;
}
}

// Result: status, the original opaque token, Win32 error. 7 retains preparation progress.
extern "C" JNIEXPORT jlongArray JNICALL
Java_dev_ronova_pro_StorageNative_prepare0(JNIEnv* env,jclass,jobject request) try {
    using namespace recovery_storage;
    if(!api().supported())return result(env,4);
    if(!exact(env,request,"dev/ronova/pro/RecoveryStorage$Ticket"))return result(env,0);
    reap_expired(env);
    int previous=admitted.fetch_add(1);if(previous>=static_cast<int>(slots.size())){admitted.fetch_sub(1);return result(env,7,0,ERROR_BUSY);}
    bool transferred=false;struct Admission{bool& transferred;~Admission(){if(!transferred)admitted.fetch_sub(1);}} admission{transferred};
    bool retained=false;
    std::wstring requested;std::string expected_id,baseline,expected;
    if(!path(env,request,requested)||!string(env,request,"fileIdentity",expected_id)
        ||!string(env,request,"baselineHash",baseline)||!string(env,request,"expectedHash",expected))return result(env,0);
    auto type=env->GetObjectClass(request);auto getter=type?env->GetMethodID(type,"imageSize","()J"):nullptr;
    if(type)env->DeleteLocalRef(type);
    if(!getter)return nullptr;jlong size=env->CallLongMethod(request,getter);
    if(env->ExceptionCheck()||size<1)return result(env,0);
    auto tx=allocate_transaction();auto actual=tx.get_deleter().tracking;
    actual->counted=true;transferred=true;
    tx->token=next_token.fetch_add(1);
    actual->observed(*tx);
    if(tx->token<=0||tx->token==std::numeric_limits<jlong>::max())return result(env,6);
    tx->request=env->NewGlobalRef(request);actual->observed(*tx);if(!tx->request)return nullptr;
    actual->bind(*tx);
    StageCleanup cleanup{env,tx,retained};
    if(!actual->object.mutate(actual->requested,[&]{tx->requested.assign(requested.data(),requested.size());})
        ||!actual->object.mutate(actual->expected_id,[&]{tx->expected_id.assign(expected_id.data(),expected_id.size());})
        ||!actual->object.mutate(actual->baseline,[&]{tx->baseline.assign(baseline.data(),baseline.size());})
        ||!actual->object.mutate(actual->expected,[&]{tx->expected.assign(expected.data(),expected.size());})
        ||!use_control(*tx,[&]{tx->image_size=size;}))return result(env,6,actual->token,ERROR_BUSY);
    auto request_type=env->GetObjectClass(request);
    auto allocated=request_type?env->GetMethodID(request_type,"nativeAllocated","(J)V"):nullptr;
    if(request_type)env->DeleteLocalRef(request_type);
    if(!allocated)return nullptr;
    if(!use_control(*tx,[&]{env->CallVoidMethod(request,allocated,tx->token);}))return result(env,6,actual->token,ERROR_BUSY);
    if(env->ExceptionCheck())return nullptr;
    std::expected<void,jlong> opened;
    if(!use_control(*tx,[&]{opened=tx->hashing.open();}))return result(env,6,actual->token,ERROR_BUSY);
    if(!opened)return result(env,6,actual->token,opened.error());
    return advance_owned(env,tx,retained);
} catch(...) {
    if(!env->ExceptionCheck()){auto error=env->FindClass("java/lang/IllegalStateException");if(error){env->ThrowNew(error,"Native transaction preparation unavailable");env->DeleteLocalRef(error);}}
    return nullptr;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_dev_ronova_pro_StorageNative_advance0(JNIEnv* env,jclass,jobject request,jlong token) try {
    using namespace recovery_storage;
    if(!exact(env,request,"dev/ronova/pro/RecoveryStorage$Ticket"))return result(env,0,token);
    auto tx=take(env,request,token);
    if(!tx)return result(env,0,token,ERROR_NOT_FOUND);
    bool retained=false;StageCleanup cleanup{env,tx,retained};
    auto actual=tx.get_deleter().tracking;
    if(actual->retiring||actual->payload_destroyed)return result(env,0,token,actual->error);
    bool live=false;
    if(!use_control(*tx,[&]{live=!tx->finalized&&live_owner(env,tx->request);}))return result(env,6,token,ERROR_BUSY);
    if(!live)return result(env,0,token);
    return advance_owned(env,tx,retained);
} catch(...) {
    if(!env->ExceptionCheck()){auto error=env->FindClass("java/lang/IllegalStateException");if(error){env->ThrowNew(error,"Native transaction continuation unavailable");env->DeleteLocalRef(error);}}
    return nullptr;
}
extern "C" JNIEXPORT jlongArray JNICALL
Java_dev_ronova_pro_StorageNative_finish0(JNIEnv* env,jclass,jobject request,jlong token,jobject permit) {
    using namespace recovery_storage;
    auto tx=take(env,request,token);
    if(!tx) {
        // An expiry sweep may already have closed this exact token. Query only its receipt;
        // never treat arbitrary absence as rollback, and never enter a new transaction here.
        if(!request||!exact(env,request,"dev/ronova/pro/RecoveryStorage$Ticket"))return result(env,0);
        auto type=env->GetObjectClass(request);auto query=env->GetMethodID(type,"nativeRetirement","(J)[J");env->DeleteLocalRef(type);
        return query?static_cast<jlongArray>(env->CallObjectMethod(request,query,token)):result(env,0);
    }
    auto actual=tx.get_deleter().tracking;
    if(actual->retiring||actual->payload_destroyed) {
        bool released=rollback_and_retire(env,tx);
        return result(env,actual->outcome,released?1:0,actual->error);
    }
    bool closed=false;
    if(!use_control(*tx,[&]{closed=close_file(*tx);})||!closed) {
        jlong error=actual->error?actual->error:ERROR_BUSY;retain_for_retirement(tx);return result(env,0,0,error);
    }
    bool observed=use_control(*tx,[&]{
        bool allowed=false;
        if(!tx->finalized&&tx->phase==Transaction::Ready&&permit
            &&exact(env,permit,"dev/ronova/pro/RecoveryStorage$Permit")) {
            auto type=env->GetObjectClass(permit);auto valid=env->GetMethodID(type,"beginCommitFor","(Ldev/ronova/pro/RecoveryStorage$Ticket;)Z");env->DeleteLocalRef(type);
            if(valid)allowed=env->CallBooleanMethod(permit,valid,request)==JNI_TRUE&&!env->ExceptionCheck();
        }
        if(!tx->finalized) {
            tx->finalized=true;
            if(tx->handle==INVALID_HANDLE_VALUE)tx->outcome=0;
            else if(allowed) {if(api().commit(tx->handle))tx->outcome=1;else{tx->outcome=3;tx->error=GetLastError();}}
            else {if(api().rollback(tx->handle))tx->outcome=2;else{tx->outcome=3;tx->error=GetLastError();}}
        }
    });
    if(!observed) {
        bool released=rollback_and_retire(env,tx);
        return result(env,actual->outcome,released?1:0,actual->error?actual->error:ERROR_BUSY);
    }
    jlong status=actual->outcome,error=actual->error;
    bool released=finish_handles(env,tx);
    return result(env,status,released?1:0,error);
}

// Called periodically even when no new storage preparation is submitted. Optional backend:
// unsupported TxF never becomes a startup dependency or a fallback ordinary overwrite.
extern "C" JNIEXPORT jlongArray JNICALL
Java_dev_ronova_pro_StorageNative_maintain0(JNIEnv* env,jclass) {
    using namespace recovery_storage;
    if(!api().supported())return result(env,4);
    jlong count=reap_expired(env);
    return result(env,1,count,admitted.load());
}

// Read-only original-transaction query. Failed closes remain owned and are retried by the same ID.
// Outcome: 1 committed, 2 aborted, 3 still pending, 4 no transaction object, 0 unknown.
extern "C" JNIEXPORT jlongArray JNICALL
Java_dev_ronova_pro_StorageNative_queryTransaction0(JNIEnv* env,jclass,jstring encoded) try {
    using namespace recovery_storage;
    if(!api().supported()||!encoded||env->GetStringLength(encoded)!=32)return result(env,0,1,ERROR_NOT_SUPPORTED);
    const char* chars=env->GetStringUTFChars(encoded,nullptr);if(!chars)return nullptr;
    std::string key(chars);env->ReleaseStringUTFChars(encoded,chars);
    GUID guid{};auto bytes=reinterpret_cast<unsigned char*>(&guid);
    auto nibble=[](char c)->int{return c>='0'&&c<='9'?c-'0':c>='a'&&c<='f'?c-'a'+10:-1;};
    for(int i=0;i<16;i++){int high=nibble(key[2*i]),low=nibble(key[2*i+1]);if(high<0||low<0)return result(env,0,1,ERROR_INVALID_DATA);bytes[i]=static_cast<unsigned char>((high<<4)|low);}
    static SRWLOCK query_lock=SRWLOCK_INIT;
    static std::map<std::string,HANDLE> retained;
    AcquireSRWLockExclusive(&query_lock);
    struct Unlock{SRWLOCK* lock;~Unlock(){ReleaseSRWLockExclusive(lock);}} unlock{&query_lock};
    HANDLE handle=INVALID_HANDLE_VALUE;auto found=retained.find(key);
    if(found!=retained.end())handle=found->second;
    else {
        if(retained.size()>=2)return result(env,0,1,ERROR_BUSY);
        handle=api().reopen(TRANSACTION_QUERY_INFORMATION,&guid);
        if(handle==INVALID_HANDLE_VALUE){DWORD error=GetLastError();return result(env,error==ERROR_TRANSACTION_NOT_FOUND?4:0,1,error);}
        retained.emplace(key,handle);
    }
    DWORD outcome=0;bool queried=api().information(handle,&outcome,nullptr,nullptr,nullptr,0,nullptr)!=FALSE;
    DWORD error=queried?0:GetLastError();
    bool closed=CloseHandle(handle)!=FALSE;if(closed)retained.erase(key);else error=GetLastError();
    jlong status=!queried?0:outcome==TransactionOutcomeCommitted?1:outcome==TransactionOutcomeAborted?2:3;
    return result(env,status,closed?1:0,error);
} catch(...) { return recovery_storage::result(env,0,0,ERROR_NOT_ENOUGH_MEMORY); }
