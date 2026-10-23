package com.cofin.cafe;

/** {@code created} is false when an identical cafe already existed and was reused. */
public record CreateResult(Long id, boolean created) {
}
