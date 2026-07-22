package mb.oauth2authorizationserver.config.security.converter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class LegacyOAuth2TokenEndpointAuthenticationConverterTest {

    private static final String REDIRECT_URI = "http://localhost/callback";
    private static final String AUTHORIZATION_CODE = "auth-code";
    private static final String REFRESH_TOKEN = "refresh-token-value";
    private static final String SCOPES = "read write";
    private static final String CLIENT_ID = "test-client-id";
    private static final String SECRET_ID = "test-client-secret";

    private final LegacyOAuth2TokenEndpointAuthenticationConverter converter = new LegacyOAuth2TokenEndpointAuthenticationConverter();

    @BeforeEach
    void setUp() {
        RegisteredClient registeredClient = RegisteredClient.withId("test-client-id")
                .clientId(CLIENT_ID)
                .clientSecret(SECRET_ID)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(REDIRECT_URI)
                .scope("read")
                .scope("write")
                .build();

        OAuth2ClientAuthenticationToken clientPrincipal = new OAuth2ClientAuthenticationToken(registeredClient, ClientAuthenticationMethod.CLIENT_SECRET_BASIC, SECRET_ID);
        SecurityContextHolder.getContext().setAuthentication(clientPrincipal);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void convert_ShouldReturnToken_WhenAuthorizationCodeParamsAreInRequestBody() {
        // Arrange
        MockHttpServletRequest request = authorizationCodeRequest();
        addAuthorizationCodeParameters(request);

        // Act
        OAuth2AuthorizationCodeAuthenticationToken token = assertAuthorizationCodeToken(converter.convert(request));

        // Assertions
        assertEquals(AUTHORIZATION_CODE, token.getCode());
        assertEquals(REDIRECT_URI, token.getRedirectUri());
    }

    @Test
    void convert_ShouldReturnToken_WhenAuthorizationCodeParamsAreInQueryString() {
        // Arrange
        MockHttpServletRequest request = authorizationCodeRequest();
        request.setQueryString("grant_type=authorization_code&code=%s&redirect_uri=%s"
                .formatted(AUTHORIZATION_CODE, REDIRECT_URI));
        addAuthorizationCodeParameters(request);

        // Act
        OAuth2AuthorizationCodeAuthenticationToken token = assertAuthorizationCodeToken(converter.convert(request));

        // Assertions
        assertEquals(AUTHORIZATION_CODE, token.getCode());
        assertEquals(REDIRECT_URI, token.getRedirectUri());
    }

    @Test
    void convert_ShouldReturnToken_WhenAdditionalParametersArePresent() {
        // Arrange
        MockHttpServletRequest request = authorizationCodeRequest();
        addAuthorizationCodeParameters(request);
        request.addParameter("code_verifier", "pkce-verifier");

        // Act
        OAuth2AuthorizationCodeAuthenticationToken token = assertAuthorizationCodeToken(converter.convert(request));

        // Assertions
        assertEquals("pkce-verifier", token.getAdditionalParameters().get("code_verifier"));
    }

    @Test
    void convert_ShouldReturnNull_WhenGrantTypeIsNotAuthorizationCode() {
        // Arrange
        MockHttpServletRequest request = authorizationCodeRequest();
        request.addParameter(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.CLIENT_CREDENTIALS.getValue());

        // Act
        Authentication authentication = converter.convert(request);

        // Assertions
        assertNull(authentication);
    }

    @Test
    void convert_ShouldReturnNull_WhenGrantTypeIsMissing() {
        // Arrange
        MockHttpServletRequest request = authorizationCodeRequest();
        request.addParameter(OAuth2ParameterNames.CODE, AUTHORIZATION_CODE);

        // Act
        Authentication authentication = converter.convert(request);

        // Assertions
        assertNull(authentication);
    }

    @Test
    void convert_ShouldReturnNull_WhenClientPrincipalIsMissing() {
        // Arrange
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = authorizationCodeRequest();
        request.addParameter(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.AUTHORIZATION_CODE.getValue());
        request.addParameter(OAuth2ParameterNames.CODE, AUTHORIZATION_CODE);
        request.addParameter(OAuth2ParameterNames.REDIRECT_URI, REDIRECT_URI);

        // Act
        Authentication authentication = converter.convert(request);

        // Assertions
        assertNull(authentication);
    }

    @Test
    void convert_ShouldReturnToken_WhenRedirectUriIsMissing() {
        // Arrange
        MockHttpServletRequest request = authorizationCodeRequest();
        request.addParameter(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.AUTHORIZATION_CODE.getValue());
        request.addParameter(OAuth2ParameterNames.CODE, AUTHORIZATION_CODE);

        // Act
        OAuth2AuthorizationCodeAuthenticationToken token = assertAuthorizationCodeToken(converter.convert(request));

        // Assertions
        assertEquals(AUTHORIZATION_CODE, token.getCode());
        assertNull(token.getRedirectUri());
    }

    @Test
    void convert_ShouldReturnToken_WhenRedirectUriIsBlank() {
        // Arrange
        MockHttpServletRequest request = authorizationCodeRequest();
        request.addParameter(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.AUTHORIZATION_CODE.getValue());
        request.addParameter(OAuth2ParameterNames.CODE, AUTHORIZATION_CODE);
        request.addParameter(OAuth2ParameterNames.REDIRECT_URI, " ");

        // Act
        OAuth2AuthorizationCodeAuthenticationToken token = assertAuthorizationCodeToken(converter.convert(request));

        // Assertions
        assertEquals(AUTHORIZATION_CODE, token.getCode());
        assertEquals(" ", token.getRedirectUri());
    }

    @Test
    void convert_ShouldThrowInvalidRequest_WhenCodeIsDuplicated() {
        // Arrange
        MockHttpServletRequest request = authorizationCodeRequest();
        request.addParameter(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.AUTHORIZATION_CODE.getValue());
        request.addParameter(OAuth2ParameterNames.CODE, AUTHORIZATION_CODE, "other-code");
        request.addParameter(OAuth2ParameterNames.REDIRECT_URI, REDIRECT_URI);

        // Act
        OAuth2AuthenticationException exception = assertThrows(OAuth2AuthenticationException.class, () -> converter.convert(request));

        // Assertions
        assertEquals(OAuth2ErrorCodes.INVALID_REQUEST, exception.getError().getErrorCode());
        assertEquals("OAuth 2.0 Parameter: code", exception.getError().getDescription());
    }

    @Test
    void convert_ShouldThrowInvalidRequest_WhenCodeIsMissing() {
        // Arrange
        MockHttpServletRequest request = authorizationCodeRequest();
        request.addParameter(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.AUTHORIZATION_CODE.getValue());
        request.addParameter(OAuth2ParameterNames.REDIRECT_URI, REDIRECT_URI);

        // Act
        OAuth2AuthenticationException exception = assertThrows(OAuth2AuthenticationException.class, () -> converter.convert(request));

        // Assertions
        assertEquals(OAuth2ErrorCodes.INVALID_REQUEST, exception.getError().getErrorCode());
        assertEquals("OAuth 2.0 Parameter: code", exception.getError().getDescription());
    }

    @Test
    void convert_ShouldThrowInvalidRequest_WhenCodeIsBlank() {
        // Arrange
        MockHttpServletRequest request = authorizationCodeRequest();
        request.addParameter(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.AUTHORIZATION_CODE.getValue());
        request.addParameter(OAuth2ParameterNames.CODE, " ");
        request.addParameter(OAuth2ParameterNames.REDIRECT_URI, REDIRECT_URI);

        // Act
        OAuth2AuthenticationException exception = assertThrows(OAuth2AuthenticationException.class, () -> converter.convert(request));

        // Assertions
        assertEquals(OAuth2ErrorCodes.INVALID_REQUEST, exception.getError().getErrorCode());
        assertEquals("OAuth 2.0 Parameter: code", exception.getError().getDescription());
    }

    @Test
    void convert_ShouldThrowInvalidRequest_WhenRedirectUriIsDuplicated() {
        // Arrange
        MockHttpServletRequest request = authorizationCodeRequest();
        request.addParameter(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.AUTHORIZATION_CODE.getValue());
        request.addParameter(OAuth2ParameterNames.CODE, AUTHORIZATION_CODE);
        request.addParameter(OAuth2ParameterNames.REDIRECT_URI, REDIRECT_URI, "http://localhost/other");

        // Act
        OAuth2AuthenticationException exception = assertThrows(OAuth2AuthenticationException.class, () -> converter.convert(request));

        // Assertions
        assertEquals(OAuth2ErrorCodes.INVALID_REQUEST, exception.getError().getErrorCode());
        assertEquals("OAuth 2.0 Parameter: redirect_uri", exception.getError().getDescription());
    }

    @Test
    void convert_ShouldExcludeStandardParametersFromAdditionalParameters_WhenStandardOAuthParamsArePresent() {
        // Arrange
        MockHttpServletRequest request = authorizationCodeRequest();
        addAuthorizationCodeParameters(request);
        request.addParameter(OAuth2ParameterNames.CLIENT_ID, CLIENT_ID);
        request.addParameter("code_verifier", "pkce-verifier");

        // Act
        OAuth2AuthorizationCodeAuthenticationToken token = assertAuthorizationCodeToken(converter.convert(request));

        // Assertions
        assertFalse(token.getAdditionalParameters().containsKey(OAuth2ParameterNames.GRANT_TYPE));
        assertFalse(token.getAdditionalParameters().containsKey(OAuth2ParameterNames.CLIENT_ID));
        assertFalse(token.getAdditionalParameters().containsKey(OAuth2ParameterNames.CODE));
        assertFalse(token.getAdditionalParameters().containsKey(OAuth2ParameterNames.REDIRECT_URI));
        assertEquals("pkce-verifier", token.getAdditionalParameters().get("code_verifier"));
    }

    @Test
    void convert_ShouldPutMultipleValuesAsArray_WhenAdditionalParameterHasMultipleValues() {
        // Arrange
        MockHttpServletRequest request = authorizationCodeRequest();
        addAuthorizationCodeParameters(request);
        request.addParameter("custom_param", "value1", "value2");

        // Act
        OAuth2AuthorizationCodeAuthenticationToken token = assertAuthorizationCodeToken(converter.convert(request));

        // Assertions
        Object customParam = token.getAdditionalParameters().get("custom_param");
        assertInstanceOf(String[].class, customParam);
        assertEquals("value1", ((String[]) customParam)[0]);
        assertEquals("value2", ((String[]) customParam)[1]);
    }

    @Test
    void convert_ShouldPutSingleValue_WhenAdditionalParameterHasSingleValue() {
        // Arrange
        MockHttpServletRequest request = authorizationCodeRequest();
        addAuthorizationCodeParameters(request);
        request.addParameter("custom_param", "single-value");

        // Act
        OAuth2AuthorizationCodeAuthenticationToken token = assertAuthorizationCodeToken(converter.convert(request));

        // Assertions
        Object customParam = token.getAdditionalParameters().get("custom_param");
        assertInstanceOf(String.class, customParam);
        assertEquals("single-value", customParam);
    }

    @Test
    void convert_ShouldReturnRefreshToken_WhenRefreshTokenParamsAreInRequestBody() {
        // Arrange
        MockHttpServletRequest request = tokenRequest();
        addRefreshTokenParameters(request);

        // Act
        OAuth2RefreshTokenAuthenticationToken token = assertRefreshTokenAuthentication(converter.convert(request));

        // Assertions
        assertEquals(REFRESH_TOKEN, token.getRefreshToken());
        assertTrue(token.getScopes() == null || token.getScopes().isEmpty());
    }

    @Test
    void convert_ShouldReturnRefreshToken_WhenRefreshTokenParamsAreInQueryString() {
        // Arrange
        MockHttpServletRequest request = tokenRequest();
        request.setQueryString("grant_type=refresh_token&refresh_token=%s".formatted(REFRESH_TOKEN));
        addRefreshTokenParameters(request);

        // Act
        OAuth2RefreshTokenAuthenticationToken token = assertRefreshTokenAuthentication(converter.convert(request));

        // Assertions
        assertEquals(REFRESH_TOKEN, token.getRefreshToken());
    }

    @Test
    void convert_ShouldReturnRefreshToken_WhenScopeIsPresent() {
        // Arrange
        MockHttpServletRequest request = tokenRequest();
        addRefreshTokenParameters(request);
        request.addParameter(OAuth2ParameterNames.SCOPE, SCOPES);

        // Act
        OAuth2RefreshTokenAuthenticationToken token = assertRefreshTokenAuthentication(converter.convert(request));

        // Assertions
        assertEquals(Set.of("read", "write"), token.getScopes());
    }

    @Test
    void convert_ShouldThrowInvalidRequest_WhenRefreshTokenIsMissing() {
        // Arrange
        MockHttpServletRequest request = tokenRequest();
        request.addParameter(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.REFRESH_TOKEN.getValue());

        // Act
        OAuth2AuthenticationException exception = assertThrows(OAuth2AuthenticationException.class, () -> converter.convert(request));

        // Assertions
        assertEquals(OAuth2ErrorCodes.INVALID_REQUEST, exception.getError().getErrorCode());
        assertEquals("OAuth 2.0 Parameter: refresh_token", exception.getError().getDescription());
    }

    @Test
    void convert_ShouldThrowInvalidRequest_WhenRefreshTokenIsDuplicated() {
        // Arrange
        MockHttpServletRequest request = tokenRequest();
        request.addParameter(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.REFRESH_TOKEN.getValue());
        request.addParameter(OAuth2ParameterNames.REFRESH_TOKEN, REFRESH_TOKEN, "other-refresh-token");

        // Act
        OAuth2AuthenticationException exception = assertThrows(OAuth2AuthenticationException.class, () -> converter.convert(request));

        // Assertions
        assertEquals(OAuth2ErrorCodes.INVALID_REQUEST, exception.getError().getErrorCode());
        assertEquals("OAuth 2.0 Parameter: refresh_token", exception.getError().getDescription());
    }

    @Test
    void convert_ShouldThrowInvalidRequest_WhenRefreshTokenScopeIsDuplicated() {
        // Arrange
        MockHttpServletRequest request = tokenRequest();
        addRefreshTokenParameters(request);
        request.addParameter(OAuth2ParameterNames.SCOPE, SCOPES, "read");

        // Act
        OAuth2AuthenticationException exception = assertThrows(OAuth2AuthenticationException.class, () -> converter.convert(request));

        // Assertions
        assertEquals(OAuth2ErrorCodes.INVALID_REQUEST, exception.getError().getErrorCode());
        assertEquals("OAuth 2.0 Parameter: scope", exception.getError().getDescription());
    }

    @Test
    void convert_ShouldExcludeRefreshTokenStandardParametersFromAdditionalParameters_WhenStandardOAuthParamsArePresent() {
        // Arrange
        MockHttpServletRequest request = tokenRequest();
        addRefreshTokenParameters(request);
        request.addParameter(OAuth2ParameterNames.SCOPE, SCOPES);
        request.addParameter("custom_param", "custom-value");

        // Act
        OAuth2RefreshTokenAuthenticationToken token = assertRefreshTokenAuthentication(converter.convert(request));

        // Assertions
        assertFalse(token.getAdditionalParameters().containsKey(OAuth2ParameterNames.GRANT_TYPE));
        assertFalse(token.getAdditionalParameters().containsKey(OAuth2ParameterNames.REFRESH_TOKEN));
        assertFalse(token.getAdditionalParameters().containsKey(OAuth2ParameterNames.SCOPE));
        assertEquals("custom-value", token.getAdditionalParameters().get("custom_param"));
    }

    @Test
    void convert_ShouldPutMultipleRefreshTokenValuesAsArray_WhenAdditionalParameterHasMultipleValues() {
        // Arrange
        MockHttpServletRequest request = tokenRequest();
        addRefreshTokenParameters(request);
        request.addParameter("custom_param", "value1", "value2");

        // Act
        OAuth2RefreshTokenAuthenticationToken token = assertRefreshTokenAuthentication(converter.convert(request));

        // Assertions
        Object customParam = token.getAdditionalParameters().get("custom_param");
        assertInstanceOf(String[].class, customParam);
        assertEquals("value1", ((String[]) customParam)[0]);
        assertEquals("value2", ((String[]) customParam)[1]);
    }

    private MockHttpServletRequest tokenRequest() {
        return new MockHttpServletRequest("POST", "/oauth/token");
    }

    private MockHttpServletRequest authorizationCodeRequest() {
        return tokenRequest();
    }

    private void addRefreshTokenParameters(MockHttpServletRequest request) {
        request.addParameter(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.REFRESH_TOKEN.getValue());
        request.addParameter(OAuth2ParameterNames.REFRESH_TOKEN, REFRESH_TOKEN);
    }

    private OAuth2RefreshTokenAuthenticationToken assertRefreshTokenAuthentication(Authentication authentication) {
        assertInstanceOf(OAuth2RefreshTokenAuthenticationToken.class, authentication);
        return (OAuth2RefreshTokenAuthenticationToken) authentication;
    }

    private void addAuthorizationCodeParameters(MockHttpServletRequest request) {
        request.addParameter(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.AUTHORIZATION_CODE.getValue());
        request.addParameter(OAuth2ParameterNames.CODE, AUTHORIZATION_CODE);
        request.addParameter(OAuth2ParameterNames.REDIRECT_URI, REDIRECT_URI);
    }

    private OAuth2AuthorizationCodeAuthenticationToken assertAuthorizationCodeToken(Authentication authentication) {
        assertInstanceOf(OAuth2AuthorizationCodeAuthenticationToken.class, authentication);
        return (OAuth2AuthorizationCodeAuthenticationToken) authentication;
    }
}
