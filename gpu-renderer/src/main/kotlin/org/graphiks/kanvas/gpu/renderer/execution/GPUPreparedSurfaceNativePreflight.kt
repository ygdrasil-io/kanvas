package org.graphiks.kanvas.gpu.renderer.execution

import java.security.MessageDigest
import java.util.IdentityHashMap
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedImageUploadArtifact
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatch
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandStream
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.passes.fromBatchPlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageBindingLayoutTopology
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageSampling
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameReadbackRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackLayout
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackPixelFormat
import org.graphiks.kanvas.gpu.renderer.recording.GPUSurfaceOutputDescriptor
import org.graphiks.kanvas.gpu.renderer.recording.GPUSurfaceOutputRef
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedConcreteResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUImageFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedImageUniformAllocation
import org.graphiks.kanvas.gpu.renderer.resources.GPUReadbackStagingLease
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseCacheResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseKind
import org.graphiks.kanvas.gpu.renderer.resources.GPU_PREPARED_IMAGE_UNIFORM_ALLOCATION_SIZE_BYTES
import org.graphiks.kanvas.gpu.renderer.resources.preparedImageDescriptorHash
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

private data class GPUPreparedSurfaceArtifactByteEvidence(
    val tightRgba8Bytes: ByteArray,
    val contentHash: String,
)

internal sealed interface GPUPreparedSurfaceNativeRunPlan {
    data class Core(val plan: GPUCorePrimitiveRenderRunPlan) :
        GPUPreparedSurfaceNativeRunPlan

    data class Image(val plan: GPUPreparedSurfaceImageRenderRunPlan) :
        GPUPreparedSurfaceNativeRunPlan
}

internal class GPUPreparedSurfaceImageFramePlan(
    val resourcePlan: GPUImageFrameResourcePlan,
    val uploadScopeKey: GPUPreparedNativeScopeKey,
    consumerRenderScopeIndices: List<Int>,
) {
    val consumerRenderScopeIndices: List<Int> =
        immutableList(consumerRenderScopeIndices)

    init {
        require(uploadScopeKey.operationKind == GPUEncoderOperationKind.Upload)
        require(this.consumerRenderScopeIndices.isNotEmpty() &&
            this.consumerRenderScopeIndices.distinct().size ==
            this.consumerRenderScopeIndices.size &&
            this.consumerRenderScopeIndices.all { it > uploadScopeKey.sourceStepIndex }
        ) {
            "One frame-global image upload must precede every unique consuming render"
        }
    }
}

internal class GPUPreparedSurfaceImageRenderRunPlan(
    val sourceScopeIndex: Int,
    val renderStep: GPUFrameStep.RenderPassStep,
    packets: List<GPUDrawSemanticPayload.SampledImage>,
    resourcePlans: List<GPUImageFrameResourcePlan>,
    orderedBindings: List<org.graphiks.kanvas.gpu.renderer.resources.GPUImageBindingRequest>,
    uniformAllocations: List<GPUPreparedImageUniformAllocation>,
    val exactScopeKey: GPUPreparedNativeScopeKey,
) {
    val sourceScopeIndices: List<Int> = listOf(sourceScopeIndex)
    val packets: List<GPUDrawSemanticPayload.SampledImage> = immutableList(packets)
    val resourcePlans: List<GPUImageFrameResourcePlan> = immutableList(resourcePlans)
    val artifactKeys = immutableList(resourcePlans.map { plan -> plan.artifactKey })
    val orderedBindings = immutableList(orderedBindings)
    val uniformAllocations: List<GPUPreparedImageUniformAllocation> =
        immutableList(uniformAllocations)

    init {
        require(sourceScopeIndex >= 0 &&
            exactScopeKey.sourceStepIndex == sourceScopeIndex &&
            exactScopeKey.operationKind == GPUEncoderOperationKind.Render &&
            exactScopeKey.operandKeys.isNotEmpty()
        ) {
            "A prepared-image run must retain one exact render-only scope"
        }
        require(this.packets.isNotEmpty() &&
            renderStep.drawPackets.mapNotNull(GPUDrawPacket::semanticPayload) == this.packets &&
            this.orderedBindings.map { binding -> binding.packetId } ==
            renderStep.drawPackets.map { packet -> packet.packetId.value } &&
            this.packets.size == this.uniformAllocations.size &&
            this.orderedBindings.map { binding -> binding.uniformAllocation } ==
            this.uniformAllocations &&
            this.artifactKeys.isNotEmpty() &&
            this.artifactKeys.distinct().size == this.artifactKeys.size &&
            this.packets.map { packet -> packet.artifact.key.value }.toSet() ==
            this.artifactKeys.map { key -> key.value }.toSet()
        ) {
            "A prepared-image render run must retain exact packets, artifacts, and ABI allocations"
        }
    }
}

internal class GPUCorePrimitiveRenderRunPlan(
    sourceScopeIndices: List<Int>,
    packetIds: List<GPUDrawPacketID>,
    val renderStep: GPUFrameStep.RenderPassStep,
    preparationRequests: List<GPUResourcePreparationRequest>,
    resourceEvidences: List<GPUPreparedResourceEvidence>,
    val routeSeal: GPUCorePrimitiveNativeScopeRouteSeal,
    val exactScopeKey: GPUPreparedNativeScopeKey,
) {
    val sourceScopeIndices: List<Int> = immutableList(sourceScopeIndices)
    val packetIds: List<GPUDrawPacketID> = immutableList(packetIds)
    val preparationRequests: List<GPUResourcePreparationRequest> =
        immutableList(preparationRequests)
    val resourceEvidences: List<GPUPreparedResourceEvidence> =
        immutableList(resourceEvidences)
    val target: GPUFrameTargetRef = renderStep.target
    val loadStore: GPULoadStorePlan = renderStep.loadStore

    init {
        require(this.sourceScopeIndices.isNotEmpty() &&
            this.sourceScopeIndices.all { it >= 0 } &&
            this.sourceScopeIndices.distinct().size == this.sourceScopeIndices.size
        )
        require(this.packetIds.isNotEmpty() &&
            this.packetIds.distinct().size == this.packetIds.size &&
            renderStep.drawPackets.map(GPUDrawPacket::packetId) == this.packetIds &&
            renderStep.resourceUses.map { use -> use.resource } ==
            this.preparationRequests.map(GPUResourcePreparationRequest::resource) &&
            this.preparationRequests.map(GPUResourcePreparationRequest::resource) ==
            this.resourceEvidences.map(GPUPreparedResourceEvidence::logicalResource)
        )
        require(routeSeal is GPUCorePrimitiveNativeScopeRouteSeal.Routes &&
            routeSeal.flattenedPacketIds == this.packetIds &&
            exactScopeKey.sourceStepIndex == this.sourceScopeIndices.single() &&
            exactScopeKey.operationKind == GPUEncoderOperationKind.Render &&
            exactScopeKey.operandKeys.isNotEmpty()
        ) {
            "A mixed CorePrimitive run requires its exact route, target, load/store, and scope seals"
        }
    }
}

internal class GPUPreparedSurfaceReadbackSeal(
    val sourceStepIndex: Int,
    val source: GPUFrameTargetRef,
    val staging: GPUFrameBufferRef,
    val request: GPUFrameReadbackRequest,
    val layout: GPUReadbackLayout,
    val stagingLease: GPUReadbackStagingLease,
    val exactScopeKey: GPUPreparedNativeScopeKey,
) {
    init {
        require(sourceStepIndex >= 0 &&
            exactScopeKey.sourceStepIndex == sourceStepIndex &&
            exactScopeKey.operationKind == GPUEncoderOperationKind.Readback &&
            exactScopeKey.operandKeys.isNotEmpty() &&
            stagingLease.resourceRef.value.isNotBlank()
        ) {
            "Prepared-surface readback must retain one exact scope and output-owned staging lease"
        }
    }
}

internal class GPUPreparedSurfaceChainSeal(
    val acquireStepIndex: Int,
    val blitStepIndex: Int,
    val presentStepIndex: Int,
    val descriptor: GPUSurfaceOutputDescriptor,
    val scene: GPUFrameTargetRef,
    val output: GPUSurfaceOutputRef,
    sourceTaskIds: List<GPUTaskID>,
    val exactBlitScopeKey: GPUPreparedNativeScopeKey,
) {
    val sourceTaskIds: List<GPUTaskID> = immutableList(sourceTaskIds)

    init {
        require(acquireStepIndex >= 0 &&
            blitStepIndex == acquireStepIndex + 1 &&
            presentStepIndex == blitStepIndex + 1 &&
            descriptor.output == output &&
            this.sourceTaskIds.isNotEmpty() &&
            exactBlitScopeKey.sourceStepIndex == blitStepIndex &&
            exactBlitScopeKey.operationKind == GPUEncoderOperationKind.SurfaceBlit
        ) {
            "Prepared-surface output must retain one exact acquire, blit, and present suffix"
        }
    }
}

internal class GPUPreparedSurfaceNativePreflightPlan(
    val frameId: GPUFrameID,
    val encoderPlanId: String,
    val contextIdentity: String,
    val sceneTarget: GPUFrameTargetRef,
    val resources: GPUPreparedResourceSet,
    val readback: GPUPreparedSurfaceReadbackSeal?,
    val surfaceChain: GPUPreparedSurfaceChainSeal?,
    orderedRuns: List<GPUPreparedSurfaceNativeRunPlan>,
    imageFrames: List<GPUPreparedSurfaceImageFramePlan>,
    exactScopeKeys: List<GPUPreparedNativeScopeKey>,
    val generationSeal: GPUPreparedGenerationSeal,
) {
    val orderedRuns: List<GPUPreparedSurfaceNativeRunPlan> = immutableList(orderedRuns)
    val imageFrames: List<GPUPreparedSurfaceImageFramePlan> = immutableList(imageFrames)
    val exactScopeKeys: List<GPUPreparedNativeScopeKey> = immutableList(exactScopeKeys)

    init {
        require(encoderPlanId.isNotBlank() && contextIdentity.isNotBlank())
        require(this.orderedRuns.isNotEmpty() && this.imageFrames.isNotEmpty())
        require(this.imageFrames.map { frame -> frame.resourcePlan.artifactKey }
            .distinct().size == this.imageFrames.size
        ) { "Global image frame plans must be unique per artifact" }
        require(this.exactScopeKeys.map(GPUPreparedNativeScopeKey::sourceStepIndex)
            .distinct().size == this.exactScopeKeys.size
        ) { "Global mixed preflight scope identities must be unique" }
        val expectedScopeKeys = buildList {
            addAll(this@GPUPreparedSurfaceNativePreflightPlan.imageFrames.map { it.uploadScopeKey })
            addAll(this@GPUPreparedSurfaceNativePreflightPlan.orderedRuns.map { run ->
                when (run) {
                    is GPUPreparedSurfaceNativeRunPlan.Core -> run.plan.exactScopeKey
                    is GPUPreparedSurfaceNativeRunPlan.Image -> run.plan.exactScopeKey
                }
            })
            readback?.let { add(it.exactScopeKey) }
            surfaceChain?.let { add(it.exactBlitScopeKey) }
        }.sortedBy(GPUPreparedNativeScopeKey::sourceStepIndex)
        require(expectedScopeKeys == this.exactScopeKeys) {
            "Global mixed preflight scopes must be one exact, complete ordered partition"
        }
        this.imageFrames.forEach { imageFrame ->
            val exactConsumers = this.orderedRuns.mapNotNull { run ->
                (run as? GPUPreparedSurfaceNativeRunPlan.Image)
                    ?.plan
                    ?.takeIf { plan ->
                        plan.resourcePlans.any { it === imageFrame.resourcePlan }
                    }
                    ?.sourceScopeIndex
            }
            require(exactConsumers == imageFrame.consumerRenderScopeIndices) {
                "Global image consumers must be derived exactly from ordered image runs"
            }
        }
        val coreRoutes = this.orderedRuns
            .filterIsInstance<GPUPreparedSurfaceNativeRunPlan.Core>()
            .map { run -> run.plan.routeSeal as GPUCorePrimitiveNativeScopeRouteSeal.Routes }
        require(coreRoutes.map { route -> route.uniformSlabSeal }.distinct().size == 1 &&
            coreRoutes.flatMap { route -> route.commandIds } ==
            coreRoutes.first().uniformSlabSeal.commandIds
        ) {
            "Mixed CorePrimitive runs must exactly partition one frame-global uniform slab"
        }
    }
}

internal sealed interface GPUPreparedSurfaceNativePreflightResult {
    data class Accepted(val plan: GPUPreparedSurfaceNativePreflightPlan) :
        GPUPreparedSurfaceNativePreflightResult

    data class Refused(val code: String, val message: String) :
        GPUPreparedSurfaceNativePreflightResult {
        init {
            require(code.isNotBlank() && message.isNotBlank())
        }
    }
}

/**
 * Pure handle-free admission for the one closed mixed prepared-surface shape. It consumes only
 * evidence already produced by semantic/resource preflight and cannot create a frame, draft,
 * completion ticket, rollback owner, native handle, cache entry, or surface lease.
 */
internal class GPUPreparedSurfaceNativePreflight {
    internal fun validateFramePlan(
        framePlan: GPUFramePlan,
        context: GPUFramePreflightContext? = null,
    ): GPUPreparedSurfaceNativePreflightResult.Refused? {
        val renders = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        val packets = renders.flatMap(GPUFrameStep.RenderPassStep::drawPackets)
        val semantics = packets.map(GPUDrawPacket::semanticPayload)
        if (packets.isEmpty() ||
            semantics.any { it == null } ||
            packets.map(GPUDrawPacket::packetId).distinct().size != packets.size
        ) {
            return refused(
                "unsupported.prepared-surface.semantic-shape",
                "Mixed prepared surfaces require one unique packet and one typed semantic per packet.",
            )
        }
        val semanticTypes = semantics.filterNotNull()
            .map(GPUDrawSemanticPayload::canonicalType)
            .toSet()
        if (semanticTypes != setOf("CorePrimitive", "SampledImage") ||
            renders.any { render ->
                render.drawPackets
                    .mapNotNull(GPUDrawPacket::semanticPayload)
                    .map(GPUDrawSemanticPayload::canonicalType)
                    .toSet().size != 1
            }
        ) {
            return refused(
                "unsupported.prepared-surface.semantic-shape",
                "The mixed route accepts only homogeneous ordered CorePrimitive and SampledImage runs.",
            )
        }
        if (framePlan.steps.any {
                it is GPUFrameStep.CopyDestinationStep ||
                    it is GPUFrameStep.CopyAsDrawMaterializationStep
            }
        ) {
            return refused(
                "unsupported.prepared-surface.destination-copy",
                "The direct mixed prepared route does not admit destination-copy scopes.",
            )
        }
        if (framePlan.steps.any { step -> !step.isPreparedSurfaceStep() }) {
            return refused(
                "unsupported.prepared-surface.encoder-shape",
                "The mixed prepared route contains an unsupported frame step.",
            )
        }
        if (renders.anyIndexed { index, render ->
                render.loadStore.loadOp != (if (index == 0) "clear" else "load") ||
                    render.loadStore.clearColorLabel != null ||
                    render.loadStore.storePlan != GPUStorePlan.Store
            }
        ) {
            return refused(
                "invalid.prepared-surface.render-load-store",
                "The first mixed render must clear and every later render must load; all must store.",
            )
        }
        if (renders.any { render ->
                render.samplePlan != GPUSamplePlan.SingleSampleFrame ||
                    render.sampleContinuation != null
            }
        ) {
            return refused(
                "unsupported.prepared-surface.sample-plan",
                "The mixed prepared route admits only single-sample render runs without continuation.",
            )
        }
        if (packets.filter { packet ->
                packet.semanticPayload is GPUDrawSemanticPayload.CorePrimitive
            }.any { packet ->
                packet.role !in setOf(
                    GPUDrawPacketRole.Shading,
                    GPUDrawPacketRole.PathStencilProducer,
                    GPUDrawPacketRole.PathStencilCover,
                )
            }
        ) {
            return refused(
                "unsupported.prepared-surface.core-route",
                "Mixed CorePrimitive runs accept only shading and path stencil producer/cover roles.",
            )
        }
        if (packets.mapNotNull(GPUDrawPacket::semanticPayload)
                .filterIsInstance<GPUDrawSemanticPayload.CorePrimitive>()
                .any { semantic ->
                    val path = semantic.geometry as?
                        GPUCorePrimitiveGeometry.TriangulatedPath
                    path != null && path.geometryMode !in setOf(
                            GPUCorePrimitiveGeometryMode.DirectTriangles,
                            GPUCorePrimitiveGeometryMode.StencilEdgeFan,
                        )
                }
        ) {
            return refused(
                "unsupported.prepared-surface.core-route",
                "Mixed CorePrimitive runs accept only Direct or IndexedPath geometry.",
            )
        }
        context?.let { expected ->
            val sceneTarget = framePlan.steps
                .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
                .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
                .singleOrNull { request -> request.role == GPUFrameResourceRole.SceneTarget }
            if (framePlan.capabilitySeal.deviceGeneration != expected.deviceGeneration ||
                sceneTarget?.resource?.value != expected.targetId ||
                renders.any { render ->
                    render.drawPackets.any { packet ->
                        packet.resourceGeneration != expected.targetGeneration
                    }
                }
            ) {
                return refused(
                    "stale.prepared-surface.frame-context",
                    "Frame identity, target, and generation must match the active preflight context.",
                )
            }
        }
        validateColorAuthority(framePlan, renders)?.let { return it }
        validateReadbackAndSurface(framePlan, context)?.let { return it }

        val uploadSteps = framePlan.steps.withIndex()
            .filter { indexed -> indexed.value is GPUFrameStep.UploadResourceStep }
            .map { indexed ->
                indexed.index to indexed.value as GPUFrameStep.UploadResourceStep
            }
        val imageUploads = uploadSteps.mapNotNull { (index, step) ->
            step.imageResourcePlan?.let { plan -> Triple(index, step, plan) }
        }
        if (imageUploads.size != uploadSteps.size ||
            imageUploads.map { (_, _, plan) -> plan.artifactKey }.distinct().size !=
            imageUploads.size
        ) {
            return refused(
                "unsupported.prepared_image.plan_identity",
                "Every mixed upload scope must retain one unique prepared-image artifact plan.",
            )
        }
        val imagePackets = packets.mapNotNull { packet ->
            (packet.semanticPayload as? GPUDrawSemanticPayload.SampledImage)?.let {
                packet to it
            }
        }
        return validateImageAuthority(
            framePlan,
            imagePackets,
            imageUploads,
            preparedImageShaderContract(),
        )
    }

    fun validate(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        shaderContract: GPUPreparedImageShaderContract,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedSurfaceNativePreflightResult {
        validateFramePlan(framePlan)?.let { return it }
        val renders = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        val packets = renders.flatMap(GPUFrameStep.RenderPassStep::drawPackets)
        validateGeneration(
            framePlan,
            encoderPlan,
            resources,
            generationSeal,
        )?.let { return it }
        validateExactEncoderPlan(framePlan, encoderPlan, generationSeal)?.let { return it }
        validateResources(framePlan, encoderPlan, resources, generationSeal)?.let { return it }
        validateColorAuthority(framePlan, renders)?.let { return it }
        if (shaderContract != preparedImageShaderContract()) {
            return refused(
                "invalid.prepared-surface.shader-contract",
                "Prepared-image WGSL source, reflection, and binding identities must be exact.",
            )
        }

        val uploadSteps = framePlan.steps.withIndex()
            .filter { indexed -> indexed.value is GPUFrameStep.UploadResourceStep }
            .map { indexed ->
                indexed.index to indexed.value as GPUFrameStep.UploadResourceStep
            }
        val imageUploads = uploadSteps.mapNotNull { (index, step) ->
            step.imageResourcePlan?.let { plan -> Triple(index, step, plan) }
        }
        if (imageUploads.size != uploadSteps.size ||
            imageUploads.map { (_, _, plan) -> plan.artifactKey }.distinct().size != imageUploads.size
        ) {
            return refused(
                "unsupported.prepared_image.plan_identity",
                "Every mixed upload scope must retain one unique prepared-image artifact plan.",
            )
        }
        val imagePackets = packets.mapNotNull { packet ->
            (packet.semanticPayload as? GPUDrawSemanticPayload.SampledImage)?.let {
                packet to it
            }
        }
        validateImageAuthority(
            framePlan,
            imagePackets,
            imageUploads,
            shaderContract,
        )?.let { return it }

        val exactScopeKeys = encoderPlan.scopes.map { scope ->
            GPUPreparedNativeScopeKey(
                scope.sourceStepIndex,
                scope.operationKind,
                scope.resourceGenerationLabels,
                scope.nativeOperandKeys,
            )
        }
        val scopeByStep = encoderPlan.scopes.associateBy(
            GPUCommandEncoderScopePlan::sourceStepIndex,
        )
        val preparationByResource = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .associateBy(GPUResourcePreparationRequest::resource)
        val evidenceByResource = resources.ordinaryResources.associateBy(
            GPUPreparedResourceEvidence::logicalResource,
        )
        val imageFrames = imageUploads.map { (uploadIndex, _, resourcePlan) ->
            val packetIds = resourcePlan.bindingRequests
                .map { binding -> binding.packetId }
                .toSet()
            val consumers = framePlan.steps.mapIndexedNotNull { index, step ->
                (step as? GPUFrameStep.RenderPassStep)
                    ?.takeIf { render ->
                        render.drawPackets.any { packet ->
                            packet.packetId.value in packetIds
                        }
                    }
                    ?.let { index }
            }
            GPUPreparedSurfaceImageFramePlan(
                resourcePlan = resourcePlan,
                uploadScopeKey = exactScopeKeys.single { scope ->
                    scope.sourceStepIndex == uploadIndex
                },
                consumerRenderScopeIndices = consumers,
            )
        }
        val orderedRuns = mutableListOf<GPUPreparedSurfaceNativeRunPlan>()
        framePlan.steps.forEachIndexed { sourceStepIndex, step ->
            val render = step as? GPUFrameStep.RenderPassStep ?: return@forEachIndexed
            val renderScope = scopeByStep[sourceStepIndex]
                ?: return refused(
                    "invalid.prepared-surface.encoder-plan",
                    "A mixed render run is absent from the full encoder plan.",
                )
            val corePackets = render.drawPackets.filter {
                it.semanticPayload is GPUDrawSemanticPayload.CorePrimitive
            }
            if (corePackets.isNotEmpty()) {
                val routes = renderScope.corePrimitiveNativeScopeRouteSeal as?
                    GPUCorePrimitiveNativeScopeRouteSeal.Routes
                    ?: return refused(
                        "invalid.prepared-surface.core-route",
                        "Every mixed CorePrimitive run requires its exact unified route seal.",
                    )
                if (routes.flattenedPacketIds != corePackets.map(GPUDrawPacket::packetId)) {
                    return refused(
                        "invalid.prepared-surface.core-route",
                        "The CorePrimitive route seal does not retain packet order exactly.",
                    )
                }
                orderedRuns += GPUPreparedSurfaceNativeRunPlan.Core(
                    GPUCorePrimitiveRenderRunPlan(
                        sourceScopeIndices = listOf(sourceStepIndex),
                        packetIds = corePackets.map(GPUDrawPacket::packetId),
                        renderStep = render,
                        preparationRequests = render.resourceUses.map { use ->
                            preparationByResource.getValue(use.resource)
                        },
                        resourceEvidences = render.resourceUses.map { use ->
                            evidenceByResource.getValue(use.resource)
                        },
                        routeSeal = routes,
                        exactScopeKey = exactScopeKeys.single { scope ->
                            scope.sourceStepIndex == sourceStepIndex
                        },
                    ),
                )
            } else {
                val runPackets = render.drawPackets.map { packet ->
                    packet.semanticPayload as GPUDrawSemanticPayload.SampledImage
                }
                val packetIds = render.drawPackets.map { packet -> packet.packetId.value }.toSet()
                val runResources = imageUploads.filter { (_, _, plan) ->
                    plan.bindingRequests.any { binding -> binding.packetId in packetIds }
                }
                if (runResources.isEmpty()) {
                    return refused(
                        "unsupported.prepared_image.native-binding",
                        "Every image run requires its exact uploaded artifact resources.",
                    )
                }
                val bindingByPacketId = runResources
                    .flatMap { (_, _, plan) -> plan.bindingRequests }
                    .associateBy { binding -> binding.packetId }
                val orderedBindings = render.drawPackets.map { packet ->
                    bindingByPacketId[packet.packetId.value]
                        ?: return refused(
                            "unsupported.prepared_image.native-binding",
                            "Prepared-image allocations must exactly cover every run packet.",
                        )
                }
                val allocations = orderedBindings.map { binding ->
                    binding.uniformAllocation
                }
                orderedRuns += GPUPreparedSurfaceNativeRunPlan.Image(
                    GPUPreparedSurfaceImageRenderRunPlan(
                        sourceScopeIndex = sourceStepIndex,
                        renderStep = render,
                        packets = runPackets,
                        resourcePlans = runResources.map { (_, _, plan) -> plan },
                        orderedBindings = orderedBindings,
                        uniformAllocations = allocations,
                        exactScopeKey = exactScopeKeys.single { scope ->
                            scope.sourceStepIndex == sourceStepIndex
                        },
                    ),
                )
            }
        }
        GPUPreparedImagePlanValidator.validateFramePlans(
            imageFrames = imageFrames,
            runs = orderedRuns
                .filterIsInstance<GPUPreparedSurfaceNativeRunPlan.Image>()
                .map(GPUPreparedSurfaceNativeRunPlan.Image::plan),
        )?.let { (code, message) ->
            return refused(code, message)
        }
        val sceneTarget = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .single { request -> request.role == GPUFrameResourceRole.SceneTarget }
            .resource as GPUFrameTargetRef
        val readbackSeal = framePlan.steps.withIndex()
            .mapNotNull { indexed ->
                (indexed.value as? GPUFrameStep.ReadbackCopyStep)?.let {
                    indexed.index to it
                }
            }
            .singleOrNull()
            ?.let { (readbackIndex, readbackStep) ->
                val readbackOutput = resources.outputOwnedReadbacks.single()
                GPUPreparedSurfaceReadbackSeal(
                    sourceStepIndex = readbackIndex,
                    source = readbackStep.source,
                    staging = readbackStep.staging,
                    request = readbackStep.request,
                    layout = readbackOutput.layout,
                    stagingLease = readbackOutput.stagingLease,
                    exactScopeKey = exactScopeKeys.single { scope ->
                        scope.sourceStepIndex == readbackIndex
                    },
                )
            }
        val surfaceChain = framePlan.steps.withIndex()
            .filter { indexed -> indexed.value is GPUFrameStep.AcquireSurfaceOutput }
            .singleOrNull()
            ?.let { indexedAcquire ->
                val acquire = indexedAcquire.value as GPUFrameStep.AcquireSurfaceOutput
                val blitIndex = indexedAcquire.index + 1
                val presentIndex = indexedAcquire.index + 2
                val blit = framePlan.steps[blitIndex] as GPUFrameStep.SurfaceBlitRenderPassStep
                GPUPreparedSurfaceChainSeal(
                    acquireStepIndex = indexedAcquire.index,
                    blitStepIndex = blitIndex,
                    presentStepIndex = presentIndex,
                    descriptor = acquire.descriptor,
                    scene = blit.scene,
                    output = blit.output,
                    sourceTaskIds = acquire.sourceTaskIds,
                    exactBlitScopeKey = exactScopeKeys.single { scope ->
                        scope.sourceStepIndex == blitIndex
                    },
                )
            }
        return try {
            GPUPreparedSurfaceNativePreflightResult.Accepted(
                GPUPreparedSurfaceNativePreflightPlan(
                    frameId = framePlan.frameId,
                    encoderPlanId = encoderPlan.planId,
                    contextIdentity = encoderPlan.contextIdentity,
                    sceneTarget = sceneTarget,
                    resources = resources,
                    readback = readbackSeal,
                    surfaceChain = surfaceChain,
                    orderedRuns = orderedRuns,
                    imageFrames = imageFrames,
                    exactScopeKeys = exactScopeKeys,
                    generationSeal = generationSeal,
                ),
            )
        } catch (_: IllegalArgumentException) {
            refused(
                "invalid.prepared-surface.run-plan",
                "The sealed mixed run plan is internally inconsistent.",
            )
        }
    }

    private fun validateGeneration(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedSurfaceNativePreflightResult.Refused? {
        if (generationSeal.deviceGeneration != framePlan.capabilitySeal.deviceGeneration ||
            generationSeal.deviceGeneration != encoderPlan.deviceGeneration ||
            generationSeal.targetGeneration != encoderPlan.targetGeneration ||
            generationSeal.capabilitySealHash != framePlan.capabilitySeal.sealHash
        ) {
            return refused(
                "stale.prepared-surface.generation",
                "Frame, encoder, target, device, and capability generations must agree.",
            )
        }
        val preparedRefs = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .map { request -> request.resource }
            .toSet()
        if (generationSeal.resourceGenerations.keys != preparedRefs ||
            generationSeal.resourceGenerations.values.any { it < 0L } ||
            resources.ordinaryResources.any { evidence ->
                evidence.deviceGeneration != generationSeal.deviceGeneration ||
                    generationSeal.resourceGenerations[evidence.logicalResource] !=
                    evidence.resourceGeneration
            } ||
            resources.outputOwnedReadbacks.any { readback ->
                generationSeal.resourceGenerations[readback.stagingResource] !=
                    readback.resourceGeneration
            }
        ) {
            return refused(
                "stale.prepared-surface.generation",
                "Every prepared resource must retain the exact current generation.",
            )
        }
        return null
    }

    private fun validateExactEncoderPlan(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedSurfaceNativePreflightResult.Refused? {
        val sceneTarget = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .singleOrNull { request -> request.role == GPUFrameResourceRole.SceneTarget }
            ?.resource as? GPUFrameTargetRef
        if (encoderPlan.planId != "frame.${framePlan.frameId.value}" ||
            sceneTarget == null ||
            encoderPlan.contextIdentity != sceneTarget.value ||
            encoderPlan.targetGeneration != generationSeal.targetGeneration ||
            generationSeal.resourceGenerations[sceneTarget] != encoderPlan.targetGeneration
        ) {
            return refused(
                "invalid.prepared-surface.encoder-plan",
                "Encoder plan, frame, scene target, and target generation identities must be exact.",
            )
        }
        val expected = framePlan.steps.mapIndexedNotNull { index, step ->
            step.preparedSurfaceOperationKindOrNull()?.let { operation -> index to operation }
        }
        if (encoderPlan.scopes.map { scope -> scope.sourceStepIndex to scope.operationKind } != expected ||
            encoderPlan.scopes.any { scope ->
                !GPUPreparedSurfaceEncoderScopeAuthority.matches(
                    framePlan,
                    framePlan.steps[scope.sourceStepIndex],
                    scope,
                    generationSeal,
                )
            }
        ) {
            return refused(
                "invalid.prepared-surface.encoder-plan",
                "The full encoder plan must retain every encodable frame step exactly once and in order.",
            )
        }
        return null
    }

    private fun validateResources(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedSurfaceNativePreflightResult.Refused? {
        val requests = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        val ordinary = requests.filter { it.role != GPUFrameResourceRole.ReadbackStaging }
        val readbacks = requests.filter { it.role == GPUFrameResourceRole.ReadbackStaging }
        val declaredResources = requests.map(GPUResourcePreparationRequest::resource)
        val resourceMismatch = when {
            declaredResources.distinct().size != declaredResources.size ->
                "Frame preparation declarations contain duplicate logical resources."
            ordinary.map(GPUResourcePreparationRequest::resource) !=
                resources.ordinaryResources.map(GPUPreparedResourceEvidence::logicalResource) ->
                "Ordinary prepared resources differ from declaration order."
            readbacks.map(GPUResourcePreparationRequest::resource) !=
                resources.outputOwnedReadbacks.map(GPUPreparedReadbackOutput::stagingResource) ->
                "Output-owned readback resources differ from declaration order."
            generationSeal.resourceGenerations.keys != declaredResources.toSet() ->
                "The generation seal does not exactly cover resource declarations."
            !resources.ordinaryResources.zip(ordinary).all { (evidence, request) ->
                evidence.matchesExactPreparation(request, generationSeal)
            } ->
                "Ordinary prepared topology, roles, or generations differ from declarations."
            !resources.hasValidCommandEvidence(encoderPlan, generationSeal) ->
                "Command-resource identities, ownership, generation, or diagnostics are invalid."
            else -> null
        }
        if (resourceMismatch != null) {
            return refused(
                "invalid.prepared-surface.resources",
                resourceMismatch,
            )
        }
        val imagePreparations = framePlan.steps
            .filterIsInstance<GPUFrameStep.UploadResourceStep>()
            .mapNotNull(GPUFrameStep.UploadResourceStep::imageResourcePlan)
            .flatMap(GPUImageFrameResourcePlan::preparationRequests)
        if (imagePreparations.any { imageRequest ->
                requests.singleOrNull { declared ->
                    declared.samePreparationAs(imageRequest)
                } == null
            }
        ) {
            return refused(
                "invalid.prepared-surface.resources",
                "Prepared-image resource plans must retain the exact frame preparation requests.",
            )
        }
        validateReadbackResources(framePlan, readbacks, resources, generationSeal)
            ?.let { return it }
        return null
    }

    private fun validateReadbackResources(
        framePlan: GPUFramePlan,
        readbackPreparations: List<GPUResourcePreparationRequest>,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedSurfaceNativePreflightResult.Refused? {
        val steps = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
        val outputs = resources.outputOwnedReadbacks
        if (steps.isEmpty() && readbackPreparations.isEmpty() && outputs.isEmpty()) {
            return null
        }
        val step = steps.singleOrNull()
        val preparation = readbackPreparations.singleOrNull()
        val output = outputs.singleOrNull()
        if (step == null || preparation == null || output == null ||
            output.stagingResource != step.staging ||
            output.request != step.request ||
            output.concreteResource.ref != output.stagingLease.resourceRef ||
            output.resourceGeneration !=
            generationSeal.resourceGenerations[output.stagingResource] ||
            output.stagingLease.deviceGeneration != generationSeal.deviceGeneration ||
            output.stagingLease.logicalMinimumBytes != output.layout.totalBufferBytes ||
            output.stagingLease.backingBufferBytes <
            output.stagingLease.logicalMinimumBytes ||
            output.stagingLease.usages != preparation.usages
        ) {
            return refused(
                "invalid.prepared-surface.readback",
                "Readback request, staging ownership, generation, lease, and concrete buffer must remain exact.",
            )
        }
        val descriptor = preparation.descriptor as? GPUFrameBufferDescriptor
        val layout = output.layout
        val width = step.request.sourceBounds.width
        val height = step.request.sourceBounds.height
        val unpadded = try {
            Math.multiplyExact(width.toLong(), 4L)
        } catch (_: ArithmeticException) {
            return refused(
                "invalid.prepared-surface.readback",
                "Readback row size overflowed.",
            )
        }
        val alignment = layout.copyBytesPerRowAlignment.toLong()
        val padded = if (alignment > 0L) {
            ((unpadded + alignment - 1L) / alignment) * alignment
        } else {
            -1L
        }
        val total = try {
            Math.addExact(
                step.request.bufferOffsetBytes,
                Math.addExact(
                    Math.multiplyExact(padded, (height - 1).toLong()),
                    unpadded,
                ),
            )
        } catch (_: ArithmeticException) {
            return refused(
                "invalid.prepared-surface.readback",
                "Readback staging size overflowed.",
            )
        }
        if (descriptor == null ||
            descriptor.byteSize != total ||
            output.stagingLease.logicalMinimumBytes != descriptor.byteSize ||
            output.stagingLease.backingBufferBytes < descriptor.byteSize ||
            layout.width != width ||
            layout.height != height ||
            layout.bytesPerPixel != 4 ||
            alignment <= 0L ||
            alignment and (alignment - 1L) != 0L ||
            layout.unpaddedBytesPerRow != unpadded ||
            layout.paddedBytesPerRow != padded ||
            layout.rowsPerImage != height ||
            layout.bufferOffset != step.request.bufferOffsetBytes ||
            layout.totalBufferBytes != total
        ) {
            return refused(
                "invalid.prepared-surface.readback",
                "Readback bounds, RGBA8 row padding, offset, and total staging bytes must be exact.",
            )
        }
        return null
    }

    private fun validateReadbackAndSurface(
        framePlan: GPUFramePlan,
        context: GPUFramePreflightContext?,
    ): GPUPreparedSurfaceNativePreflightResult.Refused? {
        val preparations = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        val scene = preparations.singleOrNull { request ->
            request.role == GPUFrameResourceRole.SceneTarget
        }
        val sceneDescriptor = scene?.descriptor as? GPUFrameTextureDescriptor
        val readback = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
        val readbackPreparations = preparations.filter { request ->
            request.role == GPUFrameResourceRole.ReadbackStaging
        }
        if (scene == null || sceneDescriptor == null) {
            return refused(
                "invalid.prepared-surface.readback",
                "The prepared surface requires one exact scene target.",
            )
        }
        if (readback.isEmpty() && readbackPreparations.isEmpty()) {
            // Readback is optional; an absent request must leave no staging declaration.
        } else {
            val exactReadback = readback.singleOrNull()
            val readbackPreparation = readbackPreparations.singleOrNull()
            if (exactReadback == null || readbackPreparation == null ||
                exactReadback.source != scene.resource ||
                exactReadback.staging != readbackPreparation.resource ||
                exactReadback.request.sourceBounds != sceneDescriptor.logicalBounds ||
                exactReadback.request.pixelFormat != GPUReadbackPixelFormat.Rgba8Unorm ||
                exactReadback.request.outputColorInterpretation !=
                GPUColorInterpretation.EncodedPremulSrgb ||
                exactReadback.request.bufferOffsetBytes != 0L ||
                readbackPreparation.descriptor !is GPUFrameBufferDescriptor ||
                readbackPreparation.usages != setOf(
                    GPUFrameResourceUsage.CopyDestination,
                    GPUFrameResourceUsage.MapRead,
                )
            ) {
                return refused(
                    "invalid.prepared-surface.readback",
                    "The optional prepared-surface readback must be one exact full-scene RGBA8 sRGB request.",
                )
            }
        }

        val acquires = framePlan.steps.filterIsInstance<GPUFrameStep.AcquireSurfaceOutput>()
        val blits = framePlan.steps.filterIsInstance<GPUFrameStep.SurfaceBlitRenderPassStep>()
        val presents = framePlan.steps.filterIsInstance<GPUFrameStep.PostSubmitPresentAction>()
        if (acquires.isEmpty() && blits.isEmpty() && presents.isEmpty()) return null
        val acquire = acquires.singleOrNull()
        val blit = blits.singleOrNull()
        val present = presents.singleOrNull()
        val suffix = framePlan.steps.takeLast(3)
        if (acquire == null || blit == null || present == null ||
            suffix.getOrNull(0) !== acquire ||
            suffix.getOrNull(1) !== blit ||
            suffix.getOrNull(2) !== present ||
            acquire.descriptor.output != blit.output ||
            acquire.descriptor.output != present.output ||
            blit.scene != scene.resource ||
            acquire.descriptor.width != sceneDescriptor.logicalBounds.width ||
            acquire.descriptor.height != sceneDescriptor.logicalBounds.height ||
            acquire.descriptor.format != sceneDescriptor.format ||
            context?.surfaceGeneration?.let { generation ->
                acquire.descriptor.targetGeneration == generation
            } == false ||
            acquire.sourceTaskIds != blit.sourceTaskIds ||
            acquire.sourceTaskIds != present.sourceTaskIds
        ) {
            return refused(
                "invalid.prepared-surface.surface-chain",
                "Surface acquire, scene blit, and present must form one exact final chain.",
            )
        }
        return null
    }

    private fun validateColorAuthority(
        framePlan: GPUFramePlan,
        renders: List<GPUFrameStep.RenderPassStep>,
    ): GPUPreparedSurfaceNativePreflightResult.Refused? {
        val sceneRequest = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .singleOrNull { request -> request.role == GPUFrameResourceRole.SceneTarget }
            ?: return refused(
                "unsupported.prepared-surface.target-color",
                "Mixed prepared surfaces require one exact scene target.",
            )
        val descriptor = sceneRequest.descriptor as? GPUFrameTextureDescriptor
        if (descriptor?.format != GPUColorFormat.RGBA8UnormSrgb ||
            descriptor.sampleCount != 1 ||
            renders.any { render -> render.target != sceneRequest.resource }
        ) {
            return refused(
                "unsupported.prepared-surface.target-color",
                "The mixed scene target must be single-sample RGBA8UnormSrgb with LinearPremul shader authority.",
            )
        }
        if (renders.any { render ->
                render.samplePlan.sampleCount > 1 &&
                    render.drawPackets.any {
                        it.semanticPayload is GPUDrawSemanticPayload.CorePrimitive
                    }
            }
        ) {
            return refused(
                "unsupported.native-core-primitive.srgb-msaa",
                "CorePrimitive sRGB MSAA remains outside the prepared mixed route.",
            )
        }
        val readbacks = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
        if (readbacks.any { readback ->
                readback.source != sceneRequest.resource ||
                    readback.request.outputColorInterpretation !=
                    GPUColorInterpretation.EncodedPremulSrgb
            }
        ) {
            return refused(
                "invalid.prepared-surface.readback-color",
                "Mixed readback must encode the exact sRGB scene target as EncodedPremulSrgb.",
            )
        }
        return null
    }

    private fun validateImageAuthority(
        framePlan: GPUFramePlan,
        imagePackets: List<Pair<GPUDrawPacket, GPUDrawSemanticPayload.SampledImage>>,
        uploads: List<Triple<Int, GPUFrameStep.UploadResourceStep, GPUImageFrameResourcePlan>>,
        shaderContract: GPUPreparedImageShaderContract,
    ): GPUPreparedSurfaceNativePreflightResult.Refused? {
        val artifactEvidenceByIdentity =
            IdentityHashMap<GPUPreparedImageUploadArtifact, GPUPreparedSurfaceArtifactByteEvidence>()
        val renderBindingList = framePlan.steps
            .filterIsInstance<GPUFrameStep.RenderPassStep>()
            .flatMap { render -> render.preparedImageBindingsByPacketId.values }
        val plannedBindingList = uploads.flatMap { (_, _, plan) -> plan.bindingRequests }
        if (renderBindingList.map { binding -> binding.packetId }.distinct().size !=
            renderBindingList.size ||
            plannedBindingList.map { binding -> binding.packetId }.distinct().size !=
            plannedBindingList.size
        ) {
            return refused(
                "invalid.prepared-surface.image-binding-duplicates",
                "Prepared-image packet bindings must be globally unique before indexed lookup.",
            )
        }
        val packetById = imagePackets.associate { (packet, semantic) ->
            packet.packetId.value to semantic
        }
        val renderBindings = renderBindingList.associateBy { binding -> binding.packetId }
        if (renderBindings.keys != packetById.keys ||
            renderBindings.values.any { binding ->
                binding.bindingLayoutHash != shaderContract.bindingLayoutHash
            }
        ) {
            return refused(
                "unsupported.prepared_image.native-binding",
                "Image render bindings must exactly cover packet identities with the reflected ABI112 layout.",
            )
        }
        val plannedBindings = plannedBindingList.associateBy { binding -> binding.packetId }
        if (plannedBindings != renderBindings) {
            return refused(
                "unsupported.prepared_image.native-binding",
                "Upload plans and render runs must retain the same exact image bindings.",
            )
        }
        uploads.forEach { (_, step, plan) ->
            if (step.staging != plan.stagingRef ||
                step.destination != plan.frameTextureRef ||
                step.layout != plan.uploadTaskLayout ||
                plan.bindingRequests.isEmpty() ||
                plan.bindingRequests.any { binding ->
                    binding.artifactKey != plan.artifactKey ||
                        binding.uniformAllocation.packetId != binding.packetId ||
                        binding.uniformAllocation.size !=
                        GPU_PREPARED_IMAGE_UNIFORM_ALLOCATION_SIZE_BYTES ||
                        packetById[binding.packetId]?.artifact?.key != plan.artifactKey
                }
            ) {
                return refused(
                    "unsupported.prepared_image.plan_identity",
                    "Prepared-image upload and binding identities must be exact.",
                )
            }
            val expectedLogicalRowBytes = try {
                Math.multiplyExact(plan.artifactWidth.toLong(), 4L)
            } catch (_: ArithmeticException) {
                return refused(
                    "unsupported.prepared_image.upload_layout",
                    "Prepared-image row layout overflowed.",
                )
            }
            val expectedUploadBytes = try {
                Math.multiplyExact(plan.uploadLayout.bytesPerRow, plan.artifactHeight.toLong())
            } catch (_: ArithmeticException) {
                return refused(
                    "unsupported.prepared_image.upload_layout",
                    "Prepared-image upload byte size overflowed.",
                )
            }
            val logicalArtifactBytes = plan.uploadLayout.logicalBytesForHash()
            val uploadBytes = plan.uploadLayout.bytesForUpload()
            val planArtifacts = plan.bindingRequests.mapNotNull { binding ->
                packetById[binding.packetId]?.artifact
            }
            if (planArtifacts.size != plan.bindingRequests.size ||
                planArtifacts.any { artifact ->
                    val byteEvidence = artifactEvidenceByIdentity[artifact] ?: run {
                        val tightBytes = artifact.tightRgba8BytesForUpload()
                        GPUPreparedSurfaceArtifactByteEvidence(
                            tightRgba8Bytes = tightBytes,
                            contentHash = preparedSurfaceSha256(tightBytes),
                        ).also { evidence ->
                            artifactEvidenceByIdentity[artifact] = evidence
                        }
                    }
                    artifact.key != plan.artifactKey ||
                        artifact.width != plan.artifactWidth ||
                        artifact.height != plan.artifactHeight ||
                        artifact.pixelLayout.normalizedRgba8RowBytes !=
                        expectedLogicalRowBytes ||
                        artifact.pixelLayout.rowCount != plan.artifactHeight ||
                        artifact.contentHash != plan.artifactContentHash ||
                        byteEvidence.contentHash != artifact.contentHash ||
                        !byteEvidence.tightRgba8Bytes.contentEquals(logicalArtifactBytes) ||
                        artifact.colorInterpretation !=
                        GPUColorInterpretation.EncodedPremulSrgb.value ||
                        if (artifact.alphaOnly) {
                            artifact.colorUploadEncoding != null ||
                                artifact.colorUploadInterpretation !=
                                GPUColorInterpretation.LinearPremul.value
                        } else {
                            artifact.colorUploadEncoding?.name !=
                                "StraightEncodedSrgb" ||
                                artifact.colorUploadInterpretation !=
                                GPUColorInterpretation.StraightEncodedSrgb.value
                        }
                } ||
                planArtifacts.map { artifact -> artifact.alphaOnly }.distinct().size != 1
            ) {
                return refused(
                    "invalid.prepared-surface.image-artifact",
                    "Prepared-image key, dimensions, immutable RGBA8 bytes, hash, alpha, and color authority must remain exact.",
                )
            }
            if (plan.uploadLayout.logicalBytesPerRow != expectedLogicalRowBytes ||
                plan.uploadLayout.width != plan.artifactWidth ||
                plan.uploadLayout.height != plan.artifactHeight ||
                plan.uploadLayout.rowsPerImage != plan.artifactHeight ||
                plan.uploadTaskLayout.sourceOffsetBytes != 0L ||
                plan.uploadTaskLayout.bytesPerRow != plan.uploadLayout.bytesPerRow ||
                plan.uploadTaskLayout.rowsPerImage != plan.uploadLayout.rowsPerImage ||
                plan.uploadTaskLayout.byteSize != expectedUploadBytes ||
                uploadBytes.size.toLong() != expectedUploadBytes ||
                preparedSurfaceSha256(logicalArtifactBytes) !=
                plan.artifactContentHash
            ) {
                return refused(
                    "unsupported.prepared_image.upload_layout",
                    "Prepared-image upload dimensions, strides, bytes, and provenance must be exact.",
                )
            }
            val descriptor = plan.preparationRequests.singleOrNull { request ->
                request.resource == plan.frameTextureRef
            }?.descriptor as? GPUFrameTextureDescriptor
            val sourceFormat = if (
                packetById.getValue(plan.bindingRequests.first().packetId).artifact.alphaOnly
            ) {
                GPUColorFormat.RGBA8Unorm
            } else {
                GPUColorFormat.RGBA8UnormSrgb
            }
            val sourceTextureFormatLabel = when (sourceFormat) {
                GPUColorFormat.RGBA8Unorm -> "RGBA8Unorm"
                GPUColorFormat.RGBA8UnormSrgb -> "rgba8unorm-srgb"
                else -> error("Prepared surface selected an unsupported image source format")
            }
            if (descriptor?.format != sourceFormat ||
                descriptor.sampleCount != 1 ||
                descriptor.logicalBounds.width != plan.artifactWidth ||
                descriptor.logicalBounds.height != plan.artifactHeight ||
                plan.textureDescriptor.sampleCount != 1 ||
                plan.textureDescriptor.usageLabels != setOf("copy_dst", "texture_binding")
            ) {
                return refused(
                    "unsupported.prepared_image.texture_usage",
                    "Prepared-image source texture format, extent, sample count, and usages must be exact.",
                )
            }
            if (plan.textureDescriptor.width != plan.artifactWidth ||
                plan.textureDescriptor.height != plan.artifactHeight ||
                plan.textureDescriptor.format != sourceTextureFormatLabel ||
                plan.bindingRequests.any { binding ->
                    val semantic = packetById[binding.packetId] ?: return@any true
                    val expectedFilter = when (semantic.sampling) {
                        GPUPreparedImageSampling.Nearest -> "nearest"
                        GPUPreparedImageSampling.Linear -> "linear"
                    }
                    binding.texture != plan.textureDescriptor ||
                        binding.view.textureDescriptorHash !=
                        plan.textureDescriptor.preparedImageDescriptorHash() ||
                        binding.view.viewDimension != "2d" ||
                        binding.view.mipRange != 0..0 ||
                        binding.view.arrayLayerRange != 0..0 ||
                        binding.sampler.addressModeU != "clamp-to-edge" ||
                        binding.sampler.addressModeV != "clamp-to-edge" ||
                        binding.sampler.magFilter != expectedFilter ||
                        binding.sampler.minFilter != expectedFilter ||
                        binding.sampler.mipmapFilter != "none" ||
                        binding.sampler.lodMinClamp != "0" ||
                        binding.sampler.lodMaxClamp != "0" ||
                        binding.sampler.compareMode != "none" ||
                        binding.sampler.maxAnisotropy != 1 ||
                        binding.sampler.capabilityRequirements.isNotEmpty()
                }
            ) {
                return refused(
                    "invalid.prepared-surface.image-physical",
                    "Prepared-image texture, view, and sampler facts must match their exact canonical physical plan.",
                )
            }
        }
        if (imagePackets.any { (packet, semantic) ->
                !semantic.hasCanonicalHashIntegrity() ||
                    semantic.pipelineKey.bindingLayoutHash !=
                    GPUPreparedImageBindingLayoutTopology.IDENTITY ||
                    packet.payloadIdentityMismatch(semantic)
            }
        ) {
            return refused(
                "invalid.prepared-surface.image-semantic",
                "Prepared-image packet and semantic identities must remain canonical.",
            )
        }
        return null
    }
}

internal object GPUPreparedSurfaceEncoderScopeAuthority {
    fun matches(
        framePlan: GPUFramePlan,
        step: GPUFrameStep,
        scope: GPUCommandEncoderScopePlan,
        generationSeal: GPUPreparedGenerationSeal,
    ): Boolean {
        val expectedLabels = step.preparedResourceRefs().map { resource ->
            val generation = generationSeal.resourceGenerations[resource] ?: return false
            "${resource::class.simpleName}:${resource.value}@$generation"
        }
        val render = step as? GPUFrameStep.RenderPassStep
        if (scope.scopeLabel != "step.${scope.sourceStepIndex}" ||
            scope.sourceTaskIds != step.sourceTaskIds ||
            scope.operationKind != step.expectedEncoderOperationKind() ||
            scope.targetGeneration != generationSeal.targetGeneration ||
            scope.resourceGenerationLabels != expectedLabels ||
            scope.sourcePacketIds != render?.drawPackets?.map(GPUDrawPacket::packetId).orEmpty() ||
            scope.targetResource != render?.target ||
            scope.facadeOperationClasses != step.expectedFacadeOperations(scope)
        ) {
            return false
        }
        if (render == null) {
            return scope.passCommandStream == null &&
                scope.nativeOperandKeys == expectedNonRenderOperandKeys(step, expectedLabels)
        }
        val stream = scope.passCommandStream ?: return false
        val expectedStream = runCatching {
            expectedRenderStream(framePlan, scope.sourceStepIndex, render, stream)
        }.getOrNull() ?: return false
        if (stream.streamId != expectedStream.streamId ||
            stream.packetStreamId != expectedStream.packetStreamId ||
            stream.passId != expectedStream.passId ||
            stream.commands != expectedStream.commands ||
            stream.diagnostics != expectedStream.diagnostics ||
            stream.operandBridge != expectedStream.operandBridge ||
            stream.sourcePassIds != expectedStream.sourcePassIds
        ) {
            return false
        }
        return scope.nativeOperandKeys ==
            expectedRenderOperandKeys(render, scope, expectedLabels, stream)
    }

    private fun expectedRenderStream(
        framePlan: GPUFramePlan,
        sourceStepIndex: Int,
        render: GPUFrameStep.RenderPassStep,
        actual: GPUPassCommandStream,
    ): GPUPassCommandStream {
        val passPlan = GPUPassBatchPlan(
            streamId = "frame.${framePlan.frameId.value}.step.$sourceStepIndex",
            passId = "frame.${framePlan.frameId.value}.render.$sourceStepIndex",
            batches = render.batches.map { batch ->
                GPUPassBatch(
                    batchId = batch.batchId,
                    packets = batch.packets,
                    kind = batch.kind,
                    targetStateHash = batch.packets.first().targetStateHash,
                    queueGuard = GPUPassBatchQueueGuard(
                        requiredRetainedRefs = emptyList(),
                        retainedRefs = emptyList(),
                    ),
                )
            },
            cuts = emptyList(),
            diagnostics = emptyList(),
            inputPacketCount = render.drawPackets.size,
        )
        return GPUPassCommandStream.fromBatchPlan(
            streamId = "frame.${framePlan.frameId.value}.commands.$sourceStepIndex",
            batchPlan = passPlan,
            loadStoreLabel = render.loadStore.dumpLabelForPreparedSurface(),
            operandBridge = actual.operandBridge,
        )
    }

    private fun expectedNonRenderOperandKeys(
        step: GPUFrameStep,
        labels: List<String>,
    ): List<GPUPreparedNativeOperandKey> {
        fun key(
            role: GPUPreparedNativeOperandRole,
            kind: GPUPreparedNativeOperandKind,
            binding: String,
            ownership: GPUPreparedNativeOperandOwnership =
                GPUPreparedNativeOperandOwnership.Borrowed,
        ) = GPUPreparedNativeOperandKey(
            role,
            kind,
            gpuPreparedNativeBindingKey(binding),
            ownership,
        )
        return when (step) {
            is GPUFrameStep.UploadResourceStep -> when (step.destinationKind) {
                org.graphiks.kanvas.gpu.renderer.recording.GPUUploadDestinationKind.Buffer ->
                    listOf(
                        key(
                            GPUPreparedNativeOperandRole.UploadSource,
                            GPUPreparedNativeOperandKind.Buffer,
                            labels[0],
                        ),
                        key(
                            GPUPreparedNativeOperandRole.UploadDestination,
                            GPUPreparedNativeOperandKind.Buffer,
                            labels[1],
                        ),
                    )
                org.graphiks.kanvas.gpu.renderer.recording.GPUUploadDestinationKind.Texture ->
                    listOf(
                        key(
                            GPUPreparedNativeOperandRole.UploadSource,
                            GPUPreparedNativeOperandKind.Buffer,
                            "prepared-image-upload-data:${step.staging.value}",
                        ),
                        key(
                            GPUPreparedNativeOperandRole.UploadDestination,
                            GPUPreparedNativeOperandKind.Texture,
                            labels[1],
                        ),
                    )
            }
            is GPUFrameStep.ReadbackCopyStep -> listOf(
                key(
                    GPUPreparedNativeOperandRole.ReadbackSource,
                    GPUPreparedNativeOperandKind.Texture,
                    labels[0],
                ),
                key(
                    GPUPreparedNativeOperandRole.ReadbackDestination,
                    GPUPreparedNativeOperandKind.Buffer,
                    labels[1],
                    GPUPreparedNativeOperandOwnership.OutputOwnedReadback,
                ),
            )
            is GPUFrameStep.SurfaceBlitRenderPassStep -> listOf(
                key(
                    GPUPreparedNativeOperandRole.SurfaceSource,
                    GPUPreparedNativeOperandKind.TextureView,
                    labels.single(),
                ),
                key(
                    GPUPreparedNativeOperandRole.SurfaceTarget,
                    GPUPreparedNativeOperandKind.TextureView,
                    "surface:${step.output.value}:target",
                ),
                key(
                    GPUPreparedNativeOperandRole.SurfacePipeline,
                    GPUPreparedNativeOperandKind.RenderPipeline,
                    "surface:${step.output.value}:pipeline",
                ),
                key(
                    GPUPreparedNativeOperandRole.SurfaceBindGroup,
                    GPUPreparedNativeOperandKind.BindGroup,
                    "surface:${step.output.value}:bind-group",
                ),
            )
            else -> emptyList()
        }
    }

    private fun expectedRenderOperandKeys(
        render: GPUFrameStep.RenderPassStep,
        scope: GPUCommandEncoderScopePlan,
        labels: List<String>,
        stream: GPUPassCommandStream,
    ): List<GPUPreparedNativeOperandKey> {
        fun key(
            role: GPUPreparedNativeOperandRole,
            kind: GPUPreparedNativeOperandKind,
            binding: String,
        ) = GPUPreparedNativeOperandKey(
            role,
            kind,
            gpuPreparedNativeBindingKey(binding),
            GPUPreparedNativeOperandOwnership.Borrowed,
        )
        fun bridgeKey(
            bridge: org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandOperandBridge,
        ): GPUPreparedNativeOperandKey? = when (bridge.operand.kind) {
            org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.RenderPipeline ->
                key(
                    GPUPreparedNativeOperandRole.RenderPipeline,
                    GPUPreparedNativeOperandKind.RenderPipeline,
                    "${bridge.commandLabel}:${bridge.operand.label}",
                )
            org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.BindGroup ->
                key(
                    GPUPreparedNativeOperandRole.RenderBindGroup,
                    GPUPreparedNativeOperandKind.BindGroup,
                    "${bridge.commandLabel}:${bridge.operand.label}",
                )
            org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.VertexBuffer ->
                key(
                    GPUPreparedNativeOperandRole.RenderVertexBuffer,
                    GPUPreparedNativeOperandKind.Buffer,
                    "${bridge.commandLabel}:${bridge.operand.label}",
                )
            org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.IndexBuffer ->
                key(
                    GPUPreparedNativeOperandRole.RenderIndexBuffer,
                    GPUPreparedNativeOperandKind.Buffer,
                    "${bridge.commandLabel}:${bridge.operand.label}",
                )
            else -> null
        }
        val target = listOf(
            key(
                GPUPreparedNativeOperandRole.RenderColorTarget,
                GPUPreparedNativeOperandKind.TextureView,
                labels.first(),
            ),
        )
        val unified = scope.corePrimitiveNativeScopeRouteSeal as?
            GPUCorePrimitiveNativeScopeRouteSeal.Routes
        val path = unified?.orderedUnits?.any {
            it is GPUCorePrimitiveNativeScopeRouteUnit.PathPair
        } == true
        val direct = scope.corePrimitiveDirectNativeRouteSeal is
            GPUCorePrimitiveDirectNativeRouteSeal.Routes
        if (path) {
            val depthIndex = render.resourceUses.indexOfFirst { use ->
                use.role == GPUFrameResourceRole.PathDepthStencil
            }
            val vertexIndex = render.resourceUses.indexOfFirst { use ->
                use.role == GPUFrameResourceRole.VertexData
            }
            val indexIndex = render.resourceUses.indexOfFirst { use ->
                use.role == GPUFrameResourceRole.IndexData
            }
            if (depthIndex < 0 || vertexIndex < 0 || indexIndex < 0) return emptyList()
            val pipelines = stream.operandBridge
                .filter {
                    it.operand.kind ==
                        org.graphiks.kanvas.gpu.renderer.resources
                            .GPUMaterializedCommandOperandKind.RenderPipeline
                }
                .zip(render.drawPackets)
                .distinctBy { (_, packet) -> packet.renderPipelineKey }
                .mapNotNull { (bridge, _) -> bridgeKey(bridge) }
            val bindGroups = stream.operandBridge
                .filter {
                    it.operand.kind ==
                        org.graphiks.kanvas.gpu.renderer.resources
                            .GPUMaterializedCommandOperandKind.BindGroup
                }
                .mapNotNull(::bridgeKey)
            return target + listOf(
                key(
                    GPUPreparedNativeOperandRole.RenderDepthStencilTarget,
                    GPUPreparedNativeOperandKind.TextureView,
                    labels[depthIndex + 1],
                ),
            ) + pipelines + listOf(
                key(
                    GPUPreparedNativeOperandRole.RenderVertexBuffer,
                    GPUPreparedNativeOperandKind.Buffer,
                    labels[vertexIndex + 1],
                ),
                key(
                    GPUPreparedNativeOperandRole.RenderIndexBuffer,
                    GPUPreparedNativeOperandKind.Buffer,
                    labels[indexIndex + 1],
                ),
            ) + bindGroups
        }
        val bridges = if (direct) {
            listOfNotNull(
                stream.operandBridge.firstOrNull {
                    it.operand.kind ==
                        org.graphiks.kanvas.gpu.renderer.resources
                            .GPUMaterializedCommandOperandKind.RenderPipeline
                },
                stream.operandBridge.firstOrNull {
                    it.operand.kind ==
                        org.graphiks.kanvas.gpu.renderer.resources
                            .GPUMaterializedCommandOperandKind.VertexBuffer
                },
                stream.operandBridge.firstOrNull {
                    it.operand.kind ==
                        org.graphiks.kanvas.gpu.renderer.resources
                            .GPUMaterializedCommandOperandKind.IndexBuffer
                },
            ) + stream.operandBridge.filter {
                it.operand.kind ==
                    org.graphiks.kanvas.gpu.renderer.resources
                        .GPUMaterializedCommandOperandKind.BindGroup
            }
        } else {
            stream.operandBridge
        }
        val keys = bridges.mapNotNull(::bridgeKey)
        return if (keys.size != bridges.size) emptyList() else target + keys
    }
}

private fun GPULoadStorePlan.dumpLabelForPreparedSurface(): String =
    "$loadOp:${storePlan.name}:${clearColorLabel ?: "none"}"

private fun GPUDrawPacket.payloadIdentityMismatch(
    semantic: GPUDrawSemanticPayload.SampledImage,
): Boolean =
    semantic.payloadRef.commandIdValue != commandIdValue ||
        semantic.payloadRef.renderStepIdentity != renderStepId.value ||
        semantic.blendPlanIdentity != blendPlan?.canonicalIdentity()

private fun GPUPreparedResourceEvidence.matchesExactPreparation(
    request: GPUResourcePreparationRequest,
    generationSeal: GPUPreparedGenerationSeal,
): Boolean {
    if (logicalResource != request.resource ||
        role != request.role ||
        deviceGeneration != generationSeal.deviceGeneration ||
        resourceGeneration != generationSeal.resourceGenerations[request.resource]
    ) {
        return false
    }
    return when (val descriptor = request.descriptor) {
        is GPUFrameTextureDescriptor -> {
            concreteResource is GPUPreparedConcreteResourceRef.Texture &&
                textureAllocation?.let { allocation ->
                    allocation.logicalBounds == descriptor.logicalBounds &&
                        allocation.backingWidth >= descriptor.logicalBounds.width &&
                        allocation.backingHeight >= descriptor.logicalBounds.height &&
                        allocation.format == descriptor.format &&
                        allocation.sampleCount == descriptor.sampleCount &&
                        allocation.usages == request.usages
                } != false
        }
        is GPUFrameBufferDescriptor ->
            concreteResource is GPUPreparedConcreteResourceRef.Buffer &&
                textureAllocation == null &&
                descriptor.byteSize == request.byteSize
    }
}

private fun GPUResourcePreparationRequest.samePreparationAs(
    other: GPUResourcePreparationRequest,
): Boolean =
    resource == other.resource &&
        descriptor == other.descriptor &&
        role == other.role &&
        usages == other.usages &&
        lifetime == other.lifetime &&
        byteSize == other.byteSize &&
        diagnosticLabel == other.diagnosticLabel

private fun GPUPreparedResourceSet.hasValidCommandEvidence(
    encoderPlan: GPUCommandEncoderPlan,
    generationSeal: GPUPreparedGenerationSeal,
): Boolean {
    val operandReferences = encoderPlan.scopes
        .flatMap { scope -> scope.passCommandStream?.operandBridge.orEmpty() }
        .map { bridge -> bridge.operand }
    val leaseIds = commandResourceLeases.map(GPUPreparedCommandResourceLease::leaseId)
    if (leaseIds.distinct().size != leaseIds.size ||
        commandResourceLeases.any { lease ->
            lease.deviceGeneration != generationSeal.deviceGeneration.value ||
                lease.cacheResult !in setOf(
                    GPUResourceLeaseCacheResult.Create,
                    GPUResourceLeaseCacheResult.Reuse,
                ) ||
                lease.usageLabels.distinct().size != lease.usageLabels.size ||
                operandReferences.count { operand ->
                    lease.matchesExactOperandReference(operand)
                } != 1
        }
    ) {
        return false
    }
    val textureRefs = commandTextureResources.map { resource -> resource.value }
    val bufferRefs = commandBufferResources.map { resource -> resource.value }
    val ordinaryConcreteRefs = ordinaryResources.map { evidence ->
        evidence.concreteResource.value
    } + outputOwnedReadbacks.map { readback -> readback.concreteResource.value }
    if (textureRefs.distinct().size != textureRefs.size ||
        bufferRefs.distinct().size != bufferRefs.size ||
        (textureRefs.toSet() intersect bufferRefs.toSet()).isNotEmpty() ||
        ((textureRefs + bufferRefs).toSet() intersect ordinaryConcreteRefs.toSet()).isNotEmpty()
    ) {
        return false
    }
    val expectedTextureLabels = operandReferences
        .filter { operand -> operand.kind.isPreparedSurfaceTextureKind() }
        .map { operand -> operand.label }
        .toSet()
    val expectedBufferLabels = operandReferences
        .filter { operand -> operand.kind.isPreparedSurfaceBufferKind() }
        .map { operand -> operand.label }
        .toSet()
    if (textureRefs.any { it !in expectedTextureLabels } ||
        bufferRefs.any { it !in expectedBufferLabels }
    ) {
        return false
    }
    val knownDiagnosticLabels = buildSet {
        addAll(textureRefs)
        addAll(bufferRefs)
        addAll(leaseIds)
        addAll(commandResourceLeases.map(GPUPreparedCommandResourceLease::descriptorHash))
        encoderPlan.scopes.forEach { scope ->
            scope.passCommandStream?.operandBridge.orEmpty().forEach { bridge ->
                add(bridge.operand.label)
            }
        }
    }
    val diagnosticIdentities = commandDiagnostics.map { diagnostic ->
        listOf(
            diagnostic.code,
            diagnostic.resourceLabel,
            diagnostic.message,
            diagnostic.facts.entries.sortedBy { entry -> entry.key }.toString(),
        )
    }
    return diagnosticIdentities.distinct().size == diagnosticIdentities.size &&
        commandDiagnostics.all { diagnostic ->
            diagnostic.resourceLabel in knownDiagnosticLabels
        }
}

private fun GPUPreparedCommandResourceLease.matchesExactOperandReference(
    operand: org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandReference,
): Boolean =
    deviceGeneration == operand.deviceGeneration &&
        descriptorHash == operand.descriptorHash &&
        ownerScope == operand.ownerScope &&
        usageLabels == operand.usageLabels &&
        resourceKind.matchesPreparedSurfaceOperandKind(operand.kind)

private fun GPUResourceLeaseKind.matchesPreparedSurfaceOperandKind(
    operandKind:
    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind,
): Boolean = when (this) {
    GPUResourceLeaseKind.UniformSlab ->
        operandKind ==
        org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.UniformBuffer
    GPUResourceLeaseKind.NullBuffer -> operandKind in setOf(
        org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.UniformBuffer,
        org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.StorageBuffer,
    )
    GPUResourceLeaseKind.BindGroup ->
        operandKind ==
        org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.BindGroup
    GPUResourceLeaseKind.Texture -> operandKind.isPreparedSurfaceTextureKind()
    GPUResourceLeaseKind.TextureView -> operandKind in setOf(
        org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.TextureView,
        org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.RenderTarget,
        org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.DepthStencilAttachment,
    )
    GPUResourceLeaseKind.Sampler ->
        operandKind ==
        org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.Sampler
}

private fun org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
    .isPreparedSurfaceTextureKind(): Boolean = this in setOf(
    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.Texture,
    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.TextureView,
    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.RenderTarget,
    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.DepthStencilAttachment,
    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.DestinationCopyTexture,
)

private fun org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
    .isPreparedSurfaceBufferKind(): Boolean = this in setOf(
    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.UniformBuffer,
    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.StorageBuffer,
    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.VertexBuffer,
    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.IndexBuffer,
    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.ReadbackResource,
)

private fun GPUFrameStep.isPreparedSurfaceStep(): Boolean = when (this) {
    is GPUFrameStep.PrepareResourcesStep,
    is GPUFrameStep.UploadResourceStep,
    is GPUFrameStep.RenderPassStep,
    is GPUFrameStep.DependencyBarrierStep,
    is GPUFrameStep.ReadbackCopyStep,
    is GPUFrameStep.AcquireSurfaceOutput,
    is GPUFrameStep.SurfaceBlitRenderPassStep,
    is GPUFrameStep.PostSubmitPresentAction,
    -> true
    else -> false
}

private fun GPUFrameStep.preparedSurfaceOperationKindOrNull(): GPUEncoderOperationKind? =
    when (this) {
        is GPUFrameStep.UploadResourceStep -> GPUEncoderOperationKind.Upload
        is GPUFrameStep.RenderPassStep -> GPUEncoderOperationKind.Render
        is GPUFrameStep.ReadbackCopyStep -> GPUEncoderOperationKind.Readback
        is GPUFrameStep.SurfaceBlitRenderPassStep -> GPUEncoderOperationKind.SurfaceBlit
        else -> null
    }

private fun preparedSurfaceSha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }

private fun refused(
    code: String,
    message: String,
) = GPUPreparedSurfaceNativePreflightResult.Refused(code, message)

private inline fun <T> List<T>.anyIndexed(predicate: (Int, T) -> Boolean): Boolean {
    forEachIndexed { index, value ->
        if (predicate(index, value)) return true
    }
    return false
}
