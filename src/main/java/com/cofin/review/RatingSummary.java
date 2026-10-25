package com.cofin.review;

/** {@code average} is null when there are no reviews. */
public record RatingSummary(Long cafeId, Double average, Long count) {

    public static RatingSummary none(Long cafeId) {
        return new RatingSummary(cafeId, null, 0L);
    }
}
