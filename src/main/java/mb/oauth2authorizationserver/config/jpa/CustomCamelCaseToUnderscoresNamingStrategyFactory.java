package mb.oauth2authorizationserver.config.jpa;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * Factory for creating {@link CustomPhysicalNamingStrategy} instances.
 * <p>
 * This factory reads {@code namespace} and {@code spring.profiles.active} configuration properties
 * to produce naming strategy instances that append a namespace suffix to schema names in
 * non-production environments (i.e. any profile other than {@code prod} or {@code stage}).
 * <p>
 * It also provides overloaded {@code create} methods that additionally configure the
 * {@code connectionInitSql} on a given {@link HikariDataSource} so that every new connection
 * opens with {@code ALTER SESSION SET CURRENT_SCHEMA} pointing at the resolved schema.
 *
 * <h3>Usage in Configuration Classes:</h3>
 *
 * <h4>Single Data Source:</h4>
 * <pre>
 * {@code
 * @Configuration
 * @RequiredArgsConstructor
 * public class DbConfig {
 *
 *     private final CustomCamelCaseToUnderscoresNamingStrategyFactory customCamelCaseToUnderscoresNamingStrategyFactory;
 *
 *     @Bean
 *     public CustomPhysicalNamingStrategy customPhysicalNamingStrategy(HikariDataSource hikariDataSource) {
 *         return customCamelCaseToUnderscoresNamingStrategyFactory.create(hikariDataSource, SchemaConstants.MY_SCHEMA_NAME);
 *     }
 * }
 * }
 * </pre>
 *
 * <h4>Multiple Data Sources:</h4>
 * <pre>
 * {@code
 * @Configuration
 * @RequiredArgsConstructor
 * public class AssetDbConfig {
 *
 *     private final CustomCamelCaseToUnderscoresNamingStrategyFactory customCamelCaseToUnderscoresNamingStrategyFactory;
 *
 *     @Bean
 *     public LocalContainerEntityManagerFactoryBean entityManagerFactory(HikariDataSource dataSource) {
 *         Map<String, Object> properties = new HashMap<>();
 *
 *         // Enable namespace-based schema naming and configure the data source schema
 *         properties.put("hibernate.physical_naming_strategy", customCamelCaseToUnderscoresNamingStrategyFactory.create(dataSource, SchemaConstants.MY_SCHEMA_NAME));
 *
 *         // OR
 *
 *         // Disable namespace-based schema naming and configure the data source schema
 *         properties.put("hibernate.physical_naming_strategy", customCamelCaseToUnderscoresNamingStrategyFactory.create(false, dataSource, SchemaConstants.MY_SCHEMA_NAME));
 *
 *         // OR
 *
 *         // Only naming strategy, without data source configuration
 *         properties.put("hibernate.physical_naming_strategy", customCamelCaseToUnderscoresNamingStrategyFactory.create());
 *
 *         // OR
 *
 *         // Disable namespace-based schema naming, without data source configuration
 *         properties.put("hibernate.physical_naming_strategy", customCamelCaseToUnderscoresNamingStrategyFactory.create(false));
 *
 *         LocalContainerEntityManagerFactoryBean localContainerEntityManagerFactoryBean = new LocalContainerEntityManagerFactoryBean();
 *         localContainerEntityManagerFactoryBean.setJpaPropertyMap(properties);
 *         return localContainerEntityManagerFactoryBean;
 *     }
 * }
 * }
 * </pre>
 *
 * @see CustomPhysicalNamingStrategy
 */
@Slf4j
@Component
public class CustomCamelCaseToUnderscoresNamingStrategyFactory implements BeanPostProcessor {

    @Value("${namespace:#{null}}")
    private String namespace;

    @Value("${spring.profiles.active:#{null}}")
    private String activeProfile;

    @Value("${oracle-schema-name:OAUTH2_AUTHORIZATION_SERVER}")
    private String defaultSchemaName;

    /**
     * Intercepts HikariDataSource beans right after creation and before they are started or sealed,
     * applying the required connection initialization parameters safely at context bootstrap.
     */
    @Override
    public Object postProcessBeforeInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
        if (bean instanceof HikariDataSource hikariDataSource) {
            try {
                // Pre-configure the connection block dynamically on application startup
                applyInitSqlConfiguration(true, hikariDataSource, defaultSchemaName);
                log.info("Successfully post-processed and attached Turkish Linguistic rules to HikariDataSource.");
            } catch (Exception e) {
                log.error("Failed to post-process HikariDataSource configuration setup. Exception: {}", ExceptionUtils.getStackTrace(e));
            }
        }
        return bean;
    }

    /**
     * Creates a naming strategy with namespace suffix enabled and configures the given
     * {@link HikariDataSource} to run {@code ALTER SESSION SET CURRENT_SCHEMA = <schema>}
     * on every new connection, where {@code <schema>} is resolved from the provided base
     * schema name using the active profile and namespace.
     *
     * @param hikariDataSource the data source whose {@code connectionInitSql} will be set
     * @param schema           the base schema name (e.g. {@code my_schema});
     *                         the namespace suffix is appended automatically in non-production profiles
     * @return a new {@link CustomPhysicalNamingStrategy} instance with namespace suffix enabled
     */
    public CustomPhysicalNamingStrategy create(HikariDataSource hikariDataSource, String schema) {
        return create(true, hikariDataSource, schema);
    }

    /**
     * Creates a naming strategy with a configurable namespace suffix and configures the given
     * {@link HikariDataSource} to run {@code ALTER SESSION SET CURRENT_SCHEMA = <schema>}
     * on every new connection, where {@code <schema>} is resolved from the provided base
     * schema name using the active profile and namespace.
     *
     * @param enabled          {@code true} to append the namespace suffix to schema names (e.g. {@code my_schema_dev}),
     *                         {@code false} to use the schema name as-is
     * @param hikariDataSource the data source whose {@code connectionInitSql} will be set
     * @param schema           the base schema name (e.g. {@code my_schema});
     *                         the namespace suffix is appended automatically in non-production profiles
     * @return a new {@link CustomPhysicalNamingStrategy} instance
     */
    public CustomPhysicalNamingStrategy create(boolean enabled, HikariDataSource hikariDataSource, String schema) {
        CustomPhysicalNamingStrategy strategy = create(enabled);
        try {
            applyInitSqlConfiguration(enabled, hikariDataSource, schema);
        } catch (Exception e) {
            log.error("Failed to set connectionInitSql on HikariDataSource. Exception: {}", ExceptionUtils.getStackTrace(e));
        }
        return strategy;
    }

    /**
     * Creates a naming strategy instance with namespace suffix enabled by default.
     * <p>
     * Use this overload when no {@link HikariDataSource} configuration is needed.
     * Equivalent to calling {@link #create(boolean) create(true)}.
     *
     * @return a new {@link CustomPhysicalNamingStrategy} instance with namespace suffix enabled
     */
    public CustomPhysicalNamingStrategy create() {
        return create(true);
    }

    /**
     * Creates a naming strategy instance with a configurable namespace suffix.
     * <p>
     * Use this overload when no {@link HikariDataSource} configuration is needed.
     *
     * @param enabled {@code true} to append the namespace suffix to schema names (e.g. {@code my_schema_dev}),
     *                {@code false} to use schema names as-is
     * @return a new {@link CustomPhysicalNamingStrategy} instance
     */
    public CustomPhysicalNamingStrategy create(boolean enabled) {
        return new CustomPhysicalNamingStrategy(null, namespace, activeProfile, enabled);
    }

    /**
     * Shared helper to cleanly evaluate pool state and format the PL/SQL execution payload block.
     */
    private void applyInitSqlConfiguration(boolean enabled, HikariDataSource hikariDataSource, String schema) {
        if (!isOracle(hikariDataSource)) {
            log.info("HikariDataSource is not Oracle. Skipping Turkish linguistic init SQL.");
            return;
        }

        // Check if the Hikari init SQL already contains our Turkish linguistic configurations
        String existingInitSql = hikariDataSource.getConnectionInitSql();
        if (existingInitSql != null && existingInitSql.contains("GENERIC_M_AI")) {
            log.info("HikariDataSource connectionInitSql already configured with Turkish linguistic rules. Skipping execution.");
            return;
        }

        // Check if it's running/sealed only when we actually need to change the configuration
        if (hikariDataSource.isRunning()) {
            log.warn("HikariDataSource is already running and sealed. Cannot append connectionInitSql.");
            return;
        }

        // We wrap the executions in a PL/SQL anonymous block so Hikari can send it as a single execution string.
        CustomPhysicalNamingStrategy strategy = create(enabled);
        StringBuilder initSqlBuilder = new StringBuilder("BEGIN ");

        if (enabled) {
            initSqlBuilder.append("EXECUTE IMMEDIATE 'ALTER SESSION SET CURRENT_SCHEMA = %s'; ".formatted(strategy.resolveSchema(schema)));
        }

        // Globally inject Turkish linguistic adjustments to cover all Native Queries, JPQL, and Criteria API
        initSqlBuilder.append("EXECUTE IMMEDIATE 'ALTER SESSION SET NLS_COMP = LINGUISTIC'; ");
        initSqlBuilder.append("EXECUTE IMMEDIATE 'ALTER SESSION SET NLS_SORT = GENERIC_M_AI'; ");
        initSqlBuilder.append("END;");

        hikariDataSource.setConnectionInitSql(initSqlBuilder.toString());
        log.info("Successfully attached Schema and Turkish Linguistic rules to HikariDataSource initialization SQL.");
    }

    private boolean isOracle(HikariDataSource hikariDataSource) {
        String jdbcUrl = hikariDataSource.getJdbcUrl();
        return jdbcUrl != null && jdbcUrl.startsWith("jdbc:oracle");
    }
}
