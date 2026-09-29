#include "sample_native.h"

int32_t sample_native_marker(void) { return 1; }

int32_t sample_c_add(int32_t first, int32_t second) {
    /* Java addition wraps. Avoid signed overflow in C. */
    uint32_t bits = (uint32_t)first + (uint32_t)second;
    return bits <= INT32_MAX ? (int32_t)bits : -1 - (int32_t)~bits;
}

int64_t sample_c_checksum(jn_handle bytes) {
    jn_owned_buffer copy = {0};
    /* bytes is borrowed: never release it. Copy the array before inspecting data. */
    if (jn_bytes_copy(bytes, &copy) != JN_OK)
        return 0; /* Leave the pending ABI error for the Java import wrapper. */

    int64_t sum = 0;
    for (uint64_t index = 0; index < copy.size; ++index)
        sum += copy.data[index];
    jn_buffer_free(&copy);
    return sum;
}
