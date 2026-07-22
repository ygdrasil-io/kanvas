package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureView
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUSurfaceOutputRef
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID

class GPUWgpu4kFramePayloadMaterializerDispatcherTest {
    @Test
    fun `all six routes retain failed common pre registration ownership`() {
        listOf(
            "failed.native-solid-rect.materialization",
            "failed.native-core-primitive.materialization",
            "failed.native-registered-uniform.materialization",
            "failed.native-destination-copy.materialization",
            "failed.native-color-glyph.materialization",
            "failed.native-separable-blur.materialization",
        ).forEach { code ->
            val ledger = GPUPreRegistrationNativeHandleLedger()
            val handle = FailOnceHandle()
            ledger.track(handle)
            ledger.closeRetainingFailures()

            val refused = refusedWgpu4kPreRegistrationMaterialization(
                code,
                "route failed",
                ledger,
            )

            assertEquals(code, refused.code)
            assertSame(ledger, refused.retainedPreRegistrationLedger)
            assertEquals(1, handle.closeAttempts)
        }
    }

    @Test
    fun `dispatcher selects every typed prepared route from semantic classes`() {
        assertEquals(
            GPUWgpu4kPreparedFramePayloadRoute.SolidRect,
            selectWgpu4kPreparedFramePayloadRoute(listOf(GPUDrawSemanticPayload.SolidRect::class)),
        )
        assertEquals(
            GPUWgpu4kPreparedFramePayloadRoute.ColorGlyph,
            selectWgpu4kPreparedFramePayloadRoute(listOf(GPUDrawSemanticPayload.ColorGlyph::class)),
        )
        assertEquals(
            GPUWgpu4kPreparedFramePayloadRoute.CorePrimitive,
            selectWgpu4kPreparedFramePayloadRoute(listOf(GPUDrawSemanticPayload.CorePrimitive::class)),
        )
        assertEquals(
            GPUWgpu4kPreparedFramePayloadRoute.RegisteredUniformRect,
            selectWgpu4kPreparedFramePayloadRoute(
                listOf(GPUDrawSemanticPayload.RegisteredUniformRect::class),
            ),
        )
        assertEquals(
            GPUWgpu4kPreparedFramePayloadRoute.SeparableBlurRect,
            selectWgpu4kPreparedFramePayloadRoute(
                listOf(GPUDrawSemanticPayload.SeparableBlurRect::class),
            ),
        )
    }

    @Test
    fun `dispatcher selects destination copy before the plain solid route`() {
        assertEquals(
            GPUWgpu4kPreparedFramePayloadRoute.DestinationCopySolidRect,
            selectWgpu4kPreparedFramePayloadRoute(
                semanticClasses = listOf(GPUDrawSemanticPayload.SolidRect::class),
                hasDestinationCopy = true,
            ),
        )
    }

    @Test
    fun `dispatcher refuses mixed solid and color shapes before invoking a native delegate`() {
        val route = selectWgpu4kPreparedFramePayloadRoute(
            listOf(
                GPUDrawSemanticPayload.SolidRect::class,
                GPUDrawSemanticPayload.ColorGlyph::class,
            ),
        )

        val refused = assertIs<GPUWgpu4kPreparedFramePayloadRoute.Refused>(route)
        assertEquals("unsupported.native-frame-payload.mixed-semantic-shape", refused.code)
    }

    @Test
    fun `surface cache failure precedes delegate ownership transfer`() {
        val ownership = DelegateOwnershipProbe()

        val result = materializeWgpu4kSurfaceRoute(
            format = GPUTextureFormat.BGRA8Unorm,
            acquireSurfaceBlit = { error("surface pipeline OOM") },
            materializeWithSurfaceBlit = {
                ownership.materializeAndTransfer()
                error("The delegate must not run after a cache acquisition failure")
            },
        )
        ownership.close()
        ownership.close()

        val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(result)
        assertEquals("failed.native-frame-payload.surface-blit-materialization", refused.code)
        assertEquals(
            "The typed surface blit payload could not be materialized: IllegalStateException",
            refused.message,
        )
        assertEquals(0, ownership.materializationCount)
        assertEquals(0, ownership.transferredHandleCount)
        assertEquals(0, ownership.closeCount)
        assertEquals(0, ownership.doubleCloseCount)
    }

    @Test
    fun `decoration failure disposes transferred draft ownership exactly once`() {
        val ownedHandle = CountingHandle()
        val draft = ownedDraft(ownedHandle)
        val delegate = TransferringDraftProbe(ownedHandle, draft)

        val result = materializeWgpu4kSurfaceRoute(
            format = GPUTextureFormat.BGRA8Unorm,
            acquireSurfaceBlit = {
                GPUWgpu4kSurfaceBlitCacheLease(
                    fakeNative("surface.source"),
                    fakeNative("surface.pipeline"),
                    fakeNative("surface.bind-group"),
                )
            },
            materializeWithSurfaceBlit = { delegate.materializeAndTransfer() },
            decorateMaterializedDraft = { _, transferredDraft ->
                assertTrue(transferredDraft === draft)
                error("surface decoration failed")
            },
        )
        delegate.close()
        delegate.close()
        assertTrue(draft.disposeBeforeRegistration())
        assertTrue(draft.disposeBeforeRegistration())

        val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(result)
        assertEquals("failed.native-frame-payload.surface-blit-materialization", refused.code)
        assertEquals(
            "The typed surface blit payload could not be materialized: IllegalStateException",
            refused.message,
        )
        assertEquals(1, delegate.materializationCount)
        assertEquals(1, delegate.transferredHandleCount)
        assertEquals(1, ownedHandle.closeCount)
    }

    @Test
    fun `failed draft disposal retains ownership for one successful retry`() {
        val ownedHandle = FailOnceHandle()
        val draft = ownedDraft(ownedHandle)
        val delegate = TransferringDraftProbe(ownedHandle, draft)

        val result = materializeWgpu4kSurfaceRoute(
            format = GPUTextureFormat.BGRA8Unorm,
            acquireSurfaceBlit = {
                GPUWgpu4kSurfaceBlitCacheLease(
                    fakeNative("surface.source.retry"),
                    fakeNative("surface.pipeline.retry"),
                    fakeNative("surface.bind-group.retry"),
                )
            },
            materializeWithSurfaceBlit = { delegate.materializeAndTransfer() },
            decorateMaterializedDraft = { _, _ -> error("surface decoration failed") },
        )
        delegate.close()
        delegate.close()

        val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(result)
        assertEquals("failed.native-frame-payload.surface-blit-materialization", refused.code)
        val retainedDraft = assertNotNull(refused.retainedDraft)
        assertSame(draft, retainedDraft)
        assertEquals(1, ownedHandle.closeAttempts)
        assertEquals(0, ownedHandle.successfulCloses)
        val quarantine = GPURuntimeResourceAdapter()
        assertTrue(quarantine.quarantinePreparedNativeFrameDraft(retainedDraft))
        assertEquals(1, quarantine.quarantinedPreparedNativeFramePayloadCount)
        quarantine.close()
        assertEquals(0, quarantine.quarantinedPreparedNativeFramePayloadCount)
        quarantine.close()
        assertEquals(2, ownedHandle.closeAttempts)
        assertEquals(1, ownedHandle.successfulCloses)
    }

    @Test
    fun `common surface decorator restores full payload identity and exact final blit`() {
        val generation = GPUDeviceGenerationID(7)
        val copyScope = scope(
            index = 0,
            kind = GPUEncoderOperationKind.Copy,
            keys = listOf(
                key(GPUPreparedNativeOperandRole.CopySource, GPUPreparedNativeOperandKind.Texture, "copy.source"),
                key(GPUPreparedNativeOperandRole.CopyDestination, GPUPreparedNativeOperandKind.Texture, "copy.target"),
            ),
        )
        val surfaceScope = scope(
            index = 2,
            kind = GPUEncoderOperationKind.SurfaceBlit,
            keys = listOf(
                key(GPUPreparedNativeOperandRole.SurfaceSource, GPUPreparedNativeOperandKind.TextureView, "surface.source"),
                key(GPUPreparedNativeOperandRole.SurfaceTarget, GPUPreparedNativeOperandKind.TextureView, "surface.target"),
                key(GPUPreparedNativeOperandRole.SurfacePipeline, GPUPreparedNativeOperandKind.RenderPipeline, "surface.pipeline"),
                key(GPUPreparedNativeOperandRole.SurfaceBindGroup, GPUPreparedNativeOperandKind.BindGroup, "surface.bind-group"),
            ),
        )
        val fullEncoderPlan = GPUCommandEncoderPlan.ordered(
            planId = "window.encoder",
            contextIdentity = "target.scene",
            deviceGeneration = generation,
            targetGeneration = 3,
            scopes = listOf(copyScope, surfaceScope),
        )
        val copy = GPUPreparedNativeScopeOperand.Copy(
            sourceStepIndex = 0,
            operationKind = GPUEncoderOperationKind.Copy,
            source = GPUPreparedNativeTextureOperand(fakeNative("copy.source"), generation),
            destination = GPUPreparedNativeTextureOperand(fakeNative("copy.target"), generation),
            textureLayout = GPUPreparedNativeTextureCopyLayout(0, 0, 0, 0, 4, 4),
        )
        val decoratedOwnedHandle = CountingHandle()
        val reusableDraft = GPUPreparedNativeFrameDraft(
            GPUPreparedNativeFramePayload(
                identity = GPUPreparedNativeFrameIdentity(
                    frameId = GPUFrameID(11),
                    contextIdentity = fullEncoderPlan.contextIdentity,
                    encoderPlanId = fullEncoderPlan.planId,
                    deviceGeneration = generation,
                    targetGeneration = fullEncoderPlan.targetGeneration,
                    scopes = listOf(
                        GPUPreparedNativeScopeKey(
                            copyScope.sourceStepIndex,
                            copyScope.operationKind,
                            copyScope.resourceGenerationLabels,
                            copyScope.nativeOperandKeys,
                        ),
                    ),
                ),
                scopeOperands = listOf(copy),
                scopeOperandKeys = listOf(copyScope.nativeOperandKeys),
                auxiliaryOwnedHandles = listOf(
                    GPUPreparedNativeAuxiliaryHandle(
                        decoratedOwnedHandle,
                        GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                    ),
                ),
            ),
        )
        val output = GPUSurfaceOutputRef("surface.window")
        val blit = GPUPreparedNativeScopeOperand.SurfaceBlit(
            sourceStepIndex = surfaceScope.sourceStepIndex,
            source = GPUPreparedNativeTextureViewOperand(fakeNative("scene.view"), generation),
            output = output,
            pipeline = GPUPreparedNativeRenderPipelineOperand(fakeNative("surface.pipeline"), generation),
            bindGroup = GPUPreparedNativeBindGroupOperand(fakeNative("surface.bind-group"), generation),
        )

        val decorated = decorateWgpu4kSurfaceBlitDraft(fullEncoderPlan, reusableDraft, blit)

        assertTrue(reusableDraft.disposeBeforeRegistration())
        assertEquals(0, decoratedOwnedHandle.closeCount)

        assertEquals(fullEncoderPlan.scopes.map { it.sourceStepIndex }, decorated.payload.identity.scopes.map { it.sourceStepIndex })
        assertEquals(listOf(GPUEncoderOperationKind.Copy, GPUEncoderOperationKind.SurfaceBlit), decorated.payload.scopeOperands.map { it.operationKind })
        assertTrue(decorated.payload.scopeOperands.last() === blit)
        val acquired = GPUAcquiredSurfaceOutput(output, generation, 19, "surface.acquired")
        val exactTarget = fakeNative<GPUTextureView>("surface.target")
        val binding = bindWgpu4kLateSurface(
            draft = decorated,
            acquiredSurface = acquired,
            resolver = GPUAcquiredSurfaceNativeTargetResolver { candidate ->
                candidate.takeIf { it === acquired }?.let {
                    GPUPreparedNativeTextureViewOperand(exactTarget, generation)
                }
            },
        )
        val bound = assertIs<GPUPreparedNativeFrameLateSurfaceBinding.Bound>(binding)
        assertEquals(output, bound.output)
        assertTrue(bound.target.view === exactTarget)
        assertTrue(decorated.disposeBeforeRegistration())
        assertEquals(1, decoratedOwnedHandle.closeCount)
    }

    private fun scope(
        index: Int,
        kind: GPUEncoderOperationKind,
        keys: List<GPUPreparedNativeOperandKey>,
    ) = GPUCommandEncoderScopePlan(
        sourceStepIndex = index,
        operationKind = kind,
        sourceTaskIds = listOf(GPUTaskID("task.$index")),
        facadeOperationClasses = listOf("encode"),
        targetGeneration = 3,
        resourceGenerationLabels = if (kind == GPUEncoderOperationKind.SurfaceBlit) {
            listOf("GPUFrameTargetRef:target.scene@3")
        } else {
            listOf("copy.source@1", "copy.target@2")
        },
    ).attachNativeOperandKeys(keys)

    private fun key(
        role: GPUPreparedNativeOperandRole,
        kind: GPUPreparedNativeOperandKind,
        binding: String,
    ) = GPUPreparedNativeOperandKey(
        role = role,
        kind = kind,
        bindingKey = gpuPreparedNativeBindingKey(binding),
        ownership = GPUPreparedNativeOperandOwnership.Borrowed,
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

    private fun ownedDraft(handle: AutoCloseable) = GPUPreparedNativeFrameDraft(
        GPUPreparedNativeFramePayload(
            identity = GPUPreparedNativeFrameIdentity(
                frameId = GPUFrameID(12),
                contextIdentity = "target.scene",
                encoderPlanId = "window.encoder.failure",
                deviceGeneration = GPUDeviceGenerationID(7),
                targetGeneration = 3,
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

    private class CountingHandle : AutoCloseable {
        var closeCount = 0
            private set

        override fun close() {
            closeCount += 1
        }
    }

    private class FailOnceHandle : AutoCloseable {
        var closeAttempts = 0
            private set
        var successfulCloses = 0
            private set

        override fun close() {
            closeAttempts += 1
            if (closeAttempts == 1) error("first close failed")
            successfulCloses += 1
        }
    }

    private class TransferringDraftProbe(
        private var pendingHandle: AutoCloseable?,
        private val draft: GPUPreparedNativeFrameDraft,
    ) : AutoCloseable {
        var materializationCount = 0
            private set
        var transferredHandleCount = 0
            private set

        fun materializeAndTransfer(): GPUPreparedNativeFramePayloadMaterialization {
            materializationCount += 1
            transferredHandleCount += 1
            pendingHandle = null
            return GPUPreparedNativeFramePayloadMaterialization.Materialized(draft)
        }

        override fun close() {
            pendingHandle?.close()
            pendingHandle = null
        }
    }

    private class DelegateOwnershipProbe : AutoCloseable {
        var materializationCount = 0
            private set
        var transferredHandleCount = 0
            private set
        var closeCount = 0
            private set
        var doubleCloseCount = 0
            private set
        private var pendingHandle = false

        fun materializeAndTransfer() {
            materializationCount += 1
            pendingHandle = true
            transferredHandleCount += 1
            pendingHandle = false
        }

        override fun close() {
            if (!pendingHandle) return
            if (closeCount == 0) closeCount += 1 else doubleCloseCount += 1
            pendingHandle = false
        }
    }
}
