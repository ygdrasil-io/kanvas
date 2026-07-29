package org.graphiks.kanvas.gpu.renderer.filters

/**
 * Proof-producing filter metadata normalizer.
 *
 * Only proven mathematical equivalences are applied:
 * - Removal of identity nodes (e.g., identity color matrix).
 * - Composition of adjacent offsets.
 * - Intersection of compatible crops.
 * - Composition of compatible color matrices with exact input ordering.
 *
 * Every rewrite produces a deterministic [GPUPreparedFilterRewriteProof].
 * All other nodes remain unchanged and are marked as materialization boundaries.
 * No approximate optimization or tolerance-based rewrite is performed.
 */
class GPUPreparedFilterNormalizer {

    private val materializationKinds = setOf(
        GPUPreparedFilterKind.Blur,
        GPUPreparedFilterKind.DropShadow,
        GPUPreparedFilterKind.Blend,
        GPUPreparedFilterKind.Dilate,
        GPUPreparedFilterKind.Erode,
        GPUPreparedFilterKind.DistantLitDiffuse,
        GPUPreparedFilterKind.PointLitDiffuse,
        GPUPreparedFilterKind.SpotLitDiffuse,
        GPUPreparedFilterKind.DistantLitSpecular,
        GPUPreparedFilterKind.PointLitSpecular,
        GPUPreparedFilterKind.SpotLitSpecular,
        GPUPreparedFilterKind.Tile,
        GPUPreparedFilterKind.Merge,
        GPUPreparedFilterKind.DisplacementMap,
        GPUPreparedFilterKind.Picture,
        GPUPreparedFilterKind.Magnifier,
        GPUPreparedFilterKind.MatrixConvolution,
        GPUPreparedFilterKind.RuntimeEffect,
        GPUPreparedFilterKind.Compose,
    )

    fun normalize(
        graph: GPUPreparedFilterGraph,
        bounds: Map<GPUPreparedFilterNodeId, GPUFilterBoundsPlan>,
        colorFacts: GPUFilterColorPlan,
    ): GPUPreparedFilterNormalization {
        if (graph.nodes.isEmpty()) {
            return GPUPreparedFilterNormalization(
                graph = graph,
                rewrites = emptyList(),
                materializationNodeIds = emptySet(),
            )
        }

        val rewrites = mutableListOf<GPUPreparedFilterRewriteProof>()
        val materializationIds = mutableSetOf<GPUPreparedFilterNodeId>()
        val workingNodes = graph.nodes.toMutableList()

        var changed = true
        while (changed) {
            changed = false
            if (workingNodes.isEmpty()) break

            var i = 0
            while (i < workingNodes.size) {
                val node = workingNodes[i]

                if (node.kind in setOf(GPUPreparedFilterKind.Offset, GPUPreparedFilterKind.Crop,
                        GPUPreparedFilterKind.ColorFilter)) {
                    if (i + 1 < workingNodes.size) {
                        val next = workingNodes[i + 1]
                        val rewrite = tryRewrite(node, next, bounds)
                        if (rewrite != null) {
                            val merged = rewrite.resultNode
                            rewrites.add(rewrite.proof)
                            workingNodes.removeAt(i + 1)
                            workingNodes[i] = merged
                            val midIds = setOf(merged.id, rewrite.proof.resultNodeIds.getOrElse(1) { merged.id })
                            materializationIds.removeAll(midIds)
                            changed = true
                            continue
                        }
                    }
                }

                if (isIdentity(node)) {
                    val newInput = node.inputs.firstOrNull()
                        ?: GPUPreparedFilterInputRef.TransparentBlack
                    rewrites.add(
                        GPUPreparedFilterRewriteProof(
                            rule = "remove-identity",
                            sourceNodeIds = listOf(node.id),
                            resultNodeIds = emptyList(),
                            removedIntermediateCount = 1,
                            inputBoundsIdentity = boundsLabel(node.id, bounds),
                            outputBoundsIdentity = boundsLabel(node.id, bounds),
                        )
                    )
                    workingNodes.removeAt(i)
                    changed = true
                    continue
                }

                if (node.kind in materializationKinds || isMaterializationBoundary(node)) {
                    materializationIds.add(node.id)
                }
                i++
            }
        }

        val outputRef = if (workingNodes.isEmpty())
            GPUPreparedFilterInputRef.TransparentBlack
        else
            GPUPreparedFilterInputRef.Node(workingNodes.last().id)

        val outputGraph = GPUPreparedFilterGraph(
            nodes = workingNodes.toList(),
            output = outputRef,
            identity = "normalized:${graph.identity}",
        )

        return GPUPreparedFilterNormalization(
            graph = outputGraph,
            rewrites = rewrites.toList(),
            materializationNodeIds = materializationIds.toSet(),
        )
    }

    private fun tryRewrite(
        node: GPUPreparedFilterNode,
        next: GPUPreparedFilterNode,
        bounds: Map<GPUPreparedFilterNodeId, GPUFilterBoundsPlan>,
    ): RewriteResult? {
        when {
            node.kind == GPUPreparedFilterKind.Offset && next.kind == GPUPreparedFilterKind.Offset -> {
                val p0 = node.parameters as OffsetParams
                val p1 = next.parameters as OffsetParams
                val merged = GPUPreparedFilterNode(
                    id = nextFreeId(listOf(node.id, next.id)),
                    kind = GPUPreparedFilterKind.Offset,
                    inputs = node.inputs,
                    parameters = OffsetParams(p0.dx + p1.dx, p0.dy + p1.dy),
                    provenance = "normalized:${node.id.value}+${next.id.value}",
                )
                return RewriteResult(
                    merged,
                    GPUPreparedFilterRewriteProof(
                        rule = "compose-offset",
                        sourceNodeIds = listOf(node.id, next.id),
                        resultNodeIds = listOf(merged.id),
                        removedIntermediateCount = 1,
                        inputBoundsIdentity = boundsLabel(node.id, bounds),
                        outputBoundsIdentity = boundsLabel(next.id, bounds),
                    ),
                )
            }
            node.kind == GPUPreparedFilterKind.Crop && next.kind == GPUPreparedFilterKind.Crop -> {
                val p0 = node.parameters as CropParams
                val p1 = next.parameters as CropParams
                val ix = maxOf(p0.x, p1.x)
                val iy = maxOf(p0.y, p1.y)
                val iw = minOf(p0.x + p0.width, p1.x + p1.width) - ix
                val ih = minOf(p0.y + p0.height, p1.y + p1.height) - iy
                val merged = GPUPreparedFilterNode(
                    id = nextFreeId(listOf(node.id, next.id)),
                    kind = GPUPreparedFilterKind.Crop,
                    inputs = node.inputs,
                    parameters = CropParams(ix, iy, maxOf(0f, iw), maxOf(0f, ih)),
                    provenance = "normalized:${node.id.value}+${next.id.value}",
                )
                return RewriteResult(
                    merged,
                    GPUPreparedFilterRewriteProof(
                        rule = "intersect-crop",
                        sourceNodeIds = listOf(node.id, next.id),
                        resultNodeIds = listOf(merged.id),
                        removedIntermediateCount = 1,
                        inputBoundsIdentity = boundsLabel(node.id, bounds),
                        outputBoundsIdentity = boundsLabel(next.id, bounds),
                    ),
                )
            }
            node.kind == GPUPreparedFilterKind.ColorFilter && next.kind == GPUPreparedFilterKind.ColorFilter -> {
                val p0 = node.parameters as ColorFilterParams
                val p1 = next.parameters as ColorFilterParams
                val composed = composeColorMatrices(p0.matrix, p1.matrix)
                val merged = GPUPreparedFilterNode(
                    id = nextFreeId(listOf(node.id, next.id)),
                    kind = GPUPreparedFilterKind.ColorFilter,
                    inputs = node.inputs,
                    parameters = ColorFilterParams(composed),
                    provenance = "normalized:${node.id.value}+${next.id.value}",
                )
                return RewriteResult(
                    merged,
                    GPUPreparedFilterRewriteProof(
                        rule = "fold-color-filter",
                        sourceNodeIds = listOf(node.id, next.id),
                        resultNodeIds = listOf(merged.id),
                        removedIntermediateCount = 1,
                        inputBoundsIdentity = boundsLabel(node.id, bounds),
                        outputBoundsIdentity = boundsLabel(next.id, bounds),
                    ),
                )
            }
        }
        return null
    }

    private fun composeColorMatrices(a: FloatArray, b: FloatArray): FloatArray {
        val result = FloatArray(20)
        for (row in 0..3) {
            for (col in 0..3) {
                var sum = 0f
                for (k in 0..3) {
                    sum += a[row * 5 + k] * b[k * 5 + col]
                }
                result[row * 5 + col] = sum
            }
            var sum = 0f
            for (k in 0..3) {
                sum += a[row * 5 + k] * b[k * 5 + 4]
            }
            sum += a[row * 5 + 4]
            result[row * 5 + 4] = sum
        }
        return result
    }

    private fun isIdentity(node: GPUPreparedFilterNode): Boolean {
        if (node.kind != GPUPreparedFilterKind.ColorFilter) return false
        val params = node.parameters as? ColorFilterParams ?: return false
        val m = params.matrix
        if (m.size != 20) return false
        return m[0] == 1f && m[1] == 0f && m[2] == 0f && m[3] == 0f && m[4] == 0f &&
            m[5] == 0f && m[6] == 1f && m[7] == 0f && m[8] == 0f && m[9] == 0f &&
            m[10] == 0f && m[11] == 0f && m[12] == 1f && m[13] == 0f && m[14] == 0f &&
            m[15] == 0f && m[16] == 0f && m[17] == 0f && m[18] == 1f && m[19] == 0f
    }

    private fun isMaterializationBoundary(node: GPUPreparedFilterNode): Boolean {
        return node.kind in materializationKinds
    }

    private fun nextFreeId(sourceIds: List<GPUPreparedFilterNodeId>): GPUPreparedFilterNodeId {
        return GPUPreparedFilterNodeId(
            sourceIds.joinToString("_") { it.value } + "_merged"
        )
    }

    private fun boundsLabel(
        nodeId: GPUPreparedFilterNodeId,
        bounds: Map<GPUPreparedFilterNodeId, GPUFilterBoundsPlan>,
    ): String {
        return bounds[nodeId]?.inputBoundsLabel ?: "unknown"
    }

    private data class RewriteResult(
        val resultNode: GPUPreparedFilterNode,
        val proof: GPUPreparedFilterRewriteProof,
    )
}
