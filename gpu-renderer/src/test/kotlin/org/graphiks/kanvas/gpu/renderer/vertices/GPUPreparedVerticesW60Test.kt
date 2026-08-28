package org.graphiks.kanvas.gpu.renderer.vertices

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GPUPreparedVerticesW60Test {
    private val limits = GPUPreparedVerticesPackingLimits(100, 300, 4096, 4096, 300)

    @Test
    fun `positions colors texcoords indices and bounds form one owned artifact`() {
        val positions = floatArrayOf(-1f, -2f, 3f, 4f, 0f, 1f)
        val colors = byteArrayOf(-1, 0, 0, -1, 0, -1, 0, 127, 0, 0, -1, -1)
        val texcoords = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f)
        val indices = intArrayOf(0, 1, 2)
        val result = GPUPreparedVerticesPacker.pack(
            GPUPreparedVerticesArtifactInput(GPUVertexMode.Triangles, positions, colors, texcoords, indices, "w60"),
            limits, supportsUint32Index = true,
        )
        val ready = assertIs<GPUPreparedVerticesPackingResult.Ready>(result)
        val vertexBytesBeforeMutation = ready.artifact.vertexBytesForUpload()
        val indexBytesBeforeMutation = ready.artifact.indexBytesForUpload()
        assertEquals(GPUVertexMode.Triangles, ready.artifact.topology)
        assertEquals(-1f, ready.sourceBounds.left)
        assertEquals(4f, ready.sourceBounds.bottom)
        positions[0] = 99f; colors[0] = 0; texcoords[0] = 99f; indices[0] = 2
        assertContentEquals(vertexBytesBeforeMutation, ready.artifact.vertexBytesForUpload())
        assertContentEquals(indexBytesBeforeMutation, ready.artifact.indexBytesForUpload())
        assertTrue(vertexBytesBeforeMutation.isNotEmpty())
        assertEquals("w60", ready.artifact.provenance)
    }

    @Test
    fun `out of range indices incoherent attributes and budgets refuse stably`() {
        val outOfRange = GPUPreparedVerticesPacker.pack(
            GPUPreparedVerticesArtifactInput(GPUVertexMode.Triangles, floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f), null, null, intArrayOf(0, 1, 3), "w60"), limits, true,
        ) as GPUPreparedVerticesPackingResult.Refused
        assertEquals(GPUPreparedVerticesRefusalCodes.IndexOutOfRange, outOfRange.code)
        val incoherent = GPUPreparedVerticesPacker.pack(
            GPUPreparedVerticesArtifactInput(GPUVertexMode.Triangles, floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f), null, floatArrayOf(0f), null, "w60"), limits, true,
        ) as GPUPreparedVerticesPackingResult.Refused
        assertEquals(GPUPreparedVerticesRefusalCodes.AttributeCount, incoherent.code)
        val budget = GPUPreparedVerticesPacker.pack(
            GPUPreparedVerticesArtifactInput(GPUVertexMode.Triangles, floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f), null, null, null, "w60"), limits.copy(maxVertices = 2), true,
        ) as GPUPreparedVerticesPackingResult.Refused
        assertEquals(GPUPreparedVerticesRefusalCodes.Budget, budget.code)
    }
}
