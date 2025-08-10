package mb.oauth2authorizationserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.ldap.LLdapContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@TestConfiguration(proxyBeanMethods = false)
public class LldapTestConfiguration {

    private static final LLdapContainer lldapContainer;

    static {
        lldapContainer = new LLdapContainer(DockerImageName.parse("lldap/lldap:v0.6.1-alpine")).withReuse(true);
        lldapContainer.start();

        assertThat(lldapContainer.isRunning()).isTrue();
        assertThat(lldapContainer.getMappedPort(3890)).isGreaterThan(0);
    }

    @Bean
    public DynamicPropertyRegistrar registerDatabaseProperties() {
        return registry -> {
            registry.add("spring.ldap.urls", () -> "ldap://%s:%d".formatted(lldapContainer.getHost(), lldapContainer.getMappedPort(3890)));
            registry.add("spring.ldap.base", () -> "uid=admin,ou=people,dc=example,dc=com");
            registry.add("spring.ldap.username", () -> "uid=admin,ou=people,dc=example,dc=com");
            registry.add("spring.ldap.password", () -> "password");
        };
    }
}
