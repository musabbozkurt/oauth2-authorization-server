package mb.oauth2authorizationserver;

import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.config.RedisTestConfiguration;
import mb.oauth2authorizationserver.constants.ServiceConstants;
import mb.oauth2authorizationserver.data.entity.SecurityUser;
import mb.oauth2authorizationserver.data.repository.AuthorizationRepository;
import mb.oauth2authorizationserver.data.repository.ClientRepository;
import mb.oauth2authorizationserver.data.repository.UserRepository;
import mb.oauth2authorizationserver.model.enums.GrantType;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = RedisTestConfiguration.class)
class OAuth2AuthenticationFlowIntegrationTest {

    private static final String SCOPES = "read openid";
    private static final String REDIRECT_URI = "http://127.0.0.1:8080/login/oauth2/code/client";
    private static final String EXPECTED_REDIRECTED_URL = "/login";
    private static final String CLIENT_ID = "client";
    private static final String SECRET_ID = "secret";
    private static final String USER = "User";
    private static final String PASSWORD = "password";
    private static final String USER_ROLE = "USER";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private AuthorizationRepository authorizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void getGrantTypeClientCredentialsToken_ShouldSucceed_WhenClientIsValid() throws Exception {
        // Arrange
        // Act
        String response = mockMvc.perform(MockMvcRequestBuilders.post("/oauth2/token")
                        .param("grant_type", GrantType.CLIENT_CREDENTIALS.getName())
                        .param("scope", SCOPES)
                        .header("Authorization", generateBasicAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.access_token").isNotEmpty())
                .andExpect(MockMvcResultMatchers.jsonPath("$.scope").value(SCOPES))
                .andExpect(MockMvcResultMatchers.jsonPath("$.token_type").value("Bearer"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JSONObject jsonObject = new JSONObject(response);
        log.info("Grant type client credentials token response: {}", jsonObject);

        Jwt jwt = jwtDecoder.decode(jsonObject.getString(OAuth2ParameterNames.ACCESS_TOKEN));

        // Assertions
        Assertions.assertEquals(CLIENT_ID, jwt.getClaim("sub").toString());
        Assertions.assertEquals(SCOPES, String.join(" ", Optional.ofNullable(jwt.getClaim("scope"))
                .filter(List.class::isInstance)
                .map(obj -> ((List<?>) obj).stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList())
                .orElse(Collections.emptyList())));
    }

    @Test
    void getGrantTypePasswordToken_ShouldSucceed_WhenUsernameAndPasswordAreValid() throws Exception {
        Assertions.assertNotNull(generateAndValidateGrantTypePasswordTokenAndGetJsonObjectWhenUsernameAndPasswordAreValid());
    }

    @Test
    void getGrantTypePasswordToken_ShouldSucceed_WhenUserIsDisabled() throws Exception {
        SecurityUser disabledUser = userRepository.findByUsername(USER).orElseThrow();
        disabledUser.setEnabled(false);
        userRepository.save(disabledUser);
        try {
            Assertions.assertNotNull(generateAndValidateGrantTypePasswordTokenAndGetJsonObjectWhenUsernameAndPasswordAreValid());
        } finally {
            disabledUser.setEnabled(true);
            userRepository.save(disabledUser);
        }
    }

    @Test
    void getGrantTypePasswordToken_ShouldNotCreateDuplicateTokens_WhenConcurrentRequestsAreSent() throws Exception {
        // Arrange — clear any existing tokens for this user
        authorizationRepository.deleteAll(authorizationRepository.findByPrincipalName(USER));

        int concurrentRequests = 5;
        try (ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests)) {
            CountDownLatch startLatch = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();

            // Act — send concurrent password grant requests simultaneously
            for (int i = 0; i < concurrentRequests; i++) {
                futures.add(executor.submit(() -> {
                    startLatch.await(); // wait until all threads are ready
                    return mockMvc.perform(MockMvcRequestBuilders.post("/oauth2/token")
                                    .param("grant_type", ServiceConstants.CUSTOM_PASSWORD)
                                    .param("username", USER)
                                    .param("password", PASSWORD)
                                    .header("Authorization", generateBasicAuthHeader()))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                }));
            }
            startLatch.countDown(); // release all threads at once

            // Wait for all requests to complete
            for (Future<Integer> future : futures) {
                int status = future.get();
                assertEquals(200, status, "All concurrent login requests should succeed");
            }
        }

        // Assertions — count tokens in database for this user with custom_password grant type
        long tokenCount = authorizationRepository.findByPrincipalName(USER)
                .stream()
                .filter(token -> ServiceConstants.CUSTOM_PASSWORD.equals(token.getAuthorizationGrantType()))
                .count();

        assertEquals(1, tokenCount, "There should be exactly 1 token — no duplicates");
    }

    @Test
    void getOAuthAuthorize_ShouldSucceedRedirection_WhenClientIdAndRedirectUriAreValidAndRequireProofKeyIsDisabledInClientSettingsToDisablePKCE() throws Exception {
        // Arrange - Configure client to disable PKCE
        clientRepository.findByClientId(CLIENT_ID)
                .ifPresent(client -> {
                    client.setClientSettings("""
                            {"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}
                            """);
                    clientRepository.save(client);
                });

        // Act
        MockHttpServletResponse response = mockMvc.perform(MockMvcRequestBuilders.get("/oauth2/authorize")
                        .queryParam("response_type", "code")
                        .queryParam("client_id", CLIENT_ID)
                        .queryParam("redirect_uri", REDIRECT_URI)
                        .queryParam("scope", SCOPES))
                .andExpect(status().is3xxRedirection())
                .andDo(print())
                .andReturn()
                .getResponse();

        String location = response.getHeader("Location");

        // Assertions
        Assertions.assertNotNull(response);
        Assertions.assertEquals(HttpStatus.FOUND.value(), response.getStatus());
        Assertions.assertTrue(StringUtils.isNotBlank(location));
        Assertions.assertEquals(EXPECTED_REDIRECTED_URL, location);
    }

    @Test
    void getOAuthAuthorize_ShouldGenerateAuthorizationCode_WhenRequestIsValidAndRequireProofKeyIsDisabledInClientSettingsToDisablePKCE() throws Exception {
        Assertions.assertNotNull(generateAndValidateAuthorizationCodeWhenRequestIsValidAndRequireProofKeyIsDisabledInClientSettingsToDisablePKCE());
    }

    @Test
    void getOAuthToken_ShouldSucceed_WhenAuthorizationCodeIsValidAndParamsAreInQueryString() throws Exception {
        String authorizationCode = generateAndValidateAuthorizationCodeWhenRequestIsValidAndRequireProofKeyIsDisabledInClientSettingsToDisablePKCE();

        MockHttpServletResponse authorizationCodeResponse = mockMvc.perform(MockMvcRequestBuilders.post("/oauth2/token")
                        .header("Authorization", generateBasicAuthHeader())
                        .queryParam("grant_type", "authorization_code")
                        .queryParam("code", authorizationCode)
                        .queryParam("redirect_uri", REDIRECT_URI))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.valueOf("application/json;charset=UTF-8")))
                .andExpect(MockMvcResultMatchers.jsonPath("$.access_token").exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.token_type").value("Bearer"))
                .andDo(print())
                .andReturn()
                .getResponse();

        JSONObject jsonResponse = new JSONObject(authorizationCodeResponse.getContentAsString());
        Jwt jwt = jwtDecoder.decode(jsonResponse.getString(OAuth2ParameterNames.ACCESS_TOKEN));

        assertNotNull(jsonResponse.getString("access_token"));
        assertEquals(USER, jwt.getClaim("user_name").toString());
        assertEquals(CLIENT_ID, jwt.getClaim("client_id").toString());
    }

    @Test
    void getOAuthAuthorize_ShouldSucceedRedirection_WhenClientIdAndRedirectUriAreValidAndRequireProofKeyIsEnabledInClientSettingsToEnablePKCE() throws Exception {
        // Arrange
        String codeVerifier = "someRandomString123456789someRandomString123456789";
        String codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes())
        );

        // Act
        MockHttpServletResponse response = mockMvc.perform(MockMvcRequestBuilders.get("/oauth2/authorize")
                        .queryParam("response_type", "code")
                        .queryParam("client_id", CLIENT_ID)
                        .queryParam("redirect_uri", REDIRECT_URI)
                        .queryParam("scope", SCOPES)
                        .queryParam("code_challenge", codeChallenge)
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andDo(print())
                .andReturn()
                .getResponse();

        String location = response.getHeader("Location");

        // Assertions
        Assertions.assertNotNull(response);
        Assertions.assertEquals(HttpStatus.FOUND.value(), response.getStatus());
        Assertions.assertTrue(StringUtils.isNotBlank(location));
        Assertions.assertEquals(EXPECTED_REDIRECTED_URL, location);
    }

    @Test
    void getOAuthToken_ShouldFail_WhenAuthorizationCodeIsInvalid() throws Exception {
        // Arrange
        String authorizationCode = "valid-auth-code";
        String expectedResponseContent = """
                {"error":"invalid_grant"}""";

        // Act
        MockHttpServletResponse response = mockMvc.perform(MockMvcRequestBuilders.post("/oauth2/token")
                        .header("Authorization", generateBasicAuthHeader())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", GrantType.AUTHORIZATION_CODE.getName())
                        .param("code", authorizationCode)
                        .param("redirect_uri", REDIRECT_URI))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.valueOf("application/json;charset=UTF-8")))
                .andDo(print())
                .andReturn()
                .getResponse();

        Assertions.assertNotNull(response);
        Assertions.assertNotNull(response.getContentAsString());
        Assertions.assertEquals(expectedResponseContent, response.getContentAsString());
    }

    @Test
    void getOAuthToken_ShouldSucceed_WhenAuthorizationCodeIsValid() throws Exception {
        // Arrange
        String authorizationCode = generateAndValidateAuthorizationCodeWhenRequestIsValidAndRequireProofKeyIsDisabledInClientSettingsToDisablePKCE();

        // Act
        MockHttpServletResponse authorizationCodeResponse = mockMvc.perform(MockMvcRequestBuilders.post("/oauth2/token")
                        .header("Authorization", generateBasicAuthHeader())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", GrantType.AUTHORIZATION_CODE.getName())
                        .param("code", authorizationCode)
                        .param("redirect_uri", REDIRECT_URI))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.valueOf("application/json;charset=UTF-8")))
                .andExpect(MockMvcResultMatchers.jsonPath("$.access_token").exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.token_type").value("Bearer"))
                .andDo(print())
                .andReturn()
                .getResponse();

        JSONObject jsonResponse = new JSONObject(authorizationCodeResponse.getContentAsString());
        Jwt jwt = jwtDecoder.decode(jsonResponse.getString(OAuth2ParameterNames.ACCESS_TOKEN));

        // Assertions
        Assertions.assertNotNull(jsonResponse.getString("access_token"));
        Assertions.assertNotNull(authorizationCodeResponse);
        Assertions.assertNotNull(authorizationCodeResponse.getContentAsString());
        Assertions.assertTrue(jwt.getClaim("sub").toString().contains(USER));
    }

    @Test
    void getOAuthToken_ShouldSucceed_WhenAuthorizationCodeIsValidWithMultipleLogins() throws Exception {
        int successfulLogins = 0;
        generateAndValidateOAuthTokenWhenAuthorizationCodeIsValid();
        successfulLogins++;
        generateAndValidateOAuthTokenWhenAuthorizationCodeIsValid();
        successfulLogins++;
        generateAndValidateOAuthTokenWhenAuthorizationCodeIsValid();
        successfulLogins++;
        generateAndValidateOAuthTokenWhenAuthorizationCodeIsValid();
        successfulLogins++;
        generateAndValidateOAuthTokenWhenAuthorizationCodeIsValid();
        successfulLogins++;
        assertEquals(5, successfulLogins, "All 5 logins should succeed");
    }

    @Test
    void checkToken_ShouldReturnTokenInfo_WhenInactiveValidTokenIsProvided() throws Exception {
        Assertions.assertNotNull(introspectTokenAndGetJsonObject("paste-your-token-to-check-token-is-active-or-inactive", false));
    }

    @Test
    void getGrantTypeRefreshToken_ShouldSucceed_WhenRefreshTokenIsValidAndParamsAreInQueryString() throws Exception {
        String refreshToken = generateAndValidateGrantTypePasswordTokenAndGetJsonObjectWhenUsernameAndPasswordAreValid()
                .getString(OAuth2ParameterNames.REFRESH_TOKEN);

        String refreshTokenResponse = mockMvc.perform(MockMvcRequestBuilders.post("/oauth2/token")
                        .header("Authorization", generateBasicAuthHeader())
                        .queryParam("grant_type", "refresh_token")
                        .queryParam("refresh_token", refreshToken))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.access_token").isNotEmpty())
                .andExpect(MockMvcResultMatchers.jsonPath("$.refresh_token").isNotEmpty())
                .andExpect(MockMvcResultMatchers.jsonPath("$.token_type").value("Bearer"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JSONObject refreshTokenJsonObject = new JSONObject(refreshTokenResponse);
        Jwt jwt = jwtDecoder.decode(refreshTokenJsonObject.getString(OAuth2ParameterNames.ACCESS_TOKEN));
        JSONObject introspectedJsonObject = introspectTokenAndGetJsonObject(jwt.getTokenValue(), true);

        Assertions.assertEquals(CLIENT_ID, jwt.getClaim("sub").toString());
        Assertions.assertEquals("Test Access Token", jwt.getClaim("Test").toString());
        Assertions.assertEquals(CLIENT_ID, introspectedJsonObject.getString("client_id"));
        Assertions.assertEquals("Bearer", introspectedJsonObject.getString("token_type"));
    }

    @Test
    void getGrantTypeRefreshToken_ShouldSucceed_WhenRefreshTokenIsValid() throws Exception {
        // Arrange
        String refreshToken = generateAndValidateGrantTypePasswordTokenAndGetJsonObjectWhenUsernameAndPasswordAreValid().getString(OAuth2ParameterNames.REFRESH_TOKEN);

        // Act
        String refreshTokenResponse = mockMvc.perform(MockMvcRequestBuilders.post("/oauth2/token")
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", refreshToken)
                        .header("Authorization", generateBasicAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.access_token").isNotEmpty())
                .andExpect(MockMvcResultMatchers.jsonPath("$.refresh_token").isNotEmpty())
                .andExpect(MockMvcResultMatchers.jsonPath("$.token_type").value("Bearer"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JSONObject refreshTokenJsonObject = new JSONObject(refreshTokenResponse);
        log.info("Grant type refresh token response: {}", refreshTokenJsonObject);

        Jwt jwt = jwtDecoder.decode(refreshTokenJsonObject.getString(OAuth2ParameterNames.ACCESS_TOKEN));
        JSONObject introspectedJsonObject = introspectTokenAndGetJsonObject(jwt.getTokenValue(), true);

        // Assertions - custom claims are in the JWT, not in the introspection response
        Assertions.assertEquals(CLIENT_ID, jwt.getClaim("sub").toString());
        Assertions.assertEquals("Test Access Token", jwt.getClaim("Test").toString());
        // Introspection only returns standard OAuth2 fields
        Assertions.assertEquals(CLIENT_ID, introspectedJsonObject.getString("client_id"));
        Assertions.assertEquals("Bearer", introspectedJsonObject.getString("token_type"));
    }

    private String generateBasicAuthHeader() {
        return "Basic %s".formatted(Base64.getEncoder().encodeToString(("%s:%s".formatted(CLIENT_ID, SECRET_ID)).getBytes()));
    }

    private JSONObject generateAndValidateGrantTypePasswordTokenAndGetJsonObjectWhenUsernameAndPasswordAreValid() throws Exception {
        // Arrange
        // Act
        String response = mockMvc.perform(MockMvcRequestBuilders.post("/oauth2/token")
                        .param("grant_type", ServiceConstants.CUSTOM_PASSWORD)
                        .param("username", USER)
                        .param("password", PASSWORD)
                        .header("Authorization", generateBasicAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.access_token").isNotEmpty())
                .andExpect(MockMvcResultMatchers.jsonPath("$.refresh_token").isNotEmpty())
                .andExpect(MockMvcResultMatchers.jsonPath("$.token_type").value("Bearer"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JSONObject jsonObject = new JSONObject(response);
        log.info("Grant type password token response: {}", jsonObject);

        Jwt jwt = jwtDecoder.decode(jsonObject.getString(OAuth2ParameterNames.ACCESS_TOKEN));
        JSONObject introspectedJsonObject = introspectTokenAndGetJsonObject(jwt.getTokenValue(), true);

        // Assertions - custom claims are in the JWT, not in the introspection response
        Assertions.assertEquals(CLIENT_ID, jwt.getClaim("sub").toString());
        Assertions.assertEquals("Test Access Token", jwt.getClaim("Test").toString());
        // Introspection only returns standard OAuth2 fields
        Assertions.assertEquals(CLIENT_ID, introspectedJsonObject.getString("client_id"));
        Assertions.assertEquals("Bearer", introspectedJsonObject.getString("token_type"));

        return jsonObject;
    }

    private String generateAndValidateAuthorizationCodeWhenRequestIsValidAndRequireProofKeyIsDisabledInClientSettingsToDisablePKCE() throws Exception {
        // Perform login with CSRF token and simulate authenticated user
        // Arrange
        clientRepository.findByClientId(CLIENT_ID)
                .ifPresent(client1 -> {
                    client1.setClientSettings("""
                            {"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}
                            """);
                    clientRepository.save(client1);
                });

        // Act
        MockHttpServletResponse response = mockMvc.perform(MockMvcRequestBuilders.get("/oauth2/authorize")
                        .with(csrf())
                        .with(user(USER).password(PASSWORD).roles(USER_ROLE)) // Add authentication
                        .queryParam("response_type", "code")
                        .queryParam("client_id", CLIENT_ID)
                        .queryParam("redirect_uri", REDIRECT_URI)
                        .queryParam("scope", SCOPES))
                .andExpect(status().is3xxRedirection())
                .andDo(print())
                .andReturn()
                .getResponse();

        String location = response.getHeader("Location");

        // Assertions
        Assertions.assertNotNull(response);
        Assertions.assertEquals(HttpStatus.FOUND.value(), response.getStatus());
        Assertions.assertTrue(StringUtils.isNotBlank(location));
        Assertions.assertTrue(location.contains("code="), "Location should contain authorization code");

        return location.substring(location.indexOf("code=") + 5);
    }

    private void generateAndValidateOAuthTokenWhenAuthorizationCodeIsValid() throws Exception {
        String authorizationCode = generateAndValidateAuthorizationCodeWhenRequestIsValidAndRequireProofKeyIsDisabledInClientSettingsToDisablePKCE();

        MockHttpServletResponse authorizationCodeResponse = mockMvc.perform(MockMvcRequestBuilders.post("/oauth2/token")
                        .header("Authorization", generateBasicAuthHeader())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", GrantType.AUTHORIZATION_CODE.getName())
                        .param("code", authorizationCode)
                        .param("redirect_uri", REDIRECT_URI))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.valueOf("application/json;charset=UTF-8")))
                .andExpect(MockMvcResultMatchers.jsonPath("$.access_token").exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.token_type").value("Bearer"))
                .andDo(print())
                .andReturn()
                .getResponse();

        JSONObject jsonResponse = new JSONObject(authorizationCodeResponse.getContentAsString());
        Jwt jwt = jwtDecoder.decode(jsonResponse.getString(OAuth2ParameterNames.ACCESS_TOKEN));

        Assertions.assertNotNull(jsonResponse.getString("access_token"));
        Assertions.assertNotNull(authorizationCodeResponse);
        Assertions.assertNotNull(authorizationCodeResponse.getContentAsString());
        Assertions.assertEquals(USER, jwt.getClaim("user_name").toString());
        Assertions.assertEquals(CLIENT_ID, jwt.getClaim("client_id").toString());
    }

    private JSONObject introspectTokenAndGetJsonObject(String token, boolean active) throws Exception {
        // Arrange
        // Act
        MockHttpServletResponse response = mockMvc.perform(MockMvcRequestBuilders.post("/oauth2/introspect")
                        .header("Authorization", generateBasicAuthHeader())
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.active").exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.active").isBoolean())
                .andExpect(MockMvcResultMatchers.jsonPath("$.active").value(active))
                .andDo(print())
                .andReturn()
                .getResponse();

        JSONObject jsonResponse = new JSONObject(response.getContentAsString());

        // Assertions
        Assertions.assertNotNull(response);
        Assertions.assertEquals(HttpStatus.OK.value(), response.getStatus());
        Assertions.assertNotNull(jsonResponse.get("active"));
        Assertions.assertEquals(jsonResponse.getBoolean("active"), active);

        return jsonResponse;
    }
}
