package mb.oauth2authorizationserver.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mb.oauth2authorizationserver.model.enums.AuthorityType;
import mb.oauth2authorizationserver.model.enums.GrantType;

import java.time.Instant;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "client")
public class Client {

    @Id
    private String id;
    private String clientId;
    private Instant clientIdIssuedAt;
    private String clientSecret;
    private Instant clientSecretExpiresAt;
    private String clientName;

    @Column(length = 1000)
    private String clientAuthenticationMethods;

    @Column(length = 1000)
    private String authorizationGrantTypes = GrantType.getAllTypesAsString();

    @Column(length = 1000)
    private String redirectUris;

    @Column(length = 1000)
    private String scopes;

    @Column(length = 2000)
    private String clientSettings;

    @Column(length = 2000)
    private String tokenSettings;

    @Column(length = 1000)
    private String authorities = AuthorityType.getAllTypesAsString();

    private Integer accessTokenValidity = 3600;

    private Integer refreshTokenValidity = 7200;

    private Integer authorizationCodeValidity = 300;
}
