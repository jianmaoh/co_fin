package com.cofin.web;

import com.cofin.cafe.TagCount;
import java.util.List;

/** A nearby Google place that is already in Co-Fin, with its community data. */
public record NearbyMatch(String placeId, Long cafeId, List<TagCount> tags, Double averageRating, Long reviewCount) {
}
