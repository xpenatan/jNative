#include "jn_arrays.hpp"
#include <algorithm>

namespace jnative {
namespace {
constexpr std::int32_t chunk_size = 1024;
constexpr std::int32_t bounded_count = 64;

// After validation, bounded loops only access atomic elements and noncollecting
// value helpers. Register roots only when the long path will introduce polling.
template<class T> void fill_elements(OrdinaryField<T>* elements, std::int32_t from, std::int32_t to, T value) {
    for (auto i = from; i < to; ++i) elements[i].set(value);
}

bool ordinary_array_kind(char kind) {
    switch (kind) {
        case 'Z': case 'B': case 'C': case 'S': case 'I':
        case 'J': case 'F': case 'D': case 'L': return true;
        default: return false;
    }
}

void fill_range(Array* array, std::int32_t from, std::int32_t to) {
    if (from > to) raise("java/lang/IllegalArgumentException");
    if (from < 0 || to > array->length) raise("java/lang/ArrayIndexOutOfBoundsException");
}

template<class T> void fill_primitive(Object* object, std::int32_t from, std::int32_t to, T value) {
    auto array = primitive_array<T>(object);
    fill_range(array, from, to);
    if (to - from <= bounded_count) {
        fill_elements(array->elements.get(), from, to, value);
        return;
    }
    LocalRoot<> root(object);
    LoopSafepoint safepoint;
    for (std::int32_t begin = from; begin < to;) {
        const auto end = begin + std::min(chunk_size, to - begin);
        fill_elements(array->elements.get(), begin, end, value);
        begin = end;
        if (begin < to) safepoint.poll();
    }
}

template<class T> bool same_element(T first, T second) { return first == second; }
bool same_element(float first, float second) { return float_bits(first, true) == float_bits(second, true); }
bool same_element(double first, double second) { return double_bits(first, true) == double_bits(second, true); }

template<class T> std::uint32_t element_hash(T value) { return static_cast<std::uint32_t>(value); }
std::uint32_t element_hash(std::uint8_t value) { return value ? 1231u : 1237u; }
std::uint32_t element_hash(std::int64_t value) {
    const auto bits = static_cast<std::uint64_t>(value);
    return static_cast<std::uint32_t>(bits ^ (bits >> 32));
}
std::uint32_t element_hash(float value) { return static_cast<std::uint32_t>(float_bits(value, true)); }
std::uint32_t element_hash(double value) { return element_hash(double_bits(value, true)); }

template<class T> bool equal_elements(OrdinaryField<T>* first, OrdinaryField<T>* second,
                                     std::int32_t from, std::int32_t to) {
    for (auto i = from; i < to; ++i) {
        if (!same_element(first[i].get(), second[i].get())) return false;
    }
    return true;
}

template<class T> std::uint32_t hash_elements(OrdinaryField<T>* elements, std::int32_t from,
                                            std::int32_t to, std::uint32_t result) {
    for (auto i = from; i < to; ++i) result = 31u * result + element_hash(elements[i].get());
    return result;
}

template<class T> std::uint8_t equals_primitive(Object* first, Object* second) {
    auto input = first ? primitive_array<T>(first) : nullptr;
    auto other = second ? primitive_array<T>(second) : nullptr;
    if (first == second) return 1;
    if (!input || !other || input->length != other->length) return 0;
    if (input->length <= bounded_count)
        return equal_elements(input->elements.get(), other->elements.get(), 0, input->length);
    LocalRoot<> first_root(first);
    LocalRoot<> second_root(second);
    LoopSafepoint safepoint;
    for (std::int32_t begin = 0; begin < input->length;) {
        const auto end = begin + std::min(chunk_size, input->length - begin);
        if (!equal_elements(input->elements.get(), other->elements.get(), begin, end)) return 0;
        begin = end;
        if (begin < input->length) safepoint.poll();
    }
    return 1;
}

template<class T> std::int32_t hash_primitive(Object* object) {
    if (!object) return 0;
    auto array = primitive_array<T>(object);
    std::uint32_t result = 1;
    if (array->length <= bounded_count)
        return signed32(hash_elements(array->elements.get(), 0, array->length, result));
    LocalRoot<> root(object);
    LoopSafepoint safepoint;
    for (std::int32_t begin = 0; begin < array->length;) {
        const auto end = begin + std::min(chunk_size, array->length - begin);
        result = hash_elements(array->elements.get(), begin, end, result);
        begin = end;
        if (begin < array->length) safepoint.poll();
    }
    return signed32(result);
}
}

void array_copy_cooperative(Object* source, std::int32_t from, Object* target, std::int32_t to, std::int32_t count) {
    // Bounded compiler intrinsics rely on this path introducing no safepoint.
    if (count <= 64) {
        array_copy(source, from, target, to, count);
        return;
    }
    LocalRoot<> source_root(source);
    LocalRoot<> target_root(target);
    const auto source_kind = require_non_null(source)->array_kind;
    const auto target_kind = require_non_null(target)->array_kind;
    if (!source_kind || source_kind != target_kind) raise("java/lang/ArrayStoreException");
    if (!ordinary_array_kind(source_kind)) throw std::logic_error("Invalid array descriptor");
    auto input = static_cast<Array*>(source);
    auto output = static_cast<Array*>(target);
    // Check the whole copy before writing, including chunks beyond the first.
    if (from < 0 || to < 0 || from > input->length - count || to > output->length - count)
        raise("java/lang/ArrayIndexOutOfBoundsException");
    LoopSafepoint safepoint;
    const bool backward = source == target && to > from && to - from < count;
    for (std::int32_t remaining = count; remaining > 0;) {
        const auto size = std::min(chunk_size, remaining);
        const auto offset = backward ? remaining - size : count - remaining;
        array_copy(source_root.get(), from + offset, target_root.get(), to + offset, size);
        remaining -= size;
        if (remaining) safepoint.poll();
    }
}

void arrays_fill_z(Object* array, std::int32_t from, std::int32_t to, std::uint8_t value) { fill_primitive(array, from, to, value); }
void arrays_fill_b(Object* array, std::int32_t from, std::int32_t to, std::int8_t value) { fill_primitive(array, from, to, value); }
void arrays_fill_c(Object* array, std::int32_t from, std::int32_t to, std::uint16_t value) { fill_primitive(array, from, to, value); }
void arrays_fill_s(Object* array, std::int32_t from, std::int32_t to, std::int16_t value) { fill_primitive(array, from, to, value); }
void arrays_fill_i(Object* array, std::int32_t from, std::int32_t to, std::int32_t value) { fill_primitive(array, from, to, value); }
void arrays_fill_j(Object* array, std::int32_t from, std::int32_t to, std::int64_t value) { fill_primitive(array, from, to, value); }
void arrays_fill_f(Object* array, std::int32_t from, std::int32_t to, float value) { fill_primitive(array, from, to, value); }
void arrays_fill_d(Object* array, std::int32_t from, std::int32_t to, double value) { fill_primitive(array, from, to, value); }

void arrays_fill_reference(Object* object, std::int32_t from, std::int32_t to, Object* value) {
    auto array = reference_array(object);
    fill_range(array, from, to);
    if (from == to) return;
    // An empty range performs no store/type check. The first actual store checks
    // covariance; the same value remains assignable at all subsequent indexes.
    // A failing check raises and unwinds; a successful check cannot collect.
    reference_set(object, from, value);
    if (to - from <= bounded_count) {
        fill_elements(array->elements.get(), from + 1, to, value);
        return;
    }
    LocalRoot<> array_root(object);
    LocalRoot<> value_root(value);
    LoopSafepoint safepoint;
    for (std::int32_t begin = from + 1; begin < to;) {
        const auto end = begin + std::min(chunk_size, to - begin);
        fill_elements(array->elements.get(), begin, end, value_root.get());
        begin = end;
        if (begin < to) safepoint.poll();
    }
}

std::uint8_t arrays_equals(Object* first, Object* second, char kind) {
    switch (kind) {
        case 'Z': return equals_primitive<std::uint8_t>(first, second);
        case 'B': return equals_primitive<std::int8_t>(first, second);
        case 'C': return equals_primitive<std::uint16_t>(first, second);
        case 'S': return equals_primitive<std::int16_t>(first, second);
        case 'I': return equals_primitive<std::int32_t>(first, second);
        case 'J': return equals_primitive<std::int64_t>(first, second);
        case 'F': return equals_primitive<float>(first, second);
        case 'D': return equals_primitive<double>(first, second);
        default: throw std::logic_error("Invalid primitive array kind");
    }
}

std::int32_t arrays_hash(Object* array, char kind) {
    switch (kind) {
        case 'Z': return hash_primitive<std::uint8_t>(array);
        case 'B': return hash_primitive<std::int8_t>(array);
        case 'C': return hash_primitive<std::uint16_t>(array);
        case 'S': return hash_primitive<std::int16_t>(array);
        case 'I': return hash_primitive<std::int32_t>(array);
        case 'J': return hash_primitive<std::int64_t>(array);
        case 'F': return hash_primitive<float>(array);
        case 'D': return hash_primitive<double>(array);
        default: throw std::logic_error("Invalid primitive array kind");
    }
}
}
