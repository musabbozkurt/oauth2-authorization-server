package mb.oauth2authorizationserver.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class CustomLdapPropertiesTest {

    private CustomLdapProperties customLdapProperties;

    @BeforeEach
    void setUp() {
        customLdapProperties = new CustomLdapProperties();
    }

    @Test
    void isValid_ShouldReturnTrue_WhenAllFieldsAreValid() {
        // Arrange
        customLdapProperties.setUrls(new String[]{"ldap://localhost:389"});
        customLdapProperties.setPassword("password123");
        customLdapProperties.setUserDn("cn=admin,dc=example,dc=com");
        customLdapProperties.setUserSearchBase("ou=users,dc=example,dc=com");
        customLdapProperties.setUserSearchFilter("(uid={0})");

        // Act
        boolean result = customLdapProperties.isValid();

        // Assertions
        assertTrue(result);
    }

    @Test
    void isValid_ShouldReturnFalse_WhenUrlsAreEmpty() {
        // Arrange
        customLdapProperties.setUrls(new String[]{});
        customLdapProperties.setPassword("password123");
        customLdapProperties.setUserDn("cn=admin,dc=example,dc=com");
        customLdapProperties.setUserSearchBase("ou=users,dc=example,dc=com");
        customLdapProperties.setUserSearchFilter("(uid={0})");

        // Act
        boolean result = customLdapProperties.isValid();

        // Assertions
        assertFalse(result);
    }

    @Test
    void isValid_ShouldReturnFalse_WhenPasswordIsNull() {
        // Arrange
        customLdapProperties.setUrls(new String[]{"ldap://localhost:389"});
        customLdapProperties.setPassword(null);
        customLdapProperties.setUserDn("cn=admin,dc=example,dc=com");
        customLdapProperties.setUserSearchBase("ou=users,dc=example,dc=com");
        customLdapProperties.setUserSearchFilter("(uid={0})");

        // Act
        boolean result = customLdapProperties.isValid();

        // Assertions
        assertFalse(result);
    }

    @Test
    void isValid_ShouldReturnFalse_WhenUserDnIsBlank() {
        // Arrange
        customLdapProperties.setUrls(new String[]{"ldap://localhost:389"});
        customLdapProperties.setPassword("password123");
        customLdapProperties.setUserDn("");
        customLdapProperties.setUserSearchBase("ou=users,dc=example,dc=com");
        customLdapProperties.setUserSearchFilter("(uid={0})");

        // Act
        boolean result = customLdapProperties.isValid();

        // Assertions
        assertFalse(result);
    }

    @Test
    void isValid_ShouldReturnFalse_WhenUserSearchBaseIsNull() {
        // Arrange
        customLdapProperties.setUrls(new String[]{"ldap://localhost:389"});
        customLdapProperties.setPassword("password123");
        customLdapProperties.setUserDn("cn=admin,dc=example,dc=com");
        customLdapProperties.setUserSearchBase(null);
        customLdapProperties.setUserSearchFilter("(uid={0})");

        // Act
        boolean result = customLdapProperties.isValid();

        // Assertions
        assertFalse(result);
    }

    @Test
    void isValid_ShouldReturnFalse_WhenUserSearchFilterIsNull() {
        // Arrange
        customLdapProperties.setUrls(new String[]{"ldap://localhost:389"});
        customLdapProperties.setPassword("password123");
        customLdapProperties.setUserDn("cn=admin,dc=example,dc=com");
        customLdapProperties.setUserSearchBase("ou=users,dc=example,dc=com");
        customLdapProperties.setUserSearchFilter(null);

        // Act
        boolean result = customLdapProperties.isValid();

        // Assertions
        assertFalse(result);
    }

    @Test
    void setUserDn_ShouldSetUserDn_WhenValidValueProvided() {
        // Arrange
        String expectedUserDn = "cn=admin,dc=example,dc=com";

        // Act
        customLdapProperties.setUserDn(expectedUserDn);

        // Assertions
        assertEquals(expectedUserDn, customLdapProperties.getUserDn());
    }

    @Test
    void getUserDn_ShouldReturnNull_WhenNotSet() {
        // Arrange
        // Act
        String result = customLdapProperties.getUserDn();

        // Assertions
        assertNull(result);
    }

    @Test
    void setUserSearchBase_ShouldSetUserSearchBase_WhenValidValueProvided() {
        // Arrange
        String expectedUserSearchBase = "ou=users,dc=example,dc=com";

        // Act
        customLdapProperties.setUserSearchBase(expectedUserSearchBase);

        // Assertions
        assertEquals(expectedUserSearchBase, customLdapProperties.getUserSearchBase());
    }

    @Test
    void getUserSearchBase_ShouldReturnNull_WhenNotSet() {
        // Arrange
        // Act
        String result = customLdapProperties.getUserSearchBase();

        // Assertions
        assertNull(result);
    }

    @Test
    void setUserSearchFilter_ShouldSetUserSearchFilter_WhenValidValueProvided() {
        // Arrange
        String expectedUserSearchFilter = "(uid={0})";

        // Act
        customLdapProperties.setUserSearchFilter(expectedUserSearchFilter);

        // Assertions
        assertEquals(expectedUserSearchFilter, customLdapProperties.getUserSearchFilter());
    }

    @Test
    void getUserSearchFilter_ShouldReturnNull_WhenNotSet() {
        // Arrange
        // Act
        String result = customLdapProperties.getUserSearchFilter();

        // Assertions
        assertNull(result);
    }
}
