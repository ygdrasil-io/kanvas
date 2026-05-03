package org.skia.core

import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColor
import org.skia.foundation.SkColorGetA
import org.skia.foundation.SkColorGetB
import org.skia.foundation.SkColorGetG
import org.skia.foundation.SkColorGetR
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.math.SkIRect
import org.skia.math.SkRect
import kotlin.math.ceil as kCeil
import kotlin.math.floor as kFloor

private fun floor(v: Float): Int = kFloor(v.toDouble()).toInt()
private fun ceil(v: Float): Int = kCeil(v.toDouble()).toInt()

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
 *
 * Receives device-space coordinates from `SkCanvas`; the canvas owns the
 * matrix and clip stacks and is responsible for transforming and clipping
 * into the `clip` rect passed here.
 */
public class SkBitmapDevice(public val bitmap: SkBitmap) {

    public val width: Int get() = bitmap.width
    public val height: Int get() = bitmap.height

    public fun deviceClipBounds(): SkIRect = SkIRect.MakeWH(width, height)

    public fun drawRect(rect: SkRect, clip: SkIRect, paint: SkPaint) {
        if (paint.isAntiAlias) drawRectAA(rect, clip, paint) else drawRectNonAA(rect, clip, paint)
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
