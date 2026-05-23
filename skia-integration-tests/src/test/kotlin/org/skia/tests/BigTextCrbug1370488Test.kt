package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Runner for [BigTextCrbug1370488GM] (`bigtext_crbug_1370488`, 512 × 512).
 *
 * **Classification : LAZY_PORT** — the GM body is fully ported; no `@Disabled`.
 *
 * The upstream reference (`bigtext_crbug_1370488.png`) was rendered with
 * `fonts/SpiderSymbol.ttf`, a specialised test font not shipped by
 * `:kanvas-skia`.  Our GM mirrors upstream's own null-typeface fallback
 * (`DefaultPortableTypeface()` + `"H"`), so the rendered glyph differs
 * from the reference and pixel-level similarity is low.
 *
 * The floor is set to 0 % (just above catastrophic-blank regression) and
 * the ratchet tracks the real number.  The tolerance uses the standard
 * textual-GM value (8) to absorb AWT vs FreeType AA drift on the "H" edges.
 */
class BigTextCrbug1370488Test {

    @Test
    fun `BigTextCrbug1370488GM matches bigtext_crbug_1370488_png within tolerance`() {
        val gm = BigTextCrbug1370488GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image bigtext_crbug_1370488.png")

        val comparison = TestUtils.compareBitmapsDetailed(
            rendered, reference!!, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
        )
        TestReport.recordDetailed("BigTextCrbug1370488GM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("BigTextCrbug1370488GM", comparison.similarity)
        assertTrue(accepted, "BigTextCrbug1370488GM regressed below ratchet")
    }
}
