package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skia.core.SkColorSpaceXformSteps
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkClipOp
import org.skia.foundation.SkColor
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShader
import org.skia.math.SkMatrix

/**
 * Phase R2.14 + R-suivi.20 — `SkCanvas.clipShader(shader, op)` end-to-end
 * through every draw entry point. The clip shader's alpha channel
 * modulates per-pixel coverage : opaque pixels of the shader keep the
 * draw ; transparent pixels clip it out.
 *
 * R-suivi.20 extends the original R2 wiring (drawPaint only) to
 * drawRect / drawPath / drawImage / drawOval / drawRRect / drawArc /
 * drawLine / drawPoints — every rasterizer flavour, since the
 * modulation now lives inside the device's blend funnels.
 */
class SkCanvasClipShaderTest {

    /**
     * Alpha-only shader : returns transparent black for x < [splitX],
     * opaque black for x >= [splitX]. The alpha channel is what
     * `clipShader` reads ; the RGB is irrelevant.
     */
    private class VerticalSplitAlphaShader(private val splitX: Int) : SkShader() {
        override fun shadeRow(devX: Int, devY: Int, count: Int, dst: IntArray) {
            for (i in 0 until count) {
                val x = devX + i
                dst[i] = if (x < splitX) 0x00000000 else 0xFF000000.toInt()
            }
        }
        override fun setupForDraw(canvasCtm: SkMatrix, xform: SkColorSpaceXformSteps) {
            // Bypass the inverse-matrix bookkeeping — we sample in pure
            // device space.
        }
    }

    @Test
    fun `clipShader kIntersect masks drawPaint by shader alpha`() {
        val bm = SkBitmap(10, 6).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(bm)
        canvas.save()
        // Shader is opaque from x >= 5, transparent for x < 5.
        canvas.clipShader(VerticalSplitAlphaShader(5), SkClipOp.kIntersect)
        canvas.drawPaint(SkPaint(SK_ColorRED))
        canvas.restore()
        for (y in 0 until 6) {
            for (x in 0 until 10) {
                val expected = if (x >= 5) SK_ColorRED else SK_ColorWHITE
                assertEquals(expected, bm.getPixel(x, y), "($x,$y)")
            }
        }
    }

    @Test
    fun `clipShader kDifference inverts the alpha mask`() {
        val bm = SkBitmap(10, 6).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(bm)
        canvas.save()
        // Same shader but op = kDifference : keep pixels where the
        // shader's alpha is *zero* — i.e. x < 5.
        canvas.clipShader(VerticalSplitAlphaShader(5), SkClipOp.kDifference)
        canvas.drawPaint(SkPaint(SK_ColorRED))
        canvas.restore()
        for (y in 0 until 6) {
            for (x in 0 until 10) {
                val expected = if (x < 5) SK_ColorRED else SK_ColorWHITE
                assertEquals(expected, bm.getPixel(x, y), "($x,$y)")
            }
        }
    }

    @Test
    fun `clipShader is scoped to save and restore`() {
        val bm = SkBitmap(10, 4).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(bm)
        // Pass 1 — under clip : only x >= 5 receives RED.
        canvas.save()
        canvas.clipShader(VerticalSplitAlphaShader(5))
        canvas.drawPaint(SkPaint(SK_ColorRED))
        canvas.restore()
        // Pass 2 — clip lifted : whole canvas (was white + half-red)
        // gets painted black.
        canvas.drawPaint(SkPaint(SK_ColorBLACK))
        for (y in 0 until 4) {
            for (x in 0 until 10) {
                assertEquals(SK_ColorBLACK, bm.getPixel(x, y), "($x,$y) must be black after pass 2")
            }
        }
    }

    @Test
    fun `clipShader composes with clipRect`() {
        val bm = SkBitmap(10, 10).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(bm)
        canvas.save()
        canvas.clipRect(org.skia.math.SkRect.MakeLTRB(0f, 0f, 10f, 5f))
        canvas.clipShader(VerticalSplitAlphaShader(5))
        canvas.drawPaint(SkPaint(SK_ColorRED))
        canvas.restore()
        for (y in 0 until 10) {
            for (x in 0 until 10) {
                val expected: SkColor = if (x >= 5 && y < 5) SK_ColorRED else SK_ColorWHITE
                assertEquals(expected, bm.getPixel(x, y), "($x,$y)")
            }
        }
    }

    // ─── R-suivi.20 — clipShader rasterizer wiring for non-drawPaint draws ─

    @Test
    fun `clipShader masks drawRect by shader alpha`() {
        val bm = SkBitmap(10, 6).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(bm)
        canvas.save()
        canvas.clipShader(VerticalSplitAlphaShader(5))
        canvas.drawRect(org.skia.math.SkRect.MakeLTRB(0f, 0f, 10f, 6f), SkPaint(SK_ColorRED))
        canvas.restore()
        for (y in 0 until 6) {
            for (x in 0 until 10) {
                val expected = if (x >= 5) SK_ColorRED else SK_ColorWHITE
                assertEquals(expected, bm.getPixel(x, y), "($x,$y)")
            }
        }
    }

    @Test
    fun `clipShader masks drawPath by shader alpha`() {
        val bm = SkBitmap(10, 6).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(bm)
        // Build an axis-aligned rectangular path covering the bitmap.
        val path = org.skia.foundation.SkPath.Rect(org.skia.math.SkRect.MakeLTRB(0f, 0f, 10f, 6f))
        canvas.save()
        canvas.clipShader(VerticalSplitAlphaShader(5))
        canvas.drawPath(path, SkPaint(SK_ColorRED))
        canvas.restore()
        for (y in 0 until 6) {
            for (x in 0 until 10) {
                val expected = if (x >= 5) SK_ColorRED else SK_ColorWHITE
                assertEquals(expected, bm.getPixel(x, y), "($x,$y)")
            }
        }
    }

    @Test
    fun `clipShader masks drawImage by shader alpha`() {
        // Build a solid-red source image.
        val srcBm = SkBitmap(10, 6).also { it.eraseColor(SK_ColorRED) }
        val srcImage = srcBm.asImage()
        val dstBm = SkBitmap(10, 6).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(dstBm)
        canvas.save()
        canvas.clipShader(VerticalSplitAlphaShader(5))
        canvas.drawImage(srcImage, 0f, 0f, paint = SkPaint())
        canvas.restore()
        for (y in 0 until 6) {
            for (x in 0 until 10) {
                val expected = if (x >= 5) SK_ColorRED else SK_ColorWHITE
                assertEquals(expected, dstBm.getPixel(x, y), "($x,$y)")
            }
        }
    }

    @Test
    fun `clipShader masks drawOval by shader alpha`() {
        val bm = SkBitmap(10, 6).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(bm)
        canvas.save()
        canvas.clipShader(VerticalSplitAlphaShader(5))
        canvas.drawOval(org.skia.math.SkRect.MakeLTRB(0f, 0f, 10f, 6f), SkPaint(SK_ColorRED))
        canvas.restore()
        // Left half (x<5) must remain white ; right half should contain
        // at least one red pixel from the oval interior.
        var sawRed = false
        for (y in 0 until 6) {
            for (x in 0 until 5) {
                assertEquals(SK_ColorWHITE, bm.getPixel(x, y), "left-half ($x,$y) must stay white")
            }
            for (x in 5 until 10) {
                if (bm.getPixel(x, y) == SK_ColorRED) sawRed = true
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(sawRed, "right half should contain at least one red pixel")
    }

    @Test
    fun `clipShader masks drawLine by shader alpha`() {
        val bm = SkBitmap(10, 6).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(bm)
        canvas.save()
        canvas.clipShader(VerticalSplitAlphaShader(5))
        // Horizontal line across the full width at y=3 with a thick stroke
        // so a sufficient pixel area lights up regardless of AA edges.
        val paint = SkPaint(SK_ColorRED).apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 2f
            isAntiAlias = false
        }
        canvas.drawLine(0f, 3f, 10f, 3f, paint)
        canvas.restore()
        // Left half (x<5) must remain white.
        for (y in 0 until 6) {
            for (x in 0 until 5) {
                assertEquals(SK_ColorWHITE, bm.getPixel(x, y), "left-half ($x,$y) must stay white")
            }
        }
        // Right half must contain at least one red pixel from the stroke.
        var sawRed = false
        for (y in 0 until 6) {
            for (x in 5 until 10) {
                if (bm.getPixel(x, y) == SK_ColorRED) sawRed = true
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(sawRed, "stroke should leave red in the right half")
    }
}
