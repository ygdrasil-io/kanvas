package org.graphiks.kanvas.gpu.renderer.filters

import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeRefusalCodes

object GPUPreparedFilterDAGPlanner {

    fun plan(normalization: GPUPreparedFilterNormalization): GPUFilterDAGPlan {
        val nodeRoutes = mutableMapOf<GPUPreparedFilterNodeId, GPUFilterNodeRoute>()
        val intermediateTextures = mutableListOf<FilterIntermediatePlan>()
        val executionOrder = mutableListOf<GPUPreparedFilterNodeId>()

        for (node in normalization.graph.nodes) {
            executionOrder.add(node.id)

            if (node.id in normalization.materializationNodeIds) {
                nodeRoutes[node.id] = GPUFilterNodeRoute.NativeRender(
                    GPUFilterRenderNodePlan(
                        renderStepLabel = "filter-${node.kind.name.lowercase()}",
                        pipelineKeyHash = node.canonicalIdentity(),
                        payloadPlanHash = "",
                        bindingPlanHash = "",
                    )
                )
                intermediateTextures.add(
                    FilterIntermediatePlan(node.id, "filter-int-${node.id.value}")
                )
                continue
            }

            if (isFoldable(node)) {
                nodeRoutes[node.id] = GPUFilterNodeRoute.FoldedMaterial(
                    materialKeyHash = node.canonicalIdentity()
                )
                continue
            }

            if (isIdentityColorFilter(node)) {
                nodeRoutes[node.id] = GPUFilterNodeRoute.FoldedMaterial(
                    materialKeyHash = node.canonicalIdentity()
                )
                continue
            }

            nodeRoutes[node.id] = GPUFilterNodeRoute.Refused(
                GPUFilterDiagnostic(
                    code = GPUPreparedCompositeRefusalCodes.NATIVE_CAPABILITY,
                    nodeId = GPUFilterNodeID(node.id.value),
                    message = "Filter kind ${node.kind} not yet supported",
                    terminal = true,
                )
            )
        }

        return GPUFilterDAGPlan(nodeRoutes, intermediateTextures, executionOrder)
    }

    private fun isFoldable(node: GPUPreparedFilterNode): Boolean =
        node.kind in setOf(GPUPreparedFilterKind.Offset, GPUPreparedFilterKind.Crop)

    private fun isIdentityColorFilter(node: GPUPreparedFilterNode): Boolean {
        if (node.kind != GPUPreparedFilterKind.ColorFilter) return false
        val params = node.parameters as ColorFilterParams
        val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        return params.matrix.contentEquals(identity)
    }
}

data class GPUFilterDAGPlan(
    val nodeRoutes: Map<GPUPreparedFilterNodeId, GPUFilterNodeRoute>,
    val intermediateTextures: List<FilterIntermediatePlan>,
    val executionOrder: List<GPUPreparedFilterNodeId>,
)

data class FilterIntermediatePlan(
    val nodeId: GPUPreparedFilterNodeId,
    val textureLabel: String,
)
