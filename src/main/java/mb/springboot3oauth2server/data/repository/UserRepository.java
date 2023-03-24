package mb.springboot3oauth2server.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import mb.springboot3oauth2server.entity.SecurityUser;

public interface UserRepository extends JpaRepository<SecurityUser, Integer> {

    SecurityUser findByUsername(String username);
}
