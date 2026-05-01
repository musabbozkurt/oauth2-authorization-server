package mb.oauth2authorizationserver.config.security.service;

import mb.oauth2authorizationserver.data.entity.SecurityUser;
import mb.oauth2authorizationserver.data.entity.UserLoginAttempt;
import mb.oauth2authorizationserver.model.enums.LoginStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserLoginAttemptService {

    void addToUserLoginAttempt(SecurityUser user, LoginStatus loginStatus);

    Page<UserLoginAttempt> findAllOrderByLoginDateDesc(Pageable pageable);

    Page<UserLoginAttempt> searchByUserId(Long userId, Pageable pageable);
}
