package mb.oauth2authorizationserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.grafana.LgtmStackContainer;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@TestConfiguration
public class LgtmStackTestConfiguration {

    @Bean
    @ServiceConnection
    public LgtmStackContainer lgtmStackContainer() {
        LgtmStackContainer lgtm = new LgtmStackContainer("grafana/otel-lgtm:0.13.0")
                .withExposedPorts(3000, 4317, 4318)
                .withEnv("OTEL_METRIC_EXPORT_INTERVAL", "500")
                .waitingFor(
                        Wait.forLogMessage(
                                ".*The OpenTelemetry collector and the Grafana LGTM stack are up and running.*\\s",
                                1
                        )
                )
                .withStartupTimeout(Duration.ofMinutes(2))
                .withReuse(true);

        lgtm.start();

        assertTrue(lgtm.isCreated());
        assertTrue(lgtm.isRunning());

        log.info("LGTM stack started. isCreated: {}, isRunning: {}", lgtm.isCreated(), lgtm.isRunning());

        return lgtm;
    }
}
