package java.util;

public interface Queue<E> extends Collection<E> {
    boolean offer(E value);

    E remove();

    E poll();

    E element();

    E peek();
}
