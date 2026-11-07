#pragma once
#include "jn_diagnostics.hpp"
#include "jn_common.hpp"
#include "jn_primitive_transfer.hpp"
#include "jn_platform.hpp"
#include "jn_allocator.hpp"

#include <atomic>
#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <limits>
#include <memory>
#include <new>
#include <mutex>
#include <stdexcept>
#include <unordered_map>
#include <unordered_set>
#include <utility>
#include <vector>
#include <cmath>
#include <cstring>
#include <iostream>
#include <sstream>
#include <string>
#include <functional>
#include <chrono>
#include <initializer_list>
#if defined(__x86_64__) || defined(_M_X64)
#include <emmintrin.h>
#endif

namespace jnative {

static_assert(sizeof(std::int32_t) == 4 && sizeof(std::int64_t) == 8, "Java requires exact integer widths");
static_assert(sizeof(void*) <= sizeof(std::uint64_t), "Unsupported pointer width");
static_assert(sizeof(float) == 4 && sizeof(double) == 8, "Java requires binary32 and binary64 storage");
static_assert(std::numeric_limits<float>::is_iec559 && std::numeric_limits<double>::is_iec559, "Java requires IEEE floating point");

struct Object;
struct Monitor;
struct MonitorOwner;
enum class RuntimeKind : std::uint8_t { object, string, buffer, class_type, file_path };
class Tracer {
    std::vector<Object*> work_;
public:
    void visit(Object* object);
    void drain();
    void clear() noexcept { work_.clear(); }
};

// A C++ shell is allocated before Java <init> runs. Trace only managed edges.
struct Object {
private:
    std::atomic<MonitorOwner*> monitor_{nullptr};
public:
    const std::shared_ptr<Monitor>& monitor();
    // Zero for ordinary objects, a primitive descriptor or 'L' for arrays.
    // The concrete array constructor fixes this tag; accesses still check it.
    const char array_kind;
    const RuntimeKind runtime_kind;
    bool marked = false;
private:
    std::uint32_t generated_type_id_ = 0;
protected:
    // Shell construction finishes before Java constructors or managed publication.
    // Each derived shell replaces the base ID with its actual Java class.
    void set_type_id(std::uint32_t value) { generated_type_id_ = value; }
public:
    explicit Object(char array_kind = 0, RuntimeKind runtime_kind = RuntimeKind::object);
    static void* operator new(std::size_t bytes) { return managed_allocate(bytes); }
    static void* operator new(std::size_t bytes, ManagedArena* arena) { return managed_allocate(bytes, arena); }
    static void operator delete(void* value) noexcept { managed_deallocate(value); }
    static void operator delete(void* value, ManagedArena*) noexcept { managed_deallocate(value); }
    virtual void trace(Tracer&) {}
    virtual const char* type_name() const { return "java/lang/Object"; }
    // Runtime-owned classes use zero. Reading immutable type metadata needs no
    // virtual call, including interface dispatch and covariant array stores.
    std::uint32_t type_id() const { return generated_type_id_; }
    virtual ~Object();
};

JNATIVE_FORCEINLINE void Tracer::visit(Object* object) {
    if (object && !object->marked) {
        object->marked = true;
        work_.push_back(object);
    }
}

template<class T> class ManagedField {
protected:
    std::atomic<T> value_{};
public:
    ManagedField() = default;
    explicit ManagedField(T value) : value_(value) {}
    T get() const { return value_.load(std::memory_order_seq_cst); }
    void set(T value) { value_.store(value, std::memory_order_seq_cst); }
    T exchange(T value) { return value_.exchange(value, std::memory_order_seq_cst); }
    bool compare_exchange(T& expected, T desired) {
        return value_.compare_exchange_strong(expected, desired, std::memory_order_seq_cst);
    }
};

// Ordinary Java accesses need atomic storage to avoid C++ data-race undefined
// behavior, but only volatile fields and atomic APIs require a total order.
template<class T> class OrdinaryField : public ManagedField<T> {
public:
    OrdinaryField() = default;
    explicit OrdinaryField(T value) : ManagedField<T>(value) {}
    T get() const { return this->value_.load(std::memory_order_relaxed); }
    void set(T value) { this->value_.store(value, std::memory_order_relaxed); }
};

// The closed program proves these instance fields are accessed only from
// the entry thread. Unknown native/reflection access and worker-reachable
// fields keep OrdinaryField; volatile fields always keep ManagedField.
// Reference slots still participate in tracing. The stop-the-world collector
// reads them only after managed threads publish their roots and park.
template<class T> class OwnedField {
    T value_{};
public:
    OwnedField() = default;
    explicit OwnedField(T value) : value_(value) {}
    T get() const { return value_; }
    void set(T value) { value_ = value; }
};

struct RootSlot {
    Object* value = nullptr;
    RootSlot* previous = nullptr;
};

struct RootFrameLink {
    Object** values;
    std::size_t count;
    RootFrameLink* previous;
};
struct RootAddressFrameLink {
    Object*** addresses;
    std::size_t count;
    RootAddressFrameLink* previous;
};

enum class ThreadState { running_managed, at_safepoint, blocked_managed, in_native };
struct JavaFrame;
class Heap;
struct BorrowedSlot {
    std::atomic<std::uint64_t> token{0};
    std::atomic<Object*> object{nullptr};
    std::uint32_t index = 0;
    std::uint32_t generation = 0;
};
struct BorrowedBlock { BorrowedSlot slots[64]; };
struct StackLocation { const char* method; const char* source; int line; };
struct ThreadContext {
    Heap* heap = nullptr;
    ManagedArena* arena = nullptr;
    bool sweeping = false;
    RootSlot* roots = nullptr;
    RootFrameLink* root_frames = nullptr;
    RootAddressFrameLink* root_addresses = nullptr;
    JavaFrame* frame = nullptr;
    std::atomic<ThreadState> state{ThreadState::running_managed};
    const std::uint64_t identity;
    Object* java_thread = nullptr;
    std::vector<Object*> held_monitors;
    std::unordered_map<std::uint64_t, Object*> thread_locals;
    std::vector<BorrowedSlot*> free_borrowed;
    std::vector<BorrowedBlock*> borrowed_blocks;
    // Larger/nonlocal allocations need a pointer queue. Small pooled allocations
    // are tracked in their arena slots. Published counters cover both representations.
    std::vector<Object*> allocation_queue;
    std::atomic<std::size_t> queued_allocations{0};
    std::atomic<std::size_t> queued_bytes{0};
    // Prefixes already merged into global pressure. The difference from the
    // published counters is the current partial batch; no duplicate increments
    // are needed for every allocation. Only this mutator or the collector writes.
    std::size_t reported_allocations = 0;
    std::size_t reported_bytes = 0;
    ThreadContext();
};
inline ThreadContext* current_thread() noexcept { return static_cast<ThreadContext*>(platform::managed_context()); }
struct JavaFrame {
    const char* method;
    const char* source;
    int line = -1;
    JavaFrame* previous;
    JavaFrame(const char* method, const char* source);
    ~JavaFrame();
    JavaFrame(const JavaFrame&) = delete;
};

class Heap {
    friend class LoopSafepoint;
    ::jnative::platform::Mutex mutex_;
    ::jnative::platform::Condition changed_;
    std::vector<ThreadContext*> threads_;
    std::vector<Object*> objects_;
    std::vector<std::unique_ptr<ManagedArena>> arenas_;
    Tracer tracer_;
    std::unordered_map<std::uint64_t, Object*> handles_;
    std::unordered_set<std::uint64_t> external_handles_;
    std::atomic<BorrowedBlock*> borrowed_directory_[4096]{};
    std::vector<std::unique_ptr<BorrowedBlock>> borrowed_blocks_;
    std::vector<BorrowedBlock*> available_borrowed_blocks_;
    std::vector<ManagedField<Object*>*> static_roots_;
    std::uint64_t next_handle_ = 1;
    std::size_t collections_ = 0;
    std::atomic<std::size_t> allocations_{0};
    std::atomic<std::size_t> allocation_bytes_{0};
    std::atomic<bool> shared_allocation_pressure_{false};
    std::size_t total_allocations_ = 0;
    std::size_t total_allocated_bytes_ = 0;
    std::size_t retained_bytes_ = 0;
    std::size_t retained_objects_ = 0;
    std::size_t peak_objects_ = 0;
    std::size_t peak_managed_bytes_ = 0;
    bool measure_ = false;
    std::vector<std::uint64_t> pauses_ns_;
    std::atomic<std::size_t> threshold_{4096};
    std::atomic<std::size_t> byte_threshold_{4 * 1024 * 1024};
    bool adaptive_threshold_ = true;
    bool collecting_ = false;
    std::atomic<bool> collection_requested_{false};
    bool closed_ = false;
    ThreadContext* collector_ = nullptr;
    void park(std::unique_lock<::jnative::platform::Mutex>& lock);
    void flush_allocations(ThreadContext* thread);
    std::size_t object_count() const;
    std::size_t managed_bytes() const;
public:
    Heap();
    static Heap& instance();
    ~Heap();
    void attach(ThreadContext* thread);
    void detach(ThreadContext* thread);
    bool try_close();
    void poll();
    bool collection_pending() const {
        return collection_requested_.load(std::memory_order_acquire);
    }
    void poll_if_requested() {
        if (collection_requested_.load(std::memory_order_acquire)) poll();
    }
    void collect();
    void enter_native(ThreadState state);
    void leave_native();
    void adopt(Object* object);
    void before_allocation();
    // Internal allocation path: the factory validated this attached context.
    // Keep context checks and page/queue growth outside the common path.
    JNATIVE_FORCEINLINE void before_allocation(ThreadContext* thread) {
        poll_if_requested();
        const auto threshold = threshold_.load(std::memory_order_relaxed);
        const auto byte_threshold = byte_threshold_.load(std::memory_order_relaxed);
        const auto queued = thread->queued_allocations.load(std::memory_order_relaxed);
        const auto queued_bytes = thread->queued_bytes.load(std::memory_order_relaxed);
        // Before a second attachment, the global prefix and the mutator's
        // partial batch add up to exactly this mutator's published totals.
        if (!shared_allocation_pressure_.load(std::memory_order_acquire)) {
            if (queued >= threshold || queued_bytes >= byte_threshold) collect();
            return;
        }
        const auto reported = allocations_.load(std::memory_order_relaxed);
        const auto reported_bytes = allocation_bytes_.load(std::memory_order_relaxed);
        const auto pending = queued - thread->reported_allocations;
        const auto pending_bytes = queued_bytes - thread->reported_bytes;
        if (reported >= threshold || pending >= threshold - reported
                || reported_bytes >= byte_threshold || pending_bytes >= byte_threshold - reported_bytes)
            collect();
    }
    JNATIVE_FORCEINLINE void adopt(ThreadContext* thread, Object* object) {
        auto bytes = ManagedArena::adopt(object, this);
        if (!bytes) {
            thread->allocation_queue.push_back(object);
            bytes = managed_allocation_size(object);
        }
        account_allocation(thread, bytes);
    }
    JNATIVE_FORCEINLINE void adopt_pooled(ThreadContext* thread, Object* object, std::size_t bytes, bool standard_delete) {
        ManagedArena::adopt_pooled(object, standard_delete);
        account_allocation(thread, bytes);
    }
private:
    JNATIVE_FORCEINLINE void account_allocation(ThreadContext* thread, std::size_t bytes) {
        const auto count = thread->queued_allocations.load(std::memory_order_relaxed) + 1;
        const auto total_bytes = thread->queued_bytes.load(std::memory_order_relaxed) + bytes;
        thread->queued_allocations.store(count, std::memory_order_relaxed);
        thread->queued_bytes.store(total_bytes, std::memory_order_relaxed);
        const auto pending = count - thread->reported_allocations;
        if (pending == 64 || threshold_.load(std::memory_order_relaxed) < 64) {
            allocations_.fetch_add(pending, std::memory_order_relaxed);
            thread->reported_allocations = count;
        }
        const auto pending_bytes = total_bytes - thread->reported_bytes;
        if (pending_bytes >= 65536) {
            allocation_bytes_.fetch_add(pending_bytes, std::memory_order_relaxed);
            thread->reported_bytes = total_bytes;
        }
    }
public:
    std::uint64_t retain(Object* object, bool external = false);
    BorrowedSlot* borrow(Object* object);
    void end_borrow(BorrowedSlot* slot) noexcept;
    std::uint64_t duplicate(std::uint64_t handle, bool external = false);
    Object* resolve(std::uint64_t handle);
    void release(std::uint64_t handle);
    bool release_checked(std::uint64_t handle);
    std::pair<std::size_t, std::size_t> statistics();
    void report_statistics();
    void collection_threshold(std::size_t threshold);
    void add_static_root(ManagedField<Object*>* field);
    void remove_static_root(ManagedField<Object*>* field);
};

[[noreturn]] JNATIVE_NOINLINE void invalid_managed_execution();
[[noreturn]] JNATIVE_NOINLINE void invalid_root_frame();

inline ThreadContext* managed_thread() {
    auto thread = current_thread();
    if (!thread || thread->state != ThreadState::running_managed || !thread->heap)
        invalid_managed_execution();
    return thread;
}
inline Heap& managed_heap() { return *managed_thread()->heap; }

// Generated methods validate their managed context at entry. Their loop checks
// reuse the heap and need the checked slow path only when a collector requests
// cooperation. Generated bounded loops may amortize this check over a small
// fixed number of iterations; other loops check on every iteration.
class LoopSafepoint {
    Heap& heap_;
public:
    LoopSafepoint() : heap_(managed_heap()) { heap_.poll_if_requested(); }
    explicit LoopSafepoint(Heap& heap) : heap_(heap) { heap_.poll_if_requested(); }
    void poll() {
        if (heap_.collection_requested_.load(std::memory_order_acquire)) heap_.poll();
    }
};

// Java arithmetic uses the default IEEE environment even when a foreign caller
// has changed its rounding mode or exception masks. Restore the caller on exit.
class FloatingEnvironment {
    platform::FloatingState previous_;
public:
    FloatingEnvironment();
    ~FloatingEnvironment();
    FloatingEnvironment(const FloatingEnvironment&) = delete;
};

class ThreadAttachment {
    std::unique_ptr<FloatingEnvironment> environment_;
    std::unique_ptr<ThreadContext> context_;
public:
    ThreadAttachment();
    ~ThreadAttachment();
    ThreadAttachment(const ThreadAttachment&) = delete;
    ThreadAttachment& operator=(const ThreadAttachment&) = delete;
};

// During this region no managed object may be accessed directly. Callbacks attach
// or reenter managed execution before accessing the heap.
class NativeRegion {
    bool entered_ = false;
    platform::FloatingState previous_;
public:
    explicit NativeRegion(ThreadState state = ThreadState::in_native);
    ~NativeRegion();
    NativeRegion(const NativeRegion&) = delete;
    NativeRegion& operator=(const NativeRegion&) = delete;
};

// Only for explicitly bounded primitive imports: no blocking, managed ABI or callbacks.
class LeafNativeRegion {
    platform::FloatingState previous_;
public:
    LeafNativeRegion() { platform::save_floating(previous_); }
    ~LeafNativeRegion() { platform::restore_floating(previous_); }
    LeafNativeRegion(const LeafNativeRegion&) = delete;
    LeafNativeRegion& operator=(const LeafNativeRegion&) = delete;
};

// Generated imports begin in Java's fixed floating environment. Java exposes no
// floating status flags, so returning restores that environment directly. The
// surrounding managed entry still preserves the complete native caller state.
class JavaNativeRegion {
    Heap& heap_;
public:
    JavaNativeRegion() : heap_(managed_heap()) { heap_.enter_native(ThreadState::in_native); }
    ~JavaNativeRegion() {
        platform::java_floating();
        heap_.leave_native();
    }
    JavaNativeRegion(const JavaNativeRegion&) = delete;
    JavaNativeRegion& operator=(const JavaNativeRegion&) = delete;
};
class JavaLeafNativeRegion {
public:
    JavaLeafNativeRegion() = default;
    ~JavaLeafNativeRegion() { platform::java_floating(); }
    JavaLeafNativeRegion(const JavaLeafNativeRegion&) = delete;
    JavaLeafNativeRegion& operator=(const JavaLeafNativeRegion&) = delete;
};

// Generated methods publish one frame. Runtime helpers may nest LocalRoot
// registrations independently; the collector traces both chains at safepoints.
template<std::size_t Size> class RootFrame {
    static_assert(Size > 0, "Empty methods do not need a root frame");
    Object* values_[Size]{};
    ThreadContext* thread_;
    RootFrameLink link_;
public:
    RootFrame() : thread_(current_thread()), link_{values_, Size, nullptr} {
        if (!thread_ || thread_->state != ThreadState::running_managed)
            invalid_root_frame();
        link_.previous = thread_->root_frames;
        thread_->root_frames = &link_;
    }
    ~RootFrame() {
        if (thread_->root_frames != &link_) std::terminate();
        thread_->root_frames = link_.previous;
    }
    RootFrame(const RootFrame&) = delete;
    RootFrame& operator=(const RootFrame&) = delete;
    Heap& heap() const { return *thread_->heap; }
    Object*& slot(std::size_t index) { return values_[index]; }
};

// Called only after a collection request was observed. Keeping root publication
// out of line lets a wrapper's ordinary entry remain a small load and branch.
template<class... References>
JNATIVE_NOINLINE void poll_with_roots(Heap& heap, References... references) {
    RootFrame<sizeof...(References)> roots;
    std::size_t index = 0;
    for (Object* reference : {static_cast<Object*>(references)...})
        roots.slot(index++) = reference;
    heap.poll_if_requested();
}

// A proven bounded normal path needs no published roots. Its exceptional
// branches activate this frame before doing any collecting work.
template<std::size_t Size> class DeferredRootFrame {
    static_assert(Size > 0, "Empty methods do not need a root frame");
    Object* values_[Size]{};
    ThreadContext* thread_ = nullptr;
    RootFrameLink link_{values_, Size, nullptr};
public:
    DeferredRootFrame() = default;
    ~DeferredRootFrame() {
        if (thread_) {
            if (thread_->root_frames != &link_) std::terminate();
            thread_->root_frames = link_.previous;
        }
    }
    DeferredRootFrame(const DeferredRootFrame&) = delete;
    DeferredRootFrame& operator=(const DeferredRootFrame&) = delete;
    Object*& slot(std::size_t index) { return values_[index]; }
    void activate() {
        if (thread_) return;
        auto current = current_thread();
        if (!current || current->state != ThreadState::running_managed || !current->heap)
            invalid_root_frame();
        thread_ = current;
        link_.previous = thread_->root_frames;
        thread_->root_frames = &link_;
    }
};

// Plain locals on a proven noncollecting path. A cold branch registers their
// addresses before it can collect, so subsequent assignments stay visible.
template<class T = Object> class RootValue {
    Object* value_;
public:
    explicit RootValue(T* value = nullptr) : value_(value) {}
    T* get() const { return static_cast<T*>(value_); }
    void set(T* value) { value_ = value; }
    Object** address() { return &value_; }
};
template<std::size_t Size> class RootAddressFrame {
    static_assert(Size > 0, "Empty branches do not need a root frame");
    Object** addresses_[Size];
    ThreadContext* thread_;
    RootAddressFrameLink link_;
public:
    explicit RootAddressFrame(std::initializer_list<Object**> addresses)
        : thread_(current_thread()), link_{addresses_, Size, nullptr} {
        if (!thread_ || thread_->state != ThreadState::running_managed || addresses.size() != Size)
            throw std::logic_error("A root address frame needs an attached managed thread and live locals");
        std::size_t index = 0;
        for (Object** address : addresses) addresses_[index++] = address;
        link_.previous = thread_->root_addresses;
        thread_->root_addresses = &link_;
    }
    ~RootAddressFrame() {
        if (thread_->root_addresses != &link_) std::terminate();
        thread_->root_addresses = link_.previous;
    }
    RootAddressFrame(const RootAddressFrame&) = delete;
    RootAddressFrame& operator=(const RootAddressFrame&) = delete;
};

// Built-in drivers can attempt only a compiler-proven bounded callback. A miss
// performs no Java work; the driver must root live references before a full call.
template<class Full, class Bounded> class RuntimeCallback {
    Full full_;
    Bounded bounded_;
public:
    RuntimeCallback(Full full, Bounded bounded) : full_(full), bounded_(bounded) {}
    template<class... Args> auto operator()(Args... args) const -> decltype(full_(args...)) {
        return full_(args...);
    }
    template<class Result, class... Args> bool try_invoke(Result* result, Args... args) const {
        return bounded_(result, args...);
    }
};
template<class Full, class Bounded>
RuntimeCallback<Full, Bounded> make_runtime_callback(Full full, Bounded bounded) {
    return RuntimeCallback<Full, Bounded>(full, bounded);
}

template<class T = Object> class FrameRoot {
    Object*& value_;
public:
    explicit FrameRoot(Object*& slot, T* object = nullptr) : value_(slot) { value_ = object; }
    ~FrameRoot() { value_ = nullptr; }
    FrameRoot(const FrameRoot&) = delete;
    FrameRoot& operator=(const FrameRoot&) = delete;
    T* get() const { return static_cast<T*>(value_); }
    void set(T* object) { value_ = object; }
    T* operator->() const { return get(); }
};

template<class T = Object> class LocalRoot : private RootSlot {
public:
    explicit LocalRoot(T* object = nullptr) {
        auto thread = current_thread();
        if (!thread || thread->state != ThreadState::running_managed)
            throw std::logic_error("A local root needs an attached managed thread");
        value = object;
        previous = thread->roots;
        thread->roots = this;
    }
    ~LocalRoot() {
        auto thread = current_thread();
        if (!thread || thread->roots != this) std::terminate();
        thread->roots = previous;
    }
    LocalRoot(const LocalRoot&) = delete;
    LocalRoot& operator=(const LocalRoot&) = delete;
    T* get() const { return static_cast<T*>(value); }
    void set(T* object) { value = object; }
    T* operator->() const { return get(); }
};

// Import arguments live in the caller's root chain. Stable, generation-checked
// slots let native code use them on any thread for the duration of the call.
// Retaining creates an independent owned token; expired tokens never alias reuse.
class BorrowedHandle {
    LocalRoot<> root_;
    BorrowedSlot* slot_;
    std::uint64_t id_;
public:
    explicit BorrowedHandle(Object* object)
        : root_(object), slot_(object ? managed_heap().borrow(object) : nullptr),
          id_(slot_ ? slot_->token.load(std::memory_order_relaxed) : 0) {}
    ~BorrowedHandle() { if (slot_) current_thread()->heap->end_borrow(slot_); }
    std::uint64_t id() const noexcept { return id_; }
    BorrowedHandle(const BorrowedHandle&) = delete;
    BorrowedHandle& operator=(const BorrowedHandle&) = delete;
};

template<class T = Object> class GlobalHandle {
    std::uint64_t id_ = 0;
public:
    explicit GlobalHandle(T* object = nullptr) : id_(Heap::instance().retain(object)) {}
    GlobalHandle(const GlobalHandle& other) : id_(Heap::instance().duplicate(other.id_)) {}
    GlobalHandle(GlobalHandle&& other) noexcept : id_(::jnative::take_value(other.id_, 0)) {}
    GlobalHandle& operator=(GlobalHandle other) noexcept { std::swap(id_, other.id_); return *this; }
    ~GlobalHandle() { if (id_) Heap::instance().release(id_); }
    T* get() const { return static_cast<T*>(Heap::instance().resolve(id_)); }
    std::uint64_t id() const { return id_; }
};

// Preserve ordinary custom native allocation, including overloads which hide
// Object's arena placement form. Only inherited allocation uses that fast path.
template<class T, class = void> struct StandardManagedNew : std::false_type {};
template<class T> struct StandardManagedNew<T,
        decltype(static_cast<void* (*)(std::size_t)>(&T::operator new),
                static_cast<void* (*)(std::size_t, ManagedArena*)>(&T::operator new), void())>
    : std::integral_constant<bool,
            static_cast<void* (*)(std::size_t)>(&T::operator new)
                    == static_cast<void* (*)(std::size_t)>(&Object::operator new)
            && static_cast<void* (*)(std::size_t, ManagedArena*)>(&T::operator new)
                    == static_cast<void* (*)(std::size_t, ManagedArena*)>(&Object::operator new)> {};

template<class T, class... Args> T* construct_managed(ManagedArena* arena, std::true_type, Args&&... args) {
    return new (arena) T(std::forward<Args>(args)...);
}
template<class T, class... Args> T* construct_managed(ManagedArena*, std::false_type, Args&&... args) {
    return new T(std::forward<Args>(args)...);
}

// Native subclasses can supply their own deallocation, including a sized-only
// overload. Only the inherited ordinary operator permits direct arena release.
template<class T, class = void> struct StandardManagedDelete : std::false_type {};
template<class T> struct StandardManagedDelete<T,
        decltype(static_cast<void (*)(void*)>(&T::operator delete), void())>
    : std::integral_constant<bool, static_cast<void (*)(void*)>(&T::operator delete)
            == static_cast<void (*)(void*)>(&Object::operator delete)> {};

// Reuse one validated context through allocation, construction and publication.
template<class T, class... Args> T* allocate_in(ThreadContext* thread, Args&&... args) {
    auto& heap = *thread->heap;
    heap.before_allocation(thread);
    std::unique_ptr<T> object(construct_managed<T>(thread->arena, StandardManagedNew<T>{},
            std::forward<Args>(args)...));
    // Preserve custom native allocation overloads. For inherited ordinary new,
    // the size class is a compile-time constant and the attached arena owns it.
    if (ManagedArena::is_small_size(sizeof(T)) && thread->arena
            && StandardManagedNew<T>::value)
        heap.adopt_pooled(thread, object.get(), ManagedArena::small_allocation_size(sizeof(T)),
                StandardManagedDelete<T>::value);
    else heap.adopt(thread, object.get());
    return object.release();
}
template<class T, class... Args> T* allocate(Args&&... args) {
    return allocate_in<T>(managed_thread(), std::forward<Args>(args)...);
}
inline void safepoint() { managed_heap().poll_if_requested(); }

class StaticReference : public ManagedField<Object*> {
public:
    StaticReference() { Heap::instance().add_static_root(this); }
    ~StaticReference() { Heap::instance().remove_static_root(this); }
};

struct Throwable : Object {
    std::string name;
    ManagedField<Object*> message;
    ManagedField<Object*> cause;
    std::vector<Object*> suppressed;
    std::vector<StackLocation> stack;
#if JNATIVE_NATIVE_TRACES
    NativeTrace native_stack;
    const char* native_capture_site="construction";
#endif
    explicit Throwable(std::string type = "java/lang/Throwable");
    const char* type_name() const override { return name.c_str(); }
    void trace(Tracer& tracer) override {
        tracer.visit(message.get());
        tracer.visit(cause.get());
        for (Object* value : suppressed) tracer.visit(value);
    }
};
class Thrown : public std::exception {
    GlobalHandle<> object_;
    std::string description_;
public:
    explicit Thrown(Object* object);
    Object* object() const { return object_.get(); }
    const char* what() const noexcept override { return description_.c_str(); }
};
[[noreturn]] void raise(const char* type, const char* message = nullptr);
[[noreturn]] void raise_with_message(const char* type, Object* message);
[[noreturn]] void raise_native(const std::exception& error);

// Bounded scalar imports keep their exception boundary while exposing the
// native expression to the caller's optimizer. They cannot retain managed roots.
template<class Call, class... Args>
JNATIVE_FORCEINLINE auto invoke_bounded_native(Call&& call, Args... args) -> decltype(call(args...)) {
    try {
        return call(args...);
    } catch (const Thrown&) { throw; }
    catch (const std::bad_alloc&) { raise("java/lang/OutOfMemoryError"); }
    catch (const std::exception& error) { raise_native(error); }
}
[[noreturn]] void throw_object(Object* object);
void print_stack_trace(Object* object, Object* stream = nullptr);
void add_suppressed(Object* object, Object* suppressed);
Object* get_suppressed(Object* object);
Object* system_property(Object* key, Object* fallback);
Object* set_system_property(Object* key, Object* value, bool clear);
int unicode_info(int code_point);
int unicode_case(int code_point, bool upper);
Object* regex_compile(Object* pattern, int flags);
Object* regex_find(Object* pattern, Object* text, int offset, bool whole);
float parse_float(Object* text);
double parse_double(Object* text);
Object* format_decimal(double value, int precision, int conversion);
Object* zlib_open(bool compress, int level, bool raw);
void zlib_input(Object* state, Object* bytes, int offset, int length);
int zlib_process(Object* state, Object* bytes, int offset, int length, bool finish);
int zlib_status(Object* state, int kind);
void zlib_close(Object* state);
void register_type(const std::string& name, const std::string& parent, std::vector<std::string> interfaces,
    std::uint32_t generated_id = 0);
std::uint32_t generated_type_id(const std::string& name);
bool assignable(const std::string& actual, const std::string& expected);
bool instance_of(Object* object, const char* expected);
Object* check_cast(Object* object, const char* expected);
[[noreturn]] JNATIVE_NOINLINE void fail_cast(Object* object, const char* expected);

// Only emitted for final, generated Java classes. Runtime-owned classes and
// arrays cannot be instances of these classes; subclasses are forbidden by Java.
inline bool instance_of_exact(Object* object, const char*, std::uint32_t expected_id) {
    return object && object->type_id() == expected_id;
}
inline Object* check_cast_exact(Object* object, const char* expected, std::uint32_t expected_id) {
    if (object && object->type_id() != expected_id) fail_cast(object, expected);
    return object;
}

// Immutable whole-program ancestry, installed before Java execution begins.
// Runtime-owned objects and arrays retain the name-based Java type rules.
struct TypeCheckTable {
    const std::uint64_t* bits;
    std::size_t classes;
    std::size_t stride;
};
// Installed once by the generated program before application initialization.
// Raw string kernels remain available to saved OriginalMethod implementations.
struct RuntimeSubstitutions {
    std::int32_t (*string_hash)(Object*);
    std::uint8_t (*string_equals)(Object*, Object*);
    Object* (*string_text)(Object*);
    Object* (*exception_factory)(const char*, Object*);
    bool file_input_fast_path;
    bool crc32_fast_path;
    RuntimeSubstitutions() : string_hash(nullptr), string_equals(nullptr), string_text(nullptr),
        exception_factory(nullptr), file_input_fast_path(true), crc32_fast_path(true) {}
};
void configure_runtime_substitutions(const RuntimeSubstitutions& policy);
const RuntimeSubstitutions& runtime_substitutions();
extern TypeCheckTable generated_type_checks;
void register_type_checks(const std::uint64_t* bits, std::size_t classes, std::size_t stride);
inline bool generated_assignable(std::uint32_t actual, std::uint32_t expected) {
    const auto& table = generated_type_checks;
    return actual && actual <= table.classes && expected && expected <= table.classes
        && (table.bits[actual * table.stride + expected / 64]
            & (std::uint64_t(1) << (expected % 64))) != 0;
}
inline bool instance_of(Object* object, const char* expected, std::uint32_t expected_id) {
    if (!object) return false;
    const std::uint32_t actual = object->type_id();
    const auto& table = generated_type_checks;
    if (actual && actual <= table.classes && expected_id && expected_id <= table.classes)
        return actual == expected_id || generated_assignable(actual, expected_id);
    return instance_of(object, expected);
}
inline Object* check_cast(Object* object, const char* expected, std::uint32_t expected_id) {
    if (!object || instance_of(object, expected, expected_id)) return object;
    return check_cast(object, expected);
}

class ClassInitialization {
    ::jnative::platform::Mutex mutex_;
    ::jnative::platform::Condition changed_;
    std::atomic<int> state_{0};
    std::uint64_t owner_ = 0;
    GlobalHandle<> failure_;
    void run_slow(const std::function<void()>& initializer);
public:
    bool completed() const { return state_.load(std::memory_order_acquire) == 2; }
    template<class F> void run(const F& initializer) {
        // Most calls arrive after initialization. Do not construct a type-erased
        // callback or enter the synchronization path for those calls.
        const int state = state_.load(std::memory_order_acquire);
        if (state == 2) return;
        // Same-class leaf calls can occur inside <clinit>. Their recursive
        // initialization check must not park or collect. owner_ is assigned
        // once, before publishing state 1, and never changes afterwards.
        if (state == 1 && current_thread() && owner_ == current_thread()->identity) return;
        run_slow(initializer);
    }
};

// Bit-preserving conversion avoids implementation-defined out-of-range signed casts.
inline std::int32_t signed32(std::uint32_t x) {
    return x <= INT32_MAX ? static_cast<std::int32_t>(x) : -1 - static_cast<std::int32_t>(~x);
}
inline std::int64_t signed64(std::uint64_t x) {
    return x <= INT64_MAX ? static_cast<std::int64_t>(x) : -1 - static_cast<std::int64_t>(~x);
}
inline std::int32_t add(std::int32_t a, std::int32_t b) { return signed32(std::uint32_t(a) + std::uint32_t(b)); }
inline std::int64_t add(std::int64_t a, std::int64_t b) { return signed64(std::uint64_t(a) + std::uint64_t(b)); }
inline std::int32_t sub(std::int32_t a, std::int32_t b) { return signed32(std::uint32_t(a) - std::uint32_t(b)); }
inline std::int64_t sub(std::int64_t a, std::int64_t b) { return signed64(std::uint64_t(a) - std::uint64_t(b)); }
inline std::int32_t mul(std::int32_t a, std::int32_t b) { return signed32(std::uint32_t(a) * std::uint32_t(b)); }
inline std::int64_t mul(std::int64_t a, std::int64_t b) { return signed64(std::uint64_t(a) * std::uint64_t(b)); }

template<class T> T divide(T a, T b) {
    if (!b) raise("java/lang/ArithmeticException", "/ by zero");
    if (a == std::numeric_limits<T>::min() && b == -1) return a;
    return a / b;
}
template<class T> T remainder(T a, T b) {
    if (!b) raise("java/lang/ArithmeticException", "/ by zero");
    if (a == std::numeric_limits<T>::min() && b == -1) return 0;
    return a % b;
}
template<class T> T signed_value(typename std::make_unsigned<T>::type value) {
    return sizeof(T) == 4 ? static_cast<T>(signed32(static_cast<std::uint32_t>(value))) : static_cast<T>(signed64(value));
}
template<class T> T shift_left(T a, std::int32_t b) {
    using U = typename std::make_unsigned<T>::type;
    return signed_value<T>(U(a) << (std::uint32_t(b) & (sizeof(T) * 8 - 1)));
}
template<class T> T unsigned_shift(T a, std::int32_t b) {
    using U = typename std::make_unsigned<T>::type;
    return signed_value<T>(U(a) >> (std::uint32_t(b) & (sizeof(T) * 8 - 1)));
}
template<class T> T shift_right(T a, std::int32_t b) {
    using U = typename std::make_unsigned<T>::type;
    unsigned n = std::uint32_t(b) & (sizeof(T) * 8 - 1);
    if (!n) return a;
    U shifted = U(a) >> n;
    if (a < 0) shifted |= (~U(0)) << (sizeof(T) * 8 - n);
    return signed_value<T>(shifted);
}
template<class T, class F> T float_to_integer(F value) {
    if (value != value) return 0;
    if (value >= static_cast<F>(std::numeric_limits<T>::max())) return std::numeric_limits<T>::max();
    if (value <= static_cast<F>(std::numeric_limits<T>::min())) return std::numeric_limits<T>::min();
    return static_cast<T>(value);
}

#if !defined(JNATIVE_PORTABLE_FLOAT_TO_INTEGER) && (defined(__x86_64__) || defined(_M_X64))
// CVTT returns the signed minimum for NaN, overflow and a valid minimum.
// Java execution masks floating exceptions; its status flags are not Java values.
// The surrounding managed entry restores the native caller's entire environment.
template<class T, class F> JNATIVE_NOINLINE T float_to_integer_sentinel(F value) {
    if (value != value) return 0;
    return value > 0 ? std::numeric_limits<T>::max() : std::numeric_limits<T>::min();
}
template<> inline std::int32_t float_to_integer<std::int32_t, float>(float value) {
    const std::int32_t result = _mm_cvttss_si32(_mm_set_ss(value));
    return result != std::numeric_limits<std::int32_t>::min()
        ? result : float_to_integer_sentinel<std::int32_t>(value);
}
template<> inline std::int32_t float_to_integer<std::int32_t, double>(double value) {
    const std::int32_t result = _mm_cvttsd_si32(_mm_set_sd(value));
    return result != std::numeric_limits<std::int32_t>::min()
        ? result : float_to_integer_sentinel<std::int32_t>(value);
}
template<> inline std::int64_t float_to_integer<std::int64_t, float>(float value) {
    const std::int64_t result = _mm_cvttss_si64(_mm_set_ss(value));
    return result != std::numeric_limits<std::int64_t>::min()
        ? result : float_to_integer_sentinel<std::int64_t>(value);
}
template<> inline std::int64_t float_to_integer<std::int64_t, double>(double value) {
    const std::int64_t result = _mm_cvttsd_si64(_mm_set_sd(value));
    return result != std::numeric_limits<std::int64_t>::min()
        ? result : float_to_integer_sentinel<std::int64_t>(value);
}
#endif

template<class T> T math_min(T a, T b) { return a <= b ? a : b; }
template<class T> T math_max(T a, T b) { return a >= b ? a : b; }
inline bool floating_sign(float value) {
    std::uint32_t bits;
    std::memcpy(&bits, &value, sizeof(bits));
    return (bits >> 31) != 0;
}
inline bool floating_sign(double value) {
    std::uint64_t bits;
    std::memcpy(&bits, &value, sizeof(bits));
    return (bits >> 63) != 0;
}
inline std::uint32_t floating_magnitude(float value) {
    std::uint32_t bits;
    std::memcpy(&bits, &value, sizeof(bits));
    return bits & UINT32_C(0x7fffffff);
}
inline std::uint64_t floating_magnitude(double value) {
    std::uint64_t bits;
    std::memcpy(&bits, &value, sizeof(bits));
    return bits & UINT64_C(0x7fffffffffffffff);
}
template<class T> bool floating_is_finite(T value) {
    return floating_magnitude(value) < (sizeof(T) == 4 ? UINT64_C(0x7f800000) : UINT64_C(0x7ff0000000000000));
}
template<class T> bool floating_is_infinite(T value) {
    return floating_magnitude(value) == (sizeof(T) == 4 ? UINT64_C(0x7f800000) : UINT64_C(0x7ff0000000000000));
}
template<class T> bool floating_is_nan(T value) {
    return floating_magnitude(value) > (sizeof(T) == 4 ? UINT64_C(0x7f800000) : UINT64_C(0x7ff0000000000000));
}
template<class T> T floating_min(T a, T b) {
    if (a != a) return a;
    // Resolve both zero operands before the ordered comparison. A native
    // min/max instruction can select a different operand when zeros compare equal.
    if (a == 0 && b == 0) return floating_sign(a) ? a : b;
    return a <= b ? a : b;
}
template<class T> T floating_max(T a, T b) {
    if (a != a) return a;
    if (a == 0 && b == 0) return floating_sign(a) ? b : a;
    return a >= b ? a : b;
}
inline float math_min(float a, float b) { return floating_min(a, b); }
inline double math_min(double a, double b) { return floating_min(a, b); }
inline float math_max(float a, float b) { return floating_max(a, b); }
inline double math_max(double a, double b) { return floating_max(a, b); }
template<class I, class U, unsigned FractionBits, unsigned Bias, class F>
I round_binary(F value) {
    U bits;
    std::memcpy(&bits, &value, sizeof(bits));
    const unsigned exponent = static_cast<unsigned>((bits >> FractionBits) & (2 * Bias + 1));
    if (exponent < Bias - 1) return 0; // Magnitude below one half, including subnormals.
    if (exponent >= Bias + FractionBits) return float_to_integer<I>(value);
    const U negative = bits >> (sizeof(U) * 8 - 1);
    const U significand = (bits & ((U(1) << FractionBits) - 1)) | (U(1) << FractionBits);
    const unsigned shift = Bias + FractionBits - exponent;
    // Round the unsigned significand, resolving an exact half toward positive infinity.
    // This range has 1..FractionBits+1 discarded bits and cannot overflow the integer result.
    const I rounded = static_cast<I>((significand + (U(1) << (shift - 1)) - negative) >> shift);
    return negative ? -rounded : rounded;
}
inline std::int32_t math_round(float value) {
    return round_binary<std::int32_t, std::uint32_t, 23, 127>(value);
}
inline std::int64_t math_round(double value) {
    return round_binary<std::int64_t, std::uint64_t, 52, 1023>(value);
}
struct String final : Object {
    std::u16string value;
    // String contents are immutable after the Java constructor completes. A zero
    // hash is recomputed; nonzero cached values are safe even with concurrent readers.
    OrdinaryField<std::int32_t> cached_hash;
    explicit String(std::u16string text) : Object(0, RuntimeKind::string), value(std::move(text)) {}
    const char* type_name() const override { return "java/lang/String"; }
};
struct PrintStream final : Object {
    std::ostream* stream;
    explicit PrintStream(std::ostream* output) : stream(output) {}
    const char* type_name() const override { return "java/io/PrintStream"; }
};
inline Object* require_non_null(Object* object) {
    if (!object) raise("java/lang/NullPointerException");
    return object;
}
String* literal(const std::u16string& text);
String* literal(const char16_t* text);
// Each generated slot has one immutable spelling, resolved during program
// initialization before Java entry. The interner owns the GC root; subsequent
// loads avoid locking, hashing, text comparison and managed allocation. The
// initializer also tolerates racing first resolution without a static guard.
struct StringLiteral {
    std::atomic<String*> cached{nullptr};
    template<class Text> String* get(const Text& text) {
        String* value = cached.load(std::memory_order_acquire);
        if (!value) {
            value = literal(text);
            cached.store(value, std::memory_order_release);
        }
        return value;
    }
};
// Generated class constants are resolved before Java entry, without running
// the represented class initializer. The class interner owns their GC roots.
struct ClassLiteral {
    std::atomic<Object*> cached{nullptr};
    Object* get(const char* name) {
        Object* value = cached.load(std::memory_order_acquire);
        return value ? value : resolve(name);
    }
private:
    Object* resolve(const char* name);
};
PrintStream* standard_out();
PrintStream* standard_error();
std::string utf8(const std::u16string& text);
// Only use with native-owned storage or a managed String rooted across polls.
std::string utf8_cooperative(const std::u16string& text);
std::string character(std::int32_t code);
void print(Object* stream, Object* value, bool newline);
void flush(Object* stream);
void print(Object* stream, const std::string& value, bool newline);
template<class T> void print(Object* stream, T value, bool newline) {
    std::ostringstream formatted;
    formatted << value;
    print(stream, formatted.str(), newline);
}

struct Array : Object {
    const std::int32_t length;
    Array(std::int32_t size, char kind) : Object(kind), length(size) {}
    // Read descriptors through type_name(): primitive arrays use fixed literals;
    // reference arrays own their dynamic descriptor for the object's lifetime.
    const char* type_name() const override = 0;
    void bounds(std::int32_t index) const {
        // Array lengths are nonnegative; one unsigned comparison checks both ends.
        if (static_cast<std::uint32_t>(index) >= static_cast<std::uint32_t>(length))
            raise("java/lang/ArrayIndexOutOfBoundsException");
    }
};
template<class T> struct PrimitiveArrayKind;
template<> struct PrimitiveArrayKind<std::uint8_t> {
    static constexpr char value = 'Z';
    static const char* descriptor() { return "[Z"; }
};
template<> struct PrimitiveArrayKind<std::int8_t> {
    static constexpr char value = 'B';
    static const char* descriptor() { return "[B"; }
};
template<> struct PrimitiveArrayKind<std::uint16_t> {
    static constexpr char value = 'C';
    static const char* descriptor() { return "[C"; }
};
template<> struct PrimitiveArrayKind<std::int16_t> {
    static constexpr char value = 'S';
    static const char* descriptor() { return "[S"; }
};
template<> struct PrimitiveArrayKind<std::int32_t> {
    static constexpr char value = 'I';
    static const char* descriptor() { return "[I"; }
};
template<> struct PrimitiveArrayKind<std::int64_t> {
    static constexpr char value = 'J';
    static const char* descriptor() { return "[J"; }
};
template<> struct PrimitiveArrayKind<float> {
    static constexpr char value = 'F';
    static const char* descriptor() { return "[F"; }
};
template<> struct PrimitiveArrayKind<double> {
    static constexpr char value = 'D';
    static const char* descriptor() { return "[D"; }
};
// The enclosing array owns this storage; consumers retain the same get/index API.
template<class T> class ArrayElements {
    OrdinaryField<T>* const data_;
public:
    explicit ArrayElements(OrdinaryField<T>* data) : data_(data) {}
    OrdinaryField<T>* get() const { return data_; }
    OrdinaryField<T>& operator[](std::size_t index) const { return data_[index]; }
};
template<class T> struct PrimitiveArray final : Array {
    using Field = OrdinaryField<T>;
    ArrayElements<T> elements;
    PrimitiveArray(const PrimitiveArray&) = delete;
    PrimitiveArray& operator=(const PrimitiveArray&) = delete;
    PrimitiveArray(PrimitiveArray&&) = delete;
    PrimitiveArray& operator=(PrimitiveArray&&) = delete;
    static constexpr std::size_t payload_offset() {
        return (sizeof(PrimitiveArray) + alignof(Field) - 1) / alignof(Field) * alignof(Field);
    }
    // Recover the live trailing array created by construct_payload without
    // loading the compatibility elements pointer. Empty payloads are not read.
    Field* data() noexcept {
        void* tail = reinterpret_cast<unsigned char*>(this) + payload_offset();
        return static_cast<Field*>(tail);
    }
    const Field* data() const noexcept {
        const void* tail = reinterpret_cast<const unsigned char*>(this) + payload_offset();
        return static_cast<const Field*>(tail);
    }
    static std::size_t allocation_size(std::size_t count) {
        static_assert(alignof(PrimitiveArray) <= alignof(std::max_align_t), "Array needs fundamental alignment");
        static_assert(alignof(Field) <= alignof(std::max_align_t), "Payload needs fundamental alignment");
        static_assert(sizeof(PrimitiveArray) <= std::numeric_limits<std::size_t>::max() - (alignof(Field) - 1),
            "Header alignment overflow");
        if (count > static_cast<std::size_t>(INT32_MAX)
                || count > (std::numeric_limits<std::size_t>::max() - payload_offset()) / sizeof(Field))
            throw std::bad_array_new_length();
        return payload_offset() + count * sizeof(Field);
    }
    static PrimitiveArray* create(std::int32_t count, const char*) {
        if (count < 0) raise("java/lang/NegativeArraySizeException");
        auto thread = managed_thread();
        auto& heap = *thread->heap;
        heap.before_allocation(thread);
        std::unique_ptr<PrimitiveArray> object(new (count, thread->arena) PrimitiveArray(count));
        heap.adopt(thread, object.get());
        return object.release();
    }
    static void operator delete(void* block) noexcept { managed_deallocate(block); }
    const char* type_name() const override { return PrimitiveArrayKind<T>::descriptor(); }
    ~PrimitiveArray() override {
        for (std::int32_t i = length; i > 0; --i) elements[i - 1].~Field();
    }
private:
    static void* operator new(std::size_t bytes, std::int32_t count, ManagedArena* arena) {
        if (bytes != sizeof(PrimitiveArray) || count < 0) throw std::bad_array_new_length();
        return managed_allocate_dynamic(allocation_size(static_cast<std::size_t>(count)), arena);
    }
    static void operator delete(void* block, std::int32_t, ManagedArena*) noexcept { managed_deallocate(block); }
    static Field* construct_payload(PrimitiveArray* shell, std::int32_t count) {
        static_assert(std::is_nothrow_default_constructible<Field>::value, "Primitive initialization must not throw");
        void* tail = reinterpret_cast<unsigned char*>(shell) + payload_offset();
        // Default construction initializes each Field's atomic value_{} to Java
        // zero. Value-initializing the array as well makes MSVC clear it twice.
        // Begin a real array lifetime without the redundant aggregate zeroing.
        // Standard nonallocating placement array new has no cookie (CWG2382 DR).
        return ::new (tail) Field[static_cast<std::size_t>(count)];
    }
    explicit PrimitiveArray(std::int32_t count)
        : Array(count, PrimitiveArrayKind<T>::value), elements(construct_payload(this, count)) {}
};
// Selected only for fresh arrays proved to remain inside a method or an
// entry-thread-owned object, with no untracked aliases.
// A distinct tag selects plain access only where the compiler proves confinement.
template<class T> struct ConfinedPrimitiveArray final : Array {
    static constexpr char kind = PrimitiveArrayKind<T>::value + ('a' - 'A');
    class Elements {
        T* const data_;
    public:
        explicit Elements(T* data) : data_(data) {}
        T* get() const { return data_; }
        T& operator[](std::size_t index) const { return data_[index]; }
    };
    Elements elements;
    ConfinedPrimitiveArray(const ConfinedPrimitiveArray&) = delete;
    ConfinedPrimitiveArray& operator=(const ConfinedPrimitiveArray&) = delete;
    static constexpr std::size_t payload_offset() {
        return (sizeof(ConfinedPrimitiveArray) + alignof(T) - 1) / alignof(T) * alignof(T);
    }
    static std::size_t allocation_size(std::size_t count) {
        static_assert(alignof(ConfinedPrimitiveArray) <= alignof(std::max_align_t), "Array needs fundamental alignment");
        static_assert(alignof(T) <= alignof(std::max_align_t), "Payload needs fundamental alignment");
        static_assert(std::is_trivially_destructible<T>::value, "Confined payload must be primitive");
        if (count > static_cast<std::size_t>(INT32_MAX)
                || count > (std::numeric_limits<std::size_t>::max() - payload_offset()) / sizeof(T))
            throw std::bad_array_new_length();
        return payload_offset() + count * sizeof(T);
    }
    static ConfinedPrimitiveArray* create(std::int32_t count, const char*) {
        if (count < 0) raise("java/lang/NegativeArraySizeException");
        auto thread = managed_thread();
        auto& heap = *thread->heap;
        heap.before_allocation(thread);
        std::unique_ptr<ConfinedPrimitiveArray> object(new (count, thread->arena) ConfinedPrimitiveArray(count));
        heap.adopt(thread, object.get());
        return object.release();
    }
    static void operator delete(void* block) noexcept { managed_deallocate(block); }
    const char* type_name() const override { return PrimitiveArrayKind<T>::descriptor(); }
private:
    static void* operator new(std::size_t bytes, std::int32_t count, ManagedArena* arena) {
        if (bytes != sizeof(ConfinedPrimitiveArray) || count < 0) throw std::bad_array_new_length();
        return managed_allocate_dynamic(allocation_size(static_cast<std::size_t>(count)), arena);
    }
    static void operator delete(void* block, std::int32_t, ManagedArena*) noexcept { managed_deallocate(block); }
    static T* construct_payload(ConfinedPrimitiveArray* shell, std::int32_t count) {
        void* tail = reinterpret_cast<unsigned char*>(shell) + payload_offset();
        return ::new (tail) T[static_cast<std::size_t>(count)]();
    }
    explicit ConfinedPrimitiveArray(std::int32_t count)
        : Array(count, kind), elements(construct_payload(this, count)) {}
};
template<class T> Object* new_confined_array(const char* descriptor, std::int32_t length) {
    return ConfinedPrimitiveArray<T>::create(length, descriptor);
}
template<class T> bool borrowed_confined_array(Object* object) {
    // Byte/boolean layouts remain excluded from compiler confinement and the
    // external byte-handle ABI; do not silently broaden that contract.
    return PrimitiveArrayKind<T>::value != 'B' && PrimitiveArrayKind<T>::value != 'Z'
            && object && object->array_kind == ConfinedPrimitiveArray<T>::kind;
}
inline char java_array_kind(char kind) {
    switch (kind) {
        case 'c': case 's': case 'i': case 'j': case 'f': case 'd':
            return kind - ('a' - 'A');
        default: return kind;
    }
}
inline std::int32_t array_length(Object* object);
template<class T> class ConfinedArrayView {
    Object* const object_;
    T* const elements_;
    const std::uint32_t length_;
    static ConfinedPrimitiveArray<T>* valid(Object* object) {
        return object && object->array_kind == ConfinedPrimitiveArray<T>::kind
            ? static_cast<ConfinedPrimitiveArray<T>*>(object) : nullptr;
    }
    ConfinedArrayView(Object* object, ConfinedPrimitiveArray<T>* array)
        : object_(object), elements_(array ? array->elements.get() : nullptr),
          length_(array ? static_cast<std::uint32_t>(array->length) : 0) {}
    [[noreturn]] void fail() const {
        if (require_non_null(object_)->array_kind != ConfinedPrimitiveArray<T>::kind)
            throw std::logic_error("Invalid confined array access");
        raise("java/lang/ArrayIndexOutOfBoundsException");
    }
public:
    explicit ConfinedArrayView(Object* object) : ConfinedArrayView(object, valid(object)) {}
    Object* object() const { return object_; }
    std::int32_t length() const {
        return elements_ ? static_cast<std::int32_t>(length_) : array_length(object_);
    }
    bool covers(std::int64_t first, std::int64_t last) const {
        return first >= 0 && last >= 0 && first < length_ && last < length_;
    }
    // The generated fast path proves the entire index range before using these.
    T get_unchecked(std::int32_t index) const {
        // Copy the object representation so strict-FP compilers can forward
        // floating bits into byte transfers without a stack spill/reload.
        T value;
        if (std::is_floating_point<T>::value) {
            using Bits = typename std::conditional<sizeof(T) == 4, std::uint32_t, std::uint64_t>::type;
            Bits bits;
            std::memcpy(&bits, elements_ + index, sizeof(T));
            std::memcpy(&value, &bits, sizeof(T));
        } else value = elements_[index];
        return value;
    }
    void set_unchecked(std::int32_t index, T value) const { elements_[index] = value; }
    T get(std::int32_t index) const {
        if (static_cast<std::uint32_t>(index) >= length_) fail();
        return get_unchecked(index);
    }
    void set(std::int32_t index, T value) const {
        if (static_cast<std::uint32_t>(index) >= length_) fail();
        elements_[index] = value;
    }
};
template<class T> T confined_array_get(Object* object, std::int32_t index) {
    return ConfinedArrayView<T>(object).get(index);
}
template<class T> void confined_array_set(Object* object, std::int32_t index, T value) {
    ConfinedArrayView<T>(object).set(index, value);
}
struct ReferenceArray final : Array {
    using Field = OrdinaryField<Object*>;
    // The shell, atomic references and owned descriptor share one allocation.
    const char* const descriptor;
    ArrayElements<Object*> elements;
    const std::uint32_t component_class_id;
    const bool accepts_any_reference;
    ReferenceArray(const ReferenceArray&) = delete;
    ReferenceArray& operator=(const ReferenceArray&) = delete;
    static constexpr std::size_t payload_offset() {
        return (sizeof(ReferenceArray) + alignof(Field) - 1) / alignof(Field) * alignof(Field);
    }
    Field* data() noexcept {
        return static_cast<Field*>(static_cast<void*>(reinterpret_cast<unsigned char*>(this) + payload_offset()));
    }
    static std::size_t allocation_size(std::size_t count, std::size_t name_size);
    static ReferenceArray* create(std::int32_t count, const char* type);
    // Generated allocations already know the component ID. Runtime-owned and
    // array components use zero and retain the name-based covariance fallback.
    static ReferenceArray* create(std::int32_t count, const char* type, std::uint32_t component_id);
    static void operator delete(void* block) noexcept { managed_deallocate(block); }
    const char* type_name() const override { return descriptor; }
    void trace(Tracer& tracer) override;
    ~ReferenceArray() override;
private:
    ReferenceArray(std::int32_t count, const char* type, std::size_t name_size, std::uint32_t component_id);
    static void* operator new(std::size_t bytes, std::int32_t count, std::size_t name_size, ManagedArena* arena);
    static void operator delete(void* block, std::int32_t, std::size_t, ManagedArena*) noexcept { managed_deallocate(block); }
    static char* construct_descriptor(ReferenceArray* shell, std::int32_t count, const char* type, std::size_t size);
    static Field* construct_payload(ReferenceArray* shell, std::int32_t count);
};
Object* new_array(const char* descriptor, std::int32_t length);
Object* multi_array(const char* descriptor, const std::vector<std::int32_t>& dimensions, std::size_t depth = 0);
[[noreturn]] JNATIVE_NOINLINE void invalid_array_operation(const char* message);
inline std::int32_t array_length(Object* object) {
    if (!require_non_null(object)->array_kind) invalid_array_operation("Invalid arraylength bytecode");
    return static_cast<Array*>(object)->length;
}
void array_copy(Object* source, std::int32_t source_offset, Object* target, std::int32_t target_offset, std::int32_t count);
namespace detail {
// Ranges and Java kinds are validated by the caller. At least one payload is
// confined; mixed representations cannot alias. Select storage outside the loop.
template<class T, std::int32_t FixedCount = -1> inline void copy_confined_primitive(Array* source, std::int32_t from,
        Array* target, std::int32_t to, std::int32_t count) {
    if (!count) return;
    if (source->array_kind == ConfinedPrimitiveArray<T>::kind) {
        auto input = static_cast<ConfinedPrimitiveArray<T>*>(source)->elements.get() + from;
        if (target->array_kind == ConfinedPrimitiveArray<T>::kind) {
            auto output = static_cast<ConfinedPrimitiveArray<T>*>(target)->elements.get() + to;
            // Distinct confined arrays own disjoint payloads. A constant byte
            // count lets the compiler inline the small transfer.
            constexpr std::size_t fixed_bytes = FixedCount > 0
                ? static_cast<std::size_t>(FixedCount) * sizeof(T) : 0;
            if (fixed_bytes && source != target) std::memcpy(output, input, fixed_bytes);
            else std::memmove(output, input, static_cast<std::size_t>(count) * sizeof(T));
        } else {
            auto output = static_cast<PrimitiveArray<T>*>(target)->data() + to;
            for (std::int32_t i = 0; i < count; ++i) output[i].set(input[i]);
        }
    } else {
        auto input = static_cast<PrimitiveArray<T>*>(source)->data() + from;
        auto output = static_cast<ConfinedPrimitiveArray<T>*>(target)->elements.get() + to;
        for (std::int32_t i = 0; i < count; ++i) output[i] = input[i].get();
    }
}
template<class T, std::int32_t FixedCount> JNATIVE_FORCEINLINE bool primitive_array_copy_storage_impl(Object* source, std::int32_t from,
        Object* target, std::int32_t to, std::int32_t count,
        OrdinaryField<T>*& input_elements, OrdinaryField<T>*& output_elements) {
    const char source_kind = require_non_null(source)->array_kind;
    const char target_kind = require_non_null(target)->array_kind;
    if (source_kind != PrimitiveArrayKind<T>::value || target_kind != PrimitiveArrayKind<T>::value) {
        // The template fixes the Java kind. Avoid general kind-normalization
        // switches on the mixed path; byte/boolean confinement is unsupported.
        if (PrimitiveArrayKind<T>::value != 'B' && PrimitiveArrayKind<T>::value != 'Z'
                && (source_kind == PrimitiveArrayKind<T>::value
                    || source_kind == ConfinedPrimitiveArray<T>::kind)
                && (target_kind == PrimitiveArrayKind<T>::value
                    || target_kind == ConfinedPrimitiveArray<T>::kind)) {
            auto input = static_cast<Array*>(source);
            auto output = static_cast<Array*>(target);
            if (from < 0 || to < 0 || count < 0 || from > input->length - count || to > output->length - count)
                raise("java/lang/ArrayIndexOutOfBoundsException");
            copy_confined_primitive<T, FixedCount>(input, from, output, to, count);
            return false;
        }
        array_copy(source, from, target, to, count);
        return false;
    }
    auto* input = static_cast<PrimitiveArray<T>*>(source);
    auto* output = static_cast<PrimitiveArray<T>*>(target);
    if (from < 0 || to < 0 || count < 0 || from > input->length - count || to > output->length - count)
        raise("java/lang/ArrayIndexOutOfBoundsException");
    input_elements = input->data();
    output_elements = output->data();
    return true;
}
template<class T> inline bool primitive_array_copy_storage(Object* source, std::int32_t from,
        Object* target, std::int32_t to, std::int32_t count,
        OrdinaryField<T>*& input_elements, OrdinaryField<T>*& output_elements) {
    return primitive_array_copy_storage_impl<T, -1>(source, from, target, to, count, input_elements, output_elements);
}
template<std::int32_t... Indices> struct PrimitiveArrayCopyIndices {};
template<std::int32_t Count, std::int32_t... Indices> struct MakePrimitiveArrayCopyIndices
    : MakePrimitiveArrayCopyIndices<Count - 1, Count - 1, Indices...> {};
template<std::int32_t... Indices> struct MakePrimitiveArrayCopyIndices<0, Indices...> {
    using type = PrimitiveArrayCopyIndices<Indices...>;
};
template<class T> T copy_element(const T& value) { return value; }
template<class T> T copy_element(const OrdinaryField<T>& value) { return value.get(); }
template<class T> void copy_element(T& output, T value) { output = value; }
template<class T> void copy_element(OrdinaryField<T>& output, T value) { output.set(value); }
// Braced initializer clauses are sequenced left to right in C++11. Each clause
// completes one atomic load/store pair; expansion needs no recursive calls.
template<class T, std::int32_t Count> struct PrimitiveArrayCopyExpansion {
    template<class Input, class Output, std::int32_t... Indices> static void forward(Input* input, Output* output,
            PrimitiveArrayCopyIndices<Indices...>) {
        (void)std::initializer_list<int>{(copy_element(output[Indices], copy_element(input[Indices])), 0)...};
    }
    template<class Input, class Output, std::int32_t... Indices> static void backward(Input* input, Output* output,
            PrimitiveArrayCopyIndices<Indices...>) {
        (void)std::initializer_list<int>{(copy_element(output[Count - Indices - 1], copy_element(input[Count - Indices - 1])), 0)...};
    }
};

}
// Generated callers prove 0 <= count <= 64 and retain both array roots.
// Preserve array_copy's null, kind, bounds, overlap and atomic access semantics.
template<class T> inline void bounded_primitive_array_copy(Object* source, std::int32_t from,
        Object* target, std::int32_t to, std::int32_t count) {
    OrdinaryField<T>* input_elements;
    OrdinaryField<T>* output_elements;
    if (!detail::primitive_array_copy_storage(source, from, target, to, count, input_elements, output_elements))
        return;
    if (source == target && to > from) {
        for (std::int32_t i = count; i > 0; --i)
            output_elements[to + i - 1].set(input_elements[from + i - 1].get());
    } else {
        for (std::int32_t i = 0; i < count; ++i)
            output_elements[to + i].set(input_elements[from + i].get());
    }
}
// Small constant counts expand the same sequential atomic operations in either
// overlap direction. Validation still runs for an empty copy.
template<class T, std::int32_t Count> JNATIVE_FORCEINLINE void bounded_primitive_array_copy_inline(Object* source, std::int32_t from,
        Object* target, std::int32_t to) {
    static_assert(Count >= 0 && Count <= 32, "Constant primitive copy count must be between 0 and 32");
    OrdinaryField<T>* input_elements;
    OrdinaryField<T>* output_elements;
    if (!detail::primitive_array_copy_storage_impl<T, Count>(source, from, target, to, Count, input_elements, output_elements))
        return;
    if (Count == 0) return;
    using Indices = typename detail::MakePrimitiveArrayCopyIndices<Count>::type;
    if (source == target && to > from)
        detail::PrimitiveArrayCopyExpansion<T, Count>::backward(input_elements + from, output_elements + to, Indices{});
    else
        detail::PrimitiveArrayCopyExpansion<T, Count>::forward(input_elements + from, output_elements + to, Indices{});
}
// Ordinary callers let the compiler choose the boundary around the expanded copy.
template<class T, std::int32_t Count> inline void bounded_primitive_array_copy(Object* source, std::int32_t from,
        Object* target, std::int32_t to) {
    bounded_primitive_array_copy_inline<T, Count>(source, from, target, to);
}

// Count is guarded by the caller. The original copy handles a missed offset
// hint and retains the Java null, type and range failure ordering.
template<class T, std::int32_t Count, std::int32_t From> JNATIVE_FORCEINLINE void bounded_primitive_array_copy(
        Object* source, std::int32_t from, Object* target, std::int32_t to) {
    static_assert(Count > 0 && Count <= 32, "Fixed source range needs a positive small count");
    static_assert(From >= 0 && From <= std::numeric_limits<std::int32_t>::max() - Count,
            "Fixed source range must fit in a Java int");
    constexpr auto source_end = From + Count;
    // Widen before adding so a negative Java index cannot wrap into range.
    const auto target_end = static_cast<std::uint64_t>(static_cast<std::uint32_t>(to)) + Count;
    if (from == From && source && target) {
        const auto kind = source->array_kind;
        constexpr auto ordinary = PrimitiveArrayKind<T>::value;
        constexpr auto confined = ConfinedPrimitiveArray<T>::kind;
        if ((kind == ordinary || kind == confined) && target->array_kind == kind
                && source_end <= static_cast<Array*>(source)->length
                && target_end <= static_cast<std::uint32_t>(static_cast<Array*>(target)->length)
                && (to >= source_end || target_end <= From || source != target)) {
            if (kind == ordinary) {
                using Indices = typename detail::MakePrimitiveArrayCopyIndices<Count>::type;
                auto input = static_cast<PrimitiveArray<T>*>(source)->data() + From;
                auto output = static_cast<PrimitiveArray<T>*>(target)->data() + to;
                detail::PrimitiveArrayCopyExpansion<T, Count>::forward(input, output, Indices{});
            } else {
                auto input = static_cast<ConfinedPrimitiveArray<T>*>(source)->elements.get() + From;
                auto output = static_cast<ConfinedPrimitiveArray<T>*>(target)->elements.get() + to;
                std::memcpy(output, input, Count * sizeof(T));
            }
            return;
        }
    }
    bounded_primitive_array_copy<T, Count>(source, from, target, to);
}

namespace detail {
template<int Base, int... Lanes> struct FloatGroupMask;
template<int Base> struct FloatGroupMask<Base> : std::integral_constant<unsigned, 0> {};
template<int Base, int Lane, int... Rest>
struct FloatGroupMask<Base, Lane, Rest...> : std::integral_constant<unsigned,
        (Lane >= Base && Lane < Base + 4 ? 1u << (Lane % 4) : 0u) | FloatGroupMask<Base, Rest...>::value> {};

#if defined(__x86_64__) || defined(_M_X64)
template<int Base, int Lane>
JNATIVE_FORCEINLINE void patch_float_group(__m128i& group, float value) {
    if (Lane >= Base && Lane < Base + 4) {
        const auto bits = _mm_castps_si128(_mm_set_ss(value));
        group = _mm_or_si128(group, _mm_slli_si128(bits, (Lane % 4) * sizeof(float)));
    }
}
#else
template<int Base, int Lane>
JNATIVE_FORCEINLINE void patch_float_group(float* group, float value) {
    if (Lane >= Base && Lane < Base + 4) std::memcpy(group + Lane % 4, &value, sizeof(float));
}
#endif

template<int Base, int... Lanes, class... Values>
JNATIVE_FORCEINLINE void store_float_group(float* source, float* target, Values... values) {
    constexpr unsigned mask = FloatGroupMask<Base, Lanes...>::value;
    if (mask == 0) {
        std::memcpy(target + Base, source + Base, 4 * sizeof(float));
        return;
    }
#if defined(__x86_64__) || defined(_M_X64)
    const auto unchanged = _mm_loadu_ps(source + Base);
    const auto changed = _mm_setr_epi32(mask & 1 ? -1 : 0, mask & 2 ? -1 : 0,
            mask & 4 ? -1 : 0, mask & 8 ? -1 : 0);
    auto group = _mm_andnot_si128(changed, _mm_castps_si128(unchanged));
    const int updates[] = {0, (patch_float_group<Base, Lanes>(group, values), 0)...};
    (void)updates;
    _mm_storeu_ps(source + Base, _mm_castsi128_ps(group));
    _mm_storeu_ps(target + Base, _mm_castsi128_ps(group));
#else
    float group[4];
    std::memcpy(group, source + Base, sizeof(group));
    const int updates[] = {0, (patch_float_group<Base, Lanes>(group, values), 0)...};
    (void)updates;
    std::memcpy(source + Base, group, sizeof(group));
    std::memcpy(target + Base, group, sizeof(group));
#endif
}

template<int... Lanes, int... Groups, class... Values>
JNATIVE_FORCEINLINE void store_float_groups(float* source, float* target,
        std::integer_sequence<int, Groups...>, Values... values) {
    const int writes[] = {0, (store_float_group<4 * Groups, Lanes...>(source, target, values...), 0)...};
    (void)writes;
}
}

// Only the compiler's verified store/copy shape calls this helper. A failed
// guard leaves the arrays untouched so Java's original stores can still throw
// after committing earlier lanes.
template<int Count, int... Lanes, class... Values>
JNATIVE_FORCEINLINE bool try_packed_float_copy(Object* source, std::int32_t from,
        Object* target, std::int32_t to, Values... values) {
    static_assert(Count >= 4 && Count <= 32 && Count % 4 == 0, "Invalid packed float count");
    static_assert(sizeof...(Lanes) == sizeof...(Values), "Each packed lane requires a value");
    using Array = ConfinedPrimitiveArray<float>;
    if (!source || !target || source == target || source->array_kind != Array::kind
            || target->array_kind != Array::kind) return false;
    const auto input = static_cast<Array*>(source);
    const auto output = static_cast<Array*>(target);
    if (from < 0 || to < 0 || from > input->length - Count || to > output->length - Count) return false;
    detail::store_float_groups<Lanes...>(input->elements.get() + from, output->elements.get() + to,
            std::make_integer_sequence<int, Count / 4>{}, values...);
    return true;
}

Object* array_clone(Object* source);
Object* array_copy_of(Object* source, std::int32_t length);
template<class T> PrimitiveArray<T>* primitive_array(Object* object) {
    if (require_non_null(object)->array_kind != PrimitiveArrayKind<T>::value)
        throw std::logic_error("Invalid primitive array bytecode");
    return static_cast<PrimitiveArray<T>*>(object);
}
template<class T> T array_get(Object* object, std::int32_t index) {
    if (borrowed_confined_array<T>(object))
        return confined_array_get<T>(object, index);
    auto array = primitive_array<T>(object);
    array->bounds(index);
    return array->elements[index].get();
}
template<class T> void array_set(Object* object, std::int32_t index, T value) {
    if (borrowed_confined_array<T>(object)) {
        confined_array_set<T>(object, index, value);
        return;
    }
    auto array = primitive_array<T>(object);
    array->bounds(index);
    array->elements[index].set(value);
}
// Borrow immutable metadata from a separately rooted, nonmoving Java array.
// Construction cannot throw: null/type/bounds failures remain at the access,
// preserving empty loops and the ordering of Java side effects and exceptions.
template<class T, bool MayBeConfined = false> class PrimitiveArrayView {
    Object* const object_;
    void* const elements_;
    const bool confined_;
    const std::uint32_t length_;
    static Array* valid(Object* object) {
        return object && (object->array_kind == PrimitiveArrayKind<T>::value
                || (MayBeConfined && borrowed_confined_array<T>(object)))
            ? static_cast<Array*>(object) : nullptr;
    }
    PrimitiveArrayView(Object* object, Array* array)
        : object_(object), elements_(!array ? nullptr : array->array_kind == PrimitiveArrayKind<T>::value
                ? static_cast<void*>(static_cast<PrimitiveArray<T>*>(array)->data())
                : static_cast<void*>(static_cast<ConfinedPrimitiveArray<T>*>(array)->elements.get())),
          confined_(MayBeConfined && array && array->array_kind == ConfinedPrimitiveArray<T>::kind),
          length_(array ? static_cast<std::uint32_t>(array->length) : 0) {}
    [[noreturn]] static void fail(Object* object) {
        if (!valid(require_non_null(object))) invalid_array_operation("Invalid primitive array access");
        raise("java/lang/ArrayIndexOutOfBoundsException");
    }
public:
    explicit PrimitiveArrayView(Object* object) : PrimitiveArrayView(object, valid(object)) {}
    Object* object() const { return object_; }
    std::int32_t length() const {
        return elements_ ? static_cast<std::int32_t>(length_) : array_length(object_);
    }
    bool covers(std::int64_t first, std::int64_t last) const {
        return first >= 0 && last >= 0 && first < length_ && last < length_;
    }
    // Representation is immutable. Ordinary arrays always retain atomic access.
    T get_unchecked(std::int32_t index) const {
        if (std::is_floating_point<T>::value) {
            // Join the paths as raw bits before producing a floating value. With
            // strict FP, joining float expressions makes MSVC spill/reload a
            // temporary in callers that transfer the value as bytes.
            using Bits = typename std::conditional<sizeof(T) == 4, std::uint32_t, std::uint64_t>::type;
            Bits bits;
            if (MayBeConfined && confined_) std::memcpy(&bits, static_cast<T*>(elements_) + index, sizeof(T));
            else {
                T value = static_cast<OrdinaryField<T>*>(elements_)[index].get();
                std::memcpy(&bits, &value, sizeof(T));
            }
            T value;
            std::memcpy(&value, &bits, sizeof(T));
            return value;
        }
        return MayBeConfined && confined_ ? static_cast<T*>(elements_)[index] : static_cast<OrdinaryField<T>*>(elements_)[index].get();
    }
    void set_unchecked(std::int32_t index, T value) const {
        if (MayBeConfined && confined_) static_cast<T*>(elements_)[index] = value;
        else static_cast<OrdinaryField<T>*>(elements_)[index].set(value);
    }
    // Raw native transfer with the same ordered reads as repeated get_unchecked.
    // The caller proves source range and writable destination capacity, retains
    // this array's root, and keeps the byte storage alive for the entire call.
    // Position, byte order and ownership of the destination remain caller-owned.
    void copy_to_bytes_unchecked(std::int32_t offset, std::int32_t count, void* destination) const {
        if (!count) return;
        auto output = static_cast<std::uint8_t*>(destination);
        if (MayBeConfined && confined_)
            detail::transfer_elements<T, true, false>(static_cast<T*>(elements_) + offset, output, count);
        else
            detail::transfer_elements<T, true, false>(static_cast<OrdinaryField<T>*>(elements_) + offset, output, count);
    }
    T get(std::int32_t index) const {
        if (static_cast<std::uint32_t>(index) >= length_) fail(object_);
        return get_unchecked(index);
    }
    void set(std::int32_t index, T value) const {
        if (static_cast<std::uint32_t>(index) >= length_) fail(object_);
        set_unchecked(index, value);
    }
};
inline ReferenceArray* reference_array(Object* object) {
    if (require_non_null(object)->array_kind != 'L') invalid_array_operation("Invalid reference array bytecode");
    return static_cast<ReferenceArray*>(object);
}
inline Object* reference_get(Object* object, std::int32_t index) {
    auto array = reference_array(object);
    array->bounds(index);
    return array->elements[index].get();
}
// Borrow only container metadata. Each read still observes the atomic reference
// payload; a returned element needs its own root if it survives a safepoint.
class ReferenceArrayView {
    Object* const object_;
    OrdinaryField<Object*>* const elements_;
    const std::uint32_t length_;
    static ReferenceArray* valid(Object* object) {
        return object && object->array_kind == 'L' ? static_cast<ReferenceArray*>(object) : nullptr;
    }
    ReferenceArrayView(Object* object, ReferenceArray* array)
        : object_(object), elements_(array ? array->elements.get() : nullptr),
          length_(array ? static_cast<std::uint32_t>(array->length) : 0) {}
    [[noreturn]] static void fail(Object* object) {
        reference_array(object);
        raise("java/lang/ArrayIndexOutOfBoundsException");
    }
public:
    explicit ReferenceArrayView(Object* object) : ReferenceArrayView(object, valid(object)) {}
    Object* object() const { return object_; }
    std::int32_t length() const {
        return elements_ ? static_cast<std::int32_t>(length_) : array_length(object_);
    }
    bool covers(std::int64_t first, std::int64_t last) const {
        return first >= 0 && last >= 0 && first < length_ && last < length_;
    }
    Object* get_unchecked(std::int32_t index) const {
        return elements_[static_cast<std::uint32_t>(index)].get();
    }
    Object* get(std::int32_t index) const {
        if (static_cast<std::uint32_t>(index) >= length_) fail(object_);
        return elements_[static_cast<std::uint32_t>(index)].get();
    }
};
void check_reference_store(ReferenceArray* array, Object* value);
// Generated proof: exact Object[], a null value, or a compatible value in an
// array with no narrower runtime component type. Keep null/bounds checks and
// atomic payload stores; only the proved-unnecessary covariance check is omitted.
inline void reference_set_exact(Object* object, std::int32_t index, Object* value) {
    auto array = reference_array(object);
    array->bounds(index);
    array->elements[index].set(value);
}
inline void reference_set(Object* object, std::int32_t index, Object* value) {
    auto array = reference_array(object);
    array->bounds(index);
    // Keep null/Object[] stores visible to the native optimizer. The helper
    // preserves covariance for typed arrays, including nested array classes.
    if (value && !array->accepts_any_reference
            && (!array->component_class_id || value->type_id() != array->component_class_id))
        check_reference_store(array, value);
    array->elements[index].set(value);
}
std::int32_t byte_get(Object* array, std::int32_t index);
void byte_set(Object* array, std::int32_t index, std::int32_t value);
Object* arguments(int argc, char** argv);
#ifdef _WIN32
Object* arguments(int argc, wchar_t** argv);
#endif
std::u16string utf16(const std::string& text);
// Managed callers with native-owned input may cooperate during long conversions.
std::u16string utf16_cooperative(const std::string& text);
String* as_string(Object* object);
std::u16string to_text(Object* object);
std::u16string to_text(std::int32_t value);
std::u16string to_text(std::int64_t value);
std::u16string to_text(float value);
std::u16string to_text(double value);
String* concatenate(const std::vector<std::u16string>& parts);
std::int32_t string_char(Object* string, std::int32_t index);
bool string_equals(Object* string, Object* other);
std::int32_t string_hash(Object* string);
std::int32_t string_compare(Object* first, Object* second);
String* substring(Object* string, std::int32_t begin, std::int32_t end);
String* string_concat(Object* first, Object* second);
String* string_trim(Object* value);
void string_from_chars(Object* string, Object* characters);
void string_from_chars(Object* string, Object* characters, std::int32_t offset, std::int32_t count);
void initialize_runtime();
std::int32_t identity_hash(Object* object);
bool try_object_hash(std::int32_t* result, Object* object) noexcept;
Object* object_string(Object* object);
bool object_equals(Object* object, Object* other);
std::int32_t object_hash(Object* object);
}

#include "jn_threads.hpp"
#include "jn_files.hpp"
#include "jn_reflection.hpp"
#include "jn_buffers.hpp"
#include "jn_random.hpp"
#include "jn_arrays.hpp"
#include "jn_string_kernels.hpp"
#include "jn_codecs.hpp"
