package org.skia.foundation

import org.graphiks.math.SkScalar

/**
 * Mirrors Skia's `SkFontMetrics` (`include/core/SkFontMetrics.h`).
 *
 * Out-param holder — Skia's `SkFont::getMetrics(SkFontMetrics*)` writes
 * into a caller-provided struct. We keep the same pattern: every field
 * is `var` and [SkFont.getMetrics] populates them in place.
 *
 * Field names use the upstream `f`-prefixed names (`fAscent`, `fDescent`,
 * …) so direct ports of `.cpp` test code drop in without renaming.
 *
 * **Y-down convention**: Skia uses screen y-down, so [fAscent] is
 * **typically negative** (above baseline) and [fDescent] is positive.
 *
 * The [fFlags] bitfield tells callers which optional fields are valid;
 * the four `*IsValid_Flag` constants below match upstream bits exactly.
 */
public class SkFontMetrics {
    public var fFlags: Int = 0

    public var fTop: SkScalar = 0f
    public var fAscent: SkScalar = 0f
    public var fDescent: SkScalar = 0f
    public var fBottom: SkScalar = 0f
    public var fLeading: SkScalar = 0f
    public var fAvgCharWidth: SkScalar = 0f
    public var fMaxCharWidth: SkScalar = 0f
    public var fXMin: SkScalar = 0f
    public var fXMax: SkScalar = 0f
    public var fXHeight: SkScalar = 0f
    public var fCapHeight: SkScalar = 0f
    public var fUnderlineThickness: SkScalar = 0f
    public var fUnderlinePosition: SkScalar = 0f
    public var fStrikeoutThickness: SkScalar = 0f
    public var fStrikeoutPosition: SkScalar = 0f

    /** Mirrors `SkFontMetrics::hasUnderlineThickness`. */
    public fun hasUnderlineThickness(out: FloatArray): Boolean {
        if ((fFlags and kUnderlineThicknessIsValid_Flag) != 0) {
            out[0] = fUnderlineThickness
            return true
        }
        return false
    }

    /** Mirrors `SkFontMetrics::hasUnderlinePosition`. */
    public fun hasUnderlinePosition(out: FloatArray): Boolean {
        if ((fFlags and kUnderlinePositionIsValid_Flag) != 0) {
            out[0] = fUnderlinePosition
            return true
        }
        return false
    }

    /** Mirrors `SkFontMetrics::hasStrikeoutThickness`. */
    public fun hasStrikeoutThickness(out: FloatArray): Boolean {
        if ((fFlags and kStrikeoutThicknessIsValid_Flag) != 0) {
            out[0] = fStrikeoutThickness
            return true
        }
        return false
    }

    /** Mirrors `SkFontMetrics::hasStrikeoutPosition`. */
    public fun hasStrikeoutPosition(out: FloatArray): Boolean {
        if ((fFlags and kStrikeoutPositionIsValid_Flag) != 0) {
            out[0] = fStrikeoutPosition
            return true
        }
        return false
    }

    public companion object {
        public const val kUnderlineThicknessIsValid_Flag: Int = 1 shl 0
        public const val kUnderlinePositionIsValid_Flag: Int = 1 shl 1
        public const val kStrikeoutThicknessIsValid_Flag: Int = 1 shl 2
        public const val kStrikeoutPositionIsValid_Flag: Int = 1 shl 3
        public const val kBoundsInvalid_Flag: Int = 1 shl 4
    }
}
