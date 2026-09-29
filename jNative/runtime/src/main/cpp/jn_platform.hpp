#pragma once
#include <chrono>
#include <cstddef>
#include <cstdint>
#include <functional>
#include <memory>
#include <mutex>
#include <ostream>
#include <string>
#include <utility>

// Implemented by the selected target provider. No operating-system types cross
// this boundary; a port supplies these functions and advertises its services.
namespace jnative { namespace platform {
class Mutex {
    struct Impl;
    alignas(std::max_align_t) unsigned char storage_[64];
    Impl* impl() { return reinterpret_cast<Impl*>(storage_); }
    friend class Condition;
public:
    Mutex();
    ~Mutex();
    void lock();
    void unlock();
    // Optional provider service. Existing ports retain the blocking path.
#if defined(JNATIVE_PLATFORM_TRY_LOCK)
    bool try_lock();
#else
    bool try_lock() { return false; }
#endif
    Mutex(const Mutex&) = delete;
    Mutex& operator=(const Mutex&) = delete;
};
std::int64_t monotonic_nanos() noexcept;
std::int64_t wall_millis() noexcept;
struct MonotonicClock {
    typedef std::chrono::nanoseconds duration;
    typedef duration::rep rep;
    typedef duration::period period;
    typedef std::chrono::time_point<MonotonicClock> time_point;
    static const bool is_steady = true;
    static time_point now() noexcept { return time_point(duration(monotonic_nanos())); }
};
class Condition {
    struct Impl;
    alignas(std::max_align_t) unsigned char storage_[64];
    Impl* impl() { return reinterpret_cast<Impl*>(storage_); }
    bool wait_deadline(Mutex&, std::int64_t);
public:
    Condition();
    ~Condition();
    void notify_one() noexcept;
    void notify_all() noexcept;
    void wait(std::unique_lock<Mutex>&);
    template<class Predicate> void wait(std::unique_lock<Mutex>& lock, Predicate ready) {
        while (!ready()) wait(lock);
    }
    template<class Predicate> bool wait_until(std::unique_lock<Mutex>& lock,
            MonotonicClock::time_point end, Predicate ready) {
        while (!ready()) {
            if (!wait_deadline(*lock.mutex(), end.time_since_epoch().count())) return ready();
        }
        return true;
    }
    Condition(const Condition&) = delete;
    Condition& operator=(const Condition&) = delete;
};
void start_thread(std::function<void()> body);
void yield_thread() noexcept;
void*& managed_context() noexcept;
class LocalKey {
    std::uintptr_t key_;
    void (*destroy_)(void*);
public:
    explicit LocalKey(void (*destroy)(void*));
    ~LocalKey();
    void* get() const noexcept;
    void set(void*);
    LocalKey(const LocalKey&) = delete;
    LocalKey& operator=(const LocalKey&) = delete;
};
template<class T> class ThreadLocal {
    static void destroy(void* value) { delete static_cast<T*>(value); }
    LocalKey key_;
public:
    ThreadLocal() : key_(destroy) {}
    T& get() {
        T* value = static_cast<T*>(key_.get());
        if (!value) {
            std::unique_ptr<T> created(new T());
            key_.set(created.get());
            value = created.release();
        }
        return *value;
    }
};
struct FloatingState { alignas(std::max_align_t) unsigned char bytes[256]; };
void save_floating(FloatingState&) noexcept;
void restore_floating(const FloatingState&) noexcept;
void java_floating() noexcept;
void* allocate(std::size_t bytes);
void deallocate(void*) noexcept;
bool interactive_console() noexcept;
void pause_console();
void write_output(bool error, const char* bytes, std::size_t size);
std::ostream& output();
std::ostream& error_output();
void exit_process(int status);
} }
