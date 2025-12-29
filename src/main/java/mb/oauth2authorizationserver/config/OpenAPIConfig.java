package mb.oauth2authorizationserver.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.OAuthFlow;
import io.swagger.v3.oas.annotations.security.OAuthFlows;
import io.swagger.v3.oas.annotations.security.OAuthScope;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;

@OpenAPIDefinition(
        servers = {
                @Server(url = "/")
        },
        info = @Info(
                title = "${openapi.title}",
                description = "${openapi.description}",
                version = "${openapi.version}"
        ),
        security = {
                @SecurityRequirement(name = "security_auth")
        }
)
@SecurityScheme(
        name = "security_auth",
        type = SecuritySchemeType.OAUTH2,
        flows = @OAuthFlows(
                password = @OAuthFlow(tokenUrl = "${openapi.oauth-flow.token-url}",
                        scopes = {
                                @OAuthScope(name = "read", description = "read scope"),
                                @OAuthScope(name = "write", description = "write scope"),
                                @OAuthScope(name = "openid", description = "openid scope"),
                                @OAuthScope(name = "profile", description = "profile scope")
                        }
                )
        )
)
public class OpenAPIConfig {
}
