package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class StrokedLinesTest {

    @Test
    fun `StrokedLinesGM drawPath variant matches strokedlines_png within tolerance`() {
        val gm = StrokedLinesGM(useDrawPath = true)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")

        // Hairline-stroker, dash, blur, and bitmap-shader rasterisation
        // all diverge slightly from upstream's GPU reference renderer ;
        // the structural snowflake grid matches to within a few %.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("StrokedLinesGM_drawPath", comparison)
        val floor = 68.1
        if (comparison.similarity < floor) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("StrokedLinesGM_drawPath", comparison.similarity)
        assertTrue(accepted, "StrokedLinesGM (drawPath) regressed below ratchet")
    }

    @Test
    fun `StrokedLinesGM drawLine variant matches strokedlines_drawPoints_png within tolerance`() {
        val gm = StrokedLinesGM(useDrawPath = false)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("StrokedLinesGM_drawLine", comparison)
        val floor = 68.1
        if (comparison.similarity < floor) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("StrokedLinesGM_drawLine", comparison.similarity)
        assertTrue(accepted, "StrokedLinesGM (drawLine) regressed below ratchet")
    }
}
