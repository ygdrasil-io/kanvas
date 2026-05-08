package org.skia.foundation

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
