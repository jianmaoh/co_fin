package com.cofin.cafe;

import java.util.List;

/**
 * @param canDelete  whether the viewer may delete this cafe (see Cafe#canBeDeletedBy)
 * @param canApprove whether the viewer is an admin looking at a pending cafe
 */
public record CafeDetail(Long id, String name, String address, String googlePlaceId, String mapsUrl,
                         List<TagView> tags, boolean approved, String createdBy,
                         boolean canDelete, boolean canApprove) {
}
