package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class SimpleRectTest {

    @Test
    fun `SimpleRectGM matches simplerect_png within tolerance`() {
        val gm = SimpleRectGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image simplerect.png")
        // SkRandom is bit-compatible with Skia and we now render into the DM
        // reference colorspace, so all 10 000 RGB565-quantised rects match
        // their reference value within 1 ulp per channel.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("SimpleRectGM", comparison)
        if (comparison.similarity < 99.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("SimpleRectGM", comparison.similarity)
        assertTrue(accepted, "SimpleRectGM regressed below tolerance")
        assertTrue(comparison.similarity >= 99.0,
            "SimpleRectGM similarity ${"%.2f".format(comparison.similarity)}% < 99.0% (t=1 floor)")
    }
}
