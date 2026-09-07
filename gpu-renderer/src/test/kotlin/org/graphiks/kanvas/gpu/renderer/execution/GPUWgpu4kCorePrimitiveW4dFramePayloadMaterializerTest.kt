package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
import org.graphiks.kanvas.gpu.plan.W4dPathStrokePlanCompiler
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureFormatSampleSupport
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureSampleCountSupport
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.passes.W4dSessionScratchV1
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanLoweringRequest
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanLoweringResult
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanTaskListLowerer
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameCapabilitySeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUSceneTarget
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.state.GPUPathSourceAuthority
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
import org.graphiks.math.geometry.PathF32
import org.graphiks.math.geometry.PathStrokeDrawMode
import org.graphiks.math.geometry.PathStrokeWidthF64
import org.graphiks.math.matrix.Matrix3x3F32

class GPUWgpu4kCorePrimitiveW4dFramePayloadMaterializerTest {
    @Test
    fun `W4d marker reaches preflight with one authenticated shared scratch`() {
        val frame = W4dExecutionFixture.framePlan()
        val renders = frame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        val scratch = assertNotNull(
            renders.first().drawPackets.single().corePrimitivePreparedAuthority?.w4dSessionScratch,
        )

        assertTrue(renders.all { render ->
            render.drawPackets.single().corePrimitivePreparedAuthority?.w4dSessionScratch === scratch
        })
        renders.flatMap(GPUFrameStep.RenderPassStep::drawPackets).forEach { packet ->
            val semantic = assertIs<
                org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload.CorePrimitive
            >(packet.semanticPayload)
            val geometry = assertIs<
                org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry.TriangulatedPath
            >(semantic.geometry)
            kotlin.test.assertEquals(GPUPathSourceAuthority.W4dPlannedPathStrokeV1, geometry.sourceAuthority)
        }

        val result = W4dExecutionFixture.preflight(frame)
        val prepared = assertIs<GPUFramePreflightResult.Prepared>(
            result,
            (result as? GPUFramePreflightResult.Refused)
                ?.let { "${it.diagnostic.code.value}: ${it.diagnostic.message}" }
                .orEmpty(),
        ).frame
        try {
            assertSame(scratch, renders.last().drawPackets.single()
                .corePrimitivePreparedAuthority?.w4dSessionScratch)
        } finally {
            assertTrue(prepared.claimForRollback())
            assertTrue(prepared.rollback.execute().successful)
        }
    }

    @Test
    fun `W4d preflight rejects a foreign capability seal before preparing a frame`() {
        val frame = W4dExecutionFixture.framePlan()
        val foreignSeal = GPUFrameCapabilitySeal.capture(
            frame.frameId,
            frame.capabilitySeal.deviceGeneration,
            W4dExecutionFixture.capabilities().copy(snapshotId = "w4d-execution.foreign"),
        )

        val result = W4dExecutionFixture.preflight(frame.copyForW4dExecutionTest(capabilitySeal = foreignSeal))

        assertEquals(
            "invalid.preflight.w4d_session_scratch",
            assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `W4d preflight rejects undersized staging that contradicts sealed resource bytes`() {
        val frame = W4dExecutionFixture.framePlan()
        val prepare = assertIs<GPUFrameStep.PrepareResourcesStep>(frame.steps.first())
        val staging = prepare.requests.single { request ->
            request.role == GPUFrameResourceRole.ReadbackStaging
        }
        val descriptor = assertIs<GPUFrameBufferDescriptor>(staging.descriptor)
        val undersizedDescriptor = descriptor.copy(byteSize = descriptor.byteSize - 256L)
        val undersized = GPUResourcePreparationRequest(
            resource = staging.resource,
            descriptor = undersizedDescriptor,
            role = staging.role,
            usages = staging.usages,
            lifetime = staging.lifetime,
            byteSize = undersizedDescriptor.byteSize,
            diagnosticLabel = staging.diagnosticLabel,
        )
        val malformedPrepare = GPUFrameStep.PrepareResourcesStep(
            prepare.requests.map { request -> undersized.takeIf { request === staging } ?: request },
            prepare.sourceTaskIds,
        )

        val result = W4dExecutionFixture.preflight(
            frame.copyForW4dExecutionTest(steps = listOf(malformedPrepare) + frame.steps.drop(1)),
        )

        assertEquals(
            "invalid.preflight.w4d_session_scratch",
            assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `W4d materializer uploads exact planned bytes and acquires one shared D24S8`() {
        val fixture = W4dExecutionFixture.nativeMaterializationFixture()
        val materializer = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            requireNotNull(W4dExecutionFixture.capabilities().limits),
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
            val renders = materialized.draft.payload.scopeOperands
                .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
            assertEquals(5, renders.size)
            assertEquals(
                listOf(0L, 1L, 1L, 2L, 2L),
                renders.flatMap(GPUPreparedNativeScopeOperand.Render::semanticPayloads).map { semantic ->
                    assertIs<
                        org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload.CorePrimitive
                    >(semantic).payloadRef.commandIdValue.toLong()
                },
            )
            assertNull(renders.first().pass.depthStencilTarget)
            assertSame(
                requireNotNull(renders[1].pass.depthStencilTarget).view,
                requireNotNull(renders[2].pass.depthStencilTarget).view,
            )
            assertSame(
                requireNotNull(renders[1].pass.depthStencilTarget).view,
                requireNotNull(renders[3].pass.depthStencilTarget).view,
            )
            assertSame(
                requireNotNull(renders[1].pass.depthStencilTarget).view,
                requireNotNull(renders[4].pass.depthStencilTarget).view,
            )
            assertEquals(
                listOf(0L, 1L, 1L, 2L, 2L).map { slot ->
                    slot * fixture.scratch.uniformStrideBytes
                },
                renders.flatMap { render ->
                    render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetBindGroup>()
                }.map { command -> command.dynamicOffsets.single() },
            )
            assertEquals(
                setOf(
                    "Kanvas.session.corePrimitive.pipeline.DirectSrcOver",
                    "Kanvas.session.corePrimitive.pipeline.PathStencilProducerEvenOdd",
                    "Kanvas.session.corePrimitive.pipeline.PathStencilProducerWinding",
                    "Kanvas.session.corePrimitive.pipeline.PathStencilCoverRegular",
                ),
                fixture.native.renderPipelineDescriptors.map { descriptor -> descriptor.label }.toSet(),
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
            assertEquals(fixture.scratch.vertexUsefulBytes.toULong(), vertexUpload.size)
            assertEquals(fixture.scratch.indexUsefulBytes.toULong(), indexUpload.size)
            assertEquals(fixture.scratch.uniformPlan.totalBytes.toULong(), uniformUpload.size)
            assertContentEquals(expectedVertexBytes(fixture.scratch), vertexUpload.snapshot)
            assertContentEquals(expectedIndexBytes(fixture.scratch), indexUpload.snapshot)
            assertContentEquals(
                expectedUniformBytes(fixture.scratch, fixture.frame),
                uniformUpload.snapshot,
            )
            val depthStencil = fixture.native.textureDescriptors.single { descriptor ->
                descriptor.format == GPUTextureFormat.Depth24PlusStencil8
            }
            assertEquals(8u, depthStencil.size.width)
            assertEquals(8u, depthStencil.size.height)
            assertEquals(1u, depthStencil.sampleCount)
            assertEquals(GPUTextureUsage.RenderAttachment, depthStencil.usage)
        } finally {
            draft?.let { assertTrue(it.disposeBeforeRegistration()) }
            materializer.close()
            fixture.close()
        }
    }

    @Test
    fun `W4d zero-width stroke-and-fill materializes direct without D24S8 or legacy stroke ABI`() {
        val fixture = W4dExecutionFixture.nativeMaterializationFixture(
            W4dExecutionFixture.directOnlyFramePlan(),
        )
        val materializer = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            requireNotNull(W4dExecutionFixture.capabilities().limits),
        )
        var draft: GPUPreparedNativeFrameDraft? = null
        try {
            val draw = fixture.scratch.draws.single()
            assertTrue(draw.isStroke)
            assertEquals(PathStrokeDrawMode.StrokeAndFill, draw.mode)
            assertEquals(
                0.0,
                assertIs<PathStrokeWidthF64.Finite>(requireNotNull(draw.styleF64).widthF64).valueF64,
            )

            val result = materializer.materializeReusable(
                fixture.frame,
                fixture.prepared.encoderPlan,
                fixture.prepared.resources,
                fixture.prepared.generationSeal,
            )
            draft = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(result).draft
            val render = draft.payload.scopeOperands.filterIsInstance<GPUPreparedNativeScopeOperand.Render>().single()
            assertNull(render.pass.depthStencilTarget)
            assertTrue(fixture.native.textureDescriptors.none { descriptor ->
                descriptor.format == GPUTextureFormat.Depth24PlusStencil8
            })
            val semantic = assertIs<
                org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload.CorePrimitive
            >(render.semanticPayloads.single())
            assertNull(
                assertIs<
                    org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry.TriangulatedPath
                >(semantic.geometry).strokeStyle,
            )
        } finally {
            draft?.let { assertTrue(it.disposeBeforeRegistration()) }
            materializer.close()
            fixture.close()
        }
    }

    @Test
    fun `W4d upload failure rolls the pool lease back before any native frame escapes`() {
        val fixture = W4dExecutionFixture.nativeMaterializationFixture()
        fixture.native.fail("writeBuffer", 2)
        val first = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            requireNotNull(W4dExecutionFixture.capabilities().limits),
        )
        val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(
            first.materializeReusable(
                fixture.frame,
                fixture.prepared.encoderPlan,
                fixture.prepared.resources,
                fixture.prepared.generationSeal,
            ),
        )
        assertEquals("failed.native-core-primitive.w4d-materialization", refused.code)
        val firstVertexBuffer = fixture.native.writeBufferCalls.single { call ->
            call.bufferLabel == "Kanvas.session.corePrimitive.framePool.vertices"
        }.buffer
        first.close()

        fixture.native.writeBufferCalls.clear()
        val retry = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            requireNotNull(W4dExecutionFixture.capabilities().limits),
        )
        var retryDraft: GPUPreparedNativeFrameDraft? = null
        try {
            val materialized = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
                retry.materializeReusable(
                    fixture.frame,
                    fixture.prepared.encoderPlan,
                    fixture.prepared.resources,
                    fixture.prepared.generationSeal,
                ),
            )
            retryDraft = materialized.draft
            val retriedVertexBuffer = fixture.native.writeBufferCalls.single { call ->
                call.bufferLabel == "Kanvas.session.corePrimitive.framePool.vertices"
            }.buffer
            assertSame(firstVertexBuffer, retriedVertexBuffer)
        } finally {
            retryDraft?.let { assertTrue(it.disposeBeforeRegistration()) }
            retry.close()
            fixture.close()
        }
    }

    @Test
    fun `W4d pooled lease remains owned until GPU completion and readback finalize`() {
        val fixture = W4dExecutionFixture.nativeMaterializationFixture()
        val materializer = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            requireNotNull(W4dExecutionFixture.capabilities().limits),
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
            var completionSink: GPUQueueCompletionSink? = null
            val completion = object : GPUQueueCompletionAccess {
                override fun reserveTicket(request: GPUQueueCompletionTicketRequest) =
                    GPUQueueCompletionTicketReservation.Missing

                override fun abandonReservedTicket(ticket: GPUQueueCompletionTicket) =
                    GPUQueueCompletionTicketAbandonResult.NotReserved(ticket.ticketId)

                override fun armAfterSubmit(
                    ticket: GPUQueueCompletionTicket,
                    sink: GPUQueueCompletionSink,
                ): GPUQueueCompletionArmResult {
                    completionSink = sink
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

                        override fun finish() = GPUFrameCommandBuffer("w4d.native.execution")

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
            assertEquals(1, adapter.activePreparedNativeFramePayloadCount)
            requireNotNull(completionSink).accept(
                GPUQueueCompletionDelivery.Accepted(
                    fixture.prepared.completionTicket.ticketId,
                    GPUQueueCompletionOutcome.Success,
                ),
            )
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

    private fun expectedVertexBytes(scratch: W4dSessionScratchV1): ByteArray = ArrayBuffer.of(
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

    private fun expectedIndexBytes(scratch: W4dSessionScratchV1): ByteArray = ArrayBuffer.of(
        scratch.draws.flatMap { draw ->
            val geometry = draw.copyGeometryF32()
            geometry.copyDirectTriangleF32OrNull()?.copyIndicesI32()?.asList() ?: buildList {
                addAll(requireNotNull(geometry.copyStencilEdgeFanF32OrNull()).copyIndicesI32().asList())
                addAll(listOf(0, 2, 1, 0, 3, 2))
            }
        }.toIntArray(),
    ).toByteArray()

    private fun expectedUniformBytes(
        scratch: W4dSessionScratchV1,
        frame: org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan,
    ): ByteArray {
        val packets = frame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            .flatMap(GPUFrameStep.RenderPassStep::drawPackets)
        return ByteArray(scratch.uniformPlan.totalBytes.toInt()).also { packed ->
            scratch.draws.forEach { draw ->
                val semantic = assertIs<
                    org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload.CorePrimitive
                >(packets.first { packet -> packet.commandIdValue == draw.commandId }.semanticPayload)
                semantic.payloadRef.uniformBlock!!.bytes.forEachIndexed { index, value ->
                    val offset = scratch.uniformPlan.slots[draw.uniformSlotIndex].alignedOffset.toInt()
                    packed[offset + index] = value.toByte()
                }
            }
        }
    }

    private fun GPUFramePlan.copyForW4dExecutionTest(
        capabilitySeal: GPUFrameCapabilitySeal = this.capabilitySeal,
        steps: List<GPUFrameStep> = this.steps,
    ): GPUFramePlan = GPUFramePlan(
        frameId = frameId,
        capabilitySeal = capabilitySeal,
        recordingSeals = recordingSeals,
        steps = steps,
        memoryBudget = memoryBudget,
        diagnostics = diagnostics,
        dependencies = dependencies,
        phaseOrder = phaseOrder,
        elidedNoOpDraws = elidedNoOpDraws,
        atomicallyRefused = atomicallyRefused,
    )
}

internal object W4dExecutionFixture {
    private val generation = GPUDeviceGenerationID(7)
    private val color = ColorARGB.fromPackedUInt(0x80ff0000u)

    fun framePlan() = GPUFramePlanner.plan(taskList())

    fun directOnlyFramePlan() = GPUFramePlanner.plan(taskList(directOnly = true))

    class NativeMaterializationFixture internal constructor(
        val frame: org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan,
        val prepared: PreparedGPUFrame,
        val scratch: W4dSessionScratchV1,
        val native: GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy,
        val target: GPUWgpu4kPreparedSceneTarget,
        val cache: GPUWgpu4kCorePrimitiveSessionCache,
    ) {
        fun close() {
            cache.close()
            target.close()
            if (prepared.claimForRollback()) check(prepared.rollback.execute().successful)
        }
    }

    fun nativeMaterializationFixture(
        frame: org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan = framePlan(),
    ): NativeMaterializationFixture {
        val prepared = assertIs<GPUFramePreflightResult.Prepared>(preflight(frame)).frame
        val scratch = assertNotNull(
            frame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
                .first().drawPackets.single().corePrimitivePreparedAuthority?.w4dSessionScratch,
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
                            GPUQueueCompletionTicketID("ticket.w4d.preflight"),
                            request.frameId,
                            request.deviceGeneration,
                        ),
                    )

                override fun abandonReservedTicket(ticket: GPUQueueCompletionTicket) =
                    GPUQueueCompletionTicketAbandonResult.Abandoned(ticket.ticketId)
            },
            surfaceProvider = object : GPUSurfaceOutputProvider {
                override fun acquire(request: GPUSurfaceAcquisitionRequest): GPUSurfaceAcquisitionResult =
                    error("W4d offscreen preflight must not acquire a surface")

                override fun release(output: GPUAcquiredSurfaceOutput) = GPUSurfaceReleaseResult.Released
            },
        ).preflight(frame)
    }

    fun capabilities(): GPUCapabilities = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "test", "w4d", "device"),
        facts = emptyList(),
        snapshotId = "w4d-execution",
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

    private fun taskList(directOnly: Boolean = false): org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList {
        val graph = graph(directOnly)
        return assertIs<GpuPlanLoweringResult.Lowered>(
            GpuPlanTaskListLowerer().lower(
            GpuPlanLoweringRequest(
                graph = graph,
                capabilities = capabilities(),
                deviceGeneration = generation,
                currentBudget = PlanBudget(1L shl 20),
                frameId = GPUFrameID(9),
                recordingId = GPURecordingID("w4d-execution"),
            ),
            ),
        ).taskList
    }

    private fun graph(directOnly: Boolean): RenderGraph {
        val scene = SceneSnapshot.of(
            SceneExtent(8, 8),
            ColorSpace.SRGB,
            if (directOnly) {
                listOf(pathDraw(triangle(), PaintStyleNode.STROKE_AND_FILL, width = 0f))
            } else {
                listOf(
                    pathDraw(triangle(), PaintStyleNode.FILL),
                    pathDraw(concaveEvenOdd(), PaintStyleNode.FILL),
                    pathDraw(openStroke(), PaintStyleNode.STROKE, width = 2f),
                )
            },
        )
        val compiler = W4dPathStrokePlanCompiler()
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

    private fun triangle(): PathF32 = PathBuilder()
        .moveTo(0f, 0f)
        .lineTo(4f, 0f)
        .lineTo(0f, 3f)
        .close()
        .build()

    private fun concaveEvenOdd(): PathF32 = PathBuilder(FillRule.EVEN_ODD)
        .moveTo(0f, 0f)
        .lineTo(6f, 0f)
        .lineTo(6f, 6f)
        .lineTo(3f, 2f)
        .lineTo(0f, 6f)
        .close()
        .build()

    private fun openStroke(): PathF32 = PathBuilder()
        .moveTo(1f, 1f)
        .lineTo(7f, 1f)
        .lineTo(7f, 7f)
        .build()

    private fun pathDraw(
        path: PathF32,
        style: PaintStyleNode,
        width: Float = 0f,
    ): SceneCommand.Draw = SceneCommand.Draw(
        DrawNode(
            geometry = GeometryNode.Path(path),
            material = MaterialNode.Solid(color),
            coverage = CoverageRequest.HARD_EDGE,
            clip = ClipStackNode.Empty,
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
                style,
                width,
                StrokeCapNode.BUTT,
                StrokeJoinNode.MITER,
                4f,
                false,
            ),
        ),
    )
}
