package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class RRectTest {
    @Test
    fun `RRectGM matches rrect_png within tolerance`() {
        val gm = RRectGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image rrect.png")
        // 4 inset procs × 4 starting rrect types × 13 d-values = 208 stroked
        // rrects. Exercises type classification (isRect / isOval / isSimple /
        // kComplex), per-corner Bézier emitter, and the AA stroker on
        // mixed-type rrects. Hairline (default strokeWidth = 0) falls back
        // to width-1 stroke in our pipeline.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("RRectGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("RRectGM", comparison.similarity)
        assertTrue(accepted, "RRectGM regressed below ratchet")
        assertTrue(comparison.similarity >= 80.0,
            "RRectGM similarity ${"%.2f".format(comparison.similarity)}% < 80.0% (hairline fallback)")
    }
}
