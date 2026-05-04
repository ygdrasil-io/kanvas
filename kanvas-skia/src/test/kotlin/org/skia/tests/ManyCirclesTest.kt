package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ManyCirclesTest {
    @Test
    fun `ManyCirclesGM matches manycircles_png within tolerance`() {
        val gm = ManyCirclesGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image manycircles.png")
        // 10 000 AA ovals with bit-compatible random positions and sizes,
        // colours quantised through 565 to match the reference's encoding.
        // Heavy compositing stress — every pixel sees many translucent
        // overlaps.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ManyCirclesGM", comparison)
        if (comparison.similarity < 85.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ManyCirclesGM", comparison.similarity)
        assertTrue(accepted, "ManyCirclesGM regressed below ratchet")
        assertTrue(comparison.similarity >= 85.0,
            "ManyCirclesGM similarity ${"%.2f".format(comparison.similarity)}% < 85.0% (t=1 floor)")
    }
}
