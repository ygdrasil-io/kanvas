package org.graphiks.kanvas.skia.evidence

import kotlin.test.Test
import kotlin.test.assertEquals

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
