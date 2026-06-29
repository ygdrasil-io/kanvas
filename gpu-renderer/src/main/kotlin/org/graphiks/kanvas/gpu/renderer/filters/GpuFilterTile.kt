package org.graphiks.kanvas.gpu.renderer.filters

/** Column/row position of a tile in the tile grid. */
data class GpuFilterTileIndex(val column: Int, val row: Int)

/** Rectangular region in the source or output texture. */
data class GpuFilterTileRegion(val x: Int, val y: Int, val width: Int, val height: Int)

/** Tile grid descriptor for tiled filter evaluation. */
data class GpuFilterTilingPlan(
    val tileWidth: Int,
    val tileHeight: Int,
    val overlap: Int,
    val columns: Int,
    val rows: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
)

/** Per-tile render plan produced by the tile evaluator. */
data class GpuFilterTileRenderPlan(
    val tilePlan: GpuFilterTilingPlan,
    val tileIndex: GpuFilterTileIndex,
    val sourceRegion: GpuFilterTileRegion,
    val targetRegion: GpuFilterTileRegion,
    val intermediateByteEstimate: Long,
)

/** Budget constraints for tiled filter evaluation. */
data class GpuFilterTileBudgetPolicy(
    val maxIntermediateBytes: Long,
    val maxActiveTileCount: Int,
)

/** Result of tiled filter evaluation. */
sealed interface GpuFilterTileEvaluationResult {
    data class Accepted(
        val plan: GpuFilterTilingPlan,
        val renderPlans: List<GpuFilterTileRenderPlan>,
    ) : GpuFilterTileEvaluationResult

    data class Refused(
        val diagnostic: GPUFilterDiagnostic,
    ) : GpuFilterTileEvaluationResult
}

/** Plans tiled filter evaluation — subdivides the source into overlapping tiles
 *  so that a large-source / large-kernel filter stays within per-tile GPU
 *  intermediate memory budgets. */
class GpuFilterTilePlanner(
    private val budgetPolicy: GpuFilterTileBudgetPolicy = GpuFilterTileBudgetPolicy(
        maxIntermediateBytes = 16L * 1024L * 1024L,
        maxActiveTileCount = 64,
    ),
    private val bytesPerPixel: Long = 4L,
) {
    /** Evaluates the tile plan for the given source, tile size, and overlap.
     *  Returns either an accepted plan with per-tile render plans, or a refused
     *  plan with a diagnostic. */
    fun evaluate(
        sourceWidth: Int,
        sourceHeight: Int,
        tileWidth: Int,
        tileHeight: Int,
        overlap: Int,
    ): GpuFilterTileEvaluationResult {
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return refused("unsupported.filter.tile_source_invalid")
        }

        if (tileWidth <= 0 || tileHeight <= 0 || overlap < 0) {
            return refused("unsupported.filter.tile_invalid_dimensions")
        }

        val interiorWidth = tileWidth - 2 * overlap
        val interiorHeight = tileHeight - 2 * overlap

        if (interiorWidth <= 0 || interiorHeight <= 0) {
            return refused("unsupported.filter.tile_smaller_than_kernel")
        }

        val columns = ceilDiv(sourceWidth, interiorWidth)
        val rows = ceilDiv(sourceHeight, interiorHeight)

        val tileCount = columns * rows

        if (tileCount > budgetPolicy.maxActiveTileCount) {
            return refused("unsupported.filter.tile_count_exceeds_budget")
        }

        val tileBytes = tileWidth.toLong() * tileHeight * bytesPerPixel
        if (tileBytes > budgetPolicy.maxIntermediateBytes) {
            return refused("unsupported.filter.tile_intermediate_memory_budget")
        }

        val plan = GpuFilterTilingPlan(
            tileWidth = tileWidth,
            tileHeight = tileHeight,
            overlap = overlap,
            columns = columns,
            rows = rows,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
        )

        val renderPlans = mutableListOf<GpuFilterTileRenderPlan>()
        for (row in 0 until rows) {
            for (col in 0 until columns) {
                val interiorX = col * interiorWidth
                val interiorY = row * interiorHeight

                val srcX = maxOf(0, interiorX - overlap)
                val srcY = maxOf(0, interiorY - overlap)
                val srcW = minOf(tileWidth, sourceWidth - srcX)
                val srcH = minOf(tileHeight, sourceHeight - srcY)

                val tgtW = minOf(interiorWidth, sourceWidth - interiorX)
                val tgtH = minOf(interiorHeight, sourceHeight - interiorY)

                val tileIntermediateBytes = tileWidth.toLong() * tileHeight * bytesPerPixel

                renderPlans.add(
                    GpuFilterTileRenderPlan(
                        tilePlan = plan,
                        tileIndex = GpuFilterTileIndex(col, row),
                        sourceRegion = GpuFilterTileRegion(srcX, srcY, srcW, srcH),
                        targetRegion = GpuFilterTileRegion(interiorX, interiorY, tgtW, tgtH),
                        intermediateByteEstimate = tileIntermediateBytes,
                    ),
                )
            }
        }

        return GpuFilterTileEvaluationResult.Accepted(plan, renderPlans)
    }

    private fun refused(code: String): GpuFilterTileEvaluationResult =
        GpuFilterTileEvaluationResult.Refused(
            GPUFilterDiagnostic(
                code = code,
                message = "Filter tile evaluation refused: $code",
                terminal = true,
            ),
        )

    private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b
}
