package dev.akre.protege;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.stream.Collectors;
import static java.util.function.Predicate.not;

public class ProtoUtilsBenchmark {

    @Test
    public void benchmarkPascalCase() {
        String[] inputs = getInputs();

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

        System.out.println("toPascalCase Original: " + durationOriginal / 1_000_000.0 + " ms");
        System.out.println("toPascalCase Optimized: " + durationOptimized / 1_000_000.0 + " ms");
        System.out.printf("toPascalCase Improvement: %.2fx%n", (double) durationOriginal / durationOptimized);
    }

    @Test
    public void benchmarkCamelCase() {
        String[] inputs = getInputs();

        // Warmup
        for (int i = 0; i < 20000; i++) {
            for (String s : inputs) {
                toCamelCaseOriginal(s);
                ProtoUtils.toCamelCase(s);
            }
        }

        // Benchmark Original
        long start = System.nanoTime();
        for (int i = 0; i < 200000; i++) {
            for (String s : inputs) {
                toCamelCaseOriginal(s);
            }
        }
        long durationOriginal = System.nanoTime() - start;

        // Benchmark Optimized
        start = System.nanoTime();
        for (int i = 0; i < 200000; i++) {
            for (String s : inputs) {
                ProtoUtils.toCamelCase(s);
            }
        }
        long durationOptimized = System.nanoTime() - start;

        System.out.println("toCamelCase Original: " + durationOriginal / 1_000_000.0 + " ms");
        System.out.println("toCamelCase Optimized: " + durationOptimized / 1_000_000.0 + " ms");
        System.out.printf("toCamelCase Improvement: %.2fx%n", (double) durationOriginal / durationOptimized);
    }

    private String[] getInputs() {
        return new String[]{
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
    }

    public static String toPascalCaseOriginal(String s) {
        return s == null ? null : Arrays.stream(s.split("_"))
                .filter(not(String::isEmpty))
                .map(ProtoUtils::capitalize)
                .collect(Collectors.joining(""));
    }

    public static String toCamelCaseOriginal(String s) {
        if (s == null) {
            return null;
        }
        String[] parts = s.split("_");
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(ProtoUtils.decapitalize(parts[0])), Arrays.stream(parts).skip(1).map(ProtoUtils::capitalize))
                .collect(Collectors.joining(""));
    }
}
