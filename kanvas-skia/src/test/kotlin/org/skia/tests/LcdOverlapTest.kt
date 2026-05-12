package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * GM port — see [LcdOverlapGM] for source-spec mapping.
 *
 * Six rotated drawings of a 32-px palindrome under various blend-mode
 * combinations at four canvas pivots. Upstream relies on
 * `kSubpixelAntiAlias` LCD-AA edging; the kanvas-skia [org.skia.foundation.SkFont]
 * silently downgrades that to plain antialiased edging — the
 * structural content (rotations + colours + blend modes) is preserved
 * but per-pixel colour at glyph edges drifts slightly.
 *
 * Floor 60% — most of the canvas is the white BG (matches exactly),
 * but every glyph pixel drifts because LCD subpixel AA produces
 * three-channel coverage per pixel whereas the downgraded plain-AA
 * path produces uniform coverage.
 */
class LcdOverlapTest {

    @Test
    fun `LcdOverlapGM matches lcdoverlap_png within tolerance`() {
        val gm = LcdOverlapGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image lcdoverlap.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("LcdOverlapGM", comparison)
        if (comparison.similarity < 60.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("LcdOverlapGM", comparison.similarity)
        assertTrue(accepted, "LcdOverlapGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 60.0,
            "LcdOverlapGM similarity ${"%.2f".format(comparison.similarity)}% < 60.0% floor",
        )
    }
}
