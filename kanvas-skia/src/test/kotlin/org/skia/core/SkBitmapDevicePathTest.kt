package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkRect
import kotlin.math.hypot

/**
 * Smoke tests for the Phase 3b path rasterization pipeline. These don't
 * compare against a reference image — they verify that the scanline fill
 * lights up the expected pixels for known geometric primitives.
 *
 * GMs ride on top of this pipeline; this is the "is the engine actually
 * doing what we think it is" net.
 */
class SkBitmapDevicePathTest {

    private fun render(width: Int, height: Int, draw: SkCanvas.() -> Unit): SkBitmap {
        val bitmap = SkBitmap(width, height)
        bitmap.eraseColor(SK_ColorWHITE)
        SkCanvas(bitmap).apply(draw)
        return bitmap
    }

    private fun isBlack(bitmap: SkBitmap, x: Int, y: Int, alphaMin: Int = 200): Boolean {
        val px = bitmap.getPixel(x, y)
        return SkColorGetR(px) < 64 && SkColorGetG(px) < 64 && SkColorGetB(px) < 64 &&
            SkColorGetA(px) >= alphaMin
    }

    @Test
    fun `fill rect drawn via SkPath Rect lights up the expected pixel set`() {
        val path = SkPath.Rect(SkRect.MakeLTRB(10f, 20f, 30f, 50f))
        val bitmap = render(40, 60) {
            drawPath(path, SkPaint().apply { color = SK_ColorBLACK })
        }
        // Inside.
        assertTrue(isBlack(bitmap, 20, 30))
        // Outside.
        assertTrue(!isBlack(bitmap, 5, 30))
        assertTrue(!isBlack(bitmap, 35, 30))
        // Corner-just-inside vs just-outside (using the pixel-edge rule of the
        // non-AA fill: pixel N covered iff x ∈ [floor(l+0.5), floor(r+0.5))).
        assertTrue(isBlack(bitmap, 10, 20))
        assertTrue(!isBlack(bitmap, 30, 50))
    }

    @Test
    fun `fill circle drawn via SkPath Circle approximates a disc`() {
        val cx = 50f; val cy = 50f; val r = 40f
        val path = SkPath.Circle(cx, cy, r)
        val bitmap = render(100, 100) {
            drawPath(path, SkPaint().apply {
                color = SK_ColorBLACK
                isAntiAlias = true
            })
        }
        // Centre is fully filled.
        assertTrue(isBlack(bitmap, 50, 50))
        // Cardinal points well inside the disc are filled.
        assertTrue(isBlack(bitmap, 50, 20), "top cardinal interior should be filled")
        assertTrue(isBlack(bitmap, 20, 50), "left cardinal interior should be filled")
        assertTrue(isBlack(bitmap, 80, 50), "right cardinal interior should be filled")
        assertTrue(isBlack(bitmap, 50, 80), "bottom cardinal interior should be filled")
        // Pixels well outside the disc (but inside the canvas) are untouched.
        assertTrue(!isBlack(bitmap, 5, 5))
        assertTrue(!isBlack(bitmap, 95, 95))
        // For every "centre-of-pixel" that should be inside vs outside, alpha
        // crosses 0 monotonically as we scan radially. Sample at several radii.
        for (theta in 0 until 8) {
            val angle = theta * (Math.PI / 4.0)
            val ix = (cx + (r - 10f) * kotlin.math.cos(angle)).toInt()
            val iy = (cy + (r - 10f) * kotlin.math.sin(angle)).toInt()
            assertTrue(
                isBlack(bitmap, ix, iy),
                "interior at angle ${theta * 45}° (px $ix,$iy) should be filled",
            )
        }
    }

    @Test
    fun `fill quadTo curve covers control point neighbourhood`() {
        // Triangle-ish shape with a quadTo bulging out toward (50, 5).
        val path = SkPathBuilder()
            .moveTo(10f, 50f)
            .lineTo(90f, 50f)
            .quadTo(50f, 5f, 10f, 50f)
            .close()
            .detach()
        val bitmap = render(100, 60) {
            drawPath(path, SkPaint().apply {
                color = SK_ColorBLACK
                isAntiAlias = true
            })
        }
        // Mid-curve point (around y ≈ 28) is filled.
        assertTrue(isBlack(bitmap, 50, 30), "mid-curve interior should be filled")
        // Above the curve (close to top edge of canvas) is empty.
        assertTrue(!isBlack(bitmap, 50, 1))
    }

    @Test
    fun `fill rule even-odd produces a hole when contours overlap`() {
        // Outer 50×50 square with an inner 30×30 hole — even-odd fill rule.
        val path = SkPathBuilder()
            .setFillType(org.skia.foundation.SkPathFillType.kEvenOdd)
            .addRect(SkRect.MakeLTRB(10f, 10f, 60f, 60f))
            .addRect(SkRect.MakeLTRB(20f, 20f, 50f, 50f))
            .detach()
        val bitmap = render(70, 70) {
            drawPath(path, SkPaint().apply { color = SK_ColorBLACK })
        }
        // Outer ring: filled.
        assertTrue(isBlack(bitmap, 12, 12), "outer ring should be filled")
        assertTrue(isBlack(bitmap, 55, 55), "outer ring should be filled")
        // Hole interior: empty.
        assertTrue(!isBlack(bitmap, 35, 35), "hole interior should be empty")
    }

    @Test
    fun `cubicTo close approximates a smooth closed curve`() {
        // Closed teardrop using cubic.
        val path = SkPathBuilder()
            .moveTo(50f, 10f)
            .cubicTo(80f, 10f, 80f, 60f, 50f, 60f)
            .cubicTo(20f, 60f, 20f, 10f, 50f, 10f)
            .close()
            .detach()
        val bitmap = render(100, 70) {
            drawPath(path, SkPaint().apply {
                color = SK_ColorBLACK
                isAntiAlias = true
            })
        }
        // Centre of the teardrop is filled.
        assertTrue(isBlack(bitmap, 50, 35), "teardrop interior should be filled")
        // Outside is empty.
        assertTrue(!isBlack(bitmap, 5, 35))
    }

    @Test
    fun `addArc 360 degree sweep encloses a full disc`() {
        val path = SkPathBuilder()
            .addArc(SkRect.MakeLTRB(10f, 10f, 90f, 90f), 0f, 360f)
            .close()
            .detach()
        val bitmap = render(100, 100) {
            drawPath(path, SkPaint().apply {
                color = SK_ColorBLACK
                isAntiAlias = true
            })
        }
        // Disc centre filled.
        assertTrue(isBlack(bitmap, 50, 50))
        // Several radii sampled near the centre — all filled.
        for (dx in -10..10 step 5) {
            for (dy in -10..10 step 5) {
                if (hypot(dx.toDouble(), dy.toDouble()) <= 10.0) {
                    assertTrue(isBlack(bitmap, 50 + dx, 50 + dy))
                }
            }
        }
    }
}
