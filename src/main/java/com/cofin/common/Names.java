package com.cofin.common;

import java.util.Locale;

/** Normalization and validation for user-typed names (cafes, tags, collections, notes). */
public final class Names {

    public static final int TAG_MAX_LENGTH = 20;

    private Names() {
    }

    /** Trims, collapses inner whitespace, and enforces 1..max characters. */
    public static String required(String raw, int max, String label) {
        String value = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        if (value.isEmpty()) {
            throw new InvalidInputException(label + "不能是空的");
        }
        // Count code points, not UTF-16 units, so "咖啡" is 2 and an emoji is 1.
        if (value.codePointCount(0, value.length()) > max) {
            throw new InvalidInputException(label + "最多 " + max + " 個字");
        }
        return value;
    }

    /** Like {@link #required} but blank input becomes null. */
    public static String optional(String raw, int max, String label) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return required(raw, max, label);
    }

    /** Tags are case-insensitive and may be typed with a leading '#': "#WiFi " becomes "wifi". */
    public static String tag(String raw) {
        String value = raw == null ? "" : raw.strip();
        while (value.startsWith("#")) {
            value = value.substring(1);
        }
        return required(value, TAG_MAX_LENGTH, "標籤").toLowerCase(Locale.ROOT);
    }

    /** Escapes LIKE wildcards so a search for "100%" matches literally. Pair with {@code escape '\'}. */
    public static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
