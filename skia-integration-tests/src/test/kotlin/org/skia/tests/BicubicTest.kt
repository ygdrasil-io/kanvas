package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class BicubicTest {

    @Test
    fun `BicubicGM matches bicubic_png within tolerance`() {
        val gm = BicubicGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image bicubic.png")
        // Bicubic line over a black background — sampling band edges differ
        // by a few ulps between us and upstream's SkRasterPipeline cubic
        // path (different premul / xform ordering). Tolerance 8 is the
        // band we use across most filter-residual GMs.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("BicubicGM", comparison)
        if (comparison.similarity < 99.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("BicubicGM", comparison.similarity)
        assertTrue(accepted, "BicubicGM regressed below ratchet")
    }
}
