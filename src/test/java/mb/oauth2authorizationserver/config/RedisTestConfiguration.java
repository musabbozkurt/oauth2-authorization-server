package mb.oauth2authorizationserver.config;

import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.exception.BaseException;
import mb.oauth2authorizationserver.exception.OAuth2AuthorizationServerServiceErrorCode;
import org.apache.commons.lang3.exception.ExceptionUtils;
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
        try (GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7.4.1"))) {
            redisContainer.withExposedPorts(REDIS_PORT).withReuse(true).start();
            log.info("Redis server started. isCreated: {}, isRunning: {}", redisContainer.isCreated(), redisContainer.isRunning());
            return redisContainer;
        } catch (Exception e) {
            log.error("Failed to start Redis container. redisContainer - Exception: {}", ExceptionUtils.getStackTrace(e));
            throw new BaseException(OAuth2AuthorizationServerServiceErrorCode.UNEXPECTED_ERROR);
        }
    }
}
