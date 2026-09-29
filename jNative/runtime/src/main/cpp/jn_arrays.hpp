#pragma once
#include "jn_runtime.hpp"

namespace jnative {
// Compiler-selected array operations may poll while processing large arrays.
void array_copy_cooperative(Object* source, std::int32_t from, Object* target, std::int32_t to, std::int32_t count);
void arrays_fill_z(Object* array, std::int32_t from, std::int32_t to, std::uint8_t value);
void arrays_fill_b(Object* array, std::int32_t from, std::int32_t to, std::int8_t value);
void arrays_fill_c(Object* array, std::int32_t from, std::int32_t to, std::uint16_t value);
void arrays_fill_s(Object* array, std::int32_t from, std::int32_t to, std::int16_t value);
void arrays_fill_i(Object* array, std::int32_t from, std::int32_t to, std::int32_t value);
void arrays_fill_j(Object* array, std::int32_t from, std::int32_t to, std::int64_t value);
void arrays_fill_f(Object* array, std::int32_t from, std::int32_t to, float value);
void arrays_fill_d(Object* array, std::int32_t from, std::int32_t to, double value);
void arrays_fill_reference(Object* array, std::int32_t from, std::int32_t to, Object* value);
std::uint8_t arrays_equals(Object* first, Object* second, char kind);
std::int32_t arrays_hash(Object* array, char kind);
inline std::uint8_t arrays_equals_z(Object* a, Object* b) { return arrays_equals(a, b, 'Z'); }
inline std::int32_t arrays_hash_z(Object* a) { return arrays_hash(a, 'Z'); }
inline std::uint8_t arrays_equals_b(Object* a, Object* b) { return arrays_equals(a, b, 'B'); }
inline std::int32_t arrays_hash_b(Object* a) { return arrays_hash(a, 'B'); }
inline std::uint8_t arrays_equals_c(Object* a, Object* b) { return arrays_equals(a, b, 'C'); }
inline std::int32_t arrays_hash_c(Object* a) { return arrays_hash(a, 'C'); }
inline std::uint8_t arrays_equals_s(Object* a, Object* b) { return arrays_equals(a, b, 'S'); }
inline std::int32_t arrays_hash_s(Object* a) { return arrays_hash(a, 'S'); }
inline std::uint8_t arrays_equals_i(Object* a, Object* b) { return arrays_equals(a, b, 'I'); }
inline std::int32_t arrays_hash_i(Object* a) { return arrays_hash(a, 'I'); }
inline std::uint8_t arrays_equals_j(Object* a, Object* b) { return arrays_equals(a, b, 'J'); }
inline std::int32_t arrays_hash_j(Object* a) { return arrays_hash(a, 'J'); }
inline std::uint8_t arrays_equals_f(Object* a, Object* b) { return arrays_equals(a, b, 'F'); }
inline std::int32_t arrays_hash_f(Object* a) { return arrays_hash(a, 'F'); }
inline std::uint8_t arrays_equals_d(Object* a, Object* b) { return arrays_equals(a, b, 'D'); }
inline std::int32_t arrays_hash_d(Object* a) { return arrays_hash(a, 'D'); }
}
