package mb.oauth2authorizationserver.config.jpa;

import org.junit.jupiter.api.Test;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class HibernatePropertiesCustomizerConfigTest {

    @Test
    void hibernatePropertiesCustomizer_ShouldRegisterOracleInClauseSettings() {
        HibernatePropertiesCustomizerConfig config = new HibernatePropertiesCustomizerConfig();
        HibernatePropertiesCustomizer customizer = config.hibernatePropertiesCustomizer();
        Map<String, Object> hibernateProperties = new HashMap<>();

        customizer.customize(hibernateProperties);

        assertThat(hibernateProperties.get("hibernate.session_factory.statement_inspector"), instanceOf(OracleInClauseInspector.class));
        assertThat(hibernateProperties.get("hibernate.query.in_clause_parameter_padding"), is(true));
        assertThat(hibernateProperties.get("hibernate.default_batch_fetch_size"), is(100));
    }

    @Test
    void oracleInClauseRewriteConfig_ShouldExposeDefaults() {
        OracleInClauseRewriteConfig rewriteConfig = OracleInClauseRewriteConfig.defaults();

        assertThat(rewriteConfig.maxParameterCount(), is(999));
        assertThat(rewriteConfig.oracleTableFunction(), is("sys.odcinumberlist"));
        assertThat(rewriteConfig.requireColumnExpression(), is(true));
        assertThat(rewriteConfig.enabled(), is(true));
    }
}
