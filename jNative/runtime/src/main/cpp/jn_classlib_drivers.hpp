#pragma once
#include "jn_classlib.hpp"
#include "jn_arrays.hpp"
#include "jn_codecs.hpp"

namespace jnative {
template<class HasNext, class Next>
std::int64_t stream_count(Object* source, HasNext has_next, Next next) {
    LocalRoot<> input(source);
    std::int64_t count = 0;
    while (has_next(input.get())) {
        next(input.get());
        count = add(count, std::int64_t(1));
        if ((count & 1023) == 0) safepoint();
    }
    return count;
}
template<class HasNext, class Next, class Test, class Value>
std::uint8_t stream_filter(Object* source, Object* predicate, Object* output,
        HasNext has_next, Next next, Test test, Value value) {
    RootFrame<4> roots;
    FrameRoot<> input(roots.slot(0), source), condition(roots.slot(1), predicate),
        result(roots.slot(2), output), candidate(roots.slot(3));
    std::uint32_t work = 0;
    while (has_next(input.get())) {
        candidate.set(next(input.get()));
        if (test(condition.get(), candidate.get())) {
            value.set(result.get(), candidate.get());
            return true;
        }
        if ((++work & 1023) == 0) safepoint();
    }
    return false;
}
template<class HasNext, class Next>
std::int64_t stream_sum(Object* has_next_object, Object* next_object, HasNext has_next, Next next) {
    RootFrame<2> roots;
    FrameRoot<> condition(roots.slot(0), has_next_object), supplier(roots.slot(1), next_object);
    std::int64_t sum = 0;
    std::uint32_t work = 0;
    while (has_next(condition.get())) {
        sum = add(sum, next(supplier.get()));
        if ((++work & 1023) == 0) safepoint();
    }
    return sum;
}
template<class Length, class CharAt, class Test>
std::uint8_t stream_all_match(Object* text, Object* predicate, Length length, CharAt char_at, Test test) {
    RootFrame<2> roots;
    FrameRoot<> input(roots.slot(0), text), condition(roots.slot(1), predicate);
    for (std::int32_t i = 0; i < length(input.get()); i = add(i, 1)) {
        const auto value = char_at(input.get(), i);
        if (!test(condition.get(), value)) return false;
        if ((i & 1023) == 1023) safepoint();
    }
    return true;
}
template<class Update, class State>
void checksum_update(Object* checksum, Object* bytes, std::int32_t offset, std::int32_t length,
                     Update update, State state) {
    RootFrame<2> roots;
    FrameRoot<> receiver(roots.slot(0), checksum), input(roots.slot(1), bytes);
    if (crc32_is_concrete(receiver.get())) {
        state.set(receiver.get(), crc32_update_bytes(state.get(receiver.get()), input.get(), offset, length));
        return;
    }
    auto storage = primitive_array<std::int8_t>(input.get());
    for (std::int32_t i = 0; i < length; ++i) {
        update(receiver.get(), storage->elements[offset + i].get());
        if ((i & 1023) == 1023) safepoint();
    }
}
}
