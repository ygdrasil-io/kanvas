package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class RadialGradient3Test {

    @Test
    fun `RadialGradient3GM dither matches radial_gradient3_png within tolerance`() {
        val gm = RadialGradient3GM(dither = true)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image radial_gradient3.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("RadialGradient3GM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("RadialGradient3GM", comparison.similarity)
        assertTrue(accepted, "RadialGradient3GM regressed below ratchet")
    }

    @Test
    fun `RadialGradient3GM nodither matches radial_gradient3_nodither_png within tolerance`() {
        val gm = RadialGradient3GM(dither = false)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image radial_gradient3_nodither.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("RadialGradient3NoditherGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("RadialGradient3NoditherGM", comparison.similarity)
        assertTrue(accepted, "RadialGradient3NoditherGM regressed below ratchet")
    }
}
