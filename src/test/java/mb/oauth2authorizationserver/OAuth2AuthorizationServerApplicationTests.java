package mb.oauth2authorizationserver;

import mb.oauth2authorizationserver.config.RedisTestConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = RedisTestConfiguration.class)
class OAuth2AuthorizationServerApplicationTests {

    @Test
    void contextLoads() {
        Assertions.assertTrue(true);
    }

}
