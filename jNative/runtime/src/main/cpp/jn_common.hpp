#pragma once
#include <memory>
#include <utility>
#include <type_traits>

namespace jnative {
template<class T, class... Args> std::unique_ptr<T> make_owned(Args&&... args) {
#if JNATIVE_USE_MAKE_UNIQUE
    return std::make_unique<T>(std::forward<Args>(args)...);
#else
    return std::unique_ptr<T>(new T(std::forward<Args>(args)...));
#endif
}
template<class T, class U> T take_value(T& value, U replacement) {
    T previous = std::move(value);
    value = std::forward<U>(replacement);
    return previous;
}
template<class T> std::unique_ptr<T[]> make_array(std::size_t count) {
    if (count > static_cast<std::size_t>(-1) / sizeof(T)) throw std::bad_array_new_length();
    return std::unique_ptr<T[]>(new T[count]());
}
}
