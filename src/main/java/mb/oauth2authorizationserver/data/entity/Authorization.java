package mb.oauth2authorizationserver.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "authorization")
public class Authorization {

    @Id
    @Column
    private String id;
    private String registeredClientId;
    private String principalName;
    private String authorizationGrantType;

    @Column(length = 1000)
    private String authorizedScopes;

    @Lob
    @Column(length = 4000)
    private String attributes;

    @Column(length = 500)
    private String state;

    @Lob
    @Column(length = 4000)
    private String authorizationCodeValue;
    private Instant authorizationCodeIssuedAt;
    private Instant authorizationCodeExpiresAt;
    private String authorizationCodeMetadata;

    @Lob
    @Column(length = 4000)
    private String accessTokenValue;
    private Instant accessTokenIssuedAt;
    private Instant accessTokenExpiresAt;

    @Lob
    @Column(length = 2000)
    private String accessTokenMetadata;
    private String accessTokenType;

    @Column(length = 1000)
    private String accessTokenScopes;

    @Lob
    @Column(length = 4000)
    private String refreshTokenValue;
    private Instant refreshTokenIssuedAt;
    private Instant refreshTokenExpiresAt;

    @Lob
    @Column(length = 2000)
    private String refreshTokenMetadata;

    @Lob
    @Column(length = 4000)
    private String oidcIdTokenValue;
    private Instant oidcIdTokenIssuedAt;
    private Instant oidcIdTokenExpiresAt;

    @Lob
    @Column(length = 2000)
    private String oidcIdTokenMetadata;

    @Lob
    @Column(length = 2000)
    private String oidcIdTokenClaims;
}
