package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Test for [InternalLinksGM] — the upstream `internal_links` GM.
 *
 * Renders blue rectangles + portable-typeface text labels on a light
 * grey background. `SkAnnotateLinkToDestination` /
 * `SkAnnotateNamedDestination` are no-ops on raster, so the visible
 * output is just rects + text.
 *
 * Uses the textual-GM tolerance (≈8) to absorb AWT-vs-FreeType AA
 * edge drift on the rendered glyphs.
 */
class InternalLinksTest {

    @Test
    fun `InternalLinksGM matches internal_links_png within tolerance`() {
        val gm = InternalLinksGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image internal_links.png")
        val comparison = TestUtils.compareBitmapsDetailed(
            rendered, reference!!,
            tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
        )
        TestReport.recordDetailed("InternalLinksGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("InternalLinksGM", comparison.similarity)
        assertTrue(accepted, "InternalLinksGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 99.7,
            "InternalLinksGM similarity ${"%.2f".format(comparison.similarity)}% < 99.7% (t=8 floor)",
        )
    }
}
