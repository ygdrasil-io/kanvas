package org.graphiks.kanvas.skia.evidence

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

private fun String.coverageKey(): String =
    lowercase().filter { char -> char.isLetterOrDigit() }

private fun String.containsAnyCoverage(vararg tokens: String): Boolean =
    tokens.any(this::contains)
