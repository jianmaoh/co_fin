package com.cofin.review;

import java.util.List;

/** Someone who reviewed or tagged a cafe. {@code review} is null if they only added tags. */
public record Participant(String username, ReviewView review, List<String> tags) {
}
