package dev.akre.util;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class StreamUtils {
    @SuppressWarnings("unchecked")
    public static <K, V> Collector<? super Map.Entry<K, ? extends V>, ?, Map<K, V>> toMap() {
        return Collectors.collectingAndThen(
                Collectors.toList(),
                list -> Map.ofEntries(list.toArray(Map.Entry[]::new))
        );
    }

    public static <K,V,R> Function<Map.Entry<K,V>,R> mapEntry(BiFunction<K,V,R> f) {
        return e -> f.apply(e.getKey(), e.getValue());
    }

}
