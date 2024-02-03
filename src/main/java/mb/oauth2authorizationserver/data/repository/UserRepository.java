package mb.oauth2authorizationserver.data.repository;

import mb.oauth2authorizationserver.data.entity.SecurityUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<SecurityUser, Integer> {

    SecurityUser findByUsername(String username);
}
