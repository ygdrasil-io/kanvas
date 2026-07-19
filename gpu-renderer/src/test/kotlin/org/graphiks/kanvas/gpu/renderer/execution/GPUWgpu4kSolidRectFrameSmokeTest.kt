package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.recording.canonicalSolidRectSrcOverBlendPlan

import io.ygdrasil.webgpu.glfwContextRenderer
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureView
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
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
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleContinuationKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleContinuationRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleLoadTransition
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleResolveAction
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleStoreAction
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.payloads.GPUMaterialPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadGatherPlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUSolidPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
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
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceCopyRegion
import org.graphiks.kanvas.gpu.renderer.resources.GPURetainedMsaaAttachmentSet
import org.graphiks.kanvas.gpu.renderer.resources.GPUSceneTarget
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUTargetIdentity
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome

class GPUWgpu4kSolidRectFrameSmokeTest {
    @Test
    fun `canonical target create view failure leaves texture in retryable setup ownership`() {
        val fixture = FailingPreparedTargetNative(failCreateView = true)
        val setup = GPUPreparedSceneSetupTransaction()

        assertFailsWith<IllegalStateException> {
            GPUWgpu4kPreparedSceneTarget.create(
                device = fixture.device,
                width = 4,
                height = 4,
                deviceGeneration = GPUDeviceGenerationID(90),
                targetGeneration = 3,
                lifecycle = GPUWgpu4kPreparedSceneTargetLifecycle(),
                setupTransaction = setup,
            )
        }
        assertEquals(1, setup.pendingResourceCount)
        assertFailsWith<IllegalStateException> { setup.close() }
        assertEquals(1, fixture.textureCloseAttempts)

        val quarantine = GPUPreparedSceneSetupRollbackQuarantine()
        quarantine.retain(setup)
        quarantine.close()

        assertEquals(2, fixture.textureCloseAttempts)
        assertEquals(0, setup.pendingResourceCount)
    }

    @Test
    fun `canonical target lifecycle failure retains view and texture until setup retry`() {
        val fixture = FailingPreparedTargetNative(failCreateView = false)
        val setup = GPUPreparedSceneSetupTransaction()
        val lifecycle = GPUWgpu4kPreparedSceneTargetLifecycle { _, _ ->
            error("lifecycle registration failed")
        }

        assertFailsWith<IllegalStateException> {
            GPUWgpu4kPreparedSceneTarget.create(
                device = fixture.device,
                width = 4,
                height = 4,
                deviceGeneration = GPUDeviceGenerationID(91),
                targetGeneration = 4,
                lifecycle = lifecycle,
                setupTransaction = setup,
            )
        }
        assertEquals(2, setup.pendingResourceCount)
        assertFailsWith<IllegalStateException> { setup.close() }
        assertEquals(1, fixture.viewCloseAttempts)
        assertEquals(1, fixture.textureCloseAttempts)

        val quarantine = GPUPreparedSceneSetupRollbackQuarantine()
        quarantine.retain(setup)
        quarantine.close()

        assertEquals(1, fixture.viewCloseAttempts)
        assertEquals(2, fixture.textureCloseAttempts)
        assertEquals(0, setup.pendingResourceCount)
    }

    @Test
    fun `prepared scene setup transaction closes partial resources once in reverse order`() {
        val releases = mutableListOf<String>()
        val transaction = GPUPreparedSceneSetupTransaction()

        assertFailsWith<IllegalStateException> {
            transaction.use { setup ->
                setup.own(AutoCloseable { releases += "target" })
                setup.own(AutoCloseable { releases += "backend" })
                error("setup failed")
            }
        }
        transaction.close()

        assertEquals(listOf("backend", "target"), releases)
    }

    @Test
    fun `prepared scene setup rollback keeps a failed target quarantined for parent teardown retry`() {
        var viewAttempts = 0
        var textureAttempts = 0
        val targetCloser = GPUPreparedSceneNativeTargetCloser(
            viewHandle = AutoCloseable {
                viewAttempts += 1
                if (viewAttempts == 1) error("view close failed")
            },
            textureHandle = AutoCloseable { textureAttempts += 1 },
        )
        val transaction = GPUPreparedSceneSetupTransaction().apply {
            own(targetCloser)
        }
        val parentRollbackQuarantine = GPUPreparedSceneSetupRollbackQuarantine()

        assertFailsWith<IllegalStateException> { transaction.close() }
        assertEquals(1, transaction.pendingResourceCount)
        assertEquals(1, viewAttempts)
        assertEquals(1, textureAttempts)

        parentRollbackQuarantine.retain(transaction)
        assertEquals(1, parentRollbackQuarantine.pendingTransactionCount)
        parentRollbackQuarantine.close()
        parentRollbackQuarantine.close()

        assertEquals(0, transaction.pendingResourceCount)
        assertEquals(0, parentRollbackQuarantine.pendingTransactionCount)
        assertEquals(2, viewAttempts)
        assertEquals(1, textureAttempts)
    }

    @Test
    fun `prepared native target closer retries only failed handle and quarantines until success`() {
        var viewAttempts = 0
        var textureAttempts = 0
        var terminalNotifications = 0
        val closer = GPUPreparedSceneNativeTargetCloser(
            viewHandle = AutoCloseable {
                viewAttempts += 1
                if (viewAttempts == 1) error("view close failed")
            },
            textureHandle = AutoCloseable { textureAttempts += 1 },
            onAllClosed = { terminalNotifications += 1 },
        )

        assertFailsWith<IllegalStateException> { closer.close() }
        assertFalse(closer.canBorrow)
        assertEquals(1, closer.quarantinedHandleCount)
        assertEquals(1, viewAttempts)
        assertEquals(1, textureAttempts)

        closer.close()
        closer.close()

        assertEquals(2, viewAttempts)
        assertEquals(1, textureAttempts)
        assertEquals(0, closer.quarantinedHandleCount)
        assertEquals(1, terminalNotifications)
    }

    @Test
    fun `disposing real backend parent closes an idle prepared child before device teardown`() {
        val backendSession = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backendSession != null)
        val child = backendSession!!.prepareSceneFrameSession(GPUOffscreenTargetRequest(4, 4))

        try {
            assertEquals(0L, child.nativeCounters().targetCloses)
            GPUBackendRuntimeNativeFactory.dispose()
            assertEquals(1L, child.nativeCounters().targetCloses)
        } finally {
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `prepared scene session reuses one native target across completion and readback frames`() {
        val backendSession = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backendSession != null)
        backendSession!!
        val runtimeCapabilities = requireNotNull(backendSession.capabilities)
        val generation = GPUDeviceGenerationID(
            runtimeCapabilities.snapshotId.substringAfterLast('-').toLong(),
        )
        val firstTasks = solidRectTaskList(
            generation = generation,
            capabilities = runtimeCapabilities,
            frameId = GPUFrameID(10_511L),
            includeReadback = false,
            readbackRequestId = GPUReadbackRequestID("readback.prepared.first"),
        )
        val secondRequestId = GPUReadbackRequestID("readback.prepared.second")
        val secondTasks = solidRectTaskList(
            generation = generation,
            capabilities = runtimeCapabilities,
            frameId = GPUFrameID(10_512L),
            includeReadback = true,
            readbackRequestId = secondRequestId,
        )
        val session = backendSession.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(4, 4, "rgba8unorm"),
        )
        var targetClosesAfterSessionClose = -1L
        try {
            val first = session.renderFrame(
                firstTasks,
                GPUSceneFrameOutputRequest.CurrentFrameCompletionOnly,
            )
            val firstTerminal = first.completion.toCompletableFuture().get(10, TimeUnit.SECONDS)
            assertEquals(
                GPUFrameStructuralOutcome.Succeeded,
                firstTerminal.outcome,
                "${firstTerminal.diagnostic?.code?.value}: ${firstTerminal.diagnostic?.message}",
            )
            assertEquals(GPUSceneFrameOutput.CurrentFrameCompletionOnly, firstTerminal.output)

            val second = session.renderFrame(
                secondTasks,
                GPUSceneFrameOutputRequest.ReadbackRgba(secondRequestId),
            )
            val secondTerminal = second.completion.toCompletableFuture().get(10, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, secondTerminal.outcome)
            val readback = assertIs<GPUSceneFrameOutput.ReadbackRgba>(secondTerminal.output)
            assertEquals(secondRequestId, readback.requestId)
            assertContentEquals(expectedPremultipliedRect(), readback.bytes)
            assertFalse(first.attemptId == second.attemptId)

            val active = session.nativeCounters()
            assertEquals(1L, active.targetCreations)
            assertEquals(0L, active.targetCloses)
            assertEquals(2L, active.targetNativeUses)
            assertEquals(2L, active.submits)
            assertEquals(1L, active.readbackCopies)
            assertEquals(0, active.activeNativePayloads)
            assertEquals(0, active.outputOwnedNativePayloads)
            assertEquals(0, active.quarantinedNativePayloads)
            assertEquals(2L, active.retentionRegistrations)
            assertEquals(2L, active.retentionCompletions)
            assertEquals(0L, active.retentionQuarantines)
            assertEquals(2L, active.frameCoordinatorCreations)
            assertEquals(2L, active.nativePayloadRegistrations)
            assertEquals(2, active.distinctRetentionTickets)
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
    fun `prepared scene batches solid packets and reuses invariant pipeline across two frames`() {
        val backendSession = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backendSession != null)
        backendSession!!
        val runtimeCapabilities = requireNotNull(backendSession.capabilities)
        val generation = GPUDeviceGenerationID(
            runtimeCapabilities.snapshotId.substringAfterLast('-').toLong(),
        )
        val firstTasks = batchedSolidRectTaskList(
            generation = generation,
            capabilities = runtimeCapabilities,
            frameId = GPUFrameID(10_513L),
            includeReadback = false,
            readbackRequestId = GPUReadbackRequestID("readback.prepared.batch.first"),
        )
        val secondRequestId = GPUReadbackRequestID("readback.prepared.batch.second")
        val secondTasks = batchedSolidRectTaskList(
            generation = generation,
            capabilities = runtimeCapabilities,
            frameId = GPUFrameID(10_514L),
            includeReadback = true,
            readbackRequestId = secondRequestId,
        )
        val session = backendSession.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(4, 4, "rgba8unorm"),
        )
        try {
            val first = session.renderFrame(
                firstTasks,
                GPUSceneFrameOutputRequest.CurrentFrameCompletionOnly,
            ).completion.toCompletableFuture().get(10, TimeUnit.SECONDS)
            assertEquals(
                GPUFrameStructuralOutcome.Succeeded,
                first.outcome,
                "${first.diagnostic?.code?.value}: ${first.diagnostic?.message}",
            )
            assertEquals(GPUSceneFrameOutput.CurrentFrameCompletionOnly, first.output)

            val second = session.renderFrame(
                secondTasks,
                GPUSceneFrameOutputRequest.ReadbackRgba(secondRequestId),
            ).completion.toCompletableFuture().get(10, TimeUnit.SECONDS)
            assertEquals(
                GPUFrameStructuralOutcome.Succeeded,
                second.outcome,
                "${second.diagnostic?.code?.value}: ${second.diagnostic?.message}",
            )
            assertContentEquals(
                expectedBatchedSolidRectPixels(),
                assertIs<GPUSceneFrameOutput.ReadbackRgba>(second.output).bytes,
            )

            val counters = session.nativeCounters()
            assertEquals(1L, counters.targetCreations)
            assertEquals(2L, counters.submits)
            assertEquals(1L, counters.readbackCopies)
            assertEquals(1L, counters.solidRectInvariantCreations)
            assertEquals(1L, counters.solidRectInvariantReuses)
            assertEquals(0L, counters.solidRectInvariantInvalidations)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `prepared scene session refuses stale device and target identity before native reuse`() {
        val backendSession = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backendSession != null)
        backendSession!!
        val runtimeCapabilities = requireNotNull(backendSession.capabilities)
        val generation = GPUDeviceGenerationID(
            runtimeCapabilities.snapshotId.substringAfterLast('-').toLong(),
        )
        val session = backendSession.prepareSceneFrameSession(GPUOffscreenTargetRequest(4, 4))
        try {
            val stale = session.renderFrame(
                solidRectTaskList(
                    generation = GPUDeviceGenerationID(generation.value + 1L),
                    capabilities = runtimeCapabilities,
                    frameId = GPUFrameID(10_513L),
                    includeReadback = false,
                    readbackRequestId = GPUReadbackRequestID("readback.prepared.stale"),
                ),
            ).completion.toCompletableFuture().get(2, TimeUnit.SECONDS)
            assertEquals("stale.prepared-scene-session.device-generation", stale.diagnostic?.code?.value)
            assertEquals(0L, session.nativeCounters().targetNativeUses)

            val accepted = session.renderFrame(
                solidRectTaskList(
                    generation = generation,
                    capabilities = runtimeCapabilities,
                    frameId = GPUFrameID(10_514L),
                    includeReadback = false,
                    readbackRequestId = GPUReadbackRequestID("readback.prepared.accepted"),
                ),
            ).completion.toCompletableFuture().get(10, TimeUnit.SECONDS)
            assertEquals(GPUFrameStructuralOutcome.Succeeded, accepted.outcome)

            val substituted = session.renderFrame(
                solidRectTaskList(
                    generation = generation,
                    capabilities = runtimeCapabilities,
                    frameId = GPUFrameID(10_515L),
                    includeReadback = false,
                    readbackRequestId = GPUReadbackRequestID("readback.prepared.substituted"),
                    renderTarget = OTHER_TARGET,
                ),
            ).completion.toCompletableFuture().get(2, TimeUnit.SECONDS)
            assertEquals("stale.prepared-scene-session.target-identity", substituted.diagnostic?.code?.value)
            assertEquals(1L, session.nativeCounters().submits)
            assertEquals(1L, session.nativeCounters().targetNativeUses)
        } finally {
            session.close()
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `one encoder keeps one 4x MSAA attachment across two copy breaks and resolves every pass`() =
        runBlocking {
            val context = glfwContextRenderer(4, 4, "kanvas-msaa-continuation", deferredRendering = true)
            val generation = GPUDeviceGenerationID(10_504L)
            val adapter = GPURuntimeResourceAdapter()
            val provider = GPUConcreteResourceProvider(leaseFactory = adapter)
            val completion = queueCompletion(context, generation)
            val mappingExecutor = Executors.newSingleThreadExecutor { task ->
                Thread(task, "kanvas-msaa-continuation-readback").apply { isDaemon = true }
            }
            val backend = GPUWgpu4kFrameEncodingBackend(
                generation,
                context.wgpuContext.device,
                context.wgpuContext.device.queue,
            )
            val materializer = GPUWgpu4kSolidRectFramePayloadMaterializer(
                context.wgpuContext.device,
                context.wgpuContext.device.queue,
            )
            try {
                val preflightResult = GPUFramePreflighter(
                    context = GPUFramePreflightContext(
                        targetId = TARGET.value,
                        deviceGeneration = generation,
                        targetGeneration = 1L,
                        resourceGenerations = mapOf(TARGET to 1L, SCRATCH to 2L, STAGING to 3L),
                    ),
                    capabilities = nativeCapabilities("wgpu4k-msaa-continuation"),
                    resourceProvider = provider,
                    completionProvider = completion,
                    surfaceProvider = NoNativeSurfaceOutput,
                    nativeBoundary = adapter.bindNativeFrameBoundary(provider, materializer),
                ).preflight(msaaContinuationReadbackPlan(generation))
                val prepared = assertIs<GPUFramePreflightResult.Prepared>(
                    preflightResult,
                    (preflightResult as? GPUFramePreflightResult.Refused)?.let {
                        "${it.diagnostic.code.value}: ${it.diagnostic.message} facts=${it.diagnostic.facts}"
                    },
                ).frame
                materializer.close()

                val executor = GPUFrameExecutor(
                    sceneTarget = sceneTarget(generation, sampleCount = 4),
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
                assertContentEquals(expectedMsaaContinuationPixels(), requireNotNull(terminal.readback).bytes)
                val counters = backend.counters()
                assertEquals(1, counters.encoders)
                assertEquals(3, counters.renderPasses)
                assertEquals(3, counters.draws)
                assertEquals(0, counters.destinationCopies)
                assertEquals(2, counters.resourceCopies)
                assertEquals(1, counters.readbackCopies)
                assertEquals(1, counters.finishes)
                assertEquals(1, counters.submits)
                assertEquals(3, counters.msaaResolves)
                val materialization = materializer.counters()
                assertEquals(1, materialization.msaaColorAttachmentCreations)
                assertEquals(1, materialization.distinctMsaaColorViews)
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
    fun `unsafe MSAA continuation shapes refuse during preflight before native handles`() = runBlocking {
        val context = glfwContextRenderer(4, 4, "kanvas-msaa-continuation-refusal", deferredRendering = true)
        val generation = GPUDeviceGenerationID(10_505L)
        val scenarios = listOf(
            InvalidMsaaContinuation.DirectCanonicalWrite to
                "unsupported.msaa.continuation_canonical_write",
            InvalidMsaaContinuation.DepthStencilRequired to
                "unsupported.msaa.continuation_depth_stencil_unavailable",
            InvalidMsaaContinuation.MissingRetainedProof to
                "unsupported.msaa.continuation_attachment_not_stored",
        )
        try {
            scenarios.forEach { (invalid, expectedCode) ->
                val snapshotId = "wgpu4k-msaa-refusal-${invalid.name.lowercase()}"
                val adapter = GPURuntimeResourceAdapter()
                val provider = GPUConcreteResourceProvider(leaseFactory = adapter)
                val completion = queueCompletion(context, generation)
                val materializer = GPUWgpu4kSolidRectFramePayloadMaterializer(
                    context.wgpuContext.device,
                    context.wgpuContext.device.queue,
                )
                try {
                    val result = GPUFramePreflighter(
                        context = GPUFramePreflightContext(
                            targetId = TARGET.value,
                            deviceGeneration = generation,
                            targetGeneration = 1L,
                            resourceGenerations = mapOf(TARGET to 1L, SCRATCH to 2L, STAGING to 3L),
                        ),
                        capabilities = nativeCapabilities(snapshotId),
                        resourceProvider = provider,
                        completionProvider = completion,
                        surfaceProvider = NoNativeSurfaceOutput,
                        nativeBoundary = adapter.bindNativeFrameBoundary(provider, materializer),
                    ).preflight(msaaContinuationReadbackPlan(generation, invalid))

                    assertEquals(
                        expectedCode,
                        assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
                    )
                    assertEquals(0L, materializer.counters().payloadResourceCreations)
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

    @Test
    fun `render and readback target substitution refuses before native resource creation`() = runBlocking {
        val context = glfwContextRenderer(4, 4, "kanvas-target-substitution", deferredRendering = true)
        val generation = GPUDeviceGenerationID(10_502L)
        val adapter = GPURuntimeResourceAdapter()
        val provider = GPUConcreteResourceProvider(leaseFactory = adapter)
        val completion = queueCompletion(context, generation)
        val materializer = GPUWgpu4kSolidRectFramePayloadMaterializer(
            context.wgpuContext.device,
            context.wgpuContext.device.queue,
        )
        try {
            val result = GPUFramePreflighter(
                context = GPUFramePreflightContext(
                    targetId = TARGET.value,
                    deviceGeneration = generation,
                    targetGeneration = 1L,
                    resourceGenerations = mapOf(TARGET to 1L, OTHER_TARGET to 1L, STAGING to 1L),
                ),
                capabilities = nativeCapabilities(),
                resourceProvider = provider,
                completionProvider = completion,
                surfaceProvider = NoNativeSurfaceOutput,
                nativeBoundary = adapter.bindNativeFrameBoundary(provider, materializer),
            ).preflight(
                solidRectReadbackPlan(
                    generation = generation,
                    renderTarget = OTHER_TARGET,
                    readbackSource = TARGET,
                ),
            )

            assertEquals(
                "unsupported.native-solid-rect.target-substitution",
                assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
            )
            assertEquals(0L, materializer.counters().payloadResourceCreations)
        } finally {
            adapter.close()
            materializer.close()
            completion.close()
            context.close()
        }
    }

    @Test
    fun `unknown solid load operation refuses before ephemeral cache or native allocation`() = runBlocking {
        val context = glfwContextRenderer(4, 4, "kanvas-solid-load-op", deferredRendering = true)
        val generation = GPUDeviceGenerationID(10_508L)
        val adapter = GPURuntimeResourceAdapter()
        val provider = GPUConcreteResourceProvider(leaseFactory = adapter)
        val completion = queueCompletion(context, generation)
        val materializer = GPUWgpu4kSolidRectFramePayloadMaterializer(
            context.wgpuContext.device,
            context.wgpuContext.device.queue,
        )
        try {
            val result = GPUFramePreflighter(
                context = GPUFramePreflightContext(
                    targetId = TARGET.value,
                    deviceGeneration = generation,
                    targetGeneration = 1L,
                    resourceGenerations = mapOf(TARGET to 1L, STAGING to 1L),
                ),
                capabilities = nativeCapabilities(),
                resourceProvider = provider,
                completionProvider = completion,
                surfaceProvider = NoNativeSurfaceOutput,
                nativeBoundary = adapter.bindNativeFrameBoundary(provider, materializer),
            ).preflight(solidRectReadbackPlan(generation, loadOp = "future-load"))

            assertEquals(
                "unsupported.native-solid-rect.load-operation",
                assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
            )
            assertEquals(0L, materializer.counters().payloadResourceCreations)
            assertEquals(0, adapter.quarantinedPreparedNativeFramePayloadCount)
        } finally {
            adapter.close()
            materializer.close()
            completion.close()
            context.close()
        }
    }

    @Test
    fun `native solid route refuses a blend plan that contradicts its SrcOver pipeline`() = runBlocking {
        val context = glfwContextRenderer(4, 4, "kanvas-solid-blend-authority", deferredRendering = true)
        val generation = GPUDeviceGenerationID(10_507L)
        val adapter = GPURuntimeResourceAdapter()
        val provider = GPUConcreteResourceProvider(leaseFactory = adapter)
        val completion = queueCompletion(context, generation)
        val materializer = GPUWgpu4kSolidRectFramePayloadMaterializer(
            context.wgpuContext.device,
            context.wgpuContext.device.queue,
        )
        try {
            val capabilities = nativeCapabilities("wgpu4k-solid-blend-authority")
            val result = GPUFramePreflighter(
                context = GPUFramePreflightContext(
                    targetId = TARGET.value,
                    deviceGeneration = generation,
                    targetGeneration = 1L,
                    resourceGenerations = mapOf(TARGET to 1L, STAGING to 1L),
                ),
                capabilities = capabilities,
                resourceProvider = provider,
                completionProvider = completion,
                surfaceProvider = NoNativeSurfaceOutput,
                nativeBoundary = adapter.bindNativeFrameBoundary(provider, materializer),
            ).preflight(
                solidRectReadbackPlan(
                    generation = generation,
                    capabilities = capabilities,
                    blendPlan = GPUBlendPlan.ShaderBlendNoDstRead(
                        mode = GPUBlendMode.SRC,
                        formulaId = "source@v1",
                        sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
                    ),
                ),
            )

            assertEquals(
                "unsupported.native-solid-rect.blend-authority",
                assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
            )
            assertEquals(0L, materializer.counters().payloadResourceCreations)
        } finally {
            adapter.close()
            materializer.close()
            completion.close()
            context.close()
        }
    }

    @Test
    fun `prepared target bounds and rgba8 format drive allocation and mismatches refuse before creation`() = runBlocking {
        val context = glfwContextRenderer(4, 4, "kanvas-target-descriptor", deferredRendering = true)
        val generation = GPUDeviceGenerationID(10_503L)
        val scenarios = listOf(
            Triple(GPUPixelBounds(0, 0, 5, 4), GPUColorFormat("rgba8unorm"), "unsupported.native-solid-rect.target-bounds"),
            Triple(GPUPixelBounds(0, 0, 4, 4), GPUColorFormat("bgra8unorm"), "unsupported.native-solid-rect.target-format"),
        )
        try {
            scenarios.forEachIndexed { index, (bounds, format, expectedCode) ->
                val adapter = GPURuntimeResourceAdapter()
                val provider = GPUConcreteResourceProvider(leaseFactory = adapter)
                val completion = queueCompletion(context, generation)
                val materializer = GPUWgpu4kSolidRectFramePayloadMaterializer(
                    context.wgpuContext.device,
                    context.wgpuContext.device.queue,
                )
                try {
                    val result = GPUFramePreflighter(
                        context = GPUFramePreflightContext(
                            targetId = TARGET.value,
                            deviceGeneration = generation,
                            targetGeneration = 1L,
                            resourceGenerations = mapOf(TARGET to 1L, STAGING to 1L),
                        ),
                        capabilities = nativeCapabilities(snapshotId = "wgpu4k-native-smoke-$index"),
                        resourceProvider = provider,
                        completionProvider = completion,
                        surfaceProvider = NoNativeSurfaceOutput,
                        nativeBoundary = adapter.bindNativeFrameBoundary(provider, materializer),
                    ).preflight(
                        solidRectReadbackPlan(
                            generation = generation,
                            targetBounds = bounds,
                            targetFormat = format,
                            capabilities = nativeCapabilities(snapshotId = "wgpu4k-native-smoke-$index"),
                        ),
                    )

                    assertEquals(
                        expectedCode,
                        assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
                    )
                    assertEquals(0L, materializer.counters().payloadResourceCreations)
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

    @Test
    fun `native target usage and lifetime contract refuses missing extra or wrong declarations before handles`() =
        runBlocking {
            val context = glfwContextRenderer(4, 4, "kanvas-target-contract", deferredRendering = true)
            val generation = GPUDeviceGenerationID(10_506L)
            val exactUsages = setOf(
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceUsage.CopySource,
            )
            val scenarios = listOf(
                TargetContractScenario(
                    usages = setOf(GPUFrameResourceUsage.RenderAttachment),
                    lifetime = GPUFrameResourceLifetime.FrameLocal,
                ),
                TargetContractScenario(
                    usages = exactUsages + GPUFrameResourceUsage.TextureBinding,
                    lifetime = GPUFrameResourceLifetime.FrameLocal,
                ),
                TargetContractScenario(
                    usages = exactUsages,
                    lifetime = GPUFrameResourceLifetime.RecordingLocal,
                ),
            )
            try {
                scenarios.forEachIndexed { index, scenario ->
                    val adapter = GPURuntimeResourceAdapter()
                    val provider = GPUConcreteResourceProvider(leaseFactory = adapter)
                    val completion = queueCompletion(context, generation)
                    val materializer = GPUWgpu4kSolidRectFramePayloadMaterializer(
                        context.wgpuContext.device,
                        context.wgpuContext.device.queue,
                    )
                    try {
                        val capabilities = nativeCapabilities("wgpu4k-target-contract-$index")
                        val result = GPUFramePreflighter(
                            context = GPUFramePreflightContext(
                                targetId = TARGET.value,
                                deviceGeneration = generation,
                                targetGeneration = 1L,
                                resourceGenerations = mapOf(TARGET to 1L, STAGING to 1L),
                            ),
                            capabilities = capabilities,
                            resourceProvider = provider,
                            completionProvider = completion,
                            surfaceProvider = NoNativeSurfaceOutput,
                            nativeBoundary = adapter.bindNativeFrameBoundary(provider, materializer),
                        ).preflight(
                            solidRectReadbackPlan(
                                generation = generation,
                                capabilities = capabilities,
                                targetUsages = scenario.usages,
                                targetLifetime = scenario.lifetime,
                            ),
                        )

                        assertEquals(
                            "unsupported.native-solid-rect.target-contract",
                            assertIs<GPUFramePreflightResult.Refused>(result).diagnostic.code.value,
                        )
                        assertEquals(0L, materializer.counters().payloadResourceCreations)
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

    @Test
    fun `one native submission renders canonical premultiplied solid rect and maps final rgba`() = runBlocking {
        val context = glfwContextRenderer(
            width = 4,
            height = 4,
            title = "kanvas-one-submit-solid-rect",
            deferredRendering = true,
        )
        val generation = GPUDeviceGenerationID(10_501L)
        val adapter = GPURuntimeResourceAdapter()
        val provider = GPUConcreteResourceProvider(leaseFactory = adapter)
        val completion = GPUQueueCompletionAdapter(
            deviceGeneration = generation,
            requirement = GPUQueueCompletionCapabilityRequirement(
                implementationRevision = "wgpu4k.0.2.0-20260716.235022-2",
                capability = "on-submitted-work-done",
            ),
            evidence = GPUQueueCompletionCapabilityEvidence(
                implementationRevision = "wgpu4k.0.2.0-20260716.235022-2",
                capability = "on-submitted-work-done",
                accepted = true,
            ),
            invoker = GPUQueueCompletionInvoker {
                context.wgpuContext.device.queue.onSubmittedWorkDone()
            },
        )
        val mappingExecutor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "kanvas-solid-rect-readback").apply { isDaemon = true }
        }
        val backend = GPUWgpu4kFrameEncodingBackend(
            generation,
            context.wgpuContext.device,
            context.wgpuContext.device.queue,
        )
        val materializer = GPUWgpu4kSolidRectFramePayloadMaterializer(
            context.wgpuContext.device,
            context.wgpuContext.device.queue,
        )
        try {
            val plan = solidRectReadbackPlan(generation)
            val preflight = GPUFramePreflighter(
                context = GPUFramePreflightContext(
                    targetId = TARGET.value,
                    deviceGeneration = generation,
                    targetGeneration = 1L,
                    resourceGenerations = mapOf(TARGET to 1L, STAGING to 1L),
                ),
                capabilities = nativeCapabilities(),
                resourceProvider = provider,
                completionProvider = completion,
                surfaceProvider = NoNativeSurfaceOutput,
                nativeBoundary = adapter.bindNativeFrameBoundary(
                    provider,
                    materializer,
                ),
            )
            val preflightResult = preflight.preflight(plan)
            val prepared = assertIs<GPUFramePreflightResult.Prepared>(
                preflightResult,
                (preflightResult as? GPUFramePreflightResult.Refused)?.let {
                    "${it.diagnostic.code.value}: ${it.diagnostic.message} facts=${it.diagnostic.facts}"
                },
            ).frame
            materializer.close()
            val output = prepared.resources.outputOwnedReadbacks.single()
            val materialization = materializer.counters()
            assertEquals(4, materialization.targetWidth)
            assertEquals(4, materialization.targetHeight)
            assertEquals("rgba8unorm", materialization.targetFormat)
            assertTrue(materialization.payloadResourceCreations > 0L)
            assertEquals(256L, output.layout.paddedBytesPerRow)
            assertEquals(4, output.layout.rowsPerImage)
            assertEquals(784L, output.layout.totalBufferBytes)
            assertEquals(1_024L, output.stagingLease.backingBufferBytes)
            assertEquals(64, output.layout.width * output.layout.height * output.layout.bytesPerPixel)

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
            val pixels = requireNotNull(terminal.readback).bytes
            assertContentEquals(expectedPremultipliedRect(), pixels)
            assertEquals(
                GPUWgpu4kFrameEncodingCounters(1, 1, 1, 1, 1, 1, 0),
                backend.counters(),
            )
        } finally {
            backend.close()
            mappingExecutor.shutdownNow()
            adapter.close()
            materializer.close()
            completion.close()
            context.close()
        }
    }

    private fun msaaContinuationReadbackPlan(
        generation: GPUDeviceGenerationID,
        invalid: InvalidMsaaContinuation? = null,
    ): GPUFramePlan {
        val direct = msaaContinuationDirectReadbackPlan(generation, invalid)
        if (invalid != null) return direct
        val recordingId = GPURecordingID("recording.msaa-continuation")
        val tasks = direct.steps.map { step ->
            when (step) {
                is GPUFrameStep.PrepareResourcesStep -> GPUTask.PrepareResources(
                    taskId = step.sourceTaskIds.single(),
                    recordingId = recordingId,
                    phase = GPUTaskPhase.Prepare,
                    requests = step.requests,
                )
                is GPUFrameStep.RenderPassStep -> GPUTask.Render(
                    taskId = step.sourceTaskIds.single(),
                    recordingId = recordingId,
                    phase = GPUTaskPhase.Render,
                    target = step.target,
                    loadStore = step.loadStore,
                    samplePlan = step.samplePlan,
                    drawPackets = step.drawPackets,
                    batchEligibilityByPacketId = step.drawPackets.associate { packet ->
                        packet.packetId to GPUPassBatchEligibility(
                            kind = GPUPassBatchKind.SolidFill,
                            queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                        )
                    },
                    sampleContinuationKey = requireNotNull(step.sampleContinuation).key,
                )
                is GPUFrameStep.CopyResourceStep -> GPUTask.Copy(
                    taskId = step.sourceTaskIds.single(),
                    recordingId = recordingId,
                    phase = GPUTaskPhase.Copy,
                    source = step.source,
                    destination = step.destination,
                    regions = step.regions,
                )
                is GPUFrameStep.ReadbackCopyStep -> GPUTask.Readback(
                    taskId = step.sourceTaskIds.single(),
                    recordingId = recordingId,
                    phase = GPUTaskPhase.Readback,
                    source = step.source,
                    staging = step.staging,
                    request = step.request,
                )
                else -> error("Unexpected E1 task source ${step::class.simpleName}")
            }
        }
        val dependencies = tasks.zipWithNext().mapIndexed { index, (from, to) ->
            GPUTaskDependency(
                fromTaskId = from.taskId,
                toTaskId = to.taskId,
                dependencyKind = "e1-order",
                useToken = GPUTaskUseToken("e1.$index"),
                reasonCode = "preserve.msaa.scope.order",
            )
        }
        return GPUFramePlanner.plan(
            GPUTaskList(
                frameId = direct.frameId,
                capabilitySeal = direct.capabilitySeal,
                recordingSeals = direct.recordingSeals,
                expectedReplayKeyHash = "replay",
                tasks = tasks,
                dependencies = dependencies,
                phaseOrder = GPUTaskPhase.entries,
                memoryBudget = direct.memoryBudget,
            ),
        )
    }

    private fun msaaContinuationDirectReadbackPlan(
        generation: GPUDeviceGenerationID,
        invalid: InvalidMsaaContinuation? = null,
    ): GPUFramePlan {
        val capabilities = nativeCapabilities(
            invalid?.let { "wgpu4k-msaa-refusal-${it.name.lowercase()}" }
                ?: "wgpu4k-msaa-continuation",
        )
        val frameId = GPUFrameID(10_504L)
        val seal = GPUFrameCapabilitySeal.capture(frameId, generation, capabilities)
        val samplePlan = GPUSamplePlan.MultisampleFrame(4)
        val continuationKey = GPUSampleContinuationKey(
            target = GPUTargetIdentity(TARGET.value),
            targetGeneration = 1L,
            deviceGeneration = generation,
            colorFormat = GPUColorFormat("rgba8unorm"),
            colorInterpretation = GPUColorInterpretation("srgb-premul"),
            samplePlan = samplePlan,
            attachmentAuthority = org.graphiks.kanvas.gpu.renderer.passes
                .GPUSampleAttachmentAuthority.SceneTargetRetained,
            colorAttachment = GPUTargetIdentity(MSAA_COLOR.value),
            depthStencilAttachment = if (invalid == InvalidMsaaContinuation.DepthStencilRequired) {
                GPUTargetIdentity("texture.msaa-depth-stencil")
            } else {
                null
            },
        )
        val packets = listOf(
            msaaPacket(
                id = "packet.msaa.red",
                commandId = 1,
                rect = GPUPixelBounds(0, 0, 2, 2),
                color = listOf(1f, 0f, 0f, 1f),
            ),
            msaaPacket(
                id = "packet.msaa.green",
                commandId = 2,
                rect = GPUPixelBounds(2, 0, 4, 2),
                color = listOf(0f, 1f, 0f, 1f),
            ),
            msaaPacket(
                id = "packet.msaa.blue",
                commandId = 3,
                rect = GPUPixelBounds(1, 2, 3, 4),
                color = listOf(0f, 0f, 1f, 1f),
            ),
        )
        val prepare = GPUFrameStep.PrepareResourcesStep(
            requests = listOf(
                GPUResourcePreparationRequest(
                    resource = TARGET,
                    descriptor = GPUFrameTextureDescriptor(
                        GPUPixelBounds(0, 0, 4, 4),
                        GPUColorFormat("rgba8unorm"),
                        1,
                    ),
                    role = GPUFrameResourceRole.SceneTarget,
                    usages = setOf(
                        GPUFrameResourceUsage.RenderAttachment,
                        GPUFrameResourceUsage.CopySource,
                    ),
                    lifetime = GPUFrameResourceLifetime.FrameLocal,
                    byteSize = 64L,
                    diagnosticLabel = "scene.msaa-resolve",
                ),
                GPUResourcePreparationRequest(
                    resource = SCRATCH,
                    descriptor = GPUFrameTextureDescriptor(
                        GPUPixelBounds(0, 0, 4, 4),
                        GPUColorFormat("rgba8unorm"),
                        1,
                    ),
                    role = GPUFrameResourceRole.CopyScratch,
                    usages = setOf(GPUFrameResourceUsage.CopyDestination),
                    lifetime = GPUFrameResourceLifetime.FrameLocal,
                    byteSize = 64L,
                    diagnosticLabel = "msaa-break-scratch",
                ),
                GPUResourcePreparationRequest(
                    resource = STAGING,
                    descriptor = GPUFrameBufferDescriptor(1_024L, 4),
                    role = GPUFrameResourceRole.ReadbackStaging,
                    usages = setOf(
                        GPUFrameResourceUsage.CopyDestination,
                        GPUFrameResourceUsage.MapRead,
                    ),
                    lifetime = GPUFrameResourceLifetime.FrameLocal,
                    byteSize = 1_024L,
                    diagnosticLabel = "readback.msaa-continuation",
                ),
            ),
            sourceTaskIds = listOf(GPUTaskID("task.prepare.msaa")),
        )
        val renders = packets.mapIndexed { index, packet ->
            val taskId = GPUTaskID("task.render.msaa.${index + 1}")
            GPUFrameStep.RenderPassStep(
                target = TARGET,
                loadStore = GPULoadStorePlan(
                    loadOp = if (index == 0) "clear" else "load",
                    storePlan = GPUStorePlan.Store,
                ),
                samplePlan = if (
                    invalid == InvalidMsaaContinuation.DirectCanonicalWrite && index == 1
                ) {
                    GPUSamplePlan.SingleSampleFrame
                } else {
                    samplePlan
                },
                drawPackets = listOf(packet),
                sourceTaskIds = listOf(taskId),
                batches = listOf(
                    GPUFrameRenderBatch(
                        batchId = "batch.msaa.${index + 1}",
                        kind = GPUPassBatchKind.SolidFill,
                        packets = listOf(packet),
                        sourceTaskIds = listOf(taskId),
                    ),
                ),
                sampleContinuation = continuationKey.takeUnless {
                    invalid == InvalidMsaaContinuation.MissingRetainedProof ||
                        (invalid == InvalidMsaaContinuation.DirectCanonicalWrite && index == 1)
                }?.let { key ->
                    GPUSampleContinuationRequest(
                        key = key,
                        loadTransition = if (index == 0) {
                            GPUSampleLoadTransition.FreshClear
                        } else {
                            GPUSampleLoadTransition.RetainedLoad
                        },
                        storeAction = GPUSampleStoreAction.Store,
                        resolveAction = GPUSampleResolveAction.ResolveCanonical,
                    )
                },
            )
        }
        fun copyBreak(index: Int) = GPUFrameStep.CopyResourceStep(
            source = TARGET,
            destination = SCRATCH,
            regions = listOf(
                GPUResourceCopyRegion(
                    sourceOffsetBytes = 0L,
                    destinationOffsetBytes = 0L,
                    logicalBounds = GPUPixelBounds(0, 0, 4, 4),
                    byteSize = 64L,
                ),
            ),
            sourceTaskIds = listOf(GPUTaskID("task.copy.msaa-break.$index")),
        )
        val readback = GPUFrameStep.ReadbackCopyStep(
            source = TARGET,
            staging = STAGING,
            request = GPUFrameReadbackRequest(
                requestId = GPUReadbackRequestID("readback.msaa-continuation"),
                sourceBounds = GPUPixelBounds(0, 0, 4, 4),
                pixelFormat = GPUReadbackPixelFormat.Rgba8Unorm,
                outputColorInterpretation = GPUColorInterpretation("srgb-premul"),
            ),
            sourceTaskIds = listOf(GPUTaskID("task.readback.msaa")),
        )
        val categories = GPUFrameMemoryCategory.entries.associateWith { category ->
            when (category) {
                GPUFrameMemoryCategory.CanonicalTarget -> 64L
                GPUFrameMemoryCategory.FrameLocalMsaaColor -> 256L
                GPUFrameMemoryCategory.ReusableScratch -> 64L
                GPUFrameMemoryCategory.ReadbackStaging -> 1_024L
                else -> 0L
            }
        }
        return GPUFramePlan(
            frameId = frameId,
            capabilitySeal = seal,
            recordingSeals = listOf(
                GPURecordingSeal(
                    GPURecordingID("recording.msaa-continuation"),
                    0,
                    "compat",
                    "replay",
                    seal.sealHash,
                ),
            ),
            steps = listOf(
                prepare,
                renders[0],
                copyBreak(1),
                renders[1],
                copyBreak(2),
                renders[2],
                readback,
            ),
            memoryBudget = GPUFrameMemoryBudgetPlan(
                peakFrameTransientBytes = 1_344L,
                targetResidentBytes = 64L,
                categoryTotals = categories,
                deviceLimitFacts = emptyList(),
                configuredAggregateBudgetBytes = 1L shl 30,
                diagnostic = null,
            ),
            diagnostics = emptyList(),
        )
    }

    private fun msaaPacket(
        id: String,
        commandId: Int,
        rect: GPUPixelBounds,
        color: List<Float>,
    ): GPUDrawPacket {
        val semantic = GPUSolidPayloadGatherer().gatherSemantic(
            GPUPayloadGatherPlan(
                planHash = "solid.gather.$id",
                commandFamily = "FillRect",
                materialAssemblyHash = "solid.material.$id",
                renderStepIdentity = "rect.fill.coverage",
                writePlanHash = "solid.write.$id",
                bindingPlanHash = "solid.binding.$id",
                uploadPlanHash = "solid.upload.$id",
                dedupScope = "pass.$id",
            ),
            GPUMaterialPayload(
                materialKeyHash = "solid.material.key.$id",
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
                diagnosticLabel = "solid.$id",
            ),
        )
        return GPUDrawPacket(
            packetId = GPUDrawPacketID(id),
            commandIdValue = commandId,
            analysisRecordId = "analysis.$id",
            passId = "pass.$id",
            layerId = "root",
            bindingListId = "bindings.$id",
            insertionReasonCode = "solid-fill-msaa",
            sortKey = commandId.toLong(),
            sortKeyPreimage = "order.$commandId",
            renderStepId = GPURenderStepID("rect.fill.coverage"),
            renderStepVersion = 1,
            role = GPUDrawPacketRole.Shading,
            blendPlan = canonicalSolidRectSrcOverBlendPlan(),
            renderPipelineKey = GPURenderPipelineKey("pipeline.$id.msaa4"),
            bindingLayoutHash = "layout.$id",
            uniformSlot = semantic.payloadRef.uniformSlot,
            semanticPayload = semantic,
            vertexSourceLabel = "fullscreen-triangle",
            targetStateHash = "target.rgba8unorm.msaa4",
            originalPaintOrder = commandId,
            resourceGeneration = 1L,
        )
    }

    private fun solidRectReadbackPlan(
        generation: GPUDeviceGenerationID,
        renderTarget: GPUFrameTargetRef = TARGET,
        readbackSource: GPUFrameTargetRef = renderTarget,
        targetBounds: GPUPixelBounds = GPUPixelBounds(0, 0, 4, 4),
        targetFormat: GPUColorFormat = GPUColorFormat("rgba8unorm"),
        capabilities: GPUCapabilities = nativeCapabilities(),
        targetUsages: Set<GPUFrameResourceUsage> = setOf(
            GPUFrameResourceUsage.RenderAttachment,
            GPUFrameResourceUsage.CopySource,
        ),
        targetLifetime: GPUFrameResourceLifetime = GPUFrameResourceLifetime.FrameLocal,
        frameId: GPUFrameID = GPUFrameID(10_501L),
        readbackRequestId: GPUReadbackRequestID = GPUReadbackRequestID("readback.native-smoke"),
        blendPlan: GPUBlendPlan = canonicalSolidRectSrcOverBlendPlan(),
        loadOp: String = "clear",
    ): GPUFramePlan {
        val seal = GPUFrameCapabilitySeal.capture(frameId, generation, capabilities)
        val semantic = GPUSolidPayloadGatherer().gatherSemantic(
            GPUPayloadGatherPlan(
                planHash = "solid.gather.native-smoke",
                commandFamily = "FillRect",
                materialAssemblyHash = "solid.material.native-smoke",
                renderStepIdentity = "rect.fill.coverage",
                writePlanHash = "solid.write.native-smoke",
                bindingPlanHash = "solid.binding.native-smoke",
                uploadPlanHash = "solid.upload.native-smoke",
                dedupScope = "pass.native-smoke",
            ),
            GPUMaterialPayload(
                materialKeyHash = "solid.material.key.native-smoke",
                payloadClass = "solid-rgba-rect",
                valueFacts = mapOf(
                    "command.id" to "1",
                    "rect.left" to "1.0",
                    "rect.top" to "1.0",
                    "rect.right" to "3.0",
                    "rect.bottom" to "3.0",
                    "radii.topLeft" to "0.0",
                    "radii.topRight" to "0.0",
                    "radii.bottomRight" to "0.0",
                    "radii.bottomLeft" to "0.0",
                    "color.r" to "1.0",
                    "color.g" to "0.0",
                    "color.b" to "0.0",
                    "color.a" to "0.5",
                ),
                resourceFacts = emptyMap(),
                diagnosticLabel = "solid.native-smoke",
            ),
        )
        val canonical = semantic.payloadRef.uniformBlock!!.bytes
        val floats = ByteBuffer.wrap(ByteArray(canonical.size) { canonical[it].toByte() })
            .order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1f, floats.getFloat(32))
        assertEquals(0.5f, floats.getFloat(44))
        val packet = GPUDrawPacket(
            packetId = GPUDrawPacketID("packet.native-smoke"),
            commandIdValue = 1,
            analysisRecordId = "analysis.native-smoke",
            passId = "pass.native-smoke",
            layerId = "root",
            bindingListId = "bindings.native-smoke",
            insertionReasonCode = "solid-fill",
            sortKey = 1L,
            sortKeyPreimage = "order.1",
            renderStepId = GPURenderStepID("rect.fill.coverage"),
            renderStepVersion = 1,
            role = GPUDrawPacketRole.Shading,
            blendPlan = blendPlan,
            renderPipelineKey = GPURenderPipelineKey("pipeline.native-smoke"),
            bindingLayoutHash = "layout.native-smoke",
            uniformSlot = semantic.payloadRef.uniformSlot,
            semanticPayload = semantic,
            vertexSourceLabel = "fullscreen-triangle",
            targetStateHash = "target.rgba8unorm",
            originalPaintOrder = 1,
            resourceGeneration = 1L,
        )
        val prepare = GPUFrameStep.PrepareResourcesStep(
            requests = buildList {
                (listOf(renderTarget, readbackSource).distinct()).forEach { target ->
                    add(
                GPUResourcePreparationRequest(
                    resource = target,
                    descriptor = GPUFrameTextureDescriptor(
                        targetBounds,
                        targetFormat,
                        1,
                    ),
                    role = GPUFrameResourceRole.SceneTarget,
                    usages = targetUsages,
                    lifetime = targetLifetime,
                    byteSize = 64L,
                    diagnosticLabel = "scene",
                ),
                    )
                }
                add(
                GPUResourcePreparationRequest(
                    resource = STAGING,
                    descriptor = GPUFrameBufferDescriptor(1_024L, 4),
                    role = GPUFrameResourceRole.ReadbackStaging,
                    usages = setOf(
                        GPUFrameResourceUsage.CopyDestination,
                        GPUFrameResourceUsage.MapRead,
                    ),
                    lifetime = GPUFrameResourceLifetime.FrameLocal,
                    byteSize = 1_024L,
                    diagnosticLabel = "readback",
                ),
                )
            },
            sourceTaskIds = listOf(GPUTaskID("task.prepare")),
        )
        val render = GPUFrameStep.RenderPassStep(
            target = renderTarget,
            loadStore = GPULoadStorePlan(loadOp, GPUStorePlan.Store),
            samplePlan = GPUSamplePlan.SingleSampleFrame,
            drawPackets = listOf(packet),
            sourceTaskIds = listOf(GPUTaskID("task.render")),
            batches = listOf(
                GPUFrameRenderBatch(
                    batchId = "batch.native-smoke",
                    kind = GPUPassBatchKind.SolidFill,
                    packets = listOf(packet),
                    sourceTaskIds = listOf(GPUTaskID("task.render")),
                ),
            ),
        )
        val request = GPUFrameReadbackRequest(
            requestId = readbackRequestId,
            sourceBounds = GPUPixelBounds(0, 0, 4, 4),
            pixelFormat = GPUReadbackPixelFormat.Rgba8Unorm,
            outputColorInterpretation = GPUColorInterpretation("srgb-premul"),
        )
        val readback = GPUFrameStep.ReadbackCopyStep(
            source = readbackSource,
            staging = STAGING,
            request = request,
            sourceTaskIds = listOf(GPUTaskID("task.readback")),
        )
        return GPUFramePlan(
            frameId = frameId,
            capabilitySeal = seal,
            recordingSeals = listOf(
                GPURecordingSeal(GPURecordingID("recording.native-smoke"), 0, "compat", "replay", seal.sealHash),
            ),
            steps = listOf(prepare, render, readback),
            memoryBudget = memoryBudget(),
            diagnostics = emptyList(),
        )
    }

    private fun solidRectTaskList(
        generation: GPUDeviceGenerationID,
        capabilities: GPUCapabilities,
        frameId: GPUFrameID,
        includeReadback: Boolean,
        readbackRequestId: GPUReadbackRequestID,
        renderTarget: GPUFrameTargetRef = TARGET,
    ): GPUTaskList {
        val plan = solidRectReadbackPlan(
            generation = generation,
            capabilities = capabilities,
            frameId = frameId,
            readbackRequestId = readbackRequestId,
            renderTarget = renderTarget,
        )
        val recordingId = plan.recordingSeals.single().recordingId
        val tasks = plan.steps.mapNotNull { step ->
            when (step) {
                is GPUFrameStep.PrepareResourcesStep -> GPUTask.PrepareResources(
                    taskId = step.sourceTaskIds.single(),
                    recordingId = recordingId,
                    phase = GPUTaskPhase.Prepare,
                    requests = if (includeReadback) {
                        step.requests
                    } else {
                        step.requests.filter { it.role != GPUFrameResourceRole.ReadbackStaging }
                    },
                )
                is GPUFrameStep.RenderPassStep -> GPUTask.Render(
                    taskId = step.sourceTaskIds.single(),
                    recordingId = recordingId,
                    phase = GPUTaskPhase.Render,
                    target = step.target,
                    loadStore = step.loadStore,
                    samplePlan = step.samplePlan,
                    drawPackets = step.drawPackets,
                    batchEligibilityByPacketId = step.drawPackets.associate { packet ->
                        packet.packetId to GPUPassBatchEligibility(
                            kind = GPUPassBatchKind.SolidFill,
                            queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                        )
                    },
                )
                is GPUFrameStep.ReadbackCopyStep -> if (includeReadback) {
                    GPUTask.Readback(
                        taskId = step.sourceTaskIds.single(),
                        recordingId = recordingId,
                        phase = GPUTaskPhase.Readback,
                        source = step.source,
                        staging = step.staging,
                        request = step.request,
                    )
                } else {
                    null
                }
                else -> error("Unexpected prepared scene task source ${step::class.simpleName}")
            }
        }
        val dependencies = tasks.zipWithNext().mapIndexed { index, (from, to) ->
            GPUTaskDependency(
                fromTaskId = from.taskId,
                toTaskId = to.taskId,
                dependencyKind = "prepared-scene-order",
                useToken = GPUTaskUseToken("prepared-scene.$index"),
                reasonCode = "preserve.prepared-scene.order",
            )
        }
        return GPUTaskList(
            frameId = frameId,
            capabilitySeal = plan.capabilitySeal,
            recordingSeals = plan.recordingSeals,
            expectedReplayKeyHash = "replay",
            tasks = tasks,
            dependencies = dependencies,
            phaseOrder = GPUTaskPhase.entries,
            memoryBudget = plan.memoryBudget,
        )
    }

    private fun batchedSolidRectTaskList(
        generation: GPUDeviceGenerationID,
        capabilities: GPUCapabilities,
        frameId: GPUFrameID,
        includeReadback: Boolean,
        readbackRequestId: GPUReadbackRequestID,
    ): GPUTaskList {
        val base = solidRectTaskList(
            generation = generation,
            capabilities = capabilities,
            frameId = frameId,
            includeReadback = includeReadback,
            readbackRequestId = readbackRequestId,
        )
        val render = base.tasks.filterIsInstance<GPUTask.Render>().single()
        val template = render.drawPackets.single()
        val specs = listOf(
            SolidRectPacketSpec(
                commandId = 1,
                left = 0f,
                top = 0f,
                right = 3f,
                bottom = 3f,
                red = 1f,
                green = 0f,
                blue = 0f,
                scissorBoundsHash = "scissor_0.0_0.0_2.0_4.0",
            ),
            SolidRectPacketSpec(
                commandId = 2,
                left = 1f,
                top = 1f,
                right = 4f,
                bottom = 4f,
                red = 0f,
                green = 0f,
                blue = 1f,
                scissorBoundsHash = "scissor_1.0_1.0_4.0_3.0",
            ),
        )
        val packets = specs.map { spec ->
            val semantic = solidSemantic(spec)
            GPUDrawPacket(
                packetId = GPUDrawPacketID("packet.native-batch.${spec.commandId}"),
                commandIdValue = spec.commandId,
                analysisRecordId = "analysis.native-batch.${spec.commandId}",
                passId = template.passId,
                layerId = template.layerId,
                bindingListId = "bindings.native-batch.${spec.commandId}",
                insertionReasonCode = template.insertionReasonCode,
                sortKey = spec.commandId.toLong(),
                sortKeyPreimage = "paint-order:${spec.commandId}",
                renderStepId = template.renderStepId,
                renderStepVersion = template.renderStepVersion,
                role = template.role,
                blendPlan = template.blendPlan,
                renderPipelineKey = template.renderPipelineKey,
                computePipelineKey = template.computePipelineKey,
                bindingLayoutHash = template.bindingLayoutHash,
                uniformSlot = semantic.payloadRef.uniformSlot,
                resourceSlot = template.resourceSlot,
                semanticPayload = semantic,
                vertexSourceLabel = template.vertexSourceLabel,
                scissorBoundsHash = spec.scissorBoundsHash,
                targetStateHash = template.targetStateHash,
                originalPaintOrder = spec.commandId,
                resourceGeneration = template.resourceGeneration,
                diagnostics = template.diagnostics,
            )
        }
        val batchedRender = GPUTask.Render(
            taskId = render.taskId,
            recordingId = render.recordingId,
            phase = render.phase,
            target = render.target,
            loadStore = render.loadStore,
            samplePlan = render.samplePlan,
            resourceUses = render.resourceUses,
            provisionalSegmentKey = render.provisionalSegmentKey,
            drawPackets = packets,
            batchEligibilityByPacketId = packets.associate { packet ->
                packet.packetId to GPUPassBatchEligibility(
                    kind = GPUPassBatchKind.SolidFill,
                    queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                )
            },
            sampleContinuationKey = render.sampleContinuationKey,
            compositeMembership = render.compositeMembership,
        )
        return GPUTaskList(
            frameId = base.frameId,
            capabilitySeal = base.capabilitySeal,
            recordingSeals = base.recordingSeals,
            expectedReplayKeyHash = base.expectedReplayKeyHash,
            tasks = base.tasks.map { if (it === render) batchedRender else it },
            dependencies = base.dependencies,
            phaseOrder = base.phaseOrder,
            memoryBudget = base.memoryBudget,
            diagnostics = base.diagnostics,
        )
    }

    private fun solidSemantic(spec: SolidRectPacketSpec): GPUDrawSemanticPayload.SolidRect =
        GPUSolidPayloadGatherer().gatherSemantic(
            GPUPayloadGatherPlan(
                planHash = "solid.gather.native-batch.${spec.commandId}",
                commandFamily = "FillRect",
                materialAssemblyHash = "solid.material.native-batch",
                renderStepIdentity = "rect.fill.coverage",
                writePlanHash = "solid.write.native-batch",
                bindingPlanHash = "solid.binding.native-batch",
                uploadPlanHash = "solid.upload.native-batch",
                dedupScope = "pass.native-batch",
            ),
            GPUMaterialPayload(
                materialKeyHash = "solid.material.key.native-batch.${spec.commandId}",
                payloadClass = "solid-rgba-rect",
                valueFacts = mapOf(
                    "command.id" to spec.commandId.toString(),
                    "rect.left" to spec.left.toString(),
                    "rect.top" to spec.top.toString(),
                    "rect.right" to spec.right.toString(),
                    "rect.bottom" to spec.bottom.toString(),
                    "radii.topLeft" to "0.0",
                    "radii.topRight" to "0.0",
                    "radii.bottomRight" to "0.0",
                    "radii.bottomLeft" to "0.0",
                    "color.r" to spec.red.toString(),
                    "color.g" to spec.green.toString(),
                    "color.b" to spec.blue.toString(),
                    "color.a" to "1.0",
                ),
                resourceFacts = emptyMap(),
                diagnosticLabel = "solid.native-batch.${spec.commandId}",
            ),
        )

    private data class SolidRectPacketSpec(
        val commandId: Int,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val red: Float,
        val green: Float,
        val blue: Float,
        val scissorBoundsHash: String,
    )

    private fun expectedPremultipliedRect(): ByteArray = ByteArray(64).also { bytes ->
        for (y in 1 until 3) for (x in 1 until 3) {
            val offset = (y * 4 + x) * 4
            bytes[offset] = 128.toByte()
            bytes[offset + 3] = 128.toByte()
        }
    }

    private fun expectedBatchedSolidRectPixels(): ByteArray = ByteArray(64).also { bytes ->
        fun pixel(x: Int, y: Int, red: Int, green: Int, blue: Int, alpha: Int) {
            val offset = (y * 4 + x) * 4
            bytes[offset] = red.toByte()
            bytes[offset + 1] = green.toByte()
            bytes[offset + 2] = blue.toByte()
            bytes[offset + 3] = alpha.toByte()
        }
        pixel(0, 0, 255, 0, 0, 255)
        pixel(1, 0, 255, 0, 0, 255)
        for (y in 1..2) {
            pixel(0, y, 255, 0, 0, 255)
            for (x in 1..3) pixel(x, y, 0, 0, 255, 255)
        }
    }

    private fun expectedMsaaContinuationPixels(): ByteArray = ByteArray(64).also { bytes ->
        for (y in 0 until 4) for (x in 0 until 4) {
            val offset = (y * 4 + x) * 4
            when {
                x in 0 until 2 && y in 0 until 2 -> bytes[offset] = 255.toByte()
                x in 2 until 4 && y in 0 until 2 -> bytes[offset + 1] = 255.toByte()
                x in 1 until 3 && y in 2 until 4 -> bytes[offset + 2] = 255.toByte()
                else -> continue
            }
            bytes[offset + 3] = 255.toByte()
        }
    }

    private fun sceneTarget(
        generation: GPUDeviceGenerationID,
        sampleCount: Int = 1,
    ): GPUSceneTarget = GPUSceneTarget(
        targetId = TARGET.value,
        resolvedTexture = GPUTextureResourceRef("prepared:${TARGET.value}"),
        retainedMsaaAttachment = if (sampleCount == 1) {
            null
        } else {
            GPURetainedMsaaAttachmentSet(
                color = GPUTextureResourceRef(MSAA_COLOR.value),
                depthStencil = null,
                targetId = TARGET.value,
                width = 4,
                height = 4,
                format = GPUColorFormat("rgba8unorm"),
                colorInterpretation = GPUColorInterpretation("srgb-premul"),
                sampleCount = sampleCount,
                deviceGeneration = generation,
                targetGeneration = 1L,
            )
        },
        width = 4,
        height = 4,
        format = GPUColorFormat("rgba8unorm"),
        colorInterpretation = GPUColorInterpretation("srgb-premul"),
        usages = setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource),
        sampleCount = sampleCount,
        deviceGeneration = generation,
        targetGeneration = 1L,
    )

    private fun nativeCapabilities(
        snapshotId: String = "wgpu4k-native-smoke",
    ): GPUCapabilities = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "wgpu4k", "native", "native"),
        facts = listOf(GPUCapabilityFact("limits", "test", "observed", true, "native-smoke")),
        snapshotId = snapshotId,
        limits = GPULimits(8_192L, 256L, 256L, maxBufferSize = 1L shl 30),
        rendererFeatures = setOf(
            GPURendererFeature.RenderPass,
            GPURendererFeature.CopyUpload,
            GPURendererFeature.Readback,
        ),
    )

    private fun memoryBudget(): GPUFrameMemoryBudgetPlan = GPUFrameMemoryBudgetPlan(
        peakFrameTransientBytes = 1_088L,
        targetResidentBytes = 64L,
        categoryTotals = GPUFrameMemoryCategory.entries.associateWith { 0L },
        deviceLimitFacts = emptyList(),
        configuredAggregateBudgetBytes = 1L shl 30,
        diagnostic = null,
    )

    private object NoNativeSurfaceOutput : GPUSurfaceOutputProvider {
        override fun acquire(request: GPUSurfaceAcquisitionRequest): GPUSurfaceAcquisitionResult =
            GPUSurfaceAcquisitionResult.Unavailable(GPUSurfaceAcquisitionStatus.Timeout)

        override fun release(output: GPUAcquiredSurfaceOutput): GPUSurfaceReleaseResult =
            GPUSurfaceReleaseResult.Released
    }

    private fun queueCompletion(
        context: io.ygdrasil.webgpu.GLFWContext,
        generation: GPUDeviceGenerationID,
    ): GPUQueueCompletionAdapter = GPUQueueCompletionAdapter(
        deviceGeneration = generation,
        requirement = GPUQueueCompletionCapabilityRequirement(
            implementationRevision = "wgpu4k.0.2.0-20260716.235022-2",
            capability = "on-submitted-work-done",
        ),
        evidence = GPUQueueCompletionCapabilityEvidence(
            implementationRevision = "wgpu4k.0.2.0-20260716.235022-2",
            capability = "on-submitted-work-done",
            accepted = true,
        ),
        invoker = GPUQueueCompletionInvoker {
            context.wgpuContext.device.queue.onSubmittedWorkDone()
        },
    )

    private object NoOpRetention : GPUFrameResourceRetention {
        override fun registerAfterSubmit(registration: GPUFrameRetentionRegistration) = Unit
        override fun complete(ticket: GPUQueueCompletionTicket, outcome: GPUQueueCompletionOutcome) = Unit
        override fun quarantine(registration: GPUFrameRetentionRegistration, diagnostic: org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic) = Unit
    }

    private companion object {
        val TARGET = GPUFrameTargetRef("target.scene")
        val OTHER_TARGET = GPUFrameTargetRef("target.other")
        val SCRATCH = GPUFrameTextureRef("texture.msaa-break-scratch")
        val MSAA_COLOR = GPUFrameTextureRef("texture.msaa-color")
        val STAGING = GPUFrameBufferRef("buffer.readback")
    }

    private enum class InvalidMsaaContinuation {
        DirectCanonicalWrite,
        DepthStencilRequired,
        MissingRetainedProof,
    }

    private data class TargetContractScenario(
        val usages: Set<GPUFrameResourceUsage>,
        val lifetime: GPUFrameResourceLifetime,
    )
}

private class FailingPreparedTargetNative(
    private val failCreateView: Boolean,
) {
    var textureCloseAttempts = 0
        private set
    var viewCloseAttempts = 0
        private set
    private var textureCloseFailuresRemaining = 1

    private val view: GPUTextureView = preparedTargetProxy(GPUTextureView::class.java) { methodName ->
        when (methodName) {
            "close" -> {
                viewCloseAttempts += 1
                Unit
            }
            "getLabel" -> "canonical-view"
            "setLabel" -> Unit
            "toString" -> "CanonicalView"
            else -> error("Unexpected view call: $methodName")
        }
    }

    private val texture: GPUTexture = preparedTargetProxy(GPUTexture::class.java) { methodName ->
        when (methodName) {
            "createView" -> if (failCreateView) error("createView failed") else view
            "close" -> {
                textureCloseAttempts += 1
                if (textureCloseFailuresRemaining > 0) {
                    textureCloseFailuresRemaining -= 1
                    error("texture close failed")
                }
                Unit
            }
            "getLabel" -> "canonical-texture"
            "setLabel" -> Unit
            "toString" -> "CanonicalTexture"
            else -> error("Unexpected texture call: $methodName")
        }
    }

    val device: GPUDevice = preparedTargetProxy(GPUDevice::class.java) { methodName ->
        when (methodName) {
            "createTexture" -> texture
            "toString" -> "CanonicalTargetDevice"
            else -> error("Unexpected device call: $methodName")
        }
    }
}

private fun <T : Any> preparedTargetProxy(
    type: Class<T>,
    invocation: (String) -> Any?,
): T = type.cast(
    Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, arguments ->
        when (method.name) {
            "equals" -> proxy === arguments?.singleOrNull()
            "hashCode" -> System.identityHashCode(proxy)
            else -> invocation(method.name)
        }
    },
)
