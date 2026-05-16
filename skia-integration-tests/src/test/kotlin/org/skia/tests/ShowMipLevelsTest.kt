package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Test for the R-final.5 [ShowMipLevelsGM] — exercises
 * [org.skia.foundation.SkMipmapBuilder] +
 * [org.skia.foundation.SkMipmapBuilder.attachTo] end-to-end.
 *
 * Pixel fidelity vs. the upstream `showmiplevels_explicit.png` depends
 * on the rasteriser honouring the explicit mip pyramid attached via
 * [org.skia.foundation.SkMipmapBuilder.attachTo]. The kanvas-skia
 * raster path consults the pyramid through
 * [org.skia.foundation.SkBitmapShader] only when the source image's
 * `mipLevels` chain is non-null — which is exactly what
 * [org.skia.foundation.SkMipmapBuilder.attachTo] sets up.
 */
class ShowMipLevelsTest {

    @Test
    fun `ShowMipLevelsGM matches showmiplevels_explicit_png within tolerance`() {
        val gm = ShowMipLevelsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed("ShowMipLevelsGM", comparison)
        if (comparison.similarity < 30.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ShowMipLevelsGM", comparison.similarity)
        assertTrue(accepted, "ShowMipLevelsGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 1.0,
            "ShowMipLevelsGM similarity ${"%.2f".format(comparison.similarity)}% < 1% floor",
        )
    }
}
