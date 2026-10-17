#ifndef RONOVA_OBJECT_STATE_HPP
#define RONOVA_OBJECT_STATE_HPP

#include "ronova_object_state.h"
#include <windows.h>
#include <cerrno>
#include <cstddef>
#include <span>
#include <type_traits>
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
    auto api=get?get(RONOVA_OBJECT_STATE_ABI_V2):nullptr;
    if(api&&api->abi==RONOVA_OBJECT_STATE_ABI_V2&&api->size>=sizeof(ronova_object_state_api_v2))return api;
    api=get?get(RONOVA_OBJECT_STATE_ABI):nullptr;
    return api&&api->abi==RONOVA_OBJECT_STATE_ABI&&api->size>=sizeof(*api)?api:nullptr;
}
template<class T> inline constexpr bool scalar=(!std::is_const_v<T>)&&(!std::is_volatile_v<T>)
        &&(std::is_integral_v<T>||std::is_enum_v<T>||std::is_floating_point_v<T>)
        &&(sizeof(T)==1||sizeof(T)==2||sizeof(T)==4||sizeof(T)==8)
        &&(!std::is_floating_point_v<T>||sizeof(T)==4||sizeof(T)==8);
template<class T> constexpr uint32_t scalar_kind() noexcept {
    static_assert(scalar<T>,"Expose a non-atomic arithmetic/enum member; borrow pointer, atomic, handle or class state.");
    if constexpr(std::is_same_v<T,bool>)return RONOVA_FIELD_BOOL;
    else if constexpr(std::is_same_v<T,std::byte>)return RONOVA_FIELD_BYTE;
    else if constexpr(std::is_enum_v<T>)return std::is_signed_v<std::underlying_type_t<T>>?RONOVA_FIELD_ENUM_SIGNED:RONOVA_FIELD_ENUM_UNSIGNED;
    else if constexpr(std::is_floating_point_v<T>)return RONOVA_FIELD_FLOAT;
    else return std::is_signed_v<T>?RONOVA_FIELD_SIGNED:RONOVA_FIELD_UNSIGNED;
}
}

class object_state;
template<class T> class scalar_field {
    friend class object_state;
    object_state *owner_=nullptr;T *address_=nullptr;
    uint64_t object_=0,generation_=0,token_=0,binding_=0;
public:
    scalar_field() noexcept=default;
    uint64_t receipt()const noexcept{return token_;}
    bool covered()const noexcept{return token_!=0;}
};
template<class T> class buffer_field {
    friend class object_state;
    object_state *owner_=nullptr;T *address_=nullptr;std::size_t size_=0;
    uint64_t object_=0,generation_=0,token_=0,binding_=0;
public:
    buffer_field() noexcept=default;
    uint64_t receipt()const noexcept{return token_;}
    std::size_t size()const noexcept{return size_;}
    bool covered()const noexcept{return token_!=0;}
};

class object_state {
    const ronova_object_state_api_v1 *api_=nullptr;
    const ronova_object_state_api_v2 *typed_=nullptr;
    uint64_t object_=0,allocation_=0,generation_=0,binding_=0;
    uintptr_t address_=0;std::size_t bytes_=0;
    bool constrained_=false;
    bool contains(const void *address,std::size_t bytes)const noexcept {
        auto actual=reinterpret_cast<uintptr_t>(address);
        return address_&&actual>=address_&&bytes<=bytes_&&actual-address_<=bytes_-bytes;
    }
    object_state(const ronova_object_state_api_v1 *api,const ronova_object_state_api_v2 *typed,
            uint64_t token,void *address,std::size_t bytes,bool constrained) noexcept
            :api_(api),typed_(typed),object_(token),binding_(1),address_(reinterpret_cast<uintptr_t>(address)),bytes_(bytes),constrained_(constrained) {
        allocation_=token?api_->allocation(token):0;generation_=token?api_->generation(token):0;
    }
    template<class T> buffer_field<T> buffer(std::span<T> values,uint32_t relation) noexcept {
        object_state_detail::errors saved;buffer_field<T> result;
        result.owner_=this;result.address_=values.data();result.size_=values.size();result.object_=object_;
        result.generation_=generation_;result.binding_=binding_;
        if(covered()&&typed_&&!values.empty())result.token_=typed_->buffer(object_,generation_,
                reinterpret_cast<uintptr_t>(values.data()),values.size_bytes(),object_state_detail::scalar_kind<std::remove_cv_t<T>>(),sizeof(T),relation);
        return result;
    }
public:
    object_state() noexcept=default;
    object_state(const object_state&)=delete;
    object_state& operator=(const object_state&)=delete;
    object_state(object_state&& other) noexcept:api_(other.api_),typed_(other.typed_),
            object_(std::exchange(other.object_,0)),allocation_(std::exchange(other.allocation_,0)),
            generation_(std::exchange(other.generation_,0)),binding_(other.binding_),
            address_(std::exchange(other.address_,0)),bytes_(std::exchange(other.bytes_,0)),constrained_(other.constrained_) {other.binding_++;}

    bool constructed(void *object,std::size_t bytes) noexcept {
        object_state_detail::errors saved;return constructed(object,bytes,object_state_detail::resolve());
    }
    bool constructed(void *object,std::size_t bytes,const ronova_object_state_api_v1 *actualApi) noexcept {
        object_state_detail::errors saved;
        if(object_||address_||!object||!bytes||binding_==UINT64_MAX)return false;
        address_=reinterpret_cast<uintptr_t>(object);bytes_=bytes;binding_++;
        api_=actualApi&&((actualApi->abi==RONOVA_OBJECT_STATE_ABI&&actualApi->size>=sizeof(*actualApi))
                ||(actualApi->abi==RONOVA_OBJECT_STATE_ABI_V2&&actualApi->size>=sizeof(ronova_object_state_api_v2)))?actualApi:nullptr;
        typed_=api_&&api_->abi==RONOVA_OBJECT_STATE_ABI_V2?reinterpret_cast<const ronova_object_state_api_v2*>(api_):nullptr;
        object_=api_?api_->constructed(reinterpret_cast<uintptr_t>(object),bytes):0;
        allocation_=object_?api_->allocation(object_):0;
        generation_=object_?api_->generation(object_):0;
        return covered();
    }
    bool covered()const noexcept {return object_&&allocation_&&generation_;}
    uint64_t allocation()const noexcept {return allocation_;}
    uint64_t generation()const noexcept {return generation_;}
    bool typed()const noexcept {return covered()&&typed_;}
    template<class T> bool constructed(T& object) noexcept {return constructed(&object,sizeof(T));}
    /* Member pointers compute a real live member address. Neither offset,
     * padding, vptr nor a class representation is inferred. Integer-encoded
     * ownership handles are a semantic exclusion too: expose them as borrowed
     * state even when their typedef has an arithmetic underlying type. */
    template<class T> scalar_field<T> scalar(T& value) noexcept {
        static_assert(object_state_detail::scalar<T>);
        object_state_detail::errors saved;scalar_field<T> result;
        if(!contains(&value,sizeof(T)))return result;
        result.owner_=this;result.address_=&value;result.object_=object_;result.generation_=generation_;result.binding_=binding_;
        if(covered()&&typed_)result.token_=typed_->field(object_,generation_,reinterpret_cast<uintptr_t>(&value),sizeof(T),
                object_state_detail::scalar_kind<T>());
        return result;
    }
    template<class Owner,class T> scalar_field<T> field(Owner& object,T Owner::*member) noexcept {
        if(reinterpret_cast<uintptr_t>(&object)!=address_||sizeof(Owner)!=bytes_)return {};
        return scalar(object.*member);
    }
    template<class T> object_state nested(T& object) noexcept {
        object_state_detail::errors saved;
        auto token=typed_&&covered()&&contains(&object,sizeof(T))?typed_->nested(object_,generation_,reinterpret_cast<uintptr_t>(&object),sizeof(T)):0;
        return object_state(api_,typed_,token,&object,sizeof(T),covered()||constrained_);
    }
    template<class T> uint64_t epoch(const scalar_field<T>& field) noexcept {
        object_state_detail::errors saved;
        return typed_&&field.owner_==this&&field.object_==object_&&field.generation_==generation_&&field.binding_==binding_
                ?typed_->epoch(object_,generation_,field.token_):0;
    }
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
            owner_->object_=owner_->allocation_=owner_->generation_=0;owner_->address_=0;owner_->bytes_=0;owner_->binding_++;return true;
        }
    };
    destroy_scope destroying() noexcept {return destroy_scope(*this);}
    template<class T> bool set(const scalar_field<T>& field,const T& value) noexcept {
        static_assert(object_state_detail::scalar<T>);object_state_detail::errors saved;
        if(field.owner_!=this||field.object_!=object_||field.generation_!=generation_||field.binding_!=binding_
                ||!field.address_||!contains(field.address_,sizeof(T)))return false;
        if(!covered()){if(constrained_)return false;*field.address_=value;return true;}
        if(!typed_||!field.token_)return false;
        auto write=writing(field.token_);if(!write.observed())return false;
        *field.address_=value;return write.complete();
    }
    template<class T> buffer_field<T> owned_buffer(std::span<T> values) noexcept {
        static_assert(object_state_detail::scalar<T>);return buffer(values,RONOVA_BUFFER_OWNED);
    }
    template<class T> buffer_field<T> borrowed_buffer(std::span<T> values) noexcept {
        static_assert(!std::is_volatile_v<T>&&object_state_detail::scalar<std::remove_cv_t<T>>);return buffer(values,RONOVA_BUFFER_BORROWED);
    }
    template<class T> bool release(buffer_field<T>& buffer) noexcept {
        object_state_detail::errors saved;
        if(buffer.owner_!=this||buffer.object_!=object_||buffer.generation_!=generation_||buffer.binding_!=binding_)return false;
        if(!covered()){if(constrained_)return false;buffer.token_=0;buffer.owner_=nullptr;return true;}
        if(!typed_||!buffer.token_||!typed_->buffer_end(object_,generation_,buffer.token_))return false;
        buffer.token_=0;buffer.owner_=nullptr;return true;
    }
    template<class T> bool assign(const buffer_field<T>& buffer,std::span<const T> values) noexcept {
        static_assert(object_state_detail::scalar<T>);object_state_detail::errors saved;
        if(buffer.owner_!=this||buffer.object_!=object_||buffer.generation_!=generation_||buffer.binding_!=binding_
                ||buffer.size_!=values.size()||(!values.empty()&&!buffer.address_))return false;
        if(!covered()){if(constrained_)return false;for(std::size_t i=0;i<values.size();i++)buffer.address_[i]=values[i];return true;}
        if(!typed_||!buffer.token_)return false;
        auto write=writing(buffer.token_);if(!write.observed())return false;
        for(std::size_t i=0;i<values.size();i++)buffer.address_[i]=values[i];return write.complete();
    }
};
}
#endif
