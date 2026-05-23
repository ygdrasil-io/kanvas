package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/** Visual regression for [MaddashGM] (port of `dashcircle.cpp::maddash`). */
class MaddashTest {
    @Test
    fun `MaddashGM matches maddash_png within tolerance`() {
        val gm = MaddashGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("MaddashGM", comparison)
        if (comparison.similarity < 75.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("MaddashGM", comparison.similarity)
        assertTrue(accepted, "MaddashGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 75.0,
            "MaddashGM similarity ${"%.2f".format(comparison.similarity)}% < 75.0%",
        )
    }
}
