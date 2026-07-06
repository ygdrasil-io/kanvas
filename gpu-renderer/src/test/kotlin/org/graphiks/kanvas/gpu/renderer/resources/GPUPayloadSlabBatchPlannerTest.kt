package org.graphiks.kanvas.gpu.renderer.resources

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadFingerprint
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadUploadPlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingBlock
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadBlock
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot

/** Verifies backend-neutral payload slab batch planning contracts. */
class GPUPayloadSlabBatchPlannerTest {
    @Test
    fun `planner accepts compatible payload batch and maps slab slots back to payload facts`() {
        val result = GPUPayloadSlabBatchPlanner.plan(
            batchRequest(
                payloadRequests = listOf(
                    payloadRequest(index = 0),
                    payloadRequest(index = 1),
                    payloadRequest(index = 2),
                ),
            ),
        )

        val accepted = assertIs<GPUPayloadSlabBatchPlanningResult.Accepted>(result)
        val plan = accepted.plan

        assertEquals("root-target", plan.targetId)
        assertEquals("frame-1", plan.frameId)
        assertEquals("payload-slab-source", plan.sourceLabel)
        assertEquals(11L, plan.deviceGeneration)
        assertEquals(768L, plan.uniformSlabPlan.totalBytes)
        assertEquals(listOf(0L, 256L, 512L), plan.slotBindings.map { binding -> binding.alignedOffset })
        assertEquals(listOf(64L, 64L, 64L), plan.slotBindings.map { binding -> binding.payloadBytes })
        assertEquals(listOf("packet-0", "packet-1", "packet-2"), plan.slotBindings.map { binding -> binding.packetId })
        assertEquals(
            listOf("pass-a:uniform:0", "pass-a:uniform:1", "pass-a:uniform:2"),
            plan.slotBindings.map { binding -> binding.uniformSlotId },
        )
        assertEquals(
            listOf("pass-a:resource:0", "pass-a:resource:1", "pass-a:resource:2"),
            plan.slotBindings.map { binding -> binding.resourceSlotId },
        )
        assertEquals(
            listOf("layout-solid-v1", "layout-solid-v1", "layout-solid-v1"),
            plan.slotBindings.map { binding -> binding.reflectedBindingLayoutHash },
        )
        assertEquals(
            listOf("uniform-fingerprint-0", "uniform-fingerprint-1", "uniform-fingerprint-2"),
            plan.slotBindings.map { binding -> binding.payloadFingerprint },
        )
        assertEquals(
            listOf(
                "packet-0:pass-a:uniform:0:pass-a:resource:0",
                "packet-1:pass-a:uniform:1:pass-a:resource:1",
                "packet-2:pass-a:uniform:2:pass-a:resource:2",
            ),
            plan.slotBindings.map { binding -> binding.slotLabel },
        )
        assertEquals(
            plan.slotBindings.map { binding -> binding.slotLabel },
            plan.uniformSlabPlan.slots.map { slot -> slot.slotLabel },
        )
    }

    @Test
    fun `dump lines are deterministic and backend-neutral`() {
        val plan = acceptedPlan()

        val dump = plan.dumpLines()
        assertContains(
            dump,
            "payload-slab.batch.plan source=payload-slab-source target=root-target frame=frame-1 " +
                "deviceGeneration=11 slots=3 totalBytes=768 hash=${plan.planHash}",
        )
        assertContains(
            dump,
            "payload-slab.batch.slot source=payload-slab-source " +
                "slot=packet-1:pass-a:uniform:1:pass-a:resource:1 packet=packet-1 " +
                "uniformSlot=pass-a:uniform:1 resourceSlot=pass-a:resource:1 offset=256 payloadBytes=64 " +
                "payloadFingerprint=uniform-fingerprint-1 layout=layout-solid-v1",
        )
        assertTrue(dump.any { line -> line.startsWith("uniform-slab.plan ") })
        assertFalse(dump.joinToString("\n").contains("@"))
        assertFalse(dump.joinToString("\n").contains("0x"))
        assertFalse(dump.joinToString("\n").contains("WGPU"))
    }

    @Test
    fun `plan hash is stable and reacts to payload and layout changes`() {
        val first = acceptedPlan()
        val second = acceptedPlan()
        val changedBytes = acceptedPlan(
            batchRequest(
                payloadRequests = listOf(
                    payloadRequest(index = 0),
                    payloadRequest(index = 1, bytes = List(64) { 9 }),
                    payloadRequest(index = 2),
                ),
            ),
        )
        val changedLayout = acceptedPlan(
            batchRequest(
                payloadRequests = listOf(
                    payloadRequest(index = 0, reflectedBindingLayoutHash = "layout-solid-v2"),
                    payloadRequest(index = 1, reflectedBindingLayoutHash = "layout-solid-v2"),
                    payloadRequest(index = 2, reflectedBindingLayoutHash = "layout-solid-v2"),
                ),
            ),
        )

        assertEquals(first.planHash, second.planHash)
        assertNotEquals(first.planHash, changedBytes.planHash)
        assertNotEquals(first.planHash, changedLayout.planHash)
    }

    @Test
    fun `planner refuses invalid batch inputs with stable diagnostics`() {
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(payloadRequests = emptyList()),
            ),
            expectedCode = "unsupported.payload_slab_empty_batch",
        )
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(
                    payloadRequests = listOf(
                        payloadRequest(index = 0),
                        payloadRequest(index = 1, targetId = "other-target"),
                    ),
                ),
            ),
            expectedCode = "unsupported.payload_slab_target_mismatch",
        )
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(
                    payloadRequests = listOf(
                        payloadRequest(index = 0),
                        payloadRequest(index = 1, deviceGeneration = 12L),
                    ),
                ),
            ),
            expectedCode = "unsupported.payload_slab_generation_mismatch",
        )
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(
                    payloadRequests = listOf(
                        payloadRequest(index = 0),
                        payloadRequest(index = 1, reflectedBindingLayoutHash = "layout-other-v1"),
                    ),
                ),
            ),
            expectedCode = "unsupported.payload_slab_layout_mismatch",
        )
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(
                    payloadRequests = listOf(
                        payloadRequest(index = 0),
                        payloadRequest(index = 0, packetId = "packet-1"),
                    ),
                ),
            ),
            expectedCode = "unsupported.payload_slab_duplicate_slot",
        )
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(payloadRequests = listOf(payloadRequest(index = 0, bytes = emptyList(), byteSize = 0L))),
            ),
            expectedCode = "unsupported.payload_slab_empty_payload",
        )
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(
                    payloadRequests = listOf(
                        payloadRequest(
                            index = 0,
                            bytes = List(64) { 1 },
                            byteSize = 128L,
                        ),
                    ),
                ),
            ),
            expectedCode = "unsupported.payload_slab_uniform_missing",
        )
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(
                    payloadRequests = listOf(
                        payloadRequest(
                            index = 0,
                            bytes = List(257) { 1 },
                            byteSize = 257L,
                        ),
                    ),
                ),
            ),
            expectedCode = "unsupported.payload_slab_budget_exceeded",
        )
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                GPUPayloadSlabBatchRequest(
                    targetId = "root-target",
                    frameId = "frame-1",
                    sourceLabel = "payload@source",
                    deviceGeneration = 11L,
                    alignmentBytes = 256L,
                    uploadBudgetBytes = 1024L,
                    payloadRequests = listOf(payloadRequest(index = 0)),
                ),
            ),
            expectedCode = "unsupported.payload_slab_dump_unsafe",
        )
    }

    @Test
    fun `planner refuses short 0x handle-looking source labels`() {
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                GPUPayloadSlabBatchRequest(
                    targetId = "root-target",
                    frameId = "frame-1",
                    sourceLabel = "fullscreen-0x1",
                    deviceGeneration = 11L,
                    alignmentBytes = 256L,
                    uploadBudgetBytes = 1024L,
                    payloadRequests = listOf(payloadRequest(index = 0)),
                ),
            ),
            expectedCode = "unsupported.payload_slab_dump_unsafe",
        )
    }

    @Test
    fun `plan constructor rejects invalid slot coverage invariants`() {
        val uniformSlabPlan = GPUUniformSlabPlan(
            planHash = "uniform-slab-hash",
            sourceLabel = "payload-slab-source",
            deviceGeneration = 11L,
            alignmentBytes = 256L,
            totalBytes = 512L,
            uploadBudgetBytes = 1024L,
            slots = listOf(
                GPUUniformSlabSlot(
                    slotLabel = "packet-0:pass-a:uniform:0:pass-a:resource:0",
                    payloadHash = "payload-hash-0",
                    payloadBytes = 64L,
                    alignedOffset = 0L,
                    allocatedBytes = 256L,
                ),
                GPUUniformSlabSlot(
                    slotLabel = "packet-1:pass-a:uniform:1:pass-a:resource:1",
                    payloadHash = "payload-hash-1",
                    payloadBytes = 64L,
                    alignedOffset = 256L,
                    allocatedBytes = 256L,
                ),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            GPUPayloadSlabBatchPlan(
                planHash = "payload-slab-hash",
                sourceLabel = "payload-slab-source",
                targetId = "root-target",
                frameId = "frame-1",
                deviceGeneration = 11L,
                uniformSlabPlan = uniformSlabPlan,
                slotBindings = listOf(
                    GPUPayloadSlabSlotBinding(
                        slotLabel = "packet-0:pass-a:uniform:0:pass-a:resource:0",
                        packetId = "packet-0",
                        uniformSlotId = "pass-a:uniform:0",
                        resourceSlotId = "pass-a:resource:0",
                        payloadFingerprint = "uniform-fingerprint-0",
                        reflectedBindingLayoutHash = "layout-solid-v1",
                        alignedOffset = 0L,
                        payloadBytes = 64L,
                    ),
                ),
            )
        }

        assertFailsWith<IllegalArgumentException> {
            GPUPayloadSlabBatchPlan(
                planHash = "payload-slab-hash",
                sourceLabel = "payload-slab-source",
                targetId = "root-target",
                frameId = "frame-1",
                deviceGeneration = 11L,
                uniformSlabPlan = uniformSlabPlan,
                slotBindings = listOf(
                    GPUPayloadSlabSlotBinding(
                        slotLabel = "packet-0:pass-a:uniform:0:pass-a:resource:0",
                        packetId = "packet-0",
                        uniformSlotId = "pass-a:uniform:0",
                        resourceSlotId = "pass-a:resource:0",
                        payloadFingerprint = "uniform-fingerprint-0",
                        reflectedBindingLayoutHash = "layout-solid-v1",
                        alignedOffset = 128L,
                        payloadBytes = 64L,
                    ),
                    GPUPayloadSlabSlotBinding(
                        slotLabel = "packet-1:pass-a:uniform:1:pass-a:resource:1",
                        packetId = "packet-1",
                        uniformSlotId = "pass-a:uniform:1",
                        resourceSlotId = "pass-a:resource:1",
                        payloadFingerprint = "uniform-fingerprint-1",
                        reflectedBindingLayoutHash = "layout-solid-v1",
                        alignedOffset = 256L,
                        payloadBytes = 64L,
                    ),
                ),
            )
        }
    }

    private fun acceptedPlan(
        request: GPUPayloadSlabBatchRequest = batchRequest(
            payloadRequests = listOf(
                payloadRequest(index = 0),
                payloadRequest(index = 1),
                payloadRequest(index = 2),
            ),
        ),
    ): GPUPayloadSlabBatchPlan =
        assertIs<GPUPayloadSlabBatchPlanningResult.Accepted>(
            GPUPayloadSlabBatchPlanner.plan(request),
        ).plan

    private fun assertRefused(
        result: GPUPayloadSlabBatchPlanningResult,
        expectedCode: String,
    ) {
        val refused = assertIs<GPUPayloadSlabBatchPlanningResult.Refused>(result)
        assertEquals(expectedCode, refused.diagnostic.code)
        assertTrue(
            refused.dumpLines().single().contains(
                "payload-slab.batch.refused code=$expectedCode terminal=true facts=",
            ),
        )
        assertFalse(refused.dumpLines().joinToString("\n").contains("WGPU"))
    }

    private fun payloadRequest(
        index: Int,
        targetId: String = "root-target",
        packetId: String = "packet-$index",
        deviceGeneration: Long = 11L,
        reflectedBindingLayoutHash: String = "layout-solid-v1",
        bytes: List<Int> = List(64) { index + 1 },
        byteSize: Long = bytes.size.toLong(),
    ): GPUPayloadMaterializationRequest =
        GPUPayloadMaterializationRequest(
            targetId = targetId,
            packetId = packetId,
            taskIds = listOf("task-payload-upload"),
            resourcePlanLabels = listOf("payload-materialization:fullscreen-$index"),
            uniformBlock = GPUUniformPayloadBlock(
                fingerprint = GPUPayloadFingerprint("uniform-fingerprint-$index"),
                packingPlanHash = "solid-rect-layout-v1",
                byteSize = byteSize,
                zeroedPadding = true,
                scope = "pass-a",
                bytes = bytes,
            ),
            uniformSlot = GPUUniformPayloadSlot(
                slotId = GPUPayloadSlotID("pass-a:uniform:$index"),
                fingerprint = GPUPayloadFingerprint("uniform-fingerprint-$index"),
                byteOffset = 0L,
            ),
            resourceBlock = GPUResourceBindingBlock(
                fingerprint = GPUPayloadFingerprint("resource-fingerprint-$index"),
                bindingPlanHash = reflectedBindingLayoutHash,
                bindingCount = 1,
                resourceDescriptorLabels = listOf("uniform:solid-payload"),
                dynamicOffsets = listOf(0L),
            ),
            resourceSlot = GPUResourceBindingSlot(
                slotId = GPUPayloadSlotID("pass-a:resource:$index"),
                fingerprint = GPUPayloadFingerprint("resource-fingerprint-$index"),
                bindingIndex = 0,
            ),
            uploadPlan = GPUPayloadUploadPlan(
                planHash = "upload-solid-v1-$index",
                byteRanges = if (byteSize == 0L) emptyList() else listOf(0L until byteSize),
                stagingScope = "pass-a-staging",
                budgetClass = "unit-test",
                beforeUseToken = "before-draw-$index",
            ),
            reflectedBindingLayoutHash = reflectedBindingLayoutHash,
            deviceGeneration = deviceGeneration,
            payloadGeneration = 7L,
            alignmentBytes = 256L,
            uploadBudgetBytes = 256L,
            uploadCapabilityAvailable = true,
            maxDynamicOffsets = 1,
            requiredUniformUsageLabels = setOf("copy_dst", "uniform"),
            availableUniformUsageLabels = setOf("copy_dst", "uniform"),
        )

    private fun batchRequest(
        uploadBudgetBytes: Long = 1024L,
        payloadRequests: List<GPUPayloadMaterializationRequest> = listOf(payloadRequest(index = 0)),
    ): GPUPayloadSlabBatchRequest =
        GPUPayloadSlabBatchRequest(
            targetId = "root-target",
            frameId = "frame-1",
            sourceLabel = "payload-slab-source",
            deviceGeneration = 11L,
            alignmentBytes = 256L,
            uploadBudgetBytes = uploadBudgetBytes,
            payloadRequests = payloadRequests,
        )
}
