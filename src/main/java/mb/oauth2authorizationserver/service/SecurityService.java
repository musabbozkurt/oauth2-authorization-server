package mb.oauth2authorizationserver.service;

import mb.oauth2authorizationserver.data.entity.SecurityUser;
import org.springframework.security.core.session.SessionInformation;

import java.util.List;
import java.util.Map;

public interface SecurityService {

    String findLoggedInUsername();

    void logout();

    SecurityUser getLoggedInUserInfo();

    void autoLogin(String username, String password);

    void invalidateExpiredSessions(Object principal);

    void invalidateSessions(Object principal, boolean clearExpiredSessions);

    Map<SecurityUser, List<SessionInformation>> getActiveUserSessions();

    boolean evictSession(String sessionId);

    void evictAllSessions();
}
