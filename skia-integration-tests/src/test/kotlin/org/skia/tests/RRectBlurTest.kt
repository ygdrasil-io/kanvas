package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class RRectBlurTest {

    @Test
    fun `RRectBlurGM matches rrect_blurs_png within tolerance`() {
        val gm = RRectBlurGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image rrect_blurs.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("RRectBlurGM", comparison)
        if (comparison.similarity < 85.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("RRectBlurGM", comparison.similarity)
        assertTrue(accepted, "RRectBlurGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 70.0,
            "RRectBlurGM similarity ${"%.2f".format(comparison.similarity)}% < 70% floor",
        )
    }
}
