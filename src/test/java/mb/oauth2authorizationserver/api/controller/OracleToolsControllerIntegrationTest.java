package mb.oauth2authorizationserver.api.controller;

import mb.oauth2authorizationserver.api.request.DatabaseConfig;
import mb.oauth2authorizationserver.api.request.MigrationRequest;
import mb.oauth2authorizationserver.api.request.ScriptGenerationRequest;
import mb.oauth2authorizationserver.config.RedisTestConfiguration;
import mb.oauth2authorizationserver.config.TestSecurityConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=none"
        }
)
@Import({RedisTestConfiguration.class, TestSecurityConfig.class})
class OracleToolsControllerIntegrationTest {

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
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void setupPostgresSchema() throws Exception {
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE SCHEMA IF NOT EXISTS test_schema");
            stmt.execute("""
                    CREATE TABLE test_schema.users (
                        id SERIAL PRIMARY KEY,
                        username VARCHAR(100) NOT NULL,
                        email VARCHAR(255),
                        active BOOLEAN DEFAULT true,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            stmt.execute("""
                    CREATE TABLE test_schema.orders (
                        id SERIAL PRIMARY KEY,
                        user_id INTEGER REFERENCES test_schema.users(id),
                        amount NUMERIC(10,2),
                        order_date DATE
                    )
                    """);
            stmt.execute("CREATE INDEX idx_users_email ON test_schema.users(email)");
            stmt.execute("INSERT INTO test_schema.users (username, email) VALUES ('john', 'john@example.com')");
            stmt.execute("INSERT INTO test_schema.users (username, email) VALUES ('jane', 'jane@example.com')");
        }
    }

    @Test
    void generateScripts_ShouldReturnDdlAndDclScripts() throws Exception {
        ScriptGenerationRequest request = ScriptGenerationRequest.builder()
                .source(DatabaseConfig.builder()
                        .jdbcUrl(postgres.getJdbcUrl())
                        .username(postgres.getUsername())
                        .password(postgres.getPassword())
                        .schema("test_schema")
                        .driverClassName("org.postgresql.Driver")
                        .build())
                .targetSchema("TARGET_SCHEMA")
                .editRoleName("EDIT_ROLE")
                .viewRoleName("VIEW_ROLE")
                .editRoleUsers(Set.of("app_user"))
                .viewRoleUsers(Set.of("readonly_user"))
                .build();

        MvcResult result = mockMvc.perform(post("/api/oracle-tools/generate-scripts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/sql"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"oracle_target_schema.sql\""))
                .andReturn();

        String sqlContent = result.getResponse().getContentAsString();
        assertThat(sqlContent)
                .contains("CREATE TABLE")
                .contains("CREATE SEQUENCE")
                .contains("CREATE ROLE EDIT_ROLE")
                .contains("CREATE ROLE VIEW_ROLE")
                .contains("GRANT EDIT_ROLE TO app_user")
                .contains("GRANT VIEW_ROLE TO readonly_user");
    }

    @Test
    void generateScripts_ShouldReturnBadRequest_WhenSourceConfigMissing() throws Exception {
        ScriptGenerationRequest request = ScriptGenerationRequest.builder()
                .targetSchema("TARGET_SCHEMA")
                .build();

        mockMvc.perform(post("/api/oracle-tools/generate-scripts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void migrate_ShouldAcceptRequest() throws Exception {
        MigrationRequest request = MigrationRequest.builder()
                .source(DatabaseConfig.builder()
                        .jdbcUrl(postgres.getJdbcUrl())
                        .username(postgres.getUsername())
                        .password(postgres.getPassword())
                        .schema("test_schema")
                        .driverClassName("org.postgresql.Driver")
                        .build())
                .destination(DatabaseConfig.builder()
                        .jdbcUrl(oracle.getJdbcUrl())
                        .username(oracle.getUsername())
                        .password(oracle.getPassword())
                        .schema("TESTUSER")
                        .driverClassName("oracle.jdbc.OracleDriver")
                        .build())
                .build();

        mockMvc.perform(post("/api/oracle-tools/migrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
