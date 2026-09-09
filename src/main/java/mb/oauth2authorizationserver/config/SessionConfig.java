package mb.oauth2authorizationserver.config;

import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.constants.ServiceConstants;
import org.jspecify.annotations.NonNull;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.FlushMode;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.data.redis.RedisSessionMapper;
import org.springframework.session.data.redis.config.ConfigureRedisAction;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;
import org.springframework.session.web.context.AbstractHttpSessionApplicationInitializer;

import java.time.Duration;

@Slf4j
@EnableCaching
@Configuration
@EnableRedisIndexedHttpSession(maxInactiveIntervalInSeconds = 3600, redisNamespace = "sso:session", flushMode = FlushMode.ON_SAVE)
public class SessionConfig extends AbstractHttpSessionApplicationInitializer implements BeanClassLoaderAware {

    private ClassLoader classLoader;

    @Override
    public void setBeanClassLoader(@NonNull ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Spring Session serializer - uses Java serialization.
     * <p>
     * WHY NOT JACKSON: Jackson 3.x removed activateDefaultTyping() from ObjectMapper
     * entirely (builder-only). SecurityJacksonModules resets the PolymorphicTypeValidator
     * to BasicPolymorphicTypeValidator during setupModule(), and there is no post-build
     * API in Jackson 3.x to override it. Java serialization is the correct tool here:
     * all Spring Security types (Authentication, SecurityContext, OAuth2Authorization, etc.)
     * implement Serializable by design, and this was Spring Session's default before
     * JSON serialization became fashionable.
     */
    @Bean
    public RedisSerializer<@NonNull Object> springSessionDefaultRedisSerializer() {
        // Uses the app classloader so Spring Security / OAuth2 types deserialize correctly
        return RedisSerializer.java(this.classLoader);
    }

    /**
     * Cache manager - uses Jackson with the same settings as the primary
     * {@link tools.jackson.databind.ObjectMapper} (see {@link JacksonConfig}), plus
     * unsafe default typing for polymorphic cache-value resolution.
     */
    @Bean
    public RedisCacheManager redisCacheManagerBuilderCustomizer(RedisConnectionFactory redisConnectionFactory) {
        GenericJacksonJsonRedisSerializer jacksonSerializer = GenericJacksonJsonRedisSerializer.builder()
                .enableUnsafeDefaultTyping()
                .customize(JacksonConfig::applyCommonSettings)
                .build();

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofDays(90))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jacksonSerializer));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(config)
                .build();
    }

    @Bean
    public SessionRepositoryCustomizer<RedisIndexedSessionRepository> redisIndexedSessionRepositoryCustomizer(RedissonClient redissonClient) {
        RedisSessionMapper defaultMapper = new RedisSessionMapper();
        return repository -> repository.setRedisSessionMapper((sessionId, map) -> {
            try {
                return defaultMapper.apply(sessionId, map);
            } catch (IllegalStateException ex) {
                log.warn("Removing corrupted session {}: {}", sessionId, ex.getMessage());
                redissonClient.getKeys().delete(ServiceConstants.sessionKey(sessionId), ServiceConstants.sessionExpiresKey(sessionId));
                return defaultMapper.apply(sessionId, map);
            }
        });
    }

    /**
     * If Redis is not configured to emit generic and expired key events, Spring Session cannot clean up indexes properly, resulting in this error.
     * You can fix this by running the following command directly on your Redis CLI:
     * CONFIG SET notify-keyspace-events Egx
     * <p>
     * (Note: If you are using AWS ElastiCache, CONFIG SET is blocked by default, so you must enable Egx via the AWS ElastiCache Parameter Group)
     */
    @Bean
    public ConfigureRedisAction configureRedisAction() {
        return ConfigureRedisAction.NO_OP; // Set to NO_OP only if Redis config is secured/managed manually
    }
}
