package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.geometry.Point
import org.graphiks.kanvas.paint.StrokeJoin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GPUPathStrokeInputTest {
    @Test
    fun `dash application carries interval progress across polyline segments`() {
        val dashed = applyDash(
            points = listOf(
                0f to 0f,
                3f to 0f,
                6f to 0f,
                9f to 0f,
                12f to 0f,
            ),
            dashArray = floatArrayOf(5f, 5f),
            phase = 0f,
        )

        val coveredLength = dashed.chunked(2).sumOf { (start, end) ->
            kotlin.math.abs(end.first - start.first).toDouble()
        }

        assertEquals(7.0, coveredLength, 1e-6)
        assertEquals(0f, dashed.first().first)
        assertEquals(12f, dashed.last().first)
        assertTrue(dashed.chunked(2).all { (start, end) ->
            end.first <= 5f || start.first >= 10f
        })
    }

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

    @Test
    fun `dashed closed stroke geometry emits triangle contours instead of one filled fan`() {
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
            dashArray = floatArrayOf(5f, 5f),
        )

        val vertexCount = stroke.vertices.size / 2
        assertTrue(vertexCount > 3)
        assertEquals(vertexCount, stroke.contourStarts.last())
        assertTrue(stroke.contourStarts.zipWithNext().all { (start, end) -> end - start == 3 })
    }
}
