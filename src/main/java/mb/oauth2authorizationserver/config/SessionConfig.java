package mb.oauth2authorizationserver.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.NonNull;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.session.FlushMode;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.context.AbstractHttpSessionApplicationInitializer;

import java.time.ZoneId;
import java.util.TimeZone;

@Configuration
@ConditionalOnProperty(name = {"spring.data.redis.host"})
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 3600, redisNamespace = "sso:session", flushMode = FlushMode.ON_SAVE)
public class SessionConfig extends AbstractHttpSessionApplicationInitializer implements BeanClassLoaderAware {

    private ClassLoader loader;

    @Bean
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        return new GenericJackson2JsonRedisSerializer(objectMapper());
    }

    @Override
    public void setBeanClassLoader(@NonNull ClassLoader classLoader) {
        this.loader = classLoader;
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper()
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .setTimeZone(TimeZone.getTimeZone(ZoneId.of("Europe/Istanbul")))
                .registerModules(new Hibernate6Module(), new JavaTimeModule())
                .registerModules(SecurityJackson2Modules.getModules(this.loader))
                .addMixIn(Long.class, LongMixin.class);
    }

    interface LongMixin {
    }
}
