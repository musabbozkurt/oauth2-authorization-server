package mb.oauth2authorizationserver.api.controller;

import mb.oauth2authorizationserver.constants.ErrorMessageConstants;
import mb.oauth2authorizationserver.constants.ServiceConstants;
import mb.oauth2authorizationserver.data.entity.Authorization;
import mb.oauth2authorizationserver.data.entity.Client;
import mb.oauth2authorizationserver.data.entity.SecurityUser;
import mb.oauth2authorizationserver.data.entity.UserLoginAttempt;
import mb.oauth2authorizationserver.model.enums.AuthorityType;
import mb.oauth2authorizationserver.model.enums.GrantType;
import mb.oauth2authorizationserver.model.request.ClientFormData;
import mb.oauth2authorizationserver.model.request.ClientUpdateFormData;
import mb.oauth2authorizationserver.model.request.UserFormData;
import mb.oauth2authorizationserver.service.AdminService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @InjectMocks
    private AdminController adminController;

    @Mock
    private AdminService adminService;

    @Mock
    private Model model;

    @Mock
    private RedirectAttributes redirectAttrs;

    @Mock
    private BindingResult bindingResult;

    @Test
    void index_ShouldReturnIndexPage_WhenAdminPageRequested() {
        String result = adminController.index();
        assertEquals("index", result);
    }

    @Test
    void listTokens_ShouldAddTokensToModel_WhenTokensExist() {
        Page<Authorization> tokenPage = new PageImpl<>(List.of(new Authorization(), new Authorization()), PageRequest.of(0, 20), 2);
        when(adminService.findAllTokens(any())).thenReturn(tokenPage);

        String result = adminController.listTokens(model, 0, 20);

        verify(model).addAttribute("tokens", tokenPage);
        assertEquals("tokens", result);
    }

    @Test
    void revokeToken_ShouldReturnSuccessMessage_WhenTokenExists() {
        Long tokenId = 1L;

        ResponseEntity<String> responseEntity = adminController.revokeToken(tokenId);

        verify(adminService).revokeToken(tokenId);
        assertEquals("Token revoked successfully", responseEntity.getBody());
    }

    @Test
    void revokeAllExpiredTokens_ShouldReturnSuccessMessageWithCount_WhenExpiredTokensExist() {
        when(adminService.revokeAllExpiredTokens()).thenReturn(5L);

        ResponseEntity<String> responseEntity = adminController.revokeAllExpiredTokens();

        verify(adminService).revokeAllExpiredTokens();
        assertEquals("Revoked 5 expired tokens", responseEntity.getBody());
    }

    @Test
    void listClients_ShouldAddClientsToModel_WhenNoSearchParamProvided() {
        Client client = new Client();
        client.setClientId("test-client");
        Page<Client> clientPage = new PageImpl<>(List.of(client), PageRequest.of(0, 20), 1);
        when(adminService.findAllClients(any())).thenReturn(clientPage);

        String result = adminController.listClients(model, 0, 20, null);

        verify(model).addAttribute(ServiceConstants.CLIENTS, clientPage);
        assertEquals(ServiceConstants.CLIENTS, result);
    }

    @Test
    void listClients_ShouldSearchClients_WhenSearchClientIdProvided() {
        Client client = new Client();
        client.setClientId("test-client");
        Page<Client> clientPage = new PageImpl<>(List.of(client), PageRequest.of(0, 20), 1);
        when(adminService.searchClients(eq("clientId"), any())).thenReturn(clientPage);

        String result = adminController.listClients(model, 0, 20, "clientId");

        verify(model).addAttribute(ServiceConstants.CLIENTS, clientPage);
        verify(model).addAttribute("searchClientId", "clientId");
        assertEquals(ServiceConstants.CLIENTS, result);
    }

    @Test
    void addClientForm_ShouldPrepareModelForClientAddition_WhenFormRequested() {
        ClientFormData defaults = ClientFormData.withDefaults();
        ClientUpdateFormData formData = new ClientUpdateFormData(defaults, List.of(), List.of());
        when(adminService.buildClientFormModel(any(ClientFormData.class))).thenReturn(formData);
        when(adminService.generateSecret()).thenReturn("a".repeat(64));

        String result = adminController.addClientForm(model);

        verify(model).addAttribute(GrantType.GRANTS, formData.availableGrants());
        verify(model).addAttribute(AuthorityType.AUTHS, formData.availableAuthorities());
        verify(model).addAttribute(ServiceConstants.CLIENT, defaults);
        verify(model).addAttribute("secret", "a".repeat(64));
        assertEquals("add-client", result);
    }

    @Test
    void addClient_ShouldSaveClientAndRedirect_WhenValidClientDetailsProvided() {
        ClientFormData clientForm = ClientFormData.withDefaults();
        when(adminService.saveClient(clientForm)).thenReturn(null);

        String result = adminController.addClient(model, redirectAttrs, clientForm);

        verify(adminService).saveClient(clientForm);
        verify(redirectAttrs).addFlashAttribute(ServiceConstants.MESSAGE, ServiceConstants.CLIENT_SAVED);
        assertEquals("redirect:/admin/clients", result);
    }

    @Test
    void addClient_ShouldReturnFormWithError_WhenClientIdAlreadyExists() {
        ClientFormData clientForm = ClientFormData.builder().clientId("existingClient").clientSecret("secret").build();
        when(adminService.saveClient(clientForm)).thenReturn(ErrorMessageConstants.CLIENT_ID_ALREADY_EXISTS + "existingClient");
        ClientUpdateFormData formData = new ClientUpdateFormData(clientForm, GrantType.getAllTypes(), AuthorityType.getAllTypes());
        when(adminService.buildClientFormModel(clientForm)).thenReturn(formData);

        String result = adminController.addClient(model, redirectAttrs, clientForm);

        verify(model).addAttribute(eq(ServiceConstants.ERROR), anyString());
        verify(model).addAttribute(ServiceConstants.CLIENT, clientForm);
        verify(redirectAttrs, never()).addFlashAttribute(anyString(), any());
        assertEquals("add-client", result);
    }

    @Test
    void editClient_ShouldPrepareModelForClientEdit_WhenClientExists() {
        String clientId = "test-client";
        ClientFormData clientForm = ClientFormData.builder().id(clientId).clientId("testClient").authorizationGrantTypes("type1,type2").authorities("auth1,auth2").build();
        List<String> grants = GrantType.getAllTypes();
        List<String> auths = AuthorityType.getAllTypes();
        ClientUpdateFormData formData = new ClientUpdateFormData(clientForm, grants, auths);
        when(adminService.getClientUpdateFormData(clientId)).thenReturn(formData);

        String result = adminController.editClient(model, clientId);

        verify(model).addAttribute(GrantType.GRANTS, grants);
        verify(model).addAttribute(AuthorityType.AUTHS, auths);
        verify(model).addAttribute(ServiceConstants.CLIENT, clientForm);
        assertEquals("edit-client", result);
    }

    @Test
    void updateClient_ShouldUpdateClientAndRedirect_WhenValidClientDetailsProvided() {
        String clientId = "test-client";
        ClientFormData clientForm = ClientFormData.builder().clientId("testClient").build();
        when(adminService.updateClient(clientId, clientForm)).thenReturn(null);

        String result = adminController.updateClient(clientId, redirectAttrs, clientForm);

        verify(adminService).updateClient(clientId, clientForm);
        verify(redirectAttrs).addFlashAttribute(ServiceConstants.MESSAGE, ServiceConstants.CLIENT_UPDATED);
        assertEquals("redirect:/admin/clients", result);
    }

    @Test
    void updateClient_ShouldRedirectWithError_WhenDuplicateConstraintViolated() {
        String clientId = "test-client";
        ClientFormData clientForm = ClientFormData.withDefaults();
        doThrow(new DataIntegrityViolationException("duplicate key")).when(adminService).updateClient(clientId, clientForm);

        String result = adminController.updateClient(clientId, redirectAttrs, clientForm);

        verify(redirectAttrs).addFlashAttribute(eq(ServiceConstants.ERROR), anyString());
        assertEquals("redirect:/admin/clients/%s/edit".formatted(clientId), result);
    }

    @Test
    void deleteClient_ShouldReturnSuccessMessage_WhenClientExists() {
        String clientId = "test-client";

        ResponseEntity<String> responseEntity = adminController.deleteClient(clientId);

        verify(adminService).deleteClient(clientId);
        assertEquals("Client deleted successfully", responseEntity.getBody());
    }

    @Test
    void listUsers_ShouldAddActiveUsersToModel_WhenShowAllIsFalse() {
        Page<SecurityUser> userPage = new PageImpl<>(List.of(new SecurityUser()), PageRequest.of(0, 20), 1);
        when(adminService.findAllUsers(any(), eq(false))).thenReturn(userPage);

        String result = adminController.listUsers(model, 0, 20, false, "firstName", "ASC", null, null);

        verify(model).addAttribute(ServiceConstants.USERS, userPage);
        verify(model).addAttribute("showAll", false);
        verify(model).addAttribute("currentSort", "firstName");
        verify(model).addAttribute("currentDirection", "ASC");
        assertEquals(ServiceConstants.USERS, result);
    }

    @Test
    void listUsers_ShouldAddAllUsersToModel_WhenShowAllIsTrue() {
        Page<SecurityUser> userPage = new PageImpl<>(List.of(new SecurityUser()), PageRequest.of(0, 20), 1);
        when(adminService.findAllUsers(any(), eq(true))).thenReturn(userPage);

        String result = adminController.listUsers(model, 0, 20, true, "firstName", "ASC", null, null);

        verify(model).addAttribute(ServiceConstants.USERS, userPage);
        verify(model).addAttribute("showAll", true);
        assertEquals(ServiceConstants.USERS, result);
    }

    @Test
    void listUsers_ShouldSearchUsers_WhenSearchParamsProvided() {
        Page<SecurityUser> userPage = new PageImpl<>(List.of(new SecurityUser()), PageRequest.of(0, 20), 1);
        when(adminService.searchUsers(eq("firstName"), eq("lastName"), any(), eq(true))).thenReturn(userPage);

        String result = adminController.listUsers(model, 0, 20, true, "firstName", "ASC", "firstName", "lastName");

        verify(model).addAttribute(ServiceConstants.USERS, userPage);
        verify(model).addAttribute("showAll", true);
        verify(model).addAttribute("searchName", "firstName");
        verify(model).addAttribute("searchLastName", "lastName");
        assertEquals(ServiceConstants.USERS, result);
    }

    @Test
    void listLoginAttempts_ShouldAddAttemptsToModel_WhenNoSearchParamProvided() {
        Page<UserLoginAttempt> attemptPage = new PageImpl<>(List.of(new UserLoginAttempt()), PageRequest.of(0, 20), 1);
        when(adminService.findAllLoginAttempts(any())).thenReturn(attemptPage);

        String result = adminController.listLoginAttempts(model, 0, 20, null);

        verify(model).addAttribute(ServiceConstants.ATTEMPTS, attemptPage);
        assertEquals("login-attempts", result);
    }

    @Test
    void listLoginAttempts_ShouldSearchByUserId_WhenSearchUserIdProvided() {
        Page<UserLoginAttempt> attemptPage = new PageImpl<>(List.of(new UserLoginAttempt()), PageRequest.of(0, 20), 1);
        when(adminService.searchLoginAttempts(eq(1L), any())).thenReturn(attemptPage);

        String result = adminController.listLoginAttempts(model, 0, 20, "1");

        verify(model).addAttribute(ServiceConstants.ATTEMPTS, attemptPage);
        verify(model).addAttribute("searchUserId", "1");
        assertEquals("login-attempts", result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "abc"})
    void listLoginAttempts_ShouldReturnAllAttempts_WhenSearchUserIdIsInvalid(String searchUserId) {
        Page<UserLoginAttempt> attemptPage = new PageImpl<>(List.of(new UserLoginAttempt()), PageRequest.of(0, 20), 1);
        when(adminService.findAllLoginAttempts(any())).thenReturn(attemptPage);

        String result = adminController.listLoginAttempts(model, 0, 20, searchUserId);

        verify(model).addAttribute(ServiceConstants.ATTEMPTS, attemptPage);
        verify(model).addAttribute("searchUserId", null);
        assertEquals("login-attempts", result);
    }

    @Test
    void addUserForm_ShouldPrepareModelForUserAddition_WhenFormRequested() {
        String result = adminController.addUserForm(model);

        verify(model).addAttribute(ServiceConstants.USER, UserFormData.withDefaults());
        assertEquals(ServiceConstants.ADD_USER, result);
    }

    @Test
    void addUser_ShouldSaveUserAndRedirect_WhenValid() {
        UserFormData userForm = UserFormData.withDefaults();
        when(adminService.saveUser(userForm)).thenReturn(null);
        when(bindingResult.hasErrors()).thenReturn(false);

        String result = adminController.addUser(model, redirectAttrs, userForm, bindingResult);

        verify(adminService).saveUser(userForm);
        verify(redirectAttrs).addFlashAttribute(ServiceConstants.MESSAGE, ServiceConstants.USER_SAVED);
        assertEquals("redirect:/admin/users", result);
    }

    @Test
    void addUser_ShouldReturnFormWithError_WhenUsernameAlreadyExists() {
        UserFormData userForm = UserFormData.builder().username("existing").enabled(true).build();
        when(bindingResult.hasErrors()).thenReturn(false);
        when(adminService.saveUser(userForm)).thenReturn(ErrorMessageConstants.USERNAME_ALREADY_EXISTS + "existing");

        String result = adminController.addUser(model, redirectAttrs, userForm, bindingResult);

        verify(model).addAttribute(eq(ServiceConstants.ERROR), anyString());
        verify(model).addAttribute(ServiceConstants.USER, userForm);
        assertEquals(ServiceConstants.ADD_USER, result);
    }

    @Test
    void addUser_ShouldReturnFormWithError_WhenValidationFails() {
        UserFormData userForm = UserFormData.builder().username("abc").enabled(true).build();
        when(bindingResult.hasErrors()).thenReturn(true);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(new FieldError("user", "username", ErrorMessageConstants.USERNAME_CAN_NOT_BE_EMPTY)));

        String result = adminController.addUser(model, redirectAttrs, userForm, bindingResult);

        verify(model).addAttribute(ServiceConstants.ERROR, ErrorMessageConstants.USERNAME_CAN_NOT_BE_EMPTY);
        verify(model).addAttribute(ServiceConstants.USER, userForm);
        verify(adminService, never()).saveUser(any());
        assertEquals(ServiceConstants.ADD_USER, result);
    }

    @Test
    void updateUser_ShouldRedirectWithError_WhenValidationFails() {
        Long userId = 1L;
        UserFormData userForm = UserFormData.builder().username("abc").enabled(true).build();
        when(bindingResult.hasErrors()).thenReturn(true);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(new FieldError("user", "username", ErrorMessageConstants.USERNAME_CAN_NOT_BE_EMPTY)));

        String result = adminController.updateUser(userId, userForm, bindingResult, redirectAttrs);

        verify(redirectAttrs).addFlashAttribute(ServiceConstants.ERROR, ErrorMessageConstants.USERNAME_CAN_NOT_BE_EMPTY);
        verify(adminService, never()).updateUser(any(), any());
        assertEquals("redirect:/admin/users/%d/edit".formatted(userId), result);
    }

    @Test
    void editUser_ShouldPrepareModelForUserEdit_WhenUserExists() {
        Long userId = 1L;
        UserFormData userForm = UserFormData.builder().id(userId).username("user1").firstName("Name").lastName("Last").email("e@m.com").enabled(true).build();
        when(adminService.getUserUpdateForm(userId)).thenReturn(userForm);

        String result = adminController.editUser(model, userId);

        verify(model).addAttribute(ServiceConstants.USER, userForm);
        assertEquals("edit-user", result);
    }

    @Test
    void updateUser_ShouldUpdateUserAndRedirect_WhenValid() {
        Long userId = 1L;
        UserFormData userForm = UserFormData.withDefaults();
        when(bindingResult.hasErrors()).thenReturn(false);

        String result = adminController.updateUser(userId, userForm, bindingResult, redirectAttrs);

        verify(adminService).updateUser(userId, userForm);
        verify(redirectAttrs).addFlashAttribute(ServiceConstants.MESSAGE, ServiceConstants.USER_UPDATED);
        assertEquals("redirect:/admin/users", result);
    }

    @Test
    void updateUser_ShouldRedirectWithError_WhenDuplicateConstraintViolated() {
        Long userId = 1L;
        UserFormData userForm = UserFormData.withDefaults();
        when(bindingResult.hasErrors()).thenReturn(false);
        doThrow(new DataIntegrityViolationException("duplicate key")).when(adminService).updateUser(userId, userForm);

        String result = adminController.updateUser(userId, userForm, bindingResult, redirectAttrs);

        verify(redirectAttrs).addFlashAttribute(eq(ServiceConstants.ERROR), anyString());
        assertEquals("redirect:/admin/users/1/edit", result);
    }

    @Test
    void deleteUser_ShouldReturnSuccessMessage_WhenUserExists() {
        Long userId = 1L;

        ResponseEntity<String> responseEntity = adminController.deleteUser(userId);

        verify(adminService).deleteUser(userId);
        assertEquals("User deleted successfully", responseEntity.getBody());
    }

    @Test
    void listLoggedInUsers_ShouldAddUserSessionMapToModel_WhenSessionsExist() {
        when(adminService.getActiveUserSessions()).thenReturn(Map.of());

        String result = adminController.listLoggedInUsers(model);

        verify(model).addAttribute(eq("userSessionMap"), any());
        assertEquals("user-sessions", result);
    }

    @Test
    void evictSession_ShouldEvictSessionAndRedirect_WhenSessionExists() {
        UUID sessionId = UUID.randomUUID();
        when(adminService.evictSession(sessionId)).thenReturn(ErrorMessageConstants.SESSION_EVICTED);

        String result = adminController.evictSession(sessionId, redirectAttrs);

        verify(redirectAttrs).addFlashAttribute(ServiceConstants.MESSAGE, ErrorMessageConstants.SESSION_EVICTED);
        assertEquals("redirect:/admin/sessions", result);
    }

    @Test
    void evictSession_ShouldRedirectWithErrorMessage_WhenSessionDoesNotExist() {
        UUID sessionId = UUID.randomUUID();
        when(adminService.evictSession(sessionId)).thenReturn(ErrorMessageConstants.SESSION_NOT_FOUND);

        String result = adminController.evictSession(sessionId, redirectAttrs);

        verify(redirectAttrs).addFlashAttribute(ServiceConstants.MESSAGE, ErrorMessageConstants.SESSION_NOT_FOUND);
        assertEquals("redirect:/admin/sessions", result);
    }

    @Test
    void evictAllSession_ShouldLogoutAndRedirect_WhenEvictionRequested() {
        String result = adminController.evictAllSession(redirectAttrs);

        verify(adminService).evictAllSessions();
        verify(redirectAttrs).addFlashAttribute(ServiceConstants.MESSAGE, ErrorMessageConstants.SESSION_EVICTED);
        assertEquals("redirect:/admin/sessions", result);
    }
}
