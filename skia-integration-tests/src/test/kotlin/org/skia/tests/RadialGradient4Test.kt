package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class RadialGradient4Test {

    @Test
    fun `RadialGradient4GM dither matches radial_gradient4_png within tolerance`() {
        val gm = RadialGradient4GM(dither = true)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image radial_gradient4.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("RadialGradient4GM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("RadialGradient4GM", comparison.similarity)
        assertTrue(accepted, "RadialGradient4GM regressed below ratchet")
    }

    @Test
    fun `RadialGradient4GM nodither matches radial_gradient4_nodither_png within tolerance`() {
        val gm = RadialGradient4GM(dither = false)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image radial_gradient4_nodither.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("RadialGradient4NoditherGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("RadialGradient4NoditherGM", comparison.similarity)
        assertTrue(accepted, "RadialGradient4NoditherGM regressed below ratchet")
    }
}
