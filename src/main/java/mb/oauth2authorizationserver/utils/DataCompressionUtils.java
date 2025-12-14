package mb.oauth2authorizationserver.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import mb.oauth2authorizationserver.exception.BaseException;
import mb.oauth2authorizationserver.exception.OAuth2AuthorizationServerServiceErrorCode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DataCompressionUtils {

    private static final int BUFFER_SIZE = 8192;
    private static final String DELIMITER = ",";

    public static String encode(Collection<String> data) {
        if (CollectionUtils.isEmpty(data)) {
            throw new BaseException(OAuth2AuthorizationServerServiceErrorCode.EMPTY_OR_NULL_COLLECTION);
        }

        try {
            String combinedData = String.join(DELIMITER, data);

            byte[] inputBytes = combinedData.getBytes(StandardCharsets.UTF_8);

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
                gzipOutputStream.write(inputBytes);
            }

            return Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray());
        } catch (Exception _) {
            throw new BaseException(OAuth2AuthorizationServerServiceErrorCode.CAN_NOT_BE_ENCODED);
        }
    }

    public static Collection<String> decode(String encodedData) {
        if (StringUtils.isEmpty(encodedData)) {
            return Set.of();
        }

        try {
            byte[] compressedBytes = Base64.getDecoder().decode(encodedData);

            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(compressedBytes);
            try (GZIPInputStream gzipInputStream = new GZIPInputStream(byteArrayInputStream);
                 ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[BUFFER_SIZE];
                int length;
                while ((length = gzipInputStream.read(buffer)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, length);
                }

                String decompressedString = byteArrayOutputStream.toString(StandardCharsets.UTF_8);
                return decompressedString.isEmpty() ? Set.of() : Set.of(decompressedString.split(DELIMITER));
            }
        } catch (Exception _) {
            throw new BaseException(OAuth2AuthorizationServerServiceErrorCode.CAN_NOT_BE_DECODED);
        }
    }
}
