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
class CustomPasswordTest extends BaseUnitTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void exchangeAccessTokenUsingCustomPassword() throws Exception {
        log.info("Start exchangeAccessTokenUsingCustomPassword test");

        MvcResult mvcResult = this.mvc.perform(post(DEFAULT_TOKEN_ENDPOINT_URI)
                        .param(OAuth2ParameterNames.GRANT_TYPE, new AuthorizationGrantType("custom_password").getValue())
                        .param(OAuth2ParameterNames.USERNAME, "User")
                        .param(OAuth2ParameterNames.PASSWORD, "password")
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + encodeBasicAuth("client", "secret")))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = getAccessToken(mvcResult).getTokenValue();
        String refreshToken = getRefreshToken(mvcResult).getTokenValue();

        log.info("Access token from 'custom_password' grant. OAuth2AccessToken: {}", accessToken);
        log.info("Refresh token from 'custom_password' grant. OAuth2RefreshToken: {}", refreshToken);

        Assertions.assertTrue(StringUtils.isNotBlank(accessToken));
        Assertions.assertTrue(StringUtils.isNotBlank(refreshToken));

        log.info("End exchangeAccessTokenUsingCustomPassword test");
    }
}
