package mb.oauth2authorizationserver.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LdapConfiguredConditionTest {

    private final LdapConfiguredCondition condition = new LdapConfiguredCondition();
    private final AnnotatedTypeMetadata metadata = mock(AnnotatedTypeMetadata.class);

    @Test
    void matches_ShouldReturnTrue_WhenAllLdapPropertiesAreValid() {
        // Arrange
        ConditionContext context = createContext("ldap://localhost:389", "password123", "cn=admin,dc=example,dc=com", "ou=users,dc=example,dc=com", "(uid={0})");

        // Act
        // Assertions
        assertTrue(condition.matches(context, metadata));
    }

    @Test
    void matches_ShouldReturnFalse_WhenNoLdapPropertiesExist() {
        // Arrange
        ConditionContext context = mockContext(new MockEnvironment());

        // Act
        // Assertions
        assertFalse(condition.matches(context, metadata));
    }

    @Test
    void matches_ShouldReturnFalse_WhenUrlsIsBlank() {
        // Arrange
        ConditionContext context = createContext("", "password123", "cn=admin,dc=example,dc=com", "ou=users,dc=example,dc=com", "(uid={0})");

        // Act
        // Assertions
        assertFalse(condition.matches(context, metadata));
    }

    @Test
    void matches_ShouldReturnFalse_WhenPasswordIsBlank() {
        // Arrange
        ConditionContext context = createContext("ldap://localhost:389", "", "cn=admin,dc=example,dc=com", "ou=users,dc=example,dc=com", "(uid={0})");

        // Act
        // Assertions
        assertFalse(condition.matches(context, metadata));
    }

    @Test
    void matches_ShouldReturnFalse_WhenUserDnIsBlank() {
        // Arrange
        ConditionContext context = createContext("ldap://localhost:389", "password123", "", "ou=users,dc=example,dc=com", "(uid={0})");

        // Act
        // Assertions
        assertFalse(condition.matches(context, metadata));
    }

    @Test
    void matches_ShouldReturnFalse_WhenUserSearchBaseIsBlank() {
        // Arrange
        ConditionContext context = createContext("ldap://localhost:389", "password123", "cn=admin,dc=example,dc=com", "", "(uid={0})");

        // Act
        // Assertions
        assertFalse(condition.matches(context, metadata));
    }

    @Test
    void matches_ShouldReturnFalse_WhenUserSearchFilterIsBlank() {
        // Arrange
        ConditionContext context = createContext("ldap://localhost:389", "password123", "cn=admin,dc=example,dc=com", "ou=users,dc=example,dc=com", "");

        // Act
        // Assertions
        assertFalse(condition.matches(context, metadata));
    }

    @Test
    void matches_ShouldReturnFalse_WhenOnlyUrlsIsProvided() {
        // Arrange
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.ldap.urls", "ldap://localhost:389");
        ConditionContext context = mockContext(env);

        // Act
        // Assertions
        assertFalse(condition.matches(context, metadata));
    }

    private ConditionContext createContext(String urls, String password, String userDn, String userSearchBase, String userSearchFilter) {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.ldap.urls", urls);
        env.setProperty("spring.ldap.password", password);
        env.setProperty("spring.ldap.user-dn", userDn);
        env.setProperty("spring.ldap.user-search-base", userSearchBase);
        env.setProperty("spring.ldap.user-search-filter", userSearchFilter);
        return mockContext(env);
    }

    private ConditionContext mockContext(MockEnvironment env) {
        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(env);
        return context;
    }
}
