package mb.oauth2authorizationserver.service;

import mb.oauth2authorizationserver.data.entity.SecurityUser;

public interface SecurityService {

    String findLoggedInUsername();

    void logout();

    SecurityUser getLoggedInUserInfo();

    void autoLogin(String username, String password);

    void invalidateExpiredSessions(Object principal);

    void invalidateSessions(Object principal, boolean clearExpiredSessions);
}
