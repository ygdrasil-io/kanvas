package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class DRRectTest {
    @Test
    fun `DRRectGM matches drrect_png within tolerance`() {
        val gm = DRRectGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image drrect.png")
        // 4 outer rrect types × 5 inner rrect types (incl. empty) = 20
        // donuts. Exercises the new SkCanvas.drawDRRect (winding-fill via
        // outer-CW + inner-CCW), the per-corner SkRRect.setRectRadii path,
        // and the addRRect Bézier emitter on every kComplex_Type.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("DRRectGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("DRRectGM", comparison.similarity)
        assertTrue(accepted, "DRRectGM regressed below ratchet")
        assertTrue(comparison.similarity >= 90.0,
            "DRRectGM similarity ${"%.2f".format(comparison.similarity)}% < 90.0% (t=1 floor)")
    }
}
