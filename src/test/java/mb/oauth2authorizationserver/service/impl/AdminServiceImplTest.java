package mb.oauth2authorizationserver.service.impl;

import mb.oauth2authorizationserver.config.security.service.TokenService;
import mb.oauth2authorizationserver.config.security.service.UserLoginAttemptService;
import mb.oauth2authorizationserver.constants.ErrorMessageConstants;
import mb.oauth2authorizationserver.data.entity.Authorization;
import mb.oauth2authorizationserver.data.entity.Client;
import mb.oauth2authorizationserver.data.entity.SecurityUser;
import mb.oauth2authorizationserver.data.entity.UserLoginAttempt;
import mb.oauth2authorizationserver.model.enums.GrantType;
import mb.oauth2authorizationserver.model.request.ClientFormData;
import mb.oauth2authorizationserver.model.request.ClientUpdateFormData;
import mb.oauth2authorizationserver.model.request.UserFormData;
import mb.oauth2authorizationserver.service.ClientService;
import mb.oauth2authorizationserver.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceImplTest {

    @InjectMocks
    private AdminServiceImpl adminService;

    @Mock
    private ClientService clientService;

    @Mock
    private UserService userService;

    @Mock
    private TokenService tokenService;

    @Mock
    private SessionRegistry sessionRegistry;

    @Mock
    private FindByIndexNameSessionRepository<?> sessionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserLoginAttemptService userLoginAttemptService;

    // ── Token ──────────────────────────────────────────────

    @Test
    void findAllTokens_ShouldReturnTokenPage_WhenTokensExist() {
        Page<Authorization> page = new PageImpl<>(List.of(new Authorization()), PageRequest.of(0, 20), 1);
        when(tokenService.findTokensOrderIdDesc(any())).thenReturn(page);

        Page<Authorization> result = adminService.findAllTokens(PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void revokeToken_ShouldRemoveToken_WhenTokenExists() {
        Long tokenId = 1L;
        Authorization token = new Authorization();
        when(tokenService.findById(String.valueOf(tokenId))).thenReturn(Optional.of(token));

        adminService.revokeToken(tokenId);

        verify(tokenService).findById(String.valueOf(tokenId));
        verify(tokenService).remove(token);
    }

    @Test
    void revokeToken_ShouldDoNothing_WhenTokenDoesNotExist() {
        Long tokenId = 1L;
        when(tokenService.findById(String.valueOf(tokenId))).thenReturn(Optional.empty());

        adminService.revokeToken(tokenId);

        verify(tokenService).findById(String.valueOf(tokenId));
        verify(tokenService, never()).remove(any());
    }

    @Test
    void revokeAllExpiredTokens_ShouldReturnRevokedCount_WhenExpiredTokensExist() {
        when(tokenService.revokeExpiredTokens()).thenReturn(5L);

        long result = adminService.revokeAllExpiredTokens();

        assertEquals(5L, result);
        verify(tokenService).revokeExpiredTokens();
    }

    @Test
    void revokeAllExpiredTokens_ShouldReturnZero_WhenNoExpiredTokensExist() {
        when(tokenService.revokeExpiredTokens()).thenReturn(0L);

        long result = adminService.revokeAllExpiredTokens();

        assertEquals(0L, result);
        verify(tokenService).revokeExpiredTokens();
    }

    // ── Client ─────────────────────────────────────────────

    @Test
    void findAllClients_ShouldReturnClientPage_WhenClientsExist() {
        Client client = new Client();
        Page<Client> page = new PageImpl<>(List.of(client), PageRequest.of(0, 20), 1);
        when(clientService.findAll(any(Pageable.class))).thenReturn(page);

        Page<Client> result = adminService.findAllClients(PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getClientUpdateFormData_ShouldReturnFormDataWithFilteredGrantsAndAuthorities_WhenClientExists() {
        String clientId = "test-client";
        Client entity = new Client();
        entity.setId("1");
        entity.setClientId(clientId);
        entity.setAuthorizationGrantTypes(GrantType.getAllTypesAsString());
        entity.setAuthorities("ROLE_CLIENT");
        when(clientService.findByClientId(clientId)).thenReturn(Optional.of(entity));

        ClientUpdateFormData result = adminService.getClientUpdateFormData(clientId);

        assertNotNull(result);
        assertNotNull(result.clientForm());
        assertFalse(result.availableGrants().contains(GrantType.AUTHORIZATION_CODE.getName()));
        assertFalse(result.availableGrants().contains(GrantType.CLIENT_CREDENTIALS.getName()));
        assertFalse(result.availableAuthorities().contains("ROLE_CLIENT"));
    }

    @Test
    void buildClientFormModel_ShouldReturnEmptyAvailableLists_WhenAllDefaultsSelected() {
        ClientFormData defaults = ClientFormData.withDefaults();

        ClientUpdateFormData result = adminService.buildClientFormModel(defaults);

        assertNotNull(result);
        assertEquals(defaults, result.clientForm());
        assertTrue(result.availableGrants().isEmpty());
        assertTrue(result.availableAuthorities().isEmpty());
    }

    @Test
    void buildClientFormModel_ShouldReturnFilteredLists_WhenPartialSelectionsProvided() {
        ClientFormData partial = ClientFormData.builder().authorizationGrantTypes(GrantType.AUTHORIZATION_CODE.getName()).authorities("ROLE_CLIENT").build();

        ClientUpdateFormData result = adminService.buildClientFormModel(partial);

        assertNotNull(result);
        assertFalse(result.availableGrants().contains(GrantType.AUTHORIZATION_CODE.getName()));
        assertTrue(result.availableGrants().contains(GrantType.CLIENT_CREDENTIALS.getName()));
        assertFalse(result.availableAuthorities().contains("ROLE_CLIENT"));
        assertTrue(result.availableAuthorities().contains("ROLE_TRUSTED_CLIENT"));
    }

    @Test
    void saveClient_ShouldReturnNull_WhenClientIdIsNew() {
        ClientFormData clientForm = ClientFormData.builder().clientId("newClient").clientSecret(adminService.generateSecret()).build();
        when(clientService.existsByClientId(any())).thenReturn(false);

        String result = adminService.saveClient(clientForm);

        assertNull(result);
        verify(clientService).save(any(Client.class));
    }

    @Test
    void saveClient_ShouldReturnErrorMessage_WhenClientIdAlreadyExists() {
        ClientFormData clientForm = ClientFormData.builder().clientId("existingClient").clientSecret(adminService.generateSecret()).build();
        when(clientService.existsByClientId("existingClient")).thenReturn(true);

        String result = adminService.saveClient(clientForm);

        assertNotNull(result);
        assertTrue(result.contains("existingClient"));
        verify(clientService, never()).save(any());
    }

    @Test
    void saveClient_ShouldReturnErrorMessage_WhenClientSecretLengthIsInvalid() {
        ClientFormData clientForm = ClientFormData.builder().clientId("newClient").clientSecret("short").build();

        String result = adminService.saveClient(clientForm);

        assertNotNull(result);
        assertTrue(result.contains("64"));
        verify(clientService, never()).save(any());
    }

    @Test
    void updateClient_ShouldConvertAndUpdate_WhenClientExists() {
        String clientId = "test-client";
        Client existing = new Client();
        existing.setId("1");
        existing.setClientId(clientId);
        ClientFormData clientForm = ClientFormData.withDefaults();
        when(clientService.findByClientId(clientId)).thenReturn(Optional.of(existing));

        String result = adminService.updateClient(clientId, clientForm);

        assertNull(result);
        verify(clientService).update(any(Client.class), any(Client.class));
    }

    @Test
    void updateClient_ShouldReturnErrorMessage_WhenSecretLengthIsInvalid() {
        String clientId = "test-client";
        ClientFormData clientForm = ClientFormData.builder().clientSecret("short").build();

        String result = adminService.updateClient(clientId, clientForm);

        assertNotNull(result);
        assertTrue(result.contains("64"));
        verify(clientService, never()).update(any(), any());
    }

    @Test
    void generateSecret_ShouldReturn64CharHexString() {
        String secret = adminService.generateSecret();

        assertNotNull(secret);
        assertEquals(64, secret.length());
        assertTrue(secret.matches("[0-9a-f]+"));
    }

    @Test
    void deleteClient_ShouldDeleteClient_WhenClientExists() {
        String clientId = "test-client";
        Client client = new Client();
        when(clientService.findByClientId(clientId)).thenReturn(Optional.of(client));

        adminService.deleteClient(clientId);

        verify(clientService).delete(client);
    }

    // ── User ───────────────────────────────────────────────

    @Test
    void findAllUsers_ShouldReturnActiveUsersOnly_WhenShowAllIsFalse() {
        Page<SecurityUser> page = new PageImpl<>(List.of(new SecurityUser()), PageRequest.of(0, 20), 1);
        when(userService.findAllByEnabledTrue(any(Pageable.class))).thenReturn(page);

        Page<SecurityUser> result = adminService.findAllUsers(PageRequest.of(0, 20), false);

        assertEquals(1, result.getTotalElements());
        verify(userService).findAllByEnabledTrue(any(Pageable.class));
        verify(userService, never()).findAll(any(Pageable.class));
    }

    @Test
    void findAllUsers_ShouldReturnAllUsers_WhenShowAllIsTrue() {
        Page<SecurityUser> page = new PageImpl<>(List.of(new SecurityUser()), PageRequest.of(0, 20), 1);
        when(userService.findAll(any(Pageable.class))).thenReturn(page);

        Page<SecurityUser> result = adminService.findAllUsers(PageRequest.of(0, 20), true);

        assertEquals(1, result.getTotalElements());
        verify(userService).findAll(any(Pageable.class));
        verify(userService, never()).findAllByEnabledTrue(any(Pageable.class));
    }

    @Test
    void getUserUpdateForm_ShouldReturnFormFromEntity_WhenUserExists() {
        Long userId = 1L;
        SecurityUser user = new SecurityUser();
        user.setId(userId);
        user.setUsername("testuser");
        user.setFirstName("Test");
        user.setLastName("User");
        when(userService.findById(userId)).thenReturn(user);

        UserFormData result = adminService.getUserUpdateForm(userId);

        assertEquals(userId, result.id());
        assertEquals("testuser", result.username());
    }

    @Test
    void saveUser_ShouldReturnNull_WhenUsernameIsNew() {
        UserFormData userForm = UserFormData.builder().username("newuser").firstName("N").lastName("L").email("e@m.com").password("pass").enabled(true).build();
        when(userService.existsByUsername("newuser")).thenReturn(false);
        when(passwordEncoder.encode("pass")).thenReturn("encoded");

        String result = adminService.saveUser(userForm);

        assertNull(result);
        verify(userService).save(any(SecurityUser.class));
    }

    @Test
    void saveUser_ShouldReturnErrorMessage_WhenUsernameAlreadyExists() {
        UserFormData userForm = UserFormData.builder().username("existing").enabled(true).build();
        when(userService.existsByUsername("existing")).thenReturn(true);

        String result = adminService.saveUser(userForm);

        assertNotNull(result);
        assertTrue(result.contains("existing"));
        verify(userService, never()).save(any());
    }

    @Test
    void updateUser_ShouldApplyFormAndUpdate_WhenPasswordIsNotBlank() {
        Long userId = 1L;
        SecurityUser user = new SecurityUser();
        user.setId(userId);
        UserFormData userForm = UserFormData.builder().id(userId).username("u").firstName("N").lastName("L").email("e@m.com").password("newpass").enabled(true).build();
        when(userService.findById(userId)).thenReturn(user);
        when(passwordEncoder.encode("newpass")).thenReturn("encoded");

        adminService.updateUser(userId, userForm);

        verify(userService).update(eq(user), any(SecurityUser.class));
    }

    @Test
    void updateUser_ShouldNotChangePassword_WhenPasswordIsBlank() {
        Long userId = 1L;
        SecurityUser user = new SecurityUser();
        user.setId(userId);
        user.setPassword("oldpass");
        UserFormData userForm = UserFormData.builder().id(userId).username("u").firstName("N").lastName("L").email("e@m.com").password("").enabled(true).build();
        when(userService.findById(userId)).thenReturn(user);

        adminService.updateUser(userId, userForm);

        verify(passwordEncoder, never()).encode(any());
        verify(userService).update(eq(user), any(SecurityUser.class));
    }

    @Test
    void deleteUser_ShouldDeleteUser_WhenUserExists() {
        Long userId = 1L;
        SecurityUser user = new SecurityUser();
        when(userService.findById(userId)).thenReturn(user);

        adminService.deleteUser(userId);

        verify(userService).delete(user);
    }

    // ── Login Attempts ──────────────────────────────────────

    @Test
    void findAllLoginAttempts_ShouldReturnAttemptPage_WhenAttemptsExist() {
        Page<UserLoginAttempt> page = new PageImpl<>(List.of(new UserLoginAttempt()), PageRequest.of(0, 20), 1);
        when(userLoginAttemptService.findAllOrderByLoginDateDesc(any(Pageable.class))).thenReturn(page);

        Page<UserLoginAttempt> result = adminService.findAllLoginAttempts(PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        verify(userLoginAttemptService).findAllOrderByLoginDateDesc(any(Pageable.class));
    }

    @Test
    void searchLoginAttempts_ShouldReturnFilteredAttemptPage_WhenUserIdProvided() {
        Page<UserLoginAttempt> page = new PageImpl<>(List.of(new UserLoginAttempt()), PageRequest.of(0, 20), 1);
        when(userLoginAttemptService.searchByUserId(eq(1L), any(Pageable.class))).thenReturn(page);

        Page<UserLoginAttempt> result = adminService.searchLoginAttempts(1L, PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        verify(userLoginAttemptService).searchByUserId(eq(1L), any(Pageable.class));
    }

    // ── Session ────────────────────────────────────────────

    @Test
    void getActiveUserSessions_ShouldReturnMapOfUserSessions_WhenActiveSessionsExist() {
        SecurityUser user = new SecurityUser();
        user.setId(1L);
        user.setUsername("user1");
        SessionInformation sessionInfo = mock(SessionInformation.class);
        when(sessionRegistry.getAllPrincipals()).thenReturn(List.of(user));
        when(sessionRegistry.getAllSessions(user, false)).thenReturn(List.of(sessionInfo));

        Map<SecurityUser, List<SessionInformation>> result = adminService.getActiveUserSessions();

        assertEquals(1, result.size());
        assertTrue(result.containsKey(user));
    }

    @Test
    void getActiveUserSessions_ShouldExcludeUsersWithNoActiveSessions_WhenNoActiveSessionsExist() {
        SecurityUser user = new SecurityUser();
        user.setId(1L);
        user.setUsername("user1");
        when(sessionRegistry.getAllPrincipals()).thenReturn(List.of(user));
        when(sessionRegistry.getAllSessions(user, false)).thenReturn(List.of());

        Map<SecurityUser, List<SessionInformation>> result = adminService.getActiveUserSessions();

        assertTrue(result.isEmpty());
    }

    @Test
    void evictSession_ShouldReturnEvictedMessage_WhenSessionExists() {
        UUID sessionId = UUID.randomUUID();
        SessionInformation sessionInfo = mock(SessionInformation.class);
        SecurityUser user = new SecurityUser();
        when(sessionRegistry.getSessionInformation(sessionId.toString())).thenReturn(sessionInfo);
        when(sessionInfo.getPrincipal()).thenReturn(user);

        String result = adminService.evictSession(sessionId);

        assertEquals(ErrorMessageConstants.SESSION_EVICTED, result);
        verify(tokenService).revokeTokensOfUser(user);
        verify(sessionRepository).deleteById(sessionId.toString());
        verify(sessionRegistry).removeSessionInformation(sessionId.toString());
    }

    @Test
    void evictSession_ShouldReturnNotFoundMessage_WhenSessionDoesNotExist() {
        UUID sessionId = UUID.randomUUID();
        when(sessionRegistry.getSessionInformation(sessionId.toString())).thenReturn(null);

        String result = adminService.evictSession(sessionId);

        assertEquals(ErrorMessageConstants.SESSION_NOT_FOUND, result);
    }

    @Test
    void evictAllSessions_ShouldEvictAllSessions_WhenEvictionRequested() {
        SecurityUser user = new SecurityUser();
        user.setId(1L);
        SessionInformation sessionInfo = mock(SessionInformation.class);
        when(sessionRegistry.getAllPrincipals()).thenReturn(List.of(user));
        when(sessionRegistry.getAllSessions(user, false)).thenReturn(List.of(sessionInfo));

        adminService.evictAllSessions();

        verify(sessionRepository).deleteById(sessionInfo.getSessionId());
        verify(sessionRegistry).removeSessionInformation(sessionInfo.getSessionId());
    }
}
