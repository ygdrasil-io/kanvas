package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Pixel-regression test for [ImageMaskSubsetGM].
 *
 * The GM checks that subset [org.skia.foundation.SkImage]s preserve the
 * original `kAlpha_8` color type after [org.skia.foundation.SkImage.makeSubset]
 * — three backing strategies (raster, GPU-fallback, lazy/generator) each
 * produce two columns: the full image drawn with a src-subset rect, and a
 * materialised `makeSubset` snapshot drawn full-bounds.
 */
class ImageMaskSubsetTest {

    @Test
    fun `ImageMaskSubsetGM matches reference`() {
        val gm = ImageMaskSubsetGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("ImageMaskSubsetGM", comparison)
        if (comparison.similarity < 85.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        assertTrue(SimilarityTracker.updateScore("ImageMaskSubsetGM", comparison.similarity))
        assertTrue(
            comparison.similarity >= 85.0,
            "ImageMaskSubsetGM similarity ${"%.2f".format(comparison.similarity)}% < 85.0% floor",
        )
    }
}
