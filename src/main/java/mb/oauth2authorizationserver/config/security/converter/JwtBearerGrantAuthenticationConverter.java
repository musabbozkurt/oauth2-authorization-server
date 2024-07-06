package mb.oauth2authorizationserver.config.security.converter;

import jakarta.servlet.http.HttpServletRequest;
import mb.oauth2authorizationserver.config.security.JwtBearerGrantAuthenticationToken;
import mb.oauth2authorizationserver.utils.SecurityUtils;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.util.*;

public final class JwtBearerGrantAuthenticationConverter implements AuthenticationConverter {

    @Nullable
    @Override
    public Authentication convert(HttpServletRequest request) {
        // grant_type (REQUIRED)
        String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);

        if (!AuthorizationGrantType.JWT_BEARER.getValue().equals(grantType)) {
            return null;
        }

        MultiValueMap<String, String> parameters = SecurityUtils.getParameters(request);

        // assertion (REQUIRED)
        String assertion = parameters.getFirst(OAuth2ParameterNames.ASSERTION);
        if (!StringUtils.hasText(assertion) || parameters.get(OAuth2ParameterNames.ASSERTION).size() != 1) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }

        // scope (OPTIONAL)
        String scope = parameters.getFirst(OAuth2ParameterNames.SCOPE);
        if (StringUtils.hasText(scope) && parameters.get(OAuth2ParameterNames.SCOPE).size() != 1) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }
        Set<String> requestedScopes = null;
        if (StringUtils.hasText(scope)) {
            requestedScopes = new HashSet<>(Arrays.asList(StringUtils.delimitedListToStringArray(scope, " ")));
        }

        Map<String, Object> additionalParameters = new HashMap<>();
        parameters.forEach((key, value) -> {
            if (!key.equals(OAuth2ParameterNames.GRANT_TYPE) && !key.equals(OAuth2ParameterNames.ASSERTION) && !key.equals(OAuth2ParameterNames.SCOPE)) {
                additionalParameters.put(key, value.getFirst());
            }
        });

        Authentication clientPrincipal = SecurityContextHolder.getContext().getAuthentication();

        return new JwtBearerGrantAuthenticationToken(assertion, clientPrincipal, requestedScopes, additionalParameters);
    }
}
