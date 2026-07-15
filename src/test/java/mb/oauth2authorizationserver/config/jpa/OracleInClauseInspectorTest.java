package mb.oauth2authorizationserver.config.jpa;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class OracleInClauseInspectorTest {

    private OracleInClauseInspector inspector;

    @BeforeEach
    void setUp() {
        inspector = new OracleInClauseInspector();
    }

    @Test
    void inspect_ShouldReturnNull_WhenSqlIsNull() {
        assertThat(inspector.inspect(null), is(nullValue()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM product_asset WHERE status = ?",
            "SELECT * FROM product_asset WHERE name LIKE '%admin%'",
            ""
    })
    void inspect_ShouldReturnSqlUnchanged_WhenSqlDoesNotContainInClause(String sql) {
        assertThat(inspector.inspect(sql), is(sql));
    }

    @Test
    void inspect_ShouldReturnSqlUnchanged_WhenInClauseHas999OrFewerParameters() {
        String sql = "SELECT * FROM product_asset WHERE product_id IN (" + placeholders(999) + ")";

        assertThat(inspector.inspect(sql), is(sql));
    }

    @Test
    void inspect_ShouldReturnSqlUnchanged_WhenInClauseHasMoreThan999Parameters() {
        String sql = "SELECT * FROM product_asset WHERE product_id IN (" + placeholders(1000) + ")";

        assertThat(inspector.inspect(sql), is(sql));
    }

    @Test
    void inspect_ShouldReturnSqlUnchanged_WhenInClauseExceedsOracleFunctionLimit() {
        String sql = "SELECT * FROM product_asset WHERE product_id IN (" + placeholders(2000) + ")";

        assertThat(inspector.inspect(sql), is(sql));
    }

    @Test
    void inspect_ShouldReturnSqlUnchanged_WhenInKeywordUsesDifferentCasing() {
        String sql = "SELECT * FROM product_asset WHERE product_id in (" + placeholders(1000) + ")";

        assertThat(inspector.inspect(sql), is(sql));
    }

    @Test
    void inspect_ShouldReturnSqlUnchanged_WhenNotInClauseExceedsLimit() {
        String sql = "SELECT * FROM product_asset WHERE product_id NOT IN (" + placeholders(1000) + ")";

        assertThat(inspector.inspect(sql), is(sql));
    }

    @Test
    void inspect_ShouldReturnSqlUnchanged_WhenSqlContainsMultipleInClauses() {
        String smallInClause = placeholders(3);
        String largeInClause = placeholders(1000);
        String sql = "SELECT * FROM product_asset WHERE status IN (" + smallInClause + ") AND product_id IN (" + largeInClause + ")";

        assertThat(inspector.inspect(sql), is(sql));
    }

    @Test
    void inspect_ShouldReturnSqlUnchanged_WhenColumnUsesTableAlias() {
        String sql = "SELECT * FROM product_asset pa WHERE pa.product_id IN (" + placeholders(1000) + ")";

        assertThat(inspector.inspect(sql), is(sql));
    }

    @Test
    void inspect_ShouldReturnSqlUnchanged_WhenLargeInClauseHasNoExtractableColumnName() {
        String sql = "IN (" + placeholders(1000) + ")";

        assertThat(inspector.inspect(sql), is(sql));
    }

    @Test
    void inspect_ShouldReturnSqlUnchanged_WhenInClauseContainsSubquery() {
        String sql = "SELECT * FROM product_asset WHERE product_id IN (SELECT id FROM other_table)";

        assertThat(inspector.inspect(sql), is(sql));
    }

    @Test
    void inspect_ShouldReturnSqlUnchanged_WhenInClauseContainsLiterals() {
        String sql = "SELECT * FROM product_asset WHERE status IN ('A', 'B', 'C')";

        assertThat(inspector.inspect(sql), is(sql));
    }

    @Test
    void inspect_ShouldRemainUnchanged_WhenInspectIsCalledTwice() {
        String sql = "SELECT * FROM product_asset WHERE product_id IN (" + placeholders(1000) + ")";

        String rewrittenSql = inspector.inspect(sql);

        assertThat(inspector.inspect(rewrittenSql), is(rewrittenSql));
    }

    @Test
    void inspect_ShouldReturnSqlUnchanged_WhenStringColumnInClauseExceedsLimit() {
        String sql = "SELECT * FROM product_asset WHERE code IN (" + placeholders(1000) + ")";

        assertThat(inspector.inspect(sql), is(sql));
    }

    @Test
    void inspect_ShouldReturnSqlUnchanged_WhenLargeInClauseIsInsideSubquery() {
        String largeInClause = placeholders(1000);
        String sql = "SELECT * FROM product_asset WHERE product_id IN (SELECT id FROM other_table WHERE status IN (" + largeInClause + "))";

        assertThat(inspector.inspect(sql), is(sql));
    }

    private String placeholders(int count) {
        if (count <= 0) {
            return "";
        }
        return "?,".repeat(count - 1) + "?";
    }
}
