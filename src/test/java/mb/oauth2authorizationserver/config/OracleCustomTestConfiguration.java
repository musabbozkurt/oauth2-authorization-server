package mb.oauth2authorizationserver.config;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Generic test configuration class for Oracle database integration testing using Testcontainers.
 *
 * <p>This configuration starts an Oracle XE container and sets up system properties
 * that can be used in test classes to configure Oracle datasources for any application.</p>
 *
 * <h3>Usage</h3>
 * <p>To use this configuration in your test class, include it in the {@code @SpringBootTest} annotation
 * and add the {@code @TestPropertySource} annotation to map the system properties to your specific datasource properties:</p>
 *
 * <h4>Example for custom datasource:</h4>
 * <pre>{@code
 * @TestPropertySource(properties = {
 *         "custom.datasource.jdbc-url=${ORACLE_DATASOURCE_JDBC_URL}",
 *         "custom.datasource.username=${ORACLE_DATASOURCE_USERNAME}",
 *         "custom.datasource.password=${ORACLE_DATASOURCE_PASSWORD}",
 * })
 * @SpringBootTest(classes = {OracleIntegrationConfiguration.class, ...})
 * class CustomIntegrationTest {
 *     // Your test code
 * }
 * }</pre>
 *
 * <h4>Example for custom datasource:</h4>
 * <pre>{@code
 * @TestPropertySource(properties = {
 *         "spring.datasource.url=${ORACLE_DATASOURCE_JDBC_URL}",
 *         "spring.datasource.username=${ORACLE_DATASOURCE_USERNAME}",
 *         "spring.datasource.password=${ORACLE_DATASOURCE_PASSWORD}",
 *         "spring.datasource.driver-class-name=oracle.jdbc.OracleDriver"
 * })
 * @SpringBootTest(classes = {OracleIntegrationConfiguration.class, ...})
 * class CustomIntegrationTest {
 *     // Your test code
 * }
 * }</pre>
 *
 * <h4>Example for multiple datasources:</h4>
 * <pre>{@code
 * @TestPropertySource(properties = {
 *         "primary.datasource.jdbc-url=${ORACLE_DATASOURCE_JDBC_URL}",
 *         "primary.datasource.username=${ORACLE_DATASOURCE_USERNAME}",
 *         "primary.datasource.password=${ORACLE_DATASOURCE_PASSWORD}",
 *         "secondary.datasource.jdbc-url=${ORACLE_DATASOURCE_JDBC_URL}",
 *         "secondary.datasource.username=${ORACLE_DATASOURCE_USERNAME}",
 *         "secondary.datasource.password=${ORACLE_DATASOURCE_PASSWORD}",
 * })
 * @SpringBootTest(classes = {OracleIntegrationConfiguration.class, ...})
 * class MultiDatasourceIntegrationTest {
 *     // Your test code
 * }
 * }</pre>
 * <h4>Example for application.yml:</h4>
 * <pre>{@docRoot}/src/test/resources/application.yml
 * spring:
 *   datasource:
 *     url: ${ORACLE_DATASOURCE_JDBC_URL}
 *     username: ${ORACLE_DATASOURCE_USERNAME}
 *     password: ${ORACLE_DATASOURCE_PASSWORD}
 *     type: com.zaxxer.hikari.HikariDataSource
 *     driver-class-name: oracle.jdbc.OracleDriver
 * </pre>
 */
@Slf4j
@TestConfiguration
public class OracleCustomTestConfiguration {

    @Container
    public static final OracleContainer oracle = new OracleContainer(DockerImageName.parse("gvenzl/oracle-free:23-slim-faststart"))
            .withExposedPorts(1521)
            .withReuse(true);

    public static class OracleInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(@Nonnull ConfigurableApplicationContext context) {
            oracle.start();

            String jdbcUrl = oracle.getJdbcUrl();
            String username = oracle.getUsername();
            String password = oracle.getPassword();

            String oracleSchemaName = context.getEnvironment().getProperty("oracle-schema-name");
            String namespace = context.getEnvironment().getProperty("namespace");

            if (StringUtils.isNotBlank(oracleSchemaName) && StringUtils.isNotBlank(namespace)) {
                String schemaUser = ("%s_%s".formatted(oracleSchemaName, namespace)).toUpperCase();
                try (Connection connection = DriverManager.getConnection(jdbcUrl, "SYSTEM", password);
                     Statement statement = connection.createStatement()) {
                    boolean userExists;
                    try (ResultSet resultSet = statement.executeQuery("SELECT 1 FROM ALL_USERS WHERE USERNAME = '%s'".formatted(schemaUser))) {
                        userExists = resultSet.next();
                    }
                    if (!userExists) {
                        statement.execute("CREATE USER %s IDENTIFIED BY %s QUOTA UNLIMITED ON USERS".formatted(schemaUser, password));
                        statement.execute("GRANT CONNECT, RESOURCE, DBA TO %s".formatted(schemaUser));
                    }
                } catch (SQLException e) {
                    log.warn("Failed to initialize Oracle test schema: {}, Exception: {}", schemaUser, ExceptionUtils.getStackTrace(e));
                }

                username = schemaUser;
            }

            System.setProperty("ORACLE_DATASOURCE_JDBC_URL", jdbcUrl);
            System.setProperty("ORACLE_DATASOURCE_USERNAME", username);
            System.setProperty("ORACLE_DATASOURCE_PASSWORD", password);

            // Override Spring datasource & JPA properties so the context uses Oracle
            // instead of the default MariaDB from test application.yml.
            // Without this, Flyway-disabled tests fail on schema validation because
            // Hibernate validates against an empty MariaDB that has no tables.
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                    context,
                    "spring.datasource.url=%s".formatted(jdbcUrl),
                    "spring.datasource.username=%s".formatted(username),
                    "spring.datasource.password=%s".formatted(password),
                    "spring.datasource.driver-class-name=oracle.jdbc.OracleDriver",
                    "spring.jpa.hibernate.ddl-auto=create-drop"
            );
        }
    }
}
