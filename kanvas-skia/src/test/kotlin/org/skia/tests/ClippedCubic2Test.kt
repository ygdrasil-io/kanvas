package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ClippedCubic2Test {
    @Test
    fun `ClippedCubic2GM matches clippedcubic2_png within tolerance`() {
        val gm = ClippedCubic2GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image clippedcubic2.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ClippedCubic2GM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ClippedCubic2GM", comparison.similarity)
        assertTrue(accepted, "ClippedCubic2GM regressed below ratchet")
        assertTrue(comparison.similarity >= 90.0,
            "ClippedCubic2GM similarity ${"%.2f".format(comparison.similarity)}% < 90.0%")
    }
}
