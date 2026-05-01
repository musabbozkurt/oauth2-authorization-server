package mb.oauth2authorizationserver.model.request;

import java.util.List;

public record ClientUpdateFormData(ClientFormData clientForm,
                                   List<String> availableGrants,
                                   List<String> availableAuthorities) {
}
