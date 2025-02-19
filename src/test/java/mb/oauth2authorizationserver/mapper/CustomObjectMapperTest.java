package mb.oauth2authorizationserver.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomObjectMapperTest {

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private CustomObjectMapper customObjectMapper;

    private ObjectMapper realObjectMapper;

    @BeforeEach
    void setUp() {
        realObjectMapper = new ObjectMapper();
    }

    @Test
    void parseMap_ShouldReturnEmptyMap_WhenInputDataIsEmpty() {
        // Arrange
        String data = "";

        // Act
        Map<String, Object> result = customObjectMapper.parseMap(data);

        // Assertions
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void parseMap_ShouldReturnParsedMap_WhenInputDataIsValidJson() throws JsonProcessingException {
        // Arrange
        String data = """
                {
                    "key1":"value1",
                    "key2":2
                }
                """;
        Map<String, Object> expectedMap = Map.of("key1", "value1", "key2", 2);
        when(objectMapper.readValue(eq(data), any(TypeReference.class))).thenReturn(expectedMap);

        // Act
        Map<String, Object> result = customObjectMapper.parseMap(data);

        // Assertions
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("value1", result.get("key1"));
        assertEquals(2, result.get("key2"));
    }

    @Test
    void parseMap_ShouldThrowIllegalArgumentException_WhenInputDataIsInvalidJson() throws JsonProcessingException {
        // Arrange
        String data = "{invalidJson}";
        when(objectMapper.readValue(eq(data), any(TypeReference.class))).thenThrow(new JsonProcessingException("Invalid JSON") {
        });

        // Act
        // Assertions
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> customObjectMapper.parseMap(data));
        assertEquals("Invalid JSON", exception.getMessage());
    }

    @Test
    void defaultParseMap_ShouldReturnEmptyMap_WhenInputDataIsEmpty() {
        // Arrange
        String data = "";

        // Act
        Map<String, Object> result = customObjectMapper.defaultParseMap(data);

        // Assertions
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void defaultParseMap_ShouldThrowIllegalArgumentException_WhenExceptionOccursDuringParsing() throws JsonProcessingException {
        // Arrange
        String invalidData = "{ invalidJson }";
        when(objectMapper.readValue(eq(invalidData), any(TypeReference.class))).thenThrow(new JsonProcessingException("Invalid JSON") {
        });

        // Act
        // Assertions
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> customObjectMapper.defaultParseMap(invalidData));
        assertEquals("Invalid JSON", exception.getMessage());
    }

    @Test
    void parseMap_ShouldMapOAuth2AuthorizationRequest_WhenValidDataIsProvided() throws Exception {
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

        Map<String, Object> rawMap = new HashMap<>();
        rawMap.put(OAuth2AuthorizationRequest.class.getName(), Map.of(
                "authorizationUri", "http://auth.com",
                "clientId", "clientId",
                "redirectUri", "http://redirect.com",
                "scopes", List.of("scope1", "scope2"),
                "state", "stateValue",
                "additionalParameters", new HashMap<>()
        ));

        when(objectMapper.readValue(eq(data), any(TypeReference.class))).thenReturn(rawMap);

        // Act
        Map<String, Object> result = customObjectMapper.parseMap(data);

        // Assertions
        assertTrue(result.containsKey(OAuth2AuthorizationRequest.class.getName()), "Expected OAuth2AuthorizationRequest to be mapped");
        assertNotNull(result.get(OAuth2AuthorizationRequest.class.getName()), "Expected OAuth2AuthorizationRequest to be non-null");
    }

    @Test
    void parseMap_ShouldHandlePrincipalMapping_WhenPrincipalDataIsProvided() throws Exception {
        // Arrange
        String data = """
                {
                  "%s": {
                    "principal": "user"
                  }
                }
                """.formatted(Principal.class.getName());

        Map<String, Object> rawMap = new HashMap<>();
        rawMap.put(Principal.class.getName(), Map.of("principal", "user"));

        when(objectMapper.readValue(eq(data), any(TypeReference.class))).thenReturn(rawMap);

        // Act
        Map<String, Object> result = customObjectMapper.parseMap(data);

        // Assertions
        assertTrue(result.containsKey(Principal.class.getName()), "Expected Principal to be mapped");
        assertInstanceOf(UsernamePasswordAuthenticationToken.class, result.get(Principal.class.getName()), "Expected mapped value to be of type UsernamePasswordAuthenticationToken");
    }

    @Test
    void defaultParseMap_ShouldReturnMap_WhenValidDataIsProvided() throws JsonProcessingException {
        // Arrange
        String data = """
                {
                    "key1": "value1",
                    "key2": "value2"
                }
                """;
        Map<String, Object> expectedMap = Map.of("key1", "value1", "key2", "value2");
        when(objectMapper.readValue(eq(data), any(TypeReference.class))).thenReturn(expectedMap);

        // Act
        Map<String, Object> result = customObjectMapper.defaultParseMap(data);

        // Assertions
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("value1", result.get("key1"));
        assertEquals("value2", result.get("key2"));
    }

    @Test
    void writeMap_ShouldReturnJsonString_WhenInputMapIsValid() throws JsonProcessingException {
        // Arrange
        Map<String, Object> data = Map.of("key1", "value1");
        String expectedJson = """
                {
                    "key1":"value1"
                }
                """;
        when(objectMapper.writeValueAsString(data)).thenReturn(expectedJson);

        // Act
        String result = customObjectMapper.writeMap(data);

        // Assertions
        assertNotNull(result);
        assertEquals(expectedJson, result);
    }

    @Test
    void writeMap_ShouldReturnJsonString_WhenInputMapIsValidAndHasMultipleRecords() throws JsonProcessingException {
        // Arrange
        Map<String, Object> data = Map.of("key1", "value1", "key2", 2);
        String expectedJson = """
                {
                    "key1":"value1",
                    "key2":2
                }
                """;
        when(objectMapper.writeValueAsString(data)).thenReturn(expectedJson);

        // Act
        String result = customObjectMapper.writeMap(data);

        // Assertions
        assertNotNull(result);

        Map<String, Object> expectedMap = realObjectMapper.readValue(expectedJson, new TypeReference<>() {
        });
        Map<String, Object> resultMap = realObjectMapper.readValue(result, new TypeReference<>() {
        });

        assertThat(resultMap).isEqualTo(expectedMap);
    }

    @Test
    void writeMap_ShouldReturnNullString_WhenInputMapIsNull() throws JsonProcessingException {
        // Arrange
        Map<String, Object> data = null;
        when(objectMapper.writeValueAsString(null)).thenReturn("null");

        // Act
        String result = customObjectMapper.writeMap(data);

        // Assertions
        assertEquals("null", result);
    }

    @Test
    void writeMap_ShouldThrowIllegalArgumentException_WhenObjectMapperFails() throws JsonProcessingException {
        // Arrange
        Map<String, Object> data = Map.of("key", new Object());
        when(objectMapper.writeValueAsString(data)).thenThrow(new JsonProcessingException("Serialization error") {
        });

        // Act
        // Assertions
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> customObjectMapper.writeMap(data));
        assertEquals("Serialization error", exception.getMessage());
    }
}
