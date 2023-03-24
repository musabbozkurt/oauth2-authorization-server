package mb.springboot3oauth2server.data.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import mb.springboot3oauth2server.data.entity.Client;

public interface ClientRepository extends JpaRepository<Client, String> {

    Optional<Client> findByClientId(String clientId);
}
