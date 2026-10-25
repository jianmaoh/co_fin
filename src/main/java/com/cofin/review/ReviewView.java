package com.cofin.review;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ReviewView(Long id, String username, int rating, String content, LocalDate visitedOn,
                         Instant updatedAt, List<Long> photoIds) {

    /** The last-edited date as shown to users (Taiwan time). */
    public LocalDate updatedOn() {
        return updatedAt.atZone(ReviewService.ZONE).toLocalDate();
    }
}
