package mb.oauth2authorizationserver.scripts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@code scripts/bump-dependents.sh}.
 *
 * <p>Dummy dependency graph used in every test:
 * <pre>
 *   common-utils ← no inter-module dependencies
 *   common-jpa ← common-utils
 *   common-history ← common-jpa
 * </pre>
 * <p>
 * Each test:
 * <ol>
 *   <li>Writes an initial {@code pom.xml} and commits it (becomes {@code HEAD~1}).
 *   <li>Writes a modified {@code pom.xml} and commits it (becomes {@code HEAD}).
 *   <li>Runs the script with {@code HEAD~1} as the base branch.
 *   <li>Asserts the resulting pom versions and/or exit code.
 * </ol>
 */
@DisplayName("bump-dependents.sh")
class BumpDependentsScriptTest extends ScriptTestSupport {

    @BeforeEach
    void setUpModules() throws Exception {
        copyDummyModulePom("common-utils");
        copyDummyModulePom("common-jpa");
        copyDummyModulePom("common-history");
    }

    // -------------------------------------------------------------------------

    @Test
    @DisplayName("patch bump on a module cascades to direct and transitive dependents")
    void shouldCascadePatchBumpTransitively() throws Exception {
        writeParentPom(v("1.0.0", "1.0.0", "1.0.0"));
        gitCommit("initial");

        writeParentPom(v("1.0.1", "1.0.0", "1.0.0"));
        gitCommit("bump common-utils patch");

        ScriptResult result = runScript("bump-dependents.sh");

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(readPomVersion("common-jpa")).isEqualTo("1.0.1");
        assertThat(readPomVersion("common-history")).isEqualTo("1.0.1");
    }

    @Test
    @DisplayName("minor bump cascades and resets patch to 0 on all dependents")
    void shouldCascadeMinorBumpAndResetPatch() throws Exception {
        writeParentPom(v("1.0.3", "1.0.3", "1.0.3"));
        gitCommit("initial");

        writeParentPom(v("1.1.0", "1.0.3", "1.0.3"));
        gitCommit("bump common-utils minor");

        ScriptResult result = runScript("bump-dependents.sh");

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(readPomVersion("common-jpa")).isEqualTo("1.1.0");
        assertThat(readPomVersion("common-history")).isEqualTo("1.1.0");
    }

    @Test
    @DisplayName("SNAPSHOT trigger causes cascaded bump to also carry -SNAPSHOT suffix")
    void shouldCascadeSnapshotSuffixToDependents() throws Exception {
        writeParentPom(v("1.0.0", "1.0.0", "1.0.0"));
        gitCommit("initial");

        // jpa bumped to a SNAPSHOT – history must follow with -SNAPSHOT
        writeParentPom(v("1.0.0", "1.0.1-SNAPSHOT", "1.0.0"));
        gitCommit("bump common-jpa to SNAPSHOT");

        ScriptResult result = runScript("bump-dependents.sh");

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(readPomVersion("common-history")).isEqualTo("1.0.1-SNAPSHOT");
    }

    @Test
    @DisplayName("-SNAPSHOT suffix is added to a manually bumped dependent that is missing it")
    void shouldAddSnapshotSuffixWhenManuallyBumpedDependentIsMissingIt() throws Exception {
        writeParentPom(v("1.0.0", "1.0.0", "1.0.0"));
        gitCommit("initial");

        // Developer manually bumped jpa to SNAPSHOT and history to 1.0.1 but forgot the suffix
        writeParentPom(v("1.0.0", "1.0.1-SNAPSHOT", "1.0.1"));
        gitCommit("bump jpa to snapshot, history manually to 1.0.1 (no snapshot)");

        ScriptResult result = runScript("bump-dependents.sh");

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(readPomVersion("common-history")).isEqualTo("1.0.1-SNAPSHOT");
        assertThat(result.output()).contains("SNAPSHOT suffix propagated");
    }

    @Test
    @DisplayName("already manually bumped dependents are not double-bumped")
    void shouldSkipAlreadyManuallyBumpedDependents() throws Exception {
        writeParentPom(v("1.0.0", "1.0.0", "1.0.0"));
        gitCommit("initial");

        // All three modules bumped manually in one commit
        writeParentPom(v("1.0.1", "1.0.1", "1.0.1"));
        gitCommit("manual bump of all modules");

        ScriptResult result = runScript("bump-dependents.sh");

        assertThat(result.exitCode()).as(result.output()).isZero();
        // Versions should remain exactly as set – no extra increment
        assertThat(readPomVersion("common-jpa")).isEqualTo("1.0.1");
        assertThat(readPomVersion("common-history")).isEqualTo("1.0.1");
    }

    @Test
    @DisplayName("no version changes → exit 0 with nothing-to-do message")
    void shouldExitSuccessfullyWhenNoVersionChanges() throws Exception {
        writeParentPom(v("1.0.0", "1.0.0", "1.0.0"));
        gitCommit("initial");
        // Advance HEAD without touching pom.xml
        gitEmptyCommit();

        ScriptResult result = runScript("bump-dependents.sh");

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("No module version changes detected");
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Builds the ordered version map: utils → jpa → history.
     */
    private Map<String, String> v(String utils, String jpa, String history) {
        var map = new LinkedHashMap<String, String>();
        map.put("common-utils", utils);
        map.put("common-jpa", jpa);
        map.put("common-history", history);
        return map;
    }
}
