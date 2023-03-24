package mb.springboot3oauth2server.data.repository;

import mb.springboot3oauth2server.data.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, String> {

    Optional<Client> findByClientId(String clientId);
}
