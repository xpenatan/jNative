#pragma once
#include "jn_abi.h"
#include "jn_runtime.hpp"

namespace jnative {
// Borrow immutable metadata and primitive storage while the input handle stays
// alive. These operations neither allocate Java objects nor call Java code.
// The heap is nonmoving; a live handle protects the complete backing object
// during collection, so native callers need no managed-state transition.
// Returned pointers and views must not outlive that handle. Native code remains
// responsible for coordinating direct-buffer writes with other buffer users.
template<class T> class NativeArrayStorage {
    PrimitiveArray<T>* array_;
    void range(std::int32_t offset, std::int32_t count) const {
        if (offset < 0 || count < 0 || offset > array_->length - count)
            throw std::invalid_argument("Native array range is out of bounds");
    }
public:
    explicit NativeArrayStorage(jn_handle handle) {
        auto object = Heap::instance().resolve(handle);
        if (!object || object->array_kind != PrimitiveArrayKind<T>::value)
            throw std::invalid_argument("Incorrect native array type");
        array_ = static_cast<PrimitiveArray<T>*>(object);
    }
    std::int32_t size() const { return array_->length; }
    void copy_to(T* destination, std::int32_t offset, std::int32_t count) const {
        range(offset, count);
        if (!destination && count) throw std::invalid_argument("Null native array destination");
        for (std::int32_t i = 0; i < count; ++i)
            destination[i] = array_->elements[offset + i].get();
    }
    void copy_from(const T* source, std::int32_t offset, std::int32_t count) const {
        range(offset, count);
        if (!source && count) throw std::invalid_argument("Null native array source");
        for (std::int32_t i = 0; i < count; ++i)
            array_->elements[offset + i].set(source[i]);
    }
};

inline const std::u16string& native_string_storage(jn_handle handle) {
    auto object = Heap::instance().resolve(handle);
    if (!object || object->runtime_kind != RuntimeKind::string)
        throw std::invalid_argument("Expected a non-null native String handle");
    return static_cast<String*>(object)->value;
}

inline std::uint8_t* native_buffer_storage(jn_handle handle, std::int32_t count, bool write) {
    auto object = Heap::instance().resolve(handle);
    if (!object || object->runtime_kind != RuntimeKind::buffer)
        throw std::invalid_argument("Expected a non-null native ByteBuffer handle");
    auto buffer = static_cast<ByteBuffer*>(object);
    if (!buffer->direct || buffer->element != 'B')
        throw std::invalid_argument("Native access requires a direct byte buffer");
    if (write && buffer->read_only)
        throw std::invalid_argument("Native buffer is read only");
    const auto position = buffer->position.get();
    if (count < 0 || count > buffer->limit.get() - position)
        throw std::invalid_argument("Native buffer range is out of bounds");
    return buffer->storage->empty() ? nullptr : buffer->storage->data() + buffer->offset + position;
}
}
