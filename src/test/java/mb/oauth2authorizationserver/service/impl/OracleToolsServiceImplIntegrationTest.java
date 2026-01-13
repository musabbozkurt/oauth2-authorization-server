package mb.oauth2authorizationserver.service.impl;

import mb.oauth2authorizationserver.api.request.DatabaseConfig;
import mb.oauth2authorizationserver.api.request.MigrationRequest;
import mb.oauth2authorizationserver.api.request.ScriptGenerationRequest;
import mb.oauth2authorizationserver.api.response.ScriptGenerationResponse;
import mb.oauth2authorizationserver.config.RedisTestConfiguration;
import mb.oauth2authorizationserver.service.OracleToolsService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@Import(RedisTestConfiguration.class)
@TestPropertySource(
        properties = {
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=none"
        }
)
class OracleToolsServiceImplIntegrationTest {

    @Container
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @Container
    private static final OracleContainer oracle = new OracleContainer(DockerImageName.parse("gvenzl/oracle-free:23-slim-faststart"))
            .withUsername("testuser")
            .withPassword("testpass");

    @Autowired
    private OracleToolsService oracleToolsService;

    @BeforeAll
    static void setupDatabases() throws Exception {
        setupPostgresSchema();
        setupOracleSchema();
    }

    static void setupPostgresSchema() throws Exception {
        Class.forName("org.postgresql.Driver");
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE SCHEMA IF NOT EXISTS source_schema");
            stmt.execute("""
                    CREATE TABLE source_schema.products (
                        id SERIAL PRIMARY KEY,
                        name VARCHAR(200) NOT NULL,
                        description TEXT,
                        price NUMERIC(12,2),
                        stock INTEGER DEFAULT 0,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP,
                        is_active BOOLEAN DEFAULT true
                    )
                    """);
            stmt.execute("""
                    CREATE TABLE source_schema.categories (
                        id SERIAL PRIMARY KEY,
                        name VARCHAR(100) NOT NULL,
                        parent_id INTEGER REFERENCES source_schema.categories(id)
                    )
                    """);
            stmt.execute("CREATE UNIQUE INDEX idx_products_name ON source_schema.products(name)");

            for (int i = 1; i <= 100; i++) {
                stmt.execute("INSERT INTO source_schema.products (name, description, price, stock) VALUES ('Product %d', 'Description for product %d', %s, %d)".formatted(i, i, i * 10.50, i * 5));
            }
            stmt.execute("INSERT INTO source_schema.categories (name) VALUES ('Electronics')");
            stmt.execute("INSERT INTO source_schema.categories (name, parent_id) VALUES ('Phones', 1)");
        }
    }

    static void setupOracleSchema() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                oracle.getJdbcUrl(), oracle.getUsername(), oracle.getPassword());
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE SEQUENCE TESTUSER.SEQ_PRODUCTS START WITH 1 INCREMENT BY 1");
            stmt.execute("CREATE SEQUENCE TESTUSER.SEQ_CATEGORIES START WITH 1 INCREMENT BY 1");
            stmt.execute("""
                    CREATE TABLE TESTUSER.PRODUCTS (
                        ID NUMBER(10) DEFAULT TESTUSER.SEQ_PRODUCTS.NEXTVAL NOT NULL,
                        NAME VARCHAR2(200) NOT NULL,
                        DESCRIPTION CLOB,
                        PRICE NUMBER(12,2),
                        STOCK NUMBER(10) DEFAULT 0,
                        CREATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP,
                        UPDATED_AT TIMESTAMP,
                        IS_ACTIVE NUMBER(1) DEFAULT 1,
                        CONSTRAINT PK_PRODUCTS PRIMARY KEY (ID)
                    )
                    """);
            stmt.execute("""
                    CREATE TABLE TESTUSER.CATEGORIES (
                        ID NUMBER(10) DEFAULT TESTUSER.SEQ_CATEGORIES.NEXTVAL NOT NULL,
                        NAME VARCHAR2(100) NOT NULL,
                        PARENT_ID NUMBER(10),
                        CONSTRAINT PK_CATEGORIES PRIMARY KEY (ID)
                    )
                    """);
        }
    }

    @Test
    void generateScripts_ShouldGenerateValidDdlScripts() {
        ScriptGenerationRequest request = ScriptGenerationRequest.builder()
                .source(createPostgresConfig("source_schema"))
                .targetSchema("TARGET_SCHEMA")
                .editRoleName("APP_EDIT_ROLE")
                .viewRoleName("APP_VIEW_ROLE")
                .editRoleUsers(Set.of("app_user"))
                .viewRoleUsers(Set.of("readonly_user"))
                .build();

        ScriptGenerationResponse response = oracleToolsService.generateScripts(request);

        assertThat(response).isNotNull();
        assertThat(response.getTableCount()).isEqualTo(2);
        assertThat(response.getFullScript())
                .contains("CREATE SEQUENCE TARGET_SCHEMA.SEQ_PRODUCTS")
                .contains("CREATE SEQUENCE TARGET_SCHEMA.SEQ_CATEGORIES")
                .contains("CREATE TABLE TARGET_SCHEMA.PRODUCTS")
                .contains("CREATE TABLE TARGET_SCHEMA.CATEGORIES")
                .contains("VARCHAR2")
                .contains("NUMBER")
                .contains("CLOB")
                .contains("NOT NULL")
                .contains("PRIMARY KEY");
    }

    @Test
    void generateScripts_ShouldGenerateDclScripts() {
        ScriptGenerationRequest request = ScriptGenerationRequest.builder()
                .source(createPostgresConfig("source_schema"))
                .targetSchema("TARGET_SCHEMA")
                .editRoleName("CUSTOM_EDIT")
                .viewRoleName("CUSTOM_VIEW")
                .editRoleUsers(Set.of("user1", "user2"))
                .viewRoleUsers(Set.of("readonly"))
                .build();

        ScriptGenerationResponse response = oracleToolsService.generateScripts(request);

        assertThat(response.getFullScript())
                .contains("CREATE ROLE CUSTOM_EDIT")
                .contains("CREATE ROLE CUSTOM_VIEW")
                .contains("GRANT SELECT, INSERT, UPDATE, DELETE ON")
                .contains("GRANT SELECT ON")
                .contains("TO CUSTOM_EDIT")
                .contains("TO CUSTOM_VIEW")
                .satisfiesAnyOf(
                        script -> assertThat(script).contains("GRANT CUSTOM_EDIT TO user1, user2"),
                        script -> assertThat(script).contains("GRANT CUSTOM_EDIT TO user2, user1")
                )
                .contains("GRANT CUSTOM_VIEW TO readonly");
    }

    @Test
    void generateScripts_ShouldGenerateIndexScripts() {
        ScriptGenerationRequest request = ScriptGenerationRequest.builder()
                .source(createPostgresConfig("source_schema"))
                .targetSchema("TARGET_SCHEMA")
                .build();

        ScriptGenerationResponse response = oracleToolsService.generateScripts(request);

        assertThat(response.getFullScript())
                .contains("CREATE UNIQUE INDEX TARGET_SCHEMA.IDX_PRODUCTS_NAME");
    }

    @Test
    void generateScripts_ShouldHandleForeignKeys() {
        ScriptGenerationRequest request = ScriptGenerationRequest.builder()
                .source(createPostgresConfig("source_schema"))
                .targetSchema("TARGET_SCHEMA")
                .build();

        ScriptGenerationResponse response = oracleToolsService.generateScripts(request);

        assertThat(response.getFullScript())
                .contains("ALTER TABLE TARGET_SCHEMA.CATEGORIES ADD CONSTRAINT");
    }

    @Test
    void migrate_ShouldMigrateDataFromPostgresToOracle() {
        MigrationRequest request = MigrationRequest.builder()
                .source(createPostgresConfig("source_schema"))
                .destination(createOracleConfig())
                .build();

        oracleToolsService.migrate(request);

        await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
            try (Connection conn = DriverManager.getConnection(
                    oracle.getJdbcUrl(), oracle.getUsername(), oracle.getPassword());
                 Statement stmt = conn.createStatement()) {

                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM TESTUSER.PRODUCTS");
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(100);

                rs = stmt.executeQuery("SELECT COUNT(*) FROM TESTUSER.CATEGORIES");
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(2);
            }
        });
    }

    @Test
    void generateScripts_ShouldMapPostgresTypesToOracleTypes() {
        ScriptGenerationRequest request = ScriptGenerationRequest.builder()
                .source(createPostgresConfig("source_schema"))
                .targetSchema("TARGET_SCHEMA")
                .build();

        ScriptGenerationResponse response = oracleToolsService.generateScripts(request);

        assertThat(response.getFullScript())
                .contains("NUMBER(10)")
                .contains("VARCHAR2(200)")
                .contains("CLOB")
                .contains("NUMBER(12,2)")
                .contains("TIMESTAMP")
                .contains("NUMBER(1)");
    }

    @Test
    void generateScripts_ShouldReturnWarningsForUnsupportedTypes() throws Exception {
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE SCHEMA IF NOT EXISTS special_schema");
            stmt.execute("""
                    CREATE TABLE special_schema.special_types (
                        id SERIAL PRIMARY KEY,
                        ip_address INET,
                        location POINT
                    )
                    """);
        }

        ScriptGenerationRequest request = ScriptGenerationRequest.builder()
                .source(createPostgresConfig("special_schema"))
                .targetSchema("TARGET_SCHEMA")
                .build();

        ScriptGenerationResponse response = oracleToolsService.generateScripts(request);

        assertThat(response.getWarnings()).isNotEmpty();
        assertThat(response.getWarnings())
                .anyMatch(w -> w.contains("inet") || w.contains("point"));
    }

    private DatabaseConfig createPostgresConfig(String schema) {
        return DatabaseConfig.builder()
                .jdbcUrl(postgres.getJdbcUrl())
                .username(postgres.getUsername())
                .password(postgres.getPassword())
                .schema(schema)
                .driverClassName("org.postgresql.Driver")
                .build();
    }

    private DatabaseConfig createOracleConfig() {
        return DatabaseConfig.builder()
                .jdbcUrl(oracle.getJdbcUrl())
                .username(oracle.getUsername())
                .password(oracle.getPassword())
                .schema("TESTUSER")
                .driverClassName("oracle.jdbc.OracleDriver")
                .build();
    }
}
