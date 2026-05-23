package org.skia.tests

/**
 * Alias for [DisplacementGM]. The upstream class is
 * `gm/displacement.cpp::DisplacementMapGM` (registered as
 * `displacement`) -- the existing port picked the short upstream
 * registration name. Keeping this `typealias` so tooling that looks
 * up the class under its longer C++ identifier resolves cleanly.
 */
public typealias DisplacementMapGM = DisplacementGM
