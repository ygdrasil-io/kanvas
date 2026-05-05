package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class LinePathTest {

    @Test
    fun `LinePathGM matches linepath_png within tolerance`() {
        val gm = LinePathGM(doClose = false)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image linepath.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("LinePathGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("LinePathGM", comparison.similarity)
        assertTrue(accepted, "LinePathGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 70.0,
            "LinePathGM similarity ${"%.2f".format(comparison.similarity)}% < 70.0% floor",
        )
    }

    @Test
    fun `LineClosePathGM matches lineclosepath_png within tolerance`() {
        val gm = LineClosePathGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image lineclosepath.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("LineClosePathGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("LineClosePathGM", comparison.similarity)
        assertTrue(accepted, "LineClosePathGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 70.0,
            "LineClosePathGM similarity ${"%.2f".format(comparison.similarity)}% < 70.0% floor",
        )
    }
}
