#include "jn_runtime.hpp"
#include <algorithm>

namespace jnative {
namespace {
std::atomic<std::int64_t> next_thread_id{1};
std::atomic<std::uint64_t> next_local_key{1};
std::atomic<std::int64_t> next_thread_name{0};
struct ActiveThreads {
    ::jnative::platform::Mutex mutex;
    ::jnative::platform::Condition changed;
    std::size_t non_daemon = 0;
    std::size_t daemon = 0;
};
ActiveThreads& active_threads() { static ActiveThreads active; return active; }

void validate_timeout(std::int64_t millis, std::int32_t nanos) {
    if (millis < 0 || nanos < 0 || nanos > 999999) raise("java/lang/IllegalArgumentException", "Invalid timeout");
}
platform::MonotonicClock::time_point deadline(std::int64_t millis, std::int32_t nanos) {
    auto now = platform::MonotonicClock::now();
    auto remaining = platform::MonotonicClock::time_point::max() - now;
    auto limit = std::chrono::duration_cast<std::chrono::milliseconds>(remaining).count();
    if (millis >= limit) return platform::MonotonicClock::time_point::max();
    return now + std::chrono::milliseconds(millis) + std::chrono::nanoseconds(nanos);
}
class WaitingOn {
    std::shared_ptr<ThreadControl> control_;
public:
    WaitingOn(std::shared_ptr<ThreadControl> control, std::function<void()> wake) : control_(std::move(control)) {
        std::lock_guard<::jnative::platform::Mutex> lock(control_->mutex);
        control_->interrupt_wake = std::move(wake);
    }
    ~WaitingOn() {
        std::lock_guard<::jnative::platform::Mutex> lock(control_->mutex);
        control_->interrupt_wake = {};
    }
};
}

Object::Object(char array_kind, RuntimeKind runtime_kind)
    : array_kind(array_kind), runtime_kind(runtime_kind) {}
struct MonitorOwner {
    const std::shared_ptr<Monitor> peer;
    MonitorOwner() : peer(std::make_shared<Monitor>()) {}
};
Object::~Object() { delete monitor_.load(std::memory_order_relaxed); }
const std::shared_ptr<Monitor>& Object::monitor() {
    // Allocate the shared owner only for objects that actually use a monitor.
    // Once published it remains immutable until object destruction. Readers
    // need neither the library's shared_ptr lock nor a refcount copy.
    auto owner = monitor_.load(std::memory_order_acquire);
    if (!owner) {
        auto created = ::jnative::make_owned<MonitorOwner>();
        if (monitor_.compare_exchange_strong(owner, created.get(),
                std::memory_order_acq_rel, std::memory_order_acquire)) owner = created.release();
    }
    return owner->peer;
}
Thread::Thread() : control(std::make_shared<ThreadControl>(next_thread_id.fetch_add(1))) {}
ThreadLocal::ThreadLocal() : key(next_local_key.fetch_add(1)) {}
Thread* as_thread(Object* object) {
    auto thread = dynamic_cast<Thread*>(require_non_null(object));
    if (!thread) raise("java/lang/ClassCastException");
    return thread;
}
Thread* current_java_thread() {
    if (!current_thread()) throw std::logic_error("Java thread requested without attachment");
    if (!current_thread()->java_thread) {
        auto thread = allocate<Thread>();
        current_thread()->java_thread = thread;
        thread->control->started = true;
        thread->control->alive = true;
        thread->name.set(literal(thread->control->id == 1 ? u"main" : u"attached-native"));
    }
    return as_thread(current_thread()->java_thread);
}
void initialize_thread(Object* object, Object* target, Object* name) {
    auto thread = as_thread(object);
    thread->target.set(target);
    if (name) thread->name.set(as_string(name));
    else thread->name.set(allocate<String>(u"Thread-" + to_text(next_thread_name.fetch_add(1))));
    thread->control->daemon = current_java_thread()->control->daemon;
}
void thread_start(Object* object, std::function<void(Object*)> body) {
    auto peer = as_thread(object)->control;
    GlobalHandle<> retained(object);
    bool duplicate = false;
    std::exception_ptr creation_failure;
    {
        NativeRegion blocked(ThreadState::blocked_managed);
        std::lock_guard<::jnative::platform::Mutex> lock(peer->mutex);
        if (peer->started) duplicate = true;
        else {
            peer->started = true;
            peer->alive = true;
            auto& active = active_threads();
            const bool daemon = peer->daemon;
            {
                std::lock_guard<::jnative::platform::Mutex> registry(active.mutex);
                if (peer->daemon) ++active.daemon; else ++active.non_daemon;
            }
            try {
                platform::start_thread([retained, peer, daemon, body]() mutable {
                    {
                        ThreadAttachment attached;
                        current_thread()->java_thread = retained.get();
                        if (auto name = dynamic_cast<String*>(as_thread(retained.get())->name.get())) diagnostics_thread_name(utf8(name->value));
                        try {
                            body(retained.get());
                        } catch (const Thrown& error) {
                            print_stack_trace(error.object());
                        } catch (const std::exception& error) {
                            // The default uncaught handler reports failure on this worker.
                            NativeRegion output;
                            platform::error_output() << "Exception in thread: " << error.what() << '\n';
                        }
                        {
                            NativeRegion ending(ThreadState::blocked_managed);
                            std::lock_guard<::jnative::platform::Mutex> lock(peer->mutex);
                            peer->alive = false;
                            peer->changed.notify_all();
                        }
                    }
                    // Release captured managed roots before the registry permits runtime teardown.
                    retained = GlobalHandle<>();
                    auto& active = active_threads();
                    std::lock_guard<::jnative::platform::Mutex> registry(active.mutex);
                    if (daemon) --active.daemon; else --active.non_daemon;
                    active.changed.notify_all();
                });
            } catch (...) {
                creation_failure = std::current_exception();
                peer->started = false;
                peer->alive = false;
                std::lock_guard<::jnative::platform::Mutex> registry(active.mutex);
                if (peer->daemon) --active.daemon; else --active.non_daemon;
                active.changed.notify_all();
            }
        }
    }
    if (duplicate) raise("java/lang/IllegalThreadStateException");
    if (creation_failure) raise("java/lang/OutOfMemoryError", "Cannot create native thread");
}
void thread_join(Object* object, std::int64_t millis, std::int32_t nanos) {
    validate_timeout(millis, nanos);
    auto target = as_thread(object)->control;
    auto caller = current_java_thread()->control;
    bool interrupted = false;
    {
        NativeRegion blocked(ThreadState::blocked_managed);
        WaitingOn waiting(caller, [target] {
            std::lock_guard<::jnative::platform::Mutex> lock(target->mutex);
            target->changed.notify_all();
        });
        std::unique_lock<::jnative::platform::Mutex> lock(target->mutex);
        auto finished = [&] { return !target->alive || caller->interrupted.load(); };
        if (target->alive) {
            if (millis == 0 && nanos == 0) target->changed.wait(lock, finished);
            else target->changed.wait_until(lock, deadline(millis, nanos), finished);
            interrupted = caller->interrupted.exchange(false);
        }
    }
    if (interrupted) raise("java/lang/InterruptedException");
}
void thread_sleep(std::int64_t millis, std::int32_t nanos) {
    validate_timeout(millis, nanos);
    auto caller = current_java_thread()->control;
    bool interrupted;
    {
        NativeRegion blocked(ThreadState::blocked_managed);
        std::unique_lock<::jnative::platform::Mutex> lock(caller->mutex);
        caller->changed.wait_until(lock, deadline(millis, nanos), [&] { return caller->interrupted.load(); });
        interrupted = caller->interrupted.exchange(false);
    }
    if (interrupted) raise("java/lang/InterruptedException");
}
void thread_interrupt(Object* object) {
    auto target = as_thread(object)->control;
    NativeRegion blocked;
    std::function<void()> wake;
    {
        std::lock_guard<::jnative::platform::Mutex> lock(target->mutex);
        target->interrupted.store(true);
        target->changed.notify_all();
        wake = target->interrupt_wake;
    }
    // Taking the wait's mutex prevents a notification from falling between its
    // predicate check and wait. Captured peers keep that mutex and condition alive.
    if (wake) wake();
}
bool thread_interrupted(Object* object, bool clear) {
    auto target = as_thread(object)->control;
    return clear ? target->interrupted.exchange(false) : target->interrupted.load();
}
bool thread_alive(Object* object) {
    auto target = as_thread(object)->control;
    NativeRegion blocked;
    std::lock_guard<::jnative::platform::Mutex> lock(target->mutex);
    return target->alive;
}
bool thread_daemon(Object* object) {
    auto target = as_thread(object)->control;
    NativeRegion blocked;
    std::lock_guard<::jnative::platform::Mutex> lock(target->mutex);
    return target->daemon;
}
void thread_set_daemon(Object* object, bool daemon) {
    auto target = as_thread(object)->control;
    bool already_started;
    {
        NativeRegion blocked;
        std::lock_guard<::jnative::platform::Mutex> lock(target->mutex);
        already_started = target->alive;
        if (!already_started) target->daemon = daemon;
    }
    if (already_started) raise("java/lang/IllegalThreadStateException");
}
void thread_yield() { NativeRegion blocked; platform::yield_thread(); }
bool finish_java_main() {
    auto main = current_thread() && current_thread()->java_thread ? as_thread(current_thread()->java_thread)->control : nullptr;
    NativeRegion blocked(ThreadState::blocked_managed);
    if (main) {
        std::lock_guard<::jnative::platform::Mutex> lock(main->mutex);
        main->alive = false;
        main->changed.notify_all();
    }
    auto& active = active_threads();
    std::unique_lock<::jnative::platform::Mutex> lock(active.mutex);
    active.changed.wait(lock, [&] { return active.non_daemon == 0; });
    return active.daemon != 0;
}
bool java_threads_active() {
    auto& active = active_threads();
    std::lock_guard<platform::Mutex> lock(active.mutex);
    return active.non_daemon != 0 || active.daemon != 0;
}
void monitor_enter(Object* object) {
    const auto& peer = require_non_null(object)->monitor();
    auto thread = current_thread();
    thread->held_monitors.push_back(object);
    // Publish the object before cooperating with collection. A successful
    // nonblocking acquisition stays managed; no foreign floating state exists.
    thread->heap->poll_if_requested();
    {
        std::unique_lock<platform::Mutex> lock(peer->mutex, std::try_to_lock);
        if (lock.owns_lock() && (!peer->depth || peer->owner == thread->identity)) {
            peer->owner = thread->identity;
            ++peer->depth;
            return;
        }
    }
    NativeRegion blocked(ThreadState::blocked_managed);
    std::unique_lock<::jnative::platform::Mutex> lock(peer->mutex);
    peer->available.wait(lock, [&] { return peer->depth == 0 || peer->owner == thread->identity; });
    peer->owner = thread->identity;
    ++peer->depth;
}
void monitor_exit(Object* object) {
    // Retain a copy for invalid-owner calls, whose object need not occur among
    // this thread's held roots when the entry poll cooperates with collection.
    auto peer = require_non_null(object)->monitor();
    auto thread = current_thread();
    // A valid owner retains the object in held_monitors until after this poll.
    thread->heap->poll_if_requested();
    bool owner;
    auto release = [&] {
        owner = peer->depth && peer->owner == thread->identity;
        if (owner && --peer->depth == 0) { peer->owner = {}; peer->available.notify_all(); }
    };
    {
        std::unique_lock<platform::Mutex> lock(peer->mutex, std::try_to_lock);
        if (lock.owns_lock()) release();
        else {
            NativeRegion blocked;
            std::lock_guard<platform::Mutex> waiting(peer->mutex);
            release();
        }
    }
    if (!owner) raise("java/lang/IllegalMonitorStateException");
    auto& roots = thread->held_monitors;
    auto found = std::find(roots.rbegin(), roots.rend(), object);
    if (found == roots.rend()) throw std::logic_error("Monitor root missing");
    roots.erase(std::next(found).base());
}
void monitor_wait(Object* object, std::int64_t millis, std::int32_t nanos) {
    validate_timeout(millis, nanos);
    auto peer = require_non_null(object)->monitor();
    auto caller = current_java_thread()->control;
    bool owner, interrupted = false;
    {
        NativeRegion blocked(ThreadState::blocked_managed);
        auto waiter = std::make_shared<Waiter>();
        WaitingOn waiting(caller, [peer, waiter] {
            std::lock_guard<::jnative::platform::Mutex> lock(peer->mutex);
            waiter->changed.notify_all();
        });
        std::unique_lock<::jnative::platform::Mutex> lock(peer->mutex);
        owner = peer->depth && peer->owner == current_thread()->identity;
        if (owner) {
            interrupted = caller->interrupted.exchange(false);
            if (!interrupted) {
                auto depth = peer->depth;
                peer->waiters.push_back(waiter);
                peer->depth = 0;
                peer->owner = {};
                peer->available.notify_all();
                auto ready = [&] { return waiter->notified || caller->interrupted.load(); };
                if (millis == 0 && nanos == 0) waiter->changed.wait(lock, ready);
                else waiter->changed.wait_until(lock, deadline(millis, nanos), ready);
                peer->waiters.erase(std::find(peer->waiters.begin(), peer->waiters.end(), waiter));
                peer->available.wait(lock, [&] { return peer->depth == 0; });
                peer->owner = current_thread()->identity;
                peer->depth = depth;
                interrupted = caller->interrupted.exchange(false);
            }
        }
    }
    if (!owner) raise("java/lang/IllegalMonitorStateException");
    if (interrupted) raise("java/lang/InterruptedException");
}
void monitor_notify(Object* object, bool all) {
    auto peer = require_non_null(object)->monitor();
    bool owner;
    {
        NativeRegion blocked;
        std::lock_guard<::jnative::platform::Mutex> lock(peer->mutex);
        owner = peer->depth && peer->owner == current_thread()->identity;
        if (owner) for (const auto& waiter : peer->waiters) {
            if (waiter->notified) continue;
            waiter->notified = true;
            waiter->changed.notify_one();
            if (!all) break;
        }
    }
    if (!owner) raise("java/lang/IllegalMonitorStateException");
}
bool holds_monitor(Object* object) {
    auto peer = require_non_null(object)->monitor();
    NativeRegion blocked;
    std::lock_guard<::jnative::platform::Mutex> lock(peer->mutex);
    return peer->depth && peer->owner == current_thread()->identity;
}
Object* class_object(const std::string& name) {
    static ::jnative::platform::Mutex mutex;
    static std::unordered_map<std::string, GlobalHandle<ClassObject>> classes;
    {
        std::lock_guard<::jnative::platform::Mutex> lock(mutex);
        auto found = classes.find(name);
        if (found != classes.end()) return found->second.get();
    }
    LocalRoot<ClassObject> created(allocate<ClassObject>(name));
    std::lock_guard<::jnative::platform::Mutex> lock(mutex);
    return classes.emplace(name, GlobalHandle<ClassObject>(created.get())).first->second.get();
}
Object* ClassLiteral::resolve(const char* name) {
    Object* value = class_object(name);
    cached.store(value, std::memory_order_release);
    return value;
}
Object* thread_local_get(Object* object, const std::function<Object*(Object*)>& initializer) {
    auto local = dynamic_cast<ThreadLocal*>(require_non_null(object));
    if (!local) raise("java/lang/ClassCastException");
    auto found = current_thread()->thread_locals.find(local->key);
    if (found != current_thread()->thread_locals.end()) return found->second;
    Object* value = initializer(object);
    current_thread()->thread_locals[local->key] = value;
    return value;
}
void thread_local_set(Object* object, Object* value) {
    auto local = dynamic_cast<ThreadLocal*>(require_non_null(object));
    if (!local) raise("java/lang/ClassCastException");
    current_thread()->thread_locals[local->key] = value;
}
void thread_local_remove(Object* object) {
    auto local = dynamic_cast<ThreadLocal*>(require_non_null(object));
    if (!local) raise("java/lang/ClassCastException");
    current_thread()->thread_locals.erase(local->key);
}
}
