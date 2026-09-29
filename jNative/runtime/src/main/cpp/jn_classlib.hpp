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
}
