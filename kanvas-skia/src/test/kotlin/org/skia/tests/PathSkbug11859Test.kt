package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class PathSkbug11859Test {
    @Test
    fun `PathSkbug11859GM matches path_skbug_11859_png within tolerance`() {
        val gm = PathSkbug11859GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image path_skbug_11859.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("PathSkbug11859GM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("PathSkbug11859GM", comparison.similarity)
        assertTrue(accepted, "PathSkbug11859GM regressed below ratchet")
        assertTrue(comparison.similarity >= 90.0,
            "PathSkbug11859GM similarity ${"%.2f".format(comparison.similarity)}% < 90.0%")
    }
}
