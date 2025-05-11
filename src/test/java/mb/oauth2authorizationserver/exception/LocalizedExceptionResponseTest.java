package mb.oauth2authorizationserver.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static java.util.Collections.EMPTY_LIST;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(locations = "classpath:messages.properties")
class LocalizedExceptionResponseTest {

    @BeforeEach
    void setUp() {
        // Reset the static message source for each test
        LocalizedExceptionResponse.setMessages(null);
    }

    @Test
    void constructor_ShouldCreateResponseWithErrorCode_WhenOnlyCodeProvided() {
        // Arrange
        String errorCode = "TEST_ERROR";

        // Act
        LocalizedExceptionResponse response = new LocalizedExceptionResponse(errorCode);

        // Assertions
        assertThat(response.getErrorCode()).isEqualTo(errorCode);
        assertThat(response.getMessage()).isNotBlank();
        assertThat(response.getParams()).isEqualTo(EMPTY_LIST);
    }

    @Test
    void constructor_ShouldCreateResponseWithArgs_WhenArgsProvided() {
        // Arrange
        String errorCode = "NUMERIC_ERROR";
        List<String> args = List.of("arg1", "arg2");

        // Act
        LocalizedExceptionResponse response = new LocalizedExceptionResponse(errorCode, args);

        // Assertions
        assertThat(response.getErrorCode()).isEqualTo(errorCode);
        assertThat(response.getMessage()).contains("arg1", "arg2");
        assertThat(response.getParams()).isEqualTo(args);
    }

    @Test
    void constructor_ShouldUseProvidedMessage_WhenMessageIsNotBlank() {
        // Arrange
        String errorCode = "TEST_ERROR";
        String message = "Custom message";

        // Act
        LocalizedExceptionResponse response = new LocalizedExceptionResponse(errorCode, message);

        // Assertions
        assertThat(response.getErrorCode()).isEqualTo(errorCode);
        assertThat(response.getMessage()).isEqualTo(message);
        assertThat(response.getParams()).isEqualTo(EMPTY_LIST);
    }

    @Test
    @Disabled("This test is disabled until github actions is fixed")
    void constructor_ShouldUseFallbackMessage_WhenMessageSourceNotFound() {
        // Arrange
        String errorCode = "NONEXISTENT_ERROR";

        // Act
        LocalizedExceptionResponse response = new LocalizedExceptionResponse(errorCode);

        // Assertions
        assertThat(response.getErrorCode()).isEqualTo(errorCode);
        assertThat(response.getMessage()).contains("Unexpected error occurred. Please try again.");
        assertThat(response.getParams()).isEqualTo(EMPTY_LIST);
    }

    @Test
    void getMessage_ShouldHandleNumericArgs_WhenProvidedInCollection() {
        // Arrange
        String errorCode = "NUMERIC_ERROR";
        List<Object> args = List.of(42, 3.14);

        // Act
        LocalizedExceptionResponse response = new LocalizedExceptionResponse(errorCode, args);

        // Assertions
        assertThat(response.getMessage()).contains("42", "3.14");
        assertThat(response.getParams()).isEqualTo(args);
    }
}
