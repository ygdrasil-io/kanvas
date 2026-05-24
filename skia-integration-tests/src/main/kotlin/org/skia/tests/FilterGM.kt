package org.skia.tests

/**
 * Alias for [BitmapFiltersGM]. The upstream class is
 * `gm/bitmapfilters.cpp::FilterGM` (registered as `bitmapfilters`)
 * -- our first port picked the more descriptive Kotlin name.
 * Keeping this `typealias` so tooling that looks up the class via
 * the upstream short name resolves cleanly.
 */
public typealias FilterGM = BitmapFiltersGM
