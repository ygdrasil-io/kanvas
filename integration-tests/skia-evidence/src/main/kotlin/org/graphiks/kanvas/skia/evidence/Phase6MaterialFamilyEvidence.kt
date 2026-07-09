package org.graphiks.kanvas.skia.evidence

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
            row.isPassing == true -> {
                classification = "instrumented-existing"
                fallback = "none"
                noScoreCause = null
                nonClaim = "${row.family} row remains instrumented until route and material diagnostics are attached."
            }
            gatedReason != null -> {
                classification = "expected-unsupported"
                fallback = gatedReason
                noScoreCause = null
                nonClaim = "$subfamily remains outside Phase 6 material-family migration scope."
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
