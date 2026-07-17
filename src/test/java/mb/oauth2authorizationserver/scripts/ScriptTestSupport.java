package mb.oauth2authorizationserver.scripts;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Base class for shell-script integration tests.
 *
 * <p>Before each test it:
 * <ol>
 *   <li>Creates an isolated temporary directory ({@code workDir}).
 *   <li>Copies both scripts into {@code workDir/scripts/} so that the scripts' own
 *       {@code SCRIPT_DIR} variable resolves to {@code workDir/} and they read/write
 *       {@code workDir/pom.xml} and scan {@code workDir/common-*pom.xml}.
 *   <li>Initializes a bare git repository in {@code workDir}.</li>
 * </ol>
 * After each test it deletes the temporary directory.
 */
abstract class ScriptTestSupport {

    protected Path workDir;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @BeforeEach
    void setUpWorkspace() throws Exception {
        workDir = Files.createTempDirectory("script-test-");
        Files.createDirectories(workDir.resolve("scripts"));

        // Prefer scripts under the module root; fall back to parent for alternate launch contexts.
        Path baseDir = Paths.get(System.getProperty("project.basedir"));
        Path sourceScripts = baseDir.resolve("docs/scripts");
        if (!Files.exists(sourceScripts)) {
            sourceScripts = baseDir.getParent().resolve("scripts");
        }

        for (String script : List.of("bump-dependents.sh", "validate-version-cascade.sh")) {
            Path dst = workDir.resolve("scripts").resolve(script);
            Files.copy(sourceScripts.resolve(script), dst);
            if (!dst.toFile().setExecutable(true)) {
                throw new IOException("Could not mark script executable: " + dst);
            }
        }

        // Initialize a git repo with a stable identity
        exec("git", "init");
        exec("git", "config", "user.email", "test@test.com");
        exec("git", "config", "user.name", "Test");
        exec("git", "config", "commit.gpgsign", "false");
    }

    @AfterEach
    void tearDownWorkspace() throws IOException {
        if (workDir != null && Files.exists(workDir)) {
            try (Stream<Path> walk = Files.walk(workDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException _) {
                                // ignore
                            }
                        });
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers – pom setup
    // -------------------------------------------------------------------------

    /**
     * Writes {@code workDir/pom.xml} containing one {@code <module.version>} property per entry.
     * The map should be a {@link java.util.LinkedHashMap} when insertion order matters.
     */
    protected void writeParentPom(Map<String, String> versions) throws IOException {
        String pom = readResource("dummy-poms/parent-pom.xml");
        for (Map.Entry<String, String> version : versions.entrySet()) {
            String token = "__"
                    + version.getKey().replace('-', '_').toUpperCase(Locale.ROOT)
                    + "_VERSION__";
            pom = pom.replace(token, version.getValue());
        }
        Files.writeString(workDir.resolve("pom.xml"), pom);
    }

    /**
     * Copies a minimal dummy module pom from test resources into
     * {@code workDir/<moduleName>/pom.xml}.
     */
    protected void copyDummyModulePom(String moduleName) throws IOException {
        Path dir = workDir.resolve(moduleName);
        Files.createDirectories(dir);
        String modulePom = readResource("dummy-poms/modules/" + moduleName + "-pom.xml");
        Files.writeString(dir.resolve("pom.xml"), modulePom);
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
        var pb = new ProcessBuilder("bash", workDir.resolve("scripts/" + script).toString(), "HEAD~1")
                .directory(workDir.toFile())
                .redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes());
        return new ScriptResult(p.waitFor(), output);
    }

    // -------------------------------------------------------------------------
    // Helpers – assertions
    // -------------------------------------------------------------------------

    /**
     * Reads the value of {@code <module.version>} from {@code workDir/pom.xml}.
     */
    protected String readPomVersion(String module) throws IOException {
        String pom = Files.readString(workDir.resolve("pom.xml"));
        String open = "<" + module + ".version>";
        String close = "</" + module + ".version>";
        int s = pom.indexOf(open);
        if (s < 0) return null;
        int vs = s + open.length();
        int e = pom.indexOf(close, vs);
        return e < 0 ? null : pom.substring(vs, e);
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private void exec(String... cmd) throws Exception {
        var p = new ProcessBuilder(cmd)
                .directory(workDir.toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        p.waitFor();
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
