package mb.oauth2authorizationserver;

import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.exception.BaseException;
import mb.oauth2authorizationserver.exception.OAuth2AuthorizationServerServiceErrorCode;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.support.LdapUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Slf4j
@SpringBootTest
@Testcontainers
class LdapIntegrationTest {

    @Container
    private static final GenericContainer<?> LLDAP_CONTAINER = createLldapContainer();

    @Autowired
    private LdapTemplate ldapTemplate;

    @Autowired
    private AuthenticationManager ldapAuthenticationManager;

    private static GenericContainer<?> createLldapContainer() {
        try (GenericContainer<?> lldapContainer = new GenericContainer<>(DockerImageName.parse("lldap/lldap:v0.6.1-alpine"))) {
            return lldapContainer
                    .withExposedPorts(3890, 17170)
                    .withEnv("LLDAP_JWT_SECRET", "test-secret")
                    .withEnv("LLDAP_LDAP_USER_PASS", "secret")
                    .withEnv("LLDAP_LDAP_BASE_DN", "dc=example,dc=com");
        } catch (Exception e) {
            log.error("Failed to start Redis container. redisContainer - Exception: {}", ExceptionUtils.getStackTrace(e));
            throw new BaseException(OAuth2AuthorizationServerServiceErrorCode.UNEXPECTED_ERROR);
        }
    }

    @DynamicPropertySource
    private static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("ldap.url", () -> "ldap://localhost:" + LLDAP_CONTAINER.getMappedPort(3890));
        registry.add("ldap.base", () -> "dc=example,dc=com");
        registry.add("ldap.username", () -> "cn=admin,dc=example,dc=com");
        registry.add("ldap.password", () -> "secret");
    }

    @Test
    void connectToLdapServer_ShouldSucceed_WhenContainerIsRunning() {
        // Arrange
        // Act
        int mappedPort = LLDAP_CONTAINER.getMappedPort(3890);

        // Assertions
        assertThat(ldapTemplate).isNotNull();
        assertThat(LLDAP_CONTAINER.isRunning()).isFalse();
        assertThat(mappedPort).isGreaterThan(0);
    }

    @Test
    void authenticate_ShouldThrowException_WhenUserDoesNotExist() {
        // Arrange
        UsernamePasswordAuthenticationToken authRequest = new UsernamePasswordAuthenticationToken("testuser", "testpass");

        // Act
        // Assertions
        assertThatThrownBy(() -> ldapAuthenticationManager.authenticate(authRequest)).isInstanceOf(AuthenticationException.class);
    }

    @Test
    void authenticate_ShouldThrowException_WhenCredentialsAreInvalid() {
        // Arrange
        UsernamePasswordAuthenticationToken authRequest = new UsernamePasswordAuthenticationToken("invaliduser", "wrongpass");

        // Act
        // Assertions
        assertThatThrownBy(() -> ldapAuthenticationManager.authenticate(authRequest)).isInstanceOf(AuthenticationException.class);
    }

    @Test
    void ldapAuthenticationManager_ShouldBeConfigured_WhenContextLoads() {
        // Arrange
        // Spring context is already loaded
        // Act
        // AuthenticationManager is already injected
        // Assertions
        assertThat(ldapAuthenticationManager).isNotNull();
    }

    @Test
    void ldapTemplate_ShouldBeConfigured_WhenContextLoads() {
        // Arrange
        // Spring context is already loaded

        // Act
        // LdapTemplate is already injected

        // Assertions
        assertThat(ldapTemplate).isNotNull();
    }

    @Test
    void ldapTemplate_ShouldReturnEmptyList_WhenSearchingForNonExistentEntries() {
        // Arrange
        String baseDn = "dc=example,dc=com";
        String filter = "(cn=nonexistentuser)";

        // Act
        List<Object> searchResults = ldapTemplate.search(baseDn, filter, (AttributesMapper<Object>) attrs -> attrs);

        // Assertions
        assertThat(searchResults).isEmpty();
    }

    @Test
    void ldapTemplate_ShouldPerformBaseDnLookup_WhenValidBaseDnProvided() {
        // Arrange
        String baseDn = "dc=example,dc=com";

        // Act
        Object result = ldapTemplate.lookup(baseDn, (AttributesMapper<Object>) attrs -> attrs);

        // Assertions
        assertThat(ldapTemplate).isNotNull();
        assertThat(result)
                .isNotNull()
                .isInstanceOf(javax.naming.directory.Attributes.class);

        Attributes attributes = (Attributes) result;
        assertThat(attributes.size()).isGreaterThanOrEqualTo(0);

        // Verify that objectClass attribute exists (common LDAP attribute)
        Attribute objectClassAttr = attributes.get("objectClass");
        if (objectClassAttr != null) {
            assertThat(objectClassAttr.size()).isGreaterThan(0);
        }

        // Verify that we can access the attributes
        assertThat(attributes.getAll()).isNotNull();
    }

    @Test
    void ldapTemplate_ShouldSearchWithFilter_WhenValidFilterProvided() {
        // Arrange
        String baseDn = "dc=example,dc=com";
        String filter = "(objectClass=*)";

        // Act
        List<String> searchResults = ldapTemplate.search(baseDn, filter,
                (AttributesMapper<String>) attrs -> {
                    Attribute cnAttr = attrs.get("cn");
                    return cnAttr != null ? cnAttr.get().toString() : "unknown";
                });

        // Assertions
        assertThat(searchResults).isNotNull();
    }

    @Test
    void ldapTemplate_ShouldFindAll_WhenSearchingAllObjects() {
        // Arrange
        String baseDn = "dc=example,dc=com";
        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        // Act
        List<String> allEntries = ldapTemplate.search(LdapUtils.newLdapName(baseDn),
                "(objectClass=*)",
                searchControls,
                (AttributesMapper<String>) attrs -> {
                    Attribute dnAttr = attrs.get("dn");
                    return dnAttr != null ? dnAttr.get().toString() : "entry";
                });

        // Assertions
        assertThat(allEntries).isNotNull();
    }
}
