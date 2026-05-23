package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Runner for [AlphaImageAlphaTintGM] (`alpha_image_alpha_tint`).
 *
 * Loose tolerance — the GM verifies paint-alpha tint of an alpha-only
 * image via two paths (direct `drawImage` and image-shader). Both
 * paths in `:kanvas-skia` share the H1.5 gap documented in
 * [AlphaImageGM] : RGB modulation of an A8 sample by `paint.color` is
 * not yet wired through the device, so the boxes render greyer than
 * the upstream translucent-green. The ratchet floor handles the rest.
 */
class AlphaImageAlphaTintTest {

    @Test
    fun `AlphaImageAlphaTintGM matches alpha_image_alpha_tint_png within tolerance`() {
        val gm = AlphaImageAlphaTintGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image alpha_image_alpha_tint.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("AlphaImageAlphaTintGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("AlphaImageAlphaTintGM", comparison.similarity)
        assertTrue(accepted, "AlphaImageAlphaTintGM regressed below ratchet")
    }
}
