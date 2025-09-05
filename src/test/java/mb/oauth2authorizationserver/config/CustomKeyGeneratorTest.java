package mb.oauth2authorizationserver.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(MockitoExtension.class)
class CustomKeyGeneratorTest {

    @InjectMocks
    private CustomKeyGenerator generator;

    @Test
    void generate_ShouldGenerateCacheKey_WhenParamsAreValid() throws NoSuchMethodException {
        // Arrange
        String userId = "123";
        Set<String> roles = Stream.of("role1", "role2").collect(Collectors.toSet());
        Method method = generator.getClass().getMethod("generate", Object.class, Method.class, Object[].class);

        // Act
        Object cacheKey = generator.generate(userId, method, userId, roles);

        // Assertions
        String[] parts = ((String) cacheKey).split("::");
        assertEquals(2, parts.length);
        assertEquals(userId, parts[0]);
        assertEquals(UUID.nameUUIDFromBytes(roles.stream().sorted().collect(Collectors.joining(",")).getBytes()), UUID.fromString(parts[1]));
    }

    @Test
    void generate_ShouldGenerateDifferentCacheKey_WhenRolesAreDifferent() throws NoSuchMethodException {
        // Arrange
        String userId = "123";
        Set<String> roles1 = Stream.of("role1", "role2").collect(Collectors.toSet());
        Set<String> roles2 = Stream.of("role2", "role1").collect(Collectors.toSet());
        Method method = generator.getClass().getMethod("generate", Object.class, Method.class, Object[].class);

        // Act
        Object cacheKey1 = generator.generate(userId, method, userId, roles1);
        Object cacheKey2 = generator.generate(userId, method, userId, roles2);

        // Assertions
        assertEquals(cacheKey1, cacheKey2);
    }

    @Test
    void generate_ShouldGenerateDifferentCacheKey_WhenUserIdIsDifferent() throws NoSuchMethodException {
        // Arrange
        String userId1 = "123";
        String userId2 = "456";
        Set<String> roles = Stream.of("role1", "role2").collect(Collectors.toSet());
        Method method = generator.getClass().getMethod("generate", Object.class, Method.class, Object[].class);

        // Act
        Object cacheKey1 = generator.generate(userId1, method, userId1, roles);
        Object cacheKey2 = generator.generate(userId2, method, userId2, roles);

        // Assertions
        assertNotEquals(cacheKey1, cacheKey2);
    }
}
