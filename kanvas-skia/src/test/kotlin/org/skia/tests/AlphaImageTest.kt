package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Phase H1-finish-B runner for [AlphaImageGM] (`alpha_image`).
 *
 * Partial port — `drawImage` on A8 source doesn't yet modulate by
 * `paint.color`, so the four-cell mosaic renders grey instead of the
 * coloured combinations upstream produces. Loose floor (≥ 15%) to
 * accommodate the H1.5 gap.
 */
class AlphaImageTest {

    @Test
    fun `AlphaImageGM matches alpha_image_png within tolerance`() {
        val gm = AlphaImageGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image alpha_image.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("AlphaImageGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("AlphaImageGM", comparison.similarity)
        assertTrue(accepted, "AlphaImageGM regressed below ratchet")
    }
}
