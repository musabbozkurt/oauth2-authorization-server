package mb.oauth2authorizationserver;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.PEMDecoder;
import java.security.PEMEncoder;
import java.security.PublicKey;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/***
 * Java 26 Features Overview
 * -------------------------
 * Performance & Runtime (JEP 500, 516, 522)
 * -----------------------------------------
 * • Prepare to Make Final Mean Final (JEP 500)
 *   - Documented only: warning text/behavior depends on runtime flags and deep-reflection library usage.
 * • AOT Object Caching with Any GC (JEP 516)
 *   - Verifies AOT cache generation with ZGC when available.
 * • G1 Throughput Improvements (JEP 522)
 *   - Documented only: throughput gains are benchmark-based and not deterministic unit-test assertions.
 * <p>
 * Networking & Security (JEP 517, 524)
 * ------------------------------------
 * • HTTP/3 for HttpClient API (JEP 517)
 *   - Verifies HTTP_3 can be configured on HttpClient.
 * • PEM Encodings (JEP 524, Second Preview)
 *   - Verifies PEM encode/decode round-trip for public keys.
 * <p>
 * Concurrency, Values, and Language (JEP 525, 526, 530)
 * ------------------------------------------------------
 * • Structured Concurrency (JEP 525, Sixth Preview)
 *   - Confirms fork/join behavior with StructuredTaskScope.
 * • Lazy Constants (JEP 526, Second Preview)
 *   - Verifies single initialization and immutable reuse via StableValue.
 * • Primitive Patterns (JEP 530, Fourth Preview)
 *   - Verifies primitive pattern matching in switch and instanceof flows.
 * <p>
 * Vector & Platform
 * -----------------
 * • Vector API (JEP 529, Eleventh Incubator)
 *   - Compiles and runs vector operations with incubator module.
 * • Applet API Removal (Release change)
 *   - Verifies legacy applet API is not present at runtime.
 * <p>
 * <a href="https://www.baeldung.com/java-26-new-features">Baeldung Java 26 Features</a>
 * <a href="https://blogs.oracle.com/java/the-arrival-of-java-26">Oracle Blog: The Arrival of Java 26</a>
 * <a href="https://www.oracle.com/java/technologies/javase/26all-relnotes.html">Oracle JDK 26 Release Notes</a>
 * -----
 */
class Java26FeaturesTest {

    @TempDir
    private Path tempDir;

    private static boolean isZgcUnsupported(String output) {
        String normalized = output.toLowerCase();
        return normalized.contains("unrecognized vm option") || normalized.contains("zgc") && normalized.contains("not supported");
    }

    private static ProcessResult run(List<String> command, Path workingDirectory) throws IOException, InterruptedException {
        try (Process process = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .start();
             var inputStream = process.getInputStream()) {
            String output = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return new ProcessResult(process.waitFor(), output);
        }
    }

    private static Path javaBin() {
        return Path.of(System.getProperty("java.home"), "bin", executableName("java"));
    }

    private static Path javacBin() {
        return Path.of(System.getProperty("java.home"), "bin", executableName("javac"));
    }

    private static String executableName(String baseName) {
        return System.getProperty("os.name").toLowerCase().contains("win") ? baseName + ".exe" : baseName;
    }

    /**
     * JEP 516: AOT cache generation should work with ZGC when runtime supports it.
     */
    @Test
    void aheadOfTimeObjectCaching_ShouldCreateCache_WhenUsingZgcAndRecordMode() throws Exception {
        // Arrange
        Path aotCache = tempDir.resolve("java26-any-gc.aot");

        // Act
        ProcessResult result = run(List.of(
                javaBin().toString(),
                "-XX:+UseZGC",
                "-XX:AOTMode=record",
                "-XX:AOTCacheOutput=" + aotCache,
                "-version"
        ), tempDir);

        // Assertions
        if (result.exitCode != 0) {
            Assumptions.assumeTrue(!isZgcUnsupported(result.output), "ZGC is unavailable on this runtime: " + result.output);
        }
        assertEquals(0, result.exitCode, result.output);
        assertTrue(Files.exists(aotCache), "AOT cache file should be created");
        assertTrue(Files.size(aotCache) > 0, "AOT cache file should not be empty");
    }

    /**
     * JEP 517: HttpClient should accept HTTP/3 as configured preferred protocol.
     */
    @Test
    void httpClient_ShouldAcceptHttp3Configuration_WhenUsingBuilderVersion() {
        // Arrange
        try (HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_3)
                .build()) {

            // Act
            HttpClient.Version preferredVersion = client.version();

            // Assertions
            assertEquals(HttpClient.Version.HTTP_3, preferredVersion);
        }
    }

    /**
     * JEP 524: PEM encode/decode round-trip for public keys.
     */
    @Test
    void pemEncodings_ShouldRoundTripPublicKey_WhenUsingPemEncoderAndDecoder() throws Exception {
        // Arrange
        var keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        var encoder = PEMEncoder.of();
        var decoder = PEMDecoder.of();

        // Act
        String pem = encoder.encodeToString(keyPair.getPublic());
        PublicKey decoded = decoder.decode(pem, PublicKey.class);

        // Assertions
        assertTrue(pem.contains("BEGIN PUBLIC KEY"));
        assertArrayEquals(keyPair.getPublic().getEncoded(), decoded.getEncoded());
    }

    /**
     * JEP 525: Structured task scopes should join forked subtasks and expose their results.
     */
    @Test
    void structuredConcurrency_ShouldJoinForkedTasks_WhenUsingStructuredTaskScope() throws Exception {
        // Arrange
        String user = "alice";
        String order = "order-42";

        // Act & Assert
        try (var scope = StructuredTaskScope.open()) {
            var userTask = scope.fork(() -> user);
            var orderTask = scope.fork(() -> order);
            scope.join();

            assertEquals("alice", userTask.get());
            assertEquals("order-42", orderTask.get());
        }
    }

    /**
     * JEP 526: LazyConstant should execute its computing function at most once.
     */
    @Test
    void lazyConstants_ShouldInitializeOnceAndKeepTheFirstValue_WhenUsingLazyConstant() {
        // Arrange
        var counter = new AtomicInteger(0);

        // Define the LazyConstant with a computing function (Supplier)
        // Note: Java 26 forbids null values to maximize constant-folding optimizations
        var lazyConstant = LazyConstant.of(() -> {
            counter.incrementAndGet();
            return "java-26";
        });

        // Act
        // The first get() triggers the computing function
        String first = lazyConstant.get();

        // Subsequent get() calls return the cached value immediately without re-running the supplier
        String second = lazyConstant.get();
        String third = lazyConstant.get();

        // Assertions
        assertEquals("java-26", first);
        assertEquals("java-26", second);
        assertEquals("java-26", third);

        // Verify the initialization logic was executed exactly once
        assertEquals(1, counter.get());
    }

    /**
     * JEP 529: Vector API should compile and run with incubator module enabled.
     */
    @Test
    void vectorApi_ShouldCompileAndRun_WhenIncubatorModuleIsEnabled() throws Exception {
        // Arrange
        Assumptions.assumeTrue(
                ModuleFinder.ofSystem().find("jdk.incubator.vector").isPresent(),
                "jdk.incubator.vector module is not available in this runtime"
        );

        Path source = tempDir.resolve("VectorApi26Sample.java");
        Files.writeString(source, """
                import jdk.incubator.vector.FloatVector;
                import java.util.Arrays;
                
                class VectorApi26Sample {
                    public static void main(String[] args) {
                        float[] left = {1f, 2f, 3f, 4f};
                        float[] right = {5f, 6f, 7f, 8f};
                        FloatVector a = FloatVector.fromArray(FloatVector.SPECIES_128, left, 0);
                        FloatVector b = FloatVector.fromArray(FloatVector.SPECIES_128, right, 0);
                        float[] out = new float[4];
                        a.add(b).intoArray(out, 0);
                        System.out.print(Arrays.toString(out));
                    }
                }
                """);

        // Act
        ProcessResult compile = run(List.of(
                javacBin().toString(),
                "--enable-preview",
                "--release", "26",
                "--add-modules", "jdk.incubator.vector",
                source.toString()
        ), tempDir);
        ProcessResult run = run(List.of(
                javaBin().toString(),
                "--enable-preview",
                "--add-modules", "jdk.incubator.vector",
                "-cp", tempDir.toString(),
                "VectorApi26Sample"
        ), tempDir);

        // Assertions
        assertEquals(0, compile.exitCode, compile.output);
        assertEquals(0, run.exitCode, run.output);
        assertTrue(run.output.contains("[6.0, 8.0, 10.0, 12.0]"), run.output);
    }

    /**
     * Java 26 platform cleanup: legacy applet API is removed.
     */
    @Test
    void appletApi_ShouldBeUnavailable_WhenLoadingLegacyAppletClass() {
        // Act & Assertions
        assertThrows(ClassNotFoundException.class, () -> Class.forName("java.applet.Applet"));
    }

    private record ProcessResult(int exitCode, String output) {
    }

    /**
     * JEP 530: primitive patterns should match their own primitive type in switch and instanceof.
     */
    @Nested
    class PrimitivePatternUtilsTest {

        private static Stream<Arguments> provideAllPrimitiveTypes() {
            return Stream.of(
                    Arguments.of(42, "int", 42),
                    Arguments.of((byte) 12, "byte", (byte) 12),
                    Arguments.of((short) 300, "short", (short) 300),
                    Arguments.of(123456789L, "long", 123456789L),
                    Arguments.of(3.14f, "float", 3.14f),
                    Arguments.of(2.71828, "double", 2.71828),
                    Arguments.of('A', "char", 'A'),
                    Arguments.of(true, "boolean", true)
            );
        }

        @MethodSource("provideAllPrimitiveTypes")
        @ParameterizedTest(name = "Java 26 Primitive Test: {1} with {0}")
        void primitivePatterns_ShouldMatchPrimitiveType_WhenUsingInstanceOfAndSwitch(Object value, String typeLabel, Object expectedValue) {
            // 1. Act & Assertions: Java 26 switch usage with primitive pattern matching
            Object switchMatch = switch (value) {
                case byte b -> b;
                case short s -> s;
                case char c -> c;
                case int i -> i;
                case long l -> l;
                case float f -> f;
                case double d -> d;
                case boolean b -> b;
                default -> null;
            };

            assertEquals(expectedValue, switchMatch);

            // 2. Act & Assert: Java 26 instanceof usage with primitive pattern matching
            boolean isMatched = false;
            if (value instanceof int i) {
                assertEquals(expectedValue, i);
                isMatched = true;
            } else if (value instanceof byte b) {
                assertEquals(expectedValue, b);
                isMatched = true;
            } else if (value instanceof short s) {
                assertEquals(expectedValue, s);
                isMatched = true;
            } else if (value instanceof long l) {
                assertEquals(expectedValue, l);
                isMatched = true;
            } else if (value instanceof float f) {
                assertEquals(expectedValue, f);
                isMatched = true;
            } else if (value instanceof double d) {
                assertEquals(expectedValue, d);
                isMatched = true;
            } else if (value instanceof char c) {
                assertEquals(expectedValue, c);
                isMatched = true;
            } else if (value instanceof boolean b) {
                assertEquals(expectedValue, b);
                isMatched = true;
            }

            assertTrue(isMatched, "The value should match its own primitive type pattern.");
            assertFalse(typeLabel.isBlank());
        }
    }
}
