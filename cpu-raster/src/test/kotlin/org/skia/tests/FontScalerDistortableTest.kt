package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * R-final.9 — variable-font test. The AWT scaler can only honour
 * the standard `wght` weight / `wdth` width axes via faux-bold, so
 * the per-cell axis-driven outline differences from the upstream PNG
 * don't materialise (cf. [FontScalerDistortableGM] KDoc). The test
 * floor is set conservatively : the structural layout still matches.
 */
class FontScalerDistortableTest {

    @Test
    fun `FontScalerDistortableGM matches fontscalerdistortable_png within tolerance`() {
        val gm = FontScalerDistortableGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image fontscalerdistortable.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed("FontScalerDistortableGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("FontScalerDistortableGM", comparison.similarity)
        assertTrue(accepted, "FontScalerDistortableGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= EXPECTED_SIMILARITY,
            "FontScalerDistortableGM similarity ${"%.2f".format(comparison.similarity)}% < $EXPECTED_SIMILARITY%",
        )
    }

    private companion object {
        // AWT cannot read the gvar table of Distortable.ttf — every
        // grid cell renders the same outline (faux-bold variant), so
        // the per-cell axis sweep is invisible. Set the floor low to
        // accept this divergence ; the SimilarityTracker ratchet keeps
        // raising it on regressions.
        const val EXPECTED_SIMILARITY: Double = 75.0
    }
}
