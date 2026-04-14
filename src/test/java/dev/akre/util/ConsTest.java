package dev.akre.util;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsTest {

    @Test
    void testConstructorValidation() {
        assertThatThrownBy(() -> new Cons<>(null, Cons.nil()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("must provide a non-null value");

        assertThatThrownBy(() -> new Cons<>(1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("must provide a non-null value");

        // NIL is special, it's allowed to have nulls
        assertThat(Cons.NIL.head()).isNull();
        assertThat(Cons.NIL.tail()).isNull();
    }

    @Test
    void testNilAndIsEmpty() {
        Cons<Integer> nil = Cons.nil();
        assertThat(nil).isSameAs(Cons.NIL);
        assertThat(nil.isEmpty()).isTrue();
    }

    @Test
    void testCons() {
        Cons<Integer> list = Cons.<Integer>nil().cons(1);
        assertThat(list.isEmpty()).isFalse();
        assertThat(list.head()).isEqualTo(1);
        assertThat(list.tail()).isSameAs(Cons.NIL);

        Cons<Integer> list2 = list.cons(2);
        assertThat(list2.head()).isEqualTo(2);
        assertThat(list2.tail()).isSameAs(list);
    }

    @Test
    void testOf() {
        Cons<Integer> list = Cons.of(1, 2, 3);
        // Cons.of uses copyOf, which uses cons(t) in a loop over the input list.
        // Input: [1, 2, 3]
        // loop 1: acc = nil.cons(1) -> [1]
        // loop 2: acc = [1].cons(2) -> [2, 1]
        // loop 3: acc = [2, 1].cons(3) -> [3, 2, 1]
        // So head is 3, tail.head is 2, tail.tail.head is 1.
        assertThat(list.head()).isEqualTo(3);
        assertThat(list.tail().head()).isEqualTo(2);
        assertThat(list.tail().tail().head()).isEqualTo(1);
        assertThat(list.tail().tail().tail().isEmpty()).isTrue();
    }

    @Test
    void testCopyOf() {
        List<Integer> source = Arrays.asList(1, 2, 3);
        Cons<Integer> list = Cons.copyOf(source);
        assertThat(list.head()).isEqualTo(3);
        assertThat(list.tail().head()).isEqualTo(2);
        assertThat(list.tail().tail().head()).isEqualTo(1);

        // Verify it returns the same instance if already a Cons
        Cons<Integer> sameList = Cons.copyOf(list);
        assertThat(sameList).isSameAs(list);
    }

    @Test
    void testSize() {
        assertThat(Cons.nil().size()).isEqualTo(0);
        assertThat(Cons.of(1, 2, 3).size()).isEqualTo(3);
    }

    @Test
    void testContains() {
        Cons<Integer> list = Cons.of(1, 2, 3);
        assertThat(list.contains(1)).isTrue();
        assertThat(list.contains(2)).isTrue();
        assertThat(list.contains(3)).isTrue();
        assertThat(list.contains(4)).isFalse();
        assertThat(Cons.nil().contains(1)).isFalse();
    }

    @Test
    void testToString() {
        assertThat(Cons.nil().toString()).isEqualTo("[]");
        // Iteration order is Oldest to Newest, so [1, 2, 3]
        assertThat(Cons.of(1, 2, 3).toString()).isEqualTo("[1, 2, 3]");
    }

    @Test
    void testIteratorAndStream() {
        Cons<Integer> list = Cons.of(1, 2, 3);
        // iterator() and stream() are Oldest to Newest
        assertThat(list).containsExactly(1, 2, 3);
        assertThat(list.stream()).containsExactly(1, 2, 3);

        assertThat(Cons.nil().iterator().hasNext()).isFalse();
        assertThat(Cons.nil().stream()).isEmpty();
    }

    @Test
    void testDescendingIteratorAndStream() {
        Cons<Integer> list = Cons.of(1, 2, 3);
        // descendingIterator() and descendingStream() are Newest to Oldest
        List<Integer> descending = new java.util.ArrayList<>();
        list.descendingIterator().forEachRemaining(descending::add);
        assertThat(descending).containsExactly(3, 2, 1);
        assertThat(list.descendingStream()).containsExactly(3, 2, 1);

        assertThat(list.descendingIterator().hasNext()).isTrue();
        var it = Cons.nil().descendingIterator();
        assertThat(it.hasNext()).isFalse();
        assertThatThrownBy(it::next).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void testReversedView() {
        Cons<Integer> list = Cons.of(1, 2, 3);
        UnmodifiableCons<Integer> reversed = list.reversed();

        // Normal iteration is Oldest to Newest: [1, 2, 3]
        // Reversed view iteration should be [3, 2, 1]
        assertThat(reversed).containsExactly(3, 2, 1);
        assertThat(reversed.stream()).containsExactly(3, 2, 1);
        assertThat(reversed.size()).isEqualTo(3);
        assertThat(reversed.isEmpty()).isFalse();
        assertThat(reversed.contains(1)).isTrue();
        assertThat(reversed.toString()).isEqualTo("[3, 2, 1]");

        // Double reversal should return original
        assertThat(reversed.reversed()).isSameAs(list);

        // Test descending iteration on reversed view (should be Oldest to Newest)
        List<Integer> descendingReversed = new java.util.ArrayList<>();
        reversed.descendingIterator().forEachRemaining(descendingReversed::add);
        assertThat(descendingReversed).containsExactly(1, 2, 3);
    }
}
