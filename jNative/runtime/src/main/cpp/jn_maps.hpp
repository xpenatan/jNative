#pragma once
#include "jn_classlib.hpp"
#include <algorithm>

namespace jnative {
inline std::int32_t hashmap_capacity(std::int32_t capacity) {
    std::int32_t size = 1;
    while (size < capacity && size < (1 << 30)) size <<= 1;
    return size;
}

template<class Buckets, class Head, class Tail, class Count, class Changes, class Load,
        class Hash, class Key, class Value, class Next, class Before, class After>
struct HashMapState {
    Buckets buckets; Head head; Tail tail; Count count; Changes changes; Load load;
    Hash hash; Key key; Value value; Next next; Before before; After after;
};

template<class State, class Hash, class Equals>
Object* hashmap_locate(Object* receiver, Object* key, State fields, Hash hash, Equals equals) {
    RootFrame<3> roots;
    FrameRoot<> map(roots.slot(0), receiver);
    FrameRoot<> wanted(roots.slot(1), key);
    FrameRoot<> node(roots.slot(2));
    std::int32_t code = hash(map.get(), wanted.get());
    Object* buckets = fields.buckets.get(map.get());
    node.set(reference_get(buckets, code & (array_length(buckets) - 1)));
    std::uint32_t work = 0;
    while (node.get()) {
        if (fields.hash.get(node.get()) == code
                && equals(map.get(), wanted.get(), fields.key.get(node.get()))) return node.get();
        node.set(fields.next.get(node.get()));
        if ((++work & 1023) == 0) safepoint();
    }
    return nullptr;
}

template<class Hash, class Equals, class... Fields>
Object* hashmap_find(Object* receiver, Object* key, Hash hash, Equals equals, Fields... fields) {
    return hashmap_locate(receiver, key, HashMapState<Fields...>{fields...}, hash, equals);
}

template<class State, class MakeBuckets>
void hashmap_grow(Object* receiver, State fields, MakeBuckets make_buckets) {
    RootFrame<5> roots;
    FrameRoot<> map(roots.slot(0), receiver);
    FrameRoot<> source(roots.slot(1), fields.buckets.get(map.get()));
    FrameRoot<> grown(roots.slot(2));
    FrameRoot<> node(roots.slot(3));
    FrameRoot<> next(roots.slot(4));
    std::int32_t size = array_length(source.get());
    if (size >= (1 << 30)) return;
    grown.set(make_buckets(size << 1));
    std::int32_t mask = array_length(grown.get()) - 1;
    std::uint32_t work = 0;
    for (std::int32_t i = 0; i < size; ++i) {
        node.set(reference_get(source.get(), i));
        while (node.get()) {
            next.set(fields.next.get(node.get()));
            std::int32_t slot = fields.hash.get(node.get()) & mask;
            fields.next.set(node.get(), reference_get(grown.get(), slot));
            reference_set(grown.get(), slot, node.get());
            node.set(next.get());
            if ((++work & 1023) == 0) safepoint();
        }
        if ((++work & 1023) == 0) safepoint();
    }
    fields.buckets.set(map.get(), grown.get());
}

template<class Hash, class Equals, class MakeNode, class MakeBuckets, class... Fields>
Object* hashmap_put(Object* receiver, Object* key, Object* value, Hash hash, Equals equals,
        MakeNode make_node, MakeBuckets make_buckets, Fields... accessors) {
    auto fields = HashMapState<Fields...>{accessors...};
    RootFrame<7> roots;
    FrameRoot<> map(roots.slot(0), receiver);
    FrameRoot<> wanted(roots.slot(1), key);
    FrameRoot<> replacement(roots.slot(2), value);
    FrameRoot<> node(roots.slot(3));
    FrameRoot<> previous(roots.slot(4));
    FrameRoot<> buckets(roots.slot(5));
    FrameRoot<> old(roots.slot(6));
    node.set(hashmap_locate(map.get(), wanted.get(), fields, hash, equals));
    if (node.get()) {
        old.set(fields.value.get(node.get()));
        fields.value.set(node.get(), replacement.get());
        return old.get();
    }
    std::int32_t code = hash(map.get(), wanted.get());
    buckets.set(fields.buckets.get(map.get()));
    std::int32_t slot = code & (array_length(buckets.get()) - 1);
    previous.set(reference_get(buckets.get(), slot));
    node.set(make_node(code, wanted.get(), replacement.get(), previous.get()));
    reference_set(buckets.get(), slot, node.get());
    // The source facade rereads the current table after allocation.
    node.set(reference_get(fields.buckets.get(map.get()), slot));
    previous.set(fields.tail.get(map.get()));
    fields.before.set(node.get(), previous.get());
    if (!previous.get()) fields.head.set(map.get(), node.get());
    else fields.after.set(previous.get(), node.get());
    fields.tail.set(map.get(), node.get());
    fields.count.set(map.get(), add(fields.count.get(map.get()), 1));
    fields.changes.set(map.get(), add(fields.changes.get(map.get()), 1));
    if (static_cast<float>(fields.count.get(map.get()))
            > static_cast<float>(array_length(fields.buckets.get(map.get()))) * fields.load.get(map.get()))
        hashmap_grow(map.get(), fields, make_buckets);
    return nullptr;
}

template<class Hash, class Equals, class... Fields>
Object* hashmap_remove(Object* receiver, Object* key, Hash hash, Equals equals, Fields... accessors) {
    auto fields = HashMapState<Fields...>{accessors...};
    RootFrame<7> roots;
    FrameRoot<> map(roots.slot(0), receiver);
    FrameRoot<> wanted(roots.slot(1), key);
    FrameRoot<> previous(roots.slot(2));
    FrameRoot<> node(roots.slot(3));
    FrameRoot<> before(roots.slot(4));
    FrameRoot<> after(roots.slot(5));
    FrameRoot<> next(roots.slot(6));
    std::int32_t code = hash(map.get(), wanted.get());
    Object* buckets = fields.buckets.get(map.get());
    std::int32_t slot = code & (array_length(buckets) - 1);
    node.set(reference_get(buckets, slot));
    std::uint32_t work = 0;
    while (node.get()) {
        if (fields.hash.get(node.get()) == code
                && equals(map.get(), wanted.get(), fields.key.get(node.get()))) {
            next.set(fields.next.get(node.get()));
            if (!previous.get()) reference_set(fields.buckets.get(map.get()), slot, next.get());
            else fields.next.set(previous.get(), next.get());
            before.set(fields.before.get(node.get()));
            after.set(fields.after.get(node.get()));
            if (!before.get()) fields.head.set(map.get(), after.get());
            else fields.after.set(before.get(), after.get());
            if (!after.get()) fields.tail.set(map.get(), before.get());
            else fields.before.set(after.get(), before.get());
            fields.count.set(map.get(), sub(fields.count.get(map.get()), 1));
            fields.changes.set(map.get(), add(fields.changes.get(map.get()), 1));
            return fields.value.get(node.get());
        }
        previous.set(node.get());
        node.set(fields.next.get(node.get()));
        if ((++work & 1023) == 0) safepoint();
    }
    return nullptr;
}

template<class Buckets, class Head, class Tail, class Count, class Changes>
void hashmap_clear(Object* receiver, Buckets buckets, Head head, Tail tail, Count count, Changes changes) {
    RootFrame<2> roots;
    FrameRoot<> map(roots.slot(0), receiver);
    FrameRoot<> table(roots.slot(1), buckets.get(map.get()));
    tail.set(map.get(), nullptr);
    head.set(map.get(), nullptr);
    std::int32_t size = array_length(table.get());
    for (std::int32_t i = 0; i < size; ++i) {
        reference_set(table.get(), i, nullptr);
        if ((i & 1023) == 1023) safepoint();
    }
    count.set(map.get(), 0);
    changes.set(map.get(), add(changes.get(map.get()), 1));
}

template<class Left, class Right>
Object* treemap_extreme(Object* initial, std::uint8_t last, Left left, Right right) {
    RootFrame<2> roots;
    FrameRoot<> node(roots.slot(0), initial);
    FrameRoot<> next(roots.slot(1));
    std::uint32_t work = 0;
    while (node.get()) {
        next.set(last ? right.get(node.get()) : left.get(node.get()));
        if (!next.get()) return node.get();
        node.set(next.get());
        if ((++work & 1023) == 0) safepoint();
    }
    return nullptr;
}

template<class Root, class Count, class Changes, class Left, class Right, class Height, class Key, class Value>
struct TreeMapState {
    Root root; Count count; Changes changes; Left left; Right right; Height height; Key key; Value value;

    std::int32_t node_height(Object* node) const { return node ? height.get(node) : 0; }
    void refresh(Object* node) const {
        height.set(node, add(1, std::max(node_height(left.get(node)), node_height(right.get(node)))));
    }
    Object* rotate_left(Object* initial) const {
        RootFrame<2> roots;
        FrameRoot<> node(roots.slot(0), initial);
        FrameRoot<> next(roots.slot(1), right.get(node.get()));
        right.set(node.get(), left.get(next.get()));
        left.set(next.get(), node.get());
        refresh(node.get());
        refresh(next.get());
        return next.get();
    }
    Object* rotate_right(Object* initial) const {
        RootFrame<2> roots;
        FrameRoot<> node(roots.slot(0), initial);
        FrameRoot<> next(roots.slot(1), left.get(node.get()));
        left.set(node.get(), right.get(next.get()));
        right.set(next.get(), node.get());
        refresh(node.get());
        refresh(next.get());
        return next.get();
    }
    Object* balance(Object* initial) const {
        RootFrame<2> roots;
        FrameRoot<> node(roots.slot(0), initial);
        FrameRoot<> child(roots.slot(1));
        refresh(node.get());
        std::int32_t difference = sub(node_height(left.get(node.get())), node_height(right.get(node.get())));
        if (difference > 1) {
            child.set(left.get(node.get()));
            if (node_height(left.get(child.get())) < node_height(right.get(child.get())))
                left.set(node.get(), rotate_left(child.get()));
            return rotate_right(node.get());
        }
        if (difference < -1) {
            child.set(right.get(node.get()));
            if (node_height(right.get(child.get())) < node_height(left.get(child.get())))
                right.set(node.get(), rotate_right(child.get()));
            return rotate_left(node.get());
        }
        return node.get();
    }
    template<class Compare>
    Object* find(Object* receiver, Object* key_object, Compare compare) const {
        RootFrame<3> roots;
        FrameRoot<> map(roots.slot(0), receiver);
        FrameRoot<> wanted(roots.slot(1), key_object);
        FrameRoot<> node(roots.slot(2), root.get(map.get()));
        std::uint32_t work = 0;
        while (node.get()) {
            std::int32_t order = compare(map.get(), wanted.get(), key.get(node.get()));
            if (!order) return node.get();
            node.set(order < 0 ? left.get(node.get()) : right.get(node.get()));
            if ((++work & 1023) == 0) safepoint();
        }
        return nullptr;
    }
    template<class Compare>
    Object* insert(Object* receiver, Object* initial, Object* added_object, Compare compare) const {
        RootFrame<4> roots;
        FrameRoot<> map(roots.slot(0), receiver);
        FrameRoot<> node(roots.slot(1), initial);
        FrameRoot<> added(roots.slot(2), added_object);
        FrameRoot<> child(roots.slot(3));
        if (!node.get()) return added.get();
        safepoint();
        if (compare(map.get(), key.get(added.get()), key.get(node.get())) < 0) {
            child.set(insert(map.get(), left.get(node.get()), added.get(), compare));
            left.set(node.get(), child.get());
        }
        else {
            child.set(insert(map.get(), right.get(node.get()), added.get(), compare));
            right.set(node.get(), child.get());
        }
        return balance(node.get());
    }
    template<class Compare>
    Object* erase(Object* receiver, Object* initial, Object* key_object, Compare compare) const {
        RootFrame<6> roots;
        FrameRoot<> map(roots.slot(0), receiver);
        FrameRoot<> node(roots.slot(1), initial);
        FrameRoot<> wanted(roots.slot(2), key_object);
        FrameRoot<> child(roots.slot(3));
        FrameRoot<> next(roots.slot(4));
        FrameRoot<> successor_key(roots.slot(5));
        safepoint();
        std::int32_t order = compare(map.get(), wanted.get(), key.get(node.get()));
        if (order < 0) {
            child.set(erase(map.get(), left.get(node.get()), wanted.get(), compare));
            left.set(node.get(), child.get());
        }
        else if (order > 0) {
            child.set(erase(map.get(), right.get(node.get()), wanted.get(), compare));
            right.set(node.get(), child.get());
        }
        else {
            if (!left.get(node.get())) return right.get(node.get());
            if (!right.get(node.get())) return left.get(node.get());
            next.set(treemap_extreme(right.get(node.get()), false, left, right));
            successor_key.set(key.get(next.get()));
            child.set(erase(map.get(), right.get(node.get()), successor_key.get(), compare));
            right.set(node.get(), child.get());
            left.set(next.get(), left.get(node.get()));
            right.set(next.get(), right.get(node.get()));
            node.set(next.get());
        }
        return balance(node.get());
    }
};

template<class Compare, class... Fields>
Object* treemap_find(Object* receiver, Object* key, Compare compare, Fields... fields) {
    return TreeMapState<Fields...>{fields...}.find(receiver, key, compare);
}

template<class Compare, class MakeNode, class... Fields>
Object* treemap_put(Object* receiver, Object* key, Object* value, Compare compare,
        MakeNode make_node, Fields... accessors) {
    auto fields = TreeMapState<Fields...>{accessors...};
    RootFrame<7> roots;
    FrameRoot<> map(roots.slot(0), receiver);
    FrameRoot<> wanted(roots.slot(1), key);
    FrameRoot<> replacement(roots.slot(2), value);
    FrameRoot<> existing(roots.slot(3));
    FrameRoot<> root(roots.slot(4));
    FrameRoot<> added(roots.slot(5));
    FrameRoot<> result(roots.slot(6));
    compare(map.get(), wanted.get(), wanted.get());
    existing.set(fields.find(map.get(), wanted.get(), compare));
    if (existing.get()) {
        result.set(fields.value.get(existing.get()));
        fields.value.set(existing.get(), replacement.get());
        return result.get();
    }
    root.set(fields.root.get(map.get()));
    added.set(make_node(wanted.get(), replacement.get()));
    result.set(fields.insert(map.get(), root.get(), added.get(), compare));
    fields.root.set(map.get(), result.get());
    fields.count.set(map.get(), add(fields.count.get(map.get()), 1));
    fields.changes.set(map.get(), add(fields.changes.get(map.get()), 1));
    return nullptr;
}

template<class Compare, class... Fields>
Object* treemap_remove(Object* receiver, Object* key, Compare compare, Fields... accessors) {
    auto fields = TreeMapState<Fields...>{accessors...};
    RootFrame<5> roots;
    FrameRoot<> map(roots.slot(0), receiver);
    FrameRoot<> wanted(roots.slot(1), key);
    FrameRoot<> existing(roots.slot(2));
    FrameRoot<> previous(roots.slot(3));
    FrameRoot<> result(roots.slot(4));
    existing.set(fields.find(map.get(), wanted.get(), compare));
    if (!existing.get()) return nullptr;
    previous.set(fields.value.get(existing.get()));
    result.set(fields.erase(map.get(), fields.root.get(map.get()), wanted.get(), compare));
    fields.root.set(map.get(), result.get());
    fields.count.set(map.get(), sub(fields.count.get(map.get()), 1));
    fields.changes.set(map.get(), add(fields.changes.get(map.get()), 1));
    return previous.get();
}

template<class Compare, class... Fields>
Object* treemap_higher(Object* receiver, Object* key, Compare compare, Fields... accessors) {
    auto fields = TreeMapState<Fields...>{accessors...};
    RootFrame<4> roots;
    FrameRoot<> map(roots.slot(0), receiver);
    FrameRoot<> wanted(roots.slot(1), key);
    FrameRoot<> node(roots.slot(2), fields.root.get(map.get()));
    FrameRoot<> result(roots.slot(3));
    std::uint32_t work = 0;
    while (node.get()) {
        if (compare(map.get(), wanted.get(), fields.key.get(node.get())) < 0) {
            result.set(node.get());
            node.set(fields.left.get(node.get()));
        }
        else node.set(fields.right.get(node.get()));
        if ((++work & 1023) == 0) safepoint();
    }
    return result.get();
}

template<class Root, class Count, class Changes>
void treemap_clear(Object* receiver, Root root, Count count, Changes changes) {
    LocalRoot<> map(receiver);
    root.set(map.get(), nullptr);
    count.set(map.get(), 0);
    changes.set(map.get(), add(changes.get(map.get()), 1));
}
}
