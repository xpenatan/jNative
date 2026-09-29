#ifndef JNATIVE_ABI_H
#define JNATIVE_ABI_H
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif

#if defined(_WIN32) && defined(JNATIVE_BUILD_SHARED)
#define JNATIVE_PUBLIC __declspec(dllexport)
#else
#define JNATIVE_PUBLIC
#endif

/* ABI version 1. Every reference, including a string or array, is opaque. */
typedef uint64_t jn_handle;
typedef int32_t jn_status;
enum {
    JN_OK = 0, JN_JAVA_EXCEPTION = 1, JN_INVALID_ARGUMENT = 2,
    JN_SHUTTING_DOWN = 3, JN_NATIVE_FAILURE = 4, JN_BUSY = 5
};
typedef struct jn_owned_buffer {
    uint8_t* data;
    uint64_t size; /* bytes, excluding any terminator; embedded zeroes are preserved */
} jn_owned_buffer;

/* Each successful retain creates a distinct owned token. Zero represents null.
 * An owner must not release its token concurrently with an operation using it.
 * Borrowed import arguments must never be released; retain before storing. */
JNATIVE_PUBLIC jn_status jn_retain(jn_handle borrowed, jn_handle* owned);
JNATIVE_PUBLIC jn_status jn_release(jn_handle owned);

/* Error state belongs to the calling OS thread. Normal operations replace it.
 * Release, buffer_free and error accessors preserve it. No C++ exception crosses
 * an export. take_exception transfers ownership; set_exception retains its input. */
JNATIVE_PUBLIC const char* jn_last_error(void);
JNATIVE_PUBLIC jn_status jn_error_status(void);
JNATIVE_PUBLIC void jn_clear_error(void);
JNATIVE_PUBLIC jn_handle jn_take_exception(void);
JNATIVE_PUBLIC jn_status jn_set_exception(jn_handle exception);

/* Copy APIs: buffers are independent of GC and released using buffer_free.
 * UTF-8 input must be well formed. UTF-16 lengths count 16-bit code units;
 * UTF-16 output is native endian and its buffer size is in bytes.
 * Output parameters must be non-null and initially empty. */
JNATIVE_PUBLIC jn_status jn_string_from_utf8(const uint8_t* data, uint64_t size, jn_handle* owned);
JNATIVE_PUBLIC jn_status jn_string_from_utf16(const uint16_t* data, uint64_t units, jn_handle* owned);
JNATIVE_PUBLIC jn_status jn_string_copy_utf8(jn_handle string, jn_owned_buffer* output);
JNATIVE_PUBLIC jn_status jn_string_copy_utf16(jn_handle string, jn_owned_buffer* output);
JNATIVE_PUBLIC jn_status jn_bytes_from_copy(const uint8_t* data, uint64_t size, jn_handle* owned);
JNATIVE_PUBLIC jn_status jn_bytes_copy(jn_handle array, jn_owned_buffer* output);
JNATIVE_PUBLIC jn_status jn_bytes_write(jn_handle array, uint64_t offset, const uint8_t* data, uint64_t size);
JNATIVE_PUBLIC void jn_buffer_free(jn_owned_buffer* buffer);

/* Optional persistent attachment preserves Java Thread/ThreadLocal identity
 * between callbacks. Exports also support temporary automatic attachment.
 * Foreign threads return to native state between calls and must detach before
 * terminating or unloading the program. Shutdown rejects new work and waits for
 * active calls and other explicitly attached native threads to drain.
 * Producers must stop on JN_SHUTTING_DOWN, detach, and be joined by their owner.
 * Shutdown is irreversible and cannot be called inside an exported callback.
 * Release, buffer_free and detach remain usable afterward. */
JNATIVE_PUBLIC jn_status jn_attach_thread(void);
JNATIVE_PUBLIC jn_status jn_detach_thread(void);
JNATIVE_PUBLIC jn_status jn_shutdown(void);
/* Nonblocking shutdown for an embedded host. JN_BUSY leaves the runtime open;
 * stop/join Java workers (including daemons), release owned tokens, and detach
 * foreign threads before retrying. Success prevents subsequent attachment/calls.
 * Never unload the library after JN_BUSY. This function never exits the process. */
JNATIVE_PUBLIC jn_status jn_try_shutdown(void);
#ifdef __cplusplus
}
#endif
#endif
