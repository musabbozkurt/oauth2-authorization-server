package mb.oauth2authorizationserver.config;

import com.redis.testcontainers.RedisContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.testcontainers.utility.DockerImageName;

@Slf4j
@TestConfiguration
public class RedisTestConfiguration {

    private static final Integer REDIS_PORT = 6379;

    @Bean(destroyMethod = "stop")
    @ServiceConnection(name = "redis")
    public RedisContainer redisContainer() {
        RedisContainer redisContainer = new RedisContainer(DockerImageName.parse("redis:8.4.0"))
                .withExposedPorts(REDIS_PORT)
                .withReuse(true);

        redisContainer.start();
        log.info("Redis server started. isCreated: {}, isRunning: {}", redisContainer.isCreated(), redisContainer.isRunning());

        System.setProperty("spring.ai.chat.memory.redis.host", redisContainer.getHost());
        System.setProperty("spring.ai.chat.memory.redis.port", String.valueOf(redisContainer.getMappedPort(REDIS_PORT)));

        return redisContainer;
    }

    /**
     * Disables the per-minute session cleanup scheduler during tests.
     * Without this, the scheduler fires during context shutdown after
     * LettuceConnectionFactory has already been stopped, causing
     * IllegalStateException: LettuceConnectionFactory has been STOPPED.
     */
    @Bean
    public SessionRepositoryCustomizer<RedisIndexedSessionRepository> disableCleanupCron() {
        return repository -> repository.setCleanupCron(Scheduled.CRON_DISABLED);
    }
}
