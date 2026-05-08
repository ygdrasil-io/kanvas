package org.skia.foundation

import org.skia.core.SkBitmapDevice
import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.math.SkScalar

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

    /**
     * Phase 7d.2 — Gaussian blur. Output grows by `±ceil(3·σ)` per
     * axis. `sigma <= 0` returns input unchanged.
     */
    public fun Blur(
        sigmaX: Float,
        sigmaY: Float = sigmaX,
        input: SkImageFilter? = null,
    ): SkImageFilter? {
        if (!sigmaX.isFinite() || !sigmaY.isFinite()) return input
        if (sigmaX <= 0f && sigmaY <= 0f) return input
        return SkBlurImageFilter(sigmaX.coerceAtLeast(0f), sigmaY.coerceAtLeast(0f), input)
    }

    /**
     * Phase 7d.2 — apply 2-D affine matrix with sampling. Output is
     * matrix-mapped bbox of the input. Non-invertible returns input.
     */
    public fun MatrixTransform(
        matrix: SkMatrix,
        sampling: SkSamplingOptions = SkSamplingOptions.Default,
        input: SkImageFilter? = null,
    ): SkImageFilter? {
        if (matrix.invert() == null) return input
        return SkMatrixTransformImageFilter(matrix, sampling, input)
    }

    /**
     * Phase 7d.2 — drop-shadow recipe. Composites a blurred + tinted
     * shadow behind the input via SrcOver.
     */
    public fun DropShadow(
        dx: Float, dy: Float,
        sigmaX: Float, sigmaY: Float,
        color: SkColor,
        input: SkImageFilter? = null,
    ): SkImageFilter = SkDropShadowImageFilter(dx, dy, sigmaX, sigmaY, color, input)

    // ─── C1.1 — Source / passthrough wrappers ────────────────────────

    /**
     * Mirrors Skia's `SkImageFilters::Image(image, srcRect, dstRect,
     * sampling)` — wraps a static [SkImage] as the filter input. The
     * `src` parameter passed to [SkImageFilter.filterImage] is
     * ignored ; the output is `image` cropped to [srcRect] and
     * placed at [dstRect].
     *
     * Mainly useful as the *source* of a filter chain — pair with
     * [Compose] / [Blend] / [Merge] / arithmetic filters that need
     * an explicit input texture rather than the rasterised draw.
     */
    public fun Image(
        image: SkImage,
        srcRect: SkRect,
        dstRect: SkRect,
        sampling: SkSamplingOptions = SkSamplingOptions.Default,
    ): SkImageFilter = SkImageImageFilter(image, srcRect, dstRect, sampling)

    /**
     * Convenience overload — wraps the full image into a filter at
     * its native bounds.
     */
    public fun Image(
        image: SkImage,
        sampling: SkSamplingOptions = SkSamplingOptions.Default,
    ): SkImageFilter {
        val full = SkRect.MakeWH(image.width.toFloat(), image.height.toFloat())
        return Image(image, full, full, sampling)
    }

    /**
     * Mirrors Skia's `SkImageFilters::Picture(pic, targetRect)` —
     * replays an [SkPicture] into a bitmap of [targetRect]'s size,
     * then exposes that bitmap as the filter's input. The `src`
     * argument to [SkImageFilter.filterImage] is ignored.
     *
     * The picture's local-space origin is mapped to the bitmap's
     * top-left via a `(-targetRect.left, -targetRect.top)` translate
     * so a picture recorded at `(50, 50)` and a target rect of
     * `(40, 40, 100, 100)` lands the recorded ops at the right
     * spot in the output bitmap.
     */
    public fun Picture(pic: SkPicture, targetRect: SkRect): SkImageFilter =
        SkPictureImageFilter(pic, targetRect)

    /**
     * Convenience overload — uses the picture's recorded
     * [SkPicture.cullRect] as the target rect.
     */
    public fun Picture(pic: SkPicture): SkImageFilter = Picture(pic, pic.cullRect)

    /**
     * Mirrors Skia's `SkImageFilters::Shader(shader, dither)` — fills
     * a buffer with [shader] sampled at every pixel, returns that
     * buffer as the filter input. The buffer's size matches the
     * `src` image passed to [SkImageFilter.filterImage] (the chain's
     * "evaluation context"), so this filter is always paired with
     * something that defines size — typically as the source of a
     * [Crop] / [Blend] / arithmetic chain on top of a real
     * `saveLayer` rasterisation.
     *
     * [dither] is plumbed for source-compat with upstream call sites
     * but currently advisory : the F16 raster path is already
     * 16-bit per channel and doesn't need dithering, and the 8888
     * path applies the project's standard dither.
     */
    public fun Shader(
        shader: SkShader,
        @Suppress("UNUSED_PARAMETER") dither: Boolean = false,
    ): SkImageFilter = SkShaderImageFilter(shader)

    /**
     * Mirrors Skia's `SkImageFilters::Empty()` — a transparent-black
     * filter input. Useful as a placeholder in `Merge` / `Compose`
     * chains under construction, and to test that downstream filters
     * handle a fully-transparent input correctly.
     */
    public fun Empty(): SkImageFilter = SkEmptyImageFilter

    /**
     * Mirrors Skia's `SkImageFilters::Crop(rect, tileMode, input)` —
     * constrains [input]'s output to [rect], with [tileMode]
     * dictating how out-of-rect samples are treated.
     *
     *  - [SkTileMode.kClamp] : every pixel inside `rect` keeps the
     *    input's value at the closest clamped coord ; outside `rect`,
     *    the result is transparent black.
     *  - [SkTileMode.kRepeat] : tile the input's contribution across
     *    the rect's interior.
     *  - [SkTileMode.kMirror] : tile with mirroring at every period
     *    boundary.
     *  - [SkTileMode.kDecal] (default) : pass through inside `rect`,
     *    transparent outside — the strict "crop" semantics.
     *
     * `input == null` is the identity input (the rasterised source
     * image) — equivalent to upstream's "crop the implicit source".
     */
    public fun Crop(
        rect: SkRect,
        tileMode: SkTileMode = SkTileMode.kDecal,
        input: SkImageFilter? = null,
    ): SkImageFilter = SkCropImageFilter(rect, tileMode, input)

    /** Convenience overload — `Crop(rect, kDecal, input)`. */
    public fun Crop(rect: SkRect, input: SkImageFilter? = null): SkImageFilter =
        Crop(rect, SkTileMode.kDecal, input)
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

// -- Phase 7d.2 ----------------------------------------------------------

internal class SkBlurImageFilter(
    private val sigmaX: Float,
    private val sigmaY: Float,
    private val input: SkImageFilter?,
) : SkImageFilter() {
    private val radiusX: Int = kotlin.math.ceil(3.0 * sigmaX).toInt().coerceAtLeast(0)
    private val radiusY: Int = kotlin.math.ceil(3.0 * sigmaY).toInt().coerceAtLeast(0)
    private val kernelX: FloatArray = gaussianKernel1D(sigmaX, radiusX)
    private val kernelY: FloatArray = gaussianKernel1D(sigmaY, radiusY)

    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        val upstream = input?.filterImage(src, ctm) ?: FilterResult(src, 0, 0)
        val srcImg = upstream.image
        val srcW = srcImg.width; val srcH = srcImg.height
        if (radiusX == 0 && radiusY == 0) return upstream
        val outW = srcW + 2 * radiusX
        val outH = srcH + 2 * radiusY
        val tmp = IntArray(outW * srcH)
        for (y in 0 until srcH) for (xOut in 0 until outW) {
            tmp[y * outW + xOut] = sampleH(srcImg, xOut - radiusX, y, kernelX, radiusX)
        }
        val outBuf = IntArray(outW * outH)
        for (yOut in 0 until outH) for (xOut in 0 until outW) {
            outBuf[yOut * outW + xOut] = sampleV(tmp, outW, srcH, xOut, yOut - radiusY, kernelY, radiusY)
        }
        return FilterResult(SkImage(outW, outH, outBuf),
            upstream.offsetX - radiusX, upstream.offsetY - radiusY)
    }

    private fun sampleH(src: SkImage, cx: Int, cy: Int, kernel: FloatArray, radius: Int): Int {
        var aF = 0f; var rF = 0f; var gF = 0f; var bF = 0f
        for (k in -radius..radius) {
            val sx = cx + k
            val px = if (sx in 0 until src.width && cy in 0 until src.height) src.peekPixel(sx, cy) else 0
            val w = kernel[k + radius]
            val a = ((px ushr 24) and 0xFF) / 255f
            aF += a * w; rF += ((px ushr 16) and 0xFF) / 255f * a * w
            gF += ((px ushr 8) and 0xFF) / 255f * a * w; bF += (px and 0xFF) / 255f * a * w
        }
        return packPremulFloat(aF, rF, gF, bF)
    }

    private fun sampleV(tmp: IntArray, w: Int, h: Int, cx: Int, cy: Int, kernel: FloatArray, radius: Int): Int {
        var aF = 0f; var rF = 0f; var gF = 0f; var bF = 0f
        for (k in -radius..radius) {
            val sy = cy + k
            val px = if (sy in 0 until h) tmp[sy * w + cx] else 0
            val ws = kernel[k + radius]
            val a = ((px ushr 24) and 0xFF) / 255f
            aF += a * ws; rF += ((px ushr 16) and 0xFF) / 255f * a * ws
            gF += ((px ushr 8) and 0xFF) / 255f * a * ws; bF += (px and 0xFF) / 255f * a * ws
        }
        return packPremulFloat(aF, rF, gF, bF)
    }

    private fun packPremulFloat(aF: Float, rF: Float, gF: Float, bF: Float): Int {
        val outA = (aF * 255f + 0.5f).toInt().coerceIn(0, 255)
        if (outA == 0) return 0
        val invA = 1f / aF
        val outR = (rF * invA * 255f + 0.5f).toInt().coerceIn(0, 255)
        val outG = (gF * invA * 255f + 0.5f).toInt().coerceIn(0, 255)
        val outB = (bF * invA * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
    }

    private companion object {
        fun gaussianKernel1D(sigma: Float, radius: Int): FloatArray {
            if (radius == 0) return floatArrayOf(1f)
            val size = 2 * radius + 1
            val k = FloatArray(size)
            val twoSigmaSq = 2f * sigma * sigma
            var sum = 0f
            for (i in 0 until size) {
                val x = (i - radius).toFloat()
                val v = kotlin.math.exp(-(x * x) / twoSigmaSq.toDouble()).toFloat()
                k[i] = v; sum += v
            }
            for (i in 0 until size) k[i] /= sum
            return k
        }
    }
}

internal class SkMatrixTransformImageFilter(
    private val matrix: SkMatrix,
    private val sampling: SkSamplingOptions,
    private val input: SkImageFilter?,
) : SkImageFilter() {
    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        val upstream = input?.filterImage(src, ctm) ?: FilterResult(src, 0, 0)
        val srcImg = upstream.image
        val mappedRect = matrix.mapRect(
            org.skia.math.SkRect.MakeWH(srcImg.width.toFloat(), srcImg.height.toFloat())
        )
        val outLeft = kotlin.math.floor(mappedRect.left.toDouble()).toInt()
        val outTop = kotlin.math.floor(mappedRect.top.toDouble()).toInt()
        val outRight = kotlin.math.ceil(mappedRect.right.toDouble()).toInt()
        val outBottom = kotlin.math.ceil(mappedRect.bottom.toDouble()).toInt()
        val outW = (outRight - outLeft).coerceAtLeast(1)
        val outH = (outBottom - outTop).coerceAtLeast(1)
        val invMatrix = matrix.invert() ?: return upstream
        val outBuf = IntArray(outW * outH)
        for (y in 0 until outH) for (x in 0 until outW) {
            val (sx, sy) = invMatrix.mapXY(outLeft + x + 0.5f, outTop + y + 0.5f)
            outBuf[y * outW + x] = sample(srcImg, sx, sy)
        }
        return FilterResult(SkImage(outW, outH, outBuf),
            upstream.offsetX + outLeft, upstream.offsetY + outTop)
    }

    private fun sample(img: SkImage, sx: Float, sy: Float): Int = when (sampling.filter) {
        SkFilterMode.kNearest -> {
            val ix = kotlin.math.floor(sx.toDouble()).toInt()
            val iy = kotlin.math.floor(sy.toDouble()).toInt()
            if (ix in 0 until img.width && iy in 0 until img.height) img.peekPixel(ix, iy) else 0
        }
        SkFilterMode.kLinear -> {
            val fx = sx - 0.5f; val fy = sy - 0.5f
            val ix0 = kotlin.math.floor(fx.toDouble()).toInt()
            val iy0 = kotlin.math.floor(fy.toDouble()).toInt()
            val tx = (fx - kotlin.math.floor(fx.toDouble()).toFloat()).coerceIn(0f, 1f)
            val ty = (fy - kotlin.math.floor(fy.toDouble()).toFloat()).coerceIn(0f, 1f)
            bilerp(peek(img, ix0, iy0), peek(img, ix0 + 1, iy0),
                peek(img, ix0, iy0 + 1), peek(img, ix0 + 1, iy0 + 1), tx, ty)
        }
    }

    private fun peek(img: SkImage, x: Int, y: Int): Int =
        if (x in 0 until img.width && y in 0 until img.height) img.peekPixel(x, y) else 0

    private fun bilerp(c00: Int, c10: Int, c01: Int, c11: Int, tx: Float, ty: Float): Int {
        val itx = 1f - tx; val ity = 1f - ty
        val w00 = itx * ity; val w10 = tx * ity; val w01 = itx * ty; val w11 = tx * ty
        fun ch(shift: Int): Int {
            val v = ((c00 ushr shift) and 0xFF) * w00 + ((c10 ushr shift) and 0xFF) * w10 +
                ((c01 ushr shift) and 0xFF) * w01 + ((c11 ushr shift) and 0xFF) * w11
            return (v + 0.5f).toInt().coerceIn(0, 255)
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

internal class SkDropShadowImageFilter(
    private val dx: Float, private val dy: Float,
    private val sigmaX: Float, private val sigmaY: Float,
    private val color: SkColor,
    private val input: SkImageFilter?,
) : SkImageFilter() {
    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        val upstream = input?.filterImage(src, ctm) ?: FilterResult(src, 0, 0)
        val srcImg = upstream.image
        val tinted = SkColorFilterImageFilter(SkColorFilters.Blend(color, SkBlendMode.kSrcIn), null)
            .filterImage(srcImg, ctm).image
        val blurred = SkBlurImageFilter(sigmaX, sigmaY, null).filterImage(tinted, ctm)
        val scale = ctm.computeMaxScale().coerceAtLeast(1f)
        val sdx = (dx * scale + 0.5f).toInt(); val sdy = (dy * scale + 0.5f).toInt()
        val shadowImg = blurred.image
        val sox = blurred.offsetX + sdx; val soy = blurred.offsetY + sdy
        val uL = minOf(0, sox); val uT = minOf(0, soy)
        val uR = maxOf(srcImg.width, sox + shadowImg.width)
        val uB = maxOf(srcImg.height, soy + shadowImg.height)
        val outW = uR - uL; val outH = uB - uT
        val outBuf = IntArray(outW * outH)
        for (y in 0 until shadowImg.height) {
            val dY = soy + y - uT; if (dY !in 0 until outH) continue
            for (x in 0 until shadowImg.width) {
                val dX = sox + x - uL; if (dX !in 0 until outW) continue
                outBuf[dY * outW + dX] = shadowImg.peekPixel(x, y)
            }
        }
        for (y in 0 until srcImg.height) {
            val dY = y - uT; if (dY !in 0 until outH) continue
            for (x in 0 until srcImg.width) {
                val dX = x - uL; if (dX !in 0 until outW) continue
                val sp = srcImg.peekPixel(x, y); val sa = (sp ushr 24) and 0xFF
                if (sa == 0xFF) outBuf[dY * outW + dX] = sp
                else if (sa > 0) outBuf[dY * outW + dX] = srcOver(sp, outBuf[dY * outW + dX])
            }
        }
        return FilterResult(SkImage(outW, outH, outBuf),
            upstream.offsetX + uL, upstream.offsetY + uT)
    }

    private fun srcOver(src: Int, dst: Int): Int {
        val sa = (src ushr 24) and 0xFF; if (sa == 0) return dst
        val da = (dst ushr 24) and 0xFF; val invSa = 255 - sa
        val outA = sa + (da * invSa + 127) / 255; if (outA == 0) return 0
        val sr = (src ushr 16) and 0xFF; val sg = (src ushr 8) and 0xFF; val sb = src and 0xFF
        val dr = (dst ushr 16) and 0xFF; val dg = (dst ushr 8) and 0xFF; val db = dst and 0xFF
        val outR = (sr * sa + dr * da * invSa / 255 + outA / 2) / outA
        val outG = (sg * sa + dg * da * invSa / 255 + outA / 2) / outA
        val outB = (sb * sa + db * da * invSa / 255 + outA / 2) / outA
        return (outA shl 24) or (outR.coerceIn(0, 255) shl 16) or
            (outG.coerceIn(0, 255) shl 8) or outB.coerceIn(0, 255)
    }
}

// -- C1.1 source / passthrough wrappers ------------------------------------

/**
 * `Image` — wraps a static [SkImage] as the filter input. The
 * `src` arg to [filterImage] is ignored — this filter generates
 * its own source from the image-pixel data.
 *
 * Output is sized to [dstRect] ; pixels are sampled from
 * [srcRect] of [image] via [sampling]. Output offset is
 * `(dstRect.left, dstRect.top)` so the filter result lands at
 * the correct device-space position.
 *
 * Fast path : when `srcRect == image bounds && dstRect == image
 * bounds && sampling == kNearest`, the image is returned as-is
 * (no sampling allocation).
 */
internal class SkImageImageFilter(
    private val image: SkImage,
    private val srcRect: SkRect,
    private val dstRect: SkRect,
    private val sampling: SkSamplingOptions,
) : SkImageFilter() {

    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        val outW = kotlin.math.max(1, kotlin.math.ceil(dstRect.width().toDouble()).toInt())
        val outH = kotlin.math.max(1, kotlin.math.ceil(dstRect.height().toDouble()).toInt())
        val outOffX = kotlin.math.floor(dstRect.left.toDouble()).toInt()
        val outOffY = kotlin.math.floor(dstRect.top.toDouble()).toInt()

        // Identity-shape fast path : the most common case (Image(image)
        // with no sub-rect remapping) skips the per-pixel sample loop.
        val fullSrc = SkRect.MakeWH(image.width.toFloat(), image.height.toFloat())
        if (srcRect == fullSrc && dstRect == fullSrc) {
            return FilterResult(image, outOffX, outOffY)
        }

        // General path : sample srcRect → dstRect via the requested
        // SkSamplingOptions filter mode. Identical math to
        // SkMatrixTransformImageFilter's bilerp / nearest, just
        // anchored on the dst-to-src remap implied by the two rects.
        val outBuf = IntArray(outW * outH)
        val sx = srcRect.width() / dstRect.width()
        val sy = srcRect.height() / dstRect.height()
        for (y in 0 until outH) {
            val srcY = srcRect.top + (y + 0.5f) * sy
            for (x in 0 until outW) {
                val srcX = srcRect.left + (x + 0.5f) * sx
                outBuf[y * outW + x] = sample(image, srcX, srcY)
            }
        }
        return FilterResult(SkImage(outW, outH, outBuf), outOffX, outOffY)
    }

    private fun sample(img: SkImage, sx: Float, sy: Float): Int = when (sampling.filter) {
        SkFilterMode.kNearest -> {
            val ix = kotlin.math.floor(sx.toDouble()).toInt()
            val iy = kotlin.math.floor(sy.toDouble()).toInt()
            if (ix in 0 until img.width && iy in 0 until img.height) img.peekPixel(ix, iy) else 0
        }
        SkFilterMode.kLinear -> {
            val fx = sx - 0.5f; val fy = sy - 0.5f
            val ix0 = kotlin.math.floor(fx.toDouble()).toInt()
            val iy0 = kotlin.math.floor(fy.toDouble()).toInt()
            val tx = (fx - kotlin.math.floor(fx.toDouble()).toFloat()).coerceIn(0f, 1f)
            val ty = (fy - kotlin.math.floor(fy.toDouble()).toFloat()).coerceIn(0f, 1f)
            bilerp(peek(img, ix0, iy0), peek(img, ix0 + 1, iy0),
                peek(img, ix0, iy0 + 1), peek(img, ix0 + 1, iy0 + 1), tx, ty)
        }
    }

    private fun peek(img: SkImage, x: Int, y: Int): Int =
        if (x in 0 until img.width && y in 0 until img.height) img.peekPixel(x, y) else 0

    private fun bilerp(c00: Int, c10: Int, c01: Int, c11: Int, tx: Float, ty: Float): Int {
        val itx = 1f - tx; val ity = 1f - ty
        val w00 = itx * ity; val w10 = tx * ity; val w01 = itx * ty; val w11 = tx * ty
        fun ch(shift: Int): Int {
            val v = ((c00 ushr shift) and 0xFF) * w00 + ((c10 ushr shift) and 0xFF) * w10 +
                ((c01 ushr shift) and 0xFF) * w01 + ((c11 ushr shift) and 0xFF) * w11
            return (v + 0.5f).toInt().coerceIn(0, 255)
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

/**
 * `Picture` — replays a recorded [SkPicture] into a bitmap of
 * [targetRect]'s size, returns that bitmap as the filter input.
 * Mirrors upstream's `SkImageFilters::Picture`. The picture is
 * rendered at draw time (each `filterImage` call replays it
 * fresh — Skia does the same and clients are expected to wrap
 * with [Compose] etc. only when they want the replay
 * memoised).
 */
internal class SkPictureImageFilter(
    private val pic: SkPicture,
    private val targetRect: SkRect,
) : SkImageFilter() {

    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        val outW = kotlin.math.max(1, kotlin.math.ceil(targetRect.width().toDouble()).toInt())
        val outH = kotlin.math.max(1, kotlin.math.ceil(targetRect.height().toDouble()).toInt())
        // Allocate a fresh raster bitmap and replay the picture into
        // it, translated so the targetRect's top-left lands at (0, 0)
        // in the bitmap. The bitmap's color space defaults to sRGB ;
        // pictures recorded against a non-sRGB working space will
        // need a callsite that re-tags appropriately (B2.4-style
        // future enhancement).
        val bitmap = SkBitmap(outW, outH)
        val canvas = SkCanvas(bitmap)
        canvas.translate(-targetRect.left, -targetRect.top)
        pic.playback(canvas)
        return FilterResult(
            image = bitmap.asImage(),
            offsetX = kotlin.math.floor(targetRect.left.toDouble()).toInt(),
            offsetY = kotlin.math.floor(targetRect.top.toDouble()).toInt(),
        )
    }
}

/**
 * `Shader` — fills a buffer sized to the input `src`'s dimensions
 * with the shader's per-pixel output. Effectively the equivalent
 * of `drawPaint(SkPaint().apply { shader = … })` into a fresh
 * bitmap. The shader is sampled at pixel centres in the input's
 * local coordinate space.
 *
 * Output offset is `(0, 0)` — the shader fills the whole input
 * bbox, so the result aligns with `src`.
 */
internal class SkShaderImageFilter(
    private val shader: SkShader,
) : SkImageFilter() {

    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        val w = src.width
        val h = src.height
        val bitmap = SkBitmap(w, h)
        // Drive the shader through SkBitmapDevice.drawPaint so it
        // sees the same pipeline (color-space xform, premul) as the
        // raster sinks. Identity CTM ; the shader's localMatrix
        // handles its own geometry.
        val device = SkBitmapDevice(bitmap)
        val paint = SkPaint().apply { this.shader = this@SkShaderImageFilter.shader }
        device.drawPaint(SkMatrix.Identity, org.skia.math.SkIRect.MakeWH(w, h), paint)
        return FilterResult(bitmap.asImage(), 0, 0)
    }
}

/**
 * `Empty` — placeholder filter returning a 1×1 transparent-black
 * image. Used as a default/initial value in `Merge` / `Compose`
 * chains under construction, and to test that downstream filters
 * handle fully-transparent inputs correctly.
 *
 * Singleton — there's no per-instance state.
 */
internal object SkEmptyImageFilter : SkImageFilter() {
    private val EMPTY_IMAGE = SkImage(1, 1, IntArray(1))
    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult =
        FilterResult(EMPTY_IMAGE, 0, 0)
}

/**
 * `Crop` — constrain [input]'s output to [rect], with [tileMode]
 * dictating how out-of-rect samples are treated.
 *
 * Implementation : run [input] (or use `src` if input is null),
 * then walk the rect-sized output buffer ; for each output pixel,
 * the corresponding input pixel is either inside upstream's image
 * bounds (direct copy), or out-of-bounds (apply [tileMode] to
 * find the source pixel — clamp / repeat / mirror — or return
 * transparent for kDecal).
 *
 * Output bounds : [rect]. Output offset matches [rect]'s top-left.
 */
internal class SkCropImageFilter(
    private val rect: SkRect,
    private val tileMode: SkTileMode,
    private val input: SkImageFilter?,
) : SkImageFilter() {

    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult {
        val upstream = input?.filterImage(src, ctm) ?: FilterResult(src, 0, 0)
        val srcImg = upstream.image
        val srcOffX = upstream.offsetX
        val srcOffY = upstream.offsetY

        val outW = kotlin.math.max(1, kotlin.math.ceil(rect.width().toDouble()).toInt())
        val outH = kotlin.math.max(1, kotlin.math.ceil(rect.height().toDouble()).toInt())
        val outOffX = kotlin.math.floor(rect.left.toDouble()).toInt()
        val outOffY = kotlin.math.floor(rect.top.toDouble()).toInt()
        val outBuf = IntArray(outW * outH)

        for (y in 0 until outH) {
            // Map output (x, y) to upstream image coords.
            // Output pixel (x, y) sits at device pos (outOffX + x, outOffY + y).
            // The upstream image's pixel (sx, sy) sits at device pos (srcOffX + sx, srcOffY + sy).
            // So sx = outOffX + x - srcOffX ; sy similarly.
            val sy = outOffY + y - srcOffY
            for (x in 0 until outW) {
                val sx = outOffX + x - srcOffX
                outBuf[y * outW + x] = sampleWithTileMode(srcImg, sx, sy)
            }
        }
        return FilterResult(SkImage(outW, outH, outBuf), outOffX, outOffY)
    }

    private fun sampleWithTileMode(img: SkImage, sx: Int, sy: Int): Int {
        val w = img.width
        val h = img.height
        if (w == 0 || h == 0) return 0
        if (sx in 0 until w && sy in 0 until h) return img.peekPixel(sx, sy)
        return when (tileMode) {
            SkTileMode.kDecal -> 0
            SkTileMode.kClamp -> img.peekPixel(sx.coerceIn(0, w - 1), sy.coerceIn(0, h - 1))
            SkTileMode.kRepeat -> img.peekPixel(positiveMod(sx, w), positiveMod(sy, h))
            SkTileMode.kMirror -> img.peekPixel(mirrorMod(sx, w), mirrorMod(sy, h))
        }
    }

    /** Mathematical modulo : `((n % m) + m) % m` so result is in `[0, m)`. */
    private fun positiveMod(n: Int, m: Int): Int {
        val r = n % m
        return if (r < 0) r + m else r
    }

    /**
     * Mirror-mod : period = `2*m`, where `[0, m)` is direct and
     * `[m, 2m)` mirrors back. Matches upstream Skia's `kMirror` tile
     * mode for raster shaders.
     */
    private fun mirrorMod(n: Int, m: Int): Int {
        if (m == 0) return 0
        val twoM = 2 * m
        var r = n % twoM
        if (r < 0) r += twoM
        return if (r < m) r else twoM - 1 - r
    }
}
