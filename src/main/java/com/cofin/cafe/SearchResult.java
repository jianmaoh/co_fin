package com.cofin.cafe;

import java.util.List;

/** {@code score}: total votes on the tags that matched the search keywords. */
public record SearchResult(Long id, String name, String address, List<TagMatch> tags, long score) {
}
