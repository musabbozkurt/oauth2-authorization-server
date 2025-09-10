package mb.oauth2authorizationserver.mapper;

import mb.oauth2authorizationserver.config.RedisTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;

import java.security.Principal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = RedisTestConfiguration.class)
class CustomObjectMapperIntegrationTest {

    @Autowired
    private CustomObjectMapper customObjectMapper;

    @Test
    void parseMap_WithEmptyString_ShouldReturnEmptyMap() {
        // Arrange
        // Act
        Map<String, Object> result = customObjectMapper.parseMap("");

        // Assertions
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void parseMap_WithNullString_ShouldReturnEmptyMap() {
        // Arrange
        // Act
        Map<String, Object> result = customObjectMapper.parseMap(null);

        // Assertions
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void parseMap_WithSimpleJson_ShouldReturnCorrectMap() {
        // Arrange
        String json = "{\"name\":\"test\",\"value\":123}";

        // Act
        Map<String, Object> result = customObjectMapper.parseMap(json);

        // Assertions
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("test", result.get("name"));
        assertEquals(123, result.get("value"));
    }

    @Test
    void parseMap_WithNestedJson_ShouldReturnCorrectMap() {
        // Arrange
        String json = "{\"outer\":{\"inner\":\"value\"}}";

        // Act
        Map<String, Object> result = customObjectMapper.parseMap(json);

        // Assertions
        assertNotNull(result);
        assertInstanceOf(Map.class, result.get("outer"));
        assertEquals("value", ((Map<?, ?>) result.get("outer")).get("inner"));
    }

    @Test
    void parseMap_WithInvalidJson_ShouldThrowIllegalArgumentException() {
        // Arrange
        String invalidJson = "{invalid:json}";

        // Act
        // Assertions
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> customObjectMapper.parseMap(invalidJson));
        assertTrue(exception.getMessage().contains("Unexpected character"));
    }

    @Test
    void parseMap_ShouldMapOAuth2AuthorizationRequest_WhenValidDataIsProvided() {
        // Arrange
        String data = """
                {
                  "%s": {
                    "authorizationUri": "http://auth.com",
                    "clientId": "clientId",
                    "redirectUri": "http://redirect.com",
                    "scopes": [
                      "scope1",
                      "scope2"
                    ],
                    "state": "stateValue",
                    "additionalParameters": {}
                  }
                }
                """.formatted(OAuth2AuthorizationRequest.class.getName());

        // Act
        Map<String, Object> result = customObjectMapper.parseMap(data);

        // Assertions
        assertTrue(result.containsKey(OAuth2AuthorizationRequest.class.getName()), "Expected OAuth2AuthorizationRequest to be mapped");
        assertNotNull(result.get(OAuth2AuthorizationRequest.class.getName()), "Expected OAuth2AuthorizationRequest to be non-null");

        OAuth2AuthorizationRequest authRequest = (OAuth2AuthorizationRequest) result.get(OAuth2AuthorizationRequest.class.getName());
        assertEquals("http://auth.com", authRequest.getAuthorizationUri());
        assertEquals("clientId", authRequest.getClientId());
        assertEquals("http://redirect.com", authRequest.getRedirectUri());
        assertTrue(authRequest.getScopes().contains("scope1"));
        assertTrue(authRequest.getScopes().contains("scope2"));
        assertEquals("stateValue", authRequest.getState());
    }

    @Test
    void parseMap_ShouldHandlePrincipalMapping_WhenPrincipalDataIsProvided() {
        // Arrange
        String data = """
                {
                  "%s": {
                    "principal": "user"
                  }
                }
                """.formatted(Principal.class.getName());

        // Act
        Map<String, Object> result = customObjectMapper.parseMap(data);

        // Assertions
        assertTrue(result.containsKey(Principal.class.getName()), "Expected Principal to be mapped");
        assertInstanceOf(UsernamePasswordAuthenticationToken.class, result.get(Principal.class.getName()), "Expected mapped value to be of type UsernamePasswordAuthenticationToken");

        Authentication auth = (UsernamePasswordAuthenticationToken) result.get(Principal.class.getName());
        assertEquals("user", auth.getPrincipal(), "Expected principal to match the provided user");
    }

    @Test
    void roundTrip_WithComplexObject_ShouldMaintainDataIntegrity() {
        // Arrange
        Map<String, Object> originalMap = new HashMap<>();
        originalMap.put("string", "value");
        originalMap.put("number", 123);
        originalMap.put("boolean", true);
        originalMap.put("duration", Duration.ofMinutes(15));
        originalMap.put("algorithm", SignatureAlgorithm.RS256);
        originalMap.put("tokenFormat", OAuth2TokenFormat.SELF_CONTAINED);

        // Act
        String json = customObjectMapper.writeMap(originalMap);
        Map<String, Object> resultMap = customObjectMapper.parseMap(json);

        // Assertions
        assertEquals(originalMap.size(), resultMap.size());
        assertEquals(originalMap.get("string"), resultMap.get("string"));
        assertEquals(originalMap.get("number"), resultMap.get("number"));
        assertEquals(originalMap.get("boolean"), resultMap.get("boolean"));
        assertEquals(originalMap.get("duration").toString(), resultMap.get("duration").toString());
        assertEquals(originalMap.get("algorithm").toString(), resultMap.get("algorithm").toString());
        assertTrue(resultMap.get("tokenFormat").toString().contains(OAuth2TokenFormat.SELF_CONTAINED.getValue()));
    }

    @Test
    void defaultParseMap_WithEmptyString_ShouldReturnEmptyMap() {
        // Arrange
        // Act
        Map<String, Object> result = customObjectMapper.defaultParseMap("");

        // Assertions
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void defaultParseMap_WithNullString_ShouldReturnEmptyMap() {
        // Arrange
        // Act
        Map<String, Object> result = customObjectMapper.defaultParseMap(null);

        // Assertions
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void defaultParseMap_WithSimpleJson_ShouldReturnCorrectMap() {
        // Arrange
        String json = "{\"name\":\"test\",\"value\":123}";

        // Act
        Map<String, Object> result = customObjectMapper.defaultParseMap(json);

        // Assertions
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("test", result.get("name"));
        assertEquals(123, result.get("value"));
    }

    @Test
    void defaultParseMap_WithNestedJson_ShouldReturnCorrectMap() {
        // Arrange
        String json = "{\"outer\":{\"inner\":\"value\"}}";

        // Act
        Map<String, Object> result = customObjectMapper.defaultParseMap(json);

        // Assertions
        assertNotNull(result);
        assertInstanceOf(Map.class, result.get("outer"));
        assertEquals("value", ((Map<?, ?>) result.get("outer")).get("inner"));
    }

    @Test
    void defaultParseMap_WithInvalidJson_ShouldThrowIllegalArgumentException() {
        // Arrange
        String invalidJson = "{invalid:json}";

        // Act
        // Assertions
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> customObjectMapper.defaultParseMap(invalidJson));
        assertTrue(exception.getMessage().contains("Unexpected character"));
    }

    @Test
    void defaultParseMap_ShouldMapOAuth2AuthorizationRequest_WhenValidDataIsProvided() {
        // Arrange
        String data = """
                {
                  "%s": {
                    "authorizationUri": "http://auth.com",
                    "clientId": "clientId",
                    "redirectUri": "http://redirect.com",
                    "scopes": [
                      "scope1",
                      "scope2"
                    ],
                    "state": "stateValue",
                    "additionalParameters": {}
                  }
                }
                """.formatted(OAuth2AuthorizationRequest.class.getName());

        // Act
        Map<String, Object> result = customObjectMapper.defaultParseMap(data);

        // Assertions
        assertTrue(result.containsKey(OAuth2AuthorizationRequest.class.getName()), "Expected OAuth2AuthorizationRequest to be mapped");
        assertNotNull(result.get(OAuth2AuthorizationRequest.class.getName()), "Expected OAuth2AuthorizationRequest to be non-null");

        LinkedHashMap<String, Object> authRequest = (LinkedHashMap<String, Object>) result.get(OAuth2AuthorizationRequest.class.getName());
        ArrayList<String> scopes = (ArrayList<String>) authRequest.get("scopes");

        assertEquals("http://auth.com", authRequest.get("authorizationUri"));
        assertEquals("clientId", authRequest.get("clientId"));
        assertEquals("http://redirect.com", authRequest.get("redirectUri"));
        assertTrue((scopes).contains("scope1"));
        assertTrue(scopes.contains("scope2"));
        assertEquals("stateValue", authRequest.get("state"));
    }

    @Test
    void defaultParseMap_ShouldHandlePrincipalMapping_WhenPrincipalDataIsProvided() {
        // Arrange
        String data = """
                {
                  "%s": {
                    "principal": "user"
                  }
                }
                """.formatted(Principal.class.getName());

        // Act
        Map<String, Object> result = customObjectMapper.defaultParseMap(data);

        // Assertions
        assertTrue(result.containsKey(Principal.class.getName()), "Expected Principal to be mapped");
        assertInstanceOf(LinkedHashMap.class, result.get(Principal.class.getName()), "Expected mapped value to be of type LinkedHashMap");

        LinkedHashMap<String, Object> auth = (LinkedHashMap<String, Object>) result.get(Principal.class.getName());
        assertEquals("user", auth.get("principal"), "Expected principal to match the provided user");
    }

    @Test
    void defaultParseMapRoundTrip_WithComplexObject_ShouldMaintainDataIntegrity() {
        // Arrange
        Map<String, Object> originalMap = new HashMap<>();
        originalMap.put("string", "value");
        originalMap.put("number", 123);
        originalMap.put("boolean", true);
        originalMap.put("duration", Duration.ofMinutes(15));
        originalMap.put("algorithm", SignatureAlgorithm.RS256);
        originalMap.put("tokenFormat", OAuth2TokenFormat.SELF_CONTAINED);

        // Act
        String json = customObjectMapper.writeMap(originalMap);
        Map<String, Object> resultMap = customObjectMapper.defaultParseMap(json);

        // Assertions
        assertEquals(originalMap.size(), resultMap.size());
        assertEquals(originalMap.get("string"), resultMap.get("string"));
        assertEquals(originalMap.get("number"), resultMap.get("number"));
        assertEquals(originalMap.get("boolean"), resultMap.get("boolean"));
        assertEquals(originalMap.get("duration").toString(), resultMap.get("duration").toString());
        assertEquals(originalMap.get("algorithm").toString(), resultMap.get("algorithm").toString());
        assertTrue(resultMap.get("tokenFormat").toString().contains(OAuth2TokenFormat.SELF_CONTAINED.getValue()));
    }

    @Test
    void writeMap_WithSimpleMap_ShouldReturnCorrectJson() {
        // Arrange
        Map<String, Object> map = Map.of("name", "test", "value", 123);

        // Act
        String result = customObjectMapper.writeMap(map);

        // Assertions
        assertTrue(result.contains("\"name\":\"test\""));
        assertTrue(result.contains("\"value\":123"));
    }

    @Test
    void writeMap_WithNestedMap_ShouldReturnCorrectJson() {
        // Arrange
        Map<String, Object> innerMap = Map.of("inner", "value");
        Map<String, Object> outerMap = Map.of("outer", innerMap);

        // Act
        String result = customObjectMapper.writeMap(outerMap);

        // Assertions
        assertTrue(result.contains("\"outer\":{\"inner\":\"value\"}"));
    }

    @Test
    void writeMap_WithTokenSettings_ShouldWriteCorrectly() {
        // Arrange
        Map<String, Object> settings = new HashMap<>();
        settings.put("settings.token.reuse-refresh-tokens", true);
        settings.put("settings.token.access-token-time-to-live", Duration.ofSeconds(900));
        settings.put("settings.token.id-token-signature-algorithm", SignatureAlgorithm.RS256);
        settings.put("settings.token.access-token-format", OAuth2TokenFormat.SELF_CONTAINED);

        // Act
        String result = customObjectMapper.writeMap(settings);

        // Assertions
        assertNotNull(result);
        assertTrue(result.contains("\"settings.token.reuse-refresh-tokens\":true"));
        assertTrue(result.contains("\"settings.token.access-token-time-to-live\""));
        assertTrue(result.contains("\"settings.token.id-token-signature-algorithm\""));
        assertTrue(result.contains("\"settings.token.access-token-format\""));
    }

    @Test
    void writeMap_WithNullMap_ShouldReturnNullString() {
        // Act
        String result = customObjectMapper.writeMap(null);

        // Assertions
        assertEquals("null", result);
    }

    @Test
    void writeMap_WithUnserializableObject_ShouldThrowIllegalArgumentException() {
        // Arrange
        Map<String, Object> map = Map.of("unserializable", new Object());

        // Act
        // Assertions
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> customObjectMapper.writeMap(map));
        assertTrue(exception.getMessage().contains("No serializer found"));
    }
}
