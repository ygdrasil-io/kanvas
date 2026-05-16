package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Crbug788500Test {

    @Test
    fun `Crbug788500GM matches crbug_788500_png within tolerance`() {
        val gm = Crbug788500GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image crbug_788500.png")
        // Path with one cubic verb and even-odd fill rule. The cubic is
        // adaptively flattened to 0.25-pixel chord error, then rasterized
        // through the 4x4 SS scanline. Since the path is unclosed and small,
        // the bug-reduction renders only a few-pixel sliver but the bulk of
        // the canvas is profile-converted background.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Crbug788500GM", comparison)
        if (comparison.similarity < 99.5) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Crbug788500GM", comparison.similarity)
        assertTrue(accepted, "Crbug788500GM regressed below ratchet")
        assertTrue(comparison.similarity >= 99.5,
            "Crbug788500GM similarity ${"%.2f".format(comparison.similarity)}% < 99.5% (t=1 floor)")
    }
}
