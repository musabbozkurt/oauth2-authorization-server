package mb.oauth2authorizationserver.data.repository;

import mb.oauth2authorizationserver.config.OracleTestConfiguration;
import mb.oauth2authorizationserver.config.RedisTestConfiguration;
import mb.oauth2authorizationserver.data.entity.Authority;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "spring.flyway.enabled=false")
@SpringBootTest(classes = {OracleTestConfiguration.class, RedisTestConfiguration.class})
class AuthorityRepositoryTest {

    @Autowired
    private AuthorityRepository authorityRepository;

    @BeforeAll
    void setUp() {
        authorityRepository.saveAll(
                Instancio.ofList(Authority.class)
                        .size(10)
                        .supply(field(Authority::getId), () -> null)  // 👈 ensure ID is null
                        .create()
        );
    }

    @Test
    void findAll_ShouldReturnTenAuthorities_WhenPersisted() {
        assertThat(authorityRepository.findAll())
                .hasSize(10)
                .allSatisfy(authority -> {
                            assertThat(authority.getId()).isNotNull();
                            assertThat(authority.getAuthority()).isNotBlank();
                        }
                );
    }
}
