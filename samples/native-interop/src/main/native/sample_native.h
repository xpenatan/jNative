#ifndef SAMPLE_NATIVE_H
#define SAMPLE_NATIVE_H
#include "jn_abi.h"

#ifdef __cplusplus
extern "C" {
#endif
int32_t sample_native_marker(void);
int32_t sample_c_add(int32_t first, int32_t second);
int64_t sample_c_checksum(jn_handle bytes);
double sample_cpp_weighted_average(double first, double second, double weight);
jn_handle sample_cpp_greeting(jn_handle name);
jn_handle sample_cpp_reverse(jn_handle bytes);
#ifdef __cplusplus
}
#endif
#endif
