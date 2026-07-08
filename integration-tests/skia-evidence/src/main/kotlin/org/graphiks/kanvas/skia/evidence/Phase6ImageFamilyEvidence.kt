package org.graphiks.kanvas.skia.evidence

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
        UnsupportedRule(setOf("yuv"), "yuv-gated", "unsupported.image.yuv_color_space"),
        UnsupportedRule(setOf("mip", "mipmap"), "mipmap-gated", "unsupported.image.mipmap_budget_exceeded"),
        UnsupportedRule(setOf("persp", "perspective"), "perspective-gated", "unsupported.transform.perspective_route_rejected"),
        UnsupportedRule(setOf("filter", "blur", "morphology", "magnifier"), "image-filter-gated", "unsupported.filter.node_unimplemented"),
        UnsupportedRule(setOf("colorspace", "p3", "outofgamut", "working"), "color-management-gated", "unsupported.color.image_profile_conversion"),
        UnsupportedRule(setOf("readpixels", "snap", "surface"), "readpixels-or-snapshot-gated", "unsupported.destination_read.strategy_unaccepted"),
    )

    fun classify(row: GmDashboardRow): Phase6ImageRowEvidence {
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
        val rows = dashboard.rows
            .filter { row -> row.family == "IMAGE" }
            .map(::classify)
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
