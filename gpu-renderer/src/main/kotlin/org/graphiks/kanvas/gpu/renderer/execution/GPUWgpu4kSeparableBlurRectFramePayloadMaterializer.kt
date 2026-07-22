package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.GPUTextureFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUSeparableBlurRectStage
import org.graphiks.kanvas.gpu.renderer.recording.SEPARABLE_BLUR_FILTER_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.SEPARABLE_BLUR_SOURCE_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.SEPARABLE_BLUR_TARGET_STATE_HASH
import org.graphiks.kanvas.gpu.renderer.recording.SEPARABLE_BLUR_VERTEX_SOURCE_LABEL
import org.graphiks.kanvas.gpu.renderer.recording.isCanonicalSolidRectSrcOver
import org.graphiks.kanvas.gpu.renderer.recording.separableBlurRectRenderStepId
import org.graphiks.kanvas.gpu.renderer.recording.separableBlurRectScissorAuthority
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedConcreteResourceRef
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

/** Public-wgpu4k materializer for the bounded source -> horizontal -> vertical blur route. */
internal class GPUWgpu4kSeparableBlurRectFramePayloadMaterializer(
    private val device: GPUDevice,
    private val queue: GPUQueue,
    private val preparedSceneTarget: GPUWgpu4kPreparedSceneTarget,
    private val sessionCache: GPUWgpu4kSeparableBlurRectSessionCache,
) : GPUPreparedNativeFramePayloadMaterializer, AutoCloseable {
    private val preRegistrationHandles = GPUPreRegistrationNativeHandleLedger()
    private var consumed = false
    private var materializing = false
    private var closed = false

    override fun materializeReusable(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedNativeFramePayloadMaterialization {
        synchronized(this) {
            if (closed || consumed) {
                return refused(
                    "unsupported.native-separable-blur.materializer-state",
                    "The separable blur materializer is one-shot and already consumed.",
                )
            }
            consumed = true
        }
        val renderSteps = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        val stageSteps = GPUSeparableBlurRectStage.entries.map { stage ->
            val step = renderSteps.singleOrNull { render ->
                render.drawPackets.singleOrNull()?.renderStepId?.value == separableBlurRectRenderStepId(stage)
            } ?: return refused(
                "unsupported.native-separable-blur.render-scope",
                "Separable blur requires one exact ${stage.wireId} render scope.",
            )
            stage to step
        }
        if (renderSteps.size != GPUSeparableBlurRectStage.entries.size ||
            stageSteps.map { framePlan.steps.indexOf(it.second) } !=
            stageSteps.map { framePlan.steps.indexOf(it.second) }.sorted()
        ) {
            return refused(
                "unsupported.native-separable-blur.render-order",
                "Separable blur requires ordered source, horizontal, and vertical render scopes.",
            )
        }
        val readbackSteps = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
        val readbackStep = readbackSteps.singleOrNull()
        if (readbackSteps.size > 1 || framePlan.steps.any { it is GPUFrameStep.CopyResourceStep }) {
            return refused(
                "unsupported.native-separable-blur.scope-shape",
                "Separable blur accepts three render scopes and at most one readback.",
            )
        }
        val renderScopes = stageSteps.map { (stage, step) ->
            val sourceStepIndex = framePlan.steps.indexOf(step)
            val scope = encoderPlan.scopes.singleOrNull {
                it.sourceStepIndex == sourceStepIndex && it.operationKind == GPUEncoderOperationKind.Render
            } ?: return refused(
                "unsupported.native-separable-blur.render-plan",
                "The ${stage.wireId} blur scope is not executable.",
            )
            stage to scope
        }
        val readbackScope = readbackStep?.let { step ->
            encoderPlan.scopes.singleOrNull {
                it.sourceStepIndex == framePlan.steps.indexOf(step) &&
                    it.operationKind == GPUEncoderOperationKind.Readback
            } ?: return refused(
                "unsupported.native-separable-blur.readback-plan",
                "The separable blur readback scope is not executable.",
            )
        }
        if (encoderPlan.scopes != (renderScopes.map { it.second } + listOfNotNull(readbackScope)).sortedBy {
                it.sourceStepIndex
            }
        ) {
            return refused(
                "unsupported.native-separable-blur.scope-order",
                "Only the ordered blur scopes and optional readback are accepted.",
            )
        }
        val semantics = stageSteps.map { (stage, step) ->
            val packet = step.drawPackets.singleOrNull() ?: return refused(
                "unsupported.native-separable-blur.packet-count",
                "The ${stage.wireId} blur scope requires exactly one packet.",
            )
            val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.SeparableBlurRect
                ?: return refused(
                    "unsupported.native-separable-blur.semantic-payload",
                    "Every blur scope requires the typed separable blur semantic payload.",
                )
            val expectedScissor = when (stage) {
                GPUSeparableBlurRectStage.Source -> semantic.sourceBounds
                GPUSeparableBlurRectStage.Horizontal,
                GPUSeparableBlurRectStage.Vertical,
                -> semantic.targetBounds
            }
            val expectedLayout = when (stage) {
                GPUSeparableBlurRectStage.Source -> SEPARABLE_BLUR_SOURCE_BINDING_LAYOUT_HASH
                GPUSeparableBlurRectStage.Horizontal,
                GPUSeparableBlurRectStage.Vertical,
                -> SEPARABLE_BLUR_FILTER_BINDING_LAYOUT_HASH
            }
            if (!semantic.hasCanonicalHashIntegrity() ||
                packet.commandIdValue != semantic.payloadRef.commandIdValue ||
                packet.uniformSlot != semantic.payloadRef.uniformSlot ||
                packet.renderPipelineKey?.value != "pipeline.separable-blur.${stage.wireId}.rgba8unorm" ||
                packet.bindingLayoutHash != expectedLayout ||
                packet.vertexSourceLabel != SEPARABLE_BLUR_VERTEX_SOURCE_LABEL ||
                packet.targetStateHash != SEPARABLE_BLUR_TARGET_STATE_HASH ||
                packet.scissorBoundsHash != separableBlurRectScissorAuthority(expectedScissor) ||
                !packet.blendPlan.isCanonicalSolidRectSrcOver()
            ) {
                return refused(
                    "unsupported.native-separable-blur.packet-authority",
                    "A blur packet contradicts its immutable stage or semantic authority.",
                )
            }
            semantic
        }
        val semantic = semantics.first()
        if (semantics.any { it.canonicalHash != semantic.canonicalHash }) {
            return refused(
                "unsupported.native-separable-blur.semantic-substitution",
                "All blur stages must retain the same immutable semantic payload.",
            )
        }
        if (stageSteps.any { (_, step) ->
                step.samplePlan != GPUSamplePlan.SingleSampleFrame ||
                    step.loadStore.loadOp != "clear" || step.loadStore.storePlan != GPUStorePlan.Store
            }
        ) {
            return refused(
                "unsupported.native-separable-blur.pass-state",
                "Every blur stage requires one single-sample clear-and-store pass.",
            )
        }
        val sourceStep = stageSteps.single { it.first == GPUSeparableBlurRectStage.Source }.second
        val horizontalStep = stageSteps.single { it.first == GPUSeparableBlurRectStage.Horizontal }.second
        val verticalStep = stageSteps.single { it.first == GPUSeparableBlurRectStage.Vertical }.second
        if (setOf(sourceStep.target, horizontalStep.target, verticalStep.target).size != 3) {
            return refused(
                "unsupported.native-separable-blur.target-alias",
                "The source, scratch, and final blur targets must be distinct.",
            )
        }
        fun sampledFilterTarget(resource: GPUFrameResourceRef) =
            GPUFrameResourceUse(
                resource,
                GPUFrameResourceRole.FilterTarget,
                GPUFrameResourceUsage.TextureBinding,
                GPUFrameResourceLifetime.FrameLocal,
                write = false,
            )
        if (sourceStep.resourceUses.isNotEmpty() ||
            horizontalStep.resourceUses != listOf(sampledFilterTarget(sourceStep.target)) ||
            verticalStep.resourceUses != listOf(sampledFilterTarget(horizontalStep.target))
        ) {
            return refused(
                "unsupported.native-separable-blur.sample-chain",
                "The blur passes must sample the exact preceding filter target without substitution.",
            )
        }
        if (readbackStep != null && readbackStep.source != verticalStep.target) {
            return refused(
                "unsupported.native-separable-blur.readback-source",
                "Separable blur readback must source the final vertical target.",
            )
        }
        val preparations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap { it.requests }
        fun preparationFor(
            step: GPUFrameStep.RenderPassStep,
            role: GPUFrameResourceRole,
            usages: Set<GPUFrameResourceUsage>,
        ): org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest? =
            preparations.singleOrNull { request ->
                request.resource == step.target && request.role == role && request.usages == usages &&
                    request.lifetime == GPUFrameResourceLifetime.FrameLocal &&
                    request.descriptor is GPUFrameTextureDescriptor
            }
        val sourcePreparation = preparationFor(
            sourceStep,
            GPUFrameResourceRole.FilterTarget,
            setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.TextureBinding),
        ) ?: return refused(
            "unsupported.native-separable-blur.source-target",
            "The source blur target declaration is missing or substituted.",
        )
        val scratchPreparation = preparationFor(
            horizontalStep,
            GPUFrameResourceRole.FilterTarget,
            setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.TextureBinding),
        ) ?: return refused(
            "unsupported.native-separable-blur.scratch-target",
            "The scratch blur target declaration is missing or substituted.",
        )
        val scenePreparation = preparationFor(
            verticalStep,
            GPUFrameResourceRole.SceneTarget,
            setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource),
        ) ?: return refused(
            "unsupported.native-separable-blur.scene-target",
            "The final blur scene target declaration is missing or substituted.",
        )
        val texturePreparations = listOf(sourcePreparation, scratchPreparation, scenePreparation)
        val descriptors = texturePreparations.map { it.descriptor as GPUFrameTextureDescriptor }
        if (descriptors.any {
                it.logicalBounds != semantic.targetBounds || it.format.value != RGBA8_UNORM || it.sampleCount != 1
            }
        ) {
            return refused(
                "unsupported.native-separable-blur.target-contract",
                "Every blur texture must match the exact rgba8unorm single-sample target bounds.",
            )
        }
        fun exactPrepared(
            preparation: org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest,
        ): Boolean {
            val expectedGeneration = generationSeal.resourceGenerations[preparation.resource] ?: return false
            return resources.ordinaryResources.singleOrNull { evidence ->
                evidence.logicalResource == preparation.resource &&
                    evidence.role == preparation.role &&
                    evidence.deviceGeneration == generationSeal.deviceGeneration &&
                    evidence.resourceGeneration == expectedGeneration &&
                    evidence.concreteResource is GPUPreparedConcreteResourceRef.Texture
            } != null
        }
        if (resources.ordinaryResources.size != 3 || texturePreparations.any { !exactPrepared(it) }) {
            return refused(
                "unsupported.native-separable-blur.prepared-resources",
                "Separable blur prepared texture evidence is missing or substituted.",
            )
        }
        if (preparedSceneTarget.width != semantic.targetBounds.width ||
            preparedSceneTarget.height != semantic.targetBounds.height ||
            preparedSceneTarget.deviceGeneration != generationSeal.deviceGeneration ||
            preparedSceneTarget.targetGeneration != generationSeal.targetGeneration
        ) {
            return refused(
                "unsupported.native-separable-blur.prepared-scene-target",
                "The session target does not match the sealed separable blur frame.",
            )
        }
        val output = resources.outputOwnedReadbacks.singleOrNull()
        if ((readbackStep == null) != (output == null) || resources.outputOwnedReadbacks.size > 1) {
            return refused(
                "unsupported.native-separable-blur.readback-output",
                "The optional blur readback must match one output-owned staging lease.",
            )
        }
        if (readbackStep != null && output != null) {
            if (output.request != readbackStep.request ||
                output.layout.width != semantic.targetBounds.width ||
                output.layout.height != semantic.targetBounds.height ||
                output.layout.unpaddedBytesPerRow != semantic.targetBounds.width.toLong() * RGBA_BYTES_PER_PIXEL ||
                output.layout.paddedBytesPerRow % WEBGPU_COPY_ROW_ALIGNMENT != 0L ||
                output.layout.totalBufferBytes > output.stagingLease.backingBufferBytes
            ) {
                return refused(
                    "unsupported.native-separable-blur.readback-layout",
                    "The blur readback layout does not cover the exact final target.",
                )
            }
        }
        synchronized(this) {
            if (closed) {
                return refused(
                    "unsupported.native-separable-blur.materializer-state",
                    "The separable blur materializer closed during validation.",
                )
            }
            materializing = true
        }
        return try {
            val (targetTexture, targetView) = preparedSceneTarget.borrow()
            val cached = sessionCache.acquire(semantic.targetBounds.width, semantic.targetBounds.height)
            val invariants = cached.invariants
            val intermediate = cached.intermediates
            val sourceUniform = createUniform(
                "Kanvas.frame.separableBlur.sourceUniform16",
                floatsToBytes(semantic.sourcePremultipliedRgba),
            )
            val blurUniform = createUniform(
                "Kanvas.frame.separableBlur.kernelUniform144",
                blurUniformBytes(semantic),
            )
            val sourceBindGroup = device.createBindGroup(
                BindGroupDescriptor(
                    label = "Kanvas.frame.separableBlur.sourceBindGroup",
                    layout = invariants.sourceBindGroupLayout,
                    entries = listOf(
                        BindGroupEntry(0u, BufferBinding(sourceUniform, 0uL, 16uL)),
                    ),
                ),
            ).tracked()
            fun blurBindGroup(label: String, view: io.ygdrasil.webgpu.GPUTextureView) =
                device.createBindGroup(
                    BindGroupDescriptor(
                        label = label,
                        layout = invariants.blurBindGroupLayout,
                        entries = listOf(
                            BindGroupEntry(0u, BufferBinding(blurUniform, 0uL, 144uL)),
                            BindGroupEntry(1u, view),
                            BindGroupEntry(2u, invariants.sampler),
                        ),
                    ),
                ).tracked()
            val horizontalBindGroup = blurBindGroup(
                "Kanvas.frame.separableBlur.horizontalBindGroup",
                intermediate.sourceView,
            )
            val verticalBindGroup = blurBindGroup(
                "Kanvas.frame.separableBlur.verticalBindGroup",
                intermediate.scratchView,
            )
            val staging = output?.let { readback ->
                device.createBuffer(
                    BufferDescriptor(
                        size = readback.stagingLease.backingBufferBytes.toULong(),
                        usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst,
                        mappedAtCreation = false,
                        label = "Kanvas.frame.separableBlur.readback",
                    ),
                ).tracked()
            }
            val borrowed = GPUPreparedNativeOperandOwnership.Borrowed
            fun renderOperand(
                stage: GPUSeparableBlurRectStage,
                target: io.ygdrasil.webgpu.GPUTextureView,
                clear: List<Float>,
                pipeline: io.ygdrasil.webgpu.GPURenderPipeline,
                bindGroups: List<GPUPreparedNativeBindGroupOperand>,
                scissor: org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds,
            ): GPUPreparedNativeScopeOperand.Render {
                val scope = renderScopes.single { it.first == stage }.second
                return GPUPreparedNativeScopeOperand.Render(
                    sourceStepIndex = scope.sourceStepIndex,
                    pass = GPUPreparedNativeRenderPassConfig(
                        colorTarget = GPUPreparedNativeTextureViewOperand(
                            target,
                            generationSeal.deviceGeneration,
                            borrowed,
                        ),
                        resolveTarget = null,
                        loadOperation = GPUPreparedNativeLoadOperation.Clear,
                        storeOperation = GPUPreparedNativeStoreOperation.Store,
                        clearColor = GPUPreparedNativeClearColor(
                            clear[0].toDouble(),
                            clear[1].toDouble(),
                            clear[2].toDouble(),
                            clear[3].toDouble(),
                        ),
                    ),
                    commands = buildList {
                        add(
                            GPUPreparedNativeRenderCommand.SetPipeline(
                                GPUPreparedNativeRenderPipelineOperand(
                                    pipeline,
                                    generationSeal.deviceGeneration,
                                    borrowed,
                                ),
                            ),
                        )
                        bindGroups.forEachIndexed { index, bindGroup ->
                            add(GPUPreparedNativeRenderCommand.SetBindGroup(index, bindGroup))
                        }
                        add(
                            GPUPreparedNativeRenderCommand.SetScissor(
                                scissor.left,
                                scissor.top,
                                scissor.width,
                                scissor.height,
                            ),
                        )
                        add(GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3)))
                    },
                    semanticPayloads = listOf(semantic),
                )
            }
            fun bindGroupOperand(group: io.ygdrasil.webgpu.GPUBindGroup) =
                GPUPreparedNativeBindGroupOperand(group, generationSeal.deviceGeneration, borrowed)
            val transparent = listOf(0f, 0f, 0f, 0f)
            val sourceOperand = renderOperand(
                GPUSeparableBlurRectStage.Source,
                intermediate.sourceView,
                transparent,
                invariants.sourcePipeline,
                listOf(bindGroupOperand(sourceBindGroup)),
                semantic.sourceBounds,
            )
            val horizontalOperand = renderOperand(
                GPUSeparableBlurRectStage.Horizontal,
                intermediate.scratchView,
                transparent,
                invariants.horizontalPipeline,
                listOf(bindGroupOperand(horizontalBindGroup)),
                semantic.targetBounds,
            )
            val verticalOperand = renderOperand(
                GPUSeparableBlurRectStage.Vertical,
                targetView,
                semantic.clearPremultipliedRgba,
                invariants.verticalPipeline,
                listOf(bindGroupOperand(verticalBindGroup)),
                semantic.targetBounds,
            )
            val readbackOperand = if (readbackScope != null && output != null && staging != null) {
                GPUPreparedNativeScopeOperand.Readback(
                    sourceStepIndex = readbackScope.sourceStepIndex,
                    source = GPUPreparedNativeTextureOperand(
                        targetTexture,
                        generationSeal.deviceGeneration,
                        borrowed,
                    ),
                    destination = GPUPreparedNativeBufferOperand(
                        staging,
                        generationSeal.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.OutputOwnedReadback,
                    ),
                    layout = GPUPreparedNativeReadbackLayout(
                        originX = output.request.sourceBounds.left,
                        originY = output.request.sourceBounds.top,
                        width = output.layout.width,
                        height = output.layout.height,
                        bytesPerRow = output.layout.paddedBytesPerRow,
                        rowsPerImage = output.layout.rowsPerImage,
                        bufferOffset = output.layout.bufferOffset,
                        mappedSize = output.layout.totalBufferBytes,
                        format = GPUTextureFormat.RGBA8Unorm,
                    ),
                )
            } else {
                null
            }
            val operandByStepIndex = buildMap<Int, GPUPreparedNativeScopeOperand> {
                listOf(sourceOperand, horizontalOperand, verticalOperand).forEach { put(it.sourceStepIndex, it) }
                readbackOperand?.let { put(it.sourceStepIndex, it) }
            }
            val payload = GPUPreparedNativeFramePayload(
                identity = GPUPreparedNativeFrameIdentity(
                    framePlan.frameId,
                    encoderPlan.contextIdentity,
                    encoderPlan.planId,
                    generationSeal.deviceGeneration,
                    generationSeal.targetGeneration,
                    encoderPlan.scopes.map { scope ->
                        GPUPreparedNativeScopeKey(
                            scope.sourceStepIndex,
                            scope.operationKind,
                            scope.resourceGenerationLabels,
                            scope.nativeOperandKeys,
                        )
                    },
                ),
                scopeOperands = encoderPlan.scopes.map { scope ->
                    checkNotNull(operandByStepIndex[scope.sourceStepIndex])
                },
                scopeOperandKeys = encoderPlan.scopes.map { it.nativeOperandKeys },
                auxiliaryOwnedHandles = buildList {
                    add(
                        GPUPreparedNativeAuxiliaryHandle(
                            GPUPreparedNativeCompletionAnchor(
                                listOf(sourceBindGroup, horizontalBindGroup, verticalBindGroup),
                            ),
                            GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                        ),
                    )
                    add(
                        GPUPreparedNativeAuxiliaryHandle(
                            sourceUniform,
                            GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                        ),
                    )
                    add(
                        GPUPreparedNativeAuxiliaryHandle(
                            blurUniform,
                            GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
                        ),
                    )
                },
            )
            val result = GPUPreparedNativeFramePayloadMaterialization.Materialized(
                GPUPreparedNativeFrameDraft(payload),
            )
            synchronized(this) {
                check(!closed) { "Separable blur materializer closed during materialization" }
                preRegistrationHandles.transferAll()
                materializing = false
            }
            result
        } catch (failure: Throwable) {
            synchronized(this) {
                materializing = false
                preRegistrationHandles.closeRetainingFailures()
            }
            refused(
                "failed.native-separable-blur.materialization",
                "Public wgpu4k separable blur materialization failed: " +
                    "${failure::class.simpleName.orEmpty()}: ${failure.message.orEmpty()}.",
            )
        }
    }

    override fun bindLateSurface(
        draft: GPUPreparedNativeFrameDraft,
        acquiredSurface: GPUAcquiredSurfaceOutput?,
    ): GPUPreparedNativeFrameLateSurfaceBinding = if (acquiredSurface == null) {
        GPUPreparedNativeFrameLateSurfaceBinding.NotRequired
    } else {
        GPUPreparedNativeFrameLateSurfaceBinding.Refused(
            "unsupported.native-separable-blur.surface",
            "The separable blur route is offscreen-only.",
        )
    }

    override fun close() {
        synchronized(this) {
            closed = true
            if (!materializing) preRegistrationHandles.closeRetainingFailures()
        }
    }

    private fun createUniform(label: String, bytes: ByteArray) = device.createBuffer(
        BufferDescriptor(
            size = bytes.size.toULong(),
            usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
            label = label,
        ),
    ).tracked().also { buffer ->
        queue.writeBuffer(buffer, 0uL, ArrayBuffer.of(bytes))
    }

    private fun floatsToBytes(values: List<Float>): ByteArray =
        ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            values.forEach(::putFloat)
        }.array()

    private fun blurUniformBytes(semantic: GPUDrawSemanticPayload.SeparableBlurRect): ByteArray =
        ByteBuffer.allocate(144).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(semantic.tapCount)
            putInt(0)
            putFloat(semantic.targetBounds.width.toFloat())
            putFloat(semantic.targetBounds.height.toFloat())
            repeat(4) { putFloat(0f) }
            semantic.weights.forEach(::putFloat)
            repeat(3) { putFloat(0f) }
        }.array()

    private fun refused(code: String, message: String) =
        refusedWgpu4kPreRegistrationMaterialization(code, message, preRegistrationHandles)

    private fun <T : AutoCloseable> T.tracked(): T = preRegistrationHandles.track(this)

    private companion object {
        const val RGBA8_UNORM = "rgba8unorm"
        const val RGBA_BYTES_PER_PIXEL = 4L
        const val WEBGPU_COPY_ROW_ALIGNMENT = 256L
    }
}
