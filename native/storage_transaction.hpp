// Optional Windows NTFS transaction backend. Staging never commits public data.
#include <bcrypt.h>
#include <ktmw32.h>
#include <memory>
#include <limits>
#include <map>

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
    Hash hashing;
    HANDLE handle=INVALID_HANDLE_VALUE,file=INVALID_HANDLE_VALUE;
    jobject request=nullptr;jlong token=0;
    ULONGLONG expires=0;bool finalized=false;jlong outcome=0,error=0;
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
static void remember_retirement(JNIEnv* env,Transaction& tx) {
    if(!tx.request||env->ExceptionCheck())return;
    auto type=env->GetObjectClass(tx.request);
    auto method=type?env->GetMethodID(type,"nativeRetired","(JJZJ)V"):nullptr;
    if(type)env->DeleteLocalRef(type);
    if(method)env->CallVoidMethod(tx.request,method,tx.token,tx.outcome,JNI_TRUE,tx.error);
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
        remember_retirement(env,*tx);
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
static jlong reap_expired(JNIEnv* env) {
    std::unique_ptr<Transaction> expired[2];ULONGLONG now=GetTickCount64();jlong reaped=0;
    AcquireSRWLockExclusive(&slots_lock);
    for(int i=0;i<2;i++)if(slots[i]&&now>=slots[i]->expires)expired[i]=std::move(slots[i]);
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
static bool hash_file(Transaction& tx,std::string& hash,ULONGLONG deadline) {
    HANDLE file=tx.file;
    LARGE_INTEGER zero{};if(!SetFilePointerEx(file,zero,nullptr,FILE_BEGIN))return false;
    Hash& digest=tx.hashing;if(!digest.open())return false;unsigned char buffer[16384];size_t total=0;
    for(;;){if(GetTickCount64()>=deadline)return false;DWORD count=0;if(!ReadFile(file,buffer,sizeof(buffer),&count,nullptr))return false;
        if(!count)break;total+=count;if(total>16*1024*1024||!digest.add(buffer,count))return false;}
    return digest.finish(hash);
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

// Result: status, opaque token, Win32 error. Token is also bound to the exact private-created request object.
extern "C" JNIEXPORT jlongArray JNICALL
Java_dev_ronova_pro_StorageNative_prepare0(JNIEnv* env,jclass,jobject request) try {
    using namespace recovery_storage;
    if(!api().supported())return result(env,4);
    if(!exact(env,request,"dev/ronova/pro/RecoveryStorage$Ticket"))return result(env,0);
    reap_expired(env);
    int previous=admitted.fetch_add(1);if(previous>=2){admitted.fetch_sub(1);return result(env,6,0,ERROR_BUSY);}
    bool retained=false;struct Admission{bool& retained;~Admission(){if(!retained)admitted.fetch_sub(1);}} admission{retained};
    std::wstring requested;std::string expected_id,baseline,expected;
    if(!path(env,request,requested)||!string(env,request,"fileIdentity",expected_id)
        ||!string(env,request,"baselineHash",baseline)||!string(env,request,"expectedHash",expected))return result(env,0);
    auto type=env->GetObjectClass(request);auto getter=env->GetMethodID(type,"encoded","()[B");env->DeleteLocalRef(type);
    if(!getter)return result(env,0);auto bytes=static_cast<jbyteArray>(env->CallObjectMethod(request,getter));
    if(env->ExceptionCheck()||!bytes)return result(env,0);jsize size=env->GetArrayLength(bytes);
    if(size<1||size>16*1024*1024){env->DeleteLocalRef(bytes);return result(env,0);}
    std::vector<unsigned char> image(size);env->GetByteArrayRegion(bytes,0,size,reinterpret_cast<jbyte*>(image.data()));env->DeleteLocalRef(bytes);
    if(env->ExceptionCheck())return nullptr;
    auto tx=std::make_unique<Transaction>();
    tx->token=next_token.fetch_add(1);
    if(tx->token<=0||tx->token==std::numeric_limits<jlong>::max())return result(env,6);
    tx->request=env->NewGlobalRef(request);if(!tx->request)return nullptr;
    // Every return after allocation retires under the same original token. Admission is
    // transferred either to the retained slot or to release(); never decremented twice.
    struct StageCleanup {
        JNIEnv* env;std::unique_ptr<Transaction>& tx;bool& retained;
        ~StageCleanup(){if(!retained&&tx){retained=true;rollback_and_retire(env,tx);}}
    } cleanup{env,tx,retained};
    // Publish allocation to the exact private ticket before any fallible staging I/O.
    auto request_type=env->GetObjectClass(request);
    auto allocated=request_type?env->GetMethodID(request_type,"nativeAllocated","(J)V"):nullptr;
    if(request_type)env->DeleteLocalRef(request_type);
    if(!allocated)return nullptr;
    env->CallVoidMethod(request,allocated,tx->token);
    if(env->ExceptionCheck())return nullptr;
    Hash& encoded=tx->hashing;std::string actual;
    if(!encoded.open()||!encoded.add(image.data(),size)||!encoded.finish(actual)||actual!=expected)return result(env,0,tx->token);
    tx->expires=GetTickCount64()+5000;
    tx->handle=api().create(nullptr,nullptr,0,0,0,5000,const_cast<wchar_t*>(L"Ronova registered recovery storage"));
    if(tx->handle==INVALID_HANDLE_VALUE)return result(env,4,tx->token,GetLastError());
    GUID guid{};if(!api().id(tx->handle,&guid))return result(env,6,tx->token,GetLastError());
    char transaction_id[33];static const char hex[]="0123456789abcdef";
    const auto raw=reinterpret_cast<const unsigned char*>(&guid);
    for(int i=0;i<16;i++){transaction_id[2*i]=hex[raw[i]>>4];transaction_id[2*i+1]=hex[raw[i]&15];}transaction_id[32]=0;
    request_type=env->GetObjectClass(request);
    auto identify=env->GetMethodID(request_type,"nativeTransaction","(Ljava/lang/String;)V");env->DeleteLocalRef(request_type);
    if(!identify)return nullptr;
    auto identity_text=env->NewStringUTF(transaction_id);if(!identity_text)return nullptr;
    env->CallVoidMethod(request,identify,identity_text);env->DeleteLocalRef(identity_text);
    if(env->ExceptionCheck())return nullptr;
    tx->file=api().open(requested.c_str(),GENERIC_READ|GENERIC_WRITE,0,nullptr,OPEN_EXISTING,FILE_FLAG_OPEN_REPARSE_POINT,nullptr,tx->handle,nullptr,nullptr);
    if(tx->file==INVALID_HANDLE_VALUE)return result(env,6,tx->token,GetLastError());
    ULONGLONG deadline=GetTickCount64()+4000;std::string file_id;
    if(!identity(tx->file,requested,file_id)||file_id!=expected_id)return result(env,5,tx->token);
    if(!hash_file(*tx,actual,deadline))return result(env,6,tx->token,GetLastError());
    if(actual!=baseline)return result(env,5,tx->token);
    LARGE_INTEGER zero{};if(!SetFilePointerEx(tx->file,zero,nullptr,FILE_BEGIN))return result(env,6,tx->token,GetLastError());
    for(size_t offset=0;offset<image.size();) {
        if(GetTickCount64()>=deadline)return result(env,6,tx->token,WAIT_TIMEOUT);
        DWORD count=0,want=static_cast<DWORD>((image.size()-offset)>16384?16384:(image.size()-offset));
        if(injected_failure(env,request,"NATIVE_WRITE")||!WriteFile(tx->file,image.data()+offset,want,&count,nullptr)||count!=want)return result(env,6,tx->token,GetLastError());offset+=count;
    }
    if(!SetEndOfFile(tx->file)||injected_failure(env,request,"NATIVE_FLUSH")||!FlushFileBuffers(tx->file)||!hash_file(*tx,actual,deadline))return result(env,6,tx->token,GetLastError());
    if(actual!=expected||!identity(tx->file,requested,file_id)||file_id!=expected_id)return result(env,5,tx->token);
    // Windows requires transaction file handles to be closed before commit/rollback.
    if(!close_file(*tx))return result(env,6,tx->token,tx->error);
    jlong token=tx->token;auto response=result(env,1,token);if(!response)return nullptr;
    AcquireSRWLockExclusive(&slots_lock);
    for(auto& slot:slots)if(!slot){slot=std::move(tx);retained=true;break;}
    ReleaseSRWLockExclusive(&slots_lock);
    if(!retained)return result(env,6,token,ERROR_BUSY);
    return response;
} catch(...) {
    if(!env->ExceptionCheck()){auto error=env->FindClass("java/lang/IllegalStateException");if(error){env->ThrowNew(error,"Native transaction preparation unavailable");env->DeleteLocalRef(error);}}
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
    if(!tx->finalized&&GetTickCount64()<tx->expires&&permit
        &&exact(env,permit,"dev/ronova/pro/RecoveryStorage$Permit")) {
        auto type=env->GetObjectClass(permit);auto valid=env->GetMethodID(type,"beginCommitFor","(Ldev/ronova/pro/RecoveryStorage$Ticket;)Z");env->DeleteLocalRef(type);
        if(valid)allowed=env->CallBooleanMethod(permit,valid,request)==JNI_TRUE&&!env->ExceptionCheck();
    }
    if(!tx->finalized) {
        tx->finalized=true;
        if(allowed) {if(!injected_failure(env,request,"NATIVE_COMMIT")&&api().commit(tx->handle))tx->outcome=1;else{tx->outcome=3;tx->error=GetLastError();}}
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
