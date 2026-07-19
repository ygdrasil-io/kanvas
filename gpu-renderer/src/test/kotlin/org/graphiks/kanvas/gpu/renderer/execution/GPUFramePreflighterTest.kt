package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureView
import java.io.File
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.graphiks.kanvas.gpu.renderer.analysis.corePrimitiveRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.analysis.corePrimitiveRRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureFormatSampleSupport
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureSampleCountSupport
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipAnalyticElement
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipAtomicGroupID
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionGeometry
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
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds as GPUCommandBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPURRect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectCornerRadii
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectNormalizationResult
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectNormalizer
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroupKey
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateIdentity
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveClipStencilPreparedCandidate
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveDirectPathDepthStencilState
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUClipProducerAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitivePreparedPacketAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticClipUniformSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticIntersectionElementSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticIntersectionUniformSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveUniformSlabSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskUniformSlabSeal
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchAdjacency
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommand
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandStream
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleContinuationKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleContinuationRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleLoadTransition
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleResolveAction
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleStoreAction
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveFillRule
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadInput
import org.graphiks.kanvas.gpu.renderer.analysis.GPUCorePrimitiveRRectGeometryAuthorityIssue
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRectRouteAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.CORE_PRIMITIVE_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.GPUMaterialPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadGatherPlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadFingerprint
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot
import org.graphiks.kanvas.gpu.renderer.payloads.GPUSolidPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUComputePipelineKey
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.recording.GPUComputeDispatch
import org.graphiks.kanvas.gpu.renderer.recording.GPUCompositeProvenanceToken
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_ANALYTIC_INTERSECTION_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_RENDER_PIPELINE_KEY
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_TARGET_STATE_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_VERTEX_SOURCE_LABEL
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_MASK_CLEAR_COLOR_LABEL
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveRenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveClipProducerPipelineKey
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveDepthStencilByteSize
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveScissorAuthority
import org.graphiks.kanvas.gpu.renderer.recording.GPUDestinationSnapshotConsumerRef
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameCapabilitySeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameTaskListBuilder
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameReadbackRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameRenderBatch
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackPixelFormat
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingSeal
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPUSurfaceOutputDescriptor
import org.graphiks.kanvas.gpu.renderer.recording.GPUSurfaceOutputRef
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskDependency
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskAtomicGroupID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskUseToken
import org.graphiks.kanvas.gpu.renderer.recording.GPUDepthStencilLoadStorePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUStencilLoadOperation
import org.graphiks.kanvas.gpu.renderer.recording.GPUTargetTransitionKind
import org.graphiks.kanvas.gpu.renderer.recording.PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION
import org.graphiks.kanvas.gpu.renderer.resources.GPUCommandOperandMaterializationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPayload
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabSlot
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlanner
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlanningResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreflightProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreparationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreparationInput
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreparationSession
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandReference
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUPhysicalPoolMaintenanceDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUPhysicalPoolRollbackSummary
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedConcreteResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUBufferResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUReadbackStagingLease
import org.graphiks.kanvas.gpu.renderer.resources.GPUReadbackStagingRollbackResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceCopyRegion
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceDiagnostic
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLease
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseCacheResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureCopyLayout
import org.graphiks.kanvas.gpu.renderer.resources.GPUUploadLayout
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUTargetIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GPUFramePreflighterTest {
    @Test
    fun `core semantic blend provenance and clip mismatches refuse before preflight side effects`() {
        val semantic = coreSemantic()
        val cases = listOf(
            coreRenderStep(semantic, blendPlan = coreBlend(GPUBlendMode.SRC)),
            coreRenderStep(semantic, provenance = GPUFrameProvenance.HarnessBackground),
            coreRenderStep(
                semantic,
                clip = GPUClipCoveragePlan.Scissor(GPUBounds(0f, 0f, 4f, 4f)),
            ),
        )

        cases.forEach { render ->
            val events = mutableListOf<String>()
            val result = preflighter(
                resources = RecordingResourceProvider(events),
                completion = RecordingCompletionProvider(events),
                surface = RecordingSurfaceProvider(events),
            ).preflight(framePlan(listOf(prepareScene(), render)))

            assertEquals(
                "invalid.preflight.core_primitive_semantic_integrity",
                assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
            )
            assertTrue(events.isEmpty(), "pure validation side effects: $events")
        }
    }

    @Test
    fun `closed coverage route cannot mask a forged core semantic envelope`() {
        val semantic = coreSemantic(coverageMode = GPUCorePrimitiveCoverageMode.ScalarAA)
        val render = coreRenderStep(
            semantic,
            packetCommandIdValue = semantic.payloadRef.commandIdValue + 1,
        )
        val events = mutableListOf<String>()
        val resources = RecordingResourceProvider(events)

        val result = preflighter(
            resources = resources,
            completion = RecordingCompletionProvider(events),
            surface = RecordingSurfaceProvider(events),
        ).preflight(
            framePlan(
                listOf(
                    prepareScene(),
                    render,
                ),
            ),
        )

        assertEquals(
            "invalid.preflight.core_primitive_semantic_integrity",
            assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
        )
        assertEquals(0, resources.beginFramePreparationCount)
        assertTrue(events.isEmpty(), "semantic envelope refusal produced side effects: $events")
    }

    @Test
    fun `later closed coverage route cannot mask an earlier missing core semantic`() {
        val events = mutableListOf<String>()
        val resources = RecordingResourceProvider(events)
        val plan = framePlan(
            listOf(
                prepareScene(),
                coreRenderStep(null, packetId = "packet.core.missing"),
                coreRenderStep(
                    coreSemantic(coverageMode = GPUCorePrimitiveCoverageMode.ScalarAA),
                    packetId = "packet.core.scalar",
                ),
            ),
        )
        val result = preflighter(
            resources = resources,
            completion = RecordingCompletionProvider(events),
            surface = RecordingSurfaceProvider(events),
        ).preflight(plan)

        assertEquals(
            "invalid.preflight.core_primitive_semantic_payload_missing",
            assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
        )
        assertEquals(0, resources.beginFramePreparationCount)
        assertTrue(events.isEmpty(), "missing semantic refusal produced side effects: $events")

        val nativeEvents = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val concreteResources = GPUConcreteResourceProvider(leaseFactory = adapter)
        try {
            val nativeResult = preflighter(
                resources = concreteResources,
                completion = RecordingCompletionProvider(nativeEvents),
                surface = RecordingSurfaceProvider(nativeEvents),
                nativeBoundary = adapter.bindNativeFrameBoundary(
                    concreteResources,
                    RenderOnlyNativePayloadMaterializer(nativeEvents),
                ),
            ).preflight(plan)

            assertEquals(
                "invalid.preflight.core_primitive_semantic_payload_missing",
                assertIs<GPUFramePreflightResult.Refused>(nativeResult).diagnostic.code.value,
            )
            assertTrue(nativeEvents.isEmpty(), "missing semantic reached native work: $nativeEvents")
            assertEquals(0, adapter.activePreparedNativeFramePayloadCount)
            assertEquals(0, concreteResources.pendingPhysicalReservationCount)
        } finally {
            adapter.close()
        }
    }

    @Test
    fun `core semantic clip execution identity mismatch refuses before preflight side effects`() {
        val semantic = coreSemantic(GPUClipExecutionPlan.NoClip)
        val substituted = GPUClipExecutionPlan.ScissorOnly(GPUPixelBounds(0, 0, 4, 4))
        val events = mutableListOf<String>()

        val result = preflighter(
            resources = RecordingResourceProvider(events),
            completion = RecordingCompletionProvider(events),
            surface = RecordingSurfaceProvider(events),
        ).preflight(
            framePlan(
                listOf(
                    prepareScene(),
                    coreRenderStep(semantic, clipExecutionPlan = substituted),
                ),
            ),
        )

        assertEquals(
            "invalid.preflight.core_primitive_semantic_integrity",
            assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
        )
        assertTrue(events.isEmpty(), "pure validation side effects: $events")
    }

    @Test
    fun `core stencil producer target depth stencil and generation corruption refuse before side effects`() {
        val scenarios = listOf(
            coreStencilFramePlan(producerTarget = GPUFrameTargetRef("target.substituted")),
            coreStencilFramePlan(depthStencilBounds = GPUPixelBounds(0, 0, 3, 4)),
            coreStencilFramePlan(producerGeneration = 1L),
            coreStencilFramePlan(producerDepthStencilWrite = false),
        )

        scenarios.forEach { plan ->
            val events = mutableListOf<String>()
            val result = preflighter(
                resources = RecordingResourceProvider(events),
                completion = RecordingCompletionProvider(events),
                surface = RecordingSurfaceProvider(events),
            ).preflight(plan)

            assertEquals(
                "invalid.preflight.core_primitive_clip_producer_authority",
                assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
            )
            assertTrue(events.isEmpty(), "pure validation side effects: $events")
        }
    }

    @Test
    fun `sealed core stencil producer and consumer accept late bound packet with scene and clip generations`() {
        val plan = coreStencilFramePlan()
        val events = mutableListOf<String>()

        val result = preflighter(
            resources = RecordingResourceProvider(events),
            completion = RecordingCompletionProvider(events),
            surface = RecordingSurfaceProvider(events),
            context = clipPreflightContext(plan),
        ).preflight(plan)

        assertIs<GPUFramePreflightResult.Prepared>(result)
        assertTrue(events.any { it.startsWith("resource:prepare:") })
    }

    @Test
    fun `native path clip stencil preflight retains exact producer and consumer scope seals`() {
        val plan = preparedNativeClipStencilFramePlan()
        val events = mutableListOf<String>()

        val result = preflighter(
            resources = RecordingResourceProvider(events),
            completion = RecordingCompletionProvider(events),
            surface = RecordingSurfaceProvider(events),
            context = clipPreflightContext(plan),
            capabilities = pathCapabilities(),
        ).preflight(plan)
        val prepared = assertIs<GPUFramePreflightResult.Prepared>(
            result,
            (result as? GPUFramePreflightResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}"
            },
        ).frame

        val renders = plan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        val scopesByStep = prepared.encoderPlan.scopes.associateBy { it.sourceStepIndex }
        val producer = renders.single { render ->
            render.drawPackets.singleOrNull()?.role == GPUDrawPacketRole.StencilProducer
        }
        val consumers = renders.filter { render ->
            render.drawPackets.singleOrNull()?.role == GPUDrawPacketRole.Shading
        }
        assertEquals(2, consumers.size)

        val producerStepIndex = plan.steps.indexOf(producer)
        val producerScope = assertIs<GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer>(
            scopesByStep.getValue(producerStepIndex).corePrimitiveClipStencilPreparedRouteSeal,
        )
        val consumerScopes = consumers.map { consumer ->
            val sourceStepIndex = plan.steps.indexOf(consumer)
            assertIs<GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Consumer>(
                scopesByStep.getValue(sourceStepIndex).corePrimitiveClipStencilPreparedRouteSeal,
            )
        }
        val accepted = producerScope.route
        assertTrue(consumerScopes.all { it.route === accepted })
        val candidate = requireNotNull(
            producer.drawPackets.single().corePrimitiveClipStencilPreparedCandidate,
        )
        assertEquals(producer.drawPackets.single().packetId, producerScope.packetId)
        assertEquals(
            consumers.map { it.drawPackets.single().packetId },
            consumerScopes.map { it.packetId },
        )
        val slices = listOf(producerScope.geometrySlice) + consumerScopes.map { it.geometrySlice }
        assertEquals(40, slices.sumOf { it.vertexCount } * 2)
        assertEquals(24, slices.sumOf { it.indexCount })
        assertEquals(
            GPUCorePrimitiveClipStencilPreparedGeometrySlice(
                producerStepIndex,
                producerScope.packetId,
                producer.drawPackets.single().commandIdValue,
                GPUCorePrimitiveClipStencilPreparedGeometryRole.Producer,
                0,
                12,
                0,
                12,
                11,
            ),
            producerScope.geometrySlice,
        )
        assertEquals(
            listOf(
                GPUCorePrimitiveClipStencilPreparedGeometrySlice(
                    plan.steps.indexOf(consumers[0]),
                    consumerScopes[0].packetId,
                    consumers[0].drawPackets.single().commandIdValue,
                    GPUCorePrimitiveClipStencilPreparedGeometryRole.Consumer,
                    12,
                    6,
                    12,
                    4,
                    3,
                ),
                GPUCorePrimitiveClipStencilPreparedGeometrySlice(
                    plan.steps.indexOf(consumers[1]),
                    consumerScopes[1].packetId,
                    consumers[1].drawPackets.single().commandIdValue,
                    GPUCorePrimitiveClipStencilPreparedGeometryRole.Consumer,
                    18,
                    6,
                    16,
                    4,
                    3,
                ),
            ),
            consumerScopes.map { it.geometrySlice },
        )
        assertEquals(candidate.consumers.map { it.sourceOrder }, consumerScopes.map { it.sourceOrder })
        assertEquals(listOf(false, true), consumerScopes.map { it.isLastConsumer })
        assertEquals(
            candidate.consumers.map { it.dependencyFromPreviousConsumerToken },
            consumerScopes.map { it.dependencyFromPreviousConsumerToken },
        )
        assertEquals(1L, producerScope.slabAuthority.vertexGeneration)
        assertEquals(1L, producerScope.slabAuthority.indexGeneration)
        assertEquals(1L, producerScope.slabAuthority.uniformGeneration)
        assertEquals(
            listOf(0L, 256L),
            consumerScopes.map { it.uniformSlice.alignedOffset },
        )
        assertEquals(listOf(32L, 32L), consumerScopes.map { it.uniformSlice.payloadBytes })
        assertTrue(consumerScopes.all {
            it.uniformSlice.resource == producerScope.slabAuthority.uniformResource
        })
        assertTrue(consumerScopes.all { it.slabAuthority === producerScope.slabAuthority })
        assertTrue(consumerScopes.all {
            it.attachmentAuthority === producerScope.attachmentAuthority
        })
        assertEquals(1L, producerScope.attachmentAuthority.resourceGeneration)
        assertEquals(
            accepted.attachment.logicalReference,
            producerScope.attachmentAuthority.resource.value,
        )
        val producerEncoderScope = scopesByStep.getValue(producerStepIndex)
        val consumerEncoderScopes = consumers.map { consumer ->
            scopesByStep.getValue(plan.steps.indexOf(consumer))
        }
        assertEquals(
            listOf(
                GPUPreparedNativeOperandRole.RenderColorTarget,
                GPUPreparedNativeOperandRole.RenderDepthStencilTarget,
                GPUPreparedNativeOperandRole.RenderPipeline,
                GPUPreparedNativeOperandRole.RenderVertexBuffer,
                GPUPreparedNativeOperandRole.RenderIndexBuffer,
            ),
            producerEncoderScope.nativeOperandKeys.map(GPUPreparedNativeOperandKey::role),
        )
        val expectedConsumerRoles = listOf(
                GPUPreparedNativeOperandRole.RenderColorTarget,
                GPUPreparedNativeOperandRole.RenderDepthStencilTarget,
                GPUPreparedNativeOperandRole.RenderPipeline,
                GPUPreparedNativeOperandRole.RenderVertexBuffer,
                GPUPreparedNativeOperandRole.RenderIndexBuffer,
                GPUPreparedNativeOperandRole.RenderBindGroup,
            )
        assertTrue(consumerEncoderScopes.all { scope ->
            scope.nativeOperandKeys.map(GPUPreparedNativeOperandKey::role) == expectedConsumerRoles
        })
        assertTrue((listOf(producerEncoderScope) + consumerEncoderScopes).flatMap {
            it.nativeOperandKeys
        }.all { key -> key.ownership == GPUPreparedNativeOperandOwnership.Borrowed })
        val depthStencilKeys = (listOf(producerEncoderScope) + consumerEncoderScopes).map { scope ->
            scope.nativeOperandKeys.single {
                it.role == GPUPreparedNativeOperandRole.RenderDepthStencilTarget
            }
        }
        assertTrue(depthStencilKeys.all {
            it.kind == GPUPreparedNativeOperandKind.TextureView &&
                it.bindingKey == gpuPreparedNativeBindingKey(
                    "GPUFrameTextureRef:${producerScope.attachmentAuthority.resource.value}@" +
                        producerScope.attachmentAuthority.resourceGeneration,
                )
        })
        val vertexKeys = (listOf(producerEncoderScope) + consumerEncoderScopes).map { scope ->
            scope.nativeOperandKeys.single {
                it.role == GPUPreparedNativeOperandRole.RenderVertexBuffer
            }.bindingKey
        }
        val indexKeys = (listOf(producerEncoderScope) + consumerEncoderScopes).map { scope ->
            scope.nativeOperandKeys.single {
                it.role == GPUPreparedNativeOperandRole.RenderIndexBuffer
            }.bindingKey
        }
        assertEquals(1, vertexKeys.distinct().size)
        assertEquals(1, indexKeys.distinct().size)
        assertTrue(producerEncoderScope.passCommandStream!!.operandBridge.none {
            it.operand.kind == GPUMaterializedCommandOperandKind.BindGroup
        })
        assertTrue(consumerEncoderScopes.all { scope ->
            scope.passCommandStream!!.operandBridge.map { it.operand.kind }.toSet() == setOf(
                GPUMaterializedCommandOperandKind.RenderPipeline,
                GPUMaterializedCommandOperandKind.VertexBuffer,
                GPUMaterializedCommandOperandKind.IndexBuffer,
                GPUMaterializedCommandOperandKind.BindGroup,
            )
        })
        assertTrue(events.any { it.startsWith("resource:prepare:") })
    }

    @Test
    fun `native coverage mask preflight accepts the builder sealed frame`() {
        val plan = preparedCoverageMaskFramePlan()
        val events = mutableListOf<String>()

        val result = preflighter(
            resources = RecordingResourceProvider(events),
            completion = RecordingCompletionProvider(events),
            surface = RecordingSurfaceProvider(events),
            context = clipPreflightContext(plan),
            capabilities = pathCapabilities(),
        ).preflight(plan)

        val prepared = assertIs<GPUFramePreflightResult.Prepared>(
            result,
            (result as? GPUFramePreflightResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message} ${it.facts}"
            },
        ).frame
        val renderScopes = prepared.encoderPlan.scopes.filter {
            it.operationKind == GPUEncoderOperationKind.Render
        }
        val producerScopes = renderScopes.mapNotNull { scope ->
            (scope.corePrimitiveCoverageMaskPreparedRouteSeal as?
                GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer)?.let { scope to it }
        }
        val consumerScopes = renderScopes.mapNotNull { scope ->
            (scope.corePrimitiveCoverageMaskPreparedRouteSeal as?
                GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer)?.let { scope to it }
        }
        assertEquals(2, producerScopes.size)
        assertEquals(2, consumerScopes.size)
        val accepted = producerScopes.first().second.route
        assertTrue(producerScopes.all { it.second.route === accepted })
        assertTrue(consumerScopes.all { it.second.route === accepted })
        val slabs = producerScopes.first().second.slabAuthority
        val attachment = producerScopes.first().second.attachmentAuthority
        assertTrue(producerScopes.all { it.second.slabAuthority === slabs })
        assertTrue(consumerScopes.all { it.second.slabAuthority === slabs })
        assertTrue(producerScopes.all {
            it.second.attachmentAuthority === attachment
        })
        assertTrue(consumerScopes.all {
            it.second.attachmentAuthority === attachment
        })
        assertEquals(1L, attachment.resourceGeneration)
        assertEquals(1L, slabs.vertexGeneration)
        assertEquals(1L, slabs.indexGeneration)
        assertEquals(1L, slabs.uniformGeneration)
        assertEquals(
            listOf(0L, 256L),
            producerScopes.map { it.second.uniformSlice.alignedOffset },
        )
        assertEquals(
            listOf(512L, 768L),
            consumerScopes.map { it.second.uniformSlice.alignedOffset },
        )
        assertEquals(
            slabs.uniformSlabSeal.consumerSlots.map {
                it.dependencyFromPreviousConsumerToken
            },
            consumerScopes.map { it.second.dependencyFromPreviousConsumerToken },
        )
        assertEquals(
            List(2) { GPUCorePrimitiveCoverageMaskPreparedDraw.Draw(3) },
            producerScopes.map { it.second.draw },
        )
        assertEquals(
            listOf(
                GPUCorePrimitiveCoverageMaskPreparedDraw.DrawIndexed(6, 0, 0),
                GPUCorePrimitiveCoverageMaskPreparedDraw.DrawIndexed(3, 6, 4),
            ),
            consumerScopes.map { it.second.draw },
        )
        assertEquals(
            listOf(
                GPUCorePrimitiveCoverageMaskPreparedGeometrySlice(0, 6, 0, 4),
                GPUCorePrimitiveCoverageMaskPreparedGeometrySlice(6, 3, 4, 3),
            ),
            consumerScopes.map { it.second.geometrySlice },
        )
        producerScopes.forEach { (scope, _) ->
            assertEquals(
                listOf(
                    "beginRenderPass",
                    "setRenderPipeline",
                    "setBindGroup",
                    "draw",
                    "endRenderPass",
                ),
                scope.passCommandStream!!.commandLabels,
            )
            assertEquals(
                listOf(
                    GPUPreparedNativeOperandRole.RenderColorTarget,
                    GPUPreparedNativeOperandRole.RenderPipeline,
                    GPUPreparedNativeOperandRole.RenderBindGroup,
                ),
                scope.nativeOperandKeys.map { it.role },
            )
            assertEquals(
                listOf(
                    GPUMaterializedCommandOperandKind.RenderPipeline,
                    GPUMaterializedCommandOperandKind.BindGroup,
                ),
                scope.passCommandStream.operandBridge.map { it.operand.kind },
            )
            assertEquals(scope.resourceGenerationLabels[0], scope.resourceGenerationLabels[1])
            assertEquals(1, scope.nativeOperandKeys.count {
                it.role == GPUPreparedNativeOperandRole.RenderColorTarget
            })
        }
        consumerScopes.forEach { (scope, seal) ->
            assertEquals(
                listOf(
                    "beginRenderPass",
                    "setRenderPipeline",
                    "setBindGroup",
                    "setVertexBuffer",
                    "setIndexBuffer",
                    "draw",
                    "endRenderPass",
                ),
                scope.passCommandStream!!.commandLabels,
            )
            assertEquals(
                listOf(
                    GPUPreparedNativeOperandRole.RenderColorTarget,
                    GPUPreparedNativeOperandRole.RenderPipeline,
                    GPUPreparedNativeOperandRole.RenderVertexBuffer,
                    GPUPreparedNativeOperandRole.RenderIndexBuffer,
                    GPUPreparedNativeOperandRole.RenderBindGroup,
                ),
                scope.nativeOperandKeys.map { it.role },
            )
            assertEquals(
                listOf(
                    GPUMaterializedCommandOperandKind.RenderPipeline,
                    GPUMaterializedCommandOperandKind.BindGroup,
                    GPUMaterializedCommandOperandKind.VertexBuffer,
                    GPUMaterializedCommandOperandKind.IndexBuffer,
                ),
                scope.passCommandStream.operandBridge.map { it.operand.kind },
            )
            assertEquals(1L, seal.sceneTargetGeneration)
        }
        assertTrue(renderScopes.flatMap { it.nativeOperandKeys }.none {
            it.role == GPUPreparedNativeOperandRole.RenderDepthStencilTarget
        })
        val producerLocations = producerScopes.map { (_, seal) ->
            GPUCorePrimitiveCoverageMaskPreparedProducerLocation(
                seal.sourceStepIndex,
                seal.packetId,
                seal.commandId,
                seal.sourceOrder,
            )
        }
        val consumerLocations = consumerScopes.map { (_, seal) ->
            GPUCorePrimitiveCoverageMaskPreparedConsumerLocation(
                seal.sourceStepIndex,
                seal.packetId,
                seal.commandId,
                seal.sourceOrder,
                seal.dependencyFromPreviousConsumerToken,
                seal.geometrySlice,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            sealGPUCorePrimitiveCoverageMaskPreparedFrameRoute(
                accepted,
                slabs,
                attachment,
                consumerScopes.first().second.sceneTarget,
                consumerScopes.first().second.sceneTargetGeneration,
                producerLocations.mapIndexed { index, location ->
                    if (index == 0) location.copy(commandId = location.commandId + 1) else location
                },
                consumerLocations,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            sealGPUCorePrimitiveCoverageMaskPreparedFrameRoute(
                accepted,
                slabs,
                attachment,
                consumerScopes.first().second.sceneTarget,
                consumerScopes.first().second.sceneTargetGeneration,
                producerLocations,
                consumerLocations.mapIndexed { index, location ->
                    if (index == 1) {
                        location.copy(dependencyFromPreviousConsumerToken = "forged.consumer.token")
                    } else {
                        location
                    }
                },
            )
        }

        fun detached(
            scope: GPUCommandEncoderScopePlan,
            resourceLabels: List<String> = scope.resourceGenerationLabels,
            facadeOperations: List<String> = scope.facadeOperationClasses,
            passCommandStream: GPUPassCommandStream? = scope.passCommandStream,
            coverageMaskSeal: GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal =
                scope.corePrimitiveCoverageMaskPreparedRouteSeal,
        ) = GPUCommandEncoderScopePlan(
            sourceStepIndex = scope.sourceStepIndex,
            operationKind = scope.operationKind,
            scopeLabel = scope.scopeLabel,
            sourceTaskIds = scope.sourceTaskIds,
            sourcePacketIds = scope.sourcePacketIds,
            facadeOperationClasses = facadeOperations,
            targetGeneration = scope.targetGeneration,
            resourceGenerationLabels = resourceLabels,
            passCommandStream = passCommandStream,
            corePrimitiveDirectNativeRouteSeal = scope.corePrimitiveDirectNativeRouteSeal,
            corePrimitivePathStencilNativeRouteSeal = scope.corePrimitivePathStencilNativeRouteSeal,
            corePrimitiveNativeScopeRouteSeal = scope.corePrimitiveNativeScopeRouteSeal,
            corePrimitiveClipStencilPreparedRouteSeal =
                scope.corePrimitiveClipStencilPreparedRouteSeal,
            corePrimitiveCoverageMaskPreparedRouteSeal =
                coverageMaskSeal,
        )
        assertFailsWith<IllegalArgumentException> {
            detached(producerScopes.first().first).attachNativeOperandKeys(
                producerScopes.first().first.nativeOperandKeys + GPUPreparedNativeOperandKey(
                    GPUPreparedNativeOperandRole.RenderVertexBuffer,
                    GPUPreparedNativeOperandKind.Buffer,
                    gpuPreparedNativeBindingKey("coverage-mask.forged.vertex"),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            detached(consumerScopes.first().first).attachNativeOperandKeys(
                consumerScopes.first().first.nativeOperandKeys.filterNot {
                    it.role == GPUPreparedNativeOperandRole.RenderBindGroup
                },
            )
        }
        assertFailsWith<IllegalArgumentException> {
            val scope = consumerScopes.first().first
            detached(
                scope,
                scope.resourceGenerationLabels.mapIndexed { index, label ->
                    if (index == scope.resourceGenerationLabels.lastIndex) "$label.forged" else label
                },
            ).attachNativeOperandKeys(scope.nativeOperandKeys)
        }
        fun forgedStream(
            scope: GPUCommandEncoderScopePlan,
            commands: List<GPUPassCommand> = requireNotNull(scope.passCommandStream).commands,
            operandBridge: List<org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandOperandBridge> =
                requireNotNull(scope.passCommandStream).operandBridge,
            passId: String = requireNotNull(scope.passCommandStream).passId,
            sourcePassIds: List<String> = requireNotNull(scope.passCommandStream).sourcePassIds,
        ): GPUPassCommandStream {
            val stream = requireNotNull(scope.passCommandStream)
            return GPUPassCommandStream(
                stream.streamId,
                stream.packetStreamId,
                passId,
                commands,
                stream.diagnostics,
                operandBridge,
                sourcePassIds,
            )
        }
        fun assertTypedCommandForgeryRefused(
            scope: GPUCommandEncoderScopePlan = consumerScopes.first().first,
            passId: ((String) -> String)? = null,
            sourcePassIds: ((List<String>) -> List<String>)? = null,
            transform: (GPUPassCommand) -> GPUPassCommand = { it },
        ) {
            val current = requireNotNull(scope.passCommandStream)
            val forged = forgedStream(
                scope,
                commands = current.commands.map(transform),
                passId = passId?.invoke(current.passId) ?: current.passId,
                sourcePassIds = sourcePassIds?.invoke(current.sourcePassIds)
                    ?: current.sourcePassIds,
            )
            assertFailsWith<IllegalArgumentException> {
                detached(
                    scope,
                    facadeOperations = forged.commandLabels,
                    passCommandStream = forged,
                ).attachNativeOperandKeys(scope.nativeOperandKeys)
            }
        }
        assertTypedCommandForgeryRefused { command ->
            if (command is GPUPassCommand.BeginRenderPass) {
                command.copy(targetStateHash = "${command.targetStateHash}.forged")
            } else command
        }
        assertTypedCommandForgeryRefused { command ->
            if (command is GPUPassCommand.BeginRenderPass) {
                command.copy(loadStoreLabel = "${command.loadStoreLabel}.forged")
            } else command
        }
        assertTypedCommandForgeryRefused { command ->
            if (command is GPUPassCommand.SetRenderPipeline) {
                command.copy(pipelineKey = GPURenderPipelineKey("coverage-mask.forged.pipeline"))
            } else command
        }
        assertTypedCommandForgeryRefused { command ->
            if (command is GPUPassCommand.SetBindGroup) {
                command.copy(bindingLayoutHash = "${command.bindingLayoutHash}.forged")
            } else command
        }
        assertTypedCommandForgeryRefused { command ->
            if (command is GPUPassCommand.SetBindGroup) {
                command.copy(uniformSlot = command.uniformSlot?.copy(
                    slotId = org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID(
                        "coverage-mask.forged.uniform-slot",
                    ),
                    byteOffset = command.uniformSlot.byteOffset + 4L,
                ))
            } else command
        }
        assertTypedCommandForgeryRefused { command ->
            if (command is GPUPassCommand.SetBindGroup) {
                command.copy(resourceSlot = org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot(
                    org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID(
                        "coverage-mask.forged.resource-slot",
                    ),
                    requireNotNull(command.uniformSlot).fingerprint,
                    1,
                ))
            } else command
        }
        assertTypedCommandForgeryRefused { command ->
            if (command is GPUPassCommand.SetVertexBuffer) command.copy(slot = command.slot + 1)
            else command
        }
        assertTypedCommandForgeryRefused { command ->
            if (command is GPUPassCommand.SetIndexBuffer) command.copy(indexFormatLabel = "uint16")
            else command
        }
        assertTypedCommandForgeryRefused { command ->
            if (command is GPUPassCommand.Draw) {
                command.copy(vertexSourceLabel = "${command.vertexSourceLabel}.forged")
            } else command
        }
        assertTypedCommandForgeryRefused(
            transform = { command ->
                if (command is GPUPassCommand.EndRenderPass) {
                    command.copy(passId = "${command.passId}.forged")
                } else command
            },
        )
        assertTypedCommandForgeryRefused(passId = { "$it.forged" })
        assertTypedCommandForgeryRefused(sourcePassIds = { listOf("forged.source-pass") })
        val consumerUniformSlot = requireNotNull(
            requireNotNull(consumerScopes.first().first.passCommandStream).commands
                .filterIsInstance<GPUPassCommand.SetBindGroup>()
                .single()
                .uniformSlot,
        )
        val producerCommandScope = producerScopes.first().first
        assertTypedCommandForgeryRefused(scope = producerCommandScope) { command ->
            if (command is GPUPassCommand.SetBindGroup) {
                command.copy(uniformSlot = consumerUniformSlot)
            } else command
        }
        assertTypedCommandForgeryRefused(scope = producerCommandScope) { command ->
            if (command is GPUPassCommand.SetBindGroup) {
                command.copy(resourceSlot = org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot(
                    org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID(
                        "coverage-mask.forged.producer-resource-slot",
                    ),
                    consumerUniformSlot.fingerprint,
                    0,
                ))
            } else command
        }
        assertTypedCommandForgeryRefused(scope = producerCommandScope) { command ->
            if (command is GPUPassCommand.Draw) {
                GPUPassCommand.SetVertexBuffer(0, command.packetId)
            } else command
        }
        fun forgedConsumerSeal(
            seal: GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer,
            uniformSlice: GPUCorePrimitiveCoverageMaskPreparedUniformSlice = seal.uniformSlice,
            geometrySlice: GPUCorePrimitiveCoverageMaskPreparedGeometrySlice = seal.geometrySlice,
        ) = GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer(
            sourceStepIndex = seal.sourceStepIndex,
            packetId = seal.packetId,
            commandId = seal.commandId,
            sourceOrder = seal.sourceOrder,
            dependencyFromPreviousConsumerToken = seal.dependencyFromPreviousConsumerToken,
            isLastConsumer = seal.isLastConsumer,
            route = seal.route,
            slabAuthority = seal.slabAuthority,
            attachmentAuthority = seal.attachmentAuthority,
            uniformSlice = uniformSlice,
            geometrySlice = geometrySlice,
            sceneTarget = seal.sceneTarget,
            sceneTargetGeneration = seal.sceneTargetGeneration,
            commandAuthority = seal.commandAuthority,
        )
        val consumerScope = consumerScopes.first().first
        val consumerSeal = consumerScopes.first().second
        listOf(
            consumerSeal.uniformSlice.copy(slotIndex = consumerSeal.uniformSlice.slotIndex + 1),
            consumerSeal.uniformSlice.copy(alignedOffset = consumerSeal.uniformSlice.alignedOffset + 256L),
            consumerSeal.uniformSlice.copy(allocatedBytes = consumerSeal.uniformSlice.allocatedBytes + 256L),
        ).forEach { uniformSlice ->
            assertFailsWith<IllegalArgumentException> {
                detached(
                    consumerScope,
                    coverageMaskSeal = forgedConsumerSeal(consumerSeal, uniformSlice = uniformSlice),
                ).attachNativeOperandKeys(consumerScope.nativeOperandKeys)
            }
        }
        assertFailsWith<IllegalArgumentException> {
            val geometry = consumerSeal.geometrySlice.copy(
                firstIndex = consumerSeal.geometrySlice.firstIndex + 1,
            )
            detached(
                consumerScope,
                coverageMaskSeal = forgedConsumerSeal(consumerSeal, geometrySlice = geometry),
            ).attachNativeOperandKeys(consumerScope.nativeOperandKeys)
        }
        assertFailsWith<IllegalArgumentException> {
            val scope = consumerScopes.first().first
            val packetId = GPUDrawPacketID("packet.coverage-mask.forged-bridge")
            val stream = requireNotNull(scope.passCommandStream)
            val commands = stream.commands.map { command ->
                when (command) {
                    is GPUPassCommand.SetRenderPipeline -> command.copy(packetId = packetId)
                    is GPUPassCommand.SetBindGroup -> command.copy(packetId = packetId)
                    is GPUPassCommand.SetVertexBuffer -> command.copy(packetId = packetId)
                    is GPUPassCommand.SetIndexBuffer -> command.copy(packetId = packetId)
                    is GPUPassCommand.Draw -> command.copy(packetId = packetId)
                    else -> command
                }
            }
            val bridges = stream.operandBridge.map { bridge -> bridge.copy(packetId = packetId) }
            val forged = forgedStream(scope, commands = commands, operandBridge = bridges)
            detached(
                scope,
                facadeOperations = forged.commandLabels,
                passCommandStream = forged,
            ).attachNativeOperandKeys(scope.nativeOperandKeys)
        }
        assertFailsWith<IllegalArgumentException> {
            val scope = producerScopes.first().first
            val bridges = requireNotNull(scope.passCommandStream).operandBridge.mapIndexed { index, bridge ->
                if (index != 0) return@mapIndexed bridge
                val operand = bridge.operand
                bridge.copy(
                    operand = GPUMaterializedCommandOperandReference(
                        label = "${operand.label}.forged",
                        kind = operand.kind,
                        descriptorHash = "${operand.descriptorHash}.forged",
                        deviceGeneration = operand.deviceGeneration,
                        ownerScope = operand.ownerScope,
                        usageLabels = operand.usageLabels,
                        invalidationPolicy = operand.invalidationPolicy,
                        evidenceFacts = operand.evidenceFacts,
                    ),
                )
            }
            val stream = forgedStream(scope, operandBridge = bridges)
            detached(
                scope,
                facadeOperations = stream.commandLabels,
                passCommandStream = stream,
            ).attachNativeOperandKeys(scope.nativeOperandKeys)
        }
        assertFailsWith<IllegalArgumentException> {
            val scope = consumerScopes.first().first
            val stream = requireNotNull(scope.passCommandStream)
            val commands = stream.commands.toMutableList().apply {
                add(
                    lastIndex,
                    GPUPassCommand.Draw(
                        vertexSourceLabel = "coverage-mask.forged.extra-draw",
                        packetId = scope.sourcePacketIds.single(),
                    ),
                )
            }
            val forged = forgedStream(scope, commands = commands)
            detached(
                scope,
                facadeOperations = forged.commandLabels,
                passCommandStream = forged,
            ).attachNativeOperandKeys(scope.nativeOperandKeys)
        }
    }

    @Test
    fun `native coverage mask atomic interval refuses foreign render compute and copy before side effects`() {
        val valid = preparedCoverageMaskFramePlan()
        val renders = valid.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        val firstProducer = renders.first {
            it.drawPackets.single().role == GPUDrawPacketRole.ClipProducer
        }
        val consumer = renders.first {
            it.drawPackets.single().role == GPUDrawPacketRole.Shading
        }
        val insertIndex = valid.steps.indexOf(firstProducer) + 1
        val foreignPacket = packet("packet.coverage-mask.foreign", 909)
        val foreignRender = GPUFrameStep.RenderPassStep(
            target = consumer.target,
            loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
            samplePlan = GPUSamplePlan.SingleSampleFrame,
            resourceUses = emptyList(),
            drawPackets = listOf(foreignPacket),
            sourceTaskIds = listOf(GPUTaskID("task.coverage-mask.foreign-render")),
            batches = listOf(batch(
                "batch.coverage-mask.foreign-render",
                foreignPacket,
                "task.coverage-mask.foreign-render",
            )),
        )
        val foreignCompute = GPUFrameStep.ComputePassStep(
            target = consumer.target,
            resourceUses = emptyList(),
            dispatches = listOf(
                GPUComputeDispatch(
                    GPUComputePipelineKey("compute.coverage-mask.foreign"),
                    1,
                    1,
                    1,
                ),
            ),
            sourceTaskIds = listOf(GPUTaskID("task.coverage-mask.foreign-compute")),
        )
        val maskResource = requireNotNull(
            firstProducer.drawPackets.single().corePrimitivePreparedAuthority
                ?.coverageMaskUniformSlabSeal,
        ).maskResource
        val foreignCopy = GPUFrameStep.CopyResourceStep(
            source = maskResource,
            destination = consumer.target,
            regions = listOf(GPUResourceCopyRegion(0L, 0L, null, 4L)),
            sourceTaskIds = listOf(GPUTaskID("task.coverage-mask.foreign-copy")),
        )
        val beforeProducerIndex = valid.steps.indexOf(firstProducer)
        val beforeLastConsumer = listOf(
            "readback" to GPUFrameStep.ReadbackCopyStep(
                source = consumer.target,
                staging = GPUFrameBufferRef("buffer.coverage-mask.early-readback"),
                request = GPUFrameReadbackRequest(
                    GPUReadbackRequestID("readback.coverage-mask.early"),
                    GPUPixelBounds(0, 0, 4, 4),
                    GPUReadbackPixelFormat.Rgba8Unorm,
                    GPUColorInterpretation("srgb-premul"),
                ),
                sourceTaskIds = listOf(GPUTaskID("task.coverage-mask.early-readback")),
            ),
            "surface-blit" to surfaceBlit(),
            "present" to present(),
        )
        val intervalScenarios = listOf(
            "render" to foreignRender,
            "compute" to foreignCompute,
            "copy" to foreignCopy,
        ).map { (label, step) -> Triple(label, insertIndex, step) } +
            beforeLastConsumer.map { (label, step) -> Triple(label, beforeProducerIndex, step) }
        intervalScenarios.forEach { (label, index, foreignStep) ->
            val plan = valid.withSteps(
                valid.steps.toMutableList().apply { add(index, foreignStep) },
            )
            val events = mutableListOf<String>()
            val result = preflighter(
                resources = RecordingResourceProvider(events),
                completion = RecordingCompletionProvider(events),
                surface = RecordingSurfaceProvider(events),
                context = clipPreflightContext(plan),
                capabilities = pathCapabilities(),
            ).preflight(plan)

            assertEquals(
                "invalid.preflight.core_primitive_coverage_mask_prepared_route",
                assertIs<GPUFramePreflightResult.Refused>(result, label).diagnostic.code.value,
                label,
            )
            assertTrue(events.isEmpty(), "$label side effects: $events")
        }
    }

    @Test
    fun `native coverage mask corruption matrix refuses before provider side effects`() {
        val valid = preparedCoverageMaskFramePlan()
        val renders = valid.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        val producers = renders.filter {
            it.drawPackets.single().role == GPUDrawPacketRole.ClipProducer
        }
        val consumers = renders.filter {
            it.drawPackets.single().role == GPUDrawPacketRole.Shading
        }
        val producerPacket = producers.first().drawPackets.single()
        val consumerPacket = consumers.first().drawPackets.single()
        val consumerSemantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(
            consumerPacket.semanticPayload,
        )
        assertEquals(null, producerPacket.uniformSlot)
        assertEquals(null, producerPacket.resourceSlot)
        assertEquals(consumerSemantic.payloadRef.uniformSlot, consumerPacket.uniformSlot)
        assertEquals(null, consumerPacket.resourceSlot)
        val clipPlan = requireNotNull(consumerPacket.clipExecutionPlan) as
            GPUClipExecutionPlan.CoverageMask
        val seal = requireNotNull(
            consumerPacket.corePrimitivePreparedAuthority?.coverageMaskUniformSlabSeal,
        )
        val forgedBytes = seal.packedBytesSnapshot().apply {
            val offset = seal.plan.slots[seal.producerSlots.size].alignedOffset.toInt() + 16
            this[offset] = (this[offset].toInt() xor 1).toByte()
        }
        val forgedByteSeal = GPUCorePrimitiveCoverageMaskUniformSlabSeal(
            seal.plan,
            seal.contentKey,
            seal.planCanonicalIdentity,
            seal.maskResource,
            seal.producerSlots,
            seal.consumerSlots,
            forgedBytes,
        )
        val forgedSemanticAuthoritySeal = GPUCorePrimitiveCoverageMaskUniformSlabSeal(
            seal.plan,
            seal.contentKey,
            seal.planCanonicalIdentity,
            seal.maskResource,
            seal.producerSlots,
            seal.consumerSlots.mapIndexed { index, slot ->
                if (index == 0) {
                    slot.copy(semanticAuthority = seal.consumerSlots[1].semanticAuthority)
                } else {
                    slot
                }
            },
            seal.packedBytesSnapshot(),
        )
        val forgedSemantic = coreSemantic(
            clipExecutionPlan = clipPlan,
            commandIdValue = consumerPacket.commandIdValue,
            geometry = GPUCorePrimitiveGeometryInput.Rect(0.5f, 0.5f, 2.5f, 2.5f),
        )
        val sameContentSemantic = consumerSemantic.withTargetBounds(consumerSemantic.targetBounds)
        val baseContext = clipPreflightContext(valid)
        val missingMaskGenerationContext = GPUFramePreflightContext(
            targetId = baseContext.targetId,
            deviceGeneration = baseContext.deviceGeneration,
            targetGeneration = baseContext.targetGeneration,
            resourceGenerations = baseContext.resourceGenerations.filterKeys {
                it != seal.maskResource
            },
        )
        val consumerTasks = consumers.map { it.sourceTaskIds.single() }
        val forgedConsumerTokenDependencies = valid.dependencies.map { dependency ->
            if (dependency.fromTaskId == consumerTasks[0] &&
                dependency.toTaskId == consumerTasks[1]
            ) {
                dependency.copy(useToken = GPUTaskUseToken("coverage-mask.consumer.forged"))
            } else {
                dependency
            }
        }
        data class Scenario(
            val label: String,
            val plan: GPUFramePlan,
            val context: GPUFramePreflightContext = baseContext,
        )
        val scenarios = listOf(
            Scenario(
                "producer-uniform-slot",
                valid.replacingCorePacket(
                    producerPacket,
                    cloneCorePacket(
                        producerPacket,
                        uniformSlot = requireNotNull(consumerPacket.uniformSlot),
                    ),
                ),
            ),
            Scenario(
                "producer-resource-slot",
                valid.replacingCorePacket(
                    producerPacket,
                    cloneCorePacket(
                        producerPacket,
                        resourceSlot = GPUResourceBindingSlot(
                            GPUPayloadSlotID("coverage-mask.forged.producer-resource"),
                            requireNotNull(consumerPacket.uniformSlot).fingerprint,
                            0,
                        ),
                    ),
                ),
            ),
            Scenario(
                "consumer-uniform-slot",
                valid.replacingCorePacket(
                    consumerPacket,
                    cloneCorePacket(
                        consumerPacket,
                        uniformSlot = requireNotNull(consumerPacket.uniformSlot).copy(
                            byteOffset = consumerPacket.uniformSlot.byteOffset + 4L,
                        ),
                    ),
                ),
            ),
            Scenario(
                "consumer-resource-slot",
                valid.replacingCorePacket(
                    consumerPacket,
                    cloneCorePacket(
                        consumerPacket,
                        resourceSlot = GPUResourceBindingSlot(
                            GPUPayloadSlotID("coverage-mask.forged.consumer-resource"),
                            requireNotNull(consumerPacket.uniformSlot).fingerprint,
                            0,
                        ),
                    ),
                ),
            ),
            Scenario(
                "pipeline-key",
                valid.replacingCorePacket(
                    consumerPacket,
                    cloneCorePacket(
                        consumerPacket,
                        renderPipelineKey = GPURenderPipelineKey("pipeline.coverage-mask.forged"),
                    ),
                ),
            ),
            Scenario(
                "binding-layout",
                valid.replacingCorePacket(
                    consumerPacket,
                    cloneCorePacket(
                        consumerPacket,
                        bindingLayoutHash = "layout.coverage-mask.forged",
                    ),
                ),
            ),
            Scenario(
                "semantic",
                valid.replacingCorePacket(
                    consumerPacket,
                    cloneCorePacket(consumerPacket, semanticPayload = forgedSemantic),
                ),
            ),
            Scenario(
                "same-content-semantic-instance",
                valid.replacingCorePacket(
                    consumerPacket,
                    cloneCorePacket(consumerPacket, semanticPayload = sameContentSemantic),
                ),
            ),
            Scenario("uniform-bytes", valid.withCoverageMaskSeal(forgedByteSeal)),
            Scenario(
                "semantic-authority-token",
                valid.withCoverageMaskSeal(forgedSemanticAuthoritySeal),
            ),
            Scenario(
                "plan-with-seal",
                valid.replacingCorePacket(
                    consumerPacket,
                    cloneCorePacket(
                        consumerPacket,
                        clipExecutionPlan = GPUClipExecutionPlan.NoClip,
                    ),
                ),
            ),
            Scenario(
                "resource-use",
                valid.replacingRender(
                    consumers.first(),
                    renderWith(
                        consumers.first(),
                        resourceUses = consumers.first().resourceUses.map { use ->
                            if (use.role == GPUFrameResourceRole.ClipMask) {
                                use.copy(usage = GPUFrameResourceUsage.Storage)
                            } else {
                                use
                            }
                        },
                    ),
                ),
            ),
            Scenario(
                "mask-descriptor",
                valid.mapPreparations { request ->
                    if (request.role != GPUFrameResourceRole.ClipMask) return@mapPreparations request
                    val descriptor = request.descriptor as GPUFrameTextureDescriptor
                    request.copyForTest(
                        descriptor = GPUFrameTextureDescriptor(
                            descriptor.logicalBounds,
                            org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat("bgra8unorm"),
                            descriptor.sampleCount,
                        ),
                    )
                },
            ),
            Scenario(
                "producer-load-store",
                valid.replacingRender(
                    producers.first(),
                    renderWith(
                        producers.first(),
                        loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
                    ),
                ),
            ),
            Scenario(
                "producer-sample-plan",
                valid.replacingRender(
                    producers.first(),
                    renderWith(
                        producers.first(),
                        samplePlan = GPUSamplePlan.MultisampleFrame(4),
                    ),
                ),
            ),
            Scenario(
                "dependency",
                valid.withDependencies(valid.dependencies.drop(1)),
            ),
            Scenario(
                "consumer-use-token",
                valid.withDependencies(forgedConsumerTokenDependencies),
            ),
            Scenario("generation", valid, missingMaskGenerationContext),
        )

        scenarios.forEach { scenario ->
            val expectedCode = if (scenario.label in setOf(
                    "consumer-uniform-slot",
                    "plan-with-seal",
                )
            ) {
                "invalid.preflight.core_primitive_semantic_integrity"
            } else {
                "invalid.preflight.core_primitive_coverage_mask_prepared_route"
            }
            val events = mutableListOf<String>()
            val result = preflighter(
                resources = RecordingResourceProvider(events),
                completion = RecordingCompletionProvider(events),
                surface = RecordingSurfaceProvider(events),
                context = scenario.context,
                capabilities = pathCapabilities(),
            ).preflight(scenario.plan)

            assertEquals(
                expectedCode,
                assertIs<GPUFramePreflightResult.Refused>(
                    result,
                    scenario.label,
                ).diagnostic.code.value,
                scenario.label,
            )
            assertTrue(events.isEmpty(), "${scenario.label} side effects: $events")
        }
    }

    @Test
    fun `clip stencil prepared scope rejects substituted native operand seals`() {
        val plan = preparedNativeClipStencilFramePlan()
        val prepared = assertIs<GPUFramePreflightResult.Prepared>(
            preflighter(
                RecordingResourceProvider(mutableListOf()),
                RecordingCompletionProvider(mutableListOf()),
                RecordingSurfaceProvider(mutableListOf()),
                context = clipPreflightContext(plan),
                capabilities = pathCapabilities(),
            ).preflight(plan),
        ).frame
        val producer = prepared.encoderPlan.scopes.single { scope ->
            scope.corePrimitiveClipStencilPreparedRouteSeal is
                GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Producer
        }
        val consumer = prepared.encoderPlan.scopes.first { scope ->
            scope.corePrimitiveClipStencilPreparedRouteSeal is
                GPUCorePrimitiveClipStencilPreparedScopeRouteSeal.Consumer
        }

        fun detached(scope: GPUCommandEncoderScopePlan) = GPUCommandEncoderScopePlan(
            sourceStepIndex = scope.sourceStepIndex,
            operationKind = scope.operationKind,
            scopeLabel = scope.scopeLabel,
            sourceTaskIds = scope.sourceTaskIds,
            sourcePacketIds = scope.sourcePacketIds,
            facadeOperationClasses = scope.facadeOperationClasses,
            targetGeneration = scope.targetGeneration,
            resourceGenerationLabels = scope.resourceGenerationLabels,
            passCommandStream = scope.passCommandStream,
            corePrimitiveDirectNativeRouteSeal = scope.corePrimitiveDirectNativeRouteSeal,
            corePrimitivePathStencilNativeRouteSeal = scope.corePrimitivePathStencilNativeRouteSeal,
            corePrimitiveNativeScopeRouteSeal = scope.corePrimitiveNativeScopeRouteSeal,
            corePrimitiveClipStencilPreparedRouteSeal =
                scope.corePrimitiveClipStencilPreparedRouteSeal,
        )

        assertFailsWith<IllegalArgumentException> {
            detached(producer).attachNativeOperandKeys(
                producer.nativeOperandKeys + GPUPreparedNativeOperandKey(
                    GPUPreparedNativeOperandRole.RenderBindGroup,
                    GPUPreparedNativeOperandKind.BindGroup,
                    gpuPreparedNativeBindingKey("clip-stencil.forged.producer-bind-group"),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            detached(consumer).attachNativeOperandKeys(
                consumer.nativeOperandKeys.filterNot {
                    it.role == GPUPreparedNativeOperandRole.RenderBindGroup
                },
            )
        }
        assertFailsWith<IllegalArgumentException> {
            detached(consumer).attachNativeOperandKeys(
                consumer.nativeOperandKeys.map { key ->
                    if (key.role == GPUPreparedNativeOperandRole.RenderDepthStencilTarget) {
                        key.copy(bindingKey = gpuPreparedNativeBindingKey(
                            "GPUFrameTextureRef:texture.path-depth-stencil.forged@1",
                        ))
                    } else {
                        key
                    }
                },
            )
        }
    }

    @Test
    fun `native path clip stencil preflight refuses fake boundary without exact native operands`() {
        val plan = preparedNativeClipStencilFramePlan()
        val events = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val resources = GPUConcreteResourceProvider(leaseFactory = adapter)
        try {
            val result = preflighter(
                resources,
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
                context = clipPreflightContext(plan),
                capabilities = pathCapabilities(),
                nativeBoundary = adapter.bindNativeFrameBoundary(
                    resources,
                    RenderOnlyNativePayloadMaterializer(events),
                ),
            ).preflight(plan)
            val refused = assertIs<GPUFramePreflightResult.Refused>(
                result,
            )
            assertEquals(
                "failed.preflight.native_payload_materialization",
                refused.diagnostic.code.value,
            )
            assertTrue(events.contains("native-payload:materialize"))
            assertEquals(0, adapter.activePreparedNativeFramePayloadCount)
            assertEquals(0, resources.pendingPhysicalReservationCount)
        } finally {
            adapter.close()
        }
    }

    @Test
    fun `native path clip stencil corruption matrix refuses before provider or native side effects`() {
        val valid = preparedNativeClipStencilFramePlan()
        val renders = valid.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        val producer = renders.single {
            it.drawPackets.single().role == GPUDrawPacketRole.StencilProducer
        }
        val consumers = renders.filter {
            it.drawPackets.single().role == GPUDrawPacketRole.Shading
        }
        val producerPacket = producer.drawPackets.single()
        val consumerPackets = consumers.map { it.drawPackets.single() }
        val candidate = requireNotNull(producerPacket.corePrimitiveClipStencilPreparedCandidate)
        val vertexUse = producer.resourceUses.single { it.role == GPUFrameResourceRole.VertexData }
        val indexUse = producer.resourceUses.single { it.role == GPUFrameResourceRole.IndexData }

        fun replaceCandidate(
            forged: GPUCorePrimitiveClipStencilPreparedCandidate,
        ) = valid.withClipStencilCandidate(forged)

        val corruptedFan = candidate.producerFanIndices.toMutableList().apply {
            this[0] = (this[0] + 1) % (candidate.producerFanVertices.size / 2)
        }
        val swappedConsumers = listOf(
            candidate.consumers[0].copy(packetId = candidate.consumers[1].packetId),
            candidate.consumers[1].copy(packetId = candidate.consumers[0].packetId),
        )
        val missingCandidate = valid.replacingCorePacket(
            producerPacket,
            cloneCorePacket(producerPacket, clipStencilCandidate = null),
        )
        val forgedProducerKey = valid.replacingCorePacket(
            producerPacket,
            cloneCorePacket(
                producerPacket,
                renderPipelineKey = GPURenderPipelineKey("pipeline.clip-stencil.forged"),
            ),
        )
        val forgedProducerUse = valid.replacingRender(
            producer,
            renderWith(
                producer,
                resourceUses = producer.resourceUses.map { use ->
                    if (use === vertexUse) use.copy(usage = GPUFrameResourceUsage.Storage) else use
                },
            ),
        )
        val forgedProducerDepthStencilRole = valid.replacingRender(
            producer,
            renderWith(
                producer,
                resourceUses = producer.resourceUses.map { use ->
                    if (use.role == GPUFrameResourceRole.ClipDepthStencil) {
                        use.copy(role = GPUFrameResourceRole.PathDepthStencil)
                    } else {
                        use
                    }
                },
            ),
        )
        val forgedProducerDepthStencilLoadStore = valid.replacingRender(
            producer,
            renderWith(
                producer,
                depthStencilLoadStore = GPUDepthStencilLoadStorePlan.WritableStencil(
                    GPUStencilLoadOperation.Load,
                    GPUStorePlan.Store,
                    null,
                ),
            ),
        )
        val forgedConsumerDepthStencilLoadStore = valid.replacingRender(
            consumers.first(),
            renderWith(
                consumers.first(),
                depthStencilLoadStore = GPUDepthStencilLoadStorePlan.WritableStencil(
                    GPUStencilLoadOperation.Clear,
                    GPUStorePlan.Store,
                    0u,
                ),
            ),
        )
        val missingUniformUse = valid.replacingRender(
            consumers.first(),
            renderWith(
                consumers.first(),
                resourceUses = consumers.first().resourceUses.filterNot {
                    it.role == GPUFrameResourceRole.UniformData
                },
            ),
        )
        val forgedVertexPreparation = valid.mapPreparations { request ->
            if (request.role != GPUFrameResourceRole.VertexData) return@mapPreparations request
            val bytes = request.byteSize + 4L
            request.copyForTest(
                descriptor = GPUFrameBufferDescriptor(bytes, 8L),
                byteSize = bytes,
            )
        }
        val forgedIndexPreparation = valid.mapPreparations { request ->
            if (request.role != GPUFrameResourceRole.IndexData) return@mapPreparations request
            request.copyForTest(usages = setOf(GPUFrameResourceUsage.Index))
        }
        val forgedDepthStencil = valid.mapPreparations { request ->
            if (request.role != GPUFrameResourceRole.ClipDepthStencil) {
                return@mapPreparations request
            }
            val descriptor = request.descriptor as GPUFrameTextureDescriptor
            request.copyForTest(
                descriptor = GPUFrameTextureDescriptor(
                    descriptor.logicalBounds,
                    GPUColorFormat("depth32float"),
                    descriptor.sampleCount,
                ),
            )
        }
        val uniformSeal = requireNotNull(
            consumerPackets.first().corePrimitivePreparedAuthority?.uniformSlabSeal,
        )
        val corruptedUniformBytes = uniformSeal.packedBytesSnapshot().apply {
            this[0] = (this[0].toInt() xor 1).toByte()
        }
        val forgedUniformSeal = GPUCorePrimitiveUniformSlabSeal(
            uniformSeal.plan,
            uniformSeal.commandIds,
            corruptedUniformBytes,
        )
        val forgedUniform = consumerPackets.fold(valid) { plan, packet ->
            plan.replacingCorePacket(
                packet,
                cloneCorePacket(packet, uniformSlabSeal = forgedUniformSeal),
            )
        }
        val consumerTasks = consumers.map { it.sourceTaskIds.single() }
        val missingConsumerDependency = valid.withDependencies(
            valid.dependencies.filterNot { dependency ->
                dependency.fromTaskId == consumerTasks[0] &&
                    dependency.toTaskId == consumerTasks[1]
            },
        )
        val forgedLoadStore = valid.replacingRender(
            consumers[1],
            GPUFrameStep.RenderPassStep(
                consumers[1].target,
                GPULoadStorePlan("clear", GPUStorePlan.Store),
                consumers[1].samplePlan,
                consumers[1].resourceUses,
                consumers[1].drawPackets,
                consumers[1].sourceTaskIds,
                consumers[1].batches,
                consumers[1].sampleContinuation,
                consumers[1].depthStencilLoadStore,
            ),
        )
        val foreignPreparation = valid.withSteps(valid.steps.map { step ->
            if (step !is GPUFrameStep.PrepareResourcesStep) return@map step
            GPUFrameStep.PrepareResourcesStep(
                step.requests + GPUResourcePreparationRequest(
                    GPUFrameTextureRef("texture.clip-stencil.foreign-mask"),
                    GPUFrameTextureDescriptor(
                        GPUPixelBounds(0, 0, 4, 4),
                        GPUColorFormat("rgba8unorm"),
                        1,
                    ),
                    GPUFrameResourceRole.ClipMask,
                    setOf(
                        GPUFrameResourceUsage.RenderAttachment,
                        GPUFrameResourceUsage.TextureBinding,
                    ),
                    GPUFrameResourceLifetime.FrameLocal,
                    64L,
                    "foreign.clip-mask",
                ),
                step.sourceTaskIds,
            )
        })
        val foreignCopy = GPUFrameStep.CopyResourceStep(
            source = vertexUse.resource,
            destination = producer.target,
            regions = listOf(GPUResourceCopyRegion(0, 0, null, 4)),
            sourceTaskIds = listOf(GPUTaskID("task.clip-stencil.foreign-copy")),
        )
        val producerIndex = valid.steps.indexOf(producer)
        val foreignCopyInInterval = valid.withSteps(
            valid.steps.toMutableList().apply { add(producerIndex + 1, foreignCopy) },
        )
        val forgedBlend = valid.replacingCorePacket(
            consumerPackets.first(),
            cloneCorePacket(
                consumerPackets.first(),
                blendPlan = coreBlend(GPUBlendMode.SRC),
            ),
        )
        val corruptedNdc = candidate.producerNdcVertices.toMutableList().apply {
            this[0] += 0.125f
        }
        val distinctCandidateInstance = candidate.copyForTest()
        val splitCandidateInstances = valid.replacingCorePacket(
            consumerPackets.first(),
            cloneCorePacket(
                consumerPackets.first(),
                clipStencilCandidate = distinctCandidateInstance,
            ),
        )
        val forgedConsumerKey = replaceCandidate(
            candidate.copyForTest(
                consumers = candidate.consumers.mapIndexed { index, consumer ->
                    if (index == 0) {
                        consumer.copy(structuralKey = candidate.producerStructuralKey)
                    } else {
                        consumer
                    }
                },
            ),
        )
        val forgedConsumerCommand = replaceCandidate(
            candidate.copyForTest(
                consumers = candidate.consumers.mapIndexed { index, consumer ->
                    if (index == 0) consumer.copy(commandId = consumer.commandId + 1000) else consumer
                },
            ),
        )
        val forgedDepthStencilSample = valid.mapPreparations { request ->
            if (request.role != GPUFrameResourceRole.ClipDepthStencil) {
                return@mapPreparations request
            }
            val descriptor = request.descriptor as GPUFrameTextureDescriptor
            request.copyForTest(
                descriptor = GPUFrameTextureDescriptor(
                    descriptor.logicalBounds,
                    descriptor.format,
                    4,
                ),
            )
        }
        val forgedDepthStencilLifetime = valid.mapPreparations { request ->
            if (request.role == GPUFrameResourceRole.ClipDepthStencil) {
                request.copyForTest(lifetime = GPUFrameResourceLifetime.RecordingLocal)
            } else {
                request
            }
        }
        val consumerOrderEdge = valid.dependencies.single { dependency ->
            dependency.fromTaskId == consumerTasks[0] &&
                dependency.toTaskId == consumerTasks[1]
        }
        val forgedDependencyToken = valid.withDependencies(
            valid.dependencies.map { dependency ->
                if (dependency === consumerOrderEdge) {
                    dependency.copy(useToken = GPUTaskUseToken("prepared-core-primitive.forged"))
                } else {
                    dependency
                }
            },
        )
        val reversedDependency = valid.withDependencies(
            valid.dependencies.filterNot { it === consumerOrderEdge } + consumerOrderEdge.copy(
                fromTaskId = consumerOrderEdge.toTaskId,
                toTaskId = consumerOrderEdge.fromTaskId,
            ),
        )
        val forgedUniformPlan = GPUUniformSlabPlan(
            planHash = "forged-uniform-plan",
            sourceLabel = uniformSeal.plan.sourceLabel,
            deviceGeneration = uniformSeal.plan.deviceGeneration,
            alignmentBytes = uniformSeal.plan.alignmentBytes,
            totalBytes = uniformSeal.plan.totalBytes,
            uploadBudgetBytes = uniformSeal.plan.uploadBudgetBytes,
            slots = uniformSeal.plan.slots.mapIndexed { index, slot ->
                if (index == 0) {
                    GPUUniformSlabSlot(
                        "draw-forged",
                        "payload-forged",
                        slot.payloadBytes,
                        slot.alignedOffset,
                        slot.allocatedBytes,
                    )
                } else {
                    slot
                }
            },
        )
        val forgedUniformPlanSeal = GPUCorePrimitiveUniformSlabSeal(
            forgedUniformPlan,
            uniformSeal.commandIds,
            uniformSeal.packedBytesSnapshot(),
        )
        val forgedUniformPlanFrame = consumerPackets.fold(valid) { plan, packet ->
            plan.replacingCorePacket(
                packet,
                cloneCorePacket(packet, uniformSlabSeal = forgedUniformPlanSeal),
            )
        }
        val nonZeroOriginTarget = valid.mapPreparations { request ->
            if (request.role != GPUFrameResourceRole.SceneTarget) return@mapPreparations request
            val descriptor = request.descriptor as GPUFrameTextureDescriptor
            request.copyForTest(
                descriptor = GPUFrameTextureDescriptor(
                    GPUPixelBounds(1, 1, 5, 5),
                    descriptor.format,
                    descriptor.sampleCount,
                ),
            )
        }
        fun insertAfterProducer(step: GPUFrameStep): GPUFramePlan = valid.withSteps(
            valid.steps.toMutableList().apply { add(producerIndex + 1, step) },
        )
        fun insertBeforeProducer(step: GPUFrameStep): GPUFramePlan = valid.withSteps(
            valid.steps.toMutableList().apply { add(producerIndex, step) },
        )
        val surfaceBlitBeforeLast = insertBeforeProducer(surfaceBlit())
        val presentBeforeLast = insertBeforeProducer(present())
        val readbackBeforeLast = insertBeforeProducer(
            GPUFrameStep.ReadbackCopyStep(
                source = producer.target,
                staging = GPUFrameBufferRef("buffer.clip-stencil.early-readback"),
                request = GPUFrameReadbackRequest(
                    GPUReadbackRequestID("readback.clip-stencil.early"),
                    GPUPixelBounds(0, 0, 4, 4),
                    GPUReadbackPixelFormat.Rgba8Unorm,
                    GPUColorInterpretation("srgb-premul"),
                ),
                sourceTaskIds = listOf(GPUTaskID("task.clip-stencil.early-readback")),
            ),
        )
        val foreignCorePacket = cloneCorePacket(
            consumerPackets.first(),
            packetId = GPUDrawPacketID("packet.clip-stencil.foreign-core"),
            clipStencilCandidate = null,
        )
        val foreignCoreRender = GPUFrameStep.RenderPassStep(
            producer.target,
            GPULoadStorePlan("load", GPUStorePlan.Store),
            GPUSamplePlan.SingleSampleFrame,
            consumers.first().resourceUses,
            listOf(foreignCorePacket),
            listOf(GPUTaskID("task.clip-stencil.foreign-core")),
            listOf(batch(
                "batch.clip-stencil.foreign-core",
                foreignCorePacket,
                "task.clip-stencil.foreign-core",
            )),
            depthStencilLoadStore = GPUDepthStencilLoadStorePlan.ReadOnlyKeep,
        )
        val foreignCoreInInterval = insertAfterProducer(foreignCoreRender)

        val scenarios = listOf(
            "candidate fan" to replaceCandidate(
                candidate.copyForTest(producerFanIndices = corruptedFan),
            ),
            "candidate identity" to replaceCandidate(
                candidate.copyForTest(contentKey = "clip.preflight.forged"),
            ),
            "consumer packet order" to replaceCandidate(
                candidate.copyForTest(consumers = swappedConsumers),
            ),
            "attachment dimensions" to replaceCandidate(
                candidate.copyForTest(attachmentWidth = candidate.attachmentWidth + 1),
            ),
            "missing producer candidate" to missingCandidate,
            "producer key" to forgedProducerKey,
            "producer vertex use" to forgedProducerUse,
            "producer depth stencil role" to forgedProducerDepthStencilRole,
            "producer depth stencil load store" to forgedProducerDepthStencilLoadStore,
            "consumer depth stencil load store" to forgedConsumerDepthStencilLoadStore,
            "consumer uniform use" to missingUniformUse,
            "vertex preparation" to forgedVertexPreparation,
            "index preparation" to forgedIndexPreparation,
            "depth stencil preparation" to forgedDepthStencil,
            "uniform payload" to forgedUniform,
            "consumer dependency" to missingConsumerDependency,
            "consumer color load" to forgedLoadStore,
            "foreign clip preparation" to foreignPreparation,
            "foreign copy interval" to foreignCopyInInterval,
            "blend" to forgedBlend,
            "candidate ndc" to replaceCandidate(
                candidate.copyForTest(producerNdcVertices = corruptedNdc),
            ),
            "candidate contours" to replaceCandidate(
                candidate.copyForTest(producerContourStarts = listOf(0, 3)),
            ),
            "distinct candidate instance" to splitCandidateInstances,
            "consumer structural key" to forgedConsumerKey,
            "consumer command" to forgedConsumerCommand,
            "depth stencil sample" to forgedDepthStencilSample,
            "depth stencil lifetime" to forgedDepthStencilLifetime,
            "dependency token" to forgedDependencyToken,
            "dependency reverse" to reversedDependency,
            "uniform plan" to forgedUniformPlanFrame,
            "target origin" to nonZeroOriginTarget,
            "surface blit before last" to surfaceBlitBeforeLast,
            "present before last" to presentBeforeLast,
            "readback before last" to readbackBeforeLast,
            "foreign core packet" to foreignCoreInInterval,
        )

        scenarios.forEach { (label, plan) ->
            val expectedCode = if (label == "blend") {
                "invalid.preflight.core_primitive_semantic_integrity"
            } else if (label in setOf(
                    "candidate identity",
                    "missing producer candidate",
                    "producer key",
                    "producer vertex use",
                    "producer depth stencil role",
                    "producer depth stencil load store",
                    "consumer depth stencil load store",
                    "depth stencil preparation",
                    "depth stencil sample",
                    "depth stencil lifetime",
                    "target origin",
                    "foreign core packet",
                )
            ) {
                "invalid.preflight.core_primitive_clip_producer_authority"
            } else {
                "invalid.preflight.core_primitive_clip_stencil_prepared_route"
            }
            val events = mutableListOf<String>()
            val resources = RecordingResourceProvider(events)
            val result = preflighter(
                resources,
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
                context = clipPreflightContext(plan),
                capabilities = pathCapabilities(),
            ).preflight(plan)

            assertEquals(
                expectedCode,
                assertIs<GPUFramePreflightResult.Refused>(result, label).diagnostic.code.value,
                label,
            )
            assertEquals(0, resources.beginFramePreparationCount, label)
            assertTrue(events.isEmpty(), "$label produced provider side effects: $events")

            val nativeEvents = mutableListOf<String>()
            val adapter = GPURuntimeResourceAdapter()
            val concreteResources = GPUConcreteResourceProvider(leaseFactory = adapter)
            try {
                val nativeResult = preflighter(
                    concreteResources,
                    RecordingCompletionProvider(nativeEvents),
                    RecordingSurfaceProvider(nativeEvents),
                    context = clipPreflightContext(plan),
                    capabilities = pathCapabilities(),
                    nativeBoundary = adapter.bindNativeFrameBoundary(
                        concreteResources,
                        RenderOnlyNativePayloadMaterializer(nativeEvents),
                    ),
                ).preflight(plan)
                assertEquals(
                    expectedCode,
                    assertIs<GPUFramePreflightResult.Refused>(nativeResult, label)
                        .diagnostic.code.value,
                    label,
                )
                assertTrue(
                    nativeEvents.none { it.startsWith("native-payload:") },
                    "$label reached native materialization: $nativeEvents",
                )
                assertEquals(0, adapter.activePreparedNativeFramePayloadCount, label)
                assertEquals(0, concreteResources.pendingPhysicalReservationCount, label)
            } finally {
                adapter.close()
            }
        }
    }

    @Test
    fun `native path clip stencil missing sealed resource generations refuse before side effects`() {
        val plan = preparedNativeClipStencilFramePlan()
        val preparations = plan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        val baseGenerations = preparations.associate { it.resource to 1L }
        val sealedRoles = listOf(
            GPUFrameResourceRole.VertexData,
            GPUFrameResourceRole.IndexData,
            GPUFrameResourceRole.UniformData,
            GPUFrameResourceRole.ClipDepthStencil,
        )

        sealedRoles.forEach { role ->
            val missingResource = preparations.single { it.role == role }.resource
            val context = GPUFramePreflightContext(
                targetId = "target.scene",
                deviceGeneration = plan.capabilitySeal.deviceGeneration,
                targetGeneration = 1L,
                resourceGenerations = baseGenerations - missingResource,
            )
            val label = "missing $role generation"
            val events = mutableListOf<String>()
            val resources = RecordingResourceProvider(events)
            val result = preflighter(
                resources,
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
                context = context,
                capabilities = pathCapabilities(),
            ).preflight(plan)

            assertEquals(
                "invalid.preflight.core_primitive_clip_stencil_prepared_route",
                assertIs<GPUFramePreflightResult.Refused>(result, label).diagnostic.code.value,
                label,
            )
            assertEquals(0, resources.beginFramePreparationCount, label)
            assertTrue(events.isEmpty(), "$label produced provider side effects: $events")

            val nativeEvents = mutableListOf<String>()
            val adapter = GPURuntimeResourceAdapter()
            val concreteResources = GPUConcreteResourceProvider(leaseFactory = adapter)
            try {
                val nativeResult = preflighter(
                    concreteResources,
                    RecordingCompletionProvider(nativeEvents),
                    RecordingSurfaceProvider(nativeEvents),
                    context = context,
                    capabilities = pathCapabilities(),
                    nativeBoundary = adapter.bindNativeFrameBoundary(
                        concreteResources,
                        RenderOnlyNativePayloadMaterializer(nativeEvents),
                    ),
                ).preflight(plan)
                assertEquals(
                    "invalid.preflight.core_primitive_clip_stencil_prepared_route",
                    assertIs<GPUFramePreflightResult.Refused>(nativeResult, label)
                        .diagnostic.code.value,
                    label,
                )
                assertTrue(
                    nativeEvents.none { it.startsWith("native-payload:") },
                    "$label reached native materialization: $nativeEvents",
                )
                assertEquals(0, adapter.activePreparedNativeFramePayloadCount, label)
                assertEquals(0, concreteResources.pendingPhysicalReservationCount, label)
            } finally {
                adapter.close()
            }
        }
    }

    @Test
    fun `core stencil ordering authority corruption refuses before side effects`() {
        val mismatchedAuthority = GPUClipStencilProducerPlan(
            geometry = GPUClipExecutionGeometry.Rect(GPUBounds(0f, 0f, 2f, 2f)),
            scissor = GPUPixelBounds(0, 0, 2, 2),
            fillRule = GPUClipFillRule.EvenOdd,
            reference = 3u,
            compare = GPUClipStencilCompare.Always,
            frontPassOperation = GPUClipStencilOperation.Invert,
            backPassOperation = GPUClipStencilOperation.Invert,
            loadOperation = GPUClipStencilLoadOperation.Clear,
            storeOperation = GPUClipStencilStoreOperation.Store,
            clearValue = 0u,
        )
        val scenarios = listOf(
            coreStencilFramePlan(includeDependency = false),
            coreStencilFramePlan(invertDependency = true),
            coreStencilFramePlan(dependencyToken = "token.substituted"),
            coreStencilFramePlan(consumerBeforeProducer = true),
            coreStencilFramePlan(consumerDepthStencilLifetime = GPUFrameResourceLifetime.RecordingLocal),
            coreStencilFramePlan(
                producerLoadOperation = GPUClipStencilLoadOperation.Load,
                producerClearValue = null,
            ),
            coreStencilFramePlan(producerStoreOperation = GPUClipStencilStoreOperation.Discard),
            coreStencilFramePlan(producerClearValue = 1u),
            coreStencilFramePlan(consumerPassOperation = GPUClipStencilOperation.Replace),
            coreStencilFramePlan(consumerStoreOperation = GPUClipStencilStoreOperation.Discard),
            coreStencilFramePlan(consumerHasClipMaskUse = true),
            coreStencilFramePlan(duplicateContradictoryEdge = true),
            coreStencilFramePlan(foreignDepthStencilWrite = true),
        )

        assertFailsWith<IllegalArgumentException> {
            coreStencilFramePlan(producerAuthorityOverride = mismatchedAuthority)
        }

        scenarios.forEach { plan ->
            val events = mutableListOf<String>()
            val result = preflighter(
                resources = RecordingResourceProvider(events),
                completion = RecordingCompletionProvider(events),
                surface = RecordingSurfaceProvider(events),
                context = clipPreflightContext(plan),
            ).preflight(plan)

            assertEquals(
                "invalid.preflight.core_primitive_clip_producer_authority",
                assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
            )
            assertTrue(events.isEmpty(), "pure validation side effects: $events")
        }
    }

    @Test
    fun `sealed core mask producer chain and consumer preflight with exact generations`() {
        val plan = coreMaskFramePlan()
        val events = mutableListOf<String>()

        val result = preflighter(
            resources = RecordingResourceProvider(events),
            completion = RecordingCompletionProvider(events),
            surface = RecordingSurfaceProvider(events),
            context = clipPreflightContext(plan),
        ).preflight(plan)

        assertIs<GPUFramePreflightResult.Prepared>(result)
    }

    @Test
    fun `core mask seal corruption matrix refuses before side effects`() {
        val scenarios = listOf(
            coreMaskFramePlan(maskByteDelta = 4),
            coreMaskFramePlan(maskDescriptorBoundsMismatch = true),
            coreMaskFramePlan(maskFormat = "rgba16float"),
            coreMaskFramePlan(maskPreparationLifetime = GPUFrameResourceLifetime.RecordingLocal),
            coreMaskFramePlan(maskPreparationUsages = setOf(GPUFrameResourceUsage.RenderAttachment)),
            coreMaskFramePlan(consumerUsage = GPUFrameResourceUsage.RenderAttachment),
            coreMaskFramePlan(consumerWrite = true),
            coreMaskFramePlan(consumerLifetime = GPUFrameResourceLifetime.RecordingLocal),
            coreMaskFramePlan(firstProducerLoad = "load"),
            coreMaskFramePlan(firstProducerClearColorLabel = "transparent"),
            coreMaskFramePlan(producerStore = GPUStorePlan.Discard),
            coreMaskFramePlan(includeChainDependency = false),
            coreMaskFramePlan(invertChainDependency = true),
            coreMaskFramePlan(dependencyToken = "token.substituted"),
            coreMaskFramePlan(duplicateContradictoryEdge = true),
            coreMaskFramePlan(reverseProducerSteps = true),
            coreMaskFramePlan(secondProducerTargetMismatch = true),
            coreMaskFramePlan(includeProducers = false),
            coreMaskFramePlan(sampleCount = 4),
            coreMaskFramePlan(depthStencilRequired = true),
        )

        scenarios.forEach { plan ->
            val events = mutableListOf<String>()
            val result = preflighter(
                resources = RecordingResourceProvider(events),
                completion = RecordingCompletionProvider(events),
                surface = RecordingSurfaceProvider(events),
                context = clipPreflightContext(plan),
            ).preflight(plan)

            assertEquals(
                "invalid.preflight.core_primitive_clip_producer_authority",
                assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
            )
            assertTrue(events.isEmpty(), "pure validation side effects: $events")
        }
    }

    @Test
    fun `core packet without semantic payload refuses before every preflight side effect`() {
        val events = mutableListOf<String>()
        val result = preflighter(
            resources = RecordingResourceProvider(events),
            completion = RecordingCompletionProvider(events),
            surface = RecordingSurfaceProvider(events),
        ).preflight(framePlan(listOf(prepareScene(), coreRenderStep(null))))

        assertEquals(
            "invalid.preflight.core_primitive_semantic_payload_missing",
            assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
        )
        assertTrue(events.isEmpty(), "pure validation side effects: $events")
    }

    @Test
    fun `direct core primitive preflight emits borrowed pooled operand bridge`() {
        val events = mutableListOf<String>()
        val semantic = coreSemantic()
        val prepared = assertIs<GPUFramePreflightResult.Prepared>(
            preflighter(
                RecordingResourceProvider(events),
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
            ).preflight(framePlan(listOf(coreDirectPrepare(), coreRenderStep(semantic)))),
        ).frame

        val scope = prepared.encoderPlan.scopes.single()
        assertEquals(
            listOf(
                "beginRenderPass",
                "setRenderPipeline",
                "setBindGroup",
                "setVertexBuffer",
                "setIndexBuffer",
                "setScissor",
                "draw",
                "endRenderPass",
            ),
            scope.facadeOperationClasses,
        )
        assertEquals(
            listOf(
                GPUPreparedNativeOperandRole.RenderColorTarget to GPUPreparedNativeOperandOwnership.Borrowed,
                GPUPreparedNativeOperandRole.RenderPipeline to GPUPreparedNativeOperandOwnership.Borrowed,
                GPUPreparedNativeOperandRole.RenderVertexBuffer to GPUPreparedNativeOperandOwnership.Borrowed,
                GPUPreparedNativeOperandRole.RenderIndexBuffer to GPUPreparedNativeOperandOwnership.Borrowed,
                GPUPreparedNativeOperandRole.RenderBindGroup to GPUPreparedNativeOperandOwnership.Borrowed,
            ),
            scope.nativeOperandKeys.map { it.role to it.ownership },
        )
        val forgedDepthStencilKeyScope = GPUCommandEncoderScopePlan(
            scope.sourceStepIndex,
            scope.operationKind,
            scope.scopeLabel,
            scope.sourceTaskIds,
            scope.sourcePacketIds,
            scope.facadeOperationClasses,
            scope.targetGeneration,
            scope.resourceGenerationLabels,
            scope.passCommandStream,
            scope.corePrimitiveDirectNativeRouteSeal,
            scope.corePrimitivePathStencilNativeRouteSeal,
            scope.corePrimitiveNativeScopeRouteSeal,
        )
        assertFailsWith<IllegalArgumentException> {
            forgedDepthStencilKeyScope.attachNativeOperandKeys(
                scope.nativeOperandKeys + GPUPreparedNativeOperandKey(
                    GPUPreparedNativeOperandRole.RenderDepthStencilTarget,
                    GPUPreparedNativeOperandKind.TextureView,
                    gpuPreparedNativeBindingKey("texture.direct.forged-depth"),
                ),
            )
        }
    }

    @Test
    fun `two direct core primitives retain one encoder render scope and one shared uniform resource`() {
        val first = coreRenderStep(coreSemantic(commandIdValue = 41), packetId = "packet.core.41")
        val second = coreRenderStep(coreSemantic(commandIdValue = 42), packetId = "packet.core.42")
        val packets = first.drawPackets + second.drawPackets
        val render = GPUFrameStep.RenderPassStep(
            target = first.target,
            loadStore = first.loadStore,
            samplePlan = first.samplePlan,
            resourceUses = first.resourceUses,
            drawPackets = packets,
            sourceTaskIds = first.sourceTaskIds,
            batches = listOf(
                org.graphiks.kanvas.gpu.renderer.recording.GPUFrameRenderBatch(
                    "batch.core.direct.multi",
                    GPUPassBatchKind.SolidFill,
                    packets,
                    first.sourceTaskIds,
                ),
            ),
        )
        val events = mutableListOf<String>()

        val prepared = assertIs<GPUFramePreflightResult.Prepared>(
            preflighter(
                RecordingResourceProvider(events),
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
            ).preflight(
                framePlan(
                    listOf(
                        coreDirectPrepare(vertexBytes = 64L, indexBytes = 48L, uniformBytes = 512L),
                        render,
                    ),
                ),
            ),
        ).frame

        assertEquals(1, prepared.encoderPlan.scopes.size)
        assertEquals(1, render.resourceUses.count { it.role == GPUFrameResourceRole.UniformData })
        val scope = prepared.encoderPlan.scopes.single()
        assertEquals(2, scope.sourcePacketIds.size)
        assertEquals(1, scope.nativeOperandKeys.count {
            it.role == GPUPreparedNativeOperandRole.RenderPipeline
        })
    }

    @Test
    fun `analytic direct core preflight accepts one exact uniform64 slab without native side effects`() {
        val plan = preparedAnalyticFramePlan(
            mapOf(
                81 to GPUClipExecutionPlan.AnalyticCoverage(
                    GPUClipExecutionGeometry.Rect(GPUBounds(0.5f, 0.75f, 3.25f, 3.5f)),
                    scissor = null,
                    antiAlias = true,
                ),
                82 to GPUClipExecutionPlan.AnalyticCoverage(
                    GPUClipExecutionGeometry.Rect(GPUBounds(1.25f, 0.25f, 3.75f, 2.75f)),
                    scissor = null,
                    antiAlias = true,
                ),
            ),
        )
        val events = mutableListOf<String>()

        val result = preflighter(
            RecordingResourceProvider(events),
            RecordingCompletionProvider(events),
            RecordingSurfaceProvider(events),
            context = clipPreflightContext(plan),
            capabilities = pathCapabilities(),
        ).preflight(plan)

        val prepared = assertIs<GPUFramePreflightResult.Prepared>(
            result,
            (result as? GPUFramePreflightResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}"
            },
        ).frame
        val scope = prepared.encoderPlan.scopes.single()
        val routes = assertIs<GPUCorePrimitiveDirectNativeRouteSeal.Routes>(
            scope.corePrimitiveDirectNativeRouteSeal,
        )
        assertEquals(2, routes.routesByPacketId.size)
        val analyticSeals = requireNotNull(routes.preparedPassSeal).analyticClipUniformSeals
        assertEquals(2, analyticSeals.size)
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (analyticSeals as MutableList<Any>).clear()
        }
        assertEquals(2, analyticSeals.size)
        assertIs<GPUCorePrimitivePathStencilNativeRouteSeal.Empty>(
            scope.corePrimitivePathStencilNativeRouteSeal,
        )
        assertTrue(events.isNotEmpty(), "accepted preflight should materialize declared resources")
    }

    @Test
    fun `analytic uniform64 corruption matrix refuses before every preflight side effect`() {
        val validPlan = preparedAnalyticFramePlan(
            mapOf(
                83 to GPUClipExecutionPlan.AnalyticCoverage(
                    GPUClipExecutionGeometry.RRect(
                        GPUBounds(0.5f, 0.75f, 3.5f, 3.25f),
                        listOf(0.5f, 0.75f, 0.5f, 0.75f, 0.5f, 0.75f, 0.5f, 0.75f),
                    ),
                    scissor = null,
                    antiAlias = true,
                ),
            ),
        )
        val render = validPlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single()
        val packet = render.drawPackets.single()
        val validSeal = requireNotNull(packet.corePrimitivePreparedAuthority?.analyticClipUniformSeal)
        val forgedOffsetPlan = validSeal.plan.copyForTest(
            totalBytes = validSeal.plan.totalBytes + validSeal.plan.alignmentBytes,
            slots = listOf(
                validSeal.plan.slots.single().copy(
                    alignedOffset = validSeal.plan.alignmentBytes,
                ),
            ),
        )
        val forgedHashPlan = validSeal.plan.copyForTest(planHash = "forged-plan-hash")
        val forgedPayloadHashPlan = validSeal.plan.copyForTest(
            slots = listOf(validSeal.plan.slots.single().copy(payloadHash = "forged-payload-hash")),
        )
        val corruptedPayload = validSeal.payloadBytesSnapshot().also { bytes -> bytes[20] = (bytes[20] + 1).toByte() }
        val cases = listOf<Pair<String, GPUCorePrimitiveAnalyticClipUniformSeal>>(
            "command" to validSeal.copyForTest(commandId = validSeal.commandId + 1),
            "packet" to validSeal.copyForTest(packetId = GPUDrawPacketID("packet.core.analytic.forged")),
            "clip identity" to validSeal.copyForTest(clipCanonicalIdentity = "analytic-forged"),
            "type" to validSeal.copyForTest(
                clipType = GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry.Rect,
            ),
            "bounds" to validSeal.copyForTest(clipBounds = listOf(0.25f, 0.75f, 3.5f, 3.25f)),
            "radii" to validSeal.copyForTest(
                clipRadii = listOf(0.25f, 0.75f, 0.25f, 0.75f, 0.25f, 0.75f, 0.25f, 0.75f),
            ),
            "aa" to validSeal.copyForTest(antiAlias = false),
            "scissor" to validSeal.copyForTest(conservativeScissor = GPUPixelBounds(1, 1, 4, 4)),
            "render key" to validSeal.copyForTest(
                renderPipelineKey = GPURenderPipelineKey("pipeline.core.analytic.forged"),
            ),
            "binding" to validSeal.copyForTest(bindingLayoutHash = "layout.core.analytic.forged"),
            "generation" to validSeal.copyForTest(resourceGeneration = 1L),
            "payload" to validSeal.copyForTest(payloadBytes = corruptedPayload),
            "offset" to validSeal.copyForTest(plan = forgedOffsetPlan),
            "plan hash" to validSeal.copyForTest(plan = forgedHashPlan),
            "payload hash" to validSeal.copyForTest(plan = forgedPayloadHashPlan),
        )

        cases.forEach { (label, forgedSeal) ->
            val forgedPacket = cloneCorePacket(
                packet,
                analyticClipUniformSeal = forgedSeal,
            )
            val plan = validPlan.replacingCorePacket(packet, forgedPacket)
            val events = mutableListOf<String>()
            val result = preflighter(
                RecordingResourceProvider(events),
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
                context = clipPreflightContext(plan),
                capabilities = pathCapabilities(),
            ).preflight(plan)

            assertEquals(
                "invalid.preflight.core_primitive_analytic_clip_uniform_seal",
                assertIs<GPUFramePreflightResult.Refused>(result, label).diagnostic.code.value,
                label,
            )
            assertTrue(events.isEmpty(), "$label produced preflight side effects: $events")
        }
    }

    @Test
    fun `analytic intersection depth three preflight retains one exact uniform160 pass seal`() {
        val plan = preparedAnalyticFramePlan(
            mapOf(
                84 to analyticIntersectionPlan(
                    GPUClipAnalyticElement(
                        GPUClipExecutionGeometry.Rect(GPUBounds(0.25f, 0.25f, 3.75f, 3.75f)),
                        true,
                    ),
                    GPUClipAnalyticElement(
                        GPUClipExecutionGeometry.RRect(
                            GPUBounds(0.5f, 0.5f, 3.5f, 3.5f),
                            List(4) { listOf(0.5f, 0.75f) }.flatten(),
                        ),
                        false,
                    ),
                    GPUClipAnalyticElement(
                        GPUClipExecutionGeometry.Rect(GPUBounds(1f, 0.75f, 3.25f, 3f)),
                        true,
                    ),
                ),
            ),
        )
        val events = mutableListOf<String>()

        val result = preflighter(
            RecordingResourceProvider(events),
            RecordingCompletionProvider(events),
            RecordingSurfaceProvider(events),
            context = clipPreflightContext(plan),
            capabilities = pathCapabilities(),
        ).preflight(plan)

        val prepared = assertIs<GPUFramePreflightResult.Prepared>(
            result,
            (result as? GPUFramePreflightResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}"
            },
        ).frame
        val scope = prepared.encoderPlan.scopes.single()
        val routes = assertIs<GPUCorePrimitiveDirectNativeRouteSeal.Routes>(
            scope.corePrimitiveDirectNativeRouteSeal,
        )
        val passSeal = requireNotNull(routes.preparedPassSeal)
        assertTrue(passSeal.analyticClipUniformSeals.isEmpty())
        assertEquals(1, passSeal.analyticIntersectionUniformSeals.size)
        assertEquals(3, passSeal.analyticIntersectionUniformSeals.single().elements.size)
        assertTrue(events.isNotEmpty())
    }

    @Test
    fun `analytic uniform160 corruption matrix refuses before every preflight side effect`() {
        val validPlan = preparedAnalyticFramePlan(
            mapOf(
                85 to analyticIntersectionPlan(
                    GPUClipAnalyticElement(
                        GPUClipExecutionGeometry.Rect(GPUBounds(0.25f, 0.25f, 3.75f, 3.75f)),
                        true,
                    ),
                    GPUClipAnalyticElement(
                        GPUClipExecutionGeometry.RRect(
                            GPUBounds(0.5f, 0.75f, 3.5f, 3.25f),
                            List(4) { listOf(0.5f, 0.75f) }.flatten(),
                        ),
                        false,
                    ),
                ),
            ),
        )
        val render = validPlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single()
        val packet = render.drawPackets.single()
        val validSeal = requireNotNull(
            packet.corePrimitivePreparedAuthority?.analyticIntersectionUniformSeal,
        )
        fun corruptedInt(offset: Int, value: Int): ByteArray =
            validSeal.payloadBytesSnapshot().also { bytes ->
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, value)
            }
        fun corruptedFloat(offset: Int, value: Float): ByteArray =
            validSeal.payloadBytesSnapshot().also { bytes ->
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putFloat(offset, value)
            }
        val reversed = validSeal.elements.reversed()
        val first = validSeal.elements.first()
        val forgedOffsetPlan = validSeal.plan.copyForTest(
            totalBytes = validSeal.plan.totalBytes + validSeal.plan.alignmentBytes,
            slots = listOf(validSeal.plan.slots.single().copy(alignedOffset = validSeal.plan.alignmentBytes)),
        )
        val cases = listOf(
            "count one" to validSeal.copyForTest(payloadBytes = corruptedInt(8, 1)),
            "count five" to validSeal.copyForTest(payloadBytes = corruptedInt(8, 5)),
            "order" to validSeal.copyForTest(elements = reversed),
            "pad" to validSeal.copyForTest(payloadBytes = corruptedInt(12, 1)),
            "inactive" to validSeal.copyForTest(payloadBytes = corruptedInt(96, 1)),
            "kind" to validSeal.copyForTest(payloadBytes = corruptedInt(56, 2)),
            "aa" to validSeal.copyForTest(payloadBytes = corruptedInt(60, 2)),
            "bounds payload" to validSeal.copyForTest(payloadBytes = corruptedFloat(32, 0.5f)),
            "radii payload" to validSeal.copyForTest(payloadBytes = corruptedFloat(80, 0.25f)),
            "bounds authority" to validSeal.copyForTest(
                elements = listOf(
                    GPUCorePrimitiveAnalyticIntersectionElementSeal(
                        first.clipType,
                        listOf(0.5f, 0.25f, 3.75f, 3.75f),
                        first.clipRadii,
                        first.antiAlias,
                    ),
                    validSeal.elements[1],
                ),
            ),
            "identity" to validSeal.copyForTest(clipCanonicalIdentity = "analytic-intersection-forged"),
            "scissor" to validSeal.copyForTest(conservativeScissor = GPUPixelBounds(1, 1, 3, 3)),
            "command" to validSeal.copyForTest(commandId = validSeal.commandId + 1),
            "packet" to validSeal.copyForTest(packetId = GPUDrawPacketID("packet.analytic-intersection.forged")),
            "slot offset" to validSeal.copyForTest(plan = forgedOffsetPlan),
            "plan hash" to validSeal.copyForTest(
                plan = validSeal.plan.copyForTest(planHash = "forged-plan-hash"),
            ),
            "payload hash" to validSeal.copyForTest(
                plan = validSeal.plan.copyForTest(
                    slots = listOf(validSeal.plan.slots.single().copy(payloadHash = "forged-payload-hash")),
                ),
            ),
            "generation" to validSeal.copyForTest(resourceGeneration = 1L),
            "layout" to validSeal.copyForTest(bindingLayoutHash = "layout.core.uniform160.forged"),
        )

        cases.forEach { (label, forgedSeal) ->
            val forgedPacket = cloneCorePacket(packet, analyticIntersectionUniformSeal = forgedSeal)
            val plan = validPlan.replacingCorePacket(packet, forgedPacket)
            val events = mutableListOf<String>()
            val result = preflighter(
                RecordingResourceProvider(events),
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
                context = clipPreflightContext(plan),
                capabilities = pathCapabilities(),
            ).preflight(plan)

            assertEquals(
                "invalid.preflight.core_primitive_analytic_intersection_uniform_seal",
                assertIs<GPUFramePreflightResult.Refused>(result, label).diagnostic.code.value,
                label,
            )
            assertTrue(events.isEmpty(), "$label produced preflight side effects: $events")
        }
    }

    @Test
    fun `path stencil preflight retains one exact ordered pair seal without native materialization`() {
        val plan = preparedPathFramePlan(mixed = false)
        val events = mutableListOf<String>()

        val result = preflighter(
                RecordingResourceProvider(events),
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
                context = clipPreflightContext(plan),
                capabilities = pathCapabilities(),
            ).preflight(plan)
        val prepared = assertIs<GPUFramePreflightResult.Prepared>(
            result,
            (result as? GPUFramePreflightResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}"
            },
        ).frame

        val render = plan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single()
        val scope = prepared.encoderPlan.scopes.single()
        assertIs<GPUCorePrimitiveDirectNativeRouteSeal.Empty>(scope.corePrimitiveDirectNativeRouteSeal)
        val pathSeal = assertIs<GPUCorePrimitivePathStencilNativeRouteSeal.Pairs>(
            scope.corePrimitivePathStencilNativeRouteSeal,
        )
        assertEquals(render.drawPackets.map(GPUDrawPacket::packetId), pathSeal.flattenedPacketIds)
        assertEquals(1, pathSeal.orderedPairs.size)
        val pair = pathSeal.orderedPairs.single()
        assertEquals(GPUDrawPacketRole.PathStencilProducer, render.drawPackets[0].role)
        assertEquals(GPUDrawPacketRole.PathStencilCover, render.drawPackets[1].role)
        assertEquals(render.drawPackets[0].packetId, pair.producerPacketId)
        assertEquals(render.drawPackets[1].packetId, pair.coverPacketId)
        val preparedPass = requireNotNull(pathSeal.preparedPassSeal)
        assertEquals(
            org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey.Role
                .PathStencilProducer,
            preparedPass.orderedPairs.single().producerStructuralPipelineKey.role,
        )
        assertEquals(
            org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey.Role
                .PathStencilCover,
            preparedPass.orderedPairs.single().coverStructuralPipelineKey.role,
        )
        assertSame(
            requireNotNull(render.drawPackets[0].corePrimitivePreparedAuthority).uniformSlabSeal,
            preparedPass.uniformSlabSeal,
        )
        val unified = assertIs<GPUCorePrimitiveNativeScopeRouteSeal.Routes>(
            scope.corePrimitiveNativeScopeRouteSeal,
        )
        assertEquals(render.drawPackets.map(GPUDrawPacket::packetId), unified.flattenedPacketIds)
        assertEquals(
            listOf(GPUCorePrimitiveNativeScopeRouteUnit.PathPair::class),
            unified.orderedUnits.map { it::class },
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
            scope.nativeOperandKeys.map(GPUPreparedNativeOperandKey::role),
        )
        assertTrue(scope.nativeOperandKeys.all {
            it.ownership == GPUPreparedNativeOperandOwnership.Borrowed
        })
        val missingDepthStencilKeyScope = GPUCommandEncoderScopePlan(
            scope.sourceStepIndex,
            scope.operationKind,
            scope.scopeLabel,
            scope.sourceTaskIds,
            scope.sourcePacketIds,
            scope.facadeOperationClasses,
            scope.targetGeneration,
            scope.resourceGenerationLabels,
            scope.passCommandStream,
            scope.corePrimitiveDirectNativeRouteSeal,
            scope.corePrimitivePathStencilNativeRouteSeal,
            scope.corePrimitiveNativeScopeRouteSeal,
        )
        assertFailsWith<IllegalArgumentException> {
            missingDepthStencilKeyScope.attachNativeOperandKeys(
                scope.nativeOperandKeys.filter {
                    it.role != GPUPreparedNativeOperandRole.RenderDepthStencilTarget
                },
            )
        }
    }

    @Test
    fun `mixed direct path direct preflight partitions derived seals and retains one ordered scope seal`() {
        val plan = preparedPathFramePlan(mixed = true)
        val events = mutableListOf<String>()

        val result = preflighter(
                RecordingResourceProvider(events),
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
                context = clipPreflightContext(plan),
                capabilities = pathCapabilities(),
            ).preflight(plan)
        val prepared = assertIs<GPUFramePreflightResult.Prepared>(
            result,
            (result as? GPUFramePreflightResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}"
            },
        ).frame

        val render = plan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single()
        val scope = prepared.encoderPlan.scopes.single()
        val direct = assertIs<GPUCorePrimitiveDirectNativeRouteSeal.Routes>(
            scope.corePrimitiveDirectNativeRouteSeal,
        )
        val path = assertIs<GPUCorePrimitivePathStencilNativeRouteSeal.Pairs>(
            scope.corePrimitivePathStencilNativeRouteSeal,
        )
        val unified = assertIs<GPUCorePrimitiveNativeScopeRouteSeal.Routes>(
            scope.corePrimitiveNativeScopeRouteSeal,
        )

        assertEquals(listOf(render.drawPackets[0].packetId, render.drawPackets[3].packetId),
            direct.routesByPacketId.keys.toList())
        assertEquals(listOf(render.drawPackets[1].packetId, render.drawPackets[2].packetId),
            path.flattenedPacketIds)
        assertEquals(render.drawPackets.map(GPUDrawPacket::packetId), unified.flattenedPacketIds)
        assertEquals(
            listOf(
                GPUCorePrimitiveNativeScopeRouteUnit.Direct::class,
                GPUCorePrimitiveNativeScopeRouteUnit.PathPair::class,
                GPUCorePrimitiveNativeScopeRouteUnit.Direct::class,
            ),
            unified.orderedUnits.map { it::class },
        )
        assertEquals(listOf(71, 72, 73), unified.commandIds)
        val arena = packCorePrimitiveNativeScopeGeometry(unified)
        assertEquals(
            listOf(
                GPUCorePrimitiveNativeScopeArenaRole.Direct,
                GPUCorePrimitiveNativeScopeArenaRole.PathProducer,
                GPUCorePrimitiveNativeScopeArenaRole.PathCover,
                GPUCorePrimitiveNativeScopeArenaRole.Direct,
            ),
            arena.slices.map { it.role },
        )
        assertEquals(
            listOf(
                GPUPreparedNativeOperandRole.RenderColorTarget,
                GPUPreparedNativeOperandRole.RenderDepthStencilTarget,
                GPUPreparedNativeOperandRole.RenderPipeline,
                GPUPreparedNativeOperandRole.RenderPipeline,
                GPUPreparedNativeOperandRole.RenderPipeline,
                GPUPreparedNativeOperandRole.RenderVertexBuffer,
                GPUPreparedNativeOperandRole.RenderIndexBuffer,
                GPUPreparedNativeOperandRole.RenderBindGroup,
                GPUPreparedNativeOperandRole.RenderBindGroup,
                GPUPreparedNativeOperandRole.RenderBindGroup,
                GPUPreparedNativeOperandRole.RenderBindGroup,
            ),
            scope.nativeOperandKeys.map(GPUPreparedNativeOperandKey::role),
        )
    }

    @Test
    fun `path stencil corruption matrix refuses before every provider side effect`() {
        val valid = preparedPathFramePlan(mixed = false)
        val render = valid.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single()
        val producer = render.drawPackets[0]
        val cover = render.drawPackets[1]
        val pathUse = render.resourceUses.single { it.role == GPUFrameResourceRole.PathDepthStencil }
        val corruptions = listOf(
            "order" to renderWith(render, drawPackets = listOf(cover, producer)),
            "role" to renderWith(
                render,
                drawPackets = listOf(
                    cloneCorePacket(producer, role = GPUDrawPacketRole.PathStencilCover),
                    cover,
                ),
            ),
            "command" to renderWith(
                render,
                drawPackets = listOf(cloneCorePacket(producer, commandIdValue = 999), cover),
            ),
            "key" to renderWith(
                render,
                drawPackets = listOf(
                    cloneCorePacket(
                        producer,
                        renderPipelineKey = GPURenderPipelineKey("pipeline.corrupt.path"),
                    ),
                    cover,
                ),
            ),
            "scissor" to renderWith(
                render,
                drawPackets = listOf(cloneCorePacket(producer, scissorBoundsHash = "scissor.corrupt"), cover),
            ),
            "depth-stencil" to renderWith(render, depthStencilLoadStore = null),
            "resource-use" to renderWith(render, resourceUses = render.resourceUses - pathUse),
            "slab" to renderWith(
                render,
                drawPackets = listOf(cloneCorePacket(producer, dropUniformSlabSeal = true), cover),
            ),
        )

        corruptions.forEach { (label, corruptedRender) ->
            val plan = replaceRender(valid, corruptedRender)
            val events = mutableListOf<String>()
            val result = preflighter(
                RecordingResourceProvider(events),
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
                context = clipPreflightContext(plan),
                capabilities = pathCapabilities(),
            ).preflight(plan)

            assertIs<GPUFramePreflightResult.Refused>(result, label)
            assertTrue(events.isEmpty(), "$label escaped pure validation: $events")
        }
    }

    @Test
    fun `unsupported path clip refuses before every provider side effect`() {
        val valid = preparedPathFramePlan(mixed = false)
        val render = valid.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single()
        val producer = render.drawPackets[0]
        val cover = render.drawPackets[1]
        val unsupportedClip = GPUClipExecutionPlan.AnalyticCoverage(
            geometry = GPUClipExecutionGeometry.Rect(GPUBounds(0f, 0f, 4f, 4f)),
            scissor = null,
            antiAlias = true,
        )
        val forgedSemantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(producer.semanticPayload)
            .withClipExecutionPlanIdentity(unsupportedClip.canonicalIdentity())
        val forgedRender = renderWith(
            render,
            drawPackets = listOf(
                cloneCorePacket(
                    producer,
                    clipExecutionPlan = unsupportedClip,
                    semanticPayload = forgedSemantic,
                ),
                cloneCorePacket(
                    cover,
                    clipExecutionPlan = unsupportedClip,
                    semanticPayload = forgedSemantic,
                ),
            ),
        )
        val plan = replaceRender(valid, forgedRender)
        val events = mutableListOf<String>()

        val result = preflighter(
            RecordingResourceProvider(events),
            RecordingCompletionProvider(events),
            RecordingSurfaceProvider(events),
            context = clipPreflightContext(plan),
            capabilities = pathCapabilities(),
        ).preflight(plan)

        assertEquals(
            "invalid.preflight.core_primitive_semantic_integrity",
            assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
        )
        assertTrue(events.isEmpty(), "unsupported path clip escaped pure validation: $events")
    }

    @Test
    fun `direct only depth stencil forgery refuses before every provider side effect`() {
        val valid = framePlan(listOf(coreDirectPrepare(), coreRenderStep(coreSemantic())))
        val prepare = valid.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>().single()
        val render = valid.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single()
        val packet = render.drawPackets.single()
        val neutralStructural = requireNotNull(packet.corePrimitivePreparedAuthority)
            .structuralPipelineKey.copy(depthStencil = corePrimitiveDirectPathDepthStencilState())
        val neutralPipeline = neutralStructural.stableRenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY)
        val forgedPacket = cloneCorePacket(
            packet,
            renderPipelineKey = neutralPipeline,
            structuralPipelineKey = neutralStructural,
        )
        val depthStencil = GPUFrameTextureRef("texture.core.direct-forged-depth-stencil")
        val forgedPrepare = GPUFrameStep.PrepareResourcesStep(
            prepare.requests + GPUResourcePreparationRequest(
                depthStencil,
                GPUFrameTextureDescriptor(
                    GPUPixelBounds(0, 0, 4, 4),
                    GPUColorFormat("depth24plus-stencil8"),
                    1,
                ),
                GPUFrameResourceRole.PathDepthStencil,
                setOf(GPUFrameResourceUsage.RenderAttachment),
                GPUFrameResourceLifetime.FrameLocal,
                64L,
                "core.direct-forged-depth-stencil",
            ),
            prepare.sourceTaskIds,
        )
        val forgedRender = renderWith(
            render,
            drawPackets = listOf(forgedPacket),
            resourceUses = render.resourceUses + GPUFrameResourceUse(
                depthStencil,
                GPUFrameResourceRole.PathDepthStencil,
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceLifetime.FrameLocal,
                true,
            ),
            depthStencilLoadStore = GPUDepthStencilLoadStorePlan.WritableStencil(
                GPUStencilLoadOperation.Clear,
                GPUStorePlan.Discard,
                0u,
            ),
        )
        val plan = valid.withSteps(listOf(forgedPrepare, forgedRender))
        val events = mutableListOf<String>()

        val result = preflighter(
            RecordingResourceProvider(events),
            RecordingCompletionProvider(events),
            RecordingSurfaceProvider(events),
            context = clipPreflightContext(plan),
        ).preflight(plan)

        assertIs<GPUFramePreflightResult.Refused>(result)
        assertTrue(events.isEmpty(), "direct PathDepthStencil forgery escaped pure validation: $events")
    }

    @Test
    fun `path shared geometry roles and refs are exclusive to its unique render scope`() {
        val valid = preparedPathFramePlan(mixed = false)
        val render = valid.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single()
        val vertexUse = render.resourceUses.single { it.role == GPUFrameResourceRole.VertexData }
        val foreignPacket = packet("packet.foreign.path-resource", 990)
        val foreignRender = GPUFrameStep.RenderPassStep(
            target = render.target,
            loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
            samplePlan = GPUSamplePlan.SingleSampleFrame,
            resourceUses = listOf(vertexUse),
            drawPackets = listOf(foreignPacket),
            sourceTaskIds = listOf(GPUTaskID("task.foreign.path-resource")),
            batches = listOf(batch("batch.foreign.path-resource", foreignPacket, "task.foreign.path-resource")),
        )
        val aliasedPathRender = renderWith(
            render,
            resourceUses = render.resourceUses + vertexUse.copy(role = GPUFrameResourceRole.StorageData),
        )
        val foreignCompute = GPUFrameStep.ComputePassStep(
            target = render.target,
            resourceUses = listOf(vertexUse),
            dispatches = listOf(GPUComputeDispatch(GPUComputePipelineKey("compute.foreign.path-resource"), 1, 1, 1)),
            sourceTaskIds = listOf(GPUTaskID("task.compute.foreign.path-resource")),
        )
        val foreignUpload = GPUFrameStep.UploadResourceStep(
            staging = vertexUse.resource as GPUFrameBufferRef,
            destination = render.target,
            layout = GPUUploadLayout(0, 4, 1, 4),
            sourceTaskIds = listOf(GPUTaskID("task.upload.foreign.path-resource")),
        )
        val foreignCopy = GPUFrameStep.CopyResourceStep(
            source = vertexUse.resource,
            destination = render.target,
            regions = listOf(GPUResourceCopyRegion(0, 0, null, 4)),
            sourceTaskIds = listOf(GPUTaskID("task.copy.foreign.path-resource")),
        )
        val scenarios = listOf(
            "render" to valid.withSteps(valid.steps + foreignRender),
            "aliased-path-render" to replaceRender(valid, aliasedPathRender),
            "compute" to valid.withSteps(valid.steps + foreignCompute),
            "upload" to valid.withSteps(valid.steps + foreignUpload),
            "copy" to valid.withSteps(valid.steps + foreignCopy),
        )

        scenarios.forEach { (label, plan) ->
            val events = mutableListOf<String>()
            val result = preflighter(
                RecordingResourceProvider(events),
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
                context = clipPreflightContext(plan),
                capabilities = pathCapabilities(),
            ).preflight(plan)

            assertIs<GPUFramePreflightResult.Refused>(result, label)
            assertTrue(events.isEmpty(), "$label escaped pure validation: $events")
        }
    }

    @Test
    fun `forged direct structural role refuses before provider and native side effects`() {
        val valid = framePlan(listOf(coreDirectPrepare(), coreRenderStep(coreSemantic())))
        val render = valid.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single()
        val directPacket = render.drawPackets.single()
        val pathPlan = preparedPathFramePlan(mixed = false)
        val pathProducerAuthority = requireNotNull(
            pathPlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single()
                .drawPackets.first().corePrimitivePreparedAuthority,
        )
        val forgedPacket = cloneCorePacket(
            directPacket,
            renderPipelineKey = pathProducerAuthority.renderPipelineKey,
            structuralPipelineKey = pathProducerAuthority.structuralPipelineKey,
        )
        val plan = replaceRender(valid, renderWith(render, drawPackets = listOf(forgedPacket)))
        val events = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val resources = GPUConcreteResourceProvider(leaseFactory = adapter)
        try {
            val result = preflighter(
                resources,
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
                context = clipPreflightContext(plan),
                nativeBoundary = adapter.bindNativeFrameBoundary(
                    resources,
                    RenderOnlyNativePayloadMaterializer(events),
                ),
            ).preflight(plan)

            assertIs<GPUFramePreflightResult.Refused>(result)
            assertTrue(events.isEmpty(), "forged role escaped pure validation: $events")
            assertEquals(0, resources.pendingPhysicalReservationCount)
            assertTrue(resources.telemetry.dumpEvents.isEmpty())
        } finally {
            adapter.close()
        }
    }

    @Test
    fun `direct native route refusal and non canonical load refuse before preparation`() {
        val directPrepare = coreDirectPrepare()
        val unsupportedBlend = coreRenderStep(
            coreSemantic(blendMode = GPUBlendMode.SRC),
            blendPlan = coreBlend(GPUBlendMode.SRC),
        )
        val canonical = coreRenderStep(coreSemantic())
        val badLoad = GPUFrameStep.RenderPassStep(
            canonical.target,
            GPULoadStorePlan("load", GPUStorePlan.Store),
            canonical.samplePlan,
            canonical.resourceUses,
            canonical.drawPackets,
            canonical.sourceTaskIds,
            canonical.batches,
        )
        listOf(
            framePlan(listOf(directPrepare, unsupportedBlend)) to
                "unsupported.native-core-primitive.blend",
            framePlan(listOf(directPrepare, badLoad)) to
                "invalid.preflight.core_primitive_direct_load_store",
        ).forEach { (plan, expectedCode) ->
            val events = mutableListOf<String>()
            val result = preflighter(
                RecordingResourceProvider(events),
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
            ).preflight(plan)

            assertEquals(
                expectedCode,
                assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
            )
            assertTrue(events.isEmpty(), "pure validation side effects: $events")
        }
    }

    @Test
    fun `core primitive native route has one production classification site`() {
        val sources = File("src/main/kotlin")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        val production = sources.joinToString("\n") { it.readText() }

        assertEquals(
            2,
            Regex("""\bvalidateCorePrimitiveDirectNativeRoute\(""").findAll(production).count(),
            "expected exactly the declaration and the sole pure-preflight call site",
        )
        assertFalse(production.contains("directCorePrimitiveNativeRouteOrNull"))
        assertFalse(production.contains("validateCorePrimitiveNativeRoute"))
        assertEquals(
            3,
            Regex("""\bvalidateCorePrimitiveCoverageSampleAuthority\(""").findAll(production).count(),
            "expected one shared declaration plus the builder and pure-preflight call sites",
        )
    }

    @Test
    fun `core primitive hot path reuses builder pipeline and uniform slab seals`() {
        val sourceRoot = File("src/main/kotlin/org/graphiks/kanvas/gpu/renderer")
        val builder = sourceRoot.resolve(
            "recording/GPUCorePrimitivePreparedFrameTaskListBuilder.kt",
        ).readText()
        val preflight = sourceRoot.resolve("execution/GPUFramePreflighter.kt").readText()
        val preparedFrame = sourceRoot.resolve("execution/PreparedGPUFrame.kt").readText()
        val coverageCommandRoute = sourceRoot.resolve(
            "execution/GPUCorePrimitiveCoverageMaskPreparedExecutionRoute.kt",
        ).readText()
        val materializer = sourceRoot.resolve(
            "execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt",
        ).readText()

        assertEquals(
            4,
            Regex("""GPUUniformSlabPlanner\.plan\(""").findAll(builder).count(),
            "recording owns uniform32, analytic uniform64/uniform160, and coverage-mask uniform64 sites",
        )
        assertEquals(
            0,
            listOf(preflight, materializer)
                .sumOf { source -> Regex("""GPUUniformSlabPlanner\.plan\(""").findAll(source).count() },
            "execution hot paths must validate sealed plans without planning them again",
        )
        assertFalse(
            preflight.contains("corePrimitiveRenderPipelineKey("),
            "pure preflight must validate the typed builder key without rebuilding its SHA/string label",
        )
        assertTrue(
            builder.contains("val coverageMaskConsumerCommandIds ="),
            "coverage-mask consumer membership must be precomputed once before resource-use lowering",
        )
        assertFalse(
            builder.contains("in coverageMaskUniformPackets.map(GPUDrawPacket::commandIdValue)"),
            "coverage-mask consumer membership must not rebuild a list for every consumer",
        )
        assertFalse(
            listOf(preflight, preparedFrame, coverageCommandRoute).any { source ->
                Regex(
                    """(?:producerSlots|consumerSlots)\s*\??\.\s*single(?:OrNull)?\s*\{""",
                )
                    .containsMatchIn(source)
            },
            "coverage-mask scope hot paths must index their sealed uniform slot in O(1)",
        )
        assertFalse(
            materializer.contains("corePrimitiveRenderPipelineKey("),
            "native materialization must consume the typed preflight seal without rebuilding the key",
        )
        assertFalse(
            materializer.contains("ByteArray(uniformSlabPlan.totalBytes.toInt())"),
            "native materialization must upload the builder-packed immutable uniform bytes",
        )
    }

    @Test
    fun `native boundary propagates every core route refusal before preparation without direct slabs`() {
        val inverseGeometry = GPUCorePrimitiveGeometryInput.TriangulatedPath(
            vertices = listOf(1f, 1f, 3f, 1f, 2f, 3f),
            indices = listOf(0, 1, 2),
            sourceContourStarts = listOf(0),
            sourceVertexCount = 3,
            coverBounds = GPUPixelBounds(0, 0, 4, 4),
            geometryMode = GPUCorePrimitiveGeometryMode.DirectTriangles,
            inverseFill = true,
        )
        val stencilGeometry = GPUCorePrimitiveGeometryInput.TriangulatedPath(
            vertices = listOf(
                -1f, -1f, 1f, 1f, 3f, 1f,
                -1f, -1f, 3f, 1f, 2f, 3f,
                -1f, -1f, 2f, 3f, 1f, 1f,
            ),
            indices = (0..8).toList(),
            sourceContourStarts = listOf(0),
            sourceVertexCount = 3,
            coverBounds = GPUPixelBounds(0, 0, 4, 4),
            geometryMode = GPUCorePrimitiveGeometryMode.StencilEdgeFan,
        )
        val srcBlend = coreBlend(GPUBlendMode.SRC)
        val cases = listOf(
            framePlan(
                listOf(
                    prepareScene(),
                    coreRenderStep(
                        coreSemantic(
                            sourceFamily = GPUCorePrimitiveSourceFamily.RRect,
                            geometry = GPUCorePrimitiveGeometryInput.RRect(
                                1f,
                                1f,
                                3f,
                                3f,
                                List(8) { 0.5f },
                            ),
                        ),
                    ),
                ),
            ) to "unsupported.core_primitive.coverage_sample.rrect_not_promoted",
            framePlan(
                listOf(
                    prepareScene(),
                    coreRenderStep(
                        coreSemantic(coverageMode = GPUCorePrimitiveCoverageMode.ScalarAA),
                    ),
                ),
            ) to "unsupported.core_primitive.coverage_sample.scalar_aa_not_promoted",
            framePlan(
                listOf(
                    prepareScene(),
                    coreRenderStep(
                        coreSemantic(
                            sourceFamily = GPUCorePrimitiveSourceFamily.Path,
                            geometry = stencilGeometry,
                        ),
                    ),
                ),
            ) to "invalid.core_primitive.coverage_sample.geometry_coverage",
            framePlan(
                listOf(
                    prepareScene(),
                    coreRenderStep(
                        coreSemantic(
                            sourceFamily = GPUCorePrimitiveSourceFamily.Path,
                            geometry = stencilGeometry,
                            coverageMode = GPUCorePrimitiveCoverageMode.Stencil1x,
                        ),
                    ),
                ),
            ) to "unsupported.native-core-primitive.coverage",
            framePlan(
                listOf(
                    prepareScene(),
                    coreRenderStep(
                        coreSemantic(
                            sourceFamily = GPUCorePrimitiveSourceFamily.Path,
                            geometry = inverseGeometry,
                        ),
                    ),
                ),
            ) to "unsupported.native-core-primitive.inverse-fill",
            coreMaskFramePlan() to "unsupported.native-core-primitive.clip",
            framePlan(
                listOf(
                    prepareScene(),
                    coreRenderStep(
                        coreSemantic(blendMode = GPUBlendMode.SRC),
                        blendPlan = srcBlend,
                    ),
                ),
            ) to "unsupported.native-core-primitive.blend",
            framePlan(
                listOf(
                    prepareScene(),
                    coreRenderStep(
                        coreSemantic(),
                        samplePlan = GPUSamplePlan.MultisampleFrame(4),
                    ),
                ),
            ) to "unsupported.core_primitive.coverage_sample.color_capability",
            framePlan(
                listOf(
                    prepareScene(format = "rgba16float"),
                    coreRenderStep(coreSemantic()),
                ),
            ) to "unsupported.native-core-primitive.target-format",
        )

        cases.forEach { (plan, expectedCode) ->
            val events = mutableListOf<String>()
            val adapter = GPURuntimeResourceAdapter()
            val resources = GPUConcreteResourceProvider(leaseFactory = adapter)
            try {
                val result = preflighter(
                    resources = resources,
                    completion = RecordingCompletionProvider(events),
                    surface = RecordingSurfaceProvider(events),
                    context = clipPreflightContext(plan),
                    nativeBoundary = adapter.bindNativeFrameBoundary(
                        resources,
                        RenderOnlyNativePayloadMaterializer(events),
                    ),
                ).preflight(plan)

                assertEquals(
                    expectedCode,
                    assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
                )
                assertTrue(events.isEmpty(), "native route refusal side effects: $events")
                assertEquals(0, resources.pendingPhysicalReservationCount)
                assertTrue(resources.telemetry.dumpEvents.isEmpty())
            } finally {
                adapter.close()
            }
        }
    }

    @Test
    fun `native direct core uniform corruption refuses before every preflight side effect`() {
        val valid = coreSemantic()
        val validBlock = requireNotNull(valid.payloadRef.uniformBlock)
        val corruptedPayloadRefs = listOf(
            valid.payloadRef.copy(uniformBlock = null),
            valid.payloadRef.copy(uniformSlot = null),
            valid.payloadRef.copy(uniformBlock = validBlock.copy(byteSize = validBlock.byteSize - 4L)),
        )

        corruptedPayloadRefs.forEach { payloadRef ->
            val corrupted = GPUDrawSemanticPayload.CorePrimitive(
                payloadRef = payloadRef,
                sourceFamily = valid.sourceFamily,
                geometry = valid.geometry,
                premultipliedRgba = valid.premultipliedRgba,
                targetBounds = valid.targetBounds,
                scissorBounds = valid.scissorBounds,
                clipCoveragePlan = valid.clipCoveragePlan,
                clipExecutionPlanIdentity = valid.clipExecutionPlanIdentity,
                blendPlanIdentity = valid.blendPlanIdentity,
                frameProvenance = valid.frameProvenance,
                canonicalHash = valid.canonicalHash,
                coverageMode = valid.coverageMode,
                analysisRecordId = valid.analysisRecordId,
                analysisCommandFamily = valid.analysisCommandFamily,
                rectRouteAuthority = valid.rectRouteAuthority,
                rectGeometryAuthority = valid.rectGeometryAuthority,
            )
            val events = mutableListOf<String>()
            val adapter = GPURuntimeResourceAdapter()
            val resources = GPUConcreteResourceProvider(leaseFactory = adapter)
            try {
                val result = preflighter(
                    resources = resources,
                    completion = RecordingCompletionProvider(events),
                    surface = RecordingSurfaceProvider(events),
                    nativeBoundary = adapter.bindNativeFrameBoundary(
                        resources,
                        RenderOnlyNativePayloadMaterializer(events),
                    ),
                ).preflight(framePlan(listOf(coreDirectPrepare(), coreRenderStep(corrupted))))

                assertEquals(
                    "invalid.preflight.core_primitive_semantic_integrity",
                    assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
                )
                assertTrue(events.isEmpty(), "uniform refusal side effects: $events")
                assertEquals(0, resources.pendingPhysicalReservationCount)
                assertTrue(resources.telemetry.dumpEvents.isEmpty())
            } finally {
                adapter.close()
            }
        }
    }

    @Test
    fun `core stencil plan without native boundary preserves the existing B3_0 phase`() {
        val plan = coreStencilFramePlan()
        val events = mutableListOf<String>()

        val result = preflighter(
            resources = RecordingResourceProvider(events),
            completion = RecordingCompletionProvider(events),
            surface = RecordingSurfaceProvider(events),
            context = clipPreflightContext(plan),
        ).preflight(plan)

        assertIs<GPUFramePreflightResult.Prepared>(result)
        assertTrue(events.any { it.startsWith("resource:prepare:") })
    }

    @Test
    fun `direct core primitive slab corruption matrix refuses before side effects`() {
        val validPrepare = coreDirectPrepare()
        val validRender = coreRenderStep(coreSemantic())
        val scene = validPrepare.requests.single { it.role == GPUFrameResourceRole.SceneTarget }
        val vertex = validPrepare.requests.single { it.role == GPUFrameResourceRole.VertexData }
        val index = validPrepare.requests.single { it.role == GPUFrameResourceRole.IndexData }
        val uniform = validPrepare.requests.single { it.role == GPUFrameResourceRole.UniformData }
        fun clone(
            original: GPUResourcePreparationRequest,
            resource: GPUFrameResourceRef = original.resource,
            descriptor: org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceDescriptor = original.descriptor,
            role: GPUFrameResourceRole = original.role,
            usages: Set<GPUFrameResourceUsage> = original.usages,
            lifetime: GPUFrameResourceLifetime = original.lifetime,
        ) = GPUResourcePreparationRequest(
            resource,
            descriptor,
            role,
            usages,
            lifetime,
            (descriptor as? GPUFrameBufferDescriptor)?.byteSize ?: original.byteSize,
            original.diagnosticLabel,
        )
        fun prepare(requests: List<GPUResourcePreparationRequest>) = GPUFrameStep.PrepareResourcesStep(
            requests,
            validPrepare.sourceTaskIds,
        )
        val badWriteRender = GPUFrameStep.RenderPassStep(
            target = validRender.target,
            loadStore = validRender.loadStore,
            samplePlan = validRender.samplePlan,
            resourceUses = validRender.resourceUses.map { use ->
                if (use.role == GPUFrameResourceRole.VertexData) use.copy(write = true) else use
            },
            drawPackets = validRender.drawPackets,
            sourceTaskIds = validRender.sourceTaskIds,
            batches = validRender.batches,
        )
        val badUniformWriteRender = GPUFrameStep.RenderPassStep(
            target = validRender.target,
            loadStore = validRender.loadStore,
            samplePlan = validRender.samplePlan,
            resourceUses = validRender.resourceUses.map { use ->
                if (use.role == GPUFrameResourceRole.UniformData) use.copy(write = true) else use
            },
            drawPackets = validRender.drawPackets,
            sourceTaskIds = validRender.sourceTaskIds,
            batches = validRender.batches,
        )
        val plans = listOf(
            framePlan(listOf(prepare(listOf(scene, vertex, index)), validRender)),
            framePlan(listOf(prepare(listOf(scene, vertex, index, uniform, clone(uniform, resource = GPUFrameBufferRef("buffer.core.uniforms.extra")))), validRender)),
            framePlan(listOf(prepare(listOf(scene, clone(vertex, descriptor = GPUFrameBufferDescriptor(28L, 4L)), index, uniform)), validRender)),
            framePlan(listOf(prepare(listOf(scene, clone(vertex, descriptor = GPUFrameBufferDescriptor(32L, 8L)), index, uniform)), validRender)),
            framePlan(listOf(prepare(listOf(scene, clone(vertex, usages = setOf(GPUFrameResourceUsage.Vertex)), index, uniform)), validRender)),
            framePlan(listOf(prepare(listOf(scene, clone(vertex, lifetime = GPUFrameResourceLifetime.RecordingLocal), index, uniform)), validRender)),
            framePlan(listOf(prepare(listOf(scene, vertex, index, clone(uniform, descriptor = GPUFrameBufferDescriptor(32L, 256L)))), validRender)),
            framePlan(listOf(prepare(listOf(scene, vertex, index, clone(uniform, descriptor = GPUFrameBufferDescriptor(256L, 16L)))), validRender)),
            framePlan(listOf(prepare(listOf(scene, vertex, index, clone(uniform, usages = setOf(GPUFrameResourceUsage.Uniform)))), validRender)),
            framePlan(listOf(prepare(listOf(scene, vertex, index, clone(uniform, lifetime = GPUFrameResourceLifetime.RecordingLocal))), validRender)),
            framePlan(listOf(validPrepare, badWriteRender)),
            framePlan(listOf(validPrepare, badUniformWriteRender)),
        )
        plans.forEach { plan ->
            val events = mutableListOf<String>()
            val result = preflighter(
                RecordingResourceProvider(events),
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
            ).preflight(plan)
            assertIs<GPUFramePreflightResult.Refused>(result)
            assertTrue(events.isEmpty(), "pure validation side effects: $events")
        }

        val missingGenerationEvents = mutableListOf<String>()
        val missingGeneration = preflightContext().let { base ->
            GPUFramePreflightContext(
                base.targetId,
                base.deviceGeneration,
                base.targetGeneration,
                base.resourceGenerations - GPUFrameBufferRef("buffer.core.indices"),
                base.surfaceGeneration,
            )
        }
        val result = preflighter(
            RecordingResourceProvider(missingGenerationEvents),
            RecordingCompletionProvider(missingGenerationEvents),
            RecordingSurfaceProvider(missingGenerationEvents),
            context = missingGeneration,
        ).preflight(framePlan(listOf(validPrepare, validRender)))
        assertIs<GPUFramePreflightResult.Refused>(result)
        assertTrue(missingGenerationEvents.isEmpty())
    }

    @Test
    fun `core executable packet authority mismatches refuse before every preflight side effect`() {
        val semantic = coreSemantic()
        val cases = listOf(
            coreRenderStep(semantic, renderPipelineKey = GPURenderPipelineKey("pipeline.core.stale")),
            coreRenderStep(semantic, bindingLayoutHash = "layout.core.stale"),
            coreRenderStep(semantic, vertexSourceLabel = "vertices.core.stale"),
            coreRenderStep(semantic, targetStateHash = "target.core.stale"),
            coreRenderStep(semantic, scissorBoundsHash = "scissor.stale"),
            coreRenderStep(semantic, renderStepVersion = 2),
            coreRenderStep(semantic, role = GPUDrawPacketRole.DepthOnly),
        )

        cases.forEach { render ->
            val events = mutableListOf<String>()
            val result = preflighter(
                resources = RecordingResourceProvider(events),
                completion = RecordingCompletionProvider(events),
                surface = RecordingSurfaceProvider(events),
            ).preflight(framePlan(listOf(prepareScene(), render)))

            assertEquals(
                "invalid.preflight.core_primitive_packet_authority",
                assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
            )
            assertTrue(events.isEmpty(), "pure validation side effects: $events")
        }
    }

    @Test
    fun `core packet analysis substitution refuses before resource native and ticket side effects`() {
        val semantic = coreSemantic()
        val valid = framePlan(listOf(coreDirectPrepare(), coreRenderStep(semantic)))
        val render = valid.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single()
        val packet = render.drawPackets.single()
        val substituted = cloneCorePacket(
            packet = packet,
            analysisRecordId = "analysis.fill_rect.999",
        )
        val plan = replaceRender(valid, renderWith(render, drawPackets = listOf(substituted)))
        val events = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val resources = GPUConcreteResourceProvider(leaseFactory = adapter)
        try {
            val result = preflighter(
                resources = resources,
                completion = RecordingCompletionProvider(events),
                surface = RecordingSurfaceProvider(events),
                context = clipPreflightContext(plan),
                nativeBoundary = adapter.bindNativeFrameBoundary(
                    resources,
                    RenderOnlyNativePayloadMaterializer(events),
                ),
            ).preflight(plan)

            assertEquals(
                "invalid.preflight.core_primitive_semantic_integrity",
                assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
            )
            assertTrue(events.isEmpty(), "analysis substitution escaped pure validation: $events")
            assertEquals(0, resources.pendingPhysicalReservationCount)
            assertTrue(resources.telemetry.dumpEvents.isEmpty())
        } finally {
            adapter.close()
        }
    }

    @Test
    fun `core coverage sample matrix refuses before resource native registry and ticket side effects`() {
        val completeMsaa = msaaCapabilities(depthStencil = true)
        val colorOnlyMsaa = msaaCapabilities(depthStencil = false)
        val cases = listOf(
            Triple(
                "unsupported.core_primitive.coverage_sample.scalar_aa_not_promoted",
                coreRenderStep(coreSemantic(coverageMode = GPUCorePrimitiveCoverageMode.ScalarAA)),
                capabilities(),
            ),
            Triple(
                "unsupported.core_primitive.coverage_sample.rrect_not_promoted",
                coreRenderStep(
                    coreSemantic(
                        sourceFamily = GPUCorePrimitiveSourceFamily.RRect,
                        geometry = GPUCorePrimitiveGeometryInput.RRect(
                            1f,
                            1f,
                            3f,
                            3f,
                            List(8) { 0.5f },
                        ),
                    ),
                ),
                capabilities(),
            ),
            Triple(
                "invalid.core_primitive.coverage_sample.stencil_aa_requires_multisample",
                coreRenderStep(pathCoreSemantic(coverageMode = GPUCorePrimitiveCoverageMode.StencilAA)),
                capabilities(),
            ),
            Triple(
                "invalid.core_primitive.coverage_sample.stencil_1x_requires_single_sample",
                coreRenderStep(
                    pathCoreSemantic(),
                    samplePlan = GPUSamplePlan.MultisampleFrame(4),
                ),
                completeMsaa,
            ),
            Triple(
                "unsupported.core_primitive.coverage_sample.local_resolve",
                coreRenderStep(
                    coreSemantic(),
                    samplePlan = GPUSamplePlan.LocalResolveApproximation(4),
                ),
                capabilities(),
            ),
            Triple(
                "unsupported.core_primitive.coverage_sample.sample_count",
                coreRenderStep(coreSemantic(), samplePlan = GPUSamplePlan.MultisampleFrame(8)),
                completeMsaa,
            ),
            Triple(
                "invalid.preflight.core_primitive_semantic_integrity",
                coreRenderStep(
                    coreSemantic().withTargetBounds(GPUPixelBounds(0, 0, 0, 4)),
                    samplePlan = GPUSamplePlan.MultisampleFrame(4),
                ),
                completeMsaa,
            ),
            Triple(
                "unsupported.core_primitive.coverage_sample.color_capability",
                coreRenderStep(coreSemantic(), samplePlan = GPUSamplePlan.MultisampleFrame(4)),
                capabilities(),
            ),
            Triple(
                "unsupported.core_primitive.coverage_sample.multisample_not_promoted",
                coreRenderStep(coreSemantic(), samplePlan = GPUSamplePlan.MultisampleFrame(4)),
                completeMsaa,
            ),
            Triple(
                "unsupported.core_primitive.coverage_sample.depth_stencil_capability",
                coreRenderStep(
                    pathCoreSemantic(coverageMode = GPUCorePrimitiveCoverageMode.StencilAA),
                    samplePlan = GPUSamplePlan.MultisampleFrame(4),
                ),
                colorOnlyMsaa,
            ),
            Triple(
                "unsupported.core_primitive.coverage_sample.stencil_aa_not_promoted",
                coreRenderStep(
                    pathCoreSemantic(coverageMode = GPUCorePrimitiveCoverageMode.StencilAA),
                    samplePlan = GPUSamplePlan.MultisampleFrame(4),
                ),
                completeMsaa,
            ),
        )

        cases.forEach { (expectedCode, render, caseCapabilities) ->
            val plan = framePlan(
                listOf(prepareScene(), render),
                capabilities = caseCapabilities,
            )
            val events = mutableListOf<String>()
            val resources = RecordingResourceProvider(events)
            val result = preflighter(
                resources = resources,
                completion = RecordingCompletionProvider(events),
                surface = RecordingSurfaceProvider(events),
                capabilities = caseCapabilities,
            ).preflight(plan)

            assertEquals(
                expectedCode,
                assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
            )
            assertEquals(0, resources.beginFramePreparationCount, expectedCode)
            assertTrue(events.isEmpty(), "$expectedCode produced pure-validation side effects: $events")

            val nativeEvents = mutableListOf<String>()
            val adapter = GPURuntimeResourceAdapter()
            val concreteResources = GPUConcreteResourceProvider(leaseFactory = adapter)
            try {
                val nativeResult = preflighter(
                    resources = concreteResources,
                    completion = RecordingCompletionProvider(nativeEvents),
                    surface = RecordingSurfaceProvider(nativeEvents),
                    capabilities = caseCapabilities,
                    nativeBoundary = adapter.bindNativeFrameBoundary(
                        concreteResources,
                        RenderOnlyNativePayloadMaterializer(nativeEvents),
                    ),
                ).preflight(plan)

                assertEquals(
                    expectedCode,
                    assertIs<GPUFramePreflightResult.Refused>(nativeResult).diagnostic.code.value,
                )
                assertTrue(nativeEvents.isEmpty(), "$expectedCode reached native work: $nativeEvents")
                assertEquals(0, adapter.activePreparedNativeFramePayloadCount, expectedCode)
                assertEquals(0, concreteResources.pendingPhysicalReservationCount, expectedCode)
            } finally {
                adapter.close()
            }
        }
    }

    @Test
    fun `core prepared target bounds mismatch refuses before every preflight side effect`() {
        val events = mutableListOf<String>()
        val result = preflighter(
            resources = RecordingResourceProvider(events),
            completion = RecordingCompletionProvider(events),
            surface = RecordingSurfaceProvider(events),
        ).preflight(
            framePlan(
                listOf(
                    prepareScene(GPUPixelBounds(0, 0, 3, 4)),
                    coreRenderStep(coreSemantic()),
                ),
            ),
        )

        assertEquals(
            "invalid.preflight.core_primitive_target_authority",
            assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
        )
        assertTrue(events.isEmpty(), "pure validation side effects: $events")
    }

    @Test
    fun `core prepared target structural mismatches refuse before every preflight side effect`() {
        val cases = listOf(
            prepareScene(role = GPUFrameResourceRole.LayerTarget),
            prepareScene(usages = setOf(GPUFrameResourceUsage.RenderAttachment)),
            prepareScene(
                usages = setOf(
                    GPUFrameResourceUsage.RenderAttachment,
                    GPUFrameResourceUsage.CopySource,
                    GPUFrameResourceUsage.TextureBinding,
                ),
            ),
            prepareScene(lifetime = GPUFrameResourceLifetime.RecordingLocal),
            prepareScene(byteSize = 63L),
        )

        cases.forEach { preparation ->
            val events = mutableListOf<String>()
            val result = preflighter(
                resources = RecordingResourceProvider(events),
                completion = RecordingCompletionProvider(events),
                surface = RecordingSurfaceProvider(events),
            ).preflight(framePlan(listOf(preparation, coreRenderStep(coreSemantic()))))

            assertEquals(
                "invalid.preflight.core_primitive_target_authority",
                assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
            )
            assertTrue(events.isEmpty(), "pure validation side effects: $events")
        }
    }

    @Test
    fun `solid semantic payload refuses every non solid route before any preflight side effect`() {
        listOf(
            "linear.gradient.fill",
            "rrect.fill.coverage",
            "rect.fill.mask_blur",
            "path.fill.coverage_mask",
        ).forEach { route ->
            assertSolidRefusalBeforeSideEffects(
                expectedCode = "invalid.preflight.solid_semantic_route",
                payload = solidSemantic(renderStepIdentity = route),
                packetRenderStepIdentity = route,
            )
        }
    }

    @Test
    fun `executable solid fill rect without semantic payload refuses before operand materialization`() {
        val events = mutableListOf<String>()
        val result = preflighter(
            resources = RecordingResourceProvider(events),
            completion = RecordingCompletionProvider(events),
            surface = RecordingSurfaceProvider(events),
        ).preflight(framePlan(listOf(prepareScene(), solidRenderStep(null))))

        assertEquals(
            "invalid.preflight.solid_semantic_payload_missing",
            assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
        )
        assertFalse("operands:prepare" in events)
    }

    @Test
    fun `solid semantic command and render step mismatches refuse before operand materialization`() {
        val commandMismatch = preflightSolid(solidSemantic(commandId = 32))
        val stepMismatch = preflightSolid(solidSemantic(renderStepIdentity = "rect.fill.other"))

        assertEquals(
            "invalid.preflight.solid_semantic_command_mismatch",
            assertIs<GPUFramePreflightResult.Refused>(commandMismatch.first).diagnostic.code.value,
        )
        assertEquals(
            "invalid.preflight.solid_semantic_render_step_mismatch",
            assertIs<GPUFramePreflightResult.Refused>(stepMismatch.first).diagnostic.code.value,
        )
        assertFalse("operands:prepare" in commandMismatch.second)
        assertFalse("operands:prepare" in stepMismatch.second)
    }

    @Test
    fun `solid semantic packet slot and internal fingerprint mismatches refuse before operand materialization`() {
        val valid = solidSemantic()
        val slot = valid.payloadRef.uniformSlot!!
        val packetSlotMismatch = preflightSolid(
            payload = valid,
            packetUniformSlot = slot.copy(byteOffset = 256),
        )
        val staleSlot = slot.copy(
            fingerprint = GPUPayloadFingerprint("stale.solid.fingerprint"),
        )
        val fingerprintMismatchPayload = GPUDrawSemanticPayload.SolidRect(
            valid.payloadRef.copy(uniformSlot = staleSlot),
        )
        val fingerprintMismatch = preflightSolid(fingerprintMismatchPayload)

        assertEquals(
            "invalid.preflight.solid_semantic_packet_slot_mismatch",
            assertIs<GPUFramePreflightResult.Refused>(packetSlotMismatch.first).diagnostic.code.value,
        )
        assertEquals(
            "invalid.preflight.solid_semantic_fingerprint_mismatch",
            assertIs<GPUFramePreflightResult.Refused>(fingerprintMismatch.first).diagnostic.code.value,
        )
        assertFalse("operands:prepare" in packetSlotMismatch.second)
        assertFalse("operands:prepare" in fingerprintMismatch.second)
    }

    @Test
    fun `solid semantic malformed ranges bytes padding and values refuse before every preflight side effect`() {
        val valid = solidSemantic()
        val block = valid.payloadRef.uniformBlock!!
        fun payloadWithBlock(changed: org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadBlock) =
            GPUDrawSemanticPayload.SolidRect(valid.payloadRef.copy(uniformBlock = changed))

        val overlappingFields = payloadWithBlock(
            block.copy(fields = block.fields.mapIndexed { index, field ->
                if (index == 1) field.copy(byteOffset = 0) else field
            }),
        )
        val invalidByteCount = payloadWithBlock(block.copy(byteSize = 63))
        val nonZeroPaddingBytes = block.bytes.toMutableList().apply { this[63] = 1 }
        val invalidPadding = payloadWithBlock(block.copy(bytes = nonZeroPaddingBytes))
        val nonFiniteBytes = block.bytes.toMutableList().apply {
            set(0, 0); set(1, 0); set(2, 192); set(3, 127)
        }
        val nonFinite = payloadWithBlock(block.copy(bytes = nonFiniteBytes))
        val outOfRangeColorBytes = block.bytes.toMutableList().apply {
            set(32, 0); set(33, 0); set(34, 0); set(35, 64)
        }
        val outOfRange = payloadWithBlock(block.copy(bytes = outOfRangeColorBytes))

        val cases = listOf(
            "invalid.preflight.solid_semantic_field_ranges" to overlappingFields,
            "invalid.preflight.solid_semantic_byte_count" to invalidByteCount,
            "invalid.preflight.solid_semantic_padding" to invalidPadding,
            "invalid.preflight.solid_semantic_non_finite" to nonFinite,
            "invalid.preflight.solid_semantic_value_range" to outOfRange,
        )
        cases.forEach { (expectedCode, payload) ->
            assertSolidRefusalBeforeSideEffects(expectedCode, payload)
        }
    }

    @Test
    fun `solid semantic ABI matrix refuses before resource native registry and ticket side effects`() {
        val valid = solidSemantic()
        val slot = valid.payloadRef.uniformSlot!!
        val block = valid.payloadRef.uniformBlock!!
        fun payloadWithRef(
            changed: org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawPayloadRef,
        ) = GPUDrawSemanticPayload.SolidRect(changed)
        fun payloadWithBlock(
            changed: org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadBlock,
        ) = payloadWithRef(valid.payloadRef.copy(uniformBlock = changed))

        val forbiddenResourceLayout = payloadWithRef(
            valid.payloadRef.copy(
                resourceSlot = org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot(
                    slotId = org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID("forbidden.resource"),
                    fingerprint = slot.fingerprint,
                    bindingIndex = 0,
                ),
            ),
        )
        val outOfRangeBytes = block.bytes.toMutableList().apply { this[0] = 256 }
        val inconsistentZeroFact = block.fields.mapIndexed { index, field ->
            if (index == 0) field.copy(zeroFilled = !field.zeroFilled) else field
        }
        val negativeRadiusBytes = block.bytes.toMutableList().apply {
            this[16] = 0
            this[17] = 0
            this[18] = 128
            this[19] = 191
        }
        val negativeRadiusFields = block.fields.mapIndexed { index, field ->
            if (index == 4) field.copy(zeroFilled = false) else field
        }

        listOf(
            "invalid.preflight.solid_semantic_uniform_missing" to
                payloadWithRef(valid.payloadRef.copy(uniformSlot = null)),
            "invalid.preflight.solid_semantic_uniform_missing" to
                payloadWithRef(valid.payloadRef.copy(uniformBlock = null)),
            "invalid.preflight.solid_semantic_layout" to forbiddenResourceLayout,
            "invalid.preflight.solid_semantic_layout" to
                payloadWithBlock(block.copy(packingPlanHash = "stale.solid.layout")),
            "invalid.preflight.solid_semantic_layout" to
                payloadWithRef(valid.payloadRef.copy(uniformSlot = slot.copy(byteOffset = 256))),
            "invalid.preflight.solid_semantic_byte_count" to
                payloadWithBlock(block.copy(bytes = outOfRangeBytes)),
            "invalid.preflight.solid_semantic_field_metadata" to
                payloadWithBlock(block.copy(fields = inconsistentZeroFact)),
            "invalid.preflight.solid_semantic_value_range" to
                payloadWithBlock(block.copy(bytes = negativeRadiusBytes, fields = negativeRadiusFields)),
        ).forEach { (expectedCode, payload) ->
            assertSolidRefusalBeforeSideEffects(expectedCode, payload)
        }
    }

    @Test
    fun `preflight preserves the exact solid semantic payload in the typed render operand`() {
        val events = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val resources = GPUConcreteResourceProvider(leaseFactory = adapter)
        val semantic = solidSemantic()
        val plan = framePlan(listOf(prepareScene(), solidRenderStep(semantic)))
        val materializer = RenderOnlyNativePayloadMaterializer(events)

        val prepared = assertIs<GPUFramePreflightResult.Prepared>(
            preflighter(
                resources = resources,
                completion = RecordingCompletionProvider(events),
                surface = RecordingSurfaceProvider(events),
                nativeBoundary = adapter.bindNativeFrameBoundary(resources, materializer),
            ).preflight(plan),
        ).frame

        assertSame(semantic, plan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single().drawPackets.single().semanticPayload)
        assertSame(semantic, prepared.semanticPlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single().drawPackets.single().semanticPayload)
        assertSame(semantic, requireNotNull(materializer.lastRenderOperand).semanticPayloads.single())
        assertEquals(1, adapter.activePreparedNativeFramePayloadCount)
    }

    @Test
    fun `native render operand omission of solid semantic payload refuses before registry and ticket`() {
        val events = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val resources = GPUConcreteResourceProvider(leaseFactory = adapter)
        val ownedHandle = CountingDraftHandle()
        val materializer = RenderOnlyNativePayloadMaterializer(
            events,
            omitSemanticPayloads = true,
            ownedHandle = ownedHandle,
        )

        val result = preflighter(
            resources = resources,
            completion = RecordingCompletionProvider(events),
            surface = RecordingSurfaceProvider(events),
            nativeBoundary = adapter.bindNativeFrameBoundary(resources, materializer),
        ).preflight(framePlan(listOf(prepareScene(), solidRenderStep(solidSemantic()))))

        assertEquals(
            "invalid.preflight.native_render_semantic_payloads",
            assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
        )
        assertEquals(0, adapter.activePreparedNativeFramePayloadCount)
        assertFalse("ticket:reserve" in events)
        assertEquals(1, ownedHandle.closeAttempts)
        assertEquals(1, ownedHandle.successfulCloses)
        adapter.close()
        assertEquals(1, ownedHandle.closeAttempts)
    }

    @Test
    fun `native boundary identity mismatch disposes transferred draft before refusal`() {
        val events = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val resources = GPUConcreteResourceProvider(leaseFactory = adapter)
        val ownedHandle = CountingDraftHandle()
        val materializer = RenderOnlyNativePayloadMaterializer(
            events,
            encoderPlanIdSuffix = ".stale",
            ownedHandle = ownedHandle,
        )

        val result = preflighter(
            resources = resources,
            completion = RecordingCompletionProvider(events),
            surface = RecordingSurfaceProvider(events),
            nativeBoundary = adapter.bindNativeFrameBoundary(resources, materializer),
        ).preflight(framePlan(listOf(prepareScene(), renderStep())))

        assertEquals(
            "stale.native-frame-payload.identity-mismatch",
            assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
        )
        assertEquals(0, adapter.activePreparedNativeFramePayloadCount)
        assertEquals(1, ownedHandle.closeAttempts)
        assertEquals(1, ownedHandle.successfulCloses)
        adapter.close()
        assertEquals(1, ownedHandle.closeAttempts)
    }

    @Test
    fun `native identity refusal preserves unique cleanup when one draft identity conflicts`() {
        val events = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val resources = GPUConcreteResourceProvider(leaseFactory = adapter)
        val conflict = CountingDraftHandle()
        val unique = CountingDraftHandle(closeFailuresRemaining = 1)
        assertTrue(adapter.quarantinePreparedNativeFrameDraft(ownedDraft(70, conflict)))
        val materializer = RenderOnlyNativePayloadMaterializer(
            events,
            encoderPlanIdSuffix = ".stale",
            ownedHandle = conflict,
            additionalOwnedHandles = listOf(unique),
        )

        val result = preflighter(
            resources = resources,
            completion = RecordingCompletionProvider(events),
            surface = RecordingSurfaceProvider(events),
            nativeBoundary = adapter.bindNativeFrameBoundary(resources, materializer),
        ).preflight(framePlan(listOf(prepareScene(), renderStep())))

        assertEquals(
            "stale.native-frame-payload.identity-mismatch",
            assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
        )
        assertEquals(0, conflict.closeAttempts)
        assertEquals(1, unique.closeAttempts)
        assertEquals(2, adapter.quarantinedPreparedNativeFramePayloadCount)
        adapter.close()
        assertEquals(1, conflict.successfulCloses)
        assertEquals(2, unique.closeAttempts)
        assertEquals(1, unique.successfulCloses)
    }

    @Test
    fun `native semantic refusal preserves unique cleanup when one draft identity conflicts`() {
        val events = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val resources = GPUConcreteResourceProvider(leaseFactory = adapter)
        val conflict = CountingDraftHandle()
        val unique = CountingDraftHandle(closeFailuresRemaining = 1)
        assertTrue(adapter.quarantinePreparedNativeFrameDraft(ownedDraft(71, conflict)))
        val materializer = RenderOnlyNativePayloadMaterializer(
            events,
            omitSemanticPayloads = true,
            ownedHandle = conflict,
            additionalOwnedHandles = listOf(unique),
        )

        val result = preflighter(
            resources = resources,
            completion = RecordingCompletionProvider(events),
            surface = RecordingSurfaceProvider(events),
            nativeBoundary = adapter.bindNativeFrameBoundary(resources, materializer),
        ).preflight(framePlan(listOf(prepareScene(), solidRenderStep(solidSemantic()))))

        assertEquals(
            "invalid.preflight.native_render_semantic_payloads",
            assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
        )
        assertEquals(0, conflict.closeAttempts)
        assertEquals(1, unique.closeAttempts)
        assertEquals(2, adapter.quarantinedPreparedNativeFramePayloadCount)
        adapter.close()
        assertEquals(1, conflict.successfulCloses)
        assertEquals(2, unique.closeAttempts)
        assertEquals(1, unique.successfulCloses)
    }

    @Test
    fun `native render payload refuses draw without pipeline and ambiguous clear state`() {
        val generation = GPUDeviceGenerationID(11)
        val target = GPUPreparedNativeTextureViewOperand(fakeNative<GPUTextureView>("target"), generation)

        assertFailsWith<IllegalArgumentException> {
            GPUPreparedNativeScopeOperand.Render(
                sourceStepIndex = 1,
                pass = GPUPreparedNativeRenderPassConfig(target),
                commands = listOf(
                    GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(6)),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedNativeRenderPassConfig(
                colorTarget = target,
                loadOperation = GPUPreparedNativeLoadOperation.Clear,
                clearColor = null,
            )
        }
    }

    @Test
    fun `preflight registers one native payload only for registry backed resources`() {
        val events = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val resources = GPUConcreteResourceProvider(leaseFactory = adapter)
        val plan = framePlan(listOf(prepareScene(), renderStep()))
        val materializer = RenderOnlyNativePayloadMaterializer(events)
        val boundary = adapter.bindNativeFrameBoundary(resources, materializer)

        val prepared = assertIs<GPUFramePreflightResult.Prepared>(
            preflighter(
                resources = resources,
                completion = RecordingCompletionProvider(events),
                surface = RecordingSurfaceProvider(events),
                nativeBoundary = boundary,
            ).preflight(plan),
        ).frame

        assertTrue(prepared.rollback.hasNativePayload)
        assertEquals(1, adapter.activePreparedNativeFramePayloadCount)
        assertTrue(events.indexOf("native-payload:materialize") < events.indexOf("ticket:reserve"))
    }

    @Test
    fun `evidence only resources cannot construct native payload boundary`() {
        val events = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val resources = GPUConcreteResourceProvider()

        assertFailsWith<IllegalArgumentException> {
            adapter.bindNativeFrameBoundary(resources, RenderOnlyNativePayloadMaterializer(events))
        }
        assertEquals(0, adapter.activePreparedNativeFramePayloadCount)
    }

    @Test
    fun `native boundary refuses a different resource provider even with the same adapter`() {
        val events = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val boundaryProvider = GPUConcreteResourceProvider(leaseFactory = adapter)
        val otherProvider = GPUConcreteResourceProvider(leaseFactory = adapter)
        val boundary = adapter.bindNativeFrameBoundary(
            boundaryProvider,
            RenderOnlyNativePayloadMaterializer(events),
        )

        val result = preflighter(
            resources = otherProvider,
            completion = RecordingCompletionProvider(events),
            surface = RecordingSurfaceProvider(events),
            nativeBoundary = boundary,
        ).preflight(framePlan(listOf(prepareScene(), renderStep())))

        assertEquals(
            "invalid.preflight.native_payload_provider_mismatch",
            assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
        )
        assertTrue(events.isEmpty())
    }

    @Test
    fun `late surface bind refusal rolls back surface ticket native payload then resources`() {
        val events = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val resources = GPUConcreteResourceProvider(leaseFactory = adapter)
        val boundary = adapter.bindNativeFrameBoundary(
            resources,
            RefusingSurfaceNativePayloadMaterializer(events),
        )

        val result = preflighter(
            resources = resources,
            completion = RecordingCompletionProvider(events),
            surface = RecordingSurfaceProvider(events),
            nativeBoundary = boundary,
        ).preflight(framePlan(listOf(acquire(), surfaceBlit(), present())))

        val refused = assertIs<GPUFramePreflightResult.Refused>(result)
        assertEquals("unsupported.native-frame-payload.surface-bind-refused", refused.diagnostic.code.value)
        assertEquals(
            listOf("surface", "completion-ticket", "native-payload", "resources"),
            requireNotNull(refused.rollbackResult).releaseOrder.map { it.substringBefore(':') },
        )
        assertEquals(0, adapter.activePreparedNativeFramePayloadCount)
        assertTrue(events.indexOf("native-payload:materialize") < events.indexOf("ticket:reserve"))
        assertTrue(events.indexOf("ticket:reserve") < events.indexOf("surface:acquire:surface.main"))
        assertTrue(events.indexOf("surface:acquire:surface.main") < events.indexOf("native-payload:bind"))
    }

    @Test
    fun `mixed frame is prepared transactionally and encoder scopes cover encodable steps once`() {
        val events = mutableListOf<String>()
        val resources = RecordingResourceProvider(events)
        val completion = RecordingCompletionProvider(events)
        val surface = RecordingSurfaceProvider(events)
        val plan = mixedPlan()

        val prepared = assertIs<GPUFramePreflightResult.Prepared>(
            preflighter(resources, completion, surface).preflight(plan),
        ).frame

        assertEquals(
            listOf(
                "resource:prepare:scene",
                "resource:prepare:upload",
                "resource:prepare:copy",
                "resource:prepare:readback",
                "operands:prepare",
                "ticket:reserve",
                "surface:acquire:surface.main",
            ),
            events,
        )
        assertEquals(0, resources.encoderCreateCount)
        assertEquals(0, resources.submitCount)
        assertEquals(
            listOf(1, 2, 3, 5, 6, 8),
            prepared.encoderPlan.scopes.map { scope -> scope.sourceStepIndex },
        )
        assertEquals(
            listOf(
                GPUEncoderOperationKind.Render,
                GPUEncoderOperationKind.Upload,
                GPUEncoderOperationKind.Copy,
                GPUEncoderOperationKind.Compute,
                GPUEncoderOperationKind.Readback,
                GPUEncoderOperationKind.SurfaceBlit,
            ),
            prepared.encoderPlan.scopes.map { scope -> scope.operationKind },
        )
        assertEquals(plan.steps.indices.toList(), prepared.stepPartition.map { evidence -> evidence.sourceStepIndex })
        assertEquals(listOf(7, 9), prepared.hostActions.map { action -> action.sourceStepIndex })
        assertEquals(listOf(4), prepared.dependencyEvidence.map { evidence -> evidence.sourceStepIndex })
        assertEquals(listOf("beginRenderPass", "setRenderPipeline", "setBindGroup", "draw", "endRenderPass"),
            prepared.encoderPlan.scopes.first().facadeOperationClasses)
    }

    @Test
    fun `two render batches keep one begin and one end around ordered packet commands`() {
        val events = mutableListOf<String>()
        val first = packet("packet.a", 1, passId = "pass.source.a")
        val second = packet("packet.b", 2, passId = "pass.source.b")
        val render = GPUFrameStep.RenderPassStep(
            target = GPUFrameTargetRef("target.scene"),
            loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store, "transparent"),
            samplePlan = org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan.SingleSampleFrame,
            drawPackets = listOf(first, second),
            sourceTaskIds = listOf(GPUTaskID("task.render.a"), GPUTaskID("task.render.b")),
            batches = listOf(
                batch("batch.a", first, "task.render.a"),
                batch("batch.b", second, "task.render.b"),
            ),
        )
        val plan = framePlan(listOf(prepareScene(), render))

        val scope = assertIs<GPUFramePreflightResult.Prepared>(
            preflighter(
                RecordingResourceProvider(events),
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
            ).preflight(plan),
        ).frame.encoderPlan.scopes.single()

        assertEquals(1, scope.facadeOperationClasses.count { it == "beginRenderPass" })
        assertEquals(1, scope.facadeOperationClasses.count { it == "endRenderPass" })
        assertEquals(2, scope.facadeOperationClasses.count { it == "draw" })
        assertEquals(listOf("packet.a", "packet.b"), scope.sourcePacketIds.map { it.value })
    }

    @Test
    fun `copy as draw destination consumer with MSAA refuses exactness before side effects`() {
        val events = mutableListOf<String>()
        val scene = GPUFrameTargetRef("target.scene")
        val source = GPUFrameTextureRef("texture.copy-as-draw.source")
        val snapshot = GPUFrameTextureRef("texture.copy-as-draw.snapshot")
        val packet = packet("packet.msaa.copy-as-draw", 41)
        val taskId = GPUTaskID("task.render.msaa.copy-as-draw")
        val samplePlan = GPUSamplePlan.MultisampleFrame(4)
        val key = msaaContinuationKey(samplePlan)
        val sourceKey = GPUDestinationSnapshotGroupKey(
            target = GPUTargetIdentity("target.scene"),
            targetGeneration = 1,
            deviceGeneration = GPUDeviceGenerationID(7),
            format = GPUColorFormat("rgba8unorm"),
            colorInterpretation = GPUColorInterpretation("srgb-premul"),
            sampleContinuation = key,
            sourceIntermediate = GPUIntermediateIdentity("intermediate.copy-as-draw"),
        )
        val consumer = GPUDestinationSnapshotConsumerRef(
            groupingCommandId = "draw.41",
            renderTaskId = taskId,
            packetId = packet.packetId,
            commandId = GPUDrawCommandID(41),
        )
        val capabilities = capabilities()
        val sealHash = GPUFrameCapabilitySeal.capture(
            GPUFrameID(7),
            GPUDeviceGenerationID(7),
            capabilities,
        ).sealHash
        val render = GPUFrameStep.RenderPassStep(
            target = scene,
            loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store),
            samplePlan = samplePlan,
            drawPackets = listOf(packet),
            sourceTaskIds = listOf(taskId),
            batches = listOf(batch("batch.msaa.copy-as-draw", packet, taskId.value)),
            sampleContinuation = GPUSampleContinuationRequest(
                key,
                GPUSampleLoadTransition.FreshClear,
                GPUSampleStoreAction.Store,
                GPUSampleResolveAction.ResolveCanonical,
            ),
        )
        val result = preflighter(
            RecordingResourceProvider(events),
            RecordingCompletionProvider(events),
            RecordingSurfaceProvider(events),
            context = GPUFramePreflightContext(
                targetId = scene.value,
                deviceGeneration = GPUDeviceGenerationID(7),
                targetGeneration = 1,
                resourceGenerations = listOf<GPUFrameResourceRef>(scene, source, snapshot).associateWith { 1L },
            ),
        ).preflight(
            framePlan(
                listOf(
                    GPUFrameStep.PrepareResourcesStep(
                        requests = listOf(
                            GPUResourcePreparationRequest(
                                scene,
                                GPUFrameTextureDescriptor(GPUPixelBounds(0, 0, 4, 4), GPUColorFormat("rgba8unorm"), 1),
                                GPUFrameResourceRole.SceneTarget,
                                setOf(GPUFrameResourceUsage.RenderAttachment),
                                GPUFrameResourceLifetime.FrameLocal,
                                64,
                                "scene.msaa",
                            ),
                            GPUResourcePreparationRequest(
                                source,
                                GPUFrameTextureDescriptor(GPUPixelBounds(0, 0, 4, 4), GPUColorFormat("rgba8unorm"), 1),
                                GPUFrameResourceRole.DestinationSnapshot,
                                setOf(GPUFrameResourceUsage.TextureBinding),
                                GPUFrameResourceLifetime.FrameLocal,
                                64,
                                "copy-as-draw.source",
                            ),
                            GPUResourcePreparationRequest(
                                snapshot,
                                GPUFrameTextureDescriptor(GPUPixelBounds(0, 0, 4, 4), GPUColorFormat("rgba8unorm"), 1),
                                GPUFrameResourceRole.DestinationSnapshot,
                                setOf(
                                    GPUFrameResourceUsage.RenderAttachment,
                                    GPUFrameResourceUsage.TextureBinding,
                                ),
                                GPUFrameResourceLifetime.FrameLocal,
                                64,
                                "copy-as-draw.snapshot",
                            ),
                        ),
                        sourceTaskIds = listOf(GPUTaskID("task.prepare.copy-as-draw")),
                    ),
                    GPUFrameStep.CopyAsDrawMaterializationStep(
                        source = source,
                        sourceKey = sourceKey,
                        sourceIntermediate = requireNotNull(sourceKey.sourceIntermediate),
                        snapshot = snapshot,
                        logicalBounds = GPUPixelBounds(0, 0, 4, 4),
                        capabilitySealHash = sealHash,
                        consumers = listOf(consumer),
                        sourceTaskIds = listOf(GPUTaskID("task.copy-as-draw")),
                    ),
                    render,
                ),
                capabilities,
            ),
        )

        assertEquals(
            "unsupported.blend.msaa_destination_read_exactness",
            assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
        )
        assertTrue(events.isEmpty())
    }

    @Test
    fun `MSAA continuation rejects indirect compute writes and contradictory store plans`() {
        val samplePlan = GPUSamplePlan.MultisampleFrame(4)
        val key = msaaContinuationKey(samplePlan)
        fun render(
            id: String,
            load: String,
            storePlan: GPUStorePlan = GPUStorePlan.Store,
        ): GPUFrameStep.RenderPassStep {
            val packet = packet("packet.$id", if (load == "clear") 51 else 52)
            val taskId = GPUTaskID("task.$id")
            return GPUFrameStep.RenderPassStep(
                target = GPUFrameTargetRef("target.scene"),
                loadStore = GPULoadStorePlan(load, storePlan),
                samplePlan = samplePlan,
                drawPackets = listOf(packet),
                sourceTaskIds = listOf(taskId),
                batches = listOf(batch("batch.$id", packet, taskId.value)),
                sampleContinuation = GPUSampleContinuationRequest(
                    key = key,
                    loadTransition = if (load == "clear") {
                        GPUSampleLoadTransition.FreshClear
                    } else {
                        GPUSampleLoadTransition.RetainedLoad
                    },
                    storeAction = GPUSampleStoreAction.Store,
                    resolveAction = GPUSampleResolveAction.ResolveCanonical,
                ),
            )
        }
        val first = render("render.msaa.first", "clear")
        val second = render("render.msaa.second", "load")
        val scene = GPUFrameTargetRef("target.scene")
        val computeTarget = GPUFrameTargetRef("target.compute.other")
        val prepare = GPUFrameStep.PrepareResourcesStep(
            requests = listOf(
                GPUResourcePreparationRequest(
                    scene,
                    GPUFrameTextureDescriptor(GPUPixelBounds(0, 0, 4, 4), GPUColorFormat("rgba8unorm"), 1),
                    GPUFrameResourceRole.SceneTarget,
                    setOf(
                        GPUFrameResourceUsage.RenderAttachment,
                        GPUFrameResourceUsage.StorageBinding,
                    ),
                    GPUFrameResourceLifetime.FrameLocal,
                    64,
                    "scene.msaa-compute",
                ),
                GPUResourcePreparationRequest(
                    computeTarget,
                    GPUFrameTextureDescriptor(GPUPixelBounds(0, 0, 4, 4), GPUColorFormat("rgba8unorm"), 1),
                    GPUFrameResourceRole.FilterTarget,
                    setOf(GPUFrameResourceUsage.StorageBinding),
                    GPUFrameResourceLifetime.FrameLocal,
                    64,
                    "compute.other",
                ),
            ),
            sourceTaskIds = listOf(GPUTaskID("task.prepare.msaa-compute")),
        )
        val indirectWriter = GPUFrameStep.ComputePassStep(
            target = computeTarget,
            resourceUses = listOf(
                org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
                    resource = GPUFrameTargetRef("target.scene"),
                    role = GPUFrameResourceRole.SceneTarget,
                    usage = GPUFrameResourceUsage.StorageBinding,
                    lifetime = GPUFrameResourceLifetime.FrameLocal,
                    write = true,
                ),
            ),
            dispatches = listOf(GPUComputeDispatch(GPUComputePipelineKey("compute.writer"), 1, 1, 1)),
            sourceTaskIds = listOf(GPUTaskID("task.compute.writer")),
        )
        val scenarios = listOf(
            listOf(prepare, first, indirectWriter, second) to
                "unsupported.msaa.continuation_canonical_write",
            listOf(prepare, render("render.msaa.discard", "clear", GPUStorePlan.Discard)) to
                "unsupported.msaa.continuation_store_operation",
        )
        scenarios.forEach { (steps, expectedCode) ->
            val events = mutableListOf<String>()
            val result = preflighter(
                RecordingResourceProvider(events),
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
                context = GPUFramePreflightContext(
                    targetId = scene.value,
                    deviceGeneration = GPUDeviceGenerationID(7),
                    targetGeneration = 1,
                    resourceGenerations = mapOf(scene to 1L, computeTarget to 1L),
                ),
            ).preflight(framePlan(steps))

            assertEquals(expectedCode, assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value)
            assertTrue(events.isEmpty())
        }
    }

    @Test
    fun `destination copy copy as draw transition and refusal steps keep their exact lanes`() {
        val capabilities = capabilities()
        val capabilitySealHash = GPUFrameCapabilitySeal.capture(
            GPUFrameID(7),
            GPUDeviceGenerationID(7),
            capabilities,
        ).sealHash
        fun snapshotKey(intermediate: GPUIntermediateIdentity?): GPUDestinationSnapshotGroupKey =
            GPUDestinationSnapshotGroupKey(
                target = GPUTargetIdentity("target.scene"),
                targetGeneration = 1,
                deviceGeneration = GPUDeviceGenerationID(7),
                format = GPUColorFormat("rgba8unorm"),
                colorInterpretation = GPUColorInterpretation("srgb-premul"),
                sampleContinuation = GPUSampleContinuationKey(
                    target = GPUTargetIdentity("target.scene"),
                    targetGeneration = 1,
                    deviceGeneration = GPUDeviceGenerationID(7),
                    colorFormat = GPUColorFormat("rgba8unorm"),
                    colorInterpretation = GPUColorInterpretation("srgb-premul"),
                    samplePlan = org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan.MultisampleFrame(4),
                    colorAttachment = GPUTargetIdentity("target.scene.msaa"),
                    depthStencilAttachment = null,
                ),
                sourceIntermediate = intermediate,
            )
        val consumer = GPUDestinationSnapshotConsumerRef(
            groupingCommandId = "draw.11",
            renderTaskId = GPUTaskID("task.render.11"),
            packetId = GPUDrawPacketID("packet.11"),
            commandId = GPUDrawCommandID(11),
        )
        val scene = GPUFrameTargetRef("target.scene")
        val copySnapshot = GPUFrameTextureRef("texture.copy.snapshot")
        val drawSource = GPUFrameTextureRef("texture.draw.source")
        val drawSnapshot = GPUFrameTextureRef("texture.draw.snapshot")
        val child = GPUFrameTargetRef("target.child")
        val preparedRefs = listOf<GPUFrameResourceRef>(scene, copySnapshot, drawSource, drawSnapshot, child)
        val preparations = GPUFrameStep.PrepareResourcesStep(
            requests = preparedRefs.map { resource ->
                GPUResourcePreparationRequest(
                    resource = resource,
                    descriptor = GPUFrameTextureDescriptor(
                        GPUPixelBounds(0, 0, 4, 4),
                        GPUColorFormat("rgba8unorm"),
                        1,
                    ),
                    role = when (resource) {
                        scene -> GPUFrameResourceRole.SceneTarget
                        child -> GPUFrameResourceRole.LayerTarget
                        else -> GPUFrameResourceRole.DestinationSnapshot
                    },
                    usages = setOf(
                        GPUFrameResourceUsage.RenderAttachment,
                        GPUFrameResourceUsage.CopySource,
                        GPUFrameResourceUsage.CopyDestination,
                        GPUFrameResourceUsage.TextureBinding,
                    ),
                    lifetime = GPUFrameResourceLifetime.FrameLocal,
                    byteSize = 64,
                    diagnosticLabel = resource.value,
                )
            },
            sourceTaskIds = listOf(GPUTaskID("task.prepare")),
        )
        val plan = framePlan(
            listOf(
                preparations,
                GPUFrameStep.CopyDestinationStep(
                    source = scene,
                    sourceKey = snapshotKey(null),
                    snapshot = copySnapshot,
                    logicalBounds = GPUPixelBounds(0, 0, 4, 4),
                    copyLayout = GPUTextureCopyLayout(256, 4),
                    consumers = listOf(consumer),
                    sourceTaskIds = listOf(GPUTaskID("task.copy.destination")),
                ),
                GPUFrameStep.CopyAsDrawMaterializationStep(
                    source = drawSource,
                    sourceKey = snapshotKey(GPUIntermediateIdentity("intermediate.source")),
                    sourceIntermediate = GPUIntermediateIdentity("intermediate.source"),
                    snapshot = drawSnapshot,
                    logicalBounds = GPUPixelBounds(0, 0, 4, 4),
                    capabilitySealHash = capabilitySealHash,
                    consumers = listOf(consumer),
                    sourceTaskIds = listOf(GPUTaskID("task.copy.draw")),
                ),
                GPUFrameStep.TargetTransitionStep(
                    parent = scene,
                    child = child,
                    transitionKind = GPUTargetTransitionKind.EnterChild,
                    sourceTaskIds = listOf(GPUTaskID("task.transition")),
                ),
                GPUFrameStep.RefusedLeafDrawStep(
                    commandId = GPUDrawCommandID(12),
                    diagnostic = preflightDiagnostic("unsupported.test.leaf", "leaf refused"),
                    sourceTaskIds = listOf(GPUTaskID("task.refusal.leaf")),
                ),
                GPUFrameStep.RefusedCompositeCommandStep(
                    commandId = GPUDrawCommandID(13),
                    provenanceTokens = listOf(GPUCompositeProvenanceToken("provenance.13")),
                    diagnostic = preflightDiagnostic("unsupported.test.composite", "composite refused"),
                    sourceTaskIds = listOf(GPUTaskID("task.refusal.composite")),
                ),
            ),
            capabilities,
        )
        val events = mutableListOf<String>()
        val prepared = assertIs<GPUFramePreflightResult.Prepared>(
            GPUFramePreflighter(
                context = GPUFramePreflightContext(
                    targetId = "target.scene",
                    deviceGeneration = GPUDeviceGenerationID(7),
                    targetGeneration = 1,
                    resourceGenerations = preparedRefs.associateWith { 1L },
                ),
                capabilities = capabilities,
                resourceProvider = RecordingResourceProvider(events),
                completionProvider = RecordingCompletionProvider(events),
                surfaceProvider = RecordingSurfaceProvider(events),
            ).preflight(plan),
        ).frame

        assertEquals(listOf(1, 2), prepared.encoderPlan.scopes.map { it.sourceStepIndex })
        assertEquals(
            listOf(GPUEncoderOperationKind.CopyDestination, GPUEncoderOperationKind.CopyAsDraw),
            prepared.encoderPlan.scopes.map { it.operationKind },
        )
        assertEquals(listOf(3), prepared.dependencyEvidence.map { it.sourceStepIndex })
        assertEquals(
            listOf(GPUPreparedStepLane.RefusalEvidence, GPUPreparedStepLane.RefusalEvidence),
            prepared.stepPartition.drop(4).map { it.lane },
        )
        assertEquals(listOf("copyTextureToTexture"), prepared.encoderPlan.scopes[0].facadeOperationClasses)
        assertEquals(
            listOf("beginRenderPass", "copyAsDraw", "endRenderPass"),
            prepared.encoderPlan.scopes[1].facadeOperationClasses,
        )
    }

    @Test
    fun `pipeline or bind group refusal rolls resources back in reverse order without surface or encoder`() {
        for (failure in listOf("pipeline", "bind-group")) {
            val events = mutableListOf<String>()
            val resources = RecordingResourceProvider(events, commandFailure = failure)
            val result = preflighter(
                resources,
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
            ).preflight(framePlan(listOf(prepareScene(), renderStep())))

            val refused = assertIs<GPUFramePreflightResult.Refused>(result)
            assertEquals("unsupported.preflight.$failure", refused.diagnostic.code.value)
            assertEquals("resources:rollback", events.last())
            assertFalse(events.any { it.startsWith("surface:") })
            assertEquals(0, resources.encoderCreateCount)
            assertEquals(0, resources.submitCount)
        }
    }

    @Test
    fun `resource scratch budget and provider exceptions refuse transactionally`() {
        val refusals = listOf(
            "scene" to "unsupported.preflight.resource_refused",
            "copy" to "unsupported.physical_pool.scratch_budget_exceeded",
        )
        refusals.forEach { (label, code) ->
            val events = mutableListOf<String>()
            val result = preflighter(
                RecordingResourceProvider(events, resourceFailureLabel = label, resourceFailureCode = code),
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
            ).preflight(if (label == "copy") mixedPlan() else framePlan(listOf(prepareScene(), renderStep())))

            assertEquals(code, assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value)
            assertEquals("resources:rollback", events.last())
            assertFalse(events.any { it.startsWith("ticket:") || it.startsWith("surface:") })
        }

        val throwingCases = listOf("resource", "operands", "ticket", "surface")
        throwingCases.forEach { lane ->
            val events = mutableListOf<String>()
            val resources = RecordingResourceProvider(
                events,
                throwOnResource = lane == "resource",
                throwOnOperands = lane == "operands",
            )
            val completion = RecordingCompletionProvider(events, if (lane == "ticket") "throw" else "reserved")
            val surface = RecordingSurfaceProvider(events, throwOnAcquire = lane == "surface")
            val plan = if (lane == "surface") {
                framePlan(listOf(prepareScene(), renderStep(), acquire(), surfaceBlit(), present()))
            } else {
                framePlan(listOf(prepareScene(), renderStep()))
            }

            val refused = assertIs<GPUFramePreflightResult.Refused>(
                preflighter(resources, completion, surface).preflight(plan),
            )
            assertTrue(refused.diagnostic.code.value.startsWith("failed.preflight"))
            assertEquals("resources:rollback", events.last())
        }
    }

    @Test
    fun `submission complete leases and non terminal diagnostics stay in prepared ownership evidence`() {
        val events = mutableListOf<String>()
        val prepared = assertIs<GPUFramePreflightResult.Prepared>(
            preflighter(
                RecordingResourceProvider(events, retainCommandEvidence = true),
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
            ).preflight(framePlan(listOf(prepareScene(), renderStep()))),
        ).frame

        assertEquals(listOf("lease.render"), prepared.resources.commandResourceLeases.map { it.leaseId })
        assertEquals(listOf("diagnostic.preflight.cache_reuse"), prepared.resources.commandDiagnostics.map { it.code })
        assertTrue(prepared.dumpLines().any { "prepared-command-lease id=lease.render" in it })
        assertTrue(prepared.dumpLines().any { "prepared-command-diagnostic code=diagnostic.preflight.cache_reuse" in it })
        assertEquals(
            listOf("setRenderPipeline.packet.main", "setBindGroup.packet.main"),
            prepared.encoderPlan.scopes.single().passCommandStream!!.materializedOperandLabels,
        )
    }

    @Test
    fun `shared provider isolates same frame attempts and unsafe identities never reach prepared dumps`() {
        val events = mutableListOf<String>()
        val provider = RecordingResourceProvider(events)
        fun prepareOnce(): PreparedGPUFrame = assertIs<GPUFramePreflightResult.Prepared>(
            preflighter(
                provider,
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
            ).preflight(mixedPlan()),
        ).frame

        val first = prepareOnce()
        val second = prepareOnce()
        assertNotEquals(
            first.resources.outputOwnedReadbacks.single().stagingLease.ownerScope,
            second.resources.outputOwnedReadbacks.single().stagingLease.ownerScope,
        )

        val unsafe = "w" + "gpuTextureHandle@123"
        val unsafeTarget = GPUFrameTargetRef(unsafe)
        val unsafePlan = framePlan(
            listOf(
                GPUFrameStep.PrepareResourcesStep(
                    requests = listOf(
                        GPUResourcePreparationRequest(
                            resource = unsafeTarget,
                            descriptor = GPUFrameTextureDescriptor(
                                GPUPixelBounds(0, 0, 4, 4),
                                GPUColorFormat("rgba8unorm"),
                                1,
                            ),
                            role = GPUFrameResourceRole.SceneTarget,
                            usages = setOf(GPUFrameResourceUsage.RenderAttachment),
                            lifetime = GPUFrameResourceLifetime.FrameLocal,
                            byteSize = 64,
                            diagnosticLabel = "unsafe",
                        ),
                    ),
                    sourceTaskIds = listOf(GPUTaskID("task.prepare")),
                ),
            ),
        )
        val unsafeEvents = mutableListOf<String>()
        val refused = assertIs<GPUFramePreflightResult.Refused>(
            GPUFramePreflighter(
                context = GPUFramePreflightContext(
                    targetId = "target.scene",
                    deviceGeneration = GPUDeviceGenerationID(7),
                    targetGeneration = 1,
                    resourceGenerations = mapOf(unsafeTarget to 1L),
                ),
                capabilities = capabilities(),
                resourceProvider = RecordingResourceProvider(unsafeEvents),
                completionProvider = RecordingCompletionProvider(unsafeEvents),
                surfaceProvider = RecordingSurfaceProvider(unsafeEvents),
            ).preflight(unsafePlan),
        )
        assertEquals("invalid.preflight.dump_unsafe_identity", refused.diagnostic.code.value)
        assertFalse(refused.diagnostic.message.contains(unsafe))
        assertFalse(refused.diagnostic.facts.values.any { unsafe in it })
        assertTrue(unsafeEvents.isEmpty())
        assertFailsWith<IllegalArgumentException> { GPUQueueCompletionTicketID(unsafe) }
        assertFailsWith<IllegalArgumentException> {
            GPUAcquiredSurfaceOutput(GPUSurfaceOutputRef("surface"), GPUDeviceGenerationID(7), 1, unsafe)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedConcreteResourceRef.Texture(GPUTextureResourceRef(unsafe))
        }

        val unsafePacket = packet("packet.unsafe", 9, pipelineKey = unsafe)
        val unsafePipelinePlan = framePlan(
            listOf(
                prepareScene(),
                GPUFrameStep.RenderPassStep(
                    target = GPUFrameTargetRef("target.scene"),
                    loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
                    samplePlan = org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan.SingleSampleFrame,
                    drawPackets = listOf(unsafePacket),
                    sourceTaskIds = listOf(GPUTaskID("task.render")),
                    batches = listOf(batch("batch.unsafe", unsafePacket, "task.render")),
                ),
            ),
        )
        val unsafePipelineEvents = mutableListOf<String>()
        val unsafePipelineResult = preflighter(
            RecordingResourceProvider(unsafePipelineEvents),
            RecordingCompletionProvider(unsafePipelineEvents),
            RecordingSurfaceProvider(unsafePipelineEvents),
        ).preflight(unsafePipelinePlan)
        assertEquals(
            "invalid.preflight.dump_unsafe_identity",
            assertIs<GPUFramePreflightResult.Refused>(unsafePipelineResult).diagnostic.code.value,
        )
        assertTrue(unsafePipelineEvents.isEmpty())
    }

    @Test
    fun `missing failed duplicate or mismatched completion proof rolls back before surface acquisition`() {
        val modes = listOf("missing", "failed", "duplicate", "wrong-frame", "wrong-generation")
        for (mode in modes) {
            val events = mutableListOf<String>()
            val resources = RecordingResourceProvider(events)
            val result = preflighter(
                resources,
                RecordingCompletionProvider(events, mode),
                RecordingSurfaceProvider(events),
            ).preflight(framePlan(listOf(prepareScene(), renderStep(), acquire(), surfaceBlit(), present())))

            val refused = assertIs<GPUFramePreflightResult.Refused>(result)
            assertTrue(refused.diagnostic.code.value.startsWith("unsupported.preflight.completion_ticket"))
            assertEquals("resources:rollback", events.last())
            assertFalse(events.any { it.startsWith("surface:") })
        }
    }

    @Test
    fun `zero resource plan abandons mismatched reserved ticket without accumulation`() {
        val events = mutableListOf<String>()
        val result = preflighter(
            RecordingResourceProvider(events),
            RecordingCompletionProvider(events, "wrong-frame"),
            RecordingSurfaceProvider(events),
        ).preflight(framePlan(emptyList()))

        val refused = assertIs<GPUFramePreflightResult.Refused>(result)
        assertEquals("unsupported.preflight.completion_ticket_mismatch", refused.diagnostic.code.value)
        assertEquals(1, events.count { it == "ticket:reserve" })
        assertEquals(1, events.count { it.startsWith("ticket:abandon:") })
        assertEquals("resources:rollback", events.last())
        assertFalse(events.any { it.startsWith("surface:") })
    }

    @Test
    fun `surface statuses are normalized and rollback resources after ticket reservation`() {
        val expectations = mapOf(
            GPUSurfaceAcquisitionStatus.Lost to "reconfigure",
            GPUSurfaceAcquisitionStatus.Outdated to "reconfigure",
            GPUSurfaceAcquisitionStatus.Timeout to "retry",
            GPUSurfaceAcquisitionStatus.OutOfMemory to "terminal",
            GPUSurfaceAcquisitionStatus.DeviceLost to "terminal",
        )
        expectations.forEach { (status, recovery) ->
            val events = mutableListOf<String>()
            val result = preflighter(
                RecordingResourceProvider(events),
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events, status),
            ).preflight(framePlan(listOf(prepareScene(), renderStep(), acquire(), surfaceBlit(), present())))

            val refused = assertIs<GPUFramePreflightResult.Refused>(result)
            assertEquals(recovery, refused.diagnostic.facts["recovery"])
            assertEquals(
                listOf(
                    "resource:prepare:scene",
                    "operands:prepare",
                    "ticket:reserve",
                    "surface:acquire:surface.main",
                    "ticket:abandon:ticket.7",
                    "resources:rollback",
                ),
                events,
            )
        }
    }

    @Test
    fun `device target resource and capability drift refuse before unsafe acquisition`() {
        val basePlan = framePlan(listOf(prepareScene(), renderStep(), acquire(), surfaceBlit(), present()))
        val cases = listOf(
            preflightContext(deviceGeneration = GPUDeviceGenerationID(8)),
            preflightContext(targetGeneration = 2),
            preflightContext(resourceGeneration = 2),
        )
        for (context in cases) {
            val events = mutableListOf<String>()
            val result = preflighter(
                RecordingResourceProvider(events),
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events),
                context = context,
            ).preflight(basePlan)
            assertIs<GPUFramePreflightResult.Refused>(result)
            assertTrue(events.isEmpty())
        }

        val events = mutableListOf<String>()
        val driftedCapabilities = capabilities(snapshotId = "capabilities.changed")
        val drifted = GPUFramePreflighter(
            context = preflightContext(),
            capabilities = driftedCapabilities,
            resourceProvider = RecordingResourceProvider(events),
            completionProvider = RecordingCompletionProvider(events),
            surfaceProvider = RecordingSurfaceProvider(events),
        ).preflight(basePlan)
        assertIs<GPUFramePreflightResult.Refused>(drifted)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `readback layout failure refuses before any resource ticket or surface acquisition`() {
        val events = mutableListOf<String>()
        val capabilities = capabilities(maxBufferSize = null)
        val result = GPUFramePreflighter(
            context = preflightContext(),
            capabilities = capabilities,
            resourceProvider = RecordingResourceProvider(events),
            completionProvider = RecordingCompletionProvider(events),
            surfaceProvider = RecordingSurfaceProvider(events),
        ).preflight(mixedPlan(capabilities))

        val refused = assertIs<GPUFramePreflightResult.Refused>(result)
        assertEquals("unsupported.readback.max_buffer_size_unavailable", refused.diagnostic.code.value)
        assertTrue(events.isEmpty())

        val duplicateEvents = mutableListOf<String>()
        val base = mixedPlan()
        val readback = base.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>().single()
        val duplicatePlan = framePlan(
            base.steps + GPUFrameStep.ReadbackCopyStep(
                source = readback.source,
                staging = readback.staging,
                request = readback.request,
                sourceTaskIds = listOf(GPUTaskID("task.readback.duplicate")),
            ),
        )
        val duplicate = preflighter(
            RecordingResourceProvider(duplicateEvents),
            RecordingCompletionProvider(duplicateEvents),
            RecordingSurfaceProvider(duplicateEvents),
        ).preflight(duplicatePlan)
        assertEquals(
            "invalid.preflight.readback_request_id_duplicate",
            assertIs<GPUFramePreflightResult.Refused>(duplicate).diagnostic.code.value,
        )
        assertTrue(duplicateEvents.isEmpty())
    }

    @Test
    fun `readback lease is output owned and prepared rollback is global reverse one shot`() {
        val events = mutableListOf<String>()
        val surface = RecordingSurfaceProvider(events)
        val prepared = assertIs<GPUFramePreflightResult.Prepared>(
            preflighter(
                RecordingResourceProvider(events),
                RecordingCompletionProvider(events),
                surface,
            ).preflight(mixedPlan()),
        ).frame

        assertEquals(1, prepared.resources.outputOwnedReadbacks.size)
        assertTrue(prepared.resources.ordinaryResources.none { it.role == GPUFrameResourceRole.ReadbackStaging })
        val readbackScope = prepared.encoderPlan.scopes.single {
            it.operationKind == GPUEncoderOperationKind.Readback
        }
        assertEquals(
            GPUPreparedNativeOperandOwnership.OutputOwnedReadback,
            readbackScope.nativeOperandKeys.single {
                it.role == GPUPreparedNativeOperandRole.ReadbackDestination
            }.ownership,
        )
        val beforeHash = prepared.stableHash()
        val first = prepared.rollback.execute()
        val second = prepared.rollback.execute()

        assertTrue(first === second)
        assertEquals(beforeHash, prepared.stableHash())
        assertEquals(1, events.count { it == "surface:release:surface.main" })
        assertEquals(1, events.count { it == "resources:rollback" })
        assertTrue(events.indexOf("surface:release:surface.main") < events.indexOf("resources:rollback"))
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (prepared.encoderPlan.scopes as MutableList<GPUCommandEncoderScopePlan>).clear()
        }
    }

    @Test
    fun `surface release failure still rolls resources back and returns stable rollback evidence`() {
        val events = mutableListOf<String>()
        val prepared = assertIs<GPUFramePreflightResult.Prepared>(
            preflighter(
                RecordingResourceProvider(events),
                RecordingCompletionProvider(events),
                RecordingSurfaceProvider(events, throwOnRelease = true),
            ).preflight(framePlan(listOf(prepareScene(), renderStep(), acquire(), surfaceBlit(), present()))),
        ).frame

        val result = prepared.rollback.execute()

        assertFalse(result.successful)
        assertEquals("failed.preflight.surface_release", result.diagnostics.single().code.value)
        assertEquals("resources:rollback", events.last())
    }

    @Test
    fun `undeclared resource and mismatched surface chain refuse before acquisition`() {
        val undeclaredEvents = mutableListOf<String>()
        val undeclared = preflighter(
            RecordingResourceProvider(undeclaredEvents),
            RecordingCompletionProvider(undeclaredEvents),
            RecordingSurfaceProvider(undeclaredEvents),
        ).preflight(
            framePlan(
                listOf(
                    prepareScene(),
                    GPUFrameStep.CopyResourceStep(
                        source = GPUFrameTargetRef("target.scene"),
                        destination = GPUFrameTextureRef("texture.undeclared"),
                        regions = listOf(GPUResourceCopyRegion(0, 0, null, 16)),
                        sourceTaskIds = listOf(GPUTaskID("task.copy")),
                    ),
                ),
            ),
        )
        assertEquals(
            "invalid.preflight.resource_undeclared",
            assertIs<GPUFramePreflightResult.Refused>(undeclared).diagnostic.code.value,
        )
        assertTrue(undeclaredEvents.isEmpty())

        val outputEvents = mutableListOf<String>()
        val mismatched = preflighter(
            RecordingResourceProvider(outputEvents),
            RecordingCompletionProvider(outputEvents),
            RecordingSurfaceProvider(outputEvents),
        ).preflight(
            framePlan(
                listOf(
                    prepareScene(),
                    renderStep(),
                    acquire(),
                    GPUFrameStep.SurfaceBlitRenderPassStep(
                        scene = GPUFrameTargetRef("target.scene"),
                        output = GPUSurfaceOutputRef("surface.other"),
                        sourceTaskIds = listOf(GPUTaskID("task.output")),
                    ),
                    present(),
                ),
            ),
        )
        assertEquals(
            "invalid.preflight.surface_output_mismatch",
            assertIs<GPUFramePreflightResult.Refused>(mismatched).diagnostic.code.value,
        )
        assertTrue(outputEvents.isEmpty())
    }

    private fun preflighter(
        resources: GPUFrameResourcePreflightProvider,
        completion: GPUQueueCompletionProvider,
        surface: GPUSurfaceOutputProvider,
        context: GPUFramePreflightContext = preflightContext(),
        nativeBoundary: GPUPreparedNativeFrameBoundary? = null,
        capabilities: GPUCapabilities = capabilities(),
    ): GPUFramePreflighter = GPUFramePreflighter(
        context = context,
        capabilities = capabilities,
        resourceProvider = resources,
        completionProvider = completion,
        surfaceProvider = surface,
        nativeBoundary = nativeBoundary,
    )

    private class RecordingResourceProvider(
        private val events: MutableList<String>,
        private val commandFailure: String? = null,
        private val resourceFailureLabel: String? = null,
        private val resourceFailureCode: String = "unsupported.preflight.resource_refused",
        private val throwOnResource: Boolean = false,
        private val throwOnOperands: Boolean = false,
        private val retainCommandEvidence: Boolean = false,
    ) : GPUFrameResourcePreflightProvider {
        var encoderCreateCount = 0
        var submitCount = 0
        var beginFramePreparationCount = 0
        private var sessionOrdinal = 0L

        override fun beginFramePreparation(
            frameId: Long,
            deviceGeneration: GPUDeviceGenerationID,
        ): GPUFrameResourcePreparationSession {
            beginFramePreparationCount += 1
            return GPUFrameResourcePreparationSession(
                "frame-preflight:$frameId:device:${deviceGeneration.value}:attempt:${++sessionOrdinal}",
            )
        }

        override fun prepareFrameResource(
            input: GPUFrameResourcePreparationInput,
        ): GPUFrameResourcePreparationDecision {
            events += "resource:prepare:${input.preparation.diagnosticLabel}"
            if (throwOnResource) error("resource provider failed")
            if (input.preparation.diagnosticLabel == resourceFailureLabel) {
                return GPUFrameResourcePreparationDecision.Refused(
                    preflightDiagnostic(resourceFailureCode, "resource preparation refused"),
                )
            }
            return GPUFrameResourcePreparationDecision.Prepared(
                logicalResource = input.preparation.resource,
                concreteResource = when (input.preparation.resource) {
                    is GPUFrameBufferRef -> GPUPreparedConcreteResourceRef.Buffer(
                        GPUBufferResourceRef("prepared:${input.preparation.resource.value}"),
                    )
                    else -> GPUPreparedConcreteResourceRef.Texture(
                        GPUTextureResourceRef("prepared:${input.preparation.resource.value}"),
                    )
                },
                role = input.preparation.role,
                deviceGeneration = input.deviceGeneration,
                resourceGeneration = input.resourceGeneration,
                outputOwnedReadbackLease = if (input.preparation.role == GPUFrameResourceRole.ReadbackStaging) {
                    GPUReadbackStagingLease(
                        reservationId = "readback.test",
                        ownerScope = input.ownerScope,
                        deviceGeneration = input.deviceGeneration,
                        resourceRef = GPUBufferResourceRef("prepared:${input.preparation.resource.value}"),
                        reservationOrdinal = 1,
                        acquisitionToken = 1,
                        logicalMinimumBytes = input.readbackStagingDescriptor!!.minimumBufferBytes,
                        backingBufferBytes = (input.preparation.descriptor as GPUFrameBufferDescriptor).byteSize,
                        usages = input.preparation.usages,
                    )
                } else null,
            )
        }

        override fun materializeCommandOperands(
            request: GPUCommandOperandMaterializationRequest,
            context: GPUTargetPreparationContext,
        ): GPUResourceMaterializationDecision {
            events += "operands:prepare"
            if (throwOnOperands) error("operand provider failed")
            val failure = commandFailure
            if (failure != null) {
                return GPUResourceMaterializationDecision.Refused(
                    diagnostic = GPUResourceDiagnostic(
                        code = "unsupported.preflight.$failure",
                        resourceLabel = failure,
                        message = "$failure refused",
                        terminal = true,
                    ),
                    targetId = context.targetId,
                )
            }
            val ownerScope = request.operands.firstOrNull()?.ownerScope ?: "frame-preflight:none"
            return GPUResourceMaterializationDecision.Materialized(
                resources = emptyList(),
                diagnostics = if (retainCommandEvidence) {
                    listOf(
                        GPUResourceDiagnostic(
                            code = "diagnostic.preflight.cache_reuse",
                            resourceLabel = "render-cache",
                            message = "Render cache entry was reused.",
                            terminal = false,
                            facts = mapOf("result" to "reuse"),
                        ),
                    )
                } else emptyList(),
                targetId = context.targetId,
                taskIds = request.taskIds,
                resourcePlanLabels = request.resourcePlanLabels,
                operandBridge = request.operands.map { operand ->
                    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandBinding(
                        packetId = operand.packetId,
                        commandLabel = operand.commandLabel,
                        operand = org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandReference(
                            label = operand.label,
                            kind = operand.kind,
                            descriptorHash = operand.descriptorHash,
                            deviceGeneration = operand.deviceGeneration,
                            ownerScope = operand.ownerScope,
                            usageLabels = operand.requiredUsageLabels.toList(),
                            invalidationPolicy = operand.invalidationPolicy,
                            evidenceFacts = operand.evidenceFacts,
                        ),
                    )
                },
                resourceLeases = if (retainCommandEvidence) {
                    listOf(
                        GPUResourceLease(
                            leaseId = "lease.render",
                            resourceKind = GPUResourceLeaseKind.BindGroup,
                            deviceGeneration = context.deviceGeneration,
                            descriptorHash = "render.bindings",
                            ownerScope = ownerScope,
                            usageLabels = listOf("render"),
                            releasePolicy = "submission-complete",
                            cacheResult = GPUResourceLeaseCacheResult.Create,
                        ),
                    )
                } else emptyList(),
            )
        }

        override fun rollbackFrameResourcesBeforeSubmit(
            ownerScope: String,
        ): GPUPhysicalPoolMaintenanceDecision<GPUPhysicalPoolRollbackSummary> {
            events += "resources:rollback"
            return GPUPhysicalPoolMaintenanceDecision.Applied(
                GPUPhysicalPoolRollbackSummary(
                    scratch = org.graphiks.kanvas.gpu.renderer.resources.GPUScratchRollbackResult(
                        emptyList(),
                        emptyList(),
                    ),
                    readback = GPUReadbackStagingRollbackResult(emptyList(), emptyList()),
                    releaseOrder = emptyList(),
                ),
            )
        }
    }

    private inner class RenderOnlyNativePayloadMaterializer(
        private val events: MutableList<String>,
        private val omitSemanticPayloads: Boolean = false,
        private val encoderPlanIdSuffix: String = "",
        private val ownedHandle: AutoCloseable? = null,
        private val additionalOwnedHandles: List<AutoCloseable> = emptyList(),
    ) : GPUPreparedNativeFramePayloadMaterializer {
        var lastRenderOperand: GPUPreparedNativeScopeOperand.Render? = null
            private set

        override fun materializeReusable(
            framePlan: GPUFramePlan,
            encoderPlan: GPUCommandEncoderPlan,
            resources: GPUPreparedResourceSet,
            generationSeal: GPUPreparedGenerationSeal,
        ): GPUPreparedNativeFramePayloadMaterialization {
            events += "native-payload:materialize"
            val rendersBySourceStepIndex = framePlan.steps.mapIndexedNotNull { index, step ->
                (step as? GPUFrameStep.RenderPassStep)?.let { index to it }
            }.toMap()
            val renderOperands = encoderPlan.scopes.map { scope ->
                val render = requireNotNull(rendersBySourceStepIndex[scope.sourceStepIndex])
                GPUPreparedNativeScopeOperand.Render(
                    sourceStepIndex = scope.sourceStepIndex,
                    pass = GPUPreparedNativeRenderPassConfig(
                        colorTarget = GPUPreparedNativeTextureViewOperand(
                            fakeNative<GPUTextureView>("target.scene.view.${scope.sourceStepIndex}"),
                            generationSeal.deviceGeneration,
                        ),
                    ),
                    commands = listOf(
                        GPUPreparedNativeRenderCommand.SetPipeline(
                            GPUPreparedNativeRenderPipelineOperand(
                                fakeNative<GPURenderPipeline>(
                                    "pipeline.rect.${scope.sourceStepIndex}",
                                ),
                                generationSeal.deviceGeneration,
                            ),
                        ),
                        GPUPreparedNativeRenderCommand.SetBindGroup(
                            index = 0,
                            bindGroup = GPUPreparedNativeBindGroupOperand(
                                fakeNative<GPUBindGroup>(
                                    "binding.rect.${scope.sourceStepIndex}",
                                ),
                                generationSeal.deviceGeneration,
                            ),
                        ),
                        GPUPreparedNativeRenderCommand.Draw(
                            GPUPreparedNativeDrawCall.Draw(vertexCount = 6),
                        ),
                    ),
                    semanticPayloads = if (omitSemanticPayloads) {
                        emptyList()
                    } else {
                        render.drawPackets.mapNotNull(GPUDrawPacket::semanticPayload)
                    },
                )
            }
            lastRenderOperand = renderOperands.last()
            return GPUPreparedNativeFramePayloadMaterialization.Materialized(
                GPUPreparedNativeFrameDraft(
                    GPUPreparedNativeFramePayload(
                        identity = GPUPreparedNativeFrameIdentity(
                            frameId = framePlan.frameId,
                            contextIdentity = encoderPlan.contextIdentity,
                            encoderPlanId = encoderPlan.planId + encoderPlanIdSuffix,
                            deviceGeneration = generationSeal.deviceGeneration,
                            targetGeneration = generationSeal.targetGeneration,
                            scopes = encoderPlan.scopes.map { scope ->
                                GPUPreparedNativeScopeKey(
                                    scope.sourceStepIndex,
                                    scope.operationKind,
                                    scope.resourceGenerationLabels,
                                    scope.nativeOperandKeys,
                                )
                            },
                        ),
                        scopeOperands = renderOperands,
                        scopeOperandKeys = encoderPlan.scopes.map { it.nativeOperandKeys },
                        auxiliaryOwnedHandles = (listOfNotNull(ownedHandle) + additionalOwnedHandles)
                            .map { handle ->
                                GPUPreparedNativeAuxiliaryHandle(
                                    handle,
                                    GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                                )
                            },
                    ),
                ),
            )
        }

        override fun bindLateSurface(
            draft: GPUPreparedNativeFrameDraft,
            acquiredSurface: GPUAcquiredSurfaceOutput?,
        ): GPUPreparedNativeFrameLateSurfaceBinding =
            GPUPreparedNativeFrameLateSurfaceBinding.NotRequired
    }

    private fun ownedDraft(frame: Long, handle: AutoCloseable) = GPUPreparedNativeFrameDraft(
        GPUPreparedNativeFramePayload(
            identity = GPUPreparedNativeFrameIdentity(
                frameId = GPUFrameID(frame),
                contextIdentity = "conflict.$frame",
                encoderPlanId = "conflict.$frame",
                deviceGeneration = GPUDeviceGenerationID(11),
                targetGeneration = 7,
                scopes = emptyList(),
            ),
            scopeOperands = emptyList(),
            scopeOperandKeys = emptyList(),
            auxiliaryOwnedHandles = listOf(
                GPUPreparedNativeAuxiliaryHandle(
                    handle,
                    GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                ),
            ),
        ),
    )

    private class CountingDraftHandle(
        private var closeFailuresRemaining: Int = 0,
    ) : AutoCloseable {
        var closeAttempts = 0
            private set
        var successfulCloses = 0
            private set

        override fun close() {
            closeAttempts += 1
            if (closeFailuresRemaining > 0) {
                closeFailuresRemaining -= 1
                error("draft close failed")
            }
            successfulCloses += 1
        }
    }

    private inner class RefusingSurfaceNativePayloadMaterializer(
        private val events: MutableList<String>,
    ) : GPUPreparedNativeFramePayloadMaterializer {
        override fun materializeReusable(
            framePlan: GPUFramePlan,
            encoderPlan: GPUCommandEncoderPlan,
            resources: GPUPreparedResourceSet,
            generationSeal: GPUPreparedGenerationSeal,
        ): GPUPreparedNativeFramePayloadMaterialization {
            events += "native-payload:materialize"
            val scope = encoderPlan.scopes.single()
            return GPUPreparedNativeFramePayloadMaterialization.Materialized(
                GPUPreparedNativeFrameDraft(
                    GPUPreparedNativeFramePayload(
                        identity = GPUPreparedNativeFrameIdentity(
                            frameId = framePlan.frameId,
                            contextIdentity = encoderPlan.contextIdentity,
                            encoderPlanId = encoderPlan.planId,
                            deviceGeneration = generationSeal.deviceGeneration,
                            targetGeneration = generationSeal.targetGeneration,
                            scopes = listOf(
                                GPUPreparedNativeScopeKey(
                                    scope.sourceStepIndex,
                                    scope.operationKind,
                                    scope.resourceGenerationLabels,
                                    scope.nativeOperandKeys,
                                ),
                            ),
                        ),
                        scopeOperands = listOf(
                            GPUPreparedNativeScopeOperand.SurfaceBlit(
                                sourceStepIndex = scope.sourceStepIndex,
                                source = GPUPreparedNativeTextureViewOperand(
                                    fakeNative<GPUTextureView>("surface-source"),
                                    generationSeal.deviceGeneration,
                                ),
                                output = GPUSurfaceOutputRef("surface.main"),
                                pipeline = GPUPreparedNativeRenderPipelineOperand(
                                    fakeNative<GPURenderPipeline>("surface-pipeline"),
                                    generationSeal.deviceGeneration,
                                ),
                                bindGroup = GPUPreparedNativeBindGroupOperand(
                                    fakeNative<GPUBindGroup>("surface-bind-group"),
                                    generationSeal.deviceGeneration,
                                ),
                            ),
                        ),
                        scopeOperandKeys = listOf(scope.nativeOperandKeys),
                    ),
                ),
            )
        }

        override fun bindLateSurface(
            draft: GPUPreparedNativeFrameDraft,
            acquiredSurface: GPUAcquiredSurfaceOutput?,
        ): GPUPreparedNativeFrameLateSurfaceBinding {
            events += "native-payload:bind"
            return GPUPreparedNativeFrameLateSurfaceBinding.Refused(
                "unsupported.native-frame-payload.surface-bind-refused",
                "Surface target could not be bound.",
            )
        }
    }

    private inline fun <reified T> fakeNative(label: String): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "getLabel" -> label
            "setLabel", "close" -> Unit
            "toString" -> "FakeNative($label)"
            else -> error("Unexpected fake native call: ${method.name}")
        }
    } as T

    private class RecordingCompletionProvider(
        private val events: MutableList<String>,
        private val mode: String = "reserved",
    ) : GPUQueueCompletionProvider {
        override fun abandonReservedTicket(
            ticket: GPUQueueCompletionTicket,
        ): GPUQueueCompletionTicketAbandonResult {
            events += "ticket:abandon:${ticket.ticketId.value}"
            return GPUQueueCompletionTicketAbandonResult.Abandoned(ticket.ticketId)
        }

        override fun reserveTicket(
            request: GPUQueueCompletionTicketRequest,
        ): GPUQueueCompletionTicketReservation {
            events += "ticket:reserve"
            return when (mode) {
                "missing" -> GPUQueueCompletionTicketReservation.Missing
                "failed" -> GPUQueueCompletionTicketReservation.Failed(
                    preflightDiagnostic("unsupported.preflight.completion_ticket_failed", "queue proof unavailable"),
                )
                "duplicate" -> GPUQueueCompletionTicketReservation.Duplicate(GPUQueueCompletionTicketID("ticket.7"))
                "wrong-frame" -> GPUQueueCompletionTicketReservation.Reserved(
                    GPUQueueCompletionTicket(GPUQueueCompletionTicketID("ticket.7"), GPUFrameID(request.frameId.value + 1), request.deviceGeneration),
                )
                "wrong-generation" -> GPUQueueCompletionTicketReservation.Reserved(
                    GPUQueueCompletionTicket(
                        GPUQueueCompletionTicketID("ticket.7"),
                        request.frameId,
                        GPUDeviceGenerationID(request.deviceGeneration.value + 1),
                    ),
                )
                "throw" -> error("ticket provider failed")
                else -> GPUQueueCompletionTicketReservation.Reserved(
                    GPUQueueCompletionTicket(GPUQueueCompletionTicketID("ticket.7"), request.frameId, request.deviceGeneration),
                )
            }
        }
    }

    private class RecordingSurfaceProvider(
        private val events: MutableList<String>,
        private val status: GPUSurfaceAcquisitionStatus? = null,
        private val throwOnRelease: Boolean = false,
        private val throwOnAcquire: Boolean = false,
    ) : GPUSurfaceOutputProvider {
        override fun acquire(
            request: GPUSurfaceAcquisitionRequest,
        ): GPUSurfaceAcquisitionResult {
            events += "surface:acquire:${request.descriptor.output.value}"
            if (throwOnAcquire) error("surface acquire failed")
            return if (status == null) {
                GPUSurfaceAcquisitionResult.Acquired(
                    GPUAcquiredSurfaceOutput(
                        output = request.descriptor.output,
                        deviceGeneration = request.deviceGeneration,
                        targetGeneration = request.descriptor.targetGeneration,
                        evidenceLabel = "surface-output",
                    ),
                )
            } else {
                GPUSurfaceAcquisitionResult.Unavailable(status)
            }
        }

        override fun release(output: GPUAcquiredSurfaceOutput): GPUSurfaceReleaseResult {
            events += "surface:release:${output.output.value}"
            if (throwOnRelease) error("surface release failed")
            return GPUSurfaceReleaseResult.Released
        }
    }

    private fun mixedPlan(capabilities: GPUCapabilities = capabilities()): GPUFramePlan = framePlan(
        listOf(
            prepareResources(),
            renderStep(),
            GPUFrameStep.UploadResourceStep(
                staging = GPUFrameBufferRef("buffer.upload"),
                destination = GPUFrameTargetRef("target.scene"),
                layout = GPUUploadLayout(0, 256, 1, 64),
                sourceTaskIds = listOf(GPUTaskID("task.upload")),
            ),
            GPUFrameStep.CopyResourceStep(
                source = GPUFrameTargetRef("target.scene"),
                destination = GPUFrameTextureRef("texture.copy"),
                regions = listOf(GPUResourceCopyRegion(0, 0, GPUPixelBounds(0, 0, 4, 4), 64)),
                sourceTaskIds = listOf(GPUTaskID("task.copy")),
            ),
            GPUFrameStep.DependencyBarrierStep(
                orderedUseTokens = listOf(GPUTaskUseToken("use.upload"), GPUTaskUseToken("use.copy")),
                reasonCode = "dependency.copy-after-upload",
                sourceTaskIds = listOf(GPUTaskID("task.barrier")),
            ),
            GPUFrameStep.ComputePassStep(
                target = GPUFrameTargetRef("target.scene"),
                resourceUses = emptyList(),
                dispatches = listOf(GPUComputeDispatch(GPUComputePipelineKey("compute.key"), 1, 1, 1)),
                sourceTaskIds = listOf(GPUTaskID("task.compute")),
            ),
            GPUFrameStep.ReadbackCopyStep(
                source = GPUFrameTargetRef("target.scene"),
                staging = GPUFrameBufferRef("buffer.readback"),
                request = GPUFrameReadbackRequest(
                    requestId = GPUReadbackRequestID("readback.main"),
                    sourceBounds = GPUPixelBounds(0, 0, 4, 4),
                    pixelFormat = GPUReadbackPixelFormat.Rgba8Unorm,
                    outputColorInterpretation = GPUColorInterpretation("srgb-premul"),
                ),
                sourceTaskIds = listOf(GPUTaskID("task.readback")),
            ),
            acquire(),
            surfaceBlit(),
            present(),
        ),
        capabilities,
    )

    private fun prepareResources(): GPUFrameStep.PrepareResourcesStep = GPUFrameStep.PrepareResourcesStep(
        requests = listOf(
            GPUResourcePreparationRequest(
                resource = GPUFrameTargetRef("target.scene"),
                descriptor = GPUFrameTextureDescriptor(GPUPixelBounds(0, 0, 4, 4), GPUColorFormat("rgba8unorm"), 1),
                role = GPUFrameResourceRole.SceneTarget,
                usages = setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = 64,
                diagnosticLabel = "scene",
            ),
            GPUResourcePreparationRequest(
                resource = GPUFrameBufferRef("buffer.upload"),
                descriptor = GPUFrameBufferDescriptor(64, 4),
                role = GPUFrameResourceRole.UploadStaging,
                usages = setOf(GPUFrameResourceUsage.CopySource),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = 64,
                diagnosticLabel = "upload",
            ),
            GPUResourcePreparationRequest(
                resource = GPUFrameTextureRef("texture.copy"),
                descriptor = GPUFrameTextureDescriptor(GPUPixelBounds(0, 0, 4, 4), GPUColorFormat("rgba8unorm"), 1),
                role = GPUFrameResourceRole.DestinationSnapshot,
                usages = setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.TextureBinding),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = 64,
                diagnosticLabel = "copy",
            ),
            GPUResourcePreparationRequest(
                resource = GPUFrameBufferRef("buffer.readback"),
                descriptor = GPUFrameBufferDescriptor(1024, 4),
                role = GPUFrameResourceRole.ReadbackStaging,
                usages = setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.MapRead),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = 1024,
                diagnosticLabel = "readback",
            ),
        ),
        sourceTaskIds = listOf(GPUTaskID("task.prepare")),
    )

    private fun prepareScene(
        bounds: GPUPixelBounds = GPUPixelBounds(0, 0, 4, 4),
        format: String = "rgba8unorm",
        role: GPUFrameResourceRole = GPUFrameResourceRole.SceneTarget,
        usages: Set<GPUFrameResourceUsage> =
            setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource),
        lifetime: GPUFrameResourceLifetime = GPUFrameResourceLifetime.FrameLocal,
        byteSize: Long = bounds.width.toLong() * bounds.height * 4L,
    ): GPUFrameStep.PrepareResourcesStep = GPUFrameStep.PrepareResourcesStep(
        requests = listOf(
            GPUResourcePreparationRequest(
                resource = GPUFrameTargetRef("target.scene"),
                descriptor = GPUFrameTextureDescriptor(
                    bounds,
                    GPUColorFormat(format),
                    1,
                ),
                role = role,
                usages = usages,
                lifetime = lifetime,
                byteSize = byteSize,
                diagnosticLabel = "scene",
            ),
        ),
        sourceTaskIds = listOf(GPUTaskID("task.prepare")),
    )

    private fun preparedPathFramePlan(mixed: Boolean): GPUFramePlan {
        val commandIds = if (mixed) listOf(71, 72, 73) else listOf(72)
        val pathCapabilities = pathCapabilities()
        val base = GPURecorder(
            GPURecordingID("recording.preflight.path"),
            GPUFrameID(107),
            pathCapabilities,
        ).apply {
            commandIds.forEachIndexed { paintOrder, commandId ->
                record(pathBuilderCommand(commandId, paintOrder))
            }
        }.close().taskList.withCoreClipPlans(commandIds.associateWith { GPUClipExecutionPlan.NoClip })
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
        val semantics = commandIds.associateWith { commandId ->
            if (commandId == 72) {
                pathCoreSemantic()
            } else {
                coreSemantic(commandIdValue = commandId)
            }
        }
        check(packets.map(GPUDrawPacket::commandIdValue).toSet() == semantics.keys) {
            "base packet commands=${packets.map(GPUDrawPacket::commandIdValue)} semantics=${semantics.keys}"
        }
        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                GPUCorePrimitivePreparedFrameRequest(
                    baseTaskList = base,
                    capabilities = pathCapabilities,
                    target = GPUFrameTargetRef("target.scene"),
                    targetBounds = GPUPixelBounds(0, 0, 4, 4),
                    semanticsByCommandId = semantics,
                ),
            ),
        ).taskList
        return GPUFramePlanner.plan(taskList).also { plan -> check(!plan.atomicallyRefused) }
    }

    private fun preparedAnalyticFramePlan(
        plans: Map<Int, GPUClipExecutionPlan>,
        geometries: Map<Int, GPUCorePrimitiveGeometryInput> = emptyMap(),
    ): GPUFramePlan {
        val analyticCapabilities = pathCapabilities()
        val commandIds = plans.keys.toList()
        val base = GPURecorder(
            GPURecordingID("recording.preflight.analytic"),
            GPUFrameID(108),
            analyticCapabilities,
        ).apply {
            commandIds.forEachIndexed { paintOrder, commandId ->
                record(pathBuilderCommand(commandId, paintOrder))
            }
        }.close().taskList.withCoreClipPlans(plans)
        val packets = base.tasks.filterIsInstance<GPUTask.Render>().flatMap(GPUTask.Render::drawPackets)
        val semantics = packets.associate { packet ->
            val geometry = geometries[packet.commandIdValue]
                ?: GPUCorePrimitiveGeometryInput.Rect(1f, 1f, 3f, 3f)
            packet.commandIdValue to coreSemantic(
                commandIdValue = packet.commandIdValue,
                sourceFamily = when (geometry) {
                    is GPUCorePrimitiveGeometryInput.Rect -> GPUCorePrimitiveSourceFamily.Rect
                    is GPUCorePrimitiveGeometryInput.RRect -> GPUCorePrimitiveSourceFamily.RRect
                    is GPUCorePrimitiveGeometryInput.TriangulatedPath -> GPUCorePrimitiveSourceFamily.Path
                },
                geometry = geometry,
            )
        }
        val taskList = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUCorePrimitivePreparedFrameTaskListBuilder().build(
                GPUCorePrimitivePreparedFrameRequest(
                    baseTaskList = base,
                    capabilities = analyticCapabilities,
                    target = GPUFrameTargetRef("target.scene"),
                    targetBounds = GPUPixelBounds(0, 0, 4, 4),
                    semanticsByCommandId = semantics,
                ),
            ),
        ).taskList
        return GPUFramePlanner.plan(taskList).also { plan -> check(!plan.atomicallyRefused) }
    }

    private fun preparedNativeClipStencilFramePlan(): GPUFramePlan {
        val targetBounds = GPUPixelBounds(0, 0, 4, 4)
        val clipPlan = GPUClipExecutionPlan.StencilCoverage(
            contentKey = "clip.preflight.native.path",
            bounds = targetBounds,
            sampleCount = 1,
            atomicGroup = GPUClipAtomicGroupID("atomic.preflight.native.path"),
            orderingToken = GPUClipOrderingToken("token.preflight.native.path"),
            producer = GPUClipStencilProducerPlan(
                geometry = GPUClipExecutionGeometry.Path(
                    vertices = listOf(1f, 1f, 3f, 1f, 3f, 3f, 1f, 3f),
                    contourStarts = listOf(0),
                    fillRule = GPUClipFillRule.Winding,
                    inverseFill = false,
                ),
                scissor = targetBounds,
                fillRule = GPUClipFillRule.Winding,
                reference = 0u,
                compare = GPUClipStencilCompare.Always,
                frontPassOperation = GPUClipStencilOperation.IncrementWrap,
                backPassOperation = GPUClipStencilOperation.DecrementWrap,
                loadOperation = GPUClipStencilLoadOperation.Clear,
                storeOperation = GPUClipStencilStoreOperation.Store,
                clearValue = 0u,
            ),
            consumer = GPUClipStencilConsumerPlan(
                scissor = targetBounds,
                reference = 0u,
                compare = GPUClipStencilCompare.NotEqual,
            ),
        )
        return preparedAnalyticFramePlan(
            mapOf(
                91 to clipPlan,
                92 to clipPlan,
            ),
        )
    }

    private fun preparedCoverageMaskFramePlan(): GPUFramePlan {
        val targetBounds = GPUPixelBounds(0, 0, 4, 4)
        val clipPlan = GPUClipExecutionPlan.CoverageMask(
            contentKey = "clip.preflight.native.mask",
            bounds = targetBounds,
            sampleCount = 1,
            depthStencilRequired = false,
            orderingToken = GPUClipOrderingToken("token.preflight.native.mask"),
            producers = listOf(
                GPUClipMaskProducerPlan(
                    sourceOrder = 0,
                    geometry = GPUClipExecutionGeometry.Rect(GPUBounds(0f, 0f, 4f, 4f)),
                    combine = GPUClipMaskCombine.Intersect,
                    antiAlias = false,
                ),
                GPUClipMaskProducerPlan(
                    sourceOrder = 1,
                    geometry = GPUClipExecutionGeometry.RRect(
                        GPUBounds(1f, 1f, 3f, 3f),
                        listOf(0.25f, 0.5f, 0.25f, 0.5f, 0.25f, 0.5f, 0.25f, 0.5f),
                    ),
                    combine = GPUClipMaskCombine.Difference,
                    antiAlias = false,
                ),
            ),
            consumer = GPUClipMaskConsumerPlan(),
        )
        return preparedAnalyticFramePlan(
            mapOf(
                93 to clipPlan,
                94 to clipPlan,
            ),
            geometries = mapOf(
                94 to GPUCorePrimitiveGeometryInput.TriangulatedPath(
                    vertices = listOf(0.5f, 0.5f, 3.5f, 0.5f, 2f, 3.5f),
                    indices = listOf(0, 2, 1),
                    sourceContourStarts = listOf(0),
                    sourceVertexCount = 3,
                    coverBounds = GPUPixelBounds(0, 0, 4, 4),
                    geometryMode = GPUCorePrimitiveGeometryMode.DirectTriangles,
                ),
            ),
        )
    }

    private fun pathBuilderCommand(commandId: Int, paintOrder: Int) = GPUFillRectCommandBuilder.build(
        commandId = GPUDrawCommandID(commandId),
        rect = GPURect(1f, 1f, 3f, 3f),
        target = GPUTargetFacts(4, 4, "rgba8unorm"),
        material = GPUMaterialDescriptor.SolidColor(0.25f, 0.5f, 0.75f, 1f),
        clip = GPUClipFacts(
            kind = GPUClipKind.WideOpen,
            bounds = GPUCommandBounds(0f, 0f, 4f, 4f),
            coveragePlan = GPUClipCoveragePlan.NoClip,
        ),
        paintOrder = paintOrder,
        source = GPUCommandSource("unit-test", "fillRect", GPUFrameProvenance.GmContent),
    )

    private fun pathCapabilities(): GPUCapabilities = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "unit", "adapter", "device"),
        facts = listOf(
            GPUCapabilityFact("first_slice.fill_rect.native", "unit-test", "supported", true, "preflight"),
            GPUCapabilityFact("first_slice.scissor.native", "unit-test", "supported", true, "preflight"),
        ),
        snapshotId = "preflight-path",
        limits = GPULimits(
            8192,
            256,
            256,
            maxBufferSize = 1L shl 30,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
        supportedTextureFormats = setOf(
            io.ygdrasil.webgpu.GPUTextureFormat.RGBA8Unorm,
        ),
        rendererFeatures = setOf(
            GPURendererFeature.RenderPass,
            GPURendererFeature.CopyUpload,
            GPURendererFeature.Readback,
        ),
    )

    private fun GPUTaskList.withCoreClipPlans(
        plans: Map<Int, GPUClipExecutionPlan>,
    ): GPUTaskList = GPUTaskList(
        frameId = frameId,
        capabilitySeal = capabilitySeal,
        recordingSeals = recordingSeals,
        expectedReplayKeyHash = expectedReplayKeyHash,
        tasks = tasks.map { task ->
            if (task !is GPUTask.Render) return@map task
            val packets = task.drawPackets.map { packet ->
                cloneCorePacket(packet, clipExecutionPlan = plans.getValue(packet.commandIdValue))
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
                packets.associate { packet ->
                    packet.packetId to requireNotNull(task.batchEligibilityByPacketId[packet.packetId])
                },
                task.sampleContinuationKey,
                task.compositeMembership,
                task.depthStencilLoadStore,
            )
        },
        dependencies = dependencies,
        phaseOrder = phaseOrder,
        memoryBudget = memoryBudget,
        diagnostics = diagnostics,
    )

    private fun renderWith(
        source: GPUFrameStep.RenderPassStep,
        drawPackets: List<GPUDrawPacket> = source.drawPackets,
        resourceUses: List<GPUFrameResourceUse> = source.resourceUses,
        loadStore: GPULoadStorePlan = source.loadStore,
        samplePlan: GPUSamplePlan = source.samplePlan,
        depthStencilLoadStore: GPUDepthStencilLoadStorePlan? = source.depthStencilLoadStore,
    ) = GPUFrameStep.RenderPassStep(
        target = source.target,
        loadStore = loadStore,
        samplePlan = samplePlan,
        resourceUses = resourceUses,
        drawPackets = drawPackets,
        sourceTaskIds = source.sourceTaskIds,
        batches = listOf(
            GPUFrameRenderBatch(
                batchId = source.batches.first().batchId,
                kind = source.batches.first().kind,
                packets = drawPackets,
                sourceTaskIds = source.sourceTaskIds,
            ),
        ),
        depthStencilLoadStore = depthStencilLoadStore,
    )

    private fun replaceRender(
        plan: GPUFramePlan,
        replacement: GPUFrameStep.RenderPassStep,
    ): GPUFramePlan {
        val original = plan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single()
        return GPUFramePlan(
            frameId = plan.frameId,
            capabilitySeal = plan.capabilitySeal,
            recordingSeals = plan.recordingSeals,
            steps = plan.steps.map { step -> if (step === original) replacement else step },
            memoryBudget = plan.memoryBudget,
            diagnostics = plan.diagnostics,
            dependencies = plan.dependencies,
        )
    }

    private fun GPUFramePlan.withSteps(replacement: List<GPUFrameStep>): GPUFramePlan = GPUFramePlan(
        frameId,
        capabilitySeal,
        recordingSeals,
        replacement,
        memoryBudget,
        diagnostics,
        dependencies,
    )

    private fun GPUFramePlan.withDependencies(
        replacement: List<GPUTaskDependency>,
    ): GPUFramePlan = GPUFramePlan(
        frameId,
        capabilitySeal,
        recordingSeals,
        steps,
        memoryBudget,
        diagnostics,
        replacement,
    )

    private fun GPUFramePlan.replacingRender(
        original: GPUFrameStep.RenderPassStep,
        replacement: GPUFrameStep.RenderPassStep,
    ): GPUFramePlan = withSteps(steps.map { step -> if (step === original) replacement else step })

    private fun GPUFramePlan.mapPreparations(
        transform: (GPUResourcePreparationRequest) -> GPUResourcePreparationRequest,
    ): GPUFramePlan = withSteps(steps.map { step ->
        if (step !is GPUFrameStep.PrepareResourcesStep) return@map step
        GPUFrameStep.PrepareResourcesStep(
            step.requests.map(transform),
            step.sourceTaskIds,
        )
    })

    private fun GPUResourcePreparationRequest.copyForTest(
        resource: GPUFrameResourceRef = this.resource,
        descriptor: org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceDescriptor =
            this.descriptor,
        role: GPUFrameResourceRole = this.role,
        usages: Set<GPUFrameResourceUsage> = this.usages,
        lifetime: GPUFrameResourceLifetime = this.lifetime,
        byteSize: Long = this.byteSize,
        diagnosticLabel: String = this.diagnosticLabel,
    ) = GPUResourcePreparationRequest(
        resource,
        descriptor,
        role,
        usages,
        lifetime,
        byteSize,
        diagnosticLabel,
    )

    private fun cloneCorePacket(
        packet: GPUDrawPacket,
        packetId: GPUDrawPacketID = packet.packetId,
        role: GPUDrawPacketRole = packet.role,
        commandIdValue: Int = packet.commandIdValue,
        analysisRecordId: String = packet.analysisRecordId,
        blendPlan: GPUBlendPlan? = packet.blendPlan,
        renderPipelineKey: GPURenderPipelineKey = requireNotNull(packet.renderPipelineKey),
        scissorBoundsHash: String? = packet.scissorBoundsHash,
        clipExecutionPlan: GPUClipExecutionPlan? = packet.clipExecutionPlan,
        semanticPayload: GPUDrawSemanticPayload? = packet.semanticPayload,
        dropUniformSlabSeal: Boolean = false,
        structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey? =
            packet.corePrimitivePreparedAuthority?.structuralPipelineKey,
        analyticClipUniformSeal: GPUCorePrimitiveAnalyticClipUniformSeal? =
            packet.corePrimitivePreparedAuthority?.analyticClipUniformSeal,
        analyticIntersectionUniformSeal: GPUCorePrimitiveAnalyticIntersectionUniformSeal? =
            packet.corePrimitivePreparedAuthority?.analyticIntersectionUniformSeal,
        clipStencilCandidate: GPUCorePrimitiveClipStencilPreparedCandidate? =
            packet.corePrimitiveClipStencilPreparedCandidate,
        uniformSlabSeal: GPUCorePrimitiveUniformSlabSeal? =
            packet.corePrimitivePreparedAuthority?.uniformSlabSeal,
        coverageMaskUniformSlabSeal: GPUCorePrimitiveCoverageMaskUniformSlabSeal? =
            packet.corePrimitivePreparedAuthority?.coverageMaskUniformSlabSeal,
        bindingLayoutHash: String = packet.bindingLayoutHash,
        uniformSlot: GPUUniformPayloadSlot? = packet.uniformSlot,
        resourceSlot: GPUResourceBindingSlot? = packet.resourceSlot,
    ): GPUDrawPacket {
        val cloned = GPUDrawPacket(
            packetId = packetId,
            commandIdValue = commandIdValue,
            analysisRecordId = analysisRecordId,
            passId = packet.passId,
            layerId = packet.layerId,
            bindingListId = packet.bindingListId,
            insertionReasonCode = packet.insertionReasonCode,
            sortKey = packet.sortKey,
            sortKeyPreimage = packet.sortKeyPreimage,
            renderStepId = packet.renderStepId,
            renderStepVersion = packet.renderStepVersion,
            role = role,
            blendPlan = blendPlan,
            renderPipelineKey = renderPipelineKey,
            computePipelineKey = packet.computePipelineKey,
            bindingLayoutHash = bindingLayoutHash,
            uniformSlot = uniformSlot,
            resourceSlot = resourceSlot,
            semanticPayload = semanticPayload,
            vertexSourceLabel = packet.vertexSourceLabel,
            scissorBoundsHash = scissorBoundsHash,
            targetStateHash = packet.targetStateHash,
            originalPaintOrder = packet.originalPaintOrder,
            resourceGeneration = packet.resourceGeneration,
            frameProvenance = packet.frameProvenance,
            clipCoveragePlan = packet.clipCoveragePlan,
            clipExecutionPlan = clipExecutionPlan,
            diagnostics = packet.diagnostics,
            clipProducerAuthority = packet.clipProducerAuthority,
        )
        val authority = packet.corePrimitivePreparedAuthority
        if (authority != null) {
            cloned.attachCorePrimitivePreparedAuthority(
                authority.copy(
                structuralPipelineKey = structuralPipelineKey ?: authority.structuralPipelineKey,
                renderPipelineKey = renderPipelineKey,
                uniformSlabSeal = uniformSlabSeal.takeUnless { dropUniformSlabSeal },
                analyticClipUniformSeal = analyticClipUniformSeal,
                analyticIntersectionUniformSeal = analyticIntersectionUniformSeal,
                coverageMaskUniformSlabSeal = coverageMaskUniformSlabSeal,
                ),
            )
        }
        clipStencilCandidate?.let(cloned::attachCorePrimitiveClipStencilPreparedCandidate)
        return cloned
    }

    private fun GPUCorePrimitiveClipStencilPreparedCandidate.copyForTest(
        contentKey: String = this.contentKey,
        planCanonicalIdentity: String = this.planCanonicalIdentity,
        producerPacketId: GPUDrawPacketID = this.producerPacketId,
        producerCommandId: Int = this.producerCommandId,
        producerNdcVertices: List<Float> = this.producerNdcVertices,
        producerContourStarts: List<Int> = this.producerContourStarts,
        producerFanVertices: List<Float> = this.producerFanVertices,
        producerFanIndices: List<Int> = this.producerFanIndices,
        producerStructuralKey: GPUCorePrimitiveRenderPipelineStructuralKey =
            this.producerStructuralKey,
        consumers: List<GPUCorePrimitiveClipStencilPreparedCandidate.Consumer> = this.consumers,
        attachmentLogicalReference: String = this.attachmentLogicalReference,
        attachmentWidth: Int = this.attachmentWidth,
        attachmentHeight: Int = this.attachmentHeight,
        attachmentSampleCount: Int = this.attachmentSampleCount,
    ) = GPUCorePrimitiveClipStencilPreparedCandidate(
        contentKey,
        planCanonicalIdentity,
        producerPacketId,
        producerCommandId,
        producerNdcVertices,
        producerContourStarts,
        producerFanVertices,
        producerFanIndices,
        producerStructuralKey,
        consumers,
        attachmentLogicalReference,
        attachmentWidth,
        attachmentHeight,
        attachmentSampleCount,
    )

    private fun GPUFramePlan.withClipStencilCandidate(
        candidate: GPUCorePrimitiveClipStencilPreparedCandidate,
    ): GPUFramePlan = candidateLocations().fold(this) { plan, packet ->
        plan.replacingCorePacket(
            packet,
            cloneCorePacket(packet, clipStencilCandidate = candidate),
        )
    }

    private fun GPUFramePlan.withCoverageMaskSeal(
        seal: GPUCorePrimitiveCoverageMaskUniformSlabSeal,
    ): GPUFramePlan = steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        .flatMap(GPUFrameStep.RenderPassStep::drawPackets)
        .filter {
            it.corePrimitivePreparedAuthority?.coverageMaskUniformSlabSeal != null
        }
        .fold(this) { plan, packet ->
            plan.replacingCorePacket(
                packet,
                cloneCorePacket(packet, coverageMaskUniformSlabSeal = seal),
            )
        }

    private fun GPUFramePlan.candidateLocations(): List<GPUDrawPacket> =
        steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            .flatMap(GPUFrameStep.RenderPassStep::drawPackets)
            .filter { it.corePrimitiveClipStencilPreparedCandidate != null }

    private fun GPUCorePrimitiveAnalyticClipUniformSeal.copyForTest(
        plan: GPUUniformSlabPlan = this.plan,
        slotIndex: Int = this.slotIndex,
        commandId: Int = this.commandId,
        packetId: GPUDrawPacketID = this.packetId,
        clipCanonicalIdentity: String = this.clipCanonicalIdentity,
        clipType: GPUCorePrimitiveRenderPipelineStructuralKey.ClipGeometry = this.clipType,
        clipBounds: List<Float> = this.clipBounds,
        clipRadii: List<Float> = this.clipRadii,
        antiAlias: Boolean = this.antiAlias,
        conservativeScissor: GPUPixelBounds = this.conservativeScissor,
        structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey = this.structuralPipelineKey,
        renderPipelineKey: GPURenderPipelineKey = this.renderPipelineKey,
        bindingLayoutHash: String = this.bindingLayoutHash,
        resourceGeneration: Long = this.resourceGeneration,
        payloadBytes: ByteArray = this.payloadBytesSnapshot(),
    ) = GPUCorePrimitiveAnalyticClipUniformSeal(
        plan,
        slotIndex,
        commandId,
        packetId,
        clipCanonicalIdentity,
        clipType,
        clipBounds,
        clipRadii,
        antiAlias,
        conservativeScissor,
        structuralPipelineKey,
        renderPipelineKey,
        bindingLayoutHash,
        resourceGeneration,
        payloadBytes,
    )

    private fun GPUCorePrimitiveAnalyticIntersectionUniformSeal.copyForTest(
        plan: GPUUniformSlabPlan = this.plan,
        slotIndex: Int = this.slotIndex,
        commandId: Int = this.commandId,
        packetId: GPUDrawPacketID = this.packetId,
        clipCanonicalIdentity: String = this.clipCanonicalIdentity,
        elements: List<GPUCorePrimitiveAnalyticIntersectionElementSeal> = this.elements,
        conservativeScissor: GPUPixelBounds = this.conservativeScissor,
        structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey = this.structuralPipelineKey,
        renderPipelineKey: GPURenderPipelineKey = this.renderPipelineKey,
        bindingLayoutHash: String = this.bindingLayoutHash,
        resourceGeneration: Long = this.resourceGeneration,
        payloadBytes: ByteArray = this.payloadBytesSnapshot(),
    ) = GPUCorePrimitiveAnalyticIntersectionUniformSeal(
        plan,
        slotIndex,
        commandId,
        packetId,
        clipCanonicalIdentity,
        elements,
        conservativeScissor,
        structuralPipelineKey,
        renderPipelineKey,
        bindingLayoutHash,
        resourceGeneration,
        payloadBytes,
    )

    private fun analyticIntersectionPlan(
        vararg elements: GPUClipAnalyticElement,
    ): GPUClipExecutionPlan = GPUClipExecutionPlan.AnalyticIntersection(elements.toList())

    private fun GPUUniformSlabPlan.copyForTest(
        planHash: String = this.planHash,
        sourceLabel: String = this.sourceLabel,
        deviceGeneration: Long = this.deviceGeneration,
        alignmentBytes: Long = this.alignmentBytes,
        totalBytes: Long = this.totalBytes,
        uploadBudgetBytes: Long = maxOf(this.uploadBudgetBytes, totalBytes),
        slots: List<org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabSlot> = this.slots,
    ) = GPUUniformSlabPlan(
        planHash,
        sourceLabel,
        deviceGeneration,
        alignmentBytes,
        totalBytes,
        uploadBudgetBytes,
        slots,
    )

    private fun GPUFramePlan.replacingCorePacket(
        original: GPUDrawPacket,
        replacement: GPUDrawPacket,
    ): GPUFramePlan {
        val originalRender = steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            .single { original in it.drawPackets }
        val packets = originalRender.drawPackets.map { packet ->
            if (packet === original) replacement else packet
        }
        val replacementRender = GPUFrameStep.RenderPassStep(
            originalRender.target,
            originalRender.loadStore,
            originalRender.samplePlan,
            originalRender.resourceUses,
            packets,
            originalRender.sourceTaskIds,
            originalRender.batches.map { batch ->
                GPUFrameRenderBatch(
                    batch.batchId,
                    batch.kind,
                    batch.packets.map { packet -> if (packet === original) replacement else packet },
                    batch.sourceTaskIds,
                )
            },
            originalRender.sampleContinuation,
            originalRender.depthStencilLoadStore,
        )
        return withSteps(steps.map { step -> if (step === originalRender) replacementRender else step })
    }

    private fun coreDirectPrepare(
        vertexBytes: Long = 32L,
        indexBytes: Long = 24L,
        uniformBytes: Long = 256L,
    ): GPUFrameStep.PrepareResourcesStep = GPUFrameStep.PrepareResourcesStep(
        requests = prepareScene().requests + listOf(
            GPUResourcePreparationRequest(
                resource = GPUFrameBufferRef("buffer.core.vertices"),
                descriptor = GPUFrameBufferDescriptor(vertexBytes, 4L),
                role = GPUFrameResourceRole.VertexData,
                usages = setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.Vertex),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = vertexBytes,
                diagnosticLabel = "core.vertices",
            ),
            GPUResourcePreparationRequest(
                resource = GPUFrameBufferRef("buffer.core.indices"),
                descriptor = GPUFrameBufferDescriptor(indexBytes, 4L),
                role = GPUFrameResourceRole.IndexData,
                usages = setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.Index),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = indexBytes,
                diagnosticLabel = "core.indices",
            ),
            GPUResourcePreparationRequest(
                resource = GPUFrameBufferRef("buffer.core.uniforms"),
                descriptor = GPUFrameBufferDescriptor(uniformBytes, 256L),
                role = GPUFrameResourceRole.UniformData,
                usages = setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.Uniform),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = uniformBytes,
                diagnosticLabel = "core.uniforms",
            ),
        ),
        sourceTaskIds = listOf(GPUTaskID("task.prepare")),
    )

    private fun renderStep(): GPUFrameStep.RenderPassStep {
        val packet = packet("packet.main", 1)
        return GPUFrameStep.RenderPassStep(
            target = GPUFrameTargetRef("target.scene"),
            loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store),
            samplePlan = org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan.SingleSampleFrame,
            drawPackets = listOf(packet),
            sourceTaskIds = listOf(GPUTaskID("task.render")),
            batches = listOf(batch("batch.main", packet, "task.render")),
        )
    }

    private fun solidRenderStep(
        payload: GPUDrawSemanticPayload?,
        commandId: Int = 31,
        packetUniformSlot: GPUUniformPayloadSlot? = payload?.payloadRef?.uniformSlot,
        renderStepIdentity: String = "rect.fill.coverage",
    ): GPUFrameStep.RenderPassStep {
        val packet = packet(
            id = "packet.solid",
            commandId = commandId,
            renderStepIdentity = renderStepIdentity,
            semanticPayload = payload,
            uniformSlot = packetUniformSlot,
        )
        return GPUFrameStep.RenderPassStep(
            target = GPUFrameTargetRef("target.scene"),
            loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
            samplePlan = org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan.SingleSampleFrame,
            drawPackets = listOf(packet),
            sourceTaskIds = listOf(GPUTaskID("task.render")),
            batches = listOf(batch("batch.solid", packet, "task.render")),
        )
    }

    private fun coreRenderStep(
        semantic: GPUDrawSemanticPayload.CorePrimitive?,
        blendPlan: GPUBlendPlan = coreBlend(GPUBlendMode.SRC_OVER),
        provenance: GPUFrameProvenance = GPUFrameProvenance.GmContent,
        clip: GPUClipCoveragePlan = GPUClipCoveragePlan.NoClip,
        renderPipelineKey: GPURenderPipelineKey? = null,
        bindingLayoutHash: String = CORE_PRIMITIVE_BINDING_LAYOUT_HASH,
        vertexSourceLabel: String = CORE_PRIMITIVE_VERTEX_SOURCE_LABEL,
        targetStateHash: String = CORE_PRIMITIVE_TARGET_STATE_HASH,
        scissorBoundsHash: String = corePrimitiveScissorAuthority(GPUPixelBounds(0, 0, 4, 4)),
        renderStepVersion: Int = 1,
        role: GPUDrawPacketRole = GPUDrawPacketRole.Shading,
        samplePlan: GPUSamplePlan = GPUSamplePlan.SingleSampleFrame,
        clipExecutionPlan: GPUClipExecutionPlan = GPUClipExecutionPlan.NoClip,
        packetId: String = "packet.core",
        packetCommandIdValue: Int? = null,
    ): GPUFrameStep.RenderPassStep {
        val packet = GPUDrawPacket(
            packetId = GPUDrawPacketID(packetId),
            commandIdValue = packetCommandIdValue ?: semantic?.payloadRef?.commandIdValue ?: 41,
            analysisRecordId = semantic?.analysisRecordId ?: "analysis.core",
            passId = "pass.core",
            layerId = "root",
            bindingListId = "bindings.core",
            insertionReasonCode = "ordered",
            sortKey = 1L,
            sortKeyPreimage = "paint-order:1",
            renderStepId = GPURenderStepID(CORE_PRIMITIVE_RENDER_STEP_IDENTITY),
            renderStepVersion = renderStepVersion,
            role = role,
            blendPlan = blendPlan,
            renderPipelineKey = renderPipelineKey ?: semantic?.let {
                corePrimitiveRenderPipelineKey(it, clipExecutionPlan, blendPlan)
            } ?: GPURenderPipelineKey("pipeline.core.missing-semantic"),
            bindingLayoutHash = bindingLayoutHash,
            uniformSlot = semantic?.payloadRef?.uniformSlot,
            semanticPayload = semantic,
            vertexSourceLabel = vertexSourceLabel,
            scissorBoundsHash = scissorBoundsHash,
            targetStateHash = targetStateHash,
            originalPaintOrder = 1,
            resourceGeneration = if (semantic == null) 1L else PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
            frameProvenance = provenance,
            clipCoveragePlan = clip,
            clipExecutionPlan = clipExecutionPlan,
        )
        return GPUFrameStep.RenderPassStep(
            target = GPUFrameTargetRef("target.scene"),
            loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store),
            samplePlan = samplePlan,
            resourceUses = if (
                semantic != null && validateCorePrimitiveDirectNativeRoute(
                    semantic,
                    org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveDirectClipAuthority(
                        clipExecutionPlan,
                        semantic.targetBounds,
                    ),
                    blendPlan,
                    samplePlan,
                    "rgba8unorm",
                ) is GPUCorePrimitiveDirectNativeRoute.Accepted
            ) {
                listOf(
                    GPUFrameResourceUse(
                        GPUFrameBufferRef("buffer.core.vertices"),
                        GPUFrameResourceRole.VertexData,
                        GPUFrameResourceUsage.Vertex,
                        GPUFrameResourceLifetime.FrameLocal,
                        write = false,
                    ),
                    GPUFrameResourceUse(
                        GPUFrameBufferRef("buffer.core.indices"),
                        GPUFrameResourceRole.IndexData,
                        GPUFrameResourceUsage.Index,
                        GPUFrameResourceLifetime.FrameLocal,
                        write = false,
                    ),
                    GPUFrameResourceUse(
                        GPUFrameBufferRef("buffer.core.uniforms"),
                        GPUFrameResourceRole.UniformData,
                        GPUFrameResourceUsage.Uniform,
                        GPUFrameResourceLifetime.FrameLocal,
                        write = false,
                    ),
                )
            } else {
                emptyList()
            },
            drawPackets = listOf(packet),
            sourceTaskIds = listOf(GPUTaskID("task.render")),
            batches = listOf(batch("batch.core", packet, "task.render")),
        )
    }

    private fun coreStencilFramePlan(
        producerTarget: GPUFrameTargetRef = GPUFrameTargetRef("target.scene"),
        depthStencilBounds: GPUPixelBounds = GPUPixelBounds(0, 0, 4, 4),
        producerGeneration: Long = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
        producerDepthStencilWrite: Boolean = true,
        producerAuthorityOverride: GPUClipStencilProducerPlan? = null,
        includeDependency: Boolean = true,
        invertDependency: Boolean = false,
        dependencyToken: String? = null,
        consumerBeforeProducer: Boolean = false,
        consumerDepthStencilLifetime: GPUFrameResourceLifetime = GPUFrameResourceLifetime.FrameLocal,
        producerLoadOperation: GPUClipStencilLoadOperation = GPUClipStencilLoadOperation.Clear,
        producerStoreOperation: GPUClipStencilStoreOperation = GPUClipStencilStoreOperation.Store,
        producerClearValue: UInt? = 0u,
        consumerPassOperation: GPUClipStencilOperation = GPUClipStencilOperation.Keep,
        consumerStoreOperation: GPUClipStencilStoreOperation = GPUClipStencilStoreOperation.Store,
        consumerHasClipMaskUse: Boolean = false,
        duplicateContradictoryEdge: Boolean = false,
        foreignDepthStencilWrite: Boolean = false,
    ): GPUFramePlan {
        val scene = GPUFrameTargetRef("target.scene")
        val depthStencil = GPUFrameTextureRef("texture.core.clip.depth-stencil")
        val targetBounds = GPUPixelBounds(0, 0, 4, 4)
        val plan = GPUClipExecutionPlan.StencilCoverage(
            contentKey = "clip.preflight.stencil",
            bounds = GPUPixelBounds(1, 1, 3, 3),
            sampleCount = 1,
            atomicGroup = GPUClipAtomicGroupID("atomic.preflight.stencil"),
            orderingToken = GPUClipOrderingToken("token.preflight.stencil"),
            producer = GPUClipStencilProducerPlan(
                geometry = GPUClipExecutionGeometry.Rect(GPUBounds(1f, 1f, 3f, 3f)),
                scissor = GPUPixelBounds(1, 1, 3, 3),
                fillRule = GPUClipFillRule.Winding,
                reference = 1u,
                compare = GPUClipStencilCompare.Always,
                frontPassOperation = GPUClipStencilOperation.Replace,
                backPassOperation = GPUClipStencilOperation.Replace,
                loadOperation = producerLoadOperation,
                storeOperation = producerStoreOperation,
                clearValue = producerClearValue,
            ),
            consumer = GPUClipStencilConsumerPlan(
                scissor = targetBounds,
                reference = 1u,
                compare = GPUClipStencilCompare.Equal,
                passOperation = consumerPassOperation,
                storeOperation = consumerStoreOperation,
            ),
        )
        val authority = GPUClipProducerAuthority.Stencil(producerAuthorityOverride ?: plan.producer)
        val producerPacket = GPUDrawPacket(
            packetId = GPUDrawPacketID("packet.core.clip.stencil"),
            commandIdValue = 41,
            analysisRecordId = "analysis.core.clip.stencil",
            passId = "pass.core.clip.stencil",
            layerId = "root",
            bindingListId = "bindings.core.clip.stencil",
            insertionReasonCode = "clip.stencil.producer",
            sortKey = 0L,
            sortKeyPreimage = "paint-order:0",
            renderStepId = GPURenderStepID("clip.stencil.producer"),
            renderStepVersion = 1,
            role = GPUDrawPacketRole.StencilProducer,
            blendPlan = coreColorWriteNoneBlend(),
            renderPipelineKey = corePrimitiveClipProducerPipelineKey(plan, authority),
            bindingLayoutHash = "layout.clip.stencil.producer.none",
            vertexSourceLabel = "clip-producer-authority",
            targetStateHash = "target.clip.stencil.producer.single-sample",
            originalPaintOrder = 0,
            resourceGeneration = producerGeneration,
            frameProvenance = GPUFrameProvenance.GmContent,
            clipCoveragePlan = GPUClipCoveragePlan.NoClip,
            clipExecutionPlan = plan,
            clipProducerAuthority = authority,
        )
        val producerTaskId = GPUTaskID("task.core.clip.stencil")
        val producer = GPUFrameStep.RenderPassStep(
            target = producerTarget,
            loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
            samplePlan = GPUSamplePlan.SingleSampleFrame,
            resourceUses = listOf(
                GPUFrameResourceUse(
                    depthStencil,
                    GPUFrameResourceRole.ClipDepthStencil,
                    GPUFrameResourceUsage.RenderAttachment,
                    GPUFrameResourceLifetime.FrameLocal,
                    producerDepthStencilWrite,
                ),
            ),
            drawPackets = listOf(producerPacket),
            sourceTaskIds = listOf(producerTaskId),
            batches = listOf(batch("batch.core.clip.stencil", producerPacket, producerTaskId.value)),
            depthStencilLoadStore = GPUDepthStencilLoadStorePlan.WritableStencil(
                GPUStencilLoadOperation.Clear,
                GPUStorePlan.Store,
                0u,
            ),
        )
        val semantic = coreSemantic(plan)
        val baseConsumer = coreRenderStep(semantic, clipExecutionPlan = plan)
        val consumer = GPUFrameStep.RenderPassStep(
            target = scene,
            loadStore = baseConsumer.loadStore,
            samplePlan = baseConsumer.samplePlan,
            resourceUses = listOf(
                GPUFrameResourceUse(
                    depthStencil,
                    GPUFrameResourceRole.ClipDepthStencil,
                    GPUFrameResourceUsage.RenderAttachment,
                    consumerDepthStencilLifetime,
                    false,
                ),
            ) + if (consumerHasClipMaskUse) listOf(
                GPUFrameResourceUse(
                    GPUFrameTargetRef("target.unexpected.clip-mask"),
                    GPUFrameResourceRole.ClipMask,
                    GPUFrameResourceUsage.TextureBinding,
                    GPUFrameResourceLifetime.FrameLocal,
                    false,
                ),
            ) else emptyList(),
            drawPackets = baseConsumer.drawPackets,
            sourceTaskIds = baseConsumer.sourceTaskIds,
            batches = baseConsumer.batches,
            depthStencilLoadStore = GPUDepthStencilLoadStorePlan.ReadOnlyKeep,
        )
        val scenePreparation = prepareScene().requests.single()
        val prepare = GPUFrameStep.PrepareResourcesStep(
            requests = listOf(
                scenePreparation,
                GPUResourcePreparationRequest(
                    depthStencil,
                    GPUFrameTextureDescriptor(
                        depthStencilBounds,
                        GPUColorFormat("depth24plus-stencil8"),
                        1,
                    ),
                    GPUFrameResourceRole.ClipDepthStencil,
                    setOf(GPUFrameResourceUsage.RenderAttachment),
                    GPUFrameResourceLifetime.FrameLocal,
                    corePrimitiveDepthStencilByteSize(depthStencilBounds, 1),
                    "core.clip.depth-stencil",
                ),
            ),
            sourceTaskIds = listOf(GPUTaskID("task.prepare.core.stencil")),
        )
        val dependency = GPUTaskDependency(
            if (invertDependency) consumer.sourceTaskIds.single() else producerTaskId,
            if (invertDependency) producerTaskId else consumer.sourceTaskIds.single(),
            "clip-producer-consumer",
            GPUTaskUseToken(dependencyToken ?: plan.orderingToken.value),
            "preserve.core-primitive.clip.producer-before-consumer",
            GPUTaskAtomicGroupID(plan.atomicGroup.value),
        )
        val foreign = coreRenderStep(coreSemantic()).let { base ->
            GPUFrameStep.RenderPassStep(
                target = base.target,
                loadStore = base.loadStore,
                samplePlan = base.samplePlan,
                resourceUses = listOf(
                    GPUFrameResourceUse(
                        depthStencil,
                        GPUFrameResourceRole.ClipDepthStencil,
                        GPUFrameResourceUsage.RenderAttachment,
                        GPUFrameResourceLifetime.FrameLocal,
                        true,
                    ),
                ),
                drawPackets = base.drawPackets,
                sourceTaskIds = base.sourceTaskIds,
                batches = base.batches,
                depthStencilLoadStore = GPUDepthStencilLoadStorePlan.WritableStencil(
                    GPUStencilLoadOperation.Load,
                    GPUStorePlan.Store,
                    null,
                ),
            )
        }
        val orderedSteps = when {
            consumerBeforeProducer -> listOf(prepare, consumer, producer)
            foreignDepthStencilWrite -> listOf(prepare, producer, foreign, consumer)
            else -> listOf(prepare, producer, consumer)
        }
        return framePlan(
            orderedSteps,
            dependencies = if (includeDependency) buildList {
                add(dependency)
                if (duplicateContradictoryEdge) add(dependency.copy(useToken = GPUTaskUseToken("token.conflict")))
            } else emptyList(),
        )
    }

    private fun coreMaskFramePlan(
        maskByteDelta: Long = 0,
        maskPreparationLifetime: GPUFrameResourceLifetime = GPUFrameResourceLifetime.FrameLocal,
        maskPreparationUsages: Set<GPUFrameResourceUsage> = setOf(
            GPUFrameResourceUsage.RenderAttachment,
            GPUFrameResourceUsage.TextureBinding,
        ),
        consumerUsage: GPUFrameResourceUsage = GPUFrameResourceUsage.TextureBinding,
        consumerWrite: Boolean = false,
        consumerLifetime: GPUFrameResourceLifetime = GPUFrameResourceLifetime.FrameLocal,
        firstProducerLoad: String = "clear",
        firstProducerClearColorLabel: String? = CORE_PRIMITIVE_MASK_CLEAR_COLOR_LABEL,
        producerStore: GPUStorePlan = GPUStorePlan.Store,
        includeChainDependency: Boolean = true,
        invertChainDependency: Boolean = false,
        dependencyToken: String? = null,
        reverseProducerSteps: Boolean = false,
        includeProducers: Boolean = true,
        sampleCount: Int = 1,
        depthStencilRequired: Boolean = false,
        secondProducerTargetMismatch: Boolean = false,
        maskDescriptorBoundsMismatch: Boolean = false,
        maskFormat: String = "rgba8unorm",
        duplicateContradictoryEdge: Boolean = false,
    ): GPUFramePlan {
        val scene = GPUFrameTargetRef("target.scene")
        val mask = GPUFrameTargetRef("target.core.clip.mask")
        val bounds = GPUPixelBounds(0, 0, 4, 4)
        val producers = listOf(
            GPUClipMaskProducerPlan(
                0,
                GPUClipExecutionGeometry.Rect(GPUBounds(0f, 0f, 4f, 4f)),
                GPUClipMaskCombine.Intersect,
                true,
            ),
            GPUClipMaskProducerPlan(
                1,
                GPUClipExecutionGeometry.Rect(GPUBounds(1f, 1f, 3f, 3f)),
                GPUClipMaskCombine.Difference,
                true,
            ),
        )
        val plan = GPUClipExecutionPlan.CoverageMask(
            contentKey = "clip.preflight.mask",
            bounds = bounds,
            sampleCount = sampleCount,
            depthStencilRequired = depthStencilRequired,
            orderingToken = GPUClipOrderingToken("token.preflight.mask"),
            producers = producers,
            consumer = GPUClipMaskConsumerPlan(),
        )
        val maskWrite = GPUFrameResourceUse(
            mask,
            GPUFrameResourceRole.ClipMask,
            GPUFrameResourceUsage.RenderAttachment,
            GPUFrameResourceLifetime.FrameLocal,
            true,
        )
        fun producerStep(index: Int): GPUFrameStep.RenderPassStep {
            val producer = producers[index]
            val authority = GPUClipProducerAuthority.Mask(producer)
            val taskId = GPUTaskID("task.core.clip.mask.$index")
            val packet = GPUDrawPacket(
                packetId = GPUDrawPacketID("packet.core.clip.mask.$index"),
                commandIdValue = 41,
                analysisRecordId = "analysis.core.clip.mask.$index",
                passId = "pass.core.clip.mask.$index",
                layerId = "root",
                bindingListId = "bindings.core.clip.mask.$index",
                insertionReasonCode = "clip.mask.producer.$index",
                sortKey = index.toLong(),
                sortKeyPreimage = "paint-order:$index",
                renderStepId = GPURenderStepID("clip.mask.producer"),
                renderStepVersion = 1,
                role = GPUDrawPacketRole.ClipProducer,
                blendPlan = coreMaskProducerBlend(producer.combine),
                renderPipelineKey = corePrimitiveClipProducerPipelineKey(plan, authority),
                bindingLayoutHash = "layout.clip.mask.producer.none",
                vertexSourceLabel = "clip-producer-authority",
                targetStateHash = "target.clip.mask.producer.single-sample",
                originalPaintOrder = index,
                resourceGeneration = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
                frameProvenance = GPUFrameProvenance.GmContent,
                clipCoveragePlan = GPUClipCoveragePlan.NoClip,
                clipExecutionPlan = plan,
                clipProducerAuthority = authority,
            )
            return GPUFrameStep.RenderPassStep(
                target = if (index == 1 && secondProducerTargetMismatch) {
                    GPUFrameTargetRef("target.core.clip.mask.substituted")
                } else mask,
                loadStore = GPULoadStorePlan(
                    if (index == 0) firstProducerLoad else "load",
                    producerStore,
                    if (index == 0) firstProducerClearColorLabel else null,
                ),
                samplePlan = GPUSamplePlan.SingleSampleFrame,
                resourceUses = listOf(maskWrite),
                drawPackets = listOf(packet),
                sourceTaskIds = listOf(taskId),
                batches = listOf(batch("batch.core.clip.mask.$index", packet, taskId.value)),
            )
        }
        val producerSteps = listOf(producerStep(0), producerStep(1))
        val baseConsumer = coreRenderStep(coreSemantic(plan), clipExecutionPlan = plan)
        val consumer = GPUFrameStep.RenderPassStep(
            target = scene,
            loadStore = baseConsumer.loadStore,
            samplePlan = baseConsumer.samplePlan,
            resourceUses = listOf(
                GPUFrameResourceUse(
                    mask,
                    GPUFrameResourceRole.ClipMask,
                    consumerUsage,
                    consumerLifetime,
                    consumerWrite,
                ),
            ),
            drawPackets = baseConsumer.drawPackets,
            sourceTaskIds = baseConsumer.sourceTaskIds,
            batches = baseConsumer.batches,
        )
        val prepare = GPUFrameStep.PrepareResourcesStep(
            requests = listOf(
                prepareScene().requests.single(),
                GPUResourcePreparationRequest(
                    mask,
                    GPUFrameTextureDescriptor(
                        if (maskDescriptorBoundsMismatch) GPUPixelBounds(0, 0, 3, 4) else bounds,
                        GPUColorFormat(maskFormat),
                        1,
                    ),
                    GPUFrameResourceRole.ClipMask,
                    maskPreparationUsages,
                    maskPreparationLifetime,
                    plan.resolvedBytes + maskByteDelta,
                    "core.clip.mask",
                ),
            ),
            sourceTaskIds = listOf(GPUTaskID("task.prepare.core.mask")),
        )
        val firstTask = producerSteps[0].sourceTaskIds.single()
        val secondTask = producerSteps[1].sourceTaskIds.single()
        val chain = GPUTaskDependency(
            if (invertChainDependency) secondTask else firstTask,
            if (invertChainDependency) firstTask else secondTask,
            "clip-producer-consumer",
            GPUTaskUseToken(dependencyToken ?: plan.orderingToken.value),
            "preserve.core-primitive.clip.mask-producer.0",
        )
        val consumerDependency = GPUTaskDependency(
            secondTask,
            consumer.sourceTaskIds.single(),
            "clip-producer-consumer",
            GPUTaskUseToken(plan.orderingToken.value),
            "preserve.core-primitive.clip.producer-before-consumer",
        )
        val orderedProducers = if (reverseProducerSteps) producerSteps.reversed() else producerSteps
        return framePlan(
            listOf(prepare) + (if (includeProducers) orderedProducers else emptyList()) + consumer,
            dependencies = if (includeProducers) {
                buildList {
                    if (includeChainDependency) add(chain)
                    if (duplicateContradictoryEdge) add(chain.copy(useToken = GPUTaskUseToken("token.conflict")))
                    add(consumerDependency)
                }
            } else emptyList(),
        )
    }

    private fun coreSemantic(
        clipExecutionPlan: GPUClipExecutionPlan = GPUClipExecutionPlan.NoClip,
        commandIdValue: Int = 41,
        blendMode: GPUBlendMode = GPUBlendMode.SRC_OVER,
        sourceFamily: GPUCorePrimitiveSourceFamily = GPUCorePrimitiveSourceFamily.Rect,
        geometry: GPUCorePrimitiveGeometryInput = GPUCorePrimitiveGeometryInput.Rect(1f, 1f, 3f, 3f),
        coverageMode: GPUCorePrimitiveCoverageMode = GPUCorePrimitiveCoverageMode.FullOrScissor,
    ): GPUDrawSemanticPayload.CorePrimitive {
        val blend = coreBlend(blendMode)
        return GPUCorePrimitivePayloadGatherer().gatherSemantic(
            GPUCorePrimitivePayloadInput(
                commandIdValue = commandIdValue,
                sourceFamily = sourceFamily,
                geometry = geometry,
                premultipliedRgba = listOf(0.25f, 0.5f, 0.75f, 1f),
                targetBounds = GPUPixelBounds(0, 0, 4, 4),
                scissorBounds = GPUPixelBounds(0, 0, 4, 4),
                clipCoveragePlan = GPUClipCoveragePlan.NoClip,
                clipExecutionPlanIdentity = clipExecutionPlan.canonicalIdentity(),
                blendPlanIdentity = blend.canonicalIdentity(),
                frameProvenance = GPUFrameProvenance.GmContent,
                coverageMode = coverageMode,
                analysisRecordId = when (sourceFamily) {
                    GPUCorePrimitiveSourceFamily.Rect -> "analysis.fill_rect.$commandIdValue"
                    GPUCorePrimitiveSourceFamily.RRect -> "analysis.fill_rrect.$commandIdValue"
                    else -> null
                },
                analysisCommandFamily = when (sourceFamily) {
                    GPUCorePrimitiveSourceFamily.Rect -> "FillRect"
                    GPUCorePrimitiveSourceFamily.RRect -> "FillRRect"
                    else -> null
                },
                rectRouteAuthority = if (sourceFamily == GPUCorePrimitiveSourceFamily.Rect) {
                    GPUCorePrimitiveRectRouteAuthority.RectAxisAligned
                } else {
                    null
                },
                rectGeometryAuthority = if (sourceFamily == GPUCorePrimitiveSourceFamily.Rect) {
                    rectGeometryAuthorityFixture(geometry as GPUCorePrimitiveGeometryInput.Rect)
                } else {
                    null
                },
                rrectGeometryAuthority = if (sourceFamily == GPUCorePrimitiveSourceFamily.RRect) {
                    rrectGeometryAuthorityFixture(geometry as GPUCorePrimitiveGeometryInput.RRect)
                } else {
                    null
                },
            ),
        )
    }

    private fun pathCoreSemantic(
        clipExecutionPlan: GPUClipExecutionPlan = GPUClipExecutionPlan.NoClip,
        coverageMode: GPUCorePrimitiveCoverageMode = GPUCorePrimitiveCoverageMode.Stencil1x,
    ): GPUDrawSemanticPayload.CorePrimitive = coreSemantic(
        clipExecutionPlan = clipExecutionPlan,
        commandIdValue = 72,
        sourceFamily = GPUCorePrimitiveSourceFamily.Path,
        geometry = GPUCorePrimitiveGeometryInput.TriangulatedPath(
            vertices = listOf(
                -1f, -1f, 1f, 1f, 3f, 1f,
                -1f, -1f, 3f, 1f, 2f, 3f,
                -1f, -1f, 2f, 3f, 1f, 1f,
            ),
            indices = (0..8).toList(),
            sourceContourStarts = listOf(0),
            sourceVertexCount = 3,
            coverBounds = GPUPixelBounds(0, 0, 4, 4),
            geometryMode = GPUCorePrimitiveGeometryMode.StencilEdgeFan,
            fillRule = GPUCorePrimitiveFillRule.Winding,
        ),
        coverageMode = coverageMode,
    )

    private fun GPUDrawSemanticPayload.CorePrimitive.withTargetBounds(
        targetBounds: GPUPixelBounds,
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
        coverageMode = coverageMode,
        analysisRecordId = analysisRecordId,
        analysisCommandFamily = analysisCommandFamily,
        rectRouteAuthority = rectRouteAuthority,
        rectGeometryAuthority = rectGeometryAuthority,
        rrectGeometryAuthority = rrectGeometryAuthority,
    )

    private fun rectGeometryAuthorityFixture(
        geometry: GPUCorePrimitiveGeometryInput.Rect,
    ) = corePrimitiveRectGeometryAuthority(
        GPURect(geometry.left, geometry.top, geometry.right, geometry.bottom),
        GPUTransformFacts.identity(),
    )

    private fun rrectGeometryAuthorityFixture(
        geometry: GPUCorePrimitiveGeometryInput.RRect,
    ): org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRRectGeometryAuthority {
        val source = geometry.toSourceRRect()
        val accepted = assertIs<GPURRectNormalizationResult.Accepted>(GPURRectNormalizer.normalize(source))
        return assertIs<GPUCorePrimitiveRRectGeometryAuthorityIssue.Issued>(
            corePrimitiveRRectGeometryAuthority(source, accepted, GPUTransformFacts.identity()),
        ).authority
    }

    private fun GPUCorePrimitiveGeometryInput.RRect.toSourceRRect(): GPURRect = GPURRect(
        rect = GPURect(left, top, right, bottom),
        topLeft = GPURRectCornerRadii(radii[0], radii[1]),
        topRight = GPURRectCornerRadii(radii[2], radii[3]),
        bottomRight = GPURRectCornerRadii(radii[4], radii[5]),
        bottomLeft = GPURRectCornerRadii(radii[6], radii[7]),
    )

    private fun coreBlend(mode: GPUBlendMode): GPUBlendPlan = GPUBlendPlan.FixedFunctionBlend(
        mode = mode,
        state = GPUFixedFunctionBlendState(
            stateId = if (mode == GPUBlendMode.SRC_OVER) "one_isa" else "state.${mode.name.lowercase()}",
            color = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            alpha = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            writeMask = "rgba",
        ),
        sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
    )

    private fun coreColorWriteNoneBlend(): GPUBlendPlan = GPUBlendPlan.FixedFunctionBlend(
        mode = GPUBlendMode.SRC,
        state = GPUFixedFunctionBlendState(
            stateId = "core-primitive-color-write-none",
            color = GPUFixedFunctionBlendComponent("zero", "one", "add"),
            alpha = GPUFixedFunctionBlendComponent("zero", "one", "add"),
            writeMask = "none",
        ),
        sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
    )

    private fun coreMaskProducerBlend(combine: GPUClipMaskCombine): GPUBlendPlan =
        GPUBlendPlan.FixedFunctionBlend(
            mode = if (combine == GPUClipMaskCombine.Difference) GPUBlendMode.DST_OUT else GPUBlendMode.DST_IN,
            state = GPUFixedFunctionBlendState(
                stateId = if (combine == GPUClipMaskCombine.Difference) {
                    "core-primitive-mask-dst-out"
                } else {
                    "core-primitive-mask-dst-in"
                },
                color = GPUFixedFunctionBlendComponent(
                    "zero",
                    if (combine == GPUClipMaskCombine.Difference) "one-minus-src-alpha" else "src-alpha",
                    "add",
                ),
                alpha = GPUFixedFunctionBlendComponent(
                    "zero",
                    if (combine == GPUClipMaskCombine.Difference) "one-minus-src-alpha" else "src-alpha",
                    "add",
                ),
                writeMask = "rgba",
            ),
            sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
        )

    private fun preflightSolid(
        payload: GPUDrawSemanticPayload,
        packetCommandId: Int = 31,
        packetUniformSlot: GPUUniformPayloadSlot? = payload.payloadRef.uniformSlot,
    ): Pair<GPUFramePreflightResult, List<String>> {
        val events = mutableListOf<String>()
        val result = preflighter(
            resources = RecordingResourceProvider(events),
            completion = RecordingCompletionProvider(events),
            surface = RecordingSurfaceProvider(events),
        ).preflight(
            framePlan(
                listOf(
                    prepareScene(),
                    solidRenderStep(payload, packetCommandId, packetUniformSlot),
                ),
            ),
        )
        return result to events.toList()
    }

    private fun assertSolidRefusalBeforeSideEffects(
        expectedCode: String,
        payload: GPUDrawSemanticPayload.SolidRect,
        packetUniformSlot: GPUUniformPayloadSlot? = payload.payloadRef.uniformSlot,
        packetRenderStepIdentity: String = "rect.fill.coverage",
    ) {
        val recordingEvents = mutableListOf<String>()
        val recordingResult = preflighter(
            resources = RecordingResourceProvider(recordingEvents),
            completion = RecordingCompletionProvider(recordingEvents),
            surface = RecordingSurfaceProvider(recordingEvents),
        ).preflight(
            framePlan(
                listOf(
                    prepareScene(),
                    solidRenderStep(
                        payload = payload,
                        packetUniformSlot = packetUniformSlot,
                        renderStepIdentity = packetRenderStepIdentity,
                    ),
                ),
            ),
        )
        assertEquals(
            expectedCode,
            assertIs<GPUFramePreflightResult.Refused>(recordingResult).diagnostic.code.value,
        )
        assertTrue(recordingEvents.isEmpty(), "pure validation side effects: $recordingEvents")

        val nativeEvents = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val concreteResources = GPUConcreteResourceProvider(leaseFactory = adapter)
        val nativeResult = preflighter(
            resources = concreteResources,
            completion = RecordingCompletionProvider(nativeEvents),
            surface = RecordingSurfaceProvider(nativeEvents),
            nativeBoundary = adapter.bindNativeFrameBoundary(
                concreteResources,
                RenderOnlyNativePayloadMaterializer(nativeEvents),
            ),
        ).preflight(
            framePlan(
                listOf(
                    prepareScene(),
                    solidRenderStep(
                        payload = payload,
                        packetUniformSlot = packetUniformSlot,
                        renderStepIdentity = packetRenderStepIdentity,
                    ),
                ),
            ),
        )
        assertEquals(
            expectedCode,
            assertIs<GPUFramePreflightResult.Refused>(nativeResult).diagnostic.code.value,
        )
        assertTrue(nativeEvents.isEmpty(), "native or ticket side effects: $nativeEvents")
        assertEquals(0, adapter.activePreparedNativeFramePayloadCount)
        assertEquals(0, concreteResources.pendingPhysicalReservationCount)
        assertTrue(concreteResources.telemetry.dumpEvents.isEmpty())
    }

    private fun solidSemantic(
        commandId: Int = 31,
        renderStepIdentity: String = "rect.fill.coverage",
    ): GPUDrawSemanticPayload.SolidRect = GPUSolidPayloadGatherer().gatherSemantic(
        GPUPayloadGatherPlan(
            planHash = "solid.gather",
            commandFamily = "FillRect",
            materialAssemblyHash = "solid.material",
            renderStepIdentity = renderStepIdentity,
            writePlanHash = "solid.write",
            bindingPlanHash = "solid.binding",
            uploadPlanHash = "solid.upload",
            dedupScope = "pass.solid",
        ),
        GPUMaterialPayload(
            materialKeyHash = "solid.material.key",
            payloadClass = "solid-rgba-rect",
            valueFacts = mapOf(
                "command.id" to commandId.toString(),
                "rect.left" to "1.0",
                "rect.top" to "2.0",
                "rect.right" to "3.0",
                "rect.bottom" to "4.0",
                "radii.topLeft" to "0.0",
                "radii.topRight" to "0.0",
                "radii.bottomRight" to "0.0",
                "radii.bottomLeft" to "0.0",
                "color.r" to "0.10",
                "color.g" to "0.20",
                "color.b" to "0.30",
                "color.a" to "1.0",
            ),
            resourceFacts = emptyMap(),
            diagnosticLabel = "solid.$commandId",
        ),
    )

    private fun packet(
        id: String,
        commandId: Int,
        passId: String = "pass.main",
        pipelineKey: String = "pipeline.fill",
        renderStepIdentity: String = "step.fill",
        semanticPayload: GPUDrawSemanticPayload? = null,
        uniformSlot: GPUUniformPayloadSlot? = null,
    ): GPUDrawPacket = GPUDrawPacket(
        packetId = GPUDrawPacketID(id),
        commandIdValue = commandId,
        analysisRecordId = "analysis.$id",
        passId = passId,
        layerId = "root",
        bindingListId = "bindings.$id",
        insertionReasonCode = "ordered",
        sortKey = commandId.toLong(),
        sortKeyPreimage = "sort.$id",
        renderStepId = GPURenderStepID(renderStepIdentity),
        renderStepVersion = 1,
        role = GPUDrawPacketRole.Shading,
        renderPipelineKey = GPURenderPipelineKey(pipelineKey),
        bindingLayoutHash = "layout.fill",
        uniformSlot = uniformSlot,
        semanticPayload = semanticPayload,
        vertexSourceLabel = "vertices.$id",
        targetStateHash = "target.state",
        originalPaintOrder = commandId,
        resourceGeneration = 1,
    )

    private fun msaaContinuationKey(
        samplePlan: GPUSamplePlan.MultisampleFrame = GPUSamplePlan.MultisampleFrame(4),
    ): GPUSampleContinuationKey = GPUSampleContinuationKey(
        target = GPUTargetIdentity("target.scene"),
        targetGeneration = 1,
        deviceGeneration = GPUDeviceGenerationID(7),
        colorFormat = GPUColorFormat("rgba8unorm"),
        colorInterpretation = GPUColorInterpretation("srgb-premul"),
        samplePlan = samplePlan,
        colorAttachment = GPUTargetIdentity("target.scene.msaa"),
        depthStencilAttachment = null,
    )

    private fun batch(id: String, packet: GPUDrawPacket, taskId: String): GPUFrameRenderBatch =
        GPUFrameRenderBatch(
            batchId = id,
            kind = GPUPassBatchKind.SolidFill,
            packets = listOf(packet),
            sourceTaskIds = listOf(GPUTaskID(taskId)),
        )

    private fun acquire(): GPUFrameStep.AcquireSurfaceOutput = GPUFrameStep.AcquireSurfaceOutput(
        descriptor = surfaceDescriptor(),
        sourceTaskIds = listOf(GPUTaskID("task.output")),
    )

    private fun surfaceBlit(): GPUFrameStep.SurfaceBlitRenderPassStep = GPUFrameStep.SurfaceBlitRenderPassStep(
        scene = GPUFrameTargetRef("target.scene"),
        output = GPUSurfaceOutputRef("surface.main"),
        sourceTaskIds = listOf(GPUTaskID("task.output")),
    )

    private fun present(): GPUFrameStep.PostSubmitPresentAction = GPUFrameStep.PostSubmitPresentAction(
        output = GPUSurfaceOutputRef("surface.main"),
        sourceTaskIds = listOf(GPUTaskID("task.output")),
    )

    private fun surfaceDescriptor(): GPUSurfaceOutputDescriptor = GPUSurfaceOutputDescriptor(
        output = GPUSurfaceOutputRef("surface.main"),
        width = 4,
        height = 4,
        format = GPUColorFormat("rgba8unorm"),
        targetGeneration = 1,
    )

    private fun framePlan(
        steps: List<GPUFrameStep>,
        capabilities: GPUCapabilities = capabilities(),
        dependencies: List<GPUTaskDependency> = emptyList(),
    ): GPUFramePlan {
        val frameId = GPUFrameID(7)
        val seal = GPUFrameCapabilitySeal.capture(frameId, GPUDeviceGenerationID(7), capabilities)
        attachCorePrimitiveBuilderAuthorities(steps, capabilities, seal.deviceGeneration.value)
        return GPUFramePlan(
            frameId = frameId,
            capabilitySeal = seal,
            recordingSeals = listOf(GPURecordingSeal(GPURecordingID("recording.main"), 0, "compat", "replay", seal.sealHash)),
            steps = steps,
            memoryBudget = budget(),
            diagnostics = emptyList(),
            dependencies = dependencies,
        )
    }

    private fun attachCorePrimitiveBuilderAuthorities(
        steps: List<GPUFrameStep>,
        capabilities: GPUCapabilities,
        deviceGeneration: Long,
    ) {
        val preparations = steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        steps.filterIsInstance<GPUFrameStep.RenderPassStep>().forEach { render ->
            val packets = render.drawPackets.filter { packet ->
                packet.role == GPUDrawPacketRole.Shading &&
                    packet.semanticPayload is GPUDrawSemanticPayload.CorePrimitive
            }
            if (packets.isEmpty()) return@forEach
            val direct = render.resourceUses.any { it.role == GPUFrameResourceRole.UniformData }
            val uniformSeal = if (direct) run seal@{
                val uniformPreparation = preparations.singleOrNull { it.role == GPUFrameResourceRole.UniformData }
                    ?: return@seal null
                val uniformDescriptor = uniformPreparation.descriptor as? GPUFrameBufferDescriptor
                    ?: return@seal null
                val limits = requireNotNull(capabilities.limits)
                val payloads = packets.mapNotNull { packet ->
                    val bytes = (packet.semanticPayload as GPUDrawSemanticPayload.CorePrimitive)
                        .payloadRef.uniformBlock?.bytes ?: return@mapNotNull null
                    GPUUniformSlabPayload(
                        "draw-${packet.commandIdValue}",
                        ByteArray(bytes.size) { index -> bytes[index].toByte() },
                    )
                }
                if (payloads.size != packets.size) return@seal null
                val plan = (
                    GPUUniformSlabPlanner.plan(
                        sourceLabel = "core-primitive-uniform-pass",
                        deviceGeneration = deviceGeneration,
                        alignmentBytes = limits.minUniformBufferOffsetAlignment,
                        uploadBudgetBytes = maxOf(uniformDescriptor.byteSize, 256L * packets.size),
                        payloads = payloads,
                        maxBufferSize = limits.maxBufferSize ?: Long.MAX_VALUE,
                        maxDynamicUniformBuffersPerPipelineLayout =
                            limits.maxDynamicUniformBuffersPerPipelineLayout ?: Long.MAX_VALUE,
                    ) as? GPUUniformSlabPlanningResult.Accepted
                    )?.plan ?: return@seal null
                val packed = ByteArray(plan.totalBytes.toInt())
                payloads.zip(plan.slots).forEach { (payload, slot) ->
                    payload.bytes.copyInto(packed, slot.alignedOffset.toInt())
                }
                return@seal GPUCorePrimitiveUniformSlabSeal(
                    plan,
                    packets.map(GPUDrawPacket::commandIdValue),
                    packed,
                )
            } else {
                null
            }
            packets.forEach { packet ->
                if (packet.corePrimitivePreparedAuthority != null) return@forEach
                val semantic = packet.semanticPayload as GPUDrawSemanticPayload.CorePrimitive
                val clip = packet.clipExecutionPlan ?: return@forEach
                val blend = packet.blendPlan ?: return@forEach
                val structuralKey = corePrimitiveRenderPipelineStructuralKey(semantic, clip, blend)
                val publicKey = structuralKey.stableRenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY)
                if (packet.renderPipelineKey == publicKey) {
                    packet.attachCorePrimitivePreparedAuthority(
                        GPUCorePrimitivePreparedPacketAuthority(structuralKey, publicKey, uniformSeal),
                    )
                }
            }
        }
    }

    private fun preflightContext(
        deviceGeneration: GPUDeviceGenerationID = GPUDeviceGenerationID(7),
        targetGeneration: Long = 1,
        resourceGeneration: Long = 1,
    ): GPUFramePreflightContext = GPUFramePreflightContext(
        targetId = "target.scene",
        deviceGeneration = deviceGeneration,
        targetGeneration = targetGeneration,
        resourceGenerations = listOf<GPUFrameResourceRef>(
            GPUFrameTargetRef("target.scene"),
            GPUFrameBufferRef("buffer.upload"),
            GPUFrameTextureRef("texture.copy"),
            GPUFrameBufferRef("buffer.readback"),
            GPUFrameBufferRef("buffer.core.vertices"),
            GPUFrameBufferRef("buffer.core.indices"),
            GPUFrameBufferRef("buffer.core.uniforms"),
        ).associateWith { resourceGeneration },
    )

    private fun clipPreflightContext(plan: GPUFramePlan): GPUFramePreflightContext =
        GPUFramePreflightContext(
            targetId = "target.scene",
            deviceGeneration = plan.capabilitySeal.deviceGeneration,
            targetGeneration = 1,
            resourceGenerations = plan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
                .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
                .associate { it.resource to 1L },
        )

    private fun capabilities(
        snapshotId: String = "capabilities.current",
        maxBufferSize: Long? = 1L shl 30,
    ): GPUCapabilities = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "unit", "adapter", "device"),
        facts = listOf(GPUCapabilityFact("limits", "test", "observed", true, "preflight")),
        snapshotId = snapshotId,
        limits = GPULimits(
            8192,
            256,
            256,
            maxBufferSize = maxBufferSize,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
        rendererFeatures = setOf(
            GPURendererFeature.RenderPass,
            GPURendererFeature.CopyUpload,
            GPURendererFeature.Readback,
        ),
    )

    private fun msaaCapabilities(depthStencil: Boolean): GPUCapabilities = capabilities().copy(
        textureFormatSampleSupport = GPUTextureFormatSampleSupport(
            buildMap {
                put(
                    GPUTextureFormat.RGBA8Unorm,
                    GPUTextureSampleCountSupport(
                        renderAttachmentSampleCounts = setOf(1, 4),
                        resolveSourceSampleCounts = setOf(4),
                    ),
                )
                if (depthStencil) {
                    put(
                        GPUTextureFormat.Depth24PlusStencil8,
                        GPUTextureSampleCountSupport(renderAttachmentSampleCounts = setOf(1, 4)),
                    )
                }
            },
        ),
    )

    private fun budget(): GPUFrameMemoryBudgetPlan = GPUFrameMemoryBudgetPlan(
        peakFrameTransientBytes = 64,
        targetResidentBytes = 64,
        categoryTotals = GPUFrameMemoryCategory.entries.associateWith { 0L },
        deviceLimitFacts = emptyList(),
        configuredAggregateBudgetBytes = 1L shl 30,
        diagnostic = null,
    )
}
