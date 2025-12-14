package mb.oauth2authorizationserver.mapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.security.Principal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomObjectMapper {

    private final ObjectMapper objectMapper;

    public Map<String, Object> parseMap(String data) {
        if (StringUtils.isEmpty(data)) {
            return Map.of();
        }
        try {
            Map<String, Object> rawMap = objectMapper.readValue(data, new TypeReference<>() {
            });

            if (Objects.isNull(rawMap)) {
                return Map.of();
            }

            Map<String, Object> result = new LinkedHashMap<>();

            for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
                if (OAuth2AuthorizationRequest.class.getName().equals(entry.getKey())) {
                    Map<String, Object> authRequestMap = (Map<String, Object>) entry.getValue();

                    OAuth2AuthorizationRequest authRequest = OAuth2AuthorizationRequest.authorizationCode()
                            .authorizationUri((String) authRequestMap.get("authorizationUri"))
                            .clientId((String) authRequestMap.get("clientId"))
                            .redirectUri((String) authRequestMap.get("redirectUri"))
                            .scopes(new HashSet<>((List<String>) authRequestMap.get("scopes")))
                            .state((String) authRequestMap.get("state"))
                            .additionalParameters((Map<String, Object>) authRequestMap.get("additionalParameters"))
                            .build();

                    result.put(entry.getKey(), authRequest);
                } else if (Principal.class.getName().equals(entry.getKey())) {
                    result.put(entry.getKey(), new UsernamePasswordAuthenticationToken(((Map<String, Object>) entry.getValue()).get("principal"), null, Collections.emptyList()));
                } else if ("metadata.token.claims".equals(entry.getKey())) {
                    convertStringToInstantAndUpdateClaims(entry, result);
                } else {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
            return result;
        } catch (Exception ex) {
            log.error("Exception occurred while parsing JSON. parseMap - Exception: {}", ExceptionUtils.getStackTrace(ex));
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    public Map<String, Object> defaultParseMap(String data) {
        if (StringUtils.isEmpty(data)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(data, new TypeReference<>() {
            });
        } catch (Exception ex) {
            log.error("Exception occurred while parsing JSON. defaultParseMap - Exception: {}", ExceptionUtils.getStackTrace(ex));
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    public String writeMap(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception ex) {
            log.error("Exception occurred while writing JSON. writeMap - Exception: {}", ExceptionUtils.getStackTrace(ex));
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private void convertStringToInstantAndUpdateClaims(Map.Entry<String, Object> entry, Map<String, Object> result) {
        Map<String, Object> claims = (Map<String, Object>) entry.getValue();
        if (claims.containsKey("nbf")) {
            String nbfStr = (String) claims.get("nbf");
            try {
                Instant nbfInstant = Instant.parse(nbfStr);
                claims.put("nbf", nbfInstant);
            } catch (DateTimeParseException e) {
                log.error("Exception occurred while parsing Date. convertStringToInstantAndUpdateClaims - Exception: {}", ExceptionUtils.getStackTrace(e));
            }
            result.put(entry.getKey(), claims);
        }
    }
}
