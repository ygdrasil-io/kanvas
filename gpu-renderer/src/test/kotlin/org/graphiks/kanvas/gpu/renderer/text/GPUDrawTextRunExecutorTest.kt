package org.graphiks.kanvas.gpu.renderer.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GPUDrawTextRunExecutorTest {
    @Test
    fun `execute returns refused result for empty sub runs`() {
        val executor = GPUDrawTextRunExecutor()
        val result = executor.execute(emptyList())
        assertFalse(result.accepted)
        assertEquals("no sub-runs to execute", result.diagnostic)
    }

    @Test
    fun `execute produces batches from sub run plans`() {
        val subRuns = listOf(
            GPUTextSubRunPlan(
                representation = "A8MaskAtlas",
                glyphRange = 0..5,
                boundsLabel = "bounds-1",
                atlasRefs = listOf("a8-atlas-key"),
                instancePlan = GPUTextInstancePlan(
                    instanceCount = 6,
                    instanceLayoutHash = "inst-layout-v1",
                    payloadHash = "payload-1",
                ),
                ordering = GPUTextOrderingToken("run-1"),
            ),
        )
        val executor = GPUDrawTextRunExecutor()
        val result = executor.execute(subRuns)
        assertTrue(result.accepted)
        assertEquals(1, result.subRunBatches.size)
        assertEquals("a8-atlas-key", result.atlasKey)
        assertEquals("A8MaskAtlas", result.subRunBatches[0].subRunLabel)
        assertEquals(0..5, result.subRunBatches[0].glyphRange)
    }

    @Test
    fun `executor label is GPUDrawTextRunExecutor`() {
        val subRuns = listOf(
            GPUTextSubRunPlan(
                representation = "A8MaskAtlas",
                glyphRange = 0..2,
                boundsLabel = "bounds-2",
                atlasRefs = listOf("atlas-key-2"),
                instancePlan = GPUTextInstancePlan(
                    instanceCount = 3,
                    instanceLayoutHash = "inst-layout-v2",
                    payloadHash = "payload-2",
                ),
                ordering = GPUTextOrderingToken("run-2"),
            ),
        )
        val executor = GPUDrawTextRunExecutor()
        val result = executor.execute(subRuns)
        assertEquals("GPUDrawTextRunExecutor", result.executorLabel)
    }
}
