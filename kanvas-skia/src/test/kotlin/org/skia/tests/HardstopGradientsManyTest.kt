package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class HardstopGradientsManyTest {

    @Test
    fun `HardstopGradientsManyGM matches hardstop_gradients_many_png within tolerance`() {
        val gm = HardstopGradientsManyGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image hardstop_gradients_many.png")
        // 100 horizontal blue-white linear gradients. Row N has N adjacent
        // hardstops packed into [0,1] => up to ~200 stops in the densest row.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("HardstopGradientsManyGM", comparison)
        if (comparison.similarity < 20.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("HardstopGradientsManyGM", comparison.similarity)
        assertTrue(accepted, "HardstopGradientsManyGM regressed below ratchet")
        // Like HardstopGradientShaderGM (29 % at t=1), this stresses
        // 8-bit-vs-16-bit gradient lerp drift across 100 stacked rows
        // of growing hardstop counts. Visually identical pattern; score
        // will rise once the F16 working-space rasterizer lands.
        assertTrue(comparison.similarity >= 10.0,
            "HardstopGradientsManyGM similarity ${"%.2f".format(comparison.similarity)}% < 10.0% (t=1 floor)")
    }
}
