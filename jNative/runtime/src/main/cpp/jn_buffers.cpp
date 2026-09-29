#include "jn_buffers.hpp"
#include <cstring>

namespace jnative {
namespace {
struct ByteOrder final : Object {
    const bool little;
    explicit ByteOrder(bool value) : little(value) {}
    const char* type_name() const override { return "java/nio/ByteOrder"; }
};
using buffer_detail::host_little_endian;
using buffer_detail::read_direct_scalar;
using buffer_detail::write_direct_scalar;
}
Object* byte_order(bool little) {
    static GlobalHandle<> big(allocate<ByteOrder>(false));
    static GlobalHandle<> small(allocate<ByteOrder>(true));
    return little ? small.get() : big.get();
}
Object* native_byte_order() {
    std::uint16_t one = 1;
    return byte_order(*reinterpret_cast<unsigned char*>(&one) != 0);
}
Object* byte_order_string(Object* object) {
    auto order = static_cast<ByteOrder*>(require_non_null(object));
    return literal(order->little ? u"LITTLE_ENDIAN" : u"BIG_ENDIAN");
}
Object* buffer_order(Object* object, Object* order) {
    auto buffer = as_byte_buffer(object);
    buffer->little_endian.set(order != byte_order(false));
    return buffer;
}
ByteBuffer::ByteBuffer(std::int32_t count, bool is_direct)
    : Object(0, RuntimeKind::buffer), storage(std::make_shared<std::vector<std::uint8_t>>(is_direct ? count : 0)), offset(0), capacity(count), read_only(false), direct(is_direct) {
    limit.set(count); mark.set(-1);
}
ByteBuffer::ByteBuffer(std::shared_ptr<std::vector<std::uint8_t>> data, std::int32_t start, std::int32_t count, bool immutable)
    : Object(0, RuntimeKind::buffer), storage(std::move(data)), offset(start), capacity(count), read_only(immutable) {
    limit.set(count); mark.set(-1);
}
const char* ByteBuffer::type_name() const {
    switch (element) {
        case 'F': return "java/nio/FloatBuffer";
        case 'S': return "java/nio/ShortBuffer";
        case 'I': return "java/nio/IntBuffer";
        case 'J': return "java/nio/LongBuffer";
        case 'D': return "java/nio/DoubleBuffer";
        default: return "java/nio/ByteBuffer";
    }
}
Object* buffer_wrap(Object* bytes, std::int32_t offset, std::int32_t length) {
    LocalRoot<> root(bytes);
    int capacity = array_length(bytes);
    if (offset < 0 || length < 0 || offset > capacity - length) raise("java/lang/IndexOutOfBoundsException");
    auto result = allocate<ByteBuffer>(capacity, false);
    result->heap_array.set(bytes);
    result->position.set(offset); result->limit.set(offset + length);
    return result;
}
Object* buffer_allocate_heap(std::int32_t capacity) {
    if (capacity < 0) raise("java/lang/IllegalArgumentException");
    LocalRoot<> bytes(new_array("[B", capacity));
    return buffer_wrap(bytes.get(), 0, capacity);
}
Object* buffer_array(Object* object) {
    auto buffer = as_byte_buffer(object);
    if (buffer->element != 'B' || !buffer->heap_array.get()) raise("java/lang/UnsupportedOperationException");
    if (buffer->read_only) raise("java/nio/ReadOnlyBufferException");
    return buffer->heap_array.get();
}
Object* buffer_typed_view(Object* object, char element) {
    LocalRoot<> root(object);
    auto buffer = as_byte_buffer(object);
    int size = element == 'S' ? 2 : element == 'F' || element == 'I' ? 4 : 8;
    auto result = allocate<ByteBuffer>(buffer->storage, buffer->offset + buffer->position.get(),
            (buffer->limit.get() - buffer->position.get()) / size, buffer->read_only);
    result->element = element;
    result->heap_array.set(buffer->heap_array.get()); result->direct = buffer->direct;
    result->little_endian.set(buffer->little_endian.get());
    return result;
}
Object* buffer_allocate(std::int32_t count) {
    if (count < 0) raise("java/lang/IllegalArgumentException", "Negative buffer capacity");
    return allocate<ByteBuffer>(count);
}
Object* buffer_position(Object* object, std::int32_t value) {
    auto buffer = as_byte_buffer(object);
    if (value < 0 || value > buffer->limit.get()) raise("java/lang/IllegalArgumentException", "Invalid buffer position");
    buffer->position.set(value);
    if (buffer->mark.get() > value) buffer->mark.set(-1);
    return buffer;
}
Object* buffer_limit(Object* object, std::int32_t value) {
    auto buffer = as_byte_buffer(object);
    if (value < 0 || value > buffer->capacity) raise("java/lang/IllegalArgumentException", "Invalid buffer limit");
    buffer->limit.set(value);
    if (buffer->position.get() > value) buffer->position.set(value);
    if (buffer->mark.get() > value) buffer->mark.set(-1);
    return buffer;
}
Object* buffer_clear(Object* object) {
    auto buffer = as_byte_buffer(object);
    buffer->position.set(0); buffer->limit.set(buffer->capacity); buffer->mark.set(-1);
    return buffer;
}
Object* buffer_flip(Object* object) {
    auto buffer = as_byte_buffer(object);
    buffer->limit.set(buffer->position.get()); buffer->position.set(0); buffer->mark.set(-1);
    return buffer;
}
Object* buffer_rewind(Object* object) {
    auto buffer = as_byte_buffer(object); buffer->position.set(0); buffer->mark.set(-1); return buffer;
}
Object* buffer_mark(Object* object) {
    auto buffer = as_byte_buffer(object); buffer->mark.set(buffer->position.get()); return buffer;
}
Object* buffer_reset(Object* object) {
    auto buffer = as_byte_buffer(object);
    if (buffer->mark.get() < 0) raise("java/nio/InvalidMarkException");
    buffer->position.set(buffer->mark.get()); return buffer;
}
Object* buffer_view(Object* object, bool slice, bool read_only) {
    LocalRoot<> root(object);
    auto buffer = as_byte_buffer(object);
    auto result = allocate<ByteBuffer>(buffer->storage, buffer->offset + (slice ? buffer->position.get() * buffer->element_size() : 0),
            slice ? buffer->limit.get() - buffer->position.get() : buffer->capacity, read_only || buffer->read_only);
    result->element = buffer->element;
    result->heap_array.set(buffer->heap_array.get()); result->direct = buffer->direct;
    if (buffer->element != 'B') result->little_endian.set(buffer->little_endian.get());
    if (!slice) { result->position.set(buffer->position.get()); result->limit.set(buffer->limit.get()); result->mark.set(buffer->mark.get()); }
    return result;
}
static std::int32_t checked_index(ByteBuffer* buffer, std::int32_t index, int bytes, bool relative, bool write) {
    if (write && buffer->read_only) raise("java/nio/ReadOnlyBufferException");
    if (relative) index = buffer->position.get();
    int count = bytes / buffer->element_size();
    if (index < 0 || bytes < 0 || index > buffer->limit.get() - count)
        raise(relative ? (write ? "java/nio/BufferOverflowException" : "java/nio/BufferUnderflowException") : "java/lang/IndexOutOfBoundsException");
    if (relative) buffer->position.set(index + count);
    return buffer->offset + index * buffer->element_size();
}
std::uint64_t buffer_read(Object* object, std::int32_t index, int bytes, bool relative) {
    auto buffer = as_byte_buffer(object);
    index = checked_index(buffer, index, bytes, relative, false);
    std::uint64_t result = 0;
    const bool little = buffer->little_endian.get() != 0;
    auto heap = buffer->heap_array.get();
    if (!heap && bytes > 0) {
        auto data = buffer->storage->data() + index;
        switch (bytes) {
            case 1: return read_direct_scalar<std::uint8_t>(data, little);
            case 2: return read_direct_scalar<std::uint16_t>(data, little);
            case 4: return read_direct_scalar<std::uint32_t>(data, little);
            case 8: return read_direct_scalar<std::uint64_t>(data, little);
        }
    }
    for (int i = 0; i < bytes; ++i) {
        int shift = little ? i * 8 : (bytes - i - 1) * 8;
        auto value = heap ? std::uint8_t(byte_get(heap, index + i)) : (*buffer->storage)[index + i];
        result |= std::uint64_t(value) << shift;
    }
    return result;
}
Object* buffer_write(Object* object, std::int32_t index, std::uint64_t value, int bytes, bool relative) {
    auto buffer = as_byte_buffer(object);
    index = checked_index(buffer, index, bytes, relative, true);
    const bool little = buffer->little_endian.get() != 0;
    auto heap = buffer->heap_array.get();
    if (!heap && bytes > 0) {
        auto data = buffer->storage->data() + index;
        switch (bytes) {
            case 1: write_direct_scalar<std::uint8_t>(data, value, little); return buffer;
            case 2: write_direct_scalar<std::uint16_t>(data, value, little); return buffer;
            case 4: write_direct_scalar<std::uint32_t>(data, value, little); return buffer;
            case 8: write_direct_scalar<std::uint64_t>(data, value, little); return buffer;
        }
    }
    for (int i = 0; i < bytes; ++i) {
        int shift = little ? i * 8 : (bytes - i - 1) * 8;
        auto byte = static_cast<std::uint8_t>(value >> shift);
        if (heap) byte_set(heap, index + i, byte);
        else (*buffer->storage)[index + i] = byte;
    }
    return buffer;
}

namespace {
template<class T, bool Write, bool Reverse>
inline void transfer_element(OrdinaryField<T>& element, std::uint8_t* buffer) {
    T value;
    if (Write) value = element.get();
    auto representation = reinterpret_cast<unsigned char*>(&value);
    if (Reverse) {
        for (std::size_t byte = 0; byte < sizeof(T); ++byte) {
            if (Write) buffer[byte] = representation[sizeof(T) - 1 - byte];
            else representation[sizeof(T) - 1 - byte] = buffer[byte];
        }
    } else {
        // memcpy supports unaligned slices and preserves floating-point bits.
        if (Write) std::memcpy(buffer, &value, sizeof(T));
        else std::memcpy(&value, buffer, sizeof(T));
    }
    if (!Write) element.set(value);
}

template<class T, bool Write, bool Reverse>
void transfer_elements(OrdinaryField<T>* array, std::uint8_t* buffer, std::int32_t count) {
    // Keep atomic accesses and their element order while reducing loop overhead.
    // Never copy the storage representation of an atomic-backed Java array.
    // Native-order writes need no byte shuffling and benefit from a larger block.
    if constexpr (Write && !Reverse) {
        while (count >= 16) {
            transfer_element<T, Write, Reverse>(array[0], buffer);
            transfer_element<T, Write, Reverse>(array[1], buffer + sizeof(T));
            transfer_element<T, Write, Reverse>(array[2], buffer + 2 * sizeof(T));
            transfer_element<T, Write, Reverse>(array[3], buffer + 3 * sizeof(T));
            transfer_element<T, Write, Reverse>(array[4], buffer + 4 * sizeof(T));
            transfer_element<T, Write, Reverse>(array[5], buffer + 5 * sizeof(T));
            transfer_element<T, Write, Reverse>(array[6], buffer + 6 * sizeof(T));
            transfer_element<T, Write, Reverse>(array[7], buffer + 7 * sizeof(T));
            transfer_element<T, Write, Reverse>(array[8], buffer + 8 * sizeof(T));
            transfer_element<T, Write, Reverse>(array[9], buffer + 9 * sizeof(T));
            transfer_element<T, Write, Reverse>(array[10], buffer + 10 * sizeof(T));
            transfer_element<T, Write, Reverse>(array[11], buffer + 11 * sizeof(T));
            transfer_element<T, Write, Reverse>(array[12], buffer + 12 * sizeof(T));
            transfer_element<T, Write, Reverse>(array[13], buffer + 13 * sizeof(T));
            transfer_element<T, Write, Reverse>(array[14], buffer + 14 * sizeof(T));
            transfer_element<T, Write, Reverse>(array[15], buffer + 15 * sizeof(T));
            array += 16;
            buffer += 16 * sizeof(T);
            count -= 16;
        }
    }
    while (count >= 4) {
        transfer_element<T, Write, Reverse>(array[0], buffer);
        transfer_element<T, Write, Reverse>(array[1], buffer + sizeof(T));
        transfer_element<T, Write, Reverse>(array[2], buffer + 2 * sizeof(T));
        transfer_element<T, Write, Reverse>(array[3], buffer + 3 * sizeof(T));
        array += 4;
        buffer += 4 * sizeof(T);
        count -= 4;
    }
    for (std::int32_t i = 0; i < count; ++i) {
        transfer_element<T, Write, Reverse>(array[i], buffer + i * sizeof(T));
    }
}

template<class T, bool Write>
void transfer_direct(ByteBuffer* buffer, Object* object, std::int32_t offset, std::int32_t count,
        std::int32_t position) {
    auto array = primitive_array<T>(object);
    if (!count) return;
    auto elements = array->elements.get() + offset;
    auto data = buffer->storage->data() + buffer->offset + static_cast<std::size_t>(position) * sizeof(T);
    if (sizeof(T) > 1 && (buffer->little_endian.get() != 0) != host_little_endian())
        transfer_elements<T, Write, true>(elements, data, count);
    else transfer_elements<T, Write, false>(elements, data, count);
}

template<bool Write>
void transfer_direct(ByteBuffer* buffer, Object* array, std::int32_t offset, std::int32_t count,
        std::int32_t position) {
    switch (buffer->element) {
        case 'F': transfer_direct<float, Write>(buffer, array, offset, count, position); break;
        case 'D': transfer_direct<double, Write>(buffer, array, offset, count, position); break;
        case 'S': transfer_direct<std::int16_t, Write>(buffer, array, offset, count, position); break;
        case 'I': transfer_direct<std::int32_t, Write>(buffer, array, offset, count, position); break;
        case 'J': transfer_direct<std::int64_t, Write>(buffer, array, offset, count, position); break;
        default: transfer_direct<std::int8_t, Write>(buffer, array, offset, count, position); break;
    }
}

void transfer_heap_bytes(ByteBuffer* buffer, Object* object, std::int32_t offset, std::int32_t count,
        std::int32_t position, bool write) {
    auto array = primitive_array<std::int8_t>(object);
    auto heap = primitive_array<std::int8_t>(buffer->heap_array.get());
    auto source = write ? array : heap;
    auto target = write ? heap : array;
    int source_offset = write ? offset : buffer->offset + position;
    int target_offset = write ? buffer->offset + position : offset;
    // ByteBuffer.wrap(array) can make the transfer overlap the same Java array.
    if (source == target && target_offset > source_offset && target_offset - source_offset < count) {
        for (int i = count; i-- > 0;)
            target->elements[target_offset + i].set(source->elements[source_offset + i].get());
    } else {
        for (int i = 0; i < count; ++i)
            target->elements[target_offset + i].set(source->elements[source_offset + i].get());
    }
}
}

Object* buffer_bulk(Object* object, Object* bytes, std::int32_t offset, std::int32_t count, bool write) {
    auto buffer = as_byte_buffer(object);
    std::int32_t length = array_length(bytes);
    if (offset < 0 || count < 0 || offset > length - count) raise("java/lang/IndexOutOfBoundsException");
    if (write && buffer->read_only) raise("java/nio/ReadOnlyBufferException");
    const std::int32_t position = buffer->position.get();
    if (count > buffer->limit.get() - position)
        raise(write ? "java/nio/BufferOverflowException" : "java/nio/BufferUnderflowException");
    if (!buffer->heap_array.get()) {
        if (write) transfer_direct<true>(buffer, bytes, offset, count, position);
        else transfer_direct<false>(buffer, bytes, offset, count, position);
        buffer->position.set(position + count);
        return buffer;
    }
    if (buffer->element == 'B') {
        transfer_heap_bytes(buffer, bytes, offset, count, position, write);
        buffer->position.set(position + count);
        return buffer;
    }
    for (std::int32_t i = 0; i < count; ++i) {
        std::uint64_t value = 0;
        if (write) {
            switch (buffer->element) {
                case 'F': value = std::uint32_t(float_bits(array_get<float>(bytes, offset + i), false)); break;
                case 'D': value = std::uint64_t(double_bits(array_get<double>(bytes, offset + i), false)); break;
                case 'S': value = std::uint16_t(array_get<std::int16_t>(bytes, offset + i)); break;
                case 'I': value = std::uint32_t(array_get<std::int32_t>(bytes, offset + i)); break;
                case 'J': value = std::uint64_t(array_get<std::int64_t>(bytes, offset + i)); break;
                default: value = std::uint8_t(byte_get(bytes, offset + i));
            }
            buffer_write(object, 0, value, buffer->element_size(), true);
        } else {
            value = buffer_read(object, 0, buffer->element_size(), true);
            switch (buffer->element) {
                case 'F': array_set<float>(bytes, offset + i, bits_float(signed32(std::uint32_t(value)))); break;
                case 'D': array_set<double>(bytes, offset + i, bits_double(signed64(value))); break;
                case 'S': array_set<std::int16_t>(bytes, offset + i, std::int32_t(value)); break;
                case 'I': array_set<std::int32_t>(bytes, offset + i, signed32(std::uint32_t(value))); break;
                case 'J': array_set<std::int64_t>(bytes, offset + i, signed64(value)); break;
                default: byte_set(bytes, offset + i, std::int32_t(value));
            }
        }
    }
    return buffer;
}
Object* buffer_copy(Object* target, Object* source) {
    auto to = as_byte_buffer(target), from = as_byte_buffer(source);
    if (to == from) raise("java/lang/IllegalArgumentException", "Source buffer is destination");
    if (to->read_only) raise("java/nio/ReadOnlyBufferException");
    int source_position = from->position.get(), target_position = to->position.get();
    int count = from->limit.get() - source_position;
    if (count < 0) raise("java/nio/BufferUnderflowException");
    if (count > to->limit.get() - target_position) raise("java/nio/BufferOverflowException");
    if (from->element == 'B' && to->element == 'B'
            && !from->heap_array.get() && !to->heap_array.get()) {
        // Views can overlap. Bulk copying avoids per-byte type/position checks
        // and a temporary allocation while retaining ByteBuffer.put semantics.
        if (count) std::memmove(to->storage->data() + to->offset + target_position,
                from->storage->data() + from->offset + source_position, static_cast<std::size_t>(count));
        from->position.set(source_position + count);
        to->position.set(target_position + count);
    } else {
        std::vector<std::uint64_t> copy(static_cast<std::size_t>(count));
        for (int i = 0; i < count; ++i) copy[i] = buffer_read(source, 0, from->element_size(), true);
        for (int i = 0; i < count; ++i) buffer_write(target, 0, copy[i], to->element_size(), true);
    }
    return target;
}
std::uint8_t* byte_buffer_data(Object* object, std::int32_t count, bool write) {
    auto buffer = as_byte_buffer(object);
    if (!buffer->direct) raise("java/lang/IllegalArgumentException", "Native access requires a direct byte buffer");
    if (write && buffer->read_only) raise("java/nio/ReadOnlyBufferException");
    if (count < 0 || count > buffer->limit.get() - buffer->position.get())
        raise(write ? "java/nio/BufferOverflowException" : "java/nio/BufferUnderflowException");
    // Avoid pointer arithmetic on a possibly null zero-size vector allocation.
    if (buffer->storage->empty()) return nullptr;
    return buffer->storage->data() + buffer->offset + buffer->position.get();
}
}
