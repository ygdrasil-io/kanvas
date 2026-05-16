package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class DRRectSmallInnerTest {

    @Test
    fun `DRRectSmallInnerGM matches drrect_small_inner_png within tolerance`() {
        val gm = DRRectSmallInnerGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image drrect_small_inner.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("DRRectSmallInnerGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("DRRectSmallInnerGM", comparison.similarity)
        assertTrue(accepted, "DRRectSmallInnerGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 95.0,
            "DRRectSmallInnerGM similarity ${"%.2f".format(comparison.similarity)}% < 95.0% floor",
        )
    }
}
