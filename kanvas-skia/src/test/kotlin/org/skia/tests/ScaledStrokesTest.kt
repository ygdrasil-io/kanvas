package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ScaledStrokesTest {
    @Test
    fun `ScaledStrokesGM matches scaledstrokes_png within tolerance`() {
        val gm = ScaledStrokesGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image scaledstrokes.png")
        // 4 scales × 4 shapes × 2 panes = 32 strokes. Stresses the stroker
        // resScale path uniformly across rect/circle/cubic/line.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ScaledStrokesGM", comparison)
        if (comparison.similarity < 85.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ScaledStrokesGM", comparison.similarity)
        assertTrue(accepted, "ScaledStrokesGM regressed below ratchet")
        assertTrue(comparison.similarity >= 85.0,
            "ScaledStrokesGM similarity ${"%.2f".format(comparison.similarity)}% < 85.0%")
    }
}
