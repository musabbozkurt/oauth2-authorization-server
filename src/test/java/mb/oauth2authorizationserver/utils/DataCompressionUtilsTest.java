package mb.oauth2authorizationserver.utils;

import mb.oauth2authorizationserver.exception.BaseException;
import mb.oauth2authorizationserver.exception.OAuth2AuthorizationServerServiceErrorCode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataCompressionUtilsTest {

    private static final String TEST_DATA_PREFIX = "TEST_DATA_PREFIX_";

    @Test
    void encode_ShouldReturnBase64String_WhenCollectionIsValid() {
        // Arrange
        Collection<String> data = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            data.add(TEST_DATA_PREFIX + i);
        }

        // Act
        String encodedData = DataCompressionUtils.encode(data);

        // Assertions
        assertNotNull(encodedData);
        assertFalse(encodedData.isEmpty());
    }

    @Test
    void encode_ShouldThrowBaseException_WhenCollectionIsNull() {
        // Arrange
        Collection<String> data = null;

        // Act
        BaseException baseException = assertThrows(BaseException.class, () -> DataCompressionUtils.encode(data));

        // Assertions
        assertEquals(OAuth2AuthorizationServerServiceErrorCode.EMPTY_OR_NULL_COLLECTION, baseException.getErrorCode());
    }

    @Test
    void encode_ShouldThrowBaseException_WhenCollectionIsEmpty() {
        // Arrange
        Collection<String> data = new ArrayList<>();

        // Act
        BaseException baseException = assertThrows(BaseException.class, () -> DataCompressionUtils.encode(data));

        // Assertions
        assertEquals(OAuth2AuthorizationServerServiceErrorCode.EMPTY_OR_NULL_COLLECTION, baseException.getErrorCode());
    }

    @Test
    void decode_ShouldReturnOriginalCollection_WhenBase64StringIsValid() {
        // Arrange
        Collection<String> originalData = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            originalData.add(TEST_DATA_PREFIX + i);
        }
        String encodedData = DataCompressionUtils.encode(originalData);

        // Act
        Collection<String> decodedData = DataCompressionUtils.decode(encodedData);

        // Assertions
        assertNotNull(decodedData);
        assertEquals(originalData.size(), decodedData.size());
        assertTrue(decodedData.containsAll(originalData));
    }

    @Test
    void decode_ShouldThrowBaseException_WhenBase64StringIsInvalid() {
        // Arrange
        String invalidBase64Data = "invalid_base64_string";

        // Act
        BaseException baseException = assertThrows(BaseException.class, () -> DataCompressionUtils.decode(invalidBase64Data));

        // Assertions
        assertEquals(OAuth2AuthorizationServerServiceErrorCode.CAN_NOT_BE_DECODED, baseException.getErrorCode());
    }

    @Test
    void decode_ShouldReturnEmptyCollection_WhenBase64StringIsNull() {
        // Arrange
        String nullBase64Data = null;

        // Act
        Collection<String> decodedData = DataCompressionUtils.decode(nullBase64Data);

        // Assertions
        assertNotNull(decodedData);
        assertTrue(decodedData.isEmpty());
    }

    @Test
    void decode_ShouldReturnEmptyCollection_WhenBase64StringIsEmpty() {
        // Arrange
        String emptyBase64Data = Base64.getEncoder().encodeToString(new byte[0]);

        // Act
        Collection<String> decodedData = DataCompressionUtils.decode(emptyBase64Data);

        // Assertions
        assertNotNull(decodedData);
        assertTrue(decodedData.isEmpty());
    }

    @Test
    void encode_ShouldHandleSingleElement_WhenCollectionHasSingleElement() {
        // Arrange
        Collection<String> data = Collections.singletonList("singleElement");

        // Act
        String encodedData = DataCompressionUtils.encode(data);

        // Assertions
        assertNotNull(encodedData);
        assertFalse(encodedData.isEmpty());
    }
}
