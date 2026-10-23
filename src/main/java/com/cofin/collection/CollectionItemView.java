package com.cofin.collection;

import java.time.Instant;

public record CollectionItemView(Long cafeId, String cafeName, String address, String note, Instant addedAt) {
}
