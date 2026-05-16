package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ManyRRectsTest {
    @Test
    fun `ManyRRectsGM matches manyrrects_png within tolerance`() {
        val gm = ManyRRectsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image manyrrects.png")
        // 7 000 small AA rrects on a 5-px grid. Reuses one rrect with
        // MakeRectXY(1, 1), only the translate changes per iteration.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ManyRRectsGM", comparison)
        if (comparison.similarity < 85.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ManyRRectsGM", comparison.similarity)
        assertTrue(accepted, "ManyRRectsGM regressed below ratchet")
        assertTrue(comparison.similarity >= 85.0,
            "ManyRRectsGM similarity ${"%.2f".format(comparison.similarity)}% < 85.0% (tiny-rrect AA-corner drift)")
    }
}
