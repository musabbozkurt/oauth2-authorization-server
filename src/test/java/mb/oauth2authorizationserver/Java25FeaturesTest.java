package mb.oauth2authorizationserver;

import module java.base;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/***
 * Java 25 Features Overview
 * -------------------------
 * Security & Cryptography (JEP 470, 510)
 * ----------------------------------------
 * • PEM Encodings (JEP 470, Preview)
 *   - Verifies PEM encode/decode round-trip for public keys
 * • Key Derivation Function API (JEP 510)
 *   - Validates deterministic HKDF derivation output
 * <p>
 * Concurrency & Values (JEP 502, 505, 506)
 * ------------------------------------------
 * • Stable Values (JEP 502, Preview)
 *   - Verifies single initialization and immutable reuse
 * • Virtual Thread Task Execution (Stable API)
 *   - Confirms fork/join style behavior with virtual-thread executor
 * • Structured Concurrency (Stable API)
 *   - Confirms fork/join style behavior with structured task scope
 * • Scoped Values (JEP 506)
 *   - Validates context binding and scope exit semantics
 * <p>
 * Language Features (JEP 507, 513)
 * --------------------------------
 * • Primitive Patterns (JEP 507, Preview)
 *   - Verifies primitive-wrapper pattern matching in switch and instanceof flows
 * • Flexible Constructor Bodies (JEP 513)
 *   - Validates guard logic before super(...) invocation
 * <p>
 * Platform & Modules (JEP 508, 511, 512)
 * ----------------------------------------
 * • Vector API (JEP 508, Incubator)
 *   - Compiles and runs vector operations with incubator module
 * • Module Import Declarations (JEP 511, Preview)
 *   - Compiles and executes import module samples
 * • Compact Source Files (JEP 512)
 *   - Runs classless instance-main source snippet
 * <p>
 * Runtime & Tooling (JEP 503, 514, 515, 518, 519, 520, 521)
 * -----------------------------------------------------------
 * • Platform Cleanup (JEP 503)
 *   - Asserts Windows 32-bit x86 is not the active architecture
 * • AOT Flags (JEP 514, 515)
 *   - Smoke-checks AOT record mode cache generation
 * • JFR Enhancements (JEP 509, 518, 520)
 *   - Verifies CPU-time profiling, cooperative sampling, and method timing events
 * • Compact Object Headers (JEP 519)
 *   - Validates compact object header flag acceptance
 * • Generational Shenandoah (JEP 521)
 *   - Tests generational GC mode configuration support
 * <p>
 * <a href="https://www.oracle.com/tr/java/technologies/javase/25all-relnotes.html">Release Notes</a>
 * <a href="https://www.jrebel.com/blog/java-25">JRebel Blog</a>
 * <p>
 * -----
 */
class Java25FeaturesTest {

    private static final ScopedValue<String> USER = ScopedValue.newInstance();

    @TempDir
    private Path tempDir;

    private static boolean isShenandoahUnsupported(String output) {
        String normalized = output.toLowerCase();
        return normalized.contains("unrecognized vm option") || normalized.contains("shenandoah") && normalized.contains("not supported");
    }

    private static ProcessResult run(List<String> command, Path workingDirectory) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .start();

        String output;
        try (var inputStream = process.getInputStream()) {
            output = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        return new ProcessResult(process.waitFor(), output);
    }

    private static Path javaBin() {
        return Path.of(System.getProperty("java.home"), "bin", executableName("java"));
    }

    private static Path javacBin() {
        return Path.of(System.getProperty("java.home"), "bin", executableName("javac"));
    }

    private static Path jfrBin() {
        return Path.of(System.getProperty("java.home"), "bin", executableName("jfr"));
    }

    private static String executableName(String baseName) {
        return System.getProperty("os.name").toLowerCase().contains("win") ? baseName + ".exe" : baseName;
    }

    /**
     * JEP 470: PEM encode/decode round-trip for public keys.
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
     * JEP 502: Stable values are initialized once and remain immutable.
     */
    @Test
    void stableValue_ShouldInitializeOnce_WhenUsingOrElseSet() {
        // Arrange
        var stable = StableValue.<String>of();

        // Act
        String first = stable.orElseSet(() -> "token");
        String second = stable.orElseSet(() -> "other");

        // Assertions
        assertTrue(stable.isSet());
        assertEquals("token", first);
        assertEquals("token", second);
        assertEquals("token", stable.orElseThrow());
        assertFalse(stable.trySet("new-token"));
    }

    /**
     * JEP 503: 32-bit x86 Windows should not be a supported runtime target.
     */
    @Test
    void platformCleanup_ShouldNotUse32BitX86_OnWindows() {
        // Arrange
        var osName = System.getProperty("os.name").toLowerCase();
        var osArch = System.getProperty("os.arch").toLowerCase();

        // Act
        boolean unsupportedWindowsX86 = osName.contains("windows") && (osArch.equals("x86") || osArch.equals("i386"));

        // Assertions
        assertFalse(unsupportedWindowsX86, "32-bit x86 architecture is no longer supported on Windows");
    }

    /**
     * JEP 505: Virtual thread task execution with executor service.
     */
    @Test
    void concurrency_ShouldJoinForkedTasks_WhenUsingVirtualThreadExecutor() throws Exception {
        // Arrange
        String user = "alice";
        String order = "order-42";

        // Act
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var userTask = executor.submit(() -> user);
            var orderTask = executor.submit(() -> order);

            // Assertions
            assertEquals("alice", userTask.get());
            assertEquals("order-42", orderTask.get());
        }
    }

    /**
     * JEP 505: Structured concurrency with task scopes.
     */
    @Test
    void concurrency_ShouldJoinForkedTasks_WhenUsingStructuredTaskScope() throws Exception {
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
     * JEP 506: Scoped values for safe context sharing.
     */
    @Test
    void scopedValues_ShouldShareBoundValue_WhenUsingWhereRun() {
        // Arrange
        String expectedUser = "scoped-user";

        // Act
        ScopedValue.where(USER, expectedUser).run(() -> assertEquals(expectedUser, USER.get()));

        // Assertions
        assertFalse(USER.isBound());
    }

    /**
     * JEP 510: KDF API derives deterministic output for identical HKDF input.
     */
    @Test
    void keyDerivationFunctionApi_ShouldDeriveStableOutput_WhenUsingHkdfSha256() throws Exception {
        // Arrange
        var kdf = KDF.getInstance("HKDF-SHA256");
        var params = HKDFParameterSpec.ofExtract()
                .addIKM("input-key-material".getBytes(StandardCharsets.UTF_8))
                .addSalt("salt".getBytes(StandardCharsets.UTF_8))
                .thenExpand("context-info".getBytes(StandardCharsets.UTF_8), 32);

        // Act
        byte[] first = kdf.deriveData(params);
        byte[] second = kdf.deriveData(params);

        // Assertions
        assertEquals(32, first.length);
        assertArrayEquals(first, second);
    }

    /**
     * JEP 511: Preview module import declarations compile and run.
     */
    @Test
    void moduleImportDeclarations_ShouldCompileAndRun_WhenUsingImportModule() throws Exception {
        // Arrange
        Path source = tempDir.resolve("ModuleImportSample.java");
        Files.writeString(source, """
                import module java.base;
                
                import java.util.Date;
                
                class ModuleImportSample {
                    public static void main(String[] args) {
                        System.out.print(new Date(0).getTime());
                    }
                }
                """);

        // Act
        ProcessResult compile = run(List.of(
                javacBin().toString(),
                "--enable-preview",
                "--release", "25",
                source.toString()
        ), tempDir);
        ProcessResult run = run(List.of(
                javaBin().toString(),
                "--enable-preview",
                "-cp", tempDir.toString(),
                "ModuleImportSample"
        ), tempDir);

        // Assertions
        assertEquals(0, compile.exitCode, compile.output);
        assertEquals(0, run.exitCode, run.output);
        assertEquals("0", run.output.strip());
    }

    /**
     * JEP 512: Compact source files and instance main methods execute.
     */
    @Test
    void compactSourceFiles_ShouldRunInstanceMain_WhenUsingClasslessSource() throws Exception {
        // Arrange
        Path source = tempDir.resolve("CompactMain.java");
        Files.writeString(source, """
                void main() {
                    System.out.print("compact-main-ok");
                }
                """);

        // Act
        ProcessResult run = run(List.of(
                javaBin().toString(),
                "--enable-preview",
                source.toString()
        ), tempDir);

        // Assertions
        assertEquals(0, run.exitCode, run.output);
        assertEquals("compact-main-ok", run.output.strip());
    }

    /**
     * JEP 513: Validation can run before {@code super(...)} in constructors.
     */
    @Test
    void flexibleConstructorBodies_ShouldAllowValidationBeforeSuperCall_WhenUsingSubclassConstructor() {
        // Arrange
        class Person {
            final int age;

            Person(int age) {
                this.age = age;
            }
        }
        class Employee extends Person {
            final String name;

            Employee(String name, int age) {
                if (age < 18 || age > 67) {
                    throw new IllegalArgumentException("Age must be between 18 and 67");
                }
                super(age);
                this.name = name;
            }
        }

        // Act
        var employee = new Employee("Alice", 35);

        // Assertions
        assertEquals("Alice", employee.name);
        assertEquals(35, employee.age);
        assertThrows(IllegalArgumentException.class, () -> new Employee("Bob", 14));
    }

    /**
     * JEP 508: Vector API compiles and runs when incubator module is present.
     */
    @Test
    void vectorApi_ShouldCompileAndRun_WhenIncubatorModuleIsEnabled() throws Exception {
        // Arrange
        Assumptions.assumeTrue(
                ModuleFinder.ofSystem().find("jdk.incubator.vector").isPresent(),
                "jdk.incubator.vector module is not available in this runtime"
        );

        Path source = tempDir.resolve("VectorSample.java");
        Files.writeString(source, """
                import jdk.incubator.vector.FloatVector;
                import java.util.Arrays;
                
                class VectorSample {
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
                "--release", "25",
                "--add-modules", "jdk.incubator.vector",
                source.toString()
        ), tempDir);
        ProcessResult run = run(List.of(
                javaBin().toString(),
                "--enable-preview",
                "--add-modules", "jdk.incubator.vector",
                "-cp", tempDir.toString(),
                "VectorSample"
        ), tempDir);

        // Assertions
        assertEquals(0, compile.exitCode, compile.output);
        assertEquals(0, run.exitCode, run.output);
        assertTrue(run.output.contains("[6.0, 8.0, 10.0, 12.0]"), run.output);
    }

    /**
     * JEP 514/515: AOT record mode should produce a cache file.
     */
    @Test
    void aheadOfTimeFlags_ShouldCreateAotCache_WhenUsingRecordMode() throws Exception {
        // Arrange
        Path aotCache = tempDir.resolve("sample.aot");

        // Act
        ProcessResult result = run(List.of(
                javaBin().toString(),
                "-XX:AOTCacheOutput=" + aotCache,
                "-XX:AOTMode=record",
                "-version"
        ), tempDir);

        // Assertions
        assertEquals(0, result.exitCode, result.output);
        assertTrue(Files.exists(aotCache), "AOT cache file should be created");
        assertTrue(Files.size(aotCache) > 0, "AOT cache file should not be empty");
        assertTrue(result.output.contains("AOTConfiguration"), result.output);
    }

    /**
     * JEP 509/518/520: JFR metadata exposes CPU-time and method timing/tracing support.
     */
    @Test
    void jfrEnhancements_ShouldExposeCpuAndMethodEvents_WhenReadingMetadata() throws Exception {
        // Act
        ProcessResult result = run(List.of(
                jfrBin().toString(),
                "metadata"
        ), tempDir);

        // Assertions
        assertEquals(0, result.exitCode, result.output);
        assertTrue(result.output.contains("jdk.CPUTimeSample"), "JEP 509 CPU-time profiling event should be present");
        assertTrue(result.output.contains("jdk.MethodTiming"), "JEP 520 method timing event should be present");
        assertTrue(result.output.contains("jdk.MethodTrace"), "JEP 520 method trace event should be present");
        assertTrue(result.output.toLowerCase().contains("safepoint-biased"), "JEP 518 cooperative sampling metadata should be present");
    }

    /**
     * JEP 519: Compact object header flag is accepted as a product option.
     */
    @Test
    void compactObjectHeaders_ShouldBeAccepted_WhenUsingProductFlag() throws Exception {
        // Act
        ProcessResult result = run(List.of(
                javaBin().toString(),
                "-XX:+UseCompactObjectHeaders",
                "-version"
        ), tempDir);

        // Assertions
        assertEquals(0, result.exitCode, result.output);
    }

    /**
     * JEP 521: Generational Shenandoah flags should work when supported by runtime.
     */
    @Test
    void generationalShenandoah_ShouldBeConfigurable_WhenRuntimeSupportsShenandoah() throws Exception {
        // Act
        ProcessResult result = run(List.of(
                javaBin().toString(),
                "-XX:+UseShenandoahGC",
                "-XX:ShenandoahGCMode=generational",
                "-version"
        ), tempDir);

        // Assertions
        if (result.exitCode != 0) {
            Assumptions.assumeTrue(
                    !isShenandoahUnsupported(result.output),
                    "Shenandoah is unavailable on this runtime: " + result.output
            );
        }
        assertEquals(0, result.exitCode, result.output);
    }

    private record ProcessResult(int exitCode, String output) {
    }

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
        @ParameterizedTest(name = "Java 25 Primitive Test: {1} with {0}")
        void primitivePatterns_ShouldMatchPrimitiveType_WhenUsingInstanceOfAndSwitch(Object value, String typeLabel, Object expectedValue) {

            // 1. Act & Assertions: Java 25 switch usage with primitive pattern matching
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

            // 2. Act & Assert: Java 25 instanceof usage with primitive pattern matching
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

            assertTrue(isMatched, "The value should match its own local (primitive) type pattern.");
        }
    }
}
