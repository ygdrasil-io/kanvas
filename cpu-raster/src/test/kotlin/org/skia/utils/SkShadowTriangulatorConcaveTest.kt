package org.skia.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * S7-C — verifies [SkShadowTriangulator] handles concave simple
 * polygons (5-point star, U-shape, arrow) without falling through to
 * the blur path.
 */
class SkShadowTriangulatorConcaveTest {

    @Test
    fun `flattenPathToPolygon walks a square's verbs into 4 vertices`() {
        val path = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(10f, 0f)
            .lineTo(10f, 10f)
            .lineTo(0f, 10f)
            .close()
            .detach()
        val poly = SkShadowTriangulator.flattenPathToPolygon(path)
        assertEquals(4, poly.size, "square has 4 distinct vertices")
    }

    @Test
    fun `isPolygonConvex says square is convex`() {
        val square = listOf(
            SkPoint(0f, 0f), SkPoint(10f, 0f),
            SkPoint(10f, 10f), SkPoint(0f, 10f),
        )
        assertTrue(SkShadowTriangulator.isPolygonConvex(square))
    }

    @Test
    fun `isPolygonConvex flags a 5-point star as concave`() {
        val star = makeStar(5, 5f, 2f)
        assertFalse(SkShadowTriangulator.isPolygonConvex(star), "5-point star is concave")
    }

    @Test
    fun `triangulateSimplePolygon produces N-2 triangles for a convex quad`() {
        val square = listOf(
            SkPoint(0f, 0f), SkPoint(10f, 0f),
            SkPoint(10f, 10f), SkPoint(0f, 10f),
        )
        val tris = SkShadowTriangulator.triangulateSimplePolygon(square)
        assertNotNull(tris, "convex quad must triangulate")
        // N - 2 = 2 triangles, 3 indices each = 6 indices.
        assertEquals(6, tris!!.size)
    }

    @Test
    fun `triangulateSimplePolygon handles a 5-point concave star`() {
        // 5-point star : 10 vertices alternating outer / inner radius.
        val star = makeStar(5, 5f, 2f)
        assertEquals(10, star.size)
        val tris = SkShadowTriangulator.triangulateSimplePolygon(star)
        assertNotNull(tris, "5-point star must triangulate via ear-clipping")
        // N - 2 = 8 triangles ⇒ 24 indices.
        assertEquals(24, tris!!.size)
        // Every emitted index must be in range.
        for (idx in tris) {
            assertTrue(idx in 0 until star.size, "index $idx out of range for ${star.size} vertices")
        }
    }

    @Test
    fun `triangulateSimplePolygon handles a U-shape (concave with reflex vertex)`() {
        // Simple U : 8 vertices.
        //    7──6      0──1
        //    │  │      │  │
        //    │  5──────4  │
        //    │            │
        //    3────────────2
        val u = listOf(
            SkPoint(0f, 0f), SkPoint(2f, 0f),
            SkPoint(2f, 5f), SkPoint(8f, 5f),
            SkPoint(8f, 0f), SkPoint(10f, 0f),
            SkPoint(10f, 8f), SkPoint(0f, 8f),
        )
        assertFalse(SkShadowTriangulator.isPolygonConvex(u))
        val tris = SkShadowTriangulator.triangulateSimplePolygon(u)
        assertNotNull(tris, "U-shape must triangulate")
        // 8 - 2 = 6 triangles ⇒ 18 indices.
        assertEquals(18, tris!!.size)
    }

    @Test
    fun `triangulateSimplePolygon rejects a degenerate polygon`() {
        val collinear = listOf(SkPoint(0f, 0f), SkPoint(1f, 0f), SkPoint(2f, 0f))
        // signed area is zero — triangulator returns null (degenerate).
        val tris = SkShadowTriangulator.triangulateSimplePolygon(collinear)
        // Either null or zero-size both indicate "no triangles" — both acceptable.
        assertTrue(tris == null || tris.isEmpty())
    }

    @Test
    fun `flattenPathToPolygon then triangulateSimplePolygon round-trips a star path`() {
        val pb = SkPathBuilder()
        val star = makeStar(5, 5f, 2f)
        pb.moveTo(star[0].fX, star[0].fY)
        for (i in 1 until star.size) pb.lineTo(star[i].fX, star[i].fY)
        pb.close()
        val path = pb.detach()

        val poly = SkShadowTriangulator.flattenPathToPolygon(path)
        // Star has 10 vertices ; close drops the redundant duplicate.
        assertEquals(10, poly.size)
        val tris = SkShadowTriangulator.triangulateSimplePolygon(poly)
        assertNotNull(tris, "star path must triangulate end-to-end")
    }

    /** Build a 2×n-vertex star polygon centred at origin. */
    private fun makeStar(n: Int, outerR: Float, innerR: Float): List<SkPoint> {
        val out = ArrayList<SkPoint>(n * 2)
        val step = PI / n
        for (i in 0 until n * 2) {
            val r = if (i % 2 == 0) outerR else innerR
            val a = i * step - PI / 2 // start at top
            out.add(SkPoint((r * cos(a)).toFloat(), (r * sin(a)).toFloat()))
        }
        return out
    }
}
