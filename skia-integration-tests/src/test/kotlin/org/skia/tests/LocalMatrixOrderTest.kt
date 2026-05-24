package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class LocalMatrixOrderTest {

    @Test
    fun `LocalMatrixOrderGM matches localmatrix_order_png within tolerance`() {
        val gm = LocalMatrixOrderGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image localmatrix_order.png")
        // Animated GM — static render uses fAngle = 0 (no rotation applied).
        // Two images (mandrill_256 + example_5 scaled 2×) rotated 45° about
        // centre and blended via kModulate. Main drift source is the nearest-
        // neighbour sampling vs Skia's bilinear for the tiled compositing.
        // Tolerance 8 covers nearest-vs-linear AA edge difference.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("LocalMatrixOrderGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("LocalMatrixOrderGM", comparison.similarity)
        assertTrue(accepted, "LocalMatrixOrderGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 50.0,
            "LocalMatrixOrderGM similarity ${"%.2f".format(comparison.similarity)}% < 50% floor",
        )
    }
}
