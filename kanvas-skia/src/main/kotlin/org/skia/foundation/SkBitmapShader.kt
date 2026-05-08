package org.skia.foundation

import org.skia.core.SkColorSpaceXformSteps
import org.skia.math.SkMatrix
import kotlin.math.floor

/**
 * Image shader (`SkBitmap.makeShader` / `SkImage.makeShader`) — Phase 5g.
 *
 * Samples the [image] using:
 *  - per-axis [tileX]/[tileY] tile mode (clamp / repeat / mirror / decal),
 *  - point or bilinear filtering ([sampling].filter — kNearest / kLinear);
 *    mipmap and cubic are out of scope.
 *
 * `setupForDraw` pre-converts every source pixel into the bitmap's working
 * colour space once per draw — far cheaper than xforming at every sample
 * (the rasterizer hits the shader once per *covered* pixel, but the source
 * image typically has far fewer pixels than the dst rect).
 *
 * `shadeRowF16` is the F16 fast path used by Phase 6b/6c rasterizer
 * branches. Returns 4 premultiplied floats per pixel without any byte
 * quantization.
 */
public class SkBitmapShader internal constructor(
    private val image: SkImage,
    private val tileX: SkTileMode,
    private val tileY: SkTileMode,
    private val sampling: SkSamplingOptions,
    localMatrix: SkMatrix = SkMatrix.Identity,
) : SkShader(localMatrix) {

    /**
     * Pre-transformed source pixels, working colour space, **non-premul**
     * 8-bit ARGB. Built once in [setupForDraw]. Same length and same row
     * stride as [image.pixels].
     */
    private val xformedPixels: IntArray = IntArray(image.width * image.height)

    /**
     * Phase 6b: pre-transformed source pixels, working colour space,
     * **premultiplied** 4-float quartets, row-major. Built once per draw
     * alongside [xformedPixels]; the F16 raster path samples from this
     * buffer and skips the byte conversion the 8-bit path needs.
     */
    private val xformedPixelsF16: FloatArray = FloatArray(image.width * image.height * 4)

    override fun setupForDraw(canvasCtm: SkMatrix, xform: SkColorSpaceXformSteps) {
        super.setupForDraw(canvasCtm, xform)
        val src = image.pixels
        val rgba = FloatArray(4)
        for (i in src.indices) {
            val c = src[i]
            // sRGB → working colour space (8-bit out, non-premul).
            if (xform.flags.isIdentity) {
                xformedPixels[i] = c
            } else {
                rgba[0] = SkColorGetR(c) / 255f
                rgba[1] = SkColorGetG(c) / 255f
                rgba[2] = SkColorGetB(c) / 255f
                rgba[3] = SkColorGetA(c) / 255f
                xform.apply(rgba)
                val outA = (rgba[3] * 255f + 0.5f).toInt().coerceIn(0, 255)
                val outR = (rgba[0] * 255f + 0.5f).toInt().coerceIn(0, 255)
                val outG = (rgba[1] * 255f + 0.5f).toInt().coerceIn(0, 255)
                val outB = (rgba[2] * 255f + 0.5f).toInt().coerceIn(0, 255)
                xformedPixels[i] = SkColorSetARGB(outA, outR, outG, outB)
            }
            // Float path: keep full precision through the xform, then premul.
            val a = SkColorGetA(c) / 255f
            val r = SkColorGetR(c) / 255f
            val g = SkColorGetG(c) / 255f
            val b = SkColorGetB(c) / 255f
            if (xform.flags.isIdentity) {
                val o = i * 4
                xformedPixelsF16[o]     = r * a
                xformedPixelsF16[o + 1] = g * a
                xformedPixelsF16[o + 2] = b * a
                xformedPixelsF16[o + 3] = a
            } else {
                rgba[0] = r; rgba[1] = g; rgba[2] = b; rgba[3] = a
                xform.apply(rgba)
                val outA = rgba[3]
                val o = i * 4
                xformedPixelsF16[o]     = rgba[0] * outA
                xformedPixelsF16[o + 1] = rgba[1] * outA
                xformedPixelsF16[o + 2] = rgba[2] * outA
                xformedPixelsF16[o + 3] = outA
            }
        }
    }

    override fun shadeRow(devX: Int, devY: Int, count: Int, dst: IntArray) {
        val inv = deviceToLocal
        if (inv == null) {
            for (i in 0 until count) dst[i] = 0
            return
        }
        val w = image.width; val h = image.height
        val x0 = devX + 0.5f
        val y0 = devY + 0.5f
        var lx = inv.sx * x0 + inv.kx * y0 + inv.tx
        var ly = inv.ky * x0 + inv.sy * y0 + inv.ty
        val stepX = inv.sx
        val stepY = inv.ky

        when (sampling.filter) {
            SkFilterMode.kNearest -> {
                for (i in 0 until count) {
                    val (sxi, syi, decalled) = sampleCoordsNearest(lx, ly, w, h)
                    dst[i] = if (decalled) 0 else xformedPixels[syi * w + sxi]
                    lx += stepX
                    ly += stepY
                }
            }
            SkFilterMode.kLinear -> {
                for (i in 0 until count) {
                    dst[i] = sampleLinear8(lx, ly, w, h)
                    lx += stepX
                    ly += stepY
                }
            }
        }
    }

    override fun shadeRowF16(devX: Int, devY: Int, count: Int, dst: FloatArray) {
        require(dst.size >= count * 4) { "dst too small: ${dst.size} < ${count * 4}" }
        val inv = deviceToLocal
        if (inv == null) {
            for (i in 0 until count * 4) dst[i] = 0f
            return
        }
        val w = image.width; val h = image.height
        val x0 = devX + 0.5f
        val y0 = devY + 0.5f
        var lx = inv.sx * x0 + inv.kx * y0 + inv.tx
        var ly = inv.ky * x0 + inv.sy * y0 + inv.ty
        val stepX = inv.sx
        val stepY = inv.ky

        var di = 0
        when (sampling.filter) {
            SkFilterMode.kNearest -> {
                for (i in 0 until count) {
                    val (sxi, syi, decalled) = sampleCoordsNearest(lx, ly, w, h)
                    if (decalled) {
                        dst[di] = 0f; dst[di + 1] = 0f; dst[di + 2] = 0f; dst[di + 3] = 0f
                    } else {
                        val o = (syi * w + sxi) * 4
                        dst[di]     = xformedPixelsF16[o]
                        dst[di + 1] = xformedPixelsF16[o + 1]
                        dst[di + 2] = xformedPixelsF16[o + 2]
                        dst[di + 3] = xformedPixelsF16[o + 3]
                    }
                    di += 4
                    lx += stepX
                    ly += stepY
                }
            }
            SkFilterMode.kLinear -> {
                for (i in 0 until count) {
                    sampleLinearF16(lx, ly, w, h, dst, di)
                    di += 4
                    lx += stepX
                    ly += stepY
                }
            }
        }
    }

    /**
     * Phase I5.3.c — sample the shader at an arbitrary `(lx, ly)`
     * point in shader-local space (image pixel coords when
     * [localMatrix] is identity). Used by
     * [org.skia.core.SkCanvas.drawVertices] to look up texture
     * pixels at a triangle's interpolated UV.
     *
     * Honours the shader's per-axis tile mode and filter mode just
     * like [shadeRow], but bypasses the device-space coordinate
     * traversal — the caller already has the local coords in hand.
     */
    override fun sampleAtLocal(lx: Float, ly: Float): SkColor {
        val w = image.width
        val h = image.height
        return when (sampling.filter) {
            SkFilterMode.kNearest -> {
                val (sxi, syi, decalled) = sampleCoordsNearest(lx, ly, w, h)
                if (decalled) 0 else xformedPixels[syi * w + sxi]
            }
            SkFilterMode.kLinear -> sampleLinear8(lx, ly, w, h)
        }
    }

    /**
     * Apply the per-axis tile mode and pick the integer texel for nearest
     * sampling. Returns `(sxi, syi, decalled)` — `decalled = true` when the
     * coordinate falls outside `[0, w)` / `[0, h)` under [SkTileMode.kDecal]
     * and the caller should emit a transparent pixel.
     */
    private fun sampleCoordsNearest(lx: Float, ly: Float, w: Int, h: Int): Triple<Int, Int, Boolean> {
        // Skia samples at pixel centres in source space too: nearest-neighbour
        // floors the source-space coordinate (which is already at a pixel
        // centre because we sample at devCenter through deviceToLocal).
        val sxF = lx
        val syF = ly
        val (sxi, decalX) = applyTileNearest(sxF, w, tileX)
        val (syi, decalY) = applyTileNearest(syF, h, tileY)
        return Triple(sxi, syi, decalX || decalY)
    }

    private fun applyTileNearest(coord: Float, size: Int, mode: SkTileMode): Pair<Int, Boolean> {
        val ic = floor(coord).toInt()
        return when (mode) {
            SkTileMode.kClamp -> ic.coerceIn(0, size - 1) to false
            SkTileMode.kRepeat -> mod(ic, size) to false
            SkTileMode.kMirror -> {
                val period = 2 * size
                val m = mod(ic, period)
                val mapped = if (m < size) m else (period - 1 - m)
                mapped to false
            }
            SkTileMode.kDecal -> {
                if (ic < 0 || ic >= size) (0 to true) else (ic to false)
            }
        }
    }

    /**
     * Bilinear sample of [xformedPixels] at fractional coords `(lx, ly)`.
     * Uses the standard "pixel centre at half-integer" convention: the
     * sample point `(lx, ly)` is treated as the device-space pixel centre,
     * and the four nearest source texels (centres at `(ix + 0.5, iy + 0.5)`)
     * are weighted by `(fx, fy)` ∈ `[0, 1]²`.
     */
    private fun sampleLinear8(lx: Float, ly: Float, w: Int, h: Int): SkColor {
        val xf = lx - 0.5f
        val yf = ly - 0.5f
        val x0 = floor(xf).toInt()
        val y0 = floor(yf).toInt()
        val fx = xf - x0
        val fy = yf - y0
        val (ix0, dx0) = applyTileNearest(x0.toFloat() + 0.5f, w, tileX)
        val (ix1, dx1) = applyTileNearest((x0 + 1).toFloat() + 0.5f, w, tileX)
        val (iy0, dy0) = applyTileNearest(y0.toFloat() + 0.5f, h, tileY)
        val (iy1, dy1) = applyTileNearest((y0 + 1).toFloat() + 0.5f, h, tileY)
        val ifx = 1f - fx; val ify = 1f - fy
        // Linear filter in unpremul ARGB is acceptable for opaque images
        // (TinyBitmapGM's only sample is fully premultiplied); for mixed
        // alpha we'd want premul-space lerp. Out of scope for the slice.
        val c00 = if (dx0 || dy0) 0 else xformedPixels[iy0 * w + ix0]
        val c10 = if (dx1 || dy0) 0 else xformedPixels[iy0 * w + ix1]
        val c01 = if (dx0 || dy1) 0 else xformedPixels[iy1 * w + ix0]
        val c11 = if (dx1 || dy1) 0 else xformedPixels[iy1 * w + ix1]
        val w00 = ifx * ify; val w10 = fx * ify
        val w01 = ifx * fy;  val w11 = fx * fy
        val a = (SkColorGetA(c00) * w00 + SkColorGetA(c10) * w10 +
                 SkColorGetA(c01) * w01 + SkColorGetA(c11) * w11 + 0.5f).toInt().coerceIn(0, 255)
        val r = (SkColorGetR(c00) * w00 + SkColorGetR(c10) * w10 +
                 SkColorGetR(c01) * w01 + SkColorGetR(c11) * w11 + 0.5f).toInt().coerceIn(0, 255)
        val g = (SkColorGetG(c00) * w00 + SkColorGetG(c10) * w10 +
                 SkColorGetG(c01) * w01 + SkColorGetG(c11) * w11 + 0.5f).toInt().coerceIn(0, 255)
        val b = (SkColorGetB(c00) * w00 + SkColorGetB(c10) * w10 +
                 SkColorGetB(c01) * w01 + SkColorGetB(c11) * w11 + 0.5f).toInt().coerceIn(0, 255)
        return SkColorSetARGB(a, r, g, b)
    }

    /** Bilinear sample, premul-float output (no byte quantization). */
    private fun sampleLinearF16(lx: Float, ly: Float, w: Int, h: Int, out: FloatArray, off: Int) {
        val xf = lx - 0.5f
        val yf = ly - 0.5f
        val x0 = floor(xf).toInt()
        val y0 = floor(yf).toInt()
        val fx = xf - x0
        val fy = yf - y0
        val (ix0, dx0) = applyTileNearest(x0.toFloat() + 0.5f, w, tileX)
        val (ix1, dx1) = applyTileNearest((x0 + 1).toFloat() + 0.5f, w, tileX)
        val (iy0, dy0) = applyTileNearest(y0.toFloat() + 0.5f, h, tileY)
        val (iy1, dy1) = applyTileNearest((y0 + 1).toFloat() + 0.5f, h, tileY)
        val ifx = 1f - fx; val ify = 1f - fy
        val w00 = ifx * ify; val w10 = fx * ify
        val w01 = ifx * fy;  val w11 = fx * fy

        val src = xformedPixelsF16
        for (chan in 0..3) {
            val v00 = if (dx0 || dy0) 0f else src[(iy0 * w + ix0) * 4 + chan]
            val v10 = if (dx1 || dy0) 0f else src[(iy0 * w + ix1) * 4 + chan]
            val v01 = if (dx0 || dy1) 0f else src[(iy1 * w + ix0) * 4 + chan]
            val v11 = if (dx1 || dy1) 0f else src[(iy1 * w + ix1) * 4 + chan]
            out[off + chan] = w00 * v00 + w10 * v10 + w01 * v01 + w11 * v11
        }
    }

    /** Mathematical modulo: result is always in `[0, m)` for `m > 0`. */
    private fun mod(n: Int, m: Int): Int {
        val r = n % m
        return if (r < 0) r + m else r
    }
}
