package mb.oauth2authorizationserver.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import mb.oauth2authorizationserver.exception.BaseException;
import mb.oauth2authorizationserver.exception.OAuth2AuthorizationServerServiceErrorCode;
import org.apache.commons.lang3.SerializationUtils;

import java.io.Serializable;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CustomSerializationUtils {

    public static byte[] serialize(Serializable obj) {
        if (obj == null) {
            throw new BaseException(OAuth2AuthorizationServerServiceErrorCode.INVALID_VALUE);
        }
        return SerializationUtils.serialize(obj);
    }

    public static <T> T deserialize(byte[] obj) {
        if (obj == null || obj.length == 0) {
            throw new BaseException(OAuth2AuthorizationServerServiceErrorCode.INVALID_VALUE);
        }
        return SerializationUtils.deserialize(obj);
    }
}
