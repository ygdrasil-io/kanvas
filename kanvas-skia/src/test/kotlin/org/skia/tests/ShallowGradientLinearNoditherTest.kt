package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ShallowGradientLinearNoditherTest {

    @Test
    fun `ShallowGradientLinearNoditherGM matches png within tolerance`() {
        val gm = ShallowGradientLinearNoditherGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image shallow_gradient_linear_nodither.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ShallowGradientLinearNoditherGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ShallowGradientLinearNoditherGM", comparison.similarity)
        assertTrue(accepted, "ShallowGradientLinearNoditherGM regressed below ratchet")
        assertTrue(comparison.similarity >= 99.0,
            "ShallowGradientLinearNoditherGM similarity ${"%.2f".format(comparison.similarity)}% < 99.0% (t=1 floor)")
    }
}
