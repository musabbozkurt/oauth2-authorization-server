package mb.oauth2authorizationserver.data.repository;

import mb.oauth2authorizationserver.data.entity.Authority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Set;

@Repository
public interface AuthorityRepository extends JpaRepository<Authority, Long> {

    Set<Authority> findAllByDefaultAuthorityIsTrue();
}
