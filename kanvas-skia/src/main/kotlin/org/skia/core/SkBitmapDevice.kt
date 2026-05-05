package org.skia.core

import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColor
import org.skia.foundation.SkColorGetA
import org.skia.foundation.SkColorGetB
import org.skia.foundation.SkColorGetG
import org.skia.foundation.SkColorGetR
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathFillType
import org.skia.foundation.SkShader
import org.skia.foundation.SkStroker
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import kotlin.math.ceil as kCeil
import kotlin.math.floor as kFloor

private fun floor(v: Float): Int = kFloor(v.toDouble()).toInt()
private fun ceil(v: Float): Int = kCeil(v.toDouble()).toInt()

/** Chord-error tolerance (in device-space pixels) for Bézier flattening. */
private const val PATH_FLATNESS: Float = 0.25f
/** Squared tolerance — used to compare against `cross² / chord²` without `sqrt`. */
private const val PATH_FLATNESS_SQ: Float = PATH_FLATNESS * PATH_FLATNESS
/** Recursion depth bound for adaptive subdivision (2^18 safety net). */
private const val PATH_MAX_DEPTH: Int = 18
/** Number of uniform-`t` segments for conic flattening. */
private const val CONIC_STEPS: Int = 32

/**
 * Skia's non-AA rect rasterization rule: pixel N is covered iff
 * `rect.{l,t} - 0.5 < N ≤ rect.{r,b} - 0.5` (top-exclusive, bottom-inclusive),
 * equivalent to integer range `[floor(c + 0.5), floor(c + 0.5))` from
 * `SkScalarRoundToInt`. Half-integer ties round toward +∞.
 */
private fun pixelEdge(c: Float): Int = kFloor(c.toDouble() + 0.5).toInt()

/**
 * CPU raster device. Phase 1: non-AA rect fill and stroke. Phase 2: adds
 * analytic AA coverage for axis-aligned rects (`paint.isAntiAlias = true`).
 * Phase 3a: adds `drawPath` (line-only paths, fill style only) using a
 * scanline rasterizer with 4×4 supersampling for AA.
 *
 * Receives device-space coordinates from `SkCanvas`; the canvas owns the
 * matrix and clip stacks and is responsible for transforming and clipping
 * into the `clip` rect passed here. `drawPath` is the exception — it
 * receives source-space verbs along with the affine `(sx, sy, tx, ty)` so
 * it can transform vertices itself (cheaper than allocating a transformed
 * copy of the path).
 */
public class SkBitmapDevice(public val bitmap: SkBitmap) {

    public val width: Int get() = bitmap.width
    public val height: Int get() = bitmap.height

    /**
     * Source-space colors entering the device are sRGB-encoded (that's the
     * SkColor convention). Build a one-shot xform that brings them into the
     * bitmap's color space. When the bitmap is sRGB, this is the identity
     * pipeline (`flags.isIdentity == true`) and [transformPaintColor] is a
     * no-op.
     */
    private val xformSteps: SkColorSpaceXformSteps = SkColorSpaceXformSteps(
        src = SkColorSpace.makeSRGB(), srcAT = SkAlphaType.kUnpremul,
        dst = bitmap.colorSpace,      dstAT = SkAlphaType.kUnpremul,
    )

    public fun deviceClipBounds(): SkIRect = SkIRect.MakeWH(width, height)

    public fun drawRect(rect: SkRect, clip: SkIRect, paint: SkPaint) {
        val devPaint = inDeviceColorSpace(paint)
        if (devPaint.isAntiAlias) drawRectAA(rect, clip, devPaint) else drawRectNonAA(rect, clip, devPaint)
    }

    /**
     * `true` when the blend mode produces a non-`dst` output for a fully
     * transparent source colour (e.g. [SkBlendMode.kClear], [SkBlendMode.kDstIn]
     * with `sa == 0` zeroes dst). Callers normally short-circuit when
     * `src.alpha == 0` to avoid touching covered pixels at all; for these
     * modes the device must still walk the spans and apply the blend.
     */
    private fun modeAffectsZeroAlphaSrc(mode: SkBlendMode): Boolean = when (mode) {
        // r = 0
        SkBlendMode.kClear,
        // r = s — when s premul = 0, dst becomes 0
        SkBlendMode.kSrc,
        // r = s * da — zeroes dst where src.alpha == 0
        SkBlendMode.kSrcIn,
        // r = d * sa — zeroes dst where src.alpha == 0
        SkBlendMode.kDstIn,
        // r = s * (1-da) — zeroes dst where src.alpha == 0
        SkBlendMode.kSrcOut,
        // r = d*sa + s*(1-da) — zeroes dst where src.alpha == 0
        SkBlendMode.kDstATop,
        // r = s * d — zeroes dst where src is 0
        SkBlendMode.kModulate -> true
        else -> false
    }

    /**
     * Mirrors Skia's `SkBitmapDevice::drawPaint`. Fills every pixel inside
     * [clip] with `paint.color` (or [paint.shader]'s output, when present),
     * composited via [paint.blendMode]. The clip is integer-aligned in
     * device coords, so per-pixel coverage is binary — no AA bookkeeping
     * needed regardless of `paint.isAntiAlias`.
     *
     * [ctm] is the current canvas matrix; only used when [paint] has a
     * shader (the shader's `setupForDraw` needs it to compute the
     * `device → local` transform).
     */
    public fun drawPaint(ctm: SkMatrix, clip: SkIRect, paint: SkPaint) {
        val mode = paint.blendMode
        val l = clip.left.coerceAtLeast(0)
        val t = clip.top.coerceAtLeast(0)
        val r = clip.right.coerceAtMost(width)
        val b = clip.bottom.coerceAtMost(height)
        if (l >= r || t >= b) return

        val shader = paint.shader
        if (shader != null) {
            // Shader path. paint.alpha modulates the shader output (Skia's
            // semantics: shaderColor * paint.alpha). With kSrcOver + F16 we
            // take a pure premul-float path that mirrors the Phase 6b
            // shader path in [scanFillPath] (no per-pixel byte conversion).
            shader.setupForDraw(ctm, xformSteps)
            val paintAlphaF = SkColorGetA(paint.color) / 255f
            if (paintAlphaF == 0f && !modeAffectsZeroAlphaSrc(mode)) return
            val rowWidth = r - l
            val isF16 = bitmap.colorType == org.skia.foundation.SkColorType.kRGBA_F16Norm
            val useF16 = isF16 && mode == SkBlendMode.kSrcOver
            if (useF16) {
                val rowF16 = FloatArray(rowWidth * 4)
                for (y in t until b) {
                    shader.shadeRowF16(l, y, rowWidth, rowF16)
                    for (xOff in 0 until rowWidth) {
                        val si = xOff * 4
                        val sa = rowF16[si + 3] * paintAlphaF
                        if (sa <= 0f) continue
                        val sr = rowF16[si]     * paintAlphaF
                        val sg = rowF16[si + 1] * paintAlphaF
                        val sb = rowF16[si + 2] * paintAlphaF
                        blendF16Premul(l + xOff, y, sr, sg, sb, sa)
                    }
                }
            } else {
                val row = IntArray(rowWidth)
                val paintAlpha = SkColorGetA(paint.color)
                for (y in t until b) {
                    shader.shadeRow(l, y, rowWidth, row)
                    for (xOff in 0 until rowWidth) {
                        val src = row[xOff]
                        val srcA = SkColorGetA(src)
                        if (srcA == 0) continue
                        val effA = if (paintAlpha == 0xFF) srcA
                            else (srcA * paintAlpha + 127) / 255
                        if (effA == 0) continue
                        blend(l + xOff, y, (effA shl 24) or (src and 0x00FFFFFF), mode)
                    }
                }
            }
            return
        }

        // Solid-colour path (Phase 1).
        val color = transformPaintColor(paint.color)
        if (SkColorGetA(color) == 0 && !modeAffectsZeroAlphaSrc(mode)) return
        for (y in t until b) {
            for (x in l until r) blend(x, y, color, mode)
        }
    }

    /**
     * Composite `src`'s pixels onto this device, with `src`'s `(0, 0)`
     * landing at this device's `(originX, originY)`, intersecting writes
     * with [clip] (in this device's coords). Source pixels are SrcOver-
     * blended through `paint?.alpha` — when `paint` is null or fully
     * opaque, the per-pixel alpha is taken straight from `src`.
     *
     * Used by `SkCanvas.restore` to flatten a `saveLayer`'s offscreen
     * device back into its parent. Pre-condition: `src` and this device
     * share the same color space, so no per-pixel xform is needed (the
     * canvas seeds the layer device with the parent's color space).
     */
    public fun compositeFrom(
        src: SkBitmapDevice,
        originX: Int,
        originY: Int,
        clip: SkIRect,
        paint: SkPaint?,
    ) {
        val paintAlpha = paint?.color?.let { SkColorGetA(it) } ?: 0xFF
        if (paintAlpha == 0) return
        val l = maxOf(clip.left, originX, 0)
        val t = maxOf(clip.top, originY, 0)
        val r = minOf(clip.right, originX + src.width, width)
        val b = minOf(clip.bottom, originY + src.height, height)
        if (l >= r || t >= b) return
        // Read source pixels via the colorType-aware [SkBitmap.getPixel]
        // accessor so this composite works for both 8888-only and F16-only
        // layer pairs (and any future colorType combos). Slightly slower
        // than the historical raw-IntArray walk, but only matters when a
        // GM uses `saveLayer` heavily — none in scope do.
        for (y in t until b) {
            for (x in l until r) {
                val sample = src.bitmap.getPixel(x - originX, y - originY)
                val effective = if (paintAlpha == 0xFF) sample else applyAlpha(sample, paintAlpha)
                if (effective ushr 24 == 0) continue
                // Phase 6 entry: saveLayer flatten remains hardcoded SrcOver
                // (per CLAUDE.md — extending it to arbitrary blend modes is a
                // separate ticket).
                blend(x, y, effective, SkBlendMode.kSrcOver)
            }
        }
    }

    /**
     * Draw `image` into the supplied **device-space** `devDst` rect, sampling
     * the image-space `src` sub-rectangle. The canvas has already applied its
     * CTM to produce `devDst`; the device only needs to perform the inverse
     * `dst → src` mapping per pixel and sample.
     *
     * Pixel coverage uses the same top-exclusive / bottom-inclusive rule as
     * the non-AA rect path (`pixelEdge`); AA edges of `devDst` are not
     * fractionally covered (matches Skia's default `drawImageRect` behaviour
     * when `paint` lacks `setAntiAlias(true)`).
     *
     * Supported [SkFilterMode]s : `kNearest`, `kLinear`. Mipmap and bicubic
     * sampling are out of scope. Out-of-range sample coordinates are clamped
     * to the image edge texels — Skia's `kClamp` tile mode and the default
     * behaviour under both [SrcRectConstraint] variants for axis-aligned
     * mappings without filter overflow.
     */
    public fun drawImageRect(
        image: SkImage,
        src: SkRect,
        devDst: SkRect,
        sampling: SkSamplingOptions,
        paint: SkPaint?,
        constraint: SrcRectConstraint,
        clip: SkIRect,
    ) {
        if (devDst.right <= devDst.left || devDst.bottom <= devDst.top) return
        if (src.right <= src.left || src.bottom <= src.top) return
        if (image.width <= 0 || image.height <= 0) return

        val ix0 = pixelEdge(devDst.left).coerceAtLeast(clip.left)
        val iy0 = pixelEdge(devDst.top).coerceAtLeast(clip.top)
        val ix1 = pixelEdge(devDst.right).coerceAtMost(clip.right)
        val iy1 = pixelEdge(devDst.bottom).coerceAtMost(clip.bottom)
        if (ix0 >= ix1 || iy0 >= iy1) return

        val devW = devDst.right - devDst.left
        val devH = devDst.bottom - devDst.top
        val srcW = src.right - src.left
        val srcH = src.bottom - src.top
        val scaleX = srcW / devW
        val scaleY = srcH / devH

        val paintAlpha = paint?.color?.let { SkColorGetA(it) } ?: 0xFF
        if (paintAlpha == 0) return
        val mode = paint?.blendMode ?: SkBlendMode.kSrcOver
        val maxX = image.width - 1
        val maxY = image.height - 1

        // [SkImage] currently has no `colorSpace` property, so by convention
        // image pixels are sRGB-encoded (same as SkColor). The device's
        // [xformSteps] (sRGB → bitmap.colorSpace) is reused here: we
        // pre-convert the entire image into device-space pixels once, then
        // sample from that buffer in the inner loop. Cheap because images
        // tend to have far fewer pixels than the dst rect.
        val devPixels = imagePixelsInDeviceColorSpace(image)

        // For modes whose formula reduces to a non-`dst` value at sa=0
        // (e.g. kClear, kSrc, kSrcIn, kSrcOut, kDstIn, kDstATop, kModulate),
        // we must still call `blend` on transparent samples — otherwise the
        // covered pixel keeps its dst value instead of being zeroed.
        val mustBlendZero = modeAffectsZeroAlphaSrc(mode)
        when (sampling.filter) {
            SkFilterMode.kNearest -> {
                for (py in iy0 until iy1) {
                    val srcYc = src.top + (py + 0.5f - devDst.top) * scaleY
                    val iy = floor(srcYc).coerceIn(0, maxY)
                    for (px in ix0 until ix1) {
                        val srcXc = src.left + (px + 0.5f - devDst.left) * scaleX
                        val ix = floor(srcXc).coerceIn(0, maxX)
                        val sample = applyAlpha(devPixels[iy * image.width + ix], paintAlpha)
                        if (sample ushr 24 == 0 && !mustBlendZero) continue
                        blend(px, py, sample, mode)
                    }
                }
            }
            SkFilterMode.kLinear -> {
                for (py in iy0 until iy1) {
                    val srcYf = src.top + (py + 0.5f - devDst.top) * scaleY - 0.5f
                    val iy0i = floor(srcYf).coerceIn(0, maxY)
                    val iy1i = (iy0i + 1).coerceAtMost(maxY)
                    val fy = (srcYf - floor(srcYf).toFloat()).coerceIn(0f, 1f)
                    for (px in ix0 until ix1) {
                        val srcXf = src.left + (px + 0.5f - devDst.left) * scaleX - 0.5f
                        val ix0i = floor(srcXf).coerceIn(0, maxX)
                        val ix1i = (ix0i + 1).coerceAtMost(maxX)
                        val fx = (srcXf - floor(srcXf).toFloat()).coerceIn(0f, 1f)
                        val c00 = devPixels[iy0i * image.width + ix0i]
                        val c10 = devPixels[iy0i * image.width + ix1i]
                        val c01 = devPixels[iy1i * image.width + ix0i]
                        val c11 = devPixels[iy1i * image.width + ix1i]
                        val sample = applyAlpha(bilerpARGB(c00, c10, c01, c11, fx, fy), paintAlpha)
                        if (sample ushr 24 == 0 && !mustBlendZero) continue
                        blend(px, py, sample, mode)
                    }
                }
            }
        }
    }

    /**
     * Pre-convert all `image` pixels from sRGB into the bitmap's color space.
     * Identity-fast-path returns the underlying buffer when the device is
     * sRGB (no allocation, no per-pixel float work).
     */
    private fun imagePixelsInDeviceColorSpace(image: SkImage): IntArray {
        if (xformSteps.flags.isIdentity) return image.pixels
        val out = IntArray(image.width * image.height)
        for (i in out.indices) out[i] = transformPaintColor(image.pixels[i])
        return out
    }

    /** Modulate `src.alpha` by `paintAlpha / 255`, leaving RGB unchanged. */
    private fun applyAlpha(src: SkColor, paintAlpha: Int): SkColor {
        if (paintAlpha == 0xFF) return src
        val sa = SkColorGetA(src)
        val newA = (sa * paintAlpha + 127) / 255
        return (src and 0x00FFFFFF) or (newA shl 24)
    }

    /** Bilinear interpolation in non-premultiplied ARGB; matches Skia for opaque samples. */
    private fun bilerpARGB(c00: SkColor, c10: SkColor, c01: SkColor, c11: SkColor, fx: Float, fy: Float): SkColor {
        val ifx = 1f - fx; val ify = 1f - fy
        val w00 = ifx * ify; val w10 = fx * ify; val w01 = ifx * fy; val w11 = fx * fy
        val a = (SkColorGetA(c00) * w00 + SkColorGetA(c10) * w10 + SkColorGetA(c01) * w01 + SkColorGetA(c11) * w11 + 0.5f).toInt().coerceIn(0, 255)
        val r = (SkColorGetR(c00) * w00 + SkColorGetR(c10) * w10 + SkColorGetR(c01) * w01 + SkColorGetR(c11) * w11 + 0.5f).toInt().coerceIn(0, 255)
        val g = (SkColorGetG(c00) * w00 + SkColorGetG(c10) * w10 + SkColorGetG(c01) * w01 + SkColorGetG(c11) * w11 + 0.5f).toInt().coerceIn(0, 255)
        val b = (SkColorGetB(c00) * w00 + SkColorGetB(c10) * w10 + SkColorGetB(c01) * w01 + SkColorGetB(c11) * w11 + 0.5f).toInt().coerceIn(0, 255)
        return SkColorSetARGB(a, r, g, b)
    }

    /**
     * Fill or stroke a path under the current CTM `(sx, sy, tx, ty)`.
     *
     * Phase 3a: kFill_Style with line-only paths and a 4×4 supersampled
     * scanline rasterizer.
     * Phase 3b: full Bézier verbs (quad/conic/cubic) flattened in device
     * space inside [buildEdges] before the scanline walk.
     * Phase 3c: kStroke_Style and kStrokeAndFill_Style — the stroker
     * ([SkStroker]) converts the source-space path into a filled outline
     * path, which is then rasterized via the same fill pipeline. Source
     * space stroke means the stroke width scales with the CTM.
     *
     * The path's verbs are interpreted in source space and transformed via
     * `(sx, sy, tx, ty)` as edges are emitted, so `dev = (sx*x + tx, sy*y + ty)`.
     * Horizontal device-space edges contribute nothing to scanline crossings
     * and are dropped. Each contour ending without `kClose` (the convention
     * `SkPath::Polygon(pts, isClosed=false)` relies on) is implicitly closed
     * by the rasterizer back to its `kMove`.
     */
    public fun drawPath(
        path: SkPath,
        ctm: SkMatrix,
        clip: SkIRect, paint: SkPaint,
    ) {
        if (path.isEmpty()) return

        val shader = paint.shader
        val color: SkColor
        val baseA: Int
        if (shader != null) {
            // Shader supplies per-pixel colour. Set it up once for this draw,
            // then the rasterizer modulates each shaded pixel by AA coverage
            // *and* by `paint.alpha` (matches Skia's
            // "shaderColor.alpha *= paint.alpha" semantics — Phase 5g).
            shader.setupForDraw(ctm, xformSteps)
            color = 0    // unused — per-pixel colour comes from the shader
            baseA = SkColorGetA(paint.color)  // paint.alpha modulator
            if (baseA == 0 && !modeAffectsZeroAlphaSrc(paint.blendMode)) return
        } else {
            color = transformPaintColor(paint.color)
            baseA = SkColorGetA(color)
            // Modes that produce a non-trivial output for transparent src
            // (e.g. kClear, kDstIn) must still walk covered pixels even when
            // the paint colour has alpha 0.
            if (baseA == 0 && !modeAffectsZeroAlphaSrc(paint.blendMode)) return
        }
        val supers = if (paint.isAntiAlias) 4 else 1
        val resScale = ctm.computeMaxScale().coerceAtLeast(1f)
        val mode = paint.blendMode

        when (paint.style) {
            SkPaint.Style.kFill_Style ->
                fillPath(path, ctm, clip, color, baseA, supers, shader, mode)
            SkPaint.Style.kStroke_Style -> {
                val outline = SkStroker.fromPaint(paint, resScale).stroke(path)
                if (outline.isEmpty()) return
                fillPath(outline, ctm, clip, color, baseA, supers, shader, mode)
            }
            SkPaint.Style.kStrokeAndFill_Style -> {
                fillPath(path, ctm, clip, color, baseA, supers, shader, mode)
                val outline = SkStroker.fromPaint(paint, resScale).stroke(path)
                if (outline.isEmpty()) return
                fillPath(outline, ctm, clip, color, baseA, supers, shader, mode)
            }
        }
    }

    private fun fillPath(
        path: SkPath, ctm: SkMatrix,
        clip: SkIRect, color: SkColor, baseA: Int, supers: Int,
        shader: SkShader?, mode: SkBlendMode,
    ) {
        val edges = buildEdges(path, ctm)
        if (edges.isEmpty()) return
        scanFillPath(edges, path.fillType, clip, color, baseA, supers, shader, mode)
    }

    /**
     * Return a [paint] copy with its `color` transformed from sRGB into the
     * bitmap's color space. Identity-fast-path when no xform is needed.
     */
    private fun inDeviceColorSpace(paint: SkPaint): SkPaint {
        if (xformSteps.flags.isIdentity) return paint
        return paint.copy().also { it.color = transformPaintColor(it.color) }
    }

    /**
     * sRGB-encoded `SkColor` → device-encoded `SkColor`. Short-circuits to
     * identity when the bitmap is sRGB (no float work).
     */
    private fun transformPaintColor(c: SkColor): SkColor {
        if (xformSteps.flags.isIdentity) return c
        val a = SkColorGetA(c)
        val r = SkColorGetR(c)
        val g = SkColorGetG(c)
        val b = SkColorGetB(c)
        val rgba = floatArrayOf(r / 255f, g / 255f, b / 255f, a / 255f)
        xformSteps.apply(rgba)
        val outR = (rgba[0] * 255f + 0.5f).toInt().coerceIn(0, 255)
        val outG = (rgba[1] * 255f + 0.5f).toInt().coerceIn(0, 255)
        val outB = (rgba[2] * 255f + 0.5f).toInt().coerceIn(0, 255)
        val outA = (rgba[3] * 255f + 0.5f).toInt().coerceIn(0, 255)
        return SkColorSetARGB(outA, outR, outG, outB)
    }

    /**
     * Phase 6c: working-space `SkColor` (8-bit non-premul ARGB) →
     * premultiplied float quartet `(sr, sg, sb, sa) ∈ [0, 1]`. The colour
     * passed in **must** already be in the bitmap's working colour space
     * (the rasterizer entry points all run their input through
     * [inDeviceColorSpace] / [transformPaintColor] first).
     *
     * Used by the F16 + SrcOver solid-colour fast paths in [fillRectAA],
     * [strokeRectAA] and [scanFillPath]. Calling this once per draw and
     * then applying coverage as a float multiplier lets the rasterizer
     * skip the 8-bit `scaleAlpha(baseA, coverage)` round-to-nearest that
     * would otherwise happen at every covered pixel.
     */
    private fun colorToF16Premul(c: SkColor, out: FloatArray) {
        require(out.size >= 4)
        val a = SkColorGetA(c) / 255f
        out[0] = SkColorGetR(c) / 255f * a
        out[1] = SkColorGetG(c) / 255f * a
        out[2] = SkColorGetB(c) / 255f * a
        out[3] = a
    }

    // --------------------------------------------------------------------
    // Non-AA path (Phase 1) — unchanged.
    // --------------------------------------------------------------------

    private fun drawRectNonAA(rect: SkRect, clip: SkIRect, paint: SkPaint) {
        val mode = paint.blendMode
        when (paint.style) {
            SkPaint.Style.kFill_Style -> fillRect(rect, clip, paint.color, mode)
            SkPaint.Style.kStroke_Style -> strokeRect(rect, paint.strokeWidth, clip, paint.color, mode)
            SkPaint.Style.kStrokeAndFill_Style -> {
                fillRect(rect, clip, paint.color, mode)
                strokeRect(rect, paint.strokeWidth, clip, paint.color, mode)
            }
        }
    }

    private fun fillRect(rect: SkRect, clip: SkIRect, color: SkColor, mode: SkBlendMode) {
        val l = pixelEdge(rect.left).coerceAtLeast(clip.left)
        val t = pixelEdge(rect.top).coerceAtLeast(clip.top)
        val r = pixelEdge(rect.right).coerceAtMost(clip.right)
        val b = pixelEdge(rect.bottom).coerceAtMost(clip.bottom)
        for (y in t until b) {
            for (x in l until r) {
                blend(x, y, color, mode)
            }
        }
    }

    private fun strokeRect(rect: SkRect, strokeWidth: Float, clip: SkIRect, color: SkColor, mode: SkBlendMode) {
        if (strokeWidth <= 0f) {
            // Hairline: 1px-wide outline. Skia's AA-off hairline snaps the
            // outline to floor-style integer coords (matches `SkScan::HairLineRgn`).
            val l = floor(rect.left)
            val t = floor(rect.top)
            val r = floor(rect.right)
            val b = floor(rect.bottom)
            drawHLine(l, r + 1, t, clip, color, mode)         // top edge
            drawHLine(l, r + 1, b, clip, color, mode)         // bottom edge
            drawVLine(l, t + 1, b, clip, color, mode)         // left edge
            drawVLine(r, t + 1, b, clip, color, mode)         // right edge
            return
        }

        val half = strokeWidth * 0.5f
        val outer = SkRect.MakeLTRB(
            rect.left - half, rect.top - half, rect.right + half, rect.bottom + half
        )
        val inner = SkRect.MakeLTRB(
            rect.left + half, rect.top + half, rect.right - half, rect.bottom - half
        )

        val ol = pixelEdge(outer.left).coerceAtLeast(clip.left)
        val ot = pixelEdge(outer.top).coerceAtLeast(clip.top)
        val or = pixelEdge(outer.right).coerceAtMost(clip.right)
        val ob = pixelEdge(outer.bottom).coerceAtMost(clip.bottom)

        val il = pixelEdge(inner.left)
        val it = pixelEdge(inner.top)
        val ir = pixelEdge(inner.right)
        val ib = pixelEdge(inner.bottom)
        val innerEmpty = il >= ir || it >= ib

        for (y in ot until ob) {
            for (x in ol until or) {
                if (innerEmpty || x < il || x >= ir || y < it || y >= ib) {
                    blend(x, y, color, mode)
                }
            }
        }
    }

    // --------------------------------------------------------------------
    // AA path (Phase 2) — analytic axis-aligned coverage.
    //
    // For an axis-aligned rect, per-pixel coverage decomposes into the
    // product of 1-D overlaps along each axis. This is *exact* (no
    // supersampling artefact) and matches Skia's `SkScan::AntiFillRect`
    // closely on integer/fractional axis-aligned boundaries.
    // --------------------------------------------------------------------

    private fun drawRectAA(rect: SkRect, clip: SkIRect, paint: SkPaint) {
        val mode = paint.blendMode
        when (paint.style) {
            SkPaint.Style.kFill_Style -> fillRectAA(rect, clip, paint.color, mode)
            SkPaint.Style.kStroke_Style -> strokeRectAA(rect, paint.strokeWidth, clip, paint.color, mode)
            SkPaint.Style.kStrokeAndFill_Style -> {
                fillRectAA(rect, clip, paint.color, mode)
                strokeRectAA(rect, paint.strokeWidth, clip, paint.color, mode)
            }
        }
    }

    private fun fillRectAA(rect: SkRect, clip: SkIRect, color: SkColor, mode: SkBlendMode) {
        if (rect.right <= rect.left || rect.bottom <= rect.top) return
        val baseA = SkColorGetA(color)
        if (baseA == 0 && !modeAffectsZeroAlphaSrc(mode)) return
        val ix0 = floor(rect.left).coerceAtLeast(clip.left)
        val iy0 = floor(rect.top).coerceAtLeast(clip.top)
        val ix1 = ceil(rect.right).coerceAtMost(clip.right)
        val iy1 = ceil(rect.bottom).coerceAtMost(clip.bottom)

        // Phase 6c — F16 + kSrcOver fast path: keep coverage as a float
        // multiplier all the way to the per-pixel premul-float SrcOver.
        // Eliminates the last 8-bit quantization in front of the F16 buffer
        // (the legacy `scaleAlpha(baseA, cx * cy)` step rounded coverage to
        // 1/255 before blending; now we go straight from the float coverage
        // computation to the F16 store).
        if (bitmap.colorType == org.skia.foundation.SkColorType.kRGBA_F16Norm &&
            mode == SkBlendMode.kSrcOver) {
            val src = FloatArray(4)
            colorToF16Premul(color, src)
            val sr = src[0]; val sg = src[1]; val sb = src[2]; val sa = src[3]
            for (y in iy0 until iy1) {
                val cy = covAxis(rect.top, rect.bottom, y)
                if (cy <= 0f) continue
                for (x in ix0 until ix1) {
                    val cx = covAxis(rect.left, rect.right, x)
                    if (cx <= 0f) continue
                    val cov = cx * cy
                    blendF16Premul(x, y, sr * cov, sg * cov, sb * cov, sa * cov)
                }
            }
            return
        }

        // Legacy 8-bit path (also covers the F16 + non-SrcOver case where
        // we'd need a per-mode float dispatch — out of scope for Phase 6c).
        val rgb = color and 0x00FFFFFF
        for (y in iy0 until iy1) {
            val cy = covAxis(rect.top, rect.bottom, y)
            if (cy <= 0f) continue
            for (x in ix0 until ix1) {
                val cx = covAxis(rect.left, rect.right, x)
                if (cx <= 0f) continue
                val effA = scaleAlpha(baseA, cx * cy)
                if (effA == 0) continue
                blend(x, y, (effA shl 24) or rgb, mode)
            }
        }
    }

    /**
     * AA stroke = AA fill of (outer rect minus inner rect). Hairline
     * (`strokeWidth <= 0`) renders as a 1-pixel-wide AA frame — for
     * axis-aligned rects this lights up the same pixel set as Skia's
     * `SkScan::AntiHairLineRgn` with matching coverage at half-integer edges.
     */
    private fun strokeRectAA(rect: SkRect, strokeWidth: Float, clip: SkIRect, color: SkColor, mode: SkBlendMode) {
        val w = if (strokeWidth <= 0f) 1f else strokeWidth
        val half = w * 0.5f
        val ol = rect.left - half
        val ot = rect.top - half
        val or = rect.right + half
        val ob = rect.bottom + half
        val il = rect.left + half
        val it = rect.top + half
        val ir = rect.right - half
        val ib = rect.bottom - half
        val innerEmpty = ir <= il || ib <= it
        if (or <= ol || ob <= ot) return
        val baseA = SkColorGetA(color)
        if (baseA == 0 && !modeAffectsZeroAlphaSrc(mode)) return
        val ix0 = floor(ol).coerceAtLeast(clip.left)
        val iy0 = floor(ot).coerceAtLeast(clip.top)
        val ix1 = ceil(or).coerceAtMost(clip.right)
        val iy1 = ceil(ob).coerceAtMost(clip.bottom)

        // Phase 6c — F16 + kSrcOver fast path (see [fillRectAA] for the
        // rationale).
        if (bitmap.colorType == org.skia.foundation.SkColorType.kRGBA_F16Norm &&
            mode == SkBlendMode.kSrcOver) {
            val src = FloatArray(4)
            colorToF16Premul(color, src)
            val sr = src[0]; val sg = src[1]; val sb = src[2]; val sa = src[3]
            for (y in iy0 until iy1) {
                val outerCY = covAxis(ot, ob, y)
                if (outerCY <= 0f) continue
                val innerCY = if (innerEmpty) 0f else covAxis(it, ib, y)
                for (x in ix0 until ix1) {
                    val outerCX = covAxis(ol, or, x)
                    if (outerCX <= 0f) continue
                    val innerCX = if (innerEmpty) 0f else covAxis(il, ir, x)
                    val cov = outerCX * outerCY - innerCX * innerCY
                    if (cov <= 0f) continue
                    blendF16Premul(x, y, sr * cov, sg * cov, sb * cov, sa * cov)
                }
            }
            return
        }

        val rgb = color and 0x00FFFFFF
        for (y in iy0 until iy1) {
            val outerCY = covAxis(ot, ob, y)
            if (outerCY <= 0f) continue
            val innerCY = if (innerEmpty) 0f else covAxis(it, ib, y)
            for (x in ix0 until ix1) {
                val outerCX = covAxis(ol, or, x)
                if (outerCX <= 0f) continue
                val innerCX = if (innerEmpty) 0f else covAxis(il, ir, x)
                val cov = outerCX * outerCY - innerCX * innerCY
                if (cov <= 0f) continue
                val effA = scaleAlpha(baseA, cov)
                if (effA == 0) continue
                blend(x, y, (effA shl 24) or rgb, mode)
            }
        }
    }

    /** Overlap in pixels between `[lo, hi)` and the unit cell `[pixel, pixel+1)`, clamped to `[0, 1]`. */
    private fun covAxis(lo: Float, hi: Float, pixel: Int): Float {
        val cov = minOf(hi, (pixel + 1).toFloat()) - maxOf(lo, pixel.toFloat())
        return when {
            cov >= 1f -> 1f
            cov <= 0f -> 0f
            else -> cov
        }
    }

    private fun scaleAlpha(baseA: Int, coverage: Float): Int {
        val a = (baseA * coverage + 0.5f).toInt()
        return when {
            a < 0 -> 0
            a > 255 -> 255
            else -> a
        }
    }

    // --------------------------------------------------------------------
    // Path scanline fill (Phase 3a).
    //
    // 4×4 supersampling for AA: for each device-space row `py`, run 4 sub-
    // scanlines at `py + (k + 0.5) / 4` for k in 0..3. At each sub-scanline:
    //
    //   - Find every edge whose device y-range contains the sub-scanline.
    //   - Compute their x crossing at that y.
    //   - Sort crossings left-to-right.
    //   - Walk the sorted crossings, maintaining the winding count, to
    //     enumerate "inside" spans.
    //   - For each span, accumulate sub-pixel x-samples (count of sub-pixel
    //     positions inside) into a row-wide coverage array.
    //
    // After the 4 sub-scanlines, `coverage[x]` lies in `[0, 16]`; the
    // pixel's effective alpha is `baseA * coverage / 16`.
    //
    // Half-open `[y0, y1)` interval prevents double-counting at shared
    // vertices. Even-odd uses `winding & 1` instead of `winding != 0`.
    // --------------------------------------------------------------------

    private data class Edge(
        val x0: Float, val y0: Float,
        val x1: Float, val y1: Float,
        val dir: Int,  // +1 if y0<y1 originally, -1 if y0>y1
    )

    /**
     * Walk the path's verb stream, transform every point to device space
     * via `(sx, sy, tx, ty)`, flatten Bézier curves into line segments,
     * and emit one `Edge` per non-horizontal segment.
     *
     * Curves are flattened in device space to a fixed 0.25-pixel chord
     * tolerance via recursive De Casteljau subdivision (adaptive). Conics
     * fall back to 32-step parametric flattening — they are rare and the
     * uniform stepping keeps the geometry simple.
     */
    private fun buildEdges(
        path: SkPath, ctm: SkMatrix,
    ): List<Edge> {
        // Cache the matrix scalars in locals to avoid property lookups inside
        // the hot loop. Mapping `(x, y)` to device space is `ctm.mapXY(x, y)`,
        // which expands to `(sx*x + kx*y + tx, ky*x + sy*y + ty)`. Skew terms
        // `kx` and `ky` are zero for axis-aligned matrices (the Phase 0–3 fast
        // path), but the JIT will fold them out when constant-zero anyway.
        val ax = ctm.sx; val bx = ctm.kx; val cx0 = ctm.tx
        val ay = ctm.ky; val by = ctm.sy; val cy0 = ctm.ty
        val out = ArrayList<Edge>(path.verbs.size)
        var px = 0f; var py = 0f          // current device-space point
        var cx = 0f; var cy = 0f          // contour-start device-space point
        var hasContour = false
        var coordIdx = 0
        var weightIdx = 0
        val coords = path.coords
        val weights = path.conicWeights
        for (verb in path.verbs) {
            when (verb) {
                SkPath.Verb.kMove -> {
                    if (hasContour) {
                        addEdge(out, px, py, cx, cy)  // implicit close
                    }
                    val sx0 = coords[coordIdx++]; val sy0 = coords[coordIdx++]
                    val x = ax * sx0 + bx * sy0 + cx0
                    val y = ay * sx0 + by * sy0 + cy0
                    px = x; py = y
                    cx = x; cy = y
                    hasContour = true
                }
                SkPath.Verb.kLine -> {
                    val sx0 = coords[coordIdx++]; val sy0 = coords[coordIdx++]
                    val x = ax * sx0 + bx * sy0 + cx0
                    val y = ay * sx0 + by * sy0 + cy0
                    addEdge(out, px, py, x, y)
                    px = x; py = y
                }
                SkPath.Verb.kQuad -> {
                    val sx1 = coords[coordIdx++]; val sy1 = coords[coordIdx++]
                    val sx2 = coords[coordIdx++]; val sy2 = coords[coordIdx++]
                    val x1 = ax * sx1 + bx * sy1 + cx0; val y1 = ay * sx1 + by * sy1 + cy0
                    val x2 = ax * sx2 + bx * sy2 + cx0; val y2 = ay * sx2 + by * sy2 + cy0
                    flattenQuad(out, px, py, x1, y1, x2, y2, depth = 0)
                    px = x2; py = y2
                }
                SkPath.Verb.kConic -> {
                    val sx1 = coords[coordIdx++]; val sy1 = coords[coordIdx++]
                    val sx2 = coords[coordIdx++]; val sy2 = coords[coordIdx++]
                    val w = weights[weightIdx++]
                    val x1 = ax * sx1 + bx * sy1 + cx0; val y1 = ay * sx1 + by * sy1 + cy0
                    val x2 = ax * sx2 + bx * sy2 + cx0; val y2 = ay * sx2 + by * sy2 + cy0
                    flattenConic(out, px, py, x1, y1, x2, y2, w)
                    px = x2; py = y2
                }
                SkPath.Verb.kCubic -> {
                    val sx1 = coords[coordIdx++]; val sy1 = coords[coordIdx++]
                    val sx2 = coords[coordIdx++]; val sy2 = coords[coordIdx++]
                    val sx3 = coords[coordIdx++]; val sy3 = coords[coordIdx++]
                    val x1 = ax * sx1 + bx * sy1 + cx0; val y1 = ay * sx1 + by * sy1 + cy0
                    val x2 = ax * sx2 + bx * sy2 + cx0; val y2 = ay * sx2 + by * sy2 + cy0
                    val x3 = ax * sx3 + bx * sy3 + cx0; val y3 = ay * sx3 + by * sy3 + cy0
                    flattenCubic(out, px, py, x1, y1, x2, y2, x3, y3, depth = 0)
                    px = x3; py = y3
                }
                SkPath.Verb.kClose -> {
                    if (hasContour) {
                        addEdge(out, px, py, cx, cy)
                        px = cx; py = cy
                        hasContour = false
                    }
                }
            }
        }
        if (hasContour) addEdge(out, px, py, cx, cy)
        return out
    }

    /** Add a non-horizontal device-space line segment as an oriented `Edge`. */
    private fun addEdge(
        out: MutableList<Edge>,
        dx0: Float, dy0: Float, dx1: Float, dy1: Float,
    ) {
        if (dy0 == dy1) return
        if (dy0 < dy1) out.add(Edge(dx0, dy0, dx1, dy1, +1))
        else out.add(Edge(dx1, dy1, dx0, dy0, -1))
    }

    /**
     * Recursive De Casteljau subdivision of a quadratic Bézier. Stops
     * when the control point is within [PATH_FLATNESS] of the chord, or
     * when [PATH_MAX_DEPTH] subdivisions have been performed (safety
     * bound — typically 4–6 levels suffice).
     */
    private fun flattenQuad(
        out: MutableList<Edge>,
        x0: Float, y0: Float,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        depth: Int,
    ) {
        if (depth >= PATH_MAX_DEPTH || quadIsFlat(x0, y0, x1, y1, x2, y2)) {
            addEdge(out, x0, y0, x2, y2)
            return
        }
        val m01x = (x0 + x1) * 0.5f; val m01y = (y0 + y1) * 0.5f
        val m12x = (x1 + x2) * 0.5f; val m12y = (y1 + y2) * 0.5f
        val mx = (m01x + m12x) * 0.5f; val my = (m01y + m12y) * 0.5f
        flattenQuad(out, x0, y0, m01x, m01y, mx, my, depth + 1)
        flattenQuad(out, mx, my, m12x, m12y, x2, y2, depth + 1)
    }

    private fun quadIsFlat(
        x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float,
    ): Boolean {
        // Twice the perpendicular distance × |chord| = |cross product|. Comparing
        // squared values avoids a sqrt per call.
        val dx = x2 - x0; val dy = y2 - y0
        val chord2 = dx * dx + dy * dy
        if (chord2 < 1e-12f) return true       // degenerate chord
        val cross = (x1 - x0) * dy - (y1 - y0) * dx
        // distance² = cross² / chord²; flat iff distance ≤ tolerance.
        return (cross * cross) <= PATH_FLATNESS_SQ * chord2
    }

    /**
     * Recursive De Casteljau subdivision of a cubic Bézier. Stops when
     * both control points are within [PATH_FLATNESS] of the chord, or at
     * the depth bound.
     */
    private fun flattenCubic(
        out: MutableList<Edge>,
        x0: Float, y0: Float,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        x3: Float, y3: Float,
        depth: Int,
    ) {
        if (depth >= PATH_MAX_DEPTH || cubicIsFlat(x0, y0, x1, y1, x2, y2, x3, y3)) {
            addEdge(out, x0, y0, x3, y3)
            return
        }
        val m01x = (x0 + x1) * 0.5f; val m01y = (y0 + y1) * 0.5f
        val m12x = (x1 + x2) * 0.5f; val m12y = (y1 + y2) * 0.5f
        val m23x = (x2 + x3) * 0.5f; val m23y = (y2 + y3) * 0.5f
        val m012x = (m01x + m12x) * 0.5f; val m012y = (m01y + m12y) * 0.5f
        val m123x = (m12x + m23x) * 0.5f; val m123y = (m12y + m23y) * 0.5f
        val mx = (m012x + m123x) * 0.5f; val my = (m012y + m123y) * 0.5f
        flattenCubic(out, x0, y0, m01x, m01y, m012x, m012y, mx, my, depth + 1)
        flattenCubic(out, mx, my, m123x, m123y, m23x, m23y, x3, y3, depth + 1)
    }

    private fun cubicIsFlat(
        x0: Float, y0: Float, x1: Float, y1: Float,
        x2: Float, y2: Float, x3: Float, y3: Float,
    ): Boolean {
        val dx = x3 - x0; val dy = y3 - y0
        val chord2 = dx * dx + dy * dy
        if (chord2 < 1e-12f) return true
        val c1 = (x1 - x0) * dy - (y1 - y0) * dx
        val c2 = (x2 - x0) * dy - (y2 - y0) * dx
        val maxCross2 = maxOf(c1 * c1, c2 * c2)
        return maxCross2 <= PATH_FLATNESS_SQ * chord2
    }

    /**
     * Conic flattening via uniform parametric stepping. Skia uses
     * adaptive splitting based on tangent error; for the path GMs we
     * touch in Phase 3b, 32 steps keep visible chord error well below
     * 0.25 px even at radii of a few hundred pixels.
     */
    private fun flattenConic(
        out: MutableList<Edge>,
        x0: Float, y0: Float, x1: Float, y1: Float,
        x2: Float, y2: Float, w: Float,
    ) {
        val n = CONIC_STEPS
        var prevX = x0; var prevY = y0
        for (k in 1..n) {
            val t = k.toFloat() / n
            val u = 1f - t
            val numW = u * u + 2f * u * t * w + t * t
            val numX = u * u * x0 + 2f * u * t * w * x1 + t * t * x2
            val numY = u * u * y0 + 2f * u * t * w * y1 + t * t * y2
            val px = numX / numW; val py = numY / numW
            addEdge(out, prevX, prevY, px, py)
            prevX = px; prevY = py
        }
    }

    private fun scanFillPath(
        edges: List<Edge>, fillType: SkPathFillType, clip: SkIRect,
        color: SkColor, baseA: Int, supers: Int,
        shader: SkShader?, mode: SkBlendMode,
    ) {
        val maxSamples = supers * supers
        var yMin = Float.POSITIVE_INFINITY
        var yMax = Float.NEGATIVE_INFINITY
        for (e in edges) {
            if (e.y0 < yMin) yMin = e.y0
            if (e.y1 > yMax) yMax = e.y1
        }
        val py0 = floor(yMin).coerceAtLeast(clip.top)
        val py1 = ceil(yMax).coerceAtMost(clip.bottom)
        if (py0 >= py1) return
        val rgb = color and 0x00FFFFFF
        val rowWidth = clip.right - clip.left
        if (rowWidth <= 0) return
        val coverage = IntArray(rowWidth)
        val crossX = FloatArray(edges.size)
        val crossDir = IntArray(edges.size)
        // Phase 6b/6c — when the bitmap is F16 *and* the blend mode is
        // SrcOver, the rasterizer takes a float-precision path: float
        // coverage, float-premul source (from a shader's `shadeRowF16` or
        // a once-per-draw `colorToF16Premul` for solid colours), direct
        // compositing into [SkBitmap.pixelsF16] without any byte-
        // quantization step. For other configurations we keep the 8-bit
        // path (and the existing F16 → byte → F16 round-trip in [blend]).
        val isF16 = bitmap.colorType == org.skia.foundation.SkColorType.kRGBA_F16Norm
        val useF16Path = isF16 && mode == SkBlendMode.kSrcOver
        val useF16ShaderPath = shader != null && useF16Path
        val useF16SolidPath = shader == null && useF16Path
        val shaderRow: IntArray? = if (shader != null && !useF16ShaderPath) IntArray(rowWidth) else null
        val shaderRowF16: FloatArray? = if (useF16ShaderPath) FloatArray(rowWidth * 4) else null
        val solidF16: FloatArray? = if (useF16SolidPath) FloatArray(4).also { colorToF16Premul(color, it) } else null
        val invMaxSamples = 1f / maxSamples.toFloat()

        for (py in py0 until py1) {
            // Reset coverage row.
            for (i in 0 until rowWidth) coverage[i] = 0
            for (k in 0 until supers) {
                val ySub = py + (k + 0.5f) / supers
                var n = 0
                for (e in edges) {
                    if (ySub >= e.y0 && ySub < e.y1) {
                        val t = (ySub - e.y0) / (e.y1 - e.y0)
                        crossX[n] = e.x0 + t * (e.x1 - e.x0)
                        crossDir[n] = e.dir
                        n++
                    }
                }
                if (n == 0) continue
                sortCrossings(crossX, crossDir, n)
                // Walk crossings, emitting spans where the fill rule is true.
                var winding = 0
                var inside = false
                var spanStart = 0f
                for (j in 0 until n) {
                    val before = inside
                    winding += crossDir[j]
                    inside = isInside(winding, fillType)
                    if (!before && inside) {
                        spanStart = crossX[j]
                    } else if (before && !inside) {
                        addSpanCoverage(spanStart, crossX[j], clip, supers, coverage)
                    }
                }
            }

            // Phase 6b: F16 shader path — pure float precision end-to-end.
            // shadeRowF16 returns premultiplied floats already in the
            // bitmap's working colour space, coverage modulates as a float
            // multiplier, and [blendF16Premul] composites without ever
            // touching an 8-bit byte. This is the only path that delivers
            // the precision F16 storage promises for gradient draws.
            if (shaderRowF16 != null) {
                shader!!.shadeRowF16(clip.left, py, rowWidth, shaderRowF16)
                // Phase 5g: paint.alpha modulates the shader output (Skia's
                // `shaderColor *= paint.alpha` semantics). Folded into the
                // coverage multiplier so the inner loop stays a single mul.
                val baseAF = baseA / 255f
                for (xOff in 0 until rowWidth) {
                    val samples = coverage[xOff]
                    if (samples == 0) continue
                    val covRaw = if (samples >= maxSamples) 1f else samples * invMaxSamples
                    val cov = covRaw * baseAF
                    val si = xOff * 4
                    val sa = shaderRowF16[si + 3] * cov
                    if (sa <= 0f) continue
                    val sr = shaderRowF16[si]     * cov
                    val sg = shaderRowF16[si + 1] * cov
                    val sb = shaderRowF16[si + 2] * cov
                    blendF16Premul(clip.left + xOff, py, sr, sg, sb, sa)
                }
            } else if (solidF16 != null) {
                // Phase 6c: F16 solid-colour path. Source premul-float is
                // computed once at draw setup (no per-pixel byte → float
                // unpack), coverage stays in float, the SrcOver blend lands
                // straight in [SkBitmap.pixelsF16].
                val sr0 = solidF16[0]; val sg0 = solidF16[1]
                val sb0 = solidF16[2]; val sa0 = solidF16[3]
                for (xOff in 0 until rowWidth) {
                    val samples = coverage[xOff]
                    if (samples == 0) continue
                    val cov = if (samples >= maxSamples) 1f else samples * invMaxSamples
                    blendF16Premul(clip.left + xOff, py, sr0 * cov, sg0 * cov, sb0 * cov, sa0 * cov)
                }
            } else if (shader != null && shaderRow != null) {
                // 8-bit shader path: same code as before (also covers the
                // F16-bitmap-with-non-SrcOver-mode case, where we accept the
                // round-trip rather than expand the F16 mode dispatch).
                // Phase 5g: paint.alpha is folded into the per-pixel
                // alpha modulation as `srcA * samples * baseA / (maxSamples * 255)`.
                shader.shadeRow(clip.left, py, rowWidth, shaderRow)
                for (xOff in 0 until rowWidth) {
                    val samples = coverage[xOff]
                    if (samples == 0) continue
                    val src = shaderRow[xOff]
                    val srcA = SkColorGetA(src)
                    if (srcA == 0) continue
                    val effA = if (samples >= maxSamples && baseA == 255) srcA
                        else (srcA * samples * baseA + (maxSamples * 255) / 2) / (maxSamples * 255)
                    if (effA == 0) continue
                    val srcRgb = src and 0x00FFFFFF
                    blend(clip.left + xOff, py, (effA shl 24) or srcRgb, mode)
                }
            } else {
                // Solid-colour path (Phase 1–4).
                for (xOff in 0 until rowWidth) {
                    val samples = coverage[xOff]
                    if (samples == 0) continue
                    val effA = if (samples >= maxSamples) baseA
                        else (baseA * samples + maxSamples / 2) / maxSamples
                    if (effA == 0) continue
                    blend(clip.left + xOff, py, (effA shl 24) or rgb, mode)
                }
            }
        }
    }

    private fun sortCrossings(xs: FloatArray, dirs: IntArray, n: Int) {
        // Insertion sort — `n` is typically small (tens) per scanline.
        for (i in 1 until n) {
            val x = xs[i]
            val d = dirs[i]
            var j = i - 1
            while (j >= 0 && xs[j] > x) {
                xs[j + 1] = xs[j]
                dirs[j + 1] = dirs[j]
                j--
            }
            xs[j + 1] = x
            dirs[j + 1] = d
        }
    }

    private fun addSpanCoverage(
        xL: Float, xR: Float, clip: SkIRect, supers: Int, coverage: IntArray,
    ) {
        if (xR <= xL) return
        val left = xL.coerceAtLeast(clip.left.toFloat())
        val right = xR.coerceAtMost(clip.right.toFloat())
        if (right <= left) return
        val pxStart = floor(left)
        val pxEnd = ceil(right)
        for (px in pxStart until pxEnd) {
            val cellL = maxOf(left, px.toFloat())
            val cellR = minOf(right, (px + 1).toFloat())
            val width = cellR - cellL
            if (width <= 0f) continue
            val samples = (width * supers + 0.5f).toInt().coerceIn(0, supers)
            coverage[px - clip.left] += samples
        }
    }

    private fun isInside(winding: Int, fillType: SkPathFillType): Boolean = when (fillType) {
        SkPathFillType.kWinding -> winding != 0
        SkPathFillType.kEvenOdd -> (winding and 1) != 0
        SkPathFillType.kInverseWinding,
        SkPathFillType.kInverseEvenOdd ->
            error("Inverse fill types are not implemented in Phase 3a")
    }

    // --------------------------------------------------------------------
    // Hairline / span helpers (Phase 1).
    // --------------------------------------------------------------------

    private fun drawHLine(x0: Int, x1: Int, y: Int, clip: SkIRect, color: SkColor, mode: SkBlendMode) {
        if (y < clip.top || y >= clip.bottom) return
        val l = x0.coerceAtLeast(clip.left)
        val r = x1.coerceAtMost(clip.right)
        for (x in l until r) blend(x, y, color, mode)
    }

    private fun drawVLine(x: Int, y0: Int, y1: Int, clip: SkIRect, color: SkColor, mode: SkBlendMode) {
        if (x < clip.left || x >= clip.right) return
        val t = y0.coerceAtLeast(clip.top)
        val b = y1.coerceAtMost(clip.bottom)
        for (y in t until b) blend(x, y, color, mode)
    }

    /**
     * Per-pixel blend dispatch. Combines:
     *  - the **F16 SrcOver fast path** (Phase 6a) when the bitmap is
     *    `kRGBA_F16Norm`, doing premultiplied-float compositing without
     *    any byte-quantization roundtrip,
     *  - the **8-bit SrcOver fast path** (Phase 1) when the bitmap is
     *    `kRGBA_8888` and `mode == kSrcOver`,
     *  - the **generic 9-mode dispatch** (Phase 6 entry) for all other
     *    blend modes.
     *
     * The generic dispatch operates on **non-premultiplied** ARGB inputs
     * and outputs; for F16 bitmaps it reads / writes via the colour-type-
     * aware `getPixel` / `setPixel` accessors, which convert to / from
     * premul float internally. That round-trip costs precision (~1 ulp
     * per channel at fractional alpha) but only for the non-SrcOver modes
     * the GMs in scope barely exercise; full F16 support for the rest of
     * the 9-mode slice is a Phase 6b task.
     */
    private fun blend(x: Int, y: Int, src: SkColor, mode: SkBlendMode) {
        // Phase 6a — F16 SrcOver fast path. Premul float compositing
        // straight in [SkBitmap.pixelsF16] with no byte-quantization
        // roundtrip on every blend.
        if (mode == SkBlendMode.kSrcOver &&
            bitmap.colorType == org.skia.foundation.SkColorType.kRGBA_F16Norm) {
            blendF16(x, y, src)
            return
        }
        // SrcOver fast path — same code as Phase 1 had — preserved bit-for-bit.
        if (mode == SkBlendMode.kSrcOver) {
            val sa = SkColorGetA(src)
            if (sa == 0xFF) {
                bitmap.setPixel(x, y, src)
                return
            }
            if (sa == 0) return
            val dst = bitmap.getPixel(x, y)
            val da = SkColorGetA(dst)
            val invSa = 255 - sa
            val outA = sa + (da * invSa + 127) / 255
            if (outA == 0) {
                bitmap.setPixel(x, y, 0)
                return
            }
            val sr = SkColorGetR(src); val sg = SkColorGetG(src); val sb = SkColorGetB(src)
            val dr = SkColorGetR(dst); val dg = SkColorGetG(dst); val db = SkColorGetB(dst)
            val outR = (sr * sa + dr * da * invSa / 255 + outA / 2) / outA
            val outG = (sg * sa + dg * da * invSa / 255 + outA / 2) / outA
            val outB = (sb * sa + db * da * invSa / 255 + outA / 2) / outA
            bitmap.setPixel(x, y, SkColorSetARGB(outA, outR, outG, outB))
            return
        }

        val dst = bitmap.getPixel(x, y)
        val out = blendPixel(src, dst, mode)
        bitmap.setPixel(x, y, out)
    }

    /**
     * Pure function (no bitmap I/O) computing `mode(src, dst)` in
     * non-premultiplied ARGB. Exposed at package level so unit tests can
     * verify blend formulas independently of the rasterizer.
     *
     * SrcOver is included for completeness; the rasterizer keeps an inlined
     * fast path in [blend]. All formulas operate on the full 8-bit ARGB
     * tuple `[a r g b] ∈ [0, 255]`.
     */
    internal fun blendPixel(src: SkColor, dst: SkColor, mode: SkBlendMode): SkColor = when (mode) {
        SkBlendMode.kClear -> 0
        SkBlendMode.kSrc -> src
        SkBlendMode.kDst -> dst
        SkBlendMode.kSrcOver -> blendSrcOver(src, dst)
        SkBlendMode.kDstOver -> blendDstOver(src, dst)
        SkBlendMode.kSrcIn -> blendSrcIn(src, dst)
        SkBlendMode.kDstIn -> blendDstIn(src, dst)
        SkBlendMode.kSrcOut -> blendSrcOut(src, dst)
        SkBlendMode.kDstOut -> blendDstOut(src, dst)
        SkBlendMode.kSrcATop -> blendSrcATop(src, dst)
        SkBlendMode.kDstATop -> blendDstATop(src, dst)
        SkBlendMode.kXor -> blendXor(src, dst)
        SkBlendMode.kPlus -> blendPlus(src, dst)
        SkBlendMode.kModulate -> blendModulate(src, dst)
        SkBlendMode.kScreen -> blendScreen(src, dst)
        SkBlendMode.kMultiply,
        SkBlendMode.kDarken,
        SkBlendMode.kLighten,
        SkBlendMode.kDifference,
        SkBlendMode.kExclusion,
        SkBlendMode.kOverlay,
        SkBlendMode.kHardLight,
        SkBlendMode.kColorDodge,
        SkBlendMode.kColorBurn,
        SkBlendMode.kSoftLight -> blendSeparable(src, dst, mode)
        SkBlendMode.kHue,
        SkBlendMode.kSaturation,
        SkBlendMode.kColor,
        SkBlendMode.kLuminosity -> blendHSL(src, dst, mode)
    }

    // ----- 9-mode slice implementations ---------------------------------
    //
    // All operate on non-premul ARGB tuples. Skia's reference uses premul,
    // so for modes that combine alpha and colour non-trivially (kSrcIn,
    // kDstIn) we mirror the premul formula exactly: pre-multiply src & dst
    // by their alphas, run the formula, post-divide by the result alpha.
    // For modes that don't multiply colour by the *other* operand's alpha
    // (kPlus, kModulate, kScreen) the non-premul formula is bit-equivalent
    // to the premul one when both inputs are fully opaque, with a sub-ulp
    // discrepancy at fractional alpha — same accuracy budget as our
    // existing kSrcOver path.
    // --------------------------------------------------------------------

    private fun blendSrcOver(src: SkColor, dst: SkColor): SkColor {
        val sa = SkColorGetA(src)
        if (sa == 0xFF) return src
        if (sa == 0) return dst
        val da = SkColorGetA(dst)
        val invSa = 255 - sa
        val outA = sa + (da * invSa + 127) / 255
        if (outA == 0) return 0
        val sr = SkColorGetR(src); val sg = SkColorGetG(src); val sb = SkColorGetB(src)
        val dr = SkColorGetR(dst); val dg = SkColorGetG(dst); val db = SkColorGetB(dst)
        val outR = (sr * sa + dr * da * invSa / 255 + outA / 2) / outA
        val outG = (sg * sa + dg * da * invSa / 255 + outA / 2) / outA
        val outB = (sb * sa + db * da * invSa / 255 + outA / 2) / outA
        return SkColorSetARGB(outA, outR, outG, outB)
    }

    /** `r = d + (1-da)*s` — symmetric to SrcOver with src/dst swapped. */
    private fun blendDstOver(src: SkColor, dst: SkColor): SkColor = blendSrcOver(dst, src)

    /**
     * `r = s * da` (premul). In non-premul, this becomes
     * `out.rgb = src.rgb` and `out.alpha = src.alpha * dst.alpha`. The dst
     * colour is replaced by the src colour, masked by dst's alpha.
     */
    private fun blendSrcIn(src: SkColor, dst: SkColor): SkColor {
        val sa = SkColorGetA(src)
        val da = SkColorGetA(dst)
        val outA = (sa * da + 127) / 255
        if (outA == 0) return 0
        return (outA shl 24) or (src and 0x00FFFFFF)
    }

    /**
     * `r = d * sa` (premul). In non-premul, dst colour is preserved and
     * its alpha is masked by src's alpha.
     */
    private fun blendDstIn(src: SkColor, dst: SkColor): SkColor {
        val sa = SkColorGetA(src)
        val da = SkColorGetA(dst)
        val outA = (sa * da + 127) / 255
        if (outA == 0) return 0
        return (outA shl 24) or (dst and 0x00FFFFFF)
    }

    // ----- Phase 6 Porter-Duff completion: kSrcOut, kDstOut, kSrcATop,
    // kDstATop, kXor. Like kSrcIn / kDstIn, alpha is the only thing that
    // really changes for the "Out" pair (RGB of the surviving operand is
    // preserved); ATop/Xor combine both operands' RGB and need a premul
    // round-trip to be exact.
    // --------------------------------------------------------------------

    /**
     * `r = s * (1 - da)` (premul). The visible part of src that lies
     * **outside** the dst. RGB of src is preserved; alpha = `sa*(1-da)`.
     */
    private fun blendSrcOut(src: SkColor, dst: SkColor): SkColor {
        val sa = SkColorGetA(src)
        val da = SkColorGetA(dst)
        val outA = (sa * (255 - da) + 127) / 255
        if (outA == 0) return 0
        return (outA shl 24) or (src and 0x00FFFFFF)
    }

    /**
     * `r = d * (1 - sa)` (premul). The visible part of dst that lies
     * **outside** the src. RGB of dst preserved; alpha = `da*(1-sa)`.
     */
    private fun blendDstOut(src: SkColor, dst: SkColor): SkColor {
        val sa = SkColorGetA(src)
        val da = SkColorGetA(dst)
        val outA = (da * (255 - sa) + 127) / 255
        if (outA == 0) return 0
        return (outA shl 24) or (dst and 0x00FFFFFF)
    }

    /**
     * `r = s * da + d * (1 - sa)` (premul). Source over dst, masked by
     * dst's alpha — paint *inside* the existing dst silhouette only.
     * Result alpha is exactly [da] (the source can never extend dst's
     * silhouette under ATop), and RGB is `lerp(dst, src, sa/255)` — the
     * standard "ATop" formula collapses cleanly when alpha is factored
     * out symbolically.
     */
    private fun blendSrcATop(src: SkColor, dst: SkColor): SkColor {
        val sa = SkColorGetA(src)
        val da = SkColorGetA(dst)
        if (da == 0) return 0
        if (sa == 0) return dst
        if (sa == 0xFF) {
            // Source replaces dst RGB inside dst's silhouette. Alpha = da.
            return (da shl 24) or (src and 0x00FFFFFF)
        }
        val sr = SkColorGetR(src); val sg = SkColorGetG(src); val sb = SkColorGetB(src)
        val dr = SkColorGetR(dst); val dg = SkColorGetG(dst); val db = SkColorGetB(dst)
        val invSa = 255 - sa
        val outR = ((sr * sa + dr * invSa + 127) / 255).coerceIn(0, 255)
        val outG = ((sg * sa + dg * invSa + 127) / 255).coerceIn(0, 255)
        val outB = ((sb * sa + db * invSa + 127) / 255).coerceIn(0, 255)
        return SkColorSetARGB(da, outR, outG, outB)
    }

    /** `r = d * sa + s * (1 - da)` (premul). Symmetric of [blendSrcATop]. */
    private fun blendDstATop(src: SkColor, dst: SkColor): SkColor = blendSrcATop(dst, src)

    /**
     * `r = s * (1 - da) + d * (1 - sa)` (premul). The "exclusive or" of
     * coverage — pixels covered by exactly one of src / dst. Both alphas
     * non-trivial and the RGB combine, so we do the full premul round-
     * trip to stay exact at fractional alpha.
     */
    private fun blendXor(src: SkColor, dst: SkColor): SkColor {
        val sa = SkColorGetA(src); val da = SkColorGetA(dst)
        if (sa == 0) return blendDstOut(src, dst)  // simplifies to dst*(1-sa) when sa=0 ⇒ dst
        if (da == 0) return blendSrcOut(src, dst)  // mirrored
        val sr = SkColorGetR(src); val sg = SkColorGetG(src); val sb = SkColorGetB(src)
        val dr = SkColorGetR(dst); val dg = SkColorGetG(dst); val db = SkColorGetB(dst)
        val invSa = 255 - sa
        val invDa = 255 - da
        // outA = sa*(1-da)/255 + da*(1-sa)/255  (premul)
        val outA = (sa * invDa + da * invSa + 127) / 255
        if (outA == 0) return 0
        // outRgb_premul = sr*sa*(1-da)/255 + dr*da*(1-sa)/255  (each term
        // is the premul colour weighted by the *other* operand's complementary
        // alpha). Un-premul by outA at the end.
        val divisor = outA * 255
        val outR = ((sr * sa * invDa + dr * da * invSa + divisor / 2) / divisor).coerceIn(0, 255)
        val outG = ((sg * sa * invDa + dg * da * invSa + divisor / 2) / divisor).coerceIn(0, 255)
        val outB = ((sb * sa * invDa + db * da * invSa + divisor / 2) / divisor).coerceIn(0, 255)
        return SkColorSetARGB(outA, outR, outG, outB)
    }

    /**
     * `r = min(s + d, 1)` (premul). Used by `ScaledRectsGM` (deferred
     * since Phase 2). Saturating channel-wise add of premultiplied colours.
     *
     * For non-premul inputs this is equivalent when both are opaque (the
     * GM case). For fractional alpha we compute the premul sum then
     * un-premul; alpha is a separate saturating add.
     */
    private fun blendPlus(src: SkColor, dst: SkColor): SkColor {
        val sa = SkColorGetA(src); val da = SkColorGetA(dst)
        if (sa == 0) return dst
        if (da == 0) return src
        if (sa == 0xFF && da == 0xFF) {
            // Common ScaledRects-style case: opaque + opaque, no alpha math.
            val r = (SkColorGetR(src) + SkColorGetR(dst)).coerceAtMost(0xFF)
            val g = (SkColorGetG(src) + SkColorGetG(dst)).coerceAtMost(0xFF)
            val b = (SkColorGetB(src) + SkColorGetB(dst)).coerceAtMost(0xFF)
            return SkColorSetARGB(0xFF, r, g, b)
        }
        val outA = (sa + da).coerceAtMost(0xFF)
        // Fall back to premul math at fractional alpha so the result is
        // exact w.r.t. Skia's pipeline.
        val sr = SkColorGetR(src) * sa; val sg = SkColorGetG(src) * sa; val sb = SkColorGetB(src) * sa
        val dr = SkColorGetR(dst) * da; val dg = SkColorGetG(dst) * da; val db = SkColorGetB(dst) * da
        val pr = (sr + dr).coerceAtMost(255 * 255)
        val pg = (sg + dg).coerceAtMost(255 * 255)
        val pb = (sb + db).coerceAtMost(255 * 255)
        // Un-premultiply by outA. With outA > 0 this is safe.
        val outR = (pr + outA / 2) / outA
        val outG = (pg + outA / 2) / outA
        val outB = (pb + outA / 2) / outA
        return SkColorSetARGB(outA, outR.coerceIn(0, 0xFF), outG.coerceIn(0, 0xFF), outB.coerceIn(0, 0xFF))
    }

    /**
     * `r = s * d` (premul). Skia comment: per-component multiply of
     * **premultiplied** colours. In non-premul ARGB this becomes
     * `out.rgb = (s.rgb * sa) * (d.rgb * da) / out.alpha` with
     * `out.alpha = sa * da`. Equivalent to `out.rgb = s.rgb * d.rgb` only
     * when both alphas are opaque — otherwise dst's alpha leaks into the
     * colour. We keep the formula explicit so the test suite can pin both
     * cases.
     */
    private fun blendModulate(src: SkColor, dst: SkColor): SkColor {
        val sa = SkColorGetA(src); val da = SkColorGetA(dst)
        if (sa == 0 || da == 0) return 0
        if (sa == 0xFF && da == 0xFF) {
            val r = (SkColorGetR(src) * SkColorGetR(dst) + 127) / 255
            val g = (SkColorGetG(src) * SkColorGetG(dst) + 127) / 255
            val b = (SkColorGetB(src) * SkColorGetB(dst) + 127) / 255
            return SkColorSetARGB(0xFF, r, g, b)
        }
        val outA = (sa * da + 127) / 255
        if (outA == 0) return 0
        // Premul colour: (sr * sa) * (dr * da) / 255² gives the premul
        // result; un-premul by outA.
        val sr = SkColorGetR(src); val sg = SkColorGetG(src); val sb = SkColorGetB(src)
        val dr = SkColorGetR(dst); val dg = SkColorGetG(dst); val db = SkColorGetB(dst)
        val pr = sr * sa * dr * da
        val pg = sg * sa * dg * da
        val pb = sb * sa * db * da
        // Divide by 255² then un-premul by outA. Combine into a single
        // (outA * 255²) divisor to keep it integer.
        val divisor = outA * 255 * 255
        val outR = ((pr + divisor / 2) / divisor).coerceIn(0, 0xFF)
        val outG = ((pg + divisor / 2) / divisor).coerceIn(0, 0xFF)
        val outB = ((pb + divisor / 2) / divisor).coerceIn(0, 0xFF)
        return SkColorSetARGB(outA, outR, outG, outB)
    }

    /**
     * `r = s + d - s*d` (premul). With both operands opaque this is the
     * non-premul Screen formula directly. Alpha follows SrcOver.
     */
    private fun blendScreen(src: SkColor, dst: SkColor): SkColor {
        val sa = SkColorGetA(src); val da = SkColorGetA(dst)
        if (sa == 0) return dst
        if (da == 0) return src
        val outA = sa + da - (sa * da + 127) / 255
        if (outA == 0) return 0
        if (sa == 0xFF && da == 0xFF) {
            val sr = SkColorGetR(src); val sg = SkColorGetG(src); val sb = SkColorGetB(src)
            val dr = SkColorGetR(dst); val dg = SkColorGetG(dst); val db = SkColorGetB(dst)
            val r = sr + dr - (sr * dr + 127) / 255
            val g = sg + dg - (sg * dg + 127) / 255
            val b = sb + db - (sb * db + 127) / 255
            return SkColorSetARGB(0xFF, r, g, b)
        }
        // Premul math at fractional alpha: outRGB = sr*sa + dr*da - (sr*sa*dr*da)/255,
        // then un-premul by outA.
        val sr = SkColorGetR(src) * sa; val sg = SkColorGetG(src) * sa; val sb = SkColorGetB(src) * sa
        val dr = SkColorGetR(dst) * da; val dg = SkColorGetG(dst) * da; val db = SkColorGetB(dst) * da
        val pr = sr + dr - (sr * dr + 127 * 255) / (255 * 255)
        val pg = sg + dg - (sg * dg + 127 * 255) / (255 * 255)
        val pb = sb + db - (sb * db + 127 * 255) / (255 * 255)
        val outR = ((pr + outA / 2) / outA).coerceIn(0, 0xFF)
        val outG = ((pg + outA / 2) / outA).coerceIn(0, 0xFF)
        val outB = ((pb + outA / 2) / outA).coerceIn(0, 0xFF)
        return SkColorSetARGB(outA, outR, outG, outB)
    }

    // --------------------------------------------------------------------
    // Phase 6 separable modes (simple): kMultiply, kDarken, kLighten,
    // kDifference, kExclusion. Formulas operate in **premultiplied
    // float** [0, 1] — Skia's reference compositor. Each takes (src,
    // dst) as 8-bit non-premul SkColor, premultiplies internally,
    // computes the per-channel result in float, and stores back as 8-bit
    // non-premul. Output alpha = SrcOver alpha = `sa + da*(1-sa)` for
    // every separable mode.
    //
    // Going through float (instead of integer fixed-point as the Phase 6
    // entry / Porter-Duff completion modes do) keeps the formulas
    // readable and avoids accuracy traps on multi-multiplication chains
    // like kMultiply. The 8-bit accuracy budget (≤ 1 ulp at fractional
    // alpha) is preserved.
    // --------------------------------------------------------------------

    private fun blendSeparable(src: SkColor, dst: SkColor, mode: SkBlendMode): SkColor {
        val sa = SkColorGetA(src) / 255f
        val da = SkColorGetA(dst) / 255f
        val sr = SkColorGetR(src) / 255f * sa
        val sg = SkColorGetG(src) / 255f * sa
        val sb = SkColorGetB(src) / 255f * sa
        val dr = SkColorGetR(dst) / 255f * da
        val dg = SkColorGetG(dst) / 255f * da
        val db = SkColorGetB(dst) / 255f * da

        // SrcOver alpha — shared by every separable mode in Skia.
        val oa = sa + da * (1f - sa)
        if (oa <= 0f) return 0

        // Apply the per-mode per-channel formula to (sX, dX, sa, da).
        val orPm = sepChannel(sr, dr, sa, da, mode)
        val ogPm = sepChannel(sg, dg, sa, da, mode)
        val obPm = sepChannel(sb, db, sa, da, mode)

        // Un-premultiply by oa and quantize.
        val invOa = 1f / oa
        val outA = (oa * 255f + 0.5f).toInt().coerceIn(0, 255)
        val outR = (orPm * invOa * 255f + 0.5f).toInt().coerceIn(0, 255)
        val outG = (ogPm * invOa * 255f + 0.5f).toInt().coerceIn(0, 255)
        val outB = (obPm * invOa * 255f + 0.5f).toInt().coerceIn(0, 255)
        return SkColorSetARGB(outA, outR, outG, outB)
    }

    /**
     * Per-channel separable formula in premul-float `[0, 1]`. Inputs `s`
     * and `d` are already premultiplied (i.e. `unpremul · alpha`); the
     * caller's `sa` / `da` give the operand alphas needed for the modes
     * that subtract `s*da` or `d*sa`.
     *
     * Phase 6 covers all 15 separable modes upstream: 5 simple
     * (kMultiply / kDarken / kLighten / kDifference / kExclusion) and
     * 5 complex (kOverlay / kHardLight / kColorDodge / kColorBurn /
     * kSoftLight). Each formula is the W3C Compositing Level 1 blend
     * function `B(Cb, Cs)` rewritten in premul space, plus the
     * SrcOver-style `(1-sa)*dc + (1-da)*sc` carrier terms.
     */
    private fun sepChannel(s: Float, d: Float, sa: Float, da: Float, mode: SkBlendMode): Float = when (mode) {
        // `rc = (1-sa)*dc + (1-da)*sc + sc*dc` — standard premul Multiply.
        // Note that this is **not** simply `s*d`: that variant ("Modulate"
        // in Skia) discards the (1-sa)*dc and (1-da)*sc terms and is
        // already covered by [blendModulate].
        SkBlendMode.kMultiply -> (1f - sa) * d + (1f - da) * s + s * d
        // `rc = sc + dc - max(sc*da, dc*sa)`. Picks the darker of the two
        // operand colours, weighted by the other's alpha.
        SkBlendMode.kDarken -> s + d - maxOf(s * da, d * sa)
        // `rc = sc + dc - min(sc*da, dc*sa)`.
        SkBlendMode.kLighten -> s + d - minOf(s * da, d * sa)
        // `rc = sc + dc - 2 * min(sc*da, dc*sa)`. Symmetric absolute
        // difference of the two operands' colours; equal colours cancel.
        SkBlendMode.kDifference -> s + d - 2f * minOf(s * da, d * sa)
        // `rc = sc + dc - 2*sc*dc`. Like Difference but doesn't depend on
        // alpha; identical to Screen at sc + dc small, but symmetric.
        SkBlendMode.kExclusion -> s + d - 2f * s * d
        // HardLight: `B(Cb, Cs) = if Cs ≤ 0.5: 2*Cb*Cs else 1-2*(1-Cb)*(1-Cs)`,
        // expressed in premul as a conditional on `2*sc ≤ sa`. Picks
        // either Multiply (when src is dark) or Screen (when src is light)
        // — equivalent to shining a hard spotlight from `s` onto `d`.
        SkBlendMode.kHardLight -> hardLightChannel(s, d, sa, da)
        // Overlay = HardLight with operands swapped (W3C: `Overlay(Cb, Cs)
        // = HardLight(Cs, Cb)`). The conditional moves to `2*dc ≤ da`,
        // but the body terms stay the same — Overlay applies the harder
        // contrast based on the **dst's** brightness.
        SkBlendMode.kOverlay -> hardLightChannel(d, s, da, sa)
        // ColorDodge: brightens dst toward white based on src.
        SkBlendMode.kColorDodge -> colorDodgeChannel(s, d, sa, da)
        // ColorBurn: darkens dst toward black based on src.
        SkBlendMode.kColorBurn -> colorBurnChannel(s, d, sa, da)
        // SoftLight: gentler version of HardLight; never produces fully
        // saturated output. Uses the W3C-canonical Pegtop formulation.
        SkBlendMode.kSoftLight -> softLightChannel(s, d, sa, da)
        else -> error("sepChannel called with non-separable mode: $mode")
    }

    /**
     * HardLight per-channel in premul space:
     * ```
     * B = if 2*sc ≤ sa: 2*sc*dc
     *     else        : sa*da - 2*(da-dc)*(sa-sc)
     * rc = (1-sa)*dc + (1-da)*sc + B
     * ```
     */
    private fun hardLightChannel(s: Float, d: Float, sa: Float, da: Float): Float {
        val carrier = (1f - sa) * d + (1f - da) * s
        val body = if (2f * s <= sa) {
            2f * s * d
        } else {
            sa * da - 2f * (da - d) * (sa - s)
        }
        return carrier + body
    }

    /**
     * ColorDodge per-channel in premul space. Skia's branch structure
     * (matches `SkBlendMode_RasterPipeline.cpp::colorDodge`):
     *
     * ```
     * if dc == 0:                 rc = sc * (1 - da)             // black dst stays black
     * elif sc ≥ sa:               rc = sa * da + sc * (1-da) + dc * (1-sa)
     * else:                       rc = min(da, dc * sa / (sa - sc)) * sa
     *                                  + sc * (1-da) + dc * (1-sa)
     * ```
     *
     * The last branch can produce divide-by-near-zero when `sa - sc` is
     * tiny but non-zero; we keep the literal Skia formulation since the
     * `min(da, …)` clamp absorbs the overflow.
     */
    private fun colorDodgeChannel(s: Float, d: Float, sa: Float, da: Float): Float {
        if (d <= 0f) return s * (1f - da)
        if (s >= sa) return sa * da + s * (1f - da) + d * (1f - sa)
        val ratio = d * sa / (sa - s)
        val n = if (ratio < da) ratio else da
        return n * sa + s * (1f - da) + d * (1f - sa)
    }

    /**
     * ColorBurn per-channel in premul space. Mirror of ColorDodge — burn
     * darkens dst toward black based on src:
     *
     * ```
     * if dc ≥ da:                 rc = sa * da + sc * (1-da) + dc * (1-sa)
     * elif sc ≤ 0:                rc = dc * (1 - sa)
     * else:                       rc = (da - min(da, (da-dc) * sa / sc)) * sa
     *                                  + sc * (1-da) + dc * (1-sa)
     * ```
     */
    private fun colorBurnChannel(s: Float, d: Float, sa: Float, da: Float): Float {
        if (d >= da) return sa * da + s * (1f - da) + d * (1f - sa)
        if (s <= 0f) return d * (1f - sa)
        val ratio = (da - d) * sa / s
        val n = if (ratio < da) ratio else da
        return (da - n) * sa + s * (1f - da) + d * (1f - sa)
    }

    /**
     * SoftLight per-channel in premul space. Direct port of Skia's
     * raster-pipeline implementation (`SkRasterPipeline_opts.h`):
     *
     * ```
     * m  = (da > 0) ? dc / da : 0           // unpremul Cb
     * s2 = 2 * sc                           // premul 2*Cs
     * if 2*sc ≤ sa:                          // dark src
     *     B = dc * (sa + (s2 - sa) * (1 - m))
     * else if 4*dc ≤ da:                     // light src + dark dst
     *     B = dc*sa + da * (s2 - sa) * ((4*dc/da) * (4*dc/da + 1) * (4*dc/da - 1) + 7*dc/da - 1)
     * else:                                  // light src + bright dst
     *     B = dc*sa + da * (s2 - sa) * (sqrt(dc/da) - dc/da)
     * rc = sc * (1 - da) + dc * (1 - sa) + B
     * ```
     *
     * The middle branch's `4*dc/da * (4*dc/da + 1) * (4*dc/da - 1) + 7*dc/da - 1`
     * is a cubic polynomial in `m = dc/da` that approximates `m^3 - 4*m`-ish
     * shape used by the W3C Pegtop softlight. We keep it as written.
     */
    private fun softLightChannel(s: Float, d: Float, sa: Float, da: Float): Float {
        val carrier = s * (1f - da) + d * (1f - sa)
        if (2f * s <= sa) {
            // Dark src: pull dst toward black proportional to (sa - 2*sc).
            val m = if (da > 0f) d / da else 0f
            val body = d * (sa + (2f * s - sa) * (1f - m))
            return carrier + body
        }
        // Light src — two sub-branches based on dst brightness.
        val m = if (da > 0f) d / da else 0f
        val correction = if (4f * d <= da) {
            // Dark dst: cubic approximation.
            val mm = 4f * m  // 4 * dc/da
            mm * (mm + 1f) * (mm - 1f) + 7f * m - 1f
        } else {
            // Bright dst: sqrt-based.
            val sqm = kotlin.math.sqrt(m)
            sqm - m
        }
        val body = d * sa + da * (2f * s - sa) * correction
        return carrier + body
    }

    // --------------------------------------------------------------------
    // Phase 6 HSL: kHue, kSaturation, kColor, kLuminosity. These modes
    // operate on the whole RGB tuple at once (not per-channel), so they
    // need their own dispatcher that doesn't fit the [sepChannel] shape.
    //
    // Formulas (W3C Compositing Level 1, in non-premul):
    //   Hue       (Cs, Cb) = SetLum(SetSat(Cs, Sat(Cb)), Lum(Cb))
    //   Saturation(Cs, Cb) = SetLum(SetSat(Cb, Sat(Cs)), Lum(Cb))
    //   Color     (Cs, Cb) = SetLum(Cs, Lum(Cb))
    //   Luminosity(Cs, Cb) = SetLum(Cb, Lum(Cs))
    //
    // Skia's premul implementation scales the operands by the *other*
    // operand's alpha so both work in `[0, sa*da]`, applies the formula,
    // and uses `sa*da` as the upper clip bound. The result is the B body
    // in premul space; carrier = `sc*(1-da) + dc*(1-sa)` is added after.
    // --------------------------------------------------------------------

    private fun blendHSL(src: SkColor, dst: SkColor, mode: SkBlendMode): SkColor {
        val sa = SkColorGetA(src) / 255f
        val da = SkColorGetA(dst) / 255f
        val sr = SkColorGetR(src) / 255f * sa
        val sg = SkColorGetG(src) / 255f * sa
        val sb = SkColorGetB(src) / 255f * sa
        val dr = SkColorGetR(dst) / 255f * da
        val dg = SkColorGetG(dst) / 255f * da
        val db = SkColorGetB(dst) / 255f * da

        val oa = sa + da * (1f - sa)
        if (oa <= 0f) return 0

        // Scale src by da and dst by sa so both live in `[0, sa*da]`.
        val a = sa * da
        val srA = sr * da; val sgA = sg * da; val sbA = sb * da
        val drA = dr * sa; val dgA = dg * sa; val dbA = db * sa

        // Compute the B body for each HSL mode in `[0, sa*da]` space.
        val body = FloatArray(3)
        when (mode) {
            SkBlendMode.kHue -> {
                // SetLum(SetSat(Cs', Sat(Cb')), a, Lum(Cb')).
                body[0] = srA; body[1] = sgA; body[2] = sbA
                setSat(body, sat3(drA, dgA, dbA))
                setLum(body, a, lum3(drA, dgA, dbA))
            }
            SkBlendMode.kSaturation -> {
                // SetLum(SetSat(Cb', Sat(Cs')), a, Lum(Cb')).
                body[0] = drA; body[1] = dgA; body[2] = dbA
                setSat(body, sat3(srA, sgA, sbA))
                setLum(body, a, lum3(drA, dgA, dbA))
            }
            SkBlendMode.kColor -> {
                // SetLum(Cs', a, Lum(Cb')).
                body[0] = srA; body[1] = sgA; body[2] = sbA
                setLum(body, a, lum3(drA, dgA, dbA))
            }
            SkBlendMode.kLuminosity -> {
                // SetLum(Cb', a, Lum(Cs')).
                body[0] = drA; body[1] = dgA; body[2] = dbA
                setLum(body, a, lum3(srA, sgA, sbA))
            }
            else -> error("blendHSL called with non-HSL mode: $mode")
        }

        // Add the SrcOver-style carrier: oc = sc*(1-da) + dc*(1-sa) + B.
        val orPm = sr * (1f - da) + dr * (1f - sa) + body[0]
        val ogPm = sg * (1f - da) + dg * (1f - sa) + body[1]
        val obPm = sb * (1f - da) + db * (1f - sa) + body[2]

        val invOa = 1f / oa
        val outA = (oa * 255f + 0.5f).toInt().coerceIn(0, 255)
        val outR = (orPm * invOa * 255f + 0.5f).toInt().coerceIn(0, 255)
        val outG = (ogPm * invOa * 255f + 0.5f).toInt().coerceIn(0, 255)
        val outB = (obPm * invOa * 255f + 0.5f).toInt().coerceIn(0, 255)
        return SkColorSetARGB(outA, outR, outG, outB)
    }

    /** Luminance: `0.3*R + 0.59*G + 0.11*B` (Skia's coefficients). */
    private fun lum3(r: Float, g: Float, b: Float): Float =
        r * 0.3f + g * 0.59f + b * 0.11f

    /** Saturation: `max(R, G, B) - min(R, G, B)` (channel-spread). */
    private fun sat3(r: Float, g: Float, b: Float): Float =
        maxOf(r, maxOf(g, b)) - minOf(r, minOf(g, b))

    /**
     * In-place: shift `rgb`'s luminance to [newLum] (uniform additive shift),
     * then [clipColor] back into `[0, alpha]` while preserving the new
     * luminance. Used by every HSL mode to lock the result's luminance to
     * either dst's (Hue/Saturation/Color) or src's (Luminosity).
     */
    private fun setLum(rgb: FloatArray, alpha: Float, newLum: Float) {
        val diff = newLum - lum3(rgb[0], rgb[1], rgb[2])
        rgb[0] += diff
        rgb[1] += diff
        rgb[2] += diff
        clipColor(rgb, alpha)
    }

    /**
     * In-place: scale `rgb`'s spread to [newSat] while preserving channel
     * order. The smallest channel collapses to 0, the largest becomes
     * `newSat`, and the middle scales proportionally — same behaviour as
     * the W3C `SetSat` algorithm without the explicit min/mid/max sort
     * (since the same factor applied to `(value - min)` gives the right
     * answer regardless of ordering).
     */
    private fun setSat(rgb: FloatArray, newSat: Float) {
        val r = rgb[0]; val g = rgb[1]; val b = rgb[2]
        val mn = minOf(r, minOf(g, b))
        val mx = maxOf(r, maxOf(g, b))
        val s = mx - mn
        if (s > 0f) {
            val factor = newSat / s
            rgb[0] = (r - mn) * factor
            rgb[1] = (g - mn) * factor
            rgb[2] = (b - mn) * factor
        } else {
            rgb[0] = 0f; rgb[1] = 0f; rgb[2] = 0f
        }
    }

    /**
     * In-place: clip `rgb` into `[0, alpha]` while preserving its
     * luminance. If a channel underflows to negative, we pull all three
     * toward `lum`; if a channel overflows past `alpha`, we push all
     * three toward `lum`. Mirrors Skia's `SkBlendMode_RasterPipeline.cpp::clipColor`.
     */
    private fun clipColor(rgb: FloatArray, alpha: Float) {
        val l = lum3(rgb[0], rgb[1], rgb[2])
        var r = rgb[0]; var g = rgb[1]; var b = rgb[2]
        val mn = minOf(r, minOf(g, b))
        val mx = maxOf(r, maxOf(g, b))
        if (mn < 0f) {
            val denom = l - mn
            val factor = if (denom > 0f) l / denom else 0f
            r = l + (r - l) * factor
            g = l + (g - l) * factor
            b = l + (b - l) * factor
        }
        if (mx > alpha) {
            val denom = mx - l
            val factor = if (denom > 0f) (alpha - l) / denom else 0f
            r = l + (r - l) * factor
            g = l + (g - l) * factor
            b = l + (b - l) * factor
        }
        rgb[0] = r; rgb[1] = g; rgb[2] = b
    }

    /**
     * Source-Over compositing in **premultiplied float** (`F16Norm` path).
     * Both src and dst are kept in `[0, 1]` premultiplied space, so the
     * formula reduces to `out = src + dst * (1 − srcA)` per channel — no
     * unpremul / repremul roundtrip and no byte quantization until the
     * pixel ultimately leaves the bitmap (PNG output).
     */
    private fun blendF16(x: Int, y: Int, src: SkColor) {
        val sa = SkColorGetA(src)
        if (sa == 0) return
        val saF = sa / 255f
        val srF = SkColorGetR(src) / 255f * saF
        val sgF = SkColorGetG(src) / 255f * saF
        val sbF = SkColorGetB(src) / 255f * saF

        val pixels = bitmap.pixelsF16
        val i = (y * bitmap.width + x) * 4
        val invSa = 1f - saF
        val outR = srF + pixels[i] * invSa
        val outG = sgF + pixels[i + 1] * invSa
        val outB = sbF + pixels[i + 2] * invSa
        val outA = saF + pixels[i + 3] * invSa
        pixels[i] = outR
        pixels[i + 1] = outG
        pixels[i + 2] = outB
        pixels[i + 3] = outA
    }

    /**
     * Phase 6b — pure premul-float SrcOver. The src tuple is already in
     * `[0, 1]` premultiplied (coverage already folded in by the caller),
     * matching the bitmap's storage convention exactly. No byte conversion
     * happens anywhere in this function — the only multiplications are the
     * two `[0, 1]` premul lerps.
     */
    private fun blendF16Premul(x: Int, y: Int, sr: Float, sg: Float, sb: Float, sa: Float) {
        if (sa <= 0f) return
        val pixels = bitmap.pixelsF16
        val i = (y * bitmap.width + x) * 4
        val invSa = 1f - sa
        pixels[i]     = sr + pixels[i]     * invSa
        pixels[i + 1] = sg + pixels[i + 1] * invSa
        pixels[i + 2] = sb + pixels[i + 2] * invSa
        pixels[i + 3] = sa + pixels[i + 3] * invSa
    }
}
