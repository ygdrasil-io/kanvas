package org.graphiks.kanvas.gpu.renderer.filters

import java.security.MessageDigest

/** Filter node identifier. */
@JvmInline
value class GPUFilterNodeID(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUFilterNodeID.value must not be blank" }
    }
}

/** Filter ordering token. */
@JvmInline
value class GPUFilterOrderingToken(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUFilterOrderingToken.value must not be blank" }
    }
}

/** Filter graph descriptor. */
data class GPUFilterGraphDescriptor(
    val graphId: String,
    val version: Int,
    val sourceRole: String,
    val nodes: List<GPUFilterNodeDescriptor>,
    val edges: List<String>,
    val coordinateSpaces: List<String>,
    val cropLabel: String? = null,
    val provenance: String,
)

/** Filter node descriptor. */
data class GPUFilterNodeDescriptor(
    val nodeId: GPUFilterNodeID,
    val nodeKind: String,
    val inputLabels: List<String>,
    val parameterHash: String,
)

/** Filter node route. */
sealed interface GPUFilterNodeRoute {
    /** Native render route. */
    data class NativeRender(val renderNode: GPUFilterRenderNodePlan) : GPUFilterNodeRoute

    /** Native compute route. */
    data class NativeCompute(val computeNode: GPUFilterComputeNodePlan) : GPUFilterNodeRoute

    /** Copy-only route. */
    data class NativeCopy(val copyLabel: String) : GPUFilterNodeRoute

    /** CPU-prepared artifact route. */
    data class CPUPreparedArtifact(val artifactKey: String) : GPUFilterNodeRoute

    /** Folded material route. */
    data class FoldedMaterial(val materialKeyHash: String) : GPUFilterNodeRoute

    /** Reference-only route for evidence. */
    data class ReferenceOnly(val oracleLabel: String) : GPUFilterNodeRoute

    /** Refused filter route. */
    data class Refused(val diagnostic: GPUFilterDiagnostic) : GPUFilterNodeRoute
}

/** Filter node plan. */
sealed interface GPUFilterNodePlan {
    /** Accepted node plan. */
    data class Accepted(val descriptor: GPUFilterNodeDescriptor, val route: GPUFilterNodeRoute) : GPUFilterNodePlan

    /** Refused node plan. */
    data class Refused(val descriptor: GPUFilterNodeDescriptor, val diagnostic: GPUFilterDiagnostic) : GPUFilterNodePlan
}

/** Filter graph plan. */
data class GPUFilterPlan(
    val graph: GPUFilterGraphDescriptor,
    val nodePlans: List<GPUFilterNodePlan>,
    val intermediates: List<GPUFilterIntermediatePlan>,
    val orderingToken: GPUFilterOrderingToken,
    val diagnostics: List<GPUFilterDiagnostic> = emptyList(),
)

/** Filter input plan. */
data class GPUFilterInputPlan(
    val inputLabel: String,
    val sourcePlan: GPUFilterSourcePlan,
    val sampling: GPUFilterSamplingPlan,
)

/** Filter source plan. */
data class GPUFilterSourcePlan(
    val sourceLabel: String,
    val boundsLabel: String,
    val colorTreatment: String,
)

/** Filter backdrop plan. */
data class GPUFilterBackdropPlan(
    val backdropLabel: String,
    val readBoundsLabel: String,
    val required: Boolean,
)

/** Filter bounds plan. */
data class GPUFilterBoundsPlan(
    val inputBoundsLabel: String,
    val outputBoundsLabel: String,
    val conservative: Boolean,
)

/** Filter crop plan. */
data class GPUFilterCropPlan(
    val cropLabel: String,
    val tilePolicy: GPUFilterTilePlan,
)

/** Filter tile plan. */
data class GPUFilterTilePlan(
    val tileModeX: String,
    val tileModeY: String,
    val decalOutsideCrop: Boolean,
)

/** Filter sampling plan. */
data class GPUFilterSamplingPlan(
    val filterMode: String,
    val mipmapMode: String,
    val coordinateSpaceLabel: String,
)

/** Filter intermediate plan. */
data class GPUFilterIntermediatePlan(
    val intermediateLabel: String,
    val boundsLabel: String,
    val formatClass: String,
    val usageLabels: Set<String>,
    val lifetimeClass: String,
    val ownerLabel: String = "",
    val generation: Long = 0L,
    val byteEstimate: Long = 0L,
    val descriptorHash: String = "",
) {
    val usageLabel: String
        get() = usageLabels.canonicalFilterUsageLabels().joinToString(",")
}

/** Filter render-node plan. */
data class GPUFilterRenderNodePlan(
    val renderStepLabel: String,
    val pipelineKeyHash: String,
    val payloadPlanHash: String,
    val bindingPlanHash: String = "",
)

/** Filter compute-node plan. */
data class GPUFilterComputeNodePlan(
    val programKeyHash: String,
    val dispatchShape: String,
    val bindingPlanHash: String,
)

/** Filter kernel plan. */
data class GPUFilterKernelPlan(
    val kernelHash: String,
    val radiusX: Float,
    val radiusY: Float,
    val separable: Boolean,
)

/** Filter runtime-effect plan. */
data class GPUFilterRuntimeEffectPlan(
    val effectId: String,
    val descriptorVersion: Int,
    val routeLabel: String,
)

/** Filter color plan. */
data class GPUFilterColorPlan(
    val workingColorSpaceLabel: String,
    val outputColorSpaceLabel: String,
    val conversionPolicy: String,
)

/** Filter cache plan. */
data class GPUFilterCachePlan(
    val cacheKey: String,
    val invalidationFacts: List<String>,
    val reusable: Boolean,
)

/** Filter budget policy. */
data class GPUFilterBudgetPolicy(
    val maxIntermediateBytes: Long,
    val maxPassCount: Int,
    val refusalCode: String? = null,
)

/** Bounded facts required by the simple filter render-node gate. */
data class GPUSimpleFilterBounds(
    val inputBoundsLabel: String,
    val outputBoundsLabel: String,
    val conservative: Boolean,
    val finite: Boolean = true,
    val width: Int = 0,
    val height: Int = 0,
)

/** Request for simple single-node filter render-route evidence. */
data class GPUSimpleFilterRenderNodeRequest(
    val label: String = "accepted",
    val graph: GPUFilterGraphDescriptor,
    val source: GPUFilterSourcePlan,
    val bounds: GPUSimpleFilterBounds,
    val crop: GPUFilterCropPlan,
    val sampling: GPUFilterSamplingPlan,
    val targetFormatClass: String,
    val targetGeneration: Long,
    val intermediateUsageLabels: Set<String>,
    val intermediateOwnershipValidated: Boolean = true,
    val activeAttachmentSampled: Boolean = false,
    val readWriteAliasing: Boolean = false,
    val renderNodeBindingValidated: Boolean = true,
    val cpuRenderedTextureFallbackRequested: Boolean = false,
    val maxIntermediateBytes: Long = DEFAULT_SIMPLE_FILTER_INTERMEDIATE_MAX_BYTES,
)

/** Evidence result for the simple filter render-node route gate. */
data class GPUSimpleFilterRenderNodeGatePlan(
    val label: String,
    val evidenceRow: String,
    val routeKind: String,
    val classification: String,
    val promoted: Boolean,
    val productActivation: Boolean,
    val materialized: Boolean,
    val filterPlan: GPUFilterPlan,
    val bounds: GPUSimpleFilterBounds,
    val crop: GPUFilterCropPlan,
    val sampling: GPUFilterSamplingPlan,
    val intermediate: GPUFilterIntermediatePlan,
    val source: GPUFilterSourcePlan,
    val budgetPolicy: GPUFilterBudgetPolicy,
    val budgetClass: String,
    val activeAttachmentSampled: Boolean,
    val readWriteAliasing: Boolean,
    val diagnostics: List<GPUFilterDiagnostic>,
) {
    val graphDescriptorHash: String
        get() = simpleFilterGraphDescriptorHash(filterPlan.graph)

    val intermediateDescriptorHash: String
        get() = intermediate.descriptorHash

    val renderPipelineKeyHash: String
        get() = acceptedRenderNode().pipelineKeyHash

    val payloadPlanHash: String
        get() = acceptedRenderNode().payloadPlanHash

    val bindingPlanHash: String
        get() = acceptedRenderNode().bindingPlanHash

    /** Returns canonical PM/review dump lines for this gate. */
    fun dumpLines(): List<String> {
        val terminalDiagnostic = diagnostics.singleOrNull { it.terminal }
        if (terminalDiagnostic != null) {
            val descriptor = refusedDescriptor()
            return listOf(
                "filter:simple-node.refused row=$evidenceRow routeKind=$routeKind " +
                    "classification=$classification promoted=$promoted productActivation=$productActivation " +
                    "materialized=$materialized graph=${filterPlan.graph.graphId} " +
                    "node=${descriptor.nodeId.value} reason=${terminalDiagnostic.code} label=$label",
                SIMPLE_FILTER_NONCLAIM_LINE,
            )
        }

        val descriptor = acceptedDescriptor()
        val renderNode = acceptedRenderNode()
        val graph = filterPlan.graph
        return listOf(
            "filter:simple-node row=$evidenceRow routeKind=$routeKind classification=$classification " +
                "promoted=$promoted productActivation=$productActivation materialized=$materialized " +
                "graph=${graph.graphId} node=${descriptor.nodeId.value} kind=${descriptor.nodeKind} " +
                "route=GPUNativeRender",
            "filter:graph id=${graph.graphId} version=${graph.version} source=${graph.sourceRole} " +
                "nodes=${graph.nodes.dumpNodeList()} edges=${graph.edges.dumpEdgeList()} " +
                "coordinates=${graph.coordinateSpaces.joinToString(",")} provenance=${graph.provenance} " +
                "descriptor=$graphDescriptorHash",
            "filter:bounds node=${descriptor.nodeId.value} input=${bounds.inputBoundsLabel} " +
                "output=${bounds.outputBoundsLabel} finite=${bounds.finite} conservative=${bounds.conservative} " +
                "crop=${crop.cropLabel} tile=${crop.tilePolicy.tileModeX}/${crop.tilePolicy.tileModeY} " +
                "sampling=${sampling.filterMode}/${sampling.mipmapMode} coord=${sampling.coordinateSpaceLabel}",
            "filter:intermediate label=${intermediate.intermediateLabel} descriptor=$intermediateDescriptorHash " +
                "owner=${intermediate.ownerLabel} generation=${intermediate.generation} " +
                "bounds=${intermediate.boundsLabel} format=${intermediate.formatClass} " +
                "usage=${intermediate.usageLabel} lifetime=${intermediate.lifetimeClass} " +
                "bytes=${intermediate.byteEstimate}",
            "filter:render-node step=${renderNode.renderStepLabel} pipeline=${renderNode.pipelineKeyHash} " +
                "payload=${renderNode.payloadPlanHash} binding=${renderNode.bindingPlanHash}",
            "filter:resource source=${source.sourceLabel} generation=${intermediate.generation} " +
                "readWriteAliasing=$readWriteAliasing activeAttachmentSampled=$activeAttachmentSampled " +
                "budget=$budgetClass intermediateBytes=${intermediate.byteEstimate}",
            "filter:diagnostic code=${diagnostics.single().code} terminal=${diagnostics.single().terminal}",
            SIMPLE_FILTER_NONCLAIM_LINE,
        )
    }

    private fun acceptedDescriptor(): GPUFilterNodeDescriptor =
        (filterPlan.nodePlans.single() as GPUFilterNodePlan.Accepted).descriptor

    private fun acceptedRenderNode(): GPUFilterRenderNodePlan =
        ((filterPlan.nodePlans.single() as GPUFilterNodePlan.Accepted).route as GPUFilterNodeRoute.NativeRender)
            .renderNode

    private fun refusedDescriptor(): GPUFilterNodeDescriptor =
        (filterPlan.nodePlans.single() as GPUFilterNodePlan.Refused).descriptor
}

/** Planner for contract-only simple filter render-node evidence. */
class GPUSimpleFilterRenderNodePlanner {
    /** Plans a bounded single-node filter route or returns a stable refusal. */
    fun plan(request: GPUSimpleFilterRenderNodeRequest): GPUSimpleFilterRenderNodeGatePlan {
        val intermediateBytes = request.bounds.intermediateByteEstimate()
        val refusalCode = request.refusalCode(intermediateBytes)
        if (refusalCode != null) {
            return refusedPlan(request, refusalCode)
        }

        val descriptor = request.graph.nodes.single()
        val usageLabels = request.intermediateUsageLabels.canonicalFilterUsageLabels()
        val intermediateLabel = "filter-intermediate:${descriptor.nodeId.value}"
        val intermediateDescriptorHash = simpleFilterIntermediateDescriptorHash(request, descriptor, usageLabels)
        val intermediate = GPUFilterIntermediatePlan(
            intermediateLabel = intermediateLabel,
            boundsLabel = request.bounds.outputBoundsLabel,
            formatClass = request.targetFormatClass,
            usageLabels = usageLabels.toSet(),
            lifetimeClass = SIMPLE_FILTER_LIFETIME_CLASS,
            ownerLabel = SIMPLE_FILTER_OWNER_LABEL,
            generation = request.targetGeneration,
            byteEstimate = intermediateBytes,
            descriptorHash = intermediateDescriptorHash,
        )
        val renderNode = GPUFilterRenderNodePlan(
            renderStepLabel = SIMPLE_FILTER_RENDER_STEP,
            pipelineKeyHash = simpleFilterRenderPipelineKeyHash(request, descriptor),
            payloadPlanHash = simpleFilterPayloadPlanHash(request, descriptor),
            bindingPlanHash = simpleFilterBindingPlanHash(request, descriptor, intermediate),
        )
        val diagnostic = GPUFilterDiagnostic(
            code = SIMPLE_FILTER_ACCEPTED_CODE,
            nodeId = descriptor.nodeId,
            message = "simple filter render node accepted: ${descriptor.nodeKind}",
            terminal = false,
        )
        val filterPlan = GPUFilterPlan(
            graph = request.graph,
            nodePlans = listOf(
                GPUFilterNodePlan.Accepted(
                    descriptor = descriptor,
                    route = GPUFilterNodeRoute.NativeRender(renderNode),
                ),
            ),
            intermediates = listOf(intermediate),
            orderingToken = GPUFilterOrderingToken("filter-order:${request.graph.graphId}:${request.targetGeneration}"),
            diagnostics = listOf(diagnostic),
        )

        return GPUSimpleFilterRenderNodeGatePlan(
            label = request.label,
            evidenceRow = SIMPLE_FILTER_EVIDENCE_ROW,
            routeKind = "GPUNative",
            classification = "TargetNative",
            promoted = false,
            productActivation = true,
            materialized = false,
            filterPlan = filterPlan,
            bounds = request.bounds,
            crop = request.crop,
            sampling = request.sampling,
            intermediate = intermediate,
            source = request.source,
            budgetPolicy = GPUFilterBudgetPolicy(request.maxIntermediateBytes, maxPassCount = 1),
            budgetClass = SIMPLE_FILTER_BUDGET_CLASS,
            activeAttachmentSampled = request.activeAttachmentSampled,
            readWriteAliasing = request.readWriteAliasing,
            diagnostics = listOf(diagnostic),
        )
    }

    private fun refusedPlan(
        request: GPUSimpleFilterRenderNodeRequest,
        refusalCode: String,
    ): GPUSimpleFilterRenderNodeGatePlan {
        val descriptor = request.graph.nodes.firstOrNull()
            ?: GPUFilterNodeDescriptor(
                nodeId = GPUFilterNodeID("none"),
                nodeKind = "Unknown",
                inputLabels = emptyList(),
                parameterHash = "none",
            )
        val diagnostic = GPUFilterDiagnostic(
            code = refusalCode,
            nodeId = descriptor.nodeId,
            message = "simple filter render node refused: $refusalCode",
            terminal = true,
        )
        val filterPlan = GPUFilterPlan(
            graph = request.graph,
            nodePlans = listOf(GPUFilterNodePlan.Refused(descriptor, diagnostic)),
            intermediates = emptyList(),
            orderingToken = GPUFilterOrderingToken("filter-order:${request.graph.graphId}:${request.targetGeneration}"),
            diagnostics = listOf(diagnostic),
        )

        return GPUSimpleFilterRenderNodeGatePlan(
            label = request.label,
            evidenceRow = SIMPLE_FILTER_EVIDENCE_ROW,
            routeKind = "RefuseDiagnostic",
            classification = "TargetNative",
            promoted = false,
            productActivation = true,
            materialized = false,
            filterPlan = filterPlan,
            bounds = request.bounds,
            crop = request.crop,
            sampling = request.sampling,
            intermediate = GPUFilterIntermediatePlan(
                intermediateLabel = "none",
                boundsLabel = request.bounds.outputBoundsLabel,
                formatClass = request.targetFormatClass,
                usageLabels = emptySet(),
                lifetimeClass = "none",
            ),
            source = request.source,
            budgetPolicy = GPUFilterBudgetPolicy(
                maxIntermediateBytes = request.maxIntermediateBytes,
                maxPassCount = 1,
                refusalCode = refusalCode,
            ),
            budgetClass = "refused",
            activeAttachmentSampled = request.activeAttachmentSampled,
            readWriteAliasing = request.readWriteAliasing,
            diagnostics = listOf(diagnostic),
        )
    }
}

/** Filter intermediate artifact descriptor. */
data class FilterIntermediateArtifact(
    val artifactKey: String,
    val intermediateLabel: String,
    val generation: Long,
    val lifetimeClass: String,
)

/** Filter diagnostic. */
data class GPUFilterDiagnostic(
    val code: String,
    val nodeId: GPUFilterNodeID? = null,
    val message: String,
    val terminal: Boolean,
)

/** Input facts used to build a refusal-only filter DAG matrix. */
data class GPUFilterDagRefusalInput(
    val graph: GPUFilterGraphDescriptor,
    val finiteBounds: Boolean = true,
    val intermediateOwnershipValidated: Boolean = true,
    val hasCycle: Boolean = false,
    val cpuRenderedTextureFallbackRequested: Boolean = false,
    val supportableBoundedNodeKinds: Set<String> = emptySet(),
    val registeredRuntimeEffectIds: Set<String> = emptySet(),
)

/** PM-facing filter DAG matrix row status. */
enum class GPUFilterDagMatrixStatus {
    /** Node can become supportable only when its bounded route has separate evidence. */
    SupportableBounded,
    /** Node or graph variant is refused with a stable diagnostic. */
    Refused,
}

/** One PM-facing support/refusal matrix row for a filter DAG node. */
data class GPUFilterDagMatrixRow(
    val nodeId: GPUFilterNodeID,
    val nodeKind: String,
    val status: GPUFilterDagMatrixStatus,
    val diagnosticCode: String?,
    val nonClaim: String,
)

/** Result of refusal-only filter DAG classification. */
data class GPUFilterDagRefusalReport(
    val graphId: String,
    val rows: List<GPUFilterDagMatrixRow>,
    val diagnostics: List<GPUFilterDiagnostic>,
) {
    /** Refusal-only M5 evidence never promotes filter DAG support by itself. */
    val promotable: Boolean = false
}

/** Builds a refusal-only filter DAG matrix without promoting filter execution support. */
object GPUFilterDagRefusalMatrix {
    /** Evaluates graph and node facts into stable refusal diagnostics. */
    fun evaluate(input: GPUFilterDagRefusalInput): GPUFilterDagRefusalReport {
        val graphDiagnostics = buildGraphDiagnostics(input)
        val rows = input.graph.nodes.map { descriptor ->
            rowFor(descriptor, input, graphDiagnostics.isNotEmpty())
        }

        return GPUFilterDagRefusalReport(
            graphId = input.graph.graphId,
            rows = rows,
            diagnostics = graphDiagnostics + rows.mapNotNull { row ->
                row.diagnosticCode?.let { code ->
                    GPUFilterDiagnostic(
                        code = code,
                        nodeId = row.nodeId,
                        message = row.nonClaim,
                        terminal = true,
                    )
                }
            },
        )
    }

    private fun buildGraphDiagnostics(input: GPUFilterDagRefusalInput): List<GPUFilterDiagnostic> =
        buildList {
            if (input.hasCycle) {
                add(graphDiagnostic("unsupported.filter.graph_cycle", input.graph.graphId))
            }
            if (!input.finiteBounds) {
                add(graphDiagnostic("unsupported.filter.bounds_unbounded", input.graph.graphId))
            }
            if (!input.intermediateOwnershipValidated) {
                add(graphDiagnostic("unsupported.filter.intermediate_unvalidated", input.graph.graphId))
            }
            if (input.cpuRenderedTextureFallbackRequested) {
                add(graphDiagnostic("unsupported.filter.cpu_rendered_texture_forbidden", input.graph.graphId))
            }
        }

    private fun graphDiagnostic(code: String, graphId: String): GPUFilterDiagnostic =
        GPUFilterDiagnostic(
            code = code,
            message = "Filter graph $graphId is refused by the DAG refusal matrix.",
            terminal = true,
        )

    private fun rowFor(
        descriptor: GPUFilterNodeDescriptor,
        input: GPUFilterDagRefusalInput,
        graphRefused: Boolean,
    ): GPUFilterDagMatrixRow {
        val diagnosticCode = when {
            graphRefused -> null
            descriptor.nodeKind == "Picture" -> "unsupported.filter.picture_unbounded"
            descriptor.nodeKind == "RuntimeShader" &&
                descriptor.parameterHash !in input.registeredRuntimeEffectIds ->
                "unsupported.filter.runtime_effect_unregistered"
            descriptor.nodeKind !in input.supportableBoundedNodeKinds ->
                "unsupported.filter.node_unimplemented"
            else -> null
        }
        val status = if (diagnosticCode == null && !graphRefused) {
            GPUFilterDagMatrixStatus.SupportableBounded
        } else {
            GPUFilterDagMatrixStatus.Refused
        }

        return GPUFilterDagMatrixRow(
            nodeId = descriptor.nodeId,
            nodeKind = descriptor.nodeKind,
            status = status,
            diagnosticCode = diagnosticCode,
            nonClaim = nonClaimFor(descriptor.nodeKind, diagnosticCode, graphRefused),
        )
    }

    private fun nonClaimFor(nodeKind: String, diagnosticCode: String?, graphRefused: Boolean): String =
        when {
            graphRefused -> "Graph-level bounds, topology, fallback, or intermediate ownership blocks promotion."
            diagnosticCode == null -> "Supportable bounded row only; no filter DAG route is promoted by this matrix."
            else -> "$nodeKind remains refused by $diagnosticCode."
        }
}

private const val DEFAULT_SIMPLE_FILTER_INTERMEDIATE_MAX_BYTES = 16L * 1024L * 1024L
private const val SIMPLE_FILTER_EVIDENCE_ROW = "gpu-renderer.filter.simple-node"
private const val SIMPLE_FILTER_ACCEPTED_CODE = "accepted.filter.simple_node"
private const val SIMPLE_FILTER_BYTES_PER_PIXEL = 4L
private const val SIMPLE_FILTER_RENDER_STEP = "filter-render:colorfilter"
private const val SIMPLE_FILTER_OWNER_LABEL = "GPURecorderScope"
private const val SIMPLE_FILTER_LIFETIME_CLASS = "layer-local"
private const val SIMPLE_FILTER_BUDGET_CLASS = "filter-small"
private const val SIMPLE_FILTER_NONCLAIM_LINE =
    "filter:nonclaim nativeFilter=false adapterBacked=false arbitraryFilterDag=false runtimeEffectFilter=false " +
        "cpuRenderedFilterTextureFallback=false productActivation=true"

private val SIMPLE_FILTER_REQUIRED_INTERMEDIATE_USAGE_LABELS = setOf("render_attachment", "texture_binding")
private val FILTER_USAGE_ORDER = listOf(
    "render_attachment",
    "copy_src",
    "copy_dst",
    "texture_binding",
    "storage_binding",
)

private fun GPUSimpleFilterRenderNodeRequest.refusalCode(intermediateBytes: Long): String? =
    when {
        !bounds.finite -> "unsupported.filter.bounds_unbounded"
        bounds.width <= 0 || bounds.height <= 0 -> "unsupported.filter.bounds_invalid"
        graph.nodes.size != 1 || graph.edges.isNotEmpty() -> "unsupported.filter.graph_node_limit"
        graph.nodes.single().nodeKind !in setOf("ColorFilter", "GaussianBlur") -> "unsupported.filter.node_unimplemented"
        !intermediateOwnershipValidated ||
            (SIMPLE_FILTER_REQUIRED_INTERMEDIATE_USAGE_LABELS - intermediateUsageLabels).isNotEmpty() ->
            "unsupported.filter.intermediate_unvalidated"
        activeAttachmentSampled || readWriteAliasing -> "unsupported.filter.read_write_aliasing"
        !renderNodeBindingValidated -> "unsupported.filter.node_descriptor_invalid"
        cpuRenderedTextureFallbackRequested -> "unsupported.filter.cpu_rendered_texture_forbidden"
        intermediateBytes > maxIntermediateBytes -> "unsupported.filter.intermediate_budget_exceeded"
        else -> null
    }

private fun GPUSimpleFilterBounds.intermediateByteEstimate(): Long =
    if (width <= 0 || height <= 0) {
        0L
    } else {
        val pixelCount = runCatching { Math.multiplyExact(width.toLong(), height.toLong()) }
            .getOrDefault(Long.MAX_VALUE / SIMPLE_FILTER_BYTES_PER_PIXEL + 1L)
        runCatching { Math.multiplyExact(pixelCount, SIMPLE_FILTER_BYTES_PER_PIXEL) }
            .getOrDefault(Long.MAX_VALUE)
    }

private fun Set<String>.canonicalFilterUsageLabels(): List<String> =
    sortedWith(
        compareBy(
            { FILTER_USAGE_ORDER.indexOf(it).let { index -> if (index < 0) Int.MAX_VALUE else index } },
            { it },
        ),
    )

private fun simpleFilterGraphDescriptorHash(graph: GPUFilterGraphDescriptor): String =
    "sha256:" + simpleFilterStableHash(
        listOf(
            "filter-graph-descriptor-v1",
            graph.graphId,
            graph.version.toString(),
            graph.sourceRole,
            graph.nodes.joinToString(",") { descriptor ->
                listOf(
                    descriptor.nodeId.value,
                    descriptor.nodeKind,
                    descriptor.inputLabels.joinToString("+"),
                    descriptor.parameterHash,
                ).joinToString(":")
            },
            graph.edges.dumpEdgeList(),
            graph.coordinateSpaces.joinToString(","),
            graph.cropLabel ?: "none",
            graph.provenance,
        ),
    )

private fun simpleFilterIntermediateDescriptorHash(
    request: GPUSimpleFilterRenderNodeRequest,
    descriptor: GPUFilterNodeDescriptor,
    usageLabels: List<String>,
): String = "sha256:" + simpleFilterStableHash(
    listOf(
        "filter-intermediate-texture-v1",
        request.graph.graphId,
        descriptor.nodeId.value,
        request.bounds.outputBoundsLabel,
        request.bounds.width.toString(),
        request.bounds.height.toString(),
        request.targetFormatClass,
        usageLabels.joinToString(","),
        "owner=$SIMPLE_FILTER_OWNER_LABEL",
        "generation=${request.targetGeneration}",
        "lifetime=$SIMPLE_FILTER_LIFETIME_CLASS",
    ),
)

private fun simpleFilterRenderPipelineKeyHash(
    request: GPUSimpleFilterRenderNodeRequest,
    descriptor: GPUFilterNodeDescriptor,
): String = "sha256:" + simpleFilterStableHash(
    listOf(
        "filter-render-pipeline-v1",
        SIMPLE_FILTER_RENDER_STEP,
        descriptor.nodeKind,
        request.targetFormatClass,
        "tile=${request.crop.tilePolicy.tileModeX}/${request.crop.tilePolicy.tileModeY}",
        "sampling=${request.sampling.filterMode}/${request.sampling.mipmapMode}",
        "coord=${request.sampling.coordinateSpaceLabel}",
    ),
)

private fun simpleFilterPayloadPlanHash(
    request: GPUSimpleFilterRenderNodeRequest,
    descriptor: GPUFilterNodeDescriptor,
): String = "sha256:" + simpleFilterStableHash(
    listOf(
        "filter-render-payload-v1",
        descriptor.nodeId.value,
        descriptor.parameterHash,
        request.bounds.inputBoundsLabel,
        request.bounds.outputBoundsLabel,
        request.crop.cropLabel,
    ),
)

private fun simpleFilterBindingPlanHash(
    request: GPUSimpleFilterRenderNodeRequest,
    descriptor: GPUFilterNodeDescriptor,
    intermediate: GPUFilterIntermediatePlan,
): String = "sha256:" + simpleFilterStableHash(
    listOf(
        "filter-render-binding-v1",
        descriptor.nodeId.value,
        descriptor.inputLabels.joinToString(","),
        "source=${request.source.sourceLabel}",
        "intermediate=${intermediate.intermediateLabel}",
        "generation=${request.targetGeneration}",
    ),
)

private fun List<GPUFilterNodeDescriptor>.dumpNodeList(): String =
    if (isEmpty()) {
        "none"
    } else {
        joinToString(",") { descriptor -> descriptor.nodeId.value }
    }

private fun List<String>.dumpEdgeList(): String =
    if (isEmpty()) {
        "none"
    } else {
        joinToString(",")
    }

private fun simpleFilterStableHash(parts: List<String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = parts.joinToString(separator = "\u001F").toByteArray()
    return digest.digest(bytes)
        .take(8)
        .joinToString("") { byte -> "%02x".format(byte) }
}
