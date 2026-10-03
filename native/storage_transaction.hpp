// Optional Windows NTFS transaction backend. Staging never commits public data.
#include <bcrypt.h>
#include <ktmw32.h>
#include <memory>
#include <limits>
#include <map>
#include <algorithm>

namespace recovery_storage {
// Validation can only manufacture failure, never return a successful native receipt.
static bool injected_failure(JNIEnv* env,jobject request,const char* point) {
    auto type=env->GetObjectClass(request);
    auto method=env->GetMethodID(type,"validationFault","(Ljava/lang/String;)Z");env->DeleteLocalRef(type);
    if(!method){env->ExceptionClear();return false;} // Optional fault hook; existing production ABI remains valid.
    auto name=env->NewStringUTF(point);if(!name)return true;
    bool failed=env->CallBooleanMethod(request,method,name)==JNI_TRUE;env->DeleteLocalRef(name);
    if(env->ExceptionCheck()){env->ExceptionClear();failed=true;}
    if(failed)SetLastError(ERROR_WRITE_FAULT);
    return failed;
}
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
    jlong error=0;
    bool close() {
        if(handle) {auto status=BCryptDestroyHash(handle);if(status<0){error=status;return false;}handle=nullptr;}
        if(algorithm) {auto status=BCryptCloseAlgorithmProvider(algorithm,0);if(status<0){error=status;return false;}algorithm=nullptr;}
        return true;
    }
    ~Hash(){close();} // Exceptional unwind only; normal retirement preserves failed handles.
    bool open(){if(!close())return false;
        auto status=BCryptOpenAlgorithmProvider(&algorithm,BCRYPT_SHA256_ALGORITHM,nullptr,0);
        if(status<0){error=status;return false;}
        status=BCryptCreateHash(algorithm,&handle,nullptr,0,nullptr,0,0);if(status<0){error=status;return false;}return true;}
    bool add(const unsigned char* bytes,ULONG size){auto status=BCryptHashData(handle,const_cast<PUCHAR>(bytes),size,0);if(status<0)error=status;return status>=0;}
    bool finish(std::string& out){unsigned char digest[32];auto status=BCryptFinishHash(handle,digest,32,0);
        if(status<0){error=status;return false;}
        static const char hex[]="0123456789abcdef";out.clear();for(auto b:digest){out+=hex[b>>4];out+=hex[b&15];}return true;}
};
struct Transaction {
    enum Phase { CandidateHash, Open, BaselineHash, Write, Flush, ExpectedHash, ClosePrepared, Ready } phase=CandidateHash;
    Hash hashing;
    HANDLE handle=INVALID_HANDLE_VALUE,file=INVALID_HANDLE_VALUE;
    jobject request=nullptr;jlong token=0;
    ULONGLONG expires=0;bool finalized=false;jlong outcome=0,error=0;
    std::wstring requested;
    std::string expected_id,baseline,expected;
    jlong image_size=0;
    ULONGLONG offset=0;
    // Exceptional process/JNI unwind only. Normal paths use explicit close receipts below.
    ~Transaction(){if(file!=INVALID_HANDLE_VALUE)CloseHandle(file);
        if(handle!=INVALID_HANDLE_VALUE){if(!finalized)api().rollback(handle);CloseHandle(handle);}}
};
static SRWLOCK slots_lock=SRWLOCK_INIT;
static std::unique_ptr<Transaction> slots[2];
static std::atomic<int> admitted{0};
static std::atomic<jlong> next_token{1};
static jlongArray result(JNIEnv* env,jlong status,jlong token=0,jlong error=0) {
    if(env->ExceptionCheck())return nullptr;
    jlong values[3]={status,token,error};auto out=env->NewLongArray(3);
    if(out)env->SetLongArrayRegion(out,0,3,values);return out;
}
static std::unique_ptr<Transaction> take(JNIEnv* env,jobject owner,jlong token) {
    AcquireSRWLockExclusive(&slots_lock);std::unique_ptr<Transaction> found;
    for(auto& slot:slots)if(slot&&slot->token==token&&env->IsSameObject(slot->request,owner)){found=std::move(slot);break;}
    ReleaseSRWLockExclusive(&slots_lock);return found;
}
static void release(JNIEnv* env,std::unique_ptr<Transaction>& tx) {
    if(!tx)return;
    if(tx->request)env->DeleteGlobalRef(tx->request);
    tx.reset();admitted.fetch_sub(1);
}
// Closing a handle and committing a transaction are independent observations. Neither JNI
// return success nor a missing registry slot is substituted for a resource-close receipt.
static bool remember_retirement(JNIEnv* env,Transaction& tx) {
    if(!tx.request)return false;
    // The original Java failure must not suppress the actual close receipt.
    jthrowable pending=env->ExceptionOccurred();if(pending)env->ExceptionClear();
    auto type=env->GetObjectClass(tx.request);
    auto method=type?env->GetMethodID(type,"nativeRetired","(JJZJ)V"):nullptr;
    if(type)env->DeleteLocalRef(type);
    if(method)env->CallVoidMethod(tx.request,method,tx.token,tx.outcome,JNI_TRUE,tx.error);
    bool recorded=method&&!env->ExceptionCheck();
    if(pending){if(env->ExceptionCheck())env->ExceptionClear();env->Throw(pending);env->DeleteLocalRef(pending);}
    return recorded;
}
static void retain_for_retirement(std::unique_ptr<Transaction>& tx) {
    tx->expires=GetTickCount64()+1000;
    AcquireSRWLockExclusive(&slots_lock);
    // An admitted transaction owns one of two positions, including while outside the lock.
    // Thus its position cannot have been consumed by a third admission.
    for(auto& slot:slots)if(!slot){slot=std::move(tx);break;}
    ReleaseSRWLockExclusive(&slots_lock);
}
static bool close_file(Transaction& tx) {
    if(tx.file==INVALID_HANDLE_VALUE)return true;
    if(!CloseHandle(tx.file)){tx.error=GetLastError();return false;}
    tx.file=INVALID_HANDLE_VALUE;return true;
}
static bool finish_handles(JNIEnv* env,std::unique_ptr<Transaction>& tx) {
    if(!tx->hashing.close()) {tx->error=tx->hashing.error;retain_for_retirement(tx);return false;}
    if(tx->file==INVALID_HANDLE_VALUE&&(tx->handle==INVALID_HANDLE_VALUE||CloseHandle(tx->handle))) {
        tx->handle=INVALID_HANDLE_VALUE;
        if(!remember_retirement(env,*tx)){retain_for_retirement(tx);return false;}
        release(env,tx);return true;
    }
    tx->error=GetLastError();
    retain_for_retirement(tx);return false;
}
static bool rollback_and_retire(JNIEnv* env,std::unique_ptr<Transaction>& tx) {
    // Keep even failed-to-close file handles. An attempted close is not a close receipt.
    if(!close_file(*tx)){retain_for_retirement(tx);return false;}
    if(!tx->finalized) {
        tx->finalized=true;
        if(tx->handle==INVALID_HANDLE_VALUE)tx->outcome=0; // never allocated, not a fictitious rollback
        else if(api().rollback(tx->handle))tx->outcome=2;
        else {tx->outcome=3;tx->error=GetLastError();}
    }
    return finish_handles(env,tx);
}
static bool live_owner(JNIEnv* env,Transaction& tx) {
    auto type=env->GetObjectClass(tx.request);
    auto method=type?env->GetMethodID(type,"nativeKeepAlive","()Z"):nullptr;
    if(type)env->DeleteLocalRef(type);
    return method&&env->CallBooleanMethod(tx.request,method)==JNI_TRUE&&!env->ExceptionCheck();
}
static jlong reap_expired(JNIEnv* env) {
    std::unique_ptr<Transaction> expired[2];ULONGLONG now=GetTickCount64();jlong reaped=0;
    AcquireSRWLockExclusive(&slots_lock);
    for(int i=0;i<2;i++)if(slots[i]&&now>=slots[i]->expires) {
        // This exact private callback reads ownership fields only. Keep an active
        // token visible to its next advance/finish while another owner maintains.
        if(!slots[i]->finalized&&live_owner(env,*slots[i]))slots[i]->expires=now+1000;
        else expired[i]=std::move(slots[i]);
        if(env->ExceptionCheck())break;
    }
    ReleaseSRWLockExclusive(&slots_lock);
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
static bool identity(HANDLE file,const std::wstring& requested,std::string& out) {
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
    JNIEnv* env;std::unique_ptr<Transaction>& tx;bool& retained;
    ~StageCleanup(){if(!retained&&tx){retained=true;rollback_and_retire(env,tx);}}
};
static bool start_file_hash(Transaction& tx) {
    LARGE_INTEGER zero{};
    tx.offset=0;
    return SetFilePointerEx(tx.file,zero,nullptr,FILE_BEGIN)&&tx.hashing.open();
}
// The time/byte allowance belongs to one call. Phase, file position and digest belong
// to the original token and are never reset merely because that allowance is spent.
static jlong drive(JNIEnv* env,Transaction& tx) {
    if(tx.phase==Transaction::Ready)return 1;
    constexpr DWORD chunk_size=16384;
    unsigned char buffer[chunk_size];
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
        env->GetByteArrayRegion(chunk,0,count,reinterpret_cast<jbyte*>(buffer));
        return !env->ExceptionCheck();
    };
    while(first||(GetTickCount64()<deadline&&consumed<1024*1024)) {
        first=false;
        switch(tx.phase) {
            case Transaction::CandidateHash: {
                DWORD want=static_cast<DWORD>(std::min<ULONGLONG>(chunk_size,static_cast<ULONGLONG>(tx.image_size)-tx.offset));
                if(want) {
                    if(!candidate(want))return 0;
                    if(!tx.hashing.add(buffer,want))return failure(tx.hashing.error);
                    tx.offset+=want;consumed+=want;break;
                }
                std::string hash;
                if(!tx.hashing.finish(hash))return failure(tx.hashing.error);
                if(hash!=tx.expected)return 0;
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
                std::string file_id;if(!identity(tx.file,tx.requested,file_id)||file_id!=tx.expected_id)return 5;
                if(!start_file_hash(tx))return failure(tx.hashing.error);
                tx.phase=Transaction::BaselineHash;break;
            }
            case Transaction::BaselineHash:
            case Transaction::ExpectedHash: {
                DWORD count=0;if(!ReadFile(tx.file,buffer,chunk_size,&count,nullptr))return failure(0);
                if(count) {
                    if(tx.offset>static_cast<ULONGLONG>(std::numeric_limits<jlong>::max())-count)return failure(ERROR_ARITHMETIC_OVERFLOW);
                    if(!tx.hashing.add(buffer,count))return failure(tx.hashing.error);
                    tx.offset+=count;consumed+=count;break;
                }
                std::string hash;if(!tx.hashing.finish(hash))return failure(tx.hashing.error);
                if(hash!=(tx.phase==Transaction::BaselineHash?tx.baseline:tx.expected))return 5;
                if(tx.phase==Transaction::ExpectedHash) { tx.phase=Transaction::ClosePrepared;break; }
                LARGE_INTEGER zero{};if(!SetFilePointerEx(tx.file,zero,nullptr,FILE_BEGIN))return failure(0);
                tx.offset=0;tx.phase=Transaction::Write;break;
            }
            case Transaction::Write: {
                DWORD want=static_cast<DWORD>(std::min<ULONGLONG>(chunk_size,static_cast<ULONGLONG>(tx.image_size)-tx.offset));
                if(!want){tx.phase=Transaction::Flush;break;}
                if(!candidate(want))return 0;
                DWORD count=0;
                if(injected_failure(env,tx.request,"NATIVE_WRITE")||!WriteFile(tx.file,buffer,want,&count,nullptr))return failure(0);
                if(!count||count>want)return failure(ERROR_WRITE_FAULT);
                tx.offset+=count;consumed+=count;break;
            }
            case Transaction::Flush:
                if(!SetEndOfFile(tx.file)||injected_failure(env,tx.request,"NATIVE_FLUSH")||!FlushFileBuffers(tx.file))return failure(0);
                if(!start_file_hash(tx))return failure(tx.hashing.error);
                tx.phase=Transaction::ExpectedHash;break;
            case Transaction::ClosePrepared: {
                std::string file_id;if(!identity(tx.file,tx.requested,file_id)||file_id!=tx.expected_id)return 5;
                if(!close_file(tx))return failure(tx.error);
                tx.phase=Transaction::Ready;return 1;
            }
            case Transaction::Ready:return 1;
        }
    }
    return 7;
}
static jlongArray advance_owned(JNIEnv* env,std::unique_ptr<Transaction>& tx,bool& retained) {
    jlong status=drive(env,*tx),token=tx->token,error=tx->error;
    auto response=result(env,status,token,error);if(!response)return nullptr;
    if(status==1||status==7){retain_for_retirement(tx);retained=true;}
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
    int previous=admitted.fetch_add(1);if(previous>=2){admitted.fetch_sub(1);return result(env,7,0,ERROR_BUSY);}
    bool retained=false;struct Admission{bool& retained;~Admission(){if(!retained)admitted.fetch_sub(1);}} admission{retained};
    std::wstring requested;std::string expected_id,baseline,expected;
    if(!path(env,request,requested)||!string(env,request,"fileIdentity",expected_id)
        ||!string(env,request,"baselineHash",baseline)||!string(env,request,"expectedHash",expected))return result(env,0);
    auto type=env->GetObjectClass(request);auto getter=type?env->GetMethodID(type,"imageSize","()J"):nullptr;
    if(type)env->DeleteLocalRef(type);
    if(!getter)return nullptr;jlong size=env->CallLongMethod(request,getter);
    if(env->ExceptionCheck()||size<1)return result(env,0);
    auto tx=std::make_unique<Transaction>();
    tx->token=next_token.fetch_add(1);
    if(tx->token<=0||tx->token==std::numeric_limits<jlong>::max())return result(env,6);
    tx->request=env->NewGlobalRef(request);if(!tx->request)return nullptr;
    StageCleanup cleanup{env,tx,retained};
    tx->requested=std::move(requested);tx->expected_id=std::move(expected_id);
    tx->baseline=std::move(baseline);tx->expected=std::move(expected);tx->image_size=size;
    auto request_type=env->GetObjectClass(request);
    auto allocated=request_type?env->GetMethodID(request_type,"nativeAllocated","(J)V"):nullptr;
    if(request_type)env->DeleteLocalRef(request_type);
    if(!allocated)return nullptr;env->CallVoidMethod(request,allocated,tx->token);
    if(env->ExceptionCheck())return nullptr;
    if(!tx->hashing.open())return result(env,6,tx->token,tx->hashing.error);
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
    if(tx->finalized||!live_owner(env,*tx))return result(env,0,token);
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
    if(!close_file(*tx)) {
        jlong error=tx->error;retain_for_retirement(tx);return result(env,0,0,error);
    }
    bool allowed=false;
    if(!tx->finalized&&tx->phase==Transaction::Ready&&permit
        &&exact(env,permit,"dev/ronova/pro/RecoveryStorage$Permit")) {
        auto type=env->GetObjectClass(permit);auto valid=env->GetMethodID(type,"beginCommitFor","(Ldev/ronova/pro/RecoveryStorage$Ticket;)Z");env->DeleteLocalRef(type);
        if(valid)allowed=env->CallBooleanMethod(permit,valid,request)==JNI_TRUE&&!env->ExceptionCheck();
    }
    if(!tx->finalized) {
        tx->finalized=true;
        if(tx->handle==INVALID_HANDLE_VALUE)tx->outcome=0;
        else if(allowed) {if(!injected_failure(env,request,"NATIVE_COMMIT")&&api().commit(tx->handle))tx->outcome=1;else{tx->outcome=3;tx->error=GetLastError();}}
        else {if(api().rollback(tx->handle))tx->outcome=2;else{tx->outcome=3;tx->error=GetLastError();}}
    }
    jlong status=tx->outcome,error=tx->error;
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
