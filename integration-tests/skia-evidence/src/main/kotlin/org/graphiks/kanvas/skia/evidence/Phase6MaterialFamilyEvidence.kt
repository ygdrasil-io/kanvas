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

data class Phase6MaterialFamiliesEvidence(
    val schemaVersion: String,
    val generatedBy: String,
    val dashboardSource: String,
    val sourceGeneratedAt: String?,
    val summary: Phase6MaterialSummary,
    val nonClaims: List<String>,
    val rows: List<Phase6MaterialRowEvidence>,
)

data class Phase6MaterialSummary(
    val totalRows: Int,
    val families: Map<String, Int>,
    val familyDeltas: Map<String, Phase6MaterialFamilyDelta>,
    val classifications: Map<String, Int>,
    val subfamilies: Map<String, Int>,
    val promotedRows: Int,
    val unexpectedFails: Int,
    val noScore: Int,
)

data class Phase6MaterialFamilyDelta(
    val baselineSource: String,
    val currentSource: String,
    val currentGeneratedAt: String?,
    val baselineCount: Int,
    val currentCount: Int,
    val delta: Int,
)

data class Phase6MaterialRowEvidence(
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

object Phase6MaterialFamilyClassifier {
    private const val baselineSource = "2026-07-09 local dashboard before material-family wave"
    private val materialFamilies = setOf("GRADIENT", "RUNTIME_EFFECT", "COLOR")
    private val baselineFamilyCounts = linkedMapOf("COLOR" to 20, "GRADIENT" to 56, "RUNTIME_EFFECT" to 25)

    fun classify(row: GmDashboardRow, rowId: String = row.name): Phase6MaterialRowEvidence {
        require(row.family in materialFamilies) { "Expected GRADIENT, RUNTIME_EFFECT, or COLOR row, got ${row.family}" }
        val subfamily = materialSubfamily(row)
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
                nonClaim = "No score is not counted as fail or support; material evidence is incomplete."
            }
            gatedReason != null -> {
                classification = "expected-unsupported"
                fallback = gatedReason
                noScoreCause = null
                nonClaim = "$subfamily remains outside Phase 6 material-family migration scope."
            }
            row.isPassing == true -> {
                classification = "instrumented-existing"
                fallback = "none"
                noScoreCause = null
                nonClaim = "${row.family} row remains instrumented until route and material diagnostics are attached."
            }
            else -> {
                classification = "unexpected-fail"
                fallback = "none"
                noScoreCause = null
                nonClaim = "Reference and generated image exist, but score is below threshold without a stable material refusal."
            }
        }

        return Phase6MaterialRowEvidence(
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

    fun buildEvidence(dashboard: GmDashboard): Phase6MaterialFamiliesEvidence {
        val rows = dashboard.rows
            .filter { row -> row.family in materialFamilies }
            .withStableMaterialRowIds()
            .map { (rowId, row) -> classify(row, rowId) }
        val classifications = rows.groupingBy { it.classification }.eachCount().toSortedMap()
        val subfamilies = rows.groupingBy { it.subfamily }.eachCount().toSortedMap()
        val families = rows.groupingBy { it.family }.eachCount().toSortedMap()
        val familyDeltas = baselineFamilyCounts.mapValues { (family, baselineCount) ->
            val currentCount = families[family] ?: 0
            Phase6MaterialFamilyDelta(
                baselineSource = baselineSource,
                currentSource = "integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json",
                currentGeneratedAt = dashboard.generatedAt,
                baselineCount = baselineCount,
                currentCount = currentCount,
                delta = currentCount - baselineCount,
            )
        }.toSortedMap()

        return Phase6MaterialFamiliesEvidence(
            schemaVersion = "phase6-material-families-v1",
            generatedBy = ":integration-tests:skia-evidence:generateGpuPhase6MaterialFamiliesEvidence",
            dashboardSource = "integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json",
            sourceGeneratedAt = dashboard.generatedAt,
            summary = Phase6MaterialSummary(
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
                "No broad shader support is claimed from classification alone.",
                "No dynamic SkSL compiler or arbitrary SkRuntimeEffect support is added.",
                "Blend composition, compose-shader pipelines, filter-graph, saveLayer, and destination-read rows remain outside this material-family wave.",
                "Rows without route and material diagnostics remain instrumented rather than promoted.",
            ),
            rows = rows,
        )
    }

    private fun materialSubfamily(row: GmDashboardRow): String {
        val name = row.name.materialKey()
        return when (row.family) {
            "GRADIENT" -> gradientSubfamily(name)
            "RUNTIME_EFFECT" -> runtimeEffectSubfamily(name)
            "COLOR" -> colorSubfamily(name)
            else -> error("Unexpected material family ${row.family}")
        }
    }

    private fun gradientSubfamily(name: String): String =
        when {
            name.containsAnyMaterial("manystops", "manycolors") -> "gradient-many-stops-gated"
            name.containsAnyMaterial("persp", "perspective") -> "gradient-perspective-gated"
            name.containsAnyMaterial("colorspace", "p3", "hue") -> "gradient-color-space-gated"
            name.containsAnyMaterial("tiling", "tilemode", "clamped", "scaledtiling") -> "gradient-tile-mode"
            name.containsAnyMaterial("matrix", "localmatrix") -> "gradient-local-matrix"
            name.containsAnyMaterial("hardstop", "hardstops") -> "gradient-hard-stops"
            name.contains("sweep") -> "gradient-sweep"
            name.contains("conical") -> "gradient-conical"
            name.contains("radial") -> "gradient-radial"
            else -> "gradient-linear"
        }

    private fun runtimeEffectSubfamily(name: String): String =
        when {
            name.containsAnyMaterial("childshader", "child") -> "runtime-effect-child-shader-gated"
            name.containsAnyMaterial("image", "surface") -> "runtime-effect-image-or-surface-gated"
            name.containsAnyMaterial("colorfilter", "colorfilterrt") -> "runtime-effect-color-filter"
            name.containsAnyMaterial("spiral", "runtimefunctions", "runtimeshader") -> "runtime-effect-unregistered-gated"
            else -> "runtime-effect-registered"
        }

    private fun colorSubfamily(name: String): String =
        when {
            name.containsAnyMaterial("colorspace", "p3", "srgb", "encode") -> "color-space-gated"
            name.containsAnyMaterial("filter", "colorfilter") -> "color-filter-gated"
            name.containsAnyMaterial("processor", "cube") -> "color-processor-gated"
            name.contains("alpha") -> "color-alpha"
            else -> "color-solid"
        }

    private fun gatedReason(subfamily: String): String? =
        when (subfamily) {
            "gradient-perspective-gated" -> "unsupported.material.perspective_shader"
            "gradient-color-space-gated" -> "unsupported.material.color_space"
            "gradient-many-stops-gated" -> "unsupported.material.gradient_many_stops"
            "gradient-tile-mode" -> "unsupported.material.gradient_tile_mode"
            "runtime-effect-unregistered-gated" -> "unsupported.runtime_effect.unregistered_descriptor"
            "runtime-effect-child-shader-gated" -> "unsupported.runtime_effect.child_shader"
            "runtime-effect-image-or-surface-gated" -> "unsupported.runtime_effect.image_or_surface_input"
            "color-space-gated" -> "unsupported.material.color_space"
            "color-filter-gated" -> "unsupported.material.color_filter"
            "color-processor-gated" -> "unsupported.material.color_processor"
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

    private fun List<GmDashboardRow>.withStableMaterialRowIds(): List<Pair<String, GmDashboardRow>> {
        val seenCounts = linkedMapOf<String, Int>()
        return map { row ->
            val count = seenCounts.getOrDefault(row.name, 0) + 1
            seenCounts[row.name] = count
            val rowId = if (count == 1) row.name else "${row.name}#$count"
            rowId to row
        }
    }
}

private fun String.materialKey(): String =
    lowercase().filter { char -> char.isLetterOrDigit() }

private fun String.containsAnyMaterial(vararg tokens: String): Boolean =
    tokens.any(this::contains)

object Phase6MaterialFamilyEvidenceWriter {
    private val prettyJson = Json { prettyPrint = true }

    fun writeOutputs(root: Path, evidence: Phase6MaterialFamiliesEvidence) {
        val evidencePath = root.resolve("reports/gpu-renderer/phase-6-material-families/evidence.json")
        val csvPath = root.resolve("reports/gpu-renderer/phase-6-material-families/classification.csv")
        val markdownPath = root.resolve("reports/gpu-renderer/2026-07-09-gpu-phase-6-material-families.md")
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

fun Phase6MaterialFamiliesEvidence.toJsonObject(): JsonObject =
    buildJsonObject {
        put("schemaVersion", schemaVersion)
        put("generatedBy", generatedBy)
        put("dashboardSource", dashboardSource)
        if (sourceGeneratedAt == null) put("sourceGeneratedAt", JsonNull) else put("sourceGeneratedAt", sourceGeneratedAt)
        put("summary", summary.toJsonObject())
        put("nonClaims", buildJsonArray { nonClaims.forEach { add(it) } })
        put("rows", buildJsonArray { rows.forEach { add(it.toJsonObject()) } })
    }

private fun Phase6MaterialSummary.toJsonObject(): JsonObject =
    buildJsonObject {
        put("totalRows", totalRows)
        put("families", families.toMaterialCountJsonObject())
        put("familyDeltas", familyDeltas.toMaterialFamilyDeltaJsonObject())
        put("classifications", classifications.toMaterialCountJsonObject())
        put("subfamilies", subfamilies.toMaterialCountJsonObject())
        put("promotedRows", promotedRows)
        put("unexpectedFails", unexpectedFails)
        put("noScore", noScore)
    }

private fun Phase6MaterialRowEvidence.toJsonObject(): JsonObject =
    buildJsonObject {
        put("rowId", rowId)
        put("name", name)
        put("family", family)
        put("subfamily", subfamily)
        put("classification", classification)
        putNullableMaterialDouble("similarity", similarity)
        putNullableMaterialDouble("minSimilarity", minSimilarity)
        if (isPassing == null) put("isPassing", JsonNull) else put("isPassing", isPassing)
        putNullableMaterialInt("width", width)
        putNullableMaterialInt("height", height)
        putNullableMaterialLong("matchingPixels", matchingPixels)
        putNullableMaterialLong("totalPixels", totalPixels)
        putNullableMaterialRgbaInt("maxDiff", maxDiff)
        putNullableMaterialRgbaDouble("meanDiff", meanDiff)
        put("fallbackReason", fallbackReason)
        putNullableMaterialString("referencePath", referencePath)
        putNullableMaterialString("generatedPath", generatedPath)
        putNullableMaterialString("diffPath", diffPath)
        putNullableMaterialString("noScoreCause", noScoreCause)
        put("nonClaim", nonClaim)
    }

private fun Map<String, Phase6MaterialFamilyDelta>.toMaterialFamilyDeltaJsonObject(): JsonObject =
    buildJsonObject {
        entries.sortedBy { it.key }.forEach { (family, delta) ->
            put(family, delta.toJsonObject())
        }
    }

private fun Phase6MaterialFamilyDelta.toJsonObject(): JsonObject =
    buildJsonObject {
        put("baselineSource", baselineSource)
        put("currentSource", currentSource)
        if (currentGeneratedAt == null) put("currentGeneratedAt", JsonNull) else put("currentGeneratedAt", currentGeneratedAt)
        put("baselineCount", baselineCount)
        put("currentCount", currentCount)
        put("delta", delta)
    }

private fun Map<String, Int>.toMaterialCountJsonObject(): JsonObject =
    buildJsonObject {
        entries.sortedBy { it.key }.forEach { (key, value) -> put(key, value) }
    }

private fun JsonObjectBuilder.putNullableMaterialString(key: String, value: String?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}

private fun JsonObjectBuilder.putNullableMaterialDouble(key: String, value: Double?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}

private fun JsonObjectBuilder.putNullableMaterialInt(key: String, value: Int?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}

private fun JsonObjectBuilder.putNullableMaterialLong(key: String, value: Long?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}

private fun JsonObjectBuilder.putNullableMaterialRgbaInt(key: String, value: GmRgbaInt?) {
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

private fun JsonObjectBuilder.putNullableMaterialRgbaDouble(key: String, value: GmRgbaDouble?) {
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

fun Phase6MaterialFamiliesEvidence.toCsv(): String =
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
                ).joinToString(",") { it.materialCsvCell() },
            )
        }
    }

fun Phase6MaterialFamiliesEvidence.toMarkdown(): String =
    buildString {
        appendLine("# GPU Phase 6 Material Families Evidence")
        appendLine()
        appendLine("## Summary")
        appendLine()
        appendLine("- Total GRADIENT + RUNTIME_EFFECT + COLOR rows: ${summary.totalRows}")
        appendLine("- Families: ${summary.families}")
        appendLine("- Classifications: ${summary.classifications}")
        appendLine("- Subfamilies: ${summary.subfamilies}")
        appendLine()
        appendLine("## Family Deltas")
        appendLine()
        appendLine("- Baseline source: `${summary.familyDeltas.values.firstOrNull()?.baselineSource ?: "2026-07-09 local dashboard before material-family wave"}`")
        appendLine("- Current dashboard: `${dashboardSource}` (${sourceGeneratedAt ?: "generatedAt unavailable"})")
        appendLine()
        appendLine("| Family | Baseline | Current | Delta |")
        appendLine("|---|---:|---:|---:|")
        summary.familyDeltas.entries.sortedBy { it.key }.forEach { (family, delta) ->
            appendLine("| `$family` | ${delta.baselineCount} | ${delta.currentCount} | ${delta.delta.toSignedMaterialDelta()} |")
        }
        appendLine()
        appendLine("## Non-Claims")
        appendLine()
        nonClaims.forEach { appendLine("- $it") }
        appendLine()
        appendLine("## Reason Code Taxonomy")
        appendLine()
        appendLine("- Material `unsupported.material.*` and `unsupported.runtime_effect.*` reason codes in this report are evidence refusal taxonomy only, not renderer route diagnostics unless separately attached.")
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
        appendLine("- Run `:integration-tests:skia:generateSkiaDashboard` before generating material-family evidence.")
        appendLine("- Dashboard data is read from `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`.")
        appendLine("- Non-material and composition/filter families are intentionally out of this material-family wave.")
    }

private fun String.materialCsvCell(): String =
    if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + replace("\"", "\"\"") + "\""
    } else {
        this
    }

private fun Int.toSignedMaterialDelta(): String = if (this >= 0) "+$this" else toString()
