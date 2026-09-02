package org.graphiks.kanvas.gpu.renderer.planning

import io.ygdrasil.webgpu.GPUTextureFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.plan.AttachmentLoadPlan
import org.graphiks.kanvas.gpu.plan.AttachmentStorePlan
import org.graphiks.kanvas.gpu.plan.BlendPlan
import org.graphiks.kanvas.gpu.plan.CoveragePlan
import org.graphiks.kanvas.gpu.plan.PlanBudget
import org.graphiks.kanvas.gpu.plan.PlanCapabilitySnapshot
import org.graphiks.kanvas.gpu.plan.PlanId
import org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat
import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.gpu.plan.PlanPassDependency
import org.graphiks.kanvas.gpu.plan.PlanResource
import org.graphiks.kanvas.gpu.plan.PlanResourceKind
import org.graphiks.kanvas.gpu.plan.PlanResourceLifetime
import org.graphiks.kanvas.gpu.plan.PlanResourceRole
import org.graphiks.kanvas.gpu.plan.PlanResourceUsage
import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.plan.SamplePlan
import org.graphiks.kanvas.gpu.plan.SolidRectDraw
import org.graphiks.kanvas.gpu.plan.W3SolidRectPlanCompiler
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.CoverageRequest
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneExtent
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.analysis.corePrimitiveRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameTaskListAssembler
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreplannedFrameRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryResourceKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommand
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveMaterialPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRectRouteAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.math.color.ColorF32
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.matrix.Matrix3x3F32

class GpuPlanTaskListLowererTest {
    private val lowerer = GpuPlanTaskListLowerer()

    @Test
    fun `public graph keeps original scene command index while packet ordering stays compact`() {
        val scene = SceneSnapshot.of(
            SceneExtent(2, 2),
            ColorSpace.SRGB,
            listOf(
                SceneCommand.Annotation.of(RectF32(0f, 0f, 2f, 2f), "origin", "test"),
                SceneCommand.SetTransform(Matrix3x3F32.Identity),
                SceneCommand.SetClip(ClipStackNode.Empty),
                SceneCommand.Draw(
                    DrawNode(
                        GeometryNode.Rect.of(RectF32(0f, 0f, 2f, 2f)),
                        MaterialNode.Solid(ColorARGB.fromPackedUInt(0xFF4080C0u)),
                        CoverageRequest.HARD_EDGE,
                        ClipStackNode.Empty,
                        BlendNode.SrcOver,
                        EffectStack.Empty,
                        Matrix3x3F32.Identity,
                        DrawOrigin.RECT,
                    ),
                ),
            ),
        )
        val graph = assertIs<RenderPlanResult.Ready<RenderGraph>>(
            W3SolidRectPlanCompiler().plan(
                scene,
                RenderTargetDescriptor(scene.extent, ColorSpace.SRGB),
                PlanCapabilitySnapshot.of(7, 2048, 1L shl 20, 256, setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL)),
                PlanBudget(1024),
            ),
        ).plan

        val packet = assertIs<GpuPlanLoweringResult.Lowered>(lowerer.lower(validRequest(graph))).taskList
            .tasks.filterIsInstance<GPUTask.Render>().single().drawPackets.single()

        assertEquals(3, packet.commandIdValue)
        assertEquals(0, packet.originalPaintOrder)
        assertEquals(0L, packet.sortKey)
    }

    @Test
    fun `public graph whose I32 bounds cannot round trip through F32 is refused`() {
        val width = 16_777_217
        val graph = graph(
            extent = SizeI32(width, 1),
            targetByteSize = width.toLong() * 4L,
            readbackBytesPerRow = 67_109_120L,
            budget = PlanBudget(256L shl 20),
            capabilities = PlanCapabilitySnapshot.of(7, width, 256L shl 20, 256, setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL)),
            drawBounds = RectI32(0, 0, width, 1),
        )

        val refusal = assertIs<GpuPlanLoweringResult.InvalidPlan>(
            lowerer.lower(
                validRequest(
                    graph,
                    currentBudget = graph.budget,
                    rendererCapabilities = capabilities(width.toLong(), 256L shl 20),
                ),
            ),
        )

        assertEquals("w3.lowering.incompatible_plan", refusal.diagnostic.code.value)
    }

    @Test
    fun `public graph at Int MAX I32 boundary is refused without saturating`() {
        val width = Int.MAX_VALUE
        val bytesPerRow = 8_589_934_592L
        val graph = graph(
            extent = SizeI32(width, 1),
            targetByteSize = width.toLong() * 4L,
            readbackBytesPerRow = bytesPerRow,
            budget = PlanBudget(32L shl 30),
            capabilities = PlanCapabilitySnapshot.of(7, width, 32L shl 30, 256, setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL)),
            drawBounds = RectI32(0, 0, width, 1),
        )

        val refusal = assertIs<GpuPlanLoweringResult.InvalidPlan>(
            lowerer.lower(
                validRequest(
                    graph = graph,
                    currentBudget = graph.budget,
                    rendererCapabilities = capabilities(width.toLong(), 32L shl 30),
                ),
            ),
        )

        assertEquals("w3.lowering.incompatible_plan", refusal.diagnostic.code.value)
    }

    @Test
    fun `coherently re-signed blend semantic payload is refused by the W3 lowering envelope`() {
        val graph = graph()
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(lowerer.lower(validRequest(graph))).taskList
        val prepare = assertIs<GPUTask.PrepareResources>(lowered.tasks[0])
        val render = assertIs<GPUTask.Render>(lowered.tasks[1])
        val readback = assertIs<GPUTask.Readback>(lowered.tasks[2])
        val original = render.drawPackets.single()
        val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(original.semanticPayload)
        val geometry = assertIs<GPUCorePrimitiveGeometry.Rect>(semantic.geometry)
        val reSigned = GPUCorePrimitivePayloadGatherer().gatherSemantic(
            GPUCorePrimitivePayloadInput(
                original.commandIdValue,
                GPUCorePrimitiveSourceFamily.Rect,
                GPUCorePrimitiveGeometryInput.Rect(geometry.left, geometry.top, geometry.right, geometry.bottom),
                semantic.premultipliedRgba,
                semantic.targetBounds,
                semantic.scissorBounds,
                semantic.clipCoveragePlan,
                semantic.clipExecutionPlanIdentity,
                "blend.re-signed",
                GPUFrameProvenance.None,
                GPUCorePrimitiveCoverageMode.FullOrScissor,
                semantic.analysisRecordId!!,
                semantic.analysisCommandFamily!!,
                GPUCorePrimitiveRectRouteAuthority.RectAxisAligned,
                corePrimitiveRectGeometryAuthority(GPURect(geometry.left, geometry.top, geometry.right, geometry.bottom), GPUTransformFacts.identity()),
            ),
        )
        val altered = GPUTask.Render(
            render.taskId, render.recordingId, render.phase, render.target, render.loadStore, render.samplePlan,
            render.resourceUses, render.provisionalSegmentKey, listOf(copyPacket(original, original.targetStateHash, semanticPayload = reSigned)),
            render.batchEligibilityByPacketId, render.sampleContinuationKey, render.compositeMembership,
            render.depthStencilLoadStore, render.preparedImageBindingsByPacketId, render.preparedTextBindingsByPacketId,
        )
        val base = GPUTaskList(
            lowered.frameId, lowered.capabilitySeal, lowered.recordingSeals, lowered.expectedReplayKeyHash,
            listOf(altered), emptyList(), lowered.phaseOrder, lowered.memoryBudget, lowered.diagnostics,
        )

        val result = GPUCorePrimitivePreparedFrameTaskListAssembler().buildPreplanned(
            GPUCorePrimitivePreplannedFrameRequest(
                graph.id, base, readback.source,
                assertIs<GPUFrameTextureDescriptor>(prepare.requests[0].descriptor).logicalBounds,
                prepare.requests[0], readback.staging, prepare.requests[1], readback.request,
                lowered.memoryBudget, graph.passes()[0].id, graph.passes()[1].id,
            ),
        )

        assertEquals("w3.lowering.incompatible_plan", assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value)
    }

    @Test
    fun `ready W3 graph lowers to a frame accepted by GPUFramePlanner`() {
        val graph = graph()
        assertEquals(0, assertIs<PlanPass.ReadbackPass>(graph.passes().last()).ordinal)
        val lowered = lowerer.lower(validRequest(graph = graph))

        val taskList = assertIs<GpuPlanLoweringResult.Lowered>(lowered, lowered.toString()).taskList
        assertIs<GPUTask.PrepareResources>(taskList.tasks[0])
        assertIs<GPUTask.Render>(taskList.tasks[1])
        assertIs<GPUTask.Readback>(taskList.tasks[2])
        val framePlan = GPUFramePlanner.plan(taskList)
        assertFalse(framePlan.atomicallyRefused)
        assertFalse(framePlan.steps.isEmpty())
    }

    @Test
    fun `lowered W3 frame preserves graph resource layout memory and pass dependencies`() {
        val graph = graph()
        val taskList = assertIs<GpuPlanLoweringResult.Lowered>(
            lowerer.lower(validRequest(graph = graph)),
        ).taskList
        val prepare = assertIs<GPUTask.PrepareResources>(taskList.tasks[0])
        val render = assertIs<GPUTask.Render>(taskList.tasks[1])
        val readback = assertIs<GPUTask.Readback>(taskList.tasks[2])
        val target = assertIs<GPUFrameTextureDescriptor>(prepare.requests[0].descriptor)
        val staging = assertIs<GPUFrameBufferDescriptor>(prepare.requests[1].descriptor)

        assertEquals(graph.targetExtent.width, target.logicalBounds.width)
        assertEquals(graph.targetExtent.height, target.logicalBounds.height)
        assertEquals(GPUColorFormat.RGBA8UnormSrgb, target.format)
        assertEquals(1, target.sampleCount)
        assertEquals(512L, staging.byteSize)
        assertEquals(256L, staging.alignmentBytes)
        assertEquals(GPUFrameResourceRole.SceneTarget, prepare.requests[0].role)
        assertEquals(GPUFrameResourceRole.ReadbackStaging, prepare.requests[1].role)
        assertEquals(
            setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource),
            prepare.requests[0].usages,
        )
        assertEquals(
            setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.MapRead),
            prepare.requests[1].usages,
        )
        assertEquals(GPUFrameResourceLifetime.FrameLocal, prepare.requests[0].lifetime)
        assertEquals(GPUFrameResourceLifetime.FrameLocal, prepare.requests[1].lifetime)
        assertEquals(16L, prepare.requests[0].byteSize)
        assertEquals(512L, prepare.requests[1].byteSize)
        assertEquals("w3.session.7.2x2.rgba8unorm-srgb.target", prepare.requests[0].diagnosticLabel)
        assertEquals("w3.session.7.2x2.rgba8unorm-srgb.staging", prepare.requests[1].diagnosticLabel)
        assertEquals(512L, taskList.memoryBudget.peakFrameTransientBytes)
        assertEquals(16L, taskList.memoryBudget.targetResidentBytes)
        assertEquals(1024L, taskList.memoryBudget.configuredAggregateBudgetBytes)
        assertEquals(null, taskList.memoryBudget.diagnostic)
        assertEquals(16L, taskList.memoryBudget.categoryTotals[GPUFrameMemoryCategory.CanonicalTarget])
        assertEquals(512L, taskList.memoryBudget.categoryTotals[GPUFrameMemoryCategory.ReadbackStaging])
        assertEquals(2, taskList.memoryBudget.allocations.size)
        assertEquals(GPUFrameMemoryResourceKind.Texture2D, taskList.memoryBudget.allocations[0].resourceKind)
        assertEquals(GPUFrameMemoryResourceKind.Buffer, taskList.memoryBudget.allocations[1].resourceKind)
        assertEquals(16L, taskList.memoryBudget.allocations[0].bytes)
        assertEquals(512L, taskList.memoryBudget.allocations[1].bytes)
        assertEquals("w3.session.7.2x2.rgba8unorm-srgb.target", taskList.memoryBudget.allocations[0].label)
        assertEquals("w3.session.7.2x2.rgba8unorm-srgb.staging", taskList.memoryBudget.allocations[1].label)
        assertEquals(GPUFrameMemoryCategory.CanonicalTarget, taskList.memoryBudget.allocations[0].category)
        assertEquals(GPUFrameMemoryCategory.ReadbackStaging, taskList.memoryBudget.allocations[1].category)
        assertEquals(target.logicalBounds, taskList.memoryBudget.allocations[0].extent)
        assertEquals(null, taskList.memoryBudget.allocations[1].extent)
        assertEquals(readback.source, render.target)
        assertEquals(GPULoadStorePlan("clear", GPUStorePlan.Store), render.loadStore)
        assertEquals(GPUSamplePlan.SingleSampleFrame, render.samplePlan)
        assertTrue(render.resourceUses.isEmpty())
        assertEquals(null, render.sampleContinuationKey)
        assertEquals(null, render.compositeMembership)
        assertEquals(null, render.depthStencilLoadStore)
        val packet = render.drawPackets.single()
        assertEquals("packet.w3.0", packet.packetId.value)
        assertEquals("pass.w3.main", packet.passId)
        assertEquals("core-primitive-device-geometry", packet.vertexSourceLabel)
        assertEquals(1, packet.renderStepVersion)
        assertEquals(0, packet.commandIdValue)
        assertEquals(0, packet.originalPaintOrder)
        assertEquals(0L, packet.sortKey)
        assertEquals("scissor_0.0_0.0_2.0_2.0", packet.scissorBoundsHash)
        val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(packet.semanticPayload)
        val geometry = assertIs<GPUCorePrimitiveGeometry.Rect>(semantic.geometry)
        assertEquals(0f, geometry.left)
        assertEquals(0f, geometry.top)
        assertEquals(2f, geometry.right)
        assertEquals(2f, geometry.bottom)
        val material = assertIs<GPUCorePrimitiveMaterialPayload.SolidColor>(semantic.material)
        assertEquals(listOf(0.051269468f, 0.21586053f, 0.5271152f, 1f), semantic.premultipliedRgba)
        assertEquals(semantic.premultipliedRgba, material.premultipliedRgba)
        assertEquals(packet.blendPlan?.canonicalIdentity(), semantic.blendPlanIdentity)
        assertEquals(GPUCorePrimitiveCoverageMode.FullOrScissor, semantic.coverageMode)
        assertEquals(target.logicalBounds, semantic.scissorBounds)
        assertEquals(target.logicalBounds, semantic.targetBounds)
        assertEquals(readback.source, prepare.requests[0].resource)
        assertEquals(readback.staging, prepare.requests[1].resource)
        assertEquals(2, taskList.dependencies.size)
        assertEquals("resource-prepare", taskList.dependencies.first().dependencyKind)
        assertEquals("plan-pass-dependency", taskList.dependencies.last().dependencyKind)
        assertEquals("w3-plan-resource-availability", taskList.dependencies.first().reasonCode)
        assertEquals("w3-plan-render-before-readback", taskList.dependencies.last().reasonCode)
        assertEquals(render.taskId, taskList.dependencies.first().toTaskId)
        assertEquals(render.taskId, taskList.dependencies.last().fromTaskId)
        assertEquals(readback.taskId, taskList.dependencies.last().toTaskId)
        assertEquals("MainRender:0", taskList.dependencies.last().fromTaskId.value.substringAfter(".render."))
        assertEquals("Readback:0", readback.taskId.value.substringAfter(".readback."))
        assertEquals(0L, readback.request.bufferOffsetBytes)
        assertEquals("w3.${graph.id.value}.readback", readback.request.requestId.value)
        assertEquals("Rgba8Unorm", readback.request.pixelFormat.name)
        assertEquals(target.logicalBounds, readback.request.sourceBounds)
        assertEquals(GPUColorInterpretation.LinearPremul, readback.request.outputColorInterpretation)
    }

    @Test
    fun `altered W3 packet envelope is refused before prepared tasks are emitted`() {
        val graph = graph()
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(
            lowerer.lower(validRequest(graph = graph)),
        ).taskList
        val prepare = assertIs<GPUTask.PrepareResources>(lowered.tasks[0])
        val render = assertIs<GPUTask.Render>(lowered.tasks[1])
        val readback = assertIs<GPUTask.Readback>(lowered.tasks[2])
        val original = render.drawPackets.single()
        val originalSemantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(original.semanticPayload)
        val forgedRect = GPURect(0f, 0f, 1f, 1f)
        val forgedSemantic = GPUCorePrimitivePayloadGatherer().gatherSemantic(
            GPUCorePrimitivePayloadInput(
                original.commandIdValue,
                GPUCorePrimitiveSourceFamily.Rect,
                GPUCorePrimitiveGeometryInput.Rect(
                    forgedRect.left,
                    forgedRect.top,
                    forgedRect.right,
                    forgedRect.bottom,
                ),
                originalSemantic.premultipliedRgba,
                originalSemantic.targetBounds,
                originalSemantic.scissorBounds,
                originalSemantic.clipCoveragePlan,
                originalSemantic.clipExecutionPlanIdentity,
                originalSemantic.blendPlanIdentity,
                GPUFrameProvenance.None,
                GPUCorePrimitiveCoverageMode.FullOrScissor,
                "analysis.fill_rect.${original.commandIdValue}",
                "FillRect",
                GPUCorePrimitiveRectRouteAuthority.RectAxisAligned,
                corePrimitiveRectGeometryAuthority(forgedRect, GPUTransformFacts.identity()),
            ),
        )
        val canonicalOrderRect = GPURect(0f, 0f, 2f, 2f)
        val canonicalOrderSemantic = GPUCorePrimitivePayloadGatherer().gatherSemantic(
            GPUCorePrimitivePayloadInput(
                1,
                GPUCorePrimitiveSourceFamily.Rect,
                GPUCorePrimitiveGeometryInput.Rect(
                    canonicalOrderRect.left,
                    canonicalOrderRect.top,
                    canonicalOrderRect.right,
                    canonicalOrderRect.bottom,
                ),
                originalSemantic.premultipliedRgba,
                originalSemantic.targetBounds,
                originalSemantic.scissorBounds,
                originalSemantic.clipCoveragePlan,
                originalSemantic.clipExecutionPlanIdentity,
                originalSemantic.blendPlanIdentity,
                GPUFrameProvenance.None,
                GPUCorePrimitiveCoverageMode.FullOrScissor,
                "analysis.fill_rect.1",
                "FillRect",
                GPUCorePrimitiveRectRouteAuthority.RectAxisAligned,
                corePrimitiveRectGeometryAuthority(canonicalOrderRect, GPUTransformFacts.identity()),
            ),
        )
        val alteredPackets = listOf(
            copyPacket(
                original,
                targetStateHash = original.targetStateHash,
                semanticPayload = forgedSemantic,
            ),
            copyPacket(
                original,
                targetStateHash = original.targetStateHash,
                packetId = GPUDrawPacketID("packet.w3.1"),
                commandIdValue = 1,
                analysisRecordId = "w3.1",
                bindingListId = "binding.w3.1",
                semanticPayload = canonicalOrderSemantic,
                sortKey = 1L,
                sortKeyPreimage = "paint-order:1",
                originalPaintOrder = 1,
            ),
        )
        alteredPackets.forEach { altered ->
            val alteredRender = GPUTask.Render(
            render.taskId,
            render.recordingId,
            render.phase,
            render.target,
            render.loadStore,
            render.samplePlan,
            render.resourceUses,
            render.provisionalSegmentKey,
            listOf(altered),
            mapOf(
                altered.packetId to render.batchEligibilityByPacketId.getValue(original.packetId),
            ),
            render.sampleContinuationKey,
            render.compositeMembership,
            render.depthStencilLoadStore,
            render.preparedImageBindingsByPacketId,
            render.preparedTextBindingsByPacketId,
        )
            val base = GPUTaskList(
            lowered.frameId,
            lowered.capabilitySeal,
            lowered.recordingSeals,
            lowered.expectedReplayKeyHash,
            listOf(alteredRender),
            emptyList(),
            lowered.phaseOrder,
            lowered.memoryBudget,
            lowered.diagnostics,
        )

            val result = GPUCorePrimitivePreparedFrameTaskListAssembler().buildPreplanned(
            GPUCorePrimitivePreplannedFrameRequest(
                graph.id,
                base,
                readback.source,
                assertIs<GPUFrameTextureDescriptor>(prepare.requests[0].descriptor).logicalBounds,
                prepare.requests[0],
                readback.staging,
                prepare.requests[1],
                readback.request,
                lowered.memoryBudget,
                graph.passes()[0].id,
                graph.passes()[1].id,
            ),
        )

            assertEquals(
                "w3.lowering.incompatible_plan",
                assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
            )
        }
    }

    @Test
    fun `W3 base list with composite commands is refused before prepared tasks are emitted`() {
        val graph = graph()
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(
            lowerer.lower(validRequest(graph = graph)),
        ).taskList
        val prepare = assertIs<GPUTask.PrepareResources>(lowered.tasks[0])
        val render = assertIs<GPUTask.Render>(lowered.tasks[1])
        val readback = assertIs<GPUTask.Readback>(lowered.tasks[2])
        val base = GPUTaskList(
            lowered.frameId,
            lowered.capabilitySeal,
            lowered.recordingSeals,
            lowered.expectedReplayKeyHash,
            listOf(render),
            emptyList(),
            lowered.phaseOrder,
            lowered.memoryBudget,
            lowered.diagnostics,
            listOf(GPUPassCommand.BeginRenderPass("forged-composite", "clear/store")),
        )

        val result = GPUCorePrimitivePreparedFrameTaskListAssembler().buildPreplanned(
            GPUCorePrimitivePreplannedFrameRequest(
                graph.id,
                base,
                readback.source,
                assertIs<GPUFrameTextureDescriptor>(prepare.requests[0].descriptor).logicalBounds,
                prepare.requests[0],
                readback.staging,
                prepare.requests[1],
                readback.request,
                lowered.memoryBudget,
                graph.passes()[0].id,
                graph.passes()[1].id,
            ),
        )

        assertEquals(
            "w3.lowering.incompatible_plan",
            assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `unsupported planned decisions are rejected instead of replanned`() {
        val result = lowerer.lower(validRequest(graph = graph(capabilityId = "other-capability")))

        assertEquals(
            "w3.lowering.incompatible_plan",
            assertIs<GpuPlanLoweringResult.InvalidPlan>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `stale graph generation is an unsupported capability`() {
        val result = lowerer.lower(validRequest(deviceGeneration = GPUDeviceGenerationID(8)))

        assertIs<GpuPlanLoweringResult.UnsupportedCapability>(result)
    }

    @Test
    fun `current budget divergence is an invalid plan`() {
        val result = lowerer.lower(validRequest(currentBudget = PlanBudget(2048)))

        assertEquals(
            "w3.lowering.incompatible_plan",
            assertIs<GpuPlanLoweringResult.InvalidPlan>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `W3 public graph with a wrong readback ordinal is refused`() {
        assertEquals(
            "w3.lowering.incompatible_plan",
            assertIs<GpuPlanLoweringResult.InvalidPlan>(
                lowerer.lower(validRequest(graph = graph(readbackOrdinal = 1))),
            ).diagnostic.code.value,
        )
    }

    @Test
    fun `W3 public graph divergence table is refused without replanning`() {
        val cases = listOf(
            "pass" to graph(readbackOrdinal = 1),
            "resource" to graph(
                targetUsages = setOf(
                    PlanResourceUsage.RenderAttachment,
                    PlanResourceUsage.CopySource,
                    PlanResourceUsage.MapRead,
                ),
            ),
            "resource-id" to graph(targetOrdinal = 1),
            "lifetime" to graph(stagingFirstPassIndex = 0),
            "cost" to graph(targetByteSize = 32L),
            "dependency" to graph(includeDependency = false),
            "layout" to graph(readbackBytesPerRow = 512L, budget = PlanBudget(2048)),
        )

        cases.forEach { (kind, divergentGraph) ->
            val result = lowerer.lower(
                validRequest(graph = divergentGraph, currentBudget = divergentGraph.budget),
            )
            val refusal = assertIs<GpuPlanLoweringResult.InvalidPlan>(result, kind)
            assertEquals("w3.lowering.incompatible_plan", refusal.diagnostic.code.value, kind)
        }
    }

    private fun validRequest(
        graph: RenderGraph = graph(),
        deviceGeneration: GPUDeviceGenerationID = GPUDeviceGenerationID(7),
        currentBudget: PlanBudget = PlanBudget(1024),
        rendererCapabilities: GPUCapabilities = capabilities(),
    ) = GpuPlanLoweringRequest(
        graph = graph,
        capabilities = rendererCapabilities,
        deviceGeneration = deviceGeneration,
        currentBudget = currentBudget,
        frameId = GPUFrameID(3),
        recordingId = GPURecordingID("w3-lowering"),
    )

    private fun graph(
        capabilityId: String = "solid-rect-pixel-aligned-simple-clip-src-over-srgb-v1",
        extent: SizeI32 = SizeI32(2, 2),
        capabilities: PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
            deviceGeneration = 7,
            maxTextureDimension2D = 2048,
            maxBufferSizeBytes = 1L shl 20,
            copyBytesPerRowAlignment = 256,
            supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
        ),
        drawBounds: RectI32 = RectI32(0, 0, 2, 2),
        readbackOrdinal: Int? = null,
        targetOrdinal: Int = 0,
        targetByteSize: Long = 16L,
        targetUsages: Set<PlanResourceUsage> = setOf(
            PlanResourceUsage.RenderAttachment,
            PlanResourceUsage.CopySource,
        ),
        stagingFirstPassIndex: Int = 1,
        readbackBytesPerRow: Long = 256L,
        includeDependency: Boolean = true,
        budget: PlanBudget = PlanBudget(1024),
    ): RenderGraph {
        if (capabilityId == W3SolidRectPlanCompiler.CAPABILITY_ID && extent == SizeI32(2, 2) &&
            capabilities == PlanCapabilitySnapshot.of(7, 2048, 1L shl 20, 256, setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL)) &&
            drawBounds == RectI32(0, 0, 2, 2) && readbackOrdinal == null &&
            targetOrdinal == 0 && targetByteSize == 16L &&
            targetUsages == setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource) &&
            stagingFirstPassIndex == 1 && readbackBytesPerRow == 256L && includeDependency &&
            budget == PlanBudget(1024)
        ) {
            val scene = SceneSnapshot.of(
                SceneExtent(2, 2),
                ColorSpace.SRGB,
                listOf(
                    SceneCommand.Draw(
                        DrawNode(
                            geometry = GeometryNode.Rect.of(RectF32(0f, 0f, 2f, 2f)),
                            material = MaterialNode.Solid(ColorARGB.fromPackedUInt(0xFF4080C0u)),
                            coverage = CoverageRequest.HARD_EDGE,
                            clip = ClipStackNode.Empty,
                            blend = BlendNode.SrcOver,
                            effects = EffectStack.Empty,
                            transform = Matrix3x3F32.Identity,
                            origin = DrawOrigin.RECT,
                        ),
                    ),
                ),
            )
            return assertIs<RenderPlanResult.Ready<RenderGraph>>(
                W3SolidRectPlanCompiler().plan(
                    scene,
                    RenderTargetDescriptor(scene.extent, ColorSpace.SRGB),
                    PlanCapabilitySnapshot.of(
                        7,
                        2048,
                        1L shl 20,
                        256,
                        setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
                    ),
                    PlanBudget(1024),
                ),
            ).plan
        }
        val target = PlanResource.of(
            PlanResourceRole.LogicalTarget, targetOrdinal, PlanResourceKind.Texture2D,
            PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL, extent, targetByteSize,
            targetUsages,
            PlanResourceLifetime.FrameLocal, 0, 2,
        )
        val staging = PlanResource.of(
            PlanResourceRole.ReadbackStaging, 0, PlanResourceKind.Buffer, null, null, readbackBytesPerRow * extent.height.toLong(),
            setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead),
            PlanResourceLifetime.FrameLocal, stagingFirstPassIndex, 2,
        )
        val render = PlanPass.RenderPass(
            0,
            target.id,
            listOf(
                SolidRectDraw.of(
                    commandIndex = 0,
                    color = ColorF32.of(0.25f, 0.5f, 0.75f, 1f),
                    visibleBounds = drawBounds,
                    scissor = drawBounds,
                    coverage = CoveragePlan.FullOrScissor,
                    sample = SamplePlan.SingleSample,
                    blend = BlendPlan.SrcOver,
                ),
            ),
            AttachmentLoadPlan.ClearTransparent,
            AttachmentStorePlan.Store,
        )
        val readback = PlanPass.ReadbackPass(readbackOrdinal ?: 0, target.id, staging.id, readbackBytesPerRow)
        return RenderGraph.of(
            PlanId("w3-plan"),
            capabilityId,
            extent,
            PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL,
            capabilities,
            budget,
            1,
            listOf(target, staging),
            listOf(render, readback),
            if (includeDependency) listOf(PlanPassDependency(render.id, readback.id)) else emptyList(),
            targetByteSize + readbackBytesPerRow * extent.height.toLong(),
        )
    }

    private fun copyPacket(
        source: GPUDrawPacket,
        targetStateHash: String,
        packetId: GPUDrawPacketID = source.packetId,
        commandIdValue: Int = source.commandIdValue,
        analysisRecordId: String = source.analysisRecordId,
        bindingListId: String = source.bindingListId,
        scissorBoundsHash: String = source.scissorBoundsHash ?: error("W3 packet must carry a scissor authority"),
        semanticPayload: GPUDrawSemanticPayload? = source.semanticPayload,
        sortKey: Long = source.sortKey,
        sortKeyPreimage: String = source.sortKeyPreimage,
        originalPaintOrder: Int = source.originalPaintOrder,
    ): GPUDrawPacket = GPUDrawPacket(
        packetId,
        commandIdValue,
        analysisRecordId,
        source.passId,
        source.layerId,
        bindingListId,
        source.insertionReasonCode,
        sortKey,
        sortKeyPreimage,
        source.renderStepId,
        source.renderStepVersion,
        source.role,
        source.blendPlan,
        source.renderPipelineKey,
        source.computePipelineKey,
        source.bindingLayoutHash,
        semanticPayload?.payloadRef?.uniformSlot ?: source.uniformSlot,
        source.resourceSlot,
        semanticPayload,
        source.vertexSourceLabel,
        scissorBoundsHash,
        targetStateHash,
        originalPaintOrder,
        source.resourceGeneration,
        source.frameProvenance,
        source.clipCoveragePlan,
        source.clipExecutionPlan,
        source.diagnostics,
        source.clipProducerAuthority,
    )

    private fun capabilities(
        maxTextureDimension2D: Long = 2048L,
        maxBufferSize: Long = 1L shl 20,
    ) = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "test", "adapter", "device"),
        facts = listOf(
            GPUCapabilityFact("first_slice.fill_rect.native", "test", "supported", true, "w3"),
            GPUCapabilityFact("first_slice.scissor.native", "test", "supported", true, "w3"),
        ),
        snapshotId = "w3-test",
        limits = GPULimits(
            maxTextureDimension2D,
            256,
            256,
            maxBufferSize = maxBufferSize,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
        supportedTextureFormats = setOf(GPUTextureFormat.RGBA8Unorm, GPUTextureFormat.RGBA8UnormSrgb),
        rendererFeatures = setOf(GPURendererFeature.RenderPass, GPURendererFeature.Readback),
    )
}
