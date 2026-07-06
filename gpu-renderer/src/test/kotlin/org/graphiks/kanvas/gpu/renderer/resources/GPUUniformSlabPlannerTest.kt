package org.graphiks.kanvas.gpu.renderer.resources

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

/** Verifies backend-neutral uniform slab layout planning. */
class GPUUniformSlabPlannerTest {
    @Test
    fun `planner aligns payload slots and emits dump-safe facts`() {
        val result = GPUUniformSlabPlanner.plan(
            sourceLabel = "fullscreen-uniform-pass",
            deviceGeneration = 7L,
            alignmentBytes = 256L,
            uploadBudgetBytes = 1024L,
            payloads = listOf(
                GPUUniformSlabPayload(slotLabel = "draw-0", bytes = byteArrayOf(1, 2, 3, 4)),
                GPUUniformSlabPayload(slotLabel = "draw-1", bytes = ByteArray(16) { 9 }),
                GPUUniformSlabPayload(slotLabel = "draw-2", bytes = ByteArray(48) { 3 }),
            ),
        )

        val accepted = assertIs<GPUUniformSlabPlanningResult.Accepted>(result)
        val plan = accepted.plan

        assertEquals("fullscreen-uniform-pass", plan.sourceLabel)
        assertEquals(7L, plan.deviceGeneration)
        assertEquals(256L, plan.alignmentBytes)
        assertEquals(768L, plan.totalBytes)
        assertEquals(listOf(0L, 256L, 512L), plan.slots.map { slot -> slot.alignedOffset })
        assertEquals(listOf(256L, 256L, 256L), plan.slots.map { slot -> slot.allocatedBytes })
        assertEquals(listOf(4L, 16L, 48L), plan.slots.map { slot -> slot.payloadBytes })
        assertEquals(listOf("draw-0", "draw-1", "draw-2"), plan.slots.map { slot -> slot.slotLabel })

        val dump = plan.dumpLines()
        assertContains(
            dump,
            "uniform-slab.plan source=fullscreen-uniform-pass deviceGeneration=7 " +
                "alignment=256 totalBytes=768 uploadBudgetBytes=1024 slots=3 hash=${plan.planHash}",
        )
        assertContains(
            dump,
            "uniform-slab.slot source=fullscreen-uniform-pass slot=draw-1 offset=256 " +
                "payloadBytes=16 allocatedBytes=256 payloadHash=${plan.slots[1].payloadHash}",
        )
        assertFalse(dump.joinToString("\n").contains("@"))
        assertFalse(dump.joinToString("\n").contains("0x"))
    }

    @Test
    fun `planner hash is stable and changes when payload bytes change`() {
        val first = acceptedPlan(payloadByte = 42)
        val second = acceptedPlan(payloadByte = 42)
        val changed = acceptedPlan(payloadByte = 43)

        assertEquals(first.planHash, second.planHash)
        assertEquals(first.slots.map { slot -> slot.payloadHash }, second.slots.map { slot -> slot.payloadHash })
        assertNotEquals(first.planHash, changed.planHash)
        assertNotEquals(first.slots.single().payloadHash, changed.slots.single().payloadHash)
    }

    @Test
    fun `planner refuses empty payload budget overflow invalid alignment stale generation and dump unsafe labels`() {
        assertRefused(
            result = GPUUniformSlabPlanner.plan(
                sourceLabel = "fullscreen-uniform-pass",
                deviceGeneration = 1L,
                alignmentBytes = 256L,
                uploadBudgetBytes = 1024L,
                payloads = listOf(GPUUniformSlabPayload(slotLabel = "draw-0", bytes = byteArrayOf())),
            ),
            expectedCode = "unsupported.uniform_slab_empty_payload",
        )
        assertRefused(
            result = GPUUniformSlabPlanner.plan(
                sourceLabel = "fullscreen-uniform-pass",
                deviceGeneration = 1L,
                alignmentBytes = 256L,
                uploadBudgetBytes = 128L,
                payloads = listOf(GPUUniformSlabPayload(slotLabel = "draw-0", bytes = ByteArray(16) { 1 })),
            ),
            expectedCode = "unsupported.uniform_slab_budget_exceeded",
        )
        assertRefused(
            result = GPUUniformSlabPlanner.plan(
                sourceLabel = "fullscreen-uniform-pass",
                deviceGeneration = 1L,
                alignmentBytes = 0L,
                uploadBudgetBytes = 1024L,
                payloads = listOf(GPUUniformSlabPayload(slotLabel = "draw-0", bytes = ByteArray(16) { 1 })),
            ),
            expectedCode = "unsupported.uniform_slab_alignment_invalid",
        )
        assertRefused(
            result = GPUUniformSlabPlanner.plan(
                sourceLabel = "fullscreen-uniform-pass",
                deviceGeneration = -1L,
                alignmentBytes = 256L,
                uploadBudgetBytes = 1024L,
                payloads = listOf(GPUUniformSlabPayload(slotLabel = "draw-0", bytes = ByteArray(16) { 1 })),
            ),
            expectedCode = "unsupported.uniform_slab_stale_generation",
        )
        assertRefused(
            result = GPUUniformSlabPlanner.plan(
                sourceLabel = "fullscreen@uniform",
                deviceGeneration = 1L,
                alignmentBytes = 256L,
                uploadBudgetBytes = 1024L,
                payloads = listOf(GPUUniformSlabPayload(slotLabel = "draw-0", bytes = ByteArray(16) { 1 })),
            ),
            expectedCode = "unsupported.uniform_slab_dump_unsafe",
        )
    }

    private fun acceptedPlan(payloadByte: Byte): GPUUniformSlabPlan =
        assertIs<GPUUniformSlabPlanningResult.Accepted>(
            GPUUniformSlabPlanner.plan(
                sourceLabel = "fullscreen-uniform-pass",
                deviceGeneration = 3L,
                alignmentBytes = 256L,
                uploadBudgetBytes = 1024L,
                payloads = listOf(
                    GPUUniformSlabPayload(slotLabel = "draw-0", bytes = byteArrayOf(payloadByte)),
                ),
            ),
        ).plan

    private fun assertRefused(
        result: GPUUniformSlabPlanningResult,
        expectedCode: String,
    ) {
        val refused = assertIs<GPUUniformSlabPlanningResult.Refused>(result)
        assertEquals(expectedCode, refused.diagnostic.code)
        val dump = refused.dumpLines().joinToString("\n")
        assertContains(dump, "uniform-slab.refused code=$expectedCode")
        assertFalse(dump.contains("@"))
    }
}
