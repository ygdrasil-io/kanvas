package org.graphiks.kanvas.gpu.renderer.vertices

/** Describes a single draw call for mesh batching. */
data class GPUDrawCallDescriptor(
    val drawId: String,
    val pipelineKey: String,
    val vertexCount: Int,
    val topology: GPUVertexMode,
    val blendMode: String,
    val sortKey: Int,
)

/** A batch of draw calls sharing the same pipeline state. */
data class GPUMeshBatch(
    val batchKey: String,
    val drawIds: List<String>,
    val pipelineKey: String,
    val totalVertexCount: Int,
)

/** Statistics from a mesh batching pass. */
data class GPUMeshBatchingStats(
    val inputDrawCount: Int,
    val batchCount: Int,
    val pipelineChangeCount: Int,
    val mergedDrawCount: Int,
    val nonClaimLine: String,
)

/** Batches compatible draw calls to reduce pipeline state changes. */
class GPUMeshBatcher {
    /** Sorts and batches draw calls by sort key and pipeline state. */
    fun batch(draws: List<GPUDrawCallDescriptor>): GPUMeshBatchingStats {
        require(draws.isNotEmpty()) { "GPUMeshBatcher requires at least one draw call" }

        val sorted = draws.sortedWith(
            compareBy<GPUDrawCallDescriptor> { it.sortKey }
                .thenBy { it.pipelineKey },
        )

        val batches = mutableListOf<GPUMeshBatch>()
        val accumulated = mutableListOf(sorted.first())
        var pipelineChanges = 0

        for (i in 1 until sorted.size) {
            val previous = sorted[i - 1]
            val draw = sorted[i]
            if (draw.pipelineKey == previous.pipelineKey &&
                draw.topology == previous.topology &&
                draw.blendMode == previous.blendMode
            ) {
                accumulated += draw
            } else {
                batches += accumulated.toMeshBatch()
                pipelineChanges++
                accumulated.clear()
                accumulated += draw
            }
        }
        batches += accumulated.toMeshBatch()

        val mergedDrawCount = sorted.size - batches.size

        return GPUMeshBatchingStats(
            inputDrawCount = draws.size,
            batchCount = batches.size,
            pipelineChangeCount = pipelineChanges,
            mergedDrawCount = mergedDrawCount,
            nonClaimLine = VERTICES_BATCHER_NONCLAIM_LINE,
        )
    }

    companion object {
        const val VERTICES_BATCHER_NONCLAIM_LINE: String =
            "vertices:nonclaim batchingSupported=true " +
                "crossLayerBatching=false destinationReadBatching=false " +
                "performanceReady=false productActivation=false"
    }
}

private fun MutableList<GPUDrawCallDescriptor>.toMeshBatch(): GPUMeshBatch {
    val first = first()
    val batchKey = "batch:${first.pipelineKey}:${first.topology.sourceLabel}:${first.blendMode}"
    return GPUMeshBatch(
        batchKey = batchKey,
        drawIds = map { it.drawId },
        pipelineKey = first.pipelineKey,
        totalVertexCount = sumOf { it.vertexCount },
    )
}
