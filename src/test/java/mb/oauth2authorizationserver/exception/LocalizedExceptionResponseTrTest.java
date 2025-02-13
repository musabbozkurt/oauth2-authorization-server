package mb.oauth2authorizationserver.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Locale;

import static java.util.Collections.EMPTY_LIST;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(locations = "classpath:messages_tr.properties")
class LocalizedExceptionResponseTrTest {

    @BeforeEach
    void setUp() {
        // Reset the static message source for each test
        LocaleContextHolder.setLocale(Locale.of("TR"));
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
        String message = "Özel mesaj";

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
        assertThat(response.getMessage()).contains("Beklenmedik hata oluştu. Lütfen tekrar deneyiniz.");
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
