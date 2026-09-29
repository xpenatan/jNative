#pragma once
#include "jn_classlib.hpp"
#include "jn_arrays.hpp"

namespace jnative {
// The caller roots the immutable source String across each bounded copy.
inline void collection_append_text(std::u16string& output, const std::u16string& value) {
    for (std::size_t from = 0; from < value.size();) {
        const auto count = std::min<std::size_t>(1024, value.size() - from);
        output.append(value, from, count);
        from += count;
        if (from < value.size()) safepoint();
    }
}
template<class Equals> bool collection_equal(Object* a, Object* b, Equals equals) {
    return a == b || (a && equals(a, b));
}
template<class Has, class Next, class Equals, class Remove>
std::uint8_t collection_find(Object* iterator, Object* value, std::uint8_t erase,
        Has has, Next next, Equals equals, Remove remove) {
    RootFrame<3> roots;
    FrameRoot<> it(roots.slot(0), iterator);
    FrameRoot<> wanted(roots.slot(1), value);
    FrameRoot<> element(roots.slot(2));
    std::uint32_t work = 0;
    while (has(it.get())) {
        element.set(next(it.get()));
        if (collection_equal(wanted.get(), element.get(), equals)) {
            if (erase) remove(it.get());
            return true;
        }
        if ((++work & 1023) == 0) safepoint();
    }
    return false;
}
template<class Has, class Next, class Contains, class Add>
std::uint8_t collection_bulk(Object* collection, Object* iterator, std::int32_t mode,
        Has has, Next next, Contains contains, Add append) {
    RootFrame<3> roots;
    FrameRoot<> target(roots.slot(0), collection);
    FrameRoot<> it(roots.slot(1), iterator);
    FrameRoot<> element(roots.slot(2));
    bool changed = false;
    std::uint32_t work = 0;
    while (has(it.get())) {
        element.set(next(it.get()));
        if (!mode) { if (!contains(target.get(), element.get())) return false; }
        else changed |= append(target.get(), element.get()) != 0;
        if ((++work & 1023) == 0) safepoint();
    }
    return mode ? changed : true;
}
template<class Has, class Next, class Contains, class Remove>
std::uint8_t collection_filter(Object* iterator, Object* other, std::uint8_t retain,
        Has has, Next next, Contains contains, Remove remove) {
    RootFrame<3> roots;
    FrameRoot<> it(roots.slot(0), iterator);
    FrameRoot<> collection(roots.slot(1), other);
    FrameRoot<> element(roots.slot(2));
    bool changed = false;
    std::uint32_t work = 0;
    while (has(it.get())) {
        element.set(next(it.get()));
        if ((contains(collection.get(), element.get()) != 0) != (retain != 0)) {
            remove(it.get()); changed = true;
        }
        if ((++work & 1023) == 0) safepoint();
    }
    return changed;
}
template<class Has, class Next, class Remove>
void collection_clear(Object* iterator, Has has, Next next, Remove remove) {
    LocalRoot<> it(iterator);
    std::uint32_t work = 0;
    while (has(it.get())) {
        next(it.get()); remove(it.get());
        if ((++work & 1023) == 0) safepoint();
    }
}
template<class Has, class Next>
std::int32_t collection_copy(Object* iterator, Object* destination, Has has, Next next) {
    RootFrame<3> roots;
    FrameRoot<> it(roots.slot(0), iterator);
    FrameRoot<> output(roots.slot(1), destination);
    FrameRoot<> element(roots.slot(2));
    std::int32_t index = 0;
    while (has(it.get())) {
        element.set(next(it.get()));
        reference_set(output.get(), index, element.get());
        index = add(index, 1);
        if ((index & 1023) == 0) safepoint();
    }
    return index;
}
template<class Has, class Next, class Hash>
std::int32_t collection_hash(Object* iterator, std::int32_t multiplier, std::int32_t seed,
        Has has, Next next, Hash hash) {
    RootFrame<2> roots;
    FrameRoot<> it(roots.slot(0), iterator);
    FrameRoot<> element(roots.slot(1));
    std::uint32_t work = 0;
    while (has(it.get())) {
        element.set(next(it.get()));
        seed = add(mul(seed, multiplier), element.get() ? hash(element.get()) : 0);
        if ((++work & 1023) == 0) safepoint();
    }
    return seed;
}
template<class Has, class Next, class ToString>
Object* collection_string(Object* collection, Object* iterator, Has has, Next next, ToString stringify) {
    RootFrame<4> roots;
    FrameRoot<> self(roots.slot(0), collection);
    FrameRoot<> it(roots.slot(1), iterator);
    FrameRoot<> element(roots.slot(2));
    FrameRoot<> text(roots.slot(3));
    std::u16string output(1, u'[');
    bool first = true;
    std::uint32_t work = 0;
    while (has(it.get())) {
        element.set(next(it.get()));
        if (!first) output += u", ";
        first = false;
        if (element.get() == self.get()) output += u"(this Collection)";
        else {
            text.set(element.get() ? stringify(element.get()) : nullptr);
            if (text.get()) collection_append_text(output, as_string(text.get())->value);
            else output += u"null";
        }
        if ((++work & 1023) == 0) safepoint();
    }
    output += u']';
    return allocate<String>(std::move(output));
}
template<class Size, class Get, class Equals>
std::int32_t list_index(Object* list, Object* value, std::uint8_t reverse, Size size, Get get, Equals equals) {
    RootFrame<3> roots;
    FrameRoot<> input(roots.slot(0), list);
    FrameRoot<> wanted(roots.slot(1), value);
    FrameRoot<> element(roots.slot(2));
    auto index = reverse ? sub(size(input.get()), 1) : 0;
    while (reverse ? index >= 0 : index < size(input.get())) {
        element.set(get(input.get(), index));
        if (collection_equal(wanted.get(), element.get(), equals)) return index;
        index = add(index, reverse ? -1 : 1);
        if ((index & 1023) == 0) safepoint();
    }
    return -1;
}
template<class Has, class Next, class Equals>
std::uint8_t list_equal(Object* first, Object* second, Has has, Next next, Equals equals) {
    RootFrame<4> roots;
    FrameRoot<> a(roots.slot(0), first);
    FrameRoot<> b(roots.slot(1), second);
    FrameRoot<> left(roots.slot(2));
    FrameRoot<> right(roots.slot(3));
    std::uint32_t work = 0;
    while (has(a.get())) {
        left.set(next(a.get())); right.set(next(b.get()));
        if (!collection_equal(left.get(), right.get(), equals)) return false;
        if ((++work & 1023) == 0) safepoint();
    }
    return true;
}
template<class Has, class Next, class Accept>
void iterable_each(Object* iterator, Object* consumer, Has has, Next next, Accept accept) {
    RootFrame<3> roots;
    FrameRoot<> it(roots.slot(0), iterator);
    FrameRoot<> action(roots.slot(1), consumer);
    FrameRoot<> element(roots.slot(2));
    std::uint32_t work = 0;
    while (has(it.get())) {
        element.set(next(it.get())); accept(action.get(), element.get());
        if ((++work & 1023) == 0) safepoint();
    }
}

template<class Has, class Next, class Key, class Value, class Equals, class Remove>
Object* map_scan(Object* iterator, Object* wanted, std::int32_t mode,
        Has has, Next next, Key key, Value value, Equals equals, Remove remove) {
    RootFrame<5> roots;
    FrameRoot<> it(roots.slot(0), iterator);
    FrameRoot<> sought(roots.slot(1), wanted);
    FrameRoot<> entry(roots.slot(2));
    FrameRoot<> candidate(roots.slot(3));
    FrameRoot<> result(roots.slot(4));
    std::uint32_t work = 0;
    while (has(it.get())) {
        entry.set(next(it.get()));
        candidate.set(mode == 1 ? value(entry.get()) : key(entry.get()));
        if (collection_equal(sought.get(), candidate.get(), equals)) {
            if (mode < 2) return entry.get();
            result.set(value(entry.get()));
            if (mode == 3) remove(it.get());
            return result.get();
        }
        if ((++work & 1023) == 0) safepoint();
    }
    return nullptr;
}
template<class Has, class Next, class Key, class Value, class Put>
void map_put_all(Object* map, Object* iterator, Has has, Next next, Key key, Value value, Put put) {
    RootFrame<5> roots;
    FrameRoot<> target(roots.slot(0), map);
    FrameRoot<> it(roots.slot(1), iterator);
    FrameRoot<> entry(roots.slot(2));
    FrameRoot<> k(roots.slot(3));
    FrameRoot<> v(roots.slot(4));
    std::uint32_t work = 0;
    while (has(it.get())) {
        entry.set(next(it.get())); k.set(key(entry.get())); v.set(value(entry.get()));
        put(target.get(), k.get(), v.get());
        if ((++work & 1023) == 0) safepoint();
    }
}
template<class Has, class Next, class Key, class Value, class Get, class Contains, class Equals>
std::uint8_t map_equal(Object* iterator, Object* other, Has has, Next next, Key key, Value value,
        Get get, Contains contains, Equals equals) {
    RootFrame<6> roots;
    FrameRoot<> it(roots.slot(0), iterator);
    FrameRoot<> map(roots.slot(1), other);
    FrameRoot<> entry(roots.slot(2));
    FrameRoot<> k(roots.slot(3));
    FrameRoot<> expected(roots.slot(4));
    FrameRoot<> actual(roots.slot(5));
    std::uint32_t work = 0;
    while (has(it.get())) {
        entry.set(next(it.get())); k.set(key(entry.get())); actual.set(get(map.get(), k.get()));
        expected.set(value(entry.get()));
        if (!collection_equal(expected.get(), actual.get(), equals)) return false;
        if (!actual.get()) { k.set(key(entry.get())); if (!contains(map.get(), k.get())) return false; }
        if ((++work & 1023) == 0) safepoint();
    }
    return true;
}
template<class Has, class Next, class Key, class Value, class ToString>
Object* map_string(Object* map, Object* iterator, Has has, Next next, Key key, Value value, ToString stringify) {
    RootFrame<5> roots;
    FrameRoot<> self(roots.slot(0), map);
    FrameRoot<> it(roots.slot(1), iterator);
    FrameRoot<> entry(roots.slot(2));
    FrameRoot<> part(roots.slot(3));
    FrameRoot<> text(roots.slot(4));
    std::u16string output(1, u'{'); bool first = true; std::uint32_t work = 0;
    while (has(it.get())) {
        entry.set(next(it.get()));
        if (!first) output += u", ";
        first = false;
        part.set(key(entry.get()));
        if (part.get() == self.get()) output += u"(this Map)";
        else { part.set(key(entry.get())); text.set(part.get() ? stringify(part.get()) : nullptr);
            if (text.get()) collection_append_text(output, as_string(text.get())->value);
            else output += u"null"; }
        output += u'=';
        part.set(value(entry.get()));
        if (part.get() == self.get()) output += u"(this Map)";
        else { part.set(value(entry.get())); text.set(part.get() ? stringify(part.get()) : nullptr);
            if (text.get()) collection_append_text(output, as_string(text.get())->value);
            else output += u"null"; }
        if ((++work & 1023) == 0) safepoint();
    }
    output += u'}'; return allocate<String>(std::move(output));
}

template<class Has, class Next, class Add>
void collection_populate(Object* collection, Object* iterator, Has has, Next next, Add append) {
    RootFrame<3> roots;
    FrameRoot<> target(roots.slot(0), collection);
    FrameRoot<> it(roots.slot(1), iterator);
    FrameRoot<> value(roots.slot(2));
    std::uint32_t work = 0;
    while (has(it.get())) {
        value.set(require_non_null(next(it.get()))); append(target.get(), value.get());
        if ((++work & 1023) == 0) safepoint();
    }
}
template<class Add> void set_populate(Object* collection, Object* array, Add append) {
    RootFrame<3> roots;
    FrameRoot<> target(roots.slot(0), collection);
    FrameRoot<> input(roots.slot(1), array);
    FrameRoot<> value(roots.slot(2));
    const auto count = array_length(array);
    for (std::int32_t i = 0; i < count; ++i) {
        value.set(require_non_null(reference_get(input.get(), i)));
        if (!append(target.get(), value.get())) raise("java/lang/IllegalArgumentException", "Duplicate element");
        if ((i & 1023) == 1023) safepoint();
    }
}
template<class Put> void map_populate(Object* map, Object* pairs, Put put) {
    RootFrame<4> roots;
    FrameRoot<> target(roots.slot(0), map);
    FrameRoot<> input(roots.slot(1), pairs);
    FrameRoot<> key(roots.slot(2));
    FrameRoot<> value(roots.slot(3));
    const auto count = array_length(pairs);
    for (std::int32_t i = 0; i < count; i += 2) {
        key.set(require_non_null(reference_get(input.get(), i)));
        value.set(require_non_null(reference_get(input.get(), i + 1)));
        if (put(target.get(), key.get(), value.get())) raise("java/lang/IllegalArgumentException", "Duplicate key");
        if ((i & 1023) == 1022) safepoint();
    }
}
template<class Set> void list_writeback(Object* list, Object* array, Set set) {
    RootFrame<3> roots;
    FrameRoot<> target(roots.slot(0), list);
    FrameRoot<> input(roots.slot(1), array);
    FrameRoot<> value(roots.slot(2));
    const auto count = array_length(array);
    for (std::int32_t i = 0; i < count; ++i) {
        value.set(reference_get(input.get(), i)); set(target.get(), i, value.get());
        if ((i & 1023) == 1023) safepoint();
    }
}
template<class Next> Object* collection_nth(Object* iterator, std::int32_t index, Next next) {
    LocalRoot<> it(iterator);
    while (index-- > 0) { next(it.get()); if ((index & 1023) == 0) safepoint(); }
    return next(it.get());
}
template<class Has, class Next, class Key, class Value>
void map_snapshot(Object* iterator, Object* array, Has has, Next next, Key key, Value value) {
    RootFrame<4> roots;
    FrameRoot<> it(roots.slot(0), iterator);
    FrameRoot<> output(roots.slot(1), array);
    FrameRoot<> entry(roots.slot(2));
    FrameRoot<> part(roots.slot(3));
    std::int32_t index = 0;
    while (has(it.get())) {
        entry.set(next(it.get())); part.set(key(entry.get())); reference_set(output.get(), index++, part.get());
        part.set(value(entry.get())); reference_set(output.get(), index++, part.get());
        if ((index & 1023) == 0) safepoint();
    }
}
}
