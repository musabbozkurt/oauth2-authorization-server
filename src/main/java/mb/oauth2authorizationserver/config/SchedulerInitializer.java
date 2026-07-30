package mb.oauth2authorizationserver.config;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.OracleStatement;
import oracle.jdbc.dcn.DatabaseChangeRegistration;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulerInitializer {

    private static final String REGISTERED_QUERY = "SELECT id FROM INTEGRATION_TABLE_NAME";

    private final DataSource dataSource;
    private final OracleCqnListener oracleCqnListener;

    private DatabaseChangeRegistration registration;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        registerCqn();
    }

    private void registerCqn() {
        log.info("Registering Oracle CQN listener for query [{}]", REGISTERED_QUERY);
        try (Connection connection = dataSource.getConnection()) {
            OracleConnection oracleConnection = connection.unwrap(OracleConnection.class);
            registration = oracleConnection.registerDatabaseChangeNotification(buildProperties());
            registration.addListener(oracleCqnListener);

            try (Statement statement = oracleConnection.createStatement()) {
                statement.unwrap(OracleStatement.class).setDatabaseChangeRegistration(registration);
                try (ResultSet resultSet = statement.executeQuery(REGISTERED_QUERY)) {
                    while (resultSet.next()) {
                        // Executing the query is what associates the table with the registration.
                    }
                }
            }

            log.info("Registered Oracle CQN registration {} on tables {}", registration.getRegId(), registration.getTables());
        } catch (Exception e) {
            registration = null;
            log.error("Failed to register Oracle CQN listener. Falling back to startup-time scheduling only. Exception: {}", ExceptionUtils.getStackTrace(e));
        }
    }

    private Properties buildProperties() {
        Properties properties = new Properties();
        // The driver opens the notification connection itself, so the database never has to
        // reach back to this process. Requires Oracle Database 19c or newer and a service name
        // format JDBC URL (@//host:port/service), otherwise registration fails with ORA-17287.
        properties.setProperty(OracleConnection.DCN_CLIENT_INIT_CONNECTION, Boolean.TRUE.toString());
        return properties;
    }

    @PreDestroy
    public void unregisterCqn() {
        if (registration == null) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.unwrap(OracleConnection.class).unregisterDatabaseChangeNotification(registration);
            log.info("Unregistered Oracle CQN registration {}", registration.getRegId());
        } catch (SQLException e) {
            log.error("Failed to unregister Oracle CQN registration {}. Exception: {}", registration.getRegId(), ExceptionUtils.getStackTrace(e));
        }
    }
}
