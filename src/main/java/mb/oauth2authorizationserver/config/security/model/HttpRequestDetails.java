package mb.oauth2authorizationserver.config.security.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import ua_parser.Client;
import ua_parser.Parser;

import java.io.Serial;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class HttpRequestDetails extends WebAuthenticationDetails {

    @Serial
    private static final long serialVersionUID = -1442946718508551777L;

    private final Map<String, String> headers;
    private final String rawUserAgent;
    private final String deviceFamily;
    private final String osFamily;
    private final String osMajor;
    private final String osMinor;
    private final String osPatch;
    private final String osPatchMinor;
    private final String userAgentFamily;
    private final String userAgentMajor;
    private final String userAgentMinor;
    private final String userAgentPatch;
    private final String method;
    private final String protocol;
    private final String uri;
    private final String url;
    private final int remotePort;

    public HttpRequestDetails(HttpServletRequest request) {
        super(request);
        headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();

        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            headers.put(name, request.getHeader(name));
        }

        this.rawUserAgent = request.getHeader("user-agent");
        this.method = request.getMethod();
        this.protocol = request.getProtocol();
        this.uri = request.getRequestURI();
        this.url = request.getRequestURL().toString();
        this.remotePort = request.getServerPort();

        Parser parser = new Parser();
        Client client = parser.parse(rawUserAgent);

        this.deviceFamily = client.device.family;
        this.osFamily = client.os.family;
        this.osMajor = client.os.major;
        this.osMinor = client.os.minor;
        this.osPatch = client.os.patch;
        this.osPatchMinor = client.os.patchMinor;
        this.userAgentFamily = client.userAgent.family;
        this.userAgentMajor = client.userAgent.major;
        this.userAgentMinor = client.userAgent.minor;
        this.userAgentPatch = client.userAgent.patch;
    }

    @JsonCreator
    public HttpRequestDetails(@JsonProperty("remoteAddress") String remoteAddress,
                              @JsonProperty("sessionId") String sessionId,
                              @JsonProperty("headers") Map<String, String> headers,
                              @JsonProperty("rawUserAgent") String rawUserAgent,
                              @JsonProperty("deviceFamily") String deviceFamily,
                              @JsonProperty("osFamily") String osFamily,
                              @JsonProperty("osMajor") String osMajor,
                              @JsonProperty("osMinor") String osMinor,
                              @JsonProperty("osPatch") String osPatch,
                              @JsonProperty("osPatchMinor") String osPatchMinor,
                              @JsonProperty("userAgentFamily") String userAgentFamily,
                              @JsonProperty("userAgentMajor") String userAgentMajor,
                              @JsonProperty("userAgentMinor") String userAgentMinor,
                              @JsonProperty("userAgentPatch") String userAgentPatch,
                              @JsonProperty("method") String method,
                              @JsonProperty("protocol") String protocol,
                              @JsonProperty("uri") String uri,
                              @JsonProperty("url") String url,
                              @JsonProperty("remotePort") int remotePort) {
        super(remoteAddress, sessionId);
        this.headers = headers;
        this.rawUserAgent = rawUserAgent;
        this.deviceFamily = deviceFamily;
        this.osFamily = osFamily;
        this.osMajor = osMajor;
        this.osMinor = osMinor;
        this.osPatch = osPatch;
        this.osPatchMinor = osPatchMinor;
        this.userAgentFamily = userAgentFamily;
        this.userAgentMajor = userAgentMajor;
        this.userAgentMinor = userAgentMinor;
        this.userAgentPatch = userAgentPatch;
        this.method = method;
        this.protocol = protocol;
        this.uri = uri;
        this.url = url;
        this.remotePort = remotePort;
    }
}
