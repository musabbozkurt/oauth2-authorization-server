package mb.oauth2authorizationserver.utils;

import mb.oauth2authorizationserver.exception.BaseException;
import mb.oauth2authorizationserver.exception.OAuth2AuthorizationServerServiceErrorCode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class FileUtilsIntegrationTest {

    public static final String TEMP_OAUTH_2_AUTHORIZATION_SERVER_0_0_1_JAR = "temp-" + FileUtils.OAUTH_2_AUTHORIZATION_SERVER_0_0_1_JAR;
    public static final String TEST_FILE_TXT = "test-file.txt";
    public static final String TEST_FILE_1_JAR = "test-file1.jar";
    public static final String TEST_FILE_2_JAR = "test-file2.jar";
    public static final String TEMP_TARGET = "temp_target";

    private static Path tempTargetDir;

    @BeforeAll
    static void setUp() {
        tempTargetDir = Paths.get(TEMP_TARGET);
        if (!Files.exists(tempTargetDir)) {
            try {
                Files.createDirectory(tempTargetDir);
            } catch (IOException _) {
                fail("Failed to create temp_target directory");
            }
        }
        assertTrue(Files.exists(tempTargetDir), "Temp Target directory should exist");
    }

    @AfterAll
    static void tearDown() {
        try {
            Files.delete(tempTargetDir.resolve(TEMP_OAUTH_2_AUTHORIZATION_SERVER_0_0_1_JAR));
            Files.delete(tempTargetDir.resolve(TEST_FILE_TXT));
            Files.delete(tempTargetDir.resolve(TEST_FILE_1_JAR));
            Files.delete(tempTargetDir.resolve(TEST_FILE_2_JAR));
        } catch (IOException _) {
            fail("Failed to delete temp_target directory");
        }
    }

    @Test
    void findFileInPathByPattern_ShouldReturnFile_WhenFileExists() throws IOException {
        // Arrange
        Path testFile = tempTargetDir.resolve(TEMP_OAUTH_2_AUTHORIZATION_SERVER_0_0_1_JAR);
        Files.createFile(testFile);

        assertTrue(Files.exists(testFile), "Test file should be created");

        // Act
        Optional<Path> result = FileUtils.findFileInPathByPattern("temp-oauth2-authorization-server-.*\\.jar");

        // Assertions
        assertTrue(result.isPresent());
        assertEquals(testFile.getFileName(), result.get().getFileName());
    }

    @Test
    void findFileInPathByPattern_ShouldReturnEmpty_WhenNoFileMatches() throws IOException {
        // Arrange
        Path testFile = tempTargetDir.resolve(TEST_FILE_TXT);
        Files.createFile(testFile);

        assertTrue(Files.exists(testFile), "Test file should be created");

        // Act
        Optional<Path> result = FileUtils.findFileInPathByPattern(".*\\.jar....");

        // Assertions
        assertTrue(result.isEmpty());
    }

    @Test
    void findFileInPathByPattern_ShouldReturnFirst_WhenMultipleFilesMatch() throws IOException {
        // Arrange
        Files.createFile(tempTargetDir.resolve(TEST_FILE_1_JAR));
        Files.createFile(tempTargetDir.resolve(TEST_FILE_2_JAR));

        // Act
        Optional<Path> result = FileUtils.findFileInPathByPattern("test-file.*\\.jar");

        // Assertions
        assertTrue(result.isPresent());
        assertTrue(result.get().getFileName().toString().matches("test-file.*\\.jar"));
    }

    @Test
    void findFileInPathByPattern_ShouldThrowException_WhenPatternIsInvalid() {
        // Arrange
        String invalidPattern = "[invalid";

        // Act
        // Assertions
        BaseException exception = assertThrows(BaseException.class, () -> FileUtils.findFileInPathByPattern(invalidPattern));
        assertEquals(OAuth2AuthorizationServerServiceErrorCode.UNEXPECTED_ERROR, exception.getErrorCode());
    }
}
