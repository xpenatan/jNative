#pragma once
#include "jn_diagnostic_common.hpp"
#include <algorithm>
#include <stdexcept>

namespace jnative { namespace diagnostics {
// Launcher utilities deliberately perform no network activity or report deletion.
inline std::vector<::jnative::platform::Path> pending_reports(const ::jnative::platform::Path& directory) {
    std::vector<::jnative::platform::Path> reports;
    if(!::jnative::platform::is_directory(directory)) return reports;
    for(const auto& entry : ::jnative::platform::directory_entries(directory)) {
        if(::jnative::platform::is_regular_file(entry) && !::jnative::platform::is_symlink(entry) && entry.extension()==".json"
                && ::jnative::platform::file_size(entry)>0 && ::jnative::platform::file_size(entry)<=32*1024*1024) reports.push_back(entry);
        if(reports.size()>=1024)break;
    }
    std::sort(reports.begin(),reports.end(),[](const ::jnative::platform::Path& a,const ::jnative::platform::Path& b) {
        return ::jnative::platform::last_write_time(a)<::jnative::platform::last_write_time(b);
    });
    return reports;
}
inline ::jnative::platform::Path export_report(const ::jnative::platform::Path& report,
        const ::jnative::platform::Path& destination,bool include_dump=true) {
    if(!::jnative::platform::is_regular_file(report) || ::jnative::platform::is_symlink(report)
            || report.extension()!=".json" || ::jnative::platform::file_size(report)>32*1024*1024)
        throw std::runtime_error("Invalid report file");
    if(::jnative::platform::exists(destination)) throw std::runtime_error("Report export requires a new directory");
    // Native reports and their dumps share an ASCII basename. Never follow an
    // arbitrary filename embedded in a report supplied by another application.
    auto dump=report;dump.replace_extension(".dmp");
    bool has_dump=include_dump && ::jnative::platform::is_regular_file(dump) && !::jnative::platform::is_symlink(dump);
    if(has_dump && ::jnative::platform::file_size(dump)>1024ULL*1024*1024)throw std::runtime_error("Dump exceeds 1 GiB");
    ::jnative::platform::create_directories(destination);
    auto exported=destination/report.filename();
    if(include_dump) ::jnative::platform::copy_file(report,exported);
    else {
        ::jnative::platform::InputFile input(report,std::ios::binary);
        std::string text((std::istreambuf_iterator<char>(input)),{});
        const std::string key="\"dump\":\"";
        auto start=text.find(key);
        if(start!=std::string::npos) {
            auto end=text.find('"',start+key.size());
            if(end==std::string::npos)throw std::runtime_error("Invalid native report");
            text.erase(start+key.size(),end-start-key.size());
        }
        ::jnative::platform::OutputFile output(exported,std::ios::binary);
        output<<text;
        if(!output)throw std::runtime_error("Cannot export report");
    }
    if(has_dump)::jnative::platform::copy_file(dump,destination/dump.filename());
    return exported;
}
} }
