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
