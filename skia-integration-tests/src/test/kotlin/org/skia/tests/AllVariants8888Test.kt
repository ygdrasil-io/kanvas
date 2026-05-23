package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Runner for [AllVariants8888GM] (`all_variants_8888`).
 *
 * 4-column × 2-row grid (over a checker) of 128 × 128 discs
 * exhausting the `(colourSpace, alphaType, colourType)` cross-product.
 * `:kanvas-skia` collapses `nullptr` colour space onto sRGB, so the
 * left and right halves render identically — upstream's reference has
 * a subtle gamut-clipping drift in the right half that we cannot
 * reproduce.
 */
class AllVariants8888Test {

    @Test
    fun `AllVariants8888GM matches all_variants_8888_png within tolerance`() {
        val gm = AllVariants8888GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image all_variants_8888.png")
        val comparison = TestUtils.compareBitmapsDetailed(
            rendered, reference!!, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
        )
        TestReport.recordDetailed("AllVariants8888GM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("AllVariants8888GM", comparison.similarity)
        assertTrue(accepted, "AllVariants8888GM regressed below ratchet")
    }
}
