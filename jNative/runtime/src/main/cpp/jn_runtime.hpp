#pragma once
#include "jn_diagnostics.hpp"
#include "jn_common.hpp"
#include "jn_platform.hpp"

#include <atomic>
#include <cstdint>
#include <limits>
#include <memory>
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
    static void* operator new(std::size_t bytes) { return platform::allocate(bytes); }
    static void operator delete(void* value) noexcept { platform::deallocate(value); }
    virtual void trace(Tracer&) {}
    virtual const char* type_name() const { return "java/lang/Object"; }
    // Runtime-owned classes use zero. Reading immutable type metadata needs no
    // virtual call, including interface dispatch and covariant array stores.
    std::uint32_t type_id() const { return generated_type_id_; }
    virtual ~Object();
};

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

// The closed program proves these instance primitives are accessed only from
// the entry thread. Unknown native/reflection access and worker-reachable
// fields keep OrdinaryField; volatile fields always keep ManagedField.
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
    // Only this managed thread or the stopped-world collector touches the queue.
    // The published count also permits statistics without reading its vector.
    std::vector<Object*> allocation_queue;
    std::atomic<std::size_t> queued_allocations{0};
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
    std::unordered_map<std::uint64_t, Object*> handles_;
    std::unordered_set<std::uint64_t> external_handles_;
    std::atomic<BorrowedBlock*> borrowed_directory_[4096]{};
    std::vector<std::unique_ptr<BorrowedBlock>> borrowed_blocks_;
    std::vector<BorrowedBlock*> available_borrowed_blocks_;
    std::vector<ManagedField<Object*>*> static_roots_;
    std::uint64_t next_handle_ = 1;
    std::size_t collections_ = 0;
    std::atomic<std::size_t> allocations_{0};
    std::size_t total_allocations_ = 0;
    std::size_t peak_objects_ = 0;
    bool measure_ = false;
    std::vector<std::uint64_t> pauses_ns_;
    std::atomic<std::size_t> threshold_{4096};
    bool adaptive_threshold_ = true;
    bool collecting_ = false;
    std::atomic<bool> collection_requested_{false};
    bool closed_ = false;
    ThreadContext* collector_ = nullptr;
    void park(std::unique_lock<::jnative::platform::Mutex>& lock);
    void flush_allocations(ThreadContext* thread);
    std::size_t object_count() const;
public:
    Heap();
    static Heap& instance();
    ~Heap();
    void attach(ThreadContext* thread);
    void detach(ThreadContext* thread);
    bool try_close();
    void poll();
    void poll_if_requested() {
        if (collection_requested_.load(std::memory_order_acquire)) poll();
    }
    void collect();
    void enter_native(ThreadState state);
    void leave_native();
    void adopt(Object* object);
    void before_allocation();
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

inline Heap& managed_heap() {
    auto thread = current_thread();
    if (!thread || thread->state != ThreadState::running_managed || !thread->heap)
        throw std::logic_error("Safepoint outside managed execution");
    return *thread->heap;
}

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
            throw std::logic_error("A root frame needs an attached managed thread");
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
            throw std::logic_error("A root frame needs an attached managed thread");
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

template<class T, class... Args> T* allocate(Args&&... args) {
    Heap::instance().before_allocation();
    auto object = ::jnative::make_owned<T>(std::forward<Args>(args)...);
    Heap::instance().adopt(object.get());
    return object.release();
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

// Immutable whole-program ancestry, installed before Java execution begins.
// Runtime-owned objects and arrays retain the name-based Java type rules.
struct TypeCheckTable {
    const std::uint64_t* bits;
    std::size_t classes;
    std::size_t stride;
};
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
        return generated_assignable(actual, expected_id);
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
    const std::string descriptor;
    Array(std::int32_t size, std::string type, char kind)
        : Object(kind), length(size), descriptor(std::move(type)) {}
    const char* type_name() const override { return descriptor.c_str(); }
    void bounds(std::int32_t index) const {
        // Array lengths are nonnegative; one unsigned comparison checks both ends.
        if (static_cast<std::uint32_t>(index) >= static_cast<std::uint32_t>(length))
            raise("java/lang/ArrayIndexOutOfBoundsException");
    }
};
template<class T> struct PrimitiveArrayKind;
template<> struct PrimitiveArrayKind<std::uint8_t> { static constexpr char value = 'Z'; };
template<> struct PrimitiveArrayKind<std::int8_t> { static constexpr char value = 'B'; };
template<> struct PrimitiveArrayKind<std::uint16_t> { static constexpr char value = 'C'; };
template<> struct PrimitiveArrayKind<std::int16_t> { static constexpr char value = 'S'; };
template<> struct PrimitiveArrayKind<std::int32_t> { static constexpr char value = 'I'; };
template<> struct PrimitiveArrayKind<std::int64_t> { static constexpr char value = 'J'; };
template<> struct PrimitiveArrayKind<float> { static constexpr char value = 'F'; };
template<> struct PrimitiveArrayKind<double> { static constexpr char value = 'D'; };
template<class T> struct PrimitiveArray final : Array {
    std::unique_ptr<OrdinaryField<T>[]> elements;
    PrimitiveArray(std::int32_t size, std::string type) : Array(size, std::move(type), PrimitiveArrayKind<T>::value),
        elements(make_array<OrdinaryField<T> >(size)) {}
};
// Selected only for fresh arrays proved to remain inside a method or an
// entry-thread-owned object, with no untracked aliases.
// A distinct tag makes accidental use by an ordinary-array helper fail its type check.
template<class T> struct ConfinedPrimitiveArray final : Array {
    static constexpr char kind = PrimitiveArrayKind<T>::value + ('a' - 'A');
    std::unique_ptr<T[]> elements;
    ConfinedPrimitiveArray(std::int32_t size, const char* type)
        : Array(size, type, kind), elements(make_array<T>(size)) {}
};
template<class T> Object* new_confined_array(const char* descriptor, std::int32_t length) {
    if (length < 0) raise("java/lang/NegativeArraySizeException");
    return allocate<ConfinedPrimitiveArray<T>>(length, descriptor);
}
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
    bool covers(std::int64_t first, std::int64_t last) const {
        return first >= 0 && last >= 0 && first < length_ && last < length_;
    }
    // The generated fast path proves the entire index range before using these.
    T get_unchecked(std::int32_t index) const { return elements_[index]; }
    void set_unchecked(std::int32_t index, T value) const { elements_[index] = value; }
    T get(std::int32_t index) const {
        if (static_cast<std::uint32_t>(index) >= length_) fail();
        return elements_[index];
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
    std::unique_ptr<OrdinaryField<Object*>[]> elements;
    const std::uint32_t component_class_id;
    const bool accepts_any_reference;
    ReferenceArray(std::int32_t size, std::string type);
    void trace(Tracer& tracer) override;
};
Object* new_array(const char* descriptor, std::int32_t length);
Object* multi_array(const char* descriptor, const std::vector<std::int32_t>& dimensions, std::size_t depth = 0);
inline std::int32_t array_length(Object* object) {
    if (!require_non_null(object)->array_kind) throw std::logic_error("Invalid arraylength bytecode");
    return static_cast<Array*>(object)->length;
}
void array_copy(Object* source, std::int32_t source_offset, Object* target, std::int32_t target_offset, std::int32_t count);
Object* array_clone(Object* source);
Object* array_copy_of(Object* source, std::int32_t length);
template<class T> PrimitiveArray<T>* primitive_array(Object* object) {
    if (require_non_null(object)->array_kind != PrimitiveArrayKind<T>::value)
        throw std::logic_error("Invalid primitive array bytecode");
    return static_cast<PrimitiveArray<T>*>(object);
}
template<class T> T array_get(Object* object, std::int32_t index) {
    auto array = primitive_array<T>(object);
    array->bounds(index);
    return array->elements[index].get();
}
template<class T> void array_set(Object* object, std::int32_t index, T value) {
    auto array = primitive_array<T>(object);
    array->bounds(index);
    array->elements[index].set(value);
}
// Borrow immutable metadata from a separately rooted, nonmoving Java array.
// Construction cannot throw: null/type/bounds failures remain at the access,
// preserving empty loops and the ordering of Java side effects and exceptions.
template<class T> class PrimitiveArrayView {
    Object* const object_;
    OrdinaryField<T>* const elements_;
    const std::uint32_t length_;
    static PrimitiveArray<T>* valid(Object* object) {
        return object && object->array_kind == PrimitiveArrayKind<T>::value
            ? static_cast<PrimitiveArray<T>*>(object) : nullptr;
    }
    PrimitiveArrayView(Object* object, PrimitiveArray<T>* array)
        : object_(object), elements_(array ? array->elements.get() : nullptr),
          length_(array ? static_cast<std::uint32_t>(array->length) : 0) {}
    [[noreturn]] void fail() const {
        primitive_array<T>(object_);
        raise("java/lang/ArrayIndexOutOfBoundsException");
    }
public:
    explicit PrimitiveArrayView(Object* object) : PrimitiveArrayView(object, valid(object)) {}
    bool covers(std::int64_t first, std::int64_t last) const {
        return first >= 0 && last >= 0 && first < length_ && last < length_;
    }
    // Bounds elimination does not change the Java array's atomic representation.
    T get_unchecked(std::int32_t index) const { return elements_[index].get(); }
    void set_unchecked(std::int32_t index, T value) const { elements_[index].set(value); }
    T get(std::int32_t index) const {
        if (static_cast<std::uint32_t>(index) >= length_) fail();
        return elements_[index].get();
    }
    void set(std::int32_t index, T value) const {
        if (static_cast<std::uint32_t>(index) >= length_) fail();
        elements_[index].set(value);
    }
};
inline ReferenceArray* reference_array(Object* object) {
    if (require_non_null(object)->array_kind != 'L') throw std::logic_error("Invalid reference array bytecode");
    return static_cast<ReferenceArray*>(object);
}
inline Object* reference_get(Object* object, std::int32_t index) {
    auto array = reference_array(object);
    array->bounds(index);
    return array->elements[index].get();
}
void reference_set(Object* array, std::int32_t index, Object* value);
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
