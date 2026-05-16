package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class LuminosityOverflowTest {

    @Test
    fun `LuminosityOverflowGM matches luminosity_overflow_png within tolerance`() {
        val gm = LuminosityOverflowGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image luminosity_overflow.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("LuminosityOverflowGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("LuminosityOverflowGM", comparison.similarity)
        assertTrue(accepted, "LuminosityOverflowGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 99.0,
            "LuminosityOverflowGM similarity ${"%.2f".format(comparison.similarity)}% < 99.0% floor",
        )
    }
}
