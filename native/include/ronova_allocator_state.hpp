#ifndef RONOVA_ALLOCATOR_STATE_HPP
#define RONOVA_ALLOCATOR_STATE_HPP
#include "ronova_allocator_state.h"
#include <windows.h>
#include <atomic>
#include <cerrno>
#include <cstdlib>
#include <limits>
#include <memory>
#include <memory_resource>
#include <mutex>
#include <new>
#include <type_traits>
#include <unordered_map>
#include <utility>

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
        catch(const std::bad_alloc&){allocator_state_detail::errors original;if(call)api->allocate_end(call,0,0,RONOVA_ALLOCATOR_FAILED);incoming=original;throw;}
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

namespace allocator_state_detail {
struct allocator_record {uint64_t receipt=0,generation=0;std::size_t bytes=0,alignment=0;bool sdk=false,busy=false,pending=false;};
/* This is the adapter's actual shared state, not an inferred upstream pool.
 * Its identity survives allocator copies/rebinds. No original allocator is
 * stored as a cleanup callback, and closing an empty state frees no storage. */
class shared_allocator_state {
    mutable std::mutex mutex_;
    const ronova_allocator_state_api_v1 *api_=nullptr;
    std::unordered_map<void*,allocator_record> records_;
    uint64_t context_=0,epoch_=0;
    std::size_t gaps_=0,inflight_=0;
    bool required_=false,closed_=false,closing_=false;
public:
    explicit shared_allocator_state(const ronova_allocator_state_api_v1 *api=nullptr)
            :api_(valid(api)),required_(api_!=nullptr){}
    const ronova_allocator_state_api_v1 *ensure() noexcept {
        errors saved;const ronova_allocator_state_api_v1 *api;uint64_t context;
        {std::lock_guard<std::mutex> lock(mutex_);if(closed_||closing_)return nullptr;api=api_;context=context_;}
        if(!api)api=resolve();
        if(api&&!context){
            context=api->context(reinterpret_cast<uintptr_t>(this));uint64_t epoch=context?api->context_generation(context):0;
            std::lock_guard<std::mutex> lock(mutex_);api_=api;
            if(!context_&&context&&epoch){context_=context;epoch_=epoch;required_=true;}
        }return api;
    }
    uint64_t begin(const ronova_allocator_state_api_v1 *&api){
        errors saved;uint64_t context,epoch;bool required;
        {std::lock_guard<std::mutex> lock(mutex_);if(closed_||closing_)throw std::bad_alloc();++inflight_;}
        api=ensure();
        {std::lock_guard<std::mutex> lock(mutex_);context=context_;epoch=epoch_;required=required_;}
        uint64_t call=api&&context?api->allocate_begin(context,epoch,0,0):0;
        if(required&&(!api||!call)){std::lock_guard<std::mutex> lock(mutex_);--inflight_;throw std::bad_alloc();}
        return call;
    }
    void allocation_failed(bool unresolved) noexcept {std::lock_guard<std::mutex> lock(mutex_);if(unresolved)++gaps_;--inflight_;}
    void allocated(void *pointer,std::size_t bytes,std::size_t alignment,const ronova_allocator_state_api_v1 *api,uint64_t call) noexcept {
        errors saved;allocator_record record;record.bytes=bytes;record.alignment=alignment;
        record.receipt=call?api->allocate_end(call,reinterpret_cast<uintptr_t>(pointer),bytes,RONOVA_ALLOCATOR_SUCCESS):0;
        record.generation=record.receipt?api->generation(record.receipt):0;
        record.sdk=call!=0&&(pointer||bytes);record.pending=record.sdk&&(!record.receipt||!record.generation);
        if(!pointer&&!bytes){std::lock_guard<std::mutex> lock(mutex_);--inflight_;return;}
        try{std::lock_guard<std::mutex> lock(mutex_);if(!records_.emplace(pointer,record).second)++gaps_;--inflight_;}
        catch(...){std::lock_guard<std::mutex> lock(mutex_);++gaps_;--inflight_;}
    }
    bool releasing(void *pointer,std::size_t bytes,std::size_t alignment,allocator_record &record,
            const ronova_allocator_state_api_v1 *&api,uint64_t &call) noexcept {
        errors saved;
        if(!pointer&&!bytes){std::lock_guard<std::mutex> lock(mutex_);++inflight_;return true;}
        {std::lock_guard<std::mutex> lock(mutex_);auto found=records_.find(pointer);
            if(found==records_.end()){++gaps_;return false;}
            if(closed_||closing_||found->second.busy||found->second.bytes!=bytes||found->second.alignment!=alignment){found->second.pending=true;return false;}
            found->second.busy=true;record=found->second;api=api_;++inflight_;}
        call=record.sdk&&api&&record.receipt?api->free_begin(record.receipt,record.generation):0;
        if(record.sdk&&!call){released(pointer,false);return false;}return true;
    }
    void released(void *pointer,bool complete) noexcept {
        std::lock_guard<std::mutex> lock(mutex_);auto found=records_.find(pointer);
        if(found!=records_.end()){if(complete)records_.erase(found);else {found->second.busy=false;found->second.pending=true;}}
        --inflight_;
    }
    uint64_t context_receipt()const noexcept {std::lock_guard<std::mutex> lock(mutex_);return context_;}
    uint64_t context_generation()const noexcept {std::lock_guard<std::mutex> lock(mutex_);return epoch_;}
    uint64_t current(void *pointer,bool generation)const noexcept {
        std::lock_guard<std::mutex> lock(mutex_);auto found=records_.find(pointer);
        return found==records_.end()?0:generation?found->second.generation:found->second.receipt;
    }
    std::size_t pending()const noexcept {
        std::lock_guard<std::mutex> lock(mutex_);std::size_t count=gaps_;
        for(const auto& record:records_)if(record.second.busy||record.second.pending)++count;return count;
    }
    bool close() noexcept {
        errors saved;const ronova_allocator_state_api_v1 *api;uint64_t context,epoch;
        {std::lock_guard<std::mutex> lock(mutex_);if(closed_)return true;
            if(closing_||inflight_||gaps_||!records_.empty())return false;
            closing_=true;api=api_;context=context_;epoch=epoch_;}
        /* The actual adapter state closes here after every original block
         * deallocation. This is not a destroy operation on its upstream. */
        uint64_t call=api&&context?api->bulk_begin(context,epoch,RONOVA_ALLOCATOR_DESTROY):0;
        bool complete=!context||call&&api->bulk_end(call,RONOVA_ALLOCATOR_SUCCESS);
        {std::lock_guard<std::mutex> lock(mutex_);closing_=false;
            if(complete){closed_=true;context_=epoch_=0;}}
        return complete;
    }
};
}

class allocator_state {
    std::shared_ptr<allocator_state_detail::shared_allocator_state> state_;
    template<class,class> friend class allocator_adapter;
public:
    allocator_state():allocator_state(nullptr){}
    explicit allocator_state(const ronova_allocator_state_api_v1 *api)
            :state_(new allocator_state_detail::shared_allocator_state(api),[](auto *state) noexcept {
                /* A refused/uncertain original still owns live storage. Keep
                 * this actual state identity instead of reusing its address. */
                if(state->close())delete state;
            }){}
    allocator_state(const allocator_state&) noexcept=default;
    allocator_state& operator=(const allocator_state&) noexcept=default;
    allocator_state(allocator_state&& other) noexcept:state_(other.state_){}
    allocator_state& operator=(allocator_state&& other) noexcept {state_=other.state_;return *this;}
    uint64_t context_receipt()const noexcept {return state_->context_receipt();}
    uint64_t context_generation()const noexcept {return state_->context_generation();}
    uint64_t current_receipt(void *pointer)const noexcept {return state_->current(pointer,false);}
    uint64_t current_generation(void *pointer)const noexcept {return state_->current(pointer,true);}
    std::size_t pending()const noexcept {return state_->pending();}
    bool close() noexcept {return state_->close();}
};

/* A standard allocator adapter for a real raw-pointer upstream allocator.
 * Copies and rebinds retain one actual state. Equality additionally requires
 * upstream equality; a moved-from adapter remains equal and usable. Fancy
 * pointer/scoped allocator contracts require their own explicit adapter. */
template<class T,class Upstream=std::allocator<T>> class allocator_adapter {
    template<class,class> friend class allocator_adapter;
    using traits=std::allocator_traits<Upstream>;
    allocator_state state_;
    Upstream upstream_;
    static_assert(std::is_same_v<typename traits::value_type,T>,"upstream allocator value_type must match T");
    static_assert(std::is_same_v<typename traits::pointer,T*>,"allocator_adapter requires a raw-pointer upstream");
    static_assert(std::is_nothrow_copy_constructible_v<Upstream>,"allocator copies must not throw");
public:
    using value_type=T;using pointer=T*;using const_pointer=const T*;
    using size_type=std::size_t;using difference_type=std::ptrdiff_t;
    using propagate_on_container_copy_assignment=std::false_type;
    using propagate_on_container_move_assignment=std::false_type;
    using propagate_on_container_swap=std::false_type;
    using is_always_equal=std::false_type;
    template<class U>struct rebind {using other=allocator_adapter<U,typename traits::template rebind_alloc<U>>;};
    allocator_adapter()=default;
    explicit allocator_adapter(const allocator_state& state,const Upstream& upstream=Upstream()) noexcept
            :state_(state),upstream_(upstream){}
    allocator_adapter(const allocator_adapter&) noexcept=default;
    allocator_adapter& operator=(const allocator_adapter&) noexcept=default;
    allocator_adapter(allocator_adapter&& other) noexcept:state_(other.state_),upstream_(other.upstream_){}
    allocator_adapter& operator=(allocator_adapter&& other) noexcept {state_=other.state_;upstream_=other.upstream_;return *this;}
    template<class U,class Other>allocator_adapter(const allocator_adapter<U,Other>& other) noexcept
            :state_(other.state_),upstream_(other.upstream_){static_assert(std::is_nothrow_constructible_v<Upstream,const Other&>,"allocator rebind conversion must not throw");}
    pointer allocate(size_type count){
        allocator_state_detail::errors incoming;
        if(count>std::numeric_limits<size_type>::max()/sizeof(T)||count>traits::max_size(upstream_))throw std::bad_array_new_length();
        const ronova_allocator_state_api_v1 *api=nullptr;uint64_t call=state_.state_->begin(api);
        incoming.restore();pointer result;
        try{result=traits::allocate(upstream_,count);}
        catch(const std::bad_alloc&){allocator_state_detail::errors original;if(call)api->allocate_end(call,0,0,RONOVA_ALLOCATOR_FAILED);
            state_.state_->allocation_failed(false);incoming=original;throw;}
        catch(...){allocator_state_detail::errors original;if(call)api->allocate_end(call,0,0,RONOVA_ALLOCATOR_UNRESOLVED);
            state_.state_->allocation_failed(call!=0);incoming=original;throw;}
        allocator_state_detail::errors original;
        state_.state_->allocated(result,count*sizeof(T),alignof(T),api,call);incoming=original;return result;
    }
    void deallocate(pointer pointer,size_type count) noexcept {
        allocator_state_detail::errors incoming;
        if(count>std::numeric_limits<size_type>::max()/sizeof(T))return;
        allocator_state_detail::allocator_record record;const ronova_allocator_state_api_v1 *api=nullptr;uint64_t call=0;
        if(!state_.state_->releasing(pointer,count*sizeof(T),alignof(T),record,api,call))return;
        incoming.restore();
        try{traits::deallocate(upstream_,pointer,count);}
        catch(...){allocator_state_detail::errors original;if(call)api->free_end(call,RONOVA_ALLOCATOR_UNRESOLVED);
            state_.state_->released(pointer,false);incoming=original;return;}
        allocator_state_detail::errors original;bool complete=!call||api->free_end(call,RONOVA_ALLOCATOR_SUCCESS);
        state_.state_->released(pointer,complete);incoming=original;
    }
    template<class U,class...Args>void construct(U *pointer,Args&&...args){traits::construct(upstream_,pointer,std::forward<Args>(args)...);}
    template<class U>void destroy(U *pointer){traits::destroy(upstream_,pointer);}
    size_type max_size()const noexcept {return traits::max_size(upstream_);}
    allocator_adapter select_on_container_copy_construction()const {
        return allocator_adapter(state_,traits::select_on_container_copy_construction(upstream_));
    }
    const allocator_state& state()const noexcept {return state_;}
    template<class U,class Other>bool operator==(const allocator_adapter<U,Other>& other)const noexcept {
        return state_.state_==other.state_.state_&&upstream_==other.upstream_;
    }
    template<class U,class Other>bool operator!=(const allocator_adapter<U,Other>& other)const noexcept {return !(*this==other);}
};
}
#endif
