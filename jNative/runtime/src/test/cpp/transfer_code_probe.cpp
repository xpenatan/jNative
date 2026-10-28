#include "jn_runtime.hpp"
#include "jn_buffers.hpp"

// Assembly controls only. No library adapter or benchmark-specific runtime path.
// Kept out of the production library and default build.
extern "C" JNATIVE_NOINLINE void transfer_float_fields(const jnative::OrdinaryField<float>* source,
        unsigned char* target, int count) {
    for (int i = 0; i < count; ++i) {
        float value = source[i].get();
        std::memcpy(target + i * sizeof(value), &value, sizeof(value));
    }
}

extern "C" JNATIVE_NOINLINE void transfer_integer_fields(const jnative::OrdinaryField<std::uint32_t>* source,
        unsigned char* target, int count) {
    for (int i = 0; i < count; ++i) {
        auto value = source[i].get();
        std::memcpy(target + i * sizeof(value), &value, sizeof(value));
    }
}

extern "C" JNATIVE_NOINLINE void transfer_integer_encoded_floats(const std::atomic<std::uint32_t>* source,
        unsigned char* target, int count) {
    for (int i = 0; i < count; ++i) {
        auto bits = source[i].load(std::memory_order_relaxed);
        float value;
        std::memcpy(&value, &bits, sizeof(value));
        std::memcpy(target + i * sizeof(value), &value, sizeof(value));
    }
}

extern "C" JNATIVE_NOINLINE void transfer_float_view(jnative::Object* source,
        unsigned char* target, int count) {
    jnative::PrimitiveArrayView<float> values(source);
    for (int i = 0; i < count; ++i) {
        float value = values.get_unchecked(i);
        std::memcpy(target + i * sizeof(value), &value, sizeof(value));
    }
}

extern "C" JNATIVE_NOINLINE void transfer_borrowed_float_view(jnative::Object* source,
        unsigned char* target, int count) {
    jnative::PrimitiveArrayView<float, true> values(source);
    for (int i = 0; i < count; ++i) {
        float value = values.get_unchecked(i);
        std::memcpy(target + i * sizeof(value), &value, sizeof(value));
    }
}

extern "C" JNATIVE_NOINLINE void transfer_confined_float_view(jnative::Object* source,
        unsigned char* target, int count) {
    jnative::ConfinedArrayView<float> values(source);
    for (int i = 0; i < count; ++i) {
        float value = values.get_unchecked(i);
        std::memcpy(target + i * sizeof(value), &value, sizeof(value));
    }
}

extern "C" JNATIVE_NOINLINE void scalar_ordinary_float_view(jnative::Object* source,
        jnative::Object* target, int count) {
    jnative::PrimitiveArrayView<float> values(source);
    for (int i = 0; i < count; ++i) {
        auto bits = jnative::float_bits(values.get_unchecked(i), false);
        jnative::buffer_write_scalar<std::uint32_t, 'F', false>(target, i,
                static_cast<std::uint64_t>(bits));
    }
}

extern "C" JNATIVE_NOINLINE void scalar_confined_float_view(jnative::Object* source,
        jnative::Object* target, int count) {
    jnative::ConfinedArrayView<float> values(source);
    for (int i = 0; i < count; ++i) {
        auto bits = jnative::float_bits(values.get_unchecked(i), false);
        jnative::buffer_write_scalar<std::uint32_t, 'F', false>(target, i,
                static_cast<std::uint64_t>(bits));
    }
}

extern "C" JNATIVE_NOINLINE void transfer_bounded_float_copy(jnative::Object* source,
        jnative::Object* target, int count) {
    jnative::bounded_primitive_array_copy<float>(source, 0, target, 0, count);
}

extern "C" JNATIVE_NOINLINE void transfer_confined_float_memcpy(const float* source,
        unsigned char* target, int count) {
    for (int i = 0; i < count; ++i) {
        float value;
        std::memcpy(&value, source + i, sizeof(value));
        std::memcpy(target + i * sizeof(value), &value, sizeof(value));
    }
}

extern "C" JNATIVE_NOINLINE void transfer_mixed_float_copy(const float* source,
        jnative::OrdinaryField<float>* target, int count) {
    for (int i = 0; i < count; ++i) target[i].set(source[i]);
}

extern "C" JNATIVE_NOINLINE void transfer_mixed_float_memcpy(const float* source,
        jnative::OrdinaryField<float>* target, int count) {
    for (int i = 0; i < count; ++i) {
        float value;
        std::memcpy(&value, source + i, sizeof(value));
        target[i].set(value);
    }
}
