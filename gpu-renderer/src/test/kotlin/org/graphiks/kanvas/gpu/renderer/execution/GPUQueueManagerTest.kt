package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GPUQueueManagerTest {
    @Test
    fun `queue manager retains resources until completion`() {
        val manager = GPUQueueManager()
        val resource = GPUQueuedResourceRef("uniform-slab:frame-1")

        val submission = manager.submit(
            label = "frame-1",
            retainedResources = listOf(resource),
        )

        assertEquals(GPUQueueSubmissionId(1), submission.id)
        assertEquals(listOf(resource), manager.retainedResources(submission.id))
        assertFalse(manager.releaseCompleted().contains(resource))

        assertTrue(manager.markCompleted(submission.id))
        assertEquals(listOf(resource), manager.releaseCompleted())
        assertTrue(manager.retainedResources(submission.id).isEmpty())
    }

    @Test
    fun `queue manager telemetry is stable and dump safe`() {
        val manager = GPUQueueManager()

        val submission = manager.submit(
            label = "frame-1",
            retainedResources = listOf(GPUQueuedResourceRef("readback:frame-1")),
        )

        assertTrue(manager.markCompleted(submission.id))
        assertEquals(listOf(GPUQueuedResourceRef("readback:frame-1")), manager.releaseCompleted())

        val dump = manager.telemetry.dumpLines().joinToString("\n")

        assertTrue(
            dump.contains("gpu-queue.telemetry submitted=1 completed=1 released=1 waits=0 unknownCompletions=0"),
        )
        assertTrue(
            dump.contains(
                "gpu-queue.submission id=1 label=frame-1 retained=1 completed=true released=true",
            ),
        )
        assertFalse(dump.contains("@"))
    }

    @Test
    fun `queue manager ignores completion for unknown submission id`() {
        val manager = GPUQueueManager()

        assertFalse(manager.markCompleted(GPUQueueSubmissionId(99)))

        val dump = manager.telemetry.dumpLines().joinToString("\n")

        assertTrue(dump.contains("unknownCompletions=1"))
    }
}
