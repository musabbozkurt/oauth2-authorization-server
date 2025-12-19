package mb.oauth2authorizationserver.api.controller;

import mb.oauth2authorizationserver.api.request.ApiUserRequest;
import mb.oauth2authorizationserver.api.response.ApiUserResponse;
import mb.oauth2authorizationserver.base.BaseUnitTest;
import mb.oauth2authorizationserver.config.RedisTestConfiguration;
import mb.oauth2authorizationserver.config.RestPageImpl;
import mb.oauth2authorizationserver.config.TestSecurityConfig;
import mb.oauth2authorizationserver.exception.ErrorResponse;
import mb.oauth2authorizationserver.exception.LocalizedExceptionResponse;
import mb.oauth2authorizationserver.exception.OAuth2AuthorizationServerServiceErrorCode;
import mb.oauth2authorizationserver.mapper.UserMapper;
import mb.oauth2authorizationserver.service.UserService;
import org.instancio.Instancio;
import org.instancio.Select;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.util.Collections;
import java.util.List;

@AutoConfigureTestRestTemplate
@TestMethodOrder(OrderAnnotation.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {TestSecurityConfig.class, RedisTestConfiguration.class})
class UserControllerIntegrationTests extends BaseUnitTest {

    private static ApiUserResponse apiUserResponse;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Test
    @Order(value = 1)
    void testServiceConnection() {
        Assertions.assertNotNull(userService);
        Assertions.assertNotNull(userMapper);
    }

    @Test
    @Order(value = 2)
    void testGetUsers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        String response = testRestTemplate.exchange("/users/", HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();

        Assertions.assertNotNull(response);
        Assertions.assertTrue(response.contains("content"));
        Assertions.assertTrue(response.contains("\"page\""));
        Assertions.assertTrue(response.contains("totalElements"));
        Assertions.assertTrue(response.contains("totalPages"));
        Assertions.assertTrue(response.contains("\"size\""));
        Assertions.assertTrue(response.contains("\"number\""));
    }

    @Test
    @Order(value = 2)
    void testGetUsers_ShouldReturnPagedResponse_WhenCalledWithParameterizedTypeReference() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        var response = testRestTemplate.exchange(
                "/users/?page=0&size=10",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<RestPageImpl<ApiUserResponse>>() {
                }
        ).getBody();

        Assertions.assertNotNull(response, "Response should not be null");
        List<ApiUserResponse> content = response.getContent();
        apiUserResponse = content.getFirst();
        Assertions.assertNotNull(content, "Page content should not be null");
        Assertions.assertFalse(content.isEmpty(), "Content should not be empty");
        Assertions.assertEquals(0, response.getNumber(), "Page number should be 0");
        Assertions.assertEquals(10, response.getSize(), "Page size should be 10");
        Assertions.assertTrue(response.getTotalElements() >= 0, "Total elements should be >= 0");
    }

    @Test
    @Order(value = 2)
    void testGetUsers_ShouldReturnPagedResponse_WhenCalled() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        var response = testRestTemplate.exchange("/users/?page=0&size=10", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class).getBody();

        Assertions.assertNotNull(response, "Response should not be null");
        Assertions.assertTrue(response.has("content"), "Response should have content");
        Assertions.assertTrue(response.has("page"), "Response should have page metadata");
        JsonNode page = response.get("page");
        Assertions.assertEquals(0, page.get("number").asInt(), "Page number should be 0");
        Assertions.assertEquals(10, page.get("size").asInt(), "Page size should be 10");
        Assertions.assertTrue(page.has("totalElements"), "Page should have totalElements");
        Assertions.assertTrue(page.has("totalPages"), "Page should have totalPages");
    }

    @Test
    @Order(value = 3)
    void testCreateUser() {
        ApiUserRequest apiUserRequest = getApiUserRequest();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ApiUserResponse response = testRestTemplate.postForObject("/users/", new HttpEntity<>(apiUserRequest, headers), ApiUserResponse.class);

        Assertions.assertNotNull(response);
        Assertions.assertEquals(apiUserRequest.getFirstName(), response.getFirstName());
        Assertions.assertEquals(apiUserRequest.getLastName(), response.getLastName());
        Assertions.assertEquals(apiUserRequest.getEmail(), response.getEmail());
    }

    @Test
    @Order(value = 4)
    void testCreateUser_ShouldFail_WhenPhoneNumberOrEmailIsAlreadyExists() {
        ApiUserRequest apiUserRequest = getApiUserRequest2();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // ErrorResponse or LocalizedExceptionResponse can be used here based on the implementation
        ErrorResponse exception = testRestTemplate.postForObject("/users/", new HttpEntity<>(apiUserRequest, headers), ErrorResponse.class);

        Assertions.assertNotNull(exception);
        Assertions.assertEquals(OAuth2AuthorizationServerServiceErrorCode.UNEXPECTED_ERROR.getCode(), exception.getErrorCode());
    }

    @Test
    @Order(value = 5)
    void testGetUserById() {
        ApiUserResponse response = testRestTemplate.getForObject("/users/%d".formatted(apiUserResponse.getId()), ApiUserResponse.class);

        Assertions.assertNotNull(response);
        Assertions.assertNotNull(response.getId());
        Assertions.assertNotNull(response.getUsername());
        Assertions.assertNotNull(response.getEmail());
    }

    @Test
    @Order(value = 6)
    void testGetUserById_ShouldFail_WhenUserIsNotFound() {
        LocalizedExceptionResponse exception = testRestTemplate.getForObject("/users/0", LocalizedExceptionResponse.class);

        Assertions.assertNotNull(exception);
        Assertions.assertEquals(OAuth2AuthorizationServerServiceErrorCode.USER_NOT_FOUND.getCode(), exception.getErrorCode());
    }

    @Test
    @Order(value = 7)
    void testUpdateUserById() {
        ApiUserRequest apiUserRequest = getApiUserRequest3();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ApiUserResponse response = testRestTemplate.exchange("/users/1", HttpMethod.PUT, new HttpEntity<>(apiUserRequest, headers), ApiUserResponse.class).getBody();

        Assertions.assertNotNull(response);
        Assertions.assertEquals(apiUserRequest.getEmail(), response.getEmail());
    }

    @Test
    @Order(value = 8)
    void testUpdateUserById_ShouldFail_WhenUserIsNotFound() {
        ApiUserRequest apiUserRequest = getApiUserRequest();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        LocalizedExceptionResponse exception = testRestTemplate.exchange("/users/0", HttpMethod.PUT, new HttpEntity<>(apiUserRequest, headers), LocalizedExceptionResponse.class).getBody();

        Assertions.assertNotNull(exception);
        Assertions.assertEquals(OAuth2AuthorizationServerServiceErrorCode.USER_NOT_FOUND.getCode(), exception.getErrorCode());
    }

    @Test
    @Order(value = 9)
    void testDeleteUserById() {
        String response = testRestTemplate.exchange("/users/4", HttpMethod.DELETE, null, String.class).getBody();

        Assertions.assertEquals("User deleted successfully.", response);
    }

    @Test
    @Order(value = 10)
    void testDeleteUserById_ShouldFail_WhenUserIsNotFound() {
        LocalizedExceptionResponse exception = testRestTemplate.exchange("/users/0", HttpMethod.DELETE, null, LocalizedExceptionResponse.class).getBody();

        Assertions.assertNotNull(exception);
        Assertions.assertEquals(OAuth2AuthorizationServerServiceErrorCode.USER_NOT_FOUND.getCode(), exception.getErrorCode());
    }

    @Test
    @Order(value = 11)
    void testCreateUser_ShouldSanitizeScriptTags_WhenXSSPayloadInFirstName() {
        ApiUserRequest apiUserRequest = Instancio.of(ApiUserRequest.class)
                .set(Select.field(ApiUserRequest::getFirstName), "<script>alert('xss')</script>John")
                .set(Select.field(ApiUserRequest::getLastName), "Doe")
                .set(Select.field(ApiUserRequest::getEmail), "xsstest13@example.com")
                .set(Select.field(ApiUserRequest::getPassword), "password123")
                .create();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ApiUserResponse response = testRestTemplate.exchange("/users/", HttpMethod.POST, new HttpEntity<>(apiUserRequest, headers), ApiUserResponse.class).getBody();

        Assertions.assertNotNull(response, "Response should not be null");
        Assertions.assertNotNull(response.getId(), "User ID should be generated");
        Assertions.assertNotNull(response.getFirstName(), "First name should not be null");
        Assertions.assertFalse(response.getFirstName().contains("<script>"), "Script opening tag should be removed");
        Assertions.assertFalse(response.getFirstName().contains("</script>"), "Script closing tag should be removed");
        Assertions.assertFalse(response.getFirstName().contains("alert"), "Script content should be removed");
        Assertions.assertTrue(response.getFirstName().contains("John"), "Safe content should be preserved");
        Assertions.assertEquals("John", response.getFirstName(), "Script tags and content should be stripped");
        Assertions.assertEquals("Doe", response.getLastName(), "Last name should remain unchanged");
        Assertions.assertEquals("xsstest13@example.com", response.getEmail(), "Email should remain unchanged");
    }

    @Test
    @Order(value = 12)
    void testUpdateUser_ShouldSanitizeScriptTags_WhenXSSPayloadInEmail() {
        ApiUserRequest apiUserRequest = Instancio.of(ApiUserRequest.class)
                .set(Select.field(ApiUserRequest::getFirstName), "Jane")
                .set(Select.field(ApiUserRequest::getLastName), "Smith")
                .set(Select.field(ApiUserRequest::getEmail), "<script>alert('xss')</script>test14@example.com")
                .set(Select.field(ApiUserRequest::getPassword), "password123")
                .create();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ApiUserResponse response = testRestTemplate.exchange("/users/1", HttpMethod.PUT, new HttpEntity<>(apiUserRequest, headers), ApiUserResponse.class).getBody();

        Assertions.assertNotNull(response, "Response should not be null");
        Assertions.assertNotNull(response.getId(), "User ID should be present");
        Assertions.assertNotNull(response.getEmail(), "Email should not be null");
        Assertions.assertFalse(response.getEmail().contains("<script>"), "Script opening tag should be removed from email");
        Assertions.assertFalse(response.getEmail().contains("</script>"), "Script closing tag should be removed from email");
        Assertions.assertTrue(response.getEmail().contains("@example.com"), "Valid email domain should be preserved");
        Assertions.assertEquals("Jane", response.getFirstName(), "First name should remain unchanged");
        Assertions.assertEquals("Smith", response.getLastName(), "Last name should remain unchanged");
    }

    @Test
    @Order(value = 13)
    void testCreateUser_ShouldSanitizeEventHandlers_WhenOnErrorAttributePresent() {
        ApiUserRequest apiUserRequest = Instancio.of(ApiUserRequest.class)
                .set(Select.field(ApiUserRequest::getFirstName), "John onerror=alert('xss')")
                .set(Select.field(ApiUserRequest::getLastName), "Doe")
                .set(Select.field(ApiUserRequest::getEmail), "xsstest15@example.com")
                .set(Select.field(ApiUserRequest::getPassword), "password123")
                .create();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ApiUserResponse response = testRestTemplate.exchange("/users/", HttpMethod.POST, new HttpEntity<>(apiUserRequest, headers), ApiUserResponse.class).getBody();

        Assertions.assertNotNull(response, "Response should not be null");
        Assertions.assertNotNull(response.getId(), "User ID should be generated");
        Assertions.assertNotNull(response.getFirstName(), "First name should not be null");
        Assertions.assertFalse(response.getFirstName().contains("onerror="), "onerror event handler should be removed");
        Assertions.assertTrue(response.getFirstName().startsWith("John"), "Safe content should be preserved");
        Assertions.assertEquals("Doe", response.getLastName(), "Last name should remain unchanged");
        Assertions.assertEquals("xsstest15@example.com", response.getEmail(), "Email should remain unchanged");
    }

    @Test
    @Order(value = 14)
    void testCreateUser_ShouldSanitizeJavascriptProtocol_WhenJavascriptUrlPresent() {
        ApiUserRequest apiUserRequest = Instancio.of(ApiUserRequest.class)
                .set(Select.field(ApiUserRequest::getFirstName), "javascript:alert('xss')")
                .set(Select.field(ApiUserRequest::getLastName), "Doe")
                .set(Select.field(ApiUserRequest::getEmail), "jstest16@example.com")
                .set(Select.field(ApiUserRequest::getPassword), "password123")
                .create();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ApiUserResponse response = testRestTemplate.exchange("/users/", HttpMethod.POST, new HttpEntity<>(apiUserRequest, headers), ApiUserResponse.class).getBody();

        Assertions.assertNotNull(response, "Response should not be null");
        Assertions.assertNotNull(response.getId(), "User ID should be generated");
        Assertions.assertNotNull(response.getFirstName(), "First name should not be null");
        Assertions.assertFalse(response.getFirstName().contains("javascript:"), "javascript: protocol should be removed");
        Assertions.assertEquals("alert('xss')", response.getFirstName(), "Content after javascript: should remain");
        Assertions.assertEquals("Doe", response.getLastName(), "Last name should remain unchanged");
        Assertions.assertEquals("jstest16@example.com", response.getEmail(), "Email should remain unchanged");
    }
}
