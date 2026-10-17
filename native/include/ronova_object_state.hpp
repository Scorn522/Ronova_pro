#ifndef RONOVA_OBJECT_STATE_HPP
#define RONOVA_OBJECT_STATE_HPP

#include "ronova_object_state.h"
#include <windows.h>
#include <cerrno>
#include <cstddef>
#include <utility>

namespace ronova {
namespace object_state_detail {
/* errno belongs to the caller's CRT provider. Preserve it here as well as the
 * control DLL's own errno so different providers retain their real TLS state. */
struct errors {
    DWORD windows=GetLastError();int crt=errno;
    ~errors() noexcept {errno=crt;SetLastError(windows);}
};
inline const ronova_object_state_api_v1 *resolve() noexcept {
    errors saved;
    auto module=GetModuleHandleW(L"ronova-pro-control.dll");
    auto get=module?reinterpret_cast<ronova_object_state_get_api>(GetProcAddress(module,"RonovaObjectStateGetApi")):nullptr;
    auto api=get?get(RONOVA_OBJECT_STATE_ABI):nullptr;
    return api&&api->abi==RONOVA_OBJECT_STATE_ABI&&api->size>=sizeof(*api)?api:nullptr;
}
}

class object_state {
    const ronova_object_state_api_v1 *api_=nullptr;
    uint64_t object_=0,allocation_=0,generation_=0;
public:
    object_state() noexcept=default;
    object_state(const object_state&)=delete;
    object_state& operator=(const object_state&)=delete;

    bool constructed(void *object,std::size_t bytes) noexcept {
        object_state_detail::errors saved;
        if(object_)return false;
        api_=object_state_detail::resolve();
        object_=api_?api_->constructed(reinterpret_cast<uintptr_t>(object),bytes):0;
        allocation_=object_?api_->allocation(object_):0;
        generation_=object_?api_->generation(object_):0;
        return covered();
    }
    bool covered()const noexcept {return object_&&allocation_&&generation_;}
    uint64_t allocation()const noexcept {return allocation_;}
    uint64_t generation()const noexcept {return generation_;}
    uint64_t bytes(void *address,std::size_t length) noexcept {
        object_state_detail::errors saved;
        return covered()?api_->span(object_,generation_,reinterpret_cast<uintptr_t>(address),length,RONOVA_OBJECT_BYTES):0;
    }
    uint64_t borrowed_object(void *address,std::size_t length) noexcept {
        object_state_detail::errors saved;
        return covered()?api_->span(object_,generation_,reinterpret_cast<uintptr_t>(address),length,RONOVA_OBJECT_BORROWED_OBJECT):0;
    }

    class write_scope {
        const ronova_object_state_api_v1 *api_=nullptr;uint64_t token_=0;
    public:
        write_scope(object_state& owner,uint64_t span) noexcept:api_(owner.api_) {
            object_state_detail::errors saved;
            if(owner.covered()&&span)token_=api_->write_begin(owner.object_,owner.generation_,span);
        }
        write_scope(const write_scope&)=delete;
        write_scope& operator=(const write_scope&)=delete;
        write_scope(write_scope&& other) noexcept:api_(other.api_),token_(std::exchange(other.token_,0)){}
        ~write_scope() noexcept {object_state_detail::errors saved;if(token_)api_->write_end(token_,0);}
        bool observed()const noexcept {return token_!=0;}
        bool complete() noexcept {
            object_state_detail::errors saved;
            return token_&&api_->write_end(std::exchange(token_,0),1)!=0;
        }
    };
    write_scope writing(uint64_t span) noexcept {return write_scope(*this,span);}

    class borrow_scope {
        const ronova_object_state_api_v1 *api_=nullptr;uint64_t token_=0;
    public:
        borrow_scope(object_state& owner,uint64_t span) noexcept:api_(owner.api_) {
            object_state_detail::errors saved;
            if(owner.covered()&&span)token_=api_->borrow_begin(owner.object_,owner.generation_,span);
        }
        borrow_scope(const borrow_scope&)=delete;
        borrow_scope& operator=(const borrow_scope&)=delete;
        borrow_scope(borrow_scope&& other) noexcept:api_(other.api_),token_(std::exchange(other.token_,0)){}
        ~borrow_scope() noexcept {object_state_detail::errors saved;if(token_)api_->borrow_end(token_);}
        bool observed()const noexcept {return token_!=0;}
    };
    borrow_scope borrowing(uint64_t span) noexcept {return borrow_scope(*this,span);}

    class destroy_scope {
        object_state *owner_=nullptr;uint64_t token_=0;
    public:
        explicit destroy_scope(object_state& owner) noexcept:owner_(&owner) {
            object_state_detail::errors saved;
            if(owner.covered())token_=owner.api_->destroy_begin(owner.object_,owner.generation_);
        }
        destroy_scope(const destroy_scope&)=delete;
        destroy_scope& operator=(const destroy_scope&)=delete;
        destroy_scope(destroy_scope&& other) noexcept:owner_(other.owner_),token_(std::exchange(other.token_,0)){}
        ~destroy_scope() noexcept {object_state_detail::errors saved;if(token_)owner_->api_->destroy_end(token_,0);}
        bool admitted()const noexcept {return token_!=0;}
        bool complete() noexcept {
            object_state_detail::errors saved;
            if(!token_||!owner_->api_->destroy_end(std::exchange(token_,0),1))return false;
            owner_->object_=owner_->allocation_=owner_->generation_=0;return true;
        }
    };
    destroy_scope destroying() noexcept {return destroy_scope(*this);}
};
}
#endif
