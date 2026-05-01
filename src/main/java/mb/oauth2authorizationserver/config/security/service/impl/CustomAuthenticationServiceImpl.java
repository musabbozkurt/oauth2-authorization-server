package mb.oauth2authorizationserver.config.security.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.config.security.service.CustomAuthenticationService;
import mb.oauth2authorizationserver.data.entity.SecurityUser;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomAuthenticationServiceImpl implements CustomAuthenticationService {

    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager ldapAuthenticationManager;

    @Override
    public boolean authenticate(String password, SecurityUser user) {
        if (passwordEncoder.matches(password, user.getPassword())) {
            return true;
        }

        try {
            return ldapAuthenticationManager.authenticate(new UsernamePasswordAuthenticationToken(user.getUsername(), password)).isAuthenticated() || authenticateWithExternalService(user.getUsername());
        } catch (Exception e) {
            if (!(e instanceof BadCredentialsException)) {
                log.error("Error occurred while requesting LDAP. authenticate - Exception: {}", ExceptionUtils.getStackTrace(e));
            }
            return authenticateWithExternalService(user.getUsername());
        }
    }

    private boolean authenticateWithExternalService(String username) {
        // Placeholder for external authentication service
        // In a real implementation, this would call an external authentication service
        log.debug("Authenticating with external service for user: {}", username);
        return false;
    }
}
