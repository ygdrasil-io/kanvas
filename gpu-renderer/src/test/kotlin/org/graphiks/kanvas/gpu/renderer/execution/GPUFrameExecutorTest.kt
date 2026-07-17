package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackLayout

import io.ygdrasil.webgpu.GPUComputePipeline
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureFormat
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroupKey
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommand
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandOperandBridge
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandStream
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleContinuationKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUComputePipelineKey
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.recording.GPUComputeDispatch
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameReadbackRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackPixelFormat
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPUDestinationSnapshotConsumerRef
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameCapabilitySeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingSeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUSurfaceOutputDescriptor
import org.graphiks.kanvas.gpu.renderer.recording.GPUSurfaceOutputRef
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskPhase
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskUseToken
import org.graphiks.kanvas.gpu.renderer.recording.GPUTargetTransitionKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreflightProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreparationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreparationInput
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourcePreparationSession
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUPhysicalPoolMaintenanceDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUPhysicalPoolRollbackSummary
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandReference
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedConcreteResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceCopyRegion
import org.graphiks.kanvas.gpu.renderer.resources.GPUBufferResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUReadbackStagingLease
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLease
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseCacheResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureCopyLayout
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext
import org.graphiks.kanvas.gpu.renderer.resources.GPUCommandOperandMaterializationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUSceneTarget
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralEventKind
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralCounter
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralPhase
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUTargetIdentity

class GPUFrameExecutorTest {
    @Test
    fun `rollback journal cannot own a completion ticket without its provider`() {
        val ticket = GPUQueueCompletionTicket(
            GPUQueueCompletionTicketID("ticket.orphan"),
            GPUFrameID(7),
            GPUDeviceGenerationID(7),
        )

        assertFailsWith<IllegalArgumentException> {
            GPUFrameCoreTestFixture.rollbackJournal(completionTicket = ticket)
        }
        assertFalse(GPUFrameCoreTestFixture.rollbackJournal().adoptCompletionTicket(ticket))
    }

    @Test
    fun `prepared frame requires the exact same completion ticket owned by rollback`() {
        assertFailsWith<IllegalArgumentException> {
            GPUFrameCoreTestFixture.preparedFrame(useDifferentRollbackTicket = true)
        }
    }

    @Test
    fun `native payload token is required and resolved before encoder creation`() {
        val events = mutableListOf<String>()

        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(events, requireNativeOperands = true),
            completion = RecordingCompletion(events),
            retention = RecordingRetention(events),
        ).execute(GPUFrameCoreTestFixture.preparedFrame())

        val immediate = assertIs<GPUFrameImmediateState.FailedBeforeSubmit>(handle.immediateState)
        assertEquals("unsupported.native-frame-payload.ownership-missing", immediate.diagnostic.code.value)
        assertTrue(events.none { it == "encoder:create" })
    }

    @Test
    fun `native payload identity mismatch refuses before encoder creation`() {
        val events = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val payload = GPUFrameCoreTestFixture.nativePayload(targetGeneration = 99)
        val ownership = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            adapter.registerReadyPayload(payload),
        ).ownership

        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(events, requireNativeOperands = true),
            completion = RecordingCompletion(events),
            retention = RecordingRetention(events),
        ).execute(GPUFrameCoreTestFixture.preparedFrame(nativePayloadOwnership = ownership))

        val immediate = assertIs<GPUFrameImmediateState.FailedBeforeSubmit>(handle.immediateState)
        assertEquals("stale.native-frame-payload.identity-mismatch", immediate.diagnostic.code.value)
        assertTrue(events.none { it == "encoder:create" })
        assertEquals(0, adapter.activePreparedNativeFramePayloadCount)
    }

    @Test
    fun `same generation native operand key mismatch refuses before encoder creation`() {
        val events = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val payload = GPUFrameCoreTestFixture.nativePayloadWithMismatchedSameGenerationOperandKey()
        val ownership = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            adapter.registerReadyPayload(payload),
        ).ownership

        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(events, requireNativeOperands = true),
            completion = RecordingCompletion(events),
            retention = RecordingRetention(events),
        ).execute(GPUFrameCoreTestFixture.preparedFrame(nativePayloadOwnership = ownership))

        val immediate = assertIs<GPUFrameImmediateState.FailedBeforeSubmit>(handle.immediateState)
        assertEquals("stale.native-frame-payload.identity-mismatch", immediate.diagnostic.code.value)
        assertTrue(events.none { it == "encoder:create" })
        assertEquals(0, adapter.activePreparedNativeFramePayloadCount)
    }

    @Test
    fun `unbound native draft refuses before encoder creation`() {
        val events = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val payload = GPUFrameCoreTestFixture.nativePayload()
        val ownership = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            adapter.registerPreparedNativeFrameDraft(GPUPreparedNativeFrameDraft(payload)),
        ).ownership

        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(events, requireNativeOperands = true),
            completion = RecordingCompletion(events),
            retention = RecordingRetention(events),
        ).execute(GPUFrameCoreTestFixture.preparedFrame(nativePayloadOwnership = ownership))

        val immediate = assertIs<GPUFrameImmediateState.FailedBeforeSubmit>(handle.immediateState)
        assertEquals("unsupported.native-frame-payload.draft-not-ready", immediate.diagnostic.code.value)
        assertTrue(events.none { it == "encoder:create" })
        assertEquals(0, adapter.activePreparedNativeFramePayloadCount)
    }

    @Test
    fun `native access exception before encoding completes the public handle`() {
        val events = mutableListOf<String>()
        val payload = GPUFrameCoreTestFixture.nativePayload()
        val ownership = GPUPreparedNativeFrameOwnership(
            GPUPreparedNativeFrameToken("native.throw.consume"),
            ThrowingNativePayloadAccess(payload, throwOnConsume = true),
        )

        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(events, requireNativeOperands = true),
            completion = RecordingCompletion(events),
            retention = RecordingRetention(events),
        ).execute(GPUFrameCoreTestFixture.preparedFrame(nativePayloadOwnership = ownership))

        val result = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)
        assertEquals("failed.native-frame-payload.access", result.diagnostic?.code?.value)
        assertTrue(events.none { it == "encoder:create" })
    }

    @Test
    fun `native access exceptions during terminal release still complete the public handle`() {
        val events = mutableListOf<String>()
        val payload = GPUFrameCoreTestFixture.nativePayload()
        val ownership = GPUPreparedNativeFrameOwnership(
            GPUPreparedNativeFrameToken("native.throw.release"),
            ThrowingNativePayloadAccess(payload, throwOnRelease = true, throwOnQuarantine = true),
        )
        val completion = RecordingCompletion(events)
        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(
                events,
                requireNativeOperands = true,
                expectedNativeOperands = payload.scopeOperands,
            ),
            completion = completion,
            retention = RecordingRetention(events),
        ).execute(GPUFrameCoreTestFixture.preparedFrame(nativePayloadOwnership = ownership))

        completion.complete(GPUQueueCompletionOutcome.Success)
        val result = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)
        assertEquals("failed.native-frame-payload.release", result.diagnostic?.code?.value)
    }

    @Test
    fun `readback completion waits for delayed map cleanup before pixels and output release`() {
        val events = mutableListOf<String>()
        val requestId = GPUReadbackRequestID("readback.main")
        val adapter = GPURuntimeResourceAdapter()
        val payload = GPUFrameCoreTestFixture.nativePayload(withReadback = true)
        val ownership = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            adapter.registerReadyPayload(payload),
        ).ownership
        val completion = RecordingCompletion(events)
        val readback = RecordingReadbackAccess(events, delayed = true)
        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(
                events,
                requireNativeOperands = true,
                expectedNativeOperands = payload.scopeOperands,
            ),
            completion = completion,
            retention = RecordingRetention(events),
            readback = readback,
        ).execute(
            GPUFrameCoreTestFixture.preparedFrame(
                nativePayloadOwnership = ownership,
                readbackRequestId = requestId,
            ),
        )

        completion.complete(GPUQueueCompletionOutcome.Success)

        assertFalse(handle.completion.toCompletableFuture().isDone)
        assertEquals(1, adapter.outputOwnedPreparedNativeFramePayloadCount)
        assertTrue(events.indexOf("readback:map") > events.indexOf("completion:arm:ticket.7"))
        adapter.close()
        assertEquals(1, adapter.outputOwnedPreparedNativeFramePayloadCount)

        val pixels = ByteArray(4 * 4 * 4) { index -> index.toByte() }
        readback.complete(GPUFrameReadbackMapDelivery.Pixels(requestId, pixels))

        val result = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)
        assertEquals(GPUFrameStructuralOutcome.Succeeded, result.outcome)
        assertContentEquals(pixels, requireNotNull(result.readback).bytes)
        assertEquals(0, adapter.outputOwnedPreparedNativeFramePayloadCount)
        assertTrue(
            events.indexOf("readback:pool-finalize:Released") >
                events.indexOf("readback:unmapped"),
        )
        assertEquals("readback:pool-finalize:Released", events.last())
    }

    @Test
    fun `asynchronous quarantined map failure completes future and releases mapping claim`() {
        val events = mutableListOf<String>()
        val requestId = GPUReadbackRequestID("readback.async-failure")
        val adapter = GPURuntimeResourceAdapter()
        val payload = GPUFrameCoreTestFixture.nativePayload(withReadback = true)
        val ownership = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            adapter.registerReadyPayload(payload),
        ).ownership
        val completion = RecordingCompletion(events)
        val readback = RecordingReadbackAccess(events, delayed = true)
        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(
                events,
                requireNativeOperands = true,
                expectedNativeOperands = payload.scopeOperands,
            ),
            completion = completion,
            retention = RecordingRetention(events),
            readback = readback,
        ).execute(
            GPUFrameCoreTestFixture.preparedFrame(
                nativePayloadOwnership = ownership,
                readbackRequestId = requestId,
            ),
        )
        completion.complete(GPUQueueCompletionOutcome.Success)
        assertFalse(handle.completion.toCompletableFuture().isDone)
        assertEquals(1, adapter.outputOwnedPreparedNativeFramePayloadCount)

        readback.complete(
            GPUFrameReadbackMapDelivery.Failed(
                executionDiagnostic("failed.test.async-map", "async map failed"),
                GPUFrameReadbackMapFailureSafety.Quarantine,
            ),
        )

        val result = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)
        assertEquals(GPUFrameStructuralOutcome.Failed, result.outcome)
        assertEquals("failed.test.async-map", result.diagnostic?.code?.value)
        assertEquals(0, adapter.outputOwnedPreparedNativeFramePayloadCount)
        assertEquals(1, adapter.quarantinedPreparedNativeFramePayloadCount)
        assertEquals("readback:pool-finalize:Quarantined", events.last())
    }

    @Test
    fun `failed queue completion quarantines readback without mapping`() {
        val events = mutableListOf<String>()
        val requestId = GPUReadbackRequestID("readback.failure")
        val adapter = GPURuntimeResourceAdapter()
        val payload = GPUFrameCoreTestFixture.nativePayload(withReadback = true)
        val ownership = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            adapter.registerReadyPayload(payload),
        ).ownership
        val completion = RecordingCompletion(events)
        val readback = RecordingReadbackAccess(events)
        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(
                events,
                requireNativeOperands = true,
                expectedNativeOperands = payload.scopeOperands,
            ),
            completion = completion,
            retention = RecordingRetention(events),
            readback = readback,
        ).execute(
            GPUFrameCoreTestFixture.preparedFrame(
                nativePayloadOwnership = ownership,
                readbackRequestId = requestId,
            ),
        )

        completion.complete(
            GPUQueueCompletionOutcome.Failure(GPUQueueCompletionFailureKind.DeviceLost),
        )

        val result = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)
        assertEquals(GPUFrameStructuralOutcome.Failed, result.outcome)
        assertTrue(events.any { it == "readback:completion-failed:DeviceLost" })
        assertFalse(events.any { it == "readback:map" })
        assertEquals(1, adapter.quarantinedPreparedNativeFramePayloadCount)
    }

    @Test
    fun `retention completion failure rejects submitted readback without mapping`() {
        val events = mutableListOf<String>()
        val requestId = GPUReadbackRequestID("readback.retention-complete-failure")
        val adapter = GPURuntimeResourceAdapter()
        val payload = GPUFrameCoreTestFixture.nativePayload(withReadback = true)
        val ownership = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            adapter.registerReadyPayload(payload),
        ).ownership
        val completion = RecordingCompletion(events)
        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(
                events,
                requireNativeOperands = true,
                expectedNativeOperands = payload.scopeOperands,
            ),
            completion = completion,
            retention = RecordingRetention(events, throwOnComplete = true),
            readback = RecordingReadbackAccess(events),
        ).execute(
            GPUFrameCoreTestFixture.preparedFrame(
                nativePayloadOwnership = ownership,
                readbackRequestId = requestId,
            ),
        )

        completion.complete(GPUQueueCompletionOutcome.Success)

        val result = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)
        assertEquals("failed.frame-execution.resource-lifetime", result.diagnostic?.code?.value)
        assertTrue(events.any { it == "readback:completion-failed:CallbackFailure" })
        assertFalse(events.any { it == "readback:map" })
        assertEquals(1, adapter.quarantinedPreparedNativeFramePayloadCount)
    }

    @Test
    fun `retention registration failure rejects submitted readback before completion arm`() {
        val events = mutableListOf<String>()
        val requestId = GPUReadbackRequestID("readback.retention-register-failure")
        val adapter = GPURuntimeResourceAdapter()
        val payload = GPUFrameCoreTestFixture.nativePayload(withReadback = true)
        val ownership = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            adapter.registerReadyPayload(payload),
        ).ownership
        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(
                events,
                requireNativeOperands = true,
                expectedNativeOperands = payload.scopeOperands,
            ),
            completion = RecordingCompletion(events),
            retention = RecordingRetention(events, throwOnRegister = true),
            readback = RecordingReadbackAccess(events),
        ).execute(
            GPUFrameCoreTestFixture.preparedFrame(
                nativePayloadOwnership = ownership,
                readbackRequestId = requestId,
            ),
        )

        val result = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)
        assertEquals("failed.frame-execution.resource-retention", result.diagnostic?.code?.value)
        assertTrue(events.any { it == "readback:completion-failed:CallbackFailure" })
        assertFalse(events.any { it == "readback:map" })
        assertEquals(1, adapter.quarantinedPreparedNativeFramePayloadCount)
    }

    @Test
    fun `readback mark submitted exception returns a failed handle after submit`() {
        val events = mutableListOf<String>()
        val requestId = GPUReadbackRequestID("readback.throw-submit")
        val adapter = GPURuntimeResourceAdapter()
        val payload = GPUFrameCoreTestFixture.nativePayload(withReadback = true)
        val ownership = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            adapter.registerReadyPayload(payload),
        ).ownership
        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(
                events,
                requireNativeOperands = true,
                expectedNativeOperands = payload.scopeOperands,
            ),
            completion = RecordingCompletion(events),
            retention = RecordingRetention(events),
            readback = RecordingReadbackAccess(events, throwOnSubmitted = true),
        ).execute(
            GPUFrameCoreTestFixture.preparedFrame(
                nativePayloadOwnership = ownership,
                readbackRequestId = requestId,
            ),
        )

        val result = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)
        assertIs<GPUFrameImmediateState.FailedAfterSubmit>(handle.immediateState)
        assertEquals("failed.frame-readback.lifecycle-callback", result.diagnostic?.code?.value)
        assertEquals(1, adapter.quarantinedPreparedNativeFramePayloadCount)
    }

    @Test
    fun `readback accept completion exception cannot escape callback and completes future`() {
        val events = mutableListOf<String>()
        val requestId = GPUReadbackRequestID("readback.throw-accept")
        val adapter = GPURuntimeResourceAdapter()
        val payload = GPUFrameCoreTestFixture.nativePayload(withReadback = true)
        val ownership = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            adapter.registerReadyPayload(payload),
        ).ownership
        val completion = RecordingCompletion(events)
        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(
                events,
                requireNativeOperands = true,
                expectedNativeOperands = payload.scopeOperands,
            ),
            completion = completion,
            retention = RecordingRetention(events),
            readback = RecordingReadbackAccess(events, throwOnAccept = true),
        ).execute(
            GPUFrameCoreTestFixture.preparedFrame(
                nativePayloadOwnership = ownership,
                readbackRequestId = requestId,
            ),
        )

        completion.complete(GPUQueueCompletionOutcome.Success)

        val result = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)
        assertEquals("failed.frame-readback.lifecycle-callback", result.diagnostic?.code?.value)
        assertEquals(1, adapter.quarantinedPreparedNativeFramePayloadCount)
    }

    @Test
    fun `readback reject completion exception cannot escape callback and completes future`() {
        val events = mutableListOf<String>()
        val requestId = GPUReadbackRequestID("readback.throw-reject")
        val adapter = GPURuntimeResourceAdapter()
        val payload = GPUFrameCoreTestFixture.nativePayload(withReadback = true)
        val ownership = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            adapter.registerReadyPayload(payload),
        ).ownership
        val completion = RecordingCompletion(events)
        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(
                events,
                requireNativeOperands = true,
                expectedNativeOperands = payload.scopeOperands,
            ),
            completion = completion,
            retention = RecordingRetention(events),
            readback = RecordingReadbackAccess(events, throwOnReject = true),
        ).execute(
            GPUFrameCoreTestFixture.preparedFrame(
                nativePayloadOwnership = ownership,
                readbackRequestId = requestId,
            ),
        )

        completion.complete(GPUQueueCompletionOutcome.Failure(GPUQueueCompletionFailureKind.DeviceLost))

        val result = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)
        assertEquals("failed.frame-readback.lifecycle-callback", result.diagnostic?.code?.value)
        assertEquals(1, adapter.quarantinedPreparedNativeFramePayloadCount)
    }

    @Test
    fun `readback mapper arm refusal completes terminally and quarantines without range read`() {
        val events = mutableListOf<String>()
        val requestId = GPUReadbackRequestID("readback.arm-refused")
        val adapter = GPURuntimeResourceAdapter()
        val payload = GPUFrameCoreTestFixture.nativePayload(withReadback = true)
        val ownership = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            adapter.registerReadyPayload(payload),
        ).ownership
        val completion = RecordingCompletion(events)
        val readback = RecordingReadbackAccess(events, armRefused = true)
        val handle = GPUFrameExecutor(
            GPUFrameCoreTestFixture.sceneTarget(),
            RecordingEncodingBackend(
                events = events,
                requireNativeOperands = true,
                expectedNativeOperands = payload.scopeOperands,
            ),
            completion,
            RecordingRetention(events),
            readback,
        ).execute(
            GPUFrameCoreTestFixture.preparedFrame( // exact output remains owned until arm refusal
                nativePayloadOwnership = ownership,
                readbackRequestId = requestId,
            ),
        )

        completion.complete(GPUQueueCompletionOutcome.Success)

        val result = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)
        assertEquals("failed.test.readback-map-arm", result.diagnostic?.code?.value)
        assertTrue(handle.completion.toCompletableFuture().isDone)
        assertFalse(events.any { it == "readback:range-read" })
        assertEquals(1, adapter.quarantinedPreparedNativeFramePayloadCount)
    }

    @Test
    fun `prepared frame carries native registry ownership only through rollback`() {
        val adapter = GPURuntimeResourceAdapter()
        val ownership = (
            adapter.registerReadyPayload(GPUFrameCoreTestFixture.nativePayload()) as
                GPUPreparedNativeFrameRegistration.Registered
            ).ownership
        val prepared = GPUFrameCoreTestFixture.preparedFrame(nativePayloadOwnership = ownership)

        assertTrue(prepared.rollback.hasNativePayload)
        assertFalse(
            PreparedGPUFrame::class.java.declaredFields.any { field ->
                field.name.contains("nativePayloadToken", ignoreCase = true)
            },
        )

        assertTrue(prepared.claimForRollback())
        prepared.rollback.execute()
        assertEquals(0, adapter.activePreparedNativeFramePayloadCount)
    }

    @Test
    fun `native payload is released only after successful completion`() {
        val events = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val payload = GPUFrameCoreTestFixture.nativePayload()
        val ownership = (
            adapter.registerReadyPayload(payload) as
                GPUPreparedNativeFrameRegistration.Registered
            ).ownership
        val completion = RecordingCompletion(events)
        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(
                events,
                requireNativeOperands = true,
                expectedNativeOperands = payload.scopeOperands,
            ),
            completion = completion,
            retention = RecordingRetention(events),
        ).execute(GPUFrameCoreTestFixture.preparedFrame(nativePayloadOwnership = ownership))

        assertEquals(1, adapter.activePreparedNativeFramePayloadCount)
        completion.complete(GPUQueueCompletionOutcome.Success)

        assertEquals(GPUFrameStructuralOutcome.Succeeded, handle.completion.toCompletableFuture().get().outcome)
        assertEquals(0, adapter.activePreparedNativeFramePayloadCount)
        assertEquals(0, adapter.quarantinedPreparedNativeFramePayloadCount)
    }

    @Test
    fun `native payload is quarantined after queue completion failure`() {
        val events = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val payload = GPUFrameCoreTestFixture.nativePayload()
        val ownership = (
            adapter.registerReadyPayload(payload) as
                GPUPreparedNativeFrameRegistration.Registered
            ).ownership
        val completion = RecordingCompletion(events)
        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(events),
            completion = completion,
            retention = RecordingRetention(events),
        ).execute(GPUFrameCoreTestFixture.preparedFrame(nativePayloadOwnership = ownership))

        completion.complete(
            GPUQueueCompletionOutcome.Failure(GPUQueueCompletionFailureKind.CallbackFailure),
        )

        assertEquals(GPUFrameStructuralOutcome.Failed, handle.completion.toCompletableFuture().get().outcome)
        assertEquals(0, adapter.activePreparedNativeFramePayloadCount)
        assertEquals(1, adapter.quarantinedPreparedNativeFramePayloadCount)
    }

    @Test
    fun `instrumented frame preserves render destination copy render compute order around target transition`() {
        val events = mutableListOf<String>()
        val completion = RecordingCompletion(events)
        val prepared = GPUFrameCoreTestFixture.orderedRenderCopyRenderComputeFrame()
        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(events),
            completion = completion,
            retention = RecordingRetention(events),
        ).execute(prepared)

        assertEquals(listOf(3), prepared.dependencyEvidence.map { it.sourceStepIndex })
        assertEquals(
            listOf(1, 2, 4, 5),
            prepared.encoderPlan.scopes.map { it.sourceStepIndex },
        )
        assertEquals(
            listOf(
                GPUEncoderOperationKind.Render,
                GPUEncoderOperationKind.CopyDestination,
                GPUEncoderOperationKind.Render,
                GPUEncoderOperationKind.Compute,
            ),
            prepared.encoderPlan.scopes.map { it.operationKind },
        )
        assertTrue(prepared.hostActions.isEmpty())
        assertEquals(
            listOf(
                "encoder:create",
                "scope:render-before",
                "scope:copy-destination",
                "scope:render-after",
                "scope:compute-filter",
                "encoder:finish",
                "queue:submit:command.1",
                "retention:register:prepared:target.scene,prepared:texture.destination",
                "completion:arm:ticket.7",
            ),
            events,
        )
        assertFalse(events.any { "transition" in it })

        completion.complete(GPUQueueCompletionOutcome.Success)
        assertEquals(
            GPUFrameStructuralOutcome.Succeeded,
            handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS).outcome,
        )
    }

    @Test
    fun `prepared frame execution claim is atomic under contention`() {
        val prepared = GPUFrameCoreTestFixture.preparedFrame()
        val workers = 16
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(workers)
        val claims = (0 until workers).map {
            pool.submit<Boolean> {
                ready.countDown()
                start.await()
                prepared.claimForExecution()
            }
        }
        ready.await(2, TimeUnit.SECONDS)
        start.countDown()

        assertEquals(1, claims.count { it.get(2, TimeUnit.SECONDS) })
        pool.shutdownNow()
    }

    @Test
    fun `prepared frame grants exactly one execution or rollback owner under contention`() {
        val prepared = GPUFrameCoreTestFixture.preparedFrame()
        val workers = 16
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(workers)
        val claims = (0 until workers).map { index ->
            pool.submit<Boolean> {
                ready.countDown()
                start.await()
                if (index % 2 == 0) prepared.claimForExecution() else prepared.claimForRollback()
            }
        }
        ready.await(2, TimeUnit.SECONDS)
        start.countDown()

        assertEquals(1, claims.count { it.get(2, TimeUnit.SECONDS) })
        assertFalse(prepared.claimForExecution())
        assertFalse(prepared.claimForRollback())
        pool.shutdownNow()
    }

    @Test
    fun `one frame uses one encoder one finish one submission and completes in two phases`() {
        val events = mutableListOf<String>()
        val completion = RecordingCompletion(events)
        val retention = RecordingRetention(events)
        val executor = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(events),
            completion = completion,
            retention = retention,
            attemptIdFactory = { org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameAttemptID("attempt.1") },
        )

        val handle = executor.execute(GPUFrameCoreTestFixture.preparedFrame(rollbackEvents = events))

        assertIs<GPUFrameImmediateState.Submitted>(handle.immediateState)
        assertFalse(handle.completion.toCompletableFuture().isDone)
        assertEquals(
            listOf(
                "encoder:create",
                "scope:copy",
                "scope:compute",
                "encoder:finish",
                "queue:submit:command.1",
                "retention:register:prepared:target.scene,prepared:texture.copy",
                "completion:arm:ticket.7",
            ),
            events,
        )

        completion.complete(GPUQueueCompletionOutcome.Success)
        val completed = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)

        assertEquals(GPUFrameStructuralOutcome.Succeeded, completed.outcome)
        assertEquals(GPUFrameStructuralPhase.Completed, completed.furthestPhase)
        assertEquals(1, completed.telemetry.counters.getValue(GPUFrameStructuralCounter.EncoderCreate))
        assertEquals(2, completed.telemetry.counters.getValue(GPUFrameStructuralCounter.EncoderScope))
        assertEquals(1, completed.telemetry.counters.getValue(GPUFrameStructuralCounter.EncoderFinish))
        assertEquals(1, completed.telemetry.counters.getValue(GPUFrameStructuralCounter.QueueSubmit))
        assertEquals(
            listOf(GPUEncoderOperationKind.Copy, GPUEncoderOperationKind.Compute),
            completed.encodedScopeKinds,
        )
        assertEquals(listOf("ticket.7:Success"), retention.completed)
        assertEquals(listOf("lease.compute"), retention.registered.single().commandLeaseIds)
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (completed.telemetry.events as MutableList).clear()
        }
        assertTrue(completed.telemetry.events.any { it.kind == GPUFrameStructuralEventKind.CompletionSucceeded })
    }

    @Test
    fun `stale target refuses before encoder creation and rolls preflight resources back`() {
        val events = mutableListOf<String>()
        val executor = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(targetGeneration = 2),
            backend = RecordingEncodingBackend(events),
            completion = RecordingCompletion(events),
            retention = RecordingRetention(events),
        )

        val handle = executor.execute(GPUFrameCoreTestFixture.preparedFrame(rollbackEvents = events))
        val result = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)

        assertIs<GPUFrameImmediateState.FailedBeforeSubmit>(handle.immediateState)
        assertEquals("stale.frame-execution.target-generation", result.diagnostic?.code?.value)
        assertEquals(listOf("rollback:resources"), events)
        assertEquals(GPUFrameStructuralPhase.Preflight, result.furthestPhase)
    }

    @Test
    fun `synchronous scope encoding failure rolls back and never finishes or submits`() {
        val events = mutableListOf<String>()
        val backend = RecordingEncodingBackend(events, failScope = "compute")
        val executor = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = backend,
            completion = RecordingCompletion(events),
            retention = RecordingRetention(events),
        )

        val handle = executor.execute(GPUFrameCoreTestFixture.preparedFrame(rollbackEvents = events))
        val result = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)

        assertIs<GPUFrameImmediateState.FailedBeforeSubmit>(handle.immediateState)
        assertEquals(GPUFrameStructuralOutcome.Failed, result.outcome)
        assertEquals("failed.frame-execution.encode", result.diagnostic?.code?.value)
        assertFalse(events.any { it.startsWith("encoder:finish") || it.startsWith("queue:submit") })
        assertEquals(1, backend.encoderDiscardCount)
        assertEquals(0, backend.liveEncoderCount)
        assertEquals(0, backend.pendingCommandBufferCount)
        assertEquals("rollback:resources", events.last())
    }

    @Test
    fun `submit ownership refusal or exception discards the finished command buffer exactly once`() {
        listOf(false, true).forEach { throwOnMarkSubmitted ->
            val events = mutableListOf<String>()
            val payload = GPUFrameCoreTestFixture.nativePayload()
            val backend = RecordingEncodingBackend(
                events,
                requireNativeOperands = true,
                expectedNativeOperands = payload.scopeOperands,
            )
            val access = ThrowingNativePayloadAccess(
                payload = payload,
                refuseMarkSubmitted = !throwOnMarkSubmitted,
                throwOnMarkSubmitted = throwOnMarkSubmitted,
            )
            val ownership = GPUPreparedNativeFrameOwnership(
                GPUPreparedNativeFrameToken("submit-transition-${if (throwOnMarkSubmitted) "throw" else "refuse"}"),
                access,
            )

            val result = GPUFrameExecutor(
                sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
                backend = backend,
                completion = RecordingCompletion(events),
                retention = RecordingRetention(events),
            ).execute(
                GPUFrameCoreTestFixture.preparedFrame(
                    rollbackEvents = events,
                    nativePayloadOwnership = ownership,
                ),
            ).completion.toCompletableFuture().get(2, TimeUnit.SECONDS)

            assertEquals("failed.native-frame-payload.submit-transition", result.diagnostic?.code?.value)
            assertEquals(1, backend.commandBufferDiscardCount)
            assertEquals(0, backend.liveEncoderCount)
            assertEquals(0, backend.pendingCommandBufferCount)
            assertFalse(events.any { it.startsWith("queue:submit") })
        }
    }

    @Test
    fun `command buffer discard failure stays typed and does not mask submit ownership refusal`() {
        val events = mutableListOf<String>()
        val payload = GPUFrameCoreTestFixture.nativePayload()
        val backend = RecordingEncodingBackend(
            events,
            requireNativeOperands = true,
            expectedNativeOperands = payload.scopeOperands,
            failCommandBufferDiscard = true,
        )
        val ownership = GPUPreparedNativeFrameOwnership(
            GPUPreparedNativeFrameToken("submit-transition-discard-failure"),
            ThrowingNativePayloadAccess(payload, refuseMarkSubmitted = true),
        )

        val result = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = backend,
            completion = RecordingCompletion(events),
            retention = RecordingRetention(events),
        ).execute(
            GPUFrameCoreTestFixture.preparedFrame(
                rollbackEvents = events,
                nativePayloadOwnership = ownership,
            ),
        ).completion.toCompletableFuture().get(2, TimeUnit.SECONDS)

        assertEquals("failed.native-frame-payload.submit-transition", result.diagnostic?.code?.value)
        assertEquals("Failed", result.diagnostic?.facts?.get("commandBufferDiscard"))
        assertEquals(1, backend.commandBufferDiscardCount)
        assertEquals(0, backend.pendingCommandBufferCount)
    }

    @Test
    fun `native scope encoding failure releases its unsubmitted payload`() {
        val events = mutableListOf<String>()
        val adapter = GPURuntimeResourceAdapter()
        val payload = GPUFrameCoreTestFixture.nativePayload()
        val ownership = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            adapter.registerReadyPayload(payload),
        ).ownership

        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(
                events,
                failScope = "compute",
                requireNativeOperands = true,
                expectedNativeOperands = payload.scopeOperands,
            ),
            completion = RecordingCompletion(events),
            retention = RecordingRetention(events),
        ).execute(
            GPUFrameCoreTestFixture.preparedFrame(
                rollbackEvents = events,
                nativePayloadOwnership = ownership,
            ),
        )

        val result = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)
        assertEquals("failed.frame-execution.encode", result.diagnostic?.code?.value)
        assertEquals(0, adapter.activePreparedNativeFramePayloadCount)
        assertEquals(0, adapter.quarantinedPreparedNativeFramePayloadCount)
        assertFalse(events.any { it.startsWith("queue:submit") })
    }

    @Test
    fun `second execution claim fails before encoder without rolling back first owner`() {
        val events = mutableListOf<String>()
        val completion = RecordingCompletion(events)
        val executor = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(events),
            completion = completion,
            retention = RecordingRetention(events),
        )
        val prepared = GPUFrameCoreTestFixture.preparedFrame(rollbackEvents = events)

        val first = executor.execute(prepared)
        val second = executor.execute(prepared)

        assertIs<GPUFrameImmediateState.Submitted>(first.immediateState)
        assertIs<GPUFrameImmediateState.FailedBeforeSubmit>(second.immediateState)
        assertEquals("failed.frame-execution.already-claimed", second.completion.toCompletableFuture().get().diagnostic?.code?.value)
        assertEquals(1, events.count { it == "encoder:create" })
        assertFalse(events.any { it == "rollback:resources" })
        completion.complete(GPUQueueCompletionOutcome.Success)
    }

    @Test
    fun `scene target identity mismatch fails before encoder and rolls back`() {
        val events = mutableListOf<String>()
        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(targetId = "target.other"),
            backend = RecordingEncodingBackend(events),
            completion = RecordingCompletion(events),
            retention = RecordingRetention(events),
        ).execute(GPUFrameCoreTestFixture.preparedFrame(rollbackEvents = events))

        val result = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)
        assertIs<GPUFrameImmediateState.FailedBeforeSubmit>(handle.immediateState)
        assertEquals("stale.frame-execution.target-identity", result.diagnostic?.code?.value)
        assertEquals(listOf("rollback:resources"), events)
    }

    @Test
    fun `submit exception is failed after submit and quarantines without rollback`() {
        val events = mutableListOf<String>()
        val retention = RecordingRetention(events)
        val executor = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(events, failSubmit = true),
            completion = RecordingCompletion(events),
            retention = retention,
        )

        val handle = executor.execute(GPUFrameCoreTestFixture.preparedFrame(rollbackEvents = events))
        val result = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)

        assertIs<GPUFrameImmediateState.FailedAfterSubmit>(handle.immediateState)
        assertEquals(GPUFrameStructuralOutcome.Failed, result.outcome)
        assertEquals("failed.frame-execution.submit", result.diagnostic?.code?.value)
        assertFalse(events.any { it == "rollback:resources" })
        assertEquals(1, retention.quarantinedRegistrations.size)
    }

    @Test
    fun `completion arm refusal is terminal after submit and quarantines retained resources`() {
        val events = mutableListOf<String>()
        val retention = RecordingRetention(events)
        val executor = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(events),
            completion = RecordingCompletion(events, armRefused = true),
            retention = retention,
        )

        val handle = executor.execute(GPUFrameCoreTestFixture.preparedFrame(rollbackEvents = events))
        val result = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)

        assertIs<GPUFrameImmediateState.FailedAfterSubmit>(handle.immediateState)
        assertEquals("failed.frame-execution.completion-arm", result.diagnostic?.code?.value)
        assertEquals(listOf("ticket.7:failed.frame-execution.completion-arm"), retention.quarantined)
        assertFalse(events.any { it.startsWith("rollback:") })
    }

    @Test
    fun `callback failure is the exact final result and quarantines submitted resources`() {
        val events = mutableListOf<String>()
        val completion = RecordingCompletion(events)
        val retention = RecordingRetention(events)
        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(events),
            completion = completion,
            retention = retention,
        ).execute(GPUFrameCoreTestFixture.preparedFrame())

        completion.complete(
            GPUQueueCompletionOutcome.Failure(
                kind = GPUQueueCompletionFailureKind.CallbackFailure,
                message = "backend callback failed",
            ),
        )
        val result = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)

        assertEquals(GPUFrameStructuralOutcome.Failed, result.outcome)
        assertEquals("failed.frame-execution.queue-completion", result.diagnostic?.code?.value)
        assertEquals("backend callback failed", result.diagnostic?.facts?.get("message"))
        assertTrue(retention.completed.isEmpty())
        assertEquals(listOf("ticket.7:failed.frame-execution.queue-completion"), retention.quarantined)
    }

    @Test
    fun `device loss callback quarantines the exact submitted registration`() {
        val events = mutableListOf<String>()
        val completion = RecordingCompletion(events)
        val retention = RecordingRetention(events)
        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(events),
            completion = completion,
            retention = retention,
        ).execute(GPUFrameCoreTestFixture.preparedFrame())

        completion.complete(GPUQueueCompletionOutcome.Failure(GPUQueueCompletionFailureKind.DeviceLost))
        handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)

        assertEquals(listOf("ticket.7:failed.frame-execution.queue-completion"), retention.quarantined)
        assertEquals(
            listOf("prepared:target.scene", "prepared:texture.copy"),
            retention.quarantinedRegistrations.single().ordinaryConcreteResources,
        )
    }

    @Test
    fun `mismatched completion ticket fails closed and quarantines`() {
        val events = mutableListOf<String>()
        val completion = RecordingCompletion(events)
        val retention = RecordingRetention(events)
        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(events),
            completion = completion,
            retention = retention,
        ).execute(GPUFrameCoreTestFixture.preparedFrame())

        completion.complete(GPUQueueCompletionOutcome.Success, GPUQueueCompletionTicketID("ticket.other"))
        val result = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)

        assertEquals("failed.frame-execution.completion-ticket", result.diagnostic?.code?.value)
        assertTrue(retention.completed.isEmpty())
        assertEquals(1, retention.quarantinedRegistrations.size)
    }

    @Test
    fun `lifetime completion exception still completes the public handle with failure`() {
        val events = mutableListOf<String>()
        val completion = RecordingCompletion(events)
        val retention = RecordingRetention(events, throwOnComplete = true)
        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(events),
            completion = completion,
            retention = retention,
        ).execute(GPUFrameCoreTestFixture.preparedFrame())

        completion.complete(GPUQueueCompletionOutcome.Success)
        val result = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)

        assertEquals("failed.frame-execution.resource-lifetime", result.diagnostic?.code?.value)
        assertEquals(1, retention.quarantinedRegistrations.size)
    }

    @Test
    fun `quarantine observer exception keeps a strong conservative ledger entry`() {
        val events = mutableListOf<String>()
        val completion = RecordingCompletion(events)
        val retention = RecordingRetention(events, throwOnQuarantine = true)
        val executor = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(events),
            completion = completion,
            retention = retention,
        )
        val handle = executor.execute(GPUFrameCoreTestFixture.preparedFrame(rollbackEvents = events))

        completion.complete(GPUQueueCompletionOutcome.Failure(GPUQueueCompletionFailureKind.DeviceLost))
        val result = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)

        assertEquals(GPUFrameStructuralOutcome.Failed, result.outcome)
        assertEquals(
            listOf("prepared:target.scene", "prepared:texture.copy"),
            executor.retainedRegistration(GPUQueueCompletionTicketID("ticket.7"))?.ordinaryConcreteResources,
        )
        assertFalse(events.any { it == "rollback:resources" })
    }

    @Test
    fun `registration observer exception fails after submit and remains strongly retained`() {
        val events = mutableListOf<String>()
        val retention = RecordingRetention(events, throwOnRegister = true)
        val executor = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(events),
            completion = RecordingCompletion(events),
            retention = retention,
        )

        val handle = executor.execute(GPUFrameCoreTestFixture.preparedFrame(rollbackEvents = events))
        val result = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)

        assertIs<GPUFrameImmediateState.FailedAfterSubmit>(handle.immediateState)
        assertEquals("failed.frame-execution.resource-retention", result.diagnostic?.code?.value)
        assertEquals(
            listOf("prepared:target.scene", "prepared:texture.copy"),
            executor.retainedRegistration(GPUQueueCompletionTicketID("ticket.7"))?.ordinaryConcreteResources,
        )
        assertFalse(events.any { it == "rollback:resources" })
    }

    @Test
    fun `completion arm exception fails after submit and quarantines`() {
        val events = mutableListOf<String>()
        val retention = RecordingRetention(events)
        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(events),
            completion = RecordingCompletion(events, throwOnArm = true),
            retention = retention,
        ).execute(GPUFrameCoreTestFixture.preparedFrame(rollbackEvents = events))

        val result = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)
        assertIs<GPUFrameImmediateState.FailedAfterSubmit>(handle.immediateState)
        assertEquals("failed.frame-execution.completion-arm", result.diagnostic?.code?.value)
        assertEquals(1, retention.quarantinedRegistrations.size)
        assertFalse(events.any { it == "rollback:resources" })
    }

    @Test
    fun `armed result for a different ticket fails after submit and quarantines immediately`() {
        val events = mutableListOf<String>()
        val retention = RecordingRetention(events)
        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(events),
            completion = RecordingCompletion(
                events,
                armedTicketId = GPUQueueCompletionTicketID("ticket.other"),
            ),
            retention = retention,
        ).execute(GPUFrameCoreTestFixture.preparedFrame(rollbackEvents = events))

        val result = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)
        assertIs<GPUFrameImmediateState.FailedAfterSubmit>(handle.immediateState)
        assertEquals("failed.frame-execution.completion-arm-ticket", result.diagnostic?.code?.value)
        assertEquals("ticket.7", result.diagnostic?.facts?.get("expected"))
        assertEquals("ticket.other", result.diagnostic?.facts?.get("actual"))
        assertEquals(1, retention.quarantinedRegistrations.size)
        assertFalse(events.any { it == "rollback:resources" })
    }

    @Test
    fun `retention ledger keeps ticket collisions and completes only the exact registration`() {
        val events = mutableListOf<String>()
        val observer = RecordingRetention(events)
        val ledger = GPUFrameRetentionLedger(observer)
        val ticket = GPUFrameCoreTestFixture.preparedFrame().completionTicket
        val first = GPUFrameRetentionRegistration(ticket, listOf("resource.first"), emptyList(), emptyList())
        val second = GPUFrameRetentionRegistration(ticket, listOf("resource.second"), emptyList(), emptyList())

        assertEquals(GPUFrameRetentionLedgerResult.Applied, ledger.registerAfterSubmit(first))
        assertEquals(GPUFrameRetentionLedgerResult.Applied, ledger.registerAfterSubmit(second))
        assertEquals(GPUFrameRetentionLedgerResult.Applied, ledger.registerAfterSubmit(first))
        assertEquals(listOf(first, second), ledger.retainedRegistrations(ticket.ticketId))
        assertEquals(2, observer.registered.size)

        assertEquals(GPUFrameRetentionLedgerResult.Applied, ledger.complete(first, GPUQueueCompletionOutcome.Success))
        assertEquals(listOf(second), ledger.retainedRegistrations(ticket.ticketId))
    }

    @Test
    fun `synchronous duplicate callback seals telemetry and lifetime exactly once`() {
        val events = mutableListOf<String>()
        val completion = RecordingCompletion(events, completeSynchronously = true)
        val retention = RecordingRetention(events)

        val handle = GPUFrameExecutor(
            sceneTarget = GPUFrameCoreTestFixture.sceneTarget(),
            backend = RecordingEncodingBackend(events),
            completion = completion,
            retention = retention,
        ).execute(GPUFrameCoreTestFixture.preparedFrame())

        val result = handle.completion.toCompletableFuture().get(2, TimeUnit.SECONDS)
        completion.complete(GPUQueueCompletionOutcome.Success)

        assertEquals(GPUFrameStructuralOutcome.Succeeded, result.outcome)
        assertEquals(listOf("ticket.7:Success"), retention.completed)
        assertEquals(1, result.telemetry.events.count { it.kind == GPUFrameStructuralEventKind.CompletionSucceeded })
    }

    private class RecordingEncodingBackend(
        private val events: MutableList<String>,
        private val failScope: String? = null,
        private val failSubmit: Boolean = false,
        private val requireNativeOperands: Boolean = false,
        private val expectedNativeOperands: List<GPUPreparedNativeScopeOperand>? = null,
        private val failCommandBufferDiscard: Boolean = false,
    ) : GPUFrameEncodingBackend {
        override val deviceGeneration = GPUDeviceGenerationID(7)
        override val encodingMode: GPUFrameEncodingMode = if (requireNativeOperands) {
            GPUFrameEncodingMode.NativeOperandsRequired
        } else {
            GPUFrameEncodingMode.Instrumented
        }
        private var encodedNativeOperandIndex = 0
        var liveEncoderCount = 0
            private set
        var pendingCommandBufferCount = 0
            private set
        var encoderDiscardCount = 0
            private set
        var commandBufferDiscardCount = 0
            private set

        override fun createCommandEncoder(label: String): GPUFrameCommandEncoder {
            events += "encoder:create"
            liveEncoderCount += 1
            return object : GPUFrameCommandEncoder {
                private var terminal = false

                override fun encode(
                    scope: GPUCommandEncoderScopePlan,
                    preparedFrame: PreparedGPUFrame,
                    sceneTarget: GPUSceneTarget,
                    nativeOperand: GPUPreparedNativeScopeOperand?,
                ) {
                    if (requireNativeOperands) {
                        checkNotNull(nativeOperand)
                        check(nativeOperand.sourceStepIndex == scope.sourceStepIndex)
                        check(nativeOperand.operationKind == scope.operationKind)
                        expectedNativeOperands?.let { expected ->
                            assertSame(expected[encodedNativeOperandIndex++], nativeOperand)
                        }
                    }
                    val short = scope.scopeLabel.removePrefix("scope.")
                    events += "scope:$short"
                    if (short == failScope) error("encode failed")
                }

                override fun finish(): GPUFrameCommandBuffer {
                    check(!terminal)
                    terminal = true
                    liveEncoderCount -= 1
                    pendingCommandBufferCount += 1
                    events += "encoder:finish"
                    return GPUFrameCommandBuffer("command.1")
                }

                override fun discard(): GPUFrameDiscardResult {
                    if (terminal) return GPUFrameDiscardResult.AlreadyReleased
                    terminal = true
                    liveEncoderCount -= 1
                    encoderDiscardCount += 1
                    events += "encoder:discard"
                    return GPUFrameDiscardResult.Discarded
                }
            }
        }

        override fun discard(commandBuffer: GPUFrameCommandBuffer): GPUFrameDiscardResult {
            commandBufferDiscardCount += 1
            pendingCommandBufferCount -= 1
            events += "command-buffer:discard:${commandBuffer.value}"
            return if (failCommandBufferDiscard) {
                GPUFrameDiscardResult.Failed("TestDiscardFailure")
            } else {
                GPUFrameDiscardResult.Discarded
            }
        }

        override fun submit(commandBuffer: GPUFrameCommandBuffer) {
            pendingCommandBufferCount -= 1
            events += "queue:submit:${commandBuffer.value}"
            if (failSubmit) error("submit failed")
        }
    }

    private class ThrowingNativePayloadAccess(
        private val payload: GPUPreparedNativeFramePayload,
        private val throwOnConsume: Boolean = false,
        private val throwOnRelease: Boolean = false,
        private val throwOnQuarantine: Boolean = false,
        private val refuseMarkSubmitted: Boolean = false,
        private val throwOnMarkSubmitted: Boolean = false,
    ) : GPUPreparedNativeFramePayloadAccess {
        override fun consumePreparedNativeFramePayload(
            token: GPUPreparedNativeFrameToken,
            expectedIdentity: GPUPreparedNativeFrameIdentity,
        ): GPUPreparedNativeFrameConsumption {
            if (throwOnConsume) error("consume failed")
            return GPUPreparedNativeFrameConsumption.Consumed(payload)
        }

        override fun rollbackPreparedNativeFramePayload(token: GPUPreparedNativeFrameToken): Boolean = true
        override fun markPreparedNativeFrameSubmitted(token: GPUPreparedNativeFrameToken): Boolean {
            if (throwOnMarkSubmitted) error("mark submitted failed")
            return !refuseMarkSubmitted
        }
        override fun releasePreparedNativeFramePayload(token: GPUPreparedNativeFrameToken): Boolean {
            if (throwOnRelease) error("release failed")
            return true
        }

        override fun claimOutputOwnedPreparedNativeFramePayloadMapping(
            token: GPUPreparedNativeFrameToken,
        ): Boolean = true

        override fun quarantinePreparedNativeFramePayload(token: GPUPreparedNativeFrameToken): Boolean {
            if (throwOnQuarantine) error("quarantine failed")
            return true
        }

        override fun bindLateSurface(
            token: GPUPreparedNativeFrameToken,
            acquiredSurface: GPUAcquiredSurfaceOutput?,
            binding: GPUPreparedNativeFrameLateSurfaceBinding,
        ): GPUPreparedNativeFrameBindingResult = GPUPreparedNativeFrameBindingResult.Ready
    }

    private class RecordingCompletion(
        private val events: MutableList<String>,
        private val armRefused: Boolean = false,
        private val completeSynchronously: Boolean = false,
        private val throwOnArm: Boolean = false,
        private val armedTicketId: GPUQueueCompletionTicketID? = null,
    ) : GPUQueueCompletionAccess {
        private var sink: GPUQueueCompletionSink? = null

        override fun abandonReservedTicket(
            ticket: GPUQueueCompletionTicket,
        ): GPUQueueCompletionTicketAbandonResult =
            GPUQueueCompletionTicketAbandonResult.Abandoned(ticket.ticketId)

        override fun reserveTicket(request: GPUQueueCompletionTicketRequest): GPUQueueCompletionTicketReservation =
            error("ticket is already reserved by preflight")

        override fun armAfterSubmit(
            ticket: GPUQueueCompletionTicket,
            sink: GPUQueueCompletionSink,
        ): GPUQueueCompletionArmResult {
            events += "completion:arm:${ticket.ticketId.value}"
            if (throwOnArm) error("completion arm failed")
            this.sink = sink
            if (completeSynchronously) {
                sink.accept(GPUQueueCompletionDelivery.Accepted(ticket.ticketId, GPUQueueCompletionOutcome.Success))
            }
            return if (armRefused) {
                GPUQueueCompletionArmResult.Refused(ticket.ticketId, GPUQueueCompletionFailureKind.CallbackFailure)
            } else {
                GPUQueueCompletionArmResult.Armed(armedTicketId ?: ticket.ticketId)
            }
        }

        override suspend fun awaitCompletion(ticket: GPUQueueCompletionTicket): GPUQueueCompletionDelivery =
            error("not used")

        override fun cancel(ticket: GPUQueueCompletionTicket): GPUQueueCompletionDelivery =
            GPUQueueCompletionDelivery.Unarmed(ticket.ticketId)

        fun complete(
            outcome: GPUQueueCompletionOutcome,
            ticketId: GPUQueueCompletionTicketID = GPUQueueCompletionTicketID("ticket.7"),
        ) {
            checkNotNull(sink).accept(
                GPUQueueCompletionDelivery.Accepted(
                    ticketId = ticketId,
                    outcome = outcome,
                ),
            )
        }
    }

    private class RecordingReadbackAccess(
        private val events: MutableList<String>,
        private val delayed: Boolean = false,
        private val armRefused: Boolean = false,
        private val throwOnSubmitted: Boolean = false,
        private val throwOnAccept: Boolean = false,
        private val throwOnReject: Boolean = false,
    ) : GPUFrameReadbackAccess {
        private var sink: GPUFrameReadbackMapSink? = null

        override fun markSubmitted(
            ticket: GPUQueueCompletionTicket,
            output: GPUPreparedReadbackOutput,
            operand: GPUPreparedNativeScopeOperand.Readback,
        ): GPUFrameReadbackLifecycleResult {
            if (throwOnSubmitted) error("readback submitted failed")
            events += "readback:submitted:${output.request.requestId.value}"
            return GPUFrameReadbackLifecycleResult.Applied
        }

        override fun acceptGPUCompletion(
            ticket: GPUQueueCompletionTicket,
            output: GPUPreparedReadbackOutput,
            operand: GPUPreparedNativeScopeOperand.Readback,
        ): GPUFrameReadbackLifecycleResult {
            if (throwOnAccept) error("readback accept failed")
            events += "readback:gpu-complete"
            return GPUFrameReadbackLifecycleResult.Applied
        }

        override fun rejectGPUCompletion(
            ticket: GPUQueueCompletionTicket,
            output: GPUPreparedReadbackOutput,
            operand: GPUPreparedNativeScopeOperand.Readback,
            failure: GPUQueueCompletionFailureKind,
        ): GPUFrameReadbackLifecycleResult {
            if (throwOnReject) error("readback reject failed")
            events += "readback:completion-failed:${failure.name}"
            return GPUFrameReadbackLifecycleResult.Applied
        }

        override fun mapAndDepad(
            output: GPUPreparedReadbackOutput,
            operand: GPUPreparedNativeScopeOperand.Readback,
            sink: GPUFrameReadbackMapSink,
        ): GPUFrameReadbackMapArmResult {
            events += "readback:map"
            if (armRefused) {
                return GPUFrameReadbackMapArmResult.Refused(
                    executionDiagnostic("failed.test.readback-map-arm", "map arm refused"),
                )
            }
            this.sink = GPUFrameReadbackMapSink { delivery ->
                events += "readback:unmapped"
                sink.accept(delivery)
            }
            if (!delayed) {
                this.sink?.accept(
                    GPUFrameReadbackMapDelivery.Pixels(
                        output.request.requestId,
                        ByteArray(output.layout.width * output.layout.height * output.layout.bytesPerPixel),
                    ),
                )
            }
            return GPUFrameReadbackMapArmResult.Armed
        }

        override fun finalizeAfterNativeClose(
            output: GPUPreparedReadbackOutput,
            operand: GPUPreparedNativeScopeOperand.Readback,
            safety: GPUFrameReadbackNativeOutputSafety,
        ): GPUFrameReadbackLifecycleResult {
            events += "readback:pool-finalize:${safety.name}"
            return GPUFrameReadbackLifecycleResult.Applied
        }

        fun complete(delivery: GPUFrameReadbackMapDelivery) {
            checkNotNull(sink).accept(delivery)
        }
    }

    private class RecordingRetention(
        private val events: MutableList<String>,
        private val throwOnRegister: Boolean = false,
        private val throwOnComplete: Boolean = false,
        private val throwOnQuarantine: Boolean = false,
    ) : GPUFrameResourceRetention {
        val completed = mutableListOf<String>()
        val quarantined = mutableListOf<String>()
        val registered = mutableListOf<GPUFrameRetentionRegistration>()
        val quarantinedRegistrations = mutableListOf<GPUFrameRetentionRegistration>()

        override fun registerAfterSubmit(registration: GPUFrameRetentionRegistration) {
            if (throwOnRegister) error("retention register failed")
            registered += registration
            events += "retention:register:${registration.ordinaryConcreteResources.joinToString(",")}"
        }

        override fun complete(
            ticket: GPUQueueCompletionTicket,
            outcome: GPUQueueCompletionOutcome,
        ) {
            if (throwOnComplete) error("retention complete failed")
            completed += "${ticket.ticketId.value}:${outcome::class.simpleName}"
        }

        override fun quarantine(registration: GPUFrameRetentionRegistration, diagnostic: GPUDiagnostic) {
            if (throwOnQuarantine) error("quarantine observer failed")
            quarantinedRegistrations += registration
            quarantined += "${registration.ticket.ticketId.value}:${diagnostic.code.value}"
        }
    }
}

private fun GPURuntimeResourceAdapter.registerReadyPayload(
    payload: GPUPreparedNativeFramePayload,
): GPUPreparedNativeFrameRegistration {
    val registration = registerPreparedNativeFrameDraft(GPUPreparedNativeFrameDraft(payload))
    if (registration is GPUPreparedNativeFrameRegistration.Registered) {
        check(
            registration.ownership.bindLateSurface(
                acquiredSurface = null,
                binding = GPUPreparedNativeFrameLateSurfaceBinding.NotRequired,
            ) is GPUPreparedNativeFrameBindingResult.Ready,
        )
    }
    return registration
}

internal object GPUFrameCoreTestFixture {
    private val targetRef = GPUFrameTargetRef("target.scene")
    private val copyRef = GPUFrameTextureRef("texture.copy")
    private val deviceGeneration = GPUDeviceGenerationID(7)

    fun sceneTarget(
        targetGeneration: Long = 1,
        targetId: String = "target.scene",
    ): GPUSceneTarget = GPUSceneTarget(
        targetId = targetId,
        resolvedTexture = GPUTextureResourceRef("prepared:target.scene"),
        retainedMsaaAttachment = null,
        width = 4,
        height = 4,
        format = GPUColorFormat("rgba8unorm"),
        colorInterpretation = GPUColorInterpretation("srgb-premul"),
        usages = setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource),
        sampleCount = 1,
        deviceGeneration = deviceGeneration,
        targetGeneration = targetGeneration,
    )

    fun orderedRenderCopyRenderComputeFrame(): PreparedGPUFrame {
        val capabilities = capabilities()
        val frameId = GPUFrameID(7)
        val seal = GPUFrameCapabilitySeal.capture(frameId, deviceGeneration, capabilities)
        val destinationRef = GPUFrameTextureRef("texture.destination")
        val prepare = GPUFrameStep.PrepareResourcesStep(emptyList(), listOf(GPUTaskID("task.prepare")))
        val beforePacket = drawPacket("packet.before", 1, "pass.before")
        val afterPacket = drawPacket("packet.after", 2, "pass.after")
        val renderBefore = renderStep(beforePacket, "task.render.before")
        val consumer = GPUDestinationSnapshotConsumerRef(
            groupingCommandId = "draw.after",
            renderTaskId = GPUTaskID("task.render.after"),
            packetId = afterPacket.packetId,
            commandId = GPUDrawCommandID(2),
        )
        val destinationCopy = GPUFrameStep.CopyDestinationStep(
            source = targetRef,
            sourceKey = destinationSnapshotKey(),
            snapshot = destinationRef,
            logicalBounds = GPUPixelBounds(1, 1, 3, 3),
            copyLayout = GPUTextureCopyLayout(256, 2),
            consumers = listOf(consumer),
            sourceTaskIds = listOf(GPUTaskID("task.copy.destination")),
        )
        val transition = GPUFrameStep.TargetTransitionStep(
            parent = targetRef,
            child = GPUFrameTargetRef("target.filter"),
            transitionKind = GPUTargetTransitionKind.EnterChild,
            sourceTaskIds = listOf(GPUTaskID("task.transition.filter")),
        )
        val renderAfter = renderStep(afterPacket, "task.render.after")
        val computeFilter = GPUFrameStep.ComputePassStep(
            target = targetRef,
            resourceUses = emptyList(),
            dispatches = listOf(GPUComputeDispatch(GPUComputePipelineKey("compute.filter"), 1, 1, 1)),
            sourceTaskIds = listOf(GPUTaskID("task.compute.filter")),
        )
        val semanticPlan = GPUFramePlan(
            frameId = frameId,
            capabilitySeal = seal,
            recordingSeals = listOf(
                GPURecordingSeal(GPURecordingID("recording.ordered"), 0, "compat", "replay", seal.sealHash),
            ),
            steps = listOf(prepare, renderBefore, destinationCopy, transition, renderAfter, computeFilter),
            memoryBudget = budget(),
            diagnostics = emptyList(),
        )
        val generations = mapOf(targetRef to 1L, destinationRef to 1L)
        val encoderPlan = GPUCommandEncoderPlan.ordered(
            planId = "frame.ordered",
            contextIdentity = "target.scene",
            deviceGeneration = deviceGeneration,
            targetGeneration = 1,
            scopes = listOf(
                renderScope(1, beforePacket, renderBefore, "render-before"),
                GPUCommandEncoderScopePlan(
                    sourceStepIndex = 2,
                    operationKind = GPUEncoderOperationKind.CopyDestination,
                    scopeLabel = "scope.copy-destination",
                    sourceTaskIds = destinationCopy.sourceTaskIds,
                    facadeOperationClasses = listOf("copyTextureToTexture"),
                    targetGeneration = 1,
                    resourceGenerationLabels = listOf(
                        "GPUFrameTargetRef:target.scene@1",
                        "GPUFrameTextureRef:texture.destination@1",
                    ),
                ),
                renderScope(4, afterPacket, renderAfter, "render-after"),
                GPUCommandEncoderScopePlan(
                    sourceStepIndex = 5,
                    operationKind = GPUEncoderOperationKind.Compute,
                    scopeLabel = "scope.compute-filter",
                    sourceTaskIds = computeFilter.sourceTaskIds,
                    facadeOperationClasses = listOf("beginComputePass", "dispatchWorkgroups", "endComputePass"),
                    targetGeneration = 1,
                    resourceGenerationLabels = listOf("GPUFrameTargetRef:target.scene@1"),
                ),
            ),
        )
        val completionTicket = GPUQueueCompletionTicket(
            GPUQueueCompletionTicketID("ticket.7"),
            frameId,
            deviceGeneration,
        )
        return PreparedGPUFrame(
            semanticPlan = semanticPlan,
            encoderPlan = encoderPlan,
            resources = GPUPreparedResourceSet(
                ordinaryResources = listOf(
                    GPUPreparedResourceEvidence(
                        targetRef,
                        GPUPreparedConcreteResourceRef.Texture(GPUTextureResourceRef("prepared:target.scene")),
                        GPUFrameResourceRole.SceneTarget,
                        deviceGeneration,
                        1,
                    ),
                    GPUPreparedResourceEvidence(
                        destinationRef,
                        GPUPreparedConcreteResourceRef.Texture(GPUTextureResourceRef("prepared:texture.destination")),
                        GPUFrameResourceRole.DestinationSnapshot,
                        deviceGeneration,
                        1,
                    ),
                ),
                outputOwnedReadbacks = emptyList(),
            ),
            generationSeal = GPUPreparedGenerationSeal(deviceGeneration, 1, generations, seal.sealHash),
            completionTicket = completionTicket,
            acquiredSurfaceOutput = null,
            rollback = GPUFrameRollback(
                ownerScope = "frame.ordered",
                resourceProvider = RollbackProvider(null),
                surfaceProvider = NoSurfaceProvider,
                acquiredSurfaceOutput = null,
                completionProvider = NoOpCompletionProvider,
                completionTicket = completionTicket,
            ),
            stepPartition = listOf(
                GPUPreparedStepEvidence(0, GPUPreparedStepLane.ResourcePreflight, "PrepareResources"),
                GPUPreparedStepEvidence(1, GPUPreparedStepLane.Encoder, "RenderPass"),
                GPUPreparedStepEvidence(2, GPUPreparedStepLane.Encoder, "CopyDestination"),
                GPUPreparedStepEvidence(3, GPUPreparedStepLane.Dependency, "TargetTransition"),
                GPUPreparedStepEvidence(4, GPUPreparedStepLane.Encoder, "RenderPass"),
                GPUPreparedStepEvidence(5, GPUPreparedStepLane.Encoder, "ComputePass"),
            ),
            dependencyEvidence = listOf(
                GPUPreparedDependencyEvidence(3, "TargetTransition", GPUTargetTransitionKind.EnterChild.name),
            ),
            hostActions = emptyList(),
        )
    }

    private fun renderStep(packet: GPUDrawPacket, taskId: String): GPUFrameStep.RenderPassStep =
        GPUFrameStep.RenderPassStep(
            target = targetRef,
            loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
            samplePlan = GPUSamplePlan.SingleSampleFrame,
            drawPackets = listOf(packet),
            sourceTaskIds = listOf(GPUTaskID(taskId)),
        )

    private fun renderScope(
        sourceStepIndex: Int,
        packet: GPUDrawPacket,
        step: GPUFrameStep.RenderPassStep,
        label: String,
    ): GPUCommandEncoderScopePlan {
        val commands = listOf(
            GPUPassCommand.BeginRenderPass(packet.targetStateHash, "load-store"),
            GPUPassCommand.SetRenderPipeline(requireNotNull(packet.renderPipelineKey), packet.packetId),
            GPUPassCommand.SetBindGroup(
                packet.bindingLayoutHash,
                packet.uniformSlot,
                packet.resourceSlot,
                packet.packetId,
            ),
            GPUPassCommand.Draw(packet.vertexSourceLabel, packet.packetId),
            GPUPassCommand.EndRenderPass(packet.passId),
        )
        val stream = GPUPassCommandStream(
            streamId = "stream.$label",
            packetStreamId = "packets.$label",
            passId = packet.passId,
            commands = commands,
            operandBridge = listOf(
                commandOperand(packet, "setRenderPipeline", GPUMaterializedCommandOperandKind.RenderPipeline),
                commandOperand(packet, "setBindGroup", GPUMaterializedCommandOperandKind.BindGroup),
            ),
        )
        return GPUCommandEncoderScopePlan(
            sourceStepIndex = sourceStepIndex,
            operationKind = GPUEncoderOperationKind.Render,
            scopeLabel = "scope.$label",
            sourceTaskIds = step.sourceTaskIds,
            sourcePacketIds = listOf(packet.packetId),
            facadeOperationClasses = stream.commandLabels,
            targetGeneration = 1,
            resourceGenerationLabels = listOf("GPUFrameTargetRef:target.scene@1"),
            passCommandStream = stream,
        )
    }

    private fun commandOperand(
        packet: GPUDrawPacket,
        commandLabel: String,
        kind: GPUMaterializedCommandOperandKind,
    ): GPUPassCommandOperandBridge = GPUPassCommandOperandBridge(
        packetId = packet.packetId,
        commandLabel = commandLabel,
        operand = GPUMaterializedCommandOperandReference(
            label = "$commandLabel.${packet.packetId.value}",
            kind = kind,
            descriptorHash = "descriptor.${packet.packetId.value}",
            deviceGeneration = deviceGeneration.value,
            ownerScope = "frame.ordered",
            usageLabels = listOf(commandLabel),
            invalidationPolicy = "submission-complete",
        ),
    )

    private fun drawPacket(id: String, commandId: Int, passId: String): GPUDrawPacket = GPUDrawPacket(
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
        renderPipelineKey = GPURenderPipelineKey("pipeline.$id"),
        bindingLayoutHash = "layout.fill",
        vertexSourceLabel = "vertices.$id",
        targetStateHash = "target.state",
        originalPaintOrder = commandId,
        resourceGeneration = 1,
    )

    private fun destinationSnapshotKey(): GPUDestinationSnapshotGroupKey = GPUDestinationSnapshotGroupKey(
        target = GPUTargetIdentity("target.scene"),
        targetGeneration = 1,
        deviceGeneration = deviceGeneration,
        format = GPUColorFormat("rgba8unorm"),
        colorInterpretation = GPUColorInterpretation("srgb-premul"),
        sampleContinuation = GPUSampleContinuationKey(
            target = GPUTargetIdentity("target.scene"),
            targetGeneration = 1,
            deviceGeneration = deviceGeneration,
            colorFormat = GPUColorFormat("rgba8unorm"),
            colorInterpretation = GPUColorInterpretation("srgb-premul"),
            samplePlan = GPUSamplePlan.MultisampleFrame(4),
            colorAttachment = GPUTargetIdentity("target.scene.msaa"),
            depthStencilAttachment = null,
        ),
        sourceIntermediate = null,
    )

    fun preparedFrame(
        rollbackEvents: MutableList<String>? = null,
        withHostActions: Boolean = false,
        surfaceGeneration: Long = 1,
        nativePayloadOwnership: GPUPreparedNativeFrameOwnership? = null,
        useDifferentRollbackTicket: Boolean = false,
        readbackRequestId: GPUReadbackRequestID? = null,
    ): PreparedGPUFrame {
        val capabilities = capabilities()
        val frameId = GPUFrameID(7)
        val seal = GPUFrameCapabilitySeal.capture(frameId, deviceGeneration, capabilities)
        val prepare = GPUFrameStep.PrepareResourcesStep(emptyList(), listOf(GPUTaskID("task.prepare")))
        val copy = GPUFrameStep.CopyResourceStep(
            source = targetRef,
            destination = copyRef,
            regions = listOf(GPUResourceCopyRegion(0, 0, GPUPixelBounds(0, 0, 4, 4), 64)),
            sourceTaskIds = listOf(GPUTaskID("task.copy")),
        )
        val barrier = GPUFrameStep.DependencyBarrierStep(
            orderedUseTokens = listOf(GPUTaskUseToken("use.copy")),
            reasonCode = "dependency.copy-before-compute",
            sourceTaskIds = listOf(GPUTaskID("task.barrier")),
        )
        val compute = GPUFrameStep.ComputePassStep(
            target = targetRef,
            resourceUses = emptyList(),
            dispatches = listOf(GPUComputeDispatch(GPUComputePipelineKey("compute.main"), 1, 1, 1)),
            sourceTaskIds = listOf(GPUTaskID("task.compute")),
        )
        val stagingRef = GPUFrameBufferRef("buffer.readback")
        val readbackRequest = readbackRequestId?.let { requestId ->
            GPUFrameReadbackRequest(
                requestId = requestId,
                sourceBounds = GPUPixelBounds(0, 0, 4, 4),
                pixelFormat = GPUReadbackPixelFormat.Rgba8Unorm,
                outputColorInterpretation = GPUColorInterpretation("srgb-premul"),
            )
        }
        val readback = readbackRequest?.let { request ->
            GPUFrameStep.ReadbackCopyStep(
                source = targetRef,
                staging = stagingRef,
                request = request,
                sourceTaskIds = listOf(GPUTaskID("task.readback")),
            )
        }
        val surfaceOutput = GPUSurfaceOutputRef("surface.main")
        val surfaceTasks = listOf(GPUTaskID("task.output"))
        val acquire = GPUFrameStep.AcquireSurfaceOutput(
            descriptor = GPUSurfaceOutputDescriptor(
                output = surfaceOutput,
                width = 4,
                height = 4,
                format = GPUColorFormat("rgba8unorm"),
                targetGeneration = surfaceGeneration,
            ),
            sourceTaskIds = surfaceTasks,
        )
        val surfaceBlit = GPUFrameStep.SurfaceBlitRenderPassStep(targetRef, surfaceOutput, surfaceTasks)
        val present = GPUFrameStep.PostSubmitPresentAction(surfaceOutput, surfaceTasks)
        val semanticSteps = buildList {
            addAll(listOf(prepare, copy, barrier, compute))
            readback?.let(::add)
            if (withHostActions) addAll(listOf(acquire, surfaceBlit, present))
        }
        val semanticPlan = GPUFramePlan(
            frameId = frameId,
            capabilitySeal = seal,
            recordingSeals = listOf(
                GPURecordingSeal(GPURecordingID("recording.main"), 0, "compat", "replay", seal.sealHash),
            ),
            steps = semanticSteps,
            memoryBudget = budget(),
            diagnostics = emptyList(),
        )
        val generations = buildMap {
            put(targetRef, 1L)
            put(copyRef, 1L)
            if (readback != null) put(stagingRef, 1L)
        }
        val encoderPlan = GPUCommandEncoderPlan.ordered(
            planId = "frame.7",
            contextIdentity = "target.scene",
            deviceGeneration = deviceGeneration,
            targetGeneration = 1,
            scopes = buildList {
                add(
                GPUCommandEncoderScopePlan(
                    sourceStepIndex = 1,
                    operationKind = GPUEncoderOperationKind.Copy,
                    scopeLabel = "scope.copy",
                    sourceTaskIds = copy.sourceTaskIds,
                    facadeOperationClasses = listOf("copyResource"),
                    targetGeneration = 1,
                    resourceGenerationLabels = listOf(
                        "GPUFrameTargetRef:target.scene@1",
                        "GPUFrameTextureRef:texture.copy@1",
                    ),
                ).attachNativeOperandKeys(copyNativeOperandKeys()),
                )
                add(
                GPUCommandEncoderScopePlan(
                    sourceStepIndex = 3,
                    operationKind = GPUEncoderOperationKind.Compute,
                    scopeLabel = "scope.compute",
                    sourceTaskIds = compute.sourceTaskIds,
                    facadeOperationClasses = listOf("beginComputePass", "dispatchWorkgroups", "endComputePass"),
                    targetGeneration = 1,
                    resourceGenerationLabels = listOf("GPUFrameTargetRef:target.scene@1"),
                ).attachNativeOperandKeys(computeNativeOperandKeys()),
                )
                if (readback != null) {
                    add(
                        GPUCommandEncoderScopePlan(
                            sourceStepIndex = 4,
                            operationKind = GPUEncoderOperationKind.Readback,
                            scopeLabel = "scope.readback",
                            sourceTaskIds = readback.sourceTaskIds,
                            facadeOperationClasses = listOf("copyTextureToBuffer"),
                            targetGeneration = 1,
                            resourceGenerationLabels = listOf(
                                "GPUFrameTargetRef:target.scene@1",
                                "GPUFrameBufferRef:buffer.readback@1",
                            ),
                        ).attachNativeOperandKeys(readbackNativeOperandKeys()),
                    )
                }
                if (withHostActions) {
                    add(
                        GPUCommandEncoderScopePlan(
                            sourceStepIndex = if (readback != null) 6 else 5,
                            operationKind = GPUEncoderOperationKind.SurfaceBlit,
                            scopeLabel = "scope.surface-blit",
                            sourceTaskIds = surfaceBlit.sourceTaskIds,
                            facadeOperationClasses = listOf("beginRenderPass", "surfaceBlit", "endRenderPass"),
                            targetGeneration = 1,
                            resourceGenerationLabels = listOf("GPUFrameTargetRef:target.scene@1"),
                        ),
                    )
                }
            },
        )
        val resources = GPUPreparedResourceSet(
            ordinaryResources = listOf(
                GPUPreparedResourceEvidence(
                    targetRef,
                    GPUPreparedConcreteResourceRef.Texture(GPUTextureResourceRef("prepared:target.scene")),
                    GPUFrameResourceRole.SceneTarget,
                    deviceGeneration,
                    1,
                ),
                GPUPreparedResourceEvidence(
                    copyRef,
                    GPUPreparedConcreteResourceRef.Texture(GPUTextureResourceRef("prepared:texture.copy")),
                    GPUFrameResourceRole.DestinationSnapshot,
                    deviceGeneration,
                    1,
                ),
            ),
            outputOwnedReadbacks = if (readbackRequest == null) {
                emptyList()
            } else {
                val concrete = GPUBufferResourceRef("prepared:buffer.readback")
                listOf(
                    GPUPreparedReadbackOutput(
                        stagingResource = stagingRef,
                        concreteResource = GPUPreparedConcreteResourceRef.Buffer(concrete),
                        resourceGeneration = 1,
                        request = readbackRequest,
                        layout = GPUReadbackLayout(
                            width = 4,
                            height = 4,
                            bytesPerPixel = 4,
                            copyBytesPerRowAlignment = 256,
                            unpaddedBytesPerRow = 16,
                            paddedBytesPerRow = 256,
                            rowsPerImage = 4,
                            bufferOffset = 0,
                            totalBufferBytes = 784,
                        ),
                        stagingLease = GPUReadbackStagingLease(
                            reservationId = "readback.7",
                            ownerScope = "frame.7",
                            deviceGeneration = deviceGeneration,
                            resourceRef = concrete,
                            reservationOrdinal = 1,
                            acquisitionToken = 1,
                            logicalMinimumBytes = 784,
                            backingBufferBytes = 1024,
                            usages = setOf(
                                GPUFrameResourceUsage.MapRead,
                                GPUFrameResourceUsage.CopyDestination,
                            ),
                        ),
                    ),
                )
            },
            commandResourceLeases = listOf(
                GPUResourceLease(
                    leaseId = "lease.compute",
                    resourceKind = GPUResourceLeaseKind.BindGroup,
                    deviceGeneration = deviceGeneration.value,
                    descriptorHash = "compute.pipeline",
                    ownerScope = "frame.7",
                    usageLabels = listOf("compute"),
                    releasePolicy = "submission-complete",
                    cacheResult = GPUResourceLeaseCacheResult.Create,
                ),
            ),
        )
        val acquiredSurfaceOutput = if (withHostActions) {
            GPUAcquiredSurfaceOutput(surfaceOutput, deviceGeneration, surfaceGeneration, "surface-output")
        } else {
            null
        }
        val completionTicket = GPUQueueCompletionTicket(
            GPUQueueCompletionTicketID("ticket.7"),
            frameId,
            deviceGeneration,
        )
        return PreparedGPUFrame(
            semanticPlan = semanticPlan,
            encoderPlan = encoderPlan,
            resources = resources,
            generationSeal = GPUPreparedGenerationSeal(deviceGeneration, 1, generations, seal.sealHash),
            completionTicket = completionTicket,
            acquiredSurfaceOutput = acquiredSurfaceOutput,
            rollback = GPUFrameRollback(
                ownerScope = "frame.7",
                resourceProvider = RollbackProvider(rollbackEvents),
                surfaceProvider = NoSurfaceProvider,
                acquiredSurfaceOutput = acquiredSurfaceOutput,
                nativePayloadOwnership = nativePayloadOwnership,
                completionProvider = NoOpCompletionProvider,
                completionTicket = if (useDifferentRollbackTicket) completionTicket.copy() else completionTicket,
            ),
            stepPartition = buildList {
                add(GPUPreparedStepEvidence(0, GPUPreparedStepLane.ResourcePreflight, "PrepareResources"))
                add(GPUPreparedStepEvidence(1, GPUPreparedStepLane.Encoder, "CopyResource"))
                add(GPUPreparedStepEvidence(2, GPUPreparedStepLane.Dependency, "DependencyBarrier"))
                add(GPUPreparedStepEvidence(3, GPUPreparedStepLane.Encoder, "ComputePass"))
                if (readback != null) {
                    add(GPUPreparedStepEvidence(4, GPUPreparedStepLane.Encoder, "ReadbackCopy"))
                }
                if (withHostActions) {
                    val base = if (readback != null) 5 else 4
                    add(GPUPreparedStepEvidence(base, GPUPreparedStepLane.HostAction, "AcquireSurfaceOutput"))
                    add(GPUPreparedStepEvidence(base + 1, GPUPreparedStepLane.Encoder, "SurfaceBlitRenderPass"))
                    add(GPUPreparedStepEvidence(base + 2, GPUPreparedStepLane.HostAction, "PostSubmitPresent"))
                }
            },
            dependencyEvidence = listOf(
                GPUPreparedDependencyEvidence(2, "DependencyBarrier", "dependency.copy-before-compute"),
            ),
            hostActions = if (withHostActions) {
                val base = if (readback != null) 5 else 4
                listOf(
                    GPUFrameHostAction(base, GPUHostActionKind.AcquireSurface, surfaceOutput),
                    GPUFrameHostAction(base + 2, GPUHostActionKind.Present, surfaceOutput),
                )
            } else {
                emptyList()
            },
        )
    }

    fun rollbackJournal(
        completionProvider: GPUQueueCompletionProvider? = null,
        completionTicket: GPUQueueCompletionTicket? = null,
    ): GPUFrameRollback = GPUFrameRollback(
        ownerScope = "frame.rollback-test",
        resourceProvider = RollbackProvider(null),
        surfaceProvider = NoSurfaceProvider,
        completionProvider = completionProvider,
        completionTicket = completionTicket,
    )

    fun nativePayload(
        targetGeneration: Long = 1,
        copySourceBindingKey: String = "GPUFrameTargetRef:target.scene@1",
        withReadback: Boolean = false,
    ): GPUPreparedNativeFramePayload {
        val copyKeys = copyNativeOperandKeys(copySourceBindingKey)
        val computeKeys = computeNativeOperandKeys()
        val readbackKeys = readbackNativeOperandKeys()
        val targetTexture = fakeNative<GPUTexture>("target.scene")
        val copyTexture = fakeNative<GPUTexture>("texture.copy")
        val scopes = buildList {
            add(
                GPUPreparedNativeScopeKey(
                    1,
                    GPUEncoderOperationKind.Copy,
                    listOf("GPUFrameTargetRef:target.scene@1", "GPUFrameTextureRef:texture.copy@1"),
                    copyKeys,
                ),
            )
            add(
                GPUPreparedNativeScopeKey(
                    3,
                    GPUEncoderOperationKind.Compute,
                    listOf("GPUFrameTargetRef:target.scene@1"),
                    computeKeys,
                ),
            )
            if (withReadback) {
                add(
                    GPUPreparedNativeScopeKey(
                        4,
                        GPUEncoderOperationKind.Readback,
                        listOf("GPUFrameTargetRef:target.scene@1", "GPUFrameBufferRef:buffer.readback@1"),
                        readbackKeys,
                    ),
                )
            }
        }
        val operands = buildList {
            add(
                GPUPreparedNativeScopeOperand.Copy(
                    sourceStepIndex = 1,
                    operationKind = GPUEncoderOperationKind.Copy,
                    source = GPUPreparedNativeTextureOperand(targetTexture, deviceGeneration),
                    destination = GPUPreparedNativeTextureOperand(copyTexture, deviceGeneration),
                    textureLayout = GPUPreparedNativeTextureCopyLayout(
                        sourceOriginX = 0,
                        sourceOriginY = 0,
                        destinationOriginX = 0,
                        destinationOriginY = 0,
                        width = 1,
                        height = 1,
                    ),
                ),
            )
            add(
                GPUPreparedNativeScopeOperand.Compute(
                    sourceStepIndex = 3,
                    pipelines = listOf(
                        GPUPreparedNativeComputePipelineOperand(
                            fakeNative<GPUComputePipeline>("compute.main"),
                            deviceGeneration,
                        ),
                    ),
                    bindGroups = emptyList(),
                ),
            )
            if (withReadback) {
                add(
                    GPUPreparedNativeScopeOperand.Readback(
                        sourceStepIndex = 4,
                        source = GPUPreparedNativeTextureOperand(targetTexture, deviceGeneration),
                        destination = GPUPreparedNativeBufferOperand(
                            fakeNative<GPUBuffer>("buffer.readback"),
                            deviceGeneration,
                            GPUPreparedNativeOperandOwnership.OutputOwnedReadback,
                        ),
                        layout = GPUPreparedNativeReadbackLayout(
                            originX = 0,
                            originY = 0,
                            width = 4,
                            height = 4,
                            bytesPerRow = 256,
                            rowsPerImage = 4,
                            bufferOffset = 0,
                            mappedSize = 784,
                            format = GPUTextureFormat.RGBA8Unorm,
                        ),
                    ),
                )
            }
        }
        return GPUPreparedNativeFramePayload(
            identity = GPUPreparedNativeFrameIdentity(
                frameId = GPUFrameID(7),
                contextIdentity = "target.scene",
                encoderPlanId = "frame.7",
                deviceGeneration = deviceGeneration,
                targetGeneration = targetGeneration,
                scopes = scopes,
            ),
            scopeOperands = operands,
            scopeOperandKeys = buildList {
                add(copyKeys)
                add(computeKeys)
                if (withReadback) add(readbackKeys)
            },
        )
    }

    fun nativePayloadWithMismatchedSameGenerationOperandKey(): GPUPreparedNativeFramePayload =
        nativePayload(copySourceBindingKey = "GPUFrameTargetRef:target.wrong@1")

    private fun copyNativeOperandKeys(
        sourceBinding: String = "GPUFrameTargetRef:target.scene@1",
    ): List<GPUPreparedNativeOperandKey> = listOf(
        GPUPreparedNativeOperandKey(
            GPUPreparedNativeOperandRole.CopySource,
            GPUPreparedNativeOperandKind.Texture,
            gpuPreparedNativeBindingKey(sourceBinding),
        ),
        GPUPreparedNativeOperandKey(
            GPUPreparedNativeOperandRole.CopyDestination,
            GPUPreparedNativeOperandKind.Texture,
            gpuPreparedNativeBindingKey("GPUFrameTextureRef:texture.copy@1"),
        ),
    )

    private fun computeNativeOperandKeys(): List<GPUPreparedNativeOperandKey> = listOf(
        GPUPreparedNativeOperandKey(
            GPUPreparedNativeOperandRole.ComputePipeline,
            GPUPreparedNativeOperandKind.ComputePipeline,
            gpuPreparedNativeBindingKey("dispatch.0:compute.main"),
        ),
    )

    private fun readbackNativeOperandKeys(): List<GPUPreparedNativeOperandKey> = listOf(
        GPUPreparedNativeOperandKey(
            GPUPreparedNativeOperandRole.ReadbackSource,
            GPUPreparedNativeOperandKind.Texture,
            gpuPreparedNativeBindingKey("GPUFrameTargetRef:target.scene@1"),
        ),
        GPUPreparedNativeOperandKey(
            GPUPreparedNativeOperandRole.ReadbackDestination,
            GPUPreparedNativeOperandKind.Buffer,
            gpuPreparedNativeBindingKey("GPUFrameBufferRef:buffer.readback@1"),
            GPUPreparedNativeOperandOwnership.OutputOwnedReadback,
        ),
    )

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

    fun taskList(): GPUTaskList {
        val prepared = preparedFrame()
        return GPUTaskList(
            frameId = prepared.semanticPlan.frameId,
            capabilitySeal = prepared.semanticPlan.capabilitySeal,
            recordingSeals = prepared.semanticPlan.recordingSeals,
            expectedReplayKeyHash = "replay",
            tasks = emptyList(),
            dependencies = emptyList(),
            phaseOrder = GPUTaskPhase.entries,
            memoryBudget = prepared.semanticPlan.memoryBudget,
        )
    }

    fun refusedPlan(diagnostic: GPUDiagnostic): GPUFramePlan {
        val capabilities = capabilities()
        val frameId = GPUFrameID(7)
        return GPUFramePlan(
            frameId = frameId,
            capabilitySeal = GPUFrameCapabilitySeal.capture(frameId, deviceGeneration, capabilities),
            recordingSeals = emptyList(),
            steps = emptyList(),
            memoryBudget = budget(),
            diagnostics = listOf(diagnostic),
            atomicallyRefused = true,
        )
    }

    private fun capabilities(): GPUCapabilities = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "unit", "adapter", "device"),
        facts = listOf(GPUCapabilityFact("limits", "test", "observed", true, "executor")),
        snapshotId = "capabilities.current",
        limits = GPULimits(8192, 256, 256, maxBufferSize = 1L shl 30),
    )

    private fun budget(): GPUFrameMemoryBudgetPlan = GPUFrameMemoryBudgetPlan(
        peakFrameTransientBytes = 0,
        targetResidentBytes = 128,
        categoryTotals = GPUFrameMemoryCategory.entries.associateWith { 0L },
        deviceLimitFacts = emptyList(),
        configuredAggregateBudgetBytes = 1L shl 30,
        diagnostic = null,
    )

    private class RollbackProvider(
        private val events: MutableList<String>?,
    ) : GPUFrameResourcePreflightProvider {
        override fun beginFramePreparation(
            frameId: Long,
            deviceGeneration: GPUDeviceGenerationID,
        ): GPUFrameResourcePreparationSession = error("not used")

        override fun prepareFrameResource(input: GPUFrameResourcePreparationInput): GPUFrameResourcePreparationDecision =
            error("not used")

        override fun materializeCommandOperands(
            request: GPUCommandOperandMaterializationRequest,
            context: GPUTargetPreparationContext,
        ): GPUResourceMaterializationDecision = error("not used")

        override fun rollbackFrameResourcesBeforeSubmit(
            ownerScope: String,
        ): GPUPhysicalPoolMaintenanceDecision<GPUPhysicalPoolRollbackSummary> {
            events?.add("rollback:resources")
            return GPUPhysicalPoolMaintenanceDecision.Applied(
                GPUPhysicalPoolRollbackSummary(
                    scratch = org.graphiks.kanvas.gpu.renderer.resources.GPUScratchRollbackResult(emptyList(), emptyList()),
                    readback = org.graphiks.kanvas.gpu.renderer.resources.GPUReadbackStagingRollbackResult(
                        emptyList(),
                        emptyList(),
                    ),
                    releaseOrder = emptyList(),
                ),
            )
        }
    }

    private object NoSurfaceProvider : GPUSurfaceOutputProvider {
        override fun acquire(request: GPUSurfaceAcquisitionRequest): GPUSurfaceAcquisitionResult = error("not used")
        override fun release(output: GPUAcquiredSurfaceOutput): GPUSurfaceReleaseResult = GPUSurfaceReleaseResult.Released
    }

    private object NoOpCompletionProvider : GPUQueueCompletionProvider {
        override fun reserveTicket(
            request: GPUQueueCompletionTicketRequest,
        ): GPUQueueCompletionTicketReservation = error("ticket is already reserved")

        override fun abandonReservedTicket(
            ticket: GPUQueueCompletionTicket,
        ): GPUQueueCompletionTicketAbandonResult =
            GPUQueueCompletionTicketAbandonResult.Abandoned(ticket.ticketId)
    }
}
