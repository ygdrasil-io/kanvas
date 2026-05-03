package org.skia.core

import org.skia.foundation.SkBitmap
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
import org.skia.foundation.SkStroker
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkIRect
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
        val maxX = image.width - 1
        val maxY = image.height - 1

        // [SkImage] currently has no `colorSpace` property, so by convention
        // image pixels are sRGB-encoded (same as SkColor). The device's
        // [xformSteps] (sRGB → bitmap.colorSpace) is reused here: we
        // pre-convert the entire image into device-space pixels once, then
        // sample from that buffer in the inner loop. Cheap because images
        // tend to have far fewer pixels than the dst rect.
        val devPixels = imagePixelsInDeviceColorSpace(image)

        when (sampling.filter) {
            SkFilterMode.kNearest -> {
                for (py in iy0 until iy1) {
                    val srcYc = src.top + (py + 0.5f - devDst.top) * scaleY
                    val iy = floor(srcYc).coerceIn(0, maxY)
                    for (px in ix0 until ix1) {
                        val srcXc = src.left + (px + 0.5f - devDst.left) * scaleX
                        val ix = floor(srcXc).coerceIn(0, maxX)
                        val sample = applyAlpha(devPixels[iy * image.width + ix], paintAlpha)
                        if (sample ushr 24 == 0) continue
                        blend(px, py, sample)
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
                        if (sample ushr 24 == 0) continue
                        blend(px, py, sample)
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
        sx: Float, sy: Float, tx: Float, ty: Float,
        clip: SkIRect, paint: SkPaint,
    ) {
        if (path.isEmpty()) return
        val color = transformPaintColor(paint.color)
        val baseA = SkColorGetA(color)
        if (baseA == 0) return
        val supers = if (paint.isAntiAlias) 4 else 1

        when (paint.style) {
            SkPaint.Style.kFill_Style ->
                fillPath(path, sx, sy, tx, ty, clip, color, baseA, supers)
            SkPaint.Style.kStroke_Style -> {
                val outline = SkStroker.fromPaint(paint).stroke(path)
                if (outline.isEmpty()) return
                fillPath(outline, sx, sy, tx, ty, clip, color, baseA, supers)
            }
            SkPaint.Style.kStrokeAndFill_Style -> {
                fillPath(path, sx, sy, tx, ty, clip, color, baseA, supers)
                val outline = SkStroker.fromPaint(paint).stroke(path)
                if (outline.isEmpty()) return
                fillPath(outline, sx, sy, tx, ty, clip, color, baseA, supers)
            }
        }
    }

    private fun fillPath(
        path: SkPath, sx: Float, sy: Float, tx: Float, ty: Float,
        clip: SkIRect, color: SkColor, baseA: Int, supers: Int,
    ) {
        val edges = buildEdges(path, sx, sy, tx, ty)
        if (edges.isEmpty()) return
        scanFillPath(edges, path.fillType, clip, color, baseA, supers)
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

    // --------------------------------------------------------------------
    // Non-AA path (Phase 1) — unchanged.
    // --------------------------------------------------------------------

    private fun drawRectNonAA(rect: SkRect, clip: SkIRect, paint: SkPaint) {
        when (paint.style) {
            SkPaint.Style.kFill_Style -> fillRect(rect, clip, paint.color)
            SkPaint.Style.kStroke_Style -> strokeRect(rect, paint.strokeWidth, clip, paint.color)
            SkPaint.Style.kStrokeAndFill_Style -> {
                fillRect(rect, clip, paint.color)
                strokeRect(rect, paint.strokeWidth, clip, paint.color)
            }
        }
    }

    private fun fillRect(rect: SkRect, clip: SkIRect, color: SkColor) {
        val l = pixelEdge(rect.left).coerceAtLeast(clip.left)
        val t = pixelEdge(rect.top).coerceAtLeast(clip.top)
        val r = pixelEdge(rect.right).coerceAtMost(clip.right)
        val b = pixelEdge(rect.bottom).coerceAtMost(clip.bottom)
        for (y in t until b) {
            for (x in l until r) {
                blend(x, y, color)
            }
        }
    }

    private fun strokeRect(rect: SkRect, strokeWidth: Float, clip: SkIRect, color: SkColor) {
        if (strokeWidth <= 0f) {
            // Hairline: 1px-wide outline. Skia's AA-off hairline snaps the
            // outline to floor-style integer coords (matches `SkScan::HairLineRgn`).
            val l = floor(rect.left)
            val t = floor(rect.top)
            val r = floor(rect.right)
            val b = floor(rect.bottom)
            drawHLine(l, r + 1, t, clip, color)         // top edge
            drawHLine(l, r + 1, b, clip, color)         // bottom edge
            drawVLine(l, t + 1, b, clip, color)         // left edge
            drawVLine(r, t + 1, b, clip, color)         // right edge
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
                    blend(x, y, color)
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
        when (paint.style) {
            SkPaint.Style.kFill_Style -> fillRectAA(rect, clip, paint.color)
            SkPaint.Style.kStroke_Style -> strokeRectAA(rect, paint.strokeWidth, clip, paint.color)
            SkPaint.Style.kStrokeAndFill_Style -> {
                fillRectAA(rect, clip, paint.color)
                strokeRectAA(rect, paint.strokeWidth, clip, paint.color)
            }
        }
    }

    private fun fillRectAA(rect: SkRect, clip: SkIRect, color: SkColor) {
        if (rect.right <= rect.left || rect.bottom <= rect.top) return
        val baseA = SkColorGetA(color)
        if (baseA == 0) return
        val rgb = color and 0x00FFFFFF
        val ix0 = floor(rect.left).coerceAtLeast(clip.left)
        val iy0 = floor(rect.top).coerceAtLeast(clip.top)
        val ix1 = ceil(rect.right).coerceAtMost(clip.right)
        val iy1 = ceil(rect.bottom).coerceAtMost(clip.bottom)
        for (y in iy0 until iy1) {
            val cy = covAxis(rect.top, rect.bottom, y)
            if (cy <= 0f) continue
            for (x in ix0 until ix1) {
                val cx = covAxis(rect.left, rect.right, x)
                if (cx <= 0f) continue
                val effA = scaleAlpha(baseA, cx * cy)
                if (effA == 0) continue
                blend(x, y, (effA shl 24) or rgb)
            }
        }
    }

    /**
     * AA stroke = AA fill of (outer rect minus inner rect). Hairline
     * (`strokeWidth <= 0`) renders as a 1-pixel-wide AA frame — for
     * axis-aligned rects this lights up the same pixel set as Skia's
     * `SkScan::AntiHairLineRgn` with matching coverage at half-integer edges.
     */
    private fun strokeRectAA(rect: SkRect, strokeWidth: Float, clip: SkIRect, color: SkColor) {
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
        if (baseA == 0) return
        val rgb = color and 0x00FFFFFF
        val ix0 = floor(ol).coerceAtLeast(clip.left)
        val iy0 = floor(ot).coerceAtLeast(clip.top)
        val ix1 = ceil(or).coerceAtMost(clip.right)
        val iy1 = ceil(ob).coerceAtMost(clip.bottom)
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
                blend(x, y, (effA shl 24) or rgb)
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
        path: SkPath, sx: Float, sy: Float, tx: Float, ty: Float,
    ): List<Edge> {
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
                    val x = coords[coordIdx++] * sx + tx
                    val y = coords[coordIdx++] * sy + ty
                    px = x; py = y
                    cx = x; cy = y
                    hasContour = true
                }
                SkPath.Verb.kLine -> {
                    val x = coords[coordIdx++] * sx + tx
                    val y = coords[coordIdx++] * sy + ty
                    addEdge(out, px, py, x, y)
                    px = x; py = y
                }
                SkPath.Verb.kQuad -> {
                    val x1 = coords[coordIdx++] * sx + tx
                    val y1 = coords[coordIdx++] * sy + ty
                    val x2 = coords[coordIdx++] * sx + tx
                    val y2 = coords[coordIdx++] * sy + ty
                    flattenQuad(out, px, py, x1, y1, x2, y2, depth = 0)
                    px = x2; py = y2
                }
                SkPath.Verb.kConic -> {
                    val x1 = coords[coordIdx++] * sx + tx
                    val y1 = coords[coordIdx++] * sy + ty
                    val x2 = coords[coordIdx++] * sx + tx
                    val y2 = coords[coordIdx++] * sy + ty
                    val w = weights[weightIdx++]
                    flattenConic(out, px, py, x1, y1, x2, y2, w)
                    px = x2; py = y2
                }
                SkPath.Verb.kCubic -> {
                    val x1 = coords[coordIdx++] * sx + tx
                    val y1 = coords[coordIdx++] * sy + ty
                    val x2 = coords[coordIdx++] * sx + tx
                    val y2 = coords[coordIdx++] * sy + ty
                    val x3 = coords[coordIdx++] * sx + tx
                    val y3 = coords[coordIdx++] * sy + ty
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
            // Emit the row.
            for (xOff in 0 until rowWidth) {
                val samples = coverage[xOff]
                if (samples == 0) continue
                val effA = if (samples >= maxSamples) baseA
                    else (baseA * samples + maxSamples / 2) / maxSamples
                if (effA == 0) continue
                blend(clip.left + xOff, py, (effA shl 24) or rgb)
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

    private fun drawHLine(x0: Int, x1: Int, y: Int, clip: SkIRect, color: SkColor) {
        if (y < clip.top || y >= clip.bottom) return
        val l = x0.coerceAtLeast(clip.left)
        val r = x1.coerceAtMost(clip.right)
        for (x in l until r) blend(x, y, color)
    }

    private fun drawVLine(x: Int, y0: Int, y1: Int, clip: SkIRect, color: SkColor) {
        if (x < clip.left || x >= clip.right) return
        val t = y0.coerceAtLeast(clip.top)
        val b = y1.coerceAtMost(clip.bottom)
        for (y in t until b) blend(x, y, color)
    }

    /** Source-Over compositing in non-premultiplied ARGB8888. */
    private fun blend(x: Int, y: Int, src: SkColor) {
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
    }
}
