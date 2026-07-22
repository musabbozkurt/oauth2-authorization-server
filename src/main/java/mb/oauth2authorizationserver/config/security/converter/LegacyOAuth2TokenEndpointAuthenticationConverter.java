package mb.oauth2authorizationserver.config.security.converter;

import jakarta.servlet.http.HttpServletRequest;
import mb.oauth2authorizationserver.utils.SecurityUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.MultiValueMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Backward-compatible token endpoint converter that accepts query parameters
 * in addition to form body parameters for authorization_code and refresh_token grants.
 */
public class LegacyOAuth2TokenEndpointAuthenticationConverter implements AuthenticationConverter {

    private static final Set<String> AUTHORIZATION_CODE_EXCLUDED_PARAMETERS = Set.of(
            OAuth2ParameterNames.GRANT_TYPE,
            OAuth2ParameterNames.CLIENT_ID,
            OAuth2ParameterNames.CODE,
            OAuth2ParameterNames.REDIRECT_URI
    );

    private static final Set<String> REFRESH_TOKEN_EXCLUDED_PARAMETERS = Set.of(
            OAuth2ParameterNames.GRANT_TYPE,
            OAuth2ParameterNames.REFRESH_TOKEN,
            OAuth2ParameterNames.SCOPE
    );

    @Nullable
    @Override
    public Authentication convert(@NonNull HttpServletRequest request) {
        MultiValueMap<String, String> parameters = SecurityUtils.getParameters(request);

        String grantType = parameters.getFirst(OAuth2ParameterNames.GRANT_TYPE);
        if (AuthorizationGrantType.AUTHORIZATION_CODE.getValue().equals(grantType)) {
            return convertAuthorizationCodeGrant(parameters);
        }
        if (AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(grantType)) {
            return convertRefreshTokenGrant(parameters);
        }
        return null;
    }

    @Nullable
    private Authentication convertAuthorizationCodeGrant(MultiValueMap<String, String> parameters) {
        Authentication clientPrincipal = getClientPrincipal();
        if (clientPrincipal == null) {
            return null;
        }

        validateRequiredParameter(parameters, OAuth2ParameterNames.CODE);
        validateOptionalParameter(parameters, OAuth2ParameterNames.REDIRECT_URI);

        return new OAuth2AuthorizationCodeAuthenticationToken(
                parameters.getFirst(OAuth2ParameterNames.CODE),
                clientPrincipal,
                parameters.getFirst(OAuth2ParameterNames.REDIRECT_URI),
                collectAdditionalParameters(parameters, AUTHORIZATION_CODE_EXCLUDED_PARAMETERS)
        );
    }

    @Nullable
    private Authentication convertRefreshTokenGrant(MultiValueMap<String, String> parameters) {
        Authentication clientPrincipal = getClientPrincipal();
        if (clientPrincipal == null) {
            return null;
        }

        validateRequiredParameter(parameters, OAuth2ParameterNames.REFRESH_TOKEN);
        validateOptionalParameter(parameters, OAuth2ParameterNames.SCOPE);

        return new OAuth2RefreshTokenAuthenticationToken(
                parameters.getFirst(OAuth2ParameterNames.REFRESH_TOKEN),
                clientPrincipal,
                parseScopes(parameters.getFirst(OAuth2ParameterNames.SCOPE)),
                collectAdditionalParameters(parameters, REFRESH_TOKEN_EXCLUDED_PARAMETERS)
        );
    }

    @Nullable
    private Authentication getClientPrincipal() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Nullable
    private Set<String> parseScopes(@Nullable String scope) {
        if (StringUtils.isBlank(scope)) {
            return null;
        }
        return new HashSet<>(Arrays.asList(scope.split(" ")));
    }

    private Map<String, Object> collectAdditionalParameters(MultiValueMap<String, String> parameters, Set<String> excludedParameters) {
        Map<String, Object> additionalParameters = new HashMap<>();
        parameters.forEach((key, values) -> {
            if (!excludedParameters.contains(key)) {
                putAdditionalParameter(additionalParameters, key, values);
            }
        });
        return additionalParameters;
    }

    private void validateRequiredParameter(MultiValueMap<String, String> parameters, String parameterName) {
        if (StringUtils.isBlank(parameters.getFirst(parameterName)) || hasMultipleValues(parameters, parameterName)) {
            throwInvalidRequest(parameterName);
        }
    }

    private void validateOptionalParameter(MultiValueMap<String, String> parameters, String parameterName) {
        if (StringUtils.isNotBlank(parameters.getFirst(parameterName)) && hasMultipleValues(parameters, parameterName)) {
            throwInvalidRequest(parameterName);
        }
    }

    private boolean hasMultipleValues(MultiValueMap<String, String> parameters, String parameterName) {
        List<String> values = parameters.get(parameterName);
        return CollectionUtils.isNotEmpty(values) && values.size() != 1;
    }

    private void putAdditionalParameter(Map<String, Object> additionalParameters, String key, List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return;
        }
        additionalParameters.put(key, values.size() == 1 ? values.getFirst() : values.toArray(new String[0]));
    }

    private void throwInvalidRequest(String parameterName) {
        throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, "OAuth 2.0 Parameter: " + parameterName, SecurityUtils.ERROR_URI));
    }
}
