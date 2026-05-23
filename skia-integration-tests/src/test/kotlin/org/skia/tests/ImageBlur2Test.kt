package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ImageBlur2Test {

    @Test
    @Disabled("SLOW.GM_STRESS: ~16s text blur grid; run explicitly when touching saveLayer text blur or large blur kernels.")
    fun `ImageBlur2GM matches imageblur2_png within tolerance`() {
        val gm = ImageBlur2GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image imageblur2.png")
        // 6x6 grid of saveLayer'd text blocks under varying Gaussian
        // sigmas. Like ImageBlurGM, the dominant pixel-drift driver is
        // AWT-vs-FreeType AA on the underlying glyphs, amplified by the
        // wider blur kernels (sigma up to 80).
        val comparison = TestUtils.compareBitmapsDetailed(
            rendered, reference!!, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
        )
        TestReport.recordDetailed("ImageBlur2GM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ImageBlur2GM", comparison.similarity)
        assertTrue(accepted, "ImageBlur2GM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 85.6,
            "ImageBlur2GM similarity ${"%.2f".format(comparison.similarity)}% < 85.6%",
        )
    }
}
