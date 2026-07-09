package org.graphiks.kanvas.skia.evidence

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScoreFirstGmFidelityWave3Test {
    @Test
    fun `ranks candidates by unmatched pixels descending`() {
        val evidence = ScoreFirstWave3Classifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T00:00:00Z",
                rows = listOf(
                    row("pictureshader", "IMAGE", matchingPixels = 626_000, totalPixels = 2_030_000),
                    row("tablecolorfilter", "COMPOSITE", matchingPixels = 451_704, totalPixels = 1_155_000),
                    row("dashing5_aa", "PATH", matchingPixels = 0, totalPixels = 80_000),
                ),
            ),
        )

        assertEquals(listOf("pictureshader", "tablecolorfilter", "dashing5_aa"), evidence.candidates.map { it.name })
        assertEquals(1_404_000, evidence.candidates.first().unmatchedPixels)
    }

    @Test
    fun `assigns candidates to wave groups`() {
        val evidence = ScoreFirstWave3Classifier.buildEvidence(
            GmDashboard(
                generatedAt = null,
                rows = listOf(
                    row("tilemode_decal", "IMAGE"),
                    row("xfermodes", "COMPOSITE"),
                    row("complexclip4_aa", "CLIP"),
                    row("rtif_unsharp", "RUNTIME_EFFECT", isPassing = false),
                ),
            ),
        )

        assertEquals(listOf("A", "B", "C", "D"), evidence.candidates.map { it.groupId })
    }

    @Test
    fun `report includes guardrails and first implementation slice`() {
        val evidence = ScoreFirstWave3Classifier.buildEvidence(
            GmDashboard(
                generatedAt = "now",
                rows = listOf(row("imageshader_tinyscale", "IMAGE", matchingPixels = 0, totalPixels = 1_000_000)),
            ),
        )

        val markdown = ScoreFirstWave3Markdown.render(evidence)

        assertContains(markdown, "Do not modify `integration-tests/skia/src/test/resources/reference/**`")
        assertContains(markdown, "First slice: Work Group A")
        assertContains(markdown, "imageshader_tinyscale")
        assertTrue(markdown.lines().any { it.contains("unmatchedPixels") })
    }

    @Test
    fun `keeps no score rows visible instead of hiding them`() {
        val evidence = ScoreFirstWave3Classifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T00:00:00Z",
                rows = listOf(
                    row("missing_reference", "IMAGE", similarity = null, isPassing = null, matchingPixels = null, totalPixels = null, noReference = true),
                    row("render_failed", "COMPOSITE", similarity = null, isPassing = null, matchingPixels = null, totalPixels = null, renderFailed = true),
                    row("size_mismatch", "PATH", similarity = null, isPassing = null, matchingPixels = null, totalPixels = null, sizeMismatch = true),
                ),
            ),
        )

        assertEquals(listOf("missing_reference", "render_failed", "size_mismatch"), evidence.candidates.map { it.name })
        assertEquals(listOf("no-reference", "render-failed", "size-mismatch"), evidence.candidates.map { it.status })
        assertContains(ScoreFirstWave3Markdown.render(evidence), "missing_reference")
    }

    @Test
    fun `writer creates markdown and tsv outputs`() {
        val evidence = ScoreFirstWave3Classifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T00:00:00Z",
                rows = listOf(row("pictureshader", "IMAGE", matchingPixels = 626_000, totalPixels = 2_030_000)),
            ),
        )

        val root = kotlin.io.path.createTempDirectory("score-first-wave3")
        val output = ScoreFirstWave3ReportWriter.write(root, evidence)

        assertEquals(true, Files.isRegularFile(output.markdownPath))
        assertEquals(true, Files.isRegularFile(output.tsvPath))
        assertContains(Files.readString(output.markdownPath), "GM Fidelity Wave 3 Score-First Evidence")
        assertContains(Files.readString(output.tsvPath), "pictureshader")
    }

    private fun row(
        name: String,
        family: String,
        similarity: Double? = 50.0,
        minSimilarity: Double? = 0.0,
        isPassing: Boolean? = true,
        matchingPixels: Long? = 50,
        totalPixels: Long? = 100,
        noReference: Boolean = false,
        renderFailed: Boolean = false,
        sizeMismatch: Boolean = false,
    ): GmDashboardRow =
        GmDashboardRow(
            name = name,
            family = family,
            similarity = similarity,
            minSimilarity = minSimilarity,
            isPassing = isPassing,
            width = 10,
            height = 10,
            maxDiff = null,
            meanDiff = null,
            matchingPixels = matchingPixels,
            totalPixels = totalPixels,
            noReference = noReference,
            renderFailed = renderFailed,
            sizeMismatch = sizeMismatch,
            hasDiff = true,
        )
}
