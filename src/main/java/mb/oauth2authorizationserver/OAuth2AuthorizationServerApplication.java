package mb.oauth2authorizationserver;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;

@EnableAsync
@SpringBootApplication
@EnableRedisIndexedHttpSession
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OAuth2AuthorizationServerApplication {

    static void main(String[] args) {
        SpringApplication.run(OAuth2AuthorizationServerApplication.class, args);
    }
}
