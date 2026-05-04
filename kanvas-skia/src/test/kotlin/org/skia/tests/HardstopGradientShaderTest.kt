package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class HardstopGradientShaderTest {
    @Test
    fun `HardstopGradientShaderGM matches hardstop_gradients_png within tolerance`() {
        val gm = HardstopGradientShaderGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image hardstop_gradients.png")
        // 8 × 3 grid covering all three "interesting" tile modes
        // (kClamp, kRepeat, kMirror). First end-to-end multi-tile-mode GM.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("HardstopGradientShaderGM", comparison)
        if (comparison.similarity < 25.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("HardstopGradientShaderGM", comparison.similarity)
        assertTrue(accepted, "HardstopGradientShaderGM regressed below ratchet")
        assertTrue(comparison.similarity >= 25.0,
            "HardstopGradientShaderGM similarity ${"%.2f".format(comparison.similarity)}% < 25.0% (dither + 8-bit-vs-16-bit drift; visual match)")
    }
}
