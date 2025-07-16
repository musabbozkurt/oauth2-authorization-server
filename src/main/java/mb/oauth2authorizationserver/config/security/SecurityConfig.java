package mb.oauth2authorizationserver.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.config.security.builder.AuthorizationBuilderService;
import mb.oauth2authorizationserver.config.security.converter.CustomPasswordAuthenticationConverter;
import mb.oauth2authorizationserver.config.security.converter.JwtBearerGrantAuthenticationConverter;
import mb.oauth2authorizationserver.config.security.handler.CustomSimpleUrlAuthenticationFailureHandler;
import mb.oauth2authorizationserver.config.security.model.CustomPasswordUser;
import mb.oauth2authorizationserver.config.security.provider.CustomPasswordAuthenticationProvider;
import mb.oauth2authorizationserver.config.security.provider.CustomRefreshTokenAuthenticationProvider;
import mb.oauth2authorizationserver.config.security.provider.JwtBearerGrantAuthenticationProvider;
import mb.oauth2authorizationserver.config.security.service.impl.OAuth2AuthorizationServiceImpl;
import mb.oauth2authorizationserver.config.security.service.impl.UserDetailsManagerImpl;
import mb.oauth2authorizationserver.data.repository.AuthorizationRepository;
import mb.oauth2authorizationserver.data.repository.UserRepository;
import mb.oauth2authorizationserver.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.session.security.web.authentication.SpringSessionRememberMeServices;
import org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] ALLOWED_ENDPOINT_PATTERNS = {
            "/images/**", "/css/**", "/js/**", "/fonts/**", "/qrcode/**", "/login/**", "/scan/**", "/authenticate/**",
            "/oauth/token/revokeById/**", "/oauth/token/**", "/oauth2/token/**", "/oauth/check_token/**",
            "/oauth/authorize/**", "/v3/api-docs/**", "/swagger-resources/**", "/configuration/ui",
            "/configuration/security", "/swagger-ui/**", "/webjars/**", "/swagger-ui.html", "/oauth2/**", "/error/**",
            "/actuator/**", "/ott/sent", "/login/ott", "/chat/**", "/vector-stores/**", "/mcp/message"
    };
    private static final String LOGIN_FORM_URL = "/login";
    private static final String JSESSIONID = "JSESSIONID";
    private static final String LOGOUT_URL = "/logout";
    private static final String AUTHORITIES = "authorities";

    private final AuthorizationRepository authorizationRepository;
    private final AuthorizationBuilderService authorizationBuilderService;
    private final UserRepository userRepository;

    @Value("${jwt.key.path:./keys/jwt.key}")
    private String jwtKeyPath;

    @Bean
    @Order(1)
    public SecurityFilterChain asSecurityFilterChain(HttpSecurity httpSecurity,
                                                     ObjectMapper objectMapper,
                                                     @Qualifier("customAccessDeniedHandler") AccessDeniedHandler accessDeniedHandler) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = OAuth2AuthorizationServerConfigurer.authorizationServer();
        OAuth2AuthorizationService oAuth2AuthorizationService = new OAuth2AuthorizationServiceImpl(authorizationRepository, authorizationBuilderService);

        httpSecurity
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> (authorize.anyRequest()).authenticated())
                .getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .oidc(Customizer.withDefaults()); // Enable OpenID Connect 1.0

        httpSecurity
                .cors(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(management -> management
                        .sessionFixation()
                        .migrateSession()
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .maximumSessions(10)
                        .sessionRegistry(sessionRegistry()))
                .rememberMe(me -> me.rememberMeServices(rememberMeServices()))
                .oauth2ResourceServer(auth2ResourceServerConfigurer -> {
                    auth2ResourceServerConfigurer.authenticationEntryPoint(new AuthExceptionEntryPoint(objectMapper));
                    auth2ResourceServerConfigurer.accessDeniedHandler(accessDeniedHandler);
                    auth2ResourceServerConfigurer.jwt(Customizer.withDefaults());
                }).exceptionHandling(e -> e.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint(LOGIN_FORM_URL)))
                .getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .tokenEndpoint(tokenEndpoint -> tokenEndpoint
                        .accessTokenRequestConverter(new CustomPasswordAuthenticationConverter())
                        .authenticationProvider(new CustomPasswordAuthenticationProvider(oAuth2AuthorizationService, tokenGenerator(), userDetailsService(), passwordEncoder()))
                        .authenticationProvider(new CustomRefreshTokenAuthenticationProvider(oAuth2AuthorizationService, tokenGenerator()))
                        .accessTokenRequestConverter(new JwtBearerGrantAuthenticationConverter())
                        .authenticationProvider(new JwtBearerGrantAuthenticationProvider(oAuth2AuthorizationService, tokenGenerator()))
                        .accessTokenRequestConverters(getConverters())
                        .authenticationProviders(getProviders()));

        return httpSecurity.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain appSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(ALLOWED_ENDPOINT_PATTERNS)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .formLogin(Customizer.withDefaults())
                .oneTimeTokenLogin(Customizer.withDefaults())
                .authenticationProvider(daoAuthenticationProvider())
                .build();
    }

    /**
     * The following method configures the security filter chain for MVC requests. It should enabled, if there is login.html page is used for login.
     * <p>
     * {@code @Bean}
     * {@code @Order(3)}
     */
    public SecurityFilterChain mvcRequestSecurityFilterChain(HttpSecurity http,
                                                             @Qualifier("customSavedRequestAwareAuthenticationSuccessHandler") AuthenticationSuccessHandler customSavedRequestAwareAuthenticationSuccessHandler,
                                                             CustomSimpleUrlAuthenticationFailureHandler customSimpleUrlAuthenticationFailureHandler) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(ALLOWED_ENDPOINT_PATTERNS)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .sessionManagement(management -> management
                        .sessionFixation()
                        .migrateSession()
                        .sessionCreationPolicy(SessionCreationPolicy.ALWAYS)
                        .maximumSessions(10)
                        .sessionRegistry(sessionRegistry()))
                .rememberMe(me -> me.rememberMeServices(rememberMeServices()))
                .formLogin(login -> {
                            login.loginPage(LOGIN_FORM_URL);
                            login.successHandler(customSavedRequestAwareAuthenticationSuccessHandler);
                            login.failureHandler(customSimpleUrlAuthenticationFailureHandler);
                        }
                )
                .logout(logout -> {
                    logout.logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher(LOGOUT_URL));
                    logout.logoutSuccessUrl(LOGIN_FORM_URL);
                    logout.deleteCookies(JSESSIONID);
                    logout.invalidateHttpSession(true);
                })
                .oneTimeTokenLogin(Customizer.withDefaults())
                .authenticationProvider(daoAuthenticationProvider())
                .build();
    }

    @Bean
    public OAuth2TokenGenerator<OAuth2Token> tokenGenerator() {
        NimbusJwtEncoder jwtEncoder = new NimbusJwtEncoder(jwkSource());
        JwtGenerator jwtGenerator = new JwtGenerator(jwtEncoder);
        jwtGenerator.setJwtCustomizer(tokenCustomizer());
        OAuth2AccessTokenGenerator accessTokenGenerator = new OAuth2AccessTokenGenerator();
        OAuth2RefreshTokenGenerator refreshTokenGenerator = new OAuth2RefreshTokenGenerator();
        return new DelegatingOAuth2TokenGenerator(jwtGenerator, accessTokenGenerator, refreshTokenGenerator);
    }

    @Bean
    @Primary
    public UserDetailsService userDetailsService() {
        return new UserDetailsManagerImpl(userRepository);
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /***
     * Authentication Provider Configuration
     * -------------------------------
     * Authentication Provider Setup
     * -------------------------------
     * • DaoAuthenticationProvider Configuration
     *   - Configures a custom {@link DaoAuthenticationProvider} to authenticate users.
     *   - If only one {@link UserDetailsService} is defined, Spring Security automatically uses the default provider.
     *   - In cases of multiple {@link UserDetailsService} beans, manual configuration is required.
     *   - Associates the appropriate {@link UserDetailsService} with the provider.
     * -------------------------------
     * Multiple UserDetailsService Beans
     * -------------------------------
     * • Need for Custom AuthenticationProvider
     *   - When more than one {@link UserDetailsService} bean is defined (e.g., with {@link org.springframework.stereotype.Service} annotation),
     *     Spring Security cannot auto-select the correct one.
     *   - In such cases, you must explicitly configure the {@link AuthenticationProvider}.
     *   - Ensures the correct {@link UserDetailsService} is used for authentication.
     * -------------------------------
     * Password Encoder Setup
     * -------------------------------
     * • Password Encoder Configuration
     *   - Configures a {@link PasswordEncoder} for proper password validation during authentication.
     * -------------------------------
     * Return Value
     * -------------------------------
     * • DaoAuthenticationProvider
     *   - Returns a configured {@link DaoAuthenticationProvider} that uses the correct {@link UserDetailsService} and {@link PasswordEncoder}.
     */
    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService());
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder().build();
    }

    @Bean
    public OAuth2AuthorizationConsentService oAuth2AuthorizationConsentService() {
        return new InMemoryOAuth2AuthorizationConsentService();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public SpringSessionRememberMeServices rememberMeServices() {
        SpringSessionRememberMeServices rememberMeServices = new SpringSessionRememberMeServices();
        rememberMeServices.setAlwaysRemember(true);
        return rememberMeServices;
    }

    @Bean
    public SpringSecurityDialect securityDialect() {
        return new SpringSecurityDialect();
    }

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
        return context -> {
            Authentication principal = context.getPrincipal();
            if (context.getTokenType().getValue().equals("id_token")) {
                context.getClaims().claim("Test", "Test Id Token");
            }
            if (context.getTokenType().getValue().equals("access_token")) {
                context.getClaims().claim("Test", "Test Access Token");
                Set<String> authorities = principal.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
                context.getClaims().claim(AUTHORITIES, authorities).claim("user", principal.getName());
            }
            if (principal.getDetails() instanceof CustomPasswordUser(
                    String username, Collection<GrantedAuthority> grantedAuthorities
            )) {
                Set<String> authorities = grantedAuthorities
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet());
                if (context.getTokenType().getValue().equals("access_token")) {
                    context.getClaims()
                            .claim(AUTHORITIES, authorities)
                            .claim("user", username);
                }
            }
            customizeRefreshToken(context, principal);
        };
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        RSAKey rsaKey = SecurityUtils.loadOrGenerateRsa(jwtKeyPath);
        JWKSet jwkSet = new JWKSet(rsaKey);
        return (jwkSelector, _) -> jwkSelector.select(jwkSet);
    }

    @Bean
    ApplicationListener<AuthenticationSuccessEvent> successEvent() {
        return event -> log.info("Success login AuthenticationClassName: {} - AuthenticationName: {}", event.getAuthentication().getClass().getSimpleName(), event.getAuthentication().getName());
    }

    @Bean
    ApplicationListener<AuthenticationFailureBadCredentialsEvent> failureEvent() {
        return event -> log.info("Bad credentials login AuthenticationClassName: {} - AuthenticationName: {}", event.getAuthentication().getClass().getSimpleName(), event.getAuthentication().getName());
    }

    private Consumer<List<AuthenticationProvider>> getProviders() {
        return a -> a.forEach(authenticationProvider -> log.info("authenticationProvider: {}", authenticationProvider));
    }

    private Consumer<List<AuthenticationConverter>> getConverters() {
        return a -> a.forEach(authenticationConverter -> log.info("authenticationConverter: {}", authenticationConverter));
    }

    private void customizeRefreshToken(JwtEncodingContext context, Authentication principal) {
        if (AuthorizationGrantType.REFRESH_TOKEN.equals(context.getAuthorizationGrantType()) && OAuth2TokenType.REFRESH_TOKEN.equals(context.getTokenType())) {
            OAuth2Authorization authorization = context.getAuthorization();
            if (authorization != null) {
                OAuth2Authorization.Token<OAuth2AccessToken> oAuth2AccessTokenToken = authorization.getToken(OAuth2AccessToken.class);
                if (Objects.nonNull(oAuth2AccessTokenToken) && oAuth2AccessTokenToken.getClaims() != null) {
                    context.getClaims().claim("Test", "Test Access Token");
                    Set<String> authorities = principal.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
                    context.getClaims().claim(AUTHORITIES, authorities).claim("user", principal.getName());
                }
            }
        }
    }
}
