package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.gpu.plan.PlanBudget
import org.graphiks.kanvas.gpu.plan.PlanBufferAllocationPolicy
import org.graphiks.kanvas.gpu.plan.PlanCapabilitySnapshot
import org.graphiks.kanvas.gpu.plan.PlanDepthStencilFormat
import org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat
import org.graphiks.kanvas.gpu.plan.PlanOperationCapability
import org.graphiks.kanvas.gpu.plan.PlanResourceUsage
import org.graphiks.kanvas.gpu.plan.PlanTextureFormat
import org.graphiks.kanvas.gpu.plan.PlanTextureResolveSupport
import org.graphiks.kanvas.gpu.plan.PlanTextureSampleSupport
import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.plan.W4dGeneralPathPlanCompiler
import org.graphiks.kanvas.gpu.plan.GpuPlanSelection
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureFormatSampleSupport
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureSampleCountSupport
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanLoweringRequest
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanLoweringResult
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanTaskListLowerer
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleResolveAction
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.render.ir.BlendMode
import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.CoverageRequest
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.kanvas.render.ir.PaintNode
import org.graphiks.kanvas.render.ir.PaintStyleNode
import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneExtent
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.kanvas.render.ir.StrokeCapNode
import org.graphiks.kanvas.render.ir.StrokeJoinNode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.matrix.Matrix3x3F32

class GPUWgpu4kCorePrimitiveW4dGeneralFrameTest {
    @Test
    fun `authenticated W4d general AA materializes pooled four and one sample native attachments`() {
        val taskList = loweredMixedAaGraph().taskList
        val frame = GPUFramePlanner.plan(taskList)
        val preflight = preflight(frame, sceneTarget(taskList))
        val prepared = assertIs<GPUFramePreflightResult.Prepared>(
            preflight,
            (preflight as? GPUFramePreflightResult.Refused)
                ?.let { "${it.diagnostic.code.value}: ${it.diagnostic.message}; ${it.diagnostic.facts}" }
                .orEmpty(),
        ).frame
        val native = GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy()
        val setup = GPUPreparedSceneSetupTransaction()
        val target = GPUWgpu4kPreparedSceneTarget.create(
            native.device,
            16,
            16,
            GPUTextureFormat.RGBA8UnormSrgb,
            generation,
            1L,
            GPUWgpu4kPreparedSceneTargetLifecycle(),
            setup,
        )
        setup.commit()
        val cache = GPUWgpu4kCorePrimitiveSessionCache(native.device, generation)
        val materializer = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
            native.device,
            native.queue,
            target,
            cache,
            requireNotNull(capabilities().limits),
        )
        var draft: GPUPreparedNativeFrameDraft? = null
        try {
            val result = materializer.materializeReusable(
                frame,
                prepared.encoderPlan,
                prepared.resources,
                prepared.generationSeal,
            )
            draft = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
                result,
                (result as? GPUPreparedNativeFramePayloadMaterialization.Refused)
                    ?.let { "${it.code}: ${it.message}" }
                    .orEmpty(),
            ).draft
            val renders = draft.payload.scopeOperands
                .filterIsInstance<GPUPreparedNativeScopeOperand.Render>()
            assertTrue(renders.size >= 2)
            assertTrue(renders.dropLast(1).all { render -> render.pass.resolveTarget == null })
            assertTrue(renders.last().pass.resolveTarget != null)
            assertTrue(renders.any { render ->
                render.pass.colorTarget.view !== requireNotNull(renders.last().pass.resolveTarget).view
            })
            assertTrue(renders.any { render -> render.pass.depthStencilTarget != null })
            assertTrue(native.textureDescriptors.any { descriptor ->
                descriptor.format == GPUTextureFormat.RGBA8UnormSrgb && descriptor.sampleCount == 4u
            })
            assertTrue(native.textureDescriptors.any { descriptor ->
                descriptor.format == GPUTextureFormat.RGBA8Unorm && descriptor.sampleCount == 1u
            })
            assertTrue(native.textureDescriptors.any { descriptor ->
                descriptor.format == GPUTextureFormat.Depth24PlusStencil8 && descriptor.sampleCount == 4u
            })
            assertTrue(native.textureDescriptors.any { descriptor ->
                descriptor.format == GPUTextureFormat.Depth24PlusStencil8 && descriptor.sampleCount == 1u
            })
            val consumerBinding = renders.flatMap { render -> render.commands }
                .filterIsInstance<GPUPreparedNativeRenderCommand.SetBindGroup>()
                .single { command ->
                    command.bindGroup.bindGroup.toString() ==
                        "Kanvas.session.corePrimitive.framePool.coverageMask.consumerBindGroup"
                }
            val consumerOffset = consumerBinding.dynamicOffsets.single().toInt()
            val uniformUpload = native.writeBufferCalls.single { call ->
                call.bufferLabel == "Kanvas.session.corePrimitive.framePool.uniforms"
            }.snapshot
            assertTrue(consumerOffset >= 0 && consumerOffset + 64 <= uniformUpload.size)
            assertTrue(
                renders.flatMap { render -> render.commands }
                    .filterIsInstance<GPUPreparedNativeRenderCommand.SetBindGroup>()
                    .any { command -> command.dynamicOffsets.singleOrNull() != consumerBinding.dynamicOffsets.single() },
            )
            val expectedConsumerUniform = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN).apply {
                putFloat(16f)
                putFloat(16f)
                putInt(0)
                putInt(0)
                putInt(16)
                putInt(16)
                putLong(0L)
                putFloat(192f / 255f)
                putFloat(0f)
                putFloat(0f)
                putFloat(192f / 255f)
                putInt(0)
                repeat(12) { put(0) }
            }.array()
            assertEquals(
                expectedConsumerUniform.toList(),
                uniformUpload.copyOfRange(consumerOffset, consumerOffset + 64).toList(),
            )
        } finally {
            draft?.let { assertTrue(it.disposeBeforeRegistration()) }
            materializer.close()
            cache.close()
            target.close()
            if (prepared.claimForRollback()) assertTrue(prepared.rollback.execute().successful)
        }
    }

    @Test
    fun `publicly injectable W4d native pre registration failures roll back the mixed lease before retry`() {
        val failureTargets = listOf(
            GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy.SemanticFailureTarget
                .BufferCreation("Kanvas.session.corePrimitive.framePool.vertices"),
            GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy.SemanticFailureTarget
                .TextureCreation("Kanvas.session.corePrimitive.framePool.msaaColor4x"),
            GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy.SemanticFailureTarget
                .TextureViewCreation("Kanvas.session.corePrimitive.framePool.msaaColor4x.view"),
            GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy.SemanticFailureTarget
                .BindGroupCreation(
                    "Kanvas.session.corePrimitive.framePool.coverageMask.consumerBindGroup",
                ),
            GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy.SemanticFailureTarget
                .BufferUpload("Kanvas.session.corePrimitive.framePool.uniforms"),
        )
        failureTargets.forEach { failureTarget ->
            val taskList = loweredMixedAaGraph().taskList
            val frame = GPUFramePlanner.plan(taskList)
            val prepared = assertIs<GPUFramePreflightResult.Prepared>(
                preflight(frame, sceneTarget(taskList)),
            ).frame
            val native = GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.NativeProxy()
            val setup = GPUPreparedSceneSetupTransaction()
            val target = GPUWgpu4kPreparedSceneTarget.create(
                native.device,
                16,
                16,
                GPUTextureFormat.RGBA8UnormSrgb,
                generation,
                1L,
                GPUWgpu4kPreparedSceneTargetLifecycle(),
                setup,
            )
            setup.commit()
            val cache = GPUWgpu4kCorePrimitiveSessionCache(native.device, generation)
            val materializer = GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
                native.device,
                native.queue,
                target,
                cache,
                requireNotNull(capabilities().limits),
            )
            var retry: GPUPreparedNativeFrameDraft? = null
            try {
                native.refuse(failureTarget)
                val first = materializer.materializeReusable(
                    frame,
                    prepared.encoderPlan,
                    prepared.resources,
                    prepared.generationSeal,
                )
                assertIs<GPUPreparedNativeFramePayloadMaterialization.Refused>(first)
                assertTrue("queue.submit" !in native.events)

                GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
                    native.device,
                    native.queue,
                    target,
                    cache,
                    requireNotNull(capabilities().limits),
                ).use { retryMaterializer ->
                    retry = assertIs<GPUPreparedNativeFramePayloadMaterialization.Materialized>(
                        retryMaterializer.materializeReusable(
                            frame,
                            prepared.encoderPlan,
                            prepared.resources,
                            prepared.generationSeal,
                        ),
                        failureTarget.toString(),
                    ).draft
                    assertTrue(retry.disposeBeforeRegistration())
                    retry = null
                }
            } finally {
                retry?.let { assertTrue(it.disposeBeforeRegistration()) }
                materializer.close()
                cache.close()
                target.close()
                if (prepared.claimForRollback()) assertTrue(prepared.rollback.execute().successful)
            }
        }
    }

    @Test
    fun `authenticated four sample W4d general stencil frame preflights before native materialization`() {
        val taskList = loweredConcaveAaGraph().taskList
        val target = sceneTarget(taskList)
        val frame = GPUFramePlanner.plan(taskList)

        val result = preflight(frame, target)

        val prepared = when (result) {
            is GPUFramePreflightResult.Prepared -> result.frame
            is GPUFramePreflightResult.Refused -> error(
                "Authenticated W4d general frame was refused: ${result.diagnostic.code.value}: " +
                    "${result.diagnostic.message}; facts=${result.diagnostic.facts}",
            )
        }
        check(prepared.claimForRollback())
        check(prepared.rollback.execute().successful)
    }

    @Test
    fun `same four sample W4d general sequence without prepared authority remains atomically refused`() {
        val authenticated = loweredMixedAaGraph().taskList

        val frame = GPUFramePlanner.plan(authenticated.withoutPreparedAuthorities())

        kotlin.test.assertTrue(frame.atomicallyRefused)
        kotlin.test.assertEquals(
            "invalid.frame_plan.msaa_continuation_missing",
            frame.diagnostics.single().code.value,
        )
    }

    @Test
    fun `authenticated W4d general planner preserves intermediate skips and final canonical resolve`() {
        val frame = GPUFramePlanner.plan(loweredConcaveAaGraph().taskList)

        val continuations = frame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            .map { step -> requireNotNull(step.sampleContinuation) }

        assertTrue(continuations.size >= 2)
        assertTrue(continuations.all { it.key.samplePlan.sampleCount == 4 })
        assertEquals(
            List(continuations.size - 1) { GPUSampleResolveAction.Skip } +
                GPUSampleResolveAction.ResolveCanonical,
            continuations.map { it.resolveAction },
        )
    }

    @Test
    fun `forged early W4d general resolve is refused during preflight`() {
        val taskList = loweredConcaveAaGraph().taskList
        val target = sceneTarget(taskList)
        val frame = GPUFramePlanner.plan(taskList)
        val forged = frame.replaceFirstIntermediateResolve()

        val result = preflight(forged, target)

        val refused = assertIs<GPUFramePreflightResult.Refused>(result)
        assertEquals("invalid.preflight.w4d_general_msaa_authority", refused.diagnostic.code.value)
    }

    @Test
    fun `authenticated hard W4d general path stays single sample without a resolve`() {
        val taskList = loweredConcaveHardGraph().taskList
        val frame = GPUFramePlanner.plan(taskList)

        val renders = frame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        assertTrue(renders.isNotEmpty())
        assertTrue(renders.all { it.samplePlan.sampleCount == 1 && it.sampleContinuation == null })
        assertIs<GPUFramePreflightResult.Prepared>(preflight(frame, sceneTarget(taskList)))
    }

    @Test
    fun `sealed W4d general native resource table refuses a substituted hard target before materialization`() {
        val taskList = loweredConcaveHardGraph().taskList
        val forged = GPUFramePlanner.plan(taskList).replaceFirstW4dTarget(GPUFrameTargetRef("forged.target"))

        val result = preflight(forged, sceneTarget(taskList))

        val refused = assertIs<GPUFramePreflightResult.Refused>(result)
        assertEquals("invalid.preflight.w4d_general_native_authority", refused.diagnostic.code.value)
    }

    private fun sceneTarget(
        taskList: org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList,
    ): GPUFrameTargetRef = requireNotNull(
        taskList.tasks.filterIsInstance<GPUTask.PrepareResources>()
            .flatMap(GPUTask.PrepareResources::requests)
            .single { request -> request.role == GPUFrameResourceRole.SceneTarget }
            .resource as? GPUFrameTargetRef,
    )

    private fun org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
        .replaceFirstIntermediateResolve(): org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan {
        var replaced = false
        return org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan(
        frameId = frameId,
        capabilitySeal = capabilitySeal,
        recordingSeals = recordingSeals,
        steps = steps.map { step ->
            val render = step as? GPUFrameStep.RenderPassStep
            if (!replaced && render?.sampleContinuation?.resolveAction == GPUSampleResolveAction.Skip) {
                replaced = true
                GPUFrameStep.RenderPassStep(
                    target = render.target,
                    loadStore = render.loadStore,
                    samplePlan = render.samplePlan,
                    resourceUses = render.resourceUses,
                    drawPackets = render.drawPackets,
                    sourceTaskIds = render.sourceTaskIds,
                    batches = render.batches,
                    sampleContinuation = render.sampleContinuation.copy(
                        resolveAction = GPUSampleResolveAction.ResolveCanonical,
                    ),
                    depthStencilLoadStore = render.depthStencilLoadStore,
                    preparedImageBindingsByPacketId = render.preparedImageBindingsByPacketId,
                    preparedTextBindingsByPacketId = render.preparedTextBindingsByPacketId,
                )
            } else {
                step
            }
        },
        memoryBudget = memoryBudget,
        diagnostics = diagnostics,
        dependencies = dependencies,
        phaseOrder = phaseOrder,
        elidedNoOpDraws = elidedNoOpDraws,
        )
    }

    private fun org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
        .replaceFirstW4dTarget(
            target: GPUFrameTargetRef,
        ): org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan {
        var replaced = false
        return org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan(
            frameId = frameId,
            capabilitySeal = capabilitySeal,
            recordingSeals = recordingSeals,
            steps = steps.map { step ->
                val render = step as? GPUFrameStep.RenderPassStep
                if (!replaced && render != null) {
                    replaced = true
                    GPUFrameStep.RenderPassStep(
                        target = target,
                        loadStore = render.loadStore,
                        samplePlan = render.samplePlan,
                        resourceUses = render.resourceUses,
                        drawPackets = render.drawPackets,
                        sourceTaskIds = render.sourceTaskIds,
                        batches = render.batches,
                        sampleContinuation = render.sampleContinuation,
                        depthStencilLoadStore = render.depthStencilLoadStore,
                        preparedImageBindingsByPacketId = render.preparedImageBindingsByPacketId,
                        preparedTextBindingsByPacketId = render.preparedTextBindingsByPacketId,
                    )
                } else {
                    step
                }
            },
            memoryBudget = memoryBudget,
            diagnostics = diagnostics,
            dependencies = dependencies,
            phaseOrder = phaseOrder,
            elidedNoOpDraws = elidedNoOpDraws,
        )
    }

    private fun org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList.withoutPreparedAuthorities() =
        org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList(
            frameId = frameId,
            capabilitySeal = capabilitySeal,
            recordingSeals = recordingSeals,
            expectedReplayKeyHash = expectedReplayKeyHash,
            tasks = tasks.map { task ->
                if (task is GPUTask.Render) task.withoutPreparedAuthorities() else task
            },
            dependencies = dependencies,
            phaseOrder = phaseOrder,
            memoryBudget = memoryBudget,
            diagnostics = diagnostics,
            compositeCommands = compositeCommands,
        )

    private fun GPUTask.Render.withoutPreparedAuthorities() = GPUTask.Render(
        taskId = taskId,
        recordingId = recordingId,
        phase = phase,
        target = target,
        loadStore = loadStore,
        samplePlan = samplePlan,
        resourceUses = resourceUses,
        provisionalSegmentKey = provisionalSegmentKey,
        drawPackets = drawPackets.map { packet -> packet.withoutPreparedAuthority() },
        batchEligibilityByPacketId = batchEligibilityByPacketId,
        sampleContinuationKey = sampleContinuationKey,
        compositeMembership = compositeMembership,
        depthStencilLoadStore = depthStencilLoadStore,
        preparedImageBindingsByPacketId = preparedImageBindingsByPacketId,
        preparedTextBindingsByPacketId = preparedTextBindingsByPacketId,
    )

    private fun GPUDrawPacket.withoutPreparedAuthority() = GPUDrawPacket(
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
        w4dBinaryMaskConsumer = w4dBinaryMaskConsumer,
    )

    private fun loweredConcaveAaGraph(): GpuPlanLoweringResult.Lowered {
        val graph = graph(antiAlias = true)
        return assertIs(
            GpuPlanTaskListLowerer().lower(
                GpuPlanLoweringRequest(
                    graph = graph,
                    capabilities = capabilities(),
                    deviceGeneration = generation,
                    currentBudget = graph.budget,
                    frameId = GPUFrameID(81),
                    recordingId = GPURecordingID("w4d-general-frame"),
                ),
            ),
        )
    }

    private fun loweredConcaveHardGraph(): GpuPlanLoweringResult.Lowered {
        val graph = graph(antiAlias = false)
        return assertIs(
            GpuPlanTaskListLowerer().lower(
                GpuPlanLoweringRequest(
                    graph = graph,
                    capabilities = capabilities(),
                    deviceGeneration = generation,
                    currentBudget = graph.budget,
                    frameId = GPUFrameID(82),
                    recordingId = GPURecordingID("w4d-general-hard-frame"),
                ),
            ),
        )
    }

    private fun loweredMixedAaGraph(): GpuPlanLoweringResult.Lowered {
        val graph = graph(antiAlias = true, mixedHard = true)
        val lowered =
            GpuPlanTaskListLowerer().lower(
                GpuPlanLoweringRequest(
                    graph = graph,
                    capabilities = capabilities(),
                    deviceGeneration = generation,
                    currentBudget = graph.budget,
                    frameId = GPUFrameID(83),
                    recordingId = GPURecordingID("w4d-general-mixed-frame"),
                ),
            )
        return assertIs(
            lowered,
            (lowered as? GpuPlanLoweringResult.InvalidPlan)?.diagnostic?.message.orEmpty(),
        )
    }

    private fun graph(antiAlias: Boolean, mixedHard: Boolean = false): RenderGraph {
        val color = ColorARGB.fromPackedUInt(0xc0ff0000u)
        val scene = SceneSnapshot.of(
            SceneExtent(16, 16),
            ColorSpace.SRGB,
            listOf(
                SceneCommand.Draw(
                    DrawNode(
                        geometry = GeometryNode.Path(
                            PathBuilder().moveTo(2f, 2f).lineTo(12f, 2f).lineTo(12f, 12f)
                                .lineTo(7f, 6f).lineTo(2f, 12f).close().build(),
                        ),
                        material = MaterialNode.Solid(color),
                        coverage = if (antiAlias) CoverageRequest.ANTIALIASED else CoverageRequest.HARD_EDGE,
                        clip = ClipStackNode.Empty,
                        blend = BlendNode.SrcOver,
                        effects = EffectStack.Empty,
                        transform = Matrix3x3F32.rotation(0.25f),
                        origin = DrawOrigin.PATH,
                        paint = PaintNode(
                            color = color,
                            shader = null,
                            blendMode = BlendMode.SRC_OVER,
                            blender = null,
                            colorFilter = null,
                            maskFilter = null,
                            imageFilter = null,
                            pathEffect = null,
                            style = PaintStyleNode.FILL,
                            strokeWidth = 2f,
                            strokeCap = StrokeCapNode.BUTT,
                            strokeJoin = StrokeJoinNode.MITER,
                            strokeMiter = 4f,
                            antiAlias = antiAlias,
                        ),
                    ),
                ),
            ) + if (mixedHard) listOf(
                SceneCommand.Draw(
                    DrawNode(
                        geometry = GeometryNode.Path(
                            PathBuilder().moveTo(3f, 3f).lineTo(12f, 3f).lineTo(12f, 12f)
                                .lineTo(8f, 7f).lineTo(3f, 12f).close().build(),
                        ),
                        material = MaterialNode.Solid(color),
                        coverage = CoverageRequest.HARD_EDGE,
                        clip = ClipStackNode.Empty,
                        blend = BlendNode.SrcOver,
                        effects = EffectStack.Empty,
                        transform = Matrix3x3F32.Identity,
                        origin = DrawOrigin.PATH,
                        paint = PaintNode(
                            color = color,
                            shader = null,
                            blendMode = BlendMode.SRC_OVER,
                            blender = null,
                            colorFilter = null,
                            maskFilter = null,
                            imageFilter = null,
                            pathEffect = null,
                            style = PaintStyleNode.FILL,
                            strokeWidth = 2f,
                            strokeCap = StrokeCapNode.BUTT,
                            strokeJoin = StrokeJoinNode.MITER,
                            strokeMiter = 4f,
                            antiAlias = false,
                        ),
                    ),
                ),
            ) else emptyList(),
        )
        val compiler = W4dGeneralPathPlanCompiler()
        val candidate = assertIs<GpuPlanSelection.Candidate>(
            compiler.select(scene, RenderTargetDescriptor(scene.extent, scene.colorSpace)),
        ).candidate
        return assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, planCapabilities(), PlanBudget(1L shl 20)),
        ).plan
    }

    private fun preflight(
        frame: org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan,
        target: GPUFrameTargetRef,
    ): GPUFramePreflightResult {
        val generations = frame.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .mapIndexed { index, request -> request.resource to index.toLong() + 1L }
            .toMap()
        return GPUFramePreflighter(
            context = GPUFramePreflightContext(
                targetId = target.value,
                deviceGeneration = generation,
                targetGeneration = 1L,
                resourceGenerations = generations,
            ),
            capabilities = capabilities(),
            resourceProvider = GPUConcreteResourceProvider(),
            completionProvider = object : GPUQueueCompletionProvider {
                override fun reserveTicket(request: GPUQueueCompletionTicketRequest) =
                    GPUQueueCompletionTicketReservation.Reserved(
                        GPUQueueCompletionTicket(
                            GPUQueueCompletionTicketID("ticket.w4d-general-frame"),
                            request.frameId,
                            request.deviceGeneration,
                        ),
                    )

                override fun abandonReservedTicket(ticket: GPUQueueCompletionTicket) =
                    GPUQueueCompletionTicketAbandonResult.Abandoned(ticket.ticketId)
            },
            surfaceProvider = object : GPUSurfaceOutputProvider {
                override fun acquire(request: GPUSurfaceAcquisitionRequest): GPUSurfaceAcquisitionResult =
                    error("W4d general offscreen preflight must not acquire a surface")

                override fun release(output: GPUAcquiredSurfaceOutput) = GPUSurfaceReleaseResult.Released
            },
        ).preflight(frame)
    }

    private fun planCapabilities(): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
        deviceGeneration = generation.value,
        maxTextureDimension2D = 2048,
        maxBufferSizeBytes = 1L shl 20,
        copyBytesPerRowAlignment = 256,
        supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
        minUniformBufferOffsetAlignment = 256,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
        supportedOperations = PlanOperationCapability.entries.toSet(),
        bufferAllocationPolicy = PlanBufferAllocationPolicy.of(16_384L, 4_096L, 4_096L),
        supportedDepthStencilFormats = setOf(PlanDepthStencilFormat.Depth24PlusStencil8),
        supportedTextureSampleSupports = setOf(
            PlanTextureSampleSupport.of(
                PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
                1,
                setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource),
            ),
            PlanTextureSampleSupport.of(
                PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
                4,
                setOf(PlanResourceUsage.RenderAttachment),
            ),
            PlanTextureSampleSupport.of(
                PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8),
                1,
                setOf(PlanResourceUsage.DepthStencilAttachment),
            ),
            PlanTextureSampleSupport.of(
                PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8),
                4,
                setOf(PlanResourceUsage.DepthStencilAttachment),
            ),
            PlanTextureSampleSupport.of(
                PlanTextureFormat.CoverageMask,
                1,
                setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled),
            ),
        ),
        supportedTextureResolveSupports = setOf(
            PlanTextureResolveSupport.of(
                PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
                4,
                1,
            ),
        ),
    )

    private fun capabilities(): GPUCapabilities = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "test", "w4d-general", "device"),
        facts = emptyList(),
        snapshotId = "w4d-general-frame",
        limits = GPULimits(
            maxTextureDimension2D = 2048,
            copyBytesPerRowAlignment = 256,
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = 1L shl 20,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
        supportedTextureFormats = setOf(
            GPUTextureFormat.RGBA8UnormSrgb,
            GPUTextureFormat.RGBA8Unorm,
            GPUTextureFormat.Depth24PlusStencil8,
        ),
        supportedTextureUsage = GPUTextureUsage.RenderAttachment or
            GPUTextureUsage.TextureBinding or GPUTextureUsage.CopySrc,
        textureFormatSampleSupport = GPUTextureFormatSampleSupport(
            mapOf(
                GPUTextureFormat.RGBA8UnormSrgb to GPUTextureSampleCountSupport(
                    renderAttachmentSampleCounts = setOf(1, 4),
                    resolveSourceSampleCounts = setOf(4),
                ),
                GPUTextureFormat.RGBA8Unorm to GPUTextureSampleCountSupport(
                    renderAttachmentSampleCounts = setOf(1),
                ),
                GPUTextureFormat.Depth24PlusStencil8 to GPUTextureSampleCountSupport(
                    renderAttachmentSampleCounts = setOf(1, 4),
                ),
            ),
        ),
        rendererFeatures = setOf(
            GPURendererFeature.RenderPass,
            GPURendererFeature.CopyUpload,
            GPURendererFeature.UniformBuffer,
            GPURendererFeature.Readback,
        ),
    )

    private companion object {
        val generation = GPUDeviceGenerationID(7)
    }
}
