package mb.oauth2authorizationserver.config.security.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.config.security.service.CustomAuthenticationService;
import mb.oauth2authorizationserver.config.security.service.UserLoginAttemptService;
import mb.oauth2authorizationserver.constants.ErrorMessageConstants;
import mb.oauth2authorizationserver.data.entity.SecurityUser;
import mb.oauth2authorizationserver.model.enums.LoginStatus;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAuthenticationProvider implements AuthenticationProvider {

    private final UserDetailsService userDetailsService;
    private final UserLoginAttemptService userLoginAttemptService;
    private final CustomAuthenticationService customAuthenticationService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        final String username = authentication.getName();
        final String password = Objects.nonNull(authentication.getCredentials()) ? authentication.getCredentials().toString() : null;

        boolean isWebAuthentication = authentication.getDetails() instanceof WebAuthenticationDetails;

        if (StringUtils.isEmpty(password) || StringUtils.isEmpty(username)) {
            if (isWebAuthentication) {
                throw new BadCredentialsException("Username and password can not be null");
            }

            throw new OAuth2AuthenticationException(ErrorMessageConstants.CREDENTIALS_CAN_NOT_BE_EMPTY);
        }

        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password.toCharArray());
            }
        });

        SecurityUser user;
        try {
            user = (SecurityUser) userDetailsService.loadUserByUsername(username);
            if (!user.isEnabled()) {
                throw new DisabledException(ErrorMessageConstants.USER_DISABLED);
            }
        } catch (UsernameNotFoundException e) {
            if (isWebAuthentication) {
                throw new DisabledException(ErrorMessageConstants.getErrorMessage(e.getMessage(), ErrorMessageConstants.MULTIPLE_USERS_WITH_SAME_EMAIL));
            }
            throw new OAuth2AuthenticationException(ErrorMessageConstants.USER_NOT_FOUND);
        }

        if (!customAuthenticationService.authenticate(password, user)) {
            userLoginAttemptService.addToUserLoginAttempt(user, LoginStatus.FAILURE);
            if (isWebAuthentication) {
                throw new BadCredentialsException("Password does not match");
            }
            throw new OAuth2AuthenticationException(ErrorMessageConstants.PASSWORD_MISMATCH);
        }

        UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(user, password, user.getAuthorities());

        if (usernamePasswordAuthenticationToken.isAuthenticated()) {
            userLoginAttemptService.addToUserLoginAttempt(user, LoginStatus.SUCCESS);
            SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
            log.debug("User logged in successfully. authenticate - username: {}", username);
            return usernamePasswordAuthenticationToken;
        }

        return authentication;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }
}
