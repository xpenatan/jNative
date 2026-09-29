#pragma once
#include "jn_runtime.hpp"

namespace jnative {
// Direct storage is stable while any buffer view is rooted, including during a
// native import. Position, limit and byte order belong to each individual view.
struct ByteBuffer final : Object {
    std::shared_ptr<std::vector<std::uint8_t>> storage;
    const std::int32_t offset, capacity;
    OrdinaryField<std::int32_t> position, limit, mark;
    OrdinaryField<std::uint8_t> little_endian;
    const bool read_only;
    bool direct = true;
    char element = 'B';
    OrdinaryField<Object*> heap_array;
    explicit ByteBuffer(std::int32_t count, bool direct = true);
    ByteBuffer(std::shared_ptr<std::vector<std::uint8_t>> data, std::int32_t start, std::int32_t count, bool immutable);
    const char* type_name() const override;
    void trace(Tracer& tracer) override { tracer.visit(heap_array.get()); }
    int element_size() const { return element == 'B' ? 1 : element == 'S' ? 2 : element == 'F' || element == 'I' ? 4 : 8; }
};
inline ByteBuffer* as_byte_buffer(Object* object) {
    if (require_non_null(object)->runtime_kind != RuntimeKind::buffer)
        raise("java/lang/ClassCastException");
    return static_cast<ByteBuffer*>(object);
}
Object* buffer_allocate(std::int32_t capacity);
Object* buffer_allocate_heap(std::int32_t capacity);
Object* buffer_wrap(Object* bytes, std::int32_t offset, std::int32_t length);
Object* buffer_array(Object*);
Object* buffer_typed_view(Object*, char element);
Object* buffer_copy(Object* target, Object* source);
Object* buffer_position(Object*, std::int32_t);
Object* buffer_limit(Object*, std::int32_t);
Object* buffer_clear(Object*);
Object* buffer_flip(Object*);
Object* buffer_rewind(Object*);
Object* buffer_mark(Object*);
Object* buffer_reset(Object*);
Object* buffer_view(Object*, bool slice, bool read_only);
std::uint64_t buffer_read(Object*, std::int32_t index, int bytes, bool relative);
Object* buffer_write(Object*, std::int32_t index, std::uint64_t value, int bytes, bool relative);
Object* buffer_bulk(Object*, Object* bytes, std::int32_t offset, std::int32_t count, bool write);
std::uint8_t* byte_buffer_data(Object*, std::int32_t count, bool write);
Object* byte_order(bool little_endian);
Object* native_byte_order();
Object* byte_order_string(Object*);
Object* buffer_order(Object*, Object* order);

namespace buffer_detail {
inline bool host_little_endian() {
    const std::uint16_t value = 1;
    return *reinterpret_cast<const unsigned char*>(&value) != 0;
}
template<class T> T reverse_bytes(T value) {
    T result = 0;
    for (std::size_t byte = 0; byte < sizeof(T); ++byte) {
        result = static_cast<T>((result << 8) | (value & 0xff));
        value >>= 8;
    }
    return result;
}
template<class T> std::uint64_t read_direct_scalar(const std::uint8_t* data, bool little) {
    T value;
    std::memcpy(&value, data, sizeof(value));
    if (sizeof(T) > 1 && little != host_little_endian()) value = reverse_bytes(value);
    return value;
}
template<class T> void write_direct_scalar(std::uint8_t* data, std::uint64_t bits, bool little) {
    T value = static_cast<T>(bits);
    if (sizeof(T) > 1 && little != host_little_endian()) value = reverse_bytes(value);
    // Buffer slices can be unaligned. Do not reinterpret byte storage as T*.
    std::memcpy(data, &value, sizeof(value));
}
template<class T, char Kind, bool Relative, bool Write>
std::int32_t scalar_index(ByteBuffer* buffer, std::int32_t index) {
    constexpr int stride = Kind == 'B' ? 1 : Kind == 'S' ? 2 : Kind == 'I' || Kind == 'F' ? 4 : 8;
    constexpr int count = sizeof(T) / stride;
    static_assert(count > 0 && sizeof(T) % stride == 0, "Invalid buffer scalar width");
    if (Write && buffer->read_only) raise("java/nio/ReadOnlyBufferException");
    if (Relative) index = buffer->position.get();
    if (index < 0 || index > buffer->limit.get() - count)
        raise(Relative ? (Write ? "java/nio/BufferOverflowException" : "java/nio/BufferUnderflowException")
                : "java/lang/IndexOutOfBoundsException");
    if (Relative) buffer->position.set(index + count);
    return buffer->offset + index * stride;
}
}

// The bytecode supplies the element kind, value width and addressing mode.
// Specializing these constants removes runtime division and width dispatch.
// Heap storage keeps the ordered atomic-byte path used by the general helper.
template<class T, char Kind, bool Relative>
std::uint64_t buffer_read_scalar(Object* object, std::int32_t index) {
    auto buffer = as_byte_buffer(object);
    if (buffer->element != Kind || buffer->heap_array.get())
        return buffer_read(object, index, sizeof(T), Relative);
    index = buffer_detail::scalar_index<T, Kind, Relative, false>(buffer, index);
    return buffer_detail::read_direct_scalar<T>(buffer->storage->data() + index,
            buffer->little_endian.get() != 0);
}
template<class T, char Kind, bool Relative>
Object* buffer_write_scalar(Object* object, std::int32_t index, std::uint64_t value) {
    auto buffer = as_byte_buffer(object);
    if (buffer->element != Kind || buffer->heap_array.get())
        return buffer_write(object, index, value, sizeof(T), Relative);
    index = buffer_detail::scalar_index<T, Kind, Relative, true>(buffer, index);
    buffer_detail::write_direct_scalar<T>(buffer->storage->data() + index, value,
            buffer->little_endian.get() != 0);
    return buffer;
}
}
