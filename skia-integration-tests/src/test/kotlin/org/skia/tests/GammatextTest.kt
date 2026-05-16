package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Wave 11A textual GM port — see [GammatextGM] for source-spec mapping.
 *
 * Tolerance 8 (default for textual GMs, [TestUtils.TEXTUAL_GM_TOLERANCE])
 * is used to absorb the AWT-vs-FreeType AA edge drift on the
 * eight-colour Hamburgefons columns. Floor is set per the actual /
 * actual-minus-buffer rule from the wave brief.
 */
class GammatextTest {

    @Test
    fun `GammatextGM matches gammatext_png within tolerance`() {
        val gm = GammatextGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image gammatext.png")

        val comparison = TestUtils.compareBitmapsDetailed(
            rendered, reference!!, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
        )
        TestReport.recordDetailed("GammatextGM", comparison)
        if (comparison.similarity < FLOOR) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("GammatextGM", comparison.similarity)
        assertTrue(accepted, "GammatextGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= FLOOR,
            "GammatextGM similarity ${"%.2f".format(comparison.similarity)}% < $FLOOR% floor",
        )
    }

    private companion object {
        // Floor calibrated to actual measured similarity (~31.83%) minus
        // a 0.1 pp safety margin — per the H3 wave brief. The headline
        // visual driver is the heat-gradient background : our F16
        // working-cs LinearGradient + colour-managed compare differs
        // structurally from the upstream sRGB-clamped
        // 8-bit gradient, dragging the per-pixel match rate well below
        // the textual GMs that draw on a flat BG. The
        // [SimilarityTracker] ratchet locks the day-to-day score
        // against further regression.
        private const val FLOOR = 31.7
    }
}
