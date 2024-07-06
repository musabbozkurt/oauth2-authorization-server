package mb.oauth2authorizationserver.service;

import mb.oauth2authorizationserver.data.entity.SecurityUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    Page<SecurityUser> getAllUsers(Pageable pageable);

    SecurityUser createUser(SecurityUser user);

    SecurityUser getUserById(Long userId);

    SecurityUser updateUserById(SecurityUser newUser);

    void deleteUserById(Long userId);
}
