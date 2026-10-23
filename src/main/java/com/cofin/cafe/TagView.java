package com.cofin.cafe;

/** A tag on a cafe page: how many people added it, and whether the viewer is one of them. */
public record TagView(String name, Long count, boolean mine) {
}
