package mb.oauth2authorizationserver.config.security;

import jakarta.servlet.http.HttpServletRequest;
import mb.oauth2authorizationserver.config.security.model.HttpRequestDetails;
import org.springframework.security.authentication.AuthenticationDetailsSource;

public class CustomAuthenticationDetailsSource implements AuthenticationDetailsSource<HttpServletRequest, HttpRequestDetails> {

    @Override
    public HttpRequestDetails buildDetails(HttpServletRequest context) {
        return new HttpRequestDetails(context);
    }
}
