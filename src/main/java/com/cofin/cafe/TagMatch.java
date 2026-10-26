package com.cofin.cafe;

/** A tag in a search result; {@code matched} when it contains one of the keywords. */
public record TagMatch(String name, Long count, boolean matched) {
}
