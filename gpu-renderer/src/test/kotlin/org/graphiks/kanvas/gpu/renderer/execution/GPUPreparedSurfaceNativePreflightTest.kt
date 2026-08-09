package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUTextureFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedImageUploadArtifact
import org.graphiks.kanvas.gpu.renderer.artifacts.toPreparedR8UploadArtifact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawImageRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.images.AlphaType
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactFactory
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactResult
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageOrientation
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProfile
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProvenance
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceClass
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceFormat
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceInput
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPUProvisionalRenderSegmentKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveDirectNativeRoute
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveUniformSlabSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandStream
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.passes.fromBatchPlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveFillRule
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawPayloadRef
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageGeometryClass
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImagePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImagePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageSampling
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageVertex
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedTextA8PayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedTextPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameRenderBatch
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameTaskListBuilder
import org.graphiks.kanvas.gpu.renderer.recording.GPURecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackLayout
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPUSurfaceOutputDescriptor
import org.graphiks.kanvas.gpu.renderer.recording.GPUSurfaceOutputRef
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskPhase
import org.graphiks.kanvas.gpu.renderer.recording.canonicalSnapshotHash
import org.graphiks.kanvas.gpu.renderer.resources.GPUBufferResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUImageBindingRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUImageFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceDiagnostic
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLease
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseCacheResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabSlot
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

class GPUPreparedSurfaceNativePreflightTest {
    @Test
    fun `multi key direct pass seal authority proves per key pipelines and one shared bind group layout`() {
        val generation = GPUDeviceGenerationID(23L)
        val limits = GPULimits(
            8192,
            256,
            256,
            maxBufferSize = 1L shl 30,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        )
        val srcOverKey = GPUCorePrimitiveRenderPipelineStructuralKey(
            shader = GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectGeometry,
            topology = GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList,
            blend = srcOverStructuralBlend(),
            clip = GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None,
        )
        val clearKey = srcOverKey.copy(blend = clearStructuralBlend())
        val plan = GPUUniformSlabPlan(
            planHash = "multi-key-plan",
            sourceLabel = "core-primitive-uniform-pass",
            deviceGeneration = generation.value,
            alignmentBytes = 256L,
            totalBytes = 512L,
            uploadBudgetBytes = 512L,
            slots = listOf(
                GPUUniformSlabSlot("draw-1", "payload-1", 32L, 0L, 256L),
                GPUUniformSlabSlot("draw-2", "payload-2", 32L, 256L, 256L),
            ),
        )
        val slab = GPUCorePrimitiveUniformSlabSeal(plan, listOf(1, 2), ByteArray(512))
        val seal = GPUCorePrimitiveMultiKeyDirectPreparedPassSeal(
            listOf(srcOverKey, clearKey),
            slab,
        )

        val accepted = assertIs<GPUCorePrimitiveMultiKeyDirectPassAuthorityValidation.Accepted>(
            validateMultiKeyDirectPassSealAuthority(
                seal,
                listOf(srcOverKey, clearKey),
                generation.value,
                limits,
            ),
        )
        assertEquals(listOf(srcOverKey, clearKey), seal.structuralPipelineKeys)
        assertEquals(2, accepted.pipelineMappings.size)
        assertEquals(2, accepted.cacheKeys.size)
        assertEquals(PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY, accepted.componentIdentity)
        assertEquals(
            GPUWgpu4kCorePrimitiveBlendProgram.PremulSrcOver,
            accepted.pipelineMappings[0].identity.blendProgram,
        )
        assertEquals(
            GPUWgpu4kCorePrimitiveBlendProgram.PremulClear,
            accepted.pipelineMappings[1].identity.blendProgram,
        )
        assertNotEquals(accepted.cacheKeys[0], accepted.cacheKeys[1])
    }

    @Test
    fun `multi key direct pass seal authority refuses stale slabs foreign keys and unmappable programs`() {
        val generation = GPUDeviceGenerationID(23L)
        val limits = GPULimits(
            8192,
            256,
            256,
            maxBufferSize = 1L shl 30,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        )
        val srcOverKey = GPUCorePrimitiveRenderPipelineStructuralKey(
            shader = GPUCorePrimitiveRenderPipelineStructuralKey.Shader.DirectGeometry,
            topology = GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList,
            blend = srcOverStructuralBlend(),
            clip = GPUCorePrimitiveRenderPipelineStructuralKey.Clip.None,
        )
        val clearKey = srcOverKey.copy(blend = clearStructuralBlend())
        fun slab(deviceGeneration: Long = generation.value) = GPUCorePrimitiveUniformSlabSeal(
            GPUUniformSlabPlan(
                planHash = "multi-key-plan",
                sourceLabel = "core-primitive-uniform-pass",
                deviceGeneration = deviceGeneration,
                alignmentBytes = 256L,
                totalBytes = 512L,
                uploadBudgetBytes = 512L,
                slots = listOf(
                    GPUUniformSlabSlot("draw-1", "payload-1", 32L, 0L, 256L),
                    GPUUniformSlabSlot("draw-2", "payload-2", 32L, 256L, 256L),
                ),
            ),
            listOf(1, 2),
            ByteArray(512),
        )
        val stale = assertIs<GPUCorePrimitiveMultiKeyDirectPassAuthorityValidation.Refused>(
            validateMultiKeyDirectPassSealAuthority(
                GPUCorePrimitiveMultiKeyDirectPreparedPassSeal(
                    listOf(srcOverKey, clearKey),
                    slab(deviceGeneration = generation.value + 1L),
                ),
                listOf(srcOverKey, clearKey),
                generation.value,
                limits,
            ),
        )
        assertEquals("invalid.native-core-primitive.multi-key-uniform", stale.code)
        val foreignKeys = assertIs<GPUCorePrimitiveMultiKeyDirectPassAuthorityValidation.Refused>(
            validateMultiKeyDirectPassSealAuthority(
                GPUCorePrimitiveMultiKeyDirectPreparedPassSeal(
                    listOf(srcOverKey, clearKey),
                    slab(),
                ),
                listOf(clearKey, srcOverKey),
                generation.value,
                limits,
            ),
        )
        assertEquals("invalid.native-core-primitive.multi-key-seal", foreignKeys.code)
        val unmappable = assertIs<GPUCorePrimitiveMultiKeyDirectPassAuthorityValidation.Refused>(
            validateMultiKeyDirectPassSealAuthority(
                GPUCorePrimitiveMultiKeyDirectPreparedPassSeal(
                    listOf(
                        srcOverKey,
                        srcOverKey.copy(
                            blend = GPUCorePrimitiveRenderPipelineStructuralKey.Blend.Unsupported(
                                GPUBlendMode.CLEAR,
                            ),
                        ),
                    ),
                    slab(),
                ),
                listOf(
                    srcOverKey,
                    srcOverKey.copy(
                        blend = GPUCorePrimitiveRenderPipelineStructuralKey.Blend.Unsupported(
                            GPUBlendMode.CLEAR,
                        ),
                    ),
                ),
                generation.value,
                limits,
            ),
        )
        assertEquals("unsupported.native-core-primitive.pipeline", unmappable.code)
    }

    @Test
    fun `direct scope authority matches per packet pipeline bridges for a mixed blend stream`() {
        val input = capturedPreparedSurfaceInputs(PreparedSurfaceFixtureShape.CoreImageText)
        val coreRender = input.framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            .single { render ->
                render.drawPackets.any { packet ->
                    packet.semanticPayload is GPUDrawSemanticPayload.CorePrimitive
                }
            }
        val sourceStepIndex = input.framePlan.steps.indexOf(coreRender)
        val coreScope = input.encoderPlan.scopes.single { scope ->
            scope.sourceStepIndex == sourceStepIndex
        }
        assertTrue(
            GPUPreparedSurfaceEncoderScopeAuthority.matches(
                input.framePlan,
                coreRender,
                coreScope,
                input.generationSeal,
            ),
        )
        val originalPacket = coreRender.drawPackets.single()
        val srcOverKey = requireNotNull(originalPacket.corePrimitivePreparedAuthority)
            .structuralPipelineKey
        val clearKey = srcOverKey.copy(blend = clearStructuralBlend())
        val clearPipelineKey = clearKey.stableRenderPipelineKey(
            org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_RENDER_PIPELINE_KEY,
        )
        val firstPacket = originalPacket
        val secondPacket = originalPacket.withPreparedSurfaceTestPipelineKey(
            packetId = GPUDrawPacketID("packet.prepared-surface.core.2"),
            commandIdValue = 99,
            renderPipelineKey = clearPipelineKey,
        )
        val packets = listOf(firstPacket, secondPacket)
        fun operand(
            kind: org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind,
            label: String,
        ) = org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandReference(
            label,
            kind,
            "descriptor.$label",
            input.generationSeal.deviceGeneration.value,
            "core.prepared-surface",
            listOf("render"),
            "frame-local",
        )
        val bridges = packets.flatMap { packet ->
            listOf(
                org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandOperandBridge(
                    packet.packetId,
                    "setRenderPipeline",
                    operand(
                        org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.RenderPipeline,
                        "pipeline.${packet.commandIdValue}",
                    ),
                ),
                org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandOperandBridge(
                    packet.packetId,
                    "setBindGroup",
                    operand(
                        org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.BindGroup,
                        "bind.${packet.commandIdValue}",
                    ),
                ),
                org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandOperandBridge(
                    packet.packetId,
                    "setVertexBuffer",
                    operand(
                        org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.VertexBuffer,
                        "vertex.${packet.commandIdValue}",
                    ),
                ),
                org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandOperandBridge(
                    packet.packetId,
                    "setIndexBuffer",
                    operand(
                        org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.IndexBuffer,
                        "index.${packet.commandIdValue}",
                    ),
                ),
            )
        }
        val batch = org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatch(
            batchId = coreRender.batches.single().batchId,
            packets = packets,
            kind = coreRender.batches.single().kind,
            targetStateHash = packets.first().targetStateHash,
            queueGuard = org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard(
                emptyList(),
                emptyList(),
            ),
        )
        val passPlan = org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchPlan(
            streamId = "frame.${input.framePlan.frameId.value}.step.$sourceStepIndex",
            passId = "frame.${input.framePlan.frameId.value}.render.$sourceStepIndex",
            batches = listOf(batch),
            cuts = emptyList(),
            diagnostics = emptyList(),
            inputPacketCount = packets.size,
        )
        val stream = GPUPassCommandStream.fromBatchPlan(
            streamId = "frame.${input.framePlan.frameId.value}.commands.$sourceStepIndex",
            batchPlan = passPlan,
            loadStoreLabel = "${coreRender.loadStore.loadOp}:${coreRender.loadStore.storePlan.name}:" +
                (coreRender.loadStore.clearColorLabel ?: "none"),
            operandBridge = bridges,
        )
        fun key(
            role: GPUPreparedNativeOperandRole,
            kind: GPUPreparedNativeOperandKind,
            binding: String,
        ) = GPUPreparedNativeOperandKey(
            role,
            kind,
            gpuPreparedNativeBindingKey(binding),
            GPUPreparedNativeOperandOwnership.Borrowed,
        )
        val expectedKeys = buildList {
            add(
                key(
                    GPUPreparedNativeOperandRole.RenderColorTarget,
                    GPUPreparedNativeOperandKind.TextureView,
                    coreScope.resourceGenerationLabels.first(),
                ),
            )
            packets.forEach { packet ->
                add(
                    key(
                        GPUPreparedNativeOperandRole.RenderPipeline,
                        GPUPreparedNativeOperandKind.RenderPipeline,
                        "setRenderPipeline:pipeline.${packet.commandIdValue}",
                    ),
                )
            }
            add(
                key(
                    GPUPreparedNativeOperandRole.RenderVertexBuffer,
                    GPUPreparedNativeOperandKind.Buffer,
                    "setVertexBuffer:vertex.${firstPacket.commandIdValue}",
                ),
            )
            add(
                key(
                    GPUPreparedNativeOperandRole.RenderIndexBuffer,
                    GPUPreparedNativeOperandKind.Buffer,
                    "setIndexBuffer:index.${firstPacket.commandIdValue}",
                ),
            )
            packets.forEach { packet ->
                add(
                    key(
                        GPUPreparedNativeOperandRole.RenderBindGroup,
                        GPUPreparedNativeOperandKind.BindGroup,
                        "setBindGroup:bind.${packet.commandIdValue}",
                    ),
                )
            }
        }
        val mutatedRender = GPUFrameStep.RenderPassStep(
            target = coreRender.target,
            loadStore = coreRender.loadStore,
            samplePlan = coreRender.samplePlan,
            resourceUses = coreRender.resourceUses,
            drawPackets = packets,
            sourceTaskIds = coreRender.sourceTaskIds,
            batches = listOf(
                GPUFrameRenderBatch(
                    batchId = batch.batchId,
                    kind = batch.kind,
                    packets = packets,
                    sourceTaskIds = coreRender.sourceTaskIds,
                ),
            ),
            sampleContinuation = coreRender.sampleContinuation,
            depthStencilLoadStore = coreRender.depthStencilLoadStore,
            preparedImageBindingsByPacketId = coreRender.preparedImageBindingsByPacketId,
        )
        val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(firstPacket.semanticPayload)
        fun classifiedRoute(packet: GPUDrawPacket) =
            assertIs<GPUCorePrimitiveDirectNativeRoute.Accepted>(
                org.graphiks.kanvas.gpu.renderer.recording.classifyCorePrimitiveDirectNativeRoute(
                    semantic,
                    requireNotNull(packet.clipExecutionPlan),
                    packet.blendPlan,
                    coreRender.samplePlan,
                    "rgba8unorm",
                ),
            )
        val originalSlab = assertIs<GPUCorePrimitiveNativeScopeUniformAuthority.Uniform32Slab>(
            (coreScope.corePrimitiveNativeScopeRouteSeal as
                GPUCorePrimitiveNativeScopeRouteSeal.Routes).uniformAuthority,
        ).seal
        val directSeal = GPUCorePrimitiveDirectNativeRouteSeal.Routes.snapshot(
            linkedMapOf(
                firstPacket.packetId to classifiedRoute(firstPacket),
                secondPacket.packetId to classifiedRoute(secondPacket),
            ),
            GPUCorePrimitiveDirectPreparedPassSeal(srcOverKey, originalSlab),
        )
        val mutatedScope = GPUCommandEncoderScopePlan(
            sourceStepIndex = coreScope.sourceStepIndex,
            operationKind = coreScope.operationKind,
            scopeLabel = coreScope.scopeLabel,
            sourceTaskIds = coreScope.sourceTaskIds,
            sourcePacketIds = packets.map { it.packetId },
            facadeOperationClasses = stream.commandLabels,
            targetGeneration = coreScope.targetGeneration,
            resourceGenerationLabels = coreScope.resourceGenerationLabels,
            passCommandStream = stream,
            corePrimitiveDirectNativeRouteSeal = directSeal,
            corePrimitiveNativeScopeRouteSeal = GPUCorePrimitiveNativeScopeRouteSeal.Empty,
            targetResource = coreScope.targetResource,
        ).attachNativeOperandKeys(expectedKeys)

        assertTrue(
            GPUPreparedSurfaceEncoderScopeAuthority.matches(
                input.framePlan,
                mutatedRender,
                mutatedScope,
                input.generationSeal,
            ),
        )
    }

    @Test
    fun `invalid WGSL crosses real native preflight as the canonical typed refusal`() {
        val fixture = preparedSurfacePreflightFixture(PreparedSurfaceFixtureShape.Mixed)

        val refused = requireNotNull(
            GPUPreparedSurfaceNativePreflight("@fragment fn broken(")
                .validateFramePlan(fixture.framePlan, fixture.context),
        )

        assertEquals(GPUPreparedImageRefusalCodes.WGSL_VALIDATION, refused.code)
        assertEquals("wgsl-validation", refused.facts["boundary"])
    }

    @Test
    fun `frame validation late binds prepared packet generation to active target`() {
        val fixture = preparedSurfacePreflightFixture(PreparedSurfaceFixtureShape.Mixed)
        val sceneTarget = fixture.framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .single { request -> request.role == GPUFrameResourceRole.SceneTarget }
            .resource
        val activeGeneration = 7L
        val context = GPUFramePreflightContext(
            targetId = fixture.context.targetId,
            deviceGeneration = fixture.context.deviceGeneration,
            targetGeneration = activeGeneration,
            resourceGenerations = fixture.context.resourceGenerations.mapValues { (resource, generation) ->
                if (resource == sceneTarget) activeGeneration else generation
            },
        )

        assertNull(
            GPUPreparedSurfaceNativePreflight().validateFramePlan(fixture.framePlan, context),
        )
    }

    @Test
    fun `accepted mixed preflight retains exact handle free frame resources scopes and run order`() {
        val input = capturePreparedSurfaceInputs()

        val result = GPUPreparedSurfaceNativePreflight().validate(
            input.framePlan,
            input.encoderPlan,
            input.resources,
            input.shaderContract,
            input.generationSeal,
        )
        val accepted = assertIs<GPUPreparedSurfaceNativePreflightResult.Accepted>(
            result,
            result.toString(),
        )

        assertEquals(input.framePlan.frameId, accepted.plan.frameId)
        assertEquals(input.encoderPlan.planId, accepted.plan.encoderPlanId)
        assertEquals(input.encoderPlan.contextIdentity, accepted.plan.contextIdentity)
        assertSame(input.resources, accepted.plan.resources)
        assertSame(input.generationSeal, accepted.plan.generationSeal)
        assertEquals(
            input.encoderPlan.scopes.map { scope ->
                GPUPreparedNativeScopeKey(
                    scope.sourceStepIndex,
                    scope.operationKind,
                    scope.resourceGenerationLabels,
                    scope.nativeOperandKeys,
                )
            },
            accepted.plan.exactScopeKeys,
        )
        assertEquals(
            listOf(
                GPUPreparedSurfaceNativeRunPlan.Core::class,
                GPUPreparedSurfaceNativeRunPlan.Image::class,
            ),
            accepted.plan.orderedRuns.map { run -> run::class },
        )
        val core = assertIs<GPUPreparedSurfaceNativeRunPlan.Core>(
            accepted.plan.orderedRuns.first(),
        ).plan
        val image = assertIs<GPUPreparedSurfaceNativeRunPlan.Image>(
            accepted.plan.orderedRuns.last(),
        ).plan
        assertIs<GPUPreparedSurfaceImageRenderRunPlan>(image)
        val coreRender = input.framePlan.steps
            .filterIsInstance<GPUFrameStep.RenderPassStep>()
            .single { render ->
                render.drawPackets.all {
                    it.semanticPayload is GPUDrawSemanticPayload.CorePrimitive
                }
            }
        val imageRender = input.framePlan.steps
            .filterIsInstance<GPUFrameStep.RenderPassStep>()
            .single { render ->
                render.drawPackets.all {
                    it.semanticPayload is GPUDrawSemanticPayload.SampledImage
                }
            }
        assertEquals(
            listOf(input.framePlan.steps.indexOf(coreRender)),
            core.sourceScopeIndices,
        )
        assertEquals(coreRender.drawPackets.map(GPUDrawPacket::packetId), core.packetIds)
        assertEquals(
            imageRender.drawPackets.map { packet -> packet.semanticPayload },
            image.packets,
        )
        val exactImageResource = input.framePlan.steps
            .filterIsInstance<GPUFrameStep.UploadResourceStep>()
            .single()
            .imageResourcePlan
        val imageFrame = accepted.plan.imageFrames.single()
        assertSame(exactImageResource, imageFrame.resourcePlan)
        assertEquals(
            listOf(input.framePlan.steps.indexOf(imageRender)),
            imageFrame.consumerRenderScopeIndices,
        )
        assertEquals(
            listOf(requireNotNull(exactImageResource).artifactKey),
            image.artifactKeys,
        )
        assertSame(imageRender, image.renderStep)
        assertEquals(imageRender.preparedImageBindingsByPacketId.values.toList(), image.orderedBindings)
        assertSame(coreRender, core.renderStep)
        assertEquals(
            coreRender.resourceUses.map { use -> use.resource },
            core.preparationRequests.map { request -> request.resource },
        )
        assertEquals(
            coreRender.resourceUses.map { use -> use.resource },
            core.resourceEvidences.map { evidence -> evidence.logicalResource },
        )
        assertEquals(
            listOf(input.framePlan.steps.indexOf(imageRender)),
            image.sourceScopeIndices,
        )
        val coreScope = input.encoderPlan.scopes.single { scope ->
            scope.sourceStepIndex == input.framePlan.steps.indexOf(coreRender)
        }
        assertSame(coreScope.corePrimitiveNativeScopeRouteSeal, core.routeSeal)
        assertEquals(coreRender.target, core.target)
        assertEquals(coreRender.loadStore, core.loadStore)
        assertEquals(
            GPUPreparedNativeScopeKey(
                coreScope.sourceStepIndex,
                coreScope.operationKind,
                coreScope.resourceGenerationLabels,
                coreScope.nativeOperandKeys,
            ),
            core.exactScopeKey,
        )
        assertEquals(coreRender.target, accepted.plan.sceneTarget)
        val readbackStep = input.framePlan.steps
            .filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
            .single()
        val readbackIndex = input.framePlan.steps.indexOf(readbackStep)
        val readbackOutput = input.resources.outputOwnedReadbacks.single()
        val readbackSeal = requireNotNull(accepted.plan.readback)
        assertEquals(readbackIndex, readbackSeal.sourceStepIndex)
        assertSame(readbackStep.request, readbackSeal.request)
        assertEquals(readbackStep.source, readbackSeal.source)
        assertEquals(readbackStep.staging, readbackSeal.staging)
        assertSame(readbackOutput.layout, readbackSeal.layout)
        assertSame(readbackOutput.stagingLease, readbackSeal.stagingLease)
        assertNull(accepted.plan.surfaceChain)
        assertTrue(
            input.framePlan.steps.none {
                it is GPUFrameStep.CopyDestinationStep ||
                    it is GPUFrameStep.CopyAsDrawMaterializationStep
            },
        )
    }

    @Test
    fun `accepted image only preflight retains one image run and no core route`() {
        val input = capturedPreparedSurfaceInputs(PreparedSurfaceFixtureShape.ImageOnly)

        val result = GPUPreparedSurfaceNativePreflight().validate(
            input.framePlan,
            input.encoderPlan,
            input.resources,
            input.shaderContract,
            input.generationSeal,
        )

        val accepted = assertIs<GPUPreparedSurfaceNativePreflightResult.Accepted>(
            result,
            result.toString(),
        )
        val image = assertIs<GPUPreparedSurfaceNativeRunPlan.Image>(
            accepted.plan.orderedRuns.single(),
        ).plan
        assertEquals(1, accepted.plan.imageFrames.size)
        assertEquals(1, image.packets.size)
        assertTrue(
            accepted.plan.orderedRuns.none { it is GPUPreparedSurfaceNativeRunPlan.Core },
        )
        assertEquals(
            input.encoderPlan.scopes.map { it.sourceStepIndex },
            accepted.plan.exactScopeKeys.map { it.sourceStepIndex },
        )
    }

    @Test
    fun `mixed Core image Text preflight retains the exact direct Core route seal`() {
        val input = capturedPreparedSurfaceInputs(PreparedSurfaceFixtureShape.CoreImageText)

        val accepted = assertIs<GPUPreparedSurfaceNativePreflightResult.Accepted>(
            GPUPreparedSurfaceNativePreflight().validate(
                input.framePlan,
                input.encoderPlan,
                input.resources,
                input.shaderContract,
                input.generationSeal,
            ),
        )
        val coreRun = accepted.plan.orderedRuns
            .filterIsInstance<GPUPreparedSurfaceNativeRunPlan.Core>()
            .single()
            .plan
        val coreSeal = assertIs<GPUCorePrimitiveNativeScopeRouteSeal.Routes>(
            coreRun.routeSeal,
        )
        val coreRender = input.framePlan.steps
            .filterIsInstance<GPUFrameStep.RenderPassStep>()
            .single { render ->
                render.drawPackets.all { packet ->
                    packet.semanticPayload is GPUDrawSemanticPayload.CorePrimitive
                }
            }

        assertEquals(coreRender.drawPackets.map(GPUDrawPacket::packetId), coreSeal.flattenedPacketIds)
        assertEquals(
            listOf(GPUCorePrimitiveNativeScopeRouteUnit.Direct::class),
            coreSeal.orderedUnits.map { unit -> unit::class },
        )
        assertSame(
            input.encoderPlan.scopes
                .single { scope ->
                    scope.sourceStepIndex == input.framePlan.steps.indexOf(coreRender)
                }
                .corePrimitiveNativeScopeRouteSeal,
            coreRun.routeSeal,
        )
    }

    @Test
    fun `mixed encoder plan refuses substituted identity scope envelope labels and operand topology`() {
        val input = capturedPreparedSurfaceInputs(
            PreparedSurfaceFixtureShape.Mixed,
            includeSurface = true,
        )
        val imageRenderIndex = input.framePlan.steps.indexOfFirst { step ->
            step is GPUFrameStep.RenderPassStep &&
                step.drawPackets.all {
                    it.semanticPayload is GPUDrawSemanticPayload.SampledImage
                }
        }
        val imageRenderScope = input.encoderPlan.scopes.single { scope ->
            scope.sourceStepIndex == imageRenderIndex
        }
        val uploadScope = input.encoderPlan.scopes.single { scope ->
            scope.operationKind == GPUEncoderOperationKind.Upload
        }
        val readbackScope = input.encoderPlan.scopes.single { scope ->
            scope.operationKind == GPUEncoderOperationKind.Readback
        }
        val surfaceScope = input.encoderPlan.scopes.single { scope ->
            scope.operationKind == GPUEncoderOperationKind.SurfaceBlit
        }
        val foreignStream = input.encoderPlan.scopes
            .single { scope ->
                scope.operationKind == GPUEncoderOperationKind.Render &&
                    scope.sourceStepIndex != imageRenderIndex
            }
            .passCommandStream
        val cases = listOf(
            input.copy(
                encoderPlan = input.encoderPlan.rebuilt(
                    planId = "frame.foreign",
                ),
            ),
            input.copy(
                encoderPlan = input.encoderPlan.rebuilt(
                    contextIdentity = "target.prepared-surface.foreign",
                ),
            ),
            input.copy(
                encoderPlan = input.encoderPlan.replacingScope(
                    imageRenderScope,
                    imageRenderScope.rebuilt(
                        scopeLabel = "step.foreign",
                    ),
                ),
            ),
            input.copy(
                encoderPlan = input.encoderPlan.replacingScope(
                    imageRenderScope,
                    imageRenderScope.rebuilt(sourcePacketIds = emptyList()),
                ),
            ),
            input.copy(
                encoderPlan = input.encoderPlan.replacingScope(
                    imageRenderScope,
                    imageRenderScope.rebuilt(
                        facadeOperationClasses = listOf("beginRenderPass", "endRenderPass"),
                    ),
                ),
            ),
            input.copy(
                encoderPlan = input.encoderPlan.replacingScope(
                    imageRenderScope,
                    imageRenderScope.rebuilt(passCommandStream = foreignStream),
                ),
            ),
            input.copy(
                encoderPlan = input.encoderPlan.replacingScope(
                    imageRenderScope,
                    imageRenderScope.rebuilt(
                        targetResource = GPUFrameTargetRef("target.prepared-surface.foreign"),
                    ),
                ),
            ),
            input.copy(
                encoderPlan = input.encoderPlan.replacingScope(
                    uploadScope,
                    uploadScope.rebuilt(
                        resourceGenerationLabels =
                            uploadScope.resourceGenerationLabels.mapIndexed { index, label ->
                                if (index == 0) "$label.foreign" else label
                            },
                    ),
                ),
            ),
            input.copy(
                encoderPlan = input.encoderPlan.replacingScope(
                    uploadScope,
                    uploadScope.rebuilt(
                        nativeOperandKeys = uploadScope.nativeOperandKeys.mapIndexed { index, key ->
                            if (index == 0) {
                                key.copy(role = GPUPreparedNativeOperandRole.CopySource)
                            } else {
                                key
                            }
                        },
                    ),
                ),
            ),
            input.copy(
                encoderPlan = input.encoderPlan.replacingScope(
                    imageRenderScope,
                    imageRenderScope.rebuilt(
                        nativeOperandKeys =
                            imageRenderScope.nativeOperandKeys.mapIndexed { index, key ->
                                if (index == 0) {
                                    key.copy(kind = GPUPreparedNativeOperandKind.Texture)
                                } else {
                                    key
                                }
                            },
                    ),
                ),
            ),
            input.copy(
                encoderPlan = input.encoderPlan.replacingScope(
                    readbackScope,
                    readbackScope.rebuilt(
                        nativeOperandKeys = readbackScope.nativeOperandKeys.map { key ->
                            if (key.role == GPUPreparedNativeOperandRole.ReadbackDestination) {
                                key.copy(
                                    ownership = GPUPreparedNativeOperandOwnership.Borrowed,
                                )
                            } else {
                                key
                            }
                        },
                    ),
                ),
            ),
            input.copy(
                encoderPlan = input.encoderPlan.replacingScope(
                    surfaceScope,
                    surfaceScope.rebuilt(
                        nativeOperandKeys =
                            surfaceScope.nativeOperandKeys.mapIndexed { index, key ->
                                if (index == 0) {
                                    key.copy(
                                        bindingKey =
                                            gpuPreparedNativeBindingKey("surface.foreign"),
                                    )
                                } else {
                                    key
                                }
                            },
                    ),
                ),
            ),
        )

        cases.forEachIndexed { index, candidate ->
            val refused = assertIs<GPUPreparedSurfaceNativePreflightResult.Refused>(
                GPUPreparedSurfaceNativePreflight().validate(
                    candidate.framePlan,
                    candidate.encoderPlan,
                    candidate.resources,
                    candidate.shaderContract,
                    candidate.generationSeal,
                ),
                "mutation $index",
            )
            assertEquals("invalid.prepared-surface.encoder-plan", refused.code, "mutation $index")
        }
    }

    @Test
    fun `mixed frame refuses non null clear labels local resolve and multisample runs`() {
        val input = capturePreparedSurfaceInputs()
        val cases = listOf(
            input.framePlan.withRenderMutation { index, render ->
                GPUFrameStep.RenderPassStep(
                    target = render.target,
                    loadStore = render.loadStore.copy(
                        clearColorLabel = "forged.clear.$index",
                    ),
                    samplePlan = render.samplePlan,
                    resourceUses = render.resourceUses,
                    drawPackets = render.drawPackets,
                    sourceTaskIds = render.sourceTaskIds,
                    batches = render.batches,
                    sampleContinuation = render.sampleContinuation,
                    depthStencilLoadStore = render.depthStencilLoadStore,
                    preparedImageBindingsByPacketId =
                        render.preparedImageBindingsByPacketId,
                )
            },
            input.framePlan.withRenderMutation { _, render ->
                GPUFrameStep.RenderPassStep(
                    target = render.target,
                    loadStore = render.loadStore,
                    samplePlan = GPUSamplePlan.LocalResolveApproximation(4),
                    resourceUses = render.resourceUses,
                    drawPackets = render.drawPackets,
                    sourceTaskIds = render.sourceTaskIds,
                    batches = render.batches,
                    sampleContinuation = null,
                    depthStencilLoadStore = render.depthStencilLoadStore,
                    preparedImageBindingsByPacketId =
                        render.preparedImageBindingsByPacketId,
                )
            },
            input.framePlan.withRenderMutation { _, render ->
                GPUFrameStep.RenderPassStep(
                    target = render.target,
                    loadStore = render.loadStore,
                    samplePlan = GPUSamplePlan.MultisampleFrame(4),
                    resourceUses = render.resourceUses,
                    drawPackets = render.drawPackets,
                    sourceTaskIds = render.sourceTaskIds,
                    batches = render.batches,
                    sampleContinuation = null,
                    depthStencilLoadStore = render.depthStencilLoadStore,
                    preparedImageBindingsByPacketId =
                        render.preparedImageBindingsByPacketId,
                )
            },
        )

        val expectedCodes = listOf(
            "invalid.prepared-surface.render-load-store",
            "unsupported.prepared-surface.sample-plan",
            "unsupported.prepared-surface.sample-plan",
        )
        cases.forEachIndexed { index, framePlan ->
            val refused = assertIs<GPUPreparedSurfaceNativePreflightResult.Refused>(
                GPUPreparedSurfaceNativePreflight().validateFramePlan(framePlan),
                "mutation $index",
            )
            assertEquals(expectedCodes[index], refused.code)
        }
    }

    @Test
    fun `mixed frame refuses duplicate image binding packet ids before map construction`() {
        val input = capturePreparedSurfaceInputs()
        val duplicated = input.framePlan.withPreparedImagePlanMutation { plan ->
            plan.copy(
                bindingRequests = plan.bindingRequests +
                    plan.bindingRequests.first(),
            )
        }

        val refused = assertIs<GPUPreparedSurfaceNativePreflightResult.Refused>(
            GPUPreparedSurfaceNativePreflight().validateFramePlan(duplicated),
        )

        assertEquals("invalid.prepared-surface.image-binding-duplicates", refused.code)
    }

    @Test
    fun `mixed frame refuses contradictory prepared image texture view and sampler facts`() {
        val input = capturePreparedSurfaceInputs()
        val cases = listOf(
            input.framePlan.withPreparedImagePlanMutation { plan ->
                plan.copy(
                    textureDescriptor = plan.textureDescriptor.copy(
                        format = "forged-rgba8",
                    ),
                )
            },
            input.framePlan.withPreparedImageBindingMutation { binding ->
                binding.copy(
                    texture = binding.texture.copy(format = "forged-rgba8"),
                )
            },
            input.framePlan.withPreparedImageBindingMutation { binding ->
                binding.copy(
                    view = binding.view.copy(
                        textureDescriptorHash = "forged-descriptor-hash",
                    ),
                )
            },
            input.framePlan.withPreparedImageBindingMutation { binding ->
                binding.copy(
                    sampler = binding.sampler.copy(addressModeU = "repeat"),
                )
            },
        )

        cases.forEachIndexed { index, framePlan ->
            val refused = assertIs<GPUPreparedSurfaceNativePreflightResult.Refused>(
                GPUPreparedSurfaceNativePreflight().validateFramePlan(framePlan),
                "mutation $index",
            )
            assertEquals("invalid.prepared-surface.image-physical", refused.code)
        }
    }

    @Test
    fun `mixed preflight supports both three run orders with one image frame resource`() {
        val cases = listOf(
            PreparedSurfaceFixtureShape.CoreImageCore to listOf(
                GPUPreparedSurfaceNativeRunPlan.Core::class,
                GPUPreparedSurfaceNativeRunPlan.Image::class,
                GPUPreparedSurfaceNativeRunPlan.Core::class,
            ),
            PreparedSurfaceFixtureShape.ImageCoreImage to listOf(
                GPUPreparedSurfaceNativeRunPlan.Image::class,
                GPUPreparedSurfaceNativeRunPlan.Core::class,
                GPUPreparedSurfaceNativeRunPlan.Image::class,
            ),
        )

        cases.forEach { (shape, expectedOrder) ->
            val input = capturedPreparedSurfaceInputs(shape)
            val accepted = assertIs<GPUPreparedSurfaceNativePreflightResult.Accepted>(
                GPUPreparedSurfaceNativePreflight().validate(
                    input.framePlan,
                    input.encoderPlan,
                    input.resources,
                    input.shaderContract,
                    input.generationSeal,
                ),
                shape.name,
            )

            assertEquals(expectedOrder, accepted.plan.orderedRuns.map { it::class })
            assertEquals(1, accepted.plan.imageFrames.size)
            val expectedImageRenderIndices = input.framePlan.steps.mapIndexedNotNull { index, step ->
                (step as? GPUFrameStep.RenderPassStep)
                    ?.takeIf { render ->
                        render.drawPackets.all {
                            it.semanticPayload is GPUDrawSemanticPayload.SampledImage
                        }
                    }
                    ?.let { index }
            }
            assertEquals(
                expectedImageRenderIndices,
                accepted.plan.imageFrames.single().consumerRenderScopeIndices,
            )
            val uploadPlan = requireNotNull(
                input.framePlan.steps
                    .filterIsInstance<GPUFrameStep.UploadResourceStep>()
                    .single()
                    .imageResourcePlan,
            )
            assertSame(uploadPlan, accepted.plan.imageFrames.single().resourcePlan)
            accepted.plan.orderedRuns
                .filterIsInstance<GPUPreparedSurfaceNativeRunPlan.Image>()
                .forEach { imageRun ->
                    assertIs<GPUPreparedSurfaceImageRenderRunPlan>(imageRun.plan)
                    assertEquals(
                        listOf(uploadPlan.artifactKey),
                        imageRun.plan.artifactKeys,
                    )
                    assertEquals(1, imageRun.plan.sourceScopeIndices.size)
                }
            assertSame(input.resources, accepted.plan.resources)
        }
    }

    @Test
    fun `mixed preflight supports indexed path and image run orders`() {
        val cases = listOf(
            PreparedSurfaceFixtureShape.PathImage,
            PreparedSurfaceFixtureShape.ImagePath,
            PreparedSurfaceFixtureShape.PathImagePath,
            PreparedSurfaceFixtureShape.DirectImagePath,
            PreparedSurfaceFixtureShape.PathImageDirect,
        )

        cases.forEach { shape ->
            val input = capturedPreparedSurfaceInputs(shape)
            val accepted = assertIs<GPUPreparedSurfaceNativePreflightResult.Accepted>(
                GPUPreparedSurfaceNativePreflight().validate(
                    input.framePlan,
                    input.encoderPlan,
                    input.resources,
                    input.shaderContract,
                    input.generationSeal,
                ),
                shape.name,
            )
            val coreRuns = accepted.plan.orderedRuns
                .filterIsInstance<GPUPreparedSurfaceNativeRunPlan.Core>()
            assertTrue(coreRuns.isNotEmpty(), shape.name)
            assertEquals(
                if (shape in setOf(
                        PreparedSurfaceFixtureShape.PathImagePath,
                        PreparedSurfaceFixtureShape.DirectImagePath,
                        PreparedSurfaceFixtureShape.PathImageDirect,
                    )
                ) {
                    2
                } else {
                    1
                },
                coreRuns.size,
            )
            coreRuns.forEach { run ->
                val units =
                    (run.plan.routeSeal as GPUCorePrimitiveNativeScopeRouteSeal.Routes).orderedUnits
                val hasPath = units.any {
                    it is GPUCorePrimitiveNativeScopeRouteUnit.PathPair
                }
                assertEquals(
                    hasPath,
                    run.plan.renderStep.resourceUses.any { use ->
                        use.role == GPUFrameResourceRole.PathDepthStencil
                    },
                )
            }
            val corePreparations = input.framePlan.steps
                .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
                .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
                .filter { request ->
                    request.role in setOf(
                        GPUFrameResourceRole.VertexData,
                        GPUFrameResourceRole.IndexData,
                        GPUFrameResourceRole.UniformData,
                        GPUFrameResourceRole.PathDepthStencil,
                    )
                }
            assertEquals(
                1,
                corePreparations.count {
                    it.role == GPUFrameResourceRole.PathDepthStencil
                },
            )
            val slab = assertIs<GPUCorePrimitiveNativeScopeUniformAuthority.Uniform32Slab>(
                (
                    coreRuns.first().plan.routeSeal as
                        GPUCorePrimitiveNativeScopeRouteSeal.Routes
                    ).uniformAuthority,
            ).seal
            assertTrue(coreRuns.all { run ->
                assertIs<GPUCorePrimitiveNativeScopeUniformAuthority.Uniform32Slab>(
                    (run.plan.routeSeal as GPUCorePrimitiveNativeScopeRouteSeal.Routes)
                        .uniformAuthority,
                ).seal === slab
            })
            assertEquals(
                slab.commandIds,
                coreRuns.flatMap { run ->
                    (run.plan.routeSeal as GPUCorePrimitiveNativeScopeRouteSeal.Routes)
                        .commandIds
                },
            )
            val imageResources = accepted.plan.imageFrames.flatMap { image ->
                image.resourcePlan.preparationRequests.map { it.resource }
            }.toSet()
            val coreResources = coreRuns.flatMap { run ->
                run.plan.preparationRequests.map { it.resource }
            }.toSet()
            assertTrue(imageResources.intersect(coreResources).isEmpty())
        }
    }

    @Test
    fun `mixed indexed path refuses D24 core uniform aliases and producer consumer roles`() {
        val input = capturedPreparedSurfaceInputs(
            PreparedSurfaceFixtureShape.PathImage,
        )
        val imageUniform = requireNotNull(
            input.framePlan.steps
                .filterIsInstance<GPUFrameStep.UploadResourceStep>()
                .single()
                .imageResourcePlan,
        ).uniformRef
        val aliases = listOf(
            input.framePlan.withCoreResourceAlias(
                GPUFrameResourceRole.PathDepthStencil,
                imageUniform,
            ),
            input.framePlan.withCoreResourceAlias(
                GPUFrameResourceRole.UniformData,
                imageUniform,
            ),
        )
        aliases.forEach { framePlan ->
            val refused = assertIs<GPUPreparedSurfaceNativePreflightResult.Refused>(
                GPUPreparedSurfaceNativePreflight().validate(
                    framePlan,
                    input.encoderPlan,
                    input.resources,
                    input.shaderContract,
                    input.generationSeal,
                ),
            )
            assertEquals("invalid.prepared-surface.encoder-plan", refused.code)
        }

        listOf(
            GPUDrawPacketRole.DepthOnly,
            GPUDrawPacketRole.StencilConsumer,
        ).forEach { role ->
            val refused = requireNotNull(
                GPUPreparedSurfaceNativePreflight().validateFramePlan(
                    input.framePlan.withFirstCorePacketRole(role),
                ),
            )
            assertEquals("unsupported.prepared-surface.core-route", refused.code)
        }
    }

    @Test
    fun `global mixed preflight refuses stale shader target upload binding core and non mixed shapes`() {
        val input = capturePreparedSurfaceInputs()
        val firstGeneration = input.generationSeal.resourceGenerations.entries.first()
        val staleGeneration = GPUPreparedGenerationSeal(
            deviceGeneration = input.generationSeal.deviceGeneration,
            targetGeneration = input.generationSeal.targetGeneration,
            resourceGenerations = input.generationSeal.resourceGenerations +
                (firstGeneration.key to firstGeneration.value + 1L),
            capabilitySealHash = input.generationSeal.capabilitySealHash,
        )
        val cases = listOf(
            "stale.prepared-surface.generation" to input.copy(
                generationSeal = staleGeneration,
            ),
            "invalid.prepared-surface.shader-contract" to input.copy(
                shaderContract = input.shaderContract.copy(sourceHash = "stale.shader"),
            ),
            "unsupported.prepared-surface.target-color" to input.copy(
                framePlan = input.framePlan.withSceneTargetFormat(GPUColorFormat.RGBA8Unorm),
            ),
            "unsupported.prepared_image.upload_layout" to input.copy(
                framePlan = input.framePlan.withInvalidPreparedImageUpload(),
            ),
            "unsupported.prepared_image.native-binding" to input.copy(
                framePlan = input.framePlan.withInvalidPreparedImageBinding(),
            ),
            "invalid.prepared-surface.image-artifact" to input.copy(
                framePlan = input.framePlan.withInvalidPreparedImageArtifactBytes(),
            ),
            "invalid.prepared-surface.image-artifact" to input.copy(
                framePlan = input.framePlan.withInvalidPreparedImageArtifactDimensions(),
            ),
            "invalid.prepared-surface.encoder-plan" to input.copy(
                encoderPlan = input.encoderPlan.withMissingCoreRoute(input.framePlan),
            ),
        )

        cases.forEach { (expectedCode, candidate) ->
            val refused = assertIs<GPUPreparedSurfaceNativePreflightResult.Refused>(
                GPUPreparedSurfaceNativePreflight().validate(
                    candidate.framePlan,
                    candidate.encoderPlan,
                    candidate.resources,
                    candidate.shaderContract,
                    candidate.generationSeal,
                ),
                expectedCode,
            )
            assertEquals(expectedCode, refused.code)
        }
    }

    @Test
    fun `global mixed preflight refuses command evidence not linked to exact encoder operands`() {
        val input = capturePreparedSurfaceInputs()
        val generation = input.generationSeal.deviceGeneration.value
        val ownerScope = input.resources.outputOwnedReadbacks.single().stagingLease.ownerScope
        val readback = input.resources.outputOwnedReadbacks.single()
        val validLease = GPUResourceLease(
            leaseId = "lease.prepared-surface.command",
            resourceKind = GPUResourceLeaseKind.BindGroup,
            deviceGeneration = generation,
            descriptorHash = "descriptor.prepared-surface.command",
            ownerScope = ownerScope,
            usageLabels = listOf("render"),
            releasePolicy = "submission-complete",
            cacheResult = GPUResourceLeaseCacheResult.Create,
        )
        val validTexture = GPUTextureResourceRef("texture.prepared-surface.command")
        val validBuffer = GPUBufferResourceRef("buffer.prepared-surface.command")
        val knownOperandLabel = input.encoderPlan.scopes
            .flatMap { scope -> scope.passCommandStream?.operandBridge.orEmpty() }
            .first()
            .operand
            .label
        val validDiagnostic = GPUResourceDiagnostic(
            code = "diagnostic.prepared-surface.command",
            resourceLabel = knownOperandLabel,
            message = "Known non-terminal command evidence.",
            terminal = false,
        )
        val validResources = input.resources.rebuilt(
            commandResourceLeases = listOf(validLease),
            commandTextureResources = listOf(validTexture),
            commandBufferResources = listOf(validBuffer),
            commandDiagnostics = listOf(validDiagnostic),
        )
        val validResult = GPUPreparedSurfaceNativePreflight().validate(
            input.framePlan,
            input.encoderPlan,
            validResources,
            input.shaderContract,
            input.generationSeal,
        )
        val artificialRefusal = assertIs<GPUPreparedSurfaceNativePreflightResult.Refused>(
            validResult,
            validResult.toString(),
        )
        assertEquals("invalid.prepared-surface.resources", artificialRefusal.code)

        val noReadbackInput = capturedPreparedSurfaceInputs(
            PreparedSurfaceFixtureShape.Mixed,
            includeReadback = false,
        )
        val noReadbackLease = validLease.copy(
            leaseId = "lease.prepared-surface.no-readback",
            deviceGeneration = noReadbackInput.generationSeal.deviceGeneration.value,
            ownerScope = "frame-preflight:no-readback",
        )
        val noReadbackResources = noReadbackInput.resources.rebuilt(
            commandResourceLeases = listOf(noReadbackLease),
        )
        val noReadbackResult = GPUPreparedSurfaceNativePreflight().validate(
            noReadbackInput.framePlan,
            noReadbackInput.encoderPlan,
            noReadbackResources,
            noReadbackInput.shaderContract,
            noReadbackInput.generationSeal,
        )
        val noReadbackRefused =
            assertIs<GPUPreparedSurfaceNativePreflightResult.Refused>(
                noReadbackResult,
                noReadbackResult.toString(),
            )
        assertEquals("invalid.prepared-surface.resources", noReadbackRefused.code)

        val forgedLease = validLease.copy(
            leaseId = "lease.prepared-surface.forged",
            cacheResult = GPUResourceLeaseCacheResult.Deferred,
        )
        val forgedDiagnostic = GPUResourceDiagnostic(
            code = "diagnostic.prepared-surface.forged",
            resourceLabel = "resource.prepared-surface.forged",
            message = "Forged non-terminal command evidence.",
            terminal = false,
        )
        val shiftedLayout = GPUReadbackLayout(
            width = readback.layout.width,
            height = readback.layout.height,
            bytesPerPixel = readback.layout.bytesPerPixel,
            copyBytesPerRowAlignment = readback.layout.copyBytesPerRowAlignment,
            unpaddedBytesPerRow = readback.layout.unpaddedBytesPerRow,
            paddedBytesPerRow = readback.layout.paddedBytesPerRow,
            rowsPerImage = readback.layout.rowsPerImage,
            bufferOffset = readback.layout.bufferOffset + 4L,
            totalBufferBytes = readback.layout.totalBufferBytes + 4L,
        )
        val cases = listOf(
            input.resources.rebuilt(
                ordinaryResources = input.resources.ordinaryResources.reversed(),
            ),
            validResources.rebuilt(
                commandResourceLeases = listOf(validLease, validLease),
            ),
            validResources.rebuilt(
                commandResourceLeases = listOf(forgedLease),
            ),
            validResources.rebuilt(
                commandTextureResources = listOf(validTexture, validTexture),
            ),
            validResources.rebuilt(
                commandBufferResources = listOf(validBuffer, validBuffer),
            ),
            validResources.rebuilt(
                commandDiagnostics = listOf(forgedDiagnostic),
            ),
            input.resources.rebuilt(
                outputOwnedReadbacks = listOf(readback.copy(layout = shiftedLayout)),
            ),
            input.resources.rebuilt(
                outputOwnedReadbacks = listOf(
                    readback.copy(
                        request = readback.request.copy(
                            sourceBounds = GPUPixelBounds(0, 0, 8, 8),
                        ),
                    ),
                ),
            ),
        )

        cases.forEachIndexed { index, resources ->
            val refused = assertIs<GPUPreparedSurfaceNativePreflightResult.Refused>(
                GPUPreparedSurfaceNativePreflight().validate(
                    input.framePlan,
                    input.encoderPlan,
                    resources,
                    input.shaderContract,
                    input.generationSeal,
                ),
                "mutation[$index]",
            )
            assertTrue(
                refused.code == "invalid.prepared-surface.resources" ||
                    refused.code == "invalid.prepared-surface.readback",
                "mutation[$index] escaped with ${refused.code}",
            )
        }
    }

    @Test
    fun `pure mixed frame validation seals optional surface suffix and exact readback request`() {
        val input = capturePreparedSurfaceInputs()
        val validSurface = input.framePlan.withSurfaceChain(
            targetGeneration = input.generationSeal.targetGeneration,
        )
        assertNull(
            GPUPreparedSurfaceNativePreflight().validateFramePlan(
                validSurface,
                preparedSurfacePreflightFixture(PreparedSurfaceFixtureShape.Mixed).context,
            ),
        )

        val cases = listOf(
            "invalid.prepared-surface.surface-chain" to
                validSurface.withMismatchedSurfacePresent(),
            "invalid.prepared-surface.surface-chain" to
                validSurface.withMismatchedSurfaceScene(),
            "invalid.prepared-surface.readback" to
                input.framePlan.withReadbackBounds(GPUPixelBounds(0, 0, 8, 8)),
        )
        cases.forEach { (expectedCode, framePlan) ->
            val refused = requireNotNull(
                GPUPreparedSurfaceNativePreflight().validateFramePlan(framePlan),
            ) { expectedCode }
            assertEquals(expectedCode, refused.code)
        }
    }

    @Test
    fun `accepted mixed preflight seals the exact optional surface suffix`() {
        val input = capturedPreparedSurfaceInputs(
            PreparedSurfaceFixtureShape.Mixed,
            includeSurface = true,
        )
        val result = GPUPreparedSurfaceNativePreflight().validate(
            input.framePlan,
            input.encoderPlan,
            input.resources,
            input.shaderContract,
            input.generationSeal,
        )
        val accepted = assertIs<GPUPreparedSurfaceNativePreflightResult.Accepted>(
            result,
            result.toString(),
        )
        val chain = requireNotNull(accepted.plan.surfaceChain)
        val acquire = input.framePlan.steps[chain.acquireStepIndex]
        val blit = input.framePlan.steps[chain.blitStepIndex]
        val present = input.framePlan.steps[chain.presentStepIndex]
        assertSame(
            assertIs<GPUFrameStep.AcquireSurfaceOutput>(acquire).descriptor,
            chain.descriptor,
        )
        assertEquals(assertIs<GPUFrameStep.SurfaceBlitRenderPassStep>(blit).scene, chain.scene)
        assertEquals(assertIs<GPUFrameStep.PostSubmitPresentAction>(present).output, chain.output)
        assertEquals(input.framePlan.steps.size - 3, chain.acquireStepIndex)
        assertEquals(
            chain.blitStepIndex,
            chain.exactBlitScopeKey.sourceStepIndex,
        )
    }

    @Test
    fun `frame preflight refuses forged sampled image clip and target authorities`() {
        val fixture = preparedSurfacePreflightFixture(PreparedSurfaceFixtureShape.Mixed)
        val imagePacket = fixture.framePlan.steps
            .filterIsInstance<GPUFrameStep.RenderPassStep>()
            .flatMap(GPUFrameStep.RenderPassStep::drawPackets)
            .single { packet ->
                packet.semanticPayload is GPUDrawSemanticPayload.SampledImage
            }
        val semantic = assertIs<GPUDrawSemanticPayload.SampledImage>(
            imagePacket.semanticPayload,
        )
        val forgedScissor = GPUPixelBounds(1, 1, 3, 3)
        val forgedSemantic = semantic.rebuiltForPreflight(
            targetBounds = semantic.targetBounds,
            scissorBounds = forgedScissor,
        )
        val forgedTargetSemantic = semantic.rebuiltForPreflight(
            targetBounds = GPUPixelBounds(0, 0, 8, 8),
            scissorBounds = GPUPixelBounds(0, 0, 8, 8),
        )
        val cases = listOf(
            fixture.framePlan.withFirstImagePacket {
                it.rebuiltForPreflight(
                    scissorBoundsHash = "forged.scissor.authority",
                )
            },
            fixture.framePlan.withFirstImagePacket {
                it.rebuiltForPreflight(
                    clipCoveragePlan = GPUClipCoveragePlan.Scissor(
                        GPUBounds(1f, 1f, 3f, 3f),
                    ),
                )
            },
            fixture.framePlan.withFirstImagePacket {
                it.rebuiltForPreflight(
                    clipExecutionPlan = GPUClipExecutionPlan.ScissorOnly(forgedScissor),
                )
            },
            fixture.framePlan.withFirstImagePacket {
                it.rebuiltForPreflight(semanticPayload = forgedSemantic)
            },
            fixture.framePlan.withFirstImagePacket {
                it.rebuiltForPreflight(semanticPayload = forgedTargetSemantic)
            },
        )

        cases.forEach { forged ->
            val refused = requireNotNull(
                GPUPreparedSurfaceNativePreflight().validateFramePlan(
                    forged,
                    fixture.context,
                ),
            )
            assertEquals("invalid.prepared-surface.image-scissor-authority", refused.code)
            assertEquals(
                "Prepared-image packet, semantic, and scene-target clip authorities must be exact.",
                refused.message,
            )
        }
    }
}

private fun GPUDrawSemanticPayload.SampledImage.rebuiltForPreflight(
    targetBounds: GPUPixelBounds,
    scissorBounds: GPUPixelBounds,
): GPUDrawSemanticPayload.SampledImage =
    GPUPreparedImagePayloadGatherer().gatherSemantic(
        GPUPreparedImagePayloadInput(
            payloadRef = payloadRef,
            artifact = artifact,
            geometry = geometry,
            sampling = sampling,
            tintPremultipliedRgba = tintPremultipliedRgba,
            atlasColorPremultipliedRgba = atlasColorPremultipliedRgba,
            atlasSourceBlend = atlasSourceBlend,
            targetBounds = targetBounds,
            scissorBounds = scissorBounds,
            blendPlanIdentity = blendPlanIdentity,
            frameProvenance = frameProvenance,
        ),
    )

private fun GPUFramePlan.withFirstImagePacket(
    transform: (GPUDrawPacket) -> GPUDrawPacket,
): GPUFramePlan {
    var changed = false
    return withRenderMutation { _, render ->
        val packets = render.drawPackets.map { packet ->
            if (!changed && packet.semanticPayload is GPUDrawSemanticPayload.SampledImage) {
                changed = true
                transform(packet)
            } else {
                packet
            }
        }
        GPUFrameStep.RenderPassStep(
            target = render.target,
            loadStore = render.loadStore,
            samplePlan = render.samplePlan,
            resourceUses = render.resourceUses,
            drawPackets = packets,
            sourceTaskIds = render.sourceTaskIds,
            batches = render.batches,
            sampleContinuation = render.sampleContinuation,
            depthStencilLoadStore = render.depthStencilLoadStore,
            preparedImageBindingsByPacketId = render.preparedImageBindingsByPacketId,
        )
    }.also { check(changed) }
}

private fun GPUDrawPacket.rebuiltForPreflight(
    semanticPayload: GPUDrawSemanticPayload? = this.semanticPayload,
    scissorBoundsHash: String? = this.scissorBoundsHash,
    clipCoveragePlan: GPUClipCoveragePlan? = this.clipCoveragePlan,
    clipExecutionPlan: GPUClipExecutionPlan? = this.clipExecutionPlan,
) = GPUDrawPacket(
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

internal enum class PreparedSurfaceFixtureShape {
    Mixed,
    CoreImageCore,
    ImageCoreImage,
    CoreImageText,
    PathImage,
    ImagePath,
    PathImagePath,
    DirectImagePath,
    PathImageDirect,
    ImageOnly,
}

internal data class PreparedSurfacePreflightFixture(
    val framePlan: GPUFramePlan,
    val capabilities: GPUCapabilities,
    val context: GPUFramePreflightContext,
)

internal fun preparedSurfacePreflightFixture(
    shape: PreparedSurfaceFixtureShape,
    includeReadback: Boolean = true,
): PreparedSurfacePreflightFixture {
    val capabilities = preparedSurfaceCapabilities()
    val commands = when (shape) {
        PreparedSurfaceFixtureShape.Mixed -> listOf(
            preparedSurfaceCoreCommand(0, 0),
            preparedSurfaceImageCommand(1, 1),
        )
        PreparedSurfaceFixtureShape.CoreImageCore -> listOf(
            preparedSurfaceCoreCommand(0, 0),
            preparedSurfaceImageCommand(1, 1),
            preparedSurfaceCoreCommand(2, 2),
        )
        PreparedSurfaceFixtureShape.ImageCoreImage -> listOf(
            preparedSurfaceImageCommand(0, 0),
            preparedSurfaceCoreCommand(1, 1),
            preparedSurfaceImageCommand(2, 2),
        )
        PreparedSurfaceFixtureShape.CoreImageText -> listOf(
            preparedSurfaceCoreCommand(0, 0),
            preparedSurfaceImageCommand(1, 1),
        )
        PreparedSurfaceFixtureShape.PathImage -> listOf(
            preparedSurfaceCoreCommand(0, 0),
            preparedSurfaceImageCommand(1, 1),
        )
        PreparedSurfaceFixtureShape.ImagePath -> listOf(
            preparedSurfaceImageCommand(0, 0),
            preparedSurfaceCoreCommand(1, 1),
        )
        PreparedSurfaceFixtureShape.PathImagePath -> listOf(
            preparedSurfaceCoreCommand(0, 0),
            preparedSurfaceImageCommand(1, 1),
            preparedSurfaceCoreCommand(2, 2),
        )
        PreparedSurfaceFixtureShape.DirectImagePath -> listOf(
            preparedSurfaceCoreCommand(0, 0),
            preparedSurfaceImageCommand(1, 1),
            preparedSurfaceCoreCommand(2, 2),
        )
        PreparedSurfaceFixtureShape.PathImageDirect -> listOf(
            preparedSurfaceCoreCommand(0, 0),
            preparedSurfaceImageCommand(1, 1),
            preparedSurfaceCoreCommand(2, 2),
        )
        PreparedSurfaceFixtureShape.ImageOnly -> listOf(
            preparedSurfaceImageCommand(0, 0),
        )
    }
    val recording = GPURecorder(
        GPURecordingID("recording.prepared-surface.native-preflight"),
        GPUFrameID(71),
        capabilities,
    ).apply {
        commands.forEach(::record)
    }.close()
    val clippedBase = recording.taskList.withPreparedSurfaceClipAuthority()
    val base = if (shape != PreparedSurfaceFixtureShape.CoreImageText) {
        clippedBase
    } else {
        val precedingRender = clippedBase.tasks.filterIsInstance<GPUTask.Render>().last()
        val textPacket = preparedTextPreflightPacket(
            commandId = 2,
            resourceGeneration = precedingRender.drawPackets.single().resourceGeneration,
        )
        GPUTaskList(
            frameId = clippedBase.frameId,
            capabilitySeal = clippedBase.capabilitySeal,
            recordingSeals = clippedBase.recordingSeals,
            expectedReplayKeyHash = clippedBase.expectedReplayKeyHash,
            tasks = clippedBase.tasks + GPUTask.Render(
                taskId = GPUTaskID("task.prepared-surface.text"),
                recordingId = precedingRender.recordingId,
                phase = GPUTaskPhase.Render,
                target = precedingRender.target,
                loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
                samplePlan = GPUSamplePlan.SingleSampleFrame,
                provisionalSegmentKey =
                    GPUProvisionalRenderSegmentKey("segment.prepared-surface.text"),
                drawPackets = listOf(textPacket),
                batchEligibilityByPacketId = mapOf(
                    textPacket.packetId to GPUPassBatchEligibility(
                        kind = GPUPassBatchKind.Isolated,
                        queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                    ),
                ),
            ),
            dependencies = clippedBase.dependencies,
            phaseOrder = clippedBase.phaseOrder,
            memoryBudget = clippedBase.memoryBudget,
            diagnostics = clippedBase.diagnostics,
        )
    }
    val pathCommandIds = when (shape) {
        PreparedSurfaceFixtureShape.PathImage -> setOf(0)
        PreparedSurfaceFixtureShape.ImagePath -> setOf(1)
        PreparedSurfaceFixtureShape.PathImagePath -> setOf(0, 2)
        PreparedSurfaceFixtureShape.DirectImagePath -> setOf(2)
        PreparedSurfaceFixtureShape.PathImageDirect -> setOf(0)
        else -> emptySet()
    }
    val semantics = base.tasks.filterIsInstance<GPUTask.Render>()
        .flatMap(GPUTask.Render::drawPackets)
        .associate { packet ->
            packet.commandIdValue to if (
                packet.renderStepId.value == "image.draw.texture_upload"
            ) {
                preparedSurfaceImageSemantic(base, packet.commandIdValue)
            } else if (packet.renderStepId.value == "text.a8_mask.sample") {
                val page = GPUPreparedTextPreflightFixture.baselinePage0()
                GPUPreparedTextPayloadGatherer().gather(
                    GPUPreparedTextA8PayloadInput(
                        commandIdValue = packet.commandIdValue,
                        atlas = page.toPreparedR8UploadArtifact(),
                        atlasGeneration = page.artifactKey.generation,
                        pageIndex = page.pageIndex,
                        instances = GPUPreparedTextPreflightFixture.baselineA8Instances(page),
                        material = GPUPreparedTextPreflightFixture.baselineMaterialProgram(),
                        deviceToLocal =
                            org.graphiks.kanvas.gpu.renderer.payloads
                                .GPUPreparedTextDeviceToLocalAffine(
                                    1f,
                                    0f,
                                    0f,
                                    0f,
                                    1f,
                                    0f,
                                ),
                        targetBounds = PREPARED_SURFACE_BOUNDS,
                        scissorBounds = PREPARED_SURFACE_BOUNDS,
                        clipIdentity = "clip:none",
                        blendPlanIdentity = requireNotNull(packet.blendPlan).canonicalIdentity(),
                        capabilitySnapshotHash = capabilities.canonicalSnapshotHash(),
                        frameProvenance = GPUFrameProvenance.GmContent,
                    ),
                )
            } else if (packet.commandIdValue in pathCommandIds) {
                preparedSurfacePathSemantic(base, packet.commandIdValue)
            } else {
                preparedSurfaceCoreSemantic(base, packet.commandIdValue)
            }
        }
    val target = GPUFrameTargetRef("target.prepared-surface")
    val build = GPUPreparedSurfaceFrameTaskListBuilder().build(
            GPUPreparedSurfaceFrameRequest(
                baseTaskList = base,
                capabilities = capabilities,
                target = target,
                targetBounds = PREPARED_SURFACE_BOUNDS,
                semanticsByCommandId = semantics,
                readbackRequestId = if (includeReadback) {
                    GPUReadbackRequestID("readback.prepared-surface")
                } else {
                    null
                },
                targetFormat = GPUColorFormat.RGBA8UnormSrgb,
            ),
        )
    val taskList = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
        build,
        build.toString(),
    ).taskList
    val framePlan = GPUFramePlanner.plan(taskList)
    val targetGeneration = taskList.tasks.filterIsInstance<GPUTask.Render>()
        .flatMap(GPUTask.Render::drawPackets)
        .first()
        .resourceGeneration
    val textGenerationByResource = buildMap {
        taskList.tasks.filterIsInstance<GPUTask.Upload>().forEach { upload ->
            upload.r8ResourcePlan?.let { plan ->
                put(plan.frameTextureRef, plan.artifactGeneration)
                put(plan.stagingRef, plan.artifactGeneration)
            }
        }
    }
    val resourceGenerations = taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
        .flatMap(GPUTask.PrepareResources::requests)
        .associate { request ->
            request.resource to if (request.role == GPUFrameResourceRole.SceneTarget) {
                targetGeneration
            } else if (request.resource in textGenerationByResource) {
                textGenerationByResource.getValue(request.resource)
            } else {
                5L
            }
        }
    return PreparedSurfacePreflightFixture(
        framePlan = framePlan,
        capabilities = capabilities,
        context = GPUFramePreflightContext(
            targetId = target.value,
            deviceGeneration = taskList.capabilitySeal.deviceGeneration,
            targetGeneration = targetGeneration,
            resourceGenerations = resourceGenerations,
        ),
    )
}

internal class CapturingPreparedNativeMaterializer(
    private val refusalCode: String = "test.prepared-surface.boundary",
    advertisePreparedSurfaceMixedSealed: Boolean = true,
) : GPUPreparedNativeFramePayloadMaterializer {
    override val capabilities: Set<GPUPreparedNativeFrameMaterializerCapability> =
        if (advertisePreparedSurfaceMixedSealed) {
            setOf(GPUPreparedNativeFrameMaterializerCapability.PreparedSurfaceMixedSealed)
        } else {
            emptySet()
        }

    var materializeCallCount: Int = 0
        private set
    var capturedFramePlan: GPUFramePlan? = null
        private set
    var capturedEncoderPlan: GPUCommandEncoderPlan? = null
        private set
    var capturedResources: GPUPreparedResourceSet? = null
        private set
    var capturedGenerationSeal: GPUPreparedGenerationSeal? = null
        private set

    override fun materializeReusable(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedNativeFramePayloadMaterialization {
        materializeCallCount += 1
        capturedFramePlan = framePlan
        capturedEncoderPlan = encoderPlan
        capturedResources = resources
        capturedGenerationSeal = generationSeal
        return GPUPreparedNativeFramePayloadMaterialization.Refused(
            refusalCode,
            "Test boundary captured the handle-free mixed-frame inputs.",
        )
    }

    override fun bindLateSurface(
        draft: GPUPreparedNativeFrameDraft,
        acquiredSurface: GPUAcquiredSurfaceOutput?,
    ): GPUPreparedNativeFrameLateSurfaceBinding =
        GPUPreparedNativeFrameLateSurfaceBinding.NotRequired
}

internal data class CapturedPreparedSurfaceInputs(
    val framePlan: GPUFramePlan,
    val encoderPlan: GPUCommandEncoderPlan,
    val resources: GPUPreparedResourceSet,
    val shaderContract: GPUPreparedImageShaderContract,
    val generationSeal: GPUPreparedGenerationSeal,
)

internal fun capturePreparedSurfaceInputs(): CapturedPreparedSurfaceInputs =
    capturedPreparedSurfaceInputs(PreparedSurfaceFixtureShape.Mixed)

internal fun capturedPreparedSurfaceInputs(
    shape: PreparedSurfaceFixtureShape,
    includeReadback: Boolean = true,
    includeSurface: Boolean = false,
): CapturedPreparedSurfaceInputs {
    val fixture = preparedSurfacePreflightFixture(shape, includeReadback)
    val framePlan = if (includeSurface) {
        fixture.framePlan.withSurfaceChain(fixture.context.surfaceGeneration)
    } else {
        fixture.framePlan
    }
    val adapter = GPURuntimeResourceAdapter()
    val resources = GPUConcreteResourceProvider(leaseFactory = adapter)
    val capture = CapturingPreparedNativeMaterializer()
    return try {
        val result = GPUFramePreflighter(
            context = fixture.context,
            capabilities = fixture.capabilities,
            resourceProvider = resources,
            completionProvider = PreparedSurfaceCompletionProvider,
            surfaceProvider = PreparedSurfaceNoSurfaceProvider,
            nativeBoundary = adapter.bindNativeFrameBoundary(resources, capture),
        ).preflight(framePlan)
        val refused = assertIs<GPUFramePreflightResult.Refused>(result)
        assertEquals(
            "test.prepared-surface.boundary",
            refused.diagnostic.code.value,
            refused.diagnostic.toString(),
        )
        CapturedPreparedSurfaceInputs(
            framePlan = requireNotNull(capture.capturedFramePlan),
            encoderPlan = requireNotNull(capture.capturedEncoderPlan),
            resources = requireNotNull(capture.capturedResources),
            shaderContract = assertIs<GPUPreparedImageShaderValidationResult.Ready>(
                validatePreparedImageShader(GPU_PREPARED_IMAGE_WGSL),
            ).shaderContract,
            generationSeal = requireNotNull(capture.capturedGenerationSeal),
        )
    } finally {
        adapter.close()
    }
}

private object PreparedSurfaceCompletionProvider : GPUQueueCompletionProvider {
    override fun reserveTicket(
        request: GPUQueueCompletionTicketRequest,
    ): GPUQueueCompletionTicketReservation = error(
        "Capturing mixed preflight must refuse before reserving a completion ticket",
    )

    override fun abandonReservedTicket(
        ticket: GPUQueueCompletionTicket,
    ): GPUQueueCompletionTicketAbandonResult = error(
        "Capturing mixed preflight never owns a completion ticket",
    )
}

private object PreparedSurfaceNoSurfaceProvider : GPUSurfaceOutputProvider {
    override fun acquire(
        request: GPUSurfaceAcquisitionRequest,
    ): GPUSurfaceAcquisitionResult = error(
        "Capturing mixed preflight must not acquire a surface",
    )

    override fun release(
        output: GPUAcquiredSurfaceOutput,
    ): GPUSurfaceReleaseResult = error(
        "Capturing mixed preflight never owns a surface",
    )
}

internal fun GPUFramePlan.withSceneTargetFormat(
    format: GPUColorFormat,
): GPUFramePlan = rebuilt(
    steps = steps.map { step ->
        if (step !is GPUFrameStep.PrepareResourcesStep) {
            step
        } else {
            GPUFrameStep.PrepareResourcesStep(
                requests = step.requests.map { request ->
                    if (request.role != GPUFrameResourceRole.SceneTarget) {
                        request
                    } else {
                        val descriptor =
                            assertIs<GPUFrameTextureDescriptor>(request.descriptor)
                        request.rebuilt(descriptor.copy(format = format))
                    }
                },
                sourceTaskIds = step.sourceTaskIds,
            )
        }
    },
)

private fun GPUFramePlan.withInvalidPreparedImageUpload(): GPUFramePlan =
    rebuilt(
        steps = steps.map { step ->
            if (step !is GPUFrameStep.UploadResourceStep ||
                step.imageResourcePlan == null
            ) {
                step
            } else {
                val plan = requireNotNull(step.imageResourcePlan)
                val invalidLayout = plan.uploadTaskLayout.copy(
                    bytesPerRow = plan.uploadTaskLayout.bytesPerRow + 256L,
                )
                GPUFrameStep.UploadResourceStep(
                    staging = step.staging,
                    destination = step.destination,
                    layout = invalidLayout,
                    sourceTaskIds = step.sourceTaskIds,
                    textureResourcePlan = plan.copy(uploadTaskLayout = invalidLayout),
                )
            }
        },
    )

private fun GPUFramePlan.withPreparedImagePlanMutation(
    transform: (GPUImageFrameResourcePlan) -> GPUImageFrameResourcePlan,
): GPUFramePlan = rebuilt(
    steps = steps.map { step ->
        if (step !is GPUFrameStep.UploadResourceStep ||
            step.imageResourcePlan == null
        ) {
            step
        } else {
            GPUFrameStep.UploadResourceStep(
                staging = step.staging,
                destination = step.destination,
                layout = step.layout,
                sourceTaskIds = step.sourceTaskIds,
                textureResourcePlan = transform(requireNotNull(step.imageResourcePlan)),
            )
        }
    },
)

private fun GPUFramePlan.withPreparedImageBindingMutation(
    transform: (GPUImageBindingRequest) -> GPUImageBindingRequest,
): GPUFramePlan {
    val transformedByPacketId = steps
        .filterIsInstance<GPUFrameStep.UploadResourceStep>()
        .mapNotNull(GPUFrameStep.UploadResourceStep::imageResourcePlan)
        .flatMap(GPUImageFrameResourcePlan::bindingRequests)
        .associate { binding -> binding.packetId to transform(binding) }
    return rebuilt(
        steps = steps.map { step ->
            when (step) {
                is GPUFrameStep.UploadResourceStep -> {
                    val plan = step.imageResourcePlan
                    if (plan == null) {
                        step
                    } else {
                        GPUFrameStep.UploadResourceStep(
                            staging = step.staging,
                            destination = step.destination,
                            layout = step.layout,
                            sourceTaskIds = step.sourceTaskIds,
                            textureResourcePlan = plan.copy(
                                bindingRequests = plan.bindingRequests.map { binding ->
                                    transformedByPacketId.getValue(binding.packetId)
                                },
                            ),
                        )
                    }
                }
                is GPUFrameStep.RenderPassStep -> GPUFrameStep.RenderPassStep(
                    target = step.target,
                    loadStore = step.loadStore,
                    samplePlan = step.samplePlan,
                    resourceUses = step.resourceUses,
                    drawPackets = step.drawPackets,
                    sourceTaskIds = step.sourceTaskIds,
                    batches = step.batches,
                    sampleContinuation = step.sampleContinuation,
                    depthStencilLoadStore = step.depthStencilLoadStore,
                    preparedImageBindingsByPacketId =
                        step.preparedImageBindingsByPacketId.mapValues { (packetId, binding) ->
                            transformedByPacketId[packetId.value] ?: binding
                        },
                )
                else -> step
            }
        },
    )
}

private fun GPUFramePlan.withInvalidPreparedImageBinding(): GPUFramePlan =
    rebuilt(
        steps = steps.map { step ->
            if (step !is GPUFrameStep.RenderPassStep ||
                step.drawPackets.none {
                    it.semanticPayload is GPUDrawSemanticPayload.SampledImage
                }
            ) {
                step
            } else {
                GPUFrameStep.RenderPassStep(
                    target = step.target,
                    loadStore = step.loadStore,
                    samplePlan = step.samplePlan,
                    resourceUses = step.resourceUses,
                    drawPackets = step.drawPackets,
                    sourceTaskIds = step.sourceTaskIds,
                    batches = step.batches,
                    sampleContinuation = step.sampleContinuation,
                    depthStencilLoadStore = step.depthStencilLoadStore,
                    preparedImageBindingsByPacketId =
                        step.preparedImageBindingsByPacketId.mapValues { (_, binding) ->
                            binding.copy(bindingLayoutHash = "stale.binding")
                        },
                )
            }
        },
    )

private fun GPUFramePlan.withInvalidPreparedImageArtifactBytes(): GPUFramePlan =
    withPreparedImageArtifact { artifact ->
        val forgedBytes = artifact.tightRgba8BytesForUpload().also { bytes ->
            bytes[0] = (bytes[0].toInt() xor 0x7f).toByte()
        }
        artifact.rebuilt(rgba8UploadBytes = forgedBytes)
    }

private fun GPUFramePlan.withInvalidPreparedImageArtifactDimensions(): GPUFramePlan =
    withPreparedImageArtifact { artifact ->
        artifact.rebuilt(width = artifact.width + 1)
    }

private fun GPUFramePlan.withPreparedImageArtifact(
    transform: (GPUPreparedImageUploadArtifact) -> GPUPreparedImageUploadArtifact,
): GPUFramePlan = rebuilt(
    steps = steps.map { step ->
        if (step !is GPUFrameStep.RenderPassStep ||
            step.drawPackets.none {
                it.semanticPayload is GPUDrawSemanticPayload.SampledImage
            }
        ) {
            step
        } else {
            val packets = step.drawPackets.map { packet ->
                val semantic = packet.semanticPayload as?
                    GPUDrawSemanticPayload.SampledImage
                    ?: return@map packet
                packet.withSemantic(
                    GPUDrawSemanticPayload.SampledImage(
                        GPUPreparedImagePayloadInput(
                            payloadRef = semantic.payloadRef,
                            artifact = transform(semantic.artifact),
                            geometry = semantic.geometry,
                            sampling = semantic.sampling,
                            tintPremultipliedRgba = semantic.tintPremultipliedRgba,
                            atlasColorPremultipliedRgba =
                                semantic.atlasColorPremultipliedRgba,
                            atlasSourceBlend = semantic.atlasSourceBlend,
                            targetBounds = semantic.targetBounds,
                            scissorBounds = semantic.scissorBounds,
                            blendPlanIdentity = semantic.blendPlanIdentity,
                            frameProvenance = semantic.frameProvenance,
                        ),
                    ),
                )
            }
            GPUFrameStep.RenderPassStep(
                target = step.target,
                loadStore = step.loadStore,
                samplePlan = step.samplePlan,
                resourceUses = step.resourceUses,
                drawPackets = packets,
                sourceTaskIds = step.sourceTaskIds,
                batches = step.batches,
                sampleContinuation = step.sampleContinuation,
                depthStencilLoadStore = step.depthStencilLoadStore,
                preparedImageBindingsByPacketId =
                    step.preparedImageBindingsByPacketId,
            )
        }
    },
)

private fun GPUPreparedImageUploadArtifact.rebuilt(
    width: Int = this.width,
    rgba8UploadBytes: ByteArray = tightRgba8BytesForUpload(),
): GPUPreparedImageUploadArtifact = GPUPreparedImageUploadArtifact(
    key = key,
    width = width,
    height = height,
    pixelLayout = pixelLayout,
    sourceGeneration = sourceGeneration,
    contentHash = contentHash,
    alphaOnly = alphaOnly,
    colorInterpretation = colorInterpretation,
    colorUploadEncoding = colorUploadEncoding,
    colorUploadInterpretation = colorUploadInterpretation,
    rgba8UploadBytes = rgba8UploadBytes,
)

private fun GPUDrawPacket.withSemantic(
    semantic: GPUDrawSemanticPayload,
): GPUDrawPacket = GPUDrawPacket(
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
    semanticPayload = semantic,
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

private fun GPUCommandEncoderPlan.withMissingCoreRoute(
    framePlan: GPUFramePlan,
): GPUCommandEncoderPlan {
    val coreStepIndex = framePlan.steps.indexOfFirst { step ->
        step is GPUFrameStep.RenderPassStep &&
            step.drawPackets.all {
                it.semanticPayload is GPUDrawSemanticPayload.CorePrimitive
            }
    }
    return GPUCommandEncoderPlan.ordered(
        planId = planId,
        contextIdentity = contextIdentity,
        deviceGeneration = deviceGeneration,
        targetGeneration = targetGeneration,
        scopes = scopes.map { scope ->
            if (scope.sourceStepIndex != coreStepIndex) {
                scope
            } else {
                GPUCommandEncoderScopePlan(
                    sourceStepIndex = scope.sourceStepIndex,
                    operationKind = scope.operationKind,
                    scopeLabel = scope.scopeLabel,
                    sourceTaskIds = scope.sourceTaskIds,
                    sourcePacketIds = scope.sourcePacketIds,
                    facadeOperationClasses = scope.facadeOperationClasses,
                    targetGeneration = scope.targetGeneration,
                    resourceGenerationLabels = scope.resourceGenerationLabels,
                    passCommandStream = scope.passCommandStream,
                ).attachNativeOperandKeys(scope.nativeOperandKeys)
            }
        },
    )
}

private fun GPUCommandEncoderPlan.rebuilt(
    planId: String = this.planId,
    contextIdentity: String = this.contextIdentity,
    targetGeneration: Long = this.targetGeneration,
    scopes: List<GPUCommandEncoderScopePlan> = this.scopes,
): GPUCommandEncoderPlan = GPUCommandEncoderPlan.ordered(
    planId = planId,
    contextIdentity = contextIdentity,
    deviceGeneration = deviceGeneration,
    targetGeneration = targetGeneration,
    scopes = scopes,
)

private fun GPUCommandEncoderPlan.replacingScope(
    source: GPUCommandEncoderScopePlan,
    replacement: GPUCommandEncoderScopePlan,
): GPUCommandEncoderPlan = rebuilt(
    scopes = scopes.map { scope -> if (scope === source) replacement else scope },
)

private fun GPUCommandEncoderScopePlan.rebuilt(
    scopeLabel: String = this.scopeLabel,
    sourcePacketIds: List<org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID> =
        this.sourcePacketIds,
    facadeOperationClasses: List<String> = this.facadeOperationClasses,
    targetGeneration: Long = this.targetGeneration,
    resourceGenerationLabels: List<String> = this.resourceGenerationLabels,
    passCommandStream: org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandStream? =
        this.passCommandStream,
    targetResource: GPUFrameTargetRef? = this.targetResource,
    nativeOperandKeys: List<GPUPreparedNativeOperandKey> = this.nativeOperandKeys,
): GPUCommandEncoderScopePlan = GPUCommandEncoderScopePlan(
    sourceStepIndex = sourceStepIndex,
    operationKind = operationKind,
    scopeLabel = scopeLabel,
    sourceTaskIds = sourceTaskIds,
    sourcePacketIds = sourcePacketIds,
    facadeOperationClasses = facadeOperationClasses,
    targetGeneration = targetGeneration,
    resourceGenerationLabels = resourceGenerationLabels,
    passCommandStream = passCommandStream,
    corePrimitiveDirectNativeRouteSeal = corePrimitiveDirectNativeRouteSeal,
    corePrimitivePathStencilNativeRouteSeal = corePrimitivePathStencilNativeRouteSeal,
    corePrimitiveNativeScopeRouteSeal = corePrimitiveNativeScopeRouteSeal,
    corePrimitiveClipStencilPreparedRouteSeal =
        corePrimitiveClipStencilPreparedRouteSeal,
    corePrimitiveCoverageMaskPreparedRouteSeal =
        corePrimitiveCoverageMaskPreparedRouteSeal,
    targetResource = targetResource,
).attachNativeOperandKeys(nativeOperandKeys)

private fun GPUFramePlan.withRenderMutation(
    transform: (Int, GPUFrameStep.RenderPassStep) -> GPUFrameStep.RenderPassStep,
): GPUFramePlan = rebuilt(
    steps = steps.mapIndexed { index, step ->
        if (step is GPUFrameStep.RenderPassStep) transform(index, step) else step
    },
)

private fun GPUFramePlan.withCoreResourceAlias(
    role: GPUFrameResourceRole,
    alias: GPUFrameResourceRef,
): GPUFramePlan = withRenderMutation { _, render ->
    if (render.drawPackets.none {
            it.semanticPayload is GPUDrawSemanticPayload.CorePrimitive
        }
    ) {
        render
    } else {
        GPUFrameStep.RenderPassStep(
            target = render.target,
            loadStore = render.loadStore,
            samplePlan = render.samplePlan,
            resourceUses = render.resourceUses.map { use ->
                if (use.role == role) use.copy(resource = alias) else use
            },
            drawPackets = render.drawPackets,
            sourceTaskIds = render.sourceTaskIds,
            batches = render.batches,
            sampleContinuation = render.sampleContinuation,
            depthStencilLoadStore = render.depthStencilLoadStore,
            preparedImageBindingsByPacketId =
                render.preparedImageBindingsByPacketId,
        )
    }
}

private fun GPUFramePlan.withFirstCorePacketRole(
    role: GPUDrawPacketRole,
): GPUFramePlan {
    var changed = false
    return withRenderMutation { _, render ->
        val packets = render.drawPackets.map { packet ->
            if (!changed &&
                packet.semanticPayload is GPUDrawSemanticPayload.CorePrimitive
            ) {
                changed = true
                packet.withPreparedSurfaceTestRole(role)
            } else {
                packet
            }
        }
        GPUFrameStep.RenderPassStep(
            target = render.target,
            loadStore = render.loadStore,
            samplePlan = render.samplePlan,
            resourceUses = render.resourceUses,
            drawPackets = packets,
            sourceTaskIds = render.sourceTaskIds,
            batches = render.batches,
            sampleContinuation = render.sampleContinuation,
            depthStencilLoadStore = render.depthStencilLoadStore,
            preparedImageBindingsByPacketId =
                render.preparedImageBindingsByPacketId,
        )
    }
}

private fun GPUDrawPacket.withPreparedSurfaceTestRole(
    role: GPUDrawPacketRole,
) = GPUDrawPacket(
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

private fun GPUDrawPacket.withPreparedSurfaceTestPipelineKey(
    packetId: GPUDrawPacketID,
    commandIdValue: Int,
    renderPipelineKey: org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey,
) = GPUDrawPacket(
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

private fun GPUResourcePreparationRequest.rebuilt(
    descriptor: GPUFrameTextureDescriptor,
) = GPUResourcePreparationRequest(
    resource = resource,
    descriptor = descriptor,
    role = role,
    usages = usages,
    lifetime = lifetime,
    byteSize = byteSize,
    diagnosticLabel = diagnosticLabel,
)

private fun GPUFramePlan.rebuilt(
    steps: List<GPUFrameStep>,
) = GPUFramePlan(
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

private fun GPUPreparedResourceSet.rebuilt(
    ordinaryResources: List<GPUPreparedResourceEvidence> = this.ordinaryResources,
    outputOwnedReadbacks: List<GPUPreparedReadbackOutput> = this.outputOwnedReadbacks,
    commandResourceLeases: List<GPUResourceLease> =
        this.commandResourceLeases.map(GPUPreparedCommandResourceLease::toResourceLease),
    commandTextureResources: List<GPUTextureResourceRef> = this.commandTextureResources,
    commandBufferResources: List<GPUBufferResourceRef> = this.commandBufferResources,
    commandDiagnostics: List<GPUResourceDiagnostic> =
        this.commandDiagnostics.map(GPUPreparedCommandDiagnostic::toResourceDiagnostic),
): GPUPreparedResourceSet = GPUPreparedResourceSet(
    ordinaryResources = ordinaryResources,
    outputOwnedReadbacks = outputOwnedReadbacks,
    commandResourceLeases = commandResourceLeases,
    commandTextureResources = commandTextureResources,
    commandBufferResources = commandBufferResources,
    commandDiagnostics = commandDiagnostics,
)

private fun GPUPreparedCommandResourceLease.toResourceLease(): GPUResourceLease =
    GPUResourceLease(
        leaseId = leaseId,
        resourceKind = resourceKind,
        deviceGeneration = deviceGeneration,
        descriptorHash = descriptorHash,
        ownerScope = ownerScope,
        usageLabels = usageLabels,
        releasePolicy = releasePolicy,
        cacheResult = cacheResult,
        evidenceFacts = evidenceFacts,
    )

private fun GPUPreparedCommandDiagnostic.toResourceDiagnostic(): GPUResourceDiagnostic =
    GPUResourceDiagnostic(
        code = code,
        resourceLabel = resourceLabel,
        message = message,
        terminal = false,
        facts = facts,
    )

private fun GPUFramePlan.withSurfaceChain(
    targetGeneration: Long,
): GPUFramePlan {
    val scene = steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
        .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        .single { request -> request.role == GPUFrameResourceRole.SceneTarget }
        .resource as GPUFrameTargetRef
    val descriptor = assertIs<GPUFrameTextureDescriptor>(
        steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .single { request -> request.resource == scene }
            .descriptor,
    )
    val output = GPUSurfaceOutputRef("surface.prepared-surface")
    val sourceTaskIds = listOf(GPUTaskID("task.prepared-surface.surface"))
    return rebuilt(
        steps = steps + listOf(
            GPUFrameStep.AcquireSurfaceOutput(
                GPUSurfaceOutputDescriptor(
                    output = output,
                    width = descriptor.logicalBounds.width,
                    height = descriptor.logicalBounds.height,
                    format = descriptor.format,
                    targetGeneration = targetGeneration,
                ),
                sourceTaskIds,
            ),
            GPUFrameStep.SurfaceBlitRenderPassStep(scene, output, sourceTaskIds),
            GPUFrameStep.PostSubmitPresentAction(output, sourceTaskIds),
        ),
    )
}

private fun GPUFramePlan.withMismatchedSurfacePresent(): GPUFramePlan =
    rebuilt(
        steps = steps.map { step ->
            if (step !is GPUFrameStep.PostSubmitPresentAction) {
                step
            } else {
                GPUFrameStep.PostSubmitPresentAction(
                    GPUSurfaceOutputRef("surface.prepared-surface.foreign"),
                    step.sourceTaskIds,
                )
            }
        },
    )

private fun GPUFramePlan.withMismatchedSurfaceScene(): GPUFramePlan =
    rebuilt(
        steps = steps.map { step ->
            if (step !is GPUFrameStep.SurfaceBlitRenderPassStep) {
                step
            } else {
                GPUFrameStep.SurfaceBlitRenderPassStep(
                    GPUFrameTargetRef("target.prepared-surface.foreign"),
                    step.output,
                    step.sourceTaskIds,
                )
            }
        },
    )

private fun GPUFramePlan.withReadbackBounds(
    bounds: GPUPixelBounds,
): GPUFramePlan = rebuilt(
    steps = steps.map { step ->
        if (step !is GPUFrameStep.ReadbackCopyStep) {
            step
        } else {
            GPUFrameStep.ReadbackCopyStep(
                source = step.source,
                staging = step.staging,
                request = step.request.copy(sourceBounds = bounds),
                sourceTaskIds = step.sourceTaskIds,
            )
        }
    },
)

private fun GPUTaskList.withPreparedSurfaceClipAuthority(): GPUTaskList = GPUTaskList(
    frameId = frameId,
    capabilitySeal = capabilitySeal,
    recordingSeals = recordingSeals,
    expectedReplayKeyHash = expectedReplayKeyHash,
    tasks = tasks.map { task ->
        if (task !is GPUTask.Render) {
            task
        } else {
            val packets = task.drawPackets.map { packet ->
                if (packet.renderStepId.value.startsWith("image.draw.")) {
                    packet
                } else {
                    packet.withNoClipAuthority()
                }
            }
            GPUTask.Render(
                taskId = task.taskId,
                recordingId = task.recordingId,
                phase = task.phase,
                target = task.target,
                loadStore = task.loadStore,
                samplePlan = task.samplePlan,
                resourceUses = task.resourceUses,
                provisionalSegmentKey = task.provisionalSegmentKey,
                drawPackets = packets,
                batchEligibilityByPacketId = packets.associate { packet ->
                    packet.packetId to task.batchEligibilityByPacketId.getValue(packet.packetId)
                },
                sampleContinuationKey = task.sampleContinuationKey,
                compositeMembership = task.compositeMembership,
                depthStencilLoadStore = task.depthStencilLoadStore,
            )
        }
    },
    dependencies = dependencies,
    phaseOrder = phaseOrder,
    memoryBudget = memoryBudget,
    diagnostics = diagnostics,
)

private fun GPUDrawPacket.withNoClipAuthority() = GPUDrawPacket(
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
    clipCoveragePlan = GPUClipCoveragePlan.NoClip,
    clipExecutionPlan = GPUClipExecutionPlan.NoClip,
    diagnostics = diagnostics,
    clipProducerAuthority = clipProducerAuthority,
)

private fun preparedSurfaceCoreSemantic(
    base: GPUTaskList,
    commandId: Int,
): GPUDrawSemanticPayload.CorePrimitive {
    val packet = preparedSurfacePacket(base, commandId)
    return GPUCorePrimitivePayloadGatherer().gatherSemantic(
        GPUCorePrimitivePayloadInput(
            commandIdValue = commandId,
            sourceFamily = GPUCorePrimitiveSourceFamily.Color,
            geometry = GPUCorePrimitiveGeometryInput.Rect(1f, 1f, 8f, 8f),
            premultipliedRgba = listOf(0.25f, 0.5f, 0.75f, 1f),
            targetBounds = PREPARED_SURFACE_BOUNDS,
            scissorBounds = PREPARED_SURFACE_BOUNDS,
            clipCoveragePlan = GPUClipCoveragePlan.NoClip,
            clipExecutionPlanIdentity = GPUClipExecutionPlan.NoClip.canonicalIdentity(),
            blendPlanIdentity = requireNotNull(packet.blendPlan).canonicalIdentity(),
            frameProvenance = packet.frameProvenance,
            coverageMode = GPUCorePrimitiveCoverageMode.FullOrScissor,
        ),
    )
}

private fun preparedSurfacePathSemantic(
    base: GPUTaskList,
    commandId: Int,
): GPUDrawSemanticPayload.CorePrimitive {
    val packet = preparedSurfacePacket(base, commandId)
    return GPUCorePrimitivePayloadGatherer().gatherSemantic(
        GPUCorePrimitivePayloadInput(
            commandIdValue = commandId,
            sourceFamily = GPUCorePrimitiveSourceFamily.Path,
            geometry = GPUCorePrimitiveGeometryInput.TriangulatedPath(
                vertices = listOf(
                    -1f, -1f, 1f, 1f, 7f, 1f,
                    -1f, -1f, 7f, 1f, 4f, 7f,
                    -1f, -1f, 4f, 7f, 1f, 1f,
                ),
                indices = (0..8).toList(),
                sourceContourStarts = listOf(0),
                sourceVertexCount = 3,
                coverBounds = GPUPixelBounds(1, 1, 8, 8),
                geometryMode = GPUCorePrimitiveGeometryMode.StencilEdgeFan,
                fillRule = GPUCorePrimitiveFillRule.Winding,
            ),
            premultipliedRgba = listOf(0.25f, 0.5f, 0.75f, 1f),
            targetBounds = PREPARED_SURFACE_BOUNDS,
            scissorBounds = PREPARED_SURFACE_BOUNDS,
            clipCoveragePlan = GPUClipCoveragePlan.NoClip,
            clipExecutionPlanIdentity = GPUClipExecutionPlan.NoClip.canonicalIdentity(),
            blendPlanIdentity = requireNotNull(packet.blendPlan).canonicalIdentity(),
            frameProvenance = packet.frameProvenance,
            coverageMode = GPUCorePrimitiveCoverageMode.Stencil1x,
        ),
    )
}

private fun srcOverStructuralBlend() = GPUCorePrimitiveRenderPipelineStructuralKey.Blend.Fixed(
    mode = GPUBlendMode.SRC_OVER,
    sourceCoverage = GPUSourceCoverageEncoding.None,
    state = GPUFixedFunctionBlendState(
        stateId = "src-over",
        color = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
        alpha = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
        writeMask = "rgba",
    ),
)

private fun clearStructuralBlend() = GPUCorePrimitiveRenderPipelineStructuralKey.Blend.Fixed(
    mode = GPUBlendMode.CLEAR,
    sourceCoverage = GPUSourceCoverageEncoding.None,
    state = GPUFixedFunctionBlendState(
        stateId = "test-clear",
        color = GPUFixedFunctionBlendComponent("zero", "zero", "add"),
        alpha = GPUFixedFunctionBlendComponent("zero", "zero", "add"),
        writeMask = "rgba",
    ),
)

private fun preparedSurfaceImageSemantic(
    base: GPUTaskList,
    commandId: Int,
): GPUDrawSemanticPayload.SampledImage {
    val packet = preparedSurfacePacket(base, commandId)
    return GPUPreparedImagePayloadGatherer().gatherSemantic(
        GPUPreparedImagePayloadInput(
            payloadRef = GPUDrawPayloadRef(commandId, "image.draw.texture_upload"),
            artifact = preparedSurfaceArtifact(),
            geometry = GPUPreparedImageGeometry(
                GPUPreparedImageGeometryClass.Rect,
                listOf(
                    GPUPreparedImageVertex(1f, 1f, 0f, 0f),
                    GPUPreparedImageVertex(8f, 1f, 1f, 0f),
                    GPUPreparedImageVertex(8f, 8f, 1f, 1f),
                    GPUPreparedImageVertex(1f, 8f, 0f, 1f),
                ),
                listOf(0, 1, 2, 0, 2, 3),
            ),
            sampling = GPUPreparedImageSampling.Nearest,
            tintPremultipliedRgba = listOf(1f, 1f, 1f, 1f),
            atlasColorPremultipliedRgba = null,
            atlasSourceBlend = null,
            targetBounds = PREPARED_SURFACE_BOUNDS,
            scissorBounds = PREPARED_SURFACE_BOUNDS,
            blendPlanIdentity = requireNotNull(packet.blendPlan).canonicalIdentity(),
            frameProvenance = packet.frameProvenance,
        ),
    )
}

private fun preparedSurfacePacket(base: GPUTaskList, commandId: Int): GPUDrawPacket =
    base.tasks.filterIsInstance<GPUTask.Render>()
        .flatMap(GPUTask.Render::drawPackets)
        .single { packet -> packet.commandIdValue == commandId }

private fun preparedSurfaceCoreCommand(commandId: Int, paintOrder: Int) =
    GPUFillRectCommandBuilder.build(
        commandId = GPUDrawCommandID(commandId),
        rect = GPURect(1f, 1f, 8f, 8f),
        target = PREPARED_SURFACE_TARGET,
        material = GPUMaterialDescriptor.SolidColor(0.25f, 0.5f, 0.75f, 1f),
        paintOrder = paintOrder,
        source = GPUCommandSource("test", "fillRect", GPUFrameProvenance.GmContent),
    )

private fun preparedSurfaceImageCommand(commandId: Int, paintOrder: Int) =
    GPUDrawImageRectCommandBuilder.build(
        commandId = GPUDrawCommandID(commandId),
        imageSourceId = "shared-image",
        src = GPURect(0f, 0f, 3f, 2f),
        dst = GPURect(1f, 1f, 8f, 8f),
        target = PREPARED_SURFACE_TARGET,
        material = GPUMaterialDescriptor.ImageDraw(
            imageSourceId = "shared-image",
            imageWidth = 3,
            imageHeight = 2,
            rgbaPixels = preparedSurfaceArtifact().tightRgba8BytesForUpload(),
            samplingFilterMode = "nearest",
        ),
        samplingFilterMode = "nearest",
        pixelsWidth = 3,
        pixelsHeight = 2,
        pixelsRowBytes = 12,
        pixelsContentHash = preparedSurfaceArtifact().contentHash,
        pixelsProvenance = "test",
        paintOrder = paintOrder,
        source = GPUCommandSource("test", "drawImageRect", GPUFrameProvenance.GmContent),
    )

private fun preparedSurfaceArtifact() = (
    GPUPreparedImageArtifactFactory.prepare(
        GPUPreparedImageSourceInput(
            GPUPreparedImageSourceClass.DecodedCpu,
            "shared-image",
            3,
            2,
            GPUPreparedImageSourceFormat.Rgba8,
            AlphaType.PREMUL,
            12,
            GPUPreparedImageProfile.Srgb,
            GPUPreparedImageOrientation.AppliedIdentity,
            GPUPreparedImageProvenance.CallerPixels,
            0,
            ByteArray(24) { index -> (index + 1).toByte() },
        ),
    ) as GPUPreparedImageArtifactResult.Ready
    ).artifact

private fun preparedSurfaceCapabilities() = GPUCapabilities(
    implementation = GPUImplementationIdentity("GPU", "test", "adapter", "device"),
    facts = listOf(
        GPUCapabilityFact("first_slice.fill_rect.native", "test", "supported", true, "test"),
        GPUCapabilityFact("first_slice.draw_image_rect.prepared", "test", "supported", true, "test"),
        GPUCapabilityFact(
            "first_slice.draw_text_run.a8_atlas",
            "test",
            "supported",
            true,
            "task9",
        ),
    ),
    snapshotId = "prepared-surface-native-preflight",
    limits = GPULimits(
        maxTextureDimension2D = 8192,
        copyBytesPerRowAlignment = 256,
        minUniformBufferOffsetAlignment = 256,
        maxBufferSize = 1L shl 30,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
    ),
    supportedTextureFormats = setOf(
        GPUTextureFormat.R8Unorm,
        GPUTextureFormat.RGBA8UnormSrgb,
    ),
)

private val PREPARED_SURFACE_BOUNDS = GPUPixelBounds(0, 0, 16, 16)
private val PREPARED_SURFACE_TARGET = GPUTargetFacts(16, 16, "rgba8unorm")
