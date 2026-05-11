package mb.oauth2authorizationserver.config.security.provider;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.config.security.CustomPasswordAuthenticationToken;
import mb.oauth2authorizationserver.config.security.builder.AuthorizationBuilderService;
import mb.oauth2authorizationserver.config.security.model.CustomPasswordUser;
import mb.oauth2authorizationserver.config.security.service.CustomAuthenticationService;
import mb.oauth2authorizationserver.config.security.service.TokenService;
import mb.oauth2authorizationserver.config.security.service.UserLoginAttemptService;
import mb.oauth2authorizationserver.constants.ErrorMessageConstants;
import mb.oauth2authorizationserver.constants.ServiceConstants;
import mb.oauth2authorizationserver.data.entity.SecurityUser;
import mb.oauth2authorizationserver.model.enums.LoginStatus;
import mb.oauth2authorizationserver.utils.SecurityUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
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
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomPasswordAuthenticationProvider implements AuthenticationProvider {

    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;
    private final UserDetailsService userDetailsService;
    private final TokenService tokenService;
    private final AuthorizationBuilderService authorizationBuilderService;
    private final UserLoginAttemptService userLoginAttemptService;
    private final CustomAuthenticationService customAuthenticationService;

    @Override
    public Authentication authenticate(@NonNull Authentication authentication) throws AuthenticationException {
        CustomPasswordAuthenticationToken customPasswordAuthenticationToken = (CustomPasswordAuthenticationToken) authentication;
        OAuth2ClientAuthenticationToken clientPrincipal = SecurityUtils.getAuthenticatedClientElseThrowInvalidClient(customPasswordAuthenticationToken);
        RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();

        if (Objects.isNull(registeredClient)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }

        String username = customPasswordAuthenticationToken.getUsername();
        String password = customPasswordAuthenticationToken.getPassword();
        SecurityUser user;
        try {
            user = (SecurityUser) userDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException _) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.ACCESS_DENIED);
        }

        if (!username.equals(user.getUsername())) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.ACCESS_DENIED);
        }

        if (!customAuthenticationService.authenticate(password, user)) {
            userLoginAttemptService.addToUserLoginAttempt(user, LoginStatus.FAILURE);
            throw new OAuth2AuthenticationException(ErrorMessageConstants.PASSWORD_MISMATCH);
        }

        Map<String, Object> additionalParameters = new HashMap<>();

        return tokenService.findByClientIdAndUsernameAndAuthorizationGrantType(registeredClient.getClientId(), username, ServiceConstants.CUSTOM_PASSWORD)
                .filter(existingToken -> Objects.nonNull(existingToken.getAccessTokenExpiresAt()) && Instant.now().isBefore(existingToken.getAccessTokenExpiresAt()))
                .map(oAuthAccessToken -> {
                    OAuth2Authorization oAuth2Authorization = authorizationBuilderService.toObject(oAuthAccessToken);
                    OAuth2Authorization.Token<OAuth2AccessToken> accessTokenHolder = oAuth2Authorization.getAccessToken();
                    OAuth2Authorization.Token<OAuth2RefreshToken> refreshTokenHolder = oAuth2Authorization.getRefreshToken();

                    if (Objects.isNull(accessTokenHolder) || Objects.isNull(refreshTokenHolder)) {
                        throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR, "The existing authorization is missing access or refresh token.", SecurityUtils.ERROR_URI));
                    }

                    buildAdditionalParameters(additionalParameters, user, registeredClient.getScopes());

                    OAuth2AccessToken existingAccessToken = accessTokenHolder.getToken();
                    if (Objects.nonNull(existingAccessToken.getExpiresAt())) {
                        long remainingSeconds = ChronoUnit.SECONDS.between(Instant.now(), existingAccessToken.getExpiresAt());
                        additionalParameters.put(ServiceConstants.EXPIRES_IN, remainingSeconds);
                    }

                    return new OAuth2AccessTokenAuthenticationToken(registeredClient, clientPrincipal, existingAccessToken, refreshTokenHolder.getToken(), additionalParameters);
                })
                .orElseGet(() -> {
                    Set<String> authorizedScopes = user.getAuthorities()
                            .stream()
                            .map(GrantedAuthority::getAuthority)
                            .filter(scope -> registeredClient.getScopes().contains(scope))
                            .collect(Collectors.toSet());

                    //-----------Create a new Security Context Holder Context----------
                    OAuth2ClientAuthenticationToken oAuth2ClientAuthenticationToken = (OAuth2ClientAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
                    CustomPasswordUser customPasswordUser = new CustomPasswordUser(user);
                    if (Objects.nonNull(oAuth2ClientAuthenticationToken)) {
                        oAuth2ClientAuthenticationToken.setDetails(customPasswordUser);
                    }

                    var newContext = SecurityContextHolder.createEmptyContext();
                    newContext.setAuthentication(oAuth2ClientAuthenticationToken);
                    SecurityContextHolder.setContext(newContext);

                    //-----------TOKEN BUILDERS----------
                    DefaultOAuth2TokenContext.Builder tokenContextBuilder = DefaultOAuth2TokenContext.builder()
                            .registeredClient(registeredClient)
                            .principal(clientPrincipal)
                            .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                            .authorizedScopes(authorizedScopes)
                            .authorizationGrantType(new AuthorizationGrantType(ServiceConstants.CUSTOM_PASSWORD))
                            .authorizationGrant(customPasswordAuthenticationToken);

                    OAuth2Authorization.Builder authorizationBuilder = OAuth2Authorization.withRegisteredClient(registeredClient)
                            .attribute(Principal.class.getName(), clientPrincipal)
                            .principalName(username)
                            .authorizationGrantType(new AuthorizationGrantType(ServiceConstants.CUSTOM_PASSWORD))
                            .authorizedScopes(authorizedScopes);

                    //-----------ACCESS TOKEN----------
                    OAuth2TokenContext tokenContext = tokenContextBuilder.tokenType(OAuth2TokenType.ACCESS_TOKEN).build();
                    OAuth2Token generatedAccessToken = this.tokenGenerator.generate(tokenContext);

                    if (Objects.isNull(generatedAccessToken)) {
                        throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR, "The token generator failed to generate the access token.", SecurityUtils.ERROR_URI));
                    }

                    OAuth2AccessToken accessToken = new OAuth2AccessToken(
                            OAuth2AccessToken.TokenType.BEARER,
                            generatedAccessToken.getTokenValue(),
                            generatedAccessToken.getIssuedAt(),
                            generatedAccessToken.getExpiresAt(),
                            tokenContext.getAuthorizedScopes()
                    );

                    if (generatedAccessToken instanceof ClaimAccessor) {
                        buildAdditionalParameters(additionalParameters, user, registeredClient.getScopes());
                        authorizationBuilder.token(accessToken, metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, additionalParameters));
                    } else {
                        authorizationBuilder.accessToken(accessToken);
                    }

                    OAuth2RefreshToken refreshToken = generateOAuth2RefreshToken(registeredClient, clientPrincipal, tokenContextBuilder, authorizationBuilder);

                    OAuth2Authorization authorization = authorizationBuilder.build();
                    this.authorizationService.save(authorization);

                    beforePostAccessToken();

                    return new OAuth2AccessTokenAuthenticationToken(registeredClient, clientPrincipal, accessToken, refreshToken, additionalParameters);
                });
    }

    @Override
    public boolean supports(@NonNull Class<?> authentication) {
        return CustomPasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private void buildAdditionalParameters(Map<String, Object> additionalParameters, SecurityUser user, Set<String> scopes) {
        additionalParameters.put(ServiceConstants.USERNAME_WITH_UNDERSCORE, user.getUsername());
        additionalParameters.put(ServiceConstants.ORGANIZATION, user.getFirstName() + " " + user.getLastName() + RandomStringUtils.secure().nextAlphabetic(4));
        additionalParameters.put(ServiceConstants.USER_ID, user.getId());
        additionalParameters.put(ServiceConstants.USER_FULL_NAME, user.getFirstName() + " " + user.getLastName());
        additionalParameters.put(ServiceConstants.SCOPE, StringUtils.collectionToDelimitedString(scopes, " "));
    }

    private OAuth2RefreshToken generateOAuth2RefreshToken(RegisteredClient registeredClient, OAuth2ClientAuthenticationToken clientPrincipal, DefaultOAuth2TokenContext.Builder tokenContextBuilder, OAuth2Authorization.Builder authorizationBuilder) {
        //-----------REFRESH TOKEN----------
        OAuth2RefreshToken refreshToken = null;

        if (registeredClient.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN) && !clientPrincipal.getClientAuthenticationMethod().equals(ClientAuthenticationMethod.NONE)) {
            OAuth2Token generatedRefreshToken = this.tokenGenerator.generate(tokenContextBuilder.tokenType(OAuth2TokenType.REFRESH_TOKEN).build());

            if (!(generatedRefreshToken instanceof OAuth2RefreshToken)) {
                throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR, "The token generator failed to generate the refresh token.", SecurityUtils.ERROR_URI));
            }

            refreshToken = (OAuth2RefreshToken) generatedRefreshToken;
            authorizationBuilder.refreshToken(refreshToken);
        }
        return refreshToken;
    }

    private void beforePostAccessToken() {
        log.debug("Received a request to check st cookie. beforePostAccessToken");
        var requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) return;
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        if (Objects.nonNull(request.getCookies())) {
            for (Cookie cookie : request.getCookies()) {
                if (ServiceConstants.ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                    cookie.setMaxAge(0);
                    log.debug("Removed st cookie. beforePostAccessToken");
                }
            }
        }
    }
}
