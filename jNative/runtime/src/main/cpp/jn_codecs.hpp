#pragma once
#include "jn_runtime.hpp"

namespace jnative {
Object* base64_encode(Object* bytes);
Object* base64_encode_string(Object* bytes);
Object* base64_decode(Object* bytes);
Object* base64_decode_string(Object* text);
std::int32_t crc32_update(std::int32_t state, std::int32_t next);
std::uint8_t crc32_is_concrete(Object* checksum);
std::int32_t crc32_update_bytes(std::int32_t state, Object* bytes, std::int32_t offset, std::int32_t length);
}
