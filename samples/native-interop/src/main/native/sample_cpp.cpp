#include "sample_native.h"
#include <algorithm>
#include <cmath>
#include <stdexcept>
#include <string>

namespace {
void check(jn_status status) {
    if (status != JN_OK) throw std::runtime_error(jn_last_error());
}

/* Owns copied native bytes; it never exposes a pointer into the managed heap. */
struct Buffer {
    jn_owned_buffer value{};
    Buffer() = default;
    Buffer(const Buffer&) = delete;
    Buffer& operator=(const Buffer&) = delete;
    ~Buffer() { jn_buffer_free(&value); }
};
}

extern "C" double sample_cpp_weighted_average(double first, double second, double weight) {
    if (std::isnan(weight) || weight < 0 || weight > 1)
        throw std::invalid_argument("weight must be between 0 and 1");
    return first * (1 - weight) + second * weight;
}

extern "C" jn_handle sample_cpp_greeting(jn_handle name) {
    Buffer input;
    check(jn_string_copy_utf8(name, &input.value));
    /* Explicit lengths preserve UTF-8 and embedded zero bytes. */
    std::string text = "Hello, ";
    text.append(reinterpret_cast<const char*>(input.value.data), static_cast<std::size_t>(input.value.size));
    text += u8" — from C++";
    jn_handle result = 0;
    check(jn_string_from_utf8(reinterpret_cast<const uint8_t*>(text.data()), text.size(), &result));
    return result; // Transfer the newly owned handle to generated Java.
}

extern "C" jn_handle sample_cpp_reverse(jn_handle bytes) {
    Buffer copy;
    check(jn_bytes_copy(bytes, &copy.value));
    std::reverse(copy.value.data, copy.value.data + static_cast<std::size_t>(copy.value.size));
    jn_handle result = 0;
    check(jn_bytes_from_copy(copy.value.data, copy.value.size, &result));
    return result; // A new managed byte[]; the borrowed input is unchanged.
}
