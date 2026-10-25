package com.cofin.cafe;

import java.util.List;

/** A Google place that is already in Co-Fin. */
public record PlaceMatch(String placeId, Long cafeId, List<TagCount> tags) {
}
