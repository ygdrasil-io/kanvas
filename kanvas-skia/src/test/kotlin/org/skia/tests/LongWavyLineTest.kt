package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class LongWavyLineTest {
    @Test
    fun `LongWavyLineGM matches longwavyline_png within tolerance`() {
        val gm = LongWavyLineGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image longwavyline.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("LongWavyLineGM", comparison)
        if (comparison.similarity < 85.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("LongWavyLineGM", comparison.similarity)
        assertTrue(accepted, "LongWavyLineGM regressed below ratchet")
        assertTrue(comparison.similarity >= 85.0,
            "LongWavyLineGM similarity ${"%.2f".format(comparison.similarity)}% < 85.0%")
    }
}
