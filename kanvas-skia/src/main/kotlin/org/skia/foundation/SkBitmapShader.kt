package org.skia.foundation


import org.graphiks.math.SkColor
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.skia.core.SkColorSpaceXformSteps
import org.graphiks.math.SkMatrix
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

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
public class SkBitmapShader public constructor(
    private val image: SkImage,
    private val tileX: SkTileMode,
    private val tileY: SkTileMode,
    private val sampling: SkSamplingOptions,
    localMatrix: SkMatrix = SkMatrix.Identity,
) : SkShader(localMatrix) {

    override val shaderKind: ShaderKind
        get() = ShaderKind.Bitmap(
            image = image,
            tileX = tileX,
            tileY = tileY,
            localMatrix = localMatrix,
        )

    // ─── Public accessors (B2.4 — SVG projection via <pattern>) ──────

    /** Source image — read-only, never aliased. */
    public fun getImage(): SkImage = image

    /** Tile mode along the local-x axis. */
    public fun getTileX(): SkTileMode = tileX

    /** Tile mode along the local-y axis. */
    public fun getTileY(): SkTileMode = tileY

    /** Sampling options (filter / mipmap / cubic). G5.2 — needed by the GPU
     *  routing in [org.skia.gpu.webgpu.SkWebGpuDevice] to pick the right
     *  bitmap pipeline (filter mode) for `paint.shader is SkBitmapShader`. */
    public fun getSampling(): SkSamplingOptions = sampling

    /**
     * Pre-transformed source pixels, working colour space, **non-premul**
     * 8-bit ARGB. Built once in [setupForDraw]. Same length and same row
     * stride as [image.pixels].
     *
     * Always points to `xformedLevels8[0]` — kept as a separate field so
     * the pre-mip code paths (which never touched the pyramid) compile
     * unchanged.
     */
    private val xformedPixels: IntArray = IntArray(image.width * image.height)

    /**
     * Phase 6b: pre-transformed source pixels, working colour space,
     * **premultiplied** 4-float quartets, row-major. Built once per draw
     * alongside [xformedPixels]; the F16 raster path samples from this
     * buffer and skips the byte conversion the 8-bit path needs.
     */
    private val xformedPixelsF16: FloatArray = FloatArray(image.width * image.height * 4)

    /**
     * Phase G10 — xform'd byte pixels per mip level. `xformedLevels8[0]`
     * always equals [xformedPixels]. Levels 1+ exist only when the
     * underlying [image] carries a mip pyramid built via
     * [SkImage.withDefaultMipmaps].
     */
    private lateinit var xformedLevels8: Array<IntArray>

    /**
     * Phase G10 — xform'd float-premul pixels per mip level. Sibling
     * of [xformedLevels8] for the F16 sampler path.
     * `xformedLevelsF16[0]` always equals [xformedPixelsF16].
     */
    private lateinit var xformedLevelsF16: Array<FloatArray>

    private fun applyXformByte(src: IntArray, dst: IntArray, xform: SkColorSpaceXformSteps) {
        val rgba = FloatArray(4)
        for (i in src.indices) {
            val c = src[i]
            if (xform.flags.isIdentity) {
                dst[i] = c
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
                dst[i] = SkColorSetARGB(outA, outR, outG, outB)
            }
        }
    }

    private fun applyXformFloat(src: IntArray, dst: FloatArray, xform: SkColorSpaceXformSteps) {
        val rgba = FloatArray(4)
        for (i in src.indices) {
            val c = src[i]
            val a = SkColorGetA(c) / 255f
            val r = SkColorGetR(c) / 255f
            val g = SkColorGetG(c) / 255f
            val b = SkColorGetB(c) / 255f
            if (xform.flags.isIdentity) {
                val o = i * 4
                dst[o]     = r * a
                dst[o + 1] = g * a
                dst[o + 2] = b * a
                dst[o + 3] = a
            } else {
                rgba[0] = r; rgba[1] = g; rgba[2] = b; rgba[3] = a
                xform.apply(rgba)
                val outA = rgba[3]
                val o = i * 4
                dst[o]     = rgba[0] * outA
                dst[o + 1] = rgba[1] * outA
                dst[o + 2] = rgba[2] * outA
                dst[o + 3] = outA
            }
        }
    }

    override fun setupForDraw(canvasCtm: SkMatrix, xform: SkColorSpaceXformSteps) {
        super.setupForDraw(canvasCtm, xform)
        applyXformByte(image.pixels, xformedPixels, xform)
        applyXformFloat(image.pixels, xformedPixelsF16, xform)

        // Build xform'd mip pyramid (level 0 reuses the buffers above).
        val mip = image.mipLevels
        if (mip == null || mip.size <= 1) {
            xformedLevels8 = arrayOf(xformedPixels)
            xformedLevelsF16 = arrayOf(xformedPixelsF16)
        } else {
            xformedLevels8 = Array(mip.size) { i ->
                if (i == 0) xformedPixels else {
                    val lvl = mip[i]
                    val out = IntArray(lvl.width * lvl.height)
                    applyXformByte(lvl.pixels, out, xform)
                    out
                }
            }
            xformedLevelsF16 = Array(mip.size) { i ->
                if (i == 0) xformedPixelsF16 else {
                    val lvl = mip[i]
                    val out = FloatArray(lvl.width * lvl.height * 4)
                    applyXformFloat(lvl.pixels, out, xform)
                    out
                }
            }
        }
    }

    // ────────────────── G10 — mip LOD selection helpers ──────────────────

    /**
     * Phase G10 — pick a mip level from the device→local Jacobian.
     *
     * `s = max(|∂u/∂x|, |∂v/∂y|)` is the per-axis source-step magnitude
     * — for an axis-aligned scale, exactly the source/destination ratio.
     * The mip level is `floor(log2(s))` clamped to `[0, numLevels-1]`.
     *
     * Returns `0` when the source has no mip pyramid (single-level
     * image) or the sampler isn't requesting mips.
     */
    private fun pickMipLevel(): Int {
        if (sampling.mipmap == SkMipmapMode.kNone) return 0
        val numLevels = xformedLevels8.size
        if (numLevels <= 1) return 0
        val inv = deviceToLocal ?: return 0
        // `inv` is the device→local matrix : column lengths give source-
        // step-per-device-pixel. For an axis-aligned source/dst, that is
        // exactly `srcW / dstW` (horizontal) and `srcH / dstH` (vertical).
        val sx = sqrt(inv.sx * inv.sx + inv.ky * inv.ky)
        val sy = sqrt(inv.kx * inv.kx + inv.sy * inv.sy)
        val s = max(sx, sy)
        if (s <= 1f) return 0
        // `log2(s)` via `ln(s) / ln(2)`. Floor and clamp.
        val level = floor(ln(s) / LN2).toInt()
        return level.coerceIn(0, numLevels - 1)
    }

    /**
     * Phase G10 — aniso shortcut. Returns `Pair(majorDx, majorDy)` in
     * **local** space — the major-axis step in source pixels per
     * device-space pixel. Magnitude is the *minor* axis length so the
     * N-tap average covers the elliptical footprint along the longer
     * axis. When the footprint is isotropic we fall back to the major
     * being the larger of `(∂u/∂x, ∂v/∂y)`.
     */
    private fun anisoMajorStep(): Pair<Float, Float> {
        val inv = deviceToLocal ?: return 0f to 0f
        // Two column vectors of the inverse map : how a one-pixel step
        // in device-x and device-y maps to local space.
        val cx0 = inv.sx; val cx1 = inv.ky
        val cy0 = inv.kx; val cy1 = inv.sy
        val lx = sqrt(cx0 * cx0 + cx1 * cx1)
        val ly = sqrt(cy0 * cy0 + cy1 * cy1)
        return if (lx >= ly) (cx0 to cx1) else (cy0 to cy1)
    }

    override fun shadeRow(devX: Int, devY: Int, count: Int, dst: IntArray) {
        val inv = deviceToLocal
        if (inv == null) {
            for (i in 0 until count) dst[i] = 0
            return
        }
        // R-final.7 — perspective deviceToLocal : the affine accumulator path
        // below ignores the persp row entirely. Route per-pixel through
        // [sampleAtLocal] which honours tile / filter / mip selection on
        // the homogeneous-divide local coordinate.
        if (inv.hasPerspective()) {
            val y0p = devY + 0.5f
            var x0p = devX + 0.5f
            for (i in 0 until count) {
                val (lxp, lyp) = inv.mapXY(x0p, y0p)
                dst[i] = sampleAtLocal(lxp, lyp)
                x0p += 1f
            }
            return
        }
        val w = image.width; val h = image.height
        val x0 = devX + 0.5f
        val y0 = devY + 0.5f
        var lx = inv.sx * x0 + inv.kx * y0 + inv.tx
        var ly = inv.ky * x0 + inv.sy * y0 + inv.ty
        val stepX = inv.sx
        val stepY = inv.ky

        // Phase G10 — anisotropic shortcut : N-tap average along the
        // major axis of the texture-space ellipse.
        if (sampling.useAniso && xformedLevels8.size > 1) {
            val (mdx, mdy) = anisoMajorStep()
            val n = sampling.maxAniso
            for (i in 0 until count) {
                dst[i] = sampleAniso8(lx, ly, mdx, mdy, n)
                lx += stepX
                ly += stepY
            }
            return
        }

        val cubic = sampling.cubic
        if (cubic != null) {
            for (i in 0 until count) {
                dst[i] = sampleCubic8(lx, ly, w, h, cubic)
                lx += stepX
                ly += stepY
            }
            return
        }

        // Phase G10 — mip LOD selection (linear/nearest filter still
        // governs the in-level sampling).
        val level = pickMipLevel()
        if (level > 0) {
            val lvlW = xformedLevelW(level)
            val lvlH = xformedLevelH(level)
            val mipScaleX = lvlW.toFloat() / w
            val mipScaleY = lvlH.toFloat() / h
            when (sampling.filter) {
                SkFilterMode.kNearest -> {
                    for (i in 0 until count) {
                        dst[i] = sampleNearestAtLevel8(lx * mipScaleX, ly * mipScaleY, level)
                        lx += stepX
                        ly += stepY
                    }
                }
                SkFilterMode.kLinear -> {
                    for (i in 0 until count) {
                        dst[i] = sampleLinearAtLevel8(lx * mipScaleX, ly * mipScaleY, level)
                        lx += stepX
                        ly += stepY
                    }
                }
            }
            return
        }

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
        // R-final.7 — perspective path : per-pixel sampling through
        // [sampleAtLocalF16]. See `shadeRow` for the rationale.
        if (inv.hasPerspective()) {
            val y0p = devY + 0.5f
            var x0p = devX + 0.5f
            var diP = 0
            for (i in 0 until count) {
                val (lxp, lyp) = inv.mapXY(x0p, y0p)
                sampleAtLocalF16(lxp, lyp, dst, diP)
                x0p += 1f
                diP += 4
            }
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

        // Phase G10 — aniso shortcut.
        if (sampling.useAniso && xformedLevelsF16.size > 1) {
            val (mdx, mdy) = anisoMajorStep()
            val n = sampling.maxAniso
            for (i in 0 until count) {
                sampleAnisoF16(lx, ly, mdx, mdy, n, dst, di)
                di += 4
                lx += stepX
                ly += stepY
            }
            return
        }

        val cubic = sampling.cubic
        if (cubic != null) {
            for (i in 0 until count) {
                sampleCubicF16(lx, ly, w, h, cubic, dst, di)
                di += 4
                lx += stepX
                ly += stepY
            }
            return
        }

        // Phase G10 — mip LOD selection.
        val level = pickMipLevel()
        if (level > 0) {
            val lvlW = xformedLevelW(level)
            val lvlH = xformedLevelH(level)
            val mipScaleX = lvlW.toFloat() / w
            val mipScaleY = lvlH.toFloat() / h
            when (sampling.filter) {
                SkFilterMode.kNearest -> {
                    for (i in 0 until count) {
                        sampleNearestAtLevelF16(lx * mipScaleX, ly * mipScaleY, level, dst, di)
                        di += 4
                        lx += stepX
                        ly += stepY
                    }
                }
                SkFilterMode.kLinear -> {
                    for (i in 0 until count) {
                        sampleLinearAtLevelF16(lx * mipScaleX, ly * mipScaleY, level, dst, di)
                        di += 4
                        lx += stepX
                        ly += stepY
                    }
                }
            }
            return
        }

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
        val cubic = sampling.cubic
        if (cubic != null) return sampleCubic8(lx, ly, w, h, cubic)
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

    /**
     * Bicubic sample at fractional coords `(lx, ly)` from [xformedPixels]
     * — 8-bit ARGB output. Convolves the 4×4 neighbourhood around the
     * sample point with the [SkCubicBC] kernel (Mitchell when
     * `(B, C) = (1/3, 1/3)`, Catmull-Rom when `(B, C) = (0, 1/2)`),
     * with per-channel weights normalised by the sum of the kernel
     * (handles minor numerical drift away from 1.0). Each output
     * channel is clamped to `[0, 255]` to absorb the kernel's
     * negative-lobe overshoot.
     */
    private fun sampleCubic8(lx: Float, ly: Float, w: Int, h: Int, cubic: SkCubicResampler): SkColor {
        val xf = lx - 0.5f
        val yf = ly - 0.5f
        val ix = floor(xf).toInt()
        val iy = floor(yf).toInt()
        val fx = xf - ix
        val fy = yf - iy

        // Pre-compute 1-D kernel weights along x and y. The 4 samples are
        // at offsets ix-1, ix, ix+1, ix+2 from the sample point ; in
        // kernel-space the distances are |t| = (1+fx), fx, (1-fx), (2-fx).
        val wx0 = SkCubicBC.weight(1f + fx, cubic.B, cubic.C)
        val wx1 = SkCubicBC.weight(fx,       cubic.B, cubic.C)
        val wx2 = SkCubicBC.weight(1f - fx,  cubic.B, cubic.C)
        val wx3 = SkCubicBC.weight(2f - fx,  cubic.B, cubic.C)
        val wy0 = SkCubicBC.weight(1f + fy, cubic.B, cubic.C)
        val wy1 = SkCubicBC.weight(fy,       cubic.B, cubic.C)
        val wy2 = SkCubicBC.weight(1f - fy,  cubic.B, cubic.C)
        val wy3 = SkCubicBC.weight(2f - fy,  cubic.B, cubic.C)

        var sumA = 0f; var sumR = 0f; var sumG = 0f; var sumB = 0f; var sumW = 0f
        for (j in 0..3) {
            val iyj = iy + j - 1
            val wy = when (j) { 0 -> wy0; 1 -> wy1; 2 -> wy2; else -> wy3 }
            val (syi, decalY) = applyTileNearest(iyj.toFloat() + 0.5f, h, tileY)
            for (i in 0..3) {
                val ixi = ix + i - 1
                val wx = when (i) { 0 -> wx0; 1 -> wx1; 2 -> wx2; else -> wx3 }
                val (sxi, decalX) = applyTileNearest(ixi.toFloat() + 0.5f, w, tileX)
                val wt = wx * wy
                if (decalX || decalY) {
                    sumW += wt
                    continue
                }
                val c = xformedPixels[syi * w + sxi]
                sumA += wt * SkColorGetA(c)
                sumR += wt * SkColorGetR(c)
                sumG += wt * SkColorGetG(c)
                sumB += wt * SkColorGetB(c)
                sumW += wt
            }
        }
        // sumW is mathematically 1.0 but we divide defensively to absorb
        // tiny FP drift (matches upstream's "normalize cubic weights"
        // step in SkRasterPipeline).
        val invW = if (sumW != 0f) 1f / sumW else 0f
        val a = (sumA * invW + 0.5f).toInt().coerceIn(0, 255)
        val r = (sumR * invW + 0.5f).toInt().coerceIn(0, 255)
        val g = (sumG * invW + 0.5f).toInt().coerceIn(0, 255)
        val b = (sumB * invW + 0.5f).toInt().coerceIn(0, 255)
        return SkColorSetARGB(a, r, g, b)
    }

    /** Bicubic sample, premul-float output (no byte quantization). */
    private fun sampleCubicF16(
        lx: Float, ly: Float, w: Int, h: Int, cubic: SkCubicResampler,
        out: FloatArray, off: Int,
    ) {
        val xf = lx - 0.5f
        val yf = ly - 0.5f
        val ix = floor(xf).toInt()
        val iy = floor(yf).toInt()
        val fx = xf - ix
        val fy = yf - iy

        val wx0 = SkCubicBC.weight(1f + fx, cubic.B, cubic.C)
        val wx1 = SkCubicBC.weight(fx,       cubic.B, cubic.C)
        val wx2 = SkCubicBC.weight(1f - fx,  cubic.B, cubic.C)
        val wx3 = SkCubicBC.weight(2f - fx,  cubic.B, cubic.C)
        val wy0 = SkCubicBC.weight(1f + fy, cubic.B, cubic.C)
        val wy1 = SkCubicBC.weight(fy,       cubic.B, cubic.C)
        val wy2 = SkCubicBC.weight(1f - fy,  cubic.B, cubic.C)
        val wy3 = SkCubicBC.weight(2f - fy,  cubic.B, cubic.C)

        val acc = FloatArray(4)
        var sumW = 0f
        val src = xformedPixelsF16
        for (j in 0..3) {
            val iyj = iy + j - 1
            val wy = when (j) { 0 -> wy0; 1 -> wy1; 2 -> wy2; else -> wy3 }
            val (syi, decalY) = applyTileNearest(iyj.toFloat() + 0.5f, h, tileY)
            for (i in 0..3) {
                val ixi = ix + i - 1
                val wx = when (i) { 0 -> wx0; 1 -> wx1; 2 -> wx2; else -> wx3 }
                val wt = wx * wy
                sumW += wt
                if (decalY) continue
                val (sxi, decalX) = applyTileNearest(ixi.toFloat() + 0.5f, w, tileX)
                if (decalX) continue
                val o = (syi * w + sxi) * 4
                acc[0] += wt * src[o]
                acc[1] += wt * src[o + 1]
                acc[2] += wt * src[o + 2]
                acc[3] += wt * src[o + 3]
            }
        }
        val invW = if (sumW != 0f) 1f / sumW else 0f
        out[off]     = (acc[0] * invW).coerceIn(0f, 1f)
        out[off + 1] = (acc[1] * invW).coerceIn(0f, 1f)
        out[off + 2] = (acc[2] * invW).coerceIn(0f, 1f)
        out[off + 3] = (acc[3] * invW).coerceIn(0f, 1f)
    }

    /** Mathematical modulo: result is always in `[0, m)` for `m > 0`. */
    private fun mod(n: Int, m: Int): Int {
        val r = n % m
        return if (r < 0) r + m else r
    }

    // ────────────────────── G10 — per-level samplers ─────────────────────

    private fun xformedLevelW(level: Int): Int =
        image.mipLevels?.get(level)?.width ?: image.width

    private fun xformedLevelH(level: Int): Int =
        image.mipLevels?.get(level)?.height ?: image.height

    /**
     * Nearest sample at integer mip `level`. The `(lx, ly)` coords are
     * already scaled into that level's local space by the caller.
     */
    private fun sampleNearestAtLevel8(lx: Float, ly: Float, level: Int): SkColor {
        val w = xformedLevelW(level)
        val h = xformedLevelH(level)
        val (sxi, decalX) = applyTileNearest(lx, w, tileX)
        val (syi, decalY) = applyTileNearest(ly, h, tileY)
        if (decalX || decalY) return 0
        return xformedLevels8[level][syi * w + sxi]
    }

    /** Bilinear sample at integer mip `level`. */
    private fun sampleLinearAtLevel8(lx: Float, ly: Float, level: Int): SkColor {
        val w = xformedLevelW(level)
        val h = xformedLevelH(level)
        val src = xformedLevels8[level]
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
        val c00 = if (dx0 || dy0) 0 else src[iy0 * w + ix0]
        val c10 = if (dx1 || dy0) 0 else src[iy0 * w + ix1]
        val c01 = if (dx0 || dy1) 0 else src[iy1 * w + ix0]
        val c11 = if (dx1 || dy1) 0 else src[iy1 * w + ix1]
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

    private fun sampleNearestAtLevelF16(lx: Float, ly: Float, level: Int, out: FloatArray, off: Int) {
        val w = xformedLevelW(level)
        val h = xformedLevelH(level)
        val (sxi, decalX) = applyTileNearest(lx, w, tileX)
        val (syi, decalY) = applyTileNearest(ly, h, tileY)
        if (decalX || decalY) {
            out[off] = 0f; out[off + 1] = 0f; out[off + 2] = 0f; out[off + 3] = 0f
            return
        }
        val src = xformedLevelsF16[level]
        val o = (syi * w + sxi) * 4
        out[off]     = src[o]
        out[off + 1] = src[o + 1]
        out[off + 2] = src[o + 2]
        out[off + 3] = src[o + 3]
    }

    private fun sampleLinearAtLevelF16(lx: Float, ly: Float, level: Int, out: FloatArray, off: Int) {
        val w = xformedLevelW(level)
        val h = xformedLevelH(level)
        val src = xformedLevelsF16[level]
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
        for (chan in 0..3) {
            val v00 = if (dx0 || dy0) 0f else src[(iy0 * w + ix0) * 4 + chan]
            val v10 = if (dx1 || dy0) 0f else src[(iy0 * w + ix1) * 4 + chan]
            val v01 = if (dx0 || dy1) 0f else src[(iy1 * w + ix0) * 4 + chan]
            val v11 = if (dx1 || dy1) 0f else src[(iy1 * w + ix1) * 4 + chan]
            out[off + chan] = w00 * v00 + w10 * v10 + w01 * v01 + w11 * v11
        }
    }

    // ─────────────────────── G10 — aniso sampler ─────────────────────────

    /**
     * Phase G10 — N-tap anisotropic sample. Walks `n` steps along the
     * texture-space major axis `(mdx, mdy)` (in source pixels per
     * device pixel), averaging linear samples taken from the mip level
     * that matches the **minor**-axis footprint. This is a pragmatic
     * raster shortcut — upstream Skia raster uses a similar
     * simplification (the WeightedFilter path).
     */
    private fun sampleAniso8(cx: Float, cy: Float, mdx: Float, mdy: Float, n: Int): SkColor {
        // Pick a mip level off the **shorter** of the two ellipse axes
        // so the minor-axis frequency is band-limited before the N-tap
        // along-major average.
        val mipLevel = pickMipLevelForAniso(mdx, mdy, n)
        val lvlW = xformedLevelW(mipLevel)
        val lvlH = xformedLevelH(mipLevel)
        val mipScaleX = lvlW.toFloat() / image.width
        val mipScaleY = lvlH.toFloat() / image.height
        // Steps in level-space.
        val sdx = mdx * mipScaleX
        val sdy = mdy * mipScaleY
        // N taps centered on (cx, cy), spanning the major axis once
        // (`t ∈ [-0.5, 0.5]`).
        var sumA = 0f; var sumR = 0f; var sumG = 0f; var sumB = 0f
        for (k in 0 until n) {
            // Distribute taps uniformly in [-0.5, 0.5] * majorLength.
            val t = if (n == 1) 0f else (k.toFloat() / (n - 1)) - 0.5f
            val sx = cx * mipScaleX + t * sdx
            val sy = cy * mipScaleY + t * sdy
            val s = sampleLinearAtLevel8(sx, sy, mipLevel)
            sumA += SkColorGetA(s)
            sumR += SkColorGetR(s)
            sumG += SkColorGetG(s)
            sumB += SkColorGetB(s)
        }
        val inv = 1f / n
        val a = (sumA * inv + 0.5f).toInt().coerceIn(0, 255)
        val r = (sumR * inv + 0.5f).toInt().coerceIn(0, 255)
        val g = (sumG * inv + 0.5f).toInt().coerceIn(0, 255)
        val b = (sumB * inv + 0.5f).toInt().coerceIn(0, 255)
        return SkColorSetARGB(a, r, g, b)
    }

    private fun sampleAnisoF16(
        cx: Float, cy: Float, mdx: Float, mdy: Float, n: Int,
        out: FloatArray, off: Int,
    ) {
        val mipLevel = pickMipLevelForAniso(mdx, mdy, n)
        val lvlW = xformedLevelW(mipLevel)
        val lvlH = xformedLevelH(mipLevel)
        val mipScaleX = lvlW.toFloat() / image.width
        val mipScaleY = lvlH.toFloat() / image.height
        val sdx = mdx * mipScaleX
        val sdy = mdy * mipScaleY
        val tmp = FloatArray(4)
        var a = 0f; var r = 0f; var g = 0f; var b = 0f
        for (k in 0 until n) {
            val t = if (n == 1) 0f else (k.toFloat() / (n - 1)) - 0.5f
            val sx = cx * mipScaleX + t * sdx
            val sy = cy * mipScaleY + t * sdy
            sampleLinearAtLevelF16(sx, sy, mipLevel, tmp, 0)
            r += tmp[0]; g += tmp[1]; b += tmp[2]; a += tmp[3]
        }
        val inv = 1f / n
        out[off]     = r * inv
        out[off + 1] = g * inv
        out[off + 2] = b * inv
        out[off + 3] = a * inv
    }

    /**
     * Pick the mip level for an N-tap aniso. The minor-axis footprint
     * is `max(majorLen / N, minorLen)` so the chosen level is
     * `floor(log2(minorLen))` — enough mips that the N taps along the
     * major axis evenly cover the elliptical footprint without
     * undersampling the orthogonal direction.
     */
    private fun pickMipLevelForAniso(mdx: Float, mdy: Float, n: Int): Int {
        val numLevels = xformedLevels8.size
        if (numLevels <= 1) return 0
        val inv = deviceToLocal ?: return 0
        val cx0 = inv.sx; val cx1 = inv.ky
        val cy0 = inv.kx; val cy1 = inv.sy
        val lx = sqrt(cx0 * cx0 + cx1 * cx1)
        val ly = sqrt(cy0 * cy0 + cy1 * cy1)
        val majorLen = sqrt(mdx * mdx + mdy * mdy)
        // The "minor" axis length — the smaller of the two column norms.
        val minorLen = if (lx >= ly) ly else lx
        // Effective per-tap minor-axis footprint.
        val perTap = if (n > 0) majorLen / n.toFloat() else majorLen
        val effective = max(minorLen, perTap)
        if (effective <= 1f) return 0
        val level = floor(ln(effective) / LN2).toInt()
        return level.coerceIn(0, numLevels - 1)
    }

    private companion object {
        private val LN2: Float = kotlin.math.ln(2.0).toFloat()
    }
}
