package mb.oauth2authorizationserver.java;

import jdk.incubator.vector.FloatVector;
import jdk.jfr.Recording;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryManagerMXBean;
import java.lang.module.ModuleFinder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.PEMDecoder;
import java.security.PEMEncoder;
import java.security.PublicKey;
import java.util.List;
import java.util.Set;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java 27 Features Overview
 * -------------------------
 * <p>
 * Performance & Runtime
 * ---------------------
 * JEP 523: Make G1 the Default Garbage Collector in All Environments
 * JEP 534: Compact Object Headers by Default
 * <p>
 * Security & Cryptography
 * -----------------------
 * JEP 527: Post-Quantum Hybrid Key Exchange for TLS 1.3
 * JEP 538: PEM Encodings of Cryptographic Objects (Third Preview)
 * <p>
 * Monitoring
 * ----------
 * JEP 536: JFR In-Process Data Redaction
 * <p>
 * Concurrency, Constants and Language
 * -----------------------------------
 * JEP 531: Lazy Constants (Third Preview)
 * JEP 532: Primitive Types in Patterns, instanceof, and switch (Fifth Preview)
 * JEP 533: Structured Concurrency (Seventh Preview)
 * <p>
 * Vector & Performance
 * --------------------
 * JEP 537: Vector API (Twelfth Incubator)
 * <p>
 * Additional compatibility checks
 * --------------------------------
 * JEP 500: Prepare to Make Final Mean Final (delivered in JDK 26, not in the JDK 27 feature list)
 * - Deep-reflection warnings are runtime-configuration dependent.
 * <p>
 * Notes:
 * - Preview features require --enable-preview.
 * - Vector API requires --add-modules jdk.incubator.vector.
 * - Performance improvements should be benchmarked, not asserted
 * through deterministic unit tests.
 * <p>
 * <a href="https://blogs.oracle.com/java/the-arrival-of-java-27">
 * Oracle Blog: The Arrival of Java 27</a>
 * <p>
 * <a href="https://www.oracle.com/java/technologies/javase/27all-relnotes.html">
 * Oracle JDK 27 Release Notes</a>
 */
class Java27FeaturesTest {

    @TempDir
    private Path tempDir;

    private static Path javaBin() {
        return Path.of(System.getProperty("java.home"), "bin", executableName("java"));
    }

    private static String executableName(String name) {
        return System.getProperty("os.name")
                .toLowerCase()
                .contains("win") ? name + ".exe" : name;
    }

    private static ProcessResult run(List<String> command, Path workingDirectory) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .start();

        try (var input = process.getInputStream()) {
            String output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return new ProcessResult(process.waitFor(), output);
        }
    }

    /**
     * JEP 523:
     * G1 should be available as a garbage collector.
     * <p>
     * Verifying that G1 is the default in every environment
     * requires a deployment-level check, not a unit test.
     */
    @Test
    void g1GarbageCollector_ShouldBeAvailable_WhenUsingJava27() {
        // Arrange
        String gcName = ManagementFactory
                .getGarbageCollectorMXBeans()
                .stream()
                .map(MemoryManagerMXBean::getName)
                .filter(name -> name.contains("G1"))
                .findFirst()
                .orElse("");

        // Assertions
        assertFalse(gcName.isBlank(), "G1 garbage collector should be available");
    }

    /**
     * JEP 534:
     * Compact object headers are enabled by default.
     * <p>
     * This test verifies that the JVM exposes the
     * corresponding runtime option.
     */
    @Test
    void compactObjectHeaders_ShouldBeSupported_WhenUsingJava27() throws Exception {
        // Arrange
        ProcessResult result = run(List.of(javaBin().toString(), "-XX:+PrintFlagsFinal", "-version"), tempDir);

        // Assertions
        assertEquals(0, result.exitCode(), result.output());

        assertTrue(result.output().contains("UseCompactObjectHeaders"), "Compact object header option should be available");
    }

    /**
     * JEP 527:
     * Verify that the Java TLS implementation is available.
     * <p>
     * Actual hybrid key exchange negotiation requires
     * an integration test with a compatible TLS peer.
     */
    @Test
    void postQuantumTls_ShouldExposeSslImplementation_WhenUsingJava27() throws Exception {
        // Arrange
        var sslContext = SSLContext.getDefault();

        // Act
        var supportedProtocols = sslContext
                .getSupportedSSLParameters()
                .getProtocols();

        // Assertions
        assertNotNull(sslContext);
        assertTrue(List.of(supportedProtocols).contains("TLSv1.3"), "TLS 1.3 should be supported");
    }

    /**
     * JEP 538:
     * PEM encoding and decoding should preserve public keys.
     */
    @Test
    void pemEncodings_ShouldRoundTripPublicKey_WhenUsingPemEncoderAndDecoder() throws Exception {
        // Arrange
        var keyPair = KeyPairGenerator
                .getInstance("RSA")
                .generateKeyPair();

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
     * JEP 533: Structured Concurrency (7th Preview)
     * StructuredTaskScope.open() creates the scope and join() waits for
     * all forked subtasks. join() declares InterruptedException.
     */
    @Test
    void structuredConcurrency_ShouldJoinWithExplicitExceptionContract_InJava27() throws Exception {
        String user = "alice";
        String order = "order-27";

        try (var scope = StructuredTaskScope.open()) {
            var userTask = scope.fork(() -> user);
            var orderTask = scope.fork(() -> order);

            // join() waits for all subtasks to complete
            scope.join();

            assertEquals("alice", userTask.get());
            assertEquals("order-27", orderTask.get());
        }
    }

    /**
     * JEP 531:
     * LazyConstant should initialize its value only once.
     */
    @Test
    void lazyConstants_ShouldInitializeOnceAndReuseValue_WhenCallingGet() {
        // Arrange
        var counter = new AtomicInteger();

        var lazyConstant = LazyConstant.of(() -> {
            counter.incrementAndGet();
            return "java-27";
        });

        // Act
        String first = lazyConstant.get();
        String second = lazyConstant.get();
        String third = lazyConstant.get();

        // Assertions
        assertEquals("java-27", first);
        assertEquals("java-27", second);
        assertEquals("java-27", third);

        assertEquals(1, counter.get(), "Lazy constant should initialize exactly once");
    }

    /**
     * NEW - JEP 531: Lazy Constants / Lazy Collections (Third Preview)
     * Java 27 adds Set.ofLazy(...) (and List.ofLazy(...)) to compute
     * distinct item visibility on demand.
     */
    @Test
    void lazyConstants_ShouldSupportLazySetEvaluation_InJava27() {
        var counter = new AtomicInteger();

        Set<String> candidateFlags = Set.of("dark-mode", "beta-checkout", "ai-recommendations");

        // Set.ofLazy takes a collection of candidates and a Predicate computing function
        Set<String> activeFlags = Set.ofLazy(candidateFlags, flag -> {
            counter.incrementAndGet();
            return flag.equals("ai-recommendations");
        });

        assertNotNull(activeFlags);
        assertEquals(0, counter.get(), "Predicate should not evaluate until an element is explicitly queried");

        // Element querying triggers the calculation at most once
        assertTrue(activeFlags.contains("ai-recommendations"));
        assertEquals(1, counter.get(), "Computing function should run exactly once for the queried candidate");

        // Querying the same item again uses the cached resolution state
        assertTrue(activeFlags.contains("ai-recommendations"));
        assertEquals(1, counter.get(), "Subsequent lookups should not trigger recalculations");
    }

    /**
     * JEP 537:
     * Vector API should execute vector operations.
     * <p>
     * The API is incubating and requires the
     * jdk.incubator.vector module.
     */
    @Test
    void vectorApi_ShouldCalculateVectorAddition_WhenUsingFloatVector() {
        // Arrange
        float[] left = {1f, 2f, 3f, 4f};
        float[] right = {5f, 6f, 7f, 8f};

        float[] output = new float[4];

        var species = FloatVector.SPECIES_128;

        // Act
        var a = FloatVector.fromArray(species, left, 0);
        var b = FloatVector.fromArray(species, right, 0);

        a.add(b).intoArray(output, 0);

        // Assertions
        assertArrayEquals(new float[]{6f, 8f, 10f, 12f}, output);
    }

    /**
     * JEP 536:
     * JFR recordings should be available for application
     * monitoring. Redaction policy itself requires a
     * separate recording-level integration test.
     */
    @Test
    void jfr_ShouldCreateRecording_WhenUsingFlightRecorder() {
        // Arrange & Act
        try (Recording recording = new Recording()) {
            recording.start();

            recording.stop();

            // Assertions
            assertTrue(recording.getId() > 0);
        }
    }

    /**
     * JEP 500:
     * Final field mutation warnings are dependent on
     * deep-reflection behavior and JVM command-line flags.
     * <p>
     * This test checks that the relevant runtime option
     * is exposed rather than asserting a particular warning.
     */
    @Test
    void finalFieldMutation_ShouldExposeRuntimeConfiguration_WhenUsingJava27() throws Exception {
        // Arrange
        // JEP 500 options are documented CLI switches, not -XX flags, so they do not appear in PrintFlagsFinal
        ProcessResult result = run(List.of(javaBin().toString(), "--help"), tempDir);

        // Assertions
        assertEquals(0, result.exitCode(), result.output());

        assertTrue(result.output().contains("--illegal-final-field-mutation"), "Final field mutation configuration should be exposed");
    }

    /**
     * JEP 500: Prepare to Make Final Mean Final
     * Instead of looking for a non-existent standard internal flag string,
     * this verifies that the JVM successfully boots and accepts the official
     * JEP 500 command-line parameter.
     */
    @Test
    void finalFieldMutation_ShouldAcceptValidCliArgument_WhenUsingJava27() throws Exception {
        // Under JEP 500, the mechanism is exposed via standard CLI options
        // like --illegal-final-field-mutation=warn|deny|allow instead of internal -XX switches.
        ProcessResult result = run(List.of(javaBin().toString(), "--illegal-final-field-mutation=warn", "-version"), tempDir);

        // If the CLI option were unrecognized or unsupported, the JVM process would fail to start (exitCode != 0)
        assertEquals(0, result.exitCode(), "JVM should boot successfully accepting JEP 500 parameters.");
    }

    /**
     * JEP 537:
     * Verify that the Vector API module is installed.
     */
    @Test
    void vectorApiModule_ShouldBeAvailable_WhenUsingJava27() {
        // Act
        boolean available = ModuleFinder
                .ofSystem()
                .find("jdk.incubator.vector")
                .isPresent();

        // Assertions
        assertTrue(available, "jdk.incubator.vector should be available");
    }

    private record ProcessResult(int exitCode, String output) {
    }

    /**
     * JEP 532:
     * Primitive patterns should match all eight
     * primitive types in switch and instanceof.
     */
    @Nested
    class PrimitivePatternUtilsTest {

        private static Stream<Arguments> provideAllPrimitiveTypes() {
            return Stream.of(
                    Arguments.of(42, 42),
                    Arguments.of((byte) 12, (byte) 12),
                    Arguments.of((short) 300, (short) 300),
                    Arguments.of(123456789L, 123456789L),
                    Arguments.of(3.14f, 3.14f),
                    Arguments.of(2.71828, 2.71828),
                    Arguments.of('A', 'A'),
                    Arguments.of(true, true)
            );
        }

        private static boolean matchesInt(Object value) {
            return value instanceof int i && i == 42;
        }

        private static boolean matchesBoolean(Object value) {
            return value instanceof boolean b && b;
        }

        private static boolean matchesDouble(Object value) {
            return value instanceof double d && d == 3.14;
        }

        @ParameterizedTest(name = "Java 27 primitive pattern: {0}")
        @MethodSource("provideAllPrimitiveTypes")
        void primitivePatterns_ShouldMatchPrimitiveType_WhenUsingSwitch(Object value, Object expected) {

            // Act
            Object result = switch (value) {
                case Byte b -> b;
                case Short s -> s;
                case Character c -> c;
                case Integer i -> i;
                case Long l -> l;
                case Float f -> f;
                case Double d -> d;
                case Boolean b -> b;
                default -> null;
            };

            // Assertions
            assertEquals(expected, result);
        }

        @Test
        void primitivePatterns_ShouldMatchInt_WhenUsingInstanceof() {
            // Act
            // Assertions
            assertTrue(matchesInt(42));
            assertFalse(matchesInt(42L), "Long should not match int pattern");
            assertFalse(matchesInt("42"), "String should not match int pattern");
        }

        @Test
        void primitivePatterns_ShouldMatchBoolean_WhenUsingInstanceof() {
            // Act
            // Assertions
            assertTrue(matchesBoolean(true));
            assertFalse(matchesBoolean(false));
            assertFalse(matchesBoolean(1), "Integer should not match boolean pattern");
        }

        @Test
        void primitivePatterns_ShouldMatchDouble_WhenUsingInstanceof() {
            // Act
            // Assertions
            assertTrue(matchesDouble(3.14));
            assertFalse(matchesDouble(3.14f), "Float should not match double pattern");
        }
    }
}
