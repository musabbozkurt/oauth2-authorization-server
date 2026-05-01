package mb.oauth2authorizationserver.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import mb.oauth2authorizationserver.constants.ErrorMessageConstants;
import mb.oauth2authorizationserver.data.entity.SecurityUser;

@Builder
public record UserFormData(Long id,
                           @NotBlank(message = ErrorMessageConstants.USERNAME_CAN_NOT_BE_EMPTY)
                           String username,
                           String firstName,
                           String lastName,
                           String email,
                           String password,
                           String phoneNumber,
                           boolean enabled,
                           boolean accountNonLocked) {

    public static UserFormData withDefaults() {
        return UserFormData.builder()
                .enabled(true)
                .accountNonLocked(true)
                .build();
    }

    public static UserFormData fromEntity(SecurityUser entity) {
        return UserFormData.builder()
                .id(entity.getId())
                .username(entity.getUsername())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .email(entity.getEmail())
                .phoneNumber(entity.getPhoneNumber())
                .enabled(entity.isEnabled())
                .accountNonLocked(entity.isAccountNonLocked())
                .build();
    }
}
