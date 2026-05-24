package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class BlurRectTest {

    @Test
    fun `BlurRectGM matches reference`() {
        val gm = BlurRectGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image blurrects.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("BlurRectGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("BlurRectGM", comparison.similarity)
        assertTrue(accepted, "BlurRectGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 50.0,
            "BlurRectGM similarity ${"%.2f".format(comparison.similarity)}% < 50.0% floor",
        )
    }
}
