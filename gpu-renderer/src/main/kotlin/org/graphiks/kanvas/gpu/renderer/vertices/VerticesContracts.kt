package org.graphiks.kanvas.gpu.renderer.vertices

import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendDestinationReadRequirement
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlanner
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendSpecializationRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceAlphaClassification
import org.graphiks.kanvas.gpu.renderer.passes.GPUTargetBlendFacts

/** Typed DrawVertices topology captured before route selection. */
sealed interface GPUVertexMode {
    /** Stable source label used in dumps and hashes. */
    val sourceLabel: String

    /** Triangle list input. */
    data object Triangles : GPUVertexMode {
        override val sourceLabel: String = "Triangles"
    }

    /** Triangle strip input. */
    data object TriangleStrip : GPUVertexMode {
        override val sourceLabel: String = "TriangleStrip"
    }

    /** Triangle fan input, requiring explicit preparation before support. */
    data object TriangleFan : GPUVertexMode {
        override val sourceLabel: String = "TriangleFan"
    }

    /** Future or unsupported topology label. */
    data class Unsupported(override val sourceLabel: String) : GPUVertexMode {
        init {
            require(sourceLabel.isNotBlank()) { "GPUVertexMode.Unsupported.sourceLabel must not be blank" }
        }
    }
}

/** Vertices descriptor captured before buffer planning. */
data class GPUVerticesDescriptor(
    val descriptorVersion: Int = 1,
    val primitiveMode: GPUVertexMode,
    val vertexCount: Int,
    val indexCount: Int? = null,
    val hasColors: Boolean,
    val hasTexCoords: Boolean,
    val boundsLabel: String,
    val provenance: String,
    val sourceKey: String = "vertices:${primitiveMode.sourceLabel}:$vertexCount:${indexCount ?: "none"}:$hasColors:$hasTexCoords",
    val positionFormat: String = "f32x2",
    val colorFormat: String? = if (hasColors) "rgba8unorm-premul" else null,
    val texCoordFormat: String? = if (hasTexCoords) "f32x2" else null,
    val primitiveBlendMode: String = if (hasColors) "SrcOver" else "none",
    val materialLocalCoordinatePolicy: String = if (hasTexCoords) "texcoord" else "position",
    val finitePositions: Boolean = true,
    val sourceMutable: Boolean = false,
) {
    init {
        require(descriptorVersion > 0) { "GPUVerticesDescriptor.descriptorVersion must be positive" }
        require(vertexCount > 0) { "GPUVerticesDescriptor.vertexCount must be positive" }
        require(indexCount == null || indexCount > 0) { "GPUVerticesDescriptor.indexCount must be positive when set" }
        require(boundsLabel.isNotBlank()) { "GPUVerticesDescriptor.boundsLabel must not be blank" }
        require(provenance.isNotBlank()) { "GPUVerticesDescriptor.provenance must not be blank" }
        require(sourceKey.isNotBlank()) { "GPUVerticesDescriptor.sourceKey must not be blank" }
        require(positionFormat.isNotBlank()) { "GPUVerticesDescriptor.positionFormat must not be blank" }
        require(!hasColors || !colorFormat.isNullOrBlank()) {
            "GPUVerticesDescriptor.colorFormat must be set when colors are present"
        }
        require(!hasTexCoords || !texCoordFormat.isNullOrBlank()) {
            "GPUVerticesDescriptor.texCoordFormat must be set when texcoords are present"
        }
        require(primitiveBlendMode.isNotBlank()) { "GPUVerticesDescriptor.primitiveBlendMode must not be blank" }
        require(materialLocalCoordinatePolicy.isNotBlank()) {
            "GPUVerticesDescriptor.materialLocalCoordinatePolicy must not be blank"
        }
    }
}

/** Vertex layout plan. */
data class GPUVertexLayoutPlan(
    val attributes: List<String>,
    val strideBytes: Int,
    val offsets: Map<String, Int>,
    val shaderLocations: Map<String, Int>,
)

/** Vertex color plan. */
data class GPUVertexColorPlan(
    val colorFormat: String,
    val premulPolicy: String,
    val colorSpaceLabel: String,
)

/** Vertex texture-coordinate plan. */
data class GPUVertexTexCoordPlan(
    val coordinateSpaceLabel: String,
    val transformHash: String? = null,
)

/** Primitive blend plan for drawVertices-style input. */
data class GPUPrimitiveBlendPlan(
    val plan: GPUBlendPlan,
)

/** Index buffer plan. */
data class GPUIndexBufferPlan(
    val indexFormat: String,
    val count: Int,
    val validationLabel: String,
    val sourceDescriptorHash: String = "",
    val sourceIndexContentHash: String = "",
    val minIndex: Int = 0,
    val maxIndex: Int = 0,
    val byteCount: Long = 0L,
    val alignment: Int = 4,
    val usageFlags: List<String> = emptyList(),
    val ownerScope: String = "GPURecorderScope",
    val uploadStagingScope: String = "GPURecorderScope",
    val uploadRequirement: String = "upload-before-draw",
    val deviceGeneration: Long = 0L,
    val bufferGeneration: Long = 0L,
    val materialKey: Boolean = false,
)

/** Vertex buffer plan. */
data class GPUVertexBufferPlan(
    val byteCount: Long,
    val layout: GPUVertexLayoutPlan,
    val uploadRequirement: String,
    val sourceDescriptorHash: String = "",
    val sourceVertexContentHash: String = "",
    val layoutHash: String = "",
    val alignment: Int = 4,
    val usageFlags: List<String> = emptyList(),
    val ownerScope: String = "GPURecorderScope",
    val uploadStagingScope: String = "GPURecorderScope",
    val deviceGeneration: Long = 0L,
    val bufferGeneration: Long = 0L,
    val materialKey: Boolean = false,
)

/** Vertices route. */
sealed interface GPUVerticesRoute {
    /** Native vertex/index buffer route. */
    data class NativeBuffers(val vertexBufferPlan: GPUVertexBufferPlan, val indexBufferPlan: GPUIndexBufferPlan?) : GPUVerticesRoute

    /** CPU-prepared vertex buffers consumed by GPU. */
    data class PreparedBuffers(val artifactKey: String, val vertexBufferPlan: GPUVertexBufferPlan) : GPUVerticesRoute

    /** Refused vertices route. */
    data class Refused(val diagnostic: GPUVerticesDiagnostic) : GPUVerticesRoute
}

/** Vertices render-step plan. */
data class GPUVerticesRenderStepPlan(
    val renderStepLabel: String,
    val vertexLayoutHash: String,
    val primitiveMode: String,
)

/** Future mesh descriptor. */
data class GPUMeshDescriptor(
    val meshId: String,
    val topology: String,
    val vertexCount: Int,
    val boundsLabel: String,
)

/** Vertices diagnostic. */
data class GPUVerticesDiagnostic(
    val code: String,
    val verticesLabel: String,
    val message: String,
    val terminal: Boolean,
)

/** Upload plan for CPU-prepared DrawVertices buffer payloads. */
data class GPUVerticesBufferUploadPlan(
    val planHash: String,
    val stagingScope: String,
    val byteRanges: List<String>,
    val totalBytes: Long,
    val beforeUseToken: String,
    val budgetClass: String,
)

/** Resource plan for CPU-prepared DrawVertices buffers before materialization. */
data class GPUVerticesBufferResourcePlan(
    val ownerScope: String,
    val deviceGeneration: Long,
    val bufferGeneration: Long,
    val invalidationFacts: List<String>,
    val usageFlags: List<String>,
    val liveHandle: Boolean,
    val materialKey: Boolean,
)

/** Request for KGPU-M8-002 buffer payload, upload, and resource evidence. */
data class GPUVerticesBufferPlanRequest(
    val routeDecision: GPUVerticesRouteDecisionGatePlan,
    val sourceVertexContentHash: String,
    val sourceIndexContentHash: String? = null,
    val sourceIndexType: String = "uint16",
    val minIndex: Int = 0,
    val maxIndex: Int = routeDecision.descriptor.vertexCount - 1,
    val uploadBeforeDraw: Boolean = true,
    val requiredUsageFlags: Set<String> = setOf("copy_dst", "vertex") +
        if (routeDecision.descriptor.indexCount != null) setOf("index") else emptySet(),
    val availableUsageFlags: Set<String> = requiredUsageFlags,
    val deviceGeneration: Long = 0L,
    val observedDeviceGeneration: Long = deviceGeneration,
    val bufferGeneration: Long = 0L,
    val observedBufferGeneration: Long = bufferGeneration,
    val maxVertexBufferBytes: Long = DEFAULT_VERTICES_BUFFER_MAX_BYTES,
    val maxIndexBufferBytes: Long = DEFAULT_VERTICES_BUFFER_MAX_BYTES,
    val maxTotalUploadBytes: Long = DEFAULT_VERTICES_BUFFER_MAX_BYTES * 2,
    val budgetPolicyId: String = "vertices-buffer-default",
    val ownerScope: String = "GPURecorderScope",
    val uploadStagingScope: String = "GPURecorderScope",
    val liveResourceHandleExposed: Boolean = false,
) {
    init {
        require(sourceVertexContentHash.isNotBlank()) {
            "GPUVerticesBufferPlanRequest.sourceVertexContentHash must not be blank"
        }
        require(sourceIndexContentHash == null || sourceIndexContentHash.isNotBlank()) {
            "GPUVerticesBufferPlanRequest.sourceIndexContentHash must not be blank when set"
        }
        require(sourceIndexType.isNotBlank()) { "GPUVerticesBufferPlanRequest.sourceIndexType must not be blank" }
        require(minIndex >= 0) { "GPUVerticesBufferPlanRequest.minIndex must not be negative" }
        require(maxIndex >= minIndex) { "GPUVerticesBufferPlanRequest.maxIndex must be >= minIndex" }
        require(deviceGeneration >= 0L) { "GPUVerticesBufferPlanRequest.deviceGeneration must not be negative" }
        require(observedDeviceGeneration >= 0L) {
            "GPUVerticesBufferPlanRequest.observedDeviceGeneration must not be negative"
        }
        require(bufferGeneration >= 0L) { "GPUVerticesBufferPlanRequest.bufferGeneration must not be negative" }
        require(observedBufferGeneration >= 0L) {
            "GPUVerticesBufferPlanRequest.observedBufferGeneration must not be negative"
        }
        require(maxVertexBufferBytes > 0L) { "GPUVerticesBufferPlanRequest.maxVertexBufferBytes must be positive" }
        require(maxIndexBufferBytes > 0L) { "GPUVerticesBufferPlanRequest.maxIndexBufferBytes must be positive" }
        require(maxTotalUploadBytes > 0L) { "GPUVerticesBufferPlanRequest.maxTotalUploadBytes must be positive" }
        require(budgetPolicyId.isNotBlank()) { "GPUVerticesBufferPlanRequest.budgetPolicyId must not be blank" }
        require(ownerScope.isNotBlank()) { "GPUVerticesBufferPlanRequest.ownerScope must not be blank" }
        require(uploadStagingScope.isNotBlank()) {
            "GPUVerticesBufferPlanRequest.uploadStagingScope must not be blank"
        }
    }
}

/** Evidence result for KGPU-M8-002 buffer payload, upload, and resource plans. */
data class GPUVerticesBufferPlanGatePlan(
    val commandId: String,
    val evidenceRow: String,
    val routeKind: String,
    val classification: String,
    val promoted: Boolean,
    val productActivation: Boolean,
    val materialized: Boolean,
    val descriptorHash: String,
    val routeDecisionHash: String,
    val artifactKey: String,
    val vertexBufferPlan: GPUVertexBufferPlan,
    val vertexBufferHash: String,
    val indexBufferPlan: GPUIndexBufferPlan?,
    val indexBufferHash: String?,
    val uploadPlan: GPUVerticesBufferUploadPlan,
    val resourcePlan: GPUVerticesBufferResourcePlan,
    val materialKeyFacts: List<String>,
    val refusalFacts: Map<String, String>,
    val diagnostics: List<GPUVerticesDiagnostic>,
) {
    /** Returns deterministic PM/review lines without claiming product support. */
    fun dumpLines(): List<String> {
        val terminal = diagnostics.singleOrNull { diagnostic -> diagnostic.terminal }
        if (terminal != null) {
            return listOf(
                "vertices:buffers.refused row=$evidenceRow routeKind=$routeKind classification=$classification " +
                    "promoted=$promoted productActivation=$productActivation materialized=$materialized " +
                    "command=$commandId descriptor=$descriptorHash routeDecision=$routeDecisionHash reason=${terminal.code}",
                "vertices:refusal facts=${refusalFacts.dumpVerticesFacts()}",
                VERTICES_BUFFER_NONCLAIM_LINE,
            )
        }

        val diagnostic = diagnostics.single()
        val indexLine = indexBufferPlan?.let { indexPlan ->
            "vertices:index-buffer hash=$indexBufferHash format=${indexPlan.indexFormat} count=${indexPlan.count} " +
                "range=${indexPlan.minIndex}..${indexPlan.maxIndex} bytes=${indexPlan.byteCount} " +
                "alignment=${indexPlan.alignment} usage=${indexPlan.usageFlags.joinToString(",")} " +
                "owner=${indexPlan.ownerScope} generation=${indexPlan.bufferGeneration} " +
                "upload=${indexPlan.uploadRequirement}"
        } ?: "vertices:index-buffer none"

        return listOf(
            "vertices:buffers row=$evidenceRow routeKind=$routeKind classification=$classification " +
                "promoted=$promoted productActivation=$productActivation materialized=$materialized " +
                "command=$commandId descriptor=$descriptorHash routeDecision=$routeDecisionHash artifact=$artifactKey",
            "vertices:vertex-buffer hash=$vertexBufferHash layout=${vertexBufferPlan.layoutHash} " +
                "bytes=${vertexBufferPlan.byteCount} alignment=${vertexBufferPlan.alignment} " +
                "usage=${vertexBufferPlan.usageFlags.joinToString(",")} owner=${vertexBufferPlan.ownerScope} " +
                "generation=${vertexBufferPlan.bufferGeneration} upload=${vertexBufferPlan.uploadRequirement}",
            indexLine,
            "vertices:upload plan=${uploadPlan.planHash} staging=${uploadPlan.stagingScope} " +
                "ranges=${uploadPlan.byteRanges.joinToString(",")} bytes=${uploadPlan.totalBytes} " +
                "dependency=${uploadPlan.beforeUseToken} budget=${uploadPlan.budgetClass}",
            "vertices:resource owner=${resourcePlan.ownerScope} " +
                "deviceGeneration=${resourcePlan.deviceGeneration} " +
                "bufferGeneration=${resourcePlan.bufferGeneration} " +
                "invalidation=${resourcePlan.invalidationFacts.joinToString(",")} " +
                "usage=${resourcePlan.usageFlags.joinToString(",")} liveHandle=${resourcePlan.liveHandle} " +
                "materialKey=${resourcePlan.materialKey}",
            "vertices:key materialFacts=${materialKeyFacts.joinToString(",")} " +
                "resourceFactsExcluded=bufferBytes,bufferGenerations,resourceHandles,uploadOffsets,vertexIndexPayload",
            "vertices:diagnostic code=${diagnostic.code} terminal=${diagnostic.terminal}",
            VERTICES_BUFFER_NONCLAIM_LINE,
        )
    }
}

/** One DrawVertices invocation considered by the contract-only batching evidence planner. */
data class GPUVerticesBatchInvocation(
    val invocationId: String,
    val routeDecision: GPUVerticesRouteDecisionGatePlan,
    val bufferPlan: GPUVerticesBufferPlanGatePlan,
    val paintOrder: Int,
    val materialKeyHash: String,
    val pipelineKeyHash: String,
    val layoutHash: String,
    val renderStepLabel: String,
    val topology: String,
    val blendClass: String,
    val layerId: String,
    val orderBand: String,
    val clipKey: String,
    val destinationReadClass: String,
    val barrierGeneration: Long,
    val uploadGeneration: Long,
    val overlapClass: String,
    val sortWindowId: String,
) {
    init {
        require(invocationId.isNotBlank()) { "GPUVerticesBatchInvocation.invocationId must not be blank" }
        require(paintOrder >= 0) { "GPUVerticesBatchInvocation.paintOrder must not be negative" }
        require(materialKeyHash.isNotBlank()) { "GPUVerticesBatchInvocation.materialKeyHash must not be blank" }
        require(pipelineKeyHash.isNotBlank()) { "GPUVerticesBatchInvocation.pipelineKeyHash must not be blank" }
        require(layoutHash.isNotBlank()) { "GPUVerticesBatchInvocation.layoutHash must not be blank" }
        require(renderStepLabel.isNotBlank()) { "GPUVerticesBatchInvocation.renderStepLabel must not be blank" }
        require(topology.isNotBlank()) { "GPUVerticesBatchInvocation.topology must not be blank" }
        require(blendClass.isNotBlank()) { "GPUVerticesBatchInvocation.blendClass must not be blank" }
        require(layerId.isNotBlank()) { "GPUVerticesBatchInvocation.layerId must not be blank" }
        require(orderBand.isNotBlank()) { "GPUVerticesBatchInvocation.orderBand must not be blank" }
        require(clipKey.isNotBlank()) { "GPUVerticesBatchInvocation.clipKey must not be blank" }
        require(destinationReadClass.isNotBlank()) {
            "GPUVerticesBatchInvocation.destinationReadClass must not be blank"
        }
        require(barrierGeneration >= 0L) { "GPUVerticesBatchInvocation.barrierGeneration must not be negative" }
        require(uploadGeneration >= 0L) { "GPUVerticesBatchInvocation.uploadGeneration must not be negative" }
        require(overlapClass.isNotBlank()) { "GPUVerticesBatchInvocation.overlapClass must not be blank" }
        require(sortWindowId.isNotBlank()) { "GPUVerticesBatchInvocation.sortWindowId must not be blank" }
    }
}

/** Request for KGPU-M8-003 vertices batching, sort, and split/refusal evidence. */
data class GPUVerticesBatchingRequest(
    val scopeId: String,
    val invocations: List<GPUVerticesBatchInvocation>,
) {
    init {
        require(scopeId.isNotBlank()) { "GPUVerticesBatchingRequest.scopeId must not be blank" }
    }
}

/** Deterministic batch key evidence for adjacent compatible DrawVertices invocations. */
data class GPUVerticesBatch(
    val batchKeyHash: String,
    val invocationIds: List<String>,
    val layerId: String,
    val orderBand: String,
    val sortWindowId: String,
    val pipelineKeyHash: String,
    val materialKeyHash: String,
    val layoutHash: String,
    val clipKey: String,
    val destinationReadClass: String,
    val barrierGeneration: Long,
    val uploadGeneration: Long,
) {
    init {
        require(invocationIds.isNotEmpty()) { "GPUVerticesBatch.invocationIds must not be empty" }
        require(sortWindowId.isNotBlank()) { "GPUVerticesBatch.sortWindowId must not be blank" }
    }
}

/** Split reason between adjacent vertices invocations. */
data class GPUVerticesBatchSplitReason(
    val reasonCode: String,
    val beforeInvocationId: String,
    val afterInvocationId: String,
)

/** Non-promoted KGPU-M8-003 batching, sort, and refusal evidence. */
data class GPUVerticesBatchingGatePlan(
    val scopeId: String,
    val evidenceRow: String,
    val routeKind: String,
    val classification: String,
    val promoted: Boolean,
    val productActivation: Boolean,
    val materialized: Boolean,
    val batches: List<GPUVerticesBatch>,
    val splitReasons: List<GPUVerticesBatchSplitReason>,
    val sortKeyHash: String,
    val sortWindowId: String,
    val sortOrder: List<String>,
    val adjacentCandidates: Int,
    val acceptedAdjacent: Int,
    val refusalFacts: Map<String, String>,
    val diagnostics: List<GPUVerticesDiagnostic>,
) {
    /** Returns deterministic PM/review lines without claiming executable batching. */
    fun dumpLines(): List<String> {
        val terminal = diagnostics.singleOrNull { diagnostic -> diagnostic.terminal }
        if (terminal != null) {
            return listOf(
                "vertices:batch.refused row=$evidenceRow routeKind=$routeKind classification=$classification " +
                    "promoted=$promoted productActivation=$productActivation materialized=$materialized " +
                    "scope=$scopeId reason=${terminal.code}",
                "vertices:refusal facts=${refusalFacts.dumpVerticesFacts()}",
                VERTICES_BATCH_NONCLAIM_LINE,
            )
        }

        val diagnostic = diagnostics.single()
        return listOf(
            "vertices:batch row=$evidenceRow routeKind=$routeKind classification=$classification " +
                "promoted=$promoted productActivation=$productActivation materialized=$materialized " +
                "scope=$scopeId batches=${batches.size} splits=${splitReasons.size}",
        ) + batches.map { batch ->
            "vertices:batch-key hash=${batch.batchKeyHash} invocations=${batch.invocationIds.joinToString(",")} " +
                "axes=layer=${batch.layerId},orderBand=${batch.orderBand},sortWindow=${batch.sortWindowId}," +
                "pipeline=${batch.pipelineKeyHash}," +
                "material=${batch.materialKeyHash},layout=${batch.layoutHash},clip=${batch.clipKey}," +
                "destinationRead=${batch.destinationReadClass},barrier=${batch.barrierGeneration}," +
                "uploadGeneration=${batch.uploadGeneration}"
        } + listOf(
            "vertices:sort window=$sortWindowId " +
                "compact=$sortKeyHash order=${sortOrder.joinToString(",")} " +
                "overlap=CompatibleOverlap insertion=original-order",
        ) + if (splitReasons.isEmpty()) {
            listOf("vertices:split none")
        } else {
            splitReasons.map { split ->
                "vertices:split reason=${split.reasonCode} before=${split.beforeInvocationId} after=${split.afterInvocationId}"
            }
        } + listOf(
            "vertices:telemetry adjacentCandidates=$adjacentCandidates acceptedAdjacent=$acceptedAdjacent " +
                "splitCount=${splitReasons.size} refused=false performanceReady=false",
            "vertices:diagnostic code=${diagnostic.code} terminal=${diagnostic.terminal}",
            VERTICES_BATCH_NONCLAIM_LINE,
        )
    }
}

/** Request for KGPU-M8-001 descriptor and route decision evidence. */
data class GPUVerticesRouteDecisionRequest(
    val commandId: String,
    val descriptor: GPUVerticesDescriptor,
    val materialKeyHash: String,
    val targetFormatClass: String,
    val adapterEvidenceLabel: String?,
    val wgslLayoutEvidenceLabel: String?,
    val maxVertexCount: Int = 65_535,
    val maxIndexCount: Int = 65_535,
    val acceptedTopologies: Set<GPUVertexMode> = setOf(GPUVertexMode.Triangles, GPUVertexMode.TriangleStrip),
    val acceptedPositionFormats: Set<String> = setOf("f32x2"),
    val acceptedColorFormats: Set<String> = setOf("rgba8unorm-premul"),
    val acceptedTexCoordFormats: Set<String> = setOf("f32x2"),
) {
    init {
        require(commandId.isNotBlank()) { "GPUVerticesRouteDecisionRequest.commandId must not be blank" }
        require(materialKeyHash.isNotBlank()) { "GPUVerticesRouteDecisionRequest.materialKeyHash must not be blank" }
        require(targetFormatClass.isNotBlank()) { "GPUVerticesRouteDecisionRequest.targetFormatClass must not be blank" }
        require(maxVertexCount > 0) { "GPUVerticesRouteDecisionRequest.maxVertexCount must be positive" }
        require(maxIndexCount > 0) { "GPUVerticesRouteDecisionRequest.maxIndexCount must be positive" }
        require(acceptedTopologies.isNotEmpty()) {
            "GPUVerticesRouteDecisionRequest.acceptedTopologies must not be empty"
        }
        require(acceptedPositionFormats.isNotEmpty()) {
            "GPUVerticesRouteDecisionRequest.acceptedPositionFormats must not be empty"
        }
    }
}

/** Evidence result for DrawVertices descriptor and route decisions. */
data class GPUVerticesRouteDecisionGatePlan(
    val commandId: String,
    val evidenceRow: String,
    val routeKind: String,
    val classification: String,
    val promoted: Boolean,
    val productActivation: Boolean,
    val materialized: Boolean,
    val descriptor: GPUVerticesDescriptor,
    val descriptorHash: String,
    val variant: String,
    val layout: GPUVertexLayoutPlan,
    val layoutHash: String,
    val renderStep: GPUVerticesRenderStepPlan,
    val materialKeyHash: String,
    val materialKeyFacts: List<String>,
    val pipelineKeyHash: String,
    val pipelineKeyFacts: List<String>,
    val targetFormatClass: String,
    val adapterEvidenceLabel: String?,
    val wgslLayoutEvidenceLabel: String?,
    val refusalFacts: Map<String, String>,
    val diagnostics: List<GPUVerticesDiagnostic>,
) {
    /** Returns deterministic PM/review lines without claiming product support. */
    fun dumpLines(): List<String> {
        val terminal = diagnostics.singleOrNull { diagnostic -> diagnostic.terminal }
        if (terminal != null) {
            return listOf(
                "vertices:descriptor.refused row=$evidenceRow routeKind=$routeKind " +
                    "classification=$classification promoted=$promoted productActivation=$productActivation " +
                    "materialized=$materialized command=$commandId descriptor=$descriptorHash " +
                "reason=${terminal.code} mode=${descriptor.primitiveMode.sourceLabel} vertexCount=${descriptor.vertexCount} " +
                    "indexCount=${descriptor.indexCount ?: "none"}",
                "vertices:refusal facts=${refusalFacts.dumpVerticesFacts()}",
                VERTICES_ROUTE_NONCLAIM_LINE,
            )
        }

        val diagnostic = diagnostics.single()
        return listOf(
            "vertices:descriptor row=$evidenceRow routeKind=$routeKind classification=$classification " +
                "promoted=$promoted productActivation=$productActivation materialized=$materialized " +
                "command=$commandId descriptor=$descriptorHash mode=${descriptor.primitiveMode.sourceLabel} " +
                "vertexCount=${descriptor.vertexCount} indexCount=${descriptor.indexCount ?: "none"} variant=$variant",
            "vertices:layout hash=$layoutHash attributes=${layout.dumpAttributes()} " +
                "stride=${layout.strideBytes} locations=${layout.dumpLocations()}",
            "vertices:route decision=NativeDescriptor renderStep=${renderStep.renderStepLabel} " +
                "pipeline=$pipelineKeyHash material=$materialKeyHash target=$targetFormatClass " +
                "adapter=${adapterEvidenceLabel ?: "missing"} wgsl=${wgslLayoutEvidenceLabel ?: "missing"}",
            "vertices:key materialFacts=${materialKeyFacts.joinToString(",")} " +
                "pipelineFacts=${pipelineKeyFacts.joinToString(",")}",
            "vertices:diagnostic code=${diagnostic.code} terminal=${diagnostic.terminal}",
            VERTICES_ROUTE_NONCLAIM_LINE,
        )
    }
}

/** Planner for descriptor-only DrawVertices route decisions. */
class GPUVerticesRouteDecisionPlanner {
    /** Plans a non-promoted native route decision or stable refusal. */
    fun plan(request: GPUVerticesRouteDecisionRequest): GPUVerticesRouteDecisionGatePlan {
        val descriptorHash = verticesDescriptorHash(request.descriptor)
        val variant = request.descriptor.variantLabel()
        val layout = request.descriptor.layoutPlan()
        val layoutHash = verticesLayoutHash(layout, request.descriptor)
        val renderStep = GPUVerticesRenderStepPlan(
            renderStepLabel = "vertices.${request.descriptor.primitiveMode.renderStepModeLabel()}.$variant.v1",
            vertexLayoutHash = layoutHash,
            primitiveMode = request.descriptor.primitiveMode.sourceLabel,
        )
        val materialKeyFacts = request.descriptor.materialKeyFacts()
        val pipelineKeyFacts = request.pipelineKeyFacts(layoutHash)
        val pipelineKeyHash = verticesStableHash(
            listOf(
                "vertices-pipeline-key-v1",
                request.materialKeyHash,
                request.targetFormatClass,
            ) + pipelineKeyFacts,
        )
        val refusalCode = request.refusalCode()

        if (refusalCode != null) {
            return gatePlan(
                request = request,
                descriptorHash = descriptorHash,
                variant = variant,
                layout = layout,
                layoutHash = layoutHash,
                renderStep = renderStep,
                materialKeyFacts = materialKeyFacts,
                pipelineKeyFacts = pipelineKeyFacts,
                pipelineKeyHash = pipelineKeyHash,
                routeKind = "RefuseDiagnostic",
                diagnostics = listOf(
                    GPUVerticesDiagnostic(
                        code = refusalCode,
                        verticesLabel = request.commandId,
                        message = "vertices route decision refused: $refusalCode",
                        terminal = true,
                    ),
                ),
                refusalFacts = request.refusalFacts(refusalCode),
            )
        }

        return gatePlan(
            request = request,
            descriptorHash = descriptorHash,
            variant = variant,
            layout = layout,
            layoutHash = layoutHash,
            renderStep = renderStep,
            materialKeyFacts = materialKeyFacts,
            pipelineKeyFacts = pipelineKeyFacts,
            pipelineKeyHash = pipelineKeyHash,
            routeKind = "GPUNative",
            diagnostics = listOf(
                GPUVerticesDiagnostic(
                    code = VERTICES_ROUTE_ACCEPTED_CODE,
                    verticesLabel = request.commandId,
                    message = "vertices descriptor route decision accepted without product activation",
                    terminal = false,
                ),
            ),
            refusalFacts = emptyMap(),
        )
    }

    private fun gatePlan(
        request: GPUVerticesRouteDecisionRequest,
        descriptorHash: String,
        variant: String,
        layout: GPUVertexLayoutPlan,
        layoutHash: String,
        renderStep: GPUVerticesRenderStepPlan,
        materialKeyFacts: List<String>,
        pipelineKeyFacts: List<String>,
        pipelineKeyHash: String,
        routeKind: String,
        diagnostics: List<GPUVerticesDiagnostic>,
        refusalFacts: Map<String, String>,
    ): GPUVerticesRouteDecisionGatePlan =
        GPUVerticesRouteDecisionGatePlan(
            commandId = request.commandId,
            evidenceRow = VERTICES_ROUTE_EVIDENCE_ROW,
            routeKind = routeKind,
            classification = "TargetNative",
            promoted = false,
            productActivation = true,
            materialized = false,
            descriptor = request.descriptor,
            descriptorHash = descriptorHash,
            variant = variant,
            layout = layout,
            layoutHash = layoutHash,
            renderStep = renderStep,
            materialKeyHash = request.materialKeyHash,
            materialKeyFacts = materialKeyFacts,
            pipelineKeyHash = pipelineKeyHash,
            pipelineKeyFacts = pipelineKeyFacts,
            targetFormatClass = request.targetFormatClass,
            adapterEvidenceLabel = request.adapterEvidenceLabel,
            wgslLayoutEvidenceLabel = request.wgslLayoutEvidenceLabel,
            refusalFacts = refusalFacts,
            diagnostics = diagnostics,
        )
}

/** Planner for CPU-prepared DrawVertices buffer payload, upload, and resource evidence. */
class GPUVerticesBufferPlanPlanner {
    /** Plans non-materialized vertex/index buffer payloads or stable refusal evidence. */
    fun plan(request: GPUVerticesBufferPlanRequest): GPUVerticesBufferPlanGatePlan {
        val descriptor = request.routeDecision.descriptor
        val descriptorHash = request.routeDecision.descriptorHash
        val routeDecisionHash = verticesStableHash(
            listOf(
                "vertices-route-decision-gate-v1",
                request.routeDecision.commandId,
                request.routeDecision.routeKind,
                descriptorHash,
                request.routeDecision.layoutHash,
                request.routeDecision.pipelineKeyHash,
            ),
        )
        val vertexBytes = descriptor.vertexCount.toLong() * request.routeDecision.layout.strideBytes.toLong()
        val indexFormat = request.sourceIndexType
        val indexElementBytes = indexFormat.indexElementBytes()
        val indexBytes = descriptor.indexCount?.toLong()?.times(indexElementBytes ?: 0L) ?: 0L
        val totalUploadBytes = vertexBytes + indexBytes
        val artifactKey = verticesBufferArtifactKey(request, routeDecisionHash)
        val vertexBufferHash = verticesStableHash(
            listOf(
                "vertices-vertex-buffer-v1",
                descriptorHash,
                request.routeDecision.layoutHash,
                request.sourceVertexContentHash,
                vertexBytes.toString(),
                request.bufferGeneration.toString(),
            ),
        )
        val indexBufferHash = descriptor.indexCount?.let {
            verticesStableHash(
                listOf(
                    "vertices-index-buffer-v1",
                    descriptorHash,
                    indexFormat,
                    request.sourceIndexContentHash ?: "missing",
                    it.toString(),
                    request.minIndex.toString(),
                    request.maxIndex.toString(),
                    request.bufferGeneration.toString(),
                ),
            )
        }
        val vertexBufferPlan = GPUVertexBufferPlan(
            byteCount = vertexBytes,
            layout = request.routeDecision.layout,
            uploadRequirement = "upload-before-draw",
            sourceDescriptorHash = descriptorHash,
            sourceVertexContentHash = request.sourceVertexContentHash,
            layoutHash = request.routeDecision.layoutHash,
            alignment = VERTICES_BUFFER_ALIGNMENT,
            usageFlags = listOf("copy_dst", "vertex"),
            ownerScope = request.ownerScope,
            uploadStagingScope = request.uploadStagingScope,
            deviceGeneration = request.deviceGeneration,
            bufferGeneration = request.bufferGeneration,
            materialKey = false,
        )
        val indexBufferPlan = descriptor.indexCount?.let { indexCount ->
            GPUIndexBufferPlan(
                indexFormat = indexFormat,
                count = indexCount,
                validationLabel = "range:${request.minIndex}..${request.maxIndex}",
                sourceDescriptorHash = descriptorHash,
                sourceIndexContentHash = request.sourceIndexContentHash ?: "missing",
                minIndex = request.minIndex,
                maxIndex = request.maxIndex,
                byteCount = indexBytes,
                alignment = VERTICES_BUFFER_ALIGNMENT,
                usageFlags = listOf("copy_dst", "index"),
                ownerScope = request.ownerScope,
                uploadStagingScope = request.uploadStagingScope,
                uploadRequirement = "upload-before-draw",
                deviceGeneration = request.deviceGeneration,
                bufferGeneration = request.bufferGeneration,
                materialKey = false,
            )
        }
        val uploadPlan = GPUVerticesBufferUploadPlan(
            planHash = verticesStableHash(
                listOf(
                    "vertices-buffer-upload-v1",
                    descriptorHash,
                    vertexBufferHash,
                    indexBufferHash ?: "none",
                    totalUploadBytes.toString(),
                    request.budgetPolicyId,
                ),
            ),
            stagingScope = request.uploadStagingScope,
            byteRanges = buildList {
                add("vertex:0..${vertexBytes - 1L}")
                if (indexBytes > 0L) {
                    add("index:$vertexBytes..${totalUploadBytes - 1L}")
                }
            },
            totalBytes = totalUploadBytes,
            beforeUseToken = "upload-before-draw:${request.routeDecision.commandId}",
            budgetClass = request.budgetPolicyId,
        )
        val resourcePlan = GPUVerticesBufferResourcePlan(
            ownerScope = request.ownerScope,
            deviceGeneration = request.deviceGeneration,
            bufferGeneration = request.bufferGeneration,
            invalidationFacts = listOf(
                "buffer-generation:${request.bufferGeneration}",
                "device-generation:${request.deviceGeneration}",
            ),
            usageFlags = (vertexBufferPlan.usageFlags + (indexBufferPlan?.usageFlags ?: emptyList())).distinct().sorted(),
            liveHandle = false,
            materialKey = false,
        )
        val refusalCode = request.refusalCode(
            vertexBytes = vertexBytes,
            indexBytes = indexBytes,
            totalUploadBytes = totalUploadBytes,
            indexElementBytes = indexElementBytes,
        )

        if (refusalCode != null) {
            return gatePlan(
                request = request,
                descriptorHash = descriptorHash,
                routeDecisionHash = routeDecisionHash,
                artifactKey = artifactKey,
                vertexBufferPlan = vertexBufferPlan,
                vertexBufferHash = vertexBufferHash,
                indexBufferPlan = indexBufferPlan,
                indexBufferHash = indexBufferHash,
                uploadPlan = uploadPlan,
                resourcePlan = resourcePlan,
                routeKind = "RefuseDiagnostic",
                diagnostics = listOf(
                    GPUVerticesDiagnostic(
                        code = refusalCode,
                        verticesLabel = request.routeDecision.commandId,
                        message = "vertices buffer plan refused: $refusalCode",
                        terminal = true,
                    ),
                ),
                refusalFacts = request.refusalFacts(
                    reasonCode = refusalCode,
                    vertexBytes = vertexBytes,
                    indexBytes = indexBytes,
                    totalUploadBytes = totalUploadBytes,
                    indexElementBytes = indexElementBytes,
                ),
            )
        }

        return gatePlan(
            request = request,
            descriptorHash = descriptorHash,
            routeDecisionHash = routeDecisionHash,
            artifactKey = artifactKey,
            vertexBufferPlan = vertexBufferPlan,
            vertexBufferHash = vertexBufferHash,
            indexBufferPlan = indexBufferPlan,
            indexBufferHash = indexBufferHash,
            uploadPlan = uploadPlan,
            resourcePlan = resourcePlan,
            routeKind = "CPUPreparedGPU",
            diagnostics = listOf(
                GPUVerticesDiagnostic(
                    code = VERTICES_BUFFER_ACCEPTED_CODE,
                    verticesLabel = request.routeDecision.commandId,
                    message = "vertices buffer payload and resource plans accepted without product activation",
                    terminal = false,
                ),
            ),
            refusalFacts = emptyMap(),
        )
    }

    private fun gatePlan(
        request: GPUVerticesBufferPlanRequest,
        descriptorHash: String,
        routeDecisionHash: String,
        artifactKey: String,
        vertexBufferPlan: GPUVertexBufferPlan,
        vertexBufferHash: String,
        indexBufferPlan: GPUIndexBufferPlan?,
        indexBufferHash: String?,
        uploadPlan: GPUVerticesBufferUploadPlan,
        resourcePlan: GPUVerticesBufferResourcePlan,
        routeKind: String,
        diagnostics: List<GPUVerticesDiagnostic>,
        refusalFacts: Map<String, String>,
    ): GPUVerticesBufferPlanGatePlan =
        GPUVerticesBufferPlanGatePlan(
            commandId = request.routeDecision.commandId,
            evidenceRow = VERTICES_BUFFER_EVIDENCE_ROW,
            routeKind = routeKind,
            classification = "TargetPrepared",
            promoted = false,
            productActivation = true,
            materialized = false,
            descriptorHash = descriptorHash,
            routeDecisionHash = routeDecisionHash,
            artifactKey = artifactKey,
            vertexBufferPlan = vertexBufferPlan,
            vertexBufferHash = vertexBufferHash,
            indexBufferPlan = indexBufferPlan,
            indexBufferHash = indexBufferHash,
            uploadPlan = uploadPlan,
            resourcePlan = resourcePlan,
            materialKeyFacts = request.routeDecision.materialKeyFacts,
            refusalFacts = refusalFacts,
            diagnostics = diagnostics,
        )
}

/** Planner for non-promoted adjacent DrawVertices batching and split evidence. */
class GPUVerticesBatchingPlanner {
    /** Plans deterministic batch keys, sort preimages, and split/refusal diagnostics. */
    fun plan(request: GPUVerticesBatchingRequest): GPUVerticesBatchingGatePlan {
        val refusal = request.refusalCodeAndFacts()
        if (refusal != null) {
            val (code, facts) = refusal
            return gatePlan(
                request = request,
                batches = emptyList(),
                splitReasons = emptyList(),
                sortKeyHash = verticesStableHash(listOf("vertices-batch-refused-v1", request.scopeId, code)),
                routeKind = "RefuseDiagnostic",
                diagnostics = listOf(
                    GPUVerticesDiagnostic(
                        code = code,
                        verticesLabel = request.scopeId,
                        message = "vertices batching refused: $code",
                        terminal = true,
                    ),
                ),
                refusalFacts = facts,
            )
        }

        val splitReasons = mutableListOf<GPUVerticesBatchSplitReason>()
        val batches = mutableListOf<GPUVerticesBatch>()
        var current = mutableListOf(request.invocations.first())

        request.invocations.zipWithNext().forEach { (previous, next) ->
            val reason = previous.splitReason(next)
            if (reason == null) {
                current += next
            } else {
                batches += current.toVerticesBatch()
                splitReasons += GPUVerticesBatchSplitReason(
                    reasonCode = reason,
                    beforeInvocationId = previous.invocationId,
                    afterInvocationId = next.invocationId,
                )
                current = mutableListOf(next)
            }
        }
        batches += current.toVerticesBatch()

        val sortKeyHash = verticesStableHash(
            listOf(
                "vertices-batch-sort-v1",
                request.scopeId,
                request.invocations.joinToString(",") { invocation ->
                    "${invocation.invocationId}:${invocation.paintOrder}:${invocation.sortWindowId}"
                },
                batches.joinToString(",") { batch -> batch.batchKeyHash },
                splitReasons.joinToString(",") { split -> split.reasonCode },
            ),
        )

        return gatePlan(
            request = request,
            batches = batches,
            splitReasons = splitReasons,
            sortKeyHash = sortKeyHash,
            routeKind = "GPUNative",
            diagnostics = listOf(
                GPUVerticesDiagnostic(
                    code = VERTICES_BATCH_ACCEPTED_CODE,
                    verticesLabel = request.scopeId,
                    message = "vertices batching keys and sort evidence accepted without product activation",
                    terminal = false,
                ),
            ),
            refusalFacts = emptyMap(),
        )
    }

    private fun gatePlan(
        request: GPUVerticesBatchingRequest,
        batches: List<GPUVerticesBatch>,
        splitReasons: List<GPUVerticesBatchSplitReason>,
        sortKeyHash: String,
        routeKind: String,
        diagnostics: List<GPUVerticesDiagnostic>,
        refusalFacts: Map<String, String>,
    ): GPUVerticesBatchingGatePlan =
        GPUVerticesBatchingGatePlan(
            scopeId = request.scopeId,
            evidenceRow = VERTICES_BATCH_EVIDENCE_ROW,
            routeKind = routeKind,
            classification = "ImplementationCandidate",
            promoted = false,
            productActivation = true,
            materialized = false,
            batches = batches,
            splitReasons = splitReasons,
            sortKeyHash = sortKeyHash,
            sortWindowId = request.invocations.map { invocation -> invocation.sortWindowId }
                .distinct()
                .joinToString("|")
                .ifEmpty { "none" },
            sortOrder = request.invocations.map { invocation -> "${invocation.invocationId}@${invocation.paintOrder}" },
            adjacentCandidates = (request.invocations.size - 1).coerceAtLeast(0),
            acceptedAdjacent = (request.invocations.size - 1 - splitReasons.size).coerceAtLeast(0),
            refusalFacts = refusalFacts,
            diagnostics = diagnostics,
        )
}

private const val VERTICES_ROUTE_EVIDENCE_ROW = "gpu-renderer.vertices.descriptor"
private const val VERTICES_ROUTE_ACCEPTED_CODE = "accepted.vertices.route_decision"
private const val VERTICES_BUFFER_EVIDENCE_ROW = "gpu-renderer.vertices.buffers"
private const val VERTICES_BUFFER_ACCEPTED_CODE = "accepted.vertices.buffer_plan"
private const val VERTICES_BATCH_EVIDENCE_ROW = "gpu-renderer.vertices-batching"
private const val VERTICES_BATCH_ACCEPTED_CODE = "accepted.vertices.batching_plan"
private const val DEFAULT_VERTICES_BUFFER_MAX_BYTES = 1_048_576L
private const val VERTICES_BUFFER_ALIGNMENT = 4
private const val VERTICES_ROUTE_NONCLAIM_LINE =
    "vertices:nonclaim drawVerticesSupport=false adapterBacked=false " +
        "vertexBufferUpload=false indexBufferUpload=false primitiveBlenderSupport=false " +
        "texcoordMaterialSupport=false meshSupport=false " +
        "productActivation=true cpuRenderedTextureFallback=false"
private const val VERTICES_BUFFER_NONCLAIM_LINE =
    "vertices:nonclaim drawVerticesSupport=false adapterBacked=false " +
        "vertexBufferUpload=false indexBufferUpload=false meshSupport=false batchingSupport=false " +
        "productActivation=true cpuRenderedTextureFallback=false liveHandles=false"
private const val VERTICES_BATCH_NONCLAIM_LINE =
    "vertices:nonclaim batchingSupport=false drawVerticesSupport=false adapterBacked=false " +
        "productActivation=true performanceReady=false crossLayerBatching=false " +
        "destinationReadBatching=false cpuRenderedTextureFallback=false"

private fun GPUVerticesRouteDecisionRequest.refusalCode(): String? =
    when {
        descriptor.sourceMutable -> "unsupported.vertices.key_nondeterministic"
        !descriptor.finitePositions -> "unsupported.vertices.positions_nonfinite"
        descriptor.primitiveMode == GPUVertexMode.TriangleFan -> "unsupported.vertices.triangle_fan_unprepared"
        descriptor.primitiveMode !in acceptedTopologies -> "unsupported.vertices.topology"
        descriptor.vertexCount > maxVertexCount -> "unsupported.vertices.vertex_count_budget"
        (descriptor.indexCount ?: 0) > maxIndexCount -> "unsupported.vertices.index_count_budget"
        descriptor.positionFormat !in acceptedPositionFormats -> "unsupported.vertices.attribute_format"
        descriptor.hasColors && descriptor.colorFormat !in acceptedColorFormats -> "unsupported.vertices.color_format"
        descriptor.hasTexCoords && descriptor.texCoordFormat !in acceptedTexCoordFormats ->
            "unsupported.vertices.attribute_format"
        descriptor.hasTexCoords && descriptor.materialLocalCoordinatePolicy != "texcoord" ->
            "unsupported.vertices.local_coords_unproven"
        primitiveBlendPlan()?.destinationReadRequirement ==
            GPUBlendDestinationReadRequirement.DestinationTextureRequired ->
            "unsupported.vertices.primitive_blend_destination_read"
        descriptor.primitiveBlendMode != "none" && primitiveBlendPlan() == null ->
            "unsupported.vertices.primitive_blender_unregistered"
        adapterEvidenceLabel.isNullOrBlank() || wgslLayoutEvidenceLabel.isNullOrBlank() ->
            "unsupported.vertices.wgsl_abi_unvalidated"
        else -> null
    }

private fun GPUVerticesRouteDecisionRequest.refusalFacts(reasonCode: String): Map<String, String> =
    linkedMapOf(
        "reason" to reasonCode,
        "mode" to descriptor.primitiveMode.sourceLabel,
        "vertexCount" to descriptor.vertexCount.toString(),
        "indexCount" to (descriptor.indexCount?.toString() ?: "none"),
        "positionFormat" to descriptor.positionFormat,
        "colorFormat" to (descriptor.colorFormat ?: "none"),
        "texCoordFormat" to (descriptor.texCoordFormat ?: "none"),
        "primitiveBlend" to descriptor.primitiveBlendMode,
        "primitiveBlendDestinationRead" to
            (primitiveBlendPlan()?.destinationReadRequirement ==
                GPUBlendDestinationReadRequirement.DestinationTextureRequired).toString(),
        "localCoords" to descriptor.materialLocalCoordinatePolicy,
        "sourceMutable" to descriptor.sourceMutable.toString(),
        "finitePositions" to descriptor.finitePositions.toString(),
        "adapterEvidence" to (adapterEvidenceLabel ?: "missing"),
        "wgslEvidence" to (wgslLayoutEvidenceLabel ?: "missing"),
    )

private fun GPUVerticesRouteDecisionRequest.primitiveBlendPlan(): GPUBlendPlan? {
    if (descriptor.primitiveBlendMode == "none") return null
    val normalizedLabel = descriptor.primitiveBlendMode
        .replace('-', '_')
        .replace(' ', '_')
        .lowercase()
    val mode = GPUBlendMode.entries.firstOrNull { candidate ->
        candidate.gpuLabel == normalizedLabel ||
            candidate.gpuLabel.replace("_", "") == normalizedLabel.replace("_", "")
    } ?: return null
    return GPUBlendPlanner().plan(
        GPUBlendSpecializationRequest(
            mode = mode,
            coverage = GPUCoverageConsumption.FullOrScissor,
            sourceAlpha = GPUSourceAlphaClassification.Translucent,
            target = GPUTargetBlendFacts(
                formatClass = targetFormatClass,
                clampsNormalizedColorWrites = targetFormatClass.endsWith("unorm"),
                premultipliedAlpha = true,
            ),
            samplePlan = GPUSamplePlan.SingleSampleFrame,
        ),
    )
}

private fun GPUVerticesBufferPlanRequest.refusalCode(
    vertexBytes: Long,
    indexBytes: Long,
    totalUploadBytes: Long,
    indexElementBytes: Long?,
): String? =
    when {
        routeDecision.routeKind != "GPUNative" || routeDecision.diagnostics.any { diagnostic -> diagnostic.terminal } ->
            "unsupported.vertices.route_decision_required"
        routeDecision.descriptor.indexCount != null && indexElementBytes == null ->
            "unsupported.vertices.index_format"
        routeDecision.descriptor.indexCount != null && sourceIndexContentHash.isNullOrBlank() ->
            "unsupported.vertices.index_payload_missing"
        routeDecision.descriptor.indexCount != null && maxIndex >= routeDecision.descriptor.vertexCount ->
            "unsupported.vertices.index_out_of_range"
        !uploadBeforeDraw -> "unsupported.vertices.upload_unavailable"
        !availableUsageFlags.containsAll(requiredUsageFlags) -> "unsupported.vertices.upload_unavailable"
        vertexBytes > maxVertexBufferBytes || indexBytes > maxIndexBufferBytes ->
            "unsupported.vertices.buffer_budget_exceeded"
        totalUploadBytes > maxTotalUploadBytes -> "unsupported.payload.upload_budget_exceeded"
        observedDeviceGeneration != deviceGeneration || observedBufferGeneration != bufferGeneration ->
            "unsupported.payload.resource_stale_generation"
        liveResourceHandleExposed -> "unsupported.vertices.resource_handle_leak"
        else -> null
    }

private fun GPUVerticesBufferPlanRequest.refusalFacts(
    reasonCode: String,
    vertexBytes: Long,
    indexBytes: Long,
    totalUploadBytes: Long,
    indexElementBytes: Long?,
): Map<String, String> =
    linkedMapOf(
        "reason" to reasonCode,
        "routeDecisionKind" to routeDecision.routeKind,
        "vertexBytes" to vertexBytes.toString(),
        "indexBytes" to indexBytes.toString(),
        "totalUploadBytes" to totalUploadBytes.toString(),
        "indexFormat" to sourceIndexType,
        "indexElementBytes" to (indexElementBytes?.toString() ?: "unsupported"),
        "minIndex" to minIndex.toString(),
        "maxIndex" to maxIndex.toString(),
        "vertexCount" to routeDecision.descriptor.vertexCount.toString(),
        "indexCount" to (routeDecision.descriptor.indexCount?.toString() ?: "none"),
        "uploadBeforeDraw" to uploadBeforeDraw.toString(),
        "requiredUsageFlags" to requiredUsageFlags.sorted().joinToString(","),
        "availableUsageFlags" to availableUsageFlags.sorted().joinToString(","),
        "deviceGeneration" to deviceGeneration.toString(),
        "observedDeviceGeneration" to observedDeviceGeneration.toString(),
        "bufferGeneration" to bufferGeneration.toString(),
        "observedBufferGeneration" to observedBufferGeneration.toString(),
        "liveResourceHandleExposed" to liveResourceHandleExposed.toString(),
        "budgetPolicy" to budgetPolicyId,
    )

private fun GPUVerticesBatchingRequest.refusalCodeAndFacts(): Pair<String, Map<String, String>>? {
    if (invocations.isEmpty()) {
        return "unsupported.vertices.batch_empty" to linkedMapOf(
            "reason" to "unsupported.vertices.batch_empty",
            "scope" to scopeId,
            "invocationCount" to "0",
        )
    }

    invocations.forEach { invocation ->
        if (invocation.routeDecision.routeKind != "GPUNative" ||
            invocation.routeDecision.diagnostics.any { diagnostic -> diagnostic.terminal }
        ) {
            return "unsupported.vertices.batch_route_required" to linkedMapOf(
                "reason" to "unsupported.vertices.batch_route_required",
                "invocation" to invocation.invocationId,
                "routeKind" to invocation.routeDecision.routeKind,
            )
        }
        if (invocation.bufferPlan.routeKind != "CPUPreparedGPU" ||
            invocation.bufferPlan.diagnostics.any { diagnostic -> diagnostic.terminal }
        ) {
            return "unsupported.vertices.batch_buffer_plan_required" to linkedMapOf(
                "reason" to "unsupported.vertices.batch_buffer_plan_required",
                "invocation" to invocation.invocationId,
                "bufferRouteKind" to invocation.bufferPlan.routeKind,
            )
        }
    }

    invocations.zipWithNext().forEach { (previous, next) ->
        if (next.paintOrder < previous.paintOrder) {
            return "unsupported.vertices.batch_order_ambiguous" to linkedMapOf(
                "reason" to "unsupported.vertices.batch_order_ambiguous",
                "previousInvocation" to previous.invocationId,
                "nextInvocation" to next.invocationId,
                "previousPaintOrder" to previous.paintOrder.toString(),
                "nextPaintOrder" to next.paintOrder.toString(),
            )
        }
    }

    return null
}

private fun GPUVerticesBatchInvocation.splitReason(next: GPUVerticesBatchInvocation): String? =
    when {
        sortWindowId != next.sortWindowId -> "planner.stop.sort_window"
        topology != next.topology -> "planner.stop.topology"
        renderStepLabel != next.renderStepLabel -> "planner.stop.render_step"
        pipelineKeyHash != next.pipelineKeyHash || layoutHash != next.layoutHash -> "planner.stop.pipeline_key"
        materialKeyHash != next.materialKeyHash -> "planner.stop.material_key"
        blendClass != next.blendClass -> "planner.stop.blend_class"
        clipKey != next.clipKey -> "planner.stop.clip_boundary"
        layerId != next.layerId || orderBand != next.orderBand -> "planner.stop.layer_boundary"
        barrierGeneration != next.barrierGeneration -> "planner.stop.barrier"
        uploadGeneration != next.uploadGeneration -> "planner.stop.upload_generation"
        destinationReadClass != next.destinationReadClass || next.destinationReadClass != "none" ->
            "planner.stop.destination_read"
        overlapClass != "CompatibleOverlap" || next.overlapClass != "CompatibleOverlap" ->
            "planner.stop.incompatible_overlap"
        else -> null
    }

private fun List<GPUVerticesBatchInvocation>.toVerticesBatch(): GPUVerticesBatch {
    val first = first()
    return GPUVerticesBatch(
        batchKeyHash = verticesStableHash(
            listOf(
                "vertices-batch-key-v1",
                first.topology,
                first.renderStepLabel,
                first.layerId,
                first.orderBand,
                first.sortWindowId,
                first.pipelineKeyHash,
                first.materialKeyHash,
                first.layoutHash,
                first.clipKey,
                first.blendClass,
                first.destinationReadClass,
                first.barrierGeneration.toString(),
                first.uploadGeneration.toString(),
            ),
        ),
        invocationIds = map { invocation -> invocation.invocationId },
        layerId = first.layerId,
        orderBand = first.orderBand,
        sortWindowId = first.sortWindowId,
        pipelineKeyHash = first.pipelineKeyHash,
        materialKeyHash = first.materialKeyHash,
        layoutHash = first.layoutHash,
        clipKey = first.clipKey,
        destinationReadClass = first.destinationReadClass,
        barrierGeneration = first.barrierGeneration,
        uploadGeneration = first.uploadGeneration,
    )
}

private fun verticesBufferArtifactKey(
    request: GPUVerticesBufferPlanRequest,
    routeDecisionHash: String,
): String =
    verticesStableHash(
        listOf(
            "vertices-precomputed-buffer-artifact-v1",
            request.routeDecision.descriptorHash,
            routeDecisionHash,
            request.routeDecision.descriptor.descriptorVersion.toString(),
            request.routeDecision.descriptor.primitiveMode.sourceLabel,
            request.routeDecision.descriptor.vertexCount.toString(),
            request.routeDecision.descriptor.indexCount?.toString() ?: "none",
            request.sourceVertexContentHash,
            request.sourceIndexContentHash ?: "none",
            request.sourceIndexType,
            request.routeDecision.descriptor.primitiveBlendMode,
            request.budgetPolicyId,
        ),
    ).let { hash -> "prepared.vertices.${hash.removePrefix("sha256:")}" }

private fun String.indexElementBytes(): Long? =
    when (this) {
        "uint16" -> 2L
        "uint32" -> 4L
        else -> null
    }

private fun GPUVerticesDescriptor.layoutPlan(): GPUVertexLayoutPlan {
    val attributes = mutableListOf("position")
    val offsets = linkedMapOf("position" to 0)
    val locations = linkedMapOf("position" to 0)
    var stride = 8

    if (hasColors) {
        attributes += "color"
        offsets["color"] = stride
        locations["color"] = 1
        stride += 4
    }

    if (hasTexCoords) {
        attributes += "texcoord"
        offsets["texcoord"] = stride
        locations["texcoord"] = 2
        stride += 8
    }

    return GPUVertexLayoutPlan(
        attributes = attributes,
        strideBytes = stride,
        offsets = offsets,
        shaderLocations = locations,
    )
}

private fun GPUVerticesDescriptor.variantLabel(): String =
    when {
        hasColors && hasTexCoords -> "color-texcoord"
        hasColors -> "color"
        hasTexCoords -> "texcoord"
        else -> "position-only"
    }

private fun GPUVerticesDescriptor.materialKeyFacts(): List<String> =
    listOf(
        "localCoords=$materialLocalCoordinatePolicy",
        "primitiveBlend=$primitiveBlendMode",
        "primitiveColor=$hasColors",
    )

private fun GPUVerticesRouteDecisionRequest.pipelineKeyFacts(layoutHash: String): List<String> =
    listOf(
        "layout=$layoutHash",
        "mode=${descriptor.primitiveMode.sourceLabel}",
        "primitiveColor=${descriptor.hasColors}",
        "target=$targetFormatClass",
        "texcoord=${descriptor.hasTexCoords}",
    )

private fun GPUVertexLayoutPlan.dumpAttributes(): String =
    attributes.joinToString(",") { attribute ->
        val format = when (attribute) {
            "position" -> "f32x2"
            "color" -> "rgba8unorm"
            "texcoord" -> "f32x2"
            else -> "unknown"
        }
        "$attribute:$format@${offsets.getValue(attribute)}:${shaderLocations.getValue(attribute)}"
    }

private fun GPUVertexLayoutPlan.dumpLocations(): String =
    attributes.joinToString(",") { attribute -> "$attribute:${shaderLocations.getValue(attribute)}" }

private fun Map<String, String>.dumpVerticesFacts(): String =
    entries.sortedBy { entry -> entry.key }
        .joinToString(",") { entry -> "${entry.key}=${entry.value}" }

private fun verticesDescriptorHash(descriptor: GPUVerticesDescriptor): String =
    verticesStableHash(
        listOf(
            "vertices-descriptor-v1",
            descriptor.descriptorVersion.toString(),
            descriptor.primitiveMode.sourceLabel,
            descriptor.vertexCount.toString(),
            descriptor.indexCount?.toString() ?: "none",
            descriptor.hasColors.toString(),
            descriptor.hasTexCoords.toString(),
            descriptor.sourceKey,
            descriptor.positionFormat,
            descriptor.colorFormat ?: "none",
            descriptor.texCoordFormat ?: "none",
            descriptor.primitiveBlendMode,
            descriptor.materialLocalCoordinatePolicy,
            descriptor.finitePositions.toString(),
        ),
    )

private fun verticesLayoutHash(layout: GPUVertexLayoutPlan, descriptor: GPUVerticesDescriptor): String =
    verticesStableHash(
        listOf(
            "vertices-layout-v1",
            descriptor.primitiveMode.sourceLabel,
            layout.attributes.joinToString(","),
            layout.strideBytes.toString(),
            layout.offsets.entries.joinToString(",") { entry -> "${entry.key}:${entry.value}" },
            layout.shaderLocations.entries.joinToString(",") { entry -> "${entry.key}:${entry.value}" },
        ),
    )

private fun GPUVertexMode.renderStepModeLabel(): String =
    sourceLabel.replace(Regex("([a-z])([A-Z])"), "$1-$2").lowercase()

private fun verticesStableHash(parts: List<String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = parts.joinToString(separator = "\u001F").toByteArray()
    return "sha256:" + digest.digest(bytes)
        .take(8)
        .joinToString("") { byte -> "%02x".format(byte) }
}
