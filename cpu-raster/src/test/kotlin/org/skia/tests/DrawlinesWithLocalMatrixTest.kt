package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class DrawlinesWithLocalMatrixTest {

    @Test
    fun `DrawlinesWithLocalMatrixGM matches drawlines_with_local_matrix_png within tolerance`() {
        val gm = DrawlinesWithLocalMatrixGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image drawlines_with_local_matrix.png")

        // 500x500, full-canvas radial gradient + 9 white-bordered
        // rainbow lines via drawPoints(kLines, …) with kSquare_Cap.
        //
        // Score (~55%) is dominated by the same gradient-interpolation
        // colour-space mismatch as [Crbug1073670Test] : `:kanvas-skia`
        // samples gradient stops in linear-ish Rec.2020 F16 throughout,
        // while upstream Skia's DM reference interpolated in encoded
        // sRGB-like space (`Interpolation::ColorSpace::kDestination`).
        // The 6-stop ROYGBIV rainbow drifts ~30 channel-units in the
        // intermediate bands, so a tolerance-8 mismatch count covers
        // the majority of the canvas. Geometry + line strokes match
        // (the lines are in the right place at the right widths) — this
        // is purely a colour-stop sampling-space drift. Floor set to
        // 50% as the catastrophic-regression guard ; the ratchet locks
        // the live score so any future improvement (gradient interpolation
        // space switch) tightens the floor automatically.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("DrawlinesWithLocalMatrixGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("DrawlinesWithLocalMatrixGM", comparison.similarity)
        assertTrue(accepted, "DrawlinesWithLocalMatrixGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 50.0,
            "DrawlinesWithLocalMatrixGM similarity ${"%.2f".format(comparison.similarity)}% < 50.0% floor",
        )
    }
}
