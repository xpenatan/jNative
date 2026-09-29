#ifndef SAMPLE_CALLBACKS_H
#define SAMPLE_CALLBACKS_H
#include "jn_abi.h"

#ifdef __cplusplus
extern "C" {
#endif
int32_t sample_callbacks_marker(void);
int64_t sample_run_workers(jn_handle prefix, int32_t workers, int32_t tasks);
void sample_roundtrip_exception(void);
#ifdef __cplusplus
}
#endif
#endif
