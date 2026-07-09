package org.graphiks.kanvas.skia.evidence

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories

data class ScoreFirstWave3Evidence(
    val generatedAt: String?,
    val candidates: List<ScoreFirstWave3Candidate>,
    val groupSummaries: List<ScoreFirstWave3GroupSummary>,
)

data class ScoreFirstWave3Candidate(
    val name: String,
    val family: String,
    val groupId: String,
    val groupName: String,
    val similarity: Double?,
    val minSimilarity: Double?,
    val isPassing: Boolean?,
    val matchingPixels: Long?,
    val totalPixels: Long?,
    val unmatchedPixels: Long,
    val status: String,
)

data class ScoreFirstWave3GroupSummary(
    val groupId: String,
    val groupName: String,
    val candidateCount: Int,
    val unmatchedPixels: Long,
)

data class ScoreFirstWave3Output(
    val markdownPath: Path,
    val tsvPath: Path,
)

object ScoreFirstWave3Classifier {
    fun buildEvidence(dashboard: GmDashboard): ScoreFirstWave3Evidence {
        val candidates = dashboard.rows
            .map { row -> row.toCandidate() }
            .sortedWith(
                compareByDescending<ScoreFirstWave3Candidate> { it.unmatchedPixels }
                    .thenBy { it.groupId }
                    .thenBy { it.name },
            )

        val summaries = candidates
            .groupBy { it.groupId to it.groupName }
            .map { (key, rows) ->
                ScoreFirstWave3GroupSummary(
                    groupId = key.first,
                    groupName = key.second,
                    candidateCount = rows.size,
                    unmatchedPixels = rows.sumOf { it.unmatchedPixels },
                )
            }
            .sortedBy { it.groupId }

        return ScoreFirstWave3Evidence(
            generatedAt = dashboard.generatedAt,
            candidates = candidates,
            groupSummaries = summaries,
        )
    }

    private fun GmDashboardRow.toCandidate(): ScoreFirstWave3Candidate {
        val group = classifyGroup()
        return ScoreFirstWave3Candidate(
            name = name,
            family = family,
            groupId = group.id,
            groupName = group.name,
            similarity = similarity,
            minSimilarity = minSimilarity,
            isPassing = isPassing,
            matchingPixels = matchingPixels,
            totalPixels = totalPixels,
            unmatchedPixels = unmatchedPixels(),
            status = status(),
        )
    }

    private fun GmDashboardRow.unmatchedPixels(): Long {
        val total = totalPixels ?: return 0
        val matched = matchingPixels ?: return 0
        if (total <= 0) return 0
        return (total - matched).coerceAtLeast(0)
    }

    private fun GmDashboardRow.status(): String =
        when {
            noReference -> "no-reference"
            renderFailed -> "render-failed"
            sizeMismatch -> "size-mismatch"
            similarity == null || isPassing == null -> "no-score"
            isPassing -> "pass"
            else -> "fail"
        }

    private fun GmDashboardRow.classifyGroup(): Wave3Group {
        val lower = name.lowercase()
        return when {
            family == "IMAGE" ||
                lower.contains("pictureshader") ||
                lower.contains("imageshader") ||
                lower.contains("tilemode") ||
                lower.contains("bitmap") ||
                lower.contains("coordclamp") -> Wave3Group.ImageSampling
            family == "COMPOSITE" ||
                lower.contains("xfer") ||
                lower.contains("colorfilter") ||
                lower.contains("blend") ||
                lower.contains("transparency") -> Wave3Group.CompositeBlend
            family == "CLIP" || family == "PATH" -> Wave3Group.ClipPathResiduals
            family == "RUNTIME_EFFECT" -> Wave3Group.RuntimeEffect
            else -> Wave3Group.Later
        }
    }
}

private sealed class Wave3Group(val id: String, val name: String) {
    data object ImageSampling : Wave3Group("A", "Image, bitmap, and shader sampling")
    data object CompositeBlend : Wave3Group("B", "Composite, color filters, and blend")
    data object ClipPathResiduals : Wave3Group("C", "Clip and path residuals")
    data object RuntimeEffect : Wave3Group("D", "Runtime effect cleanup")
    data object Later : Wave3Group("Z", "Later score-first backlog")
}

object ScoreFirstWave3Markdown {
    fun render(evidence: ScoreFirstWave3Evidence): String {
        val ranked = evidence.candidates.filter { it.unmatchedPixels > 0 }
        val noScoreRows = evidence.candidates.filter { it.status !in setOf("pass", "fail") }
        return buildString {
            appendLine("# GM Fidelity Wave 3 Score-First Evidence")
            appendLine()
            appendLine("Source: `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`")
            appendLine()
            appendLine("## Guardrails")
            appendLine()
            appendLine("- Do not modify `integration-tests/skia/src/test/resources/reference/**`.")
            appendLine("- Do not lower `minSimilarity` thresholds.")
            appendLine("- Keep noReference, renderFailed, sizeMismatch, and unsupported rows visible.")
            appendLine("- First slice: Work Group A.")
            appendLine()
            appendLine("## Group Summary")
            appendLine()
            appendLine("| Group | Name | Candidates | unmatchedPixels |")
            appendLine("|---|---|---:|---:|")
            evidence.groupSummaries.forEach { group ->
                appendLine("| ${group.groupId} | ${group.groupName} | ${group.candidateCount} | ${group.unmatchedPixels} |")
            }
            appendLine()
            appendLine("## Ranked Candidates")
            appendLine()
            appendLine("| Group | Family | GM | Similarity | Threshold | Status | unmatchedPixels |")
            appendLine("|---|---|---|---:|---:|---|---:|")
            ranked.forEach { row ->
                appendLine(
                    "| ${row.groupId} | ${row.family} | ${row.name} | ${row.similarity ?: ""} | " +
                        "${row.minSimilarity ?: ""} | ${row.status} | ${row.unmatchedPixels} |",
                )
            }
            if (noScoreRows.isNotEmpty()) {
                appendLine()
                appendLine("## Visible No-Score Rows")
                appendLine()
                appendLine("| Group | Family | GM | Status | matchingPixels | totalPixels |")
                appendLine("|---|---|---|---|---:|---:|")
                noScoreRows.forEach { row ->
                    appendLine(
                        "| ${row.groupId} | ${row.family} | ${row.name} | ${row.status} | " +
                            "${row.matchingPixels ?: ""} | ${row.totalPixels ?: ""} |",
                    )
                }
            }
        }.trimEnd() + "\n"
    }
}

object ScoreFirstWave3ReportWriter {
    fun write(root: Path, evidence: ScoreFirstWave3Evidence): ScoreFirstWave3Output {
        val reportDir = root.resolve("reports/gpu-renderer/gm-fidelity-wave-3-score-first")
        reportDir.createDirectories()
        val markdownPath = reportDir.resolve("evidence.md")
        val tsvPath = reportDir.resolve("candidates.tsv")

        Files.writeString(markdownPath, ScoreFirstWave3Markdown.render(evidence))
        Files.writeString(
            tsvPath,
            buildString {
                appendLine("group\tfamily\tname\tsimilarity\tthreshold\tstatus\tmatchingPixels\ttotalPixels\tunmatchedPixels")
                evidence.candidates.forEach { row ->
                    appendLine(
                        "${row.groupId}\t${row.family}\t${row.name}\t${row.similarity ?: ""}\t" +
                            "${row.minSimilarity ?: ""}\t${row.status}\t${row.matchingPixels ?: ""}\t${row.totalPixels ?: ""}\t${row.unmatchedPixels}",
                    )
                }
            },
        )
        return ScoreFirstWave3Output(
            markdownPath = markdownPath,
            tsvPath = tsvPath,
        )
    }
}
