package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class BlurCircles2Test {

    @Test
    fun `BlurCircles2GM matches blurcircles2_png within tolerance`() {
        val gm = BlurCircles2GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image blurcircles2.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("BlurCircles2GM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("BlurCircles2GM", comparison.similarity)
        assertTrue(accepted, "BlurCircles2GM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 30.0,
            "BlurCircles2GM similarity ${"%.2f".format(comparison.similarity)}% < 30% floor",
        )
    }
}
