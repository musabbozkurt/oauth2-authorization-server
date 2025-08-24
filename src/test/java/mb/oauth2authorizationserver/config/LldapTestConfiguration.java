package mb.oauth2authorizationserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
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

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            TestPropertyValues values = TestPropertyValues.of(
                    "spring.ldap.urls=ldap://%s:%d".formatted(lldapContainer.getHost(), lldapContainer.getMappedPort(3890)),
                    "spring.ldap.password=password",
                    "spring.ldap.user-dn=uid=admin,ou=people,dc=example,dc=com",
                    "spring.ldap.user-search-base=ou=people,dc=example,dc=com",
                    "spring.ldap.user-search-filter=(cn={0})"
            );
            values.applyTo(applicationContext);
        }
    }
}
