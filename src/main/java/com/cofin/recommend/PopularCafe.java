package com.cofin.recommend;

/** {@code saves}: how many collections include the cafe; {@code votes}: how many tag votes it has. */
public record PopularCafe(Long id, String name, String address, Long saves, Long votes) {
}
