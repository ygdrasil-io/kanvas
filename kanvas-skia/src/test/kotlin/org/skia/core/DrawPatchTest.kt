package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkPaint
import org.skia.math.SkPoint

/**
 * Phase I5.4 — `SkCanvas.drawPatch` (Coons patch tessellation).
 */
class DrawPatchTest {

    private fun newWhiteCanvas(w: Int = 40, h: Int = 40): Pair<SkBitmap, SkCanvas> {
        val bm = SkBitmap(w, h)
        bm.eraseColor(0xFFFFFFFF.toInt())
        return Pair(bm, SkCanvas(bm))
    }

    /**
     * Build a 12-point cubic array describing a flat (non-curved)
     * quad — every edge is a degenerate cubic with control points
     * evenly placed along the straight edge. Useful for tests that
     * only care about colour / texCoord interpolation.
     */
    private fun flatQuad(
        x0: Float, y0: Float, x1: Float, y1: Float,
    ): Array<SkPoint> {
        // Corner layout :
        //   [0] top-left, [3] top-right, [6] bottom-right, [9] bottom-left.
        val tl = SkPoint(x0, y0); val tr = SkPoint(x1, y0)
        val br = SkPoint(x1, y1); val bl = SkPoint(x0, y1)
        // Edge controls : place them 1/3 and 2/3 along each straight edge.
        // top edge (left → right) :
        val t1 = SkPoint(x0 + (x1 - x0) / 3f, y0)
        val t2 = SkPoint(x0 + 2 * (x1 - x0) / 3f, y0)
        // right edge (top → bottom) :
        val r1 = SkPoint(x1, y0 + (y1 - y0) / 3f)
        val r2 = SkPoint(x1, y0 + 2 * (y1 - y0) / 3f)
        // bottom edge (right → left) :
        val b1 = SkPoint(x1 - (x1 - x0) / 3f, y1)
        val b2 = SkPoint(x1 - 2 * (x1 - x0) / 3f, y1)
        // left edge (bottom → top) :
        val l1 = SkPoint(x0, y1 - (y1 - y0) / 3f)
        val l2 = SkPoint(x0, y1 - 2 * (y1 - y0) / 3f)
        return arrayOf(tl, t1, t2, tr, r1, r2, br, b1, b2, bl, l1, l2)
    }

    @Test
    fun `drawPatch rejects wrong-sized cubics array`() {
        val (bm, canvas) = newWhiteCanvas()
        assertThrows(IllegalArgumentException::class.java) {
            canvas.drawPatch(
                cubics = arrayOf(SkPoint(0f, 0f)),  // only 1, need 12
                colors = null,
                texCoords = null,
                blendMode = SkBlendMode.kSrcOver,
                paint = SkPaint(0xFF000000.toInt()),
            )
        }
    }

    @Test
    fun `drawPatch rejects wrong-sized colors array`() {
        val (bm, canvas) = newWhiteCanvas()
        assertThrows(IllegalArgumentException::class.java) {
            canvas.drawPatch(
                cubics = flatQuad(0f, 0f, 30f, 30f),
                colors = intArrayOf(0xFFFF0000.toInt()),  // only 1, need 4
                texCoords = null,
                blendMode = SkBlendMode.kSrcOver,
                paint = SkPaint(0xFF000000.toInt()),
            )
        }
    }

    @Test
    fun `drawPatch with no colors and no texCoords paints solid via paint color`() {
        val (bm, canvas) = newWhiteCanvas()
        canvas.drawPatch(
            cubics = flatQuad(5f, 5f, 30f, 30f),
            colors = null,
            texCoords = null,
            blendMode = SkBlendMode.kSrcOver,
            paint = SkPaint(0xFF0000FF.toInt()),
        )
        // Centre of the patch is filled blue.
        assertEquals(0xFF0000FF.toInt(), bm.getPixel(15, 15))
        // Outside the patch stays white.
        assertEquals(0xFFFFFFFF.toInt(), bm.getPixel(35, 35))
    }

    @Test
    fun `drawPatch with all-same corner colors paints solid`() {
        val (bm, canvas) = newWhiteCanvas()
        val red = 0xFFFF0000.toInt()
        canvas.drawPatch(
            cubics = flatQuad(5f, 5f, 30f, 30f),
            colors = intArrayOf(red, red, red, red),
            texCoords = null,
            blendMode = SkBlendMode.kSrcOver,
            paint = SkPaint(0xFF000000.toInt()),
        )
        assertEquals(red, bm.getPixel(15, 15))
    }

    @Test
    fun `drawPatch interpolates corner colors bilinearly`() {
        val (bm, canvas) = newWhiteCanvas(40, 40)
        // 4 corners : red TL, green TR, white BR, blue BL.
        val tl = 0xFFFF0000.toInt()
        val tr = 0xFF00FF00.toInt()
        val br = 0xFFFFFFFF.toInt()
        val bl = 0xFF0000FF.toInt()
        canvas.drawPatch(
            cubics = flatQuad(5f, 5f, 35f, 35f),
            colors = intArrayOf(tl, tr, br, bl),
            texCoords = null,
            blendMode = SkBlendMode.kSrcOver,
            paint = SkPaint(0xFF000000.toInt()),
        )
        // Near top-left corner → red dominates.
        val nearTL = bm.getPixel(7, 7)
        val rTL = (nearTL shr 16) and 0xFF
        val gTL = (nearTL shr 8) and 0xFF
        assertTrue(rTL > gTL) { "near TL : R=$rTL should dominate G=$gTL (got $nearTL)" }
        // Near bottom-left corner → blue dominates.
        val nearBL = bm.getPixel(7, 33)
        val gBL = (nearBL shr 8) and 0xFF
        val bBL = nearBL and 0xFF
        assertTrue(bBL > gBL) { "near BL : B=$bBL should dominate G=$gBL (got $nearBL)" }
    }

    @Test
    fun `drawPatch with texCoords samples the shader bilinearly`() {
        val (bm, canvas) = newWhiteCanvas(40, 40)
        // Tiny 2×2 atlas : red / green / blue / white at 4 quadrants.
        val atlas = SkBitmap(2, 2).apply {
            setPixel(0, 0, 0xFFFF0000.toInt())
            setPixel(1, 0, 0xFF00FF00.toInt())
            setPixel(0, 1, 0xFF0000FF.toInt())
            setPixel(1, 1, 0xFFFFFFFF.toInt())
        }.asImage()
        val paint = SkPaint(0xFF000000.toInt()).apply {
            shader = atlas.makeShader()
            isAntiAlias = false
        }
        canvas.drawPatch(
            cubics = flatQuad(5f, 5f, 35f, 35f),
            colors = null,
            texCoords = arrayOf(SkPoint(0f, 0f), SkPoint(2f, 0f), SkPoint(2f, 2f), SkPoint(0f, 2f)),
            blendMode = SkBlendMode.kModulate,
            paint = paint,
        )
        // Top-left quadrant of patch maps to (0, 0) of atlas — red.
        val tlPx = bm.getPixel(8, 8)
        val rTL = (tlPx shr 16) and 0xFF
        val gTL = (tlPx shr 8) and 0xFF
        assertTrue(rTL > gTL) { "TL patch quadrant : R=$rTL should dominate G=$gTL (got ${tlPx.toUInt().toString(16)})" }
        // Top-right quadrant maps to (1, 0) — green.
        val trPx = bm.getPixel(32, 8)
        val rTR = (trPx shr 16) and 0xFF
        val gTR = (trPx shr 8) and 0xFF
        assertTrue(gTR > rTR) { "TR patch quadrant : G=$gTR should dominate R=$rTR (got ${trPx.toUInt().toString(16)})" }
    }
}
