package mb.oauth2authorizationserver.utils;

import mb.oauth2authorizationserver.exception.BaseException;
import mb.oauth2authorizationserver.exception.OAuth2AuthorizationServerServiceErrorCode;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomSerializationUtilsTest {

    @Test
    void serialize_ShouldReturnByteArray_WhenObjectIsValid() {
        // Arrange
        String testObject = "test string";

        // Act
        byte[] result = CustomSerializationUtils.serialize(testObject);

        // Assertions
        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    void serialize_ShouldThrowException_WhenObjectIsNull() {
        // Arrange
        Serializable nullObject = null;

        // Act
        BaseException baseException = assertThrows(BaseException.class, () -> CustomSerializationUtils.serialize(nullObject));

        // Assertions
        assertNotNull(baseException);
        assertEquals(OAuth2AuthorizationServerServiceErrorCode.INVALID_VALUE, baseException.getErrorCode());
    }

    @Test
    void serialize_ShouldHandleComplexObject_WhenObjectIsSerializable() {
        // Arrange
        Map<String, Integer> testMap = new HashMap<>();
        testMap.put("key1", 1);
        testMap.put("key2", 2);

        // Act
        byte[] result = CustomSerializationUtils.serialize((Serializable) testMap);

        // Assertions
        assertNotNull(result);
        assertThat(result).hasSizeGreaterThan(0);
    }

    @Test
    void serialize_ShouldHandleList_WhenListIsSerializable() {
        // Arrange
        List<String> testList = new ArrayList<>();
        testList.add("item1");
        testList.add("item2");

        // Act
        byte[] result = CustomSerializationUtils.serialize((Serializable) testList);

        // Assertions
        assertNotNull(result);
        assertThat(result).hasSizeGreaterThan(0);
    }

    @Test
    void deserialize_ShouldReturnOriginalObject_WhenByteArrayIsValid() {
        // Arrange
        String originalObject = "test string";
        byte[] serializedData = CustomSerializationUtils.serialize(originalObject);

        // Act
        String result = CustomSerializationUtils.deserialize(serializedData);

        // Assertions
        assertNotNull(result);
        assertEquals(originalObject, result);
    }

    @Test
    void deserialize_ShouldThrowBaseException_WhenByteArrayIsNull() {
        // Arrange
        // Act
        BaseException baseException = assertThrows(BaseException.class, () -> CustomSerializationUtils.deserialize(null));

        // Assertions
        assertNotNull(baseException);
        assertEquals(OAuth2AuthorizationServerServiceErrorCode.INVALID_VALUE, baseException.getErrorCode());
    }

    @Test
    void deserialize_ShouldThrowException_WhenByteArrayIsInvalid() {
        // Arrange
        byte[] invalidBytes = new byte[]{1, 2, 3, 4, 5};

        // Act & Assertions
        assertThrows(Exception.class, () -> CustomSerializationUtils.deserialize(invalidBytes));
    }

    @Test
    void deserialize_ShouldReturnOriginalComplexObject_WhenByteArrayContainsComplexObject() {
        // Arrange
        Map<String, Integer> originalMap = new HashMap<>();
        originalMap.put("key1", 1);
        originalMap.put("key2", 2);
        byte[] serializedData = CustomSerializationUtils.serialize((Serializable) originalMap);

        // Act
        Map<String, Integer> result = CustomSerializationUtils.deserialize(serializedData);

        // Assertions
        assertNotNull(result);
        assertEquals(originalMap.size(), result.size());
        assertEquals(originalMap.get("key1"), result.get("key1"));
        assertEquals(originalMap.get("key2"), result.get("key2"));
    }

    @Test
    void serialize_deserialize_ShouldRoundTripSuccessfully_WhenObjectIsValid() {
        // Arrange
        List<String> originalList = new ArrayList<>();
        originalList.add("item1");
        originalList.add("item2");
        originalList.add("item3");

        // Act
        byte[] serialized = CustomSerializationUtils.serialize((Serializable) originalList);
        List<String> deserialized = CustomSerializationUtils.deserialize(serialized);

        // Assertions
        assertNotNull(deserialized);
        assertEquals(originalList.size(), deserialized.size());
        assertEquals(originalList, deserialized);
    }

    @Test
    void deserialize_ShouldThrowException_WhenByteArrayIsEmpty() {
        // Arrange
        byte[] emptyBytes = new byte[0];

        // Act & Assertions
        assertThrows(Exception.class, () -> CustomSerializationUtils.deserialize(emptyBytes));
    }
}
