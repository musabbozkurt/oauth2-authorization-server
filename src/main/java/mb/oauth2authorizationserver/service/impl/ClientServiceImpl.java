package mb.oauth2authorizationserver.service.impl;

import lombok.RequiredArgsConstructor;
import mb.oauth2authorizationserver.data.entity.Client;
import mb.oauth2authorizationserver.data.repository.ClientRepository;
import mb.oauth2authorizationserver.service.ClientService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClientServiceImpl implements ClientService {

    private final ClientRepository clientRepository;

    @Override
    public Page<Client> findAll(Pageable pageable) {
        return clientRepository.findAll(pageable);
    }

    @Override
    public Optional<Client> findByClientId(String clientId) {
        return clientRepository.findByClientId(clientId);
    }

    @Override
    public Page<Client> searchByClientId(String clientId, Pageable pageable) {
        return clientRepository.findByClientIdContainingIgnoreCase(clientId, pageable);
    }

    @Override
    public boolean existsByClientId(String clientId) {
        return clientRepository.findByClientId(clientId).isPresent();
    }

    @Override
    @Transactional
    public Client save(Client client) {
        return clientRepository.save(client);
    }

    @Override
    @Transactional
    public void update(Client oldClient, Client newClient) {
        clientRepository.save(newClient);
    }

    @Override
    @Transactional
    public void delete(Client client) {
        clientRepository.delete(client);
    }
}
