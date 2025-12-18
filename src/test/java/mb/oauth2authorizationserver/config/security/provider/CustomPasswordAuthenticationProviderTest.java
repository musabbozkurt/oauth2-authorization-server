package mb.oauth2authorizationserver.config.security.provider;

import mb.oauth2authorizationserver.config.security.CustomPasswordAuthenticationToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomPasswordAuthenticationProviderTest {

    private CustomPasswordAuthenticationProvider customPasswordAuthenticationProvider;

    @Mock
    private OAuth2AuthorizationService authorizationService;

    @Mock
    private OAuth2TokenGenerator<OAuth2Token> tokenGenerator;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    private OAuth2ClientAuthenticationToken clientPrincipal;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        customPasswordAuthenticationProvider = new CustomPasswordAuthenticationProvider(
                authorizationService,
                tokenGenerator,
                userDetailsService,
                bCryptPasswordEncoder
        );

        RegisteredClient registeredClient = RegisteredClient.withId("test-client-id")
                .clientId("test-client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(new AuthorizationGrantType("custom_password"))
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .scope("read")
                .scope("write")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(1))
                        .refreshTokenTimeToLive(Duration.ofDays(30))
                        .build())
                .build();

        clientPrincipal = new OAuth2ClientAuthenticationToken(registeredClient, ClientAuthenticationMethod.CLIENT_SECRET_BASIC, "client-secret");

        SecurityContextHolder.getContext().setAuthentication(clientPrincipal);

        AuthorizationServerSettings authorizationServerSettings = AuthorizationServerSettings.builder()
                .issuer("https://example.com")
                .build();
        AuthorizationServerContext authorizationServerContext = new AuthorizationServerContext() {
            @Override
            public String getIssuer() {
                return authorizationServerSettings.getIssuer();
            }

            @Override
            public AuthorizationServerSettings getAuthorizationServerSettings() {
                return authorizationServerSettings;
            }
        };
        AuthorizationServerContextHolder.setContext(authorizationServerContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        AuthorizationServerContextHolder.resetContext();
    }

    @Test
    void authenticate_ShouldThrowOAuth2AuthenticationException_WhenClientIsNotAuthenticated() {
        OAuth2ClientAuthenticationToken unauthenticatedClientPrincipal = mock(OAuth2ClientAuthenticationToken.class);
        when(unauthenticatedClientPrincipal.isAuthenticated()).thenReturn(false);

        Map<String, Object> additionalParameters = new java.util.HashMap<>();
        additionalParameters.put("username", "user@example.com");
        additionalParameters.put("password", "password");
        CustomPasswordAuthenticationToken authentication = new CustomPasswordAuthenticationToken(unauthenticatedClientPrincipal, Set.of("read", "write"), additionalParameters);

        assertThrows(OAuth2AuthenticationException.class, () -> customPasswordAuthenticationProvider.authenticate(authentication));
    }

    @Test
    void authenticate_ShouldThrowOAuth2AuthenticationException_WhenRegisteredClientIsNull() {
        String username = "user@example.com";

        OAuth2ClientAuthenticationToken mockClientPrincipal = mock(OAuth2ClientAuthenticationToken.class);
        when(mockClientPrincipal.isAuthenticated()).thenReturn(true);
        when(mockClientPrincipal.getRegisteredClient()).thenReturn(null);
        SecurityContextHolder.getContext().setAuthentication(mockClientPrincipal);

        Map<String, Object> additionalParameters = new java.util.HashMap<>();
        additionalParameters.put("username", username);
        additionalParameters.put("password", "password");
        CustomPasswordAuthenticationToken authWithMockClient = new CustomPasswordAuthenticationToken(mockClientPrincipal, Set.of("read", "write"), additionalParameters);

        OAuth2AuthenticationException ex = assertThrows(OAuth2AuthenticationException.class, () -> customPasswordAuthenticationProvider.authenticate(authWithMockClient));
        assertEquals(OAuth2ErrorCodes.INVALID_CLIENT, ex.getError().getErrorCode());
    }

    @Test
    void authenticate_ShouldThrowOAuth2AuthenticationException_WhenUserNotFound() {
        String username = "notfound@example.com";
        CustomPasswordAuthenticationToken authentication = createCustomPasswordAuthenticationToken(username, "password");
        when(userDetailsService.loadUserByUsername(username)).thenThrow(new UsernameNotFoundException("User not found"));

        OAuth2AuthenticationException ex = assertThrows(OAuth2AuthenticationException.class, () -> customPasswordAuthenticationProvider.authenticate(authentication));
        assertEquals(OAuth2ErrorCodes.ACCESS_DENIED, ex.getError().getErrorCode());
    }

    @Test
    void authenticate_ShouldThrowOAuth2AuthenticationException_WhenPasswordMismatch() {
        String username = "user@example.com";
        String password = "wrongPassword";
        CustomPasswordAuthenticationToken authentication = createCustomPasswordAuthenticationToken(username, password);
        User user = createMockUser(username);

        when(userDetailsService.loadUserByUsername(username)).thenReturn(user);
        when(bCryptPasswordEncoder.matches(password, "encodedPassword")).thenReturn(false);

        OAuth2AuthenticationException ex = assertThrows(OAuth2AuthenticationException.class, () -> customPasswordAuthenticationProvider.authenticate(authentication));
        assertEquals(OAuth2ErrorCodes.ACCESS_DENIED, ex.getError().getErrorCode());
    }

    @Test
    void authenticate_ShouldThrowOAuth2AuthenticationException_WhenUsernameMismatch() {
        String username = "user@example.com";
        String password = "correctPassword";
        CustomPasswordAuthenticationToken authentication = createCustomPasswordAuthenticationToken(username, password);

        // Create a user with a different username than what was requested
        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("read"),
                new SimpleGrantedAuthority("write")
        );
        User user = new User("different@example.com", "encodedPassword", authorities);

        when(userDetailsService.loadUserByUsername(username)).thenReturn(user);
        when(bCryptPasswordEncoder.matches(password, "encodedPassword")).thenReturn(true);

        OAuth2AuthenticationException ex = assertThrows(OAuth2AuthenticationException.class, () -> customPasswordAuthenticationProvider.authenticate(authentication));
        assertEquals(OAuth2ErrorCodes.ACCESS_DENIED, ex.getError().getErrorCode());
    }

    @Test
    void authenticate_ShouldGenerateTokens_WhenCredentialsAreValid() {
        String username = "user@example.com";
        String password = "correctPassword";
        CustomPasswordAuthenticationToken authentication = createCustomPasswordAuthenticationToken(username, password);
        User user = createMockUser(username);

        OAuth2AccessToken generatedAccessToken = createOAuth2AccessToken();
        OAuth2RefreshToken generatedRefreshToken = createOAuth2RefreshToken();

        when(userDetailsService.loadUserByUsername(username)).thenReturn(user);
        when(bCryptPasswordEncoder.matches(password, "encodedPassword")).thenReturn(true);
        when(tokenGenerator.generate(any())).thenReturn(generatedAccessToken, generatedRefreshToken);

        Authentication result = customPasswordAuthenticationProvider.authenticate(authentication);

        assertNotNull(result);
        assertInstanceOf(OAuth2AccessTokenAuthenticationToken.class, result);
        OAuth2AccessTokenAuthenticationToken tokenResult = (OAuth2AccessTokenAuthenticationToken) result;
        assertNotNull(tokenResult.getAccessToken());
        assertNotNull(tokenResult.getRefreshToken());
        verify(authorizationService, times(1)).save(any());
    }

    @Test
    void authenticate_ShouldBuildAdditionalClaims_WhenAccessTokenIsClaimAccessor() {
        String username = "user@example.com";
        String password = "correctPassword";
        CustomPasswordAuthenticationToken authentication = createCustomPasswordAuthenticationToken(username, password);
        User user = createMockUser(username);

        OAuth2RefreshToken generatedRefreshToken = createOAuth2RefreshToken();

        OAuth2Token claimAccessorToken = mock(OAuth2Token.class, Mockito.withSettings().extraInterfaces(ClaimAccessor.class));
        when(claimAccessorToken.getTokenValue()).thenReturn("access-token-value");
        when(claimAccessorToken.getIssuedAt()).thenReturn(Instant.now());
        when(claimAccessorToken.getExpiresAt()).thenReturn(Instant.now().plusSeconds(3600));
        when(((ClaimAccessor) claimAccessorToken).getClaims()).thenReturn(Map.of("sub", username));

        when(userDetailsService.loadUserByUsername(username)).thenReturn(user);
        when(bCryptPasswordEncoder.matches(password, "encodedPassword")).thenReturn(true);
        when(tokenGenerator.generate(any())).thenReturn(claimAccessorToken, generatedRefreshToken);

        Authentication result = customPasswordAuthenticationProvider.authenticate(authentication);

        assertNotNull(result);
        assertInstanceOf(OAuth2AccessTokenAuthenticationToken.class, result);
        verify(authorizationService, times(1)).save(any());
    }

    @Test
    void authenticate_ShouldThrowOAuth2AuthenticationException_WhenAccessTokenGenerationFails() {
        String username = "user@example.com";
        String password = "correctPassword";
        CustomPasswordAuthenticationToken authentication = createCustomPasswordAuthenticationToken(username, password);
        User user = createMockUser(username);

        when(userDetailsService.loadUserByUsername(username)).thenReturn(user);
        when(bCryptPasswordEncoder.matches(password, "encodedPassword")).thenReturn(true);
        when(tokenGenerator.generate(any())).thenReturn(null);

        OAuth2AuthenticationException ex = assertThrows(OAuth2AuthenticationException.class, () -> customPasswordAuthenticationProvider.authenticate(authentication));
        assertEquals(OAuth2ErrorCodes.SERVER_ERROR, ex.getError().getErrorCode());
        assertTrue(ex.getError().getDescription().contains("access token"));
        verify(authorizationService, never()).save(any());
    }

    @Test
    void authenticate_ShouldThrowOAuth2AuthenticationException_WhenRefreshTokenGenerationFails() {
        String username = "user@example.com";
        String password = "correctPassword";
        CustomPasswordAuthenticationToken authentication = createCustomPasswordAuthenticationToken(username, password);
        User user = createMockUser(username);

        OAuth2AccessToken generatedAccessToken = createOAuth2AccessToken();
        OAuth2Token invalidRefreshToken = mock(OAuth2Token.class);

        when(userDetailsService.loadUserByUsername(username)).thenReturn(user);
        when(bCryptPasswordEncoder.matches(password, "encodedPassword")).thenReturn(true);
        when(tokenGenerator.generate(any())).thenReturn(generatedAccessToken, invalidRefreshToken);

        OAuth2AuthenticationException ex = assertThrows(OAuth2AuthenticationException.class, () -> customPasswordAuthenticationProvider.authenticate(authentication));
        assertEquals(OAuth2ErrorCodes.SERVER_ERROR, ex.getError().getErrorCode());
        assertTrue(ex.getError().getDescription().contains("refresh token"));
        verify(authorizationService, never()).save(any());
    }

    @Test
    void authenticate_ShouldNotGenerateRefreshToken_WhenClientDoesNotSupportRefreshTokenGrant() {
        RegisteredClient clientWithoutRefreshToken = RegisteredClient.withId("test-client-id")
                .clientId("test-client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(new AuthorizationGrantType("custom_password"))
                .scope("read")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(1))
                        .build())
                .build();

        OAuth2ClientAuthenticationToken clientWithoutRefresh = new OAuth2ClientAuthenticationToken(clientWithoutRefreshToken, ClientAuthenticationMethod.CLIENT_SECRET_BASIC, "client-secret");
        SecurityContextHolder.getContext().setAuthentication(clientWithoutRefresh);

        String username = "user@example.com";
        String password = "correctPassword";
        Map<String, Object> additionalParameters = new java.util.HashMap<>();
        additionalParameters.put("username", username);
        additionalParameters.put("password", password);
        CustomPasswordAuthenticationToken authentication = new CustomPasswordAuthenticationToken(clientWithoutRefresh, Set.of("read"), additionalParameters);

        User user = createMockUser(username);
        OAuth2AccessToken generatedAccessToken = createOAuth2AccessToken();

        when(userDetailsService.loadUserByUsername(username)).thenReturn(user);
        when(bCryptPasswordEncoder.matches(password, "encodedPassword")).thenReturn(true);
        when(tokenGenerator.generate(any())).thenReturn(generatedAccessToken);

        Authentication result = customPasswordAuthenticationProvider.authenticate(authentication);

        assertNotNull(result);
        assertInstanceOf(OAuth2AccessTokenAuthenticationToken.class, result);
        OAuth2AccessTokenAuthenticationToken tokenResult = (OAuth2AccessTokenAuthenticationToken) result;
        assertNull(tokenResult.getRefreshToken());
        verify(authorizationService, times(1)).save(any());
    }

    @Test
    void authenticate_ShouldNotGenerateRefreshToken_WhenClientAuthenticationMethodIsNone() {
        RegisteredClient clientWithNoneAuth = RegisteredClient.withId("test-client-id")
                .clientId("test-client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(new AuthorizationGrantType("custom_password"))
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .scope("read")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(1))
                        .build())
                .build();

        OAuth2ClientAuthenticationToken clientWithNoneMethod = new OAuth2ClientAuthenticationToken(clientWithNoneAuth, ClientAuthenticationMethod.NONE, null);
        SecurityContextHolder.getContext().setAuthentication(clientWithNoneMethod);

        String username = "user@example.com";
        String password = "correctPassword";
        Map<String, Object> additionalParameters = new java.util.HashMap<>();
        additionalParameters.put("username", username);
        additionalParameters.put("password", password);
        CustomPasswordAuthenticationToken authentication = new CustomPasswordAuthenticationToken(clientWithNoneMethod, Set.of("read"), additionalParameters);

        User user = createMockUser(username);
        OAuth2AccessToken generatedAccessToken = createOAuth2AccessToken();

        when(userDetailsService.loadUserByUsername(username)).thenReturn(user);
        when(bCryptPasswordEncoder.matches(password, "encodedPassword")).thenReturn(true);
        when(tokenGenerator.generate(any())).thenReturn(generatedAccessToken);

        Authentication result = customPasswordAuthenticationProvider.authenticate(authentication);

        assertNotNull(result);
        assertInstanceOf(OAuth2AccessTokenAuthenticationToken.class, result);
        OAuth2AccessTokenAuthenticationToken tokenResult = (OAuth2AccessTokenAuthenticationToken) result;
        assertNull(tokenResult.getRefreshToken());
        verify(authorizationService, times(1)).save(any());
    }

    @Test
    void authenticate_ShouldFilterScopesToAuthorizedScopes() {
        String username = "user@example.com";
        String password = "correctPassword";
        CustomPasswordAuthenticationToken authentication = createCustomPasswordAuthenticationToken(username, password);

        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("read"),
                new SimpleGrantedAuthority("admin")
        );
        User user = new User(username, "encodedPassword", authorities);

        OAuth2AccessToken generatedAccessToken = createOAuth2AccessToken();
        OAuth2RefreshToken generatedRefreshToken = createOAuth2RefreshToken();

        when(userDetailsService.loadUserByUsername(username)).thenReturn(user);
        when(bCryptPasswordEncoder.matches(password, "encodedPassword")).thenReturn(true);
        when(tokenGenerator.generate(any())).thenReturn(generatedAccessToken, generatedRefreshToken);

        Authentication result = customPasswordAuthenticationProvider.authenticate(authentication);

        assertNotNull(result);
        verify(authorizationService, times(1)).save(any());
    }

    @Test
    void supports_ShouldReturnTrueForCustomPasswordAuthenticationToken() {
        Class<?> authClass = CustomPasswordAuthenticationToken.class;

        boolean supported = customPasswordAuthenticationProvider.supports(authClass);

        assertTrue(supported);
    }

    @Test
    void supports_ShouldReturnFalseForOtherAuthTypes() {
        Class<?> authClass = String.class;

        boolean supported = customPasswordAuthenticationProvider.supports(authClass);

        assertFalse(supported);
    }

    private CustomPasswordAuthenticationToken createCustomPasswordAuthenticationToken(String username, String password) {
        Map<String, Object> additionalParameters = new java.util.HashMap<>();
        additionalParameters.put("username", username);
        additionalParameters.put("password", password);

        return new CustomPasswordAuthenticationToken(clientPrincipal, Set.of("read", "write"), additionalParameters);
    }

    private User createMockUser(String username) {
        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("read"),
                new SimpleGrantedAuthority("write")
        );
        return new User(username, "encodedPassword", authorities);
    }

    private OAuth2AccessToken createOAuth2AccessToken() {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(3600);
        return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-token-value", issuedAt, expiresAt, Set.of("read", "write"));
    }

    private OAuth2RefreshToken createOAuth2RefreshToken() {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(86400);
        return new OAuth2RefreshToken("refresh-token-value", issuedAt, expiresAt);
    }
}
