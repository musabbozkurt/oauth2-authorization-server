package mb.oauth2authorizationserver.api.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import mb.oauth2authorizationserver.utils.XssSanitizerUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class XssRequestWrapper extends HttpServletRequestWrapper {

    private byte[] cachedBody;

    public XssRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public String getParameter(String name) {
        return XssSanitizerUtils.sanitize(super.getParameter(name));
    }

    @Override
    public String[] getParameterValues(String name) {
        return Optional.ofNullable(super.getParameterValues(name))
                .map(sanitized -> {
                    for (int i = 0; i < sanitized.length; i++) {
                        sanitized[i] = XssSanitizerUtils.sanitize(sanitized[i]);
                    }
                    return sanitized;
                })
                .orElse(null);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (cachedBody == null) {
            cachedBody = readAndSanitizeBody();
        }
        return new CachedServletInputStream(cachedBody);
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    private byte[] readAndSanitizeBody() throws IOException {
        String body = new String(super.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return XssSanitizerUtils.sanitizeJson(body).getBytes(StandardCharsets.UTF_8);
    }

    private static class CachedServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream inputStream;

        CachedServletInputStream(byte[] body) {
            this.inputStream = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return inputStream.read();
        }

        @Override
        public boolean isFinished() {
            return inputStream.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            // Not needed for synchronous processing
        }
    }
}
