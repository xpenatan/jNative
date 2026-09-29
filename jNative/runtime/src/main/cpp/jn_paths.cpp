#include "jn_platform_files.hpp"
#include <algorithm>
#include <stdexcept>

namespace jnative { namespace platform {
// Own the stream wrappers' virtual tables in one translation unit, including
// when the compiler combines platform and runtime code during release linking.
InputFile::~InputFile() = default;
OutputFile::~OutputFile() = default;
namespace {
bool separator(char value) { return value == '/' || (windows_paths() && value == '\\'); }
std::size_t root_name_end(const std::string& text) {
    if (!windows_paths()) return 0;
    if (text.size() >= 2 && text[1] == ':') return 2;
    if (text.size() > 2 && separator(text[0]) && separator(text[1])) {
        std::size_t server = text.find_first_of("/\\", 2);
        if (server == std::string::npos) return text.size();
        std::size_t share = text.find_first_of("/\\", server + 1);
        return share == std::string::npos ? text.size() : share;
    }
    return 0;
}
std::size_t root_end(const std::string& text) {
    std::size_t name = root_name_end(text);
    return name < text.size() && separator(text[name]) ? name + 1 : name;
}
char slash() { return windows_paths() ? '\\' : '/'; }
void append_utf8(std::string& result, std::uint32_t point) {
    if (point > 0x10ffff || (point >= 0xd800 && point <= 0xdfff))
        throw std::range_error("Invalid Unicode path");
    if (point < 0x80) result += static_cast<char>(point);
    else {
        if (point >= 0x10000) result += static_cast<char>(0xf0 | (point >> 18));
        else if (point >= 0x800) result += static_cast<char>(0xe0 | (point >> 12));
        else result += static_cast<char>(0xc0 | (point >> 6));
        if (point >= 0x10000) result += static_cast<char>(0x80 | ((point >> 12) & 0x3f));
        if (point >= 0x800) result += static_cast<char>(0x80 | ((point >> 6) & 0x3f));
        result += static_cast<char>(0x80 | (point & 0x3f));
    }
}
template<class Char> std::string to_utf8(const std::basic_string<Char>& value) {
    std::string result;
    for (std::size_t i = 0; i < value.size(); ++i) {
        std::uint32_t point = static_cast<std::uint32_t>(value[i]);
        if (sizeof(Char) == 2 && point >= 0xd800 && point <= 0xdbff) {
            if (++i == value.size() || value[i] < 0xdc00 || value[i] > 0xdfff)
                throw std::range_error("Incomplete Unicode path");
            point = 0x10000 + ((point - 0xd800) << 10) + (value[i] - 0xdc00);
        }
        append_utf8(result, point);
    }
    return result;
}
template<class Char> std::basic_string<Char> from_utf8(const std::string& value) {
    std::basic_string<Char> result;
    for (std::size_t i = 0; i < value.size();) {
        unsigned first = static_cast<unsigned char>(value[i++]);
        unsigned count = first < 0x80 ? 0 : first >= 0xc2 && first <= 0xdf ? 1
            : first >= 0xe0 && first <= 0xef ? 2 : first >= 0xf0 && first <= 0xf4 ? 3 : 4;
        if (count == 4 || count > value.size() - i) throw std::range_error("Invalid UTF-8 path");
        std::uint32_t point = first & (count ? (0x7f >> count) : 0x7f);
        for (unsigned n = 0; n < count; ++n) {
            unsigned next = static_cast<unsigned char>(value[i++]);
            if ((next & 0xc0) != 0x80) throw std::range_error("Invalid UTF-8 path");
            point = (point << 6) | (next & 0x3f);
        }
        if ((count == 1 && point < 0x80) || (count == 2 && point < 0x800)
                || (count == 3 && point < 0x10000) || point > 0x10ffff
                || (point >= 0xd800 && point <= 0xdfff)) throw std::range_error("Invalid UTF-8 path");
        if (sizeof(Char) == 2 && point >= 0x10000) {
            point -= 0x10000;
            result += static_cast<Char>(0xd800 + (point >> 10));
            result += static_cast<Char>(0xdc00 + (point & 0x3ff));
        } else result += static_cast<Char>(point);
    }
    return result;
}
}
Path::Path(const std::u16string& value) : text_(to_utf8(value)) {}
Path::Path(const std::wstring& value) : text_(to_utf8(value)) {}
std::u16string Path::u16string() const { return from_utf8<char16_t>(text_); }
std::wstring Path::wstring() const { return from_utf8<wchar_t>(text_); }
Path Path::cleaned() const {
    std::string result = text_.substr(0, root_end(text_));
    for (std::size_t i = result.size(); i < text_.size(); ++i) {
        char c = text_[i];
        if (separator(c)) {
            if (!result.empty() && separator(result.back())) continue;
            c = slash();
        }
        result += c;
    }
    if (windows_paths()) std::replace(result.begin(), result.end(), '/', '\\');
    while (result.size() > root_end(result) && separator(result.back())) result.pop_back();
    return Path(result);
}
Path Path::root_path() const { return Path(text_.substr(0, root_end(text_))); }
Path Path::relative_path() const { return Path(text_.substr(root_end(text_))); }
bool Path::is_absolute() const {
    if (!windows_paths()) return root_end(text_) != 0;
    std::size_t name = root_name_end(text_);
    return name > 2 || (name != 0 && root_end(text_) > name);
}
Path Path::filename() const {
    Path value = cleaned();
    if (value == value.root_path()) return Path();
    std::size_t at = value.text_.find_last_of(windows_paths() ? "/\\" : "/");
    return Path(value.text_.substr(at == std::string::npos ? root_end(value.text_) : at + 1));
}
Path Path::parent_path() const {
    Path value = cleaned();
    if (value == value.root_path()) return value;
    std::size_t at = value.text_.find_last_of(windows_paths() ? "/\\" : "/");
    return at == std::string::npos ? value.root_path() : Path(value.text_.substr(0, (std::max)(at, root_end(value.text_))));
}
Path Path::extension() const {
    std::string name = filename().string();
    std::size_t dot = name.find_last_of('.');
    if (dot == std::string::npos || dot == 0 || name == "..") return Path();
    return Path(name.substr(dot));
}
Path Path::stem() const { std::string name = filename().string(); return Path(name.substr(0, name.size() - extension().string().size())); }
Path& Path::replace_extension(const Path& extension) {
    text_.erase(text_.size() - this->extension().text_.size());
    if (!extension.empty() && extension.text_[0] != '.') text_ += '.';
    text_ += extension.text_;
    return *this;
}
Path& Path::operator/=(const Path& other) {
    std::size_t name = root_name_end(text_), other_name = root_name_end(other.text_);
    if (other.is_absolute() || (other_name && compare_paths(Path(text_.substr(0, name)), Path(other.text_.substr(0, other_name))) != 0)) text_ = other.text_;
    else if (root_end(other.text_) > other_name) text_ = text_.substr(0, name) + other.text_.substr(other_name);
    else if (!other.empty()) {
        if (!empty() && !separator(text_.back()) && text_.back() != ':') text_ += slash();
        text_ += other.relative_path().text_;
    }
    return *this;
}
int Path::compare(const Path& other) const { return compare_paths(*this, other); }
Path Path::lexically_normal() const {
    Path value = cleaned(), root = value.root_path();
    std::string relative = value.relative_path().text_;
    std::vector<std::string> parts;
    for (std::size_t at = 0; at < relative.size();) {
        std::size_t end = relative.find(slash(), at);
        if (end == std::string::npos) end = relative.size();
        std::string part = relative.substr(at, end - at);
        if (part == ".." && !parts.empty() && parts.back() != "..") parts.pop_back();
        else if (part != "." && !part.empty() && (part != ".." || root_end(value.text_) == root_name_end(value.text_))) parts.push_back(part);
        at = end + 1;
    }
    for (std::size_t i = 0; i < parts.size(); ++i) root /= Path(parts[i]);
    return root;
}
FileInfo file_info(const Path& path) {
    std::error_code error;
    FileInfo info = file_info(path, error);
    if (error) throw FileError("stat", path, error);
    return info;
}
bool exists(const Path& path, std::error_code& error) { return file_info(path, error).exists; }
bool exists(const Path& path) { return file_info(path).exists; }
bool is_directory(const Path& path) { return file_info(path).directory; }
bool is_regular_file(const Path& path) { return file_info(path).regular; }
bool is_symlink(const Path& path) { return file_info(path).symlink; }
std::uint64_t file_size(const Path& path, std::error_code& error) {
    FileInfo info = file_info(path, error);
    if (!error && !info.exists) error = std::make_error_code(std::errc::no_such_file_or_directory);
    return error ? static_cast<std::uint64_t>(-1) : info.size;
}
std::uint64_t file_size(const Path& path) {
    std::error_code error; std::uint64_t size = file_size(path, error);
    if (error) throw FileError("size", path, error);
    return size;
}
std::uint64_t last_write_time(const Path& path) { return file_info(path).modified; }
bool remove(const Path& path) {
    std::error_code error; bool removed = remove(path, error);
    if (error) throw FileError("remove", path, error);
    return removed;
}
void create_directories(const Path& path) {
    if (path.empty() || is_directory(path)) return;
    Path parent = path.parent_path();
    if (parent != path) create_directories(parent);
    create_directory(path);
}
void copy_file(const Path& from, const Path& to) {
    if (exists(to)) throw FileError("copy", to, std::make_error_code(std::errc::file_exists));
    InputFile input(from, std::ios::binary);
    OutputFile output(to, std::ios::binary);
    if (!input || !output) throw FileError("copy", to, std::make_error_code(std::errc::io_error));
    char buffer[8192];
    while (input) {
        input.read(buffer, sizeof(buffer));
        if (input.gcount()) output.write(buffer, input.gcount());
    }
    output.close();
    if (input.bad() || !output) throw FileError("copy", to, std::make_error_code(std::errc::io_error));
}
} }
