package org.skia.tests

/**
 * Alias for [SimpleShapesGM]. The upstream class is
 * `gm/shapes.cpp::ShapesGM`, an abstract base used by several
 * concrete `DEF_GM` instances. The most generic concrete instance
 * is `simpleshapes` (= `SimpleShapesGM`), which already covers the
 * 500x500 shape-grid reference. Keeping this `typealias` so
 * tooling that looks up the class via the upstream base-class
 * name resolves cleanly.
 */
public typealias ShapesGM = SimpleShapesGM
