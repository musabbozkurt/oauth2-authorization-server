package mb.oauth2authorizationserver.scripts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@code scripts/validate-version-cascade.sh}.
 *
 * <p>Dummy dependency graph used in every test:
 * <pre>
 *   common-utils ← no inter-module dependencies
 *   common-jpa ← common-utils
 *   common-history ← common-jpa
 * </pre>
 * <p>
 * Each test commits an initial state as {@code HEAD~1}, commits a modified pom as
 * {@code HEAD}, then runs the validate script with {@code HEAD~1} as the base branch,
 * and asserts the exit code and output.
 */
@DisplayName("validate-version-cascade.sh")
class ValidateVersionCascadeScriptTest extends ScriptTestSupport {

    @BeforeEach
    void setUpModules() throws Exception {
        copyDummyModulePom("common-utils");
        copyDummyModulePom("common-jpa");
        copyDummyModulePom("common-history");
    }

    // -------------------------------------------------------------------------

    @Test
    @DisplayName("exit 0 when all dependents of a bumped module are also bumped")
    void shouldPassWhenAllDependentsBumped() throws Exception {
        writeParentPom(v("1.0.0", "1.0.0", "1.0.0"));
        gitCommit("initial");

        writeParentPom(v("1.0.1", "1.0.1", "1.0.1"));
        gitCommit("bump all modules");

        ScriptResult result = runScript("validate-version-cascade.sh");

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("All dependent modules have been properly bumped");
    }

    @Test
    @DisplayName("exit 1 when a transitive dependent is not bumped after its dependency was bumped")
    void shouldFailWhenDependentNotBumped() throws Exception {
        writeParentPom(v("1.0.0", "1.0.0", "1.0.0"));
        gitCommit("initial");

        // jpa bumped but history (depends on jpa) left unchanged
        writeParentPom(v("1.0.0", "1.0.1", "1.0.0"));
        gitCommit("bump common-jpa only");

        ScriptResult result = runScript("validate-version-cascade.sh");

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.output()).contains("NOT bumped");
        assertThat(result.output()).contains("common-history");
    }

    @Test
    @DisplayName("exit 0 when SNAPSHOT trigger and all dependents also carry -SNAPSHOT suffix")
    void shouldPassWhenAllDependentsHaveSnapshotSuffix() throws Exception {
        writeParentPom(v("1.0.0", "1.0.0", "1.0.0"));
        gitCommit("initial");

        writeParentPom(v("1.0.0", "1.0.1-SNAPSHOT", "1.0.1-SNAPSHOT"));
        gitCommit("bump jpa and history both to SNAPSHOT");

        ScriptResult result = runScript("validate-version-cascade.sh");

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("correct -SNAPSHOT suffix");
    }

    @Test
    @DisplayName("exit 1 when SNAPSHOT trigger has a dependent missing the -SNAPSHOT suffix")
    void shouldFailWhenDependentMissingSnapshotSuffix() throws Exception {
        writeParentPom(v("1.0.0", "1.0.0", "1.0.0"));
        gitCommit("initial");

        // jpa bumped to SNAPSHOT, history bumped numerically but without -SNAPSHOT
        writeParentPom(v("1.0.0", "1.0.1-SNAPSHOT", "1.0.1"));
        gitCommit("bump jpa to snapshot, history to 1.0.1 (no snapshot)");

        ScriptResult result = runScript("validate-version-cascade.sh");

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.output()).contains("missing the -SNAPSHOT suffix");
        assertThat(result.output()).contains("common-history");
    }

    @Test
    @DisplayName("exit 0 with nothing-to-validate message when no module versions changed")
    void shouldPassWhenNoVersionChanges() throws Exception {
        writeParentPom(v("1.0.0", "1.0.0", "1.0.0"));
        gitCommit("initial");
        // Advance HEAD without changing pom.xml
        gitEmptyCommit();

        ScriptResult result = runScript("validate-version-cascade.sh");

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("Nothing to validate");
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

