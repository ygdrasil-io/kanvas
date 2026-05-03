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
import kotlin.math.floor as kFloor

private fun floor(v: Float): Int = kFloor(v.toDouble()).toInt()

/**
 * Skia's non-AA rect rasterization rule: pixel N is covered iff
 * `rect.{l,t} - 0.5 < N ≤ rect.{r,b} - 0.5` (top-exclusive, bottom-inclusive),
 * equivalent to integer range `[floor(c + 0.5), floor(c + 0.5))` from
 * `SkScalarRoundToInt`. Half-integer ties round toward +∞.
 */
private fun pixelEdge(c: Float): Int = kFloor(c.toDouble() + 0.5).toInt()

/**
 * CPU raster device. Phase 1: non-AA rect fill and stroke, SrcOver compositing.
 * Receives device-space coordinates from `SkCanvas`; the canvas owns the matrix
 * and clip stacks and is responsible for transforming and clipping into the
 * `clip` rect passed here.
 */
public class SkBitmapDevice(public val bitmap: SkBitmap) {

    public val width: Int get() = bitmap.width
    public val height: Int get() = bitmap.height

    public fun deviceClipBounds(): SkIRect = SkIRect.MakeWH(width, height)

    public fun drawRect(rect: SkRect, clip: SkIRect, paint: SkPaint) {
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
