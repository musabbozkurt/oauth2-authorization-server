package mb.oauth2authorizationserver.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import mb.oauth2authorizationserver.constants.ServiceConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuthExceptionEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws ServletException {
        Map<String, String> map = new HashMap<>();
        map.put(ServiceConstants.ERROR, HttpStatus.UNAUTHORIZED.toString());
        map.put(ServiceConstants.MESSAGE, authException.getMessage());
        map.put(ServiceConstants.PATH, request.getServletPath());
        map.put(ServiceConstants.TIMESTAMP, String.valueOf((LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) * 1000)));

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        try {
            objectMapper.writeValue(response.getOutputStream(), map);
        } catch (Exception _) {
            throw new ServletException();
        }
    }
}
