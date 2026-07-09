package org.graphiks.kanvas.skia.evidence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertContains

class Phase6MaterialFamilyEvidenceTest {
    @Test
    fun `classifies gradient subfamilies`() {
        assertEquals("gradient-linear", classify("linear_gradient", "GRADIENT").subfamily)
        assertEquals("gradient-radial", classify("radial_gradient3", "GRADIENT").subfamily)
        assertEquals("gradient-sweep", classify("rgbw_sweep_gradient", "GRADIENT").subfamily)
        assertEquals("gradient-conical", classify("conical_gradients", "GRADIENT").subfamily)
        assertEquals("gradient-hard-stops", classify("hardstop_gradients_many", "GRADIENT").subfamily)
        assertEquals("gradient-local-matrix", classify("gradient_matrix", "GRADIENT").subfamily)
    }

    @Test
    fun `classifies material gates with stable reasons`() {
        val perspective = classify("gradients_view_perspective", "GRADIENT", similarity = 20.0, isPassing = false)
        val manyStops = classify("gradients_color_space_many_stops", "GRADIENT", similarity = 20.0, isPassing = false)
        val colorSpace = classify("p3ovals", "COLOR", similarity = 20.0, isPassing = false)

        assertEquals("gradient-perspective-gated", perspective.subfamily)
        assertEquals("unsupported.material.perspective_shader", perspective.fallbackReason)
        assertEquals("expected-unsupported", perspective.classification)
        assertEquals("gradient-many-stops-gated", manyStops.subfamily)
        assertEquals("unsupported.material.gradient_many_stops", manyStops.fallbackReason)
        assertEquals("color-space-gated", colorSpace.subfamily)
        assertEquals("unsupported.material.color_space", colorSpace.fallbackReason)
    }

    @Test
    fun `classifies passing gated gradient rows as expected-unsupported`() {
        val perspective = classify("gradients_view_perspective", "GRADIENT", similarity = 100.0, isPassing = true)

        assertEquals("gradient-perspective-gated", perspective.subfamily)
        assertEquals("expected-unsupported", perspective.classification)
        assertEquals("unsupported.material.perspective_shader", perspective.fallbackReason)
    }

    @Test
    fun `classifies runtime effects and refusals`() {
        val registered = classify("linear_gradient_rt", "RUNTIME_EFFECT")
        val missing = classify("spiral_rt", "RUNTIME_EFFECT", similarity = 10.0, isPassing = false)
        val child = classify("runtime_shader_child_shader", "RUNTIME_EFFECT", similarity = 10.0, isPassing = false)
        val imageInput = classify("runtime_shader_image_surface", "RUNTIME_EFFECT", similarity = 10.0, isPassing = false)

        assertEquals("runtime-effect-registered", registered.subfamily)
        assertEquals("instrumented-existing", registered.classification)
        assertEquals("runtime-effect-unregistered-gated", missing.subfamily)
        assertEquals("unsupported.runtime_effect.unregistered_descriptor", missing.fallbackReason)
        assertEquals("runtime-effect-child-shader-gated", child.subfamily)
        assertEquals("unsupported.runtime_effect.child_shader", child.fallbackReason)
        assertEquals("runtime-effect-image-or-surface-gated", imageInput.subfamily)
        assertEquals("unsupported.runtime_effect.image_or_surface_input", imageInput.fallbackReason)
    }

    @Test
    fun `classifies color rows`() {
        assertEquals("color-solid", classify("color", "COLOR").subfamily)
        assertEquals("color-alpha", classify("paint_alpha_normals_rt", "COLOR").subfamily)
        assertEquals("color-filter-gated", classify("filter", "COLOR", similarity = 20.0, isPassing = false).subfamily)
        assertEquals("color-processor-gated", classify("const_color_processor", "COLOR", similarity = 20.0, isPassing = false).subfamily)
    }

    @Test
    fun `separates no score from unexpected fail`() {
        val noScore = classify("missing_gradient_reference", "GRADIENT", similarity = null, isPassing = null, noReference = true)
        val fail = classify("plain_gradient_fail", "GRADIENT", similarity = 20.0, isPassing = false)

        assertEquals("no-score", noScore.classification)
        assertEquals("reference-missing", noScore.noScoreCause)
        assertEquals("unexpected-fail", fail.classification)
        assertEquals("none", fail.fallbackReason)
    }

    @Test
    fun `build evidence filters material families only`() {
        val evidence = Phase6MaterialFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T09:00:00",
                rows = listOf(
                    row("linear_gradient", family = "GRADIENT"),
                    row("linear_gradient_rt", family = "RUNTIME_EFFECT"),
                    row("color", family = "COLOR"),
                    row("cubicpath", family = "PATH"),
                ),
            ),
        )

        assertEquals(3, evidence.summary.totalRows)
        assertEquals(mapOf("COLOR" to 1, "GRADIENT" to 1, "RUNTIME_EFFECT" to 1), evidence.summary.families)
        assertEquals(listOf("linear_gradient", "linear_gradient_rt", "color"), evidence.rows.map { it.name })
    }

    @Test
    fun `build evidence computes family deltas from baseline`() {
        val evidence = Phase6MaterialFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T10:00:00",
                rows = listOf(
                    row("linear_gradient", family = "GRADIENT"),
                    row("second_gradient", family = "GRADIENT"),
                    row("color", family = "COLOR"),
                ),
            ),
        )

        assertEquals(
            mapOf(
                "COLOR" to Phase6MaterialFamilyDelta(
                    baselineSource = "2026-07-09 local dashboard before material-family wave",
                    currentSource = "integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json",
                    currentGeneratedAt = "2026-07-09T10:00:00",
                    baselineCount = 20,
                    currentCount = 1,
                    delta = -19,
                ),
                "GRADIENT" to Phase6MaterialFamilyDelta(
                    baselineSource = "2026-07-09 local dashboard before material-family wave",
                    currentSource = "integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json",
                    currentGeneratedAt = "2026-07-09T10:00:00",
                    baselineCount = 56,
                    currentCount = 2,
                    delta = -54,
                ),
                "RUNTIME_EFFECT" to Phase6MaterialFamilyDelta(
                    baselineSource = "2026-07-09 local dashboard before material-family wave",
                    currentSource = "integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json",
                    currentGeneratedAt = "2026-07-09T10:00:00",
                    baselineCount = 25,
                    currentCount = 0,
                    delta = -25,
                ),
            ),
            evidence.summary.familyDeltas,
        )
    }

    @Test
    fun `build evidence assigns stable row ids for duplicate names`() {
        val evidence = Phase6MaterialFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T11:00:00",
                rows = listOf(
                    row("linear_gradient", family = "GRADIENT"),
                    row("linear_gradient", family = "GRADIENT"),
                    row("linear_gradient", family = "GRADIENT"),
                    row("color", family = "COLOR"),
                ),
            ),
        )

        assertEquals(
            listOf("linear_gradient", "linear_gradient#2", "linear_gradient#3", "color"),
            evidence.rows.map { it.rowId },
        )
    }

    @Test
    fun `non-claims do not mention excluded families`() {
        val evidence = Phase6MaterialFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T12:00:00",
                rows = listOf(
                    row("linear_gradient", family = "GRADIENT"),
                    row("color", family = "COLOR"),
                ),
            ),
        )

        val claimsText = evidence.nonClaims.joinToString(" ")
        assertFalse(claimsText.contains("COMPOSITE"))
        assertFalse(claimsText.contains("BLUR"))
        assertFalse(claimsText.contains("IMAGE_FILTERS"))
        assertFalse(claimsText.contains("MESH"))
        assertFalse(claimsText.contains("TEXT"))
    }

    @Test
    fun `duplicate material rows receive stable row ids and surface them in csv and markdown`() {
        val evidence = Phase6MaterialFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T09:00:00",
                rows = listOf(
                    row("linear_gradient", family = "GRADIENT"),
                    row("linear_gradient", family = "GRADIENT", similarity = 20.0, isPassing = false),
                ),
            ),
        )

        assertEquals(listOf("linear_gradient", "linear_gradient#2"), evidence.rows.map { it.rowId })
        assertContains(evidence.toCsv(), "linear_gradient,linear_gradient,GRADIENT,gradient-linear,instrumented-existing")
        assertContains(evidence.toCsv(), "linear_gradient#2,linear_gradient,GRADIENT,gradient-linear,unexpected-fail")
        assertContains(evidence.toMarkdown(), "| Row ID | Row | Family | Subfamily | Classification | Similarity | Fallback | No Score Cause |")
        assertContains(evidence.toMarkdown(), "| `linear_gradient` | `linear_gradient` | `GRADIENT` | `gradient-linear` | `instrumented-existing` | 100.00 | `none` | `` |")
        assertContains(evidence.toMarkdown(), "| `linear_gradient#2` | `linear_gradient` | `GRADIENT` | `gradient-linear` | `unexpected-fail` | 20.00 | `none` | `` |")
    }

    @Test
    fun `markdown report includes separate material family deltas`() {
        val evidence = Phase6MaterialFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T09:00:00",
                rows = buildList {
                    repeat(56) { add(row("gradient_$it", family = "GRADIENT")) }
                    repeat(25) { add(row("runtime_$it", family = "RUNTIME_EFFECT")) }
                    repeat(20) { add(row("color_$it", family = "COLOR")) }
                },
            ),
        )

        val markdown = evidence.toMarkdown()

        assertContains(markdown, "## Family Deltas")
        assertContains(markdown, "2026-07-09 local dashboard before material-family wave")
        assertContains(markdown, "| `COLOR` | 20 | 20 | +0 |")
        assertContains(markdown, "| `GRADIENT` | 56 | 56 | +0 |")
        assertContains(markdown, "| `RUNTIME_EFFECT` | 25 | 25 | +0 |")
    }

    @Test
    fun `material evidence json includes diff stats and no score cause`() {
        val evidence = Phase6MaterialFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T09:00:00",
                rows = listOf(
                    row(
                        "linear_gradient",
                        family = "GRADIENT",
                        hasDiff = true,
                        width = 320,
                        height = 240,
                        matchingPixels = 70000,
                        totalPixels = 76800,
                        maxDiff = GmRgbaInt(r = 1, g = 2, b = 3, a = 4),
                        meanDiff = GmRgbaDouble(r = 1.25, g = 2.5, b = 3.75, a = 4.0),
                    ),
                    row("missing_runtime", family = "RUNTIME_EFFECT", similarity = null, isPassing = null, noReference = true),
                ),
            ),
        )

        val json = evidence.toJsonObject().toString()

        assertContains(json, "\"schemaVersion\":\"phase6-material-families-v1\"")
        assertContains(json, "\"width\":320")
        assertContains(json, "\"height\":240")
        assertContains(json, "\"matchingPixels\":70000")
        assertContains(json, "\"totalPixels\":76800")
        assertContains(json, "\"maxDiff\":{\"r\":1,\"g\":2,\"b\":3,\"a\":4}")
        assertContains(json, "\"meanDiff\":{\"r\":1.25,\"g\":2.5,\"b\":3.75,\"a\":4.0}")
        assertContains(json, "\"noScoreCause\":\"reference-missing\"")
    }

    @Test
    fun `material writer creates json markdown and csv outputs and preserves validation section`() {
        val evidence = Phase6MaterialFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T09:00:00",
                rows = listOf(row("linear_gradient", family = "GRADIENT"), row("color", family = "COLOR")),
            ),
        )

        val root = kotlin.io.path.createTempDirectory("phase6-material-evidence")
        Phase6MaterialFamilyEvidenceWriter.writeOutputs(root, evidence)

        val evidencePath = root.resolve("reports/gpu-renderer/phase-6-material-families/evidence.json")
        val markdownPath = root.resolve("reports/gpu-renderer/2026-07-09-gpu-phase-6-material-families.md")
        val csvPath = root.resolve("reports/gpu-renderer/phase-6-material-families/classification.csv")

        assertEquals(true, java.nio.file.Files.isRegularFile(evidencePath))
        assertEquals(true, java.nio.file.Files.isRegularFile(markdownPath))
        assertEquals(true, java.nio.file.Files.isRegularFile(csvPath))
        assertContains(java.nio.file.Files.readString(evidencePath), "\"schemaVersion\": \"phase6-material-families-v1\"")
        assertContains(java.nio.file.Files.readString(markdownPath), "No broad shader support is claimed")
        assertContains(java.nio.file.Files.readString(csvPath), "linear_gradient,linear_gradient,GRADIENT,gradient-linear,instrumented-existing")

        java.nio.file.Files.writeString(
            markdownPath,
            java.nio.file.Files.readString(markdownPath) +
                """

                ## Validation

                - `:integration-tests:skia-evidence:test` passed.
                - `generateGpuPhase6MaterialFamiliesEvidence` regenerated evidence.
                """.trimIndent() +
                "\n",
        )

        Phase6MaterialFamilyEvidenceWriter.writeOutputs(root, evidence)
        val regenerated = java.nio.file.Files.readString(markdownPath)
        assertContains(regenerated, "## Validation")
        assertContains(regenerated, "- `:integration-tests:skia-evidence:test` passed.")
    }

    private fun classify(
        name: String,
        family: String,
        similarity: Double? = 100.0,
        isPassing: Boolean? = true,
        noReference: Boolean = false,
    ): Phase6MaterialRowEvidence =
        Phase6MaterialFamilyClassifier.classify(
            row(name = name, family = family, similarity = similarity, isPassing = isPassing, noReference = noReference),
        )
}

private fun row(
    name: String,
    family: String,
    similarity: Double? = 100.0,
    isPassing: Boolean? = true,
    width: Int? = null,
    height: Int? = null,
    matchingPixels: Long? = null,
    totalPixels: Long? = null,
    maxDiff: GmRgbaInt? = null,
    meanDiff: GmRgbaDouble? = null,
    noReference: Boolean = false,
    renderFailed: Boolean = false,
    sizeMismatch: Boolean = false,
    hasDiff: Boolean = false,
): GmDashboardRow =
    GmDashboardRow(
        name = name,
        family = family,
        similarity = similarity,
        minSimilarity = 99.0,
        isPassing = isPassing,
        width = width,
        height = height,
        maxDiff = maxDiff,
        meanDiff = meanDiff,
        matchingPixels = matchingPixels,
        totalPixels = totalPixels,
        noReference = noReference,
        renderFailed = renderFailed,
        sizeMismatch = sizeMismatch,
        hasDiff = hasDiff,
    )
