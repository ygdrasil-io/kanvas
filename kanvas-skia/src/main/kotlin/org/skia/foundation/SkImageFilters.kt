package org.skia.foundation

import org.skia.math.SkMatrix

/**
 * Mirrors Skia's
 * [`SkImageFilters`](https://github.com/google/skia/blob/main/include/effects/SkImageFilters.h)
 * factory namespace — the canonical set of [SkImageFilter] builders.
 *
 * **Phase 7d.1 ships** :
 *  - [Offset] — translate the image by `(dx, dy)` device pixels.
 *  - [ColorFilter] — apply an [SkColorFilter] per pixel.
 *  - [Compose] — chain `outer(inner(image))`.
 *
 * **Phase 7d.2 will add** : `Blur`, `MatrixTransform`, `DropShadow`,
 * and a port of `gm/imagefiltersbase.cpp` for end-to-end validation.
 */
public object SkImageFilters {

    /**
     * Mirrors Skia's `SkImageFilters::Offset(dx, dy, input)` — shifts
     * the image by `(dx, dy)` device pixels. The offset scales with
     * the canvas's max scale at draw time so a constant `(dx, dy)`
     * displacement stays visually-equivalent under different CTMs.
     *
     * `input == null` is the identity input (the source image passed
     * to `filterImage`). Otherwise [input]'s output is offset.
     */
    public fun Offset(dx: Float, dy: Float, input: SkImageFilter? = null): SkImageFilter =
        SkOffsetImageFilter(dx, dy, input)

    /**
     * Mirrors Skia's `SkImageFilters::ColorFilter(cf, input)` —
     * applies [cf] to every pixel of the (possibly chained) input
     * image. Identical math to `paint.colorFilter` but applied
     * **before** the blend instead of post-blend, which lets effect
     * chains compose colour operations with structural transforms
     * (offset, blur, etc.).
     */
    public fun ColorFilter(cf: SkColorFilter, input: SkImageFilter? = null): SkImageFilter =
        SkColorFilterImageFilter(cf, input)

    /**
     * Mirrors Skia's `SkImageFilters::Compose(outer, inner)` — chains
     * `outer.filterImage(inner.filterImage(src))`. Same null-handling
     * convention as [SkPathEffect.MakeCompose] :
     *  - `outer == null` ⇒ returns [inner] (or null if both are null).
     *  - `inner == null` ⇒ returns [outer].
     *  - both null ⇒ returns null.
     */
    public fun Compose(outer: SkImageFilter?, inner: SkImageFilter?): SkImageFilter? {
        if (outer == null) return inner
        if (inner == null) return outer
        return SkComposeImageFilter(outer, inner)
    }
}

// -- Internal concrete implementations --------------------------------------

/**
 * `Offset` — translates the (possibly chained) input by `(dx, dy)`
 * device pixels. The displacement scales with the canvas's max
 * scale (so a 10-px offset stays 10 px under different CTMs from
 * the device's perspective). Image pixels themselves are unchanged.
 */
internal class SkOffsetImageFilter(
    private val dx: Float,
    private val dy: Float,
    private val input: SkImageFilter?,
) : SkImageFilter() {
    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        val upstream = input?.filterImage(src, ctm) ?: FilterResult(src, 0, 0)
        val scale = ctm.computeMaxScale().coerceAtLeast(1f)
        val sx = (dx * scale + 0.5f).toInt()
        val sy = (dy * scale + 0.5f).toInt()
        return FilterResult(
            image = upstream.image,
            offsetX = upstream.offsetX + sx,
            offsetY = upstream.offsetY + sy,
        )
    }
}

/**
 * `ColorFilter` — applies [cf] to each pixel of the (possibly chained)
 * input image. Allocates a new [SkImage] sized to the input ; the
 * filter math runs in non-premul `SkColor` space (Phase 7d.1 ;
 * Phase 7e' linear-sRGB wrapper not extended here yet).
 */
internal class SkColorFilterImageFilter(
    private val cf: SkColorFilter,
    private val input: SkImageFilter?,
) : SkImageFilter() {
    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        val upstream = input?.filterImage(src, ctm) ?: FilterResult(src, 0, 0)
        val srcImg = upstream.image
        val w = srcImg.width
        val h = srcImg.height
        val outPixels = IntArray(w * h)
        for (y in 0 until h) {
            val rowOff = y * w
            for (x in 0 until w) {
                val px = srcImg.peekPixel(x, y)
                outPixels[rowOff + x] = cf.filterColor(px)
            }
        }
        return FilterResult(
            image = SkImage(w, h, outPixels),
            offsetX = upstream.offsetX,
            offsetY = upstream.offsetY,
        )
    }
}

/**
 * `Compose` — chained `outer(inner(src))`. The combined offset is
 * `inner.offset + outer.offset` (with [outer] applied to [inner]'s
 * output image, so its own offset stacks on top).
 */
internal class SkComposeImageFilter(
    private val outer: SkImageFilter,
    private val inner: SkImageFilter,
) : SkImageFilter() {
    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        val midResult = inner.filterImage(src, ctm)
        val outResult = outer.filterImage(midResult.image, ctm)
        return FilterResult(
            image = outResult.image,
            offsetX = midResult.offsetX + outResult.offsetX,
            offsetY = midResult.offsetY + outResult.offsetY,
        )
    }
}
