package com.cofin.cafe;

import java.util.List;

public record CafeDetail(Long id, String name, String address, String mapsUrl, List<TagView> tags) {
}
