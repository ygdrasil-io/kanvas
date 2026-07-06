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
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingFact
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingKind
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
        val dumpText = dump.joinToString("\n")
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
        assertFalse(dumpText.contains("WGPU"))
        assertFalse(dumpText.contains("wgpu"))
        assertFalse(dumpText.contains("@"))
        assertFalse(dumpText.contains("0x"))
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
    fun `planner accepts unsorted but contiguous upload byte ranges`() {
        val accepted = assertIs<GPUPayloadSlabBatchPlanningResult.Accepted>(
            GPUPayloadSlabBatchPlanner.plan(
                batchRequest(
                    payloadRequests = listOf(
                        payloadRequest(index = 0, uploadByteRanges = listOf(32L..63L, 0L..31L)),
                    ),
                ),
            ),
        )

        assertEquals(256L, accepted.plan.uniformSlabPlan.totalBytes)
        assertEquals(listOf(0L), accepted.plan.slotBindings.map { binding -> binding.alignedOffset })
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
            expectedReason = "binding_layout_mismatch",
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
                            bytes = listOf(256) + List(63) { 0 },
                            byteSize = 64L,
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
    fun `planner refuses invalidated target evidence before slab materialization`() {
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                GPUPayloadSlabBatchRequest(
                    targetId = "root-target",
                    frameId = "frame-1",
                    sourceLabel = "payload-slab-source",
                    deviceGeneration = 11L,
                    alignmentBytes = 256L,
                    uploadBudgetBytes = 1024L,
                    payloadRequests = listOf(payloadRequest(index = 0)),
                    invalidatedReason = "target_generation_invalidated",
                ),
            ),
            expectedCode = "unsupported.payload_slab_resource_invalidated",
            expectedReason = "target_generation_invalidated",
        )
    }

    @Test
    fun `planner accepts dimension-style frame ids that contain zero x separators`() {
        val accepted = assertIs<GPUPayloadSlabBatchPlanningResult.Accepted>(
            GPUPayloadSlabBatchPlanner.plan(
                GPUPayloadSlabBatchRequest(
                    targetId = "payload-target-frame-size",
                    frameId = "offscreen-texture-offscreenTex:320x512:rgba8unorm-srgb-frame-1",
                    sourceLabel = "payload-slab-source",
                    deviceGeneration = 11L,
                    alignmentBytes = 256L,
                    uploadBudgetBytes = 1024L,
                    payloadRequests = listOf(
                        payloadRequest(
                            index = 0,
                            targetId = "payload-target-frame-size",
                        ),
                    ),
                ),
            ),
        )

        assertEquals("offscreen-texture-offscreenTex:320x512:rgba8unorm-srgb-frame-1", accepted.plan.frameId)
    }

    @Test
    fun `planner refuses long 0x handle-looking source labels`() {
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                GPUPayloadSlabBatchRequest(
                    targetId = "root-target",
                    frameId = "frame-1",
                    sourceLabel = "fullscreen-0xdeadbeef",
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
    fun `resource ledger records bounded dump-safe payload slab events`() {
        val ledger = GPUPayloadSlabResourceLedger(maxEvents = 2)

        ledger.record(
            GPUPayloadSlabResourceEvent.Planned(
                sourceLabel = "payload-slab-source",
                targetId = "root-target",
                frameId = "frame-1",
                deviceGeneration = 11L,
                payloadCount = 3,
            ),
        )
        ledger.record(
            GPUPayloadSlabResourceEvent.Accepted(
                sourceLabel = "payload-slab-source",
                planHash = "plan-hash-1",
                totalBytes = 768L,
                slotCount = 3,
            ),
        )
        ledger.record(
            GPUPayloadSlabResourceEvent.Fallback(
                sourceLabel = "gradient-material-pass",
                reason = "binding_layout_mismatch",
                payloadCount = 2,
            ),
        )

        val dump = ledger.dumpLines()
        val dumpText = dump.joinToString("\n")

        assertEquals(2, dump.size)
        assertEquals(
            "payload-slab.resource.accepted source=payload-slab-source plan=plan-hash-1 totalBytes=768 slots=3",
            dump[0],
        )
        assertEquals(
            "payload-slab.resource.fallback source=gradient-material-pass reason=binding_layout_mismatch payloads=2",
            dump[1],
        )
        assertFalse(dumpText.contains("WGPU"))
        assertFalse(dumpText.contains("wgpu"))
        assertFalse(dumpText.contains("@"))
        assertFalse(dumpText.contains("0x"))
    }

    @Test
    fun `resource ledger rejects unsafe evidence`() {
        val ledger = GPUPayloadSlabResourceLedger(maxEvents = 4)

        assertFailsWith<IllegalArgumentException> {
            ledger.record(
                GPUPayloadSlabResourceEvent.Fallback(
                    sourceLabel = "payload@source",
                    reason = "binding_layout_mismatch",
                    payloadCount = 1,
                ),
            )
        }

        assertFailsWith<IllegalArgumentException> {
            ledger.record(
                GPUPayloadSlabResourceEvent.Fallback(
                    sourceLabel = "payload-slab-source\nforged-line",
                    reason = "binding_layout_mismatch",
                    payloadCount = 1,
                ),
            )
        }

        assertFailsWith<IllegalArgumentException> {
            ledger.record(
                GPUPayloadSlabResourceEvent.BudgetRefused(
                    sourceLabel = "payload-slab-source",
                    requestedBytes = -1L,
                    budgetBytes = 256L,
                ),
            )
        }
    }

    @Test
    fun `planner refuses invalid upload and binding evidence before slab materialization`() {
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(
                    payloadRequests = listOf(
                        payloadRequest(index = 0, zeroedPadding = false),
                    ),
                ),
            ),
            expectedCode = "unsupported.payload_slab_uniform_missing",
        )
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(
                    payloadRequests = listOf(
                        payloadRequest(index = 0, uploadByteRanges = emptyList()),
                    ),
                ),
            ),
            expectedCode = "unsupported.payload_slab_uniform_missing",
        )
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(
                    payloadRequests = listOf(
                        payloadRequest(index = 0, uploadByteRanges = listOf(0L..31L, 33L..63L)),
                    ),
                ),
            ),
            expectedCode = "unsupported.payload_slab_uniform_missing",
        )
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(
                    payloadRequests = listOf(
                        payloadRequest(index = 0, uploadByteRanges = listOf(0L..31L)),
                    ),
                ),
            ),
            expectedCode = "unsupported.payload_slab_uniform_missing",
        )
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(
                    payloadRequests = listOf(
                        payloadRequest(index = 0, uniformSlotFingerprint = "uniform-fingerprint-other"),
                    ),
                ),
            ),
            expectedCode = "unsupported.payload_slab_uniform_missing",
        )
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(
                    payloadRequests = listOf(
                        payloadRequest(index = 0, uploadCapabilityAvailable = false),
                    ),
                ),
            ),
            expectedCode = "unsupported.payload_slab_uniform_missing",
        )
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(
                    payloadRequests = listOf(
                        payloadRequest(index = 0, availableUniformUsageLabels = setOf("copy_dst")),
                    ),
                ),
            ),
            expectedCode = "unsupported.payload_slab_uniform_missing",
        )
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(
                    payloadRequests = listOf(
                        payloadRequest(index = 0, resourceSlotFingerprint = "resource-fingerprint-other"),
                    ),
                ),
            ),
            expectedCode = "unsupported.payload_slab_layout_mismatch",
        )
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(
                    payloadRequests = listOf(
                        payloadRequest(index = 0, dynamicOffsets = listOf(0L, 256L), maxDynamicOffsets = 1),
                    ),
                ),
            ),
            expectedCode = "unsupported.payload_slab_layout_mismatch",
        )
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(
                    payloadRequests = listOf(
                        payloadRequest(index = 0, dynamicOffsets = listOf(-1L)),
                    ),
                ),
            ),
            expectedCode = "unsupported.payload_slab_layout_mismatch",
        )
    }

    @Test
    fun `planner refuses invalid resource binding facts before slab materialization`() {
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(
                    payloadRequests = listOf(
                        payloadRequest(index = 0, resourceDescriptorLabels = listOf("texture:paint-image")),
                    ),
                ),
            ),
            expectedCode = "unsupported.payload_slab_layout_mismatch",
            expectedReason = "binding_fact_missing",
        )
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(
                    payloadRequests = listOf(
                        payloadRequest(
                            index = 0,
                            resourceDescriptorLabels = listOf("texture:paint-image", "sampler:paint-image"),
                            bindingFacts = listOf(sampledTextureFact()),
                        ),
                    ),
                ),
            ),
            expectedCode = "unsupported.payload_slab_layout_mismatch",
            expectedReason = "binding_fact_missing",
        )
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(
                    payloadRequests = listOf(
                        payloadRequest(
                            index = 0,
                            resourceDescriptorLabels = listOf("uniform:solid-payload"),
                            bindingFacts = listOf(sampledTextureFact(bindingLabel = "texture:other-image")),
                        ),
                    ),
                ),
            ),
            expectedCode = "unsupported.payload_slab_layout_mismatch",
            expectedReason = "binding_fact_unexpected",
        )
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(
                    payloadRequests = listOf(
                        payloadRequest(
                            index = 0,
                            resourceDescriptorLabels = listOf("texture:paint-image", "sampler:paint-image"),
                            bindingFacts = listOf(
                                sampledTextureFact(availableUsageLabels = setOf("copy_src")),
                                samplerFact(),
                            ),
                        ),
                    ),
                ),
            ),
            expectedCode = "unsupported.payload_slab_layout_mismatch",
            expectedReason = "binding_usage_missing",
        )
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(
                    payloadRequests = listOf(
                        payloadRequest(
                            index = 0,
                            resourceDescriptorLabels = listOf("texture:paint-image", "sampler:paint-image"),
                            bindingFacts = listOf(sampledTextureFact(actualGeneration = 6L), samplerFact()),
                        ),
                    ),
                ),
            ),
            expectedCode = "unsupported.payload_slab_layout_mismatch",
            expectedReason = "binding_generation_stale",
        )
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(
                    payloadRequests = listOf(
                        payloadRequest(
                            index = 0,
                            resourceDescriptorLabels = listOf("texture:paint-image", "sampler:paint-image"),
                            bindingFacts = listOf(
                                sampledTextureFact(evictedReason = "resource-cache-purge"),
                                samplerFact(),
                            ),
                        ),
                    ),
                ),
            ),
            expectedCode = "unsupported.payload_slab_layout_mismatch",
            expectedReason = "binding_resource_evicted",
        )
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(
                    payloadRequests = listOf(
                        payloadRequest(
                            index = 0,
                            resourceDescriptorLabels = listOf("texture:paint-image", "sampler:other-image"),
                            bindingFacts = listOf(
                                sampledTextureFact(bindingLabel = "texture:paint-image"),
                                samplerFact(bindingLabel = "sampler:other-image"),
                            ),
                        ),
                    ),
                ),
            ),
            expectedCode = "unsupported.payload_slab_layout_mismatch",
            expectedReason = "sampled_texture_sampler_missing",
        )
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(
                    payloadRequests = listOf(
                        payloadRequest(
                            index = 0,
                            resourceDescriptorLabels = listOf("texture:paint-image"),
                            bindingFacts = listOf(sampledTextureFact()),
                        ),
                    ),
                ),
            ),
            expectedCode = "unsupported.payload_slab_layout_mismatch",
            expectedReason = "sampled_texture_sampler_missing",
        )
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(
                    payloadRequests = listOf(
                        payloadRequest(
                            index = 0,
                            resourceDescriptorLabels = listOf("texture:paint-image", "sampler:paint-image"),
                            bindingFacts = listOf(sampledTextureFact(), sampledTextureFact(), samplerFact()),
                        ),
                    ),
                ),
            ),
            expectedCode = "unsupported.payload_slab_layout_mismatch",
            expectedReason = "sampled_texture_sampler_missing",
        )
        assertRefused(
            result = GPUPayloadSlabBatchPlanner.plan(
                batchRequest(
                    payloadRequests = listOf(
                        payloadRequest(
                            index = 0,
                            resourceDescriptorLabels = listOf("texture:paint-image", "sampler:paint-image"),
                            bindingFacts = listOf(sampledTextureFact(), samplerFact(), samplerFact()),
                        ),
                    ),
                ),
            ),
            expectedCode = "unsupported.payload_slab_layout_mismatch",
            expectedReason = "sampled_texture_sampler_missing",
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

    @Test
    fun `plan constructor rejects mismatched uniform slab source label`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPayloadSlabBatchPlan(
                planHash = "payload-slab-hash",
                sourceLabel = "payload-slab-source",
                targetId = "root-target",
                frameId = "frame-1",
                deviceGeneration = 11L,
                uniformSlabPlan = GPUUniformSlabPlan(
                    planHash = "uniform-slab-hash",
                    sourceLabel = "other-source",
                    deviceGeneration = 11L,
                    alignmentBytes = 256L,
                    totalBytes = 256L,
                    uploadBudgetBytes = 1024L,
                    slots = listOf(uniformSlabSlot(index = 0)),
                ),
                slotBindings = listOf(slotBinding(index = 0)),
            )
        }
    }

    @Test
    fun `plan constructor rejects mismatched uniform slab device generation`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPayloadSlabBatchPlan(
                planHash = "payload-slab-hash",
                sourceLabel = "payload-slab-source",
                targetId = "root-target",
                frameId = "frame-1",
                deviceGeneration = 11L,
                uniformSlabPlan = GPUUniformSlabPlan(
                    planHash = "uniform-slab-hash",
                    sourceLabel = "payload-slab-source",
                    deviceGeneration = 12L,
                    alignmentBytes = 256L,
                    totalBytes = 256L,
                    uploadBudgetBytes = 1024L,
                    slots = listOf(uniformSlabSlot(index = 0)),
                ),
                slotBindings = listOf(slotBinding(index = 0)),
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
        expectedReason: String? = null,
    ) {
        val refused = assertIs<GPUPayloadSlabBatchPlanningResult.Refused>(result)
        val dumpText = refused.dumpLines().joinToString("\n")
        assertEquals(expectedCode, refused.diagnostic.code)
        if (expectedReason != null) {
            assertEquals(expectedReason, refused.diagnostic.facts["reason"])
        }
        assertTrue(
            dumpText.contains(
                "payload-slab.batch.refused code=$expectedCode terminal=true facts=",
            ),
        )
        assertFalse(dumpText.contains("WGPU"))
        assertFalse(dumpText.contains("wgpu"))
        assertFalse(dumpText.contains("@"))
        assertFalse(dumpText.contains("0x"))
    }

    private fun payloadRequest(
        index: Int,
        targetId: String = "root-target",
        packetId: String = "packet-$index",
        deviceGeneration: Long = 11L,
        reflectedBindingLayoutHash: String = "layout-solid-v1",
        bytes: List<Int> = List(64) { index + 1 },
        byteSize: Long = bytes.size.toLong(),
        zeroedPadding: Boolean = true,
        uniformFingerprint: String = "uniform-fingerprint-$index",
        uniformSlotFingerprint: String = uniformFingerprint,
        resourceFingerprint: String = "resource-fingerprint-$index",
        resourceSlotFingerprint: String = resourceFingerprint,
        dynamicOffsets: List<Long> = listOf(0L),
        uploadByteRanges: List<LongRange> = if (byteSize == 0L) emptyList() else listOf(0L until byteSize),
        maxDynamicOffsets: Int = 1,
        uploadCapabilityAvailable: Boolean = true,
        requiredUniformUsageLabels: Set<String> = setOf("copy_dst", "uniform"),
        availableUniformUsageLabels: Set<String> = setOf("copy_dst", "uniform"),
        resourceDescriptorLabels: List<String> = listOf("uniform:solid-payload"),
        bindingFacts: List<GPUResourceBindingFact> = emptyList(),
    ): GPUPayloadMaterializationRequest =
        GPUPayloadMaterializationRequest(
            targetId = targetId,
            packetId = packetId,
            taskIds = listOf("task-payload-upload"),
            resourcePlanLabels = listOf("payload-materialization:fullscreen-$index"),
            uniformBlock = GPUUniformPayloadBlock(
                fingerprint = GPUPayloadFingerprint(uniformFingerprint),
                packingPlanHash = "solid-rect-layout-v1",
                byteSize = byteSize,
                zeroedPadding = zeroedPadding,
                scope = "pass-a",
                bytes = bytes,
            ),
            uniformSlot = GPUUniformPayloadSlot(
                slotId = GPUPayloadSlotID("pass-a:uniform:$index"),
                fingerprint = GPUPayloadFingerprint(uniformSlotFingerprint),
                byteOffset = 0L,
            ),
            resourceBlock = GPUResourceBindingBlock(
                fingerprint = GPUPayloadFingerprint(resourceFingerprint),
                bindingPlanHash = reflectedBindingLayoutHash,
                bindingCount = 1,
                resourceDescriptorLabels = resourceDescriptorLabels,
                dynamicOffsets = dynamicOffsets,
                bindingFacts = bindingFacts,
            ),
            resourceSlot = GPUResourceBindingSlot(
                slotId = GPUPayloadSlotID("pass-a:resource:$index"),
                fingerprint = GPUPayloadFingerprint(resourceSlotFingerprint),
                bindingIndex = 0,
            ),
            uploadPlan = GPUPayloadUploadPlan(
                planHash = "upload-solid-v1-$index",
                byteRanges = uploadByteRanges,
                stagingScope = "pass-a-staging",
                budgetClass = "unit-test",
                beforeUseToken = "before-draw-$index",
            ),
            reflectedBindingLayoutHash = reflectedBindingLayoutHash,
            deviceGeneration = deviceGeneration,
            payloadGeneration = 7L,
            alignmentBytes = 256L,
            uploadBudgetBytes = 256L,
            uploadCapabilityAvailable = uploadCapabilityAvailable,
            maxDynamicOffsets = maxDynamicOffsets,
            requiredUniformUsageLabels = requiredUniformUsageLabels,
            availableUniformUsageLabels = availableUniformUsageLabels,
        )

    private fun sampledTextureFact(
        bindingLabel: String = "texture:paint-image",
        actualGeneration: Long = 7L,
        availableUsageLabels: Set<String> = setOf("texture_binding"),
        evictedReason: String? = null,
    ): GPUResourceBindingFact =
        GPUResourceBindingFact(
            bindingLabel = bindingLabel,
            kind = GPUResourceBindingKind.SampledTexture,
            descriptorHash = "texture-descriptor:${bindingLabel.substringAfter(':')}",
            requiredUsageLabels = setOf("texture_binding"),
            availableUsageLabels = availableUsageLabels,
            expectedResourceGeneration = 7L,
            actualResourceGeneration = actualGeneration,
            evictedReason = evictedReason,
        )

    private fun samplerFact(
        bindingLabel: String = "sampler:paint-image",
        actualGeneration: Long = 7L,
        availableUsageLabels: Set<String> = setOf("sampler"),
        evictedReason: String? = null,
    ): GPUResourceBindingFact =
        GPUResourceBindingFact(
            bindingLabel = bindingLabel,
            kind = GPUResourceBindingKind.Sampler,
            descriptorHash = "sampler-descriptor:${bindingLabel.substringAfter(':')}",
            requiredUsageLabels = setOf("sampler"),
            availableUsageLabels = availableUsageLabels,
            expectedResourceGeneration = 7L,
            actualResourceGeneration = actualGeneration,
            evictedReason = evictedReason,
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

    private fun uniformSlabSlot(index: Int): GPUUniformSlabSlot =
        GPUUniformSlabSlot(
            slotLabel = "packet-$index:pass-a:uniform:$index:pass-a:resource:$index",
            payloadHash = "payload-hash-$index",
            payloadBytes = 64L,
            alignedOffset = index * 256L,
            allocatedBytes = 256L,
        )

    private fun slotBinding(index: Int): GPUPayloadSlabSlotBinding =
        GPUPayloadSlabSlotBinding(
            slotLabel = "packet-$index:pass-a:uniform:$index:pass-a:resource:$index",
            packetId = "packet-$index",
            uniformSlotId = "pass-a:uniform:$index",
            resourceSlotId = "pass-a:resource:$index",
            payloadFingerprint = "uniform-fingerprint-$index",
            reflectedBindingLayoutHash = "layout-solid-v1",
            alignedOffset = index * 256L,
            payloadBytes = 64L,
        )
}
