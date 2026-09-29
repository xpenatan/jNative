#pragma once
#include "jn_runtime.hpp"
#include "jn_classlib.hpp"
#include "jn_arrays.hpp"

namespace jnative {
// UTF-16 operations may allocate or poll; callers must treat them as collecting.
Object* string_to_char_array(Object* text);
void string_get_chars(Object* text, std::int32_t from, std::int32_t to,
                      Object* target, std::int32_t offset);
std::int32_t string_index_of_code_point(Object* text, std::int32_t value, std::int32_t start);
std::int32_t string_index_of_text(Object* text, Object* part, std::int32_t start);
std::int32_t string_last_index_of_code_point(Object* text, std::int32_t value, std::int32_t start);
std::uint8_t string_starts_with(Object* text, Object* prefix, std::int32_t offset);
std::int32_t string_code_point_count(Object* text, std::int32_t from, std::int32_t to);
std::int32_t string_offset_by_code_points(Object* text, std::int32_t index, std::int32_t offset);
std::int32_t string_compare_ignore_case(Object* text, Object* other);
std::uint8_t string_is_blank(Object* text);
Object* string_replace_char(Object* text, std::uint16_t before, std::uint16_t after);
Object* string_replace_text(Object* text, Object* needle, Object* replacement);
Object* string_repeat(Object* text, std::int32_t count);
Object* string_change_case(Object* text, Object* language, std::uint8_t upper);
void string_assign(Object* target, Object* value);
Object* string_from_char_range(Object* characters, std::int32_t from, std::int32_t to);
template<class CharAt, class Data, class Count>
void builder_append_sequence(Object* builder, Object* sequence, std::int32_t from, std::int32_t to,
                             CharAt char_at, Data data, Count count) {
    RootFrame<4> roots;
    FrameRoot<> target(roots.slot(0), require_non_null(builder));
    FrameRoot<> input(roots.slot(1), require_non_null(sequence));
    FrameRoot<> storage(roots.slot(2));
    FrameRoot<> grown(roots.slot(3));
    LoopSafepoint poll;
    for (auto i = from; i < to; ++i) {
        const auto character = char_at(input.get(), i);
        // charAt can reenter the builder and replace both storage and count.
        const auto position = count.get(target.get());
        const auto required = add(position, std::int32_t(1));
        if (required < 0) raise("java/lang/OutOfMemoryError");
        storage.set(data.get(target.get()));
        auto array = primitive_array<std::uint16_t>(storage.get());
        if (required > array->length) {
            auto capacity = add(mul(array->length, std::int32_t(2)), std::int32_t(2));
            if (capacity < required) capacity = required;
            grown.set(new_array("[C", capacity));
            array_copy_cooperative(storage.get(), 0, grown.get(), 0, position);
            data.set(target.get(), grown.get());
            storage.set(grown.get());
            grown.set(nullptr);
            array = primitive_array<std::uint16_t>(storage.get());
        }
        array->elements[position].set(character);
        count.set(target.get(), required);
        storage.set(nullptr);
        if (((i - from) & 1023) == 1023) poll.poll();
    }
}
Object* enum_value_of(Object* type, Object* name, Object* (*constants)(Object*), Object* (*name_of)(Object*),
                      Object* (*type_text)(Object*), Object* (*type_name)(Object*));
Object* string_format(Object* locale, Object* pattern, Object* arguments,
                      Object* (*object_text)(Object*), std::int64_t (*long_value)(Object*),
                      std::int32_t (*int_value)(Object*), double (*double_value)(Object*),
                      std::int32_t (*boolean_value)(Object*),
                      Object* (*language)(Object*), Object* (*property)(Object*));
}
