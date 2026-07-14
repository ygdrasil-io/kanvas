package org.graphiks.kanvas.gpu.renderer.recording

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPULayerFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendDiagnostic
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPURefusalScope
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUComputePipelineKey
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUUploadLayout
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

class GPUFramePlannerExecutionSemanticsTest {
    @Test
    fun `NoOp blend requires a stable non blank audit reason`() {
        assertFailsWith<IllegalArgumentException> {
            GPUBlendPlan.NoOp(GPUBlendMode.DST, "")
        }
    }

    @Test
    fun `real recorder compatible draws finalize into one render pass`() {
        val recorder = GPURecorder(
            recordingId = GPURecordingID("recording.real-batch"),
            frameId = GPUFrameID(41),
            capabilities = firstRouteCapabilities(),
        )
        recorder.record(fillRect(commandId = 1, paintOrder = 0))
        recorder.record(fillRect(commandId = 2, paintOrder = 1))

        val plan = GPUFramePlanner.plan(recorder.close().taskList)

        val render = assertIs<GPUFrameStep.RenderPassStep>(plan.steps.single())
        assertEquals(listOf(1, 2), render.drawPackets.map(GPUDrawPacket::commandIdValue))
    }

    @Test
    fun `fixed state batch cut keeps one render pass and applies clear once`() {
        val first = renderTask(
            taskId = "task.render.1",
            commandId = 1,
            loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store, "transparent"),
            pipeline = "pipeline.a",
        )
        val second = renderTask(
            taskId = "task.render.2",
            commandId = 2,
            loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store, "transparent"),
            pipeline = "pipeline.b",
        )

        val plan = GPUFramePlanner.plan(taskList(listOf(first, second)))

        val render = assertIs<GPUFrameStep.RenderPassStep>(plan.steps.single())
        assertEquals("clear", render.loadStore.loadOp)
        assertEquals(listOf(1, 2), render.drawPackets.map(GPUDrawPacket::commandIdValue))
    }

    @Test
    fun `NoOp blend packet never becomes encoder work`() {
        val noOp = renderTask(
            taskId = "task.render.no-op",
            commandId = 3,
            blendPlan = GPUBlendPlan.NoOp(GPUBlendMode.DST, "destination unchanged"),
        )

        val plan = GPUFramePlanner.plan(taskList(listOf(noOp)))

        assertTrue(plan.steps.none { it is GPUFrameStep.RenderPassStep })
        assertTrue(plan.steps.none { it.executionKind == GPUFrameStepExecutionKind.Encoder })
        assertEquals(
            GPUFrameElidedNoOpDraw(
                taskId = GPUTaskID("task.render.no-op"),
                packetId = GPUDrawPacketID("packet.3"),
                commandId = GPUDrawCommandID(3),
                mode = GPUBlendMode.DST,
                reason = "destination unchanged",
            ),
            plan.elidedNoOpDraws.single(),
        )
        assertTrue(plan.dumpLines().any { it.contains("elided-noop") && it.contains("packet.3") })

        val changedReasonPlan = GPUFramePlanner.plan(
            taskList(
                listOf(
                    renderTask(
                        taskId = "task.render.no-op",
                        commandId = 3,
                        blendPlan = GPUBlendPlan.NoOp(GPUBlendMode.DST, "different audit reason"),
                    ),
                ),
            ),
        )
        assertNotEquals(plan.stableHash(), changedReasonPlan.stableHash())
    }

    @Test
    fun `target state changes between render tasks cut the render pass`() {
        val first = renderTask(
            taskId = "task.render.target-state.1",
            commandId = 30,
            targetStateHash = "target.scene.rgba8",
        )
        val second = renderTask(
            taskId = "task.render.target-state.2",
            commandId = 31,
            targetStateHash = "target.scene.rgba16",
        )

        val plan = GPUFramePlanner.plan(taskList(listOf(first, second)))

        assertFalse(plan.atomicallyRefused)
        assertEquals(2, plan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().size)
    }

    @Test
    fun `unsupported leaf blend becomes a refusal and cannot encode`() {
        val unsupported = renderTask(
            taskId = "task.render.unsupported",
            commandId = 4,
            blendPlan = unsupportedBlend(GPURefusalScope.RefusedLeafDrawStep),
        )

        val plan = GPUFramePlanner.plan(taskList(listOf(unsupported)))

        val refusal = assertIs<GPUFrameStep.RefusedLeafDrawStep>(plan.steps.single())
        assertEquals(4, refusal.commandId.value)
        assertEquals("unsupported.blend.fixture", refusal.diagnostic.code.value)
        assertEquals(GPUDiagnosticDomain.Passes, refusal.diagnostic.domain)
    }

    @Test
    fun `unsupported atomic blend refuses the whole frame`() {
        val unsupported = renderTask(
            taskId = "task.render.atomic",
            commandId = 5,
            blendPlan = unsupportedBlend(GPURefusalScope.AtomicFrameFailure),
        )

        val plan = GPUFramePlanner.plan(taskList(listOf(unsupported)))

        assertTrue(plan.atomicallyRefused)
        assertTrue(plan.steps.isEmpty())
        assertEquals("unsupported.blend.fixture", plan.diagnostics.last().code.value)
    }

    @Test
    fun `destination texture blend without exact Task5 association refuses atomically`() {
        val missingSnapshot = renderTask(
            taskId = "task.render.destination",
            commandId = 6,
            blendPlan = GPUBlendPlan.ShaderBlendWithDstRead(
                mode = GPUBlendMode.MULTIPLY,
                formulaId = "multiply@v1",
                sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
            ),
        )

        val plan = GPUFramePlanner.plan(taskList(listOf(missingSnapshot)))

        assertTrue(plan.atomicallyRefused)
        assertTrue(plan.steps.isEmpty())
        assertEquals("invalid.frame_plan.destination_read_unbound", plan.diagnostics.last().code.value)
    }

    @Test
    fun `compute upload and dependency barrier lower directly in task order`() {
        val upload = GPUTask.Upload(
            taskId = GPUTaskID("task.upload"),
            recordingId = GPURecordingID("recording.a"),
            phase = GPUTaskPhase.Upload,
            staging = GPUFrameBufferRef("buffer.staging"),
            destination = GPUFrameTextureRef("texture.uploaded"),
            layout = GPUUploadLayout(0, 256, 4, 1024),
        )
        val compute = GPUTask.Compute(
            taskId = GPUTaskID("task.compute"),
            recordingId = GPURecordingID("recording.a"),
            phase = GPUTaskPhase.Compute,
            target = GPUFrameTargetRef("target.scene"),
            resourceUses = listOf(
                GPUFrameResourceUse(
                    resource = GPUFrameTextureRef("texture.uploaded"),
                    role = GPUFrameResourceRole.StorageData,
                    usage = GPUFrameResourceUsage.StorageBinding,
                    lifetime = GPUFrameResourceLifetime.FrameLocal,
                    write = true,
                ),
            ),
            dispatches = listOf(GPUComputeDispatch(GPUComputePipelineKey("compute.fixture"), 2, 3, 1)),
        )
        val barrier = GPUTask.Barrier(
            taskId = GPUTaskID("task.barrier"),
            recordingId = GPURecordingID("recording.a"),
            phase = GPUTaskPhase.Transition,
            orderedUseTokens = listOf(GPUTaskUseToken("upload->compute")),
            reasonCode = "fixture.dependency",
        )
        val dependencies = listOf(
            GPUTaskDependency(upload.taskId, compute.taskId, "resource", null, "upload-before-compute"),
            GPUTaskDependency(compute.taskId, barrier.taskId, "resource", null, "compute-before-barrier"),
        )

        val plan = GPUFramePlanner.plan(taskList(listOf(barrier, compute, upload), dependencies))

        assertEquals(
            listOf(
                GPUFrameStep.UploadResourceStep::class,
                GPUFrameStep.ComputePassStep::class,
                GPUFrameStep.DependencyBarrierStep::class,
            ),
            plan.steps.map { it::class },
        )
        assertEquals(GPUFrameStepExecutionKind.DependencyOnly, plan.steps.last().executionKind)
    }

    private fun renderTask(
        taskId: String,
        commandId: Int,
        loadStore: GPULoadStorePlan = GPULoadStorePlan("load", GPUStorePlan.Store),
        pipeline: String = "pipeline.rect",
        blendPlan: GPUBlendPlan = executableBlend(),
        targetStateHash: String = "target.scene.rgba8",
    ): GPUTask.Render {
        val packet = packet(commandId, pipeline, blendPlan, targetStateHash)
        return GPUTask.Render(
            taskId = GPUTaskID(taskId),
            recordingId = GPURecordingID("recording.a"),
            phase = GPUTaskPhase.Render,
            target = GPUFrameTargetRef("target.scene"),
            loadStore = loadStore,
            samplePlan = GPUSamplePlan.SingleSampleFrame,
            drawPackets = listOf(packet),
            batchEligibilityByPacketId = mapOf(
                packet.packetId to GPUPassBatchEligibility(
                    kind = GPUPassBatchKind.SolidFill,
                    queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                ),
            ),
        )
    }

    private fun packet(
        commandId: Int,
        pipeline: String,
        blendPlan: GPUBlendPlan,
        targetStateHash: String = "target.scene.rgba8",
    ): GPUDrawPacket =
        GPUDrawPacket(
            packetId = GPUDrawPacketID("packet.$commandId"),
            commandIdValue = commandId,
            analysisRecordId = "analysis.$commandId",
            passId = "pass.command.$commandId",
            layerId = "root",
            bindingListId = "bindings.$commandId",
            insertionReasonCode = "paint-order",
            sortKey = commandId.toLong(),
            sortKeyPreimage = "paint-order:$commandId",
            renderStepId = GPURenderStepID("rect.fill"),
            renderStepVersion = 1,
            role = GPUDrawPacketRole.Shading,
            blendPlan = blendPlan,
            renderPipelineKey = GPURenderPipelineKey(pipeline),
            bindingLayoutHash = "layout.rect",
            vertexSourceLabel = "vertices.rect",
            targetStateHash = targetStateHash,
            originalPaintOrder = commandId,
            resourceGeneration = 3,
        )

    private fun executableBlend(): GPUBlendPlan =
        GPUBlendPlan.ShaderBlendNoDstRead(
            mode = GPUBlendMode.SRC_OVER,
            formulaId = "src-over@v1",
            sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
        )

    private fun unsupportedBlend(scope: GPURefusalScope): GPUBlendPlan =
        GPUBlendPlan.UnsupportedBlend(
            mode = GPUBlendMode.MULTIPLY,
            diagnostic = GPUBlendDiagnostic(
                code = "unsupported.blend.fixture",
                mode = GPUBlendMode.MULTIPLY,
                message = "fixture unsupported blend",
            ),
            refusalScope = scope,
        )

    private fun taskList(
        tasks: List<GPUTask>,
        dependencies: List<GPUTaskDependency> = emptyList(),
    ): GPUTaskList {
        val capabilitySeal = GPUFrameCapabilitySeal.capture(
            frameId = GPUFrameID(41),
            deviceGeneration = GPUDeviceGenerationID(3),
            capabilities = firstRouteCapabilities(),
        )
        return GPUTaskList(
        frameId = GPUFrameID(41),
        capabilitySeal = capabilitySeal,
        recordingSeals = listOf(
            GPURecordingSeal(
                recordingId = GPURecordingID("recording.a"),
                insertionOrder = 0,
                compatibilityKeyHash = "compat.same",
                replayKeyHash = "replay.same",
                capabilitySealHash = capabilitySeal.sealHash,
            ),
        ),
        expectedReplayKeyHash = "replay.same",
        tasks = tasks,
        dependencies = dependencies,
        phaseOrder = GPUTaskPhase.entries,
        memoryBudget = GPUFrameMemoryBudgetPlan(
            peakFrameTransientBytes = 0,
            targetResidentBytes = 0,
            categoryTotals = GPUFrameMemoryCategory.entries.associateWith { 0L },
            deviceLimitFacts = emptyList(),
            configuredAggregateBudgetBytes = 1,
            diagnostic = null,
        ),
        )
    }

    private fun fillRect(commandId: Int, paintOrder: Int) =
        GPUFillRectCommandBuilder.build(
            commandId = GPUDrawCommandID(commandId),
            rect = GPURect(2f, 3f, 18f, 21f),
            target = firstRouteTarget,
            material = GPUMaterialDescriptor.SolidColor(1f, 0.25f, 0.5f, 1f),
            transform = GPUTransformFacts.identity(),
            layer = GPULayerFacts.root(firstRouteTarget),
            paintOrder = paintOrder,
            source = GPUCommandSource("unit-test", "fillRect"),
        )

    private fun firstRouteCapabilities(): GPUCapabilities =
        GPUCapabilities(
            implementation = GPUImplementationIdentity(
                facadeName = "test-gpu",
                implementationName = "unit",
                adapterName = "fixture-adapter",
                deviceName = "fixture-device",
            ),
            facts = listOf(
                GPUCapabilityFact(
                    name = "first_slice.fill_rect.native",
                    source = "unit-test",
                    value = "supported",
                    affectsValidity = true,
                    evidenceLabel = "first-route-fixture",
                ),
            ),
            snapshotId = "first-route-test",
        )

    private companion object {
        val firstRouteTarget = GPUTargetFacts(width = 128, height = 64, colorFormat = "rgba8unorm")
    }
}
