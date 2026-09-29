package com.github.xpenatan.jnative.classlib.java.util;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.util.*;

@SubstituteClass("java.util.ArrayDeque")
public class ArrayDeque<E> extends AbstractCollection<E> implements Deque<E> {
    private Object[] values;
    private int head;
    private int size;
    private int changes;

    public ArrayDeque() {
        this(16);
    }

    public ArrayDeque(int capacity) {
        values = new Object[Math.max(1, capacity)];
    }

    public ArrayDeque(Collection<? extends E> source) {
        this(source.size());
        addAll(source);
    }

    public int size() {
        return size;
    }

    private int offset(int index) {
        return (head + index) % values.length;
    }

    private void grow() {
        if(size < values.length) return;
        Object[] grown = new Object[Math.max(2, values.length * 2)];
        int first = Math.min(size, values.length - head);
        System.arraycopy(values, head, grown, 0, first);
        System.arraycopy(values, 0, grown, first, size - first);
        values = grown;
        head = 0;
    }

    public void addFirst(E value) {
        Objects.requireNonNull(value);
        grow();
        head = head == 0 ? values.length - 1 : head - 1;
        values[head] = value;
        ++size;
        ++changes;
    }

    public void addLast(E value) {
        Objects.requireNonNull(value);
        grow();
        values[offset(size++)] = value;
        ++changes;
    }

    public boolean add(E value) {
        addLast(value);
        return true;
    }

    public boolean offer(E value) {
        return offerLast(value);
    }

    public boolean offerFirst(E value) {
        addFirst(value);
        return true;
    }

    public boolean offerLast(E value) {
        addLast(value);
        return true;
    }

    @SuppressWarnings("unchecked")
    public E peekFirst() {
        return size == 0 ? null : (E)values[head];
    }

    @SuppressWarnings("unchecked")
    public E peekLast() {
        return size == 0 ? null : (E)values[offset(size - 1)];
    }

    public E pollFirst() {
        E value = peekFirst();
        if(size != 0) {
            values[head] = null;
            head = offset(1);
            --size;
            ++changes;
        }
        return value;
    }

    public E pollLast() {
        E value = peekLast();
        if(size != 0) {
            values[offset(--size)] = null;
            ++changes;
        }
        return value;
    }

    public E removeFirst() {
        if(size == 0) throw new NoSuchElementException();
        return pollFirst();
    }

    public E removeLast() {
        if(size == 0) throw new NoSuchElementException();
        return pollLast();
    }

    public E getFirst() {
        if(size == 0) throw new NoSuchElementException();
        return peekFirst();
    }

    public E getLast() {
        if(size == 0) throw new NoSuchElementException();
        return peekLast();
    }

    public E remove() {
        return removeFirst();
    }

    public E poll() {
        return pollFirst();
    }

    public E peek() {
        return peekFirst();
    }

    public E element() {
        return getFirst();
    }

    public void push(E value) {
        addFirst(value);
    }

    public E pop() {
        return removeFirst();
    }

    public void clear() {
        Arrays.fill(values, null);
        size = 0;
        head = 0;
        ++changes;
    }

    public Iterator<E> iterator() {
        return new Iterator<E>() {
            private int cursor;
            private int expected = changes;
            private int last = -1;

            private void check() {
                if(changes != expected) throw new ConcurrentModificationException();
            }

            public boolean hasNext() {
                return cursor < size;
            }

            @SuppressWarnings("unchecked")
            public E next() {
                check();
                if(!hasNext()) throw new NoSuchElementException();
                last = cursor++;
                return (E)values[offset(last)];
            }

            public void remove() {
                check();
                if(last < 0) throw new IllegalStateException();
                int removed = offset(last);
                int tail = offset(size - 1);
                if(removed < tail) {
                    System.arraycopy(values, removed + 1, values, removed, tail - removed);
                }
                else if(removed > tail) {
                    System.arraycopy(values, removed + 1, values, removed, values.length - removed - 1);
                    values[values.length - 1] = values[0];
                    System.arraycopy(values, 1, values, 0, tail);
                }
                --size;
                values[tail] = null;
                cursor = last;
                last = -1;
                expected = ++changes;
            }
        };
    }
}
