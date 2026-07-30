package mb.oauth2authorizationserver.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@TestPropertySource(properties = "spring.flyway.enabled=false")
@SpringBootTest(classes = {OracleTestConfiguration.class, RedisTestConfiguration.class})
class SchedulerInitializerIntegrationTest {

    @Autowired
    private SchedulerInitializer schedulerInitializer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void ensureRegisteredTableExists() {
        jdbcTemplate.execute("""
                BEGIN
                    EXECUTE IMMEDIATE 'CREATE TABLE INTEGRATION_TABLE_NAME (id NUMBER PRIMARY KEY)';
                EXCEPTION
                    WHEN OTHERS THEN
                        IF SQLCODE != -955 THEN
                            RAISE;
                        END IF;
                END;
                """);

        jdbcTemplate.execute("""
                MERGE INTO INTEGRATION_TABLE_NAME t
                USING (SELECT 1 id FROM dual) s
                ON (t.id = s.id)
                WHEN NOT MATCHED THEN
                  INSERT (id) VALUES (s.id)
                """);
    }

    @Test
    void init_shouldNotThrow_whenUsingRealOracleContainerDataSource() {
        assertDoesNotThrow(() -> schedulerInitializer.init());
    }

    @Test
    void unregisterCqn_shouldNotThrow_whenRegistrationIsMissingOrUnavailable() {
        assertDoesNotThrow(() -> schedulerInitializer.unregisterCqn());
    }
}
