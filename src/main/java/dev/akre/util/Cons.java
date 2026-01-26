package dev.akre.util;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * An immutable, singly-linked list implementation (Lisp-style cons cells).
 * <p>
 * This structure is optimized for stack-based traversal where efficient
 * head insertion and tail sharing are required. It is <strong>not</strong>
 * suitable for random access operations.
 * <p>
 * Iteration order via {@link #iterator()} and {@link #stream()} is from
 * Tail to Head (Oldest to Newest), effectively reversing the stack.
 * Use {@link #descendingIterator()} or {@link #descendingStream()} for
 * Head to Tail (Newest to Oldest) traversal.
 *
 * @param <T> The type of elements held in this collection.
 * @link <a href="https://en.wikipedia.org/wiki/Greenspun%27s_tenth_rule">Greenspun's tenth rule</a>
 */
public record Cons<T>(T head, Cons<T> tail) implements UnmodifiableCons<T> {

    /**
     * The singleton empty list.
     */
    public static final Cons<?> NIL = new Cons<>(null, null);

    /**
     * Constructor for internal use.
     *
     * @param head The head element.
     * @param tail The tail list.
     * @throws IllegalArgumentException if this is not NIL and tail is null.
     */
    public Cons {
        if (tail == null && NIL != null) {
            throw new IllegalArgumentException("must be a non-empty cons or NIL");
        }
    }

    /**
     * Returns the empty list.
     *
     * @param <T> The type of elements.
     * @return The empty list.
     */
    @SuppressWarnings("unchecked")
    public static <T> Cons<T> nil() {
        return (Cons<T>) NIL;
    }

    @Override
    public boolean isEmpty() {
        return this == NIL;
    }

    /**
     * Prepends an element to this list, creating a new head.
     *
     * @param t The element to prepend.
     * @return A new Cons with {@code t} as the head and {@code this} as the tail.
     */
    public Cons<T> cons(T t) {
        return new Cons<>(t, this);
    }

    /**
     * Creates a list from the given elements.
     *
     * @param elements The elements to include.
     * @param <T>      The type of elements.
     * @return A list containing the elements in the order specified.
     */
    @SafeVarargs
    public static <T> Cons<T> of(T... elements) {
        Cons<T> acc = nil();
        for (int i = elements.length - 1; i >= 0; i--) {
            acc = new Cons<>(elements[i], acc);
        }
        return acc;
    }

    /**
     * Creates a list from a {@link List}.
     *
     * @param values The values to include.
     * @param <T>    The type of elements.
     * @return A list containing the values.
     */
    public static <T> Cons<T> copyOf(List<T> values) {
        Cons<T> acc = nil();
        for (int i = values.size() - 1; i >= 0; i--) {
            acc = new Cons<>(values.get(i), acc);
        }
        return acc;
    }

    @Override
    public int size() {
        int count = 0;
        Cons<T> curr = this;
        while (!curr.isEmpty()) {
            count++;
            curr = curr.tail();
        }
        return count;
    }

    @Override
    public boolean contains(Object o) {
        Cons<T> curr = this;
        while (!curr.isEmpty()) {
            if (Objects.equals(o, curr.head)) return true;
            curr = curr.tail();
        }
        return false;
    }

    @Override
    public Iterator<T> iterator() {
        return stream().iterator();
    }

    @Override
    public Iterator<T> descendingIterator() {
        return new Iterator<>() {
            private Cons<T> current = Cons.this;

            public boolean hasNext() {
                return !current.isEmpty();
            }

            public T next() {
                if (!hasNext()) throw new NoSuchElementException();
                T value = current.head();
                current = current.tail();
                return value;
            }
        };
    }

    /**
     * Returns a stream of elements in descending order (Head to Tail).
     *
     * @return A stream.
     */
    public Stream<T> descendingStream() {
        return StreamSupport.stream(descendingSpliterator(), false);
    }

    @Override
    public Stream<T> stream() {
        List<T> buffer = new ArrayList<>();
        descendingIterator().forEachRemaining(buffer::add);
        Collections.reverse(buffer);
        return buffer.stream();
    }

    @Override
    public String toString() {
        return stream().map(T::toString).collect(Collectors.joining(", ", "[", "]"));
    }

    @Override
    public UnmodifiableCons<T> reversed() {
        return new UnmodifiableCons<>() {
            @Override
            public UnmodifiableCons<T> reversed() {
                return Cons.this;
            }

            @Override
            public Iterator<T> iterator() {
                return Cons.this.descendingIterator();
            }

            @Override
            public Iterator<T> descendingIterator() {
                return Cons.this.iterator();
            }

            @Override
            public int size() {
                return Cons.this.size();
            }

            @Override
            public boolean isEmpty() {
                return Cons.this.isEmpty();
            }

            @Override
            public boolean contains(Object o) {
                return Cons.this.contains(o);
            }

            @Override
            public Stream<T> stream() {
                return Cons.this.descendingStream();
            }

            @Override
            public String toString() {
                return stream().map(String::valueOf).collect(Collectors.joining(", ", "[", "]"));
            }
        };
    }

}
