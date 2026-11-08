package mb.oauth2authorizationserver.scripts;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Base class for shell-script integration tests.
 *
 * <p>Supports Windows (Git Bash), macOS and Linux.
 */
abstract class ScriptTestSupport {

    protected Path workDir;

    private String bashExecutable;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @BeforeEach
    void setUpWorkspace() throws Exception {
        workDir = Files.createTempDirectory("script-test-");

        Files.createDirectories(workDir.resolve("scripts"));

        bashExecutable = findBashExecutable();

        // Prefer scripts under the module root; fall back to parent.
        Path baseDir = Paths.get(System.getProperty("project.basedir"));

        Path sourceScripts = baseDir.resolve("docs/scripts");

        if (!Files.exists(sourceScripts)) {
            sourceScripts = baseDir.getParent().resolve("scripts");
        }

        for (String script : List.of("bump-dependents.sh", "validate-version-cascade.sh")) {
            Path dst = workDir.resolve("scripts").resolve(script);
            Files.copy(sourceScripts.resolve(script), dst);
        }

        // Initialize a Git repository with a stable identity.
        exec("git", "init");
        exec("git", "config", "core.autocrlf", "false");
        exec("git", "config", "core.safecrlf", "false");
        exec("git", "config", "user.email", "test@test.com");
        exec("git", "config", "user.name", "Test");
        exec("git", "config", "commit.gpgsign", "false");
    }

    @AfterEach
    void tearDownWorkspace() throws IOException {
        if (workDir != null && Files.exists(workDir)) {
            try (Stream<Path> walk = Files.walk(workDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException _) {
                                // Best-effort cleanup.
                            }
                        });
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers - platform
    // -------------------------------------------------------------------------

    /**
     * Finds Bash executable.
     *
     * <p>On Windows, supports Git Bash installed in its default locations.
     * The GIT_BASH environment variable can override the executable path.
     */
    private String findBashExecutable() throws IOException {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        if (!os.contains("win")) {
            return "bash";
        }

        String configuredBash = System.getenv("GIT_BASH");

        if (configuredBash != null && !configuredBash.isBlank()) {
            if (Files.isExecutable(Path.of(configuredBash))) {
                return configuredBash;
            }

            throw new IOException("GIT_BASH points to an invalid executable: " + configuredBash);
        }

        List<Path> candidates = new ArrayList<>();

        String programFiles = System.getenv("ProgramFiles");
        String programFilesX86 = System.getenv("ProgramFiles(x86)");

        if (programFiles != null) {
            candidates.add(Path.of(programFiles, "Git", "bin", "bash.exe"));
        }

        if (programFilesX86 != null) {
            candidates.add(Path.of(programFilesX86, "Git", "bin", "bash.exe"));
        }

        String localAppData = System.getenv("LOCALAPPDATA");

        if (localAppData != null) {
            candidates.add(Path.of(localAppData, "Programs", "Git", "bin", "bash.exe"));
        }

        for (Path candidate : candidates) {
            if (Files.isExecutable(candidate)) {
                return candidate.toString();
            }
        }

        // Last resort: Git Bash available through PATH.
        return "bash";
    }

    /**
     * Converts Windows paths to Git Bash-compatible paths.
     */
    private String toBashPath(Path path) {
        String absolutePath = path.toAbsolutePath()
                .toString()
                .replace('\\', '/');

        if (absolutePath.length() >= 2 && absolutePath.charAt(1) == ':') {
            char drive = Character.toLowerCase(absolutePath.charAt(0));
            return "/" + drive + absolutePath.substring(2);
        }
        return absolutePath;
    }

    // -------------------------------------------------------------------------
    // Helpers - pom setup
    // -------------------------------------------------------------------------

    /**
     * Writes {@code workDir/pom.xml} containing one {@code <module.version>} property per entry.
     * The map should be a {@link java.util.LinkedHashMap} when insertion order matters.
     */
    protected void writeParentPom(Map<String, String> versions) throws IOException {
        String pom = readResource("dummy-poms/parent-pom.xml");
        for (Map.Entry<String, String> version : versions.entrySet()) {
            String token = "__" + version.getKey().replace('-', '_').toUpperCase(Locale.ROOT) + "_VERSION__";
            pom = pom.replace(token, version.getValue());
        }

        Files.writeString(workDir.resolve("pom.xml"), pom, StandardCharsets.UTF_8);
    }

    /**
     * Copies a minimal dummy module pom from test resources into
     * {@code workDir/<moduleName>/pom.xml}.
     */
    protected void copyDummyModulePom(String moduleName) throws IOException {
        Path dir = workDir.resolve(moduleName);
        Files.createDirectories(dir);
        String modulePom = readResource("dummy-poms/modules/" + moduleName + "-pom.xml");
        Files.writeString(dir.resolve("pom.xml"), modulePom, StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // Helpers – git
    // -------------------------------------------------------------------------

    protected void gitCommit(String message) throws Exception {
        exec("git", "add", "-A");
        exec("git", "commit", "-m", message);
    }

    /**
     * Creates a git commit with no file changes (used to advance HEAD without touching the pom).
     */
    protected void gitEmptyCommit() throws Exception {
        exec("git", "commit", "--allow-empty", "-m", "no-op");
    }

    // -------------------------------------------------------------------------
    // Helpers – script execution
    // -------------------------------------------------------------------------

    protected ScriptResult runScript(String script) throws Exception {
        Path scriptPath = workDir.resolve("scripts").resolve(script);
        ProcessBuilder pb = new ProcessBuilder(bashExecutable, toBashPath(scriptPath), "HEAD~1")
                .directory(workDir.toFile())
                .redirectErrorStream(true);

        Process process = pb.start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        int exitCode = process.waitFor();

        return new ScriptResult(exitCode, output);
    }

    // -------------------------------------------------------------------------
    // Helpers – assertions
    // -------------------------------------------------------------------------

    /**
     * Reads the value of {@code <module.version>} from {@code workDir/pom.xml}.
     */
    protected String readPomVersion(String module) throws IOException {
        String pom = Files.readString(workDir.resolve("pom.xml"), StandardCharsets.UTF_8);

        String open = "<" + module + ".version>";
        String close = "</" + module + ".version>";

        int start = pom.indexOf(open);

        if (start < 0) {
            return null;
        }

        int versionStart = start + open.length();

        int end = pom.indexOf(close, versionStart);

        return end < 0 ? null : pom.substring(versionStart, end);
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private void exec(String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command)
                .directory(workDir.toFile())
                .redirectErrorStream(true);

        Process process = pb.start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IllegalStateException("Command failed (" + exitCode + "): " + String.join(" ", command) + System.lineSeparator() + output);
        }
    }

    private String readResource(String resourcePath) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Missing test resource: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // -------------------------------------------------------------------------
    // Result type
    // -------------------------------------------------------------------------

    protected record ScriptResult(int exitCode, String output) {
    }
}
