package mb.oauth2authorizationserver.config.security.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.config.security.service.TokenService;
import mb.oauth2authorizationserver.data.entity.Authorization;
import mb.oauth2authorizationserver.data.entity.SecurityUser;
import mb.oauth2authorizationserver.data.repository.AuthorizationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private final AuthorizationRepository authorizationRepository;

    @Override
    public Page<Authorization> findTokensOrderIdDesc(Pageable pageable) {
        return authorizationRepository.findAllByOrderByAccessTokenIssuedAtDesc(pageable);
    }

    @Override
    @Transactional
    public void revokeTokensOfUser(SecurityUser user) {
        List<Authorization> tokens = authorizationRepository.findByPrincipalName(user.getUsername());
        authorizationRepository.deleteAll(tokens);
    }

    @Override
    @Transactional
    public long revokeExpiredTokens() {
        return authorizationRepository.deleteExpiredTokens(Instant.now());
    }

    @Override
    @Transactional
    public long revokeAllTokens() {
        long count = authorizationRepository.count();
        authorizationRepository.deleteAll();
        return count;
    }

    @Override
    public void save(Authorization authorization) {
        authorizationRepository.save(authorization);
    }

    @Override
    public Optional<Authorization> findById(String id) {
        return authorizationRepository.findById(id);
    }

    @Override
    public void remove(Authorization entity) {
        authorizationRepository.delete(entity);
    }

    @Override
    public Optional<Authorization> findByClientIdAndUsernameAndAuthorizationGrantType(String clientId, String username, String authorizationGrantType) {
        return authorizationRepository.findByRegisteredClientIdAndPrincipalNameAndAuthorizationGrantType(clientId, username, authorizationGrantType);
    }
}
