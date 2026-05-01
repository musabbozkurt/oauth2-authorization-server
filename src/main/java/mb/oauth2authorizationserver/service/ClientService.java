package mb.oauth2authorizationserver.service;

import mb.oauth2authorizationserver.data.entity.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ClientService {

    Page<Client> findAll(Pageable pageable);

    Optional<Client> findByClientId(String clientId);

    Page<Client> searchByClientId(String clientId, Pageable pageable);

    boolean existsByClientId(String clientId);

    Client save(Client client);

    void update(Client oldClient, Client newClient);

    void delete(Client client);
}
