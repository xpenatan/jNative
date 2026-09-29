#include "jn_diagnostics.hpp"
#include "jn_report_files.hpp"
#include <chrono>
#include <iostream>

static volatile int* invalid_address = nullptr;

extern "C" std::int32_t demo_action(std::int32_t mode) {
    if (mode == 1) {
        *invalid_address = 42; // Deliberate isolated demonstration crash.
    }
    if (mode == 2) {
        throw jnative::NativeException("Example failure from handwritten C++");
    }
    auto directory = jnative::diagnostics_report_directory();
    auto reports = jnative::diagnostics::pending_reports(directory);
    if (mode == 3 && !reports.empty()) {
        auto stamp = std::chrono::system_clock::now().time_since_epoch().count();
        auto destination = directory.parent_path() / ("export-" + std::to_string(stamp));
        auto file = jnative::diagnostics::export_report(reports.back(), destination);
        std::cout << "Attach this directory to your support request: " << file.parent_path().u8string() << '\n';
    } else {
        std::cout << "Capture capability: " << jnative::diagnostics_capability() << '\n';
        std::cout << reports.size() << " saved reports in " << directory.u8string() << '\n';
        if (!reports.empty()) {
            std::cout << "Run diagnostics-demo export to prepare a report for manual submission.\n";
        }
    }
    return 0;
}
