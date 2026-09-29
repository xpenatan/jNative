#pragma once
#include "jn_runtime.hpp"
#include "jn_platform_files.hpp"

namespace jnative {
Object* path_parent(Object* path);
Object* io_open(Object* path, bool write, bool append);
std::int32_t io_read(Object* handle, Object* bytes, std::int32_t offset, std::int32_t count);
std::int32_t io_read_byte(Object* handle);
void io_write_byte(Object* handle, std::int32_t value);
Object* io_path_text(Object* path, std::int32_t mode);
void io_write(Object* handle, Object* bytes, std::int32_t offset, std::int32_t count);
void io_close(Object* handle);
void io_flush(Object* handle);
int io_status(Object* path);
std::int64_t io_length(Object* path);
bool io_mkdirs(Object* path);
struct FilePath final : Object {
    const ::jnative::platform::Path value;
    explicit FilePath(::jnative::platform::Path path)
        : Object(0, RuntimeKind::file_path), value(std::move(path)) {}
    const char* type_name() const override { return "jnative/runtime/NativePath"; }
};
Object* path_get(Object* first, Object* parts);
Object* path_resolve(Object* path, Object* part);
Object* path_absolute(Object* path);
Object* path_normalize(Object* path);
Object* path_filename(Object* path);
Object* path_string(Object* path);
bool path_equals(Object* path, Object* other);
std::int32_t path_compare(Object* path, Object* other);
Object* file_read(Object* path, bool text);
Object* file_write(Object* path, Object* value, Object* options, bool text);
bool file_exists(Object* path, Object* options);
bool file_delete(Object* path, bool required);
}
