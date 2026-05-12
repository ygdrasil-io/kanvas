package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class MakeRasterImageTest {

    @Test
    fun `MakeRasterImageGM matches makeRasterImage_png within tolerance`() {
        val gm = MakeRasterImageGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image makeRasterImage.png")
        // 128 × 128 plain image draw. makeRasterImage is a no-op on the
        // CPU sink — the decoded color_wheel.png pixels land 1:1, modulo
        // the SkColor F16/sRGB → Rec.2020 reference colorspace transform.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 2)
        TestReport.recordDetailed("MakeRasterImageGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("MakeRasterImageGM", comparison.similarity)
        assertTrue(accepted, "MakeRasterImageGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 95.0,
            "MakeRasterImageGM similarity ${"%.2f".format(comparison.similarity)}% < 95.0% (t=2 floor)",
        )
    }
}
