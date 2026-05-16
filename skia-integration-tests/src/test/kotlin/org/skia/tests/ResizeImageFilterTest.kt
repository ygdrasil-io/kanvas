package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Test for [ResizeImageFilterGM] — the upstream `resizeimagefilter`
 * GM. Six 96×96 panels of `MatrixTransform`-resampled ovals at
 * different sampler qualities, plus an Image filter input panel.
 *
 * Generous tolerance — each panel's pixel pattern depends on the
 * exact resampler kernel + saveLayer-format quantization, so
 * upstream-vs-us drift is non-trivial.
 */
class ResizeImageFilterTest {

    @Test
    fun `ResizeImageFilterGM matches resizeimagefilter_png within tolerance`() {
        val gm = ResizeImageFilterGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image resizeimagefilter.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 16)
        TestReport.recordDetailed("ResizeImageFilterGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ResizeImageFilterGM", comparison.similarity)
        assertTrue(accepted, "ResizeImageFilterGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 81.0,
            "ResizeImageFilterGM similarity ${"%.2f".format(comparison.similarity)}% < 81.0% (t=16 floor)",
        )
    }
}
