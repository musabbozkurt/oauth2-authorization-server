package mb.oauth2authorizationserver.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
public class OracleTestConfiguration {

    private static final String OAUTH2_AUTHORIZATION_SERVER_OWNER = "OAUTH2_AUTHORIZATION_SERVER_OWNER";
    private static final String OAUTH2_AUTHORIZATION_SERVER = "OAUTH2_AUTHORIZATION_SERVER";
    private static final String PASSWORD = "password";

    private static final OracleContainer oracleContainer;

    static {
        oracleContainer = new OracleContainer(DockerImageName.parse("gvenzl/oracle-free:23-slim-faststart"))
                .withCopyFileToContainer(MountableFile.forClasspathResource("init_users.sql"), "/container-entrypoint-initdb.d/init_users.sql")
                .withUsername(OAUTH2_AUTHORIZATION_SERVER_OWNER)
                .withPassword(PASSWORD);
        oracleContainer.start();  // 🟢 start container early
    }

    @Bean
    public DynamicPropertyRegistrar registerDatabaseProperties() {
        return registry -> {
            registry.add("spring.datasource.url", oracleContainer::getJdbcUrl);
            registry.add("spring.datasource.username", () -> OAUTH2_AUTHORIZATION_SERVER);
            registry.add("spring.datasource.password", () -> PASSWORD);
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
            registry.add("spring.datasource.driver-class-name", () -> "oracle.jdbc.OracleDriver");
        };
    }
}
