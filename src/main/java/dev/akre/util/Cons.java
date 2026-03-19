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
 * Head to Tail (Newest to Oldest) traversal in classic Lisp style.
 *
 * @param <T> element type
 * @param head the head element of the cons cell
 * @param tail the tail cons cell
 * @see <a href="https://en.wikipedia.org/wiki/Greenspun%27s_tenth_rule">Greenspun's tenth rule</a>
 */
public record Cons<T>(T head, Cons<T> tail) implements UnmodifiableCons<T> {

    public static final Cons<?> NIL = new Cons<>(null, null);

    /**
     * Compact constructor for the Cons record.
     */
    public Cons {
        if ((head == null || tail == null) && NIL != null) {
            throw new IllegalArgumentException("must provide a non-null value");
        }
    }

    /**
     * Returns the empty nil instance.
     * @param <T> The element type
     * @return The nil instance
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
     * @param t The element to prepend
     * @return A new Cons instance
     */
    public Cons<T> cons(T t) {
        return new Cons<>(t, this);
    }

    /**
     * Creates a new Cons list containing the provided elements.
     * @param elements The elements to include
     * @param <T> The element type
     * @return A new Cons instance
     */
    @SafeVarargs
    public static <T> Cons<T> of(T... elements) {
        return copyOf(Arrays.asList(elements));
    }

    /**
     * Creates a copy of a collection as a cons list. The iteration order of the resulting list
     * will match the iteration order of the original collection.
     * <p>
     * Note that a reversed cons list will be iterated using {@link #descendingIterator()}
     * and thus will be copied using the classic "reverse a linked list" implementation.
     * @param values The iterable to copy
     * @param <T> The element type
     * @return A new Cons instance containing the copied values
     */
    public static <T> Cons<T> copyOf(Iterable<T> values) {
        if (values instanceof Cons<T> c) {
            // already unmodifiable
            return c;
        }
        Cons<T> acc = nil();
        for (T t : values) {
            acc = acc.cons(t);
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
     * @return A stream in descending order
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
        return stream().map(String::valueOf).collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * Returns a reversed read-only view of the collection. To be able to append elements to this collection, use Cons.copyOf.
     */
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
