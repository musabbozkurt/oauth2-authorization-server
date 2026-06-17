package mb.oauth2authorizationserver.api.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
// import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Slf4j
//@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class PasswordEncodingDebugFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(request.getMethod()) && request.getRequestURI().contains("/login")) {
            String password = request.getParameter("password");
            if (password != null) {
                byte[] utf8Bytes = password.getBytes(StandardCharsets.UTF_8);
                log.info("LOGIN DEBUG - Request encoding: {}", request.getCharacterEncoding());
                log.info("LOGIN DEBUG - Password length: {} chars, {} bytes", password.length(), utf8Bytes.length);
                log.info("LOGIN DEBUG - Password hex (UTF-8): {}", HexFormat.of().formatHex(utf8Bytes));
                log.info("LOGIN DEBUG - Password value: [{}]", password);
                // log.info("LOGIN DEBUG - BCrypt match result: {}", new BCryptPasswordEncoder().matches(password, "$2a$10$h3KLe9h30W2ACL01yMEAP.StxUQg.rtMrGvQtKQ7E9YiZ49JTNRyq"));
            }
        }
        filterChain.doFilter(request, response);
    }
}
