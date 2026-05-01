package mb.oauth2authorizationserver.data.repository;

import mb.oauth2authorizationserver.data.entity.UserLoginAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserLoginAttemptRepository extends JpaRepository<UserLoginAttempt, Long> {

    Page<UserLoginAttempt> findAllByOrderByLoginDateDesc(org.springframework.data.domain.Pageable pageable);

    Page<UserLoginAttempt> findByUserId(Long userId, org.springframework.data.domain.Pageable pageable);
}
