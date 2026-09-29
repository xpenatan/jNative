#pragma once
#include "jn_runtime.hpp"
#include "jn_classlib.hpp"

namespace jnative {
std::int32_t character_lower(std::int32_t code);
std::int32_t character_upper(std::int32_t code);
std::int32_t character_digit(std::int32_t code, std::int32_t radix);
std::int32_t integer_leading_zeros(std::int32_t value);
std::int32_t integer_trailing_zeros(std::int32_t value);
std::int32_t long_leading_zeros(std::int64_t value);
std::int32_t integer_parse(Object* text, std::int32_t radix);
std::int32_t integer_decode(Object* text);
Object* integer_hex(std::int32_t value);
Object* integer_string(std::int32_t value);
std::int64_t long_parse(Object* text, std::int32_t radix);
Object* long_string(std::int64_t value);
Object* long_unsigned_string(std::int64_t value);
struct BigDecimalParsed {
    std::u16string digits;
    std::int32_t scale;
    bool negative;
};
BigDecimalParsed big_decimal_parse(Object* text);
template<class Digits, class Scale, class Negative>
void big_decimal_initialize(Object* receiver, Object* input, Digits digits_field,
                            Scale scale_field, Negative negative_field) {
    LocalRoot<> target(require_non_null(receiver)), text(input);
    auto parsed = big_decimal_parse(text.get());
    LocalRoot<String> digits(allocate<String>(std::move(parsed.digits)));
    // Parsing and allocation finish before publishing any parsed field. These
    // typed final-field stores neither call Java nor introduce a safepoint.
    digits_field.set(target.get(), digits.get());
    scale_field.set(target.get(), parsed.scale);
    negative_field.set(target.get(), static_cast<std::int32_t>(parsed.negative));
}
std::int64_t big_decimal_integral(Object* digits, std::uint8_t negative,
                                 std::int32_t scale, std::uint8_t exact);
std::int32_t big_decimal_compare(Object* digits, std::int32_t scale, Object* other_digits,
                                std::int32_t other_scale, std::int32_t sign, std::int32_t other_sign);
Object* big_decimal_string(Object* digits, std::uint8_t negative, std::int32_t scale);
}
