package mb.oauth2authorizationserver.config.jpa;

import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class to customize Hibernate properties for the application.
 * This configuration includes a custom StatementInspector to handle Oracle IN clause limitations,
 * enables padding for IN clause parameters, and sets a default batch fetch size for optimized fetching.
 * <p>
 * Links:
 * - <a href="https://github.com/hibernate/hibernate-orm/pull/6555">Hibernate ORM PR #6555</a>
 */
@Configuration
public class HibernatePropertiesCustomizerConfig {

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return hibernateProperties -> {
            hibernateProperties.put("hibernate.session_factory.statement_inspector", new OracleInClauseInspector());
            // Enable padding for IN clause parameters to avoid exceeding Oracle's limit of 1000 items in an IN clause
            // Automatically pads the IN clause list sizes to powers of 2 (e.g., 4, 8, 16, 512, 1024...)
            hibernateProperties.put("hibernate.query.in_clause_parameter_padding", true);
            // Set the default batch fetch size to 100 to optimize fetching of collections and associations
            // This helps reduce the number of queries executed when fetching related entities in batches
            hibernateProperties.put("hibernate.default_batch_fetch_size", 100);
        };
    }
}
