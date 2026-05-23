package org.skia.tests

/**
 * Alias for [FilterFastBoundsGM]. The upstream class is
 * `gm/imagefilterstransformed.cpp::ImageFilterFastBoundGM` (registered as
 * `filterfastbounds`) -- our first port picked the short
 * registration name. Keeping this `typealias` so tooling that looks
 * up the class via the longer C++ identifier resolves cleanly.
 */
public typealias ImageFilterFastBoundGM = FilterFastBoundsGM
