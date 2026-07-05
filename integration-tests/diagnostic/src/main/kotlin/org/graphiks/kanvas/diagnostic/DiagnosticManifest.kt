package org.graphiks.kanvas.diagnostic

import java.util.Locale

/**
 * Root diagnostic output for a single GM test. Contains the pixel comparison
 * result, optional spatial report (Layer 1), op trace (Layer 2), pipeline trace
 * (Layer 3), and an `agentSummary` section with natural-language hypotheses and
 * actionable fix suggestions. Use `toJson()` to serialize to agent-consumable
 * JSON.
 */
data class DiagnosticManifest(
    val gm: String,
    val debugLevel: String,
    val generatedAt: String,
    val result: ResultSection,
    val spatialReport: SpatialReport?,
    val opTrace: OpTrace?,
    val pipelineTrace: PipelineTrace?,
    val agentSummary: AgentSummary,
) {
    fun toJson(): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"gm\": \"${esc(gm)}\",")
        sb.appendLine("  \"debugLevel\": \"${esc(debugLevel)}\",")
        sb.appendLine("  \"generatedAt\": \"${esc(generatedAt)}\",")

        sb.appendLine("  \"result\": {")
        sb.appendLine("    \"status\": \"${result.status}\",")
        sb.appendLine("    \"similarity\": ${fmt2(result.similarity)},")
        sb.appendLine("    \"threshold\": ${fmt2(result.threshold)},")
        sb.appendLine("    \"totalPixels\": ${result.totalPixels},")
        sb.appendLine("    \"mismatchingPixels\": ${result.mismatchingPixels},")
        sb.appendLine("    \"perChannel\": {")
        val chs = listOf("R" to 0, "G" to 1, "B" to 2, "A" to 3)
        sb.append(chs.joinToString(",\n") { (name, i) ->
            "      \"$name\": { \"maxDelta\": ${result.maxDelta[i]}, \"meanDelta\": ${fmt2(result.meanDelta[i])}, \"mismatchPct\": ${fmt2(result.mismatchPct[i])} }"
        })
        sb.appendLine()
        sb.appendLine("    }")
        sb.appendLine("  },")
        sb.appendLine("  \"spatialReport\": ${spatialJson(spatialReport)},")
        sb.appendLine("  \"opTrace\": ${opTraceJson(opTrace)},")
        sb.appendLine("  \"pipelineTrace\": ${pipelineTraceJson(pipelineTrace)},")
        sb.appendLine("  \"agentSummary\": {")
        sb.appendLine("    \"primaryIssue\": \"${esc(agentSummary.primaryIssue)}\",")
        sb.appendLine("    \"alphaChannel\": \"${esc(agentSummary.alphaChannel)}\",")
        sb.appendLine("    \"suspectOps\": [")
        sb.append(agentSummary.suspectOps.joinToString(",\n") { sop ->
            "      { \"index\": ${sop.index}, \"hypothesis\": \"${esc(sop.hypothesis)}\", \"action\": \"${esc(sop.action)}\" }"
        })
        sb.appendLine()
        sb.appendLine("    ]")
        sb.appendLine("  }")
        sb.append("}")
        return sb.toString()
    }

    private fun spatialJson(sr: SpatialReport?): String {
        if (sr == null) return "null"
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"ssim\": ${fmt2(sr.ssim)},")
        sb.appendLine("  \"ssimBlocks\": [")
        sb.append(sr.ssimBlocks.joinToString(",\n") { "    { \"x\": ${it.x}, \"y\": ${it.y}, \"score\": ${fmt2(it.score)} }" })
        sb.appendLine()
        sb.appendLine("  ],")
        sb.appendLine("  \"zones\": [")
        sb.append(sr.zones.joinToString(",\n") { z ->
            "    { \"label\": \"${z.label}\", \"bounds\": { \"x\": ${z.bounds.x}, \"y\": ${z.bounds.y}, \"w\": ${z.bounds.w}, \"h\": ${z.bounds.h} }, \"dominantChannel\": \"${z.dominantChannel}\", \"severity\": \"${z.severity}\", \"avgDelta\": ${fmt2(z.avgDelta)} }"
        })
        sb.appendLine()
        sb.appendLine("  ],")
        sb.appendLine("  \"heatmapUrl\": ${sr.heatmapUrl?.let { "\"$it\"" } ?: "null"}")
        sb.append("}")
        return sb.toString()
    }

    private fun opTraceJson(ot: OpTrace?): String {
        if (ot == null) return "null"
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"totalOps\": ${ot.totalOps},")
        sb.appendLine("  \"ops\": [")
        sb.append(ot.ops.joinToString(",\n") { op ->
            "    { \"index\": ${op.index}, \"type\": \"${op.type}\", \"pixelContribution\": ${fmt2(op.pixelContribution)}, \"isSuspect\": ${op.isSuspect}, \"beforeUrl\": ${op.beforeUrl?.let { "\"$it\"" } ?: "null"}, \"afterUrl\": ${op.afterUrl?.let { "\"$it\"" } ?: "null"}, \"deltaUrl\": ${op.deltaUrl?.let { "\"$it\"" } ?: "null"} }"
        })
        sb.appendLine()
        sb.appendLine("  ],")
        sb.appendLine("  \"suspectOps\": [${ot.suspectOps.joinToString { it.toString() }}]")
        sb.append("}")
        return sb.toString()
    }

    private fun pipelineTraceJson(pt: PipelineTrace?): String {
        if (pt == null) return "null"
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"summary\": { \"dispatched\": ${pt.summary.dispatched}, \"refused\": ${pt.summary.refused} },")
        sb.appendLine("  \"ops\": [")
        sb.append(pt.ops.joinToString(",\n") { op ->
            val shaders = if (op.shaders != null) "[${op.shaders.joinToString { "\"$it\"" }}]" else "null"
            "    { \"opIndex\": ${op.opIndex}, \"route\": \"${op.route}\", \"status\": \"${op.status}\", \"shaders\": $shaders, \"vertexCount\": ${op.vertexCount ?: "null"}, \"blendMode\": ${op.blendMode?.let { "\"$it\"" } ?: "null"}, \"reason\": ${if (op.reason != null) "{ \"code\": \"${op.reason.code}\", \"message\": \"${esc(op.reason.message)}\" }" else "null"} }"
        })
        sb.appendLine()
        sb.appendLine("  ]")
        sb.append("}")
        return sb.toString()
    }

    companion object {
        private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
        private fun fmt2(v: Double): String = String.format(Locale.US, "%.2f", v)
    }
}

/**
 * Pixel comparison result: pass/fail status, similarity percentage, threshold,
 * pixel counts, per-channel max and mean delta, and per-channel mismatch
 * percentages.
 */
data class ResultSection(
    val status: String,
    val similarity: Double,
    val threshold: Double,
    val totalPixels: Int,
    val mismatchingPixels: Int,
    val maxDelta: IntArray,
    val meanDelta: DoubleArray,
    val mismatchPct: DoubleArray,
)

/**
 * Human-readable diagnostic synthesis for AI agent consumption. Contains the
 * primary rendering issue description, alpha channel analysis, and a list of
 * suspect operations with hypotheses and suggested fix actions.
 */
data class AgentSummary(
    val primaryIssue: String,
    val alphaChannel: String,
    val suspectOps: List<SuspectOpSummary>,
)

/**
 * A suspect drawing operation: its index in the op list, a hypothesis about what
 * went wrong, and a concrete action for the agent to take.
 */
data class SuspectOpSummary(
    val index: Int,
    val hypothesis: String,
    val action: String,
)
