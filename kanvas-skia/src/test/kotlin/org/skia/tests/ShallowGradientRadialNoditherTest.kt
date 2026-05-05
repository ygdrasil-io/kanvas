package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ShallowGradientRadialNoditherTest {

    @Test
    fun `ShallowGradientRadialNoditherGM matches png within tolerance`() {
        val gm = ShallowGradientRadialNoditherGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image shallow_gradient_radial_nodither.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ShallowGradientRadialNoditherGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ShallowGradientRadialNoditherGM", comparison.similarity)
        assertTrue(accepted, "ShallowGradientRadialNoditherGM regressed below ratchet")
        assertTrue(comparison.similarity >= 99.0,
            "ShallowGradientRadialNoditherGM similarity ${"%.2f".format(comparison.similarity)}% < 99.0% (t=1 floor)")
    }
}
