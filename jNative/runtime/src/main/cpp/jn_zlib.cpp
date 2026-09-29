#include "jn_runtime.hpp"
#if defined(JNATIVE_USE_ZLIB)
#include <zlib.h>

namespace jnative {
namespace {
struct Compression final : Object {
    z_stream stream{};
    std::vector<std::uint8_t> input;
    bool compress, opened = false, finished = false, dictionary = false;
    explicit Compression(bool compress) : compress(compress) {}
    void close() { if (opened) { compress ? deflateEnd(&stream) : inflateEnd(&stream); opened = false; } }
    ~Compression() override { close(); }
};
Compression* compression(Object* state) {
    auto value = dynamic_cast<Compression*>(require_non_null(state));
    if (!value || !value->opened) raise("java/lang/NullPointerException", "Compression stream closed");
    return value;
}
void compression_bounds(Object* bytes, int offset, int length) {
    int size = array_length(bytes);
    if (offset < 0 || length < 0 || offset > size - length) raise("java/lang/IndexOutOfBoundsException");
}
}
Object* zlib_open(bool compress, int level, bool raw) {
    LocalRoot<Compression> value(allocate<Compression>(compress));
    int status = compress ? deflateInit2(&value.get()->stream, level, Z_DEFLATED, raw ? -15 : 15, 8, Z_DEFAULT_STRATEGY)
            : inflateInit2(&value.get()->stream, raw ? -15 : 15);
    if (status == Z_MEM_ERROR) raise("java/lang/OutOfMemoryError");
    if (status != Z_OK) raise("java/lang/IllegalArgumentException", "Invalid compression configuration");
    value.get()->opened = true;
    return value.get();
}
void zlib_input(Object* state, Object* bytes, int offset, int length) {
    LocalRoot<> state_root(state), bytes_root(bytes);
    compression_bounds(bytes, offset, length);
    auto value = compression(state);
    value->input.resize(static_cast<std::size_t>(length));
    auto source = primitive_array<std::int8_t>(bytes);
    for (int i = 0; i < length; ++i) {
        value->input[i] = std::uint8_t(source->elements[offset + i].get());
        if ((i & 1023) == 1023) safepoint();
    }
    value->stream.next_in = value->input.data();
    value->stream.avail_in = static_cast<uInt>(length);
}
int zlib_process(Object* state, Object* bytes, int offset, int length, bool finish) {
    LocalRoot<> state_root(state), bytes_root(bytes);
    compression_bounds(bytes, offset, length);
    auto value = compression(state);
    if (length == 0 || value->finished) return 0;
    std::vector<std::uint8_t> output(static_cast<std::size_t>(length));
    value->stream.next_out = output.data(); value->stream.avail_out = static_cast<uInt>(length);
    int status;
    {
        NativeRegion blocked(ThreadState::blocked_managed);
        status = value->compress ? deflate(&value->stream, finish ? Z_FINISH : Z_NO_FLUSH) : inflate(&value->stream, Z_NO_FLUSH);
    }
    int produced = length - static_cast<int>(value->stream.avail_out);
    value->stream.next_out = nullptr; value->stream.avail_out = 0;
    if (status == Z_MEM_ERROR) raise("java/lang/OutOfMemoryError");
    if (status == Z_DATA_ERROR) raise("java/util/zip/DataFormatException", value->stream.msg ? value->stream.msg : "Invalid compressed data");
    if (status == Z_STREAM_ERROR) raise("java/lang/IllegalStateException", "Invalid compression state");
    value->dictionary = status == Z_NEED_DICT;
    value->finished = status == Z_STREAM_END;
    auto destination = primitive_array<std::int8_t>(bytes);
    for (int i = 0; i < produced; ++i) {
        destination->elements[offset + i].set(static_cast<std::int8_t>(output[i]));
        if ((i & 1023) == 1023) safepoint();
    }
    return produced;
}
int zlib_status(Object* state, int kind) {
    auto value = compression(state);
    return kind == 0 ? static_cast<int>(value->stream.avail_in) : kind == 1 ? value->finished : value->dictionary;
}
void zlib_close(Object* state) {
    auto value = dynamic_cast<Compression*>(require_non_null(state));
    if (value) value->close();
}
}
#endif
