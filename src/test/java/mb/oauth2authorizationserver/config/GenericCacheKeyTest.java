package mb.oauth2authorizationserver.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenericCacheKeyTest {

    @Test
    @DisplayName("Should generate identical hashes for identical payloads")
    void cacheKeyEqualityAndHashTest_ShouldBeEqual_WhenPayloadsAreIdentical() {
        // Arrange
        var roles = List.of("ROLE_USER", "ROLE_ADMIN");
        var permissions = Set.of("READ", "WRITE");
        var metadata = Map.of("region", "US", "tier", "premium");

        var query1 = new FakeQuery("Alice", "alice@example.com", roles, permissions, metadata);
        var fields1 = Set.of(FakeFields.NAME, FakeFields.EMAIL);

        var query2 = new FakeQuery("Alice", "alice@example.com", roles, permissions, metadata);
        var fields2 = Set.of(FakeFields.NAME, FakeFields.EMAIL);

        // Act
        var key1 = new GenericCacheKey<>(query1, fields1);
        var key2 = new GenericCacheKey<>(query2, fields2);

        // Assertions
        assertThat(key1)
                .isEqualTo(key2)
                .hasSameHashCodeAs(key2);
        assertThat(key1.toString()).hasToString(key2.toString());
    }

    @Test
    @DisplayName("Should generate different hashes when core text data differs")
    void cacheKeyDifferentiationTest_ShouldNotBeEqual_WhenQueryDataDiffers() {
        // Arrange
        var roles = List.of("ROLE_USER");
        var permissions = Set.of("READ");
        var metadata = Map.of("region", "US");

        var query1 = new FakeQuery("Alice", "alice@example.com", roles, permissions, metadata);
        var query2 = new FakeQuery("Bob", "bob@example.com", roles, permissions, metadata);
        var fields = Set.of(FakeFields.NAME);

        // Act
        var key1 = new GenericCacheKey<>(query1, fields);
        var key2 = new GenericCacheKey<>(query2, fields);

        // Assertions
        assertThat(key1).isNotEqualTo(key2);
        assertThat(key1.hashCode()).isNotEqualTo(key2.hashCode());
    }

    @Test
    @DisplayName("Should generate different hashes when list item order within query changes")
    void cacheKeyDifferentiationTest_ShouldNotBeEqual_WhenListOrderDiffers() {
        // Arrange
        var permissions = Set.of("READ");
        var metadata = Map.of("region", "US");

        // Java Lists care about order for equality and hashing
        var query1 = new FakeQuery("Alice", "alice@example.com", List.of("ROLE_USER", "ROLE_ADMIN"), permissions, metadata);
        var query2 = new FakeQuery("Alice", "alice@example.com", List.of("ROLE_ADMIN", "ROLE_USER"), permissions, metadata);
        var fields = Set.of(FakeFields.NAME);

        // Act
        var key1 = new GenericCacheKey<>(query1, fields);
        var key2 = new GenericCacheKey<>(query2, fields);

        // Assertions
        assertThat(key1).isNotEqualTo(key2);
        assertThat(key1.hashCode()).isNotEqualTo(key2.hashCode());
    }

    @Test
    @DisplayName("Should generate identical hashes when set item order within query changes")
    void cacheKeyEqualityAndHashTest_ShouldBeEqual_WhenSetOrderDiffersButElementsAreSame() {
        // Arrange
        var roles = List.of("ROLE_USER");
        var metadata = Map.of("region", "US");

        // Java Sets do NOT care about order for equality and hashing
        var query1 = new FakeQuery("Alice", "alice@example.com", roles, Set.of("READ", "WRITE"), metadata);
        var query2 = new FakeQuery("Alice", "alice@example.com", roles, Set.of("WRITE", "READ"), metadata);
        var fields = Set.of(FakeFields.NAME);

        // Act
        var key1 = new GenericCacheKey<>(query1, fields);
        var key2 = new GenericCacheKey<>(query2, fields);

        // Assertions
        assertThat(key1)
                .isEqualTo(key2)
                .hasSameHashCodeAs(key2);
    }

    @Test
    @DisplayName("Should generate different hashes when map metadata changes")
    void cacheKeyDifferentiationTest_ShouldNotBeEqual_WhenMapMetadataDiffers() {
        // Arrange
        var roles = List.of("ROLE_USER");
        var permissions = Set.of("READ");

        var query1 = new FakeQuery("Alice", "alice@example.com", roles, permissions, Map.of("region", "US"));
        var query2 = new FakeQuery("Alice", "alice@example.com", roles, permissions, Map.of("region", "EU"));
        var fields = Set.of(FakeFields.NAME);

        // Act
        var key1 = new GenericCacheKey<>(query1, fields);
        var key2 = new GenericCacheKey<>(query2, fields);

        // Assertions
        assertThat(key1).isNotEqualTo(key2);
        assertThat(key1.hashCode()).isNotEqualTo(key2.hashCode());
    }

    @Test
    @DisplayName("Should throw an exception if the query object is null")
    void nullQueryValidationTest_ShouldThrowException_WhenQueryIsNull() {
        // Arrange
        // Act
        // Assertions
        assertThatThrownBy(() -> new GenericCacheKey<>(null, Set.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Query payload cannot be null");
    }

    enum FakeFields {ID, NAME, EMAIL}

    // Dummy records updated with List, Set, and Map for strict collection testing
    record FakeQuery(String name,
                     String email,
                     List<String> roles,
                     Set<String> permissions,
                     Map<String, String> metadata) {
    }
}
