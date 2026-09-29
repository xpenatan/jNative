#pragma once

namespace jnative {

inline std::int64_t random_scramble(std::int64_t seed) {
    return static_cast<std::int64_t>((std::uint64_t(seed) ^ UINT64_C(0x5DEECE66D))
        & UINT64_C(0xFFFFFFFFFFFF));
}

inline std::int64_t random_advance(std::int64_t seed) {
    return static_cast<std::int64_t>((std::uint64_t(seed) * UINT64_C(0x5DEECE66D) + 11)
        & UINT64_C(0xFFFFFFFFFFFF));
}

inline std::int32_t random_bits(std::int64_t seed, std::int32_t bits) {
    return signed32(static_cast<std::uint32_t>(unsigned_shift(seed, sub(48, bits))));
}

inline std::int64_t random_uniqueSeed(std::int64_t previous) {
    return signed64(std::uint64_t(previous) * UINT64_C(1181783497276652981));
}

// Callbacks are generated virtual-call adapters. Keep these helpers in managed
// thread state and root receivers across callbacks, which may allocate or throw.
// Templates accept plain callbacks without allocating managed suppliers.
template<class Next> std::int32_t random_nextInt(Object* random, Next next) {
    LocalRoot<> receiver(require_non_null(random));
    return next(receiver.get(), 32);
}

template<class Next> std::int32_t random_nextInt(Object* random, std::int32_t bound, Next next) {
    if (bound <= 0) raise("java/lang/IllegalArgumentException", "bound must be positive");
    LocalRoot<> receiver(require_non_null(random));
    std::int32_t bits = next(receiver.get(), 31);
    const std::int32_t mask = bound - 1;
    if ((bound & mask) == 0)
        return static_cast<std::int32_t>(shift_right(std::int64_t(bound) * bits, 31));
    std::int32_t value = remainder(bits, bound);
    while (add(sub(bits, value), mask) < 0) {
        safepoint();
        bits = next(receiver.get(), 31);
        value = remainder(bits, bound);
    }
    return value;
}

template<class Next> std::int64_t random_nextLong(Object* random, Next next) {
    LocalRoot<> receiver(require_non_null(random));
    const std::int64_t first = next(receiver.get(), 32);
    const std::int64_t second = next(receiver.get(), 32);
    // Java sign-extends the second 32-bit value before adding it.
    return add(shift_left(first, 32), second);
}

template<class Next> std::uint8_t random_nextBoolean(Object* random, Next next) {
    LocalRoot<> receiver(require_non_null(random));
    return next(receiver.get(), 1) != 0;
}

template<class Next> float random_nextFloat(Object* random, Next next) {
    LocalRoot<> receiver(require_non_null(random));
    return static_cast<float>(next(receiver.get(), 24)) / 16777216.0f;
}

template<class Next> double random_nextDouble(Object* random, Next next) {
    LocalRoot<> receiver(require_non_null(random));
    const std::int64_t first = next(receiver.get(), 26);
    const std::int64_t second = next(receiver.get(), 27);
    return static_cast<double>(add(shift_left(first, 27), second)) * (1.0 / 9007199254740992.0);
}

template<class NextInt> void random_nextBytes(Object* random, Object* bytes, NextInt nextInt) {
    LocalRoot<> receiver(require_non_null(random));
    LocalRoot<> output(bytes);
    const std::int32_t length = array_length(output.get());
    std::int32_t index = 0;
    while (index < length) {
        if ((index & 1023) == 0) safepoint();
        std::int32_t value = nextInt(receiver.get());
        const std::int32_t remaining = length - index;
        const std::int32_t count = remaining < 4 ? remaining : 4;
        // Reacquire storage after the callback; retain ordinary element access.
        for (std::int32_t i = 0; i < count; ++i) {
            const std::int32_t byte = value & 255;
            array_set<std::int8_t>(output.get(), index++,
                static_cast<std::int8_t>(byte < 128 ? byte : byte - 256));
            value = shift_right(value, 8);
        }
    }
}

}
