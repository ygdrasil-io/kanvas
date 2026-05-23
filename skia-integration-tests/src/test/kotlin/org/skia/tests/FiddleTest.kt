package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class FiddleTest {

    @Test
    fun `FiddleGM matches fiddle_png within tolerance`() {
        val gm = FiddleGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image fiddle.png")
        // Upstream draw is empty — `original-888/fiddle.png` is a pure
        // 256×256 white canvas. The comparison is essentially "did we
        // background-fill correctly?" ; we ratchet at 100 % similarity.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("FiddleGM", comparison)
        if (comparison.similarity < 99.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("FiddleGM", comparison.similarity)
        assertTrue(accepted, "FiddleGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 99.0,
            "FiddleGM similarity ${"%.2f".format(comparison.similarity)}% < 99.0%",
        )
    }
}
