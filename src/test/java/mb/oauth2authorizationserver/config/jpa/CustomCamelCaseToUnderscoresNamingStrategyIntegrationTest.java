package mb.oauth2authorizationserver.config.jpa;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import mb.oauth2authorizationserver.OAuth2AuthorizationServerApplication;
import mb.oauth2authorizationserver.config.OracleTestConfiguration;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

interface TurkishSearchTestRepository extends JpaRepository<TurkishSearchTestEntity, Long> {

    List<TurkishSearchTestEntity> findByTextContent(String textContent);

    List<TurkishSearchTestEntity> findByTextContentLike(String pattern);
}

@TestPropertySource(
        properties = {
                "spring.flyway.enabled=false",
                "spring.profiles.active=test",
                "oracle-schema-name=oauth2",
                "namespace=authorization_server"
        }
)
@SpringBootTest(classes = {
        OAuth2AuthorizationServerApplication.class,
        OracleTestConfiguration.class,
        TurkishSearchJpaTestConfiguration.class
})
class CustomCamelCaseToUnderscoresNamingStrategyIntegrationTest {

    private static final List<TurkishCharTestCase> TURKISH_CHAR_TEST_CASES = List.of(
            new TurkishCharTestCase("ç", "çiçek", "ÇİÇEK", "%çiç%"),
            new TurkishCharTestCase("ğ", "dağ", "DAĞ", "%dağ%"),
            new TurkishCharTestCase("ı", "ışık", "IŞIK", "%ışı%"),
            new TurkishCharTestCase("i", "istanbul", "İSTANBUL", "%ista%"),
            new TurkishCharTestCase("ö", "gök", "GÖK", "%gök%"),
            new TurkishCharTestCase("ş", "şeker", "ŞEKER", "%şek%"),
            new TurkishCharTestCase("ü", "gül", "GÜL", "%gül%")
    );

    @Autowired
    private HikariDataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TurkishSearchTestRepository turkishSearchTestRepository;

    @Autowired
    private CustomCamelCaseToUnderscoresNamingStrategyFactory namingStrategyFactory;

    @Test
    void create_ShouldConfigureHikariInitSqlWithPlSqlAnonymousBlock_WhenDataSourceIsProvided() {
        // Arrange
        // Dependencies are injected by Spring Boot Test context automatically

        // Act
        // Assert that the PL/SQL execution block was correctly applied to the data source configuration
        String initSql = dataSource.getConnectionInitSql();

        // Assertions
        assertThat(initSql)
                .isNotNull()
                .startsWith("BEGIN ")
                .contains("ALTER SESSION SET CURRENT_SCHEMA")
                .contains("NLS_COMP = LINGUISTIC")
                .contains("NLS_SORT = GENERIC_M_AI")
                .endsWith("END;");
    }

    @Test
    void create_ShouldApplySchemaAndTurkishLinguisticSettingsToOracleSession_WhenConnectionsAreBorrowed() throws SQLException {
        // Arrange
        // Read the naming strategy without mutating the active, running Hikari pool instance
        CustomPhysicalNamingStrategy strategy = namingStrategyFactory.create(false);
        String expectedSchema = strategy.resolveSchema("OAUTH2");

        // Act
        String currentSchema;
        String nlsComp;
        String nlsSort;

        // Run verification queries on an active database session produced by Hikari
        try (Connection connection = dataSource.getConnection()) {
            // 1. Verify Current Schema Configuration
            currentSchema = jdbcTemplate.queryForObject("SELECT sys_context('USERENV', 'CURRENT_SCHEMA') FROM dual", String.class);

            // 2. Verify Turkish Case-Insensitive Linguistic Settings
            nlsComp = jdbcTemplate.queryForObject("SELECT value FROM nls_session_parameters WHERE parameter = 'NLS_COMP'", String.class);
            nlsSort = jdbcTemplate.queryForObject("SELECT value FROM nls_session_parameters WHERE parameter = 'NLS_SORT'", String.class);
        }

        // Assertions
        assertThat(currentSchema).isEqualToIgnoringCase(expectedSchema);
        assertThat(nlsComp).isEqualTo("LINGUISTIC");
        assertThat(nlsSort).isEqualTo("GENERIC_M_AI");
    }

    @Test
    void search_ShouldFindAllTurkishCharacters_WhenLinguisticSettingsAreActive() {
        // Arrange
        // Create a temporary table for isolation (Oracle syntax)
        jdbcTemplate.execute("CREATE TABLE turkish_test (id NUMBER, text_content VARCHAR2(100))");

        try {
            // Insert sample records containing all Turkish character case combinations
            int id = 1;
            for (TurkishCharTestCase testCase : TURKISH_CHAR_TEST_CASES) {
                jdbcTemplate.update("INSERT INTO turkish_test (id, text_content) VALUES (?, ?)", id++, testCase.lowercaseWord());
                jdbcTemplate.update("INSERT INTO turkish_test (id, text_content) VALUES (?, ?)", id++, testCase.uppercaseWord());
            }

            // Act
            // Search each Turkish character with exact and LIKE conditions via plain SQL
            for (TurkishCharTestCase testCase : TURKISH_CHAR_TEST_CASES) {
                Integer exactLowercaseCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM turkish_test WHERE text_content = ?",
                        Integer.class,
                        testCase.lowercaseWord()
                );
                Integer exactUppercaseCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM turkish_test WHERE text_content = ?",
                        Integer.class,
                        testCase.uppercaseWord()
                );
                Integer likeCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM turkish_test WHERE text_content LIKE ?",
                        Integer.class,
                        testCase.likePattern()
                );

                // Assertions
                // Exact match folds case for Turkish letter '%s' -> matches both '%s' and '%s'
                assertThat(exactLowercaseCount)
                        .as("exact lowercase search for '%s'", testCase.letter())
                        .isEqualTo(2);
                assertThat(exactUppercaseCount)
                        .as("exact uppercase search for '%s'", testCase.letter())
                        .isEqualTo(2);
                // Plain LIKE works transparently via NLS_COMP=LINGUISTIC + NLS_SORT=GENERIC_M_AI
                assertThat(likeCount)
                        .as("LIKE search for '%s'", testCase.letter())
                        .isEqualTo(2);
            }
        } finally {
            // Clean up the database environment after execution
            jdbcTemplate.execute("DROP TABLE turkish_test");
        }
    }

    private record TurkishCharTestCase(String letter, String lowercaseWord, String uppercaseWord, String likePattern) {
    }

    @Nested
    @Transactional
    class TurkishSearchQueryTest {

        @BeforeEach
        void seedTurkishSearchData() {
            turkishSearchTestRepository.deleteAll();

            // Assign IDs explicitly — records are immutable so Hibernate cannot mutate @GeneratedValue fields after insert
            long id = 1;
            List<TurkishSearchTestEntity> entities = new ArrayList<>();
            for (TurkishCharTestCase testCase : TURKISH_CHAR_TEST_CASES) {
                entities.add(new TurkishSearchTestEntity(id++, testCase.lowercaseWord()));
                entities.add(new TurkishSearchTestEntity(id++, testCase.uppercaseWord()));
            }
            // Use persist() — saveAll() calls merge() for assigned IDs, which requires a no-arg constructor on records
            entities.forEach(entityManager::persist);
            entityManager.flush();
        }

        @Test
        void jpql_ShouldFindAllTurkishCharacters_WhenLinguisticSettingsAreActive() {
            for (TurkishCharTestCase testCase : TURKISH_CHAR_TEST_CASES) {
                // Arrange
                // Seed data is prepared in @BeforeEach with lowercase and uppercase variants per Turkish letter

                // Act
                // JPQL exact match with lowercase search term
                List<TurkishSearchTestEntity> exactLowercaseMatches = entityManager
                        .createQuery("SELECT e FROM TurkishSearchTestEntity e WHERE e.textContent = :text", TurkishSearchTestEntity.class)
                        .setParameter("text", testCase.lowercaseWord())
                        .getResultList();

                // JPQL exact match with uppercase search term
                List<TurkishSearchTestEntity> exactUppercaseMatches = entityManager
                        .createQuery("SELECT e FROM TurkishSearchTestEntity e WHERE e.textContent = :text", TurkishSearchTestEntity.class)
                        .setParameter("text", testCase.uppercaseWord())
                        .getResultList();

                // JPQL fuzzy search via LIKE
                List<TurkishSearchTestEntity> likeMatches = entityManager
                        .createQuery("SELECT e FROM TurkishSearchTestEntity e WHERE e.textContent LIKE :pattern", TurkishSearchTestEntity.class)
                        .setParameter("pattern", testCase.likePattern())
                        .getResultList();

                // Assertions
                assertThat(exactLowercaseMatches)
                        .as("JPQL exact lowercase for '%s'", testCase.letter())
                        .hasSize(2);
                assertThat(exactUppercaseMatches)
                        .as("JPQL exact uppercase for '%s'", testCase.letter())
                        .hasSize(2);
                assertThat(likeMatches)
                        .as("JPQL LIKE for '%s'", testCase.letter())
                        .hasSize(2);
            }
        }

        @Test
        void criteria_ShouldFindAllTurkishCharacters_WhenLinguisticSettingsAreActive() {
            CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();

            for (TurkishCharTestCase testCase : TURKISH_CHAR_TEST_CASES) {
                // Arrange
                // Criteria queries are built per Turkish letter from seeded lowercase/uppercase pairs

                // Act
                // Criteria exact match with lowercase search term
                CriteriaQuery<TurkishSearchTestEntity> exactLowercaseQuery = criteriaBuilder.createQuery(TurkishSearchTestEntity.class);
                Root<TurkishSearchTestEntity> exactLowercaseRoot = exactLowercaseQuery.from(TurkishSearchTestEntity.class);
                exactLowercaseQuery.select(exactLowercaseRoot).where(criteriaBuilder.equal(exactLowercaseRoot.get("textContent"), testCase.lowercaseWord()));
                List<TurkishSearchTestEntity> exactLowercaseMatches = entityManager.createQuery(exactLowercaseQuery).getResultList();

                // Criteria exact match with uppercase search term
                CriteriaQuery<TurkishSearchTestEntity> exactUppercaseQuery = criteriaBuilder.createQuery(TurkishSearchTestEntity.class);
                Root<TurkishSearchTestEntity> exactUppercaseRoot = exactUppercaseQuery.from(TurkishSearchTestEntity.class);
                exactUppercaseQuery.select(exactUppercaseRoot).where(criteriaBuilder.equal(exactUppercaseRoot.get("textContent"), testCase.uppercaseWord()));
                List<TurkishSearchTestEntity> exactUppercaseMatches = entityManager.createQuery(exactUppercaseQuery).getResultList();

                // Criteria fuzzy search via LIKE
                CriteriaQuery<TurkishSearchTestEntity> likeQuery = criteriaBuilder.createQuery(TurkishSearchTestEntity.class);
                Root<TurkishSearchTestEntity> likeRoot = likeQuery.from(TurkishSearchTestEntity.class);
                likeQuery.select(likeRoot).where(criteriaBuilder.like(likeRoot.get("textContent"), testCase.likePattern()));
                List<TurkishSearchTestEntity> likeMatches = entityManager.createQuery(likeQuery).getResultList();

                // Assertions
                assertThat(exactLowercaseMatches)
                        .as("Criteria exact lowercase for '%s'", testCase.letter())
                        .hasSize(2);
                assertThat(exactUppercaseMatches)
                        .as("Criteria exact uppercase for '%s'", testCase.letter())
                        .hasSize(2);
                assertThat(likeMatches)
                        .as("Criteria LIKE for '%s'", testCase.letter())
                        .hasSize(2);
            }
        }

        @Test
        void hibernate_ShouldFindAllTurkishCharacters_WhenLinguisticSettingsAreActive() {
            // Arrange
            Session session = entityManager.unwrap(Session.class);

            for (TurkishCharTestCase testCase : TURKISH_CHAR_TEST_CASES) {
                // Act
                // Hibernate HQL exact match with lowercase search term
                List<TurkishSearchTestEntity> exactLowercaseMatches = session
                        .createQuery("FROM TurkishSearchTestEntity e WHERE e.textContent = :text", TurkishSearchTestEntity.class)
                        .setParameter("text", testCase.lowercaseWord())
                        .list();

                // Hibernate HQL exact match with uppercase search term
                List<TurkishSearchTestEntity> exactUppercaseMatches = session
                        .createQuery("FROM TurkishSearchTestEntity e WHERE e.textContent = :text", TurkishSearchTestEntity.class)
                        .setParameter("text", testCase.uppercaseWord())
                        .list();

                // Hibernate HQL fuzzy search via LIKE
                List<TurkishSearchTestEntity> likeMatches = session
                        .createQuery("FROM TurkishSearchTestEntity e WHERE e.textContent LIKE :pattern", TurkishSearchTestEntity.class)
                        .setParameter("pattern", testCase.likePattern())
                        .list();

                // Assertions
                assertThat(exactLowercaseMatches)
                        .as("Hibernate exact lowercase for '%s'", testCase.letter())
                        .hasSize(2);
                assertThat(exactUppercaseMatches)
                        .as("Hibernate exact uppercase for '%s'", testCase.letter())
                        .hasSize(2);
                assertThat(likeMatches)
                        .as("Hibernate LIKE for '%s'", testCase.letter())
                        .hasSize(2);
            }
        }

        @Test
        void springDataDerivedQuery_ShouldFindAllTurkishCharacters_WhenLinguisticSettingsAreActive() {
            for (TurkishCharTestCase testCase : TURKISH_CHAR_TEST_CASES) {
                // Arrange
                // Spring Data derived queries run against seeded lowercase/uppercase pairs

                // Act
                List<TurkishSearchTestEntity> exactLowercaseMatches = turkishSearchTestRepository.findByTextContent(testCase.lowercaseWord());
                List<TurkishSearchTestEntity> exactUppercaseMatches = turkishSearchTestRepository.findByTextContent(testCase.uppercaseWord());
                List<TurkishSearchTestEntity> likeMatches = turkishSearchTestRepository.findByTextContentLike(testCase.likePattern());

                // Assertions
                assertThat(exactLowercaseMatches)
                        .as("Spring Data exact lowercase for '%s'", testCase.letter())
                        .hasSize(2);
                assertThat(exactUppercaseMatches)
                        .as("Spring Data exact uppercase for '%s'", testCase.letter())
                        .hasSize(2);
                assertThat(likeMatches)
                        .as("Spring Data LIKE for '%s'", testCase.letter())
                        .hasSize(2);
            }
        }
    }

    @Nested
    class WithNamespaceAndDevProfileContextTest {

        @Test
        void resolveSchema_ShouldAppendNamespaceSuffix_WhenEnvironmentIsNonProduction() {
            // Arrange
            // Explicitly test the naming strategy behavior as a plain Java object (POJO) to avoid context pollution or ORA errors.
            // Arguments correspond to: (implicitNamingStrategy, namespace, activeProfile, enabled)
            CustomPhysicalNamingStrategy strategy = new CustomPhysicalNamingStrategy(null, "featurex", "dev", true);
            String inputSchema = "MY_SCHEMA";

            // Act
            // Verify that the underlying strategy appends your namespace suffix properly when profile != prod/stage
            String resolvedSchema = strategy.resolveSchema(inputSchema);

            // Assertions
            assertThat(resolvedSchema).isEqualTo("MY_SCHEMA_featurex");
        }
    }
}

@TestConfiguration
@EntityScan(basePackageClasses = TurkishSearchTestEntity.class)
@EnableJpaRepositories(basePackageClasses = TurkishSearchTestRepository.class)
class TurkishSearchJpaTestConfiguration {
}

@Entity
@Table(name = "turkish_search_test")
record TurkishSearchTestEntity(@Id
                               Long id,

                               @Column(nullable = false)
                               String textContent) {
}
