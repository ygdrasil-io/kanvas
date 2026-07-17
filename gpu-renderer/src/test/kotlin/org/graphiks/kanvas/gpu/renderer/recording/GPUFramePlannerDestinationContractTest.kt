package org.graphiks.kanvas.gpu.renderer.recording

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCopyAsDrawImplementationCapability
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.destination.CopyAsDrawMaterialization
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadMember
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroup
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroupKey
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroupingResult
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotMaterialization
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateIdentity
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleContinuationKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceCopyRegion
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureCopyLayout
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUTargetIdentity

class GPUFramePlannerDestinationContractTest {
    @Test
    fun `MSAA render tasks require one exact typed continuation proof before linearization`() {
        val samplePlan = GPUSamplePlan.MultisampleFrame(4)
        val valid = continuationKey(samplePlan = samplePlan)
        val cases = listOf(
            null to "invalid.frame_plan.msaa_continuation_missing",
            valid.copy(target = GPUTargetIdentity("target.other")) to
                "invalid.frame_plan.msaa_continuation_target",
            valid.copy(samplePlan = GPUSamplePlan.MultisampleFrame(8)) to
                "invalid.frame_plan.msaa_continuation_sample_plan",
            valid.copy(deviceGeneration = GPUDeviceGenerationID(99)) to
                "invalid.frame_plan.msaa_continuation_device_generation",
        )

        cases.forEachIndexed { index, (proof, expectedCode) ->
            val render = renderTask(
                taskId = "task.render.msaa.$index",
                commandId = 70 + index,
                blendPlan = directBlendPlan(),
                samplePlan = samplePlan,
                sampleContinuationKey = proof,
            )
            val plan = GPUFramePlanner.plan(taskList(listOf(render), emptyList()))

            assertEquals(expectedCode, plan.diagnostics.last().code.value)
        }
    }

    @Test
    fun `all MSAA render tasks for one target require the same continuation key`() {
        val firstKey = continuationKey()
        val first = renderTask(
            "task.render.msaa.first",
            80,
            blendPlan = directBlendPlan(),
            samplePlan = firstKey.samplePlan,
            sampleContinuationKey = firstKey,
        )
        val second = renderTask(
            "task.render.msaa.second",
            81,
            blendPlan = directBlendPlan(),
            samplePlan = firstKey.samplePlan,
            sampleContinuationKey = firstKey.copy(
                colorAttachment = GPUTargetIdentity("target.scene.msaa.other"),
            ),
        )

        val plan = GPUFramePlanner.plan(
            taskList(listOf(first, second), listOf(dependency(first, second))),
        )

        assertEquals("invalid.frame_plan.msaa_continuation_key_mismatch", plan.diagnostics.last().code.value)
    }

    @Test
    fun `destination grouping continuation must exactly match its consumer render task proof`() {
        val renderKey = continuationKey()
        val destination = destinationTask(
            GPUDestinationSnapshotTaskPayload(
                grouping = grouping(
                    groups = listOf(
                        group(
                            0,
                            "draw.90",
                            sourceIntermediate = null,
                            sampleContinuation = renderKey.copy(
                                colorAttachment = GPUTargetIdentity("target.scene.msaa.grouping"),
                            ),
                        ),
                    ),
                    materializations = listOf(
                        GPUDestinationSnapshotMaterialization.TextureCopy(0, BOUNDS),
                    ),
                ),
                operations = listOf(
                    textureCopyOperation(
                        groupIndex = 0,
                        groupingCommandId = "draw.90",
                        renderTaskId = "task.render.90",
                        commandId = 90,
                    ),
                ),
            ),
        )
        val render = renderTask(
            "task.render.90",
            90,
            samplePlan = renderKey.samplePlan,
            sampleContinuationKey = renderKey,
        )

        val plan = GPUFramePlanner.plan(
            taskList(listOf(destination, render), listOf(dependency(destination, render))),
        )

        assertEquals("invalid.frame_plan.destination_continuation_mismatch", plan.diagnostics.last().code.value)
    }

    @Test
    fun `payload normalizes Task 5 operations and preserves exact typed sources`() {
        val grouping = grouping(
            groups = listOf(
                group(0, "draw.7", sourceIntermediate = null),
                group(1, "draw.8", sourceIntermediate = SOURCE_INTERMEDIATE),
            ),
            materializations = listOf(
                GPUDestinationSnapshotMaterialization.TextureCopy(0, BOUNDS),
                CopyAsDrawMaterialization(1, BOUNDS, SOURCE_INTERMEDIATE),
            ),
        )
        val payload = GPUDestinationSnapshotTaskPayload(
            grouping = grouping,
            operations = listOf(
                copyAsDrawOperation(
                    groupIndex = 1,
                    groupingCommandId = "draw.8",
                    renderTaskId = "task.render.8",
                    commandId = 8,
                ),
                textureCopyOperation(
                    groupIndex = 0,
                    groupingCommandId = "draw.7",
                    renderTaskId = "task.render.7",
                    commandId = 7,
                ),
            ),
        )

        assertEquals(listOf(0, 1), payload.operations.map { operation -> operation.groupIndex })
        val textureCopy = assertIs<GPUDestinationSnapshotOperation.TextureCopy>(payload.operations[0])
        val copyAsDraw = assertIs<GPUDestinationSnapshotOperation.CopyAsDraw>(payload.operations[1])
        val targetSource: GPUFrameResourceRef = textureCopy.source
        val intermediateSource: GPUFrameResourceRef = copyAsDraw.source
        assertEquals(SCENE_TARGET, targetSource)
        assertEquals(SOURCE_TEXTURE, intermediateSource)
        assertEquals(SOURCE_INTERMEDIATE, copyAsDraw.sourceIntermediate)
        assertEquals(
            GPUDestinationSnapshotConsumerRef(
                groupingCommandId = "draw.8",
                renderTaskId = GPUTaskID("task.render.8"),
                packetId = GPUDrawPacketID("packet.8"),
                commandId = GPUDrawCommandID(8),
            ),
            copyAsDraw.consumers.single(),
        )
    }

    @Test
    fun `payload requires one operation for every Task 5 materialization`() {
        val grouping = grouping(
            groups = listOf(
                group(0, "draw.7", sourceIntermediate = null),
                group(1, "draw.8", sourceIntermediate = SOURCE_INTERMEDIATE),
            ),
            materializations = listOf(
                GPUDestinationSnapshotMaterialization.TextureCopy(0, BOUNDS),
                CopyAsDrawMaterialization(1, BOUNDS, SOURCE_INTERMEDIATE),
            ),
        )
        val first = textureCopyOperation(
            groupIndex = 0,
            groupingCommandId = "draw.7",
            renderTaskId = "task.render.7",
            commandId = 7,
        )

        assertFailsWith<IllegalArgumentException> {
            GPUDestinationSnapshotTaskPayload(grouping = grouping, operations = listOf(first))
        }
        assertFailsWith<IllegalArgumentException> {
            GPUDestinationSnapshotTaskPayload(grouping = grouping, operations = listOf(first, first))
        }
    }

    @Test
    fun `one shared snapshot is copied once before its first exact consumer`() {
        val grouping = grouping(
            groups = listOf(group(0, "draw.7", "draw.8", sourceIntermediate = null)),
            materializations = listOf(GPUDestinationSnapshotMaterialization.TextureCopy(0, BOUNDS)),
        )
        val destination = destinationTask(
            payload = GPUDestinationSnapshotTaskPayload(
                grouping = grouping,
                operations = listOf(
                    textureCopyOperation(
                        groupIndex = 0,
                        consumers = listOf(
                            consumer("draw.7", "task.render.7", 7),
                            consumer("draw.8", "task.render.8", 8),
                        ),
                    ),
                ),
            ),
        )
        val first = renderTask("task.render.7", 7)
        val second = renderTask("task.render.8", 8)
        val plan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(second, destination, first),
                dependencies = listOf(
                    dependency(destination, first),
                    dependency(destination, second),
                    dependency(first, second),
                ),
            ),
        )

        assertFalse(plan.atomicallyRefused)
        val copies = plan.steps.filterIsInstance<GPUFrameStep.CopyDestinationStep>()
        assertEquals(1, copies.size)
        assertEquals(SCENE_TARGET, copies.single().source)
        assertEquals(SNAPSHOT_TEXTURE, copies.single().snapshot)
        val copyIndex = plan.steps.indexOf(copies.single())
        val firstConsumerIndex = plan.steps.indexOfFirst { step ->
            step is GPUFrameStep.RenderPassStep &&
                step.drawPackets.any { packet -> packet.packetId == GPUDrawPacketID("packet.7") }
        }
        assertTrue(firstConsumerIndex >= 0)
        assertTrue(copyIndex < firstConsumerIndex)
    }

    @Test
    fun `missing destination consumer binding refuses the frame atomically`() {
        val destination = destinationTask(
            payload = GPUDestinationSnapshotTaskPayload(
                grouping = grouping(
                    groups = listOf(group(0, "draw.7", sourceIntermediate = null)),
                    materializations = listOf(
                        GPUDestinationSnapshotMaterialization.TextureCopy(0, BOUNDS),
                    ),
                ),
                operations = listOf(textureCopyOperation(groupIndex = 0, consumers = emptyList())),
            ),
        )
        val render = renderTask("task.render.7", 7)

        assertAtomicConsumerBindingRefusal(
            taskList(
                tasks = listOf(destination, render),
                dependencies = listOf(dependency(destination, render)),
            ),
        )
    }

    @Test
    fun `wrong destination packet binding refuses the frame atomically`() {
        val destination = destinationTask(
            payload = GPUDestinationSnapshotTaskPayload(
                grouping = grouping(
                    groups = listOf(group(0, "draw.7", sourceIntermediate = null)),
                    materializations = listOf(
                        GPUDestinationSnapshotMaterialization.TextureCopy(0, BOUNDS),
                    ),
                ),
                operations = listOf(
                    textureCopyOperation(
                        groupIndex = 0,
                        consumers = listOf(
                            consumer("draw.7", "task.render.7", 7).copy(
                                packetId = GPUDrawPacketID("packet.wrong"),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val render = renderTask("task.render.7", 7)

        assertAtomicConsumerBindingRefusal(
            taskList(
                tasks = listOf(destination, render),
                dependencies = listOf(dependency(destination, render)),
            ),
        )
    }

    @Test
    fun `CopyAsDraw absent from the canonical frame capability seal is refused atomically`() {
        val destination = copyAsDrawDestinationTask()
        val render = renderTask("task.render.8", 8)

        val plan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(destination, render),
                dependencies = listOf(dependency(destination, render)),
            ),
        )

        assertTrue(plan.atomicallyRefused)
        assertTrue(plan.steps.isEmpty())
        assertEquals("unsupported.destination_read.copy_unavailable", plan.diagnostics.last().code.value)
    }

    @Test
    fun `canonical CopyAsDraw seal is deterministic and preserved by the frame plan`() {
        val seal = copyAsDrawSeal()
        val sameSeal = copyAsDrawSeal()
        val changedVersion = copyAsDrawSeal(implementationVersion = "copy-as-draw-v3")
        val destination = copyAsDrawDestinationTask()
        val render = renderTask("task.render.8", 8)

        val plan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(destination, render),
                dependencies = listOf(dependency(destination, render)),
                capabilitySeal = seal,
            ),
        )

        assertEquals(seal.sealHash, sameSeal.sealHash)
        assertNotEquals(seal.sealHash, changedVersion.sealHash)
        assertNotEquals(seal.capabilitySnapshotHash, changedVersion.capabilitySnapshotHash)
        assertFalse(plan.atomicallyRefused)
        assertEquals(seal, plan.capabilitySeal)
        val step = plan.steps.filterIsInstance<GPUFrameStep.CopyAsDrawMaterializationStep>().single()
        assertEquals(SOURCE_TEXTURE, step.source)
        assertEquals(SOURCE_INTERMEDIATE, step.sourceIntermediate)
        assertEquals(seal.sealHash, step.capabilitySealHash)
        assertEquals(FRAME_ID, plan.capabilitySeal.frameId)
        assertEquals(DEVICE_GENERATION, plan.capabilitySeal.deviceGeneration)
        assertEquals("kanvas.copy-as-draw", plan.capabilitySeal.copyAsDrawCapability?.implementationId)
        assertEquals("copy-as-draw-v2", plan.capabilitySeal.copyAsDrawCapability?.implementationVersion)
        assertEquals("kanvas-test", plan.capabilitySeal.implementation.implementationName)
        assertEquals(seal.capabilitySnapshotHash, plan.capabilitySeal.capabilitySnapshotHash)
    }

    @Test
    fun `CopyAsDraw seal hash must match recording and destination device generation`() {
        val destination = copyAsDrawDestinationTask()
        val render = renderTask("task.render.8", 8)
        val wrongGeneration = copyAsDrawSeal(deviceGeneration = GPUDeviceGenerationID(99))
        val validSeal = copyAsDrawSeal()
        val wrongRecordingCongruence = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(destination, render),
                dependencies = listOf(dependency(destination, render)),
                capabilitySeal = validSeal,
                recordingCapabilitySealHash = "sha256:wrong-recording-seal",
            ),
        )
        val wrongGenerationPlan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(destination, render),
                dependencies = listOf(dependency(destination, render)),
                capabilitySeal = wrongGeneration,
            ),
        )

        listOf(wrongRecordingCongruence, wrongGenerationPlan).forEach { plan ->
            assertTrue(plan.atomicallyRefused)
            assertTrue(plan.steps.isEmpty())
            assertEquals("invalid.frame_plan.capability_seal", plan.diagnostics.last().code.value)
        }
    }

    @Test
    fun `destination step dump preserves every exact Task 5 consumer reference`() {
        val destination = destinationTask(
            payload = GPUDestinationSnapshotTaskPayload(
                grouping = grouping(
                    groups = listOf(group(0, "draw.consumer", sourceIntermediate = null)),
                    materializations = listOf(
                        GPUDestinationSnapshotMaterialization.TextureCopy(0, BOUNDS),
                    ),
                ),
                operations = listOf(
                    textureCopyOperation(
                        groupIndex = 0,
                        consumers = listOf(consumer("draw.consumer", "task.render.7", 7)),
                    ),
                ),
            ),
        )
        val render = renderTask("task.render.7", 7)

        val dump = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(destination, render),
                dependencies = listOf(dependency(destination, render)),
            ),
        ).dumpLines().joinToString("\n")

        assertTrue(dump.contains("consumerGrouping=draw.consumer"), dump)
        assertTrue(dump.contains("consumerTask=task.render.7"), dump)
        assertTrue(dump.contains("consumerPacket=packet.7"), dump)
        assertTrue(dump.contains("consumerCommand=7"), dump)
    }

    @Test
    fun `complete Task 5 grouping key contributes to frame plan hash`() {
        val baselineGroup = group(0, "draw.7", sourceIntermediate = null)
        val changedGroup = baselineGroup.copy(
            key = baselineGroup.key.copy(
                targetGeneration = baselineGroup.key.targetGeneration + 1,
                colorInterpretation = GPUColorInterpretation("display-p3-premul"),
            ),
        )

        val baseline = textureCopyPlan(group = baselineGroup, groupingCommandId = "draw.7")
        val changed = textureCopyPlan(group = changedGroup, groupingCommandId = "draw.7")

        assertNotEquals(baseline.stableHash(), changed.stableHash())
        assertTrue(
            changed.dumpLines().any { line ->
                line.contains("targetGeneration=3") && line.contains("color=display-p3-premul")
            },
            changed.dumpLines().joinToString("\n"),
        )
    }

    @Test
    fun `grouping command identity contributes to frame plan hash`() {
        val first = textureCopyPlan(
            group = group(0, "draw.alpha", sourceIntermediate = null),
            groupingCommandId = "draw.alpha",
        )
        val second = textureCopyPlan(
            group = group(0, "draw.beta", sourceIntermediate = null),
            groupingCommandId = "draw.beta",
        )

        assertNotEquals(first.stableHash(), second.stableHash())
    }

    @Test
    fun `TextureCopy rejects a source that is not the exact Task 5 target`() {
        val group = group(0, "draw.7", sourceIntermediate = null)

        assertFailsWith<IllegalArgumentException> {
            GPUDestinationSnapshotTaskPayload(
                grouping = grouping(
                    groups = listOf(group),
                    materializations = listOf(
                        GPUDestinationSnapshotMaterialization.TextureCopy(0, BOUNDS),
                    ),
                ),
                operations = listOf(
                    textureCopyOperation(
                        groupIndex = 0,
                        source = GPUFrameTargetRef("target.wrong"),
                    ),
                ),
            )
        }
    }

    @Test
    fun `CopyAsDraw rejects a non texture source`() {
        assertFailsWith<IllegalArgumentException> {
            copyAsDrawPayload(source = GPUFrameBufferRef("buffer.not-texturable"))
        }
    }

    @Test
    fun `CopyAsDraw rejects sampling from its destination snapshot`() {
        assertFailsWith<IllegalArgumentException> {
            copyAsDrawPayload(source = SNAPSHOT_TEXTURE)
        }
    }

    @Test
    fun `overlapping destination snapshot aliases refuse atomically`() {
        val destination = destinationTask(
            payload = twoTargetSharedSnapshotPayload(
                firstGroupingCommandIds = listOf("draw.1", "draw.3"),
                firstConsumers = listOf(
                    consumer("draw.1", "task.render.1", 1),
                    consumer("draw.3", "task.render.3", 3),
                ),
                secondGroupingCommandId = "draw.2",
                secondConsumer = consumer("draw.2", "task.render.2", 2),
            ),
        )
        val first = renderTask("task.render.1", 1, target = TARGET_A)
        val middle = renderTask("task.render.2", 2, target = TARGET_B)
        val last = renderTask("task.render.3", 3, target = TARGET_A)

        val plan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(destination, first, middle, last),
                dependencies = listOf(
                    dependency(destination, first),
                    dependency(destination, middle),
                    dependency(destination, last),
                    dependency(first, middle),
                    dependency(middle, last),
                ),
            ),
        )

        assertTrue(plan.atomicallyRefused)
        assertTrue(plan.steps.isEmpty())
        assertEquals("invalid.frame_plan.destination_snapshot_alias", plan.diagnostics.last().code.value)
    }

    @Test
    fun `non overlapping destination snapshot aliases may reuse one logical texture`() {
        val destination = destinationTask(
            payload = twoTargetSharedSnapshotPayload(
                firstGroupingCommandIds = listOf("draw.1"),
                firstConsumers = listOf(consumer("draw.1", "task.render.1", 1)),
                secondGroupingCommandId = "draw.2",
                secondConsumer = consumer("draw.2", "task.render.2", 2),
            ),
        )
        val first = renderTask("task.render.1", 1, target = TARGET_A)
        val second = renderTask("task.render.2", 2, target = TARGET_B)

        val plan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(destination, first, second),
                dependencies = listOf(
                    dependency(destination, first),
                    dependency(destination, second),
                    dependency(first, second),
                ),
            ),
        )

        assertFalse(plan.atomicallyRefused)
        assertEquals(2, plan.steps.filterIsInstance<GPUFrameStep.CopyDestinationStep>().size)
    }

    @Test
    fun `non overlapping destination snapshot aliases may reuse one texture inside one render task`() {
        val destination = destinationTask(
            payload = GPUDestinationSnapshotTaskPayload(
                grouping = grouping(
                    groups = listOf(
                        group(0, "draw.1", sourceIntermediate = null),
                        group(1, "draw.3", sourceIntermediate = null),
                    ),
                    materializations = listOf(
                        GPUDestinationSnapshotMaterialization.TextureCopy(0, BOUNDS),
                        GPUDestinationSnapshotMaterialization.TextureCopy(1, BOUNDS),
                    ),
                ),
                operations = listOf(
                    textureCopyOperation(
                        groupIndex = 0,
                        groupingCommandId = "draw.1",
                        renderTaskId = "task.render.alias.non-overlap",
                        commandId = 1,
                        snapshot = SHARED_SNAPSHOT_TEXTURE,
                    ),
                    textureCopyOperation(
                        groupIndex = 1,
                        groupingCommandId = "draw.3",
                        renderTaskId = "task.render.alias.non-overlap",
                        commandId = 3,
                        snapshot = SHARED_SNAPSHOT_TEXTURE,
                    ),
                ),
            ),
        )
        val render = renderTask(
            taskId = "task.render.alias.non-overlap",
            packets = listOf(
                packet(1, destinationBlendPlan()),
                packet(2, directBlendPlan()),
                packet(3, destinationBlendPlan()),
            ),
        )

        val plan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(destination, render),
                dependencies = listOf(dependency(destination, render)),
            ),
        )

        assertFalse(plan.atomicallyRefused, plan.diagnostics.joinToString { it.code.value })
        assertEquals(2, plan.steps.filterIsInstance<GPUFrameStep.CopyDestinationStep>().size)
        assertEquals(
            listOf("packet.1", "packet.2", "packet.3"),
            plan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
                .flatMap { it.drawPackets }
                .map { it.packetId.value },
        )
    }

    @Test
    fun `overlapping destination snapshot aliases inside one render task refuse atomically`() {
        val destination = destinationTask(
            payload = GPUDestinationSnapshotTaskPayload(
                grouping = grouping(
                    groups = listOf(
                        group(0, "draw.1", "draw.3", sourceIntermediate = null),
                        group(1, "draw.2", sourceIntermediate = null),
                    ),
                    materializations = listOf(
                        GPUDestinationSnapshotMaterialization.TextureCopy(0, BOUNDS),
                        GPUDestinationSnapshotMaterialization.TextureCopy(1, BOUNDS),
                    ),
                ),
                operations = listOf(
                    textureCopyOperation(
                        groupIndex = 0,
                        snapshot = SHARED_SNAPSHOT_TEXTURE,
                        consumers = listOf(
                            consumer("draw.1", "task.render.alias.overlap", 1),
                            consumer("draw.3", "task.render.alias.overlap", 3),
                        ),
                    ),
                    textureCopyOperation(
                        groupIndex = 1,
                        groupingCommandId = "draw.2",
                        renderTaskId = "task.render.alias.overlap",
                        commandId = 2,
                        snapshot = SHARED_SNAPSHOT_TEXTURE,
                    ),
                ),
            ),
        )
        val render = renderTask(
            taskId = "task.render.alias.overlap",
            packets = (1..3).map { packet(it, destinationBlendPlan()) },
        )

        val plan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(destination, render),
                dependencies = listOf(dependency(destination, render)),
            ),
        )

        assertTrue(plan.atomicallyRefused)
        assertTrue(plan.steps.isEmpty())
        assertEquals("invalid.frame_plan.destination_snapshot_alias", plan.diagnostics.last().code.value)
    }

    @Test
    fun `render slicing preserves packet order ownership and batch partitions exactly`() {
        val destination = destinationTask(
            payload = GPUDestinationSnapshotTaskPayload(
                grouping = grouping(
                    groups = listOf(group(0, "draw.2", sourceIntermediate = null)),
                    materializations = listOf(
                        GPUDestinationSnapshotMaterialization.TextureCopy(0, BOUNDS),
                    ),
                ),
                operations = listOf(
                    textureCopyOperation(
                        groupIndex = 0,
                        groupingCommandId = "draw.2",
                        renderTaskId = "task.render.batch.first",
                        commandId = 2,
                    ),
                ),
            ),
        )
        val first = renderTask(
            taskId = "task.render.batch.first",
            packets = listOf(
                packet(1, directBlendPlan()),
                packet(2, destinationBlendPlan()),
                packet(3, directBlendPlan()),
            ),
            loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store, "transparent"),
        )
        val second = renderTask(
            taskId = "task.render.batch.second",
            packets = listOf(packet(4, directBlendPlan()), packet(5, directBlendPlan())),
            loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store, "transparent"),
        )

        val plan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(destination, first, second),
                dependencies = listOf(
                    dependency(destination, first),
                    dependency(first, second),
                ),
            ),
        )

        assertFalse(plan.atomicallyRefused, plan.diagnostics.joinToString { it.code.value })
        val renders = plan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        assertEquals(
            listOf("packet.1", "packet.2", "packet.3", "packet.4", "packet.5"),
            renders.flatMap { it.drawPackets }.map { it.packetId.value },
        )
        assertEquals(listOf(first.taskId), renders.first().sourceTaskIds)
        assertEquals(listOf(first.taskId, second.taskId), renders.last().sourceTaskIds)
        renders.forEach { render ->
            assertEquals(
                render.drawPackets.map { it.packetId },
                render.batches.flatMap { it.packets }.map { it.packetId },
            )
            assertEquals(
                render.sourceTaskIds,
                render.batches.flatMap { it.sourceTaskIds }.distinct(),
            )
        }
        assertEquals(2, plan.dependencies.size)
        assertEquals(dependency(first, second), plan.dependencies.single { it.fromTaskId == first.taskId })
    }

    @Test
    fun `compatible renders remain in one pass across an all NoOp task`() {
        val first = renderTask("task.render.1", 1, blendPlan = directBlendPlan())
        val noOp = renderTask("task.render.2", 2, blendPlan = noOpBlendPlan())
        val last = renderTask("task.render.3", 3, blendPlan = directBlendPlan())

        val plan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(first, noOp, last),
                dependencies = listOf(dependency(first, noOp), dependency(noOp, last)),
            ),
        )

        assertFalse(plan.atomicallyRefused)
        val renderPasses = plan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        assertEquals(1, renderPasses.size)
        val renderPass = renderPasses.single()
        assertEquals(listOf("packet.1", "packet.3"), renderPass.drawPackets.map { it.packetId.value })
    }

    @Test
    fun `NoOp between shared destination consumers is not a target write`() {
        val destination = destinationTask(
            payload = GPUDestinationSnapshotTaskPayload(
                grouping = grouping(
                    groups = listOf(group(0, "draw.1", "draw.3", sourceIntermediate = null)),
                    materializations = listOf(
                        GPUDestinationSnapshotMaterialization.TextureCopy(0, BOUNDS),
                    ),
                ),
                operations = listOf(
                    textureCopyOperation(
                        groupIndex = 0,
                        consumers = listOf(
                            consumer("draw.1", "task.render.1", 1),
                            consumer("draw.3", "task.render.3", 3),
                        ),
                    ),
                ),
            ),
        )
        val first = renderTask("task.render.1", 1)
        val noOp = renderTask("task.render.2", 2, blendPlan = noOpBlendPlan())
        val last = renderTask("task.render.3", 3)

        val plan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(destination, first, noOp, last),
                dependencies = listOf(
                    dependency(destination, first),
                    dependency(destination, last),
                    dependency(first, noOp),
                    dependency(noOp, last),
                ),
            ),
        )

        assertFalse(plan.atomicallyRefused, plan.diagnostics.joinToString { it.code.value })
        assertEquals(1, plan.steps.filterIsInstance<GPUFrameStep.CopyDestinationStep>().size)
    }

    @Test
    fun `shared destination snapshot refuses a write to the snapshot before its last consumer`() {
        val destination = destinationTask(
            payload = GPUDestinationSnapshotTaskPayload(
                grouping = grouping(
                    groups = listOf(group(0, "draw.1", "draw.3", sourceIntermediate = null)),
                    materializations = listOf(
                        GPUDestinationSnapshotMaterialization.TextureCopy(0, BOUNDS),
                    ),
                ),
                operations = listOf(
                    textureCopyOperation(
                        groupIndex = 0,
                        consumers = listOf(
                            consumer("draw.1", "task.render.1", 1),
                            consumer("draw.3", "task.render.3", 3),
                        ),
                    ),
                ),
            ),
        )
        val first = renderTask("task.render.1", 1)
        val overwrite = GPUTask.Copy(
            taskId = GPUTaskID("task.copy.snapshot-overwrite"),
            recordingId = RECORDING_ID,
            phase = GPUTaskPhase.Copy,
            source = GPUFrameBufferRef("buffer.overwrite"),
            destination = SNAPSHOT_TEXTURE,
            regions = listOf(GPUResourceCopyRegion(0, 0, null, 16)),
        )
        val last = renderTask("task.render.3", 3)
        val tasks = listOf(destination, first, overwrite, last)

        val plan = GPUFramePlanner.plan(
            taskList(
                tasks = tasks,
                dependencies = listOf(
                    dependency(destination, first),
                    dependency(destination, last),
                    dependency(first, overwrite),
                    dependency(overwrite, last),
                ),
            ),
        )

        assertTrue(plan.atomicallyRefused)
        assertTrue(plan.steps.isEmpty())
        assertEquals("invalid.frame_plan.destination_order", plan.diagnostics.last().code.value)
    }

    @Test
    fun `destination copy schedules after an earlier source write in the same render task`() {
        val destination = destinationTask(
            payload = GPUDestinationSnapshotTaskPayload(
                grouping = grouping(
                    groups = listOf(group(0, "draw.2", sourceIntermediate = null)),
                    materializations = listOf(
                        GPUDestinationSnapshotMaterialization.TextureCopy(0, BOUNDS),
                    ),
                ),
                operations = listOf(
                    textureCopyOperation(
                        groupIndex = 0,
                        groupingCommandId = "draw.2",
                        renderTaskId = "task.render.multi",
                        commandId = 2,
                    ),
                ),
            ),
        )
        val render = renderTask(
            taskId = "task.render.multi",
            packets = listOf(
                packet(1, directBlendPlan()),
                packet(2, destinationBlendPlan()),
            ),
            loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store, "transparent"),
        )

        val plan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(destination, render),
                dependencies = listOf(dependency(destination, render)),
            ),
        )

        assertFalse(plan.atomicallyRefused, plan.diagnostics.joinToString { it.code.value })
        assertEquals(3, plan.steps.size)
        val prefix = assertIs<GPUFrameStep.RenderPassStep>(plan.steps[0])
        assertEquals(listOf("packet.1"), prefix.drawPackets.map { it.packetId.value })
        assertEquals("clear", prefix.loadStore.loadOp)
        assertEquals(listOf(render.taskId), prefix.sourceTaskIds)
        val copy = assertIs<GPUFrameStep.CopyDestinationStep>(plan.steps[1])
        assertEquals(listOf(destination.taskId), copy.sourceTaskIds)
        val suffix = assertIs<GPUFrameStep.RenderPassStep>(plan.steps[2])
        assertEquals(listOf("packet.2"), suffix.drawPackets.map { it.packetId.value })
        assertEquals("load", suffix.loadStore.loadOp)
        assertEquals(listOf(render.taskId), suffix.sourceTaskIds)
    }

    @Test
    fun `destination copy is inserted at the first middle and last consumer packet`() {
        data class Case(
            val name: String,
            val consumerCommandId: Int,
            val expectedSteps: List<String>,
        )

        listOf(
            Case("first", 1, listOf("copy", "render:1,2,3:clear")),
            Case("middle", 2, listOf("render:1:clear", "copy", "render:2,3:load")),
            Case("last", 3, listOf("render:1,2:clear", "copy", "render:3:load")),
        ).forEach { case ->
            val destination = destinationTask(
                payload = GPUDestinationSnapshotTaskPayload(
                    grouping = grouping(
                        groups = listOf(
                            group(0, "draw.${case.consumerCommandId}", sourceIntermediate = null),
                        ),
                        materializations = listOf(
                            GPUDestinationSnapshotMaterialization.TextureCopy(0, BOUNDS),
                        ),
                    ),
                    operations = listOf(
                        textureCopyOperation(
                            groupIndex = 0,
                            groupingCommandId = "draw.${case.consumerCommandId}",
                            renderTaskId = "task.render.${case.name}",
                            commandId = case.consumerCommandId,
                        ),
                    ),
                ),
            )
            val render = renderTask(
                taskId = "task.render.${case.name}",
                packets = (1..3).map { commandId ->
                    packet(
                        commandId,
                        if (commandId == case.consumerCommandId) {
                            destinationBlendPlan()
                        } else {
                            directBlendPlan()
                        },
                    )
                },
                loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store, "transparent"),
            )

            val plan = GPUFramePlanner.plan(
                taskList(
                    tasks = listOf(destination, render),
                    dependencies = listOf(dependency(destination, render)),
                ),
            )

            assertFalse(plan.atomicallyRefused, "${case.name}: ${plan.diagnostics.joinToString { it.code.value }}")
            assertEquals(case.expectedSteps, plan.steps.map { it.destinationScheduleLabel() }, case.name)
        }
    }

    @Test
    fun `NoOp-only prefix does not turn the first real render after destination copy into load`() {
        val destination = destinationTask(
            payload = GPUDestinationSnapshotTaskPayload(
                grouping = grouping(
                    groups = listOf(group(0, "draw.2", sourceIntermediate = null)),
                    materializations = listOf(
                        GPUDestinationSnapshotMaterialization.TextureCopy(0, BOUNDS),
                    ),
                ),
                operations = listOf(
                    textureCopyOperation(
                        groupIndex = 0,
                        groupingCommandId = "draw.2",
                        renderTaskId = "task.render.noop-prefix",
                        commandId = 2,
                    ),
                ),
            ),
        )
        val render = renderTask(
            taskId = "task.render.noop-prefix",
            packets = listOf(packet(1, noOpBlendPlan()), packet(2, destinationBlendPlan())),
            loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store, "transparent"),
        )

        val plan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(destination, render),
                dependencies = listOf(dependency(destination, render)),
            ),
        )

        assertFalse(plan.atomicallyRefused, plan.diagnostics.joinToString { it.code.value })
        assertEquals(
            listOf("copy", "render:2:clear"),
            plan.steps.map { it.destinationScheduleLabel() },
        )
    }

    @Test
    fun `multiple copy boundaries load only after a real render was emitted`() {
        val secondSnapshot = GPUFrameTextureRef("texture.snapshot.noop-boundary.second")
        val destination = destinationTask(
            payload = GPUDestinationSnapshotTaskPayload(
                grouping = grouping(
                    groups = listOf(
                        group(0, "draw.2", sourceIntermediate = null),
                        group(1, "draw.4", sourceIntermediate = null),
                    ),
                    materializations = listOf(
                        GPUDestinationSnapshotMaterialization.TextureCopy(0, BOUNDS),
                        GPUDestinationSnapshotMaterialization.TextureCopy(1, BOUNDS),
                    ),
                ),
                operations = listOf(
                    textureCopyOperation(
                        groupIndex = 0,
                        groupingCommandId = "draw.2",
                        renderTaskId = "task.render.noop-boundaries",
                        commandId = 2,
                    ),
                    textureCopyOperation(
                        groupIndex = 1,
                        groupingCommandId = "draw.4",
                        renderTaskId = "task.render.noop-boundaries",
                        commandId = 4,
                        snapshot = secondSnapshot,
                    ),
                ),
            ),
        )
        val render = renderTask(
            taskId = "task.render.noop-boundaries",
            packets = listOf(
                packet(1, noOpBlendPlan()),
                packet(2, destinationBlendPlan()),
                packet(3, noOpBlendPlan()),
                packet(4, destinationBlendPlan()),
            ),
            loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store, "transparent"),
        )

        val plan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(destination, render),
                dependencies = listOf(dependency(destination, render)),
            ),
        )

        assertFalse(plan.atomicallyRefused, plan.diagnostics.joinToString { it.code.value })
        assertEquals(
            listOf("copy", "render:2:clear", "copy", "render:4:load"),
            plan.steps.map { it.destinationScheduleLabel() },
        )
    }

    @Test
    fun `multiple consumers of one snapshot schedule one copy in the same render task`() {
        val destination = destinationTask(
            payload = GPUDestinationSnapshotTaskPayload(
                grouping = grouping(
                    groups = listOf(group(0, "draw.1", "draw.3", sourceIntermediate = null)),
                    materializations = listOf(
                        GPUDestinationSnapshotMaterialization.TextureCopy(0, BOUNDS),
                    ),
                ),
                operations = listOf(
                    textureCopyOperation(
                        groupIndex = 0,
                        consumers = listOf(
                            consumer("draw.1", "task.render.shared", 1),
                            consumer("draw.3", "task.render.shared", 3),
                        ),
                    ),
                ),
            ),
        )
        val render = renderTask(
            taskId = "task.render.shared",
            packets = listOf(
                packet(1, destinationBlendPlan()),
                packet(2, noOpBlendPlan()),
                packet(3, destinationBlendPlan()),
            ),
        )

        val plan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(destination, render),
                dependencies = listOf(dependency(destination, render)),
            ),
        )

        assertFalse(plan.atomicallyRefused, plan.diagnostics.joinToString { it.code.value })
        assertEquals(1, plan.steps.filterIsInstance<GPUFrameStep.CopyDestinationStep>().size)
        assertEquals(
            listOf("packet.1", "packet.3"),
            plan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
                .flatMap { it.drawPackets }
                .map { it.packetId.value },
        )
    }

    @Test
    fun `two destination copies at distinct packets produce two exact render splits`() {
        val secondSnapshot = GPUFrameTextureRef("texture.snapshot.second")
        val destination = destinationTask(
            payload = GPUDestinationSnapshotTaskPayload(
                grouping = grouping(
                    groups = listOf(
                        group(0, "draw.2", sourceIntermediate = null),
                        group(1, "draw.4", sourceIntermediate = null),
                    ),
                    materializations = listOf(
                        GPUDestinationSnapshotMaterialization.TextureCopy(0, BOUNDS),
                        GPUDestinationSnapshotMaterialization.TextureCopy(1, BOUNDS),
                    ),
                ),
                operations = listOf(
                    textureCopyOperation(
                        groupIndex = 0,
                        groupingCommandId = "draw.2",
                        renderTaskId = "task.render.two-copies",
                        commandId = 2,
                    ),
                    textureCopyOperation(
                        groupIndex = 1,
                        groupingCommandId = "draw.4",
                        renderTaskId = "task.render.two-copies",
                        commandId = 4,
                        snapshot = secondSnapshot,
                    ),
                ),
            ),
        )
        val render = renderTask(
            taskId = "task.render.two-copies",
            packets = listOf(
                packet(1, directBlendPlan()),
                packet(2, destinationBlendPlan()),
                packet(3, directBlendPlan()),
                packet(4, destinationBlendPlan()),
            ),
            loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store, "transparent"),
        )

        val plan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(destination, render),
                dependencies = listOf(dependency(destination, render)),
            ),
        )

        assertFalse(plan.atomicallyRefused, plan.diagnostics.joinToString { it.code.value })
        assertEquals(
            listOf(
                "render:1:clear",
                "copy",
                "render:2,3:load",
                "copy",
                "render:4:load",
            ),
            plan.steps.map { it.destinationScheduleLabel() },
        )
        assertEquals(
            listOf(SNAPSHOT_TEXTURE, secondSnapshot),
            plan.steps.filterIsInstance<GPUFrameStep.CopyDestinationStep>().map { it.snapshot },
        )
    }

    @Test
    fun `source write after copy and before the last exact consumer refuses atomically`() {
        val destination = destinationTask(
            payload = GPUDestinationSnapshotTaskPayload(
                grouping = grouping(
                    groups = listOf(group(0, "draw.1", "draw.3", sourceIntermediate = null)),
                    materializations = listOf(
                        GPUDestinationSnapshotMaterialization.TextureCopy(0, BOUNDS),
                    ),
                ),
                operations = listOf(
                    textureCopyOperation(
                        groupIndex = 0,
                        consumers = listOf(
                            consumer("draw.1", "task.render.unsafe", 1),
                            consumer("draw.3", "task.render.unsafe", 3),
                        ),
                    ),
                ),
            ),
        )
        val render = renderTask(
            taskId = "task.render.unsafe",
            packets = listOf(
                packet(1, destinationBlendPlan()),
                packet(2, directBlendPlan()),
                packet(3, destinationBlendPlan()),
            ),
        )

        val plan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(destination, render),
                dependencies = listOf(dependency(destination, render)),
            ),
        )

        assertTrue(plan.atomicallyRefused)
        assertTrue(plan.steps.isEmpty())
        assertEquals("invalid.frame_plan.destination_order", plan.diagnostics.last().code.value)
    }

    @Test
    fun `destination consumer target must match the exact Task 5 target`() {
        val destination = destinationTask(
            payload = GPUDestinationSnapshotTaskPayload(
                grouping = grouping(
                    groups = listOf(group(0, "draw.7", sourceIntermediate = null)),
                    materializations = listOf(
                        GPUDestinationSnapshotMaterialization.TextureCopy(0, BOUNDS),
                    ),
                ),
                operations = listOf(textureCopyOperation(groupIndex = 0)),
            ),
        )
        val wrongTargetConsumer = renderTask(
            taskId = "task.render.7",
            commandId = 7,
            target = GPUFrameTargetRef("target.other"),
        )

        val plan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(destination, wrongTargetConsumer),
                dependencies = listOf(dependency(destination, wrongTargetConsumer)),
            ),
        )

        assertTrue(plan.atomicallyRefused)
        assertTrue(plan.steps.isEmpty())
        assertEquals("invalid.frame_plan.destination_consumer_binding", plan.diagnostics.last().code.value)
    }

    @Test
    fun `destination consumers must follow Task 5 access order`() {
        val destination = destinationTask(
            payload = GPUDestinationSnapshotTaskPayload(
                grouping = grouping(
                    groups = listOf(group(0, "draw.1", "draw.2", sourceIntermediate = null)),
                    materializations = listOf(
                        GPUDestinationSnapshotMaterialization.TextureCopy(0, BOUNDS),
                    ),
                ),
                operations = listOf(
                    textureCopyOperation(
                        groupIndex = 0,
                        consumers = listOf(
                            consumer("draw.1", "task.render.late", 1),
                            consumer("draw.2", "task.render.early", 2),
                        ),
                    ),
                ),
            ),
        )
        val early = renderTask("task.render.early", 2)
        val late = renderTask("task.render.late", 1)

        val plan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(destination, early, late),
                dependencies = listOf(
                    dependency(destination, early),
                    dependency(destination, late),
                    dependency(early, late),
                ),
            ),
        )

        assertTrue(plan.atomicallyRefused)
        assertTrue(plan.steps.isEmpty())
        assertEquals("invalid.frame_plan.destination_consumer_binding", plan.diagnostics.last().code.value)
    }

    @Test
    fun `render continuation after destination copy loads preserved target contents`() {
        val first = renderTask(
            taskId = "task.render.clear-first",
            commandId = 40,
            blendPlan = directBlendPlan(),
            loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store, "transparent"),
        )
        val destination = destinationTask(
            payload = GPUDestinationSnapshotTaskPayload(
                grouping = grouping(
                    groups = listOf(group(0, "draw.41", sourceIntermediate = null)),
                    materializations = listOf(
                        GPUDestinationSnapshotMaterialization.TextureCopy(0, BOUNDS),
                    ),
                ),
                operations = listOf(
                    textureCopyOperation(
                        groupIndex = 0,
                        groupingCommandId = "draw.41",
                        renderTaskId = "task.render.destination-consumer",
                        commandId = 41,
                    ),
                ),
            ),
        )
        val consumer = renderTask(
            taskId = "task.render.destination-consumer",
            commandId = 41,
            loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store, "transparent"),
        )

        val plan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(first, destination, consumer),
                dependencies = listOf(dependency(first, destination), dependency(destination, consumer)),
            ),
        )

        assertFalse(plan.atomicallyRefused)
        assertEquals(
            listOf("clear", "load"),
            plan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().map { it.loadStore.loadOp },
        )
    }

    private fun assertAtomicConsumerBindingRefusal(taskList: GPUTaskList) {
        val plan = GPUFramePlanner.plan(taskList)

        assertTrue(plan.atomicallyRefused)
        assertTrue(plan.steps.isEmpty())
        assertEquals("invalid.frame_plan.destination_consumer_binding", plan.diagnostics.last().code.value)
    }

    private fun textureCopyPlan(
        group: GPUDestinationSnapshotGroup,
        groupingCommandId: String,
    ): GPUFramePlan {
        val destination = destinationTask(
            payload = GPUDestinationSnapshotTaskPayload(
                grouping = grouping(
                    groups = listOf(group),
                    materializations = listOf(
                        GPUDestinationSnapshotMaterialization.TextureCopy(0, BOUNDS),
                    ),
                ),
                operations = listOf(
                    textureCopyOperation(
                        groupIndex = 0,
                        consumers = listOf(consumer(groupingCommandId, "task.render.7", 7)),
                    ),
                ),
            ),
        )
        val render = renderTask("task.render.7", 7)
        return GPUFramePlanner.plan(
            taskList(
                tasks = listOf(destination, render),
                dependencies = listOf(dependency(destination, render)),
            ),
        )
    }

    private fun copyAsDrawPayload(source: GPUFrameResourceRef): GPUDestinationSnapshotTaskPayload =
        GPUDestinationSnapshotTaskPayload(
            grouping = grouping(
                groups = listOf(group(0, "draw.8", sourceIntermediate = SOURCE_INTERMEDIATE)),
                materializations = listOf(
                    CopyAsDrawMaterialization(0, BOUNDS, SOURCE_INTERMEDIATE),
                ),
            ),
            operations = listOf(
                copyAsDrawOperation(
                    groupIndex = 0,
                    groupingCommandId = "draw.8",
                    renderTaskId = "task.render.8",
                    commandId = 8,
                    source = source,
                ),
            ),
        )

    private fun twoTargetSharedSnapshotPayload(
        firstGroupingCommandIds: List<String>,
        firstConsumers: List<GPUDestinationSnapshotConsumerRef>,
        secondGroupingCommandId: String,
        secondConsumer: GPUDestinationSnapshotConsumerRef,
    ): GPUDestinationSnapshotTaskPayload =
        GPUDestinationSnapshotTaskPayload(
            grouping = grouping(
                groups = listOf(
                    group(
                        groupIndex = 0,
                        groupingCommandIds = firstGroupingCommandIds.toTypedArray(),
                        sourceIntermediate = null,
                        target = TARGET_A,
                    ),
                    group(
                        groupIndex = 1,
                        groupingCommandIds = arrayOf(secondGroupingCommandId),
                        sourceIntermediate = null,
                        target = TARGET_B,
                    ),
                ),
                materializations = listOf(
                    GPUDestinationSnapshotMaterialization.TextureCopy(0, BOUNDS),
                    GPUDestinationSnapshotMaterialization.TextureCopy(1, BOUNDS),
                ),
            ),
            operations = listOf(
                textureCopyOperation(
                    groupIndex = 0,
                    source = TARGET_A,
                    snapshot = SHARED_SNAPSHOT_TEXTURE,
                    consumers = firstConsumers,
                ),
                textureCopyOperation(
                    groupIndex = 1,
                    source = TARGET_B,
                    snapshot = SHARED_SNAPSHOT_TEXTURE,
                    consumers = listOf(secondConsumer),
                ),
            ),
        )

    private fun copyAsDrawDestinationTask(): GPUTask.DestinationSnapshots =
        destinationTask(
            payload = GPUDestinationSnapshotTaskPayload(
                grouping = grouping(
                    groups = listOf(group(0, "draw.8", sourceIntermediate = SOURCE_INTERMEDIATE)),
                    materializations = listOf(
                        CopyAsDrawMaterialization(0, BOUNDS, SOURCE_INTERMEDIATE),
                    ),
                ),
                operations = listOf(
                    copyAsDrawOperation(
                        groupIndex = 0,
                        groupingCommandId = "draw.8",
                        renderTaskId = "task.render.8",
                        commandId = 8,
                    ),
                ),
            ),
        )

    private fun destinationTask(payload: GPUDestinationSnapshotTaskPayload): GPUTask.DestinationSnapshots =
        GPUTask.DestinationSnapshots(
            taskId = GPUTaskID("task.destination"),
            recordingId = RECORDING_ID,
            phase = GPUTaskPhase.Copy,
            payload = payload,
        )

    private fun textureCopyOperation(
        groupIndex: Int,
        groupingCommandId: String = "draw.7",
        renderTaskId: String = "task.render.7",
        commandId: Int = 7,
        source: GPUFrameResourceRef = SCENE_TARGET,
        snapshot: GPUFrameTextureRef = SNAPSHOT_TEXTURE,
        consumers: List<GPUDestinationSnapshotConsumerRef> = listOf(
            consumer(groupingCommandId, renderTaskId, commandId),
        ),
    ): GPUDestinationSnapshotOperation.TextureCopy =
        GPUDestinationSnapshotOperation.TextureCopy(
            groupIndex = groupIndex,
            source = source,
            snapshot = snapshot,
            logicalBounds = BOUNDS,
            copyLayout = GPUTextureCopyLayout(bytesPerRow = 256, rowsPerImage = 4),
            consumers = consumers,
        )

    private fun copyAsDrawOperation(
        groupIndex: Int,
        groupingCommandId: String,
        renderTaskId: String,
        commandId: Int,
        source: GPUFrameResourceRef = SOURCE_TEXTURE,
        snapshot: GPUFrameTextureRef = SNAPSHOT_TEXTURE,
    ): GPUDestinationSnapshotOperation.CopyAsDraw =
        GPUDestinationSnapshotOperation.CopyAsDraw(
            groupIndex = groupIndex,
            source = source,
            sourceIntermediate = SOURCE_INTERMEDIATE,
            snapshot = snapshot,
            logicalBounds = BOUNDS,
            consumers = listOf(consumer(groupingCommandId, renderTaskId, commandId)),
        )

    private fun consumer(
        groupingCommandId: String,
        renderTaskId: String,
        commandId: Int,
    ): GPUDestinationSnapshotConsumerRef =
        GPUDestinationSnapshotConsumerRef(
            groupingCommandId = groupingCommandId,
            renderTaskId = GPUTaskID(renderTaskId),
            packetId = GPUDrawPacketID("packet.$commandId"),
            commandId = GPUDrawCommandID(commandId),
        )

    private fun grouping(
        groups: List<GPUDestinationSnapshotGroup>,
        materializations: List<GPUDestinationSnapshotMaterialization>,
    ): GPUDestinationSnapshotGroupingResult =
        GPUDestinationSnapshotGroupingResult(
            groups = groups,
            materializations = materializations,
            totalCopiedBytes = groups.sumOf(GPUDestinationSnapshotGroup::copiedBytes),
            refusals = emptyList(),
            decisionDump = listOf("fixture"),
        )

    private fun group(
        groupIndex: Int,
        vararg groupingCommandIds: String,
        sourceIntermediate: GPUIntermediateIdentity?,
        target: GPUFrameTargetRef = SCENE_TARGET,
        sampleContinuation: GPUSampleContinuationKey? = null,
    ): GPUDestinationSnapshotGroup =
        GPUDestinationSnapshotGroup(
            key = destinationGroupKey(sourceIntermediate, target, sampleContinuation),
            logicalBounds = BOUNDS,
            members = groupingCommandIds.mapIndexed { memberIndex, groupingCommandId ->
                GPUDestinationReadMember(
                    commandId = groupingCommandId,
                    accessIndex = groupIndex * 10 + memberIndex,
                    logicalBounds = BOUNDS,
                )
            },
            copiedBytes = 64,
            decisionDump = listOf("group:$groupIndex"),
        )

    private fun destinationGroupKey(
        sourceIntermediate: GPUIntermediateIdentity?,
        target: GPUFrameTargetRef,
        sampleContinuation: GPUSampleContinuationKey? = null,
    ): GPUDestinationSnapshotGroupKey =
        GPUDestinationSnapshotGroupKey(
            target = GPUTargetIdentity(target.value),
            targetGeneration = 2,
            deviceGeneration = DEVICE_GENERATION,
            format = GPUColorFormat("rgba8unorm"),
            colorInterpretation = GPUColorInterpretation("srgb-premul"),
            sampleContinuation = sampleContinuation,
            sourceIntermediate = sourceIntermediate,
        )

    private fun continuationKey(
        target: GPUFrameTargetRef = SCENE_TARGET,
        samplePlan: GPUSamplePlan.MultisampleFrame = GPUSamplePlan.MultisampleFrame(4),
    ): GPUSampleContinuationKey = GPUSampleContinuationKey(
        target = GPUTargetIdentity(target.value),
        targetGeneration = 2,
        deviceGeneration = DEVICE_GENERATION,
        colorFormat = GPUColorFormat("rgba8unorm"),
        colorInterpretation = GPUColorInterpretation("srgb-premul"),
        samplePlan = samplePlan,
        colorAttachment = GPUTargetIdentity("${target.value}.msaa"),
        depthStencilAttachment = null,
    )

    private fun renderTask(
        taskId: String,
        commandId: Int,
        target: GPUFrameTargetRef = SCENE_TARGET,
        blendPlan: GPUBlendPlan = destinationBlendPlan(),
        loadStore: GPULoadStorePlan = GPULoadStorePlan("load", GPUStorePlan.Store),
        samplePlan: GPUSamplePlan = GPUSamplePlan.SingleSampleFrame,
        sampleContinuationKey: GPUSampleContinuationKey? = null,
    ): GPUTask.Render {
        val packet = packet(commandId, blendPlan)
        return GPUTask.Render(
            taskId = GPUTaskID(taskId),
            recordingId = RECORDING_ID,
            phase = GPUTaskPhase.Render,
            target = target,
            loadStore = loadStore,
            samplePlan = samplePlan,
            drawPackets = listOf(packet),
            batchEligibilityByPacketId = mapOf(
                packet.packetId to GPUPassBatchEligibility(
                    kind = GPUPassBatchKind.SolidFill,
                    queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                ),
            ),
            sampleContinuationKey = sampleContinuationKey,
        )
    }

    private fun renderTask(
        taskId: String,
        packets: List<GPUDrawPacket>,
        target: GPUFrameTargetRef = SCENE_TARGET,
        loadStore: GPULoadStorePlan = GPULoadStorePlan("load", GPUStorePlan.Store),
    ): GPUTask.Render = GPUTask.Render(
        taskId = GPUTaskID(taskId),
        recordingId = RECORDING_ID,
        phase = GPUTaskPhase.Render,
        target = target,
        loadStore = loadStore,
        samplePlan = GPUSamplePlan.SingleSampleFrame,
        drawPackets = packets,
        batchEligibilityByPacketId = packets.associate { packet ->
            packet.packetId to GPUPassBatchEligibility(
                kind = GPUPassBatchKind.SolidFill,
                queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
            )
        },
    )

    private fun GPUFrameStep.destinationScheduleLabel(): String = when (this) {
        is GPUFrameStep.RenderPassStep ->
            "render:${drawPackets.joinToString(",") { it.commandIdValue.toString() }}:${loadStore.loadOp}"
        is GPUFrameStep.CopyDestinationStep -> "copy"
        else -> this::class.simpleName.orEmpty()
    }

    private fun packet(
        commandId: Int,
        blendPlan: GPUBlendPlan = destinationBlendPlan(),
    ): GPUDrawPacket =
        GPUDrawPacket(
            packetId = GPUDrawPacketID("packet.$commandId"),
            commandIdValue = commandId,
            analysisRecordId = "analysis.$commandId",
            passId = "pass.scene",
            layerId = "root",
            bindingListId = "bindings.$commandId",
            insertionReasonCode = "paint-order",
            sortKey = commandId.toLong(),
            sortKeyPreimage = "paint-order:$commandId",
            renderStepId = GPURenderStepID("rect.fill"),
            renderStepVersion = 1,
            role = GPUDrawPacketRole.Shading,
            blendPlan = blendPlan,
            renderPipelineKey = GPURenderPipelineKey("pipeline.rect.dst-read"),
            bindingLayoutHash = "layout.rect.dst-read",
            vertexSourceLabel = "vertices.rect",
            targetStateHash = "target.scene.rgba8",
            originalPaintOrder = commandId,
            resourceGeneration = DEVICE_GENERATION.value,
        )

    private fun destinationBlendPlan(): GPUBlendPlan =
        GPUBlendPlan.ShaderBlendWithDstRead(
            mode = GPUBlendMode.OVERLAY,
            formulaId = "overlay",
            sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
        )

    private fun directBlendPlan(): GPUBlendPlan =
        GPUBlendPlan.ShaderBlendNoDstRead(
            mode = GPUBlendMode.SRC_OVER,
            formulaId = "src-over",
            sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
        )

    private fun noOpBlendPlan(): GPUBlendPlan =
        GPUBlendPlan.NoOp(GPUBlendMode.DST, "destination unchanged")

    private fun dependency(from: GPUTask, to: GPUTask): GPUTaskDependency =
        GPUTaskDependency(
            fromTaskId = from.taskId,
            toTaskId = to.taskId,
            dependencyKind = "destination-consumer",
            useToken = GPUTaskUseToken("${from.taskId.value}->${to.taskId.value}"),
            reasonCode = "destination.snapshot.before.consumer",
        )

    private fun taskList(
        tasks: List<GPUTask>,
        dependencies: List<GPUTaskDependency>,
        capabilitySeal: GPUFrameCapabilitySeal = frameCapabilitySeal(copyAsDraw = null),
        recordingCapabilitySealHash: String = capabilitySeal.sealHash,
    ): GPUTaskList =
        GPUTaskList(
            frameId = FRAME_ID,
            recordingSeals = listOf(recordingSeal(recordingCapabilitySealHash)),
            expectedReplayKeyHash = "replay.same",
            tasks = tasks,
            dependencies = dependencies,
            phaseOrder = GPUTaskPhase.entries,
            memoryBudget = emptyBudget(),
            capabilitySeal = capabilitySeal,
        )

    private fun recordingSeal(capabilitySealHash: String): GPURecordingSeal =
        GPURecordingSeal(
            recordingId = RECORDING_ID,
            insertionOrder = 0,
            compatibilityKeyHash = "compat.same",
            replayKeyHash = "replay.same",
            capabilitySealHash = capabilitySealHash,
        )

    private fun copyAsDrawSeal(
        deviceGeneration: GPUDeviceGenerationID = DEVICE_GENERATION,
        implementationVersion: String = "copy-as-draw-v2",
    ): GPUFrameCapabilitySeal =
        frameCapabilitySeal(
            deviceGeneration = deviceGeneration,
            copyAsDraw = GPUCopyAsDrawImplementationCapability(
                implementationId = "kanvas.copy-as-draw",
                implementationVersion = implementationVersion,
                available = true,
            ),
        )

    private fun frameCapabilitySeal(
        deviceGeneration: GPUDeviceGenerationID = DEVICE_GENERATION,
        copyAsDraw: GPUCopyAsDrawImplementationCapability?,
    ): GPUFrameCapabilitySeal =
        GPUFrameCapabilitySeal.capture(
            frameId = FRAME_ID,
            deviceGeneration = deviceGeneration,
            capabilities = GPUCapabilities(
                implementation = GPUImplementationIdentity(
                    facadeName = "wgpu4k",
                    implementationName = "kanvas-test",
                    adapterName = "fixture-adapter",
                    deviceName = "fixture-device",
                ),
                facts = emptyList(),
                snapshotId = "capabilities.destination.fixture",
                copyAsDrawCapability = copyAsDraw,
            ),
        )

    private fun emptyBudget(): GPUFrameMemoryBudgetPlan =
        GPUFrameMemoryBudgetPlan(
            peakFrameTransientBytes = 0,
            targetResidentBytes = 0,
            categoryTotals = GPUFrameMemoryCategory.entries.associateWith { 0L },
            deviceLimitFacts = emptyList(),
            configuredAggregateBudgetBytes = 1,
            diagnostic = null,
        )

    private companion object {
        val FRAME_ID = GPUFrameID(41)
        val RECORDING_ID = GPURecordingID("recording.destination")
        val DEVICE_GENERATION = GPUDeviceGenerationID(7)
        val BOUNDS = GPUPixelBounds(0, 0, 4, 4)
        val SCENE_TARGET = GPUFrameTargetRef("target.scene")
        val SOURCE_TEXTURE = GPUFrameTextureRef("texture.intermediate.source")
        val SNAPSHOT_TEXTURE = GPUFrameTextureRef("texture.snapshot")
        val SHARED_SNAPSHOT_TEXTURE = GPUFrameTextureRef("texture.snapshot.shared")
        val TARGET_A = GPUFrameTargetRef("target.a")
        val TARGET_B = GPUFrameTargetRef("target.b")
        val SOURCE_INTERMEDIATE = GPUIntermediateIdentity("intermediate.source")
    }
}
