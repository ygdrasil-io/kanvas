package org.skia.tests

/**
 * Alias for [ShallowGradientLinearGM]. The upstream class is
 * `gm/shallowgradient.cpp::ShallowGradientGM`, an abstract base
 * used by several concrete `DEF_GM` instances (linear / radial /
 * conical / sweep, each with a `_nodither` variant). The full
 * matrix is already covered by [ShallowGradientLinearGM],
 * [ShallowGradientLinearNoditherGM], [ShallowGradientRadialGM],
 * [ShallowGradientRadialNoditherGM], [ShallowGradientConicalGM],
 * and [ShallowGradientSweepGM]. Keeping this `typealias` so
 * tooling that looks up the class via the upstream base-class
 * name resolves cleanly.
 */
public typealias ShallowGradientGM = ShallowGradientLinearGM
