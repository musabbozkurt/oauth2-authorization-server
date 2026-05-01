package mb.oauth2authorizationserver.service;

import mb.oauth2authorizationserver.data.entity.SecurityUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    Page<SecurityUser> getAllUsers(Pageable pageable);

    Page<SecurityUser> findAll(Pageable pageable);

    Page<SecurityUser> findAllByEnabledTrue(Pageable pageable);

    Page<SecurityUser> findByNameAndLastName(String firstName, String lastName, Pageable pageable);

    Page<SecurityUser> findByNameAndLastNameAndEnabledTrue(String firstName, String lastName, Pageable pageable);

    SecurityUser findById(Long id);

    boolean existsByUsername(String username);

    SecurityUser save(SecurityUser user);

    void update(SecurityUser oldUser, SecurityUser newUser);

    void delete(SecurityUser user);

    // Legacy methods for compatibility
    SecurityUser createUser(SecurityUser user);

    SecurityUser getUserById(Long userId);

    SecurityUser updateUserById(SecurityUser newUser);

    void deleteUserById(Long userId);
}
