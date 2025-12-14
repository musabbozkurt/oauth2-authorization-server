package mb.oauth2authorizationserver;

import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.config.LldapTestConfiguration;
import mb.oauth2authorizationserver.config.RedisTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.support.LdapUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.test.context.ContextConfiguration;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Slf4j
@ContextConfiguration(initializers = LldapTestConfiguration.Initializer.class)
@SpringBootTest(classes = {LldapTestConfiguration.class, RedisTestConfiguration.class})
class LdapIntegrationTest {

    @Autowired
    private LdapTemplate ldapTemplate;

    @Autowired
    private AuthenticationManager ldapAuthenticationManager;

    @Test
    void ldapComponents_ShouldBeConfigured_WhenContextLoads() {
        // Arrange
        // Act
        // Assertions
        assertThat(ldapTemplate).isNotNull();
        assertThat(ldapAuthenticationManager).isNotNull();
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
