package mb.oauth2authorizationserver;

import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.base.BaseUnitTest;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@AutoConfigureMockMvc
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JwtBearerGrantTests extends BaseUnitTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void exchangeAccessTokenUsingJwtBearerGrant() throws Exception {
        log.info("Start exchangeAccessTokenUsingJwtBearerGrant test");

        // Obtain access token using 'client_credentials' grant
        MvcResult mvcResult = this.mvc.perform(post(DEFAULT_TOKEN_ENDPOINT_URI)
                        .param(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.CLIENT_CREDENTIALS.getValue())
                        .param(OAuth2ParameterNames.SCOPE, "read openid")
                        .header(HttpHeaders.AUTHORIZATION, "Basic %s".formatted(encodeBasicAuth("client", "secret"))))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = getAccessToken(mvcResult).getTokenValue();
        log.info("Access token from 'client_credentials' grant. OAuth2AccessToken: {}", accessToken);

        Assertions.assertTrue(StringUtils.isNotBlank(accessToken));

        // Exchange access token using 'jwt-bearer' grant
        mvcResult = this.mvc.perform(post(DEFAULT_TOKEN_ENDPOINT_URI)
                        .param(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.JWT_BEARER.getValue())
                        .param(OAuth2ParameterNames.ASSERTION, accessToken)
                        .param(OAuth2ParameterNames.SCOPE, "read")
                        .header(HttpHeaders.AUTHORIZATION, "Basic %s".formatted(encodeBasicAuth("client", "secret"))))
                .andExpect(status().isOk())
                .andReturn();

        accessToken = getAccessToken(mvcResult).getTokenValue();
        log.info("Access token from 'jwt-bearer' grant. OAuth2AccessToken: {}", accessToken);

        Assertions.assertTrue(StringUtils.isNotBlank(accessToken));

        log.info("End exchangeAccessTokenUsingJwtBearerGrant test");
    }
}