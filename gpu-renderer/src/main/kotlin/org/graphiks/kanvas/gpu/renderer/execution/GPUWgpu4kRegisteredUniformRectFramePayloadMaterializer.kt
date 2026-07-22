package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.TextureDescriptor
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPURegisteredUniformProgram
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.REGISTERED_UNIFORM_RECT_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.REGISTERED_UNIFORM_RECT_TARGET_STATE_HASH
import org.graphiks.kanvas.gpu.renderer.recording.REGISTERED_UNIFORM_RECT_VERTEX_SOURCE_LABEL
import org.graphiks.kanvas.gpu.renderer.recording.isCanonicalSolidRectSrcOver
import org.graphiks.kanvas.gpu.renderer.recording.registeredUniformRectPipelineKey
import org.graphiks.kanvas.gpu.renderer.recording.registeredUniformRectScissorAuthority
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedConcreteResourceRef
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

/** Public-wgpu4k materializer for one heterogeneous batch of closed uniform programs. */
internal class GPUWgpu4kRegisteredUniformRectFramePayloadMaterializer(
    private val device: GPUDevice,
    private val queue: GPUQueue,
    private val preparedSceneTarget: GPUWgpu4kPreparedSceneTarget? = null,
    private val sessionCache: GPUWgpu4kRegisteredUniformRectSessionCache? = null,
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
                    "unsupported.native-registered-uniform.materializer-state",
                    "The registered uniform materializer is one-shot and already consumed.",
                )
            }
            consumed = true
        }
        val renderStep = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().singleOrNull()
            ?: return refused(
                "unsupported.native-registered-uniform.render-scope",
                "Registered uniform execution requires exactly one render scope.",
            )
        val readbackStep = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>().singleOrNull()
        if (framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>().size > 1 ||
            framePlan.steps.any { it is GPUFrameStep.CopyResourceStep }
        ) {
            return refused(
                "unsupported.native-registered-uniform.scope-shape",
                "Registered uniform execution accepts one render and at most one readback.",
            )
        }
        val renderScope = encoderPlan.scopes.singleOrNull {
            it.sourceStepIndex == framePlan.steps.indexOf(renderStep) &&
                it.operationKind == GPUEncoderOperationKind.Render
        } ?: return refused(
            "unsupported.native-registered-uniform.render-plan",
            "The registered uniform render scope is not executable.",
        )
        val readbackScope = readbackStep?.let { step ->
            encoderPlan.scopes.singleOrNull {
                it.sourceStepIndex == framePlan.steps.indexOf(step) &&
                    it.operationKind == GPUEncoderOperationKind.Readback
            } ?: return refused(
                "unsupported.native-registered-uniform.readback-plan",
                "The registered uniform readback scope is not executable.",
            )
        }
        if (encoderPlan.scopes != listOfNotNull(renderScope, readbackScope).sortedBy { it.sourceStepIndex }) {
            return refused(
                "unsupported.native-registered-uniform.scope-order",
                "Only the ordered render and optional readback scopes are accepted.",
            )
        }
        if (readbackStep != null && readbackStep.source != renderStep.target) {
            return refused(
                "unsupported.native-registered-uniform.target-substitution",
                "The render target and readback source must be the same target.",
            )
        }
        if (renderStep.samplePlan != GPUSamplePlan.SingleSampleFrame ||
            renderStep.loadStore.loadOp != "clear" || renderStep.loadStore.storePlan != GPUStorePlan.Store
        ) {
            return refused(
                "unsupported.native-registered-uniform.pass-state",
                "Registered uniform execution requires one single-sample clear-and-store pass.",
            )
        }
        val semanticPackets = renderStep.drawPackets.map { packet ->
            val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.RegisteredUniformRect
                ?: return refused(
                    "unsupported.native-registered-uniform.semantic-payload",
                    "Every packet requires one registered uniform semantic payload.",
                )
            packet to semantic
        }
        if (semanticPackets.isEmpty()) {
            return refused(
                "unsupported.native-registered-uniform.empty",
                "Registered uniform execution requires at least one packet.",
            )
        }
        val supportedPrograms = setOf(
            GPURegisteredUniformProgram.SolidColor,
            GPURegisteredUniformProgram.LinearGradient,
            GPURegisteredUniformProgram.RadialGradient,
            GPURegisteredUniformProgram.SweepGradient,
            GPURegisteredUniformProgram.ColorMatrix,
            GPURegisteredUniformProgram.SimpleRuntimeEffect,
        )
        if (semanticPackets.any { (_, semantic) -> semantic.program !in supportedPrograms }) {
            return refused(
                "unsupported.native-registered-uniform.program",
                "This slice supports solid color, registered two-stop gradients, ColorMatrix, and SimpleRT.",
            )
        }
        if (semanticPackets.any { (packet, semantic) ->
                !semantic.hasCanonicalHashIntegrity() ||
                    packet.commandIdValue != semantic.payloadRef.commandIdValue ||
                    packet.uniformSlot != semantic.payloadRef.uniformSlot ||
                    packet.renderPipelineKey != registeredUniformRectPipelineKey(semantic.program) ||
                    packet.bindingLayoutHash != REGISTERED_UNIFORM_RECT_BINDING_LAYOUT_HASH ||
                    packet.vertexSourceLabel != REGISTERED_UNIFORM_RECT_VERTEX_SOURCE_LABEL ||
                    packet.targetStateHash != REGISTERED_UNIFORM_RECT_TARGET_STATE_HASH ||
                    packet.scissorBoundsHash != registeredUniformRectScissorAuthority(semantic.scissorBounds) ||
                    !packet.blendPlan.isCanonicalSolidRectSrcOver()
            }
        ) {
            return refused(
                "unsupported.native-registered-uniform.packet-authority",
                "A packet contradicts its immutable program, uniform, blend, or scissor authority.",
            )
        }

        val targetPreparation = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap { it.requests }
            .singleOrNull { it.resource == renderStep.target }
            ?: return refused(
                "unsupported.native-registered-uniform.target-declaration",
                "The render target requires one exact preparation declaration.",
            )
        val targetDescriptor = targetPreparation.descriptor as? GPUFrameTextureDescriptor
        if (targetPreparation.role != GPUFrameResourceRole.SceneTarget || targetDescriptor == null ||
            targetDescriptor.format.value != RGBA8_UNORM || targetDescriptor.sampleCount != 1 ||
            targetPreparation.usages != setOf(
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceUsage.CopySource,
            ) || targetPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal
        ) {
            return refused(
                "unsupported.native-registered-uniform.target-contract",
                "Registered uniform execution requires one frame-local rgba8unorm scene target.",
            )
        }
        val targetBounds = targetDescriptor.logicalBounds
        if (targetBounds.left != 0 || targetBounds.top != 0 ||
            semanticPackets.any { (_, semantic) -> semantic.targetBounds != targetBounds }
        ) {
            return refused(
                "unsupported.native-registered-uniform.target-bounds",
                "Every semantic payload must name the exact zero-origin target bounds.",
            )
        }
        val expectedTargetGeneration = generationSeal.resourceGenerations[renderStep.target]
            ?: return refused(
                "unsupported.native-registered-uniform.prepared-target",
                "The registered uniform target has no sealed resource generation.",
            )
        val preparedTarget = resources.ordinaryResources.singleOrNull {
            it.logicalResource == renderStep.target &&
                it.role == GPUFrameResourceRole.SceneTarget &&
                it.deviceGeneration == generationSeal.deviceGeneration &&
                it.resourceGeneration == expectedTargetGeneration &&
                it.concreteResource is GPUPreparedConcreteResourceRef.Texture
        } ?: return refused(
            "unsupported.native-registered-uniform.prepared-target",
            "The exact prepared target evidence is missing or substituted.",
        )
        if (preparedTarget.concreteResource !is GPUPreparedConcreteResourceRef.Texture) {
            return refused(
                "unsupported.native-registered-uniform.prepared-target",
                "The prepared target must resolve to one texture.",
            )
        }
        preparedSceneTarget?.let { target ->
            if (target.width != targetBounds.width || target.height != targetBounds.height ||
                target.deviceGeneration != generationSeal.deviceGeneration ||
                target.targetGeneration != generationSeal.targetGeneration
            ) {
                return refused(
                    "unsupported.native-registered-uniform.prepared-scene-target-incompatible",
                    "The session target does not match the sealed registered uniform frame.",
                )
            }
        }

        val output = resources.outputOwnedReadbacks.singleOrNull()
        if ((readbackStep == null) != (output == null) || resources.outputOwnedReadbacks.size > 1) {
            return refused(
                "unsupported.native-registered-uniform.readback-output",
                "The optional readback must match one output-owned staging lease.",
            )
        }
        if (readbackStep != null && output != null) {
            if (output.request != readbackStep.request || readbackStep.request.sourceBounds != targetBounds ||
                output.layout.width != targetBounds.width || output.layout.height != targetBounds.height ||
                output.layout.unpaddedBytesPerRow != targetBounds.width.toLong() * RGBA_BYTES_PER_PIXEL ||
                output.layout.totalBufferBytes > output.stagingLease.backingBufferBytes ||
                output.layout.paddedBytesPerRow % WEBGPU_COPY_ROW_ALIGNMENT != 0L
            ) {
                return refused(
                    "unsupported.native-registered-uniform.readback-layout",
                    "The output-owned RGBA8 readback layout is not executable.",
                )
            }
        }

        synchronized(this) {
            if (closed) {
                return refused(
                    "unsupported.native-registered-uniform.materializer-state",
                    "The registered uniform materializer closed during validation.",
                )
            }
            materializing = true
        }
        var ephemeralCache: GPUWgpu4kRegisteredUniformRectSessionCache? = null
        return try {
            val preparedNativeTarget = preparedSceneTarget?.borrow()
            val targetTexture = preparedNativeTarget?.first ?: device.createTexture(
                TextureDescriptor(
                    size = Extent3D(targetBounds.width.toUInt(), targetBounds.height.toUInt()),
                    format = GPUTextureFormat.RGBA8Unorm,
                    usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.CopySrc,
                    label = "Kanvas.frame.registeredUniform.target",
                ),
            ).tracked()
            val targetView = preparedNativeTarget?.second ?: targetTexture.createView().tracked()
            val ownsTarget = preparedNativeTarget == null
            val uniformBuffers = semanticPackets.mapIndexed { index, (_, semantic) ->
                device.createBuffer(
                    BufferDescriptor(
                        size = semantic.program.uniformByteSize.toULong(),
                        usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                        label = "Kanvas.frame.registeredUniform.${semantic.program.wireId}.uniform.$index",
                    ),
                ).tracked().also { buffer ->
                    queue.writeBuffer(
                        buffer,
                        0uL,
                        ArrayBuffer.of(ByteArray(semantic.uniformBytes.size) { semantic.uniformBytes[it].toByte() }),
                    )
                }
            }
            val staging = output?.let { readbackOutput ->
                device.createBuffer(
                    BufferDescriptor(
                        size = readbackOutput.stagingLease.backingBufferBytes.toULong(),
                        usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst,
                        mappedAtCreation = false,
                        label = "Kanvas.frame.registeredUniform.readback",
                    ),
                ).tracked()
            }
            val effectiveCache = sessionCache ?: GPUWgpu4kRegisteredUniformRectSessionCache(device).also {
                ephemeralCache = it
            }
            val invariants = semanticPackets.map { (_, semantic) ->
                effectiveCache.acquire(
                    GPUWgpu4kRegisteredUniformRectPipelineCacheKey(
                        semantic.program,
                        targetDescriptor.format.value,
                        1,
                    ),
                )
            }
            val bindGroups = semanticPackets.indices.map { index ->
                val semantic = semanticPackets[index].second
                device.createBindGroup(
                    BindGroupDescriptor(
                        label = "Kanvas.frame.registeredUniform.bindGroup.$index",
                        layout = invariants[index].bindGroupLayout,
                        entries = listOf(
                            BindGroupEntry(
                                binding = 0u,
                                resource = BufferBinding(
                                    uniformBuffers[index],
                                    0uL,
                                    semantic.program.uniformByteSize.toULong(),
                                ),
                            ),
                        ),
                    ),
                ).tracked()
            }
            val borrowed = GPUPreparedNativeOperandOwnership.Borrowed
            val completionOwned = GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion
            val renderOperand = GPUPreparedNativeScopeOperand.Render(
                sourceStepIndex = renderScope.sourceStepIndex,
                pass = GPUPreparedNativeRenderPassConfig(
                    colorTarget = GPUPreparedNativeTextureViewOperand(
                        targetView,
                        generationSeal.deviceGeneration,
                        borrowed,
                    ),
                    resolveTarget = null,
                    loadOperation = GPUPreparedNativeLoadOperation.Clear,
                    storeOperation = GPUPreparedNativeStoreOperation.Store,
                    clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0),
                ),
                commands = buildList {
                    semanticPackets.indices.forEach { index ->
                        val semantic = semanticPackets[index].second
                        val scissor = semantic.scissorBounds
                        add(
                            GPUPreparedNativeRenderCommand.SetPipeline(
                                GPUPreparedNativeRenderPipelineOperand(
                                    invariants[index].pipeline,
                                    generationSeal.deviceGeneration,
                                    borrowed,
                                ),
                            ),
                        )
                        add(
                            GPUPreparedNativeRenderCommand.SetBindGroup(
                                0,
                                GPUPreparedNativeBindGroupOperand(
                                    bindGroups[index],
                                    generationSeal.deviceGeneration,
                                    borrowed,
                                ),
                            ),
                        )
                        add(
                            GPUPreparedNativeRenderCommand.SetScissor(
                                scissor.left - targetBounds.left,
                                scissor.top - targetBounds.top,
                                scissor.width,
                                scissor.height,
                            ),
                        )
                        add(
                            GPUPreparedNativeRenderCommand.Draw(
                                GPUPreparedNativeDrawCall.Draw(vertexCount = 3),
                            ),
                        )
                    }
                },
                semanticPayloads = semanticPackets.map { it.second },
            )
            val targetTextureOperand = GPUPreparedNativeTextureOperand(
                targetTexture,
                generationSeal.deviceGeneration,
                borrowed,
            )
            val readbackOperand = if (readbackScope != null && output != null && staging != null) {
                GPUPreparedNativeScopeOperand.Readback(
                    sourceStepIndex = readbackScope.sourceStepIndex,
                    source = targetTextureOperand,
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
                put(renderOperand.sourceStepIndex, renderOperand)
                readbackOperand?.let { put(it.sourceStepIndex, it) }
            }
            val payload = GPUPreparedNativeFramePayload(
                identity = GPUPreparedNativeFrameIdentity(
                    frameId = framePlan.frameId,
                    contextIdentity = encoderPlan.contextIdentity,
                    encoderPlanId = encoderPlan.planId,
                    deviceGeneration = generationSeal.deviceGeneration,
                    targetGeneration = generationSeal.targetGeneration,
                    scopes = encoderPlan.scopes.map { scope ->
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
                                listOfNotNull(targetView.takeIf { ownsTarget }) + bindGroups,
                            ),
                            completionOwned,
                        ),
                    )
                    uniformBuffers.forEach { buffer ->
                        add(GPUPreparedNativeAuxiliaryHandle(buffer, completionOwned))
                    }
                    ephemeralCache?.let { cache ->
                        add(GPUPreparedNativeAuxiliaryHandle(cache, completionOwned))
                    }
                    targetTexture.takeIf { ownsTarget }?.let { texture ->
                        add(GPUPreparedNativeAuxiliaryHandle(texture, completionOwned))
                    }
                },
            )
            val result = GPUPreparedNativeFramePayloadMaterialization.Materialized(
                GPUPreparedNativeFrameDraft(payload),
            )
            synchronized(this) {
                check(!closed) { "Registered uniform materializer closed during materialization" }
                preRegistrationHandles.transferAll()
                materializing = false
            }
            result
        } catch (failure: Throwable) {
            val retainedCache = ephemeralCache?.takeIf { cache ->
                runCatching { cache.close() }.isFailure
            }
            synchronized(this) {
                materializing = false
                preRegistrationHandles.closeRetainingFailures()
            }
            refused(
                "failed.native-registered-uniform.materialization",
                "Public wgpu4k registered uniform materialization failed: " +
                    "${failure::class.simpleName.orEmpty()}: ${failure.message.orEmpty()}.",
                retainedCache,
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
            "unsupported.native-registered-uniform.surface",
            "The registered uniform route is offscreen-only.",
        )
    }

    override fun close() {
        synchronized(this) {
            closed = true
            if (!materializing) preRegistrationHandles.closeRetainingFailures()
        }
    }

    private fun refused(
        code: String,
        message: String,
        retainedCloseOwner: AutoCloseable? = null,
    ) = refusedWgpu4kPreRegistrationMaterialization(
        code, message, preRegistrationHandles, retainedCloseOwner,
    )

    private fun <T : AutoCloseable> T.tracked(): T = preRegistrationHandles.track(this)

    private companion object {
        const val RGBA8_UNORM = "rgba8unorm"
        const val RGBA_BYTES_PER_PIXEL = 4L
        const val WEBGPU_COPY_ROW_ALIGNMENT = 256L
    }
}
