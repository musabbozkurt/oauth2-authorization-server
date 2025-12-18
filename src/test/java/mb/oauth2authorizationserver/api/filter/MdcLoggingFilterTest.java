package mb.oauth2authorizationserver.api.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import mb.oauth2authorizationserver.constants.ServiceConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MdcLoggingFilterTest {

    @InjectMocks
    private MdcLoggingFilter mdcLoggingFilter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private HttpSession session;

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void doFilter_ShouldSetMdcUsername_WhenUsernameHeaderExists() throws Exception {
        // Arrange
        when(request.getHeader(ServiceConstants.USERNAME)).thenReturn("testUser");
        when(request.getSession()).thenReturn(session);
        when(session.getId()).thenReturn("sessionId123");

        // Act
        mdcLoggingFilter.doFilter(request, response, filterChain);

        // Assertions
        assertNull(MDC.get(ServiceConstants.USERNAME));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_ShouldSetMdcUsername_WhenUserNameUnderscoreHeaderExists() throws Exception {
        // Arrange
        when(request.getHeader(ServiceConstants.USERNAME)).thenReturn(null);
        when(request.getHeader(ServiceConstants.USERNAME_WITH_UNDERSCORE)).thenReturn("testUser");
        when(request.getSession()).thenReturn(session);
        when(session.getId()).thenReturn("sessionId123");

        // Act
        mdcLoggingFilter.doFilter(request, response, filterChain);

        // Assertions
        assertNull(MDC.get(ServiceConstants.USERNAME));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_ShouldNotSetMdcUsername_WhenNoUsernameHeadersExist() throws Exception {
        // Arrange
        when(request.getHeader(ServiceConstants.USERNAME)).thenReturn(null);
        when(request.getHeader(ServiceConstants.USERNAME_WITH_UNDERSCORE)).thenReturn(null);
        when(request.getSession()).thenReturn(session);
        when(session.getId()).thenReturn("sessionId123");

        // Act
        mdcLoggingFilter.doFilter(request, response, filterChain);

        // Assertions
        assertNull(MDC.get(ServiceConstants.USERNAME));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_ShouldSetSessionId_WhenSessionExists() throws Exception {
        // Arrange
        String expectedSessionId = "testSessionId";
        when(request.getSession()).thenReturn(session);
        when(session.getId()).thenReturn(expectedSessionId);

        // Act
        mdcLoggingFilter.doFilter(request, response, filterChain);

        // Assertions
        assertNull(MDC.get("sessionId"));
        verify(filterChain).doFilter(request, response);
        verify(session).getId();
    }
}
