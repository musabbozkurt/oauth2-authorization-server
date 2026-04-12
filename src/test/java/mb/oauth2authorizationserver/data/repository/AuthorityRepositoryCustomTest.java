package mb.oauth2authorizationserver.data.repository;

import mb.oauth2authorizationserver.config.OracleCustomTestConfiguration;
import mb.oauth2authorizationserver.config.RedisCustomTestConfiguration;
import mb.oauth2authorizationserver.data.entity.Authority;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(
        properties = {
                "oracle-schema-name=oauth2",
                "spring.flyway.enabled=false",
                "namespace=authorization_server"
        })
@ContextConfiguration(
        initializers = {
                RedisCustomTestConfiguration.RedisInitializer.class,
                OracleCustomTestConfiguration.OracleInitializer.class
        }
)
class AuthorityRepositoryCustomTest {

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
