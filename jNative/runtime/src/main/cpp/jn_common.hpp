#pragma once
#include <memory>
#include <utility>
#include <type_traits>

// Header-defined helpers expose their bodies to every caller.
#if defined(_MSC_VER)
#define JNATIVE_FORCEINLINE __forceinline
#elif defined(__GNUC__) || defined(__clang__)
#define JNATIVE_FORCEINLINE inline __attribute__((always_inline))
#else
#define JNATIVE_FORCEINLINE inline
#endif

// Generated internal entries are called only within their owning translation unit.
// Other compilers keep ordinary linkage rather than requiring inline body visibility.
#if defined(_MSC_VER)
#define JNATIVE_INLINE_ENTRY __forceinline
#else
#define JNATIVE_INLINE_ENTRY
#endif

// Keep cold implementations outside their callers' inline fast paths.
#if defined(_MSC_VER)
#define JNATIVE_NOINLINE __declspec(noinline)
#elif defined(__GNUC__) || defined(__clang__)
#define JNATIVE_NOINLINE __attribute__((noinline))
#else
#define JNATIVE_NOINLINE
#endif

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
