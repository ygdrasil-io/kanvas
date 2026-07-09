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

        assertEquals(113, evidence.summary.familyDeltas.getValue("COMPOSITE").baselineCount)
        assertEquals(2, evidence.summary.familyDeltas.getValue("COMPOSITE").currentCount)
        assertEquals(-111, evidence.summary.familyDeltas.getValue("COMPOSITE").delta)
        assertEquals(45, evidence.summary.familyDeltas.getValue("BLUR").baselineCount)
        assertEquals(1, evidence.summary.familyDeltas.getValue("BLUR").currentCount)
        assertEquals(-44, evidence.summary.familyDeltas.getValue("BLUR").delta)
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
        assertContains(evidence.toCsv(), "modecolorfilters#2,modecolorfilters,COMPOSITE")
        assertContains(evidence.toMarkdown(), "`modecolorfilters#2`")
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
    fun `writer creates json markdown and csv outputs and preserves validation section`() {
        val root = Files.createTempDirectory("phase6-effect-composition-evidence-test")
        val markdown = root.resolve("reports/gpu-renderer/2026-07-09-gpu-phase-6-effect-composition-families.md")
        Files.createDirectories(markdown.parent)
        Files.writeString(markdown, "# Existing\n\n## Validation\n\n- keep this validation note\n")
        val evidence = Phase6EffectCompositionFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T10:00:00",
                rows = listOf(row("srcmode", family = "COMPOSITE")),
            ),
        )

        Phase6EffectCompositionFamilyEvidenceWriter.writeOutputs(root, evidence)

        assertContains(Files.readString(root.resolve("reports/gpu-renderer/phase-6-effect-composition-families/evidence.json")), "phase6-effect-composition-families-v1")
        assertContains(Files.readString(root.resolve("reports/gpu-renderer/phase-6-effect-composition-families/classification.csv")), "rowId,name,family,subfamily,classification")
        assertContains(Files.readString(markdown), "## Validation")
        assertContains(Files.readString(markdown), "keep this validation note")
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
        assertFalse(nonClaims.contains("complete support", ignoreCase = true))
    }

    private fun classify(
        name: String,
        family: String,
        similarity: Double? = 100.0,
        minSimilarity: Double? = 0.0,
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
        minSimilarity: Double? = 0.0,
        isPassing: Boolean? = true,
        noReference: Boolean = false,
        renderFailed: Boolean = false,
        sizeMismatch: Boolean = false,
        maxDiff: GmRgbaInt? = null,
        meanDiff: GmRgbaDouble? = null,
    ): GmDashboardRow =
        GmDashboardRow(
            name = name,
            family = family,
            similarity = similarity,
            minSimilarity = minSimilarity,
            isPassing = isPassing,
            width = 256,
            height = 256,
            matchingPixels = if (similarity == null) null else 65536,
            totalPixels = if (similarity == null) null else 65536,
            maxDiff = maxDiff,
            meanDiff = meanDiff,
            noReference = noReference,
            renderFailed = renderFailed,
            sizeMismatch = sizeMismatch,
            hasDiff = similarity != null && !renderFailed && !noReference && !sizeMismatch,
        )
}
