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

data class Phase6EffectCompositionFamiliesEvidence(
    val schemaVersion: String,
    val generatedBy: String,
    val dashboardSource: String,
    val sourceGeneratedAt: String?,
    val summary: Phase6EffectCompositionSummary,
    val nonClaims: List<String>,
    val followUpCandidates: List<Phase6EffectCompositionFollowUpCandidate>,
    val rows: List<Phase6EffectCompositionRowEvidence>,
)

data class Phase6EffectCompositionSummary(
    val totalRows: Int,
    val families: Map<String, Int>,
    val familyDeltas: Map<String, Phase6EffectCompositionFamilyDelta>,
    val classifications: Map<String, Int>,
    val subfamilies: Map<String, Int>,
    val promotedRows: Int,
    val unexpectedFails: Int,
    val noScore: Int,
)

data class Phase6EffectCompositionFamilyDelta(
    val baselineSource: String,
    val currentSource: String,
    val currentGeneratedAt: String?,
    val baselineCount: Int,
    val currentCount: Int,
    val delta: Int,
)

data class Phase6EffectCompositionFollowUpCandidate(
    val rootCause: String,
    val classification: String,
    val rowCount: Int,
    val sampleRows: List<String>,
)

data class Phase6EffectCompositionRowEvidence(
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

object Phase6EffectCompositionFamilyClassifier {
    private const val baselineSource = "2026-07-09 local dashboard before effect-composition-family wave"
    private val effectCompositionFamilies = setOf("COMPOSITE", "BLUR")
    private val baselineFamilyCounts = linkedMapOf("BLUR" to 45, "COMPOSITE" to 113)

    fun classify(row: GmDashboardRow, rowId: String = row.name): Phase6EffectCompositionRowEvidence {
        require(row.family in effectCompositionFamilies) { "Expected COMPOSITE or BLUR row, got ${row.family}" }
        val subfamily = effectCompositionSubfamily(row)
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
                nonClaim = "No score is not counted as fail or support; effect/composition evidence is incomplete."
            }
            gatedReason != null -> {
                classification = "expected-unsupported"
                fallback = gatedReason
                noScoreCause = null
                nonClaim = "$subfamily remains outside Phase 6 effect/composition-family migration scope."
            }
            row.isPassing == true -> {
                classification = "instrumented-existing"
                fallback = "none"
                noScoreCause = null
                nonClaim = "${row.family} row remains instrumented until route and effect/composition diagnostics are attached."
            }
            else -> {
                classification = "unexpected-fail"
                fallback = "none"
                noScoreCause = null
                nonClaim = "Reference and generated image exist, but score is below threshold without a stable effect/composition refusal."
            }
        }

        return Phase6EffectCompositionRowEvidence(
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

    fun buildEvidence(dashboard: GmDashboard): Phase6EffectCompositionFamiliesEvidence {
        val rows = dashboard.rows
            .filter { row -> row.family in effectCompositionFamilies }
            .withStableEffectCompositionRowIds()
            .map { (rowId, row) -> classify(row, rowId) }
        val classifications = rows.groupingBy { it.classification }.eachCount().toSortedMap()
        val subfamilies = rows.groupingBy { it.subfamily }.eachCount().toSortedMap()
        val families = rows.groupingBy { it.family }.eachCount().toSortedMap()
        val familyDeltas = baselineFamilyCounts.mapValues { (family, baselineCount) ->
            val currentCount = families[family] ?: 0
            Phase6EffectCompositionFamilyDelta(
                baselineSource = baselineSource,
                currentSource = "integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json",
                currentGeneratedAt = dashboard.generatedAt,
                baselineCount = baselineCount,
                currentCount = currentCount,
                delta = currentCount - baselineCount,
            )
        }.toSortedMap()

        return Phase6EffectCompositionFamiliesEvidence(
            schemaVersion = "phase6-effect-composition-families-v1",
            generatedBy = ":integration-tests:skia-evidence:generateGpuPhase6EffectCompositionFamiliesEvidence",
            dashboardSource = "integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json",
            sourceGeneratedAt = dashboard.generatedAt,
            summary = Phase6EffectCompositionSummary(
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
                "No broad COMPOSITE or BLUR support is claimed from classification alone.",
                "saveLayer, destination-read, backdrop filters, image-filter DAGs, matrix convolution, and advanced blend chains remain outside this evidence wave unless row diagnostics prove a bounded route.",
                "Rows without route and effect/composition diagnostics remain instrumented rather than promoted.",
                "COLOR, TEXT, IMAGE, PATH, CLIP, MATERIAL, and MESH dependencies are not absorbed into this wave.",
            ),
            followUpCandidates = rows.toEffectCompositionFollowUpCandidates(),
            rows = rows,
        )
    }

    private fun effectCompositionSubfamily(row: GmDashboardRow): String {
        val name = row.name.effectCompositionKey()
        return when (row.family) {
            "COMPOSITE" -> compositeSubfamily(name)
            "BLUR" -> blurSubfamily(name)
            else -> error("Unexpected effect/composition family ${row.family}")
        }
    }

    private fun compositeSubfamily(name: String): String =
        when {
            name == "srcmode" -> "composite-src-over-basic"
            name.containsAnyEffectComposition("advancedblend", "advanced", "hsl", "luminosity", "overlay", "hardlight", "softlight", "colordodge", "colorburn") -> "composite-advanced-blend-gated"
            name.containsAnyEffectComposition("savelayer", "layer") -> "composite-save-layer-gated"
            name.containsAnyEffectComposition("backdrop") -> "composite-backdrop-gated"
            name.containsAnyEffectComposition("dstread", "readshuffle", "destinationread") -> "composite-destination-read-gated"
            name.containsAnyEffectComposition("imagefilter", "imagefilters", "filtergraph", "offsetimagefilter", "matriximagefilter", "localmatriximagefilter") -> "composite-image-filter-gated"
            name.containsAnyEffectComposition("atlas", "vertices", "patch") -> "composite-atlas-or-vertices-gated"
            name.containsAnyEffectComposition(
                "colorfilter",
                "color4blendcf",
                "color4shader",
                "colormatrix",
                "colorcomposefilter",
                "composecfif",
                "mixercf",
                "modecolorfilters",
                "tablecolorfilter",
            ) -> "composite-color-filter-gated"
            name.containsAnyEffectComposition("xfer", "mode", "porterduff", "srcmode", "aaxfer", "hairmodes", "lcdblend") -> "composite-porter-duff"
            name.containsAnyEffectComposition("overdraw") -> "composite-overdraw-diagnostic"
            name.containsAnyEffectComposition("bounds", "croprect") -> "composite-layer-bounds-gated"
            else -> "composite-src-over-basic"
        }

    private fun blurSubfamily(name: String): String =
        when {
            name.containsAnyEffectComposition("matrixconvolution") -> "blur-matrix-convolution-gated"
            name.containsAnyEffectComposition("text") -> "blur-text-dependent-gated"
            name.containsAnyEffectComposition("backdrop") -> "blur-backdrop-gated"
            name.containsAnyEffectComposition("fastslow", "imagefilter", "imagefilters", "filtergraph", "inversefillfilters", "inversewindingmodefilters") -> "blur-filter-graph-gated"
            name.containsAnyEffectComposition("bigsigma", "large", "biggest", "bigger") -> "blur-large-sigma-gated"
            name.containsAnyEffectComposition("matrix", "xform", "persp", "perspective") -> "blur-transform-or-perspective-gated"
            name.containsAnyEffectComposition("clip", "clipped") -> "blur-clip-interaction-gated"
            name.containsAnyEffectComposition("image", "drawimage") -> "blur-image-basic"
            name.containsAnyEffectComposition("small", "sigma") -> "blur-small-sigma"
            name.containsAnyEffectComposition("rect", "rrect", "circle") -> "blur-rect-rrect-circle"
            name.containsAnyEffectComposition("resource", "texture", "hdr", "pip") -> "blur-resource-budget-gated"
            else -> "blur-mask-basic"
        }

    private fun gatedReason(subfamily: String): String? =
        when (subfamily) {
            "composite-advanced-blend-gated" -> "unsupported.composition.advanced_blend"
            "composite-xfermode-gated" -> "unsupported.composition.xfermode"
            "composite-save-layer-gated" -> "unsupported.composition.save_layer"
            "composite-backdrop-gated" -> "unsupported.composition.backdrop_filter"
            "composite-destination-read-gated" -> "unsupported.composition.destination_read"
            "composite-image-filter-gated" -> "unsupported.composition.image_filter_dag"
            "composite-atlas-or-vertices-gated" -> "unsupported.composition.atlas_or_vertices"
            "composite-color-filter-gated" -> "unsupported.composition.color_dependency"
            "composite-layer-bounds-gated" -> "unsupported.composition.layer_bounds"
            "blur-large-sigma-gated" -> "unsupported.blur.large_sigma"
            "blur-transform-or-perspective-gated" -> "unsupported.blur.transform_or_perspective"
            "blur-clip-interaction-gated" -> "unsupported.blur.clip_interaction"
            "blur-filter-graph-gated" -> "unsupported.blur.image_filter_graph"
            "blur-image-filter-gated" -> "unsupported.blur.image_filter_graph"
            "blur-matrix-convolution-gated" -> "unsupported.blur.matrix_convolution"
            "blur-backdrop-gated" -> "unsupported.blur.backdrop"
            "blur-text-dependent-gated" -> "unsupported.blur.text_dependency"
            "blur-resource-budget-gated" -> "unsupported.blur.resource_budget"
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

    private fun List<GmDashboardRow>.withStableEffectCompositionRowIds(): List<Pair<String, GmDashboardRow>> {
        val seenCounts = linkedMapOf<String, Int>()
        return map { row ->
            val count = seenCounts.getOrDefault(row.name, 0) + 1
            seenCounts[row.name] = count
            val rowId = if (count == 1) row.name else "${row.name}#$count"
            rowId to row
        }
    }
}

private fun String.effectCompositionKey(): String =
    lowercase().filter { char -> char.isLetterOrDigit() }

private fun String.containsAnyEffectComposition(vararg tokens: String): Boolean =
    tokens.any(this::contains)

object Phase6EffectCompositionFamilyEvidenceWriter {
    private val prettyJson = Json { prettyPrint = true }

    fun writeOutputs(root: Path, evidence: Phase6EffectCompositionFamiliesEvidence) {
        val evidencePath = root.resolve("reports/gpu-renderer/phase-6-effect-composition-families/evidence.json")
        val csvPath = root.resolve("reports/gpu-renderer/phase-6-effect-composition-families/classification.csv")
        val markdownPath = root.resolve("reports/gpu-renderer/2026-07-09-gpu-phase-6-effect-composition-families.md")
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

fun Phase6EffectCompositionFamiliesEvidence.toJsonObject(): JsonObject =
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

private fun Phase6EffectCompositionSummary.toJsonObject(): JsonObject =
    buildJsonObject {
        put("totalRows", totalRows)
        put("families", families.toEffectCompositionCountJsonObject())
        put("familyDeltas", familyDeltas.toEffectCompositionFamilyDeltaJsonObject())
        put("classifications", classifications.toEffectCompositionCountJsonObject())
        put("subfamilies", subfamilies.toEffectCompositionCountJsonObject())
        put("promotedRows", promotedRows)
        put("unexpectedFails", unexpectedFails)
        put("noScore", noScore)
    }

private fun Phase6EffectCompositionRowEvidence.toJsonObject(): JsonObject =
    buildJsonObject {
        put("rowId", rowId)
        put("name", name)
        put("family", family)
        put("subfamily", subfamily)
        put("classification", classification)
        putNullableEffectCompositionDouble("similarity", similarity)
        putNullableEffectCompositionDouble("minSimilarity", minSimilarity)
        if (isPassing == null) put("isPassing", JsonNull) else put("isPassing", isPassing)
        putNullableEffectCompositionInt("width", width)
        putNullableEffectCompositionInt("height", height)
        putNullableEffectCompositionLong("matchingPixels", matchingPixels)
        putNullableEffectCompositionLong("totalPixels", totalPixels)
        putNullableEffectCompositionRgbaInt("maxDiff", maxDiff)
        putNullableEffectCompositionRgbaDouble("meanDiff", meanDiff)
        put("fallbackReason", fallbackReason)
        putNullableEffectCompositionString("referencePath", referencePath)
        putNullableEffectCompositionString("generatedPath", generatedPath)
        putNullableEffectCompositionString("diffPath", diffPath)
        putNullableEffectCompositionString("noScoreCause", noScoreCause)
        put("nonClaim", nonClaim)
    }

private fun Map<String, Phase6EffectCompositionFamilyDelta>.toEffectCompositionFamilyDeltaJsonObject(): JsonObject =
    buildJsonObject {
        entries.sortedBy { it.key }.forEach { (family, delta) ->
            put(family, delta.toJsonObject())
        }
    }

private fun Phase6EffectCompositionFamilyDelta.toJsonObject(): JsonObject =
    buildJsonObject {
        put("baselineSource", baselineSource)
        put("currentSource", currentSource)
        if (currentGeneratedAt == null) put("currentGeneratedAt", JsonNull) else put("currentGeneratedAt", currentGeneratedAt)
        put("baselineCount", baselineCount)
        put("currentCount", currentCount)
        put("delta", delta)
    }

private fun Phase6EffectCompositionFollowUpCandidate.toJsonObject(): JsonObject =
    buildJsonObject {
        put("rootCause", rootCause)
        put("classification", classification)
        put("rowCount", rowCount)
        put("sampleRows", buildJsonArray { sampleRows.forEach { add(it) } })
    }

private fun Map<String, Int>.toEffectCompositionCountJsonObject(): JsonObject =
    buildJsonObject {
        entries.sortedBy { it.key }.forEach { (key, value) -> put(key, value) }
    }

private fun JsonObjectBuilder.putNullableEffectCompositionString(key: String, value: String?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}

private fun JsonObjectBuilder.putNullableEffectCompositionDouble(key: String, value: Double?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}

private fun JsonObjectBuilder.putNullableEffectCompositionInt(key: String, value: Int?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}

private fun JsonObjectBuilder.putNullableEffectCompositionLong(key: String, value: Long?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}

private fun JsonObjectBuilder.putNullableEffectCompositionRgbaInt(key: String, value: GmRgbaInt?) {
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

private fun JsonObjectBuilder.putNullableEffectCompositionRgbaDouble(key: String, value: GmRgbaDouble?) {
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

fun Phase6EffectCompositionFamiliesEvidence.toCsv(): String =
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
                ).joinToString(",") { it.effectCompositionCsvCell() },
            )
        }
    }

fun Phase6EffectCompositionFamiliesEvidence.toMarkdown(): String =
    buildString {
        appendLine("# GPU Phase 6 Effect Composition Families Evidence")
        appendLine()
        appendLine("## Summary")
        appendLine()
        appendLine("- Total COMPOSITE + BLUR rows: ${summary.totalRows}")
        appendLine("- Families: ${summary.families}")
        appendLine("- Classifications: ${summary.classifications}")
        appendLine("- Subfamilies: ${summary.subfamilies}")
        appendLine("- Promoted rows: ${summary.promotedRows}")
        appendLine("- Unexpected fails: ${summary.unexpectedFails}")
        appendLine("- No score: ${summary.noScore}")
        appendLine()
        appendLine("## Family Deltas")
        appendLine()
        appendLine("- Baseline source: `${summary.familyDeltas.values.firstOrNull()?.baselineSource ?: "2026-07-09 local dashboard before effect-composition-family wave"}`")
        appendLine("- Current dashboard: `${dashboardSource}` (${sourceGeneratedAt ?: "generatedAt unavailable"})")
        appendLine()
        appendLine("| Family | Baseline | Current | Delta |")
        appendLine("|---|---:|---:|---:|")
        summary.familyDeltas.entries.sortedBy { it.key }.forEach { (family, delta) ->
            appendLine("| `$family` | ${delta.baselineCount} | ${delta.currentCount} | ${delta.delta.toSignedEffectCompositionDelta()} |")
        }
        appendLine()
        appendLine("## Non-Claims")
        appendLine()
        nonClaims.forEach { appendLine("- $it") }
        appendLine()
        appendLine("## Reason Code Taxonomy")
        appendLine()
        appendLine("- Effect/composition `unsupported.composition.*` and `unsupported.blur.*` reason codes in this report are evidence refusal taxonomy only, not renderer route diagnostics unless separately attached.")
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
        appendLine("- Run `:integration-tests:skia:generateSkiaDashboard` before generating effect/composition-family evidence.")
        appendLine("- Dashboard data is read from `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`.")
        appendLine("- Non-effect/composition families and cross-family dependencies are intentionally out of this evidence wave.")
    }

private fun String.effectCompositionCsvCell(): String =
    if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + replace("\"", "\"\"") + "\""
    } else {
        this
    }

private fun Int.toSignedEffectCompositionDelta(): String = if (this >= 0) "+$this" else toString()

private fun List<Phase6EffectCompositionRowEvidence>.toEffectCompositionFollowUpCandidates(): List<Phase6EffectCompositionFollowUpCandidate> =
    filter { row -> row.classification != "instrumented-existing" }
        .groupBy { row -> row.followUpRootCause() to row.classification }
        .entries
        .map { (key, rows) ->
            Phase6EffectCompositionFollowUpCandidate(
                rootCause = key.first,
                classification = key.second,
                rowCount = rows.size,
                sampleRows = rows.map { it.rowId }.sorted().take(5),
            )
        }
        .sortedWith(compareBy<Phase6EffectCompositionFollowUpCandidate> { it.rootCause }.thenBy { it.classification })

private fun Phase6EffectCompositionRowEvidence.followUpRootCause(): String =
    when (classification) {
        "no-score" -> noScoreCause ?: "no-score.unknown"
        "unexpected-fail" -> "unexpected-fail.without-stable-refusal"
        else -> fallbackReason.takeUnless { it == "none" } ?: subfamily
    }
