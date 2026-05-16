package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class StrokeRectsRotatedTest {

    @Test
    fun `StrokeRectsRotatedGM matches strokerects_rotated_png within tolerance`() {
        val gm = StrokeRectsRotatedGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image strokerects_rotated.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("StrokeRectsRotatedGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("StrokeRectsRotatedGM", comparison.similarity)
        assertTrue(accepted, "StrokeRectsRotatedGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 80.0,
            "StrokeRectsRotatedGM similarity ${"%.2f".format(comparison.similarity)}% < 80.0% floor",
        )
    }
}
