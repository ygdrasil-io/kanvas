package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class BlurRectCompareTest {

    @Test
    fun `BlurRectCompareGM matches reference`() {
        val gm = BlurRectCompareGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image blurrect_compare.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("BlurRectCompareGM", comparison)
        if (comparison.similarity < 84.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        assertTrue(SimilarityTracker.updateScore("BlurRectCompareGM", comparison.similarity))
        assertTrue(
            comparison.similarity >= 84.0,
            "BlurRectCompareGM similarity ${"%.2f".format(comparison.similarity)}% < 84.0% floor",
        )
    }
}
