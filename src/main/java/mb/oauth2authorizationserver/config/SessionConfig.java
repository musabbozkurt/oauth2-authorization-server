package mb.oauth2authorizationserver.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import mb.oauth2authorizationserver.config.security.model.HttpRequestDetails;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.security.oauth2.server.authorization.jackson.OAuth2AuthorizationServerJacksonModule;
import org.springframework.session.FlushMode;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.context.AbstractHttpSessionApplicationInitializer;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.datatype.hibernate7.Hibernate7Module;

import java.time.Duration;
import java.time.ZoneId;
import java.util.TimeZone;

@EnableCaching
@Configuration
@ConditionalOnProperty(name = {"spring.data.redis.host"})
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 3600, redisNamespace = "sso:session", flushMode = FlushMode.ON_SAVE)
public class SessionConfig extends AbstractHttpSessionApplicationInitializer implements BeanClassLoaderAware {

    private ClassLoader classLoader;

    @Bean
    public RedisSerializer<@NonNull Object> springSessionDefaultRedisSerializer() {
        return new GenericJacksonJsonRedisSerializer(objectMapper());
    }

    @Override
    public void setBeanClassLoader(@NonNull ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Bean
    public RedisCacheManager redisCacheManagerBuilderCustomizer() {
        return RedisCacheManager.builder().cacheDefaults(RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofDays(90))).build();
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
                .configure(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
                .changeDefaultVisibility(visibilityChecker -> visibilityChecker.withVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY))
                .activateDefaultTyping(BasicPolymorphicTypeValidator.builder().build(), DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY)
                .changeDefaultPropertyInclusion(value -> value.withValueInclusion(JsonInclude.Include.NON_NULL))
                .defaultTimeZone(TimeZone.getTimeZone(ZoneId.of("Europe/Istanbul")))
                .addModules(new Hibernate7Module(), new OAuth2AuthorizationServerJacksonModule())
                .addModules(SecurityJacksonModules.getModules(this.classLoader))
                .addMixIn(Long.class, LongMixin.class)
                .addMixIn(HttpRequestDetails.class, HttpRequestDetailsMixin.class)
                .addMixIn(DisabledException.class, Object.class)
                .addMixIn(Throwable.class, ThrowableMixin.class)
                .build();
    }

    interface LongMixin {
    }

    interface HttpRequestDetailsMixin {
    }

    @JsonIgnoreProperties({"suppressed", "stackTrace", "localizedMessage", "cause"})
    public static class ThrowableMixin {
    }
}
