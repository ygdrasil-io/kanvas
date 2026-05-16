package org.skia.foundation

import org.graphiks.math.SkcmsMatrix3x3
import org.skia.foundation.skcms.skcmsPrimariesToXYZD50

/**
 * Bit-compatible port of `SkColorSpacePrimaries`
 * ([include/core/SkColorSpace.h:25-40](file:///Users/chaos/workspace/kanvas-forge/skia-main/include/core/SkColorSpace.h)).
 *
 * Describes a color gamut by its three primary chromaticities (xy) plus a
 * white point (xy). [toXYZD50] derives the 3x3 matrix Skia stores in
 * `SkNamedGamut`.
 */
public data class SkColorSpacePrimaries(
    public val fRX: Float,
    public val fRY: Float,
    public val fGX: Float,
    public val fGY: Float,
    public val fBX: Float,
    public val fBY: Float,
    public val fWX: Float,
    public val fWY: Float,
) {
    /**
     * Convert these primaries + white point into a `toXYZD50` matrix.
     * Returns `null` if any input is out of `[0, 1]` or the primaries
     * matrix is singular. Mirrors upstream
     * `SkColorSpacePrimaries::toXYZD50` (`SkColorSpace.cpp:124-126`),
     * which delegates to `skcms_PrimariesToXYZD50`.
     */
    public fun toXYZD50(): SkcmsMatrix3x3? =
        skcmsPrimariesToXYZD50(fRX, fRY, fGX, fGY, fBX, fBY, fWX, fWY)
}
