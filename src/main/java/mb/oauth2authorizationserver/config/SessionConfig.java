package mb.oauth2authorizationserver.config;

import org.jspecify.annotations.NonNull;
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
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;
import org.springframework.session.web.context.AbstractHttpSessionApplicationInitializer;

import java.time.Duration;

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
}
