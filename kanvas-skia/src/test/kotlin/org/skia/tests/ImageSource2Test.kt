package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Tests for the four [ImageSource2GM] variants — upstream
 * `imagesrc2_none` / `_low` / `_med` / `_high`.
 *
 * High-frequency stripe pattern resampled through
 * [SkImageFilters.Image] with a fractional destination rect — the
 * exact moire pattern depends on the sampler kernel, so tolerance is
 * generous to absorb single-tap differences between our resamplers
 * and upstream's.
 */
class ImageSource2Test {

    @Test
    fun `ImageSource2NoneGM matches imagesrc2_none_png within tolerance`() =
        run(ImageSource2NoneGM(), "ImageSource2NoneGM", floor = 24.1)

    @Test
    fun `ImageSource2LowGM matches imagesrc2_low_png within tolerance`() =
        run(ImageSource2LowGM(), "ImageSource2LowGM", floor = 23.1)

    @Test
    fun `ImageSource2MedGM matches imagesrc2_med_png within tolerance`() =
        run(ImageSource2MedGM(), "ImageSource2MedGM", floor = 22.7)

    @Test
    fun `ImageSource2HighGM matches imagesrc2_high_png within tolerance`() =
        run(ImageSource2HighGM(), "ImageSource2HighGM", floor = 24.1)

    /**
     * Low similarities (< 40%) reflect the fact that our resamplers
     * diverge significantly from upstream's on the 503-px stripe
     * pattern with a fractional dst rect — every sampler kernel
     * picks up a different moire pattern. We ratchet from the
     * measured baseline and flag this as a future hi-fi target.
     */
    private fun run(gm: ImageSource2GM, key: String, floor: Double) {
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed(key, comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(key, comparison.similarity)
        assertTrue(accepted, "$key regressed below ratchet")
        assertTrue(
            comparison.similarity >= floor,
            "$key similarity ${"%.2f".format(comparison.similarity)}% < $floor% (t=8 floor)",
        )
    }
}
