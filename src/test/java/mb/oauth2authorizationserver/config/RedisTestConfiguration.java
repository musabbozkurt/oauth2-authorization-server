package mb.oauth2authorizationserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@Slf4j
@TestConfiguration
public class RedisTestConfiguration {

    private static final Integer REDIS_PORT = 6379;

    @Bean
    public GenericContainer<?> redisContainer() {
        GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7.4.1"))
                .withExposedPorts(REDIS_PORT)
                .withReuse(true);

        redisContainer.start();

        log.info("Redis server started. isCreated: {}, isRunning: {}", redisContainer.isCreated(), redisContainer.isRunning());

        // Redis properties before Spring Boot 3
        System.setProperty("spring.redis.host", redisContainer.getHost());
        System.setProperty("spring.redis.port", redisContainer.getMappedPort(REDIS_PORT).toString());

        // Redis properties after Spring Boot 3
        System.setProperty("spring.data.redis.host", redisContainer.getHost());
        System.setProperty("spring.data.redis.port", redisContainer.getMappedPort(REDIS_PORT).toString());

        return redisContainer;
    }
}
