package org.graphiks.kanvas.skia.evidence

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class Phase6CoverageFamiliesEvidence(
    val schemaVersion: String,
    val generatedBy: String,
    val dashboardSource: String,
    val sourceGeneratedAt: String?,
    val summary: Phase6CoverageSummary,
    val nonClaims: List<String>,
    val rows: List<Phase6CoverageRowEvidence>,
)

data class Phase6CoverageSummary(
    val totalRows: Int,
    val families: Map<String, Int>,
    val classifications: Map<String, Int>,
    val subfamilies: Map<String, Int>,
    val promotedRows: Int,
    val unexpectedFails: Int,
    val noScore: Int,
)

data class Phase6CoverageRowEvidence(
    val rowId: String,
    val name: String,
    val family: String,
    val subfamily: String,
    val classification: String,
    val similarity: Double?,
    val minSimilarity: Double?,
    val isPassing: Boolean?,
    val fallbackReason: String,
    val referencePath: String?,
    val generatedPath: String?,
    val diffPath: String?,
    val noScoreCause: String?,
    val nonClaim: String,
)

object Phase6CoverageFamilyClassifier {
    fun classify(
        row: GmDashboardRow,
        rowId: String = row.name,
    ): Phase6CoverageRowEvidence {
        require(row.family == "PATH" || row.family == "CLIP") { "Expected PATH or CLIP row, got ${row.family}" }
        val subfamily = coverageSubfamily(row)
        val gatedReason = gatedReason(subfamily)
        val noScore = row.similarity == null || row.noReference || row.renderFailed || row.sizeMismatch

        val classification: String
        val fallback: String
        val noScoreCause: String?
        val nonClaim: String
        when {
            noScore -> {
                classification = "no-score"
                fallback = gatedReason ?: "none"
                noScoreCause = noScoreCause(row)
                nonClaim = "No score is not counted as fail or support; reference/render/size/comparison evidence is incomplete."
            }
            row.isPassing == true -> {
                classification = "instrumented-existing"
                fallback = "none"
                noScoreCause = null
                nonClaim = "${row.family} row remains instrumented until route and coverage diagnostics are attached."
            }
            gatedReason != null -> {
                classification = "expected-unsupported"
                fallback = gatedReason
                noScoreCause = null
                nonClaim = "$subfamily remains outside Phase 6 coverage-family migration scope."
            }
            else -> {
                classification = "unexpected-fail"
                fallback = "none"
                noScoreCause = null
                nonClaim = "Reference and generated image exist, but score is below threshold without a stable refusal."
            }
        }

        return Phase6CoverageRowEvidence(
            rowId = rowId,
            name = row.name,
            family = row.family,
            subfamily = subfamily,
            classification = classification,
            similarity = row.similarity,
            minSimilarity = row.minSimilarity,
            isPassing = row.isPassing,
            fallbackReason = fallback,
            referencePath = if (row.noReference) null else "images/reference/${row.name}.png",
            generatedPath = if (row.renderFailed) null else "images/generated/${row.name}.png",
            diffPath = if (row.hasDiff) "images/diff/${row.name}.png" else null,
            noScoreCause = noScoreCause,
            nonClaim = nonClaim,
        )
    }

    fun buildEvidence(dashboard: GmDashboard): Phase6CoverageFamiliesEvidence {
        val rows = dashboard.rows
            .filter { row -> row.family == "PATH" || row.family == "CLIP" }
            .withStableCoverageRowIds()
            .map { (rowId, row) -> classify(row, rowId) }
        val classifications = rows.groupingBy { row -> row.classification }.eachCount().toSortedMap()
        val subfamilies = rows.groupingBy { row -> row.subfamily }.eachCount().toSortedMap()
        val families = rows.groupingBy { row -> row.family }.eachCount().toSortedMap()
        return Phase6CoverageFamiliesEvidence(
            schemaVersion = "phase6-coverage-families-v1",
            generatedBy = ":integration-tests:skia-evidence:generateGpuPhase6CoverageFamiliesEvidence",
            dashboardSource = "integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json",
            sourceGeneratedAt = dashboard.generatedAt,
            summary = Phase6CoverageSummary(
                totalRows = rows.size,
                families = families,
                classifications = classifications,
                subfamilies = subfamilies,
                promotedRows = classifications["promoted-support"] ?: 0,
                unexpectedFails = classifications["unexpected-fail"] ?: 0,
                noScore = classifications["no-score"] ?: 0,
            ),
            nonClaims = listOf(
                "No broad Path AA support is claimed from classification alone.",
                "No broad clip stack, inverse clip, perspective clip, path boolean, or global coverage budget support is added.",
                "Rows without route diagnostics remain instrumented rather than promoted.",
            ),
            rows = rows,
        )
    }

    private fun coverageSubfamily(row: GmDashboardRow): String {
        val name = row.name.coverageKey()
        return if (row.family == "PATH") pathSubfamily(name) else clipSubfamily(name)
    }

    private fun pathSubfamily(name: String): String =
        when {
            name.containsAnyCoverage("pathops", "poly2poly", "drawregion") -> "path-ops-gated"
            name.containsAnyCoverage("dash", "dashing", "trim", "patheffect") -> "path-dash-gated"
            name.containsAnyCoverage("hairline", "teeny", "thin", "zerocontrol", "zeroline", "zeropath") -> "path-hairline-gated"
            name.containsAnyCoverage("persp", "perspective") -> "path-perspective-gated"
            name.contains("shader") -> "path-shader-material-gated"
            name.containsAnyCoverage("stroke", "caps", "join", "overstroke", "widebuttcaps") -> "path-stroke-caps-joins"
            name.containsAnyCoverage("linepath", "points") -> "path-stroke-basic"
            name.contains("convex") -> "path-fill-convex"
            name.containsAnyCoverage("concave", "cubic", "quad", "conic", "roundrect", "manycircles", "polygons", "mandoline", "stlouis", "filltype", "preservefillrule") -> "path-fill-concave"
            else -> "path-fill-simple"
        }

    private fun clipSubfamily(name: String): String =
        when {
            name.containsAnyCoverage("inverse", "invert") -> "clip-inverse-gated"
            name.contains("complexclip") -> "clip-complex-gated"
            name.containsAnyCoverage("persp", "perspective") -> "clip-perspective-gated"
            name.containsAnyCoverage("large", "distant") -> "clip-large-budget-gated"
            name.contains("rrect") -> "clip-rrect"
            name.containsAnyCoverage("windowrect", "rect") -> "clip-rect"
            name.contains("convex") -> "clip-convex"
            name.containsAnyCoverage("aaclip", "simpleaaclip") -> "clip-path-aa-gated"
            else -> "clip-nested-bounded"
        }

    private fun gatedReason(subfamily: String): String? =
        when (subfamily) {
            "path-ops-gated" -> "unsupported.coverage.path_ops"
            "path-dash-gated" -> "unsupported.coverage.dash_pattern"
            "path-hairline-gated" -> "unsupported.coverage.hairline_stroke"
            "path-perspective-gated" -> "unsupported.coverage.perspective_clip"
            "path-shader-material-gated" -> "unsupported.material.shader_on_path"
            "clip-inverse-gated" -> "unsupported.coverage.inverse_clip"
            "clip-complex-gated" -> "unsupported.coverage.complex_clip"
            "clip-perspective-gated" -> "unsupported.coverage.perspective_clip"
            "clip-large-budget-gated" -> "unsupported.coverage.clip_depth_exceeded"
            "clip-path-aa-gated" -> "unsupported.coverage.complex_clip"
            else -> null
        }

    private fun noScoreCause(row: GmDashboardRow): String =
        when {
            row.noReference -> "reference-missing"
            row.renderFailed -> "generated-render-missing"
            row.sizeMismatch -> "size-mismatch"
            row.similarity == null -> "comparison-unavailable"
            else -> "none"
        }

    private fun List<GmDashboardRow>.withStableCoverageRowIds(): List<Pair<String, GmDashboardRow>> {
        val seenCounts = linkedMapOf<String, Int>()
        return map { row ->
            val count = seenCounts.getOrDefault(row.name, 0) + 1
            seenCounts[row.name] = count
            val rowId = if (count == 1) row.name else "${row.name}#$count"
            rowId to row
        }
    }
}

object Phase6CoverageFamilyEvidenceWriter {
    private val prettyJson = Json { prettyPrint = true }

    fun writeOutputs(root: Path, evidence: Phase6CoverageFamiliesEvidence) {
        val evidencePath = root.resolve("reports/gpu-renderer/phase-6-coverage-families/evidence.json")
        val csvPath = root.resolve("reports/gpu-renderer/phase-6-coverage-families/classification.csv")
        val markdownPath = root.resolve("reports/gpu-renderer/2026-07-09-gpu-phase-6-coverage-families.md")
        Files.createDirectories(evidencePath.parent)
        Files.createDirectories(markdownPath.parent)
        Files.writeString(evidencePath, prettyJson.encodeToString(JsonObject.serializer(), evidence.toJsonObject()) + "\n")
        Files.writeString(csvPath, evidence.toCsv())
        Files.writeString(markdownPath, evidence.toMarkdown())
    }
}

fun Phase6CoverageFamiliesEvidence.toJsonObject(): JsonObject =
    buildJsonObject {
        put("schemaVersion", schemaVersion)
        put("generatedBy", generatedBy)
        put("dashboardSource", dashboardSource)
        if (sourceGeneratedAt == null) put("sourceGeneratedAt", JsonNull) else put("sourceGeneratedAt", sourceGeneratedAt)
        put("summary", summary.toJsonObject())
        put("nonClaims", buildJsonArray { nonClaims.forEach { add(it) } })
        put("rows", buildJsonArray { rows.forEach { add(it.toJsonObject()) } })
    }

private fun Phase6CoverageSummary.toJsonObject(): JsonObject =
    buildJsonObject {
        put("totalRows", totalRows)
        put("families", families.toCoverageCountJsonObject())
        put("classifications", classifications.toCoverageCountJsonObject())
        put("subfamilies", subfamilies.toCoverageCountJsonObject())
        put("promotedRows", promotedRows)
        put("unexpectedFails", unexpectedFails)
        put("noScore", noScore)
    }

private fun Phase6CoverageRowEvidence.toJsonObject(): JsonObject =
    buildJsonObject {
        put("rowId", rowId)
        put("name", name)
        put("family", family)
        put("subfamily", subfamily)
        put("classification", classification)
        putNullableCoverageDouble("similarity", similarity)
        putNullableCoverageDouble("minSimilarity", minSimilarity)
        if (isPassing == null) put("isPassing", JsonNull) else put("isPassing", isPassing)
        put("fallbackReason", fallbackReason)
        putNullableCoverageString("referencePath", referencePath)
        putNullableCoverageString("generatedPath", generatedPath)
        putNullableCoverageString("diffPath", diffPath)
        putNullableCoverageString("noScoreCause", noScoreCause)
        put("nonClaim", nonClaim)
    }

private fun Map<String, Int>.toCoverageCountJsonObject(): JsonObject =
    buildJsonObject {
        entries.sortedBy { it.key }.forEach { (key, value) -> put(key, value) }
    }

private fun JsonObjectBuilder.putNullableCoverageString(key: String, value: String?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}

private fun JsonObjectBuilder.putNullableCoverageDouble(key: String, value: Double?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}

fun Phase6CoverageFamiliesEvidence.toCsv(): String =
    buildString {
        appendLine("rowId,name,family,subfamily,classification,similarity,minSimilarity,fallbackReason,noScoreCause")
        rows.forEach { row ->
            appendLine(
                listOf(
                    row.rowId,
                    row.name,
                    row.family,
                    row.subfamily,
                    row.classification,
                    row.similarity?.toString().orEmpty(),
                    row.minSimilarity?.toString().orEmpty(),
                    row.fallbackReason,
                    row.noScoreCause.orEmpty(),
                ).joinToString(",") { it.coverageCsvCell() },
            )
        }
    }

fun Phase6CoverageFamiliesEvidence.toMarkdown(): String =
    buildString {
        appendLine("# GPU Phase 6 Coverage Families Evidence")
        appendLine()
        appendLine("## Summary")
        appendLine()
        appendLine("- Total PATH + CLIP rows: ${summary.totalRows}")
        appendLine("- Families: ${summary.families}")
        appendLine("- Classifications: ${summary.classifications}")
        appendLine("- Subfamilies: ${summary.subfamilies}")
        appendLine()
        appendLine("## Non-Claims")
        appendLine()
        nonClaims.forEach { appendLine("- $it") }
        appendLine()
        appendLine("## Rows")
        appendLine()
        appendLine("| Row ID | Row | Family | Subfamily | Classification | Similarity | Fallback |")
        appendLine("|---|---|---|---|---|---:|---|")
        rows.forEach { row ->
            val similarity = row.similarity?.let { "%.2f".format(java.util.Locale.US, it) } ?: "n/a"
            appendLine("| `${row.rowId}` | `${row.name}` | `${row.family}` | `${row.subfamily}` | `${row.classification}` | $similarity | `${row.fallbackReason}` |")
        }
        appendLine()
        appendLine("## Regeneration Notes")
        appendLine()
        appendLine("- Run `:integration-tests:skia:generateSkiaDashboard` before generating coverage-family evidence.")
        appendLine("- Dashboard data is read from `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`.")
        appendLine("- Source counts changed after `#2010`; the regenerated dashboard is the evidence source of truth.")
    }

private fun String.coverageKey(): String =
    lowercase().filter { char -> char.isLetterOrDigit() }

private fun String.containsAnyCoverage(vararg tokens: String): Boolean =
    tokens.any(this::contains)

private fun String.coverageCsvCell(): String =
    if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + replace("\"", "\"\"") + "\""
    } else {
        this
    }
