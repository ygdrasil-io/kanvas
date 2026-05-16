package org.skia.foundation


import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkIPoint
import org.graphiks.math.SkMatrix

/**
 * Mirrors Skia's
 * [`SkMaskFilter`](https://github.com/google/skia/blob/main/include/core/SkMaskFilter.h)
 * — a transformation of the rasteriser's coverage mask, applied
 * **after** the path effect / stroker but **before** the colour
 * composite. Examples : Gaussian blur, emboss, table lookup.
 *
 * In the kanvas-skia raster pipeline the per-draw sequence for paths
 * with a non-null `paint.maskFilter` is :
 *
 * ```
 *   path → pathEffect → stroker → rasterise to mask
 *        → maskFilter (blur etc.) → tint with paint colour → blend
 * ```
 *
 * **Phase 7c status** : the abstract base ships alongside the
 * first concrete subclass, [SkBlurMaskFilter]. Phase **C3** adds
 * [SkEmbossMaskFilter] and the [Format.k3D] / [filterMask3D]
 * extension that lets a filter return three planes (alpha +
 * multiply + additive) so per-pixel lighting can modulate the
 * paint colour.
 *
 * Construct via the concrete subclass's `Make` companion factory.
 * The base type is exposed so client code can hold a generic
 * reference (`val mf: SkMaskFilter? = ...`).
 */
public abstract class SkMaskFilter {

    /**
     * Mirrors Skia's `SkMask::Format` — only the two values we
     * actually emit are exposed.
     *
     * - [kA8] : single-plane 8-bit coverage. The default for blur
     *   and most legacy filters. The output of [filterMask] is the
     *   final coverage mask the device tints with the paint colour.
     * - [k3D] : three planes (alpha + multiply + additive). Used by
     *   [SkEmbossMaskFilter] to carry per-pixel lighting alongside
     *   coverage. The device composites with
     *   `dst = paintColor × multiply / 255 + additive` per channel,
     *   then attenuates by `alpha` to produce the source pixel.
     */
    public enum class Format { kA8, k3D }

    /**
     * Output format of [filterMask] / [filterMask3D]. Default is
     * [Format.kA8] (single coverage plane) ; [SkEmbossMaskFilter]
     * overrides to [Format.k3D].
     */
    public open val format: Format get() = Format.kA8

    /**
     * Transform an alpha mask. [src] is a row-major 8-bit coverage
     * buffer of [w] × [h] entries (one byte per pixel, `0` =
     * uncovered, `255` = fully covered, in-between = AA). Returns
     * the new mask buffer of the same dimensions ; may be the same
     * object as [src] (mutated in place) or a fresh allocation.
     *
     * The buffer's `(0, 0)` is the top-left of the rasterisation
     * bounds expanded by [margin] pixels, so a blur kernel that
     * reads `±margin` neighbours never overflows.
     *
     * Filters that override [format] to [Format.k3D] should
     * implement [filterMask3D] instead and may delegate this
     * single-plane variant to a fallback (e.g. return [src] as is
     * — the caller checks [format] before composing).
     */
    public abstract fun filterMask(src: ByteArray, w: Int, h: Int): ByteArray

    /**
     * Three-plane variant for [Format.k3D] filters. Returns the
     * coverage alpha, a per-pixel multiply factor (`0..255`,
     * applied to the paint's RGB), and a per-pixel additive term
     * (`0..255`, added after the multiply for specular highlights).
     *
     * Default implementation degrades gracefully for [Format.kA8]
     * filters : the alpha plane is the result of [filterMask], the
     * multiply plane is constant `255` (no shading), and the
     * additive plane is constant `0` (no highlight). Devices that
     * always go through [filterMask3D] therefore get a working
     * answer even when the underlying filter is single-plane.
     */
    public open fun filterMask3D(src: ByteArray, w: Int, h: Int): Sk3DMask {
        val alpha = filterMask(src, w, h)
        val n = alpha.size
        val multiply = ByteArray(n) { 0xFF.toByte() }
        val additive = ByteArray(n)
        return Sk3DMask(alpha, multiply, additive)
    }

    /**
     * Pixels of padding the filter needs around the input mask.
     * For a Gaussian blur, this is `ceil(3 × sigma)` per side. The
     * device expands the path's device-space bounds by this much
     * before allocating the mask buffer.
     */
    public abstract fun margin(): Int

    /**
     * Phase R1-C — `true` if the filter's sigma scales with the CTM
     * (Skia default). When `false`, [withCtmScale] returns a filter
     * whose effective sigma is divided by the CTM scale so the blur
     * radius stays in source-pixel units regardless of the canvas
     * transform. Mirrors the `respectCTM` knob on Skia's
     * `SkMaskFilter::MakeBlur(style, sigma, respectCTM)` factory
     * (`include/core/SkMaskFilter.h:30-35`).
     *
     * The rasteriser walks the mask filter in device space (sigma in
     * device pixels), so this defaults to `true` ; `SkBlurMaskFilter`
     * built with `respectCTM = false` overrides to `false` and
     * provides a [withCtmScale] that rescales the sigma.
     */
    public open val respectCTM: Boolean get() = true

    /**
     * Phase R1-C — when [respectCTM] is `false`, return a filter
     * whose effective sigma is `sigma / scale` (so a CTM scale of 2
     * shrinks the blur radius by half — keeping the on-screen blur
     * footprint constant in source coordinates). When [respectCTM]
     * is `true` (the default), returns `this` unchanged.
     *
     * The device calls this with `ctm.computeMaxScale()` before
     * invoking [filterMask] / [margin] — `respectCTM = false` then
     * propagates a *new* mask filter with the rescaled radius.
     */
    public open fun withCtmScale(@Suppress("UNUSED_PARAMETER") scale: Float): SkMaskFilter = this

    /**
     * R-suivi.18 — bitmap-level convenience wrapper around [filterMask].
     * Reads the alpha channel of [srcBitmap] (which may be any colour
     * type — for non-[SkColorType.kAlpha_8] inputs we sample the alpha
     * via [SkBitmap.getPixel]), runs [filterMask] on the resulting byte
     * buffer (expanded by [margin] pixels per side so the kernel never
     * overflows), and returns a fresh [SkColorType.kAlpha_8] bitmap
     * holding the filtered coverage.
     *
     * The returned bitmap is `srcBitmap.width + 2 * margin` ×
     * `srcBitmap.height + 2 * margin` ; the original `(0, 0)` of the
     * source lands at `(margin, margin)` of the returned bitmap. When
     * [offset] is non-null, it is set to `(-margin, -margin)` so the
     * caller knows where the new mask's origin sits relative to the
     * source.
     *
     * The [ctm] argument is currently ignored — kanvas-skia's blur
     * filter operates in source-pixel units inside this helper (the
     * device-CTM-aware path is the per-draw `filterMask(ByteArray)`
     * variant invoked by the rasteriser). Reserved for future
     * filters whose kernel depends on the canvas transform.
     */
    public fun filterMask(
        srcBitmap: SkBitmap,
        @Suppress("UNUSED_PARAMETER") ctm: SkMatrix = SkMatrix.Identity,
        offset: SkIPoint? = null,
    ): SkBitmap {
        val srcW = srcBitmap.width
        val srcH = srcBitmap.height
        val m = margin()
        val expW = srcW + 2 * m
        val expH = srcH + 2 * m
        // Extract the source's alpha channel into a margin-expanded
        // 8-bit coverage buffer (zero-padded on all four sides).
        val srcAlpha = ByteArray(expW * expH)
        if (srcBitmap.colorType == SkColorType.kAlpha_8) {
            // Fast path : raw byte copy of the existing A8 plane,
            // placed at (margin, margin) inside the expanded buffer.
            for (y in 0 until srcH) {
                val dstRow = (y + m) * expW + m
                val srcRow = y * srcW
                System.arraycopy(srcBitmap.pixelsA8, srcRow, srcAlpha, dstRow, srcW)
            }
        } else {
            // General path : sample alpha via getPixel.
            for (y in 0 until srcH) {
                val dstRow = (y + m) * expW + m
                for (x in 0 until srcW) {
                    srcAlpha[dstRow + x] = SkColorGetA(srcBitmap.getPixel(x, y)).toByte()
                }
            }
        }
        val filtered = filterMask(srcAlpha, expW, expH)
        val out = SkBitmap(expW, expH, srcBitmap.colorSpace, SkColorType.kAlpha_8)
        System.arraycopy(filtered, 0, out.pixelsA8, 0, filtered.size)
        offset?.set(-m, -m)
        return out
    }

    public companion object {
        /**
         * Mirrors Skia's
         * [`SkMaskFilter::MakeBlur(style, sigma, respectCTM)`](https://github.com/google/skia/blob/main/include/core/SkMaskFilter.h)
         * (`include/core/SkMaskFilter.h:34-35`) — convenience pass-through
         * to [SkBlurMaskFilter.Make] so client code can spell the upstream
         * idiom verbatim. `respectCTM` defaults to `true` (Skia default) ;
         * `respectCTM = false` produces a filter whose blur radius is in
         * **source-pixel** units regardless of the active canvas CTM.
         *
         * Used by `gm/blurignorexform.cpp`.
         */
        public fun MakeBlur(style: SkBlurStyle, sigma: Float, respectCTM: Boolean = true): SkMaskFilter? =
            SkBlurMaskFilter.Make(style, sigma, respectCTM)
    }
}

/**
 * Three-plane coverage output for [SkMaskFilter.Format.k3D] filters
 * — one row-major byte buffer per plane, all of the same dimensions
 * (`w × h` per the call to [SkMaskFilter.filterMask3D]).
 *
 * Mirrors Skia's `SkMask::k3D_Format` layout, which packs the three
 * planes contiguously into a single allocation (`alpha`,
 * `multiply`, `additive` in that order). We expose them as separate
 * Kotlin arrays to keep the consumer code clean — the device walks
 * three indexed reads per pixel anyway.
 *
 * Composite formula at each pixel `(x, y)` :
 * ```
 *   dst.r = clamp((paint.r * multiply + 127) / 255 + additive, 0, 255)
 *   dst.g = clamp((paint.g * multiply + 127) / 255 + additive, 0, 255)
 *   dst.b = clamp((paint.b * multiply + 127) / 255 + additive, 0, 255)
 *   dst.a = (paint.a * alpha + 127) / 255
 * ```
 */
public data class Sk3DMask(
    val alpha: ByteArray,
    val multiply: ByteArray,
    val additive: ByteArray,
) {
    init {
        require(alpha.size == multiply.size && multiply.size == additive.size) {
            "Sk3DMask plane sizes must match : alpha=${alpha.size}, " +
                "multiply=${multiply.size}, additive=${additive.size}"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Sk3DMask) return false
        return alpha.contentEquals(other.alpha) &&
            multiply.contentEquals(other.multiply) &&
            additive.contentEquals(other.additive)
    }

    override fun hashCode(): Int {
        var r = alpha.contentHashCode()
        r = 31 * r + multiply.contentHashCode()
        r = 31 * r + additive.contentHashCode()
        return r
    }
}
