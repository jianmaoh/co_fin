package com.cofin.review;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * Detects the image format from the file's first bytes ("magic numbers").
 * The browser-supplied Content-Type and file name are not trusted: anyone can label any file "image/png".
 */
public final class ImageType {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP = "WEBP".getBytes(StandardCharsets.US_ASCII);

    private ImageType() {
    }

    public static Optional<String> detect(byte[] data) {
        if (startsWith(data, JPEG, 0)) {
            return Optional.of("image/jpeg");
        }
        if (startsWith(data, PNG, 0)) {
            return Optional.of("image/png");
        }
        if (startsWith(data, RIFF, 0) && startsWith(data, WEBP, 8)) {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, byte[] prefix, int offset) {
        return data.length >= offset + prefix.length
                && Arrays.equals(data, offset, offset + prefix.length, prefix, 0, prefix.length);
    }
}
