package mb.oauth2authorizationserver.config;

import com.nimbusds.jose.jwk.source.JWKSource;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@TestConfiguration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = false)
public class TestSecurityConfig {

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring().requestMatchers(PathPatternRequestMatcher.withDefaults().matcher("/**"));
    }

    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        return Mockito.mock(JwtDecoder.class);
    }

    @Bean
    public JWKSource<?> jwkSource() {
        return Mockito.mock(JWKSource.class);
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return Mockito.mock(SessionRegistry.class);
    }
}
