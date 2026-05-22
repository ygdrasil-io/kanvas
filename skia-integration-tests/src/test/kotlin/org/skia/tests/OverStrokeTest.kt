package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class OverStrokeTest {
    @Test
    fun `OverStrokeGM matches OverStroke_png within tolerance`() {
        val gm = OverStrokeGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image OverStroke.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("OverStrokeGM", comparison)
        if (comparison.similarity < 85.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("OverStrokeGM", comparison.similarity)
        assertTrue(accepted, "OverStrokeGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 85.5,
            "OverStrokeGM similarity ${"%.2f".format(comparison.similarity)}% < 85.5% floor",
        )
    }
}
