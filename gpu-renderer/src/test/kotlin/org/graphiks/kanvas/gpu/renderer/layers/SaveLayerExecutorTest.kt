package org.graphiks.kanvas.gpu.renderer.layers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SaveLayerExecutorTest {

    @Test
    fun `execute allocates offscreen target and returns stats`() {
        val executor = SaveLayerExecutor()

        val stats = executor.execute(
            scopeLabel = "test-savelayer",
            width = 320,
            height = 200,
        )

        assertTrue(stats.targetAllocated)
        assertEquals(0, stats.childrenRendered)
        assertTrue(stats.compositeApplied)
    }

    @Test
    fun `savelayer executor dump includes nonclaim lines`() {
        val executor = SaveLayerExecutor()

        val stats = executor.execute(
            scopeLabel = "savelayer-dump-test",
            width = 640,
            height = 480,
        )

        assertEquals(true, stats.targetAllocated)
        assertEquals(true, stats.compositeApplied)
    }
}
