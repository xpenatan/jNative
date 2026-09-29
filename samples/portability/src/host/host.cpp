#include "jnative_exports.h"
#include <cstdio>

int main() {
    if (jn_app_initialize() != JN_OK) return 1;
    jn_handle retained = 0;
    const uint8_t text[] = {'h', 'o', 's', 't'};
    if (jn_string_from_utf8(text, sizeof(text), &retained) != JN_OK) return 2;
    if (jn_app_shutdown() != JN_BUSY) return 3;
    if (jn_release(retained) != JN_OK) return 4;
    const char* arguments[] = {"from-native-host"};
    if (jn_app_main(1, arguments) != JN_OK) {
        std::fprintf(stderr, "%s\n", jn_last_error());
        return 5;
    }
    int32_t result = 0;
    if (portable_java_twice(30, &result) != JN_OK || result != 60) return 6;
    std::puts("Host continues after Java main: 60");
    if (jn_app_shutdown() != JN_OK) return 7;
    if (jn_app_initialize() != JN_SHUTTING_DOWN) return 8;
    return 0;
}
