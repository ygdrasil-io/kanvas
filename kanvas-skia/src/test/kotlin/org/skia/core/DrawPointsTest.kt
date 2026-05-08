package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPaint
import org.skia.math.SkPoint

/**
 * Phase I5.1 — `drawPoints` semantics across all three [SkCanvas.PointMode]s.
 */
class DrawPointsTest {

    private fun newWhiteCanvas(w: Int = 30, h: Int = 30): Pair<SkBitmap, SkCanvas> {
        val bm = SkBitmap(w, h)
        bm.eraseColor(0xFFFFFFFF.toInt())
        return Pair(bm, SkCanvas(bm))
    }

    @Test
    fun `empty point array is a no-op`() {
        val (bm, canvas) = newWhiteCanvas()
        val before = bm.pixels.copyOf()
        canvas.drawPoints(SkCanvas.PointMode.kPoints, emptyArray(), SkPaint(0xFF000000.toInt()))
        assertEquals(before.toList(), bm.pixels.toList())
    }

    @Test
    fun `kPoints with round cap draws filled circles`() {
        val (bm, canvas) = newWhiteCanvas()
        val paint = SkPaint(0xFF000000.toInt()).apply {
            strokeWidth = 6f
            strokeCap = SkPaint.Cap.kRound_Cap
            isAntiAlias = false
        }
        canvas.drawPoints(SkCanvas.PointMode.kPoints, arrayOf(SkPoint(15f, 15f)), paint)
        // Centre pixel must be black ; corners (far from radius=3) stay white.
        assertEquals(0xFF000000.toInt(), bm.getPixel(15, 15))
        assertEquals(0xFFFFFFFF.toInt(), bm.getPixel(0, 0))
        assertEquals(0xFFFFFFFF.toInt(), bm.getPixel(29, 29))
    }

    @Test
    fun `kPoints with square cap draws axis-aligned squares`() {
        val (bm, canvas) = newWhiteCanvas()
        val paint = SkPaint(0xFF000000.toInt()).apply {
            strokeWidth = 6f
            strokeCap = SkPaint.Cap.kSquare_Cap
            isAntiAlias = false
        }
        canvas.drawPoints(SkCanvas.PointMode.kPoints, arrayOf(SkPoint(15f, 15f)), paint)
        // The square spans [12, 18) × [12, 18). Centre pixel is black ;
        // corner pixels just inside the square (12, 12) are also black ;
        // (11, 11) outside the square stays white.
        assertEquals(0xFF000000.toInt(), bm.getPixel(15, 15))
        assertEquals(0xFF000000.toInt(), bm.getPixel(12, 12))
        assertEquals(0xFFFFFFFF.toInt(), bm.getPixel(11, 11))
        assertEquals(0xFFFFFFFF.toInt(), bm.getPixel(18, 18))
    }

    @Test
    fun `kPoints with butt cap and zero stroke renders single-pixel dots`() {
        val (bm, canvas) = newWhiteCanvas()
        val paint = SkPaint(0xFF000000.toInt()).apply {
            strokeWidth = 0f  // hairline
            strokeCap = SkPaint.Cap.kButt_Cap
            isAntiAlias = false
        }
        canvas.drawPoints(SkCanvas.PointMode.kPoints, arrayOf(SkPoint(15f, 15f)), paint)
        // Single pixel drawn at (15, 15) ; neighbours stay white.
        assertEquals(0xFF000000.toInt(), bm.getPixel(15, 15))
        assertEquals(0xFFFFFFFF.toInt(), bm.getPixel(14, 15))
        assertEquals(0xFFFFFFFF.toInt(), bm.getPixel(16, 16))
    }

    @Test
    fun `kPoints with butt cap and positive stroke is invisible (degenerate stroke)`() {
        val (bm, canvas) = newWhiteCanvas()
        val before = bm.pixels.copyOf()
        val paint = SkPaint(0xFF000000.toInt()).apply {
            strokeWidth = 6f
            strokeCap = SkPaint.Cap.kButt_Cap
            isAntiAlias = false
        }
        canvas.drawPoints(SkCanvas.PointMode.kPoints, arrayOf(SkPoint(15f, 15f)), paint)
        // Degenerate butt stroke at a point produces no output.
        assertEquals(before.toList(), bm.pixels.toList())
    }

    private fun countNonWhite(bm: SkBitmap): Int {
        var n = 0
        for (i in 0 until bm.pixels.size) {
            if (bm.pixels[i] != 0xFFFFFFFF.toInt()) n++
        }
        return n
    }

    @Test
    fun `kLines connects each pair of points (line count matches pairs)`() {
        // 4 points → 2 lines : (5,15)→(25,15) and (15,5)→(15,25).
        // The two lines cross at (15, 15).
        val twoLineBm = newWhiteCanvas(30, 30)
        val paint = SkPaint(0xFF000000.toInt()).apply {
            strokeWidth = 1f
            style = SkPaint.Style.kStroke_Style
            isAntiAlias = false
        }
        twoLineBm.second.drawPoints(
            SkCanvas.PointMode.kLines,
            arrayOf(SkPoint(5f, 15f), SkPoint(25f, 15f), SkPoint(15f, 5f), SkPoint(15f, 25f)),
            paint,
        )
        val pixelsTwo = countNonWhite(twoLineBm.first)

        // Same input but only the first 2 points (single horizontal line).
        val oneLineBm = newWhiteCanvas(30, 30)
        oneLineBm.second.drawPoints(
            SkCanvas.PointMode.kLines,
            arrayOf(SkPoint(5f, 15f), SkPoint(25f, 15f)),
            paint,
        )
        val pixelsOne = countNonWhite(oneLineBm.first)

        // Two lines should fill ~2× as many pixels (modulo the shared
        // crossing pixel(s)).
        assertTrue(pixelsTwo > pixelsOne) {
            "expected 2 lines (n=$pixelsTwo) > 1 line (n=$pixelsOne)"
        }
    }

    @Test
    fun `kLines drops trailing odd point`() {
        val paint = SkPaint(0xFF000000.toInt()).apply {
            strokeWidth = 1f
            style = SkPaint.Style.kStroke_Style
            isAntiAlias = false
        }
        // 3 points : kLines pairs (p0, p1), drops trailing p2.
        val threeBm = newWhiteCanvas()
        threeBm.second.drawPoints(
            SkCanvas.PointMode.kLines,
            arrayOf(SkPoint(5f, 15f), SkPoint(25f, 15f), SkPoint(15f, 25f)),
            paint,
        )

        // Same first 2 points only — should produce identical output.
        val twoBm = newWhiteCanvas()
        twoBm.second.drawPoints(
            SkCanvas.PointMode.kLines,
            arrayOf(SkPoint(5f, 15f), SkPoint(25f, 15f)),
            paint,
        )

        assertEquals(threeBm.first.pixels.toList(), twoBm.first.pixels.toList())
    }

    @Test
    fun `kPolygon connects N-1 lines for N points`() {
        val paint = SkPaint(0xFF000000.toInt()).apply {
            strokeWidth = 1f
            style = SkPaint.Style.kStroke_Style
            isAntiAlias = false
        }
        // 3 points = 2 polyline segments. Compare with 2 points = 1 segment.
        val threeBm = newWhiteCanvas()
        threeBm.second.drawPoints(
            SkCanvas.PointMode.kPolygon,
            arrayOf(SkPoint(5f, 5f), SkPoint(5f, 25f), SkPoint(25f, 25f)),
            paint,
        )
        val twoBm = newWhiteCanvas()
        twoBm.second.drawPoints(
            SkCanvas.PointMode.kPolygon,
            arrayOf(SkPoint(5f, 5f), SkPoint(5f, 25f)),
            paint,
        )

        // 2 segments must paint strictly more than 1.
        assertTrue(countNonWhite(threeBm.first) > countNonWhite(twoBm.first)) {
            "3-pt polyline (${countNonWhite(threeBm.first)}) should paint more than 2-pt polyline (${countNonWhite(twoBm.first)})"
        }
    }

    @Test
    fun `kPolygon does not auto-close`() {
        val paint = SkPaint(0xFF000000.toInt()).apply {
            strokeWidth = 1f
            style = SkPaint.Style.kStroke_Style
            isAntiAlias = false
        }
        // Triangle vertices : if kPolygon auto-closed, output would
        // equal kLines on the same points + 1 extra segment closing
        // back to the start. We compare with a manually-closed polyline
        // (4 points, last == first) ; the difference would equal the
        // closing segment.
        val openBm = newWhiteCanvas()
        openBm.second.drawPoints(
            SkCanvas.PointMode.kPolygon,
            arrayOf(SkPoint(5f, 5f), SkPoint(25f, 5f), SkPoint(15f, 25f)),
            paint,
        )
        val closedBm = newWhiteCanvas()
        closedBm.second.drawPoints(
            SkCanvas.PointMode.kPolygon,
            arrayOf(SkPoint(5f, 5f), SkPoint(25f, 5f), SkPoint(15f, 25f), SkPoint(5f, 5f)),
            paint,
        )
        // The explicitly-closed polygon paints strictly more pixels
        // than the open one (the closing segment).
        assertTrue(countNonWhite(closedBm.first) > countNonWhite(openBm.first)) {
            "closed (${countNonWhite(closedBm.first)}) should > open (${countNonWhite(openBm.first)})"
        }
    }
}
