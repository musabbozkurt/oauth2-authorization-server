package mb.oauth2authorizationserver.config.security.converter;

import jakarta.servlet.http.HttpServletRequest;
import mb.oauth2authorizationserver.config.security.CustomPasswordAuthenticationToken;
import mb.oauth2authorizationserver.constants.ServiceConstants;
import mb.oauth2authorizationserver.utils.SecurityUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CustomPasswordAuthenticationConverter implements AuthenticationConverter {

    private static final List<String> ALLOWED_GRANT_TYPES = List.of("password", "custom_password");

    @Nullable
    @Override
    public Authentication convert(HttpServletRequest request) {
        String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);

        if (!ALLOWED_GRANT_TYPES.contains(grantType)) {
            return null;
        }

        MultiValueMap<@NonNull String, String> parameters = SecurityUtils.getParameters(request);

        // scope (OPTIONAL)
        String scope = parameters.getFirst(OAuth2ParameterNames.SCOPE);
        if (StringUtils.hasText(scope) && parameters.get(OAuth2ParameterNames.SCOPE).size() != 1) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }

        // username (REQUIRED)
        String username = parameters.getFirst(ServiceConstants.USERNAME);
        if (!StringUtils.hasText(username) || parameters.get(ServiceConstants.USERNAME).size() != 1) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }

        // password (REQUIRED)
        String password = parameters.getFirst(ServiceConstants.PASSWORD);
        if (!StringUtils.hasText(password) || parameters.get(ServiceConstants.PASSWORD).size() != 1) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }

        Set<String> requestedScopes = null;
        if (StringUtils.hasText(scope)) {
            requestedScopes = new HashSet<>(Arrays.asList(StringUtils.delimitedListToStringArray(scope, " ")));
        }

        Map<String, Object> additionalParameters = new HashMap<>();
        parameters.forEach((key, value) -> {
            if (!key.equals(OAuth2ParameterNames.GRANT_TYPE) && !key.equals(OAuth2ParameterNames.SCOPE)) {
                additionalParameters.put(key, value.getFirst());
            }
        });

        Authentication clientPrincipal = SecurityContextHolder.getContext().getAuthentication();
        return new CustomPasswordAuthenticationToken(clientPrincipal, requestedScopes, additionalParameters);
    }
}
