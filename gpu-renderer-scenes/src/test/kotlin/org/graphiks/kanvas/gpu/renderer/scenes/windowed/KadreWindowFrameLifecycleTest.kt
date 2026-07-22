package org.graphiks.kanvas.gpu.renderer.scenes.windowed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KadreWindowFrameLifecycleTest {
    @Test
    fun `close requested in flight stops redraws and defers resource close until completion`() {
        val lifecycle = KadreWindowFrameLifecycle()

        assertTrue(lifecycle.beginFrame())
        assertEquals(
            KadreWindowLifecycleAction.AwaitFrameCompletion,
            lifecycle.requestClose(),
        )
        assertFalse(lifecycle.canRequestRedraw)
        assertFalse(lifecycle.beginFrame())
        assertEquals(
            KadreWindowLifecycleAction.AwaitFrameCompletion,
            lifecycle.destroySurfaces(),
        )

        assertEquals(
            KadreWindowLifecycleAction.CloseResources,
            lifecycle.frameCompleted(),
        )
        assertEquals(KadreWindowLifecycleAction.None, lifecycle.frameCompleted())
    }

    @Test
    fun `completion without close returns lifecycle to redrawable idle state`() {
        val lifecycle = KadreWindowFrameLifecycle()

        assertTrue(lifecycle.beginFrame())
        assertEquals(KadreWindowLifecycleAction.RequestRedraw, lifecycle.frameCompleted())
        assertTrue(lifecycle.canRequestRedraw)
        assertEquals(KadreWindowLifecycleAction.CloseResources, lifecycle.requestClose())
        assertFalse(lifecycle.canRequestRedraw)
    }
}
