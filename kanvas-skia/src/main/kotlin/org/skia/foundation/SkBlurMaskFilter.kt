package org.skia.foundation

import kotlin.math.ceil
import kotlin.math.exp

/**
 * Mirrors Skia's
 * [`SkBlurMaskFilter`](https://github.com/google/skia/blob/main/include/core/SkMaskFilter.h)
 * — Gaussian-blur the rasterised alpha mask before the paint colour
 * composite. Used by the canonical "drop-shadow / glow" idiom :
 *
 * ```
 * paint.maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, sigma)
 * canvas.drawRRect(rrect, paint)   // produces a soft-edged tinted
 *                                  // rectangle whose alpha is the
 *                                  // path's coverage convolved with a
 *                                  // 2D Gaussian of standard
 *                                  // deviation σ.
 * ```
 *
 * **Phase 7c coverage** :
 *  - [SkBlurStyle.kNormal] — only style fully implemented in this
 *    slice. Returns the convolution of the path's coverage with the
 *    2D Gaussian kernel.
 *  - [SkBlurStyle.kSolid], [SkBlurStyle.kOuter], [SkBlurStyle.kInner]
 *    — accepted by the constructor (so client GMs can be ported)
 *    but currently degrade to [SkBlurStyle.kNormal]. The visual
 *    difference (sharp original kept inside / outer-only ring /
 *    inner-only ring) is a Phase 7c' refinement if any GM in scope
 *    demands it.
 *
 * The kernel is **separable** : a 2D Gaussian of standard deviation
 * `σ` factorises into the convolution of a 1D horizontal Gaussian
 * with a 1D vertical Gaussian, both of standard deviation `σ`. We
 * apply the two passes sequentially via a scratch buffer.
 *
 * Margin around the rasterised path : `ceil(3 × σ)` pixels per side
 * (captures 99.7 % of the kernel mass per the 3-sigma rule).
 */
public class SkBlurMaskFilter private constructor(
    private val style: SkBlurStyle,
    private val sigma: Float,
) : SkMaskFilter() {

    private val radius: Int = ceil(3.0 * sigma).toInt().coerceAtLeast(1)
    private val kernel: FloatArray = gaussianKernel1D(sigma, radius)

    override fun margin(): Int = radius

    override fun filterMask(src: ByteArray, w: Int, h: Int): ByteArray {
        require(src.size == w * h) { "src.size (${src.size}) != $w × $h" }
        if (sigma <= 0f) return src
        // Separable 1D pass : horizontal then vertical.
        val tmp = ByteArray(w * h)
        blurHorizontal(src, tmp, w, h, kernel, radius)
        val out = ByteArray(w * h)
        blurVertical(tmp, out, w, h, kernel, radius)
        return out
    }

    public companion object {
        /**
         * Mirrors Skia's `SkMaskFilter::MakeBlur(style, sigma)`.
         * `sigma <= 0` returns `null` (no-op blur). Negative values
         * are treated as 0.
         */
        public fun Make(style: SkBlurStyle, sigma: Float): SkMaskFilter? {
            if (!sigma.isFinite() || sigma <= 0f) return null
            return SkBlurMaskFilter(style, sigma)
        }

        /**
         * Build a 1D Gaussian kernel of the given [sigma] and half-
         * width [radius]. The returned array has size `2*radius + 1`
         * with `kernel[radius]` = peak (centre). Coefficients are
         * normalised so they sum to 1.
         */
        private fun gaussianKernel1D(sigma: Float, radius: Int): FloatArray {
            val size = 2 * radius + 1
            val k = FloatArray(size)
            val twoSigmaSq = 2f * sigma * sigma
            var sum = 0f
            for (i in 0 until size) {
                val x = (i - radius).toFloat()
                val v = exp(-(x * x) / twoSigmaSq.toDouble()).toFloat()
                k[i] = v
                sum += v
            }
            // Normalise.
            for (i in 0 until size) k[i] /= sum
            return k
        }

        /**
         * 1D horizontal Gaussian convolution. Border pixels read
         * outside the buffer's left/right edges are clamped to 0
         * (the buffer is sized to include [radius] padding, so this
         * only matters for the very first / last [radius] columns).
         */
        private fun blurHorizontal(
            src: ByteArray, dst: ByteArray,
            w: Int, h: Int,
            kernel: FloatArray, radius: Int,
        ) {
            for (y in 0 until h) {
                val rowOffset = y * w
                for (x in 0 until w) {
                    var acc = 0f
                    for (k in -radius..radius) {
                        val xi = x + k
                        val v = if (xi in 0 until w) (src[rowOffset + xi].toInt() and 0xFF) else 0
                        acc += v * kernel[k + radius]
                    }
                    dst[rowOffset + x] = (acc + 0.5f).toInt().coerceIn(0, 255).toByte()
                }
            }
        }

        /** Vertical counterpart. */
        private fun blurVertical(
            src: ByteArray, dst: ByteArray,
            w: Int, h: Int,
            kernel: FloatArray, radius: Int,
        ) {
            for (y in 0 until h) {
                for (x in 0 until w) {
                    var acc = 0f
                    for (k in -radius..radius) {
                        val yi = y + k
                        val v = if (yi in 0 until h) (src[yi * w + x].toInt() and 0xFF) else 0
                        acc += v * kernel[k + radius]
                    }
                    dst[y * w + x] = (acc + 0.5f).toInt().coerceIn(0, 255).toByte()
                }
            }
        }
    }
}

/**
 * Mirrors Skia's
 * [`SkBlurStyle`](https://github.com/google/skia/blob/main/include/core/SkBlurTypes.h).
 *
 * Phase 7c implements [kNormal] only ; the other three are accepted
 * by the [SkBlurMaskFilter.Make] factory but currently render as
 * `kNormal`.
 */
public enum class SkBlurStyle {
    /** Convolution of the original mask with the 2D Gaussian. */
    kNormal,

    /**
     * Original mask kept opaque ; blur extends outward only. Phase 7c
     * accepts but currently renders as [kNormal].
     */
    kSolid,

    /**
     * Original interior cleared ; blurred ring outside the original.
     * Phase 7c accepts but currently renders as [kNormal].
     */
    kOuter,

    /**
     * Blurred mask clipped to the original interior. Phase 7c accepts
     * but currently renders as [kNormal].
     */
    kInner,
}
