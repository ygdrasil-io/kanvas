package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.geometry.Point
import org.graphiks.kanvas.paint.StrokeJoin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GPUPathStrokeInputTest {
    @Test
    fun `stroked paths keep flattened contour vertices instead of fill triangles`() {
        val flattened = listOf(
            Point(0f, 0f),
            Point(10f, 0f),
            Point(10f, 10f),
            Point(0f, 10f),
            Point(0f, 0f),
        )
        val fillTriangles = listOf(
            Point(0f, 0f),
            Point(10f, 0f),
            Point(10f, 10f),
            Point(0f, 0f),
            Point(10f, 10f),
            Point(0f, 10f),
        )

        val selected = selectPathVerticesForCommand(
            isStroke = true,
            flattened = flattened,
            triangulated = fillTriangles,
        )

        assertEquals(flattened, selected)
    }

    @Test
    fun `closed stroke geometry emits triangle contours instead of one filled fan`() {
        val square = listOf(
            0f, 0f,
            10f, 0f,
            10f, 10f,
            0f, 10f,
            0f, 0f,
        )

        val stroke = strokeToFillGeometry(
            contourVertices = square,
            contourStarts = listOf(0),
            strokeWidth = 2f,
            joinStyle = StrokeJoin.MITER,
        )

        assertEquals(24, stroke.vertices.size / 2)
        assertEquals((0..24 step 3).toList(), stroke.contourStarts)
        assertTrue(stroke.contourStarts.zipWithNext().all { (start, end) -> end - start == 3 })
    }
}
