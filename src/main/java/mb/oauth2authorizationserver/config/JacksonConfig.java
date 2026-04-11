package mb.oauth2authorizationserver.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import mb.oauth2authorizationserver.config.security.model.HttpRequestDetails;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.server.authorization.jackson.OAuth2AuthorizationServerJacksonModule;
import org.springframework.web.servlet.FlashMap;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.hibernate7.Hibernate7Module;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central Jackson configuration.
 * <p>
 * Customizes the auto-configured {@link tools.jackson.databind.ObjectMapper} via
 * {@link JsonMapperBuilderCustomizer}. The same settings are reused by the Redis
 * cache serializer via {@link #applyCommonSettings(JsonMapper.Builder)}.
 */
@Configuration
public class JacksonConfig {

    /**
     * Shared Jackson configuration applied to both the auto-configured
     * {@link tools.jackson.databind.ObjectMapper} and the Redis cache
     * serializer's internal mapper.
     * <p>
     * Does NOT include SecurityJacksonModules — they reset the
     * {@link tools.jackson.databind.jsontype.PolymorphicTypeValidator} to
     * {@code BasicPolymorphicTypeValidator} during {@code setupModule()}.
     */
    public static void applyCommonSettings(JsonMapper.Builder builder) {
        builder.configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
                .configure(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
                .changeDefaultVisibility(vc -> vc.withVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY))
                .changeDefaultPropertyInclusion(v -> v.withValueInclusion(JsonInclude.Include.NON_NULL))
                .defaultTimeZone(TimeZone.getTimeZone(ZoneId.of("Europe/Istanbul")))
                .addModules(new Hibernate7Module(), new OAuth2AuthorizationServerJacksonModule())
                .addMixIn(Long.class, LongMixin.class)
                .addMixIn(HttpRequestDetails.class, HttpRequestDetailsMixin.class)
                .addMixIn(DisabledException.class, Object.class)
                .addMixIn(Throwable.class, ThrowableMixin.class)
                .addMixIn(CopyOnWriteArrayList.class, Object.class)
                .addMixIn(ArrayList.class, Object.class)
                .addMixIn(LinkedHashMap.class, Object.class)
                .addMixIn(TreeMap.class, Object.class)
                .addMixIn(FlashMap.class, Object.class);
    }

    @Bean
    public JsonMapperBuilderCustomizer appJsonMapperBuilderCustomizer() {
        return JacksonConfig::applyCommonSettings;
    }

    interface LongMixin {
    }

    interface HttpRequestDetailsMixin {
    }

    @JsonIgnoreProperties({"suppressed", "stackTrace", "localizedMessage", "cause"})
    public static class ThrowableMixin {
    }
}
