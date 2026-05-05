package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class CubicClosePathTest {

    @Test
    fun `CubicClosePathGM matches cubicclosepath_png within tolerance`() {
        val gm = CubicClosePathGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image cubicclosepath.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("CubicClosePathGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("CubicClosePathGM", comparison.similarity)
        assertTrue(accepted, "CubicClosePathGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 85.0,
            "CubicClosePathGM similarity ${"%.2f".format(comparison.similarity)}% < 85.0% floor",
        )
    }
}
