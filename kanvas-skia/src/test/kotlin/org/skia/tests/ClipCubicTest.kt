package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ClipCubicTest {
    @Test
    fun `ClipCubicGM matches clipcubic_png within tolerance`() {
        val gm = ClipCubicGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image clipcubic.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ClipCubicGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ClipCubicGM", comparison.similarity)
        assertTrue(accepted, "ClipCubicGM regressed below ratchet")
        assertTrue(comparison.similarity >= 90.0,
            "ClipCubicGM similarity ${"%.2f".format(comparison.similarity)}% < 90.0%")
    }
}
