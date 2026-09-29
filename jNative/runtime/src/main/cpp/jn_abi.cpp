#include "jn_abi.hpp"
#include <algorithm>
#include <cstring>
#include <cstdlib>

namespace jnative {
namespace {
struct Gate {
    ::jnative::platform::Mutex mutex;
    ::jnative::platform::Condition changed;
    bool accepting = true;
    std::size_t active = 0, attached = 0;
};
Gate& gate() { static Gate value; return value; }
struct Closed : std::exception {};
struct ErrorState {
    jn_status status = JN_OK;
    char message[2048]{};
    jn_handle exception = 0;
    ~ErrorState() { if (exception) Heap::instance().release(exception); }
};
ErrorState& error_state();
void describe(jn_status status, const char* text) noexcept {
    auto& error = error_state();
    error.status = status;
    std::strncpy(error.message, text ? text : "", sizeof(error.message) - 1);
    error.message[sizeof(error.message) - 1] = 0;
}
struct ForeignAttachment {
    std::unique_ptr<ThreadAttachment> owner;
    ~ForeignAttachment() { reset(); }
    void reset() {
        if (!owner) return;
        owner.reset();
        auto& g = gate();
        std::lock_guard<::jnative::platform::Mutex> lock(g.mutex);
        --g.attached;
        g.changed.notify_all();
    }
};
struct LocalState {
    unsigned depth = 0;
    ErrorState error;
    ForeignAttachment foreign;
};
LocalState& local_state() {
    // TLS cleanup can release handles and detach; its key must die before both owners.
    (void)Heap::instance();
    (void)gate();
    static platform::ThreadLocal<LocalState> state;
    return state.get();
}
ErrorState& error_state() { return local_state().error; }
void clear_error(ErrorState& error) {
    if (error.exception) Heap::instance().release(::jnative::take_value(error.exception, 0));
    error.status = JN_OK;
    error.message[0] = 0;
}
void input(const void* data, uint64_t count) {
    if (count > INT32_MAX || (!data && count)) throw std::invalid_argument("Invalid ABI input buffer or length");
}
void empty_output(jn_owned_buffer* output) {
    require_output(output);
    if (output->data || output->size) throw std::invalid_argument("Output buffer must be empty");
}
void copy_output(const void* data, std::size_t bytes, jn_owned_buffer* output) {
    empty_output(output);
    auto copy = static_cast<uint8_t*>(std::malloc(bytes ? bytes : 1));
    if (!copy) throw std::bad_alloc();
    if (bytes) std::memcpy(copy, data, bytes);
    output->data = copy;
    output->size = bytes;
}
Object* resolve(jn_handle handle) {
    // Invalid or expired handles are caller errors, not generated-bytecode errors.
    try { return Heap::instance().resolve(handle); }
    catch (const std::logic_error&) { throw std::invalid_argument("Expired native handle"); }
}
String* string_value(jn_handle handle) {
    auto value = dynamic_cast<String*>(resolve(handle));
    if (!value) throw std::invalid_argument("Expected a non-null String handle");
    return value;
}
Array* byte_array(jn_handle handle) {
    auto array = dynamic_cast<Array*>(resolve(handle));
    if (!array || array->descriptor != "[B") throw std::invalid_argument("Expected a non-null byte[] handle");
    return array;
}
}

ApiScope::ApiScope() {
    auto& g = gate();
    std::lock_guard<::jnative::platform::Mutex> lock(g.mutex);
    if (!g.accepting && !local_state().depth) throw Closed{};
    ++g.active;
    ++local_state().depth;
}
ApiScope::~ApiScope() {
    auto& g = gate();
    std::lock_guard<::jnative::platform::Mutex> lock(g.mutex);
    --local_state().depth;
    --g.active;
    g.changed.notify_all();
}
ManagedEntry::ManagedEntry() {
    previous_ = current_thread()->state;
    if (previous_ != ThreadState::running_managed) Heap::instance().leave_native();
}
ManagedEntry::~ManagedEntry() {
    if (previous_ != ThreadState::running_managed) Heap::instance().enter_native(previous_);
}
ManagedAccess::ManagedAccess() {
    previous_ = current_thread()->state;
    if (previous_ != ThreadState::running_managed) Heap::instance().leave_native();
}
ManagedAccess::~ManagedAccess() {
    if (previous_ != ThreadState::running_managed) Heap::instance().enter_native(previous_);
}
jn_status capture_abi_error() noexcept {
    jn_clear_error();
    try { throw; }
    catch (const Closed&) { describe(JN_SHUTTING_DOWN, "Native callback gate is closed"); }
    catch (const Thrown& thrown) {
        describe(JN_JAVA_EXCEPTION, thrown.what());
        try { error_state().exception = Heap::instance().retain(thrown.object(), true); }
        catch (...) { describe(JN_NATIVE_FAILURE, "Unable to retain Java exception"); }
    }
    catch (const std::invalid_argument& failure) { describe(JN_INVALID_ARGUMENT, failure.what()); }
    catch (const std::exception& failure) { describe(JN_NATIVE_FAILURE, failure.what()); }
    catch (...) { describe(JN_NATIVE_FAILURE, "Unknown native failure"); }
    return error_state().status;
}
namespace {
void rethrow_error(ErrorState& error) {
    if (error.status == JN_OK) return;
    OwnedHandle exception(::jnative::take_value(error.exception, 0));
    std::string message = error.message;
    clear_error(error);
    if (exception.get()) throw_object(exception.get());
    raise("java/lang/RuntimeException", message.c_str());
}
Object* consume_reference(jn_handle handle, const char* descriptor,
                         std::initializer_list<jn_handle> borrowed, ErrorState& error) {
    if (handle && std::find(borrowed.begin(), borrowed.end(), handle) != borrowed.end())
        raise("java/lang/IllegalStateException", "Native reference return must be retained, not borrowed");
    OwnedHandle result(handle);
    rethrow_error(error);
    return check_cast(resolve(handle), descriptor);
}
}
void rethrow_native_error() { rethrow_error(error_state()); }
Object* import_reference(jn_handle handle, const char* descriptor,
                         std::initializer_list<jn_handle> borrowed) {
    return consume_reference(handle, descriptor, borrowed, error_state());
}
NativeCallError::NativeCallError() : state_(&error_state()) {
    clear_error(*static_cast<ErrorState*>(state_));
}
void NativeCallError::check() const { rethrow_error(*static_cast<ErrorState*>(state_)); }
Object* NativeCallError::import_reference(jn_handle handle, const char* descriptor,
                                         std::initializer_list<jn_handle> borrowed) const {
    return consume_reference(handle, descriptor, borrowed, *static_cast<ErrorState*>(state_));
}
}

extern "C" {
const char* jn_last_error(void) { return jnative::error_state().message; }
jn_status jn_error_status(void) { return jnative::error_state().status; }
void jn_clear_error(void) {
    jnative::clear_error(jnative::error_state());
}
jn_handle jn_take_exception(void) { return ::jnative::take_value(jnative::error_state().exception, 0); }
jn_status jn_retain(jn_handle borrowed, jn_handle* owned) {
    return jnative::abi_call([&] {
        jnative::require_output(owned);
        *owned = jnative::Heap::instance().duplicate(borrowed, true);
    });
}
jn_status jn_release(jn_handle owned) {
    return jnative::Heap::instance().release_checked(owned) ? JN_OK : JN_INVALID_ARGUMENT;
}
jn_status jn_set_exception(jn_handle exception) {
    jn_status status = jnative::abi_call([&] {
        auto object = jnative::resolve(exception);
        if (!object || !jnative::instance_of(object, "java/lang/Throwable"))
            throw std::invalid_argument("Expected a non-null Throwable handle");
        jnative::error_state().exception = jnative::Heap::instance().duplicate(exception, true);
        jnative::describe(JN_JAVA_EXCEPTION, jnative::Thrown(object).what());
    });
    return status;
}
jn_status jn_string_from_utf8(const uint8_t* data, uint64_t size, jn_handle* owned) {
    return jnative::abi_call([&] {
        jnative::require_output(owned); jnative::input(data, size);
        std::string bytes;
        if (size) bytes.assign(reinterpret_cast<const char*>(data), std::size_t(size));
        std::u16string value = jnative::utf16(bytes);
        if (jnative::utf8(value) != bytes) throw std::invalid_argument("Malformed UTF-8 input");
        jnative::LocalRoot<jnative::String> string(jnative::allocate<jnative::String>(value));
        *owned = jnative::Heap::instance().retain(string.get(), true);
    });
}
jn_status jn_string_from_utf16(const uint16_t* data, uint64_t units, jn_handle* owned) {
    return jnative::abi_call([&] {
        jnative::require_output(owned); jnative::input(data, units);
        std::u16string value;
        value.reserve(std::size_t(units));
        for (uint64_t i = 0; i < units; ++i) value += char16_t(data[i]);
        jnative::LocalRoot<jnative::String> string(jnative::allocate<jnative::String>(value));
        *owned = jnative::Heap::instance().retain(string.get(), true);
    });
}
jn_status jn_string_copy_utf8(jn_handle string, jn_owned_buffer* output) {
    return jnative::abi_call([&] {
        const auto bytes = jnative::utf8(jnative::string_value(string)->value);
        jnative::copy_output(bytes.data(), bytes.size(), output);
    });
}
jn_status jn_string_copy_utf16(jn_handle string, jn_owned_buffer* output) {
    return jnative::abi_call([&] {
        const auto& value = jnative::string_value(string)->value;
        jnative::copy_output(value.data(), value.size() * sizeof(char16_t), output);
    });
}
jn_status jn_bytes_from_copy(const uint8_t* data, uint64_t size, jn_handle* owned) {
    return jnative::abi_call([&] {
        jnative::require_output(owned); jnative::input(data, size);
        jnative::LocalRoot<> array(jnative::new_array("[B", int32_t(size)));
        for (uint64_t i = 0; i < size; ++i) jnative::byte_set(array.get(), int32_t(i), data[i]);
        *owned = jnative::Heap::instance().retain(array.get(), true);
    });
}
jn_status jn_bytes_copy(jn_handle array, jn_owned_buffer* output) {
    return jnative::abi_call([&] {
        auto value = jnative::byte_array(array);
        std::vector<uint8_t> bytes(std::size_t(value->length));
        for (int32_t i = 0; i < value->length; ++i) bytes[std::size_t(i)] = uint8_t(jnative::byte_get(value, i));
        jnative::copy_output(bytes.data(), bytes.size(), output);
    });
}
jn_status jn_bytes_write(jn_handle array, uint64_t offset, const uint8_t* data, uint64_t size) {
    return jnative::abi_call([&] {
        jnative::input(data, size);
        auto value = jnative::byte_array(array);
        if (offset > uint64_t(value->length) || size > uint64_t(value->length) - offset)
            throw std::invalid_argument("Byte array write out of bounds");
        for (uint64_t i = 0; i < size; ++i) jnative::byte_set(value, int32_t(offset + i), data[i]);
    });
}
void jn_buffer_free(jn_owned_buffer* buffer) {
    if (buffer) { std::free(buffer->data); buffer->data = nullptr; buffer->size = 0; }
}
jn_status jn_attach_thread(void) {
    try {
        jnative::ApiScope scope;
        jn_clear_error();
        if (jnative::current_thread()) return JN_OK;
        auto owner = ::jnative::make_owned<jnative::ThreadAttachment>();
        jnative::Heap::instance().enter_native(jnative::ThreadState::in_native);
        jnative::local_state().foreign.owner = std::move(owner);
        auto& g = jnative::gate();
        std::lock_guard<::jnative::platform::Mutex> lock(g.mutex);
        ++g.attached;
        return JN_OK;
    } catch (...) { return jnative::capture_abi_error(); }
}
jn_status jn_detach_thread(void) {
    try {
        if (jnative::local_state().depth || (jnative::current_thread() && (!jnative::local_state().foreign.owner
                || jnative::current_thread()->state != jnative::ThreadState::in_native)))
            throw std::invalid_argument("Cannot detach a Java-owned thread or an active callback");
        jn_clear_error();
        jnative::local_state().foreign.reset();
        return JN_OK;
    } catch (...) { return jnative::capture_abi_error(); }
}
jn_status jn_shutdown(void) {
    try {
        if (jnative::local_state().depth) throw std::invalid_argument("Cannot shut down from an active callback");
        jn_clear_error();
        auto& g = jnative::gate();
        { std::lock_guard<::jnative::platform::Mutex> lock(g.mutex); g.accepting = false; }
        std::unique_ptr<jnative::NativeRegion> blocking;
        if (jnative::current_thread() && jnative::current_thread()->state == jnative::ThreadState::running_managed)
            blocking = ::jnative::make_owned<jnative::NativeRegion>();
        std::unique_lock<::jnative::platform::Mutex> lock(g.mutex);
        g.changed.wait(lock, [&] { return g.active == 0 && g.attached <= (jnative::local_state().foreign.owner ? 1u : 0u); });
        return JN_OK;
    } catch (...) { return jnative::capture_abi_error(); }
}
jn_status jn_try_shutdown(void) {
    try {
        jn_clear_error();
        auto& g = jnative::gate();
        std::lock_guard<jnative::platform::Mutex> lock(g.mutex);
        if (g.active || g.attached || jnative::java_threads_active() || !jnative::Heap::instance().try_close()) {
            jnative::describe(JN_BUSY, "Runtime still has active calls, attached threads, Java workers or owned native handles");
            return JN_BUSY;
        }
        g.accepting = false;
        return JN_OK;
    } catch (...) { return jnative::capture_abi_error(); }
}
}
