package mb.oauth2authorizationserver.service.impl;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import mb.oauth2authorizationserver.config.security.service.TokenService;
import mb.oauth2authorizationserver.config.security.service.impl.UserDetailsManagerImpl;
import mb.oauth2authorizationserver.constants.ServiceConstants;
import mb.oauth2authorizationserver.data.entity.SecurityUser;
import mb.oauth2authorizationserver.data.repository.AuthorizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RKeys;
import org.redisson.api.RSet;
import org.redisson.api.RType;
import org.redisson.api.RedissonClient;
import org.redisson.api.options.KeysScanOptions;
import org.redisson.client.codec.StringCodec;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityServiceImplTest {

    @Mock
    private UserDetailsManagerImpl userDetailsService;

    @Mock
    private AuthorizationRepository authorizationRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private HttpServletRequest servletRequest;

    @Mock
    private FindByIndexNameSessionRepository<Session> sessionRepository;

    @Mock
    private SessionRegistry sessionRegistry;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityUser securityUser;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private TokenService tokenService;

    @Mock
    private RKeys rKeys;

    private SecurityUser userDetails;

    private SecurityServiceImpl securityService;

    @BeforeEach
    void setUp() {
        securityService = new SecurityServiceImpl(
                userDetailsService,
                authorizationRepository,
                authenticationManager,
                servletRequest,
                sessionRepository,
                sessionRegistry,
                redissonClient,
                tokenService
        );
        userDetails = mock(SecurityUser.class);
        authentication = mock(Authentication.class);
        lenient().when(redissonClient.getKeys()).thenReturn(rKeys);
        lenient().when(rKeys.getKeys(any(KeysScanOptions.class))).thenReturn(Set.of());
    }

    @Test
    void findLoggedInUsername_ShouldReturnUsername_WhenUserIsSecurityUser() {
        // Arrange
        try (MockedStatic<SecurityContextHolder> securityContextHolderMock = mockStatic(SecurityContextHolder.class)) {
            when(securityUser.getUsername()).thenReturn("testuser");
            when(authentication.getPrincipal()).thenReturn(securityUser);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            securityContextHolderMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            // Act
            String result = securityService.findLoggedInUsername();

            // Assertions
            assertEquals("testuser", result, "Should return the username from SecurityUser.");
        }
    }

    @Test
    void findLoggedInUsername_ShouldReturnNull_WhenPrincipalIsNotSecurityUser() {
        // Arrange
        try (MockedStatic<SecurityContextHolder> securityContextHolderMock = mockStatic(SecurityContextHolder.class)) {
            when(authentication.getPrincipal()).thenReturn("notSecurityUser");
            when(securityContext.getAuthentication()).thenReturn(authentication);
            securityContextHolderMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            // Act
            String result = securityService.findLoggedInUsername();

            // Assertions
            assertNull(result, "Should return null when principal is not SecurityUser.");
        }
    }

    @Test
    void logout_ShouldRevokeTokenFromHeader_WhenAuthorizationHeaderPresent() {
        // Arrange
        String token = "sample-token";
        when(servletRequest.getHeader(ServiceConstants.AUTHORIZATION_HEADER_STRING))
                .thenReturn(ServiceConstants.TOKEN_PREFIX + token);
        when(servletRequest.getCookies()).thenReturn(null);

        try (MockedStatic<SecurityContextHolder> securityContextHolderMock = mockStatic(SecurityContextHolder.class)) {
            when(securityContext.getAuthentication()).thenReturn(null);
            securityContextHolderMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            // Act
            securityService.logout();

            // Assertions
            verify(authorizationRepository).deleteByAccessTokenValue(token);
        }
    }

    @Test
    void logout_ShouldRevokeTokenFromCookie_WhenAccessTokenCookiePresent() {
        // Arrange
        String token = "cookie-token";
        Cookie accessTokenCookie = new Cookie(ServiceConstants.ACCESS_TOKEN_COOKIE_NAME, token);
        Cookie[] cookies = {accessTokenCookie};

        when(servletRequest.getHeader(ServiceConstants.AUTHORIZATION_HEADER_STRING)).thenReturn(null);
        when(servletRequest.getCookies()).thenReturn(cookies);

        try (MockedStatic<SecurityContextHolder> securityContextHolderMock = mockStatic(SecurityContextHolder.class)) {
            when(securityContext.getAuthentication()).thenReturn(null);
            securityContextHolderMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            // Act
            securityService.logout();

            // Assertions
            verify(authorizationRepository).deleteByAccessTokenValue(token);
            assertEquals(0, accessTokenCookie.getMaxAge(), "Cookie max age should be set to 0.");
        }
    }

    @Test
    void logout_ShouldInvalidateSessions_WhenAuthenticationExists() {
        // Arrange
        Object principal = new Object();
        when(servletRequest.getHeader(ServiceConstants.AUTHORIZATION_HEADER_STRING)).thenReturn(null);
        when(servletRequest.getCookies()).thenReturn(null);

        try (MockedStatic<SecurityContextHolder> securityContextHolderMock = mockStatic(SecurityContextHolder.class)) {
            when(authentication.getPrincipal()).thenReturn(principal);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            securityContextHolderMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            // Act
            securityService.logout();

            // Assertions
            verify(sessionRegistry).getAllSessions(principal, true);
        }
    }

    @Test
    void logout_ShouldHandleException_WhenAuthorizationRepositoryThrowsException() {
        // Arrange
        String token = "sample-token";
        when(servletRequest.getHeader(ServiceConstants.AUTHORIZATION_HEADER_STRING))
                .thenReturn(ServiceConstants.TOKEN_PREFIX + token);
        when(servletRequest.getCookies()).thenReturn(null);
        doThrow(new RuntimeException("Database error")).when(authorizationRepository).deleteByAccessTokenValue(token);

        try (MockedStatic<SecurityContextHolder> securityContextHolderMock = mockStatic(SecurityContextHolder.class)) {
            when(securityContext.getAuthentication()).thenReturn(null);
            securityContextHolderMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            // Act & Assert - Should not throw exception
            assertDoesNotThrow(() -> securityService.logout(), "Should handle exception gracefully.");
            verify(authorizationRepository).deleteByAccessTokenValue(token);
        }
    }

    @Test
    void logout_ShouldNotRevokeToken_WhenCookieNameDoesNotMatch() {
        // Arrange
        Cookie otherCookie = new Cookie("other-cookie", "some-value");
        Cookie[] cookies = {otherCookie};

        when(servletRequest.getHeader(ServiceConstants.AUTHORIZATION_HEADER_STRING)).thenReturn(null);
        when(servletRequest.getCookies()).thenReturn(cookies);

        try (MockedStatic<SecurityContextHolder> securityContextHolderMock = mockStatic(SecurityContextHolder.class)) {
            when(securityContext.getAuthentication()).thenReturn(null);
            securityContextHolderMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            // Act
            securityService.logout();

            // Assertions
            verify(authorizationRepository, never()).deleteByAccessTokenValue(anyString());
            assertEquals(-1, otherCookie.getMaxAge(), "Other cookie max age should remain unchanged.");
        }
    }

    @Test
    void logout_ShouldNotRevokeToken_WhenAuthorizationDoesNotContainTokenPrefix() {
        // Arrange
        String authorizationWithoutPrefix = "invalid-token-format";
        when(servletRequest.getHeader(ServiceConstants.AUTHORIZATION_HEADER_STRING))
                .thenReturn(authorizationWithoutPrefix);
        when(servletRequest.getCookies()).thenReturn(null);

        try (MockedStatic<SecurityContextHolder> securityContextHolderMock = mockStatic(SecurityContextHolder.class)) {
            when(securityContext.getAuthentication()).thenReturn(null);
            securityContextHolderMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            // Act
            securityService.logout();

            // Assertions
            verify(authorizationRepository, never()).deleteByAccessTokenValue(anyString());
        }
    }


    @Test
    void getLoggedInUserInfo_ShouldReturnSecurityUser_WhenUserIsLoggedIn() {
        // Arrange
        try (MockedStatic<SecurityContextHolder> securityContextHolderMock = mockStatic(SecurityContextHolder.class)) {
            when(securityUser.getUsername()).thenReturn("testuser");
            when(authentication.getPrincipal()).thenReturn(securityUser);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            securityContextHolderMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            // Act
            SecurityUser result = securityService.getLoggedInUserInfo();

            // Assertions
            assertNotNull(result, "Should return SecurityUser when user is logged in.");
            assertEquals(securityUser, result, "Should return the same SecurityUser instance.");
        }
    }

    @Test
    void getLoggedInUserInfo_ShouldReturnNull_WhenUserIsNotLoggedIn() {
        // Arrange
        try (MockedStatic<SecurityContextHolder> securityContextHolderMock = mockStatic(SecurityContextHolder.class)) {
            when(authentication.getPrincipal()).thenReturn("notSecurityUser");
            when(securityContext.getAuthentication()).thenReturn(authentication);
            securityContextHolderMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            // Act
            SecurityUser result = securityService.getLoggedInUserInfo();

            // Assertions
            assertNull(result, "Should return null when user is not logged in.");
        }
    }

    @Test
    void invalidateExpiredSessions_ShouldRemoveOnlyExpiredSessions_WhenCalledWithPrincipal() {
        // Arrange
        Object principal = new Object();
        SessionInformation expiredSession = mock(SessionInformation.class);
        SessionInformation activeSession = mock(SessionInformation.class);

        when(expiredSession.isExpired()).thenReturn(true);
        when(expiredSession.getSessionId()).thenReturn("expired-session-id");
        when(activeSession.isExpired()).thenReturn(false);

        when(sessionRegistry.getAllSessions(principal, true))
                .thenReturn(Arrays.asList(expiredSession, activeSession));

        // Act
        securityService.invalidateExpiredSessions(principal);

        // Assertions
        verify(sessionRepository).deleteById("expired-session-id");
        verify(sessionRegistry).removeSessionInformation("expired-session-id");
        verify(sessionRepository, never()).deleteById(activeSession.getSessionId());
    }

    @Test
    void invalidateSessions_ShouldRemoveAllSessions_WhenCalledWithPrincipal() {
        // Arrange
        Object principal = new Object();
        SessionInformation session1 = mock(SessionInformation.class);
        SessionInformation session2 = mock(SessionInformation.class);

        when(session1.getSessionId()).thenReturn("session-1");
        when(session2.getSessionId()).thenReturn("session-2");

        when(sessionRegistry.getAllSessions(principal, false))
                .thenReturn(Arrays.asList(session1, session2));

        // Act
        securityService.invalidateSessions(principal, false);

        // Assertions
        verify(sessionRepository).deleteById("session-1");
        verify(sessionRepository).deleteById("session-2");
        verify(sessionRegistry).removeSessionInformation("session-1");
        verify(sessionRegistry).removeSessionInformation("session-2");
    }

    @Test
    void autoLogin_ShouldAuthenticateUser_WhenCredentialsAreValid() {
        // Arrange
        String username = "testuser";
        String password = "password";
        UsernamePasswordAuthenticationToken token = mock(UsernamePasswordAuthenticationToken.class);

        when(userDetailsService.loadUserByUsername(username)).thenReturn(securityUser);
        when(securityUser.getAuthorities()).thenReturn(Collections.emptyList());
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(token);

        try (MockedStatic<SecurityContextHolder> securityContextHolderMock = mockStatic(SecurityContextHolder.class)) {
            securityContextHolderMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            // Act
            securityService.autoLogin(username, password);

            // Assertions
            verify(userDetailsService).loadUserByUsername(username);
            verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
            verify(securityContext).setAuthentication(any(UsernamePasswordAuthenticationToken.class));
        }
    }

    @Test
    void getActiveUserSessions_ShouldReturnSessionsFromRedis_WhenActiveSessionsExist() {
        // Arrange
        String sessionId = "1333c60e-7c61-4815-b71a-0f74ca40bf48";
        String principalName = "123456";
        String principalIndexKey = ServiceConstants.principalIndexKey(principalName);
        Session session = mock(Session.class);
        RSet<Object> sessionIdSet = mock(RSet.class);
        when(userDetailsService.loadUserByUsername(principalName)).thenReturn(userDetails);
        when(rKeys.getKeys(any(KeysScanOptions.class))).thenReturn(Set.of(principalIndexKey));
        when(rKeys.getType(principalIndexKey)).thenReturn(RType.SET);
        when(redissonClient.getSet(principalIndexKey, StringCodec.INSTANCE)).thenReturn(sessionIdSet);
        when(sessionIdSet.readAll()).thenReturn(Set.of("\"" + sessionId + "\""));
        when(sessionRepository.findById(sessionId)).thenReturn(session);
        when(session.getLastAccessedTime()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));

        // Act
        Map<SecurityUser, List<SessionInformation>> result = securityService.getActiveUserSessions();

        // Assertions
        assertEquals(1, result.size());
        assertEquals(sessionId, result.get(userDetails).getFirst().getSessionId());
    }

    @Test
    void evictAllSessions_ShouldRevokeAllTokensAndRemoveSessions_WhenSessionsExist() {
        // Arrange
        String sessionId = "1333c60e-7c61-4815-b71a-0f74ca40bf48";
        String principalIndexKey = ServiceConstants.principalIndexKey("123456");
        RSet<Object> sessionIdSet = mock(RSet.class);
        when(rKeys.getKeys(any(KeysScanOptions.class))).thenReturn(Set.of(principalIndexKey));
        when(rKeys.getType(principalIndexKey)).thenReturn(RType.SET);
        when(redissonClient.getSet(principalIndexKey, StringCodec.INSTANCE)).thenReturn(sessionIdSet);
        when(sessionIdSet.readAll()).thenReturn(Set.of("\"" + sessionId + "\""));

        // Act
        securityService.evictAllSessions();

        // Assertions
        verify(tokenService).revokeAllTokens();
        verify(sessionRepository).deleteById(sessionId);
        verify(sessionRegistry).removeSessionInformation(sessionId);
        verify(tokenService, times(0)).revokeTokensOfUser(any());
    }

    @Test
    void evictAllSessions_ShouldRevokeAllTokens_WhenNoSessionsExist() {
        // Arrange
        when(rKeys.getKeys(any(KeysScanOptions.class))).thenReturn(Set.of());

        // Act
        securityService.evictAllSessions();

        // Assertions
        verify(tokenService).revokeAllTokens();
        verify(sessionRepository, never()).deleteById(anyString());
        verify(sessionRegistry, never()).removeSessionInformation(anyString());
    }

    @Test
    void evictSession_ShouldRevokeUserTokensAndRemoveSession_WhenSessionExistsInRedis() {
        // Arrange
        String sessionId = "1333c60e-7c61-4815-b71a-0f74ca40bf48";
        String principalName = "123456";
        String principalIndexKey = ServiceConstants.principalIndexKey(principalName);
        RSet<Object> sessionIdSet = mock(RSet.class);
        when(userDetailsService.loadUserByUsername(principalName)).thenReturn(userDetails);
        when(rKeys.getKeys(any(KeysScanOptions.class))).thenReturn(Set.of(principalIndexKey));
        when(rKeys.getType(principalIndexKey)).thenReturn(RType.SET);
        when(redissonClient.getSet(principalIndexKey, StringCodec.INSTANCE)).thenReturn(sessionIdSet);
        when(sessionIdSet.contains(sessionId)).thenReturn(true);

        // Act
        boolean result = securityService.evictSession(sessionId);

        // Assertions
        assertTrue(result);
        verify(tokenService).revokeTokensOfUser(userDetails);
        verify(sessionRepository).deleteById(sessionId);
        verify(sessionRegistry).removeSessionInformation(sessionId);
    }

    @Test
    void evictSession_ShouldReturnFalse_WhenSessionDoesNotExist() {
        // Arrange
        String sessionId = "1333c60e-7c61-4815-b71a-0f74ca40bf48";
        when(sessionRepository.findById(sessionId)).thenReturn(null);

        // Act
        boolean result = securityService.evictSession(sessionId);

        // Assertions
        assertFalse(result);
        verify(tokenService, never()).revokeTokensOfUser(any());
        verify(sessionRepository, never()).deleteById(anyString());
        verify(sessionRegistry, never()).removeSessionInformation(anyString());
    }
}
