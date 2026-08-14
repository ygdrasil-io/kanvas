package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureView
import java.util.Collections
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUDepthStencilLoadStorePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUStencilLoadOperation
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

internal sealed interface GPUCorePrimitiveRenderRunMaterialization {
    class Ready(
        renderOperands: List<GPUPreparedNativeScopeOperand.Render>,
        val leaseLifecycle: GPUPreparedNativeFrameLeaseLifecycle,
        pathDepthStencilViewAuthority: Map<Int, GPUTextureView>,
    ) : GPUCorePrimitiveRenderRunMaterialization {
        val renderOperands: List<GPUPreparedNativeScopeOperand.Render> =
            immutableList(renderOperands)
        val pathDepthStencilViewAuthority: Map<Int, GPUTextureView> =
            Collections.unmodifiableMap(pathDepthStencilViewAuthority.toMap())

        init {
            require(this.renderOperands.isNotEmpty())
            require(this.renderOperands.map { render -> render.sourceStepIndex }.distinct().size ==
                this.renderOperands.size
            )
            require(this.pathDepthStencilViewAuthority.keys.all { sourceStepIndex ->
                this.renderOperands.any { render -> render.sourceStepIndex == sourceStepIndex }
            })
        }
    }

    data class Refused(
        val code: String,
        val message: String,
    ) : GPUCorePrimitiveRenderRunMaterialization {
        init {
            require(code.isNotBlank() && message.isNotBlank())
        }
    }
}

/**
 * Materializes the frame-global CorePrimitive geometry/uniform arena into one pooled V/I/U lease
 * and, when required, one pooled D24S8 attachment. The caller already owns the global preflight,
 * scene-target borrow, payload assembly, readback, and registration boundaries.
 */
internal class GPUWgpu4kCorePrimitiveRenderRunMaterializer(
    private val queue: GPUQueue,
    private val sessionCache: GPUWgpu4kCorePrimitiveSessionCache,
    private val limits: GPULimits,
) : AutoCloseable {
    @Suppress("UNUSED_PARAMETER")
    fun materializeAcceptedRuns(
        plans: List<GPUCorePrimitiveRenderRunPlan>,
        targetTexture: GPUTexture,
        targetView: GPUTextureView,
        generationSeal: GPUPreparedGenerationSeal,
        dstRead: CorePrimitiveDestinationSnapshotHandles? = null,
    ): GPUCorePrimitiveRenderRunMaterialization {
        val routes = plans.mapNotNull { plan ->
            plan.routeSeal as? GPUCorePrimitiveNativeScopeRouteSeal.Routes
        }
        validateAcceptedPlans(plans, routes, generationSeal)?.let { refusal ->
            return refusal
        }

        val frameRoutes = try {
            routes.first().withOrderedUnits(
                routes.flatMap { route -> route.orderedUnits },
            )
        } catch (_: IllegalArgumentException) {
            return refused(
                "invalid.native-core-primitive.frame-global-route",
                "Frame-global CorePrimitive routes do not form one exact command partition.",
            )
        }
        val arena = try {
            GPUCorePrimitiveNativeScopeGeometryArena.pack(frameRoutes)
        } catch (failure: Throwable) {
            return refused(
                "invalid.native-core-primitive.frame-global-geometry-arena",
                "Frame-global CorePrimitive geometry cannot be packed safely: " +
                    "${failure::class.simpleName.orEmpty()}.",
            )
        }
        val vertexBytes: Long
        val indexBytes: Long
        try {
            vertexBytes = Math.multiplyExact(
                arena.vertexFloatCount.toLong(),
                Float.SIZE_BYTES.toLong(),
            )
            indexBytes = Math.multiplyExact(
                arena.indexCount.toLong(),
                Int.SIZE_BYTES.toLong(),
            )
        } catch (_: ArithmeticException) {
            return refused(
                "invalid.native-core-primitive.frame-global-geometry-arena",
                "Frame-global CorePrimitive geometry byte sizing overflows.",
            )
        }
        if (vertexBytes <= 0L || indexBytes <= 0L ||
            vertexBytes % (2L * Float.SIZE_BYTES) != 0L ||
            indexBytes % Int.SIZE_BYTES != 0L ||
            arena.slices.map { slice -> slice.packetId } !=
            plans.flatMap { plan -> plan.packetIds }
        ) {
            return refused(
                "invalid.native-core-primitive.frame-global-geometry-arena",
                "Frame-global CorePrimitive slices differ from the sealed packet stream.",
            )
        }
        val uniformPlan = frameRoutes.uniformPlan
        if (uniformPlan.deviceGeneration != generationSeal.deviceGeneration.value ||
            uniformPlan.alignmentBytes != limits.minUniformBufferOffsetAlignment ||
            uniformPlan.totalBytes <= 0L ||
            uniformPlan.totalBytes > Int.MAX_VALUE.toLong()
        ) {
            return refused(
                "invalid.native-core-primitive.frame-global-uniform",
                "Frame-global CorePrimitive uniform authority is stale, unaligned, or not host-addressable.",
            )
        }

        val structuralKeys = frameRoutes.orderedUnits.flatMap { unit ->
            when (unit) {
                is GPUCorePrimitiveNativeScopeRouteUnit.Direct ->
                    listOf(unit.structuralPipelineKey)
                is GPUCorePrimitiveNativeScopeRouteUnit.PathPair -> listOf(
                    unit.producerStructuralPipelineKey,
                    unit.coverStructuralPipelineKey,
                )
                is GPUCorePrimitiveNativeScopeRouteUnit.PathProducer ->
                    listOf(unit.structuralPipelineKey)
                is GPUCorePrimitiveNativeScopeRouteUnit.PathCover ->
                    listOf(unit.structuralPipelineKey)
            }
        }.distinct()
        val cacheKeys = linkedMapOf<
            GPUCorePrimitiveRenderPipelineStructuralKey,
            GPUWgpu4kCorePrimitivePipelineCacheKey
            >()
        structuralKeys.forEach { structuralKey ->
            val mapped = mapCorePrimitiveStructuralKeyToWgpu4kPipelineIdentity(
                structuralKey,
            ) as? GPUWgpu4kCorePrimitivePipelineMapping.Mapped ?: return refused(
                "unsupported.native-core-primitive.frame-global-pipeline",
                "A frame-global CorePrimitive structural key has no closed native pipeline.",
            )
            cacheKeys[structuralKey] = GPUWgpu4kCorePrimitivePipelineCacheKey(
                mapped.componentIdentity,
                mapped.identity,
            )
        }
        val hasPath = frameRoutes.orderedUnits.any {
            it is GPUCorePrimitiveNativeScopeRouteUnit.PathPair ||
                it is GPUCorePrimitiveNativeScopeRouteUnit.PathProducer ||
                it is GPUCorePrimitiveNativeScopeRouteUnit.PathCover
        }
        val frameComponentIdentity = if (hasPath) {
            val supportedPathComponents = setOf(
                PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY,
                PRODUCTION_CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_COMPONENT_IDENTITY,
            )
            if (cacheKeys.values.any { key ->
                    key.componentIdentity !in supportedPathComponents &&
                        !key.componentIdentity.isCorePrimitiveDstRead()
                }
            ) {
                return refused(
                    "invalid.native-core-primitive.frame-global-path-pipeline",
                    "PathPair runs require only the exact stencil producer, shared uniform cover, " +
                        "and continued destination-read cover programs.",
                )
            }
            // FP-13 Task 8: a continued destination-read cover run binds its snapshot through
            // the per-mode dst-read component identity, so the frame slot adopts that identity
            // instead of the standard uniform cover component.
            val dstReadIdentities = cacheKeys.values
                .map(GPUWgpu4kCorePrimitivePipelineCacheKey::componentIdentity)
                .filter(GPUWgpu4kCorePrimitiveComponentIdentity::isCorePrimitiveDstRead)
                .distinct()
            when (dstReadIdentities.size) {
                0 -> PRODUCTION_CORE_PRIMITIVE_COMPONENT_IDENTITY
                1 -> dstReadIdentities.single()
                else -> return refused(
                    "invalid.native-core-primitive.frame-global-path-pipeline",
                    "A continued path cover run cannot mix multiple destination-read component identities.",
                )
            }
        } else {
            val componentIdentities = cacheKeys.values
                .map { key -> key.componentIdentity }
                .distinct()
            componentIdentities.singleOrNull() ?: return refused(
                "unsupported.native-core-primitive.frame-global-component",
                "Direct frame-global runs must share one exact bind-group component identity.",
            )
        }

        val pipelineByStructural = linkedMapOf<
            GPUCorePrimitiveRenderPipelineStructuralKey,
            GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired
            >()
        cacheKeys.forEach { (structuralKey, cacheKey) ->
            when (val acquired = sessionCache.acquire(cacheKey)) {
                is GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired ->
                    pipelineByStructural[structuralKey] = acquired
                is GPUWgpu4kCorePrimitiveSessionCacheAcquire.Refused ->
                    return refusedSessionCacheAcquire(acquired.reason)
            }
        }

        val targetBounds = plans.first().renderStep.drawPackets.first()
            .semanticPayload
            .let { semantic ->
                (semantic as GPUDrawSemanticPayload.CorePrimitive).targetBounds
            }
        val pathRequirement = if (hasPath) {
            GPUWgpu4kCorePrimitivePathDepthStencilRequirement(
                width = targetBounds.width,
                height = targetBounds.height,
                format = GPUTextureFormat.Depth24PlusStencil8,
                sampleCount = 1,
                usage = GPUTextureUsage.RenderAttachment,
            )
        } else {
            null
        }
        var frameLease: GPUWgpu4kCorePrimitiveFramePoolLease? = null
        var frameLeaseTransferred = false
        return try {
            frameLease = when (
                val checkout = sessionCache.acquireFrame(
                    GPUWgpu4kCorePrimitiveFramePoolRequirements(
                        deviceGeneration = generationSeal.deviceGeneration,
                        vertexBytes = vertexBytes,
                        indexBytes = indexBytes,
                        uniformBytes = uniformPlan.totalBytes,
                        pathDepthStencil = pathRequirement,
                        componentIdentity = frameComponentIdentity,
                        sampleCount = 1,
                        dstRead = dstRead?.binding,
                    ),
                )
            ) {
                is GPUWgpu4kCorePrimitiveFramePoolCheckout.Acquired -> checkout.lease
                is GPUWgpu4kCorePrimitiveFramePoolCheckout.Refused ->
                    return refusedPoolCheckout(checkout.reason)
            }
            val pooled = requireNotNull(frameLease)
            val pathHandles = pooled.handles.pathDepthStencil
            check((pathRequirement == null) == (pathHandles == null) &&
                pathHandles?.requirement == pathRequirement &&
                pooled.handles.sampleCount == 1 &&
                pooled.handles.msaaColor == null
            ) {
                "Pooled CorePrimitive handles differ from the frame-global single-sample requirements"
            }
            val vertexData = FloatArray(arena.vertexFloatCount).also(arena::copyVerticesInto)
            val indexData = IntArray(arena.indexCount).also(arena::copyIndicesInto)
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
                ArrayBuffer.of(frameRoutes.packedUniformBytesForUpload()),
                uniformPlan.totalBytes,
                pooled.capacities.uniformBytes,
            )

            val targetOperand = GPUPreparedNativeTextureViewOperand(
                targetView,
                generationSeal.deviceGeneration,
                GPUPreparedNativeOperandOwnership.Borrowed,
            )
            val depthStencilOperand = pathHandles?.let { handles ->
                GPUPreparedNativeTextureViewOperand(
                    handles.view,
                    generationSeal.deviceGeneration,
                    GPUPreparedNativeOperandOwnership.Borrowed,
                )
            }
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
            val pipelineOperands = pipelineByStructural.mapValues { (_, acquired) ->
                GPUPreparedNativeRenderPipelineOperand.fromCorePrimitiveAcquisition(
                    acquired,
                    generationSeal.deviceGeneration,
                )
            }

            var unitOffset = 0
            var packetOffset = 0
            val pathAuthority = linkedMapOf<Int, GPUTextureView>()
            val renderOperands = plans.zip(routes).map { (plan, route) ->
                val runHasPath = route.orderedUnits.any {
                    it is GPUCorePrimitiveNativeScopeRouteUnit.PathPair ||
                        it is GPUCorePrimitiveNativeScopeRouteUnit.PathProducer ||
                        it is GPUCorePrimitiveNativeScopeRouteUnit.PathCover
                }
                val runPackets = plan.renderStep.drawPackets
                val runSlices = arena.slices.subList(
                    packetOffset,
                    packetOffset + runPackets.size,
                )
                val commands = if (runHasPath) {
                    indexedCommands(
                        route = route,
                        packets = runPackets,
                        slices = runSlices,
                        globalUnitOffset = unitOffset,
                        uniformPlan = uniformPlan,
                        pipelineOperands = pipelineOperands,
                        vertexOperand = vertexOperand,
                        indexOperand = indexOperand,
                        bindGroupOperand = bindGroupOperand,
                        vertexBytes = vertexBytes,
                        indexBytes = indexBytes,
                    )
                } else {
                    directCommands(
                        route = route,
                        packets = runPackets,
                        slices = runSlices,
                        globalUnitOffset = unitOffset,
                        uniformPlan = uniformPlan,
                        pipelineOperands = pipelineOperands,
                        vertexOperand = vertexOperand,
                        indexOperand = indexOperand,
                        bindGroupOperand = bindGroupOperand,
                        vertexBytes = vertexBytes,
                        indexBytes = indexBytes,
                    )
                }
                val sourceStepIndex = plan.sourceScopeIndices.single()
                if (runHasPath) {
                    pathAuthority[sourceStepIndex] = requireNotNull(pathHandles).view
                }
                unitOffset += route.orderedUnits.size
                packetOffset += runPackets.size
                GPUPreparedNativeScopeOperand.Render(
                    sourceStepIndex = sourceStepIndex,
                    pass = GPUPreparedNativeRenderPassConfig(
                        colorTarget = targetOperand,
                        depthStencilTarget = depthStencilOperand.takeIf { runHasPath },
                        loadOperation = when (plan.loadStore.loadOp) {
                            "clear" -> GPUPreparedNativeLoadOperation.Clear
                            "load" -> GPUPreparedNativeLoadOperation.Load
                            else -> error("Preflight admitted an unsupported CorePrimitive load operation")
                        },
                        storeOperation = GPUPreparedNativeStoreOperation.Store,
                        clearColor = GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0)
                            .takeIf { plan.loadStore.loadOp == "clear" },
                        depthReadOnly = true,
                        stencilClearValue = when (val ds = plan.renderStep.depthStencilLoadStore) {
                            is GPUDepthStencilLoadStorePlan.WritableStencil -> ds.clearValue
                            else -> null
                        },
                        stencilLoadOperation = when (val ds = plan.renderStep.depthStencilLoadStore) {
                            is GPUDepthStencilLoadStorePlan.WritableStencil -> when (ds.loadOperation) {
                                GPUStencilLoadOperation.Clear -> GPUPreparedNativeLoadOperation.Clear
                                GPUStencilLoadOperation.Load -> GPUPreparedNativeLoadOperation.Load
                            }
                            else -> null
                        },
                        stencilStoreOperation = when (val ds = plan.renderStep.depthStencilLoadStore) {
                            is GPUDepthStencilLoadStorePlan.WritableStencil -> when (ds.storeOperation) {
                                GPUStorePlan.Store -> GPUPreparedNativeStoreOperation.Store
                                GPUStorePlan.Discard -> GPUPreparedNativeStoreOperation.Discard
                                GPUStorePlan.ResolveAndStore ->
                                    error("Path stencil authority cannot resolve-and-store the fan")
                            }
                            else -> null
                        },
                        stencilReadOnly = when (plan.renderStep.depthStencilLoadStore) {
                            is GPUDepthStencilLoadStorePlan.WritableStencil -> false
                            else -> true
                        },
                    ),
                    commands = commands,
                    semanticPayloads = runPackets.map { packet ->
                        packet.semanticPayload as GPUDrawSemanticPayload.CorePrimitive
                    },
                    operandLayout = when {
                        runHasPath ->
                            GPUPreparedNativeRenderOperandLayout.IndexedCorePrimitive
                        route.orderedUnits
                            .map { (it as GPUCorePrimitiveNativeScopeRouteUnit.Direct).structuralPipelineKey }
                            .distinct().size > 1 ->
                            GPUPreparedNativeRenderOperandLayout.IndexedCorePrimitive
                        else -> GPUPreparedNativeRenderOperandLayout.CommandOrder
                    },
                )
            }
            check(unitOffset == frameRoutes.orderedUnits.size &&
                packetOffset == arena.slices.size
            ) {
                "Frame-global CorePrimitive run partitions diverged during materialization"
            }
            val ready = GPUCorePrimitiveRenderRunMaterialization.Ready(
                renderOperands = renderOperands,
                leaseLifecycle = GPUWgpu4kCorePrimitivePayloadLeaseLifecycle(pooled),
                pathDepthStencilViewAuthority = pathAuthority,
            )
            frameLeaseTransferred = true
            ready
        } catch (failure: Throwable) {
            if (!frameLeaseTransferred) {
                terminalizePooledLeaseBeforeRegistration(frameLease)
            }
            refused(
                "failed.native-core-primitive.frame-global-materialization",
                "Frame-global wgpu4k CorePrimitive materialization failed: " +
                    "${failure::class.simpleName.orEmpty()}: ${failure.message.orEmpty()}.",
            )
        }
    }

    private fun validateAcceptedPlans(
        plans: List<GPUCorePrimitiveRenderRunPlan>,
        routes: List<GPUCorePrimitiveNativeScopeRouteSeal.Routes>,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUCorePrimitiveRenderRunMaterialization.Refused? {
        if (plans.isEmpty() || routes.size != plans.size) {
            return refused(
                "invalid.native-core-primitive.frame-global-plan",
                "Frame-global CorePrimitive materialization requires at least one exact routed run.",
            )
        }
        val sourceStepIndices = plans.map { plan -> plan.sourceScopeIndices.single() }
        if (sourceStepIndices != sourceStepIndices.sorted() ||
            sourceStepIndices.distinct().size != sourceStepIndices.size
        ) {
            return refused(
                "invalid.native-core-primitive.frame-global-plan",
                "Frame-global CorePrimitive runs must retain unique increasing source scopes.",
            )
        }
        if (plans.any { plan ->
                plan.renderStep.samplePlan != GPUSamplePlan.SingleSampleFrame ||
                    plan.renderStep.sampleContinuation != null ||
                    plan.loadStore.storePlan != GPUStorePlan.Store ||
                    plan.loadStore.loadOp !in setOf("clear", "load")
            }
        ) {
            return refused(
                "unsupported.native-core-primitive.frame-global-render-state",
                "Frame-global CorePrimitive runs require single-sample clear/load and store authority.",
            )
        }
        // Layer-composite frames legitimately mix scene and pooled layer targets in one frame.
        // Every run is still bound to the shared target operand below; the prepared-surface
        // assembler redirects layer-target runs to their pooled attachments afterwards. The
        // legacy single-render routes never observe mixed targets.
        val target = plans.first().target
        val uniformAuthorityRoute = routes.first()
        if (routes.any { route ->
                !route.hasSameUniformAuthority(uniformAuthorityRoute)
            } ||
            routes.flatMap { route -> route.commandIds } !=
            uniformAuthorityRoute.uniformCommandIds ||
            routes.flatMap { route -> route.flattenedPacketIds } !=
            plans.flatMap { plan -> plan.packetIds }
        ) {
            return refused(
                "invalid.native-core-primitive.frame-global-route",
                "CorePrimitive runs must exactly partition one frame-global route and uniform slab.",
            )
        }
        val packets = plans.flatMap { plan -> plan.renderStep.drawPackets }
        val semantics = packets.map { packet ->
            packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
                ?: return refused(
                    "invalid.native-core-primitive.frame-global-semantic",
                    "Every frame-global CorePrimitive packet requires one typed semantic payload.",
                )
        }
        if (packets.map { packet -> packet.packetId }.distinct().size != packets.size ||
            semantics.map { semantic -> semantic.targetBounds }.distinct().size != 1
        ) {
            return refused(
                "invalid.native-core-primitive.frame-global-semantic",
                "Frame-global CorePrimitive packet and target-bound authorities must remain unique and exact.",
            )
        }
        if (uniformAuthorityRoute.uniformPlan.deviceGeneration !=
            generationSeal.deviceGeneration.value
        ) {
            return refused(
                "stale.native-core-primitive.frame-global-generation",
                "Frame-global CorePrimitive uniform authority does not match the sealed device generation.",
            )
        }
        routes.zip(plans).forEach { (route, plan) ->
            val hasPath = route.orderedUnits.any {
                it is GPUCorePrimitiveNativeScopeRouteUnit.PathPair ||
                    it is GPUCorePrimitiveNativeScopeRouteUnit.PathProducer ||
                    it is GPUCorePrimitiveNativeScopeRouteUnit.PathCover
            }
            if (hasPath != (plan.renderStep.depthStencilLoadStore != null) ||
                (!hasPath && route.orderedUnits.any {
                    it !is GPUCorePrimitiveNativeScopeRouteUnit.Direct
                })
            ) {
                return refused(
                    "invalid.native-core-primitive.frame-global-path-authority",
                    "Each CorePrimitive run must retain exact direct or PathPair depth/stencil authority.",
                )
            }
            if (!hasPath &&
                route.orderedUnits.any { unit ->
                    unit !is GPUCorePrimitiveNativeScopeRouteUnit.Direct ||
                        unit.structuralPipelineKey.role !=
                        GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading
                }
            ) {
                return refused(
                    "invalid.native-core-primitive.frame-global-direct-pipeline",
                    "Every direct CorePrimitive run requires exact shading structural pipelines.",
                )
            }
        }
        return null
    }

    private fun directCommands(
        route: GPUCorePrimitiveNativeScopeRouteSeal.Routes,
        packets: List<org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket>,
        slices: List<GPUCorePrimitiveNativeScopeGeometrySlice>,
        globalUnitOffset: Int,
        uniformPlan: org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlan,
        pipelineOperands: Map<
            GPUCorePrimitiveRenderPipelineStructuralKey,
            GPUPreparedNativeRenderPipelineOperand
            >,
        vertexOperand: GPUPreparedNativeBufferOperand,
        indexOperand: GPUPreparedNativeBufferOperand,
        bindGroupOperand: GPUPreparedNativeBindGroupOperand,
        vertexBytes: Long,
        indexBytes: Long,
    ): List<GPUPreparedNativeRenderCommand> {
        val units = route.orderedUnits.map {
            it as GPUCorePrimitiveNativeScopeRouteUnit.Direct
        }
        return buildList {
            var lastPipeline = requireNotNull(
                pipelineOperands[units.first().structuralPipelineKey],
            ) {
                "A direct CorePrimitive run requires an exact pipeline for its first structural key"
            }
            add(GPUPreparedNativeRenderCommand.SetPipeline(lastPipeline))
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
            units.indices.forEach { index ->
                val semantic =
                    packets[index].semanticPayload as GPUDrawSemanticPayload.CorePrimitive
                val scissor = units[index].route.renderScissor ?: semantic.scissorBounds
                val slice = slices[index]
                val pipeline = requireNotNull(
                    pipelineOperands[units[index].structuralPipelineKey],
                ) {
                    "A direct CorePrimitive run requires an exact pipeline for every structural key"
                }
                if (pipeline !== lastPipeline) {
                    add(GPUPreparedNativeRenderCommand.SetPipeline(pipeline))
                    lastPipeline = pipeline
                }
                if (pipeline.bindingPolicy ==
                    GPUPreparedNativeRenderPipelineBindingPolicy.BindGroupRequired
                ) {
                    add(
                        GPUPreparedNativeRenderCommand.SetBindGroup(
                            0,
                            bindGroupOperand,
                            listOf(uniformPlan.slots[globalUnitOffset + index].alignedOffset),
                        ),
                    )
                }
                add(
                    GPUPreparedNativeRenderCommand.SetScissor(
                        scissor.left,
                        scissor.top,
                        scissor.width,
                        scissor.height,
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
    }

    private fun indexedCommands(
        route: GPUCorePrimitiveNativeScopeRouteSeal.Routes,
        packets: List<org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket>,
        slices: List<GPUCorePrimitiveNativeScopeGeometrySlice>,
        globalUnitOffset: Int,
        uniformPlan: org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlan,
        pipelineOperands: Map<
            GPUCorePrimitiveRenderPipelineStructuralKey,
            GPUPreparedNativeRenderPipelineOperand
            >,
        vertexOperand: GPUPreparedNativeBufferOperand,
        indexOperand: GPUPreparedNativeBufferOperand,
        bindGroupOperand: GPUPreparedNativeBindGroupOperand,
        vertexBytes: Long,
        indexBytes: Long,
    ): List<GPUPreparedNativeRenderCommand> = buildList {
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
        var packetIndex = 0
        route.orderedUnits.forEachIndexed { unitIndex, unit ->
            val structuralKeys = when (unit) {
                is GPUCorePrimitiveNativeScopeRouteUnit.Direct ->
                    listOf(unit.structuralPipelineKey)
                is GPUCorePrimitiveNativeScopeRouteUnit.PathPair -> listOf(
                    unit.producerStructuralPipelineKey,
                    unit.coverStructuralPipelineKey,
                )
                is GPUCorePrimitiveNativeScopeRouteUnit.PathProducer ->
                    listOf(unit.structuralPipelineKey)
                is GPUCorePrimitiveNativeScopeRouteUnit.PathCover ->
                    listOf(unit.structuralPipelineKey)
            }
            structuralKeys.forEach { structuralKey ->
                val semantic =
                    packets[packetIndex].semanticPayload as GPUDrawSemanticPayload.CorePrimitive
                val scissor = when (unit) {
                    is GPUCorePrimitiveNativeScopeRouteUnit.Direct ->
                        unit.route.renderScissor ?: semantic.scissorBounds
                    is GPUCorePrimitiveNativeScopeRouteUnit.PathPair,
                    is GPUCorePrimitiveNativeScopeRouteUnit.PathProducer,
                    is GPUCorePrimitiveNativeScopeRouteUnit.PathCover,
                    -> semantic.scissorBounds
                }
                val slice = slices[packetIndex]
                val pipeline = requireNotNull(pipelineOperands[structuralKey])
                add(
                    GPUPreparedNativeRenderCommand.SetPipeline(
                        pipeline,
                    ),
                )
                if (pipeline.bindingPolicy ==
                    GPUPreparedNativeRenderPipelineBindingPolicy.BindGroupRequired
                ) {
                    add(
                        GPUPreparedNativeRenderCommand.SetBindGroup(
                            0,
                            bindGroupOperand,
                            listOf(
                                uniformPlan.slots[globalUnitOffset + unitIndex].alignedOffset,
                            ),
                        ),
                    )
                }
                add(
                    GPUPreparedNativeRenderCommand.SetScissor(
                        scissor.left,
                        scissor.top,
                        scissor.width,
                        scissor.height,
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
                packetIndex += 1
            }
        }
        check(packetIndex == packets.size && packetIndex == slices.size)
    }

    private fun uploadExact(
        buffer: io.ygdrasil.webgpu.GPUBuffer,
        data: ArrayBuffer,
        usedBytes: Long,
        capacityBytes: Long,
    ) {
        require(usedBytes >= 0L && usedBytes <= capacityBytes)
        val explicitSize = usedBytes.toULong()
        require(explicitSize <= data.size)
        queue.writeBuffer(buffer, 0uL, data, 0uL, explicitSize)
    }

    private fun refusedPoolCheckout(
        reason: GPUWgpu4kCorePrimitiveFramePoolRefusal,
    ): GPUCorePrimitiveRenderRunMaterialization.Refused = when (reason) {
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
    ): GPUCorePrimitiveRenderRunMaterialization.Refused = when (reason) {
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
        if (lease.rollbackBeforeSubmit() is
            GPUWgpu4kCorePrimitiveFramePoolLeaseTransition.Applied
        ) {
            return
        }
        lease.quarantineUncertain()
    }

    private fun refused(
        code: String,
        message: String,
    ) = GPUCorePrimitiveRenderRunMaterialization.Refused(code, message)

    override fun close() = Unit
}
