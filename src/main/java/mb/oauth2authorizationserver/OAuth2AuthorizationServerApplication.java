package mb.oauth2authorizationserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class OAuth2AuthorizationServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(OAuth2AuthorizationServerApplication.class, args);
    }

}
