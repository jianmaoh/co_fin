package com.cofin.recommend;

import com.cofin.cafe.TagCount;
import java.util.List;

public record PopularCafeView(Long id, String name, String address, Long saves, Long votes, List<TagCount> tags) {
}
