package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroupKey
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateIdentity
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchAdjacency
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleContinuationKey
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUComputePipelineKey
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.recording.GPUComputeDispatch
import org.graphiks.kanvas.gpu.renderer.recording.GPUCompositeProvenanceToken
import org.graphiks.kanvas.gpu.renderer.recording.GPUDestinationSnapshotConsumerRef
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameCapabilitySeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameReadbackRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameRenderBatch
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackPixelFormat
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingSeal
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUSurfaceOutputDescriptor
import org.graphiks.kanvas.gpu.renderer.recording.GPUSurfaceOutputRef
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskUseToken
import org.graphiks.kanvas.gpu.renderer.recording.GPUTargetTransitionKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUCommandOperandMaterializationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreflightProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreparationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreparationInput
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreparationSession
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef
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
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUTargetIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GPUFramePreflighterTest {
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
    ): GPUFramePreflighter = GPUFramePreflighter(
        context = context,
        capabilities = capabilities(),
        resourceProvider = resources,
        completionProvider = completion,
        surfaceProvider = surface,
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
        private var sessionOrdinal = 0L

        override fun beginFramePreparation(
            frameId: Long,
            deviceGeneration: GPUDeviceGenerationID,
        ): GPUFrameResourcePreparationSession = GPUFrameResourcePreparationSession(
            "frame-preflight:$frameId:device:${deviceGeneration.value}:attempt:${++sessionOrdinal}",
        )

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

    private class RecordingCompletionProvider(
        private val events: MutableList<String>,
        private val mode: String = "reserved",
    ) : GPUQueueCompletionProvider {
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

    private fun prepareScene(): GPUFrameStep.PrepareResourcesStep = GPUFrameStep.PrepareResourcesStep(
        requests = listOf(
            GPUResourcePreparationRequest(
                resource = GPUFrameTargetRef("target.scene"),
                descriptor = GPUFrameTextureDescriptor(
                    GPUPixelBounds(0, 0, 4, 4),
                    GPUColorFormat("rgba8unorm"),
                    1,
                ),
                role = GPUFrameResourceRole.SceneTarget,
                usages = setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = 64,
                diagnosticLabel = "scene",
            ),
        ),
        sourceTaskIds = listOf(GPUTaskID("task.prepare")),
    )

    private fun renderStep(): GPUFrameStep.RenderPassStep {
        val packet = packet("packet.main", 1)
        return GPUFrameStep.RenderPassStep(
            target = GPUFrameTargetRef("target.scene"),
            loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
            samplePlan = org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan.SingleSampleFrame,
            drawPackets = listOf(packet),
            sourceTaskIds = listOf(GPUTaskID("task.render")),
            batches = listOf(batch("batch.main", packet, "task.render")),
        )
    }

    private fun packet(
        id: String,
        commandId: Int,
        passId: String = "pass.main",
        pipelineKey: String = "pipeline.fill",
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
        renderStepId = GPURenderStepID("step.fill"),
        renderStepVersion = 1,
        role = GPUDrawPacketRole.Shading,
        renderPipelineKey = GPURenderPipelineKey(pipelineKey),
        bindingLayoutHash = "layout.fill",
        vertexSourceLabel = "vertices.$id",
        targetStateHash = "target.state",
        originalPaintOrder = commandId,
        resourceGeneration = 1,
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
    ): GPUFramePlan {
        val frameId = GPUFrameID(7)
        val seal = GPUFrameCapabilitySeal.capture(frameId, GPUDeviceGenerationID(7), capabilities)
        return GPUFramePlan(
            frameId = frameId,
            capabilitySeal = seal,
            recordingSeals = listOf(GPURecordingSeal(GPURecordingID("recording.main"), 0, "compat", "replay", seal.sealHash)),
            steps = steps,
            memoryBudget = budget(),
            diagnostics = emptyList(),
        )
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
        ).associateWith { resourceGeneration },
    )

    private fun capabilities(
        snapshotId: String = "capabilities.current",
        maxBufferSize: Long? = 1L shl 30,
    ): GPUCapabilities = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "unit", "adapter", "device"),
        facts = listOf(GPUCapabilityFact("limits", "test", "observed", true, "preflight")),
        snapshotId = snapshotId,
        limits = GPULimits(8192, 256, 256, maxBufferSize = maxBufferSize),
        rendererFeatures = setOf(
            GPURendererFeature.RenderPass,
            GPURendererFeature.CopyUpload,
            GPURendererFeature.Readback,
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
