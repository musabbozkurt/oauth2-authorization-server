package mb.oauth2authorizationserver.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("StringCollectionCompressorUtils Tests")
class StringCollectionCompressorUtilsTest {

    @Nested
    @DisplayName("Compression Tests")
    class CompressionTests {

        @Test
        void compress_ShouldReturnCompressedString_WhenGivenSingleString() {
            // Arrange
            Set<String> strings = Set.of("sampleString");

            // Act
            String compressed = StringCollectionCompressorUtils.compress(strings);

            // Assertions
            assertNotNull(compressed);
            assertFalse(compressed.isEmpty());
            assertTrue(compressed.length() > strings.toString().length());
        }

        @Test
        void compress_ShouldReturnCompressedString_WhenGivenMultipleStrings() {
            // Arrange
            Set<String> strings = Set.of("string1", "string2", "string3");

            // Act
            String compressed = StringCollectionCompressorUtils.compress(strings);

            // Assertions
            assertNotNull(compressed);
            assertFalse(compressed.isEmpty());
            assertTrue(compressed.length() > strings.toString().length());
        }

        @ParameterizedTest
        @NullAndEmptySource
        void compress_ShouldReturnEmptyString_WhenGivenNullOrEmptyCollection(Collection<String> strings) {
            // Arrange - Input provided by @NullAndEmptySource

            // Act
            String compressed = StringCollectionCompressorUtils.compress(strings);

            // Assertions
            assertEquals("", compressed);
        }

        @Test
        void compress_ShouldReturnCompressedString_WhenGivenDuplicateStrings() {
            // Arrange
            List<String> strings = Arrays.asList("duplicate", "duplicate", "duplicate");

            // Act
            String compressed = StringCollectionCompressorUtils.compress(strings);

            // Assertions
            assertNotNull(compressed);
            Set<String> decompressed = StringCollectionCompressorUtils.decompress(compressed);
            assertEquals(1, decompressed.size());
        }
    }

    @Nested
    @DisplayName("Decompression Tests")
    class DecompressionTests {

        @Test
        void decompress_ShouldReturnOriginalStrings_WhenGivenValidCompressedString() {
            // Arrange
            Set<String> original = Set.of("test1", "test2", "test3");
            String compressed = StringCollectionCompressorUtils.compress(original);

            // Act
            Set<String> decompressed = StringCollectionCompressorUtils.decompress(compressed);

            // Assertions
            assertEquals(original, decompressed);
        }

        @ParameterizedTest
        @NullAndEmptySource
        void decompress_ShouldReturnEmptySet_WhenGivenNullOrEmptyString(String compressed) {
            // Arrange - Input provided by @NullAndEmptySource
            // Act
            Set<String> decompressed = StringCollectionCompressorUtils.decompress(compressed);

            // Assertions
            assertTrue(decompressed.isEmpty());
        }

        @Test
        void decompress_ShouldReturnEmptySet_WhenGivenInvalidCompressedString() {
            // Arrange
            String invalid = "invalid-compressed-string";

            // Act
            Set<String> decompressed = StringCollectionCompressorUtils.decompress(invalid);

            // Assertions
            assertTrue(decompressed.isEmpty());
        }
    }

    @Nested
    @DisplayName("Data Integrity Tests")
    class DataIntegrityTests {

        static Stream<Arguments> provideTestCases() {
            return Stream.of(
                    arguments(Set.of("simple")),
                    arguments(Set.of("multiple", "strings", "test")),
                    arguments(Set.of("special@#$", "chars%^&", "test!@#")),
                    arguments(Set.of("unicode字", "テスト", "тест")),
                    arguments(Set.of("with,comma", "with.dot", "with space"))
            );
        }

        @ParameterizedTest
        @MethodSource("provideTestCases")
        void process_ShouldMaintainDataIntegrity_WhenCompressingAndDecompressing(Set<String> original) {
            // Arrange - Input provided by @MethodSource
            // Act
            String compressed = StringCollectionCompressorUtils.compress(original);
            Set<String> decompressed = StringCollectionCompressorUtils.decompress(compressed);

            // Assertions
            assertEquals(original.stream().sorted().toList().getLast(), decompressed.stream().sorted().toList().getLast());
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        void process_ShouldCompleteWithinTimeLimit_WhenProcessingLargeDataset() {
            // Arrange
            Set<String> largeSet = IntStream.range(0, 10000)
                    .mapToObj(i -> "string" + i)
                    .collect(Collectors.toSet());

            // Act
            long startTime = System.nanoTime();
            String compressed = StringCollectionCompressorUtils.compress(largeSet);
            Set<String> decompressed = StringCollectionCompressorUtils.decompress(compressed);
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;

            // Assertions
            assertTrue(durationMs < 1000, "Operation took too long: " + durationMs + "ms");
            assertEquals(largeSet, decompressed);
        }

        @Test
        void compress_ShouldAchieveMinimumCompressionRatio_WhenProcessingRepetitiveData() {
            // Arrange
            Set<String> strings = IntStream.range(0, 100)
                    .mapToObj(i -> "repeated_string_" + i)
                    .collect(Collectors.toSet());

            // Act
            String compressed = StringCollectionCompressorUtils.compress(strings);
            double compressionRatio = (double) compressed.length() /
                    strings.toString().length();

            // Assertions
            assertTrue(compressionRatio < 0.5,
                    "Compression ratio not efficient enough: " + compressionRatio);
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        void process_ShouldMaintainIntegrity_WhenProcessingLongStrings() {
            // Arrange
            String longString = "a".repeat(1000);
            Set<String> strings = Set.of(longString);

            // Act
            String compressed = StringCollectionCompressorUtils.compress(strings);
            Set<String> decompressed = StringCollectionCompressorUtils.decompress(compressed);

            // Assertions
            assertEquals(strings, decompressed);
        }

        @Test
        void process_ShouldMaintainIntegrity_WhenProcessingStringsWithDelimiter() {
            // Arrange
            Set<String> strings = Set.of("first,part", "second,part", "third,part");

            // Act
            String compressed = StringCollectionCompressorUtils.compress(strings);
            Set<String> decompressed = StringCollectionCompressorUtils.decompress(compressed);

            // Assertions
            assertTrue(strings.stream().sorted().toList().getFirst().contains(decompressed.stream().sorted().toList().getFirst()));
            assertTrue(strings.stream().sorted().toList().getLast().contains(decompressed.stream().sorted().toList().getLast()));
        }

        @Test
        void process_ShouldMaintainIntegrity_WhenProcessingMixedContent() {
            // Arrange
            Set<String> strings = new HashSet<>();
            strings.add("normal");
            strings.add("with,comma");
            strings.add("with\nnewline");
            strings.add("unicode字");
            strings.add("a".repeat(100));

            // Act
            String compressed = StringCollectionCompressorUtils.compress(strings);
            Set<String> decompressed = StringCollectionCompressorUtils.decompress(compressed);

            // Assertions
            assertEquals(strings.stream().sorted().toList().getFirst(), decompressed.stream().sorted().toList().getFirst());
        }

        @Test
        void process_ShouldMaintainIntegrity_WhenProcessingEmptyStrings() {
            // Arrange
            Set<String> strings = Set.of("");

            // Act
            String compressed = StringCollectionCompressorUtils.compress(strings);
            Set<String> decompressed = StringCollectionCompressorUtils.decompress(compressed);

            // Assertions
            assertTrue(decompressed.isEmpty());
        }
    }
}
