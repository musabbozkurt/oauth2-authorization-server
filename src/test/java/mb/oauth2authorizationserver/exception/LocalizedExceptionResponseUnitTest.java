package mb.oauth2authorizationserver.exception;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.util.List;
import java.util.Locale;

import static java.util.Collections.EMPTY_LIST;
import static org.assertj.core.api.Assertions.assertThat;

class LocalizedExceptionResponseUnitTest {

    @BeforeEach
    void setUp() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        LocalizedExceptionResponse.setMessages(new MessageSourceAccessor(messageSource, Locale.getDefault()));
    }

    @AfterAll
    static void tearDown() {
        // Reset the static message source after all tests
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
        assertThat(response.getMessage()).isEqualTo("Unexpected error occurred. Please try again.");
        assertThat(response.getParams()).isEqualTo(EMPTY_LIST);
    }

    @Test
    void constructor_ShouldCreateResponseWithArgs_WhenArgsProvided() {
        // Arrange
        String errorCode = "NUMERIC_ERROR";
        List<Object> args = List.of(42, 3.14);

        // Act
        LocalizedExceptionResponse response = new LocalizedExceptionResponse(errorCode, args);

        // Assertions
        assertThat(response.getErrorCode()).isEqualTo(errorCode);
        assertThat(response.getMessage()).isEqualTo("Error with numbers: 42 and 3.14.");
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
    void constructor_ShouldUseFallbackMessage_WhenMessageSourceNotFound() {
        // Arrange
        String errorCode = "NONEXISTENT_ERROR";

        // Act
        LocalizedExceptionResponse response = new LocalizedExceptionResponse(errorCode);

        // Assertions
        assertThat(response.getErrorCode()).isEqualTo(errorCode);
        assertThat(response.getMessage()).isEqualTo("Unexpected error occurred. Please try again.");
        assertThat(response.getParams()).isEqualTo(EMPTY_LIST);
    }
}
