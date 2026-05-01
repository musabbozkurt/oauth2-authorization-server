package mb.oauth2authorizationserver.service.impl;

import mb.oauth2authorizationserver.data.entity.Client;
import mb.oauth2authorizationserver.data.repository.ClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @InjectMocks
    private ClientServiceImpl clientService;

    @Mock
    private ClientRepository clientRepository;

    private Client client;

    @BeforeEach
    void setUp() {
        client = new Client();
        client.setId("1");
        client.setClientId("test-client");
        client.setClientSecret("secret");
    }

    @Test
    void findByClientId_ShouldReturnClient_WhenClientExists() {
        String clientId = "test-client";
        when(clientRepository.findByClientId(clientId)).thenReturn(Optional.of(client));

        Optional<Client> result = clientService.findByClientId(clientId);

        assertTrue(result.isPresent());
        assertEquals("test-client", result.get().getClientId());
    }

    @Test
    void findByClientId_ShouldReturnEmpty_WhenClientDoesNotExist() {
        String clientId = "non-existent";
        when(clientRepository.findByClientId(clientId)).thenReturn(Optional.empty());

        Optional<Client> result = clientService.findByClientId(clientId);

        assertFalse(result.isPresent());
    }

    @Test
    void existsByClientId_ShouldReturnTrue_WhenClientExists() {
        String clientId = "test-client";
        when(clientRepository.findByClientId(clientId)).thenReturn(Optional.of(client));

        boolean result = clientService.existsByClientId(clientId);

        assertTrue(result);
    }

    @Test
    void existsByClientId_ShouldReturnFalse_WhenClientDoesNotExist() {
        String clientId = "non-existent";
        when(clientRepository.findByClientId(clientId)).thenReturn(Optional.empty());

        boolean result = clientService.existsByClientId(clientId);

        assertFalse(result);
    }

    @Test
    void findAll_ShouldReturnClients_WhenClientsExist() {
        Page<Client> page = new PageImpl<>(List.of(client), PageRequest.of(0, 20), 1);
        when(clientRepository.findAll(any(Pageable.class))).thenReturn(page);

        Page<Client> result = clientService.findAll(PageRequest.of(0, 20));

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void searchByClientId_ShouldReturnFilteredClients_WhenSearchTermProvided() {
        Page<Client> page = new PageImpl<>(List.of(client), PageRequest.of(0, 20), 1);
        when(clientRepository.findByClientIdContainingIgnoreCase(eq("test"), any(Pageable.class))).thenReturn(page);

        Page<Client> result = clientService.searchByClientId("test", PageRequest.of(0, 20));

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void save_ShouldSaveClient_WhenClientIsValid() {
        when(clientRepository.save(any(Client.class))).thenReturn(client);

        Client result = clientService.save(client);

        verify(clientRepository, times(1)).save(client);
        assertNotNull(result);
    }

    @Test
    void update_ShouldUpdateClient_WhenClientIsValid() {
        Client existingClient = new Client();
        existingClient.setId("1");
        existingClient.setClientId("old-client");
        when(clientRepository.save(any(Client.class))).thenReturn(client);

        clientService.update(existingClient, client);

        verify(clientRepository, times(1)).save(client);
    }

    @Test
    void delete_ShouldDeleteClient_WhenClientExists() {
        clientService.delete(client);

        verify(clientRepository, times(1)).delete(client);
    }
}
