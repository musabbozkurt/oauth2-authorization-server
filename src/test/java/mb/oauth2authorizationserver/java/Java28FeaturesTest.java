package mb.oauth2authorizationserver.java;

import jdk.incubator.json.Json;
import jdk.incubator.json.JsonObject;
import jdk.incubator.json.JsonValue;
import jdk.incubator.vector.FloatVector;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryManagerMXBean;
import java.lang.module.ModuleFinder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.PEMDecoder;
import java.security.PEMEncoder;
import java.security.PrivateKey;
import java.util.List;
import java.util.Map;
import java.util.concurrent.StructuredTaskScope;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java 28 Features Overview (early access build 28-ea)
 * ----------------------------------------------------
 * <p>
 * Language & Runtime
 * ------------------
 * JEP 401: Value Objects (Preview)
 * JEP 539: Strict Field Initialization in the JVM (Preview)
 * <p>
 * Performance & Garbage Collection
 * --------------------------------
 * JEP 535: Generational Shenandoah by Default
 * JEP 544: Ahead-of-Time Code Compilation
 * JEP 545: Faster Startup and Warmup with ZGC
 * JEP 546: Adaptive Heap Sizing for ZGC
 * <p>
 * Security & Cryptography
 * -----------------------
 * JEP 542: PEM Encodings of Cryptographic Objects
 * <p>
 * Data & Concurrency
 * ------------------
 * JEP 540: Simple JSON API (Incubator)
 * JEP 543: Structured Concurrency (Final)
 * <p>
 * Platform
 * --------
 * JEP 541: Deprecate the macOS/x64 Port for Removal
 * <p>
 * Notes:
 * - Value objects (JEP 401) require --enable-preview.
 * - The JSON API requires --add-modules jdk.incubator.json.
 * - Vector API requires --add-modules jdk.incubator.vector.
 * - Structured concurrency is final and does not need --enable-preview.
 * - Strict field initialization and the GC behavior changes are JVM-level
 * features, so the tests check the exposed runtime configuration, not
 * the performance or the exact behavior.
 * <p>
 * <a href="https://openjdk.org/projects/jdk/28/">
 * OpenJDK JDK 28 Project Page</a>
 */
class Java28FeaturesTest {

    @TempDir
    private Path tempDir;

    private static Path javaBin() {
        return Path.of(System.getProperty("java.home"), "bin", executableName());
    }

    private static String executableName() {
        return System.getProperty("os.name")
                .toLowerCase()
                .contains("win") ? "java" + ".exe" : "java";
    }

    private static ProcessResult run(List<String> command, Path workingDirectory) throws IOException, InterruptedException {
        try (Process process = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .start()) {
            try (var input = process.getInputStream()) {
                String output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                return new ProcessResult(process.waitFor(), output);
            }
        }
    }

    /**
     * JEP 401: Value Objects (Preview)
     * A value class has identity-free semantics and is
     * reported as a value class at runtime.
     */
    @Test
    void valueObjects_ShouldBeReportedAsValueClass_WhenUsingJava28() {
        // Arrange
        var money = new Money(10, "EUR");
        var sameMoney = new Money(10, "EUR");

        // Act
        boolean isValue = Money.class.isValue();

        // Assertions
        assertTrue(isValue, "Money should be a value class");
        assertEquals(10, money.amount());
        assertEquals("EUR", money.currency());
        assertEquals(money, sameMoney, "Value objects with equal state should be equal");
    }

    /**
     * JEP 539: Strict Field Initialization in the JVM (Preview)
     * This JEP adds no source-level syntax. Strict fields are a
     * class-file and JVM feature, so the test verifies that the JVM
     * starts with preview features enabled.
     */
    @Test
    void strictFieldInitialization_ShouldStartJvm_WhenPreviewEnabledInJava28() throws Exception {
        // Arrange
        ProcessResult result = run(List.of(javaBin().toString(), "--enable-preview", "-version"), tempDir);

        // Assertions
        assertEquals(0, result.exitCode(), result.output());
    }

    /**
     * JEP 535: Generational Shenandoah by Default
     * Shenandoah must be available to use its generational mode.
     * The option is platform-specific, so the test is skipped when absent.
     */
    @Test
    void generationalShenandoah_ShouldExposeShenandoahOption_WhenUsingJava28() throws Exception {
        // Arrange
        ProcessResult result = run(List.of(javaBin().toString(), "-XX:+UnlockExperimentalVMOptions", "-XX:+PrintFlagsFinal", "-version"), tempDir);

        // Assertions
        assertEquals(0, result.exitCode(), result.output());
        Assumptions.assumeTrue(result.output().contains("UseShenandoahGC"), "Shenandoah is not supported on this platform");
    }

    /**
     * JEP 544: Ahead-of-Time Code Compilation
     * The AOT configuration options should be exposed by the JVM.
     */
    @Test
    void aotCodeCompilation_ShouldExposeAotOptions_WhenUsingJava28() throws Exception {
        // Arrange
        ProcessResult result = run(List.of(javaBin().toString(), "-XX:+UnlockExperimentalVMOptions", "-XX:+PrintFlagsFinal", "-version"), tempDir);

        // Assertions
        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("AOTMode"), "AOTMode option should be available");
        assertTrue(result.output().contains("AOTCache"), "AOTCache option should be available");
    }

    /**
     * JEP 545: Faster Startup and Warmup with ZGC
     * The ZGC garbage collector should start successfully.
     */
    @Test
    void zgcStartup_ShouldInitializeZgc_WhenUsingJava28() throws Exception {
        // Arrange
        ProcessResult result = run(List.of(javaBin().toString(), "-XX:+UseZGC", "-Xlog:gc+init", "-version"), tempDir);

        // Assertions
        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("The Z Garbage Collector"), "ZGC should initialize");
    }

    /**
     * JEP 546: Adaptive Heap Sizing for ZGC
     * The adaptive heap sizing option should be accepted with ZGC.
     */
    @Test
    void zgcAdaptiveHeapSizing_ShouldAcceptOption_WhenUsingJava28() throws Exception {
        // Arrange
        ProcessResult result = run(List.of(javaBin().toString(), "-XX:+UseZGC", "-XX:+ZAdaptiveHeapSizing", "-version"), tempDir);

        // Assertions
        assertEquals(0, result.exitCode(), result.output());
    }

    /**
     * JEP 542: PEM Encodings of Cryptographic Objects
     * PEM encoding and decoding should round-trip a private key.
     */
    @Test
    void pemEncodings_ShouldRoundTripPrivateKey_WhenUsingPemEncoderAndDecoder() throws Exception {
        // Arrange
        var keyPair = KeyPairGenerator
                .getInstance("EC")
                .generateKeyPair();

        // Act
        String pem = PEMEncoder.of().encodeToString(keyPair.getPrivate());
        PrivateKey decoded = PEMDecoder.of().decode(pem, PrivateKey.class);

        // Assertions
        assertTrue(pem.contains("BEGIN PRIVATE KEY"));
        assertArrayEquals(keyPair.getPrivate().getEncoded(), decoded.getEncoded());
    }

    /**
     * JEP 540: Simple JSON API (Incubator)
     * JSON documents should be parsed and queried through the incubating API.
     */
    @Test
    void simpleJsonApi_ShouldParseAndQueryDocument_WhenUsingJava28() {
        // Arrange
        String document = "{\"name\":\"alice\",\"roles\":[\"admin\",\"user\"],\"level\":2}";

        // Act
        JsonValue value = Json.parse(document);

        // Assertions
        assertEquals("alice", value.get("name").asString());
        assertEquals("user", value.get("roles").get(1).asString());
        assertEquals(2, value.get("level").asInt());
        assertFalse(value.tryGet("missing").isPresent(), "Missing key should not be present");
    }

    /**
     * JEP 540: Simple JSON API (Incubator)
     * A JSON object should be creatable from a Java map.
     */
    @Test
    void simpleJsonApi_ShouldCreateJsonObject_WhenUsingMap() {
        // Arrange
        JsonObject object = JsonObject.of(Map.of("client", Json.parse("\"oauth2-authorization-server\"")));

        // Act
        JsonValue client = object.get("client");

        // Assertions
        assertEquals("oauth2-authorization-server", client.asString());
    }

    /**
     * JEP 541: Deprecate the macOS/x64 Port for Removal
     * The deprecation is a platform and release note. Verifying it
     * requires a deployment-level check, so this test only confirms that
     * the JVM reports a valid architecture.
     */
    @Test
    void macOsX64Deprecation_ShouldReportArchitecture_WhenUsingJava28() {
        // Act
        String arch = System.getProperty("os.arch");

        // Assertions
        assertNotNull(arch);
        assertFalse(arch.isBlank(), "Architecture should be reported");
    }

    /**
     * JEP 543: Structured Concurrency (Final)
     * StructuredTaskScope.open() creates the scope, and join() waits for
     * all forked subtasks. No preview flag is needed.
     */
    @Test
    void structuredConcurrency_ShouldJoinSubtasks_WhenUsingFinalApi() throws Exception {
        // Arrange
        String user = "alice";
        String order = "order-28";

        try (var scope = StructuredTaskScope.open()) {
            var userTask = scope.fork(() -> user);
            var orderTask = scope.fork(() -> order);

            // Act
            scope.join();

            // Assertions
            assertEquals("alice", userTask.get());
            assertEquals("order-28", orderTask.get());
        }
    }

    /**
     * JEP 537: Vector API (incubator)
     * Vector operations should execute on the incubating Vector API.
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
     * Verifies that the JSON incubator module is installed.
     */
    @Test
    void jsonModule_ShouldBeAvailable_WhenUsingJava28() {
        // Act
        boolean available = ModuleFinder
                .ofSystem()
                .find("jdk.incubator.json")
                .isPresent();

        // Assertions
        assertTrue(available, "jdk.incubator.json should be available");
    }

    /**
     * G1 should still be available as a garbage collector.
     */
    @Test
    void g1GarbageCollector_ShouldBeAvailable_WhenUsingJava28() {
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

    private record ProcessResult(int exitCode, String output) {
    }

    // Must stay a value class (not a record) to exercise JEP 401
    @SuppressWarnings("ClassCanBeRecord")
    private static value class Money {
        private final int amount;
        private final String currency;

        private Money(int amount, String currency) {
            this.amount = amount;
            this.currency = currency;
        }

        int amount() {
            return amount;
        }

        String currency() {
            return currency;
        }
    }

    // Must stay a value class (not a record) to exercise JEP 401
    @SuppressWarnings("ClassCanBeRecord")
    private static value class ValueMoney {
        private final int amount;
        private final String currency;

        ValueMoney(int amount, String currency) {
            this.amount = amount;
            this.currency = currency;
        }

        int amount() {
            return amount;
        }

        String currency() {
            return currency;
        }
    }

    /**
     * JEP 401: Value Objects (Preview)
     * Value class fields and equality are checked with the preview syntax.
     */
    @Nested
    class ValueObjectTest {

        private static Stream<Arguments> provideValues() {
            return Stream.of(
                    Arguments.of(1, "EUR"),
                    Arguments.of(25, "USD"),
                    Arguments.of(100, "TRY")
            );
        }

        @ParameterizedTest(name = "Java 28 value object: {0} {1}")
        @MethodSource("provideValues")
        void valueObjects_ShouldKeepState_WhenCreatedWithFields(int amount, String currency) {
            // Act
            var money = new ValueMoney(amount, currency);

            // Assertions
            assertEquals(amount, money.amount());
            assertEquals(currency, money.currency());
        }
    }
}
