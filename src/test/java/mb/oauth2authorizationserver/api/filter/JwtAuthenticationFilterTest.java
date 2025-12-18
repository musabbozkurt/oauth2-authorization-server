package mb.oauth2authorizationserver.api.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mb.oauth2authorizationserver.constants.ServiceConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import tools.jackson.databind.ObjectMapper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_ShouldSetAuthentication_WhenValidTokenProvided() throws Exception {
        // Arrange
        String token = "Bearer validToken";
        Jwt jwt = createMockJwt(List.of("ROLE_USER"));

        when(request.getHeader(ServiceConstants.AUTHORIZATION_HEADER_STRING)).thenReturn(token);
        when(jwtDecoder.decode("validToken")).thenReturn(jwt);

        // Act
        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        // Assertions
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_ShouldNotSetAuthentication_WhenNoTokenProvided() throws Exception {
        // Arrange
        when(request.getHeader(ServiceConstants.AUTHORIZATION_HEADER_STRING)).thenReturn(null);

        // Act
        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        // Assertions
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_ShouldNotSetAuthentication_WhenTokenWithoutBearerPrefix() throws Exception {
        // Arrange
        when(request.getHeader(ServiceConstants.AUTHORIZATION_HEADER_STRING)).thenReturn("invalidToken");

        // Act
        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        // Assertions
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_ShouldNotContinueFilterChain_WhenUnauthorizedStatusAlreadySet() throws Exception {
        // Arrange
        when(request.getHeader(ServiceConstants.AUTHORIZATION_HEADER_STRING)).thenReturn(null);
        when(response.getStatus()).thenReturn(HttpServletResponse.SC_UNAUTHORIZED);

        // Act
        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        // Assertions
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilter_ShouldReturnUnauthorized_WhenJwtExceptionOccurs() throws Exception {
        // Arrange
        String token = "Bearer invalidToken";
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        when(request.getHeader(ServiceConstants.AUTHORIZATION_HEADER_STRING)).thenReturn(token);
        when(jwtDecoder.decode("invalidToken")).thenThrow(new JwtException("Invalid token"));
        when(response.getWriter()).thenReturn(printWriter);
        when(response.getStatus()).thenReturn(HttpServletResponse.SC_UNAUTHORIZED);
        when(objectMapper.createObjectNode()).thenReturn(new ObjectMapper().createObjectNode());
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(new ObjectMapper().writerWithDefaultPrettyPrinter());

        // Act
        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        // Assertions
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilter_ShouldContinueFilterChain_WhenNoTokenAndDefaultStatus() throws Exception {
        // Arrange
        when(request.getHeader(ServiceConstants.AUTHORIZATION_HEADER_STRING)).thenReturn(null);
        when(response.getStatus()).thenReturn(HttpServletResponse.SC_OK);

        // Act
        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        // Assertions
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_ShouldReturnNull_WhenGenericExceptionOccurs() throws Exception {
        // Arrange
        String token = "Bearer validToken";

        when(request.getHeader(ServiceConstants.AUTHORIZATION_HEADER_STRING)).thenReturn(token);
        when(jwtDecoder.decode("validToken")).thenThrow(new RuntimeException("Unexpected error"));
        when(response.getStatus()).thenReturn(HttpServletResponse.SC_OK);

        // Act
        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        // Assertions
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain).doFilter(request, response);
    }

    private Jwt createMockJwt(List<String> roles) {
        return new Jwt(
                "tokenValue",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of("roles", roles, "sub", "testUser")
        );
    }
}
