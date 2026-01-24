package dev.akre.util;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Immutable Linked List
 *
 * @link <a href="https://en.wikipedia.org/wiki/Greenspun%27s_tenth_rule">Greenspun's tenth rule</a>
 */
public record Cons<T>(T head, Cons<T> tail) implements Iterable<T> {
    public Cons {
        if (tail == null && NIL != null) {
            throw new IllegalArgumentException("must be a non-empty cons or NIL");
        }
    }

    public static final Cons<?> NIL = new Cons<>(null, null);

    @SuppressWarnings("unchecked")
    public static <T> Cons<T> nil() {
        return (Cons<T>) NIL;
    }

    public boolean isEmpty() {
        return this == NIL;
    }

    public Cons<T> cons(T t) {
        return new Cons<>(t, this);
    }

    @SafeVarargs
    public static <T> Cons<T> of(T... elements) {
        Cons<T> acc = nil();
        for (int i = elements.length - 1; i >= 0; i--) {
            acc = new Cons<>(elements[i], acc);
        }
        return acc;
    }

    public static <T> Cons<T> copyOf(List<T> values) {
        Cons<T> acc = nil();
        for (int i = values.size() - 1; i >= 0; i--) {
            acc = new Cons<>(values.get(i), acc);
        }
        return acc;
    }

    @Override
    public Spliterator<T> spliterator() {
        return Spliterators.spliteratorUnknownSize(iterator(), Spliterator.IMMUTABLE | Spliterator.ORDERED | Spliterator.NONNULL);
    }

    public Stream<T> stream() {
        return streamBuilder(this).build();
    }

    private static <T> Stream.Builder<T> streamBuilder(Cons<T> cons) {
        return switch (cons) {
            case Cons<T> c when c.isEmpty() -> Stream.builder();
            case Cons<T> c -> streamBuilder(c.tail()).add(c.head);
        };
    }

    @Override
    public Iterator<T> iterator() {
        return stream().iterator();
    }

    @Override
    public String toString() {
        return stream().map(T::toString).collect(Collectors.joining(", ", "[", "]"));
    }
}
