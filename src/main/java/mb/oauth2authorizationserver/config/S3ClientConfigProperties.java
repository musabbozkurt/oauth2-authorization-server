package mb.oauth2authorizationserver.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Setter
@Configuration
@ConfigurationProperties(prefix = "minio")
@ConditionalOnProperty(value = {"minio.endpoint", "minio.accessKey", "minio.secretKey", "minio.bucket"})
public class S3ClientConfigProperties {

    private String endpoint;
    private String accessKey;
    private String secretKey;

    @Getter
    private String bucket;

    @Bean
    public S3Client s3Client() {
        S3Client s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.US_EAST_1)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();

        if (s3Client.listBuckets().buckets().stream().noneMatch(b -> b.name().equals(bucket))) {
            s3Client.createBucket(b -> b.bucket(bucket));
        }

        return s3Client;
    }
}
