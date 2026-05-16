@file:JvmName("SkColorSpacePriv")

package org.skia.foundation

import org.skia.foundation.skcms.SkNamedTransferFn
import org.skia.foundation.skcms.SkcmsMatrix3x3
import org.skia.foundation.skcms.SkcmsTransferFunction
import kotlin.math.abs

/**
 * Bit-compatible port of helpers in
 * [src/core/SkColorSpacePriv.h](file:///Users/chaos/workspace/kanvas-forge/skia-main/src/core/SkColorSpacePriv.h):21-65.
 *
 * Used by [SkColorSpace.makeRGB] to snap a quasi-standard transfer function
 * or gamut to the matching `SkNamedTransferFn::k*` / `SkNamedGamut::k*`
 * singleton, so that:
 *  - `gammaCloseToSRGB()` (which does an exact compare) returns `true` for
 *    a TF that originally landed within ICC-fixed-point precision of sRGB;
 *  - `Equals(a, b)` becomes pointer-equality for common cases instead of
 *    drifting because of s15Fixed16 truncation.
 */

/** Tolerance for matrix cells (gamut comparisons). Mirrors upstream `0.01f`. */
public const val COLORSPACE_ALMOST_EQUAL_TOLERANCE: Float = 0.01f

/** Tighter tolerance for transfer function parameters. Mirrors upstream `0.001f`. */
public const val TRANSFER_FN_ALMOST_EQUAL_TOLERANCE: Float = 0.001f

internal fun colorSpaceAlmostEqual(a: Float, b: Float): Boolean =
    abs(a - b) < COLORSPACE_ALMOST_EQUAL_TOLERANCE

internal fun transferFnAlmostEqual(a: Float, b: Float): Boolean =
    abs(a - b) < TRANSFER_FN_ALMOST_EQUAL_TOLERANCE

/**
 * Cell-by-cell `colorSpaceAlmostEqual` on two 3x3 matrices.
 */
public fun xyzAlmostEqual(mA: SkcmsMatrix3x3, mB: SkcmsMatrix3x3): Boolean {
    for (r in 0 until 3) for (c in 0 until 3) {
        if (!colorSpaceAlmostEqual(mA.vals[r][c], mB.vals[r][c])) return false
    }
    return true
}

/**
 * `true` if `tf` is parametrically equivalent to `SkNamedTransferFn.kSRGB`
 * within `transferFnAlmostEqual` (per-component tolerance `0.001`).
 */
public fun isAlmostSRGB(tf: SkcmsTransferFunction): Boolean {
    val srgb = SkNamedTransferFn.kSRGB
    return transferFnAlmostEqual(srgb.a, tf.a) &&
        transferFnAlmostEqual(srgb.b, tf.b) &&
        transferFnAlmostEqual(srgb.c, tf.c) &&
        transferFnAlmostEqual(srgb.d, tf.d) &&
        transferFnAlmostEqual(srgb.e, tf.e) &&
        transferFnAlmostEqual(srgb.f, tf.f) &&
        transferFnAlmostEqual(srgb.g, tf.g)
}

/**
 * `true` if `tf` matches a 2.2-power TF: g≈2.2, a=1, b=e=0, d≤0 (the linear
 * branch is unreachable). Mirrors upstream `is_almost_2dot2`.
 */
public fun isAlmost2Dot2(tf: SkcmsTransferFunction): Boolean =
    transferFnAlmostEqual(1f, tf.a) &&
        transferFnAlmostEqual(0f, tf.b) &&
        transferFnAlmostEqual(0f, tf.e) &&
        transferFnAlmostEqual(2.2f, tf.g) &&
        tf.d <= 0f

/**
 * `true` if `tf` is the identity transfer function. Two encoding forms:
 *  - exponential: `a=1, b=e=0, g=1, d≤0` (so `y = x^1 = x` in the power branch).
 *  - linear: `c=1, f=0, d≥1` (so `y = 1*x + 0 = x` in the linear branch).
 *
 * Mirrors upstream `is_almost_linear`.
 */
public fun isAlmostLinear(tf: SkcmsTransferFunction): Boolean {
    // Form 1: y = x^1
    val linearExp = transferFnAlmostEqual(1f, tf.a) &&
        transferFnAlmostEqual(0f, tf.b) &&
        transferFnAlmostEqual(0f, tf.e) &&
        transferFnAlmostEqual(1f, tf.g) &&
        tf.d <= 0f

    // Form 2: y = 1*x + 0
    val linearFn = transferFnAlmostEqual(1f, tf.c) &&
        transferFnAlmostEqual(0f, tf.f) &&
        tf.d >= 1f

    return linearExp || linearFn
}
