package mb.oauth2authorizationserver.config;

import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = GenericCacheKeyIntegrationTest.TestConfig.class)
class GenericCacheKeyIntegrationTest {

    @Autowired
    private DummySearchService searchService;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCache() {
        Objects.requireNonNull(cacheManager.getCache("userSearches")).clear();
        searchService.resetCallCount();
    }

    @Test
    @DisplayName("Should return cached result on second invocation when identical key context is requested")
    void searchUsers_ShouldReturnCachedResult_WhenCalledWithIdenticalQueryAndFields() {
        // Arrange
        List<String> roles = List.of("ROLE_USER", "ROLE_ADMIN");
        FakeQueryDto query = new FakeQueryDto("john_doe", "active", roles);
        Set<String> returnFields = Set.of("id", "email");
        GenericCacheKey<FakeQueryDto, Set<String>> expectedKey = new GenericCacheKey<>(query, returnFields);

        // Act
        FakeResultDto firstResult = searchService.searchUsers(query, returnFields);
        FakeResultDto secondResult = searchService.searchUsers(query, returnFields);

        // Assertions
        assertThat(firstResult)
                .isNotNull()
                .isEqualTo(secondResult);

        // Verifies the target method bypassed processing on the second call
        assertThat(searchService.getCallCount()).isEqualTo(1);

        // Verifies the object cache footprint exists in Spring's CacheManager natively
        var cachedValue = Objects.requireNonNull(cacheManager.getCache("userSearches")).get(expectedKey);
        assertThat(cachedValue).isNotNull();
        assertThat(cachedValue.get()).isEqualTo(firstResult);
    }

    @Test
    @DisplayName("Should execute service logic twice when called with different query parameters")
    void searchUsers_ShouldNotReturnCachedResult_WhenCalledWithDifferentQueries() {
        // Arrange
        List<String> roles = List.of("ROLE_USER");
        FakeQueryDto query1 = new FakeQueryDto("john_doe", "active", roles);
        FakeQueryDto query2 = new FakeQueryDto("jane_doe", "active", roles); // Different username
        Set<String> returnFields = Set.of("id", "email");

        // Act
        FakeResultDto firstResult = searchService.searchUsers(query1, returnFields);
        FakeResultDto secondResult = searchService.searchUsers(query2, returnFields);

        // Assertions
        assertThat(firstResult).isNotEqualTo(secondResult);
        assertThat(searchService.getCallCount()).isEqualTo(2); // Should execute both times
    }

    @Test
    @DisplayName("Should execute service logic twice when called with different return fields")
    void searchUsers_ShouldNotReturnCachedResult_WhenCalledWithDifferentFields() {
        // Arrange
        List<String> roles = List.of("ROLE_USER");
        FakeQueryDto query = new FakeQueryDto("john_doe", "active", roles);
        Set<String> fields1 = Set.of("id");
        Set<String> fields2 = Set.of("id", "email"); // Different projection fields

        // Act
        FakeResultDto firstResult = searchService.searchUsers(query, fields1);
        FakeResultDto secondResult = searchService.searchUsers(query, fields2);

        // Assertions
        assertThat(searchService.getCallCount()).isEqualTo(2); // Must not hit cache for different fields
        assertThat(firstResult).isNotEqualTo(secondResult);
    }

    @Test
    @DisplayName("Should execute service logic twice when list order inside query changes")
    void searchUsers_ShouldNotReturnCachedResult_WhenRoleListOrderDiffers() {
        // Arrange
        FakeQueryDto query1 = new FakeQueryDto("john_doe", "active", List.of("ROLE_USER", "ROLE_ADMIN"));
        FakeQueryDto query2 = new FakeQueryDto("john_doe", "active", List.of("ROLE_ADMIN", "ROLE_USER")); // Permuted list
        Set<String> returnFields = Set.of("id");

        // Act
        searchService.searchUsers(query1, returnFields);
        searchService.searchUsers(query2, returnFields);

        // Assertions
        assertThat(searchService.getCallCount()).isEqualTo(2); // Lists with different orders are distinct keys
    }

    @Test
    @DisplayName("Should handle concurrent read hits safely and hit the cache across parallel threads")
    void searchUsers_ShouldExecuteOnceAndCacheSafely_WhenInvokedByMultipleThreadsConcurrently() throws InterruptedException {
        // Arrange
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        FakeQueryDto query = new FakeQueryDto("concurrent_user", "active", List.of("ROLE_USER"));
        Set<String> returnFields = Set.of("id", "username");

        // Act & Auto-Close
        try (ExecutorService executorService = Executors.newFixedThreadPool(threadCount)) {
            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        startLatch.await(); // Hold all threads up until ready
                        searchService.searchUsers(query, returnFields);
                    } catch (InterruptedException _) {
                        Thread.currentThread().interrupt();
                    } finally {
                        finishLatch.countDown();
                    }
                });
            }

            startLatch.countDown(); // Released simultaneously!
            finishLatch.await();    // Wait for all worker threads to complete execution
        } // executorService.close() is called automatically right here

        // Assertions
        assertThat(searchService.getCallCount()).isLessThanOrEqualTo(2);
    }

    // --- INNER TEST INFRASTRUCTURE ---

    @Configuration
    @EnableCaching
    static class TestConfig {

        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("userSearches");
        }

        @Bean
        public DummySearchService dummySearchService() {
            return new DummySearchService();
        }
    }

    @Getter
    static class DummySearchService {
        // Thread-safe wrapper around a volatile int
        private final AtomicInteger callCount = new AtomicInteger(0);

        @Cacheable(
                value = "userSearches",
                sync = true, // Enforces sequential execution (Thread-Safe Cache)
                key = "new mb.oauth2authorizationserver.config.GenericCacheKey(#queryDto, #fields)"
        )
        public FakeResultDto searchUsers(FakeQueryDto queryDto, Set<String> fields) {
            // Atomically increments the current value by 1
            callCount.incrementAndGet();

            String userId = fields.contains("id") ? UUID.randomUUID().toString() : null;
            String username = fields.contains("username") ? queryDto.username() : "masked_username";
            String email = fields.contains("email") ? "test@example.com" : null;

            return new FakeResultDto(userId, username, email);
        }

        public int getCallCount() {
            return callCount.get();
        }

        public void resetCallCount() {
            this.callCount.set(0);
        }
    }

    record FakeQueryDto(String username, String status, List<String> roles) {
    }

    record FakeResultDto(String userId, String username, String email) {
    }
}
