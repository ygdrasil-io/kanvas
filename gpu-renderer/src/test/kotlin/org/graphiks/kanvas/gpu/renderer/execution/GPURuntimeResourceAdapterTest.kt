package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferMapState
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUMapMode
import io.ygdrasil.webgpu.GPUIndexFormat
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUSize64
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureView
import java.lang.reflect.Proxy
import org.graphiks.kanvas.gpu.renderer.resources.GPUBindGroupLeaseRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseCacheResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseFactoryResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabLeaseRequest
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class GPURuntimeResourceAdapterTest {
    @Test
    fun `wgpu4k indexed command lowering preserves exact backend call order and uint32 draw`() {
        val generation = GPUDeviceGenerationID(11)
        val pipeline = GPUPreparedNativeRenderPipelineOperand(fakeNative("backend-pipeline"), generation)
        val bindGroup = GPUPreparedNativeBindGroupOperand(fakeNative("backend-bind-group"), generation)
        val vertex = GPUPreparedNativeBufferOperand(CountingGPUBuffer("backend-vertex"), generation)
        val index = GPUPreparedNativeBufferOperand(CountingGPUBuffer("backend-index"), generation)
        val events = mutableListOf<String>()
        val draws = encodeWgpu4kRenderCommands(
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(pipeline),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, bindGroup),
                GPUPreparedNativeRenderCommand.SetVertexBuffer(0, vertex, 0, 64),
                GPUPreparedNativeRenderCommand.SetIndexBuffer(index, GPUPreparedNativeIndexFormat.Uint32, 0, 24),
                GPUPreparedNativeRenderCommand.SetScissor(0, 0, 1, 1),
                GPUPreparedNativeRenderCommand.DrawIndexed(GPUPreparedNativeDrawCall.DrawIndexed(6)),
            ),
            GPUWgpu4kRenderCommandActions(
                setPipeline = { actual -> assertSame(pipeline.pipeline, actual); events += "pipeline" },
                setBindGroup = { slot, actual ->
                    assertEquals(0u, slot)
                    assertSame(bindGroup.bindGroup, actual)
                    events += "bind-group"
                },
                setVertexBuffer = { slot, actual ->
                    assertEquals(0u, slot)
                    assertSame(vertex.buffer, actual)
                    events += "vertex"
                },
                setIndexBuffer = { actual, format ->
                    assertSame(index.buffer, actual)
                    assertEquals(GPUIndexFormat.Uint32, format)
                    events += "index-uint32"
                },
                setScissor = { x, y, width, height ->
                    assertEquals(listOf(0u, 0u, 1u, 1u), listOf(x, y, width, height))
                    events += "scissor"
                },
                draw = { _, _, _, _ -> error("non-indexed draw must not be emitted") },
                drawIndexed = { count -> assertEquals(6u, count); events += "draw-indexed" },
            ),
        )

        assertEquals(1, draws)
        assertEquals(
            listOf("pipeline", "bind-group", "vertex", "index-uint32", "scissor", "draw-indexed"),
            events,
        )
    }

    @Test
    fun `indexed render algebra requires bindings and preserves exact command and ownership order`() {
        val generation = GPUDeviceGenerationID(11)
        val pipeline = GPUPreparedNativeRenderPipelineOperand(fakeNative("indexed-pipeline"), generation)
        val bindGroup = GPUPreparedNativeBindGroupOperand(
            CountingGPUBindGroup("indexed-bind-group"),
            generation,
            GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
        )
        val vertex = GPUPreparedNativeBufferOperand(
            CountingGPUBuffer("indexed-vertex"),
            generation,
            GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
        )
        val index = GPUPreparedNativeBufferOperand(
            CountingGPUBuffer("indexed-index"),
            generation,
            GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
        )
        val pass = GPUPreparedNativeRenderPassConfig(
            GPUPreparedNativeTextureViewOperand(fakeNative("indexed-target"), generation),
        )
        val draw = GPUPreparedNativeRenderCommand.DrawIndexed(GPUPreparedNativeDrawCall.DrawIndexed(6))

        listOf(
            listOf<GPUPreparedNativeRenderCommand>(draw),
            listOf(GPUPreparedNativeRenderCommand.SetPipeline(pipeline), draw),
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(pipeline),
                GPUPreparedNativeRenderCommand.SetVertexBuffer(0, vertex, 0, 64),
                draw,
            ),
            listOf(
                GPUPreparedNativeRenderCommand.SetVertexBuffer(0, vertex, 0, 64),
                GPUPreparedNativeRenderCommand.SetIndexBuffer(index, GPUPreparedNativeIndexFormat.Uint32, 0, 24),
                draw,
            ),
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(pipeline),
                GPUPreparedNativeRenderCommand.SetVertexBuffer(0, vertex, 0, 64),
                GPUPreparedNativeRenderCommand.SetIndexBuffer(index, GPUPreparedNativeIndexFormat.Uint32, 0, 24),
                GPUPreparedNativeRenderCommand.SetScissor(0, 0, 1, 1),
                draw,
            ),
            listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(pipeline),
                GPUPreparedNativeRenderCommand.SetBindGroup(0, bindGroup),
                GPUPreparedNativeRenderCommand.SetVertexBuffer(0, vertex, 0, 64),
                GPUPreparedNativeRenderCommand.SetIndexBuffer(index, GPUPreparedNativeIndexFormat.Uint32, 0, 24),
                draw,
            ),
        ).forEach { commands ->
            assertFailsWith<IllegalArgumentException> {
                GPUPreparedNativeScopeOperand.Render(1, pass, commands)
            }
        }

        val commands = listOf(
            GPUPreparedNativeRenderCommand.SetPipeline(pipeline),
            GPUPreparedNativeRenderCommand.SetBindGroup(0, bindGroup),
            GPUPreparedNativeRenderCommand.SetVertexBuffer(0, vertex, 0, 64),
            GPUPreparedNativeRenderCommand.SetIndexBuffer(index, GPUPreparedNativeIndexFormat.Uint32, 0, 24),
            GPUPreparedNativeRenderCommand.SetScissor(0, 0, 1, 1),
            draw,
        )
        val scope = GPUPreparedNativeScopeOperand.Render(1, pass, commands)
        assertEquals(
            listOf("pipeline", "bind-group", "vertex", "index", "scissor", "draw-indexed"),
            scope.commands.map { command ->
                when (command) {
                    is GPUPreparedNativeRenderCommand.SetPipeline -> "pipeline"
                    is GPUPreparedNativeRenderCommand.SetBindGroup -> "bind-group"
                    is GPUPreparedNativeRenderCommand.SetVertexBuffer -> "vertex"
                    is GPUPreparedNativeRenderCommand.SetIndexBuffer -> "index"
                    is GPUPreparedNativeRenderCommand.SetScissor -> "scissor"
                    is GPUPreparedNativeRenderCommand.DrawIndexed -> "draw-indexed"
                    is GPUPreparedNativeRenderCommand.Draw -> "draw"
                }
            },
        )
        assertEquals(0, (commands[2] as GPUPreparedNativeRenderCommand.SetVertexBuffer).slot)
        assertEquals(64, (commands[2] as GPUPreparedNativeRenderCommand.SetVertexBuffer).size)
        assertEquals(GPUPreparedNativeIndexFormat.Uint32, (commands[3] as GPUPreparedNativeRenderCommand.SetIndexBuffer).format)
        assertEquals(24, (commands[3] as GPUPreparedNativeRenderCommand.SetIndexBuffer).size)
        assertEquals(6, draw.drawCall.indexCount)

        val payload = testPayload(6_001, listOf(scope))
        val keys = payload.identity.scopes.single().operandKeys
        assertEquals(
            listOf(
                GPUPreparedNativeOperandRole.RenderColorTarget,
                GPUPreparedNativeOperandRole.RenderPipeline,
                GPUPreparedNativeOperandRole.RenderBindGroup,
                GPUPreparedNativeOperandRole.RenderVertexBuffer,
                GPUPreparedNativeOperandRole.RenderIndexBuffer,
            ),
            keys.map { it.role },
        )
        assertEquals(keys.size, keys.map { it.bindingKey }.distinct().size)
        assertEquals(
            listOf(
                GPUPreparedNativeOperandOwnership.Borrowed,
                GPUPreparedNativeOperandOwnership.Borrowed,
                GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
            ),
            keys.map { it.ownership },
        )
    }

    @Test
    fun `prepared native payload token is opaque generation bound and consumed once`() {
        val adapter = GPURuntimeResourceAdapter()
        val payload = uploadPayload()

        val registration = adapter.registerReadyPayload(payload)
        val token = (registration as GPUPreparedNativeFrameRegistration.Registered).token
        val first = adapter.consumePreparedNativeFramePayload(token, payload.identity)
        val duplicate = adapter.consumePreparedNativeFramePayload(token, payload.identity)

        assertTrue(first is GPUPreparedNativeFrameConsumption.Consumed)
        assertEquals(
            "unsupported.native-frame-payload.token-already-consumed",
            (duplicate as GPUPreparedNativeFrameConsumption.Refused).code,
        )
        assertFalse(token.value.contains("@"))
        assertFalse(token.value.contains("0x"))
        assertFalse(token.value.contains("unit-buffer"))
    }

    @Test
    fun `prepared native payload refuses stale identity before consumption`() {
        val adapter = GPURuntimeResourceAdapter()
        val payload = uploadPayload()
        val token = (
            adapter.registerReadyPayload(payload) as
                GPUPreparedNativeFrameRegistration.Registered
            ).token
        val stale = GPUPreparedNativeFrameIdentity(
            frameId = payload.identity.frameId,
            contextIdentity = payload.identity.contextIdentity,
            encoderPlanId = payload.identity.encoderPlanId + ".stale",
            deviceGeneration = payload.identity.deviceGeneration,
            targetGeneration = payload.identity.targetGeneration,
            scopes = payload.identity.scopes,
        )

        val result = adapter.consumePreparedNativeFramePayload(token, stale)

        assertEquals(
            "stale.native-frame-payload.identity-mismatch",
            (result as GPUPreparedNativeFrameConsumption.Refused).code,
        )
    }

    @Test
    fun `queue completion keeps output owned readback until terminal output release`() {
        val adapter = GPURuntimeResourceAdapter()
        val staging = CountingGPUBuffer("readback-staging")
        val payload = readbackPayload(staging)
        val token = adapter.registeredToken(payload)
        assertTrue(adapter.consumePreparedNativeFramePayload(token, payload.identity) is GPUPreparedNativeFrameConsumption.Consumed)
        assertTrue(adapter.markPreparedNativeFrameSubmitted(token))

        assertTrue(adapter.releasePreparedNativeFramePayload(token))

        assertEquals(0, staging.closeCount)
        assertEquals(1, adapter.outputOwnedPreparedNativeFramePayloadCount)
        assertTrue(adapter.claimOutputOwnedPreparedNativeFramePayloadMapping(token))
        assertTrue(adapter.releaseOutputOwnedPreparedNativeFramePayload(token))
        assertEquals(1, staging.closeCount)
        assertEquals(0, adapter.outputOwnedPreparedNativeFramePayloadCount)
    }

    @Test
    fun `adapter close cannot close an output buffer while its mapping is claimed`() {
        val adapter = GPURuntimeResourceAdapter()
        val staging = CountingGPUBuffer("readback-mapping-claimed")
        val payload = readbackPayload(staging)
        val token = adapter.registeredToken(payload)
        assertIs<GPUPreparedNativeFrameConsumption.Consumed>(
            adapter.consumePreparedNativeFramePayload(token, payload.identity),
        )
        assertTrue(adapter.markPreparedNativeFrameSubmitted(token))
        assertTrue(adapter.releasePreparedNativeFramePayload(token))
        assertTrue(adapter.claimOutputOwnedPreparedNativeFramePayloadMapping(token))

        adapter.close()

        assertEquals(0, staging.closeCount)
        assertEquals(1, adapter.outputOwnedPreparedNativeFramePayloadCount)
        assertTrue(adapter.releaseOutputOwnedPreparedNativeFramePayload(token))
        assertEquals(1, staging.closeCount)
        assertEquals(0, adapter.outputOwnedPreparedNativeFramePayloadCount)
    }

    @Test
    fun `terminal close releases owned handles but never borrowed handles`() {
        val adapter = GPURuntimeResourceAdapter()
        val owned = CountingGPUBuffer("owned")
        val borrowed = CountingGPUBuffer("borrowed")
        val payload = uploadPayload(
            frame = 19,
            source = GPUPreparedNativeBufferOperand(
                owned,
                GPUDeviceGenerationID(11),
                GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
            ),
            destination = GPUPreparedNativeBufferOperand(borrowed, GPUDeviceGenerationID(11)),
        )
        val token = adapter.registeredToken(payload)
        assertTrue(adapter.consumePreparedNativeFramePayload(token, payload.identity) is GPUPreparedNativeFrameConsumption.Consumed)
        assertTrue(adapter.markPreparedNativeFrameSubmitted(token))
        assertTrue(adapter.quarantinePreparedNativeFramePayload(token))

        adapter.close()

        assertEquals(1, owned.closeCount)
        assertEquals(0, borrowed.closeCount)
        assertTrue(
            adapter.registerReadyPayload(uploadPayload(20)) is
                GPUPreparedNativeFrameRegistration.Refused,
        )
    }

    @Test
    fun `payload completion releases an auxiliary uniform buffer owned by the materializer`() {
        val adapter = GPURuntimeResourceAdapter()
        val uniform = CountingGPUBuffer("solid-rect-canonical-uniform")
        val payload = testPayload(
            frame = 20,
            scopes = listOf(
                GPUPreparedNativeScopeOperand.Upload(
                    sourceStepIndex = 1,
                    source = GPUPreparedNativeBufferOperand(
                        CountingGPUBuffer("borrowed-upload-source"),
                        GPUDeviceGenerationID(11),
                    ),
                    destination = GPUPreparedNativeBufferOperand(
                        CountingGPUBuffer("borrowed-upload-destination"),
                        GPUDeviceGenerationID(11),
                    ),
                ),
            ),
            auxiliaryOwnedHandles = listOf(
                GPUPreparedNativeAuxiliaryHandle(
                    uniform,
                    GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                ),
            ),
        )
        val token = adapter.registeredToken(payload)
        assertIs<GPUPreparedNativeFrameConsumption.Consumed>(
            adapter.consumePreparedNativeFramePayload(token, payload.identity),
        )
        assertTrue(adapter.markPreparedNativeFrameSubmitted(token))

        assertTrue(adapter.releasePreparedNativeFramePayload(token))

        assertEquals(1, uniform.closeCount)
    }

    @Test
    fun `completion anchor owns borrowed operand handles until the payload is terminal`() {
        val adapter = GPURuntimeResourceAdapter()
        val borrowed = CountingGPUBuffer("materializer-borrowed")
        val anchor = GPUPreparedNativeCompletionAnchor(listOf(borrowed))
        val payload = testPayload(
            frame = 21,
            scopes = listOf(
                GPUPreparedNativeScopeOperand.Upload(
                    sourceStepIndex = 1,
                    source = GPUPreparedNativeBufferOperand(
                        borrowed,
                        GPUDeviceGenerationID(11),
                    ),
                    destination = GPUPreparedNativeBufferOperand(
                        CountingGPUBuffer("materializer-borrowed-destination"),
                        GPUDeviceGenerationID(11),
                    ),
                ),
            ),
            auxiliaryOwnedHandles = listOf(
                GPUPreparedNativeAuxiliaryHandle(
                    anchor,
                    GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                ),
            ),
        )
        val token = adapter.registeredToken(payload)
        assertEquals(0, borrowed.closeCount)
        assertIs<GPUPreparedNativeFrameConsumption.Consumed>(
            adapter.consumePreparedNativeFramePayload(token, payload.identity),
        )
        assertTrue(adapter.markPreparedNativeFrameSubmitted(token))

        assertTrue(adapter.releasePreparedNativeFramePayload(token))
        assertEquals(1, borrowed.closeCount)
        anchor.close()
        adapter.close()
        assertEquals(1, borrowed.closeCount)
    }

    @Test
    fun `completion anchor closes on rollback and closed registry refusal`() {
        val rollbackAdapter = GPURuntimeResourceAdapter()
        val rollbackHandle = CountingGPUBuffer("anchor-rollback")
        val rollbackPayload = anchoredBorrowedPayload(22, rollbackHandle)
        val rollbackToken = rollbackAdapter.registeredToken(rollbackPayload)

        assertTrue(rollbackAdapter.rollbackPreparedNativeFramePayload(rollbackToken))
        assertEquals(1, rollbackHandle.closeCount)
        rollbackAdapter.close()
        assertEquals(1, rollbackHandle.closeCount)

        val refusalAdapter = GPURuntimeResourceAdapter()
        refusalAdapter.close()
        val refusalHandle = CountingGPUBuffer("anchor-refusal")
        val refusal = refusalAdapter.registerReadyPayload(anchoredBorrowedPayload(23, refusalHandle))

        assertIs<GPUPreparedNativeFrameRegistration.Refused>(refusal)
        assertEquals(1, refusalHandle.closeCount)
    }

    @Test
    fun `completion anchor close failure stays quarantined and retries only the failed handle`() {
        val adapter = GPURuntimeResourceAdapter()
        val stable = CountingGPUBuffer("anchor-stable")
        val retry = CountingGPUBuffer("anchor-retry", closeFailuresRemaining = 1)
        val payload = anchoredBorrowedPayload(24, stable, retry)
        val token = adapter.registeredToken(payload)
        assertIs<GPUPreparedNativeFrameConsumption.Consumed>(
            adapter.consumePreparedNativeFramePayload(token, payload.identity),
        )
        assertTrue(adapter.markPreparedNativeFrameSubmitted(token))

        assertFalse(adapter.releasePreparedNativeFramePayload(token))
        assertEquals(1, adapter.quarantinedPreparedNativeFramePayloadCount)
        assertEquals(1, stable.closeCount)
        assertEquals(1, retry.closeCount)

        adapter.close()

        assertEquals(0, adapter.quarantinedPreparedNativeFramePayloadCount)
        assertEquals(1, stable.closeCount)
        assertEquals(2, retry.closeCount)
    }

    @Test
    fun `second anchor sharing an owned handle refuses without closing the first owner`() {
        val adapter = GPURuntimeResourceAdapter()
        val shared = CountingGPUBuffer("anchor-shared")
        val firstUnique = CountingGPUBuffer("anchor-first-unique")
        val secondUnique = CountingGPUBuffer("anchor-second-unique")
        val firstPayload = anchoredBorrowedPayload(25, shared, firstUnique)
        val firstToken = adapter.registeredToken(firstPayload)

        val second = adapter.registerReadyPayload(
            anchoredBorrowedPayload(26, shared, secondUnique),
        )

        assertIs<GPUPreparedNativeFrameRegistration.Refused>(second)
        assertEquals(0, shared.closeCount)
        assertEquals(0, firstUnique.closeCount)
        assertEquals(1, secondUnique.closeCount)
        assertIs<GPUPreparedNativeFrameConsumption.Consumed>(
            adapter.consumePreparedNativeFramePayload(firstToken, firstPayload.identity),
        )
        assertTrue(adapter.markPreparedNativeFrameSubmitted(firstToken))
        assertTrue(adapter.releasePreparedNativeFramePayload(firstToken))
        assertEquals(1, shared.closeCount)
        assertEquals(1, firstUnique.closeCount)
        adapter.close()
        assertEquals(1, shared.closeCount)
        assertEquals(1, firstUnique.closeCount)
        assertEquals(1, secondUnique.closeCount)
    }

    @Test
    fun `anchored claim conflicts with an earlier borrower without closing external ownership`() {
        val adapter = GPURuntimeResourceAdapter()
        val externallyBorrowed = CountingGPUBuffer("anchor-earlier-borrower")
        val firstPayload = uploadPayload(
            frame = 27,
            source = GPUPreparedNativeBufferOperand(
                externallyBorrowed,
                GPUDeviceGenerationID(11),
            ),
        )
        val firstToken = adapter.registeredToken(firstPayload)
        val secondUnique = CountingGPUBuffer("anchor-borrower-conflict-unique")

        val second = adapter.registerReadyPayload(
            anchoredBorrowedPayload(28, externallyBorrowed, secondUnique),
        )

        assertIs<GPUPreparedNativeFrameRegistration.Refused>(second)
        assertEquals(0, externallyBorrowed.closeCount)
        assertEquals(1, secondUnique.closeCount)
        assertTrue(adapter.rollbackPreparedNativeFramePayload(firstToken))
        assertEquals(0, externallyBorrowed.closeCount)
        adapter.close()
        assertEquals(0, externallyBorrowed.closeCount)
        assertEquals(1, secondUnique.closeCount)
    }

    @Test
    fun `completion anchor snapshot may contain only exact borrowed operand identities`() {
        val borrowed = CountingGPUBuffer("anchor-exact-borrowed")
        val notAnOperand = CountingGPUBuffer("anchor-not-an-operand")
        val anchor = GPUPreparedNativeCompletionAnchor(listOf(notAnOperand))
        assertSame(notAnOperand, anchor.ownedHandlesSnapshot().single())

        val failure = assertFailsWith<IllegalArgumentException> {
            testPayload(
                frame = 29,
                scopes = listOf(
                    GPUPreparedNativeScopeOperand.Upload(
                        sourceStepIndex = 1,
                        source = GPUPreparedNativeBufferOperand(borrowed, GPUDeviceGenerationID(11)),
                        destination = GPUPreparedNativeBufferOperand(
                            CountingGPUBuffer("anchor-exact-destination"),
                            GPUDeviceGenerationID(11),
                        ),
                    ),
                ),
                auxiliaryOwnedHandles = listOf(
                    GPUPreparedNativeAuxiliaryHandle(
                        anchor,
                        GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                    ),
                ),
            )
        }

        assertTrue(failure.message.orEmpty().contains("exact borrowed operand"))
        assertEquals(0, notAnOperand.closeCount)
        anchor.close()
        assertEquals(1, notAnOperand.closeCount)
    }

    @Test
    fun `pre registration ledger retries only close failures without forgetting successful handles`() {
        val ledger = GPUPreRegistrationNativeHandleLedger()
        val stable = CountingGPUBuffer("pre-registration-stable")
        val retry = CountingGPUBuffer("pre-registration-retry", closeFailuresRemaining = 1)
        ledger.track(stable)
        ledger.track(retry)

        assertFalse(ledger.closeRetainingFailures())
        assertEquals(1, stable.closeCount)
        assertEquals(1, retry.closeCount)
        assertEquals(1, ledger.pendingHandleCount)

        assertTrue(ledger.closeRetainingFailures())
        assertEquals(1, stable.closeCount)
        assertEquals(2, retry.closeCount)
        assertEquals(0, ledger.pendingHandleCount)
        assertTrue(ledger.closeRetainingFailures())
        assertEquals(1, stable.closeCount)
        assertEquals(2, retry.closeCount)
    }

    @Test
    fun `completion then terminal output release never closes completion owned handle twice`() {
        val adapter = GPURuntimeResourceAdapter()
        val completionOwned = CountingGPUBuffer("completion-owned")
        val outputOwned = CountingGPUBuffer("output-owned")
        val payload = mixedOwnershipPayload(completionOwned, outputOwned)
        val token = adapter.registeredToken(payload)
        assertTrue(adapter.consumePreparedNativeFramePayload(token, payload.identity) is GPUPreparedNativeFrameConsumption.Consumed)
        assertTrue(adapter.markPreparedNativeFrameSubmitted(token))

        assertTrue(adapter.releasePreparedNativeFramePayload(token))
        assertEquals(1, completionOwned.closeCount)
        assertEquals(0, outputOwned.closeCount)

        adapter.close()

        assertEquals(1, completionOwned.closeCount)
        assertEquals(1, outputOwned.closeCount)
    }

    @Test
    fun `same native handle cannot have two ownership categories`() {
        val shared = CountingGPUBuffer("shared-cross-category")

        val failure = assertFailsWith<IllegalArgumentException> {
            mixedOwnershipPayload(shared, shared)
        }

        assertTrue(failure.message.orEmpty().contains("ownership"))
        assertEquals(0, shared.closeCount)
    }

    @Test
    fun `owned native handle cannot be registered by two payloads`() {
        val adapter = GPURuntimeResourceAdapter()
        val shared = CountingGPUBuffer("shared-across-payloads")
        val first = adapter.registerReadyPayload(
            uploadPayload(
                frame = 51,
                source = GPUPreparedNativeBufferOperand(
                    shared,
                    GPUDeviceGenerationID(11),
                    GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                ),
            ),
        )

        val second = adapter.registerReadyPayload(
            uploadPayload(
                frame = 52,
                source = GPUPreparedNativeBufferOperand(
                    shared,
                    GPUDeviceGenerationID(11),
                    GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                ),
            ),
        )

        assertIs<GPUPreparedNativeFrameRegistration.Registered>(first)
        assertEquals(
            "unsupported.native-frame-payload.owned-handle-conflict",
            assertIs<GPUPreparedNativeFrameRegistration.Refused>(second).code,
        )
        assertEquals(0, shared.closeCount)
        assertTrue(first.ownership.rollback())
        assertEquals(1, shared.closeCount)
    }

    @Test
    fun `payload owned handles cannot collide with uniform slab or bind group registries`() {
        val adapter = GPURuntimeResourceAdapter()
        val sharedBuffer = CountingGPUBuffer("shared-uniform")
        adapter.prepareUniformSlab("uniform-slab:fullscreen:frame-1") { sharedBuffer }
        assertIs<GPUResourceLeaseFactoryResult.Created>(adapter.createUniformSlab(fullscreenUniformRequest()))

        val bufferCollision = adapter.registerReadyPayload(
            uploadPayload(
                frame = 53,
                source = GPUPreparedNativeBufferOperand(
                    sharedBuffer,
                    GPUDeviceGenerationID(11),
                    GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                ),
            ),
        )
        assertEquals(
            "unsupported.native-frame-payload.owned-handle-conflict",
            assertIs<GPUPreparedNativeFrameRegistration.Refused>(bufferCollision).code,
        )
        assertEquals(0, sharedBuffer.closeCount)

        val sharedBindGroup = CountingGPUBindGroup("shared-bind-group")
        val bindGroupId = "bind-group:shared:collision"
        adapter.prepareBindGroup(bindGroupId) { sharedBindGroup }
        assertIs<GPUResourceLeaseFactoryResult.Created>(
            adapter.createBindGroup(fullscreenBindGroupRequest(bindGroupId)),
        )
        val bindGroupCollision = adapter.registerReadyPayload(bindGroupOwnedPayload(54, sharedBindGroup))
        assertEquals(
            "unsupported.native-frame-payload.owned-handle-conflict",
            assertIs<GPUPreparedNativeFrameRegistration.Refused>(bindGroupCollision).code,
        )
        assertEquals(0, sharedBindGroup.closeCount)

        adapter.close()
        assertEquals(1, sharedBuffer.closeCount)
        assertEquals(1, sharedBindGroup.closeCount)

        val payloadFirstAdapter = GPURuntimeResourceAdapter()
        val payloadBuffer = CountingGPUBuffer("payload-buffer-first")
        val payloadBufferRegistration = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            payloadFirstAdapter.registerReadyPayload(
                uploadPayload(
                    frame = 68,
                    source = GPUPreparedNativeBufferOperand(
                        payloadBuffer,
                        GPUDeviceGenerationID(11),
                        GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                    ),
                ),
            ),
        )
        payloadFirstAdapter.prepareUniformSlab("uniform-slab:fullscreen:frame-1") { payloadBuffer }
        assertIs<GPUResourceLeaseFactoryResult.Failed>(
            payloadFirstAdapter.createUniformSlab(fullscreenUniformRequest()),
        )

        val payloadBindGroup = CountingGPUBindGroup("payload-bind-group-first")
        val payloadBindGroupRegistration = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            payloadFirstAdapter.registerReadyPayload(bindGroupOwnedPayload(69, payloadBindGroup)),
        )
        val payloadBindGroupId = "bind-group:shared:payload-first"
        payloadFirstAdapter.prepareBindGroup(payloadBindGroupId) { payloadBindGroup }
        assertIs<GPUResourceLeaseFactoryResult.Failed>(
            payloadFirstAdapter.createBindGroup(fullscreenBindGroupRequest(payloadBindGroupId)),
        )
        assertTrue(payloadBufferRegistration.ownership.rollback())
        assertTrue(payloadBindGroupRegistration.ownership.rollback())
        assertEquals(1, payloadBuffer.closeCount)
        assertEquals(1, payloadBindGroup.closeCount)
    }

    @Test
    fun `output pending and quarantined payloads keep their owned handle reservation`() {
        val outputAdapter = GPURuntimeResourceAdapter()
        val outputHandle = CountingGPUBuffer("output-pending")
        val outputPayload = readbackPayload(outputHandle)
        val outputToken = outputAdapter.registeredToken(outputPayload)
        assertIs<GPUPreparedNativeFrameConsumption.Consumed>(
            outputAdapter.consumePreparedNativeFramePayload(outputToken, outputPayload.identity),
        )
        assertTrue(outputAdapter.markPreparedNativeFrameSubmitted(outputToken))
        assertTrue(outputAdapter.releasePreparedNativeFramePayload(outputToken))
        val outputCollision = outputAdapter.registerReadyPayload(
            uploadPayload(
                frame = 55,
                source = GPUPreparedNativeBufferOperand(
                    outputHandle,
                    GPUDeviceGenerationID(11),
                    GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                ),
            ),
        )
        assertIs<GPUPreparedNativeFrameRegistration.Refused>(outputCollision)
        assertEquals(0, outputHandle.closeCount)
        assertTrue(outputAdapter.claimOutputOwnedPreparedNativeFramePayloadMapping(outputToken))
        assertTrue(outputAdapter.releaseOutputOwnedPreparedNativeFramePayload(outputToken))
        assertEquals(1, outputHandle.closeCount)

        val quarantineAdapter = GPURuntimeResourceAdapter()
        val quarantinedHandle = CountingGPUBuffer("quarantined")
        val quarantinedPayload = uploadPayload(
            frame = 56,
            source = GPUPreparedNativeBufferOperand(
                quarantinedHandle,
                GPUDeviceGenerationID(11),
                GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
            ),
        )
        val quarantineToken = quarantineAdapter.registeredToken(quarantinedPayload)
        assertIs<GPUPreparedNativeFrameConsumption.Consumed>(
            quarantineAdapter.consumePreparedNativeFramePayload(quarantineToken, quarantinedPayload.identity),
        )
        assertTrue(quarantineAdapter.markPreparedNativeFrameSubmitted(quarantineToken))
        assertTrue(quarantineAdapter.quarantinePreparedNativeFramePayload(quarantineToken))
        assertIs<GPUPreparedNativeFrameRegistration.Refused>(
            quarantineAdapter.registerReadyPayload(
                uploadPayload(
                    frame = 57,
                    source = GPUPreparedNativeBufferOperand(
                        quarantinedHandle,
                        GPUDeviceGenerationID(11),
                        GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                    ),
                ),
            ),
        )
        assertEquals(0, quarantinedHandle.closeCount)
        quarantineAdapter.close()
        assertEquals(1, quarantinedHandle.closeCount)
    }

    @Test
    fun `borrowed handle may be shared across borrowed payloads`() {
        val adapter = GPURuntimeResourceAdapter()
        val shared = CountingGPUBuffer("borrowed-shared")
        val first = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            adapter.registerReadyPayload(uploadPayload(frame = 58, source = GPUPreparedNativeBufferOperand(shared, GPUDeviceGenerationID(11)))),
        )
        val second = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            adapter.registerReadyPayload(uploadPayload(frame = 59, source = GPUPreparedNativeBufferOperand(shared, GPUDeviceGenerationID(11)))),
        )

        assertTrue(first.ownership.rollback())
        assertTrue(second.ownership.rollback())
        assertEquals(0, shared.closeCount)
    }

    @Test
    fun `payload owned and borrowed handle references conflict in both orders`() {
        val borrowedFirstAdapter = GPURuntimeResourceAdapter()
        val borrowedFirst = CountingGPUBuffer("borrowed-then-owned")
        val borrowedRegistration = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            borrowedFirstAdapter.registerReadyPayload(
                uploadPayload(frame = 60, source = GPUPreparedNativeBufferOperand(borrowedFirst, GPUDeviceGenerationID(11))),
            ),
        )
        assertIs<GPUPreparedNativeFrameRegistration.Refused>(
            borrowedFirstAdapter.registerReadyPayload(
                uploadPayload(
                    frame = 61,
                    source = GPUPreparedNativeBufferOperand(
                        borrowedFirst,
                        GPUDeviceGenerationID(11),
                        GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                    ),
                ),
            ),
        )
        assertTrue(borrowedRegistration.ownership.rollback())
        assertEquals(0, borrowedFirst.closeCount)

        val ownedFirstAdapter = GPURuntimeResourceAdapter()
        val ownedFirst = CountingGPUBuffer("owned-then-borrowed")
        val ownedRegistration = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            ownedFirstAdapter.registerReadyPayload(
                uploadPayload(
                    frame = 62,
                    source = GPUPreparedNativeBufferOperand(
                        ownedFirst,
                        GPUDeviceGenerationID(11),
                        GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                    ),
                ),
            ),
        )
        assertIs<GPUPreparedNativeFrameRegistration.Refused>(
            ownedFirstAdapter.registerReadyPayload(
                uploadPayload(frame = 63, source = GPUPreparedNativeBufferOperand(ownedFirst, GPUDeviceGenerationID(11))),
            ),
        )
        assertTrue(ownedRegistration.ownership.rollback())
        assertEquals(1, ownedFirst.closeCount)
    }

    @Test
    fun `registry owned handles refuse later borrowed payloads`() {
        val adapter = GPURuntimeResourceAdapter()
        val registryBuffer = CountingGPUBuffer("registry-buffer-then-borrowed")
        adapter.prepareUniformSlab("uniform-slab:fullscreen:frame-1") { registryBuffer }
        assertIs<GPUResourceLeaseFactoryResult.Created>(adapter.createUniformSlab(fullscreenUniformRequest()))
        assertIs<GPUPreparedNativeFrameRegistration.Refused>(
            adapter.registerReadyPayload(
                uploadPayload(frame = 64, source = GPUPreparedNativeBufferOperand(registryBuffer, GPUDeviceGenerationID(11))),
            ),
        )

        val registryBindGroup = CountingGPUBindGroup("registry-bind-group-then-borrowed")
        val bindGroupId = "bind-group:shared:borrowed-collision"
        adapter.prepareBindGroup(bindGroupId) { registryBindGroup }
        assertIs<GPUResourceLeaseFactoryResult.Created>(adapter.createBindGroup(fullscreenBindGroupRequest(bindGroupId)))
        assertIs<GPUPreparedNativeFrameRegistration.Refused>(
            adapter.registerReadyPayload(bindGroupBorrowedPayload(65, registryBindGroup)),
        )
        adapter.close()
        assertEquals(1, registryBuffer.closeCount)
        assertEquals(1, registryBindGroup.closeCount)
    }

    @Test
    fun `borrowed payload handles refuse later uniform slab and bind group ownership`() {
        val bufferAdapter = GPURuntimeResourceAdapter()
        val borrowedBuffer = CountingGPUBuffer("borrowed-buffer-then-registry")
        val bufferPayload = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            bufferAdapter.registerReadyPayload(
                uploadPayload(frame = 66, source = GPUPreparedNativeBufferOperand(borrowedBuffer, GPUDeviceGenerationID(11))),
            ),
        )
        bufferAdapter.prepareUniformSlab("uniform-slab:fullscreen:frame-1") { borrowedBuffer }
        val bufferResult = assertIs<GPUResourceLeaseFactoryResult.Failed>(
            bufferAdapter.createUniformSlab(fullscreenUniformRequest()),
        )
        assertEquals("owned-handle-conflict", bufferResult.diagnostic.facts["reason"])
        assertTrue(bufferPayload.ownership.rollback())
        assertEquals(0, borrowedBuffer.closeCount)

        val bindGroupAdapter = GPURuntimeResourceAdapter()
        val borrowedBindGroup = CountingGPUBindGroup("borrowed-bind-group-then-registry")
        val bindGroupPayload = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            bindGroupAdapter.registerReadyPayload(bindGroupBorrowedPayload(67, borrowedBindGroup)),
        )
        val bindGroupId = "bind-group:shared:borrowed-first"
        bindGroupAdapter.prepareBindGroup(bindGroupId) { borrowedBindGroup }
        val bindGroupResult = assertIs<GPUResourceLeaseFactoryResult.Failed>(
            bindGroupAdapter.createBindGroup(fullscreenBindGroupRequest(bindGroupId)),
        )
        assertEquals("owned-handle-conflict", bindGroupResult.diagnostic.facts["reason"])
        assertTrue(bindGroupPayload.ownership.rollback())
        assertEquals(0, borrowedBindGroup.closeCount)
    }

    @Test
    fun `all eight native scope variants retain exact handles and draft waits for surface bind`() {
        val generation = GPUDeviceGenerationID(11)
        val renderTarget = fakeNative<GPUTextureView>("render-target")
        val renderPipeline = fakeNative<GPURenderPipeline>("render-pipeline")
        val computePipeline = fakeNative<io.ygdrasil.webgpu.GPUComputePipeline>("compute-pipeline")
        val uploadSource = CountingGPUBuffer("upload-source")
        val uploadDestination = CountingGPUBuffer("upload-destination")
        val copySource = fakeNative<GPUTexture>("copy-source")
        val copyDestination = fakeNative<GPUTexture>("copy-destination")
        val destinationSource = fakeNative<GPUTexture>("destination-source")
        val destinationTarget = fakeNative<GPUTexture>("destination-target")
        val drawSource = fakeNative<GPUTextureView>("draw-source")
        val drawTarget = fakeNative<GPUTextureView>("draw-target")
        val drawPipeline = fakeNative<GPURenderPipeline>("draw-pipeline")
        val drawBindGroup = fakeNative<GPUBindGroup>("draw-bind-group")
        val readbackSource = fakeNative<GPUTexture>("readback-source")
        val readbackDestination = CountingGPUBuffer("readback-destination")
        val surfaceSource = fakeNative<GPUTextureView>("surface-source")
        val surfacePipeline = fakeNative<GPURenderPipeline>("surface-pipeline")
        val surfaceBindGroup = fakeNative<GPUBindGroup>("surface-bind-group")
        val lateSurfaceTarget = fakeNative<GPUTextureView>("surface-target")
        val surfaceOutput = org.graphiks.kanvas.gpu.renderer.recording.GPUSurfaceOutputRef("surface.main")
        val scopes = listOf<GPUPreparedNativeScopeOperand>(
            GPUPreparedNativeScopeOperand.Render(
                sourceStepIndex = 0,
                pass = GPUPreparedNativeRenderPassConfig(
                    GPUPreparedNativeTextureViewOperand(renderTarget, generation),
                ),
                commands = listOf(
                    GPUPreparedNativeRenderCommand.SetPipeline(
                        GPUPreparedNativeRenderPipelineOperand(renderPipeline, generation),
                    ),
                    GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3)),
                ),
            ),
            GPUPreparedNativeScopeOperand.Compute(
                sourceStepIndex = 1,
                pipelines = listOf(GPUPreparedNativeComputePipelineOperand(computePipeline, generation)),
                bindGroups = emptyList(),
            ),
            GPUPreparedNativeScopeOperand.Upload(
                sourceStepIndex = 2,
                source = GPUPreparedNativeBufferOperand(uploadSource, generation),
                destination = GPUPreparedNativeBufferOperand(uploadDestination, generation),
            ),
            GPUPreparedNativeScopeOperand.Copy(
                sourceStepIndex = 3,
                operationKind = GPUEncoderOperationKind.Copy,
                source = GPUPreparedNativeTextureOperand(copySource, generation),
                destination = GPUPreparedNativeTextureOperand(copyDestination, generation),
                textureLayout = GPUPreparedNativeTextureCopyLayout(
                    sourceOriginX = 0,
                    sourceOriginY = 0,
                    destinationOriginX = 0,
                    destinationOriginY = 0,
                    width = 1,
                    height = 1,
                ),
            ),
            GPUPreparedNativeScopeOperand.Copy(
                sourceStepIndex = 4,
                operationKind = GPUEncoderOperationKind.CopyDestination,
                source = GPUPreparedNativeTextureOperand(destinationSource, generation),
                destination = GPUPreparedNativeTextureOperand(destinationTarget, generation),
                textureLayout = GPUPreparedNativeTextureCopyLayout(
                    sourceOriginX = 7,
                    sourceOriginY = 9,
                    destinationOriginX = 0,
                    destinationOriginY = 0,
                    width = 11,
                    height = 13,
                ),
            ),
            GPUPreparedNativeScopeOperand.CopyAsDraw(
                sourceStepIndex = 5,
                source = GPUPreparedNativeTextureViewOperand(drawSource, generation),
                target = GPUPreparedNativeTextureViewOperand(drawTarget, generation),
                pipeline = GPUPreparedNativeRenderPipelineOperand(drawPipeline, generation),
                bindGroup = GPUPreparedNativeBindGroupOperand(drawBindGroup, generation),
            ),
            GPUPreparedNativeScopeOperand.Readback(
                sourceStepIndex = 6,
                source = GPUPreparedNativeTextureOperand(readbackSource, generation),
                destination = GPUPreparedNativeBufferOperand(readbackDestination, generation),
                layout = GPUPreparedNativeReadbackLayout(
                    originX = 0,
                    originY = 0,
                    width = 4,
                    height = 4,
                    bytesPerRow = 256,
                    rowsPerImage = 4,
                    bufferOffset = 0,
                    mappedSize = 1024,
                    format = GPUTextureFormat.RGBA8Unorm,
                ),
            ),
            GPUPreparedNativeScopeOperand.SurfaceBlit(
                sourceStepIndex = 7,
                source = GPUPreparedNativeTextureViewOperand(surfaceSource, generation),
                output = surfaceOutput,
                pipeline = GPUPreparedNativeRenderPipelineOperand(surfacePipeline, generation),
                bindGroup = GPUPreparedNativeBindGroupOperand(surfaceBindGroup, generation),
            ),
        )
        val scopeKeys = scopes.mapIndexed { index, scope -> testOperandKeys(scope, "matrix.$index") }
        val payload = GPUPreparedNativeFramePayload(
            identity = GPUPreparedNativeFrameIdentity(
                frameId = GPUFrameID(41),
                contextIdentity = "target.scene",
                encoderPlanId = "frame.41",
                deviceGeneration = generation,
                targetGeneration = 3,
                scopes = scopes.mapIndexed { index, scope ->
                    GPUPreparedNativeScopeKey(
                        scope.sourceStepIndex,
                        scope.operationKind,
                        operandKeys = scopeKeys[index],
                    )
                },
            ),
            scopeOperands = scopes,
            scopeOperandKeys = scopeKeys,
        )
        val adapter = GPURuntimeResourceAdapter()
        val ownership = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
            adapter.registerPreparedNativeFrameDraft(GPUPreparedNativeFrameDraft(payload)),
        ).ownership

        assertEquals(
            "unsupported.native-frame-payload.draft-not-ready",
            assertIs<GPUPreparedNativeFrameConsumption.Refused>(ownership.consume(payload.identity)).code,
        )
        val acquired = GPUAcquiredSurfaceOutput(surfaceOutput, generation, 3, "surface")
        val surfaceScope = assertIs<GPUPreparedNativeScopeOperand.SurfaceBlit>(scopes.last())
        assertIs<GPUPreparedNativeFrameBindingResult.Ready>(
            ownership.bindLateSurface(
                acquired,
                GPUPreparedNativeFrameLateSurfaceBinding.Bound(
                    surfaceOutput,
                    GPUPreparedNativeTextureViewOperand(lateSurfaceTarget, generation),
                ),
            ),
        )
        assertSame(lateSurfaceTarget, surfaceScope.target.view)
        assertSame(lateSurfaceTarget, surfaceScope.operands[1].nativeHandle())
        val consumed = assertIs<GPUPreparedNativeFrameConsumption.Consumed>(ownership.consume(payload.identity))
        assertSame(payload, consumed.payload)

        val expectedHandles = listOf(
            listOf(renderTarget, renderPipeline),
            listOf(computePipeline),
            listOf(uploadSource, uploadDestination),
            listOf(copySource, copyDestination),
            listOf(destinationSource, destinationTarget),
            listOf(drawSource, drawTarget, drawPipeline, drawBindGroup),
            listOf(readbackSource, readbackDestination),
            listOf(surfaceSource, lateSurfaceTarget, surfacePipeline, surfaceBindGroup),
        )
        scopes.zip(expectedHandles).forEach { (scope, expected) ->
            assertEquals(expected.size, scope.operands.size)
            scope.operands.zip(expected).forEach { (operand, handle) ->
                assertSame(handle, operand.nativeHandle())
            }
        }
        val destinationCopy = assertIs<GPUPreparedNativeScopeOperand.Copy>(scopes[4])
        assertEquals(7, destinationCopy.textureLayout?.sourceOriginX)
        assertEquals(9, destinationCopy.textureLayout?.sourceOriginY)
        assertEquals(11, destinationCopy.textureLayout?.width)
        assertEquals(13, destinationCopy.textureLayout?.height)
    }

    @Test
    fun `destination copy layout requires bounded positive extent and non negative origins`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedNativeTextureCopyLayout(-1, 0, 0, 0, 1, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedNativeTextureCopyLayout(0, 0, 0, 0, 0, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedNativeTextureCopyLayout(0, 0, 0, 0, 1, 0)
        }
    }

    @Test
    fun `rollback close failure never escapes and retains ownership for terminal retry`() {
        val adapter = GPURuntimeResourceAdapter()
        val owned = CountingGPUBuffer("throw-once", closeFailuresRemaining = 1)
        val payload = uploadPayload(
            source = GPUPreparedNativeBufferOperand(
                owned,
                GPUDeviceGenerationID(11),
                GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
            ),
        )
        val token = adapter.registeredToken(payload)

        assertFalse(adapter.rollbackPreparedNativeFramePayload(token))
        assertEquals(1, adapter.quarantinedPreparedNativeFramePayloadCount)

        adapter.close()

        assertEquals(2, owned.closeCount)
        assertEquals(0, adapter.quarantinedPreparedNativeFramePayloadCount)
    }

    @Test
    fun `terminal close retries a newly quarantined prepared payload once`() {
        val adapter = GPURuntimeResourceAdapter()
        val transient = CountingGPUBuffer("prepared-close-transient", closeFailuresRemaining = 1)
        adapter.registeredToken(
            uploadPayload(
                frame = 70,
                source = GPUPreparedNativeBufferOperand(
                    transient,
                    GPUDeviceGenerationID(11),
                    GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                ),
            ),
        )

        adapter.close()

        assertEquals(2, transient.closeCount)
        assertEquals(0, adapter.activePreparedNativeFramePayloadCount)
        assertEquals(0, adapter.quarantinedPreparedNativeFramePayloadCount)
    }

    @Test
    fun `terminal close retries newly quarantined unclaimed output without reclosing completion handles`() {
        val adapter = GPURuntimeResourceAdapter()
        val completionOwned = CountingGPUBuffer("output-close-completion")
        val transientOutput = CountingGPUBuffer("output-close-transient", closeFailuresRemaining = 1)
        val payload = mixedOwnershipPayload(completionOwned, transientOutput)
        val token = adapter.registeredToken(payload)
        assertIs<GPUPreparedNativeFrameConsumption.Consumed>(
            adapter.consumePreparedNativeFramePayload(token, payload.identity),
        )
        assertTrue(adapter.markPreparedNativeFrameSubmitted(token))
        assertTrue(adapter.releasePreparedNativeFramePayload(token))
        assertEquals(1, completionOwned.closeCount)
        assertEquals(1, adapter.outputOwnedPreparedNativeFramePayloadCount)

        adapter.close()

        assertEquals(1, completionOwned.closeCount)
        assertEquals(2, transientOutput.closeCount)
        assertEquals(0, adapter.outputOwnedPreparedNativeFramePayloadCount)
        assertEquals(0, adapter.quarantinedPreparedNativeFramePayloadCount)
    }

    @Test
    fun `terminal close still processes payloads quarantined before close`() {
        val adapter = GPURuntimeResourceAdapter()
        val preexisting = CountingGPUBuffer("preexisting-quarantine")
        val payload = uploadPayload(
            frame = 71,
            source = GPUPreparedNativeBufferOperand(
                preexisting,
                GPUDeviceGenerationID(11),
                GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
            ),
        )
        val token = adapter.registeredToken(payload)
        assertIs<GPUPreparedNativeFrameConsumption.Consumed>(
            adapter.consumePreparedNativeFramePayload(token, payload.identity),
        )
        assertTrue(adapter.markPreparedNativeFrameSubmitted(token))
        assertTrue(adapter.quarantinePreparedNativeFramePayload(token))
        assertEquals(1, adapter.quarantinedPreparedNativeFramePayloadCount)

        adapter.close()

        assertEquals(1, preexisting.closeCount)
        assertEquals(0, adapter.quarantinedPreparedNativeFramePayloadCount)
    }

    @Test
    fun `permanent terminal close failure remains quarantined and repeated close never recloses successes`() {
        val adapter = GPURuntimeResourceAdapter()
        val stable = CountingGPUBuffer("terminal-close-stable")
        val permanent = CountingGPUBuffer(
            "terminal-close-permanent",
            closeFailuresRemaining = Int.MAX_VALUE,
        )
        adapter.registeredToken(anchoredBorrowedPayload(72, stable, permanent))

        adapter.close()

        assertEquals(1, stable.closeCount)
        assertEquals(2, permanent.closeCount)
        assertEquals(1, adapter.quarantinedPreparedNativeFramePayloadCount)

        adapter.close()

        assertEquals(1, stable.closeCount)
        assertEquals(3, permanent.closeCount)
        assertEquals(1, adapter.quarantinedPreparedNativeFramePayloadCount)
    }

    @Test
    fun `closed registry takes ownership of refused payload without leaking handles`() {
        val adapter = GPURuntimeResourceAdapter()
        adapter.close()
        val owned = CountingGPUBuffer("registration-refused")
        val payload = uploadPayload(
            source = GPUPreparedNativeBufferOperand(
                owned,
                GPUDeviceGenerationID(11),
                GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
            ),
        )

        assertTrue(
            adapter.registerReadyPayload(payload) is GPUPreparedNativeFrameRegistration.Refused,
        )
        assertEquals(1, owned.closeCount)
    }

    @Test
    fun `native frame registry never invokes legacy pending creation callback`() {
        val adapter = GPURuntimeResourceAdapter(requirePreparedResources = true)
        var legacyCreates = 0
        adapter.prepareUniformSlab("legacy") {
            legacyCreates += 1
            CountingGPUBuffer("legacy")
        }

        adapter.registerReadyPayload(uploadPayload())

        assertEquals(0, legacyCreates)
    }

    @Test
    fun `rollback invalidates unsubmitted payload while completion releases and failure quarantines`() {
        val adapter = GPURuntimeResourceAdapter()
        val rolledBack = uploadPayload(frame = 1)
        val completed = uploadPayload(frame = 2)
        val failed = uploadPayload(frame = 3)
        val rollbackToken = adapter.registeredToken(rolledBack)
        val completedToken = adapter.registeredToken(completed)
        val failedToken = adapter.registeredToken(failed)

        assertTrue(adapter.rollbackPreparedNativeFramePayload(rollbackToken))
        assertTrue(adapter.consumePreparedNativeFramePayload(rollbackToken, rolledBack.identity) is GPUPreparedNativeFrameConsumption.Refused)

        assertTrue(adapter.consumePreparedNativeFramePayload(completedToken, completed.identity) is GPUPreparedNativeFrameConsumption.Consumed)
        assertTrue(adapter.markPreparedNativeFrameSubmitted(completedToken))
        assertTrue(adapter.releasePreparedNativeFramePayload(completedToken))

        assertTrue(adapter.consumePreparedNativeFramePayload(failedToken, failed.identity) is GPUPreparedNativeFrameConsumption.Consumed)
        assertTrue(adapter.markPreparedNativeFrameSubmitted(failedToken))
        assertTrue(adapter.quarantinePreparedNativeFramePayload(failedToken))
        assertEquals(0, adapter.activePreparedNativeFramePayloadCount)
        assertEquals(1, adapter.quarantinedPreparedNativeFramePayloadCount)
    }

    @Test
    fun `create uniform slab registers lease facts and membership`() {
        val adapter = GPURuntimeResourceAdapter()

        val result = adapter.createUniformSlab(
            GPUUniformSlabLeaseRequest(
                leaseId = "uniform-slab:fullscreen:frame-1",
                targetId = "root-target",
                frameId = "frame-1",
                deviceGeneration = 11,
                descriptorHash = "sha256:uniform-slab-frame-1",
                totalBytes = 512,
                alignmentBytes = 256,
                releasePolicy = "submission-complete",
                payloadCount = 2,
            ),
        )

        val created = result as GPUResourceLeaseFactoryResult.Created

        assertEquals(GPUResourceLeaseKind.UniformSlab, created.lease.resourceKind)
        assertEquals(
            mapOf(
                "alignment" to "256",
                "payloadCount" to "2",
                "target" to "root-target",
                "totalBytes" to "512",
            ),
            created.lease.evidenceFacts,
        )
        assertTrue(adapter.containsLease("uniform-slab:fullscreen:frame-1"))
        assertFalse(created.lease.dumpLines().joinToString("\n").contains("@"))
    }

    @Test
    fun `fullscreen bind group is refused until prerequisite uniform slab exists`() {
        val adapter = GPURuntimeResourceAdapter()
        val bindGroupId = "bind-group:fullscreen:frame-1"

        val result = adapter.createBindGroup(
            GPUBindGroupLeaseRequest(
                leaseId = bindGroupId,
                deviceGeneration = 11,
                descriptorHash = "sha256:bind-group-frame-1",
                ownerScope = "frame-1",
                usageLabels = listOf("uniform"),
                releasePolicy = "submission-complete",
            ),
        )

        val failed = result as GPUResourceLeaseFactoryResult.Failed

        assertEquals("unsupported.resource.adapter_create_failed", failed.diagnostic.code)
        assertFalse(adapter.containsLease(bindGroupId))
        assertFalse(failed.diagnostic.message.contains("@"))
        assertFalse(failed.diagnostic.facts.values.joinToString("\n").contains("@"))
    }

    @Test
    fun `fullscreen bind group is accepted after prerequisite uniform slab exists`() {
        val adapter = GPURuntimeResourceAdapter()
        val uniformId = "uniform-slab:fullscreen:frame-1"
        val bindGroupId = "bind-group:fullscreen:frame-1"

        val uniform = adapter.createUniformSlab(
            GPUUniformSlabLeaseRequest(
                leaseId = uniformId,
                targetId = "root-target",
                frameId = "frame-1",
                deviceGeneration = 11,
                descriptorHash = "sha256:uniform-slab-frame-1",
                totalBytes = 512,
                alignmentBytes = 256,
                releasePolicy = "submission-complete",
                payloadCount = 2,
            ),
        )
        assertTrue(uniform is GPUResourceLeaseFactoryResult.Created)

        val result = adapter.createBindGroup(
            GPUBindGroupLeaseRequest(
                leaseId = bindGroupId,
                deviceGeneration = 11,
                descriptorHash = "sha256:bind-group-frame-1",
                ownerScope = "frame-1",
                usageLabels = listOf("uniform"),
                releasePolicy = "submission-complete",
            ),
        )

        val created = result as GPUResourceLeaseFactoryResult.Created

        assertEquals(GPUResourceLeaseKind.BindGroup, created.lease.resourceKind)
        assertEquals(GPUResourceLeaseCacheResult.Create, created.lease.cacheResult)
        assertTrue(adapter.containsLease(bindGroupId))
        assertFalse(created.lease.dumpLines().joinToString("\n").contains("@"))
    }

    @Test
    fun `non fullscreen bind group is accepted without prerequisite`() {
        val adapter = GPURuntimeResourceAdapter()
        val bindGroupId = "bind-group:shared:frame-1"

        val result = adapter.createBindGroup(
            GPUBindGroupLeaseRequest(
                leaseId = bindGroupId,
                deviceGeneration = 11,
                descriptorHash = "sha256:bind-group-shared",
                ownerScope = "frame-1",
                usageLabels = listOf("uniform"),
                releasePolicy = "submission-complete",
            ),
        )

        val created = result as GPUResourceLeaseFactoryResult.Created

        assertEquals(GPUResourceLeaseKind.BindGroup, created.lease.resourceKind)
        assertTrue(adapter.containsLease(bindGroupId))
        assertFalse(created.lease.dumpLines().joinToString("\n").contains("@"))
    }

    @Test
    fun `strict adapter refuses unprepared uniform slabs`() {
        val adapter = GPURuntimeResourceAdapter(requirePreparedResources = true)

        val result = adapter.createUniformSlab(fullscreenUniformRequest())

        val failed = result as GPUResourceLeaseFactoryResult.Failed
        assertEquals("unsupported.resource.adapter_create_failed", failed.diagnostic.code)
        assertEquals("uniform-slab-preparation-missing", failed.diagnostic.facts["reason"])
        assertNull(adapter.uniformSlabBuffer("uniform-slab:fullscreen:frame-1"))
        assertFalse(adapter.containsLease("uniform-slab:fullscreen:frame-1"))
    }

    @Test
    fun `strict adapter reuses prepared native handles without recreating them`() {
        val adapter = GPURuntimeResourceAdapter(requirePreparedResources = true)
        val buffer = CountingGPUBuffer(label = "unit-buffer")
        val bindGroup = CountingGPUBindGroup(label = "unit-bind-group")
        var bufferCreates = 0
        var bindGroupCreates = 0
        val uniformId = "uniform-slab:fullscreen:frame-1"
        val bindGroupId = "bind-group:fullscreen:frame-1:slot:payload-0"

        adapter.prepareUniformSlab(uniformId) {
            bufferCreates += 1
            buffer
        }
        assertTrue(adapter.createUniformSlab(fullscreenUniformRequest()) is GPUResourceLeaseFactoryResult.Created)
        assertTrue(adapter.createUniformSlab(fullscreenUniformRequest()) is GPUResourceLeaseFactoryResult.Created)
        adapter.prepareBindGroup(bindGroupId) {
            bindGroupCreates += 1
            bindGroup
        }
        assertTrue(adapter.createBindGroup(fullscreenBindGroupRequest(bindGroupId)) is GPUResourceLeaseFactoryResult.Created)
        assertTrue(adapter.createBindGroup(fullscreenBindGroupRequest(bindGroupId)) is GPUResourceLeaseFactoryResult.Created)

        assertEquals(1, bufferCreates)
        assertEquals(1, bindGroupCreates)
        assertSame(buffer, adapter.uniformSlabBuffer(uniformId))
        assertSame(bindGroup, adapter.bindGroup(bindGroupId))
    }

    @Test
    fun `close releases prepared native handles and clears lease membership`() {
        val adapter = GPURuntimeResourceAdapter(requirePreparedResources = true)
        val buffer = CountingGPUBuffer(label = "unit-buffer")
        val bindGroup = CountingGPUBindGroup(label = "unit-bind-group")
        val uniformId = "uniform-slab:fullscreen:frame-1"
        val bindGroupId = "bind-group:fullscreen:frame-1:slot:payload-0"
        adapter.prepareUniformSlab(uniformId) { buffer }
        assertTrue(adapter.createUniformSlab(fullscreenUniformRequest()) is GPUResourceLeaseFactoryResult.Created)
        adapter.prepareBindGroup(bindGroupId) { bindGroup }
        assertTrue(adapter.createBindGroup(fullscreenBindGroupRequest(bindGroupId)) is GPUResourceLeaseFactoryResult.Created)

        adapter.close()

        assertEquals(1, bindGroup.closeCount)
        assertEquals(1, buffer.closeCount)
        assertFalse(adapter.containsLease(uniformId))
        assertFalse(adapter.containsLease(bindGroupId))
        assertNull(adapter.uniformSlabBuffer(uniformId))
        assertNull(adapter.bindGroup(bindGroupId))
    }
}

private fun GPURuntimeResourceAdapter.registeredToken(
    payload: GPUPreparedNativeFramePayload,
): GPUPreparedNativeFrameToken = (
    registerReadyPayload(payload) as GPUPreparedNativeFrameRegistration.Registered
    ).token

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

private fun testOperandKeys(
    scope: GPUPreparedNativeScopeOperand,
    prefix: String,
): List<GPUPreparedNativeOperandKey> {
    val roles = when (scope) {
        is GPUPreparedNativeScopeOperand.Render -> listOf(GPUPreparedNativeOperandRole.RenderColorTarget) +
            scope.operands.drop(1).map { operand ->
                when (operand) {
                    is GPUPreparedNativeRenderPipelineOperand -> GPUPreparedNativeOperandRole.RenderPipeline
                    is GPUPreparedNativeBindGroupOperand -> GPUPreparedNativeOperandRole.RenderBindGroup
                    is GPUPreparedNativeBufferOperand -> when {
                        scope.commands.any {
                            it is GPUPreparedNativeRenderCommand.SetVertexBuffer && it.buffer === operand
                        } -> GPUPreparedNativeOperandRole.RenderVertexBuffer
                        scope.commands.any {
                            it is GPUPreparedNativeRenderCommand.SetIndexBuffer && it.buffer === operand
                        } -> GPUPreparedNativeOperandRole.RenderIndexBuffer
                        else -> error("Unexpected render buffer test operand")
                    }
                    else -> error("Unexpected render test operand")
                }
            }
        is GPUPreparedNativeScopeOperand.Compute -> listOf(GPUPreparedNativeOperandRole.ComputePipeline)
        is GPUPreparedNativeScopeOperand.Upload -> listOf(
            GPUPreparedNativeOperandRole.UploadSource,
            GPUPreparedNativeOperandRole.UploadDestination,
        )
        is GPUPreparedNativeScopeOperand.Copy -> listOf(
            GPUPreparedNativeOperandRole.CopySource,
            GPUPreparedNativeOperandRole.CopyDestination,
        )
        is GPUPreparedNativeScopeOperand.CopyAsDraw -> listOf(
            GPUPreparedNativeOperandRole.CopyAsDrawSource,
            GPUPreparedNativeOperandRole.CopyAsDrawTarget,
            GPUPreparedNativeOperandRole.CopyAsDrawPipeline,
            GPUPreparedNativeOperandRole.CopyAsDrawBindGroup,
        )
        is GPUPreparedNativeScopeOperand.Readback -> listOf(
            GPUPreparedNativeOperandRole.ReadbackSource,
            GPUPreparedNativeOperandRole.ReadbackDestination,
        )
        is GPUPreparedNativeScopeOperand.SurfaceBlit -> listOf(
            GPUPreparedNativeOperandRole.SurfaceSource,
            GPUPreparedNativeOperandRole.SurfaceTarget,
            GPUPreparedNativeOperandRole.SurfacePipeline,
            GPUPreparedNativeOperandRole.SurfaceBindGroup,
        )
    }
    val descriptors = if (scope is GPUPreparedNativeScopeOperand.SurfaceBlit) {
        listOf(
            scope.source.nativeKind() to scope.source.ownership,
            GPUPreparedNativeOperandKind.TextureView to GPUPreparedNativeOperandOwnership.Borrowed,
            scope.pipeline.nativeKind() to scope.pipeline.ownership,
            scope.bindGroup.nativeKind() to scope.bindGroup.ownership,
        )
    } else {
        scope.operands.map { it.nativeKind() to it.ownership }
    }
    return roles.zip(descriptors).mapIndexed { index, (role, descriptor) ->
        GPUPreparedNativeOperandKey(role, descriptor.first, "$prefix.operand.$index", descriptor.second)
    }
}

private fun uploadPayload(
    frame: Long = 7,
    source: GPUPreparedNativeBufferOperand = GPUPreparedNativeBufferOperand(
        CountingGPUBuffer("upload-source-$frame"),
        GPUDeviceGenerationID(11),
    ),
    destination: GPUPreparedNativeBufferOperand = GPUPreparedNativeBufferOperand(
        CountingGPUBuffer("upload-destination-$frame"),
        GPUDeviceGenerationID(11),
    ),
): GPUPreparedNativeFramePayload = testPayload(
    frame = frame,
    scopes = listOf(
        GPUPreparedNativeScopeOperand.Upload(
            sourceStepIndex = 1,
            source = source,
            destination = destination,
        ),
    ),
)

private fun anchoredBorrowedPayload(
    frame: Long,
    vararg handles: CountingGPUBuffer,
): GPUPreparedNativeFramePayload {
    require(handles.size in 1..2)
    return testPayload(
        frame = frame,
        scopes = listOf(
            GPUPreparedNativeScopeOperand.Upload(
                sourceStepIndex = 1,
                source = GPUPreparedNativeBufferOperand(handles.first(), GPUDeviceGenerationID(11)),
                destination = GPUPreparedNativeBufferOperand(
                    handles.getOrNull(1) ?: CountingGPUBuffer("anchor-destination-$frame"),
                    GPUDeviceGenerationID(11),
                ),
            ),
        ),
        auxiliaryOwnedHandles = listOf(
            GPUPreparedNativeAuxiliaryHandle(
                GPUPreparedNativeCompletionAnchor(handles.toList()),
                GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
            ),
        ),
    )
}

private fun readbackPayload(staging: CountingGPUBuffer): GPUPreparedNativeFramePayload =
    testPayload(
        frame = 31,
        scopes = listOf(
            GPUPreparedNativeScopeOperand.Readback(
                sourceStepIndex = 1,
                source = GPUPreparedNativeTextureOperand(
                    fakeNative("readback-source"),
                    GPUDeviceGenerationID(11),
                ),
                destination = GPUPreparedNativeBufferOperand(
                    staging,
                    GPUDeviceGenerationID(11),
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
                    mappedSize = 1024,
                    format = GPUTextureFormat.RGBA8Unorm,
                ),
            ),
        ),
    )

private fun mixedOwnershipPayload(
    completionOwned: CountingGPUBuffer,
    outputOwned: CountingGPUBuffer,
): GPUPreparedNativeFramePayload = testPayload(
    frame = 32,
    scopes = listOf(
        GPUPreparedNativeScopeOperand.Upload(
            sourceStepIndex = 1,
            source = GPUPreparedNativeBufferOperand(
                completionOwned,
                GPUDeviceGenerationID(11),
                GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
            ),
            destination = GPUPreparedNativeBufferOperand(
                CountingGPUBuffer("upload-destination"),
                GPUDeviceGenerationID(11),
            ),
        ),
        GPUPreparedNativeScopeOperand.Readback(
            sourceStepIndex = 2,
            source = GPUPreparedNativeTextureOperand(
                fakeNative("readback-source"),
                GPUDeviceGenerationID(11),
            ),
            destination = GPUPreparedNativeBufferOperand(
                outputOwned,
                GPUDeviceGenerationID(11),
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
                mappedSize = 1024,
                format = GPUTextureFormat.RGBA8Unorm,
            ),
        ),
    ),
)

private fun bindGroupOwnedPayload(
    frame: Long,
    bindGroup: CountingGPUBindGroup,
): GPUPreparedNativeFramePayload = bindGroupPayload(
    frame,
    bindGroup,
    GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
)

private fun bindGroupBorrowedPayload(
    frame: Long,
    bindGroup: CountingGPUBindGroup,
): GPUPreparedNativeFramePayload = bindGroupPayload(
    frame,
    bindGroup,
    GPUPreparedNativeOperandOwnership.Borrowed,
)

private fun bindGroupPayload(
    frame: Long,
    bindGroup: CountingGPUBindGroup,
    ownership: GPUPreparedNativeOperandOwnership,
): GPUPreparedNativeFramePayload = testPayload(
    frame = frame,
    scopes = listOf(
        GPUPreparedNativeScopeOperand.Render(
            sourceStepIndex = 1,
            pass = GPUPreparedNativeRenderPassConfig(
                GPUPreparedNativeTextureViewOperand(
                    fakeNative("render-target-$frame"),
                    GPUDeviceGenerationID(11),
                ),
            ),
            commands = listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(
                    GPUPreparedNativeRenderPipelineOperand(
                        fakeNative("render-pipeline-$frame"),
                        GPUDeviceGenerationID(11),
                    ),
                ),
                GPUPreparedNativeRenderCommand.SetBindGroup(
                    0,
                    GPUPreparedNativeBindGroupOperand(
                        bindGroup,
                        GPUDeviceGenerationID(11),
                        ownership,
                    ),
                ),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3)),
            ),
        ),
    ),
)

private fun testPayload(
    frame: Long,
    scopes: List<GPUPreparedNativeScopeOperand>,
    auxiliaryOwnedHandles: List<GPUPreparedNativeAuxiliaryHandle> = emptyList(),
): GPUPreparedNativeFramePayload {
    val keys = scopes.mapIndexed { index, scope -> testOperandKeys(scope, "frame.$frame.scope.$index") }
    return GPUPreparedNativeFramePayload(
        identity = GPUPreparedNativeFrameIdentity(
            frameId = GPUFrameID(frame),
            contextIdentity = "target.scene",
            encoderPlanId = "frame.$frame",
            deviceGeneration = GPUDeviceGenerationID(11),
            targetGeneration = 3,
            scopes = scopes.mapIndexed { index, scope ->
                GPUPreparedNativeScopeKey(
                    scope.sourceStepIndex,
                    scope.operationKind,
                    operandKeys = keys[index],
                )
            },
        ),
        scopeOperands = scopes,
        scopeOperandKeys = keys,
        auxiliaryOwnedHandles = auxiliaryOwnedHandles,
    )
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

private fun fullscreenUniformRequest(): GPUUniformSlabLeaseRequest =
    GPUUniformSlabLeaseRequest(
        leaseId = "uniform-slab:fullscreen:frame-1",
        targetId = "root-target",
        frameId = "frame-1",
        deviceGeneration = 11,
        descriptorHash = "sha256:uniform-slab-frame-1",
        totalBytes = 512,
        alignmentBytes = 256,
        releasePolicy = "submission-complete",
        payloadCount = 2,
    )

private fun fullscreenBindGroupRequest(leaseId: String): GPUBindGroupLeaseRequest =
    GPUBindGroupLeaseRequest(
        leaseId = leaseId,
        deviceGeneration = 11,
        descriptorHash = "sha256:bind-group-frame-1",
        ownerScope = "frame-1",
        usageLabels = listOf("uniform"),
        releasePolicy = "submission-complete",
    )

private class CountingGPUBuffer(
    override var label: String,
    private var closeFailuresRemaining: Int = 0,
) : GPUBuffer {
    var closeCount: Int = 0
        private set

    override val size: ULong = 512u
    override val usage: Set<GPUBufferUsage> = setOf(GPUBufferUsage.Uniform)
    override val mapState: GPUBufferMapState = GPUBufferMapState.Unmapped

    override suspend fun mapAsync(mode: GPUMapMode, offset: GPUSize64, size: GPUSize64?): Result<Unit> =
        Result.success(Unit)

    override fun getMappedRange(offset: GPUSize64, size: GPUSize64?): ArrayBuffer =
        error("CountingGPUBuffer does not expose mapped ranges")

    override fun unmap() = Unit

    override fun close() {
        closeCount += 1
        if (closeFailuresRemaining > 0) {
            closeFailuresRemaining -= 1
            error("close failed")
        }
    }
}

private class CountingGPUBindGroup(
    override var label: String,
) : GPUBindGroup {
    var closeCount: Int = 0
        private set

    override fun close() {
        closeCount += 1
    }
}
