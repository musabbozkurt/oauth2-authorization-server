package mb.oauth2authorizationserver.data.repository;

import mb.oauth2authorizationserver.data.entity.SecurityUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<SecurityUser, Long> {

    Optional<SecurityUser> findByUsername(String username);

    Page<SecurityUser> findByEnabledTrue(Pageable pageable);

    Page<SecurityUser> findByFirstNameContainingIgnoreCaseAndLastNameContainingIgnoreCase(String firstName, String lastName, Pageable pageable);

    Page<SecurityUser> findByFirstNameContainingIgnoreCaseAndLastNameContainingIgnoreCaseAndEnabledTrue(String firstName, String lastName, Pageable pageable);
}
