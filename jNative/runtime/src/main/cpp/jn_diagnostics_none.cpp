#include "jn_diagnostics.hpp"
namespace jnative {
NativeTrace capture_native_trace() noexcept { return NativeTrace(); }
void print_native_trace(const NativeTrace&, std::ostream&) {}
std::string save_native_report(const char*, const std::string&, const NativeTrace&,
        const char*, const std::vector<NativeCause>&) noexcept { return std::string(); }
void report_native_exception(const std::exception& error, std::ostream& output) noexcept {
    try { output << error.what() << '\n'; } catch (...) {}
}
void diagnostics_attach_thread() noexcept {}
void diagnostics_detach_thread() noexcept {}
void diagnostics_thread_name(const std::string&) noexcept {}
platform::Path diagnostics_report_directory() { return platform::Path(); }
const char* diagnostics_capability() noexcept { return "disabled"; }
const char* diagnostics_build_id() noexcept { return "unversioned"; }
DiagnosticSession::DiagnosticSession() noexcept {}
DiagnosticSession::~DiagnosticSession() {}
}
