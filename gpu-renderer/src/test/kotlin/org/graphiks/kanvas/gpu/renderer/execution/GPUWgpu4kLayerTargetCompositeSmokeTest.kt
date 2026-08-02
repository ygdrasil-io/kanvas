package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.glfwContextRenderer

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommand
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskDependency
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskPhase
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskUseToken
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameTaskListBuilder
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_RENDER_PIPELINE_KEY
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveTargetStateHash
import org.graphiks.kanvas.gpu.renderer.recording.PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION
import org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUSceneTarget
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureResourceRef
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome

/**
 * Layer-target materialization smoke tests (Task 15).
 *
 * A frame with one saveLayer (one rect child, srcOver, configurable alpha) must:
 * 1. allocate the layer texture (frame-local RGBA8 attachment),
 * 2. render the children into the layer texture (clear -> draw -> store),
 * 3. composite the layer texture onto the scene target with the real blend plan and alpha.
 */
class GPUWgpu4kLayerTargetCompositeSmokeTest {
    @Test
    fun `layer target composite renders child into layer then onto scene`() {
        val backendSession = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backendSession != null)
        backendSession!!
        val runtimeCapabilities = requireNotNull(backendSession.capabilities)
        val generation = backendSession.deviceGeneration
        val requestId = GPUReadbackRequestID("readback.prepared.layer-composite")
        val session = backendSession.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(
            4,
            4,
            GPUColorFormat.RGBA8UnormSrgb,
            GPUColorInterpretation.LinearPremul,
        ),
        )
        try {
            val terminal = session.renderFrame(
                layerCompositeTaskList(
                    generation = generation,
                    capabilities = runtimeCapabilities,
                    frameId = GPUFrameID(10_760),
                    readbackRequestId = requestId,
                    alpha = 1f,
                ),
                GPUSceneFrameOutputRequest.ReadbackRgba(requestId),
            ).completion.toCompletableFuture().get(10, TimeUnit.SECONDS)

            assertEquals(
                GPUFrameStructuralOutcome.Succeeded,
                terminal.outcome,
                "${terminal.diagnostic?.code?.value}: ${terminal.diagnostic?.message} " +
                    "facts=${terminal.diagnostic?.facts}",
            )
            assertContentEquals(
                expectedOpaqueCompositePixels(),
                assertIs<GPUSceneFrameOutput.ReadbackRgba>(terminal.output).bytes,
            )
            val counters = session.nativeCounters()
            assertEquals(1L, counters.submits)
            assertEquals(1L, counters.readbackCopies)
        } finally {
            try {
                session.close()
            } finally {
                GPUBackendRuntimeNativeFactory.dispose()
            }
        }
    }

    @Test
    fun `translucent layer alpha composites over background`() {
        val backendSession = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backendSession != null)
        backendSession!!
        val runtimeCapabilities = requireNotNull(backendSession.capabilities)
        val generation = backendSession.deviceGeneration
        val requestId = GPUReadbackRequestID("readback.prepared.layer-alpha")
        val session = backendSession.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(
            4,
            4,
            GPUColorFormat.RGBA8UnormSrgb,
            GPUColorInterpretation.LinearPremul,
        ),
        )
        try {
            val terminal = session.renderFrame(
                layerCompositeTaskList(
                    generation = generation,
                    capabilities = runtimeCapabilities,
                    frameId = GPUFrameID(10_761),
                    readbackRequestId = requestId,
                    alpha = 0.5f,
                ),
                GPUSceneFrameOutputRequest.ReadbackRgba(requestId),
            ).completion.toCompletableFuture().get(10, TimeUnit.SECONDS)

            assertEquals(
                GPUFrameStructuralOutcome.Succeeded,
                terminal.outcome,
                "${terminal.diagnostic?.code?.value}: ${terminal.diagnostic?.message} " +
                    "facts=${terminal.diagnostic?.facts}",
            )
            assertRgbaNear(
                expectedTranslucentCompositePixels(),
                assertIs<GPUSceneFrameOutput.ReadbackRgba>(terminal.output).bytes,
                2,
                "translucent layer composite",
            )
        } finally {
            try {
                session.close()
            } finally {
                GPUBackendRuntimeNativeFactory.dispose()
            }
        }
    }

    @Test
    fun `prepared scene session plans one layer triplet between scene render and readback`() {
        val backendSession = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backendSession != null)
        backendSession!!
        val runtimeCapabilities = requireNotNull(backendSession.capabilities)
        val generation = backendSession.deviceGeneration
        val taskList = layerCompositeTaskList(
            generation = generation,
            capabilities = runtimeCapabilities,
            frameId = GPUFrameID(10_762),
            readbackRequestId = GPUReadbackRequestID("readback.prepared.layer-plan"),
            alpha = 1f,
        )
        try {
            val plan = GPUFramePlanner.plan(taskList)
            assertTrue(!plan.atomicallyRefused, plan.diagnostics.joinToString { it.code.value })
            val kinds = plan.steps.map { it::class }
            val readbackIndex = kinds.indexOf(GPUFrameStep.ReadbackCopyStep::class)
            val compositeIndex = kinds.indexOf(GPUFrameStep.LayerCompositeRenderStep::class)
            val childrenIndex = kinds.indexOf(GPUFrameStep.LayerChildrenRenderStep::class)
            val prepareIndex = kinds.indexOf(GPUFrameStep.LayerTargetPrepareStep::class)
            val sceneRenderIndex = kinds.indexOf(GPUFrameStep.RenderPassStep::class)
            assertTrue(readbackIndex > compositeIndex, "composite must precede readback: $kinds")
            assertTrue(compositeIndex > sceneRenderIndex, "composite must follow the scene render: $kinds")
            assertTrue(
                compositeIndex > childrenIndex && childrenIndex > prepareIndex,
                "layer triplet order: $kinds",
            )
        } finally {
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `materializer produces layer children and composite operands over the frame-local layer texture`() = runBlocking {
        val context = glfwContextRenderer(4, 4, "kanvas-layer-composite-operands", deferredRendering = true)
        val generation = GPUDeviceGenerationID(10_763)
        val fixture = capturedLayerCompositeInputs(
            generation,
            compositeCapabilities("layer-composite-operands").withFillRectFacts(),
        )
        val targetLifecycle = GPUWgpu4kPreparedSceneTargetLifecycle()
        val setup = GPUPreparedSceneSetupTransaction()
        val target = GPUWgpu4kPreparedSceneTarget.create(
            device = context.wgpuContext.device,
            width = 4,
            height = 4,
            format = io.ygdrasil.webgpu.GPUTextureFormat.RGBA8UnormSrgb,
            deviceGeneration = generation,
            targetGeneration = 1L,
            lifecycle = targetLifecycle,
            setupTransaction = setup,
        )
        setup.commit()
        val coreCache = GPUWgpu4kCorePrimitiveSessionCache(
            context.wgpuContext.device,
            generation,
        )
        val imageCache = GPUWgpu4kPreparedImageSessionCache(
            context.wgpuContext.device,
            generation,
        )
        val textCache = GPUWgpu4kPreparedTextSessionCache(
            context.wgpuContext.device,
            generation,
        )
        val colorGlyphCache = GPUWgpu4kColorGlyphSessionCache(context.wgpuContext.device)
        val surfaceBlitCache = GPUWgpu4kSurfaceBlitSessionCache(context.wgpuContext.device, target)
        val materializer = GPUWgpu4kPreparedSurfaceFramePayloadMaterializer(
            device = context.wgpuContext.device,
            queue = context.wgpuContext.device.queue,
            preparedSceneTarget = target,
            corePrimitiveCache = coreCache,
            preparedImageCache = imageCache,
            preparedTextCache = textCache,
            colorGlyphCache = colorGlyphCache,
            preparedImageHandleFactory = GPUWgpu4kPreparedImageNativeHandleFactory(
                context.wgpuContext.device,
            ),
            preparedImageCapabilities = compositeCapabilities("layer-composite-operands"),
            surfaceBlitCache = surfaceBlitCache,
            corePrimitiveLimits = GPULimits(8_192, 256, 256, maxBufferSize = 1L shl 30),
        )
        try {
            val materialization = materializer.materializeReusable(
                fixture.framePlan,
                fixture.encoderPlan,
                fixture.resources,
                fixture.generationSeal,
            )
            val materialized = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
                materialization,
                (materialization as? GPUPreparedNativeFramePayloadMaterialization.Refused)
                    ?.let { "refused: ${it.code}: ${it.message}" } ?: "layer-composite materialization must succeed",
            )
            val payload = materialized.draft.payload
            val compositeStepIndex = fixture.framePlan.steps.indexOfFirst {
                it is GPUFrameStep.LayerCompositeRenderStep
            }
            val layerRenderStepIndex = fixture.framePlan.steps.indexOfFirst { step ->
                step is GPUFrameStep.RenderPassStep && step.target == LAYER_TARGET
            }
            val sceneRenderStepIndex = fixture.framePlan.steps.indexOfFirst { step ->
                step is GPUFrameStep.RenderPassStep && step.target == TARGET
            }
            val compositeOperand = assertIs<GPUPreparedNativeScopeOperand.Render>(
                payload.scopeOperands.single { it.sourceStepIndex == compositeStepIndex },
            )
            assertEquals(GPUPreparedNativeLoadOperation.Load, compositeOperand.pass.loadOperation)
            assertEquals(GPUPreparedNativeStoreOperation.Store, compositeOperand.pass.storeOperation)
            assertNotNull(compositeOperand.pass.colorTarget)
            assertTrue(
                compositeOperand.commands.any { it is GPUPreparedNativeRenderCommand.SetPipeline },
                "composite must bind the prepared-image pipeline",
            )
            assertTrue(
                compositeOperand.commands.any { it is GPUPreparedNativeRenderCommand.SetBindGroup },
                "composite must bind a group sampling the layer texture",
            )
            assertTrue(
                compositeOperand.commands.any { it is GPUPreparedNativeRenderCommand.Draw },
                "composite must draw the textured quad",
            )
            val layerChildrenOperand = assertIs<GPUPreparedNativeScopeOperand.Render>(
                payload.scopeOperands.single { it.sourceStepIndex == layerRenderStepIndex },
            )
            assertEquals(GPUPreparedNativeLoadOperation.Clear, layerChildrenOperand.pass.loadOperation)
            assertTrue(
                layerChildrenOperand.pass.colorTarget.view !== target.view,
                "layer children must render into the frame-local layer texture, not the scene target",
            )
            val sceneOperand = assertIs<GPUPreparedNativeScopeOperand.Render>(
                payload.scopeOperands.single { it.sourceStepIndex == sceneRenderStepIndex },
            )
            assertEquals(target.view, sceneOperand.pass.colorTarget.view)
            assertTrue(materialized.draft.disposeBeforeRegistration())
        } finally {
            materializer.close()
            surfaceBlitCache.close()
            imageCache.close()
            textCache.close()
            colorGlyphCache.close()
            coreCache.close()
            target.close()
            context.close()
        }
    }

    @Test
    fun `executor harness composites the frame-local layer onto the scene with real blend`() = runBlocking {
        val context = glfwContextRenderer(4, 4, "kanvas-layer-composite-executor", deferredRendering = true)
        val generation = GPUDeviceGenerationID(10_764)
        val adapter = GPURuntimeResourceAdapter()
        val provider = GPUConcreteResourceProvider(leaseFactory = adapter)
        val completion = GPUQueueCompletionAdapter(
            deviceGeneration = generation,
            requirement = GPUQueueCompletionCapabilityRequirement(
                implementationRevision = "wgpu4k.0.2.0-20260716.235022-2.layer-composite-executor",
                capability = "on-submitted-work-done",
            ),
            evidence = GPUQueueCompletionCapabilityEvidence(
                implementationRevision = "wgpu4k.0.2.0-20260716.235022-2.layer-composite-executor",
                capability = "on-submitted-work-done",
                accepted = true,
            ),
            invoker = GPUQueueCompletionInvoker {
                context.wgpuContext.device.queue.onSubmittedWorkDone()
            },
        )
        val mappingExecutor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "kanvas-layer-composite-executor-readback").apply { isDaemon = true }
        }
        val targetLifecycle = GPUWgpu4kPreparedSceneTargetLifecycle()
        val setup = GPUPreparedSceneSetupTransaction()
        val target = GPUWgpu4kPreparedSceneTarget.create(
            device = context.wgpuContext.device,
            width = 4,
            height = 4,
            format = io.ygdrasil.webgpu.GPUTextureFormat.RGBA8UnormSrgb,
            deviceGeneration = generation,
            targetGeneration = 1L,
            lifecycle = targetLifecycle,
            setupTransaction = setup,
        )
        setup.commit()
        val coreCache = GPUWgpu4kCorePrimitiveSessionCache(context.wgpuContext.device, generation)
        val imageCache = GPUWgpu4kPreparedImageSessionCache(context.wgpuContext.device, generation)
        val textCache = GPUWgpu4kPreparedTextSessionCache(context.wgpuContext.device, generation)
        val colorGlyphCache = GPUWgpu4kColorGlyphSessionCache(context.wgpuContext.device)
        val surfaceBlitCache = GPUWgpu4kSurfaceBlitSessionCache(context.wgpuContext.device, target)
        val materializer = GPUWgpu4kPreparedSurfaceFramePayloadMaterializer(
            device = context.wgpuContext.device,
            queue = context.wgpuContext.device.queue,
            preparedSceneTarget = target,
            corePrimitiveCache = coreCache,
            preparedImageCache = imageCache,
            preparedTextCache = textCache,
            colorGlyphCache = colorGlyphCache,
            preparedImageHandleFactory = GPUWgpu4kPreparedImageNativeHandleFactory(
                context.wgpuContext.device,
            ),
            preparedImageCapabilities = compositeCapabilities("layer-composite-executor").withFillRectFacts(),
            surfaceBlitCache = surfaceBlitCache,
            corePrimitiveLimits = GPULimits(8_192, 256, 256, maxBufferSize = 1L shl 30),
        )
        val backend = GPUWgpu4kFrameEncodingBackend(
            generation,
            context.wgpuContext.device,
            context.wgpuContext.device.queue,
        )
        try {
            val requestId = GPUReadbackRequestID("readback.prepared.layer-executor")
            val taskList = layerCompositeTaskList(
                generation = generation,
                capabilities = compositeCapabilities("layer-composite-executor").withFillRectFacts(),
                frameId = GPUFrameID(10_764),
                readbackRequestId = requestId,
                alpha = 1f,
            )
            val plan = GPUFramePlanner.plan(taskList)
            assertTrue(!plan.atomicallyRefused, plan.diagnostics.joinToString { it.code.value })
            val targetGeneration = taskList.tasks.filterIsInstance<GPUTask.Render>()
                .flatMap(GPUTask.Render::drawPackets)
                .first()
                .resourceGeneration
            val resourceGenerations = taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
                .flatMap(GPUTask.PrepareResources::requests)
                .associate { request ->
                    request.resource to if (request.role == GPUFrameResourceRole.SceneTarget) {
                        targetGeneration
                    } else {
                        5L
                    }
                } + (LAYER_TARGET to (targetGeneration + 1L))
            val prepared = assertIs<GPUFramePreflightResult.Prepared>(
                GPUFramePreflighter(
                    context = GPUFramePreflightContext(
                        targetId = TARGET.value,
                        deviceGeneration = generation,
                        targetGeneration = targetGeneration,
                        resourceGenerations = resourceGenerations,
                    ),
                    capabilities = compositeCapabilities("layer-composite-executor").withFillRectFacts(),
                    resourceProvider = provider,
                    completionProvider = completion,
                    surfaceProvider = NoNativeSurfaceOutput,
                    nativeBoundary = adapter.bindNativeFrameBoundary(provider, materializer),
                ).preflight(plan),
                plan.diagnostics.joinToString { it.code.value },
            ).frame
            val executor = GPUFrameExecutor(
                sceneTarget = GPUSceneTarget(
                    targetId = TARGET.value,
                    resolvedTexture = GPUTextureResourceRef("prepared:${TARGET.value}"),
                    retainedMsaaAttachment = null,
                    width = 4,
                    height = 4,
                    format = GPUColorFormat.RGBA8UnormSrgb,
                    colorInterpretation = GPUColorInterpretation.LinearPremul,
                    usages = setOf(
                        GPUFrameResourceUsage.RenderAttachment,
                        GPUFrameResourceUsage.CopySource,
                        GPUFrameResourceUsage.TextureBinding,
                    ),
                    sampleCount = 1,
                    deviceGeneration = generation,
                    targetGeneration = targetGeneration,
                ),
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
            val readbackBytes = requireNotNull(terminal.readback).bytes
            assertEquals(
                expectedOpaqueCompositePixels().toList(),
                readbackBytes.toList(),
                "executor actual=" + readbackBytes.toList().chunked(4).joinToString { it.joinToString(",") },
            )
        } finally {
            backend.close()
            materializer.close()
            surfaceBlitCache.close()
            imageCache.close()
            textCache.close()
            colorGlyphCache.close()
            coreCache.close()
            target.close()
            mappingExecutor.shutdownNow()
            adapter.close()
            completion.close()
            context.close()
        }
    }


    @Test
    fun `nested layer composite is refused with the stable nesting code`() {
        val backendSession = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backendSession != null)
        backendSession!!
        val runtimeCapabilities = requireNotNull(backendSession.capabilities)
        val generation = backendSession.deviceGeneration
        val fixture = capturedTwoLayerInputs(
            generation = generation,
            capabilities = runtimeCapabilities,
            nested = true,
        )
        try {
            val result = GPUPreparedSurfaceNativePreflight().validate(
                fixture.framePlan,
                fixture.encoderPlan,
                fixture.resources,
                fixture.shaderContract,
                fixture.generationSeal,
            )
            assertEquals(
                "unsupported.prepared-surface.layer-nesting",
                assertIs<GPUPreparedSurfaceNativePreflightResult.Refused>(result).code,
            )
        } finally {
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    @Test
    fun `sibling layer composites remain admitted`() {
        val backendSession = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backendSession != null)
        backendSession!!
        val runtimeCapabilities = requireNotNull(backendSession.capabilities)
        val generation = backendSession.deviceGeneration
        val fixture = capturedTwoLayerInputs(
            generation = generation,
            capabilities = runtimeCapabilities,
            nested = false,
        )
        try {
            val result = GPUPreparedSurfaceNativePreflight().validate(
                fixture.framePlan,
                fixture.encoderPlan,
                fixture.resources,
                fixture.shaderContract,
                fixture.generationSeal,
            )
            assertIs<GPUPreparedSurfaceNativePreflightResult.Accepted>(
                result,
                (result as? GPUPreparedSurfaceNativePreflightResult.Refused)?.let {
                    "refused: ${it.code}: ${it.message}"
                } ?: "sibling layer composites must pass the prepared-surface preflight",
            )
        } finally {
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }
    private fun twoLayerCompositeTaskList(
        generation: GPUDeviceGenerationID,
        capabilities: GPUCapabilities,
        frameId: GPUFrameID,
        nested: Boolean,
    ): GPUTaskList {
        val recording = GPURecorder(
            GPURecordingID("recording.layer-composite-two"),
            frameId,
            capabilities,
            deviceGeneration = generation,
        ).apply {
            record(
                GPUFillRectCommandBuilder.build(
                    commandId = GPUDrawCommandID(1),
                    rect = GPURect(0f, 0f, 4f, 4f),
                    target = GPUTargetFacts(4, 4, "rgba8unorm"),
                    material = GPUMaterialDescriptor.SolidColor(0f, 0f, 1f, 1f),
                    paintOrder = 1,
                    source = GPUCommandSource("test", "fillRect", GPUFrameProvenance.GmContent),
                ),
            )
            record(
                GPUFillRectCommandBuilder.build(
                    commandId = GPUDrawCommandID(2),
                    rect = GPURect(1f, 1f, 3f, 3f),
                    target = GPUTargetFacts(4, 4, "rgba8unorm"),
                    material = GPUMaterialDescriptor.SolidColor(1f, 0f, 0f, 1f),
                    paintOrder = 2,
                    source = GPUCommandSource("test", "fillRect", GPUFrameProvenance.GmContent),
                ),
            )
            record(
                GPUFillRectCommandBuilder.build(
                    commandId = GPUDrawCommandID(3),
                    rect = GPURect(0f, 0f, 2f, 2f),
                    target = GPUTargetFacts(4, 4, "rgba8unorm"),
                    material = GPUMaterialDescriptor.SolidColor(0f, 1f, 0f, 1f),
                    paintOrder = 3,
                    source = GPUCommandSource("test", "fillRect", GPUFrameProvenance.GmContent),
                ),
            )
        }.close()
        val base = recording.taskList
        val build = GPUPreparedSurfaceFrameTaskListBuilder().build(
            GPUPreparedSurfaceFrameRequest(
                baseTaskList = base,
                capabilities = capabilities,
                target = TARGET,
                targetBounds = TARGET_BOUNDS,
                semanticsByCommandId = listOf(1, 2, 3).associate { commandId ->
                    commandId to preparedSurfaceCoreSemantic(base, commandId)
                },
                readbackRequestId = GPUReadbackRequestID("readback.prepared.layer-two"),
                targetFormat = GPUColorFormat.RGBA8UnormSrgb,
            ),
        )
        val recorded = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
            build,
            build.toString(),
        ).taskList
        val render = recorded.tasks.filterIsInstance<GPUTask.Render>().single()
        val background = render.drawPackets.single { it.commandIdValue == 1 }
        val child = render.drawPackets.single { it.commandIdValue == 2 }
        val secondChild = render.drawPackets.single { it.commandIdValue == 3 }
        val recordingId = recorded.recordingSeals.single().recordingId
        val sceneRender = GPUTask.Render(
            taskId = render.taskId,
            recordingId = recordingId,
            phase = GPUTaskPhase.Render,
            target = TARGET,
            loadStore = render.loadStore,
            samplePlan = render.samplePlan,
            resourceUses = render.resourceUses,
            drawPackets = listOf(background),
            batchEligibilityByPacketId = mapOf(
                background.packetId to GPUPassBatchEligibility(
                    kind = GPUPassBatchKind.SolidFill,
                    queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                ),
            ),
        )
        val layerRender = GPUTask.Render(
            taskId = GPUTaskID("task.render.layer"),
            recordingId = recordingId,
            phase = GPUTaskPhase.Render,
            target = LAYER_TARGET,
            loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store),
            samplePlan = render.samplePlan,
            resourceUses = render.resourceUses,
            drawPackets = listOf(layerBoundPacket(child, "packet.layer.child")),
            batchEligibilityByPacketId = mapOf(
                GPUDrawPacketID("packet.layer.child") to GPUPassBatchEligibility(
                    kind = GPUPassBatchKind.SolidFill,
                    queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                ),
            ),
        )
        val secondLayerRender = GPUTask.Render(
            taskId = GPUTaskID("task.render.layer.second"),
            recordingId = recordingId,
            phase = GPUTaskPhase.Render,
            target = SECOND_LAYER_TARGET,
            loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store),
            samplePlan = render.samplePlan,
            resourceUses = render.resourceUses,
            drawPackets = listOf(layerBoundPacket(secondChild, "packet.layer.second")),
            batchEligibilityByPacketId = mapOf(
                GPUDrawPacketID("packet.layer.second") to GPUPassBatchEligibility(
                    kind = GPUPassBatchKind.SolidFill,
                    queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                ),
            ),
        )
        val tasks = recorded.tasks.map { task -> if (task === render) sceneRender else task } +
            layerRender +
            secondLayerRender
        val ordered = tasks.sortedBy { task -> task.phase.ordinal }
        val dependencies = ordered.zipWithNext().mapIndexed { index, (from, to) ->
            GPUTaskDependency(
                fromTaskId = from.taskId,
                toTaskId = to.taskId,
                dependencyKind = "layer-composite-order",
                useToken = GPUTaskUseToken("layer-composite.$index"),
                reasonCode = "preserve.layer-composite.order",
            )
        }
        return GPUTaskList(
            frameId = recorded.frameId,
            capabilitySeal = recorded.capabilitySeal,
            recordingSeals = recorded.recordingSeals,
            expectedReplayKeyHash = recorded.expectedReplayKeyHash,
            tasks = tasks,
            dependencies = dependencies,
            phaseOrder = recorded.phaseOrder,
            memoryBudget = recorded.memoryBudget,
            compositeCommands = listOf(
                GPUPassCommand.PrepareLayerTarget(
                    targetLabel = LAYER_TARGET.value,
                    descriptorHash = "sha256:layer-test",
                    usageLabel = "render_attachment,texture_binding",
                    byteEstimate = 16384L,
                ),
                GPUPassCommand.RenderLayerChildren(
                    scopeLabel = "layer:test",
                    targetLabel = LAYER_TARGET.value,
                    childrenLabel = "draw.2",
                    tokenLabel = "token:layer",
                ),
                GPUPassCommand.CompositeLayer(
                    sourceLabel = LAYER_TARGET.value,
                    parentTargetLabel = TARGET.value,
                    blendModeLabel = "srcOver",
                    blendPlan = GPUBlendPlan.NoOp(GPUBlendMode.SRC_OVER, "test"),
                    routeLabel = "native.draw_layer.isolated_target",
                    tokenLabel = "token:layer",
                    alpha = 1f,
                    clipLabel = null,
                ),
                GPUPassCommand.PrepareLayerTarget(
                    targetLabel = SECOND_LAYER_TARGET.value,
                    descriptorHash = "sha256:layer-second",
                    usageLabel = "render_attachment,texture_binding",
                    byteEstimate = 16384L,
                ),
                GPUPassCommand.RenderLayerChildren(
                    scopeLabel = "layer:second",
                    targetLabel = SECOND_LAYER_TARGET.value,
                    childrenLabel = "draw.3",
                    tokenLabel = "token:layer.second",
                ),
                GPUPassCommand.CompositeLayer(
                    sourceLabel = SECOND_LAYER_TARGET.value,
                    parentTargetLabel = if (nested) LAYER_TARGET.value else TARGET.value,
                    blendModeLabel = "srcOver",
                    blendPlan = GPUBlendPlan.NoOp(GPUBlendMode.SRC_OVER, "test"),
                    routeLabel = "native.draw_layer.isolated_target",
                    tokenLabel = "token:layer.second",
                    alpha = 1f,
                    clipLabel = null,
                ),
            ),
        )
    }

    private fun layerBoundPacket(
        source: GPUDrawPacket,
        packetId: String,
    ): GPUDrawPacket {
        val authority = requireNotNull(source.corePrimitivePreparedAuthority)
        val layerStructural = authority.structuralPipelineKey.copy(
            colorFormat = GPUCorePrimitiveRenderPipelineStructuralKey.ColorFormat.Rgba8Unorm,
        )
        val layerPipelineKey =
            layerStructural.stableRenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY)
        return GPUDrawPacket(
            packetId = GPUDrawPacketID(packetId),
            commandIdValue = source.commandIdValue,
            analysisRecordId = source.analysisRecordId,
            passId = source.passId,
            layerId = source.layerId,
            bindingListId = source.bindingListId,
            insertionReasonCode = source.insertionReasonCode,
            sortKey = source.sortKey,
            sortKeyPreimage = source.sortKeyPreimage,
            renderStepId = source.renderStepId,
            renderStepVersion = source.renderStepVersion,
            role = source.role,
            blendPlan = source.blendPlan,
            renderPipelineKey = layerPipelineKey,
            bindingLayoutHash = source.bindingLayoutHash,
            uniformSlot = source.uniformSlot,
            resourceSlot = source.resourceSlot,
            semanticPayload = source.semanticPayload,
            vertexSourceLabel = source.vertexSourceLabel,
            scissorBoundsHash = source.scissorBoundsHash,
            targetStateHash = corePrimitiveTargetStateHash(1, GPUColorFormat("rgba8unorm")),
            originalPaintOrder = source.originalPaintOrder,
            resourceGeneration = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
            frameProvenance = source.frameProvenance,
            clipCoveragePlan = source.clipCoveragePlan,
            clipExecutionPlan = source.clipExecutionPlan,
            diagnostics = source.diagnostics,
            clipProducerAuthority = source.clipProducerAuthority,
        ).also { packet ->
            packet.attachCorePrimitivePreparedAuthority(
                authority.copy(
                    structuralPipelineKey = layerStructural,
                    renderPipelineKey = layerPipelineKey,
                ),
            )
        }
    }

    private fun capturedTwoLayerInputs(
        generation: GPUDeviceGenerationID,
        capabilities: GPUCapabilities,
        nested: Boolean,
    ): CapturedPreparedSurfaceInputs {
        val taskList = twoLayerCompositeTaskList(
            generation = generation,
            capabilities = capabilities,
            frameId = GPUFrameID(if (nested) 10_765 else 10_766),
            nested = nested,
        )
        val plan = GPUFramePlanner.plan(taskList)
        assertTrue(!plan.atomicallyRefused, plan.diagnostics.joinToString { it.code.value })
        val targetGeneration = taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
            .first()
            .resourceGeneration
        val resourceGenerations = taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
            .flatMap(GPUTask.PrepareResources::requests)
            .associate { request ->
                request.resource to if (request.role == GPUFrameResourceRole.SceneTarget) {
                    targetGeneration
                } else {
                    5L
                }
            } + mapOf(
            LAYER_TARGET to (targetGeneration + 1L),
            SECOND_LAYER_TARGET to (targetGeneration + 2L),
        )
        val adapter = GPURuntimeResourceAdapter()
        val provider = GPUConcreteResourceProvider(leaseFactory = adapter)
        val capture = CapturingPreparedNativeMaterializer()
        return try {
            val result = GPUFramePreflighter(
                context = GPUFramePreflightContext(
                    targetId = TARGET.value,
                    deviceGeneration = generation,
                    targetGeneration = targetGeneration,
                    resourceGenerations = resourceGenerations,
                ),
                capabilities = capabilities,
                resourceProvider = provider,
                completionProvider = LayerCompositeCompletionProvider,
                surfaceProvider = NoNativeSurfaceOutput,
                nativeBoundary = adapter.bindNativeFrameBoundary(provider, capture),
            ).preflight(plan)
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
    private fun capturedLayerCompositeInputs(
        generation: GPUDeviceGenerationID,
        capabilities: GPUCapabilities,
    ): CapturedPreparedSurfaceInputs {
        val taskList = layerCompositeTaskList(
            generation = generation,
            capabilities = capabilities,
            frameId = GPUFrameID(10_763),
            readbackRequestId = GPUReadbackRequestID("readback.prepared.layer-operands"),
            alpha = 1f,
        )
        val plan = GPUFramePlanner.plan(taskList)
        assertTrue(!plan.atomicallyRefused, plan.diagnostics.joinToString { it.code.value })
        val targetGeneration = taskList.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
            .first()
            .resourceGeneration
        val resourceGenerations = taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
            .flatMap(GPUTask.PrepareResources::requests)
            .associate { request ->
                request.resource to if (request.role == GPUFrameResourceRole.SceneTarget) {
                    targetGeneration
                } else {
                    5L
                }
            } + (LAYER_TARGET to (targetGeneration + 1L))
        val adapter = GPURuntimeResourceAdapter()
        val provider = GPUConcreteResourceProvider(leaseFactory = adapter)
        val capture = CapturingPreparedNativeMaterializer()
        return try {
            val result = GPUFramePreflighter(
                context = GPUFramePreflightContext(
                    targetId = TARGET.value,
                    deviceGeneration = generation,
                    targetGeneration = targetGeneration,
                    resourceGenerations = resourceGenerations,
                ),
                capabilities = capabilities,
                resourceProvider = provider,
                completionProvider = LayerCompositeCompletionProvider,
                surfaceProvider = NoNativeSurfaceOutput,
                nativeBoundary = adapter.bindNativeFrameBoundary(provider, capture),
            ).preflight(plan)
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

    private fun expectedOpaqueCompositePixels(): ByteArray = ByteArray(64).also { bytes ->
        for (y in 0 until 4) for (x in 0 until 4) {
            val offset = (y * 4 + x) * 4
            if (x in 1 until 3 && y in 1 until 3) {
                bytes[offset] = 255.toByte()
            } else {
                bytes[offset + 2] = 255.toByte()
            }
            bytes[offset + 3] = 255.toByte()
        }
    }

    private fun expectedTranslucentCompositePixels(): ByteArray = ByteArray(64).also { bytes ->
        for (y in 0 until 4) for (x in 0 until 4) {
            val offset = (y * 4 + x) * 4
            if (x in 1 until 3 && y in 1 until 3) {
                // Linear srcOver of premul red at alpha 0.5 over blue yields linear
                // (0.5, 0, 0.5); the sRGB attachment stores the encoded value (~187-188).
                bytes[offset] = 188.toByte()
                bytes[offset + 2] = 188.toByte()
            } else {
                bytes[offset + 2] = 255.toByte()
            }
            bytes[offset + 3] = 255.toByte()
        }
    }

    private fun assertRgbaNear(
        expected: ByteArray,
        actual: ByteArray,
        tolerance: Int,
        label: String,
    ) {
        assertEquals(expected.size, actual.size, label)
        expected.indices.forEach { index ->
            val delta = kotlin.math.abs(
                (expected[index].toInt() and 0xff) - (actual[index].toInt() and 0xff),
            )
            assertTrue(delta <= tolerance, "$label byte[$index] delta=$delta")
        }
    }

    private fun layerCompositeTaskList(
        generation: GPUDeviceGenerationID,
        capabilities: GPUCapabilities,
        frameId: GPUFrameID,
        readbackRequestId: GPUReadbackRequestID,
        alpha: Float,
    ): GPUTaskList {
        val recording = GPURecorder(
            GPURecordingID("recording.layer-composite"),
            frameId,
            capabilities,
            deviceGeneration = generation,
        ).apply {
            record(
                GPUFillRectCommandBuilder.build(
                    commandId = GPUDrawCommandID(1),
                    rect = GPURect(0f, 0f, 4f, 4f),
                    target = GPUTargetFacts(4, 4, "rgba8unorm"),
                    material = GPUMaterialDescriptor.SolidColor(0f, 0f, 1f, 1f),
                    paintOrder = 1,
                    source = GPUCommandSource("test", "fillRect", GPUFrameProvenance.GmContent),
                ),
            )
            record(
                GPUFillRectCommandBuilder.build(
                    commandId = GPUDrawCommandID(2),
                    rect = GPURect(1f, 1f, 3f, 3f),
                    target = GPUTargetFacts(4, 4, "rgba8unorm"),
                    material = GPUMaterialDescriptor.SolidColor(1f, 0f, 0f, 1f),
                    paintOrder = 2,
                    source = GPUCommandSource("test", "fillRect", GPUFrameProvenance.GmContent),
                ),
            )
        }.close()
        val base = recording.taskList
        val build = GPUPreparedSurfaceFrameTaskListBuilder().build(
            GPUPreparedSurfaceFrameRequest(
                baseTaskList = base,
                capabilities = capabilities,
                target = TARGET,
                targetBounds = TARGET_BOUNDS,
                semanticsByCommandId = listOf(1, 2).associate { commandId ->
                    commandId to preparedSurfaceCoreSemantic(base, commandId)
                },
                readbackRequestId = readbackRequestId,
                targetFormat = GPUColorFormat.RGBA8UnormSrgb,
            ),
        )
        val recorded = assertIs<GPUPreparedSurfaceFrameResult.Recorded>(
            build,
            build.toString(),
        ).taskList
        val render = recorded.tasks.filterIsInstance<GPUTask.Render>().single()
        val background = render.drawPackets.single { it.commandIdValue == 1 }
        val child = render.drawPackets.single { it.commandIdValue == 2 }
        val childAuthority = requireNotNull(child.corePrimitivePreparedAuthority)
        val layerStructural = childAuthority.structuralPipelineKey.copy(
            colorFormat = GPUCorePrimitiveRenderPipelineStructuralKey.ColorFormat.Rgba8Unorm,
        )
        val layerPipelineKey =
            layerStructural.stableRenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY)
        val childLayerPacket = GPUDrawPacket(
            packetId = GPUDrawPacketID("packet.layer.child"),
            commandIdValue = child.commandIdValue,
            analysisRecordId = child.analysisRecordId,
            passId = child.passId,
            layerId = child.layerId,
            bindingListId = child.bindingListId,
            insertionReasonCode = child.insertionReasonCode,
            sortKey = child.sortKey,
            sortKeyPreimage = child.sortKeyPreimage,
            renderStepId = child.renderStepId,
            renderStepVersion = child.renderStepVersion,
            role = child.role,
            blendPlan = child.blendPlan,
            renderPipelineKey = layerPipelineKey,
            bindingLayoutHash = child.bindingLayoutHash,
            uniformSlot = child.uniformSlot,
            resourceSlot = child.resourceSlot,
            semanticPayload = child.semanticPayload,
            vertexSourceLabel = child.vertexSourceLabel,
            scissorBoundsHash = child.scissorBoundsHash,
            targetStateHash = corePrimitiveTargetStateHash(1, GPUColorFormat("rgba8unorm")),
            originalPaintOrder = child.originalPaintOrder,
            resourceGeneration = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
            frameProvenance = child.frameProvenance,
            clipCoveragePlan = child.clipCoveragePlan,
            clipExecutionPlan = child.clipExecutionPlan,
            diagnostics = child.diagnostics,
            clipProducerAuthority = child.clipProducerAuthority,
        ).also { packet ->
            packet.attachCorePrimitivePreparedAuthority(
                childAuthority.copy(
                    structuralPipelineKey = layerStructural,
                    renderPipelineKey = layerPipelineKey,
                ),
            )
        }
        val recordingId = recorded.recordingSeals.single().recordingId
        val sceneRender = GPUTask.Render(
            taskId = render.taskId,
            recordingId = recordingId,
            phase = GPUTaskPhase.Render,
            target = TARGET,
            loadStore = render.loadStore,
            samplePlan = render.samplePlan,
            resourceUses = render.resourceUses,
            drawPackets = listOf(background),
            batchEligibilityByPacketId = mapOf(
                background.packetId to GPUPassBatchEligibility(
                    kind = GPUPassBatchKind.SolidFill,
                    queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                ),
            ),
        )
        val layerRender = GPUTask.Render(
            taskId = GPUTaskID("task.render.layer"),
            recordingId = recordingId,
            phase = GPUTaskPhase.Render,
            target = LAYER_TARGET,
            loadStore = GPULoadStorePlan("clear", GPUStorePlan.Store),
            samplePlan = render.samplePlan,
            resourceUses = render.resourceUses,
            drawPackets = listOf(childLayerPacket),
            batchEligibilityByPacketId = mapOf(
                childLayerPacket.packetId to GPUPassBatchEligibility(
                    kind = GPUPassBatchKind.SolidFill,
                    queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                ),
            ),
        )
        val tasks = recorded.tasks.map { task -> if (task === render) sceneRender else task } + layerRender
        val ordered = tasks.sortedBy { task -> task.phase.ordinal }
        val dependencies = ordered.zipWithNext().mapIndexed { index, (from, to) ->
            GPUTaskDependency(
                fromTaskId = from.taskId,
                toTaskId = to.taskId,
                dependencyKind = "layer-composite-order",
                useToken = GPUTaskUseToken("layer-composite.$index"),
                reasonCode = "preserve.layer-composite.order",
            )
        }
        return GPUTaskList(
            frameId = recorded.frameId,
            capabilitySeal = recorded.capabilitySeal,
            recordingSeals = recorded.recordingSeals,
            expectedReplayKeyHash = recorded.expectedReplayKeyHash,
            tasks = tasks,
            dependencies = dependencies,
            phaseOrder = recorded.phaseOrder,
            memoryBudget = recorded.memoryBudget,
            compositeCommands = listOf(
                GPUPassCommand.PrepareLayerTarget(
                    targetLabel = LAYER_TARGET.value,
                    descriptorHash = "sha256:layer-test",
                    usageLabel = "render_attachment,texture_binding",
                    byteEstimate = 16384L,
                ),
                GPUPassCommand.RenderLayerChildren(
                    scopeLabel = "layer:test",
                    targetLabel = LAYER_TARGET.value,
                    childrenLabel = "draw.2",
                    tokenLabel = "token:layer",
                ),
                GPUPassCommand.CompositeLayer(
                    sourceLabel = LAYER_TARGET.value,
                    parentTargetLabel = TARGET.value,
                    blendModeLabel = "srcOver",
                    blendPlan = GPUBlendPlan.NoOp(GPUBlendMode.SRC_OVER, "test"),
                    routeLabel = "native.draw_layer.isolated_target",
                    tokenLabel = "token:layer",
                    alpha = alpha,
                    clipLabel = null,
                ),
            ),
        )
    }

    private fun preparedSurfaceCoreSemantic(
        base: GPUTaskList,
        commandId: Int,
    ): GPUDrawSemanticPayload.CorePrimitive {
        val packet = base.tasks.filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
            .single { it.commandIdValue == commandId }
        val rect = when (commandId) {
            1 -> GPUCorePrimitiveGeometryInput.Rect(0f, 0f, 4f, 4f)
            2 -> GPUCorePrimitiveGeometryInput.Rect(1f, 1f, 3f, 3f)
            3 -> GPUCorePrimitiveGeometryInput.Rect(0f, 0f, 2f, 2f)
            else -> error("Unexpected fixture command $commandId")
        }
        val color = when (commandId) {
            1 -> listOf(0f, 0f, 1f, 1f)
            2 -> listOf(1f, 0f, 0f, 1f)
            3 -> listOf(0f, 1f, 0f, 1f)
            else -> error("Unexpected fixture command $commandId")
        }
        return GPUCorePrimitivePayloadGatherer().gatherSemantic(
            GPUCorePrimitivePayloadInput(
                commandIdValue = commandId,
                sourceFamily = GPUCorePrimitiveSourceFamily.Color,
                geometry = rect,
                premultipliedRgba = color,
                targetBounds = TARGET_BOUNDS,
                scissorBounds = TARGET_BOUNDS,
                clipCoveragePlan = GPUClipCoveragePlan.NoClip,
                clipExecutionPlanIdentity = GPUClipExecutionPlan.NoClip.canonicalIdentity(),
                blendPlanIdentity = requireNotNull(packet.blendPlan).canonicalIdentity(),
                frameProvenance = packet.frameProvenance,
                coverageMode = GPUCorePrimitiveCoverageMode.FullOrScissor,
            ),
        )
    }

    private fun GPUCapabilities.withFillRectFacts(): GPUCapabilities = copy(
        facts = facts + GPUCapabilityFact(
            "first_slice.fill_rect.native",
            "test",
            "supported",
            true,
            "layer-composite",
        ),
    )

    private fun compositeCapabilities(snapshotId: String) = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "wgpu4k", "native", "native"),
        facts = listOf(GPUCapabilityFact("limits", "test", "observed", true, "layer-composite")),
        snapshotId = snapshotId,
        limits = GPULimits(
            maxTextureDimension2D = 8_192,
            copyBytesPerRowAlignment = 256,
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = 1L shl 30,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
        rendererFeatures = setOf(
            GPURendererFeature.RenderPass,
            GPURendererFeature.CopyUpload,
            GPURendererFeature.Readback,
        ),
    )

    private object NoOpRetention : GPUFrameResourceRetention {
        override fun registerAfterSubmit(registration: GPUFrameRetentionRegistration) = Unit
        override fun complete(ticket: GPUQueueCompletionTicket, outcome: GPUQueueCompletionOutcome) = Unit
        override fun quarantine(
            registration: GPUFrameRetentionRegistration,
            diagnostic: org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic,
        ) = Unit
    }

    private object NoNativeSurfaceOutput : GPUSurfaceOutputProvider {
        override fun acquire(request: GPUSurfaceAcquisitionRequest): GPUSurfaceAcquisitionResult =
            GPUSurfaceAcquisitionResult.Unavailable(GPUSurfaceAcquisitionStatus.Timeout)

        override fun release(output: GPUAcquiredSurfaceOutput): GPUSurfaceReleaseResult =
            GPUSurfaceReleaseResult.Released
    }

    private object LayerCompositeCompletionProvider : GPUQueueCompletionProvider {
        override fun reserveTicket(
            request: GPUQueueCompletionTicketRequest,
        ): GPUQueueCompletionTicketReservation = error(
            "Layer-composite capture must refuse before reserving a completion ticket",
        )

        override fun abandonReservedTicket(
            ticket: GPUQueueCompletionTicket,
        ): GPUQueueCompletionTicketAbandonResult = error(
            "Layer-composite capture never owns a completion ticket",
        )
    }

    private companion object {
        val TARGET = GPUFrameTargetRef("target.scene")
        val LAYER_TARGET = GPUFrameTargetRef("layer-target:test")
        val SECOND_LAYER_TARGET = GPUFrameTargetRef("layer-target:second")
        val STAGING = GPUFrameBufferRef("buffer.readback")
        val TARGET_BOUNDS = GPUPixelBounds(0, 0, 4, 4)
    }
}
