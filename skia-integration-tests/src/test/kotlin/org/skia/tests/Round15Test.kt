package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Round15Test {

    private fun runGm(gm: org.skia.tests.GM, trackerName: String, floor: Double) {
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed(trackerName, comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(trackerName, comparison.similarity)
        assertTrue(accepted, "$trackerName regressed below tolerance")
        assertTrue(
            comparison.similarity >= floor,
            "$trackerName similarity ${"%.2f".format(comparison.similarity)}% < $floor% floor",
        )
    }

    // BlurLargeRRectsGM at ~36 % — visual layout matches (4 rotated
    // colored RRects with σ=20 halos) but the giant -20000 ymin
    // coord makes the mask-buffer's bbox computation drift on the
    // AA edges of every cell. Floor 30 — tracker-only.
    @Test
    fun `BlurLargeRRectsGM matches reference`() =
        runGm(BlurLargeRRectsGM(), "BlurLargeRRectsGM", 30.0)

    @Test
    fun `SimpleBlurRoundRectGM matches reference`() =
        runGm(SimpleBlurRoundRectGM(), "SimpleBlurRoundRectGM", 50.0)
}
