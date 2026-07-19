package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupLayoutDescriptor
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureView
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.TextureDescriptor
import java.io.File
import java.lang.reflect.Proxy
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.analysis.corePrimitiveRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipAnalyticElement
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipAtomicGroupID
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipFillRule
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipOrderingToken
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskCombine
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskConsumerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskProducerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilCompare
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilConsumerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilLoadOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilProducerPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilStoreOperation
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds as GPUClipBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionGeometry
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketStream
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskConsumerGeometrySnapshot
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveUniformSlabSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandOperandBridge
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandStream
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveFillRule
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRectRouteAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadFingerprint
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameTaskListBuilder
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveDirectClipAuthority
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameReadbackRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackLayoutPlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackLayoutPlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandReference
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedConcreteResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUBufferResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUReadbackStagingLease
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance

class GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest {
    private enum class RouteShape { Direct, PathOnly, Mixed, TwoPathPairs, ClipStencil, CoverageMask }

    @Test
    fun `analytic shape closed route refuses at real preflight boundary before every side effect`() {
        val fixture = fixture()
        val originalPackets = fixture.plan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            .single().drawPackets
        val plan = originalPackets.fold(fixture.plan) { currentPlan, originalPacket ->
            val originalAuthority = requireNotNull(originalPacket.corePrimitivePreparedAuthority)
            val analyticKey = originalAuthority.structuralPipelineKey.copy(
                shader = org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
                    .Shader.AnalyticShape,
            )
            currentPlan.replacingPacket(
                originalPacket,
                originalPacket.withPreparedAuthority(
                    originalAuthority.copy(structuralPipelineKey = analyticKey),
                ),
            )
        }
        val adapter = GPURuntimeResourceAdapter()
        val resources = GPUConcreteResourceProvider(leaseFactory = adapter)
        var ticketCalls = 0
        var surfaceCalls = 0
        var materializerCalls = 0
        val completion = object : GPUQueueCompletionProvider {
            override fun reserveTicket(
                request: GPUQueueCompletionTicketRequest,
            ): GPUQueueCompletionTicketReservation {
                ticketCalls += 1
                error("Closed analytic-shape preflight must not reserve a completion ticket")
            }

            override fun abandonReservedTicket(
                ticket: GPUQueueCompletionTicket,
            ): GPUQueueCompletionTicketAbandonResult {
                ticketCalls += 1
                error("Closed analytic-shape preflight must not abandon a completion ticket")
            }
        }
        val surface = object : GPUSurfaceOutputProvider {
            override fun acquire(request: GPUSurfaceAcquisitionRequest): GPUSurfaceAcquisitionResult {
                surfaceCalls += 1
                error("Closed analytic-shape preflight must not acquire a surface")
            }

            override fun release(output: GPUAcquiredSurfaceOutput): GPUSurfaceReleaseResult {
                surfaceCalls += 1
                error("Closed analytic-shape preflight must not release a surface")
            }
        }
        val nativeMaterializer = object : GPUPreparedNativeFramePayloadMaterializer {
            override fun materializeReusable(
                framePlan: GPUFramePlan,
                encoderPlan: GPUCommandEncoderPlan,
                resources: GPUPreparedResourceSet,
                generationSeal: GPUPreparedGenerationSeal,
            ): GPUPreparedNativeFramePayloadMaterialization {
                materializerCalls += 1
                error("Closed analytic-shape preflight must not materialize native payloads")
            }

            override fun bindLateSurface(
                draft: GPUPreparedNativeFrameDraft,
                acquiredSurface: GPUAcquiredSurfaceOutput?,
            ): GPUPreparedNativeFrameLateSurfaceBinding {
                materializerCalls += 1
                error("Closed analytic-shape preflight must not bind a late surface")
            }
        }

        val result = GPUFramePreflighter(
            context = GPUFramePreflightContext(
                targetId = "target.core.proxy",
                deviceGeneration = fixture.generationSeal.deviceGeneration,
                targetGeneration = fixture.generationSeal.targetGeneration,
                resourceGenerations = fixture.generationSeal.resourceGenerations,
            ),
            capabilities = capabilities(),
            resourceProvider = resources,
            completionProvider = completion,
            surfaceProvider = surface,
            nativeBoundary = adapter.bindNativeFrameBoundary(resources, nativeMaterializer),
        ).preflight(plan)

        val refused = assertIs<GPUFramePreflightResult.Refused>(result)
        assertEquals(CORE_PRIMITIVE_ANALYTIC_SHAPE_ROUTE_CLOSED_CODE, refused.diagnostic.code.value)
        assertEquals(CORE_PRIMITIVE_ANALYTIC_SHAPE_ROUTE_CLOSED_MESSAGE, refused.diagnostic.message)
        assertEquals(0, ticketCalls)
        assertEquals(0, surfaceCalls)
        assertEquals(0, materializerCalls)
        assertEquals(0, adapter.activePreparedNativeFramePayloadCount)
        assertEquals(0, resources.pendingPhysicalReservationCount)
        assertTrue(resources.telemetry.dumpEvents.isEmpty())
        fixture.close()
        adapter.close()
    }

    @Test
    fun `analytic shape closed route refuses at real materializer boundary before cache pool and native`() {
        val fixture = fixture()
        val scope = fixture.encoderPlan.scopes.single()
        val originalRoutes = assertIs<GPUCorePrimitiveDirectNativeRouteSeal.Routes>(
            scope.corePrimitiveDirectNativeRouteSeal,
        )
        val originalPass = requireNotNull(originalRoutes.preparedPassSeal)
        val analyticPass = GPUCorePrimitiveDirectPreparedPassSeal(
            structuralPipelineKey = originalPass.structuralPipelineKey.copy(
                shader = org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
                    .Shader.AnalyticShape,
            ),
            uniformSlabSeal = originalPass.uniformSlabSeal,
        )
        setPrivateField(
            scope,
            "corePrimitiveDirectNativeRouteSeal",
            GPUCorePrimitiveDirectNativeRouteSeal.Routes.snapshot(
                originalRoutes.routesByPacketId,
                analyticPass,
            ),
        )
        fixture.native.events.clear()
        fixture.native.writeBufferCalls.clear()
        val cacheBefore = fixture.cache.counters()
        val poolSlotsBefore = framePoolSlotCount(fixture.cache)
        val materializer = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            fixture.limits,
        )

        val result = materializer.materializeReusable(
            fixture.plan,
            fixture.encoderPlan,
            fixture.resources,
            fixture.generationSeal,
        )

        val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(result)
        assertEquals(CORE_PRIMITIVE_ANALYTIC_SHAPE_ROUTE_CLOSED_CODE, refused.code)
        assertEquals(CORE_PRIMITIVE_ANALYTIC_SHAPE_ROUTE_CLOSED_MESSAGE, refused.message)
        assertEquals(cacheBefore, fixture.cache.counters())
        assertEquals(poolSlotsBefore, framePoolSlotCount(fixture.cache))
        assertEquals(emptyList(), fixture.native.events)
        assertEquals(emptyList(), fixture.native.writeBufferCalls)
        materializer.close()
        fixture.close()
    }

    @Test
    fun `prepared coverage mask materializes color-only producers and consumers with one shared mask`() {
        val fixture = fixture(
            readback = true,
            routeShape = RouteShape.CoverageMask,
            useRealPreflight = true,
        )

        val materialized = fixture.materializeCore()
        val renders = materialized.draft.payload.scopeOperands
            .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
        assertEquals(4, renders.size)
        val producers = renders.take(2)
        val consumers = renders.drop(2)
        assertTrue(renders.all { it.pass.depthStencilTarget == null })
        assertEquals(1, distinctIdentityCount(producers.map { it.pass.colorTarget.view }))
        assertEquals(1, distinctIdentityCount(consumers.map { it.pass.colorTarget.view }))
        assertNotSame(producers.first().pass.colorTarget.view, consumers.first().pass.colorTarget.view)
        assertEquals(
            listOf(GPUPreparedNativeLoadOperation.Clear, GPUPreparedNativeLoadOperation.Load),
            producers.map { it.pass.loadOperation },
        )
        assertEquals(GPUPreparedNativeClearColor(1.0, 1.0, 1.0, 1.0), producers.first().pass.clearColor)
        assertEquals(null, producers.last().pass.clearColor)
        assertEquals(
            listOf(GPUPreparedNativeLoadOperation.Clear, GPUPreparedNativeLoadOperation.Load),
            consumers.map { it.pass.loadOperation },
        )
        assertEquals(
            listOf(0L, 256L),
            producers.map { producer ->
                producer.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetBindGroup>()
                    .single().dynamicOffsets.single()
            },
        )
        producers.forEach { producer ->
            assertEquals(3, producer.commands.size)
            assertIs<GPUPreparedNativeRenderCommand.SetPipeline>(producer.commands[0])
            assertEquals(
                listOf(producer.commands[1]),
                producer.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetBindGroup>(),
            )
            assertEquals(
                GPUPreparedNativeDrawCall.Draw(3),
                assertIs<GPUPreparedNativeRenderCommand.Draw>(producer.commands[2]).drawCall,
            )
            assertTrue(producer.commands.none {
                it is GPUPreparedNativeRenderCommand.SetVertexBuffer ||
                    it is GPUPreparedNativeRenderCommand.SetIndexBuffer ||
                    it is GPUPreparedNativeRenderCommand.SetScissor
            })
        }
        assertEquals(
            listOf(512L, 768L),
            consumers.map { consumer ->
                consumer.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetBindGroup>()
                    .single().dynamicOffsets.single()
            },
        )
        consumers.forEach { consumer ->
            assertEquals(GPUPreparedNativeRenderOperandLayout.IndexedCorePrimitiveFullTarget, consumer.operandLayout)
            assertEquals(1, consumer.commands.filterIsInstance<GPUPreparedNativeRenderCommand.DrawIndexed>().size)
            assertTrue(consumer.commands.none {
                it is GPUPreparedNativeRenderCommand.SetScissor ||
                    it is GPUPreparedNativeRenderCommand.SetStencilReference
            })
        }
        assertNotSame(
            producers[0].commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetPipeline>()
                .single().pipeline.pipeline,
            producers[1].commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetPipeline>()
                .single().pipeline.pipeline,
        )
        assertSame(
            consumers[0].commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetPipeline>()
                .single().pipeline.pipeline,
            consumers[1].commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetPipeline>()
                .single().pipeline.pipeline,
        )
        val producerBindGroups = producers.map {
            it.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetBindGroup>()
                .single().bindGroup.bindGroup
        }
        val consumerBindGroups = consumers.map {
            it.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetBindGroup>()
                .single().bindGroup.bindGroup
        }
        assertEquals(1, distinctIdentityCount(producerBindGroups))
        assertEquals(1, distinctIdentityCount(consumerBindGroups))
        assertNotSame(producerBindGroups.first(), consumerBindGroups.first())
        assertEquals(3, fixture.native.writeBufferCalls.size)
        val routeSeal = assertIs<GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer>(
            fixture.encoderPlan.scopes.first().corePrimitiveCoverageMaskPreparedRouteSeal,
        )
        val packedGeometry = packCorePrimitiveFrameGeometry(routeSeal.route.consumers.map { consumer ->
            when (val geometry = consumer.geometry) {
                is GPUCorePrimitiveCoverageMaskConsumerGeometrySnapshot.Rect ->
                    GPUCorePrimitiveDirectNativeRoute.Accepted(
                        floatArrayOf(
                            geometry.left, geometry.top, geometry.right, geometry.top,
                            geometry.right, geometry.bottom, geometry.left, geometry.bottom,
                        ),
                        intArrayOf(0, 2, 1, 0, 3, 2),
                    )
                is GPUCorePrimitiveCoverageMaskConsumerGeometrySnapshot.DirectTriangles ->
                    GPUCorePrimitiveDirectNativeRoute.Accepted(
                        geometry.vertices.toFloatArray(),
                        geometry.indices.toIntArray(),
                    )
            }
        })
        val exactUploads = listOf(
            Triple(
                "Kanvas.session.corePrimitive.framePool.vertices",
                ArrayBuffer.of(packedGeometry.vertices).toByteArray(),
                routeSeal.slabAuthority.vertexByteSize,
            ),
            Triple(
                "Kanvas.session.corePrimitive.framePool.indices",
                ArrayBuffer.of(packedGeometry.indices).toByteArray(),
                routeSeal.slabAuthority.indexByteSize,
            ),
            Triple(
                "Kanvas.session.corePrimitive.framePool.uniforms",
                routeSeal.slabAuthority.uniformSlabSeal.packedBytesSnapshot(),
                routeSeal.slabAuthority.uniformByteSize,
            ),
        )
        fixture.native.writeBufferCalls.zip(exactUploads).forEach { (call, expected) ->
            val (label, bytes, byteSize) = expected
            assertEquals(label, call.bufferLabel)
            assertSame<Any>(fixture.native.createdHandles(label).single(), requireNotNull(call.buffer))
            assertEquals(0uL, call.bufferOffset)
            assertEquals(0uL, call.dataOffset)
            assertEquals(byteSize.toULong(), call.size)
            assertEquals(byteSize.toULong(), call.dataBytes)
            assertContentEquals(bytes, call.snapshot)
        }
        assertEquals(1, fixture.native.events.count {
            it == "createTexture:Kanvas.session.corePrimitive.framePool.coverageMask"
        })
        assertTrue(renders.flatMap { it.operands }.all {
            it.ownership == GPUPreparedNativeOperandOwnership.Borrowed
        })
        val readback = assertIs<GPUPreparedNativeScopeOperand.Readback>(
            materialized.draft.payload.scopeOperands.last(),
        )
        assertEquals(GPUPreparedNativeOperandOwnership.Borrowed, readback.source.ownership)
        assertEquals(
            GPUPreparedNativeOperandOwnership.OutputOwnedReadback,
            readback.destination.ownership,
        )

        assertTrue(materialized.draft.disposeBeforeRegistration())
        fixture.close()
    }

    @Test
    fun `coverage mask corruption table refuses before any native action`() {
        data class Scenario(
            val label: String,
            val mutate: (Fixture) -> CoverageMaskMaterializationInput,
        )
        val scenarios = listOf(
            Scenario("live-producer-packet") { fixture ->
                val producer = fixture.plan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
                    .flatMap(GPUFrameStep.RenderPassStep::drawPackets)
                    .first { it.role == GPUDrawPacketRole.ClipProducer }
                setPrivateField(producer, "clipExecutionPlan", GPUClipExecutionPlan.NoClip)
                CoverageMaskMaterializationInput(
                    fixture.plan,
                    fixture.encoderPlan,
                    fixture.resources,
                    fixture.generationSeal,
                )
            },
            Scenario("live-consumer-semantic") { fixture ->
                val consumer = fixture.plan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
                    .flatMap(GPUFrameStep.RenderPassStep::drawPackets)
                    .first { it.role == GPUDrawPacketRole.Shading }
                CoverageMaskMaterializationInput(
                    fixture.plan.replacingPacket(consumer, consumer.withSemantic(semantic(consumer))),
                    fixture.encoderPlan,
                    fixture.resources,
                    fixture.generationSeal,
                )
            },
            Scenario("same-content-consumer-semantic-instance") { fixture ->
                val consumer = fixture.plan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
                    .flatMap(GPUFrameStep.RenderPassStep::drawPackets)
                    .first { it.role == GPUDrawPacketRole.Shading }
                val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(consumer.semanticPayload)
                CoverageMaskMaterializationInput(
                    fixture.plan.replacingPacket(
                        consumer,
                        consumer.withSemantic(semantic.sameContentClone()),
                    ),
                    fixture.encoderPlan,
                    fixture.resources,
                    fixture.generationSeal,
                )
            },
            Scenario("uniform-size") { fixture ->
                val uniform = fixture.plan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
                    .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
                    .single { it.role == GPUFrameResourceRole.UniformData }
                val descriptor = assertIs<org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor>(
                    uniform.descriptor,
                )
                CoverageMaskMaterializationInput(
                    fixture.plan.withUniformPreparation(
                        descriptor.byteSize + descriptor.alignmentBytes,
                        descriptor.alignmentBytes,
                    ),
                    fixture.encoderPlan,
                    fixture.resources,
                    fixture.generationSeal,
                )
            },
            Scenario("scene-byte-size") { fixture ->
                CoverageMaskMaterializationInput(
                    fixture.plan.withPreparation(GPUFrameResourceRole.SceneTarget) { request ->
                        GPUResourcePreparationRequest(
                            request.resource,
                            request.descriptor,
                            request.role,
                            request.usages,
                            request.lifetime,
                            request.byteSize + 4L,
                            request.diagnosticLabel,
                        )
                    },
                    fixture.encoderPlan,
                    fixture.resources,
                    fixture.generationSeal,
                )
            },
            Scenario("mask-usage") { fixture ->
                CoverageMaskMaterializationInput(
                    fixture.plan.withPreparation(GPUFrameResourceRole.ClipMask) { request ->
                        GPUResourcePreparationRequest(
                            request.resource,
                            request.descriptor,
                            request.role,
                            setOf(org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding),
                            request.lifetime,
                            request.byteSize,
                            request.diagnosticLabel,
                        )
                    },
                    fixture.encoderPlan,
                    fixture.resources,
                    fixture.generationSeal,
                )
            },
            Scenario("vertex-lifetime") { fixture ->
                CoverageMaskMaterializationInput(
                    fixture.plan.withPreparation(GPUFrameResourceRole.VertexData) { request ->
                        GPUResourcePreparationRequest(
                            request.resource,
                            request.descriptor,
                            request.role,
                            request.usages,
                            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime.PassLocal,
                            request.byteSize,
                            request.diagnosticLabel,
                        )
                    },
                    fixture.encoderPlan,
                    fixture.resources,
                    fixture.generationSeal,
                )
            },
            Scenario("consumer-load-store") { fixture ->
                val render = fixture.plan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
                    .first { it.drawPackets.single().role == GPUDrawPacketRole.Shading }
                CoverageMaskMaterializationInput(
                    fixture.plan.replacingStep(
                        render,
                        render.withLoadStore(
                            org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan(
                                "load",
                                org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan.Store,
                            ),
                        ),
                    ),
                    fixture.encoderPlan,
                    fixture.resources,
                    fixture.generationSeal,
                )
            },
            Scenario("missing-prepared-resource") { fixture ->
                CoverageMaskMaterializationInput(
                    fixture.plan,
                    fixture.encoderPlan,
                    GPUPreparedResourceSet(
                        fixture.resources.ordinaryResources.dropLast(1),
                        fixture.resources.outputOwnedReadbacks,
                    ),
                    fixture.generationSeal,
                )
            },
            Scenario("missing-mask-generation") { fixture ->
                val mask = fixture.plan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
                    .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
                    .single { it.role == GPUFrameResourceRole.ClipMask }
                CoverageMaskMaterializationInput(
                    fixture.plan,
                    fixture.encoderPlan,
                    fixture.resources,
                    GPUPreparedGenerationSeal(
                        fixture.generationSeal.deviceGeneration,
                        fixture.generationSeal.targetGeneration,
                        fixture.generationSeal.resourceGenerations - mask.resource,
                        fixture.generationSeal.capabilitySealHash,
                    ),
                )
            },
        )

        scenarios.forEach { scenario ->
            val fixture = fixture(routeShape = RouteShape.CoverageMask, useRealPreflight = true)
            val input = scenario.mutate(fixture)
            fixture.native.events.clear()
            fixture.native.writeBufferCalls.clear()
            val cacheBefore = fixture.cache.counters()
            val poolSlotsBefore = framePoolSlotCount(fixture.cache)
            val materializer = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
                fixture.native.device,
                fixture.native.queue,
                fixture.target,
                fixture.cache,
                fixture.limits,
            )

            val result = materializer.materializeReusable(
                input.plan,
                input.encoderPlan,
                input.resources,
                input.generationSeal,
            )

            assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(result, scenario.label)
            assertEquals(emptyList(), fixture.native.events, scenario.label)
            assertEquals(emptyList(), fixture.native.writeBufferCalls, scenario.label)
            assertEquals(cacheBefore, fixture.cache.counters(), scenario.label)
            assertEquals(poolSlotsBefore, framePoolSlotCount(fixture.cache), scenario.label)
            materializer.close()
            fixture.close()
        }
    }

    @Test
    fun `coverage mask sealed corruption matrix refuses on a cold cache before native action`() {
        data class Scenario(val label: String, val mutate: (Fixture) -> Unit)
        val scenarios = listOf(
            Scenario("missing-seal") { fixture ->
                setPrivateField(
                    fixture.encoderPlan.scopes.first(),
                    "corePrimitiveCoverageMaskPreparedRouteSeal",
                    GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Missing,
                )
            },
            Scenario("scope-order") { fixture ->
                setPrivateField(fixture.encoderPlan, "scopes", fixture.encoderPlan.scopes.reversed())
            },
            Scenario("packet-ids") { fixture ->
                setPrivateField(fixture.encoderPlan.scopes.first(), "sourcePacketIds", emptyList<Any>())
            },
            Scenario("consumer-token") { fixture ->
                val seal = assertIs<GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer>(
                    fixture.encoderPlan.scopes.last().corePrimitiveCoverageMaskPreparedRouteSeal,
                )
                setPrivateField(seal, "dependencyFromPreviousConsumerToken", "forged.consumer.token")
            },
            Scenario("last-consumer") { fixture ->
                val seal = assertIs<GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer>(
                    fixture.encoderPlan.scopes.last().corePrimitiveCoverageMaskPreparedRouteSeal,
                )
                setPrivateField(seal, "isLastConsumer", false)
            },
            Scenario("aligned-wrong-offset") { fixture ->
                val seal = assertIs<GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer>(
                    fixture.encoderPlan.scopes.first().corePrimitiveCoverageMaskPreparedRouteSeal,
                )
                setPrivateField(
                    seal,
                    "uniformSlice",
                    seal.uniformSlice.copy(alignedOffset = seal.uniformSlice.alignedOffset + 256L),
                )
            },
            Scenario("geometry-slice") { fixture ->
                val scope = fixture.encoderPlan.scopes.first {
                    it.corePrimitiveCoverageMaskPreparedRouteSeal is
                        GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer
                }
                val seal = assertIs<GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer>(
                    scope.corePrimitiveCoverageMaskPreparedRouteSeal,
                )
                setPrivateField(
                    seal,
                    "geometrySlice",
                    seal.geometrySlice.copy(firstIndex = seal.geometrySlice.firstIndex + 1),
                )
            },
            Scenario("command-authority") { fixture ->
                setPrivateField(fixture.encoderPlan.scopes.first(), "passCommandStream", null)
            },
            Scenario("native-keys") { fixture ->
                val scope = fixture.encoderPlan.scopes.first()
                setPrivateField(scope, "nativeOperandKeys", scope.nativeOperandKeys.dropLast(1))
            },
            Scenario("uniform-padding") { fixture ->
                val seal = assertIs<GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer>(
                    fixture.encoderPlan.scopes.first().corePrimitiveCoverageMaskPreparedRouteSeal,
                ).slabAuthority.uniformSlabSeal
                val packed = privateFieldValue<ByteArray>(seal, "packedBytesSnapshot")
                packed[64] = 1
            },
        )

        scenarios.forEach { scenario ->
            val fixture = fixture(routeShape = RouteShape.CoverageMask, useRealPreflight = true)
            scenario.mutate(fixture)
            fixture.native.events.clear()
            fixture.native.writeBufferCalls.clear()
            val cacheBefore = fixture.cache.counters()
            val poolSlotsBefore = framePoolSlotCount(fixture.cache)

            val result = fixture.materializeCoreResult()

            assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(result, scenario.label)
            assertEquals(emptyList(), fixture.native.events, scenario.label)
            assertEquals(emptyList(), fixture.native.writeBufferCalls, scenario.label)
            assertEquals(cacheBefore, fixture.cache.counters(), scenario.label)
            assertEquals(poolSlotsBefore, framePoolSlotCount(fixture.cache), scenario.label)
            fixture.close()
        }
    }

    @Test
    fun `coverage mask revalidates every live consumer authority on a cold cache`() {
        data class Scenario(val label: String, val mutate: (Fixture, GPUDrawPacket) -> Unit)
        fun firstConsumer(fixture: Fixture): GPUDrawPacket = fixture.plan.steps
            .filterIsInstance<GPUFrameStep.RenderPassStep>()
            .flatMap(GPUFrameStep.RenderPassStep::drawPackets)
            .first { it.role == GPUDrawPacketRole.Shading }
        fun firstConsumerSlot(fixture: Fixture) =
            assertIs<GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer>(
                fixture.encoderPlan.scopes.first().corePrimitiveCoverageMaskPreparedRouteSeal,
            ).slabAuthority.uniformSlabSeal.consumerSlots.first()

        val forgedPipelineKey = "pipeline.forged.coverage-mask-consumer"
        val scenarios = listOf(
            Scenario("slot-structural-key") { fixture, _ ->
                val slot = firstConsumerSlot(fixture)
                val producerStructuralKey = assertIs<
                    GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer
                    >(
                    fixture.encoderPlan.scopes.first().corePrimitiveCoverageMaskPreparedRouteSeal,
                ).slabAuthority.uniformSlabSeal.producerSlots.first().structuralPipelineKey
                setPrivateField(
                    slot,
                    "structuralPipelineKey",
                    producerStructuralKey,
                )
            },
            Scenario("slot-render-pipeline-key") { fixture, _ ->
                setPrivateField(firstConsumerSlot(fixture), "renderPipelineKey", forgedPipelineKey)
            },
            Scenario("slot-binding-layout-hash") { fixture, _ ->
                setPrivateField(firstConsumerSlot(fixture), "bindingLayoutHash", "layout.forged")
            },
            Scenario("packet-render-pipeline-key") { _, packet ->
                setPrivateField(packet, "renderPipelineKey", forgedPipelineKey)
            },
            Scenario("packet-binding-layout-hash") { _, packet ->
                setPrivateField(packet, "bindingLayoutHash", "layout.forged")
            },
            Scenario("packet-uniform-slot") { _, packet ->
                val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(packet.semanticPayload)
                setPrivateField(
                    packet,
                    "uniformSlot",
                    requireNotNull(semantic.payloadRef.uniformSlot).copy(byteOffset = 4L),
                )
            },
            Scenario("packet-resource-slot") { _, packet ->
                val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(packet.semanticPayload)
                val forged = semantic.payloadRef.resourceSlot?.copy(bindingIndex = 7)
                    ?: GPUResourceBindingSlot(
                        GPUPayloadSlotID("slot.forged.coverage-mask"),
                        GPUPayloadFingerprint("fingerprint.forged.coverage-mask"),
                        7,
                    )
                setPrivateField(packet, "resourceSlot", forged)
            },
            Scenario("packet-resource-generation") { _, packet ->
                setPrivateField(packet, "resourceGeneration", packet.resourceGeneration + 1L)
            },
            Scenario("packet-clip-coverage-plan") { _, packet ->
                val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(packet.semanticPayload)
                val forged = if (semantic.clipCoveragePlan == GPUClipCoveragePlan.NoClip) {
                    GPUClipCoveragePlan.Refused("forged.coverage-mask.consumer")
                } else {
                    GPUClipCoveragePlan.NoClip
                }
                setPrivateField(packet, "clipCoveragePlan", forged)
            },
            Scenario("packet-frame-provenance") { _, packet ->
                val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(packet.semanticPayload)
                val forged = if (semantic.frameProvenance == GPUFrameProvenance.None) {
                    GPUFrameProvenance.GmContent
                } else {
                    GPUFrameProvenance.None
                }
                setPrivateField(packet, "frameProvenance", forged)
            },
            Scenario("authority-structural-key") { fixture, packet ->
                val authority = requireNotNull(packet.corePrimitivePreparedAuthority)
                val producerStructuralKey = assertIs<
                    GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer
                    >(
                    fixture.encoderPlan.scopes.first().corePrimitiveCoverageMaskPreparedRouteSeal,
                ).slabAuthority.uniformSlabSeal.producerSlots.first().structuralPipelineKey
                setPrivateField(
                    authority,
                    "structuralPipelineKey",
                    producerStructuralKey,
                )
            },
            Scenario("authority-render-pipeline-key") { _, packet ->
                setPrivateField(
                    requireNotNull(packet.corePrimitivePreparedAuthority),
                    "renderPipelineKey",
                    forgedPipelineKey,
                )
            },
            Scenario("authority-coverage-slab") { _, packet ->
                setPrivateField(
                    requireNotNull(packet.corePrimitivePreparedAuthority),
                    "coverageMaskUniformSlabSeal",
                    null,
                )
            },
            Scenario("authority-foreign-uniform-seal") { fixture, packet ->
                val coverageSlab = assertIs<GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer>(
                    fixture.encoderPlan.scopes.first().corePrimitiveCoverageMaskPreparedRouteSeal,
                ).slabAuthority.uniformSlabSeal
                val foreignSeal = GPUCorePrimitiveUniformSlabSeal(
                    coverageSlab.plan,
                    coverageSlab.plan.slots.indices.toList(),
                    coverageSlab.packedBytesSnapshot(),
                )
                setPrivateField(
                    requireNotNull(packet.corePrimitivePreparedAuthority),
                    "uniformSlabSeal",
                    foreignSeal,
                )
            },
            Scenario("packet-target-state") { _, packet ->
                setPrivateField(packet, "targetStateHash", "target.forged")
            },
            Scenario("packet-vertex-source") { _, packet ->
                setPrivateField(packet, "vertexSourceLabel", "vertex.forged")
            },
            Scenario("packet-scissor-authority") { _, packet ->
                setPrivateField(packet, "scissorBoundsHash", "scissor.forged")
            },
        )

        scenarios.forEach { scenario ->
            val fixture = fixture(routeShape = RouteShape.CoverageMask, useRealPreflight = true)
            scenario.mutate(fixture, firstConsumer(fixture))
            fixture.native.events.clear()
            fixture.native.writeBufferCalls.clear()
            val cacheBefore = fixture.cache.counters()
            val poolSlotsBefore = framePoolSlotCount(fixture.cache)

            val result = fixture.materializeCoreResult()

            assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(result, scenario.label)
            assertEquals(emptyList(), fixture.native.events, scenario.label)
            assertEquals(emptyList(), fixture.native.writeBufferCalls, scenario.label)
            assertEquals(cacheBefore, fixture.cache.counters(), scenario.label)
            assertEquals(poolSlotsBefore, framePoolSlotCount(fixture.cache), scenario.label)
            fixture.close()
        }
    }

    @Test
    fun `coverage mask rejects shifted scene readback before native action`() {
        val fixture = fixture(
            readback = true,
            routeShape = RouteShape.CoverageMask,
            useRealPreflight = true,
        )
        val readback = fixture.plan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>().single()
        val shifted = GPUFrameStep.ReadbackCopyStep(
            readback.source,
            readback.staging,
            GPUFrameReadbackRequest(
                readback.request.requestId,
                GPUPixelBounds(1, 0, TARGET.width + 1, TARGET.height),
                readback.request.pixelFormat,
                readback.request.outputColorInterpretation,
            ),
            readback.sourceTaskIds,
        )
        fixture.native.events.clear()

        val result = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            fixture.limits,
        ).materializeReusable(
            fixture.plan.replacingStep(readback, shifted),
            fixture.encoderPlan,
            fixture.resources,
            fixture.generationSeal,
        )

        assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(result)
        assertEquals(emptyList(), fixture.native.events)
        fixture.close()
    }

    @Test
    fun `coverage mask rejects forged readback layout before native action`() {
        val fixture = fixture(
            readback = true,
            routeShape = RouteShape.CoverageMask,
            useRealPreflight = true,
        )
        val output = fixture.resources.outputOwnedReadbacks.single()
        setPrivateField(
            output.layout,
            "paddedBytesPerRow",
            output.layout.paddedBytesPerRow + 256L,
        )
        fixture.native.events.clear()

        val result = fixture.materializeCoreResult()

        assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(result)
        assertEquals(emptyList(), fixture.native.events)
        fixture.close()
    }

    @Test
    fun `coverage mask readback authority rejects foreign evidence on a cold cache`() {
        data class Scenario(
            val label: String,
            val mutate: (Fixture) -> CoverageMaskMaterializationInput,
        )
        fun input(
            fixture: Fixture,
            plan: GPUFramePlan = fixture.plan,
            encoderPlan: GPUCommandEncoderPlan = fixture.encoderPlan,
            resources: GPUPreparedResourceSet = fixture.resources,
        ) = CoverageMaskMaterializationInput(
            plan,
            encoderPlan,
            resources,
            fixture.generationSeal,
        )
        fun copiedLease(
            lease: GPUReadbackStagingLease,
            resourceRef: GPUBufferResourceRef = lease.resourceRef,
            deviceGeneration: GPUDeviceGenerationID = lease.deviceGeneration,
            usages: Set<org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage> = lease.usages,
        ) = GPUReadbackStagingLease(
            reservationId = lease.reservationId,
            ownerScope = lease.ownerScope,
            deviceGeneration = deviceGeneration,
            resourceRef = resourceRef,
            reservationOrdinal = lease.reservationOrdinal,
            acquisitionToken = lease.acquisitionToken,
            logicalMinimumBytes = lease.logicalMinimumBytes,
            backingBufferBytes = lease.backingBufferBytes,
            usages = usages,
        )
        fun withOutput(
            fixture: Fixture,
            transform: (GPUPreparedReadbackOutput) -> GPUPreparedReadbackOutput,
        ): CoverageMaskMaterializationInput {
            val output = fixture.resources.outputOwnedReadbacks.single()
            return input(
                fixture,
                resources = GPUPreparedResourceSet(
                    fixture.resources.ordinaryResources,
                    listOf(transform(output)),
                ),
            )
        }

        val scenarios = listOf(
            Scenario("staging-alignment") { fixture ->
                input(
                    fixture,
                    plan = fixture.plan.withPreparation(GPUFrameResourceRole.ReadbackStaging) { request ->
                        val descriptor = assertIs<
                            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
                            >(request.descriptor)
                        GPUResourcePreparationRequest(
                            request.resource,
                            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor(
                                descriptor.byteSize,
                                8L,
                            ),
                            request.role,
                            request.usages,
                            request.lifetime,
                            request.byteSize,
                            request.diagnosticLabel,
                        )
                    },
                )
            },
            Scenario("foreign-staging-lease-resource") { fixture ->
                withOutput(fixture) { output ->
                    output.copy(
                        stagingLease = copiedLease(
                            output.stagingLease,
                            resourceRef = GPUBufferResourceRef("buffer.foreign.readback"),
                        ),
                    )
                }
            },
            Scenario("foreign-staging-lease-generation") { fixture ->
                withOutput(fixture) { output ->
                    output.copy(
                        stagingLease = copiedLease(
                            output.stagingLease,
                            deviceGeneration = GPUDeviceGenerationID(
                                output.stagingLease.deviceGeneration.value + 1L,
                            ),
                        ),
                    )
                }
            },
            Scenario("foreign-staging-lease-usages") { fixture ->
                withOutput(fixture) { output ->
                    output.copy(
                        stagingLease = copiedLease(
                            output.stagingLease,
                            usages = setOf(
                                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.MapRead,
                            ),
                        ),
                    )
                }
            },
            Scenario("foreign-output-concrete-resource") { fixture ->
                withOutput(fixture) { output ->
                    output.copy(
                        concreteResource = GPUPreparedConcreteResourceRef.Buffer(
                            GPUBufferResourceRef("buffer.foreign.output"),
                        ),
                    )
                }
            },
            Scenario("foreign-readback-source-binding-key") { fixture ->
                val scope = fixture.encoderPlan.scopes.single {
                    it.operationKind == GPUEncoderOperationKind.Readback
                }
                setPrivateField(
                    scope,
                    "nativeOperandKeys",
                    scope.nativeOperandKeys.mapIndexed { index, key ->
                        if (index == 0) key.copy(bindingKey = gpuPreparedNativeBindingKey("foreign.source"))
                        else key
                    },
                )
                input(fixture)
            },
            Scenario("foreign-readback-destination-binding-key") { fixture ->
                val scope = fixture.encoderPlan.scopes.single {
                    it.operationKind == GPUEncoderOperationKind.Readback
                }
                setPrivateField(
                    scope,
                    "nativeOperandKeys",
                    scope.nativeOperandKeys.mapIndexed { index, key ->
                        if (index == 1) key.copy(bindingKey = gpuPreparedNativeBindingKey("foreign.destination"))
                        else key
                    },
                )
                input(fixture)
            },
            Scenario("overflow-sized-readback-layout") { fixture ->
                withOutput(fixture) { output ->
                    setPrivateField(output.layout, "paddedBytesPerRow", Long.MAX_VALUE)
                    setPrivateField(output.layout, "totalBufferBytes", Long.MAX_VALUE)
                    output
                }
            },
        )

        scenarios.forEach { scenario ->
            val fixture = fixture(
                readback = true,
                routeShape = RouteShape.CoverageMask,
                useRealPreflight = true,
            )
            val materializationInput = scenario.mutate(fixture)
            fixture.native.events.clear()
            fixture.native.writeBufferCalls.clear()
            val cacheBefore = fixture.cache.counters()
            val poolSlotsBefore = framePoolSlotCount(fixture.cache)
            val materializer = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
                fixture.native.device,
                fixture.native.queue,
                fixture.target,
                fixture.cache,
                fixture.limits,
            )

            val result = materializer.materializeReusable(
                materializationInput.plan,
                materializationInput.encoderPlan,
                materializationInput.resources,
                materializationInput.generationSeal,
            )

            assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(result, scenario.label)
            assertEquals(emptyList(), fixture.native.events, scenario.label)
            assertEquals(emptyList(), fixture.native.writeBufferCalls, scenario.label)
            assertEquals(cacheBefore, fixture.cache.counters(), scenario.label)
            assertEquals(poolSlotsBefore, framePoolSlotCount(fixture.cache), scenario.label)
            materializer.close()
            fixture.close()
        }
    }

    @Test
    fun `coverage mask upload rollback completion reuse and uncertainty quarantine preserve slot lifecycle`() {
        val fixture = fixture(routeShape = RouteShape.CoverageMask, useRealPreflight = true)
        val maskTextureLabel = "Kanvas.session.corePrimitive.framePool.coverageMask"
        val maskViewLabel = "$maskTextureLabel.view"
        fixture.native.events.clear()
        fixture.native.fail("writeBuffer", 2)

        val refused = fixture.materializeCoreResult()

        assertEquals(
            "failed.native-core-primitive.coverage-mask-materialization",
            assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(refused).code,
        )
        val rolledBack = fixture.materializeCore()
        val firstMask = rolledBack.draft.payload.scopeOperands
            .filterIsInstance<GPUPreparedNativeScopeOperand.Render>().first().pass.colorTarget.view
        assertSame(fixture.native.createdHandles(maskViewLabel).single(), firstMask)
        assertTrue(rolledBack.draft.disposeBeforeRegistration())

        val completed = fixture.materializeCore()
        assertSame(
            firstMask,
            completed.draft.payload.scopeOperands.filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
                .first().pass.colorTarget.view,
        )
        val adapter = GPURuntimeResourceAdapter()
        val completedRegistration = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            adapter.registerPreparedNativeFrameDraft(completed.draft),
        )
        assertIs<GPUPreparedNativeFrameBindingResult.Ready>(
            completedRegistration.ownership.bindLateSurface(
                null,
                GPUPreparedNativeFrameLateSurfaceBinding.NotRequired,
            ),
        )
        assertIs<GPUPreparedNativeFrameConsumption.Consumed>(
            completedRegistration.ownership.consume(completed.draft.payload.identity),
        )
        assertTrue(completedRegistration.ownership.markSubmitted())
        assertTrue(completedRegistration.ownership.releaseAfterCompletion())

        val uncertain = fixture.materializeCore()
        val uncertainRegistration = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            adapter.registerPreparedNativeFrameDraft(uncertain.draft),
        )
        assertTrue(uncertainRegistration.ownership.quarantine())

        val replacement = fixture.materializeCore()
        val replacementMask = replacement.draft.payload.scopeOperands
            .filterIsInstance<GPUPreparedNativeScopeOperand.Render>().first().pass.colorTarget.view
        assertNotSame(firstMask, replacementMask)
        assertEquals(2, fixture.native.createdHandles(maskTextureLabel).size)
        assertEquals(2, fixture.native.createdHandles(maskViewLabel).size)
        assertTrue(replacement.draft.disposeBeforeRegistration())
        adapter.close()
        fixture.close()
    }

    @Test
    fun `each coverage mask upload failure rolls back the same pooled mask slot`() {
        (1..3).forEach { ordinal ->
            val fixture = fixture(routeShape = RouteShape.CoverageMask, useRealPreflight = true)
            val maskViewLabel = "Kanvas.session.corePrimitive.framePool.coverageMask.view"
            fixture.native.events.clear()
            fixture.native.fail("writeBuffer", ordinal)

            val refused = fixture.materializeCoreResult()

            assertEquals(
                "failed.native-core-primitive.coverage-mask-materialization",
                assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(refused).code,
                "upload $ordinal",
            )
            val createdMask = fixture.native.createdHandles(maskViewLabel).single()
            val recovered = fixture.materializeCore()
            assertSame(
                createdMask,
                recovered.draft.payload.scopeOperands
                    .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
                    .first().pass.colorTarget.view,
                "upload $ordinal",
            )
            assertTrue(recovered.draft.disposeBeforeRegistration())
            fixture.close()
        }
    }

    @Test
    fun `coverage mask staging failure rolls back mask slot before retry`() {
        val fixture = fixture(
            readback = true,
            routeShape = RouteShape.CoverageMask,
            useRealPreflight = true,
        )
        val maskViewLabel = "Kanvas.session.corePrimitive.framePool.coverageMask.view"
        fixture.native.events.clear()
        fixture.native.fail("createBuffer", 4)

        val refused = fixture.materializeCoreResult()

        assertEquals(
            "failed.native-core-primitive.coverage-mask-materialization",
            assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(refused).code,
        )
        val createdMask = fixture.native.createdHandles(maskViewLabel).single()
        val recovered = fixture.materializeCore()
        assertSame(
            createdMask,
            recovered.draft.payload.scopeOperands
                .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
                .first().pass.colorTarget.view,
        )
        assertTrue(recovered.draft.disposeBeforeRegistration())
        fixture.close()
    }

    @Test
    fun `prepared clip stencil materializes producer and consumers with one shared attachment`() {
        val fixture = fixture(
            readback = true,
            routeShape = RouteShape.ClipStencil,
            useRealPreflight = true,
        )

        val materialized = fixture.materializeCore()
        val renders = materialized.draft.payload.scopeOperands
            .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
        assertEquals(3, renders.size)
        val producer = renders.first()
        val consumers = renders.drop(1)
        val depthStencilTargets = renders.map { requireNotNull(it.pass.depthStencilTarget).view }

        assertEquals(1, distinctIdentityCount(depthStencilTargets))
        assertEquals(GPUPreparedNativeLoadOperation.Load, producer.pass.loadOperation)
        assertEquals(GPUPreparedNativeLoadOperation.Clear, producer.pass.stencilLoadOperation)
        assertEquals(GPUPreparedNativeStoreOperation.Store, producer.pass.stencilStoreOperation)
        assertFalse(producer.pass.stencilReadOnly)
        assertTrue(producer.commands.none { it is GPUPreparedNativeRenderCommand.SetBindGroup })
        assertEquals(1, producer.commands.filterIsInstance<GPUPreparedNativeRenderCommand.DrawIndexed>().size)
        assertEquals(listOf(0u), producer.commands
            .filterIsInstance<GPUPreparedNativeRenderCommand.SetStencilReference>()
            .map { it.reference })

        assertEquals(
            listOf(GPUPreparedNativeLoadOperation.Clear, GPUPreparedNativeLoadOperation.Load),
            consumers.map { it.pass.loadOperation },
        )
        consumers.forEach { consumer ->
            assertTrue(consumer.pass.stencilReadOnly)
            assertEquals(null, consumer.pass.stencilLoadOperation)
            assertEquals(null, consumer.pass.stencilStoreOperation)
            assertEquals(1, consumer.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetBindGroup>().size)
            assertEquals(1, consumer.commands.filterIsInstance<GPUPreparedNativeRenderCommand.DrawIndexed>().size)
            assertEquals(listOf(0u), consumer.commands
                .filterIsInstance<GPUPreparedNativeRenderCommand.SetStencilReference>()
                .map { it.reference })
        }
        assertEquals(3, fixture.native.writeBufferCalls.size)
        assertEquals(1, fixture.native.events.count {
            it == "createTexture:Kanvas.session.corePrimitive.framePool.clipDepthStencil"
        })
        assertIs<GPUPreparedNativeScopeOperand.Readback>(materialized.draft.payload.scopeOperands.last())

        assertTrue(materialized.draft.disposeBeforeRegistration())
        fixture.close()
    }

    @Test
    fun `prepared clip stencil accepts a pooled slot that retains an independent path attachment`() {
        val pathFixture = fixture(routeShape = RouteShape.PathOnly)
        val clipFixture = fixture(routeShape = RouteShape.ClipStencil, useRealPreflight = true)
        val clipOnSharedPool = clipFixture.copy(
            native = pathFixture.native,
            target = pathFixture.target,
            cache = pathFixture.cache,
        )

        val path = pathFixture.materializeCore()
        val pathView = path.draft.payload.scopeOperands
            .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
            .single().pass.depthStencilTarget!!.view
        assertTrue(path.draft.disposeBeforeRegistration())

        val clip = clipOnSharedPool.materializeCore()
        val clipViews = clip.draft.payload.scopeOperands
            .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
            .map { it.pass.depthStencilTarget!!.view }
        assertEquals(1, distinctIdentityCount(clipViews))
        assertNotSame(pathView, clipViews.first())
        assertEquals(1, pathFixture.native.events.count {
            it == "createTexture:Kanvas.session.corePrimitive.framePool.pathDepthStencil"
        })
        assertEquals(1, pathFixture.native.events.count {
            it == "createTexture:Kanvas.session.corePrimitive.framePool.clipDepthStencil"
        })
        assertTrue(clip.draft.disposeBeforeRegistration())

        clipFixture.close()
        pathFixture.close()
    }

    @Test
    fun `second prepared clip frame reuses its clip view and clears stencil again`() {
        val fixture = fixture(routeShape = RouteShape.ClipStencil, useRealPreflight = true)

        val first = fixture.materializeCore()
        val firstRenders = first.draft.payload.scopeOperands
            .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
        val firstClipView = firstRenders.first().pass.depthStencilTarget!!.view
        assertNotSame(firstRenders.first().pass.colorTarget.view, firstClipView)
        assertEquals(GPUPreparedNativeLoadOperation.Clear, firstRenders.first().pass.stencilLoadOperation)
        assertEquals(0u, firstRenders.first().pass.stencilClearValue)
        assertTrue(first.draft.disposeBeforeRegistration())

        val second = fixture.materializeCore()
        val secondRenders = second.draft.payload.scopeOperands
            .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
        assertSame(firstClipView, secondRenders.first().pass.depthStencilTarget!!.view)
        assertEquals(GPUPreparedNativeLoadOperation.Clear, secondRenders.first().pass.stencilLoadOperation)
        assertEquals(0u, secondRenders.first().pass.stencilClearValue)
        assertEquals(1, fixture.native.events.count {
            it == "createTexture:Kanvas.session.corePrimitive.framePool.clipDepthStencil"
        })
        assertTrue(second.draft.disposeBeforeRegistration())
        fixture.close()
    }

    @Test
    fun `even odd inverse concave clip with a hole keeps partial scissor and two translucent consumers`() {
        val partialScissor = GPUPixelBounds(1, 1, 14, 14)
        val clipPlan = nativeClipStencilPlan(
            fillRule = GPUClipFillRule.EvenOdd,
            inverseFill = true,
            vertices = listOf(
                2f, 2f,
                14f, 2f,
                9f, 7f,
                14f, 14f,
                2f, 14f,
                6f, 8f,
                6f, 6f,
                10f, 6f,
                10f, 10f,
                6f, 10f,
            ),
            contourStarts = listOf(0, 6),
            scissor = partialScissor,
        )
        val fixture = fixture(
            routeShape = RouteShape.ClipStencil,
            useRealPreflight = true,
            clipStencilPlan = clipPlan,
        )

        val materialized = fixture.materializeCore()
        val renders = materialized.draft.payload.scopeOperands
            .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
        assertEquals(3, renders.size)
        assertEquals(
            listOf(
                "Kanvas.session.corePrimitive.pipeline.ClipStencilProducerEvenOdd",
                "Kanvas.session.corePrimitive.pipeline.ClipStencilConsumerInverse",
            ),
            fixture.native.renderPipelineDescriptors.map { it.label },
        )
        assertTrue(renders.first().commands.none {
            it is GPUPreparedNativeRenderCommand.SetBindGroup
        })
        assertTrue(renders.drop(1).all { render ->
            render.semanticPayloads.single().let {
                (it as GPUDrawSemanticPayload.CorePrimitive).premultipliedRgba[3] == 0.5f
            } && render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetScissor>()
                .single().let { scissor ->
                    scissor.x == partialScissor.left && scissor.y == partialScissor.top &&
                        scissor.width == partialScissor.width && scissor.height == partialScissor.height
                }
        })
        val producerSeal = assertIs<GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer>(
            fixture.encoderPlan.scopes.first().corePrimitiveClipStencilPreparedRouteSeal,
        )
        assertEquals(3, producerSeal.geometryArena.slices.size)
        assertTrue(producerSeal.geometrySlice.vertexCount > 4)

        assertTrue(materialized.draft.disposeBeforeRegistration())
        fixture.close()
    }

    @Test
    fun `direct core materializer integrity gate performs no canonical hash work`() {
        val source = File(
            "src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/" +
                "GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt",
        ).readText()

        assertTrue(source.contains("semantic.hasStructuralIntegrity()"))
        assertFalse(source.contains("hasCanonicalHashIntegrity()"))
        assertFalse(source.contains("MessageDigest"))
        assertFalse(source.contains("SHA-256"))
        assertFalse(source.contains("step !in renderEntries.map(RenderEntry::render)"))
        assertTrue(source.contains("retainedCoverageMaskRenderSteps"))
    }

    @Test
    fun `surface split retains the exact core route seal and accepted geometry instances`() {
        val fixture = fixture()
        val coreScope = fixture.encoderPlan.scopes.single()
        val surfaceScope = GPUCommandEncoderScopePlan(
            sourceStepIndex = fixture.plan.steps.size + 1,
            operationKind = GPUEncoderOperationKind.SurfaceBlit,
            sourceTaskIds = listOf(GPUTaskID("task.surface.blit")),
            facadeOperationClasses = listOf("beginRenderPass", "surfaceBlit", "endRenderPass"),
            targetGeneration = fixture.encoderPlan.targetGeneration,
            resourceGenerationLabels = listOf("GPUFrameTargetRef:target.core.proxy@1"),
        )
        val fullPlan = GPUCommandEncoderPlan.ordered(
            planId = fixture.encoderPlan.planId,
            contextIdentity = fixture.encoderPlan.contextIdentity,
            deviceGeneration = fixture.encoderPlan.deviceGeneration,
            targetGeneration = fixture.encoderPlan.targetGeneration,
            scopes = listOf(coreScope, surfaceScope),
        )

        val reusable = wgpu4kReusableEncoderPlanWithoutSurface(fullPlan)

        val retainedScope = reusable.scopes.single()
        assertSame(coreScope, retainedScope)
        assertSame(
            coreScope.corePrimitiveDirectNativeRouteSeal,
            retainedScope.corePrimitiveDirectNativeRouteSeal,
        )
        val originalRoutes = assertIs<GPUCorePrimitiveDirectNativeRouteSeal.Routes>(
            coreScope.corePrimitiveDirectNativeRouteSeal,
        )
        val retainedRoutes = assertIs<GPUCorePrimitiveDirectNativeRouteSeal.Routes>(
            retainedScope.corePrimitiveDirectNativeRouteSeal,
        )
        originalRoutes.routesByPacketId.forEach { (packetId, accepted) ->
            assertSame(accepted, retainedRoutes.routesByPacketId.getValue(packetId))
        }
        fixture.close()
    }

    @Test
    fun `refusal performs no native action`() {
        val fixture = fixture()
        val events = fixture.native.events
        events.clear()
        val materializer = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            fixture.limits,
        )

        val refused = materializer.materializeReusable(
            fixture.plan,
            fixture.encoderPlan,
            GPUPreparedResourceSet(emptyList(), emptyList()),
            fixture.generationSeal,
        )

        assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(refused)
        assertEquals(emptyList(), events)
        materializer.close()
        fixture.close()
    }

    @Test
    fun `two draws share one render pass one dynamic uniform slab and one bind group`() {
        val fixture = fixture()
        val events = fixture.native.events
        events.clear()
        val materializer = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            fixture.limits,
        )

        val result = materializer.materializeReusable(
                fixture.plan,
                fixture.encoderPlan,
                fixture.resources,
                fixture.generationSeal,
            )
        val materialized = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
            result,
            (result as? GPUPreparedNativeFramePayloadMaterialization.Refused)?.let { "${it.code}: ${it.message}" },
        )
        val renders = materialized.draft.payload.scopeOperands
            .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
        assertEquals(1, renders.size)
        val render = renders.single()
        val vertices = render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetVertexBuffer>()
        val indices = render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetIndexBuffer>()
        val setBindGroups = render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetBindGroup>()
        val bindGroups = setBindGroups.map(GPUPreparedNativeRenderCommand.SetBindGroup::bindGroup)
        val draws = render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.DrawIndexed>()
        assertEquals(1, render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetPipeline>().size)
        assertEquals(1, vertices.size)
        assertEquals(1, indices.size)
        assertEquals(0L, vertices.single().offset)
        assertEquals(64L, vertices.single().size)
        assertEquals(0L, indices.single().offset)
        assertEquals(48L, indices.single().size)
        assertEquals(8L, vertices.single().vertexStrideBytes)
        assertEquals(16L * 1024L, vertices.single().buffer.byteCapacity)
        assertEquals(4L * 1024L, indices.single().buffer.byteCapacity)
        assertEquals(listOf(0, 6), draws.map { it.drawCall.firstIndex })
        assertEquals(listOf(0, 4), draws.map { it.drawCall.baseVertex })
        assertEquals(listOf(4, 4), draws.map { it.drawCall.vertexCount })
        assertEquals(listOf(3, 3), draws.map { it.drawCall.maxLocalIndex })
        assertEquals(
            listOf("pipeline", "vertex", "index", "bind", "scissor", "draw", "bind", "scissor", "draw"),
            render.commands.map { command ->
                when (command) {
                    is GPUPreparedNativeRenderCommand.SetPipeline -> "pipeline"
                    is GPUPreparedNativeRenderCommand.SetVertexBuffer -> "vertex"
                    is GPUPreparedNativeRenderCommand.SetIndexBuffer -> "index"
                    is GPUPreparedNativeRenderCommand.SetBindGroup -> "bind"
                    is GPUPreparedNativeRenderCommand.SetScissor -> "scissor"
                    is GPUPreparedNativeRenderCommand.DrawIndexed -> "draw"
                    is GPUPreparedNativeRenderCommand.SetStencilReference -> "stencil"
                    is GPUPreparedNativeRenderCommand.Draw -> "non-indexed-draw"
                }
            },
        )
        assertEquals(listOf(listOf(0L), listOf(256L)), setBindGroups.map { it.dynamicOffsets })
        assertEquals(1, distinctIdentityCount(bindGroups.map { it.bindGroup }))
        assertTrue(bindGroups.all { it.ownership == GPUPreparedNativeOperandOwnership.Borrowed })
        assertTrue(vertices.all { it.buffer.ownership == GPUPreparedNativeOperandOwnership.Borrowed })
        assertTrue(indices.all { it.buffer.ownership == GPUPreparedNativeOperandOwnership.Borrowed })
        assertEquals(1, events.count { it == "createBuffer:Kanvas.session.corePrimitive.framePool.vertices" })
        assertEquals(1, events.count { it == "createBuffer:Kanvas.session.corePrimitive.framePool.indices" })
        assertEquals(1, events.count { it == "createBuffer:Kanvas.session.corePrimitive.framePool.uniforms" })
        assertEquals(1, events.count { it == "createBindGroup:Kanvas.session.corePrimitive.framePool.bindGroup0" })
        assertEquals(3, events.count { it == "writeBuffer" })
        val uniformBinding = requireNotNull(
            fixture.native.bindGroupLayoutDescriptors.single().entries.single().buffer,
        )
        assertTrue(uniformBinding.hasDynamicOffset)
        assertEquals(32uL, uniformBinding.minBindingSize)

        assertTrue(materialized.draft.disposeBeforeRegistration())
        assertEquals(0, fixture.native.closeCounts.getOrDefault(vertices[0].buffer.buffer, 0))
        assertEquals(0, fixture.native.closeCounts.getOrDefault(indices[0].buffer.buffer, 0))
        materializer.close()
        fixture.close()
        assertEquals(1, fixture.native.closeCounts.getOrDefault(vertices[0].buffer.buffer, 0))
        assertEquals(1, fixture.native.closeCounts.getOrDefault(indices[0].buffer.buffer, 0))
    }

    @Test
    fun `two analytic clips upload one exact uniform64 slab with one compatible dynamic bind group`() {
        val fixture = fixture(analyticClip = true, useRealPreflight = true)
        fixture.native.events.clear()

        val materialized = fixture.materializeCore()
        val render = materialized.draft.payload.scopeOperands
            .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
            .single()
        val commands = render.commands
        val bindGroups = commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetBindGroup>()
        val scissors = commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetScissor>()
        val pipelines = commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetPipeline>()
        val preparedPackets = fixture.plan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            .single().drawPackets
        val seals = preparedPackets.map { packet ->
            requireNotNull(packet.corePrimitivePreparedAuthority?.analyticClipUniformSeal)
        }
        val expectedUpload = ByteArray(seals.first().plan.totalBytes.toInt()).also { packed ->
            seals.forEach { seal ->
                seal.payloadBytesSnapshot().copyInto(packed, seal.alignedOffset.toInt())
            }
        }

        assertEquals(1, pipelines.size)
        assertEquals(listOf(listOf(0L), listOf(256L)), bindGroups.map { it.dynamicOffsets })
        assertEquals(1, distinctIdentityCount(bindGroups.map { it.bindGroup.bindGroup }))
        assertEquals(seals.map { it.conservativeScissor }, scissors.map {
            GPUPixelBounds(it.x, it.y, it.x + it.width, it.y + it.height)
        })
        assertEquals(3, fixture.native.writeBufferCalls.size)
        assertContentEquals(expectedUpload, fixture.native.writeBufferCalls.last().snapshot)
        val uniformLayout = fixture.native.bindGroupLayoutDescriptors.single().entries.single().buffer
        assertEquals(64uL, requireNotNull(uniformLayout).minBindingSize)
        assertEquals(null, render.pass.depthStencilTarget)

        assertTrue(materialized.draft.disposeBeforeRegistration())
        fixture.close()
    }

    @Test
    fun `analytic intersections depth two and four share one uniform160 pipeline bind group and slab`() {
        val fixture = fixture(analyticIntersection = true, useRealPreflight = true)
        fixture.native.events.clear()

        val materialized = fixture.materializeCore()
        val render = materialized.draft.payload.scopeOperands
            .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
            .single()
        val bindGroups = render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetBindGroup>()
        val scissors = render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetScissor>()
        val preparedPackets = fixture.plan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            .single().drawPackets
        val seals = preparedPackets.map { packet ->
            requireNotNull(packet.corePrimitivePreparedAuthority?.analyticIntersectionUniformSeal)
        }
        val expectedUpload = ByteArray(seals.first().plan.totalBytes.toInt()).also { packed ->
            seals.forEach { seal ->
                seal.payloadBytesSnapshot().copyInto(packed, seal.alignedOffset.toInt())
            }
        }

        assertEquals(listOf(2, 4), seals.map { it.elements.size })
        assertEquals(1, render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetPipeline>().size)
        assertEquals(listOf(listOf(0L), listOf(256L)), bindGroups.map { it.dynamicOffsets })
        assertEquals(1, distinctIdentityCount(bindGroups.map { it.bindGroup.bindGroup }))
        assertEquals(seals.map { it.conservativeScissor }, scissors.map {
            GPUPixelBounds(it.x, it.y, it.x + it.width, it.y + it.height)
        })
        assertEquals(3, fixture.native.writeBufferCalls.size)
        assertContentEquals(expectedUpload, fixture.native.writeBufferCalls.last().snapshot)
        val uniformLayout = fixture.native.bindGroupLayoutDescriptors.single().entries.single().buffer
        assertEquals(160uL, requireNotNull(uniformLayout).minBindingSize)
        assertEquals(null, render.pass.depthStencilTarget)
        assertTrue(fixture.native.textureDescriptors.none {
            it.format == GPUTextureFormat.Depth24PlusStencil8
        })

        assertTrue(materialized.draft.disposeBeforeRegistration())
        fixture.close()
    }

    @Test
    fun `typed uniform160 packet seal substitution refuses before every native action`() {
        val fixture = fixture(analyticIntersection = true, useRealPreflight = true)
        val render = fixture.plan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single()
        val first = render.drawPackets.first()
        val secondSeal = requireNotNull(
            render.drawPackets.last().corePrimitivePreparedAuthority?.analyticIntersectionUniformSeal,
        )
        val originalAuthority = requireNotNull(first.corePrimitivePreparedAuthority)
        val substituted = first.withPreparedAuthority(
            originalAuthority.copy(analyticIntersectionUniformSeal = secondSeal),
        )
        val plan = fixture.plan.replacingPacket(first, substituted)
        fixture.native.events.clear()

        val result = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            fixture.limits,
        ).materializeReusable(plan, fixture.encoderPlan, fixture.resources, fixture.generationSeal)

        assertEquals(
            "invalid.native-core-primitive.packet-authority",
            assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(result).code,
        )
        assertEquals(emptyList(), fixture.native.events)
        fixture.close()
    }

    @Test
    fun `typed uniform160 payload corruption refuses before every native action`() {
        val fixture = fixture(analyticIntersection = true, useRealPreflight = true)
        val render = fixture.plan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single()
        val first = render.drawPackets.first()
        val originalAuthority = requireNotNull(first.corePrimitivePreparedAuthority)
        val originalSeal = requireNotNull(originalAuthority.analyticIntersectionUniformSeal)
        val corruptedPayload = originalSeal.payloadBytesSnapshot().also { bytes ->
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0xff).toByte()
        }
        val corruptedSeal = org.graphiks.kanvas.gpu.renderer.passes
            .GPUCorePrimitiveAnalyticIntersectionUniformSeal(
                originalSeal.plan,
                originalSeal.slotIndex,
                originalSeal.commandId,
                originalSeal.packetId,
                originalSeal.clipCanonicalIdentity,
                originalSeal.elements,
                originalSeal.conservativeScissor,
                originalSeal.structuralPipelineKey,
                originalSeal.renderPipelineKey,
                originalSeal.bindingLayoutHash,
                originalSeal.resourceGeneration,
                corruptedPayload,
            )
        val corruptedPacket = first.withPreparedAuthority(
            originalAuthority.copy(analyticIntersectionUniformSeal = corruptedSeal),
        )
        val plan = fixture.plan.replacingPacket(first, corruptedPacket)
        val originalScope = fixture.encoderPlan.scopes.single()
        val originalRoutes = assertIs<GPUCorePrimitiveDirectNativeRouteSeal.Routes>(
            originalScope.corePrimitiveDirectNativeRouteSeal,
        )
        val originalPass = requireNotNull(originalRoutes.preparedPassSeal)
        val corruptedSeals = originalPass.analyticIntersectionUniformSeals.map { seal ->
            if (seal === originalSeal) corruptedSeal else seal
        }
        val corruptedPass = GPUCorePrimitiveDirectPreparedPassSeal(
            originalPass.structuralPipelineKey,
            uniformSlabSeal = null,
            analyticIntersectionUniformSeals = corruptedSeals,
            analyticIntersectionPackedBytes = originalPass.packedUniformBytesForUpload(),
        )
        val corruptedScope = GPUCommandEncoderScopePlan(
            sourceStepIndex = originalScope.sourceStepIndex,
            operationKind = originalScope.operationKind,
            scopeLabel = originalScope.scopeLabel,
            sourceTaskIds = originalScope.sourceTaskIds,
            sourcePacketIds = originalScope.sourcePacketIds,
            facadeOperationClasses = originalScope.facadeOperationClasses,
            targetGeneration = originalScope.targetGeneration,
            resourceGenerationLabels = originalScope.resourceGenerationLabels,
            passCommandStream = originalScope.passCommandStream,
            corePrimitiveDirectNativeRouteSeal = GPUCorePrimitiveDirectNativeRouteSeal.Routes.snapshot(
                originalRoutes.routesByPacketId,
                corruptedPass,
            ),
            corePrimitivePathStencilNativeRouteSeal = originalScope.corePrimitivePathStencilNativeRouteSeal,
            corePrimitiveNativeScopeRouteSeal = originalScope.corePrimitiveNativeScopeRouteSeal,
        ).attachNativeOperandKeys(originalScope.nativeOperandKeys)
        val encoderPlan = GPUCommandEncoderPlan.ordered(
            fixture.encoderPlan.planId,
            fixture.encoderPlan.contextIdentity,
            fixture.encoderPlan.deviceGeneration,
            fixture.encoderPlan.targetGeneration,
            listOf(corruptedScope),
        )
        fixture.native.events.clear()

        val result = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            fixture.limits,
        ).materializeReusable(plan, encoderPlan, fixture.resources, fixture.generationSeal)

        assertEquals(
            "invalid.native-core-primitive.analytic-intersection-uniform-seal",
            assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(result).code,
        )
        assertEquals(emptyList(), fixture.native.events)
        fixture.close()
    }

    @Test
    fun `path only materializes one indexed stencil pair with exact operand layout`() {
        val fixture = fixture(routeShape = RouteShape.PathOnly)
        fixture.native.events.clear()

        val renderStep = fixture.plan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single()
        assertEquals(
            (listOf(renderStep.target) + renderStep.resourceUses.map { it.resource }).map { resource ->
                "${resource::class.simpleName}:${resource.value}@" +
                    fixture.generationSeal.resourceGenerations.getValue(resource)
            },
            fixture.encoderPlan.scopes.single().resourceGenerationLabels,
        )

        val result = fixture.materializeCoreResult()

        val materialized = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
            result,
            (result as? GPUPreparedNativeFramePayloadMaterialization.Refused)?.let { "${it.code}: ${it.message}" },
        )
        assertIndexedPathPayload(
            fixture,
            materialized,
            expectedRoles = listOf("producer", "cover"),
            expectedUniformOffsets = listOf(0L, 0L),
            expectedSemanticCommands = listOf(2, 2),
            expectedUniquePipelines = 2,
        )
        assertTrue(materialized.draft.disposeBeforeRegistration())
        fixture.close()
    }

    @Test
    fun `mixed materializes direct path pair direct in sealed command order`() {
        val fixture = fixture(routeShape = RouteShape.Mixed)
        fixture.native.events.clear()

        val result = fixture.materializeCoreResult()

        val materialized = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
            result,
            (result as? GPUPreparedNativeFramePayloadMaterialization.Refused)?.let { "${it.code}: ${it.message}" },
        )
        assertIndexedPathPayload(
            fixture,
            materialized,
            expectedRoles = listOf("direct", "producer", "cover", "direct"),
            expectedUniformOffsets = listOf(0L, 256L, 256L, 512L),
            expectedSemanticCommands = listOf(1, 2, 2, 3),
            expectedUniquePipelines = 3,
        )
        assertTrue(materialized.draft.disposeBeforeRegistration())
        fixture.close()
    }

    @Test
    fun `real preflight indexed keys feed the path materializer without manual route fixtures`() {
        val fixture = fixture(routeShape = RouteShape.PathOnly, useRealPreflight = true)
        fixture.native.events.clear()
        val scope = fixture.encoderPlan.scopes.single()

        assertIs<GPUCorePrimitiveNativeScopeRouteSeal.Routes>(scope.corePrimitiveNativeScopeRouteSeal)
        assertIs<GPUCorePrimitivePathStencilNativeRouteSeal.Pairs>(
            scope.corePrimitivePathStencilNativeRouteSeal,
        )
        assertEquals(
            listOf(
                GPUPreparedNativeOperandRole.RenderColorTarget,
                GPUPreparedNativeOperandRole.RenderDepthStencilTarget,
                GPUPreparedNativeOperandRole.RenderPipeline,
                GPUPreparedNativeOperandRole.RenderPipeline,
                GPUPreparedNativeOperandRole.RenderVertexBuffer,
                GPUPreparedNativeOperandRole.RenderIndexBuffer,
                GPUPreparedNativeOperandRole.RenderBindGroup,
                GPUPreparedNativeOperandRole.RenderBindGroup,
            ),
            scope.nativeOperandKeys.map { it.role },
        )

        val materialized = fixture.materializeCore()
        assertIndexedPathPayload(
            fixture,
            materialized,
            expectedRoles = listOf("producer", "cover"),
            expectedUniformOffsets = listOf(0L, 0L),
            expectedSemanticCommands = listOf(2, 2),
            expectedUniquePipelines = 2,
        )
        assertTrue(materialized.draft.disposeBeforeRegistration())
        fixture.close()
    }

    @Test
    fun `adjacent winding regular and even odd inverse pairs retain reset order and exact programs`() {
        val fixture = fixture(routeShape = RouteShape.TwoPathPairs)
        fixture.native.events.clear()

        val materialized = fixture.materializeCore()

        assertIndexedPathPayload(
            fixture,
            materialized,
            expectedRoles = listOf("producer", "cover", "producer", "cover"),
            expectedUniformOffsets = listOf(0L, 0L, 256L, 256L),
            expectedSemanticCommands = listOf(2, 2, 3, 3),
            expectedUniquePipelines = 4,
        )
        assertEquals(
            listOf(
                "Kanvas.session.corePrimitive.pipeline.PathStencilProducerWinding",
                "Kanvas.session.corePrimitive.pipeline.PathStencilCoverRegular",
                "Kanvas.session.corePrimitive.pipeline.PathStencilProducerEvenOdd",
                "Kanvas.session.corePrimitive.pipeline.PathStencilCoverInverse",
            ),
            fixture.native.renderPipelineDescriptors.map { it.label },
        )
        assertTrue(materialized.draft.disposeBeforeRegistration())
        fixture.close()
    }

    @Test
    fun `path write failure rolls back and lifecycle reuses or quarantines every pooled handle exactly`() {
        val fixture = fixture(routeShape = RouteShape.PathOnly)
        val pathTextureLabel = "Kanvas.session.corePrimitive.framePool.pathDepthStencil"
        val pathViewLabel = "$pathTextureLabel.view"
        fixture.native.events.clear()
        fixture.native.fail("writeBuffer", 2)

        val refused = fixture.materializeCoreResult()

        assertEquals(
            "failed.native-core-primitive.materialization",
            assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(refused).code,
        )
        val pooledLabels = listOf(
            "Kanvas.session.corePrimitive.framePool.vertices",
            "Kanvas.session.corePrimitive.framePool.indices",
            "Kanvas.session.corePrimitive.framePool.uniforms",
            "Kanvas.session.corePrimitive.framePool.bindGroup0",
            pathTextureLabel,
            pathViewLabel,
        )
        assertTrue(pooledLabels.all { fixture.native.createdHandles(it).size == 1 })

        val rolledBackReuse = fixture.materializeCore()
        val firstDepthView = rolledBackReuse.draft.payload.scopeOperands
            .filterIsInstance<GPUPreparedNativeScopeOperand.Render>().single().pass.depthStencilTarget!!.view
        assertSame(fixture.native.createdHandles(pathViewLabel).single(), firstDepthView)
        assertTrue(rolledBackReuse.draft.disposeBeforeRegistration())

        val completionReuse = fixture.materializeCore()
        assertSame(
            firstDepthView,
            completionReuse.draft.payload.scopeOperands.filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
                .single().pass.depthStencilTarget!!.view,
        )
        val adapter = GPURuntimeResourceAdapter()
        val completedRegistration = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            adapter.registerPreparedNativeFrameDraft(completionReuse.draft),
        )
        assertIs<GPUPreparedNativeFrameBindingResult.Ready>(
            completedRegistration.ownership.bindLateSurface(
                null,
                GPUPreparedNativeFrameLateSurfaceBinding.NotRequired,
            ),
        )
        assertIs<GPUPreparedNativeFrameConsumption.Consumed>(
            completedRegistration.ownership.consume(completionReuse.draft.payload.identity),
        )
        assertTrue(completedRegistration.ownership.markSubmitted())
        assertTrue(completedRegistration.ownership.releaseAfterCompletion())

        val quarantined = fixture.materializeCore()
        assertSame(
            firstDepthView,
            quarantined.draft.payload.scopeOperands.filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
                .single().pass.depthStencilTarget!!.view,
        )
        val quarantinedRegistration = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            adapter.registerPreparedNativeFrameDraft(quarantined.draft),
        )
        assertTrue(quarantinedRegistration.ownership.quarantine())

        val replacement = fixture.materializeCore()
        val replacementDepthView = replacement.draft.payload.scopeOperands
            .filterIsInstance<GPUPreparedNativeScopeOperand.Render>().single().pass.depthStencilTarget!!.view
        assertNotSame(firstDepthView, replacementDepthView)
        assertEquals(2, fixture.native.createdHandles(pathTextureLabel).size)
        assertEquals(2, fixture.native.createdHandles(pathViewLabel).size)
        assertTrue(replacement.draft.disposeBeforeRegistration())
        adapter.close()
        fixture.close()
        val pathCloses = fixture.native.events.filter { event ->
            event == "close:$pathViewLabel" || event == "close:$pathTextureLabel"
        }
        assertEquals(
            listOf(
                "close:$pathViewLabel",
                "close:$pathTextureLabel",
                "close:$pathViewLabel",
                "close:$pathTextureLabel",
            ),
            pathCloses,
        )
    }

    @Test
    fun `path validation refusal performs no native action`() {
        val fixture = fixture(routeShape = RouteShape.PathOnly)
        fixture.native.events.clear()

        val result = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            fixture.limits,
        ).materializeReusable(
            fixture.plan,
            fixture.encoderPlan,
            GPUPreparedResourceSet(emptyList(), emptyList()),
            fixture.generationSeal,
        )

        assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(result)
        assertEquals(emptyList(), fixture.native.events)
        fixture.close()
    }

    @Test
    fun `path texture and view allocation failures remain transactional before uploads`() {
        listOf("createTexture", "createView").forEach { operation ->
            val fixture = fixture(routeShape = RouteShape.PathOnly)
            val textureLabel = "Kanvas.session.corePrimitive.framePool.pathDepthStencil"
            val viewLabel = "$textureLabel.view"
            fixture.native.events.clear()
            fixture.native.fail(operation, 1)

            val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(
                fixture.materializeCoreResult(),
                operation,
            )

            assertEquals("failed.native-core-primitive.frame-pool-allocation", refused.code, operation)
            assertTrue(fixture.native.writeBufferCalls.isEmpty(), operation)
            if (operation == "createTexture") {
                assertTrue(fixture.native.createdHandles(textureLabel).isEmpty())
                assertTrue(fixture.native.createdHandles(viewLabel).isEmpty())
            } else {
                val unpublishedTexture = fixture.native.createdHandles(textureLabel).single()
                assertTrue(fixture.native.createdHandles(viewLabel).isEmpty())
                assertEquals(1, fixture.native.closeCounts.getOrDefault(unpublishedTexture, 0))
            }
            fixture.close()
        }
    }

    @Test
    fun `core uploads pass explicit zero data offsets and exact used byte ranges`() {
        val fixture = fixture()
        fixture.native.events.clear()
        fixture.native.writeBufferCalls.clear()
        val materializer = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            fixture.limits,
        )
        val uniformUsedBytes = fixture.plan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .single { it.role == GPUFrameResourceRole.UniformData }
            .byteSize
            .toULong()

        val materialized = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
            materializer.materializeReusable(
                fixture.plan,
                fixture.encoderPlan,
                fixture.resources,
                fixture.generationSeal,
            ),
        )

        assertEquals(
            listOf(
                WriteBufferCall("Kanvas.session.corePrimitive.framePool.vertices", 0uL, 0uL, 64uL, 64uL),
                WriteBufferCall("Kanvas.session.corePrimitive.framePool.indices", 0uL, 0uL, 48uL, 48uL),
                WriteBufferCall(
                    "Kanvas.session.corePrimitive.framePool.uniforms",
                    0uL,
                    0uL,
                    uniformUsedBytes,
                    uniformUsedBytes,
                ),
            ),
            fixture.native.writeBufferCalls.map {
                it.copy(snapshot = EMPTY_UPLOAD_SNAPSHOT, buffer = null)
            },
        )
        assertTrue(materialized.draft.disposeBeforeRegistration())
        materializer.close()
        fixture.close()
    }

    @Test
    fun `sequential submitted frames reuse the exact pooled handles after successful completion`() {
        val fixture = fixture()
        fixture.native.events.clear()
        fixture.native.writeBufferCalls.clear()
        val first = fixture.materializeCore()
        val firstRender = first.draft.payload.scopeOperands
            .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
            .single()
        val firstVertex = firstRender.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetVertexBuffer>()
            .first().buffer.buffer
        val firstIndex = firstRender.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetIndexBuffer>()
            .first().buffer.buffer
        val firstBindGroup = firstRender.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetBindGroup>()
            .first().bindGroup.bindGroup
        val adapter = GPURuntimeResourceAdapter()
        val registration = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            adapter.registerPreparedNativeFrameDraft(first.draft),
        )
        assertIs<GPUPreparedNativeFrameBindingResult.Ready>(
            registration.ownership.bindLateSurface(null, GPUPreparedNativeFrameLateSurfaceBinding.NotRequired),
        )
        assertIs<GPUPreparedNativeFrameConsumption.Consumed>(
            registration.ownership.consume(first.draft.payload.identity),
        )
        assertTrue(registration.ownership.markSubmitted())
        assertTrue(registration.ownership.releaseAfterCompletion())

        val second = fixture.materializeCore()
        val secondRender = second.draft.payload.scopeOperands
            .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
            .single()
        assertSame(
            firstVertex,
            secondRender.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetVertexBuffer>().first().buffer.buffer,
        )
        assertSame(
            firstIndex,
            secondRender.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetIndexBuffer>().first().buffer.buffer,
        )
        assertSame(
            firstBindGroup,
            secondRender.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetBindGroup>().first().bindGroup.bindGroup,
        )
        assertEquals(1, fixture.native.createdHandles("Kanvas.session.corePrimitive.framePool.vertices").size)
        assertEquals(1, fixture.native.createdHandles("Kanvas.session.corePrimitive.framePool.indices").size)
        assertEquals(1, fixture.native.createdHandles("Kanvas.session.corePrimitive.framePool.uniforms").size)
        assertEquals(1, fixture.native.createdHandles("Kanvas.session.corePrimitive.framePool.bindGroup0").size)
        assertEquals(6, fixture.native.writeBufferCalls.size)

        assertTrue(second.draft.disposeBeforeRegistration())
        adapter.close()
        fixture.close()
    }

    @Test
    fun `three in flight frames use distinct slots fourth refuses and rollback reuses immediately`() {
        val fixture = fixture()
        fixture.native.events.clear()
        val live = List(3) { fixture.materializeCore().draft }
        val vertices = live.map { draft ->
            draft.payload.scopeOperands.filterIsInstance<GPUPreparedNativeScopeOperand.Render>().single()
                .commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetVertexBuffer>().first().buffer.buffer
        }
        assertNotSame(vertices[0], vertices[1])
        assertNotSame(vertices[1], vertices[2])

        val fourth = fixture.materializeCoreResult()

        assertEquals(
            "unsupported.native-core-primitive.frame-pool-saturated",
            assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(fourth).code,
        )
        assertEquals(3, fixture.native.createdHandles("Kanvas.session.corePrimitive.framePool.vertices").size)
        assertTrue(live.first().disposeBeforeRegistration())
        val reused = fixture.materializeCore().draft
        val reusedVertex = reused.payload.scopeOperands.filterIsInstance<GPUPreparedNativeScopeOperand.Render>().single()
            .commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetVertexBuffer>().first().buffer.buffer
        assertSame(vertices.first(), reusedVertex)
        assertEquals(3, fixture.native.createdHandles("Kanvas.session.corePrimitive.framePool.vertices").size)

        live.drop(1).forEach { assertTrue(it.disposeBeforeRegistration()) }
        assertTrue(reused.disposeBeforeRegistration())
        fixture.close()
    }

    @Test
    fun `uniform byte corruption refuses before every native action`() {
        val fixture = fixture()
        val render = fixture.plan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().first()
        val packet = render.drawPackets.first()
        val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(packet.semanticPayload)
        val block = requireNotNull(semantic.payloadRef.uniformBlock)
        val corruptedBytes = block.bytes.toMutableList().also { it[it.lastIndex] = it.last() xor 0xff }
        val corrupted = GPUDrawSemanticPayload.CorePrimitive(
            semantic.payloadRef.copy(uniformBlock = block.copy(bytes = corruptedBytes)),
            semantic.sourceFamily,
            semantic.geometry,
            semantic.premultipliedRgba,
            semantic.targetBounds,
            semantic.scissorBounds,
            semantic.clipCoveragePlan,
            semantic.clipExecutionPlanIdentity,
            semantic.blendPlanIdentity,
            semantic.frameProvenance,
            semantic.canonicalHash,
            semantic.coverageMode,
        )
        val plan = fixture.plan.replacingPacket(packet, packet.withSemantic(corrupted))
        fixture.native.events.clear()
        val result = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            fixture.limits,
        ).materializeReusable(plan, fixture.encoderPlan, fixture.resources, fixture.generationSeal)

        assertEquals(
            "invalid.native-core-primitive.packet-authority",
            assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(result).code,
        )
        assertEquals(emptyList(), fixture.native.events)
        fixture.close()
    }

    @Test
    fun `shifted full size readback refuses before every native action`() {
        val fixture = fixture(readback = true)
        val readback = fixture.plan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>().single()
        val shifted = GPUFrameStep.ReadbackCopyStep(
            readback.source,
            readback.staging,
            GPUFrameReadbackRequest(
                readback.request.requestId,
                GPUPixelBounds(1, 0, TARGET.width + 1, TARGET.height),
                readback.request.pixelFormat,
                readback.request.outputColorInterpretation,
            ),
            readback.sourceTaskIds,
        )
        val plan = fixture.plan.replacingStep(readback, shifted)
        fixture.native.events.clear()
        val result = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            fixture.limits,
        ).materializeReusable(plan, fixture.encoderPlan, GPUPreparedResourceSet(emptyList(), emptyList()), fixture.generationSeal)

        assertEquals(
            "unsupported.native-core-primitive.readback-layout",
            assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(result).code,
        )
        assertEquals(emptyList(), fixture.native.events)
        fixture.close()
    }

    @Test
    fun `forged host unaddressable descriptor cannot replace the sealed uniform slab`() {
        val fixture = fixture()
        val alignment = 1L shl 31
        val totalBytes = 1L shl 32
        val plan = fixture.plan.withUniformPreparation(totalBytes, alignment)
        fixture.native.events.clear()

        val result = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            fixture.limits.copy(
                minUniformBufferOffsetAlignment = alignment,
                maxBufferSize = 1L shl 33,
            ),
        ).materializeReusable(plan, fixture.encoderPlan, fixture.resources, fixture.generationSeal)

        assertEquals(
            "invalid.native-core-primitive.uniform-seal-generation",
            assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(result).code,
        )
        assertEquals(emptyList(), fixture.native.events)
        fixture.close()
    }

    @Test
    fun `allocation upload and bind group failures close every created frame handle once`() {
        val vertex = "Kanvas.session.corePrimitive.framePool.vertices"
        val index = "Kanvas.session.corePrimitive.framePool.indices"
        val uniform = "Kanvas.session.corePrimitive.framePool.uniforms"
        val bindGroup = "Kanvas.session.corePrimitive.framePool.bindGroup0"
        listOf(
            FailureCase(
                "createBuffer",
                1,
                emptySet(),
                expectedCode = "failed.native-core-primitive.frame-pool-allocation",
            ),
            FailureCase(
                "createBuffer",
                2,
                setOf(vertex),
                expectedCode = "failed.native-core-primitive.frame-pool-allocation",
            ),
            FailureCase(
                "createBuffer",
                3,
                setOf(vertex, index),
                expectedCode = "failed.native-core-primitive.frame-pool-allocation",
            ),
            FailureCase(
                "createBuffer",
                4,
                setOf(vertex, index, uniform, bindGroup),
                readback = true,
            ),
            FailureCase("writeBuffer", 1, setOf(vertex, index, uniform, bindGroup)),
            FailureCase(
                "writeBuffer",
                2,
                setOf(vertex, index, uniform, bindGroup),
            ),
            FailureCase(
                "writeBuffer",
                3,
                setOf(vertex, index, uniform, bindGroup),
            ),
            FailureCase(
                "createBindGroup",
                1,
                setOf(vertex, index, uniform),
                expectedCode = "failed.native-core-primitive.frame-pool-allocation",
            ),
        ).forEach { failureCase ->
            val fixture = fixture(readback = failureCase.readback)
            fixture.native.events.clear()
            fixture.native.fail(failureCase.operation, failureCase.ordinal)
            val materializer = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
                fixture.native.device,
                fixture.native.queue,
                fixture.target,
                fixture.cache,
                fixture.limits,
            )

            val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(
                materializer.materializeReusable(
                    fixture.plan,
                    fixture.encoderPlan,
                    fixture.resources,
                    fixture.generationSeal,
                ),
            )

            assertEquals(failureCase.expectedCode, refused.code)
            assertEquals(null, refused.retainedPreRegistrationLedger)
            materializer.close()
            fixture.close()
            assertEquals(
                failureCase.closedLabels.associateWith { 1 },
                fixture.native.coreCloseAttempts(),
            )
        }
    }

    @Test
    fun `failed pooled handle close is retained by the session and retried without double close`() {
        val fixture = fixture()
        fixture.native.events.clear()
        fixture.native.fail("createBindGroup", 1)
        fixture.native.failCloseOnce("Kanvas.session.corePrimitive.framePool.uniforms")
        val materializer = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            fixture.limits,
        )

        val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(
            materializer.materializeReusable(
                fixture.plan,
                fixture.encoderPlan,
                fixture.resources,
                fixture.generationSeal,
            ),
        )
        assertEquals("failed.native-core-primitive.frame-pool-allocation", refused.code)
        assertEquals(null, refused.retainedPreRegistrationLedger)

        materializer.close()
        fixture.close()
        assertEquals(
            mapOf(
                "Kanvas.session.corePrimitive.framePool.vertices" to 1,
                "Kanvas.session.corePrimitive.framePool.indices" to 1,
                "Kanvas.session.corePrimitive.framePool.uniforms" to 2,
            ),
            fixture.native.coreCloseAttempts(),
        )
    }

    @Test
    fun `readback staging is output owned and draft rollback closes it once`() {
        val fixture = fixture(readback = true)
        fixture.native.events.clear()
        val materializer = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            fixture.native.device,
            fixture.native.queue,
            fixture.target,
            fixture.cache,
            fixture.limits,
        )

        val materialized = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
            materializer.materializeReusable(
                fixture.plan,
                fixture.encoderPlan,
                fixture.resources,
                fixture.generationSeal,
            ),
        )
        val readback = materialized.draft.payload.scopeOperands
            .filterIsInstance<GPUPreparedNativeScopeOperand.Readback>()
            .single()
        assertEquals(GPUPreparedNativeOperandOwnership.OutputOwnedReadback, readback.destination.ownership)
        assertEquals(1, fixture.native.events.count {
            it == "createBuffer:Kanvas.frame.corePrimitive.readback"
        })
        assertTrue(materialized.draft.disposeBeforeRegistration())
        assertEquals(
            1,
            fixture.native.coreCloseAttempts().getValue("Kanvas.frame.corePrimitive.readback"),
        )
        materializer.close()
        fixture.close()
    }

    private fun assertIndexedPathPayload(
        fixture: Fixture,
        materialized: GPUPreparedNativeFramePayloadMaterialization.Materialized,
        expectedRoles: List<String>,
        expectedUniformOffsets: List<Long>,
        expectedSemanticCommands: List<Int>,
        expectedUniquePipelines: Int,
    ) {
        val render = materialized.draft.payload.scopeOperands
            .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
            .single()
        assertEquals(GPUPreparedNativeRenderOperandLayout.IndexedCorePrimitive, render.operandLayout)
        assertEquals(GPUPreparedNativeLoadOperation.Clear, render.pass.loadOperation)
        assertEquals(GPUPreparedNativeStoreOperation.Store, render.pass.storeOperation)
        assertEquals(GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0), render.pass.clearColor)
        assertTrue(render.pass.depthReadOnly)
        assertEquals(null, render.pass.depthLoadOperation)
        assertEquals(null, render.pass.depthStoreOperation)
        assertFalse(render.pass.stencilReadOnly)
        assertEquals(0u, render.pass.stencilClearValue)
        assertEquals(GPUPreparedNativeLoadOperation.Clear, render.pass.stencilLoadOperation)
        assertEquals(GPUPreparedNativeStoreOperation.Discard, render.pass.stencilStoreOperation)
        assertEquals(1, render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetStencilReference>().size)
        assertEquals(0u, render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetStencilReference>().single().reference)
        assertEquals(1, render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetVertexBuffer>().size)
        assertEquals(1, render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetIndexBuffer>().size)
        assertEquals(
            listOf("vertex", "index", "stencil") + expectedRoles.flatMap {
                listOf("pipeline", "bind", "scissor", "draw")
            },
            render.commands.map { command ->
                when (command) {
                    is GPUPreparedNativeRenderCommand.SetVertexBuffer -> "vertex"
                    is GPUPreparedNativeRenderCommand.SetIndexBuffer -> "index"
                    is GPUPreparedNativeRenderCommand.SetStencilReference -> "stencil"
                    is GPUPreparedNativeRenderCommand.SetPipeline -> "pipeline"
                    is GPUPreparedNativeRenderCommand.SetBindGroup -> "bind"
                    is GPUPreparedNativeRenderCommand.SetScissor -> "scissor"
                    is GPUPreparedNativeRenderCommand.DrawIndexed -> "draw"
                    is GPUPreparedNativeRenderCommand.Draw -> "non-indexed-draw"
                }
            },
        )
        val pipelines = render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetPipeline>()
        val bindGroups = render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetBindGroup>()
        val draws = render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.DrawIndexed>()
        assertEquals(expectedRoles.size, pipelines.size)
        assertEquals(expectedRoles.size, bindGroups.size)
        assertEquals(expectedRoles.size, draws.size)
        assertEquals(expectedUniformOffsets.map(::listOf), bindGroups.map { it.dynamicOffsets })
        assertEquals(expectedSemanticCommands, render.semanticPayloads.map { payload ->
            (payload as GPUDrawSemanticPayload.CorePrimitive).payloadRef.commandIdValue
        })
        assertEquals(expectedUniquePipelines, distinctIdentityCount(pipelines.map { it.pipeline.pipeline }))
        if (expectedRoles.first() == "direct") {
            assertSame(pipelines.first().pipeline.pipeline, pipelines.last().pipeline.pipeline)
        }
        assertEquals(1, distinctIdentityCount(bindGroups.map { it.bindGroup.bindGroup }))
        val routes = assertIs<GPUCorePrimitiveNativeScopeRouteSeal.Routes>(
            fixture.encoderPlan.scopes.single().corePrimitiveNativeScopeRouteSeal,
        )
        val arena = packCorePrimitiveNativeScopeGeometry(routes)
        assertEquals(
            arena.slices.map { slice ->
                GPUPreparedNativeDrawCall.DrawIndexed(
                    indexCount = slice.indexCount,
                    instanceCount = 1,
                    firstIndex = slice.firstIndex,
                    baseVertex = slice.baseVertex,
                    firstInstance = 0,
                    vertexCount = slice.vertexCount,
                    maxLocalIndex = slice.maxLocalIndex,
                )
            },
            draws.map { it.drawCall },
        )
        assertEquals(3, fixture.native.writeBufferCalls.size)
        val preparationBytes = fixture.plan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .filter { request -> request.role in setOf(
                GPUFrameResourceRole.VertexData,
                GPUFrameResourceRole.IndexData,
                GPUFrameResourceRole.UniformData,
            ) }
            .map(GPUResourcePreparationRequest::byteSize)
            .map(Long::toULong)
        assertEquals(preparationBytes, fixture.native.writeBufferCalls.map { it.size })
        assertTrue(fixture.native.writeBufferCalls.all { call ->
            call.bufferOffset == 0uL && call.dataOffset == 0uL && call.dataBytes == call.size
        })
        val expectedVertices = FloatArray(arena.vertexFloatCount).also(arena::copyVerticesInto)
        val expectedIndices = IntArray(arena.indexCount).also(arena::copyIndicesInto)
        val vertexUpload = fixture.native.writeBufferCalls.single {
            it.bufferLabel.endsWith("framePool.vertices")
        }
        val indexUpload = fixture.native.writeBufferCalls.single {
            it.bufferLabel.endsWith("framePool.indices")
        }
        val uploadedVertices = ArrayBuffer.of(vertexUpload.snapshot).toFloatArray()
        val uploadedIndices = ArrayBuffer.of(indexUpload.snapshot).toIntArray()
        assertContentEquals(expectedVertices, uploadedVertices)
        assertContentEquals(expectedIndices, uploadedIndices)
        arena.slices.forEach { slice ->
            val localIndices = uploadedIndices.sliceArray(
                slice.firstIndex until slice.firstIndex + slice.indexCount,
            )
            assertTrue(localIndices.all { it in 0 until slice.vertexCount })
            assertEquals(slice.maxLocalIndex, localIndices.maxOrNull())
        }
        assertEquals(1, fixture.native.events.count {
            it == "createTexture:Kanvas.session.corePrimitive.framePool.pathDepthStencil"
        })
        val depthDescriptor = fixture.native.textureDescriptors.single { descriptor ->
            descriptor.label == "Kanvas.session.corePrimitive.framePool.pathDepthStencil"
        }
        assertEquals(TARGET.width.toUInt(), depthDescriptor.size.width)
        assertEquals(TARGET.height.toUInt(), depthDescriptor.size.height)
        assertEquals(GPUTextureFormat.Depth24PlusStencil8, depthDescriptor.format)
        assertEquals(1u, depthDescriptor.sampleCount)
        assertEquals(GPUTextureUsage.RenderAttachment, depthDescriptor.usage)
        assertEquals(expectedUniquePipelines, fixture.native.events.count { it == "createRenderPipeline" })
        val expectedKeyRoles = fixture.encoderPlan.scopes.single().nativeOperandKeys.map { it.role }
        assertEquals(expectedKeyRoles.size, render.operands.size)
        assertEquals(2 + expectedUniquePipelines + 2 + expectedRoles.size, render.operands.size)
    }

    private fun distinctIdentityCount(values: List<Any>): Int =
        IdentityHashMap<Any, Unit>().apply { values.forEach { put(it, Unit) } }.size

    private fun setPrivateField(target: Any, name: String, value: Any?) {
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> privateFieldValue(target: Any, name: String): T =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target) as T

    private fun framePoolSlotCount(cache: GPUWgpu4kCorePrimitiveSessionCache): Int {
        val pool = privateFieldValue<Any>(cache, "framePool")
        return privateFieldValue<List<Any>>(pool, "slots").size
    }

    private fun fixture(
        readback: Boolean = false,
        routeShape: RouteShape = RouteShape.Direct,
        useRealPreflight: Boolean = false,
        analyticClip: Boolean = false,
        analyticIntersection: Boolean = false,
        clipStencilPlan: GPUClipExecutionPlan.StencilCoverage? = null,
    ): Fixture {
        require(!analyticClip || !analyticIntersection)
        val generation = GPUDeviceGenerationID(23L)
        val capabilities = capabilities()
        val frameId = GPUFrameID(231L)
        val commandIds = when (routeShape) {
            RouteShape.Direct -> listOf(1, 2)
            RouteShape.PathOnly -> listOf(2)
            RouteShape.Mixed -> listOf(1, 2, 3)
            RouteShape.TwoPathPairs -> listOf(2, 3)
            RouteShape.ClipStencil -> listOf(1, 2)
            RouteShape.CoverageMask -> listOf(1, 2)
        }
        val recorded = GPURecorder(GPURecordingID("recording.core.proxy"), frameId, capabilities, generation).apply {
            commandIds.forEachIndexed { order, commandId ->
                record(command(commandId, order, GPURect(1f + order, 1f, 5f + order, 5f)))
            }
        }.close().taskList
        val clipPlans = commandIds.associateWith { commandId ->
            if (analyticIntersection) {
                if (commandId == commandIds.first()) {
                    GPUClipExecutionPlan.AnalyticIntersection(
                        listOf(
                            GPUClipAnalyticElement(
                                GPUClipExecutionGeometry.Rect(GPUClipBounds(0.5f, 0.75f, 9.5f, 9.25f)),
                                antiAlias = false,
                            ),
                            GPUClipAnalyticElement(
                                GPUClipExecutionGeometry.RRect(
                                    GPUClipBounds(1.25f, 1.5f, 8.75f, 8.5f),
                                    List(4) { listOf(0.75f, 1.25f) }.flatten(),
                                ),
                                antiAlias = true,
                            ),
                        ),
                    )
                } else {
                    GPUClipExecutionPlan.AnalyticIntersection(
                        listOf(
                            GPUClipAnalyticElement(
                                GPUClipExecutionGeometry.RRect(
                                    GPUClipBounds(1.5f, 0.5f, 11.5f, 10.5f),
                                    List(4) { listOf(1.5f, 0.5f) }.flatten(),
                                ),
                                antiAlias = false,
                            ),
                            GPUClipAnalyticElement(
                                GPUClipExecutionGeometry.Rect(GPUClipBounds(2f, 1f, 11f, 10f)),
                                antiAlias = true,
                            ),
                            GPUClipAnalyticElement(
                                GPUClipExecutionGeometry.Rect(GPUClipBounds(2.5f, 1.5f, 10.5f, 9.5f)),
                                antiAlias = false,
                            ),
                            GPUClipAnalyticElement(
                                GPUClipExecutionGeometry.RRect(
                                    GPUClipBounds(3f, 2f, 10f, 9f),
                                    List(4) { listOf(0.5f, 1f) }.flatten(),
                                ),
                                antiAlias = true,
                            ),
                        ),
                    )
                }
            } else if (analyticClip) {
                GPUClipExecutionPlan.AnalyticCoverage(
                    GPUClipExecutionGeometry.Rect(
                        if (commandId == commandIds.first()) {
                            GPUClipBounds(0.5f, 0.75f, 7.5f, 8.25f)
                        } else {
                            GPUClipBounds(2.25f, 1.5f, 10.75f, 9.5f)
                        },
                    ),
                    scissor = null,
                    antiAlias = true,
                )
            } else if (routeShape == RouteShape.ClipStencil) {
                clipStencilPlan ?: nativeClipStencilPlan()
            } else if (routeShape == RouteShape.CoverageMask) {
                nativeCoverageMaskPlan()
            } else {
                GPUClipExecutionPlan.NoClip
            }
        }
        val base = recorded.withClipPlans(clipPlans)
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
        val semantics = packets.associate { packet ->
            packet.commandIdValue to if (routeShape == RouteShape.TwoPathPairs ||
            packet.commandIdValue == 2 && routeShape != RouteShape.Direct &&
                routeShape != RouteShape.ClipStencil && routeShape != RouteShape.CoverageMask
            ) {
                pathSemantic(
                    packet,
                    fillRule = if (packet.commandIdValue == 3) {
                        GPUCorePrimitiveFillRule.EvenOdd
                    } else {
                        GPUCorePrimitiveFillRule.Winding
                    },
                    inverseFill = packet.commandIdValue == 3,
                    coordinateOffset = if (packet.commandIdValue == 3) 2f else 0f,
                )
            } else if (routeShape == RouteShape.CoverageMask && packet.commandIdValue == 2) {
                coverageMaskTriangleSemantic(packet)
            } else {
                semantic(
                    packet,
                    scissorBounds = if (routeShape == RouteShape.ClipStencil) {
                        (clipStencilPlan ?: nativeClipStencilPlan()).consumer.scissor
                            ?: TARGET
                    } else {
                        TARGET
                    },
                )
            }
        }
        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                GPUCorePrimitivePreparedFrameRequest(
                    baseTaskList = base,
                    capabilities = capabilities,
                    target = GPUFrameTargetRef("target.core.proxy"),
                    targetBounds = TARGET,
                    semanticsByCommandId = semantics,
                    readbackRequestId = if (readback) GPUReadbackRequestID("readback.core.proxy") else null,
                ),
            ),
        ).taskList
        val plan = GPUFramePlanner.plan(taskList)
        check(!plan.atomicallyRefused) { plan.dumpLines().joinToString("\n") }
        val generations = plan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .mapIndexed { index, request -> request.resource to (index + 1L) }
            .toMap()
        val preparedByPreflight = if (useRealPreflight) {
            val resourceProvider = GPUConcreteResourceProvider()
            val completionProvider = object : GPUQueueCompletionProvider {
                override fun reserveTicket(
                    request: GPUQueueCompletionTicketRequest,
                ): GPUQueueCompletionTicketReservation = GPUQueueCompletionTicketReservation.Reserved(
                    GPUQueueCompletionTicket(
                        GPUQueueCompletionTicketID("ticket.core.materializer"),
                        request.frameId,
                        request.deviceGeneration,
                    ),
                )

                override fun abandonReservedTicket(
                    ticket: GPUQueueCompletionTicket,
                ): GPUQueueCompletionTicketAbandonResult =
                    GPUQueueCompletionTicketAbandonResult.Abandoned(ticket.ticketId)
            }
            val surfaceProvider = object : GPUSurfaceOutputProvider {
                override fun acquire(request: GPUSurfaceAcquisitionRequest): GPUSurfaceAcquisitionResult =
                    error("Offscreen CorePrimitive preflight must not acquire a surface")

                override fun release(output: GPUAcquiredSurfaceOutput): GPUSurfaceReleaseResult =
                    GPUSurfaceReleaseResult.Released
            }
            val result = GPUFramePreflighter(
                context = GPUFramePreflightContext(
                    targetId = "target.core.proxy",
                    deviceGeneration = generation,
                    targetGeneration = 1L,
                    resourceGenerations = generations,
                ),
                capabilities = capabilities,
                resourceProvider = resourceProvider,
                completionProvider = completionProvider,
                surfaceProvider = surfaceProvider,
            ).preflight(plan)
            assertIs<GPUFramePreflightResult.Prepared>(
                result,
                (result as? GPUFramePreflightResult.Refused)?.diagnostic?.let {
                    "${it.code.value}: ${it.message}"
                },
            ).frame
        } else {
            null
        }
        val renderScopes = if (preparedByPreflight == null) plan.steps.withIndex().mapNotNull { (index, step) ->
            val render = step as? GPUFrameStep.RenderPassStep ?: return@mapNotNull null
            val stream = commandStream(render.drawPackets, generation)
            val routeSeals = testRouteSeals(render)
            GPUCommandEncoderScopePlan(
                sourceStepIndex = index,
                operationKind = GPUEncoderOperationKind.Render,
                scopeLabel = "step.$index",
                sourceTaskIds = render.sourceTaskIds,
                sourcePacketIds = render.drawPackets.map(GPUDrawPacket::packetId),
                facadeOperationClasses = stream.commandLabels,
                targetGeneration = 1L,
                resourceGenerationLabels = (listOf(render.target) + render.resourceUses.map { it.resource })
                    .map { resource ->
                        "${resource::class.simpleName}:${resource.value}@${generations.getValue(resource)}"
                    },
                passCommandStream = stream,
                corePrimitiveDirectNativeRouteSeal = routeSeals.direct,
                corePrimitivePathStencilNativeRouteSeal = routeSeals.path,
                corePrimitiveNativeScopeRouteSeal = routeSeals.unified,
            ).attachNativeOperandKeys(
                if (routeSeals.path is GPUCorePrimitivePathStencilNativeRouteSeal.Pairs) {
                    indexedRenderOperandKeys(render.drawPackets)
                } else {
                    renderOperandKeys(render.drawPackets.first().commandIdValue)
                },
            )
        } else emptyList()
        val readbackScopes = if (preparedByPreflight == null) plan.steps.withIndex().mapNotNull { (index, step) ->
            val readbackStep = step as? GPUFrameStep.ReadbackCopyStep ?: return@mapNotNull null
            GPUCommandEncoderScopePlan(
                sourceStepIndex = index,
                operationKind = GPUEncoderOperationKind.Readback,
                sourceTaskIds = readbackStep.sourceTaskIds,
                facadeOperationClasses = listOf("copyTextureToBuffer"),
                targetGeneration = 1L,
                resourceGenerationLabels = listOf(
                    "target@${generations.getValue(readbackStep.source)}",
                    "staging@${generations.getValue(readbackStep.staging)}",
                ),
            ).attachNativeOperandKeys(readbackOperandKeys())
        } else emptyList()
        val encoderPlan = preparedByPreflight?.encoderPlan ?: GPUCommandEncoderPlan.ordered(
            planId = "core.proxy.encoder",
            contextIdentity = "core.proxy",
            deviceGeneration = generation,
            targetGeneration = 1L,
            scopes = renderScopes + readbackScopes,
        )
        val resources = preparedByPreflight?.resources ?: GPUPreparedResourceSet(
            ordinaryResources = plan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
                .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
                .filter { it.role != GPUFrameResourceRole.ReadbackStaging }
                .map { request ->
                    GPUPreparedResourceEvidence(
                        logicalResource = request.resource,
                        concreteResource = if (request.role == GPUFrameResourceRole.SceneTarget ||
                            request.role == GPUFrameResourceRole.PathDepthStencil ||
                            request.role == GPUFrameResourceRole.ClipDepthStencil ||
                            request.role == GPUFrameResourceRole.ClipMask
                        ) {
                            GPUPreparedConcreteResourceRef.Texture(GPUTextureResourceRef("prepared.target"))
                        } else {
                            GPUPreparedConcreteResourceRef.Buffer(GPUBufferResourceRef("prepared.${request.resource.value}"))
                        },
                        role = request.role,
                        deviceGeneration = generation,
                        resourceGeneration = generations.getValue(request.resource),
                    )
                },
            outputOwnedReadbacks = if (!readback) {
                emptyList()
            } else {
                val readbackStep = plan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>().single()
                val layout = assertIs<GPUReadbackLayoutPlan.Planned>(
                    GPUReadbackLayoutPlanner().plan(readbackStep.request, capabilities),
                ).layout
                val concrete = GPUBufferResourceRef("prepared.${readbackStep.staging.value}")
                listOf(
                    GPUPreparedReadbackOutput(
                        stagingResource = readbackStep.staging,
                        concreteResource = GPUPreparedConcreteResourceRef.Buffer(concrete),
                        resourceGeneration = generations.getValue(readbackStep.staging),
                        request = readbackStep.request,
                        layout = layout,
                        stagingLease = GPUReadbackStagingLease(
                            reservationId = "reservation.core.proxy",
                            ownerScope = "frame.core.proxy",
                            deviceGeneration = generation,
                            resourceRef = concrete,
                            reservationOrdinal = 1L,
                            acquisitionToken = 1L,
                            logicalMinimumBytes = layout.totalBufferBytes,
                            backingBufferBytes = layout.totalBufferBytes,
                            usages = setOf(
                                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.MapRead,
                                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.CopyDestination,
                            ),
                        ),
                    ),
                )
            },
        )
        val native = NativeProxy()
        val setup = GPUPreparedSceneSetupTransaction()
        val target = GPUWgpu4kPreparedSceneTarget.create(
            native.device,
            TARGET.width,
            TARGET.height,
            generation,
            1L,
            GPUWgpu4kPreparedSceneTargetLifecycle(),
            setup,
        )
        setup.commit()
        return Fixture(
            plan,
            encoderPlan,
            resources,
            preparedByPreflight?.generationSeal ?:
                GPUPreparedGenerationSeal(generation, 1L, generations, plan.capabilitySeal.sealHash),
            native,
            target,
            GPUWgpu4kCorePrimitiveSessionCache(native.device, generation),
            requireNotNull(capabilities.limits),
            preparedByPreflight,
        )
    }

    private fun commandStream(packets: List<GPUDrawPacket>, generation: GPUDeviceGenerationID): GPUPassCommandStream {
        fun operand(kind: GPUMaterializedCommandOperandKind, label: String) =
            GPUMaterializedCommandOperandReference(
                label,
                kind,
                "descriptor.$label",
                generation.value,
                "core.proxy",
                listOf("render"),
                "frame-local",
            )
        val bridges = packets.flatMap { packet ->
            listOf(
                GPUPassCommandOperandBridge(packet.packetId, "setRenderPipeline", operand(GPUMaterializedCommandOperandKind.RenderPipeline, "pipeline")),
                GPUPassCommandOperandBridge(packet.packetId, "setBindGroup", operand(GPUMaterializedCommandOperandKind.BindGroup, "bind")),
                GPUPassCommandOperandBridge(packet.packetId, "draw", operand(GPUMaterializedCommandOperandKind.VertexBuffer, "vertex")),
                GPUPassCommandOperandBridge(packet.packetId, "draw", operand(GPUMaterializedCommandOperandKind.IndexBuffer, "index")),
            )
        }
        val first = packets.first()
        return GPUPassCommandStream.fromDrawPacketStream(
            "stream.${first.commandIdValue}",
            GPUDrawPacketStream("packets.${first.commandIdValue}", first.passId, packets),
            first.targetStateHash,
            "store",
            operandBridge = bridges,
        )
    }

    private data class TestRouteSeals(
        val direct: GPUCorePrimitiveDirectNativeRouteSeal,
        val path: GPUCorePrimitivePathStencilNativeRouteSeal,
        val unified: GPUCorePrimitiveNativeScopeRouteSeal,
    )

    private fun testRouteSeals(render: GPUFrameStep.RenderPassStep): TestRouteSeals {
        val slab = requireNotNull(render.drawPackets.first().corePrimitivePreparedAuthority?.uniformSlabSeal)
        val directRoutes = linkedMapOf<org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID, GPUCorePrimitiveDirectNativeRoute.Accepted>()
        val pathPairs = mutableListOf<GPUCorePrimitivePathStencilNativeRoute.AcceptedPair>()
        val preparedPairs = mutableListOf<GPUCorePrimitivePathStencilPreparedPairSeal>()
        val units = mutableListOf<GPUCorePrimitiveNativeScopeRouteUnit>()
        var packetIndex = 0
        while (packetIndex < render.drawPackets.size) {
            val packet = render.drawPackets[packetIndex]
            val semantic = packet.semanticPayload as GPUDrawSemanticPayload.CorePrimitive
            when (packet.role) {
                GPUDrawPacketRole.Shading -> {
                    val route = assertIs<GPUCorePrimitiveDirectNativeRoute.Accepted>(
                        validateCorePrimitiveDirectNativeRoute(
                            semantic,
                            corePrimitiveDirectClipAuthority(
                                requireNotNull(packet.clipExecutionPlan),
                                semantic.targetBounds,
                            ),
                            packet.blendPlan,
                            render.samplePlan,
                            "rgba8unorm",
                        ),
                    )
                    val structural = requireNotNull(packet.corePrimitivePreparedAuthority).structuralPipelineKey
                    directRoutes[packet.packetId] = route
                    units += GPUCorePrimitiveNativeScopeRouteUnit.Direct(
                        packet.commandIdValue,
                        packet.packetId,
                        route,
                        structural,
                    )
                    packetIndex += 1
                }
                GPUDrawPacketRole.PathStencilProducer -> {
                    val cover = render.drawPackets[packetIndex + 1]
                    check(cover.role == GPUDrawPacketRole.PathStencilCover)
                    val geometry = semantic.geometry as GPUCorePrimitiveGeometry.TriangulatedPath
                    val pair = GPUCorePrimitivePathStencilNativeRoute.AcceptedPair(
                        packet.packetId,
                        cover.packetId,
                        FloatArray(geometry.vertices.size) { geometry.vertices[it] },
                        IntArray(geometry.indices.size) { geometry.indices[it] },
                        geometry.coverBounds,
                        semantic.targetBounds,
                        geometry.inverseFill,
                    )
                    val producerKey = requireNotNull(packet.corePrimitivePreparedAuthority).structuralPipelineKey
                    val coverKey = requireNotNull(cover.corePrimitivePreparedAuthority).structuralPipelineKey
                    pathPairs += pair
                    preparedPairs += GPUCorePrimitivePathStencilPreparedPairSeal(
                        packet.commandIdValue,
                        slab.commandIds.indexOf(packet.commandIdValue),
                        packet.packetId,
                        cover.packetId,
                        producerKey,
                        coverKey,
                    )
                    units += GPUCorePrimitiveNativeScopeRouteUnit.PathPair(
                        packet.commandIdValue,
                        pair,
                        producerKey,
                        coverKey,
                    )
                    packetIndex += 2
                }
                else -> error("Unexpected standalone CorePrimitive packet role ${packet.role}")
            }
        }
        if (pathPairs.isEmpty()) {
            val structural = requireNotNull(render.drawPackets.first().corePrimitivePreparedAuthority).structuralPipelineKey
            return TestRouteSeals(
                GPUCorePrimitiveDirectNativeRouteSeal.Routes.snapshot(
                    directRoutes,
                    GPUCorePrimitiveDirectPreparedPassSeal(structural, slab),
                ),
                GPUCorePrimitivePathStencilNativeRouteSeal.Empty,
                GPUCorePrimitiveNativeScopeRouteSeal.Empty,
            )
        }
        val directSeal = if (directRoutes.isEmpty()) {
            GPUCorePrimitiveDirectNativeRouteSeal.Empty
        } else {
            val structural = units.filterIsInstance<GPUCorePrimitiveNativeScopeRouteUnit.Direct>()
                .first().structuralPipelineKey
            GPUCorePrimitiveDirectNativeRouteSeal.Routes.snapshot(
                directRoutes,
                GPUCorePrimitiveDirectPreparedPassSeal(structural, slab),
            )
        }
        return TestRouteSeals(
            directSeal,
            GPUCorePrimitivePathStencilNativeRouteSeal.Pairs(
                pathPairs,
                GPUCorePrimitivePathStencilPreparedPassSeal(preparedPairs, slab),
            ),
            GPUCorePrimitiveNativeScopeRouteSeal.Routes(units, slab),
        )
    }

    private fun indexedRenderOperandKeys(packets: List<GPUDrawPacket>): List<GPUPreparedNativeOperandKey> {
        fun key(
            role: GPUPreparedNativeOperandRole,
            kind: GPUPreparedNativeOperandKind,
            ordinal: Int = 0,
        ) = GPUPreparedNativeOperandKey(
            role,
            kind,
            gpuPreparedNativeBindingKey("core.indexed.${role.name}.$ordinal"),
            GPUPreparedNativeOperandOwnership.Borrowed,
        )
        val pipelineCount = packets.distinctBy(GPUDrawPacket::renderPipelineKey).size
        return listOf(
            key(GPUPreparedNativeOperandRole.RenderColorTarget, GPUPreparedNativeOperandKind.TextureView),
            key(GPUPreparedNativeOperandRole.RenderDepthStencilTarget, GPUPreparedNativeOperandKind.TextureView),
        ) + List(pipelineCount) { ordinal ->
            key(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline, ordinal)
        } + listOf(
            key(GPUPreparedNativeOperandRole.RenderVertexBuffer, GPUPreparedNativeOperandKind.Buffer),
            key(GPUPreparedNativeOperandRole.RenderIndexBuffer, GPUPreparedNativeOperandKind.Buffer),
        ) + List(packets.size) { ordinal ->
            key(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup, ordinal)
        }
    }

    private fun renderOperandKeys(commandId: Int): List<GPUPreparedNativeOperandKey> {
        fun key(
            role: GPUPreparedNativeOperandRole,
            kind: GPUPreparedNativeOperandKind,
            ownership: GPUPreparedNativeOperandOwnership,
            ordinal: Int = 0,
        ) = GPUPreparedNativeOperandKey(
            role,
            kind,
            gpuPreparedNativeBindingKey("core.$commandId.${role.name}.$ordinal"),
            ownership,
        )
        return listOf(
            key(GPUPreparedNativeOperandRole.RenderColorTarget, GPUPreparedNativeOperandKind.TextureView, GPUPreparedNativeOperandOwnership.Borrowed),
            key(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline, GPUPreparedNativeOperandOwnership.Borrowed),
            key(GPUPreparedNativeOperandRole.RenderVertexBuffer, GPUPreparedNativeOperandKind.Buffer, GPUPreparedNativeOperandOwnership.Borrowed),
            key(GPUPreparedNativeOperandRole.RenderIndexBuffer, GPUPreparedNativeOperandKind.Buffer, GPUPreparedNativeOperandOwnership.Borrowed),
            key(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup, GPUPreparedNativeOperandOwnership.Borrowed),
            key(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup, GPUPreparedNativeOperandOwnership.Borrowed, 1),
        )
    }

    private fun readbackOperandKeys(): List<GPUPreparedNativeOperandKey> = listOf(
        GPUPreparedNativeOperandKey(
            GPUPreparedNativeOperandRole.ReadbackSource,
            GPUPreparedNativeOperandKind.Texture,
            gpuPreparedNativeBindingKey("core.readback.source"),
            GPUPreparedNativeOperandOwnership.Borrowed,
        ),
        GPUPreparedNativeOperandKey(
            GPUPreparedNativeOperandRole.ReadbackDestination,
            GPUPreparedNativeOperandKind.Buffer,
            gpuPreparedNativeBindingKey("core.readback.destination"),
            GPUPreparedNativeOperandOwnership.OutputOwnedReadback,
        ),
    )

    private fun semantic(
        packet: GPUDrawPacket,
        scissorBounds: GPUPixelBounds = TARGET,
    ): GPUDrawSemanticPayload.CorePrimitive =
        GPUCorePrimitivePayloadGatherer().gatherSemantic(
            GPUCorePrimitivePayloadInput(
                commandIdValue = packet.commandIdValue,
                sourceFamily = GPUCorePrimitiveSourceFamily.Rect,
                geometry = GPUCorePrimitiveGeometryInput.Rect(1f, 1f, 5f, 5f),
                premultipliedRgba = listOf(0.5f, 0f, 0f, 0.5f),
                targetBounds = TARGET,
                scissorBounds = scissorBounds,
                clipCoveragePlan = GPUClipCoveragePlan.NoClip,
                blendPlanIdentity = requireNotNull(packet.blendPlan).canonicalIdentity(),
                frameProvenance = GPUFrameProvenance.GmContent,
                analysisRecordId = "analysis.fill_rect.${packet.commandIdValue}",
                analysisCommandFamily = "FillRect",
                rectRouteAuthority = GPUCorePrimitiveRectRouteAuthority.RectAxisAligned,
                rectGeometryAuthority = corePrimitiveRectGeometryAuthority(
                    GPURect(1f, 1f, 5f, 5f),
                    GPUTransformFacts.identity(),
                ),
            ),
        )

    private fun GPUDrawSemanticPayload.CorePrimitive.sameContentClone() =
        GPUDrawSemanticPayload.CorePrimitive(
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
        )

    private fun pathSemantic(
        packet: GPUDrawPacket,
        fillRule: GPUCorePrimitiveFillRule = GPUCorePrimitiveFillRule.Winding,
        inverseFill: Boolean = false,
        coordinateOffset: Float = 0f,
    ): GPUDrawSemanticPayload.CorePrimitive =
        GPUCorePrimitivePayloadGatherer().gatherSemantic(
            GPUCorePrimitivePayloadInput(
                commandIdValue = packet.commandIdValue,
                sourceFamily = GPUCorePrimitiveSourceFamily.Path,
                geometry = GPUCorePrimitiveGeometryInput.TriangulatedPath(
                    vertices = listOf(
                        -1f, -1f, 1f + coordinateOffset, 1f, 5f + coordinateOffset, 1f,
                        -1f, -1f, 5f + coordinateOffset, 1f, 4f + coordinateOffset, 5f,
                        -1f, -1f, 4f + coordinateOffset, 5f, 1f + coordinateOffset, 1f,
                    ),
                    indices = (0..8).toList(),
                    sourceContourStarts = listOf(0),
                    sourceVertexCount = 3,
                    coverBounds = GPUPixelBounds(0, 0, 8, 6),
                    geometryMode = GPUCorePrimitiveGeometryMode.StencilEdgeFan,
                    fillRule = fillRule,
                    inverseFill = inverseFill,
                ),
                premultipliedRgba = listOf(0.5f, 0f, 0f, 0.5f),
                targetBounds = TARGET,
                scissorBounds = TARGET,
                clipCoveragePlan = GPUClipCoveragePlan.NoClip,
                blendPlanIdentity = requireNotNull(packet.blendPlan).canonicalIdentity(),
                frameProvenance = GPUFrameProvenance.GmContent,
                coverageMode = GPUCorePrimitiveCoverageMode.Stencil1x,
            ),
        )

    private fun coverageMaskTriangleSemantic(
        packet: GPUDrawPacket,
    ): GPUDrawSemanticPayload.CorePrimitive =
        GPUCorePrimitivePayloadGatherer().gatherSemantic(
            GPUCorePrimitivePayloadInput(
                commandIdValue = packet.commandIdValue,
                sourceFamily = GPUCorePrimitiveSourceFamily.Path,
                geometry = GPUCorePrimitiveGeometryInput.TriangulatedPath(
                    vertices = listOf(2f, 2f, 10f, 2f, 6f, 10f),
                    indices = listOf(0, 2, 1),
                    sourceContourStarts = listOf(0),
                    sourceVertexCount = 3,
                    coverBounds = TARGET,
                    geometryMode = GPUCorePrimitiveGeometryMode.DirectTriangles,
                ),
                premultipliedRgba = listOf(0.5f, 0f, 0f, 0.5f),
                targetBounds = TARGET,
                scissorBounds = TARGET,
                clipCoveragePlan = GPUClipCoveragePlan.NoClip,
                blendPlanIdentity = requireNotNull(packet.blendPlan).canonicalIdentity(),
                frameProvenance = GPUFrameProvenance.GmContent,
            ),
        )

    private fun nativeCoverageMaskPlan() = GPUClipExecutionPlan.CoverageMask(
        contentKey = "clip.native.materializer.coverage-mask",
        bounds = TARGET,
        sampleCount = 1,
        depthStencilRequired = false,
        orderingToken = GPUClipOrderingToken("token.clip.native.materializer.coverage-mask"),
        producers = listOf(
            GPUClipMaskProducerPlan(
                sourceOrder = 0,
                geometry = GPUClipExecutionGeometry.Rect(GPUClipBounds(0f, 0f, 16f, 16f)),
                combine = GPUClipMaskCombine.Intersect,
                antiAlias = false,
            ),
            GPUClipMaskProducerPlan(
                sourceOrder = 1,
                geometry = GPUClipExecutionGeometry.RRect(
                    GPUClipBounds(2f, 2f, 14f, 14f),
                    listOf(1f, 2f, 1f, 2f, 1f, 2f, 1f, 2f),
                ),
                combine = GPUClipMaskCombine.Difference,
                antiAlias = false,
            ),
        ),
        consumer = GPUClipMaskConsumerPlan(),
    )

    private fun nativeClipStencilPlan(
        fillRule: GPUClipFillRule = GPUClipFillRule.Winding,
        inverseFill: Boolean = false,
        vertices: List<Float> = listOf(2f, 2f, 14f, 2f, 14f, 14f, 2f, 14f),
        contourStarts: List<Int> = listOf(0),
        scissor: GPUPixelBounds = TARGET,
    ) = GPUClipExecutionPlan.StencilCoverage(
        contentKey = "clip.native.materializer.${fillRule.name}.${if (inverseFill) "inverse" else "regular"}",
        bounds = TARGET,
        sampleCount = 1,
        atomicGroup = GPUClipAtomicGroupID("atomic.clip.native.materializer.${fillRule.name}"),
        orderingToken = GPUClipOrderingToken("token.clip.native.materializer.${fillRule.name}"),
        producer = GPUClipStencilProducerPlan(
            geometry = GPUClipExecutionGeometry.Path(
                vertices = vertices,
                contourStarts = contourStarts,
                fillRule = fillRule,
                inverseFill = inverseFill,
            ),
            scissor = scissor,
            fillRule = fillRule,
            reference = 0u,
            compare = GPUClipStencilCompare.Always,
            frontPassOperation = if (fillRule == GPUClipFillRule.Winding) {
                GPUClipStencilOperation.IncrementWrap
            } else {
                GPUClipStencilOperation.Invert
            },
            backPassOperation = if (fillRule == GPUClipFillRule.Winding) {
                GPUClipStencilOperation.DecrementWrap
            } else {
                GPUClipStencilOperation.Invert
            },
            loadOperation = GPUClipStencilLoadOperation.Clear,
            storeOperation = GPUClipStencilStoreOperation.Store,
            clearValue = 0u,
        ),
        consumer = GPUClipStencilConsumerPlan(
            scissor = scissor,
            reference = 0u,
            compare = if (inverseFill) GPUClipStencilCompare.Equal else GPUClipStencilCompare.NotEqual,
        ),
    )

    private fun command(id: Int, order: Int, rect: GPURect) = GPUFillRectCommandBuilder.build(
        commandId = GPUDrawCommandID(id),
        rect = rect,
        target = GPUTargetFacts(TARGET.width, TARGET.height, "rgba8unorm"),
        material = GPUMaterialDescriptor.SolidColor(0.5f, 0f, 0f, 0.5f),
        clip = GPUClipFacts(
            kind = GPUClipKind.WideOpen,
            bounds = GPUBounds(0f, 0f, 16f, 16f),
            coveragePlan = GPUClipCoveragePlan.NoClip,
        ),
        paintOrder = order,
        source = GPUCommandSource("unit-test", "core-proxy", GPUFrameProvenance.GmContent),
    )

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "unit", "adapter", "device"),
        facts = listOf(
            GPUCapabilityFact("first_slice.fill_rect.native", "unit", "supported", true, "core"),
            GPUCapabilityFact("first_slice.scissor.native", "unit", "supported", true, "core"),
        ),
        snapshotId = "core-proxy",
        limits = GPULimits(
            8192,
            256,
            256,
            maxBufferSize = 1L shl 30,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
        supportedTextureFormats = setOf(GPUTextureFormat.RGBA8Unorm),
        rendererFeatures = setOf(GPURendererFeature.RenderPass, GPURendererFeature.Readback),
    )

    private fun GPUTaskList.withClipPlans(
        plans: Map<Int, GPUClipExecutionPlan>,
    ): GPUTaskList = GPUTaskList(
        frameId,
        capabilitySeal,
        recordingSeals,
        expectedReplayKeyHash,
        tasks.map { task ->
            if (task !is GPUTask.Render) return@map task
            val packets = task.drawPackets.map { packet ->
                packet.withClipPlan(requireNotNull(plans[packet.commandIdValue]))
            }
            GPUTask.Render(
                task.taskId,
                task.recordingId,
                task.phase,
                task.target,
                task.loadStore,
                task.samplePlan,
                task.resourceUses,
                task.provisionalSegmentKey,
                packets,
                packets.associate { it.packetId to requireNotNull(task.batchEligibilityByPacketId[it.packetId]) },
                task.sampleContinuationKey,
                task.compositeMembership,
            )
        },
        dependencies,
        phaseOrder,
        memoryBudget,
        diagnostics,
    )

    private fun GPUDrawPacket.withClipPlan(plan: GPUClipExecutionPlan) = GPUDrawPacket(
        packetId, commandIdValue, analysisRecordId, passId, layerId, bindingListId,
        insertionReasonCode, sortKey, sortKeyPreimage, renderStepId, renderStepVersion, role,
        blendPlan, renderPipelineKey, computePipelineKey, bindingLayoutHash, uniformSlot, resourceSlot,
        semanticPayload, vertexSourceLabel, scissorBoundsHash, targetStateHash, originalPaintOrder,
        resourceGeneration, frameProvenance, clipCoveragePlan, plan, diagnostics,
    )

    private fun GPUDrawPacket.withSemantic(semantic: GPUDrawSemanticPayload.CorePrimitive) = GPUDrawPacket(
        packetId, commandIdValue, analysisRecordId, passId, layerId, bindingListId,
        insertionReasonCode, sortKey, sortKeyPreimage, renderStepId, renderStepVersion, role,
        blendPlan, renderPipelineKey, computePipelineKey, bindingLayoutHash, uniformSlot, resourceSlot,
        semantic, vertexSourceLabel, scissorBoundsHash, targetStateHash, originalPaintOrder,
        resourceGeneration, frameProvenance, clipCoveragePlan, clipExecutionPlan, diagnostics,
    )

    private fun GPUDrawPacket.withPreparedAuthority(
        authority: org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitivePreparedPacketAuthority,
    ) = GPUDrawPacket(
        packetId, commandIdValue, analysisRecordId, passId, layerId, bindingListId,
        insertionReasonCode, sortKey, sortKeyPreimage, renderStepId, renderStepVersion, role,
        blendPlan, renderPipelineKey, computePipelineKey, bindingLayoutHash, uniformSlot, resourceSlot,
        semanticPayload, vertexSourceLabel, scissorBoundsHash, targetStateHash, originalPaintOrder,
        resourceGeneration, frameProvenance, clipCoveragePlan, clipExecutionPlan, diagnostics,
    ).attachCorePrimitivePreparedAuthority(authority)

    private fun GPUFramePlan.replacingPacket(
        original: GPUDrawPacket,
        replacement: GPUDrawPacket,
    ): GPUFramePlan = replacingStep(
        steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { original in it.drawPackets },
        steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single { original in it.drawPackets }
            .withPacket(original, replacement),
    )

    private fun GPUFrameStep.RenderPassStep.withPacket(
        original: GPUDrawPacket,
        replacement: GPUDrawPacket,
    ): GPUFrameStep.RenderPassStep {
        val packets = drawPackets.map { if (it === original) replacement else it }
        return GPUFrameStep.RenderPassStep(
            target,
            loadStore,
            samplePlan,
            resourceUses,
            packets,
            sourceTaskIds,
            batches.map { batch ->
                org.graphiks.kanvas.gpu.renderer.recording.GPUFrameRenderBatch(
                    batch.batchId,
                    batch.kind,
                    batch.packets.map { if (it === original) replacement else it },
                    batch.sourceTaskIds,
                )
            },
            sampleContinuation,
            depthStencilLoadStore,
        )
    }

    private fun GPUFrameStep.RenderPassStep.withLoadStore(
        replacement: org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan,
    ) = GPUFrameStep.RenderPassStep(
        target,
        replacement,
        samplePlan,
        resourceUses,
        drawPackets,
        sourceTaskIds,
        batches,
        sampleContinuation,
        depthStencilLoadStore,
    )

    private fun GPUFramePlan.replacingStep(original: GPUFrameStep, replacement: GPUFrameStep) = GPUFramePlan(
        frameId,
        capabilitySeal,
        recordingSeals,
        steps.map { if (it === original) replacement else it },
        memoryBudget,
        diagnostics,
        dependencies,
        phaseOrder,
        elidedNoOpDraws,
        atomicallyRefused,
    )

    private fun GPUFramePlan.withUniformPreparation(
        byteSize: Long,
        alignmentBytes: Long,
    ): GPUFramePlan {
        val preparation = steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>().single()
        val uniform = preparation.requests.single { it.role == GPUFrameResourceRole.UniformData }
        val replacement = GPUResourcePreparationRequest(
            uniform.resource,
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor(byteSize, alignmentBytes),
            uniform.role,
            uniform.usages,
            uniform.lifetime,
            byteSize,
            uniform.diagnosticLabel,
        )
        return replacingStep(
            preparation,
            GPUFrameStep.PrepareResourcesStep(
                preparation.requests.map { if (it === uniform) replacement else it },
                preparation.sourceTaskIds,
            ),
        )
    }

    private fun GPUFramePlan.withPreparation(
        role: GPUFrameResourceRole,
        transform: (GPUResourcePreparationRequest) -> GPUResourcePreparationRequest,
    ): GPUFramePlan {
        val preparation = steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>().single()
        val request = preparation.requests.single { it.role == role }
        return replacingStep(
            preparation,
            GPUFrameStep.PrepareResourcesStep(
                preparation.requests.map { if (it === request) transform(request) else it },
                preparation.sourceTaskIds,
            ),
        )
    }

    private data class Fixture(
        val plan: org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan,
        val encoderPlan: GPUCommandEncoderPlan,
        val resources: GPUPreparedResourceSet,
        val generationSeal: GPUPreparedGenerationSeal,
        val native: NativeProxy,
        val target: GPUWgpu4kPreparedSceneTarget,
        val cache: GPUWgpu4kCorePrimitiveSessionCache,
        val limits: GPULimits,
        val preparedByPreflight: PreparedGPUFrame? = null,
    ) {
        fun materializeCoreResult(): GPUPreparedNativeFramePayloadMaterialization {
            val materializer = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
                native.device,
                native.queue,
                target,
                cache,
                limits,
            )
            return materializer.materializeReusable(plan, encoderPlan, resources, generationSeal).also {
                materializer.close()
            }
        }

        fun materializeCore(): GPUPreparedNativeFramePayloadMaterialization.Materialized {
            val result = materializeCoreResult()
            return assertIs(
                result,
                (result as? GPUPreparedNativeFramePayloadMaterialization.Refused)?.let {
                    "${it.code}: ${it.message}"
                },
            )
        }

        fun close() {
            preparedByPreflight?.let { prepared ->
                check(prepared.claimForRollback())
                check(prepared.rollback.execute().successful)
            }
            cache.close()
            target.close()
        }
    }

    private class NativeProxy {
        val events = mutableListOf<String>()
        val writeBufferCalls = mutableListOf<WriteBufferCall>()
        val bindGroupLayoutDescriptors = mutableListOf<BindGroupLayoutDescriptor>()
        val textureDescriptors = mutableListOf<TextureDescriptor>()
        val renderPipelineDescriptors = mutableListOf<RenderPipelineDescriptor>()
        val closeCounts = IdentityHashMap<Any, Int>()
        private val createdHandlesByLabel = linkedMapOf<String, MutableList<Any>>()
        private val closeAttemptsByLabel = linkedMapOf<String, Int>()
        private var failingOperation: String? = null
        private var failingOperationOrdinal = 0
        private var operationInvocationCount = 0
        private var failingCloseLabel: String? = null
        private var closeFailureConsumed = false
        private val view = handle(GPUTextureView::class.java, "target.view")
        private val texture = handle(GPUTexture::class.java, "target.texture") { method ->
            if (method.name == "createView") view else null
        }
        val device: GPUDevice = proxy(GPUDevice::class.java) { method, args ->
            when (method.name) {
                "createTexture" -> {
                    val descriptor = args?.firstOrNull() as TextureDescriptor
                    textureDescriptors += descriptor
                    val label = descriptor.label.orEmpty()
                    events += "createTexture:$label"
                    failIfRequested("createTexture")
                    if (label == "Kanvas.session.corePrimitive.framePool.pathDepthStencil" ||
                        label == "Kanvas.session.corePrimitive.framePool.clipDepthStencil" ||
                        label == "Kanvas.session.corePrimitive.framePool.coverageMask"
                    ) {
                        val attachmentViewLabel = "$label.view"
                        handle(GPUTexture::class.java, label) { textureMethod ->
                            if (textureMethod.name == "createView") {
                                events += "createView:$attachmentViewLabel"
                                failIfRequested("createView")
                                recordedHandle(
                                    GPUTextureView::class.java,
                                    attachmentViewLabel,
                                ) as GPUTextureView
                            } else {
                                null
                            }
                        }.also { created ->
                            createdHandlesByLabel.getOrPut(label) { mutableListOf() } += created
                        }
                    } else {
                        texture
                    }
                }
                "createBuffer" -> {
                    val label = (args?.firstOrNull() as BufferDescriptor).label.orEmpty()
                    events += "createBuffer:$label"
                    failIfRequested("createBuffer")
                    recordedHandle(GPUBuffer::class.java, label)
                }
                "createBindGroup" -> {
                    val label = (args?.firstOrNull() as BindGroupDescriptor).label.orEmpty()
                    events += "createBindGroup:$label"
                    failIfRequested("createBindGroup")
                    recordedHandle(method.returnType, label)
                }
                "createBindGroupLayout" -> {
                    bindGroupLayoutDescriptors += args?.firstOrNull() as BindGroupLayoutDescriptor
                    events += method.name
                    handle(method.returnType, method.name)
                }
                "createRenderPipeline" -> {
                    renderPipelineDescriptors += args?.firstOrNull() as RenderPipelineDescriptor
                    events += method.name
                    handle(method.returnType, method.name)
                }
                "createShaderModule", "createPipelineLayout" -> {
                    events += method.name
                    handle(method.returnType, method.name)
                }
                else -> defaultValue(method.returnType)
            }
        }
        val queue: GPUQueue = proxy(GPUQueue::class.java) { method, args ->
            if (method.name.startsWith("writeBuffer")) {
                events += "writeBuffer"
                val data = args?.getOrNull(2) as io.ygdrasil.webgpu.ArrayBuffer
                writeBufferCalls += WriteBufferCall(
                    bufferLabel = args[0].toString(),
                    bufferOffset = (args[1] as Long).toULong(),
                    dataOffset = (args[3] as Long).toULong(),
                    dataBytes = data.size,
                    size = args[4] as ULong?,
                    snapshot = data.toByteArray(),
                    buffer = args[0] as GPUBuffer,
                )
                failIfRequested("writeBuffer")
            }
            defaultValue(method.returnType)
        }

        fun fail(operation: String, ordinal: Int) {
            require(
                operation in setOf(
                    "createTexture",
                    "createView",
                    "createBuffer",
                    "writeBuffer",
                    "createBindGroup",
                ) && ordinal > 0,
            )
            failingOperation = operation
            failingOperationOrdinal = ordinal
            operationInvocationCount = 0
        }

        fun failCloseOnce(label: String) {
            failingCloseLabel = label
            closeFailureConsumed = false
        }

        fun coreCloseAttempts(): Map<String, Int> = closeAttemptsByLabel.filterKeys { label ->
            label.startsWith("Kanvas.frame.corePrimitive.") ||
                label.startsWith("Kanvas.session.corePrimitive.framePool.")
        }

        fun createdHandles(label: String): List<Any> = createdHandlesByLabel[label].orEmpty()

        private fun failIfRequested(operation: String) {
            if (failingOperation != operation) return
            operationInvocationCount += 1
            if (operationInvocationCount == failingOperationOrdinal) {
                error("injected $operation failure")
            }
        }

        private fun <T> handle(
            type: Class<T>,
            label: String,
            extra: (java.lang.reflect.Method) -> Any? = { null },
        ): T = proxy(type) { method, _ ->
            when (method.name) {
                "close" -> {
                    val proxy = currentProxy.get()
                    closeCounts[proxy] = closeCounts.getOrDefault(proxy, 0) + 1
                    closeAttemptsByLabel[label] = closeAttemptsByLabel.getOrDefault(label, 0) + 1
                    events += "close:$label"
                    if (label == failingCloseLabel && !closeFailureConsumed) {
                        closeFailureConsumed = true
                        error("injected close failure")
                    }
                    null
                }
                "toString" -> label
                else -> extra(method) ?: defaultValue(method.returnType)
            }
        }

        private fun recordedHandle(type: Class<*>, label: String): Any =
            handle(type, label).also { created ->
                createdHandlesByLabel.getOrPut(label) { mutableListOf() } += created
            }

        private val currentProxy = ThreadLocal<Any>()

        @Suppress("UNCHECKED_CAST")
        private fun <T> proxy(
            type: Class<T>,
            action: (java.lang.reflect.Method, Array<out Any?>?) -> Any?,
        ): T {
            var created: Any? = null
            created = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
                currentProxy.set(proxy)
                try {
                    when (method.name) {
                        "hashCode" -> System.identityHashCode(proxy)
                        "equals" -> proxy === args?.firstOrNull()
                        else -> action(method, args)
                    }
                } finally {
                    currentProxy.remove()
                }
            }
            return created as T
        }

        private fun defaultValue(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> 0.toChar()
            else -> null
        }
    }

    private data class FailureCase(
        val operation: String,
        val ordinal: Int,
        val closedLabels: Set<String>,
        val readback: Boolean = false,
        val expectedCode: String = "failed.native-core-primitive.materialization",
    )

    private data class CoverageMaskMaterializationInput(
        val plan: GPUFramePlan,
        val encoderPlan: GPUCommandEncoderPlan,
        val resources: GPUPreparedResourceSet,
        val generationSeal: GPUPreparedGenerationSeal,
    )

    private data class WriteBufferCall(
        val bufferLabel: String,
        val bufferOffset: ULong,
        val dataOffset: ULong,
        val dataBytes: ULong,
        val size: ULong?,
        val snapshot: ByteArray = EMPTY_UPLOAD_SNAPSHOT,
        val buffer: GPUBuffer? = null,
    )

    private companion object {
        val TARGET = GPUPixelBounds(0, 0, 16, 16)
        val EMPTY_UPLOAD_SNAPSHOT = ByteArray(0)
    }
}
