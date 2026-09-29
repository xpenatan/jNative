#include "callbacks.h"
#include "jnative_exports.h"
#include <atomic>
#include <exception>
#include <mutex>
#include <stdexcept>
#include <thread>
#include <vector>

namespace {
void check(jn_status status) {
    if (status != JN_OK) throw std::runtime_error(jn_last_error());
}
struct Handle {
    jn_handle value = 0;
    explicit Handle(jn_handle owned = 0) : value(owned) {}
    Handle(const Handle&) = delete;
    Handle& operator=(const Handle&) = delete;
    ~Handle() { jn_release(value); }
};
struct Buffer {
    jn_owned_buffer value{};
    Buffer() = default;
    Buffer(const Buffer&) = delete;
    Buffer& operator=(const Buffer&) = delete;
    ~Buffer() { jn_buffer_free(&value); }
};
struct Attachment {
    Attachment() { check(jn_attach_thread()); }
    Attachment(const Attachment&) = delete;
    ~Attachment() { jn_detach_thread(); }
};
struct Workers {
    std::vector<std::thread> threads;
    void join() { for (auto& thread : threads) if (thread.joinable()) thread.join(); }
    ~Workers() { join(); } // Also join if native allocation/thread creation fails.
};
}

extern "C" int32_t sample_callbacks_marker(void) { return 1; }

extern "C" int64_t sample_run_workers(jn_handle prefix, int32_t count, int32_t tasks) {
    if (count < 1 || count > 32 || tasks < 1 || tasks > 1000)
        throw std::invalid_argument("workers must be 1..32 and tasks must be 1..1000");
    std::atomic<int64_t> total{0};
    std::mutex failure_mutex;
    std::exception_ptr failure;
    Workers workers; // Destroy before the shared state that its threads use.
    workers.threads.reserve(static_cast<std::size_t>(count));
    for (int32_t worker = 0; worker < count; ++worker) {
        jn_handle retained = 0;
        check(jn_retain(prefix, &retained)); // Each worker gets its own owned token.
        try {
            workers.threads.emplace_back([retained, worker, tasks, &total, &failure, &failure_mutex] {
                Handle input(retained);
                try {
                    // Persistent attachment preserves Java ThreadLocal between callbacks.
                    Attachment attached;
                    for (int32_t task = 0; task < tasks; ++task) {
                        Handle result;
                        check(sample_java_process(input.value, worker, task, &result.value));
                        Buffer text;
                        check(jn_string_copy_utf16(result.value, &text.value));
                        total.fetch_add(static_cast<int64_t>(text.value.size / sizeof(uint16_t)));
                        // Release the copied buffer and owned result on every iteration.
                    }
                } catch (...) {
                    std::lock_guard<std::mutex> lock(failure_mutex);
                    if (!failure) failure = std::current_exception();
                }
                // Attachment detaches; input releases the worker's retained token.
            });
        } catch (...) {
            jn_release(retained); // No worker owns the token if creation failed.
            throw;
        }
    }
    workers.join();
    if (failure) std::rethrow_exception(failure);
    return total.load();
}

extern "C" void sample_roundtrip_exception(void) {
    // The generated C export catches the Java exception and sets a pending error.
    // Returning immediately lets the import wrapper rethrow the original Java object.
    // Do not clear the error or call another normal ABI operation first.
    if (sample_java_reject() != JN_OK) return;
}
