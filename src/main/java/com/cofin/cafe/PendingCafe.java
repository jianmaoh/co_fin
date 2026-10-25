package com.cofin.cafe;

import java.time.Instant;

/** {@code createdBy} is null when the creator's account was deleted. */
public record PendingCafe(Long id, String name, String address, String createdBy, Instant createdAt) {
}
