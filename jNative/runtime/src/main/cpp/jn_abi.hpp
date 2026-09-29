#pragma once
#include "jn_abi.h"
#include "jn_runtime.hpp"
#include <initializer_list>

namespace jnative {
class ApiScope {
public:
    ApiScope();
    ~ApiScope();
    ApiScope(const ApiScope&) = delete;
};
class ManagedEntry {
    ThreadAttachment attachment_;
    FloatingEnvironment environment_;
    ThreadState previous_ = ThreadState::running_managed;
public:
    ManagedEntry();
    ~ManagedEntry();
    ManagedEntry(const ManagedEntry&) = delete;
};
// Internal storage access with the usual collector handshake. The body must
// not invoke Java or perform floating-point arithmetic. Java callbacks still
// require ManagedEntry, which also installs the Java floating environment.
class ManagedAccess {
    ThreadAttachment attachment_;
    ThreadState previous_ = ThreadState::running_managed;
public:
    ManagedAccess();
    ~ManagedAccess();
    ManagedAccess(const ManagedAccess&) = delete;
};
// Takes ownership of one ABI token, including tokens returned by native imports.
class OwnedHandle {
    jn_handle id_;
public:
    explicit OwnedHandle(jn_handle id) : id_(id) {}
    ~OwnedHandle() { Heap::instance().release(id_); }
    OwnedHandle(const OwnedHandle&) = delete;
    Object* get() const { return Heap::instance().resolve(id_); }
};
jn_status capture_abi_error() noexcept;
void rethrow_native_error();
Object* import_reference(jn_handle handle, const char* descriptor,
                         std::initializer_list<jn_handle> borrowed);

// Capture the current fiber's error slot for one synchronous native import.
// Nested callbacks use this same slot; it is never cached across native calls.
class NativeCallError {
    void* const state_;
public:
    NativeCallError();
    NativeCallError(const NativeCallError&) = delete;
    void check() const;
    Object* import_reference(jn_handle handle, const char* descriptor,
                             std::initializer_list<jn_handle> borrowed) const;
};

template<class F> jn_status abi_call(F&& action) noexcept {
    try {
        ApiScope scope;
        ManagedEntry entry;
        jn_clear_error();
        try { action(); return JN_OK; }
        catch (...) { return capture_abi_error(); }
    } catch (...) { return capture_abi_error(); }
}
template<class T> void require_output(T* output) {
    if (!output) throw std::invalid_argument("Null ABI output parameter");
}
}
