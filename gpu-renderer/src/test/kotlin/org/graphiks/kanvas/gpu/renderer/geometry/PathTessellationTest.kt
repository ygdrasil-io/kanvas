package org.graphiks.kanvas.gpu.renderer.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathTessellationTest {
    @Test
    fun `triangle tessellates to single fan with three vertices`() {
        val vertices = listOf(0f, 0f, 16f, 0f, 8f, 16f)
        val result = GPUBasicPathFillPreparedPlanner().tessellate(
            flattenedVertices = vertices,
            contourStarts = listOf(0),
            edgeCount = 3,
        )

        assertTrue(result.accepted)
        assertEquals(3, result.vertexCount)
        assertEquals(1, result.triangleCount)
        assertEquals(listOf(0, 1, 2), result.indexBuffer)
        assertEquals(vertices, result.vertexBuffer)
    }

    @Test
    fun `quad tessellates to two triangles`() {
        val vertices = listOf(0f, 0f, 16f, 0f, 16f, 16f, 0f, 16f)
        val result = GPUBasicPathFillPreparedPlanner().tessellate(
            flattenedVertices = vertices,
            contourStarts = listOf(0),
            edgeCount = 4,
        )

        assertTrue(result.accepted)
        assertEquals(4, result.vertexCount)
        assertEquals(2, result.triangleCount)
        assertEquals(listOf(0, 1, 2, 0, 2, 3), result.indexBuffer)
    }

    @Test
    fun `degenerate path with fewer than three vertices refuses`() {
        val result = GPUBasicPathFillPreparedPlanner().tessellate(
            flattenedVertices = listOf(0f, 0f, 16f, 0f),
            contourStarts = listOf(0),
            edgeCount = 2,
        )

        assertFalse(result.accepted)
        assertEquals("unsupported.geometry.path_degenerate", result.refusalCode)
    }

    @Test
    fun `path exceeding max edge budget refuses`() {
        val vertices = (0 until 258).flatMap { listOf(it.toFloat(), 0f) }
        val result = GPUBasicPathFillPreparedPlanner().tessellate(
            flattenedVertices = vertices,
            contourStarts = listOf(0),
            edgeCount = 258,
        )

        assertFalse(result.accepted)
        assertEquals("unsupported.path.edge_budget", result.refusalCode)
    }

    @Test
    fun `bounded path at exactly max edges accepts`() {
        val vertices = (0 until 256).flatMap { listOf(it.toFloat(), 0f) }
        val result = GPUBasicPathFillPreparedPlanner().tessellate(
            flattenedVertices = vertices,
            contourStarts = listOf(0),
            edgeCount = 256,
        )

        assertTrue(result.accepted)
        assertEquals(256, result.vertexCount)
    }

    @Test
    fun `multi-contour path tessellates each contour separately`() {
        val vertices = listOf(
            0f, 0f, 16f, 0f, 8f, 16f,
            24f, 0f, 40f, 0f, 32f, 16f,
        )
        val result = GPUBasicPathFillPreparedPlanner().tessellate(
            flattenedVertices = vertices,
            contourStarts = listOf(0, 3),
            edgeCount = 6,
        )

        assertTrue(result.accepted)
        assertEquals(6, result.vertexCount)
        assertEquals(2, result.triangleCount)
        assertEquals(listOf(0, 1, 2, 3, 4, 5), result.indexBuffer)
    }
}
