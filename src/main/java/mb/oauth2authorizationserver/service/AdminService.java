package mb.oauth2authorizationserver.service;

import mb.oauth2authorizationserver.data.entity.Authorization;
import mb.oauth2authorizationserver.data.entity.Client;
import mb.oauth2authorizationserver.data.entity.SecurityUser;
import mb.oauth2authorizationserver.data.entity.UserLoginAttempt;
import mb.oauth2authorizationserver.model.request.ClientFormData;
import mb.oauth2authorizationserver.model.request.ClientUpdateFormData;
import mb.oauth2authorizationserver.model.request.UserFormData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.session.SessionInformation;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AdminService {

    // ── Token ──
    Page<Authorization> findAllTokens(Pageable pageable);

    void revokeToken(String tokenId);

    long revokeAllExpiredTokens();

    long revokeAllTokens();

    // ── Client ──
    Page<Client> findAllClients(Pageable pageable);

    Page<Client> searchClients(String clientId, Pageable pageable);

    ClientUpdateFormData getClientUpdateFormData(String clientId);

    /**
     * Builds the form model data (client form + available grants/authorities)
     * by filtering already-selected values from all available options.
     */
    ClientUpdateFormData buildClientFormModel(ClientFormData clientForm);

    /**
     * Validates and saves a new client.
     *
     * @return error message if validation fails; {@code null} on success
     */
    String saveClient(ClientFormData clientForm);

    /**
     * Generates a cryptographically secure client secret.
     */
    String generateSecret();

    /**
     * Validates and updates an existing client.
     *
     * @return error message if validation fails; {@code null} on success
     */
    String updateClient(String clientId, ClientFormData clientForm);

    void deleteClient(String clientId);

    // ── User ──

    /**
     * Returns a page of users. When {@code showAll} is false (default),
     * only active (enabled) users are returned.
     */
    Page<SecurityUser> findAllUsers(Pageable pageable, boolean showAll);

    /**
     * Searches users by firstName and/or lastName. When {@code showAll} is false,
     * only active (enabled) users are returned.
     */
    Page<SecurityUser> searchUsers(String firstName, String lastName, Pageable pageable, boolean showAll);

    UserFormData getUserUpdateForm(Long userId);

    /**
     * Validates and saves a new user.
     *
     * @return error message if validation fails; {@code null} on success
     */
    String saveUser(UserFormData userForm);

    void updateUser(Long userId, UserFormData userForm);

    void deleteUser(Long userId);

    // ── Login Attempts ──
    Page<UserLoginAttempt> findAllLoginAttempts(Pageable pageable);

    Page<UserLoginAttempt> searchLoginAttempts(Long userId, Pageable pageable);

    // ── Session ──
    Map<SecurityUser, List<SessionInformation>> getActiveUserSessions();

    String evictSession(UUID sessionId);

    void evictAllSessions();
}
