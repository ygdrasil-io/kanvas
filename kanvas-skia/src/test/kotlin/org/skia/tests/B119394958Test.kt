package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class B119394958Test {

    @Test
    fun `B119394958GM matches b_119394958_png within tolerance`() {
        val gm = B119394958GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image b_119394958.png")
        // Filled circle + stroked circle + round-cap arc.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("B119394958GM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("B119394958GM", comparison.similarity)
        assertTrue(accepted, "B119394958GM regressed below ratchet")
        // Drift around the arc's round-cap rim and the green stroke
        // edge — sub-pixel coverage diff vs upstream's analytic stroker.
        assertTrue(comparison.similarity >= 88.0,
            "B119394958GM similarity ${"%.2f".format(comparison.similarity)}% < 88.0% (t=1 floor)")
    }
}
