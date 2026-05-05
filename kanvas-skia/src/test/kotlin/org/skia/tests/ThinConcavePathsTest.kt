package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ThinConcavePathsTest {

    @Test
    fun `ThinConcavePathsGM matches thinconcavepaths_png within tolerance`() {
        val gm = ThinConcavePathsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image thinconcavepaths.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ThinConcavePathsGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ThinConcavePathsGM", comparison.similarity)
        assertTrue(accepted, "ThinConcavePathsGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 70.0,
            "ThinConcavePathsGM similarity ${"%.2f".format(comparison.similarity)}% < 70.0% floor",
        )
    }
}
