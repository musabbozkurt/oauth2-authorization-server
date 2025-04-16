package mb.oauth2authorizationserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@Slf4j
@TestConfiguration
public class RedisTestConfiguration {

    private static final Integer REDIS_PORT = 6379;

    @Bean
    @ServiceConnection(name = "redis")
    public GenericContainer<?> redisContainer() {
        GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7.4.1"))
                .withExposedPorts(REDIS_PORT)
                .withReuse(true);

        redisContainer.start();

        log.info("Redis server started. isCreated: {}, isRunning: {}", redisContainer.isCreated(), redisContainer.isRunning());

        return redisContainer;
    }
}
