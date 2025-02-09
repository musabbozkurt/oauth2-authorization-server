package mb.oauth2authorizationserver.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StringCollectionCompressorUtils {

    private static final String DELIMITER = ",";

    /**
     * Compresses a collection of strings into a compact string representation
     *
     * @param strings Collection of strings to compress
     * @return Compressed string representation
     */
    public static String compress(Collection<String> strings) {
        if (strings == null || strings.isEmpty()) {
            return "";
        }

        // Join strings with delimiter
        String joinedStrings = String.join(DELIMITER, strings);

        // Convert to bytes and compress
        byte[] input = joinedStrings.getBytes(StandardCharsets.UTF_8);

        // Initialize deflater
        Deflater deflater = new Deflater();
        deflater.setInput(input);
        deflater.finish();

        // Compress the bytes
        byte[] buffer = new byte[1024];
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(input.length);

        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            outputStream.write(buffer, 0, count);
        }

        deflater.end();

        // Encode as Base64
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(outputStream.toByteArray());
    }

    /**
     * Decompresses a string back into a collection of strings
     *
     * @param compressed Compressed string representation
     * @return Set of decompressed strings
     */
    public static Set<String> decompress(String compressed) {
        if (compressed == null || compressed.isEmpty()) {
            return Set.of();
        }

        try {
            // Decode Base64
            byte[] compressedData = Base64.getUrlDecoder().decode(compressed);

            // Initialize inflater
            Inflater inflater = new Inflater();
            inflater.setInput(compressedData);

            // Decompress the bytes
            byte[] buffer = new byte[1024];
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(compressedData.length);

            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }

            inflater.end();

            // Convert back to string and split
            String decompressed = outputStream.toString(StandardCharsets.UTF_8);

            return Arrays.stream(decompressed.split(DELIMITER))
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());

        } catch (Exception e) {
            log.error("Error decompressing string collection", e);
            return Set.of();
        }
    }
}
