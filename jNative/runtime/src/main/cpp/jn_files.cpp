#include "jn_files.hpp"
#include <fstream>
#include <cerrno>

namespace jnative {
namespace {
::jnative::platform::Path path_value(Object* object) {
    auto path = dynamic_cast<FilePath*>(require_non_null(object));
    if (!path) raise("java/lang/ClassCastException");
    return path->value;
}
::jnative::platform::Path clean_path(::jnative::platform::Path value) {
    return value.cleaned();
}
::jnative::platform::Path from_string(Object* object) {
    const auto& text = as_string(object)->value;
    if (text.find(char16_t(0)) != std::u16string::npos)
        raise("java/nio/file/InvalidPathException", "Path contains a zero character");
    try { return clean_path(::jnative::platform::Path(text)); }
    catch (const std::exception&) { raise("java/nio/file/InvalidPathException", "Invalid filesystem path"); }
}
template<class F> auto filesystem_call(F&& action) -> decltype(action()) {
    try { NativeRegion blocked(ThreadState::blocked_managed); return action(); }
    catch (const ::jnative::platform::FileError& error) {
        const char* type = "java/io/IOException";
        if (error.code() == std::errc::no_such_file_or_directory) type = "java/nio/file/NoSuchFileException";
        else if (error.code() == std::errc::permission_denied) type = "java/nio/file/AccessDeniedException";
        else if (error.code() == std::errc::directory_not_empty) type = "java/nio/file/DirectoryNotEmptyException";
        else if (error.code() == std::errc::file_exists) type = "java/nio/file/FileAlreadyExistsException";
        raise(type, error.what());
    }
    catch (const std::ios_base::failure& error) { raise("java/io/IOException", error.what()); }
}
void io_error(const char* operation, const ::jnative::platform::Path& path) {
    std::error_code code(errno ? errno : EIO, std::generic_category());
    throw ::jnative::platform::FileError(operation, path, code);
}
void default_options(Object* options) {
    if (array_length(options) != 0)
        raise("java/lang/UnsupportedOperationException", "This profile accepts default file options only");
}
}
Object* path_get(Object* first, Object* parts) {
    auto value = from_string(first);
    auto array = dynamic_cast<ReferenceArray*>(require_non_null(parts));
    if (!array) raise("java/lang/ClassCastException");
    for (int i = 0; i < array->length; ++i) {
        auto text = from_string(array->elements[i].get());
        // Paths.get joins strings as path components; resolve handles absolute replacement.
        if (!text.empty()) {
            if (value.empty()) value = text;
            else {
                auto joined = value.string();
                joined += (platform::windows_paths() ? '\\' : '/');
                joined += text.string();
                value = ::jnative::platform::Path(joined);
            }
        }
    }
    return allocate<FilePath>(clean_path(value));
}
Object* path_resolve(Object* path, Object* part) {
    auto base = path_value(path);
    auto value = dynamic_cast<FilePath*>(require_non_null(part));
    auto suffix = value ? value->value : from_string(part);
    return allocate<FilePath>(clean_path(base / suffix));
}
Object* path_absolute(Object* path) {
    auto value = path_value(path);
    auto absolute = filesystem_call([&] { return value.empty() ? ::jnative::platform::current_path() : ::jnative::platform::absolute(value); });
    return allocate<FilePath>(clean_path(absolute));
}
Object* path_normalize(Object* path) {
    auto normalized = path_value(path).lexically_normal();
    if (normalized == ::jnative::platform::Path(".")) normalized.clear();
    return allocate<FilePath>(clean_path(normalized));
}
Object* path_filename(Object* path) {
    auto value = path_value(path);
    if (value.empty()) return allocate<FilePath>(value);
    if (value == value.root_path()) return nullptr;
    return allocate<FilePath>(value.filename());
}
Object* path_string(Object* path) { return allocate<String>(path_value(path).u16string()); }
Object* path_parent(Object* path) {
    auto value = path_value(path);
    auto parent = value.parent_path();
    return parent.empty() || parent == value ? nullptr : allocate<FilePath>(parent);
}
Object* io_path_text(Object* path, std::int32_t mode) {
    LocalRoot<> input(path);
    auto value = path_value(path);
    if (mode == 0) {
        value = filesystem_call([&] { return value.empty() ? platform::current_path() : platform::absolute(value); });
        value = clean_path(value);
    } else if (mode == 1) {
        if (value == value.root_path()) return literal(u"");
        value = value.filename();
    } else {
        auto parent = value.parent_path();
        if (parent.empty() || parent == value) return nullptr;
        value = parent;
    }
    return allocate<String>(value.u16string());
}
namespace {
struct StreamFile final : Object {
    std::unique_ptr<platform::InputFile> input;
    std::unique_ptr<platform::OutputFile> output;
    std::mutex mutex;
    bool closed = false;
};
StreamFile* as_stream(Object* handle) {
    auto stream = dynamic_cast<StreamFile*>(require_non_null(handle));
    if (!stream) raise("java/lang/IllegalArgumentException", "Invalid file stream");
    return stream;
}
void io_bounds(Object* bytes, int offset, int count) {
    int size = array_length(bytes);
    if (offset < 0 || count < 0 || offset > size - count) raise("java/lang/IndexOutOfBoundsException");
}
}
Object* io_open(Object* path, bool write, bool append) {
    auto filename = from_string(path);
    LocalRoot<StreamFile> stream(allocate<StreamFile>());
    filesystem_call([&] {
        if (write) {
            stream.get()->output.reset(new platform::OutputFile(filename, std::ios::binary | (append ? std::ios::app : std::ios::trunc)));
            if (!*stream.get()->output) io_error("open for writing", filename);
        } else {
            stream.get()->input.reset(new platform::InputFile(filename, std::ios::binary));
            if (!*stream.get()->input) io_error("open for reading", filename);
        }
    });
    return stream.get();
}
std::int32_t io_read(Object* handle, Object* bytes, std::int32_t offset, std::int32_t count) {
    LocalRoot<> handle_root(handle), bytes_root(bytes);
    io_bounds(bytes, offset, count);
    auto stream = as_stream(handle);
    std::vector<char> buffer(static_cast<std::size_t>(count));
    int read = filesystem_call([&] {
        std::lock_guard<std::mutex> lock(stream->mutex);
        if (stream->closed || !stream->input) throw std::ios_base::failure("Stream closed or not readable");
        if (count == 0) return 0;
        stream->input->read(buffer.data(), count);
        if (stream->input->bad()) throw std::ios_base::failure("File read failed");
        int read = static_cast<int>(stream->input->gcount());
        return read == 0 ? -1 : read;
    });
    auto destination = primitive_array<std::int8_t>(bytes);
    for (int i = 0; i < read; ++i) {
        destination->elements[offset + i].set(static_cast<std::int8_t>(buffer[i]));
        if ((i & 1023) == 1023) safepoint();
    }
    return read;
}
void io_write(Object* handle, Object* bytes, std::int32_t offset, std::int32_t count) {
    LocalRoot<> handle_root(handle), bytes_root(bytes);
    io_bounds(bytes, offset, count);
    auto stream = as_stream(handle);
    std::vector<char> buffer(static_cast<std::size_t>(count));
    auto source = primitive_array<std::int8_t>(bytes);
    for (int i = 0; i < count; ++i) {
        buffer[i] = char(source->elements[offset + i].get());
        if ((i & 1023) == 1023) safepoint();
    }
    filesystem_call([&] {
        std::lock_guard<std::mutex> lock(stream->mutex);
        if (stream->closed || !stream->output) throw std::ios_base::failure("Stream closed or not writable");
        if (count) stream->output->write(buffer.data(), count);
        if (!*stream->output) throw std::ios_base::failure("File write failed");
    });
}
std::int32_t io_read_byte(Object* handle) {
    LocalRoot<> root(handle);
    auto stream = as_stream(handle);
    return filesystem_call([&]() -> std::int32_t {
        std::lock_guard<std::mutex> lock(stream->mutex);
        if (stream->closed || !stream->input) throw std::ios_base::failure("Stream closed or not readable");
        char value = 0;
        stream->input->read(&value, 1);
        if (stream->input->bad()) throw std::ios_base::failure("File read failed");
        return stream->input->gcount() == 0 ? -1 : static_cast<std::uint8_t>(value);
    });
}
void io_write_byte(Object* handle, std::int32_t byte) {
    LocalRoot<> root(handle);
    auto stream = as_stream(handle);
    const char value = static_cast<char>(byte);
    filesystem_call([&] {
        std::lock_guard<std::mutex> lock(stream->mutex);
        if (stream->closed || !stream->output) throw std::ios_base::failure("Stream closed or not writable");
        stream->output->write(&value, 1);
        if (!*stream->output) throw std::ios_base::failure("File write failed");
    });
}
void io_flush(Object* handle) {
    LocalRoot<> root(handle);
    auto stream = as_stream(handle);
    filesystem_call([&] {
        std::lock_guard<std::mutex> lock(stream->mutex);
        if (stream->closed) throw std::ios_base::failure("Stream closed");
        if (stream->output) { stream->output->flush(); if (!*stream->output) throw std::ios_base::failure("File flush failed"); }
    });
}
void io_close(Object* handle) {
    LocalRoot<> root(handle);
    auto stream = as_stream(handle);
    filesystem_call([&] {
        std::lock_guard<std::mutex> lock(stream->mutex);
        if (stream->closed) return;
        stream->closed = true;
        if (stream->input) stream->input->close();
        if (stream->output) { stream->output->close(); if (!*stream->output) throw std::ios_base::failure("File close failed"); }
    });
}
int io_status(Object* path) {
    auto filename = path_value(path);
    return filesystem_call([&] { std::error_code error; auto info = platform::file_info(filename, error);
        return error ? 0 : (info.regular ? 1 : 0) | (info.directory ? 2 : 0); });
}
std::int64_t io_length(Object* path) {
    auto filename = path_value(path);
    return filesystem_call([&] { std::error_code error; auto info = platform::file_info(filename, error);
        return error || !info.regular ? std::int64_t(0) : static_cast<std::int64_t>(info.size); });
}
bool io_mkdirs(Object* path) {
    auto filename = path_value(path);
    try { return filesystem_call([&] {
        if (platform::exists(filename)) return false;
        platform::create_directories(filename);
        return true;
    }); } catch (const Thrown&) { return false; }
}
std::int32_t path_compare(Object* path, Object* other) {
    auto first = path_value(path), second = path_value(other);
    return first.compare(second);
}
bool path_equals(Object* path, Object* other) {
    auto second = dynamic_cast<FilePath*>(other);
    if (!second) return false;
    auto first = dynamic_cast<FilePath*>(require_non_null(path));
    if (!first) raise("java/lang/ClassCastException");
    LocalRoot<> left(path), right(other);
    NativeRegion blocked(ThreadState::blocked_managed);
    return first->value.compare(second->value) == 0;
}
Object* file_read(Object* path, bool text) {
    auto value = path_value(path);
    auto bytes = filesystem_call([&] {
        errno = 0;
        ::jnative::platform::InputFile input(value, std::ios::binary | std::ios::ate);
        if (!input) io_error("open for reading", value);
        auto count = input.tellg();
        if (count < 0) io_error("read size", value);
        if (count > INT32_MAX) throw std::ios_base::failure("File exceeds supported array/string length");
        input.seekg(0);
        std::string result(std::size_t(count), '\0');
        if (count && !input.read(&result[0], count)) io_error("read", value);
        return result;
    });
    if (text) {
        auto decoded = utf16(bytes);
        if (utf8(decoded) != bytes) raise("java/nio/charset/MalformedInputException", "Malformed UTF-8 input");
        return allocate<String>(decoded);
    }
    LocalRoot<> array(new_array("[B", int32_t(bytes.size())));
    for (int32_t i = 0; i < int32_t(bytes.size()); ++i) byte_set(array.get(), i, uint8_t(bytes[std::size_t(i)]));
    return array.get();
}
Object* file_write(Object* path, Object* value, Object* options, bool text) {
    default_options(options);
    auto filename = path_value(path);
    std::string bytes;
    if (text) {
        const auto& characters = as_string(value)->value;
        bytes = utf8(characters);
        if (utf16(bytes) != characters) raise("java/nio/charset/MalformedInputException", "Malformed UTF-16 input");
    } else {
        auto size = array_length(value);
        bytes.resize(std::size_t(size));
        for (int32_t i = 0; i < size; ++i) bytes[std::size_t(i)] = char(uint8_t(byte_get(value, i)));
    }
    filesystem_call([&] {
        errno = 0;
        ::jnative::platform::OutputFile output(filename, std::ios::binary | std::ios::trunc);
        if (!output) io_error("open for writing", filename);
        output.write(bytes.data(), std::streamsize(bytes.size()));
        output.close();
        if (!output) io_error("write", filename);
    });
    return path;
}
bool file_exists(Object* path, Object* options) {
    default_options(options);
    auto filename = path_value(path);
    return filesystem_call([&] { std::error_code error; return ::jnative::platform::exists(filename, error); });
}
bool file_delete(Object* path, bool required) {
    auto filename = path_value(path);
    bool removed = filesystem_call([&] { return ::jnative::platform::remove(filename); });
    if (!removed && required) raise("java/nio/file/NoSuchFileException");
    return removed;
}
}
