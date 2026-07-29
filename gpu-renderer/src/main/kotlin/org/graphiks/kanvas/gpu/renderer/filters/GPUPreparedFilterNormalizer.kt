package org.graphiks.kanvas.gpu.renderer.filters

class GPUPreparedFilterNormalizer {

    private val materializationKinds = setOf(
        GPUPreparedFilterKind.Blur, GPUPreparedFilterKind.DropShadow,
        GPUPreparedFilterKind.Blend, GPUPreparedFilterKind.Dilate, GPUPreparedFilterKind.Erode,
        GPUPreparedFilterKind.DistantLitDiffuse, GPUPreparedFilterKind.PointLitDiffuse,
        GPUPreparedFilterKind.SpotLitDiffuse, GPUPreparedFilterKind.DistantLitSpecular,
        GPUPreparedFilterKind.PointLitSpecular, GPUPreparedFilterKind.SpotLitSpecular,
        GPUPreparedFilterKind.Tile, GPUPreparedFilterKind.Merge,
        GPUPreparedFilterKind.DisplacementMap, GPUPreparedFilterKind.Picture,
        GPUPreparedFilterKind.Magnifier, GPUPreparedFilterKind.MatrixConvolution,
        GPUPreparedFilterKind.RuntimeEffect, GPUPreparedFilterKind.Compose,
    )

    fun normalize(
        graph: GPUPreparedFilterGraph,
        bounds: Map<GPUPreparedFilterNodeId, GPUFilterBoundsPlan>,
        colorFacts: GPUFilterColorPlan,
    ): GPUPreparedFilterNormalization {
        val rewrites = mutableListOf<GPUPreparedFilterRewriteProof>()
        val materializationIds = mutableSetOf<GPUPreparedFilterNodeId>()
        var currentNodes = graph.nodes.toMutableList()
        var currentEdges = buildEdgeMap(currentNodes)
        val allReplacements = mutableMapOf<GPUPreparedFilterNodeId, GPUPreparedFilterInputRef>()

        var changed = true
        while (changed) {
            changed = false
            val newNodes = mutableListOf<GPUPreparedFilterNode>()
            val removedIds = mutableSetOf<GPUPreparedFilterNodeId>()
            val replacements = mutableMapOf<GPUPreparedFilterNodeId, GPUPreparedFilterInputRef>()

            for (node in currentNodes) {
                if (node.id in removedIds) continue

                val rewrittenNode = tryRemoveIdentity(node, rewrites, bounds)
                if (rewrittenNode != null) {
                    val replacementInput = rewrittenNode.inputs.firstOrNull()
                        ?: GPUPreparedFilterInputRef.TransparentBlack
                    replacements[node.id] = replacementInput
                    removedIds.add(node.id)
                    allReplacements.putAll(replacements)
                    changed = true
                    continue
                }

                val consumerIds = currentEdges[node.id] ?: emptySet()
                val consumerCount = consumerIds.size

                if (consumerCount == 1) {
                    val consumerId = consumerIds.first()
                    val consumerNode = currentNodes.find { it.id == consumerId } ?: continue

                    val rewrite = tryRewrite(node, consumerNode, bounds, colorFacts)
                    if (rewrite != null) {
                        rewrites.add(rewrite.proof)
                        replacements[consumerId] = GPUPreparedFilterInputRef.Node(rewrite.resultNode.id)
                        removedIds.add(node.id)
                        removedIds.add(consumerId)
                        newNodes.add(rewrite.resultNode)
                        changed = true
                        continue
                    }
                }

                if (isMaterialization(node)) materializationIds.add(node.id)
                newNodes.add(remapInputs(node, replacements))
            }

            if (changed) {
                currentNodes = newNodes
                currentEdges = buildEdgeMap(currentNodes)
            }
        }

        val output = if (currentNodes.isEmpty()) {
            if (graph.output is GPUPreparedFilterInputRef.Node) {
                allReplacements[graph.output.id] ?: GPUPreparedFilterInputRef.TransparentBlack
            } else graph.output
        } else if (graph.output is GPUPreparedFilterInputRef.Node) {
            val outId = (graph.output as GPUPreparedFilterInputRef.Node).id
            if (currentNodes.any { it.id == outId }) graph.output
            else GPUPreparedFilterInputRef.Node(currentNodes.last().id)
        } else graph.output

        val resultGraph = GPUPreparedFilterGraph(
            nodes = currentNodes.toList(),
            output = output,
            identity = GPUPreparedFilterGraph.computeIdentity(currentNodes.toList(), output),
        )

        return GPUPreparedFilterNormalization(
            graph = resultGraph,
            rewrites = rewrites.toList(),
            materializationNodeIds = materializationIds.filter {
                currentNodes.any { n -> n.id == it }
            }.toSet(),
        )
    }

    private fun buildEdgeMap(nodes: List<GPUPreparedFilterNode>): Map<GPUPreparedFilterNodeId, Set<GPUPreparedFilterNodeId>> {
        val map = mutableMapOf<GPUPreparedFilterNodeId, MutableSet<GPUPreparedFilterNodeId>>()
        for (node in nodes) {
            for (input in node.inputs) {
                if (input is GPUPreparedFilterInputRef.Node) {
                    map.getOrPut(input.id) { mutableSetOf() }.add(node.id)
                }
            }
        }
        return map.mapValues { it.value.toSet() }
    }

    private fun tryRemoveIdentity(
        node: GPUPreparedFilterNode,
        rewrites: MutableList<GPUPreparedFilterRewriteProof>,
        bounds: Map<GPUPreparedFilterNodeId, GPUFilterBoundsPlan>,
    ): GPUPreparedFilterNode? {
        if (!isIdentity(node)) return null
        rewrites.add(GPUPreparedFilterRewriteProof(
            rule = "remove-identity",
            sourceNodeIds = listOf(node.id),
            resultNodeIds = emptyList(),
            removedIntermediateCount = 1,
            inputBoundsIdentity = bounds[node.id]?.inputBoundsLabel ?: "unknown",
            outputBoundsIdentity = bounds[node.id]?.outputBoundsLabel ?: "unknown",
        ))
        return node
    }

    private fun tryRewrite(
        producer: GPUPreparedFilterNode,
        consumer: GPUPreparedFilterNode,
        bounds: Map<GPUPreparedFilterNodeId, GPUFilterBoundsPlan>,
        colorFacts: GPUFilterColorPlan,
    ): RewriteResult? {
        val targetInput = consumer.inputs.firstOrNull { it is GPUPreparedFilterInputRef.Node && it.id == producer.id }
            ?: return null

        return when {
            producer.kind == GPUPreparedFilterKind.Offset && consumer.kind == GPUPreparedFilterKind.Offset -> {
                val p0 = producer.parameters as OffsetParams
                val p1 = consumer.parameters as OffsetParams
                val merged = GPUPreparedFilterNode(
                    id = mergeId(producer.id, consumer.id),
                    kind = GPUPreparedFilterKind.Offset,
                    inputs = producer.inputs,
                    parameters = OffsetParams(p0.dx + p1.dx, p0.dy + p1.dy),
                    provenance = "normalized:${producer.id.value}+${consumer.id.value}",
                )
                RewriteResult(merged, GPUPreparedFilterRewriteProof(
                    rule = "compose-offset",
                    sourceNodeIds = listOf(producer.id, consumer.id),
                    resultNodeIds = listOf(merged.id),
                    removedIntermediateCount = 1,
                    inputBoundsIdentity = bounds[producer.id]?.inputBoundsLabel ?: "unknown",
                    outputBoundsIdentity = bounds[consumer.id]?.outputBoundsLabel ?: "unknown",
                ))
            }
            producer.kind == GPUPreparedFilterKind.Crop && consumer.kind == GPUPreparedFilterKind.Crop -> {
                val p0 = producer.parameters as CropParams
                val p1 = consumer.parameters as CropParams
                val ix = maxOf(p0.x, p1.x)
                val iy = maxOf(p0.y, p1.y)
                val iw = minOf(p0.x + p0.width, p1.x + p1.width) - ix
                val ih = minOf(p0.y + p0.height, p1.y + p1.height) - iy
                val merged = GPUPreparedFilterNode(
                    id = mergeId(producer.id, consumer.id),
                    kind = GPUPreparedFilterKind.Crop,
                    inputs = producer.inputs,
                    parameters = CropParams(ix, iy, maxOf(0f, iw), maxOf(0f, ih), tileMode = p0.tileMode),
                    provenance = "normalized:${producer.id.value}+${consumer.id.value}",
                )
                RewriteResult(merged, GPUPreparedFilterRewriteProof(
                    rule = "intersect-crop",
                    sourceNodeIds = listOf(producer.id, consumer.id),
                    resultNodeIds = listOf(merged.id),
                    removedIntermediateCount = 1,
                    inputBoundsIdentity = bounds[producer.id]?.inputBoundsLabel ?: "unknown",
                    outputBoundsIdentity = bounds[consumer.id]?.outputBoundsLabel ?: "unknown",
                ))
            }
            producer.kind == GPUPreparedFilterKind.ColorFilter && consumer.kind == GPUPreparedFilterKind.ColorFilter -> {
                if (colorFacts.conversionPolicy != "passthrough" && colorFacts.conversionPolicy != "") return null
                val p0 = producer.parameters as ColorFilterParams
                val p1 = consumer.parameters as ColorFilterParams
                val composed = composeColorMatrices(p0.matrix, p1.matrix)
                val merged = GPUPreparedFilterNode(
                    id = mergeId(producer.id, consumer.id),
                    kind = GPUPreparedFilterKind.ColorFilter,
                    inputs = producer.inputs,
                    parameters = ColorFilterParams(composed),
                    provenance = "normalized:${producer.id.value}+${consumer.id.value}",
                )
                RewriteResult(merged, GPUPreparedFilterRewriteProof(
                    rule = "fold-color-filter",
                    sourceNodeIds = listOf(producer.id, consumer.id),
                    resultNodeIds = listOf(merged.id),
                    removedIntermediateCount = 1,
                    inputBoundsIdentity = bounds[producer.id]?.inputBoundsLabel ?: "unknown",
                    outputBoundsIdentity = bounds[consumer.id]?.outputBoundsLabel ?: "unknown",
                ))
            }
            else -> null
        }
    }

    private fun composeColorMatrices(a: FloatArray, b: FloatArray): FloatArray {
        val result = FloatArray(20)
        for (row in 0..3) {
            for (col in 0..3) {
                var sum = 0f
                for (k in 0..3) sum += a[row * 5 + k] * b[k * 5 + col]
                result[row * 5 + col] = sum
            }
            var sum = 0f
            for (k in 0..3) sum += a[row * 5 + k] * b[k * 5 + 4]
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

    private fun isMaterialization(node: GPUPreparedFilterNode): Boolean =
        node.kind in materializationKinds

    private fun mergeId(a: GPUPreparedFilterNodeId, b: GPUPreparedFilterNodeId): GPUPreparedFilterNodeId =
        GPUPreparedFilterNodeId("${a.value}_${b.value}_merged")

    private fun remapInputs(node: GPUPreparedFilterNode, replacements: Map<GPUPreparedFilterNodeId, GPUPreparedFilterInputRef>): GPUPreparedFilterNode {
        val newInputs = node.inputs.map { input ->
            if (input is GPUPreparedFilterInputRef.Node) {
                replacements[input.id] ?: input
            } else input
        }
        return GPUPreparedFilterNode(node.id, node.kind, newInputs, node.parameters, node.provenance)
    }

    private fun remapOutput(output: GPUPreparedFilterInputRef, replacements: Map<GPUPreparedFilterNodeId, GPUPreparedFilterInputRef>): GPUPreparedFilterInputRef {
        if (output is GPUPreparedFilterInputRef.Node) {
            return replacements[output.id] ?: output
        }
        return output
    }

    private class RewriteResult(
        val resultNode: GPUPreparedFilterNode,
        val proof: GPUPreparedFilterRewriteProof,
    )
}
