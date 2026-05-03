package org.skia.foundation

import org.skia.skcms.SkcmsMatrix3x3

/**
 * Bit-compatible port of `SkColorSpacePrimaries`
 * ([include/core/SkColorSpace.h:25-40](file:///Users/chaos/workspace/kanvas-forge/skia-main/include/core/SkColorSpace.h)).
 *
 * Describes a color gamut by its three primary chromaticities (xy) plus a
 * white point (xy). Used as input to `skcms_PrimariesToXYZD50` to derive
 * the 3x3 matrix Skia stores in `SkNamedGamut`.
 *
 * The `toXYZD50` method is added in Phase E of
 * MIGRATION_PLAN_COLORSPACE_PORT.md (it depends on `skcms_PrimariesToXYZD50`
 * and `skcms_AdaptToXYZD50`, both Phase E ports).
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
     * Phase E will wire this to `skcms_PrimariesToXYZD50`. Until then,
     * unsupported.
     */
    public fun toXYZD50(): SkcmsMatrix3x3? =
        throw NotImplementedError(
            "SkColorSpacePrimaries.toXYZD50() requires skcms_PrimariesToXYZD50 (Phase E)"
        )
}
