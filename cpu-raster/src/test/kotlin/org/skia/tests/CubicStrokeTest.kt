package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class CubicStrokeTest {
    @Test
    fun `CubicStrokeGM matches CubicStroke_png within tolerance`() {
        val gm = CubicStrokeGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image CubicStroke.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("CubicStrokeGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("CubicStrokeGM", comparison.similarity)
        assertTrue(accepted, "CubicStrokeGM regressed below ratchet")
        assertTrue(comparison.similarity >= 97.0,
            "CubicStrokeGM similarity ${"%.2f".format(comparison.similarity)}% < 97.0%")
    }
}
