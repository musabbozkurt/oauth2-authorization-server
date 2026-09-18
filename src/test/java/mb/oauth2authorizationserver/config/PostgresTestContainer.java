package mb.oauth2authorizationserver.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PostgresTestContainer {

    private static final PostgreSQLContainer CONTAINER = new PostgreSQLContainer(DockerImageName.parse("postgres:18.6-alpine"))
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass")
            // Postgres 18+ stores data under /var/lib/postgresql/<major>/docker (not /data directly)
            .withTmpFs(Map.of("/var/lib/postgresql", "rw"));

    static {
        CONTAINER.start();
    }

    public static PostgreSQLContainer instance() {
        return CONTAINER;
    }
}
