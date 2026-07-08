package org.graphiks.kanvas.skia.evidence

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class Phase6ImageFamilyEvidenceTest {
    @Test
    fun `classifies passed image rect as instrumented until route evidence exists`() {
        val classified = Phase6ImageFamilyClassifier.classify(row("DrawBitmapRect3"))

        assertEquals("instrumented-existing", classified.classification)
        assertEquals("simple-image-rect", classified.subfamily)
        assertEquals("none", classified.fallbackReason)
        assertContains(classified.nonClaim, "route/cache/batching evidence missing")
    }

    @Test
    fun `classifies codec rows as expected unsupported`() {
        val classified = Phase6ImageFamilyClassifier.classify(
            row("AnimatedGif", similarity = 37.5, isPassing = false),
        )

        assertEquals("expected-unsupported", classified.classification)
        assertEquals("animation-gated", classified.subfamily)
        assertEquals("dependency.image.codec.unregistered", classified.fallbackReason)
    }

    @Test
    fun `classifies yuv rows with stable conversion reason`() {
        val classified = Phase6ImageFamilyClassifier.classify(
            row("YUV", similarity = 22.0, isPassing = false),
        )

        assertEquals("expected-unsupported", classified.classification)
        assertEquals("yuv-gated", classified.subfamily)
        assertEquals("unsupported.color.yuv_conversion", classified.fallbackReason)
    }

    @Test
    fun `classifies no score separately from unexpected fail`() {
        val noScore = Phase6ImageFamilyClassifier.classify(
            row("MissingReferenceImage", similarity = null, isPassing = null, noReference = true),
        )
        val fail = Phase6ImageFamilyClassifier.classify(
            row("PlainImageFail", similarity = 40.0, isPassing = false),
        )

        assertEquals("no-score", noScore.classification)
        assertEquals("reference-missing", noScore.noScoreCause)
        assertEquals("unexpected-fail", fail.classification)
    }

    @Test
    fun `build evidence filters only image family`() {
        val dashboard = GmDashboard(
            generatedAt = "2026-07-08T21:00:00",
            rows = listOf(
                row("DrawBitmapRect3"),
                row("aaclip", family = "CLIP"),
            ),
        )

        val evidence = Phase6ImageFamilyClassifier.buildEvidence(dashboard)

        assertEquals(1, evidence.summary.totalImageRows)
        assertEquals(1, evidence.summary.classifications["instrumented-existing"])
        assertEquals("DrawBitmapRect3", evidence.rows.single().name)
    }
}

private fun row(
    name: String,
    family: String = "IMAGE",
    similarity: Double? = 100.0,
    isPassing: Boolean? = true,
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
        noReference = noReference,
        renderFailed = renderFailed,
        sizeMismatch = sizeMismatch,
        hasDiff = hasDiff,
    )
