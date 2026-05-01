package mb.oauth2authorizationserver.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.config.security.service.TokenService;
import mb.oauth2authorizationserver.config.security.service.UserLoginAttemptService;
import mb.oauth2authorizationserver.constants.ErrorMessageConstants;
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
import mb.oauth2authorizationserver.service.ClientService;
import mb.oauth2authorizationserver.service.UserService;
import mb.oauth2authorizationserver.utils.SecurityUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    public static final String CLIENT_NOT_FOUND_WITH_ID = "Client not found with id: ";
    private static final int CLIENT_SECRET_LENGTH = 64;

    private final ClientService clientService;
    private final UserService userService;
    private final TokenService tokenService;
    private final UserLoginAttemptService userLoginAttemptService;
    private final SessionRegistry sessionRegistry;
    private final FindByIndexNameSessionRepository<?> sessionRepository;
    private final PasswordEncoder passwordEncoder;

    // ── Token ──────────────────────────────────────────────

    @Override
    public Page<Authorization> findAllTokens(Pageable pageable) {
        return tokenService.findTokensOrderIdDesc(pageable);
    }

    @Override
    @Transactional
    public void revokeToken(Long tokenId) {
        tokenService.findById(String.valueOf(tokenId)).ifPresent(tokenService::remove);
    }

    @Override
    @Transactional
    public long revokeAllExpiredTokens() {
        return tokenService.revokeExpiredTokens();
    }

    // ── Client ─────────────────────────────────────────────

    @Override
    public Page<Client> findAllClients(Pageable pageable) {
        return clientService.findAll(pageable);
    }

    @Override
    public Page<Client> searchClients(String clientId, Pageable pageable) {
        return clientService.searchByClientId(clientId, pageable);
    }

    @Override
    public ClientUpdateFormData getClientUpdateFormData(String clientId) {
        Client client = clientService.findByClientId(clientId)
                .orElseThrow(() -> new IllegalArgumentException(CLIENT_NOT_FOUND_WITH_ID + clientId));
        return buildClientFormModel(ClientFormData.fromEntity(client));
    }

    @Override
    public ClientUpdateFormData buildClientFormModel(ClientFormData clientForm) {
        List<String> grantTypes = GrantType.getAllTypes();
        List<String> authorities = AuthorityType.getAllTypes();

        if (StringUtils.isNotBlank(clientForm.authorizationGrantTypes())) {
            for (String grant : clientForm.authorizationGrantTypes().split(",")) {
                grantTypes.remove(grant.trim());
            }
        }

        if (StringUtils.isNotBlank(clientForm.authorities())) {
            for (String auth : clientForm.authorities().split(",")) {
                authorities.remove(auth.trim());
            }
        }

        return new ClientUpdateFormData(clientForm, grantTypes, authorities);
    }

    @Override
    public String saveClient(ClientFormData clientForm) {
        if (StringUtils.isNotBlank(clientForm.clientId()) && clientService.existsByClientId(clientForm.clientId())) {
            return ErrorMessageConstants.CLIENT_ID_ALREADY_EXISTS + clientForm.clientId();
        }
        if (StringUtils.isBlank(clientForm.clientSecret()) || clientForm.clientSecret().length() != CLIENT_SECRET_LENGTH) {
            return String.format(ErrorMessageConstants.CLIENT_SECRET_LENGTH_INVALID, CLIENT_SECRET_LENGTH);
        }

        Client client = convertToEntity(clientForm);
        clientService.save(client);
        return null;
    }

    @Override
    public String updateClient(String clientId, ClientFormData clientForm) {
        if (StringUtils.isNotBlank(clientForm.clientSecret()) && clientForm.clientSecret().length() != CLIENT_SECRET_LENGTH) {
            return String.format(ErrorMessageConstants.CLIENT_SECRET_LENGTH_INVALID, CLIENT_SECRET_LENGTH);
        }
        Client existingClient = clientService.findByClientId(clientId)
                .orElseThrow(() -> new IllegalArgumentException(CLIENT_NOT_FOUND_WITH_ID + clientId));

        Client updatedClient = convertToEntity(clientForm);
        updatedClient.setId(existingClient.getId());
        clientService.update(existingClient, updatedClient);
        return null;
    }

    @Override
    public void deleteClient(String clientId) {
        Client client = clientService.findByClientId(clientId)
                .orElseThrow(() -> new IllegalArgumentException(CLIENT_NOT_FOUND_WITH_ID + clientId));
        clientService.delete(client);
    }

    @Override
    public String generateSecret() {
        return SecurityUtils.generateRandomHex(CLIENT_SECRET_LENGTH);
    }

    // ── User ───────────────────────────────────────────────

    @Override
    public Page<SecurityUser> findAllUsers(Pageable pageable, boolean showAll) {
        return showAll ? userService.findAll(pageable) : userService.findAllByEnabledTrue(pageable);
    }

    @Override
    public Page<SecurityUser> searchUsers(String firstName, String lastName, Pageable pageable, boolean showAll) {
        return showAll
                ? userService.findByNameAndLastName(firstName, lastName, pageable)
                : userService.findByNameAndLastNameAndEnabledTrue(firstName, lastName, pageable);
    }

    @Override
    public UserFormData getUserUpdateForm(Long userId) {
        return UserFormData.fromEntity(userService.findById(userId));
    }

    @Override
    public String saveUser(UserFormData userForm) {
        if (StringUtils.isNotBlank(userForm.username()) && userService.existsByUsername(userForm.username())) {
            return ErrorMessageConstants.USERNAME_ALREADY_EXISTS + userForm.username();
        }
        SecurityUser user = new SecurityUser();
        applyUserForm(user, userForm);
        userService.save(user);
        return null;
    }

    @Override
    public void updateUser(Long userId, UserFormData userForm) {
        SecurityUser existingUser = userService.findById(userId);

        SecurityUser updatedUser = new SecurityUser();
        updatedUser.setId(existingUser.getId());
        updatedUser.setPassword(existingUser.getPassword());
        applyUserForm(updatedUser, userForm);

        userService.update(existingUser, updatedUser);
    }

    @Override
    public void deleteUser(Long userId) {
        SecurityUser user = userService.findById(userId);
        userService.delete(user);
    }

    // ── Login Attempts ──────────────────────────────────────

    @Override
    public Page<UserLoginAttempt> findAllLoginAttempts(Pageable pageable) {
        return userLoginAttemptService.findAllOrderByLoginDateDesc(pageable);
    }

    @Override
    public Page<UserLoginAttempt> searchLoginAttempts(Long userId, Pageable pageable) {
        return userLoginAttemptService.searchByUserId(userId, pageable);
    }

    // ── Session ────────────────────────────────────────────

    @Override
    public Map<SecurityUser, List<SessionInformation>> getActiveUserSessions() {
        Map<SecurityUser, List<SessionInformation>> userSessionMap = new HashMap<>();

        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof SecurityUser user) {
                List<SessionInformation> activeSessions = sessionRegistry.getAllSessions(principal, false);
                if (!CollectionUtils.isEmpty(activeSessions)) {
                    userSessionMap.put(user, activeSessions);
                }
            }
        }
        return userSessionMap;
    }

    @Override
    @Transactional
    public String evictSession(UUID sessionId) {
        SessionInformation sessionInformation = sessionRegistry.getSessionInformation(sessionId.toString());

        if (Objects.nonNull(sessionInformation) && sessionInformation.getPrincipal() instanceof SecurityUser user) {
            tokenService.revokeTokensOfUser(user);
            sessionRepository.deleteById(sessionId.toString());
            sessionRegistry.removeSessionInformation(sessionId.toString());
            return ErrorMessageConstants.SESSION_EVICTED;
        }
        return ErrorMessageConstants.SESSION_NOT_FOUND;
    }

    @Override
    @Transactional
    public void evictAllSessions() {
        sessionRegistry.getAllPrincipals().forEach(principal -> {
            List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
            sessions.forEach(session -> {
                sessionRepository.deleteById(session.getSessionId());
                sessionRegistry.removeSessionInformation(session.getSessionId());
            });
        });
    }

    // ── Private helpers ────────────────────────────────────

    private Client convertToEntity(ClientFormData form) {
        Client client = new Client();
        client.setId(UUID.randomUUID().toString());
        client.setClientId(form.clientId());
        client.setClientSecret(form.clientSecret());
        client.setAuthorizationGrantTypes(form.authorizationGrantTypes());
        client.setRedirectUris(form.redirectUris());
        client.setAuthorities(form.authorities());
        client.setAccessTokenValidity(form.accessTokenValidity());
        client.setRefreshTokenValidity(form.refreshTokenValidity());
        client.setAuthorizationCodeValidity(form.authorizationCodeValidity());
        client.setClientName(form.clientName());
        return client;
    }

    private void applyUserForm(SecurityUser user, UserFormData form) {
        user.setUsername(form.username());
        user.setFirstName(form.firstName());
        user.setLastName(form.lastName());
        user.setEmail(form.email());
        user.setPhoneNumber(form.phoneNumber());
        user.setEnabled(form.enabled());
        user.setAccountNonLocked(form.accountNonLocked());
        if (StringUtils.isNotBlank(form.password())) {
            user.setPassword(passwordEncoder.encode(form.password()));
        }
    }
}
