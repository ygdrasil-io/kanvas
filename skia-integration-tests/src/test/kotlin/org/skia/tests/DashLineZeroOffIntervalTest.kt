package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class DashLineZeroOffIntervalTest {
    @Test
    fun `DashLineZeroOffIntervalGM matches dash_line_zero_off_interval_png within tolerance`() {
        val gm = DashLineZeroOffIntervalGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("DashLineZeroOffIntervalGM", comparison)
        if (comparison.similarity < 85.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("DashLineZeroOffIntervalGM", comparison.similarity)
        assertTrue(accepted, "DashLineZeroOffIntervalGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 50.0,
            "DashLineZeroOffIntervalGM similarity ${"%.2f".format(comparison.similarity)}% < 50% floor",
        )
    }
}
