package mb.oauth2authorizationserver.api.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.constants.ServiceConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends GenericFilterBean {

    private static final String ROLES = "roles";

    private final ObjectMapper objectMapper;
    private final JwtDecoder jwtDecoder;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        Authentication authentication = getAuthentication((HttpServletRequest) request, (HttpServletResponse) response);

        if (Objects.nonNull(authentication)) {
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } else {
            if (HttpServletResponse.SC_UNAUTHORIZED == ((HttpServletResponse) response).getStatus()) {
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private Authentication getAuthentication(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String token = request.getHeader(ServiceConstants.AUTHORIZATION_HEADER_STRING);

        if (StringUtils.isNotBlank(token) && token.startsWith(ServiceConstants.TOKEN_PREFIX)) {
            token = token.substring(7);
            try {
                Jwt jwt = jwtDecoder.decode(token);
                List<SimpleGrantedAuthority> authorities = jwt.getClaimAsStringList(ROLES)
                        .stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();

                JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                return authentication;
            } catch (JwtException e) {
                log.error("Jwt exception is occurred while decoding token. getAuthentication - Exception: {}", ExceptionUtils.getStackTrace(e));
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

                ObjectNode rootNode = objectMapper.createObjectNode();
                rootNode.put(ServiceConstants.SUCCESS, false)
                        .put(ServiceConstants.MESSAGE, "Invalid token");

                String jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
                response.getWriter().write(jsonString);
                return null;
            } catch (Exception _) {
                return null;
            }
        }
        return null;
    }
}
