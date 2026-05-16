@file:JvmName("SkcmsKnownProfiles")

package org.skia.foundation.skcms
import org.skia.math.SkcmsTransferFunction
import org.skia.math.SkcmsMatrix3x3

import org.skia.foundation.transferFnAlmostEqual
import org.skia.foundation.xyzAlmostEqual

/**
 * Phase F6 of `MIGRATION_PLAN_COLORSPACE_PORT.md` — well-known profile
 * builders and `skcmsApproximatelyEqualProfiles`.
 *
 * Mirrors the C functions in `skcms.cc:1511-1709, 1741-1797`. The
 * [skcmsSrgbProfile] / [skcmsXyzd50Profile] singletons construct the
 * canonical sRGB / identity ICC profile shapes that Skia's API surface
 * exposes for callers that want a "default" profile without parsing
 * one. They're useful as the dst of a default xform, or as inputs to
 * `SkColorSpace.make(profile)`.
 *
 * `skcmsApproximatelyEqualProfiles` is ported as a structural equality
 * (matrix tolerance + per-channel TRC equivalence) rather than the
 * upstream `skcms_Transform`-based byte-comparison: porting the full
 * skcms transform pipeline is Phase K, and the structural variant
 * captures the same intent ("same gamut + same TRC, modulo the noise
 * an ICC parser introduces") for the consumer cases the colorspace
 * plan covers.
 */

/**
 * The canonical sRGB ICC profile. Mirror of [skcms.cc:1511-1607].
 *
 * Three identical sRGB parametric TRCs + the Bradford-adapted sRGB
 * gamut to D50. Useful as a "default" profile when callers want to
 * transform RGB pixels without first parsing an ICC blob.
 */
public val skcmsSrgbProfile: SkcmsICCProfile = SkcmsICCProfile(
    buffer = null,
    size = 0,
    dataColorSpace = SkcmsSignature.RGB.value,
    pcs = SkcmsSignature.XYZ.value,
    tagCount = 0,
    trc = arrayOf(
        SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB),
        SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB),
        SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB),
    ),
    toXYZD50 = SkNamedGamut.kSRGB,
    hasTrc = true,
    hasToXYZD50 = true,
)

/**
 * The identity profile: linear TRCs and identity gamut matrix. Mirror
 * of [skcms.cc:1609-1705]. Used by upstream's `skcmsApproximatelyEqualProfiles`
 * as the comparison anchor; we keep it for consumers that need an
 * explicit "no-op" profile (e.g. an XYZ working space).
 */
public val skcmsXyzd50Profile: SkcmsICCProfile = SkcmsICCProfile(
    buffer = null,
    size = 0,
    dataColorSpace = SkcmsSignature.RGB.value,
    pcs = SkcmsSignature.XYZ.value,
    tagCount = 0,
    trc = arrayOf(
        SkcmsCurve.Parametric(SkNamedTransferFn.kLinear),
        SkcmsCurve.Parametric(SkNamedTransferFn.kLinear),
        SkcmsCurve.Parametric(SkNamedTransferFn.kLinear),
    ),
    toXYZD50 = SkcmsMatrix3x3.IDENTITY,
    hasTrc = true,
    hasToXYZD50 = true,
)

/** Trivial accessor mirroring `skcms_sRGB_TransferFunction()` upstream. */
public fun skcmsSrgbTransferFunction(): SkcmsTransferFunction = SkNamedTransferFn.kSRGB

/**
 * Structural variant of `skcms_ApproximatelyEqualProfiles` (skcms.cc:1741-1797).
 *
 * Returns `true` when:
 *  - `a === b` (trivial identity), OR
 *  - both profiles have a usable `toXYZD50` and a usable per-channel
 *    `trc` array, both are RGB-or-Gray (CMYK is a separate ballgame
 *    per upstream), the gamuts match within `xyzAlmostEqual` tolerance
 *    (`0.01` per cell), and each `trc[i]` is structurally equivalent
 *    to `b.trc[i]`.
 *
 * TRC equivalence:
 *  - Two `Parametric` curves: every TF parameter within `0.001`
 *    (`transferFnAlmostEqual`).
 *  - Two `Table` curves: same byte arrays.
 *  - Cross types (Parametric vs Table): not equivalent — comparing them
 *    properly requires sampling the parametric form at the table grid,
 *    a Phase K-class operation. We conservatively return `false`.
 *
 * The upstream byte-comparison via `skcms_Transform` would catch a
 * couple more equivalence cases than the structural check (e.g. a
 * tabulated sRGB curve vs the parametric sRGB), but those don't matter
 * for the Make(profile) / Equal-profile fast-paths the colorspace plan
 * targets.
 */
public fun skcmsApproximatelyEqualProfiles(
    a: SkcmsICCProfile,
    b: SkcmsICCProfile,
): Boolean {
    if (a === b) return true
    if (!a.hasToXYZD50 || !b.hasToXYZD50) return false
    if (!a.hasTrc || !b.hasTrc) return false

    // CMYK is not equivalent to RGB even under the structural check.
    val aIsCmyk = a.dataColorSpace == SkcmsSignature.CMYK.value
    val bIsCmyk = b.dataColorSpace == SkcmsSignature.CMYK.value
    if (aIsCmyk != bIsCmyk) return false

    if (!xyzAlmostEqual(a.toXYZD50, b.toXYZD50)) return false

    for (i in 0 until 3) {
        val ca = a.trc[i] ?: return false
        val cb = b.trc[i] ?: return false
        if (!curvesAlmostEqual(ca, cb)) return false
    }
    return true
}

private fun curvesAlmostEqual(a: SkcmsCurve, b: SkcmsCurve): Boolean = when {
    a is SkcmsCurve.Parametric && b is SkcmsCurve.Parametric -> {
        val ta = a.parametric
        val tb = b.parametric
        transferFnAlmostEqual(ta.g, tb.g) &&
            transferFnAlmostEqual(ta.a, tb.a) &&
            transferFnAlmostEqual(ta.b, tb.b) &&
            transferFnAlmostEqual(ta.c, tb.c) &&
            transferFnAlmostEqual(ta.d, tb.d) &&
            transferFnAlmostEqual(ta.e, tb.e) &&
            transferFnAlmostEqual(ta.f, tb.f)
    }
    a is SkcmsCurve.Table && b is SkcmsCurve.Table -> a == b
    else -> false
}
