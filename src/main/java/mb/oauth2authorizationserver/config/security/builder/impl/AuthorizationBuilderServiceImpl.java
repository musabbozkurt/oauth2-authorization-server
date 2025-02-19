package mb.oauth2authorizationserver.config.security.builder.impl;

import lombok.RequiredArgsConstructor;
import mb.oauth2authorizationserver.config.security.builder.AuthorizationBuilderService;
import mb.oauth2authorizationserver.data.entity.Authorization;
import mb.oauth2authorizationserver.mapper.CustomObjectMapper;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class AuthorizationBuilderServiceImpl implements AuthorizationBuilderService {

    private final RegisteredClientRepository registeredClientRepository;
    private final CustomObjectMapper customObjectMapper;

    @Override
    public OAuth2Authorization toObject(Authorization entity) {
        RegisteredClient registeredClient = this.registeredClientRepository.findById(entity.getRegisteredClientId());
        if (registeredClient == null) {
            throw new DataRetrievalFailureException("The RegisteredClient with id '%s' was not found in the RegisteredClientRepository.".formatted(entity.getRegisteredClientId()));
        }

        OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(registeredClient)
                .id(entity.getId())
                .principalName(entity.getPrincipalName())
                .authorizationGrantType(AuthorizationBuilderService.resolveAuthorizationGrantType(entity.getAuthorizationGrantType()))
                .authorizedScopes(StringUtils.commaDelimitedListToSet(entity.getAuthorizedScopes()))
                .attributes(attributes -> attributes.putAll(customObjectMapper.parseMap(entity.getAttributes())));
        if (entity.getState() != null) {
            builder.attribute(OAuth2ParameterNames.STATE, entity.getState());
        }

        if (entity.getAuthorizationCodeValue() != null) {
            OAuth2AuthorizationCode authorizationCode = new OAuth2AuthorizationCode(entity.getAuthorizationCodeValue(), entity.getAuthorizationCodeIssuedAt(), entity.getAuthorizationCodeExpiresAt());
            builder.token(authorizationCode, metadata -> metadata.putAll(customObjectMapper.parseMap(entity.getAuthorizationCodeMetadata())));
        }

        if (entity.getAccessTokenValue() != null) {
            OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                    entity.getAccessTokenValue(),
                    entity.getAccessTokenIssuedAt(),
                    entity.getAccessTokenExpiresAt(),
                    StringUtils.commaDelimitedListToSet(entity.getAccessTokenScopes()));
            builder.token(accessToken, metadata -> metadata.putAll(customObjectMapper.parseMap(entity.getAccessTokenMetadata())));
        }

        if (entity.getRefreshTokenValue() != null) {
            OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(entity.getRefreshTokenValue(), entity.getRefreshTokenIssuedAt(), entity.getRefreshTokenExpiresAt());
            builder.token(refreshToken, metadata -> metadata.putAll(customObjectMapper.parseMap(entity.getRefreshTokenMetadata())));
        }

        if (entity.getOidcIdTokenValue() != null) {
            OidcIdToken idToken = new OidcIdToken(entity.getOidcIdTokenValue(), entity.getOidcIdTokenIssuedAt(), entity.getOidcIdTokenExpiresAt(), customObjectMapper.parseMap(entity.getOidcIdTokenClaims()));
            builder.token(idToken, metadata -> metadata.putAll(customObjectMapper.parseMap(entity.getOidcIdTokenMetadata())));
        }

        return builder.build();
    }

    @Override
    public Authorization toEntity(OAuth2Authorization authorization) {
        Authorization entity = new Authorization();
        entity.setId(authorization.getId());
        entity.setRegisteredClientId(authorization.getRegisteredClientId());
        entity.setPrincipalName(authorization.getPrincipalName());
        entity.setAuthorizationGrantType(authorization.getAuthorizationGrantType().getValue());
        entity.setAuthorizedScopes(StringUtils.collectionToDelimitedString(authorization.getAuthorizedScopes(), ","));
        entity.setAttributes(customObjectMapper.writeMap(authorization.getAttributes()));
        entity.setState(authorization.getAttribute(OAuth2ParameterNames.STATE));

        OAuth2Authorization.Token<OAuth2AuthorizationCode> authorizationCode = authorization.getToken(OAuth2AuthorizationCode.class);
        setTokenValues(authorizationCode, entity::setAuthorizationCodeValue, entity::setAuthorizationCodeIssuedAt, entity::setAuthorizationCodeExpiresAt, entity::setAuthorizationCodeMetadata);

        OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getToken(OAuth2AccessToken.class);
        setTokenValues(accessToken, entity::setAccessTokenValue, entity::setAccessTokenIssuedAt, entity::setAccessTokenExpiresAt, entity::setAccessTokenMetadata);
        if (accessToken != null && accessToken.getToken().getScopes() != null) {
            entity.setAccessTokenScopes(StringUtils.collectionToDelimitedString(accessToken.getToken().getScopes(), ","));
        }

        OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken = authorization.getToken(OAuth2RefreshToken.class);
        setTokenValues(refreshToken, entity::setRefreshTokenValue, entity::setRefreshTokenIssuedAt, entity::setRefreshTokenExpiresAt, entity::setRefreshTokenMetadata);

        OAuth2Authorization.Token<OidcIdToken> oidcIdToken = authorization.getToken(OidcIdToken.class);
        setTokenValues(oidcIdToken, entity::setOidcIdTokenValue, entity::setOidcIdTokenIssuedAt, entity::setOidcIdTokenExpiresAt, entity::setOidcIdTokenMetadata);
        if (oidcIdToken != null) {
            entity.setOidcIdTokenClaims(customObjectMapper.writeMap(oidcIdToken.getClaims()));
        }

        return entity;
    }

    private void setTokenValues(OAuth2Authorization.Token<?> token,
                                Consumer<String> tokenValueConsumer,
                                Consumer<Instant> issuedAtConsumer,
                                Consumer<Instant> expiresAtConsumer,
                                Consumer<String> metadataConsumer) {
        if (token != null) {
            OAuth2Token oAuth2Token = token.getToken();
            tokenValueConsumer.accept(oAuth2Token.getTokenValue());
            issuedAtConsumer.accept(oAuth2Token.getIssuedAt());
            expiresAtConsumer.accept(oAuth2Token.getExpiresAt());
            metadataConsumer.accept(customObjectMapper.writeMap(token.getMetadata()));
        }
    }
}
