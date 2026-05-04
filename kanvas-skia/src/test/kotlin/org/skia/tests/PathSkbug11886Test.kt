package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class PathSkbug11886Test {
    @Test
    fun `PathSkbug11886GM matches path_skbug_11886_png within tolerance`() {
        val gm = PathSkbug11886GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image path_skbug_11886.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("PathSkbug11886GM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("PathSkbug11886GM", comparison.similarity)
        assertTrue(accepted, "PathSkbug11886GM regressed below ratchet")
        assertTrue(comparison.similarity >= 90.0,
            "PathSkbug11886GM similarity ${"%.2f".format(comparison.similarity)}% < 90.0%")
    }
}
