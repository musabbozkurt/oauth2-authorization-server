package mb.oauth2authorizationserver.config.security;

import lombok.Getter;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;

import java.io.Serial;
import java.util.*;

@Getter
public class CustomPasswordAuthenticationToken extends OAuth2AuthorizationGrantAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;
    private final String username;
    private final String password;
    private final Set<String> scopes;

    public CustomPasswordAuthenticationToken(Authentication clientPrincipal,
                                             @Nullable Set<String> scopes,
                                             @Nullable Map<String, Object> additionalParameters) {
        super(new AuthorizationGrantType("custom_password"), clientPrincipal, additionalParameters);
        this.username = (String) Objects.requireNonNull(additionalParameters).get("username");
        this.password = (String) additionalParameters.get("password");
        this.scopes = Collections.unmodifiableSet(scopes != null ? new HashSet<>(scopes) : Collections.emptySet());
    }
}
