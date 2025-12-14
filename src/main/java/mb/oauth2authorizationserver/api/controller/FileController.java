package mb.oauth2authorizationserver.api.controller;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import mb.oauth2authorizationserver.config.MinioConfigProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/files")
@ConditionalOnProperty(value = {"minio.endpoint", "minio.accessKey", "minio.secretKey", "minio.bucket"})
public class FileController {

    private final S3Client s3Client;
    private final MinioClient minioClient;
    private final MinioConfigProperties minioConfigProperties;

    @PostMapping(path = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(minioConfigProperties.getBucket())
                .key(filename)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));

        return ResponseEntity.ok(filename);
    }

    @GetMapping(path = "/download/{filename}", produces = "application/octet-stream")
    public ResponseEntity<byte[]> download(@PathVariable String filename) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(minioConfigProperties.getBucket())
                .key(filename)
                .build();

        try (ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(request)) {
            byte[] data = stream.readAllBytes();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .body(data);
        } catch (IOException _) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(path = "/upload/stream", consumes = "multipart/form-data")
    public ResponseEntity<String> uploadStream(@RequestParam("file") MultipartFile file) {
        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        try {
            minioClient.putObject(
                    PutObjectArgs
                            .builder()
                            .bucket(minioConfigProperties.getBucket())
                            .object(filename)
                            .stream(file.getInputStream(), file.getSize(), -1) // -1 indicates unknown part size
                            .build()
            );
        } catch (Exception _) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.ok(filename);
    }
}
