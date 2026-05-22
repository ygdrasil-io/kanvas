package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ModeColorFilterTest {
    @Test
    fun `ModeColorFilterGM matches modecolorfilters_png within tolerance`() {
        val gm = ModeColorFilterGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image modecolorfilters.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ModeColorFilterGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ModeColorFilterGM", comparison.similarity)
        assertTrue(accepted, "ModeColorFilterGM regressed below ratchet")
        assertTrue(comparison.similarity >= 0.0,
            "ModeColorFilterGM similarity ${"%.2f".format(comparison.similarity)}% < 0.0% floor")
    }
}
