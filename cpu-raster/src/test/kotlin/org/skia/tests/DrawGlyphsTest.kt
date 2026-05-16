package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class DrawGlyphsTest {

    @Test
    fun `DrawGlyphsGM matches drawglyphs_png within tolerance`() {
        val gm = DrawGlyphsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image drawglyphs.png")

        // 640 × 480, three straight text rows + one curved (RSXform-along-arc)
        // row of "Call me Ishmael..." at 18pt serif. The RSXform path is
        // emulated via per-glyph save / concat / drawPath — visible output
        // matches but per-glyph AA edges can shift compared to upstream's
        // single-pass batched path.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("DrawGlyphsGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("DrawGlyphsGM", comparison.similarity)
        assertTrue(accepted, "DrawGlyphsGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 95.0,
            "DrawGlyphsGM similarity ${"%.2f".format(comparison.similarity)}% < 95.0% floor",
        )
    }
}
