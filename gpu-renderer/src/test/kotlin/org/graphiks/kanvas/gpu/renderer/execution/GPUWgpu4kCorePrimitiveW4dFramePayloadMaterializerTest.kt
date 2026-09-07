package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureView
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
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
import org.graphiks.kanvas.gpu.plan.PlanResourceId
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
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.passes.W4dSessionScratchV1
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitivePreparedPacketAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanLoweringRequest
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanLoweringResult
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanTaskListLowerer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameCapabilitySeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameRenderBatch
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskDependency
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUSceneTarget
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.state.GPUPathSourceAuthority
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
import org.graphiks.math.geometry.PathF32
import org.graphiks.math.geometry.PathStrokeDrawMode
import org.graphiks.math.geometry.PathStrokeWidthF64
import org.graphiks.math.matrix.Matrix3x3F32

private typealias W4dNativeFailureTarget =
    GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy.SemanticFailureTarget
private typealias W4dBufferCreationFailure =
    GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy.SemanticFailureTarget.BufferCreation
private typealias W4dTextureCreationFailure =
    GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy.SemanticFailureTarget.TextureCreation
private typealias W4dTextureViewCreationFailure =
    GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy.SemanticFailureTarget.TextureViewCreation
private typealias W4dBindGroupCreationFailure =
    GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy.SemanticFailureTarget.BindGroupCreation
private typealias W4dBufferUploadFailure =
    GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy.SemanticFailureTarget.BufferUpload

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
    fun `W4d preflight rejects forged plan scratch packet and resource identities`() {
        val frame = W4dExecutionFixture.framePlan()
        val scratch = frame.w4dScratch()
        val cover = frame.w4dPacket(GPUDrawPacketRole.PathStencilCover)
        val coverTask = frame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            .first { render -> render.drawPackets.single() === cover }
            .sourceTaskIds.single()
        val mutations = listOf(
            "stale plan id" to frame.withW4dScratch(
                scratch.copyForW4dTest(planId = "0".repeat(64)),
            ),
            "foreign vertex resource" to frame.withW4dScratch(
                scratch.copyForW4dTest(vertexResourceId = PlanResourceId("VertexData:99")),
            ),
            "foreign index resource" to frame.withW4dScratch(
                scratch.copyForW4dTest(indexResourceId = PlanResourceId("IndexData:99")),
            ),
            "foreign uniform resource" to frame.withW4dScratch(
                scratch.copyForW4dTest(uniformResourceId = PlanResourceId("UniformData:99")),
            ),
            "foreign D24S8 resource" to frame.withW4dScratch(
                scratch.copyForW4dTest(depthStencilResourceId = PlanResourceId("DepthStencil:99")),
            ),
            "forged D24S8 bytes" to frame.withW4dScratch(
                scratch.copyForW4dTest(depthStencilBytes = scratch.depthStencilBytes + 4L),
            ),
            "forged D24S8 first use" to frame.withW4dScratch(
                scratch.copyForW4dTest(depthStencilFirstPassIndex = 0),
            ),
            "forged target bytes" to frame.withW4dScratch(
                scratch.copyForW4dTest(targetBytes = scratch.targetBytes + 4L),
            ),
            "forged readback bytes" to frame.withW4dScratch(
                scratch.copyForW4dTest(stagingBytes = scratch.stagingBytes + 256L),
            ),
            "forged device buffer limit" to frame.withW4dScratch(
                scratch.copyForW4dTest(maxBufferSize = scratch.maxBufferSize + 1L),
            ),
            "forged packet id" to frame.withW4dScratch(
                scratch,
                packetIdFor = { packet ->
                    if (packet === cover) GPUDrawPacketID("packet.w4d.forged.cover") else packet.packetId
                },
            ),
            "forged pass id" to frame.withW4dScratch(
                scratch,
                passIdFor = { packet ->
                    if (packet === cover) "StencilCover:99" else packet.passId
                },
            ),
            "forged task id" to frame.withW4dTaskIdReplaced(
                coverTask,
                GPUTaskID("task.w4d.forged.cover"),
            ),
        )

        mutations.forEach { (label, malformed) ->
            assertEquals(
                "invalid.preflight.w4d_session_scratch",
                w4dPreflightCode(malformed),
                label,
            )
        }
    }

    @Test
    fun `W4d preflight rejects forged D24S8 usage lifetime and state facts`() {
        val frame = W4dExecutionFixture.framePlan()
        val producerIndex = frame.steps.indexOfFirst { step ->
            (step as? GPUFrameStep.RenderPassStep)?.drawPackets?.singleOrNull()?.role ==
                GPUDrawPacketRole.PathStencilProducer
        }
        val producer = assertIs<GPUFrameStep.RenderPassStep>(frame.steps[producerIndex])
        val depthUse = producer.resourceUses.single()
        fun malformed(use: GPUFrameResourceUse = depthUse, samplePlan: GPUSamplePlan = producer.samplePlan) =
            frame.withW4dRenderStep(
                producerIndex,
                producer,
                resourceUses = listOf(use),
                samplePlan = samplePlan,
            )
        val mutations = listOf(
            "resource" to malformed(depthUse.copy(resource = GPUFrameTextureRef("w4d.forged.depth"))),
            "role" to malformed(depthUse.copy(role = GPUFrameResourceRole.ClipDepthStencil)),
            "usage" to malformed(depthUse.copy(usage = GPUFrameResourceUsage.TextureBinding)),
            "lifetime" to malformed(depthUse.copy(lifetime = GPUFrameResourceLifetime.PassLocal)),
            "write state" to malformed(depthUse.copy(write = false)),
            "sample state" to malformed(samplePlan = GPUSamplePlan.MultisampleFrame(4)),
        )

        mutations.forEach { (label, malformed) ->
            assertEquals(
                "invalid.preflight.w4d_session_scratch",
                w4dPreflightCode(malformed),
                label,
            )
        }
    }

    @Test
    fun `W4d preflight rejects forged source authority payload and packet state`() {
        val frame = W4dExecutionFixture.framePlan()
        val direct = frame.w4dPacket(GPUDrawPacketRole.Shading)
        val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(direct.semanticPayload)
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        val foreignGeometry = geometry.copyForW4dTest(GPUPathSourceAuthority.Unknown)
        val foreignAuthoritySemantic = semantic.copyForW4dTest(geometry = foreignGeometry)
        val forgedPayload = semantic.copyForW4dTest(
            premultipliedRgba = listOf(0f, 0f, 0f, 0f),
        )
        val mutations = listOf(
            "source authority" to frame.withW4dPacket(
                direct,
                direct.copyForW4dTest(semanticPayload = foreignAuthoritySemantic),
            ),
            "payload integrity" to frame.withW4dPacket(
                direct,
                direct.copyForW4dTest(semanticPayload = forgedPayload),
            ),
            "packet role" to frame.withW4dPacket(
                direct,
                direct.copyForW4dTest(role = GPUDrawPacketRole.PathStencilCover),
            ),
        )

        mutations.forEach { (label, malformed) ->
            assertEquals(
                "invalid.preflight.w4d_session_scratch",
                w4dPreflightCode(malformed),
                label,
            )
        }
    }

    @Test
    fun `W4d native byte gate rejects undersize I64 and I32 overflow independently`() {
        GPUPlannedPathNativeByteRange.Resource.entries.forEach { resource ->
            assertSame(
                GPUPlannedPathNativeByteRangeValidation.Accepted,
                validatePlannedPathNativeByteRanges(
                    listOf(GPUPlannedPathNativeByteRange(resource, 0L, 8L, 8L, 1L)),
                ),
                resource.name,
            )
            val undersized = assertIs<GPUPlannedPathNativeByteRangeValidation.Refused>(
                validatePlannedPathNativeByteRanges(
                    listOf(GPUPlannedPathNativeByteRange(resource, 0L, 8L, 7L, 1L)),
                ),
                resource.name,
            )
            assertEquals(resource, undersized.resource)
            assertEquals(
                GPUPlannedPathNativeByteRangeValidation.Reason.CapacityUndersized,
                undersized.reason,
            )
            val i64Overflow = assertIs<GPUPlannedPathNativeByteRangeValidation.Refused>(
                validatePlannedPathNativeByteRanges(
                    listOf(
                        GPUPlannedPathNativeByteRange(
                            resource,
                            Long.MAX_VALUE,
                            1L,
                            Long.MAX_VALUE,
                            1L,
                        ),
                    ),
                ),
                resource.name,
            )
            assertEquals(resource, i64Overflow.resource)
            assertEquals(GPUPlannedPathNativeByteRangeValidation.Reason.I64Overflow, i64Overflow.reason)
            val i32Overflow = assertIs<GPUPlannedPathNativeByteRangeValidation.Refused>(
                validatePlannedPathNativeByteRanges(
                    listOf(
                        GPUPlannedPathNativeByteRange(
                            resource,
                            Int.MAX_VALUE.toLong(),
                            1L,
                            Long.MAX_VALUE,
                            1L,
                        ),
                    ),
                ),
                resource.name,
            )
            assertEquals(resource, i32Overflow.resource)
            assertEquals(GPUPlannedPathNativeByteRangeValidation.Reason.I32HostOverflow, i32Overflow.reason)
        }

        val offsetMisaligned = assertIs<GPUPlannedPathNativeByteRangeValidation.Refused>(
            validatePlannedPathNativeByteRanges(
                listOf(
                    GPUPlannedPathNativeByteRange(
                        GPUPlannedPathNativeByteRange.Resource.Vertex,
                        1L,
                        4L,
                        8L,
                        4L,
                    ),
                ),
            ),
        )
        assertEquals(GPUPlannedPathNativeByteRangeValidation.Reason.Misaligned, offsetMisaligned.reason)
        val rangeMisaligned = assertIs<GPUPlannedPathNativeByteRangeValidation.Refused>(
            validatePlannedPathNativeByteRanges(
                listOf(
                    GPUPlannedPathNativeByteRange(
                        GPUPlannedPathNativeByteRange.Resource.Index,
                        0L,
                        6L,
                        8L,
                        4L,
                    ),
                ),
            ),
        )
        assertEquals(GPUPlannedPathNativeByteRangeValidation.Reason.Misaligned, rangeMisaligned.reason)
    }

    @Test
    fun `W4d completion-ticket refusal leaves resources reusable by the next preflight`() {
        val frame = W4dExecutionFixture.framePlan()
        val refusals = listOf<Pair<String, (GPUQueueCompletionTicketRequest) -> GPUQueueCompletionTicketReservation>>(
            "missing" to { GPUQueueCompletionTicketReservation.Missing },
            "failed" to { GPUQueueCompletionTicketReservation.Failed(testDiagnostic("failed.w4d.ticket")) },
            "duplicate" to { request ->
                GPUQueueCompletionTicketReservation.Duplicate(
                    GPUQueueCompletionTicketID("ticket.w4d.duplicate.${request.frameId.value}"),
                )
            },
        )
        refusals.forEach { (label, reservation) ->
            val resources = GPUConcreteResourceProvider()
            val refusedProvider = object : GPUQueueCompletionProvider {
                override fun reserveTicket(request: GPUQueueCompletionTicketRequest) = reservation(request)

                override fun abandonReservedTicket(ticket: GPUQueueCompletionTicket) =
                    GPUQueueCompletionTicketAbandonResult.Abandoned(ticket.ticketId)
            }
            assertIs<GPUFramePreflightResult.Refused>(
                W4dExecutionFixture.preflight(frame, resources, refusedProvider),
                label,
            )
            val retry = assertIs<GPUFramePreflightResult.Prepared>(
                W4dExecutionFixture.preflight(frame, resources),
                label,
            ).frame
            assertTrue(retry.claimForRollback(), label)
            assertTrue(retry.rollback.execute().successful, label)
        }
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
    fun `W4d stencil-only materialization preserves exact scoped draws and pass associations`() {
        val fixture = W4dExecutionFixture.nativeMaterializationFixture(
            W4dExecutionFixture.stencilOnlyFramePlan(),
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
            val materialized = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
                materializer.materializeReusable(
                    fixture.frame,
                    fixture.prepared.encoderPlan,
                    fixture.prepared.resources,
                    fixture.prepared.generationSeal,
                ),
            )
            draft = materialized.draft
            val renders = draft.payload.scopeOperands
                .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
            assertEquals(2, renders.size)
            val draw = fixture.scratch.draws.single()
            val fan = requireNotNull(draw.copyGeometryF32().copyStencilEdgeFanF32OrNull())
            val producerDraw = renders[0].commands
                .filterIsInstance<GPUPreparedNativeRenderCommand.DrawIndexed>().single().drawCall
            val coverDraw = renders[1].commands
                .filterIsInstance<GPUPreparedNativeRenderCommand.DrawIndexed>().single().drawCall
            assertEquals(
                listOf(
                    GPUPreparedNativeDrawCall.DrawIndexed(
                        indexCount = fan.indexCountI32,
                        firstIndex = (draw.indexOffsetBytes / Int.SIZE_BYTES).toInt(),
                        baseVertex = (draw.vertexOffsetBytes / (2L * Float.SIZE_BYTES)).toInt(),
                        vertexCount = fan.vertexCountI32,
                        maxLocalIndex = fan.vertexCountI32 - 1,
                    ),
                    GPUPreparedNativeDrawCall.DrawIndexed(
                        indexCount = 6,
                        firstIndex = ((draw.indexOffsetBytes / Int.SIZE_BYTES) + fan.indexCountI32).toInt(),
                        baseVertex = ((draw.vertexOffsetBytes / (2L * Float.SIZE_BYTES)) +
                            fan.vertexCountI32).toInt(),
                        vertexCount = 4,
                        maxLocalIndex = 3,
                    ),
                ),
                listOf(producerDraw, coverDraw),
            )
            val scissor = draw.copyScissorBounds()
            assertEquals(
                List(2) { listOf(scissor.left, scissor.top, scissor.width, scissor.height) },
                renders.map { render ->
                    render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetScissor>()
                        .single().let { listOf(it.x, it.y, it.width, it.height) }
                },
            )
            assertEquals(
                listOf(GPUPreparedNativeLoadOperation.Clear, GPUPreparedNativeLoadOperation.Load),
                renders.map { it.pass.loadOperation },
            )
            assertEquals(
                listOf(GPUPreparedNativeLoadOperation.Clear, GPUPreparedNativeLoadOperation.Load),
                renders.map { it.pass.stencilLoadOperation },
            )
            assertTrue(renders.all { it.pass.storeOperation == GPUPreparedNativeStoreOperation.Store })
            assertTrue(renders.all { it.pass.stencilStoreOperation == GPUPreparedNativeStoreOperation.Store })
            assertSame(
                requireNotNull(renders[0].pass.depthStencilTarget).view,
                requireNotNull(renders[1].pass.depthStencilTarget).view,
            )
            assertEquals(
                listOf(
                    "Kanvas.session.corePrimitive.pipeline.PathStencilProducerWinding",
                    "Kanvas.session.corePrimitive.pipeline.PathStencilCoverRegular",
                ),
                fixture.native.renderPipelineDescriptors.map { it.label },
            )
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
        fixture.native.refuse(
            W4dBufferUploadFailure(
                "Kanvas.session.corePrimitive.framePool.indices",
            ),
        )
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
    fun `W4d acquisition upload and staging failures are transactional at every boundary`() {
        data class FailureScenario(
            val target: W4dNativeFailureTarget,
            val publishedPoolSlot: Boolean,
            val refusalCode: String = if (publishedPoolSlot) {
                "failed.native-core-primitive.w4d-materialization"
            } else {
                "failed.native-core-primitive.frame-pool-allocation"
            },
        )
        val scenarios = listOf(
            FailureScenario(
                W4dBufferCreationFailure(
                    "Kanvas.session.corePrimitive.framePool.vertices",
                ),
                false,
            ),
            FailureScenario(
                W4dBufferCreationFailure(
                    "Kanvas.session.corePrimitive.framePool.indices",
                ),
                false,
            ),
            FailureScenario(
                W4dBufferCreationFailure(
                    "Kanvas.session.corePrimitive.framePool.uniforms",
                ),
                false,
            ),
            FailureScenario(
                W4dTextureCreationFailure(
                    "Kanvas.session.corePrimitive.framePool.pathDepthStencil",
                ),
                false,
            ),
            FailureScenario(
                W4dTextureViewCreationFailure(
                    "Kanvas.session.corePrimitive.framePool.pathDepthStencil.view",
                ),
                false,
            ),
            FailureScenario(
                W4dBindGroupCreationFailure(
                    "Kanvas.session.corePrimitive.framePool.bindGroup0",
                ),
                false,
            ),
            FailureScenario(
                W4dBufferUploadFailure(
                    "Kanvas.session.corePrimitive.framePool.vertices",
                ),
                true,
            ),
            FailureScenario(
                W4dBufferUploadFailure(
                    "Kanvas.session.corePrimitive.framePool.indices",
                ),
                true,
            ),
            FailureScenario(
                W4dBufferUploadFailure(
                    "Kanvas.session.corePrimitive.framePool.uniforms",
                ),
                true,
            ),
            FailureScenario(
                W4dBufferCreationFailure(
                    "Kanvas.frame.w4d.readback",
                ),
                true,
            ),
        )

        scenarios.forEach { scenario ->
            val fixture = W4dExecutionFixture.nativeMaterializationFixture()
            fixture.native.refuse(scenario.target)
            val first = fixture.materializer()
            var retryDraft: GPUPreparedNativeFrameDraft? = null
            val label = scenario.target.toString()
            try {
                val firstResult = first.materializeReusable(
                    fixture.frame,
                    fixture.prepared.encoderPlan,
                    fixture.prepared.resources,
                    fixture.prepared.generationSeal,
                )
                (firstResult as? GPUPreparedNativeFramePayloadMaterialization.Materialized)
                    ?.draft?.disposeBeforeRegistration()
                val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(
                    firstResult,
                    "$label; observed=${fixture.native.bufferDescriptors.map { it.label }}",
                )
                assertEquals(scenario.refusalCode, refused.code, label)
                first.close()
                val firstVertex = fixture.native.createdHandles(
                    "Kanvas.session.corePrimitive.framePool.vertices",
                ).firstOrNull()
                if (firstVertex != null && !scenario.publishedPoolSlot) {
                    assertEquals(1, fixture.native.closeCounts.getOrDefault(firstVertex, 0), label)
                }

                fixture.native.writeBufferCalls.clear()
                val retry = fixture.materializer()
                try {
                    retryDraft = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
                        retry.materializeReusable(
                            fixture.frame,
                            fixture.prepared.encoderPlan,
                            fixture.prepared.resources,
                            fixture.prepared.generationSeal,
                        ),
                        label,
                    ).draft
                    val retriedVertex = fixture.native.writeBufferCalls.single { call ->
                        call.bufferLabel == "Kanvas.session.corePrimitive.framePool.vertices"
                    }.buffer
                    if (firstVertex != null) {
                        if (scenario.publishedPoolSlot) {
                            assertTrue(firstVertex === retriedVertex, label)
                        } else {
                            assertFalse(firstVertex === retriedVertex, label)
                        }
                    }
                } finally {
                    retryDraft?.let { assertTrue(it.disposeBeforeRegistration(), label) }
                    retry.close()
                }
            } finally {
                first.close()
                fixture.close()
            }
        }
    }

    @Test
    fun `W4d post-checkout render-run refusal returns the exact pooled slot`() {
        val fixture = W4dExecutionFixture.nativeMaterializationFixture()
        val coverIndex = fixture.frame.steps.indexOfFirst { step ->
            (step as? GPUFrameStep.RenderPassStep)?.drawPackets?.singleOrNull()?.role ==
                GPUDrawPacketRole.PathStencilCover
        }
        val cover = assertIs<GPUFrameStep.RenderPassStep>(fixture.frame.steps[coverIndex])
        val malformed = fixture.frame.withW4dRenderStep(
            coverIndex,
            cover,
            samplePlan = GPUSamplePlan.MultisampleFrame(4),
        )
        val first = fixture.materializer()
        var retriedDraft: GPUPreparedNativeFrameDraft? = null
        try {
            val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(
                first.materializeReusable(
                    malformed,
                    fixture.prepared.encoderPlan,
                    fixture.prepared.resources,
                    fixture.prepared.generationSeal,
                ),
            )
            assertEquals("invalid.native-core-primitive.w4d-render-run", refused.code)
            val firstVertex = fixture.native.writeBufferCalls.single { call ->
                call.bufferLabel == "Kanvas.session.corePrimitive.framePool.vertices"
            }.buffer
            first.close()

            fixture.native.writeBufferCalls.clear()
            val retry = fixture.materializer()
            try {
                retriedDraft = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
                    retry.materializeReusable(
                        fixture.frame,
                        fixture.prepared.encoderPlan,
                        fixture.prepared.resources,
                        fixture.prepared.generationSeal,
                    ),
                ).draft
                val retriedVertex = fixture.native.writeBufferCalls.single { call ->
                    call.bufferLabel == "Kanvas.session.corePrimitive.framePool.vertices"
                }.buffer
                assertSame(firstVertex, retriedVertex)
            } finally {
                retriedDraft?.let { assertTrue(it.disposeBeforeRegistration()) }
                retry.close()
            }
        } finally {
            first.close()
            fixture.close()
        }
    }

    @Test
    fun `W4d duplicate registration preserves the first owner until its explicit rollback`() {
        val fixture = W4dExecutionFixture.nativeMaterializationFixture()
        val materializer = fixture.materializer()
        val adapter = GPURuntimeResourceAdapter()
        var competing: GPUWgpu4kCorePrimitiveFramePoolLease? = null
        var recovered: GPUWgpu4kCorePrimitiveFramePoolLease? = null
        try {
            val draft = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
                materializer.materializeReusable(
                    fixture.frame,
                    fixture.prepared.encoderPlan,
                    fixture.prepared.resources,
                    fixture.prepared.generationSeal,
                ),
            ).draft
            val originalVertex = fixture.native.writeBufferCalls.single { call ->
                call.bufferLabel == "Kanvas.session.corePrimitive.framePool.vertices"
            }.buffer
            val originalDepthStencil = requireNotNull(
                draft.payload.scopeOperands.filterIsInstance<GPUPreparedNativeScopeOperand.Render>()[1]
                    .pass.depthStencilTarget,
            ).view
            val first = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
                adapter.registerPreparedNativeFrameDraft(draft),
            )

            val duplicate = assertIs<GPUPreparedNativeFrameRegistration.Refused>(
                adapter.registerPreparedNativeFrameDraft(draft),
            )

            assertEquals("invalid.native-frame-payload.draft-ownership", duplicate.code)
            assertEquals(
                GPUPreparedNativeFrameRegistration.RefusalOwnership.ReleasedOrAdapterQuarantined,
                duplicate.ownership,
            )
            assertEquals(1, adapter.activePreparedNativeFramePayloadCount)
            competing = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(
                fixture.cache.acquireFrame(fixture.exactPoolRequirements()),
            ).lease
            assertNotSame(originalVertex, competing.handles.vertexBuffer)
            assertNotSame(
                originalDepthStencil,
                requireNotNull(competing.handles.pathDepthStencil).view,
            )
            assertIs<GPUWgpu4kCorePrimitiveFramePoolLeaseTransition.Applied>(
                competing.rollbackBeforeSubmit(),
            )
            competing = null

            assertTrue(first.ownership.rollback())
            assertEquals(0, adapter.activePreparedNativeFramePayloadCount)
            recovered = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(
                fixture.cache.acquireFrame(fixture.exactPoolRequirements()),
            ).lease
            assertSame(originalVertex, recovered.handles.vertexBuffer)
            assertSame(
                originalDepthStencil,
                requireNotNull(recovered.handles.pathDepthStencil).view,
            )
        } finally {
            recovered?.rollbackBeforeSubmit()
            competing?.rollbackBeforeSubmit()
            adapter.close()
            materializer.close()
            fixture.close()
        }
    }

    @Test
    fun `W4d executor pre-submit failures rollback the exact native pool slot`() {
        listOf(
            W4dBackendFailureStage.CreateEncoder,
            W4dBackendFailureStage.Encode,
            W4dBackendFailureStage.Finish,
        ).forEach { failureStage ->
            val fixture = W4dExecutionFixture.nativeMaterializationFixture()
            val adapter = GPURuntimeResourceAdapter()
            val registered = fixture.registerNativeFrame(adapter)
            var checkout: GPUWgpu4kCorePrimitiveFramePoolLease? = null
            try {
                val handle = GPUFrameExecutor(
                    fixture.sceneTarget(),
                    fixture.backend(failureStage),
                    HeldCompletionAccess(),
                    noOpRetention(),
                    HeldReadbackAccess(),
                ).execute(fixture.prepared)
                assertIs<GPUFrameImmediateState.FailedBeforeSubmit>(handle.immediateState, failureStage.name)
                assertEquals(
                    GPUFrameStructuralOutcome.Failed,
                    handle.completion.toCompletableFuture().join().outcome,
                    failureStage.name,
                )
                assertEquals(0, adapter.activePreparedNativeFramePayloadCount, failureStage.name)
                checkout = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(
                    fixture.cache.acquireFrame(fixture.exactPoolRequirements()),
                    failureStage.name,
                ).lease
                assertSame(registered.originalVertex, checkout.handles.vertexBuffer, failureStage.name)
                assertSame(
                    registered.originalDepthStencil,
                    requireNotNull(checkout.handles.pathDepthStencil).view,
                    failureStage.name,
                )
            } finally {
                checkout?.rollbackBeforeSubmit()
                adapter.close()
                registered.materializer.close()
                fixture.close()
            }
        }
    }

    @Test
    fun `W4d synchronous submit failure quarantines the exact native pool slot`() {
        val fixture = W4dExecutionFixture.nativeMaterializationFixture()
        val adapter = GPURuntimeResourceAdapter()
        val registered = fixture.registerNativeFrame(adapter)
        var firstCheckout: GPUWgpu4kCorePrimitiveFramePoolLease? = null
        var secondCheckout: GPUWgpu4kCorePrimitiveFramePoolLease? = null
        try {
            val handle = GPUFrameExecutor(
                fixture.sceneTarget(),
                fixture.backend(W4dBackendFailureStage.Submit),
                HeldCompletionAccess(),
                noOpRetention(),
                HeldReadbackAccess(),
            ).execute(fixture.prepared)

            assertIs<GPUFrameImmediateState.FailedAfterSubmit>(handle.immediateState)
            assertEquals(
                GPUFrameStructuralOutcome.Failed,
                handle.completion.toCompletableFuture().join().outcome,
            )
            assertEquals(0, adapter.activePreparedNativeFramePayloadCount)
            assertEquals(1, adapter.quarantinedPreparedNativeFramePayloadCount)
            firstCheckout = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(
                fixture.cache.acquireFrame(fixture.exactPoolRequirements()),
            ).lease
            assertNotSame(registered.originalVertex, firstCheckout.handles.vertexBuffer)
            assertNotSame(
                registered.originalDepthStencil,
                requireNotNull(firstCheckout.handles.pathDepthStencil).view,
            )
            secondCheckout = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(
                fixture.cache.acquireFrame(fixture.exactPoolRequirements()),
            ).lease
            assertNotSame(registered.originalVertex, secondCheckout.handles.vertexBuffer)
            assertNotSame(
                registered.originalDepthStencil,
                requireNotNull(secondCheckout.handles.pathDepthStencil).view,
            )
        } finally {
            secondCheckout?.rollbackBeforeSubmit()
            firstCheckout?.rollbackBeforeSubmit()
            adapter.close()
            registered.materializer.close()
            fixture.close()
        }
    }

    @Test
    fun `W4d mark submitted refusal and throw quarantine the exact slot`() {
        W4dReadbackFailureMode.entries.forEach { failureMode ->
            val fixture = W4dExecutionFixture.nativeMaterializationFixture()
            val adapter = GPURuntimeResourceAdapter()
            val registered = fixture.registerNativeFrame(adapter)
            var checkout: GPUWgpu4kCorePrimitiveFramePoolLease? = null
            try {
                val handle = GPUFrameExecutor(
                    fixture.sceneTarget(),
                    fixture.backend(),
                    HeldCompletionAccess(),
                    noOpRetention(),
                    HeldReadbackAccess(W4dReadbackFailureStage.MarkSubmitted, failureMode),
                ).execute(fixture.prepared)

                assertIs<GPUFrameImmediateState.FailedAfterSubmit>(handle.immediateState, failureMode.name)
                assertEquals(
                    GPUFrameStructuralOutcome.Failed,
                    handle.completion.toCompletableFuture().join().outcome,
                    failureMode.name,
                )
                assertEquals(0, adapter.activePreparedNativeFramePayloadCount, failureMode.name)
                assertEquals(1, adapter.quarantinedPreparedNativeFramePayloadCount, failureMode.name)
                checkout = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(
                    fixture.cache.acquireFrame(fixture.exactPoolRequirements()),
                    failureMode.name,
                ).lease
                assertNotSame(registered.originalVertex, checkout.handles.vertexBuffer, failureMode.name)
                assertNotSame(
                    registered.originalDepthStencil,
                    requireNotNull(checkout.handles.pathDepthStencil).view,
                    failureMode.name,
                )
            } finally {
                checkout?.rollbackBeforeSubmit()
                adapter.close()
                registered.materializer.close()
                fixture.close()
            }
        }
    }

    @Test
    fun `W4d completion acceptance and map arm failures quarantine one exact slot`() {
        listOf(
            W4dReadbackFailureStage.AcceptGPUCompletion,
            W4dReadbackFailureStage.MapArm,
        ).forEach { failureStage ->
            W4dReadbackFailureMode.entries.forEach { failureMode ->
                val label = "${failureStage.name}.${failureMode.name}"
                val fixture = W4dExecutionFixture.nativeMaterializationFixture()
                val adapter = GPURuntimeResourceAdapter()
                val registered = fixture.registerNativeFrame(adapter)
                val completion = HeldCompletionAccess()
                var firstCheckout: GPUWgpu4kCorePrimitiveFramePoolLease? = null
                var secondCheckout: GPUWgpu4kCorePrimitiveFramePoolLease? = null
                try {
                    val handle = GPUFrameExecutor(
                        fixture.sceneTarget(),
                        fixture.backend(),
                        completion,
                        noOpRetention(),
                        HeldReadbackAccess(failureStage, failureMode),
                    ).execute(fixture.prepared)
                    assertIs<GPUFrameImmediateState.Submitted>(handle.immediateState, label)
                    val delivery = GPUQueueCompletionOutcome.Success
                    completion.deliver(fixture.prepared.completionTicket, delivery)

                    assertEquals(
                        GPUFrameStructuralOutcome.Failed,
                        handle.completion.toCompletableFuture().join().outcome,
                        label,
                    )
                    assertEquals(0, adapter.activePreparedNativeFramePayloadCount, label)
                    assertEquals(1, adapter.quarantinedPreparedNativeFramePayloadCount, label)
                    firstCheckout = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(
                        fixture.cache.acquireFrame(fixture.exactPoolRequirements()),
                        label,
                    ).lease
                    assertNotSame(registered.originalVertex, firstCheckout.handles.vertexBuffer, label)
                    assertNotSame(
                        registered.originalDepthStencil,
                        requireNotNull(firstCheckout.handles.pathDepthStencil).view,
                        label,
                    )

                    completion.deliver(fixture.prepared.completionTicket, delivery)
                    assertEquals(1, adapter.quarantinedPreparedNativeFramePayloadCount, label)
                    secondCheckout = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(
                        fixture.cache.acquireFrame(fixture.exactPoolRequirements()),
                        label,
                    ).lease
                    assertNotSame(registered.originalVertex, secondCheckout.handles.vertexBuffer, label)
                    assertNotSame(firstCheckout.handles.vertexBuffer, secondCheckout.handles.vertexBuffer, label)
                } finally {
                    secondCheckout?.rollbackBeforeSubmit()
                    firstCheckout?.rollbackBeforeSubmit()
                    adapter.close()
                    registered.materializer.close()
                    fixture.close()
                }
            }
        }
    }

    @Test
    fun `W4d finalize refusal and throw retain the exact slot during callback then quarantine it`() {
        W4dReadbackFailureMode.entries.forEach { failureMode ->
            val fixture = W4dExecutionFixture.nativeMaterializationFixture()
            val adapter = GPURuntimeResourceAdapter()
            val registered = fixture.registerNativeFrame(adapter)
            val completion = HeldCompletionAccess()
            var duringFinalize: GPUWgpu4kCorePrimitiveFramePoolLease? = null
            var afterFinalize: GPUWgpu4kCorePrimitiveFramePoolLease? = null
            val readback = HeldReadbackAccess(
                failureStage = W4dReadbackFailureStage.Finalize,
                failureMode = failureMode,
                duringFinalize = {
                    duringFinalize = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(
                        fixture.cache.acquireFrame(fixture.exactPoolRequirements()),
                    ).lease
                },
            )
            try {
                val handle = GPUFrameExecutor(
                    fixture.sceneTarget(),
                    fixture.backend(),
                    completion,
                    noOpRetention(),
                    readback,
                ).execute(fixture.prepared)
                val completionDelivery = GPUQueueCompletionOutcome.Success
                completion.deliver(fixture.prepared.completionTicket, completionDelivery)
                val pixels = GPUFrameReadbackMapDelivery.Pixels(
                    fixture.prepared.resources.outputOwnedReadbacks.single().request.requestId,
                    ByteArray(
                        fixture.scratch.targetBounds.width * fixture.scratch.targetBounds.height * 4,
                    ),
                )
                readback.deliver(pixels)

                assertEquals(
                    GPUFrameStructuralOutcome.Failed,
                    handle.completion.toCompletableFuture().join().outcome,
                    failureMode.name,
                )
                val competing = requireNotNull(duringFinalize)
                assertNotSame(registered.originalVertex, competing.handles.vertexBuffer, failureMode.name)
                assertNotSame(
                    registered.originalDepthStencil,
                    requireNotNull(competing.handles.pathDepthStencil).view,
                    failureMode.name,
                )
                assertEquals(1, adapter.quarantinedPreparedNativeFramePayloadCount, failureMode.name)

                readback.deliver(pixels)
                completion.deliver(fixture.prepared.completionTicket, completionDelivery)
                assertEquals(1, adapter.quarantinedPreparedNativeFramePayloadCount, failureMode.name)
                afterFinalize = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(
                    fixture.cache.acquireFrame(fixture.exactPoolRequirements()),
                    failureMode.name,
                ).lease
                assertNotSame(registered.originalVertex, afterFinalize.handles.vertexBuffer, failureMode.name)
                assertNotSame(competing.handles.vertexBuffer, afterFinalize.handles.vertexBuffer, failureMode.name)
            } finally {
                afterFinalize?.rollbackBeforeSubmit()
                duringFinalize?.rollbackBeforeSubmit()
                adapter.close()
                registered.materializer.close()
                fixture.close()
            }
        }
    }

    @Test
    fun `W4d queue failure and cancellation quarantine one lease despite duplicate delivery`() {
        listOf(
            GPUQueueCompletionFailureKind.DeviceLost,
            GPUQueueCompletionFailureKind.Cancelled,
        ).forEach { failureKind ->
            val fixture = W4dExecutionFixture.nativeMaterializationFixture()
            val adapter = GPURuntimeResourceAdapter()
            val registered = fixture.registerNativeFrame(adapter)
            val completion = HeldCompletionAccess()
            var firstCheckout: GPUWgpu4kCorePrimitiveFramePoolLease? = null
            var secondCheckout: GPUWgpu4kCorePrimitiveFramePoolLease? = null
            try {
                val handle = GPUFrameExecutor(
                    fixture.sceneTarget(),
                    fixture.backend(),
                    completion,
                    noOpRetention(),
                    HeldReadbackAccess(),
                ).execute(fixture.prepared)
                assertIs<GPUFrameImmediateState.Submitted>(handle.immediateState, failureKind.name)
                val outcome = GPUQueueCompletionOutcome.Failure(failureKind)
                completion.deliver(fixture.prepared.completionTicket, outcome)
                assertEquals(
                    GPUFrameStructuralOutcome.Failed,
                    handle.completion.toCompletableFuture().join().outcome,
                    failureKind.name,
                )
                assertEquals(0, adapter.activePreparedNativeFramePayloadCount, failureKind.name)
                assertEquals(1, adapter.quarantinedPreparedNativeFramePayloadCount, failureKind.name)
                firstCheckout = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(
                    fixture.cache.acquireFrame(fixture.exactPoolRequirements()),
                ).lease
                assertNotSame(registered.originalVertex, firstCheckout.handles.vertexBuffer, failureKind.name)

                completion.deliver(fixture.prepared.completionTicket, outcome)
                assertEquals(1, adapter.quarantinedPreparedNativeFramePayloadCount, failureKind.name)
                secondCheckout = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(
                    fixture.cache.acquireFrame(fixture.exactPoolRequirements()),
                ).lease
                assertNotSame(registered.originalVertex, secondCheckout.handles.vertexBuffer, failureKind.name)
                assertNotSame(firstCheckout.handles.vertexBuffer, secondCheckout.handles.vertexBuffer, failureKind.name)
            } finally {
                secondCheckout?.rollbackBeforeSubmit()
                firstCheckout?.rollbackBeforeSubmit()
                adapter.close()
                registered.materializer.close()
                fixture.close()
            }
        }
    }

    @Test
    fun `W4d asynchronous readback failure releases or quarantines one lease exactly once`() {
        listOf(
            GPUFrameReadbackMapFailureSafety.SafeToRelease to true,
            GPUFrameReadbackMapFailureSafety.Quarantine to false,
        ).forEach { (failureSafety, reusable) ->
            val fixture = W4dExecutionFixture.nativeMaterializationFixture()
            val adapter = GPURuntimeResourceAdapter()
            val registered = fixture.registerNativeFrame(adapter)
            val completion = HeldCompletionAccess()
            val readback = HeldReadbackAccess()
            var firstCheckout: GPUWgpu4kCorePrimitiveFramePoolLease? = null
            var secondCheckout: GPUWgpu4kCorePrimitiveFramePoolLease? = null
            try {
                val handle = GPUFrameExecutor(
                    fixture.sceneTarget(),
                    fixture.backend(),
                    completion,
                    noOpRetention(),
                    readback,
                ).execute(fixture.prepared)
                completion.deliver(fixture.prepared.completionTicket, GPUQueueCompletionOutcome.Success)
                val delivery = GPUFrameReadbackMapDelivery.Failed(
                    testDiagnostic("failed.w4d.readback.${failureSafety.name}"),
                    failureSafety,
                )
                readback.deliver(delivery)
                assertEquals(
                    GPUFrameStructuralOutcome.Failed,
                    handle.completion.toCompletableFuture().join().outcome,
                    failureSafety.name,
                )
                assertEquals(
                    if (reusable) GPUFrameReadbackNativeOutputSafety.Released
                    else GPUFrameReadbackNativeOutputSafety.Quarantined,
                    readback.finalizedSafety,
                    failureSafety.name,
                )
                firstCheckout = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(
                    fixture.cache.acquireFrame(fixture.exactPoolRequirements()),
                ).lease
                if (reusable) {
                    assertSame(registered.originalVertex, firstCheckout.handles.vertexBuffer, failureSafety.name)
                } else {
                    assertNotSame(registered.originalVertex, firstCheckout.handles.vertexBuffer, failureSafety.name)
                }

                readback.deliver(delivery)
                secondCheckout = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(
                    fixture.cache.acquireFrame(fixture.exactPoolRequirements()),
                ).lease
                assertNotSame(firstCheckout.handles.vertexBuffer, secondCheckout.handles.vertexBuffer, failureSafety.name)
                assertNotSame(registered.originalVertex, secondCheckout.handles.vertexBuffer, failureSafety.name)
            } finally {
                secondCheckout?.rollbackBeforeSubmit()
                firstCheckout?.rollbackBeforeSubmit()
                adapter.close()
                registered.materializer.close()
                fixture.close()
            }
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
        var duringFinalize: GPUWgpu4kCorePrimitiveFramePoolLease? = null
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
            var readbackSink: GPUFrameReadbackMapSink? = null
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
                    readbackSink = sink
                    return GPUFrameReadbackMapArmResult.Armed
                }

                override fun finalizeAfterNativeClose(
                    output: GPUPreparedReadbackOutput,
                    operand: GPUPreparedNativeScopeOperand.Readback,
                    safety: GPUFrameReadbackNativeOutputSafety,
                ): GPUFrameReadbackLifecycleResult {
                    duringFinalize = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(
                        fixture.cache.acquireFrame(fixture.exactPoolRequirements()),
                    ).lease
                    return GPUFrameReadbackLifecycleResult.Applied
                }
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
            val completionDelivery = GPUQueueCompletionDelivery.Accepted(
                fixture.prepared.completionTicket.ticketId,
                GPUQueueCompletionOutcome.Success,
            )
            requireNotNull(completionSink).accept(completionDelivery)
            val originalVertex = fixture.native.writeBufferCalls.single { call ->
                call.bufferLabel == "Kanvas.session.corePrimitive.framePool.vertices"
            }.buffer
            val originalDepthStencil = requireNotNull(
                materialized.draft.payload.scopeOperands
                    .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()[1]
                    .pass.depthStencilTarget,
            ).view
            val whileMapping = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(
                fixture.cache.acquireFrame(fixture.exactPoolRequirements()),
            ).lease
            assertNotSame(originalVertex, whileMapping.handles.vertexBuffer)
            assertNotSame(
                originalDepthStencil,
                requireNotNull(whileMapping.handles.pathDepthStencil).view,
            )
            assertIs<GPUWgpu4kCorePrimitiveFramePoolLeaseTransition.Applied>(
                whileMapping.rollbackBeforeSubmit(),
            )

            val pixels = GPUFrameReadbackMapDelivery.Pixels(
                fixture.prepared.resources.outputOwnedReadbacks.single().request.requestId,
                ByteArray(
                    fixture.scratch.targetBounds.width * fixture.scratch.targetBounds.height * 4,
                ),
            )
            requireNotNull(readbackSink).accept(pixels)
            assertEquals(
                GPUFrameStructuralOutcome.Succeeded,
                handle.completion.toCompletableFuture().join().outcome,
            )
            val competingDuringFinalize = requireNotNull(duringFinalize)
            assertNotSame(originalVertex, competingDuringFinalize.handles.vertexBuffer)
            assertNotSame(
                originalDepthStencil,
                requireNotNull(competingDuringFinalize.handles.pathDepthStencil).view,
            )
            assertEquals(0, adapter.activePreparedNativeFramePayloadCount)
            val afterFinalize = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(
                fixture.cache.acquireFrame(fixture.exactPoolRequirements()),
            ).lease
            assertSame(originalVertex, afterFinalize.handles.vertexBuffer)
            assertSame(
                originalDepthStencil,
                requireNotNull(afterFinalize.handles.pathDepthStencil).view,
            )

            requireNotNull(completionSink).accept(completionDelivery)
            requireNotNull(readbackSink).accept(pixels)
            val afterDuplicates = assertIs<GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired>(
                fixture.cache.acquireFrame(fixture.exactPoolRequirements()),
            ).lease
            assertNotSame(afterFinalize.handles.vertexBuffer, afterDuplicates.handles.vertexBuffer)
            assertIs<GPUWgpu4kCorePrimitiveFramePoolLeaseTransition.Applied>(
                afterDuplicates.rollbackBeforeSubmit(),
            )
            assertIs<GPUWgpu4kCorePrimitiveFramePoolLeaseTransition.Applied>(
                afterFinalize.rollbackBeforeSubmit(),
            )
        } finally {
            duringFinalize?.rollbackBeforeSubmit()
            materializer.close()
            adapter.close()
            fixture.close()
        }
    }

    private data class RegisteredW4dNativeFrame(
        val materializer: GPUWgpu4kCorePrimitiveFramePayloadMaterializer,
        val originalVertex: GPUBuffer,
        val originalDepthStencil: GPUTextureView,
    )

    private fun W4dExecutionFixture.NativeMaterializationFixture.registerNativeFrame(
        adapter: GPURuntimeResourceAdapter,
    ): RegisteredW4dNativeFrame {
        val materializer = materializer()
        val draft = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
            materializer.materializeReusable(
                frame,
                prepared.encoderPlan,
                prepared.resources,
                prepared.generationSeal,
            ),
        ).draft
        val originalVertex = requireNotNull(native.writeBufferCalls.single { call ->
            call.bufferLabel == "Kanvas.session.corePrimitive.framePool.vertices"
        }.buffer)
        val originalDepthStencil = requireNotNull(
            draft.payload.scopeOperands.filterIsInstance<GPUPreparedNativeScopeOperand.Render>()[1]
                .pass.depthStencilTarget,
        ).view
        val registration = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            adapter.registerPreparedNativeFrameDraft(draft),
        )
        assertIs<GPUPreparedNativeFrameBindingResult.Ready>(
            registration.ownership.bindLateSurface(
                acquiredSurface = null,
                binding = GPUPreparedNativeFrameLateSurfaceBinding.NotRequired,
            ),
        )
        assertTrue(prepared.rollback.adoptNativePayload(registration.ownership))
        return RegisteredW4dNativeFrame(materializer, originalVertex, originalDepthStencil)
    }

    private enum class W4dBackendFailureStage {
        CreateEncoder,
        Encode,
        Finish,
        Submit,
    }

    private enum class W4dReadbackFailureStage {
        MarkSubmitted,
        AcceptGPUCompletion,
        MapArm,
        Finalize,
    }

    private enum class W4dReadbackFailureMode {
        Refused,
        Throw,
    }

    private class HeldCompletionAccess(
        private val armResult: ((GPUQueueCompletionTicket) -> GPUQueueCompletionArmResult)? = null,
    ) : GPUQueueCompletionAccess {
        private var sink: GPUQueueCompletionSink? = null

        override fun reserveTicket(request: GPUQueueCompletionTicketRequest) =
            GPUQueueCompletionTicketReservation.Missing

        override fun abandonReservedTicket(ticket: GPUQueueCompletionTicket) =
            GPUQueueCompletionTicketAbandonResult.NotReserved(ticket.ticketId)

        override fun armAfterSubmit(
            ticket: GPUQueueCompletionTicket,
            sink: GPUQueueCompletionSink,
        ): GPUQueueCompletionArmResult {
            this.sink = sink
            return armResult?.invoke(ticket) ?: GPUQueueCompletionArmResult.Armed(ticket.ticketId)
        }

        override suspend fun awaitCompletion(ticket: GPUQueueCompletionTicket) =
            GPUQueueCompletionDelivery.Accepted(ticket.ticketId, GPUQueueCompletionOutcome.Success)

        override fun cancel(ticket: GPUQueueCompletionTicket) =
            GPUQueueCompletionDelivery.Accepted(
                ticket.ticketId,
                GPUQueueCompletionOutcome.Failure(GPUQueueCompletionFailureKind.Cancelled),
            )

        fun deliver(ticket: GPUQueueCompletionTicket, outcome: GPUQueueCompletionOutcome) {
            requireNotNull(sink).accept(GPUQueueCompletionDelivery.Accepted(ticket.ticketId, outcome))
        }
    }

    private class HeldReadbackAccess(
        private val failureStage: W4dReadbackFailureStage? = null,
        private val failureMode: W4dReadbackFailureMode = W4dReadbackFailureMode.Refused,
        private val duringFinalize: (() -> Unit)? = null,
    ) : GPUFrameReadbackAccess {
        private var sink: GPUFrameReadbackMapSink? = null
        var finalizedSafety: GPUFrameReadbackNativeOutputSafety? = null
            private set

        override fun markSubmitted(
            ticket: GPUQueueCompletionTicket,
            output: GPUPreparedReadbackOutput,
            operand: GPUPreparedNativeScopeOperand.Readback,
        ) = lifecycle(W4dReadbackFailureStage.MarkSubmitted)

        override fun acceptGPUCompletion(
            ticket: GPUQueueCompletionTicket,
            output: GPUPreparedReadbackOutput,
            operand: GPUPreparedNativeScopeOperand.Readback,
        ) = lifecycle(W4dReadbackFailureStage.AcceptGPUCompletion)

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
            if (failureStage == W4dReadbackFailureStage.MapArm) {
                return when (failureMode) {
                    W4dReadbackFailureMode.Refused -> GPUFrameReadbackMapArmResult.Refused(
                        failureDiagnostic("failed.w4d.readback.MapArm.Refused"),
                    )
                    W4dReadbackFailureMode.Throw -> error("failed.w4d.readback.MapArm.Throw")
                }
            }
            this.sink = sink
            return GPUFrameReadbackMapArmResult.Armed
        }

        override fun finalizeAfterNativeClose(
            output: GPUPreparedReadbackOutput,
            operand: GPUPreparedNativeScopeOperand.Readback,
            safety: GPUFrameReadbackNativeOutputSafety,
        ): GPUFrameReadbackLifecycleResult {
            finalizedSafety = safety
            duringFinalize?.invoke()
            return lifecycle(W4dReadbackFailureStage.Finalize)
        }

        fun deliver(delivery: GPUFrameReadbackMapDelivery) = requireNotNull(sink).accept(delivery)

        private fun lifecycle(stage: W4dReadbackFailureStage): GPUFrameReadbackLifecycleResult {
            if (failureStage != stage) return GPUFrameReadbackLifecycleResult.Applied
            return when (failureMode) {
                W4dReadbackFailureMode.Refused -> GPUFrameReadbackLifecycleResult.Refused(
                    failureDiagnostic("failed.w4d.readback.${stage.name}.Refused"),
                )
                W4dReadbackFailureMode.Throw -> error("failed.w4d.readback.${stage.name}.Throw")
            }
        }

        private fun failureDiagnostic(code: String) = GPUDiagnostic(
            GPUDiagnosticCode(code),
            GPUDiagnosticDomain.Execution,
            GPUDiagnosticSeverity.Error,
            code,
        )
    }

    private fun W4dExecutionFixture.NativeMaterializationFixture.sceneTarget(): GPUSceneTarget =
        GPUSceneTarget(
            targetId = scratch.target.value,
            resolvedTexture = GPUTextureResourceRef("prepared:${scratch.target.value}"),
            retainedMsaaAttachment = null,
            width = scratch.targetBounds.width,
            height = scratch.targetBounds.height,
            format = GPUColorFormat("rgba8unorm-srgb"),
            colorInterpretation = GPUColorInterpretation("linear-premul"),
            usages = setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource),
            sampleCount = 1,
            deviceGeneration = prepared.generationSeal.deviceGeneration,
            targetGeneration = prepared.generationSeal.targetGeneration,
        )

    private fun W4dExecutionFixture.NativeMaterializationFixture.backend(
        failureStage: W4dBackendFailureStage? = null,
    ): GPUFrameEncodingBackend = object : GPUFrameEncodingBackend {
        override val deviceGeneration = prepared.generationSeal.deviceGeneration
        override val encodingMode = GPUFrameEncodingMode.NativeOperandsRequired

        override fun createCommandEncoder(label: String): GPUFrameCommandEncoder {
            if (failureStage == W4dBackendFailureStage.CreateEncoder) error("injected create failure")
            return object : GPUFrameCommandEncoder {
                override fun encode(
                    scope: GPUCommandEncoderScopePlan,
                    preparedFrame: PreparedGPUFrame,
                    sceneTarget: GPUSceneTarget,
                    nativeOperand: GPUPreparedNativeScopeOperand?,
                ) {
                    if (failureStage == W4dBackendFailureStage.Encode) error("injected encode failure")
                    requireNotNull(nativeOperand)
                }

                override fun finish(): GPUFrameCommandBuffer {
                    if (failureStage == W4dBackendFailureStage.Finish) error("injected finish failure")
                    return GPUFrameCommandBuffer("w4d.controlled.execution")
                }

                override fun discard() = GPUFrameDiscardResult.Discarded
            }
        }

        override fun isCanonicalSceneTargetView(
            sceneTarget: GPUSceneTarget,
            operand: GPUPreparedNativeTextureViewOperand,
        ) = operand.view === target.view

        override fun discard(commandBuffer: GPUFrameCommandBuffer) = GPUFrameDiscardResult.Discarded

        override fun submit(commandBuffer: GPUFrameCommandBuffer) {
            if (failureStage == W4dBackendFailureStage.Submit) error("injected submit failure")
        }
    }

    private fun noOpRetention(): GPUFrameResourceRetention = object : GPUFrameResourceRetention {
        override fun registerAfterSubmit(registration: GPUFrameRetentionRegistration) = Unit

        override fun complete(
            ticket: GPUQueueCompletionTicket,
            outcome: GPUQueueCompletionOutcome,
        ) = Unit

        override fun quarantine(
            registration: GPUFrameRetentionRegistration,
            diagnostic: GPUDiagnostic,
        ) = Unit
    }

    private fun testDiagnostic(code: String): GPUDiagnostic = GPUDiagnostic(
        GPUDiagnosticCode(code),
        GPUDiagnosticDomain.Execution,
        GPUDiagnosticSeverity.Error,
        code,
    )

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

    private fun GPUFramePlan.w4dScratch(): W4dSessionScratchV1 = requireNotNull(
        steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            .first().drawPackets.single().corePrimitivePreparedAuthority?.w4dSessionScratch,
    )

    private fun GPUFramePlan.w4dPacket(role: GPUDrawPacketRole): GPUDrawPacket = steps
        .filterIsInstance<GPUFrameStep.RenderPassStep>()
        .flatMap(GPUFrameStep.RenderPassStep::drawPackets)
        .first { packet -> packet.role == role }

    private fun W4dSessionScratchV1.copyForW4dTest(
        planId: String = this.planId,
        targetBytes: Long = this.targetBytes,
        stagingBytes: Long = this.stagingBytes,
        vertexResourceId: PlanResourceId = this.vertexResourceId,
        indexResourceId: PlanResourceId = this.indexResourceId,
        uniformResourceId: PlanResourceId = this.uniformResourceId,
        depthStencilResourceId: PlanResourceId? = this.depthStencilResourceId,
        depthStencilFirstPassIndex: Int? = this.depthStencilFirstPassIndex,
        depthStencilBytes: Long = this.depthStencilBytes,
        maxBufferSize: Long = this.maxBufferSize,
    ): W4dSessionScratchV1 = W4dSessionScratchV1(
        planId = planId,
        capabilitySealHash = capabilitySealHash,
        deviceGeneration = deviceGeneration,
        target = target,
        staging = staging,
        targetBounds = targetBounds,
        vertexResourceId = vertexResourceId,
        indexResourceId = indexResourceId,
        uniformResourceId = uniformResourceId,
        depthStencilResourceId = depthStencilResourceId,
        targetBytes = targetBytes,
        stagingBytes = stagingBytes,
        capabilityId = capabilityId,
        renderPassIds = renderPassIds,
        readbackPassId = readbackPassId,
        resourceLastPassIndexExclusive = resourceLastPassIndexExclusive,
        depthStencilFirstPassIndex = depthStencilFirstPassIndex,
        draws = draws,
        uniformPlan = uniformPlan,
        uniformStrideBytes = uniformStrideBytes,
        vertexUsefulBytes = vertexUsefulBytes,
        indexUsefulBytes = indexUsefulBytes,
        uniformUsefulBytes = uniformUsefulBytes,
        vertexCapacityBytes = vertexCapacityBytes,
        indexCapacityBytes = indexCapacityBytes,
        uniformCapacityBytes = uniformCapacityBytes,
        depthStencilBytes = depthStencilBytes,
        poolCapacities = poolCapacities,
        maxBufferSize = maxBufferSize,
        maxDynamicUniformBuffersPerPipelineLayout = maxDynamicUniformBuffersPerPipelineLayout,
    )

    private fun GPUDrawPacket.reauthenticatedW4dPacket(
        scratch: W4dSessionScratchV1,
        packetId: GPUDrawPacketID = this.packetId,
        passId: String = this.passId,
    ): GPUDrawPacket {
        val authority = requireNotNull(corePrimitivePreparedAuthority)
        val reauthenticated = copyForW4dTest(packetId = packetId, passId = passId, attachAuthority = false)
        return reauthenticated.attachCorePrimitivePreparedAuthority(
            GPUCorePrimitivePreparedPacketAuthority.plannedW4d(
                packet = reauthenticated,
                structuralPipelineKey = authority.structuralPipelineKey,
                renderPipelineKey = authority.renderPipelineKey,
                planId = scratch.planId,
                capabilitySealHash = scratch.capabilitySealHash,
                scratch = scratch,
            ),
        )
    }

    private fun GPUDrawPacket.copyForW4dTest(
        packetId: GPUDrawPacketID = this.packetId,
        passId: String = this.passId,
        role: GPUDrawPacketRole = this.role,
        semanticPayload: GPUDrawSemanticPayload? = this.semanticPayload,
        attachAuthority: Boolean = true,
    ): GPUDrawPacket {
        val replacement = GPUDrawPacket(
            packetId = packetId,
            commandIdValue = commandIdValue,
            analysisRecordId = analysisRecordId,
            passId = passId,
            layerId = layerId,
            bindingListId = bindingListId,
            insertionReasonCode = insertionReasonCode,
            sortKey = sortKey,
            sortKeyPreimage = sortKeyPreimage,
            renderStepId = renderStepId,
            renderStepVersion = renderStepVersion,
            role = role,
            blendPlan = blendPlan,
            renderPipelineKey = renderPipelineKey,
            computePipelineKey = computePipelineKey,
            bindingLayoutHash = bindingLayoutHash,
            uniformSlot = uniformSlot,
            resourceSlot = resourceSlot,
            semanticPayload = semanticPayload,
            vertexSourceLabel = vertexSourceLabel,
            scissorBoundsHash = scissorBoundsHash,
            targetStateHash = targetStateHash,
            originalPaintOrder = originalPaintOrder,
            resourceGeneration = resourceGeneration,
            frameProvenance = frameProvenance,
            clipCoveragePlan = clipCoveragePlan,
            clipExecutionPlan = clipExecutionPlan,
            diagnostics = diagnostics,
            clipProducerAuthority = clipProducerAuthority,
        )
        return if (attachAuthority) {
            replacement.attachCorePrimitivePreparedAuthority(requireNotNull(corePrimitivePreparedAuthority))
        } else {
            replacement
        }
    }

    private fun GPUCorePrimitiveGeometry.TriangulatedPath.copyForW4dTest(
        sourceAuthority: GPUPathSourceAuthority = this.sourceAuthority,
    ): GPUCorePrimitiveGeometry.TriangulatedPath = GPUCorePrimitiveGeometry.TriangulatedPath(
        vertices = vertices,
        indices = indices,
        sourceContourStarts = sourceContourStarts,
        sourceVertexCount = sourceVertexCount,
        coverBounds = coverBounds,
        geometryMode = geometryMode,
        fillRule = fillRule,
        inverseFill = inverseFill,
        strokeStyle = strokeStyle,
        sourceAuthority = sourceAuthority,
    )

    private fun GPUDrawSemanticPayload.CorePrimitive.copyForW4dTest(
        geometry: GPUCorePrimitiveGeometry = this.geometry,
        premultipliedRgba: List<Float> = this.premultipliedRgba,
    ): GPUDrawSemanticPayload.CorePrimitive = GPUDrawSemanticPayload.CorePrimitive(
        payloadRef = payloadRef,
        sourceFamily = sourceFamily,
        geometry = geometry,
        premultipliedRgba = premultipliedRgba,
        targetBounds = targetBounds,
        scissorBounds = scissorBounds,
        clipCoveragePlan = clipCoveragePlan,
        clipExecutionPlanIdentity = clipExecutionPlanIdentity,
        blendPlanIdentity = blendPlanIdentity,
        frameProvenance = frameProvenance,
        canonicalHash = canonicalHash,
        coverageMode = coverageMode,
        analysisRecordId = analysisRecordId,
        analysisCommandFamily = analysisCommandFamily,
        rectRouteAuthority = rectRouteAuthority,
        rectGeometryAuthority = rectGeometryAuthority,
        rrectGeometryAuthority = rrectGeometryAuthority,
        drrectOuterGeometryAuthority = drrectOuterGeometryAuthority,
        drrectInnerGeometryAuthority = drrectInnerGeometryAuthority,
        material = material,
    )

    private fun GPUFramePlan.withW4dScratch(
        scratch: W4dSessionScratchV1,
        packetIdFor: (GPUDrawPacket) -> GPUDrawPacketID = GPUDrawPacket::packetId,
        passIdFor: (GPUDrawPacket) -> String = GPUDrawPacket::passId,
    ): GPUFramePlan = copyForW4dExecutionTest(
        steps = steps.map { step ->
            val render = step as? GPUFrameStep.RenderPassStep ?: return@map step
            val packet = render.drawPackets.single()
            val replacement = packet.reauthenticatedW4dPacket(
                scratch,
                packetIdFor(packet),
                passIdFor(packet),
            )
            render.copyForW4dTest(drawPackets = listOf(replacement))
        },
    )

    private fun GPUFramePlan.withW4dPacket(
        original: GPUDrawPacket,
        replacement: GPUDrawPacket,
    ): GPUFramePlan = copyForW4dExecutionTest(
        steps = steps.map { step ->
            val render = step as? GPUFrameStep.RenderPassStep ?: return@map step
            if (render.drawPackets.single() === original) {
                render.copyForW4dTest(drawPackets = listOf(replacement))
            } else {
                render
            }
        },
    )

    private fun GPUFramePlan.withW4dRenderStep(
        index: Int,
        original: GPUFrameStep.RenderPassStep,
        resourceUses: List<GPUFrameResourceUse> = original.resourceUses,
        samplePlan: GPUSamplePlan = original.samplePlan,
    ): GPUFramePlan = copyForW4dExecutionTest(
        steps = steps.mapIndexed { stepIndex, step ->
            if (stepIndex == index) {
                original.copyForW4dTest(resourceUses = resourceUses, samplePlan = samplePlan)
            } else {
                step
            }
        },
    )

    private fun GPUFrameStep.RenderPassStep.copyForW4dTest(
        resourceUses: List<GPUFrameResourceUse> = this.resourceUses,
        drawPackets: List<GPUDrawPacket> = this.drawPackets,
        samplePlan: GPUSamplePlan = this.samplePlan,
        sourceTaskIds: List<GPUTaskID> = this.sourceTaskIds,
    ): GPUFrameStep.RenderPassStep = GPUFrameStep.RenderPassStep(
        target = target,
        loadStore = loadStore,
        samplePlan = samplePlan,
        resourceUses = resourceUses,
        drawPackets = drawPackets,
        sourceTaskIds = sourceTaskIds,
        batches = batches.map { batch ->
            GPUFrameRenderBatch(
                batchId = batch.batchId,
                kind = batch.kind,
                packets = drawPackets,
                sourceTaskIds = sourceTaskIds,
            )
        },
        sampleContinuation = sampleContinuation,
        depthStencilLoadStore = depthStencilLoadStore,
        preparedImageBindingsByPacketId = preparedImageBindingsByPacketId,
        preparedTextBindingsByPacketId = preparedTextBindingsByPacketId,
    )

    private fun GPUFramePlan.withW4dTaskIdReplaced(
        original: GPUTaskID,
        replacement: GPUTaskID,
    ): GPUFramePlan {
        fun replace(ids: List<GPUTaskID>) = ids.map { replacement.takeIf { it == original } ?: it }
        return copyForW4dExecutionTest(
            steps = steps.map { step -> when (step) {
                is GPUFrameStep.PrepareResourcesStep -> GPUFrameStep.PrepareResourcesStep(
                    step.requests,
                    replace(step.sourceTaskIds),
                )
                is GPUFrameStep.RenderPassStep -> step.copyForW4dTest(
                    sourceTaskIds = replace(step.sourceTaskIds),
                )
                is GPUFrameStep.ReadbackCopyStep -> GPUFrameStep.ReadbackCopyStep(
                    step.source,
                    step.staging,
                    step.request,
                    replace(step.sourceTaskIds),
                )
                else -> step
            } },
            dependencies = dependencies.map { dependency ->
                GPUTaskDependency(
                    fromTaskId = replacement.takeIf { dependency.fromTaskId == original }
                        ?: dependency.fromTaskId,
                    toTaskId = replacement.takeIf { dependency.toTaskId == original }
                        ?: dependency.toTaskId,
                    dependencyKind = dependency.dependencyKind,
                    useToken = dependency.useToken,
                    reasonCode = dependency.reasonCode,
                    atomicGroupId = dependency.atomicGroupId,
                )
            },
        )
    }

    private fun w4dPreflightCode(frame: GPUFramePlan): String = when (
        val result = W4dExecutionFixture.preflight(frame)
    ) {
        is GPUFramePreflightResult.Refused -> result.diagnostic.code.value
        is GPUFramePreflightResult.Prepared -> try {
            "prepared"
        } finally {
            assertTrue(result.frame.claimForRollback())
            assertTrue(result.frame.rollback.execute().successful)
        }
    }

    private fun GPUFramePlan.copyForW4dExecutionTest(
        capabilitySeal: GPUFrameCapabilitySeal = this.capabilitySeal,
        steps: List<GPUFrameStep> = this.steps,
        dependencies: List<GPUTaskDependency> = this.dependencies,
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

    fun stencilOnlyFramePlan() = GPUFramePlanner.plan(taskList(stencilOnly = true))

    class NativeMaterializationFixture internal constructor(
        val frame: org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan,
        val prepared: PreparedGPUFrame,
        val scratch: W4dSessionScratchV1,
        val native: GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy,
        val target: GPUWgpu4kPreparedSceneTarget,
        val cache: GPUWgpu4kCorePrimitiveSessionCache,
    ) {
        fun materializer() = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            native.device,
            native.queue,
            target,
            cache,
            requireNotNull(capabilities().limits),
        )

        fun close() {
            cache.close()
            target.close()
            if (prepared.claimForRollback()) check(prepared.rollback.execute().successful)
        }

        fun exactPoolRequirements(): GPUWgpu4kCorePrimitiveFramePoolRequirements {
            val capacities = GPUWgpu4kCorePrimitiveFramePoolCapacities(
                scratch.vertexCapacityBytes,
                scratch.indexCapacityBytes,
                scratch.uniformCapacityBytes,
            )
            val pathDepthStencil = scratch.depthStencilResourceId?.let {
                GPUWgpu4kCorePrimitivePathDepthStencilRequirement(
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
                )
            }
            return GPUWgpu4kCorePrimitiveFramePoolRequirements(
                deviceGeneration = prepared.generationSeal.deviceGeneration,
                vertexBytes = scratch.vertexUsefulBytes,
                indexBytes = scratch.indexUsefulBytes,
                uniformBytes = scratch.uniformPlan.totalBytes,
                expectedCapacities = capacities,
                pathDepthStencil = pathDepthStencil,
                componentIdentity = PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY,
                sampleCount = 1,
            )
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

    fun preflight(
        frame: org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan,
        resourceProvider: GPUConcreteResourceProvider = GPUConcreteResourceProvider(),
        completionProvider: GPUQueueCompletionProvider = defaultCompletionProvider(frame),
    ): GPUFramePreflightResult {
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
            resourceProvider = resourceProvider,
            completionProvider = completionProvider,
            surfaceProvider = object : GPUSurfaceOutputProvider {
                override fun acquire(request: GPUSurfaceAcquisitionRequest): GPUSurfaceAcquisitionResult =
                    error("W4d offscreen preflight must not acquire a surface")

                override fun release(output: GPUAcquiredSurfaceOutput) = GPUSurfaceReleaseResult.Released
            },
        ).preflight(frame)
    }

    private fun defaultCompletionProvider(
        frame: org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan,
    ): GPUQueueCompletionProvider = object : GPUQueueCompletionProvider {
        override fun reserveTicket(request: GPUQueueCompletionTicketRequest) =
            GPUQueueCompletionTicketReservation.Reserved(
                GPUQueueCompletionTicket(
                    GPUQueueCompletionTicketID("ticket.w4d.preflight.${frame.frameId.value}"),
                    request.frameId,
                    request.deviceGeneration,
                ),
            )

        override fun abandonReservedTicket(ticket: GPUQueueCompletionTicket) =
            GPUQueueCompletionTicketAbandonResult.Abandoned(ticket.ticketId)
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

    private fun taskList(
        directOnly: Boolean = false,
        stencilOnly: Boolean = false,
    ): org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList {
        val graph = graph(directOnly, stencilOnly)
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

    private fun graph(directOnly: Boolean, stencilOnly: Boolean): RenderGraph {
        require(!(directOnly && stencilOnly))
        val scene = SceneSnapshot.of(
            SceneExtent(8, 8),
            ColorSpace.SRGB,
            when {
                directOnly -> listOf(pathDraw(triangle(), PaintStyleNode.STROKE_AND_FILL, width = 0f))
                stencilOnly -> listOf(pathDraw(openStroke(), PaintStyleNode.STROKE, width = 2f))
                else -> listOf(
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
