#pragma once

#include <chrono>
#include <deque>
#include <functional>

namespace jnative {
struct Waiter {
    ::jnative::platform::Condition changed;
    bool notified = false;
};
struct Monitor {
    ::jnative::platform::Mutex mutex;
    ::jnative::platform::Condition available;
    std::uint64_t owner = 0;
    std::size_t depth = 0;
    std::deque<std::shared_ptr<Waiter>> waiters;
};
struct ThreadControl {
    ::jnative::platform::Mutex mutex;
    ::jnative::platform::Condition changed;
    std::function<void()> interrupt_wake;
    std::atomic<bool> interrupted{false};
    bool started = false;
    bool alive = false;
    bool daemon = false;
    std::int64_t id;
    explicit ThreadControl(std::int64_t identity) : id(identity) {}
};
struct Thread : Object {
    const std::shared_ptr<ThreadControl> control;
    ManagedField<Object*> target;
    ManagedField<Object*> name;
    Thread();
    const char* type_name() const override { return "java/lang/Thread"; }
    void trace(Tracer& tracer) override { tracer.visit(target.get()); tracer.visit(name.get()); }
};
struct ThreadLocal : Object {
    const std::uint64_t key;
    ThreadLocal();
    const char* type_name() const override { return "java/lang/ThreadLocal"; }
};
struct AtomicInteger : Object {
    ManagedField<std::int32_t> value;
    const char* type_name() const override { return "java/util/concurrent/atomic/AtomicInteger"; }
};
struct AtomicLong : Object {
    ManagedField<std::int64_t> value;
    const char* type_name() const override { return "java/util/concurrent/atomic/AtomicLong"; }
};
template<class T> T atomic_add(ManagedField<T>& field, T amount, bool return_new) {
    T before = field.get();
    while (!field.compare_exchange(before, add(before, amount))) {}
    return return_new ? add(before, amount) : before;
}
struct ClassObject final : Object {
    const std::string name;
    const std::uint32_t represented_type_id;
    explicit ClassObject(std::string class_name)
        : Object(0, RuntimeKind::class_type), name(std::move(class_name)),
          represented_type_id(generated_type_id(name)) {}
    const char* type_name() const override { return "java/lang/Class"; }
};

Thread* as_thread(Object* thread);
Thread* current_java_thread();
void initialize_thread(Object* thread, Object* target, Object* name);
void thread_start(Object* thread, std::function<void(Object*)> body);
void thread_join(Object* thread, std::int64_t millis, std::int32_t nanos);
void thread_sleep(std::int64_t millis, std::int32_t nanos);
void thread_interrupt(Object* thread);
bool thread_interrupted(Object* thread, bool clear);
bool thread_alive(Object* thread);
bool thread_daemon(Object* thread);
void thread_set_daemon(Object* thread, bool daemon);
void thread_yield();
bool finish_java_main();
bool java_threads_active();
void monitor_enter(Object* object);
void monitor_exit(Object* object);
void monitor_wait(Object* object, std::int64_t millis, std::int32_t nanos);
void monitor_notify(Object* object, bool all);
bool holds_monitor(Object* object);
Object* class_object(const std::string& name);
Object* thread_local_get(Object* local, const std::function<Object*(Object*)>& initializer);
void thread_local_set(Object* local, Object* value);
void thread_local_remove(Object* local);

class MonitorGuard {
    Object* object_ = nullptr;
public:
    MonitorGuard() = default;
    explicit MonitorGuard(Object* object) { lock(object); }
    ~MonitorGuard() { try { unlock(); } catch (...) { std::terminate(); } }
    void lock(Object* object) {
        if (object_) raise("java/lang/IllegalMonitorStateException");
        monitor_enter(object);
        object_ = object;
    }
    void unlock() {
        if (!object_) return;
        monitor_exit(object_);
        object_ = nullptr;
    }
    MonitorGuard(const MonitorGuard&) = delete;
    MonitorGuard& operator=(const MonitorGuard&) = delete;
};
}
