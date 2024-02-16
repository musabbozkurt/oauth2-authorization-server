package mb.oauth2authorizationserver.config.security;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;
import org.springframework.util.Assert;

import java.io.Serial;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Getter
@EqualsAndHashCode(callSuper = false)
public class JwtBearerGrantAuthenticationToken extends OAuth2AuthorizationGrantAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;
    private final String assertion;
    private final Set<String> scopes;

    public JwtBearerGrantAuthenticationToken(String assertion,
                                             Authentication clientPrincipal,
                                             @Nullable Set<String> scopes,
                                             @Nullable Map<String, Object> additionalParameters) {
        super(AuthorizationGrantType.JWT_BEARER, clientPrincipal, additionalParameters);
        Assert.hasText(assertion, "assertion cannot be empty");
        this.assertion = assertion;
        this.scopes = Collections.unmodifiableSet(scopes != null ? new HashSet<>(scopes) : Collections.emptySet());
    }

}