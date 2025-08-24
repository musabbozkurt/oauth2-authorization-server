package mb.oauth2authorizationserver.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration
public class MinioTestConfiguration {

    private static final MinIOContainer minio = new MinIOContainer(DockerImageName.parse("minio/minio:RELEASE.2025-07-23T15-54-02Z-cpuv1"))
            .withEnv("MINIO_ACCESS_KEY", "minio-admin")
            .withEnv("MINIO_SECRET_KEY", "minio-password")
            .withCommand("server /data")
            .withExposedPorts(9000)
            .withReuse(true);

    static {
        minio.start();
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
