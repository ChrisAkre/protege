package dev.akre.util;

import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StreamUtilsTest {

    @Test
    void testToMap_happyPath() {
        Map<String, Integer> result = Stream.of(
                new AbstractMap.SimpleEntry<>("A", 1),
                new AbstractMap.SimpleEntry<>("B", 2),
                new AbstractMap.SimpleEntry<>("C", 3)
        ).collect(StreamUtils.<String, Integer>toMap());

        assertThat(result)
                .hasSize(3)
                .containsEntry("A", 1)
                .containsEntry("B", 2)
                .containsEntry("C", 3);
    }

    @Test
    void testToMap_emptyStream() {
        Map<String, Integer> result = Stream.<Map.Entry<String, Integer>>empty()
                .collect(StreamUtils.<String, Integer>toMap());

        assertThat(result).isEmpty();
    }

    @Test
    void testToMap_duplicateKeys() {
        Stream<Map.Entry<String, Integer>> stream = Stream.of(
                new AbstractMap.SimpleEntry<>("A", 1),
                new AbstractMap.SimpleEntry<>("A", 2)
        );

        assertThatThrownBy(() -> stream.collect(StreamUtils.<String, Integer>toMap()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testMapEntry_singleEntry() {
        Map.Entry<String, Integer> entry = new AbstractMap.SimpleEntry<>("key", 10);
        String result = StreamUtils.<String, Integer, String>mapEntry((k, v) -> k + ":" + v).apply(entry);

        assertThat(result).isEqualTo("key:10");
    }

    @Test
    void testMapEntry_streamTransformation() {
        Stream<Map.Entry<String, Integer>> stream = Stream.of(
                new AbstractMap.SimpleEntry<>("one", 1),
                new AbstractMap.SimpleEntry<>("two", 2)
        );

        Stream<String> resultStream = stream.map(StreamUtils.<String, Integer, String>mapEntry((k, v) -> k + "=" + v));

        assertThat(resultStream).containsExactly("one=1", "two=2");
    }
}
