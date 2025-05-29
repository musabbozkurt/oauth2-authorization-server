package mb.oauth2authorizationserver.utils;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.servlet.http.HttpServletRequest;
import mb.oauth2authorizationserver.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.util.MultiValueMap;

import java.io.File;
import java.nio.file.Path;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityUtilsTest {

    @Test
    void loadOrGenerateRsa_ShouldGenerateAndSaveNewKey_WhenFileDoesNotExist(@TempDir Path tempDir) throws JOSEException {
        // Arrange
        String keyPath = tempDir.resolve("new-key.dat").toString();

        // Act
        RSAKey rsaKey = SecurityUtils.loadOrGenerateRsa(keyPath);

        // Assert
        assertNotNull(rsaKey);
        assertInstanceOf(RSAPublicKey.class, rsaKey.toRSAPublicKey());
        assertInstanceOf(RSAPrivateKey.class, rsaKey.toRSAPrivateKey());
        assertTrue(new File(keyPath).exists());

        // Verify we can load the saved key
        RSAKey loadedKey = SecurityUtils.loadOrGenerateRsa(keyPath);
        assertNotNull(loadedKey);
    }

    @Test
    void loadOrGenerateRsa_ShouldLoadExistingKey_WhenFileExists(@TempDir Path tempDir) throws JOSEException {
        // Arrange
        String keyPath = tempDir.resolve("existing-key.dat").toString();

        // First create a key file
        RSAKey firstKey = SecurityUtils.loadOrGenerateRsa(keyPath);

        // Act
        RSAKey loadedKey = SecurityUtils.loadOrGenerateRsa(keyPath);

        // Assertions
        assertNotNull(loadedKey);
        assertNotNull(loadedKey.getKeyID());
        assertEquals(firstKey.toRSAPublicKey().getModulus(), loadedKey.toRSAPublicKey().getModulus());
    }

    @Test
    void loadOrGenerateRsa_ShouldThrowException_WhenFileCannotBeRead(@TempDir Path tempDir) {
        // Arrange
        String keyPath = tempDir.resolve("directory-not-file").toString();
        File keyFile = new File(keyPath);

        // Act
        // Assertions
        assertTrue(keyFile.mkdir());
        assertThrows(BaseException.class, () -> SecurityUtils.loadOrGenerateRsa(keyPath));
    }

    @Test
    void getParameters_ShouldReturnEmptyMap_WhenNoParameters() {
        // Arrange
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getParameterMap()).thenReturn(Collections.emptyMap());

        // Act
        MultiValueMap<String, String> result = SecurityUtils.getParameters(request);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getParameters_ShouldConvertParameters_WhenRequestHasParameters() {
        // Arrange
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Map<String, String[]> parameterMap = new HashMap<>();
        parameterMap.put("single", new String[]{"value"});
        parameterMap.put("multiple", new String[]{"value1", "value2"});

        when(request.getParameterMap()).thenReturn(parameterMap);

        // Act
        MultiValueMap<String, String> result = SecurityUtils.getParameters(request);

        // Assertions
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(1, result.get("single").size());
        assertEquals("value", result.getFirst("single"));
        assertEquals(2, result.get("multiple").size());
        assertEquals("value1", result.get("multiple").get(0));
        assertEquals("value2", result.get("multiple").get(1));
    }

    @Test
    void getAuthenticatedClientElseThrowInvalidClient_ShouldReturnToken_WhenClientIsAuthenticated() {
        // Arrange
        OAuth2ClientAuthenticationToken clientToken = mock(OAuth2ClientAuthenticationToken.class);
        when(clientToken.isAuthenticated()).thenReturn(true);

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(clientToken);

        // Act
        OAuth2ClientAuthenticationToken result = SecurityUtils.getAuthenticatedClientElseThrowInvalidClient(authentication);

        // Assertions
        assertSame(clientToken, result);
    }

    @Test
    void getAuthenticatedClientElseThrowInvalidClient_ShouldThrowException_WhenClientNotAuthenticated() {
        // Arrange
        OAuth2ClientAuthenticationToken clientToken = mock(OAuth2ClientAuthenticationToken.class);
        when(clientToken.isAuthenticated()).thenReturn(false);

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(clientToken);

        // Act
        // Assertions
        assertThrows(OAuth2AuthenticationException.class, () -> SecurityUtils.getAuthenticatedClientElseThrowInvalidClient(authentication));
    }

    @Test
    void getAuthenticatedClientElseThrowInvalidClient_ShouldThrowException_WhenPrincipalNotOAuthToken() {
        // Arrange
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn("not a token");

        // Act
        // Assertions
        assertThrows(OAuth2AuthenticationException.class, () -> SecurityUtils.getAuthenticatedClientElseThrowInvalidClient(authentication));
    }
}
