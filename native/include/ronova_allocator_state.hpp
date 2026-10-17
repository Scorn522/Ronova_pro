#ifndef RONOVA_ALLOCATOR_STATE_HPP
#define RONOVA_ALLOCATOR_STATE_HPP
#include "ronova_allocator_state.h"
#include <windows.h>
#include <atomic>
#include <cerrno>
#include <cstdlib>
#include <memory_resource>
#include <mutex>
#include <new>
#include <unordered_map>

namespace ronova {
namespace allocator_state_detail {
struct errors {
    DWORD windows=GetLastError();int crt=errno;unsigned long dos=_doserrno;
    void restore()const noexcept {_doserrno=dos;errno=crt;SetLastError(windows);}
    ~errors() noexcept {restore();}
};
inline const ronova_allocator_state_api_v1 *valid(const ronova_allocator_state_api_v1 *api) noexcept {
    return api&&api->abi==RONOVA_ALLOCATOR_STATE_ABI&&api->size>=sizeof(*api)?api:nullptr;
}
inline const ronova_allocator_state_api_v1 *resolve(bool *present=nullptr) noexcept {
    errors saved;auto module=GetModuleHandleW(L"ronova-pro-control.dll");
    auto get=module?reinterpret_cast<ronova_allocator_state_get_api>(GetProcAddress(module,"RonovaAllocatorStateGetApi")):nullptr;
    if(present)*present=get!=nullptr;
    return valid(get?get(RONOVA_ALLOCATOR_STATE_ABI):nullptr);
}
}

/* The upstream resource is borrowed. This adapter never destroys a pool or
 * substitutes a release function. RESET/DESTROY bracket an explicit ORIGINAL
 * pool operation. A resource without observed backing stays uncovered/pending.
 * Do not destroy an upstream pool while its records or pending blocks remain.
 */
class allocator_resource final : public std::pmr::memory_resource {
    struct record {uint64_t receipt=0,generation=0;std::size_t bytes=0,alignment=0;bool sdk=false,busy=false,pending=false;};
    std::pmr::memory_resource *upstream_;
    const ronova_allocator_state_api_v1 *api_=nullptr;
    mutable std::mutex mutex_;
    std::unordered_map<void*,record> records_;
    uint64_t context_=0,epoch_=0;
    bool required_=false;
    std::atomic<std::size_t> gaps_{0};

    const ronova_allocator_state_api_v1 *ensure() noexcept {
        allocator_state_detail::errors saved;
        const ronova_allocator_state_api_v1 *api;uint64_t context;
        {std::lock_guard<std::mutex> lock(mutex_);api=api_;context=context_;}
        if(!api)api=allocator_state_detail::resolve();
        if(api&&!context){
            context=api->context(reinterpret_cast<uintptr_t>(upstream_));
            uint64_t epoch=context?api->context_generation(context):0;
            std::lock_guard<std::mutex> lock(mutex_);api_=api;
            if(!context_&&context&&epoch){context_=context;epoch_=epoch;required_=true;}
        }return api;
    }
    void pending_record(void *pointer) noexcept {
        std::lock_guard<std::mutex> lock(mutex_);auto found=records_.find(pointer);
        if(found!=records_.end()){found->second.busy=false;found->second.pending=true;}
    }
protected:
    void *do_allocate(std::size_t bytes,std::size_t alignment) override {
        allocator_state_detail::errors incoming;
        auto api=ensure();uint64_t context=0,epoch=0;bool required;
        {std::lock_guard<std::mutex> lock(mutex_);context=context_;epoch=epoch_;required=required_;}
        uint64_t call=api&&context?api->allocate_begin(context,epoch,0,0):0;
        if(required&&(!api||!call))throw std::bad_alloc();
        incoming.restore();void *pointer=nullptr;
        try{pointer=upstream_->allocate(bytes,alignment);}
        catch(...){allocator_state_detail::errors original;if(call)api->allocate_end(call,0,0,RONOVA_ALLOCATOR_UNRESOLVED);incoming=original;throw;}
        allocator_state_detail::errors original;
        uint64_t receipt=call?api->allocate_end(call,reinterpret_cast<uintptr_t>(pointer),bytes,RONOVA_ALLOCATOR_SUCCESS):0;
        uint64_t generation=receipt?api->generation(receipt):0;
        record entry;entry.receipt=receipt;entry.generation=generation;entry.bytes=bytes;entry.alignment=alignment;
        entry.sdk=call!=0;entry.pending=entry.sdk&&(!receipt||!generation);
        try{std::lock_guard<std::mutex> lock(mutex_);if(!records_.emplace(pointer,entry).second)gaps_.fetch_add(1);}
        catch(...){gaps_.fetch_add(1);}
        /* The actual block is still returned when bookkeeping cannot retain a
         * receipt. It is an explicit gap, never a claimed observed allocation.
         * No guessed release is called to hide that bookkeeping failure. */
        incoming=original;return pointer;
    }
    void do_deallocate(void *pointer,std::size_t bytes,std::size_t alignment) noexcept override {
        allocator_state_detail::errors incoming;record entry;const ronova_allocator_state_api_v1 *api;
        {std::lock_guard<std::mutex> lock(mutex_);auto found=records_.find(pointer);
            if(found==records_.end()){gaps_.fetch_add(1);return;}
            if(found->second.busy||found->second.bytes!=bytes||found->second.alignment!=alignment){found->second.pending=true;return;}
            found->second.busy=true;entry=found->second;api=api_;}
        uint64_t call=entry.sdk&&api&&entry.receipt?api->free_begin(entry.receipt,entry.generation):0;
        if(entry.sdk&&!call){pending_record(pointer);return;}
        incoming.restore();
        try{upstream_->deallocate(pointer,bytes,alignment);}
        catch(...){allocator_state_detail::errors original;if(call)api->free_end(call,RONOVA_ALLOCATOR_UNRESOLVED);
            pending_record(pointer);incoming=original;return;}
        allocator_state_detail::errors original;
        int closed=!call||api->free_end(call,RONOVA_ALLOCATOR_SUCCESS);
        if(closed){std::lock_guard<std::mutex> lock(mutex_);records_.erase(pointer);}
        else pending_record(pointer);
        incoming=original;
    }
    bool do_is_equal(const std::pmr::memory_resource& other)const noexcept override {return this==&other;}
public:
    explicit allocator_resource(std::pmr::memory_resource *upstream):upstream_(upstream?upstream:std::pmr::get_default_resource()){}
    allocator_resource(std::pmr::memory_resource *upstream,const ronova_allocator_state_api_v1 *api)
            :upstream_(upstream?upstream:std::pmr::get_default_resource()),api_(allocator_state_detail::valid(api)),required_(api_!=nullptr){}
    allocator_resource(const allocator_resource&)=delete;
    allocator_resource& operator=(const allocator_resource&)=delete;
    ~allocator_resource() override=default;
    const ronova_allocator_state_api_v1 *state_api()const noexcept {std::lock_guard<std::mutex> lock(mutex_);return api_;}
    uint64_t context_receipt()const noexcept {std::lock_guard<std::mutex> lock(mutex_);return context_;}
    uint64_t context_generation()const noexcept {std::lock_guard<std::mutex> lock(mutex_);return epoch_;}
    uint64_t current_receipt(void *pointer)const noexcept {
        std::lock_guard<std::mutex> lock(mutex_);auto found=records_.find(pointer);return found==records_.end()?0:found->second.receipt;
    }
    uint64_t current_generation(void *pointer)const noexcept {
        std::lock_guard<std::mutex> lock(mutex_);auto found=records_.find(pointer);return found==records_.end()?0:found->second.generation;
    }
    std::size_t pending()const noexcept {
        std::size_t count=gaps_.load();std::lock_guard<std::mutex> lock(mutex_);
        for(const auto& entry:records_)if(entry.second.pending||entry.second.busy)++count;return count;
    }
    uint64_t bulk_begin(uint32_t operation) noexcept {
        allocator_state_detail::errors saved;auto api=ensure();uint64_t context=context_receipt(),epoch=context_generation();
        return api&&context?api->bulk_begin(context,epoch,operation):0;
    }
    bool bulk_end(uint64_t call,uint32_t outcome) noexcept {
        allocator_state_detail::errors saved;auto api=state_api();if(!api||!call||!api->bulk_end(call,outcome))return false;
        if(outcome==RONOVA_ALLOCATOR_SUCCESS){uint64_t epoch=api->context_generation(context_receipt());
            std::lock_guard<std::mutex> lock(mutex_);epoch_=epoch;if(!epoch)context_=0;records_.clear();gaps_.store(0);}
        return true;
    }
};
}
#endif
