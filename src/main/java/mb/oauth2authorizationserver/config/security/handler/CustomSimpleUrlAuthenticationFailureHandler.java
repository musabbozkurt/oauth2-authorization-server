package mb.oauth2authorizationserver.config.security.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * The following code is a custom authentication failure handler for Spring Security, and it should be added to the login.html file
 * <p>
 * <div th:if="${param.error}">
 * <div class="alert alert-danger alert-danger-custom">
 * <span class="material-icons-outlined"> error </span>
 * <p class="danger-p" th:text="${session.loginError} ?: 'An error occurred during login. Please try again.'"></p>
 * </div>
 * </div>
 */
@Component
public class CustomSimpleUrlAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        String errorMessage = "An error occurred during login. Please try again.";

        if (exception instanceof BadCredentialsException) {
            errorMessage = exception.getMessage();
        } else if (exception instanceof DisabledException) {
            errorMessage = "This account is disabled. Please contact support.";
        }

        // Store the error message in the session to display it on the login page for 'session.loginError'
        request.getSession().setAttribute("loginError", errorMessage);
        setDefaultFailureUrl("/login?error=true");
        super.onAuthenticationFailure(request, response, exception);
    }
}
