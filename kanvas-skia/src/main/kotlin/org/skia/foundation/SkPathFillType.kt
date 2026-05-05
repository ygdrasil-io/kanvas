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
    kInverseEvenOdd;

    /**
     * True if the rule treats odd crossings as "inside". Mirrors
     * `SkPathFillType_IsEvenOdd` (`include/core/SkPathTypes.h:28-30`):
     * bit 0 of the ordinal distinguishes winding (0) from even-odd (1).
     */
    public fun isEvenOdd(): Boolean = (ordinal and 1) != 0

    /**
     * True if the rule fills the *complement* of the path's interior
     * (clipped to the device bounds). Mirrors `SkPathFillType_IsInverse`
     * (`include/core/SkPathTypes.h:32-34`): bit 1 of the ordinal
     * distinguishes inverse (1) from non-inverse (0).
     */
    public fun isInverse(): Boolean = (ordinal and 2) != 0

    /**
     * Flip the inverse bit of this fill rule, leaving the
     * winding/even-odd choice untouched. Mirrors
     * `SkPathFillType_ToggleInverse` (`include/core/SkPathTypes.h:36-38`).
     */
    public fun toggleInverse(): SkPathFillType = entries[ordinal xor 2]

    /**
     * Drop the inverse bit, returning the corresponding non-inverse
     * fill rule. Mirrors `SkPathFillType_ConvertToNonInverse`
     * (`include/core/SkPathTypes.h:40-42`).
     */
    public fun convertToNonInverse(): SkPathFillType = entries[ordinal and 1]
}
