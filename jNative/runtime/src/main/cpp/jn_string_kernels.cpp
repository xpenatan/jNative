#include "jn_string_kernels.hpp"
#include "jn_classlib_numbers.hpp"
#include <algorithm>

namespace jnative {
namespace {
// Count comparisons as well as outer iterations: substring search must poll
// even when a long prefix is repeatedly matched at consecutive positions.
class StringPoll {
    LoopSafepoint safepoint_;
    std::int32_t remaining_ = 1024;
public:
    void step() {
        if (--remaining_ == 0) {
            safepoint_.poll();
            remaining_ = 1024;
        }
    }
};

std::int32_t length(String* text) { return static_cast<std::int32_t>(text->value.size()); }
bool high(char16_t value) { return value >= 0xd800 && value <= 0xdbff; }
bool low(char16_t value) { return value >= 0xdc00 && value <= 0xdfff; }

std::int32_t point_at(const std::u16string& text, std::size_t index) {
    const auto first = text[index];
    if (high(first) && index + 1 < text.size() && low(text[index + 1]))
        return 0x10000 + ((first - 0xd800) << 10) + text[index + 1] - 0xdc00;
    return first;
}
void append_character(std::u16string& output, char16_t value, StringPoll& poll) {
    if (output.size() == static_cast<std::size_t>(INT32_MAX)) raise("java/lang/OutOfMemoryError");
    output += value;
    poll.step();
}
void append_text(std::u16string& output, const std::u16string& input,
                 std::size_t from, std::size_t to, StringPoll& poll) {
    for (auto i = from; i < to; ++i) append_character(output, input[i], poll);
}
void append_point(std::u16string& output, std::int32_t point, StringPoll& poll) {
    if (point <= 0xffff) append_character(output, static_cast<char16_t>(point), poll);
    else {
        append_character(output, static_cast<char16_t>(0xd800 + ((point - 0x10000) >> 10)), poll);
        append_character(output, static_cast<char16_t>(0xdc00 + ((point - 0x10000) & 1023)), poll);
    }
}
std::u16string changed_case(String* text, String* language, bool upper, bool& changed) {
    const bool turkic = language && (language->value == u"tr" || language->value == u"az");
    std::u16string result;
    StringPoll poll;
    for (std::size_t i = 0; i < text->value.size();) {
        const auto code = point_at(text->value, i);
        auto converted = turkic && upper && code == 'i' ? 0x130
            : turkic && !upper && code == 'I' ? 0x131 : unicode_case(code, upper);
        const bool dotted = !turkic && !upper && code == 0x130;
        const bool sharp = upper && code == 0xdf;
        if (!changed && (converted != code || dotted || sharp)) {
            changed = true;
            append_text(result, text->value, 0, i, poll);
        }
        if (changed) {
            if (dotted) { append_character(result, u'i', poll); append_character(result, 0x307, poll); }
            else if (sharp) { append_character(result, u'S', poll); append_character(result, u'S', poll); }
            else append_point(result, converted, poll);
        }
        i += code > 0xffff ? 2 : 1;
        poll.step();
    }
    return result;
}

void copy_characters(String* text, PrimitiveArray<std::uint16_t>* target,
                     std::int32_t from, std::int32_t to, std::int32_t offset) {
    StringPoll poll;
    for (auto i = from; i < to; ++i) {
        target->elements[offset + (i - from)].set(static_cast<std::uint16_t>(text->value[i]));
        poll.step();
    }
}

bool matches_code_point(String* text, std::int32_t index, std::int32_t value) {
    if (value <= 0xffff) return text->value[index] == value;
    const auto code = value - 0x10000;
    return index < length(text) - 1
        && text->value[index] == static_cast<char16_t>(0xd800 + (code >> 10))
        && text->value[index + 1] == static_cast<char16_t>(0xdc00 + (code & 1023));
}
}

Object* string_to_char_array(Object* object) {
    LocalRoot<String> text(as_string(object));
    const auto size = length(text.get());
    LocalRoot<> result(new_array("[C", size));
    copy_characters(text.get(), primitive_array<std::uint16_t>(result.get()), 0, size, 0);
    return result.get();
}

void string_get_chars(Object* object, std::int32_t from, std::int32_t to,
                      Object* destination, std::int32_t offset) {
    LocalRoot<String> text(as_string(object));
    // String.getChars validates source bounds before dereferencing the target.
    if (from < 0 || to < from || to > length(text.get()))
        raise("java/lang/StringIndexOutOfBoundsException");
    LocalRoot<> target_root(destination);
    auto target = primitive_array<std::uint16_t>(target_root.get());
    const auto count = to - from;
    if (offset < 0 || offset > target->length - count)
        raise("java/lang/StringIndexOutOfBoundsException");
    copy_characters(text.get(), target, from, to, offset);
}

std::int32_t string_index_of_code_point(Object* object, std::int32_t value, std::int32_t start) {
    LocalRoot<String> text(as_string(object));
    if (value < 0 || value > 0x10ffff) return -1;
    StringPoll poll;
    const auto size = length(text.get());
    for (auto i = std::max(std::int32_t(0), start); i < size; ++i) {
        if (matches_code_point(text.get(), i, value)) return i;
        poll.step();
    }
    return -1;
}

std::int32_t string_index_of_text(Object* object, Object* part, std::int32_t start) {
    LocalRoot<String> text(as_string(object));
    LocalRoot<String> needle(as_string(part));
    const auto size = length(text.get());
    const auto needle_size = length(needle.get());
    const auto begin = std::max(std::int32_t(0), start);
    if (needle_size == 0) return std::min(begin, size);
    const auto limit = size - needle_size;
    StringPoll poll;
    for (auto i = begin; i <= limit; ++i) {
        bool match = true;
        for (std::int32_t j = 0; j < needle_size; ++j) {
            const bool same = text.get()->value[i + j] == needle.get()->value[j];
            poll.step();
            if (!same) {
                match = false;
                break;
            }
        }
        if (match) return i;
    }
    return -1;
}

std::int32_t string_last_index_of_code_point(Object* object, std::int32_t value, std::int32_t start) {
    LocalRoot<String> text(as_string(object));
    if (value < 0 || value > 0x10ffff) return -1;
    StringPoll poll;
    for (auto i = std::min(start, length(text.get()) - 1); i >= 0; --i) {
        if (matches_code_point(text.get(), i, value)) return i;
        poll.step();
    }
    return -1;
}

std::uint8_t string_starts_with(Object* object, Object* prefix, std::int32_t offset) {
    LocalRoot<String> text(as_string(object));
    if (offset < 0) return 0;
    LocalRoot<String> needle(as_string(prefix));
    const auto size = length(needle.get());
    if (offset > length(text.get()) - size) return 0;
    StringPoll poll;
    for (std::int32_t i = 0; i < size; ++i) {
        const bool same = text.get()->value[offset + i] == needle.get()->value[i];
        poll.step();
        if (!same) return 0;
    }
    return 1;
}

std::int32_t string_code_point_count(Object* object, std::int32_t from, std::int32_t to) {
    LocalRoot<String> text(as_string(object));
    if (from < 0 || to < from || to > length(text.get()))
        raise("java/lang/IndexOutOfBoundsException");
    StringPoll poll;
    std::int32_t count = 0;
    for (auto i = from; i < to; ++count) {
        const auto first = text.get()->value[i++];
        if (high(first) && i < to && low(text.get()->value[i])) ++i;
        poll.step();
    }
    return count;
}

std::int32_t string_offset_by_code_points(Object* object, std::int32_t index, std::int32_t offset) {
    LocalRoot<String> text(as_string(object));
    const auto size = length(text.get());
    if (index < 0 || index > size) raise("java/lang/IndexOutOfBoundsException");
    StringPoll poll;
    while (offset > 0) {
        if (index == size) raise("java/lang/IndexOutOfBoundsException");
        const auto first = text.get()->value[index++];
        if (high(first) && index < size && low(text.get()->value[index])) ++index;
        --offset;
        poll.step();
    }
    while (offset < 0) {
        if (index == 0) raise("java/lang/IndexOutOfBoundsException");
        const auto last = text.get()->value[--index];
        if (low(last) && index > 0 && high(text.get()->value[index - 1])) --index;
        ++offset;
        poll.step();
    }
    return index;
}

std::int32_t string_compare_ignore_case(Object* object, Object* other) {
    LocalRoot<String> text(as_string(object)), next(as_string(other));
    const auto size = std::min(length(text.get()), length(next.get()));
    StringPoll poll;
    for (std::int32_t i = 0; i < size; ++i) {
        const auto first = unicode_case(unicode_case(point_at(text->value, i), true), false);
        const auto second = unicode_case(unicode_case(point_at(next->value, i), true), false);
        if (first != second) return first - second;
        if (first > 0xffff) ++i;
        poll.step();
    }
    return length(text.get()) - length(next.get());
}

std::uint8_t string_is_blank(Object* object) {
    LocalRoot<String> text(as_string(object));
    StringPoll poll;
    for (std::size_t i = 0; i < text->value.size();) {
        const auto code = point_at(text->value, i);
        if ((unicode_info(code) & 256) == 0) return 0;
        i += code > 0xffff ? 2 : 1;
        poll.step();
    }
    return 1;
}

Object* string_replace_char(Object* object, std::uint16_t before, std::uint16_t after) {
    LocalRoot<String> text(as_string(object));
    if (before == after) return object;
    StringPoll poll;
    std::u16string result;
    bool changed = false;
    for (std::size_t i = 0; i < text->value.size(); ++i) {
        const auto code = text->value[i];
        if (!changed && code == before) { changed = true; append_text(result, text->value, 0, i, poll); }
        if (changed) append_character(result, code == before ? after : code, poll);
        poll.step();
    }
    return changed ? allocate<String>(std::move(result)) : object;
}

Object* string_replace_text(Object* object, Object* needle_object, Object* replacement_object) {
    LocalRoot<String> text(as_string(object)), needle(as_string(needle_object)), replacement(as_string(replacement_object));
    StringPoll poll;
    std::u16string result;
    if (needle->value.empty()) {
        append_text(result, replacement->value, 0, replacement->value.size(), poll);
        for (auto code : text->value) {
            append_character(result, code, poll);
            append_text(result, replacement->value, 0, replacement->value.size(), poll);
        }
        return allocate<String>(std::move(result));
    }
    std::size_t offset = 0;
    bool changed = false;
    while (offset + needle->value.size() <= text->value.size()) {
        bool match = true;
        for (std::size_t j = 0; j < needle->value.size(); ++j) {
            const bool same = text->value[offset + j] == needle->value[j];
            poll.step();
            if (!same) { match = false; break; }
        }
        if (match) {
            if (!changed) append_text(result, text->value, 0, offset, poll);
            changed = true;
            append_text(result, replacement->value, 0, replacement->value.size(), poll);
            offset += needle->value.size();
        } else {
            if (changed) append_character(result, text->value[offset], poll);
            ++offset;
        }
    }
    if (!changed) return object;
    append_text(result, text->value, offset, text->value.size(), poll);
    return allocate<String>(std::move(result));
}

Object* string_repeat(Object* object, std::int32_t count) {
    // The facade checks count before reading text, matching the previous driver.
    if (count < 0) raise("java/lang/IllegalArgumentException", "Negative repeat count");
    LocalRoot<String> text(as_string(object));
    if (text->value.empty() || count == 0) return literal(u"");
    if (count == 1) return object;
    const auto size = static_cast<std::int64_t>(text->value.size()) * count;
    if (size > INT32_MAX) raise("java/lang/OutOfMemoryError");
    std::u16string result;
    result.reserve(static_cast<std::size_t>(size));
    StringPoll poll;
    for (std::int32_t i = 0; i < count; ++i) append_text(result, text->value, 0, text->value.size(), poll);
    return allocate<String>(std::move(result));
}

Object* string_change_case(Object* object, Object* language_object, std::uint8_t upper) {
    LocalRoot<String> text(as_string(object)), language(as_string(language_object));
    bool changed = false;
    auto result = changed_case(text.get(), language.get(), upper != 0, changed);
    return changed ? allocate<String>(std::move(result)) : object;
}

void string_assign(Object* target, Object* value) {
    LocalRoot<String> output(as_string(target)), input(as_string(value));
    std::u16string result;
    StringPoll poll;
    append_text(result, input->value, 0, input->value.size(), poll);
    output->value = std::move(result);
}

Object* string_from_char_range(Object* object, std::int32_t from, std::int32_t to) {
    LocalRoot<> characters(object);
    const auto array = primitive_array<std::uint16_t>(characters.get());
    if (from < 0 || to < from || to > array->length) raise("java/lang/StringIndexOutOfBoundsException");
    std::u16string result;
    result.reserve(static_cast<std::size_t>(to - from));
    StringPoll poll;
    for (auto i = from; i < to; ++i) append_character(result, array->elements[i].get(), poll);
    return allocate<String>(std::move(result));
}

Object* enum_value_of(Object* type_object, Object* name_object, Object* (*constants)(Object*),
                      Object* (*name_of)(Object*), Object* (*type_text)(Object*), Object* (*type_name)(Object*)) {
    if (!name_object) raise("java/lang/NullPointerException", "Name is null");
    LocalRoot<String> name(as_string(name_object));
    LocalRoot<> type(require_non_null(type_object)), values(constants(type.get())), candidate;
    StringPoll poll;
    if (!values.get()) {
        LocalRoot<String> description(as_string(type_text(type.get())));
        std::u16string message = u"Not an enum: ";
        append_text(message, description->value, 0, description->value.size(), poll);
        LocalRoot<String> error_text(allocate<String>(std::move(message)));
        raise_with_message("java/lang/IllegalArgumentException", error_text.get());
    }
    const auto array = reference_array(values.get());
    for (std::int32_t i = 0; i < array->length; ++i) {
        candidate.set(array->elements[i].get());
        LocalRoot<String> spelling(as_string(name_of(candidate.get())));
        bool match = spelling->value.size() == name->value.size();
        if (match) for (std::size_t j = 0; j < name->value.size(); ++j) {
            const bool same = spelling->value[j] == name->value[j];
            poll.step();
            if (!same) { match = false; break; }
        }
        if (match) return candidate.get();
        poll.step();
    }
    LocalRoot<String> owner(as_string(type_name(type.get())));
    std::u16string message = u"No enum constant ";
    append_text(message, owner->value, 0, owner->value.size(), poll);
    append_character(message, u'.', poll);
    append_text(message, name->value, 0, name->value.size(), poll);
    LocalRoot<String> error_text(allocate<String>(std::move(message)));
    raise_with_message("java/lang/IllegalArgumentException", error_text.get());
}

Object* string_format(Object* locale_object, Object* pattern_object, Object* argument_object,
                      Object* (*object_text)(Object*), std::int64_t (*long_value)(Object*),
                      std::int32_t (*int_value)(Object*), double (*double_value)(Object*),
                      std::int32_t (*boolean_value)(Object*),
                      Object* (*language)(Object*), Object* (*property)(Object*)) {
    LocalRoot<String> pattern(as_string(pattern_object));
    LocalRoot<> locale(locale_object), arguments(argument_object), value;
    LocalRoot<String> text, locale_language;
    const auto array = arguments.get() ? reference_array(arguments.get()) : nullptr;
    std::u16string result;
    StringPoll poll;
    std::int32_t argument = 0, previous = -1;
    const auto size = length(pattern.get());
    // This parser deliberately preserves the emulated formatter's supported
    // conversions and flag/argument ordering rather than extending its API.
    for (std::int32_t i = 0; i < size; ++i) {
        if (pattern->value[i] != u'%') { append_character(result, pattern->value[i], poll); continue; }
        ++i;
        if (i == size) raise("java/lang/IllegalArgumentException", "Incomplete format");
        const auto start = i;
        std::int32_t index = 0;
        auto decimal = [&](std::int32_t& number) {
            while (i < size && pattern->value[i] >= u'0' && pattern->value[i] <= u'9') {
                const auto digit = pattern->value[i++] - u'0';
                // The Java parser used Math.multiplyExact then Math.addExact.
                if (number > INT32_MAX / 10 || number * 10 > INT32_MAX - digit)
                    raise("java/lang/ArithmeticException", "integer overflow");
                number = number * 10 + digit;
                poll.step();
            }
        };
        decimal(index);
        const bool explicit_index = i < size && pattern->value[i] == u'$';
        if (explicit_index) { --index; ++i; }
        else { i = start; index = -1; }
        bool left = false, zero = false, plus = false, space = false, relative = false;
        while (i < size) {
            const auto flag = pattern->value[i];
            if (flag == u'-') left = true;
            else if (flag == u'0') zero = true;
            else if (flag == u'+') plus = true;
            else if (flag == u' ') space = true;
            else if (flag == u'<') relative = true;
            else break;
            ++i;
            poll.step();
        }
        std::int32_t width = 0, precision = -1;
        decimal(width);
        if (i < size && pattern->value[i] == u'.') { precision = 0; ++i; decimal(precision); }
        if (i == size) raise("java/lang/IllegalArgumentException", "Incomplete format");
        const auto conversion = pattern->value[i];
        if (conversion == u'%') { append_character(result, u'%', poll); continue; }
        if (conversion == u'n') {
            LocalRoot<> key(literal(u"line.separator"));
            LocalRoot<> newline(property(key.get()));
            if (!newline.get()) append_text(result, u"null", 0, 4, poll);
            else {
                const auto spelling = as_string(newline.get());
                append_text(result, spelling->value, 0, spelling->value.size(), poll);
            }
            continue;
        }
        index = relative ? previous : explicit_index ? index : argument++;
        if (index < 0 || (array && index >= array->length))
            raise("java/lang/IllegalArgumentException", "Missing format argument");
        previous = index;
        value.set(array ? array->elements[index].get() : nullptr);
        const auto lower = static_cast<char16_t>(unicode_case(conversion, false));
        if (lower == u's') text.set(value.get() ? as_string(object_text(value.get())) : literal(u"null"));
        else if (lower == u'b') {
            const bool flag = instance_of(value.get(), "java/lang/Boolean") ? boolean_value(value.get()) != 0 : value.get() != nullptr;
            text.set(literal(flag ? u"true" : u"false"));
        } else if (!value.get()) text.set(literal(u"null"));
        else if (lower == u'c') {
            if (instance_of(value.get(), "java/lang/Character")) text.set(as_string(object_text(value.get())));
            else {
                const auto code = int_value(check_cast(value.get(), "java/lang/Number"));
                if (code < 0 || code > 0x10ffff) raise("java/lang/IllegalArgumentException", "Invalid code point");
                std::u16string spelling;
                append_point(spelling, code, poll);
                text.set(allocate<String>(std::move(spelling)));
            }
        } else if (lower == u'd') text.set(as_string(long_string(long_value(check_cast(value.get(), "java/lang/Number")))));
        else if (lower == u'x') text.set(as_string(integer_hex(int_value(check_cast(value.get(), "java/lang/Number")))));
        else if (lower == u'f' || lower == u'e' || lower == u'g') {
            const auto number = double_value(check_cast(value.get(), "java/lang/Number"));
            text.set(as_string(format_decimal(number, precision < 0 ? 6 : precision, lower)));
        } else {
            std::u16string message = u"Unsupported format conversion: ";
            message += conversion;
            raise("java/lang/UnsupportedOperationException", utf8(message).c_str());
        }
        if (lower == u's' && precision >= 0 && text->value.size() > static_cast<std::size_t>(precision)) {
            std::u16string clipped;
            append_text(clipped, text->value, 0, static_cast<std::size_t>(precision), poll);
            text.set(allocate<String>(std::move(clipped)));
        }
        if (conversion != lower) {
            locale_language.set(locale.get() ? as_string(language(locale.get())) : nullptr);
            bool changed = false;
            auto spelling = changed_case(text.get(), locale_language.get(), true, changed);
            if (changed) text.set(allocate<String>(std::move(spelling)));
        }
        std::u16string spelling;
        if ((lower == u'd' || lower == u'f' || lower == u'e' || lower == u'g')
                && value.get() && (text->value.empty() || text->value[0] != u'-') && text->value != u"NaN") {
            if (plus) spelling += u'+';
            else if (space) spelling += u' ';
        }
        append_text(spelling, text->value, 0, text->value.size(), poll);
        const auto padding = std::max(std::int32_t(0), width - static_cast<std::int32_t>(spelling.size()));
        std::size_t begin = 0;
        if (!left && zero && padding > 0 && !spelling.empty()
                && (spelling[0] == u'-' || spelling[0] == u'+' || spelling[0] == u' ')) {
            append_character(result, spelling[0], poll);
            begin = 1;
        }
        if (!left) for (std::int32_t j = 0; j < padding; ++j) append_character(result, zero ? u'0' : u' ', poll);
        append_text(result, spelling, begin, spelling.size(), poll);
        if (left) for (std::int32_t j = 0; j < padding; ++j) append_character(result, u' ', poll);
    }
    return allocate<String>(std::move(result));
}
}
