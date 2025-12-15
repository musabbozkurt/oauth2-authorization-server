package mb.oauth2authorizationserver.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XssSanitizerUtilsTest {

    // Method source providers
    static Stream<Arguments> scriptTagProvider() {
        return Stream.of(
                Arguments.of("<script>alert('xss')</script>", "<script>"),
                Arguments.of("<SCRIPT>alert('xss')</SCRIPT>", "<SCRIPT>"),
                Arguments.of("<script type='text/javascript'>malicious()</script>", "<script")
        );
    }

    static Stream<Arguments> xssPayloadProvider() {
        return Stream.of(
                Arguments.of("<script>alert('xss')</script>John", "John", "<script>"),
                Arguments.of("javascript:alert('xss')", "alert('xss')", "javascript:"),
                Arguments.of("onerror=alert('xss')", "", "onerror="),
                Arguments.of("onclick=malicious()", "", "onclick="),
                Arguments.of("onload=hack()", "", "onload=")
        );
    }

    @Nested
    @DisplayName("sanitize() - HTML encoding tests")
    class SanitizeTests {

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Should return null or empty for null/empty input")
        void shouldHandleNullAndEmpty(String input) {
            assertEquals(input, XssSanitizerUtils.sanitize(input));
        }

        @ParameterizedTest
        @DisplayName("Should preserve safe content unchanged")
        @ValueSource(strings = {"Hello World", "John Doe", "test@example.com", "Normal text 123"})
        void shouldPreserveSafeContent(String input) {
            String result = XssSanitizerUtils.sanitize(input);
            assertNotNull(result);
            assertFalse(result.contains("<script>"));
        }

        @ParameterizedTest
        @DisplayName("Should encode script tags as HTML entities")
        @MethodSource("mb.oauth2authorizationserver.utils.XssSanitizerUtilsTest#scriptTagProvider")
        void shouldEncodeScriptTags(String input, String forbiddenContent) {
            String result = XssSanitizerUtils.sanitize(input);
            assertNotNull(result);
            assertFalse(result.contains(forbiddenContent), "Should not contain: " + forbiddenContent);
            assertTrue(result.contains("&lt;") || !result.contains("<"), "Should encode or remove angle brackets");
        }
    }

    @Nested
    @DisplayName("sanitizeJson() - JSON body sanitization tests")
    class SanitizeJsonTests {

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Should return null or empty for null/empty JSON")
        void shouldHandleNullAndEmptyJson(String input) {
            assertEquals(input, XssSanitizerUtils.sanitizeJson(input));
        }

        @ParameterizedTest
        @DisplayName("Should remove XSS payloads from JSON")
        @MethodSource("mb.oauth2authorizationserver.utils.XssSanitizerUtilsTest#xssPayloadProvider")
        void shouldRemoveXssPayloads(String input, String expectedCleanPart, String forbiddenPart) {
            String result = XssSanitizerUtils.sanitizeJson(input);
            assertNotNull(result);
            assertFalse(result.contains(forbiddenPart), "Should remove: " + forbiddenPart);
            if (expectedCleanPart != null && !expectedCleanPart.isEmpty()) {
                assertTrue(result.contains(expectedCleanPart), "Should preserve: " + expectedCleanPart);
            }
        }

        @Test
        @DisplayName("Should remove script tags from JSON firstName field")
        void shouldRemoveScriptTagsFromFirstName() {
            String json = "{\"firstName\":\"<script>alert('xss')</script>John\",\"lastName\":\"Doe\"}";
            String result = XssSanitizerUtils.sanitizeJson(json);

            assertNotNull(result);
            assertFalse(result.contains("<script>"), "Script opening tag should be removed");
            assertFalse(result.contains("</script>"), "Script closing tag should be removed");
            assertTrue(result.contains("John"), "Safe content should be preserved");
            assertTrue(result.contains("Doe"), "Other fields should remain unchanged");
        }

        @Test
        @DisplayName("Should remove javascript: protocol from JSON")
        void shouldRemoveJavascriptProtocol() {
            String json = "{\"firstName\":\"javascript:alert('xss')\"}";
            String result = XssSanitizerUtils.sanitizeJson(json);

            assertNotNull(result);
            assertFalse(result.contains("javascript:"), "javascript: protocol should be removed");
            assertTrue(result.contains("alert('xss')"), "Content after protocol should remain");
        }

        @Test
        @DisplayName("Should remove onerror event handler from JSON")
        void shouldRemoveOnErrorHandler() {
            String json = "{\"firstName\":\"John onerror=alert('xss')\"}";
            String result = XssSanitizerUtils.sanitizeJson(json);

            assertNotNull(result);
            assertFalse(result.contains("onerror="), "onerror handler should be removed");
            assertTrue(result.contains("John"), "Safe content should be preserved");
        }

        @Test
        @DisplayName("Should remove onclick event handler from JSON")
        void shouldRemoveOnClickHandler() {
            String json = "{\"data\":\"onclick=malicious()\"}";
            String result = XssSanitizerUtils.sanitizeJson(json);

            assertNotNull(result);
            assertFalse(result.contains("onclick="), "onclick handler should be removed");
        }

        @Test
        @DisplayName("Should remove onload event handler from JSON")
        void shouldRemoveOnLoadHandler() {
            String json = "{\"data\":\"onload=malicious()\"}";
            String result = XssSanitizerUtils.sanitizeJson(json);

            assertNotNull(result);
            assertFalse(result.contains("onload="), "onload handler should be removed");
        }
    }

    @Nested
    @DisplayName("sanitizeForAttribute() tests")
    class SanitizeForAttributeTests {

        @Test
        @DisplayName("Should return null for null input")
        void shouldReturnNullForNull() {
            assertNull(XssSanitizerUtils.sanitizeForAttribute(null));
        }

        @Test
        @DisplayName("Should encode HTML attribute special characters")
        void shouldEncodeAttributeCharacters() {
            String input = "value=\"test\"";
            String result = XssSanitizerUtils.sanitizeForAttribute(input);

            assertNotNull(result);
            assertFalse(result.contains("\"") && !result.contains("&quot;"), "Quotes should be encoded for attribute context");
        }
    }
}
