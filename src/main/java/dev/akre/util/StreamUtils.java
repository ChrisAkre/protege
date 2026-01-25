//package dev.akre.util;
//
//import java.util.List;
//import java.util.Map;
//import java.util.stream.Collector;
//import java.util.stream.Collectors;
//import java.util.stream.Stream;
//
//public class StreamUtils {
////    @SuppressWarnings("unchecked")
////    public static <K, V> Collector<Map.Entry<K, V>, ?, Map<K, V>> toMap() {
////        return Collectors.collectingAndThen(Collectors.toList(), l -> Map.ofEntries(l.toArray(new Map.Entry[0])));
////    }
////
////    public static <T> Stream<T> interleave(List<T> l, T t) {
////        return l.stream()
////                .flatMap(v -> Stream.of(v, t))
////                .limit(Math.max(0, l.size() * 2 - 1));
////    }
////
////    public static <T> Stream<T> interleave(Stream<T> s, T t) {
////        return interleave(s.toList(), t);
////    }
//
////    public static <T> Gatherer<T, ?, T> interleaver(T t) {
////        return Gatherer.ofSequential(
////                () -> new boolean[]{true}, // State: isFirst
////                (isFirst, element, downstream) -> {
////                    if (!isFirst[0]) downstream.push(t);
////                    isFirst[0] = false;
////                    return downstream.push(element);
////                }
////        );
////    }
//}
