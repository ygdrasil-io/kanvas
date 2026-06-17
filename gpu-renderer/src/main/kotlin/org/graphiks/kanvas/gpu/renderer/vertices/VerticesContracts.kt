package org.graphiks.kanvas.gpu.renderer.vertices

import java.security.MessageDigest

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
    val blendModeLabel: String,
    val requiresDestinationRead: Boolean,
)

/** Index buffer plan. */
data class GPUIndexBufferPlan(
    val indexFormat: String,
    val count: Int,
    val validationLabel: String,
)

/** Vertex buffer plan. */
data class GPUVertexBufferPlan(
    val byteCount: Long,
    val layout: GPUVertexLayoutPlan,
    val uploadRequirement: String,
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
    val acceptedPrimitiveBlenders: Set<String> = setOf("none", "SrcOver"),
    val destinationReadPrimitiveBlenders: Set<String> = setOf("Multiply", "Screen"),
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
        require(acceptedPrimitiveBlenders.isNotEmpty()) {
            "GPUVerticesRouteDecisionRequest.acceptedPrimitiveBlenders must not be empty"
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
            productActivation = false,
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

private const val VERTICES_ROUTE_EVIDENCE_ROW = "gpu-renderer.vertices.descriptor"
private const val VERTICES_ROUTE_ACCEPTED_CODE = "accepted.vertices.route_decision"
private const val VERTICES_ROUTE_NONCLAIM_LINE =
    "vertices:nonclaim drawVerticesSupport=false adapterBacked=false " +
        "vertexBufferUpload=false indexBufferUpload=false primitiveBlenderSupport=false " +
        "texcoordMaterialSupport=false meshSupport=false " +
        "productActivation=false cpuRenderedTextureFallback=false"

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
        descriptor.primitiveBlendMode in destinationReadPrimitiveBlenders ->
            "unsupported.vertices.primitive_blend_destination_read"
        descriptor.primitiveBlendMode !in acceptedPrimitiveBlenders ->
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
            (descriptor.primitiveBlendMode in destinationReadPrimitiveBlenders).toString(),
        "localCoords" to descriptor.materialLocalCoordinatePolicy,
        "sourceMutable" to descriptor.sourceMutable.toString(),
        "finitePositions" to descriptor.finitePositions.toString(),
        "adapterEvidence" to (adapterEvidenceLabel ?: "missing"),
        "wgslEvidence" to (wgslLayoutEvidenceLabel ?: "missing"),
    )

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
