package mb.oauth2authorizationserver;

import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.config.RedisTestConfiguration;
import mb.oauth2authorizationserver.data.repository.ClientRepository;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@Transactional
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = RedisTestConfiguration.class)
class OAuth2AuthenticationFlowIntegrationTest {

    private static final String SCOPES = "read openid";
    private static final String ACCESS_TOKEN = "access_token";
    private static final String REDIRECT_URI = "http://127.0.0.1:8080/login/oauth2/code/client";
    private static final String EXPECTED_REDIRECTED_URL = "http://localhost/login";
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

    @Test
    void getGrantTypeClientCredentialsToken_ShouldSucceed_WhenClientIsValid() throws Exception {
        // Arrange
        // Act
        String response = mockMvc.perform(MockMvcRequestBuilders.post("/oauth2/token")
                        .param("grant_type", "client_credentials")
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

        Jwt jwt = jwtDecoder.decode(jsonObject.getString(ACCESS_TOKEN));

        // Assertions
        Assertions.assertEquals(CLIENT_ID, jwt.getClaim("sub").toString());
        Assertions.assertEquals(SCOPES, String.join(" ", (ArrayList<String>) jwt.getClaims().get("scope")));
    }

    @Test
    void getGrantTypePasswordToken_ShouldSucceed_WhenUsernameAndPasswordAreValid() throws Exception {
        // Arrange
        // Act
        String response = mockMvc.perform(MockMvcRequestBuilders.post("/oauth2/token")
                        .param("grant_type", "custom_password")
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

        Jwt jwt = jwtDecoder.decode(jsonObject.getString(ACCESS_TOKEN));

        // Assertions
        Assertions.assertEquals(CLIENT_ID, jwt.getClaim("sub").toString());
    }

    @Test
    void getOAuthAuthorize_ShouldSucceedRedirection_WhenClientIdAndRedirectUriAreValidAndRequireProofKeyIsDisabledInClientSettingsToDisablePKCE() throws Exception {
        // Arrange
        // Act
        MockHttpServletResponse response = mockMvc.perform(MockMvcRequestBuilders.get("/oauth/authorize")
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
        generateAndValidateAuthorizationCodeWhenRequestIsValidAndRequireProofKeyIsDisabledInClientSettingsToDisablePKCE();
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
                        .param("grant_type", "authorization_code")
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
                        .param("grant_type", "authorization_code")
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
        Jwt jwt = jwtDecoder.decode(jsonResponse.getString(ACCESS_TOKEN));

        // Assertions
        Assertions.assertNotNull(jsonResponse.getString("access_token"));
        Assertions.assertNotNull(authorizationCodeResponse);
        Assertions.assertNotNull(authorizationCodeResponse.getContentAsString());
        Assertions.assertEquals(USER, jwt.getClaim("sub").toString());
    }

    private String generateBasicAuthHeader() {
        return "Basic %s".formatted(Base64.getEncoder().encodeToString(("%s:%s".formatted(CLIENT_ID, SECRET_ID)).getBytes()));
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
}
