package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.corePrimitiveUniformBytes
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_BINDING_LAYOUT_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_TARGET_STATE_HASH
import org.graphiks.kanvas.gpu.renderer.recording.CORE_PRIMITIVE_VERTEX_SOURCE_LABEL
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStepExecutionKind
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveScissorAuthority
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedConcreteResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

/** Public-wgpu4k materializer for direct and unified indexed path CorePrimitive routes. */
internal class GPUWgpu4kCorePrimitiveFramePayloadMaterializer(
    private val device: GPUDevice,
    private val queue: GPUQueue,
    private val preparedSceneTarget: GPUWgpu4kPreparedSceneTarget,
    private val sessionCache: GPUWgpu4kCorePrimitiveSessionCache,
    private val limits: GPULimits,
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
                    "unsupported.native-core-primitive.materializer-state",
                    "The CorePrimitive materializer is one-shot and already consumed.",
                )
            }
            consumed = true
        }

        val renderSteps = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        val renderStep = renderSteps.singleOrNull()
        if (renderStep == null || renderStep.drawPackets.isEmpty()) {
            return refused(
                "unsupported.native-core-primitive.render-shape",
                "Direct CorePrimitive requires one non-empty multi-packet render scope; " +
                    "observed ${renderSteps.size} scope(s).",
            )
        }
        val renderScope = encoderPlan.scopes.singleOrNull {
            it.sourceStepIndex == framePlan.steps.indexOf(renderStep) &&
                it.operationKind == GPUEncoderOperationKind.Render
        } ?: return refused(
            "unsupported.native-core-primitive.render-plan",
            "The direct CorePrimitive render scope is absent from the encoder plan.",
        )
        val unifiedRoute = renderScope.corePrimitiveNativeScopeRouteSeal as?
            GPUCorePrimitiveNativeScopeRouteSeal.Routes
        if (unifiedRoute?.orderedUnits?.any {
                it is GPUCorePrimitiveNativeScopeRouteUnit.PathPair
            } == true
        ) {
            return materializeIndexedPathCore(
                framePlan,
                encoderPlan,
                resources,
                generationSeal,
                renderStep,
                renderScope,
                unifiedRoute,
            )
        }
        val sealedRoutes = renderScope.corePrimitiveDirectNativeRouteSeal as?
            GPUCorePrimitiveDirectNativeRouteSeal.Routes ?: return refused(
            "invalid.native-core-primitive.route-seal",
                "The direct CorePrimitive render scope requires its pure-preflight route seal.",
            )
        val preparedPassSeal = sealedRoutes.preparedPassSeal ?: return refused(
            "invalid.native-core-primitive.prepared-pass-seal",
            "The direct CorePrimitive route requires the builder authority proven by pure preflight.",
        )
        if (sealedRoutes.routesByPacketId.keys.toList() != renderStep.drawPackets.map { it.packetId }) {
            return refused(
                "invalid.native-core-primitive.route-seal",
                "The direct CorePrimitive route seal must exactly match render packet order and identity.",
            )
        }
        val semanticPackets = renderStep.drawPackets.map { packet ->
            val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
                ?: return refused(
                    "unsupported.native-core-primitive.semantic-payload",
                    "Every direct CorePrimitive scope requires one typed semantic payload.",
                )
            Triple(renderStep, packet, semantic)
        }
        val targetBounds = semanticPackets.first().third.targetBounds
        val acceptedGeometries = semanticPackets.map { (_, packet, semantic) ->
            if (!semantic.hasStructuralIntegrity() || packet.role != GPUDrawPacketRole.Shading ||
                packet.commandIdValue != semantic.payloadRef.commandIdValue ||
                packet.uniformSlot != semantic.payloadRef.uniformSlot ||
                packet.bindingLayoutHash != CORE_PRIMITIVE_BINDING_LAYOUT_HASH ||
                packet.vertexSourceLabel != CORE_PRIMITIVE_VERTEX_SOURCE_LABEL ||
                packet.targetStateHash != CORE_PRIMITIVE_TARGET_STATE_HASH ||
                packet.scissorBoundsHash != corePrimitiveScissorAuthority(semantic.scissorBounds) ||
                packet.corePrimitivePreparedAuthority?.structuralPipelineKey !=
                preparedPassSeal.structuralPipelineKey ||
                packet.corePrimitivePreparedAuthority?.renderPipelineKey != packet.renderPipelineKey ||
                packet.corePrimitivePreparedAuthority?.uniformSlabSeal !== preparedPassSeal.uniformSlabSeal ||
                semantic.targetBounds != targetBounds || semantic.payloadRef.uniformBlock?.byteSize !=
                CORE_PRIMITIVE_UNIFORM_BYTES.toLong() || semantic.payloadRef.uniformBlock.bytes !=
                corePrimitiveUniformBytes(semantic.targetBounds, semantic.premultipliedRgba)
            ) {
                return refused(
                    "invalid.native-core-primitive.packet-authority",
                    "A CorePrimitive packet contradicts its immutable semantic, pipeline, uniform, or target authority.",
                )
            }
            sealedRoutes.routesByPacketId.getValue(packet.packetId)
        }
        val arena = try {
            packCorePrimitiveFrameGeometry(acceptedGeometries)
        } catch (failure: Throwable) {
            return refused(
                "invalid.native-core-primitive.geometry-arena",
                "Direct CorePrimitive geometry cannot be packed safely: ${failure::class.simpleName.orEmpty()}.",
            )
        }
        val vertexBytes: Long
        val indexBytes: Long
        val geometrySlicesValid: Boolean
        try {
            vertexBytes = Math.multiplyExact(arena.vertices.size.toLong(), Float.SIZE_BYTES.toLong())
            indexBytes = Math.multiplyExact(arena.indices.size.toLong(), Int.SIZE_BYTES.toLong())
            val totalVertexCount = arena.vertices.size / 2
            var expectedFirstIndex = 0
            var expectedBaseVertex = 0
            geometrySlicesValid = arena.vertices.size % 2 == 0 && arena.slices.all { slice ->
                val nextFirstIndex = Math.addExact(slice.firstIndex, slice.indexCount)
                val nextBaseVertex = Math.addExact(slice.baseVertex, slice.vertexCount)
                val maximumAddressedVertex = Math.addExact(slice.baseVertex, slice.maxLocalIndex)
                val valid = slice.firstIndex == expectedFirstIndex &&
                    slice.baseVertex == expectedBaseVertex &&
                    slice.indexCount > 0 && slice.vertexCount > 0 &&
                    slice.maxLocalIndex in 0 until slice.vertexCount &&
                    nextFirstIndex <= arena.indices.size && nextBaseVertex <= totalVertexCount &&
                    maximumAddressedVertex < totalVertexCount
                expectedFirstIndex = nextFirstIndex
                expectedBaseVertex = nextBaseVertex
                valid
            } && expectedFirstIndex == arena.indices.size && expectedBaseVertex == totalVertexCount
        } catch (_: ArithmeticException) {
            return refused(
                "invalid.native-core-primitive.geometry-slices",
                "Direct CorePrimitive geometry slices overflow their exact shared-slab convention.",
            )
        }
        if (vertexBytes <= 0L || indexBytes <= 0L || vertexBytes % 8L != 0L || indexBytes % 4L != 0L ||
            !geometrySlicesValid
        ) {
            return refused(
                "invalid.native-core-primitive.geometry-slices",
                "Direct CorePrimitive geometry slices violate the exact shared-slab offset convention.",
            )
        }

        val readbackSteps = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
        val readbackStep = readbackSteps.singleOrNull()
        if (readbackSteps.size > 1 || framePlan.steps.any { it is GPUFrameStep.CopyResourceStep }) {
            return refused(
                "unsupported.native-core-primitive.scope-shape",
                "Direct CorePrimitive accepts only render scopes and one optional readback scope.",
            )
        }
        if (readbackStep != null && readbackStep.request.sourceBounds != targetBounds) {
            return refused(
                "unsupported.native-core-primitive.readback-layout",
                "CorePrimitive readback must cover the exact canonical target bounds.",
            )
        }
        val expectedEncoderSteps = 1 + if (readbackStep == null) 0 else 1
        if (framePlan.steps.count { it.executionKind == GPUFrameStepExecutionKind.Encoder } != expectedEncoderSteps) {
            return refused(
                "unsupported.native-core-primitive.encoder-shape",
                "Direct CorePrimitive contains an unsupported encoder operation.",
            )
        }
        val readbackScope = readbackStep?.let { step ->
            encoderPlan.scopes.singleOrNull {
                it.sourceStepIndex == framePlan.steps.indexOf(step) &&
                    it.operationKind == GPUEncoderOperationKind.Readback
            } ?: return refused(
                "unsupported.native-core-primitive.readback-plan",
                "The direct CorePrimitive readback scope is absent from the encoder plan.",
            )
        }
        if (encoderPlan.scopes != listOfNotNull(renderScope, readbackScope)) {
            return refused(
                "unsupported.native-core-primitive.scope-order",
                "CorePrimitive encoder scopes must preserve render order then optional readback.",
            )
        }
        if (renderStep.samplePlan != GPUSamplePlan.SingleSampleFrame || renderStep.sampleContinuation != null ||
            renderStep.loadStore.loadOp != "clear" || renderStep.loadStore.storePlan != GPUStorePlan.Store ||
            renderStep.loadStore.clearColorLabel != null || semanticPackets.any { (_, _, semantic) ->
                semantic.scissorBounds.isEmpty ||
                semantic.scissorBounds.left < targetBounds.left || semantic.scissorBounds.top < targetBounds.top ||
                semantic.scissorBounds.right > targetBounds.right || semantic.scissorBounds.bottom > targetBounds.bottom
            }
        ) {
            return refused(
                "unsupported.native-core-primitive.render-state",
                "CorePrimitive requires one clear/store single-sample pass and contained scissors.",
            )
        }

        val preparations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        fun preparation(role: GPUFrameResourceRole) = preparations.filter { it.role == role }.singleOrNull()
        val targetPreparation = preparation(GPUFrameResourceRole.SceneTarget)
            ?: return refused("unsupported.native-core-primitive.target", "CorePrimitive target declaration is missing.")
        val vertexPreparation = preparation(GPUFrameResourceRole.VertexData)
            ?: return refused("unsupported.native-core-primitive.vertex", "CorePrimitive vertex slab declaration is missing.")
        val indexPreparation = preparation(GPUFrameResourceRole.IndexData)
            ?: return refused("unsupported.native-core-primitive.index", "CorePrimitive index slab declaration is missing.")
        val uniformPreparation = preparation(GPUFrameResourceRole.UniformData)
            ?: return refused("unsupported.native-core-primitive.uniform", "CorePrimitive uniform slab declaration is missing.")
        val stagingPreparation = preparation(GPUFrameResourceRole.ReadbackStaging)
        if (preparations.size != 4 + (if (readbackStep == null) 0 else 1) ||
            (readbackStep == null) != (stagingPreparation == null)
        ) {
            return refused(
                "unsupported.native-core-primitive.resource-shape",
                "CorePrimitive requires exactly target, shared vertex/index/uniform slabs, and optional readback staging.",
            )
        }
        val targetDescriptor = targetPreparation.descriptor as? GPUFrameTextureDescriptor
        if (targetPreparation.resource != renderSteps.first().target ||
            renderSteps.any { it.target != targetPreparation.resource } || targetDescriptor == null ||
            targetDescriptor.logicalBounds != targetBounds || targetDescriptor.format.value != RGBA8_UNORM ||
            targetDescriptor.sampleCount != 1 ||
            targetPreparation.usages != setOf(
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceUsage.CopySource,
            ) || targetPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal
        ) {
            return refused(
                "unsupported.native-core-primitive.target-contract",
                "CorePrimitive requires one exact frame-local rgba8unorm scene target.",
            )
        }
        fun exactGeometryBuffer(
            preparation: GPUResourcePreparationRequest,
            role: GPUFrameResourceRole,
            usage: GPUFrameResourceUsage,
            bytes: Long,
        ): Boolean {
            val descriptor = preparation.descriptor as? GPUFrameBufferDescriptor ?: return false
            return preparation.role == role && descriptor.byteSize == bytes && descriptor.alignmentBytes == 4L &&
                preparation.byteSize == bytes &&
                preparation.usages == setOf(GPUFrameResourceUsage.CopyDestination, usage) &&
                preparation.lifetime == GPUFrameResourceLifetime.FrameLocal
        }
        if (!exactGeometryBuffer(vertexPreparation, GPUFrameResourceRole.VertexData, GPUFrameResourceUsage.Vertex, vertexBytes) ||
            !exactGeometryBuffer(indexPreparation, GPUFrameResourceRole.IndexData, GPUFrameResourceUsage.Index, indexBytes) ||
            setOf(vertexPreparation.resource, indexPreparation.resource, uniformPreparation.resource).size != 3
        ) {
            return refused(
                "unsupported.native-core-primitive.buffer-contract",
                "CorePrimitive shared Float32x2 vertex and Uint32 index slabs are not exact.",
            )
        }
        val uniformDescriptor = uniformPreparation.descriptor as? GPUFrameBufferDescriptor
            ?: return refused(
                "unsupported.native-core-primitive.uniform-contract",
                "CorePrimitive uniform slab requires one exact buffer descriptor.",
            )
        val uniformSlabSeal = preparedPassSeal.uniformSlabSeal
        val uniformSlabPlan = uniformSlabSeal.plan
        if (uniformSlabPlan.deviceGeneration != generationSeal.deviceGeneration.value ||
            uniformSlabPlan.alignmentBytes != limits.minUniformBufferOffsetAlignment
        ) {
            return refused(
                "invalid.native-core-primitive.uniform-seal-generation",
                "CorePrimitive builder uniform authority is stale for the materialized device generation.",
            )
        }
        if (uniformDescriptor.byteSize != uniformSlabPlan.totalBytes ||
            uniformDescriptor.alignmentBytes != uniformSlabPlan.alignmentBytes ||
            uniformPreparation.byteSize != uniformSlabPlan.totalBytes ||
            uniformPreparation.usages != setOf(
                GPUFrameResourceUsage.CopyDestination,
                GPUFrameResourceUsage.Uniform,
            ) || uniformPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal
        ) {
            return refused(
                "unsupported.native-core-primitive.uniform-contract",
                "CorePrimitive uniform preparation differs from the sealed aligned slab plan.",
            )
        }
        if (uniformSlabPlan.totalBytes > Int.MAX_VALUE.toLong()) {
            return refused(
                "unsupported.native-core-primitive.uniform-slab-host-size",
                "CorePrimitive uniform slab exceeds the host-addressable ByteArray size.",
            )
        }
        val exactUses = setOf(
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
                vertexPreparation.resource,
                GPUFrameResourceRole.VertexData,
                GPUFrameResourceUsage.Vertex,
                GPUFrameResourceLifetime.FrameLocal,
                write = false,
            ),
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
                indexPreparation.resource,
                GPUFrameResourceRole.IndexData,
                GPUFrameResourceUsage.Index,
                GPUFrameResourceLifetime.FrameLocal,
                write = false,
            ),
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
                uniformPreparation.resource,
                GPUFrameResourceRole.UniformData,
                GPUFrameResourceUsage.Uniform,
                GPUFrameResourceLifetime.FrameLocal,
                write = false,
            ),
        )
        if (renderSteps.any { render ->
                render.resourceUses.filter {
                    it.role == GPUFrameResourceRole.VertexData || it.role == GPUFrameResourceRole.IndexData ||
                        it.role == GPUFrameResourceRole.UniformData
                }.toSet() != exactUses
            }
        ) {
            return refused(
                "invalid.native-core-primitive.render-resource-uses",
                "Every direct CorePrimitive draw must read the exact shared vertex and index slabs.",
            )
        }

        val preparedByLogical = resources.ordinaryResources.associateBy { it.logicalResource }
        if (resources.ordinaryResources.size != 4 ||
            listOf(targetPreparation, vertexPreparation, indexPreparation, uniformPreparation).any { preparation ->
                val evidence = preparedByLogical[preparation.resource]
                val expectedKind = if (preparation.role == GPUFrameResourceRole.SceneTarget) {
                    GPUPreparedConcreteResourceRef.Texture::class.java
                } else {
                    GPUPreparedConcreteResourceRef.Buffer::class.java
                }
                evidence == null || evidence.role != preparation.role ||
                    evidence.deviceGeneration != generationSeal.deviceGeneration ||
                    evidence.resourceGeneration != generationSeal.resourceGenerations[preparation.resource] ||
                    !expectedKind.isInstance(evidence.concreteResource)
            } || listOf(targetPreparation, vertexPreparation, indexPreparation, uniformPreparation).any {
                generationSeal.resourceGenerations[it.resource] == null
            }
        ) {
            return refused(
                "unsupported.native-core-primitive.prepared-resources",
                "CorePrimitive prepared target and geometry evidence is missing or substituted.",
            )
        }
        if (preparedSceneTarget.width != targetBounds.width || preparedSceneTarget.height != targetBounds.height ||
            preparedSceneTarget.deviceGeneration != generationSeal.deviceGeneration ||
            preparedSceneTarget.targetGeneration != generationSeal.targetGeneration
        ) {
            return refused(
                "unsupported.native-core-primitive.prepared-target",
                "The prepared scene target differs from the sealed CorePrimitive target.",
            )
        }
        val output = resources.outputOwnedReadbacks.singleOrNull()
        if ((readbackStep == null) != (output == null) || resources.outputOwnedReadbacks.size > 1) {
            return refused(
                "unsupported.native-core-primitive.readback-output",
                "The optional CorePrimitive readback must match one output-owned staging lease.",
            )
        }
        if (readbackStep != null && stagingPreparation != null && output != null) {
            val stagingDescriptor = stagingPreparation.descriptor as? GPUFrameBufferDescriptor
            if (readbackStep.source != targetPreparation.resource || readbackStep.staging != stagingPreparation.resource ||
                output.request != readbackStep.request || output.stagingResource != stagingPreparation.resource ||
                output.request.sourceBounds != targetBounds ||
                stagingDescriptor?.byteSize != output.layout.totalBufferBytes ||
                stagingPreparation.byteSize != output.layout.totalBufferBytes ||
                stagingPreparation.usages != setOf(
                    GPUFrameResourceUsage.CopyDestination,
                    GPUFrameResourceUsage.MapRead,
                ) || stagingPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
                output.resourceGeneration != generationSeal.resourceGenerations[stagingPreparation.resource] ||
                output.layout.width != targetBounds.width || output.layout.height != targetBounds.height ||
                output.layout.unpaddedBytesPerRow != targetBounds.width.toLong() * RGBA_BYTES_PER_PIXEL ||
                output.layout.paddedBytesPerRow % WEBGPU_COPY_ROW_ALIGNMENT != 0L ||
                output.layout.totalBufferBytes > output.stagingLease.backingBufferBytes
            ) {
                return refused(
                    "unsupported.native-core-primitive.readback-layout",
                    "The output-owned CorePrimitive RGBA8 readback layout is not exact.",
                )
            }
        }

        synchronized(this) {
            if (closed) {
                return refused(
                    "unsupported.native-core-primitive.materializer-state",
                    "The CorePrimitive materializer closed during validation.",
                )
            }
            materializing = true
        }
        var frameLease: GPUWgpu4kCorePrimitiveFramePoolLease? = null
        var frameLeaseTransferred = false
        return try {
            val (targetTexture, targetView) = preparedSceneTarget.borrow()
            val invariants = when (
                val acquired = sessionCache.acquire(
                    GPUWgpu4kCorePrimitivePipelineCacheKey(RGBA8_UNORM, 1),
                )
            ) {
                is GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired -> acquired.handles
                is GPUWgpu4kCorePrimitiveSessionCacheAcquire.Refused -> {
                    synchronized(this) { materializing = false }
                    return refusedSessionCacheAcquire(acquired.reason)
                }
            }
            frameLease = when (
                val checkout = sessionCache.acquireFrame(
                    GPUWgpu4kCorePrimitiveFramePoolRequirements(
                        generationSeal.deviceGeneration,
                        vertexBytes,
                        indexBytes,
                        uniformSlabPlan.totalBytes,
                    ),
                )
            ) {
                is GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired -> checkout.lease
                is GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused -> {
                    synchronized(this) { materializing = false }
                    return refusedPoolCheckout(checkout.reason)
                }
            }
            val pooled = requireNotNull(frameLease)
            val vertexBuffer = pooled.handles.vertexBuffer
            val indexBuffer = pooled.handles.indexBuffer
            val uniformBuffer = pooled.handles.uniformBuffer
            val bindGroup = pooled.handles.bindGroup
            uploadExact(
                vertexBuffer,
                ArrayBuffer.of(arena.vertices),
                usedBytes = vertexBytes,
                capacityBytes = pooled.capacities.vertexBytes,
            )
            uploadExact(
                indexBuffer,
                ArrayBuffer.of(arena.indices),
                usedBytes = indexBytes,
                capacityBytes = pooled.capacities.indexBytes,
            )
            uploadExact(
                uniformBuffer,
                ArrayBuffer.of(uniformSlabSeal.packedBytesForUpload()),
                usedBytes = uniformSlabPlan.totalBytes,
                capacityBytes = pooled.capacities.uniformBytes,
            )
            val stagingBuffer = output?.let { readback ->
                device.createBuffer(
                    BufferDescriptor(
                        size = readback.stagingLease.backingBufferBytes.toULong(),
                        usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst,
                        mappedAtCreation = false,
                        label = "Kanvas.frame.corePrimitive.readback",
                    ),
                ).tracked()
            }
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
            val vertexOperand = GPUPreparedNativeBufferOperand(
                vertexBuffer,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
                byteCapacity = pooled.capacities.vertexBytes,
            )
            val indexOperand = GPUPreparedNativeBufferOperand(
                indexBuffer,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
                byteCapacity = pooled.capacities.indexBytes,
            )
            val sharedBindGroupOperand = GPUPreparedNativeBindGroupOperand(
                bindGroup,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            )
            val renderCommands = buildList {
                add(GPUPreparedNativeRenderCommand.SetPipeline(pipelineOperand))
                add(
                    GPUPreparedNativeRenderCommand.SetVertexBuffer(
                        0,
                        vertexOperand,
                        offset = 0L,
                        size = vertexBytes,
                        vertexStrideBytes = 8L,
                    ),
                )
                add(
                    GPUPreparedNativeRenderCommand.SetIndexBuffer(
                        indexOperand,
                        GPUPreparedNativeIndexFormat.Uint32,
                        offset = 0L,
                        size = indexBytes,
                    ),
                )
                semanticPackets.indices.forEach { index ->
                    val semantic = semanticPackets[index].third
                    val slice = arena.slices[index]
                    val uniformSlot = uniformSlabPlan.slots[index]
                    add(
                        GPUPreparedNativeRenderCommand.SetBindGroup(
                            0,
                            sharedBindGroupOperand,
                            dynamicOffsets = listOf(uniformSlot.alignedOffset),
                        ),
                    )
                    add(
                        GPUPreparedNativeRenderCommand.SetScissor(
                            semantic.scissorBounds.left,
                            semantic.scissorBounds.top,
                            semantic.scissorBounds.width,
                            semantic.scissorBounds.height,
                        ),
                    )
                    add(
                        GPUPreparedNativeRenderCommand.DrawIndexed(
                            GPUPreparedNativeDrawCall.DrawIndexed(
                                indexCount = slice.indexCount,
                                firstIndex = slice.firstIndex,
                                baseVertex = slice.baseVertex,
                                vertexCount = slice.vertexCount,
                                maxLocalIndex = slice.maxLocalIndex,
                            ),
                        ),
                    )
                }
            }
            val renderOperand = GPUPreparedNativeScopeOperand.Render(
                sourceStepIndex = renderScope.sourceStepIndex,
                pass = GPUPreparedNativeRenderPassConfig(
                    colorTarget = targetViewOperand,
                    loadOperation = GPUPreparedNativeLoadOperation.Clear,
                    storeOperation = GPUPreparedNativeStoreOperation.Store,
                    clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0),
                ),
                commands = renderCommands,
                semanticPayloads = semanticPackets.map { it.third },
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
            val operandsByStep = (listOf(renderOperand) + listOfNotNull(readbackOperand))
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
                leaseLifecycle = GPUWgpu4kCorePrimitivePayloadLeaseLifecycle(pooled),
            )
            val result = GPUPreparedNativeFramePayloadMaterialization.Materialized(
                GPUPreparedNativeFrameDraft(payload),
            )
            synchronized(this) {
                check(!closed) { "Native CorePrimitive materializer closed during materialization" }
                preRegistrationHandles.transferAll()
                materializing = false
                frameLeaseTransferred = true
            }
            result
        } catch (failure: Throwable) {
            if (!frameLeaseTransferred) terminalizePooledLeaseBeforeRegistration(frameLease)
            synchronized(this) {
                materializing = false
                preRegistrationHandles.closeRetainingFailures()
            }
            refused(
                "failed.native-core-primitive.materialization",
                "Public wgpu4k CorePrimitive materialization failed: " +
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
            "unsupported.native-core-primitive.surface",
            "The direct CorePrimitive route is offscreen-only before surface decoration.",
        )
    }

    private fun materializeIndexedPathCore(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
        renderStep: GPUFrameStep.RenderPassStep,
        renderScope: GPUCommandEncoderScopePlan,
        unifiedRoute: GPUCorePrimitiveNativeScopeRouteSeal.Routes,
    ): GPUPreparedNativeFramePayloadMaterialization {
        val pathSeal = renderScope.corePrimitivePathStencilNativeRouteSeal as?
            GPUCorePrimitivePathStencilNativeRouteSeal.Pairs ?: return refused(
            "invalid.native-core-primitive.path-route-seal",
            "Indexed CorePrimitive requires one exact non-empty path-pair compatibility seal.",
        )
        val packetIds = renderStep.drawPackets.map { it.packetId }
        if (renderScope.sourcePacketIds != packetIds ||
            unifiedRoute.flattenedPacketIds != packetIds ||
            pathSeal.flattenedPacketIds != unifiedRoute.orderedUnits
                .filterIsInstance<GPUCorePrimitiveNativeScopeRouteUnit.PathPair>()
                .flatMap(GPUCorePrimitiveNativeScopeRouteUnit.PathPair::flattenedPacketIds)
        ) {
            return refused(
                "invalid.native-core-primitive.indexed-route-seal",
                "Indexed CorePrimitive unified and path seals must match the exact packet order.",
            )
        }
        if (framePlan.steps.count { it.executionKind == GPUFrameStepExecutionKind.Encoder } != 1 ||
            encoderPlan.scopes != listOf(renderScope)
        ) {
            return refused(
                "unsupported.native-core-primitive.indexed-scope-shape",
                "Indexed CorePrimitive currently requires one exact render encoder scope.",
            )
        }
        val indexedPathUnits = unifiedRoute.orderedUnits.withIndex()
            .filter { (_, unit) -> unit is GPUCorePrimitiveNativeScopeRouteUnit.PathPair }
        val pathUnits = indexedPathUnits.map { it.value as GPUCorePrimitiveNativeScopeRouteUnit.PathPair }
        val preparedPathPass = pathSeal.preparedPassSeal
        val preparedPathExact = preparedPathPass != null &&
            preparedPathPass.uniformSlabSeal === unifiedRoute.uniformSlabSeal &&
            preparedPathPass.orderedPairs.size == indexedPathUnits.size &&
            preparedPathPass.orderedPairs.zip(indexedPathUnits).all { (prepared, indexedUnit) ->
                val unit = indexedUnit.value as GPUCorePrimitiveNativeScopeRouteUnit.PathPair
                prepared.commandIdValue == unit.commandIdValue &&
                    prepared.uniformSlotIndex == indexedUnit.index &&
                    prepared.producerPacketId == unit.pair.producerPacketId &&
                    prepared.coverPacketId == unit.pair.coverPacketId &&
                    prepared.producerStructuralPipelineKey == unit.producerStructuralPipelineKey &&
                    prepared.coverStructuralPipelineKey == unit.coverStructuralPipelineKey
            }
        if (pathUnits.map { it.pair } != pathSeal.orderedPairs ||
            !preparedPathExact
        ) {
            return refused(
                "invalid.native-core-primitive.path-route-seal",
                "Indexed CorePrimitive path compatibility authority differs from the unified seal.",
            )
        }
        val directUnits = unifiedRoute.orderedUnits
            .filterIsInstance<GPUCorePrimitiveNativeScopeRouteUnit.Direct>()
        when (val directSeal = renderScope.corePrimitiveDirectNativeRouteSeal) {
            GPUCorePrimitiveDirectNativeRouteSeal.Empty -> if (directUnits.isNotEmpty()) {
                return refused(
                    "invalid.native-core-primitive.direct-route-seal",
                    "Mixed indexed CorePrimitive is missing its derived direct compatibility seal.",
                )
            }
            is GPUCorePrimitiveDirectNativeRouteSeal.Routes -> {
                val preparedDirectPass = directSeal.preparedPassSeal
                val preparedDirectExact = preparedDirectPass != null &&
                    preparedDirectPass.uniformSlabSeal === unifiedRoute.uniformSlabSeal &&
                    preparedDirectPass.structuralPipelineKey == directUnits.firstOrNull()?.structuralPipelineKey &&
                    directUnits.all {
                        it.structuralPipelineKey == preparedDirectPass.structuralPipelineKey
                    }
                if (directSeal.routesByPacketId.keys.toList() != directUnits.map { it.packetId } ||
                    directUnits.any { directSeal.routesByPacketId[it.packetId] !== it.route } ||
                    !preparedDirectExact
                ) {
                    return refused(
                        "invalid.native-core-primitive.direct-route-seal",
                        "Mixed indexed CorePrimitive direct compatibility authority is not exact.",
                    )
                }
            }
            GPUCorePrimitiveDirectNativeRouteSeal.Missing -> return refused(
                "invalid.native-core-primitive.direct-route-seal",
                "Indexed CorePrimitive direct compatibility authority is missing.",
            )
        }

        data class PacketPlan(
            val packet: org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket,
            val semantic: GPUDrawSemanticPayload.CorePrimitive,
            val structuralKey: org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey,
            val uniformSlotIndex: Int,
        )

        val packetPlans = mutableListOf<PacketPlan>()
        var packetIndex = 0
        unifiedRoute.orderedUnits.forEachIndexed { uniformSlotIndex, unit ->
            when (unit) {
                is GPUCorePrimitiveNativeScopeRouteUnit.Direct -> {
                    val packet = renderStep.drawPackets.getOrNull(packetIndex)
                        ?: return refused(
                            "invalid.native-core-primitive.indexed-packet-authority",
                            "Indexed CorePrimitive direct packet is absent.",
                        )
                    if (packet.packetId != unit.packetId || packet.role != GPUDrawPacketRole.Shading) {
                        return refused(
                            "invalid.native-core-primitive.indexed-packet-authority",
                            "Indexed CorePrimitive direct unit differs from packet role or identity.",
                        )
                    }
                    val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
                        ?: return refused(
                            "invalid.native-core-primitive.indexed-packet-authority",
                            "Indexed CorePrimitive direct packet has no typed semantic payload.",
                        )
                    packetPlans += PacketPlan(packet, semantic, unit.structuralPipelineKey, uniformSlotIndex)
                    packetIndex += 1
                }
                is GPUCorePrimitiveNativeScopeRouteUnit.PathPair -> {
                    val producer = renderStep.drawPackets.getOrNull(packetIndex)
                    val cover = renderStep.drawPackets.getOrNull(packetIndex + 1)
                    if (producer?.packetId != unit.pair.producerPacketId ||
                        cover?.packetId != unit.pair.coverPacketId ||
                        producer.role != GPUDrawPacketRole.PathStencilProducer ||
                        cover.role != GPUDrawPacketRole.PathStencilCover
                    ) {
                        return refused(
                            "invalid.native-core-primitive.indexed-packet-authority",
                            "Indexed CorePrimitive path unit differs from producer/cover packet order.",
                        )
                    }
                    val producerSemantic = producer.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
                    val coverSemantic = cover.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
                    if (producerSemantic == null || coverSemantic == null ||
                        producerSemantic.payloadRef.commandIdValue != coverSemantic.payloadRef.commandIdValue ||
                        producerSemantic.geometry != coverSemantic.geometry ||
                        producerSemantic.targetBounds != coverSemantic.targetBounds ||
                        producerSemantic.scissorBounds != coverSemantic.scissorBounds ||
                        producerSemantic.coverageMode != org.graphiks.kanvas.gpu.renderer.payloads
                            .GPUCorePrimitiveCoverageMode.Stencil1x ||
                        coverSemantic.coverageMode != org.graphiks.kanvas.gpu.renderer.payloads
                            .GPUCorePrimitiveCoverageMode.Stencil1x ||
                        producer.uniformSlot != cover.uniformSlot ||
                        producer.clipExecutionPlan != cover.clipExecutionPlan ||
                        producerSemantic.payloadRef.uniformBlock?.bytes !=
                        coverSemantic.payloadRef.uniformBlock?.bytes
                    ) {
                        return refused(
                            "invalid.native-core-primitive.indexed-packet-authority",
                            "Indexed CorePrimitive path pair requires one identical typed semantic payload.",
                        )
                    }
                    packetPlans += PacketPlan(
                        producer,
                        producerSemantic,
                        unit.producerStructuralPipelineKey,
                        uniformSlotIndex,
                    )
                    packetPlans += PacketPlan(
                        cover,
                        coverSemantic,
                        unit.coverStructuralPipelineKey,
                        uniformSlotIndex,
                    )
                    packetIndex += 2
                }
            }
        }
        if (packetIndex != renderStep.drawPackets.size ||
            unifiedRoute.commandIds != unifiedRoute.uniformSlabSeal.commandIds
        ) {
            return refused(
                "invalid.native-core-primitive.indexed-packet-authority",
                "Indexed CorePrimitive route does not cover the exact prepared packet stream.",
            )
        }
        val targetBounds = packetPlans.first().semantic.targetBounds
        if (packetPlans.any { plan ->
                val authority = plan.packet.corePrimitivePreparedAuthority
                !plan.semantic.hasStructuralIntegrity() ||
                    plan.packet.commandIdValue != plan.semantic.payloadRef.commandIdValue ||
                    plan.packet.uniformSlot != plan.semantic.payloadRef.uniformSlot ||
                    plan.packet.bindingLayoutHash != CORE_PRIMITIVE_BINDING_LAYOUT_HASH ||
                    plan.packet.vertexSourceLabel != CORE_PRIMITIVE_VERTEX_SOURCE_LABEL ||
                    plan.packet.targetStateHash != CORE_PRIMITIVE_TARGET_STATE_HASH ||
                    plan.packet.scissorBoundsHash != corePrimitiveScissorAuthority(plan.semantic.scissorBounds) ||
                    authority?.structuralPipelineKey != plan.structuralKey ||
                    authority.renderPipelineKey != plan.packet.renderPipelineKey ||
                    authority.uniformSlabSeal !== unifiedRoute.uniformSlabSeal ||
                    unifiedRoute.uniformSlabSeal.commandIds.getOrNull(plan.uniformSlotIndex) !=
                    plan.packet.commandIdValue ||
                    plan.semantic.targetBounds != targetBounds ||
                    plan.semantic.payloadRef.uniformBlock?.byteSize != CORE_PRIMITIVE_UNIFORM_BYTES.toLong() ||
                    plan.semantic.payloadRef.uniformBlock.bytes !=
                    corePrimitiveUniformBytes(plan.semantic.targetBounds, plan.semantic.premultipliedRgba)
            }
        ) {
            return refused(
                "invalid.native-core-primitive.indexed-packet-authority",
                "Indexed CorePrimitive packet contradicts its sealed semantic, pipeline, or uniform authority.",
            )
        }
        if (renderStep.samplePlan != GPUSamplePlan.SingleSampleFrame || renderStep.sampleContinuation != null ||
            renderStep.loadStore.loadOp != "clear" || renderStep.loadStore.storePlan != GPUStorePlan.Store ||
            renderStep.loadStore.clearColorLabel != null ||
            renderStep.depthStencilLoadStore != org.graphiks.kanvas.gpu.renderer.recording
                .GPUDepthStencilLoadStorePlan.WritableStencil(
                    org.graphiks.kanvas.gpu.renderer.recording.GPUStencilLoadOperation.Clear,
                    GPUStorePlan.Discard,
                    0u,
                ) ||
            packetPlans.any { plan ->
                plan.semantic.scissorBounds.isEmpty ||
                    plan.semantic.scissorBounds.left < targetBounds.left ||
                    plan.semantic.scissorBounds.top < targetBounds.top ||
                    plan.semantic.scissorBounds.right > targetBounds.right ||
                    plan.semantic.scissorBounds.bottom > targetBounds.bottom
            }
        ) {
            return refused(
                "unsupported.native-core-primitive.indexed-render-state",
                "Indexed CorePrimitive requires exact clear/store color and clear-zero/discard stencil state.",
            )
        }

        val arena = try {
            GPUCorePrimitiveNativeScopeGeometryArena.pack(unifiedRoute)
        } catch (_: IllegalArgumentException) {
            return refused(
                "invalid.native-core-primitive.indexed-geometry-arena",
                "Indexed CorePrimitive geometry cannot be packed safely.",
            )
        } catch (_: ArithmeticException) {
            return refused(
                "invalid.native-core-primitive.indexed-geometry-arena",
                "Indexed CorePrimitive geometry sizing overflows.",
            )
        }
        val vertexBytes: Long
        val indexBytes: Long
        try {
            vertexBytes = Math.multiplyExact(arena.vertexFloatCount.toLong(), Float.SIZE_BYTES.toLong())
            indexBytes = Math.multiplyExact(arena.indexCount.toLong(), Int.SIZE_BYTES.toLong())
        } catch (_: ArithmeticException) {
            return refused(
                "invalid.native-core-primitive.indexed-geometry-arena",
                "Indexed CorePrimitive geometry byte sizing overflows.",
            )
        }
        if (vertexBytes <= 0L || indexBytes <= 0L || vertexBytes % 8L != 0L || indexBytes % 4L != 0L ||
            arena.slices.map { it.packetId } != packetIds
        ) {
            return refused(
                "invalid.native-core-primitive.indexed-geometry-arena",
                "Indexed CorePrimitive geometry slices do not match the sealed packet stream.",
            )
        }
        val vertexData = FloatArray(arena.vertexFloatCount).also(arena::copyVerticesInto)
        val indexData = IntArray(arena.indexCount).also(arena::copyIndicesInto)

        val preparations = framePlan.steps.filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        fun preparation(role: GPUFrameResourceRole) = preparations.filter { it.role == role }.singleOrNull()
        val targetPreparation = preparation(GPUFrameResourceRole.SceneTarget)
        val vertexPreparation = preparation(GPUFrameResourceRole.VertexData)
        val indexPreparation = preparation(GPUFrameResourceRole.IndexData)
        val uniformPreparation = preparation(GPUFrameResourceRole.UniformData)
        val depthStencilPreparation = preparation(GPUFrameResourceRole.PathDepthStencil)
        if (preparations.size != 5 || targetPreparation == null || vertexPreparation == null ||
            indexPreparation == null || uniformPreparation == null || depthStencilPreparation == null
        ) {
            return refused(
                "unsupported.native-core-primitive.indexed-resource-shape",
                "Indexed CorePrimitive requires exactly target, V/I/U slabs, and one path depth/stencil attachment.",
            )
        }
        val targetDescriptor = targetPreparation.descriptor as? GPUFrameTextureDescriptor
        val depthDescriptor = depthStencilPreparation.descriptor as? GPUFrameTextureDescriptor
        val depthBytes = try {
            Math.multiplyExact(
                Math.multiplyExact(targetBounds.width.toLong(), targetBounds.height.toLong()),
                4L,
            )
        } catch (_: ArithmeticException) {
            return refused(
                "invalid.native-core-primitive.indexed-depth-stencil",
                "Indexed CorePrimitive depth/stencil size overflows.",
            )
        }
        if (targetPreparation.resource != renderStep.target || targetDescriptor?.logicalBounds != targetBounds ||
            targetDescriptor.format.value != RGBA8_UNORM || targetDescriptor.sampleCount != 1 ||
            targetPreparation.usages != setOf(
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceUsage.CopySource,
            ) || targetPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            depthDescriptor?.logicalBounds != targetBounds ||
            depthDescriptor.format.value != DEPTH24PLUS_STENCIL8 || depthDescriptor.sampleCount != 1 ||
            depthStencilPreparation.usages != setOf(GPUFrameResourceUsage.RenderAttachment) ||
            depthStencilPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            depthStencilPreparation.byteSize != depthBytes
        ) {
            return refused(
                "invalid.native-core-primitive.indexed-attachment-contract",
                "Indexed CorePrimitive target or D24S8 attachment contract is not exact.",
            )
        }
        fun exactGeometryBuffer(
            preparation: GPUResourcePreparationRequest,
            role: GPUFrameResourceRole,
            usage: GPUFrameResourceUsage,
            bytes: Long,
        ): Boolean {
            val descriptor = preparation.descriptor as? GPUFrameBufferDescriptor ?: return false
            return preparation.role == role && descriptor.byteSize == bytes && descriptor.alignmentBytes == 4L &&
                preparation.byteSize == bytes &&
                preparation.usages == setOf(GPUFrameResourceUsage.CopyDestination, usage) &&
                preparation.lifetime == GPUFrameResourceLifetime.FrameLocal
        }
        if (!exactGeometryBuffer(
                vertexPreparation,
                GPUFrameResourceRole.VertexData,
                GPUFrameResourceUsage.Vertex,
                vertexBytes,
            ) || !exactGeometryBuffer(
                indexPreparation,
                GPUFrameResourceRole.IndexData,
                GPUFrameResourceUsage.Index,
                indexBytes,
            ) || setOf(
                vertexPreparation.resource,
                indexPreparation.resource,
                uniformPreparation.resource,
                depthStencilPreparation.resource,
            ).size != 4
        ) {
            return refused(
                "invalid.native-core-primitive.indexed-buffer-contract",
                "Indexed CorePrimitive shared V/I slabs are not exact.",
            )
        }
        val uniformSlabSeal = unifiedRoute.uniformSlabSeal
        val uniformPlan = uniformSlabSeal.plan
        val uniformDescriptor = uniformPreparation.descriptor as? GPUFrameBufferDescriptor
        if (uniformDescriptor == null || uniformPlan.deviceGeneration != generationSeal.deviceGeneration.value ||
            uniformPlan.alignmentBytes != limits.minUniformBufferOffsetAlignment ||
            uniformPlan.totalBytes > Int.MAX_VALUE.toLong() ||
            uniformDescriptor.byteSize != uniformPlan.totalBytes ||
            uniformDescriptor.alignmentBytes != uniformPlan.alignmentBytes ||
            uniformPreparation.byteSize != uniformPlan.totalBytes ||
            uniformPreparation.usages != setOf(
                GPUFrameResourceUsage.CopyDestination,
                GPUFrameResourceUsage.Uniform,
            ) || uniformPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal
        ) {
            return refused(
                "invalid.native-core-primitive.indexed-uniform-contract",
                "Indexed CorePrimitive uniform slab differs from its sealed plan.",
            )
        }
        val exactUses = setOf(
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
                vertexPreparation.resource,
                GPUFrameResourceRole.VertexData,
                GPUFrameResourceUsage.Vertex,
                GPUFrameResourceLifetime.FrameLocal,
                false,
            ),
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
                indexPreparation.resource,
                GPUFrameResourceRole.IndexData,
                GPUFrameResourceUsage.Index,
                GPUFrameResourceLifetime.FrameLocal,
                false,
            ),
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
                uniformPreparation.resource,
                GPUFrameResourceRole.UniformData,
                GPUFrameResourceUsage.Uniform,
                GPUFrameResourceLifetime.FrameLocal,
                false,
            ),
            org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse(
                depthStencilPreparation.resource,
                GPUFrameResourceRole.PathDepthStencil,
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceLifetime.FrameLocal,
                true,
            ),
        )
        if (renderStep.resourceUses.toSet() != exactUses || renderStep.resourceUses.size != exactUses.size) {
            return refused(
                "invalid.native-core-primitive.indexed-render-resource-uses",
                "Indexed CorePrimitive render must retain exactly V/I/U and path depth/stencil uses.",
            )
        }
        val preparedByLogical = resources.ordinaryResources.associateBy { it.logicalResource }
        if (resources.ordinaryResources.size != 5 || resources.outputOwnedReadbacks.isNotEmpty() ||
            listOf(
                targetPreparation,
                vertexPreparation,
                indexPreparation,
                uniformPreparation,
                depthStencilPreparation,
            ).any { preparation ->
                val evidence = preparedByLogical[preparation.resource]
                val textureRole = preparation.role == GPUFrameResourceRole.SceneTarget ||
                    preparation.role == GPUFrameResourceRole.PathDepthStencil
                val expectedKind = if (textureRole) {
                    GPUPreparedConcreteResourceRef.Texture::class.java
                } else {
                    GPUPreparedConcreteResourceRef.Buffer::class.java
                }
                evidence == null || evidence.role != preparation.role ||
                    evidence.deviceGeneration != generationSeal.deviceGeneration ||
                    evidence.resourceGeneration != generationSeal.resourceGenerations[preparation.resource] ||
                    !expectedKind.isInstance(evidence.concreteResource)
            }
        ) {
            return refused(
                "invalid.native-core-primitive.indexed-prepared-resources",
                "Indexed CorePrimitive prepared target, slabs, or depth/stencil evidence is missing or substituted.",
            )
        }
        if (preparedSceneTarget.width != targetBounds.width || preparedSceneTarget.height != targetBounds.height ||
            preparedSceneTarget.deviceGeneration != generationSeal.deviceGeneration ||
            preparedSceneTarget.targetGeneration != generationSeal.targetGeneration
        ) {
            return refused(
                "invalid.native-core-primitive.indexed-prepared-target",
                "Indexed CorePrimitive prepared scene target differs from its sealed target.",
            )
        }

        val structuralKeys = packetPlans.map(PacketPlan::structuralKey).distinct()
        val expectedNativeRoles = buildList {
            add(GPUPreparedNativeOperandRole.RenderColorTarget)
            add(GPUPreparedNativeOperandRole.RenderDepthStencilTarget)
            repeat(structuralKeys.size) { add(GPUPreparedNativeOperandRole.RenderPipeline) }
            add(GPUPreparedNativeOperandRole.RenderVertexBuffer)
            add(GPUPreparedNativeOperandRole.RenderIndexBuffer)
            repeat(packetPlans.size) { add(GPUPreparedNativeOperandRole.RenderBindGroup) }
        }
        val expectedNativeKinds = buildList {
            add(GPUPreparedNativeOperandKind.TextureView)
            add(GPUPreparedNativeOperandKind.TextureView)
            repeat(structuralKeys.size) { add(GPUPreparedNativeOperandKind.RenderPipeline) }
            add(GPUPreparedNativeOperandKind.Buffer)
            add(GPUPreparedNativeOperandKind.Buffer)
            repeat(packetPlans.size) { add(GPUPreparedNativeOperandKind.BindGroup) }
        }
        if (renderScope.nativeOperandKeys.map { it.role } != expectedNativeRoles ||
            renderScope.nativeOperandKeys.map { it.kind } != expectedNativeKinds ||
            renderScope.nativeOperandKeys.any {
                it.ownership != GPUPreparedNativeOperandOwnership.Borrowed
            }
        ) {
            return refused(
                "invalid.native-core-primitive.indexed-native-keys",
                "Indexed CorePrimitive native keys must exactly encode target, depth/stencil, " +
                    "unique pipelines, shared geometry, and packet-order bind groups.",
            )
        }
        val cacheKeys = linkedMapOf<
            org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey,
            GPUWgpu4kCorePrimitivePipelineCacheKey
            >()
        structuralKeys.forEach { structuralKey ->
            val mapped = mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(structuralKey) as?
                GPUWgpu4kCorePrimitivePipelineMapping.Mapped ?: return refused(
                "unsupported.native-core-primitive.indexed-pipeline",
                "Indexed CorePrimitive contains a structural pipeline outside the closed native programs.",
            )
            if (structuralKey.role == org.graphiks.kanvas.gpu.renderer.passes
                    .GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading &&
                mapped.identity.program !=
                GPUWgpu4kCorePrimitivePipelineProgram.DirectSrcOverWithPathDepthStencil
            ) {
                return refused(
                    "invalid.native-core-primitive.indexed-direct-pipeline",
                    "Mixed indexed CorePrimitive direct draws require the neutral path depth/stencil program.",
                )
            }
            cacheKeys[structuralKey] = GPUWgpu4kCorePrimitivePipelineCacheKey(
                RGBA8_UNORM,
                1,
            ).copy(pipelineIdentity = mapped.identity)
        }

        synchronized(this) {
            if (closed) {
                return refused(
                    "unsupported.native-core-primitive.materializer-state",
                    "The CorePrimitive materializer closed during indexed validation.",
                )
            }
            materializing = true
        }
        var frameLease: GPUWgpu4kCorePrimitiveFramePoolLease? = null
        var frameLeaseTransferred = false
        return try {
            val (_, targetView) = preparedSceneTarget.borrow()
            val pipelineByStructural = linkedMapOf<
                org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey,
                GPUWgpu4kCorePrimitiveInvariantHandles
                >()
            cacheKeys.forEach { (structuralKey, cacheKey) ->
                val handles = when (val acquired = sessionCache.acquire(cacheKey)) {
                    is GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired -> acquired.handles
                    is GPUWgpu4kCorePrimitiveSessionCacheAcquire.Refused -> {
                        synchronized(this) { materializing = false }
                        return refusedSessionCacheAcquire(acquired.reason)
                    }
                }
                pipelineByStructural[structuralKey] = handles
            }
            val pathRequirement = GPUWgpu4kCorePrimitivePathDepthStencilRequirement(
                targetBounds.width,
                targetBounds.height,
                GPUTextureFormat.Depth24PlusStencil8,
                1,
                GPUTextureUsage.RenderAttachment,
            )
            frameLease = when (val checkout = sessionCache.acquireFrame(
                GPUWgpu4kCorePrimitiveFramePoolRequirements(
                    generationSeal.deviceGeneration,
                    vertexBytes,
                    indexBytes,
                    uniformPlan.totalBytes,
                    pathRequirement,
                ),
            )) {
                is GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired -> checkout.lease
                is GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused -> {
                    synchronized(this) { materializing = false }
                    return refusedPoolCheckout(checkout.reason)
                }
            }
            val pooled = requireNotNull(frameLease)
            val pathHandles = requireNotNull(pooled.handles.pathDepthStencil)
            require(pathHandles.requirement == pathRequirement) {
                "Pooled CorePrimitive path depth/stencil attachment differs from its exact requirement"
            }
            uploadExact(
                pooled.handles.vertexBuffer,
                ArrayBuffer.of(vertexData),
                vertexBytes,
                pooled.capacities.vertexBytes,
            )
            uploadExact(
                pooled.handles.indexBuffer,
                ArrayBuffer.of(indexData),
                indexBytes,
                pooled.capacities.indexBytes,
            )
            uploadExact(
                pooled.handles.uniformBuffer,
                ArrayBuffer.of(uniformSlabSeal.packedBytesForUpload()),
                uniformPlan.totalBytes,
                pooled.capacities.uniformBytes,
            )
            val targetOperand = GPUPreparedNativeTextureViewOperand(
                targetView,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            )
            val depthStencilOperand = GPUPreparedNativeTextureViewOperand(
                pathHandles.view,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            )
            val vertexOperand = GPUPreparedNativeBufferOperand(
                pooled.handles.vertexBuffer,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
                pooled.capacities.vertexBytes,
            )
            val indexOperand = GPUPreparedNativeBufferOperand(
                pooled.handles.indexBuffer,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
                pooled.capacities.indexBytes,
            )
            val bindGroupOperand = GPUPreparedNativeBindGroupOperand(
                pooled.handles.bindGroup,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            )
            val pipelineOperands = pipelineByStructural.mapValues { (_, handles) ->
                GPUPreparedNativeRenderPipelineOperand(
                    handles.pipeline,
                    generationSeal.deviceGeneration,
                    GPUPreparedNativeOperandOwnership.Borrowed,
                )
            }
            val commands = buildList {
                add(
                    GPUPreparedNativeRenderCommand.SetVertexBuffer(
                        0,
                        vertexOperand,
                        0L,
                        vertexBytes,
                        8L,
                    ),
                )
                add(
                    GPUPreparedNativeRenderCommand.SetIndexBuffer(
                        indexOperand,
                        GPUPreparedNativeIndexFormat.Uint32,
                        0L,
                        indexBytes,
                    ),
                )
                add(GPUPreparedNativeRenderCommand.SetStencilReference(0u))
                packetPlans.forEachIndexed { index, plan ->
                    val slice = arena.slices[index]
                    add(
                        GPUPreparedNativeRenderCommand.SetPipeline(
                            requireNotNull(pipelineOperands[plan.structuralKey]),
                        ),
                    )
                    add(
                        GPUPreparedNativeRenderCommand.SetBindGroup(
                            0,
                            bindGroupOperand,
                            listOf(uniformPlan.slots[plan.uniformSlotIndex].alignedOffset),
                        ),
                    )
                    add(
                        GPUPreparedNativeRenderCommand.SetScissor(
                            plan.semantic.scissorBounds.left,
                            plan.semantic.scissorBounds.top,
                            plan.semantic.scissorBounds.width,
                            plan.semantic.scissorBounds.height,
                        ),
                    )
                    add(
                        GPUPreparedNativeRenderCommand.DrawIndexed(
                            GPUPreparedNativeDrawCall.DrawIndexed(
                                slice.indexCount,
                                firstIndex = slice.firstIndex,
                                baseVertex = slice.baseVertex,
                                vertexCount = slice.vertexCount,
                                maxLocalIndex = slice.maxLocalIndex,
                            ),
                        ),
                    )
                }
            }
            val renderOperand = GPUPreparedNativeScopeOperand.Render(
                sourceStepIndex = renderScope.sourceStepIndex,
                pass = GPUPreparedNativeRenderPassConfig(
                    colorTarget = targetOperand,
                    depthStencilTarget = depthStencilOperand,
                    loadOperation = GPUPreparedNativeLoadOperation.Clear,
                    storeOperation = GPUPreparedNativeStoreOperation.Store,
                    clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0),
                    depthReadOnly = true,
                    stencilClearValue = 0u,
                    stencilLoadOperation = GPUPreparedNativeLoadOperation.Clear,
                    stencilStoreOperation = GPUPreparedNativeStoreOperation.Discard,
                    stencilReadOnly = false,
                ),
                commands = commands,
                semanticPayloads = packetPlans.map(PacketPlan::semantic),
                operandLayout = GPUPreparedNativeRenderOperandLayout.IndexedCorePrimitive,
            )
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
                scopeOperands = listOf(renderOperand),
                scopeOperandKeys = listOf(renderScope.nativeOperandKeys),
                leaseLifecycle = GPUWgpu4kCorePrimitivePayloadLeaseLifecycle(pooled),
            )
            val result = GPUPreparedNativeFramePayloadMaterialization.Materialized(
                GPUPreparedNativeFrameDraft(payload),
            )
            synchronized(this) {
                check(!closed) { "Native CorePrimitive materializer closed during indexed materialization" }
                preRegistrationHandles.transferAll()
                materializing = false
                frameLeaseTransferred = true
            }
            result
        } catch (failure: Throwable) {
            if (!frameLeaseTransferred) terminalizePooledLeaseBeforeRegistration(frameLease)
            synchronized(this) {
                materializing = false
                preRegistrationHandles.closeRetainingFailures()
            }
            refused(
                "failed.native-core-primitive.materialization",
                "Public wgpu4k indexed CorePrimitive materialization failed: " +
                    "${failure::class.simpleName.orEmpty()}: ${failure.message.orEmpty()}.",
            )
        }
    }

    @Synchronized
    override fun close() {
        closed = true
        if (!materializing) preRegistrationHandles.closeRetainingFailures()
    }

    private fun refused(code: String, message: String) =
        refusedWgpu4kPreRegistrationMaterialization(code, message, preRegistrationHandles)

    private fun refusedPoolCheckout(
        reason: GPUWgpu4kCorePrimitiveFramePoolRefusal,
    ): GPUPreparedNativeFramePayloadMaterialization.Refused = when (reason) {
        is GPUWgpu4kCorePrimitiveFramePoolRefusal.DeviceGenerationMismatch -> refused(
            "stale.native-core-primitive.frame-pool-generation",
            "CorePrimitive frame-pool generation ${reason.expected.value} does not match " +
                "${reason.observed.value}.",
        )
        is GPUWgpu4kCorePrimitiveFramePoolRefusal.InvalidCapacity -> refused(
            "invalid.native-core-primitive.frame-pool-capacity",
            "CorePrimitive ${reason.resource.name} requires a positive host-addressable byte range.",
        )
        is GPUWgpu4kCorePrimitiveFramePoolRefusal.AllocationFailed -> refused(
            "failed.native-core-primitive.frame-pool-allocation",
            "CorePrimitive ${reason.resource.name} pooled allocation failed: ${reason.failureType}.",
        )
        is GPUWgpu4kCorePrimitiveFramePoolRefusal.Saturated -> refused(
            "unsupported.native-core-primitive.frame-pool-saturated",
            "CorePrimitive frame pool already has ${reason.maxSlots} live slots.",
        )
        GPUWgpu4kCorePrimitiveFramePoolRefusal.Closing,
        GPUWgpu4kCorePrimitiveFramePoolRefusal.Closed,
        -> refused(
            "unsupported.native-core-primitive.frame-pool-closed",
            "CorePrimitive frame pool is closing or closed.",
        )
    }

    private fun refusedSessionCacheAcquire(
        reason: GPUWgpu4kCorePrimitiveSessionCacheRefusal,
    ): GPUPreparedNativeFramePayloadMaterialization.Refused = when (reason) {
        is GPUWgpu4kCorePrimitiveSessionCacheRefusal.IncompatibleComponentIdentity -> refused(
            "invalid.native-core-primitive.session-cache-component",
            "CorePrimitive component identity does not match the session cache.",
        )
        is GPUWgpu4kCorePrimitiveSessionCacheRefusal.UnsupportedPipelineIdentity -> refused(
            "unsupported.native-core-primitive.session-cache-pipeline",
            "CorePrimitive render pipeline identity is not executable by this native factory.",
        )
        is GPUWgpu4kCorePrimitiveSessionCacheRefusal.Saturated -> refused(
            "unsupported.native-core-primitive.session-cache-saturated",
            "CorePrimitive session cache already has ${reason.maxEntries} live render pipelines.",
        )
        is GPUWgpu4kCorePrimitiveSessionCacheRefusal.NativeCreationFailed -> refused(
            "failed.native-core-primitive.session-cache-creation",
            "CorePrimitive ${reason.resource.name} creation failed: ${reason.failureType}: ${reason.message}.",
        )
        is GPUWgpu4kCorePrimitiveSessionCacheRefusal.CleanupPending -> refused(
            "failed.native-core-primitive.session-cache-cleanup",
            "CorePrimitive session cache retains ${reason.pendingHandles} native cleanup handle(s).",
        )
        GPUWgpu4kCorePrimitiveSessionCacheRefusal.Closing,
        GPUWgpu4kCorePrimitiveSessionCacheRefusal.Closed,
        -> refused(
            "unsupported.native-core-primitive.session-cache-closed",
            "CorePrimitive session cache is closing or closed.",
        )
    }

    private fun terminalizePooledLeaseBeforeRegistration(
        lease: GPUWgpu4kCorePrimitiveFramePoolLease?,
    ) {
        if (lease == null) return
        if (lease.rollbackBeforeSubmit() is GPUWgpu4kCorePrimitiveFramePoolLeaseTransition.Applied) return
        lease.quarantineUncertain()
    }

    private fun <T : AutoCloseable> T.tracked(): T = preRegistrationHandles.track(this)

    private fun uploadExact(
        buffer: GPUBuffer,
        data: ArrayBuffer,
        usedBytes: Long,
        capacityBytes: Long,
    ) {
        require(usedBytes >= 0L) { "CorePrimitive upload byte count must be non-negative" }
        require(usedBytes <= capacityBytes) { "CorePrimitive upload exceeds its native buffer capacity" }
        val explicitSize = usedBytes.toULong()
        require(explicitSize <= data.size) { "CorePrimitive upload exceeds its host data range" }
        queue.writeBuffer(buffer, 0uL, data, 0uL, explicitSize)
    }

    private companion object {
        const val RGBA8_UNORM = "rgba8unorm"
        const val DEPTH24PLUS_STENCIL8 = "depth24plus-stencil8"
        const val CORE_PRIMITIVE_UNIFORM_BYTES = 32
        const val RGBA_BYTES_PER_PIXEL = 4L
        const val WEBGPU_COPY_ROW_ALIGNMENT = 256L
    }
}

private class GPUWgpu4kCorePrimitivePayloadLeaseLifecycle(
    private val lease: GPUWgpu4kCorePrimitiveFramePoolLease,
) : GPUPreparedNativeFrameLeaseLifecycle {
    override fun releaseBeforeSubmit(): GPUPreparedNativeFrameLeaseTransition =
        lease.rollbackBeforeSubmit().toPreparedTransition()

    override fun markSubmitted(): GPUPreparedNativeFrameLeaseTransition =
        lease.markSubmitted().toPreparedTransition()

    override fun releaseAfterCompletion(): GPUPreparedNativeFrameLeaseTransition =
        lease.completeSuccessfully().toPreparedTransition()

    override fun quarantineUncertain(): GPUPreparedNativeFrameLeaseTransition =
        lease.quarantineUncertain().toPreparedTransition()
}

private fun GPUWgpu4kCorePrimitiveFramePoolLeaseTransition.toPreparedTransition():
    GPUPreparedNativeFrameLeaseTransition = when (this) {
    GPUWgpu4kCorePrimitiveFramePoolLeaseTransition.Applied ->
        GPUPreparedNativeFrameLeaseTransition.Applied
    is GPUWgpu4kCorePrimitiveFramePoolLeaseTransition.Refused ->
        GPUPreparedNativeFrameLeaseTransition.Refused(reason)
}
