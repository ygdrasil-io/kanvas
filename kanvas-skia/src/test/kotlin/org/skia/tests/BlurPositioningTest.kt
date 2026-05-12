package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class BlurPositioningTest {

    @Test
    fun `BlurPositioningGM matches check_small_sigma_offset_png within tolerance`() {
        val gm = BlurPositioningGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image check_small_sigma_offset.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 2)
        TestReport.recordDetailed("BlurPositioningGM", comparison)
        if (comparison.similarity < 99.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("BlurPositioningGM", comparison.similarity)
        assertTrue(accepted, "BlurPositioningGM regressed below ratchet")
        assertTrue(comparison.similarity >= 95.0,
            "BlurPositioningGM similarity ${"%.2f".format(comparison.similarity)}% < 95% (t=2 floor)")
    }
}
