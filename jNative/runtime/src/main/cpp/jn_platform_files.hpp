#pragma once
#include <cstdint>
#include <fstream>
#include <string>
#include <system_error>
#include <vector>

namespace jnative { namespace platform {
// Paths carry UTF-8 independently of the target's native filename representation.
class Path {
    std::string text_;
public:
    Path() {}
    Path(const char* value) : text_(value) {}
    Path(std::string value) : text_(std::move(value)) {}
    Path(const std::u16string&);
    Path(const std::wstring&);
    Path(const wchar_t* value) : Path(std::wstring(value)) {}
    const std::string& string() const { return text_; }
    const std::string& u8string() const { return text_; }
    std::u16string u16string() const;
    std::wstring wstring() const;
    bool empty() const { return text_.empty(); }
    void clear() { text_.clear(); }
    Path cleaned() const;
    Path root_path() const;
    Path relative_path() const;
    Path parent_path() const;
    Path filename() const;
    Path extension() const;
    Path stem() const;
    Path lexically_normal() const;
    bool is_absolute() const;
    Path& replace_extension(const Path&);
    Path& operator/=(const Path&);
    int compare(const Path&) const;
};
inline Path operator/(Path first, const Path& second) { return first /= second; }
inline bool operator==(const Path& first, const Path& second) { return first.string() == second.string(); }
inline bool operator!=(const Path& first, const Path& second) { return !(first == second); }
bool windows_paths() noexcept;
int compare_paths(const Path&, const Path&);
Path current_path();
Path absolute(const Path&);
struct FileInfo {
    bool exists = false, directory = false, regular = false, symlink = false;
    std::uint64_t size = 0, modified = 0;
};
FileInfo file_info(const Path&, std::error_code&);
bool remove(const Path&, std::error_code&);
void create_directory(const Path&);
std::vector<Path> directory_entries(const Path&);
class FileError : public std::system_error {
public:
    FileError(const char* operation, const Path& path, std::error_code code)
        : std::system_error(code, std::string(operation) + ": " + path.string()) {}
};
class InputFile : public std::ifstream {
public:
    explicit InputFile(const Path&, std::ios::openmode = std::ios::in);
    ~InputFile() override;
};
class OutputFile : public std::ofstream {
public:
    explicit OutputFile(const Path&, std::ios::openmode = std::ios::out);
    ~OutputFile() override;
};
FileInfo file_info(const Path&);
bool exists(const Path&, std::error_code&);
bool exists(const Path&);
bool is_directory(const Path&);
bool is_regular_file(const Path&);
bool is_symlink(const Path&);
std::uint64_t file_size(const Path&, std::error_code&);
std::uint64_t file_size(const Path&);
std::uint64_t last_write_time(const Path&);
bool remove(const Path&);
void create_directories(const Path&);
void copy_file(const Path&, const Path&);
} }
