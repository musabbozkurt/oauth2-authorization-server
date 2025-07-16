package mb.oauth2authorizationserver.config.security.handler;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@Component("customSavedRequestAwareAuthenticationSuccessHandler")
public class CustomSavedRequestAwareAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final RequestCache requestCache = new HttpSessionRequestCache();
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
    private final SessionRegistry sessionRegistry;

    @PostConstruct
    void init() {
        setUseReferer(true);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        this.handle(request, response, authentication);
        sessionRegistry
                .getAllSessions(authentication.getPrincipal(), true)
                .stream()
                .filter(SessionInformation::isExpired)
                .forEach(sessionInformation -> sessionRegistry.removeSessionInformation(sessionInformation.getSessionId()));
    }

    @Override
    protected void handle(final HttpServletRequest request, final HttpServletResponse response, final Authentication authentication) throws IOException {
        SavedRequest savedRequest = this.requestCache.getRequest(request, response);

        if (Objects.isNull(savedRequest)) {
            String targetUrl = getDefaultTargetUrl();
            if (response.isCommitted()) {
                log.debug("Response has already been committed. Unable to redirect to target url. handle - targetUrl: {}", targetUrl);
            } else {
                this.redirectStrategy.sendRedirect(request, response, targetUrl);
            }
        } else {
            processWhenSavedRequestIsNull(request, response, authentication, savedRequest);
        }
    }

    private void processWhenSavedRequestIsNull(HttpServletRequest request, HttpServletResponse response, Authentication authentication, SavedRequest savedRequest) throws IOException {
        String targetUrlParameter = this.getTargetUrlParameter();

        if (!this.isAlwaysUseDefaultTargetUrl() && (Objects.isNull(targetUrlParameter) || StringUtils.isBlank(request.getParameter(targetUrlParameter)))) {
            this.clearAuthenticationAttributes(request);
            String targetUrl = savedRequest.getRedirectUrl();

            if (StringUtils.isNotBlank(targetUrl) && !targetUrl.contains("redirect_uri")) {
                targetUrl = getDefaultTargetUrl();
            }

            log.debug("Redirecting to default saved request url. processWhenSavedRequestIsNull - targetUrl: {}", targetUrl);
            this.redirectStrategy.sendRedirect(request, response, targetUrl);
        } else {
            this.requestCache.removeRequest(request, response);
            try {
                super.onAuthenticationSuccess(request, response, authentication);
            } catch (ServletException e) {
                log.error("Servlet exception occurred while calling on authentication success. processWhenSavedRequestIsNull - Exception: {}", ExceptionUtils.getStackTrace(e));
            }
        }
    }
}
