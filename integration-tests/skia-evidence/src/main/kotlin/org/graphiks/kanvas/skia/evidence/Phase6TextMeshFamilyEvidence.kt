package org.graphiks.kanvas.skia.evidence

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class Phase6TextMeshFamiliesEvidence(
    val schemaVersion: String,
    val generatedBy: String,
    val dashboardSource: String,
    val sourceGeneratedAt: String?,
    val summary: Phase6TextMeshSummary,
    val nonClaims: List<String>,
    val followUpCandidates: List<Phase6TextMeshFollowUpCandidate>,
    val rows: List<Phase6TextMeshRowEvidence>,
)

data class Phase6TextMeshSummary(
    val totalRows: Int,
    val families: Map<String, Int>,
    val familyDeltas: Map<String, Phase6TextMeshFamilyDelta>,
    val classifications: Map<String, Int>,
    val subfamilies: Map<String, Int>,
    val promotedRows: Int,
    val unexpectedFails: Int,
    val noScore: Int,
)

data class Phase6TextMeshFamilyDelta(
    val baselineSource: String,
    val currentSource: String,
    val currentGeneratedAt: String?,
    val baselineCount: Int,
    val currentCount: Int,
    val delta: Int,
)

data class Phase6TextMeshFollowUpCandidate(
    val rootCause: String,
    val classification: String,
    val rowCount: Int,
    val sampleRows: List<String>,
)

data class Phase6TextMeshRowEvidence(
    val rowId: String,
    val name: String,
    val family: String,
    val subfamily: String,
    val classification: String,
    val similarity: Double?,
    val minSimilarity: Double?,
    val isPassing: Boolean?,
    val width: Int?,
    val height: Int?,
    val matchingPixels: Long?,
    val totalPixels: Long?,
    val maxDiff: GmRgbaInt?,
    val meanDiff: GmRgbaDouble?,
    val fallbackReason: String,
    val referencePath: String?,
    val generatedPath: String?,
    val diffPath: String?,
    val noScoreCause: String?,
    val nonClaim: String,
)

object Phase6TextMeshFamilyClassifier {
    private const val baselineSource = "2026-07-09 local dashboard before text-mesh-family wave"
    private val textMeshFamilies = setOf("TEXT", "MESH")
    private val baselineFamilyCounts = linkedMapOf("MESH" to 16, "TEXT" to 77)

    fun classify(
        row: GmDashboardRow,
        rowId: String = row.name,
    ): Phase6TextMeshRowEvidence {
        require(row.family in textMeshFamilies) { "Expected TEXT or MESH row, got ${row.family}" }
        val subfamily = textMeshSubfamily(row)
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
                nonClaim = "No score is not counted as fail or support; text/mesh evidence is incomplete."
            }
            gatedReason != null -> {
                classification = "expected-unsupported"
                fallback = gatedReason
                noScoreCause = null
                nonClaim = "$subfamily remains outside Phase 6 text/mesh-family migration scope."
            }
            row.isPassing == true -> {
                classification = "instrumented-existing"
                fallback = "none"
                noScoreCause = null
                nonClaim = "${row.family} row remains instrumented until route and text/mesh diagnostics are attached."
            }
            else -> {
                classification = "unexpected-fail"
                fallback = "none"
                noScoreCause = null
                nonClaim = "Reference and generated image exist, but score is below threshold without a stable text/mesh refusal."
            }
        }

        return Phase6TextMeshRowEvidence(
            rowId = rowId,
            name = row.name,
            family = row.family,
            subfamily = subfamily,
            classification = classification,
            similarity = row.similarity,
            minSimilarity = row.minSimilarity,
            isPassing = row.isPassing,
            width = row.width,
            height = row.height,
            matchingPixels = row.matchingPixels,
            totalPixels = row.totalPixels,
            maxDiff = row.maxDiff,
            meanDiff = row.meanDiff,
            fallbackReason = fallback,
            referencePath = if (row.noReference) null else "images/reference/${row.name}.png",
            generatedPath = if (row.renderFailed) null else "images/generated/${row.name}.png",
            diffPath = if (row.hasDiff) "images/diff/${row.name}.png" else null,
            noScoreCause = noScoreCause,
            nonClaim = nonClaim,
        )
    }

    fun buildEvidence(dashboard: GmDashboard): Phase6TextMeshFamiliesEvidence {
        val rows = dashboard.rows
            .filter { row -> row.family in textMeshFamilies }
            .withStableTextMeshRowIds()
            .map { (rowId, row) -> classify(row, rowId) }
        val classifications = rows.groupingBy { it.classification }.eachCount().toSortedMap()
        val subfamilies = rows.groupingBy { it.subfamily }.eachCount().toSortedMap()
        val families = rows.groupingBy { it.family }.eachCount().toSortedMap()
        val familyDeltas = baselineFamilyCounts.mapValues { (family, baselineCount) ->
            val currentCount = families[family] ?: 0
            Phase6TextMeshFamilyDelta(
                baselineSource = baselineSource,
                currentSource = "integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json",
                currentGeneratedAt = dashboard.generatedAt,
                baselineCount = baselineCount,
                currentCount = currentCount,
                delta = currentCount - baselineCount,
            )
        }.toSortedMap()

        return Phase6TextMeshFamiliesEvidence(
            schemaVersion = "phase6-text-mesh-families-v1",
            generatedBy = ":integration-tests:skia-evidence:generateGpuPhase6TextMeshFamiliesEvidence",
            dashboardSource = "integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json",
            sourceGeneratedAt = dashboard.generatedAt,
            summary = Phase6TextMeshSummary(
                totalRows = rows.size,
                families = families,
                familyDeltas = familyDeltas,
                classifications = classifications,
                subfamilies = subfamilies,
                promotedRows = classifications["promoted-support"] ?: 0,
                unexpectedFails = classifications["unexpected-fail"] ?: 0,
                noScore = classifications["no-score"] ?: 0,
            ),
            nonClaims = listOf(
                "No broad TEXT or MESH support is claimed from classification alone.",
                "shaping, font fallback, glyph atlas, glyph cache, color fonts, emoji, palettes, transformed text, text filters, and clip/text interactions remain outside this evidence wave unless row diagnostics prove a bounded route.",
                "custom mesh, dynamic mesh updates, perspective mesh, picture mesh, image dependencies, paint-image dependencies, mesh effects, and arbitrary vertices remain outside this evidence wave unless row diagnostics prove a bounded route.",
                "Rows without route and text/mesh diagnostics remain instrumented rather than promoted.",
            ),
            followUpCandidates = rows.toTextMeshFollowUpCandidates(),
            rows = rows,
        )
    }

    private fun textMeshSubfamily(row: GmDashboardRow): String {
        val name = row.name.textMeshKey()
        return when (row.family) {
            "TEXT" -> textSubfamily(name)
            "MESH" -> meshSubfamily(name)
            else -> error("Unexpected text/mesh family ${row.family}")
        }
    }

    private fun textSubfamily(name: String): String =
        when {
            name.containsAnyTextMesh("clip") -> "text-clip-interaction-gated"
            name.containsAnyTextMesh("textfilter", "blur") -> "text-filter-or-blur-gated"
            name.containsAnyTextMesh("gradtext", "gradienttext", "shader") -> "text-shader-or-gradient-gated"
            name.containsAnyTextMesh("rsxform") -> "text-rsxform-gated"
            name.containsAnyTextMesh("persp", "perspective", "dftextblobpersp") -> "text-perspective-or-transform-gated"
            name.containsAnyTextMesh("fontmgr", "fontmanager") -> "text-font-manager-gated"
            name.containsAnyTextMesh("fontcache", "glyphcache") -> "text-blob-gated"
            name.containsAnyTextMesh("fontregen", "fallback") -> "text-font-fallback-gated"
            name.containsAnyTextMesh("coloremoji", "emoji") -> "text-emoji-gated"
            name.containsAnyTextMesh("colrv1", "colrv0", "colorfont") -> "text-color-font-gated"
            name.containsAnyTextMesh("palette") -> "text-color-palette-gated"
            name.containsAnyTextMesh("textblob", "blob") -> "text-blob-gated"
            name.containsAnyTextMesh("annotated", "annotation") -> "text-annotation-gated"
            name.containsAnyTextMesh("bigtext", "largeglyph") -> "text-large-or-cache"
            else -> "text-basic-latin"
        }

    private fun meshSubfamily(name: String): String =
        when {
            name.containsAnyTextMesh("uniform") -> "mesh-custom-uniforms-gated"
            name.containsAnyTextMesh("effect") -> "mesh-effect-dependency-gated"
            name.containsAnyTextMesh("paintimage") -> "mesh-paint-image-dependency-gated"
            name.containsAnyTextMesh("paintcolor") -> "mesh-paint-color-dependency-gated"
            name.containsAnyTextMesh("image") -> "mesh-image-dependency-gated"
            name.containsAnyTextMesh("perspective", "persp") -> "mesh-perspective-gated"
            name.containsAnyTextMesh("updates", "dynamic") -> "mesh-update-or-dynamic-gated"
            name.containsAnyTextMesh("zeroinit", "zero") -> "mesh-zero-init-gated"
            name.containsAnyTextMesh("picture") -> "mesh-picture-dependency-gated"
            name.containsAnyTextMesh("colorspace", "cs") -> "mesh-color-space-gated"
            name.containsAnyTextMesh("custommesh") -> "mesh-custom-basic"
            else -> "mesh-basic-vertices"
        }

    private fun gatedReason(subfamily: String): String? =
        when (subfamily) {
            "text-rsxform-gated" -> "unsupported.text.rsxform"
            "text-perspective-or-transform-gated" -> "unsupported.text.perspective"
            "text-font-manager-gated" -> "unsupported.text.font_manager"
            "text-font-fallback-gated" -> "unsupported.text.font_fallback"
            "text-annotation-gated" -> "unsupported.text.annotation"
            "text-color-font-gated" -> "unsupported.text.color_font"
            "text-emoji-gated" -> "unsupported.text.emoji"
            "text-color-palette-gated" -> "unsupported.text.palette"
            "text-blob-gated" -> "unsupported.text.glyph_cache"
            "text-shader-or-gradient-gated" -> "unsupported.text.shader_or_gradient"
            "text-filter-or-blur-gated" -> "unsupported.text.filter_or_blur"
            "text-clip-interaction-gated" -> "unsupported.text.clip_interaction"
            "mesh-custom-uniforms-gated" -> "unsupported.mesh.custom_uniforms"
            "mesh-color-space-gated" -> "unsupported.mesh.color_space"
            "mesh-effect-dependency-gated" -> "unsupported.mesh.effect_dependency"
            "mesh-image-dependency-gated" -> "unsupported.mesh.image_dependency"
            "mesh-paint-color-dependency-gated" -> "unsupported.mesh.paint_color_dependency"
            "mesh-paint-image-dependency-gated" -> "unsupported.mesh.paint_image_dependency"
            "mesh-perspective-gated" -> "unsupported.mesh.perspective"
            "mesh-update-or-dynamic-gated" -> "unsupported.mesh.dynamic_updates"
            "mesh-zero-init-gated" -> "unsupported.mesh.zero_init"
            "mesh-picture-dependency-gated" -> "unsupported.mesh.picture_dependency"
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

    private fun List<GmDashboardRow>.withStableTextMeshRowIds(): List<Pair<String, GmDashboardRow>> {
        val seenCounts = linkedMapOf<String, Int>()
        return map { row ->
            val count = seenCounts.getOrDefault(row.name, 0) + 1
            seenCounts[row.name] = count
            val rowId = if (count == 1) row.name else "${row.name}#$count"
            rowId to row
        }
    }
}

private fun String.textMeshKey(): String =
    lowercase().filter { char -> char.isLetterOrDigit() }

private fun String.containsAnyTextMesh(vararg tokens: String): Boolean =
    tokens.any(this::contains)

object Phase6TextMeshFamilyEvidenceWriter {
    private val prettyJson = Json { prettyPrint = true }

    fun writeOutputs(root: Path, evidence: Phase6TextMeshFamiliesEvidence) {
        val evidencePath = root.resolve("reports/gpu-renderer/phase-6-text-mesh-families/evidence.json")
        val csvPath = root.resolve("reports/gpu-renderer/phase-6-text-mesh-families/classification.csv")
        val markdownPath = root.resolve("reports/gpu-renderer/2026-07-09-gpu-phase-6-text-mesh-families.md")
        Files.createDirectories(evidencePath.parent)
        Files.createDirectories(markdownPath.parent)
        Files.writeString(evidencePath, prettyJson.encodeToString(JsonObject.serializer(), evidence.toJsonObject()) + "\n")
        Files.writeString(csvPath, evidence.toCsv())
        val preservedValidation = readValidationSection(markdownPath)
        val markdown = buildString {
            append(evidence.toMarkdown().trimEnd())
            if (preservedValidation != null) {
                appendLine()
                appendLine()
                append(preservedValidation.trim())
            }
            appendLine()
        }
        Files.writeString(markdownPath, markdown)
    }

    private fun readValidationSection(markdownPath: Path): String? {
        if (!Files.isRegularFile(markdownPath)) return null
        val existing = Files.readString(markdownPath)
        val heading = "## Validation"
        val start = existing.indexOf(heading)
        if (start < 0) return null
        return existing.substring(start).trim()
    }
}

fun Phase6TextMeshFamiliesEvidence.toJsonObject(): JsonObject =
    buildJsonObject {
        put("schemaVersion", schemaVersion)
        put("generatedBy", generatedBy)
        put("dashboardSource", dashboardSource)
        if (sourceGeneratedAt == null) put("sourceGeneratedAt", JsonNull) else put("sourceGeneratedAt", sourceGeneratedAt)
        put("summary", summary.toJsonObject())
        put("nonClaims", buildJsonArray { nonClaims.forEach { add(it) } })
        put("followUpCandidates", buildJsonArray { followUpCandidates.forEach { add(it.toJsonObject()) } })
        put("rows", buildJsonArray { rows.forEach { add(it.toJsonObject()) } })
    }

private fun Phase6TextMeshSummary.toJsonObject(): JsonObject =
    buildJsonObject {
        put("totalRows", totalRows)
        put("families", families.toTextMeshCountJsonObject())
        put("familyDeltas", familyDeltas.toTextMeshFamilyDeltaJsonObject())
        put("classifications", classifications.toTextMeshCountJsonObject())
        put("subfamilies", subfamilies.toTextMeshCountJsonObject())
        put("promotedRows", promotedRows)
        put("unexpectedFails", unexpectedFails)
        put("noScore", noScore)
    }

private fun Phase6TextMeshRowEvidence.toJsonObject(): JsonObject =
    buildJsonObject {
        put("rowId", rowId)
        put("name", name)
        put("family", family)
        put("subfamily", subfamily)
        put("classification", classification)
        putNullableTextMeshDouble("similarity", similarity)
        putNullableTextMeshDouble("minSimilarity", minSimilarity)
        if (isPassing == null) put("isPassing", JsonNull) else put("isPassing", isPassing)
        putNullableTextMeshInt("width", width)
        putNullableTextMeshInt("height", height)
        putNullableTextMeshLong("matchingPixels", matchingPixels)
        putNullableTextMeshLong("totalPixels", totalPixels)
        putNullableTextMeshRgbaInt("maxDiff", maxDiff)
        putNullableTextMeshRgbaDouble("meanDiff", meanDiff)
        put("fallbackReason", fallbackReason)
        putNullableTextMeshString("referencePath", referencePath)
        putNullableTextMeshString("generatedPath", generatedPath)
        putNullableTextMeshString("diffPath", diffPath)
        putNullableTextMeshString("noScoreCause", noScoreCause)
        put("nonClaim", nonClaim)
    }

private fun Map<String, Phase6TextMeshFamilyDelta>.toTextMeshFamilyDeltaJsonObject(): JsonObject =
    buildJsonObject {
        entries.sortedBy { it.key }.forEach { (family, delta) ->
            put(family, delta.toJsonObject())
        }
    }

private fun Phase6TextMeshFamilyDelta.toJsonObject(): JsonObject =
    buildJsonObject {
        put("baselineSource", baselineSource)
        put("currentSource", currentSource)
        if (currentGeneratedAt == null) put("currentGeneratedAt", JsonNull) else put("currentGeneratedAt", currentGeneratedAt)
        put("baselineCount", baselineCount)
        put("currentCount", currentCount)
        put("delta", delta)
    }

private fun Phase6TextMeshFollowUpCandidate.toJsonObject(): JsonObject =
    buildJsonObject {
        put("rootCause", rootCause)
        put("classification", classification)
        put("rowCount", rowCount)
        put("sampleRows", buildJsonArray { sampleRows.forEach { add(it) } })
    }

private fun Map<String, Int>.toTextMeshCountJsonObject(): JsonObject =
    buildJsonObject {
        entries.sortedBy { it.key }.forEach { (key, value) -> put(key, value) }
    }

private fun JsonObjectBuilder.putNullableTextMeshString(key: String, value: String?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}

private fun JsonObjectBuilder.putNullableTextMeshDouble(key: String, value: Double?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}

private fun JsonObjectBuilder.putNullableTextMeshInt(key: String, value: Int?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}

private fun JsonObjectBuilder.putNullableTextMeshLong(key: String, value: Long?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}

private fun JsonObjectBuilder.putNullableTextMeshRgbaInt(key: String, value: GmRgbaInt?) {
    if (value == null) {
        put(key, JsonNull)
    } else {
        put(
            key,
            buildJsonObject {
                put("r", value.r)
                put("g", value.g)
                put("b", value.b)
                put("a", value.a)
            },
        )
    }
}

private fun JsonObjectBuilder.putNullableTextMeshRgbaDouble(key: String, value: GmRgbaDouble?) {
    if (value == null) {
        put(key, JsonNull)
    } else {
        put(
            key,
            buildJsonObject {
                put("r", value.r)
                put("g", value.g)
                put("b", value.b)
                put("a", value.a)
            },
        )
    }
}

fun Phase6TextMeshFamiliesEvidence.toCsv(): String =
    buildString {
        appendLine("rowId,name,family,subfamily,classification,similarity,minSimilarity,isPassing,width,height,matchingPixels,totalPixels,maxDiff,meanDiff,fallbackReason,referencePath,generatedPath,diffPath,noScoreCause")
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
                    row.isPassing?.toString().orEmpty(),
                    row.width?.toString().orEmpty(),
                    row.height?.toString().orEmpty(),
                    row.matchingPixels?.toString().orEmpty(),
                    row.totalPixels?.toString().orEmpty(),
                    row.maxDiff?.let { "${it.r}/${it.g}/${it.b}/${it.a}" }.orEmpty(),
                    row.meanDiff?.let { "${it.r}/${it.g}/${it.b}/${it.a}" }.orEmpty(),
                    row.fallbackReason,
                    row.referencePath.orEmpty(),
                    row.generatedPath.orEmpty(),
                    row.diffPath.orEmpty(),
                    row.noScoreCause.orEmpty(),
                ).joinToString(",") { it.textMeshCsvCell() },
            )
        }
    }

fun Phase6TextMeshFamiliesEvidence.toMarkdown(): String =
    buildString {
        appendLine("# GPU Phase 6 Text Mesh Families Evidence")
        appendLine()
        appendLine("## Summary")
        appendLine()
        appendLine("- Total TEXT + MESH rows: ${summary.totalRows}")
        appendLine("- Families: ${summary.families}")
        appendLine("- Classifications: ${summary.classifications}")
        appendLine("- Subfamilies: ${summary.subfamilies}")
        appendLine("- Promoted rows: ${summary.promotedRows}")
        appendLine("- Unexpected fails: ${summary.unexpectedFails}")
        appendLine("- No score: ${summary.noScore}")
        appendLine()
        appendLine("## Family Deltas")
        appendLine()
        appendLine("- Baseline source: `${summary.familyDeltas.values.firstOrNull()?.baselineSource ?: "2026-07-09 local dashboard before text-mesh-family wave"}`")
        appendLine("- Current dashboard: `${dashboardSource}` (${sourceGeneratedAt ?: "generatedAt unavailable"})")
        appendLine()
        appendLine("| Family | Baseline | Current | Delta |")
        appendLine("|---|---:|---:|---:|")
        summary.familyDeltas.entries.sortedBy { it.key }.forEach { (family, delta) ->
            appendLine("| `$family` | ${delta.baselineCount} | ${delta.currentCount} | ${delta.delta.toSignedTextMeshDelta()} |")
        }
        appendLine()
        appendLine("## Non-Claims")
        appendLine()
        nonClaims.forEach { appendLine("- $it") }
        appendLine()
        appendLine("## Reason Code Taxonomy")
        appendLine()
        appendLine("- Text/mesh `unsupported.text.*` and `unsupported.mesh.*` reason codes in this report are evidence refusal taxonomy only, not renderer route diagnostics unless separately attached.")
        appendLine()
        appendLine("## Follow-Up Candidates")
        appendLine()
        appendLine("| Root Cause | Classification | Rows | Samples |")
        appendLine("|---|---|---:|---|")
        followUpCandidates.forEach { candidate ->
            appendLine("| `${candidate.rootCause}` | `${candidate.classification}` | ${candidate.rowCount} | ${candidate.sampleRows.joinToString(", ") { "`$it`" }} |")
        }
        appendLine()
        appendLine("## Rows")
        appendLine()
        appendLine("| Row ID | Row | Family | Subfamily | Classification | Similarity | Fallback | No Score Cause |")
        appendLine("|---|---|---|---|---|---:|---|---|")
        rows.forEach { row ->
            val similarity = row.similarity?.let { "%.2f".format(Locale.US, it) } ?: "n/a"
            val noScoreCause = row.noScoreCause?.takeIf { it.isNotBlank() } ?: "n/a"
            appendLine("| `${row.rowId}` | `${row.name}` | `${row.family}` | `${row.subfamily}` | `${row.classification}` | $similarity | `${row.fallbackReason}` | `$noScoreCause` |")
        }
        appendLine()
        appendLine("## Regeneration Notes")
        appendLine()
        appendLine("- Run `:integration-tests:skia:generateSkiaDashboard` before generating text-mesh-family evidence.")
        appendLine("- Dashboard data is read from `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`.")
        appendLine("- Non-TEXT/MESH families and cross-family dependencies are intentionally out of this evidence wave.")
    }

private fun String.textMeshCsvCell(): String =
    if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + replace("\"", "\"\"") + "\""
    } else {
        this
    }

private fun Int.toSignedTextMeshDelta(): String =
    if (this >= 0) "+$this" else toString()

private fun List<Phase6TextMeshRowEvidence>.toTextMeshFollowUpCandidates(): List<Phase6TextMeshFollowUpCandidate> =
    filter { row -> row.classification != "instrumented-existing" }
        .groupBy { row -> row.followUpRootCause() to row.classification }
        .entries
        .map { (key, rows) ->
            Phase6TextMeshFollowUpCandidate(
                rootCause = key.first,
                classification = key.second,
                rowCount = rows.size,
                sampleRows = rows.map { it.rowId }.sorted().take(5),
            )
        }
        .sortedWith(compareBy<Phase6TextMeshFollowUpCandidate> { it.rootCause }.thenBy { it.classification })

private fun Phase6TextMeshRowEvidence.followUpRootCause(): String =
    when (classification) {
        "no-score" -> noScoreCause ?: "no-score.unknown"
        "unexpected-fail" -> "unexpected-fail.without-stable-refusal"
        else -> fallbackReason.takeUnless { it == "none" } ?: subfamily
    }
