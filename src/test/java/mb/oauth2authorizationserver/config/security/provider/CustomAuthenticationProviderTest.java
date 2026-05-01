package mb.oauth2authorizationserver.config.security.provider;

import mb.oauth2authorizationserver.config.security.service.CustomAuthenticationService;
import mb.oauth2authorizationserver.config.security.service.UserLoginAttemptService;
import mb.oauth2authorizationserver.constants.ErrorMessageConstants;
import mb.oauth2authorizationserver.data.entity.SecurityUser;
import mb.oauth2authorizationserver.model.enums.LoginStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomAuthenticationProviderTest {

    @InjectMocks
    private CustomAuthenticationProvider customAuthenticationProvider;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private UserLoginAttemptService userLoginAttemptService;

    @Mock
    private CustomAuthenticationService customAuthenticationService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticate_ShouldThrowBadCredentialsException_WhenUsernameOrPasswordNull_ForWebAuthentication() {
        Authentication authentication = createAuthentication("", "", true);
        assertThrows(BadCredentialsException.class, () -> customAuthenticationProvider.authenticate(authentication));
    }

    @Test
    void authenticate_ShouldThrowOAuth2AuthenticationException_WhenUsernameOrPasswordNull_ForNonWebAuthentication() {
        Authentication authentication = createAuthentication("", "", false);
        assertThrows(OAuth2AuthenticationException.class, () -> customAuthenticationProvider.authenticate(authentication));
    }

    @Test
    void authenticate_ShouldThrowDisabledException_WhenUserIsDisabled() {
        String username = "disabledUser@example.com";
        Authentication authentication = createAuthentication(username, "password", true);
        SecurityUser disabledUser = mock(SecurityUser.class);
        when(disabledUser.isEnabled()).thenReturn(false);
        when(userDetailsService.loadUserByUsername(username)).thenReturn(disabledUser);

        DisabledException ex = assertThrows(DisabledException.class, () -> customAuthenticationProvider.authenticate(authentication));
        assertTrue(ex.getMessage().contains(ErrorMessageConstants.USER_DISABLED));
    }

    @Test
    void authenticate_ShouldThrowDisabledException_WhenUsernameNotFound_ForWebAuthentication() {
        String username = "notfound@example.com";
        Authentication authentication = createAuthentication(username, "password", true);
        when(userDetailsService.loadUserByUsername(username)).thenThrow(new UsernameNotFoundException("some msg"));

        assertThrows(DisabledException.class, () -> customAuthenticationProvider.authenticate(authentication));
    }

    @Test
    void authenticate_ShouldThrowOAuth2AuthenticationException_WhenUsernameNotFound_ForNonWebAuthentication() {
        String username = "notfound@example.com";
        Authentication authentication = createAuthentication(username, "password", false);
        when(userDetailsService.loadUserByUsername(username)).thenThrow(new UsernameNotFoundException("some msg"));

        assertThrows(OAuth2AuthenticationException.class, () -> customAuthenticationProvider.authenticate(authentication));
    }

    @Test
    void authenticate_ShouldRecordFailureAndThrowBadCredentialsException_WhenPasswordDoesNotMatch_ForWebAuthentication() {
        String username = "user@example.com";
        Authentication authentication = createAuthentication(username, "wrongPassword", true);
        SecurityUser user = createEnabledUser();
        when(userDetailsService.loadUserByUsername(username)).thenReturn(user);
        when(customAuthenticationService.authenticate("wrongPassword", user)).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> customAuthenticationProvider.authenticate(authentication));
        verify(userLoginAttemptService, times(1)).addToUserLoginAttempt(user, LoginStatus.FAILURE);
    }

    @Test
    void authenticate_ShouldRecordFailureAndThrowOAuth2AuthenticationException_WhenPasswordMismatch_ForNonWebAuthentication() {
        String username = "user@example.com";
        Authentication authentication = createAuthentication(username, "bad", false);
        SecurityUser user = createEnabledUser();
        when(userDetailsService.loadUserByUsername(username)).thenReturn(user);
        when(customAuthenticationService.authenticate("bad", user)).thenReturn(false);

        assertThrows(OAuth2AuthenticationException.class, () -> customAuthenticationProvider.authenticate(authentication));
        verify(userLoginAttemptService, times(1)).addToUserLoginAttempt(user, LoginStatus.FAILURE);
    }

    @Test
    void authenticate_ShouldAuthenticateAndReturnToken_WhenCredentialsValid_AndTokenAuthenticated() {
        String username = "good@example.com";
        String password = "correct";
        Authentication authentication = createAuthentication(username, password, true);
        SecurityUser user = createEnabledUser();
        when(userDetailsService.loadUserByUsername(username)).thenReturn(user);
        when(customAuthenticationService.authenticate(password, user)).thenReturn(true);

        Authentication result = customAuthenticationProvider.authenticate(authentication);

        assertNotNull(result);
        assertInstanceOf(UsernamePasswordAuthenticationToken.class, result);
        verify(userLoginAttemptService, times(1)).addToUserLoginAttempt(user, LoginStatus.SUCCESS);
        assertEquals(result, SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void supports_ShouldReturnTrueForUsernamePasswordAuthenticationToken_WhenSupported() {
        Class<?> authClass = UsernamePasswordAuthenticationToken.class;
        boolean supported = customAuthenticationProvider.supports(authClass);
        assertTrue(supported);
    }

    @Test
    void supports_ShouldReturnFalseForOtherAuthTypes_WhenNotSupported() {
        Class<?> authClass = String.class;
        boolean supported = customAuthenticationProvider.supports(authClass);
        assertFalse(supported);
    }

    private Authentication createAuthentication(String username, String password, boolean asWebAuthentication) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(username);
        when(authentication.getCredentials()).thenReturn(password);
        if (asWebAuthentication) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            WebAuthenticationDetails details = new WebAuthenticationDetails(request);
            when(authentication.getDetails()).thenReturn(details);
        } else {
            when(authentication.getDetails()).thenReturn(null);
        }
        return authentication;
    }

    private SecurityUser createEnabledUser() {
        SecurityUser user = mock(SecurityUser.class);
        when(user.isEnabled()).thenReturn(true);
        return user;
    }
}
