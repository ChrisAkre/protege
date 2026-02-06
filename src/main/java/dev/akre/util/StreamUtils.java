package dev.akre.util;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Utility methods for working with Java Streams.
 */
public class StreamUtils {
    /**
     * Returns a collector that accumulates elements into an unmodifiable map.
     *
     * @param <K> the type of keys
     * @param <V> the type of values
     * @return a collector that accumulates elements into an unmodifiable map
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Collector<? super Map.Entry<K, ? extends V>, ?, Map<K, V>> toMap() {
        return Collectors.collectingAndThen(
                Collectors.toList(),
                list -> Map.ofEntries(list.toArray(Map.Entry[]::new))
        );
    }

    /**
     * Returns a function that applies a bi-function to the key and value of a map entry.
     *
     * @param f   the bi-function to apply
     * @param <K> the type of keys
     * @param <V> the type of values
     * @param <R> the type of the result
     * @return a function that applies a bi-function to the key and value of a map entry
     */
    public static <K,V,R> Function<Map.Entry<K,V>,R> mapEntry(BiFunction<K,V,R> f) {
        return e -> f.apply(e.getKey(), e.getValue());
    }

}
