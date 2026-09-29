#pragma once
// Private desktop-provider implementation, never included by generated classes.
#include "jn_platform.hpp"
#include <cfenv>
#include <cstdio>
#include <cstring>
#include <new>
#if defined(__SSE2__) || defined(_M_X64)
#include <xmmintrin.h>
#endif
#if defined(JNATIVE_X64_FLOATING)
extern "C" void jn_save_floating_x64(void*) noexcept;
extern "C" void jn_restore_floating_x64(const void*) noexcept;
extern "C" void jn_java_floating_x64(const void*) noexcept;
#endif
namespace jnative { namespace platform {
void*& managed_context() noexcept {
    static thread_local void* context = nullptr;
    return context;
}
static_assert(sizeof(std::fenv_t) <= sizeof(FloatingState), "Floating state storage is too small");
void save_floating(FloatingState& state) noexcept {
#if defined(JNATIVE_X64_FLOATING)
    jn_save_floating_x64(state.bytes);
#elif defined(__x86_64__) && (defined(__GNUC__) || defined(__clang__))
    __asm__ __volatile__("fnstenv %0; fldenv %0" : "=m" (state.bytes) : : "memory");
    unsigned int control = _mm_getcsr();
    std::memcpy(state.bytes + 28, &control, sizeof(control));
#else
    std::fenv_t environment;
    std::fegetenv(&environment);
    std::memcpy(state.bytes, &environment, sizeof(environment));
#endif
}
void restore_floating(const FloatingState& state) noexcept {
#if defined(JNATIVE_X64_FLOATING)
    jn_restore_floating_x64(state.bytes);
#elif defined(__x86_64__) && (defined(__GNUC__) || defined(__clang__))
    __asm__ __volatile__("fldenv %0" : : "m" (state.bytes) : "memory");
    unsigned int control;
    std::memcpy(&control, state.bytes + 28, sizeof(control));
    _mm_setcsr(control);
#else
    std::fenv_t environment;
    std::memcpy(&environment, state.bytes, sizeof(environment));
    std::fesetenv(&environment);
#endif
}
void java_floating() noexcept {
#if defined(JNATIVE_X64_FLOATING) || (defined(__x86_64__) && (defined(__GNUC__) || defined(__clang__)))
    // Capture the platform's exact default once, including its x87 precision
    // and exception masks. Native callbacks then restore that state directly.
    static const FloatingState java_state = [] {
        FloatingState original{}, state{};
        save_floating(original);
        std::fesetenv(FE_DFL_ENV);
        std::fesetround(FE_TONEAREST);
        _mm_setcsr(_mm_getcsr() & ~((1u << 15) | (1u << 6)));
        save_floating(state);
        restore_floating(original);
        return state;
    }();
#if defined(JNATIVE_X64_FLOATING)
    jn_java_floating_x64(java_state.bytes);
#else
    unsigned short control, status, expected_control;
    __asm__ __volatile__("fnstcw %0; fnstsw %%ax" : "=m" (control), "=a" (status) : : "memory");
    std::memcpy(&expected_control, java_state.bytes, sizeof(expected_control));
    if (control != expected_control || status != 0) {
        restore_floating(java_state);
    } else {
        unsigned int expected_simd;
        std::memcpy(&expected_simd, java_state.bytes + 28, sizeof(expected_simd));
        if (_mm_getcsr() != expected_simd) _mm_setcsr(expected_simd);
    }
#endif
#else
    std::fesetenv(FE_DFL_ENV);
    std::fesetround(FE_TONEAREST);
#if defined(__SSE2__) || defined(_M_X64)
    _mm_setcsr(_mm_getcsr() & ~((1u << 15) | (1u << 6)));
#endif
#endif
}
void* allocate(std::size_t bytes) { return ::operator new(bytes); }
void deallocate(void* value) noexcept { ::operator delete(value); }
void write_output(bool error, const char* bytes, std::size_t size) {
    std::FILE* stream = error ? stderr : stdout;
    if (size) std::fwrite(bytes, 1, size, stream);
    std::fflush(stream);
}
} }
