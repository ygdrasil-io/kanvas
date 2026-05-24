package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ImageMagnifierTest {

    @Test
    fun `ImageMagnifierGM matches imagemagnifier_png within tolerance`() {
        val gm = ImageMagnifierGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image imagemagnifier.png")
        // Random-text + magnifier filter — same per-glyph drift seen in
        // `ImageBlurGM` (OpenType vs FreeType + filter amplification).
        val comparison = TestUtils.compareBitmapsDetailed(
            rendered, reference!!, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
        )
        TestReport.recordDetailed("ImageMagnifierGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ImageMagnifierGM", comparison.similarity)
        assertTrue(accepted, "ImageMagnifierGM regressed below ratchet")
    }

    @Test
    fun `ImageMagnifierCroppedGM matches imagemagnifier_cropped_png within tolerance`() {
        val gm = ImageMagnifierCroppedGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image imagemagnifier_cropped.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ImageMagnifierCroppedGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ImageMagnifierCroppedGM", comparison.similarity)
        assertTrue(accepted, "ImageMagnifierCroppedGM regressed below ratchet")
    }

    @Test
    fun `ImageMagnifierBoundsGM matches imagemagnifier_bounds_png within tolerance`() {
        val gm = ImageMagnifierBoundsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image imagemagnifier_bounds.png")
        val comparison = TestUtils.compareBitmapsDetailed(
            rendered, reference!!, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
        )
        TestReport.recordDetailed("ImageMagnifierBoundsGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ImageMagnifierBoundsGM", comparison.similarity)
        assertTrue(accepted, "ImageMagnifierBoundsGM regressed below ratchet")
    }
}
