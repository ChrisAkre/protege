package dev.akre.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;

/**
 * A read-only collection interface for {@link Cons}, primarily used as a return type for Cons.reversed()
 */
public interface UnmodifiableCons<T> extends Collection<T> {

    /**
     * Returns a view of this list in reversed order.
     *
     * @return a reversed view of the list
     */
    UnmodifiableCons<T> reversed();

    /**
     * Returns an iterator over the elements in this list in descending order (Head to Tail).
     *
     * @return an iterator in descending order
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

    /**
     * Returns a Spliterator over the elements in this list in descending order (Head to Tail).
     *
     * @return a spliterator in descending order
     */
    default Spliterator<T> descendingSpliterator() {
        return Spliterators.spliteratorUnknownSize(descendingIterator(), Spliterator.IMMUTABLE | Spliterator.ORDERED | Spliterator.NONNULL);
    }

}
