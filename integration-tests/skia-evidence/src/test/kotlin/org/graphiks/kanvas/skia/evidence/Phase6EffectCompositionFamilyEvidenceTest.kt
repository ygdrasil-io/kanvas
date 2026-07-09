package org.graphiks.kanvas.skia.evidence

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import java.nio.file.Files

class Phase6EffectCompositionFamilyEvidenceTest {
    @Test
    fun `build evidence filters composite and blur only`() {
        val evidence = Phase6EffectCompositionFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T10:00:00",
                rows = listOf(
                    row("srcmode", family = "COMPOSITE"),
                    row("BlurSmallSigma", family = "BLUR"),
                    row("linear_gradient", family = "GRADIENT"),
                    row("cubicpath", family = "PATH"),
                ),
            ),
        )

        assertEquals(2, evidence.summary.totalRows)
        assertEquals(mapOf("BLUR" to 1, "COMPOSITE" to 1), evidence.summary.families)
        assertEquals(listOf("srcmode", "BlurSmallSigma"), evidence.rows.map { it.name })
        assertEquals("phase6-effect-composition-families-v1", evidence.schemaVersion)
    }

    @Test
    fun `classifies composite subfamilies with stable reasons`() {
        val srcOver = classify("srcmode", "COMPOSITE")
        val pd = classify("xfermodes", "COMPOSITE")
        val advanced = classify("advanced_blend_modes", "COMPOSITE")
        val saveLayer = classify("savelayer_f16", "COMPOSITE")
        val backdrop = classify("backdrop_imagefilter_croprect", "COMPOSITE")
        val dstRead = classify("dstreadshuffle", "COMPOSITE")
        val imageFilter = classify("imagefilters_xfermodes", "COMPOSITE")
        val atlas = classify("draw-atlas-colors", "COMPOSITE")
        val colorFilter = classify("modecolorfilters", "COMPOSITE")
        val colorShader = classify("color4shader", "COMPOSITE")
        val colorMatrix = classify("colormatrix", "COMPOSITE")

        assertEquals("composite-src-over-basic", srcOver.subfamily)
        assertEquals("instrumented-existing", srcOver.classification)
        assertEquals("none", srcOver.fallbackReason)
        assertEquals("composite-porter-duff", pd.subfamily)
        assertEquals("instrumented-existing", pd.classification)
        assertEquals("composite-advanced-blend-gated", advanced.subfamily)
        assertEquals("unsupported.composition.advanced_blend", advanced.fallbackReason)
        assertEquals("expected-unsupported", advanced.classification)
        assertEquals("composite-save-layer-gated", saveLayer.subfamily)
        assertEquals("unsupported.composition.save_layer", saveLayer.fallbackReason)
        assertEquals("composite-backdrop-gated", backdrop.subfamily)
        assertEquals("unsupported.composition.backdrop_filter", backdrop.fallbackReason)
        assertEquals("composite-destination-read-gated", dstRead.subfamily)
        assertEquals("unsupported.composition.destination_read", dstRead.fallbackReason)
        assertEquals("composite-image-filter-gated", imageFilter.subfamily)
        assertEquals("unsupported.composition.image_filter_dag", imageFilter.fallbackReason)
        assertEquals("composite-atlas-or-vertices-gated", atlas.subfamily)
        assertEquals("unsupported.composition.atlas_or_vertices", atlas.fallbackReason)
        assertEquals("composite-color-filter-gated", colorFilter.subfamily)
        assertEquals("unsupported.composition.color_dependency", colorFilter.fallbackReason)
        assertEquals("expected-unsupported", colorFilter.classification)
        assertEquals("composite-color-filter-gated", colorShader.subfamily)
        assertEquals("unsupported.composition.color_dependency", colorShader.fallbackReason)
        assertEquals("expected-unsupported", colorShader.classification)
        assertEquals("composite-color-filter-gated", colorMatrix.subfamily)
        assertEquals("unsupported.composition.color_dependency", colorMatrix.fallbackReason)
        assertEquals("expected-unsupported", colorMatrix.classification)
    }

    @Test
    fun `classifies blur subfamilies with stable reasons`() {
        val smallSigma = classify("BlurSmallSigma", "BLUR")
        val rects = classify("blur2rects", "BLUR")
        val image = classify("blur_image", "BLUR")
        val big = classify("BlurBigSigma", "BLUR")
        val transform = classify("blur_matrix_rect", "BLUR")
        val clip = classify("blurredclippedcircle", "BLUR")
        val graph = classify("fast_slow_blurimagefilter", "BLUR")
        val convolution = classify("matrixconvolution", "BLUR")
        val text = classify("imagefilterstext_if", "BLUR")

        assertEquals("blur-small-sigma", smallSigma.subfamily)
        assertEquals("instrumented-existing", smallSigma.classification)
        assertEquals("blur-rect-rrect-circle", rects.subfamily)
        assertEquals("instrumented-existing", rects.classification)
        assertEquals("blur-image-basic", image.subfamily)
        assertEquals("instrumented-existing", image.classification)
        assertEquals("blur-large-sigma-gated", big.subfamily)
        assertEquals("unsupported.blur.large_sigma", big.fallbackReason)
        assertEquals("expected-unsupported", big.classification)
        assertEquals("blur-transform-or-perspective-gated", transform.subfamily)
        assertEquals("unsupported.blur.transform_or_perspective", transform.fallbackReason)
        assertEquals("blur-clip-interaction-gated", clip.subfamily)
        assertEquals("unsupported.blur.clip_interaction", clip.fallbackReason)
        assertEquals("blur-filter-graph-gated", graph.subfamily)
        assertEquals("unsupported.blur.image_filter_graph", graph.fallbackReason)
        assertEquals("blur-matrix-convolution-gated", convolution.subfamily)
        assertEquals("unsupported.blur.matrix_convolution", convolution.fallbackReason)
        assertEquals("blur-text-dependent-gated", text.subfamily)
        assertEquals("unsupported.blur.text_dependency", text.fallbackReason)
    }

    @Test
    fun `separates no score from unexpected fail`() {
        val noScore = classify("animatedbackdropblur", "BLUR", similarity = null, isPassing = null, noReference = true)
        val fail = classify("plain_composite_fail", "COMPOSITE", similarity = 20.0, isPassing = false)

        assertEquals("no-score", noScore.classification)
        assertEquals("reference-missing", noScore.noScoreCause)
        assertEquals("unexpected-fail", fail.classification)
        assertEquals("none", fail.fallbackReason)
    }

    @Test
    fun `passing gated effect rows remain expected unsupported`() {
        val advanced = classify("advanced_blend_modes", "COMPOSITE", similarity = 100.0, isPassing = true)
        val bigBlur = classify("BlurBigSigma", "BLUR", similarity = 100.0, isPassing = true)

        assertEquals("expected-unsupported", advanced.classification)
        assertEquals("unsupported.composition.advanced_blend", advanced.fallbackReason)
        assertEquals("expected-unsupported", bigBlur.classification)
        assertEquals("unsupported.blur.large_sigma", bigBlur.fallbackReason)
    }

    @Test
    fun `no score color dependency rows keep stable color refusal reason`() {
        val colorCompose = classify("colorcomposefilter_alpha", "COMPOSITE", similarity = null, isPassing = null, renderFailed = true)
        val composeCf = classify("composeCFIF", "COMPOSITE", similarity = null, isPassing = null, renderFailed = true)
        val mixer = classify("mixerCF", "COMPOSITE", similarity = null, isPassing = null, renderFailed = true)

        listOf(colorCompose, composeCf, mixer).forEach { row ->
            assertEquals("composite-color-filter-gated", row.subfamily)
            assertEquals("no-score", row.classification)
            assertEquals("unsupported.composition.color_dependency", row.fallbackReason)
            assertEquals("generated-render-missing", row.noScoreCause)
        }
    }

    @Test
    fun `build evidence computes family deltas from baseline`() {
        val evidence = Phase6EffectCompositionFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T10:00:00",
                rows = listOf(
                    row("srcmode", family = "COMPOSITE"),
                    row("xfermodes", family = "COMPOSITE"),
                    row("BlurSmallSigma", family = "BLUR"),
                ),
            ),
        )

        assertEquals(
            mapOf(
                "BLUR" to Phase6EffectCompositionFamilyDelta(
                    baselineSource = "2026-07-09 local dashboard before effect-composition-family wave",
                    currentSource = "integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json",
                    currentGeneratedAt = "2026-07-09T10:00:00",
                    baselineCount = 45,
                    currentCount = 1,
                    delta = -44,
                ),
                "COMPOSITE" to Phase6EffectCompositionFamilyDelta(
                    baselineSource = "2026-07-09 local dashboard before effect-composition-family wave",
                    currentSource = "integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json",
                    currentGeneratedAt = "2026-07-09T10:00:00",
                    baselineCount = 113,
                    currentCount = 2,
                    delta = -111,
                ),
            ),
            evidence.summary.familyDeltas,
        )
    }

    @Test
    fun `duplicate rows receive stable row ids and surface them in csv and markdown`() {
        val evidence = Phase6EffectCompositionFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T10:00:00",
                rows = listOf(
                    row("modecolorfilters", family = "COMPOSITE"),
                    row("modecolorfilters", family = "COMPOSITE"),
                ),
            ),
        )

        assertEquals(listOf("modecolorfilters", "modecolorfilters#2"), evidence.rows.map { it.rowId })
        assertEquals(listOf("modecolorfilters", "modecolorfilters"), evidence.rows.map { it.name })
        assertContains(evidence.toCsv(), "modecolorfilters,modecolorfilters,COMPOSITE")
        assertContains(evidence.toCsv(), "modecolorfilters#2,modecolorfilters,COMPOSITE")
        assertContains(evidence.toMarkdown(), "| `modecolorfilters` | `modecolorfilters` | `COMPOSITE` |")
        assertContains(evidence.toMarkdown(), "| `modecolorfilters#2` | `modecolorfilters` | `COMPOSITE` |")
    }

    @Test
    fun `evidence json includes diff stats and no score cause`() {
        val evidence = Phase6EffectCompositionFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T10:00:00",
                rows = listOf(
                    row("animatedbackdropblur", family = "BLUR", similarity = null, isPassing = null, noReference = true),
                    row("srcmode", family = "COMPOSITE", maxDiff = GmRgbaInt(1, 2, 3, 4), meanDiff = GmRgbaDouble(1.5, 2.5, 3.5, 4.5)),
                ),
            ),
        )

        val json = evidence.toJsonObject().toString()
        assertContains(json, "\"schemaVersion\":\"phase6-effect-composition-families-v1\"")
        assertContains(json, "\"noScoreCause\":\"reference-missing\"")
        assertContains(json, "\"maxDiff\":{\"r\":1,\"g\":2,\"b\":3,\"a\":4}")
        assertContains(json, "\"meanDiff\":{\"r\":1.5,\"g\":2.5,\"b\":3.5,\"a\":4.5}")
    }

    @Test
    fun `evidence json and markdown include follow up candidates by root cause`() {
        val evidence = Phase6EffectCompositionFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T10:00:00",
                rows = listOf(
                    row("modecolorfilters", family = "COMPOSITE"),
                    row("color4shader", family = "COMPOSITE"),
                    row("advanced_blend_modes", family = "COMPOSITE"),
                    row("animatedbackdropblur", family = "BLUR", similarity = null, isPassing = null, noReference = true),
                    row("plain_composite_fail", family = "COMPOSITE", similarity = 20.0, isPassing = false),
                    row("srcmode", family = "COMPOSITE", similarity = 100.0, isPassing = true),
                ),
            ),
        )

        val json = evidence.toJsonObject().toString()
        val markdown = evidence.toMarkdown()

        assertContains(json, "\"followUpCandidates\"")
        assertContains(json, "\"rootCause\":\"unsupported.composition.color_dependency\"")
        assertContains(json, "\"rootCause\":\"unsupported.composition.advanced_blend\"")
        assertContains(json, "\"rootCause\":\"reference-missing\"")
        assertContains(json, "\"rootCause\":\"unexpected-fail.without-stable-refusal\"")
        assertFalse(json.contains("\"rootCause\":\"none\""))
        assertContains(markdown, "## Follow-Up Candidates")
        assertContains(markdown, "| `unsupported.composition.color_dependency` | `expected-unsupported` | 2 | `color4shader`, `modecolorfilters` |")
    }

    @Test
    fun `markdown summary surfaces promoted unexpected fail and no score counters`() {
        val evidence = Phase6EffectCompositionFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T10:00:00",
                rows = listOf(
                    row("srcmode", family = "COMPOSITE", similarity = 100.0, isPassing = true),
                    row("plain_composite_fail", family = "COMPOSITE", similarity = 20.0, isPassing = false),
                    row("animatedbackdropblur", family = "BLUR", similarity = null, isPassing = null, noReference = true),
                ),
            ),
        )

        val markdown = evidence.toMarkdown()

        assertContains(markdown, "- Promoted rows: 0")
        assertContains(markdown, "- Unexpected fails: 1")
        assertContains(markdown, "- No score: 1")
    }

    @Test
    fun `writer creates json markdown and csv outputs and preserves validation section`() {
        val root = Files.createTempDirectory("phase6-effect-composition-evidence-test")
        val markdown = root.resolve("reports/gpu-renderer/2026-07-09-gpu-phase-6-effect-composition-families.md")
        val evidence = Phase6EffectCompositionFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T10:00:00",
                rows = listOf(row("srcmode", family = "COMPOSITE")),
            ),
        )

        Phase6EffectCompositionFamilyEvidenceWriter.writeOutputs(root, evidence)

        val evidencePath = root.resolve("reports/gpu-renderer/phase-6-effect-composition-families/evidence.json")
        val csvPath = root.resolve("reports/gpu-renderer/phase-6-effect-composition-families/classification.csv")
        val sentinel = "SENTINEL BODY MUTATION"

        assertContains(Files.readString(evidencePath), "phase6-effect-composition-families-v1")
        assertContains(Files.readString(markdown), "No broad COMPOSITE or BLUR support is claimed")
        assertContains(Files.readString(csvPath), "rowId,name,family,subfamily,classification")

        Files.writeString(
            markdown,
            Files.readString(markdown) +
                "\n$sentinel\n" +
                """

                ## Validation

                - `:integration-tests:skia-evidence:test` passed.
                - `generateGpuPhase6EffectCompositionFamiliesEvidence` regenerated evidence.
                """.trimIndent() +
                "\n",
        )

        Phase6EffectCompositionFamilyEvidenceWriter.writeOutputs(root, evidence)

        val regenerated = Files.readString(markdown)
        assertFalse(regenerated.contains(sentinel))
        assertContains(regenerated, "## Validation")
        assertContains(regenerated, "- `:integration-tests:skia-evidence:test` passed.")
        assertContains(regenerated, "- `generateGpuPhase6EffectCompositionFamiliesEvidence` regenerated evidence.")
    }

    @Test
    fun `non claims do not mention excluded support as complete`() {
        val evidence = Phase6EffectCompositionFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T10:00:00",
                rows = listOf(row("srcmode", family = "COMPOSITE")),
            ),
        )

        val nonClaims = evidence.nonClaims.joinToString("\n")
        assertContains(nonClaims, "No broad COMPOSITE or BLUR support is claimed from classification alone.")
        assertContains(nonClaims, "saveLayer, destination-read, backdrop filters, image-filter DAGs, matrix convolution, and advanced blend chains remain outside this evidence wave unless row diagnostics prove a bounded route.")
        assertContains(nonClaims, "COLOR, TEXT, IMAGE, PATH, CLIP, MATERIAL, and MESH dependencies are not absorbed into this wave.")
        assertFalse(nonClaims.contains("complete support", ignoreCase = true))
    }

    private fun classify(
        name: String,
        family: String,
        similarity: Double? = 100.0,
        minSimilarity: Double? = 99.0,
        isPassing: Boolean? = true,
        noReference: Boolean = false,
        renderFailed: Boolean = false,
        sizeMismatch: Boolean = false,
        maxDiff: GmRgbaInt? = null,
        meanDiff: GmRgbaDouble? = null,
    ): Phase6EffectCompositionRowEvidence =
        Phase6EffectCompositionFamilyClassifier.classify(
            row(
                name = name,
                family = family,
                similarity = similarity,
                minSimilarity = minSimilarity,
                isPassing = isPassing,
                noReference = noReference,
                renderFailed = renderFailed,
                sizeMismatch = sizeMismatch,
                maxDiff = maxDiff,
                meanDiff = meanDiff,
            ),
        )

    private fun row(
        name: String,
        family: String,
        similarity: Double? = 100.0,
        minSimilarity: Double? = 99.0,
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
            minSimilarity = minSimilarity,
            isPassing = isPassing,
            width = width,
            height = height,
            matchingPixels = matchingPixels,
            totalPixels = totalPixels,
            maxDiff = maxDiff,
            meanDiff = meanDiff,
            noReference = noReference,
            renderFailed = renderFailed,
            sizeMismatch = sizeMismatch,
            hasDiff = hasDiff,
        )
}
