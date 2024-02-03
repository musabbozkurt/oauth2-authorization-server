package mb.oauth2authorizationserver.config.service.impl;

import mb.oauth2authorizationserver.config.service.RegisteredClientBuilderService;
import mb.oauth2authorizationserver.data.repository.ClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
public class RegisteredClientRepositoryImpl implements RegisteredClientRepository {

    private final ClientRepository clientRepository;
    private final RegisteredClientBuilderService registeredClientBuilderService;

    public RegisteredClientRepositoryImpl(ClientRepository clientRepository,
                                          RegisteredClientBuilderService registeredClientBuilderService) {
        Assert.notNull(clientRepository, "clientRepository cannot be null");
        this.clientRepository = clientRepository;
        this.registeredClientBuilderService = registeredClientBuilderService;
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        Assert.notNull(registeredClient, "registeredClient cannot be null");
        this.clientRepository.save(registeredClientBuilderService.toEntity(registeredClient));
    }

    @Override
    public RegisteredClient findById(String id) {
        Assert.hasText(id, "id cannot be empty");
        return this.clientRepository.findById(id).map(registeredClientBuilderService::toObject).orElse(null);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        Assert.hasText(clientId, "clientId cannot be empty");
        return this.clientRepository.findByClientId(clientId).map(registeredClientBuilderService::toObject).orElse(null);
    }
}
