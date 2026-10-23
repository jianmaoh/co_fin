package com.cofin.cafe;

import java.util.List;

public record CafeSummary(Long id, String name, String address, List<TagCount> tags) {
}
