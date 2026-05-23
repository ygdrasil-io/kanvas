package org.skia.foundation

import org.graphiks.math.SkIRect
import org.graphiks.math.SkRect

/**
 * Mirrors the internal Skia helper
 * [`SkBlurMask`](https://github.com/google/skia/blob/main/src/core/SkBlurMask.h)
 * — low-level utility that computes Gaussian-blurred alpha masks for
 * geometric primitives without going through the full paint / canvas
 * machinery.
 *
 * The Kotlin surface exposes only the small subset required by
 * `:skia-integration-tests` GMs (`blurrect_gallery`). The underlying
 * `BlurRect` algorithm (analytically compute the Gaussian convolution
 * of a rectangle's coverage at each pixel) is a non-trivial
 * implementation, deferred as a stub.
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
     * (`src/core/SkBlurMask.cpp`). The full analytic implementation is
     * deferred; this stub always throws [NotImplementedError] with tag
     * `STUB.BLURRECT_GALLERY` so the test can be @Disabled appropriately.
     */
    public fun BlurRect(
        sigma: Float,
        bounds: SkRect,
        style: SkBlurStyle,
    ): BlurRectResult? = TODO("STUB.BLURRECT_GALLERY")

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
