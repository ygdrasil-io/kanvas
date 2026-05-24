package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Wave 12B — UserFontGM regression test.
 *
 * The user-built typeface re-uses the default-portable typeface's first
 * 128 glyph paths via [org.skia.utils.SkCustomTypefaceBuilder]. The
 * left waterfall draws the source font directly ; the right waterfall
 * draws the rebuilt one. Pixel-level drift vs the reference is
 * dominated by the OpenType-vs-FreeType AA edge difference (already
 * absorbed by [TestUtils.TEXTUAL_GM_TOLERANCE] for textual GMs) plus
 * the drawable-glyph green fill on odd code-point glyphs (which the
 * reference renders identically — drawable glyph fill colour
 * `0xff008000` is shared with upstream).
 */
class UserFontTest {

    @Test
    fun `UserFontGM matches user_typeface_png within tolerance`() {
        val gm = UserFontGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image user_typeface.png")
        val comparison = TestUtils.compareBitmapsDetailed(
            rendered, reference!!, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
        )
        TestReport.recordDetailed("UserFontGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("UserFontGM", comparison.similarity)
        assertTrue(accepted, "UserFontGM regressed below ratchet")
    }
}
