#pragma once
#include "jn_collections.hpp"

namespace jnative {
template<class T> void array_sift(Object* array, std::int32_t offset, std::int32_t root, std::int32_t size) {
    while (root < size / 2) {
        auto child = root * 2 + 1;
        if (child + 1 < size && array_get<T>(array, offset + child) < array_get<T>(array, offset + child + 1)) ++child;
        if (array_get<T>(array, offset + root) >= array_get<T>(array, offset + child)) return;
        auto value = array_get<T>(array, offset + root);
        array_set<T>(array, offset + root, array_get<T>(array, offset + child));
        array_set<T>(array, offset + child, value);
        root = child;
    }
}
template<class T> void array_sort_primitive(Object* array, std::int32_t from, std::int32_t to) {
    LocalRoot<> input(array);
    if (from > to) raise("java/lang/IllegalArgumentException");
    if (from < 0 || to > array_length(array)) raise("java/lang/ArrayIndexOutOfBoundsException");
    const auto size = to - from;
    for (auto root = size / 2 - 1; root >= 0; --root) {
        array_sift<T>(input.get(), from, root, size);
        if ((root & 255) == 0) safepoint();
    }
    for (auto end = size - 1; end > 0; --end) {
        const auto first = array_get<T>(input.get(), from);
        array_set<T>(input.get(), from, array_get<T>(input.get(), from + end));
        array_set<T>(input.get(), from + end, first);
        array_sift<T>(input.get(), from, 0, end);
        if ((end & 255) == 0) safepoint();
    }
}
inline void arrays_sort_i(Object* array) { array_sort_primitive<std::int32_t>(array, 0, array_length(array)); }
inline void arrays_sort_j(Object* array, std::int32_t from, std::int32_t to) { array_sort_primitive<std::int64_t>(array, from, to); }
inline std::int32_t arrays_search_i(Object* array, std::int32_t key) {
    LocalRoot<> input(array);
    std::int32_t low = 0, high = array_length(array) - 1;
    while (low <= high) {
        auto middle = low + (high - low) / 2;
        if (array_get<std::int32_t>(input.get(), middle) < key) low = middle + 1;
        else if (array_get<std::int32_t>(input.get(), middle) > key) high = middle - 1;
        else return middle;
    }
    return -low - 1;
}
template<class Equals> std::uint8_t arrays_equal_object(Object* first, Object* second, Equals equals) {
    if (first == second) return true;
    if (!first || !second || array_length(first) != array_length(second)) return false;
    LocalRoot<> a(first), b(second), left, right;
    const auto count = array_length(first);
    for (std::int32_t i = 0; i < count; ++i) {
        left.set(reference_get(a.get(), i)); right.set(reference_get(b.get(), i));
        if (!collection_equal(left.get(), right.get(), equals)) return false;
        if ((i & 1023) == 1023) safepoint();
    }
    return true;
}
template<class Hash> std::int32_t arrays_hash_object(Object* array, Hash hash) {
    if (!array) return 0;
    LocalRoot<> input(array), element;
    std::int32_t result = 1;
    const auto count = array_length(array);
    for (std::int32_t i = 0; i < count; ++i) {
        element.set(reference_get(input.get(), i));
        result = add(mul(result, 31), element.get() ? hash(element.get()) : 0);
        if ((i & 1023) == 1023) safepoint();
    }
    return result;
}
template<class Compare, class Natural>
void arrays_merge(Object* array, Object* scratch, std::int32_t from, std::int32_t to,
        Object* comparator, Compare compare, Natural natural, LocalRoot<>& left_value, LocalRoot<>& right_value) {
    if (to - from < 2) return;
    auto middle = from + (to - from) / 2;
    arrays_merge(array, scratch, from, middle, comparator, compare, natural, left_value, right_value);
    arrays_merge(array, scratch, middle, to, comparator, compare, natural, left_value, right_value);
    std::int32_t left = from, right = middle, count = 0;
    while (left < middle && right < to) {
        left_value.set(reference_get(array, left)); right_value.set(reference_get(array, right));
        auto order = comparator ? compare(comparator, left_value.get(), right_value.get())
            : natural(check_cast(left_value.get(), "java/lang/Comparable"), right_value.get());
        reference_set(scratch, count++, reference_get(array, order <= 0 ? left++ : right++));
        if ((count & 1023) == 0) safepoint();
    }
    while (left < middle) {
        reference_set(scratch, count++, reference_get(array, left++));
        if ((count & 1023) == 0) safepoint();
    }
    while (right < to) {
        reference_set(scratch, count++, reference_get(array, right++));
        if ((count & 1023) == 0) safepoint();
    }
    array_copy_cooperative(scratch, 0, array, from, count);
}
template<class Compare, class Natural>
void arrays_sort_object(Object* array, std::int32_t from, std::int32_t to, Object* comparator,
        Compare compare, Natural natural) {
    LocalRoot<> input(array), ordering(comparator);
    if (from > to) raise("java/lang/IllegalArgumentException");
    if (from < 0 || to > array_length(array)) raise("java/lang/ArrayIndexOutOfBoundsException");
    if (to - from < 2) return;
    LocalRoot<> scratch(new_array("[Ljava/lang/Object;", to - from)), left, right;
    arrays_merge(input.get(), scratch.get(), from, to, ordering.get(), compare, natural, left, right);
}
template<class Natural> std::int32_t arrays_search_object(Object* array, Object* key, Natural compare) {
    LocalRoot<> input(array), wanted(key), element;
    std::int32_t low = 0, high = array_length(array) - 1;
    while (low <= high) {
        auto middle = low + (high - low) / 2;
        element.set(reference_get(input.get(), middle));
        auto order = compare(check_cast(element.get(), "java/lang/Comparable"), wanted.get());
        if (order < 0) low = middle + 1;
        else if (order > 0) high = middle - 1;
        else return middle;
    }
    return -low - 1;
}
template<class T> Object* arrays_string_primitive(Object* array) {
    if (!array) return literal(u"null");
    LocalRoot<> input(array);
    const auto count = array_length(array);
    std::u16string output(1, u'[');
    for (std::int32_t i = 0; i < count; ++i) {
        if (i) output += u", ";
        const auto value = array_get<T>(input.get(), i);
        if (std::is_same<T, std::uint8_t>::value) output += value ? u"true" : u"false";
        else if (std::is_same<T, std::uint16_t>::value) output += static_cast<char16_t>(value);
        else output += to_text(value);
        if ((i & 1023) == 1023) safepoint();
    }
    output += u']'; return allocate<String>(std::move(output));
}
template<class ToString> Object* arrays_string_object(Object* array, ToString stringify) {
    if (!array) return literal(u"null");
    LocalRoot<> input(array), element, text;
    const auto count = array_length(array);
    std::u16string output(1, u'[');
    for (std::int32_t i = 0; i < count; ++i) {
        if (i) output += u", ";
        element.set(reference_get(input.get(), i));
        text.set(element.get() ? stringify(element.get()) : nullptr);
        if (text.get()) collection_append_text(output, as_string(text.get())->value);
        else output += u"null";
        if ((i & 1023) == 1023) safepoint();
    }
    output += u']'; return allocate<String>(std::move(output));
}
inline Object* arrays_string_z(Object* array) { return arrays_string_primitive<std::uint8_t>(array); }
inline Object* arrays_string_b(Object* array) { return arrays_string_primitive<std::int8_t>(array); }
inline Object* arrays_string_c(Object* array) { return arrays_string_primitive<std::uint16_t>(array); }
inline Object* arrays_string_s(Object* array) { return arrays_string_primitive<std::int16_t>(array); }
inline Object* arrays_string_i(Object* array) { return arrays_string_primitive<std::int32_t>(array); }
inline Object* arrays_string_j(Object* array) { return arrays_string_primitive<std::int64_t>(array); }
inline Object* arrays_string_f(Object* array) { return arrays_string_primitive<float>(array); }
inline Object* arrays_string_d(Object* array) { return arrays_string_primitive<double>(array); }
inline Object* arrays_copy_range(Object* array, std::int32_t from, std::int32_t to) {
    LocalRoot<> input(array);
    if (from > to) raise("java/lang/IllegalArgumentException");
    if (from < 0 || from > array_length(array)) raise("java/lang/ArrayIndexOutOfBoundsException");
    LocalRoot<> result(new_array(array->type_name(), sub(to, from)));
    array_copy_cooperative(input.get(), from, result.get(), 0,
        std::min(array_length(result.get()), array_length(input.get()) - from));
    return result.get();
}
}
