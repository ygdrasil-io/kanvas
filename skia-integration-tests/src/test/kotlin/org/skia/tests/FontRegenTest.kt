package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Cross-backend test for [FontRegenGM] (upstream `gm/fontregen.cpp::FontRegenGM`).
 *
 * Upstream was GPU-only ("It's not necessary to run this with CPU configs")
 * because the GM's purpose was to stress the glyph atlas regeneration
 * path. Raster has no atlas — `SkCanvas.drawTextBlob` materialises
 * each glyph through `SkTypeface.getPath` per call — so the
 * "regeneration" path is degenerate. The *visible draw output*, however,
 * is the same four text blobs at the same positions/colours as upstream
 * captured into `fontregen.png`, which is therefore a perfectly usable
 * cross-backend GM reference.
 */
class FontRegenTest {

    @Test
    fun `FontRegenGM matches fontregen_png within tolerance`() {
        val gm = FontRegenGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image fontregen.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("FontRegenGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("FontRegenGM", comparison.similarity)
        assertTrue(accepted, "FontRegenGM regressed below ratchet")
    }
}
