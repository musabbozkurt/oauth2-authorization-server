package mb.oauth2authorizationserver.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import mb.oauth2authorizationserver.exception.BaseException;
import mb.oauth2authorizationserver.exception.OAuth2AuthorizationServerServiceErrorCode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FileUtils {

    public static final String OAUTH_2_AUTHORIZATION_SERVER_0_0_1_JAR = "oauth2-authorization-server-0.0.1.jar";

    public static Optional<Path> findFileInPathByPattern(String pattern) {
        Path targetDir = Paths.get(FileUtils.class.getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .getPath())
                .getParent()
                .getParent();

        try (Stream<Path> pathStream = Files.walk(targetDir)) {
            return pathStream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches(pattern))
                    .findFirst();
        } catch (Exception _) {
            throw new BaseException(OAuth2AuthorizationServerServiceErrorCode.UNEXPECTED_ERROR);
        }
    }
}
