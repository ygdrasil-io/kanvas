package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class OvalTest {
    @Test
    fun `OvalGM matches ovals_png within tolerance`() {
        val gm = OvalGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ovals.png")
        // 5×8 grid of ovals under all paint × matrix combinations + special
        // rows. First real Phase 4b stress: rotate(60/90) and skew(2,3)
        // CTM through buildEdges and the stroker. Radial-gradient row is
        // dropped (no SkShader yet) — solid colour fallback drags the
        // score below 100 % around column 0 of the special-row area.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("OvalGM", comparison)
        if (comparison.similarity < 75.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("OvalGM", comparison.similarity)
        assertTrue(accepted, "OvalGM regressed below ratchet")
        assertTrue(comparison.similarity >= 93.0,
            "OvalGM similarity ${"%.2f".format(comparison.similarity)}% < 93.0% (gradient column missing)")
    }
}
