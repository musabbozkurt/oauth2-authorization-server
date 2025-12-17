package mb.oauth2authorizationserver.api.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mb.oauth2authorizationserver.constants.ServiceConstants;
import mb.oauth2authorizationserver.utils.ClientUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ClientCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest httpServletRequest, @NonNull HttpServletResponse httpServletResponse, @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (StringUtils.isBlank(ClientUtils.getDeviceId(httpServletRequest))) {
            Cookie sessionCookie = new Cookie(ServiceConstants.CLIENT_DEVICE_COOKIE_NAME, RandomStringUtils.secure().nextAlphanumeric(10));
            sessionCookie.setSecure(true);
            sessionCookie.setPath("/");
            sessionCookie.setHttpOnly(true);
            sessionCookie.setDomain(ServiceConstants.DOMAIN);
            sessionCookie.setMaxAge(ServiceConstants.MAX_AGE);

            httpServletResponse.addCookie(sessionCookie);
        }

        httpServletResponse.setHeader(ServiceConstants.ACCESS_CONTROL_ALLOW_HEADERS, ServiceConstants.ACCESS_CONTROL_ALLOW_HEADERS_VALUE);

        filterChain.doFilter(httpServletRequest, httpServletResponse);
    }
}
