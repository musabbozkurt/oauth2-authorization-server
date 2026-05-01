package mb.oauth2authorizationserver.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public enum GrantType {

    AUTHORIZATION_CODE("authorization_code"),
    CLIENT_CREDENTIALS("client_credentials"),
    IMPLICIT("implicit"),
    PASSWORD("password"),
    REFRESH_TOKEN("refresh_token");

    public static final String GRANTS = "grants";

    private final String name;

    public static List<String> getAllTypes() {
        return Arrays.stream(GrantType.values()).map(GrantType::getName).collect(Collectors.toList());
    }

    public static String getAllTypesAsString() {
        return String.join(",", getAllTypes());
    }
}
