package dev.akre.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;

/**
 * A read-only collection interface for {@link Cons}, primarily used as a return type for Cons.reversed()
 *
 * @param <T> the element type
 */
public interface UnmodifiableCons<T> extends Collection<T> {

    /**
     * Reverses the iteration order of this list.
     *
     * @return A view of the list in reversed order.
     */
    UnmodifiableCons<T> reversed();

    /**
     * Returns an iterator that traverses the list in descending order (Head to Tail).
     *
     * @return an iterator in descending order.
     */
    Iterator<T> descendingIterator();

    @Override
    default boolean add(T e) {
        throw new UnsupportedOperationException();
    }

    @Override
    default boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    default boolean addAll(Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    default boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    default boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    default void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    default Object[] toArray() {
        return stream().toArray();
    }

    @Override
    default <U> U[] toArray(U[] a) {
        return stream().toList().toArray(a);
    }

    @Override
    default boolean containsAll(Collection<?> c) {
        return c.stream().allMatch(this::contains);
    }

    @Override
    default Spliterator<T> spliterator() {
        return Spliterators.spliteratorUnknownSize(iterator(), Spliterator.IMMUTABLE | Spliterator.ORDERED | Spliterator.NONNULL);
    }

    default Spliterator<T> descendingSpliterator() {
        return Spliterators.spliteratorUnknownSize(descendingIterator(), Spliterator.IMMUTABLE | Spliterator.ORDERED | Spliterator.NONNULL);
    }

}
