package dev.akre.util;

import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class StreamUtils {
    @SuppressWarnings("unchecked")
    public static <K, V> Collector<Map.Entry<K, V>, ?, Map<K, V>> toMap() {
        return Collectors.collectingAndThen(Collectors.toList(), l -> Map.ofEntries(l.toArray(new Map.Entry[0])));
    }
}
