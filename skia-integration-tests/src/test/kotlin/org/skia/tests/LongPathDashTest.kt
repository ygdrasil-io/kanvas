package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class LongPathDashTest {
    @Test
    fun `LongPathDashGM matches longpathdash_png within tolerance`() {
        val gm = LongPathDashGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("LongPathDashGM", comparison)
        if (comparison.similarity < 85.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("LongPathDashGM", comparison.similarity)
        assertTrue(accepted, "LongPathDashGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 39.0,
            "LongPathDashGM similarity ${"%.2f".format(comparison.similarity)}% < 39% floor",
        )
    }
}
