package dev.akre.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;

interface UnmodifiableCons<T> extends Collection<T> {
    UnmodifiableCons<T> reversed();

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
