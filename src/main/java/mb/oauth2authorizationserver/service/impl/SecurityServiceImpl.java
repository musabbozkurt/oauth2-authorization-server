package mb.oauth2authorizationserver.service.impl;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.config.security.service.TokenService;
import mb.oauth2authorizationserver.config.security.service.impl.UserDetailsManagerImpl;
import mb.oauth2authorizationserver.constants.ServiceConstants;
import mb.oauth2authorizationserver.data.entity.SecurityUser;
import mb.oauth2authorizationserver.data.repository.AuthorizationRepository;
import mb.oauth2authorizationserver.service.SecurityService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.redisson.api.RSet;
import org.redisson.api.RType;
import org.redisson.api.RedissonClient;
import org.redisson.api.options.KeysScanOptions;
import org.redisson.client.codec.StringCodec;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityServiceImpl implements SecurityService {

    private final UserDetailsManagerImpl userDetailsService;
    private final AuthorizationRepository authorizationRepository;
    private final AuthenticationManager authenticationManager;
    private final HttpServletRequest servletRequest;
    private final FindByIndexNameSessionRepository<?> sessionRepository;
    private final SessionRegistry sessionRegistry;
    private final RedissonClient redissonClient;
    private final TokenService tokenService;

    @Override
    public String findLoggedInUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (Objects.nonNull(authentication) && Objects.nonNull(authentication.getPrincipal()) && authentication.getPrincipal() instanceof SecurityUser user) {
            return user.getUsername();
        }

        return null;
    }

    @Override
    public void logout() {
        String authorization = servletRequest.getHeader(ServiceConstants.AUTHORIZATION_HEADER_STRING);

        if (Objects.nonNull(servletRequest.getCookies())) {
            for (Cookie cookie : servletRequest.getCookies()) {
                if (ServiceConstants.ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                    authorization = ServiceConstants.TOKEN_PREFIX + cookie.getValue();
                    cookie.setMaxAge(0);
                    log.debug("Removed st cookie. logout");
                }
            }
        }

        if (Objects.nonNull(authorization) && authorization.contains(ServiceConstants.TOKEN_PREFIX)) {
            String token = authorization.substring(ServiceConstants.TOKEN_PREFIX.length());

            try {
                authorizationRepository.deleteByAccessTokenValue(token);
            } catch (Exception e) {
                log.error("Exception occurred while revoking token. logout - Exception: {}", ExceptionUtils.getStackTrace(e));
            }
        }

        if (Objects.nonNull(SecurityContextHolder.getContext().getAuthentication())) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            invalidateSessions(authentication.getPrincipal(), true);
        }
    }

    @Override
    public SecurityUser getLoggedInUserInfo() {
        String username = findLoggedInUsername();
        if (StringUtils.isNotBlank(username)) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (Objects.nonNull(authentication) && Objects.nonNull(authentication.getPrincipal())) {
                return (SecurityUser) authentication.getPrincipal();
            }
        }

        return null;
    }

    @Override
    public void invalidateExpiredSessions(Object principal) {
        sessionRegistry
                .getAllSessions(principal, true)
                .stream()
                .filter(SessionInformation::isExpired)
                .forEach(sessionInformation -> deleteSessionFromRedis(sessionInformation.getSessionId()));
    }

    @Override
    public void invalidateSessions(Object principal, boolean clearExpiredSessions) {
        sessionRegistry
                .getAllSessions(principal, clearExpiredSessions)
                .forEach(sessionInformation -> deleteSessionFromRedis(sessionInformation.getSessionId()));
    }

    @Override
    public void autoLogin(String username, String password) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(userDetails, password, userDetails.getAuthorities());

        authenticationManager.authenticate(usernamePasswordAuthenticationToken);

        if (usernamePasswordAuthenticationToken.isAuthenticated()) {
            SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
            log.debug("User auto login successfully. autoLogin - username: {}", username);
        }
    }

    @Override
    public Map<SecurityUser, List<SessionInformation>> getActiveUserSessions() {
        Map<SecurityUser, List<SessionInformation>> userSessionMap = new HashMap<>();

        for (String principalIndexKey : redissonClient.getKeys().getKeys(KeysScanOptions.defaults().pattern(ServiceConstants.PRINCIPAL_INDEX_KEY_PATTERN))) {
            if (redissonClient.getKeys().getType(principalIndexKey) == RType.SET) {
                SecurityUser user = loadUserForPrincipal(principalIndexKey.substring(ServiceConstants.PRINCIPAL_INDEX_KEY_PREFIX.length()));
                if (Objects.nonNull(user)) {
                    List<SessionInformation> activeSessions = redissonClient.getSet(principalIndexKey, StringCodec.INSTANCE).readAll()
                            .stream()
                            .map(this::stripSessionIdQuotes)
                            .map(sessionId -> {
                                Session session = findSessionById(sessionId);
                                return session == null ? null : new SessionInformation(user, sessionId, Date.from(session.getLastAccessedTime()));
                            })
                            .filter(Objects::nonNull)
                            .toList();

                    if (CollectionUtils.isNotEmpty(activeSessions)) {
                        userSessionMap.put(user, activeSessions);
                    }
                }
            }
        }

        return userSessionMap;
    }

    @Override
    @Transactional
    public boolean evictSession(String sessionId) {
        String principalName = findPrincipalForSession(sessionId);
        if (StringUtils.isBlank(principalName) && findSessionById(sessionId) == null) {
            return false;
        }

        if (StringUtils.isNotBlank(principalName)) {
            SecurityUser user = loadUserForPrincipal(principalName);
            if (user != null) {
                tokenService.revokeTokensOfUser(user);
            }
        }

        deleteSessionFromRedis(sessionId);
        return true;
    }

    @Override
    @Transactional
    public void evictAllSessions() {
        Set<String> sessionIds = new HashSet<>();
        for (String principalIndexKey : redissonClient.getKeys().getKeys(KeysScanOptions.defaults().pattern(ServiceConstants.PRINCIPAL_INDEX_KEY_PATTERN))) {
            if (redissonClient.getKeys().getType(principalIndexKey) == RType.SET) {
                redissonClient.getSet(principalIndexKey, StringCodec.INSTANCE).readAll().forEach(value -> sessionIds.add(stripSessionIdQuotes(value)));
            }
        }

        for (String sessionId : sessionIds) {
            deleteSessionFromRedis(sessionId);
        }

        tokenService.revokeAllTokens();
    }

    private void deleteSessionFromRedis(String sessionId) {
        sessionRepository.deleteById(sessionId);
        sessionRegistry.removeSessionInformation(sessionId);
    }

    private Session findSessionById(String sessionId) {
        try {
            return sessionRepository.findById(sessionId);
        } catch (IllegalStateException ex) {
            log.warn("Skipping corrupt session {}: {}", sessionId, ex.getMessage());
            return null;
        }
    }

    private String findPrincipalForSession(String sessionId) {
        String quotedSessionId = "\"" + sessionId + "\"";
        for (String principalIndexKey : redissonClient.getKeys().getKeys(KeysScanOptions.defaults().pattern(ServiceConstants.PRINCIPAL_INDEX_KEY_PATTERN))) {
            if (redissonClient.getKeys().getType(principalIndexKey) == RType.SET) {
                RSet<Object> sessionIdSet = redissonClient.getSet(principalIndexKey, StringCodec.INSTANCE);
                if (sessionIdSet.contains(sessionId) || sessionIdSet.contains(quotedSessionId)) {
                    return principalIndexKey.substring(ServiceConstants.PRINCIPAL_INDEX_KEY_PREFIX.length());
                }
            }
        }
        return null;
    }

    private SecurityUser loadUserForPrincipal(String principalName) {
        try {
            return userDetailsService.loadUserByUsername(principalName);
        } catch (Exception e) {
            log.warn("Skipping active sessions for principal {}: {}", principalName, e.getMessage());
            return null;
        }
    }

    private String stripSessionIdQuotes(Object value) {
        String sessionId = String.valueOf(value);
        if (sessionId.length() >= 2 && sessionId.startsWith("\"") && sessionId.endsWith("\"")) {
            return sessionId.substring(1, sessionId.length() - 1);
        }
        return sessionId;
    }
}
