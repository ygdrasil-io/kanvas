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
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphAtlasFormat
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.COLOR_GLYPH_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.COLOR_GLYPH_RENDER_PIPELINE_KEY
import org.graphiks.kanvas.gpu.renderer.recording.COLOR_GLYPH_TARGET_STATE_HASH
import org.graphiks.kanvas.gpu.renderer.recording.COLOR_GLYPH_VERTEX_SOURCE_LABEL
import org.graphiks.kanvas.gpu.renderer.recording.colorGlyphScissorAuthority
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedConcreteResourceRef
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

/** Public-wgpu4k materializer for one canonical indexed COLRv0 color-glyph pass. */
internal class GPUWgpu4kColorGlyphFramePayloadMaterializer(
    private val device: GPUDevice,
    private val queue: GPUQueue,
    private val preparedSceneTarget: GPUWgpu4kPreparedSceneTarget,
    private val sessionCache: GPUWgpu4kColorGlyphSessionCache,
) : GPUPreparedNativeFramePayloadMaterializer, AutoCloseable {
    private val preRegistrationHandles = GPUPreRegistrationNativeHandleLedger()
    private var materialized = false
    private var materializing = false
    private var closed = false

    override fun materializeReusable(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedNativeFramePayloadMaterialization {
        synchronized(this) {
            if (closed || materialized) {
                return refused(
                    "unsupported.native-color-glyph.materializer-state",
                    "The native ColorGlyph materializer is one-shot and already consumed.",
                )
            }
            materialized = true
        }

        val renderStep = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().singleOrNull()
            ?: return refused(
                "unsupported.native-color-glyph.render-scope",
                "ColorGlyph requires exactly one render scope.",
            )
        val packet = renderStep.drawPackets.singleOrNull()
            ?: return refused(
                "unsupported.native-color-glyph.packet-count",
                "ColorGlyph requires exactly one indexed draw packet.",
            )
        val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.ColorGlyph
            ?: return refused(
                "unsupported.native-color-glyph.semantic-payload",
                "ColorGlyph requires one canonical typed semantic payload.",
            )
        if (!semantic.hasCanonicalHashIntegrity()) {
            return refused(
                "invalid.native-color-glyph.canonical-hash",
                "ColorGlyph canonical payload integrity changed after preflight.",
            )
        }
        if (packet.renderPipelineKey != COLOR_GLYPH_RENDER_PIPELINE_KEY ||
            packet.bindingLayoutHash != COLOR_GLYPH_BINDING_LAYOUT_HASH ||
            packet.vertexSourceLabel != COLOR_GLYPH_VERTEX_SOURCE_LABEL ||
            packet.targetStateHash != COLOR_GLYPH_TARGET_STATE_HASH ||
            packet.scissorBoundsHash != colorGlyphScissorAuthority(semantic.scissorBounds) ||
            renderStep.loadStore.loadOp != "clear" ||
            renderStep.loadStore.storePlan != GPUStorePlan.Store ||
            renderStep.loadStore.clearColorLabel != "opaque-black"
        ) {
            return refused(
                "invalid.native-color-glyph.packet-authority",
                "ColorGlyph packet and pass state differ from the exact prepared native route authority.",
            )
        }
        val readbackStep = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>().singleOrNull()
        if (framePlan.steps.count { it.executionKind == org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStepExecutionKind.Encoder } !=
            1 + if (readbackStep == null) 0 else 1
        ) {
            return refused(
                "unsupported.native-color-glyph.scope-shape",
                "ColorGlyph accepts only one render and one optional readback encoder scope.",
            )
        }
        val renderStepIndex = framePlan.steps.indexOf(renderStep)
        val renderScope = encoderPlan.scopes.singleOrNull {
            it.sourceStepIndex == renderStepIndex && it.operationKind == GPUEncoderOperationKind.Render
        } ?: return refused(
            "unsupported.native-color-glyph.render-plan",
            "ColorGlyph render scope is absent from the encoder plan.",
        )
        val readbackScope = readbackStep?.let { step ->
            encoderPlan.scopes.singleOrNull {
                it.sourceStepIndex == framePlan.steps.indexOf(step) &&
                    it.operationKind == GPUEncoderOperationKind.Readback
            } ?: return refused(
                "unsupported.native-color-glyph.readback-plan",
                "ColorGlyph readback scope is absent from the encoder plan.",
            )
        }
        if (encoderPlan.scopes != listOfNotNull(renderScope, readbackScope)) {
            return refused(
                "unsupported.native-color-glyph.scope-order",
                "ColorGlyph encoder scopes must be render then optional readback.",
            )
        }
        if (renderStep.samplePlan != org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan.SingleSampleFrame ||
            renderStep.sampleContinuation != null || renderStep.loadStore.loadOp != "clear" ||
            renderStep.loadStore.storePlan != GPUStorePlan.Store ||
            renderStep.loadStore.clearColorLabel != "opaque-black"
        ) {
            return refused(
                "unsupported.native-color-glyph.render-state",
                "ColorGlyph requires one opaque-black clear, stored, single-sample pass.",
            )
        }

        val preparations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap { it.requests }
        if (preparations.size != 5 + if (readbackStep == null) 0 else 1) {
            return refused(
                "unsupported.native-color-glyph.resource-count",
                "ColorGlyph requires five prepared resources plus optional readback staging.",
            )
        }
        fun preparation(role: GPUFrameResourceRole) = preparations.filter { it.role == role }.singleOrNull()
        val targetPreparation = preparation(GPUFrameResourceRole.SceneTarget)
            ?: return refused("unsupported.native-color-glyph.target", "ColorGlyph target declaration is missing.")
        val atlasPreparation = preparation(GPUFrameResourceRole.GlyphAtlas)
            ?: return refused("unsupported.native-color-glyph.atlas", "ColorGlyph atlas declaration is missing.")
        val vertexPreparation = preparation(GPUFrameResourceRole.VertexData)
            ?: return refused("unsupported.native-color-glyph.vertex", "ColorGlyph vertex declaration is missing.")
        val indexPreparation = preparation(GPUFrameResourceRole.IndexData)
            ?: return refused("unsupported.native-color-glyph.index", "ColorGlyph index declaration is missing.")
        val uniformPreparation = preparation(GPUFrameResourceRole.UniformData)
            ?: return refused("unsupported.native-color-glyph.uniform", "ColorGlyph uniform declaration is missing.")
        val stagingPreparation = preparation(GPUFrameResourceRole.ReadbackStaging)
        if ((readbackStep == null) != (stagingPreparation == null)) {
            return refused(
                "unsupported.native-color-glyph.readback-resource",
                "ColorGlyph staging must exist exactly when readback is requested.",
            )
        }

        val targetDescriptor = targetPreparation.descriptor as? GPUFrameTextureDescriptor
        val atlasDescriptor = atlasPreparation.descriptor as? GPUFrameTextureDescriptor
        val vertexDescriptor = vertexPreparation.descriptor as? GPUFrameBufferDescriptor
        val indexDescriptor = indexPreparation.descriptor as? GPUFrameBufferDescriptor
        val uniformDescriptor = uniformPreparation.descriptor as? GPUFrameBufferDescriptor
        if (targetPreparation.resource != renderStep.target || targetDescriptor == null ||
            targetDescriptor.logicalBounds != semantic.targetBounds || targetDescriptor.format.value != RGBA8_UNORM ||
            targetDescriptor.sampleCount != 1 ||
            targetPreparation.usages != setOf(GPUFrameResourceUsage.RenderAttachment, GPUFrameResourceUsage.CopySource) ||
            targetPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal
        ) {
            return refused(
                "unsupported.native-color-glyph.target-contract",
                "ColorGlyph target declaration differs from its exact RGBA8 semantic target.",
            )
        }
        if (atlasDescriptor == null || atlasDescriptor.format.value != R8_UNORM || atlasDescriptor.sampleCount != 1 ||
            atlasDescriptor.logicalBounds.left != 0 || atlasDescriptor.logicalBounds.top != 0 ||
            atlasDescriptor.logicalBounds.width != semantic.atlasWidth ||
            atlasDescriptor.logicalBounds.height != semantic.atlasHeight ||
            atlasPreparation.usages != setOf(
                GPUFrameResourceUsage.TextureBinding,
                GPUFrameResourceUsage.CopyDestination,
            ) || atlasPreparation.lifetime != GPUFrameResourceLifetime.SharedCache ||
            atlasPreparation.byteSize != semantic.atlasA8Bytes.size.toLong() ||
            semantic.atlasFormat != GPUColorGlyphAtlasFormat.R8Unorm
        ) {
            return refused(
                "unsupported.native-color-glyph.atlas-contract",
                "ColorGlyph atlas declaration differs from its exact shared-cache R8 payload.",
            )
        }
        val vertexBytes = semantic.vertexData.size.toLong() * Float.SIZE_BYTES
        val indexBytes = semantic.indexData.size.toLong() * Int.SIZE_BYTES
        val uniformBytes = semantic.uniformBytes.size.toLong()
        fun exactBuffer(
            preparation: org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest,
            descriptor: GPUFrameBufferDescriptor?,
            usage: GPUFrameResourceUsage,
            bytes: Long,
            alignment: Long,
        ): Boolean = descriptor != null && descriptor.byteSize == bytes && descriptor.alignmentBytes == alignment &&
            preparation.byteSize == bytes && preparation.usages == setOf(usage, GPUFrameResourceUsage.CopyDestination) &&
            preparation.lifetime == GPUFrameResourceLifetime.FrameLocal
        if (!exactBuffer(vertexPreparation, vertexDescriptor, GPUFrameResourceUsage.Vertex, vertexBytes, 4L) ||
            !exactBuffer(indexPreparation, indexDescriptor, GPUFrameResourceUsage.Index, indexBytes, 4L) ||
            !exactBuffer(uniformPreparation, uniformDescriptor, GPUFrameResourceUsage.Uniform, uniformBytes, 16L) ||
            uniformBytes != CANONICAL_UNIFORM_BYTES || semantic.indexData.isEmpty() ||
            semantic.vertexData.isEmpty() || semantic.vertexData.size % 4 != 0
        ) {
            return refused(
                "unsupported.native-color-glyph.buffer-contract",
                "ColorGlyph vertex, uint32 index, or 784-byte uniform contract is not exact.",
            )
        }
        if (semantic.scissorBounds.isEmpty ||
            semantic.scissorBounds.left < semantic.targetBounds.left ||
            semantic.scissorBounds.top < semantic.targetBounds.top ||
            semantic.scissorBounds.right > semantic.targetBounds.right ||
            semantic.scissorBounds.bottom > semantic.targetBounds.bottom
        ) {
            return refused(
                "unsupported.native-color-glyph.scissor",
                "ColorGlyph scissor must be non-empty and contained by its target.",
            )
        }

        val planGeneration = semantic.planArtifactKey.generation.value.toLong()
        val exactGenerations = mapOf(
            targetPreparation.resource to generationSeal.targetGeneration,
            atlasPreparation.resource to semantic.atlasGeneration,
            vertexPreparation.resource to planGeneration,
            indexPreparation.resource to planGeneration,
            uniformPreparation.resource to planGeneration,
        ) + listOfNotNull(stagingPreparation).associate { it.resource to requireNotNull(generationSeal.resourceGenerations[it.resource]) }
        if (packet.resourceGeneration != planGeneration ||
            exactGenerations.any { (resource, generation) -> generationSeal.resourceGenerations[resource] != generation }
        ) {
            return refused(
                "stale.native-color-glyph.resource-generation",
                "ColorGlyph native resource generations differ from the immutable payload.",
            )
        }
        fun exactPrepared(
            role: GPUFrameResourceRole,
            preparation: org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest,
            concreteKind: Class<out GPUPreparedConcreteResourceRef>,
        ): Boolean {
            val evidence = resources.ordinaryResources.singleOrNull { it.logicalResource == preparation.resource }
                ?: return false
            return evidence.role == role && evidence.deviceGeneration == generationSeal.deviceGeneration &&
                evidence.resourceGeneration == generationSeal.resourceGenerations.getValue(preparation.resource) &&
                concreteKind.isInstance(evidence.concreteResource)
        }
        if (resources.ordinaryResources.size != 5 ||
            !exactPrepared(GPUFrameResourceRole.SceneTarget, targetPreparation, GPUPreparedConcreteResourceRef.Texture::class.java) ||
            !exactPrepared(GPUFrameResourceRole.GlyphAtlas, atlasPreparation, GPUPreparedConcreteResourceRef.Texture::class.java) ||
            !exactPrepared(GPUFrameResourceRole.VertexData, vertexPreparation, GPUPreparedConcreteResourceRef.Buffer::class.java) ||
            !exactPrepared(GPUFrameResourceRole.IndexData, indexPreparation, GPUPreparedConcreteResourceRef.Buffer::class.java) ||
            !exactPrepared(GPUFrameResourceRole.UniformData, uniformPreparation, GPUPreparedConcreteResourceRef.Buffer::class.java)
        ) {
            return refused(
                "unsupported.native-color-glyph.prepared-resources",
                "ColorGlyph prepared resource evidence is missing or substituted.",
            )
        }
        val output = resources.outputOwnedReadbacks.singleOrNull()
        if ((readbackStep == null) != (output == null) || resources.outputOwnedReadbacks.size > 1) {
            return refused(
                "unsupported.native-color-glyph.readback-output",
                "ColorGlyph optional readback must match one output-owned staging lease.",
            )
        }
        if (readbackStep != null && output != null) {
            if (readbackStep.source != renderStep.target || output.request != readbackStep.request ||
                output.stagingResource != readbackStep.staging ||
                output.resourceGeneration != generationSeal.resourceGenerations[readbackStep.staging] ||
                output.layout.width != semantic.targetBounds.width ||
                output.layout.height != semantic.targetBounds.height ||
                output.layout.unpaddedBytesPerRow != semantic.targetBounds.width.toLong() * RGBA_BYTES_PER_PIXEL ||
                output.layout.paddedBytesPerRow % WEBGPU_COPY_ROW_ALIGNMENT != 0L ||
                output.layout.totalBufferBytes > output.stagingLease.backingBufferBytes
            ) {
                return refused(
                    "unsupported.native-color-glyph.readback-layout",
                    "ColorGlyph readback layout does not cover the exact canonical target.",
                )
            }
        }
        if (preparedSceneTarget.width != semantic.targetBounds.width ||
            preparedSceneTarget.height != semantic.targetBounds.height ||
            preparedSceneTarget.deviceGeneration != generationSeal.deviceGeneration ||
            preparedSceneTarget.targetGeneration != generationSeal.targetGeneration
        ) {
            return refused(
                "unsupported.native-color-glyph.prepared-target",
                "ColorGlyph prepared scene target differs from the sealed target.",
            )
        }
        synchronized(this) {
            if (closed) {
                return refused(
                    "unsupported.native-color-glyph.materializer-state",
                    "The native ColorGlyph materializer closed during validation.",
                )
            }
            materializing = true
        }
        return try {
            val (targetTexture, targetView) = preparedSceneTarget.borrow()
            val cached = sessionCache.acquire(semantic)
            val invariants = cached.invariants
            val atlas = cached.atlas
            val vertexBuffer = device.createBuffer(
                BufferDescriptor(
                    size = vertexBytes.toULong(),
                    usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                    label = "Kanvas.frame.colorGlyph.vertices",
                ),
            ).tracked().also { buffer ->
                queue.writeBuffer(buffer, 0uL, ArrayBuffer.of(semantic.vertexData.toFloatArray()))
            }
            val indexBuffer = device.createBuffer(
                BufferDescriptor(
                    size = indexBytes.toULong(),
                    usage = GPUBufferUsage.Index or GPUBufferUsage.CopyDst,
                    label = "Kanvas.frame.colorGlyph.indices",
                ),
            ).tracked().also { buffer ->
                queue.writeBuffer(buffer, 0uL, ArrayBuffer.of(semantic.indexData.toIntArray()))
            }
            val uniformBuffer = device.createBuffer(
                BufferDescriptor(
                    size = uniformBytes.toULong(),
                    usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                    label = "Kanvas.frame.colorGlyph.uniform784",
                ),
            ).tracked().also { buffer ->
                queue.writeBuffer(buffer, 0uL, ArrayBuffer.of(semantic.uniformBytes.map(Int::toByte).toByteArray()))
            }
            val stagingBuffer = output?.let { readback ->
                device.createBuffer(
                    BufferDescriptor(
                        size = readback.stagingLease.backingBufferBytes.toULong(),
                        usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst,
                        mappedAtCreation = false,
                        label = "Kanvas.frame.colorGlyph.readback",
                    ),
                ).tracked()
            }
            val bindGroup = device.createBindGroup(
                BindGroupDescriptor(
                    label = "Kanvas.frame.colorGlyph.bindGroup0",
                    layout = invariants.bindGroupLayout,
                    entries = listOf(
                        BindGroupEntry(
                            binding = 0u,
                            resource = BufferBinding(
                                buffer = uniformBuffer,
                                offset = 0uL,
                                size = uniformBytes.toULong(),
                            ),
                        ),
                        BindGroupEntry(binding = 1u, resource = atlas.view),
                        BindGroupEntry(binding = 2u, resource = invariants.sampler),
                    ),
                ),
            ).tracked()

            val targetViewOperand = GPUPreparedNativeTextureViewOperand(
                targetView,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            )
            val pipelineOperand = GPUPreparedNativeRenderPipelineOperand(
                invariants.pipeline,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            )
            val bindGroupOperand = GPUPreparedNativeBindGroupOperand(
                bindGroup,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
            )
            val vertexOperand = GPUPreparedNativeBufferOperand(
                vertexBuffer,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
            )
            val indexOperand = GPUPreparedNativeBufferOperand(
                indexBuffer,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
            )
            val renderOperand = GPUPreparedNativeScopeOperand.Render(
                sourceStepIndex = renderScope.sourceStepIndex,
                pass = GPUPreparedNativeRenderPassConfig(
                    colorTarget = targetViewOperand,
                    loadOperation = GPUPreparedNativeLoadOperation.Clear,
                    storeOperation = GPUPreparedNativeStoreOperation.Store,
                    clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 1.0),
                ),
                commands = listOf(
                    GPUPreparedNativeRenderCommand.SetPipeline(pipelineOperand),
                    GPUPreparedNativeRenderCommand.SetBindGroup(0, bindGroupOperand),
                    GPUPreparedNativeRenderCommand.SetVertexBuffer(0, vertexOperand, 0L, vertexBytes),
                    GPUPreparedNativeRenderCommand.SetIndexBuffer(
                        indexOperand,
                        GPUPreparedNativeIndexFormat.Uint32,
                        0L,
                        indexBytes,
                    ),
                    GPUPreparedNativeRenderCommand.SetScissor(
                        semantic.scissorBounds.left,
                        semantic.scissorBounds.top,
                        semantic.scissorBounds.width,
                        semantic.scissorBounds.height,
                    ),
                    GPUPreparedNativeRenderCommand.DrawIndexed(
                        GPUPreparedNativeDrawCall.DrawIndexed(indexCount = semantic.indexData.size),
                    ),
                ),
                semanticPayloads = listOf(semantic),
            )
            val readbackOperand = if (readbackScope != null && output != null && stagingBuffer != null) {
                GPUPreparedNativeScopeOperand.Readback(
                    sourceStepIndex = readbackScope.sourceStepIndex,
                    source = GPUPreparedNativeTextureOperand(
                        targetTexture,
                        generationSeal.deviceGeneration,
                        GPUPreparedNativeOperandOwnership.Borrowed,
                    ),
                    destination = GPUPreparedNativeBufferOperand(
                        stagingBuffer,
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
            val operandsByStep = listOfNotNull(renderOperand, readbackOperand)
                .associateBy(GPUPreparedNativeScopeOperand::sourceStepIndex)
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
                    requireNotNull(operandsByStep[scope.sourceStepIndex])
                },
                scopeOperandKeys = encoderPlan.scopes.map { it.nativeOperandKeys },
                auxiliaryOwnedHandles = listOf(
                    GPUPreparedNativeAuxiliaryHandle(uniformBuffer, GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion),
                ),
            )
            val result = GPUPreparedNativeFramePayloadMaterialization.Materialized(
                GPUPreparedNativeFrameDraft(payload),
            )
            synchronized(this) {
                check(!closed) { "Native ColorGlyph materializer closed during materialization" }
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
                "failed.native-color-glyph.materialization",
                "Public wgpu4k ColorGlyph materialization failed: " +
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
            "unsupported.native-color-glyph.surface",
            "The first native ColorGlyph route is offscreen-only.",
        )
    }

    @Synchronized
    override fun close() {
        closed = true
        if (!materializing) preRegistrationHandles.closeRetainingFailures()
    }

    private fun refused(code: String, message: String) =
        GPUPreparedNativeFramePayloadMaterialization.Refused(code, message)

    private fun <T : AutoCloseable> T.tracked(): T = preRegistrationHandles.track(this)

    private companion object {
        const val RGBA8_UNORM = "rgba8unorm"
        const val R8_UNORM = "r8unorm"
        const val CANONICAL_UNIFORM_BYTES = 784L
        const val RGBA_BYTES_PER_PIXEL = 4L
        const val WEBGPU_COPY_ROW_ALIGNMENT = 256
    }
}
