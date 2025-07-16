package mb.oauth2authorizationserver.api.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class SessionRegistryFilter extends GenericFilterBean {

    private final SessionRegistry sessionRegistry;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        final HttpServletRequest request = (HttpServletRequest) servletRequest;

        final HttpSession session = request.getSession(false);
        if (Objects.nonNull(session)) {
            final SessionInformation info = sessionRegistry.getSessionInformation(session.getId());
            if (Objects.isNull(info)) {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (Objects.nonNull(authentication) && Objects.nonNull(authentication.getPrincipal())) {
                    sessionRegistry.registerNewSession(session.getId(), authentication.getPrincipal());
                }
            }
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }
}
