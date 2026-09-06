package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.GPUCompareFunction
import io.ygdrasil.webgpu.GPUStencilOperation
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.gpu.plan.GpuPlanSelection
import org.graphiks.kanvas.gpu.plan.PlanBudget
import org.graphiks.kanvas.gpu.plan.PlanBufferAllocationPolicy
import org.graphiks.kanvas.gpu.plan.PlanCapabilitySnapshot
import org.graphiks.kanvas.gpu.plan.PlanDepthStencilFormat
import org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat
import org.graphiks.kanvas.gpu.plan.PlanOperationCapability
import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.plan.W4cPathFillPlanCompiler
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureFormatSampleSupport
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureSampleCountSupport
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.passes.W4cSessionScratchV1
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanLoweringRequest
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanLoweringResult
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanTaskListLowerer
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUSceneTarget
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureResourceRef
import org.graphiks.kanvas.gpu.renderer.state.GPUTargetIdentity
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome
import org.graphiks.kanvas.render.ir.BlendMode
import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.CoverageRequest
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.kanvas.render.ir.PaintNode
import org.graphiks.kanvas.render.ir.PaintStyleNode
import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneExtent
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.kanvas.render.ir.StrokeCapNode
import org.graphiks.kanvas.render.ir.StrokeJoinNode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.matrix.Matrix3x3F32

/** Public-frame regression coverage for W4c's sealed preflight entry point. */
class GPUWgpu4kCorePrimitiveW4cFramePayloadMaterializerTest {
    @Test
    fun `W4c marker reaches preflight and retains its one shared session scratch`() {
        val frame = W4cExecutionFixture.framePlan()
        val renders = frame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        val scratch = assertNotNull(
            renders.first().drawPackets.single().corePrimitivePreparedAuthority?.w4cSessionScratch,
        )

        assertTrue(renders.all { render ->
            render.drawPackets.single().corePrimitivePreparedAuthority?.w4cSessionScratch === scratch
        })
        assertEquals(listOf(0L, 1L, 1L, 2L), renders.map { it.drawPackets.single().sortKey })
        assertEquals(listOf(0L, 24L, 176L), scratch.draws.map { it.vertexOffsetBytes })
        assertEquals(3, scratch.uniformPlan.slots.size)

        val result = W4cExecutionFixture.preflight(frame)
        val prepared = assertIs<GPUFramePreflightResult.Prepared>(
            result,
            (result as? GPUFramePreflightResult.Refused)
                ?.let { "${it.diagnostic.code.value}: ${it.diagnostic.message}" }
                .orEmpty(),
        ).frame
        try {
            assertEquals(frame.capabilitySeal.sealHash, prepared.generationSeal.capabilitySealHash)
        } finally {
            assertTrue(prepared.claimForRollback())
            assertTrue(prepared.rollback.execute().successful)
        }
    }

    @Test
    fun `W4c materializer uploads sealed math bytes and retains writable stencil cover state`() {
        val fixture = W4cExecutionFixture.nativeMaterializationFixture()
        val materializer = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            requireNotNull(W4cExecutionFixture.capabilities().limits),
        )
        var draft: GPUPreparedNativeFrameDraft? = null
        try {
            val result = materializer.materializeReusable(
                fixture.frame,
                fixture.prepared.encoderPlan,
                fixture.prepared.resources,
                fixture.prepared.generationSeal,
            )
            val materialized = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
                result,
                (result as? GPUPreparedNativeFramePayloadMaterialization.Refused)
                    ?.let { "${it.code}: ${it.message}" }
                    .orEmpty(),
            )
            draft = materialized.draft
            val scratch = fixture.scratch
            val renders = materialized.draft.payload.scopeOperands
                .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
            val expectedRenderSteps = fixture.frame.steps.withIndex().mapNotNull { (index, step) ->
                index.takeIf { step is GPUFrameStep.RenderPassStep }
            }

            assertEquals(expectedRenderSteps, renders.map(GPUPreparedNativeScopeOperand.Render::sourceStepIndex))
            assertEquals(
                listOf(0, 1, 1, 2),
                renders.flatMap { render -> render.semanticPayloads }.map { semantic ->
                    assertIs<org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload.CorePrimitive>(semantic)
                        .payloadRef.commandIdValue
                },
            )
            assertEquals(
                listOf(0L, scratch.uniformStrideBytes, scratch.uniformStrideBytes,
                    scratch.uniformStrideBytes * 2L),
                renders.flatMap { render ->
                    render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetBindGroup>()
                }.map { command -> command.dynamicOffsets.single() },
            )
            assertEquals(
                listOf(
                    GPUPreparedNativeLoadOperation.Clear,
                    GPUPreparedNativeLoadOperation.Load,
                    GPUPreparedNativeLoadOperation.Load,
                    GPUPreparedNativeLoadOperation.Load,
                ),
                renders.map { render -> render.pass.loadOperation },
            )
            assertTrue(renders.all { render -> render.pass.storeOperation == GPUPreparedNativeStoreOperation.Store })

            val producer = renders[1]
            val cover = renders[2]
            assertEquals(GPUPreparedNativeLoadOperation.Clear, producer.pass.stencilLoadOperation)
            assertEquals(GPUPreparedNativeStoreOperation.Store, producer.pass.stencilStoreOperation)
            assertEquals(0u, producer.pass.stencilClearValue)
            assertFalse(producer.pass.stencilReadOnly)
            assertEquals(GPUPreparedNativeLoadOperation.Load, cover.pass.stencilLoadOperation)
            assertEquals(GPUPreparedNativeStoreOperation.Store, cover.pass.stencilStoreOperation)
            assertEquals(null, cover.pass.stencilClearValue)
            assertFalse(cover.pass.stencilReadOnly)
            assertEquals(null, renders.first().pass.depthStencilTarget)
            assertEquals(null, renders.last().pass.depthStencilTarget)
            assertSame(
                requireNotNull(producer.pass.depthStencilTarget).view,
                requireNotNull(cover.pass.depthStencilTarget).view,
            )

            val vertexUpload = fixture.native.writeBufferCalls.single { call ->
                call.bufferLabel == "Kanvas.session.corePrimitive.framePool.vertices"
            }
            val indexUpload = fixture.native.writeBufferCalls.single { call ->
                call.bufferLabel == "Kanvas.session.corePrimitive.framePool.indices"
            }
            val uniformUpload = fixture.native.writeBufferCalls.single { call ->
                call.bufferLabel == "Kanvas.session.corePrimitive.framePool.uniforms"
            }
            assertEquals(scratch.vertexUsefulBytes.toULong(), vertexUpload.size)
            assertEquals(scratch.indexUsefulBytes.toULong(), indexUpload.size)
            assertEquals(scratch.uniformPlan.totalBytes.toULong(), uniformUpload.size)
            assertContentEquals(expectedVertexBytes(scratch), vertexUpload.snapshot)
            assertContentEquals(expectedIndexBytes(scratch), indexUpload.snapshot)
            assertContentEquals(expectedUniformBytes(scratch, fixture.frame), uniformUpload.snapshot)

            val depthStencil = fixture.native.textureDescriptors.single { descriptor ->
                descriptor.format == GPUTextureFormat.Depth24PlusStencil8
            }
            assertEquals(4u, depthStencil.size.width)
            assertEquals(4u, depthStencil.size.height)
            assertEquals(1u, depthStencil.sampleCount)
            assertEquals(GPUTextureUsage.RenderAttachment, depthStencil.usage)
            val coverPipeline = fixture.native.renderPipelineDescriptors.single { descriptor ->
                descriptor.label == "Kanvas.session.corePrimitive.pipeline.PathStencilCoverRegular"
            }
            val coverStencil = assertNotNull(coverPipeline.depthStencil)
            assertEquals(GPUCompareFunction.NotEqual, coverStencil.stencilFront.compare)
            assertEquals(GPUCompareFunction.NotEqual, coverStencil.stencilBack.compare)
            assertEquals(GPUStencilOperation.Zero, coverStencil.stencilFront.passOp)
            assertEquals(GPUStencilOperation.Zero, coverStencil.stencilBack.passOp)
            assertEquals(0xffu, coverStencil.stencilWriteMask)
            assertEquals(
                listOf(
                    "Kanvas.session.corePrimitive.pipeline.PathStencilProducerEvenOdd",
                    "Kanvas.session.corePrimitive.pipeline.PathStencilCoverRegular",
                ),
                fixture.native.renderPipelineDescriptors.mapNotNull { descriptor ->
                    descriptor.label.takeIf { label -> "PathStencil" in label }
                },
            )
        } finally {
            draft?.let { prepared -> assertTrue(prepared.disposeBeforeRegistration()) }
            materializer.close()
            fixture.close()
        }
    }

    @Test
    fun `W4c writable Load cover reaches native execution with its prepared payload`() {
        val fixture = W4cExecutionFixture.nativeMaterializationFixture()
        val materializer = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            requireNotNull(W4cExecutionFixture.capabilities().limits),
        )
        val adapter = GPURuntimeResourceAdapter()
        try {
            val materialized = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
                materializer.materializeReusable(
                    fixture.frame,
                    fixture.prepared.encoderPlan,
                    fixture.prepared.resources,
                    fixture.prepared.generationSeal,
                ),
            )
            val registration = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
                adapter.registerPreparedNativeFrameDraft(materialized.draft),
            )
            assertIs<GPUPreparedNativeFrameBindingResult.Ready>(
                registration.ownership.bindLateSurface(
                    acquiredSurface = null,
                    binding = GPUPreparedNativeFrameLateSurfaceBinding.NotRequired,
                ),
            )
            assertTrue(fixture.prepared.rollback.adoptNativePayload(registration.ownership))

            val sceneTarget = GPUSceneTarget(
                targetId = fixture.scratch.target.value,
                resolvedTexture = GPUTextureResourceRef("prepared:${fixture.scratch.target.value}"),
                retainedMsaaAttachment = null,
                width = fixture.scratch.targetBounds.width,
                height = fixture.scratch.targetBounds.height,
                format = GPUColorFormat("rgba8unorm-srgb"),
                colorInterpretation = GPUColorInterpretation("linear-premul"),
                usages = setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource),
                sampleCount = 1,
                deviceGeneration = fixture.prepared.generationSeal.deviceGeneration,
                targetGeneration = fixture.prepared.generationSeal.targetGeneration,
            )
            val completion = object : GPUQueueCompletionAccess {
                override fun reserveTicket(request: GPUQueueCompletionTicketRequest) =
                    GPUQueueCompletionTicketReservation.Missing

                override fun abandonReservedTicket(ticket: GPUQueueCompletionTicket) =
                    GPUQueueCompletionTicketAbandonResult.NotReserved(ticket.ticketId)

                override fun armAfterSubmit(
                    ticket: GPUQueueCompletionTicket,
                    sink: GPUQueueCompletionSink,
                ): GPUQueueCompletionArmResult {
                    sink.accept(
                        GPUQueueCompletionDelivery.Accepted(
                            ticket.ticketId,
                            GPUQueueCompletionOutcome.Success,
                        ),
                    )
                    return GPUQueueCompletionArmResult.Armed(ticket.ticketId)
                }

                override suspend fun awaitCompletion(ticket: GPUQueueCompletionTicket) =
                    GPUQueueCompletionDelivery.Accepted(ticket.ticketId, GPUQueueCompletionOutcome.Success)

                override fun cancel(ticket: GPUQueueCompletionTicket) =
                    GPUQueueCompletionDelivery.Accepted(
                        ticket.ticketId,
                        GPUQueueCompletionOutcome.Failure(GPUQueueCompletionFailureKind.Cancelled),
                    )
            }
            val readback = object : GPUFrameReadbackAccess {
                override fun markSubmitted(
                    ticket: GPUQueueCompletionTicket,
                    output: GPUPreparedReadbackOutput,
                    operand: GPUPreparedNativeScopeOperand.Readback,
                ) = GPUFrameReadbackLifecycleResult.Applied

                override fun acceptGPUCompletion(
                    ticket: GPUQueueCompletionTicket,
                    output: GPUPreparedReadbackOutput,
                    operand: GPUPreparedNativeScopeOperand.Readback,
                ) = GPUFrameReadbackLifecycleResult.Applied

                override fun rejectGPUCompletion(
                    ticket: GPUQueueCompletionTicket,
                    output: GPUPreparedReadbackOutput,
                    operand: GPUPreparedNativeScopeOperand.Readback,
                    failure: GPUQueueCompletionFailureKind,
                ) = GPUFrameReadbackLifecycleResult.Applied

                override fun mapAndDepad(
                    output: GPUPreparedReadbackOutput,
                    operand: GPUPreparedNativeScopeOperand.Readback,
                    sink: GPUFrameReadbackMapSink,
                ): GPUFrameReadbackMapArmResult {
                    sink.accept(
                        GPUFrameReadbackMapDelivery.Pixels(
                            output.request.requestId,
                            ByteArray(output.layout.width * output.layout.height * output.layout.bytesPerPixel),
                        ),
                    )
                    return GPUFrameReadbackMapArmResult.Armed
                }

                override fun finalizeAfterNativeClose(
                    output: GPUPreparedReadbackOutput,
                    operand: GPUPreparedNativeScopeOperand.Readback,
                    safety: GPUFrameReadbackNativeOutputSafety,
                ) = GPUFrameReadbackLifecycleResult.Applied
            }
            val handle = GPUFrameExecutor(
                sceneTarget = sceneTarget,
                backend = object : GPUFrameEncodingBackend {
                    override val deviceGeneration = fixture.prepared.generationSeal.deviceGeneration
                    override val encodingMode = GPUFrameEncodingMode.NativeOperandsRequired

                    override fun createCommandEncoder(label: String) = object : GPUFrameCommandEncoder {
                        override fun encode(
                            scope: GPUCommandEncoderScopePlan,
                            preparedFrame: PreparedGPUFrame,
                            sceneTarget: GPUSceneTarget,
                            nativeOperand: GPUPreparedNativeScopeOperand?,
                        ) {
                            requireNotNull(nativeOperand)
                        }

                        override fun finish() = GPUFrameCommandBuffer("w4c.native.execution")

                        override fun discard() = GPUFrameDiscardResult.Discarded
                    }

                    override fun isCanonicalSceneTargetView(
                        sceneTarget: GPUSceneTarget,
                        operand: GPUPreparedNativeTextureViewOperand,
                    ) = operand.view === fixture.target.view

                    override fun discard(commandBuffer: GPUFrameCommandBuffer) =
                        GPUFrameDiscardResult.Discarded

                    override fun submit(commandBuffer: GPUFrameCommandBuffer) = Unit
                },
                completion = completion,
                retention = object : GPUFrameResourceRetention {
                    override fun registerAfterSubmit(registration: GPUFrameRetentionRegistration) = Unit

                    override fun complete(
                        ticket: GPUQueueCompletionTicket,
                        outcome: GPUQueueCompletionOutcome,
                    ) = Unit

                    override fun quarantine(
                        registration: GPUFrameRetentionRegistration,
                        diagnostic: GPUDiagnostic,
                    ) = Unit
                },
                readback = readback,
            ).execute(fixture.prepared)

            assertIs<GPUFrameImmediateState.Submitted>(handle.immediateState)
            assertEquals(
                GPUFrameStructuralOutcome.Succeeded,
                handle.completion.toCompletableFuture().join().outcome,
            )
            assertEquals(0, adapter.activePreparedNativeFramePayloadCount)
        } finally {
            materializer.close()
            adapter.close()
            fixture.close()
        }
    }

    @Test
    fun `W4c direct-only frame materializes without a D24S8 lease`() {
        val fixture = W4cExecutionFixture.nativeMaterializationFixture(
            W4cExecutionFixture.directOnlyFramePlan(),
        )
        val materializer = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            requireNotNull(W4cExecutionFixture.capabilities().limits),
        )
        var draft: GPUPreparedNativeFrameDraft? = null
        try {
            val materialized = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
                materializer.materializeReusable(
                    fixture.frame,
                    fixture.prepared.encoderPlan,
                    fixture.prepared.resources,
                    fixture.prepared.generationSeal,
                ),
            )
            draft = materialized.draft
            val renders = materialized.draft.payload.scopeOperands
                .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
            assertEquals(1, renders.size)
            assertEquals(null, renders.single().pass.depthStencilTarget)
            assertTrue(renders.single().pass.stencilReadOnly)
            assertTrue(
                fixture.native.textureDescriptors.none { descriptor ->
                    descriptor.format == GPUTextureFormat.Depth24PlusStencil8
                },
            )
        } finally {
            draft?.let { prepared -> assertTrue(prepared.disposeBeforeRegistration()) }
            materializer.close()
            fixture.close()
        }
    }

    @Test
    fun `W4c winding producer uses its existing producer and regular cover programs`() {
        val fixture = W4cExecutionFixture.nativeMaterializationFixture(
            W4cExecutionFixture.windingFramePlan(),
        )
        val materializer = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            requireNotNull(W4cExecutionFixture.capabilities().limits),
        )
        var draft: GPUPreparedNativeFrameDraft? = null
        try {
            val materialized = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
                materializer.materializeReusable(
                    fixture.frame,
                    fixture.prepared.encoderPlan,
                    fixture.prepared.resources,
                    fixture.prepared.generationSeal,
                ),
            )
            draft = materialized.draft
            assertEquals(
                listOf(
                    "Kanvas.session.corePrimitive.pipeline.PathStencilProducerWinding",
                    "Kanvas.session.corePrimitive.pipeline.PathStencilCoverRegular",
                ),
                fixture.native.renderPipelineDescriptors.mapNotNull { descriptor ->
                    descriptor.label.takeIf { label -> "PathStencil" in label }
                },
            )
        } finally {
            draft?.let { prepared -> assertTrue(prepared.disposeBeforeRegistration()) }
            materializer.close()
            fixture.close()
        }
    }

    @Test
    fun `W4c post-checkout render refusal returns its exact pooled handles`() {
        val fixture = W4cExecutionFixture.nativeMaterializationFixture()
        val materializer = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            requireNotNull(W4cExecutionFixture.capabilities().limits),
        )
        var retried: GPUWgpu4kCorePrimitiveFramePoolLease? = null
        var handlesReturned = false
        try {
            fixture.acquireW4cPipelineComponents()
            val requirements = fixture.exactW4cFramePoolRequirements()
            val baseline = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(
                fixture.cache.acquireFrame(requirements),
            ).lease
            val baselineVertex = baseline.handles.vertexBuffer
            val baselineIndex = baseline.handles.indexBuffer
            val baselineUniform = baseline.handles.uniformBuffer
            val baselineDepthStencil = requireNotNull(baseline.handles.pathDepthStencil)
            assertIs<GPUWgpu4kCorePrimitiveFramePoolLeaseTransition.Applied>(
                baseline.rollbackBeforeSubmit(),
            )

            val refusal = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(
                materializer.materializeReusable(
                    fixture.frame.withW4cCoverSamplePlan(GPUSamplePlan.MultisampleFrame(4)),
                    fixture.prepared.encoderPlan,
                    fixture.prepared.resources,
                    fixture.prepared.generationSeal,
                ),
            )
            assertEquals("invalid.native-core-primitive.w4c-render-run", refusal.code)

            retried = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(
                fixture.cache.acquireFrame(requirements),
            ).lease
            assertSame(baselineVertex, retried.handles.vertexBuffer)
            assertSame(baselineIndex, retried.handles.indexBuffer)
            assertSame(baselineUniform, retried.handles.uniformBuffer)
            assertSame(baselineDepthStencil.texture, requireNotNull(retried.handles.pathDepthStencil).texture)
            assertSame(baselineDepthStencil.view, requireNotNull(retried.handles.pathDepthStencil).view)
            handlesReturned = true
        } finally {
            retried?.rollbackBeforeSubmit()
            materializer.close()
            val close = runCatching(fixture::close)
            if (handlesReturned) close.getOrThrow()
        }
    }

    @Test
    fun `only a planned W4c cover may retain writable Load stencil state`() {
        val fixture = W4cExecutionFixture.nativeMaterializationFixture()
        try {
            val coverIndex = fixture.frame.steps.indexOfFirst { step ->
                (step as? GPUFrameStep.RenderPassStep)
                    ?.drawPackets
                    ?.singleOrNull()
                    ?.role == GPUDrawPacketRole.PathStencilCover
            }
            assertTrue(coverIndex >= 0)
            val cover = fixture.frame.steps[coverIndex] as GPUFrameStep.RenderPassStep
            val originalPacket = cover.drawPackets.single()
            val nonW4cPacket = GPUDrawPacket(
                packetId = originalPacket.packetId,
                commandIdValue = originalPacket.commandIdValue,
                analysisRecordId = originalPacket.analysisRecordId,
                passId = originalPacket.passId,
                layerId = originalPacket.layerId,
                bindingListId = originalPacket.bindingListId,
                insertionReasonCode = originalPacket.insertionReasonCode,
                sortKey = originalPacket.sortKey,
                sortKeyPreimage = originalPacket.sortKeyPreimage,
                renderStepId = originalPacket.renderStepId,
                renderStepVersion = originalPacket.renderStepVersion,
                role = GPUDrawPacketRole.Shading,
                blendPlan = originalPacket.blendPlan,
                renderPipelineKey = originalPacket.renderPipelineKey,
                computePipelineKey = originalPacket.computePipelineKey,
                bindingLayoutHash = originalPacket.bindingLayoutHash,
                uniformSlot = originalPacket.uniformSlot,
                resourceSlot = originalPacket.resourceSlot,
                semanticPayload = originalPacket.semanticPayload,
                vertexSourceLabel = originalPacket.vertexSourceLabel,
                scissorBoundsHash = originalPacket.scissorBoundsHash,
                targetStateHash = originalPacket.targetStateHash,
                originalPaintOrder = originalPacket.originalPaintOrder,
                resourceGeneration = originalPacket.resourceGeneration,
                frameProvenance = originalPacket.frameProvenance,
                clipCoveragePlan = originalPacket.clipCoveragePlan,
                clipExecutionPlan = originalPacket.clipExecutionPlan,
                diagnostics = originalPacket.diagnostics,
                clipProducerAuthority = originalPacket.clipProducerAuthority,
            )
            val nonW4cCover = GPUFrameStep.RenderPassStep(
                target = cover.target,
                loadStore = cover.loadStore,
                samplePlan = cover.samplePlan,
                resourceUses = cover.resourceUses,
                drawPackets = listOf(nonW4cPacket),
                sourceTaskIds = cover.sourceTaskIds,
                sampleContinuation = cover.sampleContinuation,
                depthStencilLoadStore = cover.depthStencilLoadStore,
                preparedImageBindingsByPacketId = cover.preparedImageBindingsByPacketId,
                preparedTextBindingsByPacketId = cover.preparedTextBindingsByPacketId,
            )
            val corruptedPlan = GPUFramePlan(
                frameId = fixture.frame.frameId,
                capabilitySeal = fixture.frame.capabilitySeal,
                recordingSeals = fixture.frame.recordingSeals,
                steps = fixture.frame.steps.mapIndexed { index, step ->
                    if (index == coverIndex) nonW4cCover else step
                },
                memoryBudget = fixture.frame.memoryBudget,
                diagnostics = fixture.frame.diagnostics,
                dependencies = fixture.frame.dependencies,
                phaseOrder = fixture.frame.phaseOrder,
                elidedNoOpDraws = fixture.frame.elidedNoOpDraws,
                atomicallyRefused = fixture.frame.atomicallyRefused,
            )
            val ticket = GPUQueueCompletionTicket(
                GPUQueueCompletionTicketID("ticket.w4c.non-authoritative-cover"),
                corruptedPlan.frameId,
                fixture.prepared.generationSeal.deviceGeneration,
            )
            val rollback = GPUFrameRollback(
                ownerScope = "w4c.non-authoritative-cover",
                resourceProvider = GPUConcreteResourceProvider(),
                surfaceProvider = object : GPUSurfaceOutputProvider {
                    override fun acquire(request: GPUSurfaceAcquisitionRequest) =
                        error("The forged prepared frame must not acquire a surface")

                    override fun release(output: GPUAcquiredSurfaceOutput) =
                        GPUSurfaceReleaseResult.Released
                },
                completionProvider = object : GPUQueueCompletionProvider {
                    override fun reserveTicket(request: GPUQueueCompletionTicketRequest) =
                        GPUQueueCompletionTicketReservation.Missing

                    override fun abandonReservedTicket(ticket: GPUQueueCompletionTicket) =
                        GPUQueueCompletionTicketAbandonResult.Abandoned(ticket.ticketId)
                },
                completionTicket = ticket,
            )
            val result = runCatching {
                PreparedGPUFrame(
                    semanticPlan = corruptedPlan,
                    encoderPlan = fixture.prepared.encoderPlan,
                    resources = fixture.prepared.resources,
                    generationSeal = fixture.prepared.generationSeal,
                    completionTicket = ticket,
                    acquiredSurfaceOutput = null,
                    rollback = rollback,
                    stepPartition = fixture.prepared.stepPartition,
                    dependencyEvidence = fixture.prepared.dependencyEvidence,
                    hostActions = fixture.prepared.hostActions,
                )
            }
            assertTrue(
                result.isFailure,
                "A non-W4c packet must not retain WritableStencil(Load, Store, null).",
            )
        } finally {
            fixture.close()
        }
    }

    private fun expectedVertexBytes(scratch: W4cSessionScratchV1): ByteArray = ArrayBuffer.of(
        scratch.draws.flatMap { draw ->
            val geometry = draw.copyGeometryF32()
            geometry.copyDirectTriangleF32OrNull()?.copyVerticesF32()?.asList() ?: buildList {
                addAll(requireNotNull(geometry.copyStencilEdgeFanF32OrNull()).copyVerticesF32().asList())
                val cover = draw.copyScissorBounds()
                addAll(
                    listOf(
                        cover.left.toFloat(), cover.top.toFloat(),
                        cover.right.toFloat(), cover.top.toFloat(),
                        cover.right.toFloat(), cover.bottom.toFloat(),
                        cover.left.toFloat(), cover.bottom.toFloat(),
                    ),
                )
            }
        }.toFloatArray(),
    ).toByteArray()

    private fun expectedIndexBytes(scratch: W4cSessionScratchV1): ByteArray = ArrayBuffer.of(
        scratch.draws.flatMap { draw ->
            val geometry = draw.copyGeometryF32()
            geometry.copyDirectTriangleF32OrNull()?.copyIndicesI32()?.asList() ?: buildList {
                addAll(requireNotNull(geometry.copyStencilEdgeFanF32OrNull()).copyIndicesI32().asList())
                addAll(listOf(0, 2, 1, 0, 3, 2))
            }
        }.toIntArray(),
    ).toByteArray()

    private fun expectedUniformBytes(
        scratch: W4cSessionScratchV1,
        frame: org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan,
    ): ByteArray = ByteArray(scratch.uniformPlan.totalBytes.toInt()).also { packed ->
        val packets = frame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            .flatMap(GPUFrameStep.RenderPassStep::drawPackets)
        scratch.draws.forEach { draw ->
            val semantic = assertIs<org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload.CorePrimitive>(
                packets.first { packet -> packet.commandIdValue == draw.commandId }.semanticPayload,
            )
            val bytes = requireNotNull(semantic.payloadRef.uniformBlock).bytes
                .map(Int::toByte)
                .toByteArray()
            bytes.copyInto(
                packed,
                destinationOffset = scratch.uniformPlan.slots[draw.uniformSlotIndex].alignedOffset.toInt(),
            )
        }
    }

    private fun W4cExecutionFixture.NativeMaterializationFixture.exactW4cFramePoolRequirements():
        GPUWgpu4kCorePrimitiveFramePoolRequirements {
        val componentIdentities = w4cPipelineMappings().map(
            GPUWgpu4kCorePrimitivePipelineMapping.Mapped::componentIdentity,
        ).toSet()
        return GPUWgpu4kCorePrimitiveFramePoolRequirements(
            deviceGeneration = prepared.generationSeal.deviceGeneration,
            vertexBytes = scratch.vertexUsefulBytes,
            indexBytes = scratch.indexUsefulBytes,
            uniformBytes = scratch.uniformPlan.totalBytes,
            expectedCapacities = GPUWgpu4kCorePrimitiveFramePoolCapacities(
                scratch.vertexCapacityBytes,
                scratch.indexCapacityBytes,
                scratch.uniformCapacityBytes,
            ),
            pathDepthStencil = GPUWgpu4kCorePrimitivePathDepthStencilRequirement(
                width = scratch.targetBounds.width,
                height = scratch.targetBounds.height,
                format = GPUTextureFormat.Depth24PlusStencil8,
                sampleCount = 1,
                usage = GPUTextureUsage.RenderAttachment,
                target = scratch.target,
                depthStencilAttachment = GPUTargetIdentity(
                    scratch.target.value.removeSuffix(".target").plus(".depth-stencil"),
                ),
                deviceGeneration = prepared.generationSeal.deviceGeneration,
                targetGeneration = prepared.generationSeal.targetGeneration,
            ),
            componentIdentity = PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY,
            sampleCount = 1,
            additionalComponentIdentities = componentIdentities -
                PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY,
        )
    }

    private fun W4cExecutionFixture.NativeMaterializationFixture.acquireW4cPipelineComponents() {
        w4cPipelineMappings().forEach { mapping ->
            assertIs<GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired>(
                cache.acquire(
                    GPUWgpu4kCorePrimitivePipelineCacheKey(
                        mapping.componentIdentity,
                        mapping.identity,
                    ),
                ),
            )
        }
    }

    private fun W4cExecutionFixture.NativeMaterializationFixture.w4cPipelineMappings():
        List<GPUWgpu4kCorePrimitivePipelineMapping.Mapped> = frame.steps
        .filterIsInstance<GPUFrameStep.RenderPassStep>()
        .map { render ->
            val structural = requireNotNull(
                render.drawPackets.single().corePrimitivePreparedAuthority,
            ).structuralPipelineKey
            mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(structural)
                as? GPUWgpu4kCorePrimitivePipelineMapping.Mapped
                ?: error("W4c test frame must use an admitted CorePrimitive pipeline")
        }
        .distinct()

    private fun GPUFramePlan.withW4cCoverSamplePlan(samplePlan: GPUSamplePlan): GPUFramePlan {
        val coverIndex = steps.indexOfFirst { step ->
            (step as? GPUFrameStep.RenderPassStep)?.drawPackets?.singleOrNull()?.role ==
                GPUDrawPacketRole.PathStencilCover
        }
        require(coverIndex >= 0) { "W4c test frame must contain one stencil cover" }
        val cover = steps[coverIndex] as GPUFrameStep.RenderPassStep
        val malformedCover = GPUFrameStep.RenderPassStep(
            target = cover.target,
            loadStore = cover.loadStore,
            samplePlan = samplePlan,
            resourceUses = cover.resourceUses,
            drawPackets = cover.drawPackets,
            sourceTaskIds = cover.sourceTaskIds,
            batches = cover.batches,
            depthStencilLoadStore = cover.depthStencilLoadStore,
            preparedImageBindingsByPacketId = cover.preparedImageBindingsByPacketId,
            preparedTextBindingsByPacketId = cover.preparedTextBindingsByPacketId,
        )
        return GPUFramePlan(
            frameId = frameId,
            capabilitySeal = capabilitySeal,
            recordingSeals = recordingSeals,
            steps = steps.mapIndexed { index, step -> if (index == coverIndex) malformedCover else step },
            memoryBudget = memoryBudget,
            diagnostics = diagnostics,
            dependencies = dependencies,
            phaseOrder = phaseOrder,
            elidedNoOpDraws = elidedNoOpDraws,
            atomicallyRefused = atomicallyRefused,
        )
    }
}

internal object W4cExecutionFixture {
    private val generation = GPUDeviceGenerationID(7)
    private val color = ColorARGB.fromPackedUInt(0x80ff0000u)

    fun framePlan() = GPUFramePlanner.plan(taskList())

    fun directOnlyFramePlan() = GPUFramePlanner.plan(taskList(directOnly = true))

    fun windingFramePlan() = GPUFramePlanner.plan(taskList(windingOnly = true))

    class NativeMaterializationFixture internal constructor(
        val frame: org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan,
        val prepared: PreparedGPUFrame,
        val scratch: W4cSessionScratchV1,
        val native: GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy,
        val target: GPUWgpu4kPreparedSceneTarget,
        val cache: GPUWgpu4kCorePrimitiveSessionCache,
    ) {
        fun close() {
            cache.close()
            target.close()
            if (prepared.claimForRollback()) {
                check(prepared.rollback.execute().successful)
            }
        }
    }

    fun nativeMaterializationFixture(
        frame: org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan = framePlan(),
    ): NativeMaterializationFixture {
        val prepared = assertIs<GPUFramePreflightResult.Prepared>(preflight(frame)).frame
        val scratch = assertNotNull(
            frame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
                .first().drawPackets.single().corePrimitivePreparedAuthority?.w4cSessionScratch,
        )
        val native = GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy()
        val setup = GPUPreparedSceneSetupTransaction()
        val target = GPUWgpu4kPreparedSceneTarget.create(
            native.device,
            scratch.targetBounds.width,
            scratch.targetBounds.height,
            GPUTextureFormat.RGBA8UnormSrgb,
            generation,
            1L,
            GPUWgpu4kPreparedSceneTargetLifecycle(),
            setup,
        )
        setup.commit()
        return NativeMaterializationFixture(
            frame,
            prepared,
            scratch,
            native,
            target,
            GPUWgpu4kCorePrimitiveSessionCache(native.device, generation),
        )
    }

    fun preflight(frame: org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan): GPUFramePreflightResult {
        val generations = frame.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .mapIndexed { index, request -> request.resource to index.toLong() + 1L }
            .toMap()
        val target = frame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().first().target
        return GPUFramePreflighter(
            context = GPUFramePreflightContext(
                targetId = target.value,
                deviceGeneration = generation,
                targetGeneration = 1L,
                resourceGenerations = generations,
            ),
            capabilities = capabilities(),
            resourceProvider = GPUConcreteResourceProvider(),
            completionProvider = object : GPUQueueCompletionProvider {
                override fun reserveTicket(request: GPUQueueCompletionTicketRequest) =
                    GPUQueueCompletionTicketReservation.Reserved(
                        GPUQueueCompletionTicket(
                            GPUQueueCompletionTicketID("ticket.w4c.preflight"),
                            request.frameId,
                            request.deviceGeneration,
                        ),
                    )

                override fun abandonReservedTicket(ticket: GPUQueueCompletionTicket) =
                    GPUQueueCompletionTicketAbandonResult.Abandoned(ticket.ticketId)
            },
            surfaceProvider = object : GPUSurfaceOutputProvider {
                override fun acquire(request: GPUSurfaceAcquisitionRequest): GPUSurfaceAcquisitionResult =
                    error("W4c offscreen preflight must not acquire a surface")

                override fun release(output: GPUAcquiredSurfaceOutput) =
                    GPUSurfaceReleaseResult.Released
            },
        ).preflight(frame)
    }

    fun capabilities(): GPUCapabilities = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "test", "w4c", "device"),
        facts = emptyList(),
        snapshotId = "w4c-execution",
        limits = GPULimits(
            maxTextureDimension2D = 2048,
            copyBytesPerRowAlignment = 256,
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = 1L shl 20,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
        supportedTextureFormats = setOf(
            GPUTextureFormat.RGBA8UnormSrgb,
            GPUTextureFormat.Depth24PlusStencil8,
        ),
        textureFormatSampleSupport = GPUTextureFormatSampleSupport(
            mapOf(
                GPUTextureFormat.RGBA8UnormSrgb to GPUTextureSampleCountSupport(setOf(1)),
                GPUTextureFormat.Depth24PlusStencil8 to GPUTextureSampleCountSupport(setOf(1)),
            ),
        ),
        rendererFeatures = setOf(
            GPURendererFeature.RenderPass,
            GPURendererFeature.CopyUpload,
            GPURendererFeature.UniformBuffer,
            GPURendererFeature.Readback,
        ),
    )

    private fun taskList(
        directOnly: Boolean = false,
        windingOnly: Boolean = false,
    ) = assertIs<GpuPlanLoweringResult.Lowered>(
        GpuPlanTaskListLowerer().lower(
            GpuPlanLoweringRequest(
                graph = graph(directOnly, windingOnly),
                capabilities = capabilities(),
                deviceGeneration = generation,
                currentBudget = graph(directOnly, windingOnly).budget,
                frameId = GPUFrameID(4),
                recordingId = GPURecordingID("w4c-execution"),
            ),
        ),
    ).taskList

    private fun graph(
        directOnly: Boolean = false,
        windingOnly: Boolean = false,
    ): RenderGraph {
        val scene = SceneSnapshot.of(
            SceneExtent(4, 4),
            ColorSpace.SRGB,
            when {
                directOnly -> listOf(triangle())
                windingOnly -> listOf(concaveWinding())
                else -> listOf(triangle(), concaveEvenOdd(), triangle())
            },
        )
        val compiler = W4cPathFillPlanCompiler()
        val candidate = assertIs<GpuPlanSelection.Candidate>(
            compiler.select(scene, RenderTargetDescriptor(scene.extent, scene.colorSpace)),
        ).candidate
        return assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, planCapabilities(), PlanBudget(1L shl 20)),
        ).plan
    }

    private fun planCapabilities(): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
        deviceGeneration = generation.value,
        maxTextureDimension2D = 2048,
        maxBufferSizeBytes = 1L shl 20,
        copyBytesPerRowAlignment = 256,
        supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
        minUniformBufferOffsetAlignment = 256,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
        supportedOperations = setOf(
            PlanOperationCapability.RenderPass,
            PlanOperationCapability.CopyUpload,
            PlanOperationCapability.UniformBuffer,
            PlanOperationCapability.Readback,
            PlanOperationCapability.DepthStencilAttachment,
            PlanOperationCapability.StencilCover,
        ),
        bufferAllocationPolicy = PlanBufferAllocationPolicy.of(16_384L, 4_096L, 4_096L),
        supportedDepthStencilFormats = setOf(PlanDepthStencilFormat.Depth24PlusStencil8),
    )

    private fun triangle(): SceneCommand.Draw = SceneCommand.Draw(pathNode(
        PathBuilder()
            .moveTo(0f, 0f)
            .lineTo(4f, 0f)
            .lineTo(0f, 3f)
            .close()
            .build(),
    ))

    private fun concaveEvenOdd(): SceneCommand.Draw = SceneCommand.Draw(pathNode(
        PathBuilder(FillRule.EVEN_ODD)
            .moveTo(0f, 0f)
            .lineTo(4f, 0f)
            .lineTo(4f, 3f)
            .lineTo(2f, 1f)
            .lineTo(0f, 3f)
            .close()
            .build(),
    ))

    private fun concaveWinding(): SceneCommand.Draw = SceneCommand.Draw(pathNode(
        PathBuilder()
            .moveTo(0f, 0f)
            .lineTo(4f, 0f)
            .lineTo(4f, 3f)
            .lineTo(2f, 1f)
            .lineTo(0f, 3f)
            .close()
            .build(),
    ))

    private fun pathNode(path: org.graphiks.math.geometry.PathF32): DrawNode = DrawNode(
        geometry = GeometryNode.Path(path),
        material = MaterialNode.Solid(color),
        coverage = CoverageRequest.HARD_EDGE,
        clip = GPUClipCoveragePlan.NoClip.let { ClipStackNode.Empty },
        blend = BlendNode.SrcOver,
        effects = EffectStack.Empty,
        transform = Matrix3x3F32.Identity,
        origin = DrawOrigin.PATH,
        paint = PaintNode(
            color,
            null,
            BlendMode.SRC_OVER,
            null,
            null,
            null,
            null,
            null,
            PaintStyleNode.FILL,
            0f,
            StrokeCapNode.BUTT,
            StrokeJoinNode.MITER,
            4f,
            false,
        ),
    )
}
