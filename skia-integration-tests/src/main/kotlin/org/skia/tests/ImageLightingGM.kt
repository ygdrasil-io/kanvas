package org.skia.tests

/**
 * Alias for [LightingGM]. The upstream class is
 * `gm/imagelighting.cpp::ImageLightingGM` (registered as `lighting`)
 * -- the existing port picked the short upstream registration name.
 * Keeping this `typealias` so tooling that looks up the class via
 * the longer C++ identifier resolves cleanly.
 */
public typealias ImageLightingGM = LightingGM
