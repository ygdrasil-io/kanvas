package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Visual regression test for [ShadowUtilsGaussianColorFilterGM].
 *
 * Upstream reference : `shadow_utils_gaussian_colorfilter.png` (512 x 256).
 */
class ShadowUtilsGaussianColorFilterTest {

    @Test
    fun `ShadowUtilsGaussianColorFilterGM matches reference`() {
        val gm = ShadowUtilsGaussianColorFilterGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 16)
        TestReport.recordDetailed("ShadowUtilsGaussianColorFilter", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ShadowUtilsGaussianColorFilter", comparison.similarity)
        assertTrue(accepted, "ShadowUtilsGaussianColorFilter regressed below ratchet")
    }
}
