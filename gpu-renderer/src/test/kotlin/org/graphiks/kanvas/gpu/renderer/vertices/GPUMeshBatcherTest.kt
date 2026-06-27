package org.graphiks.kanvas.gpu.renderer.vertices

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GPUMeshBatcherTest {
    @Test
    fun `batch with single draw returns one batch`() {
        val draws = listOf(
            GPUDrawCallDescriptor(
                drawId = "draw-1",
                pipelineKey = "solid-fill",
                vertexCount = 3,
                topology = GPUVertexMode.Triangles,
                blendMode = "SrcOver",
                sortKey = 0,
            ),
        )
        val stats = GPUMeshBatcher().batch(draws)

        assertEquals(1, stats.inputDrawCount)
        assertEquals(1, stats.batchCount)
        assertEquals(0, stats.pipelineChangeCount)
        assertEquals(0, stats.mergedDrawCount)
    }

    @Test
    fun `batch merges adjacent same pipeline draws`() {
        val draws = listOf(
            GPUDrawCallDescriptor("draw-1", "solid-fill", 3, GPUVertexMode.Triangles, "SrcOver", 0),
            GPUDrawCallDescriptor("draw-2", "solid-fill", 3, GPUVertexMode.Triangles, "SrcOver", 1),
        )
        val stats = GPUMeshBatcher().batch(draws)

        assertEquals(2, stats.inputDrawCount)
        assertEquals(1, stats.batchCount)
        assertEquals(0, stats.pipelineChangeCount)
        assertEquals(1, stats.mergedDrawCount)
    }

    @Test
    fun `batch splits on pipeline key change`() {
        val draws = listOf(
            GPUDrawCallDescriptor("draw-1", "solid-fill", 3, GPUVertexMode.Triangles, "SrcOver", 0),
            GPUDrawCallDescriptor("draw-2", "gradient-fill", 3, GPUVertexMode.Triangles, "SrcOver", 1),
        )
        val stats = GPUMeshBatcher().batch(draws)

        assertEquals(2, stats.inputDrawCount)
        assertEquals(2, stats.batchCount)
        assertEquals(1, stats.pipelineChangeCount)
        assertEquals(0, stats.mergedDrawCount)
    }

    @Test
    fun `batch splits on topology change`() {
        val draws = listOf(
            GPUDrawCallDescriptor("draw-1", "solid-fill", 3, GPUVertexMode.Triangles, "SrcOver", 0),
            GPUDrawCallDescriptor("draw-2", "solid-fill", 3, GPUVertexMode.TriangleStrip, "SrcOver", 1),
        )
        val stats = GPUMeshBatcher().batch(draws)

        assertEquals(2, stats.inputDrawCount)
        assertEquals(2, stats.batchCount)
        assertEquals(1, stats.pipelineChangeCount)
    }

    @Test
    fun `batch splits on blend mode change`() {
        val draws = listOf(
            GPUDrawCallDescriptor("draw-1", "solid-fill", 3, GPUVertexMode.Triangles, "SrcOver", 0),
            GPUDrawCallDescriptor("draw-2", "solid-fill", 3, GPUVertexMode.Triangles, "Multiply", 1),
        )
        val stats = GPUMeshBatcher().batch(draws)

        assertEquals(2, stats.inputDrawCount)
        assertEquals(2, stats.batchCount)
    }

    @Test
    fun `batch sorts by sortKey then pipelineKey`() {
        val draws = listOf(
            GPUDrawCallDescriptor("draw-b", "gradient-fill", 3, GPUVertexMode.Triangles, "SrcOver", 2),
            GPUDrawCallDescriptor("draw-a", "solid-fill", 3, GPUVertexMode.Triangles, "SrcOver", 1),
        )
        val stats = GPUMeshBatcher().batch(draws)

        assertEquals(2, stats.inputDrawCount)
        assertEquals(2, stats.batchCount)
    }

    @Test
    fun `batch rejects empty draws`() {
        val exception = runCatching {
            GPUMeshBatcher().batch(emptyList())
        }.exceptionOrNull()

        assertTrue(exception?.message?.contains("at least one draw") == true)
    }

    @Test
    fun `nonClaim line is present in stats`() {
        val draws = listOf(
            GPUDrawCallDescriptor("draw-1", "solid-fill", 3, GPUVertexMode.Triangles, "SrcOver", 0),
        )
        val stats = GPUMeshBatcher().batch(draws)

        assertTrue(stats.nonClaimLine.isNotBlank())
        assertTrue(stats.nonClaimLine.contains("batchingSupported=true"))
        assertTrue(stats.nonClaimLine.contains("productActivation=true"))
    }
}
