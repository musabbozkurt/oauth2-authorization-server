package mb.oauth2authorizationserver.api.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import mb.oauth2authorizationserver.constants.ServiceConstants;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Component
public class MdcLoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        try {
            HttpServletRequest httpRequest = (HttpServletRequest) request;

            Optional.ofNullable(httpRequest.getHeader(ServiceConstants.USERNAME))
                    .or(() -> Optional.ofNullable(httpRequest.getHeader(ServiceConstants.USERNAME_WITH_UNDERSCORE)))
                    .ifPresent(username -> MDC.put(ServiceConstants.USERNAME, username));
            MDC.put("sessionId", httpRequest.getSession().getId());

            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
