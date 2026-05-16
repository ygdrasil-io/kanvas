package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class QuadPathTest {

    @Test
    fun `QuadPathGM matches quadpath_png within tolerance`() {
        val gm = QuadPathGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image quadpath.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("QuadPathGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("QuadPathGM", comparison.similarity)
        assertTrue(accepted, "QuadPathGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 86.0,
            "QuadPathGM similarity ${"%.2f".format(comparison.similarity)}% < 86.0% floor",
        )
    }

    @Test
    fun `QuadClosePathGM matches quadclosepath_png within tolerance`() {
        val gm = QuadClosePathGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image quadclosepath.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("QuadClosePathGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("QuadClosePathGM", comparison.similarity)
        assertTrue(accepted, "QuadClosePathGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 86.0,
            "QuadClosePathGM similarity ${"%.2f".format(comparison.similarity)}% < 86.0% floor",
        )
    }
}
