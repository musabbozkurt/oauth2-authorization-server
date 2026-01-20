package mb.oauth2authorizationserver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@Slf4j
@EnableAsync
@SpringBootApplication
@EnableRedisIndexedHttpSession
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class OAuth2AuthorizationServerApplication {

    static void main(String[] args) {
        SpringApplication.run(OAuth2AuthorizationServerApplication.class, args);
    }

    @Bean
    CommandLineRunner runner() {
        return _ -> log.info("Spring Boot Version: {}, Spring AI Version: {}", SpringBootVersion.getVersion(), ChatModel.class.getPackage().getImplementationVersion());
    }
}
