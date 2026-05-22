package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ScaledTiling2Test {
    @Test
    fun `ScaledTilingGradientGM matches scaled_tilemode_gradient_png within tolerance`() {
        val gm = ScaledTilingGradientGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image scaled_tilemode_gradient.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ScaledTilingGradientGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ScaledTilingGradientGM", comparison.similarity)
        assertTrue(accepted, "ScaledTilingGradientGM regressed below ratchet")
    }
}
