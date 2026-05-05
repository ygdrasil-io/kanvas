package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class CubicPathTest {

    @Test
    fun `CubicPathGM matches cubicpath_png within tolerance`() {
        val gm = CubicPathGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image cubicpath.png")
        // 36-cell grid (4 fills × 3 styles × 3 caps). Inverse fills paint
        // every pixel of the cell's clipped rect that the path's interior
        // doesn't cover, exercising the Phase 3.8 scanline-walker
        // extension end-to-end. Stroke + fill combos exercise the
        // Phase 3g cap/join matrix; text labels ride on the AWT-backed
        // text harness.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("CubicPathGM", comparison)
        if (comparison.similarity < 85.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("CubicPathGM", comparison.similarity)
        assertTrue(accepted, "CubicPathGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 85.0,
            "CubicPathGM similarity ${"%.2f".format(comparison.similarity)}% < 85.0%",
        )
    }
}
