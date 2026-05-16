package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class RotatedCubicPathTest {
    @Test
    fun `RotatedCubicPathGM matches rotatedcubicpath_png within tolerance`() {
        val gm = RotatedCubicPathGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image rotatedcubicpath.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("RotatedCubicPathGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("RotatedCubicPathGM", comparison.similarity)
        assertTrue(accepted, "RotatedCubicPathGM regressed below ratchet")
        assertTrue(comparison.similarity >= 90.0,
            "RotatedCubicPathGM similarity ${"%.2f".format(comparison.similarity)}% < 90.0%")
    }
}
