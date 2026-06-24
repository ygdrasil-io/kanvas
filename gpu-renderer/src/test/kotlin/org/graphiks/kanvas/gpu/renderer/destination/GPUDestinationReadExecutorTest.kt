package org.graphiks.kanvas.gpu.renderer.destination

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class GPUDestinationReadExecutorTest {

    @Test
    fun `copy strategy splits pass and copies target`() {
        val executor = GPUDestinationReadExecutor()

        val stats = executor.executeCopyStrategy(
            sourceLabel = "main-target",
            width = 320,
            height = 200,
            format = "rgba8unorm",
        )

        assertTrue(stats.passSplit)
        assertTrue(stats.copyPerformed)
        assertEquals("main-target", stats.sourceLabel)
    }

    @Test
    fun `bind intermediate strategy uses existing texture`() {
        val executor = GPUDestinationReadExecutor()

        val stats = executor.executeBindIntermediate(
            intermediateLabel = "existing-intermediate",
            width = 320,
            height = 200,
        )

        assertTrue(stats.intermediateBound)
        assertFalse(stats.copyPerformed)
        assertEquals("existing-intermediate", stats.intermediateLabel)
    }
}
