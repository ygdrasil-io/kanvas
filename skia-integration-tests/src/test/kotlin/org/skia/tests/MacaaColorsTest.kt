package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Wave 12B — MacaaColorsGM regression test.
 *
 * The GM renders four background colour panels with `Hamburgefons` at
 * five sizes × four (lcd, hinting) configs. `:kanvas-skia` collapses
 * each (lcd, hinting) quadruple to a single visual config (all four
 * configs route through AA-no-hint OpenType ; cf. KDoc on [MacaaColorsGM]),
 * so per-pixel comparison vs upstream's reference (which alternates
 * between LCD subpixel and full-coverage AA, and between hinted and
 * unhinted glyphs) drifts at every glyph edge.
 *
 * Tolerance accordingly loose. The dominant drift sources :
 *  - text rasteriser identity (Liberation Serif vs portable Times),
 *  - downgraded LCD AA (no subpixel mask path),
 *  - hinting collapsed to the portable OpenType default.
 */
class MacaaColorsTest {

    @Test
    fun `MacaaColorsGM matches macaa_colors_png within tolerance`() {
        val gm = MacaaColorsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image macaa_colors.png")
        val comparison = TestUtils.compareBitmapsDetailed(
            rendered, reference!!, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
        )
        TestReport.recordDetailed("MacaaColorsGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("MacaaColorsGM", comparison.similarity)
        assertTrue(accepted, "MacaaColorsGM regressed below ratchet")
    }
}
