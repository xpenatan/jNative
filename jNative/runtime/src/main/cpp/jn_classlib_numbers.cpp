#include "jn_classlib_numbers.hpp"
#include <algorithm>
#include <limits>

namespace jnative {
namespace {
class NumberPoll {
    LoopSafepoint safepoint_;
    unsigned remaining_ = 1024;
public:
    void step() { if (--remaining_ == 0) { safepoint_.poll(); remaining_ = 1024; } }
};
const std::int32_t decimal_zeroes[] = {
    0x30, 0x660, 0x6F0, 0x7C0, 0x966, 0x9E6, 0xA66, 0xAE6,
    0xB66, 0xBE6, 0xC66, 0xCE6, 0xD66, 0xDE6, 0xE50, 0xED0,
    0xF20, 0x1040, 0x1090, 0x17E0, 0x1810, 0x1946, 0x19D0, 0x1A80,
    0x1A90, 0x1B50, 0x1BB0, 0x1C40, 0x1C50, 0xA620, 0xA8D0, 0xA900,
    0xA9D0, 0xA9F0, 0xAA50, 0xABF0, 0xFF10, 0x104A0, 0x10D30, 0x10D40,
    0x11066, 0x110F0, 0x11136, 0x111D0, 0x112F0, 0x11450, 0x114D0, 0x11650,
    0x116C0, 0x116D0, 0x116DA, 0x11730, 0x118E0, 0x11950, 0x11BF0, 0x11C50,
    0x11D50, 0x11DA0, 0x11F50, 0x16130, 0x16A60, 0x16AC0, 0x16B50, 0x16D70,
    0x1CCF0, 0x1D7CE, 0x1D7D8, 0x1D7E2, 0x1D7EC, 0x1D7F6, 0x1E140, 0x1E2F0,
    0x1E4F0, 0x1E5F1, 0x1E950, 0x1FBF0
};
[[noreturn]] void bad_number(String* text) {
    raise_with_message("java/lang/NumberFormatException", text);
}
template<class T> T parse_span(String* text, std::size_t from, std::size_t to,
                              std::int32_t radix, bool negative) {
    if (from == to) bad_number(text);
    const T limit = negative ? std::numeric_limits<T>::min() : -std::numeric_limits<T>::max();
    T value = 0;
    NumberPoll poll;
    for (auto i = from; i < to; ++i) {
        const auto digit = character_digit(text->value[i], radix);
        if (digit < 0 || value < limit / radix) bad_number(text);
        value *= radix;
        if (value < limit + digit) bad_number(text);
        value -= digit;
        poll.step();
    }
    return negative ? value : -value;
}
template<class T> T parse_number(Object* object, std::int32_t radix) {
    if (!object) bad_number(nullptr);
    LocalRoot<String> text(as_string(object));
    if (text->value.empty() || radix < 2 || radix > 36) bad_number(text.get());
    const bool negative = text->value[0] == u'-';
    const auto from = negative || text->value[0] == u'+' ? 1u : 0u;
    return parse_span<T>(text.get(), from, text->value.size(), radix, negative);
}
void append_span(std::u16string& output, const std::u16string& source,
                 std::size_t from, std::size_t to, NumberPoll& poll) {
    for (auto i = from; i < to; ++i) { output += source[i]; poll.step(); }
}
}

std::int32_t character_lower(std::int32_t code) { return unicode_case(code, false); }
std::int32_t character_upper(std::int32_t code) { return unicode_case(code, true); }
std::int32_t character_digit(std::int32_t code, std::int32_t radix) {
    if (radix < 2 || radix > 36) return -1;
    std::int32_t value;
    if (code >= '0' && code <= '9') value = code - '0';
    else if (code >= 'a' && code <= 'z') value = code - 'a' + 10;
    else if (code >= 'A' && code <= 'Z') value = code - 'A' + 10;
    else if (code >= 0xff41 && code <= 0xff5a) value = code - 0xff41 + 10;
    else if (code >= 0xff21 && code <= 0xff3a) value = code - 0xff21 + 10;
    else {
        const auto end = decimal_zeroes + sizeof(decimal_zeroes) / sizeof(decimal_zeroes[0]);
        const auto found = std::upper_bound(decimal_zeroes, end, code);
        if (found == decimal_zeroes) return -1;
        value = code - found[-1];
        if (value > 9) return -1;
    }
    return value < radix ? value : -1;
}
std::int32_t integer_leading_zeros(std::int32_t value) {
    if (value == 0) return 32;
    std::uint32_t bits = static_cast<std::uint32_t>(value);
    std::int32_t count = 0;
    while ((bits & UINT32_C(0x80000000)) == 0) { ++count; bits <<= 1; }
    return count;
}
std::int32_t integer_trailing_zeros(std::int32_t value) {
    if (value == 0) return 32;
    std::uint32_t bits = static_cast<std::uint32_t>(value);
    std::int32_t count = 0;
    while ((bits & UINT32_C(1)) == 0) { ++count; bits >>= 1; }
    return count;
}
std::int32_t long_leading_zeros(std::int64_t value) {
    if (value == 0) return 64;
    std::uint64_t bits = static_cast<std::uint64_t>(value);
    std::int32_t count = 0;
    while ((bits & UINT64_C(0x8000000000000000)) == 0) { ++count; bits <<= 1; }
    return count;
}
std::int32_t integer_parse(Object* text, std::int32_t radix) { return parse_number<std::int32_t>(text, radix); }
std::int64_t long_parse(Object* text, std::int32_t radix) { return parse_number<std::int64_t>(text, radix); }
std::int32_t integer_decode(Object* object) {
    if (!object) bad_number(nullptr);
    LocalRoot<String> text(as_string(object));
    if (text->value.empty()) bad_number(text.get());
    std::size_t from = text->value[0] == u'+' || text->value[0] == u'-' ? 1 : 0;
    const bool negative = text->value[0] == u'-';
    std::int32_t radix = 10;
    if (from + 1 < text->value.size() && text->value[from] == u'0'
            && (text->value[from + 1] == u'x' || text->value[from + 1] == u'X')) {
        from += 2; radix = 16;
    } else if (from < text->value.size() && text->value[from] == u'#') {
        ++from; radix = 16;
    } else if (from + 1 < text->value.size() && text->value[from] == u'0') {
        ++from; radix = 8;
    }
    if (from < text->value.size() && (text->value[from] == u'+' || text->value[from] == u'-'))
        bad_number(text.get());
    std::u16string spelling = negative ? u"-" : u"";
    NumberPoll poll;
    append_span(spelling, text->value, from, text->value.size(), poll);
    LocalRoot<String> normalized(allocate<String>(std::move(spelling)));
    return integer_parse(normalized.get(), radix);
}
Object* integer_hex(std::int32_t value) {
    if (value == 0) return literal(u"0");
    std::uint32_t bits = static_cast<std::uint32_t>(value);
    char16_t buffer[8];
    unsigned from = 8;
    do { buffer[--from] = u"0123456789abcdef"[bits & 15]; bits >>= 4; } while (bits);
    return allocate<String>(std::u16string(buffer + from, buffer + 8));
}
Object* integer_string(std::int32_t value) { return allocate<String>(utf16(std::to_string(value))); }
Object* long_string(std::int64_t value) { return allocate<String>(utf16(std::to_string(value))); }
Object* long_unsigned_string(std::int64_t value) {
    return allocate<String>(utf16(std::to_string(static_cast<std::uint64_t>(value))));
}

BigDecimalParsed big_decimal_parse(Object* object) {
    LocalRoot<String> text(as_string(object));
    const auto& input = text->value;
    const bool negative = !input.empty() && input[0] == u'-';
    std::size_t index = !input.empty() && (input[0] == u'+' || input[0] == u'-') ? 1 : 0;
    std::u16string digits;
    bool point = false;
    std::int32_t fractional = 0;
    NumberPoll poll;
    while (index < input.size()) {
        const auto character = input[index];
        if (character == u'e' || character == u'E') break;
        if (character == u'.' && !point) { point = true; ++index; continue; }
        const auto digit = character_digit(character, 10);
        if (digit < 0) bad_number(text.get());
        digits += static_cast<char16_t>(u'0' + digit);
        if (point) ++fractional;
        ++index;
        poll.step();
    }
    if (digits.empty()) bad_number(text.get());
    std::int64_t exponent = 0;
    if (index != input.size()) {
        std::u16string suffix;
        append_span(suffix, input, index + 1, input.size(), poll);
        LocalRoot<String> exponent_text(allocate<String>(std::move(suffix)));
        exponent = long_parse(exponent_text.get(), 10);
    }
    if (exponent < INT32_MIN || exponent > INT32_MAX)
        raise("java/lang/NumberFormatException", "Exponent overflow");
    const auto scale = static_cast<std::int64_t>(fractional) - exponent;
    if (scale < INT32_MIN || scale > INT32_MAX)
        raise("java/lang/NumberFormatException", "Scale overflow");
    std::size_t first = 0;
    while (first + 1 < digits.size() && digits[first] == u'0') { ++first; poll.step(); }
    std::u16string normalized;
    append_span(normalized, digits, first, digits.size(), poll);
    const bool signed_value = negative && normalized != u"0";
    return BigDecimalParsed{std::move(normalized), static_cast<std::int32_t>(scale), signed_value};
}

std::int64_t big_decimal_integral(Object* object, std::uint8_t negative,
                                 std::int32_t scale, std::uint8_t exact) {
    LocalRoot<String> text(as_string(object));
    const auto size = static_cast<std::int64_t>(text->value.size());
    const auto count = size - scale;
    NumberPoll poll;
    if (exact) {
        if (count <= 0) raise("java/lang/ArithmeticException", "Rounding necessary");
        if (count > 19) raise("java/lang/ArithmeticException", "Overflow");
        for (auto i = std::min(count, size); i < size; ++i) {
            if (text->value[static_cast<std::size_t>(i)] != u'0')
                raise("java/lang/ArithmeticException", "Rounding necessary");
            poll.step();
        }
    }
    if (count <= 0 || (!exact && count - size >= 64)) return 0;
    const auto limit = negative ? INT64_MIN : -INT64_MAX;
    std::int64_t value = 0;
    for (std::int64_t i = 0; i < count; ++i) {
        const std::int64_t digit = i < size ? text->value[static_cast<std::size_t>(i)] - u'0' : 0;
        if (exact && (value < limit / 10 || value * 10 < limit + digit))
            raise("java/lang/ArithmeticException", "Overflow");
        value = sub(mul(value, std::int64_t(10)), digit);
        poll.step();
    }
    return negative ? value : sub(std::int64_t(0), value);
}

std::int32_t big_decimal_compare(Object* object, std::int32_t scale, Object* other,
                                std::int32_t other_scale, std::int32_t sign, std::int32_t other_sign) {
    // signum is overridable; the Java facade reads both signs before this driver.
    if (sign != other_sign) return sign < other_sign ? -1 : 1;
    if (sign == 0) return 0;
    LocalRoot<String> text(as_string(object));
    LocalRoot<String> second(as_string(other));
    const auto exponent = static_cast<std::int64_t>(text->value.size()) - scale;
    const auto other_exponent = static_cast<std::int64_t>(second->value.size()) - other_scale;
    if (exponent != other_exponent) return mul(exponent < other_exponent ? -1 : 1, sign);
    NumberPoll poll;
    const auto count = std::max(text->value.size(), second->value.size());
    for (std::size_t i = 0; i < count; ++i) {
        const auto first = i < text->value.size() ? text->value[i] : u'0';
        const auto next = i < second->value.size() ? second->value[i] : u'0';
        if (first != next) return mul(first < next ? -1 : 1, sign);
        poll.step();
    }
    return 0;
}

Object* big_decimal_string(Object* object, std::uint8_t negative, std::int32_t scale) {
    LocalRoot<String> text(as_string(object));
    const auto& digits = text->value;
    const auto exponent = static_cast<std::int64_t>(digits.size()) - scale - 1;
    std::u16string result = negative ? u"-" : u"";
    NumberPoll poll;
    if (scale >= 0 && exponent >= -6) {
        const auto point = static_cast<std::int64_t>(digits.size()) - scale;
        if (scale == 0) append_span(result, digits, 0, digits.size(), poll);
        else if (point > 0) {
            append_span(result, digits, 0, static_cast<std::size_t>(point), poll);
            result += u'.';
            append_span(result, digits, static_cast<std::size_t>(point), digits.size(), poll);
        } else {
            result += u"0.";
            for (std::int64_t i = point; i < 0; ++i) { result += u'0'; poll.step(); }
            append_span(result, digits, 0, digits.size(), poll);
        }
    } else {
        result += digits[0];
        if (digits.size() > 1) { result += u'.'; append_span(result, digits, 1, digits.size(), poll); }
        result += u'E';
        if (exponent >= 0) result += u'+';
        result += utf16(std::to_string(exponent));
    }
    if (result.size() > static_cast<std::size_t>(INT32_MAX)) raise("java/lang/OutOfMemoryError");
    return allocate<String>(std::move(result));
}
}
