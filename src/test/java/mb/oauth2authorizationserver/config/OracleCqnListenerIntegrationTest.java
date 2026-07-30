package mb.oauth2authorizationserver.config;

import oracle.jdbc.dcn.DatabaseChangeEvent;
import oracle.jdbc.dcn.TableChangeDescription;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestPropertySource(properties = "spring.flyway.enabled=false")
@SpringBootTest(classes = {OracleTestConfiguration.class, RedisTestConfiguration.class})
class OracleCqnListenerIntegrationTest {

    @Autowired
    private OracleCqnListener oracleCqnListener;

    @Test
    void onDatabaseChangeNotification_ShouldNotThrow_WhenEventHasTableChanges() {
        DatabaseChangeEvent event = mock(DatabaseChangeEvent.class);
        TableChangeDescription tableChangeDescription = mock(TableChangeDescription.class);
        when(event.getTableChangeDescription()).thenReturn(new TableChangeDescription[]{tableChangeDescription});

        assertDoesNotThrow(() -> oracleCqnListener.onDatabaseChangeNotification(event));
    }

    @Test
    void onDatabaseChangeNotification_ShouldNotThrow_WhenEventHasNoTableChanges() {
        DatabaseChangeEvent event = mock(DatabaseChangeEvent.class);
        when(event.getTableChangeDescription()).thenReturn(null);

        assertDoesNotThrow(() -> oracleCqnListener.onDatabaseChangeNotification(event));
    }

    @Test
    void onDatabaseChangeNotification_ShouldNotThrow_WhenEventHasEmptyTableChanges() {
        DatabaseChangeEvent event = mock(DatabaseChangeEvent.class);
        when(event.getTableChangeDescription()).thenReturn(new TableChangeDescription[0]);

        assertDoesNotThrow(() -> oracleCqnListener.onDatabaseChangeNotification(event));
    }
}
