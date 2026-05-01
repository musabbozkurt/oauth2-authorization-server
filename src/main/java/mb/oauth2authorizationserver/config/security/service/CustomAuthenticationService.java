package mb.oauth2authorizationserver.config.security.service;

import mb.oauth2authorizationserver.data.entity.SecurityUser;

public interface CustomAuthenticationService {

    boolean authenticate(String password, SecurityUser user);
}
