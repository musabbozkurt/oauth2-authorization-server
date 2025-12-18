package mb.oauth2authorizationserver.config.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthExceptionEntryPointTest {

    @InjectMocks
    private AuthExceptionEntryPoint authExceptionEntryPoint;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private AuthenticationException authException;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    void commence_ShouldWriteResponse_WhenAuthenticationExceptionIsThrown() throws IOException, ServletException {
        // Arrange
        String errorMessage = "Authentication failed";
        String servletPath = "/some-path";

        when(authException.getMessage()).thenReturn(errorMessage);
        when(request.getServletPath()).thenReturn(servletPath);
        ServletOutputStream servletOutputStream = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        // Act
        authExceptionEntryPoint.commence(request, response, authException);

        // Assertions
        verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // Verify the correct map content is written
        ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(objectMapper).writeValue(any(OutputStream.class), mapCaptor.capture());

        Map<String, String> capturedMap = mapCaptor.getValue();
        assertEquals(HttpStatus.UNAUTHORIZED.toString(), capturedMap.get("error"));
        assertEquals(errorMessage, capturedMap.get("message"));
        assertEquals(servletPath, capturedMap.get("path"));
    }

    @ParameterizedTest
    @CsvSource({
            "'Authentication failed', '/some-path'",
            "null, '/some-path'"
    })
    void commence_ShouldHandleExceptions_WhenObjectMapperFailsOrMessageIsNull(String message, String servletPath) throws IOException {
        // Arrange
        when(authException.getMessage()).thenReturn(message);
        when(request.getServletPath()).thenReturn(servletPath);
        ServletOutputStream servletOutputStream = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        // Mock the ObjectMapper to throw an exception
        doThrow(new RuntimeException("Mocked exception")).when(objectMapper).writeValue(any(OutputStream.class), any());

        // Act
        // Assertions
        assertThrows(ServletException.class, () -> authExceptionEntryPoint.commence(request, response, authException));
    }

    @Test
    void commence_ShouldSetCorrectResponseContentTypeAndStatus_WhenCalled() throws IOException, ServletException {
        // Arrange
        when(authException.getMessage()).thenReturn("Authentication failed");
        when(request.getServletPath()).thenReturn("/some-path");
        ServletOutputStream servletOutputStream = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        // Act
        authExceptionEntryPoint.commence(request, response, authException);

        // Assertions
        verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void commence_ShouldHandleEmptyServletPath_WhenCalled() throws IOException {
        // Arrange
        when(authException.getMessage()).thenReturn("Authentication failed");
        when(request.getServletPath()).thenReturn("");
        ServletOutputStream servletOutputStream = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        doThrow(new RuntimeException("Mocked exception")).when(objectMapper).writeValue(any(OutputStream.class), any());

        // Act
        // Assertions
        assertThrows(ServletException.class, () -> authExceptionEntryPoint.commence(request, response, authException));
    }
}
