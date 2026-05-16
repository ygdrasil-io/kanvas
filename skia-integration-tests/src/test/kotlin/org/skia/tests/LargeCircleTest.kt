package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class LargeCircleTest {
    @Test
    fun `LargeCircleGM matches largecircle_png within tolerance`() {
        val gm = LargeCircleGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image largecircle.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("LargeCircleGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("LargeCircleGM", comparison.similarity)
        assertTrue(accepted, "LargeCircleGM regressed below ratchet")
        assertTrue(comparison.similarity >= 90.0,
            "LargeCircleGM similarity ${"%.2f".format(comparison.similarity)}% < 90.0%")
    }
}
