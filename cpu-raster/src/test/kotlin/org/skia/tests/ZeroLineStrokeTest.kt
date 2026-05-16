package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ZeroLineStrokeTest {
    @Test
    fun `ZeroLineStrokeGM matches zerolinestroke_png within tolerance`() {
        val gm = ZeroLineStrokeGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image zerolinestroke.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ZeroLineStrokeGM", comparison)
        if (comparison.similarity < 85.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ZeroLineStrokeGM", comparison.similarity)
        assertTrue(accepted, "ZeroLineStrokeGM regressed below ratchet")
        assertTrue(comparison.similarity >= 85.0,
            "ZeroLineStrokeGM similarity ${"%.2f".format(comparison.similarity)}% < 85.0%")
    }
}
