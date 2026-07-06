package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLease
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseCacheResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GPUQueueManagerTest {
    @Test
    fun `queue manager retains lease resources while submission is pending`() {
        val manager = GPUQueueManager()
        val lease = queueLease("uniform-slab:frame-1")

        val submission = manager.submitLeases(
            label = "frame-1",
            retainedLeases = listOf(lease),
        )

        assertEquals(listOf(submission.id), manager.pendingSubmissionIds())
        assertEquals(listOf(GPUQueuedResourceRef("lease:uniform-slab:frame-1")), manager.retainedResources(submission.id))
        assertEquals(emptyList(), manager.releaseCompleted())

        val dump = manager.telemetry.dumpLines().joinToString("\n")
        assertTrue(dump.contains("submitted=1 completed=0 released=0 pending=1 waits=0 unknownCompletions=0"))
        assertTrue(dump.contains("completion=pending"))
    }

    @Test
    fun `queue manager releases resources once after readback completion`() {
        val manager = GPUQueueManager()
        val resource = GPUQueuedResourceRef("readback:frame-1")

        val submission = manager.submit(
            label = "offscreen-pass:frame-1",
            retainedResources = listOf(resource),
        )

        assertTrue(manager.markCompleted(submission.id, GPU_QUEUE_COMPLETION_READBACK_COMPLETE))
        assertEquals(listOf(resource), manager.releaseCompleted())
        assertEquals(emptyList(), manager.releaseCompleted())
        assertTrue(manager.retainedResources(submission.id).isEmpty())

        val dump = manager.telemetry.dumpLines().joinToString("\n")
        assertTrue(dump.contains("submitted=1 completed=1 released=1 pending=0 waits=0 unknownCompletions=0"))
        assertTrue(dump.contains("completion=readback-complete"))
        assertFalse(dump.contains("@"))
        assertFalse(dump.contains("0x"))
        assertFalse(dump.contains("W" + "GPU"))
        assertFalse(dump.contains("w" + "gpu"))
    }

    @Test
    fun `queue manager records presented completion reason`() {
        val manager = GPUQueueManager()

        val submission = manager.submit(
            label = "window-frame:frame-1",
            retainedResources = listOf(GPUQueuedResourceRef("target:window-1")),
        )

        assertTrue(manager.markCompleted(submission.id, GPU_QUEUE_COMPLETION_PRESENTED))
        assertEquals(listOf(GPUQueuedResourceRef("target:window-1")), manager.releaseCompleted())

        val dump = manager.telemetry.dumpLines().joinToString("\n")
        assertTrue(dump.contains("label=window-frame:frame-1"))
        assertTrue(dump.contains("completion=presented"))
    }

    @Test
    fun `queue manager pending ids can be filtered by label prefix`() {
        val manager = GPUQueueManager()
        val first = manager.submit(
            label = "offscreen-pass:frame-1",
            retainedResources = listOf(GPUQueuedResourceRef("target:offscreen-1")),
        )
        manager.submit(
            label = "window-frame:frame-2",
            retainedResources = listOf(GPUQueuedResourceRef("target:window-1")),
        )

        assertEquals(listOf(first.id), manager.pendingSubmissionIds(labelPrefix = "offscreen-pass:"))
    }

    @Test
    fun `queue manager rejects unsafe pending id prefix`() {
        val manager = GPUQueueManager()
        val submission = manager.submit(
            label = "frame-1",
            retainedResources = listOf(GPUQueuedResourceRef("target:frame-1")),
        )
        assertTrue(manager.pendingSubmissionIds().contains(submission.id))

        assertFailsWith<IllegalArgumentException> {
            manager.pendingSubmissionIds(labelPrefix = "bad prefix")
        }
    }

    @Test
    fun `queue manager telemetry records waits and unknown completions`() {
        val manager = GPUQueueManager()

        manager.recordWait()
        assertFalse(manager.markCompleted(GPUQueueSubmissionId(99), GPU_QUEUE_COMPLETION_READBACK_COMPLETE))

        val dump = manager.telemetry.dumpLines().joinToString("\n")
        assertTrue(dump.contains("waits=1"))
        assertTrue(dump.contains("unknownCompletions=1"))
    }

    @Test
    fun `queue manager rejects unsafe completion reasons`() {
        val manager = GPUQueueManager()
        val submission = manager.submit(
            label = "frame-1",
            retainedResources = listOf(GPUQueuedResourceRef("target:frame-1")),
        )

        assertFailsWith<IllegalArgumentException> {
            manager.markCompleted(submission.id, "bad reason")
        }
    }

}

private fun queueLease(leaseId: String): GPUResourceLease =
    GPUResourceLease(
        leaseId = leaseId,
        resourceKind = GPUResourceLeaseKind.UniformSlab,
        deviceGeneration = 11,
        descriptorHash = "sha256:uniform-slab-frame-1",
        ownerScope = "frame-1",
        usageLabels = listOf("copy_dst", "uniform"),
        releasePolicy = "submission-complete",
        cacheResult = GPUResourceLeaseCacheResult.Create,
    )
