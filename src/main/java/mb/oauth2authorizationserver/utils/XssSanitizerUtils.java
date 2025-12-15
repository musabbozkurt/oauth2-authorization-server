package mb.oauth2authorizationserver.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.owasp.encoder.Encode;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class XssSanitizerUtils {

    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        return Encode.forHtml(input);
    }

    public static String sanitizeForAttribute(String input) {
        if (input == null) {
            return null;
        }
        return Encode.forHtmlAttribute(input);
    }

    public static String sanitizeJson(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        return json.replaceAll("<script[^>]*>.*?</script>", "")
                .replaceAll("on\\w+\\s*=", "")
                .replaceAll("onerror\\s*=", "")
                .replaceAll("onclick\\s*=", "")
                .replaceAll("onload\\s*=", "")
                .replace("javascript:", "");
    }
}
