package org.graphiks.kanvas.skia.evidence

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class GmDashboard(
    val generatedAt: String?,
    val rows: List<GmDashboardRow>,
)

data class GmDashboardRow(
    val name: String,
    val family: String,
    val similarity: Double?,
    val minSimilarity: Double?,
    val isPassing: Boolean?,
    val noReference: Boolean,
    val renderFailed: Boolean,
    val sizeMismatch: Boolean,
    val hasDiff: Boolean,
)

data class ResourceEvidence(
    val rowId: String,
    val dumpLines: List<String>,
    val nonClaims: List<String>,
)

data class Phase6ImageFamilyEvidence(
    val schemaVersion: String,
    val generatedBy: String,
    val dashboardSource: String,
    val sourceGeneratedAt: String?,
    val summary: Phase6ImageSummary,
    val resourceEvidence: ResourceEvidence?,
    val nonClaims: List<String>,
    val rows: List<Phase6ImageRowEvidence>,
)

data class Phase6ImageSummary(
    val totalImageRows: Int,
    val classifications: Map<String, Int>,
    val subfamilies: Map<String, Int>,
    val promotedRows: Int,
    val unexpectedFails: Int,
    val noScore: Int,
)

data class Phase6ImageRowEvidence(
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

object Phase6ImageFamilyClassifier {
    private val unsupportedRules = listOf(
        UnsupportedRule(setOf("animated", "gif", "video", "codec", "encode"), "animation-gated", "dependency.image.codec.unregistered"),
        UnsupportedRule(setOf("yuv"), "yuv-gated", "unsupported.color.yuv_conversion"),
        UnsupportedRule(setOf("mip", "mipmap"), "mipmap-gated", "unsupported.image.mipmap_budget_exceeded"),
        UnsupportedRule(setOf("persp", "perspective"), "perspective-gated", "unsupported.transform.perspective_route_rejected"),
        UnsupportedRule(setOf("filter", "blur", "morphology", "magnifier"), "image-filter-gated", "unsupported.filter.node_unimplemented"),
        UnsupportedRule(setOf("colorspace", "p3", "outofgamut", "working"), "color-management-gated", "unsupported.color.image_profile_conversion"),
        UnsupportedRule(setOf("readpixels", "snap", "surface"), "readpixels-or-snapshot-gated", "unsupported.destination_read.strategy_unaccepted"),
    )

    fun classify(
        row: GmDashboardRow,
        rowId: String = row.name,
    ): Phase6ImageRowEvidence {
        require(row.family == "IMAGE") { "Expected IMAGE row, got ${row.family}" }
        val subfamily = imageSubfamily(row)
        val reason = expectedUnsupportedReason(row)
        val noScore = row.similarity == null || row.noReference || row.renderFailed || row.sizeMismatch

        val classification: String
        val fallback: String
        val nonClaim: String
        val noScoreCause: String?
        when {
            noScore -> {
                classification = "no-score"
                fallback = reason ?: "none"
                noScoreCause = noScoreCause(row)
                nonClaim = "No score is not counted as fail or support; reference/render/size/comparison evidence is incomplete."
            }
            reason != null -> {
                classification = "expected-unsupported"
                fallback = reason
                noScoreCause = null
                nonClaim = "$subfamily remains outside Phase 6 IMAGE migration scope."
            }
            row.isPassing == true -> {
                classification = "instrumented-existing"
                fallback = "none"
                noScoreCause = null
                nonClaim = "Rendered row remains instrumented until route/cache/batching evidence missing from dashboard is attached."
            }
            else -> {
                classification = "unexpected-fail"
                fallback = "none"
                noScoreCause = null
                nonClaim = "Reference and generated image exist, but score is below threshold without a stable refusal."
            }
        }

        return Phase6ImageRowEvidence(
            rowId = rowId,
            name = row.name,
            family = "IMAGE",
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

    fun buildEvidence(
        dashboard: GmDashboard,
        resourceEvidence: ResourceEvidence? = null,
    ): Phase6ImageFamilyEvidence {
        val imageRows = dashboard.rows
            .filter { row -> row.family == "IMAGE" }
        val rows = imageRows
            .withStableRowIds()
            .map { (rowId, row) -> classify(row, rowId) }
        val classifications = rows.groupingBy { row -> row.classification }.eachCount().toSortedMap()
        val subfamilies = rows.groupingBy { row -> row.subfamily }.eachCount().toSortedMap()
        return Phase6ImageFamilyEvidence(
            schemaVersion = "phase6-image-family-v1",
            generatedBy = ":integration-tests:skia-evidence:generateGpuPhase6ImageFamilyEvidence",
            dashboardSource = "integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json",
            sourceGeneratedAt = dashboard.generatedAt,
            summary = Phase6ImageSummary(
                totalImageRows = rows.size,
                classifications = classifications,
                subfamilies = subfamilies,
                promotedRows = classifications["promoted-support"] ?: 0,
                unexpectedFails = classifications["unexpected-fail"] ?: 0,
                noScore = classifications["no-score"] ?: 0,
            ),
            resourceEvidence = resourceEvidence,
            nonClaims = listOf(
                "No broad IMAGE support is claimed from classification alone.",
                "No codec, animation, mipmap, YUV, perspective, image-filter, picture-shader, or broad color-management support is added.",
                "CPU-oracle rows do not count as Skia-comparable fidelity.",
            ),
            rows = rows,
        )
    }

    private fun List<GmDashboardRow>.withStableRowIds(): List<Pair<String, GmDashboardRow>> {
        val seenCounts = linkedMapOf<String, Int>()
        return map { row ->
            val count = seenCounts.getOrDefault(row.name, 0) + 1
            seenCounts[row.name] = count
            val rowId = if (count == 1) row.name else "${row.name}#$count"
            rowId to row
        }
    }

    private fun imageSubfamily(row: GmDashboardRow): String {
        val name = row.name.lowercase()
        unsupportedRules.firstOrNull { rule -> rule.tokens.any(name::contains) }?.let { return it.subfamily }
        return when {
            "drawbitmaprect" in name || "bitmaprect" in name || "drawmini" in name -> "simple-image-rect"
            "localmatrix" in name -> "local-matrix-affine"
            "shader" in name || "bitmapshader" in name -> "bitmap-shader-affine"
            "sampling" in name || "nearest" in name || "linear" in name -> "strict-nearest-linear"
            "tile" in name -> "sampler-policy-candidate"
            else -> "texture-cache-candidate"
        }
    }

    private fun expectedUnsupportedReason(row: GmDashboardRow): String? {
        val name = row.name.lowercase()
        return unsupportedRules.firstOrNull { rule -> rule.tokens.any(name::contains) }?.reason
    }

    private fun noScoreCause(row: GmDashboardRow): String =
        when {
            row.noReference -> "reference-missing"
            row.renderFailed -> "generated-render-missing"
            row.sizeMismatch -> "size-mismatch"
            row.similarity == null -> "comparison-unavailable"
            else -> "none"
        }
}

private data class UnsupportedRule(
    val tokens: Set<String>,
    val subfamily: String,
    val reason: String,
)

object GmDashboardJsonReader {
    private val json = Json { ignoreUnknownKeys = true }

    fun read(path: Path): GmDashboard {
        val root = json.parseToJsonElement(Files.readString(path)).jsonObject
        val rows = root["gms"]?.jsonArray.orEmpty().map { element -> parseRow(element.jsonObject) }
        return GmDashboard(
            generatedAt = root["generatedAt"]?.jsonPrimitive?.contentOrNull,
            rows = rows,
        )
    }

    private fun parseRow(row: JsonObject): GmDashboardRow =
        GmDashboardRow(
            name = row.string("name") ?: error("GM dashboard row missing name"),
            family = row.string("family") ?: error("GM dashboard row missing family"),
            similarity = row.double("similarity"),
            minSimilarity = row.double("minSimilarity"),
            isPassing = row.boolean("isPassing"),
            noReference = row.boolean("noReference") ?: false,
            renderFailed = row.boolean("renderFailed") ?: false,
            sizeMismatch = row.boolean("sizeMismatch") ?: false,
            hasDiff = row.boolean("hasDiff") ?: false,
        )
}

internal fun JsonObject.string(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

internal fun JsonObject.double(key: String): Double? =
    this[key]?.jsonPrimitive?.doubleOrNull

internal fun JsonObject.boolean(key: String): Boolean? =
    this[key]?.jsonPrimitive?.booleanOrNull

internal fun JsonObject.stringArray(key: String): List<String> =
    (this[key] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { element -> element.jsonPrimitive.contentOrNull }

object ResourceEvidenceReader {
    private val json = Json { ignoreUnknownKeys = true }
    private val resourceEvidencePath = Path.of("reports/gpu-renderer/phase-6-image-family/resource-evidence.json")

    fun readIfPresent(root: Path): ResourceEvidence? {
        val path = root.resolve(resourceEvidencePath)
        if (!Files.isRegularFile(path)) return null
        val obj = json.parseToJsonElement(Files.readString(path)).jsonObject
        return ResourceEvidence(
            rowId = obj.string("rowId") ?: error("resource evidence missing rowId"),
            dumpLines = obj.stringArray("dumpLines"),
            nonClaims = obj.stringArray("nonClaims"),
        )
    }
}

object Phase6ImageFamilyEvidenceWriter {
    private val prettyJson = Json { prettyPrint = true }

    fun writeOutputs(root: Path, evidence: Phase6ImageFamilyEvidence) {
        val evidencePath = root.resolve("reports/gpu-renderer/phase-6-image-family/evidence.json")
        val csvPath = root.resolve("reports/gpu-renderer/phase-6-image-family/classification.csv")
        val markdownPath = root.resolve("reports/gpu-renderer/2026-07-08-gpu-phase-6-image-family.md")
        Files.createDirectories(evidencePath.parent)
        Files.createDirectories(markdownPath.parent)
        Files.writeString(evidencePath, prettyJson.encodeToString(JsonObject.serializer(), evidence.toJsonObject()) + "\n")
        Files.writeString(csvPath, evidence.toCsv())
        Files.writeString(markdownPath, evidence.toMarkdown())
    }
}

fun Phase6ImageFamilyEvidence.toJsonObject(): JsonObject =
    buildJsonObject {
        put("schemaVersion", schemaVersion)
        put("generatedBy", generatedBy)
        put("dashboardSource", dashboardSource)
        if (sourceGeneratedAt == null) put("sourceGeneratedAt", JsonNull) else put("sourceGeneratedAt", sourceGeneratedAt)
        put("summary", summary.toJsonObject())
        if (resourceEvidence == null) put("resourceEvidence", JsonNull) else put("resourceEvidence", resourceEvidence.toJsonObject())
        put("nonClaims", buildJsonArray { nonClaims.forEach { add(it) } })
        put("rows", buildJsonArray { rows.forEach { add(it.toJsonObject()) } })
    }

private fun Phase6ImageSummary.toJsonObject(): JsonObject =
    buildJsonObject {
        put("totalImageRows", totalImageRows)
        put("classifications", classifications.toCountJsonObject())
        put("subfamilies", subfamilies.toCountJsonObject())
        put("promotedRows", promotedRows)
        put("unexpectedFails", unexpectedFails)
        put("noScore", noScore)
    }

private fun ResourceEvidence.toJsonObject(): JsonObject =
    buildJsonObject {
        put("rowId", rowId)
        put("dumpLines", buildJsonArray { dumpLines.forEach { add(it) } })
        put("nonClaims", buildJsonArray { nonClaims.forEach { add(it) } })
    }

private fun Phase6ImageRowEvidence.toJsonObject(): JsonObject =
    buildJsonObject {
        put("rowId", rowId)
        put("name", name)
        put("family", family)
        put("subfamily", subfamily)
        put("classification", classification)
        putNullableDouble("similarity", similarity)
        putNullableDouble("minSimilarity", minSimilarity)
        if (isPassing == null) put("isPassing", JsonNull) else put("isPassing", isPassing)
        put("fallbackReason", fallbackReason)
        putNullableString("referencePath", referencePath)
        putNullableString("generatedPath", generatedPath)
        putNullableString("diffPath", diffPath)
        putNullableString("noScoreCause", noScoreCause)
        put("nonClaim", nonClaim)
    }

private fun Map<String, Int>.toCountJsonObject(): JsonObject =
    buildJsonObject {
        entries.sortedBy { it.key }.forEach { (key, value) -> put(key, value) }
    }

private fun JsonObjectBuilder.putNullableString(key: String, value: String?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}

private fun JsonObjectBuilder.putNullableDouble(key: String, value: Double?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}

fun Phase6ImageFamilyEvidence.toCsv(): String =
    buildString {
        appendLine("rowId,name,subfamily,classification,similarity,minSimilarity,fallbackReason,noScoreCause")
        rows.forEach { row ->
            appendLine(
                listOf(
                    row.rowId,
                    row.name,
                    row.subfamily,
                    row.classification,
                    row.similarity?.toString().orEmpty(),
                    row.minSimilarity?.toString().orEmpty(),
                    row.fallbackReason,
                    row.noScoreCause.orEmpty(),
                ).joinToString(",") { it.csvCell() },
            )
        }
    }

fun Phase6ImageFamilyEvidence.toMarkdown(): String =
    buildString {
        appendLine("# GPU Phase 6 IMAGE Family Evidence")
        appendLine()
        appendLine("## Summary")
        appendLine()
        appendLine("- Total IMAGE rows: ${summary.totalImageRows}")
        appendLine("- Classifications: ${summary.classifications}")
        appendLine("- Subfamilies: ${summary.subfamilies}")
        appendLine()
        appendLine("## Non-Claims")
        appendLine()
        nonClaims.forEach { appendLine("- $it") }
        resourceEvidence?.let { resource ->
            appendLine()
            appendLine("## Resource And Cache Evidence")
            appendLine()
            appendLine("- Row id: `${resource.rowId}`")
            resource.dumpLines.forEach { appendLine("- `$it`") }
            resource.nonClaims.forEach { appendLine("- `$it`") }
        }
        appendLine()
        appendLine("## Rows")
        appendLine()
        appendLine("| Row ID | Row | Subfamily | Classification | Similarity | Fallback |")
        appendLine("|---|---|---|---|---:|---|")
        rows.forEach { row ->
            val similarity = row.similarity?.let { "%.2f".format(java.util.Locale.US, it) } ?: "n/a"
            appendLine("| `${row.rowId}` | `${row.name}` | `${row.subfamily}` | `${row.classification}` | $similarity | `${row.fallbackReason}` |")
        }
    }

private fun String.csvCell(): String =
    if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + replace("\"", "\"\"") + "\""
    } else {
        this
    }
