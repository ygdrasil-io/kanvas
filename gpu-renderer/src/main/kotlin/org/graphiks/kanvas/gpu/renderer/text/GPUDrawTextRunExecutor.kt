package org.graphiks.kanvas.gpu.renderer.text

/** A batch of glyph sub-runs sharing the same atlas. */
data class TextSubRunBatch(
    val subRunLabel: String,
    val atlasKey: String,
    val glyphRange: IntRange,
)

/** Result of executing a drawTextRun operation. */
data class DrawTextRunResult(
    val executorLabel: String,
    val subRunBatches: List<TextSubRunBatch>,
    val atlasKey: String,
    val accepted: Boolean,
    val diagnostic: String? = null,
)

/** Batches text sub-runs and produces drawTextRun execution results. */
class GPUDrawTextRunExecutor {
    /** Groups sub-runs into atlas-backed batches and returns the result. */
    fun execute(subRuns: List<GPUTextSubRunPlan>): DrawTextRunResult {
        if (subRuns.isEmpty()) {
            return DrawTextRunResult(
                executorLabel = "GPUDrawTextRunExecutor",
                subRunBatches = emptyList(),
                atlasKey = "",
                accepted = false,
                diagnostic = "no sub-runs to execute",
            )
        }

        val batches = subRuns.map { subRun ->
            TextSubRunBatch(
                subRunLabel = subRun.representation,
                atlasKey = subRun.atlasRefs.firstOrNull() ?: "a8-atlas-pending",
                glyphRange = subRun.glyphRange,
            )
        }

        val atlasKey = batches.firstOrNull()?.atlasKey ?: ""

        return DrawTextRunResult(
            executorLabel = "GPUDrawTextRunExecutor",
            subRunBatches = batches,
            atlasKey = atlasKey,
            accepted = atlasKey.isNotBlank(),
        )
    }

    companion object {
        const val nonClaimLine: String =
            "nonclaim:no-instance-batch-upload no-backend-draw-calls no-gpu-pipeline-state no-vertex-buffer-binding"
    }
}
