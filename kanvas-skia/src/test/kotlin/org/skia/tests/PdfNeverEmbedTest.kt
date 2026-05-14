package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Visual regression for [PdfNeverEmbedGM] (port of upstream
 * `gm/pdf_never_embed.cpp::pdf_never_embed`).
 *
 * Renders four passes of `"HELLO, WORLD!"` at 60 px under varying
 * rotation / scale to verify the typeface fallback machinery doesn't
 * crash on a font with restricted-embedding OS/2 flags. Upstream
 * loads `fonts/Roboto2-Regular_NoEmbed.ttf`; that resource isn't
 * bundled here, so both we and upstream fall through to
 * `ToolUtils::DefaultPortableTypeface()` — same font, same glyph
 * shapes, so the cell layout is faithful modulo metric rounding.
 *
 * **Glyph-positioning gap** — upstream goes through
 * `SkTextBlobBuilder::allocRunPos` to lay out each glyph at the
 * advance computed by `SkFont::getPos`; we fall through to
 * [org.skia.core.SkCanvas.drawString] which walks the typeface
 * directly. The two paths produce the same advance widths for
 * Liberation Sans, so the visible drift is sub-pixel.
 */
class PdfNeverEmbedTest {

    @Test
    fun `PdfNeverEmbedGM matches pdf_never_embed_png within tolerance`() {
        val gm = PdfNeverEmbedGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image pdf_never_embed.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("PdfNeverEmbedGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("PdfNeverEmbedGM", comparison.similarity)
        assertTrue(accepted, "PdfNeverEmbedGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 76.2,
            "PdfNeverEmbedGM similarity ${"%.2f".format(comparison.similarity)}% < 76.2% floor",
        )
    }
}
