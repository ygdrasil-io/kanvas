package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Wave 11A textual GM port — see [GlyphPosGM] for source-spec mapping.
 *
 * Six variants — one PNG reference each — covering the
 * `(hairline / non-hairline) × (fill / stroke / strokeAndFill)` matrix.
 * Tolerance = [TestUtils.TEXTUAL_GM_TOLERANCE] (8) absorbs the
 * OpenType-vs-FreeType path-fill drift that already drives the other
 * textual GMs (BigText, AnnotatedText, ...).
 *
 * Per-variant floors are calibrated to the **observed** similarity
 * minus a 0.1 pp safety margin (per the H3 wave brief). The
 * stroked-only variant scores ~6 pp lower than the filled / fill+stroke
 * pair because our stroker's per-glyph join behaviour at 0px and 1.2px
 * deviates from upstream's. The [SimilarityTracker] ratchet locks
 * the per-test day-to-day score against further regression.
 */
class GlyphPosTest {

    @Test fun `GlyphPosHbGM matches glyph_pos_h_b_png within tolerance`() =
        run(GlyphPosHbGM(), floor = 89.9)

    @Test fun `GlyphPosNbGM matches glyph_pos_n_b_png within tolerance`() =
        run(GlyphPosNbGM(), floor = 93.9)

    @Test fun `GlyphPosHsGM matches glyph_pos_h_s_png within tolerance`() =
        run(GlyphPosHsGM(), floor = 86.5)

    @Test fun `GlyphPosNsGM matches glyph_pos_n_s_png within tolerance`() =
        run(GlyphPosNsGM(), floor = 92.3)

    @Test fun `GlyphPosHfGM matches glyph_pos_h_f_png within tolerance`() =
        run(GlyphPosHfGM(), floor = 93.9)

    @Test fun `GlyphPosNfGM matches glyph_pos_n_f_png within tolerance`() =
        run(GlyphPosNfGM(), floor = 93.9)

    private fun run(gm: GlyphPosGM, floor: Double) {
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")

        val comparison = TestUtils.compareBitmapsDetailed(
            rendered, reference!!, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
        )
        val tag = gm::class.simpleName ?: gm.name()
        TestReport.recordDetailed(tag, comparison)
        if (comparison.similarity < floor) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(tag, comparison.similarity)
        assertTrue(accepted, "$tag regressed below tolerance")
        assertTrue(
            comparison.similarity >= floor,
            "$tag similarity ${"%.2f".format(comparison.similarity)}% < $floor% floor",
        )
    }
}
