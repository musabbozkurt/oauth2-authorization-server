package mb.oauth2authorizationserver.config;

import com.redis.testcontainers.RedisContainer;
import jakarta.annotation.Nonnull;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration
public class RedisCustomTestConfiguration {

    @Container
    public static final RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:8.4.0"))
            .withExposedPorts(6379)
            .withReuse(true);

    public static class RedisInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(@Nonnull ConfigurableApplicationContext context) {
            redis.start();

            String host = redis.getHost();
            String port = String.valueOf(redis.getMappedPort(6379));

            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                    context,

                    // Spring Boot 2.x
                    "spring.redis.host=%s".formatted(host),
                    "spring.redis.port=%s".formatted(port),

                    // Spring Boot 3.x, 4.x
                    "spring.data.redis.host=%s".formatted(host),
                    "spring.data.redis.port=%s".formatted(port),

                    "spring.ai.chat.memory.redis.host=%s".formatted(host),
                    "spring.ai.chat.memory.redis.port=%s".formatted(port)
            );
        }
    }
}
