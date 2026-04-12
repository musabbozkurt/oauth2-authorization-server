package mb.oauth2authorizationserver.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Test security configuration that disables all Spring Security filters.
 * <p>
 * Does NOT re-declare {@code jwtDecoder}, {@code jwkSource}, or {@code sessionRegistry} —
 * those are provided by the real {@code SecurityConfig} and work without Redis or external keys.
 * {@code FindByIndexNameSessionRepository} is provided by {@code RedisTestConfiguration}.
 */
@TestConfiguration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = false)
public class TestSecurityConfig {

    /**
     * Disables all Spring Security filters so controller tests run without authentication.
     *
     * @return a customizer that ignores all request paths
     */
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring().requestMatchers(PathPatternRequestMatcher.withDefaults().matcher("/**"));
    }
}
