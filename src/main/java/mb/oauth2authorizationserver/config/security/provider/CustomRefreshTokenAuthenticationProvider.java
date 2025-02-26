package mb.oauth2authorizationserver.config.security.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.utils.SecurityUtils;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomRefreshTokenAuthenticationProvider implements AuthenticationProvider {

    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;

    @Override
    @Transactional
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2RefreshTokenAuthenticationToken refreshTokenAuthentication = (OAuth2RefreshTokenAuthenticationToken) authentication;

        // Ensure the client is authenticated
        OAuth2ClientAuthenticationToken clientPrincipal = SecurityUtils.getAuthenticatedClientElseThrowInvalidClient(refreshTokenAuthentication);
        RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();

        if (Objects.isNull(registeredClient) || !registeredClient.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        }

        String refreshToken = refreshTokenAuthentication.getRefreshToken();
        OAuth2Authorization authorization = this.authorizationService.findByToken(refreshToken, OAuth2TokenType.REFRESH_TOKEN);

        OAuth2RefreshToken oauth2RefreshToken = validateAndGetOAuth2RefreshToken(authorization, registeredClient);

        Instant expiresAt = oauth2RefreshToken.getExpiresAt();
        if (Objects.nonNull(expiresAt) && expiresAt.isBefore(Instant.now())) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }

        // Validate authorized scopes
        Set<String> authorizedScopes = authorization.getAuthorizedScopes();
        Set<String> requestedScopes = refreshTokenAuthentication.getScopes();
        if (!requestedScopes.isEmpty() && !authorizedScopes.containsAll(requestedScopes)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_SCOPE);
        }

        // Use requested scopes or fallback to original scopes
        Set<String> scopesToUse = !requestedScopes.isEmpty() ? requestedScopes : authorizedScopes;

        // Build token context
        DefaultOAuth2TokenContext.Builder tokenContextBuilder = DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient)
                .principal(clientPrincipal)
                .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                .authorization(authorization)
                .authorizedScopes(scopesToUse)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrant(refreshTokenAuthentication);

        // Generate access token
        OAuth2TokenContext tokenContext = tokenContextBuilder.tokenType(OAuth2TokenType.ACCESS_TOKEN).build();
        OAuth2Token generatedAccessToken = this.tokenGenerator.generate(tokenContext);

        if (generatedAccessToken == null) {
            OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR, "The token generator failed to generate the access token.", SecurityUtils.ERROR_URI);
            throw new OAuth2AuthenticationException(error);
        }

        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, generatedAccessToken.getTokenValue(), generatedAccessToken.getIssuedAt(), generatedAccessToken.getExpiresAt(), scopesToUse);

        // Update or reuse refresh token based on configuration
        OAuth2RefreshToken newRefreshToken;
        if (registeredClient.getTokenSettings().isReuseRefreshTokens()) {
            newRefreshToken = oauth2RefreshToken;
        } else {
            tokenContext = tokenContextBuilder.tokenType(OAuth2TokenType.REFRESH_TOKEN).build();
            OAuth2Token generatedRefreshToken = this.tokenGenerator.generate(tokenContext);

            if (generatedRefreshToken == null) {
                OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR, "The token generator failed to generate the refresh token.", SecurityUtils.ERROR_URI);
                throw new OAuth2AuthenticationException(error);
            }

            newRefreshToken = new OAuth2RefreshToken(generatedRefreshToken.getTokenValue(), generatedRefreshToken.getIssuedAt(), generatedRefreshToken.getExpiresAt());
        }

        // Build additional parameters
        Map<String, Object> additionalParameters = new HashMap<>();

        // Extract claims if available
        if (generatedAccessToken instanceof ClaimAccessor claimAccessor) {
            Map<String, Object> claims = new HashMap<>(claimAccessor.getClaims());
            additionalParameters.putAll(claims);
        }

        // Update authorization
        OAuth2Authorization.Builder authorizationBuilder = OAuth2Authorization.from(authorization).accessToken(accessToken);

        if (newRefreshToken != oauth2RefreshToken) {
            authorizationBuilder.refreshToken(newRefreshToken);
        }

        authorizationService.save(authorizationBuilder.build());

        return new OAuth2AccessTokenAuthenticationToken(registeredClient, clientPrincipal, accessToken, newRefreshToken, additionalParameters);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2RefreshTokenAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private OAuth2RefreshToken validateAndGetOAuth2RefreshToken(OAuth2Authorization authorization, RegisteredClient registeredClient) {
        if (authorization == null || !registeredClient.getId().equals(authorization.getRegisteredClientId())) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }

        OAuth2Authorization.Token<OAuth2RefreshToken> authorizationRefreshToken = authorization.getRefreshToken();
        if (authorizationRefreshToken == null) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }

        return authorizationRefreshToken.getToken();
    }
}
