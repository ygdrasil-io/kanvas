package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class BeziersTest {

    @Test
    fun `BeziersGM matches beziers_png within tolerance`() {
        val gm = BeziersGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image beziers.png")
        // 10 quad paths + 10 cubic paths, AA stroked at random colours
        // and squared widths (nextRangeScalar(1,5)²). SkRandom is bit
        // compatible with upstream, so geometry + colour match.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("BeziersGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("BeziersGM", comparison.similarity)
        assertTrue(accepted, "BeziersGM regressed below ratchet")
        assertTrue(comparison.similarity >= 88.0,
            "BeziersGM similarity ${"%.2f".format(comparison.similarity)}% < 88.0% (t=1 floor)")
    }
}
