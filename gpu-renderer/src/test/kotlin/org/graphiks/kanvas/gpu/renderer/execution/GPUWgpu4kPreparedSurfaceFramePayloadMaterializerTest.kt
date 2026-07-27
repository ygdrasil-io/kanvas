package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUSampler
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureView
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUSurfaceOutputRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUImageBindingRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUImageFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUSamplerDescriptor

class GPUWgpu4kPreparedSurfaceFramePayloadMaterializerTest {
    @Test
    fun `mixed materializer composes both lattices in the exact full encoder order`() {
        listOf(
            PreparedSurfaceFixtureShape.CoreImageCore,
            PreparedSurfaceFixtureShape.ImageCoreImage,
        ).forEach { shape ->
            val fixture = fixture(shape)
            try {
                val result = fixture.materialize()
                val materialized = assertIs<
                    GPUPreparedNativeFramePayloadMaterialization.Materialized
                    >(result, result.toString())
                val payload = materialized.draft.payload

                assertEquals(
                    fixture.input.encoderPlan.scopes.map { it.sourceStepIndex },
                    payload.scopeOperands.map { it.sourceStepIndex },
                    shape.name,
                )
                assertEquals(
                    fixture.input.encoderPlan.scopes.map { it.operationKind },
                    payload.scopeOperands.map { it.operationKind },
                    shape.name,
                )
                assertEquals(
                    fixture.input.encoderPlan.scopes.map { it.nativeOperandKeys },
                    payload.scopeOperandKeys,
                    shape.name,
                )
                assertEquals(
                    fixture.input.encoderPlan.scopes.map { scope ->
                        GPUPreparedNativeScopeKey(
                            scope.sourceStepIndex,
                            scope.operationKind,
                            scope.resourceGenerationLabels,
                            scope.nativeOperandKeys,
                        )
                    },
                    payload.identity.scopes,
                    shape.name,
                )
                assertTrue(
                    payload.scopeOperands.none {
                        it is GPUPreparedNativeScopeOperand.PreparedImageRenderRun
                    },
                    "a production payload must contain only target-bound Render image operands",
                )

                val uploads = payload.scopeOperands.filterIsInstance<
                    GPUPreparedNativeScopeOperand.TextureUpload
                    >()
                val imageConsumers = payload.scopeOperands
                    .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
                    .filter { render ->
                        render.semanticPayloads.all {
                            it is GPUDrawSemanticPayload.SampledImage
                        }
                    }
                assertEquals(1, uploads.size, "the shared image must be uploaded once")
                assertEquals(
                    if (shape == PreparedSurfaceFixtureShape.ImageCoreImage) 2 else 1,
                    imageConsumers.size,
                )
                assertTrue(imageConsumers.all {
                    uploads.single().sourceStepIndex < it.sourceStepIndex
                })

                assertEquals(
                    Triple(1L, 0L, 1L),
                    fixture.targetLifecycle.snapshot(),
                    "one canonical target must be created once and borrowed once",
                )
                assertTrue(materialized.draft.disposeBeforeRegistration())
            } finally {
                fixture.close()
            }
        }
    }

    @Test
    fun `real dispatcher materializes and encodes core image core without compatibility detour`() {
        val input = capturedPreparedSurfaceInputs(
            shape = PreparedSurfaceFixtureShape.CoreImageCore,
            includeReadback = true,
        )
        val native = GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy()
        val targetLifecycle = GPUWgpu4kPreparedSceneTargetLifecycle()
        val setup = GPUPreparedSceneSetupTransaction()
        val target = GPUWgpu4kPreparedSceneTarget.create(
            device = native.device,
            width = 16,
            height = 16,
            format = GPUTextureFormat.RGBA8UnormSrgb,
            deviceGeneration = input.generationSeal.deviceGeneration,
            targetGeneration = input.generationSeal.targetGeneration,
            lifecycle = targetLifecycle,
            setupTransaction = setup,
        )
        setup.commit()
        val solidRectCache = GPUWgpu4kSolidRectSessionCache(native.device)
        val coreCache = GPUWgpu4kCorePrimitiveSessionCache(
            native.device,
            input.generationSeal.deviceGeneration,
        )
        val colorGlyphCache = GPUWgpu4kColorGlyphSessionCache(native.device, native.queue)
        val registeredUniformRectCache = GPUWgpu4kRegisteredUniformRectSessionCache(native.device)
        val blurCache = GPUWgpu4kSeparableBlurRectSessionCache(native.device)
        val destinationCopyCache = GPUWgpu4kDestinationCopySessionCache(native.device)
        val imageCache = GPUWgpu4kPreparedImageSessionCache(
            native.device,
            input.generationSeal.deviceGeneration,
        )
        val surfaceCache = GPUWgpu4kSurfaceBlitSessionCache(native.device, target)
        val mixed = GPUWgpu4kPreparedSurfaceFramePayloadMaterializer(
            device = native.device,
            queue = native.queue,
            preparedSceneTarget = target,
            corePrimitiveCache = coreCache,
            preparedImageCache = imageCache,
            preparedImageHandleFactory = GPUWgpu4kPreparedImageNativeHandleFactory(native.device),
            surfaceBlitCache = surfaceCache,
            corePrimitiveLimits = LIMITS,
        )
        val dispatcher = GPUWgpu4kFramePayloadMaterializerDispatcher(
            device = native.device,
            queue = native.queue,
            preparedSceneTarget = target,
            solidRectCache = solidRectCache,
            corePrimitiveCache = coreCache,
            colorGlyphCache = colorGlyphCache,
            registeredUniformRectCache = registeredUniformRectCache,
            separableBlurRectCache = blurCache,
            destinationCopyCache = destinationCopyCache,
            surfaceBlitCache = surfaceCache,
            corePrimitiveLimits = LIMITS,
            preparedSurfaceMixedMaterializer = mixed,
        )
        val backend = GPUWgpu4kFrameEncodingBackend(
            deviceGeneration = input.generationSeal.deviceGeneration,
            device = native.device,
            queue = native.queue,
            canonicalSceneTargetView = target.view,
        )
        val registry = GPURuntimeResourceAdapter()
        val encodingWitness = GPUFrameCoreTestFixture.preparedFrame()
        var draft: GPUPreparedNativeFrameDraft? = null
        var ownership: GPUPreparedNativeFrameOwnership? = null
        try {
            val materialized = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
                dispatcher.materializeReusable(
                    input.framePlan,
                    input.encoderPlan,
                    input.resources,
                    input.generationSeal,
                ),
            )
            draft = materialized.draft
            val payload = materialized.draft.payload
            val imageUploadStep = input.framePlan.steps
                .filterIsInstance<GPUFrameStep.UploadResourceStep>()
                .single { it.imageResourcePlan != null }
            val imageResourcePlan = requireNotNull(imageUploadStep.imageResourcePlan)
            val textureUpload = assertIs<GPUPreparedNativeScopeOperand.TextureUpload>(
                payload.scopeOperands.single {
                    it is GPUPreparedNativeScopeOperand.TextureUpload
                },
            )

            assertTrue(
                native.writeBufferCalls.any {
                    it.bufferLabel == "Kanvas.frame.preparedImage.uniforms"
                },
                "prepared-image uniforms must be uploaded during materialization",
            )
            val writeBufferCountAfterMaterialization = native.writeBufferCalls.size
            assertEquals(0, native.writeTextureCalls)
            assertEquals(imageResourcePlan.uploadLayout, textureUpload.layout)
            kotlin.test.assertContentEquals(
                imageResourcePlan.uploadLayout.bytesForUpload(),
                textureUpload.data.bytes(),
            )
            assertEquals(
                gpuPreparedNativeBindingKey(
                    "prepared-image-upload-data:${imageUploadStep.staging.value}",
                ),
                textureUpload.data.key.bindingKey,
            )
            assertTrue(
                payload.scopeOperands.none {
                    it is GPUPreparedNativeScopeOperand.PreparedImageRenderRun
                },
            )
            assertTrue(
                input.framePlan.steps.none {
                    it is GPUFrameStep.CopyDestinationStep ||
                        it is GPUFrameStep.CopyAsDrawMaterializationStep
                },
                "the main route must not create a compatibility copy step",
            )
            assertTrue(
                input.encoderPlan.scopes.none {
                    it.operationKind == GPUEncoderOperationKind.CopyDestination ||
                        it.operationKind == GPUEncoderOperationKind.CopyAsDraw
                },
                "the main route must not encode a compatibility copy operation",
            )
            assertEquals(input.encoderPlan.scopes.size, payload.scopeOperands.size)
            assertEquals(
                input.encoderPlan.scopes.map { it.nativeOperandKeys },
                payload.scopeOperandKeys,
            )
            val registeredOwnership = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
                registry.registerPreparedNativeFrameDraft(materialized.draft),
            ).ownership
            ownership = registeredOwnership
            assertIs<GPUPreparedNativeFrameBindingResult.Ready>(
                registeredOwnership.bindLateSurface(
                    acquiredSurface = null,
                    binding = GPUPreparedNativeFrameLateSurfaceBinding.NotRequired,
                ),
            )
            assertIs<GPUPreparedNativeFrameConsumption.Consumed>(
                registeredOwnership.consume(payload.identity),
            )

            val encoder = backend.createCommandEncoder("prepared-surface-e2e")
            input.encoderPlan.scopes.zip(payload.scopeOperands).forEach { (scope, operand) ->
                assertEquals(scope.sourceStepIndex, operand.sourceStepIndex)
                assertEquals(scope.operationKind, operand.operationKind)
                encoder.encode(
                    scope,
                    encodingWitness,
                    GPUFrameCoreTestFixture.sceneTarget(
                        targetGeneration = input.generationSeal.targetGeneration,
                    ),
                    operand,
                )
            }
            assertEquals(writeBufferCountAfterMaterialization, native.writeBufferCalls.size)
            assertEquals(1, native.writeTextureCalls)
            assertEquals(listOf("core", "image", "core"), native.renderPipelineKinds)
            assertEquals(1, native.readbackCopyCalls)

            val commandBuffer = encoder.finish()
            backend.submit(commandBuffer)
            assertTrue(
                native.events.indexOf("encoder.finish") <
                    native.events.indexOf("queue.submit"),
            )
            assertEquals(1, native.closeAttempts("frame.commandEncoder"))
            assertEquals(1, native.closeAttempts("frame.commandBuffer"))

            assertTrue(registeredOwnership.markSubmitted())
            assertTrue(registeredOwnership.releaseAfterCompletion())
            assertTrue(registeredOwnership.claimOutputMapping())
            assertTrue(registeredOwnership.releaseOutputAfterReadback())
            val closeCountsAfterCompletion = native.closeCounts.toMap()
            assertFalse(registeredOwnership.releaseAfterCompletion())
            assertFalse(registeredOwnership.releaseOutputAfterReadback())
            assertEquals(closeCountsAfterCompletion, native.closeCounts)
            assertTrue(native.closeCounts.values.all { it == 1 })
        } finally {
            ownership?.rollback() ?: draft?.disposeBeforeRegistration()
            if (encodingWitness.claimForRollback()) encodingWitness.rollback.execute()
            runCatching { registry.close() }
            runCatching { backend.close() }
            runCatching { dispatcher.close() }
            runCatching { surfaceCache.close() }
            runCatching { imageCache.close() }
            runCatching { destinationCopyCache.close() }
            runCatching { blurCache.close() }
            runCatching { registeredUniformRectCache.close() }
            runCatching { colorGlyphCache.close() }
            runCatching { coreCache.close() }
            runCatching { solidRectCache.close() }
            runCatching { target.close() }
        }
    }

    @Test
    fun `global refusal creates no native handle and retains no draft or owner`() {
        val fixture = fixture(PreparedSurfaceFixtureShape.CoreImageCore)
        try {
            val nativeEventCount = fixture.native.events.size
            val imageHandleCount = fixture.imageFactory.handleCreates
            val staleSeal = GPUPreparedGenerationSeal(
                deviceGeneration = GPUDeviceGenerationID(
                    fixture.input.generationSeal.deviceGeneration.value + 1L,
                ),
                targetGeneration = fixture.input.generationSeal.targetGeneration,
                resourceGenerations = fixture.input.generationSeal.resourceGenerations,
                capabilitySealHash = fixture.input.generationSeal.capabilitySealHash,
            )

            val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(
                fixture.materializer.materializeReusable(
                    fixture.input.framePlan,
                    fixture.input.encoderPlan,
                    fixture.input.resources,
                    staleSeal,
                ),
            )

            assertEquals(nativeEventCount, fixture.native.events.size)
            assertEquals(imageHandleCount, fixture.imageFactory.handleCreates)
            assertEquals(null, refused.retainedDraft)
            assertEquals(null, refused.retainedPreRegistrationLedger)
            assertEquals(null, refused.retainedCloseOwner)
            assertEquals(Triple(1L, 0L, 0L), fixture.targetLifecycle.snapshot())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `permuted image bindings are refused before target borrow or native allocation`() {
        val fixture = fixture(PreparedSurfaceFixtureShape.ImageCoreImage)
        try {
            val malformedInput = fixture.input.copy(
                framePlan = fixture.input.framePlan.withReversedPreparedImageBindings(),
            )
            val nativeEventCount = fixture.native.events.size
            val imageHandleCount = fixture.imageFactory.handleCreates

            val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(
                fixture.materializer.materializeReusable(
                    malformedInput.framePlan,
                    malformedInput.encoderPlan,
                    malformedInput.resources,
                    malformedInput.generationSeal,
                ),
            )

            assertEquals(GPUPreparedImageRefusalCodes.NATIVE_BINDING, refused.code)
            assertEquals(nativeEventCount, fixture.native.events.size)
            assertEquals(imageHandleCount, fixture.imageFactory.handleCreates)
            assertEquals(null, refused.retainedDraft)
            assertEquals(null, refused.retainedPreRegistrationLedger)
            assertEquals(null, refused.retainedCloseOwner)
            assertEquals(Triple(1L, 0L, 0L), fixture.targetLifecycle.snapshot())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `one successful materialization creates one draft with one readback and one close ledger`() {
        val fixture = fixture(PreparedSurfaceFixtureShape.ImageCoreImage)
        try {
            val result = fixture.materialize()
            val materialized = assertIs<
                GPUPreparedNativeFramePayloadMaterialization.Materialized
                >(result, result.toString())
            val draft = materialized.draft

            assertEquals(
                1,
                draft.payload.scopeOperands
                    .filterIsInstance<GPUPreparedNativeScopeOperand.Readback>()
                    .size,
            )
            assertEquals(
                draft.pendingOwnedHandlesSnapshot().distinctBy(System::identityHashCode).size,
                draft.pendingOwnedHandlesSnapshot().size,
            )

            assertTrue(draft.disposeBeforeRegistration())
            assertTrue(draft.disposeBeforeRegistration())
            assertTrue(draft.pendingOwnedHandlesSnapshot().isEmpty())
            assertTrue(fixture.imageFactory.closeCounts.values.all { it == 1 })
            assertTrue(fixture.native.closeCounts.values.all { it <= 1 })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `surface composition appends one late bound surface scope to the same draft`() {
        val fixture = fixture(
            shape = PreparedSurfaceFixtureShape.CoreImageCore,
            includeSurface = true,
        )
        try {
            val result = fixture.materialize()
            val materialized = assertIs<
                GPUPreparedNativeFramePayloadMaterialization.Materialized
                >(result, result.toString())
            val draft = materialized.draft
            val output = fixture.input.framePlan.steps
                .filterIsInstance<GPUFrameStep.AcquireSurfaceOutput>()
                .single()
                .descriptor
                .output
            val acquired = GPUAcquiredSurfaceOutput(
                output = output,
                deviceGeneration = fixture.input.generationSeal.deviceGeneration,
                targetGeneration = fixture.input.generationSeal.targetGeneration,
                evidenceLabel = "prepared-surface-materializer-test",
            )
            val binding = assertIs<GPUPreparedNativeFrameLateSurfaceBinding.Bound>(
                fixture.materializer.bindLateSurface(draft, acquired),
            )

            assertEquals(
                fixture.input.encoderPlan.scopes.map { it.sourceStepIndex },
                draft.payload.scopeOperands.map { it.sourceStepIndex },
            )
            assertEquals(
                fixture.input.encoderPlan.scopes.map { it.operationKind },
                draft.payload.scopeOperands.map { it.operationKind },
            )
            assertEquals(
                fixture.input.encoderPlan.scopes.map { it.nativeOperandKeys },
                draft.payload.scopeOperandKeys,
            )
            assertEquals(
                1,
                draft.payload.scopeOperands
                    .filterIsInstance<GPUPreparedNativeScopeOperand.Readback>()
                    .size,
            )
            assertEquals(
                1,
                draft.payload.scopeOperands
                    .filterIsInstance<GPUPreparedNativeScopeOperand.SurfaceBlit>()
                    .size,
            )
            assertTrue(draft.payload.bindLateSurface(acquired, binding))
            assertFalse(draft.payload.bindLateSurface(acquired, binding))
            assertTrue(draft.payload.lateSurfaceReady)
            assertEquals(1L, fixture.targetLifecycle.snapshot().first)
            assertTrue(draft.disposeBeforeRegistration())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `mixed late surface refusals keep stable codes and allocate no native state`() {
        data class Case(
            val label: String,
            val expectedCode: String,
            val surfaceTargetAvailable: Boolean = true,
            val surfaceTargetGenerationDelta: Long = 0L,
            val acquired: (Fixture, GPUSurfaceOutputRef) -> GPUAcquiredSurfaceOutput?,
        )
        val cases = listOf(
            Case(
                label = "missing acquired output",
                expectedCode = "unsupported.native-frame-payload.surface-output-missing",
                acquired = { _, _ -> null },
            ),
            Case(
                label = "mismatched acquired output",
                expectedCode = "stale.native-frame-payload.surface-output-mismatch",
                acquired = { fixture, _ ->
                    fixture.acquiredSurface(GPUSurfaceOutputRef("surface.other"))
                },
            ),
            Case(
                label = "native target unavailable",
                expectedCode = "unsupported.native-frame-payload.surface-target-unavailable",
                surfaceTargetAvailable = false,
                acquired = { fixture, output -> fixture.acquiredSurface(output) },
            ),
            Case(
                label = "stale native target generation",
                expectedCode = "stale.native-frame-payload.surface-target-generation",
                surfaceTargetGenerationDelta = 1L,
                acquired = { fixture, output -> fixture.acquiredSurface(output) },
            ),
        )

        cases.forEach { case ->
            val fixture = fixture(
                shape = PreparedSurfaceFixtureShape.CoreImageCore,
                includeSurface = true,
                surfaceTargetAvailable = case.surfaceTargetAvailable,
                surfaceTargetGenerationDelta = case.surfaceTargetGenerationDelta,
            )
            try {
                val materialized = assertIs<
                    GPUPreparedNativeFramePayloadMaterialization.Materialized
                    >(fixture.materialize(), case.label)
                val draft = materialized.draft
                val output = fixture.surfaceOutput()
                val nativeEventsBefore = fixture.native.events.toList()
                val imageCreatesBefore = fixture.imageFactory.handleCreates
                val handlesBefore = draft.pendingOwnedHandlesSnapshot()

                val refused = assertIs<GPUPreparedNativeFrameLateSurfaceBinding.Refused>(
                    fixture.materializer.bindLateSurface(
                        draft,
                        case.acquired(fixture, output),
                    ),
                    case.label,
                )

                assertEquals(case.expectedCode, refused.code, case.label)
                assertEquals(nativeEventsBefore, fixture.native.events, case.label)
                assertEquals(imageCreatesBefore, fixture.imageFactory.handleCreates, case.label)
                val handlesAfter = draft.pendingOwnedHandlesSnapshot()
                assertEquals(handlesBefore.size, handlesAfter.size, case.label)
                assertTrue(
                    handlesBefore.zip(handlesAfter).all { (before, after) -> before === after },
                    case.label,
                )
                assertFalse(draft.payload.lateSurfaceReady, case.label)
                assertTrue(draft.disposeBeforeRegistration(), case.label)
            } finally {
                fixture.close()
            }
        }
    }

    @Test
    fun `duplicate mixed late surface bind is refused by the real registry without allocation`() {
        val fixture = fixture(
            shape = PreparedSurfaceFixtureShape.CoreImageCore,
            includeSurface = true,
        )
        val adapter = GPURuntimeResourceAdapter()
        try {
            val draft = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
                fixture.materialize(),
            ).draft
            val provider = GPUConcreteResourceProvider(leaseFactory = adapter)
            val boundary = adapter.bindNativeFrameBoundary(provider, fixture.materializer)
            val registration = assertIs<GPUPreparedNativeFrameRegistration.Registered>(
                boundary.register(draft),
            )
            val acquired = fixture.acquiredSurface(fixture.surfaceOutput())

            assertIs<GPUPreparedNativeFrameBindingResult.Ready>(
                boundary.bindLateSurface(registration.ownership, draft, acquired),
            )
            val nativeEventsBeforeDuplicate = fixture.native.events.toList()
            val imageCreatesBeforeDuplicate = fixture.imageFactory.handleCreates

            val duplicate = assertIs<GPUPreparedNativeFrameBindingResult.Refused>(
                boundary.bindLateSurface(registration.ownership, draft, acquired),
            )

            assertEquals("unsupported.native-frame-payload.draft-state", duplicate.code)
            assertEquals(nativeEventsBeforeDuplicate, fixture.native.events)
            assertEquals(imageCreatesBeforeDuplicate, fixture.imageFactory.handleCreates)
            assertTrue(registration.ownership.rollback())
        } finally {
            adapter.close()
            fixture.close()
        }
    }

    @Test
    fun `exception after global preflight closes each frame owned image handle once`() {
        val fixture = fixture(
            shape = PreparedSurfaceFixtureShape.ImageCoreImage,
            failImageBindGroup = true,
        )
        try {
            val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(
                fixture.materialize(),
            )

            assertTrue(fixture.imageFactory.handleCreates > 0)
            assertTrue(fixture.imageFactory.closeCounts.isNotEmpty())
            assertTrue(fixture.imageFactory.closeCounts.values.all { it == 1 })
            assertEquals(null, refused.retainedDraft)
            assertEquals(null, refused.retainedPreRegistrationLedger)
            assertEquals(null, refused.retainedCloseOwner)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `image allocation rollback retains only the handle whose first close failed`() {
        val fixture = fixture(
            shape = PreparedSurfaceFixtureShape.ImageCoreImage,
            failImageBindGroup = true,
            failFirstImageClose = true,
        )
        try {
            val refused = assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(
                fixture.materialize(),
            )
            val retainedOwner = requireNotNull(refused.retainedCloseOwner)
            val closeCountsAfterRefusal = fixture.imageFactory.closeCounts.toMap()
            val failedHandle = requireNotNull(fixture.imageFactory.failedCloseLabel)
            assertTrue(closeCountsAfterRefusal.values.all { it == 1 })
            assertTrue(refused.message.contains("Rollback retained native ownership"))

            retainedOwner.close()

            assertEquals(2, fixture.imageFactory.closeCounts.getValue(failedHandle))
            assertTrue(
                fixture.imageFactory.closeCounts
                    .filterKeys { it != failedHandle }
                    .values
                    .all { it == 1 },
                "handles closed successfully during rollback must not be closed twice",
            )
        } finally {
            fixture.close()
        }
    }

    private fun fixture(
        shape: PreparedSurfaceFixtureShape,
        includeSurface: Boolean = false,
        failImageBindGroup: Boolean = false,
        failFirstImageClose: Boolean = false,
        surfaceTargetAvailable: Boolean = true,
        surfaceTargetGenerationDelta: Long = 0L,
    ): Fixture {
        val input = capturedPreparedSurfaceInputs(
            shape = shape,
            includeReadback = true,
            includeSurface = includeSurface,
        )
        val native = GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy()
        val targetLifecycle = GPUWgpu4kPreparedSceneTargetLifecycle()
        val setup = GPUPreparedSceneSetupTransaction()
        val target = GPUWgpu4kPreparedSceneTarget.create(
            device = native.device,
            width = 16,
            height = 16,
            format = GPUTextureFormat.RGBA8UnormSrgb,
            deviceGeneration = input.generationSeal.deviceGeneration,
            targetGeneration = input.generationSeal.targetGeneration,
            lifecycle = targetLifecycle,
            setupTransaction = setup,
        )
        setup.commit()
        val coreCache = GPUWgpu4kCorePrimitiveSessionCache(
            native.device,
            input.generationSeal.deviceGeneration,
        )
        val imageCache = GPUWgpu4kPreparedImageSessionCache(
            native.device,
            input.generationSeal.deviceGeneration,
        )
        val imageFactory = CompositePreparedImageHandleFactory(
            failFirstClose = failFirstImageClose,
        )
        val selectedImageFactory = if (failImageBindGroup) {
            FailOnFirstBindGroupPreparedImageHandleFactory(imageFactory)
        } else {
            imageFactory
        }
        val surfaceCache = GPUWgpu4kSurfaceBlitSessionCache(native.device, target)
        val lateSurfaceTarget = fakeNativeHandle<GPUTextureView>("late-surface-target")
        val materializer = GPUWgpu4kPreparedSurfaceFramePayloadMaterializer(
            device = native.device,
            queue = native.queue,
            preparedSceneTarget = target,
            corePrimitiveCache = coreCache,
            preparedImageCache = imageCache,
            preparedImageHandleFactory = selectedImageFactory,
            surfaceBlitCache = surfaceCache,
            surfaceTargetResolver = GPUAcquiredSurfaceNativeTargetResolver {
                if (!surfaceTargetAvailable) {
                    null
                } else {
                    GPUPreparedNativeTextureViewOperand(
                        lateSurfaceTarget,
                        GPUDeviceGenerationID(
                            input.generationSeal.deviceGeneration.value +
                                surfaceTargetGenerationDelta,
                        ),
                        GPUPreparedNativeOperandOwnership.Borrowed,
                    )
                }
            },
            corePrimitiveLimits = LIMITS,
        )
        return Fixture(
            input,
            native,
            targetLifecycle,
            target,
            coreCache,
            imageCache,
            surfaceCache,
            imageFactory,
            materializer,
        )
    }

    private data class Fixture(
        val input: CapturedPreparedSurfaceInputs,
        val native: GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy,
        val targetLifecycle: GPUWgpu4kPreparedSceneTargetLifecycle,
        val target: GPUWgpu4kPreparedSceneTarget,
        val coreCache: GPUWgpu4kCorePrimitiveSessionCache,
        val imageCache: GPUWgpu4kPreparedImageSessionCache,
        val surfaceCache: GPUWgpu4kSurfaceBlitSessionCache,
        val imageFactory: CompositePreparedImageHandleFactory,
        val materializer: GPUWgpu4kPreparedSurfaceFramePayloadMaterializer,
    ) {
        fun materialize(): GPUPreparedNativeFramePayloadMaterialization =
            materializer.materializeReusable(
                input.framePlan,
                input.encoderPlan,
                input.resources,
                input.generationSeal,
            )

        fun surfaceOutput(): GPUSurfaceOutputRef =
            input.framePlan.steps
                .filterIsInstance<GPUFrameStep.AcquireSurfaceOutput>()
                .single()
                .descriptor
                .output

        fun acquiredSurface(output: GPUSurfaceOutputRef): GPUAcquiredSurfaceOutput =
            GPUAcquiredSurfaceOutput(
                output = output,
                deviceGeneration = input.generationSeal.deviceGeneration,
                targetGeneration = input.generationSeal.targetGeneration,
                evidenceLabel = "prepared-surface-materializer-test",
            )

        fun close() {
            runCatching { materializer.close() }
            runCatching { surfaceCache.close() }
            runCatching { imageCache.close() }
            runCatching { coreCache.close() }
            runCatching { target.close() }
        }
    }

    private class FailOnFirstBindGroupPreparedImageHandleFactory(
        private val delegate: CompositePreparedImageHandleFactory,
    ) : GPUPreparedImageNativeHandleFactory {
        override fun createTexture(request: GPUImageFrameResourcePlan): GPUTexture =
            delegate.createTexture(request)

        override fun createTextureView(
            texture: GPUTexture,
            request: GPUImageFrameResourcePlan,
        ): GPUTextureView = delegate.createTextureView(texture, request)

        override fun createSampler(descriptor: GPUSamplerDescriptor): GPUSampler =
            delegate.createSampler(descriptor)

        override fun createUniformBuffer(size: Long): GPUBuffer =
            delegate.createUniformBuffer(size)

        override fun createBindGroup(
            bindGroupLayout: GPUBindGroupLayout,
            request: GPUImageBindingRequest,
            uniformBuffer: GPUBuffer,
            textureView: GPUTextureView,
            sampler: GPUSampler,
        ): GPUBindGroup = error("injected prepared-image bind-group failure")
    }

    private class CompositePreparedImageHandleFactory(
        private val failFirstClose: Boolean,
    ) : GPUPreparedImageNativeHandleFactory {
        val closeCounts = linkedMapOf<String, Int>()
        var handleCreates = 0
        var failedCloseLabel: String? = null
        private var nextOrdinal = 0
        private var closeFailurePending = failFirstClose

        override fun createTexture(request: GPUImageFrameResourcePlan): GPUTexture =
            handle("texture")

        override fun createTextureView(
            texture: GPUTexture,
            request: GPUImageFrameResourcePlan,
        ): GPUTextureView = handle("view")

        override fun createSampler(descriptor: GPUSamplerDescriptor): GPUSampler =
            handle("sampler")

        override fun createUniformBuffer(size: Long): GPUBuffer = handle("uniform")

        override fun createBindGroup(
            bindGroupLayout: GPUBindGroupLayout,
            request: GPUImageBindingRequest,
            uniformBuffer: GPUBuffer,
            textureView: GPUTextureView,
            sampler: GPUSampler,
        ): GPUBindGroup = handle("bind-group")

        @Suppress("UNCHECKED_CAST")
        private inline fun <reified T> handle(prefix: String): T {
            handleCreates += 1
            val label = "$prefix.${nextOrdinal++}"
            return Proxy.newProxyInstance(
                T::class.java.classLoader,
                arrayOf(T::class.java),
            ) { proxy, method, arguments ->
                when (method.name) {
                    "equals" -> proxy === arguments?.singleOrNull()
                    "hashCode" -> System.identityHashCode(proxy)
                    "close" -> {
                        closeCounts[label] = closeCounts.getOrDefault(label, 0) + 1
                        if (closeFailurePending) {
                            closeFailurePending = false
                            failedCloseLabel = label
                            error("injected first image close failure")
                        }
                    }
                    "setLabel" -> Unit
                    "getLabel", "toString" -> label
                    else -> null
                }
            } as T
        }
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> fakeNativeHandle(label: String): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) {
                proxy, method, arguments ->
            when (method.name) {
                "equals" -> proxy === arguments?.singleOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "close", "setLabel" -> Unit
                "getLabel", "toString" -> label
                else -> null
            }
        } as T

    private companion object {
        val LIMITS = GPULimits(
            maxTextureDimension2D = 8192,
            copyBytesPerRowAlignment = 256,
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = 1L shl 30,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        )
    }
}

private fun GPUFramePlan.withReversedPreparedImageBindings(): GPUFramePlan {
    var reversedBindingCount = 0
    val updatedSteps = steps.map { step ->
        if (step !is GPUFrameStep.UploadResourceStep || step.imageResourcePlan == null) {
            step
        } else {
            val resourcePlan = step.imageResourcePlan
            reversedBindingCount += resourcePlan.bindingRequests.size
            GPUFrameStep.UploadResourceStep(
                staging = step.staging,
                destination = step.destination,
                layout = step.layout,
                sourceTaskIds = step.sourceTaskIds,
                imageResourcePlan = resourcePlan.copy(
                    bindingRequests = resourcePlan.bindingRequests.reversed(),
                ),
            )
        }
    }
    require(reversedBindingCount >= 2) {
        "The regression fixture must contain at least two image bindings"
    }
    return GPUFramePlan(
        frameId = frameId,
        capabilitySeal = capabilitySeal,
        recordingSeals = recordingSeals,
        steps = updatedSteps,
        memoryBudget = memoryBudget,
        diagnostics = diagnostics,
        dependencies = dependencies,
        phaseOrder = phaseOrder,
        elidedNoOpDraws = elidedNoOpDraws,
        atomicallyRefused = atomicallyRefused,
    )
}
