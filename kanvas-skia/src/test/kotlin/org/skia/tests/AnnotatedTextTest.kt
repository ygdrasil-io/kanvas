package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Fourth textual GM port — see [AnnotatedTextGM] for source-spec mapping.
 *
 * Validates the rotated-text + saveLayer + clipRect combination that
 * earlier text GMs didn't cover.
 *
 * **Surfaced a real bug**: this GM's first run scored 24.9% because
 * `SkCanvas.drawColor` (and therefore `clear`) was bypassing the
 * active clip via `bitmap.eraseColor()`. Upstream Skia routes
 * `clear()` through `drawPaint(blendMode=kSrc)` which respects the
 * clip — so `clipRect(64,64,256,256); clear(0xEEEEEE)` only paints
 * the clipped region. After fixing `drawColor` to honour the clip
 * (this PR), the score jumped to ~99.9% — well above the 95% floor.
 *
 * Tolerance 8, floor 95% match the convention from `BigTextTest` and
 * `ColorWheelNativeTest`.
 */
class AnnotatedTextTest {

    @Test
    fun `AnnotatedTextGM matches annotated_text_png within tolerance`() {
        val gm = AnnotatedTextGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image annotated_text.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("AnnotatedTextGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("AnnotatedTextGM", comparison.similarity)
        assertTrue(accepted, "AnnotatedTextGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 95.0,
            "AnnotatedTextGM similarity ${"%.2f".format(comparison.similarity)}% < 95.0% floor",
        )
    }
}
