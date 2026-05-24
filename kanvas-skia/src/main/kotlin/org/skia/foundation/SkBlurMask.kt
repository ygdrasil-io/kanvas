package org.skia.foundation

import org.graphiks.math.SkIRect
import org.graphiks.math.SkRect
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Mirrors the internal Skia helper
 * [`SkBlurMask`](https://github.com/google/skia/blob/main/src/core/SkBlurMask.h)
 * — low-level utility that computes Gaussian-blurred alpha masks for
 * geometric primitives without going through the full paint / canvas
 * machinery.
 *
 * The Kotlin surface exposes only the small subset required by
 * `:skia-integration-tests` GMs (`blurrect_gallery`). `BlurRect` shares
 * the same separable Gaussian implementation as [SkBlurMaskFilter], which
 * keeps the low-level helper behaviour aligned with the paint mask-filter
 * path.
 *
 * Upstream C++ lives in `src/core/SkBlurMask.cpp`.
 */
public object SkBlurMask {

    /**
     * Empirical radius → sigma conversion used across Skia's blur
     * machinery:
     *
     * ```
     * sigma = radius * (1 / √3) + 0.5
     * ```
     *
     * Mirrors `SkBlurMask::ConvertRadiusToSigma` (src/core/SkBlurMask.cpp).
     */
    public fun ConvertRadiusToSigma(radius: Float): Float =
        if (radius > 0f) 0.57735f * radius + 0.5f else 0f

    /**
     * Compute the Gaussian blur mask for an axis-aligned rectangle.
     *
     * Returns a [BlurRectResult] describing the alpha-mask bitmap whose
     * pixel grid is expanded by the blur spread on all four sides. The
     * caller draws the result as an A8 image using `drawImage`.
     *
     * Returns `null` if the sigma or rect is degenerate (mirrors the
     * `bool` return of the C++ original — callers must guard against
     * `null`).
     *
     * Upstream: `SkBlurMask::BlurRect(sigma, *mask, bounds, style)`
     * (`src/core/SkBlurMask.cpp`). Skia's C++ implementation uses an
     * analytic rectangle fast path; this port constructs the equivalent
     * source A8 rect and delegates the Gaussian/style composition to the
     * existing mask filter.
     */
    public fun BlurRect(
        sigma: Float,
        bounds: SkRect,
        style: SkBlurStyle,
    ): BlurRectResult? {
        if (!sigma.isFinite() || sigma <= 0f) return null
        if (bounds.isEmpty) return null

        val filter = SkBlurMaskFilter.Make(style, sigma) ?: return null
        val margin = filter.margin()

        val left = floor(bounds.left.toDouble()).toInt()
        val top = floor(bounds.top.toDouble()).toInt()
        val right = ceil(bounds.right.toDouble()).toInt()
        val bottom = ceil(bounds.bottom.toDouble()).toInt()
        if (right <= left || bottom <= top) return null

        val rectW = right - left
        val rectH = bottom - top
        val maskW = rectW + 2 * margin
        val maskH = rectH + 2 * margin
        if (maskW <= 0 || maskH <= 0) return null

        val source = ByteArray(maskW * maskH)
        for (y in 0 until rectH) {
            val dst = (y + margin) * maskW + margin
            java.util.Arrays.fill(source, dst, dst + rectW, 0xFF.toByte())
        }

        val image = filter.filterMask(source, maskW, maskH)
        val resultBounds = SkIRect.MakeLTRB(left - margin, top - margin, right + margin, bottom + margin)
        return BlurRectResult(resultBounds, image, maskW)
    }

    /**
     * Result of [BlurRect] — mirrors the layout of Skia's `SkMask`
     * structure: a row-major A8 pixel buffer, its axis-aligned bounds
     * (in local coordinates), and the row stride in bytes.
     *
     * The caller is responsible for reading [fRowBytes] correctly when
     * the stride is wider than [fBounds].width().
     */
    public class BlurRectResult(
        /** Axis-aligned bounds of the output mask in local coordinates. */
        public val fBounds: SkIRect,
        /** Alpha channel pixel data, row-major, [fRowBytes] bytes per row. */
        public val image: ByteArray,
        /** Row stride in bytes (>= fBounds.width()). */
        public val fRowBytes: Int,
    )
}
