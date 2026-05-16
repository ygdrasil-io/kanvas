package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Test for [PointsMaskFilterGM] — the upstream `points_maskfilter`
 * GM. 30 random points stamped first as σ=6 blurred black discs,
 * then as solid red discs on top. Square caps in the left column,
 * round caps in the right.
 *
 * The blurred halos cover much of the column area, so any mismatch
 * in the Gaussian kernel normalisation or in the AA-stroke fill
 * shows up immediately.
 */
class PointsMaskFilterTest {

    @Test
    fun `PointsMaskFilterGM matches points_maskfilter_png within tolerance`() {
        val gm = PointsMaskFilterGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image points_maskfilter.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 6)
        TestReport.recordDetailed("PointsMaskFilterGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("PointsMaskFilterGM", comparison.similarity)
        assertTrue(accepted, "PointsMaskFilterGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 60.0,
            "PointsMaskFilterGM similarity ${"%.2f".format(comparison.similarity)}% < 60.0% floor",
        )
    }
}
