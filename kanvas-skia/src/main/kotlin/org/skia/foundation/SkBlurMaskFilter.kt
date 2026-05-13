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
 * **Phase 7c (current)** — all four [SkBlurStyle] values implemented :
 *  - [SkBlurStyle.kNormal] — convolution of the path's coverage with
 *    the 2D Gaussian.
 *  - [SkBlurStyle.kSolid] — original mask kept opaque + outer blur
 *    halo. Combination : `out = orig + blur·(255 − orig) / 255`.
 *  - [SkBlurStyle.kOuter] — only the outer halo (the original
 *    interior is cleared). Combination : `out = blur·(255 − orig) / 255`.
 *  - [SkBlurStyle.kInner] — blur clipped to the original interior.
 *    Combination : `out = blur·orig / 255`.
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
    private val respectCTMFlag: Boolean = true,
) : SkMaskFilter() {

    private val radius: Int = ceil(3.0 * sigma).toInt().coerceAtLeast(1)
    private val kernel: FloatArray = gaussianKernel1D(sigma, radius)

    override val respectCTM: Boolean get() = respectCTMFlag

    /**
     * Phase R1-C — when `respectCTM = false` (constructed via
     * [Make] with the 3-arg overload), the device calls this with the
     * active CTM's max scale ; we return a *new* blur filter whose
     * effective sigma is `sigma / scale`, so the blur radius stays
     * constant in source-pixel units. When `respectCTM = true`, the
     * filter is returned unchanged.
     */
    override fun withCtmScale(scale: Float): SkMaskFilter {
        if (respectCTMFlag) return this
        if (scale <= 0f || !scale.isFinite() || scale == 1f) return this
        val scaledSigma = (sigma / scale).coerceAtLeast(1e-3f)
        return SkBlurMaskFilter(style, scaledSigma, respectCTMFlag = false)
    }

    override fun margin(): Int = radius

    override fun filterMask(src: ByteArray, w: Int, h: Int): ByteArray {
        require(src.size == w * h) { "src.size (${src.size}) != $w × $h" }
        if (sigma <= 0f) return src
        // Separable 1D pass : horizontal then vertical.
        val tmp = ByteArray(w * h)
        blurHorizontal(src, tmp, w, h, kernel, radius)
        val blur = ByteArray(w * h)
        blurVertical(tmp, blur, w, h, kernel, radius)
        return when (style) {
            SkBlurStyle.kNormal -> blur
            SkBlurStyle.kSolid -> combineSolid(src, blur)
            SkBlurStyle.kOuter -> combineOuter(src, blur)
            SkBlurStyle.kInner -> combineInner(src, blur)
        }
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
         * Phase R1-C — 3-arg overload mirroring Skia's
         * `SkMaskFilter::MakeBlur(style, sigma, respectCTM)`
         * (`include/core/SkMaskFilter.h:34-35`). When `respectCTM` is
         * `false`, the device subsequently calls [SkMaskFilter.withCtmScale]
         * to rescale `sigma` by `1 / ctmScale` so the on-screen blur
         * footprint stays constant in source-pixel units regardless of
         * the active canvas CTM.
         *
         * Used by `gm/blurignorexform.cpp` (3 variants — 99 % similarity).
         */
        public fun Make(style: SkBlurStyle, sigma: Float, respectCTM: Boolean): SkMaskFilter? {
            if (!sigma.isFinite() || sigma <= 0f) return null
            return SkBlurMaskFilter(style, sigma, respectCTMFlag = respectCTM)
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

        /**
         * [SkBlurStyle.kSolid] — composite the original sharp interior
         * with the outer blur halo : `out = orig + blur·(255 − orig) / 255`.
         * Inside the path (orig=255) the result is fully opaque ;
         * outside (orig=0) it is the bare blur. Mirrors Skia's
         * `SkBlurMask.cpp::Blur(...)` solid path.
         */
        private fun combineSolid(orig: ByteArray, blur: ByteArray): ByteArray {
            require(orig.size == blur.size)
            val out = ByteArray(orig.size)
            for (i in orig.indices) {
                val o = orig[i].toInt() and 0xFF
                val b = blur[i].toInt() and 0xFF
                // out = o + b·(255 − o) / 255  (rounded).
                val v = o + (b * (255 - o) + 127) / 255
                out[i] = (if (v > 255) 255 else v).toByte()
            }
            return out
        }

        /**
         * [SkBlurStyle.kOuter] — keep only the outer halo : the part of
         * the blur that sits outside the original. `out = blur·(255 − orig) / 255`.
         * Pixels fully inside the path (orig=255) become 0 ; pixels
         * fully outside (orig=0) preserve the bare blur.
         */
        private fun combineOuter(orig: ByteArray, blur: ByteArray): ByteArray {
            require(orig.size == blur.size)
            val out = ByteArray(orig.size)
            for (i in orig.indices) {
                val o = orig[i].toInt() and 0xFF
                val b = blur[i].toInt() and 0xFF
                out[i] = ((b * (255 - o) + 127) / 255).toByte()
            }
            return out
        }

        /**
         * [SkBlurStyle.kInner] — clip the blur to the original interior.
         * `out = blur·orig / 255`. The path interior fades according to
         * the blur, with full opacity at the centre and softening towards
         * the edges. Pixels outside the path (orig=0) are zeroed.
         */
        private fun combineInner(orig: ByteArray, blur: ByteArray): ByteArray {
            require(orig.size == blur.size)
            val out = ByteArray(orig.size)
            for (i in orig.indices) {
                val o = orig[i].toInt() and 0xFF
                val b = blur[i].toInt() and 0xFF
                out[i] = ((b * o + 127) / 255).toByte()
            }
            return out
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
 * All four styles are now rendered correctly (Phase 7c). The combiner
 * formulas live alongside the Gaussian convolution in [SkBlurMaskFilter].
 */
public enum class SkBlurStyle {
    /** Convolution of the original mask with the 2D Gaussian. */
    kNormal,

    /** Original mask kept opaque ; blur extends outward only. */
    kSolid,

    /** Original interior cleared ; blurred ring outside the original. */
    kOuter,

    /** Blurred mask clipped to the original interior. */
    kInner,
}
