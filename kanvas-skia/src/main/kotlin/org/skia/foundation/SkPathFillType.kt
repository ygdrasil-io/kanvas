package org.skia.foundation

/**
 * Fill rule for `SkPath`. Mirrors Skia's `SkPathFillType` enum.
 *
 * - `kWinding` : non-zero winding rule. A point is inside iff the winding
 *   number of the contour around it is non-zero.
 * - `kEvenOdd` : a point is inside iff it has an odd number of crossings.
 * - `kInverseWinding` / `kInverseEvenOdd` : invert the result (everything
 *   outside the standard "inside" set, clipped to the device bounds).
 *   **Phase 3a does not implement the inverse variants.**
 */
public enum class SkPathFillType {
    kWinding,
    kEvenOdd,
    kInverseWinding,
    kInverseEvenOdd,
}
