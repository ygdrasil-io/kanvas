package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class WindowRectanglesTest {

    @Test
    fun `WindowRectanglesGM matches windowrectangles_png within tolerance`() {
        val gm = WindowRectanglesGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image windowrectangles.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("WindowRectanglesGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("WindowRectanglesGM", comparison.similarity)
        assertTrue(accepted, "WindowRectanglesGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 70.0,
            "WindowRectanglesGM similarity ${"%.2f".format(comparison.similarity)}% < 70.0%",
        )
    }
}
