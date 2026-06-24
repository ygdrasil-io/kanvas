package org.graphiks.kanvas.gpu.renderer.vertices

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerticesExecutorTest {
    @Test
    fun `execute with triangle vertices returns expected stats`() {
        val vertices = listOf(0f, 0f, 16f, 0f, 0f, 16f)
        val stats = VerticesExecutor().execute(vertices, null, GPUVertexMode.Triangles)

        assertEquals(3, stats.vertexCount)
        assertEquals(0, stats.colorCount)
        assertEquals(GPUVertexMode.Triangles, stats.primitiveMode)
        assertTrue(stats.executed)
    }

    @Test
    fun `execute with vertex colors returns expected stats`() {
        val vertices = listOf(0f, 0f, 16f, 0f, 0f, 16f)
        val colors = listOf(1f, 0f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 0f, 1f, 1f)
        val stats = VerticesExecutor().execute(vertices, colors, GPUVertexMode.Triangles)

        assertEquals(3, stats.vertexCount)
        assertEquals(3, stats.colorCount)
        assertTrue(stats.executed)
    }

    @Test
    fun `execute with triangle strip accepts valid input`() {
        val vertices = listOf(0f, 0f, 16f, 0f, 0f, 16f, 16f, 16f)
        val stats = VerticesExecutor().execute(vertices, null, GPUVertexMode.TriangleStrip)

        assertEquals(4, stats.vertexCount)
        assertEquals(GPUVertexMode.TriangleStrip, stats.primitiveMode)
        assertTrue(stats.executed)
    }

    @Test
    fun `execute refusal with triangle fan`() {
        val vertices = listOf(0f, 0f, 16f, 0f, 0f, 16f)
        val exception = runCatching {
            VerticesExecutor().execute(vertices, null, GPUVertexMode.TriangleFan)
        }.exceptionOrNull()

        assertTrue(exception?.message?.contains("TriangleFan") == true)
    }

    @Test
    fun `execute rejects insufficient vertices`() {
        val vertices = listOf(0f, 0f)
        val exception = runCatching {
            VerticesExecutor().execute(vertices, null, GPUVertexMode.Triangles)
        }.exceptionOrNull()

        assertTrue(exception?.message?.contains("at least 6 floats") == true)
    }

    @Test
    fun `execute rejects odd number of floats`() {
        val vertices = listOf(0f, 0f, 16f, 0f, 0f, 16f, 0f)
        val exception = runCatching {
            VerticesExecutor().execute(vertices, null, GPUVertexMode.Triangles)
        }.exceptionOrNull()

        assertTrue(exception?.message?.contains("pairs") == true)
    }

    @Test
    fun `execute rejects color count mismatch`() {
        val vertices = listOf(0f, 0f, 16f, 0f, 0f, 16f)
        val colors = listOf(1f, 0f, 0f, 1f)
        val exception = runCatching {
            VerticesExecutor().execute(vertices, colors, GPUVertexMode.Triangles)
        }.exceptionOrNull()

        assertTrue(exception?.message?.contains("Color count") == true)
    }

    @Test
    fun `execute rejects non RGBA color count`() {
        val vertices = listOf(0f, 0f, 16f, 0f, 0f, 16f)
        val colors = listOf(1f, 0f, 0f)
        val exception = runCatching {
            VerticesExecutor().execute(vertices, colors, GPUVertexMode.Triangles)
        }.exceptionOrNull()

        assertTrue(exception?.message?.contains("RGBA") == true)
    }

    @Test
    fun `nonClaim line is present in stats`() {
        val vertices = listOf(0f, 0f, 16f, 0f, 0f, 16f)
        val stats = VerticesExecutor().execute(vertices, null, GPUVertexMode.Triangles)

        assertTrue(stats.nonClaimLine.isNotBlank())
        assertTrue(stats.nonClaimLine.contains("verticesExecutionSupported=true"))
        assertFalse(stats.nonClaimLine.contains("productActivation=true"))
    }
}
