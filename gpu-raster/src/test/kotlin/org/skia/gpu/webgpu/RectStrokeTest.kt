package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkPaint
import org.skia.math.SkRect

/**
 * G3.1 acceptance tests — axis-aligned rect stroke on GPU.
 *
 * `drawRect` now dispatches on `paint.style` :
 *  - `kFill_Style` -> single fill (G2.3a)
 *  - `kStroke_Style` -> 4 edge sub-rects (annular outer/inner), or
 *    hairline 1-pixel edges when `strokeWidth <= 0`
 *  - `kStrokeAndFill_Style` -> fill then stroke
 *
 * These tests exercise the 3 main stroke regimes (hairline, thin,
 * thick) and the corner-bookkeeping pattern (top/bottom cover the
 * corners, left/right exclude them).
 */
class RectStrokeTest {

    @Test
    fun `hairline stroke produces 1-pixel outline on integer edges`() {
        // Stroke width = 0 -> hairline. Rect [10, 10, 30, 30] on white bg
        // -> 1-pixel blue frame at cols/rows {10, 30}.
        val pixels = runStrokeRect(
            rect = SkRect.MakeLTRB(10f, 10f, 30f, 30f),
            strokeWidth = 0f,
            isAa = false,
        )

        // Corner pixel : blue (on hairline edge)
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(10, 10), "top-left corner")
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(30, 10), "top-right corner")
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(10, 30), "bottom-left corner")
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(30, 30), "bottom-right corner")
        // Top edge interior : blue
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(20, 10), "top edge")
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(20, 30), "bottom edge")
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(10, 20), "left edge")
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(30, 20), "right edge")
        // Interior pixel : white (untouched by hairline stroke)
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(20, 20), "interior unfilled")
        // Outside the rect : white
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(5, 5), "outside top-left")
    }

    @Test
    fun `thin stroke (width 2) fills a 2px-wide annular band`() {
        // Stroke width = 2, non-AA -> outer rect [9, 9, 31, 31], inner rect
        // [11, 11, 29, 29]. The 4 edges fill the band between outer and inner.
        val pixels = runStrokeRect(
            rect = SkRect.MakeLTRB(10f, 10f, 30f, 30f),
            strokeWidth = 2f,
            isAa = false,
        )

        // Pixel in the stroke band (col 9) : blue
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(9, 20), "left band col 9")
        // Pixel in the stroke band (col 10) : still blue
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(10, 20), "left band col 10")
        // Pixel just inside the inner rect : white (not painted)
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(11, 20), "inner pixel col 11")
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(20, 20), "deep inner pixel")
        // Right band : symmetric
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(30, 20), "right band col 30")
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(29, 20), "right band col 29")
        // Outside the outer : white
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(8, 20), "outside outer col 8")
    }

    @Test
    fun `thick stroke whose inner is empty falls back to a full fill`() {
        // Stroke width = 50 over a 20x20 rect -> outer is way larger than
        // inner, but inner [10+25, 10+25, 30-25, 30-25] = [35, 35, 5, 5]
        // is inverted -> innerEmpty branch -> draw the whole outer rect.
        val pixels = runStrokeRect(
            rect = SkRect.MakeLTRB(10f, 10f, 30f, 30f),
            strokeWidth = 50f,
            isAa = false,
        )

        // What would have been the "interior" is now part of the fill.
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(20, 20), "filled where inner used to be")
        // Outer extends to (rect ± 25) = [-15, -15, 55, 55] -> clipped to
        // viewport. (5, 5) is inside the clamped outer.
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(5, 5), "filled in former outside-of-frame")
    }

    private fun runStrokeRect(rect: SkRect, strokeWidth: Float, isAa: Boolean): ByteArray {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val paint = SkPaint().apply {
            color = SK_ColorBLUE
            style = SkPaint.Style.kStroke_Style
            this.strokeWidth = strokeWidth
            isAntiAlias = isAa
        }
        return context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawRect(rect, paint)
                device.flush()
            }
        }
    }

    private fun ByteArray.rgbaAt(x: Int, y: Int): List<Int> {
        val i = (y * W + x) * 4
        return listOf(
            this[i].toInt() and 0xFF,
            this[i + 1].toInt() and 0xFF,
            this[i + 2].toInt() and 0xFF,
            this[i + 3].toInt() and 0xFF,
        )
    }

    private companion object {
        const val W: Int = 64
        const val H: Int = 64
    }
}
