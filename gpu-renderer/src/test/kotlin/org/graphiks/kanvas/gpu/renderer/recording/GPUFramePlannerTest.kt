package org.graphiks.kanvas.gpu.renderer.recording

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCopyAsDrawImplementationCapability
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
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
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
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
import org.graphiks.kanvas.gpu.renderer.passes.GPURefusalScope
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleContinuationKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceCopyRegion
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureCopyLayout
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUTargetIdentity

class GPUFramePlannerTest {
    @Test
    fun `readback request validates and preserves a non-zero buffer offset`() {
        val request = GPUFrameReadbackRequest(
            requestId = GPUReadbackRequestID("readback.offset"),
            sourceBounds = GPUPixelBounds(0, 0, 16, 8),
            pixelFormat = GPUReadbackPixelFormat.Rgba8Unorm,
            outputColorInterpretation = GPUColorInterpretation("srgb-premul"),
            bufferOffsetBytes = 512,
        )

        assertEquals(512, request.bufferOffsetBytes)
        assertFailsWith<IllegalArgumentException> {
            request.copy(bufferOffsetBytes = -1)
        }
    }

    @Test
    fun `stable recording insertion and phase order produce one adjacent render segment`() {
        val capabilitySeal = frameCapabilitySeal()
        val first = renderTask(
            taskId = "task.render.1",
            recordingId = "recording.second",
            commandId = 1,
        )
        val second = renderTask(
            taskId = "task.render.2",
            recordingId = "recording.first",
            commandId = 2,
        )
        val taskList = GPUTaskList(
            frameId = FRAME_ID,
            capabilitySeal = capabilitySeal,
            recordingSeals = listOf(
                seal("recording.first", insertionOrder = 0, capabilitySealHash = capabilitySeal.sealHash),
                seal("recording.second", insertionOrder = 1, capabilitySealHash = capabilitySeal.sealHash),
            ),
            expectedReplayKeyHash = "replay.same",
            tasks = listOf(first, second),
            dependencies = emptyList(),
            phaseOrder = listOf(GPUTaskPhase.Render),
            memoryBudget = emptyBudget(),
        )

        val plan = GPUFramePlanner.plan(taskList)

        val render = assertIs<GPUFrameStep.RenderPassStep>(plan.steps.single())
        assertEquals(listOf(2, 1), render.drawPackets.map { it.commandIdValue })
        assertEquals(
            listOf(GPUTaskID("task.render.2"), GPUTaskID("task.render.1")),
            render.sourceTaskIds,
        )
        assertEquals(listOf("recording.first", "recording.second"), plan.recordingSeals.map { it.recordingId.value })
    }

    @Test
    fun `cycle and seal mismatches refuse the whole plan without encodable steps`() {
        val first = renderTask("task.a", "recording.a", 1)
        val second = renderTask("task.b", "recording.a", 2)
        val cycle = taskList(
            tasks = listOf(first, second),
            dependencies = listOf(
                dependency(first, second),
                dependency(second, first),
            ),
        )

        val cyclePlan = GPUFramePlanner.plan(cycle)

        assertTrue(cyclePlan.atomicallyRefused)
        assertTrue(cyclePlan.steps.isEmpty())
        assertEquals("invalid.frame_plan.task_cycle", cyclePlan.diagnostics.last().code.value)

        val replayMismatch = taskList(
            tasks = listOf(first),
            seals = listOf(seal("recording.a", 0).copy(replayKeyHash = "replay.other")),
        )
        val replayPlan = GPUFramePlanner.plan(replayMismatch)
        assertTrue(replayPlan.atomicallyRefused)
        assertEquals("replay.compatibility_key_mismatch", replayPlan.diagnostics.last().code.value)

        val incompatible = taskList(
            tasks = listOf(
                first,
                renderTask("task.other", "recording.b", 3),
            ),
            seals = listOf(
                seal("recording.a", 0),
                seal("recording.b", 1).copy(compatibilityKeyHash = "compat.other"),
            ),
        )
        val incompatiblePlan = GPUFramePlanner.plan(incompatible)
        assertTrue(incompatiblePlan.atomicallyRefused)
        assertEquals("recording.compatibility_key_mismatch", incompatiblePlan.diagnostics.last().code.value)
    }

    @Test
    fun `explicit dependencies preserve pass copy pass and target hazards cut render segments`() {
        val first = renderTask("task.render.a", "recording.a", 1)
        val copy = GPUTask.Copy(
            taskId = GPUTaskID("task.copy"),
            recordingId = GPURecordingID("recording.a"),
            phase = GPUTaskPhase.Copy,
            source = GPUFrameTargetRef("target.scene"),
            destination = GPUFrameTextureRef("texture.snapshot"),
            regions = listOf(GPUResourceCopyRegion(0, 0, GPUPixelBounds(0, 0, 4, 4), 64)),
        )
        val second = renderTask("task.render.b", "recording.a", 2)
        val ordered = taskList(
            tasks = listOf(second, copy, first),
            dependencies = listOf(
                GPUTaskDependency(first.taskId, copy.taskId, "paint-order", null, "test"),
                GPUTaskDependency(copy.taskId, second.taskId, "paint-order", null, "test"),
            ),
        )

        val plan = GPUFramePlanner.plan(ordered)

        assertEquals(
            listOf(
                GPUFrameStep.RenderPassStep::class,
                GPUFrameStep.CopyResourceStep::class,
                GPUFrameStep.RenderPassStep::class,
            ),
            plan.steps.map { it::class },
        )

        val otherTarget = renderTask("task.render.other", "recording.a", 3, target = "target.other")
        val targetPlan = GPUFramePlanner.plan(taskList(tasks = listOf(first, otherTarget)))
        assertEquals(2, targetPlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().size)
    }

    @Test
    fun `layer transition readback and output expand into the closed ordered step algebra`() {
        val enter = GPUTask.TargetTransition(
            taskId = GPUTaskID("task.transition.enter"),
            recordingId = GPURecordingID("recording.a"),
            phase = GPUTaskPhase.Transition,
            parent = GPUFrameTargetRef("target.scene"),
            child = GPUFrameTargetRef("target.layer"),
            transitionKind = GPUTargetTransitionKind.EnterChild,
        )
        val composite = GPUTask.TargetTransition(
            taskId = GPUTaskID("task.transition.composite"),
            recordingId = GPURecordingID("recording.a"),
            phase = GPUTaskPhase.Transition,
            parent = GPUFrameTargetRef("target.scene"),
            child = GPUFrameTargetRef("target.layer"),
            transitionKind = GPUTargetTransitionKind.CompositeChild,
        )
        val returnToParent = GPUTask.TargetTransition(
            taskId = GPUTaskID("task.transition.return"),
            recordingId = GPURecordingID("recording.a"),
            phase = GPUTaskPhase.Transition,
            parent = GPUFrameTargetRef("target.scene"),
            child = GPUFrameTargetRef("target.layer"),
            transitionKind = GPUTargetTransitionKind.ReturnToParent,
        )
        val readback = GPUTask.Readback(
            taskId = GPUTaskID("task.readback"),
            recordingId = GPURecordingID("recording.a"),
            phase = GPUTaskPhase.Readback,
            source = GPUFrameTargetRef("target.scene"),
            staging = org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef("buffer.readback"),
            request = GPUFrameReadbackRequest(
                requestId = GPUReadbackRequestID("request.readback"),
                sourceBounds = GPUPixelBounds(0, 0, 8, 8),
                pixelFormat = GPUReadbackPixelFormat.Rgba8Unorm,
                outputColorInterpretation = GPUColorInterpretation("srgb-premul"),
                bufferOffsetBytes = 256,
            ),
        )
        val output = GPUTask.Output(
            taskId = GPUTaskID("task.output"),
            recordingId = GPURecordingID("recording.a"),
            phase = GPUTaskPhase.Output,
            scene = GPUFrameTargetRef("target.scene"),
            descriptor = GPUSurfaceOutputDescriptor(
                output = GPUSurfaceOutputRef("surface.output"),
                width = 8,
                height = 8,
                format = GPUColorFormat("rgba8unorm"),
                targetGeneration = 4,
            ),
        )

        val plan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(output, readback, returnToParent, composite, enter),
                dependencies = listOf(
                    GPUTaskDependency(enter.taskId, composite.taskId, "order", null, "test"),
                    GPUTaskDependency(composite.taskId, returnToParent.taskId, "order", null, "test"),
                    GPUTaskDependency(returnToParent.taskId, readback.taskId, "order", null, "test"),
                    GPUTaskDependency(readback.taskId, output.taskId, "order", null, "test"),
                ),
            ),
        )

        assertEquals(
            listOf(
                GPUFrameStep.TargetTransitionStep::class,
                GPUFrameStep.TargetTransitionStep::class,
                GPUFrameStep.TargetTransitionStep::class,
                GPUFrameStep.ReadbackCopyStep::class,
                GPUFrameStep.AcquireSurfaceOutput::class,
                GPUFrameStep.SurfaceBlitRenderPassStep::class,
                GPUFrameStep.PostSubmitPresentAction::class,
            ),
            plan.steps.map { it::class },
        )
        assertEquals(256, assertIs<GPUFrameStep.ReadbackCopyStep>(plan.steps[3]).request.bufferOffsetBytes)
    }

    @Test
    fun `plan snapshots caller collections and keeps repeatable dump and hash`() {
        val capabilitySeal = frameCapabilitySeal()
        val tasks = mutableListOf<GPUTask>(renderTask("task.render", "recording.a", 1))
        val seals = mutableListOf(seal("recording.a", 0))
        val diagnostics = mutableListOf(diagnostic("test.info", terminal = false))
        val categoryTotals = GPUFrameMemoryCategory.entries.associateWith { 0L }.toMutableMap()
        val memoryBudget = GPUFrameMemoryBudgetPlan(
            peakFrameTransientBytes = 0,
            targetResidentBytes = 0,
            categoryTotals = categoryTotals,
            deviceLimitFacts = emptyList(),
            configuredAggregateBudgetBytes = 1,
            diagnostic = null,
        )
        val taskList = GPUTaskList(
            frameId = FRAME_ID,
            capabilitySeal = capabilitySeal,
            recordingSeals = seals,
            expectedReplayKeyHash = "replay.same",
            tasks = tasks,
            dependencies = emptyList(),
            phaseOrder = GPUTaskPhase.entries,
            memoryBudget = memoryBudget,
            diagnostics = diagnostics,
        )
        val plan = GPUFramePlanner.plan(taskList)
        val dump = plan.dumpLines()
        val hash = plan.stableHash()

        tasks.clear()
        seals.clear()
        diagnostics.clear()
        categoryTotals[GPUFrameMemoryCategory.DestinationSnapshot] = 99

        assertEquals(dump, plan.dumpLines())
        assertEquals(hash, plan.stableHash())
        assertEquals(1, plan.steps.size)
        assertEquals(1, plan.recordingSeals.size)
        assertEquals(1, plan.diagnostics.size)
        assertEquals(0, plan.memoryBudget.categoryTotals.getValue(GPUFrameMemoryCategory.DestinationSnapshot))
    }

    @Test
    fun `leaf refusal is isolated while composite refusal consumes child provenance`() {
        val leaf = refusedTask(
            taskId = "task.refused.leaf",
            commandId = 10,
            scope = GPURefusalScope.RefusedLeafDrawStep,
        )
        val leafPlan = GPUFramePlanner.plan(taskList(tasks = listOf(leaf)))
        assertFalse(leafPlan.atomicallyRefused)
        assertEquals(10, assertIs<GPUFrameStep.RefusedLeafDrawStep>(leafPlan.steps.single()).commandId.value)

        val scopeId = GPUCompositeScopeID("scope.composite.12")
        val provenanceToken = GPUCompositeProvenanceToken("child.11")
        val child = renderTask(
            "task.child",
            "recording.a",
            11,
            compositeMembership = GPUTaskCompositeMembership(
                scopeId = scopeId,
                parentCommandId = GPUDrawCommandID(12),
                childOrdinal = 0,
                provenanceToken = provenanceToken,
            ),
        )
        val composite = refusedTask(
            taskId = "task.refused.composite",
            commandId = 12,
            scope = GPURefusalScope.RefusedCompositeCommand,
            provenance = listOf(provenanceToken),
            consumedChildren = listOf(child.taskId),
            compositeScopeId = scopeId,
        )
        val compositePlan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(composite, child),
                dependencies = listOf(dependency(child, composite)),
            ),
        )
        val refusedComposite = assertIs<GPUFrameStep.RefusedCompositeCommandStep>(compositePlan.steps.single())
        assertEquals(listOf(child.taskId, composite.taskId), refusedComposite.sourceTaskIds)
        assertEquals(listOf("child.11"), refusedComposite.provenanceTokens.map { it.value })
    }

    @Test
    fun `composite child dependency leaking outside the scope refuses atomically`() {
        val scopeId = GPUCompositeScopeID("scope.composite.3")
        val provenanceToken = GPUCompositeProvenanceToken("child.1")
        val child = renderTask(
            "task.child",
            "recording.a",
            1,
            compositeMembership = GPUTaskCompositeMembership(
                scopeId = scopeId,
                parentCommandId = GPUDrawCommandID(3),
                childOrdinal = 0,
                provenanceToken = provenanceToken,
            ),
        )
        val external = renderTask("task.external", "recording.a", 2)
        val composite = refusedTask(
            taskId = "task.composite",
            commandId = 3,
            scope = GPURefusalScope.RefusedCompositeCommand,
            provenance = listOf(provenanceToken),
            consumedChildren = listOf(child.taskId),
            compositeScopeId = scopeId,
        )
        val plan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(child, external, composite),
                dependencies = listOf(
                    dependency(child, external),
                    dependency(child, composite),
                ),
            ),
        )

        assertTrue(plan.atomicallyRefused)
        assertTrue(plan.steps.isEmpty())
        assertEquals("invalid.frame_plan.atomic_order", plan.diagnostics.last().code.value)
    }

    @Test
    fun `destination copy uses typed layout and copy-as-draw requires exact capability`() {
        val textureCopy = destinationTask(
            materialization = GPUDestinationSnapshotMaterialization.TextureCopy(
                groupIndex = 0,
                logicalBounds = GPUPixelBounds(0, 0, 4, 4),
            ),
        )
        val consumer = renderTask(
            "task.render.7",
            "recording.a",
            7,
            blendPlan = GPUBlendPlan.ShaderBlendWithDstRead(
                mode = GPUBlendMode.OVERLAY,
                formulaId = "overlay",
                sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
            ),
        )
        val copyPlan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(textureCopy, consumer),
                dependencies = listOf(dependency(textureCopy, consumer)),
            ),
        )
        assertIs<GPUFrameStep.CopyDestinationStep>(copyPlan.steps.first())
        assertTrue(copyPlan.steps.none { it is GPUFrameStep.CopyAsDrawMaterializationStep })

        val copyAsDraw = destinationTask(
            materialization = CopyAsDrawMaterialization(
                groupIndex = 0,
                logicalBounds = GPUPixelBounds(0, 0, 4, 4),
                sourceIntermediate = GPUIntermediateIdentity("intermediate.source"),
            ),
        )
        val unavailablePlan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(copyAsDraw, consumer),
                dependencies = listOf(dependency(copyAsDraw, consumer)),
            ),
        )
        assertTrue(unavailablePlan.atomicallyRefused)
        assertEquals("unsupported.destination_read.copy_unavailable", unavailablePlan.diagnostics.last().code.value)
        assertTrue(unavailablePlan.steps.none { it.executionKind == GPUFrameStepExecutionKind.Encoder })

        val available = destinationTask(
            materialization = CopyAsDrawMaterialization(
                groupIndex = 0,
                logicalBounds = GPUPixelBounds(0, 0, 4, 4),
                sourceIntermediate = GPUIntermediateIdentity("intermediate.source"),
            ),
        )
        val availableSeal = frameCapabilitySeal(
            GPUCopyAsDrawImplementationCapability("kanvas.copy-as-draw", "1", available = true),
        )
        val availablePlan = GPUFramePlanner.plan(
            taskList(
                tasks = listOf(available, consumer),
                dependencies = listOf(dependency(available, consumer)),
                capabilitySeal = availableSeal,
            ),
        )
        assertIs<GPUFrameStep.CopyAsDrawMaterializationStep>(availablePlan.steps.first())
    }

    @Test
    fun `texture preparation keeps complete typed scratch key facts`() {
        val descriptor = GPUFrameTextureDescriptor(
            logicalBounds = GPUPixelBounds(1, 2, 9, 10),
            format = GPUColorFormat("rgba8unorm"),
            sampleCount = 4,
        )
        val request = GPUResourcePreparationRequest(
            resource = GPUFrameTextureRef("texture.scratch"),
            descriptor = descriptor,
            role = GPUFrameResourceRole.DestinationSnapshot,
            usages = setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.TextureBinding),
            lifetime = GPUFrameResourceLifetime.FrameLocal,
            byteSize = 256,
            diagnosticLabel = "scratch.destination",
        )
        val task = GPUTask.PrepareResources(
            taskId = GPUTaskID("task.prepare"),
            recordingId = GPURecordingID("recording.a"),
            phase = GPUTaskPhase.Prepare,
            requests = listOf(request),
        )

        val plan = GPUFramePlanner.plan(taskList(tasks = listOf(task)))

        val prepared = assertIs<GPUFrameStep.PrepareResourcesStep>(plan.steps.single()).requests.single()
        assertEquals(descriptor, prepared.descriptor)
        assertFailsWith<IllegalArgumentException> {
            GPUFrameTextureDescriptor(GPUPixelBounds(0, 0, 0, 1), GPUColorFormat("rgba8unorm"), 1)
        }
        assertFailsWith<IllegalArgumentException> {
            descriptor.copy(sampleCount = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUResourcePreparationRequest(
                resource = GPUFrameTextureRef("texture.invalid"),
                descriptor = GPUFrameBufferDescriptor(byteSize = 16, alignmentBytes = 4),
                role = GPUFrameResourceRole.DestinationSnapshot,
                usages = setOf(GPUFrameResourceUsage.TextureBinding),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = 16,
                diagnosticLabel = "invalid.texture",
            )
        }
    }

    private fun renderTask(
        taskId: String,
        recordingId: String,
        commandId: Int,
        target: String = "target.scene",
        compositeMembership: GPUTaskCompositeMembership? = null,
        blendPlan: GPUBlendPlan = executableBlend(),
    ): GPUTask.Render {
        val packet = packet(commandId, blendPlan)
        return GPUTask.Render(
            taskId = GPUTaskID(taskId),
            recordingId = GPURecordingID(recordingId),
            phase = GPUTaskPhase.Render,
            target = GPUFrameTargetRef(target),
            loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
            samplePlan = GPUSamplePlan.SingleSampleFrame,
            drawPackets = listOf(packet),
            batchEligibilityByPacketId = mapOf(
                packet.packetId to GPUPassBatchEligibility(
                    kind = GPUPassBatchKind.SolidFill,
                    queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                ),
            ),
            compositeMembership = compositeMembership,
        )
    }

    private fun packet(
        commandId: Int,
        blendPlan: GPUBlendPlan,
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
            renderPipelineKey = GPURenderPipelineKey("pipeline.rect"),
            bindingLayoutHash = "layout.rect",
            vertexSourceLabel = "vertices.rect",
            targetStateHash = "target.scene.rgba8",
            originalPaintOrder = commandId,
            resourceGeneration = 3,
        )

    private fun executableBlend(): GPUBlendPlan =
        GPUBlendPlan.ShaderBlendNoDstRead(
            mode = GPUBlendMode.SRC,
            formulaId = "source",
            sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
        )

    private fun seal(
        recordingId: String,
        insertionOrder: Long,
        capabilitySealHash: String = frameCapabilitySeal().sealHash,
    ): GPURecordingSeal =
        GPURecordingSeal(
            recordingId = GPURecordingID(recordingId),
            insertionOrder = insertionOrder,
            compatibilityKeyHash = "compat.same",
            replayKeyHash = "replay.same",
            capabilitySealHash = capabilitySealHash,
        )

    private fun taskList(
        tasks: List<GPUTask>,
        dependencies: List<GPUTaskDependency> = emptyList(),
        capabilitySeal: GPUFrameCapabilitySeal = frameCapabilitySeal(),
        seals: List<GPURecordingSeal> = listOf(seal("recording.a", 0, capabilitySeal.sealHash)),
    ): GPUTaskList = GPUTaskList(
        frameId = FRAME_ID,
        capabilitySeal = capabilitySeal,
        recordingSeals = seals,
        expectedReplayKeyHash = "replay.same",
        tasks = tasks,
        dependencies = dependencies,
        phaseOrder = GPUTaskPhase.entries,
        memoryBudget = emptyBudget(),
    )

    private fun dependency(from: GPUTask, to: GPUTask): GPUTaskDependency =
        GPUTaskDependency(
            fromTaskId = from.taskId,
            toTaskId = to.taskId,
            dependencyKind = "test-order",
            useToken = GPUTaskUseToken("${from.taskId.value}->${to.taskId.value}"),
            reasonCode = "test.order",
        )

    private fun refusedTask(
        taskId: String,
        commandId: Int,
        scope: GPURefusalScope,
        provenance: List<GPUCompositeProvenanceToken> = emptyList(),
        consumedChildren: List<GPUTaskID> = emptyList(),
        compositeScopeId: GPUCompositeScopeID? = null,
    ): GPUTask.Refused = GPUTask.Refused(
        taskId = GPUTaskID(taskId),
        recordingId = GPURecordingID("recording.a"),
        phase = GPUTaskPhase.Refusal,
        commandId = GPUDrawCommandID(commandId),
        scope = scope,
        compositeScopeId = compositeScopeId,
        provenanceTokens = provenance,
        consumedChildTaskIds = consumedChildren,
        diagnostic = diagnostic("unsupported.test.$commandId"),
    )

    private fun destinationTask(
        materialization: org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotMaterialization,
    ): GPUTask.DestinationSnapshots {
        val bounds = materialization.logicalBounds
        val sourceIntermediate = (materialization as? CopyAsDrawMaterialization)?.sourceIntermediate
        val grouping = GPUDestinationSnapshotGroupingResult(
            groups = listOf(
                GPUDestinationSnapshotGroup(
                    key = destinationGroupKey(sourceIntermediate),
                    logicalBounds = bounds,
                    members = listOf(GPUDestinationReadMember("draw.7", 0, bounds)),
                    copiedBytes = 64,
                    decisionDump = listOf("fixture"),
                ),
            ),
            materializations = listOf(materialization),
            totalCopiedBytes = 64,
            refusals = emptyList(),
            decisionDump = listOf("fixture"),
        )
        return GPUTask.DestinationSnapshots(
            taskId = GPUTaskID("task.destination"),
            recordingId = GPURecordingID("recording.a"),
            phase = GPUTaskPhase.Copy,
            payload = GPUDestinationSnapshotTaskPayload(
                grouping = grouping,
                operations = listOf(
                    when (materialization) {
                        is GPUDestinationSnapshotMaterialization.TextureCopy ->
                            GPUDestinationSnapshotOperation.TextureCopy(
                                groupIndex = 0,
                                source = GPUFrameTargetRef("target.scene"),
                                snapshot = GPUFrameTextureRef("texture.snapshot"),
                                logicalBounds = bounds,
                                copyLayout = GPUTextureCopyLayout(256, 4),
                                consumers = listOf(destinationConsumer()),
                            )
                        is CopyAsDrawMaterialization ->
                            GPUDestinationSnapshotOperation.CopyAsDraw(
                                groupIndex = 0,
                                source = GPUFrameTextureRef("texture.intermediate.source"),
                                sourceIntermediate = materialization.sourceIntermediate,
                                snapshot = GPUFrameTextureRef("texture.snapshot"),
                                logicalBounds = bounds,
                                consumers = listOf(destinationConsumer()),
                            )
                    },
                ),
            ),
        )
    }

    private fun destinationConsumer(): GPUDestinationSnapshotConsumerRef =
        GPUDestinationSnapshotConsumerRef(
            groupingCommandId = "draw.7",
            renderTaskId = GPUTaskID("task.render.7"),
            packetId = GPUDrawPacketID("packet.7"),
            commandId = GPUDrawCommandID(7),
        )

    private fun destinationGroupKey(
        sourceIntermediate: GPUIntermediateIdentity?,
    ): GPUDestinationSnapshotGroupKey =
        GPUDestinationSnapshotGroupKey(
            target = GPUTargetIdentity("target.scene"),
            targetGeneration = 2,
            deviceGeneration = GPUDeviceGenerationID(3),
            format = GPUColorFormat("rgba8unorm"),
            colorInterpretation = GPUColorInterpretation("srgb-premul"),
            sampleContinuation = GPUSampleContinuationKey(
                target = GPUTargetIdentity("target.scene"),
                targetGeneration = 2,
                deviceGeneration = GPUDeviceGenerationID(3),
                colorFormat = GPUColorFormat("rgba8unorm"),
                colorInterpretation = GPUColorInterpretation("srgb-premul"),
                samplePlan = GPUSamplePlan.MultisampleFrame(4),
                colorAttachment = GPUTargetIdentity("target.scene.msaa"),
                depthStencilAttachment = null,
            ),
            sourceIntermediate = sourceIntermediate,
        )

    private fun frameCapabilitySeal(
        copyAsDraw: GPUCopyAsDrawImplementationCapability? = null,
    ): GPUFrameCapabilitySeal =
        GPUFrameCapabilitySeal.capture(
            frameId = FRAME_ID,
            deviceGeneration = GPUDeviceGenerationID(3),
            capabilities = GPUCapabilities(
                implementation = GPUImplementationIdentity(
                    facadeName = "wgpu4k",
                    implementationName = "kanvas-test",
                    adapterName = "fixture-adapter",
                    deviceName = "fixture-device",
                ),
                facts = emptyList(),
                snapshotId = "capabilities.frame-planner.fixture",
                copyAsDrawCapability = copyAsDraw,
            ),
        )

    private fun diagnostic(code: String, terminal: Boolean = true): GPUDiagnostic =
        GPUDiagnostic(
            code = GPUDiagnosticCode(code),
            domain = GPUDiagnosticDomain.Recording,
            severity = if (terminal) GPUDiagnosticSeverity.Error else GPUDiagnosticSeverity.Info,
            message = code,
            facts = mapOf("fixture" to "true"),
            isTerminal = terminal,
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
        val FRAME_ID = GPUFrameID(11)
    }
}
