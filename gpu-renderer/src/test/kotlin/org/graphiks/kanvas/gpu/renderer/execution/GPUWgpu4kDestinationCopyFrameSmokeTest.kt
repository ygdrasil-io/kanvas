package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.glfwContextRenderer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
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
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadMember
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroup
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroupingResult
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotMaterialization
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleContinuationKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.payloads.GPUMaterialPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadGatherPlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUSolidPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.recording.GPUDestinationSnapshotConsumerRef
import org.graphiks.kanvas.gpu.renderer.recording.GPUDestinationSnapshotOperation
import org.graphiks.kanvas.gpu.renderer.recording.GPUDestinationSnapshotTaskPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameCapabilitySeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameReadbackRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameRenderBatch
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackPixelFormat
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingSeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskDependency
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskPhase
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskUseToken
import org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUSceneTarget
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureCopyLayout
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureResourceRef
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUTargetIdentity
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome

class GPUWgpu4kDestinationCopyFrameSmokeTest {
    @Test
    fun `prepared scene session dispatches destination copy on its canonical target`() {
        val backendSession = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backendSession != null)
        backendSession!!
        val runtimeCapabilities = requireNotNull(backendSession.capabilities)
        val generation = GPUDeviceGenerationID(
            runtimeCapabilities.snapshotId.substringAfterLast('-').toLong(),
        )
        val requestId = GPUReadbackRequestID("readback.prepared.destination-copy")
        val tasks = destinationCopyTaskList(
            generation = generation,
            capabilities = runtimeCapabilities,
            frameId = GPUFrameID(10_605),
            readbackRequestId = requestId,
        )
        val session = backendSession.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(4, 4, "rgba8unorm"),
        )
        var targetClosesAfterSessionClose = -1L
        try {
            val handle = session.renderFrame(
                tasks,
                GPUSceneFrameOutputRequest.ReadbackRgba(requestId),
            )
            val terminal = handle.completion.toCompletableFuture().get(10, TimeUnit.SECONDS)

            assertEquals(
                GPUFrameStructuralOutcome.Succeeded,
                terminal.outcome,
                "${terminal.diagnostic?.code?.value}: ${terminal.diagnostic?.message}",
            )
            val readback = assertIs<GPUSceneFrameOutput.ReadbackRgba>(terminal.output)
            assertEquals(requestId, readback.requestId)
            assertContentEquals(expectedDifferencePixels(), readback.bytes)
            val counters = session.nativeCounters()
            assertEquals(1L, counters.targetCreations)
            assertEquals(0L, counters.targetCloses)
            assertEquals(1L, counters.targetNativeUses)
            assertEquals(1L, counters.submits)
            assertEquals(1L, counters.readbackCopies)
            assertEquals(0, counters.activeNativePayloads)
        } finally {
            try {
                session.close()
                targetClosesAfterSessionClose = session.nativeCounters().targetCloses
            } finally {
                GPUBackendRuntimeNativeFactory.dispose()
            }
        }
        assertEquals(1L, targetClosesAfterSessionClose)
    }

    @Test
    fun `real planner splits prefix destination consumer and submits exact pixels once`() = runBlocking {
        val context = glfwContextRenderer(4, 4, "kanvas-destination-copy", deferredRendering = true)
        val generation = GPUDeviceGenerationID(10_601)
        val adapter = GPURuntimeResourceAdapter()
        val provider = GPUConcreteResourceProvider(leaseFactory = adapter)
        val completion = completion(context, generation)
        val mappingExecutor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "kanvas-destination-copy-readback").apply { isDaemon = true }
        }
        val backend = GPUWgpu4kFrameEncodingBackend(
            generation,
            context.wgpuContext.device,
            context.wgpuContext.device.queue,
        )
        val materializer = GPUWgpu4kDestinationCopyFramePayloadMaterializer(
            context.wgpuContext.device,
            context.wgpuContext.device.queue,
        )
        try {
            val plan = plannedDestinationCopyPlan(generation)
            assertFalse(plan.atomicallyRefused, plan.diagnostics.joinToString { it.code.value })
            assertEquals(
                listOf(
                    GPUFrameStep.PrepareResourcesStep::class,
                    GPUFrameStep.RenderPassStep::class,
                    GPUFrameStep.CopyDestinationStep::class,
                    GPUFrameStep.RenderPassStep::class,
                    GPUFrameStep.ReadbackCopyStep::class,
                ),
                plan.steps.map { it::class },
            )
            assertEquals(
                listOf("packet.background", "packet.consumer"),
                plan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
                    .flatMap { it.drawPackets }
                    .map { it.packetId.value },
            )
            val result = preflighter(
                generation,
                adapter,
                provider,
                completion,
                materializer,
            ).preflight(plan)
            val prepared = assertIs<GPUFramePreflightResult.Prepared>(
                result,
                (result as? GPUFramePreflightResult.Refused)?.let {
                    "${it.diagnostic.code.value}: ${it.diagnostic.message} facts=${it.diagnostic.facts}"
                },
            ).frame
            materializer.close()
            val snapshot = prepared.resources.ordinaryResources.single { it.logicalResource == SNAPSHOT }
            val allocation = requireNotNull(snapshot.textureAllocation)
            assertEquals(COPY_BOUNDS, allocation.logicalBounds)
            assertEquals(16, allocation.backingWidth)
            assertEquals(16, allocation.backingHeight)

            val materialization = materializer.counters()
            assertEquals(COPY_BOUNDS, materialization.snapshotLogicalBounds)
            assertEquals(16, materialization.snapshotBackingWidth)
            assertEquals(16, materialization.snapshotBackingHeight)
            assertEquals(1, materialization.copySourceOriginX)
            assertEquals(1, materialization.copySourceOriginY)
            assertEquals(2, materialization.copyWidth)
            assertEquals(2, materialization.copyHeight)
            assertTrue(materialization.nativeResourceCreations > 0)

            val executor = GPUFrameExecutor(
                sceneTarget = sceneTarget(generation),
                backend = backend,
                completion = completion,
                retention = NoOpRetention,
                readback = GPUConcreteFrameReadbackAccess(
                    provider,
                    GPUWgpu4kNativeReadbackMapper(mappingExecutor),
                ),
            )
            val handle = executor.execute(prepared)
            assertIs<GPUFrameImmediateState.Submitted>(handle.immediateState)
            val terminal = handle.completion.toCompletableFuture().get(10, TimeUnit.SECONDS)

            assertEquals(GPUFrameStructuralOutcome.Succeeded, terminal.outcome)
            assertContentEquals(expectedDifferencePixels(), requireNotNull(terminal.readback).bytes)
            val counters = backend.counters()
            assertEquals(1, counters.encoders)
            assertEquals(2, counters.renderPasses)
            assertEquals(2, counters.draws)
            assertEquals(1, counters.destinationCopies)
            assertEquals(1, counters.readbackCopies)
            assertEquals(1, counters.finishes)
            assertEquals(1, counters.submits)
            assertEquals(0, counters.pendingCommandBuffers)
        } finally {
            backend.close()
            mappingExecutor.shutdownNow()
            adapter.close()
            materializer.close()
            completion.close()
            context.close()
        }
    }

    @Test
    fun `consumer first copies old target then clears outside draw in one submission`() = runBlocking {
        val context = glfwContextRenderer(4, 4, "kanvas-destination-copy-consumer-first", deferredRendering = true)
        val generation = GPUDeviceGenerationID(10_603)
        val adapter = GPURuntimeResourceAdapter()
        val provider = GPUConcreteResourceProvider(leaseFactory = adapter)
        val completion = completion(context, generation, "destination-copy-consumer-first")
        val mappingExecutor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "kanvas-destination-copy-consumer-first-readback").apply { isDaemon = true }
        }
        val backend = GPUWgpu4kFrameEncodingBackend(
            generation,
            context.wgpuContext.device,
            context.wgpuContext.device.queue,
        )
        val materializer = GPUWgpu4kDestinationCopyFramePayloadMaterializer(
            context.wgpuContext.device,
            context.wgpuContext.device.queue,
        )
        try {
            val plan = plannedDestinationCopyPlan(generation, includeBackground = false)
            assertFalse(plan.atomicallyRefused, plan.diagnostics.joinToString { it.code.value })
            assertEquals(
                listOf(
                    GPUFrameStep.PrepareResourcesStep::class,
                    GPUFrameStep.CopyDestinationStep::class,
                    GPUFrameStep.RenderPassStep::class,
                    GPUFrameStep.ReadbackCopyStep::class,
                ),
                plan.steps.map { it::class },
            )
            assertEquals(
                "clear",
                plan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().single().loadStore.loadOp,
            )
            val result = preflighter(
                generation,
                adapter,
                provider,
                completion,
                materializer,
                "destination-copy-consumer-first",
            ).preflight(plan)
            val prepared = assertIs<GPUFramePreflightResult.Prepared>(
                result,
                (result as? GPUFramePreflightResult.Refused)?.let {
                    "${it.diagnostic.code.value}: ${it.diagnostic.message} facts=${it.diagnostic.facts}"
                },
            ).frame
            materializer.close()
            val executor = GPUFrameExecutor(
                sceneTarget = sceneTarget(generation),
                backend = backend,
                completion = completion,
                retention = NoOpRetention,
                readback = GPUConcreteFrameReadbackAccess(
                    provider,
                    GPUWgpu4kNativeReadbackMapper(mappingExecutor),
                ),
            )
            val handle = executor.execute(prepared)
            assertIs<GPUFrameImmediateState.Submitted>(handle.immediateState)
            val terminal = handle.completion.toCompletableFuture().get(10, TimeUnit.SECONDS)

            assertEquals(GPUFrameStructuralOutcome.Succeeded, terminal.outcome)
            assertContentEquals(expectedConsumerFirstClearPixels(), requireNotNull(terminal.readback).bytes)
            val counters = backend.counters()
            assertEquals(1, counters.encoders)
            assertEquals(1, counters.renderPasses)
            assertEquals(1, counters.draws)
            assertEquals(1, counters.destinationCopies)
            assertEquals(1, counters.readbackCopies)
            assertEquals(1, counters.finishes)
            assertEquals(1, counters.submits)
            assertEquals(0, counters.pendingCommandBuffers)
        } finally {
            backend.close()
            mappingExecutor.shutdownNow()
            adapter.close()
            materializer.close()
            completion.close()
            context.close()
        }
    }

    @Test
    fun `ambiguous inexact blend or out of bounds destination copy refuses transactionally before native creation`() =
        runBlocking {
            val context = glfwContextRenderer(4, 4, "kanvas-destination-copy-refusal", deferredRendering = true)
            val generation = GPUDeviceGenerationID(10_602)
            val scenarios = listOf(
                InvalidRoute.DuplicateConsumer to "unsupported.native-destination-copy.consumer-ambiguous",
                InvalidRoute.MismatchedPacket to "unsupported.native-destination-copy.consumer-binding",
                InvalidRoute.MissingDestinationBlend to "unsupported.native-destination-copy.consumer-blend",
                InvalidRoute.MultiplyDestinationBlend to "unsupported.native-destination-copy.consumer-blend",
                InvalidRoute.OverlayDestinationBlend to "unsupported.native-destination-copy.consumer-blend",
                InvalidRoute.WrongDifferenceFormula to "unsupported.native-destination-copy.consumer-blend",
                InvalidRoute.WrongDifferenceCoverage to "unsupported.native-destination-copy.consumer-blend",
                InvalidRoute.CopyOutsideTarget to "unsupported.native-destination-copy.source-bounds",
                InvalidRoute.TargetMissingRenderAttachment to
                    "unsupported.native-destination-copy.target-topology",
                InvalidRoute.TargetMissingCopySource to "unsupported.native-destination-copy.target-topology",
                InvalidRoute.TargetExtraTextureBinding to
                    "unsupported.native-destination-copy.target-topology",
                InvalidRoute.SnapshotMissingCopyDestination to
                    "unsupported.native-destination-copy.snapshot-declaration",
                InvalidRoute.SnapshotMissingTextureBinding to
                    "unsupported.native-destination-copy.snapshot-declaration",
                InvalidRoute.SourceFormatMismatch to "unsupported.native-destination-copy.source-key",
                InvalidRoute.SourceColorMismatch to "unsupported.native-destination-copy.source-key",
                InvalidRoute.SourceTargetMismatch to "invalid.preflight.destination_key_source",
                InvalidRoute.SourceTargetGenerationMismatch to
                    "stale.preflight.destination_key_generation",
                InvalidRoute.SourceDeviceGenerationMismatch to
                    "stale.preflight.destination_key_generation",
                InvalidRoute.SourceSampleMismatch to "unsupported.native-destination-copy.source-key",
                InvalidRoute.RenderSampleMismatch to
                    "unsupported.blend.msaa_destination_read_exactness",
            )
            try {
                scenarios.forEachIndexed { index, (invalid, expectedCode) ->
                    val snapshotId = "destination-copy-${invalid.name}"
                    val adapter = GPURuntimeResourceAdapter()
                    val provider = GPUConcreteResourceProvider(leaseFactory = adapter)
                    val completion = completion(context, generation, "destination-copy-refusal-$index")
                    val materializer = GPUWgpu4kDestinationCopyFramePayloadMaterializer(
                        context.wgpuContext.device,
                        context.wgpuContext.device.queue,
                    )
                    try {
                        val result = preflighter(
                            generation,
                            adapter,
                            provider,
                            completion,
                            materializer,
                            snapshotId,
                        ).preflight(destinationCopyPlan(generation, invalid))

                        assertEquals(
                            expectedCode,
                            assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
                        )
                        assertEquals(0, materializer.counters().nativeResourceCreations)
                        assertEquals(0, provider.pendingPhysicalReservationCount)
                        assertEquals(0, adapter.activePreparedNativeFramePayloadCount)
                    } finally {
                        adapter.close()
                        materializer.close()
                        completion.close()
                    }
                }
            } finally {
                context.close()
            }
        }

    private fun preflighter(
        generation: GPUDeviceGenerationID,
        adapter: GPURuntimeResourceAdapter,
        provider: GPUConcreteResourceProvider,
        completion: GPUQueueCompletionAdapter,
        materializer: GPUWgpu4kDestinationCopyFramePayloadMaterializer,
        snapshotId: String = "destination-copy-native",
    ) = GPUFramePreflighter(
        context = GPUFramePreflightContext(
            targetId = TARGET.value,
            deviceGeneration = generation,
            targetGeneration = 1,
            resourceGenerations = mapOf(TARGET to 1, SNAPSHOT to 2, STAGING to 3),
        ),
        capabilities = capabilities(snapshotId),
        resourceProvider = provider,
        completionProvider = completion,
        surfaceProvider = NoNativeSurfaceOutput,
        nativeBoundary = adapter.bindNativeFrameBoundary(provider, materializer),
    )

    private fun plannedDestinationCopyPlan(
        generation: GPUDeviceGenerationID,
        includeBackground: Boolean = true,
    ): GPUFramePlan = GPUFramePlanner.plan(
        destinationCopyTaskList(
            generation = generation,
            capabilities = capabilities(
                if (includeBackground) "destination-copy-native" else "destination-copy-consumer-first",
            ),
            includeBackground = includeBackground,
        ),
    )

    private fun destinationCopyTaskList(
        generation: GPUDeviceGenerationID,
        capabilities: GPUCapabilities,
        includeBackground: Boolean = true,
        frameId: GPUFrameID = GPUFrameID(10_601),
        readbackRequestId: GPUReadbackRequestID =
            GPUReadbackRequestID("readback.destination-copy"),
    ): GPUTaskList {
        val seal = GPUFrameCapabilitySeal.capture(frameId, generation, capabilities)
        val recordingId = GPURecordingID("recording.destination-copy")
        val background = packet(
            id = "packet.background",
            commandId = 1,
            task = "background",
            rect = TARGET_BOUNDS,
            color = listOf(0f, 0f, 1f, 1f),
            blendPlan = GPUBlendPlan.ShaderBlendNoDstRead(
                mode = GPUBlendMode.SRC,
                formulaId = "source@v1",
                sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
            ),
            passId = "pass.destination-copy",
        )
        val consumerPacket = packet(
            id = "packet.consumer",
            commandId = 2,
            task = "consumer",
            rect = COPY_BOUNDS,
            color = listOf(1f, 0f, 0f, 1f),
            blendPlan = GPUBlendPlan.ShaderBlendWithDstRead(
                mode = GPUBlendMode.DIFFERENCE,
                formulaId = "difference@v1",
                sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
            ),
            passId = "pass.destination-copy",
        )
        val prepare = GPUTask.PrepareResources(
            taskId = GPUTaskID("task.prepare"),
            recordingId = recordingId,
            phase = GPUTaskPhase.Prepare,
            requests = listOf(
                GPUResourcePreparationRequest(
                    TARGET,
                    GPUFrameTextureDescriptor(TARGET_BOUNDS, RGBA8, 1),
                    GPUFrameResourceRole.SceneTarget,
                    setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource),
                    GPUFrameResourceLifetime.FrameLocal,
                    64,
                    "scene",
                ),
                GPUResourcePreparationRequest(
                    SNAPSHOT,
                    GPUFrameTextureDescriptor(COPY_BOUNDS, RGBA8, 1),
                    GPUFrameResourceRole.DestinationSnapshot,
                    setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.TextureBinding),
                    GPUFrameResourceLifetime.FrameLocal,
                    16,
                    "destination-snapshot",
                ),
                GPUResourcePreparationRequest(
                    STAGING,
                    GPUFrameBufferDescriptor(1_024, 4),
                    GPUFrameResourceRole.ReadbackStaging,
                    setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.MapRead),
                    GPUFrameResourceLifetime.FrameLocal,
                    1_024,
                    "readback",
                ),
            ),
        )
        val render = GPUTask.Render(
            taskId = CONSUMER_TASK,
            recordingId = recordingId,
            phase = GPUTaskPhase.Render,
            target = TARGET,
            loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store),
            samplePlan = GPUSamplePlan.SingleSampleFrame,
            drawPackets = listOfNotNull(background.takeIf { includeBackground }, consumerPacket),
            batchEligibilityByPacketId =
            listOfNotNull(background.takeIf { includeBackground }, consumerPacket).associate { packet ->
                packet.packetId to GPUPassBatchEligibility(
                    kind = GPUPassBatchKind.SolidFill,
                    queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                )
            },
        )
        val consumer = GPUDestinationSnapshotConsumerRef(
            groupingCommandId = "draw.consumer",
            renderTaskId = render.taskId,
            packetId = consumerPacket.packetId,
            commandId = GPUDrawCommandID(consumerPacket.commandIdValue),
        )
        val destination = GPUTask.DestinationSnapshots(
            taskId = GPUTaskID("task.copy.destination"),
            recordingId = recordingId,
            phase = GPUTaskPhase.Copy,
            payload = GPUDestinationSnapshotTaskPayload(
                grouping = GPUDestinationSnapshotGroupingResult(
                    groups = listOf(
                        GPUDestinationSnapshotGroup(
                            key = snapshotKey(generation),
                            logicalBounds = COPY_BOUNDS,
                            members = listOf(
                                GPUDestinationReadMember("draw.consumer", 0, COPY_BOUNDS),
                            ),
                            copiedBytes = 16,
                            decisionDump = listOf("native-planner-smoke"),
                        ),
                    ),
                    materializations = listOf(
                        GPUDestinationSnapshotMaterialization.TextureCopy(0, COPY_BOUNDS),
                    ),
                    totalCopiedBytes = 16,
                    refusals = emptyList(),
                    decisionDump = listOf("native-planner-smoke"),
                ),
                operations = listOf(
                    GPUDestinationSnapshotOperation.TextureCopy(
                        groupIndex = 0,
                        source = TARGET,
                        snapshot = SNAPSHOT,
                        logicalBounds = COPY_BOUNDS,
                        copyLayout = GPUTextureCopyLayout(256, 2),
                        consumers = listOf(consumer),
                    ),
                ),
            ),
        )
        val readback = GPUTask.Readback(
            taskId = GPUTaskID("task.readback"),
            recordingId = recordingId,
            phase = GPUTaskPhase.Readback,
            source = TARGET,
            staging = STAGING,
            request = GPUFrameReadbackRequest(
                readbackRequestId,
                TARGET_BOUNDS,
                GPUReadbackPixelFormat.Rgba8Unorm,
                GPUColorInterpretation("srgb-premul"),
            ),
        )
        val dependencies = listOf(
            taskDependency(prepare, destination),
            taskDependency(destination, render),
            taskDependency(render, readback),
        )
        return GPUTaskList(
            frameId = frameId,
            capabilitySeal = seal,
            recordingSeals = listOf(
                GPURecordingSeal(recordingId, 0, "compat", "replay", seal.sealHash),
            ),
            expectedReplayKeyHash = "replay",
            tasks = listOf(readback, render, destination, prepare),
            dependencies = dependencies,
            phaseOrder = GPUTaskPhase.entries,
            memoryBudget = memoryBudget(),
        )
    }

    private fun taskDependency(from: GPUTask, to: GPUTask): GPUTaskDependency =
        GPUTaskDependency(
            fromTaskId = from.taskId,
            toTaskId = to.taskId,
            dependencyKind = "native-planner-smoke",
            useToken = GPUTaskUseToken("${from.taskId.value}->${to.taskId.value}"),
            reasonCode = "native.planner.order",
        )

    private fun destinationCopyPlan(
        generation: GPUDeviceGenerationID,
        invalid: InvalidRoute? = null,
    ): GPUFramePlan {
        val capabilities = capabilities(
            if (invalid == null) "destination-copy-native" else "destination-copy-${invalid.name}",
        )
        val frameId = GPUFrameID(10_601)
        val seal = GPUFrameCapabilitySeal.capture(frameId, generation, capabilities)
        val background = packet(
            id = "packet.background",
            commandId = 1,
            task = "background",
            rect = TARGET_BOUNDS,
            color = listOf(0f, 0f, 1f, 1f),
            blendPlan = null,
        )
        val destinationBlend = when (invalid) {
            InvalidRoute.MissingDestinationBlend -> null
            InvalidRoute.MultiplyDestinationBlend -> GPUBlendPlan.ShaderBlendWithDstRead(
                mode = GPUBlendMode.MULTIPLY,
                formulaId = "multiply@v1",
                sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
            )
            InvalidRoute.OverlayDestinationBlend -> GPUBlendPlan.ShaderBlendWithDstRead(
                mode = GPUBlendMode.OVERLAY,
                formulaId = "overlay@v1",
                sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
            )
            InvalidRoute.WrongDifferenceFormula -> GPUBlendPlan.ShaderBlendWithDstRead(
                mode = GPUBlendMode.DIFFERENCE,
                formulaId = "difference@v2",
                sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
            )
            InvalidRoute.WrongDifferenceCoverage -> GPUBlendPlan.ShaderBlendWithDstRead(
                mode = GPUBlendMode.DIFFERENCE,
                formulaId = "difference@v1",
                sourceCoverageEncoding = GPUSourceCoverageEncoding.ScalarCoverageInShader,
            )
            else -> GPUBlendPlan.ShaderBlendWithDstRead(
                mode = GPUBlendMode.DIFFERENCE,
                formulaId = "difference@v1",
                sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
            )
        }
        val consumerPacket = packet(
            id = "packet.consumer",
            commandId = 2,
            task = "consumer",
            rect = COPY_BOUNDS,
            color = listOf(1f, 0f, 0f, 1f),
            blendPlan = destinationBlend,
        )
        val consumer = GPUDestinationSnapshotConsumerRef(
            groupingCommandId = "draw.consumer",
            renderTaskId = CONSUMER_TASK,
            packetId = if (invalid == InvalidRoute.MismatchedPacket) {
                GPUDrawPacketID("packet.other")
            } else {
                consumerPacket.packetId
            },
            commandId = GPUDrawCommandID(2),
        )
        val copyBounds = if (invalid == InvalidRoute.CopyOutsideTarget) {
            GPUPixelBounds(3, 3, 5, 5)
        } else {
            COPY_BOUNDS
        }
        val consumers = if (invalid == InvalidRoute.DuplicateConsumer) {
            listOf(consumer, consumer.copy(groupingCommandId = "draw.consumer.duplicate"))
        } else {
            listOf(consumer)
        }
        val prepare = GPUFrameStep.PrepareResourcesStep(
            requests = listOf(
                GPUResourcePreparationRequest(
                    TARGET,
                    GPUFrameTextureDescriptor(TARGET_BOUNDS, RGBA8, 1),
                    GPUFrameResourceRole.SceneTarget,
                    when (invalid) {
                        InvalidRoute.TargetMissingRenderAttachment -> setOf(GPUFrameResourceUsage.CopySource)
                        InvalidRoute.TargetMissingCopySource -> setOf(GPUFrameResourceUsage.RenderAttachment)
                        InvalidRoute.TargetExtraTextureBinding -> setOf(
                            GPUFrameResourceUsage.RenderAttachment,
                            GPUFrameResourceUsage.CopySource,
                            GPUFrameResourceUsage.TextureBinding,
                        )
                        else -> setOf(
                            GPUFrameResourceUsage.RenderAttachment,
                            GPUFrameResourceUsage.CopySource,
                        )
                    },
                    GPUFrameResourceLifetime.FrameLocal,
                    64,
                    "scene",
                ),
                GPUResourcePreparationRequest(
                    SNAPSHOT,
                    GPUFrameTextureDescriptor(copyBounds, RGBA8, 1),
                    GPUFrameResourceRole.DestinationSnapshot,
                    when (invalid) {
                        InvalidRoute.SnapshotMissingCopyDestination ->
                            setOf(GPUFrameResourceUsage.TextureBinding)
                        InvalidRoute.SnapshotMissingTextureBinding ->
                            setOf(GPUFrameResourceUsage.CopyDestination)
                        else -> setOf(
                            GPUFrameResourceUsage.CopyDestination,
                            GPUFrameResourceUsage.TextureBinding,
                        )
                    },
                    GPUFrameResourceLifetime.FrameLocal,
                    16,
                    "destination-snapshot",
                ),
                GPUResourcePreparationRequest(
                    STAGING,
                    GPUFrameBufferDescriptor(1_024, 4),
                    GPUFrameResourceRole.ReadbackStaging,
                    setOf(GPUFrameResourceUsage.CopyDestination, GPUFrameResourceUsage.MapRead),
                    GPUFrameResourceLifetime.FrameLocal,
                    1_024,
                    "readback",
                ),
            ),
            sourceTaskIds = listOf(GPUTaskID("task.prepare")),
        )
        val renderBackground = renderStep(
            background,
            GPUTaskID("task.render.background"),
            GPULoadStorePlan("clear", GPUStorePlan.Store),
        )
        val copy = GPUFrameStep.CopyDestinationStep(
            source = TARGET,
            sourceKey = snapshotKey(generation, invalid),
            snapshot = SNAPSHOT,
            logicalBounds = copyBounds,
            copyLayout = GPUTextureCopyLayout(256, 2),
            consumers = consumers,
            sourceTaskIds = listOf(GPUTaskID("task.copy.destination")),
        )
        val renderConsumer = renderStep(
            consumerPacket,
            CONSUMER_TASK,
            GPULoadStorePlan("load", GPUStorePlan.Store),
            if (invalid == InvalidRoute.RenderSampleMismatch) {
                GPUSamplePlan.MultisampleFrame(4)
            } else {
                GPUSamplePlan.SingleSampleFrame
            },
        )
        val readback = GPUFrameStep.ReadbackCopyStep(
            source = TARGET,
            staging = STAGING,
            request = GPUFrameReadbackRequest(
                GPUReadbackRequestID("readback.destination-copy"),
                TARGET_BOUNDS,
                GPUReadbackPixelFormat.Rgba8Unorm,
                GPUColorInterpretation("srgb-premul"),
            ),
            sourceTaskIds = listOf(GPUTaskID("task.readback")),
        )
        return GPUFramePlan(
            frameId = frameId,
            capabilitySeal = seal,
            recordingSeals = listOf(
                GPURecordingSeal(
                    GPURecordingID("recording.destination-copy"),
                    0,
                    "compat",
                    "replay",
                    seal.sealHash,
                ),
            ),
            steps = listOf(prepare, renderBackground, copy, renderConsumer, readback),
            memoryBudget = memoryBudget(),
            diagnostics = emptyList(),
        )
    }

    private fun packet(
        id: String,
        commandId: Int,
        task: String,
        rect: GPUPixelBounds,
        color: List<Float>,
        blendPlan: GPUBlendPlan?,
        passId: String = "pass.$task",
    ): GPUDrawPacket {
        val semantic = GPUSolidPayloadGatherer().gatherSemantic(
            GPUPayloadGatherPlan(
                planHash = "solid.gather.$task",
                commandFamily = "FillRect",
                materialAssemblyHash = "solid.material.$task",
                renderStepIdentity = "rect.fill.coverage",
                writePlanHash = "solid.write.$task",
                bindingPlanHash = "solid.binding.$task",
                uploadPlanHash = "solid.upload.$task",
                dedupScope = "pass.$task",
            ),
            GPUMaterialPayload(
                materialKeyHash = "solid.material.key.$task",
                payloadClass = "solid-rgba-rect",
                valueFacts = mapOf(
                    "command.id" to commandId.toString(),
                    "rect.left" to rect.left.toFloat().toString(),
                    "rect.top" to rect.top.toFloat().toString(),
                    "rect.right" to rect.right.toFloat().toString(),
                    "rect.bottom" to rect.bottom.toFloat().toString(),
                    "radii.topLeft" to "0.0",
                    "radii.topRight" to "0.0",
                    "radii.bottomRight" to "0.0",
                    "radii.bottomLeft" to "0.0",
                    "color.r" to color[0].toString(),
                    "color.g" to color[1].toString(),
                    "color.b" to color[2].toString(),
                    "color.a" to color[3].toString(),
                ),
                resourceFacts = emptyMap(),
                diagnosticLabel = "solid.$task",
            ),
        )
        return GPUDrawPacket(
            packetId = GPUDrawPacketID(id),
            commandIdValue = commandId,
            analysisRecordId = "analysis.$task",
            passId = passId,
            layerId = "root",
            bindingListId = "bindings.$task",
            insertionReasonCode = "solid-fill",
            sortKey = commandId.toLong(),
            sortKeyPreimage = "order.$commandId",
            renderStepId = GPURenderStepID("rect.fill.coverage"),
            renderStepVersion = 1,
            role = GPUDrawPacketRole.Shading,
            blendPlan = blendPlan,
            renderPipelineKey = GPURenderPipelineKey("pipeline.$task"),
            bindingLayoutHash = "layout.$task",
            uniformSlot = semantic.payloadRef.uniformSlot,
            semanticPayload = semantic,
            vertexSourceLabel = "fullscreen-triangle",
            targetStateHash = "target.rgba8unorm",
            originalPaintOrder = commandId,
            resourceGeneration = 1,
        )
    }

    private fun renderStep(
        packet: GPUDrawPacket,
        task: GPUTaskID,
        loadStore: GPULoadStorePlan,
        samplePlan: GPUSamplePlan = GPUSamplePlan.SingleSampleFrame,
    ) = GPUFrameStep.RenderPassStep(
        target = TARGET,
        loadStore = loadStore,
        samplePlan = samplePlan,
        drawPackets = listOf(packet),
        sourceTaskIds = listOf(task),
        batches = listOf(
            GPUFrameRenderBatch(
                "batch.${packet.packetId.value}",
                GPUPassBatchKind.SolidFill,
                listOf(packet),
                listOf(task),
            ),
        ),
    )

    private fun snapshotKey(
        generation: GPUDeviceGenerationID,
        invalid: InvalidRoute? = null,
    ) = GPUDestinationSnapshotGroupKey(
        target = GPUTargetIdentity(
            if (invalid == InvalidRoute.SourceTargetMismatch) "target.other" else TARGET.value,
        ),
        targetGeneration = if (invalid == InvalidRoute.SourceTargetGenerationMismatch) 2 else 1,
        deviceGeneration = if (invalid == InvalidRoute.SourceDeviceGenerationMismatch) {
            GPUDeviceGenerationID(generation.value + 1)
        } else {
            generation
        },
        format = if (invalid == InvalidRoute.SourceFormatMismatch) {
            GPUColorFormat("bgra8unorm")
        } else {
            RGBA8
        },
        colorInterpretation = if (invalid == InvalidRoute.SourceColorMismatch) {
            GPUColorInterpretation("linear-premul")
        } else {
            GPUColorInterpretation("srgb-premul")
        },
        sampleContinuation = if (invalid == InvalidRoute.SourceSampleMismatch) {
            GPUSampleContinuationKey(
                target = GPUTargetIdentity(TARGET.value),
                targetGeneration = 1,
                deviceGeneration = generation,
                colorFormat = RGBA8,
                colorInterpretation = GPUColorInterpretation("srgb-premul"),
                samplePlan = GPUSamplePlan.MultisampleFrame(4),
                colorAttachment = GPUTargetIdentity("target.scene.msaa"),
                depthStencilAttachment = null,
            )
        } else {
            null
        },
        sourceIntermediate = null,
    )

    private fun expectedDifferencePixels(): ByteArray = ByteArray(64).also { bytes ->
        for (y in 0 until 4) for (x in 0 until 4) {
            val offset = (y * 4 + x) * 4
            if (x in 1 until 3 && y in 1 until 3) {
                bytes[offset] = 255.toByte()
                bytes[offset + 2] = 255.toByte()
            } else {
                bytes[offset + 2] = 255.toByte()
            }
            bytes[offset + 3] = 255.toByte()
        }
    }

    private fun expectedConsumerFirstClearPixels(): ByteArray = ByteArray(64).also { bytes ->
        for (y in 1 until 3) for (x in 1 until 3) {
            val offset = (y * 4 + x) * 4
            bytes[offset] = 255.toByte()
            bytes[offset + 3] = 255.toByte()
        }
    }

    private fun sceneTarget(generation: GPUDeviceGenerationID) = GPUSceneTarget(
        targetId = TARGET.value,
        resolvedTexture = GPUTextureResourceRef("prepared:${TARGET.value}"),
        retainedMsaaAttachment = null,
        width = 4,
        height = 4,
        format = RGBA8,
        colorInterpretation = GPUColorInterpretation("srgb-premul"),
        usages = setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource),
        sampleCount = 1,
        deviceGeneration = generation,
        targetGeneration = 1,
    )

    private fun capabilities(snapshotId: String) = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "wgpu4k", "native", "native"),
        facts = listOf(GPUCapabilityFact("limits", "test", "observed", true, "destination-copy")),
        snapshotId = snapshotId,
        limits = GPULimits(8_192, 256, 256, maxBufferSize = 1L shl 30),
        rendererFeatures = setOf(
            GPURendererFeature.RenderPass,
            GPURendererFeature.CopyUpload,
            GPURendererFeature.Readback,
        ),
    )

    private fun memoryBudget() = GPUFrameMemoryBudgetPlan(
        peakFrameTransientBytes = 2_112,
        targetResidentBytes = 64,
        categoryTotals = GPUFrameMemoryCategory.entries.associateWith { 0 },
        deviceLimitFacts = emptyList(),
        configuredAggregateBudgetBytes = 1L shl 30,
        diagnostic = null,
    )

    private fun completion(
        context: io.ygdrasil.webgpu.GLFWContext,
        generation: GPUDeviceGenerationID,
        suffix: String = "destination-copy-native",
    ) = GPUQueueCompletionAdapter(
        deviceGeneration = generation,
        requirement = GPUQueueCompletionCapabilityRequirement(
            implementationRevision = "wgpu4k.0.2.0-20260716.235022-2.$suffix",
            capability = "on-submitted-work-done",
        ),
        evidence = GPUQueueCompletionCapabilityEvidence(
            implementationRevision = "wgpu4k.0.2.0-20260716.235022-2.$suffix",
            capability = "on-submitted-work-done",
            accepted = true,
        ),
        invoker = GPUQueueCompletionInvoker { context.wgpuContext.device.queue.onSubmittedWorkDone() },
    )

    private object NoNativeSurfaceOutput : GPUSurfaceOutputProvider {
        override fun acquire(request: GPUSurfaceAcquisitionRequest): GPUSurfaceAcquisitionResult =
            GPUSurfaceAcquisitionResult.Unavailable(GPUSurfaceAcquisitionStatus.Timeout)

        override fun release(output: GPUAcquiredSurfaceOutput): GPUSurfaceReleaseResult =
            GPUSurfaceReleaseResult.Released
    }

    private object NoOpRetention : GPUFrameResourceRetention {
        override fun registerAfterSubmit(registration: GPUFrameRetentionRegistration) = Unit
        override fun complete(ticket: GPUQueueCompletionTicket, outcome: GPUQueueCompletionOutcome) = Unit
        override fun quarantine(
            registration: GPUFrameRetentionRegistration,
            diagnostic: org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic,
        ) = Unit
    }

    private enum class InvalidRoute {
        DuplicateConsumer,
        MismatchedPacket,
        MissingDestinationBlend,
        MultiplyDestinationBlend,
        OverlayDestinationBlend,
        WrongDifferenceFormula,
        WrongDifferenceCoverage,
        CopyOutsideTarget,
        TargetMissingRenderAttachment,
        TargetMissingCopySource,
        TargetExtraTextureBinding,
        SnapshotMissingCopyDestination,
        SnapshotMissingTextureBinding,
        SourceFormatMismatch,
        SourceColorMismatch,
        SourceTargetMismatch,
        SourceTargetGenerationMismatch,
        SourceDeviceGenerationMismatch,
        SourceSampleMismatch,
        RenderSampleMismatch,
    }

    private companion object {
        val TARGET = GPUFrameTargetRef("target.scene")
        val SNAPSHOT = GPUFrameTextureRef("texture.destination-snapshot")
        val STAGING = GPUFrameBufferRef("buffer.readback")
        val TARGET_BOUNDS = GPUPixelBounds(0, 0, 4, 4)
        val COPY_BOUNDS = GPUPixelBounds(1, 1, 3, 3)
        val RGBA8 = GPUColorFormat("rgba8unorm")
        val CONSUMER_TASK = GPUTaskID("task.render.consumer")
    }
}
