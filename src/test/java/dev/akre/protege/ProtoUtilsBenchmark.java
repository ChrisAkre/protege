package dev.akre.protege;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.stream.Collectors;
import static java.util.function.Predicate.not;

public class ProtoUtilsBenchmark {

    @Test
    public void benchmark() {
        String[] inputs = {
            "test_string",
            "another_test_string",
            "yet_another_long_test_string_with_many_parts",
            "simple",
            "CamelCase",
            "snake_case",
            "mixed_Case_TEST",
            "__leading_underscore",
            "trailing_underscore__",
            "__double__underscore__"
        };

        // Warmup
        for (int i = 0; i < 20000; i++) {
            for (String s : inputs) {
                toPascalCaseOriginal(s);
                ProtoUtils.toPascalCase(s);
            }
        }

        // Benchmark Original
        long start = System.nanoTime();
        for (int i = 0; i < 200000; i++) {
            for (String s : inputs) {
                toPascalCaseOriginal(s);
            }
        }
        long durationOriginal = System.nanoTime() - start;

        // Benchmark Optimized
        start = System.nanoTime();
        for (int i = 0; i < 200000; i++) {
            for (String s : inputs) {
                ProtoUtils.toPascalCase(s);
            }
        }
        long durationOptimized = System.nanoTime() - start;

        System.out.println("Original: " + durationOriginal / 1_000_000.0 + " ms");
        System.out.println("Optimized: " + durationOptimized / 1_000_000.0 + " ms");
        System.out.printf("Improvement: %.2fx%n", (double) durationOriginal / durationOptimized);
    }

    public static String toPascalCaseOriginal(String s) {
        return s == null ? null : Arrays.stream(s.split("_"))
                .filter(not(String::isEmpty))
                .map(ProtoUtils::capitalize)
                .collect(Collectors.joining(""));
    }
}
