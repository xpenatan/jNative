#pragma once
#include "jn_runtime.hpp"
#include "jn_buffers.hpp"

namespace jnative {
inline std::int32_t charset_lookup(Object* name) {
    LocalRoot<> input(name);
    const auto& text = as_string(require_non_null(name))->value;
    std::u16string normalized;
    normalized.reserve(text.size());
    for (std::size_t i = 0; i < text.size(); ++i) {
        auto c = text[i];
        normalized += c == u'_' ? u'-' : c >= u'a' && c <= u'z' ? c - (u'a' - u'A') : c;
        if ((i & 1023) == 1023) safepoint();
    }
    if (normalized == u"UTF-8" || normalized == u"UTF8") return 0;
    if (normalized == u"US-ASCII" || normalized == u"ASCII") return 1;
    if (normalized == u"ISO-8859-1" || normalized == u"ISO8859-1" || normalized == u"LATIN1") return 2;
    if (normalized == u"UTF-16BE") return 3;
    if (normalized == u"UTF-16LE") return 4;
    if (normalized == u"UTF-16" || normalized == u"UTF16") return 5;
    return -1;
}
template<class Write> void charset_encode_scan(const std::u16string& text, std::int32_t encoding, Write write) {
    if (encoding == 5 && !text.empty()) { write(0xfe); write(0xff); }
    std::uint32_t work = 0;
    for (std::size_t i = 0; i < text.size(); ++i) {
        std::uint32_t code = text[i]; bool malformed = false;
        if (code >= 0xd800 && code <= 0xdbff) {
            if (i + 1 < text.size() && text[i + 1] >= 0xdc00 && text[i + 1] <= 0xdfff)
                code = 0x10000 + ((code - 0xd800) << 10) + (text[++i] - 0xdc00);
            else malformed = true;
        } else if (code >= 0xdc00 && code <= 0xdfff) malformed = true;
        if (encoding >= 3) {
            auto unit = [&](std::uint32_t value) {
                write(encoding == 4 ? value & 255 : value >> 8);
                write(encoding == 4 ? value >> 8 : value & 255);
            };
            if (malformed) unit(0xfffd);
            else if (code < 0x10000) unit(code);
            else { unit(0xd800 + ((code - 0x10000) >> 10)); unit(0xdc00 + ((code - 0x10000) & 1023)); }
        } else if (encoding == 1 || encoding == 2) write(!malformed && code <= (encoding == 1 ? 127u : 255u) ? code : '?');
        else if (malformed) write('?');
        else if (code < 128) write(code);
        else if (code < 2048) { write(0xc0 | (code >> 6)); write(0x80 | (code & 63)); }
        else if (code < 65536) { write(0xe0 | (code >> 12)); write(0x80 | ((code >> 6) & 63)); write(0x80 | (code & 63)); }
        else { write(0xf0 | (code >> 18)); write(0x80 | ((code >> 12) & 63)); write(0x80 | ((code >> 6) & 63)); write(0x80 | (code & 63)); }
        if ((++work & 1023) == 0) safepoint();
    }
}
inline Object* charset_encode(Object* text, std::int32_t encoding) {
    LocalRoot<> input(text);
    const auto& chars = as_string(require_non_null(text))->value;
    std::int64_t size = 0;
    charset_encode_scan(chars, encoding, [&](std::uint32_t) {
        if (++size > INT32_MAX) raise("java/lang/OutOfMemoryError");
    });
    LocalRoot<> result(new_array("[B", static_cast<std::int32_t>(size)));
    auto output = static_cast<PrimitiveArray<std::int8_t>*>(result.get());
    std::int32_t index = 0;
    charset_encode_scan(chars, encoding, [&](std::uint32_t value) {
        output->elements[index++].set(static_cast<std::int8_t>(value));
    });
    return result.get();
}
// Actions: 0 REPORT, 1 REPLACE, 2 IGNORE. All supported byte decoders map valid
// input to Unicode, so they have no unmappable sequences distinct from malformed input.
template<class Read, class Commit, class Malformed>
Object* charset_decode(std::int32_t encoding, std::int32_t size, std::int32_t action,
        Read read, Commit commit, Malformed malformed) {
    std::u16string output;
    output.reserve(static_cast<std::size_t>(size));
    std::int32_t offset = 0; bool little = encoding == 4;
    if (encoding == 5 && size >= 2) {
        if (read(0) == 0xfe && read(1) == 0xff) offset = 2;
        else if (read(0) == 0xff && read(1) == 0xfe) { little = true; offset = 2; }
    }
    auto error = [&](std::int32_t length) {
        if (action == 0) { commit(offset); malformed(length); }
        if (action == 1) output += u'\ufffd';
        offset += length;
    };
    std::uint32_t work = 0;
    while (offset < size) {
        if ((++work & 1023) == 0) safepoint();
        if (encoding >= 3) {
            if (size - offset < 2) { error(1); continue; }
            auto unit = [&](std::int32_t at) {
                return little ? read(at) | (read(at + 1) << 8) : (read(at) << 8) | read(at + 1);
            };
            auto high = unit(offset);
            if (high >= 0xd800 && high <= 0xdbff) {
                if (size - offset < 4) { error(size - offset); continue; }
                auto low = unit(offset + 2);
                if (low < 0xdc00 || low > 0xdfff) { error(4); continue; }
                output += static_cast<char16_t>(high); output += static_cast<char16_t>(low); offset += 4;
            } else if (high >= 0xdc00 && high <= 0xdfff) error(2);
            else { output += static_cast<char16_t>(high); offset += 2; }
            continue;
        }
        auto first = read(offset);
        if (encoding == 2 || first < 128) { output += static_cast<char16_t>(first); ++offset; continue; }
        if (encoding == 1) { error(1); continue; }
        auto needed = first >= 0xc2 && first <= 0xdf ? 2 : first >= 0xe0 && first <= 0xef ? 3 : first >= 0xf0 && first <= 0xf4 ? 4 : 0;
        if (!needed) { error(1); continue; }
        std::int32_t used = 1;
        while (used < needed && used < size - offset && (read(offset + used) & 0xc0) == 0x80) {
            auto next = read(offset + used);
            if (used == 1 && ((first == 0xe0 && next < 0xa0) || (first == 0xf0 && next < 0x90) || (first == 0xf4 && next > 0x8f))) break;
            ++used;
        }
        if (used < needed) { error(used); continue; }
        auto code = first & (0x7f >> needed);
        for (std::int32_t i = 1; i < needed; ++i) code = (code << 6) | (read(offset + i) & 63);
        if (code >= 0xd800 && code <= 0xdfff) { error(needed); continue; }
        if (code < 0x10000) output += static_cast<char16_t>(code);
        else { output += static_cast<char16_t>(0xd800 + ((code - 0x10000) >> 10)); output += static_cast<char16_t>(0xdc00 + ((code - 0x10000) & 1023)); }
        offset += needed;
    }
    commit(offset);
    return allocate<String>(std::move(output));
}
template<class Malformed>
Object* charset_decode_bytes(Object* bytes, std::int32_t offset, std::int32_t length,
        std::int32_t encoding, std::int32_t action, Malformed malformed) {
    LocalRoot<> input(require_non_null(bytes));
    if (offset < 0 || length < 0 || offset > array_length(bytes) - length) raise("java/lang/IndexOutOfBoundsException");
    return charset_decode(encoding, length, action,
        [&](std::int32_t i) { return static_cast<std::uint8_t>(array_get<std::int8_t>(input.get(), offset + i)); },
        [](std::int32_t) {}, malformed);
}
template<class Malformed>
Object* charset_decode_buffer(Object* buffer, std::int32_t encoding, std::int32_t action,
        std::int32_t unmappable, Malformed malformed) {
    LocalRoot<> input(buffer);
    auto source = as_byte_buffer(input.get());
    const auto begin = source->position.get(), end = source->limit.get();
    (void)unmappable; // No valid sequence is unmappable in the supported byte-to-Unicode decoders.
    return charset_decode(encoding, end - begin, action,
        [&](std::int32_t i) { return static_cast<std::uint8_t>(buffer_read(source, begin + i, 1, false)); },
        [&](std::int32_t consumed) { buffer_position(source, begin + consumed); }, malformed);
}
template<class CharAt> Object* char_sequence_range(Object* sequence, std::int32_t from, std::int32_t to, CharAt char_at) {
    LocalRoot<> text(sequence);
    std::u16string output;
    output.reserve(static_cast<std::size_t>(to - from));
    for (auto i = from; i < to; ++i) {
        output += static_cast<char16_t>(char_at(text.get(), i));
        if ((i & 1023) == 1023) safepoint();
    }
    return allocate<String>(std::move(output));
}
}
