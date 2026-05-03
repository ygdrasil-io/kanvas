package org.skia.foundation

/**
 * Direction in which a contour is wound. Mirrors Skia's `SkPathDirection`.
 *
 * - `kCW` : clockwise (positive sweep in screen coords where Y points down).
 * - `kCCW` : counter-clockwise (negative sweep).
 *
 * For closed shape factories like `addRect`, `addOval`, `addCircle` the
 * direction controls the order in which vertices are emitted, which in
 * turn drives the winding number contributed by the contour.
 */
public enum class SkPathDirection { kCW, kCCW }
