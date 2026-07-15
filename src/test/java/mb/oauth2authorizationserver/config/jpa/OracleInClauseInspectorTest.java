package mb.oauth2authorizationserver.config.jpa;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
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
    void inspect_ShouldRewriteInClause_WhenInClauseHasMoreThan999Parameters() {
        int parameterCount = 1000;
        String sql = "SELECT * FROM product_asset WHERE product_id IN (" + placeholders(parameterCount) + ")";

        String rewrittenSql = inspector.inspect(sql);

        assertThat(rewrittenSql, is(not(sql)));
        assertThat(rewrittenSql, containsString("product_id IN (SELECT column_value FROM TABLE(sys.odcinumberlist("));
        assertThat(rewrittenSql, containsString(placeholders(parameterCount) + ")))"));
        assertThat(countPlaceholders(rewrittenSql), is(parameterCount));
    }

    @Test
    void inspect_ShouldRewriteInClause_WhenInKeywordUsesDifferentCasing() {
        String sql = "SELECT * FROM product_asset WHERE product_id in (" + placeholders(1000) + ")";

        String rewrittenSql = inspector.inspect(sql);

        assertThat(rewrittenSql.toLowerCase(), containsString("in (select column_value from table(sys.odcinumberlist("));
    }

    @Test
    void inspect_ShouldRewriteNotInClause_WhenPlaceholderListExceedsLimit() {
        String sql = "SELECT * FROM product_asset WHERE product_id NOT IN (" + placeholders(1000) + ")";

        String rewrittenSql = inspector.inspect(sql);

        assertThat(rewrittenSql, containsString("product_id NOT IN (SELECT column_value FROM TABLE(sys.odcinumberlist("));
    }

    @Test
    void inspect_ShouldRewriteOnlyLargeInClauses_WhenSqlContainsMultipleInClauses() {
        String smallInClause = placeholders(3);
        String largeInClause = placeholders(1000);
        String sql = "SELECT * FROM product_asset WHERE status IN (" + smallInClause + ") AND product_id IN (" + largeInClause + ")";

        String rewrittenSql = inspector.inspect(sql);

        assertThat(rewrittenSql, containsString("status IN (" + smallInClause + ")"));
        assertThat(rewrittenSql, containsString("product_id IN (SELECT column_value FROM TABLE(sys.odcinumberlist(" + largeInClause + ")))"));
    }

    @Test
    void inspect_ShouldRewriteInClause_WhenColumnUsesTableAlias() {
        String sql = "SELECT * FROM product_asset pa WHERE pa.product_id IN (" + placeholders(1000) + ")";

        String rewrittenSql = inspector.inspect(sql);

        assertThat(rewrittenSql, containsString("pa.product_id IN (SELECT column_value FROM TABLE(sys.odcinumberlist("));
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
    void inspect_ShouldUseConfiguredOracleFunction_WhenCustomConfigIsProvided() {
        OracleInClauseInspector stringInspector = new OracleInClauseInspector(OracleInClauseRewriteConfig.forStrings());
        String sql = "SELECT * FROM product_asset WHERE code IN (" + placeholders(1000) + ")";

        String rewrittenSql = stringInspector.inspect(sql);

        assertThat(rewrittenSql, containsString("TABLE(sys.odcivarchar2list("));
    }

    @Test
    void inspect_ShouldReturnSqlUnchanged_WhenRewriteIsDisabled() {
        OracleInClauseInspector disabledInspector = new OracleInClauseInspector(
                new OracleInClauseRewriteConfig(999, "sys.odcinumberlist", true, false));
        String sql = "SELECT * FROM product_asset WHERE product_id IN (" + placeholders(1000) + ")";

        assertThat(disabledInspector.inspect(sql), is(sql));
    }

    @Test
    void inspect_ShouldReturnSqlUnchanged_WhenLargeInClauseIsInsideSubquery() {
        String largeInClause = placeholders(1000);
        String sql = "SELECT * FROM product_asset WHERE product_id IN (SELECT id FROM other_table WHERE status IN (" + largeInClause + "))";

        assertThat(inspector.inspect(sql), is(sql));
    }

    private static String placeholders(int count) {
        if (count <= 0) {
            return "";
        }
        return "?,".repeat(count - 1) + "?";
    }

    private static int countPlaceholders(String sql) {
        return (int) sql.chars().filter(ch -> ch == '?').count();
    }
}
