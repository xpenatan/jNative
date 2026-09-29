#include "jn_runtime.hpp"
#include "jn_arrays.hpp"
#include "jn_unicode_data.hpp"
#include <algorithm>
#include <cstring>
#if JNATIVE_USE_CHARCONV
#include <charconv>
#endif
#include <iomanip>
#include <locale>
#include <cstdlib>

namespace jnative {
ThreadContext::ThreadContext() : identity([] {
    static std::atomic<std::uint64_t> next{1};
    return next.fetch_add(1);
}()) {}
JavaFrame::JavaFrame(const char* method, const char* source)
    : method(method), source(source), previous(current_thread()->frame) { current_thread()->frame = this; }
JavaFrame::~JavaFrame() { current_thread()->frame = previous; }
Throwable::Throwable(std::string type) : name(std::move(type)) {
#if JNATIVE_NATIVE_TRACES
    native_stack = capture_native_trace();
#endif
#if JNATIVE_JAVA_TRACES
    if (current_thread())
        for (auto frame = current_thread()->frame; frame; frame = frame->previous)
            stack.push_back({frame->method, frame->source, frame->line});
#endif
}
void add_suppressed(Object* object, Object* suppressed) {
    require_non_null(suppressed);
    if (object == suppressed) raise("java/lang/IllegalArgumentException", "Self suppression is not allowed");
    MonitorGuard guard(object);
    static_cast<Throwable*>(object)->suppressed.push_back(suppressed);
}
Object* get_suppressed(Object* object) {
    MonitorGuard guard(object);
    auto error = static_cast<Throwable*>(object);
    LocalRoot<> result(new_array("[Ljava/lang/Throwable;", static_cast<std::int32_t>(error->suppressed.size())));
    for (std::size_t i = 0; i < error->suppressed.size(); ++i)
        reference_set(result.get(), static_cast<std::int32_t>(i), error->suppressed[i]);
    return result.get();
}
void print_stack_trace(Object* object, Object* stream) {
    auto& output = stream ? *static_cast<PrintStream*>(stream)->stream : platform::error_output();
    LocalRoot<> root(require_non_null(object));
    std::vector<Object*> visited;
#if JNATIVE_NATIVE_TRACES
    std::vector<NativeCause> causes;
    auto* primary=dynamic_cast<Throwable*>(object);
    std::string message=Thrown(object).what();
#endif
    while (object) {
        if (std::find(visited.begin(), visited.end(), object) != visited.end()) {
            output << "[Circular exception cause]\n";
            break;
        }
        if (!visited.empty()) output << "Caused by: ";
        visited.push_back(object);
        output << Thrown(object).what() << '\n';
        auto error = dynamic_cast<Throwable*>(object);
        if (!error) break;
        for (const auto& frame : error->stack) {
            output << "\tat " << frame.method << '(' << frame.source;
            if (frame.line >= 0) output << ':' << frame.line;
            output << ")\n";
        }
#if JNATIVE_NATIVE_TRACES
        print_native_trace(error->native_stack, output);
        if(error!=primary && causes.size()<17) causes.push_back({Thrown(object).what(),&error->native_stack,error->native_capture_site});
#endif
        LocalRoot<> suppressed(get_suppressed(error));
        for (std::int32_t i = 0; i < array_length(suppressed.get()); ++i) {
            Object* secondary = reference_get(suppressed.get(), i);
            output << "\tSuppressed: " << Thrown(secondary).what() << '\n';
            for (const auto& frame : static_cast<Throwable*>(secondary)->stack)
                output << "\t\tat " << frame.method << '(' << frame.source << ':' << frame.line << ")\n";
        }
        object = error->cause.get();
    }
#if JNATIVE_NATIVE_TRACES
    if(primary) {
        auto report=save_native_report("exception",message,primary->native_stack,primary->native_capture_site,causes);
        if(!report.empty()) output<<"Crash report: "<<report<<'\n';
    }
#endif
}

void Tracer::visit(Object* object) {
    if (object && !object->marked) {
        object->marked = true;
        work_.push_back(object);
    }
}
void Tracer::drain() {
    while (!work_.empty()) {
        auto object = work_.back();
        work_.pop_back();
        object->trace(*this);
    }
}

Heap& Heap::instance() { static Heap heap; return heap; }
Heap::Heap() {
    const char* value = std::getenv("JNATIVE_STATS");
    measure_ = value && std::string(value) == "1";
}
Heap::~Heap() { for (auto object : objects_) delete object; }

void Heap::attach(ThreadContext* thread) {
    std::unique_lock<::jnative::platform::Mutex> lock(mutex_);
    if (closed_) throw std::logic_error("Runtime has shut down");
    changed_.wait(lock, [&] { return !collecting_; });
    threads_.push_back(thread);
    thread->heap = this;
    platform::managed_context() = thread;
}
void Heap::detach(ThreadContext* thread) {
    std::lock_guard<::jnative::platform::Mutex> lock(mutex_);
    if (thread->roots || thread->root_frames || thread->root_addresses) std::terminate();
    flush_allocations(thread);
    for (BorrowedBlock* block : thread->borrowed_blocks)
        available_borrowed_blocks_.push_back(block);
    threads_.erase(std::remove(threads_.begin(), threads_.end(), thread), threads_.end());
    platform::managed_context() = nullptr;
    thread->heap = nullptr;
    changed_.notify_all();
}
bool Heap::try_close() {
    std::lock_guard<platform::Mutex> lock(mutex_);
    if (!threads_.empty() || !external_handles_.empty()) return false;
    closed_ = true;
    return true;
}
void Heap::park(std::unique_lock<::jnative::platform::Mutex>& lock) {
    if (collecting_ && collector_ != current_thread()) {
        current_thread()->state = ThreadState::at_safepoint;
        changed_.notify_all();
        changed_.wait(lock, [&] { return !collecting_; });
        current_thread()->state = ThreadState::running_managed;
    }
}
void Heap::poll() {
    auto thread = current_thread();
    if (!thread || thread->state != ThreadState::running_managed)
        throw std::logic_error("Safepoint outside managed execution");
    // A collector still waits for every managed thread to park under mutex_.
    // Observing an idle heap need not acquire that global lock on each loop edge.
    if (!collection_requested_.load(std::memory_order_acquire)) return;
    std::unique_lock<::jnative::platform::Mutex> lock(mutex_);
    park(lock);
}
void Heap::collect() {
    if (!current_thread() || current_thread()->state != ThreadState::running_managed)
        throw std::logic_error("Collection outside managed execution");
    std::unique_lock<::jnative::platform::Mutex> lock(mutex_);
    if (collecting_) { park(lock); return; }
    auto start = measure_ ? platform::MonotonicClock::now() : platform::MonotonicClock::time_point{};
    collecting_ = true;
    collector_ = current_thread();
    collection_requested_.store(true, std::memory_order_seq_cst);
    changed_.wait(lock, [&] {
        return std::all_of(threads_.begin(), threads_.end(), [&](ThreadContext* thread) {
            return thread == collector_ || thread->state != ThreadState::running_managed;
        });
    });
    try {
        for (auto thread : threads_) flush_allocations(thread);
        Tracer tracer;
        for (auto thread : threads_) {
            for (auto root = thread->roots; root; root = root->previous)
                tracer.visit(root->value);
            for (auto frame = thread->root_frames; frame; frame = frame->previous)
                for (std::size_t i = 0; i < frame->count; ++i) tracer.visit(frame->values[i]);
            for (auto frame = thread->root_addresses; frame; frame = frame->previous)
                for (std::size_t i = 0; i < frame->count; ++i) tracer.visit(*frame->addresses[i]);
            tracer.visit(thread->java_thread);
            for (auto object : thread->held_monitors) tracer.visit(object);
            for (const auto& entry : thread->thread_locals) tracer.visit(entry.second);
        }
        for (auto entry : handles_) tracer.visit(entry.second);
        for (auto field : static_roots_) tracer.visit(field->get());
        tracer.drain();
        auto end = std::remove_if(objects_.begin(), objects_.end(), [](Object* object) {
            if (object->marked) { object->marked = false; return false; }
            delete object;
            return true;
        });
        objects_.erase(end, objects_.end());
        ++collections_;
        allocations_.store(0, std::memory_order_relaxed);
        // Keep collection work proportional to allocations as the live application grows.
        if (adaptive_threshold_)
            threshold_.store(std::max<std::size_t>(4096, objects_.size()), std::memory_order_relaxed);
        if (measure_) pauses_ns_.push_back(static_cast<std::uint64_t>(
                std::chrono::duration_cast<std::chrono::nanoseconds>(platform::MonotonicClock::now() - start).count()));
    } catch (...) {
        for (auto object : objects_) object->marked = false;
        collecting_ = false;
        collector_ = nullptr;
        collection_requested_.store(false, std::memory_order_seq_cst);
        changed_.notify_all();
        throw;
    }
    collecting_ = false;
    collector_ = nullptr;
    collection_requested_.store(false, std::memory_order_seq_cst);
    changed_.notify_all();
}
void Heap::before_allocation() {
    poll();
    if (allocations_.load(std::memory_order_relaxed) >= threshold_.load(std::memory_order_relaxed))
        collect();
}
void Heap::adopt(Object* object) {
    auto thread = current_thread();
    if (!thread || thread->heap != this || thread->state != ThreadState::running_managed)
        throw std::logic_error("Allocation outside managed execution");
    thread->allocation_queue.push_back(object);
    thread->queued_allocations.store(thread->allocation_queue.size(), std::memory_order_relaxed);
    allocations_.fetch_add(1, std::memory_order_relaxed);
}
void Heap::flush_allocations(ThreadContext* thread) {
    auto& pending = thread->allocation_queue;
    objects_.insert(objects_.end(), pending.begin(), pending.end());
    total_allocations_ += pending.size();
    pending.clear();
    thread->queued_allocations.store(0, std::memory_order_relaxed);
    peak_objects_ = std::max(peak_objects_, object_count());
}
std::size_t Heap::object_count() const {
    std::size_t result = objects_.size();
    for (auto thread : threads_)
        result += thread->queued_allocations.load(std::memory_order_relaxed);
    return result;
}
void Heap::enter_native(ThreadState state) {
    auto thread = current_thread();
    if (!thread || thread->state != ThreadState::running_managed)
        throw std::logic_error("Native transition outside managed execution");
    // Publish the complete root chain before the collector can observe this
    // thread as stopped. Pair the SC state/request operations with leave_native
    // and collect: either the collector sees running, or reentry sees its request.
    thread->state.store(state, std::memory_order_seq_cst);
    if (collection_requested_.load(std::memory_order_seq_cst)) {
        // Taking the waiter's mutex prevents a notification racing its wait.
        std::lock_guard<::jnative::platform::Mutex> lock(mutex_);
        changed_.notify_all();
    }
}
void Heap::leave_native() {
    auto thread = current_thread();
    if (!thread) throw std::logic_error("Native reentry needs an attached thread");
    thread->state.store(ThreadState::running_managed, std::memory_order_seq_cst);
    // Do not read or mutate managed storage until this handshake completes. A
    // collector that already observed in_native may still be tracing our roots.
    if (collection_requested_.load(std::memory_order_seq_cst)) {
        std::unique_lock<::jnative::platform::Mutex> lock(mutex_);
        park(lock);
    }
}
std::uint64_t Heap::retain(Object* object, bool external) {
    if (!object) return 0;
    std::lock_guard<::jnative::platform::Mutex> lock(mutex_);
    if (next_handle_ & (std::uint64_t(1) << 63))
        throw std::overflow_error("Native handle identifiers exhausted");
    auto id = next_handle_++;
    handles_.emplace(id, object);
    try { if (external) external_handles_.insert(id); }
    catch (...) { handles_.erase(id); throw; }
    return id;
}
Object* Heap::resolve(std::uint64_t handle) {
    if (!handle) return nullptr;
    if (handle & (std::uint64_t(1) << 63)) {
        std::uint32_t index = static_cast<std::uint32_t>(handle);
        if (index / 64 >= 4096) throw std::logic_error("Expired native handle");
        BorrowedBlock* block = borrowed_directory_[index / 64].load(std::memory_order_acquire);
        if (!block) throw std::logic_error("Expired native handle");
        BorrowedSlot& slot = block->slots[index % 64];
        if (slot.token.load(std::memory_order_acquire) != handle)
            throw std::logic_error("Expired native handle");
        Object* object = slot.object.load(std::memory_order_acquire);
        // An acquire that observes a replacement object also observes the prior
        // token invalidation. Rechecking prevents a stale token from resolving
        // to an object installed by a later borrower of this slot.
        if (slot.token.load(std::memory_order_acquire) != handle)
            throw std::logic_error("Expired native handle");
        return object;
    }
    std::lock_guard<::jnative::platform::Mutex> lock(mutex_);
    auto found = handles_.find(handle);
    if (found == handles_.end()) throw std::logic_error("Expired native handle");
    return found->second;
}
std::uint64_t Heap::duplicate(std::uint64_t handle, bool external) {
    if (!handle) return 0;
    if (handle & (std::uint64_t(1) << 63)) {
        Object* object;
        try { object = resolve(handle); }
        catch (const std::logic_error&) { throw std::invalid_argument("Expired native handle"); }
        return retain(object, external);
    }
    std::lock_guard<::jnative::platform::Mutex> lock(mutex_);
    auto found = handles_.find(handle);
    if (found == handles_.end()) throw std::invalid_argument("Expired native handle");
    if (next_handle_ & (std::uint64_t(1) << 63))
        throw std::overflow_error("Native handle identifiers exhausted");
    auto id = next_handle_++;
    handles_.emplace(id, found->second);
    try { if (external) external_handles_.insert(id); }
    catch (...) { handles_.erase(id); throw; }
    return id;
}
void Heap::release(std::uint64_t handle) {
    if (!handle) return;
    std::lock_guard<::jnative::platform::Mutex> lock(mutex_);
    handles_.erase(handle);
    external_handles_.erase(handle);
}
bool Heap::release_checked(std::uint64_t handle) {
    if (!handle) return true;
    std::lock_guard<::jnative::platform::Mutex> lock(mutex_);
    external_handles_.erase(handle);
    return handles_.erase(handle) != 0;
}
std::pair<std::size_t, std::size_t> Heap::statistics() {
    std::lock_guard<::jnative::platform::Mutex> lock(mutex_);
    return {object_count(), collections_};
}
void Heap::report_statistics() {
    std::lock_guard<::jnative::platform::Mutex> lock(mutex_);
    if (!measure_) return;
    auto pauses = pauses_ns_;
    std::sort(pauses.begin(), pauses.end());
    std::uint64_t total = 0;
    for (auto value : pauses) total += value;
    auto percentile = [&](std::size_t p) {
        return pauses.empty() ? std::uint64_t(0) : pauses[((pauses.size() - 1) * p + 99) / 100];
    };
    auto live_objects = object_count();
    auto total_allocations = total_allocations_ + live_objects - objects_.size();
    peak_objects_ = std::max(peak_objects_, live_objects);
    platform::error_output() << "JNATIVE_STATS allocations=" << total_allocations << " live_objects=" << live_objects
        << " peak_objects=" << peak_objects_ << " collections=" << collections_
        << " gc_total_ns=" << total << " gc_p50_ns=" << percentile(50) << " gc_p99_ns=" << percentile(99)
        << " gc_max_ns=" << percentile(100) << '\n';
}
void Heap::collection_threshold(std::size_t threshold) {
    if (!threshold) throw std::invalid_argument("Collection threshold must be positive");
    std::lock_guard<::jnative::platform::Mutex> lock(mutex_);
    threshold_.store(threshold, std::memory_order_relaxed);
    adaptive_threshold_ = false;
}
void Heap::add_static_root(ManagedField<Object*>* field) {
    std::lock_guard<::jnative::platform::Mutex> lock(mutex_);
    static_roots_.push_back(field);
}
void Heap::remove_static_root(ManagedField<Object*>* field) {
    std::lock_guard<::jnative::platform::Mutex> lock(mutex_);
    static_roots_.erase(std::remove(static_roots_.begin(), static_roots_.end(), field), static_roots_.end());
}
ThreadAttachment::ThreadAttachment() {
    if (!current_thread()) {
        diagnostics_attach_thread();
        environment_ = ::jnative::make_owned<FloatingEnvironment>();
        context_ = ::jnative::make_owned<ThreadContext>();
        Heap::instance().attach(context_.get());
    }
}
ThreadAttachment::~ThreadAttachment() {
    if (context_) { Heap::instance().detach(context_.get()); diagnostics_detach_thread(); }
}
NativeRegion::NativeRegion(ThreadState state) {
    platform::save_floating(previous_);
    Heap::instance().enter_native(state);
    entered_ = true;
}
NativeRegion::~NativeRegion() {
    platform::restore_floating(previous_);
    if (entered_) Heap::instance().leave_native();
}
FloatingEnvironment::FloatingEnvironment() {
    platform::save_floating(previous_);
    platform::java_floating();
}
FloatingEnvironment::~FloatingEnvironment() { platform::restore_floating(previous_); }

namespace {
// Lookup keys borrow the caller's text only for the duration of find(). Stored
// keys refer to immutable UTF-16 storage owned by the entry's rooted String.
struct LiteralText {
    const char16_t* data;
    std::size_t size;
    bool operator==(const LiteralText& other) const {
        return size == other.size && (size == 0
            || std::char_traits<char16_t>::compare(data, other.data, size) == 0);
    }
};
struct LiteralHash {
    std::size_t operator()(const LiteralText& text) const {
        std::size_t hash = 2166136261u;
        for (std::size_t i = 0; i < text.size; ++i)
            hash = (hash ^ static_cast<std::size_t>(text.data[i])) * 16777619u;
        return hash;
    }
};
struct RootedLiteral {
    GlobalHandle<String> root;
    String* value;
    explicit RootedLiteral(String* string) : root(string), value(string) {}
};
String* intern_literal(const char16_t* text, std::size_t size) {
    // Literal table is also a root set. The mutex is never held at a safepoint.
    static ::jnative::platform::Mutex mutex;
    static std::unordered_map<LiteralText, RootedLiteral, LiteralHash> strings;
    {
        std::lock_guard<::jnative::platform::Mutex> lock(mutex);
        auto found = strings.find(LiteralText{text, size});
        if (found != strings.end()) return found->second.value;
    }
    LocalRoot<String> created(allocate<String>(std::u16string(text, size)));
    std::lock_guard<::jnative::platform::Mutex> lock(mutex);
    LiteralText stored{created.get()->value.data(), created.get()->value.size()};
    return strings.emplace(stored, RootedLiteral(created.get())).first->second.value;
}
}

BorrowedSlot* Heap::borrow(Object* object) {
    ThreadContext* thread = current_thread();
    while (thread->free_borrowed.empty()) {
        // Capacity grows only on deeper nesting, never on steady-state imports.
        thread->free_borrowed.reserve(thread->borrowed_blocks.size() * 64 + 64);
        thread->borrowed_blocks.reserve(thread->borrowed_blocks.size() + 1);
        std::lock_guard<platform::Mutex> lock(mutex_);
        BorrowedBlock* block;
        if (!available_borrowed_blocks_.empty()) {
            block = available_borrowed_blocks_.back();
            available_borrowed_blocks_.pop_back();
        } else {
            if (borrowed_blocks_.size() == 4096)
                throw std::overflow_error("Simultaneous borrowed handle capacity exhausted");
            available_borrowed_blocks_.reserve(borrowed_blocks_.size() + 1);
            auto owned = ::jnative::make_owned<BorrowedBlock>();
            block = owned.get();
            std::uint32_t base = static_cast<std::uint32_t>(borrowed_blocks_.size()) * 64;
            for (std::uint32_t i = 0; i < 64; ++i) block->slots[i].index = base + i;
            borrowed_blocks_.push_back(std::move(owned));
            borrowed_directory_[base / 64].store(block, std::memory_order_release);
        }
        thread->borrowed_blocks.push_back(block);
        for (BorrowedSlot& slot : block->slots)
            if (slot.generation != 0x7fffffff) thread->free_borrowed.push_back(&slot);
    }
    BorrowedSlot* slot = thread->free_borrowed.back();
    thread->free_borrowed.pop_back();
    std::uint64_t token = (std::uint64_t(1) << 63)
        | (std::uint64_t(++slot->generation) << 32) | slot->index;
    // Publish storage before its token. The separate SC managed/native handshake
    // publishes the entire root chain before collection can inspect this thread.
    slot->object.store(object, std::memory_order_release);
    slot->token.store(token, std::memory_order_release);
    return slot;
}
void Heap::end_borrow(BorrowedSlot* slot) noexcept {
    slot->token.store(0, std::memory_order_release);
    slot->object.store(nullptr, std::memory_order_release);
    if (slot->generation != 0x7fffffff) current_thread()->free_borrowed.push_back(slot);
}
String* literal(const std::u16string& text) {
    return intern_literal(text.data(), text.size());
}
String* literal(const char16_t* text) {
    return intern_literal(text, std::char_traits<char16_t>::length(text));
}
PrintStream* standard_out() {
    static GlobalHandle<PrintStream> output(allocate<PrintStream>(&platform::output()));
    return output.get();
}
PrintStream* standard_error() {
    static GlobalHandle<PrintStream> output(allocate<PrintStream>(&platform::error_output()));
    return output.get();
}
static std::string utf8_impl(const std::u16string& text, bool cooperative) {
    std::string result;
    std::uint32_t work = 0;
    if (cooperative) safepoint();
    for (std::size_t i = 0; i < text.size(); ++i) {
        std::uint32_t point = text[i];
        if (point >= 0xd800 && point <= 0xdbff) {
            if (i + 1 < text.size() && text[i + 1] >= 0xdc00 && text[i + 1] <= 0xdfff)
                point = 0x10000 + ((point - 0xd800) << 10) + (text[++i] - 0xdc00);
            else point = '?';
        } else if (point >= 0xdc00 && point <= 0xdfff) point = '?';
        if (point < 0x80) result += char(point);
        else if (point < 0x800) {
            result += char(0xc0 | (point >> 6)); result += char(0x80 | (point & 63));
        } else if (point < 0x10000) {
            result += char(0xe0 | (point >> 12)); result += char(0x80 | ((point >> 6) & 63)); result += char(0x80 | (point & 63));
        } else {
            result += char(0xf0 | (point >> 18)); result += char(0x80 | ((point >> 12) & 63));
            result += char(0x80 | ((point >> 6) & 63)); result += char(0x80 | (point & 63));
        }
        if (cooperative && (++work & 1023) == 0) safepoint();
    }
    return result;
}
std::string utf8(const std::u16string& text) { return utf8_impl(text, false); }
std::string utf8_cooperative(const std::u16string& text) { return utf8_impl(text, true); }
std::string character(std::int32_t code) { return utf8(std::u16string(1, char16_t(code))); }
void print(Object* stream, const std::string& value, bool newline) {
    auto output = dynamic_cast<PrintStream*>(require_non_null(stream));
    if (!output) throw std::logic_error("Invalid print receiver");
    auto destination = output->stream;
    static ::jnative::platform::Mutex mutex;
    NativeRegion blocking;
    std::lock_guard<::jnative::platform::Mutex> lock(mutex);
    *destination << value;
    if (newline) *destination << '\n';
    destination->flush();
}
void print(Object* stream, Object* value, bool newline) {
    if (!value) print(stream, std::string("null"), newline);
    else if (auto text = dynamic_cast<String*>(value)) print(stream, utf8(text->value), newline);
    else print(stream, std::string(value->type_name()), newline);
}

namespace {
struct Parents {
    std::string parent;
    std::vector<std::string> interfaces;
    std::uint32_t generated_id;
    Parents(std::string parent = {}, std::vector<std::string> interfaces = {}, std::uint32_t generated_id = 0)
        : parent(std::move(parent)), interfaces(std::move(interfaces)), generated_id(generated_id) {}
};
std::unordered_map<std::string, Parents>& types() {
    static std::unordered_map<std::string, Parents> table = {
        {"java/lang/Object", {"", {}}},
        {"java/lang/Class", {"java/lang/Object", {"java/io/Serializable"}}},
        {"java/nio/Buffer", {"java/lang/Object", {}}},
        {"java/nio/ByteBuffer", {"java/nio/Buffer", {"java/lang/Comparable"}}},
        {"java/nio/FloatBuffer", {"java/nio/Buffer", {"java/lang/Comparable"}}},
        {"java/nio/ShortBuffer", {"java/nio/Buffer", {"java/lang/Comparable"}}},
        {"java/nio/IntBuffer", {"java/nio/Buffer", {"java/lang/Comparable"}}},
        {"java/nio/LongBuffer", {"java/nio/Buffer", {"java/lang/Comparable"}}},
        {"java/nio/DoubleBuffer", {"java/nio/Buffer", {"java/lang/Comparable"}}},
        {"java/nio/ByteOrder", {"java/lang/Object", {}}},
        {"java/nio/BufferUnderflowException", {"java/lang/RuntimeException", {}}},
        {"java/nio/BufferOverflowException", {"java/lang/RuntimeException", {}}},
        {"java/nio/InvalidMarkException", {"java/lang/IllegalStateException", {}}},
        {"java/nio/ReadOnlyBufferException", {"java/lang/UnsupportedOperationException", {}}},
        {"java/lang/reflect/Member", {"java/lang/Object", {}}},
        {"java/lang/reflect/AccessibleObject", {"java/lang/Object", {}}},
        {"java/lang/reflect/Executable", {"java/lang/reflect/AccessibleObject", {"java/lang/reflect/Member"}}},
        {"java/lang/reflect/Method", {"java/lang/reflect/Executable", {}}},
        {"java/lang/reflect/Constructor", {"java/lang/reflect/Executable", {}}},
        {"java/lang/reflect/Field", {"java/lang/reflect/AccessibleObject", {"java/lang/reflect/Member"}}},
        {"java/lang/ReflectiveOperationException", {"java/lang/Exception", {}}},
        {"java/lang/ClassNotFoundException", {"java/lang/ReflectiveOperationException", {}}},
        {"java/lang/NoSuchMethodException", {"java/lang/ReflectiveOperationException", {}}},
        {"java/lang/NoSuchFieldException", {"java/lang/ReflectiveOperationException", {}}},
        {"java/lang/IllegalAccessException", {"java/lang/ReflectiveOperationException", {}}},
        {"java/lang/InstantiationException", {"java/lang/ReflectiveOperationException", {}}},
        {"java/lang/reflect/InvocationTargetException", {"java/lang/ReflectiveOperationException", {}}},
        {"java/lang/Cloneable", {"java/lang/Object", {}}},
        {"java/io/Serializable", {"java/lang/Object", {}}},
        {"java/lang/Runnable", {"java/lang/Object", {}}},
        {"java/lang/Throwable", {"java/lang/Object", {"java/io/Serializable"}}},
        {"java/lang/Exception", {"java/lang/Throwable", {}}},
        {"java/lang/RuntimeException", {"java/lang/Exception", {}}},
        {"java/lang/Error", {"java/lang/Throwable", {}}},
        {"java/lang/LinkageError", {"java/lang/Error", {}}},
        {"java/lang/ExceptionInInitializerError", {"java/lang/LinkageError", {}}},
        {"java/lang/NoClassDefFoundError", {"java/lang/LinkageError", {}}},
        {"java/lang/AbstractMethodError", {"java/lang/LinkageError", {}}},
        {"java/lang/OutOfMemoryError", {"java/lang/Error", {}}},
        {"java/lang/NullPointerException", {"java/lang/RuntimeException", {}}},
        {"java/lang/ArithmeticException", {"java/lang/RuntimeException", {}}},
        {"java/lang/IndexOutOfBoundsException", {"java/lang/RuntimeException", {}}},
        {"java/lang/ArrayIndexOutOfBoundsException", {"java/lang/IndexOutOfBoundsException", {}}},
        {"java/lang/StringIndexOutOfBoundsException", {"java/lang/IndexOutOfBoundsException", {}}},
        {"java/lang/ArrayStoreException", {"java/lang/RuntimeException", {}}},
        {"java/lang/ClassCastException", {"java/lang/RuntimeException", {}}},
        {"java/lang/NegativeArraySizeException", {"java/lang/RuntimeException", {}}},
        {"java/lang/IllegalArgumentException", {"java/lang/RuntimeException", {}}},
        {"java/lang/IllegalStateException", {"java/lang/RuntimeException", {}}},
        {"java/lang/UnsupportedOperationException", {"java/lang/RuntimeException", {}}},
        {"java/util/NoSuchElementException", {"java/lang/RuntimeException", {}}},
        {"java/util/ConcurrentModificationException", {"java/lang/RuntimeException", {}}},
        {"java/io/IOException", {"java/lang/Exception", {}}},
        {"java/util/zip/DataFormatException", {"java/lang/Exception", {}}},
        {"java/nio/file/FileSystemException", {"java/io/IOException", {}}},
        {"java/nio/file/NoSuchFileException", {"java/nio/file/FileSystemException", {}}},
        {"java/nio/file/AccessDeniedException", {"java/nio/file/FileSystemException", {}}},
        {"java/nio/file/FileAlreadyExistsException", {"java/nio/file/FileSystemException", {}}},
        {"java/nio/file/DirectoryNotEmptyException", {"java/nio/file/FileSystemException", {}}},
        {"java/nio/file/InvalidPathException", {"java/lang/IllegalArgumentException", {}}},
        {"java/nio/charset/CharacterCodingException", {"java/io/IOException", {}}},
        {"java/nio/charset/MalformedInputException", {"java/nio/charset/CharacterCodingException", {}}},
        {"jnative/runtime/NativePath", {"java/lang/Object", {"java/nio/file/Path", "java/lang/Comparable", "java/lang/Iterable"}}},
        {"java/lang/IllegalMonitorStateException", {"java/lang/RuntimeException", {}}},
        {"java/lang/IllegalThreadStateException", {"java/lang/IllegalArgumentException", {}}},
        {"java/lang/InterruptedException", {"java/lang/Exception", {}}},
        {"java/lang/Thread", {"java/lang/Object", {"java/lang/Runnable"}}},
        {"java/lang/ThreadLocal", {"java/lang/Object", {}}},
        {"java/lang/Number", {"java/lang/Object", {"java/io/Serializable"}}},
        {"java/util/concurrent/atomic/AtomicInteger", {"java/lang/Number", {}}},
        {"java/util/concurrent/atomic/AtomicLong", {"java/lang/Number", {}}},
        {"java/lang/String", {"java/lang/Object", {"java/io/Serializable", "java/lang/Comparable", "java/lang/CharSequence"}}}
    };
    return table;
}
// Borrow names during checks. Registry keys and array descriptors own the text;
// successful casts and array stores must not allocate temporary native strings.
struct TypeName {
    const char* data;
    std::size_t size;
    TypeName(const char* text) : data(text), size(std::strlen(text)) {}
    TypeName(const std::string& text) : data(text.data()), size(text.size()) {}
    TypeName(const char* text, std::size_t count) : data(text), size(count) {}
    bool operator==(TypeName other) const {
        return size == other.size && (size == 0 || data == other.data || std::memcmp(data, other.data, size) == 0);
    }
};
struct TypeNameHash {
    std::size_t operator()(TypeName value) const {
        std::size_t hash = 2166136261u;
        for (std::size_t i = 0; i < value.size; ++i)
            hash = (hash ^ static_cast<unsigned char>(value.data[i])) * 16777619u;
        return hash;
    }
};
using TypeLookup = std::unordered_map<TypeName, const Parents*, TypeNameHash>;
TypeLookup& type_lookup() {
    static TypeLookup table = [] {
        TypeLookup result;
        for (const auto& entry : types()) result.emplace(TypeName(entry.first), &entry.second);
        return result;
    }();
    return table;
}
TypeName component(TypeName descriptor) {
    if (descriptor.size <= 1) return TypeName("", 0);
    TypeName value(descriptor.data + 1, descriptor.size - 1);
    if (value.data[0] == 'L')
        return TypeName(value.data + 1, value.size >= 2 ? value.size - 2 : 0);
    return value;
}
bool assignable_name(TypeName actual, TypeName expected) {
    if (actual == expected || expected == TypeName("java/lang/Object")) return true;
    if (actual.size == 0 || expected.size == 0) return false;
    if (actual.data[0] == '[') {
        if (expected == TypeName("java/lang/Cloneable") || expected == TypeName("java/io/Serializable")) return true;
        if (expected.data[0] != '[') return false;
        // A one-letter class name is still a reference component (for example [LI;).
        const bool actual_primitive = actual.size == 2 && actual.data[1] != '[';
        const bool expected_primitive = expected.size == 2 && expected.data[1] != '[';
        auto a = component(actual), b = component(expected);
        if (actual_primitive || expected_primitive)
            return actual_primitive && expected_primitive && a == b;
        return assignable_name(a, b);
    }
    auto found = type_lookup().find(actual);
    if (found == type_lookup().end()) return false;
    if (assignable_name(found->second->parent, expected)) return true;
    for (const auto& face : found->second->interfaces) if (assignable_name(face, expected)) return true;
    return false;
}
}
void register_type(const std::string& name, const std::string& parent, std::vector<std::string> interfaces,
    std::uint32_t generated_id) {
    // Registration finishes on the main thread before Java execution starts.
    auto entry = types().emplace(name, Parents()).first;
    entry->second = {parent, std::move(interfaces), generated_id};
    // Unordered-map rehashing preserves references to these owning nodes.
    type_lookup()[TypeName(entry->first)] = &entry->second;
}
std::uint32_t generated_type_id(const std::string& name) {
    const auto& lookup = type_lookup();
    auto found = lookup.find(TypeName(name));
    return found == lookup.end() ? 0 : found->second->generated_id;
}
bool assignable(const std::string& actual, const std::string& expected) {
    return assignable_name(actual, expected);
}
TypeCheckTable generated_type_checks{nullptr, 0, 0};
void register_type_checks(const std::uint64_t* bits, std::size_t classes, std::size_t stride) {
    if (!bits || stride != (classes + 64) / 64)
        throw std::logic_error("Invalid generated type hierarchy");
    generated_type_checks = {bits, classes, stride};
}
bool instance_of(Object* object, const char* expected) {
    if (!object) return false;
    const char* actual = object->type_name();
    if (actual == expected || std::strcmp(actual, expected) == 0) return true;
    return assignable_name(actual, expected);
}
Object* check_cast(Object* object, const char* expected) {
    if (object && !instance_of(object, expected)) {
        std::string message = std::string(object->type_name()) + " cannot be cast to " + expected;
        std::replace(message.begin(), message.end(), '/', '.');
        raise("java/lang/ClassCastException", message.c_str());
    }
    return object;
}
Thrown::Thrown(Object* object) : object_(object), description_(object->type_name()) {
    std::replace(description_.begin(), description_.end(), '/', '.');
    if (auto throwable = dynamic_cast<Throwable*>(object)) {
        LocalRoot<String> message(dynamic_cast<String*>(throwable->message.get()));
        if (message.get()) description_ += ": " + utf8_cooperative(message->value);
    }
}
[[noreturn]] void raise(const char* type, const char* message) {
    LocalRoot<Throwable> error(allocate<Throwable>(type));
    if (message) error->message.set(literal(utf16(message)));
    throw Thrown(error.get());
}
[[noreturn]] void raise_with_message(const char* type, Object* message) {
    LocalRoot<> text(message);
    LocalRoot<Throwable> error(allocate<Throwable>(type));
    error->message.set(text.get());
    throw Thrown(error.get());
}
[[noreturn]] void raise_native(const std::exception& source) {
    LocalRoot<Throwable> error(allocate<Throwable>("java/lang/RuntimeException"));
    error->message.set(literal(utf16(source.what())));
#if JNATIVE_NATIVE_TRACES
    auto* owned=dynamic_cast<const NativeException*>(&source);
    if(owned) error->native_stack=owned->native_trace();
    error->native_capture_site=owned ? "native-construction" : "native-boundary-catch; throw-site unavailable";
#endif
    throw Thrown(error.get());
}
[[noreturn]] void throw_object(Object* object) {
    if (!object) raise("java/lang/NullPointerException");
    throw Thrown(object);
}
void ClassInitialization::run_slow(const std::function<void()>& initializer) {
    // Completed initialization publishes its writes without repeating a native transition.
    if (state_.load(std::memory_order_acquire) == 2) return;
    bool previously_failed = false;
    {
        NativeRegion blocked(ThreadState::blocked_managed);
        std::unique_lock<::jnative::platform::Mutex> lock(mutex_);
        changed_.wait(lock, [&] { return state_ != 1 || owner_ == current_thread()->identity; });
        if (state_ == 2 || (state_ == 1 && owner_ == current_thread()->identity)) return;
        if (state_ == 3) previously_failed = true;
        else {
            owner_ = current_thread()->identity;
            state_.store(1, std::memory_order_release);
        }
    }
    if (previously_failed) raise("java/lang/NoClassDefFoundError", "Could not initialize class");
    try {
        initializer();
    } catch (const Thrown& thrown) {
        {
            std::lock_guard<::jnative::platform::Mutex> lock(mutex_);
            failure_ = GlobalHandle<>(thrown.object());
            state_ = 3;
            changed_.notify_all();
        }
        if (instance_of(thrown.object(), "java/lang/Error")) throw;
        LocalRoot<Throwable> wrapped(allocate<Throwable>("java/lang/ExceptionInInitializerError"));
        wrapped->cause.set(thrown.object());
        throw Thrown(wrapped.get());
    } catch (...) {
        std::lock_guard<::jnative::platform::Mutex> lock(mutex_);
        state_ = 3;
        changed_.notify_all();
        throw;
    }
    std::lock_guard<::jnative::platform::Mutex> lock(mutex_);
    state_ = 2;
    changed_.notify_all();
}
ReferenceArray::ReferenceArray(std::int32_t size, std::string type)
    : Array(size, std::move(type), 'L'), elements(make_array<OrdinaryField<Object*> >(size)),
      component_class_id([this] {
          auto found = type_lookup().find(component(descriptor));
          return found == type_lookup().end() ? 0 : found->second->generated_id;
      }()), accepts_any_reference(descriptor == "[Ljava/lang/Object;") {}
void ReferenceArray::trace(Tracer& tracer) {
    for (std::int32_t i = 0; i < length; ++i) tracer.visit(elements[i].get());
}
Object* new_array(const char* descriptor, std::int32_t length) {
    if (length < 0) raise("java/lang/NegativeArraySizeException");
    switch (descriptor[1]) {
        case 'Z': return allocate<PrimitiveArray<std::uint8_t>>(length, descriptor);
        case 'B': return allocate<PrimitiveArray<std::int8_t>>(length, descriptor);
        case 'C': return allocate<PrimitiveArray<std::uint16_t>>(length, descriptor);
        case 'S': return allocate<PrimitiveArray<std::int16_t>>(length, descriptor);
        case 'I': return allocate<PrimitiveArray<std::int32_t>>(length, descriptor);
        case 'J': return allocate<PrimitiveArray<std::int64_t>>(length, descriptor);
        case 'F': return allocate<PrimitiveArray<float>>(length, descriptor);
        case 'D': return allocate<PrimitiveArray<double>>(length, descriptor);
        default: return allocate<ReferenceArray>(length, descriptor);
    }
}
Object* multi_array(const char* descriptor, const std::vector<std::int32_t>& dimensions, std::size_t depth) {
    for (auto size : dimensions) if (size < 0) raise("java/lang/NegativeArraySizeException");
    LocalRoot<> array(new_array(descriptor, dimensions[depth]));
    if (depth + 1 < dimensions.size()) {
        for (std::int32_t i = 0; i < dimensions[depth]; ++i) {
            LocalRoot<> child(multi_array(descriptor + 1, dimensions, depth + 1));
            reference_set(array.get(), i, child.get());
        }
    }
    return array.get();
}
void reference_set(Object* object, std::int32_t index, Object* value) {
    auto array = reference_array(object);
    array->bounds(index);
    if (value && !array->accepts_any_reference) {
        // The component is immutable. Generated objects can use the same
        // hierarchy bitmap as casts, without parsing or hashing a class name
        // on every store. Runtime objects and nested arrays keep Java covariance.
        const auto actual = value->type_id();
        const auto expected = array->component_class_id;
        const bool known = actual && expected && actual <= generated_type_checks.classes
            && expected <= generated_type_checks.classes;
        const bool accepted = known ? generated_assignable(actual, expected)
            : assignable_name(value->type_name(), component(array->descriptor));
        if (!accepted) raise("java/lang/ArrayStoreException");
    }
    array->elements[index].set(value);
}
std::int32_t byte_get(Object* object, std::int32_t index) {
    return require_non_null(object)->array_kind == 'Z' ? array_get<std::uint8_t>(object, index) : array_get<std::int8_t>(object, index);
}
void byte_set(Object* object, std::int32_t index, std::int32_t value) {
    if (require_non_null(object)->array_kind == 'Z') array_set<std::uint8_t>(object, index, value & 1);
    else array_set<std::int8_t>(object, index, std::int8_t(((value & 255) ^ 128) - 128));
}
namespace {
std::u16string utf16_impl(const std::string& text, bool cooperative) {
    std::u16string result;
    std::uint32_t work = 0;
    if (cooperative) safepoint();
    for (std::size_t i = 0; i < text.size();) {
        if (cooperative && (++work & 1023) == 0) safepoint();
        std::uint32_t point = static_cast<unsigned char>(text[i++]);
        unsigned count = 0;
        if (point >= 0xf0 && point <= 0xf4) { point &= 7; count = 3; }
        else if (point >= 0xe0 && point < 0xf0) { point &= 15; count = 2; }
        else if (point >= 0xc2 && point < 0xe0) { point &= 31; count = 1; }
        else if (point >= 0x80) { result += char16_t(0xfffd); continue; }
        bool valid = true;
        for (unsigned n = 0; n < count; ++n) {
            if (i >= text.size() || (static_cast<unsigned char>(text[i]) & 0xc0) != 0x80) { valid = false; break; }
            point = (point << 6) | (static_cast<unsigned char>(text[i++]) & 63);
        }
        if (!valid || point > 0x10ffff || (point >= 0xd800 && point <= 0xdfff)
                || (count == 1 && point < 0x80) || (count == 2 && point < 0x800) || (count == 3 && point < 0x10000))
            point = 0xfffd;
        if (point < 0x10000) result += char16_t(point);
        else { point -= 0x10000; result += char16_t(0xd800 + (point >> 10)); result += char16_t(0xdc00 + (point & 1023)); }
    }
    return result;
}
}
std::u16string utf16(const std::string& text) { return utf16_impl(text, false); }
std::u16string utf16_cooperative(const std::string& text) { return utf16_impl(text, true); }
Object* arguments(int argc, char** argv) {
    LocalRoot<> array(new_array("[Ljava/lang/String;", argc - 1));
    for (int i = 1; i < argc; ++i) {
        LocalRoot<String> argument(allocate<String>(utf16(argv[i])));
        reference_set(array.get(), i - 1, argument.get());
    }
    return array.get();
}
#ifdef _WIN32
Object* arguments(int argc, wchar_t** argv) {
    LocalRoot<> array(new_array("[Ljava/lang/String;", argc - 1));
    for (int i = 1; i < argc; ++i) {
        std::u16string value;
        for (const wchar_t* character = argv[i]; *character; ++character) value += char16_t(*character);
        LocalRoot<String> argument(allocate<String>(std::move(value)));
        reference_set(array.get(), i - 1, argument.get());
    }
    return array.get();
}
#endif
String* as_string(Object* object) {
    if (require_non_null(object)->runtime_kind != RuntimeKind::string) {
        std::string message = std::string(object->type_name()) + " cannot be cast to java.lang.String";
        std::replace(message.begin(), message.end(), '/', '.');
        raise("java/lang/ClassCastException", message.c_str());
    }
    return static_cast<String*>(object);
}
std::u16string to_text(Object* object) {
    if (!object) return u"null";
    if (object->runtime_kind == RuntimeKind::string) return static_cast<String*>(object)->value;
    return utf16(object->type_name());
}
std::u16string to_text(std::int32_t value) { return utf16(std::to_string(value)); }
std::u16string to_text(std::int64_t value) { return utf16(std::to_string(value)); }
template<class T> bool fixed_decimal(T value, std::string& result) {
    const bool negative = std::signbit(value);
    const T absolute = std::abs(value);
    if (absolute >= T(1e7) || (absolute != 0 && absolute < T(1e-3))) return false;
    const std::int32_t integral = static_cast<std::int32_t>(absolute);
    if (T(integral) == absolute) {
        result = (negative ? "-" : "") + std::to_string(integral) + ".0";
        return true;
    }
    // Both integers in this division are exact even in binary32. Below 2^23,
    // the decimal grid is wider than the rounding interval: a matching value
    // has a unique numerator. Trying scales in order finds its shortest fixed
    // decimal without formatting/parsing streams. Other values keep the full
    // converter, including scientific notation and very small fractions.
    std::uint32_t scale = 1;
    for (int places = 1; places <= 6; ++places) {
        scale *= 10;
        const double scaled = static_cast<double>(absolute) * scale;
        if (scaled >= 8388607.0) return false;
        const std::uint32_t digits = static_cast<std::uint32_t>(scaled + .5);
        if (T(digits) / T(scale) != absolute) continue;
        std::string text = std::to_string(digits);
        if (text.size() <= static_cast<std::size_t>(places))
            text.insert(0, static_cast<std::size_t>(places) + 1 - text.size(), '0');
        text.insert(text.size() - places, ".");
        result = (negative ? "-" : "") + text;
        return true;
    }
    return false;
}
template<class T> std::string decimal_text(T value, bool scientific, bool two_digits = false) {
#if JNATIVE_USE_CHARCONV
    char buffer[128];
    std::to_chars_result converted = two_digits
        ? std::to_chars(buffer, buffer + sizeof(buffer), value, std::chars_format::scientific, 1)
        : std::to_chars(buffer, buffer + sizeof(buffer), value,
                scientific ? std::chars_format::scientific : std::chars_format::fixed);
    if (converted.ec != std::errc()) throw std::runtime_error("Number formatting failed");
    return std::string(buffer, converted.ptr);
#else
    // Find the shortest decimal that round-trips in the classic locale. This
    // implementation needs no optional conversion library or global locale change.
    std::string text;
    std::ostringstream output;
    output.imbue(std::locale::classic());
    std::istringstream input;
    input.imbue(std::locale::classic());
    for (int precision = two_digits ? 1 : 0; precision < std::numeric_limits<T>::max_digits10; ++precision) {
        output.str(std::string());
        output.clear();
        output << std::scientific << std::setprecision(precision) << value;
        text = output.str();
        input.str(text);
        input.clear();
        T round_trip = 0;
        input >> round_trip;
        if (two_digits || (!input.fail() && round_trip == value)) break;
    }
    if (scientific) return text;
    const std::size_t exponent_at = text.find('e');
    int point = std::stoi(text.substr(exponent_at + 1)) + 1;
    std::string digits = text.substr(0, exponent_at);
    const bool negative = !digits.empty() && digits[0] == '-';
    if (negative) digits.erase(0, 1);
    const std::size_t dot = digits.find('.');
    if (dot != std::string::npos) digits.erase(dot, 1);
    if (point <= 0) digits = "0." + std::string(static_cast<std::size_t>(-point), '0') + digits;
    else if (static_cast<std::size_t>(point) >= digits.size()) digits.append(static_cast<std::size_t>(point) - digits.size(), '0');
    else digits.insert(static_cast<std::size_t>(point), ".");
    return negative ? "-" + digits : digits;
#endif
}
template<class T> std::u16string floating_text(T value) {
    if (std::isnan(value)) return u"NaN";
    if (std::isinf(value)) return value < 0 ? u"-Infinity" : u"Infinity";
    std::string fixed;
    if (fixed_decimal(value, fixed)) return utf16(fixed);
    std::string text = decimal_text(value, std::abs(value) >= T(1e7)
        || (value != 0 && std::abs(value) < T(1e-3)));
    auto e = text.find('e');
    if (e == std::string::npos) { if (text.find('.') == std::string::npos) text += ".0"; }
    else {
        std::string mantissa = text.substr(0, e);
        if (mantissa.find('.') == std::string::npos) {
            // Java selects at least two significant decimal digits. Re-round
            // rather than appending zero (notably 4.9E-324 and 1.4E-45).
            text = decimal_text(value, true, true);
            e = text.find('e');
            mantissa = text.substr(0, e);
        }
        text = mantissa + "E" + std::to_string(std::stoi(text.substr(e + 1)));
    }
    return utf16(text);
}
std::u16string to_text(float value) { return floating_text(value); }
std::u16string to_text(double value) { return floating_text(value); }
String* concatenate(const std::vector<std::u16string>& parts) {
    std::u16string result;
    for (const auto& part : parts) result += part;
    return allocate<String>(std::move(result));
}
std::int32_t string_char(Object* object, std::int32_t index) {
    auto string = as_string(object);
    if (index < 0 || std::size_t(index) >= string->value.size()) raise("java/lang/StringIndexOutOfBoundsException");
    return string->value[index];
}
bool string_equals(Object* object, Object* other) {
    auto string = as_string(object);
    if (!other || other->runtime_kind != RuntimeKind::string) return false;
    auto second = static_cast<String*>(other);
    if (string == second) return true;
    const auto size = string->value.size();
    if (size != second->value.size()) return false;
    if (size <= 1024) return string->value == second->value;
    LocalRoot<String> first_root(string), second_root(second);
    for (std::size_t from = 0; from < size; from += 1024) {
        safepoint();
        const auto count = std::min<std::size_t>(1024, size - from);
        if (!std::equal(string->value.begin() + from, string->value.begin() + from + count,
                        second->value.begin() + from)) return false;
    }
    return true;
}
std::int32_t string_hash(Object* object) {
    auto string = as_string(object);
    std::int32_t cached = string->cached_hash.get();
    if (cached != 0) return cached;
    std::int32_t result = 0;
    if (string->value.size() <= 1024) {
        for (char16_t c : string->value) result = add(mul(result, std::int32_t(31)), std::int32_t(c));
    } else {
        LocalRoot<String> input(string);
        for (std::size_t from = 0; from < string->value.size(); from += 1024) {
            safepoint();
            const auto end = std::min(from + 1024, string->value.size());
            for (auto i = from; i < end; ++i)
                result = add(mul(result, std::int32_t(31)), std::int32_t(string->value[i]));
        }
    }
    if (result != 0) string->cached_hash.set(result);
    return result;
}
std::int32_t string_compare(Object* first, Object* second) {
    auto a = as_string(first), b = as_string(second);
    for (std::size_t i = 0; i < std::min(a->value.size(), b->value.size()); ++i)
        if (a->value[i] != b->value[i]) return std::int32_t(a->value[i]) - std::int32_t(b->value[i]);
    return std::int32_t(a->value.size()) - std::int32_t(b->value.size());
}
String* substring(Object* object, std::int32_t begin, std::int32_t end) {
    auto string = as_string(object);
    if (begin < 0 || end < begin || std::size_t(end) > string->value.size()) raise("java/lang/StringIndexOutOfBoundsException");
    if (begin == 0 && std::size_t(end) == string->value.size()) return string;
    return allocate<String>(string->value.substr(begin, end - begin));
}
String* string_concat(Object* first, Object* second) {
    auto a = as_string(first), b = as_string(second);
    if (b->value.empty()) return a;
    return allocate<String>(a->value + b->value);
}
void string_from_chars(Object* object, Object* characters) {
    string_from_chars(object, characters, 0, array_length(characters));
}
void string_from_chars(Object* object, Object* characters, std::int32_t offset, std::int32_t count) {
    std::int32_t length = array_length(characters);
    if (offset < 0 || count < 0 || offset > length - count)
        raise("java/lang/StringIndexOutOfBoundsException");
    auto string = as_string(object);
    string->value.resize(count);
    for (std::int32_t i = 0; i < count; ++i) string->value[i] = array_get<std::uint16_t>(characters, offset + i);
}
std::int32_t identity_hash(Object* object) {
    return object ? signed32(static_cast<std::uint32_t>(reinterpret_cast<std::uintptr_t>(object))) : 0;
}
Object* object_string(Object* object) {
    require_non_null(object);
    if (object->runtime_kind == RuntimeKind::string) return object;
    if (object->runtime_kind == RuntimeKind::file_path) return path_string(object);
    if (dynamic_cast<Throwable*>(object)) return allocate<String>(utf16(Thrown(object).what()));
    if (std::string(object->type_name()) == "java/nio/ByteOrder") return byte_order_string(object);
    std::string name = object->type_name();
    std::replace(name.begin(), name.end(), '/', '.');
    std::ostringstream value;
    value << name << '@' << std::hex << std::uint32_t(identity_hash(object));
    return allocate<String>(utf16(value.str()));
}
bool object_equals(Object* object, Object* other) {
    require_non_null(object);
    if (object->runtime_kind == RuntimeKind::string) return string_equals(object, other);
    if (object->runtime_kind == RuntimeKind::file_path) {
        LocalRoot<> first(object), second(other);
        safepoint();
        return path_equals(first.get(), second.get());
    }
    return object == other;
}
std::int32_t object_hash(Object* object) {
    require_non_null(object);
    if (object->runtime_kind == RuntimeKind::string) return string_hash(object);
    // Path hash values are implementation-specific. A constant preserves the
    // equality contract across host-specific case-folding rules.
    if (object->runtime_kind == RuntimeKind::file_path) return 0;
    return identity_hash(object);
}
namespace {
template<class T> void copy_primitive(Array* source, std::int32_t from, Array* target, std::int32_t to, std::int32_t count) {
    auto input = static_cast<PrimitiveArray<T>*>(source);
    auto output = static_cast<PrimitiveArray<T>*>(target);
    if (source == target && to > from) {
        for (std::int32_t i = count; i > 0; --i) output->elements[to + i - 1].set(input->elements[from + i - 1].get());
    } else {
        for (std::int32_t i = 0; i < count; ++i) output->elements[to + i].set(input->elements[from + i].get());
    }
}
}
void array_copy(Object* source, std::int32_t from, Object* target, std::int32_t to, std::int32_t count) {
    const char source_kind = require_non_null(source)->array_kind;
    const char target_kind = require_non_null(target)->array_kind;
    if (!source_kind || source_kind != target_kind) raise("java/lang/ArrayStoreException");
    auto input = static_cast<Array*>(source);
    auto output = static_cast<Array*>(target);
    const bool references = source_kind == 'L';
    if (from < 0 || to < 0 || count < 0 || from > input->length - count || to > output->length - count)
        raise("java/lang/ArrayIndexOutOfBoundsException");
    if (references) {
        if (static_cast<ReferenceArray*>(output)->accepts_any_reference
                || input->descriptor == output->descriptor) {
            auto in = static_cast<ReferenceArray*>(input);
            auto out = static_cast<ReferenceArray*>(output);
            // Object[] accepts every reference. Identical component types have
            // already been checked at the source array's stores.
            if (source == target && to > from) {
                for (std::int32_t i = count; i > 0; --i)
                    out->elements[to + i - 1].set(in->elements[from + i - 1].get());
            } else {
                for (std::int32_t i = 0; i < count; ++i)
                    out->elements[to + i].set(in->elements[from + i].get());
            }
            return;
        }
        if (source == target && to > from) {
            for (std::int32_t i = count; i > 0; --i) reference_set(target, to + i - 1, reference_get(source, from + i - 1));
        } else {
            for (std::int32_t i = 0; i < count; ++i) reference_set(target, to + i, reference_get(source, from + i));
        }
        return;
    }
    switch (source_kind) {
        case 'Z': copy_primitive<std::uint8_t>(input, from, output, to, count); break;
        case 'B': copy_primitive<std::int8_t>(input, from, output, to, count); break;
        case 'C': copy_primitive<std::uint16_t>(input, from, output, to, count); break;
        case 'S': copy_primitive<std::int16_t>(input, from, output, to, count); break;
        case 'I': copy_primitive<std::int32_t>(input, from, output, to, count); break;
        case 'J': copy_primitive<std::int64_t>(input, from, output, to, count); break;
        case 'F': copy_primitive<float>(input, from, output, to, count); break;
        case 'D': copy_primitive<double>(input, from, output, to, count); break;
        default: throw std::logic_error("Invalid array descriptor");
    }
}
void flush(Object* stream) {
    auto output = static_cast<PrintStream*>(require_non_null(stream))->stream;
    NativeRegion native;
    output->flush();
}
Object* array_copy_of(Object* source, std::int32_t length) {
    LocalRoot<> root(require_non_null(source));
    if (!source->array_kind) raise("java/lang/ArrayStoreException");
    auto input = static_cast<Array*>(source);
    LocalRoot<> result(new_array(input->descriptor.c_str(), length));
    array_copy_cooperative(source, 0, result.get(), 0, std::min(length, input->length));
    return result.get();
}
Object* array_clone(Object* source) {
    LocalRoot<> root(require_non_null(source));
    if (!source->array_kind) raise("java/lang/CloneNotSupportedException");
    auto input = static_cast<Array*>(source);
    LocalRoot<> result(new_array(input->descriptor.c_str(), input->length));
    array_copy_cooperative(source, 0, result.get(), 0, input->length);
    return result.get();
}
String* string_trim(Object* object) {
    auto value = as_string(object);
    std::size_t first = 0, last = value->value.size();
    while (first < last && value->value[first] <= 32) ++first;
    while (last > first && value->value[last - 1] <= 32) --last;
    if (first == 0 && last == value->value.size()) return value;
    return allocate<String>(value->value.substr(first, last - first));
}
namespace {
std::mutex property_mutex;
std::unordered_map<std::string, std::pair<bool, std::u16string>> properties;
template<std::size_t N> int unicode_lookup(int code, const UnicodeRange (&ranges)[N]) {
    std::size_t low = 0, high = N;
    while (low < high) {
        std::size_t middle = low + (high - low) / 2;
        if (code < ranges[middle].first) high = middle;
        else if (code > ranges[middle].last) low = middle + 1;
        else return ranges[middle].value;
    }
    return 0;
}
}
int unicode_info(int code) { return unicode_lookup(code, unicode_properties); }
int unicode_case(int code, bool upper) {
    return code + (upper ? unicode_lookup(code, unicode_uppercase) : unicode_lookup(code, unicode_lowercase));
}
Object* system_property(Object* key, Object* fallback) {
    std::string name = utf8(as_string(key)->value);
    if (name.empty()) raise("java/lang/IllegalArgumentException", "Empty property name");
    std::pair<bool, std::u16string> entry;
    bool found = false;
    {
        std::lock_guard<std::mutex> lock(property_mutex);
        auto value = properties.find(name);
        if (value != properties.end()) { entry = value->second; found = true; }
    }
    if (found) return entry.first ? allocate<String>(entry.second) : fallback;
    if (name == "file.separator") return literal(platform::windows_paths() ? u"\\" : u"/");
    if (name == "path.separator") return literal(platform::windows_paths() ? u";" : u":");
    if (name == "line.separator") return literal(platform::windows_paths() ? u"\r\n" : u"\n");
    if (name == "user.dir") return allocate<String>(platform::current_path().u16string());
    if (name == "os.name") return literal(platform::windows_paths() ? u"Windows" : u"Linux");
    if (name == "java.io.tmpdir") {
        const char* path = std::getenv(platform::windows_paths() ? "TEMP" : "TMPDIR");
        return allocate<String>(utf16(path ? path : (platform::windows_paths() ? "." : "/tmp")));
    }
    if (name == "user.home") {
        const char* path = std::getenv(platform::windows_paths() ? "USERPROFILE" : "HOME");
        return path ? allocate<String>(utf16(path)) : fallback;
    }
    return fallback;
}
Object* set_system_property(Object* key, Object* value, bool clear) {
    LocalRoot<> key_root(key), value_root(value);
    std::string name = utf8(as_string(key)->value);
    if (name.empty()) raise("java/lang/IllegalArgumentException", "Empty property name");
    std::u16string text = clear ? std::u16string() : as_string(value)->value;
    LocalRoot<> previous(system_property(key, nullptr));
    bool found = false;
    std::pair<bool, std::u16string> old;
    {
        std::lock_guard<std::mutex> lock(property_mutex);
        auto entry = properties.find(name);
        if (entry != properties.end()) { old = entry->second; found = true; }
        properties[name] = { !clear, text };
    }
    return found ? (old.first ? allocate<String>(old.second) : nullptr) : previous.get();
}
void initialize_runtime() {
    if (const char* interval = std::getenv("JNATIVE_GC_INTERVAL")) {
        char* end = nullptr;
        auto value = std::strtoull(interval, &end, 10);
        if (!*interval || *interval == '-' || *end || value == 0 || value > 1000000000ULL)
            throw std::invalid_argument("JNATIVE_GC_INTERVAL must be between 1 and 1000000000");
        Heap::instance().collection_threshold(static_cast<std::size_t>(value));
    }
    // Initialize allocation-bearing C++ statics before workers can contend for
    // their C++ initialization guards while a collector is waiting for them.
    standard_out();
    standard_error();
    literal(u"");
    byte_order(false);
    for (const auto& entry : types()) {
        int modifiers = entry.first == "java/lang/String" || entry.first == "java/lang/Class" ? 0x11 : 1;
        if (entry.first == "java/lang/Cloneable" || entry.first == "java/io/Serializable"
                || entry.first == "java/lang/Runnable" || entry.first == "java/lang/reflect/Member") modifiers = 0x601;
        register_reflection_type({entry.first, entry.second.parent, entry.second.interfaces, modifiers, false, nullptr, {}, {}, true});
    }
    current_java_thread();
}
}
