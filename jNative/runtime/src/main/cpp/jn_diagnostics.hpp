#pragma once
#include <array>
#include <cstdint>
#include <memory>
#include <ostream>
#include <string>
#include <vector>
#include <exception>
#include "jn_diagnostic_common.hpp"

#ifndef JNATIVE_NATIVE_TRACES
#define JNATIVE_NATIVE_TRACES 0
#endif
#ifndef JNATIVE_JAVA_TRACES
#define JNATIVE_JAVA_TRACES 1
#endif
#ifndef JNATIVE_CRASH_REPORTS
#define JNATIVE_CRASH_REPORTS 0
#endif

namespace jnative {
struct NativeFrame {
    std::uintptr_t pc=0;
    std::shared_ptr<const diagnostics::Module> module;
};
struct NativeTrace {
    std::array<NativeFrame,64> frames{};
    unsigned count=0;
    std::uint64_t thread=0;
    std::string thread_name;
    bool truncated=false;
};
NativeTrace capture_native_trace() noexcept;
void print_native_trace(const NativeTrace&, std::ostream&);
struct NativeCause { std::string message; const NativeTrace* trace; const char* capture_site; };
std::string save_native_report(const char* event,const std::string& message,const NativeTrace&,
        const char* capture_site="construction",const std::vector<NativeCause>& causes={}) noexcept;
class NativeException : public std::exception {
    std::string message_;
    NativeTrace trace_;
public:
    explicit NativeException(std::string message) : message_(std::move(message)),trace_(capture_native_trace()) {}
    const char* what() const noexcept override {return message_.c_str();}
    const NativeTrace& native_trace() const noexcept {return trace_;}
};
void report_native_exception(const std::exception&,std::ostream&) noexcept;
void diagnostics_attach_thread() noexcept;
void diagnostics_detach_thread() noexcept;
void diagnostics_thread_name(const std::string&) noexcept;
::jnative::platform::Path diagnostics_report_directory();
const char* diagnostics_capability() noexcept;
const char* diagnostics_build_id() noexcept;
class DiagnosticSession {
public:
    DiagnosticSession() noexcept;
    ~DiagnosticSession();
    DiagnosticSession(const DiagnosticSession&)=delete;
};
}
