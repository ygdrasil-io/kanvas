package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class StrokedLineCapsTest {

    @Test
    fun `StrokedLineCapsGM matches strokedline_caps_png within tolerance`() {
        val gm = StrokedLineCapsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("StrokedLineCapsGM", comparison)
        if (comparison.similarity < 70.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("StrokedLineCapsGM", comparison.similarity)
        assertTrue(accepted, "StrokedLineCapsGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 90.0,
            "StrokedLineCapsGM similarity ${"%.2f".format(comparison.similarity)}% < 90.0% floor",
        )
    }
}
