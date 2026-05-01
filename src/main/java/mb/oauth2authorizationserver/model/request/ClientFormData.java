package mb.oauth2authorizationserver.model.request;

import lombok.Builder;
import mb.oauth2authorizationserver.data.entity.Client;
import mb.oauth2authorizationserver.model.enums.AuthorityType;
import mb.oauth2authorizationserver.model.enums.GrantType;

@Builder
public record ClientFormData(String id,
                             String clientId,
                             String clientSecret,
                             String authorizationGrantTypes,
                             String redirectUris,
                             String authorities,
                             Integer accessTokenValidity,
                             Integer refreshTokenValidity,
                             Integer authorizationCodeValidity,
                             String clientName) {

    public static ClientFormData withDefaults() {
        return ClientFormData.builder()
                .authorizationGrantTypes(GrantType.getAllTypesAsString())
                .redirectUris("https://example.com/callback")
                .authorities(AuthorityType.getAllTypesAsString())
                .accessTokenValidity(3600)
                .refreshTokenValidity(7200)
                .authorizationCodeValidity(300)
                .build();
    }

    public static ClientFormData fromEntity(Client entity) {
        return ClientFormData.builder()
                .id(entity.getId())
                .clientId(entity.getClientId())
                .authorizationGrantTypes(entity.getAuthorizationGrantTypes())
                .redirectUris(entity.getRedirectUris())
                .authorities(entity.getAuthorities())
                .accessTokenValidity(entity.getAccessTokenValidity())
                .refreshTokenValidity(entity.getRefreshTokenValidity())
                .authorizationCodeValidity(entity.getAuthorizationCodeValidity())
                .clientName(entity.getClientName())
                .build();
    }
}
