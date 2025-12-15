package mb.oauth2authorizationserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@TestConfiguration
public class MinioTestConfiguration {

    private static final MinIOContainer minio = new MinIOContainer(DockerImageName.parse("minio/minio:RELEASE.2025-09-07T16-13-09Z"))
            .withEnv("MINIO_ACCESS_KEY", "minio-admin")
            .withEnv("MINIO_SECRET_KEY", "minio-password")
            .withCommand("server /data")
            .withExposedPorts(9000)
            .withReuse(true);

    static {
        minio.start();

        assertThat(minio.isRunning()).isTrue();
        assertThat(minio.getMappedPort(9000)).isGreaterThan(0);

        // Register shutdown hook to stop the container when the JVM exits
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (minio.isRunning()) {
                log.info("Stopping LLDAP container.");
                minio.stop();
                log.info("LLDAP container stopped.");
            }
        }));
    }

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            TestPropertyValues values = TestPropertyValues.of(
                    "minio.accessKey=minio-admin",
                    "minio.secretKey=minio-password",
                    "minio.endpoint=%s".formatted("http://%s:%d".formatted(minio.getHost(), minio.getMappedPort(9000))),
                    "minio.bucket=test-bucket"
            );
            values.applyTo(applicationContext);
        }
    }
}
