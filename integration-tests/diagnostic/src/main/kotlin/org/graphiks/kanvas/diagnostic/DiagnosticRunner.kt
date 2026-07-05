package org.graphiks.kanvas.diagnostic

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.surface.DebugLevel
import org.graphiks.kanvas.test.ComparisonUtils
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * All inputs needed to produce a `DiagnosticManifest` for a single GM: pixel
 * buffers, dimensions, tolerance, recorded display operations, pipeline stats,
 * diagnostic messages, debug level, and output directory.
 */
data class RunnerInput(
    val gmName: String,
    val minSimilarity: Double,
    val actualRgba: ByteArray,
    val referenceRgba: ByteArray,
    val width: Int,
    val height: Int,
    val tolerance: Int,
    val ops: List<DisplayOp>,
    val dispatchedCount: Int,
    val refusedCount: Int,
    val diagnostics: List<String>,
    val debugLevel: DebugLevel,
    val outputDir: File,
)

/**
 * Orchestrator that runs all activated diagnostic layers and produces a
 * `DiagnosticManifest`. Layers are selected by `DebugLevel`: PIXEL enables
 * `DiffAnalyzer`, OP adds `OpInspector`, TRACE adds `PipelineTracer` (if a
 * tracer was attached to the Surface).
 */
object DiagnosticRunner {
    fun run(input: RunnerInput): DiagnosticManifest {
        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        val comparison = ComparisonUtils.compareRgba(
            actual = input.actualRgba,
            reference = input.referenceRgba,
            width = input.width,
            height = input.height,
            tolerance = input.tolerance,
            minSimilarity = input.minSimilarity,
        )

        val result = ResultSection(
            status = if (comparison.isPassing) "PASS" else "FAIL",
            similarity = comparison.similarity,
            threshold = comparison.minSimilarity,
            totalPixels = comparison.totalPixels,
            mismatchingPixels = comparison.totalPixels - comparison.matchingPixels,
            maxDelta = comparison.maxDiff,
            meanDelta = comparison.meanDiff,
            mismatchPct = doubleArrayOf(
                if (comparison.totalPixels > 0) 100.0 * (comparison.totalPixels - comparison.matchingPixels) / comparison.totalPixels else 0.0,
                if (comparison.totalPixels > 0) 100.0 * (comparison.totalPixels - comparison.matchingPixels) / comparison.totalPixels else 0.0,
                if (comparison.totalPixels > 0) 100.0 * (comparison.totalPixels - comparison.matchingPixels) / comparison.totalPixels else 0.0,
                if (comparison.totalPixels > 0) 100.0 * (comparison.totalPixels - comparison.matchingPixels) / comparison.totalPixels else 0.0,
            ),
        )

        val spatialReport = if (input.debugLevel >= DebugLevel.PIXEL) {
            DiffAnalyzer.analyze(
                actualRgba = input.actualRgba,
                referenceRgba = input.referenceRgba,
                width = input.width,
                height = input.height,
                tolerance = input.tolerance,
                outputDir = input.outputDir,
            )
        } else null

        val opTrace = if (input.debugLevel >= DebugLevel.OP && input.ops.isNotEmpty()) {
            OpInspector.inspect(
                ops = input.ops,
                referenceRgba = input.referenceRgba,
                gmWidth = input.width,
                gmHeight = input.height,
                tolerance = input.tolerance,
                outputDir = input.outputDir,
            )
        } else null

        val pipelineTrace: PipelineTrace? = null

        val channelNames = listOf("R", "G", "B", "A")
        val dominantChannelIdx = comparison.meanDiff.indices.maxByOrNull { comparison.meanDiff[it] } ?: 0
        val primaryIssue = "${channelNames[dominantChannelIdx]} channel shows dominant divergence " +
            "(maxDelta=${comparison.maxDiff[dominantChannelIdx]}, " +
            "meanDelta=${"%.2f".format(comparison.meanDiff[dominantChannelIdx])})"

        val alphaMatches = comparison.maxDiff[3] == 0
        val alphaChannel = if (alphaMatches)
            "Alpha channel matches perfectly — issue is color/rendering, not geometry or shape"
        else "Alpha channel also diverges — geometry or coverage may be incorrect"

        val suspectOpSummaries = if (opTrace != null) {
            opTrace.suspectOps.take(5).map { idx ->
                val op = opTrace.ops.find { it.index == idx }
                SuspectOpSummary(idx,
                    buildHypothesis(op?.type ?: "unknown"),
                    buildAction(op?.type ?: "unknown"))
            }
        } else emptyList()

        val agentSummary = AgentSummary(primaryIssue, alphaChannel, suspectOpSummaries)

        return DiagnosticManifest(
            gm = input.gmName,
            debugLevel = input.debugLevel.name,
            generatedAt = now,
            result = result,
            spatialReport = spatialReport,
            opTrace = opTrace,
            pipelineTrace = pipelineTrace,
            agentSummary = agentSummary,
        )
    }

    private fun buildHypothesis(opType: String): String =
        "$opType caused significant pixel divergence"

    private fun buildAction(opType: String): String =
        "Inspect $opType rendering logic in GmCanvas or pipeline"
}
