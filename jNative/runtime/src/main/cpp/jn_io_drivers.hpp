#pragma once
#include "jn_classlib.hpp"
#include "jn_arrays.hpp"
#include "jn_files.hpp"

namespace jnative {
template<class Read>
std::int32_t file_input_read(Object* stream, Object* handle, Read read) {
    RootFrame<2> roots;
    FrameRoot<> receiver(roots.slot(0), stream), file(roots.slot(1), handle);
    if (std::strcmp(require_non_null(stream)->type_name(), "java/io/FileInputStream") == 0)
        return io_read_byte(file.get());
    LocalRoot<> one(new_array("[B", 1));
    return read(receiver.get(), one.get(), 0, 1) < 0 ? -1
        : static_cast<std::uint8_t>(array_get<std::int8_t>(one.get(), 0));
}
template<class Read>
std::int32_t input_read(Object* stream, Object* bytes, std::int32_t offset, std::int32_t length, Read read) {
    RootFrame<2> roots;
    FrameRoot<> input(roots.slot(0), stream), output(roots.slot(1), bytes);
    if (offset < 0 || length < 0 || offset > array_length(bytes) - length)
        raise("java/lang/IndexOutOfBoundsException");
    if (!length) return 0;
    auto storage = primitive_array<std::int8_t>(output.get());
    auto first = read(input.get());
    if (first < 0) return -1;
    storage->elements[offset].set(static_cast<std::int8_t>(first));
    std::int32_t count = 1;
    try {
        while (count < length) {
            auto value = read(input.get());
            if (value < 0) break;
            storage->elements[offset + count++].set(static_cast<std::int8_t>(value));
            if ((count & 1023) == 0) safepoint();
        }
    } catch (const Thrown& error) {
        if (!instance_of(error.object(), "java/io/IOException")) throw;
    }
    return count;
}
template<class Read>
std::int64_t input_skip(Object* stream, std::int64_t count, Read read) {
    LocalRoot<> input(stream);
    if (count <= 0) return 0;
    const auto capacity = static_cast<std::int32_t>(std::min<std::int64_t>(2048, count));
    LocalRoot<> buffer(new_array("[B", capacity));
    std::int64_t skipped = 0;
    while (skipped < count) {
        auto n = read(input.get(), buffer.get(), 0,
            static_cast<std::int32_t>(std::min<std::int64_t>(capacity, count - skipped)));
        if (n < 0) break;
        skipped = add(skipped, std::int64_t(n));
        safepoint();
    }
    return skipped;
}
template<class Read>
Object* input_read_all(Object* stream, Read read) {
    RootFrame<3> roots;
    FrameRoot<> input(roots.slot(0), stream), buffer(roots.slot(1), new_array("[B", 8192)),
        output(roots.slot(2), new_array("[B", 32));
    std::int32_t count = 0;
    while (true) {
        auto n = read(input.get(), buffer.get());
        if (n < 0) break;
        if (n > 8192) raise("java/lang/IndexOutOfBoundsException");
        if (n > INT32_MAX - count) raise("java/lang/OutOfMemoryError");
        auto capacity = array_length(output.get());
        if (count + n > capacity) {
            const auto doubled = capacity > INT32_MAX / 2 ? INT32_MAX : capacity * 2;
            output.set(array_copy_of(output.get(), std::max(count + n, doubled)));
        }
        array_copy_cooperative(buffer.get(), 0, output.get(), count, n);
        count += n;
        safepoint();
    }
    return array_copy_of(output.get(), count);
}
template<class Write>
void output_write(Object* stream, Object* bytes, std::int32_t offset, std::int32_t length, Write write) {
    RootFrame<2> roots;
    FrameRoot<> output(roots.slot(0), stream), input(roots.slot(1), bytes);
    if (offset < 0 || length < 0 || offset > array_length(bytes) - length)
        raise("java/lang/IndexOutOfBoundsException");
    auto storage = primitive_array<std::int8_t>(input.get());
    for (std::int32_t i = 0; i < length; ++i) {
        write(output.get(), storage->elements[offset + i].get());
        if ((i & 1023) == 1023) safepoint();
    }
}
template<class Write>
void data_write_short(Object* stream, std::int32_t value, Write write) {
    LocalRoot<> output(stream);
    write(output.get(), unsigned_shift(value, 8));
    write(output.get(), value);
}
template<class Write>
void data_write_int(Object* stream, std::int32_t value, Write write) {
    LocalRoot<> output(stream);
    write(output.get(), unsigned_shift(value, 24));
    write(output.get(), unsigned_shift(value, 16));
    write(output.get(), unsigned_shift(value, 8));
    write(output.get(), value);
}
template<class WriteInt>
void data_write_long(Object* stream, std::int64_t value, WriteInt write_int) {
    LocalRoot<> output(stream);
    write_int(output.get(), signed32(static_cast<std::uint32_t>(unsigned_shift(value, 32))));
    write_int(output.get(), signed32(static_cast<std::uint32_t>(value)));
}
}
