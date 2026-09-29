#include "jn_runtime.hpp"
#include "jn_arrays.hpp"
#include "jn_abi.h"
#include "jn_abi.hpp"
#include "jn_native_storage.hpp"
#include <thread>
#include <iostream>
#include <string>
#include <cfenv>
#if defined(__SSE2__) || defined(_M_X64)
#include <xmmintrin.h>
#endif

namespace {
void require(bool ok, const char* message) { if (!ok) throw std::runtime_error(message); }
struct Node final : jnative::Object {
    jnative::ManagedField<Node*> next;
    jnative::ManagedField<std::int32_t> value;
    void trace(jnative::Tracer& tracer) override { tracer.visit(next.get()); }
};
struct InitializationProbe final : jnative::Object {
    std::atomic<bool>& destroyed;
    explicit InitializationProbe(std::atomic<bool>& destroyed) : destroyed(destroyed) {}
    ~InitializationProbe() { destroyed.store(true, std::memory_order_release); }
};
void native_import_errors() {
    jnative::LocalRoot<jnative::Throwable> failure(
        jnative::allocate<jnative::Throwable>("java/lang/IllegalStateException"));
    failure->message.set(jnative::allocate<jnative::String>(u"native callback"));
    jnative::BorrowedHandle borrowed(failure.get());
    jnative::NativeCallError outer;
    {
        jnative::NativeCallError inner;
        require(jn_set_exception(borrowed.id()) == JN_OK, "Nested callback exception was rejected");
        bool caught = false;
        try { inner.check(); }
        catch (const jnative::Thrown& error) { caught = error.object() == failure.get(); }
        require(caught && jn_error_status() == JN_OK, "Nested callback lost exception identity or error cleanup");
    }
    outer.check();
    require(jn_set_exception(borrowed.id()) == JN_OK, "Outer callback exception was rejected");
    {
        jnative::NativeRegion native;
        std::thread foreign([] { jn_clear_error(); });
        foreign.join();
    }
    bool caught = false;
    try { outer.check(); }
    catch (const jnative::Thrown& error) { caught = error.object() == failure.get(); }
    require(caught, "Another thread changed a captured native error slot");
    jnative::NativeCallError reference;
    jn_handle result = 0;
    const uint8_t bytes[] = {'r', 'e', 's', 'u', 'l', 't'};
    require(jn_string_from_utf8(bytes, sizeof(bytes), &result) == JN_OK, "Native result creation failed");
    require(jn_set_exception(borrowed.id()) == JN_OK, "Reference callback exception was rejected");
    caught = false;
    try { reference.import_reference(result, "java/lang/String", {}); }
    catch (const jnative::Thrown& error) { caught = error.object() == failure.get(); }
    require(caught && jn_release(result) == JN_INVALID_ARGUMENT, "Failed reference import leaked its owned result");
    require(jn_set_exception(borrowed.id()) == JN_OK, "Stale error setup failed");
    jnative::NativeCallError next;
    next.check();
    require(jn_error_status() == JN_OK && jn_take_exception() == 0, "Next import retained a stale exception");
}
void native_storage() {
    auto& heap = jnative::Heap::instance();
    heap.collection_threshold(1);
    jn_handle expired = 0;
    {
        jnative::BorrowedHandle integers(jnative::allocate<jnative::PrimitiveArray<std::int32_t>>(16, "[I"));
        jnative::BorrowedHandle floats(jnative::allocate<jnative::PrimitiveArray<float>>(4, "[F"));
        jnative::BorrowedHandle text(jnative::allocate<jnative::String>(u"native \u03a9 \U0001f680"));
        jnative::BorrowedHandle buffer(jnative::buffer_allocate(32));
        jnative::buffer_position(heap.resolve(buffer.id()), 3);
        jnative::BorrowedHandle view(jnative::buffer_view(heap.resolve(buffer.id()), true, false));
        jnative::BorrowedHandle read_only(jnative::buffer_view(heap.resolve(view.id()), false, true));
        jnative::BorrowedHandle heap_buffer(jnative::buffer_allocate_heap(8));
        jnative::BorrowedHandle empty(jnative::buffer_allocate(0));
        expired = integers.id();
        bool correct = true;
        std::atomic<int> collections{0};
        {
            jnative::NativeRegion native;
            std::thread collector([&] {
                jnative::ThreadAttachment attached;
                for (int i = 0; i < 200; ++i) {
                    heap.collect();
                    ++collections;
                }
            });
            jnative::NativeArrayStorage<std::int32_t> values(integers.id());
            jnative::NativeArrayStorage<float> floating(floats.id());
            std::int32_t source[16], destination[16];
            float float_source[] = {-0.0f, std::numeric_limits<float>::denorm_min(),
                std::numeric_limits<float>::infinity(), std::numeric_limits<float>::quiet_NaN()};
            float float_destination[4];
            for (int pass = 0; pass < 4000; ++pass) {
                for (int i = 0; i < 16; ++i) source[i] = pass + i;
                values.copy_from(source, 0, 16);
                values.copy_to(destination, 0, 16);
                correct &= std::memcmp(source, destination, sizeof(source)) == 0;
                floating.copy_from(float_source, 0, 4);
                floating.copy_to(float_destination, 0, 4);
                correct &= std::memcmp(float_source, float_destination, sizeof(float_source)) == 0;
                correct &= jnative::native_string_storage(text.id()) == u"native \u03a9 \U0001f680";
                auto data = jnative::native_buffer_storage(view.id(), 8, true);
                data[0] = static_cast<std::uint8_t>(pass);
                correct &= jnative::native_buffer_storage(read_only.id(), 8, false)[0] == data[0];
                correct &= jnative::current_thread()->state == jnative::ThreadState::in_native;
            }
            collector.join();
            require(jnative::native_buffer_storage(empty.id(), 0, false) == nullptr,
                    "Empty native buffer pointer changed");
            values.copy_to(nullptr, 16, 0);
            values.copy_from(nullptr, 16, 0);
            try { values.copy_to(destination, 0, INT32_MAX); require(false, "Oversized native array read"); }
            catch (const std::invalid_argument&) {}
            try { values.copy_from(source, -1, 1); require(false, "Negative native array write"); }
            catch (const std::invalid_argument&) {}
            try { values.copy_from(nullptr, 0, 1); require(false, "Null native array source"); }
            catch (const std::invalid_argument&) {}
            try { jnative::NativeArrayStorage<float> wrong(integers.id()); require(false, "Incorrect native array type"); }
            catch (const std::invalid_argument&) {}
            try { jnative::native_string_storage(integers.id()); require(false, "Incorrect native string type"); }
            catch (const std::invalid_argument&) {}
            try { jnative::native_buffer_storage(read_only.id(), 1, true); require(false, "Native read-only buffer write"); }
            catch (const std::invalid_argument&) {}
            try { jnative::native_buffer_storage(view.id(), 30, false); require(false, "Native buffer overread"); }
            catch (const std::invalid_argument&) {}
            try { jnative::native_buffer_storage(heap_buffer.id(), 1, false); require(false, "Native heap buffer accepted"); }
            catch (const std::invalid_argument&) {}
        }
        require(correct && collections == 200, "Native storage did not survive concurrent collection");
    }
    try { jnative::NativeArrayStorage<std::int32_t> stale(expired); require(false, "Expired native storage token"); }
    catch (const std::logic_error&) {}
    heap.collect();
    require(heap.statistics().first == 0, "Native storage retained expired objects");
}
void recursive_initialization() {
    auto& heap = jnative::Heap::instance();
    std::atomic<bool> destroyed{false}, collecting{false};
    jnative::ClassInitialization initialization;
    initialization.run([&] {
        // A bounded same-class helper may hold a reference in an ordinary C++
        // local. Recursive initialization must not introduce a hidden safepoint.
        auto* value = jnative::allocate<InitializationProbe>(destroyed);
        std::thread worker([&] {
            jnative::ThreadAttachment attached;
            collecting.store(true, std::memory_order_release);
            heap.collect();
        });
        while (!collecting.load(std::memory_order_acquire)) std::this_thread::yield();
        for (int i = 0; i < 10000; ++i)
            initialization.run([] { throw std::logic_error("Recursive initializer executed twice"); });
        const bool survived = !destroyed.load(std::memory_order_acquire);
        jnative::LocalRoot<> retained(survived ? value : nullptr);
        { jnative::NativeRegion waiting; worker.join(); }
        require(survived, "Recursive initialization collected a bounded helper's reference");
        require(!initialization.completed(), "Recursive initialization published completion early");
    });
    require(initialization.completed(), "Initialization did not publish completion");
    heap.collect();
    require(destroyed.load(std::memory_order_acquire), "Initialization probe remained rooted");
}
void floating_environments() {
    jnative::platform::FloatingState original{};
    jnative::platform::save_floating(original);
    const int modes[] = { FE_DOWNWARD, FE_UPWARD, FE_TOWARDZERO };
    for (int mode : modes) {
        std::fesetenv(FE_DFL_ENV);
        std::fesetround(mode);
        std::feraiseexcept(FE_INVALID | FE_INEXACT);
#if defined(__SSE2__) || defined(_M_X64)
        const unsigned native_control = _mm_getcsr() | (1u << 15) | (1u << 6);
        _mm_setcsr(native_control);
#endif
        {
            jnative::FloatingEnvironment java;
            require(std::fegetround() == FE_TONEAREST, "Java entry inherited native rounding");
            require(std::fetestexcept(FE_ALL_EXCEPT) == 0, "Java entry inherited native exceptions");
#if defined(__SSE2__) || defined(_M_X64)
            require((_mm_getcsr() & ((1u << 15) | (1u << 6) | (3u << 13))) == 0,
                    "Java entry inherited flush-to-zero or SIMD rounding");
#endif
            std::fesetround(FE_DOWNWARD);
            {
                jnative::FloatingEnvironment nested;
                require(std::fegetround() == FE_TONEAREST, "Nested entry inherited rounding");
                std::feraiseexcept(FE_OVERFLOW);
            }
            require(std::fegetround() == FE_DOWNWARD, "Nested exit changed native rounding");
            require(std::fetestexcept(FE_ALL_EXCEPT) == 0, "Nested exit leaked floating exceptions");
        }
        require(std::fegetround() == mode, "Java exit changed native rounding");
        require((std::fetestexcept(FE_ALL_EXCEPT) & (FE_INVALID | FE_INEXACT))
                        == (FE_INVALID | FE_INEXACT), "Java exit lost native exceptions");
#if defined(__SSE2__) || defined(_M_X64)
        require(_mm_getcsr() == native_control, "Java exit changed native SIMD environment");
#endif
    }
    jnative::platform::restore_floating(original);
}
void managed_storage_access() {
    auto& heap = jnative::Heap::instance();
    jnative::LocalRoot<Node> root(jnative::allocate<Node>());
    jnative::BorrowedHandle borrowed(root.get());
    {
        jnative::NativeRegion native;
        std::fesetround(FE_DOWNWARD);
        {
            jnative::ManagedAccess access;
            require(jnative::current_thread()->state == jnative::ThreadState::running_managed,
                    "Storage access did not enter managed execution");
            auto value = static_cast<Node*>(heap.resolve(borrowed.id()));
            value->value.set(27);
            heap.collect();
            require(value->value.get() == 27, "Storage access lost its borrowed root");
            require(std::fegetround() == FE_DOWNWARD, "Storage access changed native rounding");
            {
                jnative::ManagedEntry callback;
                require(std::fegetround() == FE_TONEAREST, "Nested Java callback inherited native rounding");
            }
            require(std::fegetround() == FE_DOWNWARD, "Java callback did not restore native rounding");
        }
        require(jnative::current_thread()->state == jnative::ThreadState::in_native,
                "Storage access did not restore the native state");
        require(std::fegetround() == FE_DOWNWARD, "Storage access exit changed native rounding");
    }
    require(std::fegetround() == FE_TONEAREST, "Import exit did not restore Java rounding");
}
void generated_import_environments() {
    jnative::platform::FloatingState original{};
    jnative::platform::save_floating(original);
    std::fesetround(FE_DOWNWARD);
    std::feraiseexcept(FE_INVALID | FE_INEXACT);
#if defined(__SSE2__) || defined(_M_X64)
    const unsigned caller_control = _mm_getcsr() | (1u << 15) | (1u << 6);
    _mm_setcsr(caller_control);
#endif
    {
        jnative::ManagedEntry entry;
        {
            jnative::JavaNativeRegion native;
            require(jnative::current_thread()->state == jnative::ThreadState::in_native,
                    "Generated import did not cooperate with collection");
            std::fesetround(FE_UPWARD);
            std::feraiseexcept(FE_OVERFLOW);
#if defined(__SSE2__) || defined(_M_X64)
            _mm_setcsr(_mm_getcsr() | (1u << 15) | (1u << 6));
#endif
            {
                jnative::ManagedEntry callback;
                require(std::fegetround() == FE_TONEAREST, "Callback inherited import rounding");
                jnative::Heap::instance().collect();
                {
                    jnative::JavaLeafNativeRegion leaf;
                    std::fesetround(FE_TOWARDZERO);
                }
                require(std::fegetround() == FE_TONEAREST, "Leaf import changed Java rounding");
            }
            require(std::fegetround() == FE_UPWARD, "Callback lost native rounding");
            require((std::fetestexcept(FE_ALL_EXCEPT) & FE_OVERFLOW) != 0,
                    "Callback lost native exception flags");
        }
        require(jnative::current_thread()->state == jnative::ThreadState::running_managed,
                "Generated import did not reenter managed execution");
        require(std::fegetround() == FE_TONEAREST, "Generated import changed Java rounding");
        require(std::fetestexcept(FE_ALL_EXCEPT) == 0, "Generated import leaked native exceptions");
#if defined(__SSE2__) || defined(_M_X64)
        require((_mm_getcsr() & ((1u << 15) | (1u << 6) | (3u << 13))) == 0,
                "Generated import leaked SIMD controls");
#endif
    }
    require(std::fegetround() == FE_DOWNWARD, "Managed exit lost caller rounding");
    require((std::fetestexcept(FE_ALL_EXCEPT) & (FE_INVALID | FE_INEXACT)) == (FE_INVALID | FE_INEXACT),
            "Managed exit lost caller exception flags");
#if defined(__SSE2__) || defined(_M_X64)
    require(_mm_getcsr() == caller_control, "Managed exit lost caller SIMD controls");
#endif
    jnative::platform::restore_floating(original);
}
void roots_and_cycles() {
    auto& heap = jnative::Heap::instance();
    heap.collection_threshold(4);
    {
        jnative::LocalRoot<Node> first(jnative::allocate<Node>());
        jnative::LocalRoot<Node> second(jnative::allocate<Node>());
        first->next.set(second.get());
        second->next.set(first.get());
        first->value.set(42);
        heap.collect();
        require(heap.statistics().first == 2, "Rooted cycle was lost");
        require(second->next.get()->value.get() == 42, "Managed edge was lost");
        {
            jnative::GlobalHandle<Node> handle(first.get());
            first.set(nullptr);
            second.set(nullptr);
            heap.collect();
            require(heap.statistics().first == 2, "Native handle was not traced");
            require(handle.get()->value.get() == 42, "Native handle changed identity");
        }
    }
    heap.collect();
    require(heap.statistics().first == 0, "Unreachable cycle was not reclaimed");
}
void multiple_mutators() {
    auto& heap = jnative::Heap::instance();
    heap.collection_threshold(7);
    std::atomic<int> completed{0};
    auto work = [&] {
        jnative::ThreadAttachment thread;
        jnative::LocalRoot<Node> root(jnative::allocate<Node>());
        for (int i = 0; i < 2000; ++i) {
            jnative::LocalRoot<Node> next(jnative::allocate<Node>());
            next->value.set(i);
            root->next.set(next.get());
            if (i % 11 == 0) heap.collect();
            require(root->next.get()->value.get() == i, "Mutator root corrupted");
            jnative::safepoint();
        }
        ++completed;
    };
    std::thread first(work), second(work);
    {
        jnative::NativeRegion waiting(jnative::ThreadState::blocked_managed);
        first.join();
        second.join();
    }
    require(completed == 2, "Workers did not finish");
    heap.collect();
    require(heap.statistics().first == 0, "Thread detach retained roots");
}
void allocation_queues() {
    auto& heap = jnative::Heap::instance();
    heap.collect();
    heap.collection_threshold(100000);
    {
        jnative::LocalRoot<Node> local(jnative::allocate<Node>());
        local->value.set(31);
        require(heap.statistics().first == 1, "Statistics missed an unflushed allocation");
        jnative::GlobalHandle<Node> exported;
        std::atomic<bool> ready{false}, resume{false};
        std::exception_ptr failure;
        std::thread worker([&] {
            try {
                jnative::ThreadAttachment attached;
                jnative::LocalRoot<Node> parent(jnative::allocate<Node>());
                parent->next.set(jnative::allocate<Node>());
                parent->next.get()->value.set(47);
                jnative::allocate<Node>();
                exported = jnative::GlobalHandle<Node>(parent.get());
                {
                    jnative::NativeRegion waiting;
                    ready.store(true, std::memory_order_release);
                    while (!resume.load(std::memory_order_acquire)) std::this_thread::yield();
                }
                parent->next.set(jnative::allocate<Node>());
                parent->next.get()->value.set(59);
            } catch (...) {
                failure = std::current_exception();
                ready.store(true, std::memory_order_release);
            }
        });
        while (!ready.load(std::memory_order_acquire)) jnative::safepoint();
        bool correct = heap.statistics().first == 4;
        heap.collect();
        correct = correct && heap.statistics().first == 3 && local->value.get() == 31
                && exported.get() && exported.get()->next.get()->value.get() == 47;
        resume.store(true, std::memory_order_release);
        {
            jnative::NativeRegion waiting;
            worker.join();
        }
        if (failure) std::rethrow_exception(failure);
        require(correct, "Collection lost queued objects or retained queued garbage");
        require(heap.statistics().first == 4, "Detach lost its pending allocation queue");
        heap.collect();
        require(heap.statistics().first == 3 && exported.get()->next.get()->value.get() == 59,
                "An object published before detach was not retained");
    }
    heap.collect();
    require(heap.statistics().first == 0, "Allocation queues leaked released objects");
    heap.collection_threshold(1);
    auto collections = heap.statistics().second;
    {
        jnative::LocalRoot<Node> first(jnative::allocate<Node>());
        jnative::LocalRoot<Node> second(jnative::allocate<Node>());
        require(heap.statistics().second == collections + 1,
                "Allocation queues bypassed the requested collection interval");
    }
    heap.collect();
}
void monitor_publication() {
    auto& heap = jnative::Heap::instance();
    jnative::LocalRoot<Node> object(jnative::allocate<Node>());
    std::shared_ptr<jnative::Monitor> peers[8];
    std::thread workers[8];
    for (std::size_t i = 0; i < 8; ++i) {
        auto pointer = object.get();
        workers[i] = std::thread([&, i, pointer] {
            jnative::ThreadAttachment attached;
            peers[i] = pointer->monitor();
            for (int pass = 0; pass < 100; ++pass) {
                jnative::MonitorGuard guard(pointer);
                pointer->value.set(pointer->value.get() + 1);
            }
        });
    }
    {
        jnative::NativeRegion waiting;
        for (auto& worker : workers) worker.join();
    }
    require(object->value.get() == 800, "Concurrent monitor initialization lost exclusion");
    for (const auto& peer : peers)
        require(peer == peers[0], "Concurrent callers observed different monitors");
    std::weak_ptr<jnative::Monitor> weak = peers[0];
    object.set(nullptr);
    heap.collect();
    require(!weak.expired(), "Object collection invalidated retained monitor callbacks");
    for (auto& peer : peers) peer.reset();
    require(weak.expired(), "Monitor publication leaked its shared owner");
}
void exception_roots() {
    auto& heap = jnative::Heap::instance();
    try {
        jnative::LocalRoot<jnative::Throwable> error(jnative::allocate<jnative::Throwable>("java/lang/IllegalArgumentException"));
        error->message.set(jnative::allocate<jnative::String>(u"exception root"));
        throw jnative::Thrown(error.get());
    } catch (const jnative::Thrown& error) {
        heap.collect();
        auto object = dynamic_cast<jnative::Throwable*>(error.object());
        require(jnative::as_string(object->message.get())->value == u"exception root", "Exception was not rooted across unwinding");
    }
    heap.collect();
    require(heap.statistics().first == 0, "Caught exception retained an expired handle");
}
void borrowed_handles() {
    auto& heap = jnative::Heap::instance();
    heap.collection_threshold(1);
    jn_handle expired = 0, retained = 0;
    {
        jnative::LocalRoot<Node> value(jnative::allocate<Node>());
        value->value.set(12345);
        {
            jnative::BorrowedHandle borrowed(value.get());
            expired = borrowed.id();
            value.set(nullptr);
            require(jn_release(expired) == JN_INVALID_ARGUMENT, "Borrowed handle accepted owned release");
            std::atomic<bool> correct{false};
            std::thread reader([&] {
                jnative::ThreadAttachment attached;
                jnative::LocalRoot<Node> root(static_cast<Node*>(heap.resolve(expired)));
                heap.collect();
                correct = root->value.get() == 12345 && jn_retain(expired, &retained) == JN_OK;
            });
            { jnative::NativeRegion waiting; reader.join(); }
            require(correct, "Borrowed handle failed across threads and collection");
            require(retained && retained != expired, "Retain reused a borrowed token");
        }
        heap.collect();
        require(static_cast<Node*>(heap.resolve(retained))->value.get() == 12345,
                "Retained import argument expired with its call");
        jn_handle invalid = 0;
        require(jn_retain(expired, &invalid) == JN_INVALID_ARGUMENT, "Expired borrowed token retained");
        for (int reuse = 0; reuse < 2000; ++reuse) {
            jnative::BorrowedHandle replacement(heap.resolve(retained));
            require(replacement.id() != expired, "Borrowed token aliased slot reuse");
            require(heap.resolve(replacement.id()) == heap.resolve(retained), "Borrowed identity changed");
        }
        {
            std::vector<std::unique_ptr<jnative::BorrowedHandle>> nested;
            for (int depth = 0; depth < 200; ++depth)
                nested.push_back(jnative::make_owned<jnative::BorrowedHandle>(heap.resolve(retained)));
            heap.collect();
            for (const auto& item : nested)
                require(static_cast<Node*>(heap.resolve(item->id()))->value.get() == 12345,
                        "Nested borrowed block lost its root");
            while (!nested.empty()) nested.pop_back();
        }
        require(jn_release(retained) == JN_OK, "Retained borrowed token could not be released");
        jnative::BorrowedHandle null(nullptr);
        require(null.id() == 0, "Null borrowed handle was allocated");
    }
    // Thread-owned blocks can be recycled after detach, without recycling tokens.
    std::atomic<bool> correct{true};
    for (int pass = 0; pass < 12; ++pass) {
        std::thread worker([&] {
            jnative::ThreadAttachment attached;
            jnative::BorrowedHandle value(jnative::allocate<Node>());
            heap.collect();
            if (!heap.resolve(value.id())) correct = false;
            jn_handle invalid = 0;
            if (jn_retain(expired, &invalid) != JN_INVALID_ARGUMENT) correct = false;
            expired = value.id();
        });
        { jnative::NativeRegion waiting; worker.join(); }
    }
    require(correct, "Detached thread's borrowed token aliased a replacement");
    heap.collect();
    require(heap.statistics().first == 0, "Borrowed slots retained expired roots");
}
void borrowed_reuse_race() {
    auto& heap = jnative::Heap::instance();
    jnative::LocalRoot<Node> original(jnative::allocate<Node>());
    jnative::LocalRoot<Node> replacement(jnative::allocate<Node>());
    std::atomic<bool> ready{false}, stop{false}, correct{true};
    std::thread observer;
    {
        jnative::BorrowedHandle borrowed(original.get());
        const auto token = borrowed.id();
        auto expected = original.get();
        observer = std::thread([&, token, expected] {
            jnative::ThreadAttachment attached;
            jnative::NativeRegion native;
            ready.store(true, std::memory_order_release);
            while (!stop.load(std::memory_order_acquire)) {
                try {
                    if (heap.resolve(token) != expected) correct.store(false);
                } catch (const std::logic_error&) {
                    // Expiration is allowed; resolving a replacement is not.
                }
            }
        });
        while (!ready.load(std::memory_order_acquire)) jnative::thread_yield();
    }
    for (int pass = 0; pass < 20000; ++pass) {
        jnative::BorrowedHandle reused(replacement.get());
        if (pass % 200 == 0) heap.collect();
    }
    stop.store(true, std::memory_order_release);
    { jnative::NativeRegion waiting; observer.join(); }
    require(correct, "A stale borrowed token resolved to a replacement object");
}
void monitor_contention() {
    auto& heap = jnative::Heap::instance();
    heap.collection_threshold(1);
    std::atomic<int> count{0};
    std::atomic<jnative::Object*> identity{nullptr};
    std::atomic<bool> inconsistent{false};
    auto work = [&] {
        jnative::ThreadAttachment attached;
        for (int i = 0; i < 300; ++i) {
            jnative::LocalRoot<> object(jnative::class_object("runtime.MonitorContention"));
            auto expected = static_cast<jnative::Object*>(nullptr);
            identity.compare_exchange_strong(expected, object.get());
            if (identity.load() != object.get()) inconsistent = true;
            jnative::MonitorGuard lock(object.get());
            int before = count.load();
            if (i % 5 == 0) heap.collect();
            jnative::thread_yield();
            count.store(before + 1);
        }
    };
    std::thread a(work), b(work);
    { jnative::NativeRegion blocked; a.join(); b.join(); }
    require(!inconsistent, "Class monitor identity changed");
    require(count == 600, "Monitor allowed overlapping owners");
}
void monitor_fast_path() {
    auto& heap = jnative::Heap::instance();
    auto thread = jnative::current_thread();
    const auto previous_roots = thread->held_monitors.size();
    jnative::LocalRoot<Node> object(jnative::allocate<Node>());
    object->value.set(127);
#if defined(JNATIVE_PLATFORM_TRY_LOCK)
    // Nonblocking Java synchronization does not cross a foreign-code boundary.
    jnative::platform::FloatingState original;
    jnative::platform::save_floating(original);
    std::fesetround(FE_UPWARD);
    std::feraiseexcept(FE_INVALID | FE_INEXACT);
    const auto exceptions = std::fetestexcept(FE_ALL_EXCEPT);
    jnative::monitor_enter(object.get());
    jnative::monitor_enter(object.get());
    jnative::monitor_exit(object.get());
    jnative::monitor_exit(object.get());
    bool preserved = std::fegetround() == FE_UPWARD
            && std::fetestexcept(FE_ALL_EXCEPT) == exceptions;
    jnative::platform::restore_floating(original);
    require(preserved, "Uncontended monitor changed the floating environment");
#endif
    std::atomic<bool> done{false};
    std::atomic<int> collections{0};
    std::thread collector([&] {
        jnative::ThreadAttachment attached;
        for (int pass = 0; pass < 40; ++pass) {
            heap.collect();
            collections.fetch_add(1);
        }
        done.store(true, std::memory_order_release);
    });
    bool correct = true;
    const auto deadline = jnative::platform::monotonic_nanos() + 30000000000LL;
    // No safepoint, yield, allocation or native transition in this loop apart
    // from the monitor operations themselves. A fast path must still park.
    while (!done.load(std::memory_order_acquire)
            && jnative::platform::monotonic_nanos() < deadline) {
        jnative::monitor_enter(object.get());
        jnative::monitor_enter(object.get());
        correct = correct && thread->held_monitors.size() == previous_roots + 2
                && object->value.get() == 127;
        jnative::monitor_exit(object.get());
        jnative::monitor_exit(object.get());
        correct = correct && thread->held_monitors.size() == previous_roots;
    }
    bool progressed = done.load(std::memory_order_acquire) && collections.load() == 40;
    // If a regression starves the collector, unblock it before reporting failure.
    { jnative::NativeRegion waiting; collector.join(); }
    require(progressed, "Uncontended monitor loop prevented collection");
    require(correct, "Recursive monitor root ownership changed");
    require(thread->held_monitors.size() == previous_roots, "Monitor roots leaked");
}
}
void platform_contracts() {
    using namespace jnative::platform;
    const std::u16string unicode = u"\u03a9-\U0001f680";
    require(Path(unicode).u16string() == unicode, "UTF-16 path round trip");
    require(Path(Path(unicode).wstring()).u16string() == unicode, "Native wide path round trip");
    try { Path("\xc0\x80").u16string(); require(false, "Overlong UTF-8 accepted"); }
    catch (const std::range_error&) {}
    try { Path(std::u16string(1, char16_t(0xd800))); require(false, "Unpaired surrogate accepted"); }
    catch (const std::range_error&) {}
    if (windows_paths()) {
        require(!Path("\\other").is_absolute() && !Path("C:relative").is_absolute(), "Drive-relative path classification");
        require((Path("C:\\base") / "c:relative").string() == "C:\\base\\relative", "Same-drive relative join");
        require((Path("C:\\base") / "\\other").string() == "C:\\other", "Root-relative join lost its drive");
        require((Path("C:\\base") / "D:relative").string() == "D:relative", "Different-drive join");
        require(Path("\\..\\other").lexically_normal().string() == "\\other", "Root-relative normalization");
        require((Path("\\\\server\\share\\base") / "\\other").string() == "\\\\server\\share\\other", "UNC root join");
    }
    Path directory("runtime-files-" + std::to_string(monotonic_nanos()));
    create_directories(directory);
    Path file = directory / Path(std::u16string(u"report-\u03a9-\U0001f680.txt"));
    { OutputFile output(file, std::ios::binary); output << "unicode content"; }
    require(exists(file), "Unicode file was not created");
    require(file_size(file) == 15, "File size mismatch");
    { InputFile input(file, std::ios::binary); std::string line; std::getline(input, line); require(line == "unicode content", "Unicode file did not round trip"); }
    require((directory / "unused" / ".." / file.filename()).lexically_normal() == file, "Path normalization mismatch");
    require(absolute(file).is_absolute(), "Expected an absolute path");
    require(directory_entries(directory).size() == 1, "Directory listing mismatch");
    Path empty = directory / "empty", copied = directory / "copied";
    { OutputFile output(empty, std::ios::binary); }
    copy_file(empty, copied);
    require(file_size(copied) == 0, "Empty file copy failed");
    require(remove(empty) && remove(copied), "Empty file cleanup failed");
    require(remove(file) && !remove(file), "File removal mismatch");
    require(remove(directory), "Directory removal mismatch");
    require(wall_millis() > 0 && monotonic_nanos() > 0, "Clock unavailable");
    require(jnative::to_text(1.25) == u"1.25", "Decimal formatting mismatch");
    require(jnative::to_text(-0.0) == u"-0.0", "Negative zero formatting mismatch");
    require(jnative::to_text(std::numeric_limits<double>::denorm_min()) == u"4.9E-324", "Subnormal formatting mismatch");
}
void root_addresses() {
    using namespace jnative;
    auto& heap = Heap::instance();
    RootValue<String> first(allocate<String>(u"first"));
    RootValue<String> second;
    {
        RootAddressFrame<2> roots({first.address(), second.address()});
        second.set(allocate<String>(u"second"));
        heap.collect();
        require(first.get()->value == u"first" && second.get()->value == u"second", "Cold root registration lost a local");
        first.set(allocate<String>(u"replacement"));
        heap.collect();
        require(first.get()->value == u"replacement", "A registered root address missed an assignment");
        try {
            RootValue<String> nested(allocate<String>(u"nested"));
            RootAddressFrame<1> nested_roots({nested.address()});
            LocalRoot<String> local(allocate<String>(u"local"));
            RootFrame<1> frame;
            frame.slot(0) = allocate<String>(u"frame");
            heap.collect();
            require(nested.get()->value == u"nested" && local->value == u"local", "Nested cold roots were lost");
            raise("java/lang/IllegalStateException");
        } catch (const Thrown&) {
            heap.collect();
            require(second.get()->value == u"second", "Unwinding damaged an outer root address frame");
        }
    }
    require(current_thread()->root_addresses == nullptr, "Cold root frame escaped its scope");
}
void array_kernel_contracts() {
    using namespace jnative;
    auto expect_java = [](const char* type, auto operation) {
        bool caught = false;
        try { operation(); }
        catch (const Thrown& error) { caught = std::strcmp(error.object()->type_name(), type) == 0; }
        require(caught, "Array kernel exception type changed");
    };
    LocalRoot<> integers(new_array("[I", 4096));
    for (std::int32_t i = 0; i < 4096; ++i) array_set<std::int32_t>(integers.get(), i, i);
    array_copy_cooperative(integers.get(), 0, integers.get(), 511, 3072);
    for (std::int32_t i = 0; i < 3072; ++i)
        require(array_get<std::int32_t>(integers.get(), 511 + i) == i, "Backward chunked copy corrupted overlap");
    array_copy_cooperative(integers.get(), 511, integers.get(), 0, 3072);
    for (std::int32_t i = 0; i < 3072; ++i)
        require(array_get<std::int32_t>(integers.get(), i) == i, "Forward chunked copy corrupted overlap");
    expect_java("java/lang/ArrayIndexOutOfBoundsException", [&] {
        array_copy_cooperative(integers.get(), 0, integers.get(), 1025, 3072);
    });
    require(array_get<std::int32_t>(integers.get(), 1025) == 1025, "Invalid full copy wrote an early chunk");
    arrays_fill_i(integers.get(), 0, 4096, -77);
    expect_java("java/lang/ArrayIndexOutOfBoundsException", [&] { arrays_fill_i(integers.get(), 0, 4097, 8); });
    require(array_get<std::int32_t>(integers.get(), 0) == -77, "Invalid fill wrote an early chunk");
    expect_java("java/lang/IllegalArgumentException", [&] { arrays_fill_i(integers.get(), 2, 1, 8); });
    expect_java("java/lang/NullPointerException", [&] { arrays_fill_i(nullptr, 2, 1, 8); });
    arrays_fill_i(integers.get(), 4096, 4096, 8);
    LocalRoot<> duplicate(new_array("[I", 4096));
    arrays_fill_i(duplicate.get(), 0, 4096, -77);
    require(arrays_equals(integers.get(), duplicate.get(), 'I'), "Chunked primitive equality failed");
    std::uint32_t expected_hash = 1;
    for (int i = 0; i < 4096; ++i) expected_hash = 31u * expected_hash + std::uint32_t(-77);
    require(arrays_hash(integers.get(), 'I') == signed32(expected_hash), "Array hash overflow changed");
    array_set<std::int32_t>(duplicate.get(), 4095, 8);
    require(!arrays_equals(integers.get(), duplicate.get(), 'I'), "Equality skipped its final chunk");
    require(arrays_equals(nullptr, nullptr, 'I') && !arrays_equals(integers.get(), nullptr, 'I')
            && arrays_hash(nullptr, 'I') == 0, "Null array equality/hash changed");
    LocalRoot<> empty(new_array("[I", 0));
    require(arrays_hash(empty.get(), 'I') == 1, "Empty array hash changed");
    LocalRoot<> confined(new_confined_array<std::int32_t>("[I", 4096));
    try { arrays_fill_i(confined.get(), 0, 1, 3); require(false, "Fill accepted confined storage"); }
    catch (const std::logic_error&) {}
    try { arrays_equals(confined.get(), confined.get(), 'I'); require(false, "Equality accepted confined storage"); }
    catch (const std::logic_error&) {}
    try { arrays_hash(confined.get(), 'I'); require(false, "Hash accepted confined storage"); }
    catch (const std::logic_error&) {}

    LocalRoot<> references(new_array("[Ljava/lang/Object;", 2050));
    LocalRoot<> strings(new_array("[Ljava/lang/String;", 2050));
    LocalRoot<String> text(allocate<String>(u"array"));
    LocalRoot<> wrong(allocate<Object>());
    arrays_fill_reference(references.get(), 0, 2050, text.get());
    reference_set(references.get(), 1100, wrong.get());
    expect_java("java/lang/ArrayStoreException", [&] {
        array_copy_cooperative(references.get(), 0, strings.get(), 0, 2050);
    });
    for (int i = 0; i < 2050; ++i)
        require(reference_get(strings.get(), i) == (i < 1100 ? text.get() : nullptr), "Covariant copy partial writes changed");
    arrays_fill_reference(strings.get(), 0, 0, wrong.get());
    expect_java("java/lang/ArrayStoreException", [&] { arrays_fill_reference(strings.get(), 0, 2050, wrong.get()); });
    require(reference_get(strings.get(), 0) == text.get(), "Incompatible reference fill changed an entry");
    arrays_fill_reference(strings.get(), 0, 2050, nullptr);
    require(reference_get(strings.get(), 0) == nullptr && reference_get(strings.get(), 2049) == nullptr,
            "Reference fill missed a chunk");

    std::atomic<bool> released{false};
    {
        LocalRoot<InitializationProbe> value(allocate<InitializationProbe>(released));
        arrays_fill_reference(references.get(), 0, 2050, value.get());
    }
    Heap::instance().collect();
    require(!released.load(), "Native reference fill failed to retain its values");
    arrays_fill_reference(references.get(), 0, 2050, nullptr);
    Heap::instance().collect();
    require(released.load(), "Native reference clear retained removed values");

    LocalRoot<> booleans(new_array("[Z", 2));
    arrays_fill_z(booleans.get(), 0, 1, 1);
    require(arrays_hash(booleans.get(), 'Z') == 40359, "Boolean array hash constants changed");
    LocalRoot<> bytes(new_array("[B", 1));
    arrays_fill_b(bytes.get(), 0, 1, -1);
    require(arrays_hash(bytes.get(), 'B') == 30, "Byte array hash lost sign extension");
    LocalRoot<> characters(new_array("[C", 1));
    arrays_fill_c(characters.get(), 0, 1, 65535);
    require(arrays_hash(characters.get(), 'C') == 65566, "Char array hash lost unsigned value");
    LocalRoot<> shorts(new_array("[S", 1));
    arrays_fill_s(shorts.get(), 0, 1, -32768);
    require(arrays_hash(shorts.get(), 'S') == -32737, "Short array hash lost sign extension");
    LocalRoot<> longs(new_array("[J", 1));
    arrays_fill_j(longs.get(), 0, 1, INT64_C(0x1234567887654321));
    require(arrays_hash(longs.get(), 'J') == signed32(31u + UINT32_C(0x95511559)), "Long array hash changed");
    LocalRoot<> floats(new_array("[F", 1));
    LocalRoot<> other_floats(new_array("[F", 1));
    arrays_fill_f(floats.get(), 0, 1, bits_float(signed32(UINT32_C(0x7fc00001))));
    arrays_fill_f(other_floats.get(), 0, 1, bits_float(signed32(UINT32_C(0xffa00002))));
    require(arrays_equals(floats.get(), other_floats.get(), 'F')
            && arrays_hash(floats.get(), 'F') == signed32(31u + UINT32_C(0x7fc00000)), "Float NaN canonicalization changed");
    arrays_fill_f(floats.get(), 0, 1, -0.0f);
    arrays_fill_f(other_floats.get(), 0, 1, 0.0f);
    require(!arrays_equals(floats.get(), other_floats.get(), 'F')
            && arrays_hash(floats.get(), 'F') != arrays_hash(other_floats.get(), 'F'), "Float signed zeros collapsed");
    LocalRoot<> doubles(new_array("[D", 1));
    LocalRoot<> other_doubles(new_array("[D", 1));
    arrays_fill_d(doubles.get(), 0, 1, bits_double(INT64_C(0x7ff8000000000001)));
    arrays_fill_d(other_doubles.get(), 0, 1, bits_double(signed64(UINT64_C(0xfff0000000000002))));
    require(arrays_equals(doubles.get(), other_doubles.get(), 'D')
            && arrays_hash(doubles.get(), 'D') == signed32(31u + UINT32_C(0x7ff80000)), "Double NaN canonicalization changed");
    arrays_fill_d(doubles.get(), 0, 1, -0.0);
    arrays_fill_d(other_doubles.get(), 0, 1, 0.0);
    require(!arrays_equals(doubles.get(), other_doubles.get(), 'D')
            && arrays_hash(doubles.get(), 'D') != arrays_hash(other_doubles.get(), 'D'), "Double signed zeros collapsed");
}
void text_codec_kernel_contracts() {
    using namespace jnative;
    LocalRoot<> bytes(new_array("[B", 9));
    for (int i = 0; i < 9; ++i) array_set<std::int8_t>(bytes.get(), i, static_cast<std::int8_t>('1' + i));
    auto checksum = crc32_update_bytes(-1, bytes.get(), 0, 9);
    require(~static_cast<std::uint32_t>(checksum) == UINT32_C(0xcbf43926), "CRC32 known vector changed");
    require(crc32_update(-1, -1) == crc32_update(-1, 255), "CRC32 scalar input was not masked");
    LocalRoot<> encoded(base64_encode(bytes.get()));
    LocalRoot<> encoded_text(base64_encode_string(bytes.get()));
    require(as_string(encoded_text.get())->value == u"MTIzNDU2Nzg5", "Base64 known vector changed");
    LocalRoot<> decoded(base64_decode(encoded.get()));
    LocalRoot<> decoded_text(base64_decode_string(encoded_text.get()));
    require(arrays_equals(bytes.get(), decoded.get(), 'B') && arrays_equals(bytes.get(), decoded_text.get(), 'B'),
            "Base64 byte/text entry points differ");
    LocalRoot<> confined(new_confined_array<std::int8_t>("[B", 3));
    try { base64_encode(confined.get()); require(false, "Base64 accepted confined storage"); }
    catch (const std::logic_error&) { }
    try { crc32_update_bytes(-1, confined.get(), 0, 3); require(false, "CRC32 accepted confined storage"); }
    catch (const std::logic_error&) { }
    LocalRoot<String> text(allocate<String>(u"a\U0001f642b"));
    LocalRoot<> characters(string_to_char_array(text.get()));
    require(array_length(characters.get()) == 4 && array_get<std::uint16_t>(characters.get(), 1) == 0xd83d,
            "String copy changed UTF-16 units");
    require(string_index_of_code_point(text.get(), 0x1f642, 0) == 1
            && string_last_index_of_code_point(text.get(), 0x1f642, INT32_MAX) == 1
            && string_code_point_count(text.get(), 0, 4) == 3
            && string_offset_by_code_points(text.get(), 4, -2) == 1, "Supplementary string traversal changed");
    arrays_fill_c(characters.get(), 0, 4, 7);
    bool caught = false;
    try { string_get_chars(text.get(), 0, 4, characters.get(), 1); }
    catch (const Thrown& error) { caught = std::strcmp(error.object()->type_name(), "java/lang/StringIndexOutOfBoundsException") == 0; }
    require(caught && array_get<std::uint16_t>(characters.get(), 1) == 7, "Invalid String copy modified destination");
}
std::int32_t reference_string_hash(const std::u16string& text) {
    std::uint32_t result = 0;
    for (char16_t value : text) result = result * UINT32_C(31) + static_cast<std::uint32_t>(value);
    std::int32_t signed_result;
    std::memcpy(&signed_result, &result, sizeof(result));
    return signed_result;
}
void long_string_contracts() {
    using namespace jnative;
    const std::size_t sizes[] = {1023, 1024, 1025, 2047, 2048, 2049, 4097};
    LocalRoot<> not_string(allocate<Object>());
    for (auto size : sizes) {
        std::u16string units(size, u'\uffff');
        for (std::size_t i = 0; i < size; i += 17) units[i] = static_cast<char16_t>(i * 97);
        units[size - 2] = 0xd800;
        units[size - 1] = 0xdc00;
        LocalRoot<String> first(allocate<String>(units)), equal(allocate<String>(units));
        const auto expected = reference_string_hash(units);
        require(string_hash(first.get()) == expected, "String hash changed UTF-16 overflow arithmetic");
        require(first->cached_hash.get() == expected && string_hash(first.get()) == expected,
                "Repeated String hash changed its cached value");
        require(string_equals(first.get(), equal.get()) && string_equals(first.get(), first.get()),
                "String equality skipped a boundary or identity match");
        require(!string_equals(first.get(), nullptr) && !string_equals(first.get(), not_string.get()),
                "String equality accepted null or a non-String");
        auto mismatch = units;
        mismatch.back() ^= 1;
        LocalRoot<String> late(allocate<String>(std::move(mismatch)));
        require(!string_equals(first.get(), late.get()), "String equality skipped a late mismatch");
        LocalRoot<String> shorter(allocate<String>(units.substr(0, size - 1)));
        require(!string_equals(first.get(), shorter.get()), "String equality ignored its UTF-16 length");
        if (size > 1024) {
            mismatch = units;
            mismatch[1024] ^= 1;
            LocalRoot<String> boundary(allocate<String>(std::move(mismatch)));
            require(!string_equals(first.get(), boundary.get()), "String equality skipped its second chunk");
        }
    }
    LocalRoot<String> zero(allocate<String>(std::u16string(4097, u'\0')));
    require(string_hash(zero.get()) == 0 && zero->cached_hash.get() == 0 && string_hash(zero.get()) == 0,
            "Long zero-hash String changed during recomputation");
    LocalRoot<String> empty(allocate<String>(u""));
    require(string_hash(empty.get()) == 0 && !string_equals(zero.get(), empty.get()),
            "Embedded zero units were treated as an empty String");
}
void long_string_collection(bool hashing) {
    using namespace jnative;
    auto& heap = Heap::instance();
    // All-zero text deliberately hashes to zero, so every hash call scans and
    // cannot satisfy the progress assertion through the cached fast path.
    const std::u16string units(262145, u'\0');
    LocalRoot<String> first(allocate<String>(units)), second;
    if (!hashing) second.set(allocate<String>(units));
    auto* first_pointer = first.get();
    auto* second_pointer = second.get();
    auto* roots_before = current_thread()->roots;
    std::atomic<bool> done{false};
    std::atomic<int> collections{0};
    std::thread collector([&] {
        ThreadAttachment attached;
        for (int i = 0; i < 32; ++i) {
            heap.collect();
            collections.fetch_add(1, std::memory_order_relaxed);
        }
        done.store(true, std::memory_order_release);
    });
    // There are no caller roots for these arguments during the scan. Collection
    // cannot begin before the helper's first poll, when its own roots must exist.
    first.set(nullptr);
    second.set(nullptr);
    bool correct = true;
    std::size_t calls = 0;
    const auto deadline = platform::monotonic_nanos() + 30000000000LL;
    while (!done.load(std::memory_order_acquire) && platform::monotonic_nanos() < deadline) {
        const bool value = hashing ? string_hash(first_pointer) == 0
                : string_equals(first_pointer, second_pointer);
        correct = correct && value && current_thread()->roots == roots_before;
        ++calls;
    }
    const bool progressed = done.load(std::memory_order_acquire) && collections.load() == 32;
    first.set(first_pointer);
    second.set(second_pointer);
    // Release a starved collector before reporting the failure, as in the
    // existing monitor progress test; joining a running managed thread can block GC.
    { NativeRegion waiting; collector.join(); }
    require(progressed && calls > 0, hashing ? "Long String hash prevented collection"
                                           : "Long String equality prevented collection");
    require(correct && first->value == units && (!second.get() || second->value == units),
            "Long String scan lost its own argument roots or leaked root slots");
}
int main() {
    try {
        // The first entry also exercises lazy default-state initialization.
        floating_environments();
        {
        jnative::ThreadAttachment thread;
        require(jn_try_shutdown() == JN_BUSY, "Attached threads must block embedded shutdown");
        platform_contracts();
        managed_storage_access();
        native_storage();
        native_import_errors();
        generated_import_environments();
        recursive_initialization();
        roots_and_cycles();
        root_addresses();
        array_kernel_contracts();
        text_codec_kernel_contracts();
        long_string_contracts();
        long_string_collection(false);
        long_string_collection(true);
        multiple_mutators();
        allocation_queues();
        monitor_publication();
        exception_roots();
        borrowed_handles();
        borrowed_reuse_race();
        monitor_contention();
        monitor_fast_path();
        require(jnative::add(INT32_MAX, std::int32_t(1)) == INT32_MIN, "int overflow");
        require(jnative::mul(INT64_MAX, std::int64_t(2)) == -2, "long overflow");
        std::cout << "runtime contracts passed; collections=" << jnative::Heap::instance().statistics().second << '\n';
        }
        jn_handle value = 0;
        const uint8_t text[] = {'o', 'w', 'n', 'e', 'd'};
        require(jn_string_from_utf8(text, sizeof(text), &value) == JN_OK, "Native handle creation failed");
        require(jn_try_shutdown() == JN_BUSY, "Owned handles must block embedded shutdown");
        require(jn_release(value) == JN_OK, "Native handle release failed");
        require(jn_try_shutdown() == JN_OK, "Idle embedded shutdown failed");
        require(jn_attach_thread() == JN_SHUTTING_DOWN, "Shutdown must reject new work");
        return 0;
    } catch (const std::exception& error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
}
