package mb.oauth2authorizationserver.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public enum AuthorityType {

    ROLE_CLIENT("ROLE_CLIENT"),
    ROLE_TRUSTED_CLIENT("ROLE_TRUSTED_CLIENT");

    public static final String AUTHS = "auths";

    private final String name;

    public static List<String> getAllTypes() {
        return Arrays.stream(AuthorityType.values()).map(AuthorityType::getName).collect(Collectors.toList());
    }

    public static String getAllTypesAsString() {
        return String.join(",", getAllTypes());
    }
}
