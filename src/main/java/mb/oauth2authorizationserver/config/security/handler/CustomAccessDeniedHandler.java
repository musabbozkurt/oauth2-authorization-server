package mb.oauth2authorizationserver.config.security.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import mb.oauth2authorizationserver.constants.ServiceConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Component("customAccessDeniedHandler")
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException {
        Map<String, String> map = new HashMap<>();
        map.put(ServiceConstants.ERROR, HttpStatus.BAD_REQUEST.toString());
        map.put(ServiceConstants.MESSAGE, accessDeniedException.getMessage());
        map.put(ServiceConstants.PATH, request.getServletPath());
        map.put(ServiceConstants.TIMESTAMP, String.valueOf((LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) * 1000)));

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write(objectMapper.writeValueAsString(map));
    }
}
