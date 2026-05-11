package mb.oauth2authorizationserver.config.security.service.impl;

import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.config.security.builder.AuthorizationBuilderService;
import mb.oauth2authorizationserver.constants.ServiceConstants;
import mb.oauth2authorizationserver.data.entity.Authorization;
import mb.oauth2authorizationserver.data.repository.AuthorizationRepository;
import org.jspecify.annotations.Nullable;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OAuth2AuthorizationServiceImpl implements OAuth2AuthorizationService {

    private final AuthorizationRepository authorizationRepository;
    private final AuthorizationBuilderService authorizationBuilderService;
    private final RedissonClient redissonClient;

    public OAuth2AuthorizationServiceImpl(AuthorizationRepository authorizationRepository, AuthorizationBuilderService authorizationBuilderService, RedissonClient redissonClient) {
        Assert.notNull(authorizationRepository, "authorizationRepository cannot be null");
        this.authorizationRepository = authorizationRepository;
        this.authorizationBuilderService = authorizationBuilderService;
        this.redissonClient = redissonClient;
    }

    @Override
    @Transactional
    public void save(OAuth2Authorization authorization) {
        Assert.notNull(authorization, "authorization cannot be null");

        Authorization newEntity = authorizationBuilderService.toEntity(authorization);
        AuthorizationGrantType authorizationGrantType = authorization.getAuthorizationGrantType();

        // Acquire distributed lock to prevent duplicate tokens during concurrent requests
        String lockKey = String.format(ServiceConstants.AUTHORIZATION_LOCK, authorization.getRegisteredClientId(), newEntity.getPrincipalName(), authorizationGrantType.getValue());

        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                log.warn("Could not acquire distributed lock within timeout, skipping token save to prevent duplicate. clientId: {}, principalName: {}, grantType: {}", authorization.getRegisteredClientId(), newEntity.getPrincipalName(), authorizationGrantType.getValue());
                return;
            }

            Optional<Authorization> optionalExistingAuthorization = AuthorizationGrantType.AUTHORIZATION_CODE.equals(authorizationGrantType)
                    ? authorizationRepository.findByAuthorizationCodeValue(newEntity.getAuthorizationCodeValue())
                    : authorizationRepository.findByRegisteredClientIdAndPrincipalNameAndAuthorizationGrantType(authorization.getRegisteredClientId(), newEntity.getPrincipalName(), authorizationGrantType.getValue());

            optionalExistingAuthorization.ifPresentOrElse(existingAuthorization -> {
                        newEntity.setId(existingAuthorization.getId());
                        authorizationRepository.save(newEntity);
                    },
                    () -> authorizationRepository.save(newEntity)
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while acquiring distributed lock. lockKey: {}", lockKey, e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        Assert.notNull(authorization, "authorization cannot be null");
        this.authorizationRepository.deleteById(authorization.getId());
    }

    @Nullable
    @Override
    public OAuth2Authorization findById(String id) {
        Assert.hasText(id, "id cannot be empty");
        return this.authorizationRepository.findById(id).map(authorizationBuilderService::toObject).orElse(null);
    }

    @Nullable
    @Override
    @Transactional
    public OAuth2Authorization findByToken(String token, @Nullable OAuth2TokenType tokenType) {
        Assert.hasText(token, "token cannot be empty");

        Optional<Authorization> result;

        if (Objects.isNull(tokenType)) {
            result = this.authorizationRepository.findByAccessTokenValue(token).or(() -> authorizationRepository.findByStateOrAuthorizationCodeValueOrAccessTokenValueOrRefreshTokenValue(token));
        } else if (OAuth2ParameterNames.STATE.equals(tokenType.getValue())) {
            result = this.authorizationRepository.findByState(token);
        } else if (OAuth2ParameterNames.CODE.equals(tokenType.getValue())) {
            result = this.authorizationRepository.findByAuthorizationCodeValue(token);
        } else if (OAuth2ParameterNames.ACCESS_TOKEN.equals(tokenType.getValue())) {
            result = this.authorizationRepository.findByAccessTokenValue(token);
        } else if (OAuth2ParameterNames.REFRESH_TOKEN.equals(tokenType.getValue())) {
            result = this.authorizationRepository.findByRefreshTokenValue(token);
        } else {
            result = Optional.empty();
        }

        return result.map(authorization -> {
            boolean isRefreshTokenLookup = tokenType != null && OAuth2ParameterNames.REFRESH_TOKEN.equals(tokenType.getValue());
            if (isRefreshTokenLookup) {
                if (authorization.isRefreshTokenExpired()) {
                    log.debug("Found expired refresh token for user '{}', removing from database", authorization.getPrincipalName());
                    authorizationRepository.delete(authorization);
                    return null;
                }
            } else if (authorization.isExpired()) {
                log.debug("Found expired token for user '{}', removing from database", authorization.getPrincipalName());
                authorizationRepository.delete(authorization);
                return null;
            }
            return authorizationBuilderService.toObject(authorization);
        }).orElse(null);
    }
}
