#pragma once
#include "jn_runtime.hpp"
#include "jn_reflection.hpp"
#include <cmath>

namespace jnative {
template<class Owner, class Field> struct NativeFieldAccess {
    Field Owner::* member;
    explicit NativeFieldAccess(Field Owner::* field) : member(field) {}
    auto get(Object* object) const -> decltype((static_cast<Owner*>(object)->*member).get()) {
        return (static_cast<Owner*>(require_non_null(object))->*member).get();
    }
    template<class Value> void set(Object* object, Value value) const {
        (static_cast<Owner*>(require_non_null(object))->*member).set(value);
    }
};

// Built-in helpers know their field at generation time. Encode the member in
// the type so even a non-inlined helper needs no member-pointer argument and
// loads/stores use a constant offset. User native adapters keep the API above.
template<class Owner, class Field, Field Owner::* Member> struct BoundNativeFieldAccess {
    bool exact_owner(Object* object) const {
        return object && object->type_id() == Owner::class_id;
    }
    auto get(Object* object) const -> decltype((static_cast<Owner*>(object)->*Member).get()) {
        return (static_cast<Owner*>(require_non_null(object))->*Member).get();
    }
    template<class Value> void set(Object* object, Value value) const {
        (static_cast<Owner*>(require_non_null(object))->*Member).set(value);
    }
};
}
