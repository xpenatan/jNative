#include "portable.h"
#include "jnative_exports.h"

extern "C" int32_t portable_native_callback(int32_t value) {
    int32_t result = 0;
    if (portable_java_twice(value, &result) != JN_OK) return 0;
    return result;
}
