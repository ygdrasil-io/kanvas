package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class FontScalerTest {

    @Test
    fun `FontScalerGM matches fontscaler_png within tolerance`() {
        val gm = FontScalerGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image fontscaler.png")

        // Heavy text GM — 1450 × 750 of small-to-medium glyphs at sizes 6..22
        // across 10 columns. AWT-vs-FreeType scaler delta dominates the
        // residual, especially at 6-9pt where every pixel of an AA edge can
        // shift. Background remains majority white.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("FontScalerGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("FontScalerGM", comparison.similarity)
        assertTrue(accepted, "FontScalerGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 80.0,
            "FontScalerGM similarity ${"%.2f".format(comparison.similarity)}% < 80.0% floor",
        )
    }
}
