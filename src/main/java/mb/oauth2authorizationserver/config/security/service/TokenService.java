package mb.oauth2authorizationserver.config.security.service;

import mb.oauth2authorizationserver.data.entity.Authorization;
import mb.oauth2authorizationserver.data.entity.SecurityUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface TokenService {

    Page<Authorization> findTokensOrderIdDesc(Pageable pageable);

    void revokeTokensOfUser(SecurityUser user);

    long revokeExpiredTokens();

    void save(Authorization authorization);

    Optional<Authorization> findById(String id);

    void remove(Authorization entity);

    Optional<Authorization> findByClientIdAndUsernameAndAuthorizationGrantType(String clientId, String username, String authorizationGrantType);
}
