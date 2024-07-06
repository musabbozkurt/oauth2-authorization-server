package mb.oauth2authorizationserver.base;

import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.api.request.ApiUserRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
public abstract class BaseUnitTest {

    public static final String DEFAULT_TOKEN_ENDPOINT_URI = "/oauth2/token";

    private static final HttpMessageConverter<OAuth2AccessTokenResponse> accessTokenHttpResponseConverter = new OAuth2AccessTokenResponseHttpMessageConverter();

    public OAuth2AccessToken getAccessToken(MvcResult mvcResult) {
        MockHttpServletResponse servletResponse = mvcResult.getResponse();
        MockClientHttpResponse httpResponse = new MockClientHttpResponse(servletResponse.getContentAsByteArray(), HttpStatus.valueOf(servletResponse.getStatus()));
        try {
            return accessTokenHttpResponseConverter.read(OAuth2AccessTokenResponse.class, httpResponse).getAccessToken();
        } catch (Exception ex) {
            log.info("Exception message: {}", ex.getMessage());
            throw new RuntimeException(ex);
        }
    }

    public String encodeBasicAuth(String clientId, String secret) {
        clientId = URLEncoder.encode(clientId, StandardCharsets.UTF_8);
        secret = URLEncoder.encode(secret, StandardCharsets.UTF_8);
        String credentialsString = "%s:%s".formatted(clientId, secret);
        byte[] encodedBytes = Base64.getEncoder().encode(credentialsString.getBytes(StandardCharsets.UTF_8));
        return new String(encodedBytes, StandardCharsets.UTF_8);
    }

    public OAuth2RefreshToken getRefreshToken(MvcResult mvcResult) {
        MockHttpServletResponse servletResponse = mvcResult.getResponse();
        MockClientHttpResponse httpResponse = new MockClientHttpResponse(servletResponse.getContentAsByteArray(), HttpStatus.valueOf(servletResponse.getStatus()));
        try {
            return accessTokenHttpResponseConverter.read(OAuth2AccessTokenResponse.class, httpResponse).getRefreshToken();
        } catch (Exception ex) {
            log.info("Exception message: {}", ex.getMessage());
            throw new RuntimeException(ex);
        }
    }

    public static ApiUserRequest getApiUserRequest() {
        ApiUserRequest apiUserRequest = new ApiUserRequest();
        apiUserRequest.setFirstName("Jack");
        apiUserRequest.setLastName("Hack");
        apiUserRequest.setUsername("jack_hack");
        apiUserRequest.setPassword("test1234");
        apiUserRequest.setEmail("jack.hack@gmail.com");
        apiUserRequest.setPhoneNumber("1234567899");
        return apiUserRequest;
    }

    public static ApiUserRequest getApiUserRequest2() {
        ApiUserRequest apiUserRequest = new ApiUserRequest();
        apiUserRequest.setFirstName("Jack");
        apiUserRequest.setLastName("Hack");
        apiUserRequest.setUsername("jack_hack");
        apiUserRequest.setPassword("test1234");
        apiUserRequest.setEmail("jack.hack@gmail.com");
        apiUserRequest.setPhoneNumber("1234567890");
        return apiUserRequest;
    }

    public static ApiUserRequest getApiUserRequest3() {
        ApiUserRequest apiUserRequest = new ApiUserRequest();
        apiUserRequest.setEmail("jack.hack.new@gmail.com");
        return apiUserRequest;
    }
}
