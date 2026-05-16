package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class TextBlobGeometryChangeTest {

    @Test
    fun `TextBlobGeometryChangeGM matches textblobgeometrychange_png within tolerance`() {
        val gm = TextBlobGeometryChangeGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image textblobgeometrychange.png")

        // Two "Hamburgefons" blob draws at 20pt — one direct, one through an
        // offscreen surface composite. The offscreen path goes through
        // SkSurface::draw, which decodes glyphs identically on raster.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("TextBlobGeometryChangeGM", comparison)
        if (comparison.similarity < 93.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("TextBlobGeometryChangeGM", comparison.similarity)
        assertTrue(accepted, "TextBlobGeometryChangeGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 93.0,
            "TextBlobGeometryChangeGM similarity ${"%.2f".format(comparison.similarity)}% < 93.0% floor",
        )
    }
}
