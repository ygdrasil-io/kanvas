package org.graphiks.kanvas.gpu.renderer.filters

import java.nio.ByteBuffer
import java.security.MessageDigest

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
        val rewrites = mutableListOf<GPUPreparedFilterRewriteProof>()
        val nodes = graph.nodes.toMutableList()
        var output = graph.output

        while (true) {
            val identity = nodes.firstOrNull(::isIdentity)
            if (identity != null) {
                val replacement = identity.inputs.single()
                remapAllConsumers(nodes, identity.id, replacement)
                output = remapReference(output, identity.id, replacement)
                nodes.remove(identity)
                rewrites += GPUPreparedFilterRewriteProof(
                    rule = "remove-identity",
                    sourceNodeIds = listOf(identity.id),
                    resultNodeIds = emptyList(),
                    removedIntermediateCount = 1,
                    inputBoundsIdentity = bounds[identity.id]?.inputBoundsLabel ?: "unknown",
                    outputBoundsIdentity = bounds[identity.id]?.outputBoundsLabel ?: "unknown",
                )
                continue
            }

            val consumers = buildConsumerMap(nodes)
            val rewrite = nodes.firstNotNullOfOrNull { producer ->
                if (output == GPUPreparedFilterInputRef.Node(producer.id)) {
                    null
                } else {
                    val consumerIds = consumers[producer.id].orEmpty()
                    if (consumerIds.size != 1) {
                        null
                    } else {
                        val consumer = nodes.firstOrNull { it.id == consumerIds.single() }
                        consumer?.let {
                            tryRewrite(
                                producer = producer,
                                consumer = it,
                                bounds = bounds,
                                colorFacts = colorFacts,
                                occupiedIds = nodes.mapTo(mutableSetOf()) { node -> node.id },
                            )
                        }
                    }
                }
            } ?: break

            val producerIndex = nodes.indexOfFirst { it.id == rewrite.producerId }
            val consumerIndex = nodes.indexOfFirst { it.id == rewrite.consumerId }
            val insertionIndex = minOf(producerIndex, consumerIndex)
            nodes.removeAll { it.id == rewrite.producerId || it.id == rewrite.consumerId }
            remapAllConsumers(
                nodes,
                rewrite.consumerId,
                GPUPreparedFilterInputRef.Node(rewrite.resultNode.id),
            )
            output = remapReference(
                output,
                rewrite.consumerId,
                GPUPreparedFilterInputRef.Node(rewrite.resultNode.id),
            )
            nodes.add(insertionIndex.coerceAtMost(nodes.size), rewrite.resultNode)
            rewrites += rewrite.proof
        }

        val normalizedGraph = GPUPreparedFilterGraph(
            nodes = nodes,
            output = output,
        )
        return GPUPreparedFilterNormalization(
            graph = normalizedGraph,
            rewrites = rewrites,
            materializationNodeIds = nodes
                .asSequence()
                .filter(::isMaterialization)
                .mapTo(linkedSetOf()) { it.id },
        )
    }

    private fun buildConsumerMap(
        nodes: List<GPUPreparedFilterNode>,
    ): Map<GPUPreparedFilterNodeId, Set<GPUPreparedFilterNodeId>> {
        val result =
            mutableMapOf<GPUPreparedFilterNodeId, MutableSet<GPUPreparedFilterNodeId>>()
        nodes.forEach { consumer ->
            consumer.inputs.forEach { input ->
                if (input is GPUPreparedFilterInputRef.Node) {
                    result.getOrPut(input.id) { linkedSetOf() } += consumer.id
                }
            }
        }
        return result.mapValues { (_, ids) -> ids.toSet() }
    }

    private fun tryRewrite(
        producer: GPUPreparedFilterNode,
        consumer: GPUPreparedFilterNode,
        bounds: Map<GPUPreparedFilterNodeId, GPUFilterBoundsPlan>,
        colorFacts: GPUFilterColorPlan,
        occupiedIds: Set<GPUPreparedFilterNodeId>,
    ): RewriteResult? {
        if (consumer.inputs.count {
                it == GPUPreparedFilterInputRef.Node(producer.id)
            } != 1
        ) {
            return null
        }

        val result = when {
            producer.kind == GPUPreparedFilterKind.Offset &&
                consumer.kind == GPUPreparedFilterKind.Offset -> {
                val first = producer.parameters as OffsetParams
                val second = consumer.parameters as OffsetParams
                val dx = first.dx + second.dx
                val dy = first.dy + second.dy
                if (!dx.isFinite() || !dy.isFinite()) return null
                RewrittenNode(
                    kind = GPUPreparedFilterKind.Offset,
                    inputs = producer.inputs,
                    parameters = OffsetParams(dx, dy),
                    rule = "compose-offset",
                )
            }

            producer.kind == GPUPreparedFilterKind.Crop &&
                consumer.kind == GPUPreparedFilterKind.Crop -> {
                val first = producer.parameters as CropParams
                val second = consumer.parameters as CropParams
                if (first.tileMode != second.tileMode) return null
                val left = maxOf(first.x, second.x)
                val top = maxOf(first.y, second.y)
                val right = minOf(first.x + first.width, second.x + second.width)
                val bottom = minOf(first.y + first.height, second.y + second.height)
                if (!left.isFinite() || !top.isFinite() ||
                    !right.isFinite() || !bottom.isFinite()
                ) {
                    return null
                }
                RewrittenNode(
                    kind = GPUPreparedFilterKind.Crop,
                    inputs = producer.inputs,
                    parameters = CropParams(
                        x = left,
                        y = top,
                        width = maxOf(0f, right - left),
                        height = maxOf(0f, bottom - top),
                        tileMode = first.tileMode,
                    ),
                    rule = "intersect-crop",
                )
            }

            producer.kind == GPUPreparedFilterKind.ColorFilter &&
                consumer.kind == GPUPreparedFilterKind.ColorFilter -> {
                if (colorFacts.conversionPolicy.isNotEmpty() &&
                    colorFacts.conversionPolicy != "passthrough"
                ) {
                    return null
                }
                val first = producer.parameters as ColorFilterParams
                val second = consumer.parameters as ColorFilterParams
                val composed = composeColorMatrices(
                    producer = first.matrix,
                    consumer = second.matrix,
                )
                if (composed.any { !it.isFinite() }) return null
                RewrittenNode(
                    kind = GPUPreparedFilterKind.ColorFilter,
                    inputs = producer.inputs,
                    parameters = ColorFilterParams(composed),
                    rule = "fold-color-filter",
                )
            }

            else -> return null
        }

        val mergedId = mergeId(producer, consumer, occupiedIds)
        val mergedNode = GPUPreparedFilterNode(
            id = mergedId,
            kind = result.kind,
            inputs = result.inputs,
            parameters = result.parameters,
            provenance = "normalized:${producer.provenance}+${consumer.provenance}",
        )
        return RewriteResult(
            producerId = producer.id,
            consumerId = consumer.id,
            resultNode = mergedNode,
            proof = GPUPreparedFilterRewriteProof(
                rule = result.rule,
                sourceNodeIds = listOf(producer.id, consumer.id),
                resultNodeIds = listOf(mergedId),
                removedIntermediateCount = 1,
                inputBoundsIdentity =
                    bounds[producer.id]?.inputBoundsLabel ?: "unknown",
                outputBoundsIdentity =
                    bounds[consumer.id]?.outputBoundsLabel ?: "unknown",
            ),
        )
    }

    /**
     * A producer matrix executes first, then the consumer matrix. With column
     * colors that means result = consumer × producer, including bias.
     */
    private fun composeColorMatrices(
        producer: FloatArray,
        consumer: FloatArray,
    ): FloatArray {
        val result = FloatArray(20)
        for (row in 0..3) {
            for (column in 0..3) {
                var sum = 0f
                for (k in 0..3) {
                    sum += consumer[row * 5 + k] * producer[k * 5 + column]
                }
                result[row * 5 + column] = sum
            }
            var bias = consumer[row * 5 + 4]
            for (k in 0..3) {
                bias += consumer[row * 5 + k] * producer[k * 5 + 4]
            }
            result[row * 5 + 4] = bias
        }
        return result
    }

    private fun remapAllConsumers(
        nodes: MutableList<GPUPreparedFilterNode>,
        removedId: GPUPreparedFilterNodeId,
        replacement: GPUPreparedFilterInputRef,
    ) {
        nodes.indices.forEach { index ->
            val node = nodes[index]
            val remappedInputs = node.inputs.map { input ->
                remapReference(input, removedId, replacement)
            }
            if (remappedInputs != node.inputs) {
                nodes[index] = GPUPreparedFilterNode(
                    id = node.id,
                    kind = node.kind,
                    inputs = remappedInputs,
                    parameters = remapParameterInputs(
                        parameters = node.parameters,
                        removedId = removedId,
                        replacement = replacement,
                    ),
                    provenance = node.provenance,
                )
            }
        }
    }

    private fun remapParameterInputs(
        parameters: GPUPreparedFilterParameters,
        removedId: GPUPreparedFilterNodeId,
        replacement: GPUPreparedFilterInputRef,
    ): GPUPreparedFilterParameters =
        when (parameters) {
            is ComposeParams -> ComposeParams(
                inner = remapReference(parameters.inner, removedId, replacement),
                outer = remapReference(parameters.outer, removedId, replacement),
            )
            is MergeParams -> MergeParams(
                parameters.inputs.map { input ->
                    remapReference(input, removedId, replacement)
                },
            )
            else -> parameters
        }

    private fun remapReference(
        reference: GPUPreparedFilterInputRef,
        removedId: GPUPreparedFilterNodeId,
        replacement: GPUPreparedFilterInputRef,
    ): GPUPreparedFilterInputRef =
        if (reference == GPUPreparedFilterInputRef.Node(removedId)) {
            replacement
        } else {
            reference
        }

    private fun isIdentity(node: GPUPreparedFilterNode): Boolean {
        if (node.kind != GPUPreparedFilterKind.ColorFilter) return false
        val matrix = (node.parameters as? ColorFilterParams)?.matrix ?: return false
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        return matrix.contentEquals(identity)
    }

    private fun isMaterialization(node: GPUPreparedFilterNode): Boolean =
        node.kind in materializationKinds

    private fun mergeId(
        producer: GPUPreparedFilterNode,
        consumer: GPUPreparedFilterNode,
        occupiedIds: Set<GPUPreparedFilterNodeId>,
    ): GPUPreparedFilterNodeId {
        var salt = 0
        while (true) {
            val digest = MessageDigest.getInstance("SHA-256")
            listOf(
                "normalized-filter-node:v1",
                producer.canonicalIdentity(),
                consumer.canonicalIdentity(),
                salt.toString(),
            ).forEach { part ->
                val bytes = part.toByteArray(Charsets.UTF_8)
                digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
                digest.update(bytes)
            }
            val value = "normalized_" +
                digest.digest().joinToString("") { "%02x".format(it) }
            val candidate = GPUPreparedFilterNodeId(value)
            if (candidate !in occupiedIds) return candidate
            salt++
        }
    }

    private data class RewrittenNode(
        val kind: GPUPreparedFilterKind,
        val inputs: List<GPUPreparedFilterInputRef>,
        val parameters: GPUPreparedFilterParameters,
        val rule: String,
    )

    private data class RewriteResult(
        val producerId: GPUPreparedFilterNodeId,
        val consumerId: GPUPreparedFilterNodeId,
        val resultNode: GPUPreparedFilterNode,
        val proof: GPUPreparedFilterRewriteProof,
    )
}
