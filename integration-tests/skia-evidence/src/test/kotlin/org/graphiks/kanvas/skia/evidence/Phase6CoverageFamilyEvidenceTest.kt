package org.graphiks.kanvas.skia.evidence

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class Phase6CoverageFamilyEvidenceTest {
    @Test
    fun `classifies path fill simple as instrumented`() {
        val classified = Phase6CoverageFamilyClassifier.classify(row("cubicpath", family = "PATH"))

        assertEquals("instrumented-existing", classified.classification)
        assertEquals("path-fill-concave", classified.subfamily)
        assertEquals("none", classified.fallbackReason)
    }

    @Test
    fun `classifies stroke caps joins as instrumented when passing`() {
        val classified = Phase6CoverageFamilyClassifier.classify(row("strokedline_caps", family = "PATH"))

        assertEquals("instrumented-existing", classified.classification)
        assertEquals("path-stroke-caps-joins", classified.subfamily)
    }

    @Test
    fun `classifies failing dash rows as expected unsupported`() {
        val classified = Phase6CoverageFamilyClassifier.classify(
            row("dashing", family = "PATH", similarity = 40.0, isPassing = false),
        )

        assertEquals("expected-unsupported", classified.classification)
        assertEquals("path-dash-gated", classified.subfamily)
        assertEquals("unsupported.coverage.dash_pattern", classified.fallbackReason)
    }

    @Test
    fun `classifies failing path ops rows as expected unsupported`() {
        val classified = Phase6CoverageFamilyClassifier.classify(
            row("pathops_skbug_10155", family = "PATH", similarity = 10.0, isPassing = false),
        )

        assertEquals("expected-unsupported", classified.classification)
        assertEquals("path-ops-gated", classified.subfamily)
        assertEquals("unsupported.coverage.path_ops", classified.fallbackReason)
    }

    @Test
    fun `classifies rect and rrect clips`() {
        val rect = Phase6CoverageFamilyClassifier.classify(row("windowrectangles", family = "CLIP"))
        val rrect = Phase6CoverageFamilyClassifier.classify(row("rrect_clip_aa", family = "CLIP"))

        assertEquals("clip-rect", rect.subfamily)
        assertEquals("clip-rrect", rrect.subfamily)
    }

    @Test
    fun `classifies nested bounded clip`() {
        val classified = Phase6CoverageFamilyClassifier.classify(row("clipdrawdraw", family = "CLIP"))

        assertEquals("instrumented-existing", classified.classification)
        assertEquals("clip-nested-bounded", classified.subfamily)
    }

    @Test
    fun `classifies inverse complex and perspective clips with stable reasons`() {
        val inverse = Phase6CoverageFamilyClassifier.classify(
            row("inverseclip", family = "CLIP", similarity = 5.0, isPassing = false),
        )
        val complex = Phase6CoverageFamilyClassifier.classify(
            row("complexclip_aa", family = "CLIP", similarity = 5.0, isPassing = false),
        )
        val perspective = Phase6CoverageFamilyClassifier.classify(
            row("perspective_clip", family = "CLIP", similarity = 5.0, isPassing = false),
        )

        assertEquals("clip-inverse-gated", inverse.subfamily)
        assertEquals("unsupported.coverage.inverse_clip", inverse.fallbackReason)
        assertEquals("clip-complex-gated", complex.subfamily)
        assertEquals("unsupported.coverage.complex_clip", complex.fallbackReason)
        assertEquals("clip-perspective-gated", perspective.subfamily)
        assertEquals("unsupported.coverage.perspective_clip", perspective.fallbackReason)
    }

    @Test
    fun `classifies no score separately from unexpected fail`() {
        val noScore = Phase6CoverageFamilyClassifier.classify(
            row("missing_path_reference", family = "PATH", similarity = null, isPassing = null, noReference = true),
        )
        val fail = Phase6CoverageFamilyClassifier.classify(
            row("plain_path_fail", family = "PATH", similarity = 20.0, isPassing = false),
        )

        assertEquals("no-score", noScore.classification)
        assertEquals("reference-missing", noScore.noScoreCause)
        assertEquals("unexpected-fail", fail.classification)
        assertEquals("none", fail.fallbackReason)
    }

    @Test
    fun `build evidence filters path and clip only`() {
        val evidence = Phase6CoverageFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T08:00:00",
                rows = listOf(
                    row("cubicpath", family = "PATH"),
                    row("aaclip", family = "CLIP"),
                    row("all_bitmap_configs", family = "IMAGE"),
                ),
            ),
        )

        assertEquals(2, evidence.summary.totalRows)
        assertEquals(mapOf("CLIP" to 1, "PATH" to 1), evidence.summary.families)
        assertEquals(listOf("cubicpath", "aaclip"), evidence.rows.map { it.name })
    }
}

private fun row(
    name: String,
    family: String,
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
