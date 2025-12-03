package mb.oauth2authorizationserver.config.security.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.ott.DefaultOneTimeToken;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationToken;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class CustomOneTimeTokenServiceImpl implements OneTimeTokenService {

    private final Map<String, OneTimeToken> oneTimeTokens = new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemUTC();

    @NonNull
    @Override
    public OneTimeToken generate(GenerateOneTimeTokenRequest request) {
        String token = UUID.randomUUID().toString();
        Instant expiresAt = this.clock.instant().plus(5, ChronoUnit.MINUTES);

        OneTimeToken oneTimeToken = new DefaultOneTimeToken(token, request.getUsername(), expiresAt);
        oneTimeTokens.put(token, oneTimeToken);

        return oneTimeToken;
    }

    @Nullable
    @Override
    public OneTimeToken consume(OneTimeTokenAuthenticationToken authenticationToken) {
        log.info("Consuming one-time token: {}", authenticationToken.getTokenValue());
        OneTimeToken oneTimeToken = oneTimeTokens.remove(authenticationToken.getTokenValue());
        if (oneTimeToken == null || isExpired(oneTimeToken)) {
            return null;
        }
        return oneTimeToken;
    }

    private boolean isExpired(OneTimeToken oneTimeToken) {
        return this.clock.instant().isAfter(oneTimeToken.getExpiresAt());
    }
}
