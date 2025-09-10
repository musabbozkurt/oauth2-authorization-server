package mb.oauth2authorizationserver.service.impl;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.config.security.service.impl.UserDetailsManagerImpl;
import mb.oauth2authorizationserver.constants.ServiceConstants;
import mb.oauth2authorizationserver.data.entity.SecurityUser;
import mb.oauth2authorizationserver.data.repository.AuthorizationRepository;
import mb.oauth2authorizationserver.service.SecurityService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;

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

    @Override
    public String findLoggedInUsername() {
        Object userDetails = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (userDetails instanceof SecurityUser user) {
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
            return (SecurityUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        }

        return null;
    }

    @Override
    public void invalidateExpiredSessions(Object principal) {
        sessionRegistry
                .getAllSessions(principal, true)
                .stream()
                .filter(SessionInformation::isExpired)
                .forEach(sessionInformation -> {
                    sessionRepository.deleteById(sessionInformation.getSessionId());
                    sessionRegistry.removeSessionInformation(sessionInformation.getSessionId());
                });
    }

    @Override
    public void invalidateSessions(Object principal, boolean clearExpiredSessions) {
        sessionRegistry
                .getAllSessions(principal, clearExpiredSessions)
                .forEach(sessionInformation -> {
                    sessionRepository.deleteById(sessionInformation.getSessionId());
                    sessionRegistry.removeSessionInformation(sessionInformation.getSessionId());
                });
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
}
