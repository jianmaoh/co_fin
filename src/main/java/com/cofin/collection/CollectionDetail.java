package com.cofin.collection;

import java.util.List;

public record CollectionDetail(Long id, String name, List<CollectionItemView> items) {
}
