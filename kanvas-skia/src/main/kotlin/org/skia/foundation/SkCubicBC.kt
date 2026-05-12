package org.skia.foundation

import kotlin.math.abs

/**
 * Internal kernel evaluator for the Mitchell-Netravali B-C cubic
 * family used by [SkCubicResampler] / [SkSamplingOptions.cubic].
 *
 * Upstream Skia evaluates the same piecewise cubic in
 * `src/core/SkBitmapProcState_matrix.h` and the closed-form scalar
 * fast path in `src/shaders/SkImageShader.cpp::cubic_eval`.
 *
 * For a sample at fractional offset `t` (always non-negative — caller
 * passes `|distance|`) :
 *  - `t < 1` :     `((12 - 9B - 6C) t³ + (-18 + 12B + 6C) t² + (6 - 2B)) / 6`
 *  - `1 ≤ t < 2` : `((-B - 6C) t³ + (6B + 30C) t² + (-12B - 48C) t + (8B + 24C)) / 6`
 *  - `t ≥ 2`     : `0`
 *
 * The kernel sums to 1 over the 4 samples at offsets
 * `(1 + fx, fx, 1 - fx, 2 - fx)` (for `fx ∈ [0, 1]`) — this is the
 * partition-of-unity property of the Mitchell-Netravali family.
 */
internal object SkCubicBC {
    fun weight(t: Float, B: Float, C: Float): Float {
        val x = abs(t)
        if (x >= 2f) return 0f
        if (x < 1f) {
            val x2 = x * x
            val x3 = x2 * x
            return ((12f - 9f * B - 6f * C) * x3 +
                    (-18f + 12f * B + 6f * C) * x2 +
                    (6f - 2f * B)) / 6f
        }
        val x2 = x * x
        val x3 = x2 * x
        return ((-B - 6f * C) * x3 +
                (6f * B + 30f * C) * x2 +
                (-12f * B - 48f * C) * x +
                (8f * B + 24f * C)) / 6f
    }
}
