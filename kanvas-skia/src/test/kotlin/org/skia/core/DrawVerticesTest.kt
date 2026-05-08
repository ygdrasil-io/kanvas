package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkVertices
import org.skia.math.SkPoint

/**
 * Phase I5.3.a — `SkCanvas.drawVertices` solid-color path semantics.
 *
 * Per-vertex colour / UV interpolation is deferred to I5.3.b ; this
 * suite covers triangle iteration under all three [SkVertices.VertexMode]s
 * and the indices-indirection path.
 */
class DrawVerticesTest {

    private fun newWhiteCanvas(w: Int = 30, h: Int = 30): Pair<SkBitmap, SkCanvas> {
        val bm = SkBitmap(w, h)
        bm.eraseColor(0xFFFFFFFF.toInt())
        return Pair(bm, SkCanvas(bm))
    }

    @Test
    fun `triangleCount on a kTriangles vertex array`() {
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangles,
            arrayOf(
                SkPoint(0f, 0f), SkPoint(10f, 0f), SkPoint(0f, 10f),
                SkPoint(10f, 0f), SkPoint(10f, 10f), SkPoint(0f, 10f),
            ),
        )
        assertEquals(2, v.triangleCount())
    }

    @Test
    fun `triangleCount on kTriangleStrip is N-2`() {
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangleStrip,
            arrayOf(SkPoint(0f, 0f), SkPoint(10f, 0f), SkPoint(0f, 10f), SkPoint(10f, 10f)),
        )
        assertEquals(2, v.triangleCount())
    }

    @Test
    fun `triangleCount on kTriangleFan is N-2`() {
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangleFan,
            arrayOf(SkPoint(5f, 5f), SkPoint(0f, 0f), SkPoint(10f, 0f), SkPoint(10f, 10f), SkPoint(0f, 10f)),
        )
        assertEquals(3, v.triangleCount())
    }

    @Test
    fun `triangleAt returns vertex indices honouring fan mode`() {
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangleFan,
            arrayOf(SkPoint(5f, 5f), SkPoint(0f, 0f), SkPoint(10f, 0f), SkPoint(10f, 10f)),
        )
        // Fan : (0, 1, 2), (0, 2, 3).
        assertTrue(v.triangleAt(0).contentEquals(intArrayOf(0, 1, 2)))
        assertTrue(v.triangleAt(1).contentEquals(intArrayOf(0, 2, 3)))
    }

    @Test
    fun `triangleAt with indices indirects through the indirection table`() {
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangles,
            arrayOf(SkPoint(0f, 0f), SkPoint(10f, 0f), SkPoint(0f, 10f), SkPoint(10f, 10f)),
            indices = shortArrayOf(0, 1, 2, 1, 3, 2),
        )
        assertEquals(2, v.triangleCount())
        assertTrue(v.triangleAt(0).contentEquals(intArrayOf(0, 1, 2)))
        assertTrue(v.triangleAt(1).contentEquals(intArrayOf(1, 3, 2)))
    }

    @Test
    fun `MakeCopy rejects mismatched colors size`() {
        assertThrows(IllegalArgumentException::class.java) {
            SkVertices.MakeCopy(
                SkVertices.VertexMode.kTriangles,
                arrayOf(SkPoint(0f, 0f), SkPoint(1f, 0f), SkPoint(0f, 1f)),
                colors = intArrayOf(0xFFFF0000.toInt()),  // wrong : should be 3
            )
        }
    }

    @Test
    fun `drawVertices on empty triangle count is a no-op`() {
        val (bm, canvas) = newWhiteCanvas()
        val before = bm.pixels.copyOf()
        // Single vertex → no triangles in any mode.
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangles,
            arrayOf(SkPoint(5f, 5f)),
        )
        canvas.drawVertices(v, SkBlendMode.kSrcOver, SkPaint(0xFF000000.toInt()))
        assertEquals(before.toList(), bm.pixels.toList())
    }

    @Test
    fun `drawVertices fills a single triangle with paint color`() {
        val (bm, canvas) = newWhiteCanvas()
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangles,
            arrayOf(SkPoint(5f, 5f), SkPoint(25f, 5f), SkPoint(5f, 25f)),
        )
        canvas.drawVertices(v, SkBlendMode.kSrcOver, SkPaint(0xFF0000FF.toInt()))
        // (10, 10) is inside the right-triangle (5,5)-(25,5)-(5,25). Expect blue.
        assertEquals(0xFF0000FF.toInt(), bm.getPixel(10, 10))
        // (29, 29) is outside. White.
        assertEquals(0xFFFFFFFF.toInt(), bm.getPixel(29, 29))
        // (4, 4) is outside (left of triangle). White.
        assertEquals(0xFFFFFFFF.toInt(), bm.getPixel(4, 4))
    }

    @Test
    fun `kTriangleFan tessellates a quad into two triangles`() {
        val (bm, canvas) = newWhiteCanvas()
        // Quad 5..25 × 5..25 as a fan : (5,5)-(25,5)-(25,25)-(5,25).
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangleFan,
            arrayOf(SkPoint(5f, 5f), SkPoint(25f, 5f), SkPoint(25f, 25f), SkPoint(5f, 25f)),
        )
        canvas.drawVertices(v, SkBlendMode.kSrcOver, SkPaint(0xFF0000FF.toInt()))
        // Quad interior is filled.
        assertEquals(0xFF0000FF.toInt(), bm.getPixel(15, 15))
        assertEquals(0xFF0000FF.toInt(), bm.getPixel(20, 10))
        assertEquals(0xFF0000FF.toInt(), bm.getPixel(10, 20))
        // Outside the quad stays white.
        assertEquals(0xFFFFFFFF.toInt(), bm.getPixel(2, 2))
    }

    @Test
    fun `kTriangleStrip tessellates a strip into a quad`() {
        val (bm, canvas) = newWhiteCanvas()
        // Strip : (5,5)-(25,5)-(5,25)-(25,25). Two triangles cover quad.
        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangleStrip,
            arrayOf(SkPoint(5f, 5f), SkPoint(25f, 5f), SkPoint(5f, 25f), SkPoint(25f, 25f)),
        )
        canvas.drawVertices(v, SkBlendMode.kSrcOver, SkPaint(0xFF0000FF.toInt()))
        assertEquals(0xFF0000FF.toInt(), bm.getPixel(15, 15))
        assertEquals(0xFFFFFFFF.toInt(), bm.getPixel(2, 2))
    }
}
